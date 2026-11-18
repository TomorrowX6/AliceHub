package io.github.kichikuou.xsystem4

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.CancellationException

fun ByteBuffer.skip(n: Int) {
    this.position(this.position() + n)
}

class PEResourceExtractor private constructor(
    private val exeFile: File,
    private val fileLength: Long,
    private val rsrcRootFileOffset: Long,
    private val rsrcMaxLimitOffset: Long,
    private val rvaDelta: Long,
    private val sections: List<SectionRange>
) {
    private data class SectionRange(
        val name: String,
        val virtualAddress: Long,
        val virtualEnd: Long,
        val pointerToRawData: Long,
        val sizeOfRawData: Long
    )

    companion object {
        // Resource types.
        const val RT_ICON = 3
        const val RT_GROUP_ICON = 14

        private const val MAX_SECTIONS = 512
        private const val MAX_RESOURCE_DEPTH = 8
        private const val MAX_DIR_ENTRIES = 4096
        private const val MAX_ICON_COUNT = 256
        private const val MAX_RESOURCE_SIZE = 16 * 1024 * 1024
        private const val MAX_TOTAL_ICO_SIZE = 32 * 1024 * 1024

        private fun readExactBuffer(ch: FileChannel, fileLength: Long, offset: Long, size: Int): ByteBuffer? {
            if (size < 0 || offset < 0L || offset + size.toLong() > fileLength) {
                return null
            }
            val arr = ByteArray(size)
            val bb = ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN)
            var pos = offset
            while (bb.hasRemaining()) {
                val n = ch.read(bb, pos)
                if (n < 0) return null
                pos += n
            }
            bb.flip()
            return bb
        }

        // Returns null if the given file is not in PE format or has no resource section.
        fun create(exeFile: File): PEResourceExtractor? {
            return try {
                if (!exeFile.isFile) return null
                val fileLength = exeFile.length()
                if (fileLength < 0x40L) return null

                RandomAccessFile(exeFile, "r").use { raf ->
                    val ch = raf.channel

                    // MS-DOS Stub (64 bytes)
                    val dosBuf = readExactBuffer(ch, fileLength, 0L, 0x40) ?: return null
                    val mzMagic = dosBuf.getShort(0).toInt() and 0xFFFF
                    if (mzMagic != 0x5A4D) { // "MZ"
                        return null
                    }
                    val ofsPe = dosBuf.getInt(0x3C).toLong()
                    if (ofsPe < 4L || ofsPe + 24L > fileLength) {
                        return null
                    }

                    // PE signature (4 bytes) + COFF file header (20 bytes)
                    val peCoffBuf = readExactBuffer(ch, fileLength, ofsPe, 24) ?: return null
                    if (peCoffBuf.getInt(0) != 0x00004550) { // "PE\0\0"
                        return null
                    }
                    val numberOfSections = peCoffBuf.getShort(6).toInt() and 0xFFFF
                    if (numberOfSections !in 1..MAX_SECTIONS) {
                        return null
                    }
                    val sizeOfOptionalHeader = peCoffBuf.getShort(20).toInt() and 0xFFFF

                    // Optional header (inspect Resource DataDirectory[2] if present)
                    val ofsOpt = ofsPe + 24L
                    if (ofsOpt + sizeOfOptionalHeader.toLong() > fileLength) {
                        return null
                    }
                    var dataDirRsrcRva = 0L
                    if (sizeOfOptionalHeader >= 2) {
                        val optReadLen = sizeOfOptionalHeader.coerceAtMost(256)
                        val optBuf = readExactBuffer(ch, fileLength, ofsOpt, optReadLen) ?: return null
                        val optMagic = optBuf.getShort(0).toInt() and 0xFFFF
                        val rsrcDirOffset = when (optMagic) {
                            0x010B -> if (optReadLen >= 96 + 3 * 8 && (optBuf.getInt(92).toLong() and 0xFFFFFFFFL) >= 3L) 96 + 2 * 8 else -1
                            0x020B -> if (optReadLen >= 112 + 3 * 8 && (optBuf.getInt(108).toLong() and 0xFFFFFFFFL) >= 3L) 112 + 2 * 8 else -1
                            else -> -1
                        }
                        if (rsrcDirOffset >= 0) {
                            dataDirRsrcRva = optBuf.getInt(rsrcDirOffset).toLong() and 0xFFFFFFFFL
                        }
                    }

                    // Section table (40 bytes per section)
                    val ofsSectionTable = ofsOpt + sizeOfOptionalHeader.toLong()
                    val sectionTableBytesLong = numberOfSections.toLong() * 40L
                    if (ofsSectionTable < 0L || ofsSectionTable + sectionTableBytesLong > fileLength) {
                        return null
                    }
                    val secBuf = readExactBuffer(ch, fileLength, ofsSectionTable, sectionTableBytesLong.toInt())
                        ?: return null

                    val sections = ArrayList<SectionRange>(numberOfSections)
                    val nameBytes = ByteArray(8)
                    for (i in 0 until numberOfSections) {
                        val base = i * 40
                        secBuf.position(base)
                        secBuf.get(nameBytes)
                        val name = String(nameBytes, Charsets.ISO_8859_1)
                        val virtualSize = secBuf.getInt(base + 8).toLong() and 0xFFFFFFFFL
                        val virtualAddress = secBuf.getInt(base + 12).toLong() and 0xFFFFFFFFL
                        val sizeOfRawData = secBuf.getInt(base + 16).toLong() and 0xFFFFFFFFL
                        val pointerToRawData = secBuf.getInt(base + 20).toLong() and 0xFFFFFFFFL
                        val span = maxOf(virtualSize, sizeOfRawData)
                        if (span > 0L && sizeOfRawData > 0L && pointerToRawData < fileLength) {
                            sections.add(
                                SectionRange(
                                    name = name,
                                    virtualAddress = virtualAddress,
                                    virtualEnd = virtualAddress + span,
                                    pointerToRawData = pointerToRawData,
                                    sizeOfRawData = sizeOfRawData.coerceAtMost(fileLength - pointerToRawData)
                                )
                            )
                        }
                    }

                    val rsrcSection = (if (dataDirRsrcRva != 0L) {
                        sections.firstOrNull { dataDirRsrcRva >= it.virtualAddress && dataDirRsrcRva < it.virtualEnd }
                    } else null) ?: sections.firstOrNull { it.name == ".rsrc\u0000\u0000\u0000" }
                        ?: return null

                    val intraSectionOffset = if (dataDirRsrcRva >= rsrcSection.virtualAddress &&
                        dataDirRsrcRva < rsrcSection.virtualAddress + rsrcSection.sizeOfRawData
                    ) {
                        dataDirRsrcRva - rsrcSection.virtualAddress
                    } else {
                        0L
                    }

                    val rsrcRootFileOffset = rsrcSection.pointerToRawData + intraSectionOffset
                    val rsrcMaxLimitOffset = (rsrcSection.pointerToRawData + rsrcSection.sizeOfRawData)
                        .coerceAtMost(fileLength)
                    if (rsrcRootFileOffset < 0L || rsrcRootFileOffset + 16L > rsrcMaxLimitOffset) {
                        return null
                    }

                    PEResourceExtractor(
                        exeFile = exeFile,
                        fileLength = fileLength,
                        rsrcRootFileOffset = rsrcRootFileOffset,
                        rsrcMaxLimitOffset = rsrcMaxLimitOffset,
                        rvaDelta = rsrcSection.virtualAddress - rsrcSection.pointerToRawData,
                        sections = sections
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private val activeChannel = ThreadLocal<FileChannel?>()

    private fun <T> withChannel(block: (FileChannel) -> T?): T? {
        val existing = activeChannel.get()
        if (existing != null) {
            return block(existing)
        }
        return RandomAccessFile(exeFile, "r").use { raf ->
            val ch = raf.channel
            activeChannel.set(ch)
            try {
                block(ch)
            } finally {
                activeChannel.remove()
            }
        }
    }

    private fun rvaToFileOffset(rva: Long): Long {
        for (sec in sections) {
            if (rva >= sec.virtualAddress && rva < sec.virtualAddress + sec.sizeOfRawData) {
                return sec.pointerToRawData + (rva - sec.virtualAddress)
            }
        }
        return rva - rvaDelta
    }

    data class ResourceDirectoryEntry(val nameOrId: Int, val isDirectory: Boolean, val offset: Int)

    inner class ResourceDirectory internal constructor(
        offsetFromSectionTop: Int,
        private val depth: Int = 0
    ) {
        private val entries = arrayListOf<ResourceDirectoryEntry>()

        init {
            if (depth <= MAX_RESOURCE_DEPTH && offsetFromSectionTop >= 0) {
                val dirOffset = rsrcRootFileOffset + offsetFromSectionTop.toLong()
                if (dirOffset >= rsrcRootFileOffset && dirOffset + 16L <= rsrcMaxLimitOffset) {
                    withChannel { ch ->
                        val hdr = readExactBuffer(ch, fileLength, dirOffset, 16) ?: return@withChannel null
                        val numNamed = hdr.getShort(12).toInt() and 0xFFFF
                        val numId = hdr.getShort(14).toInt() and 0xFFFF
                        val totalEntries = numNamed + numId
                        if (totalEntries in 1..MAX_DIR_ENTRIES) {
                            val entriesBytesLong = totalEntries.toLong() * 8L
                            if (dirOffset + 16L + entriesBytesLong <= rsrcMaxLimitOffset) {
                                val entBuf = readExactBuffer(ch, fileLength, dirOffset + 16L, entriesBytesLong.toInt())
                                    ?: return@withChannel null
                                repeat(totalEntries) {
                                    val nameOrId = entBuf.int
                                    val ofs = entBuf.int
                                    if (ofs < 0) {
                                        entries.add(ResourceDirectoryEntry(nameOrId, true, ofs and 0x7fffffff))
                                    } else {
                                        entries.add(ResourceDirectoryEntry(nameOrId, false, ofs))
                                    }
                                }
                            }
                        }
                        Unit
                    }
                }
            }
        }

        fun getDirectory(nameOrId: Int): ResourceDirectory? {
            if (depth >= MAX_RESOURCE_DEPTH) return null
            return entries.find {
                it.nameOrId == nameOrId && it.isDirectory
            }?.let { ResourceDirectory(it.offset, depth + 1) }
        }

        fun getResource(nameOrId: Int): ByteArray? {
            return entries.find {
                it.nameOrId == nameOrId && !it.isDirectory
            }?.let { this@PEResourceExtractor.getResource(it.offset) }
        }

        fun getFirstResource(maxDepth: Int = MAX_RESOURCE_DEPTH - depth): ByteArray? {
            if (maxDepth <= 0 || entries.isEmpty()) {
                return null
            }
            val first = entries[0]
            return if (first.isDirectory) {
                if (depth >= MAX_RESOURCE_DEPTH) {
                    null
                } else {
                    ResourceDirectory(first.offset, depth + 1).getFirstResource(maxDepth - 1)
                }
            } else {
                this@PEResourceExtractor.getResource(first.offset)
            }
        }
    }

    private fun getRoot(): ResourceDirectory = ResourceDirectory(0, 0)

    fun getDirectory(type: Int): ResourceDirectory? {
        return try {
            withChannel {
                getRoot().getDirectory(type)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun getResource(offset: Int): ByteArray? {
        if (offset < 0) return null
        return try {
            val entryOffset = rsrcRootFileOffset + offset.toLong()
            if (entryOffset < rsrcRootFileOffset || entryOffset + 16L > rsrcMaxLimitOffset) {
                return null
            }
            withChannel { ch ->
                val dataEntry = readExactBuffer(ch, fileLength, entryOffset, 16) ?: return@withChannel null
                val dataRva = dataEntry.getInt(0).toLong() and 0xFFFFFFFFL
                val size = dataEntry.getInt(4)
                if (size !in 1..MAX_RESOURCE_SIZE) {
                    return@withChannel null
                }
                val offsetInFile = rvaToFileOffset(dataRva)
                if (offsetInFile < 0L || offsetInFile + size.toLong() > fileLength) {
                    return@withChannel null
                }
                val resBuf = readExactBuffer(ch, fileLength, offsetInFile, size) ?: return@withChannel null
                val out = ByteArray(size)
                resBuf.get(out)
                out
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    // Reconstruct an icon file from RT_GROUP_ICON / RT_ICON resources.
    // https://devblogs.microsoft.com/oldnewthing/20120720-00/?p=7083
    fun extractIcon(): ByteArray? {
        return try {
            withChannel {
                val root = getRoot()
                val iconResources = root.getDirectory(RT_ICON) ?: return@withChannel null
                val grpIconBytes = root.getDirectory(RT_GROUP_ICON)?.getFirstResource() ?: return@withChannel null
                if (grpIconBytes.size < 6) return@withChannel null

                val buf = ByteBuffer.wrap(grpIconBytes).order(ByteOrder.LITTLE_ENDIAN)
                val reserved = buf.getShort(0).toInt() and 0xFFFF
                val type = buf.getShort(2).toInt() and 0xFFFF
                val count = buf.getShort(4).toInt() and 0xFFFF
                if (reserved != 0 || (type != 1 && type != 2) || count !in 1..MAX_ICON_COUNT) {
                    return@withChannel null
                }
                if (grpIconBytes.size < 6 + count * 14) {
                    return@withChannel null
                }

                val out = ByteArrayOutputStream()
                // Write 6-byte ICONDIR header
                out.write(0)
                out.write(0)
                out.write(type and 0xff)
                out.write((type shr 8) and 0xff)
                out.write(count and 0xff)
                out.write((count shr 8) and 0xff)
                buf.position(6)

                var offsetInFileLong = 6L + count.toLong() * 16L
                val entryHeader = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                val icons = Array(count) {
                    val width = buf.get().toInt() and 0xFF
                    val height = buf.get().toInt() and 0xFF
                    val colorCount = buf.get().toInt() and 0xFF
                    val entryReserved = buf.get().toInt() and 0xFF
                    val planes = buf.short.toInt() and 0xFFFF
                    val bitCount = buf.short.toInt() and 0xFFFF
                    val declaredBytes = buf.int // dwBytesInRes (may be validated against actual size)
                    val iconId = buf.short.toInt() and 0xFFFF

                    val iconBytes = iconResources.getDirectory(iconId)?.getFirstResource()
                        ?: return@withChannel null
                    if (iconBytes.isEmpty() || iconBytes.size > MAX_RESOURCE_SIZE) {
                        return@withChannel null
                    }
                    if (offsetInFileLong + iconBytes.size.toLong() > MAX_TOTAL_ICO_SIZE.toLong()) {
                        return@withChannel null
                    }
                    val actualBytesInRes = if (declaredBytes > 0 && declaredBytes == iconBytes.size) {
                        declaredBytes
                    } else {
                        iconBytes.size
                    }

                    entryHeader.clear()
                    entryHeader.put(width.toByte())
                    entryHeader.put(height.toByte())
                    entryHeader.put(colorCount.toByte())
                    entryHeader.put(entryReserved.toByte())
                    entryHeader.putShort(planes.toShort())
                    entryHeader.putShort(bitCount.toShort())
                    entryHeader.putInt(actualBytesInRes)
                    out.write(entryHeader.array(), 0, 12)

                    val offsetInFile = offsetInFileLong.toInt()
                    out.write(offsetInFile and 0xff)
                    out.write((offsetInFile shr 8) and 0xff)
                    out.write((offsetInFile shr 16) and 0xff)
                    out.write((offsetInFile shr 24) and 0xff)

                    offsetInFileLong += iconBytes.size.toLong()
                    iconBytes
                }
                for (icon in icons) {
                    out.write(icon)
                }
                out.toByteArray()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
