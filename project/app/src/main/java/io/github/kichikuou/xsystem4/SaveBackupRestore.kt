package io.github.kichikuou.xsystem4

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipFile

internal object SaveBackupRestore {
    @JvmOverloads
    fun restore(
        backup: File,
        saveDir: File,
        beforeSwap: () -> Unit = {},
        rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
        createDirectory: (File) -> Boolean = { it.mkdir() }
    ): Int {
        if (!backup.isFile || backup.length() <= 0L) {
            throw IOException("Backup archive does not exist or is empty")
        }
        val parent = saveDir.absoluteFile.parentFile
            ?: throw IOException("Save directory has no parent")
        val staging = Files.createTempDirectory(parent.toPath(), ".restore_staging_").toFile()
        var old: File? = null
        try {
            val root = staging.canonicalFile
            var count = 0
            ZipFile(backup).use { zip ->
                val entries = zip.entries()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val rel = entry.name.replace('\\', '/').removePrefix("SaveData/").trimStart('/')
                    val dst = if (rel.isEmpty() || rel.startsWith(".")) null else File(root, rel).canonicalFile
                    if (dst != null && !dst.path.startsWith(root.path + File.separator)) {
                        throw IOException("Unsafe path in backup archive: ${entry.name}")
                    }
                    if (dst != null && !dst.parentFile.isDirectory && !dst.parentFile.mkdirs()) {
                        throw IOException("Cannot create staging directory")
                    }
                    val crc = CRC32()
                    var written = 0L
                    val output = dst?.outputStream()?.buffered()
                    try {
                        zip.getInputStream(entry).buffered().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                crc.update(buffer, 0, read)
                                written += read
                                output?.write(buffer, 0, read)
                            }
                        }
                    } finally {
                        output?.close()
                    }
                    if (written != entry.size || crc.value != entry.crc) {
                        throw IOException("Corrupt entry in backup archive: ${entry.name}")
                    }
                    if (dst != null) count++
                }
            }
            beforeSwap()
            if (saveDir.exists()) {
                val liveEntries = saveDir.listFiles()
                    ?: throw IOException("Cannot list original saves")
                fun copyStrictly(source: File, destination: File) {
                    if (source.isDirectory) {
                        if (!createDirectory(destination)) {
                            throw IOException("Cannot create hidden save directory: $destination")
                        }
                        val children = source.listFiles()
                            ?: throw IOException("Cannot list hidden save directory: $source")
                        for (child in children) {
                            copyStrictly(child, File(destination, child.name))
                        }
                    } else {
                        source.copyTo(destination)
                    }
                }
                for (entry in liveEntries.filter { it.name.startsWith(".") }) {
                    copyStrictly(entry, File(staging, entry.name))
                }
            }
            if (saveDir.exists()) {
                val oldDir = Files.createTempDirectory(parent.toPath(), ".restore_old_").toFile()
                if (!oldDir.delete() || !rename(saveDir, oldDir)) {
                    throw IOException("Cannot move original saves aside")
                }
                old = oldDir
            }
            try {
                if (!rename(staging, saveDir)) throw IOException("Cannot install restored saves")
            } catch (failure: Exception) {
                if (old != null) {
                    try {
                        if (!rename(old, saveDir)) {
                            throw IOException("Original saves retained at ${old.path}; rollback failed")
                        }
                    } catch (rollbackFailure: Exception) {
                        failure.addSuppressed(rollbackFailure)
                    }
                }
                throw failure
            }
            // The swap is committed. Cleanup must not turn success into a failed restore.
            try {
                old?.deleteRecursively()
            } catch (_: Exception) {
            }
            return count
        } finally {
            try {
                staging.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }
}
