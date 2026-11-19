package io.github.kichikuou.xsystem4

import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveBackupRestoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun live(): File = temporary.newFolder("saves").apply {
        File(this, "original.asd").writeText("original")
        File(this, ".hidden").writeText("hidden")
    }

    private fun archive(vararg names: String): File = temporary.newFile("backup.zip").apply {
        ZipOutputStream(outputStream()).use { zip ->
            for (name in names) {
                val bytes = "replacement".toByteArray()
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun assertOriginal(live: File) {
        assertEquals("original", File(live, "original.asd").readText())
        assertEquals("hidden", File(live, ".hidden").readText())
        assertEquals(2, live.listFiles()!!.size)
        assertFalse(temporary.root.listFiles()!!.any { it.name.startsWith(".restore_") })
    }

    @Test fun corruptCrcDoesNotTouchLiveSaves() {
        val live = live()
        val zip = archive("SaveData/new.asd")
        val bytes = zip.readBytes()
        // Change stored payload without changing its size or either header's CRC.
        val offset = bytes.indices.first { i ->
            i + 11 <= bytes.size && String(bytes, i, 11, Charsets.UTF_8) == "replacement"
        }
        bytes[offset] = (bytes[offset].toInt() xor 1).toByte()
        zip.writeBytes(bytes)
        val error = assertThrows(IOException::class.java) { SaveBackupRestore.restore(zip, live) }
        assertTrue(error.message!!.contains("Corrupt entry"))
        assertOriginal(live)
    }

    @Test fun successfulRestoreReplacesWholeSaveSet() {
        val live = live()
        assertEquals(2, SaveBackupRestore.restore(archive("SaveData/new.asd", "SaveData/sub/slot.asd"), live))
        assertEquals("replacement", File(live, "new.asd").readText())
        assertEquals("replacement", File(live, "sub/slot.asd").readText())
        assertFalse(File(live, "original.asd").exists())
        assertFalse(temporary.root.listFiles()!!.any { it.name.startsWith(".restore_") })
    }

    @Test fun extractionFailureAfterFirstFilePreservesOriginal() {
        val live = live()
        val zip = archive("SaveData/new.asd", "SaveData/new.asd/child")
        assertThrows(IOException::class.java) { SaveBackupRestore.restore(zip, live) }
        assertOriginal(live)
    }

    @Test fun failedInstallRollsBackDirectorySwap() {
        val live = live()
        val zip = archive("SaveData/new.asd")
        var moves = 0
        assertThrows(IOException::class.java) {
            SaveBackupRestore.restore(zip, live, rename = { from, to ->
                moves++
                if (moves == 2) false else from.renameTo(to)
            })
        }
        assertEquals(3, moves)
        assertOriginal(live)
    }

    @Test fun emptyArchiveRestoresEmptySaveSet() {
        val live = live()
        assertEquals(0, SaveBackupRestore.restore(archive(), live))
        assertTrue(live.isDirectory)
        assertTrue(live.listFiles()!!.isEmpty())
    }

    @Test fun beforeSwapFailurePreservesOriginal() {
        val live = live()
        val zip = archive("SaveData/new.asd")
        assertThrows(IOException::class.java) {
            SaveBackupRestore.restore(zip, live, beforeSwap = { throw IOException("Backup failed") })
        }
        assertOriginal(live)
    }
}
