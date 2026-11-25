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
    @Volatile
    internal var cachedClassifiedId: String? = null
    @Volatile
    internal var cachedHasMod: Boolean? = null
    @Volatile
    internal var cachedModInfoResolved: Boolean = false
    @Volatile
    internal var cachedModInfo: InstalledModInfo? = null
    @Volatile
    internal var cachedIconFingerprint: String? = null
    @Volatile
    internal var cachedHasLocalCnMarkers: Boolean? = null
    @Volatile
    internal var cachedHasExplicitNonCnEdition: Boolean? = null

    internal fun iconFingerprint(): String {
        cachedIconFingerprint?.let { return it }
        val computed = icon?.takeIf { it.isFile }?.let { "${it.lastModified()}:${it.length()}" }.orEmpty()
        cachedIconFingerprint = computed
        return computed
    }

    internal fun invalidateMetadataCache() {
        cachedClassifiedId = null
        cachedHasMod = null
        cachedModInfoResolved = false
        cachedModInfo = null
        cachedIconFingerprint = null
        cachedHasLocalCnMarkers = null
        cachedHasExplicitNonCnEdition = null
    }

    companion object {
        private val iconBitmapCache = object : LruCache<String, Bitmap>(8 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

        fun fromDirectory(dir: File, homedir: File, context: Context): Item {
            val isSys3x = GameCatalog.isSystem3xGameDir(dir)
            val iniFile = resolveLaunchIniFile(dir)
            if (!iniFile.exists() && !isSys3x) {
                val err = context.getString(R.string.toast_no_ini, dir.path)
                Log.w("GameList", err)
                return Item(dir.name, dir, homedir, null, null, err).apply {
                    GameCatalog.classifyItem(this)
                    cachedHasMod = false
                    cachedModInfo = null
                    cachedModInfoResolved = true
                    cachedIconFingerprint = ""
                    cachedHasLocalCnMarkers = GameCatalog.hasLocalChineseMarkers(dir)
                    cachedHasExplicitNonCnEdition = GameCatalog.hasExplicitNonChineseEdition(dir)
                }
            }
            val icon = findIcon(dir)
            val iconFp = icon?.takeIf { it.isFile }?.let { "${it.lastModified()}:${it.length()}" }.orEmpty()
            val ini = if (iniFile.exists()) System40Ini.parse(iniFile) else System40Ini(null, null)
            val fallbackSys3Name = if (isSys3x) {
                when {
                    GameCatalog.isLegacySystem3GameDir(dir) -> GameCatalog.legacySystem3Title(dir) ?: dir.name
                    GameCatalog.isRance04GameDir(dir) -> context.getString(R.string.game_rance04_title)
                    GameCatalog.isRance41GameDir(dir) -> context.getString(R.string.game_rance41_title)
                    GameCatalog.isRance42GameDir(dir) -> context.getString(R.string.game_rance42_title)
                    GameCatalog.isRance5dGameDir(dir) -> context.getString(R.string.game_rance5d_title)
                    GameCatalog.isEscalayerGameDir(dir) -> context.getString(R.string.game_escalayer_title)
                    GameCatalog.isKichikuouGameDir(dir) -> context.getString(R.string.game_kichikuou_title)
                    GameCatalog.isDaiakujiGameDir(dir) -> context.getString(R.string.game_daiakuji_title)
                    GameCatalog.isTsumamiguiGameDir(dir) -> context.getString(R.string.game_tsumamigui_title)
                    GameCatalog.isKaerunyoGameDir(dir) -> context.getString(R.string.game_kaerunyo_title)
                    GameCatalog.isAtlachnachaGameDir(dir) -> context.getString(R.string.game_atlachnacha_title)
                    GameCatalog.isReishikiGameDir(dir) -> context.getString(R.string.game_reishiki_title)
                    else -> dir.name
                }
            } else {
                null
            }
            val fallbackSys4Name = if (!isSys3x && iniFile.exists()) {
                resolveFallbackSystem4Name(dir, ini)
            } else {
                null
            }
            val effectiveGameName = ini.gameName?.takeIf { it.isNotBlank() } ?: fallbackSys3Name ?: fallbackSys4Name
            if (effectiveGameName == null) {
                val err = context.getString(R.string.toast_no_GameName, iniFile.path)
                Log.w("GameList", err)
                return Item(dir.name, dir, homedir, null, icon, err).apply {
                    GameCatalog.classifyItem(this)
                    cachedHasMod = false
                    cachedModInfo = null
                    cachedModInfoResolved = true
                    cachedIconFingerprint = iconFp
                    cachedHasLocalCnMarkers = GameCatalog.hasLocalChineseMarkers(dir)
                    cachedHasExplicitNonCnEdition = GameCatalog.hasExplicitNonChineseEdition(dir)
                }
            }
            val savedir = resolveSaveDir(context.filesDir, dir, ini)
            savedir.mkdirs()
            val modMeta = InstalledModInfo.readOrInfer(dir)
            val hasModMarker = modMeta != null || detectInstalledMod(dir, ini)
            val rawName = effectiveGameName.replace("兰斯１０", "兰斯10")
            val displayName = if (hasModMarker) {
                val bakIni = File(dir, "AliceStart.ini.vanilla.bak").takeIf { it.exists() }
                    ?: File(dir, "System40.ini.vanilla.bak").takeIf { it.exists() }
                val baseName = (bakIni?.let { System40Ini.parse(it).gameName }
                    ?: when {
                        rawName.contains("收费必是倒卖") || rawName.contains("日不落") ||
                            rawName.contains("全系列") || rawName.contains("bfyy", ignoreCase = true) ->
                            "兰斯10 决战"
                        rawName.contains("大战国") || rawName.contains("大戦国") ||
                            rawName.contains("daisengoku", ignoreCase = true) ->
                            "战国兰斯"
                        else -> rawName
                    })
                    .replace("兰斯１０", "兰斯10")
                    .removeSuffix("[MOD]")
                    .trim()
                val verLabel = modMeta?.version?.takeIf { it.isNotBlank() }
                val groupLabel = modMeta?.group?.takeIf { it.isNotBlank() }
                val tag = if (modMeta?.kind == InstalledModInfo.KIND_CN_PATCH) "汉化" else "MOD"
                when {
                    modMeta?.kind != InstalledModInfo.KIND_CN_PATCH &&
                        groupLabel != null &&
                        verLabel != null &&
                        !groupLabel.equals(verLabel, ignoreCase = true) ->
                        "$baseName [$tag · $groupLabel · $verLabel]"
                    verLabel != null ->
                        "$baseName [$tag · $verLabel]"
                    else ->
                        "$baseName [$tag]"
                }
            } else {
                rawName
            }
            return Item(displayName, dir, homedir, savedir, icon, null).apply {
                GameCatalog.classifyItem(this)
                cachedHasMod = hasModMarker
                cachedModInfo = modMeta
                cachedModInfoResolved = true
                cachedIconFingerprint = iconFp
                cachedHasLocalCnMarkers = GameCatalog.hasLocalChineseMarkers(dir)
                cachedHasExplicitNonCnEdition = GameCatalog.hasExplicitNonChineseEdition(dir)
            }
        }

        fun resolveFallbackSystem4Name(dir: File, ini: System40Ini): String? {
            val fileNames = dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
            val matchedCodeFile = GameCatalog.matchConfiguredSystem4CodeFileName(ini.codeName, fileNames)
                ?: return null
            val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(matchedCodeFile)
            return repaired.substringBeforeLast('.').takeIf { it.isNotBlank() }
                ?: repaired.takeIf { it.isNotBlank() }
                ?: dir.name.takeIf { it.isNotBlank() }
        }

        const val NON_VANILLA_BASELINE_MARKER = ".mod_non_vanilla_baseline"
        const val DAISENGOKU_SAVE_DIR_SUFFIX = "__SaveDataDSR"

        fun resolveSaveDir(filesDir: File, gameDir: File, parsedIni: System40Ini? = null): File {
            val ini = parsedIni ?: run {
                val iniFile = File(gameDir, "System40.ini").takeIf { it.isFile }
                    ?: File(gameDir, "AliceStart.ini").takeIf { it.isFile }
                iniFile?.let { System40Ini.parse(it) }
            }
            val suffix = if (ini?.SaveFolder?.equals("SaveDataDSR", ignoreCase = true) == true) {
                DAISENGOKU_SAVE_DIR_SUFFIX
            } else {
                ""
            }
            return File(filesDir, "saves/${gameDir.name}$suffix")
        }

        fun detectInstalledMod(dir: File, parsedIni: System40Ini? = null): Boolean {
            if (File(dir, InstalledModInfo.META_FILE_NAME).isFile ||
                File(dir, ".mod_installed_files.txt").isFile ||
                File(dir, NON_VANILLA_BASELINE_MARKER).isFile
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
                        (lower.startsWith("rance10cgbfyy") && lower.endsWith(".afa")) ||
                        (lower.startsWith("rance10flatbfyy") && lower.endsWith(".afa")) ||
                        lower == "daisengoku.ain" ||
                        lower == "daisengokugm.ald" ||
                        lower == "daisengokubm.ald" ||
                        (lower.endsWith("gm.ald") && (lower.contains("愴崙") || lower.contains("戦国") || lower.contains("sengoku"))) ||
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
                if (ini.SaveFolder?.equals("ModSaveData", ignoreCase = true) == true ||
                    ini.SaveFolder?.equals("SaveDataBFYY", ignoreCase = true) == true ||
                    ini.SaveFolder?.equals("SaveDataDSR", ignoreCase = true) == true
                ) {
                    return true
                }
                val gn = ini.gameName ?: ""
                if (gn.contains("收费必是倒卖") || gn.contains("日不落") ||
                    gn.contains("全系列") || gn.contains("bfyy", ignoreCase = true) ||
                    gn.contains("大战国") || gn.contains("大戦国") ||
                    gn.contains("daisengoku", ignoreCase = true) ||
                    gn.contains("[MOD")
                ) {
                    return true
                }
            }
            return false
        }

        fun canRestoreVanillaDir(dir: File): Boolean {
            if (File(dir, NON_VANILLA_BASELINE_MARKER).isFile) {
                return false
            }
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
            val cacheKey = "${iconFile.absolutePath}:${iconFile.lastModified()}:${iconFile.length()}:$reqSize"
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

data class System40Ini(val gameName: String?, val SaveFolder: String?, val codeName: String? = null) {
    companion object {
        private val ASSIGNMENT_REGEX =
            Regex("""^([A-Za-z][A-Za-z0-9_-]*)\s*=\s*"([^"\r\n]*)"\s*(?:(?:;|//).*)?$""")

        private fun hasUtf8Bom(raw: ByteArray): Boolean =
            raw.size >= 3 &&
                raw[0] == 0xEF.toByte() &&
                raw[1] == 0xBB.toByte() &&
                raw[2] == 0xBF.toByte()

        // Matches native xsystem4 sys4_bytes_look_utf8: strict UTF-8 across the whole file,
        // with either a UTF-8 BOM or at least one 3- or 4-byte UTF-8 sequence (preventing
        // 2-byte GBK characters from being misidentified as 2-byte Latin UTF-8).
        private fun bytesLookUtf8(raw: ByteArray): Boolean {
            if (hasUtf8Bom(raw)) return true
            var hasThreeOrFourByte = false
            var i = 0
            while (i < raw.size) {
                val b0 = raw[i].toInt() and 0xFF
                when {
                    b0 < 0x80 -> i++
                    b0 in 0xC2..0xDF -> {
                        if (i + 1 >= raw.size) return false
                        val b1 = raw[i + 1].toInt() and 0xFF
                        if ((b1 and 0xC0) != 0x80) return false
                        i += 2
                    }
                    b0 in 0xE0..0xEF -> {
                        if (i + 2 >= raw.size) return false
                        val b1 = raw[i + 1].toInt() and 0xFF
                        val b2 = raw[i + 2].toInt() and 0xFF
                        if ((b1 and 0xC0) != 0x80 || (b2 and 0xC0) != 0x80) return false
                        if (b0 == 0xE0 && b1 < 0xA0) return false
                        if (b0 == 0xED && b1 >= 0xA0) return false
                        hasThreeOrFourByte = true
                        i += 3
                    }
                    b0 in 0xF0..0xF4 -> {
                        if (i + 3 >= raw.size) return false
                        val b1 = raw[i + 1].toInt() and 0xFF
                        val b2 = raw[i + 2].toInt() and 0xFF
                        val b3 = raw[i + 3].toInt() and 0xFF
                        if ((b1 and 0xC0) != 0x80 || (b2 and 0xC0) != 0x80 || (b3 and 0xC0) != 0x80) return false
                        if (b0 == 0xF0 && b1 < 0x90) return false
                        if (b0 == 0xF4 && b1 > 0x8F) return false
                        hasThreeOrFourByte = true
                        i += 4
                    }
                    else -> return false
                }
            }
            return hasThreeOrFourByte
        }

        private fun detectLegacyCharset(sampleRaw: ByteArray): Charset {
            val decoded = ArchiveExtractor.decodeBestCjkText(sampleRaw, preferGbkOnTie = true)
            val sjis = Charset.forName("Shift_JIS")
            val gbk = Charset.forName("GBK")
            return if (decoded == String(sampleRaw, sjis) && decoded != String(sampleRaw, gbk)) {
                sjis
            } else {
                gbk
            }
        }

        fun parse(file: File): System40Ini = parse(file.readBytes())

        fun parse(raw: ByteArray): System40Ini {
            val body = if (hasUtf8Bom(raw)) raw.copyOfRange(3, raw.size) else raw
            val chosenCharset = if (bytesLookUtf8(raw)) {
                Charsets.UTF_8
            } else {
                val latin = String(body, Charsets.ISO_8859_1)
                var titleSample: ByteArray? = null
                var otherSample: ByteArray? = null
                for (line in latin.lines()) {
                    val m = ASSIGNMENT_REGEX.matchEntire(line.trim()) ?: continue
                    val rawVal = m.groupValues[2].toByteArray(Charsets.ISO_8859_1)
                    if (rawVal.none { (it.toInt() and 0x80) != 0 }) continue
                    when (m.groupValues[1]) {
                        "GameName", "Regist" -> if (titleSample == null) titleSample = rawVal
                        "CodeName", "SaveFolder" -> if (otherSample == null) otherSample = rawVal
                    }
                }
                val sample = titleSample ?: otherSample
                if (sample != null) detectLegacyCharset(sample) else Charset.forName("GBK")
            }
            var gameName: String? = null
            var registName: String? = null
            var SaveFolder: String? = null
            var codeName: String? = null
            for (line in String(body, chosenCharset).lines()) {
                ASSIGNMENT_REGEX.matchEntire(line.trim())?.let {
                    val rawValue = it.groupValues[2]
                    val value = if (chosenCharset == Charsets.UTF_8) {
                        rawValue
                    } else {
                        ArchiveExtractor.repairSjisAsGbkMojibake(rawValue)
                    }
                    when (it.groupValues[1]) {
                        "GameName" -> gameName = value
                        "Regist" -> registName = value
                        "SaveFolder" -> SaveFolder = value
                        "CodeName" -> codeName = value.trim()
                    }
                }
            }
            return System40Ini(gameName ?: registName, SaveFolder, codeName)
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
    private var syncJob: Job? = null
    private var retiringBarrierJob: Job? = null
    private var activeSessionGen: Long = 0L
    private var activeAbortHandle: InstallAbortHandle? = null
    private var activeEvictPartial: (() -> List<File>)? = null
    private var diskSnapshot: Map<String, DirSignature> = emptyMap()
    @Volatile
    var dataVersion: Long = 1L
        private set
    operator fun get(index: Int): Item = items[index]
    val size: Int get() = items.size

    internal data class DirSignature(
        val dirModified: Long,
        val launchIniName: String,
        val launchIniModified: Long,
        val launchIniLength: Long,
        val modVersionIniName: String,
        val modVersionIniModified: Long,
        val modVersionIniLength: Long,
        val system3IniName: String,
        val system3IniModified: Long,
        val system3IniLength: Long,
        val extraMetadataFingerprints: List<String>,
        val iconName: String,
        val iconModified: Long,
        val iconLength: Long,
        val modMetaModified: Long,
        val modMetaLength: Long,
        val journalModified: Long,
        val journalLength: Long,
        val saveDirModified: Long,
        val saveDirEntries: List<String>
    )

    companion object {
        internal fun computeDirSignature(dir: File, saveDir: File? = null): DirSignature {
            val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
            val launchIni = resolveLaunchIniFile(dir).takeIf { it.isFile }
            val modVersionIni = OnlineInstaller.resolveModVersionIniFile(dir)
            val sys3Ini = files.firstOrNull { it.name == "System3.ini" }
                ?: files.firstOrNull { it.name.equals("system3.ini", ignoreCase = true) }
            val extraMeta = files.mapNotNull { f ->
                val lower = f.name.lowercase()
                if (lower.endsWith(".ini") ||
                    lower.endsWith(".chs") ||
                    lower.endsWith(".vanilla.bak") ||
                    lower == "rance10.ain"
                ) {
                    "${f.name}:${f.lastModified()}:${f.length()}"
                } else {
                    null
                }
            }.sorted()
            val iconFile = files.firstOrNull { it.extension.equals("ico", ignoreCase = true) }
            val modMeta = File(dir, InstalledModInfo.META_FILE_NAME).takeIf { it.isFile }
            val journal = File(dir, ".mod_installed_files.txt").takeIf { it.isFile }
            val saveDirExists = saveDir != null && saveDir.isDirectory
            val saveEntries = if (saveDirExists) {
                saveDir!!.listFiles()?.map { entry ->
                    if (entry.isDirectory) "D:${entry.name}" else "F:${entry.name}"
                }?.sorted().orEmpty()
            } else {
                emptyList()
            }
            return DirSignature(
                dirModified = dir.lastModified(),
                launchIniName = launchIni?.name.orEmpty(),
                launchIniModified = launchIni?.lastModified() ?: 0L,
                launchIniLength = launchIni?.length() ?: 0L,
                modVersionIniName = modVersionIni?.name.orEmpty(),
                modVersionIniModified = modVersionIni?.lastModified() ?: 0L,
                modVersionIniLength = modVersionIni?.length() ?: 0L,
                system3IniName = sys3Ini?.name.orEmpty(),
                system3IniModified = sys3Ini?.lastModified() ?: 0L,
                system3IniLength = sys3Ini?.length() ?: 0L,
                extraMetadataFingerprints = extraMeta,
                iconName = iconFile?.name.orEmpty(),
                iconModified = iconFile?.lastModified() ?: 0L,
                iconLength = iconFile?.length() ?: 0L,
                modMetaModified = modMeta?.lastModified() ?: 0L,
                modMetaLength = modMeta?.length() ?: 0L,
                journalModified = journal?.lastModified() ?: 0L,
                journalLength = journal?.length() ?: 0L,
                saveDirModified = if (saveDirExists) saveDir!!.lastModified() else 0L,
                saveDirEntries = saveEntries
            )
        }
    }

    private fun discoverMountedStorageDirs(
        knownRoots: Set<String> = emptySet(),
        createDummyIfEmpty: Boolean = false
    ): List<Pair<File, Boolean>> {
        val result = ArrayList<Pair<File, Boolean>>()
        for (storagePath in appContext.getExternalFilesDirs(null).filterNotNull()) {
            val state = Environment.getExternalStorageState(storagePath)
            if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                continue
            }
            val writable = state == Environment.MEDIA_MOUNTED
            result.add(storagePath to writable)
            if (writable && storagePath.absolutePath !in knownRoots) {
                OnlineInstaller.recoverInterruptedReinstalls(storagePath)
            }
            if (createDummyIfEmpty && writable) {
                val files = storagePath.listFiles()
                if (files != null && files.isEmpty()) {
                    val dummyFile = File(storagePath, "dummy.txt")
                    dummyFile.writeText("Create a subfolder here that stores game files.")
                    MediaScannerConnection.scanFile(
                        appContext,
                        arrayOf(dummyFile.absolutePath),
                        null,
                        null
                    )
                }
            }
        }
        if (result.isEmpty()) {
            val storage = File(appContext.filesDir, "games")
            storage.mkdirs()
            result.add(storage to true)
        }
        return result
    }

    private fun bumpDataVersion() {
        dataVersion++
    }

    private fun resolveSaveDirFor(dir: File): File = Item.resolveSaveDir(appContext.filesDir, dir)

    private fun recordItemSignature(item: Item) {
        val next = HashMap(diskSnapshot)
        next[item.path.absolutePath] = computeDirSignature(item.path, item.savedir ?: resolveSaveDirFor(item.path))
        diskSnapshot = next
    }

    private fun removeItemSignature(absPath: String) {
        if (absPath in diskSnapshot) {
            val next = HashMap(diskSnapshot)
            next.remove(absPath)
            diskSnapshot = next
        }
    }
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
        val discovered = discoverMountedStorageDirs(knownRoots = emptySet(), createDummyIfEmpty = true)
        for ((storagePath, _) in discovered) {
            storageDirs.add(storagePath)
            Rance10InstanceManager.cleanupStaleCloneStaging(storagePath)
            val homedir = File(storagePath, ".xsystem4")
            val files = storagePath.listFiles() ?: continue
            for (path in files) {
                if (!path.name.startsWith(".") && path.isDirectory && !uninstallingPaths.contains(path.absolutePath)) {
                    items.add(Item.fromDirectory(path, homedir, context))
                }
            }
        }
        val initialSnap = HashMap<String, DirSignature>(items.size)
        for (item in items) {
            initialSnap[item.path.absolutePath] = computeDirSignature(item.path, item.savedir ?: resolveSaveDirFor(item.path))
        }
        diskSnapshot = initialSnap
    }

    fun syncFromDiskAsync(onChanged: () -> Unit) {
        if (installState !is InstallState.Idle || installJob?.isActive == true || isRestoringMod) return
        if (syncJob?.isActive == true) return
        val startVersion = dataVersion
        val prevRoots = storageDirs.map { it.absolutePath }
        val prevRootSet = prevRoots.toHashSet()
        val existingByPath = HashMap<String, Item>(items.size)
        for (item in items) {
            existingByPath[item.path.absolutePath] = item
        }
        val prevSnap = diskSnapshot
        syncJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                val discovered = discoverMountedStorageDirs(knownRoots = prevRootSet, createDummyIfEmpty = false)
                val newStorageDirs = discovered.map { it.first }
                val newRootPaths = newStorageDirs.map { it.absolutePath }
                val scannedEntries = ArrayList<Pair<File, File>>() // (gameDir, homedir)
                val newSnap = HashMap<String, DirSignature>()
                for (storagePath in newStorageDirs) {
                    val homedir = File(storagePath, ".xsystem4")
                    val files = storagePath.listFiles() ?: continue
                    for (path in files) {
                        if (!path.name.startsWith(".") && path.isDirectory && !uninstallingPaths.contains(path.absolutePath)) {
                            scannedEntries.add(path to homedir)
                            val saveDir = existingByPath[path.absolutePath]?.savedir ?: resolveSaveDirFor(path)
                            newSnap[path.absolutePath] = computeDirSignature(path, saveDir)
                        }
                    }
                }
                if (newRootPaths == prevRoots && newSnap == prevSnap && newSnap.size == existingByPath.size) {
                    return@withContext null
                }
                val rebuilt = ArrayList<Item>(scannedEntries.size)
                val ctx = context
                for ((dir, homedir) in scannedEntries) {
                    val key = dir.absolutePath
                    val prevItem = existingByPath[key]
                    if (prevItem != null && prevSnap[key] == newSnap[key]) {
                        rebuilt.add(prevItem)
                    } else {
                        val rebuiltItem = Item.fromDirectory(dir, homedir, ctx)
                        rebuilt.add(rebuiltItem)
                        // Refresh signature in case Item.fromDirectory extracted .xsystem4.ico or created savedir.
                        newSnap[key] = computeDirSignature(dir, rebuiltItem.savedir ?: resolveSaveDirFor(dir))
                    }
                }
                Triple(newStorageDirs, rebuilt, newSnap)
            }
            if (result != null &&
                dataVersion == startVersion &&
                installState is InstallState.Idle &&
                installJob?.isActive != true &&
                !isRestoringMod
            ) {
                storageDirs.clear()
                storageDirs.addAll(result.first)
                items.clear()
                items.addAll(result.second)
                diskSnapshot = result.third
                bumpDataVersion()
                onChanged()
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
                // A re-install refreshes the existing directory in place; don't list it twice.
                items.removeAll { it.path.canonicalFile == path.canonicalFile }
                items.add(created)
                recordItemSignature(created)
                bumpDataVersion()
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

    fun allExistingDirectoryNames(): Set<String> {
        val names = HashSet<String>()
        val roots = LinkedHashSet<File>(storageDirs)
        for ((mountedRoot, _) in discoverMountedStorageDirs(knownRoots = emptySet(), createDummyIfEmpty = false)) {
            roots.add(mountedRoot)
        }
        for (root in roots) {
            root.listFiles()?.forEach { child ->
                if (!child.name.startsWith(".")) {
                    names.add(child.name)
                }
            }
        }
        for (item in items) {
            names.add(item.path.name)
        }
        return names
    }

    fun cloneRance10Directory(
        sourceItem: Item,
        targetDirName: String,
        cleanVanilla: Boolean
    ): Boolean {
        if (installState is InstallState.Installing || installJob?.isActive == true || isRestoringMod) {
            return false
        }
        val parentDir = sourceItem.path.parentFile ?: storageDirs[0]
        val extraUsedNames = allExistingDirectoryNames()
        val validation = Rance10InstanceManager.validateDirName(parentDir, targetDirName, extraUsedNames)
        if (!validation.isValid) {
            return false
        }
        val finalName = validation.sanitizedName
        val stagingRef = java.util.concurrent.atomic.AtomicReference<File?>(null)
        val committedDirRef = java.util.concurrent.atomic.AtomicReference<File?>(null)
        val prevBarrier = retiringBarrierJob
        val (sessionGen, abortHandle) = beginInstallSession(
            notifTitle = context.getString(R.string.hub_create_instance_notif_title),
            initialProgress = context.getString(R.string.hub_create_instance_preparing, finalName),
            initialPermille = 0,
            cancellable = true,
            resumable = false,
            targetGameId = GameCatalog.ID_RANCE10,
            evictPartialOnCancel = {
                listOfNotNull(stagingRef.get(), committedDirRef.get())
                    .mapNotNull { OnlineInstaller.evictFileToTrash(it) }
            }
        )
        val mainHandler = Handler(Looper.getMainLooper())
        installJob = scope.launch {
            try {
                val createdDir = withContext(Dispatchers.IO) {
                    try {
                        prevBarrier?.join()
                    } catch (_: Throwable) {
                    }
                    val myJob = kotlin.coroutines.coroutineContext[Job]
                    val plan = Rance10InstanceManager.planCopy(sourceItem.path, cleanVanilla)
                    var lastReportMs = 0L
                    Rance10InstanceManager.executeCopy(
                        plan = plan,
                        parentDir = parentDir,
                        targetDirName = finalName,
                        onStagingDirCreated = { stagingRef.set(it) },
                        checkCancelled = {
                            if (abortHandle.isAborted || myJob?.isActive == false) {
                                throw CancellationException()
                            }
                        },
                        onProgress = { done, total, relFile ->
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastReportMs >= 100L || done >= total) {
                                lastReportMs = now
                                val pct = ((done * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 100)
                                val pm = ((done * 1000L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 1000)
                                val text = context.getString(
                                    R.string.hub_create_instance_progress,
                                    finalName,
                                    pct,
                                    OnlineInstaller.formatSize(done),
                                    OnlineInstaller.formatSize(total),
                                    relFile
                                )
                                mainHandler.post {
                                    if (!abortHandle.isAborted && myJob?.isActive == true) {
                                        setInstallProgress(
                                            sessionGen,
                                            text,
                                            pm,
                                            cancellable = true,
                                            resumable = false
                                        )
                                    }
                                }
                            }
                        },
                        extraUsedNames = extraUsedNames,
                        onFinalDirCommitted = { committedDirRef.set(it) }
                    )
                }
                committedDirRef.set(null)
                val homedir = File(parentDir, ".xsystem4")
                val created = Item.fromDirectory(createdDir, homedir, context)
                items.removeAll { it.path.canonicalFile == createdDir.canonicalFile }
                items.add(created)
                recordItemSignature(created)
                bumpDataVersion()
                lastInstalledItem = created
                GameHubActivity.persistSelectedInstancePath(context, GameCatalog.ID_RANCE10, created.path.path)
                setInstallSucceeded(sessionGen)
            } catch (_: CancellationException) {
                val staged = stagingRef.get()
                if (staged != null && staged.exists()) {
                    deleteDirAsyncViaTrash(staged)
                }
                val committed = committedDirRef.getAndSet(null)
                if (committed != null && committed.exists()) {
                    deleteDirAsyncViaTrash(committed)
                }
                setInstallCancelled(sessionGen, wasPaused = false)
            } catch (e: InstallFailureException) {
                setInstallFailed(sessionGen, e.msgId)
            } catch (e: Exception) {
                Log.e("GameList", "Failed to clone Rance 10 directory", e)
                setInstallFailed(sessionGen, R.string.hub_create_instance_failed)
            }
        }
        return true
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
                        classifiedId == GameCatalog.ID_RANCE04 ||
                        classifiedId == GameCatalog.ID_KICHIKUOU ||
                        classifiedId == GameCatalog.ID_DAIAKUJI ||
                        classifiedId == GameCatalog.ID_EVENICLE2 ||
                        classifiedId == GameCatalog.ID_ESCALAYER_REBOOT ||
                        classifiedId == GameCatalog.ID_RANCE06 ||
                        classifiedId == GameCatalog.ID_GALZOO ||
                        classifiedId == GameCatalog.ID_IXSEAL ||
                        classifiedId == GameCatalog.ID_HARUKA ||
                        classifiedId == GameCatalog.ID_TSUMASHIBORI ||
                        classifiedId == GameCatalog.ID_TSUMAMIGUI3
                    ) {
                        pruneRedundantBaseBloat(imported)
                    }
                    maybeRecordPreinstalledModMeta(imported, uri)
                    imported
                }
                val created = Item.fromDirectory(path, File(storage, ".xsystem4"), context)
                items.add(created)
                recordItemSignature(created)
                bumpDataVersion()
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
                recordItemSignature(created)
                bumpDataVersion()
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
        mergeSiblingMultiDiscDirsInternal(searchRoot, primaryDir, checkCancelled)
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
        val isHarukaTarget = targetGameId == GameCatalog.ID_HARUKA
        val isTsumashiboriTarget = targetGameId == GameCatalog.ID_TSUMASHIBORI
        val isTsumamigui3Target = targetGameId == GameCatalog.ID_TSUMAMIGUI3
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
                        (isHarukaTarget && isHarukaBloatRelPath(rel)) ||
                        (isTsumashiboriTarget && isTsumashiboriBloatRelPath(rel)) ||
                        (isTsumamigui3Target && isTsumamigui3BloatRelPath(rel))
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
                    Iso9660Extractor.containsGameRoot(it, expectedAinName, targetGameId, checkCancelled)
                } ?: if (expectedAinName.isNullOrBlank() &&
                    (targetGameId.isNullOrBlank() || targetGameId.startsWith(GameCatalog.PREFIX_CUSTOM))
                ) {
                    discs.firstOrNull { Iso9660Extractor.containsGameRoot(it, null, null, checkCancelled) }
                } else {
                    null
                }
                if (chosenDisc == null && discs.any { Iso9660Extractor.containsGameRoot(it, null, null, checkCancelled) }) {
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
                                (isHarukaTarget && isHarukaBloatRelPath(rel)) ||
                                (isTsumashiboriTarget && isTsumashiboriBloatRelPath(rel)) ||
                        (isTsumamigui3Target && isTsumamigui3BloatRelPath(rel))
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
                            (!isHarukaTarget || !isHarukaBloatRelPath(rel)) &&
                            (!isTsumashiboriTarget || !isTsumashiboriBloatRelPath(rel)) &&
                            (!isTsumamigui3Target || !isTsumamigui3BloatRelPath(rel))
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
                            (isHarukaTarget && isHarukaBloatRelPath(rel)) ||
                            (isTsumashiboriTarget && isTsumashiboriBloatRelPath(rel)) ||
                        (isTsumamigui3Target && isTsumamigui3BloatRelPath(rel))
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
                    it.isFile && (GameCatalog.isSystem4LaunchIni(it.name) ||
                        it.name.equals("System39.ini", true))
                }
                throw InstallFailureException(if (hasIni) R.string.directory_no_game else R.string.zip_no_ini)
            }
            mergeSiblingMultiDiscDirs(tempDir, srcDir, checkCancelled)
            val srcFiles = srcDir.listFiles()?.filter { it.isFile }.orEmpty()
            val srcFileNames = srcFiles.map { it.name }
            val ainFile = srcFiles.firstOrNull { GameCatalog.isSystem4AinFile(it.name) }
                ?: srcFiles.firstOrNull {
                    GameCatalog.isSystem4CodeFile(it.name, srcFileNames) { name ->
                        GameCatalog.readSmallRootFile(srcDir, name)
                    }
                }
            val sys3Scenario = srcFiles.firstOrNull { GameCatalog.isSystem35Scenario(it.name) }
            val legacySystem3 = GameCatalog.isLegacySystem3GameFiles(srcFiles.map { it.name })
            if (ainFile == null && sys3Scenario == null && !legacySystem3) {
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
            if (!legacySystem3) {
                mergeInstallSetData(listOf(tempDir, srcDir), srcDir, skipRance02Bloat = isRance02, checkCancelled = checkCancelled)
            }
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
                classifiedId == GameCatalog.ID_RANCE04 ||
                classifiedId == GameCatalog.ID_KICHIKUOU ||
                classifiedId == GameCatalog.ID_DAIAKUJI ||
                classifiedId == GameCatalog.ID_EVENICLE2 ||
                classifiedId == GameCatalog.ID_ESCALAYER_REBOOT ||
                classifiedId == GameCatalog.ID_RANCE06 ||
                classifiedId == GameCatalog.ID_GALZOO ||
                classifiedId == GameCatalog.ID_IXSEAL ||
                classifiedId == GameCatalog.ID_HARUKA ||
                classifiedId == GameCatalog.ID_TSUMASHIBORI ||
                classifiedId == GameCatalog.ID_TSUMAMIGUI3
            ) {
                pruneRedundantBaseBloat(srcDir)
            }
            val baseName = when {
                classifiedId == GameCatalog.ID_YAKATA2 -> "Yakata2"
                classifiedId == GameCatalog.ID_SDPSMARIA -> "SuperDpsMaria"
                classifiedId == GameCatalog.ID_GAKUENKING -> "GakuenKING"
                classifiedId == GameCatalog.ID_ONLYYOU -> "OnlyYou"
                classifiedId == GameCatalog.ID_AYUMI -> "AyumiChan"
                classifiedId == GameCatalog.ID_DPSALL -> "DpsAll"
                classifiedId == GameCatalog.ID_PROSTUDENTG -> "ProstudentG"
                classifiedId == GameCatalog.ID_YAKATA3 -> "Yakata3"
                legacySystem3 -> sanitizeDerivedGameDirName(
                    (GameCatalog.legacySystem3Title(srcDir) ?: srcDir.name).take(64),
                    fallback = "System3"
                )
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
                classifiedId == GameCatalog.ID_HARUKA -> "Haruka"
                classifiedId == GameCatalog.ID_TSUMASHIBORI -> "Tsumashibori"
                classifiedId == GameCatalog.ID_TSUMAMIGUI -> "Tsumamigui"
                classifiedId == GameCatalog.ID_TSUMAMIGUI3 -> "Tsumamigui3"
                classifiedId == GameCatalog.ID_DAIBANCHOU -> "Daibanchou"
                classifiedId == GameCatalog.ID_DAITEIKOKU -> "Daiteikoku"
                classifiedId == GameCatalog.ID_KAERUNYO -> "Kaerunyo"
                classifiedId == GameCatalog.ID_ATLACHNACHA -> "AtlachNacha"
                classifiedId == GameCatalog.ID_REISHIKI -> "Reishiki"
                classifiedId == GameCatalog.ID_HARUURARE -> "Haruurare"
                classifiedId == GameCatalog.ID_OKUSAMAJUN -> "OkusamaJun"
                classifiedId == GameCatalog.ID_OKUSAMANTR -> "OkusamaNtr"
                classifiedId == GameCatalog.ID_EVENICLE2RANCE -> "Evenicle2Rance"
                classifiedId == GameCatalog.ID_EVENICLEGAIDEN -> "EvenicleGaiden"
                classifiedId == GameCatalog.ID_WAIDONYO -> "Waidonyo"
                classifiedId == GameCatalog.ID_RANCE6AFTER -> "Rance6After"
                classifiedId == GameCatalog.ID_TOUSHIN3 -> "Toushin3"
                classifiedId == GameCatalog.ID_OYAKORANKAN -> "Oyakorankan"
                classifiedId == GameCatalog.ID_YORUGAKURU -> "Yorugakuru"
                classifiedId == GameCatalog.ID_RANCE1DIGEST -> "Rance1Digest"
                classifiedId == GameCatalog.ID_MAMAN -> "Maman"
                classifiedId == GameCatalog.ID_DIABOLIQUE -> "DiaboLiQuE"
                classifiedId == GameCatalog.ID_GAKUENKINGEX -> "GakuenKINGEx"
                classifiedId == GameCatalog.ID_OUDOUYUUSHA -> "OudouYuusha"
                classifiedId == GameCatalog.ID_TOUSHIN1 -> "Toushin1"
                classifiedId == GameCatalog.ID_TOUSHIN2 -> "Toushin2"
                classifiedId == GameCatalog.ID_DARCROWS -> "DARCROWS"
                classifiedId == GameCatalog.ID_IKUSAMIKO -> "IkusaMiko"
                classifiedId == GameCatalog.ID_SANBIKI -> "Sanbiki"
                classifiedId == GameCatalog.ID_PERSIOM -> "PERSIOM"
                classifiedId == GameCatalog.ID_SEEINAO -> "SeeInAO"
                classifiedId == GameCatalog.ID_HUSHABY -> "HushabyBaby"
                classifiedId == GameCatalog.ID_CIELCLAIRE -> "CielClaire"
                classifiedId == GameCatalog.ID_NIGHTDEMON -> "NightDemon"
                classifiedId == GameCatalog.ID_KATSUMI -> "Katsumi"
                classifiedId == GameCatalog.ID_DRSTOP -> "DrStop"
                classifiedId == GameCatalog.ID_KOREDPS -> "KoreDps"
                classifiedId == GameCatalog.ID_KAERUALICE -> "KaeruAlice"
                classifiedId == GameCatalog.ID_PROSTUDENTGOOD -> "ProstudentGood"
                classifiedId == GameCatalog.ID_PASTELCHIME -> "PastelChime"
                classifiedId == GameCatalog.ID_PASTELCHIME2 -> "PastelChime2"
                classifiedId == GameCatalog.ID_DALK -> "DALK"
                classifiedId == GameCatalog.ID_NINGENGARI -> "NingenGari"
                classifiedId == GameCatalog.ID_PASCHACPP -> "PaschaCpp"
                classifiedId == GameCatalog.ID_PASCHA3 -> "Pascha3"
                classifiedId == GameCatalog.ID_MAMATOTO -> "Mamatoto"
                classifiedId == GameCatalog.ID_ONLYYOURECROSS -> "OnlyYouReCross"
                classifiedId == GameCatalog.ID_MAMORIGAMI -> "Mamorigami"
                classifiedId == GameCatalog.ID_AMBIVALENZ -> "AmbivalenZ"
                classifiedId == GameCatalog.ID_FUNNYBEE -> "FunnyBee"
                classifiedId == GameCatalog.ID_TOUSHIN2SP -> "Toushin2Sp"
                classifiedId == GameCatalog.ID_MUGENHOUYOU -> "MugenHouyou"
                classifiedId == GameCatalog.ID_OTOMESENKI -> "OtomeSenki"
                classifiedId == GameCatalog.ID_GAKUENHYOURYUU -> "GakuenHyouryuu"
                classifiedId == GameCatalog.ID_MAIDNOSUSUME -> "MaidNoSusume"
                classifiedId == GameCatalog.ID_DALKGAIDEN -> "DalkGaiden"
                classifiedId == GameCatalog.ID_MAMANYONYO -> "Mamanyonyo"
                classifiedId == GameCatalog.ID_MAJONOSHOKUZAI -> "MajoNoShokuzai"
                classifiedId == GameCatalog.ID_SABOTEN -> "Saboten"
                classifiedId == GameCatalog.ID_DOUBLESENSEI -> "DoubleSensei"
                classifiedId == GameCatalog.ID_OJOUSAMA -> "Ojousama"
                classifiedId == GameCatalog.ID_EDORANZE -> "Edoranze"
                classifiedId == GameCatalog.ID_VANISH -> "Vanish"
                classifiedId == GameCatalog.ID_HOKENSHITSU -> "Hokenshitsu"
                classifiedId == GameCatalog.ID_KAMUNAGARA -> "Kamunagara"
                classifiedId == GameCatalog.ID_ERUMEIFLOWER -> "ErumeiFlower"
                classifiedId == GameCatalog.ID_ERUMEIWAKARE -> "ErumeiWakare"
                classifiedId == GameCatalog.ID_MANANA1 -> "Manana1"
                classifiedId == GameCatalog.ID_MANANA2 -> "Manana2"
                classifiedId == GameCatalog.ID_MANANA3 -> "Manana3"
                classifiedId == GameCatalog.ID_MANANA4 -> "Manana4"
                classifiedId == GameCatalog.ID_MANANA5 -> "Manana5"
                classifiedId == GameCatalog.ID_TSUMASAKURA -> "TsumaSakura"
                classifiedId == GameCatalog.ID_SHAMANSSANCTUARY -> "ShamansSanctuary"
                classifiedId == GameCatalog.ID_ALIVEZ -> "AliveZ"
                classifiedId == GameCatalog.ID_DUNGEONSDOLLS -> "DungeonsDolls"
                classifiedId == GameCatalog.ID_SUZUMIYA -> "Suzumiya"
                classifiedId == GameCatalog.ID_SHIMAIMA -> "Shimaima"
                classifiedId == GameCatalog.ID_DAIBANCHOUGAIDEN -> "DaibanchouGaiden"
                classifiedId == GameCatalog.ID_COSPLAYLEMON -> "CosplayLemon"
                classifiedId == GameCatalog.ID_WHIPHONEY -> "WhipHoney"
                classifiedId == GameCatalog.ID_ONIMUSU -> "Onimusu"
                classifiedId == GameCatalog.ID_PROGRX -> "ProGrx"
                classifiedId == GameCatalog.ID_DPSVERN -> "DpsVerN"
                classifiedId == GameCatalog.ID_SUKIIRONO -> "Sukiirono"
                classifiedId == GameCatalog.ID_GIRLS -> "Girls"
                classifiedId == GameCatalog.ID_DEBUT -> "Debut"
                classifiedId == GameCatalog.ID_HARUKAVSESCA -> "HarukaVsEsca"
                classifiedId == GameCatalog.ID_HARUKANINJA -> "HarukaNinja"
                classifiedId == GameCatalog.ID_MEGANE -> "Megane"
                classifiedId == GameCatalog.ID_RACHELSAKURA -> "RachelSakura"
                classifiedId == GameCatalog.ID_SABAKUNIKA -> "SabakuNika"
                classifiedId == GameCatalog.ID_VANISHPALM -> "VanishPalm"
                classifiedId == GameCatalog.ID_BLADEBRIDERS -> "BladeBriders"
                classifiedId == GameCatalog.ID_DORAPEKO -> "Dorapeko"
                classifiedId == GameCatalog.ID_HANIWAPETIT -> "HaniwaPetit"
                classifiedId == GameCatalog.ID_YAKATA456 -> "Yakata456"
                classifiedId == GameCatalog.ID_UMINIOCHITE -> "UmiNiOchite"
                classifiedId == GameCatalog.ID_GAKUENHYOURYUU2 -> "GakuenHyouryuu2"
                classifiedId == GameCatalog.ID_YOUSEI -> "Yousei"
                classifiedId == GameCatalog.ID_TOBIRA -> "Tobira"
                classifiedId == GameCatalog.ID_BUROBURO -> "Buroburo"
                classifiedId == GameCatalog.ID_KUDAKERO -> "Kudakero"
                classifiedId == GameCatalog.ID_LITTLEVAMPIRE -> "LittleVampire"
                classifiedId == GameCatalog.ID_INTRUDER -> "Intruder"
                classifiedId == GameCatalog.ID_CRESCENT -> "CrescentMoonGirl"
                classifiedId == GameCatalog.ID_TENGU -> "AbunaiTengu"
                classifiedId == GameCatalog.ID_DPS -> "DPS"
                classifiedId == GameCatalog.ID_YAKATA1 -> "Yakata1"
                classifiedId == GameCatalog.ID_SDPSTONO -> "SuperDpsTono"
                classifiedId == GameCatalog.ID_SDPSKAIZOKU -> "SuperDpsKaizoku"
                classifiedId == GameCatalog.ID_PROGOMAKE -> "ProstudentGOmake"
                classifiedId == GameCatalog.ID_AYUMIHINT -> "AyumiHint"
                classifiedId == GameCatalog.ID_AYUMILIVE -> "AyumiLive"
                classifiedId == GameCatalog.ID_NISENAGURI -> "NiseNaguri"
                classifiedId == GameCatalog.ID_CAROL -> "Carol"
                classifiedId == GameCatalog.ID_OTHELLOT -> "Othellot"
                classifiedId == GameCatalog.ID_MASQUERADE -> "Masquerade"
                classifiedId == GameCatalog.ID_DEKIRUKANA -> "Dekirukana"
                classifiedId == GameCatalog.ID_TATAKIKOROSHI -> "Tatakikoroshi"
                classifiedId == GameCatalog.ID_BALLOONPOP -> "BalloonPop"
                classifiedId == GameCatalog.ID_HIYOKO2 -> "Hiyoko2"
                classifiedId == GameCatalog.ID_SELFGAME -> "SelfGame"
                classifiedId == GameCatalog.ID_MOMOIROGUARDIAN -> "MomoiroGuardian"
                classifiedId == GameCatalog.ID_RANCE1CLASSIC -> "Rance1Classic"
                classifiedId == GameCatalog.ID_RANCE2CLASSIC -> "Rance2Classic"
                classifiedId == GameCatalog.ID_RANCE3CLASSIC -> "Rance3Classic"
                classifiedId == GameCatalog.ID_DPSSGFAHREN -> "DpsSgFahren"
                classifiedId == GameCatalog.ID_DPSSGKATEI -> "DpsSgKatei"
                classifiedId == GameCatalog.ID_DPSSGNOBUNAGA -> "DpsSgNobunaga"
                classifiedId == GameCatalog.ID_DPSSG2ANTIQUE -> "DpsSg2Antique"
                classifiedId == GameCatalog.ID_DPSSG2IKENAI -> "DpsSg2Ikenai"
                classifiedId == GameCatalog.ID_DPSSG2AKAI -> "DpsSg2Akai"
                classifiedId == GameCatalog.ID_DPSSG3RABBIT -> "DpsSg3Rabbit"
                classifiedId == GameCatalog.ID_DPSSG3SHINKON -> "DpsSg3Shinkon"
                classifiedId == GameCatalog.ID_DPSSG3SOTSUGYOU -> "DpsSg3Sotsugyou"
                classifiedId == GameCatalog.ID_RANCE5D -> "Rance5D"
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
                    removeItemSignature(key)
                    bumpDataVersion()
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

    fun hasInstalledMod(item: Item): Boolean {
        item.cachedHasMod?.let { return it }
        val detected = hasInstalledModDir(item.path)
        item.cachedHasMod = detected
        return detected
    }

    fun canRestoreVanilla(item: Item): Boolean = Item.canRestoreVanillaDir(item.path)

    private fun maybeRecordPreinstalledModMeta(gameDir: File, srcUri: Uri) {
        if (!hasInstalledModDir(gameDir)) {
            return
        }
        if (!Item.canRestoreVanillaDir(gameDir)) {
            val marker = File(gameDir, Item.NON_VANILLA_BASELINE_MARKER)
            if (!marker.isFile) {
                try {
                    val tmp = File(gameDir, "${Item.NON_VANILLA_BASELINE_MARKER}.tmp")
                    tmp.outputStream().use { out ->
                        out.write("preinstalled-mod\n".toByteArray(Charsets.UTF_8))
                        out.flush()
                        out.fd.sync()
                    }
                    if (!tmp.renameTo(marker)) {
                        tmp.delete()
                    }
                } catch (t: Throwable) {
                    Log.w("GameList", "Failed to write non-vanilla baseline marker", t)
                }
            }
        }
        if (File(gameDir, InstalledModInfo.META_FILE_NAME).isFile) {
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
                    installedAtMs = System.currentTimeMillis(),
                    group = OnlineInstaller.inferModGroup(srcLabel, gameDir)
                )
            )
        }
    }

    fun getInstalledModInfo(item: Item): InstalledModInfo? {
        if (item.cachedModInfoResolved) return item.cachedModInfo
        val info = InstalledModInfo.readOrInfer(item.path)
        item.cachedModInfo = info
        item.cachedModInfoResolved = true
        return info
    }

    fun refreshItemMetadata(item: Item) {
        refreshTargetItemInList(item)
    }

    fun restoreMod(item: Item): Boolean {
        if (installState is InstallState.Installing || installJob?.isActive == true || isRestoringMod) return false
        if (!canRestoreVanilla(item)) return false
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
        if (!canRestoreVanilla(item)) {
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
                    if (!isCnPatch && Item.detectInstalledMod(targetItem.path) &&
                        !Item.canRestoreVanillaDir(targetItem.path)
                    ) {
                        val existingGroup = prevInfo?.group.orEmpty()
                            .ifBlank { OnlineInstaller.inferModGroup("", targetItem.path) }
                        val incomingGroup = OnlineInstaller.inferModGroup(srcLabel, null)
                        if (OnlineInstaller.groupsConflict(incomingGroup, existingGroup)) {
                            throw InstallFailureException(R.string.uninstall_mod_no_backup_msg)
                        }
                    }
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
                    val inferredGroup = if (isCnPatch) {
                        ""
                    } else {
                        OnlineInstaller.inferModGroup(srcLabel, targetItem.path)
                            .ifEmpty { if (wasFull) "" else prevInfo?.group.orEmpty() }
                    }
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
                            kind = overlayKind,
                            group = inferredGroup
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
                    if (!isCnPatch && Item.detectInstalledMod(targetItem.path) &&
                        !Item.canRestoreVanillaDir(targetItem.path)
                    ) {
                        val existingGroup = prevInfo?.group.orEmpty()
                            .ifBlank { OnlineInstaller.inferModGroup("", targetItem.path) }
                        val incomingGroup = OnlineInstaller.inferModGroup(srcLabel, null)
                        if (OnlineInstaller.groupsConflict(incomingGroup, existingGroup)) {
                            throw InstallFailureException(R.string.uninstall_mod_no_backup_msg)
                        }
                    }
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
                    val inferredGroup = if (isCnPatch) {
                        ""
                    } else {
                        OnlineInstaller.inferModGroup(srcLabel, targetItem.path)
                            .ifEmpty { if (wasFull) "" else prevInfo?.group.orEmpty() }
                    }
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
                            kind = overlayKind,
                            group = inferredGroup
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
        val notifLabel = if (targetGroup.isNotBlank()) {
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
                                val prevInfo = InstalledModInfo.readOrInfer(targetItem.path)
                                if (!isCnPatch && Item.detectInstalledMod(targetItem.path) &&
                                    !Item.canRestoreVanillaDir(targetItem.path)
                                ) {
                                    val existingGroup = prevInfo?.group.orEmpty()
                                        .ifBlank { OnlineInstaller.inferModGroup("", targetItem.path) }
                                    if (OnlineInstaller.groupsConflict(release.group, existingGroup)) {
                                        throw InstallFailureException(R.string.uninstall_mod_no_backup_msg)
                                    }
                                }
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
                                val resolvedGroup = release.group.ifBlank {
                                    if (!release.isPatch || wasFull) "" else prevInfo?.group.orEmpty()
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
                                        kind = overlayKind,
                                        group = resolvedGroup
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
        targetItem.invalidateMetadataCache()
        val idx = items.indexOfFirst { it.path == targetItem.path }
        if (idx >= 0) {
            val refreshed = Item.fromDirectory(targetItem.path, targetItem.homedir, context)
            items[idx] = refreshed
            recordItemSignature(refreshed)
            bumpDataVersion()
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
    val hasRibuluo = topFiles.any { it.name.equals("Rance10MODCG.afa", ignoreCase = true) } &&
        topFiles.any { it.name.equals("Rance10ModFlat.afa", ignoreCase = true) }
    val hasBfyy = topFiles.any {
        it.name.startsWith("Rance10CGBFYY", ignoreCase = true) && it.name.endsWith(".afa", ignoreCase = true)
    } && topFiles.any {
        it.name.equals("Rance10FlatBFYY.afa", ignoreCase = true)
    }
    val hasDaiSengoku = topFiles.any {
        it.name.equals("DaiSengokuGA.ald", ignoreCase = true) ||
            it.name.equals("DaiSengokuGM.ald", ignoreCase = true) ||
            it.name.endsWith("GM.ald", ignoreCase = true)
    }
    return hasAin && (hasRibuluo || hasBfyy || hasDaiSengoku)
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
            if (safeInside && (!f.exists() || f.isFile)) {
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
    val nonVanillaBaseline = !isCnPatch &&
        Item.detectInstalledMod(targetDir) &&
        !Item.canRestoreVanillaDir(targetDir)
    if (nonVanillaBaseline) {
        val existingGroup = InstalledModInfo.readOrInfer(targetDir)?.group.orEmpty()
            .ifBlank { OnlineInstaller.inferModGroup("", targetDir) }
        val incomingGroup = OnlineInstaller.inferModGroup("", modRoot)
        if (OnlineInstaller.groupsConflict(incomingGroup, existingGroup)) {
            throw GameList.InstallFailureException(R.string.uninstall_mod_no_backup_msg)
        }
        val marker = File(targetDir, Item.NON_VANILLA_BASELINE_MARKER)
        if (!marker.isFile) {
            val markerTmp = File(targetDir, "${Item.NON_VANILLA_BASELINE_MARKER}.tmp")
            markerTmp.outputStream().use { out ->
                out.write("preinstalled-mod\n".toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            if (!markerTmp.renameTo(marker)) {
                markerTmp.delete()
                throw IOException("Failed to commit non-vanilla baseline marker: $marker")
            }
        }
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
    var persistedFiles: Set<String>? = null
    fun persistAddedFiles() {
        if (addedFiles == persistedFiles) return
        val manifestTmp = File(targetDir, ".mod_installed_files.txt.tmp")
        manifestTmp.outputStream().use { out ->
            out.write(addedFiles.joinToString("\n").toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (!manifestTmp.renameTo(manifestFile)) {
            throw IOException("Failed to commit mod file journal: $manifestFile")
        }
        persistedFiles = addedFiles.toSet()
    }
    val acceptedFiles = modFiles.mapNotNull { srcFile ->
        val relPath = srcFile.relativeTo(modRoot).path.replace('\\', '/')
        if (relPath.isEmpty() ||
            relPath.startsWith(".") ||
            relPath.contains("..") ||
            relPath.endsWith(".vanilla.bak") ||
            relPath.endsWith(".vanilla.bak.tmp") ||
            NativeGameImport.skip(relPath) ||
            (isEvenicle2Target && isEvenicle2CnPatchJunkRelPath(relPath))
        ) {
            null
        } else {
            srcFile to relPath
        }
    }
    val addedFileNames = java.util.TreeSet(String.CASE_INSENSITIVE_ORDER).apply { addAll(addedFiles) }
    var failure: Throwable? = null
    try {
        for ((_, relPath) in acceptedFiles) {
            val destFile = resolveCaseInsensitiveDest(targetDir, relPath)
            destFile.parentFile?.mkdirs()
            val destRelPath = destFile.relativeTo(targetDir).path.replace('\\', '/')
            if (!destFile.exists() && addedFileNames.add(destRelPath)) {
                addedFiles.add(destRelPath)
            }
        }
        if (addedFiles.isNotEmpty() || manifestFile.exists()) {
            persistAddedFiles()
        }
        for ((srcFile, relPath) in acceptedFiles) {
            val destFile = resolveCaseInsensitiveDest(targetDir, relPath)
            val destRelPath = destFile.relativeTo(targetDir).path.replace('\\', '/')
            val wasAddedByMod = addedFileNames.contains(destRelPath)
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
    } catch (t: Throwable) {
        failure = t
        throw t
    } finally {
        if (addedFiles.isNotEmpty() || manifestFile.exists()) {
            try {
                persistAddedFiles()
            } catch (t: Throwable) {
                if (failure != null) {
                    failure.addSuppressed(t)
                } else {
                    throw t
                }
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

internal fun resolveLaunchIniFile(dir: File): File {
    val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
    val hasHaruCnAin = files.any { it.name.equals("haru_c.ain", ignoreCase = true) }
    val hasChsTbl = files.any { it.name.equals("CHS.TBL", ignoreCase = true) }
    fun findIni(name: String): File? = files.firstOrNull { it.name == name }
        ?: files.firstOrNull { it.name.equals(name, ignoreCase = true) }
    if (hasHaruCnAin) {
        findIni("AliceStart2.ini")?.let { return it }
    }
    if (hasChsTbl) {
        findIni("AliceStart.CHS")?.let { return it }
    }
    return findIni("System40.ini")
        ?: findIni("AliceStart.ini")
        ?: findIni("AliceStart2.ini")
        ?: findIni("AliceStart.CHS")
        ?: findIni("System39.ini")
        ?: File(dir, "System40.ini")
}

internal fun normalizeSystem35ScenarioIdentity(name: String): String {
    val lower = name.lowercase()
    return if (lower.endsWith("_chs.ald")) {
        lower.removeSuffix("_chs.ald") + ".ald"
    } else {
        lower
    }
}

internal fun mergeSiblingMultiDiscDirsInternal(
    searchRoot: File,
    primaryDir: File,
    checkCancelled: () -> Unit = {}
) {
    if (!GameCatalog.isSystem3xGameDir(primaryDir)) return
    val primaryCanonical = primaryDir.canonicalPath
    val primaryScenario = primaryDir.listFiles()?.firstOrNull {
        it.isFile && GameCatalog.isSystem35Scenario(it.name)
    }?.name?.let(::normalizeSystem35ScenarioIdentity)
    val dataExts = setOf("ald", "bgi", "wai", "alk", "s3l", "ini", "ain", "gr", "tbl")
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
                GameCatalog.isSystem35Scenario(it.name)
            }?.name?.let(::normalizeSystem35ScenarioIdentity)
            if (primaryScenario != null && otherScenario != null &&
                otherScenario != primaryScenario
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

internal fun preferCnRootOverInstallData(candidate: File?): File? {
    if (candidate != null &&
        candidate.name.equals("InstallData", ignoreCase = true) &&
        candidate.parentFile?.name.equals("InstallSet", ignoreCase = true)
    ) {
        val outerCnRoot = candidate.parentFile?.parentFile
        if (outerCnRoot != null && outerCnRoot.isDirectory &&
            outerCnRoot.listFiles()?.any {
                it.isFile && it.name.endsWith(".ain", ignoreCase = true) &&
                    !it.name.equals("System39.ain", ignoreCase = true)
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
    val fileNames = files.map { it.name }
    val hasIni = fileNames.any { GameCatalog.isSystem4LaunchIni(it) }
    val hasAin = fileNames.any {
        GameCatalog.isSystem4CodeFile(it, fileNames) { name ->
            GameCatalog.readSmallRootFile(dir, name)
        }
    }
    return hasIni && hasAin
}

internal fun isSystem3GameRoot(dir: File): Boolean {
    val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
    if (isSystem4GameRoot(dir)) return false
    return files.any { GameCatalog.isSystem35Scenario(it.name) } ||
        GameCatalog.isLegacySystem3GameFiles(files.map { it.name })
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
                seg == "広告" ||
                seg == "广告" ||
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

internal fun isRance04BloatRelPath(relPath: String): Boolean =
    relPath.replace('\\', '/').substringAfterLast('/').equals("RANCE4CN.EXE", true)

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

internal fun isHarukaBloatRelPath(relPath: String): Boolean {
    val norm = ArchiveExtractor.repairSjisAsGbkMojibake(relPath).replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    val dirMatch: (String) -> Boolean = { seg ->
        seg == "directx" ||
            seg == "dll" ||
            seg == "backdata" ||
            seg == "savedata" ||
            seg == "広告" ||
            seg == "广告" ||
            seg == "备份" ||
            seg == "备份文件" ||
            seg == "特典相关" ||
            seg == "全cg存档"
    }
    for (i in 0 until segments.size - 1) {
        if (dirMatch(segments[i].lowercase())) {
            return true
        }
    }
    val fileNameLower = segments.last().lowercase()
    if (fileNameLower.endsWith(".bak") && !fileNameLower.endsWith(".vanilla.bak")) {
        return true
    }
    return fileNameLower.endsWith(".exe") ||
        fileNameLower.endsWith(".dll") ||
        fileNameLower.endsWith(".bat") ||
        fileNameLower.endsWith(".cmd") ||
        fileNameLower.endsWith(".lnk") ||
        fileNameLower == "setup.bmp" ||
        fileNameLower == "autorun.inf" ||
        fileNameLower == "desktop.ini" ||
        fileNameLower == "超昂閃忍ハルカ.ico" ||
        fileNameLower == "超昂闪忍遥.ico" ||
        fileNameLower == "haruka.ico" ||
        fileNameLower == "超昂閃忍ハルカdm.dat" ||
        fileNameLower == "超昂闪忍遥dm.dat" ||
        fileNameLower == "harukadm.dat" ||
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

internal fun isTsumamigui3BloatRelPath(relPath: String): Boolean {
    val norm = relPath.replace('\\', '/').trim('/')
    if (norm.isEmpty()) return false
    val segments = norm.split('/').filter { it.isNotEmpty() }
    val dirMatch: (String) -> Boolean = { seg ->
        seg == "backup" ||
            seg == "backdata" ||
            seg == "dll" ||
            seg == "directx" ||
            seg == "savedata" ||
            seg == "manual" ||
            seg == "installinfo" ||
            seg == "license" ||
            seg == "补丁" ||
            seg == "汉化补丁" ||
            seg == "攻略" ||
            seg == "备份" ||
            seg == "备份文件" ||
            seg == "全cg存档" ||
            seg.endsWith("汉化原版")
    }
    for (i in 0 until segments.size - 1) {
        if (dirMatch(segments[i].lowercase())) return true
    }
    val leafLower = segments.last().lowercase()
    if (leafLower.endsWith(".bak") && !leafLower.endsWith(".vanilla.bak")) {
        return true
    }
    return leafLower.endsWith(".exe") ||
        leafLower.endsWith(".dll") ||
        leafLower.endsWith(".bat") ||
        leafLower.endsWith(".cmd") ||
        leafLower.endsWith(".lnk") ||
        leafLower.endsWith(".rar去掉") ||
        (leafLower.startsWith("读我") && leafLower.endsWith(".txt")) ||
        leafLower == "tsumamigui3.ico" ||
        leafLower == "autorun.inf" ||
        leafLower == "desktop.ini" ||
        dirMatch(leafLower)
}

internal fun normalizeRance06DungeonData(gameDir: File) {
    val data = gameDir.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("Data", ignoreCase = true) } ?: return
    val entries = data.listFiles().orEmpty()
    if (entries.any { it.name.equals("DungeonData.dlf", ignoreCase = true) }) return
    val copyName = Regex("DungeonData\\([0-9]+\\)\\.dlf", RegexOption.IGNORE_CASE)
    val source = entries.filter { it.isFile && copyName.matches(it.name) }.minByOrNull { it.name } ?: return
    if (!source.renameTo(File(data, "DungeonData.dlf"))) {
        throw IOException("Cannot normalize dungeon data: ${source.name}")
    }
}

internal fun pruneRedundantBaseBloat(destGameDir: File) {
    val gameId = GameCatalog.classifyDirectory(destGameDir, destGameDir.name)
    if (gameId == GameCatalog.ID_RANCE06) normalizeRance06DungeonData(destGameDir)
    if (gameId != GameCatalog.ID_RANCE02 &&
        gameId != GameCatalog.ID_RANCE04 &&
        gameId != GameCatalog.ID_KICHIKUOU &&
        gameId != GameCatalog.ID_DAIAKUJI &&
        gameId != GameCatalog.ID_EVENICLE2 &&
        gameId != GameCatalog.ID_ESCALAYER_REBOOT &&
        gameId != GameCatalog.ID_RANCE06 &&
        gameId != GameCatalog.ID_GALZOO &&
        gameId != GameCatalog.ID_IXSEAL &&
        gameId != GameCatalog.ID_HARUKA &&
        gameId != GameCatalog.ID_TSUMASHIBORI &&
        gameId != GameCatalog.ID_TSUMAMIGUI3
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
                (gameId == GameCatalog.ID_HARUKA && isHarukaBloatRelPath(rel)) ||
                (gameId == GameCatalog.ID_TSUMASHIBORI && isTsumashiboriBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_TSUMAMIGUI3 && isTsumamigui3BloatRelPath(rel))
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
                    (gameId == GameCatalog.ID_RANCE04 && isRance04BloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_KICHIKUOU && isKichikuouBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_DAIAKUJI && isDaiakujiBloatRelPath(rel, skipDDemo0Backup = hasDDemoAlk)) ||
                    (gameId == GameCatalog.ID_EVENICLE2 && isEvenicle2BloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_ESCALAYER_REBOOT && isEscalayerRebootBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_RANCE06 && isRance06BloatRelPath(rel, skipPatchSubdir = true)) ||
                    (gameId == GameCatalog.ID_GALZOO && isGalzooBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_IXSEAL && isIxsealBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_HARUKA && isHarukaBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_TSUMASHIBORI && isTsumashiboriBloatRelPath(rel)) ||
                    (gameId == GameCatalog.ID_TSUMAMIGUI3 && isTsumamigui3BloatRelPath(rel))
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
