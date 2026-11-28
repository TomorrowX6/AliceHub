package io.github.kichikuou.xsystem4

import android.content.Context
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Locale

object System4BootEntry {
    private const val PREFS_NAME = "alicehub_settings"
    private const val KEY_BOOT_ENTRY_PREFIX = "hub_system4_boot_entry_"
    private const val MAX_INI_BYTES = 256 * 1024

    private val STANDARD_BOOT_INI_NAMES = setOf(
        "alicestart.chs",
        "alicestart.ini",
        "alicestart2.ini",
        "system40.ini"
    )

    private val CODE_NAME_REGEX = Regex("""(?im)^[\t ]*CodeName[\t ]*=[\t ]*(?:"([^"\r\n]+)"|([^\s;#\r\n]+))""")

    data class BootConfiguration(val ini: File, val script: File)

    @JvmStatic
    fun isStandardBootIni(name: String): Boolean =
        name.lowercase(Locale.ROOT) in STANDARD_BOOT_INI_NAMES

    @JvmStatic
    fun isCandidateIniFile(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return !lower.startsWith(".") &&
            lower != "system39.ini" &&
            lower != "system3.ini" &&
            (lower.endsWith(".ini") || lower == "alicestart.chs")
    }

    private fun isRegularNonSymlink(file: File): Boolean {
        return try {
            !Files.isSymbolicLink(file.toPath()) && file.isFile
        } catch (_: Throwable) {
            file.isFile
        }
    }

    private fun isDirectoryNonSymlink(file: File): Boolean {
        return try {
            !Files.isSymbolicLink(file.toPath()) && file.isDirectory
        } catch (_: Throwable) {
            file.isDirectory
        }
    }

    private fun canonicalOrAbsolute(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Throwable) {
            file.absolutePath
        }
    }

    private fun regularTopLevelFiles(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { !it.name.startsWith(".") && isRegularNonSymlink(it) }
            .orEmpty()
    }

    private fun system4ScriptFiles(regularFiles: List<File>): List<File> {
        val hasChsTable = regularFiles.any { it.name.equals("chs.tbl", ignoreCase = true) }
        return regularFiles.filter { file ->
            val lower = file.name.lowercase(Locale.ROOT)
            (lower.endsWith(".ain") && lower != "system39.ain") ||
                (hasChsTable && lower.endsWith(".chs") && lower != "alicestart.chs")
        }.sortedBy { it.name }
    }

    private fun readBoundedFile(file: File): ByteArray? {
        if (!isRegularNonSymlink(file)) return null
        return try {
            file.inputStream().use { input ->
                val buf = ByteArray(MAX_INI_BYTES + 1)
                var total = 0
                while (total < buf.size) {
                    val read = input.read(buf, total, buf.size - total)
                    if (read <= 0) break
                    total += read
                }
                if (total > MAX_INI_BYTES) null else buf.copyOf(total)
            }
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun extractCodeNames(raw: ByteArray): List<String> {
        if (raw.size > MAX_INI_BYTES) return emptyList()
        val result = LinkedHashSet<String>()
        runCatching { System40Ini.parse(raw).codeName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { result.add(it) }

        val candidates = listOfNotNull(
            runCatching { String(raw, Charsets.UTF_8) }.getOrNull(),
            runCatching { ArchiveExtractor.decodeBestCjkText(raw, preferGbkOnTie = true) }.getOrNull(),
            runCatching { String(raw, Charset.forName("Shift_JIS")) }.getOrNull(),
            runCatching { String(raw, Charset.forName("GBK")) }.getOrNull()
        )
        for (decoded in candidates) {
            val text = decoded.removePrefix("\uFEFF")
            val match = CODE_NAME_REGEX.find(text) ?: continue
            val code = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (code.isNotBlank()) {
                result.add(code)
            }
        }
        return result.toList()
    }

    @JvmStatic
    fun resolveConfiguredScript(codeName: String, root: File): File? {
        val path = codeName.replace('\\', '/')
        if (path.startsWith("/") || path.contains('\u0000')) return null
        val components = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (components.isEmpty()) return null
        if (components.first().contains(':')) return null
        if (components.any { it == ".." }) return null

        val leafLower = components.last().lowercase(Locale.ROOT)
        val validLeaf = (leafLower.endsWith(".ain") && leafLower != "system39.ain") ||
            (leafLower.endsWith(".chs") && leafLower != "alicestart.chs")
        if (!validLeaf) return null

        var directory = root
        for (i in components.indices) {
            val leaf = components[i]
            val children = directory.listFiles() ?: return null
            val match = children.firstOrNull { it.name == leaf }
                ?: children.firstOrNull { it.name.equals(leaf, ignoreCase = true) }
                ?: children.firstOrNull {
                    ArchiveExtractor.repairSjisAsGbkMojibake(it.name).equals(leaf, ignoreCase = true)
                }
                ?: return null
            if (i == components.lastIndex) {
                return if (isRegularNonSymlink(match)) match else null
            }
            if (!isDirectoryNonSymlink(match)) return null
            directory = match
        }
        return null
    }

    @JvmStatic
    fun bootConfigurations(dir: File): List<BootConfiguration> =
        bootConfigurations(regularTopLevelFiles(dir), dir)

    private fun bootConfigurations(regularFiles: List<File>, root: File): List<BootConfiguration> {
        return regularFiles
            .filter { isCandidateIniFile(it.name) }
            .sortedBy { it.name }
            .mapNotNull { file ->
                val raw = readBoundedFile(file) ?: return@mapNotNull null
                for (code in extractCodeNames(raw)) {
                    val script = resolveConfiguredScript(code, root)
                    if (script != null) {
                        return@mapNotNull BootConfiguration(file, script)
                    }
                }
                null
            }
    }

    @JvmStatic
    fun iniUsesDaiSengokuSaveFolder(iniFile: File): Boolean? {
        val raw = readBoundedFile(iniFile) ?: return null
        val text = (runCatching { String(raw, Charsets.UTF_8) }.getOrNull()
            ?: String(raw, Charsets.ISO_8859_1)).removePrefix("\uFEFF")
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#") || line.startsWith("//")) {
                continue
            }
            val eq = line.indexOf('=')
            if (eq < 0) continue
            val key = line.substring(0, eq).trim()
            if (!key.equals("SaveFolder", ignoreCase = true)) continue
            var value = line.substring(eq + 1).trim()
            if (value.startsWith("\"")) {
                val rest = value.substring(1)
                val close = rest.indexOf('"')
                value = if (close >= 0) rest.substring(0, close) else rest
            } else {
                for (marker in listOf(";", "#", "//")) {
                    val idx = value.indexOf(marker)
                    if (idx >= 0) {
                        value = value.substring(0, idx)
                    }
                }
                value = value.trim()
            }
            return value.equals("SaveDataDSR", ignoreCase = true)
        }
        return false
    }

    @JvmStatic
    fun usesDaiSengokuSaveFolder(gameDir: File): Boolean {
        val regularFiles = regularTopLevelFiles(gameDir)
        for (fileName in listOf("System40.ini", "AliceStart.ini")) {
            val iniFile = regularFiles.firstOrNull { it.name == fileName }
                ?: regularFiles.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
                ?: continue
            val usesDsr = iniUsesDaiSengokuSaveFolder(iniFile)
            if (usesDsr != null) return usesDsr
        }
        return false
    }

    @JvmStatic
    fun usesDaiSengokuSaveFolder(gameDir: File, target: File): Boolean {
        if (canonicalOrAbsolute(target) == canonicalOrAbsolute(gameDir)) {
            return usesDaiSengokuSaveFolder(gameDir)
        }
        val lower = target.name.lowercase(Locale.ROOT)
        return if (lower.endsWith(".ini") || lower == "alicestart.chs") {
            iniUsesDaiSengokuSaveFolder(target) == true
        } else {
            false
        }
    }

    @JvmStatic
    fun listEntries(gameDir: File): List<String> {
        if (!gameDir.isDirectory || GameCatalog.isSystem3xGameDir(gameDir)) return emptyList()
        val regularFiles = regularTopLevelFiles(gameDir)
        val scripts = system4ScriptFiles(regularFiles)
        val configurations = bootConfigurations(regularFiles, gameDir)
        val configuredScripts = configurations.map { canonicalOrAbsolute(it.script) }.toSet()
        val hasIsolatedSaves = usesDaiSengokuSaveFolder(gameDir) ||
            configurations.any { iniUsesDaiSengokuSaveFolder(it.ini) == true }
        val entries = configurations.map { it.ini } + scripts.filter { script ->
            !configuredScripts.contains(canonicalOrAbsolute(script)) &&
                (!hasIsolatedSaves || script.name.equals("alicecn.ain", ignoreCase = true))
        }
        val hasStandardIni = regularFiles.any { isStandardBootIni(it.name) }
        if (entries.size > 1 || (hasStandardIni && entries.any { !isStandardBootIni(it.name) })) {
            return entries.map { it.name }
        }
        return emptyList()
    }

    @JvmStatic
    fun resolveDefaultLaunchTarget(gameDir: File): File {
        val regularFiles = regularTopLevelFiles(gameDir)
        if (regularFiles.any { isStandardBootIni(it.name) }) {
            return gameDir
        }
        val configurations = bootConfigurations(regularFiles, gameDir)
        configurations.firstOrNull()?.ini?.let { return it }
        val scripts = system4ScriptFiles(regularFiles)
        scripts.firstOrNull { it.name.endsWith(".ain", ignoreCase = true) }?.let { return it }
        scripts.firstOrNull()?.let { return it }
        return gameDir
    }

    @JvmStatic
    fun resolveLaunchTarget(gameDir: File, selectedEntry: String?): File {
        if (!selectedEntry.isNullOrBlank() && selectedEntry in listEntries(gameDir)) {
            return File(gameDir, selectedEntry)
        }
        return resolveDefaultLaunchTarget(gameDir)
    }

    @JvmStatic
    fun resolveLaunchTarget(context: Context, gameDir: File): File =
        resolveLaunchTarget(gameDir, getSelectedEntry(context, gameDir))

    @JvmStatic
    fun resolveIniForSaveFolder(gameDir: File, target: File): File? {
        if (canonicalOrAbsolute(target) == canonicalOrAbsolute(gameDir)) {
            return resolveLaunchIniFile(gameDir).takeIf { it.isFile }
        }
        val lower = target.name.lowercase(Locale.ROOT)
        return if ((lower.endsWith(".ini") || lower == "alicestart.chs") && isRegularNonSymlink(target)) {
            target
        } else {
            null
        }
    }

    private fun prefKey(gameDir: File): String = KEY_BOOT_ENTRY_PREFIX + gameDir.absolutePath

    @JvmStatic
    fun getSelectedEntry(context: Context, gameDir: File): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(prefKey(gameDir), null)
            .orEmpty()
    }

    @JvmStatic
    fun setSelectedEntry(context: Context, gameDir: File, entry: String?) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val key = prefKey(gameDir)
        if (entry.isNullOrBlank()) {
            editor.remove(key)
        } else {
            editor.putString(key, entry)
        }
        editor.apply()
    }

    @JvmStatic
    fun clearSelectedEntry(context: Context, gameDir: File) {
        setSelectedEntry(context, gameDir, null)
    }
}
