package io.github.kichikuou.xsystem4

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

interface GameListObserver {
    fun onInstallProgress(path: String)
    fun onInstallSuccess()
    fun onInstallCancelled()
    fun onInstallFailure(msgId: Int)
}

sealed class InstallState {
    object Idle : InstallState()
    data class Installing(
        val progress: String?,
        val permille: Int? = null,
        val cancellable: Boolean = false,
        val resumable: Boolean = false
    ) : InstallState()
    object Succeeded : InstallState()
    data class Cancelled(val paused: Boolean = false) : InstallState()
    data class Failed(val msgId: Int) : InstallState()
}

data class Item(val name: String, val path: File, val homedir: File, val savedir: File?, val icon: File?, val error: String?) {
    companion object {
        private val iconBitmapCache = object : LruCache<String, Bitmap>(8 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

        fun fromDirectory(dir: File, homedir: File, context: Context): Item {
            val isSys3x = GameCatalog.isSystem3xGameDir(dir)
            var iniFile = File(dir, "System40.ini")
            if (!iniFile.exists())
                iniFile = File(dir, "AliceStart.ini")
            if (!iniFile.exists())
                iniFile = File(dir, "System39.ini")
            if (!iniFile.exists()) {
                val ignoreCaseIni = dir.listFiles()?.firstOrNull {
                    it.isFile && (it.name.equals("System40.ini", ignoreCase = true) ||
                        it.name.equals("AliceStart.ini", ignoreCase = true) ||
                        it.name.equals("System39.ini", ignoreCase = true))
                }
                if (ignoreCaseIni != null) {
                    iniFile = ignoreCaseIni
                }
            }
            if (!iniFile.exists() && !isSys3x) {
                val err = context.getString(R.string.toast_no_ini, dir.path)
                Log.w("GameList", err)
                return Item(dir.name, dir, homedir, null, null, err)
            }
            val icon = findIcon(dir)
            val ini = if (iniFile.exists()) System40Ini.parse(iniFile) else System40Ini(null, null)
            val fallbackSys3Name = if (isSys3x) {
                when {
                    GameCatalog.isEscalayerGameDir(dir) -> context.getString(R.string.game_escalayer_title)
                    GameCatalog.isKichikuouGameDir(dir) -> context.getString(R.string.game_kichikuou_title)
                    GameCatalog.isDaiakujiGameDir(dir) -> context.getString(R.string.game_daiakuji_title)
                    else -> dir.name
                }
            } else {
                null
            }
            val effectiveGameName = ini.gameName?.takeIf { it.isNotBlank() } ?: fallbackSys3Name
            if (effectiveGameName == null) {
                val err = context.getString(R.string.toast_no_GameName, iniFile.path)
                Log.w("GameList", err)
                return Item(dir.name, dir, homedir, null, icon, err)
            }
            val savedir = File(context.filesDir, "saves/${dir.name}")
            savedir.mkdirs()
            val modMeta = InstalledModInfo.readOrInfer(dir)
            val hasModMarker = modMeta != null || detectInstalledMod(dir, ini)
            val rawName = effectiveGameName.replace("兰斯１０", "兰斯10")
            val displayName = if (hasModMarker) {
                val bakIni = File(dir, "AliceStart.ini.vanilla.bak").takeIf { it.exists() }
                    ?: File(dir, "System40.ini.vanilla.bak").takeIf { it.exists() }
                val baseName = (bakIni?.let { System40Ini.parse(it).gameName }
                    ?: if (rawName.contains("收费必是倒卖") || rawName.contains("日不落")) "兰斯10 决战" else rawName)
                    .replace("兰斯１０", "兰斯10")
                    .removeSuffix("[MOD]")
                    .trim()
                val verLabel = modMeta?.version?.takeIf { it.isNotBlank() }
                val tag = if (modMeta?.kind == InstalledModInfo.KIND_CN_PATCH) "汉化" else "MOD"
                if (verLabel != null) {
                    "$baseName [$tag · $verLabel]"
                } else {
                    "$baseName [$tag]"
                }
            } else {
                rawName
            }
            return Item(displayName, dir, homedir, savedir, icon, null)
        }

        fun detectInstalledMod(dir: File, parsedIni: System40Ini? = null): Boolean {
            if (File(dir, InstalledModInfo.META_FILE_NAME).isFile ||
                File(dir, ".mod_installed_files.txt").isFile
            ) {
                return true
            }
            val children = dir.listFiles()
            if (children != null) {
                for (f in children) {
                    if (!f.isFile) continue
                    val lower = f.name.lowercase()
                    if (lower.endsWith(".vanilla.bak") ||
                        (lower.startsWith("rance10modcg") && lower.endsWith(".afa")) ||
                        (lower.startsWith("rance10modflat") && lower.endsWith(".afa")) ||
                        lower == "asratest.ini" ||
                        lower == "作弊码.txt" ||
                        (lower == "rance10.ain" && f.length() > 8_000_000L)
                    ) {
                        return true
                    }
                }
            }
            val ini = parsedIni ?: run {
                val iniFile = File(dir, "System40.ini").takeIf { it.isFile }
                    ?: File(dir, "AliceStart.ini").takeIf { it.isFile }
                    ?: children?.firstOrNull {
                        it.isFile && (it.name.equals("System40.ini", ignoreCase = true) ||
                            it.name.equals("AliceStart.ini", ignoreCase = true))
                    }
                iniFile?.let { System40Ini.parse(it) }
            }
            if (ini != null) {
                if (ini.SaveFolder?.equals("ModSaveData", ignoreCase = true) == true) {
                    return true
                }
                val gn = ini.gameName ?: ""
                if (gn.contains("收费必是倒卖") || gn.contains("日不落") || gn.contains("[MOD")) {
                    return true
                }
            }
            return false
        }

        fun canRestoreVanillaDir(dir: File): Boolean {
            return File(dir, ".mod_installed_files.txt").isFile ||
                (dir.listFiles()?.any { it.name.endsWith(".vanilla.bak") } == true)
        }

        private fun findIcon(dir: File): File? {
            dir.listFiles()?.forEach {
                if (it.isFile && it.extension.equals("ico", ignoreCase = true)) {
                    return it
                }
            }
            try {
                dir.listFiles { file ->
                    file.isFile && file.extension.equals("exe", ignoreCase = true) &&
                        !file.name.equals("OpenSaveFolder.exe", ignoreCase = true) &&
                        !file.name.equals("ResetConfig.exe", ignoreCase = true) &&
                        !file.name.equals("Uninstaller.exe", ignoreCase = true)
                }?.forEach { exeFile ->
                    PEResourceExtractor.create(exeFile)?.extractIcon()?.let { bytes ->
                        val f = File(dir, ".xsystem4.ico")
                        val tmp = File(dir, ".xsystem4.ico.tmp")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(f)) {
                            f.writeBytes(bytes)
                            tmp.delete()
                        }
                        return f
                    }
                }
            } catch (e: Throwable) {
                Log.e("GameList", "Failed to extract or write icon", e)
            }
            return null
        }
    }

    fun getIconBitmap(reqSize: Int): Bitmap? {
        return try {
            val iconFile = icon ?: return null
            if (!iconFile.isFile) return null
            val cacheKey = "${iconFile.absolutePath}:${iconFile.lastModified()}:$reqSize"
            synchronized(iconBitmapCache) {
                iconBitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }
            }?.let { return it }

            val bytes = iconFile.readBytes()
            if (bytes.size < 6) return null
            val isPngHeader = bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            if (!isPngHeader) {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val reserved = buf.short.toInt() and 0xFFFF
                val icoType = buf.short.toInt() and 0xFFFF
                val count = buf.short.toInt() and 0xFFFF
                if (reserved != 0 || (icoType != 1 && icoType != 2) || count <= 0) {
                    return null
                }
                if (6L + count.toLong() * 16L > bytes.size.toLong()) {
                    return null
                }
                val validEntries = ArrayList<ByteArray>(count)
                for (idx in 0 until count) {
                    val entryBytes = ByteArray(16)
                    buf.get(entryBytes)
                    val entryBuf = ByteBuffer.wrap(entryBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val planes = entryBuf.getShort(4).toInt() and 0xFFFF
                    val bitCount = entryBuf.getShort(6).toInt() and 0xFFFF
                    val size = entryBuf.getInt(8)
                    val offset = entryBuf.getInt(12)
                    if (offset >= 0 && size > 0 && offset.toLong() + size.toLong() <= bytes.size.toLong() &&
                        planes >= 0 && bitCount >= 0
                    ) {
                        validEntries.add(entryBytes)
                    }
                }
                if (validEntries.isEmpty()) {
                    return null
                }
                if (count > 1) {
                    val bestMatchEntry = validEntries.minWith(compareBy({
                        val rawW = it[0].toInt() and 0xFF
                        val width = if (rawW == 0) 256 else rawW
                        kotlin.math.abs(width - reqSize)
                    }, {
                        val entryBuf = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
                        val planes = entryBuf.getShort(4).toInt() and 0xFFFF
                        val bitCount = entryBuf.getShort(6).toInt() and 0xFFFF
                        -(bitCount * 16 + planes.coerceAtMost(15))
                    }))
                    buf.position(4)
                    buf.putShort(1)
                    buf.put(bestMatchEntry)
                }
            }

            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (decoded != null) {
                synchronized(iconBitmapCache) {
                    iconBitmapCache.put(cacheKey, decoded)
                }
            }
            decoded
        } catch (_: Throwable) {
            null
        }
    }
}

data class System40Ini(val gameName: String?, val SaveFolder: String?) {
    companion object {
        fun parse(file: File): System40Ini = parse(file.readBytes())

        fun parse(raw: ByteArray): System40Ini {
            val regex = Regex("""(\w+)\s*=\s*"(.*)"""")
            val latin = String(raw, Charsets.ISO_8859_1)
            val gnMatch = Regex("""(?:^|\r?\n)\s*(?:GameName|Regist)\s*=\s*"([^\r\n"]*)"""").find(latin)
            val chosenCharset = if (gnMatch != null) {
                val gnRaw = gnMatch.groupValues[1].toByteArray(Charsets.ISO_8859_1)
                val decoded = ArchiveExtractor.decodeBestCjkText(gnRaw, preferGbkOnTie = true)
                when {
                    decoded == String(gnRaw, Charsets.UTF_8) && gnRaw.any { (it.toInt() and 0x80) != 0 } ->
                        Charsets.UTF_8
                    decoded == String(gnRaw, Charset.forName("Shift_JIS")) &&
                        decoded != String(gnRaw, Charset.forName("GBK")) ->
                        Charset.forName("Shift_JIS")
                    else -> Charset.forName("GBK")
                }
            } else {
                Charset.forName("GBK")
            }
            var gameName: String? = null
            var registName: String? = null
            var SaveFolder: String? = null
            for (line in String(raw, chosenCharset).lines()) {
                regex.matchEntire(line.trim())?.let {
                    when (it.groupValues[1]) {
                        "GameName" -> gameName = ArchiveExtractor.repairSjisAsGbkMojibake(it.groupValues[2])
                        "Regist" -> registName = ArchiveExtractor.repairSjisAsGbkMojibake(it.groupValues[2])
                        "SaveFolder" -> SaveFolder = ArchiveExtractor.repairSjisAsGbkMojibake(it.groupValues[2])
                    }
                }
            }
            return System40Ini(gameName ?: registName, SaveFolder)
        }
    }
}

// Game directories whose deletion is still running (absolute paths). Process-wide and thread-safe,
// so concurrent GameList rescans exclude paths currently being uninstalled.
private val uninstallingPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()
private val backgroundCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class GameList(activity: Activity) {
    private val appContext: Context = activity.applicationContext
    private val context: Context
        get() = AppSettings.wrapThemeContext(appContext)
    private val items: ArrayList<Item> = arrayListOf()
    private val storageDirs: ArrayList<File> = arrayListOf()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var installJob: Job? = null
    private var retiringBarrierJob: Job? = null
    private var activeSessionGen: Long = 0L
    private var activeAbortHandle: InstallAbortHandle? = null
    private var activeEvictPartial: (() -> List<File>)? = null
    operator fun get(index: Int): Item = items[index]
    val size: Int get() = items.size
    var observer: GameListObserver? = null
    var installState: InstallState = InstallState.Idle
        private set
    var isProgressMinimized: Boolean = false
        private set
    @Volatile
    var isRestoringMod: Boolean = false
        private set
    private var activeInstallResumable: Boolean = false
    @Volatile
    private var cancelDiscardPartial: Boolean = false
    private var activeTargetGamePath: String? = null
    var activeTargetGameId: String? = null
        private set
    var lastInstalledItem: Item? = null
        private set

    class InstallFailureException(val msgId: Int) : Exception()

    init {
        for (storagePath in activity.getExternalFilesDirs(null).filterNotNull()) {
            val state = Environment.getExternalStorageState(storagePath)
            Log.i("GameList", "${storagePath}: $state")
            if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                continue
            }
            storageDirs.add(storagePath)
            val homedir = File(storagePath, ".xsystem4")
            val files = storagePath.listFiles() ?: continue
            for (path in files) {
                if (!path.name.startsWith(".") && path.isDirectory && !uninstallingPaths.contains(path.absolutePath)) {
                    items.add(Item.fromDirectory(path, homedir, context))
                }
            }
            if (state == Environment.MEDIA_MOUNTED && files.isEmpty()) {
                val dummyFile = File(storagePath, "dummy.txt")
                dummyFile.writeText("Create a subfolder here that stores game files.")
                MediaScannerConnection.scanFile(
                    activity,
                    arrayOf(dummyFile.absolutePath),
                    null,
                    null
                )
            }
        }
        if (storageDirs.isEmpty()) {
            val storage = File(context.filesDir, "games")
            storage.mkdirs()
            storageDirs.add(storage)
            storage.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith('.') && !uninstallingPaths.contains(it.absolutePath) }
                ?.forEach {
                    items.add(Item.fromDirectory(it, File(storage, ".xsystem4"), context))
                }
        }
    }

    fun close() {
        if (installState !is InstallState.Installing && installJob?.isActive != true && !isRestoringMod) {
            observer = null
            scope.cancel()
        }
    }

    fun minimizeInstallProgress() {
        if (installState is InstallState.Installing) {
            isProgressMinimized = true
            InstallForegroundService.flushLatestNotification(context)
        }
    }

    fun restoreInstallProgress() {
        isProgressMinimized = false
    }

    fun isInstallingInto(item: Item): Boolean {
        return installState is InstallState.Installing && activeTargetGamePath == item.path.path
    }

    fun isInstallingForGame(gameId: String): Boolean {
        return installState is InstallState.Installing && activeTargetGameId == gameId
    }

    fun pauseInstall() {
        finishActiveByUser(discardPartial = false)
    }

    fun cancelInstall() {
        finishActiveByUser(discardPartial = true)
    }

    private fun finishActiveByUser(discardPartial: Boolean) {
        if (installState !is InstallState.Installing) return
        cancelDiscardPartial = discardPartial
        val gen = activeSessionGen
        val job = installJob
        val abort = activeAbortHandle
        val evictFn = activeEvictPartial
        val prevBarrier = retiringBarrierJob

        installJob = null
        activeAbortHandle = null
        activeEvictPartial = null

        abort?.abort()
        job?.cancel()

        val initialTrash = if (discardPartial && evictFn != null) {
            evictFn.invoke()
        } else {
            emptyList()
        }

        retiringBarrierJob = backgroundCleanupScope.launch(Dispatchers.IO) {
            try {
                prevBarrier?.join()
            } catch (_: Throwable) {
            }
            try {
                job?.join()
            } catch (_: Throwable) {
            }
            if (discardPartial && evictFn != null) {
                val lateTrash = evictFn.invoke()
                val allTrash = ArrayList<File>(initialTrash.size + lateTrash.size).apply {
                    addAll(initialTrash)
                    addAll(lateTrash)
                }
                if (allTrash.isNotEmpty()) {
                    backgroundCleanupScope.launch(Dispatchers.IO) {
                        for (t in allTrash) {
                            try {
                                t.deleteRecursively()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
        }

        setInstallCancelled(gen, wasPaused = !discardPartial)
    }

    private fun deleteDirAsyncViaTrash(dir: File?) {
        val trashed = OnlineInstaller.evictFileToTrash(dir) ?: return
        backgroundCleanupScope.launch(Dispatchers.IO) {
            try {
                trashed.deleteRecursively()
            } catch (_: Throwable) {
            }
        }
    }

    private fun beginInstallSession(
        notifTitle: String,
        initialProgress: String?,
        initialPermille: Int? = null,
        cancellable: Boolean = false,
        resumable: Boolean = false,
        targetGamePath: String? = null,
        notifModManagerPath: String? = null,
        targetGameId: String? = null,
        evictPartialOnCancel: (() -> List<File>)? = null
    ): Pair<Long, InstallAbortHandle> {
        val gen = ++activeSessionGen
        val abort = InstallAbortHandle()
        activeAbortHandle = abort
        activeEvictPartial = evictPartialOnCancel
        lastInstalledItem = null
        isProgressMinimized = false
        activeInstallResumable = resumable
        cancelDiscardPartial = false
        activeTargetGamePath = targetGamePath
        activeTargetGameId = targetGameId
        InstallForegroundService.pauseCallback = { pauseInstall() }
        InstallForegroundService.cancelCallback = { cancelInstall() }
        installState = InstallState.Installing(
            progress = initialProgress,
            permille = initialPermille,
            cancellable = cancellable,
            resumable = resumable
        )
        InstallForegroundService.startSession(
            context = context,
            title = notifTitle,
            initialText = initialProgress,
            initialPermille = initialPermille,
            isCancellable = cancellable,
            isResumable = resumable,
            targetGamePathForMod = notifModManagerPath,
            targetGameId = targetGameId
        )
        return gen to abort
    }

    fun installOnlineBaseGame(
        cdnBaseUrl: String = OnlineInstaller.RANCE10_CDN_URL,
        targetGameId: String = GameCatalog.ID_RANCE10
    ) {
        if (installState is InstallState.Installing || installJob?.isActive == true) return
        val storagePath = storageDirs[0]
        val resolvedStaging = java.util.concurrent.atomic.AtomicReference<File?>(null)
        val prevBarrier = retiringBarrierJob
        val (sessionGen, abortHandle) = beginInstallSession(
            notifTitle = context.getString(R.string.notif_title_base_online),
            initialProgress = context.getString(R.string.online_fetching_manifest),
            initialPermille = null,
            cancellable = true,
            resumable = true,
            targetGameId = targetGameId,
            evictPartialOnCancel = {
                OnlineInstaller.evictBaseStagingToTrash(storagePath, targetGameId, resolvedStaging.get())
            }
        )
        val mainHandler = Handler(Looper.getMainLooper())
        installJob = scope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    try {
                        prevBarrier?.join()
                    } catch (_: Throwable) {
                    }
                    val myJob = kotlin.coroutines.coroutineContext[Job]
                    OnlineInstaller.installBaseGameFromCloud(
                        context = context,
                        storagePath = storagePath,
                        cdnBaseUrl = cdnBaseUrl,
                        abortHandle = abortHandle,
                        onStagingDirReady = { resolvedStaging.set(it) },
                        checkCancelled = {
                            if (abortHandle.isAborted || myJob?.isActive == false) {
                                throw CancellationException()
                            }
                        },
                        onProgress = { update ->
                            mainHandler.post {
                                if (!abortHandle.isAborted && myJob?.isActive == true) {
                                    setInstallProgress(
                                        sessionGen,
                                        update.text,
                                        update.permille,
                                        cancellable = true,
                                        resumable = true
                                    )
                                }
                            }
                        }
                    )
                }
                val homedir = File(storagePath, ".xsystem4")
                val created = Item.fromDirectory(path, homedir, context)
                items.add(created)
                lastInstalledItem = created
                setInstallSucceeded(sessionGen)
            } catch (_: CancellationException) {
                val discard = cancelDiscardPartial
                if (discard && sessionGen == activeSessionGen) {
                    val trashed = OnlineInstaller.evictBaseStagingToTrash(storagePath, targetGameId, resolvedStaging.get())
                    if (trashed.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            for (t in trashed) {
                                try {
                                    t.deleteRecursively()
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                }
                setInstallCancelled(sessionGen, wasPaused = !discard)
            } catch (e: InstallFailureException) {
                setInstallFailed(sessionGen, e.msgId)
            } catch (e: Exception) {
                Log.e("GameList", "Failed to install base game online", e)
                setInstallFailed(sessionGen, R.string.online_download_error)
            }
        }
    }

    fun installDirectory(
        uri: Uri,
        expectedAinName: String? = null,
        targetGameId: String? = null
    ) {
        if (installState is InstallState.Installing || installJob?.isActive == true) return
        val prevBarrier = retiringBarrierJob
        val (sessionGen, abortHandle) = beginInstallSession(
            notifTitle = context.getString(R.string.notif_title_local_game),
            initialProgress = null,
            cancellable = true,
            resumable = false,
            targetGameId = targetGameId
        )
        installJob = scope.launch {
            try {
                val storage = storageDirs[0]
                val path = withContext(Dispatchers.IO) {
                    try {
                        prevBarrier?.join()
                    } catch (_: Throwable) {
                    }
                    val imported = NativeGameImport.copyTree(
                        context = context,
                        tree = uri,
                        storage = storage,
                        expectedAinName = expectedAinName,
                        targetGameId = targetGameId
                    ) { progress ->
                        if (abortHandle.isAborted) throw CancellationException()
                        withContext(Dispatchers.Main) {
                            setInstallProgress(sessionGen, progress, cancellable = true, resumable = false)
                        }
                    }
                    val classifiedId = GameCatalog.classifyDirectory(imported, imported.name)
                    val isRance02 = classifiedId == GameCatalog.ID_RANCE02
                    mergeInstallSetData(listOf(imported), imported, skipRance02Bloat = isRance02) {
                        if (abortHandle.isAborted) throw CancellationException()
                    }
                    if (classifiedId == GameCatalog.ID_GALZOO) {
                        mergeGalzooCompanionData(listOf(imported), imported) {
                            if (abortHandle.isAborted) throw CancellationException()
                        }
                    }
                    if (isRance02 ||
                        classifiedId == GameCatalog.ID_KICHIKUOU ||
                        classifiedId == GameCatalog.ID_DAIAKUJI ||
                        classifiedId == GameCatalog.ID_EVENICLE2 ||
                        classifiedId == GameCatalog.ID_ESCALAYER_REBOOT ||
                        classifiedId == GameCatalog.ID_RANCE06 ||
                        classifiedId == GameCatalog.ID_GALZOO ||
                        classifiedId == GameCatalog.ID_IXSEAL ||
                        classifiedId == GameCatalog.ID_TSUMASHIBORI
                    ) {
                        pruneRedundantBaseBloat(imported)
                    }
                    maybeRecordPreinstalledModMeta(imported, uri)
                    imported
                }
                val created = Item.fromDirectory(path, File(storage, ".xsystem4"), context)
                items.add(created)
                lastInstalledItem = created
                setInstallSucceeded(sessionGen)
            } catch (_: CancellationException) {
                setInstallCancelled(sessionGen, wasPaused = false)
            } catch (e: InstallFailureException) {
                setInstallFailed(sessionGen, e.msgId)
            } catch (e: Exception) {
                Log.e("GameList", "Failed to import game directory", e)
                setInstallFailed(sessionGen, R.string.directory_import_error)
            }
        }
    }

    fun installArchive(
        uri: Uri,
        expectedAinName: String? = null,
        targetGameId: String? = null
    ) {
        if (installState is InstallState.Installing || installJob?.isActive == true) return
        val prevBarrier = retiringBarrierJob
        val (sessionGen, abortHandle) = beginInstallSession(
            notifTitle = context.getString(R.string.notif_title_local_game),
            initialProgress = null,
            cancellable = true,
            resumable = false,
            targetGameId = targetGameId
        )
        val mainHandler = Handler(Looper.getMainLooper())
        installJob = scope.launch {
            try {
                val storagePath = storageDirs[0]
                val path = withContext(Dispatchers.IO) {
                    try {
                        prevBarrier?.join()
                    } catch (_: Throwable) {
                    }
                    val myJob = kotlin.coroutines.coroutineContext[Job]
                    val extracted = doInstallArchive(
                        uri = uri,
                        storagePath = storagePath,
                        sessionGen = sessionGen,
                        expectedAinName = expectedAinName,
                        targetGameId = targetGameId,
                        checkCancelled = {
                            if (abortHandle.isAborted || myJob?.isActive == false) {
                                throw CancellationException()
                            }
                        }
                    ) { msg ->
                        mainHandler.post {
                            if (!abortHandle.isAborted && myJob?.isActive == true) {
                                setInstallProgress(sessionGen, msg, cancellable = true, resumable = false)
                            }
                        }
                    }
                    maybeRecordPreinstalledModMeta(extracted, uri)
                    extracted
                }
                val homedir = File(storagePath, ".xsystem4")
                val created = Item.fromDirectory(path, homedir, context)
                items.add(created)
                lastInstalledItem = created
                setInstallSucceeded(sessionGen)
            } catch (_: CancellationException) {
                setInstallCancelled(sessionGen, wasPaused = false)
            } catch (e: InstallFailureException) {
                setInstallFailed(sessionGen, e.msgId)
            } catch (e: Exception) {
                Log.e("GameList", "Failed to extract game archive", e)
                setInstallFailed(sessionGen, R.string.zip_extraction_error)
            }
        }
    }

    fun consumeInstallResult() {
        if (installState is InstallState.Succeeded ||
            installState is InstallState.Cancelled ||
            installState is InstallState.Failed
        ) {
            installState = InstallState.Idle
            InstallForegroundService.clearResultNotification(context)
        }
    }

    private fun setInstallProgress(
        sessionGen: Long,
        progress: String,
        permille: Int? = null,
        cancellable: Boolean = false,
        resumable: Boolean = false
    ) {
        if (sessionGen != activeSessionGen) return
        installState = InstallState.Installing(progress, permille, cancellable, resumable)
        InstallForegroundService.updateProgress(
            context = context,
            text = progress,
            newPermille = permille,
            isCancellable = cancellable,
            isResumable = resumable
        )
        observer?.onInstallProgress(progress)
    }

    private fun setInstallSucceeded(sessionGen: Long) {
        if (sessionGen != activeSessionGen) return
        activeSessionGen++
        installJob = null
        activeAbortHandle = null
        activeEvictPartial = null
        val postNotif = observer == null || isProgressMinimized
        isProgressMinimized = false
        activeTargetGamePath = null
        activeTargetGameId = null
        installState = InstallState.Succeeded
        InstallForegroundService.finishSessionSucceeded(context, postResultNotification = postNotif)
        observer?.onInstallSuccess()
    }

    private fun setInstallCancelled(sessionGen: Long, wasPaused: Boolean) {
        if (sessionGen != activeSessionGen) return
        activeSessionGen++
        installJob = null
        activeAbortHandle = null
        activeEvictPartial = null
        val inBackground = observer == null || isProgressMinimized
        val pausedOutcome = wasPaused && activeInstallResumable
        isProgressMinimized = false
        activeTargetGamePath = null
        activeTargetGameId = null
        InstallForegroundService.finishSessionCancelled(context)
        if (inBackground) {
            installState = InstallState.Idle
            val toastMsg = if (pausedOutcome) {
                R.string.online_download_paused_toast
            } else {
                R.string.install_cancelled_toast
            }
            val toastLen = if (pausedOutcome) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(context, toastMsg, toastLen).show()
            observer?.onInstallCancelled()
        } else {
            installState = InstallState.Cancelled(paused = pausedOutcome)
            observer?.onInstallCancelled()
        }
    }

    private fun setInstallFailed(sessionGen: Long, msgId: Int) {
        if (sessionGen != activeSessionGen) return
        activeSessionGen++
        installJob = null
        activeAbortHandle = null
        activeEvictPartial = null
        val postNotif = observer == null || isProgressMinimized
        isProgressMinimized = false
        activeTargetGamePath = null
        activeTargetGameId = null
        installState = InstallState.Failed(msgId)
        InstallForegroundService.finishSessionFailed(
            context = context,
            errorMsg = context.getString(msgId),
            postResultNotification = postNotif
        )
        observer?.onInstallFailure(msgId)
    }

    private fun shouldSkipBaseInstallEntry(relPath: String): Boolean {
        if (NativeGameImport.skip(relPath)) return true
        val fileName = relPath.substringAfterLast('/').lowercase()
        val isArchive = fileName.endsWith(".rar") || fileName.endsWith(".zip") || fileName.endsWith(".7z")
        if (isArchive && (fileName.contains("sound album") || fileName.contains("(flac)") || fileName.contains("ost"))) {
            return true
        }
        return false
    }

    private fun isSystem4GameRoot(dir: File): Boolean =
        io.github.kichikuou.xsystem4.isSystem4GameRoot(dir)

    private fun isSystem3GameRoot(dir: File): Boolean =
        io.github.kichikuou.xsystem4.isSystem3GameRoot(dir)

    private fun findGameRootDir(
        root: File,
        expectedAinName: String?,
        targetGameId: String? = null
    ): File? = findGameRootDirInternal(root, expectedAinName, targetGameId)

    /** Merges multi-disc System 3.x sibling directories (e.g. Disc1/GAMEDATA .. Disc3/GAMEDATA). */
    private fun mergeSiblingMultiDiscDirs(
        searchRoot: File,
        primaryDir: File,
        checkCancelled: () -> Unit
    ) {
        if (!GameCatalog.isSystem3xGameDir(primaryDir)) return
        val primaryCanonical = primaryDir.canonicalPath
        val primaryScenario = primaryDir.listFiles()?.firstOrNull {
            it.isFile && (it.name.endsWith("sa.ald", true) || it.name.equals("adisk.ald", true))
        }?.name
        val dataExts = setOf("ald", "bgi", "wai", "alk", "s3l", "ini", "ain", "gr")
        searchRoot.walkTopDown()
            .filter { d ->
                d.isDirectory && d.canonicalPath != primaryCanonical && run {
                    val lower = d.path.replace('\\', '/').lowercase()
                    lower.contains("disc") || lower.contains("disk") || lower.endsWith("/gamedata")
                }
            }
            .toList()
            .forEach { sibling ->
                checkCancelled()
                val files = sibling.listFiles()?.filter { it.isFile }.orEmpty()
                val otherScenario = files.firstOrNull {
                    it.name.endsWith("sa.ald", true) || it.name.equals("adisk.ald", true)
                }?.name
                if (primaryScenario != null && otherScenario != null &&
                    !otherScenario.equals(primaryScenario, ignoreCase = true)
                ) {
                    return@forEach
                }
                for (f in files) {
                    checkCancelled()
                    if (NativeGameImport.skip(f.name)) continue
                    val ext = f.extension.lowercase()
                    if (ext !in dataExts && !f.name.equals("Alice.exe", true)) continue
                    val target = File(primaryDir, f.name)
                    if (!target.exists()) {
                        if (!f.renameTo(target)) {
                            f.copyTo(target, overwrite = false)
                            f.delete()
                        }
                    }
                }
            }
    }

    private fun overlayPatchUpdatesIfPresent(searchRoots: List<File>, destGameDir: File, checkCancelled: () -> Unit) {
        val ainName = destGameDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".ain", true) }?.name
        val destCanonical = destGameDir.canonicalPath
        val patchDirs = mutableListOf<File>()
        for (root in searchRoots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { d ->
                    d.isDirectory && !d.canonicalPath.startsWith(destCanonical) && run {
                        val p = d.path.replace('\\', '/').lowercase()
                        p.endsWith("/updatefiles/files/commonfiles") || p.endsWith("/commonfiles")
                    }
                }
                .forEach { patchDirs.add(it) }
        }
        for (pdir in patchDirs) {
            val files = pdir.listFiles()?.filter { it.isFile }.orEmpty()
            val hasAin = files.any { it.name.endsWith(".ain", true) }
            if (ainName != null && hasAin && files.none { it.name.equals(ainName, true) }) continue
            pdir.walkTopDown().forEach { f ->
                checkCancelled()
                val rel = f.relativeTo(pdir).path.replace('\\', '/')
                if (rel.isEmpty() || NativeGameImport.skip(rel)) return@forEach
                val out = File(destGameDir, rel)
                if (f.isDirectory) {
                    out.mkdirs()
                } else if (f.isFile) {
                    out.parentFile?.mkdirs()
                    f.copyTo(out, overwrite = true)
                }
            }
        }
    }

    private fun doInstallArchive(
        uri: Uri,
        storagePath: File,
        sessionGen: Long,
        expectedAinName: String? = null,
        targetGameId: String? = null,
        checkCancelled: () -> Unit,
        progressCallback: (String) -> Unit
    ): File {
        val isRance02Target = targetGameId == GameCatalog.ID_RANCE02
        val isRance06Target = targetGameId == GameCatalog.ID_RANCE06
        val isGalzooTarget = targetGameId == GameCatalog.ID_GALZOO
        val isKichikuouTarget = targetGameId == GameCatalog.ID_KICHIKUOU
        val isDaiakujiTarget = targetGameId == GameCatalog.ID_DAIAKUJI
        val isEvenicle2Target = targetGameId == GameCatalog.ID_EVENICLE2
        val isEscalayerRebootTarget = targetGameId == GameCatalog.ID_ESCALAYER_REBOOT
        val isIxsealTarget = targetGameId == GameCatalog.ID_IXSEAL
        val isTsumashiboriTarget = targetGameId == GameCatalog.ID_TSUMASHIBORI
        val tempDir = File(storagePath, ".install_temp_$sessionGen")
        tempDir.deleteRecursively()
        try {
            var currentStageDir = File(tempDir, "stage0")
            ArchiveExtractor.extractUriToDirectory(
                context = context,
                uri = uri,
                destDir = currentStageDir,
                tempScratchParent = storagePath,
                shouldSkip = { rel ->
                    shouldSkipBaseInstallEntry(rel) ||
                        (isRance02Target && isBaseInstallBloatRelPath(rel, skipPatchSubdir = false)) ||
                        (isKichikuouTarget && isKichikuouBloatRelPath(rel)) ||
                        (isDaiakujiTarget && isDaiakujiBloatRelPath(rel, skipDDemo0Backup = false)) ||
                        (isEvenicle2Target && isEvenicle2BloatRelPath(rel)) ||
                        (isEscalayerRebootTarget && isEscalayerRebootBloatRelPath(rel, skipArchives = false)) ||
                        (isRance06Target && isRance06BloatRelPath(rel, skipPatchSubdir = false)) ||
                        (isGalzooTarget && isGalzooBloatRelPath(rel)) ||
                        (isIxsealTarget && isIxsealBloatRelPath(rel, skipPatchCnSubdir = false)) ||
                        (isTsumashiboriTarget && isTsumashiboriBloatRelPath(rel))
                },
                throwInstallFailureOnNoSpace = true,
                checkCancelled = checkCancelled,
                onProgress = { totalBytes, entryPath ->
                    progressCallback(
                        context.getString(
                            R.string.import_extract_progress,
                            totalBytes / (1024L * 1024L),
                            entryPath
                        )
                    )
                }
            )

            var srcDir: File? = preferCnRootOverInstallData(findGameRootDir(currentStageDir, expectedAinName, targetGameId))
            var stage = 1
            while (srcDir == null && stage <= 3) {
                checkCancelled()
                val discs = currentStageDir.walkTopDown()
                    .filter { it.isFile && Iso9660Extractor.isIsoOrMdfName(it.name) }
                    .sortedByDescending { it.length() }
                    .toList()
                val chosenDisc = discs.firstOrNull {
                    Iso9660Extractor.containsGameRoot(it, expectedAinName, targetGameId)
                } ?: if (expectedAinName.isNullOrBlank() &&
                    (targetGameId.isNullOrBlank() || targetGameId.startsWith(GameCatalog.PREFIX_CUSTOM))
                ) {
                    discs.firstOrNull { Iso9660Extractor.containsGameRoot(it, null, null) }
                } else {
                    null
                }
                if (chosenDisc == null && discs.any { Iso9660Extractor.containsGameRoot(it, null, null) }) {
                    throw InstallFailureException(R.string.import_wrong_game)
                }

                if (chosenDisc != null) {
                    val nextDir = File(tempDir, "stage$stage")
                    ArchiveExtractor.extractFileToDirectory(
                        archiveFile = chosenDisc,
                        destDir = nextDir,
                        shouldSkip = { rel ->
                            shouldSkipBaseInstallEntry(rel) ||
                                (isRance02Target && isBaseInstallBloatRelPath(rel, skipPatchSubdir = false)) ||
                                (isKichikuouTarget && isKichikuouBloatRelPath(rel)) ||
                                (isDaiakujiTarget && isDaiakujiBloatRelPath(rel, skipDDemo0Backup = false)) ||
                                (isEvenicle2Target && isEvenicle2BloatRelPath(rel)) ||
                                (isEscalayerRebootTarget && isEscalayerRebootBloatRelPath(rel, skipArchives = false)) ||
                                (isRance06Target && isRance06BloatRelPath(rel, skipPatchSubdir = false)) ||
                                (isGalzooTarget && isGalzooBloatRelPath(rel)) ||
                                (isIxsealTarget && isIxsealBloatRelPath(rel, skipPatchCnSubdir = false)) ||
                                (isTsumashiboriTarget && isTsumashiboriBloatRelPath(rel))
                        },
                        throwInstallFailureOnNoSpace = true,
                        checkCancelled = checkCancelled,
                        onProgress = { totalBytes, entryPath ->
                            progressCallback(
                                context.getString(
                                    R.string.import_extract_progress,
                                    totalBytes / (1024L * 1024L),
                                    entryPath
                                )
                            )
                        }
                    )
                    // Delete extracted disc images immediately to reclaim space while preserving UpdateFiles
                    for (d in discs) d.delete()
                    currentStageDir.walkTopDown()
                        .filter { it.isFile && it.name.lowercase().endsWith(".mds") }
                        .forEach { it.delete() }
                    currentStageDir = nextDir
                    srcDir = preferCnRootOverInstallData(findGameRootDir(currentStageDir, expectedAinName, targetGameId))
                    stage++
                    continue
                }

                val archives = currentStageDir.walkTopDown()
                    .filter {
                        val rel = it.relativeTo(currentStageDir).path.replace('\\', '/')
                        it.isFile &&
                            ArchiveExtractor.isSupportedArchiveName(it.name) &&
                            !shouldSkipBaseInstallEntry(it.name) &&
                            (!isRance02Target || !isBaseInstallBloatRelPath(rel, skipPatchSubdir = true)) &&
                            (!isDaiakujiTarget || !isDaiakujiBloatRelPath(rel, skipDDemo0Backup = false)) &&
                            (!isEscalayerRebootTarget || !isEscalayerRebootBloatRelPath(rel, skipArchives = false)) &&
                            (!isRance06Target || !isRance06BloatRelPath(rel, skipPatchSubdir = true)) &&
                            (!isGalzooTarget || !isGalzooBloatRelPath(rel)) &&
                            (!isIxsealTarget || !isIxsealBloatRelPath(rel, skipPatchCnSubdir = true)) &&
                            (!isTsumashiboriTarget || !isTsumashiboriBloatRelPath(rel))
                    }
                    .sortedByDescending { it.length() }
                    .toList()
                val chosenArchive = archives.firstOrNull() ?: break
                val nextDir = File(tempDir, "stage$stage")
                ArchiveExtractor.extractFileToDirectory(
                    archiveFile = chosenArchive,
                    destDir = nextDir,
                    shouldSkip = { rel ->
                        shouldSkipBaseInstallEntry(rel) ||
                            (isRance02Target && isBaseInstallBloatRelPath(rel, skipPatchSubdir = false)) ||
                            (isKichikuouTarget && isKichikuouBloatRelPath(rel)) ||
                            (isDaiakujiTarget && isDaiakujiBloatRelPath(rel, skipDDemo0Backup = false)) ||
                            (isEvenicle2Target && isEvenicle2BloatRelPath(rel)) ||
                            (isEscalayerRebootTarget && isEscalayerRebootBloatRelPath(rel, skipArchives = false)) ||
                            (isRance06Target && isRance06BloatRelPath(rel, skipPatchSubdir = false)) ||
                            (isGalzooTarget && isGalzooBloatRelPath(rel)) ||
                            (isIxsealTarget && isIxsealBloatRelPath(rel, skipPatchCnSubdir = false)) ||
                            (isTsumashiboriTarget && isTsumashiboriBloatRelPath(rel))
                    },
                    throwInstallFailureOnNoSpace = true,
                    checkCancelled = checkCancelled,
                    onProgress = { totalBytes, entryPath ->
                        progressCallback(
                            context.getString(
                                R.string.import_extract_progress,
                                totalBytes / (1024L * 1024L),
                                entryPath
                            )
                        )
                    }
                )
                for (a in archives) a.delete()
                currentStageDir = nextDir
                srcDir = preferCnRootOverInstallData(findGameRootDir(currentStageDir, expectedAinName, targetGameId))
                stage++
            }

            if (srcDir == null) {
                val hasIni = tempDir.walkTopDown().any {
                    it.isFile && (it.name.equals("System40.ini", true) ||
                        it.name.equals("AliceStart.ini", true) ||
                        it.name.equals("System39.ini", true))
                }
                throw InstallFailureException(if (hasIni) R.string.directory_no_game else R.string.zip_no_ini)
            }
            mergeSiblingMultiDiscDirs(tempDir, srcDir, checkCancelled)
            val srcFiles = srcDir.listFiles()?.filter { it.isFile }.orEmpty()
            val ainFile = srcFiles.firstOrNull { it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true) }
            val sys3Scenario = srcFiles.firstOrNull {
                it.name.endsWith("sa.ald", true) || it.name.equals("adisk.ald", true)
            }
            if (ainFile == null && sys3Scenario == null) {
                throw InstallFailureException(R.string.directory_no_game)
            }
            if (!expectedAinName.isNullOrBlank() &&
                srcFiles.none { it.name.equals(expectedAinName, ignoreCase = true) }
            ) {
                throw InstallFailureException(R.string.import_wrong_game)
            }
            overlayPatchUpdatesIfPresent(listOf(tempDir), srcDir, checkCancelled)
            val isRance02 = isRance02Target ||
                GameCatalog.classifyDirectory(srcDir, srcDir.name) == GameCatalog.ID_RANCE02
            mergeInstallSetData(listOf(tempDir, srcDir), srcDir, skipRance02Bloat = isRance02, checkCancelled = checkCancelled)
            if (isGalzooTarget || GameCatalog.classifyGameRootDir(srcDir) == GameCatalog.ID_GALZOO) {
                mergeGalzooCompanionData(listOf(tempDir, srcDir), srcDir, checkCancelled = checkCancelled)
            }
            if (isIxsealTarget || GameCatalog.classifyDirectory(srcDir, srcDir.name) == GameCatalog.ID_IXSEAL) {
                overlayIxsealCnPatchIfPresent(listOf(tempDir), srcDir, checkCancelled)
            }
            if (!GameCatalog.validateImportedGameDir(srcDir, expectedAinName, targetGameId)) {
                throw InstallFailureException(R.string.import_wrong_game)
            }
            val classifiedId = GameCatalog.classifyDirectory(srcDir, srcDir.name)
            if (classifiedId == GameCatalog.ID_RANCE02 ||
                classifiedId == GameCatalog.ID_KICHIKUOU ||
                classifiedId == GameCatalog.ID_DAIAKUJI ||
                classifiedId == GameCatalog.ID_EVENICLE2 ||
                classifiedId == GameCatalog.ID_ESCALAYER_REBOOT ||
                classifiedId == GameCatalog.ID_RANCE06 ||
                classifiedId == GameCatalog.ID_GALZOO ||
                classifiedId == GameCatalog.ID_IXSEAL ||
                classifiedId == GameCatalog.ID_TSUMASHIBORI
            ) {
                pruneRedundantBaseBloat(srcDir)
            }
            val baseName = when {
                GameCatalog.isEscalayerGameDir(srcDir) -> "Escalayer"
                GameCatalog.isKichikuouGameDir(srcDir) -> "KichikuRance"
                GameCatalog.isDaiakujiGameDir(srcDir) -> "Daiakuji"
                ainFile != null && ainFile.name.equals("Evenicle2.ain", ignoreCase = true) -> "Evenicle2"
                ainFile != null && (
                    ainFile.name.equals("EscalayerReboot.ain", ignoreCase = true) ||
                        ainFile.name.equals("エスカレイヤー・リブート.ain", ignoreCase = true) ||
                        ainFile.name.equals("エスカレイヤーリブート.ain", ignoreCase = true)
                    ) -> "EscalayerReboot"
                classifiedId == GameCatalog.ID_IXSEAL -> "Ixseal"
                classifiedId == GameCatalog.ID_TSUMASHIBORI -> "Tsumashibori"
                classifiedId == GameCatalog.ID_RANCE06 -> "Rance06"
                classifiedId == GameCatalog.ID_GALZOO -> "GALZOO"
                ainFile != null -> sanitizeDerivedGameDirName(
                    ArchiveExtractor.repairSjisAsGbkMojibake(ainFile.nameWithoutExtension),
                    fallback = "Game"
                )
                sys3Scenario != null -> sanitizeDerivedGameDirName(
                    ArchiveExtractor.repairSjisAsGbkMojibake(sys3Scenario.nameWithoutExtension),
                    fallback = "System3"
                )
                else -> "Game"
            }
            var dstDir = File(storagePath, baseName)
            var i = 1
            while (dstDir.exists()) {
                dstDir = File(storagePath, "$baseName-${i++}")
            }
            if (!srcDir.renameTo(dstDir)) {
                throw IOException("Failed to move installed game: ${srcDir} -> ${dstDir}")
            }
            return dstDir
        } finally {
            deleteDirAsyncViaTrash(tempDir)
        }
    }

    private fun sanitizeDerivedGameDirName(raw: String, fallback: String = "Game"): String {
        val cleaned = raw
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')
            .filter { it >= ' ' }
            .trim()
            .trimStart('.', ' ', '\t')
            .trimEnd(' ', '.')
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") fallback else cleaned
    }

    fun isUninstalling(item: Item): Boolean = uninstallingPaths.contains(item.path.absolutePath)

    /**
     * Deletes [item]'s directory on an IO thread and drops it from the list when done.
     * A game can be several GB, so this must not run on the main thread. Runs in the
     * process-wide cleanup scope so leaving or refreshing the screen doesn't strand a
     * half-deleted entry or resurface a ghost item.
     * Returns false (and does nothing) if the same game is already being deleted.
     */
    fun uninstallAsync(item: Item, onDone: (deleted: Boolean) -> Unit): Boolean {
        val key = item.path.absolutePath
        if (!uninstallingPaths.add(key)) return false
        backgroundCleanupScope.launch {
            var deleted: Boolean
            try {
                deleted = withContext(Dispatchers.IO) {
                    item.path.deleteRecursively()
                    !item.path.exists()
                }
                if (deleted) {
                    items.removeAll { it.path.absolutePath == key }
                }
            } catch (t: Throwable) {
                Log.w("GameList", "uninstall failed: ${item.path}", t)
                deleted = false
            } finally {
                uninstallingPaths.remove(key)
            }
            onDone(deleted)
        }
        return true
    }

    private fun hasInstalledModDir(dir: File): Boolean = Item.detectInstalledMod(dir)

    fun hasInstalledMod(item: Item): Boolean = hasInstalledModDir(item.path)

    fun canRestoreVanilla(item: Item): Boolean = Item.canRestoreVanillaDir(item.path)

    private fun maybeRecordPreinstalledModMeta(gameDir: File, srcUri: Uri) {
        if (!hasInstalledModDir(gameDir) || File(gameDir, InstalledModInfo.META_FILE_NAME).isFile) {
            return
        }
        val srcLabel = resolveUriDisplayName(srcUri)
        val inferred = OnlineInstaller.inferVersionFromFileName(
            fileName = srcLabel,
            isPatchHint = false,
            fallbackIniDir = gameDir
        )
        if (inferred != "MOD") {
            InstalledModInfo.write(
                gameDir,
                InstalledModInfo(
                    version = inferred,
                    baseVersion = inferred,
                    fileId = -1,
                    baseFileId = -1,
                    fileName = srcLabel,
                    uploadedAt = "",
                    source = "preinstalled",
                    installedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun getInstalledModInfo(item: Item): InstalledModInfo? = InstalledModInfo.readOrInfer(item.path)

    fun refreshItemMetadata(item: Item) {
        refreshTargetItemInList(item)
    }

    fun restoreMod(item: Item): Boolean {
        if (installState is InstallState.Installing || installJob?.isActive == true || isRestoringMod) return false
        isRestoringMod = true
        return try {
            restoreVanillaFromMod(item.path)
            refreshTargetItemInList(item)
            true
        } catch (e: Exception) {
            Log.e("GameList", "Failed to restore vanilla game", e)
            false
        } finally {
            isRestoringMod = false
        }
    }

    fun launchRestoreMod(item: Item, onDone: (Boolean) -> Unit): Boolean {
        if (installState is InstallState.Installing || installJob?.isActive == true || isRestoringMod) {
            return false
        }
        isRestoringMod = true
        backgroundCleanupScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    restoreVanillaFromMod(item.path)
                }
                refreshTargetItemInList(item)
                true
            } catch (e: Throwable) {
                Log.e("GameList", "Failed to restore vanilla game", e)
                false
            } finally {
                isRestoringMod = false
            }
            onDone(ok)
        }
        return true
    }

    fun installModDirectory(targetItem: Item, uri: Uri) {
        if (installState is InstallState.Installing || installJob?.isActive == true) return
        val gameId = GameCatalog.classifyItem(targetItem)
        val isCnPatch = GameCatalog.descriptorFor(context, gameId).isCnPatch
        val overlayKind = if (isCnPatch) InstalledModInfo.KIND_CN_PATCH else InstalledModInfo.KIND_MOD
        val prevBarrier = retiringBarrierJob
        val (sessionGen, abortHandle) = beginInstallSession(
            notifTitle = context.getString(if (isCnPatch) R.string.notif_title_local_cn_patch else R.string.notif_title_local_mod),
            initialProgress = null,
            cancellable = true,
            resumable = false,
            targetGamePath = targetItem.path.path,
            targetGameId = gameId
        )
        installJob = scope.launch {
            val storage = targetItem.path.parentFile ?: storageDirs[0]
            var staging: File? = null
            try {
                withContext(Dispatchers.IO) {
                    try {
                        prevBarrier?.join()
                    } catch (_: Throwable) {
                    }
                    val prevInfo = InstalledModInfo.readOrInfer(targetItem.path)
                    val srcLabel = resolveUriDisplayName(uri)
                    staging = NativeGameImport.copyModTree(context, uri, storage) { progress ->
                        if (abortHandle.isAborted) throw CancellationException()
                        withContext(Dispatchers.Main) {
                            setInstallProgress(sessionGen, progress, cancellable = true, resumable = false)
                        }
                    }
                    val wasFull = applyModStagingToGameDir(
                        stagingDir = staging!!,
                        targetDir = targetItem.path,
                        cleanReplaceIfStandalone = true,
                        isCnPatch = isCnPatch,
                        allowEvenicle2IncrementalDetect = true
                    )
                    val inferredVer = OnlineInstaller.inferLocalOverlayVersion(
                        fileName = srcLabel,
                        isPatchHint = !wasFull,
                        fallbackIniDir = if (wasFull || isCnPatch) targetItem.path else null,
                        isCnPatch = isCnPatch
                    )
                    InstalledModInfo.write(
                        targetItem.path,
                        InstalledModInfo(
                            version = inferredVer,
                            baseVersion = if (wasFull || isCnPatch) inferredVer else prevInfo?.baseVersion,
                            fileId = -1,
                            baseFileId = if (wasFull || isCnPatch) -1 else (prevInfo?.baseFileId ?: -1),
                            fileName = srcLabel,
                            uploadedAt = "",
                            source = "local",
                            installedAtMs = System.currentTimeMillis(),
                            kind = overlayKind
                        )
                    )
                }
                refreshTargetItemInList(targetItem)
                setInstallSucceeded(sessionGen)
            } catch (_: CancellationException) {
                setInstallCancelled(sessionGen, wasPaused = false)
            } catch (e: InstallFailureException) {
                setInstallFailed(sessionGen, e.msgId)
            } catch (e: Exception) {
                Log.e("GameList", "Failed to install MOD from directory", e)
                setInstallFailed(sessionGen, if (isCnPatch) R.string.cn_patch_install_error else R.string.mod_install_error)
            } finally {
                deleteDirAsyncViaTrash(staging)
            }
        }
    }

    fun installModArchive(targetItem: Item, uri: Uri) {
        if (installState is InstallState.Installing || installJob?.isActive == true) return
        val gameId = GameCatalog.classifyItem(targetItem)
        val isCnPatch = GameCatalog.descriptorFor(context, gameId).isCnPatch
        val overlayKind = if (isCnPatch) InstalledModInfo.KIND_CN_PATCH else InstalledModInfo.KIND_MOD
        val prevBarrier = retiringBarrierJob
        val (sessionGen, abortHandle) = beginInstallSession(
            notifTitle = context.getString(if (isCnPatch) R.string.notif_title_local_cn_patch else R.string.notif_title_local_mod),
            initialProgress = null,
            cancellable = true,
            resumable = false,
            targetGamePath = targetItem.path.path,
            targetGameId = gameId
        )
        val mainHandler = Handler(Looper.getMainLooper())
        installJob = scope.launch {
            val storagePath = targetItem.path.parentFile ?: storageDirs[0]
            val tempDir = File(storagePath, ".mod_install_temp_$sessionGen")
            try {
                withContext(Dispatchers.IO) {
                    try {
                        prevBarrier?.join()
                    } catch (_: Throwable) {
                    }
                    val myJob = kotlin.coroutines.coroutineContext[Job]
                    val prevInfo = InstalledModInfo.readOrInfer(targetItem.path)
                    val srcLabel = resolveUriDisplayName(uri)
                    tempDir.deleteRecursively()
                    ArchiveExtractor.extractUriToDirectory(
                        context = context,
                        uri = uri,
                        destDir = tempDir,
                        tempScratchParent = storagePath,
                        shouldSkip = { rel -> NativeGameImport.skip(rel) },
                        throwInstallFailureOnNoSpace = true,
                        checkCancelled = {
                            if (abortHandle.isAborted || myJob?.isActive == false) {
                                throw CancellationException()
                            }
                        },
                        onProgress = { totalBytes, entryPath ->
                            val msg = context.getString(
                                R.string.import_extract_progress,
                                totalBytes / (1024L * 1024L),
                                entryPath
                            )
                            mainHandler.post {
                                if (!abortHandle.isAborted && myJob?.isActive == true) {
                                    setInstallProgress(sessionGen, msg, cancellable = true, resumable = false)
                                }
                            }
                        }
                    )
                    val wasFull = applyModStagingToGameDir(
                        stagingDir = tempDir,
                        targetDir = targetItem.path,
                        cleanReplaceIfStandalone = true,
                        isCnPatch = isCnPatch,
                        allowEvenicle2IncrementalDetect = true
                    )
                    val inferredVer = OnlineInstaller.inferLocalOverlayVersion(
                        fileName = srcLabel,
                        isPatchHint = !wasFull,
                        fallbackIniDir = if (wasFull || isCnPatch) targetItem.path else null,
                        isCnPatch = isCnPatch
                    )
                    InstalledModInfo.write(
                        targetItem.path,
                        InstalledModInfo(
                            version = inferredVer,
                            baseVersion = if (wasFull || isCnPatch) inferredVer else prevInfo?.baseVersion,
                            fileId = -1,
                            baseFileId = if (wasFull || isCnPatch) -1 else (prevInfo?.baseFileId ?: -1),
                            fileName = srcLabel,
                            uploadedAt = "",
                            source = "local",
                            installedAtMs = System.currentTimeMillis(),
                            kind = overlayKind
                        )
                    )
                }
                refreshTargetItemInList(targetItem)
                setInstallSucceeded(sessionGen)
            } catch (_: CancellationException) {
                setInstallCancelled(sessionGen, wasPaused = false)
            } catch (e: InstallFailureException) {
                setInstallFailed(sessionGen, e.msgId)
            } catch (e: Exception) {
                Log.e("GameList", "Failed to install MOD from archive", e)
                setInstallFailed(sessionGen, if (isCnPatch) R.string.cn_patch_install_error else R.string.mod_install_error)
            } finally {
                deleteDirAsyncViaTrash(tempDir)
            }
        }
    }

    fun installOnlineModPlan(
        targetItem: Item,
        steps: List<ModReleaseEntry>,
        fromModManager: Boolean = true
    ) {
        if (steps.isEmpty() || installState is InstallState.Installing || installJob?.isActive == true) return
        val gameId = GameCatalog.classifyItem(targetItem)
        val isCnPatch = GameCatalog.descriptorFor(context, gameId).isCnPatch
        val overlayKind = if (isCnPatch) InstalledModInfo.KIND_CN_PATCH else InstalledModInfo.KIND_MOD
        val firstLabel = if (steps.size > 1) {
            "[1/${steps.size}] ${steps[0].version}"
        } else {
            steps[0].version
        }
        val targetVer = steps.last().version
        val targetGroup = steps.last().group
        val notifLabel = if (isCnPatch && targetGroup.isNotBlank()) {
            "$targetGroup · $targetVer"
        } else {
            targetVer
        }
        val storagePath = targetItem.path.parentFile ?: storageDirs[0]
        val prevBarrier = retiringBarrierJob
        val (sessionGen, abortHandle) = beginInstallSession(
            notifTitle = context.getString(
                if (isCnPatch) R.string.notif_title_cn_patch_online else R.string.notif_title_mod_online,
                notifLabel
            ),
            initialProgress = context.getString(R.string.online_mod_starting_download, firstLabel),
            initialPermille = 0,
            cancellable = true,
            resumable = true,
            targetGamePath = targetItem.path.path,
            notifModManagerPath = if (fromModManager) targetItem.path.path else null,
            targetGameId = gameId,
            evictPartialOnCancel = {
                OnlineInstaller.evictModCacheToTrash(storagePath, steps)
            }
        )
        val mainHandler = Handler(Looper.getMainLooper())
        installJob = scope.launch {
            val tempDir = File(storagePath, ".mod_online_extract_temp_$sessionGen")
            var completedCount = 0
            try {
                withContext(Dispatchers.IO) {
                    try {
                        prevBarrier?.join()
                    } catch (_: Throwable) {
                    }
                    val myJob = kotlin.coroutines.coroutineContext[Job]
                    for ((idx, release) in steps.withIndex()) {
                        val stepPrefix = if (steps.size > 1) "[${idx + 1}/${steps.size}] " else ""
                        val req = ModDownloadRequest(
                            url = release.downloadUrl,
                            fileName = release.fileName,
                            contentLength = release.expectedSize,
                            expectedSha256 = release.expectedSha256
                        )
                        OnlineInstaller.downloadAndExtractMod(
                            context = context,
                            req = req,
                            storagePath = storagePath,
                            tempExtractDir = tempDir,
                            stepPrefix = stepPrefix,
                            releaseLabel = release.version,
                            abortHandle = abortHandle,
                            checkCancelled = {
                                if (abortHandle.isAborted || myJob?.isActive == false) {
                                    throw CancellationException()
                                }
                            },
                            onProgress = { update ->
                                mainHandler.post {
                                    if (!abortHandle.isAborted && myJob?.isActive == true) {
                                        setInstallProgress(
                                            sessionGen,
                                            update.text,
                                            update.permille,
                                            cancellable = true,
                                            resumable = true
                                        )
                                    }
                                }
                            },
                            applyStaging = { staging ->
                                val prevInfo = InstalledModInfo.read(targetItem.path)
                                val wasFull = applyModStagingToGameDir(
                                    stagingDir = staging,
                                    targetDir = targetItem.path,
                                    cleanReplaceIfStandalone = !release.isPatch,
                                    isCnPatch = isCnPatch
                                )
                                val baseVer = if (!release.isPatch || wasFull) {
                                    release.version
                                } else {
                                    release.baseVersion ?: prevInfo?.baseVersion
                                }
                                val baseFid = if (!release.isPatch || wasFull) {
                                    release.fileId
                                } else {
                                    release.baseFileId ?: prevInfo?.baseFileId ?: -1
                                }
                                InstalledModInfo.write(
                                    targetItem.path,
                                    InstalledModInfo(
                                        version = release.version,
                                        baseVersion = baseVer,
                                        fileId = release.fileId,
                                        baseFileId = baseFid,
                                        fileName = release.fileName,
                                        uploadedAt = release.uploadedAt,
                                        source = "online",
                                        installedAtMs = System.currentTimeMillis(),
                                        kind = overlayKind
                                    )
                                )
                            }
                        )
                        completedCount++
                        withContext(Dispatchers.Main) {
                            refreshTargetItemInList(targetItem)
                        }
                    }
                }
                refreshTargetItemInList(targetItem)
                setInstallSucceeded(sessionGen)
            } catch (_: CancellationException) {
                val discard = cancelDiscardPartial
                if (discard && sessionGen == activeSessionGen) {
                    val trashed = OnlineInstaller.evictModCacheToTrash(storagePath, steps)
                    if (trashed.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            for (t in trashed) {
                                try {
                                    t.deleteRecursively()
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                }
                if (completedCount > 0) refreshTargetItemInList(targetItem)
                setInstallCancelled(sessionGen, wasPaused = !discard)
            } catch (e: InstallFailureException) {
                if (completedCount > 0) refreshTargetItemInList(targetItem)
                setInstallFailed(sessionGen, e.msgId)
            } catch (e: Exception) {
                if (completedCount > 0) refreshTargetItemInList(targetItem)
                Log.e("GameList", "Failed to download and install online MOD plan", e)
                setInstallFailed(sessionGen, R.string.online_mod_download_error)
            } finally {
                deleteDirAsyncViaTrash(tempDir)
            }
        }
    }

    private fun refreshTargetItemInList(targetItem: Item) {
        val idx = items.indexOfFirst { it.path == targetItem.path }
        if (idx >= 0) {
            items[idx] = Item.fromDirectory(targetItem.path, targetItem.homedir, context)
        }
    }

    private fun resolveUriDisplayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = c.getString(idx)?.trim()
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "MOD"
    }

    private fun restoreVanillaFromMod(targetDir: File) {
        restoreVanillaFromModDir(targetDir)
    }

    private fun applyModStagingToGameDir(
        stagingDir: File,
        targetDir: File,
        cleanReplaceIfStandalone: Boolean = true,
        isCnPatch: Boolean = false,
        allowEvenicle2IncrementalDetect: Boolean = false
    ): Boolean = applyModStagingToGameDirInternal(
        stagingDir = stagingDir,
        targetDir = targetDir,
        cleanReplaceIfStandalone = cleanReplaceIfStandalone,
        isCnPatch = isCnPatch,
        allowEvenicle2IncrementalDetect = allowEvenicle2IncrementalDetect
    )
}

private fun isModAssetFile(file: File): Boolean {
    if (!file.isFile) return false
    val ext = file.extension.lowercase()
    return ext == "ain" || ext == "afa" || ext == "ex" || ext == "ald" || ext == "bgi" || ext == "wai" || ext == "fnl"
}

private fun isStandaloneFullModDir(modRoot: File): Boolean {
    val topFiles = modRoot.listFiles()?.filter { it.isFile } ?: return false
    val hasAin = topFiles.any { it.name.endsWith(".ain", ignoreCase = true) }
    val hasModCg = topFiles.any { it.name.equals("Rance10MODCG.afa", ignoreCase = true) }
    val hasModFlat = topFiles.any { it.name.equals("Rance10ModFlat.afa", ignoreCase = true) }
    return hasAin && hasModCg && hasModFlat
}

private fun findModRootDir(stagingDir: File, isCnPatch: Boolean = false): File {
    var cur = stagingDir
    while (true) {
        val children = cur.listFiles() ?: break
        if (children.any { isModAssetFile(it) }) {
            return cur
        }
        val dirs = children.filter { it.isDirectory && !it.name.startsWith(".") }
        if (dirs.size == 1) {
            cur = dirs[0]
        } else {
            val found = dirs.firstOrNull { d -> d.walkTopDown().any { isModAssetFile(it) } }
            if (found != null) {
                cur = found
            } else {
                break
            }
        }
    }
    throw GameList.InstallFailureException(
        if (isCnPatch) R.string.cn_patch_invalid_package else R.string.mod_invalid_package
    )
}

private fun pruneEmptyParentDirs(startDir: File?, stopDir: File) {
    val stopCanonical = try { stopDir.canonicalFile } catch (_: Throwable) { stopDir.absoluteFile }
    var cur = startDir
    while (cur != null) {
        val curCanonical = try { cur.canonicalFile } catch (_: Throwable) { cur.absoluteFile }
        if (curCanonical == stopCanonical ||
            !curCanonical.path.startsWith("${stopCanonical.path}${File.separator}")
        ) {
            break
        }
        val children = cur.listFiles()
        if (children == null || children.isNotEmpty() || !cur.delete()) {
            break
        }
        cur = cur.parentFile
    }
}

internal fun isEvenicle2CnPatchJunkRelPath(relPath: String): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val fileNameLower = norm.substringAfterLast('/').lowercase()
    return fileNameLower.endsWith(".exe") ||
        fileNameLower.endsWith(".dll") ||
        fileNameLower.endsWith(".torrent") ||
        fileNameLower == "staff.png" ||
        (fileNameLower.startsWith("请读我") && fileNameLower.endsWith(".png"))
}

/**
 * PC Evenicle 2 CN patch archives place `Evenicle2.ain` and `Evenicle2EX.ex` under `mai/sc/`
 * alongside `mai/word.mai` for `mai.dll` redirection. Promote `mai/sc/` script/ex files up to
 * the patch root so `xsystem4` loads the patched files directly while keeping `mai/word.mai`.
 */
internal fun normalizeEvenicle2CnPatchStaging(stagingDir: File) {
    if (!stagingDir.isDirectory) return
    val maiDirs = stagingDir.walkTopDown()
        .filter { it.isDirectory && it.name.equals("mai", ignoreCase = true) }
        .toList()
    for (maiDir in maiDirs) {
        val patchRoot = maiDir.parentFile ?: continue
        val maiChildren = maiDir.listFiles() ?: continue
        val scDir = maiChildren.firstOrNull { it.isDirectory && it.name.equals("sc", ignoreCase = true) }
            ?: continue
        val scFiles = scDir.listFiles()?.filter { it.isFile }.orEmpty()
        if (scFiles.none {
                it.name.endsWith(".ain", ignoreCase = true) ||
                    it.name.endsWith(".ex", ignoreCase = true)
            }
        ) {
            continue
        }
        for (f in scFiles) {
            val dest = File(patchRoot, f.name)
            if (dest.exists() && !dest.delete()) {
                throw IOException("Failed to replace staging file: $dest")
            }
            if (!f.renameTo(dest)) {
                f.copyTo(dest, overwrite = true)
                f.delete()
            }
        }
        for (subName in arrayOf("sc", "enc", "org", "pic")) {
            val subDir = maiChildren.firstOrNull { it.isDirectory && it.name.equals(subName, ignoreCase = true) }
            if (subDir != null && subDir.listFiles()?.isEmpty() == true) {
                subDir.delete()
            }
        }
    }
}

internal fun restoreVanillaFromModDir(targetDir: File) {
    InstalledModInfo.delete(targetDir)
    val canonicalTarget = try {
        targetDir.canonicalPath
    } catch (_: Throwable) {
        targetDir.absolutePath
    }
    targetDir.walkBottomUp()
        .filter {
            it.isFile && (it.name.endsWith(".vanilla.bak.tmp") ||
                (it.name.startsWith(".mod_apply_") && it.name.endsWith(".tmp")))
        }
        .forEach { it.delete() }

    val manifestFile = File(targetDir, ".mod_installed_files.txt")
    if (manifestFile.exists()) {
        for (rel in manifestFile.readLines()) {
            val trimmed = rel.trim().replace('\\', '/')
            if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.contains("..")) continue
            val f = File(targetDir, trimmed)
            val safeInside = try {
                val cp = f.canonicalPath
                cp.startsWith("$canonicalTarget${File.separator}")
            } catch (_: Throwable) {
                false
            }
            if (safeInside && f.exists() && f.isFile) {
                val hasBak = File(f.parentFile, "${f.name}.vanilla.bak").isFile
                if (!hasBak) {
                    f.delete()
                    pruneEmptyParentDirs(f.parentFile, targetDir)
                }
            }
        }
        manifestFile.delete()
    }
    val asraIni = File(targetDir, "AsraTest.ini")
    if (asraIni.isFile && !File(targetDir, "AsraTest.ini.vanilla.bak").isFile) {
        asraIni.delete()
    }
    val bakFiles = targetDir.walkBottomUp().filter { it.isFile && it.name.endsWith(".vanilla.bak") }.toList()
    for (bak in bakFiles) {
        val origName = bak.name.removeSuffix(".vanilla.bak")
        val origFile = File(bak.parentFile, origName)
        val tmpRestore = File(bak.parentFile, ".restore_${origName}.tmp")
        tmpRestore.delete()
        if (origFile.exists()) {
            origFile.delete()
        }
        if (!bak.renameTo(origFile)) {
            try {
                bak.copyTo(tmpRestore, overwrite = true)
                if (origFile.exists()) {
                    origFile.delete()
                }
                if (!tmpRestore.renameTo(origFile)) {
                    tmpRestore.copyTo(origFile, overwrite = true)
                    tmpRestore.delete()
                }
                bak.delete()
            } finally {
                tmpRestore.delete()
            }
        }
    }
}

internal fun applyModStagingToGameDirInternal(
    stagingDir: File,
    targetDir: File,
    cleanReplaceIfStandalone: Boolean = true,
    isCnPatch: Boolean = false,
    allowEvenicle2IncrementalDetect: Boolean = false
): Boolean {
    val isEvenicle2Target = isCnPatch &&
        GameCatalog.classifyDirectory(targetDir, targetDir.name) == GameCatalog.ID_EVENICLE2
    if (isEvenicle2Target) {
        normalizeEvenicle2CnPatchStaging(stagingDir)
    }
    val modRoot = findModRootDir(stagingDir, isCnPatch = isCnPatch)
    val isFullStandalone = isStandaloneFullModDir(modRoot)
    val modFiles = modRoot.walkTopDown().filter { it.isFile }.toList()
    val effectiveCleanReplace = if (isEvenicle2Target && cleanReplaceIfStandalone && allowEvenicle2IncrementalDetect) {
        modFiles.any { it.name.equals("Evenicle2CG.afa", ignoreCase = true) }
    } else {
        cleanReplaceIfStandalone
    }
    if (effectiveCleanReplace && (Item.detectInstalledMod(targetDir) || Item.canRestoreVanillaDir(targetDir))) {
        restoreVanillaFromModDir(targetDir)
    }

    val manifestFile = File(targetDir, ".mod_installed_files.txt")
    val addedFiles = linkedSetOf<String>()
    if (manifestFile.exists()) {
        manifestFile.readLines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("..") }
            .forEach { addedFiles.add(it) }
    }
    try {
        for (srcFile in modFiles) {
            val relPath = srcFile.relativeTo(modRoot).path.replace('\\', '/')
            if (relPath.isEmpty() ||
                relPath.startsWith(".") ||
                relPath.contains("..") ||
                relPath.endsWith(".vanilla.bak") ||
                relPath.endsWith(".vanilla.bak.tmp") ||
                NativeGameImport.skip(relPath) ||
                (isEvenicle2Target && isEvenicle2CnPatchJunkRelPath(relPath))
            ) {
                continue
            }
            val destFile = resolveCaseInsensitiveDest(targetDir, relPath)
            val destRelPath = destFile.relativeTo(targetDir).path.replace('\\', '/')
            val wasAddedByMod = addedFiles.any { it.equals(destRelPath, ignoreCase = true) }
            destFile.parentFile?.mkdirs()
            if (destFile.exists()) {
                if (!wasAddedByMod) {
                    val bakFile = File(destFile.parentFile, "${destFile.name}.vanilla.bak")
                    if (!bakFile.exists()) {
                        val tmpBak = File(destFile.parentFile, "${destFile.name}.vanilla.bak.tmp")
                        tmpBak.delete()
                        try {
                            destFile.copyTo(tmpBak, overwrite = true)
                        } catch (t: Throwable) {
                            tmpBak.delete()
                            throw t
                        }
                        if (!bakFile.exists()) {
                            if (!tmpBak.renameTo(bakFile)) {
                                tmpBak.delete()
                                throw IOException("Failed to commit vanilla backup: $bakFile")
                            }
                        } else {
                            tmpBak.delete()
                        }
                    }
                }
            } else if (!wasAddedByMod) {
                addedFiles.add(destRelPath)
            }

            val tmpDest = File(destFile.parentFile, ".mod_apply_${destFile.name}.tmp")
            tmpDest.delete()
            if (!srcFile.renameTo(tmpDest)) {
                try {
                    srcFile.copyTo(tmpDest, overwrite = true)
                } catch (t: Throwable) {
                    tmpDest.delete()
                    throw t
                }
                srcFile.delete()
            }
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tmpDest.renameTo(destFile)) {
                tmpDest.copyTo(destFile, overwrite = true)
                tmpDest.delete()
            }
        }
        if (File(targetDir, "Rance10.ain").isFile &&
            !File(targetDir, "AsraTest.ini.vanilla.bak").isFile
        ) {
            addedFiles.add("AsraTest.ini")
        }
    } finally {
        if (addedFiles.isNotEmpty() || manifestFile.exists()) {
            val manifestTmp = File(targetDir, ".mod_installed_files.txt.tmp")
            val content = addedFiles.joinToString("\n")
            manifestTmp.writeText(content)
            if (!manifestTmp.renameTo(manifestFile)) {
                manifestFile.writeText(content)
                manifestTmp.delete()
            }
        }
    }
    return effectiveCleanReplace || isFullStandalone
}

internal fun resolveCaseInsensitiveDest(targetDir: File, relPath: String): File {
    val parts = relPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
    var cur = targetDir
    for ((idx, seg) in parts.withIndex()) {
        val isLast = idx == parts.lastIndex
        val matched = cur.listFiles()?.firstOrNull { child ->
            if (isLast) {
                child.isFile &&
                    !child.name.endsWith(".vanilla.bak") &&
                    !child.name.endsWith(".vanilla.bak.tmp") &&
                    child.name.equals(seg, ignoreCase = true)
            } else {
                child.isDirectory && child.name.equals(seg, ignoreCase = true)
            }
        }
        cur = matched ?: File(cur, seg)
    }
    return cur
}

internal fun isBaseInstallBloatRelPath(relPath: String, skipPatchSubdir: Boolean = true): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    val fileNameLower = segments.last().lowercase()
    if (fileNameLower.endsWith(".bak") && !fileNameLower.endsWith(".vanilla.bak")) {
        return true
    }
    if (fileNameLower.contains("翻墙软件")) {
        return true
    }
    val matchSeg = { seg: String ->
        seg == "cgpatch" || seg == "directx" || (skipPatchSubdir && seg == "补丁")
    }
    for (i in 0 until segments.size - 1) {
        if (matchSeg(segments[i].lowercase())) {
            return true
        }
    }
    return matchSeg(segments.last().lowercase())
}

internal fun preferCnRootOverInstallData(candidate: File?): File? {
    if (candidate != null &&
        candidate.name.equals("InstallData", ignoreCase = true) &&
        candidate.parentFile?.name.equals("InstallSet", ignoreCase = true)
    ) {
        val outerCnRoot = candidate.parentFile?.parentFile
        if (outerCnRoot != null && outerCnRoot.isDirectory &&
            outerCnRoot.listFiles()?.any {
                it.isFile && (it.name.endsWith(".ain", ignoreCase = true) ||
                    it.name.equals("AliceStart.ini", ignoreCase = true) ||
                    it.name.equals("System40.ini", ignoreCase = true))
            } == true
        ) {
            return outerCnRoot
        }
    }
    return candidate
}

internal fun overlayIxsealCnPatchIfPresent(
    searchRoots: List<File>,
    destGameDir: File,
    checkCancelled: () -> Unit = {}
) {
    if (GameCatalog.classifyDirectory(destGameDir, destGameDir.name) != GameCatalog.ID_IXSEAL) {
        return
    }
    val destCanonical = try {
        destGameDir.canonicalPath
    } catch (_: Throwable) {
        destGameDir.absolutePath
    }
    val patchRoots = mutableListOf<File>()
    for (root in searchRoots) {
        if (!root.exists()) continue
        root.walkTopDown()
            .filter { d ->
                d.isDirectory &&
                    d.name.equals("patchCN", ignoreCase = true) &&
                    !d.canonicalPath.startsWith(destCanonical + File.separator) &&
                    d.canonicalPath != destCanonical
            }
            .forEach { d ->
                val hasDirectFiles = d.listFiles()?.any {
                    it.isFile && (it.name.equals("Ixseal.ain", true) || it.name.equals("IxsealCG.afa", true))
                } == true
                if (hasDirectFiles) {
                    patchRoots.add(d)
                } else {
                    d.walkTopDown()
                        .filter { sub ->
                            sub.isDirectory && sub != d && sub.listFiles()?.any {
                                it.isFile && (it.name.equals("Ixseal.ain", true) || it.name.equals("IxsealCG.afa", true))
                            } == true
                        }
                        .sortedBy { it.name }
                        .forEach { patchRoots.add(it) }
                }
            }
    }
    for (pdir in patchRoots.distinctBy { try { it.canonicalPath } catch (_: Throwable) { it.absolutePath } }) {
        pdir.walkTopDown().forEach { f ->
            checkCancelled()
            val rel = f.relativeTo(pdir).path.replace('\\', '/')
            if (rel.isEmpty() || NativeGameImport.skip(rel) || isIxsealBloatRelPath(rel, skipPatchCnSubdir = false)) {
                return@forEach
            }
            val destFile = resolveCaseInsensitiveDest(destGameDir, rel)
            if (f.isDirectory) {
                destFile.mkdirs()
            } else if (f.isFile) {
                destFile.parentFile?.mkdirs()
                if (destFile.isFile) {
                    destFile.delete()
                }
                if (!OnlineInstaller.moveFileOrDir(f, destFile)) {
                    f.copyTo(destFile, overwrite = true)
                }
            }
        }
    }
}

internal fun mergeInstallSetData(
    searchRoots: List<File>,
    destGameDir: File,
    skipRance02Bloat: Boolean = false,
    checkCancelled: () -> Unit = {}
) {
    if (!skipRance02Bloat &&
        GameCatalog.classifyDirectory(destGameDir, destGameDir.name) != GameCatalog.ID_RANCE02
    ) {
        return
    }
    val destCanonical = try {
        destGameDir.canonicalPath
    } catch (_: Throwable) {
        destGameDir.absolutePath
    }
    val installDataDirs = mutableListOf<File>()
    for (root in searchRoots) {
        if (!root.exists()) continue
        root.walkTopDown()
            .filter { d ->
                d.isDirectory &&
                    d.name.equals("InstallData", ignoreCase = true) &&
                    d.parentFile?.name.equals("InstallSet", ignoreCase = true)
            }
            .forEach { d ->
                val dCanon = try { d.canonicalPath } catch (_: Throwable) { d.absolutePath }
                if (dCanon != destCanonical) {
                    installDataDirs.add(d)
                }
            }
    }
    for (dataDir in installDataDirs.distinctBy { try { it.canonicalPath } catch (_: Throwable) { it.absolutePath } }) {
        dataDir.walkTopDown().forEach { f ->
            checkCancelled()
            if (!f.isFile) return@forEach
            val rel = f.relativeTo(dataDir).path.replace('\\', '/')
            if (rel.isEmpty() || NativeGameImport.skip(rel) || (skipRance02Bloat && isBaseInstallBloatRelPath(rel))) {
                return@forEach
            }
            val destFile = resolveCaseInsensitiveDest(destGameDir, rel)
            if (!destFile.exists()) {
                destFile.parentFile?.mkdirs()
                f.copyTo(destFile, overwrite = false)
            }
        }
        val installSetDir = dataDir.parentFile
        if (installSetDir != null && installSetDir.name.equals("InstallSet", ignoreCase = true)) {
            val setCanon = try { installSetDir.canonicalPath } catch (_: Throwable) { installSetDir.absolutePath }
            if (setCanon.startsWith(destCanonical + File.separator) && setCanon != destCanonical) {
                installSetDir.deleteRecursively()
            }
        }
    }
}

@JvmOverloads
internal fun mergeGalzooCompanionData(
    searchRoots: List<File>,
    destGameDir: File,
    checkCancelled: () -> Unit = {}
) {
    if (GameCatalog.classifyGameRootDir(destGameDir) != GameCatalog.ID_GALZOO) {
        return
    }
    val destDataDir = File(destGameDir, "Data")
    val destDataCanon = try { destDataDir.canonicalPath } catch (_: Throwable) { destDataDir.absolutePath }
    val destCanon = try { destGameDir.canonicalPath } catch (_: Throwable) { destGameDir.absolutePath }
    val companionDataDirs = mutableListOf<File>()
    for (root in searchRoots) {
        if (!root.exists()) continue
        root.walkTopDown()
            .filter { d ->
                if (!d.isDirectory || !d.name.equals("Data", ignoreCase = true)) return@filter false
                val dCanon = try { d.canonicalPath } catch (_: Throwable) { d.absolutePath }
                if (dCanon == destDataCanon) return@filter false
                val relSegs = d.relativeTo(root).path.replace('\\', '/').split('/')
                if (relSegs.any {
                        it.equals("BackData", ignoreCase = true) ||
                            it == "补丁" ||
                            it == "备份" ||
                            it == "备份文件"
                    }
                ) {
                    return@filter false
                }
                d.listFiles()?.any { f ->
                    f.isFile && (
                        f.name.equals("PolyObj.lin", ignoreCase = true) ||
                            f.name.equals("Event.alk", ignoreCase = true) ||
                            f.name.endsWith(".dgn", ignoreCase = true)
                        )
                } == true
            }
            .forEach { companionDataDirs.add(it) }
    }
    for (dataDir in companionDataDirs.distinctBy { try { it.canonicalPath } catch (_: Throwable) { it.absolutePath } }) {
        dataDir.walkTopDown().forEach { f ->
            checkCancelled()
            if (!f.isFile) return@forEach
            val rel = f.relativeTo(dataDir).path.replace('\\', '/')
            if (rel.isEmpty() || NativeGameImport.skip(rel) || isGalzooBloatRelPath("Data/$rel")) {
                return@forEach
            }
            val destFile = resolveCaseInsensitiveDest(destDataDir, rel)
            if (!destFile.exists()) {
                destFile.parentFile?.mkdirs()
                f.copyTo(destFile, overwrite = false)
            }
        }
        val parent = dataDir.parentFile
        val dataCanon = try { dataDir.canonicalPath } catch (_: Throwable) { dataDir.absolutePath }
        if (dataCanon.startsWith(destCanon + File.separator)) {
            dataDir.deleteRecursively()
            if (parent != null && parent != destGameDir && parent.listFiles()?.isEmpty() == true) {
                parent.delete()
            }
        }
    }
}

internal fun isSystem4GameRoot(dir: File): Boolean {
    val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
    val hasIni = files.any { it.name.equals("System40.ini", true) || it.name.equals("AliceStart.ini", true) }
    val hasAin = files.any { it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true) }
    return hasIni && hasAin
}

internal fun isSystem3GameRoot(dir: File): Boolean {
    val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
    if (isSystem4GameRoot(dir)) return false
    return files.any { it.name.endsWith("sa.ald", true) || it.name.equals("adisk.ald", true) }
}

internal fun findGameRootDirInternal(
    root: File,
    expectedAinName: String?,
    targetGameId: String? = null
): File? {
    fun isBackupOrPatchSubtree(dir: File): Boolean {
        val rel = dir.relativeTo(root).path.replace('\\', '/')
        return rel.split('/').any { seg ->
            seg.equals("backup", ignoreCase = true) ||
                seg.equals("BackData", ignoreCase = true) ||
                seg.equals("OLD", ignoreCase = true) ||
                seg == "补丁" ||
                seg == "汉化补丁" ||
                seg == "解答集" ||
                seg == "备份" ||
                seg == "备份文件" ||
                seg == "原版有码备份" ||
                seg == "无码补丁备份" ||
                seg == "汉化补丁备份" ||
                seg == "特典相关" ||
                seg == "全CG存档" ||
                seg.equals("patchCN", ignoreCase = true) ||
                seg.equals("patchWM", ignoreCase = true)
        }
    }
    val candidates = root.walkTopDown()
        .filter { d ->
            d.isDirectory && (isSystem4GameRoot(d) || isSystem3GameRoot(d))
        }
        .sortedWith(
            compareBy<File>(
                { if (isBackupOrPatchSubtree(it)) 1 else 0 },
                { it.relativeTo(root).path.length }
            )
        )
        .toList()
    if (!expectedAinName.isNullOrBlank()) {
        candidates.firstOrNull { d ->
            d.listFiles()?.any { it.isFile && it.name.equals(expectedAinName, true) } == true
        }?.let { return it }
    }
    if (!targetGameId.isNullOrBlank() && !targetGameId.startsWith(GameCatalog.PREFIX_CUSTOM)) {
        candidates.firstOrNull { d ->
            GameCatalog.classifyGameRootDir(d) == targetGameId
        }?.let { return it }
    }
    return candidates.firstOrNull()
}

internal fun isKichikuouBloatRelPath(relPath: String): Boolean {
    val fileNameLower = relPath.replace('\\', '/').trim('/').substringAfterLast('/').lowercase()
    return fileNameLower == "xsystem35.exe" || fileNameLower == "system35.exe"
}

@JvmOverloads
internal fun isDaiakujiBloatRelPath(relPath: String, skipDDemo0Backup: Boolean = false): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    if (segments.any {
            it.equals("dll", ignoreCase = true) ||
                it.equals("全cg存档", ignoreCase = true)
        }
    ) {
        return true
    }
    val fileNameLower = segments.last().lowercase()
    return fileNameLower.endsWith(".exe") ||
        fileNameLower.endsWith(".dll") ||
        fileNameLower.endsWith(".bat") ||
        fileNameLower == "_inmmcnf.ini" ||
        fileNameLower == "staff.jpg" ||
        fileNameLower == "请读我.jpg" ||
        fileNameLower == "运行游戏前必看进入方法.txt" ||
        fileNameLower == "@vieira.txt" ||
        (skipDDemo0Backup && fileNameLower == "ddemo0.alk")
}

internal fun isEvenicle2BloatRelPath(relPath: String): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    if (segments.any { it.equals("directx", ignoreCase = true) }) {
        return true
    }
    val fileNameLower = segments.last().lowercase()
    return fileNameLower == "evenicle2.exe" ||
        fileNameLower == "opensavefolder.exe" ||
        fileNameLower == "resetconfig.exe" ||
        fileNameLower == "uninstaller.exe" ||
        fileNameLower == "installer.exe" ||
        fileNameLower == "regclean.exe" ||
        fileNameLower == "startmenu.exe" ||
        fileNameLower == "autorun.inf" ||
        fileNameLower == "startmenu.bmp" ||
        fileNameLower == "installer.ini" ||
        fileNameLower == "installfiles.ini"
}

@JvmOverloads
internal fun isEscalayerRebootBloatRelPath(relPath: String, skipArchives: Boolean = true): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    if (segments.any {
            it.equals("directx", ignoreCase = true) ||
                it.equals("dll", ignoreCase = true) ||
                it.equals("备份文件", ignoreCase = true) ||
                it.equals("savedata", ignoreCase = true)
        }
    ) {
        return true
    }
    val fileNameLower = segments.last().lowercase()
    if (fileNameLower.endsWith(".exe") ||
        fileNameLower.endsWith(".dll") ||
        fileNameLower == "autorun.inf" ||
        fileNameLower == "startmenu.bmp" ||
        fileNameLower.endsWith(".docx") ||
        fileNameLower == "战力对比数字对照表.txt" ||
        fileNameLower == "战斗技能表.txt"
    ) {
        return true
    }
    if (fileNameLower.endsWith(".rar")) {
        return skipArchives ||
            fileNameLower.contains("存档") ||
            fileNameLower.contains("补丁")
    }
    return false
}

@JvmOverloads
internal fun isRance06BloatRelPath(relPath: String, skipPatchSubdir: Boolean = true): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    val fileNameLower = segments.last().lowercase()
    if (fileNameLower.endsWith(".exe") ||
        fileNameLower.endsWith(".dll") ||
        fileNameLower.endsWith(".bat") ||
        fileNameLower.endsWith(".cmd") ||
        fileNameLower.endsWith(".lnk") ||
        fileNameLower == "desktop.ini" ||
        fileNameLower == "autorun.inf" ||
        fileNameLower == "system40tcn.ini"
    ) {
        return true
    }
    if (fileNameLower.endsWith(".bak") && !fileNameLower.endsWith(".vanilla.bak")) {
        return true
    }
    val isNestedArchive = ArchiveExtractor.isSupportedArchiveName(fileNameLower)
    val matchDirSeg = { seg: String ->
        seg == "backdata" ||
            seg == "dll" ||
            seg == "cgpatch" ||
            seg == "directx" ||
            seg == "备份" ||
            seg == "备份文件" ||
            ((skipPatchSubdir || !isNestedArchive) && seg == "补丁")
    }
    for (i in 0 until segments.size - 1) {
        if (matchDirSeg(segments[i].lowercase())) {
            return true
        }
    }
    return matchDirSeg(fileNameLower)
}

internal fun isGalzooBloatRelPath(relPath: String): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    val fileNameLower = segments.last().lowercase()
    if (fileNameLower.endsWith(".exe") ||
        fileNameLower.endsWith(".dll") ||
        fileNameLower.endsWith(".bat") ||
        fileNameLower.endsWith(".cmd") ||
        fileNameLower.endsWith(".lnk") ||
        fileNameLower.endsWith(".mrk") ||
        fileNameLower == "galzoo.ico" ||
        fileNameLower == "desktop.ini" ||
        fileNameLower == "autorun.inf"
    ) {
        return true
    }
    if (fileNameLower.endsWith(".bak") && !fileNameLower.endsWith(".vanilla.bak")) {
        return true
    }
    val matchDirSeg = { seg: String ->
        seg == "backdata" ||
            seg == "dll" ||
            seg == "savedata" ||
            seg == "cgpatch" ||
            seg == "directx" ||
            seg == "备份" ||
            seg == "备份文件" ||
            seg == "补丁"
    }
    for (i in 0 until segments.size - 1) {
        if (matchDirSeg(segments[i].lowercase())) {
            return true
        }
    }
    return matchDirSeg(fileNameLower)
}

@JvmOverloads
internal fun isIxsealBloatRelPath(relPath: String, skipPatchCnSubdir: Boolean = true): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    val dirMatch: (String) -> Boolean = { seg ->
        seg == "directx" ||
            seg == "dll" ||
            seg == "backdata" ||
            seg == "savedata" ||
            seg == "备份" ||
            seg == "备份文件" ||
            seg == "原版有码备份" ||
            seg == "无码补丁备份" ||
            seg == "汉化补丁备份" ||
            seg == "特典相关" ||
            seg == "全cg存档" ||
            seg == "patchwm" ||
            (skipPatchCnSubdir && seg == "patchcn")
    }
    for (i in 0 until segments.size - 1) {
        if (dirMatch(segments[i].lowercase())) {
            return true
        }
    }
    val fileNameLower = segments.last().lowercase()
    return fileNameLower.endsWith(".exe") ||
        fileNameLower.endsWith(".dll") ||
        fileNameLower.endsWith(".bat") ||
        fileNameLower.endsWith(".cmd") ||
        fileNameLower.endsWith(".lnk") ||
        fileNameLower == "autorun.inf" ||
        fileNameLower == "desktop.ini" ||
        fileNameLower == "@vieira.txt" ||
        fileNameLower == "game uploaded by the user vieira" ||
        dirMatch(fileNameLower)
}

internal fun isTsumashiboriBloatRelPath(relPath: String): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    val dirMatch: (String) -> Boolean = { seg ->
        seg == "backup" ||
            seg == "backdata" ||
            seg == "dll" ||
            seg == "directx" ||
            seg == "savedata" ||
            seg == "汉化补丁" ||
            seg == "解答集" ||
            seg == "补丁" ||
            seg == "备份" ||
            seg == "备份文件" ||
            seg == "全cg存档"
    }
    for (i in 0 until segments.size - 1) {
        val segLower = ArchiveExtractor.repairSjisAsGbkMojibake(segments[i]).lowercase()
        if (dirMatch(segLower) || dirMatch(segments[i].lowercase())) {
            return true
        }
    }
    val rawLeafLower = segments.last().lowercase()
    val leafLower = ArchiveExtractor.repairSjisAsGbkMojibake(segments.last()).lowercase()
    if (leafLower.endsWith(".bak") && !leafLower.endsWith(".vanilla.bak")) {
        return true
    }
    return leafLower.endsWith(".exe") ||
        leafLower.endsWith(".dll") ||
        leafLower.endsWith(".bat") ||
        leafLower.endsWith(".cmd") ||
        leafLower.endsWith(".lnk") ||
        leafLower == "请读我.txt" ||
        leafLower == "妻しぼり.ico" ||
        rawLeafLower == "嵢偟傏傝.ico" ||
        leafLower == "tsumashibori.ico" ||
        leafLower == "autorun.inf" ||
        leafLower == "desktop.ini" ||
        dirMatch(leafLower) ||
        dirMatch(rawLeafLower)
}

internal fun pruneRedundantBaseBloat(destGameDir: File) {
    val gameId = GameCatalog.classifyDirectory(destGameDir, destGameDir.name)
    if (gameId != GameCatalog.ID_RANCE02 &&
        gameId != GameCatalog.ID_KICHIKUOU &&
        gameId != GameCatalog.ID_DAIAKUJI &&
        gameId != GameCatalog.ID_EVENICLE2 &&
        gameId != GameCatalog.ID_ESCALAYER_REBOOT &&
        gameId != GameCatalog.ID_RANCE06 &&
        gameId != GameCatalog.ID_GALZOO &&
        gameId != GameCatalog.ID_IXSEAL &&
        gameId != GameCatalog.ID_TSUMASHIBORI
    ) {
        return
    }
    val topFiles = destGameDir.listFiles()?.filter { it.isFile }.orEmpty()
    val hasGameData = topFiles.any {
        it.name.endsWith(".ain", ignoreCase = true) ||
            it.name.endsWith(".ald", ignoreCase = true) ||
            it.name.endsWith(".afa", ignoreCase = true)
    }
    if (!hasGameData) return
    val hasDDemoAlk = topFiles.any { it.name.equals("dDemo.alk", ignoreCase = true) }
    val dirsToDelete = mutableListOf<File>()
    val filesToDelete = mutableListOf<File>()
    destGameDir.walkTopDown().forEach { f ->
        if (f == destGameDir) return@forEach
        val rel = f.relativeTo(destGameDir).path.replace('\\', '/')
        if (f.isDirectory) {
            if ((gameId == GameCatalog.ID_RANCE02 && (
                    f.name.equals("CGpatch", ignoreCase = true) ||
                        f.name.equals("补丁", ignoreCase = true) ||
                        f.name.equals("DirectX", ignoreCase = true)
                    )) ||
                (gameId == GameCatalog.ID_EVENICLE2 && f.name.equals("DirectX", ignoreCase = true)) ||
                (gameId == GameCatalog.ID_DAIAKUJI && (
                    f.name.equals("DLL", ignoreCase = true) ||
                        f.name.equals("全CG存档", ignoreCase = true) ||
                        f.name.equals("OLD", ignoreCase = true)
                    )) ||
                (gameId == GameCatalog.ID_ESCALAYER_REBOOT && (
                    f.name.equals("备份文件", ignoreCase = true) ||
                        f.name.equals("DLL", ignoreCase = true) ||
                        f.name.equals("DirectX", ignoreCase = true) ||
                        f.name.equals("SaveData", ignoreCase = true)
                    )) ||
                (gameId == GameCatalog.ID_IXSEAL && isIxsealBloatRelPath(rel)) ||
                (gameId == GameCatalog.ID_TSUMASHIBORI && isTsumashiboriBloatRelPath(rel))
            ) {
                dirsToDelete.add(f)
            } else if ((gameId == GameCatalog.ID_RANCE06 || gameId == GameCatalog.ID_GALZOO) && (
                    f.name.equals("BackData", ignoreCase = true) ||
                        f.name.equals("DLL", ignoreCase = true) ||
                        f.name.equals("CGpatch", ignoreCase = true) ||
                        f.name.equals("DirectX", ignoreCase = true) ||
                        (gameId == GameCatalog.ID_GALZOO && f.name.equals("SaveData", ignoreCase = true)) ||
                        f.name == "补丁" ||
                        f.name == "备份" ||
                        f.name == "备份文件"
                    )
            ) {
                dirsToDelete.add(f)
            }
        } else if (f.isFile && (
                (gameId == GameCatalog.ID_RANCE02 && isBaseInstallBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_KICHIKUOU && isKichikuouBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_DAIAKUJI && isDaiakujiBloatRelPath(rel, skipDDemo0Backup = hasDDemoAlk)) ||
                    (gameId == GameCatalog.ID_EVENICLE2 && isEvenicle2BloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_ESCALAYER_REBOOT && isEscalayerRebootBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_RANCE06 && isRance06BloatRelPath(rel, skipPatchSubdir = true)) ||
                    (gameId == GameCatalog.ID_GALZOO && isGalzooBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_IXSEAL && isIxsealBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_TSUMASHIBORI && isTsumashiboriBloatRelPath(rel))
                )
        ) {
            filesToDelete.add(f)
        }
    }
    for (f in filesToDelete) {
        try { f.delete() } catch (_: Throwable) {}
    }
    for (d in dirsToDelete) {
        try { d.deleteRecursively() } catch (_: Throwable) {}
    }
}
