package io.github.kichikuou.xsystem4

import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure, atomic directory cloning helpers for creating additional Rance 10 instances
 * so players can keep vanilla and multiple MOD versions side by side with isolated saves.
 */
object Rance10InstanceManager {
    const val CLONE_STAGING_PREFIX = ".clone_rance10_"
    private const val FREE_SPACE_HEADROOM_BYTES = 64L * 1024L * 1024L
    private const val COPY_BUFFER_BYTES = 256 * 1024
    private const val MAX_DIR_NAME_LENGTH = 80
    private val activeStagingPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun canonicalKey(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    enum class NameValidationError {
        EMPTY,
        INVALID_CHARS,
        TOO_LONG,
        ALREADY_EXISTS
    }

    data class NameValidationResult(
        val sanitizedName: String,
        val error: NameValidationError?
    ) {
        val isValid: Boolean get() = error == null
    }

    data class CopyEntry(
        val sourceFile: File,
        val targetRelPath: String,
        val sizeBytes: Long
    )

    data class CopyPlan(
        val entries: List<CopyEntry>,
        val totalBytes: Long,
        val cleanVanilla: Boolean
    ) {
        val fileCount: Int get() = entries.size
    }

    @JvmOverloads
    fun suggestNextDirName(
        parentDir: File,
        basePrefix: String = "Rance10",
        extraUsedNames: Collection<String> = emptyList()
    ): String {
        val existingLower = existingEntryNamesLower(parentDir, extraUsedNames)
        var suffix = 1
        while (suffix < 10_000) {
            val candidate = "$basePrefix-$suffix"
            if (!existingLower.contains(candidate.lowercase(Locale.ROOT))) {
                return candidate
            }
            suffix++
        }
        return "$basePrefix-${System.currentTimeMillis()}"
    }

    @JvmOverloads
    fun validateDirName(
        parentDir: File,
        rawName: String,
        extraUsedNames: Collection<String> = emptyList()
    ): NameValidationResult {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            return NameValidationResult(trimmed, NameValidationError.EMPTY)
        }
        if (trimmed.length > MAX_DIR_NAME_LENGTH) {
            return NameValidationResult(trimmed, NameValidationError.TOO_LONG)
        }
        if (trimmed == "." || trimmed == ".." || trimmed.startsWith(".")) {
            return NameValidationResult(trimmed, NameValidationError.INVALID_CHARS)
        }
        val forbidden = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (trimmed.any { it.code < 0x20 || it in forbidden }) {
            return NameValidationResult(trimmed, NameValidationError.INVALID_CHARS)
        }
        if (existingEntryNamesLower(parentDir, extraUsedNames).contains(trimmed.lowercase(Locale.ROOT))) {
            return NameValidationResult(trimmed, NameValidationError.ALREADY_EXISTS)
        }
        return NameValidationResult(trimmed, null)
    }

    private fun existingEntryNamesLower(
        parentDir: File,
        extraUsedNames: Collection<String> = emptyList()
    ): Set<String> {
        val result = HashSet<String>()
        parentDir.listFiles()?.forEach { result.add(it.name.lowercase(Locale.ROOT)) }
        for (name in extraUsedNames) {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                result.add(trimmed.lowercase(Locale.ROOT))
            }
        }
        return result
    }

    private fun isTransientFile(name: String): Boolean {
        return name.endsWith(".vanilla.bak.tmp") ||
            (name.startsWith(".mod_apply_") && name.endsWith(".tmp")) ||
            (name.startsWith(".restore_") && name.endsWith(".tmp")) ||
            name == "${Item.NON_VANILLA_BASELINE_MARKER}.tmp"
    }

    /**
     * Builds the exact file copy list for cloning [sourceDir].
     *
     * When [cleanVanilla] is true and [sourceDir] has restorable vanilla backups, MOD-only files
     * listed in `.mod_installed_files.txt` (without `.vanilla.bak`) are skipped completely and
     * `<file>.vanilla.bak` entries are mapped directly to `<file>` in the destination — avoiding
     * wasted disk I/O and temporary space for large MOD archives.
     */
    fun planCopy(sourceDir: File, cleanVanilla: Boolean): CopyPlan {
        if (!sourceDir.isDirectory) {
            return CopyPlan(emptyList(), 0L, cleanVanilla = false)
        }
        val effectiveClean = cleanVanilla && Item.canRestoreVanillaDir(sourceDir)
        val modAddedPathsLower = HashSet<String>()
        val bakTargetPathsLower = HashSet<String>()

        if (effectiveClean) {
            val manifestFile = File(sourceDir, ".mod_installed_files.txt")
            if (manifestFile.isFile) {
                runCatching {
                    for (line in manifestFile.readLines()) {
                        val rel = line.trim().replace('\\', '/')
                        if (rel.isNotEmpty() && !rel.startsWith("/") && !rel.contains("..")) {
                            modAddedPathsLower.add(rel.lowercase(Locale.ROOT))
                        }
                    }
                }
            }
            for (f in sourceDir.walkTopDown()) {
                if (f.isFile && f.name.endsWith(".vanilla.bak")) {
                    val relBak = f.relativeTo(sourceDir).path.replace('\\', '/')
                    val origRel = relBak.removeSuffix(".vanilla.bak")
                    bakTargetPathsLower.add(origRel.lowercase(Locale.ROOT))
                }
            }
        }

        val entries = ArrayList<CopyEntry>()
        var totalBytes = 0L
        for (f in sourceDir.walkTopDown()) {
            if (!f.isFile) continue
            if (isTransientFile(f.name)) continue
            val rel = f.relativeTo(sourceDir).path.replace('\\', '/')
            val relLower = rel.lowercase(Locale.ROOT)

            if (effectiveClean) {
                if (relLower == InstalledModInfo.META_FILE_NAME.lowercase(Locale.ROOT) ||
                    relLower == ".mod_installed_files.txt" ||
                    relLower == Item.NON_VANILLA_BASELINE_MARKER.lowercase(Locale.ROOT)
                ) {
                    continue
                }
                if (f.name.endsWith(".vanilla.bak")) {
                    val targetRel = rel.removeSuffix(".vanilla.bak")
                    val sz = f.length().coerceAtLeast(0L)
                    entries.add(CopyEntry(f, targetRel, sz))
                    totalBytes += sz
                    continue
                }
                if (bakTargetPathsLower.contains(relLower)) {
                    // Replaced by MOD; its sibling .vanilla.bak will be copied into this path instead.
                    continue
                }
                if (modAddedPathsLower.contains(relLower)) {
                    // Added purely by MOD with no vanilla counterpart.
                    continue
                }
                if (relLower == "asratest.ini") {
                    // Mirrors restoreVanillaFromModDir: drop MOD-created AsraTest.ini unless backed up.
                    continue
                }
            }

            val sz = f.length().coerceAtLeast(0L)
            entries.add(CopyEntry(f, rel, sz))
            totalBytes += sz
        }
        entries.sortBy { it.targetRelPath }
        return CopyPlan(entries, totalBytes, effectiveClean)
    }

    fun cleanupStaleCloneStaging(parentDir: File) {
        val children = parentDir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory && child.name.startsWith(CLONE_STAGING_PREFIX)) {
                if (canonicalKey(child) in activeStagingPaths) {
                    continue
                }
                runCatching { child.deleteRecursively() }
            }
        }
    }

    @JvmOverloads
    fun executeCopy(
        plan: CopyPlan,
        parentDir: File,
        targetDirName: String,
        availableBytesOverride: Long? = null,
        onStagingDirCreated: ((File) -> Unit)? = null,
        checkCancelled: () -> Unit = {},
        onProgress: (doneBytes: Long, totalBytes: Long, currentFile: String) -> Unit = { _, _, _ -> },
        extraUsedNames: Collection<String> = emptyList(),
        onFinalDirCommitted: ((File) -> Unit)? = null
    ): File {
        val validation = validateDirName(parentDir, targetDirName, extraUsedNames)
        if (!validation.isValid) {
            throw IOException("Invalid target directory name: $targetDirName (${validation.error})")
        }
        if (plan.entries.isEmpty()) {
            throw IOException("Nothing to copy")
        }
        parentDir.mkdirs()
        val availBytes = availableBytesOverride ?: runCatching {
            StatFs(parentDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)
        if (availBytes < plan.totalBytes + FREE_SPACE_HEADROOM_BYTES) {
            throw GameList.InstallFailureException(R.string.hub_create_instance_no_space)
        }

        val finalDir = File(parentDir, validation.sanitizedName)
        val stagingDir = File(parentDir, "$CLONE_STAGING_PREFIX${UUID.randomUUID().toString().take(8)}")
        val stagingKey = canonicalKey(stagingDir)
        activeStagingPaths.add(stagingKey)
        var committed = false
        try {
            if (!stagingDir.mkdirs()) {
                throw IOException("Failed to create staging directory: $stagingDir")
            }
            // Re-register with canonical path after directory creation (resolves any symlinks).
            val liveStagingKey = canonicalKey(stagingDir)
            if (liveStagingKey != stagingKey) {
                activeStagingPaths.add(liveStagingKey)
            }
            onStagingDirCreated?.invoke(stagingDir)

            var doneBytes = 0L
            val buf = ByteArray(COPY_BUFFER_BYTES)
            val total = plan.totalBytes.coerceAtLeast(1L)
            for (entry in plan.entries) {
                checkCancelled()
                val dst = File(stagingDir, entry.targetRelPath)
                dst.parentFile?.mkdirs()
                var written = 0L
                FileInputStream(entry.sourceFile).use { input ->
                    FileOutputStream(dst, false).use { output ->
                        while (true) {
                            checkCancelled()
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n > 0) {
                                output.write(buf, 0, n)
                                written += n
                                doneBytes += n
                                onProgress(doneBytes, total, entry.targetRelPath)
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (written != entry.sizeBytes) {
                    throw IOException("Short copy for ${entry.targetRelPath}: $written != ${entry.sizeBytes}")
                }
            }
            checkCancelled()
            if (existingEntryNamesLower(parentDir, extraUsedNames).contains(validation.sanitizedName.lowercase(Locale.ROOT))) {
                throw IOException("Target directory already exists: ${validation.sanitizedName}")
            }
            if (!stagingDir.renameTo(finalDir) || !finalDir.isDirectory) {
                throw IOException("Failed to finalize cloned directory: ${finalDir.name}")
            }
            committed = true
            onFinalDirCommitted?.invoke(finalDir)
            checkCancelled()
            return finalDir
        } catch (t: Throwable) {
            runCatching { stagingDir.deleteRecursively() }
            if (committed) {
                runCatching { finalDir.deleteRecursively() }
            }
            throw t
        } finally {
            activeStagingPaths.remove(stagingKey)
            activeStagingPaths.remove(canonicalKey(stagingDir))
        }
    }
}
