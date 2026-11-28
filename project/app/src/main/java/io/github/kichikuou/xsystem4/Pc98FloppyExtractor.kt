package io.github.kichikuou.xsystem4

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.Locale

/**
 * Pure-Kotlin reader/extractor for PC-98 Anex86 `.fdi` floppy disk images (FAT12/FAT16)
 * and companion PC-98 2048x2048 1bpp `FONT.bmp` bitmap fonts.
 */
object Pc98FloppyExtractor {
    private const val MAX_FDI_HEADER_BYTES = 65536
    private const val MAX_FLOPPY_DATA_BYTES = 32L * 1024L * 1024L
    private const val MAX_DIR_DEPTH = 16
    private const val MIN_FREE_HEADROOM_BYTES = 16L * 1024L * 1024L

    private val CP932_CHARSET: Charset by lazy {
        runCatching { Charset.forName("windows-31j") }.getOrElse { Charset.forName("Shift_JIS") }
    }

    private val DOS_BOOT_SYSTEM_FILES = setOf(
        "io.sys", "msdos.sys", "command.com", "autoexec.bat", "config.sys"
    )

    private data class FloppyVolume(
        val headerSize: Long,
        val fddSize: Long,
        val bps: Int,
        val spc: Int,
        val fatOffset: Long,
        val fatBytes: Int,
        val rootOffset: Long,
        val rootBytes: Int,
        val dataOffset: Long,
        val totalClusters: Int,
        val isFat16: Boolean,
        val fat: ByteArray
    )

    private data class FatEntry(
        val name: String,
        val isDir: Boolean,
        val startCluster: Int,
        val size: Long
    )

    fun isFdiName(fileName: String): Boolean {
        var lower = fileName.lowercase(Locale.ROOT)
        if (lower.endsWith(".fdi")) return true
        var strippedTemp = false
        while (lower.endsWith(".part") || lower.endsWith(".tmp")) {
            lower = lower.substringBeforeLast('.')
            strippedTemp = true
        }
        if (!strippedTemp) return false
        if (lower.endsWith(".fdi")) return true
        val lastDot = lower.lastIndexOf('.')
        if (lastDot > 0) {
            val tag = lower.substring(lastDot + 1)
            if (tag.isNotEmpty() && tag.all { it in '0'..'9' || it in 'a'..'f' }) {
                if (lower.substring(0, lastDot).endsWith(".fdi")) return true
            }
        }
        return false
    }

    fun isFdiFile(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            FileInputStream(file).use { fis -> isFdiChannel(fis.channel) }
        } catch (_: Throwable) {
            false
        }
    }

    fun isFdiChannel(ch: FileChannel): Boolean {
        return try {
            openVolume(ch) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks whether [file] is a valid PC-98 2048x2048 1bpp `FONT.bmp` containing GB2312 Level-2
     * Chinese glyphs in JIS rows 0x75..0x77 (empty in standard Japanese JIS X 0208 fonts).
     */
    fun isChinesePc98FontBmp(file: File): Boolean {
        if (!file.isFile || file.length() < 62L + 256L * 2048L) return false
        return try {
            FileInputStream(file).use { fis ->
                val ch = fis.channel
                val hdr = readExact(ch, 0L, 62) ?: return false
                if (hdr[0] != 'B'.code.toByte() || hdr[1] != 'M'.code.toByte()) return false
                val dataOff = le32(hdr, 10)
                val dibSize = le32(hdr, 14)
                val width = le32Signed(hdr, 18)
                val height = le32Signed(hdr, 22)
                val planes = le16(hdr, 26)
                val bpp = le16(hdr, 28)
                val compression = le32(hdr, 30)
                if (dibSize < 40L || width != 2048 || (height != 2048 && height != -2048) ||
                    planes != 1 || bpp != 1 || compression != 0L || dataOff < 14L + dibSize + 8L
                ) {
                    return false
                }
                if (dataOff + 256L * 2048L > ch.size()) return false
                val pal = readExact(ch, 14L + dibSize, 8) ?: return false
                val lum0 = (pal[2].toInt() and 0xFF) * 30 + (pal[1].toInt() and 0xFF) * 59 + (pal[0].toInt() and 0xFF) * 11
                val lum1 = (pal[6].toInt() and 0xFF) * 30 + (pal[5].toInt() and 0xFF) * 59 + (pal[4].toInt() and 0xFF) * 11
                val bgByte = if (lum0 < lum1) 0xFF else 0x00
                val topDown = height < 0
                val raw = readExact(ch, dataOff, 256 * 2048) ?: return false
                var occupied = 0
                for (cx in 85..87) {
                    val colByte = cx * 2
                    for (ry in 0x21..0x7E) {
                        var nonEmpty = false
                        val gy = ry * 16
                        for (dy in 0 until 16) {
                            val y = gy + dy
                            val srcY = if (topDown) y else (2047 - y)
                            val rowOff = srcY * 256 + colByte
                            if ((raw[rowOff].toInt() and 0xFF) != bgByte ||
                                (raw[rowOff + 1].toInt() and 0xFF) != bgByte
                            ) {
                                nonEmpty = true
                                break
                            }
                        }
                        if (nonEmpty) occupied++
                    }
                }
                occupied >= 32
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if [file] is a valid PC-98 2048x2048 1bpp `FONT.bmp` (Japanese or Chinese).
     */
    fun isValidPc98FontBmp(file: File): Boolean {
        if (!file.isFile || file.length() < 62L + 256L * 2048L) return false
        return try {
            FileInputStream(file).use { fis ->
                val ch = fis.channel
                val hdr = readExact(ch, 0L, 62) ?: return false
                if (hdr[0] != 'B'.code.toByte() || hdr[1] != 'M'.code.toByte()) return false
                val dataOff = le32(hdr, 10)
                val dibSize = le32(hdr, 14)
                val width = le32Signed(hdr, 18)
                val height = le32Signed(hdr, 22)
                val planes = le16(hdr, 26)
                val bpp = le16(hdr, 28)
                val compression = le32(hdr, 30)
                dibSize >= 40L && width == 2048 && (height == 2048 || height == -2048) &&
                    planes == 1 && bpp == 1 && compression == 0L &&
                    dataOff >= 14L + dibSize + 8L && dataOff + 256L * 2048L <= ch.size()
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Copies a companion PC-98 `FONT.bmp` (if found in [searchRoots]) into [destGameDir] when
     * [destGameDir] does not already contain one.
     */
    fun copyCompanionFontIfNeeded(searchRoots: Collection<File>, destGameDir: File): Boolean {
        if (!destGameDir.isDirectory) return false
        val existing = destGameDir.listFiles()?.any { it.isFile && it.name.equals("FONT.bmp", true) } == true
        if (existing) return false
        val destCanonical = runCatching { destGameDir.canonicalPath }.getOrNull() ?: destGameDir.path
        for (root in searchRoots) {
            if (!root.exists()) continue
            val font = root.walkTopDown()
                .maxDepth(4)
                .firstOrNull { f ->
                    f.isFile &&
                        f.name.equals("FONT.bmp", true) &&
                        runCatching { f.canonicalPath }.getOrNull() != destCanonical &&
                        isValidPc98FontBmp(f)
                }
            if (font != null) {
                font.copyTo(File(destGameDir, "FONT.bmp"), overwrite = false)
                return true
            }
        }
        return false
    }

    @JvmOverloads
    fun containsGameRoot(
        file: File,
        expectedAinName: String? = null,
        targetGameId: String? = null,
        checkCancelled: (() -> Unit)? = null
    ): Boolean {
        return try {
            checkCancelled?.invoke()
            FileInputStream(file).use { fis ->
                val ch = fis.channel
                val vol = openVolume(ch) ?: return false
                val queue = ArrayDeque<Pair<ByteArray, Int>>()
                val visitedDirClusters = HashSet<Int>()
                val rootBytes = readExact(ch, vol.rootOffset, vol.rootBytes) ?: return false
                queue.add(rootBytes to 0)
                while (queue.isNotEmpty()) {
                    checkCancelled?.invoke()
                    val (dirBytes, depth) = queue.removeFirst()
                    val entries = parseDirectory(dirBytes)
                    val dirFileNames = ArrayList<String>()
                    val entryByLower = HashMap<String, FatEntry>()
                    for (e in entries) {
                        checkCancelled?.invoke()
                        if (e.isDir) {
                            if (depth < MAX_DIR_DEPTH && e.startCluster >= 2 && visitedDirClusters.add(e.startCluster)) {
                                val sub = readClusterChain(ch, vol, e.startCluster, null, checkCancelled)
                                queue.add(sub to (depth + 1))
                            }
                        } else if (!e.name.startsWith('.')) {
                            dirFileNames.add(e.name)
                            entryByLower[e.name.lowercase(Locale.ROOT)] = e
                        }
                    }
                    val hasLegacy = GameCatalog.isLegacySystem3GameFiles(dirFileNames)
                    val hasSys35 = dirFileNames.any { GameCatalog.isSystem35Scenario(it) }
                    if (hasLegacy || hasSys35) {
                        if (!expectedAinName.isNullOrBlank()) {
                            if (dirFileNames.none { it.equals(expectedAinName, ignoreCase = true) }) {
                                continue
                            }
                        }
                        if (targetGameId.isNullOrBlank() || targetGameId.startsWith(GameCatalog.PREFIX_CUSTOM)) {
                            return true
                        }
                        val readSmall: (String) -> ByteArray? = { name ->
                            entryByLower[name.lowercase(Locale.ROOT)]?.let { fe ->
                                val cap = if (name.equals("ADISK.DAT", true) || name.equals("BDISK.DAT", true)) {
                                    256L
                                } else {
                                    minOf(fe.size, 65536L)
                                }
                                readClusterChain(ch, vol, fe.startCluster, minOf(fe.size, cap), checkCancelled)
                            }
                        }
                        if (GameCatalog.matchesTargetGameFiles(
                                targetGameId = targetGameId,
                                fileNames = dirFileNames,
                                bgmFileNames = emptyList(),
                                readSmallFile = readSmall
                            )
                        ) {
                            return true
                        }
                    }
                }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    fun extractFileToDirectory(
        fdiFile: File,
        outDir: File,
        shouldSkip: (normalizedRelPath: String) -> Boolean = { false },
        throwInstallFailureOnNoSpace: Boolean = false,
        checkCancelled: (() -> Unit)? = null,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)? = null,
        onProgress: ((totalBytes: Long, currentEntryPath: String) -> Unit)? = null
    ): Int {
        outDir.mkdirs()
        val outCanonical = outDir.canonicalFile
        return FileInputStream(fdiFile).use { fis ->
            extractChannel(
                ch = fis.channel,
                outCanonical = outCanonical,
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
        shouldSkip: (normalizedRelPath: String) -> Boolean = { false },
        throwInstallFailureOnNoSpace: Boolean = false,
        checkCancelled: (() -> Unit)? = null,
        onEntryAccepted: ((outFile: File, normalizedRelPath: String) -> Unit)? = null,
        onProgress: ((totalBytes: Long, currentEntryPath: String) -> Unit)? = null
    ): Int {
        outDir.mkdirs()
        val outCanonical = outDir.canonicalFile
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        }
        if (pfd != null) {
            pfd.use {
                FileInputStream(it.fileDescriptor).use { fis ->
                    if (isFdiChannel(fis.channel)) {
                        return extractChannel(
                            ch = fis.channel,
                            outCanonical = outCanonical,
                            shouldSkip = shouldSkip,
                            throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
                            checkCancelled = checkCancelled,
                            onEntryAccepted = onEntryAccepted,
                            onProgress = onProgress
                        )
                    }
                }
            }
        }
        tempScratchParent.mkdirs()
        val staged = File(tempScratchParent, ".fdi_src_${java.util.UUID.randomUUID()}.fdi")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open selected FDI URI")
            input.use { src ->
                staged.outputStream().buffered().use { dst ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled?.invoke()
                        val n = src.read(buf)
                        if (n < 0) break
                        dst.write(buf, 0, n)
                    }
                }
            }
            return extractFileToDirectory(
                fdiFile = staged,
                outDir = outCanonical,
                shouldSkip = shouldSkip,
                throwInstallFailureOnNoSpace = throwInstallFailureOnNoSpace,
                checkCancelled = checkCancelled,
                onEntryAccepted = onEntryAccepted,
                onProgress = onProgress
            )
        } finally {
            staged.delete()
        }
    }

    private fun extractChannel(
        ch: FileChannel,
        outCanonical: File,
        shouldSkip: (String) -> Boolean,
        throwInstallFailureOnNoSpace: Boolean,
        checkCancelled: (() -> Unit)?,
        onEntryAccepted: ((File, String) -> Unit)?,
        onProgress: ((Long, String) -> Unit)?
    ): Int {
        val vol = openVolume(ch) ?: throw IOException("Not a valid PC-98 FDI floppy image")
        val maxTotalExtract = vol.fddSize * 2L

        data class ExtractTask(val relPath: String, val target: File, val entry: FatEntry)

        val tasks = ArrayList<ExtractTask>()
        var plannedBytes = 0L
        val visitedDirClusters = HashSet<Int>()

        fun walk(dirBytes: ByteArray, prefix: String, depth: Int) {
            if (depth > MAX_DIR_DEPTH) throw IOException("FDI directory nesting too deep")
            val entries = parseDirectory(dirBytes)
            for (e in entries) {
                checkCancelled?.invoke()
                val rel = if (prefix.isEmpty()) e.name else "$prefix/${e.name}"
                if (!e.isDir && depth == 0 && e.name.lowercase(Locale.ROOT) in DOS_BOOT_SYSTEM_FILES) {
                    continue
                }
                if (shouldSkip(rel)) continue
                val target = Iso9660Extractor.resolveSafeExtractTarget(outCanonical, rel, e.isDir)
                if (e.isDir) {
                    target.mkdirs()
                    if (e.startCluster >= 2) {
                        if (!visitedDirClusters.add(e.startCluster)) {
                            throw IOException("Cyclic directory cluster in FDI image: ${e.startCluster}")
                        }
                        val sub = readClusterChain(ch, vol, e.startCluster, null, checkCancelled)
                        walk(sub, rel, depth + 1)
                    }
                } else {
                    if (e.size > vol.fddSize) {
                        throw IOException("FDI entry size exceeds floppy capacity: $rel (${e.size})")
                    }
                    plannedBytes = Iso9660Extractor.addBytesChecked(plannedBytes, e.size)
                    if (plannedBytes > maxTotalExtract) {
                        throw IOException("FDI total extracted size exceeds safety cap")
                    }
                    tasks.add(ExtractTask(rel, target, e))
                }
            }
        }

        val rootBytes = readExact(ch, vol.rootOffset, vol.rootBytes)
            ?: throw IOException("Failed to read FDI root directory")
        walk(rootBytes, "", 0)

        if (plannedBytes > 0L) {
            val available = runCatching { StatFs(outCanonical.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (available < plannedBytes + MIN_FREE_HEADROOM_BYTES) {
                if (throwInstallFailureOnNoSpace) {
                    throw GameList.InstallFailureException(R.string.import_no_space)
                }
                throw IOException("Insufficient space to extract FDI image")
            }
        }

        var writtenTotal = 0L
        var lastProgressMs = 0L
        var extractedCount = 0
        for (task in tasks) {
            checkCancelled?.invoke()
            task.target.parentFile?.mkdirs()
            val data = if (task.entry.size == 0L || task.entry.startCluster < 2) {
                ByteArray(0)
            } else {
                readClusterChain(ch, vol, task.entry.startCluster, task.entry.size, checkCancelled)
            }
            try {
                task.target.writeBytes(data)
            } catch (e: IOException) {
                if (throwInstallFailureOnNoSpace && Iso9660Extractor.isNoSpaceException(e)) {
                    throw GameList.InstallFailureException(R.string.import_no_space)
                }
                throw e
            }
            extractedCount++
            writtenTotal += data.size
            onEntryAccepted?.invoke(task.target, task.relPath)
            val now = System.nanoTime() / 1_000_000L
            if (now - lastProgressMs >= 150L || extractedCount == tasks.size) {
                lastProgressMs = now
                onProgress?.invoke(writtenTotal, task.relPath)
            }
        }
        return extractedCount
    }

    private fun openVolume(ch: FileChannel): FloppyVolume? {
        val fileSize = ch.size()
        if (fileSize < 512L) return null
        val hdr = readExact(ch, 0L, 32) ?: return null
        val zero = le32(hdr, 0)
        val fddType = le32(hdr, 4)
        val headerSize = le32(hdr, 8)
        val fddSize = le32(hdr, 12)
        val sectorSize = le32(hdr, 16)
        val sectors = le32(hdr, 20)
        val heads = le32(hdr, 24)
        val cylinders = le32(hdr, 28)

        if (zero != 0L || fddType == 0L || fddType > 0xFFL) return null
        if (headerSize < 32L || headerSize > MAX_FDI_HEADER_BYTES.toLong()) return null
        if (fddSize <= 0L || fddSize > MAX_FLOPPY_DATA_BYTES) return null
        if (headerSize + fddSize > fileSize) return null
        if (sectorSize != 256L && sectorSize != 512L && sectorSize != 1024L) return null
        if (sectors !in 1L..64L || heads !in 1L..2L || cylinders !in 1L..256L) return null
        val geomBytes = sectorSize * sectors * heads * cylinders
        if (geomBytes <= 0L || geomBytes > fddSize) return null

        val boot = readExact(ch, headerSize, 64) ?: return null
        val bps = le16(boot, 11)
        val spc = boot[13].toInt() and 0xFF
        val rsv = le16(boot, 14)
        val nfats = boot[16].toInt() and 0xFF
        val rootEnt = le16(boot, 17)
        val tot16 = le16(boot, 19)
        val spf = le16(boot, 22)
        val tot32 = le32(boot, 32)

        if (bps != 256 && bps != 512 && bps != 1024) return null
        if (spc == 0 || spc > 128 || (spc and (spc - 1)) != 0) return null
        if (rsv < 1 || nfats !in 1..2 || rootEnt < 1 || spf < 1) return null

        val totSectors = if (tot16 != 0) tot16.toLong() else tot32
        if (totSectors <= 0L || totSectors * bps.toLong() > fddSize) return null

        val fatOffset = headerSize + rsv.toLong() * bps.toLong()
        val fatBytes = spf * bps
        val rootOffset = fatOffset + nfats.toLong() * fatBytes.toLong()
        val rootBytes = rootEnt * 32
        val rootSecs = (rootBytes + bps - 1) / bps
        val dataOffset = rootOffset + rootSecs.toLong() * bps.toLong()
        val nonDataSecs = rsv.toLong() + nfats.toLong() * spf.toLong() + rootSecs.toLong()
        if (totSectors <= nonDataSecs) return null
        val totalClustersLong = (totSectors - nonDataSecs) / spc.toLong()
        if (totalClustersLong < 2L || totalClustersLong >= 65525L) return null
        if (dataOffset >= headerSize + fddSize) return null
        val fat = readExact(ch, fatOffset, fatBytes) ?: return null

        return FloppyVolume(
            headerSize = headerSize,
            fddSize = fddSize,
            bps = bps,
            spc = spc,
            fatOffset = fatOffset,
            fatBytes = fatBytes,
            rootOffset = rootOffset,
            rootBytes = rootBytes,
            dataOffset = dataOffset,
            totalClusters = totalClustersLong.toInt(),
            isFat16 = totalClustersLong >= 4085L,
            fat = fat
        )
    }

    private fun nextCluster(vol: FloppyVolume, cluster: Int): Int {
        return if (vol.isFat16) {
            val off = cluster * 2
            if (off + 2 > vol.fat.size) 0xFFFF else le16(vol.fat, off)
        } else {
            val off = (cluster * 3) / 2
            if (off + 2 > vol.fat.size) {
                0xFFF
            } else {
                val raw = le16(vol.fat, off)
                if ((cluster and 1) != 0) (raw ushr 4) else (raw and 0x0FFF)
            }
        }
    }

    private fun readClusterChain(
        ch: FileChannel,
        vol: FloppyVolume,
        startCluster: Int,
        limitBytes: Long?,
        checkCancelled: (() -> Unit)?
    ): ByteArray {
        val clusterBytes = vol.spc * vol.bps
        val maxBytes = limitBytes ?: vol.fddSize
        val eofThreshold = if (vol.isFat16) 0xFFF8 else 0x0FF8
        val out = ByteArrayOutputStream(minOf(maxBytes, 65536L).toInt().coerceAtLeast(0))
        val visited = HashSet<Int>()
        var c = startCluster
        while (c >= 2 && c < eofThreshold && out.size().toLong() < maxBytes) {
            checkCancelled?.invoke()
            if (c >= vol.totalClusters + 2) break
            if (!visited.add(c)) throw IOException("Cyclic cluster chain in FDI image at cluster $c")
            val pos = vol.dataOffset + (c - 2).toLong() * clusterBytes.toLong()
            val remaining = maxBytes - out.size().toLong()
            val toRead = minOf(clusterBytes.toLong(), remaining).toInt()
            val chunk = readExact(ch, pos, toRead)
                ?: throw IOException("Truncated cluster $c in FDI image")
            out.write(chunk)
            c = nextCluster(vol, c)
        }
        return out.toByteArray()
    }

    private fun parseDirectory(dirBytes: ByteArray): List<FatEntry> {
        val result = ArrayList<FatEntry>()
        var off = 0
        while (off + 32 <= dirBytes.size) {
            val first = dirBytes[off].toInt() and 0xFF
            if (first == 0x00) break
            if (first == 0xE5) {
                off += 32
                continue
            }
            val attr = dirBytes[off + 11].toInt() and 0xFF
            if (attr == 0x0F || (attr and 0x08) != 0) {
                off += 32
                continue
            }
            val nameRaw = dirBytes.copyOfRange(off, off + 8)
            if ((nameRaw[0].toInt() and 0xFF) == 0x05) {
                nameRaw[0] = 0xE5.toByte()
            }
            val extRaw = dirBytes.copyOfRange(off + 8, off + 11)
            val nameLen = trimTrailingSpaces(nameRaw)
            val extLen = trimTrailingSpaces(extRaw)
            if (nameLen > 0) {
                val combined = if (extLen > 0) {
                    ByteArray(nameLen + 1 + extLen).also {
                        System.arraycopy(nameRaw, 0, it, 0, nameLen)
                        it[nameLen] = '.'.code.toByte()
                        System.arraycopy(extRaw, 0, it, nameLen + 1, extLen)
                    }
                } else {
                    nameRaw.copyOf(nameLen)
                }
                val decoded = String(combined, CP932_CHARSET).trim()
                if (decoded.isNotEmpty() && decoded != "." && decoded != ".." &&
                    !decoded.contains('/') && !decoded.contains('\\') && !decoded.contains('\u0000')
                ) {
                    val isDir = (attr and 0x10) != 0
                    val startCluster = le16(dirBytes, off + 26)
                    val size = if (isDir) 0L else le32(dirBytes, off + 28)
                    result.add(FatEntry(decoded, isDir, startCluster, size))
                }
            }
            off += 32
        }
        return result
    }

    private fun trimTrailingSpaces(bytes: ByteArray): Int {
        var len = bytes.size
        while (len > 0 && bytes[len - 1] == 0x20.toByte()) len--
        return len
    }

    private fun readExact(ch: FileChannel, offset: Long, len: Int): ByteArray? {
        if (offset < 0L || len < 0 || offset + len.toLong() > ch.size()) return null
        val buf = ByteBuffer.allocate(len)
        var pos = offset
        while (buf.hasRemaining()) {
            val n = ch.read(buf, pos)
            if (n <= 0) return null
            pos += n
        }
        return buf.array()
    }

    private fun le16(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)

    private fun le32(buf: ByteArray, off: Int): Long =
        (buf[off].toLong() and 0xFFL) or
            ((buf[off + 1].toLong() and 0xFFL) shl 8) or
            ((buf[off + 2].toLong() and 0xFFL) shl 16) or
            ((buf[off + 3].toLong() and 0xFFL) shl 24)

    private fun le32Signed(buf: ByteArray, off: Int): Int = le32(buf, off).toInt()
}
