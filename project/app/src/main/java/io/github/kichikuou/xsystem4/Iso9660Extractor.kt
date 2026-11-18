package io.github.kichikuou.xsystem4

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

object Iso9660Extractor {
    private const val SECTOR_DATA_SIZE = 2048
    private const val STREAM_BUF_SECTORS = 256 // 512 KiB per read batch
    private const val MIN_FREE_HEADROOM_BYTES = 16L * 1024L * 1024L

    private data class SectorLayout(val stride: Int, val dataOffset: Int)

    private data class DirEntry(
        val name: String,
        val isDir: Boolean,
        val extents: MutableList<Pair<Long, Long>>,
        var lastWasMultiExtent: Boolean
    )

    private data class FileExtractTask(
        val relPath: String,
        val target: File,
        val extents: List<Pair<Long, Long>>,
        val totalSize: Long
    )

    private val CANDIDATE_LAYOUTS = listOf(
        SectorLayout(2048, 0),
        SectorLayout(2352, 16),
        SectorLayout(2352, 24),
        SectorLayout(2448, 16),
        SectorLayout(2448, 24)
    )

    fun isIsoOrMdfName(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return lower.endsWith(".iso") || lower.endsWith(".mdf")
    }

    fun isIsoOrMdfChannel(ch: FileChannel): Boolean {
        return try {
            openRootDescriptor(ch) != null
        } catch (_: Throwable) {
            false
        }
    }

    /** Fast metadata-only check to test if an ISO/MDF disc image contains a matching game directory. */
    @JvmOverloads
    fun containsGameRoot(
        file: File,
        expectedAinName: String? = null,
        targetGameId: String? = null
    ): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val ch = fis.channel
                val desc = openRootDescriptor(ch) ?: return false
                val queue = ArrayDeque<Pair<Long, Long>>()
                queue.add(desc.rootLba to desc.rootSize)
                var hasUnclassifiedSys3Root = false
                var hasIsoKichikuMarker = false
                while (queue.isNotEmpty()) {
                    val (dlba, dsize) = queue.removeFirst()
                    val entries = parseDirectory(readExtentBytes(ch, desc.layout, dlba, dsize), desc.joliet)
                    var hasIni = false
                    var hasMatchingAin = false
                    var hasSys3Scenario = false
                    val dirFileNames = ArrayList<String>()
                    val entryByLowerName = HashMap<String, DirEntry>()
                    var bgmDirEntry: DirEntry? = null
                    for (e in entries) {
                        if (e.isDir) {
                            if (e.extents.isNotEmpty()) {
                                queue.add(e.extents[0])
                                if (e.name.equals("bgm", ignoreCase = true)) {
                                    bgmDirEntry = e
                                }
                            }
                        } else if (!e.name.startsWith('.')) {
                            dirFileNames.add(e.name)
                            entryByLowerName[e.name.lowercase(Locale.ROOT)] = e
                            if (e.name.lowercase(Locale.ROOT).startsWith("kichiku_")) {
                                hasIsoKichikuMarker = true
                            }
                            if (e.name.equals("AliceStart.ini", ignoreCase = true) ||
                                e.name.equals("System40.ini", ignoreCase = true)
                            ) {
                                hasIni = true
                            }
                            if (e.name.endsWith(".ain", ignoreCase = true) &&
                                !e.name.equals("System39.ain", ignoreCase = true)
                            ) {
                                if (expectedAinName.isNullOrBlank() ||
                                    e.name.equals(expectedAinName, ignoreCase = true)
                                ) {
                                    hasMatchingAin = true
                                }
                            }
                            if (e.name.endsWith("sa.ald", ignoreCase = true) ||
                                e.name.equals("adisk.ald", ignoreCase = true)
                            ) {
                                if (expectedAinName.isNullOrBlank() ||
                                    e.name.equals(expectedAinName, ignoreCase = true)
                                ) {
                                    hasSys3Scenario = true
                                }
                            }
                        }
                    }
                    if (targetGameId == GameCatalog.ID_KICHIKUOU) {
                        val playlistEntry = entryByLowerName["playlist.txt"]
                        if (playlistEntry != null) {
                            val raw = readEntrySmallBytes(ch, desc.layout, playlistEntry)
                            if (raw != null && String(raw, Charsets.ISO_8859_1).lowercase(Locale.ROOT).contains("kichiku_")) {
                                hasIsoKichikuMarker = true
                            }
                        }
                    }
                    if ((hasIni && hasMatchingAin) || hasSys3Scenario) {
                        if (targetGameId.isNullOrBlank() || targetGameId.startsWith(GameCatalog.PREFIX_CUSTOM)) {
                            return true
                        }
                        val bgmNames = if (targetGameId == GameCatalog.ID_KICHIKUOU &&
                            bgmDirEntry != null &&
                            bgmDirEntry.extents.isNotEmpty()
                        ) {
                            val (blba, bsize) = bgmDirEntry.extents[0]
                            parseDirectory(readExtentBytes(ch, desc.layout, blba, bsize), desc.joliet)
                                .filter { !it.isDir }
                                .map { it.name }
                        } else {
                            emptyList()
                        }
                        val readSmallFile: (String) -> ByteArray? = { name ->
                            entryByLowerName[name.lowercase(Locale.ROOT)]?.let {
                                readEntrySmallBytes(ch, desc.layout, it)
                            }
                        }
                        if (GameCatalog.matchesTargetGameFiles(
                                targetGameId = targetGameId,
                                fileNames = dirFileNames,
                                bgmFileNames = bgmNames,
                                readSmallFile = readSmallFile
                            )
                        ) {
                            return true
                        }
                        if (hasSys3Scenario &&
                            GameCatalog.classifyGameRootFiles(dirFileNames, bgmNames, readSmallFile) == null
                        ) {
                            hasUnclassifiedSys3Root = true
                        }
                    }
                }
                if (targetGameId == GameCatalog.ID_KICHIKUOU && hasUnclassifiedSys3Root && hasIsoKichikuMarker) {
                    return true
                }
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun readEntrySmallBytes(
        ch: FileChannel,
        layout: SectorLayout,
        entry: DirEntry,
        maxBytes: Long = 256L * 1024L
    ): ByteArray? {
        if (entry.extents.isEmpty()) return null
        val total = entry.extents.sumOf { it.second }
        if (total <= 0L || total > maxBytes) return null
        if (entry.extents.size == 1) {
            val (lba, len) = entry.extents[0]
            return readExtentBytes(ch, layout, lba, len)
        }
        val out = ByteArrayOutputStream(total.toInt())
        for ((lba, len) in entry.extents) {
            out.write(readExtentBytes(ch, layout, lba, len))
        }
        return out.toByteArray()
    }

    fun extractFileToDirectory(
        isoFile: File,
        outDir: File,
        shouldSkip: (String) -> Boolean = { NativeGameImport.skip(it) },
        throwInstallFailureOnNoSpace: Boolean = false,
        checkCancelled: (() -> Unit)? = null,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)? = null,
        onProgress: ((doneBytes: Long, currentName: String) -> Unit)? = null
    ): Int {
        if (!isoFile.isFile) throw IOException("ISO/MDF file not found: ${isoFile.name}")
        FileInputStream(isoFile).use { fis ->
            return extractChannelToDirectory(
                ch = fis.channel,
                outDir = outDir,
                shouldSkip = shouldSkip,
                throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
                checkCancelled = checkCancelled,
                onEntryAccepted = onEntryAccepted,
                onProgress = onProgress
            )
        }
    }

    fun extractUriToDirectory(
        context: Context,
        uri: Uri,
        outDir: File,
        tempScratchParent: File = outDir.parentFile ?: context.cacheDir,
        shouldSkip: (String) -> Boolean = { NativeGameImport.skip(it) },
        throwInstallFailureOnNoSpace: Boolean = false,
        checkCancelled: (() -> Unit)? = null,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)? = null,
        onProgress: ((doneBytes: Long, currentName: String) -> Unit)? = null
    ): Int {
        // Try zero-copy seekable FileChannel via ContentResolver first
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Throwable) {
            null
        }
        if (pfd != null) {
            pfd.use {
                val fis = FileInputStream(it.fileDescriptor)
                val ch = fis.channel
                val seekable = try {
                    ch.size() >= 18L * SECTOR_DATA_SIZE && run {
                        ch.position(16L * SECTOR_DATA_SIZE)
                        true
                    }
                } catch (_: Throwable) {
                    false
                }
                if (seekable) {
                    return extractChannelToDirectory(
                        ch = ch,
                        outDir = outDir,
                        shouldSkip = shouldSkip,
                        throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
                        checkCancelled = checkCancelled,
                        onEntryAccepted = onEntryAccepted,
                        onProgress = onProgress
                    )
                }
            }
        }

        // Fallback only for non-seekable SAF providers: stream to temporary file then extract and delete
        tempScratchParent.mkdirs()
        val tempIso = File(tempScratchParent, ".tmp_disc_${System.nanoTime()}.mdf")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open disc image URI")
            input.use { src ->
                tempIso.outputStream().buffered().use { dst ->
                    val buf = ByteArray(256 * 1024)
                    var copied = 0L
                    var lastTick = 0L
                    while (true) {
                        checkCancelled?.invoke()
                        val n = src.read(buf)
                        if (n < 0) break
                        dst.write(buf, 0, n)
                        copied += n
                        val now = SystemClock.elapsedRealtime()
                        if (onProgress != null && now - lastTick >= 200) {
                            onProgress(copied, "${copied / (1024 * 1024)} MB")
                            lastTick = now
                        }
                    }
                }
            }
            return extractFileToDirectory(
                isoFile = tempIso,
                outDir = outDir,
                shouldSkip = shouldSkip,
                throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
                checkCancelled = checkCancelled,
                onEntryAccepted = onEntryAccepted,
                onProgress = onProgress
            )
        } finally {
            tempIso.delete()
        }
    }

    private data class RootDescriptor(
        val layout: SectorLayout,
        val rootLba: Long,
        val rootSize: Long,
        val joliet: Boolean
    )

    private fun openRootDescriptor(ch: FileChannel): RootDescriptor? {
        val fileSize = ch.size()
        val layout = CANDIDATE_LAYOUTS.firstOrNull { cand ->
            val probePos = 16L * cand.stride + cand.dataOffset
            if (probePos + 8 > fileSize) return@firstOrNull false
            val hdr = readExact(ch, probePos, 8) ?: return@firstOrNull false
            hdr[1] == 'C'.code.toByte() &&
                hdr[2] == 'D'.code.toByte() &&
                hdr[3] == '0'.code.toByte() &&
                hdr[4] == '0'.code.toByte() &&
                hdr[5] == '1'.code.toByte()
        } ?: return null

        var pvdLba = -1L
        var pvdSize = 0L
        var svdLba = -1L
        var svdSize = 0L

        for (sec in 16..64) {
            val secData = readLogicalSectors(ch, layout, sec.toLong(), SECTOR_DATA_SIZE) ?: break
            if (secData[1] != 'C'.code.toByte() ||
                secData[2] != 'D'.code.toByte() ||
                secData[3] != '0'.code.toByte() ||
                secData[4] != '0'.code.toByte() ||
                secData[5] != '1'.code.toByte()
            ) {
                break
            }
            val type = secData[0].toInt() and 0xFF
            if (type == 255) break
            if (type == 1) {
                pvdLba = le32u(secData, 158)
                pvdSize = le32u(secData, 166)
            } else if (type == 2) {
                val e0 = secData[88].toInt() and 0xFF
                val e1 = secData[89].toInt() and 0xFF
                val e2 = secData[90].toInt() and 0xFF
                if (e0 == 0x25 && e1 == 0x2F && (e2 == 0x40 || e2 == 0x43 || e2 == 0x45)) {
                    svdLba = le32u(secData, 158)
                    svdSize = le32u(secData, 166)
                }
            }
        }

        return when {
            svdLba >= 0 && svdSize > 0 -> RootDescriptor(layout, svdLba, svdSize, true)
            pvdLba >= 0 && pvdSize > 0 -> RootDescriptor(layout, pvdLba, pvdSize, false)
            else -> null
        }
    }

    fun extractChannelToDirectory(
        ch: FileChannel,
        outDir: File,
        shouldSkip: (String) -> Boolean,
        throwInstallFailureOnNoSpace: Boolean = false,
        checkCancelled: (() -> Unit)? = null,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)? = null,
        onProgress: ((doneBytes: Long, currentName: String) -> Unit)? = null
    ): Int {
        val desc = openRootDescriptor(ch)
            ?: throw IOException("Unsupported or invalid ISO9660/MDF image")
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw IOException("Cannot create output directory: ${outDir.path}")
        }
        val outCanonical = outDir.canonicalFile
        val rootPrefix = outCanonical.path.let { if (it.endsWith(File.separator)) it else it + File.separator }

        val tasks = mutableListOf<FileExtractTask>()
        val dirQueue = ArrayDeque<Triple<String, Long, Long>>()
        dirQueue.add(Triple("", desc.rootLba, desc.rootSize))
        var totalBytes = 0L

        while (dirQueue.isNotEmpty()) {
            checkCancelled?.invoke()
            val (prefix, dlba, dsize) = dirQueue.removeFirst()
            val rawDir = readExtentBytes(ch, desc.layout, dlba, dsize)
            val entries = parseDirectory(rawDir, desc.joliet)
            for (e in entries) {
                val rel = if (prefix.isEmpty()) e.name else "$prefix/${e.name}"
                if (shouldSkip(rel)) continue
                val dest = File(outCanonical, rel).canonicalFile
                if (!dest.path.startsWith(rootPrefix) && dest != outCanonical) {
                    throw IOException("Illegal path in disc image: $rel")
                }
                if (e.isDir) {
                    if (!dest.exists() && !dest.mkdirs()) {
                        throw IOException("Cannot create directory: $rel")
                    }
                    if (e.extents.isNotEmpty()) {
                        val (clba, csize) = e.extents[0]
                        dirQueue.add(Triple(rel, clba, csize))
                    }
                } else {
                    val fileSize = e.extents.sumOf { it.second }
                    totalBytes += fileSize
                    tasks.add(FileExtractTask(rel, dest, e.extents, fileSize))
                }
            }
        }

        if (totalBytes > 0L) {
            val avail = try {
                StatFs(outCanonical.path).availableBytes - MIN_FREE_HEADROOM_BYTES
            } catch (_: Throwable) {
                Long.MAX_VALUE
            }
            if (totalBytes > avail) {
                if (throwInstallFailureOnNoSpace) {
                    throw GameList.InstallFailureException(R.string.import_no_space)
                }
                throw IOException("ENOSPC: Insufficient storage space")
            }
        }

        val ioBuf = ByteArray(STREAM_BUF_SECTORS * SECTOR_DATA_SIZE)
        var doneBytes = 0L
        var lastTick = 0L

        for (task in tasks) {
            checkCancelled?.invoke()
            task.target.parentFile?.let { p ->
                if (!p.exists() && !p.mkdirs()) {
                    throw IOException("Cannot create parent directory for ${task.relPath}")
                }
            }
            onEntryAccepted?.invoke(task.target, task.relPath)
            if (onProgress != null) {
                val nowStart = SystemClock.elapsedRealtime()
                if (nowStart - lastTick >= 150) {
                    onProgress(doneBytes, task.relPath)
                    lastTick = nowStart
                }
            }
            task.target.outputStream().buffered(256 * 1024).use { out ->
                for ((extLba, extLen) in task.extents) {
                    var remaining = extLen
                    var curLba = extLba
                    while (remaining > 0L) {
                        checkCancelled?.invoke()
                        if (desc.layout.stride == SECTOR_DATA_SIZE && desc.layout.dataOffset == 0) {
                            val chunk = minOf(remaining, ioBuf.size.toLong()).toInt()
                            val sectorsUsed = ((chunk + SECTOR_DATA_SIZE - 1) / SECTOR_DATA_SIZE).toLong()
                            readFullyAt(ch, curLba * SECTOR_DATA_SIZE, ioBuf, chunk)
                            out.write(ioBuf, 0, chunk)
                            curLba += sectorsUsed
                            remaining -= chunk
                            doneBytes += chunk
                        } else {
                            val sectors = minOf(
                                ((remaining + SECTOR_DATA_SIZE - 1) / SECTOR_DATA_SIZE),
                                STREAM_BUF_SECTORS.toLong()
                            ).toInt()
                            for (s in 0 until sectors) {
                                val secOff = (curLba + s) * desc.layout.stride + desc.layout.dataOffset
                                val toWrite = minOf(remaining, SECTOR_DATA_SIZE.toLong()).toInt()
                                readFullyAt(ch, secOff, ioBuf, toWrite)
                                out.write(ioBuf, 0, toWrite)
                                remaining -= toWrite
                                doneBytes += toWrite
                            }
                            curLba += sectors
                        }
                        if (onProgress != null) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTick >= 150) {
                                onProgress(doneBytes, task.relPath)
                                lastTick = now
                            }
                        }
                    }
                }
            }
        }
        return tasks.size
    }

    private fun parseDirectory(buf: ByteArray, joliet: Boolean): List<DirEntry> {
        val result = mutableListOf<DirEntry>()
        var pos = 0
        while (pos + 33 <= buf.size) {
            val recLen = buf[pos].toInt() and 0xFF
            if (recLen == 0) {
                pos = ((pos / SECTOR_DATA_SIZE) + 1) * SECTOR_DATA_SIZE
                continue
            }
            if (pos + recLen > buf.size) break
            val lba = le32u(buf, pos + 2)
            val dataLen = le32u(buf, pos + 10)
            val flags = buf[pos + 25].toInt() and 0xFF
            val isDir = (flags and 0x02) != 0
            val multiExtent = (flags and 0x80) != 0
            val idLen = buf[pos + 32].toInt() and 0xFF
            if (idLen > 0 && pos + 33 + idLen <= pos + recLen) {
                val idBytes = buf.copyOfRange(pos + 33, pos + 33 + idLen)
                val isSelfOrParent = idLen == 1 && (idBytes[0] == 0.toByte() || idBytes[0] == 1.toByte())
                if (!isSelfOrParent) {
                    val name = sanitizeName(decodeIsoName(idBytes, joliet))
                    if (name != null) {
                        val last = result.lastOrNull()
                        if (!isDir && last != null && !last.isDir && last.lastWasMultiExtent && last.name == name) {
                            last.extents.add(lba to dataLen)
                            last.lastWasMultiExtent = multiExtent
                        } else {
                            result.add(
                                DirEntry(
                                    name = name,
                                    isDir = isDir,
                                    extents = mutableListOf(lba to dataLen),
                                    lastWasMultiExtent = multiExtent
                                )
                            )
                        }
                    }
                }
            }
            pos += recLen
        }
        return result
    }

    private fun decodeIsoName(bytes: ByteArray, joliet: Boolean): String {
        val raw = if (joliet) {
            String(bytes, Charsets.UTF_16BE).trimEnd('\u0000')
        } else {
            ArchiveExtractor.decodeBestCjkText(bytes, preferGbkOnTie = false)
        }
        val semi = raw.lastIndexOf(';')
        return if (semi > 0 && raw.substring(semi + 1).all { it in '0'..'9' }) {
            raw.substring(0, semi)
        } else {
            raw
        }
    }

    private fun sanitizeName(name: String): String? {
        val trimmed = name.trimEnd('\u0000')
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return null
        if ('/' in trimmed || '\\' in trimmed || '\u0000' in trimmed) return null
        return trimmed
    }

    private fun readExtentBytes(ch: FileChannel, layout: SectorLayout, lba: Long, len: Long): ByteArray {
        val safeLen = len.coerceAtMost(32L * 1024 * 1024).toInt()
        if (safeLen <= 0) return ByteArray(0)
        if (layout.stride == SECTOR_DATA_SIZE && layout.dataOffset == 0) {
            val buf = ByteArray(safeLen)
            readFullyAt(ch, lba * SECTOR_DATA_SIZE, buf, safeLen)
            return buf
        }
        val out = ByteArrayOutputStream(safeLen)
        val tmp = ByteArray(SECTOR_DATA_SIZE)
        var remaining = safeLen
        var curLba = lba
        while (remaining > 0) {
            val n = minOf(remaining, SECTOR_DATA_SIZE)
            readFullyAt(ch, curLba * layout.stride + layout.dataOffset, tmp, n)
            out.write(tmp, 0, n)
            curLba++
            remaining -= n
        }
        return out.toByteArray()
    }

    private fun readLogicalSectors(ch: FileChannel, layout: SectorLayout, lba: Long, len: Int): ByteArray? {
        val off = lba * layout.stride + layout.dataOffset
        if (off + len > ch.size()) return null
        return readExact(ch, off, len)
    }

    private fun readExact(ch: FileChannel, offset: Long, len: Int): ByteArray? {
        if (offset < 0 || offset + len > ch.size()) return null
        val buf = ByteArray(len)
        readFullyAt(ch, offset, buf, len)
        return buf
    }

    private fun readFullyAt(ch: FileChannel, offset: Long, dst: ByteArray, len: Int) {
        val bb = ByteBuffer.wrap(dst, 0, len)
        var pos = offset
        while (bb.hasRemaining()) {
            val n = ch.read(bb, pos)
            if (n < 0) throw IOException("Unexpected EOF while reading disc image at $pos")
            pos += n
        }
    }

    private fun le32u(b: ByteArray, off: Int): Long {
        return (b[off].toLong() and 0xFFL) or
            ((b[off + 1].toLong() and 0xFFL) shl 8) or
            ((b[off + 2].toLong() and 0xFFL) shl 16) or
            ((b[off + 3].toLong() and 0xFFL) shl 24)
    }
}
