package io.github.kichikuou.xsystem4

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.UUID

object ArchiveExtractor {
    private const val TAG = "ArchiveExtractor"
    private const val MIN_FREE_HEADROOM_BYTES = 32L * 1024L * 1024L
    private val GBK_CHARSET: Charset by lazy { Charset.forName("GBK") }
    private val SJIS_CHARSET: Charset by lazy { Charset.forName("Shift_JIS") }
    private val CP932_CHARSET: Charset by lazy {
        runCatching { Charset.forName("windows-31j") }.getOrElse { SJIS_CHARSET }
    }

    @Volatile
    private var nativeLoaded = false

    @Synchronized
    fun ensureLoaded() {
        if (nativeLoaded) return
        System.loadLibrary("archive_jni")
        nativeLoaded = true
    }

    interface NativeCallback {
        fun onEntry(rawPath: ByteArray, entrySize: Long): String?
        fun onProgress(totalBytesWritten: Long)
    }

    @JvmStatic
    private external fun nativeIsFdSeekable(fd: Int): Boolean

    @JvmStatic
    private external fun nativeExtractFd(fd: Int, callback: NativeCallback): Int

    fun isSupportedArchiveName(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return lower.endsWith(".zip") ||
            lower.endsWith(".rar") ||
            lower.endsWith(".7z") ||
            lower.endsWith(".tar") ||
            lower.endsWith(".tar.gz") ||
            lower.endsWith(".tgz") ||
            lower.endsWith(".tar.xz") ||
            lower.endsWith(".txz") ||
            lower.endsWith(".iso") ||
            lower.endsWith(".mdf")
    }

    fun decodeBestCjkText(rawBytes: ByteArray, preferGbkOnTie: Boolean = true): String {
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(rawBytes)).toString()
        } catch (_: CharacterCodingException) {
            val sjis = decodeStrict(rawBytes, SJIS_CHARSET) ?: String(rawBytes, SJIS_CHARSET)
            val gbk = decodeStrict(rawBytes, GBK_CHARSET) ?: String(rawBytes, GBK_CHARSET)
            val sjisScore = scoreCjkString(sjis) + gb2312TrailPenaltyForSjis(rawBytes)
            val gbkScore = scoreCjkString(gbk) + gb2312Bonus(rawBytes)
            return when {
                sjisScore > gbkScore -> sjis
                gbkScore > sjisScore -> gbk
                preferGbkOnTie -> gbk
                else -> sjis
            }
        }
    }

    private fun decodeStrict(rawBytes: ByteArray, cs: Charset): String? {
        return try {
            cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(rawBytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun scoreCjkString(s: String): Int {
        var score = 0
        for (ch in s) {
            when {
                ch == '\uFFFD' -> score -= 25
                ch in '\u3041'..'\u309F' || ch in '\u30A1'..'\u30FF' -> score += 4
                ch in '\uFF61'..'\uFF9F' -> score -= 6
                ch in '\uFF01'..'\uFF5E' -> score += 2
                ch in '\u4E00'..'\u9FFF' -> score += 1
            }
        }
        return score
    }

    private fun gb2312Bonus(bytes: ByteArray): Int {
        var bonus = 0
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            if (b0 < 0x80) {
                i++
                continue
            }
            if (i + 1 < bytes.size) {
                val b1 = bytes[i + 1].toInt() and 0xFF
                if (b0 in 0xB0..0xF7 && b1 in 0xA1..0xFE) {
                    bonus += 3
                } else if (b0 in 0x81..0x9F && b1 < 0xA1) {
                    bonus -= 3
                }
                i += 2
            } else {
                bonus -= 10
                i++
            }
        }
        return bonus
    }

    private fun gb2312TrailPenaltyForSjis(bytes: ByteArray): Int {
        // High byte pairs in 0xB0..0xDF followed by 0xA1..0xFE cannot be two-byte Shift-JIS chars
        var penalty = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            if (b0 < 0x80) {
                i++
                continue
            }
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 in 0xB0..0xDF && b1 in 0xA1..0xFE) {
                penalty -= 6
                i += 2
            } else {
                i += if (b0 in 0xA1..0xDF) 1 else 2
            }
        }
        return penalty
    }

    /**
     * Repairs a filename or relative path whose Shift-JIS bytes were previously decoded as GBK
     * (e.g. "愴崙儔儞僗_Sys.ASD" -> "戦国ランス_Sys.ASD", "儔儞僗丒僋僄僗僩.ain" -> "ランス・クエスト.ain").
     * Leaves genuine Simplified Chinese and already-valid Japanese text untouched by requiring
     * strict Shift-JIS structure with zero halfwidth katakana and zero GB2312 high-byte pairs.
     */
    fun repairSjisAsGbkMojibake(input: String): String {
        if (input.isEmpty()) return input
        var hasNonAscii = false
        for (ch in input) {
            if (ch.code >= 0x80) {
                hasNonAscii = true
                break
            }
        }
        if (!hasNonAscii) return input

        val normalized = input.replace('\\', '/')
        val sep = if (input.contains('\\') && !input.contains('/')) '\\' else '/'
        val repairedSegments = normalized.split('/').map { seg -> repairSegmentSjisAsGbk(seg) }
        return repairedSegments.joinToString(sep.toString())
    }

    private fun repairSegmentSjisAsGbk(seg: String): String {
        if (seg.isEmpty() || seg.none { it.code >= 0x80 }) return seg
        val gbkBytes = encodeStrict(seg, GBK_CHARSET) ?: return seg
        var i = 0
        var cjkPairs = 0
        var nonGb2312Pairs = 0
        while (i < gbkBytes.size) {
            val b0 = gbkBytes[i].toInt() and 0xFF
            if (b0 < 0x80) {
                i++
                continue
            }
            if (b0 in 0xA1..0xDF) {
                // Halfwidth katakana byte range inside Shift-JIS indicates normal GBK text, not SJIS mojibake.
                return seg
            }
            if (!((b0 in 0x81..0x9F) || (b0 in 0xE0..0xEF)) || i + 1 >= gbkBytes.size) {
                return seg
            }
            val b1 = gbkBytes[i + 1].toInt() and 0xFF
            if (!((b1 in 0x40..0x7E) || (b1 in 0x80..0xFC))) {
                return seg
            }
            cjkPairs++
            if (b0 < 0xA1 || b1 < 0xA1) {
                nonGb2312Pairs++
            }
            i += 2
        }
        if (cjkPairs == 0 || nonGb2312Pairs != cjkPairs) return seg
        val decoded = decodeStrict(gbkBytes, CP932_CHARSET) ?: return seg
        if (decoded.indexOf('\uFFFD') >= 0) return seg
        return decoded
    }

    private fun encodeStrict(text: String, cs: Charset): ByteArray? {
        return try {
            val buf = cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(text))
            ByteArray(buf.remaining()).also { buf.get(it) }
        } catch (_: CharacterCodingException) {
            null
        }
    }

    fun decodeEntryPath(rawBytes: ByteArray): String =
        repairSjisAsGbkMojibake(decodeBestCjkText(rawBytes, preferGbkOnTie = true))

    /**
     * Extracts a `.zip`, `.rar` (RAR4/RAR5), `.7z`, `.tar.*`, `.iso`, or `.mdf` archive from a local [archiveFile] into [destDir].
     */
    fun extractFileToDirectory(
        archiveFile: File,
        destDir: File,
        shouldSkip: (normalizedRelPath: String) -> Boolean = { false },
        throwInstallFailureOnNoSpace: Boolean = false,
        checkCancelled: (() -> Unit)? = null,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)? = null,
        onProgress: ((totalBytes: Long, currentEntryPath: String) -> Unit)? = null
    ): Int {
        if (Iso9660Extractor.isIsoOrMdfName(archiveFile.name)) {
            return Iso9660Extractor.extractFileToDirectory(
                isoFile = archiveFile,
                outDir = destDir,
                shouldSkip = shouldSkip,
                throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
                checkCancelled = checkCancelled,
                onEntryAccepted = onEntryAccepted,
                onProgress = onProgress
            )
        }
        ensureLoaded()
        val trackingCallback = createTrackingCallback(
            destDir = destDir,
            shouldSkip = shouldSkip,
            throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
            checkCancelled = checkCancelled,
            onEntryAccepted = onEntryAccepted,
            onProgress = onProgress
        )
        ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            return runNativeExtract(pfd.fd, trackingCallback, throwInstallFailureOnNoSpace)
        }
    }

    /**
     * Extracts a `.zip`, `.rar` (RAR4/RAR5), `.7z`, or `.tar.*` archive from [uri] into [destDir].
     * Uses a direct seekable SAF file descriptor whenever available (zero temporary archive copy),
     * falling back to a temporary file copy only for non-seekable virtual streams.
     */
    fun extractUriToDirectory(
        context: Context,
        uri: Uri,
        destDir: File,
        tempScratchParent: File = destDir.parentFile ?: context.cacheDir,
        shouldSkip: (normalizedRelPath: String) -> Boolean = { false },
        throwInstallFailureOnNoSpace: Boolean = false,
        checkCancelled: (() -> Unit)? = null,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)? = null,
        onProgress: ((totalBytes: Long, currentEntryPath: String) -> Unit)? = null,
        decodePath: (ByteArray) -> String = ::decodeEntryPath
    ): Int {
        ensureLoaded()
        val trackingCallback = createTrackingCallback(
            destDir = destDir,
            shouldSkip = shouldSkip,
            throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
            checkCancelled = checkCancelled,
            onEntryAccepted = onEntryAccepted,
            onProgress = onProgress,
            decodePath = decodePath
        )

        var directPfd: ParcelFileDescriptor? = null
        var tempArchiveFile: File? = null
        try {
            directPfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                Log.w(TAG, "openFileDescriptor failed, falling back to stream copy", e)
                null
            }

            if (directPfd != null) {
                val isIso = try {
                    val ch = FileInputStream(directPfd.fileDescriptor).channel
                    val detected = Iso9660Extractor.isIsoOrMdfChannel(ch)
                    if (!detected) {
                        try { ch.position(0L) } catch (_: Throwable) {}
                    }
                    detected
                } catch (_: Throwable) {
                    false
                }
                if (isIso) {
                    directPfd.close()
                    directPfd = null
                    return Iso9660Extractor.extractUriToDirectory(
                        context = context,
                        uri = uri,
                        outDir = destDir,
                        tempScratchParent = tempScratchParent,
                        shouldSkip = shouldSkip,
                        throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
                        checkCancelled = checkCancelled,
                        onEntryAccepted = onEntryAccepted,
                        onProgress = onProgress
                    )
                }
                if (nativeIsFdSeekable(directPfd.fd)) {
                    return runNativeExtract(directPfd.fd, trackingCallback, throwInstallFailureOnNoSpace)
                }
            }

            directPfd?.close()
            directPfd = null

            tempScratchParent.mkdirs()
            val stagedFile = File(tempScratchParent, ".archive_src_${UUID.randomUUID()}.tmp")
            tempArchiveFile = stagedFile

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open selected archive URI")
            input.use { src ->
                stagedFile.outputStream().buffered().use { dst ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        checkCancelled?.invoke()
                        val n = src.read(buf)
                        if (n < 0) break
                        dst.write(buf, 0, n)
                    }
                }
            }

            ParcelFileDescriptor.open(stagedFile, ParcelFileDescriptor.MODE_READ_ONLY).use { stagedPfd ->
                return runNativeExtract(stagedPfd.fd, trackingCallback, throwInstallFailureOnNoSpace)
            }
        } finally {
            try {
                directPfd?.close()
            } catch (_: Throwable) {
            }
            try {
                tempArchiveFile?.delete()
            } catch (_: Throwable) {
            }
        }
    }

    private fun createTrackingCallback(
        destDir: File,
        shouldSkip: (normalizedRelPath: String) -> Boolean,
        throwInstallFailureOnNoSpace: Boolean,
        checkCancelled: (() -> Unit)?,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)?,
        onProgress: ((totalBytes: Long, currentEntryPath: String) -> Unit)?,
        decodePath: (ByteArray) -> String = ::decodeEntryPath
    ): NativeCallback {
        destDir.mkdirs()
        val destCanonical = destDir.canonicalFile
        val rootPrefix = destCanonical.path + File.separator

        var lastProgressMs = 0L
        var currentRelPath = ""
        var latestBytesWritten = 0L
        val createdDirs = HashSet<String>()

        return object : NativeCallback {
            override fun onEntry(rawPath: ByteArray, entrySize: Long): String? {
                checkCancelled?.invoke()
                val decoded = decodePath(rawPath)
                if ('\u0000' in decoded) {
                    throw IOException("Archive entry contains NUL character")
                }
                val isDirBySuffix = decoded.endsWith('/') || decoded.endsWith('\\')
                val normalized = decoded.replace('\\', '/').trimStart('/')
                val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
                if (parts.isEmpty()) return null
                if (parts.any { it == "__MACOSX" || it == ".DS_Store" }) return null
                if (parts.any { it == ".." }) {
                    throw IOException("Archive entry escapes destination directory: $decoded")
                }

                val relPath = parts.joinToString("/")
                if (shouldSkip(relPath)) {
                    return null
                }
                if (isDirBySuffix) {
                    val dirFile = File(destCanonical, parts.joinToString(File.separator)).canonicalFile
                    if (dirFile != destCanonical && !dirFile.path.startsWith(rootPrefix)) {
                        throw IOException("Archive directory escapes destination directory: $decoded")
                    }
                    if (createdDirs.add(dirFile.path)) {
                        dirFile.mkdirs()
                    }
                    return null
                }

                if (entrySize > 0L) {
                    val avail = try {
                        StatFs(destCanonical.path).availableBytes - MIN_FREE_HEADROOM_BYTES
                    } catch (_: Throwable) {
                        Long.MAX_VALUE
                    }
                    if (entrySize > avail) {
                        if (throwInstallFailureOnNoSpace) {
                            throw GameList.InstallFailureException(R.string.import_no_space)
                        }
                        throw IOException("ENOSPC: Insufficient storage space")
                    }
                }

                val outFile = File(destCanonical, parts.joinToString(File.separator)).canonicalFile
                if (outFile == destCanonical || !outFile.path.startsWith(rootPrefix)) {
                    throw IOException("Archive entry escapes destination directory: $decoded")
                }

                outFile.parentFile?.let { parent ->
                    if (createdDirs.add(parent.path)) {
                        parent.mkdirs()
                    }
                }

                currentRelPath = relPath
                onEntryAccepted?.invoke(outFile, relPath)

                if (onProgress != null) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressMs >= 150L) {
                        lastProgressMs = now
                        onProgress(latestBytesWritten, currentRelPath)
                    }
                }

                return outFile.path
            }

            override fun onProgress(totalBytesWritten: Long) {
                latestBytesWritten = totalBytesWritten
                checkCancelled?.invoke()
                if (onProgress != null && currentRelPath.isNotEmpty()) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressMs >= 150L) {
                        lastProgressMs = now
                        onProgress(totalBytesWritten, currentRelPath)
                    }
                }
            }
        }
    }

    private fun runNativeExtract(
        fd: Int,
        callback: NativeCallback,
        throwInstallFailureOnNoSpace: Boolean
    ): Int {
        try {
            return nativeExtractFd(fd, callback)
        } catch (e: IOException) {
            val msg = e.message ?: ""
            if (msg.startsWith("ENOSPC:")) {
                if (throwInstallFailureOnNoSpace) {
                    throw GameList.InstallFailureException(R.string.import_no_space)
                }
            }
            throw e
        }
    }
}
