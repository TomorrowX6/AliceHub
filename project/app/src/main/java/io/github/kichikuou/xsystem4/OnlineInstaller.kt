package io.github.kichikuou.xsystem4

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class ModDownloadRequest(
    val url: String,
    val fileName: String,
    val contentLength: Long,
    val expectedSha256: String? = null
)

data class ModReleaseEntry(
    val fileId: Int,
    val version: String,
    val fileName: String,
    val summary: String,
    val changelog: String,
    val uploadedAt: String,
    val downloadUrl: String,
    val isPatch: Boolean,
    val baseFileId: Int?,
    val baseVersion: String?,
    val expectedSize: Long = -1L,
    val expectedSha256: String? = null,
    val group: String = ""
) {
    val formattedDate: String
        get() = uploadedAt.replace('T', ' ').take(16)

    val isFullMarked: Boolean
        get() = version.contains("完整") || fileName.contains("完整") || version.contains("正式版")
}

data class InstalledModInfo(
    val version: String,
    val baseVersion: String?,
    val fileId: Int,
    val baseFileId: Int,
    val fileName: String,
    val uploadedAt: String,
    val source: String,
    val installedAtMs: Long,
    val kind: String = KIND_MOD
) {
    val formattedDate: String
        get() = uploadedAt.replace('T', ' ').take(16)

    companion object {
        const val META_FILE_NAME = ".mod_version.json"
        const val KIND_MOD = "mod"
        const val KIND_CN_PATCH = "cn_patch"

        fun read(gameDir: File): InstalledModInfo? {
            val f = File(gameDir, META_FILE_NAME)
            if (!f.isFile) return null
            return try {
                val j = JSONObject(f.readText(Charsets.UTF_8))
                val rawVer = j.optString("version", "").trim()
                if (rawVer.isEmpty()) return null
                val rawBaseVer = j.optString("baseVersion", "").trim().takeIf { it.isNotEmpty() }
                val source = j.optString("source", "online").trim().ifEmpty { "online" }
                val fileName = j.optString("fileName", "").trim()
                val kind = j.optString("kind", KIND_MOD).trim().ifEmpty { KIND_MOD }
                val isPatchStored = rawBaseVer != null && rawBaseVer != rawVer
                val isPatchHint = when {
                    rawBaseVer == null -> null
                    isPatchStored -> true
                    else -> false
                }
                val ver = if (source == "online" || kind == KIND_CN_PATCH) {
                    rawVer
                } else {
                    OnlineInstaller.inferVersionFromFileName(
                        fileName = rawVer.ifEmpty { fileName },
                        isPatchHint = isPatchHint,
                        fallbackIniDir = if (!isPatchStored) gameDir else null
                    )
                }
                val baseVer = when {
                    rawBaseVer == null -> null
                    source == "online" || kind == KIND_CN_PATCH -> rawBaseVer
                    else -> OnlineInstaller.inferVersionFromFileName(
                        fileName = rawBaseVer,
                        isPatchHint = false,
                        fallbackIniDir = gameDir
                    )
                }
                InstalledModInfo(
                    version = ver,
                    baseVersion = baseVer,
                    fileId = j.optInt("fileId", -1),
                    baseFileId = j.optInt("baseFileId", -1),
                    fileName = fileName,
                    uploadedAt = j.optString("uploadedAt", "").trim(),
                    source = source,
                    installedAtMs = j.optLong("installedAtMs", 0L),
                    kind = kind
                )
            } catch (_: Throwable) {
                null
            }
        }

        fun readOrInfer(gameDir: File): InstalledModInfo? {
            read(gameDir)?.let { return it }
            if (!Item.detectInstalledMod(gameDir)) return null
            val inferred = OnlineInstaller.extractCanonicalModVersionFromDir(gameDir) ?: return null
            return InstalledModInfo(
                version = inferred,
                baseVersion = inferred,
                fileId = -1,
                baseFileId = -1,
                fileName = "",
                uploadedAt = "",
                source = "preinstalled",
                installedAtMs = 0L
            )
        }

        fun write(gameDir: File, info: InstalledModInfo) {
            try {
                val j = JSONObject()
                    .put("version", info.version)
                    .put("baseVersion", info.baseVersion ?: "")
                    .put("fileId", info.fileId)
                    .put("baseFileId", info.baseFileId)
                    .put("fileName", info.fileName)
                    .put("uploadedAt", info.uploadedAt)
                    .put("source", info.source)
                    .put("installedAtMs", info.installedAtMs)
                    .put("kind", info.kind)
                File(gameDir, META_FILE_NAME).writeText(j.toString(), Charsets.UTF_8)
            } catch (_: Throwable) {
            }
        }

        fun delete(gameDir: File) {
            try {
                File(gameDir, META_FILE_NAME).delete()
            } catch (_: Throwable) {
            }
        }
    }
}

data class ModUpdatePlan(
    val targetRelease: ModReleaseEntry,
    val steps: List<ModReleaseEntry>,
    val isIncrementalOnly: Boolean
)

data class OnlineProgressUpdate(
    val text: String,
    val permille: Int? = null
)

class InstallAbortHandle {
    private val lock = Any()
    private var aborted = false
    private val connections = HashSet<HttpURLConnection>()
    private val workerThreads = HashSet<Thread>()

    val isAborted: Boolean
        get() = synchronized(lock) { aborted }

    fun register(conn: HttpURLConnection) {
        val curThread = Thread.currentThread()
        val disconnectImmediately = synchronized(lock) {
            if (aborted) {
                true
            } else {
                connections.add(conn)
                workerThreads.add(curThread)
                false
            }
        }
        if (disconnectImmediately) {
            disconnectAsync(listOf(conn))
            throw CancellationException()
        }
    }

    fun unregister(conn: HttpURLConnection?) {
        val curThread = Thread.currentThread()
        val wasAborted = synchronized(lock) {
            if (conn != null) {
                connections.remove(conn)
            }
            workerThreads.remove(curThread)
            aborted
        }
        if (wasAborted) {
            // Clear interrupt flag before returning worker thread to Dispatchers.IO pool
            Thread.interrupted()
        }
    }

    fun abort() {
        val (threads, toClose) = synchronized(lock) {
            aborted = true
            val tCopy = workerThreads.toList()
            val cCopy = connections.toList()
            workerThreads.clear()
            connections.clear()
            tCopy to cCopy
        }
        for (t in threads) {
            try {
                t.interrupt()
            } catch (_: Throwable) {
            }
        }
        if (toClose.isNotEmpty()) {
            disconnectAsync(toClose)
        }
    }

    companion object {
        fun disconnectAsync(conns: List<HttpURLConnection>) {
            Thread({
                for (c in conns) {
                    try {
                        c.disconnect()
                    } catch (_: Throwable) {
                    }
                }
            }, "InstallHttpAbort").apply {
                isDaemon = true
                start()
            }
        }
    }
}

object OnlineInstaller {
    const val RANCE10_CDN_URL = "https://alicehub.000.moe/rance10"
    const val DOHNA_DOHNA_CDN_URL = "https://alicehub.000.moe/dohnadohna"
    const val EVENICLE_CDN_URL = "https://alicehub.000.moe/evenicle"
    const val EVENICLE2_CDN_URL = "https://alicehub.000.moe/evenicle2"
    const val SENGOKU_RANCE_CDN_URL = "https://alicehub.000.moe/sengokurance"
    const val RANCE01_CDN_URL = "https://alicehub.000.moe/rance01"
    const val RANCE02_CDN_URL = "https://alicehub.000.moe/rance02"
    const val RANCE03_CDN_URL = "https://alicehub.000.moe/rance03"
    const val RANCE06_CDN_URL = "https://alicehub.000.moe/rance06"
    const val RANCE08_CDN_URL = "https://alicehub.000.moe/rance08"
    const val RANCE09_CDN_URL = "https://alicehub.000.moe/rance09"
    const val KICHIKUOU_CDN_URL = "https://alicehub.000.moe/kichikuou"
    const val DAIAKUJI_CDN_URL = "https://alicehub.000.moe/daiakuji"
    const val ESCALAYER_CDN_URL = "https://alicehub.000.moe/escalayer"
    const val ESCALAYER_REBOOT_CDN_URL = "https://alicehub.000.moe/escalayerreboot"
    const val GALZOO_CDN_URL = "https://alicehub.000.moe/galzoo"
    const val IXSEAL_CDN_URL = "https://alicehub.000.moe/ixseal"
    const val HARUKA_CDN_URL = "https://alicehub.000.moe/haruka"
    const val TSUMASHIBORI_CDN_URL = "https://alicehub.000.moe/tsumashibori"
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val HEADROOM_BYTES = 64L * 1024L * 1024L
    private const val MAX_HTTP_RETRIES = 8
    private const val MAX_REDIRECTS = 6
    private const val READ_SLICE_TIMEOUT_MS = 3_000
    private const val TOTAL_READ_TIMEOUT_MS = 45_000L

    private data class ManifestEntry(
        val path: String,
        val key: String,
        val size: Long,
        val sha256: String,
        val extract: Boolean
    )

    private data class OnlineManifest(
        val gameName: String,
        val baseName: String,
        val totalBytes: Long,
        val entries: List<ManifestEntry>
    )

    private data class SavedMeta(
        val etag: String?,
        val totalBytes: Long
    )

    private val copySuffixRegex = Regex("""(?:\s*[\(（]\d+[\)）]|\s*[-_]?\s*副本)+$""")

    private fun stripArchiveExtAndCopySuffix(raw: String): String {
        var s = raw.trim()
        for (ext in arrayOf(".tar.gz", ".tar.xz", ".rar", ".7z", ".zip", ".tgz", ".txz")) {
            if (s.lowercase(Locale.ROOT).endsWith(ext)) {
                s = s.substring(0, s.length - ext.length).trim()
                break
            }
        }
        s = copySuffixRegex.replace(s, "").trim()
        return s
    }

    fun normalizeModVersionKey(raw: String): String {
        var s = stripArchiveExtAndCopySuffix(raw.lowercase(Locale.ROOT))
        s = s.replace("(", "")
            .replace(")", "")
            .replace("（", "")
            .replace("）", "")
            .replace("[", "")
            .replace("]", "")
            .replace(" ", "")
            .replace("_", "")
        var changed = true
        while (changed) {
            changed = false
            for (prefix in arrayOf(
                "兰斯10日不落mod",
                "rance10日不落mod",
                "日不落mod",
                "日不落",
                "兰斯10mod",
                "rance10mod",
                "兰斯10",
                "rance10"
            )) {
                if (s.startsWith(prefix)) {
                    s = s.removePrefix(prefix).trimStart('-', '.')
                    changed = true
                }
            }
        }
        if (s.startsWith("v") && s.length > 1 && s[1].isDigit()) {
            s = s.substring(1)
        }
        s = s.replace("bug修复补丁", "补丁")
            .replace("修复补丁", "补丁")
            .replace("增量补丁", "补丁")
            .replace("patch", "补丁")
        if (!s.contains("补丁")) {
            var suffixChanged = true
            while (suffixChanged) {
                suffixChanged = false
                for (suffix in arrayOf("完整版", "正式版", "完整包", "整合版", "本体", "更新")) {
                    if (s.endsWith(suffix) && s.length > suffix.length) {
                        s = s.removeSuffix(suffix).trimEnd('-', '.')
                        suffixChanged = true
                    }
                }
            }
        }
        return s
    }

    private val modVersionNumberRegex = Regex("""v?(\d+\.\d+(?:\.\d+)?[a-zA-Z]?)""", RegexOption.IGNORE_CASE)
    private val patchSeqRegex = Regex("""(?:补丁|patch|hotfix)\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun extractCanonicalModVersionFromDir(gameDir: File): String? {
        if (!gameDir.isDirectory) return null
        val iniFile = File(gameDir, "AliceStart.ini").takeIf { it.isFile }
            ?: File(gameDir, "System40.ini").takeIf { it.isFile }
            ?: gameDir.listFiles()?.firstOrNull {
                it.isFile && (it.name.equals("AliceStart.ini", ignoreCase = true) ||
                    it.name.equals("System40.ini", ignoreCase = true))
            }
        val gn = iniFile?.let {
            try {
                System40Ini.parse(it).gameName
            } catch (_: Throwable) {
                null
            }
        }
        if (!gn.isNullOrBlank()) {
            extractCanonicalModVersion(gn, isPatchHint = false)?.let { return it }
        }
        return extractCanonicalModVersion(gameDir.name, isPatchHint = false)
    }

    private fun extractCanonicalModVersion(raw: String, isPatchHint: Boolean?): String? {
        val base = stripArchiveExtAndCopySuffix(
            raw.substringAfterLast('/').substringAfterLast('\\').trim()
        )
        if (base.isEmpty()) return null

        var cleaned = base
            .replace('（', ' ')
            .replace('）', ' ')
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('【', ' ')
            .replace('】', ' ')
            .replace("收费必是倒卖", " ")
            .replace("免费分享", " ")
            .replace("严禁倒卖", " ")
            .trim()

        val trimSep = charArrayOf(' ', '-', '_', '·', ':', '：')
        var prefixStripped = true
        while (prefixStripped) {
            prefixStripped = false
            cleaned = cleaned.trimStart(*trimSep)
            for (prefix in arrayOf(
                "兰斯10",
                "兰斯１０",
                "决战",
                "rance10",
                "rance 10",
                "日不落mod",
                "日不落",
                "mod"
            )) {
                if (cleaned.startsWith(prefix, ignoreCase = true)) {
                    cleaned = cleaned.substring(prefix.length).trimStart(*trimSep)
                    prefixStripped = true
                }
            }
        }

        val match = modVersionNumberRegex.find(cleaned) ?: return null
        val ver = match.groupValues[1]
        val lower = cleaned.lowercase(Locale.ROOT)
        val afterVer = cleaned.substring(match.range.last + 1)

        val hasPatchWord = lower.contains("补丁") || lower.contains("patch") ||
            lower.contains("hotfix") || lower.contains("热更") || lower.contains("修复")
        val hasFullWord = lower.contains("完整") || lower.contains("正式") ||
            lower.contains("整合") || lower.contains("本体")

        val isPatch = when {
            hasPatchWord && !hasFullWord -> true
            hasFullWord && !hasPatchWord -> false
            isPatchHint != null -> isPatchHint
            else -> hasPatchWord
        }

        if (isPatch) {
            val patchNum = patchSeqRegex.find(afterVer)?.groupValues?.getOrNull(1).orEmpty()
            return if (lower.contains("增量")) {
                "v${ver}增量补丁$patchNum"
            } else {
                "v${ver}修复补丁$patchNum"
            }
        }

        return when {
            lower.contains("内测") -> "v${ver}内测版"
            lower.contains("公测") -> "v${ver}公测版"
            lower.contains("测试") || lower.contains("beta") -> "v${ver}测试版"
            lower.contains("先行") -> "v${ver}先行版"
            lower.contains("体验") -> "v${ver}体验版"
            else -> "v${ver}正式版"
        }
    }

    fun inferVersionFromFileName(
        fileName: String,
        isPatchHint: Boolean? = null,
        fallbackIniDir: File? = null
    ): String {
        extractCanonicalModVersion(fileName, isPatchHint)?.let { return it }

        if (fallbackIniDir != null) {
            extractCanonicalModVersionFromDir(fallbackIniDir)?.let { return it }
        }

        val rawBase = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        val stripped = stripArchiveExtAndCopySuffix(rawBase)
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('（', ' ')
            .replace('）', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (stripped.isEmpty()) return "MOD"
        val lower = stripped.lowercase(Locale.ROOT)
        if (lower == "mod" || lower == "mods" || lower == "rance10" || lower == "兰斯10") {
            return "MOD"
        }
        return stripped
    }

    /** Infers display version for a local overlay archive/folder. */
    fun inferLocalOverlayVersion(
        fileName: String,
        isPatchHint: Boolean,
        fallbackIniDir: File?,
        isCnPatch: Boolean
    ): String {
        if (!isCnPatch) {
            return inferVersionFromFileName(
                fileName = fileName,
                isPatchHint = isPatchHint,
                fallbackIniDir = if (!isPatchHint) fallbackIniDir else null
            )
        }
        val iniName = fallbackIniDir?.let { dir ->
            val iniFile = File(dir, "AliceStart.ini").takeIf { it.isFile }
                ?: File(dir, "System40.ini").takeIf { it.isFile }
            iniFile?.let {
                try {
                    System40Ini.parse(it).gameName
                } catch (_: Throwable) {
                    null
                }
            }
        }.orEmpty()
        val verMatch = modVersionNumberRegex.find(fileName)
            ?: modVersionNumberRegex.find(iniName)
        return if (verMatch != null) "V${verMatch.groupValues[1]}" else "V1.00"
    }

    fun matchInstalledIndex(releases: List<ModReleaseEntry>, installed: InstalledModInfo?): Int {
        if (installed == null || releases.isEmpty()) return -1
        if (installed.fileId > 0) {
            val byId = releases.indexOfFirst { it.fileId == installed.fileId }
            if (byId >= 0) return byId
        }
        if (installed.fileName.isNotBlank()) {
            val byFile = releases.indexOfFirst { it.fileName.equals(installed.fileName, ignoreCase = true) }
            if (byFile >= 0) return byFile
            val normFile = normalizeModVersionKey(installed.fileName)
            if (normFile.isNotEmpty()) {
                val byNormFile = releases.indexOfFirst {
                    normalizeModVersionKey(it.fileName) == normFile ||
                        normalizeModVersionKey(it.version) == normFile
                }
                if (byNormFile >= 0) return byNormFile
            }
        }
        val normInstalled = normalizeModVersionKey(installed.version)
        if (normInstalled.isNotEmpty()) {
            val byVer = releases.indexOfFirst {
                normalizeModVersionKey(it.version) == normInstalled ||
                    normalizeModVersionKey(it.fileName) == normInstalled
            }
            if (byVer >= 0) return byVer
        }
        return -1
    }

    private fun isInstalledMatchingBaseRelease(
        baseRelease: ModReleaseEntry,
        installed: InstalledModInfo?
    ): Boolean {
        if (installed == null) return false
        if (installed.baseFileId > 0 && installed.baseFileId == baseRelease.fileId) {
            return true
        }
        val baseNorm = normalizeModVersionKey(baseRelease.version)
        if (baseNorm.isNotEmpty() && !installed.baseVersion.isNullOrBlank()) {
            if (normalizeModVersionKey(installed.baseVersion) == baseNorm) {
                return true
            }
        }
        return false
    }

    fun hasMatchingBaseForPatch(
        releases: List<ModReleaseEntry>,
        patchIdx: Int,
        hasMod: Boolean,
        installed: InstalledModInfo?
    ): Boolean {
        if (!hasMod || patchIdx !in releases.indices) return false
        val patch = releases[patchIdx]
        if (!patch.isPatch) return false
        val baseIdx = (patchIdx + 1 until releases.size).firstOrNull { !releases[it].isPatch } ?: return false
        val baseRelease = releases[baseIdx]
        val installedIdx = matchInstalledIndex(releases, installed)
        if (installedIdx in patchIdx..baseIdx) return true
        return isInstalledMatchingBaseRelease(baseRelease, installed)
    }

    fun computeUpdatePlan(
        releases: List<ModReleaseEntry>,
        hasMod: Boolean,
        installed: InstalledModInfo?
    ): ModUpdatePlan? {
        val latest = releases.firstOrNull() ?: return null
        val installedIdx = if (hasMod) matchInstalledIndex(releases, installed) else -1
        if (hasMod && installedIdx == 0) {
            return null
        }
        if (hasMod && installed != null && installed.fileId > 0 && latest.fileId > 0 &&
            installedIdx < 0 && installed.fileId >= latest.fileId
        ) {
            return null
        }
        if (!latest.isPatch) {
            return ModUpdatePlan(
                targetRelease = latest,
                steps = listOf(latest),
                isIncrementalOnly = false
            )
        }
        val baseIdx = (1 until releases.size).firstOrNull { !releases[it].isPatch } ?: -1
        if (baseIdx < 0) {
            return if (hasMod) {
                ModUpdatePlan(
                    targetRelease = latest,
                    steps = listOf(latest),
                    isIncrementalOnly = true
                )
            } else {
                null
            }
        }
        val baseRelease = releases[baseIdx]
        val matchingBase = hasMod && (
            installedIdx in 1..baseIdx ||
                isInstalledMatchingBaseRelease(baseRelease, installed)
            )
        if (matchingBase) {
            val patches = if (installedIdx in 1..baseIdx) {
                (installedIdx - 1 downTo 0).map { releases[it] }.filter { it.isPatch }
            } else {
                val minFid = installed?.fileId ?: -1
                (baseIdx - 1 downTo 0).map { releases[it] }.filter {
                    it.isPatch && (minFid <= 0 || it.fileId <= 0 || it.fileId > minFid)
                }
            }
            if (patches.isNotEmpty()) {
                return ModUpdatePlan(
                    targetRelease = latest,
                    steps = patches,
                    isIncrementalOnly = true
                )
            }
        }
        val fullSteps = ArrayList<ModReleaseEntry>()
        fullSteps.add(baseRelease)
        for (i in baseIdx - 1 downTo 0) {
            if (releases[i].isPatch) {
                fullSteps.add(releases[i])
            }
        }
        return ModUpdatePlan(
            targetRelease = latest,
            steps = fullSteps,
            isIncrementalOnly = false
        )
    }

    fun computeStepsForSelectedRelease(
        releases: List<ModReleaseEntry>,
        targetIdx: Int,
        hasMod: Boolean,
        installed: InstalledModInfo?,
        includeBaseForPatch: Boolean
    ): List<ModReleaseEntry> {
        if (targetIdx !in releases.indices) return emptyList()
        val target = releases[targetIdx]
        if (!target.isPatch) {
            return listOf(target)
        }
        val baseIdx = (targetIdx + 1 until releases.size).firstOrNull { !releases[it].isPatch } ?: -1
        if (!includeBaseForPatch || baseIdx < 0) {
            if (hasMod && baseIdx >= 0) {
                val installedIdx = matchInstalledIndex(releases, installed)
                if (installedIdx in (targetIdx + 1)..baseIdx) {
                    val incremental = (installedIdx - 1 downTo targetIdx)
                        .map { releases[it] }
                        .filter { it.isPatch }
                    if (incremental.isNotEmpty()) return incremental
                }
            }
            return listOf(target)
        }
        val steps = ArrayList<ModReleaseEntry>()
        steps.add(releases[baseIdx])
        for (i in baseIdx - 1 downTo targetIdx) {
            if (releases[i].isPatch) {
                steps.add(releases[i])
            }
        }
        return steps
    }

    private fun sanitizeParenFree(raw: String, multiline: Boolean): String {
        val cleaned = raw
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('（', ' ')
            .replace('）', ' ')
        return if (multiline) {
            cleaned.replace("\r", "")
                .split('\n')
                .map { it.replace(Regex("""\s+"""), " ").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        } else {
            cleaned.replace(Regex("""\s+"""), " ").trim()
        }
    }

    fun fetchModCatalog(
        cdnBaseUrl: String = RANCE10_CDN_URL,
        catalogRelPath: String = "files/mods/index.json"
    ): List<ModReleaseEntry> {
        val base = cdnBaseUrl.trimEnd('/')
        val cleanRel = catalogRelPath.trimStart('/')
        var conn: HttpURLConnection? = null
        try {
            conn = openFollowingRedirects(
                initialUrl = "$base/$cleanRel",
                headers = mapOf(
                    "User-Agent" to DEFAULT_USER_AGENT,
                    "Accept" to "application/json",
                    "Accept-Encoding" to "identity"
                )
            )
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("MOD catalog HTTP $code from $base")
            }
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val arr = root.optJSONArray("releases")
                ?: throw IOException("No MOD releases array in catalog")
            val rawRows = ArrayList<ModReleaseEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = obj.optString("key", "").trim()
                val fileName = obj.optString("fileName", "").trim()
                val version = sanitizeParenFree(obj.optString("version", ""), multiline = false)
                if (key.isEmpty() || fileName.isEmpty() || version.isEmpty()) continue
                if (!ArchiveExtractor.isSupportedArchiveName(fileName)) continue
                val fileId = obj.optInt("fileId", 1000 - i)
                val group = sanitizeParenFree(obj.optString("group", ""), multiline = false)
                val summary = sanitizeParenFree(obj.optString("summary", ""), multiline = false)
                val changelog = sanitizeParenFree(obj.optString("changelog", ""), multiline = true)
                    .ifEmpty { summary }
                val uploadedAt = obj.optString("uploadedAt", "").trim()
                val isPatch = obj.optBoolean(
                    "isPatch",
                    version.contains("补丁") || fileName.contains("补丁")
                )
                val encodedKey = key.split('/').joinToString("/") { Uri.encode(it) }
                val fileUrl = "$base/files/$encodedKey"
                rawRows.add(
                    ModReleaseEntry(
                        fileId = fileId,
                        version = version,
                        fileName = fileName,
                        summary = summary,
                        changelog = changelog,
                        uploadedAt = uploadedAt,
                        downloadUrl = fileUrl,
                        isPatch = isPatch,
                        baseFileId = null,
                        baseVersion = null,
                        expectedSize = obj.optLong("size", -1L),
                        expectedSha256 = obj.optString("sha256", "").trim().takeIf { it.isNotEmpty() },
                        group = group
                    )
                )
            }
            if (rawRows.isEmpty()) {
                throw IOException("No MOD releases found")
            }
            val resolved = ArrayList<ModReleaseEntry>(rawRows.size)
            for (i in rawRows.indices) {
                val item = rawRows[i]
                if (!item.isPatch) {
                    resolved.add(item)
                } else {
                    val baseEntry = (i + 1 until rawRows.size).map { rawRows[it] }.firstOrNull { !it.isPatch }
                    resolved.add(
                        item.copy(
                            baseFileId = baseEntry?.fileId,
                            baseVersion = baseEntry?.version
                        )
                    )
                }
            }
            return resolved
        } catch (t: Throwable) {
            throw (t as? IOException) ?: IOException("No MOD releases found", t)
        } finally {
            conn?.disconnect()
        }
    }

    fun installBaseGameFromCloud(
        context: Context,
        storagePath: File,
        cdnBaseUrl: String = RANCE10_CDN_URL,
        abortHandle: InstallAbortHandle? = null,
        onStagingDirReady: ((File) -> Unit)? = null,
        checkCancelled: () -> Unit,
        onProgress: (OnlineProgressUpdate) -> Unit
    ): File {
        onProgress(OnlineProgressUpdate(context.getString(R.string.online_fetching_manifest), null))
        val cleanBaseUrl = cdnBaseUrl.trimEnd('/')
        val manifest = fetchManifest(cleanBaseUrl, abortHandle, checkCancelled)
        if (manifest.entries.isEmpty()) {
            throw GameList.InstallFailureException(R.string.online_download_error)
        }
        if (abortHandle?.isAborted == true) throw CancellationException()
        checkCancelled()
        AppSettings.setCachedBaseGameSize(context, cleanBaseUrl, manifest.totalBytes)

        val safeBase = sanitizeInstallDirName(manifest.baseName, fallback = "Game")
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .trim('_')
            .ifEmpty { "Game" }
        val storageCanonical = storagePath.canonicalFile
        val stagingDir = File(storageCanonical, ".online_base_staging_$safeBase").canonicalFile
        if (stagingDir.parentFile != storageCanonical) {
            throw IOException("Invalid staging path")
        }
        if (abortHandle?.isAborted == true) throw CancellationException()
        checkCancelled()
        stagingDir.mkdirs()
        onStagingDirReady?.invoke(stagingDir)
        val stagingPrefix = stagingDir.path + File.separator
        val verifiedDoneEntries = HashSet<String>()

        var alreadyCompletedBytes = 0L
        for (entry in manifest.entries) {
            checkCancelled()
            if (isManifestEntryDone(stagingDir, stagingPrefix, entry, verifiedDoneEntries, checkCancelled)) {
                alreadyCompletedBytes += entry.size
            }
        }

        val remainingTotal = (manifest.totalBytes - alreadyCompletedBytes).coerceAtLeast(0L)
        ensureAvailableSpace(storageCanonical, remainingTotal)

        var completedBeforeCurrent = alreadyCompletedBytes
        val totalFiles = manifest.entries.size
        val totalGameMb = (manifest.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)

        for ((index, entry) in manifest.entries.withIndex()) {
            checkCancelled()
            if (isManifestEntryDone(stagingDir, stagingPrefix, entry, verifiedDoneEntries, checkCancelled)) {
                continue
            }

            val cleanPath = ArchiveExtractor.repairSjisAsGbkMojibake(entry.path)
            val relParts = cleanPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (relParts.isEmpty() || relParts.any { it == ".." }) {
                throw IOException("Invalid manifest entry path: ${entry.path}")
            }
            val finalFile = File(stagingDir, relParts.joinToString(File.separator)).canonicalFile
            if (!finalFile.path.startsWith(stagingPrefix)) {
                throw IOException("Manifest path escapes staging directory: ${entry.path}")
            }
            finalFile.parentFile?.mkdirs()

            val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
            val partMetaFile = File("${partFile.path}.meta")
            val encodedKey = entry.key.split('/').joinToString("/") { Uri.encode(it) }
            val fileUrl = "$cleanBaseUrl/files/$encodedKey"

            downloadFileWithResume(
                url = fileUrl,
                destPartFile = partFile,
                expectedSize = entry.size,
                expectedSha256 = entry.sha256,
                headers = mapOf("User-Agent" to DEFAULT_USER_AGENT),
                abortHandle = abortHandle,
                checkCancelled = checkCancelled,
                onProgress = { fileDoneBytes, _, speedBps ->
                    val globalDone = (completedBeforeCurrent + fileDoneBytes).coerceAtMost(manifest.totalBytes)
                    val permille = if (manifest.totalBytes > 0L) {
                        ((globalDone * 1000L) / manifest.totalBytes).toInt().coerceIn(0, 1000)
                    } else {
                        0
                    }
                    val pct = permille / 10
                    val doneMb = globalDone / (1024L * 1024L)
                    val speedStr = formatSpeed(speedBps)
                    val msg = context.getString(
                        R.string.online_base_download_progress,
                        index + 1,
                        totalFiles,
                        pct,
                        speedStr,
                        doneMb,
                        totalGameMb,
                        cleanPath
                    )
                    onProgress(OnlineProgressUpdate(msg, permille))
                }
            )

            if (entry.extract) {
                onProgress(
                    OnlineProgressUpdate(
                        context.getString(R.string.online_verifying_extracting, cleanPath),
                        if (manifest.totalBytes > 0L) {
                            (((completedBeforeCurrent + entry.size) * 1000L) / manifest.totalBytes).toInt().coerceIn(0, 1000)
                        } else null
                    )
                )
                ArchiveExtractor.extractFileToDirectory(
                    archiveFile = partFile,
                    destDir = stagingDir,
                    shouldSkip = { rel -> NativeGameImport.skip(rel) },
                    throwInstallFailureOnNoSpace = true,
                    checkCancelled = checkCancelled
                )
                markerFileForExtractEntry(stagingDir, entry).writeText(entry.sha256)
                partFile.delete()
                partMetaFile.delete()
            } else {
                if (finalFile.exists() && !finalFile.delete()) {
                    throw IOException("Cannot overwrite target file: $finalFile")
                }
                if (!moveFileOrDir(partFile, finalFile)) {
                    throw IOException("Failed to finalize downloaded file: $partFile -> $finalFile")
                }
                partMetaFile.delete()
            }

            completedBeforeCurrent += entry.size
        }

        // Clean up internal marker/meta files before finalizing game directory
        stagingDir.walkTopDown()
            .filter { it.isFile && (it.name.startsWith(".extracted_") || it.name.endsWith(".part.meta")) }
            .toList()
            .forEach { it.delete() }

        val hasSys4Ini = File(stagingDir, "AliceStart.ini").isFile ||
            File(stagingDir, "System40.ini").isFile ||
            (stagingDir.listFiles()?.any {
                it.isFile && (it.name.equals("AliceStart.ini", true) || it.name.equals("System40.ini", true))
            } == true)
        val sys4AinFile = stagingDir.listFiles()?.firstOrNull {
            it.isFile && !it.name.startsWith('.') && it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true)
        } ?: stagingDir.listFiles()?.firstOrNull {
            it.isFile && it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true)
        }
        val isSys3x = GameCatalog.isSystem3xGameDir(stagingDir)
        if (!(hasSys4Ini && sys4AinFile != null) && !isSys3x) {
            throw GameList.InstallFailureException(R.string.directory_no_game)
        }

        val rawAinBase = sys4AinFile?.let { ArchiveExtractor.repairSjisAsGbkMojibake(it.nameWithoutExtension) }.orEmpty()
        val sanitizedManifestBase = sanitizeInstallDirName(manifest.baseName, fallback = "")
        val sanitizedAinBase = sanitizeInstallDirName(rawAinBase, fallback = "")
        val classifiedStagedId = GameCatalog.classifyGameRootDir(stagingDir)
        val dirBaseName = when {
            classifiedStagedId == GameCatalog.ID_RANCE06 -> sanitizedManifestBase.ifEmpty { "Rance06" }
            classifiedStagedId == GameCatalog.ID_GALZOO -> sanitizedManifestBase.ifEmpty { "GALZOO" }
            classifiedStagedId == GameCatalog.ID_TSUMASHIBORI -> sanitizedManifestBase.ifEmpty { "Tsumashibori" }
            sanitizedManifestBase.isNotEmpty() &&
                (isSys3x || sanitizedAinBase.isEmpty() || sanitizedAinBase.any { it.code >= 0x80 }) ->
                sanitizedManifestBase
            else -> sanitizedAinBase.ifEmpty { sanitizedManifestBase.ifEmpty { "Game" } }
        }
        var dstDir = File(storageCanonical, dirBaseName).canonicalFile
        var suffix = 1
        while (dstDir.exists()) {
            dstDir = File(storageCanonical, "$dirBaseName-${suffix++}").canonicalFile
        }
        if (dstDir.parentFile != storageCanonical) {
            throw IOException("Destination directory escapes storage path: $dstDir")
        }
        if (!moveFileOrDir(stagingDir, dstDir)) {
            throw IOException("Failed to finalize installed game directory: $stagingDir -> $dstDir")
        }
        return dstDir
    }

    fun sanitizeInstallDirName(raw: String, fallback: String = "Game"): String {
        var cleaned = raw.filter { it != '/' && it != '\\' && it.code >= 0x20 }
        cleaned = cleaned.trim { it.isWhitespace() || it == '.' }
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_")
        }
        cleaned = cleaned.trim { it.isWhitespace() || it == '.' }
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") fallback else cleaned
    }

    fun moveFileOrDir(src: File, dst: File): Boolean {
        if (!src.exists()) return false
        return try {
            val srcCanonical = src.canonicalFile
            val dstCanonical = dst.canonicalFile
            if (srcCanonical == dstCanonical) return true
            dstCanonical.parentFile?.mkdirs()
            if (src.renameTo(dst)) {
                return dst.exists()
            }
            if (dst.exists() && !dst.deleteRecursively()) {
                return false
            }
            if (!copyRecursivelyVerified(src, dst)) {
                dst.deleteRecursively()
                return false
            }
            if (!src.deleteRecursively() || src.exists()) {
                return false
            }
            dst.exists()
        } catch (_: Throwable) {
            try {
                if (src.exists() && dst.exists() && src.canonicalFile != dst.canonicalFile) {
                    dst.deleteRecursively()
                }
            } catch (_: Throwable) {
            }
            false
        }
    }

    private fun copyRecursivelyVerified(src: File, dst: File): Boolean {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) return false
            val children = src.listFiles() ?: return false
            for (child in children) {
                if (!copyRecursivelyVerified(child, File(dst, child.name))) {
                    return false
                }
            }
            return dst.isDirectory
        } else if (src.isFile) {
            dst.parentFile?.mkdirs()
            val expectedLen = src.length()
            FileInputStream(src).use { input ->
                FileOutputStream(dst, false).use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n > 0) output.write(buf, 0, n)
                    }
                    output.fd.sync()
                }
            }
            return dst.isFile && dst.length() == expectedLen
        }
        return false
    }

    fun downloadAndExtractMod(
        context: Context,
        req: ModDownloadRequest,
        storagePath: File,
        tempExtractDir: File,
        stepPrefix: String = "",
        releaseLabel: String? = null,
        abortHandle: InstallAbortHandle? = null,
        checkCancelled: () -> Unit,
        onProgress: (OnlineProgressUpdate) -> Unit,
        applyStaging: (File) -> Unit
    ) {
        val cacheDir = File(storagePath, ".online_mod_cache")
        cacheDir.mkdirs()
        val cacheId = req.expectedSha256?.takeIf { it.length >= 16 }?.take(16)
            ?: sha256Hex(req.url.toByteArray(Charsets.UTF_8)).take(16)
        val ext = extractArchiveExtension(req.fileName)
        val partFile = File(cacheDir, "mod_${cacheId}${ext}.part")
        val metaFile = File("${partFile.path}.meta")

        val headers = mapOf("User-Agent" to DEFAULT_USER_AGENT)

        val rawName = req.fileName.ifBlank { "MOD$ext" }
        val displayName = if (!releaseLabel.isNullOrBlank() && !rawName.contains(releaseLabel)) {
            "$releaseLabel · $rawName"
        } else {
            rawName
        }

        val reportProgress: (Long, Long, Long) -> Unit = { doneBytes, totalBytes, speedBps ->
            val speedStr = formatSpeed(speedBps)
            val doneMb = doneBytes / (1024L * 1024L)
            if (totalBytes > 0L) {
                val permille = ((doneBytes * 1000L) / totalBytes).toInt().coerceIn(0, 1000)
                val pct = permille / 10
                val totalMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                val msg = stepPrefix + context.getString(
                    R.string.online_mod_download_progress,
                    pct,
                    speedStr,
                    doneMb,
                    totalMb,
                    displayName
                )
                onProgress(OnlineProgressUpdate(msg, permille))
            } else {
                val msg = stepPrefix + context.getString(
                    R.string.online_mod_download_progress_indeterminate,
                    doneMb,
                    speedStr,
                    displayName
                )
                onProgress(OnlineProgressUpdate(msg, null))
            }
        }

        downloadFileWithResume(
            url = req.url,
            destPartFile = partFile,
            expectedSize = req.contentLength.takeIf { it > 0L } ?: -1L,
            expectedSha256 = req.expectedSha256,
            headers = headers,
            abortHandle = abortHandle,
            checkCancelled = checkCancelled,
            onProgress = reportProgress
        )

        var extractedOk = false
        try {
            tempExtractDir.deleteRecursively()
            ArchiveExtractor.extractFileToDirectory(
                archiveFile = partFile,
                destDir = tempExtractDir,
                shouldSkip = { rel -> NativeGameImport.skip(rel) },
                throwInstallFailureOnNoSpace = true,
                checkCancelled = checkCancelled,
                onProgress = { extractedBytes, entryPath ->
                    val msg = stepPrefix + context.getString(
                        R.string.import_extract_progress,
                        extractedBytes / (1024L * 1024L),
                        entryPath
                    )
                    onProgress(OnlineProgressUpdate(msg, null))
                }
            )
            extractedOk = true
            applyStaging(tempExtractDir)
            partFile.delete()
            metaFile.delete()
            if (cacheDir.listFiles()?.isEmpty() == true) {
                cacheDir.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GameList.InstallFailureException) {
            if (!extractedOk) {
                partFile.delete()
                metaFile.delete()
            }
            throw e
        } catch (e: Exception) {
            if (!extractedOk) {
                partFile.delete()
                metaFile.delete()
            }
            throw e
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }

    private fun verifyStagedEntryHash(
        file: File,
        entry: ManifestEntry,
        verifiedCache: MutableSet<String>,
        checkCancelled: () -> Unit
    ): Boolean {
        if (!file.isFile || file.length() != entry.size) {
            return false
        }
        if (entry.sha256.isBlank()) {
            return true
        }
        val cacheKey = "${file.path}:${file.length()}:${file.lastModified()}:${entry.sha256.lowercase(Locale.ROOT)}"
        if (cacheKey in verifiedCache) {
            return true
        }
        val actualSha = computeFileSha256(file, checkCancelled)
        return if (actualSha.equals(entry.sha256, ignoreCase = true)) {
            verifiedCache.add(cacheKey)
            true
        } else {
            file.delete()
            false
        }
    }

    private fun isManifestEntryDone(
        stagingDir: File,
        stagingPrefix: String,
        entry: ManifestEntry,
        verifiedCache: MutableSet<String>,
        checkCancelled: () -> Unit
    ): Boolean {
        if (entry.extract) {
            val marker = markerFileForExtractEntry(stagingDir, entry)
            return marker.isFile && marker.readText().trim().equals(entry.sha256, ignoreCase = true)
        }
        val cleanPath = ArchiveExtractor.repairSjisAsGbkMojibake(entry.path)
        val relParts = cleanPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (relParts.isEmpty() || relParts.any { it == ".." }) return false
        val f = File(stagingDir, relParts.joinToString(File.separator)).canonicalFile
        if (!f.path.startsWith(stagingPrefix)) return false
        if (f.isFile) {
            if (verifyStagedEntryHash(f, entry, verifiedCache, checkCancelled)) {
                return true
            }
            if (f.exists() && f.length() != entry.size) {
                f.delete()
            }
        }
        if (cleanPath != entry.path) {
            val rawParts = entry.path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (rawParts.isNotEmpty() && rawParts.none { it == ".." }) {
                val rawFile = File(stagingDir, rawParts.joinToString(File.separator)).canonicalFile
                if (rawFile.path.startsWith(stagingPrefix) && rawFile.isFile) {
                    if (verifyStagedEntryHash(rawFile, entry, verifiedCache, checkCancelled)) {
                        f.parentFile?.mkdirs()
                        if (!moveFileOrDir(rawFile, f)) {
                            return false
                        }
                        if (entry.sha256.isNotBlank()) {
                            verifiedCache.add(
                                "${f.path}:${f.length()}:${f.lastModified()}:${entry.sha256.lowercase(Locale.ROOT)}"
                            )
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun markerFileForExtractEntry(stagingDir: File, entry: ManifestEntry): File {
        val safe = entry.path.replace(Regex("[^A-Za-z0-9._\\-]"), "_")
        return File(stagingDir, ".extracted_${safe}.done")
    }

    fun evictFileToTrash(target: File?): File? {
        if (target == null || !target.exists()) return null
        val parent = target.parentFile ?: return target
        val trash = File(parent, ".trash_${System.nanoTime()}_${(Math.random() * 100000).toInt()}")
        return if (target.renameTo(trash)) trash else target
    }

    fun evictBaseStagingToTrash(
        storagePath: File,
        targetGameId: String?,
        resolvedStagingDir: File?
    ): List<File> {
        val out = ArrayList<File>()
        try {
            val storageCanonical = storagePath.canonicalFile
            if (resolvedStagingDir != null &&
                resolvedStagingDir.canonicalFile.parentFile == storageCanonical &&
                resolvedStagingDir.exists()
            ) {
                evictFileToTrash(resolvedStagingDir)?.let { out.add(it) }
            }
            val children = storageCanonical.listFiles() ?: return out
            for (child in children) {
                if (!child.isDirectory || !child.name.startsWith(".online_base_staging_")) continue
                val lower = child.name.lowercase(Locale.ROOT)
                val matches = when (targetGameId) {
                    GameCatalog.ID_RANCE10 -> lower.contains("rance10") || lower.contains("rance_10")
                    GameCatalog.ID_DOHNA_DOHNA -> lower.contains("dohna")
                    GameCatalog.ID_EVENICLE -> lower.contains("evenicle") && !lower.contains("evenicle2") && !lower.contains("evenicle_2")
                    GameCatalog.ID_EVENICLE2 -> lower.contains("evenicle2") || lower.contains("evenicle_2")
                    GameCatalog.ID_SENGOKU_RANCE -> lower.contains("sengokurance")
                    GameCatalog.ID_RANCE01 -> lower.contains("rance01") || lower.contains("rance_01")
                    GameCatalog.ID_RANCE02 -> lower.contains("rance02") || lower.contains("rance_02")
                    GameCatalog.ID_RANCE03 -> lower.contains("rance03") || lower.contains("rance_03")
                    GameCatalog.ID_RANCE06 -> lower.contains("rance06") || lower.contains("rance_06") ||
                        Regex("(?<![a-z0-9])rance_?6(?![0-9])").containsMatchIn(lower)
                    GameCatalog.ID_RANCE08 -> lower.contains("rance08") || lower.contains("rance_08")
                    GameCatalog.ID_RANCE09 -> lower.contains("rance09") || lower.contains("rance_09")
                    GameCatalog.ID_KICHIKUOU -> lower.contains("kichiku")
                    GameCatalog.ID_DAIAKUJI -> lower.contains("daiakuji") ||
                        lower.contains("大悪司") ||
                        lower.contains("大恶司")
                    GameCatalog.ID_ESCALAYER -> lower.contains("escalayer") &&
                        !lower.contains("escalayerreboot") &&
                        !lower.contains("escalayer_reboot") &&
                        !lower.contains("reboot")
                    GameCatalog.ID_ESCALAYER_REBOOT -> lower.contains("escalayerreboot") ||
                        lower.contains("escalayer_reboot") ||
                        (lower.contains("escalayer") && lower.contains("reboot"))
                    GameCatalog.ID_GALZOO -> lower.contains("galzoo")
                    GameCatalog.ID_IXSEAL -> lower.contains("ixseal")
                    GameCatalog.ID_HARUKA -> lower.contains("haruka") ||
                        lower.contains("超昂閃忍") ||
                        lower.contains("超昂闪忍") ||
                        lower.contains("ハルカ")
                    GameCatalog.ID_TSUMASHIBORI -> lower.contains("tsumashibori") ||
                        lower.contains("tsumasibori") ||
                        lower.contains("妻しぼり") ||
                        lower.contains("嵢偟傏傝")
                    null, "" -> true
                    else -> false
                }
                if (matches) {
                    evictFileToTrash(child)?.let { out.add(it) }
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun evictModCacheToTrash(storagePath: File, steps: List<ModReleaseEntry>): List<File> {
        val out = ArrayList<File>()
        try {
            val cacheDir = File(storagePath, ".online_mod_cache")
            if (!cacheDir.isDirectory) return out
            for (step in steps) {
                val cacheId = step.expectedSha256?.takeIf { it.length >= 16 }?.take(16)
                    ?: sha256Hex(step.downloadUrl.toByteArray(Charsets.UTF_8)).take(16)
                val ext = extractArchiveExtension(step.fileName)
                evictFileToTrash(File(cacheDir, "mod_${cacheId}${ext}.part"))?.let { out.add(it) }
                evictFileToTrash(File(cacheDir, "mod_${cacheId}${ext}.part.meta"))?.let { out.add(it) }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun cleanupBaseStagingForGame(
        storagePath: File,
        targetGameId: String?,
        resolvedStagingDir: File?
    ) {
        for (f in evictBaseStagingToTrash(storagePath, targetGameId, resolvedStagingDir)) {
            try {
                f.deleteRecursively()
            } catch (_: Throwable) {
            }
        }
    }

    fun cleanupModCacheForSteps(storagePath: File, steps: List<ModReleaseEntry>) {
        for (f in evictModCacheToTrash(storagePath, steps)) {
            try {
                f.deleteRecursively()
            } catch (_: Throwable) {
            }
        }
        try {
            val cacheDir = File(storagePath, ".online_mod_cache")
            if (cacheDir.isDirectory && cacheDir.listFiles()?.isEmpty() == true) {
                cacheDir.delete()
            }
        } catch (_: Throwable) {
        }
    }

    fun fetchManifestTotalBytes(cdnBaseUrl: String): Long {
        val cleanBaseUrl = cdnBaseUrl.trimEnd('/')
        return fetchManifest(cleanBaseUrl, null) {}.totalBytes
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val gb = 1024.0 * 1024.0 * 1024.0
        val mb = 1024.0 * 1024.0
        val kb = 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun fetchManifest(
        cdnBaseUrl: String,
        abortHandle: InstallAbortHandle?,
        checkCancelled: () -> Unit
    ): OnlineManifest {
        var lastErr: Exception? = null
        for (attempt in 0 until 3) {
            if (abortHandle?.isAborted == true) throw CancellationException()
            checkCancelled()
            var conn: HttpURLConnection? = null
            try {
                conn = openFollowingRedirects(
                    initialUrl = "$cdnBaseUrl/manifest.json",
                    headers = mapOf(
                        "User-Agent" to DEFAULT_USER_AGENT,
                        "Accept" to "application/json",
                        "Accept-Encoding" to "identity"
                    ),
                    abortHandle = abortHandle,
                    readTimeoutMs = 15_000
                )
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Manifest HTTP $code")
                }
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(raw)
                val gameName = root.optString("gameName", "兰斯10")
                val baseName = root.optString("baseName", "Rance10")
                val filesArr = root.getJSONArray("files")
                val entries = ArrayList<ManifestEntry>(filesArr.length())
                var sumBytes = 0L
                for (i in 0 until filesArr.length()) {
                    val item = filesArr.getJSONObject(i)
                    val sz = item.getLong("size")
                    sumBytes += sz
                    entries.add(
                        ManifestEntry(
                            path = item.getString("path"),
                            key = item.getString("key"),
                            size = sz,
                            sha256 = item.getString("sha256"),
                            extract = item.optBoolean("extract", false)
                        )
                    )
                }
                val totalBytes = root.optLong("totalBytes", sumBytes).takeIf { it > 0L } ?: sumBytes
                return OnlineManifest(gameName, baseName, totalBytes, entries)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GameList.InstallFailureException) {
                throw e
            } catch (e: Exception) {
                if (abortHandle?.isAborted == true) throw CancellationException()
                checkCancelled()
                lastErr = e
                if (attempt < 2) {
                    sleepCancellable(1200L * (attempt + 1), checkCancelled)
                }
            } finally {
                abortHandle?.unregister(conn)
                if (conn != null) {
                    if (abortHandle?.isAborted == true) {
                        InstallAbortHandle.disconnectAsync(listOf(conn))
                    } else {
                        conn.disconnect()
                    }
                }
            }
        }
        throw IOException("Failed to fetch online manifest", lastErr)
    }

    private fun downloadFileWithResume(
        url: String,
        destPartFile: File,
        expectedSize: Long,
        expectedSha256: String?,
        headers: Map<String, String>,
        abortHandle: InstallAbortHandle?,
        checkCancelled: () -> Unit,
        onProgress: (doneBytes: Long, totalBytes: Long, speedBps: Long) -> Unit
    ) {
        destPartFile.parentFile?.mkdirs()
        val metaFile = File("${destPartFile.path}.meta")
        var knownTotal = expectedSize

        val savedMeta = readSavedMeta(metaFile, url, expectedSha256)
        if (savedMeta == null && (metaFile.isFile || (expectedSha256.isNullOrBlank() && destPartFile.isFile))) {
            destPartFile.delete()
            metaFile.delete()
        }
        if (knownTotal <= 0L && savedMeta != null && savedMeta.totalBytes > 0L) {
            knownTotal = savedMeta.totalBytes
        }

        var lastIoError: Exception? = null
        var retriedCleanAfterCorruptPart = false
        var attempt = 0
        while (attempt < MAX_HTTP_RETRIES) {
            if (abortHandle?.isAborted == true) throw CancellationException()
            checkCancelled()
            var curLen = if (destPartFile.isFile) destPartFile.length() else 0L
            if (knownTotal > 0L && curLen > knownTotal) {
                destPartFile.delete()
                metaFile.delete()
                curLen = 0L
            }
            if (knownTotal > 0L && curLen == knownTotal) {
                if (!expectedSha256.isNullOrBlank()) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (actualSha.equals(expectedSha256, ignoreCase = true)) {
                        onProgress(curLen, knownTotal, 0L)
                        return
                    }
                    // Pre-existing file matches expected size but failed SHA-256; delete and re-download.
                    destPartFile.delete()
                    metaFile.delete()
                    curLen = 0L
                } else {
                    lastIoError = null
                    break
                }
            }

            if (knownTotal > 0L) {
                ensureAvailableSpace(destPartFile.parentFile ?: destPartFile, (knownTotal - curLen).coerceAtLeast(0L))
            }

            val startedWithPartial = curLen > 0L
            var conn: HttpURLConnection? = null
            var completedNormally = false
            try {
                val reqHeaders = LinkedHashMap<String, String>(headers)
                reqHeaders["Accept-Encoding"] = "identity"
                if (curLen > 0L) {
                    reqHeaders["Range"] = "bytes=$curLen-"
                }

                conn = openFollowingRedirects(url, reqHeaders, abortHandle)
                val code = conn.responseCode
                conn.readTimeout = READ_SLICE_TIMEOUT_MS
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw IOException("HTTP $code")
                }

                val respEtag = conn.getHeaderField("ETag")?.trim()
                val curSavedEtag = readSavedMeta(metaFile, url, expectedSha256)?.etag
                val appendMode: Boolean

                when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        if (expectedSha256.isNullOrBlank() && curSavedEtag != null && respEtag != null && curSavedEtag != respEtag) {
                            abortHandle?.unregister(conn)
                            conn.disconnect()
                            conn = null
                            destPartFile.delete()
                            metaFile.delete()
                            attempt++
                            continue
                        }
                        val cr = conn.getHeaderField("Content-Range")
                        val parsed = parseContentRange(cr)
                        if (parsed != null) {
                            if (parsed.first != curLen) {
                                abortHandle?.unregister(conn)
                                conn.disconnect()
                                conn = null
                                destPartFile.delete()
                                metaFile.delete()
                                attempt++
                                continue
                            }
                            if (parsed.second > 0L) {
                                knownTotal = parsed.second
                            }
                        } else {
                            val cl = conn.contentLengthLong
                            if (cl > 0L && knownTotal <= 0L) {
                                knownTotal = curLen + cl
                            }
                        }
                        appendMode = true
                    }
                    HttpURLConnection.HTTP_OK -> {
                        curLen = 0L
                        appendMode = false
                        val cl = conn.contentLengthLong
                        if (cl > 0L) {
                            knownTotal = cl
                        }
                    }
                    416 -> {
                        val cr = conn.getHeaderField("Content-Range")
                        val slashTotal = cr?.substringAfter('/', "")?.trim()?.toLongOrNull() ?: -1L
                        if (slashTotal > 0L && curLen == slashTotal) {
                            knownTotal = slashTotal
                            completedNormally = true
                            abortHandle?.unregister(conn)
                            conn.disconnect()
                            conn = null
                            if (!expectedSha256.isNullOrBlank()) {
                                val actualSha = computeFileSha256(destPartFile, checkCancelled)
                                if (actualSha.equals(expectedSha256, ignoreCase = true)) {
                                    onProgress(curLen, knownTotal, 0L)
                                    return
                                }
                                destPartFile.delete()
                                metaFile.delete()
                                if (expectedSize > 0L) knownTotal = expectedSize
                                if (!retriedCleanAfterCorruptPart) {
                                    retriedCleanAfterCorruptPart = true
                                    continue
                                }
                                throw IOException("SHA-256 verification failed for ${destPartFile.name}")
                            }
                            lastIoError = null
                            break
                        }
                        destPartFile.delete()
                        metaFile.delete()
                        attempt++
                        continue
                    }
                    else -> throw IOException("Unexpected HTTP status $code")
                }

                if (knownTotal > 0L) {
                    ensureAvailableSpace(destPartFile.parentFile ?: destPartFile, (knownTotal - curLen).coerceAtLeast(0L))
                }
                if (!respEtag.isNullOrEmpty() || knownTotal > 0L || !expectedSha256.isNullOrBlank()) {
                    writeSavedMeta(metaFile, url, respEtag ?: curSavedEtag, knownTotal, expectedSha256)
                }

                var speedWindowStartMs = SystemClock.elapsedRealtime()
                var speedWindowBytes = 0L
                var currentSpeedBps = 0L
                var lastUiReportMs = 0L

                val input = conn.inputStream
                try {
                    FileOutputStream(destPartFile, appendMode).use { output ->
                        val buf = ByteArray(256 * 1024)
                        var idleReadMs = 0L
                        while (true) {
                            if (abortHandle?.isAborted == true) throw CancellationException()
                            checkCancelled()
                            val n = try {
                                input.read(buf)
                            } catch (ste: java.net.SocketTimeoutException) {
                                if (abortHandle?.isAborted == true) throw CancellationException()
                                checkCancelled()
                                idleReadMs += READ_SLICE_TIMEOUT_MS
                                if (idleReadMs >= TOTAL_READ_TIMEOUT_MS) {
                                    throw ste
                                }
                                continue
                            }
                            idleReadMs = 0L
                            if (abortHandle?.isAborted == true) throw CancellationException()
                            checkCancelled()
                            if (n < 0) break
                            if (n == 0) continue
                            output.write(buf, 0, n)
                            curLen += n
                            speedWindowBytes += n

                            val now = SystemClock.elapsedRealtime()
                            val elapsedWindow = now - speedWindowStartMs
                            if (elapsedWindow >= 600L) {
                                currentSpeedBps = (speedWindowBytes * 1000L) / elapsedWindow
                                speedWindowStartMs = now
                                speedWindowBytes = 0L
                            }
                            if (now - lastUiReportMs >= 150L) {
                                lastUiReportMs = now
                                onProgress(curLen, knownTotal, currentSpeedBps)
                            }
                        }
                        output.fd.sync()
                    }
                    completedNormally = true
                } finally {
                    if (completedNormally) {
                        try {
                            input.close()
                        } catch (_: Throwable) {
                        }
                    }
                }

                onProgress(curLen, knownTotal, currentSpeedBps)

                if (knownTotal > 0L && curLen < knownTotal) {
                    throw IOException("Premature EOF at $curLen / $knownTotal bytes")
                }

                if (!expectedSha256.isNullOrBlank()) {
                    val actualSha = computeFileSha256(destPartFile, checkCancelled)
                    if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                        destPartFile.delete()
                        metaFile.delete()
                        if (expectedSize > 0L) {
                            knownTotal = expectedSize
                        }
                        if (startedWithPartial && !retriedCleanAfterCorruptPart) {
                            retriedCleanAfterCorruptPart = true
                            continue
                        }
                        throw IOException("SHA-256 verification failed for ${destPartFile.name}")
                    }
                }

                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: GameList.InstallFailureException) {
                throw e
            } catch (e: Exception) {
                if (abortHandle?.isAborted == true) throw CancellationException()
                checkCancelled()
                lastIoError = e
                if (attempt + 1 < MAX_HTTP_RETRIES) {
                    sleepCancellable(1000L * (attempt + 1).coerceAtMost(5), checkCancelled)
                }
                attempt++
            } finally {
                abortHandle?.unregister(conn)
                if (conn != null) {
                    if (!completedNormally && abortHandle?.isAborted == true) {
                        InstallAbortHandle.disconnectAsync(listOf(conn))
                    } else {
                        conn.disconnect()
                    }
                }
            }
        }

        if (lastIoError != null) {
            val finalLen = if (destPartFile.isFile) destPartFile.length() else 0L
            if (knownTotal <= 0L || finalLen < knownTotal || !expectedSha256.isNullOrBlank()) {
                throw lastIoError
            }
        }

        if (knownTotal > 0L && (!destPartFile.isFile || destPartFile.length() != knownTotal)) {
            throw IOException("Downloaded size mismatch: expected $knownTotal, got ${destPartFile.length()}")
        }

        if (!expectedSha256.isNullOrBlank()) {
            val actualSha = computeFileSha256(destPartFile, checkCancelled)
            if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                destPartFile.delete()
                metaFile.delete()
                throw IOException("SHA-256 verification failed for ${destPartFile.name}")
            }
        }
    }

    private fun openFollowingRedirects(
        initialUrl: String,
        headers: Map<String, String>,
        abortHandle: InstallAbortHandle? = null,
        readTimeoutMs: Int = READ_SLICE_TIMEOUT_MS
    ): HttpURLConnection {
        var currentUrl = URL(initialUrl)

        for (hop in 0..MAX_REDIRECTS) {
            if (abortHandle?.isAborted == true) throw CancellationException()
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                for ((k, v) in headers) {
                    setRequestProperty(k, v)
                }
            }
            abortHandle?.register(conn)
            val code = try {
                conn.responseCode
            } catch (t: Throwable) {
                abortHandle?.unregister(conn)
                InstallAbortHandle.disconnectAsync(listOf(conn))
                throw t
            }
            if (code in intArrayOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                abortHandle?.unregister(conn)
                conn.disconnect()
                if (loc.isNullOrBlank()) {
                    throw IOException("Redirect $code missing Location header")
                }
                currentUrl = URL(currentUrl, loc)
                continue
            }
            return conn
        }
        throw IOException("Too many HTTP redirects")
    }

    private fun parseContentRange(header: String?): Pair<Long, Long>? {
        if (header.isNullOrBlank()) return null
        val m = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
            .find(header.trim()) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        val total = m.groupValues[3].toLongOrNull() ?: -1L
        return start to total
    }

    private fun ensureAvailableSpace(dir: File, neededBytes: Long) {
        try {
            val avail = StatFs(dir.path).availableBytes - HEADROOM_BYTES
            if (neededBytes > avail) {
                throw GameList.InstallFailureException(R.string.import_no_space)
            }
        } catch (e: GameList.InstallFailureException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    private fun readSavedMeta(metaFile: File, expectedUrl: String, expectedSha256: String?): SavedMeta? {
        if (!metaFile.isFile) return null
        return try {
            val j = JSONObject(metaFile.readText())
            val savedSha = j.optString("sha256")
            val shaMatched = !expectedSha256.isNullOrBlank() &&
                savedSha.isNotEmpty() &&
                savedSha.equals(expectedSha256, ignoreCase = true)
            if (!shaMatched && j.optString("url") != expectedUrl) return null
            SavedMeta(
                etag = j.optString("etag").takeIf { it.isNotEmpty() },
                totalBytes = j.optLong("totalBytes", -1L)
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeSavedMeta(metaFile: File, url: String, etag: String?, totalBytes: Long, sha256: String?) {
        try {
            val j = JSONObject().put("url", url)
            if (!etag.isNullOrEmpty()) j.put("etag", etag)
            if (totalBytes > 0L) j.put("totalBytes", totalBytes)
            if (!sha256.isNullOrBlank()) j.put("sha256", sha256)
            metaFile.writeText(j.toString())
        } catch (_: Throwable) {
        }
    }

    private fun computeFileSha256(file: File, checkCancelled: () -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                checkCancelled()
                val n = fis.read(buf)
                if (n < 0) break
                if (n > 0) md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun extractArchiveExtension(fileName: String): String {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".tar.gz") -> ".tar.gz"
            lower.endsWith(".tar.xz") -> ".tar.xz"
            lower.endsWith(".rar") -> ".rar"
            lower.endsWith(".7z") -> ".7z"
            lower.endsWith(".zip") -> ".zip"
            lower.endsWith(".tgz") -> ".tgz"
            lower.endsWith(".txz") -> ".txz"
            else -> ".rar"
        }
    }

    private fun formatSpeed(bps: Long): String {
        if (bps <= 0L) return "-- MB/s"
        val mbps = bps.toDouble() / (1024.0 * 1024.0)
        return if (mbps >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mbps)
        } else {
            val kbps = bps.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB/s", kbps)
        }
    }

    private fun sleepCancellable(ms: Long, checkCancelled: () -> Unit) {
        var remaining = ms
        while (remaining > 0L) {
            checkCancelled()
            val step = remaining.coerceAtMost(200L)
            Thread.sleep(step)
            remaining -= step
        }
    }
}
