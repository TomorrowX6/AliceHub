package io.github.kichikuou.xsystem4

import java.io.File
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.Locale

class SaveImportPaths(gameDir: File?) {
    private val gameFiles = gameDir?.listFiles().orEmpty().filter { it.isFile }
    private val system35Prefix = findSystem35Prefix()
    private val saveFolder = gameDir?.let(::resolveLaunchIniFile)?.takeIf { it.isFile }
        ?.let { runCatching { System40Ini.parse(it).SaveFolder }.getOrNull() }
        ?.takeIf { isSafeComponent(it) }

    private fun findSystem35Prefix(): String? {
        val names = gameFiles.map { it.name }
        if (!GameCatalog.isSystem3xGameFiles(names) || GameCatalog.isLegacySystem3GameFiles(names) ||
            names.any { it == "xsystem35.gr" }
        ) return null
        val scenarios = names.filterNot { it.contains("uncensored", true) }
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
        name.takeIf { atRoot }?.let(::system35Name) ?: ArchiveExtractor.repairSjisAsGbkMojibake(name)

    fun migrateExistingSaves(root: File) {
        fun migrate(dir: File, atRoot: Boolean) {
            for (child in dir.listFiles().orEmpty()) {
                if (child.name.startsWith('.') || child.name.endsWith(conflictBackupSuffix)) continue
                if (child.isDirectory) migrate(child, atRoot = false)
                val slotName = if (atRoot && child.isFile) system35Name(child.name) else null
                val legacyName = ArchiveExtractor.repairSjisAsGbkMojibake(child.name)
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
    ): T {
        fun visibleChildren(node: T): List<T> = children(node).filterNot { name(it).startsWith('.') }
        fun isArchiveSave(name: String): Boolean =
            saveExtension.matches(name) ||
                name.endsWith(".vsf", true) || name.equals("keyfile.dat", true)
        fun hasLooseSave(nodes: List<T>, isSave: (String) -> Boolean): Boolean =
            nodes.any { !isDirectory(it) && isSave(name(it)) }

        fun explicitSaveFolder(dirs: List<T>, eligible: (T) -> Boolean = { true }): T? {
            val configured = saveFolder?.let { expected ->
                dirs.firstOrNull { name(it) == expected && eligible(it) }
                    ?: dirs.singleOrNull { equivalent(name(it), expected) && eligible(it) }
            }
            val conventional = if (fromArchive) {
                dirs.firstOrNull { name(it).equals("SaveData", true) && eligible(it) }
            } else {
                dirs.singleOrNull { name(it).equals("SaveData", true) }
            }
            return configured ?: conventional
        }

        if (fromArchive) {
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
            var level = listOf(root to false)
            repeat(3) {
                level = level.flatMap { (node, ancestorHasSave) ->
                    val visible = visibleChildren(node)
                    val competes = ancestorHasSave || hasLooseSave(visible, ::isArchiveSave)
                    visible.filter(isDirectory).map { it to competes }
                }
                val competingByDir = level.toMap()
                explicitSaveFolder(level.map { it.first }) { node ->
                    competingByDir[node] != true || containsSave(node)
                }?.let { return it }
            }
        }

        fun find(node: T, depth: Int, isSave: (String) -> Boolean): T? {
            val visible = visibleChildren(node)
            if (hasLooseSave(visible, isSave)) return node
            if (depth == 0) return null
            val dirs = visible.filter(isDirectory)
            if (!fromArchive) explicitSaveFolder(dirs)?.let { return it }
            return dirs.singleOrNull()?.let { find(it, depth - 1, isSave) }
        }
        find(root, 3, saveExtension::matches)?.let { return it }
        if (!fromArchive) return root
        find(root, 3, ::isArchiveSave)?.let { return it }

        var node = root
        while (true) {
            val child = visibleChildren(node).singleOrNull()?.takeIf(isDirectory) ?: return node
            node = child
        }
    }

    class ImportWrites {
        private val written = mutableSetOf<File>()

        fun write(staged: File, destination: File) {
            val target = destination.canonicalFile
            if (target in written) {
                written.add(moveToConflictBackup(staged, target))
            } else {
                if (!staged.renameTo(target)) staged.copyTo(target, overwrite = true)
                written.add(target)
            }
        }
    }

    private companion object {
        const val conflictBackupSuffix = ".import-conflict.bak"
        val scenarioArchive = Regex("^(.*)S[A-Z](_chs)?\\.ald$", RegexOption.IGNORE_CASE)
        val slotFile = Regex("^(.*)(s[a-z]\\.asd)$", RegexOption.IGNORE_CASE)
        val saveExtension = Regex(".*\\.(asd|sav|rsm|msgskip)$", RegexOption.IGNORE_CASE)
        val cp932 = runCatching { Charset.forName("windows-31j") }.getOrElse { Charset.forName("Shift_JIS") }
        val gbk = Charset.forName("GBK")

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
