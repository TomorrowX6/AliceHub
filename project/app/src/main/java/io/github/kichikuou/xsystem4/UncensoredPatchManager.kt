package io.github.kichikuou.xsystem4

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

data class UncensoredPatchManifest(
    val version: String,
    val gameId: String,
    val title: String,
    val summary: String,
    val fileName: String,
    val key: String,
    val size: Long,
    val sha256: String,
    val cgCount: Int,
    val updatedAt: String,
    val downloadUrl: String
)

data class InstalledUncensoredInfo(
    val version: String,
    val gameId: String,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val cgCount: Int,
    val updatedAt: String,
    val installedAtMs: Long
) {
    companion object {
        const val META_FILE_NAME = ".uncensored_patch.json"

        fun read(gameDir: File): InstalledUncensoredInfo? {
            val f = File(gameDir, META_FILE_NAME)
            if (!f.isFile) return null
            return try {
                val j = JSONObject(f.readText(Charsets.UTF_8))
                val fileName = j.optString("fileName", "").trim()
                if (!UncensoredPatchManager.isSafeOverlayFileName(fileName)) return null
                if (!File(gameDir, fileName).isFile) return null
                InstalledUncensoredInfo(
                    version = j.optString("version", "1.0").trim().ifEmpty { "1.0" },
                    gameId = j.optString("gameId", "").trim(),
                    fileName = fileName,
                    size = j.optLong("size", 0L),
                    sha256 = j.optString("sha256", "").trim().lowercase(Locale.ROOT),
                    cgCount = j.optInt("cgCount", 0),
                    updatedAt = j.optString("updatedAt", "").trim(),
                    installedAtMs = j.optLong("installedAtMs", 0L)
                )
            } catch (_: Throwable) {
                null
            }
        }

        fun write(gameDir: File, info: InstalledUncensoredInfo) {
            val tmp = File(gameDir, "$META_FILE_NAME.tmp")
            val target = File(gameDir, META_FILE_NAME)
            val j = JSONObject()
                .put("version", info.version)
                .put("gameId", info.gameId)
                .put("fileName", info.fileName)
                .put("size", info.size)
                .put("sha256", info.sha256)
                .put("cgCount", info.cgCount)
                .put("updatedAt", info.updatedAt)
                .put("installedAtMs", info.installedAtMs)
            tmp.writeText(j.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }

        fun delete(gameDir: File) {
            try {
                File(gameDir, META_FILE_NAME).delete()
                File(gameDir, "$META_FILE_NAME.tmp").delete()
            } catch (_: Throwable) {
            }
        }
    }
}

sealed class UncensoredInstallState {
    object Idle : UncensoredInstallState()
    data class Downloading(
        val gameDirPath: String,
        val gameId: String,
        val fileName: String,
        val doneBytes: Long,
        val totalBytes: Long,
        val speedBps: Long
    ) : UncensoredInstallState() {
        val permille: Int?
            get() = if (totalBytes > 0L) ((doneBytes * 1000L) / totalBytes).toInt().coerceIn(0, 1000) else null
    }
    data class Succeeded(val gameDirPath: String, val gameId: String) : UncensoredInstallState()
    data class Cancelled(val gameDirPath: String, val gameId: String) : UncensoredInstallState()
    data class Failed(val gameDirPath: String, val gameId: String, val message: String) : UncensoredInstallState()
}

object UncensoredPatchManager {
    const val MANIFEST_REL_PATH = "files/uncensored/manifest.json"
    const val CACHE_DIR_NAME = ".uncensored_cache"
    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
    private val MANAGED_OVERLAY_NAMES = setOf(
        "alicehubuncensoredcg.afa",
        "alicehubuncensoredcg.ald"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()
    private var activeJob: Job? = null
    private var activeAbortHandle: InstallAbortHandle? = null
    private var installGeneration: Long = 0L
    private var stateSeq: Long = 0L
    private val listeners = LinkedHashSet<(UncensoredInstallState) -> Unit>()

    @Volatile
    private var stateDispatcherOverride: ((() -> Unit) -> Unit)? = null

    @JvmName("setStateDispatcherOverride")
    internal fun setStateDispatcherOverride(override: ((() -> Unit) -> Unit)?) {
        stateDispatcherOverride = override
    }

    @Volatile
    var state: UncensoredInstallState = UncensoredInstallState.Idle
        private set

    fun addListener(listener: (UncensoredInstallState) -> Unit) {
        val current = synchronized(lock) {
            listeners.add(listener)
            state
        }
        listener(current)
    }

    fun removeListener(listener: (UncensoredInstallState) -> Unit) {
        synchronized(lock) { listeners.remove(listener) }
    }

    @JvmName("beginGenerationForTest")
    internal fun beginGenerationForTest(initialState: UncensoredInstallState): Long {
        synchronized(lock) {
            val gen = ++installGeneration
            publishState(initialState, expectedGeneration = gen, allowFromTerminal = true)
            return gen
        }
    }

    @JvmName("publishStateForTest")
    internal fun publishState(
        newState: UncensoredInstallState,
        expectedGeneration: Long? = null,
        allowFromTerminal: Boolean = true
    ): Boolean {
        val (snapshot, seq) = synchronized(lock) {
            if (expectedGeneration != null && expectedGeneration != installGeneration) {
                return false
            }
            if (!allowFromTerminal && state !is UncensoredInstallState.Downloading) {
                return false
            }
            state = newState
            stateSeq++
            listeners.toList() to stateSeq
        }
        val deliver = {
            val currentSeq = synchronized(lock) { stateSeq }
            if (seq == currentSeq) {
                for (l in snapshot) {
                    l(newState)
                }
            }
        }
        val override = stateDispatcherOverride
        if (override != null) {
            override(deliver)
        } else {
            scope.launch { deliver() }
        }
        return true
    }

    fun consumeTerminalState() {
        val (snapshot, seq) = synchronized(lock) {
            val cur = state
            if (cur is UncensoredInstallState.Succeeded ||
                cur is UncensoredInstallState.Cancelled ||
                cur is UncensoredInstallState.Failed
            ) {
                state = UncensoredInstallState.Idle
                stateSeq++
                listeners.toList() to stateSeq
            } else {
                return
            }
        }
        val deliver = {
            val currentSeq = synchronized(lock) { stateSeq }
            if (seq == currentSeq) {
                for (l in snapshot) {
                    l(UncensoredInstallState.Idle)
                }
            }
        }
        val override = stateDispatcherOverride
        if (override != null) {
            override(deliver)
        } else {
            scope.launch { deliver() }
        }
    }

    fun isDownloadingFor(gameDirPath: String): Boolean {
        val cur = state
        return cur is UncensoredInstallState.Downloading && cur.gameDirPath == gameDirPath
    }

    fun isAnyDownloading(): Boolean = state is UncensoredInstallState.Downloading

    fun isSafeOverlayFileName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith(".") || name.contains('/') || name.contains('\\') ||
            name.contains("..") || name.contains(':') || name.any { it.code < 0x20 }
        ) {
            return false
        }
        val lower = name.lowercase(Locale.ROOT)
        if (lower.endsWith(".vanilla.bak") || lower.endsWith(".part") || lower.endsWith(".tmp")) {
            return false
        }
        if (!lower.endsWith(".afa") && !lower.endsWith(".ald")) {
            return false
        }
        return lower.contains("uncensored")
    }

    fun isSafeKey(key: String): Boolean {
        if (key.isEmpty() || !key.startsWith("uncensored/") || key.contains('\\') ||
            key.contains(':') || key.any { it.code < 0x20 }
        ) {
            return false
        }
        val parts = key.split('/')
        return parts.all { it.isNotEmpty() && it != "." && it != ".." }
    }

    fun isManagedOverlayFileName(name: String): Boolean {
        return name.lowercase(Locale.ROOT) in MANAGED_OVERLAY_NAMES
    }

    fun detectInstalled(gameDir: File): Boolean {
        if (!gameDir.isDirectory) return false
        if (InstalledUncensoredInfo.read(gameDir) != null) return true
        val files = gameDir.listFiles() ?: return false
        return files.any { it.isFile && isManagedOverlayFileName(it.name) }
    }

    @Throws(IOException::class)
    fun parseManifest(raw: String, cdnBaseUrl: String): UncensoredPatchManifest {
        if (raw.length > 1024 * 1024) {
            throw IOException("Uncensored patch manifest too large")
        }
        val j = JSONObject(raw)
        val version = j.optString("version", "1.0").trim().ifEmpty { "1.0" }
        val gameId = j.optString("gameId", "").trim()
        val title = j.optString("title", "").trim()
        val summary = j.optString("summary", "").trim()
        val fileName = j.optString("fileName", "").trim()
        val key = j.optString("key", "").trim()
        val size = j.optLong("size", -1L)
        val sha256 = j.optString("sha256", "").trim().lowercase(Locale.ROOT)
        val cgCount = j.optInt("cgCount", 0)
        val updatedAt = j.optString("updatedAt", "").trim()

        if (!isSafeOverlayFileName(fileName) || !isSafeKey(key) || size <= 0L || !SHA256_REGEX.matches(sha256)) {
            throw IOException("Invalid uncensored patch manifest")
        }
        val encodedKey = key.split('/').joinToString("/") { seg ->
            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        val base = AppSettings.canonicalCdnUrl(cdnBaseUrl)
        val downloadUrl = "$base/files/$encodedKey"
        return UncensoredPatchManifest(
            version = version,
            gameId = gameId,
            title = title,
            summary = summary,
            fileName = fileName,
            key = key,
            size = size,
            sha256 = sha256,
            cgCount = cgCount,
            updatedAt = updatedAt,
            downloadUrl = downloadUrl
        )
    }

    private class NonRetryableManifestException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private fun isRetryableHttpStatus(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    @Throws(IOException::class)
    fun fetchManifest(cdnBaseUrl: String): UncensoredPatchManifest {
        val base = AppSettings.canonicalCdnUrl(cdnBaseUrl)
        var lastErr: IOException? = null
        for (attempt in 0 until 3) {
            var conn: HttpURLConnection? = null
            try {
                conn = OnlineInstaller.openFollowingRedirects(
                    initialUrl = "$base/$MANIFEST_REL_PATH",
                    headers = mapOf(
                        "User-Agent" to OnlineInstaller.DEFAULT_USER_AGENT,
                        "Accept" to "application/json",
                        "Accept-Encoding" to "identity"
                    ),
                    readTimeoutMs = 15_000
                )
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    if (isRetryableHttpStatus(code)) {
                        throw IOException("HTTP $code")
                    }
                    throw NonRetryableManifestException("HTTP $code")
                }
                val raw = conn.inputStream.use { input ->
                    val maxBytes = 1024 * 1024
                    val buf = ByteArray(8192)
                    val out = java.io.ByteArrayOutputStream()
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (out.size() + n > maxBytes) {
                            throw NonRetryableManifestException("Uncensored patch manifest too large")
                        }
                        out.write(buf, 0, n)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
                return try {
                    parseManifest(raw, base)
                } catch (e: IOException) {
                    throw NonRetryableManifestException(e.message ?: "Invalid uncensored patch manifest", e)
                } catch (e: Exception) {
                    throw NonRetryableManifestException("Invalid uncensored patch manifest", e)
                }
            } catch (e: NonRetryableManifestException) {
                throw e
            } catch (e: IOException) {
                lastErr = e
                if (attempt < 2) {
                    try {
                        Thread.sleep(600L * (attempt + 1))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Throwable) {
                }
            }
        }
        throw lastErr ?: IOException("Failed to fetch uncensored patch manifest")
    }

    @JvmOverloads
    @JvmName("installPartFileAtomically")
    @Throws(IOException::class)
    internal fun installPartFileAtomically(
        partFile: File,
        destFile: File,
        expectedSize: Long,
        expectedSha256: String,
        checkCancelled: () -> Unit = {},
        renameFn: (File, File) -> Boolean = { src, dst -> src.renameTo(dst) }
    ) {
        if (renameFn(partFile, destFile)) {
            return
        }
        val destParent = destFile.parentFile ?: throw IOException("Destination file has no parent directory")
        if (!destParent.isDirectory && !destParent.mkdirs()) {
            throw IOException("Cannot create destination directory: $destParent")
        }
        val tmpFile = File(destParent, ".${destFile.name}.${System.nanoTime()}.tmp")
        var backupFile: File? = null
        try {
            val md = if (expectedSha256.isNotBlank()) MessageDigest.getInstance("SHA-256") else null
            var written = 0L
            FileInputStream(partFile).use { input ->
                FileOutputStream(tmpFile, false).use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        checkCancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n == 0) continue
                        output.write(buf, 0, n)
                        md?.update(buf, 0, n)
                        written += n.toLong()
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (expectedSize > 0L && written != expectedSize) {
                throw IOException("Fallback copy size mismatch: expected $expectedSize, got $written")
            }
            if (md != null) {
                val actualSha = md.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("Fallback copy SHA-256 mismatch for ${destFile.name}")
                }
            }
            if (!renameFn(tmpFile, destFile)) {
                if (destFile.exists()) {
                    val bak = File(destParent, ".${destFile.name}.${System.nanoTime()}.bak")
                    if (!renameFn(destFile, bak)) {
                        throw IOException("Cannot replace existing destination file: ${destFile.name}")
                    }
                    backupFile = bak
                    if (!renameFn(tmpFile, destFile)) {
                        try {
                            if (renameFn(bak, destFile)) {
                                backupFile = null
                            }
                        } catch (_: Throwable) {
                        }
                        throw IOException("Cannot finalize atomic copy to ${destFile.name}")
                    }
                } else {
                    throw IOException("Cannot rename staged copy to ${destFile.name}")
                }
            }
            backupFile?.delete()
            partFile.delete()
        } catch (t: Throwable) {
            try {
                tmpFile.delete()
            } catch (_: Throwable) {
            }
            val bak = backupFile
            if (bak != null && bak.exists() && !destFile.exists()) {
                try {
                    renameFn(bak, destFile)
                } catch (_: Throwable) {
                }
            }
            throw t
        }
    }

    @JvmOverloads
    fun startInstall(
        gameDir: File,
        gameId: String,
        manifest: UncensoredPatchManifest,
        maxConcurrency: Int = AppSettings.DEFAULT_DOWNLOAD_CONCURRENCY
    ): Boolean {
        synchronized(lock) {
            if (activeJob?.isActive == true) return false
            val abort = InstallAbortHandle()
            activeAbortHandle = abort
            val myGen = ++installGeneration
            val dirPath = gameDir.path
            publishState(
                UncensoredInstallState.Downloading(
                    gameDirPath = dirPath,
                    gameId = gameId,
                    fileName = manifest.fileName,
                    doneBytes = 0L,
                    totalBytes = manifest.size,
                    speedBps = 0L
                ),
                expectedGeneration = myGen,
                allowFromTerminal = true
            )
            activeJob = scope.launch(Dispatchers.IO) {
                try {
                    val checkCancelled = {
                        if (abort.isAborted || Thread.currentThread().isInterrupted) {
                            throw CancellationException()
                        }
                    }
                    checkCancelled()
                    val idPrefix = manifest.sha256.take(16)
                    val cacheDir = File(gameDir.parentFile ?: gameDir, CACHE_DIR_NAME)
                    if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
                        throw IOException("Failed to create uncensored cache directory")
                    }
                    val partFile = File(cacheDir, "uncensored_${idPrefix}.part")
                    val metaFile = File("${partFile.path}.meta")
                    val destFile = File(gameDir, manifest.fileName)
                    val destCanonical = destFile.canonicalFile
                    if (destCanonical.parentFile != gameDir.canonicalFile) {
                        throw IOException("Invalid destination path")
                    }
                    val prevInfo = InstalledUncensoredInfo.read(gameDir)
                    val destOwned = !destFile.exists() ||
                        isManagedOverlayFileName(destFile.name) ||
                        (prevInfo != null && prevInfo.fileName.equals(destFile.name, ignoreCase = true))
                    if (!destOwned) {
                        throw IOException("Refusing to overwrite unowned file: ${destFile.name}")
                    }

                    OnlineInstaller.downloadFileWithResume(
                        url = AppSettings.canonicalCdnUrl(manifest.downloadUrl),
                        destPartFile = partFile,
                        expectedSize = manifest.size,
                        expectedSha256 = manifest.sha256,
                        headers = mapOf("User-Agent" to OnlineInstaller.DEFAULT_USER_AGENT),
                        abortHandle = abort,
                        checkCancelled = checkCancelled,
                        onProgress = { done, total, speed ->
                            publishState(
                                UncensoredInstallState.Downloading(
                                    gameDirPath = dirPath,
                                    gameId = gameId,
                                    fileName = manifest.fileName,
                                    doneBytes = done,
                                    totalBytes = if (total > 0L) total else manifest.size,
                                    speedBps = speed
                                ),
                                expectedGeneration = myGen,
                                allowFromTerminal = false
                            )
                        },
                        maxConnections = AppSettings.normalizeDownloadConcurrency(maxConcurrency)
                    )

                    checkCancelled()
                    if (prevInfo != null && isSafeOverlayFileName(prevInfo.fileName) &&
                        !prevInfo.fileName.equals(manifest.fileName, ignoreCase = true)
                    ) {
                        val prevFile = File(gameDir, prevInfo.fileName)
                        if (prevFile.isFile) prevFile.delete()
                    }
                    installPartFileAtomically(
                        partFile = partFile,
                        destFile = destFile,
                        expectedSize = manifest.size,
                        expectedSha256 = manifest.sha256,
                        checkCancelled = checkCancelled
                    )
                    metaFile.delete()
                    cleanupLegacyGameDirPartFiles(gameDir)
                    InstalledUncensoredInfo.write(
                        gameDir,
                        InstalledUncensoredInfo(
                            version = manifest.version,
                            gameId = manifest.gameId.ifEmpty { gameId },
                            fileName = manifest.fileName,
                            size = manifest.size,
                            sha256 = manifest.sha256,
                            cgCount = manifest.cgCount,
                            updatedAt = manifest.updatedAt,
                            installedAtMs = System.currentTimeMillis()
                        )
                    )
                    publishState(
                        UncensoredInstallState.Succeeded(dirPath, gameId),
                        expectedGeneration = myGen,
                        allowFromTerminal = false
                    )
                } catch (_: CancellationException) {
                    publishState(
                        UncensoredInstallState.Cancelled(dirPath, gameId),
                        expectedGeneration = myGen,
                        allowFromTerminal = false
                    )
                } catch (e: Throwable) {
                    if (abort.isAborted) {
                        publishState(
                            UncensoredInstallState.Cancelled(dirPath, gameId),
                            expectedGeneration = myGen,
                            allowFromTerminal = false
                        )
                    } else {
                        publishState(
                            UncensoredInstallState.Failed(
                                dirPath,
                                gameId,
                                e.localizedMessage ?: e.javaClass.simpleName
                            ),
                            expectedGeneration = myGen,
                            allowFromTerminal = false
                        )
                    }
                } finally {
                    synchronized(lock) {
                        if (installGeneration == myGen) {
                            activeAbortHandle = null
                            activeJob = null
                        }
                    }
                }
            }
            return true
        }
    }

    fun cancelActiveInstall() {
        val abort = synchronized(lock) { activeAbortHandle }
        abort?.abort()
    }

    fun uninstall(gameDir: File): Boolean {
        if (!gameDir.isDirectory) return false
        var ok = true
        val recorded = InstalledUncensoredInfo.read(gameDir)
        if (recorded != null && isSafeOverlayFileName(recorded.fileName)) {
            val f = File(gameDir, recorded.fileName)
            if (f.isFile && !f.delete()) ok = false
        }
        gameDir.listFiles()?.forEach { f ->
            if (f.isFile && isManagedOverlayFileName(f.name)) {
                if (!f.delete()) ok = false
            }
        }
        cleanupLegacyGameDirPartFiles(gameDir)
        InstalledUncensoredInfo.delete(gameDir)
        return ok
    }

    private fun cleanupLegacyGameDirPartFiles(gameDir: File) {
        gameDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith(".uncensored_") &&
                (f.name.endsWith(".part") || f.name.endsWith(".part.meta"))
            ) {
                f.delete()
            }
        }
    }
}
