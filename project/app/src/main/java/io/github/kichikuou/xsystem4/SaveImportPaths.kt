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
    private val saveFolder = listOf("System40.ini", "AliceStart.ini")
        .firstNotNullOfOrNull { ini -> gameFiles.firstOrNull { it.name.equals(ini, true) } }
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
        return (scenarios.filter { it.second }.ifEmpty { scenarios })
            .map { it.first }.distinct().singleOrNull()
    }

    fun nameForImport(raw: String, atRoot: Boolean): String? {
        val name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        if (!isSafeComponent(name)) return null
        return normalizedName(name, atRoot).takeIf(::isSafeComponent)
    }

    fun normalizedName(name: String, atRoot: Boolean): String {
        val prefix = system35Prefix
        if (atRoot && prefix != null) {
            val slot = slotFile.matchEntire(name)
            if (slot != null && equivalent(slot.groupValues[1], prefix)) {
                return prefix + slot.groupValues[2].lowercase(Locale.ROOT)
            }
            if (name.endsWith(".msgskip", true) && equivalent(name.dropLast(8), prefix)) {
                return "$prefix.msgskip"
            }
        }
        return ArchiveExtractor.repairSjisAsGbkMojibake(name)
    }

    fun migrateExistingSaves(root: File) {
        fun migrate(dir: File, atRoot: Boolean) {
            for (child in dir.listFiles().orEmpty()) {
                if (child.name.startsWith('.') || child.name.endsWith(conflictBackupSuffix)) continue
                if (child.isDirectory) migrate(child, atRoot = false)
                val repaired = normalizedName(child.name, atRoot && child.isFile)
                if (repaired == child.name || !isSafeComponent(repaired)) continue

                val target = File(dir, repaired)
                try {
                    Files.move(child.toPath(), target.toPath())
                } catch (_: FileAlreadyExistsException) {
                    if (child.isFile) moveToConflictBackup(child, target)
                } catch (_: IOException) {
                }
            }
        }
        migrate(root, atRoot = true)
    }

    private fun moveToConflictBackup(alias: File, target: File) {
        var index = 0
        while (true) {
            val suffix = if (index == 0) "" else ".$index"
            val backup = File(target.parentFile, target.name + suffix + conflictBackupSuffix)
            try {
                Files.move(alias.toPath(), backup.toPath())
                return
            } catch (_: FileAlreadyExistsException) {
                index++
            } catch (_: IOException) {
                return
            }
        }
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

        fun explicitSaveFolder(dirs: List<T>): T? {
            val configured = saveFolder?.let { expected ->
                dirs.firstOrNull { name(it) == expected }
                    ?: dirs.singleOrNull { equivalent(name(it), expected) }
            }
            val conventional = if (fromArchive) {
                dirs.firstOrNull { name(it).equals("SaveData", true) }
            } else {
                dirs.singleOrNull { name(it).equals("SaveData", true) }
            }
            return configured ?: conventional
        }

        if (fromArchive) {
            var level = listOf(root)
            repeat(3) {
                level = level.flatMap { visibleChildren(it).filter(isDirectory) }
                explicitSaveFolder(level)?.let { return it }
            }
        }

        fun find(node: T, depth: Int): T? {
            val visible = visibleChildren(node)
            if (visible.any { !isDirectory(it) && saveExtension.matches(name(it)) }) return node
            if (depth == 0) return null
            val dirs = visible.filter(isDirectory)
            return explicitSaveFolder(dirs) ?: dirs.singleOrNull()?.let { find(it, depth - 1) }
        }
        find(root, 3)?.let { return it }
        if (!fromArchive) return root

        var node = root
        while (true) {
            val child = visibleChildren(node).singleOrNull()?.takeIf(isDirectory) ?: return node
            node = child
        }
    }

    private companion object {
        const val conflictBackupSuffix = ".import-conflict.bak"
        val scenarioArchive = Regex("^(.*)S[A-Z](_chs)?\\.ald$", RegexOption.IGNORE_CASE)
        val slotFile = Regex("^(.*)(s[a-z]\\.asd)$", RegexOption.IGNORE_CASE)
        val saveExtension = Regex(".*\\.(asd|sav|rsm|msgskip)$", RegexOption.IGNORE_CASE)
        val cp932 = runCatching { Charset.forName("windows-31j") }.getOrElse { Charset.forName("Shift_JIS") }
        val gbk = Charset.forName("GBK")

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
