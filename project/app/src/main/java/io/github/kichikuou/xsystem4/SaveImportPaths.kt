package io.github.kichikuou.xsystem4

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal const val SAVE_FOLDER_LAYOUT_MARKER = ".alicehub-save-folder-layout-v1"

class SaveImportPaths @JvmOverloads constructor(
    gameDir: File?,
    launchTarget: File? = null,
    saveFolderOverride: String? = null
) {
    private val gameFiles = gameDir?.listFiles().orEmpty().filter { it.isFile }
    private val isSystem35 = gameFiles.map { it.name }.let { names ->
        GameCatalog.isSystem3xGameFiles(names) && !GameCatalog.isLegacySystem3GameFiles(names) &&
            names.none { it == "xsystem35.gr" }
    }
    private val system35Prefix = findSystem35Prefix()
    private val saveFolder = (saveFolderOverride ?: gameDir?.let { dir ->
        if (launchTarget != null) {
            System4BootEntry.resolveIniForSaveFolder(dir, launchTarget)
        } else {
            resolveLaunchIniFile(dir).takeIf { it.isFile }
        }
    }?.let { runCatching { System40Ini.parse(it).SaveFolder }.getOrNull() })
        ?.takeIf { isSafeComponent(it) }

    private fun findSystem35Prefix(): String? {
        if (!isSystem35) return null
        val scenarios = gameFiles.map { it.name }.filterNot { it.contains("uncensored", true) }
            .mapNotNull(scenarioArchive::matchEntire)
            .map { it.groupValues[1] to it.groupValues[2].isNotEmpty() }
        return (scenarios.lastOrNull { it.second } ?: scenarios.firstOrNull())?.first
    }

    fun nameForImport(raw: String, atRoot: Boolean): String? {
        val name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        if (!isSafeComponent(name)) return null
        return normalizedName(name, atRoot).takeIf(::isSafeComponent)
    }

    private fun system35Name(name: String): String? {
        if (isSystem35 && name.equals("Volume.sav", ignoreCase = true)) return "Volume.sav"
        val prefix = system35Prefix ?: return null
        val slot = slotFile.matchEntire(name)
        if (slot != null && equivalent(slot.groupValues[1], prefix)) {
            return prefix + slot.groupValues[2].lowercase(Locale.ROOT)
        }
        if (name.endsWith(".msgskip", true) && equivalent(name.dropLast(8), prefix)) {
            return "$prefix.msgskip"
        }
        return null
    }

    fun normalizedName(name: String, atRoot: Boolean): String =
        name.takeIf { atRoot }?.let(::system35Name) ?: normalizeScriptName(name)

    private fun normalizeScriptName(name: String): String {
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(name)
        return if (!isSystem35 || system35MapFile.matches(repaired)) repaired else name
    }

    fun decodeArchivePath(rawPath: ByteArray): String =
        if (isSystem35) ArchiveExtractor.decodeBestCjkText(rawPath) else ArchiveExtractor.decodeEntryPath(rawPath)

    fun migrateExistingSaves(root: File) {
        fun migrate(dir: File, atRoot: Boolean) {
            for (child in dir.listFiles().orEmpty()) {
                if (child.name.startsWith('.') || child.name.endsWith(conflictBackupSuffix)) continue
                if (child.isDirectory) migrate(child, atRoot = false)
                val slotName = if (atRoot && child.isFile) system35Name(child.name) else null
                val legacyName = when {
                    slotName == "Volume.sav" -> slotName
                    slotName != null -> ArchiveExtractor.repairSjisAsGbkMojibake(child.name)
                    else -> normalizeScriptName(child.name)
                }
                val repaired = slotName ?: legacyName
                if (repaired == child.name || !isSafeComponent(repaired)) continue

                val target = File(dir, repaired)
                try {
                    if (target.exists()) {
                        if (child.isFile) moveToConflictBackup(child, target)
                    } else if (repaired == legacyName) {
                        Files.move(child.toPath(), target.toPath())
                    }
                } catch (_: IOException) {
                }
            }
        }
        migrate(root, atRoot = true)
    }

    @JvmOverloads
    fun <T> sourceRoot(
        root: T,
        children: (T) -> List<T>,
        name: (T) -> String,
        isDirectory: (T) -> Boolean,
        fromArchive: Boolean = false
    ): T = selectSource(root, children, name, isDirectory, fromArchive).root

    internal data class Source<T>(val root: T, val outerState: List<T> = emptyList())

    internal fun <T> selectSource(
        root: T,
        children: (T) -> List<T>,
        name: (T) -> String,
        isDirectory: (T) -> Boolean,
        fromArchive: Boolean
    ): Source<T> {
        if (!fromArchive) {
            val rootChildren = children(root)
            if (rootChildren.any { !isDirectory(it) && name(it) == SAVE_FOLDER_LAYOUT_MARKER }) {
                return Source(root)
            }
            SaveZipLayout.exportedRoot(rootChildren, name, isDirectory)?.let { return Source(it) }
        }
        fun visibleChildren(node: T): List<T> = children(node).filterNot { name(it).startsWith('.') }
        fun hasLooseSave(nodes: List<T>, isSave: (String) -> Boolean): Boolean =
            nodes.any { !isDirectory(it) && isSave(name(it)) }

        fun sourceWithState(node: T, ancestors: List<T>): Source<T> = Source(
            node, ancestors.asReversed().flatMap { parent ->
                visibleChildren(parent).filter { !isDirectory(it) && isFallbackState(name(it)) }
            }
        )

        fun explicitSaveFolder(dirs: List<T>, eligible: (T) -> Boolean = { true }): T? {
            val configured = saveFolder?.let { expected ->
                dirs.firstOrNull { name(it) == expected && eligible(it) }
                    ?: dirs.singleOrNull { equivalent(name(it), expected) && eligible(it) }
            }
            val conventional = if (fromArchive) {
                dirs.firstOrNull { name(it).equals("SaveData", true) && eligible(it) }
            } else {
                dirs.singleOrNull { name(it).equals("SaveData", true) }?.takeIf(eligible)
            }
            return configured ?: conventional
        }

        fun containsSave(node: T): Boolean {
            val pending = ArrayDeque<T>()
            pending.add(node)
            while (pending.isNotEmpty()) {
                val visible = visibleChildren(pending.removeLast())
                if (hasLooseSave(visible, ::isArchiveSave)) return true
                pending.addAll(visible.filter(isDirectory))
            }
            return false
        }

        fun explicitSource(node: T, ancestors: List<T>): Source<T> {
            val visible = visibleChildren(node)
            val competes = if (fromArchive) hasLooseSave(visible, ::isPotentialSlot)
                else hasLooseSave(visible, saveExtension::matches)
            if (ancestors.size < 3 && !competes) {
                val child = visible.filter(isDirectory).singleOrNull()
                if (child != null && explicitSaveFolder(listOf(child)) != null && containsSave(child)) {
                    return explicitSource(child, ancestors + node)
                }
            }
            return sourceWithState(node, ancestors)
        }

        if (fromArchive) {
            var level = listOf(Triple(root, false, emptyList<T>()))
            repeat(3) {
                level = level.flatMap { (node, ancestorHasSave, ancestors) ->
                    val visible = visibleChildren(node)
                    val competes = ancestorHasSave || hasLooseSave(visible, ::isArchiveSave)
                    visible.filter(isDirectory).map { Triple(it, competes, ancestors + node) }
                }
                val byDir = level.associateBy { it.first }
                explicitSaveFolder(level.map { it.first }) { node ->
                    !byDir.getValue(node).second || containsSave(node)
                }?.let { return explicitSource(it, byDir.getValue(it).third) }
            }
        }

        fun find(node: T, depth: Int, ancestors: List<T>, isSave: (String) -> Boolean): Source<T>? {
            val visible = visibleChildren(node)
            if (hasLooseSave(visible, isSave)) return sourceWithState(node, ancestors)
            if (depth == 0) return null
            val dirs = visible.filter(isDirectory)
            val parents = ancestors + node
            if (!fromArchive) explicitSaveFolder(dirs)?.let { return explicitSource(it, parents) }
            return dirs.singleOrNull()?.let { find(it, depth - 1, parents, isSave) }
        }
        find(root, 3, emptyList(), saveExtension::matches)?.let { return it }
        if (!fromArchive) return Source(root)
        find(root, 3, emptyList(), ::isArchiveSave)?.let { return it }

        var node = root
        while (true) {
            val child = visibleChildren(node).singleOrNull()?.takeIf(isDirectory) ?: return Source(node)
            node = child
        }
    }

    fun importExtractedArchive(root: File, targetDir: File, writes: ImportWrites): Int {
        val source = SaveZipLayout.exportedRoot(root)?.let { Source(it) } ?: selectSource(
            root, { it.listFiles()?.toList().orEmpty() }, File::getName, File::isDirectory, fromArchive = true
        )
        val rootCanonical = targetDir.canonicalFile
        var copied = 0
        fun copy(src: File, parts: List<String>, fallback: Boolean = false) {
            val relPath = parts.map { normalizedName(it, atRoot = parts.size == 1) }.joinToString("/")
            val dst = File(rootCanonical, relPath).canonicalFile
            if (!dst.path.startsWith(rootCanonical.path + File.separator)) return
            dst.parentFile?.mkdirs()
            if (fallback) {
                writes.stageFallback(src, dst)
                return
            }
            val tmpDst = File(dst.parentFile, ".imp_${System.nanoTime()}.tmp")
            try {
                src.copyTo(tmpDst, overwrite = true)
                writes.write(tmpDst, dst)
                copied++
            } finally {
                tmpDst.delete()
            }
        }
        source.root.walkTopDown().forEach { src ->
            if (src.isFile && !src.name.startsWith('.')) {
                copy(src, src.relativeTo(source.root).invariantSeparatorsPath.split('/'))
            }
        }
        source.outerState.filter(File::isFile).forEach { copy(it, listOf(it.name), fallback = true) }
        return copied
    }

    class ImportWrites : Closeable {
        private val written = mutableSetOf<File>()
        private val markers = mutableMapOf<Pair<File, String>, File>()
        private val existingMarkers = mutableMapOf<File, Map<String, List<File>>>()
        private val fallbacks = mutableListOf<Pair<File, File>>()

        private fun markerName(file: File): String = buildString(file.name.length) {
            for (char in file.name) append(if (char in 'A'..'Z') char.lowercaseChar() else char)
        }
        private fun markerKey(file: File) = file.parentFile!! to markerName(file)

        fun write(staged: File, destination: File) {
            val target = destination.canonicalFile
            if (target in written) {
                written.add(moveToConflictBackup(staged, target))
            } else {
                if (!staged.renameTo(target)) staged.copyTo(target, overwrite = true)
                written.add(target)
                if (isFallbackState(target.name)) {
                    val key = markerKey(target)
                    if (markers.putIfAbsent(key, target) == null) {
                        val existing = existingMarkers.getOrPut(key.first) {
                            key.first.listFiles().orEmpty().filter {
                                isFallbackState(it.name) && Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS)
                            }.sortedBy { it.name }.groupBy(::markerName)
                        }[key.second].orEmpty()
                        for (alias in existing) {
                            if (alias != target) written.add(moveToConflictBackup(alias, target))
                        }
                    }
                }
            }
        }

        fun stageFallback(source: File, destination: File) {
            val staged = Files.createTempFile(destination.parentFile!!.toPath(), ".imp_", ".tmp").toFile()
            try {
                source.copyTo(staged, overwrite = true)
                fallbacks.add(staged to destination)
            } catch (e: Exception) {
                staged.delete()
                throw e
            }
        }

        fun finishFallbacks(): Int {
            val count = fallbacks.size
            try {
                for ((staged, destination) in fallbacks) {
                    val target = destination.canonicalFile
                    write(staged, markers[markerKey(target)] ?: target)
                }
            } finally {
                close()
            }
            return count
        }

        override fun close() {
            fallbacks.forEach { (staged, _) -> staged.delete() }
            fallbacks.clear()
        }
    }

    private companion object {
        const val conflictBackupSuffix = ".import-conflict.bak"
        val scenarioArchive = Regex("^(.*)S[A-Z](_chs)?\\.ald$", RegexOption.IGNORE_CASE)
        val slotFile = Regex("^(.*)(s[a-z]\\.asd)$", RegexOption.IGNORE_CASE)
        val system35MapFile = Regex("王子_M-?\\d+\\.asd", RegexOption.IGNORE_CASE)
        val saveExtension = Regex(".*\\.(asd|sav|rsm|msgskip)$", RegexOption.IGNORE_CASE)
        val numberedSlot = Regex("(?:.*[ _.-])?(?:(?:save(?:data)?|slot)[ _.-]*)?\\d+", RegexOption.IGNORE_CASE)
        val namedState = Regex(
            "(?:.*[_-])?(?:af[a-z]+|config|settings?|system(?:data)?|sys|common(?:system)?(?:data)?|" +
                "global(?:save)?(?:data)?|collection|extra|progress|volume|msgskip(?:flag)?)",
            RegexOption.IGNORE_CASE
        )
        val cp932 = runCatching { Charset.forName("windows-31j") }.getOrElse { Charset.forName("Shift_JIS") }
        val gbk = Charset.forName("GBK")

        fun isArchiveMarker(name: String): Boolean =
            name.endsWith(".vsf", true) || name.equals("keyfile.dat", true)

        fun isArchiveSave(name: String): Boolean = saveExtension.matches(name) || isArchiveMarker(name)

        fun isPotentialSlot(name: String): Boolean = saveExtension.matches(name) &&
            !name.endsWith(".msgskip", true) && !namedState.matches(name.substringBeforeLast('.'))

        fun isFallbackState(name: String): Boolean = isArchiveMarker(name) ||
            name.endsWith(".msgskip", true) ||
            name.endsWith(".sav", true) && !numberedSlot.matches(name.substringBeforeLast('.')) ||
            saveExtension.matches(name) && namedState.matches(name.substringBeforeLast('.'))

        fun moveToConflictBackup(alias: File, target: File): File {
            var index = 0
            while (true) {
                val suffix = if (index == 0) "" else ".$index"
                val backup = File(target.parentFile, target.name + suffix + conflictBackupSuffix)
                try {
                    Files.move(alias.toPath(), backup.toPath())
                    return backup
                } catch (_: FileAlreadyExistsException) {
                    index++
                }
            }
        }

        fun isSafeComponent(name: String): Boolean =
            name.isNotEmpty() && !name.startsWith('.') && name.none { it == '/' || it == '\\' || it == '\u0000' }

        fun equivalent(a: String, b: String): Boolean =
            a.equals(b, true) || sameBytes(a, cp932, b, gbk) || sameBytes(a, gbk, b, cp932)

        fun sameBytes(a: String, aCharset: Charset, b: String, bCharset: Charset): Boolean =
            a.any { it.code >= 0x80 } && b.any { it.code >= 0x80 } &&
                runCatching {
                    aCharset.newEncoder().encode(CharBuffer.wrap(a)) ==
                        bCharset.newEncoder().encode(CharBuffer.wrap(b))
                }.getOrDefault(false)
    }
}

internal object SaveZipLayout {
    private const val wrapper = "SaveData"
    private const val marker = ".alicehub-save-layout-v1"

    fun write(
        zip: ZipOutputStream,
        entries: List<SaveFileEntry>,
        copy: (File, ZipOutputStream) -> Boolean
    ): Pair<Int, Int> {
        zip.putNextEntry(ZipEntry(marker))
        zip.closeEntry()
        var exported = 0
        var converted = 0
        for (entry in entries) {
            if (!entry.file.exists()) continue
            val zipEntry = ZipEntry("$wrapper/${entry.relativePath}")
            zipEntry.time = entry.lastModified
            zip.putNextEntry(zipEntry)
            if (copy(entry.file, zip)) converted++
            zip.closeEntry()
            exported++
        }
        return exported to converted
    }

    fun exportedRoot(root: File): File? =
        exportedRoot(root.listFiles()?.toList().orEmpty(), File::getName, File::isDirectory) {
            it.isFile && it.length() == 0L
        }

    fun <T> exportedRoot(
        children: List<T>,
        name: (T) -> String,
        isDirectory: (T) -> Boolean,
        isMarkerFile: (T) -> Boolean = { !isDirectory(it) }
    ): T? {
        if (children.none { name(it) == marker && isMarkerFile(it) }) return null
        return children.singleOrNull { !name(it).startsWith('.') }
            ?.takeIf { isDirectory(it) && name(it) == wrapper }
    }
}
