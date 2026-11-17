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
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

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
        fun fromDirectory(dir: File, homedir: File, context: Context): Item {
            var iniFile = File(dir, "System40.ini")
            if (!iniFile.exists())
                iniFile = File(dir, "AliceStart.ini")
            if (!iniFile.exists()) {
                val ignoreCaseIni = dir.listFiles()?.firstOrNull {
                    it.isFile && (it.name.equals("System40.ini", ignoreCase = true) ||
                        it.name.equals("AliceStart.ini", ignoreCase = true))
                }
                if (ignoreCaseIni != null) {
                    iniFile = ignoreCaseIni
                }
            }
            if (!iniFile.exists()) {
                val err = context.getString(R.string.toast_no_ini, dir.path)
                Log.w("GameList", err)
                return Item(dir.name, dir, homedir, null, null, err)
            }
            val icon = findIcon(dir)
            val ini = System40Ini.parse(iniFile)
            if (ini.gameName == null) {
                val err = context.getString(R.string.toast_no_GameName, iniFile.path)
                Log.w("GameList", err)
                return Item(dir.name, dir, homedir, null, icon, err)
            }
            val savedir = File(context.filesDir, "saves/${dir.name}")
            savedir.mkdirs()
            val modMeta = InstalledModInfo.readOrInfer(dir)
            val hasModMarker = modMeta != null || detectInstalledMod(dir, ini)
            val rawName = ini.gameName.replace("兰斯１０", "兰斯10")
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
                if (it.extension.equals("ico", ignoreCase = true)) { return it }
            }
            try {
                dir.listFiles { file ->
                    file.extension.equals("exe", ignoreCase = true) &&
                            !file.name.equals("OpenSaveFolder.exe", ignoreCase = true) &&
                            !file.name.equals("ResetConfig.exe", ignoreCase = true) &&
                            !file.name.equals("Uninstaller.exe", ignoreCase = true)
                }?.forEach { exeFile ->
                    PEResourceExtractor.create(exeFile)?.extractIcon()?.let { bytes ->
                        val f = File(dir, ".xsystem4.ico")
                        f.writeBytes(bytes)
                        return f
                    }
                }
            } catch (e: Exception) {
                Log.e("GameList", "Failed to extract or write icon", e)
            }
            return null
        }
    }

    fun getIconBitmap(reqSize: Int): Bitmap? {
        val bytes = icon?.readBytes() ?: return null
        val buf = ByteBuffer.wrap(bytes)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.skip(4)
        val numIcons = buf.short.toInt()

        if (numIcons > 1) {
            val bestMatchEntry = Array(numIcons) {
                val entryBytes = ByteArray(16)
                buf.get(entryBytes)
                entryBytes
            }.minWith(compareBy({
                val rawW = it[0].toInt() and 0xff
                val width = if (rawW == 0) 256 else rawW
                kotlin.math.abs(width - reqSize)
            }, {
                -(it[6].toInt() and 0xff)
            }))
            buf.position(4)
            buf.putShort(1)
            buf.put(bestMatchEntry)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

data class System40Ini(val gameName: String?, val SaveFolder: String?) {
    companion object {
        fun parse(file: File): System40Ini {
            val regex = Regex("""(\w+)\s*=\s*"(.*)"""")
            val raw = file.readBytes()
            val latin = String(raw, Charsets.ISO_8859_1)
            val gnMatch = Regex("""(?:^|\r?\n)\s*GameName\s*=\s*"([^\r\n"]*)"""").find(latin)
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
            var SaveFolder: String? = null
            for (line in String(raw, chosenCharset).lines()) {
                regex.matchEntire(line.trim())?.let {
                    when (it.groupValues[1]) {
                        "GameName" -> gameName = ArchiveExtractor.repairSjisAsGbkMojibake(it.groupValues[2])
                        "SaveFolder" -> SaveFolder = ArchiveExtractor.repairSjisAsGbkMojibake(it.groupValues[2])
                    }
                }
            }
            return System40Ini(gameName, SaveFolder)
        }
    }
}

// Game directories whose deletion is still running (absolute paths). Process-wide, so a
// refreshed GameList instance still knows about a deletion started by an older one.
private val uninstallingPaths = HashSet<String>()

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
                if (!path.name.startsWith(".") && path.isDirectory) {
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
            storage.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }?.forEach {
                items.add(Item.fromDirectory(it, File(storage, ".xsystem4"), context))
            }
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

        retiringBarrierJob = scope.launch(Dispatchers.IO) {
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
                    scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
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
                    val imported = NativeGameImport.copyTree(context, uri, storage, expectedAinName) { progress ->
                        if (abortHandle.isAborted) throw CancellationException()
                        withContext(Dispatchers.Main) {
                            setInstallProgress(sessionGen, progress, cancellable = true, resumable = false)
                        }
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

    private fun findGameRootDir(root: File, expectedAinName: String?): File? {
        val candidates = root.walkTopDown()
            .filter { d ->
                d.isDirectory && run {
                    val files = d.listFiles()?.filter { it.isFile }.orEmpty()
                    val hasIni = files.any { it.name.equals("System40.ini", true) || it.name.equals("AliceStart.ini", true) }
                    val hasAin = files.any { it.name.endsWith(".ain", true) }
                    hasIni && hasAin
                }
            }
            .sortedBy { it.relativeTo(root).path.length }
            .toList()
        if (!expectedAinName.isNullOrBlank()) {
            candidates.firstOrNull { d ->
                d.listFiles()?.any { it.isFile && it.name.equals(expectedAinName, true) } == true
            }?.let { return it }
        }
        return candidates.firstOrNull()
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
        checkCancelled: () -> Unit,
        progressCallback: (String) -> Unit
    ): File {
        val tempDir = File(storagePath, ".install_temp_$sessionGen")
        tempDir.deleteRecursively()
        try {
            var currentStageDir = File(tempDir, "stage0")
            ArchiveExtractor.extractUriToDirectory(
                context = context,
                uri = uri,
                destDir = currentStageDir,
                tempScratchParent = storagePath,
                shouldSkip = { rel -> shouldSkipBaseInstallEntry(rel) },
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

            var srcDir: File? = findGameRootDir(currentStageDir, expectedAinName)
            var stage = 1
            while (srcDir == null && stage <= 3) {
                checkCancelled()
                val discs = currentStageDir.walkTopDown()
                    .filter { it.isFile && Iso9660Extractor.isIsoOrMdfName(it.name) }
                    .sortedByDescending { it.length() }
                    .toList()
                val chosenDisc = discs.firstOrNull { Iso9660Extractor.containsGameRoot(it, expectedAinName) }
                    ?: if (expectedAinName.isNullOrBlank()) {
                        discs.firstOrNull { Iso9660Extractor.containsGameRoot(it, null) }
                    } else null

                if (chosenDisc != null) {
                    val nextDir = File(tempDir, "stage$stage")
                    ArchiveExtractor.extractFileToDirectory(
                        archiveFile = chosenDisc,
                        destDir = nextDir,
                        shouldSkip = { rel -> shouldSkipBaseInstallEntry(rel) },
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
                    srcDir = findGameRootDir(currentStageDir, expectedAinName)
                    stage++
                    continue
                }

                val archives = currentStageDir.walkTopDown()
                    .filter {
                        it.isFile &&
                            ArchiveExtractor.isSupportedArchiveName(it.name) &&
                            !shouldSkipBaseInstallEntry(it.name)
                    }
                    .sortedByDescending { it.length() }
                    .toList()
                val chosenArchive = archives.firstOrNull() ?: break
                val nextDir = File(tempDir, "stage$stage")
                ArchiveExtractor.extractFileToDirectory(
                    archiveFile = chosenArchive,
                    destDir = nextDir,
                    shouldSkip = { rel -> shouldSkipBaseInstallEntry(rel) },
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
                srcDir = findGameRootDir(currentStageDir, expectedAinName)
                stage++
            }

            if (srcDir == null) {
                val hasIni = tempDir.walkTopDown().any {
                    it.isFile && (it.name.equals("System40.ini", true) || it.name.equals("AliceStart.ini", true))
                }
                throw InstallFailureException(if (hasIni) R.string.directory_no_game else R.string.zip_no_ini)
            }
            val ainFile = srcDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".ain", true) }
                ?: throw InstallFailureException(R.string.directory_no_game)
            if (!expectedAinName.isNullOrBlank() && !ainFile.name.equals(expectedAinName, ignoreCase = true)) {
                throw InstallFailureException(R.string.import_wrong_game)
            }
            overlayPatchUpdatesIfPresent(listOf(tempDir), srcDir, checkCancelled)
            val baseName = ArchiveExtractor.repairSjisAsGbkMojibake(ainFile.nameWithoutExtension).ifEmpty { "Game" }
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

    fun isUninstalling(item: Item): Boolean = uninstallingPaths.contains(item.path.absolutePath)

    /**
     * Deletes [item]'s directory on an IO thread and drops it from the list when done.
     * A game can be several GB, so this must not run on the main thread. Runs in the
     * GameList scope so leaving the screen doesn't strand a half-deleted entry.
     * Returns false (and does nothing) if the same game is already being deleted.
     */
    fun uninstallAsync(item: Item, onDone: (deleted: Boolean) -> Unit): Boolean {
        val key = item.path.absolutePath
        if (!uninstallingPaths.add(key)) return false
        scope.launch {
            val deleted = try {
                withContext(Dispatchers.IO) {
                    item.path.deleteRecursively()
                    !item.path.exists()
                }
            } catch (t: Throwable) {
                Log.w("GameList", "uninstall failed: ${item.path}", t)
                false
            }
            if (deleted) items.remove(item)
            uninstallingPaths.remove(key)
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
        if (installJob?.isActive == true) return false
        return try {
            restoreVanillaFromMod(item.path)
            refreshTargetItemInList(item)
            true
        } catch (e: Exception) {
            Log.e("GameList", "Failed to restore vanilla game", e)
            false
        }
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
                        isCnPatch = isCnPatch
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
                        isCnPatch = isCnPatch
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

    private fun isModAssetFile(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        return ext == "ain" || ext == "afa" || ext == "ex" || ext == "ald" || ext == "bgi" || ext == "wai" || ext == "fnl"
    }

    private fun isStandaloneFullMod(modRoot: File): Boolean {
        val topFiles = modRoot.listFiles()?.filter { it.isFile } ?: return false
        val hasAin = topFiles.any { it.name.endsWith(".ain", ignoreCase = true) }
        val hasModCg = topFiles.any { it.name.equals("Rance10MODCG.afa", ignoreCase = true) }
        val hasModFlat = topFiles.any { it.name.equals("Rance10ModFlat.afa", ignoreCase = true) }
        return hasAin && hasModCg && hasModFlat
    }

    private fun findModRoot(stagingDir: File, isCnPatch: Boolean = false): File {
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
        throw InstallFailureException(if (isCnPatch) R.string.cn_patch_invalid_package else R.string.mod_invalid_package)
    }

    private fun restoreVanillaFromMod(targetDir: File) {
        InstalledModInfo.delete(targetDir)
        val manifestFile = File(targetDir, ".mod_installed_files.txt")
        if (manifestFile.exists()) {
            for (rel in manifestFile.readLines()) {
                val trimmed = rel.trim()
                if (trimmed.isEmpty() || trimmed.contains("..")) continue
                val f = File(targetDir, trimmed)
                if (f.exists() && f.isFile) {
                    f.delete()
                }
            }
            manifestFile.delete()
        }
        File(targetDir, "AsraTest.ini").delete()
        val bakFiles = targetDir.walkBottomUp().filter { it.isFile && it.name.endsWith(".vanilla.bak") }.toList()
        for (bak in bakFiles) {
            val origName = bak.name.removeSuffix(".vanilla.bak")
            val origFile = File(bak.parentFile, origName)
            if (origFile.exists()) {
                origFile.delete()
            }
            if (!bak.renameTo(origFile)) {
                bak.copyTo(origFile, overwrite = true)
                bak.delete()
            }
        }
    }

    private fun resolveCaseInsensitiveDest(targetDir: File, relPath: String): File {
        val parts = relPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        var cur = targetDir
        for ((idx, seg) in parts.withIndex()) {
            val isLast = idx == parts.lastIndex
            val matched = cur.listFiles()?.firstOrNull { child ->
                if (isLast) {
                    child.isFile && !child.name.endsWith(".vanilla.bak") && child.name.equals(seg, ignoreCase = true)
                } else {
                    child.isDirectory && child.name.equals(seg, ignoreCase = true)
                }
            }
            cur = matched ?: File(cur, seg)
        }
        return cur
    }

    private fun applyModStagingToGameDir(
        stagingDir: File,
        targetDir: File,
        cleanReplaceIfStandalone: Boolean = true,
        isCnPatch: Boolean = false
    ): Boolean {
        val modRoot = findModRoot(stagingDir, isCnPatch = isCnPatch)
        val isFullStandalone = isStandaloneFullMod(modRoot)
        if (cleanReplaceIfStandalone && isFullStandalone && hasInstalledModDir(targetDir)) {
            restoreVanillaFromMod(targetDir)
        }

        val manifestFile = File(targetDir, ".mod_installed_files.txt")
        val addedFiles = linkedSetOf<String>()
        if (manifestFile.exists()) {
            manifestFile.readLines().map { it.trim() }
                .filter { it.isNotEmpty() && !it.contains("..") }
                .forEach { addedFiles.add(it) }
        }
        val modFiles = modRoot.walkTopDown().filter { it.isFile }.toList()
        for (srcFile in modFiles) {
            val relPath = srcFile.relativeTo(modRoot).path.replace('\\', '/')
            if (relPath.isEmpty() || relPath.startsWith(".") || relPath.endsWith(".vanilla.bak") || NativeGameImport.skip(relPath)) {
                continue
            }
            val destFile = resolveCaseInsensitiveDest(targetDir, relPath)
            val destRelPath = destFile.relativeTo(targetDir).path.replace('\\', '/')
            val wasAddedByMod = addedFiles.any { it.equals(destRelPath, ignoreCase = true) }
            destFile.parentFile?.mkdirs()
            if (destFile.exists()) {
                if (wasAddedByMod) {
                    destFile.delete()
                } else {
                    val bakFile = File(destFile.parentFile, "${destFile.name}.vanilla.bak")
                    if (!bakFile.exists()) {
                        if (!destFile.renameTo(bakFile)) {
                            destFile.copyTo(bakFile, overwrite = true)
                            destFile.delete()
                        }
                    } else {
                        destFile.delete()
                    }
                }
            } else if (!wasAddedByMod) {
                addedFiles.add(destRelPath)
            }
            if (!srcFile.renameTo(destFile)) {
                srcFile.copyTo(destFile, overwrite = true)
                srcFile.delete()
            }
        }
        if (File(targetDir, "Rance10.ain").isFile) {
            addedFiles.add("AsraTest.ini")
        }
        File(targetDir, ".mod_installed_files.txt").writeText(addedFiles.joinToString("\n"))
        return isFullStandalone
    }
}
