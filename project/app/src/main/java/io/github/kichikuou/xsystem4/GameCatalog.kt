package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import java.io.File
import java.security.MessageDigest

data class GameDescriptor @JvmOverloads constructor(
    val id: String,
    val title: String,
    val defaultDirName: String,
    val expectedAinName: String?,
    val iconRes: Int,
    val accentColor: Int,
    val accentBorderColor: Int,
    val iconFrameColor: Int,
    val supportsOnlineBaseInstall: Boolean,
    val onlineBaseCdnUrl: String?,
    val supportsOnlineMod: Boolean,
    val supportsLocalMod: Boolean,
    val isRance10: Boolean,
    val isCnPatch: Boolean = false,
    val overlayCdnBaseUrl: String? = null,
    val overlayCatalogRelPath: String = "files/mods/index.json",
    val supportsUncensoredPatch: Boolean = false,
    val hasChinese: Boolean = false
) {
    val effectiveOverlayCdnUrl: String?
        get() = overlayCdnBaseUrl ?: onlineBaseCdnUrl

    val overlayKind: String
        get() = if (isCnPatch) InstalledModInfo.KIND_CN_PATCH else InstalledModInfo.KIND_MOD
}

data class GameSelectEntry(
    val descriptor: GameDescriptor,
    val items: List<Item>,
    val primaryItem: Item?
)

object GameCatalog {
    const val ID_RANCE10 = "rance10"
    const val ID_DOHNA_DOHNA = "dohnadohna"
    const val ID_EVENICLE = "evenicle"
    const val ID_EVENICLE2 = "evenicle2"
    const val ID_SENGOKU_RANCE = "sengokurance"
    const val ID_RANCE01 = "rance01"
    const val ID_RANCE02 = "rance02"
    const val ID_RANCE03 = "rance03"
    const val ID_RANCE04 = "rance04"
    const val ID_RANCE41 = "rance41"
    const val ID_RANCE42 = "rance42"
    const val ID_RANCE5D = "rance5d"
    const val ID_RANCE06 = "rance06"
    const val ID_RANCE08 = "rance08"
    const val ID_RANCE09 = "rance09"
    const val ID_KICHIKUOU = "kichikuou"
    const val ID_DAIAKUJI = "daiakuji"
    const val ID_DAIBANCHOU = "daibanchou"
    const val ID_DAITEIKOKU = "daiteikoku"
    const val ID_ESCALAYER = "escalayer"
    const val ID_ESCALAYER_REBOOT = "escalayer_reboot"
    const val ID_GALZOO = "galzoo"
    const val ID_IXSEAL = "ixseal"
    const val ID_HARUKA = "haruka"
    const val ID_TSUMASHIBORI = "tsumashibori"
    const val ID_TSUMAMIGUI = "tsumamigui"
    const val ID_TSUMAMIGUI3 = "tsumamigui3"
    const val ID_KAERUNYO = "kaerunyo"
    const val ID_ATLACHNACHA = "atlachnacha"
    const val ID_REISHIKI = "reishiki"
    const val ID_HARUURARE = "haruurare"
    const val ID_OKUSAMAJUN = "okusamajun"
    const val ID_OKUSAMANTR = "okusamantr"
    const val ID_EVENICLE2RANCE = "evenicle2rance"
    const val ID_EVENICLEGAIDEN = "eveniclegaiden"
    const val ID_WAIDONYO = "waidonyo"
    const val ID_RANCE6AFTER = "rance6after"
    const val ID_YAKATA2 = "yakata2"
    const val ID_SDPSMARIA = "sdpsmaria"
    const val ID_TOUSHIN3 = "toushin3"
    const val ID_OYAKORANKAN = "oyakorankan"
    const val ID_YORUGAKURU = "yorugakuru"
    const val ID_RANCE1DIGEST = "rance1digest"
    const val ID_MAMAN = "maman"
    const val ID_EVENICLERANCE = "eveniclerance"
    const val ID_HAHARANMAN = "haharanman"
    const val ID_TSUMAMIGUI2 = "tsumamigui2"
    const val ID_TSUMAMIGUI3JUN = "tsumamigui3jun"
    const val ID_TSUMAMIGUI3NTR = "tsumamigui3ntr"
    const val ID_DIABOLIQUE = "diabolique"
    const val ID_GAKUENKING = "gakuenking"
    const val ID_GAKUENKINGEX = "gakuenkingex"
    const val ID_OUDOUYUUSHA = "oudouyuusha"
    const val ID_TOUSHIN1 = "toushin1"
    const val ID_TOUSHIN2 = "toushin2"
    const val ID_DARCROWS = "darcrows"
    const val ID_IKUSAMIKO = "ikusamiko"
    const val ID_ONLYYOU = "onlyyou"
    const val ID_SANBIKI = "sanbiki"
    const val ID_PERSIOM = "persiom"
    const val ID_SEEINAO = "seeinao"
    const val ID_HUSHABY = "hushaby"
    const val ID_AYUMI = "ayumi"
    const val ID_CIELCLAIRE = "cielclaire"
    const val ID_NIGHTDEMON = "nightdemon"
    const val ID_KATSUMI = "katsumi"
    const val ID_DRSTOP = "drstop"
    const val ID_DPSALL = "dpsall"
    const val ID_KOREDPS = "koredps"
    const val ID_KAERUALICE = "kaerualice"
    const val ID_PROSTUDENTG = "prostudentg"
    const val ID_PROSTUDENTGOOD = "prostudentgood"
    const val ID_PASTELCHIME = "pastelchime"
    const val ID_PASTELCHIME2 = "pastelchime2"
    const val ID_YAKATA3 = "yakata3"
    const val ID_DALK = "dalk"
    const val ID_NINGENGARI = "ningengari"
    const val ID_PASCHACPP = "paschacpp"
    const val ID_PASCHA3 = "pascha3"
    const val ID_MAMATOTO = "mamatoto"
    const val ID_ONLYYOURECROSS = "onlyyourecross"
    const val ID_MAMORIGAMI = "mamorigami"
    const val ID_AMBIVALENZ = "ambivalenz"
    const val ID_FUNNYBEE = "funnybee"
    const val ID_TOUSHIN2SP = "toushin2sp"
    const val ID_MUGENHOUYOU = "mugenhouyou"
    const val ID_OTOMESENKI = "otomesenki"
    const val ID_GAKUENHYOURYUU = "gakuenhyouryuu"
    const val ID_MAIDNOSUSUME = "maidnosusume"
    const val ID_DALKGAIDEN = "dalkgaiden"
    const val ID_MAMANYONYO = "mamanyonyo"
    const val ID_MAJONOSHOKUZAI = "majonoshokuzai"
    const val ID_SABOTEN = "saboten"
    const val ID_DOUBLESENSEI = "doublesensei"
    const val ID_OJOUSAMA = "ojousama"
    const val ID_EDORANZE = "edoranze"
    const val ID_VANISH = "vanish"
    const val ID_HOKENSHITSU = "hokenshitsu"
    const val ID_KAMUNAGARA = "kamunagara"
    const val ID_ERUMEIFLOWER = "erumeiflower"
    const val ID_ERUMEIWAKARE = "erumeiwakare"
    const val ID_MANANA1 = "manana1"
    const val ID_MANANA2 = "manana2"
    const val ID_MANANA3 = "manana3"
    const val ID_MANANA4 = "manana4"
    const val ID_MANANA5 = "manana5"
    const val ID_TSUMASAKURA = "tsumasakura"
    const val ID_SHAMANSSANCTUARY = "shamanssanctuary"
    const val ID_ALIVEZ = "alivez"
    const val ID_DUNGEONSDOLLS = "dungeonsdolls"
    const val ID_SUZUMIYA = "suzumiya"
    const val ID_SHIMAIMA = "shimaima"
    const val ID_DAIBANCHOUGAIDEN = "daibanchougaiden"
    const val ID_COSPLAYLEMON = "cosplaylemon"
    const val ID_WHIPHONEY = "whiphoney"
    const val ID_ONIMUSU = "onimusu"
    const val ID_PROGRX = "progrx"
    const val ID_DPSVERN = "dpsvern"
    const val ID_SUKIIRONO = "sukiirono"
    const val ID_GIRLS = "girls"
    const val ID_DEBUT = "debut"
    const val ID_HARUKAVSESCA = "harukavsesca"
    const val ID_HARUKANINJA = "harukaninja"
    const val ID_MEGANE = "megane"
    const val ID_RACHELSAKURA = "rachelsakura"
    const val ID_SABAKUNIKA = "sabakunika"
    const val ID_VANISHPALM = "vanishpalm"
    const val ID_BLADEBRIDERS = "bladebriders"
    const val ID_DORAPEKO = "dorapeko"
    const val ID_HANIWAPETIT = "haniwapetit"
    const val ID_YAKATA456 = "yakata456"
    const val ID_UMINIOCHITE = "uminiochite"
    const val ID_GAKUENHYOURYUU2 = "gakuenhyouryuu2"
    const val ID_YOUSEI = "yousei"
    const val ID_TOBIRA = "tobira"
    const val ID_BUROBURO = "buroburo"
    const val ID_KUDAKERO = "kudakero"
    const val ID_LITTLEVAMPIRE = "littlevampire"
    const val ID_INTRUDER = "intruder"
    const val ID_CRESCENT = "crescent"
    const val ID_TENGU = "tengu"
    const val ID_DPS = "dps"
    const val ID_YAKATA1 = "yakata1"
    const val ID_SDPSTONO = "sdpstono"
    const val ID_SDPSKAIZOKU = "sdpskaizoku"
    const val ID_PROGOMAKE = "progomake"
    const val ID_AYUMIHINT = "ayumihint"
    const val ID_AYUMILIVE = "ayumilive"
    const val ID_NISENAGURI = "nisenaguri"
    const val ID_CAROL = "carol"
    const val ID_OTHELLOT = "othellot"
    const val ID_MASQUERADE = "masquerade"
    const val ID_DEKIRUKANA = "dekirukana"
    const val ID_TATAKIKOROSHI = "tatakikoroshi"
    const val ID_BALLOONPOP = "balloonpop"
    const val ID_HIYOKO2 = "hiyoko2"
    const val ID_SELFGAME = "selfgame"
    const val ID_MOMOIROGUARDIAN = "momoiroguardian"
    const val ID_RANCE1CLASSIC = "rance1classic"
    const val ID_RANCE2CLASSIC = "rance2classic"
    const val ID_RANCE3CLASSIC = "rance3classic"
    const val ID_DPSSGFAHREN = "dpssgfahren"
    const val ID_DPSSGKATEI = "dpssgkatei"
    const val ID_DPSSGNOBUNAGA = "dpssgnobunaga"
    const val ID_DPSSG2ANTIQUE = "dpssg2antique"
    const val ID_DPSSG2IKENAI = "dpssg2ikenai"
    const val ID_DPSSG2AKAI = "dpssg2akai"
    const val ID_DPSSG3RABBIT = "dpssg3rabbit"
    const val ID_DPSSG3SHINKON = "dpssg3shinkon"
    const val ID_DPSSG3SOTSUGYOU = "dpssg3sotsugyou"
    const val PREFIX_CUSTOM = "custom:"
    const val GITHUB_URL = "https://github.com/TomorrowX6/AliceHub"

    internal fun isSystem4LaunchIni(name: String): Boolean =
        name.equals("System40.ini", true) ||
            name.equals("AliceStart.ini", true) ||
            name.equals("AliceStart2.ini", true) ||
            name.equals("AliceStart.CHS", true)

    internal fun isSystem4AinFile(name: String): Boolean =
        name.endsWith(".ain", true) && !name.equals("System39.ain", true)

    internal fun hasLonginusChsConfig(fileNames: Collection<String>): Boolean =
        fileNames.any { it.equals("AliceStart.CHS", true) || it.equals("CHS.TBL", true) }

    private val CHINESE_MARKER_ASSET_EXTENSIONS = setOf(
        "ain", "afa", "ald", "chs", "ex", "fnl", "ini", "dat", "ttf", "otf"
    )

    /**
     * Detects explicit local Chinese-translation asset markers in a game directory's top-level
     * filenames (ignoring documentation files such as readme/manual `.txt` or `.md`).
     */
    @JvmStatic
    fun hasLocalChineseMarkers(fileNames: Collection<String>): Boolean {
        if (fileNames.isEmpty()) return false
        if (hasLonginusChsConfig(fileNames)) return true
        return fileNames.any { name ->
            val lower = name.lowercase()
            if (lower == "alicecn.ain" ||
                lower == "haru_c.ain" ||
                lower == "gbk.ain" ||
                lower == "simhei.ttf" ||
                lower == "uif_config.json" ||
                lower.endsWith("_chs.ald") ||
                lower.endsWith("chs.afa") ||
                lower.endsWith("chs2.afa") ||
                lower.endsWith("_cn.afa") ||
                lower.endsWith("_cn.ain") ||
                lower.endsWith("_cn.ald")
            ) {
                return@any true
            }
            val ext = lower.substringAfterLast('.', "")
            if (ext !in CHINESE_MARKER_ASSET_EXTENSIONS) {
                return@any false
            }
            lower.startsWith("sourcehansanscn") ||
                lower.contains("_cn_") ||
                lower.contains("汉化") ||
                lower.contains("中文")
        }
    }

    @JvmStatic
    fun hasLocalChineseMarkers(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return hasLocalChineseMarkers(files.map { it.name }) || files.any(::isHarukaChineseScript)
    }

    private fun isHarukaChineseScript(file: File): Boolean {
        // The cloud CN edition keeps the English INI and script filename.
        if (!file.name.equals("haruka.ain", true) || file.length() != 2759127L) return false
        return runCatching {
            MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) } ==
                "c05bb31b0e46c276c7766dd00a76e90367c65fa5d395d4cc45d11983e03ee576"
        }.getOrDefault(false)
    }

    @JvmStatic
    fun hasExplicitNonChineseEdition(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val ini = dir.listFiles()?.firstOrNull { it.isFile && it.name.equals("system3.ini", true) }
            ?: return false
        if (ini.length() > 256L * 1024L) return false
        val legacyId = runCatching { legacySystem3GameId(ini.readBytes()) }.getOrNull() ?: return false
        return legacyId.endsWith("_eng")
    }

    @JvmStatic
    @JvmOverloads
    fun hasChineseTranslation(
        descriptor: GameDescriptor,
        item: Item? = null,
        modInfo: InstalledModInfo? = null
    ): Boolean {
        if (modInfo?.kind == InstalledModInfo.KIND_CN_PATCH) return true
        if (item != null) {
            val localCn = item.cachedHasLocalCnMarkers ?: hasLocalChineseMarkers(item.path).also {
                item.cachedHasLocalCnMarkers = it
            }
            if (localCn) return true
            if (descriptor.id == ID_HARUKA) return false
            val explicitNonCn = item.cachedHasExplicitNonCnEdition ?: hasExplicitNonChineseEdition(item.path).also {
                item.cachedHasExplicitNonCnEdition = it
            }
            if (explicitNonCn) return false
        }
        return descriptor.hasChinese || descriptor.isCnPatch
    }

    internal fun matchConfiguredChsCodeFileName(
        codeName: String?,
        fileNames: Collection<String>
    ): String? {
        val normalized = codeName
            ?.replace('\\', '/')?.substringAfterLast('/')?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        if (!normalized.endsWith(".chs", true) || normalized.equals("AliceStart.CHS", true)) return null
        return fileNames.firstOrNull {
            it.equals(normalized, true) || ArchiveExtractor.repairSjisAsGbkMojibake(it).equals(normalized, true)
        }
    }

    internal fun matchConfiguredSystem4CodeFileName(
        codeName: String?,
        fileNames: Collection<String>
    ): String? {
        matchConfiguredChsCodeFileName(codeName, fileNames)?.let { return it }
        val normalized = codeName
            ?.replace('\\', '/')?.substringAfterLast('/')?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        if (!isSystem4AinFile(normalized)) return null
        return fileNames.firstOrNull {
            it.equals(normalized, true) || ArchiveExtractor.repairSjisAsGbkMojibake(it).equals(normalized, true)
        }
    }

    internal fun configuredChsCodeFileName(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): String? {
        if (readSmallFile == null) return null
        val hasChsTbl = fileNames.any { it.equals("CHS.TBL", true) }
        val hasHaruCn = fileNames.any { it.equals("haru_c.ain", true) }
        fun findFile(target: String) = fileNames.firstOrNull { it == target }
            ?: fileNames.firstOrNull { it.equals(target, true) }
        val iniName = (if (hasHaruCn) findFile("AliceStart2.ini") else null)
            ?: (if (hasChsTbl) findFile("AliceStart.CHS") else null)
            ?: findFile("System40.ini")
            ?: findFile("AliceStart.ini")
            ?: findFile("AliceStart2.ini")
            ?: findFile("AliceStart.CHS")
            ?: return null
        val raw = runCatching { readSmallFile(iniName) }.getOrNull() ?: return null
        val codeName = runCatching { System40Ini.parse(raw).codeName }.getOrNull()
        return matchConfiguredChsCodeFileName(codeName, fileNames)
    }

    internal fun isSystem4CodeFile(
        name: String,
        siblingFileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem4AinFile(name)) return true
        if (!name.endsWith(".chs", true) || name.equals("AliceStart.CHS", true)) return false
        if (hasLonginusChsConfig(siblingFileNames)) return true
        val configured = configuredChsCodeFileName(siblingFileNames, readSmallFile)
        return configured != null && (
            name.equals(configured, true) ||
                ArchiveExtractor.repairSjisAsGbkMojibake(name).equals(configured, true)
            )
    }

    private fun hasSystem4Markers(fileNames: Collection<String>): Boolean = fileNames.any {
        isSystem4CodeFile(it, fileNames) || isSystem4LaunchIni(it)
    }

    internal fun isSystem35Scenario(name: String): Boolean =
        name.endsWith("sa.ald", true) || name.endsWith("sa_chs.ald", true) || name.equals("adisk.ald", true)

    private fun hasSystem35Markers(fileNames: Collection<String>): Boolean = fileNames.any {
        isSystem35Scenario(it) || it.equals("System39.ini", true) || it.equals("System39.ain", true)
    }

    private fun hasLegacySystem3Data(fileNames: Collection<String>): Boolean =
        fileNames.any { it.equals("ADISK.DAT", true) } &&
            fileNames.any { it.equals("ACG.DAT", true) || it.equals("ACG0.DAT", true) || it.equals("ACG3.DAT", true) }

    fun isLegacySystem3GameFiles(fileNames: Collection<String>): Boolean =
        hasLegacySystem3Data(fileNames) && !hasSystem4Markers(fileNames) && !hasSystem35Markers(fileNames)

    fun isLegacySystem3GameDir(dir: File?): Boolean =
        isLegacySystem3GameFiles(dir?.listFiles().orEmpty().filter { it.isFile }.map { it.name })

    fun isSystem3xGameFiles(fileNames: Collection<String>): Boolean =
        !hasSystem4Markers(fileNames) && (hasSystem35Markers(fileNames) || hasLegacySystem3Data(fileNames))

    internal fun legacySystem3Title(ini: ByteArray?): String? =
        ini?.toString(Charsets.UTF_8)?.lineSequence()?.firstNotNullOfOrNull { line ->
            val key = line.substringBefore('=').trim()
            if (key.equals("title", true)) line.substringAfter('=', "").trim().ifEmpty { null } else null
        }

    internal fun legacySystem3GameId(ini: ByteArray?): String? =
        ini?.toString(Charsets.UTF_8)?.lineSequence()?.firstNotNullOfOrNull { line ->
            val key = line.substringBefore('=').trim()
            if (key.equals("game", true)) line.substringAfter('=', "").trim().lowercase().ifEmpty { null } else null
        }

    internal fun legacySystem3Title(dir: File): String? {
        val ini = dir.listFiles()?.firstOrNull { it.isFile && it.name.equals("system3.ini", true) }
        return if (ini != null && ini.length() <= 256 * 1024) {
            runCatching { legacySystem3Title(ini.readBytes()) }.getOrNull()
        } else null
    }

    fun isSystem3xGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isSystem3xGameFiles(files.map { it.name })
    }

    @JvmOverloads
    fun isEscalayerGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any { it.equals("DOKI_SA.ALD", true) }) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            try {
                val raw = readSmallFile(iniName)
                if (raw != null) {
                    val title = System40Ini.parse(raw).gameName?.lowercase() ?: ""
                    if (title.contains("エスカレイヤー") || title.contains("escalayer") || title.contains("超昂天使")) {
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun isEscalayerGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isEscalayerGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) f.readBytes() else null
        }
    }

    @JvmOverloads
    fun isKichikuouGameFiles(
        fileNames: Collection<String>,
        bgmFileNames: Collection<String> = emptyList(),
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("KichikuRanceSA.ALD", true) ||
                    it.equals("KichikuRanceGA.ald", true) ||
                    repaired.equals("鬼畜王SA.ALD", true) ||
                    it.equals("鬼畜王SA.ALD", true) ||
                    repaired.equals("鬼畜王GA.ALD", true) ||
                    it.equals("鬼畜王GA.ALD", true)
            }
        ) {
            return true
        }
        val playlistName = fileNames.firstOrNull { it.equals("playlist.txt", true) }
        if (playlistName != null && readSmallFile != null) {
            try {
                val raw = readSmallFile(playlistName)
                if (raw != null && String(raw, Charsets.ISO_8859_1).lowercase().contains("kichiku_")) {
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        if (bgmFileNames.any { it.lowercase().startsWith("kichiku_") }) {
            return true
        }
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            try {
                val raw = readSmallFile(iniName)
                if (raw != null) {
                    val title = System40Ini.parse(raw).gameName?.lowercase() ?: ""
                    if (title.contains("鬼畜王") || title.contains("kichiku")) {
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun isKichikuouGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        val fileNames = files.map { it.name }
        if (!isSystem3xGameFiles(fileNames)) return false
        val bgmDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("bgm", true) }
        val bgmFileNames = bgmDir?.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        return isKichikuouGameFiles(fileNames, bgmFileNames) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) f.readBytes() else null
        }
    }

    private val DAIAKUJI_ALIAS_TOKENS = listOf(
        "daiakuji",
        "daiakuzi",
        "大悪司",
        "大恶司",
        "戭埆巌",
        "戝埆巌"
    )

    private val DAIAKUJI_ALD_NAMES = setOf(
        "大悪司sa.ald",
        "大悪司ga.ald",
        "大悪司wa.ald",
        "大悪司da.ald",
        "大恶司sa.ald",
        "大恶司ga.ald",
        "大恶司wa.ald",
        "大恶司da.ald",
        "daiakujisa.ald",
        "daiakujiga.ald",
        "daiakujiwa.ald",
        "daiakujida.ald",
        "daiakuji_sa.ald",
        "daiakuji_ga.ald",
        "daiakuzisa.ald",
        "daiakuziga.ald",
        "戭埆巌sa.ald",
        "戭埆巌ga.ald",
        "戭埆巌wa.ald",
        "戭埆巌da.ald",
        "戝埆巌sa.ald",
        "戝埆巌ga.ald",
        "戝埆巌wa.ald",
        "戝埆巌da.ald"
    )

    fun isDaiakujiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return DAIAKUJI_ALIAS_TOKENS.any { token ->
            repaired.contains(token) || rawLower.contains(token)
        }
    }

    @JvmOverloads
    fun isDaiakujiGameFiles(
        fileNames: Collection<String>,
        bgmFileNames: Collection<String> = emptyList(),
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val lower = it.lowercase()
                val repairedLower = ArchiveExtractor.repairSjisAsGbkMojibake(it).lowercase()
                lower in DAIAKUJI_ALD_NAMES || repairedLower in DAIAKUJI_ALD_NAMES
            }
        ) {
            return true
        }
        if (bgmFileNames.any {
                val lower = it.lowercase()
                lower.startsWith("daiakuji_") || lower.startsWith("daiakuzi_")
            }
        ) {
            return true
        }
        if (readSmallFile != null) {
            for (listName in listOf("_inmm.ini", "playlist.txt")) {
                val matchName = fileNames.firstOrNull { it.equals(listName, true) } ?: continue
                try {
                    val raw = readSmallFile(matchName) ?: continue
                    val latin = String(raw, Charsets.ISO_8859_1).lowercase()
                    if (latin.contains("daiakuji_") || latin.contains("daiakuzi_")) {
                        return true
                    }
                    val decoded = ArchiveExtractor.decodeBestCjkText(raw)
                    if (isDaiakujiAlias(decoded)) {
                        return true
                    }
                } catch (_: Throwable) {
                }
            }
            val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
            if (iniName != null) {
                try {
                    val raw = readSmallFile(iniName)
                    if (raw != null) {
                        val title = System40Ini.parse(raw).gameName ?: ""
                        if (isDaiakujiAlias(title)) {
                            return true
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return false
    }

    fun isDaiakujiGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        val fileNames = files.map { it.name }
        if (!isSystem3xGameFiles(fileNames)) return false
        val bgmDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("bgm", true) }
        val bgmFileNames = bgmDir?.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        return isDaiakujiGameFiles(fileNames, bgmFileNames) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) f.readBytes() else null
        }
    }

    /**
     * Classifies a game root strictly from its files (never directory/display names), returning
     * a built-in game ID if recognized or null for custom/unrecognized roots.
     */
    @JvmOverloads
    fun classifyGameRootFiles(
        fileNames: Collection<String>,
        bgmFileNames: Collection<String> = emptyList(),
        readSmallFile: ((String) -> ByteArray?)? = null,
        getFileSize: ((String) -> Long?)? = null
    ): String? {
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) }
        if (ainNames.any { it.equals("Rance10.ain", ignoreCase = true) }) {
            return ID_RANCE10
        }
        if (ainNames.any { it.equals("dohnadohna.ain", ignoreCase = true) }) {
            return ID_DOHNA_DOHNA
        }
        if (isEvenicle2RanceGameFiles(fileNames, readSmallFile)) {
            return ID_EVENICLE2RANCE
        }
        if (isEvenicleRanceGameFiles(fileNames, readSmallFile)) {
            return ID_EVENICLERANCE
        }
        if (isEvenicleGaidenGameFiles(fileNames, readSmallFile)) {
            return ID_EVENICLEGAIDEN
        }
        if (ainNames.any { it.equals("Evenicle2.ain", ignoreCase = true) }) {
            return ID_EVENICLE2
        }
        if (ainNames.any { it.equals("Evenicle.ain", ignoreCase = true) }) {
            return ID_EVENICLE
        }
        if (ainNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("EscalayerReboot.ain", ignoreCase = true) ||
                    repaired.equals("EscalayerReboot.ain", ignoreCase = true) ||
                    it.equals("エスカレイヤー・リブート.ain", ignoreCase = true) ||
                    it.equals("エスカレイヤーリブート.ain", ignoreCase = true) ||
                    repaired.equals("エスカレイヤー・リブート.ain", ignoreCase = true) ||
                    repaired.equals("エスカレイヤーリブート.ain", ignoreCase = true)
            }
        ) {
            return ID_ESCALAYER_REBOOT
        }
        if (isRance1DigestGameFiles(fileNames, readSmallFile)) {
            return ID_RANCE1DIGEST
        }
        if (ainNames.any { it.equals("Rance01.ain", ignoreCase = true) } ||
            fileNames.any { it.startsWith("Rance01", ignoreCase = true) && it.endsWith(".ald", ignoreCase = true) }
        ) {
            return ID_RANCE01
        }
        if (ainNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("Rance02.ain", ignoreCase = true) ||
                    it.equals("ランス０２.ain", ignoreCase = true) ||
                    it.equals("ランス02.ain", ignoreCase = true) ||
                    repaired.equals("ランス０２.ain", ignoreCase = true) ||
                    repaired.equals("ランス02.ain", ignoreCase = true)
            } || fileNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.endsWith(".ald", ignoreCase = true) &&
                    (it.startsWith("ランス０２") || it.startsWith("ランス02") ||
                        repaired.startsWith("ランス０２") || repaired.startsWith("ランス02"))
            }
        ) {
            return ID_RANCE02
        }
        if (ainNames.any { it.equals("Rance03.ain", ignoreCase = true) }) {
            return ID_RANCE03
        }
        if (isRance04GameFiles(fileNames)) {
            return ID_RANCE04
        }
        if (isRance1ClassicGameFiles(fileNames, readSmallFile)) {
            return ID_RANCE1CLASSIC
        }
        if (isRance2ClassicGameFiles(fileNames, readSmallFile)) {
            return ID_RANCE2CLASSIC
        }
        if (isRance3ClassicGameFiles(fileNames, readSmallFile)) {
            return ID_RANCE3CLASSIC
        }
        if (isRance41GameFiles(fileNames, readSmallFile)) {
            return ID_RANCE41
        }
        if (isRance42GameFiles(fileNames, readSmallFile)) {
            return ID_RANCE42
        }
        if (isRance5dGameFiles(fileNames)) {
            return ID_RANCE5D
        }
        if (isRance6AfterGameFiles(fileNames, readSmallFile)) {
            return ID_RANCE6AFTER
        }
        if (isRance06GameFiles(fileNames)) {
            return ID_RANCE06
        }
        if (isGalzooGameFiles(fileNames)) {
            return ID_GALZOO
        }
        if (isHarukaVsEscaGameFiles(fileNames, readSmallFile)) {
            return ID_HARUKAVSESCA
        }
        if (isHarukaNinjaGameFiles(fileNames, readSmallFile)) {
            return ID_HARUKANINJA
        }
        if (isHarukaGameFiles(fileNames, readSmallFile)) {
            return ID_HARUKA
        }
        if (ainNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("Rance09.ain", ignoreCase = true) ||
                    it.equals("Rance9.ain", ignoreCase = true) ||
                    repaired.equals("Rance9.ain", ignoreCase = true)
            }
        ) {
            return ID_RANCE09
        }
        if (ainNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("Rance08.ain", ignoreCase = true) ||
                    it.equals("Rance8.ain", ignoreCase = true) ||
                    it.equals("RanceQuest.ain", ignoreCase = true) ||
                    it.contains("ランス・クエスト") ||
                    it.contains("儔儞僗丒僋僄僗僩") ||
                    repaired.contains("ランス・クエスト")
            }
        ) {
            return ID_RANCE08
        }
        if (isSanbikiGameFiles(fileNames, readSmallFile)) {
            return ID_SANBIKI
        }
        if (ainNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("SengokuRance.ain", ignoreCase = true) ||
                    it.equals("AliceCN.ain", ignoreCase = true) ||
                    it.equals("DaiSengoku.ain", ignoreCase = true) ||
                    it.contains("愴崙儔儞僗") ||
                    it.contains("戦国ランス") ||
                    repaired.contains("戦国ランス")
            } || fileNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.endsWith("GA.ald", ignoreCase = true) &&
                    (it.equals("DaiSengokuGA.ald", ignoreCase = true) ||
                        it.contains("愴崙") || it.contains("戦国") || repaired.contains("戦国"))
            }
        ) {
            return ID_SENGOKU_RANCE
        }
        if (isIxsealGameFiles(fileNames, readSmallFile)) {
            return ID_IXSEAL
        }
        if (isTsumamigui3JunGameFiles(fileNames, readSmallFile)) {
            return ID_TSUMAMIGUI3JUN
        }
        if (isTsumamigui3NtrGameFiles(fileNames, readSmallFile)) {
            return ID_TSUMAMIGUI3NTR
        }
        if (isTsumamigui3GameFiles(fileNames, readSmallFile)) {
            return ID_TSUMAMIGUI3
        }
        if (isTsumashiboriGameFiles(fileNames, readSmallFile)) {
            return ID_TSUMASHIBORI
        }
        if (isDaibanchouGaidenGameFiles(fileNames, readSmallFile)) {
            return ID_DAIBANCHOUGAIDEN
        }
        if (isDaibanchouGameFiles(fileNames, readSmallFile)) {
            return ID_DAIBANCHOU
        }
        if (isDaiteikokuGameFiles(fileNames, readSmallFile)) {
            return ID_DAITEIKOKU
        }
        if (isHaruurareGameFiles(fileNames, readSmallFile)) {
            return ID_HARUURARE
        }
        if (isOkusamaJunGameFiles(fileNames, readSmallFile)) {
            return ID_OKUSAMAJUN
        }
        if (isOkusamaNtrGameFiles(fileNames, readSmallFile)) {
            return ID_OKUSAMANTR
        }
        if (isWaidonyoGameFiles(fileNames, readSmallFile)) {
            return ID_WAIDONYO
        }
        if (isYakata2GameFiles(fileNames, readSmallFile)) {
            return ID_YAKATA2
        }
        if (isYakata1GameFiles(fileNames, readSmallFile)) {
            return ID_YAKATA1
        }
        if (isLittleVampireGameFiles(fileNames, readSmallFile)) {
            return ID_LITTLEVAMPIRE
        }
        if (isIntruderGameFiles(fileNames, readSmallFile)) {
            return ID_INTRUDER
        }
        if (isCrescentGameFiles(fileNames, readSmallFile)) {
            return ID_CRESCENT
        }
        if (isTenguGameFiles(fileNames, readSmallFile)) {
            return ID_TENGU
        }
        if (isDpsGameFiles(fileNames, readSmallFile)) {
            return ID_DPS
        }
        if (isDpsSgFahrenGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSGFAHREN
        }
        if (isDpsSgKateiGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSGKATEI
        }
        if (isDpsSgNobunagaGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSGNOBUNAGA
        }
        if (isDpsSg2AntiqueGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSG2ANTIQUE
        }
        if (isDpsSg2IkenaiGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSG2IKENAI
        }
        if (isDpsSg2AkaiGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSG2AKAI
        }
        if (isDpsSg3RabbitGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSG3RABBIT
        }
        if (isDpsSg3ShinkonGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSG3SHINKON
        }
        if (isDpsSg3SotsugyouGameFiles(fileNames, readSmallFile)) {
            return ID_DPSSG3SOTSUGYOU
        }
        if (isSdpsTonoGameFiles(fileNames, readSmallFile)) {
            return ID_SDPSTONO
        }
        if (isSdpsKaizokuGameFiles(fileNames, readSmallFile)) {
            return ID_SDPSKAIZOKU
        }
        if (isSdpsMariaGameFiles(fileNames, readSmallFile)) {
            return ID_SDPSMARIA
        }
        if (isGakuenHyouryuu2GameFiles(fileNames, readSmallFile)) {
            return ID_GAKUENHYOURYUU2
        }
        if (isGakuenHyouryuuGameFiles(fileNames, readSmallFile)) {
            return ID_GAKUENHYOURYUU
        }
        if (isGakuenKingGameFiles(fileNames, readSmallFile)) {
            return ID_GAKUENKING
        }
        if (isOnlyYouReCrossGameFiles(fileNames, readSmallFile)) {
            return ID_ONLYYOURECROSS
        }
        if (isOnlyYouGameFiles(fileNames, readSmallFile)) {
            return ID_ONLYYOU
        }
        if (isAyumiLiveGameFiles(fileNames, readSmallFile)) {
            return ID_AYUMILIVE
        }
        if (isAyumiHintGameFiles(fileNames, readSmallFile)) {
            return ID_AYUMIHINT
        }
        if (isAyumiGameFiles(fileNames, readSmallFile)) {
            return ID_AYUMI
        }
        if (isDpsAllGameFiles(fileNames, readSmallFile)) {
            return ID_DPSALL
        }
        if (isProgOmakeGameFiles(fileNames, readSmallFile)) {
            return ID_PROGOMAKE
        }
        if (isProstudentGGameFiles(fileNames, readSmallFile)) {
            return ID_PROSTUDENTG
        }
        if (isNiseNaguriGameFiles(fileNames, readSmallFile)) {
            return ID_NISENAGURI
        }
        if (isYakata3GameFiles(fileNames, readSmallFile)) {
            return ID_YAKATA3
        }
        if (isAmbivalenzGameFiles(fileNames, readSmallFile)) {
            return ID_AMBIVALENZ
        }
        if (isFunnyBeeGameFiles(fileNames, readSmallFile)) {
            return ID_FUNNYBEE
        }
        if (isToushin2SpGameFiles(fileNames, readSmallFile)) {
            return ID_TOUSHIN2SP
        }
        if (isMugenHouyouGameFiles(fileNames, readSmallFile)) {
            return ID_MUGENHOUYOU
        }
        if (isOtomeSenkiGameFiles(fileNames, readSmallFile)) {
            return ID_OTOMESENKI
        }
        if (isMamatotoGameFiles(fileNames, readSmallFile)) {
            return ID_MAMATOTO
        }
        if (isMamorigamiGameFiles(fileNames, readSmallFile)) {
            return ID_MAMORIGAMI
        }
        if (isMaidNoSusumeGameFiles(fileNames, readSmallFile)) {
            return ID_MAIDNOSUSUME
        }
        if (isDalkGaidenGameFiles(fileNames, readSmallFile)) {
            return ID_DALKGAIDEN
        }
        if (isMamanyonyoGameFiles(fileNames, readSmallFile)) {
            return ID_MAMANYONYO
        }
        if (isMajoNoShokuzaiGameFiles(fileNames, readSmallFile)) {
            return ID_MAJONOSHOKUZAI
        }
        if (isSabotenGameFiles(fileNames, readSmallFile)) {
            return ID_SABOTEN
        }
        if (isDoubleSenseiGameFiles(fileNames, readSmallFile)) {
            return ID_DOUBLESENSEI
        }
        if (isOjousamaGameFiles(fileNames, readSmallFile)) {
            return ID_OJOUSAMA
        }
        if (isEdoranzeGameFiles(fileNames, readSmallFile)) {
            return ID_EDORANZE
        }
        if (isVanishPalmGameFiles(fileNames, readSmallFile)) {
            return ID_VANISHPALM
        }
        if (isVanishGameFiles(fileNames, readSmallFile)) {
            return ID_VANISH
        }
        if (isHokenshitsuGameFiles(fileNames, readSmallFile)) {
            return ID_HOKENSHITSU
        }
        if (isKamunagaraGameFiles(fileNames, readSmallFile)) {
            return ID_KAMUNAGARA
        }
        if (isErumeiWakareGameFiles(fileNames, readSmallFile, getFileSize)) {
            return ID_ERUMEIWAKARE
        }
        if (isErumeiFlowerGameFiles(fileNames, readSmallFile, getFileSize)) {
            return ID_ERUMEIFLOWER
        }
        if (isManana1GameFiles(fileNames, readSmallFile)) {
            return ID_MANANA1
        }
        if (isManana2GameFiles(fileNames, readSmallFile)) {
            return ID_MANANA2
        }
        if (isManana3GameFiles(fileNames, readSmallFile)) {
            return ID_MANANA3
        }
        if (isManana4GameFiles(fileNames, readSmallFile)) {
            return ID_MANANA4
        }
        if (isManana5GameFiles(fileNames, readSmallFile)) {
            return ID_MANANA5
        }
        if (isTsumaSakuraGameFiles(fileNames, readSmallFile)) {
            return ID_TSUMASAKURA
        }
        if (isShamansSanctuaryGameFiles(fileNames, readSmallFile)) {
            return ID_SHAMANSSANCTUARY
        }
        if (isAliveZGameFiles(fileNames, readSmallFile)) {
            return ID_ALIVEZ
        }
        if (isDungeonsDollsGameFiles(fileNames, readSmallFile)) {
            return ID_DUNGEONSDOLLS
        }
        if (isSuzumiyaGameFiles(fileNames, readSmallFile)) {
            return ID_SUZUMIYA
        }
        if (isShimaimaGameFiles(fileNames, readSmallFile)) {
            return ID_SHIMAIMA
        }
        if (isCosplayLemonGameFiles(fileNames, readSmallFile)) {
            return ID_COSPLAYLEMON
        }
        if (isWhipHoneyGameFiles(fileNames, readSmallFile)) {
            return ID_WHIPHONEY
        }
        if (isOnimusuGameFiles(fileNames, readSmallFile)) {
            return ID_ONIMUSU
        }
        if (isProGrxGameFiles(fileNames, readSmallFile)) {
            return ID_PROGRX
        }
        if (isDpsVerNGameFiles(fileNames, readSmallFile)) {
            return ID_DPSVERN
        }
        if (isSukiironoGameFiles(fileNames, readSmallFile)) {
            return ID_SUKIIRONO
        }
        if (isGirlsGameFiles(fileNames, readSmallFile)) {
            return ID_GIRLS
        }
        if (isDebutGameFiles(fileNames, readSmallFile)) {
            return ID_DEBUT
        }
        if (isMeganeGameFiles(fileNames, readSmallFile)) {
            return ID_MEGANE
        }
        if (isRachelSakuraGameFiles(fileNames, readSmallFile)) {
            return ID_RACHELSAKURA
        }
        if (isSabakuNikaGameFiles(fileNames, readSmallFile)) {
            return ID_SABAKUNIKA
        }
        if (isBladeBridersGameFiles(fileNames, readSmallFile)) {
            return ID_BLADEBRIDERS
        }
        if (isDorapekoGameFiles(fileNames, readSmallFile)) {
            return ID_DORAPEKO
        }
        if (isHaniwaPetitGameFiles(fileNames, readSmallFile)) {
            return ID_HANIWAPETIT
        }
        if (isYakata456GameFiles(fileNames, readSmallFile)) {
            return ID_YAKATA456
        }
        if (isUmiNiOchiteGameFiles(fileNames, readSmallFile)) {
            return ID_UMINIOCHITE
        }
        if (isYouseiGameFiles(fileNames, readSmallFile)) {
            return ID_YOUSEI
        }
        if (isTobiraGameFiles(fileNames, readSmallFile)) {
            return ID_TOBIRA
        }
        if (isBuroburoGameFiles(fileNames, readSmallFile)) {
            return ID_BUROBURO
        }
        if (isKudakeroGameFiles(fileNames, readSmallFile)) {
            return ID_KUDAKERO
        }
        if (isCarolGameFiles(fileNames, readSmallFile)) {
            return ID_CAROL
        }
        if (isOthellotGameFiles(fileNames, readSmallFile)) {
            return ID_OTHELLOT
        }
        if (isMasqueradeGameFiles(fileNames, readSmallFile)) {
            return ID_MASQUERADE
        }
        if (isDekirukanaGameFiles(fileNames, readSmallFile)) {
            return ID_DEKIRUKANA
        }
        if (isTatakikoroshiGameFiles(fileNames, readSmallFile)) {
            return ID_TATAKIKOROSHI
        }
        if (isBalloonPopGameFiles(fileNames, readSmallFile)) {
            return ID_BALLOONPOP
        }
        if (isHiyoko2GameFiles(fileNames, readSmallFile)) {
            return ID_HIYOKO2
        }
        if (isSelfGameFiles(fileNames, readSmallFile)) {
            return ID_SELFGAME
        }
        if (isMomoiroGuardianGameFiles(fileNames, readSmallFile)) {
            return ID_MOMOIROGUARDIAN
        }
        if (isPascha3GameFiles(fileNames, readSmallFile)) {
            return ID_PASCHA3
        }
        if (isPaschaCppGameFiles(fileNames, readSmallFile)) {
            return ID_PASCHACPP
        }
        if (isPastelChime2GameFiles(fileNames, readSmallFile)) {
            return ID_PASTELCHIME2
        }
        if (isToushin3GameFiles(fileNames, readSmallFile)) {
            return ID_TOUSHIN3
        }
        if (isToushin2GameFiles(fileNames, readSmallFile)) {
            return ID_TOUSHIN2
        }
        if (isToushin1GameFiles(fileNames, readSmallFile)) {
            return ID_TOUSHIN1
        }
        if (isOyakorankanGameFiles(fileNames, readSmallFile)) {
            return ID_OYAKORANKAN
        }
        if (isYorugakuruGameFiles(fileNames, readSmallFile)) {
            return ID_YORUGAKURU
        }
        if (isMamanGameFiles(fileNames, readSmallFile)) {
            return ID_MAMAN
        }
        if (isHaharanmanGameFiles(fileNames, readSmallFile)) {
            return ID_HAHARANMAN
        }
        if (isTsumamigui2GameFiles(fileNames, readSmallFile)) {
            return ID_TSUMAMIGUI2
        }
        if (isTsumamiguiGameFiles(fileNames, readSmallFile)) {
            return ID_TSUMAMIGUI
        }
        if (isDiaboliqueGameFiles(fileNames, readSmallFile)) {
            return ID_DIABOLIQUE
        }
        if (isGakuenKingExGameFiles(fileNames, readSmallFile)) {
            return ID_GAKUENKINGEX
        }
        if (isOudouYuushaGameFiles(fileNames, readSmallFile)) {
            return ID_OUDOUYUUSHA
        }
        if (isDarcrowsGameFiles(fileNames, readSmallFile)) {
            return ID_DARCROWS
        }
        if (isIkusaMikoGameFiles(fileNames, readSmallFile)) {
            return ID_IKUSAMIKO
        }
        if (isPersiomGameFiles(fileNames, readSmallFile)) {
            return ID_PERSIOM
        }
        if (isSeeInAoGameFiles(fileNames, readSmallFile)) {
            return ID_SEEINAO
        }
        if (isHushabyGameFiles(fileNames, readSmallFile)) {
            return ID_HUSHABY
        }
        if (isCielClaireGameFiles(fileNames, readSmallFile)) {
            return ID_CIELCLAIRE
        }
        if (isNightDemonGameFiles(fileNames, readSmallFile)) {
            return ID_NIGHTDEMON
        }
        if (isKatsumiGameFiles(fileNames, readSmallFile)) {
            return ID_KATSUMI
        }
        if (isDrStopGameFiles(fileNames, readSmallFile)) {
            return ID_DRSTOP
        }
        if (isKoreDpsGameFiles(fileNames, readSmallFile)) {
            return ID_KOREDPS
        }
        if (isKaeruAliceGameFiles(fileNames, readSmallFile)) {
            return ID_KAERUALICE
        }
        if (isProstudentGoodGameFiles(fileNames, readSmallFile)) {
            return ID_PROSTUDENTGOOD
        }
        if (isPastelChimeGameFiles(fileNames, readSmallFile)) {
            return ID_PASTELCHIME
        }
        if (isDalkGameFiles(fileNames, readSmallFile)) {
            return ID_DALK
        }
        if (isNingenGariGameFiles(fileNames, readSmallFile)) {
            return ID_NINGENGARI
        }
        if (isKaerunyoGameFiles(fileNames, readSmallFile)) {
            return ID_KAERUNYO
        }
        if (isAtlachnachaGameFiles(fileNames, readSmallFile)) {
            return ID_ATLACHNACHA
        }
        if (isReishikiGameFiles(fileNames, readSmallFile)) {
            return ID_REISHIKI
        }
        if (isEscalayerGameFiles(fileNames, readSmallFile)) {
            return ID_ESCALAYER
        }
        if (isKichikuouGameFiles(fileNames, bgmFileNames, readSmallFile)) {
            return ID_KICHIKUOU
        }
        if (isDaiakujiGameFiles(fileNames, bgmFileNames, readSmallFile)) {
            return ID_DAIAKUJI
        }
        return null
    }

    fun classifyGameRootDir(gameDir: File?): String? {
        if (gameDir == null || !gameDir.isDirectory) return null
        val children = gameDir.listFiles() ?: return null
        val files = children.filter { it.isFile }
        val fileNames = files.map { it.name }
        val bgmFileNames = if (isSystem3xGameFiles(fileNames)) {
            children.firstOrNull { it.isDirectory && it.name.equals("bgm", true) }
                ?.listFiles()
                ?.filter { it.isFile }
                ?.map { it.name }
                .orEmpty()
        } else {
            emptyList()
        }
        return classifyGameRootFiles(
            fileNames = fileNames,
            bgmFileNames = bgmFileNames,
            readSmallFile = { name ->
                val f = files.firstOrNull { it.name.equals(name, true) }
                when {
                    f == null -> null
                    name.equals("ADISK.DAT", true) || name.equals("BDISK.DAT", true) -> readFilePrefix(f, 256)
                    f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                    else -> null
                }
            },
            getFileSize = { name ->
                files.firstOrNull { it.name.equals(name, true) }?.length()
            }
        )
    }

    @JvmOverloads
    fun matchesTargetGameFiles(
        targetGameId: String?,
        fileNames: Collection<String>,
        bgmFileNames: Collection<String> = emptyList(),
        readSmallFile: ((String) -> ByteArray?)? = null,
        getFileSize: ((String) -> Long?)? = null
    ): Boolean {
        if (targetGameId.isNullOrBlank() || targetGameId.startsWith(PREFIX_CUSTOM)) {
            return true
        }
        return classifyGameRootFiles(fileNames, bgmFileNames, readSmallFile, getFileSize) == targetGameId
    }

    fun matchesTargetGame(targetGameId: String?, gameDir: File?): Boolean {
        if (targetGameId.isNullOrBlank() || targetGameId.startsWith(PREFIX_CUSTOM)) {
            return true
        }
        if (gameDir == null || !gameDir.isDirectory) return false
        return classifyGameRootDir(gameDir) == targetGameId
    }

    fun validateImportedGameDir(
        gameDir: File?,
        expectedAinName: String?,
        targetGameId: String?
    ): Boolean {
        if (gameDir == null || !gameDir.isDirectory) return false
        if (!expectedAinName.isNullOrBlank()) {
            val hasExpected = gameDir.listFiles()?.any {
                it.isFile && (
                    it.name.equals(expectedAinName, ignoreCase = true) ||
                        ArchiveExtractor.repairSjisAsGbkMojibake(it.name).equals(expectedAinName, ignoreCase = true)
                    )
            } == true || System4BootEntry.bootConfigurations(gameDir).any {
                it.script.name.equals(expectedAinName, ignoreCase = true) ||
                    ArchiveExtractor.repairSjisAsGbkMojibake(it.script.name).equals(expectedAinName, ignoreCase = true)
            }
            if (!hasExpected) return false
        }
        return matchesTargetGame(targetGameId, gameDir)
    }

    private const val RUNNING_GAME_FILE = ".running_game_root"
    private const val RUNNING_LAUNCH_TARGET_FILE = ".running_launch_target"
    private val RANCE01_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?1(?![0-9０-９])")
    private val RANCE02_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?2(?![0-9０-９])")
    private val RANCE03_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?3(?![0-9０-９])")
    private val RANCE06_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯[0０]?[6６](?![0-9０-９])")
    private val RANCE06_JP_ALIAS_REGEX = Regex("(?<![0-9０-９])ランス[0０]?[6６](?![0-9０-９])")
    private val RANCE06_EN_ALIAS_REGEX = Regex("(?<![a-z])rance\\s*(?:0?6(?![0-9])|vi(?![a-z]))", RegexOption.IGNORE_CASE)
    private val RANCE06_ALD_REGEX = Regex("^(?:rance0?6|ランス[0０]?[6６])(?:|[a-z_].*)\\.ald$", RegexOption.IGNORE_CASE)
    private val RANCE06_SAVE_SLOT_REGEX = Regex("^Save[A-La-l]\\.asd$")
    private val RANCE08_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?8(?![0-9０-９])")
    private val RANCE09_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?9(?![0-9０-９])")
    private val ESCALAYER_R_JA_ALIAS_REGEX = Regex("エスカレイヤー[\\s・_-]*r(?![a-z0-9])")
    private val ESCALAYER_R_CN_ALIAS_REGEX = Regex("艾斯卡蕾雅[\\s・_-]*r(?![a-z0-9])")
    private val ESCALAYER_R_EN_ALIAS_REGEX = Regex("(?<![a-z0-9])escalayer[\\s._-]*r(?![a-z0-9])")
    private val GALZOO_EN_ALIAS_REGEX =
        Regex("(?<![a-z0-9])galzoo(?:[\\s._-]*(?:island|cn))?(?![a-z0-9])", RegexOption.IGNORE_CASE)
    private val GALZOO_ALD_REGEX =
        Regex("^(?:galzoo(?:island)?|galzooアイランド|ギャルズーアイランド)(?:|[a-z_].*)\\.ald$", RegexOption.IGNORE_CASE)
    private val GALZOO_SAVE_SLOT_REGEX =
        Regex("^(?:galzoo|ギャルズー).*\\.(?:asd|sav|rsm)$", RegexOption.IGNORE_CASE)
    private val HARUKA_EN_ALIAS_REGEX =
        Regex("(?<![a-z0-9])(?:beat\\s+blades\\s+haruka|choukou\\s+sennin\\s+haruka|haruka)(?![a-z0-9])", RegexOption.IGNORE_CASE)
    private val HARUKA_ALD_REGEX =
        Regex("^(?:超昂閃忍ハルカ|超昂闪忍遥|超昂閃忍遥|haruka)(?:|[a-z_].*)\\.ald$", RegexOption.IGNORE_CASE)
    private val HARUKA_SYSTEM_SAVE_REGEX =
        Regex("^(?:超昂閃忍ハルカ|超昂闪忍遥|超昂閃忍遥|beat\\s*blades\\s*haruka|choukou\\s*sennin\\s*haruka|haruka)(?:sf|sys|fn)\\.asd$", RegexOption.IGNORE_CASE)

    fun isHarukaSystemSaveFileName(fileName: String): Boolean {
        val raw = fileName.trim()
        if (raw.isEmpty()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        return raw.equals("システム共通データ.asd", ignoreCase = true) ||
            repaired.equals("システム共通データ.asd", ignoreCase = true) ||
            HARUKA_SYSTEM_SAVE_REGEX.matches(raw) ||
            HARUKA_SYSTEM_SAVE_REGEX.matches(repaired)
    }

    private val TSUMASHIBORI_EN_ALIAS_REGEX =
        Regex("(?<![a-z0-9])ts?uma[\\s._-]*sh?ibori(?![a-z0-9])", RegexOption.IGNORE_CASE)
    private val TSUMASHIBORI_ALD_REGEX =
        Regex("^(?:妻しぼり|嵢偟傏傝|ts?umash?ibori)[a-z0-9_]*\\.ald$", RegexOption.IGNORE_CASE)
    private val TSUMAMIGUI3_EN_ALIAS_REGEX =
        Regex("(?<![a-z0-9])ts?uma[\\s._-]*migui[\\s._-]*(?:3|iii)(?![a-z0-9])", RegexOption.IGNORE_CASE)
    private val TSUMAMIGUI3_CJK_ALIAS_REGEX =
        Regex("(?:妻みぐい|ツマミグイ|つまみぐい|嵢傒偖偄|妻中蜜)[\\s_-]*[3３Ⅲ]")
    private val TSUMAMIGUI3_ASSET_REGEX =
        Regex("^ts?umamigui3(?:cg|flat|pact|sound|voice)\\.afa$", RegexOption.IGNORE_CASE)
    private val TSUMASHIBORI_SAVE_SLOT_REGEX =
        Regex("^(?:妻しぼり|嵢偟傏傝|ts?umash?ibori)(?:sys|\\d+)\\.asd$", RegexOption.IGNORE_CASE)

    fun isEscalayerRebootAlias(lowerText: String): Boolean {
        return lowerText.contains("escalayerreboot") ||
            lowerText.contains("escalayer reboot") ||
            lowerText.contains("escalayer_reboot") ||
            lowerText.contains("escalayer-reboot") ||
            lowerText.contains("escalayer・reboot") ||
            lowerText.contains("escalayerr") ||
            lowerText.contains("エスカレイヤー・リブート") ||
            lowerText.contains("エスカレイヤーリブート") ||
            lowerText.contains("エスカレイヤー リブート") ||
            lowerText.contains("リブート") ||
            ((lowerText.contains("超昂天使") || lowerText.contains("艾斯卡蕾雅") || lowerText.contains("escalayer")) &&
                (lowerText.contains("reboot") || lowerText.contains("重制") || lowerText.contains("重启"))) ||
            ESCALAYER_R_JA_ALIAS_REGEX.containsMatchIn(lowerText) ||
            ESCALAYER_R_CN_ALIAS_REGEX.containsMatchIn(lowerText) ||
            ESCALAYER_R_EN_ALIAS_REGEX.containsMatchIn(lowerText)
    }

    fun isIxsealAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        if (lower.contains("escalayer") || lower.contains("超昂天使") || lower.contains("エスカレイヤー")) {
            return false
        }
        return lower.contains("ixseal") ||
            lower.contains("エクシール") ||
            lower.contains("超昂神騎") ||
            lower.contains("超昂神骑") ||
            lower.contains("beat valkyrie")
    }

    @JvmOverloads
    fun isIxsealGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val sys4AinNames = fileNames.filter {
            it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true)
        }
        if (sys4AinNames.isEmpty()) return false
        if (sys4AinNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("Ixseal.ain", ignoreCase = true) ||
                    repaired.equals("Ixseal.ain", ignoreCase = true) ||
                    it.equals("エクシール.ain", ignoreCase = true) ||
                    repaired.equals("エクシール.ain", ignoreCase = true) ||
                    it.equals("超昂神騎エクシール.ain", ignoreCase = true) ||
                    repaired.equals("超昂神騎エクシール.ain", ignoreCase = true) ||
                    it.equals("超昂神骑.ain", ignoreCase = true) ||
                    repaired.equals("超昂神骑.ain", ignoreCase = true) ||
                    it.equals("超昂神騎.ain", ignoreCase = true) ||
                    repaired.equals("超昂神騎.ain", ignoreCase = true)
            }
        ) {
            return true
        }
        if (fileNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("IxsealCG.afa", ignoreCase = true) ||
                    repaired.equals("IxsealCG.afa", ignoreCase = true) ||
                    it.equals("IxsealFont.fnl", ignoreCase = true) ||
                    it.equals("IxsealVoice.afa", ignoreCase = true) ||
                    it.equals("IxsealFlat.afa", ignoreCase = true)
            }
        ) {
            return true
        }
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", ignoreCase = true) }
        if (iniName != null && readSmallFile != null) {
            try {
                val raw = readSmallFile(iniName)
                if (raw != null) {
                    val parsed = System40Ini.parse(raw)
                    val title = parsed.gameName?.trim().orEmpty()
                    if (title.isNotEmpty() && isIxsealAlias(title)) {
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun isIxsealGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isIxsealGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) {
                try { f.readBytes() } catch (_: Throwable) { null }
            } else {
                null
            }
        }
    }

    fun isHarukaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val norm = nfkcLower(raw)
        val rawLower = raw.lowercase()
        if (norm.contains("escalayer") || norm.contains("超昂天使") || norm.contains("エスカレイヤー") ||
            norm.contains("ixseal") || norm.contains("超昂神騎") || norm.contains("超昂神骑") ||
            norm.contains("エクシール") || norm.contains("beat angel") || norm.contains("beat valkyrie") ||
            norm.contains("harukavsesca") || norm.contains("haruka vs") || norm.contains("ハルカvs") || norm.contains("遥vs") ||
            norm.contains("harukaninja") || norm.contains("shippuu") || norm.contains("疾風") || norm.contains("疾风") ||
            norm.contains("忍者大作戦") || norm.contains("忍者大作战") || norm.contains("忍者大作戰")
        ) {
            return false
        }
        return HARUKA_EN_ALIAS_REGEX.containsMatchIn(lower) ||
            lower.contains("beatbladesharuka") ||
            lower.contains("choukousennin") ||
            lower.contains("超昂閃忍") ||
            lower.contains("超昂闪忍") ||
            lower.contains("閃忍ハルカ") ||
            lower.contains("闪忍遥") ||
            lower.contains("ハルカ") ||
            rawLower.contains("挻峍慚擡") ||
            rawLower.contains("僴儖僇")
    }

    @JvmOverloads
    fun isHarukaGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        if (isHarukaVsEscaGameFiles(fileNames, readSmallFile) || isHarukaNinjaGameFiles(fileNames, readSmallFile)) return false
        val sys4AinNames = fileNames.filter {
            it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true)
        }
        if (sys4AinNames.isEmpty()) return false
        if (sys4AinNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("超昂閃忍ハルカ.ain", ignoreCase = true) ||
                    repaired.equals("超昂閃忍ハルカ.ain", ignoreCase = true) ||
                    it.equals("超昂闪忍遥.ain", ignoreCase = true) ||
                    repaired.equals("超昂闪忍遥.ain", ignoreCase = true) ||
                    it.equals("超昂閃忍遥.ain", ignoreCase = true) ||
                    repaired.equals("超昂閃忍遥.ain", ignoreCase = true) ||
                    it.equals("ハルカ.ain", ignoreCase = true) ||
                    repaired.equals("ハルカ.ain", ignoreCase = true) ||
                    it.equals("Haruka.ain", ignoreCase = true) ||
                    repaired.equals("Haruka.ain", ignoreCase = true) ||
                    it.equals("BeatBladesHaruka.ain", ignoreCase = true) ||
                    it.equals("ChoukouSenninHaruka.ain", ignoreCase = true)
            }
        ) {
            return true
        }
        if (fileNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("HarukaDll.inc", ignoreCase = true) ||
                    repaired.equals("HarukaDll.inc", ignoreCase = true) ||
                    HARUKA_ALD_REGEX.matches(it) ||
                    HARUKA_ALD_REGEX.matches(repaired)
            }
        ) {
            return true
        }
        val iniName = fileNames.firstOrNull {
            it.equals("System40.ini", ignoreCase = true) || it.equals("AliceStart.ini", ignoreCase = true)
        }
        if (iniName != null && readSmallFile != null) {
            try {
                val raw = readSmallFile(iniName)
                if (raw != null) {
                    val title = System40Ini.parse(raw).gameName?.trim().orEmpty()
                    if (title.isNotEmpty() && isHarukaAlias(title)) {
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun isHarukaGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isHarukaGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) {
                try { f.readBytes() } catch (_: Throwable) { null }
            } else {
                null
            }
        }
    }

    fun isEvenicle2Alias(lowerText: String): Boolean {
        if (isEvenicle2RanceAlias(lowerText)) return false
        return lowerText.contains("evenicle2") ||
            lowerText.contains("evenicle 2") ||
            lowerText.contains("evenicle_2") ||
            lowerText.contains("evenicle ii") ||
            lowerText.contains("evenicleii") ||
            lowerText.contains("evenicle ⅱ") ||
            lowerText.contains("evenicleⅱ") ||
            lowerText.contains("イブニクル2") ||
            lowerText.contains("イブニクル２") ||
            lowerText.contains("イブニクル 2") ||
            lowerText.contains("イブニクル ２") ||
            lowerText.contains("イブニクルⅱ") ||
            lowerText.contains("夏娃年代记2") ||
            lowerText.contains("夏娃年代记 2") ||
            lowerText.contains("夏娃年代记ii") ||
            lowerText.contains("夏娃年代记ⅱ") ||
            lowerText.contains("夏娃年代記2") ||
            lowerText.contains("夏娃2")
    }

    fun isRance04GameFiles(fileNames: Collection<String>): Boolean =
        isSystem3xGameFiles(fileNames) && fileNames.any { it.equals("RANCE4SA.ALD", true) }

    fun isRance04GameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isRance04GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty())

    fun isRance04Alias(raw: String): Boolean {
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).lowercase()
        if (Regex("(?:rance|兰斯|ランス)\\s*0?(?:4|iv)\\s*[.・]\\s*[12]", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) return false
        return Regex("(?<![a-z0-9])(?:rance|兰斯|ランス)\\s*(?:0?4(?![0-9.]|\\s*[.・]\\s*[0-9])|iv(?![a-z0-9.]|\\s*[.・]\\s*[0-9]))", RegexOption.IGNORE_CASE).containsMatchIn(normalized) ||
            normalized.contains("教团的遗产") || normalized.contains("教団の遺産")
    }

    private val RANCE41_ADISK_CRC32S = setOf(0xa43fb4b6L, 0xc39ca9e6L)
    private val RANCE42_ADISK_CRC32S = setOf(0x04d24d1eL, 0x07e46c85L)

    private fun readFilePrefix(file: File, maxBytes: Int): ByteArray? = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(maxBytes)
            var off = 0
            while (off < maxBytes) {
                val n = input.read(buf, off, maxBytes - off)
                if (n < 0) break
                off += n
            }
            if (off == maxBytes) buf else buf.copyOf(off)
        }
    }.getOrNull()

    fun legacyAdiskCrc32(raw: ByteArray?): Long? {
        if (raw == null || raw.size < 256) return null
        val crc = java.util.zip.CRC32()
        crc.update(raw, 0, 256)
        return crc.value
    }

    @JvmOverloads
    fun isRance41GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any { it.equals("RAN41_SA.ALD", true) || it.equals("RANCE41SA.ALD", true) }) {
            return true
        }
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "rance41" || game == "rance41_eng" || game == "rance4.1") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isRance41Alias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in RANCE41_ADISK_CRC32S) return true
            if (crc != null && crc in RANCE42_ADISK_CRC32S) return false
        }
        return fileNames.any { it.equals("RANCE41.ICO", true) }
    }

    fun isRance41GameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isRance41GameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isRance41Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).lowercase()
        return Regex("(?<![a-z0-9])(?:rance|兰斯|ランス)\\s*(?:0?4|iv)\\s*[.・]?\\s*1(?![0-9])", RegexOption.IGNORE_CASE).containsMatchIn(normalized) ||
            normalized.contains("拯救制药厂") ||
            normalized.contains("お薬工場")
    }

    @JvmOverloads
    fun isRance42GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any { it.equals("RAN42_SA.ALD", true) || it.equals("RANCE42SA.ALD", true) }) {
            return true
        }
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "rance42" || game == "rance42_eng" || game == "rance4.2") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isRance42Alias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in RANCE42_ADISK_CRC32S) return true
        }
        return false
    }

    fun isRance42GameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isRance42GameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isRance42Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).lowercase()
        return Regex("(?<![a-z0-9])(?:rance|兰斯|ランス)\\s*(?:0?4|iv)\\s*[.・]?\\s*2(?![0-9])", RegexOption.IGNORE_CASE).containsMatchIn(normalized) ||
            normalized.contains("天使组") ||
            normalized.contains("天使組") ||
            normalized.contains("エンジェル組") ||
            normalized.contains("angelgumi") ||
            normalized.contains("angel army")
    }

    fun isRance5dGameFiles(fileNames: Collection<String>): Boolean =
        isSystem3xGameFiles(fileNames) && fileNames.any { it.equals("R5D_SA.ALD", true) }

    fun isRance5dGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isRance5dGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty())

    fun isRance5dAlias(raw: String): Boolean {
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).lowercase()
        return Regex("(?<![a-z0-9])(?:rance|兰斯|ランス)\\s*5d(?![a-z0-9])").containsMatchIn(normalized)
    }

    fun isRance06GameFiles(fileNames: Collection<String>): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        if (isRance6AfterGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) }
        if (ainNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("Rance6.ain", ignoreCase = true) ||
                    it.equals("Rance06.ain", ignoreCase = true) ||
                    it.equals("RanceVI.ain", ignoreCase = true) ||
                    it.equals("ランス6.ain", ignoreCase = true) ||
                    it.equals("ランス６.ain", ignoreCase = true) ||
                    it.equals("ランス06.ain", ignoreCase = true) ||
                    it.equals("ランス０６.ain", ignoreCase = true) ||
                    repaired.equals("Rance6.ain", ignoreCase = true) ||
                    repaired.equals("Rance06.ain", ignoreCase = true) ||
                    repaired.equals("ランス6.ain", ignoreCase = true) ||
                    repaired.equals("ランス６.ain", ignoreCase = true) ||
                    repaired.equals("ランス06.ain", ignoreCase = true) ||
                    repaired.equals("ランス０６.ain", ignoreCase = true) ||
                    it.contains("ゼス崩壊") ||
                    repaired.contains("ゼス崩壊") ||
                    it.contains("赛斯崩坏") ||
                    repaired.contains("赛斯崩坏")
            }
        ) {
            return true
        }
        return fileNames.any {
            val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
            if (it.contains("after", true) || it.contains("sonogo", true) ||
                it.contains("その後") || repaired.contains("その後")
            ) {
                return@any false
            }
            it.equals("Rance6DLL.inc", ignoreCase = true) ||
                RANCE06_ALD_REGEX.matches(it) ||
                RANCE06_ALD_REGEX.matches(repaired)
        }
    }

    fun isRance06Alias(raw: String): Boolean {
        if (raw.isBlank() || isRance6AfterAlias(raw)) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        return RANCE06_EN_ALIAS_REGEX.containsMatchIn(lower) ||
            RANCE06_CN_ALIAS_REGEX.containsMatchIn(lower) ||
            RANCE06_JP_ALIAS_REGEX.containsMatchIn(lower) ||
            Regex("(?:^|[^a-z0-9])r6汉硬(?:[0-9.]|$)").containsMatchIn(lower) ||
            lower.contains("ゼス崩壊") ||
            lower.contains("赛斯崩坏") ||
            lower.contains("賽斯崩壞") ||
            lower.contains("collapse of zeth")
    }

    fun isGalzooGameFiles(fileNames: Collection<String>): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) }
        val isGalzooAin = { rawAin: String ->
            val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(rawAin)
            rawAin.equals("GALZOO.ain", ignoreCase = true) ||
                repaired.equals("GALZOO.ain", ignoreCase = true) ||
                rawAin.equals("GALZOOIsland.ain", ignoreCase = true) ||
                repaired.equals("GALZOOIsland.ain", ignoreCase = true) ||
                rawAin.equals("GALZOO Island.ain", ignoreCase = true) ||
                rawAin.equals("GALZOO_Island.ain", ignoreCase = true) ||
                rawAin.equals("GALZOOアイランド.ain", ignoreCase = true) ||
                repaired.equals("GALZOOアイランド.ain", ignoreCase = true) ||
                rawAin.equals("ギャルズーアイランド.ain", ignoreCase = true) ||
                repaired.equals("ギャルズーアイランド.ain", ignoreCase = true)
        }
        if (ainNames.any(isGalzooAin)) {
            return true
        }
        val nonSpecialAins = ainNames.filter {
            !it.equals("System39.ain", ignoreCase = true) && !it.equals("AliceCN.ain", ignoreCase = true)
        }
        if (nonSpecialAins.isNotEmpty()) {
            return false
        }
        return fileNames.any {
            val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
            it.equals("GALZOODLL.inc", ignoreCase = true) ||
                GALZOO_ALD_REGEX.matches(it) ||
                GALZOO_ALD_REGEX.matches(repaired)
        }
    }

    fun isGalzooGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isGalzooGameFiles(files.map { it.name })
    }

    fun isGalzooAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        return GALZOO_EN_ALIAS_REGEX.containsMatchIn(lower) ||
            lower.contains("galzooisland") ||
            lower.contains("galzooアイランド") ||
            lower.contains("galzoo アイランド") ||
            lower.contains("ギャルズー") ||
            lower.contains("galzoo岛") ||
            lower.contains("galzoo 岛") ||
            lower.contains("galzoo艾兰德") ||
            lower.contains("galzoo 艾兰德")
    }

    fun isTsumashiboriAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val rawLower = raw.lowercase()
        val repairedLower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val check: (String) -> Boolean = { s ->
            TSUMASHIBORI_EN_ALIAS_REGEX.containsMatchIn(s) ||
                s.contains("妻しぼり") ||
                s.contains("ツマシボリ") ||
                s.contains("つましぼり") ||
                s.contains("嵢偟傏傝") ||
                s.contains("妻管严") ||
                s.contains("妻管嚴") ||
                s.contains("妻榨") ||
                s.contains("妻搾") ||
                s.contains("\u69a8\u7cbe\u59bb") ||
                s.contains("\u643e\u7cbe\u59bb")
        }
        return check(repairedLower) || check(rawLower)
    }

    @JvmOverloads
    fun isTsumashiboriGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) }
        val isTsumaAin = { rawAin: String ->
            val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(rawAin)
            rawAin.equals("妻しぼり.ain", ignoreCase = true) ||
                repaired.equals("妻しぼり.ain", ignoreCase = true) ||
                rawAin.equals("嵢偟傏傝.ain", ignoreCase = true) ||
                rawAin.equals("Tsumashibori.ain", ignoreCase = true) ||
                repaired.equals("Tsumashibori.ain", ignoreCase = true) ||
                rawAin.equals("Tsumasibori.ain", ignoreCase = true) ||
                rawAin.equals("Tumashibori.ain", ignoreCase = true) ||
                rawAin.equals("Tumasibori.ain", ignoreCase = true) ||
                rawAin.equals("ツマシボリ.ain", ignoreCase = true) ||
                repaired.equals("ツマシボリ.ain", ignoreCase = true) ||
                rawAin.equals("妻管严.ain", ignoreCase = true) ||
                repaired.equals("妻管严.ain", ignoreCase = true)
        }
        if (ainNames.any(isTsumaAin)) {
            return true
        }
        if (fileNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                TSUMASHIBORI_ALD_REGEX.matches(it) ||
                    TSUMASHIBORI_ALD_REGEX.matches(repaired) ||
                    it.equals("妻しぼりＯＰ.mpg", ignoreCase = true) ||
                    repaired.equals("妻しぼりＯＰ.mpg", ignoreCase = true) ||
                    it.equals("嵢偟傏傝俷俹.mpg", ignoreCase = true)
            }
        ) {
            val nonSpecialAins = ainNames.filter {
                !it.equals("System39.ain", ignoreCase = true) && !it.equals("AliceCN.ain", ignoreCase = true)
            }
            if (nonSpecialAins.isEmpty()) {
                return true
            }
        }
        val sys4Ains = ainNames.filter { !it.equals("System39.ain", ignoreCase = true) }
        val iniName = fileNames.firstOrNull {
            it.equals("System40.ini", ignoreCase = true) || it.equals("AliceStart.ini", ignoreCase = true)
        }
        if (sys4Ains.isNotEmpty() && iniName != null && readSmallFile != null) {
            try {
                val raw = readSmallFile(iniName)
                if (raw != null) {
                    val title = System40Ini.parse(raw).gameName?.trim().orEmpty()
                    if (title.isNotEmpty() && isTsumashiboriAlias(title)) {
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun isTsumashiboriGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isTsumashiboriGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) {
                try { f.readBytes() } catch (_: Throwable) { null }
            } else {
                null
            }
        }
    }

    fun isTsumamigui3Alias(raw: String): Boolean {
        if (raw.isBlank() || isTsumamigui3JunAlias(raw) || isTsumamigui3NtrAlias(raw)) return false
        val rawLower = raw.lowercase()
        val repairedLower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val check: (String) -> Boolean = { s ->
            TSUMAMIGUI3_EN_ALIAS_REGEX.containsMatchIn(s) || TSUMAMIGUI3_CJK_ALIAS_REGEX.containsMatchIn(s)
        }
        return check(repairedLower) || check(rawLower)
    }

    @JvmOverloads
    fun isTsumamigui3GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) }
        if (ainNames.any { it.equals("Tsumamigui3.ain", ignoreCase = true) || it.equals("Tumamigui3.ain", ignoreCase = true) }) {
            return true
        }
        if (fileNames.any { TSUMAMIGUI3_ASSET_REGEX.matches(it) }) {
            val otherAins = ainNames.filter { !it.equals("System39.ain", ignoreCase = true) }
            if (otherAins.isEmpty()) return true
        }
        val iniName = fileNames.firstOrNull {
            it.equals("System40.ini", ignoreCase = true) || it.equals("AliceStart.ini", ignoreCase = true)
        }
        if (ainNames.isNotEmpty() && iniName != null && readSmallFile != null) {
            try {
                val raw = readSmallFile(iniName)
                if (raw != null) {
                    val title = System40Ini.parse(raw).gameName?.trim().orEmpty()
                    if (title.isNotEmpty() && isTsumamigui3Alias(title)) {
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun isTsumamigui3GameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isTsumamigui3GameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) {
                try { f.readBytes() } catch (_: Throwable) { null }
            } else {
                null
            }
        }
    }

    internal fun readSmallRootFile(dir: File, name: String): ByteArray? {
        val f = dir.listFiles()?.firstOrNull { it.isFile && it.name.equals(name, true) } ?: return null
        return if (f.length() <= 256L * 1024L) runCatching { f.readBytes() }.getOrNull() else null
    }

    fun isDaibanchouAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val norm = nfkcLower(raw)
        if (norm.contains("gaiden") || norm.contains("外伝") || norm.contains("外传") || norm.contains("外傳")) {
            return false
        }
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return lower.contains("daibanchou") || lower.contains("daibancho") ||
            lower.contains("big bang age") || lower.contains("bigbangage") ||
            lower.contains("大番長") || lower.contains("大番长") ||
            rawLower.contains("戝斣挿")
    }

    @JvmOverloads
    fun isDaibanchouGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("大番長.ain", true) || r.equals("大番長.ain", true) ||
                    it.equals("大番长.ain", true) || r.equals("大番长.ain", true) ||
                    it.equals("Daibanchou.ain", true) || it.equals("BigBangAge.ain", true)
            } || fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("大番長GA.ald", true) || r.equals("大番長GA.ald", true) ||
                    it.equals("大番長OP.alk", true) || r.equals("大番長OP.alk", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System40.ini", true) || it.equals("AliceStart.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDaibanchouAlias(title)) return true
        }
        return false
    }

    fun isDaibanchouGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDaibanchouGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isDaiteikokuAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return lower.contains("daiteikoku") ||
            lower.contains("大帝国") || lower.contains("大帝國") ||
            rawLower.contains("戝掗崙")
    }

    @JvmOverloads
    fun isDaiteikokuGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("大帝国.ain", true) || r.equals("大帝国.ain", true) ||
                    it.equals("大帝國.ain", true) || r.equals("大帝國.ain", true) ||
                    it.equals("Daiteikoku.ain", true)
            } || fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("大帝国CG.afa", true) || r.equals("大帝国CG.afa", true) ||
                    it.equals("大帝国AFF.afa", true) || r.equals("大帝国AFF.afa", true) ||
                    it.equals("DaiteikokuCG.afa", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDaiteikokuAlias(title)) return true
        }
        return false
    }

    fun isDaiteikokuGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDaiteikokuGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isTsumamiguiAlias(raw: String): Boolean {
        if (raw.isBlank() || isTsumamigui3JunAlias(raw) || isTsumamigui3NtrAlias(raw) ||
            isTsumamigui3Alias(raw) || isTsumamigui2Alias(raw)
        ) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        if (Regex("(?:ts?umamigui|妻みぐい|妻中蜜)[\\s._-]*(?:[23２３]|ii|iii)", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return false
        }
        return Regex("(?<![a-z0-9])ts?uma[\\s._-]*migui(?![\\s._-]*[0-9]|ii)", RegexOption.IGNORE_CASE).containsMatchIn(lower) ||
            lower.contains("妻みぐい") || lower.contains("妻中蜜") || rawLower.contains("嵢傒偖偄")
    }

    @JvmOverloads
    fun isTsumamiguiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("妻みぐいSA.ald", true) || r.equals("妻みぐいSA.ald", true) ||
                    it.equals("妻みぐいGA.ald", true) || r.equals("妻みぐいGA.ald", true) ||
                    it.equals("嵢傒偖偄SA.ald", true) || it.equals("嵢傒偖偄GA.ald", true) ||
                    it.equals("TsumamiguiSA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isTsumamiguiAlias(title)) return true
        }
        return false
    }

    fun isTsumamiguiGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isTsumamiguiGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isKaerunyoAlias(raw: String): Boolean {
        if (raw.isBlank() || isKaeruAliceAlias(raw)) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return lower.contains("kaerunyo") || lower.contains("kaeru nyo") ||
            lower.contains("panyon") || lower.contains("panyoon") ||
            lower.contains("かえるにょ") || lower.contains("ぱにょ") ||
            lower.contains("回家扭") || rawLower.contains("偐偊傞偵傜") || rawLower.contains("傺偵傜")
    }

    @JvmOverloads
    fun isKaerunyoGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("ぱにょSA.ALD", true) || r.equals("ぱにょSA.ALD", true) ||
                    it.equals("ぱにょGA.ALD", true) || r.equals("ぱにょGA.ALD", true) ||
                    it.equals("KaerunyoSA.ALD", true)
            }
        ) return true
        if (fileNames.any { it.equals("FLOOR_1.MAP", true) } && fileNames.any { it.equals("FLOOR_33.MAP", true) }) {
            return true
        }
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isKaerunyoAlias(title)) return true
        }
        return false
    }

    fun isKaerunyoGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isKaerunyoGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isAtlachnachaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        return lower.contains("atlach") || lower.contains("アトラク") ||
            lower.contains("阿特拉克")
    }

    @JvmOverloads
    fun isAtlachnachaGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("蜘蛛SA.ALD", true) || r.equals("蜘蛛SA.ALD", true) ||
                    it.equals("蜘蛛SA_CHS.ALD", true) || r.equals("蜘蛛SA_CHS.ALD", true) ||
                    it.equals("蜘蛛GA.ALD", true) || r.equals("蜘蛛GA.ALD", true) ||
                    it.equals("蜘蛛GA_CHS.ALD", true) || r.equals("蜘蛛GA_CHS.ALD", true) ||
                    it.equals("AtlachSA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isAtlachnachaAlias(title)) return true
        }
        return false
    }

    fun isAtlachnachaGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isAtlachnachaGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isReishikiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return lower.contains("reishiki") || lower.contains("zero shiki") ||
            lower.contains("zeroshiki") || lower.contains("type_zero") ||
            lower.contains("零式") || rawLower.contains("楇幃")
    }

    @JvmOverloads
    fun isReishikiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("零式SA.ALD", true) || r.equals("零式SA.ALD", true) ||
                    it.equals("零式GA.ALD", true) || r.equals("零式GA.ALD", true) ||
                    it.equals("楇幃SA.ALD", true) || it.equals("楇幃GA.ALD", true) ||
                    it.equals("ReishikiSA.ALD", true)
            }
        ) return true
        if (fileNames.any { it.equals("MAP_DATA.DAT", true) } && fileNames.any { it.equals("UR_DATA.DAT", true) }) {
            return true
        }
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isReishikiAlias(title)) return true
        }
        return false
    }

    fun isReishikiGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isReishikiGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isHaruurareAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return lower.contains("haruurare") || lower.contains("haru urare") ||
            lower.contains("はるうられ") || lower.contains("校内赤线") ||
            lower.contains("校内赤線") || rawLower.contains("偼傞偆傜傟")
    }

    @JvmOverloads
    fun isHaruurareGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("haru_c.ain", true) ||
                    it.equals("はるうられ.ain", true) || r.equals("はるうられ.ain", true) ||
                    it.equals("Haruurare.ain", true)
            } || fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("はるうられGA.ald", true) || r.equals("はるうられGA.ald", true)
            }
        ) return true
        for (iniCandidate in listOf("AliceStart2.ini", "AliceStart.ini", "System40.ini")) {
            val iniName = fileNames.firstOrNull { it.equals(iniCandidate, true) } ?: continue
            if (readSmallFile != null) {
                val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
                if (title.isNotEmpty() && isHaruurareAlias(title)) return true
            }
        }
        return false
    }

    fun isHaruurareGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isHaruurareGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isOkusamaJunAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        if (lower.contains("ntr") || lower.contains("夺爱") || lower.contains("奪愛")) return false
        return lower.contains("okusamajun") ||
            lower.contains("okusama_jun") ||
            lower.contains("okusama jun") ||
            lower.contains("okusama-jun") ||
            ((lower.contains("okusama") || lower.contains("太太的恢复术") ||
                lower.contains("太太的恢復術") || lower.contains("奥さまの回復術") || lower.contains("奥さま")) &&
                (lower.contains("jun") || lower.contains("纯爱") || lower.contains("純愛") ||
                    lower.contains("pure love") || lower.contains("purelove")))
    }

    @JvmOverloads
    fun isOkusamaJunGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                it.equals("okusama_jun_cn.ain", true) ||
                    it.equals("okusama_jun.ain", true) ||
                    it.equals("okusamajun.ain", true)
            } || fileNames.any {
                it.equals("okusama_jun_cnCG.afa", true) ||
                    it.equals("okusama_junCG.afa", true) ||
                    it.equals("okusama_jun_cnPact.afa", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isOkusamaJunAlias(title)) return true
        }
        return false
    }

    fun isOkusamaJunGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isOkusamaJunGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isOkusamaNtrAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        if (lower.contains("okusamajun") || lower.contains("okusama_jun") ||
            lower.contains("纯爱") || lower.contains("純愛") || lower.contains("pure love")
        ) return false
        return lower.contains("okusamantr") ||
            lower.contains("okusama_ntr") ||
            lower.contains("okusama ntr") ||
            lower.contains("okusama-ntr") ||
            ((lower.contains("okusama") || lower.contains("太太的恢复术") ||
                lower.contains("太太的恢復術") || lower.contains("奥さまの回復術") || lower.contains("奥さま")) &&
                (lower.contains("ntr") || lower.contains("夺爱") || lower.contains("奪愛")))
    }

    @JvmOverloads
    fun isOkusamaNtrGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                it.equals("okusama_ntr_cn.ain", true) ||
                    it.equals("okusama_ntr.ain", true) ||
                    it.equals("okusamantr.ain", true)
            } || fileNames.any {
                it.equals("okusama_ntr_cnCG.afa", true) ||
                    it.equals("okusama_ntrCG.afa", true) ||
                    it.equals("okusama_ntr_cnPact.afa", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isOkusamaNtrAlias(title)) return true
        }
        return false
    }

    fun isOkusamaNtrGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isOkusamaNtrGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isEvenicle2RanceAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        val check: (String) -> Boolean = { s ->
            s.contains("evenicle2rance") ||
                s.contains("evenicle2_rance") ||
                s.contains("evenicle2-rance") ||
                s.contains("evenicle2 rance") ||
                s.contains("evenicle 2 rance") ||
                s.contains("イブニクル2ランス") ||
                s.contains("イブニクル２ランス") ||
                s.contains("イブニクル2 ランス") ||
                s.contains("イブニクル２ ランス") ||
                s.contains("夏娃年代记2 兰斯") ||
                s.contains("夏娃年代记2兰斯") ||
                s.contains("夏娃年代記2蘭斯") ||
                s.contains("夏娃年代記2 蘭斯") ||
                s.contains("夏娃2兰斯") ||
                s.contains("夏娃2 兰斯") ||
                ((s.contains("夏娃年代记2") || s.contains("夏娃年代記2") || s.contains("夏娃2")) &&
                    (s.contains("兰斯") || s.contains("蘭斯") || s.contains("rance"))) ||
                (s.contains("イブニクル") && (s.contains("2") || s.contains("２")) && s.contains("ランス"))
        }
        return check(lower) || check(rawLower) || rawLower.contains("僀僽僯僋儖俀儔儞僗")
    }

    @JvmOverloads
    fun isEvenicle2RanceGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any { it.equals("Evenicle2Rance.ain", true) } ||
            fileNames.any {
                it.equals("Evenicle2RanceCG.afa", true) ||
                    it.equals("Evenicle2RanceCHS.afa", true) ||
                    it.equals("Evenicle2RanceCHS2.afa", true) ||
                    it.equals("Evenicle2RancePact.afa", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isEvenicle2RanceAlias(title)) return true
        }
        return false
    }

    fun isEvenicle2RanceGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isEvenicle2RanceGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isEvenicleGaidenAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return lower.contains("eveniclegaiden") ||
            lower.contains("evenicle_gaiden") ||
            lower.contains("evenicle gaiden") ||
            lower.contains("eveniclegaiten") ||
            lower.contains("evenicle gaiten") ||
            lower.contains("evenicleapc") ||
            lower.contains("evenicle_apc") ||
            lower.contains("イブニクル外典") ||
            lower.contains("夏娃年代记外典") ||
            lower.contains("夏娃年代記外典") ||
            lower.contains("夏娃外典") ||
            rawLower.contains("僀僽僯僋儖奜揟")
    }

    @JvmOverloads
    fun isEvenicleGaidenGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                it.equals("EvenicleApc.ain", true) ||
                    it.equals("EvenicleGaiden.ain", true) ||
                    it.equals("EvenicleGaiten.ain", true)
            } || fileNames.any {
                it.equals("EvenicleApcCG.afa", true) ||
                    it.equals("EvenicleApcPact.afa", true) ||
                    it.equals("EvenicleApcFlat.afa", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isEvenicleGaidenAlias(title)) return true
        }
        return false
    }

    fun isEvenicleGaidenGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isEvenicleGaidenGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isWaidonyoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        return lower.contains("waidonyo") ||
            lower.contains("わいどにょ") ||
            lower.contains("异空窟") ||
            lower.contains("異空窟") ||
            rawLower.contains("傢偄偳偵傚")
    }

    @JvmOverloads
    fun isWaidonyoGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("waidonyo.ain", true) ||
                    it.equals("わいどにょ.ain", true) || r.equals("わいどにょ.ain", true) ||
                    it.equals("傢偄偳偵傚.ain", true)
            } || fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("waidonyoDLL.inc", true) ||
                    it.equals("waidonyoGA.ald", true) ||
                    it.equals("waidonyoGZ.ald", true) ||
                    it.equals("わいどにょGA.ald", true) || r.equals("わいどにょGA.ald", true) ||
                    it.equals("わいどにょGZ.ald", true) || r.equals("わいどにょGZ.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isWaidonyoAlias(title)) return true
        }
        return false
    }

    fun isWaidonyoGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isWaidonyoGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isRance6AfterAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        val normalized = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFKC)
        return normalized.contains("rance6after") ||
            normalized.contains("rance6_after") ||
            normalized.contains("rance6 after") ||
            normalized.contains("rance06after") ||
            normalized.contains("rance06 after") ||
            normalized.contains("rance vi aftermath") ||
            normalized.contains("rance vi: aftermath") ||
            normalized.contains("rance vi sonogo") ||
            normalized.contains("rance 6 aftermath") ||
            normalized.contains("rance 6 sonogo") ||
            normalized.contains("rance6sonogo") ||
            normalized.contains("ランス6その後") ||
            normalized.contains("ランス06その後") ||
            normalized.contains("ランス6 その後") ||
            normalized.contains("兰斯06 后日谈") ||
            normalized.contains("兰斯06后日谈") ||
            normalized.contains("兰斯6 后日谈") ||
            normalized.contains("兰斯6后日谈") ||
            normalized.contains("兰斯vi后日谈") ||
            normalized.contains("蘭斯06後日談") ||
            normalized.contains("蘭斯6後日談") ||
            rawLower.contains("儔儞僗俇偦偺屻") ||
            rawLower.contains("儔儞僗6偦偺屻")
    }

    @JvmOverloads
    fun isRance6AfterGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("rance6after.ain", true) ||
                    it.equals("ランス６その後.ain", true) || r.equals("ランス６その後.ain", true) ||
                    it.equals("ランス6その後.ain", true) || r.equals("ランス6その後.ain", true) ||
                    it.equals("儔儞僗俇偦偺屻.ain", true)
            } || fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("rance6afterDLL.inc", true) ||
                    it.equals("rance6afterGA.ald", true) ||
                    it.equals("ランス６その後GA.ald", true) || r.equals("ランス６その後GA.ald", true) ||
                    it.equals("ランス6その後GA.ald", true) || r.equals("ランス6その後GA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System40.ini", true) || it.equals("AliceStart.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isRance6AfterAlias(title)) return true
        }
        return false
    }

    fun isRance6AfterGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isRance6AfterGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    private val YAKATA2_ADISK_CRC32S = setOf(0x45951cabL)
    private val SDPSMARIA_ADISK_CRC32S = setOf(0x9418eef5L)

    fun isYakata2Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("yakata2") ||
            normalized.contains("yakata 2") ||
            normalized.contains("yakata_2") ||
            normalized.contains("alice no yakata ii") ||
            normalized.contains("alice no yakata 2") ||
            normalized.contains("aliceの館ii") ||
            normalized.contains("aliceの館2") ||
            normalized.contains("爱丽丝之馆2") ||
            normalized.contains("爱丽丝之馆ii") ||
            normalized.contains("愛麗絲之館2") ||
            normalized.contains("愛麗絲之館ii") ||
            normalized.contains("揍揍卷卷塔") ||
            normalized.contains("ぽんぽんぺんぺん")
    }

    @JvmOverloads
    fun isYakata2GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "yakata2" || game == "yakata2_eng") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isYakata2Alias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in YAKATA2_ADISK_CRC32S) return true
        }
        return false
    }

    fun isYakata2GameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isYakata2GameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isSdpsMariaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("sdpsmaria") ||
            normalized.contains("sdps_maria") ||
            normalized.contains("sdps maria") ||
            normalized.contains("superdpsmaria") ||
            normalized.contains("super dps") ||
            normalized.contains("superdps") ||
            normalized.contains("super d.p.s") ||
            normalized.contains("玛利亚和坎潘") ||
            normalized.contains("瑪利亞和坎潘") ||
            normalized.contains("玛丽亚和坎潘") ||
            normalized.contains("マリアとカンパン") ||
            normalized.contains("maria & kanpan") ||
            normalized.contains("maria and kanpan")
    }

    @JvmOverloads
    fun isSdpsMariaGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "sdps_maria" || game == "sdps_maria_eng" || game == "sdps" || game == "superdps") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isSdpsMariaAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in SDPSMARIA_ADISK_CRC32S) return true
        }
        return false
    }

    fun isSdpsMariaGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isSdpsMariaGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isToushin3Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        return normalized.contains("toushin3") ||
            normalized.contains("toushin 3") ||
            normalized.contains("toushin_3") ||
            normalized.contains("toushin toshi iii") ||
            normalized.contains("toushin toshi 3") ||
            normalized.contains("toushintoshi3") ||
            normalized.contains("toushintoshiiii") ||
            normalized.contains("闘神都市3") ||
            normalized.contains("闘神都市iii") ||
            normalized.contains("斗神都市3") ||
            normalized.contains("斗神都市iii") ||
            normalized.contains("鬥神都市3") ||
            normalized.contains("鬥神都市iii") ||
            rawLower.contains("摤恄搒巗3") ||
            rawLower.contains("摤恄搒巗iii") ||
            rawLower.contains("摤恄搒巗俁")
    }

    @JvmOverloads
    fun isToushin3GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("toushin3.ain", true) ||
                    it.equals("斗神都市3.ain", true) ||
                    it.equals("闘神都市３.ain", true) || r.equals("闘神都市３.ain", true) ||
                    it.equals("闘神都市3.ain", true) || r.equals("闘神都市3.ain", true)
            } || fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("闘神都市３DLL.inc", true) || r.equals("闘神都市３DLL.inc", true) ||
                    it.equals("闘神都市３GM.ald", true) || r.equals("闘神都市３GM.ald", true) ||
                    it.equals("闘神都市３GA.ald", true) || r.equals("闘神都市３GA.ald", true) ||
                    it.equals("Toushin3Map.dat", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System40.ini", true) || it.equals("AliceStart.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isToushin3Alias(title)) return true
        }
        return false
    }

    fun isToushin3GameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isToushin3GameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isOyakorankanAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        return normalized.contains("oyakorankan") ||
            normalized.contains("oyako rankan") ||
            normalized.contains("oyako_rankan") ||
            normalized.contains("母娘乱館") ||
            normalized.contains("母娘乱馆") ||
            normalized.contains("母娘亂館") ||
            rawLower.contains("曣枅棆娰")
    }

    @JvmOverloads
    fun isOyakorankanGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val codeNames = fileNames.filter { isSystem4CodeFile(it, fileNames, readSmallFile) }
        if (codeNames.isEmpty()) return false
        if (codeNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("oyakorankan.ain", true) ||
                    it.equals("母娘乱館.CHS", true) || r.equals("母娘乱館.CHS", true) ||
                    it.equals("母娘乱館.ain", true) || r.equals("母娘乱館.ain", true) ||
                    it.equals("母娘乱馆.CHS", true) || it.equals("母娘乱馆.ain", true)
            } || fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("母娘乱館CG.afa", true) || r.equals("母娘乱館CG.afa", true) ||
                    it.equals("母娘乱館BA.ald", true) || r.equals("母娘乱館BA.ald", true) ||
                    it.equals("母娘乱館WA.ald", true) || r.equals("母娘乱館WA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull {
            it.equals("AliceStart.CHS", true) || it.equals("AliceStart.ini", true) || it.equals("System40.ini", true)
        }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isOyakorankanAlias(title)) return true
        }
        return false
    }

    fun isOyakorankanGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isOyakorankanGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isYorugakuruAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        return normalized.contains("yorugakuru") ||
            normalized.contains("yoru ga kuru") ||
            normalized.contains("yoru_ga_kuru") ||
            normalized.contains("nightremaster") ||
            normalized.contains("night remaster") ||
            normalized.contains("square of the moon") ||
            normalized.contains("夜が来る") ||
            normalized.contains("邪夜将至") ||
            normalized.contains("邪夜將至") ||
            rawLower.contains("枌偑棃傞")
    }

    @JvmOverloads
    fun isYorugakuruGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any {
                it.equals("nightRemaster.ain", true) ||
                    it.equals("yorugakuru.ain", true)
            } || fileNames.any {
                it.equals("NightRemasterCG.afa", true) ||
                    it.equals("NightRemasterPact.afa", true) ||
                    it.equals("NightRemasterDict.dat", true) ||
                    it.equals("NightRemasterEX.ex", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isYorugakuruAlias(title)) return true
        }
        return false
    }

    fun isYorugakuruGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isYorugakuruGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isRance1DigestAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("rance1digest") ||
            normalized.contains("rance1_digest") ||
            normalized.contains("rance 1 digest") ||
            normalized.contains("rance1 digest") ||
            normalized.contains("rance01digest") ||
            normalized.contains("rance -光をもとめて- digest") ||
            normalized.contains("rance -hikari o motomete- digest") ||
            normalized.contains("兰斯1摘要") ||
            normalized.contains("兰斯1 摘要") ||
            normalized.contains("兰斯01摘要") ||
            normalized.contains("蘭斯1摘要") ||
            normalized.contains("ランス1ダイジェスト") ||
            normalized.contains("ランス1 ダイジェスト") ||
            normalized.contains("光をもとめて- digest") ||
            (normalized.contains("光をもとめて") && (normalized.contains("digest") || normalized.contains("ダイジェスト") || normalized.contains("摘要")))
    }

    @JvmOverloads
    fun isRance1DigestGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any { it.equals("Rance1.ain", true) } ||
            fileNames.any {
                it.equals("Rance1_DLL.inc", true) ||
                    it.equals("Rance1GA.ald", true) ||
                    it.equals("RANCE1BA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System40.ini", true) || it.equals("AliceStart.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isRance1DigestAlias(title)) return true
        }
        return false
    }

    fun isRance1DigestGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isRance1DigestGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isMamanAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        return normalized == "maman" ||
            normalized.startsWith("maman-") ||
            normalized.startsWith("maman_") ||
            normalized.contains("heartful maman") ||
            normalized.contains("heartfulmaman") ||
            normalized.contains("は～とふるママン") ||
            normalized.contains("は〜とふるママン") ||
            normalized.contains("はーとふるママン") ||
            normalized.contains("は~とふるママン") ||
            normalized.contains("再婚妈妈") ||
            normalized.contains("再婚媽媽") ||
            rawLower.contains("偼乣偲傇傞儅儅儞")
    }

    @JvmOverloads
    fun isMamanGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any { it.equals("Maman.ain", true) } ||
            fileNames.any {
                it.equals("MamanCG.afa", true) ||
                    it.equals("MamanFlat.afa", true) ||
                    it.equals("MamanPact.afa", true) ||
                    it.equals("MamanFont.fnl", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMamanAlias(title)) return true
        }
        return false
    }

    fun isMamanGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isMamanGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isEvenicleRanceAlias(raw: String): Boolean {
        if (raw.isBlank() || isEvenicle2RanceAlias(raw)) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(raw).lowercase()
        val rawLower = raw.lowercase()
        val check: (String) -> Boolean = { s ->
            s.contains("eveniclerance") ||
                s.contains("evenicle_rance") ||
                s.contains("evenicle-rance") ||
                s.contains("evenicle rance") ||
                s.contains("イブニクルランス") ||
                s.contains("イブニクル・ランス") ||
                s.contains("イブニクル ランス") ||
                s.contains("夏娃年代记兰斯") ||
                s.contains("夏娃年代记 兰斯") ||
                s.contains("夏娃年代記蘭斯") ||
                s.contains("夏娃年代記 蘭斯") ||
                s.contains("夏娃兰斯") ||
                ((s.contains("夏娃年代记") || s.contains("夏娃年代記") || s.contains("イブニクル")) &&
                    !s.contains("2") && !s.contains("２") &&
                    (s.contains("兰斯") || s.contains("蘭斯") || s.contains("ランス") || s.contains("rance")))
        }
        return check(lower) || check(rawLower) || rawLower.contains("僀僽僯僋儖儔儞僗")
    }

    @JvmOverloads
    fun isEvenicleRanceGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any { it.equals("EvenicleRance.ain", true) } ||
            fileNames.any {
                it.equals("EvenicleRanceCG.afa", true) ||
                    it.equals("EvenicleRanceFlat.afa", true) ||
                    it.equals("EvenicleRancePact.afa", true) ||
                    it.equals("EvenicleRance.ex", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isEvenicleRanceAlias(title)) return true
        }
        return false
    }

    fun isEvenicleRanceGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isEvenicleRanceGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isHaharanmanAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        return normalized == "haharanman" ||
            normalized.startsWith("haharanman-") ||
            normalized.startsWith("haharanman_") ||
            normalized.contains("haharanman") ||
            normalized.contains("haha ranman") ||
            normalized.contains("母爛漫") ||
            normalized.contains("母烂漫") ||
            rawLower.contains("曣椆枤")
    }

    @JvmOverloads
    fun isHaharanmanGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any { it.equals("Haharanman.ain", true) } ||
            fileNames.any {
                it.equals("HaharanmanCG.afa", true) ||
                    it.equals("HaharanmanFlat.afa", true) ||
                    it.equals("HaharanmanPact.afa", true) ||
                    it.equals("HaharanmanFont.fnl", true) ||
                    it.equals("HaharanmanEX.ex", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isHaharanmanAlias(title)) return true
        }
        return false
    }

    fun isHaharanmanGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isHaharanmanGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isTsumamigui2Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        return Regex("(?:ts?uma[\\s._-]*migui|妻みぐい|妻中蜜)[\\s._-]*(?:2|ii)(?![0-9a-z])", RegexOption.IGNORE_CASE)
            .containsMatchIn(normalized) ||
            rawLower.contains("嵢傒偖偄俀") ||
            rawLower.contains("嵢傒偖偄2")
    }

    @JvmOverloads
    fun isTsumamigui2GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                val n = java.text.Normalizer.normalize(r, java.text.Normalizer.Form.NFKC)
                n.equals("妻みぐい2SA.ald", true) || n.equals("妻みぐい2GA.ald", true) ||
                    it.equals("妻みぐい２SA.ald", true) || it.equals("妻みぐい２GA.ald", true) ||
                    it.equals("嵢傒偖偄俀SA.ald", true) || it.equals("嵢傒偖偄俀GA.ald", true) ||
                    it.equals("Tsumamigui2SA.ald", true) || it.equals("Tsumamigui2GA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isTsumamigui2Alias(title)) return true
        }
        return false
    }

    fun isTsumamigui2GameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isTsumamigui2GameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isTsumamigui3JunAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        if (normalized.contains("ntr") || normalized.contains("netorare") ||
            normalized.contains("寝取") || normalized.contains("寢取")
        ) return false
        val hasT3 = normalized.contains("tsuma3so") ||
            normalized.contains("tsumamigui3") ||
            normalized.contains("tumamigui3") ||
            normalized.contains("妻みぐい3") ||
            normalized.contains("妻中蜜3")
        return normalized.contains("tsumamigui3jun") ||
            normalized.contains("tsumamigui3_jun") ||
            normalized.contains("tsuma3so_junai") ||
            normalized.contains("tsuma3sojunai") ||
            (hasT3 && (normalized.contains("junai") || normalized.contains("jun") ||
                normalized.contains("纯爱") || normalized.contains("純愛") ||
                normalized.contains("pure love") || normalized.contains("purelove")))
    }

    @JvmOverloads
    fun isTsumamigui3JunGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any { it.equals("Tsuma3SO_Junai.ain", true) || it.equals("Tsumamigui3Jun.ain", true) } ||
            fileNames.any {
                it.equals("Tsuma3SO_JunaiCG.afa", true) ||
                    it.equals("Tsuma3SO_JunaiFlat.afa", true) ||
                    it.equals("Tsuma3SO_JunaiPact.afa", true) ||
                    it.equals("Tsuma3SO_JunaiFont.fnl", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isTsumamigui3JunAlias(title)) return true
        }
        return false
    }

    fun isTsumamigui3JunGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isTsumamigui3JunGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isTsumamigui3NtrAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        if (normalized.contains("junai") || normalized.contains("纯爱") || normalized.contains("純愛")) return false
        val hasT3 = normalized.contains("tsuma3so") ||
            normalized.contains("tsumamigui3") ||
            normalized.contains("tumamigui3") ||
            normalized.contains("妻みぐい3") ||
            normalized.contains("妻中蜜3")
        return normalized.contains("tsumamigui3ntr") ||
            normalized.contains("tsumamigui3_ntr") ||
            normalized.contains("tsuma3so_netorare") ||
            normalized.contains("tsuma3sonetorare") ||
            (hasT3 && (normalized.contains("netorare") || normalized.contains("ntr") ||
                normalized.contains("寝取") || normalized.contains("寢取")))
    }

    @JvmOverloads
    fun isTsumamigui3NtrGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (isSystem3xGameFiles(fileNames)) return false
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) && !it.equals("System39.ain", ignoreCase = true) }
        if (ainNames.isEmpty()) return false
        if (ainNames.any { it.equals("Tsuma3SO_Netorare.ain", true) || it.equals("Tsumamigui3Ntr.ain", true) } ||
            fileNames.any {
                it.equals("Tsuma3SO_NetorareCG.afa", true) ||
                    it.equals("Tsuma3SO_NetorareFlat.afa", true) ||
                    it.equals("Tsuma3SO_NetorarePact.afa", true) ||
                    it.equals("Tsuma3SO_NetorareFont.fnl", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("AliceStart.ini", true) || it.equals("System40.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isTsumamigui3NtrAlias(title)) return true
        }
        return false
    }

    fun isTsumamigui3NtrGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isTsumamigui3NtrGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    private val GAKUENKING_ADISK_CRC32S = setOf(0xd1bf243bL)
    private val ONLYYOU_ADISK_CRC32S = setOf(0x832aeb97L)
    private val AYUMI_ADISK_CRC32S = setOf(0xd2bed9eeL, 0x4e2fed2aL, 0x4e2f5678L)
    private val AYUMILIVE_ADISK_CRC32S = setOf(0x00d15a2bL, 0x5f66ff1dL)
    private val AYUMIHINT_ADISK_CRC32S = setOf(0xf6bd963aL)
    private val YAKATA1_ADISK_CRC32S = setOf(0x8cef6fa6L, 0xd8f84e34L)
    private val LITTLEVAMPIRE_ADISK_CRC32S = setOf(0x957bcfbfL)
    private val INTRUDER_ADISK_CRC32S = setOf(0xa7520fb2L)
    private val CRESCENT_ADISK_CRC32S = setOf(0x42351f2cL)
    private val TENGU_ADISK_CRC32S = setOf(0xc942ff58L)
    private val DPS_ADISK_CRC32S = setOf(0x69ea4865L)
    private val DPSSG1_BASE_ADISK_CRC32S = setOf(0xab4cda48L)
    private val DPSSG3_BASE_ADISK_CRC32S = setOf(0xb77ae133L)
    private val DPSSGFAHREN_BDISK_CRC32S = setOf(0xe405d57cL)
    private val DPSSGKATEI_BDISK_CRC32S = setOf(0x23e67d18L)
    private val DPSSGNOBUNAGA_BDISK_CRC32S = setOf(0x2ec116f2L)
    private val DPSSG2ANTIQUE_BDISK_CRC32S = setOf(0x41fe8b3dL)
    private val DPSSG2IKENAI_BDISK_CRC32S = setOf(0x6b562c09L)
    private val DPSSG2AKAI_BDISK_CRC32S = setOf(0x1098e78cL)
    private val DPSSG3RABBIT_BDISK_CRC32S = setOf(0xa3228b6cL)
    private val DPSSG3SHINKON_BDISK_CRC32S = setOf(0x09b4448aL)
    private val DPSSG3SOTSUGYOU_BDISK_CRC32S = setOf(0xbc4525d8L)
    private val RANCE1CLASSIC_ADISK_CRC32S = setOf(0x2fffbd60L)
    private val RANCE2CLASSIC_ADISK_CRC32S = setOf(0x28f8298fL)
    private val RANCE3CLASSIC_ADISK_CRC32S = setOf(0x47a399a1L)
    private val SDPS_BASE_ADISK_CRC32S = setOf(0xc7a20cdfL)
    private val SDPSTONO_BDISK_CRC32S = setOf(0xbb1edff1L)
    private val SDPSKAIZOKU_BDISK_CRC32S = setOf(0xf81829e3L)
    private val NISENAGURI_ADISK_CRC32S = setOf(0xfabe6302L)
    private val DPSALL_ADISK_CRC32S = setOf(0xd48b4ec6L)
    private val PROSTUDENTG_ADISK_CRC32S = setOf(0xfb0e4a63L, 0x5ffbfee7L)
    private val YAKATA3_ADISK_CRC32S = setOf(0x7f8f5e2aL, 0x58ebcc99L)
    private val AMBIVALENZ_ADISK_CRC32S = setOf(0x4b10db69L, 0xa6b48dfeL)
    private val FUNNYBEE_ADISK_CRC32S = setOf(0xe14e3971L, 0x731267faL)
    private val TOUSHIN2SP_ADISK_CRC32S = setOf(0x2172c7b2L)
    private val MUGENHOUYOU_ADISK_CRC32S = setOf(0xbb27d1baL)
    private val OTOMESENKI_ADISK_CRC32S = setOf(0x49a4db15L)

    fun isDiaboliqueAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("diabolique") ||
            normalized.contains("diabo_sa") ||
            normalized.contains("デアボリカ") ||
            normalized.contains("魔鬼恋曲") ||
            normalized.contains("魔鬼戀曲")
    }

    @JvmOverloads
    fun isDiaboliqueGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                it.equals("DIABO_SA.ALD", true) ||
                    it.equals("DIABO_GA.ALD", true) ||
                    it.equals("DIABO_GB.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDiaboliqueAlias(title)) return true
        }
        return false
    }

    fun isDiaboliqueGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDiaboliqueGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isGakuenKingExAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val hasGakuenKing = normalized.contains("gakuenking") ||
            normalized.contains("gakuen_king") ||
            normalized.contains("gakuen king") ||
            normalized.contains("学園king") ||
            normalized.contains("学园king") ||
            normalized.contains("學園king")
        return normalized.contains("gakuenkingex") ||
            normalized.contains("gakuenking_ex") ||
            normalized.contains("gakuenking-ex") ||
            normalized.contains("gakuenkingbangai") ||
            normalized.contains("学園番外編") ||
            normalized.contains("日出彦 カレーをつくる") ||
            normalized.contains("日出彦カレーをつくる") ||
            normalized.contains("hidehiko curry") ||
            (hasGakuenKing && (normalized.contains("bangai") ||
                normalized.contains("番外") ||
                normalized.contains("curry") ||
                normalized.contains("カレー") ||
                Regex("(?<![a-z0-9])ex(?![a-z0-9])").containsMatchIn(normalized)))
    }

    @JvmOverloads
    fun isGakuenKingExGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("学園番外編SA.ald", true) || r.equals("学園番外編SA.ald", true) ||
                    it.equals("学園番外編GA.ald", true) || r.equals("学園番外編GA.ald", true) ||
                    it.equals("学園番外編MA.ald", true) || r.equals("学園番外編MA.ald", true) ||
                    it.equals("学園番外編WA.ald", true) || r.equals("学園番外編WA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isGakuenKingExAlias(title)) return true
        }
        return false
    }

    fun isGakuenKingExGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isGakuenKingExGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isGakuenKingAlias(raw: String): Boolean {
        if (raw.isBlank() || isGakuenKingExAlias(raw)) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("gakuenking") ||
            normalized.contains("gakuen_king") ||
            normalized.contains("gakuen-king") ||
            normalized.contains("gakuen king") ||
            normalized.contains("学園king") ||
            normalized.contains("学园king") ||
            normalized.contains("學園king") ||
            normalized.contains("日出彦 学校をつくる") ||
            normalized.contains("日出彦学校をつくる") ||
            normalized.contains("hidehiko gakkou")
    }

    @JvmOverloads
    fun isGakuenKingGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "gakuen_king" || game == "gakuenking") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isGakuenKingAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in GAKUENKING_ADISK_CRC32S) return true
        }
        return fileNames.any { it.equals("GAKUEN.COM", true) }
    }

    fun isGakuenKingGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isGakuenKingGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isOudouYuushaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("oudouyuusha") ||
            normalized.contains("oudou_yuusha") ||
            normalized.contains("oudou-yuusha") ||
            normalized.contains("oudou yuusha") ||
            normalized.contains("odo yuusha") ||
            normalized.contains("王道勇者") ||
            normalized.contains("王道sa")
    }

    @JvmOverloads
    fun isOudouYuushaGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("王道SA.ALD", true) || r.equals("王道SA.ALD", true) ||
                    it.equals("王道GA.ALD", true) || r.equals("王道GA.ALD", true) ||
                    it.equals("王道GB.ALD", true) || r.equals("王道GB.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isOudouYuushaAlias(title)) return true
        }
        return false
    }

    fun isOudouYuushaGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isOudouYuushaGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isToushin2Alias(raw: String): Boolean {
        if (raw.isBlank() || isToushin3Alias(raw) || isToushin2SpAlias(raw)) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        return Regex("(?<![a-z0-9])(?:toushin|toshin)[\\s._-]*(?:toshi[\\s._-]*)?(?:2|ii)(?![0-9a-z])", RegexOption.IGNORE_CASE)
            .containsMatchIn(normalized) ||
            Regex("(?:闘神都市|斗神都市|鬥神都市)[\\s._-]*(?:2|ii)(?![0-9a-z])", RegexOption.IGNORE_CASE)
                .containsMatchIn(normalized) ||
            normalized == "tt2" ||
            normalized.contains("tt2sa") ||
            rawLower.contains("摤恄搒巗2") ||
            rawLower.contains("摤恄搒巗ii") ||
            rawLower.contains("摤恄搒巗俀")
    }

    @JvmOverloads
    fun isToushin2GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                val n = java.text.Normalizer.normalize(r, java.text.Normalizer.Form.NFKC)
                it.equals("tt2SA.ALD", true) ||
                    it.equals("tt2GA.ald", true) ||
                    it.equals("tt2DA.ALD", true) ||
                    it.equals("tt2WA.ALD", true) ||
                    n.equals("闘神都市2SA.ALD", true) ||
                    n.equals("闘神都市IISA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isToushin2Alias(title)) return true
        }
        return false
    }

    fun isToushin2GameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isToushin2GameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isToushin1Alias(raw: String): Boolean {
        if (raw.isBlank() || isToushin3Alias(raw) || isToushin2SpAlias(raw) || isToushin2Alias(raw)) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        val rawLower = raw.lowercase()
        if (Regex("(?:toushin|toshin|闘神都市|斗神都市|鬥神都市)[\\s._-]*(?:[23]|ii|iii)", RegexOption.IGNORE_CASE)
                .containsMatchIn(normalized)
        ) {
            return false
        }
        return normalized.contains("toushin1") ||
            normalized.contains("toushin 1") ||
            normalized.contains("toushin_1") ||
            normalized.contains("toushin-1") ||
            normalized.contains("toshin1") ||
            normalized.contains("toushin toshi") ||
            normalized.contains("toushintoshi") ||
            normalized.contains("toshin toshi") ||
            normalized.contains("闘神都市") ||
            normalized.contains("斗神都市") ||
            normalized.contains("鬥神都市") ||
            rawLower.contains("摤恄搒巗")
    }

    @JvmOverloads
    fun isToushin1GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames) || isToushin2GameFiles(fileNames, readSmallFile)) return false
        if (fileNames.any {
                it.equals("TOUSHINSA.ALD", true) ||
                    it.equals("TOUSHINGA.ALD", true) ||
                    it.equals("TOUSHINDA.ALD", true) ||
                    it.equals("TOUSHINMA.ALD", true) ||
                    it.equals("TOUSHINWA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isToushin1Alias(title)) return true
        }
        return false
    }

    fun isToushin1GameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isToushin1GameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isDarcrowsAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("darcrows") ||
            normalized.contains("ダークロウズ") ||
            normalized.contains("暗鸦") ||
            normalized.contains("暗鴉") ||
            normalized.contains("hime_sa")
    }

    @JvmOverloads
    fun isDarcrowsGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                it.equals("HIME_SA.ALD", true) ||
                    it.equals("HIME_GA.ALD", true) ||
                    it.equals("HIME_GB.ALD", true) ||
                    it.equals("HIME_RA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDarcrowsAlias(title)) return true
        }
        return false
    }

    fun isDarcrowsGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDarcrowsGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isIkusaMikoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        return normalized.contains("ikusamiko") ||
            normalized.contains("ikusa_miko") ||
            normalized.contains("ikusa-miko") ||
            normalized.contains("ikusa miko") ||
            normalized.contains("vestal virgin") ||
            normalized.contains("戦巫女") ||
            normalized.contains("战巫女") ||
            normalized.contains("戰巫女")
    }

    @JvmOverloads
    fun isIkusaMikoGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("戦巫女SA.ALD", true) || r.equals("戦巫女SA.ALD", true) ||
                    it.equals("戦巫女GA.ALD", true) || r.equals("戦巫女GA.ALD", true) ||
                    it.equals("戦巫女WA.ALD", true) || r.equals("戦巫女WA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isIkusaMikoAlias(title)) return true
        }
        return false
    }

    fun isIkusaMikoGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isIkusaMikoGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isOnlyYouAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(raw)
        val normalized = java.text.Normalizer.normalize(repaired, java.text.Normalizer.Form.NFKC).lowercase()
        if (normalized.contains("recross") || normalized.contains("re-cross") ||
            normalized.contains("re cross") || normalized.contains("re・cross") ||
            normalized.contains("リクルス") || normalized.contains("リ・クルス")
        ) return false
        return normalized.contains("onlyyou") ||
            normalized.contains("only_you") ||
            normalized.contains("only-you") ||
            normalized.contains("only you") ||
            normalized.contains("世紀末のジュリエット") ||
            normalized.contains("世纪末的朱丽叶") ||
            normalized.contains("世紀末的朱麗葉") ||
            normalized.contains("seikimatsu no juliette")
    }

    @JvmOverloads
    fun isOnlyYouGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "onlyyou" || game == "only_you") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isOnlyYouAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in ONLYYOU_ADISK_CRC32S) return true
        }
        return false
    }

    fun isOnlyYouGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isOnlyYouGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    private fun nfkcLower(raw: String): String =
        java.text.Normalizer.normalize(ArchiveExtractor.repairSjisAsGbkMojibake(raw), java.text.Normalizer.Form.NFKC).lowercase()

    fun isSanbikiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("sanbiki") ||
            n.contains("三匹が") ||
            n.contains("三匹") ||
            n.contains("slash!thrust!burn!") ||
            n.contains("slash! thrust! burn!")
    }

    @JvmOverloads
    fun isSanbikiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("三匹がGA.ald", true) || r.equals("三匹がGA.ald", true) ||
                    it.equals("三匹がBA.ald", true) || r.equals("三匹がBA.ald", true) ||
                    it.equals("三匹がWA.ald", true) || r.equals("三匹がWA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isSanbikiAlias(title)) return true
        }
        return false
    }

    fun isSanbikiGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isSanbikiGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isPersiomAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("persiom") || n.contains("約束の集う場所") || n.contains("约定汇聚之所") || n.contains("約定匯聚之所")
    }

    @JvmOverloads
    fun isPersiomGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                it.equals("PERSIOM_SA.ALD", true) || it.equals("PERSIOM_GA.ALD", true) ||
                    it.equals("PERSIOM_GB.ALD", true) || it.equals("PERSIOM_DA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isPersiomAlias(title)) return true
        }
        return false
    }

    fun isPersiomGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isPersiomGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isSeeInAoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("seeinao") || n.contains("seein_ao") || n.contains("seein青") ||
            n.contains("see in 青") || n.contains("see in ao") || n.contains("シーンao") || n.contains("scene ao")
    }

    @JvmOverloads
    fun isSeeInAoGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("イルカSA.ald", true) || r.equals("イルカSA.ald", true) ||
                    it.equals("イルカGA.ald", true) || r.equals("イルカGA.ald", true) ||
                    it.equals("イルカMA.ald", true) || r.equals("イルカMA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isSeeInAoAlias(title)) return true
        }
        return false
    }

    fun isSeeInAoGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isSeeInAoGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isHushabyAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("hushaby") || n.contains("幽霊sa")
    }

    @JvmOverloads
    fun isHushabyGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("幽霊SA.ALD", true) || r.equals("幽霊SA.ALD", true) ||
                    it.equals("幽霊GA.ALD", true) || r.equals("幽霊GA.ALD", true) ||
                    it.equals("幽霊MA.ALD", true) || r.equals("幽霊MA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isHushabyAlias(title)) return true
        }
        return false
    }

    fun isHushabyGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isHushabyGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isAyumiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("ayumichan") || n.contains("ayumi_chan") || n.contains("ayumi-chan") ||
            n.contains("ayumi_cd") || Regex("(?<![a-z0-9])ayumi(?![a-z0-9])").containsMatchIn(n) ||
            n.contains("あゆみちゃん") || n.contains("步美物语") || n.contains("步美物語")
    }

    @JvmOverloads
    fun isAyumiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "ayumi_cd" || game == "ayumi" || game == "ayumi_fd" ||
                game == "ayumi_proto"
            ) return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isAyumiAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in AYUMI_ADISK_CRC32S) return true
        }
        return fileNames.any { it.equals("ACG0.DAT", true) } && fileNames.any { it.equals("ADISK.DAT", true) }
    }

    fun isAyumiGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isAyumiGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isCielClaireAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("cielclaire") || n.contains("ciel_claire") || n.contains("ciel claire") ||
            n.contains("chairs cruelles") || n.contains("シェル・クレイル") || n.contains("シェルクレイル") ||
            n.contains("愛しあう逃避") || n.contains("逃避行sa") || n.contains("残酷的爱恋") || n.contains("殘酷的愛戀")
    }

    @JvmOverloads
    fun isCielClaireGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("逃避行SA.ALD", true) || r.equals("逃避行SA.ALD", true) ||
                    it.equals("逃避行GA.ald", true) || r.equals("逃避行GA.ald", true) ||
                    it.equals("逃避行BA.ald", true) || r.equals("逃避行BA.ald", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isCielClaireAlias(title)) return true
        }
        return false
    }

    fun isCielClaireGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isCielClaireGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isNightDemonAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("nightdemon") || n.contains("night_demon") || n.contains("night demon") ||
            n.contains("ナイトデーモン") || n.contains("夢鬼") || n.contains("梦鬼") || n.contains("yume oni")
    }

    @JvmOverloads
    fun isNightDemonGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("夢鬼SA.ald", true) || r.equals("夢鬼SA.ald", true) ||
                    it.equals("夢鬼GA.ald", true) || r.equals("夢鬼GA.ald", true) ||
                    it.equals("NightDemonDemo1.alk", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isNightDemonAlias(title)) return true
        }
        return false
    }

    fun isNightDemonGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isNightDemonGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isKatsumiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("katsumi") || n.contains("いけないかつみ") || n.contains("かつみ先生") ||
            n.contains("かつみsa") || n.contains("胜美大夫") || n.contains("勝美大夫")
    }

    @JvmOverloads
    fun isKatsumiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("かつみSA.ALD", true) || r.equals("かつみSA.ALD", true) ||
                    it.equals("かつみGA.ALD", true) || r.equals("かつみGA.ALD", true) ||
                    it.equals("かつみGB.ALD", true) || r.equals("かつみGB.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isKatsumiAlias(title)) return true
        }
        return false
    }

    fun isKatsumiGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isKatsumiGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isDrStopAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("drstop") || n.contains("dr_stop") || n.contains("dr.stop") || n.contains("dr stop")
    }

    @JvmOverloads
    fun isDrStopGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any { it.equals("DS_SA.ALD", true) } &&
            fileNames.any { it.equals("DS_GA.ALD", true) || it.equals("DS_MA.ALD", true) }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDrStopAlias(title)) return true
        }
        return false
    }

    fun isDrStopGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDrStopGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isKoreDpsAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("koredps") || n.contains("kore_dps") || n.contains("kore dps") ||
            n.contains("kore d.p.s") || n.contains("これdps") || n.contains("これd.p.s") ||
            n.contains("これd・p・s") || n.contains("这是dps") || n.contains("这是d.p.s") ||
            n.contains("這是dps") || n.contains("這是d.p.s")
    }

    @JvmOverloads
    fun isKoreDpsGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "これdps?sa.ald" || n == "これdps?ga.ald" || n == "これdps?ma.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isKoreDpsAlias(title)) return true
        }
        return false
    }

    fun isKoreDpsGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isKoreDpsGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isDpsAllAlias(raw: String): Boolean {
        if (raw.isBlank() || isKoreDpsAlias(raw) || isSdpsMariaAlias(raw)) return false
        val n = nfkcLower(raw)
        return n.contains("dpsall") || n.contains("dps_all") || n.contains("dps all") ||
            n.contains("d.p.s.全部") || n.contains("d.p.s. 全部") || n.contains("dps全部") ||
            n.contains("d.p.s. zenbu") || n.contains("dps zenbu")
    }

    @JvmOverloads
    fun isDpsAllGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "dps_all" || game == "dpsall") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isDpsAllAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in DPSALL_ADISK_CRC32S) return true
        }
        return fileNames.any { it.equals("ACG1_1.DAT", true) } && fileNames.any { it.equals("ACGS1_16.DAT", true) }
    }

    fun isDpsAllGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isDpsAllGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isKaeruAliceAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("kaerualice") || n.contains("kaeru_alice") ||
            n.contains("kuni nyo alice") || n.contains("かえるにょ国にょアリス") ||
            n.contains("国にょアリス") || n.contains("蛙国爱丽丝") || n.contains("蛙國愛麗絲")
    }

    @JvmOverloads
    fun isKaeruAliceGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("かえるにょ国にょアリスSA.ALD", true) || r.equals("かえるにょ国にょアリスSA.ALD", true) ||
                    it.equals("かえるにょ国にょアリスGA.ALD", true) || r.equals("かえるにょ国にょアリスGA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isKaeruAliceAlias(title)) return true
        }
        return false
    }

    fun isKaeruAliceGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isKaeruAliceGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isProstudentGoodAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("prostudentgood") || n.contains("prostudent_good") || n.contains("prostudent good") ||
            n.contains("prostu★dent") || n.contains("ぷろすちゅ★でんと") || n.contains("ぷろすちゅーでんとgood") ||
            n.contains("sr猿") || n.contains("职业学生good") || n.contains("職業學生good")
    }

    @JvmOverloads
    fun isProstudentGoodGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "sr猿sa.ald" || n == "sr猿ga.ald" || n == "sr猿da.ald" || n == "sr猿wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isProstudentGoodAlias(title)) return true
        }
        return false
    }

    fun isProstudentGoodGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isProstudentGoodGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isProstudentGAlias(raw: String): Boolean {
        if (raw.isBlank() || isProstudentGoodAlias(raw)) return false
        val n = nfkcLower(raw)
        return n.contains("prostudentg") || n.contains("prostudent_g") || n.contains("prostudent g") ||
            n.contains("prog_cd") || n.contains("ぷろすちゅーでんとg") ||
            n.contains("职业学生g") || n.contains("職業學生g")
    }

    @JvmOverloads
    fun isProstudentGGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "prog_cd" || game == "prog_fd" || game == "prog" || game == "prostudentg" || game == "prostudent_g") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isProstudentGAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in PROSTUDENTG_ADISK_CRC32S) return true
        }
        return false
    }

    fun isProstudentGGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isProstudentGGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isPascha3Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("pascha3") || n.contains("pascha_3") || n.contains("pastelchime3") ||
            n.contains("pastel chime 3") || n.contains("bind seeker") || n.contains("bindseeker") ||
            n.contains("パステルチャイム3") || n.contains("バインドシーカー") ||
            n.contains("粉彩铃音3") || n.contains("粉彩鈴音3") || n.contains("封神追寻者") || n.contains("封神追尋者")
    }

    @JvmOverloads
    fun isPascha3GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "パステルチャイム3.ain" || n == "pascha3_cn.afa" ||
                    n == "パステルチャイム3cg.afa" || n == "パステルチャイム3aff.afa" ||
                    n == "pc3特典データcg.afa"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isPascha3Alias(title)) return true
        }
        return false
    }

    fun isPascha3GameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isPascha3GameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isMamatotoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("mamatoto") || n.contains("ママトト") ||
            n.contains("妈妈托托") || n.contains("媽媽托托")
    }

    @JvmOverloads
    fun isMamatotoGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ママトトsa.ald" || n == "ママトトga.ald" || n == "ママトトgb.ald" ||
                    n == "ママトトda.ald" || n == "ママトトma.ald" || n == "ママトトwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMamatotoAlias(title)) return true
        }
        return false
    }

    fun isMamatotoGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isMamatotoGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isOnlyYouReCrossAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("onlyyourecross") || n.contains("onlyyou_recross") ||
            n.contains("onlyyou-recross") || n.contains("recross") ||
            n.contains("re-cross") || n.contains("re cross") || n.contains("re・cross") ||
            n.contains("リ・クルス") || n.contains("リクルス")
    }

    @JvmOverloads
    fun isOnlyYouReCrossGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "オンリーsa.ald" || n == "オンリーga.ald" || n == "オンリーwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isOnlyYouReCrossAlias(title)) return true
        }
        return false
    }

    fun isOnlyYouReCrossGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isOnlyYouReCrossGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isMamorigamiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("mamorigami") || n.contains("守り神様") ||
            n.contains("守神sa") || n.contains("守护神大人") || n.contains("守護神大人") ||
            n.contains("守护神明") || n.contains("守護神明")
    }

    @JvmOverloads
    fun isMamorigamiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "守神sa.ald" || n == "守神ga.ald" || n == "守神gb.ald" ||
                    n == "守神ma.ald" || n == "守神wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMamorigamiAlias(title)) return true
        }
        return false
    }

    fun isMamorigamiGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isMamorigamiGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isAmbivalenzAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("ambivalenz") || n.contains("二律背反") ||
            n.contains("niritsu haihan") || n.contains("niritsuhaihan")
    }

    @JvmOverloads
    fun isAmbivalenzGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "ambivalenz_cd" || game == "ambivalenz_fd" || game == "ambivalenz") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isAmbivalenzAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in AMBIVALENZ_ADISK_CRC32S) return true
        }
        return false
    }

    fun isAmbivalenzGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isAmbivalenzGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isFunnyBeeAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("funnybee") || n.contains("funny_bee") ||
            n.contains("funny-bee") || n.contains("funny bee") ||
            n.contains("ファニーbee") || n.contains("宇宙快盗") || n.contains("宇宙怪盗")
    }

    @JvmOverloads
    fun isFunnyBeeGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "funnybee_cd" || game == "funnybee_fd" || game == "funnybee") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isFunnyBeeAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in FUNNYBEE_ADISK_CRC32S) return true
        }
        return false
    }

    fun isFunnyBeeGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isFunnyBeeGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isToushin2SpAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("toushin2sp") || n.contains("toushin2_sp") ||
            n.contains("toushin2-sp") || n.contains("toushin 2 sp") ||
            n.contains("soshite, sorekara") || n.contains("soshitesorekara") ||
            n.contains("そして、それから") || n.contains("そしてそれから") ||
            n.contains("然后,从那以后") || n.contains("然后从那以后") ||
            n.contains("然後,從那以後") || n.contains("然後從那以後")
    }

    @JvmOverloads
    fun isToushin2SpGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "toushin2_sp" || game == "toushin2sp") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isToushin2SpAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in TOUSHIN2SP_ADISK_CRC32S) return true
        }
        return false
    }

    fun isToushin2SpGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isToushin2SpGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isMugenHouyouAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("mugenhouyou") || n.contains("mugen_houyou") ||
            n.contains("mugen-houyou") || n.contains("mugen houyou") ||
            n.contains("夢幻泡影") || n.contains("梦幻泡影")
    }

    @JvmOverloads
    fun isMugenHouyouGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "mugen" || game == "mugenhouyou" || game == "mugen_houyou") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isMugenHouyouAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in MUGENHOUYOU_ADISK_CRC32S) return true
        }
        return false
    }

    fun isMugenHouyouGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isMugenHouyouGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isOtomeSenkiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("otomesenki") || n.contains("otome_senki") ||
            n.contains("otome-senki") || n.contains("otome senki") ||
            n.contains("乙女戦記") || n.contains("乙女战记") || n.contains("乙女戰記")
    }

    @JvmOverloads
    fun isOtomeSenkiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "otome" || game == "otomesenki" || game == "otome_senki") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isOtomeSenkiAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in OTOMESENKI_ADISK_CRC32S) return true
        }
        return false
    }

    fun isOtomeSenkiGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isOtomeSenkiGameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isGakuenHyouryuuAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("gakuenhyouryuu") || n.contains("gakuen_hyouryuu") ||
            n.contains("gakuen-hyouryuu") || n.contains("gakuen hyouryuu") ||
            n.contains("学園漂流") || n.contains("学园漂流") || n.contains("學園漂流")
    }

    @JvmOverloads
    fun isGakuenHyouryuuGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "学園漂流sa.ald" || n == "学園漂流ga.ald" ||
                    n == "学園漂流ma.ald" || n == "学園漂流wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isGakuenHyouryuuAlias(title)) return true
        }
        return false
    }

    fun isGakuenHyouryuuGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isGakuenHyouryuuGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isMaidNoSusumeAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("maidnosusume") || n.contains("maid_no_susume") ||
            n.contains("maid-no-susume") || n.contains("maid no susume") ||
            n.contains("メイドのススメ") || n.contains("女仆的推荐") || n.contains("女僕的推薦")
    }

    @JvmOverloads
    fun isMaidNoSusumeGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "メイドsa.ald" || n == "メイドga.ald" ||
                    n == "メイドma.ald" || n == "メイドwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMaidNoSusumeAlias(title)) return true
        }
        return false
    }

    fun isMaidNoSusumeGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isMaidNoSusumeGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isPaschaCppAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("paschacpp") || n.contains("pascha_cpp") || n.contains("pascha c++") ||
            n.contains("paschac++") || n.contains("ぱすちゃc++") || n.contains("ぱすちゃc ++") ||
            n.contains("ぱすてる+") || n.contains("粉彩铃音 c++") || n.contains("粉彩铃音c++") ||
            n.contains("粉彩鈴音 c++") || n.contains("粉彩鈴音c++")
    }

    @JvmOverloads
    fun isPaschaCppGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ぱすてる+.ain" || n == "ぱすてる+ga.ald" || n == "ぱすてる+ba.ald" || n == "ぱすてる+wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isPaschaCppAlias(title)) return true
        }
        return false
    }

    fun isPaschaCppGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isPaschaCppGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isPastelChime2Alias(raw: String): Boolean {
        if (raw.isBlank() || isPascha3Alias(raw) || isPaschaCppAlias(raw)) return false
        val n = nfkcLower(raw)
        return n.contains("pastelchime2") || n.contains("pastel_chime_2") || n.contains("pastel chime 2") ||
            n.contains("pastel chime continue") || n.contains("pastelchimecontinue") || n.contains("pastelcc") ||
            n.contains("ぱすてるチャイムcontinue") || n.contains("ぱすてるチャイム continue") ||
            n.contains("ぱすてる2") || n.contains("粉彩铃音continue") || n.contains("粉彩铃音 continue") ||
            n.contains("粉彩铃音2") || n.contains("粉彩鈴音2")
    }

    @JvmOverloads
    fun isPastelChime2GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ぱすてる2.ain" || n == "ぱすてる2ga.ald" || n == "ぱすてる2ba.ald" ||
                    n == "pastelcc_op.mpg" || n == "pastelcc_ed1.mpg"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isPastelChime2Alias(title)) return true
        }
        return false
    }

    fun isPastelChime2GameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isPastelChime2GameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isPastelChimeAlias(raw: String): Boolean {
        if (raw.isBlank() || isPascha3Alias(raw) || isPaschaCppAlias(raw) || isPastelChime2Alias(raw)) return false
        val n = nfkcLower(raw)
        return n.contains("pastelchime") || n.contains("pastel_chime") || n.contains("pastel chime") ||
            n.contains("ぱすてるチャイム") || n.contains("恋のスキルアップ") || n.contains("ぱすてるsa") ||
            n.contains("粉彩铃音") || n.contains("粉彩鈴音")
    }

    @JvmOverloads
    fun isPastelChimeGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("ぱすてるSA.ALD", true) || r.equals("ぱすてるSA.ALD", true) ||
                    it.equals("ぱすてるGA.ALD", true) || r.equals("ぱすてるGA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isPastelChimeAlias(title)) return true
        }
        return false
    }

    fun isPastelChimeGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isPastelChimeGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isYakata3Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("yakata3") || n.contains("yakata_3") || n.contains("yakata 3") ||
            n.contains("yakataiii") || n.contains("aliceの館3") || n.contains("aliceの館iii") ||
            n.contains("爱丽丝之馆3") || n.contains("愛麗絲之館3") || n.contains("爱丽丝之馆iii")
    }

    @JvmOverloads
    fun isYakata3GameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "yakata3_cd" || game == "yakata3" || game == "yakata3_fd") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isYakata3Alias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in YAKATA3_ADISK_CRC32S) return true
        }
        return false
    }

    fun isYakata3GameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isYakata3GameFiles(files.map { it.name }) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            when {
                f == null -> null
                name.equals("ADISK.DAT", true) -> readFilePrefix(f, 256)
                f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
                else -> null
            }
        }
    }

    fun isDalkAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        if (n.contains("gaiden") || n.contains("外伝") || n.contains("外传") || n.contains("外傳") || n.contains("dalkdemo")) return false
        return Regex("(?<![a-z0-9])dalk(?![a-z0-9])").containsMatchIn(n) || n.contains("dalk_sa")
    }

    @JvmOverloads
    fun isDalkGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                it.equals("DALK_SA.ALD", true) || it.equals("DALK_GA.ALD", true) || it.equals("DALK_DA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDalkAlias(title)) return true
        }
        return false
    }

    fun isDalkGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDalkGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isNingenGariAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("ningengari") || n.contains("ningen_gari") || n.contains("ningen gari") ||
            n.contains("人間狩り") || n.contains("人间狩猎") || n.contains("人間狩獵")
    }

    @JvmOverloads
    fun isNingenGariGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        if (fileNames.any {
                val r = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("人間狩りSA.ALD", true) || r.equals("人間狩りSA.ALD", true) ||
                    it.equals("人間狩りGA.ALD", true) || r.equals("人間狩りGA.ALD", true)
            }
        ) return true
        val iniName = fileNames.firstOrNull { it.equals("System39.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isNingenGariAlias(title)) return true
        }
        return false
    }

    fun isNingenGariGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isNingenGariGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isDalkGaidenAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dalkgaiden") || n.contains("dalk_gaiden") ||
            n.contains("dalk-gaiden") || n.contains("dalk gaiden") ||
            n.contains("dalk外伝") || n.contains("dalk外传") || n.contains("dalk外傳")
    }

    @JvmOverloads
    fun isDalkGaidenGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "dalk外伝.ain" || n == "dalk外伝ga.ald" || n == "dalk外伝ba.ald" ||
                    n == "dalk外伝wa.ald" || n == "dalk外伝da.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDalkGaidenAlias(title)) return true
        }
        return false
    }

    fun isDalkGaidenGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDalkGaidenGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isMamanyonyoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("mamanyonyo") || n.contains("ままにょにょ") ||
            n.contains("妈妈软软") || n.contains("媽媽軟軟")
    }

    @JvmOverloads
    fun isMamanyonyoGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ままにょにょ.ain" || n == "ままにょにょga.ald" || n == "ままにょにょba.ald" ||
                    n == "ままにょにょwa.ald" || n == "mamanyodemo.alk"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMamanyonyoAlias(title)) return true
        }
        return false
    }

    fun isMamanyonyoGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isMamanyonyoGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isMajoNoShokuzaiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("majonoshokuzai") || n.contains("majo_no_shokuzai") ||
            n.contains("majo-no-shokuzai") || n.contains("majo no shokuzai") ||
            n.contains("魔女の贖罪") || n.contains("魔女的赎罪") || n.contains("魔女的贖罪")
    }

    @JvmOverloads
    fun isMajoNoShokuzaiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "魔女の贖罪.ain" || n == "魔女の贖罪ga.ald" || n == "魔女の贖罪ba.ald" ||
                    n == "魔女の贖罪wa.ald" || n == "魔女の贖罪wb.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMajoNoShokuzaiAlias(title)) return true
        }
        return false
    }

    fun isMajoNoShokuzaiGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isMajoNoShokuzaiGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isSabotenAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("saboten") || n.contains("よくばりサボテン") ||
            n.contains("贪心仙人掌") || n.contains("貪心仙人掌")
    }

    @JvmOverloads
    fun isSabotenGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "よくばりサボテン.ain" || n == "よくばりサボテンga.ald" ||
                    n == "よくばりサボテンba.ald" || n == "よくばりサボテンwa.ald" ||
                    n == "よくばりサボテンwb.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isSabotenAlias(title)) return true
        }
        return false
    }

    fun isSabotenGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isSabotenGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isDoubleSenseiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("doublesensei") || n.contains("double_sensei") ||
            n.contains("double-sensei") || n.contains("double sensei") ||
            n.contains("だぶる先生らいふ") || n.contains("双倍教师生活") || n.contains("雙倍教師生活")
    }

    @JvmOverloads
    fun isDoubleSenseiGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "だぶる先生らいふ.ain" || n == "だぶる先生らいふga.ald" ||
                    n == "だぶる先生らいふba.ald" || n == "だぶる先生らいふwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDoubleSenseiAlias(title)) return true
        }
        return false
    }

    fun isDoubleSenseiGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isDoubleSenseiGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isOjousamaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("ojousama") || n.contains("ojou-sama o iinari") ||
            n.contains("ojousama o iinari") || n.contains("お嬢様をいいなり") ||
            n.contains("让大小姐言听计从") || n.contains("讓大小姐言聽計從")
    }

    @JvmOverloads
    fun isOjousamaGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "お嬢様.ain" || n == "お嬢様ga.ald" || n == "お嬢様ba.ald" || n == "お嬢様wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isOjousamaAlias(title)) return true
        }
        return false
    }

    fun isOjousamaGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isOjousamaGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isEdoranzeAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("edoranze") || n.contains("edo ranze") || n.contains("edo-ranze") ||
            n.contains("エドランゼ") || n.contains("水戸姫様") ||
            n.contains("江户乱世") || n.contains("江戶亂世") || n.contains("水户姬大人") || n.contains("水戶姬大人")
    }

    @JvmOverloads
    fun isEdoranzeGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "エドランゼ.ain" || n == "エドランゼga.ald" ||
                    n == "エドランゼba.ald" || n == "エドランゼwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isEdoranzeAlias(title)) return true
        }
        return false
    }

    fun isEdoranzeGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isEdoranzeGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isVanishAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        if (n.contains("この手のひら") || n.contains("kono te no hira") ||
            n.contains("vanishpalm") || n.contains("vanish_palm") ||
            n.contains("ばにしゅ!番外") || n.contains("掌中奇缘") || n.contains("掌中奇緣")
        ) return false
        return n.contains("vanish") || n.contains("ばにしゅ") ||
            n.contains("おっぱいの消えた王国") || n.contains("消失的王国") || n.contains("消失的王國")
    }

    @JvmOverloads
    fun isVanishGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ばにしゅ!.ain" || n == "ばにしゅ!ga.ald" ||
                    n == "ばにしゅ!ba.ald" || n == "ばにしゅ!wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isVanishAlias(title)) return true
        }
        return false
    }

    fun isVanishGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isVanishGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isHokenshitsuAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("hokenshitsu") || n.contains("僕だけの保健室") ||
            n.contains("只属于我的保健室") || n.contains("只屬於我的保健室")
    }

    @JvmOverloads
    fun isHokenshitsuGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "僕だけの保健室.ain" || n == "僕だけの保健室ga.ald" ||
                    n == "僕だけの保健室ba.ald" || n == "僕だけの保健室wa.ald" ||
                    n == "hoken.alm"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isHokenshitsuAlias(title)) return true
        }
        return false
    }

    fun isHokenshitsuGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isHokenshitsuGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isKamunagaraAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        if (n.contains("神武2") || n.contains("第弐話") || n.contains("第2話")) return false
        return n.contains("kamunagara") || n.contains("カムナガラ") || n.contains("神武") ||
            n.contains("卡姆纳加拉") || n.contains("卡姆納加拉")
    }

    @JvmOverloads
    fun isKamunagaraGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "神武.ain" || n == "神武ga.ald" || n == "神武ba.ald" || n == "神武wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isKamunagaraAlias(title)) return true
        }
        return false
    }

    fun isKamunagaraGameDir(dir: File?): Boolean =
        dir?.isDirectory == true && isKamunagaraGameFiles(
            dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) { readSmallRootFile(dir, it) }

    fun isErumeiWakareAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("erumeiwakare") || n.contains("erumei_wakare") ||
            n.contains("erumei-wakare") || n.contains("wakare no yoru") ||
            n.contains("別れの夜") || n.contains("别离之夜") || n.contains("別離之夜")
    }

    @JvmOverloads
    fun isErumeiWakareGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null,
        getFileSize: ((String) -> Long?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        val hasErumeiFiles = fileNames.any {
            val n = nfkcLower(it)
            n == "エルメイフラワー.ain" || n == "エルメイフラワーga.ald" ||
                n == "エルメイフラワーba.ald" || n == "エルメイフラワーwa.ald"
        }
        if (!hasErumeiFiles) return false
        val waiName = fileNames.firstOrNull { nfkcLower(it) == "エルメイフラワーwa.wai" }
        val waiSize = waiName?.let { name ->
            getFileSize?.invoke(name)?.takeIf { it > 0L }
                ?: readSmallFile?.let { runCatching { it(name) }.getOrNull()?.size?.toLong()?.takeIf { s -> s > 0L } }
        }
        if (waiSize != null) {
            return waiSize <= 256L
        }
        val bgiName = fileNames.firstOrNull { nfkcLower(it) == "エルメイフラワーba.bgi" }
        val bgiSize = bgiName?.let { name ->
            getFileSize?.invoke(name)?.takeIf { it > 0L }
                ?: readSmallFile?.let { runCatching { it(name) }.getOrNull()?.size?.toLong()?.takeIf { s -> s > 0L } }
        }
        if (bgiSize != null) {
            return bgiSize <= 600L
        }
        val ainName = fileNames.firstOrNull { nfkcLower(it) == "エルメイフラワー.ain" }
        val ainSize = ainName?.let { name -> getFileSize?.invoke(name)?.takeIf { it > 0L } }
        if (ainSize != null) {
            return ainSize <= 500_000L
        }
        return false
    }

    fun isErumeiWakareGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        val names = files.map { it.name }
        return isErumeiWakareGameFiles(
            fileNames = names,
            readSmallFile = { readSmallRootFile(dir, it) },
            getFileSize = { name -> files.firstOrNull { it.name.equals(name, true) }?.length() }
        )
    }

    fun isErumeiFlowerAlias(raw: String): Boolean {
        if (raw.isBlank() || isErumeiWakareAlias(raw)) return false
        val n = nfkcLower(raw)
        return n.contains("erumeiflower") || n.contains("erumei_flower") ||
            n.contains("erumei-flower") || n.contains("el mayflower") || n.contains("elmayflower") ||
            n.contains("エルメイフラワー") || n.contains("艾尔梅花") || n.contains("艾爾梅花")
    }

    @JvmOverloads
    fun isErumeiFlowerGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null,
        getFileSize: ((String) -> Long?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames) || isErumeiWakareGameFiles(fileNames, readSmallFile, getFileSize)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "エルメイフラワー.ain" || n == "エルメイフラワーga.ald" ||
                    n == "エルメイフラワーba.ald" || n == "エルメイフラワーwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isErumeiFlowerAlias(title)) return true
        }
        return false
    }

    fun isErumeiFlowerGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        return isErumeiFlowerGameFiles(
            fileNames = files.map { it.name },
            readSmallFile = { readSmallRootFile(dir, it) },
            getFileSize = { name -> files.firstOrNull { it.name.equals(name, true) }?.length() }
        )
    }

    private fun isMananaVolAlias(raw: String, vol: Int): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        val v = vol.toString()
        return Regex("(?<![a-z0-9])manana[\\s._-]*(?:vol\\.?[\\s._-]*)?$v(?![0-9])").containsMatchIn(n) ||
            Regex("(?:まなな|玛娜娜|瑪娜娜)[\\s._-]*(?:vol\\.?[\\s._-]*)?$v(?![0-9])").containsMatchIn(n)
    }

    private fun isMananaVolGameFiles(
        vol: Int,
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        val v = vol.toString()
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "まななvol$v.ain" || n == "まななvol${v}ga.ald" ||
                    n == "まななvol${v}ba.ald" || n == "まななvol${v}wa.ald" ||
                    n == "まななvol${v}wb.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMananaVolAlias(title, vol)) return true
        }
        return false
    }

    fun isManana1Alias(raw: String): Boolean = isMananaVolAlias(raw, 1)
    @JvmOverloads fun isManana1GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean = isMananaVolGameFiles(1, fileNames, readSmallFile)
    fun isManana1GameDir(dir: File?): Boolean = dir?.isDirectory == true && isManana1GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isManana2Alias(raw: String): Boolean = isMananaVolAlias(raw, 2)
    @JvmOverloads fun isManana2GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean = isMananaVolGameFiles(2, fileNames, readSmallFile)
    fun isManana2GameDir(dir: File?): Boolean = dir?.isDirectory == true && isManana2GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isManana3Alias(raw: String): Boolean = isMananaVolAlias(raw, 3)
    @JvmOverloads fun isManana3GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean = isMananaVolGameFiles(3, fileNames, readSmallFile)
    fun isManana3GameDir(dir: File?): Boolean = dir?.isDirectory == true && isManana3GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isManana4Alias(raw: String): Boolean = isMananaVolAlias(raw, 4)
    @JvmOverloads fun isManana4GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean = isMananaVolGameFiles(4, fileNames, readSmallFile)
    fun isManana4GameDir(dir: File?): Boolean = dir?.isDirectory == true && isManana4GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isManana5Alias(raw: String): Boolean = isMananaVolAlias(raw, 5)
    @JvmOverloads fun isManana5GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean = isMananaVolGameFiles(5, fileNames, readSmallFile)
    fun isManana5GameDir(dir: File?): Boolean = dir?.isDirectory == true && isManana5GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isTsumaSakuraAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("tsumasakura") || n.contains("tsuma_sakura") || n.contains("tsuma-tachi no sakuragai") ||
            n.contains("tsumatachi no sakuragai") || n.contains("妻たちの桜貝") || n.contains("妻们的樱贝") || n.contains("妻們的櫻貝")
    }

    @JvmOverloads
    fun isTsumaSakuraGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "妻たちの桜貝.ain" || n == "妻たちの桜貝ga.ald" || n == "妻たちの桜貝ba.ald" || n == "妻たちの桜貝wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isTsumaSakuraAlias(title)) return true
        }
        return false
    }

    fun isTsumaSakuraGameDir(dir: File?): Boolean = dir?.isDirectory == true && isTsumaSakuraGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isShamansSanctuaryAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("shamanssanctuary") || n.contains("shamans_sanctuary") || n.contains("shaman's sanctuary") ||
            n.contains("miko no seiiki") || n.contains("しゃーまんず") || n.contains("さんくちゅあり") ||
            n.contains("まんくちゅ") || n.contains("巫女の聖域") || n.contains("巫女的圣域") || n.contains("巫女的聖域")
    }

    @JvmOverloads
    fun isShamansSanctuaryGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "まんくちゅ.ain" || n == "まんくちゅga.ald" || n == "まんくちゅba.ald" || n == "まんくちゅwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isShamansSanctuaryAlias(title)) return true
        }
        return false
    }

    fun isShamansSanctuaryGameDir(dir: File?): Boolean = dir?.isDirectory == true && isShamansSanctuaryGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isAliveZAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("alivez") || n.contains("alive_z") || n.contains("alive-z")
    }

    @JvmOverloads
    fun isAliveZGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "alivez.ain" || n == "alivezga.ald" || n == "alivezba.ald" || n == "alivezwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isAliveZAlias(title)) return true
        }
        return false
    }

    fun isAliveZGameDir(dir: File?): Boolean = dir?.isDirectory == true && isAliveZGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isDungeonsDollsAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dungeonsdolls") || n.contains("dungeons_dolls") || n.contains("dungeons & dolls") ||
            n.contains("dungeons&dolls") || n.contains("dungeons and dolls") || n.contains("ドールズ")
    }

    @JvmOverloads
    fun isDungeonsDollsGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ドールズ.ain" || n == "ドールズga.ald" || n == "ドールズba.ald" || n == "ドールズwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDungeonsDollsAlias(title)) return true
        }
        return false
    }

    fun isDungeonsDollsGameDir(dir: File?): Boolean = dir?.isDirectory == true && isDungeonsDollsGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isSuzumiyaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("suzumiya") || n.contains("keimu shoukan") ||
            n.contains("鈴宮刑務娼館") || n.contains("铃宫刑务娼馆") || n.contains("鈴宮刑務娼館")
    }

    @JvmOverloads
    fun isSuzumiyaGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "鈴宮刑務娼館.ain" || n == "鈴宮刑務娼館ga.ald" || n == "鈴宮刑務娼館ba.ald" || n == "鈴宮刑務娼館wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isSuzumiyaAlias(title)) return true
        }
        return false
    }

    fun isSuzumiyaGameDir(dir: File?): Boolean = dir?.isDirectory == true && isSuzumiyaGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isShimaimaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("shimaima") || n.contains("しまいま") || n == "姐妹。" || n == "姐妹"
    }

    @JvmOverloads
    fun isShimaimaGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "しまいま.ain" || n == "しまいまga.ald" || n == "しまいまba.ald" || n == "しまいまwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isShimaimaAlias(title)) return true
        }
        return false
    }

    fun isShimaimaGameDir(dir: File?): Boolean = dir?.isDirectory == true && isShimaimaGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isDaibanchouGaidenAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("daibanchougaiden") || n.contains("daibanchou_gaiden") ||
            n.contains("daibanchou gaiden") || n.contains("daibanchou-gaiden") ||
            n.contains("大番長外伝") || n.contains("大番长外传") || n.contains("大番長外傳")
    }

    @JvmOverloads
    fun isDaibanchouGaidenGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "大番長外伝.ain" || n == "大番長外伝ga.ald" || n == "大番長外伝ba.ald" || n == "大番長外伝wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDaibanchouGaidenAlias(title)) return true
        }
        return false
    }

    fun isDaibanchouGaidenGameDir(dir: File?): Boolean = dir?.isDirectory == true && isDaibanchouGaidenGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isCosplayLemonAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("cosplaylemon") || n.contains("cosplay_lemon") || n.contains("cosplay lemon") ||
            n.contains("こすぷれれもん") || n.contains("コスプレれもん") || n.contains("cosplay柠檬") || n.contains("cosplay檸檬")
    }

    @JvmOverloads
    fun isCosplayLemonGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "こすぷれれもん.ain" || n == "こすぷれれもんga.ald" || n == "こすぷれれもんba.ald" || n == "こすぷれれもんwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isCosplayLemonAlias(title)) return true
        }
        return false
    }

    fun isCosplayLemonGameDir(dir: File?): Boolean = dir?.isDirectory == true && isCosplayLemonGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isWhipHoneyAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("whiphoney") || n.contains("whip_honey") || n.contains("whip honey") ||
            n.contains("ほいっぷハニー") || n.contains("ホイップハニー") || n.contains("奶油哈尼")
    }

    @JvmOverloads
    fun isWhipHoneyGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ほいっぷハニー.ain" || n == "ほいっぷハニーga.ald" || n == "ほいっぷハニーba.ald" || n == "ほいっぷハニーwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isWhipHoneyAlias(title)) return true
        }
        return false
    }

    fun isWhipHoneyGameDir(dir: File?): Boolean = dir?.isDirectory == true && isWhipHoneyGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isOnimusuAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("onimusu") || n.contains("オニむス") || n == "鬼女"
    }

    @JvmOverloads
    fun isOnimusuGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "オニむス.ain" || n == "オニむスga.ald" || n == "オニむスba.ald" || n == "オニむスwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isOnimusuAlias(title)) return true
        }
        return false
    }

    fun isOnimusuGameDir(dir: File?): Boolean = dir?.isDirectory == true && isOnimusuGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isProGrxAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("progrx") || n.contains("pro_grx") || n.contains("pro grx") ||
            n.contains("ぷろgrx") || n.contains("职业grx") || n.contains("職業grx")
    }

    @JvmOverloads
    fun isProGrxGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ぷろgrx.ain" || n == "ぷろgrxga.ald" || n == "ぷろgrxba.ald" || n == "ぷろgrxwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isProGrxAlias(title)) return true
        }
        return false
    }

    fun isProGrxGameDir(dir: File?): Boolean = dir?.isDirectory == true && isProGrxGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isDpsVerNAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpsvern") || n.contains("dps_ver_n") || n.contains("dps ver_n") ||
            n.contains("d.p.s. ver_n") || n.contains("d.p.s.ver_n") || n.contains("でーぴーえす")
    }

    @JvmOverloads
    fun isDpsVerNGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "でーぴーえすver_n.ain" || n == "でーぴーえすver_nga.ald" || n == "でーぴーえすver_nba.ald" || n == "でーぴーえすver_nwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDpsVerNAlias(title)) return true
        }
        return false
    }

    fun isDpsVerNGameDir(dir: File?): Boolean = dir?.isDirectory == true && isDpsVerNGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isSukiironoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("sukiirono") || n.contains("sukiiro") || n.contains("透色")
    }

    @JvmOverloads
    fun isSukiironoGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "透色.ain" || n == "透色ga.ald" || n == "透色ba.ald" || n == "透色wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isSukiironoAlias(title)) return true
        }
        return false
    }

    fun isSukiironoGameDir(dir: File?): Boolean = dir?.isDirectory == true && isSukiironoGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isGirlsAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw).trim()
        return Regex("(?<![a-z0-9])girls(?![a-z0-9])").containsMatchIn(n) ||
            n.contains("girls.ain") || n.contains("girlsga.ald")
    }

    @JvmOverloads
    fun isGirlsGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "girls.ain" || n == "girlsga.ald" || n == "girlsba.ald" || n == "girlswa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isGirlsAlias(title)) return true
        }
        return false
    }

    fun isGirlsGameDir(dir: File?): Boolean = dir?.isDirectory == true && isGirlsGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isDebutAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw).trim()
        return Regex("(?<![a-z0-9])debut(?![a-z0-9])").containsMatchIn(n) ||
            n.contains("debut.ain") || n.contains("debutga.ald")
    }

    @JvmOverloads
    fun isDebutGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "debut.ain" || n == "debutga.ald" || n == "debutba.ald" || n == "debutwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDebutAlias(title)) return true
        }
        return false
    }

    fun isDebutGameDir(dir: File?): Boolean = dir?.isDirectory == true && isDebutGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isHarukaVsEscaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("harukavsesca") || n.contains("haruka_vs_esca") ||
            n.contains("haruka vs escalayer") || n.contains("haruka vs esca") ||
            n.contains("ハルカvs") || n.contains("ハルカ vs") ||
            n.contains("遥vs艾斯卡") || n.contains("遙vs艾斯卡") ||
            ((n.contains("ハルカ") || n.contains("haruka") || n.contains("闪忍遥") || n.contains("閃忍遙")) &&
                (n.contains("エスカレイヤー") || n.contains("escalayer") || n.contains("艾斯卡")))
    }

    @JvmOverloads
    fun isHarukaVsEscaGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ハルカvs.ain" || n == "ハルカvsga.ald" || n == "ハルカvsgb.ald" || n == "ハルカvsba.ald" || n == "ハルカvswa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isHarukaVsEscaAlias(title)) return true
        }
        return false
    }

    fun isHarukaVsEscaGameDir(dir: File?): Boolean = dir?.isDirectory == true && isHarukaVsEscaGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isHarukaNinjaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("harukaninja") || n.contains("haruka_ninja") ||
            n.contains("shippuu!? ninja") || n.contains("ninja dai sakusen") ||
            n.contains("疾風!?忍者大作戦") || n.contains("疾风!?忍者大作战") ||
            n.contains("疾風!?忍者大作戰") || n.contains("忍者大作戦") || n.contains("忍者大作战")
    }

    @JvmOverloads
    fun isHarukaNinjaGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "疾風!?忍者大作戦.ain" || n == "疾風!?忍者大作戦ga.ald" ||
                    n == "疾風!?忍者大作戦gb.ald" || n == "疾風!?忍者大作戦ba.ald" || n == "疾風!?忍者大作戦wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isHarukaNinjaAlias(title)) return true
        }
        return false
    }

    fun isHarukaNinjaGameDir(dir: File?): Boolean = dir?.isDirectory == true && isHarukaNinjaGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isMeganeAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("megane no mei") || Regex("(?<![a-z0-9])megane(?![a-z0-9])").containsMatchIn(n) ||
            n.contains("眼鏡のメイ") || n.contains("眼镜梅与暑假") || n.contains("眼鏡梅與暑假")
    }

    @JvmOverloads
    fun isMeganeGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "眼鏡のメイと夏休み.ain" || n == "眼鏡のメイと夏休みga.ald" ||
                    n == "眼鏡のメイと夏休みba.ald" || n == "眼鏡のメイと夏休みwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMeganeAlias(title)) return true
        }
        return false
    }

    fun isMeganeGameDir(dir: File?): Boolean = dir?.isDirectory == true && isMeganeGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isRachelSakuraAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("rachelsakura") || n.contains("rachel_sakura") ||
            n.contains("rachell no sakuragai") || n.contains("rachel no sakuragai") ||
            n.contains("レイチェルの桜貝") || n.contains("蕾切尔的樱贝") || n.contains("蕾切爾的櫻貝")
    }

    @JvmOverloads
    fun isRachelSakuraGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "レイチェルの桜貝.ain" || n == "レイチェルの桜貝ga.ald" ||
                    n == "レイチェルの桜貝ba.ald" || n == "レイチェルの桜貝wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isRachelSakuraAlias(title)) return true
        }
        return false
    }

    fun isRachelSakuraGameDir(dir: File?): Boolean = dir?.isDirectory == true && isRachelSakuraGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isSabakuNikaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("sabakunika") || n.contains("sabaku_nika") || n.contains("sabaku no nikka") ||
            n.contains("砂漠のニッカ") || n.contains("沙漠的妮卡")
    }

    @JvmOverloads
    fun isSabakuNikaGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "砂漠のニッカ.ain" || n == "砂漠のニッカga.ald" ||
                    n == "砂漠のニッカba.ald" || n == "砂漠のニッカwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isSabakuNikaAlias(title)) return true
        }
        return false
    }

    fun isSabakuNikaGameDir(dir: File?): Boolean = dir?.isDirectory == true && isSabakuNikaGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isVanishPalmAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("vanishpalm") || n.contains("vanish_palm") ||
            n.contains("この手のひらにおっぱい") || n.contains("この手のひら") ||
            n.contains("kono te no hira") || n.contains("ばにしゅ!番外") ||
            n.contains("掌中奇缘") || n.contains("掌中奇緣")
    }

    @JvmOverloads
    fun isVanishPalmGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ばにしゅ!番外.ain" || n == "ばにしゅ!番外ga.ald" ||
                    n == "ばにしゅ!番外ba.ald" || n == "ばにしゅ!番外wa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isVanishPalmAlias(title)) return true
        }
        return false
    }

    fun isVanishPalmGameDir(dir: File?): Boolean = dir?.isDirectory == true && isVanishPalmGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    // --- Wave 5: System 4 CN (3) + Official System 3.x (7) + Classic System 1-3 (12) + Alice CD Bonus (8) ---

    fun isBladeBridersAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("bladebriders") || n.contains("blade_briders") || n.contains("blade briders") ||
            n.contains("blade☆briders") || n.contains("burebura") || n.contains("ぶれいど") ||
            n.contains("ブライダーズ") || n.contains("武想少女隊") || n.contains("武想少女队")
    }

    @JvmOverloads
    fun isBladeBridersGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "burebura.ain" || n == "burebura.ex" || n == "bureburacg.afa" ||
                    n == "bureburawa.afa" || n == "bureburaflat.afa"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isBladeBridersAlias(title)) return true
        }
        return false
    }

    fun isBladeBridersGameDir(dir: File?): Boolean = dir?.isDirectory == true && isBladeBridersGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isDorapekoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dorapeko") || n.contains("drapeko") || n.contains("どらぺこ") ||
            n.contains("おねだりドラゴン") || n.contains("饥饿龙娘") || n.contains("飢餓龍娘")
    }

    @JvmOverloads
    fun isDorapekoGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "どらぺこ!.ain" || n == "どらぺこ!.ex" || n == "どらぺこ!cg.afa" ||
                    n == "どらぺこ!aff.afa" || n == "どらぺこ!wa.ald" || n == "drapeko.ain"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isDorapekoAlias(title)) return true
        }
        return false
    }

    fun isDorapekoGameDir(dir: File?): Boolean = dir?.isDirectory == true && isDorapekoGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isHaniwaPetitAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("haniwapetit") || n.contains("haniwa_petit") || n.contains("haniwa petit") ||
            n.contains("ハニワ闇開発室") || n.contains("闇開発室ぷち") ||
            n.contains("哈尼瓦暗黑开发室") || n.contains("哈尼瓦闇開發室")
    }

    @JvmOverloads
    fun isHaniwaPetitGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ハニワ闇開発室ぷち.ain" || n == "ハニワ闇開発室ぷちcg.afa" ||
                    n == "ハニワ闇開発室ぷちba.ald" || n == "ハニワ闇開発室ぷちwa.ald"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isHaniwaPetitAlias(title)) return true
        }
        return false
    }

    fun isHaniwaPetitGameDir(dir: File?): Boolean = dir?.isDirectory == true && isHaniwaPetitGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isMomoiroGuardianAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("momoiroguardian") || n.contains("momoiro_guardian") || n.contains("momoiro guardian") ||
            n.contains("ももいろガーディアン") || n.contains("桃色守护者") || n.contains("桃色守護者") ||
            n.contains("ももんが") || n.contains("momonga")
    }

    @JvmOverloads
    fun isMomoiroGuardianGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!hasSystem4Markers(fileNames)) return false
        if (fileNames.any {
                val n = nfkcLower(it)
                n == "ももんが.ain" || n == "ももんがga.ald" || n == "ももんがwa.ald" ||
                    n == "ももんがba.ald" || n == "momonga.inc" || n == "momonga.ain"
            }
        ) return true
        val iniName = fileNames.firstOrNull { isSystem4LaunchIni(it) }
        if (iniName != null && readSmallFile != null) {
            val title = runCatching { readSmallFile(iniName)?.let { System40Ini.parse(it).gameName } }.getOrNull().orEmpty()
            if (title.isNotEmpty() && isMomoiroGuardianAlias(title)) return true
        }
        return false
    }

    fun isMomoiroGuardianGameDir(dir: File?): Boolean = dir?.isDirectory == true && isMomoiroGuardianGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isRance1ClassicAlias(raw: String): Boolean {
        if (raw.isBlank() || isRance1DigestAlias(raw)) return false
        val n = nfkcLower(raw)
        if (n.contains("rance01") || n.contains("rance 01") || n.contains("兰斯01") || n.contains("ランス01")) return false
        return n.contains("rance1classic") || n.contains("rance1_classic") || n.contains("rance 1 classic") ||
            n.contains("rancesa.ald") || n.contains("rancega.ald") ||
            n.contains("兰斯1原版") || n.contains("兰斯1 原版") ||
            n.contains("兰斯1(原版)") || n.contains("兰斯1（原版）") ||
            Regex("(?<![a-z0-9])(?:rance|兰斯|蘭斯|ランス)\\s*i(?![a-z0-9])").containsMatchIn(n) ||
            ((n.contains("光を求めて") || n.contains("光をもとめて") || n.contains("寻找小光") || n.contains("hikari o motomete")) &&
                (n.contains("原版") || n.contains("classic") || n.contains("怀旧")))
    }

    @JvmOverloads
    fun isRance1ClassicGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (hasSystem4Markers(fileNames)) return false
        if (isSystem3xGameFiles(fileNames) && fileNames.any { it.equals("RANCESA.ALD", true) }) return true
        if (isLegacySystem3GameFiles(fileNames) && readSmallFile != null) {
            val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
            if (iniName != null) {
                val game = legacySystem3GameId(runCatching { readSmallFile(iniName) }.getOrNull())
                if (game == "rance") return true
                if (game != null) return false
            }
            val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
            if (adiskName != null) {
                val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
                if (crc != null && crc in RANCE1CLASSIC_ADISK_CRC32S) return true
            }
        }
        return false
    }

    fun isRance1ClassicGameDir(dir: File?): Boolean = dir?.isDirectory == true && isRance1ClassicGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isRance2ClassicAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        if (n.contains("rance02") || n.contains("rance 02") || n.contains("兰斯02") || n.contains("ランス02")) return false
        return n.contains("rance2classic") || n.contains("rance2_classic") || n.contains("rance 2 classic") ||
            n.contains("rance2sa.ald") || n.contains("rance2ga.ald") ||
            n.contains("兰斯2原版") || n.contains("兰斯2 原版") ||
            n.contains("兰斯2(原版)") || n.contains("兰斯2（原版）") ||
            Regex("(?<![a-z0-9])(?:rance|兰斯|蘭斯|ランス)\\s*ii(?![a-z0-9])").containsMatchIn(n) ||
            ((n.contains("反逆の少女") || n.contains("反叛的少女") || n.contains("hangyaku no shoujo")) &&
                (n.contains("原版") || n.contains("classic") || n.contains("怀旧")))
    }

    @JvmOverloads
    fun isRance2ClassicGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (hasSystem4Markers(fileNames)) return false
        if (isSystem3xGameFiles(fileNames) && fileNames.any { it.equals("RANCE2SA.ALD", true) }) return true
        if (isLegacySystem3GameFiles(fileNames) && readSmallFile != null) {
            val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
            if (iniName != null) {
                val game = legacySystem3GameId(runCatching { readSmallFile(iniName) }.getOrNull())
                if (game == "rance2") return true
                if (game != null) return false
            }
            val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
            if (adiskName != null) {
                val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
                if (crc != null && crc in RANCE2CLASSIC_ADISK_CRC32S) return true
            }
        }
        return false
    }

    fun isRance2ClassicGameDir(dir: File?): Boolean = dir?.isDirectory == true && isRance2ClassicGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isRance3ClassicAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        if (n.contains("rance03") || n.contains("rance 03") || n.contains("兰斯03") || n.contains("ランス03")) return false
        return n.contains("rance3classic") || n.contains("rance3_classic") || n.contains("rance 3 classic") ||
            n.contains("rance3sa.ald") || n.contains("rance3ga.ald") ||
            n.contains("兰斯3原版") || n.contains("兰斯3 原版") ||
            n.contains("兰斯3(原版)") || n.contains("兰斯3（原版）") ||
            Regex("(?<![a-z0-9])(?:rance|兰斯|蘭斯|ランス)\\s*iii(?![a-z0-9])").containsMatchIn(n) ||
            ((n.contains("リーザス") || n.contains("利萨斯") || n.contains("leazas kanraku")) &&
                (n.contains("原版") || n.contains("classic") || n.contains("怀旧")))
    }

    @JvmOverloads
    fun isRance3ClassicGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (hasSystem4Markers(fileNames)) return false
        if (isSystem3xGameFiles(fileNames) && fileNames.any { it.equals("RANCE3SA.ALD", true) }) return true
        if (isLegacySystem3GameFiles(fileNames) && readSmallFile != null) {
            val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
            if (iniName != null) {
                val game = legacySystem3GameId(runCatching { readSmallFile(iniName) }.getOrNull())
                if (game == "rance3") return true
                if (game != null) return false
            }
            val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
            if (adiskName != null) {
                val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
                if (crc != null && crc in RANCE3CLASSIC_ADISK_CRC32S) return true
            }
        }
        return false
    }

    fun isRance3ClassicGameDir(dir: File?): Boolean = dir?.isDirectory == true && isRance3ClassicGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    private fun isDpsSgScenarioGameFiles(
        fileNames: Collection<String>,
        readSmallFile: ((String) -> ByteArray?)?,
        expectedIds: Set<String>,
        expectedAdiskCrcs: Set<Long>,
        expectedBdiskCrcs: Set<Long>,
        aliasCheck: (String) -> Boolean
    ): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game != null) return game in expectedIds
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && aliasCheck(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        val bdiskName = fileNames.firstOrNull { it.equals("BDISK.DAT", true) }
        if (adiskName != null && bdiskName != null && readSmallFile != null) {
            val acrc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            val bcrc = legacyAdiskCrc32(runCatching { readSmallFile(bdiskName) }.getOrNull())
            if (acrc != null && acrc in expectedAdiskCrcs && bcrc != null && bcrc in expectedBdiskCrcs) return true
        }
        return false
    }

    fun isDpsSgFahrenAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssgfahren") || n.contains("dps_sg_fahren") || n.contains("fahren fliegen") ||
            n.contains("fahnen fliegen") || n.contains("飞翔的梦") || n.contains("飛翔的夢")
    }

    @JvmOverloads
    fun isDpsSgFahrenGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg_fahren", "dpssgfahren"), DPSSG1_BASE_ADISK_CRC32S, DPSSGFAHREN_BDISK_CRC32S, ::isDpsSgFahrenAlias)

    fun isDpsSgKateiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssgkatei") || n.contains("dps_sg_katei") || n.contains("家庭教師はステキなお仕事") ||
            n.contains("家庭教师是美妙的工作") || n.contains("家庭教師是美妙的工作") || n.contains("kateikyoushi")
    }

    @JvmOverloads
    fun isDpsSgKateiGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg_katei", "dpssgkatei"), DPSSG1_BASE_ADISK_CRC32S, DPSSGKATEI_BDISK_CRC32S, ::isDpsSgKateiAlias)

    fun isDpsSgNobunagaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssgnobunaga") || n.contains("dps_sg_nobunaga") || n.contains("信長の淫謀") ||
            n.contains("信长的淫谋") || n.contains("信長的淫謀") || n.contains("nobunaga no inbou")
    }

    @JvmOverloads
    fun isDpsSgNobunagaGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg_nobunaga", "dpssgnobunaga"), DPSSG1_BASE_ADISK_CRC32S, DPSSGNOBUNAGA_BDISK_CRC32S, ::isDpsSgNobunagaAlias)

    fun isDpsSg2AntiqueAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssg2antique") || n.contains("dps_sg2_antique") || n.contains("antique house") ||
            n.contains("antiquehouse") || n.contains("古董屋")
    }

    @JvmOverloads
    fun isDpsSg2AntiqueGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg2_antique", "dpssg2antique"), DPSSG1_BASE_ADISK_CRC32S, DPSSG2ANTIQUE_BDISK_CRC32S, ::isDpsSg2AntiqueAlias)

    fun isDpsSg2IkenaiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssg2ikenai") || n.contains("dps_sg2_ikenai") || n.contains("いけない内科検診") ||
            n.contains("不妙的内科体检") || n.contains("不妙的內科體檢") || n.contains("naika kenshin")
    }

    @JvmOverloads
    fun isDpsSg2IkenaiGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg2_ikenai", "dpssg2ikenai"), DPSSG1_BASE_ADISK_CRC32S, DPSSG2IKENAI_BDISK_CRC32S, ::isDpsSg2IkenaiAlias)

    fun isDpsSg2AkaiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssg2akai") || n.contains("dps_sg2_akai") || n.contains("朱い夜") ||
            n.contains("朱红之夜") || n.contains("朱紅之夜") || n.contains("akai yoru")
    }

    @JvmOverloads
    fun isDpsSg2AkaiGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg2_akai", "dpssg2akai"), DPSSG1_BASE_ADISK_CRC32S, DPSSG2AKAI_BDISK_CRC32S, ::isDpsSg2AkaiAlias)

    fun isDpsSg3RabbitAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssg3rabbit") || n.contains("dps_sg3_rabbit") || n.contains("rabbit p4p") ||
            n.contains("rabbitp4p")
    }

    @JvmOverloads
    fun isDpsSg3RabbitGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg3_rabbit", "dpssg3rabbit"), DPSSG3_BASE_ADISK_CRC32S, DPSSG3RABBIT_BDISK_CRC32S, ::isDpsSg3RabbitAlias)

    fun isDpsSg3ShinkonAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssg3shinkon") || n.contains("dps_sg3_shinkon") || n.contains("しんこんさんものがたり") ||
            n.contains("新婚物语") || n.contains("新婚物語") || n.contains("shinkon-san") || n.contains("shinkon san")
    }

    @JvmOverloads
    fun isDpsSg3ShinkonGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg3_shinkon", "dpssg3shinkon"), DPSSG3_BASE_ADISK_CRC32S, DPSSG3SHINKON_BDISK_CRC32S, ::isDpsSg3ShinkonAlias)

    fun isDpsSg3SotsugyouAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dpssg3sotsugyou") || n.contains("dps_sg3_sotsugyou") || n.contains("dps sg set3 - sotsugyou") ||
            n.contains("d.p.s. sg set3 - sotsugyou") || n.contains("d.p.s. sg set3 毕业") || n.contains("d.p.s. sg set3 畢業") ||
            n.contains("d.p.s. sg set3 －卒業－") || n == "卒業" || n == "sotsugyou"
    }

    @JvmOverloads
    fun isDpsSg3SotsugyouGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean =
        isDpsSgScenarioGameFiles(fileNames, readSmallFile, setOf("dps_sg3_sotsugyou", "dpssg3sotsugyou"), DPSSG3_BASE_ADISK_CRC32S, DPSSG3SOTSUGYOU_BDISK_CRC32S, ::isDpsSg3SotsugyouAlias)

    fun isYakata456Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("yakata456") || n.contains("yakata_456") || n.contains("456_sa.ald") ||
            n.contains("アリスの館4") || n.contains("aliceの館4") ||
            n.contains("爱丽丝之馆4") || n.contains("愛麗絲之館4")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isYakata456GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "456_sa.ald" || n == "456_ga.ald" || n == "456_ma.ald"
        }
    }

    fun isYakata456GameDir(dir: File?): Boolean = dir?.isDirectory == true && isYakata456GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isUmiNiOchiteAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("uminiochite") || n.contains("umi_ni_ochite") || n.contains("umi ni ochite") ||
            n.contains("蒼海に堕ちて") || n.contains("蒼海sa.ald") ||
            n.contains("坠入苍海") || n.contains("墜入蒼海")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isUmiNiOchiteGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "蒼海sa.ald" || n == "蒼海ga.ald"
        }
    }

    fun isUmiNiOchiteGameDir(dir: File?): Boolean = dir?.isDirectory == true && isUmiNiOchiteGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isGakuenHyouryuu2Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("gakuenhyouryuu2") || n.contains("gakuen_hyouryuu2") || n.contains("gakuenhyouryuusenki2") ||
            n.contains("学園漂流戦記第2") || n.contains("学園漂流戦記 第2") || n.contains("学園漂流戦記2") ||
            n.contains("学园漂流战记第2") || n.contains("学园漂流战记 第2") || n.contains("学园漂流战记2") ||
            n.contains("學園漂流戰記第2") || n.contains("戦記2sa.ald")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isGakuenHyouryuu2GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "戦記2sa.ald" || n == "戦記2ga.ald"
        }
    }

    fun isGakuenHyouryuu2GameDir(dir: File?): Boolean = dir?.isDirectory == true && isGakuenHyouryuu2GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isYouseiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n == "yousei" || n.startsWith("yousei-") || n.startsWith("yousei_") ||
            n == "妖精" || n.contains("妖精sa.ald") || n.contains("妖精ga.ald")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isYouseiGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "妖精sa.ald" || n == "妖精ga.ald"
        }
    }

    fun isYouseiGameDir(dir: File?): Boolean = dir?.isDirectory == true && isYouseiGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isTobiraAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n == "tobira" || n.contains("-tobira-") || n.contains("[扉]") ||
            n.contains("[扉]sa.ald") || n.contains("[扉]ga.ald")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isTobiraGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "[扉]sa.ald" || n == "[扉]ga.ald"
        }
    }

    fun isTobiraGameDir(dir: File?): Boolean = dir?.isDirectory == true && isTobiraGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isBuroburoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("buroburo") || n.contains("ぶろぶろ") || n.contains("壁sa.ald") ||
            n.contains("噗咯噗咯")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isBuroburoGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "壁sa.ald" || n == "壁ga.ald"
        }
    }

    fun isBuroburoGameDir(dir: File?): Boolean = dir?.isDirectory == true && isBuroburoGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isKudakeroAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("kudakero") || n.contains("当たって!!kudakero") || n.contains("当砕sa.ald") ||
            n.contains("撞碎吧")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isKudakeroGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "当砕sa.ald" || n == "当砕ga.ald"
        }
    }

    fun isKudakeroGameDir(dir: File?): Boolean = dir?.isDirectory == true && isKudakeroGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    private fun readLegacyDiskPrefix(dir: File, name: String): ByteArray? {
        val f = dir.listFiles()?.firstOrNull { it.isFile && it.name.equals(name, true) } ?: return null
        return when {
            name.equals("ADISK.DAT", true) || name.equals("BDISK.DAT", true) -> readFilePrefix(f, 256)
            f.length() <= 256L * 1024L -> runCatching { f.readBytes() }.getOrNull()
            else -> null
        }
    }

    fun isLittleVampireAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("littlevampire") || n.contains("little_vampire") || n.contains("little vampire") ||
            n.contains("リトルヴァンパイア") || n.contains("リトル ヴァンパイア") || n.contains("小吸血鬼")
    }

    @JvmOverloads
    fun isLittleVampireGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "vampire" || game == "little_vampire" || game == "littlevampire") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isLittleVampireAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in LITTLEVAMPIRE_ADISK_CRC32S) return true
        }
        return false
    }

    fun isLittleVampireGameDir(dir: File?): Boolean = dir?.isDirectory == true && isLittleVampireGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isIntruderAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("intruder") || n.contains("桜屋敷の探索") || n.contains("樱屋敷的探索") ||
            n.contains("櫻屋敷的探索")
    }

    @JvmOverloads
    fun isIntruderGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "intruder") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isIntruderAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in INTRUDER_ADISK_CRC32S) return true
        }
        return false
    }

    fun isIntruderGameDir(dir: File?): Boolean = dir?.isDirectory == true && isIntruderGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isCrescentAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("crescentmoongirl") || n.contains("crescent moon") || n == "crescent" ||
            n.contains("クレセントムーン") || n.contains("新月少女")
    }

    @JvmOverloads
    fun isCrescentGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "crescent") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isCrescentAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in CRESCENT_ADISK_CRC32S) return true
        }
        return false
    }

    fun isCrescentGameDir(dir: File?): Boolean = dir?.isDirectory == true && isCrescentGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isTenguAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("abunaitengu") || n.contains("abunai tengu") || n == "tengu" ||
            n.contains("あぶないてんぐ") || n.contains("あぶない天狗") ||
            n.contains("危险的天狗") || n.contains("危險的天狗")
    }

    @JvmOverloads
    fun isTenguGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "tengu" || game == "abunai_tengu") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isTenguAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in TENGU_ADISK_CRC32S) return true
        }
        return false
    }

    fun isTenguGameDir(dir: File?): Boolean = dir?.isDirectory == true && isTenguGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isDpsAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        if (n.contains("super") || n.contains("sdps") || n.contains("全部") || n.contains("zenbu") ||
            n.contains("これ") || n.contains("这是") || n.contains("這是") || n.contains("ver_n") ||
            n.contains("vern") || n.contains("dpsall") || n.contains("koredps") ||
            n.contains("dpssg") || n.contains("dps_sg") || n.contains("dps sg") ||
            n.contains("d.p.s. sg") || n.contains("d.p.s sg")
        ) return false
        return n == "dps" || n == "d.p.s." || n == "d.p.s" ||
            n.contains("dream program system") || n.contains("梦幻程序系统") || n.contains("夢幻程序系統")
    }

    @JvmOverloads
    fun isDpsGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "dps") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isDpsAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in DPS_ADISK_CRC32S) return true
        }
        return false
    }

    fun isDpsGameDir(dir: File?): Boolean = dir?.isDirectory == true && isDpsGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isYakata1Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        if (n.contains("館2") || n.contains("館ii") || n.contains("館3") || n.contains("館iii") ||
            n.contains("館4") || n.contains("館7") || n.contains("yakata2") || n.contains("yakata3") ||
            n.contains("yakata456") || n.contains("yakata7") || n.contains("之馆2") || n.contains("之馆3") ||
            n.contains("之馆4") || n.contains("之館2") || n.contains("之館3") || n.contains("之館4")
        ) return false
        return n == "yakata1" || n == "yakata" || n == "aliceの館" || n == "アリスの館" ||
            n == "alice no yakata" || n == "爱丽丝之馆" || n == "愛麗絲之館" ||
            n.contains("aliceの館1") || n.contains("爱丽丝之馆1")
    }

    @JvmOverloads
    fun isYakata1GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "yakata" || game == "yakata1") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isYakata1Alias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in YAKATA1_ADISK_CRC32S) return true
        }
        return false
    }

    fun isYakata1GameDir(dir: File?): Boolean = dir?.isDirectory == true && isYakata1GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isSdpsTonoAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("sdpstono") || n.contains("sdps_tono") || n.contains("superdpstono") ||
            n.contains("遠野の森") || n.contains("远野之森") || n.contains("遠野之森") ||
            n.contains("tono no mori")
    }

    @JvmOverloads
    fun isSdpsTonoGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "sdps_tono" || game == "sdpstono") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isSdpsTonoAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        val bdiskName = fileNames.firstOrNull { it.equals("BDISK.DAT", true) }
        if (adiskName != null && bdiskName != null && readSmallFile != null) {
            val acrc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            val bcrc = legacyAdiskCrc32(runCatching { readSmallFile(bdiskName) }.getOrNull())
            if (acrc != null && acrc in SDPS_BASE_ADISK_CRC32S && bcrc != null && bcrc in SDPSTONO_BDISK_CRC32S) return true
        }
        return false
    }

    fun isSdpsTonoGameDir(dir: File?): Boolean = dir?.isDirectory == true && isSdpsTonoGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isSdpsKaizokuAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("sdpskaizoku") || n.contains("sdps_kaizoku") || n.contains("superdpskaizoku") ||
            n.contains("海賊稼業") || n.contains("海盗生涯") || n.contains("海盜生涯") ||
            n.contains("kaizoku kagyou")
    }

    @JvmOverloads
    fun isSdpsKaizokuGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "sdps_kaizoku" || game == "sdpskaizoku") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isSdpsKaizokuAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        val bdiskName = fileNames.firstOrNull { it.equals("BDISK.DAT", true) }
        if (adiskName != null && bdiskName != null && readSmallFile != null) {
            val acrc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            val bcrc = legacyAdiskCrc32(runCatching { readSmallFile(bdiskName) }.getOrNull())
            if (acrc != null && acrc in SDPS_BASE_ADISK_CRC32S && bcrc != null && bcrc in SDPSKAIZOKU_BDISK_CRC32S) return true
        }
        return false
    }

    fun isSdpsKaizokuGameDir(dir: File?): Boolean = dir?.isDirectory == true && isSdpsKaizokuGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isProgOmakeAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("progomake") || n.contains("prog_omake") || n.contains("prostudentgomake") ||
            n.contains("prostudent g omake") || n.contains("ぷろすちゅーでんとg おまけ") ||
            n.contains("职业学生g 附赠") || n.contains("职业学生g附赠") || n.contains("職業學生g 附贈")
    }

    @JvmOverloads
    fun isProgOmakeGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "prog_omake" || game == "progomake") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isProgOmakeAlias(title)) return true
        }
        return false
    }

    fun isProgOmakeGameDir(dir: File?): Boolean = dir?.isDirectory == true && isProgOmakeGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isAyumiHintAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("ayumihint") || n.contains("ayumi_hint") || n.contains("ayumi hint") ||
            n.contains("ヒントディスク") || n.contains("步美物语 提示") || n.contains("步美物语提示") ||
            n.contains("步美物語 提示")
    }

    @JvmOverloads
    fun isAyumiHintGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "ayumi_hint" || game == "ayumihint") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isAyumiHintAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in AYUMIHINT_ADISK_CRC32S) return true
        }
        return false
    }

    fun isAyumiHintGameDir(dir: File?): Boolean = dir?.isDirectory == true && isAyumiHintGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isAyumiLiveAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("ayumilive") || n.contains("ayumi_live") || n.contains("ayumi live") ||
            n.contains("実写版") || n.contains("jisshaban") || n.contains("live action") ||
            n.contains("步美物语 真人") || n.contains("步美物语真人") || n.contains("步美物語 真人")
    }

    @JvmOverloads
    fun isAyumiLiveGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "ayumi_live_256" || game == "ayumi_live_full" || game == "ayumi_live" || game == "ayumilive") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isAyumiLiveAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in AYUMILIVE_ADISK_CRC32S) return true
        }
        return fileNames.any { it.equals("ACG3.DAT", true) } && fileNames.any { it.equals("ADISK.DAT", true) }
    }

    fun isAyumiLiveGameDir(dir: File?): Boolean = dir?.isDirectory == true && isAyumiLiveGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isNiseNaguriAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("nisenaguri") || n.contains("nise_naguri") || n.contains("nise naguri") ||
            n.contains("にせなぐり") || n.contains("偽殴打") || n.contains("伪殴打")
    }

    @JvmOverloads
    fun isNiseNaguriGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isLegacySystem3GameFiles(fileNames)) return false
        val iniName = fileNames.firstOrNull { it.equals("system3.ini", true) }
        if (iniName != null && readSmallFile != null) {
            val raw = runCatching { readSmallFile(iniName) }.getOrNull()
            val game = legacySystem3GameId(raw)
            if (game == "nise_naguri" || game == "nisenaguri") return true
            if (game != null) return false
            val title = legacySystem3Title(raw)
            if (!title.isNullOrBlank() && isNiseNaguriAlias(title)) return true
        }
        val adiskName = fileNames.firstOrNull { it.equals("ADISK.DAT", true) }
        if (adiskName != null && readSmallFile != null) {
            val crc = legacyAdiskCrc32(runCatching { readSmallFile(adiskName) }.getOrNull())
            if (crc != null && crc in NISENAGURI_ADISK_CRC32S) return true
        }
        return false
    }

    fun isNiseNaguriGameDir(dir: File?): Boolean = dir?.isDirectory == true && isNiseNaguriGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readLegacyDiskPrefix(dir, it) }

    fun isCarolAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n == "carol" || n.startsWith("carol ") || n.startsWith("carol~") ||
            n.contains("キャロル") || n.contains("聖なる鐘が響く夜") ||
            n.contains("卡萝尔") || n.contains("圣钟鸣响之夜")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isCarolGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "キャロルsa.ald" || n == "キャロルga.ald"
        }
    }

    fun isCarolGameDir(dir: File?): Boolean = dir?.isDirectory == true && isCarolGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isOthellotAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("othellot") || n.contains("おせろっと") || n.contains("黑白棋娘")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isOthellotGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "おせろっとsa.ald" || n == "おせろっとga.ald"
        }
    }

    fun isOthellotGameDir(dir: File?): Boolean = dir?.isDirectory == true && isOthellotGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isMasqueradeAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("masquerade") || n.contains("マスカレイド") || n.contains("mqn_sa.ald") ||
            n.contains("假面舞会") || n.contains("假面舞會")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isMasqueradeGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "mqn_sa.ald" || n == "mqn_ga.ald"
        }
    }

    fun isMasqueradeGameDir(dir: File?): Boolean = dir?.isDirectory == true && isMasqueradeGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isDekirukanaAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("dekirukana") || n.contains("dekiru kana") || n.contains("できるかな") ||
            n.contains("できsa.ald") || n.contains("能做到吗") || n.contains("能做到嗎")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isDekirukanaGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "できsa.ald" || n == "できga.ald"
        }
    }

    fun isDekirukanaGameDir(dir: File?): Boolean = dir?.isDirectory == true && isDekirukanaGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isTatakikoroshiAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("tatakikoroshi") || n.contains("たたき殺し") || n.contains("叩き殺し") ||
            n.contains("敲杀太郎") || n.contains("敲殺太郎")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isTatakikoroshiGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "叩き殺しsa.ald" || n == "叩き殺しga.ald"
        }
    }

    fun isTatakikoroshiGameDir(dir: File?): Boolean = dir?.isDirectory == true && isTatakikoroshiGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isBalloonPopAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("balloonpop") || n.contains("balloon pop") || n.contains("ばるーんぽっぷ") ||
            n.contains("風船_sa.ald") || n.contains("气球爆破") || n.contains("氣球爆破")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isBalloonPopGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "風船_sa.ald" || n == "風船_ga.ald"
        }
    }

    fun isBalloonPopGameDir(dir: File?): Boolean = dir?.isDirectory == true && isBalloonPopGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isHiyoko2Alias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n.contains("hiyoko2") || n.contains("kousoku hiyoko") || n.contains("高速ひよこ") ||
            n.contains("高速小鸡") || n.contains("高速小雞")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isHiyoko2GameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any { it.equals("piyod.dat", true) } &&
            fileNames.any { it.equals("sa.ald", true) || it.equals("ga.ald", true) }
    }

    fun isHiyoko2GameDir(dir: File?): Boolean = dir?.isDirectory == true && isHiyoko2GameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    fun isSelfGameAlias(raw: String): Boolean {
        if (raw.isBlank()) return false
        val n = nfkcLower(raw)
        return n == "self" || n == "selfgame" || n == "self_game" ||
            n.contains("selfsa.ald") || n.contains("selfga.ald")
    }

    @JvmOverloads
    @Suppress("UNUSED_PARAMETER")
    fun isSelfGameFiles(fileNames: Collection<String>, readSmallFile: ((String) -> ByteArray?)? = null): Boolean {
        if (!isSystem3xGameFiles(fileNames)) return false
        return fileNames.any {
            val n = nfkcLower(it)
            n == "selfsa.ald" || n == "selfga.ald"
        }
    }

    fun isSelfGameDir(dir: File?): Boolean = dir?.isDirectory == true && isSelfGameFiles(dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()) { readSmallRootFile(dir, it) }

    /** Strips any trailing overlay badge tag ([MOD ...] / [汉化 ...]) from a game's display name. */
    fun cleanItemTitle(rawName: String): String {
        val cut = listOf(" [MOD", " [汉化")
            .map { rawName.indexOf(it) }
            .filter { it > 0 }
            .minOrNull()
        return if (cut != null) rawName.substring(0, cut) else rawName
    }

    private fun classifyByGameDirOnly(gameDir: File?, dirName: String): String? {
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null) {
                classifyGameRootDir(gameDir)?.let { return it }
                val fileNames = files.filter { it.isFile }.map { it.name }
                if (fileNames.any { isSystem4CodeFile(it, fileNames) { n -> readSmallRootFile(gameDir, n) } }) {
                    return "$PREFIX_CUSTOM$dirName"
                }
                if (isSystem3xGameDir(gameDir)) {
                    return "$PREFIX_CUSTOM$dirName"
                }
            }
        }
        return null
    }

    fun classifyItem(item: Item): String {
        item.cachedClassifiedId?.let { return it }
        val gameDirResult = classifyByGameDirOnly(item.path, item.path.name)
        if (gameDirResult != null) {
            item.cachedClassifiedId = gameDirResult
            return gameDirResult
        }
        val resolved = classifyDirectory(
            gameDir = item.path,
            dirName = item.path.name,
            saveDir = item.savedir,
            displayName = item.name
        )
        // Only cache classifications that do not depend on saveDir markers, since
        // first-launch saves in item.savedir can refine ambiguous installations.
        if (item.savedir == null) {
            item.cachedClassifiedId = resolved
        }
        return resolved
    }

    fun classifyDirectory(
        gameDir: File?,
        dirName: String,
        saveDir: File? = null,
        displayName: String? = null
    ): String {
        classifyByGameDirOnly(gameDir, dirName)?.let { return it }

        if (saveDir != null && saveDir.isDirectory) {
            if (saveDir.listFiles()?.any {
                    it.isFile && (
                        it.name.equals("Esca.sav", ignoreCase = true) ||
                            it.name.lowercase().startsWith("escalayerreboot_")
                        )
                } == true &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return ID_ESCALAYER_REBOOT
            }
            if (saveDir.listFiles()?.any {
                    it.isFile && (
                        it.name.equals("Ixseal_Extra.sav", ignoreCase = true) ||
                            it.name.lowercase().startsWith("ixseal_")
                        )
                } == true &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return ID_IXSEAL
            }
            if (saveDir.listFiles()?.any {
                    it.isFile && (
                        it.name.equals("Maman_Extra.sav", ignoreCase = true) ||
                            it.name.equals("Maman_User.sav", ignoreCase = true) ||
                            it.name.lowercase().startsWith("maman_")
                        )
                } == true &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return ID_MAMAN
            }
            if (saveDir.listFiles()?.any {
                    it.isFile && isHarukaSystemSaveFileName(it.name)
                } == true &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                !File(saveDir, "CommonSystemData.sav").isFile &&
                !File(saveDir, "AFInfo.asd").isFile &&
                !File(saveDir, "AFConfig.asd").isFile &&
                !File(saveDir, "AFCommon.asd").isFile &&
                !File(saveDir, "3DSetting.asd").isFile &&
                !File(saveDir, "音楽モード.asd").isFile &&
                !File(saveDir, "壒妝儌乕僪.asd").isFile &&
                !File(saveDir, "顔ＣＧ").isDirectory &&
                !File(saveDir, "婄俠俧").isDirectory
            ) {
                return ID_HARUKA
            }
            if (saveDir.listFiles()?.any {
                    if (!it.isFile) return@any false
                    val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it.name)
                    TSUMASHIBORI_SAVE_SLOT_REGEX.matches(it.name) ||
                        TSUMASHIBORI_SAVE_SLOT_REGEX.matches(repaired)
                } == true &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return ID_TSUMASHIBORI
            }
            val saveFiles = saveDir.listFiles()?.filter { it.isFile }.orEmpty()
            val hasGlobalSave = saveFiles.any {
                it.name.equals("GlobalSave.asd", ignoreCase = true) ||
                    it.name.equals("GlobalSaveData.asd", ignoreCase = true)
            }
            if (!hasGlobalSave && saveFiles.any {
                    it.name.equals("Haharanman_Collection.sav", ignoreCase = true) ||
                        it.name.equals("Haharanman_User.sav", ignoreCase = true) ||
                        it.name.lowercase().startsWith("haharanman_")
                }
            ) {
                return ID_HAHARANMAN
            }
            if (!hasGlobalSave && saveFiles.any {
                    val lower = it.name.lowercase()
                    lower == "nightrmemmodeinfo.asd" ||
                        lower == "nightrcommongamedata.asd" ||
                        lower.startsWith("nightraf") ||
                        lower.startsWith("nightr_")
                }
            ) {
                return ID_YORUGAKURU
            }
            if (!hasGlobalSave && saveFiles.any {
                    val lower = ArchiveExtractor.repairSjisAsGbkMojibake(it.name).lowercase()
                    lower == "pc3.sav" ||
                        lower == "pc3user.sav" ||
                        lower == "pc3conf.sav" ||
                        lower == "pc3enquete.sav" ||
                        lower.startsWith("パステルチャイム３_") ||
                        lower.startsWith("パステルチャイム3_") ||
                        lower.endsWith("_pc3.sav") ||
                        lower.endsWith("_pc3user.sav") ||
                        lower.endsWith("_pc3conf.sav")
                }
            ) {
                return ID_PASCHA3
            }
            if (!hasGlobalSave && saveFiles.any {
                    it.name.lowercase().startsWith("tsuma3so_junai_")
                }
            ) {
                return ID_TSUMAMIGUI3JUN
            }
            if (!hasGlobalSave && saveFiles.any {
                    it.name.lowercase().startsWith("tsuma3so_netorare_")
                }
            ) {
                return ID_TSUMAMIGUI3NTR
            }
            if (!hasGlobalSave && saveFiles.any {
                    it.name.equals("Tuma3Extra.sav", ignoreCase = true) ||
                        it.name.equals("Tuma3User.sav", ignoreCase = true) ||
                        it.name.equals("Tuma3Enquete.sav", ignoreCase = true)
                }
            ) {
                return ID_TSUMAMIGUI3
            }
            if (!hasGlobalSave && saveFiles.any {
                    it.name.equals("MemModeInfo.asd", ignoreCase = true) ||
                        it.name.equals("CommonGameData.asd", ignoreCase = true)
                }
            ) {
                val hints = listOfNotNull(dirName, displayName)
                val looksNtr = hints.any { hint ->
                    if (isOkusamaJunAlias(hint)) return@any false
                    if (isOkusamaNtrAlias(hint)) return@any true
                    val lowerHint = ArchiveExtractor.repairSjisAsGbkMojibake(hint).lowercase()
                    lowerHint.contains("ntr") ||
                        lowerHint.contains("夺爱") ||
                        lowerHint.contains("奪愛") ||
                        lowerHint.contains("寝取")
                }
                return if (looksNtr) ID_OKUSAMANTR else ID_OKUSAMAJUN
            }
            val hasPrefixedE2Af = saveFiles.any { f ->
                val lowerRepaired = ArchiveExtractor.repairSjisAsGbkMojibake(f.name).lowercase()
                isEvenicle2Alias(lowerRepaired) && (
                    lowerRepaired.endsWith("afinfo.asd") ||
                        lowerRepaired.endsWith("afcgmode.asd") ||
                        lowerRepaired.endsWith("afbgmmode.asd") ||
                        lowerRepaired.endsWith("afmemmode.asd") ||
                        lowerRepaired.endsWith("afcommon.asd")
                    )
            }
            val hasE2AfMarker = saveFiles.any { f ->
                val lower = f.name.lowercase()
                lower.endsWith("afcommon.asd") ||
                    lower.endsWith("afconfig.asd") ||
                    lower.endsWith("afinfo.asd") ||
                    lower.endsWith("afcgmode.asd") ||
                    lower.endsWith("afbgmmode.asd") ||
                    lower.endsWith("afmemmode.asd")
            }
            val hasCommonOther = saveFiles.any { it.name.equals("CommonOtherData.sav", ignoreCase = true) }
            val has3DConfig = saveFiles.any { it.name.equals("3DConfig.vsf", ignoreCase = true) }
            if (!hasGlobalSave && (hasPrefixedE2Af || ((hasCommonOther || has3DConfig) && hasE2AfMarker))) {
                return ID_EVENICLE2
            }
            if (File(saveDir, "AFConfig.asd").isFile &&
                (File(saveDir, "AFCGMode.asd").isFile ||
                    File(saveDir, "Thumb0000.qnt").isFile ||
                    saveDir.listFiles()?.any { it.isFile && it.name.endsWith(".vsf", ignoreCase = true) } == true) &&
                !File(saveDir, "AFInfo.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                !File(saveDir, "GlobalSave.asd").isFile
            ) {
                return ID_RANCE09
            }
            if ((File(saveDir, "ＣＧモード.asd").isFile ||
                    File(saveDir, "回想モード.asd").isFile ||
                    File(saveDir, "QC.asd").isFile) &&
                !File(saveDir, "共有.asd").isFile &&
                !File(saveDir, "嫟桳.asd").isFile &&
                !File(saveDir, "音楽モード.asd").isFile &&
                !File(saveDir, "壒妝儌乕僪.asd").isFile &&
                !File(saveDir, "3DSetting.asd").isFile &&
                !File(saveDir, "顔ＣＧ").isDirectory &&
                !File(saveDir, "婄俠俧").isDirectory &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return ID_RANCE02
            }
            if ((File(saveDir, "共有.asd").isFile ||
                    File(saveDir, "嫟桳.asd").isFile ||
                    File(saveDir, "ＣＧモード.asd").isFile ||
                    File(saveDir, "俠俧儌乕僪.asd").isFile ||
                    File(saveDir, "音楽モード.asd").isFile ||
                    File(saveDir, "壒妝儌乕僪.asd").isFile ||
                    File(saveDir, "3DSetting.asd").isFile ||
                    File(saveDir, "顔ＣＧ").isDirectory ||
                    File(saveDir, "婄俠俧").isDirectory) &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return ID_RANCE08
            }
            if (File(saveDir, "CommonSystemData.sav").isFile &&
                (File(saveDir, "AFCommon.asd").isFile ||
                    File(saveDir, "AFCGMode.asd").isFile ||
                    File(saveDir, "AFBGMMode.asd").isFile) &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                !File(saveDir, "AFInfo.asd").isFile &&
                !File(saveDir, "AFConfig.asd").isFile
            ) {
                return ID_RANCE01
            }
            if ((File(saveDir, "AFBGMMode.asd").isFile ||
                    File(saveDir, "AFCommon.asd").isFile ||
                    saveDir.listFiles()?.any {
                        it.isFile && it.name.startsWith("AFSaveData", ignoreCase = true) && it.name.endsWith(".asd", ignoreCase = true)
                    } == true) &&
                !File(saveDir, "CommonSystemData.sav").isFile &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                !File(saveDir, "AFInfo.asd").isFile &&
                !File(saveDir, "AFConfig.asd").isFile
            ) {
                return ID_RANCE03
            }
            if ((File(saveDir, "Progress.asd").isFile ||
                    (File(saveDir, "Config.asd").isFile &&
                        (File(saveDir, "Joypad.asd").isFile ||
                            File(saveDir, "已读文本跳过.asd").isFile ||
                            File(saveDir, "既読スキップ.asd").isFile)) ||
                    saveDir.listFiles()?.any {
                        it.isFile && RANCE06_SAVE_SLOT_REGEX.matches(it.name)
                    } == true) &&
                !File(saveDir, "CommonSystemData.sav").isFile &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                !File(saveDir, "AFInfo.asd").isFile &&
                !File(saveDir, "AFConfig.asd").isFile &&
                !File(saveDir, "AFCommon.asd").isFile
            ) {
                return ID_RANCE06
            }
            if ((File(saveDir, "MsgSkipFlag.sav").isFile ||
                    saveFiles.any {
                        if (it.name.equals("keyfile.dat", ignoreCase = true)) return@any true
                        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it.name)
                        GALZOO_SAVE_SLOT_REGEX.matches(it.name) ||
                            GALZOO_SAVE_SLOT_REGEX.matches(repaired)
                    }) &&
                !File(saveDir, "Progress.asd").isFile &&
                !File(saveDir, "Config.asd").isFile &&
                !File(saveDir, "CommonSystemData.sav").isFile &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                !File(saveDir, "AFInfo.asd").isFile &&
                !File(saveDir, "AFConfig.asd").isFile &&
                !File(saveDir, "AFCommon.asd").isFile
            ) {
                return ID_GALZOO
            }
            if ((File(saveDir, "key0104.dat").isFile ||
                    saveDir.listFiles()?.any {
                        it.isFile && it.name.endsWith("_Sys.ASD", ignoreCase = true)
                    } == true) &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return ID_SENGOKU_RANCE
            }
            if (File(saveDir, "CommonSystemData.sav").isFile &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                !File(saveDir, "3DSetting.asd").isFile &&
                !File(saveDir, "共有.asd").isFile &&
                !File(saveDir, "嫟桳.asd").isFile &&
                !File(saveDir, "AFCommon.asd").isFile &&
                !File(saveDir, "AFCGMode.asd").isFile &&
                !File(saveDir, "AFBGMMode.asd").isFile &&
                !File(saveDir, "Esca.sav").isFile &&
                saveDir.listFiles()?.any { it.isFile && it.name.lowercase().startsWith("escalayerreboot_") } != true
            ) {
                return ID_EVENICLE
            }
            if ((File(saveDir, "GlobalSaveData.asd").isFile ||
                    File(saveDir, "AFInfo.asd").isFile ||
                    File(saveDir, "SaveData5000.asd").isFile) &&
                !File(saveDir, "GlobalSave.asd").isFile
            ) {
                return ID_DOHNA_DOHNA
            }
            if (File(saveDir, "GlobalSave.asd").isFile) {
                return ID_RANCE10
            }
            if (saveDir.listFiles()?.any {
                    it.isFile && it.name.lowercase().startsWith("doki_s") && it.name.lowercase().endsWith(".asd")
                } == true
            ) {
                return ID_ESCALAYER
            }
            if (!File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                saveDir.listFiles()?.any {
                    if (!it.isFile) return@any false
                    val lower = ArchiveExtractor.repairSjisAsGbkMojibake(it.name).lowercase()
                    (lower.startsWith("kichikurance") && (lower.endsWith(".asd") || lower.endsWith(".msgskip"))) ||
                        ((lower.startsWith("鬼畜王") || lower.startsWith("婼抺墹")) && lower.endsWith(".asd"))
                } == true
            ) {
                return ID_KICHIKUOU
            }
            if (!File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile &&
                saveDir.listFiles()?.any {
                    if (!it.isFile) return@any false
                    val lower = it.name.lowercase()
                    (lower.endsWith(".asd") || lower.endsWith(".msgskip")) && isDaiakujiAlias(it.name)
                } == true
            ) {
                return ID_DAIAKUJI
            }
        }

        val lowerDir = ArchiveExtractor.repairSjisAsGbkMojibake(dirName).lowercase()
        if (lowerDir == "rance10" || lowerDir.startsWith("rance10-") ||
            lowerDir.contains("rance 10") ||
            lowerDir.contains("兰斯10") || lowerDir.contains("兰斯１０") ||
            lowerDir.contains("ランス10") || lowerDir.contains("ランス１０")
        ) {
            return ID_RANCE10
        }
        if (lowerDir == "rance09" || lowerDir.startsWith("rance09-") ||
            lowerDir == "rance9" || lowerDir.startsWith("rance9-") ||
            lowerDir.contains("rance 09") || lowerDir.contains("rance 9") || lowerDir.contains("rance ix") ||
            RANCE09_CN_ALIAS_REGEX.containsMatchIn(lowerDir) || lowerDir.contains("ランス09") ||
            lowerDir.contains("ランス9") || lowerDir.contains("ヘルマン") || lowerDir.contains("赫尔曼")
        ) {
            return ID_RANCE09
        }
        if (lowerDir == "rance08" || lowerDir.startsWith("rance08-") ||
            lowerDir == "rance8" || lowerDir.startsWith("rance8-") ||
            lowerDir.contains("rance 08") || lowerDir.contains("rancequest") || lowerDir.contains("rance quest") ||
            RANCE08_CN_ALIAS_REGEX.containsMatchIn(lowerDir) || lowerDir.contains("ランス08") ||
            lowerDir.contains("ランス8") || lowerDir.contains("ランス・クエスト")
        ) {
            return ID_RANCE08
        }
        if (isRance1DigestAlias(dirName)) {
            return ID_RANCE1DIGEST
        }
        if (lowerDir == "rance01" || lowerDir.startsWith("rance01-") || lowerDir.contains("rance 01") ||
            RANCE01_CN_ALIAS_REGEX.containsMatchIn(lowerDir) || lowerDir.contains("ランス01") ||
            lowerDir.contains("ランス０１")) {
            return ID_RANCE01
        }
        if (lowerDir == "rance02" || lowerDir.startsWith("rance02-") || lowerDir.contains("rance 02") ||
            RANCE02_CN_ALIAS_REGEX.containsMatchIn(lowerDir) || lowerDir.contains("ランス02") ||
            lowerDir.contains("ランス０２")) {
            return ID_RANCE02
        }
        if (lowerDir == "rance03" || lowerDir.startsWith("rance03-") || lowerDir.contains("rance 03") ||
            RANCE03_CN_ALIAS_REGEX.containsMatchIn(lowerDir) || lowerDir.contains("ランス03")
        ) {
            return ID_RANCE03
        }
        if (isRance41Alias(lowerDir)) {
            return ID_RANCE41
        }
        if (isRance42Alias(lowerDir)) {
            return ID_RANCE42
        }
        if (isRance04Alias(lowerDir)) {
            return ID_RANCE04
        }
        if (isRance5dAlias(lowerDir)) {
            return ID_RANCE5D
        }
        if (isRance6AfterAlias(dirName)) {
            return ID_RANCE6AFTER
        }
        if (isRance06Alias(lowerDir)) {
            return ID_RANCE06
        }
        if (isGalzooAlias(lowerDir)) {
            return ID_GALZOO
        }
        if (isSanbikiAlias(dirName)) {
            return ID_SANBIKI
        }
        if (lowerDir == "sengokurance" || lowerDir.startsWith("sengokurance-") || lowerDir.contains("sengoku") ||
            lowerDir.contains("daisengoku") || lowerDir.contains("大战国") || lowerDir.contains("大戦国") ||
            lowerDir.contains("战国兰斯") || lowerDir.contains("戦国ランス") || lowerDir.contains("愴崙儔儞僗")
        ) {
            return ID_SENGOKU_RANCE
        }
        if (isEvenicle2RanceAlias(dirName)) {
            return ID_EVENICLE2RANCE
        }
        if (isEvenicle2Alias(lowerDir)) {
            return ID_EVENICLE2
        }
        if (isEvenicleRanceAlias(dirName)) {
            return ID_EVENICLERANCE
        }
        if (isEvenicleGaidenAlias(dirName)) {
            return ID_EVENICLEGAIDEN
        }
        if (lowerDir == "evenicle" || lowerDir.startsWith("evenicle-") || lowerDir.contains("イブニクル") || lowerDir.contains("夏娃")) {
            return ID_EVENICLE
        }
        if (lowerDir == "dohnadohna" || lowerDir.startsWith("dohnadohna-") || lowerDir.contains("多娜多娜")) {
            return ID_DOHNA_DOHNA
        }
        if (isEscalayerRebootAlias(lowerDir)) {
            return ID_ESCALAYER_REBOOT
        }
        if (isHarukaVsEscaAlias(dirName)) {
            return ID_HARUKAVSESCA
        }
        if (isHarukaNinjaAlias(dirName)) {
            return ID_HARUKANINJA
        }
        if (lowerDir == "escalayer" || lowerDir.startsWith("escalayer-") || lowerDir.contains("escalayer") ||
            lowerDir.contains("超昂天使") || lowerDir.contains("エスカレイヤー")
        ) {
            return ID_ESCALAYER
        }
        if (isIxsealAlias(lowerDir)) {
            return ID_IXSEAL
        }
        if (isHarukaAlias(dirName)) {
            return ID_HARUKA
        }
        if (isTsumamigui3JunAlias(dirName)) {
            return ID_TSUMAMIGUI3JUN
        }
        if (isTsumamigui3NtrAlias(dirName)) {
            return ID_TSUMAMIGUI3NTR
        }
        if (isTsumamigui3Alias(dirName)) {
            return ID_TSUMAMIGUI3
        }
        if (isTsumamigui2Alias(dirName)) {
            return ID_TSUMAMIGUI2
        }
        if (isTsumamiguiAlias(dirName)) {
            return ID_TSUMAMIGUI
        }
        if (isTsumashiboriAlias(dirName)) {
            return ID_TSUMASHIBORI
        }
        if (isDaibanchouGaidenAlias(dirName)) {
            return ID_DAIBANCHOUGAIDEN
        }
        if (isDaibanchouAlias(dirName)) {
            return ID_DAIBANCHOU
        }
        if (isDaiteikokuAlias(dirName)) {
            return ID_DAITEIKOKU
        }
        if (isKaeruAliceAlias(dirName)) {
            return ID_KAERUALICE
        }
        if (isKaerunyoAlias(dirName)) {
            return ID_KAERUNYO
        }
        if (isAtlachnachaAlias(dirName)) {
            return ID_ATLACHNACHA
        }
        if (isReishikiAlias(dirName)) {
            return ID_REISHIKI
        }
        if (isHaruurareAlias(dirName)) {
            return ID_HARUURARE
        }
        if (isOkusamaJunAlias(dirName)) {
            return ID_OKUSAMAJUN
        }
        if (isOkusamaNtrAlias(dirName)) {
            return ID_OKUSAMANTR
        }
        if (isWaidonyoAlias(dirName)) {
            return ID_WAIDONYO
        }
        if (isYakata456Alias(dirName)) {
            return ID_YAKATA456
        }
        if (isYakata3Alias(dirName)) {
            return ID_YAKATA3
        }
        if (isYakata2Alias(dirName)) {
            return ID_YAKATA2
        }
        if (isYakata1Alias(dirName)) {
            return ID_YAKATA1
        }
        if (isKoreDpsAlias(dirName)) {
            return ID_KOREDPS
        }
        if (isDpsAllAlias(dirName)) {
            return ID_DPSALL
        }
        if (isSdpsTonoAlias(dirName)) {
            return ID_SDPSTONO
        }
        if (isSdpsKaizokuAlias(dirName)) {
            return ID_SDPSKAIZOKU
        }
        if (isSdpsMariaAlias(dirName)) {
            return ID_SDPSMARIA
        }
        if (isDpsAlias(dirName)) {
            return ID_DPS
        }
        if (isToushin3Alias(dirName)) {
            return ID_TOUSHIN3
        }
        if (isToushin2SpAlias(dirName)) {
            return ID_TOUSHIN2SP
        }
        if (isToushin2Alias(dirName)) {
            return ID_TOUSHIN2
        }
        if (isToushin1Alias(dirName)) {
            return ID_TOUSHIN1
        }
        if (isDiaboliqueAlias(dirName)) {
            return ID_DIABOLIQUE
        }
        if (isGakuenHyouryuu2Alias(dirName)) {
            return ID_GAKUENHYOURYUU2
        }
        if (isGakuenHyouryuuAlias(dirName)) {
            return ID_GAKUENHYOURYUU
        }
        if (isGakuenKingExAlias(dirName)) {
            return ID_GAKUENKINGEX
        }
        if (isGakuenKingAlias(dirName)) {
            return ID_GAKUENKING
        }
        if (isOudouYuushaAlias(dirName)) {
            return ID_OUDOUYUUSHA
        }
        if (isDarcrowsAlias(dirName)) {
            return ID_DARCROWS
        }
        if (isIkusaMikoAlias(dirName)) {
            return ID_IKUSAMIKO
        }
        if (isOnlyYouReCrossAlias(dirName)) {
            return ID_ONLYYOURECROSS
        }
        if (isOnlyYouAlias(dirName)) {
            return ID_ONLYYOU
        }
        if (isMamatotoAlias(dirName)) {
            return ID_MAMATOTO
        }
        if (isMamorigamiAlias(dirName)) {
            return ID_MAMORIGAMI
        }
        if (isAmbivalenzAlias(dirName)) {
            return ID_AMBIVALENZ
        }
        if (isFunnyBeeAlias(dirName)) {
            return ID_FUNNYBEE
        }
        if (isMugenHouyouAlias(dirName)) {
            return ID_MUGENHOUYOU
        }
        if (isOtomeSenkiAlias(dirName)) {
            return ID_OTOMESENKI
        }
        if (isMaidNoSusumeAlias(dirName)) {
            return ID_MAIDNOSUSUME
        }
        if (isPersiomAlias(dirName)) {
            return ID_PERSIOM
        }
        if (isSeeInAoAlias(dirName)) {
            return ID_SEEINAO
        }
        if (isHushabyAlias(dirName)) {
            return ID_HUSHABY
        }
        if (isAyumiLiveAlias(dirName)) {
            return ID_AYUMILIVE
        }
        if (isAyumiHintAlias(dirName)) {
            return ID_AYUMIHINT
        }
        if (isAyumiAlias(dirName)) {
            return ID_AYUMI
        }
        if (isCielClaireAlias(dirName)) {
            return ID_CIELCLAIRE
        }
        if (isNightDemonAlias(dirName)) {
            return ID_NIGHTDEMON
        }
        if (isKatsumiAlias(dirName)) {
            return ID_KATSUMI
        }
        if (isDrStopAlias(dirName)) {
            return ID_DRSTOP
        }
        if (isProstudentGoodAlias(dirName)) {
            return ID_PROSTUDENTGOOD
        }
        if (isProgOmakeAlias(dirName)) {
            return ID_PROGOMAKE
        }
        if (isProstudentGAlias(dirName)) {
            return ID_PROSTUDENTG
        }
        if (isPascha3Alias(dirName)) {
            return ID_PASCHA3
        }
        if (isPaschaCppAlias(dirName)) {
            return ID_PASCHACPP
        }
        if (isPastelChime2Alias(dirName)) {
            return ID_PASTELCHIME2
        }
        if (isPastelChimeAlias(dirName)) {
            return ID_PASTELCHIME
        }
        if (isDalkGaidenAlias(dirName)) {
            return ID_DALKGAIDEN
        }
        if (isDalkAlias(dirName)) {
            return ID_DALK
        }
        if (isNingenGariAlias(dirName)) {
            return ID_NINGENGARI
        }
        if (isMamanyonyoAlias(dirName)) {
            return ID_MAMANYONYO
        }
        if (isMajoNoShokuzaiAlias(dirName)) {
            return ID_MAJONOSHOKUZAI
        }
        if (isSabotenAlias(dirName)) {
            return ID_SABOTEN
        }
        if (isDoubleSenseiAlias(dirName)) {
            return ID_DOUBLESENSEI
        }
        if (isOjousamaAlias(dirName)) {
            return ID_OJOUSAMA
        }
        if (isEdoranzeAlias(dirName)) {
            return ID_EDORANZE
        }
        if (isVanishPalmAlias(dirName)) {
            return ID_VANISHPALM
        }
        if (isVanishAlias(dirName)) {
            return ID_VANISH
        }
        if (isHokenshitsuAlias(dirName)) {
            return ID_HOKENSHITSU
        }
        if (isKamunagaraAlias(dirName)) {
            return ID_KAMUNAGARA
        }
        if (isErumeiWakareAlias(dirName)) {
            return ID_ERUMEIWAKARE
        }
        if (isErumeiFlowerAlias(dirName)) {
            return ID_ERUMEIFLOWER
        }
        if (isManana1Alias(dirName)) {
            return ID_MANANA1
        }
        if (isManana2Alias(dirName)) {
            return ID_MANANA2
        }
        if (isManana3Alias(dirName)) {
            return ID_MANANA3
        }
        if (isManana4Alias(dirName)) {
            return ID_MANANA4
        }
        if (isManana5Alias(dirName)) {
            return ID_MANANA5
        }
        if (isTsumaSakuraAlias(dirName)) {
            return ID_TSUMASAKURA
        }
        if (isShamansSanctuaryAlias(dirName)) {
            return ID_SHAMANSSANCTUARY
        }
        if (isAliveZAlias(dirName)) {
            return ID_ALIVEZ
        }
        if (isDungeonsDollsAlias(dirName)) {
            return ID_DUNGEONSDOLLS
        }
        if (isSuzumiyaAlias(dirName)) {
            return ID_SUZUMIYA
        }
        if (isShimaimaAlias(dirName)) {
            return ID_SHIMAIMA
        }
        if (isCosplayLemonAlias(dirName)) {
            return ID_COSPLAYLEMON
        }
        if (isWhipHoneyAlias(dirName)) {
            return ID_WHIPHONEY
        }
        if (isOnimusuAlias(dirName)) {
            return ID_ONIMUSU
        }
        if (isProGrxAlias(dirName)) {
            return ID_PROGRX
        }
        if (isDpsVerNAlias(dirName)) {
            return ID_DPSVERN
        }
        if (isSukiironoAlias(dirName)) {
            return ID_SUKIIRONO
        }
        if (isGirlsAlias(dirName)) {
            return ID_GIRLS
        }
        if (isDebutAlias(dirName)) {
            return ID_DEBUT
        }
        if (isMeganeAlias(dirName)) {
            return ID_MEGANE
        }
        if (isRachelSakuraAlias(dirName)) {
            return ID_RACHELSAKURA
        }
        if (isSabakuNikaAlias(dirName)) {
            return ID_SABAKUNIKA
        }
        if (isBladeBridersAlias(dirName)) {
            return ID_BLADEBRIDERS
        }
        if (isDorapekoAlias(dirName)) {
            return ID_DORAPEKO
        }
        if (isHaniwaPetitAlias(dirName)) {
            return ID_HANIWAPETIT
        }
        if (isUmiNiOchiteAlias(dirName)) {
            return ID_UMINIOCHITE
        }
        if (isYouseiAlias(dirName)) {
            return ID_YOUSEI
        }
        if (isTobiraAlias(dirName)) {
            return ID_TOBIRA
        }
        if (isBuroburoAlias(dirName)) {
            return ID_BUROBURO
        }
        if (isKudakeroAlias(dirName)) {
            return ID_KUDAKERO
        }
        if (isLittleVampireAlias(dirName)) {
            return ID_LITTLEVAMPIRE
        }
        if (isIntruderAlias(dirName)) {
            return ID_INTRUDER
        }
        if (isCrescentAlias(dirName)) {
            return ID_CRESCENT
        }
        if (isTenguAlias(dirName)) {
            return ID_TENGU
        }
        if (isNiseNaguriAlias(dirName)) {
            return ID_NISENAGURI
        }
        if (isCarolAlias(dirName)) {
            return ID_CAROL
        }
        if (isOthellotAlias(dirName)) {
            return ID_OTHELLOT
        }
        if (isMasqueradeAlias(dirName)) {
            return ID_MASQUERADE
        }
        if (isDekirukanaAlias(dirName)) {
            return ID_DEKIRUKANA
        }
        if (isTatakikoroshiAlias(dirName)) {
            return ID_TATAKIKOROSHI
        }
        if (isBalloonPopAlias(dirName)) {
            return ID_BALLOONPOP
        }
        if (isHiyoko2Alias(dirName)) {
            return ID_HIYOKO2
        }
        if (isSelfGameAlias(dirName)) {
            return ID_SELFGAME
        }
        if (isMomoiroGuardianAlias(dirName)) {
            return ID_MOMOIROGUARDIAN
        }
        if (isRance1ClassicAlias(dirName)) {
            return ID_RANCE1CLASSIC
        }
        if (isRance2ClassicAlias(dirName)) {
            return ID_RANCE2CLASSIC
        }
        if (isRance3ClassicAlias(dirName)) {
            return ID_RANCE3CLASSIC
        }
        if (isDpsSgFahrenAlias(dirName)) {
            return ID_DPSSGFAHREN
        }
        if (isDpsSgKateiAlias(dirName)) {
            return ID_DPSSGKATEI
        }
        if (isDpsSgNobunagaAlias(dirName)) {
            return ID_DPSSGNOBUNAGA
        }
        if (isDpsSg2AntiqueAlias(dirName)) {
            return ID_DPSSG2ANTIQUE
        }
        if (isDpsSg2IkenaiAlias(dirName)) {
            return ID_DPSSG2IKENAI
        }
        if (isDpsSg2AkaiAlias(dirName)) {
            return ID_DPSSG2AKAI
        }
        if (isDpsSg3RabbitAlias(dirName)) {
            return ID_DPSSG3RABBIT
        }
        if (isDpsSg3ShinkonAlias(dirName)) {
            return ID_DPSSG3SHINKON
        }
        if (isDpsSg3SotsugyouAlias(dirName)) {
            return ID_DPSSG3SOTSUGYOU
        }
        if (isOyakorankanAlias(dirName)) {
            return ID_OYAKORANKAN
        }
        if (isYorugakuruAlias(dirName)) {
            return ID_YORUGAKURU
        }
        if (isMamanAlias(dirName)) {
            return ID_MAMAN
        }
        if (isHaharanmanAlias(dirName)) {
            return ID_HAHARANMAN
        }
        if (lowerDir == "kichikurance" || lowerDir.startsWith("kichikurance-") ||
            lowerDir == "kichikuou" || lowerDir.startsWith("kichikuou-") ||
            lowerDir.contains("kichikuou") || lowerDir.contains("kichiku") ||
            lowerDir.contains("鬼畜王")
        ) {
            return ID_KICHIKUOU
        }
        if (isDaiakujiAlias(dirName)) {
            return ID_DAIAKUJI
        }

        val lowerDisp = displayName?.let { ArchiveExtractor.repairSjisAsGbkMojibake(it) }?.lowercase() ?: ""
        if (isEscalayerRebootAlias(lowerDisp)) {
            return ID_ESCALAYER_REBOOT
        }
        if (!displayName.isNullOrBlank() && isHarukaVsEscaAlias(displayName)) {
            return ID_HARUKAVSESCA
        }
        if (!displayName.isNullOrBlank() && isHarukaNinjaAlias(displayName)) {
            return ID_HARUKANINJA
        }
        if (lowerDisp.contains("escalayer") || lowerDisp.contains("超昂天使") ||
            lowerDisp.contains("エスカレイヤー") || lowerDisp.contains("艾斯卡蕾雅")
        ) {
            return ID_ESCALAYER
        }
        if (isIxsealAlias(lowerDisp)) {
            return ID_IXSEAL
        }
        if (!displayName.isNullOrBlank() && isHarukaAlias(displayName)) {
            return ID_HARUKA
        }
        if (!displayName.isNullOrBlank() && isTsumamigui3JunAlias(displayName)) {
            return ID_TSUMAMIGUI3JUN
        }
        if (!displayName.isNullOrBlank() && isTsumamigui3NtrAlias(displayName)) {
            return ID_TSUMAMIGUI3NTR
        }
        if (!displayName.isNullOrBlank() && isTsumamigui3Alias(displayName)) {
            return ID_TSUMAMIGUI3
        }
        if (!displayName.isNullOrBlank() && isTsumamigui2Alias(displayName)) {
            return ID_TSUMAMIGUI2
        }
        if (!displayName.isNullOrBlank() && isTsumamiguiAlias(displayName)) {
            return ID_TSUMAMIGUI
        }
        if (!displayName.isNullOrBlank() && isTsumashiboriAlias(displayName)) {
            return ID_TSUMASHIBORI
        }
        if (!displayName.isNullOrBlank() && isDaibanchouGaidenAlias(displayName)) {
            return ID_DAIBANCHOUGAIDEN
        }
        if (!displayName.isNullOrBlank() && isDaibanchouAlias(displayName)) {
            return ID_DAIBANCHOU
        }
        if (!displayName.isNullOrBlank() && isDaiteikokuAlias(displayName)) {
            return ID_DAITEIKOKU
        }
        if (!displayName.isNullOrBlank() && isKaeruAliceAlias(displayName)) {
            return ID_KAERUALICE
        }
        if (!displayName.isNullOrBlank() && isKaerunyoAlias(displayName)) {
            return ID_KAERUNYO
        }
        if (!displayName.isNullOrBlank() && isAtlachnachaAlias(displayName)) {
            return ID_ATLACHNACHA
        }
        if (!displayName.isNullOrBlank() && isReishikiAlias(displayName)) {
            return ID_REISHIKI
        }
        if (!displayName.isNullOrBlank() && isHaruurareAlias(displayName)) {
            return ID_HARUURARE
        }
        if (!displayName.isNullOrBlank() && isOkusamaJunAlias(displayName)) {
            return ID_OKUSAMAJUN
        }
        if (!displayName.isNullOrBlank() && isOkusamaNtrAlias(displayName)) {
            return ID_OKUSAMANTR
        }
        if (!displayName.isNullOrBlank() && isWaidonyoAlias(displayName)) {
            return ID_WAIDONYO
        }
        if (!displayName.isNullOrBlank() && isYakata456Alias(displayName)) {
            return ID_YAKATA456
        }
        if (!displayName.isNullOrBlank() && isYakata3Alias(displayName)) {
            return ID_YAKATA3
        }
        if (!displayName.isNullOrBlank() && isYakata2Alias(displayName)) {
            return ID_YAKATA2
        }
        if (!displayName.isNullOrBlank() && isYakata1Alias(displayName)) {
            return ID_YAKATA1
        }
        if (!displayName.isNullOrBlank() && isKoreDpsAlias(displayName)) {
            return ID_KOREDPS
        }
        if (!displayName.isNullOrBlank() && isDpsAllAlias(displayName)) {
            return ID_DPSALL
        }
        if (!displayName.isNullOrBlank() && isSdpsTonoAlias(displayName)) {
            return ID_SDPSTONO
        }
        if (!displayName.isNullOrBlank() && isSdpsKaizokuAlias(displayName)) {
            return ID_SDPSKAIZOKU
        }
        if (!displayName.isNullOrBlank() && isSdpsMariaAlias(displayName)) {
            return ID_SDPSMARIA
        }
        if (!displayName.isNullOrBlank() && isDpsAlias(displayName)) {
            return ID_DPS
        }
        if (!displayName.isNullOrBlank() && isToushin3Alias(displayName)) {
            return ID_TOUSHIN3
        }
        if (!displayName.isNullOrBlank() && isToushin2SpAlias(displayName)) {
            return ID_TOUSHIN2SP
        }
        if (!displayName.isNullOrBlank() && isToushin2Alias(displayName)) {
            return ID_TOUSHIN2
        }
        if (!displayName.isNullOrBlank() && isToushin1Alias(displayName)) {
            return ID_TOUSHIN1
        }
        if (!displayName.isNullOrBlank() && isDiaboliqueAlias(displayName)) {
            return ID_DIABOLIQUE
        }
        if (!displayName.isNullOrBlank() && isGakuenHyouryuu2Alias(displayName)) {
            return ID_GAKUENHYOURYUU2
        }
        if (!displayName.isNullOrBlank() && isGakuenHyouryuuAlias(displayName)) {
            return ID_GAKUENHYOURYUU
        }
        if (!displayName.isNullOrBlank() && isGakuenKingExAlias(displayName)) {
            return ID_GAKUENKINGEX
        }
        if (!displayName.isNullOrBlank() && isGakuenKingAlias(displayName)) {
            return ID_GAKUENKING
        }
        if (!displayName.isNullOrBlank() && isOudouYuushaAlias(displayName)) {
            return ID_OUDOUYUUSHA
        }
        if (!displayName.isNullOrBlank() && isDarcrowsAlias(displayName)) {
            return ID_DARCROWS
        }
        if (!displayName.isNullOrBlank() && isIkusaMikoAlias(displayName)) {
            return ID_IKUSAMIKO
        }
        if (!displayName.isNullOrBlank() && isOnlyYouReCrossAlias(displayName)) {
            return ID_ONLYYOURECROSS
        }
        if (!displayName.isNullOrBlank() && isOnlyYouAlias(displayName)) {
            return ID_ONLYYOU
        }
        if (!displayName.isNullOrBlank() && isMamatotoAlias(displayName)) {
            return ID_MAMATOTO
        }
        if (!displayName.isNullOrBlank() && isMamorigamiAlias(displayName)) {
            return ID_MAMORIGAMI
        }
        if (!displayName.isNullOrBlank() && isAmbivalenzAlias(displayName)) {
            return ID_AMBIVALENZ
        }
        if (!displayName.isNullOrBlank() && isFunnyBeeAlias(displayName)) {
            return ID_FUNNYBEE
        }
        if (!displayName.isNullOrBlank() && isMugenHouyouAlias(displayName)) {
            return ID_MUGENHOUYOU
        }
        if (!displayName.isNullOrBlank() && isOtomeSenkiAlias(displayName)) {
            return ID_OTOMESENKI
        }
        if (!displayName.isNullOrBlank() && isMaidNoSusumeAlias(displayName)) {
            return ID_MAIDNOSUSUME
        }
        if (!displayName.isNullOrBlank() && isPersiomAlias(displayName)) {
            return ID_PERSIOM
        }
        if (!displayName.isNullOrBlank() && isSeeInAoAlias(displayName)) {
            return ID_SEEINAO
        }
        if (!displayName.isNullOrBlank() && isHushabyAlias(displayName)) {
            return ID_HUSHABY
        }
        if (!displayName.isNullOrBlank() && isAyumiLiveAlias(displayName)) {
            return ID_AYUMILIVE
        }
        if (!displayName.isNullOrBlank() && isAyumiHintAlias(displayName)) {
            return ID_AYUMIHINT
        }
        if (!displayName.isNullOrBlank() && isAyumiAlias(displayName)) {
            return ID_AYUMI
        }
        if (!displayName.isNullOrBlank() && isCielClaireAlias(displayName)) {
            return ID_CIELCLAIRE
        }
        if (!displayName.isNullOrBlank() && isNightDemonAlias(displayName)) {
            return ID_NIGHTDEMON
        }
        if (!displayName.isNullOrBlank() && isKatsumiAlias(displayName)) {
            return ID_KATSUMI
        }
        if (!displayName.isNullOrBlank() && isDrStopAlias(displayName)) {
            return ID_DRSTOP
        }
        if (!displayName.isNullOrBlank() && isProstudentGoodAlias(displayName)) {
            return ID_PROSTUDENTGOOD
        }
        if (!displayName.isNullOrBlank() && isProgOmakeAlias(displayName)) {
            return ID_PROGOMAKE
        }
        if (!displayName.isNullOrBlank() && isProstudentGAlias(displayName)) {
            return ID_PROSTUDENTG
        }
        if (!displayName.isNullOrBlank() && isPascha3Alias(displayName)) {
            return ID_PASCHA3
        }
        if (!displayName.isNullOrBlank() && isPaschaCppAlias(displayName)) {
            return ID_PASCHACPP
        }
        if (!displayName.isNullOrBlank() && isPastelChime2Alias(displayName)) {
            return ID_PASTELCHIME2
        }
        if (!displayName.isNullOrBlank() && isPastelChimeAlias(displayName)) {
            return ID_PASTELCHIME
        }
        if (!displayName.isNullOrBlank() && isDalkGaidenAlias(displayName)) {
            return ID_DALKGAIDEN
        }
        if (!displayName.isNullOrBlank() && isDalkAlias(displayName)) {
            return ID_DALK
        }
        if (!displayName.isNullOrBlank() && isNingenGariAlias(displayName)) {
            return ID_NINGENGARI
        }
        if (!displayName.isNullOrBlank() && isMamanyonyoAlias(displayName)) {
            return ID_MAMANYONYO
        }
        if (!displayName.isNullOrBlank() && isMajoNoShokuzaiAlias(displayName)) {
            return ID_MAJONOSHOKUZAI
        }
        if (!displayName.isNullOrBlank() && isSabotenAlias(displayName)) {
            return ID_SABOTEN
        }
        if (!displayName.isNullOrBlank() && isDoubleSenseiAlias(displayName)) {
            return ID_DOUBLESENSEI
        }
        if (!displayName.isNullOrBlank() && isOjousamaAlias(displayName)) {
            return ID_OJOUSAMA
        }
        if (!displayName.isNullOrBlank() && isEdoranzeAlias(displayName)) {
            return ID_EDORANZE
        }
        if (!displayName.isNullOrBlank() && isVanishPalmAlias(displayName)) {
            return ID_VANISHPALM
        }
        if (!displayName.isNullOrBlank() && isVanishAlias(displayName)) {
            return ID_VANISH
        }
        if (!displayName.isNullOrBlank() && isHokenshitsuAlias(displayName)) {
            return ID_HOKENSHITSU
        }
        if (!displayName.isNullOrBlank() && isKamunagaraAlias(displayName)) {
            return ID_KAMUNAGARA
        }
        if (!displayName.isNullOrBlank() && isErumeiWakareAlias(displayName)) {
            return ID_ERUMEIWAKARE
        }
        if (!displayName.isNullOrBlank() && isErumeiFlowerAlias(displayName)) {
            return ID_ERUMEIFLOWER
        }
        if (!displayName.isNullOrBlank() && isManana1Alias(displayName)) {
            return ID_MANANA1
        }
        if (!displayName.isNullOrBlank() && isManana2Alias(displayName)) {
            return ID_MANANA2
        }
        if (!displayName.isNullOrBlank() && isManana3Alias(displayName)) {
            return ID_MANANA3
        }
        if (!displayName.isNullOrBlank() && isManana4Alias(displayName)) {
            return ID_MANANA4
        }
        if (!displayName.isNullOrBlank() && isManana5Alias(displayName)) {
            return ID_MANANA5
        }
        if (!displayName.isNullOrBlank() && isTsumaSakuraAlias(displayName)) {
            return ID_TSUMASAKURA
        }
        if (!displayName.isNullOrBlank() && isShamansSanctuaryAlias(displayName)) {
            return ID_SHAMANSSANCTUARY
        }
        if (!displayName.isNullOrBlank() && isAliveZAlias(displayName)) {
            return ID_ALIVEZ
        }
        if (!displayName.isNullOrBlank() && isDungeonsDollsAlias(displayName)) {
            return ID_DUNGEONSDOLLS
        }
        if (!displayName.isNullOrBlank() && isSuzumiyaAlias(displayName)) {
            return ID_SUZUMIYA
        }
        if (!displayName.isNullOrBlank() && isShimaimaAlias(displayName)) {
            return ID_SHIMAIMA
        }
        if (!displayName.isNullOrBlank() && isCosplayLemonAlias(displayName)) {
            return ID_COSPLAYLEMON
        }
        if (!displayName.isNullOrBlank() && isWhipHoneyAlias(displayName)) {
            return ID_WHIPHONEY
        }
        if (!displayName.isNullOrBlank() && isOnimusuAlias(displayName)) {
            return ID_ONIMUSU
        }
        if (!displayName.isNullOrBlank() && isProGrxAlias(displayName)) {
            return ID_PROGRX
        }
        if (!displayName.isNullOrBlank() && isDpsVerNAlias(displayName)) {
            return ID_DPSVERN
        }
        if (!displayName.isNullOrBlank() && isSukiironoAlias(displayName)) {
            return ID_SUKIIRONO
        }
        if (!displayName.isNullOrBlank() && isGirlsAlias(displayName)) {
            return ID_GIRLS
        }
        if (!displayName.isNullOrBlank() && isDebutAlias(displayName)) {
            return ID_DEBUT
        }
        if (!displayName.isNullOrBlank() && isMeganeAlias(displayName)) {
            return ID_MEGANE
        }
        if (!displayName.isNullOrBlank() && isRachelSakuraAlias(displayName)) {
            return ID_RACHELSAKURA
        }
        if (!displayName.isNullOrBlank() && isSabakuNikaAlias(displayName)) {
            return ID_SABAKUNIKA
        }
        if (!displayName.isNullOrBlank() && isBladeBridersAlias(displayName)) {
            return ID_BLADEBRIDERS
        }
        if (!displayName.isNullOrBlank() && isDorapekoAlias(displayName)) {
            return ID_DORAPEKO
        }
        if (!displayName.isNullOrBlank() && isHaniwaPetitAlias(displayName)) {
            return ID_HANIWAPETIT
        }
        if (!displayName.isNullOrBlank() && isUmiNiOchiteAlias(displayName)) {
            return ID_UMINIOCHITE
        }
        if (!displayName.isNullOrBlank() && isYouseiAlias(displayName)) {
            return ID_YOUSEI
        }
        if (!displayName.isNullOrBlank() && isTobiraAlias(displayName)) {
            return ID_TOBIRA
        }
        if (!displayName.isNullOrBlank() && isBuroburoAlias(displayName)) {
            return ID_BUROBURO
        }
        if (!displayName.isNullOrBlank() && isKudakeroAlias(displayName)) {
            return ID_KUDAKERO
        }
        if (!displayName.isNullOrBlank() && isLittleVampireAlias(displayName)) {
            return ID_LITTLEVAMPIRE
        }
        if (!displayName.isNullOrBlank() && isIntruderAlias(displayName)) {
            return ID_INTRUDER
        }
        if (!displayName.isNullOrBlank() && isCrescentAlias(displayName)) {
            return ID_CRESCENT
        }
        if (!displayName.isNullOrBlank() && isTenguAlias(displayName)) {
            return ID_TENGU
        }
        if (!displayName.isNullOrBlank() && isNiseNaguriAlias(displayName)) {
            return ID_NISENAGURI
        }
        if (!displayName.isNullOrBlank() && isCarolAlias(displayName)) {
            return ID_CAROL
        }
        if (!displayName.isNullOrBlank() && isOthellotAlias(displayName)) {
            return ID_OTHELLOT
        }
        if (!displayName.isNullOrBlank() && isMasqueradeAlias(displayName)) {
            return ID_MASQUERADE
        }
        if (!displayName.isNullOrBlank() && isDekirukanaAlias(displayName)) {
            return ID_DEKIRUKANA
        }
        if (!displayName.isNullOrBlank() && isTatakikoroshiAlias(displayName)) {
            return ID_TATAKIKOROSHI
        }
        if (!displayName.isNullOrBlank() && isBalloonPopAlias(displayName)) {
            return ID_BALLOONPOP
        }
        if (!displayName.isNullOrBlank() && isHiyoko2Alias(displayName)) {
            return ID_HIYOKO2
        }
        if (!displayName.isNullOrBlank() && isSelfGameAlias(displayName)) {
            return ID_SELFGAME
        }
        if (!displayName.isNullOrBlank() && isMomoiroGuardianAlias(displayName)) {
            return ID_MOMOIROGUARDIAN
        }
        if (!displayName.isNullOrBlank() && isRance1ClassicAlias(displayName)) {
            return ID_RANCE1CLASSIC
        }
        if (!displayName.isNullOrBlank() && isRance2ClassicAlias(displayName)) {
            return ID_RANCE2CLASSIC
        }
        if (!displayName.isNullOrBlank() && isRance3ClassicAlias(displayName)) {
            return ID_RANCE3CLASSIC
        }
        if (!displayName.isNullOrBlank() && isDpsSgFahrenAlias(displayName)) {
            return ID_DPSSGFAHREN
        }
        if (!displayName.isNullOrBlank() && isDpsSgKateiAlias(displayName)) {
            return ID_DPSSGKATEI
        }
        if (!displayName.isNullOrBlank() && isDpsSgNobunagaAlias(displayName)) {
            return ID_DPSSGNOBUNAGA
        }
        if (!displayName.isNullOrBlank() && isDpsSg2AntiqueAlias(displayName)) {
            return ID_DPSSG2ANTIQUE
        }
        if (!displayName.isNullOrBlank() && isDpsSg2IkenaiAlias(displayName)) {
            return ID_DPSSG2IKENAI
        }
        if (!displayName.isNullOrBlank() && isDpsSg2AkaiAlias(displayName)) {
            return ID_DPSSG2AKAI
        }
        if (!displayName.isNullOrBlank() && isDpsSg3RabbitAlias(displayName)) {
            return ID_DPSSG3RABBIT
        }
        if (!displayName.isNullOrBlank() && isDpsSg3ShinkonAlias(displayName)) {
            return ID_DPSSG3SHINKON
        }
        if (!displayName.isNullOrBlank() && isDpsSg3SotsugyouAlias(displayName)) {
            return ID_DPSSG3SOTSUGYOU
        }
        if (!displayName.isNullOrBlank() && isOyakorankanAlias(displayName)) {
            return ID_OYAKORANKAN
        }
        if (!displayName.isNullOrBlank() && isYorugakuruAlias(displayName)) {
            return ID_YORUGAKURU
        }
        if (!displayName.isNullOrBlank() && isRance1DigestAlias(displayName)) {
            return ID_RANCE1DIGEST
        }
        if (!displayName.isNullOrBlank() && isMamanAlias(displayName)) {
            return ID_MAMAN
        }
        if (!displayName.isNullOrBlank() && isHaharanmanAlias(displayName)) {
            return ID_HAHARANMAN
        }
        if (lowerDisp.contains("kichikuou") || lowerDisp.contains("kichikurance") || lowerDisp.contains("鬼畜王")) {
            return ID_KICHIKUOU
        }
        if (!displayName.isNullOrBlank() && isDaiakujiAlias(displayName)) {
            return ID_DAIAKUJI
        }
        if (lowerDisp.contains("兰斯10") || lowerDisp.contains("兰斯１０") || lowerDisp.contains("ランス10") ||
            lowerDisp.contains("ランス１０") || lowerDisp.contains("rance10") || lowerDisp.contains("rance 10")
        ) {
            return ID_RANCE10
        }
        if (lowerDisp.contains("rance09") || lowerDisp.contains("rance 09") ||
            lowerDisp.contains("rance9") || lowerDisp.contains("rance 9") || lowerDisp.contains("rance ix") ||
            RANCE09_CN_ALIAS_REGEX.containsMatchIn(lowerDisp) || lowerDisp.contains("ランス09") ||
            lowerDisp.contains("ランス9") || lowerDisp.contains("ヘルマン") || lowerDisp.contains("赫尔曼")
        ) {
            return ID_RANCE09
        }
        if (lowerDisp.contains("rance08") || lowerDisp.contains("rance 08") ||
            lowerDisp.contains("rancequest") || lowerDisp.contains("rance quest") ||
            RANCE08_CN_ALIAS_REGEX.containsMatchIn(lowerDisp) || lowerDisp.contains("ランス08") ||
            lowerDisp.contains("ランス8") || lowerDisp.contains("ランス・クエスト") ||
            lowerDisp.contains("ランスクエスト") || lowerDisp.contains("マグナム") || lowerDisp.contains("magnum")
        ) {
            return ID_RANCE08
        }
        if (lowerDisp.contains("rance01") || lowerDisp.contains("rance 01") ||
            RANCE01_CN_ALIAS_REGEX.containsMatchIn(lowerDisp) || lowerDisp.contains("ランス01") ||
            lowerDisp.contains("ランス０１") ||
            lowerDisp.contains("光をもとめて") || lowerDisp.contains("寻找小光")) {
            return ID_RANCE01
        }
        if (lowerDisp.contains("rance02") || lowerDisp.contains("rance 02") ||
            RANCE02_CN_ALIAS_REGEX.containsMatchIn(lowerDisp) || lowerDisp.contains("ランス02") ||
            lowerDisp.contains("ランス０２") || lowerDisp.contains("反叛的少女") || lowerDisp.contains("反逆の少女")) {
            return ID_RANCE02
        }
        if (lowerDisp.contains("rance03") || lowerDisp.contains("rance 03") ||
            RANCE03_CN_ALIAS_REGEX.containsMatchIn(lowerDisp) || lowerDisp.contains("ランス03") ||
            lowerDisp.contains("リーザス") || lowerDisp.contains("利萨斯")
        ) {
            return ID_RANCE03
        }
        if (isRance41Alias(lowerDisp)) {
            return ID_RANCE41
        }
        if (isRance42Alias(lowerDisp)) {
            return ID_RANCE42
        }
        if (isRance04Alias(lowerDisp)) {
            return ID_RANCE04
        }
        if (isRance5dAlias(lowerDisp)) {
            return ID_RANCE5D
        }
        if (!displayName.isNullOrBlank() && isRance6AfterAlias(displayName)) {
            return ID_RANCE6AFTER
        }
        if (isRance06Alias(lowerDisp)) {
            return ID_RANCE06
        }
        if (isGalzooAlias(lowerDisp)) {
            return ID_GALZOO
        }
        if (!displayName.isNullOrBlank() && isSanbikiAlias(displayName)) {
            return ID_SANBIKI
        }
        if (lowerDisp.contains("sengoku") || lowerDisp.contains("daisengoku") ||
            lowerDisp.contains("大战国") || lowerDisp.contains("大戦国") ||
            lowerDisp.contains("战国兰斯") || lowerDisp.contains("戰國蘭斯") ||
            lowerDisp.contains("戦国ランス")
        ) {
            return ID_SENGOKU_RANCE
        }
        if (!displayName.isNullOrBlank() && isEvenicle2RanceAlias(displayName)) {
            return ID_EVENICLE2RANCE
        }
        if (isEvenicle2Alias(lowerDisp)) {
            return ID_EVENICLE2
        }
        if (!displayName.isNullOrBlank() && isEvenicleRanceAlias(displayName)) {
            return ID_EVENICLERANCE
        }
        if (!displayName.isNullOrBlank() && isEvenicleGaidenAlias(displayName)) {
            return ID_EVENICLEGAIDEN
        }
        if (lowerDisp.contains("イブニクル") || lowerDisp.contains("evenicle") || lowerDisp.contains("夏娃")) {
            return ID_EVENICLE
        }
        if (lowerDisp.contains("多娜多娜") || lowerDisp.contains("ドーナドーナ") || lowerDisp.contains("dohna")) {
            return ID_DOHNA_DOHNA
        }

        return "$PREFIX_CUSTOM$dirName"
    }

    fun matchingItems(gameList: GameList, gameId: String): List<Item> {
        val result = ArrayList<Item>()
        for (i in 0 until gameList.size) {
            val item = gameList[i]
            if (classifyItem(item) == gameId) {
                result.add(item)
            }
        }
        result.sortBy { it.path.name }
        return result
    }

    fun descriptorFor(context: Context, gameId: String, gameList: GameList? = null): GameDescriptor =
        descriptorForWithResolver(gameId, gameList) { resId -> context.getString(resId) }

    @JvmOverloads
    fun descriptorForWithResolver(
        gameId: String,
        gameList: GameList? = null,
        stringResolver: (Int) -> String
    ): GameDescriptor {
        return when (gameId) {
            ID_RANCE10 -> GameDescriptor(
                id = ID_RANCE10,
                title = stringResolver(R.string.game_rance10_title),
                defaultDirName = "Rance10",
                expectedAinName = "Rance10.ain",
                iconRes = R.drawable.ic_game_rance10,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF7043,
                iconFrameColor = 0xFF221A18.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE10_CDN_URL,
                supportsOnlineMod = true,
                supportsLocalMod = true,
                isRance10 = true,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_DOHNA_DOHNA -> GameDescriptor(
                id = ID_DOHNA_DOHNA,
                title = stringResolver(R.string.game_dohnadohna_title),
                defaultDirName = "dohnadohna",
                expectedAinName = "dohnadohna.ain",
                iconRes = R.drawable.ic_game_dohnadohna,
                accentColor = 0xFFE91E63.toInt(),
                accentBorderColor = 0x66FF4081,
                iconFrameColor = 0x00000000,
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DOHNA_DOHNA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_EVENICLE -> GameDescriptor(
                id = ID_EVENICLE,
                title = stringResolver(R.string.game_evenicle_title),
                defaultDirName = "Evenicle",
                expectedAinName = "Evenicle.ain",
                iconRes = R.drawable.ic_game_evenicle,
                accentColor = 0xFF1E88E5.toInt(),
                accentBorderColor = 0x6642A5F5,
                iconFrameColor = 0xFF151E2E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.EVENICLE_CDN_URL,
                supportsOnlineMod = true,
                supportsLocalMod = true,
                isRance10 = false,
                isCnPatch = true,
                overlayCdnBaseUrl = OnlineInstaller.EVENICLE_CDN_URL,
                overlayCatalogRelPath = "files/cn-patch/index.json",
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_EVENICLE2 -> GameDescriptor(
                id = ID_EVENICLE2,
                title = stringResolver(R.string.game_evenicle2_title),
                defaultDirName = "Evenicle2",
                expectedAinName = "Evenicle2.ain",
                iconRes = R.drawable.ic_game_evenicle2,
                accentColor = 0xFF00838F.toInt(),
                accentBorderColor = 0x6626C6DA,
                iconFrameColor = 0xFF0F2026.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.EVENICLE2_CDN_URL,
                supportsOnlineMod = true,
                supportsLocalMod = true,
                isRance10 = false,
                isCnPatch = true,
                overlayCdnBaseUrl = OnlineInstaller.EVENICLE2_CDN_URL,
                overlayCatalogRelPath = "files/cn-patch/index.json",
                hasChinese = true
            )
            ID_SENGOKU_RANCE -> GameDescriptor(
                id = ID_SENGOKU_RANCE,
                title = stringResolver(R.string.game_sengokurance_title),
                defaultDirName = "SengokuRance",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sengokurance,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF7043,
                iconFrameColor = 0xFF1F1410.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SENGOKU_RANCE_CDN_URL,
                supportsOnlineMod = true,
                supportsLocalMod = true,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_RANCE01 -> GameDescriptor(
                id = ID_RANCE01,
                title = stringResolver(R.string.game_rance01_title),
                defaultDirName = "Rance01",
                expectedAinName = "Rance01.ain",
                iconRes = R.drawable.ic_game_rance01,
                accentColor = 0xFFD99B26.toInt(),
                accentBorderColor = 0x66F5C451,
                iconFrameColor = 0xFF211A0E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE01_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_RANCE02 -> GameDescriptor(
                id = ID_RANCE02,
                title = stringResolver(R.string.game_rance02_title),
                defaultDirName = "Rance02",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance02,
                accentColor = 0xFF3FA67A.toInt(),
                accentBorderColor = 0x666BD4A6,
                iconFrameColor = 0xFF102018.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE02_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_RANCE03 -> GameDescriptor(
                id = ID_RANCE03,
                title = stringResolver(R.string.game_rance03_title),
                defaultDirName = "Rance03",
                expectedAinName = "Rance03.ain",
                iconRes = R.drawable.ic_game_rance03,
                accentColor = 0xFFC62828.toInt(),
                accentBorderColor = 0x66EF5350,
                iconFrameColor = 0xFF1D1012.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE03_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_RANCE04 -> GameDescriptor(
                id = ID_RANCE04,
                title = stringResolver(R.string.game_rance04_title),
                defaultDirName = "Rance04",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance04,
                accentColor = 0xFF3949AB.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF14182C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE04_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_RANCE41 -> GameDescriptor(
                id = ID_RANCE41,
                title = stringResolver(R.string.game_rance41_title),
                defaultDirName = "Rance41",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance41,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF8A65,
                iconFrameColor = 0xFF281612.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE41_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_RANCE42 -> GameDescriptor(
                id = ID_RANCE42,
                title = stringResolver(R.string.game_rance42_title),
                defaultDirName = "Rance42",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance42,
                accentColor = 0xFF1E88E5.toInt(),
                accentBorderColor = 0x6664B5F6,
                iconFrameColor = 0xFF121D2B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE42_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_RANCE5D -> GameDescriptor(
                id = ID_RANCE5D,
                title = stringResolver(R.string.game_rance5d_title),
                defaultDirName = "Rance5D",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance5d,
                accentColor = 0xFFAD1457.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF28121B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE5D_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_RANCE06 -> GameDescriptor(
                id = ID_RANCE06,
                title = stringResolver(R.string.game_rance06_title),
                defaultDirName = "Rance06",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance06,
                accentColor = 0xFF00796B.toInt(),
                accentBorderColor = 0x664DB6AC,
                iconFrameColor = 0xFF0E1F1D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE06_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_RANCE08 -> GameDescriptor(
                id = ID_RANCE08,
                title = stringResolver(R.string.game_rance08_title),
                defaultDirName = "Rance08",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance08,
                accentColor = 0xFF2E7D32.toInt(),
                accentBorderColor = 0x6666BB6A,
                iconFrameColor = 0xFF102014.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE08_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_RANCE09 -> GameDescriptor(
                id = ID_RANCE09,
                title = stringResolver(R.string.game_rance09_title),
                defaultDirName = "Rance09",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance09,
                accentColor = 0xFF1565C0.toInt(),
                accentBorderColor = 0x6642A5F5,
                iconFrameColor = 0xFF101928.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE09_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_ESCALAYER -> GameDescriptor(
                id = ID_ESCALAYER,
                title = stringResolver(R.string.game_escalayer_title),
                defaultDirName = "Escalayer",
                expectedAinName = "DOKI_SA.ALD",
                iconRes = R.drawable.ic_game_escalayer,
                accentColor = 0xFFC2185B.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF281224.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ESCALAYER_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_ESCALAYER_REBOOT -> GameDescriptor(
                id = ID_ESCALAYER_REBOOT,
                title = stringResolver(R.string.game_escalayer_reboot_title),
                defaultDirName = "EscalayerReboot",
                expectedAinName = "EscalayerReboot.ain",
                iconRes = R.drawable.ic_game_escalayer_reboot,
                accentColor = 0xFF8E24AA.toInt(),
                accentBorderColor = 0x66BA68C8,
                iconFrameColor = 0xFF24122B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ESCALAYER_REBOOT_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_GALZOO -> GameDescriptor(
                id = ID_GALZOO,
                title = stringResolver(R.string.game_galzoo_title),
                defaultDirName = "GALZOO",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_galzoo,
                accentColor = 0xFF0288D1.toInt(),
                accentBorderColor = 0x664FC3F7,
                iconFrameColor = 0xFF0D2136.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.GALZOO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_IXSEAL -> GameDescriptor(
                id = ID_IXSEAL,
                title = stringResolver(R.string.game_ixseal_title),
                defaultDirName = "Ixseal",
                expectedAinName = "Ixseal.ain",
                iconRes = R.drawable.ic_game_ixseal,
                accentColor = 0xFF0277BD.toInt(),
                accentBorderColor = 0x664FC3F7,
                iconFrameColor = 0xFF0E1B28.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.IXSEAL_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_HARUKA -> GameDescriptor(
                id = ID_HARUKA,
                title = stringResolver(R.string.game_haruka_title),
                defaultDirName = "Haruka",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_haruka,
                accentColor = 0xFFE08E00.toInt(),
                accentBorderColor = 0x66FFB74D,
                iconFrameColor = 0xFF211814.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HARUKA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                supportsUncensoredPatch = true,
                hasChinese = true
            )
            ID_TSUMASHIBORI -> GameDescriptor(
                id = ID_TSUMASHIBORI,
                title = stringResolver(R.string.game_tsumashibori_title),
                defaultDirName = "Tsumashibori",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tsumashibori,
                accentColor = 0xFFD81B60.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF28121B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TSUMASHIBORI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_TSUMAMIGUI3 -> GameDescriptor(
                id = ID_TSUMAMIGUI3,
                title = stringResolver(R.string.game_tsumamigui3_title),
                defaultDirName = "Tsumamigui3",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tsumamigui3,
                accentColor = 0xFFE65100.toInt(),
                accentBorderColor = 0x66FFB74D,
                iconFrameColor = 0xFF2A1A10.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TSUMAMIGUI3_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_KICHIKUOU -> GameDescriptor(
                id = ID_KICHIKUOU,
                title = stringResolver(R.string.game_kichikuou_title),
                defaultDirName = "KichikuRance",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_kichikuou,
                accentColor = 0xFFB71C1C.toInt(),
                accentBorderColor = 0x66EF5350,
                iconFrameColor = 0xFF1E1111.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.KICHIKUOU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_DAIAKUJI -> GameDescriptor(
                id = ID_DAIAKUJI,
                title = stringResolver(R.string.game_daiakuji_title),
                defaultDirName = "Daiakuji",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_daiakuji,
                accentColor = 0xFFC62828.toInt(),
                accentBorderColor = 0x66EF5350,
                iconFrameColor = 0xFF1E1010.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DAIAKUJI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_DAIBANCHOU -> GameDescriptor(
                id = ID_DAIBANCHOU,
                title = stringResolver(R.string.game_daibanchou_title),
                defaultDirName = "Daibanchou",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_daibanchou,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF8A65,
                iconFrameColor = 0xFF22130F.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DAIBANCHOU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_DAITEIKOKU -> GameDescriptor(
                id = ID_DAITEIKOKU,
                title = stringResolver(R.string.game_daiteikoku_title),
                defaultDirName = "Daiteikoku",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_daiteikoku,
                accentColor = 0xFF1565C0.toInt(),
                accentBorderColor = 0x6664B5F6,
                iconFrameColor = 0xFF0E1929.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DAITEIKOKU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_TSUMAMIGUI -> GameDescriptor(
                id = ID_TSUMAMIGUI,
                title = stringResolver(R.string.game_tsumamigui_title),
                defaultDirName = "Tsumamigui",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tsumamigui,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF29131C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TSUMAMIGUI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_KAERUNYO -> GameDescriptor(
                id = ID_KAERUNYO,
                title = stringResolver(R.string.game_kaerunyo_title),
                defaultDirName = "Kaerunyo",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_kaerunyo,
                accentColor = 0xFF43A047.toInt(),
                accentBorderColor = 0x6681C784,
                iconFrameColor = 0xFF112113.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.KAERUNYO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_ATLACHNACHA -> GameDescriptor(
                id = ID_ATLACHNACHA,
                title = stringResolver(R.string.game_atlachnacha_title),
                defaultDirName = "AtlachNacha",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_atlachnacha,
                accentColor = 0xFF6A1B9A.toInt(),
                accentBorderColor = 0x66BA68C8,
                iconFrameColor = 0xFF1E1126.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ATLACHNACHA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_REISHIKI -> GameDescriptor(
                id = ID_REISHIKI,
                title = stringResolver(R.string.game_reishiki_title),
                defaultDirName = "Reishiki",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_reishiki,
                accentColor = 0xFF00838F.toInt(),
                accentBorderColor = 0x664DD0E1,
                iconFrameColor = 0xFF0F2024.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.REISHIKI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_HARUURARE -> GameDescriptor(
                id = ID_HARUURARE,
                title = stringResolver(R.string.game_haruurare_title),
                defaultDirName = "Haruurare",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_haruurare,
                accentColor = 0xFF0288D1.toInt(),
                accentBorderColor = 0x664FC3F7,
                iconFrameColor = 0xFF0E1F2D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HARUURARE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_OKUSAMAJUN -> GameDescriptor(
                id = ID_OKUSAMAJUN,
                title = stringResolver(R.string.game_okusamajun_title),
                defaultDirName = "OkusamaJun",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_okusamajun,
                accentColor = 0xFFE91E63.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2B141D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.OKUSAMAJUN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_OKUSAMANTR -> GameDescriptor(
                id = ID_OKUSAMANTR,
                title = stringResolver(R.string.game_okusamantr_title),
                defaultDirName = "OkusamaNtr",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_okusamantr,
                accentColor = 0xFF3949AB.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF14182B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.OKUSAMANTR_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_EVENICLE2RANCE -> GameDescriptor(
                id = ID_EVENICLE2RANCE,
                title = stringResolver(R.string.game_evenicle2rance_title),
                defaultDirName = "Evenicle2Rance",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_evenicle2rance,
                accentColor = 0xFF2E7D32.toInt(),
                accentBorderColor = 0x6681C784,
                iconFrameColor = 0xFF112316.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.EVENICLE2RANCE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_EVENICLEGAIDEN -> GameDescriptor(
                id = ID_EVENICLEGAIDEN,
                title = stringResolver(R.string.game_eveniclegaiden_title),
                defaultDirName = "EvenicleGaiden",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_eveniclegaiden,
                accentColor = 0xFF00897B.toInt(),
                accentBorderColor = 0x664DB6AC,
                iconFrameColor = 0xFF0E2220.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.EVENICLEGAIDEN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_WAIDONYO -> GameDescriptor(
                id = ID_WAIDONYO,
                title = stringResolver(R.string.game_waidonyo_title),
                defaultDirName = "Waidonyo",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_waidonyo,
                accentColor = 0xFF5E35B1.toInt(),
                accentBorderColor = 0x669575CD,
                iconFrameColor = 0xFF1A122B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.WAIDONYO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_RANCE6AFTER -> GameDescriptor(
                id = ID_RANCE6AFTER,
                title = stringResolver(R.string.game_rance6after_title),
                defaultDirName = "Rance6After",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance6after,
                accentColor = 0xFF00796B.toInt(),
                accentBorderColor = 0x664DB6AC,
                iconFrameColor = 0xFF0E1F1D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE6AFTER_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_YAKATA2 -> GameDescriptor(
                id = ID_YAKATA2,
                title = stringResolver(R.string.game_yakata2_title),
                defaultDirName = "Yakata2",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_yakata2,
                accentColor = 0xFFF57C00.toInt(),
                accentBorderColor = 0x66FFB74D,
                iconFrameColor = 0xFF281A0E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.YAKATA2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_SDPSMARIA -> GameDescriptor(
                id = ID_SDPSMARIA,
                title = stringResolver(R.string.game_sdpsmaria_title),
                defaultDirName = "SuperDpsMaria",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sdpsmaria,
                accentColor = 0xFFAD1457.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF28121B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SDPSMARIA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_TOUSHIN3 -> GameDescriptor(
                id = ID_TOUSHIN3,
                title = stringResolver(R.string.game_toushin3_title),
                defaultDirName = "Toushin3",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_toushin3,
                accentColor = 0xFF1565C0.toInt(),
                accentBorderColor = 0x6664B5F6,
                iconFrameColor = 0xFF0E1D2F.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TOUSHIN3_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_OYAKORANKAN -> GameDescriptor(
                id = ID_OYAKORANKAN,
                title = stringResolver(R.string.game_oyakorankan_title),
                defaultDirName = "Oyakorankan",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_oyakorankan,
                accentColor = 0xFFC2185B.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF2B121C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.OYAKORANKAN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_YORUGAKURU -> GameDescriptor(
                id = ID_YORUGAKURU,
                title = stringResolver(R.string.game_yorugakuru_title),
                defaultDirName = "Yorugakuru",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_yorugakuru,
                accentColor = 0xFF283593.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF11152A.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.YORUGAKURU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_RANCE1DIGEST -> GameDescriptor(
                id = ID_RANCE1DIGEST,
                title = stringResolver(R.string.game_rance1digest_title),
                defaultDirName = "Rance1Digest",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance1digest,
                accentColor = 0xFF2E7D32.toInt(),
                accentBorderColor = 0x6681C784,
                iconFrameColor = 0xFF112315.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE1DIGEST_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_MAMAN -> GameDescriptor(
                id = ID_MAMAN,
                title = stringResolver(R.string.game_maman_title),
                defaultDirName = "Maman",
                expectedAinName = "Maman.ain",
                iconRes = R.drawable.ic_game_maman,
                accentColor = 0xFFD81B60.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2B131E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MAMAN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_EVENICLERANCE -> GameDescriptor(
                id = ID_EVENICLERANCE,
                title = stringResolver(R.string.game_eveniclerance_title),
                defaultDirName = "EvenicleRance",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_eveniclerance,
                accentColor = 0xFF2E7D32.toInt(),
                accentBorderColor = 0x6681C784,
                iconFrameColor = 0xFF132617.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.EVENICLERANCE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_HAHARANMAN -> GameDescriptor(
                id = ID_HAHARANMAN,
                title = stringResolver(R.string.game_haharanman_title),
                defaultDirName = "Haharanman",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_haharanman,
                accentColor = 0xFFC2185B.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF29131C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HAHARANMAN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_TSUMAMIGUI2 -> GameDescriptor(
                id = ID_TSUMAMIGUI2,
                title = stringResolver(R.string.game_tsumamigui2_title),
                defaultDirName = "Tsumamigui2",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tsumamigui2,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF29131C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TSUMAMIGUI2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_TSUMAMIGUI3JUN -> GameDescriptor(
                id = ID_TSUMAMIGUI3JUN,
                title = stringResolver(R.string.game_tsumamigui3jun_title),
                defaultDirName = "Tsumamigui3Jun",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tsumamigui3jun,
                accentColor = 0xFFAD1457.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF28121B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TSUMAMIGUI3JUN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_TSUMAMIGUI3NTR -> GameDescriptor(
                id = ID_TSUMAMIGUI3NTR,
                title = stringResolver(R.string.game_tsumamigui3ntr_title),
                defaultDirName = "Tsumamigui3Ntr",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tsumamigui3ntr,
                accentColor = 0xFF6A1B9A.toInt(),
                accentBorderColor = 0x66BA68C8,
                iconFrameColor = 0xFF201329.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TSUMAMIGUI3NTR_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_DIABOLIQUE -> GameDescriptor(
                id = ID_DIABOLIQUE,
                title = stringResolver(R.string.game_diabolique_title),
                defaultDirName = "DiaboLiQuE",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_diabolique,
                accentColor = 0xFFB71C1C.toInt(),
                accentBorderColor = 0x66EF5350,
                iconFrameColor = 0xFF231015.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DIABOLIQUE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_GAKUENKING -> GameDescriptor(
                id = ID_GAKUENKING,
                title = stringResolver(R.string.game_gakuenking_title),
                defaultDirName = "GakuenKING",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_gakuenking,
                accentColor = 0xFFEF6C00.toInt(),
                accentBorderColor = 0x66FFB74D,
                iconFrameColor = 0xFF261A0D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.GAKUENKING_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_GAKUENKINGEX -> GameDescriptor(
                id = ID_GAKUENKINGEX,
                title = stringResolver(R.string.game_gakuenkingex_title),
                defaultDirName = "GakuenKINGEx",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_gakuenkingex,
                accentColor = 0xFFF57F17.toInt(),
                accentBorderColor = 0x66FFD54F,
                iconFrameColor = 0xFF261E0D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.GAKUENKINGEX_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_OUDOUYUUSHA -> GameDescriptor(
                id = ID_OUDOUYUUSHA,
                title = stringResolver(R.string.game_oudouyuusha_title),
                defaultDirName = "OudouYuusha",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_oudouyuusha,
                accentColor = 0xFF1976D2.toInt(),
                accentBorderColor = 0x6664B5F6,
                iconFrameColor = 0xFF0E1B2B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.OUDOUYUUSHA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_TOUSHIN1 -> GameDescriptor(
                id = ID_TOUSHIN1,
                title = stringResolver(R.string.game_toushin1_title),
                defaultDirName = "Toushin1",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_toushin1,
                accentColor = 0xFF283593.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF11152A.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TOUSHIN1_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_TOUSHIN2 -> GameDescriptor(
                id = ID_TOUSHIN2,
                title = stringResolver(R.string.game_toushin2_title),
                defaultDirName = "Toushin2",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_toushin2,
                accentColor = 0xFF0277BD.toInt(),
                accentBorderColor = 0x664FC3F7,
                iconFrameColor = 0xFF0D1D2B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TOUSHIN2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DARCROWS -> GameDescriptor(
                id = ID_DARCROWS,
                title = stringResolver(R.string.game_darcrows_title),
                defaultDirName = "DARCROWS",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_darcrows,
                accentColor = 0xFF4A148C.toInt(),
                accentBorderColor = 0x66BA68C8,
                iconFrameColor = 0xFF1A1026.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DARCROWS_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_IKUSAMIKO -> GameDescriptor(
                id = ID_IKUSAMIKO,
                title = stringResolver(R.string.game_ikusamiko_title),
                defaultDirName = "IkusaMiko",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_ikusamiko,
                accentColor = 0xFFC62828.toInt(),
                accentBorderColor = 0x66EF5350,
                iconFrameColor = 0xFF261113.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.IKUSAMIKO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_ONLYYOU -> GameDescriptor(
                id = ID_ONLYYOU,
                title = stringResolver(R.string.game_onlyyou_title),
                defaultDirName = "OnlyYou",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_onlyyou,
                accentColor = 0xFFAD1457.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF28121D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ONLYYOU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SANBIKI -> GameDescriptor(
                id = ID_SANBIKI,
                title = stringResolver(R.string.game_sanbiki_title),
                defaultDirName = "Sanbiki",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sanbiki,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF7043,
                iconFrameColor = 0xFF2B140E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SANBIKI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_PERSIOM -> GameDescriptor(
                id = ID_PERSIOM,
                title = stringResolver(R.string.game_persiom_title),
                defaultDirName = "PERSIOM",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_persiom,
                accentColor = 0xFF1565C0.toInt(),
                accentBorderColor = 0x6642A5F5,
                iconFrameColor = 0xFF0E1D30.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PERSIOM_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SEEINAO -> GameDescriptor(
                id = ID_SEEINAO,
                title = stringResolver(R.string.game_seeinao_title),
                defaultDirName = "SeeInAO",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_seeinao,
                accentColor = 0xFF00838F.toInt(),
                accentBorderColor = 0x6626C6DA,
                iconFrameColor = 0xFF0D2327.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SEEINAO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_HUSHABY -> GameDescriptor(
                id = ID_HUSHABY,
                title = stringResolver(R.string.game_hushaby_title),
                defaultDirName = "HushabyBaby",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_hushaby,
                accentColor = 0xFF4527A0.toInt(),
                accentBorderColor = 0x667E57C2,
                iconFrameColor = 0xFF1A142D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HUSHABY_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_AYUMI -> GameDescriptor(
                id = ID_AYUMI,
                title = stringResolver(R.string.game_ayumi_title),
                defaultDirName = "AyumiChan",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_ayumi,
                accentColor = 0xFFC2185B.toInt(),
                accentBorderColor = 0x66EC407A,
                iconFrameColor = 0xFF2A121C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.AYUMI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_CIELCLAIRE -> GameDescriptor(
                id = ID_CIELCLAIRE,
                title = stringResolver(R.string.game_cielclaire_title),
                defaultDirName = "CielClaire",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_cielclaire,
                accentColor = 0xFF6A1B9A.toInt(),
                accentBorderColor = 0x66AB47BC,
                iconFrameColor = 0xFF21122A.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.CIELCLAIRE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_NIGHTDEMON -> GameDescriptor(
                id = ID_NIGHTDEMON,
                title = stringResolver(R.string.game_nightdemon_title),
                defaultDirName = "NightDemon",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_nightdemon,
                accentColor = 0xFF283593.toInt(),
                accentBorderColor = 0x665C6BC0,
                iconFrameColor = 0xFF14182D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.NIGHTDEMON_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_KATSUMI -> GameDescriptor(
                id = ID_KATSUMI,
                title = stringResolver(R.string.game_katsumi_title),
                defaultDirName = "Katsumi",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_katsumi,
                accentColor = 0xFF00897B.toInt(),
                accentBorderColor = 0x664DB6AC,
                iconFrameColor = 0xFF102523.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.KATSUMI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DRSTOP -> GameDescriptor(
                id = ID_DRSTOP,
                title = stringResolver(R.string.game_drstop_title),
                defaultDirName = "DrStop",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_drstop,
                accentColor = 0xFF0277BD.toInt(),
                accentBorderColor = 0x6629B6F6,
                iconFrameColor = 0xFF0F202D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DRSTOP_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSALL -> GameDescriptor(
                id = ID_DPSALL,
                title = stringResolver(R.string.game_dpsall_title),
                defaultDirName = "DpsAll",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpsall,
                accentColor = 0xFFEF6C00.toInt(),
                accentBorderColor = 0x66FFA726,
                iconFrameColor = 0xFF2B1C0E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSALL_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_KOREDPS -> GameDescriptor(
                id = ID_KOREDPS,
                title = stringResolver(R.string.game_koredps_title),
                defaultDirName = "KoreDps",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_koredps,
                accentColor = 0xFFF57F17.toInt(),
                accentBorderColor = 0x66FFCA28,
                iconFrameColor = 0xFF2B210E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.KOREDPS_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_KAERUALICE -> GameDescriptor(
                id = ID_KAERUALICE,
                title = stringResolver(R.string.game_kaerualice_title),
                defaultDirName = "KaeruAlice",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_kaerualice,
                accentColor = 0xFF2E7D32.toInt(),
                accentBorderColor = 0x6666BB6A,
                iconFrameColor = 0xFF132615.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.KAERUALICE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PROSTUDENTG -> GameDescriptor(
                id = ID_PROSTUDENTG,
                title = stringResolver(R.string.game_prostudentg_title),
                defaultDirName = "ProstudentG",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_prostudentg,
                accentColor = 0xFF558B2F.toInt(),
                accentBorderColor = 0x669CCC65,
                iconFrameColor = 0xFF1A2512.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PROSTUDENTG_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PROSTUDENTGOOD -> GameDescriptor(
                id = ID_PROSTUDENTGOOD,
                title = stringResolver(R.string.game_prostudentgood_title),
                defaultDirName = "ProstudentGood",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_prostudentgood,
                accentColor = 0xFF9E9D24.toInt(),
                accentBorderColor = 0x66D4E157,
                iconFrameColor = 0xFF262611.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PROSTUDENTGOOD_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PASTELCHIME -> GameDescriptor(
                id = ID_PASTELCHIME,
                title = stringResolver(R.string.game_pastelchime_title),
                defaultDirName = "PastelChime",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_pastelchime,
                accentColor = 0xFFD81B60.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF2B121D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PASTELCHIME_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PASTELCHIME2 -> GameDescriptor(
                id = ID_PASTELCHIME2,
                title = stringResolver(R.string.game_pastelchime2_title),
                defaultDirName = "PastelChime2",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_pastelchime2,
                accentColor = 0xFF8E24AA.toInt(),
                accentBorderColor = 0x66BA68C8,
                iconFrameColor = 0xFF24132A.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PASTELCHIME2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_YAKATA3 -> GameDescriptor(
                id = ID_YAKATA3,
                title = stringResolver(R.string.game_yakata3_title),
                defaultDirName = "Yakata3",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_yakata3,
                accentColor = 0xFF00695C.toInt(),
                accentBorderColor = 0x6626A69A,
                iconFrameColor = 0xFF102421.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.YAKATA3_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DALK -> GameDescriptor(
                id = ID_DALK,
                title = stringResolver(R.string.game_dalk_title),
                defaultDirName = "DALK",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dalk,
                accentColor = 0xFF5D4037.toInt(),
                accentBorderColor = 0x668D6E63,
                iconFrameColor = 0xFF231916.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DALK_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_NINGENGARI -> GameDescriptor(
                id = ID_NINGENGARI,
                title = stringResolver(R.string.game_ningengari_title),
                defaultDirName = "NingenGari",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_ningengari,
                accentColor = 0xFF37474F.toInt(),
                accentBorderColor = 0x6678909C,
                iconFrameColor = 0xFF171D20.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.NINGENGARI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PASCHACPP -> GameDescriptor(
                id = ID_PASCHACPP,
                title = stringResolver(R.string.game_paschacpp_title),
                defaultDirName = "PaschaCpp",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_paschacpp,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2C141F.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PASCHACPP_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PASCHA3 -> GameDescriptor(
                id = ID_PASCHA3,
                title = stringResolver(R.string.game_pascha3_title),
                defaultDirName = "Pascha3",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_pascha3,
                accentColor = 0xFFE91E63.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF2B101A.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PASCHA3_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_MAMATOTO -> GameDescriptor(
                id = ID_MAMATOTO,
                title = stringResolver(R.string.game_mamatoto_title),
                defaultDirName = "Mamatoto",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_mamatoto,
                accentColor = 0xFF5C6BC0.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF191D32.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MAMATOTO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_ONLYYOURECROSS -> GameDescriptor(
                id = ID_ONLYYOURECROSS,
                title = stringResolver(R.string.game_onlyyourecross_title),
                defaultDirName = "OnlyYouReCross",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_onlyyourecross,
                accentColor = 0xFF7E57C2.toInt(),
                accentBorderColor = 0x66B39DDB,
                iconFrameColor = 0xFF221633.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ONLYYOURECROSS_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MAMORIGAMI -> GameDescriptor(
                id = ID_MAMORIGAMI,
                title = stringResolver(R.string.game_mamorigami_title),
                defaultDirName = "Mamorigami",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_mamorigami,
                accentColor = 0xFF26A69A.toInt(),
                accentBorderColor = 0x6680CBC4,
                iconFrameColor = 0xFF102826.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MAMORIGAMI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_AMBIVALENZ -> GameDescriptor(
                id = ID_AMBIVALENZ,
                title = stringResolver(R.string.game_ambivalenz_title),
                defaultDirName = "AmbivalenZ",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_ambivalenz,
                accentColor = 0xFF78909C.toInt(),
                accentBorderColor = 0x66B0BEC5,
                iconFrameColor = 0xFF1A2226.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.AMBIVALENZ_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_FUNNYBEE -> GameDescriptor(
                id = ID_FUNNYBEE,
                title = stringResolver(R.string.game_funnybee_title),
                defaultDirName = "FunnyBee",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_funnybee,
                accentColor = 0xFFFFB300.toInt(),
                accentBorderColor = 0x66FFD54F,
                iconFrameColor = 0xFF2E220C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.FUNNYBEE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_TOUSHIN2SP -> GameDescriptor(
                id = ID_TOUSHIN2SP,
                title = stringResolver(R.string.game_toushin2sp_title),
                defaultDirName = "Toushin2Sp",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_toushin2sp,
                accentColor = 0xFF42A5F5.toInt(),
                accentBorderColor = 0x6690CAF9,
                iconFrameColor = 0xFF122336.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TOUSHIN2SP_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MUGENHOUYOU -> GameDescriptor(
                id = ID_MUGENHOUYOU,
                title = stringResolver(R.string.game_mugenhouyou_title),
                defaultDirName = "MugenHouyou",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_mugenhouyou,
                accentColor = 0xFFC62828.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2B1111.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MUGENHOUYOU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_OTOMESENKI -> GameDescriptor(
                id = ID_OTOMESENKI,
                title = stringResolver(R.string.game_otomesenki_title),
                defaultDirName = "OtomeSenki",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_otomesenki,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF8A65,
                iconFrameColor = 0xFF2E1710.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.OTOMESENKI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_GAKUENHYOURYUU -> GameDescriptor(
                id = ID_GAKUENHYOURYUU,
                title = stringResolver(R.string.game_gakuenhyouryuu_title),
                defaultDirName = "GakuenHyouryuu",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_gakuenhyouryuu,
                accentColor = 0xFF29B6F6.toInt(),
                accentBorderColor = 0x6681D4FA,
                iconFrameColor = 0xFF102430.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.GAKUENHYOURYUU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MAIDNOSUSUME -> GameDescriptor(
                id = ID_MAIDNOSUSUME,
                title = stringResolver(R.string.game_maidnosusume_title),
                defaultDirName = "MaidNoSusume",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_maidnosusume,
                accentColor = 0xFFAB47BC.toInt(),
                accentBorderColor = 0x66CE93D8,
                iconFrameColor = 0xFF27142B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MAIDNOSUSUME_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DALKGAIDEN -> GameDescriptor(
                id = ID_DALKGAIDEN,
                title = stringResolver(R.string.game_dalkgaiden_title),
                defaultDirName = "DalkGaiden",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dalkgaiden,
                accentColor = 0xFF26A69A.toInt(),
                accentBorderColor = 0x6680CBC4,
                iconFrameColor = 0xFF0E2926.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DALKGAIDEN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MAMANYONYO -> GameDescriptor(
                id = ID_MAMANYONYO,
                title = stringResolver(R.string.game_mamanyonyo_title),
                defaultDirName = "Mamanyonyo",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_mamanyonyo,
                accentColor = 0xFFF06292.toInt(),
                accentBorderColor = 0x66F8BBD0,
                iconFrameColor = 0xFF2E1420.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MAMANYONYO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MAJONOSHOKUZAI -> GameDescriptor(
                id = ID_MAJONOSHOKUZAI,
                title = stringResolver(R.string.game_majonoshokuzai_title),
                defaultDirName = "MajoNoShokuzai",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_majonoshokuzai,
                accentColor = 0xFFE57373.toInt(),
                accentBorderColor = 0x66FFCDD2,
                iconFrameColor = 0xFF2D1616.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MAJONOSHOKUZAI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SABOTEN -> GameDescriptor(
                id = ID_SABOTEN,
                title = stringResolver(R.string.game_saboten_title),
                defaultDirName = "Saboten",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_saboten,
                accentColor = 0xFF66BB6A.toInt(),
                accentBorderColor = 0x66A5D6A7,
                iconFrameColor = 0xFF142917.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SABOTEN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DOUBLESENSEI -> GameDescriptor(
                id = ID_DOUBLESENSEI,
                title = stringResolver(R.string.game_doublesensei_title),
                defaultDirName = "DoubleSensei",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_doublesensei,
                accentColor = 0xFF9575CD.toInt(),
                accentBorderColor = 0x66D1C4E9,
                iconFrameColor = 0xFF211833.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DOUBLESENSEI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_OJOUSAMA -> GameDescriptor(
                id = ID_OJOUSAMA,
                title = stringResolver(R.string.game_ojousama_title),
                defaultDirName = "Ojousama",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_ojousama,
                accentColor = 0xFFFF7043.toInt(),
                accentBorderColor = 0x66FFAB91,
                iconFrameColor = 0xFF2E1810.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.OJOUSAMA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_EDORANZE -> GameDescriptor(
                id = ID_EDORANZE,
                title = stringResolver(R.string.game_edoranze_title),
                defaultDirName = "Edoranze",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_edoranze,
                accentColor = 0xFFFFCA28.toInt(),
                accentBorderColor = 0x66FFE082,
                iconFrameColor = 0xFF2E240E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.EDORANZE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_VANISH -> GameDescriptor(
                id = ID_VANISH,
                title = stringResolver(R.string.game_vanish_title),
                defaultDirName = "Vanish",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_vanish,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2D1420.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.VANISH_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_HOKENSHITSU -> GameDescriptor(
                id = ID_HOKENSHITSU,
                title = stringResolver(R.string.game_hokenshitsu_title),
                defaultDirName = "Hokenshitsu",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_hokenshitsu,
                accentColor = 0xFFEF5350.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2E1414.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HOKENSHITSU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_KAMUNAGARA -> GameDescriptor(
                id = ID_KAMUNAGARA,
                title = stringResolver(R.string.game_kamunagara_title),
                defaultDirName = "Kamunagara",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_kamunagara,
                accentColor = 0xFF3F51B5.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF141930.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.KAMUNAGARA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_ERUMEIFLOWER -> GameDescriptor(
                id = ID_ERUMEIFLOWER,
                title = stringResolver(R.string.game_erumeiflower_title),
                defaultDirName = "ErumeiFlower",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_erumeiflower,
                accentColor = 0xFFE91E63.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF2B101B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ERUMEIFLOWER_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_ERUMEIWAKARE -> GameDescriptor(
                id = ID_ERUMEIWAKARE,
                title = stringResolver(R.string.game_erumeiwakare_title),
                defaultDirName = "ErumeiWakare",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_erumeiwakare,
                accentColor = 0xFFFFB74D.toInt(),
                accentBorderColor = 0x66FFE0B2,
                iconFrameColor = 0xFF2D2112.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ERUMEIWAKARE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MANANA1 -> GameDescriptor(
                id = ID_MANANA1,
                title = stringResolver(R.string.game_manana1_title),
                defaultDirName = "Manana1",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_manana1,
                accentColor = 0xFFFF7043.toInt(),
                accentBorderColor = 0x66FFAB91,
                iconFrameColor = 0xFF2E1710.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MANANA1_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MANANA2 -> GameDescriptor(
                id = ID_MANANA2,
                title = stringResolver(R.string.game_manana2_title),
                defaultDirName = "Manana2",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_manana2,
                accentColor = 0xFF29B6F6.toInt(),
                accentBorderColor = 0x6681D4FA,
                iconFrameColor = 0xFF102430.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MANANA2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MANANA3 -> GameDescriptor(
                id = ID_MANANA3,
                title = stringResolver(R.string.game_manana3_title),
                defaultDirName = "Manana3",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_manana3,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2D1220.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MANANA3_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MANANA4 -> GameDescriptor(
                id = ID_MANANA4,
                title = stringResolver(R.string.game_manana4_title),
                defaultDirName = "Manana4",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_manana4,
                accentColor = 0xFFFFA726.toInt(),
                accentBorderColor = 0x66FFCC80,
                iconFrameColor = 0xFF2E1F0E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MANANA4_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MANANA5 -> GameDescriptor(
                id = ID_MANANA5,
                title = stringResolver(R.string.game_manana5_title),
                defaultDirName = "Manana5",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_manana5,
                accentColor = 0xFFAB47BC.toInt(),
                accentBorderColor = 0x66CE93D8,
                iconFrameColor = 0xFF26122B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MANANA5_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_TSUMASAKURA -> GameDescriptor(
                id = ID_TSUMASAKURA,
                title = stringResolver(R.string.game_tsumasakura_title),
                defaultDirName = "TsumaSakura",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tsumasakura,
                accentColor = 0xFFF48FB1.toInt(),
                accentBorderColor = 0x66F8BBD0,
                iconFrameColor = 0xFF2E1622.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TSUMASAKURA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SHAMANSSANCTUARY -> GameDescriptor(
                id = ID_SHAMANSSANCTUARY,
                title = stringResolver(R.string.game_shamanssanctuary_title),
                defaultDirName = "ShamansSanctuary",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_shamanssanctuary,
                accentColor = 0xFFE53935.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2E1114.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SHAMANSSANCTUARY_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_ALIVEZ -> GameDescriptor(
                id = ID_ALIVEZ,
                title = stringResolver(R.string.game_alivez_title),
                defaultDirName = "AliveZ",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_alivez,
                accentColor = 0xFF26C6DA.toInt(),
                accentBorderColor = 0x6680DEEA,
                iconFrameColor = 0xFF0E252B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ALIVEZ_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DUNGEONSDOLLS -> GameDescriptor(
                id = ID_DUNGEONSDOLLS,
                title = stringResolver(R.string.game_dungeonsdolls_title),
                defaultDirName = "DungeonsDolls",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dungeonsdolls,
                accentColor = 0xFF7E57C2.toInt(),
                accentBorderColor = 0x66B39DDB,
                iconFrameColor = 0xFF1E1630.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DUNGEONSDOLLS_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SUZUMIYA -> GameDescriptor(
                id = ID_SUZUMIYA,
                title = stringResolver(R.string.game_suzumiya_title),
                defaultDirName = "Suzumiya",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_suzumiya,
                accentColor = 0xFF8D6E63.toInt(),
                accentBorderColor = 0x66BCAAA4,
                iconFrameColor = 0xFF241B18.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SUZUMIYA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SHIMAIMA -> GameDescriptor(
                id = ID_SHIMAIMA,
                title = stringResolver(R.string.game_shimaima_title),
                defaultDirName = "Shimaima",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_shimaima,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2D1220.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SHIMAIMA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DAIBANCHOUGAIDEN -> GameDescriptor(
                id = ID_DAIBANCHOUGAIDEN,
                title = stringResolver(R.string.game_daibanchougaiden_title),
                defaultDirName = "DaibanchouGaiden",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_daibanchougaiden,
                accentColor = 0xFFFF7043.toInt(),
                accentBorderColor = 0x66FFAB91,
                iconFrameColor = 0xFF2E1710.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DAIBANCHOUGAIDEN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_COSPLAYLEMON -> GameDescriptor(
                id = ID_COSPLAYLEMON,
                title = stringResolver(R.string.game_cosplaylemon_title),
                defaultDirName = "CosplayLemon",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_cosplaylemon,
                accentColor = 0xFFFDD835.toInt(),
                accentBorderColor = 0x66FFF59D,
                iconFrameColor = 0xFF2E280D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.COSPLAYLEMON_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_WHIPHONEY -> GameDescriptor(
                id = ID_WHIPHONEY,
                title = stringResolver(R.string.game_whiphoney_title),
                defaultDirName = "WhipHoney",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_whiphoney,
                accentColor = 0xFFFFB74D.toInt(),
                accentBorderColor = 0x66FFE0B2,
                iconFrameColor = 0xFF2E2110.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.WHIPHONEY_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_ONIMUSU -> GameDescriptor(
                id = ID_ONIMUSU,
                title = stringResolver(R.string.game_onimusu_title),
                defaultDirName = "Onimusu",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_onimusu,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF8A65,
                iconFrameColor = 0xFF2E150E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.ONIMUSU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PROGRX -> GameDescriptor(
                id = ID_PROGRX,
                title = stringResolver(R.string.game_progrx_title),
                defaultDirName = "ProGrx",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_progrx,
                accentColor = 0xFF42A5F5.toInt(),
                accentBorderColor = 0x6690CAF9,
                iconFrameColor = 0xFF112233.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PROGRX_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSVERN -> GameDescriptor(
                id = ID_DPSVERN,
                title = stringResolver(R.string.game_dpsvern_title),
                defaultDirName = "DpsVerN",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpsvern,
                accentColor = 0xFF26A69A.toInt(),
                accentBorderColor = 0x6680CBC4,
                iconFrameColor = 0xFF0E2926.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSVERN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SUKIIRONO -> GameDescriptor(
                id = ID_SUKIIRONO,
                title = stringResolver(R.string.game_sukiirono_title),
                defaultDirName = "Sukiirono",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sukiirono,
                accentColor = 0xFF29B6F6.toInt(),
                accentBorderColor = 0x6681D4FA,
                iconFrameColor = 0xFF102430.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SUKIIRONO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_GIRLS -> GameDescriptor(
                id = ID_GIRLS,
                title = stringResolver(R.string.game_girls_title),
                defaultDirName = "Girls",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_girls,
                accentColor = 0xFFF06292.toInt(),
                accentBorderColor = 0x66F8BBD0,
                iconFrameColor = 0xFF2E1420.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.GIRLS_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DEBUT -> GameDescriptor(
                id = ID_DEBUT,
                title = stringResolver(R.string.game_debut_title),
                defaultDirName = "Debut",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_debut,
                accentColor = 0xFF66BB6A.toInt(),
                accentBorderColor = 0x66A5D6A7,
                iconFrameColor = 0xFF142917.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DEBUT_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_HARUKAVSESCA -> GameDescriptor(
                id = ID_HARUKAVSESCA,
                title = stringResolver(R.string.game_harukavsesca_title),
                defaultDirName = "HarukaVsEsca",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_harukavsesca,
                accentColor = 0xFFAB47BC.toInt(),
                accentBorderColor = 0x66CE93D8,
                iconFrameColor = 0xFF26122B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HARUKAVSESCA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_HARUKANINJA -> GameDescriptor(
                id = ID_HARUKANINJA,
                title = stringResolver(R.string.game_harukaninja_title),
                defaultDirName = "HarukaNinja",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_harukaninja,
                accentColor = 0xFFFFA726.toInt(),
                accentBorderColor = 0x66FFCC80,
                iconFrameColor = 0xFF2E1F0E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HARUKANINJA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MEGANE -> GameDescriptor(
                id = ID_MEGANE,
                title = stringResolver(R.string.game_megane_title),
                defaultDirName = "Megane",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_megane,
                accentColor = 0xFFE57373.toInt(),
                accentBorderColor = 0x66FFCDD2,
                iconFrameColor = 0xFF2D1616.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MEGANE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_RACHELSAKURA -> GameDescriptor(
                id = ID_RACHELSAKURA,
                title = stringResolver(R.string.game_rachelsakura_title),
                defaultDirName = "RachelSakura",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rachelsakura,
                accentColor = 0xFF26A69A.toInt(),
                accentBorderColor = 0x6680CBC4,
                iconFrameColor = 0xFF0E2926.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RACHELSAKURA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SABAKUNIKA -> GameDescriptor(
                id = ID_SABAKUNIKA,
                title = stringResolver(R.string.game_sabakunika_title),
                defaultDirName = "SabakuNika",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sabakunika,
                accentColor = 0xFF3F51B5.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF141930.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SABAKUNIKA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_VANISHPALM -> GameDescriptor(
                id = ID_VANISHPALM,
                title = stringResolver(R.string.game_vanishpalm_title),
                defaultDirName = "VanishPalm",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_vanishpalm,
                accentColor = 0xFFEF5350.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2E1414.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.VANISHPALM_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_BLADEBRIDERS -> GameDescriptor(
                id = ID_BLADEBRIDERS,
                title = stringResolver(R.string.game_bladebriders_title),
                defaultDirName = "BladeBriders",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_bladebriders,
                accentColor = 0xFF1976D2.toInt(),
                accentBorderColor = 0x6664B5F6,
                iconFrameColor = 0xFF0E1D30.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.BLADEBRIDERS_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_DORAPEKO -> GameDescriptor(
                id = ID_DORAPEKO,
                title = stringResolver(R.string.game_dorapeko_title),
                defaultDirName = "Dorapeko",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dorapeko,
                accentColor = 0xFFE65100.toInt(),
                accentBorderColor = 0x66FFB74D,
                iconFrameColor = 0xFF2D190C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DORAPEKO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_HANIWAPETIT -> GameDescriptor(
                id = ID_HANIWAPETIT,
                title = stringResolver(R.string.game_haniwapetit_title),
                defaultDirName = "HaniwaPetit",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_haniwapetit,
                accentColor = 0xFFFB8C00.toInt(),
                accentBorderColor = 0x66FFCC80,
                iconFrameColor = 0xFF2D2010.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HANIWAPETIT_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false,
                hasChinese = true
            )
            ID_YAKATA456 -> GameDescriptor(
                id = ID_YAKATA456,
                title = stringResolver(R.string.game_yakata456_title),
                defaultDirName = "Yakata456",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_yakata456,
                accentColor = 0xFF3949AB.toInt(),
                accentBorderColor = 0x669FA8DA,
                iconFrameColor = 0xFF151930.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.YAKATA456_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_UMINIOCHITE -> GameDescriptor(
                id = ID_UMINIOCHITE,
                title = stringResolver(R.string.game_uminiochite_title),
                defaultDirName = "UmiNiOchite",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_uminiochite,
                accentColor = 0xFF0288D1.toInt(),
                accentBorderColor = 0x664FC3F7,
                iconFrameColor = 0xFF0C202D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.UMINIOCHITE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_GAKUENHYOURYUU2 -> GameDescriptor(
                id = ID_GAKUENHYOURYUU2,
                title = stringResolver(R.string.game_gakuenhyouryuu2_title),
                defaultDirName = "GakuenHyouryuu2",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_gakuenhyouryuu2,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF8A65,
                iconFrameColor = 0xFF2D1610.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.GAKUENHYOURYUU2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_YOUSEI -> GameDescriptor(
                id = ID_YOUSEI,
                title = stringResolver(R.string.game_yousei_title),
                defaultDirName = "Yousei",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_yousei,
                accentColor = 0xFF00838F.toInt(),
                accentBorderColor = 0x6680DEEA,
                iconFrameColor = 0xFF0E2326.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.YOUSEI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_TOBIRA -> GameDescriptor(
                id = ID_TOBIRA,
                title = stringResolver(R.string.game_tobira_title),
                defaultDirName = "Tobira",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tobira,
                accentColor = 0xFF8D6E63.toInt(),
                accentBorderColor = 0x66BCAAA4,
                iconFrameColor = 0xFF261E1B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TOBIRA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_BUROBURO -> GameDescriptor(
                id = ID_BUROBURO,
                title = stringResolver(R.string.game_buroburo_title),
                defaultDirName = "Buroburo",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_buroburo,
                accentColor = 0xFFE53935.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2E1212.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.BUROBURO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_KUDAKERO -> GameDescriptor(
                id = ID_KUDAKERO,
                title = stringResolver(R.string.game_kudakero_title),
                defaultDirName = "Kudakero",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_kudakero,
                accentColor = 0xFFF4511E.toInt(),
                accentBorderColor = 0x66FFAB91,
                iconFrameColor = 0xFF2E1710.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.KUDAKERO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_LITTLEVAMPIRE -> GameDescriptor(
                id = ID_LITTLEVAMPIRE,
                title = stringResolver(R.string.game_littlevampire_title),
                defaultDirName = "LittleVampire",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_littlevampire,
                accentColor = 0xFF8E24AA.toInt(),
                accentBorderColor = 0x66CE93D8,
                iconFrameColor = 0xFF24102B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.LITTLEVAMPIRE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_INTRUDER -> GameDescriptor(
                id = ID_INTRUDER,
                title = stringResolver(R.string.game_intruder_title),
                defaultDirName = "Intruder",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_intruder,
                accentColor = 0xFF283593.toInt(),
                accentBorderColor = 0x667986CB,
                iconFrameColor = 0xFF11162E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.INTRUDER_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_CRESCENT -> GameDescriptor(
                id = ID_CRESCENT,
                title = stringResolver(R.string.game_crescent_title),
                defaultDirName = "CrescentMoonGirl",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_crescent,
                accentColor = 0xFF00ACC1.toInt(),
                accentBorderColor = 0x6680DEEA,
                iconFrameColor = 0xFF0E262B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.CRESCENT_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_TENGU -> GameDescriptor(
                id = ID_TENGU,
                title = stringResolver(R.string.game_tengu_title),
                defaultDirName = "AbunaiTengu",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tengu,
                accentColor = 0xFFC62828.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2B1111.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TENGU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPS -> GameDescriptor(
                id = ID_DPS,
                title = stringResolver(R.string.game_dps_title),
                defaultDirName = "DPS",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dps,
                accentColor = 0xFF1E88E5.toInt(),
                accentBorderColor = 0x6690CAF9,
                iconFrameColor = 0xFF102030.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPS_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_YAKATA1 -> GameDescriptor(
                id = ID_YAKATA1,
                title = stringResolver(R.string.game_yakata1_title),
                defaultDirName = "Yakata1",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_yakata1,
                accentColor = 0xFF43A047.toInt(),
                accentBorderColor = 0x66A5D6A7,
                iconFrameColor = 0xFF122814.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.YAKATA1_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SDPSTONO -> GameDescriptor(
                id = ID_SDPSTONO,
                title = stringResolver(R.string.game_sdpstono_title),
                defaultDirName = "SuperDpsTono",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sdpstono,
                accentColor = 0xFF5E35B1.toInt(),
                accentBorderColor = 0x66B39DDB,
                iconFrameColor = 0xFF1B132E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SDPSTONO_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SDPSKAIZOKU -> GameDescriptor(
                id = ID_SDPSKAIZOKU,
                title = stringResolver(R.string.game_sdpskaizoku_title),
                defaultDirName = "SuperDpsKaizoku",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sdpskaizoku,
                accentColor = 0xFF00897B.toInt(),
                accentBorderColor = 0x6680CBC4,
                iconFrameColor = 0xFF0E2623.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SDPSKAIZOKU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_PROGOMAKE -> GameDescriptor(
                id = ID_PROGOMAKE,
                title = stringResolver(R.string.game_progomake_title),
                defaultDirName = "ProstudentGOmake",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_progomake,
                accentColor = 0xFF546E7A.toInt(),
                accentBorderColor = 0x66B0BEC5,
                iconFrameColor = 0xFF1A2226.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.PROGOMAKE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_AYUMIHINT -> GameDescriptor(
                id = ID_AYUMIHINT,
                title = stringResolver(R.string.game_ayumihint_title),
                defaultDirName = "AyumiHint",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_ayumihint,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2D121D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.AYUMIHINT_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_AYUMILIVE -> GameDescriptor(
                id = ID_AYUMILIVE,
                title = stringResolver(R.string.game_ayumilive_title),
                defaultDirName = "AyumiLive",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_ayumilive,
                accentColor = 0xFFD81B60.toInt(),
                accentBorderColor = 0x66F06292,
                iconFrameColor = 0xFF2B101A.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.AYUMILIVE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_NISENAGURI -> GameDescriptor(
                id = ID_NISENAGURI,
                title = stringResolver(R.string.game_nisenaguri_title),
                defaultDirName = "NiseNaguri",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_nisenaguri,
                accentColor = 0xFF6D4C41.toInt(),
                accentBorderColor = 0x66BCAAA4,
                iconFrameColor = 0xFF241916.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.NISENAGURI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_CAROL -> GameDescriptor(
                id = ID_CAROL,
                title = stringResolver(R.string.game_carol_title),
                defaultDirName = "Carol",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_carol,
                accentColor = 0xFF039BE5.toInt(),
                accentBorderColor = 0x6681D4FA,
                iconFrameColor = 0xFF0D212D.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.CAROL_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_OTHELLOT -> GameDescriptor(
                id = ID_OTHELLOT,
                title = stringResolver(R.string.game_othellot_title),
                defaultDirName = "Othellot",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_othellot,
                accentColor = 0xFF2E7D32.toInt(),
                accentBorderColor = 0x6681C784,
                iconFrameColor = 0xFF112613.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.OTHELLOT_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MASQUERADE -> GameDescriptor(
                id = ID_MASQUERADE,
                title = stringResolver(R.string.game_masquerade_title),
                defaultDirName = "Masquerade",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_masquerade,
                accentColor = 0xFFFDD835.toInt(),
                accentBorderColor = 0x66FFF176,
                iconFrameColor = 0xFF2B2610.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MASQUERADE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DEKIRUKANA -> GameDescriptor(
                id = ID_DEKIRUKANA,
                title = stringResolver(R.string.game_dekirukana_title),
                defaultDirName = "Dekirukana",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dekirukana,
                accentColor = 0xFF78909C.toInt(),
                accentBorderColor = 0x66CFD8DC,
                iconFrameColor = 0xFF1C2327.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DEKIRUKANA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_TATAKIKOROSHI -> GameDescriptor(
                id = ID_TATAKIKOROSHI,
                title = stringResolver(R.string.game_tatakikoroshi_title),
                defaultDirName = "Tatakikoroshi",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_tatakikoroshi,
                accentColor = 0xFFAB47BC.toInt(),
                accentBorderColor = 0x66E1BEE7,
                iconFrameColor = 0xFF28132C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.TATAKIKOROSHI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_BALLOONPOP -> GameDescriptor(
                id = ID_BALLOONPOP,
                title = stringResolver(R.string.game_balloonpop_title),
                defaultDirName = "BalloonPop",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_balloonpop,
                accentColor = 0xFFFF7043.toInt(),
                accentBorderColor = 0x66FFAB91,
                iconFrameColor = 0xFF2D1912.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.BALLOONPOP_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_HIYOKO2 -> GameDescriptor(
                id = ID_HIYOKO2,
                title = stringResolver(R.string.game_hiyoko2_title),
                defaultDirName = "Hiyoko2",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_hiyoko2,
                accentColor = 0xFFFFCA28.toInt(),
                accentBorderColor = 0x66FFE082,
                iconFrameColor = 0xFF2B230E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.HIYOKO2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SELFGAME -> GameDescriptor(
                id = ID_SELFGAME,
                title = stringResolver(R.string.game_selfgame_title),
                defaultDirName = "SelfGame",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_selfgame,
                accentColor = 0xFF7E57C2.toInt(),
                accentBorderColor = 0x66B39DDB,
                iconFrameColor = 0xFF1F162E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SELFGAME_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_MOMOIROGUARDIAN -> GameDescriptor(
                id = ID_MOMOIROGUARDIAN,
                title = stringResolver(R.string.game_momoiroguardian_title),
                defaultDirName = "MomoiroGuardian",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_momoiroguardian,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2D141F.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.MOMOIROGUARDIAN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_RANCE1CLASSIC -> GameDescriptor(
                id = ID_RANCE1CLASSIC,
                title = stringResolver(R.string.game_rance1classic_title),
                defaultDirName = "Rance1Classic",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance1classic,
                accentColor = 0xFF26A69A.toInt(),
                accentBorderColor = 0x6680CBC4,
                iconFrameColor = 0xFF0E2926.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE1CLASSIC_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_RANCE2CLASSIC -> GameDescriptor(
                id = ID_RANCE2CLASSIC,
                title = stringResolver(R.string.game_rance2classic_title),
                defaultDirName = "Rance2Classic",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance2classic,
                accentColor = 0xFF43A047.toInt(),
                accentBorderColor = 0x6681C784,
                iconFrameColor = 0xFF102A14.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE2CLASSIC_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_RANCE3CLASSIC -> GameDescriptor(
                id = ID_RANCE3CLASSIC,
                title = stringResolver(R.string.game_rance3classic_title),
                defaultDirName = "Rance3Classic",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_rance3classic,
                accentColor = 0xFFFB8C00.toInt(),
                accentBorderColor = 0x66FFB74D,
                iconFrameColor = 0xFF2E1E0C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.RANCE3CLASSIC_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSGFAHREN -> GameDescriptor(
                id = ID_DPSSGFAHREN,
                title = stringResolver(R.string.game_dpssgfahren_title),
                defaultDirName = "DpsSgFahren",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssgfahren,
                accentColor = 0xFFE53935.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2C1010.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSGFAHREN_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSGKATEI -> GameDescriptor(
                id = ID_DPSSGKATEI,
                title = stringResolver(R.string.game_dpssgkatei_title),
                defaultDirName = "DpsSgKatei",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssgkatei,
                accentColor = 0xFF00ACC1.toInt(),
                accentBorderColor = 0x6680DEEA,
                iconFrameColor = 0xFF0E272B.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSGKATEI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSGNOBUNAGA -> GameDescriptor(
                id = ID_DPSSGNOBUNAGA,
                title = stringResolver(R.string.game_dpssgnobunaga_title),
                defaultDirName = "DpsSgNobunaga",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssgnobunaga,
                accentColor = 0xFF3949AB.toInt(),
                accentBorderColor = 0x669FA8DA,
                iconFrameColor = 0xFF121630.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSGNOBUNAGA_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSG2ANTIQUE -> GameDescriptor(
                id = ID_DPSSG2ANTIQUE,
                title = stringResolver(R.string.game_dpssg2antique_title),
                defaultDirName = "DpsSg2Antique",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssg2antique,
                accentColor = 0xFF5C6BC0.toInt(),
                accentBorderColor = 0x669FA8DA,
                iconFrameColor = 0xFF151930.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSG2ANTIQUE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSG2IKENAI -> GameDescriptor(
                id = ID_DPSSG2IKENAI,
                title = stringResolver(R.string.game_dpssg2ikenai_title),
                defaultDirName = "DpsSg2Ikenai",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssg2ikenai,
                accentColor = 0xFFD81B60.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2B101C.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSG2IKENAI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSG2AKAI -> GameDescriptor(
                id = ID_DPSSG2AKAI,
                title = stringResolver(R.string.game_dpssg2akai_title),
                defaultDirName = "DpsSg2Akai",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssg2akai,
                accentColor = 0xFFC62828.toInt(),
                accentBorderColor = 0x66EF9A9A,
                iconFrameColor = 0xFF2B0E0E.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSG2AKAI_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSG3RABBIT -> GameDescriptor(
                id = ID_DPSSG3RABBIT,
                title = stringResolver(R.string.game_dpssg3rabbit_title),
                defaultDirName = "DpsSg3Rabbit",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssg3rabbit,
                accentColor = 0xFFEC407A.toInt(),
                accentBorderColor = 0x66F48FB1,
                iconFrameColor = 0xFF2D141F.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSG3RABBIT_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSG3SHINKON -> GameDescriptor(
                id = ID_DPSSG3SHINKON,
                title = stringResolver(R.string.game_dpssg3shinkon_title),
                defaultDirName = "DpsSg3Shinkon",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssg3shinkon,
                accentColor = 0xFFFF7043.toInt(),
                accentBorderColor = 0x66FFAB91,
                iconFrameColor = 0xFF2E1810.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSG3SHINKON_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_DPSSG3SOTSUGYOU -> GameDescriptor(
                id = ID_DPSSG3SOTSUGYOU,
                title = stringResolver(R.string.game_dpssg3sotsugyou_title),
                defaultDirName = "DpsSg3Sotsugyou",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_dpssg3sotsugyou,
                accentColor = 0xFF43A047.toInt(),
                accentBorderColor = 0x66A5D6A7,
                iconFrameColor = 0xFF102A14.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.DPSSG3SOTSUGYOU_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            else -> {
                val customDir = gameId.removePrefix(PREFIX_CUSTOM).ifBlank { "Game" }
                val matched = gameList?.let { list ->
                    (0 until list.size).map { list[it] }.firstOrNull { it.path.name == customDir }
                }
                val title = matched?.let { cleanItemTitle(it.name) } ?: customDir
                GameDescriptor(
                    id = "$PREFIX_CUSTOM$customDir",
                    title = title,
                    defaultDirName = customDir,
                    expectedAinName = null,
                    iconRes = R.drawable.ic_launcher,
                    accentColor = 0xFF3F51B5.toInt(),
                    accentBorderColor = 0x665C6BC0,
                    iconFrameColor = 0xFF232733.toInt(),
                    supportsOnlineBaseInstall = false,
                    onlineBaseCdnUrl = null,
                    supportsOnlineMod = false,
                    supportsLocalMod = false,
                    isRance10 = false
                )
            }
        }
    }

    private val BUILT_IN_SELECT_ORDER: List<String> = listOf(
        ID_RANCE10,
        ID_DOHNA_DOHNA,
        ID_EVENICLE,
        ID_EVENICLE2,
        ID_RANCE01,
        ID_RANCE02,
        ID_RANCE03,
        ID_RANCE04,
        ID_RANCE41,
        ID_RANCE42,
        ID_RANCE5D,
        ID_RANCE06,
        ID_SENGOKU_RANCE,
        ID_RANCE08,
        ID_RANCE09,
        ID_KICHIKUOU,
        ID_DAIAKUJI,
        ID_DAIBANCHOU,
        ID_DAITEIKOKU,
        ID_ESCALAYER,
        ID_ESCALAYER_REBOOT,
        ID_GALZOO,
        ID_IXSEAL,
        ID_HARUKA,
        ID_TSUMASHIBORI,
        ID_TSUMAMIGUI,
        ID_TSUMAMIGUI3,
        ID_KAERUNYO,
        ID_ATLACHNACHA,
        ID_REISHIKI,
        ID_HARUURARE,
        ID_OKUSAMAJUN,
        ID_OKUSAMANTR,
        ID_EVENICLE2RANCE,
        ID_EVENICLEGAIDEN,
        ID_WAIDONYO,
        ID_RANCE6AFTER,
        ID_YAKATA2,
        ID_SDPSMARIA,
        ID_TOUSHIN3,
        ID_OYAKORANKAN,
        ID_YORUGAKURU,
        ID_RANCE1DIGEST,
        ID_MAMAN,
        ID_EVENICLERANCE,
        ID_HAHARANMAN,
        ID_TSUMAMIGUI2,
        ID_TSUMAMIGUI3JUN,
        ID_TSUMAMIGUI3NTR,
        ID_DIABOLIQUE,
        ID_GAKUENKING,
        ID_GAKUENKINGEX,
        ID_OUDOUYUUSHA,
        ID_TOUSHIN1,
        ID_TOUSHIN2,
        ID_DARCROWS,
        ID_IKUSAMIKO,
        ID_ONLYYOU,
        ID_SANBIKI,
        ID_PERSIOM,
        ID_SEEINAO,
        ID_HUSHABY,
        ID_AYUMI,
        ID_CIELCLAIRE,
        ID_NIGHTDEMON,
        ID_KATSUMI,
        ID_DRSTOP,
        ID_DPSALL,
        ID_KOREDPS,
        ID_KAERUALICE,
        ID_PROSTUDENTG,
        ID_PROSTUDENTGOOD,
        ID_PASTELCHIME,
        ID_PASTELCHIME2,
        ID_YAKATA3,
        ID_DALK,
        ID_NINGENGARI,
        ID_PASCHACPP,
        ID_PASCHA3,
        ID_MAMATOTO,
        ID_ONLYYOURECROSS,
        ID_MAMORIGAMI,
        ID_AMBIVALENZ,
        ID_FUNNYBEE,
        ID_TOUSHIN2SP,
        ID_MUGENHOUYOU,
        ID_OTOMESENKI,
        ID_GAKUENHYOURYUU,
        ID_MAIDNOSUSUME,
        ID_DALKGAIDEN,
        ID_MAMANYONYO,
        ID_MAJONOSHOKUZAI,
        ID_SABOTEN,
        ID_DOUBLESENSEI,
        ID_OJOUSAMA,
        ID_EDORANZE,
        ID_VANISH,
        ID_HOKENSHITSU,
        ID_KAMUNAGARA,
        ID_ERUMEIFLOWER,
        ID_ERUMEIWAKARE,
        ID_MANANA1,
        ID_MANANA2,
        ID_MANANA3,
        ID_MANANA4,
        ID_MANANA5,
        ID_TSUMASAKURA,
        ID_SHAMANSSANCTUARY,
        ID_ALIVEZ,
        ID_DUNGEONSDOLLS,
        ID_SUZUMIYA,
        ID_SHIMAIMA,
        ID_DAIBANCHOUGAIDEN,
        ID_COSPLAYLEMON,
        ID_WHIPHONEY,
        ID_ONIMUSU,
        ID_PROGRX,
        ID_DPSVERN,
        ID_SUKIIRONO,
        ID_GIRLS,
        ID_DEBUT,
        ID_HARUKAVSESCA,
        ID_HARUKANINJA,
        ID_MEGANE,
        ID_RACHELSAKURA,
        ID_SABAKUNIKA,
        ID_VANISHPALM,
        ID_BLADEBRIDERS,
        ID_DORAPEKO,
        ID_HANIWAPETIT,
        ID_YAKATA456,
        ID_UMINIOCHITE,
        ID_GAKUENHYOURYUU2,
        ID_YOUSEI,
        ID_TOBIRA,
        ID_BUROBURO,
        ID_KUDAKERO,
        ID_LITTLEVAMPIRE,
        ID_INTRUDER,
        ID_CRESCENT,
        ID_TENGU,
        ID_DPS,
        ID_YAKATA1,
        ID_SDPSTONO,
        ID_SDPSKAIZOKU,
        ID_PROGOMAKE,
        ID_AYUMIHINT,
        ID_AYUMILIVE,
        ID_NISENAGURI,
        ID_CAROL,
        ID_OTHELLOT,
        ID_MASQUERADE,
        ID_DEKIRUKANA,
        ID_TATAKIKOROSHI,
        ID_BALLOONPOP,
        ID_HIYOKO2,
        ID_SELFGAME,
        ID_MOMOIROGUARDIAN,
        ID_RANCE1CLASSIC,
        ID_RANCE2CLASSIC,
        ID_RANCE3CLASSIC,
        ID_DPSSGFAHREN,
        ID_DPSSGKATEI,
        ID_DPSSGNOBUNAGA,
        ID_DPSSG2ANTIQUE,
        ID_DPSSG2IKENAI,
        ID_DPSSG2AKAI,
        ID_DPSSG3RABBIT,
        ID_DPSSG3SHINKON,
        ID_DPSSG3SOTSUGYOU
    )

    private val BUILT_IN_SELECT_ORDER_SET: Set<String> = BUILT_IN_SELECT_ORDER.toHashSet()

    @JvmStatic
    fun builtInSelectOrder(): List<String> = BUILT_IN_SELECT_ORDER

    @JvmStatic
    fun isBuiltInGameId(gameId: String): Boolean = gameId in BUILT_IN_SELECT_ORDER_SET

    fun buildSelectEntries(context: Context, gameList: GameList): List<GameSelectEntry> {
        val grouped = HashMap<String, MutableList<Item>>()
        val customOrder = ArrayList<Pair<String, Item>>()
        for (i in 0 until gameList.size) {
            val item = gameList[i]
            val cid = classifyItem(item)
            if (cid in BUILT_IN_SELECT_ORDER_SET) {
                grouped.getOrPut(cid) { ArrayList() }.add(item)
            } else {
                customOrder.add(cid to item)
            }
        }
        for (bucket in grouped.values) {
            if (bucket.size > 1) {
                bucket.sortBy { it.path.name }
            }
        }

        val entries = ArrayList<GameSelectEntry>(BUILT_IN_SELECT_ORDER.size + customOrder.size)
        for (gid in BUILT_IN_SELECT_ORDER) {
            val desc = descriptorFor(context, gid, gameList)
            val matched = grouped[gid] ?: emptyList()
            entries.add(GameSelectEntry(desc, matched, matched.firstOrNull()))
        }
        for ((cid, item) in customOrder) {
            val desc = descriptorFor(context, cid, gameList)
            entries.add(GameSelectEntry(desc, listOf(item), item))
        }
        return GameSorting.sorted(
            entries,
            AppSettings.getGameSortMode(context),
            AppSettings.resolveLocale(context),
            hasChinese = { entry ->
                val primary = entry.primaryItem
                val modInfo = if (primary != null) gameList.getInstalledModInfo(primary) else null
                hasChineseTranslation(entry.descriptor, primary, modInfo)
            }
        )
    }

    fun writeRunningGameRoot(context: Context, gameRoot: String) {
        try {
            File(context.filesDir, RUNNING_GAME_FILE).writeText(gameRoot, Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    fun writeRunningLaunchTarget(context: Context, launchTarget: String) {
        try {
            File(context.filesDir, RUNNING_LAUNCH_TARGET_FILE).writeText(launchTarget, Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    fun clearRunningGameRoot(context: Context) {
        try {
            File(context.filesDir, RUNNING_GAME_FILE).delete()
        } catch (_: Throwable) {
        }
        try {
            File(context.filesDir, RUNNING_LAUNCH_TARGET_FILE).delete()
        } catch (_: Throwable) {
        }
    }

    fun getRunningLaunchTarget(context: Context): String? {
        if (!isGameProcessRunning(context)) {
            clearRunningGameRoot(context)
            return null
        }
        return try {
            val f = File(context.filesDir, RUNNING_LAUNCH_TARGET_FILE)
            if (f.isFile) f.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun isGameProcessRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val targetProcName = "${context.packageName}:game"
        return try {
            am.runningAppProcesses?.any { it.processName == targetProcName } == true
        } catch (_: Throwable) {
            false
        }
    }

    fun showGameStopFailedToast(context: Context) {
        val msg = GamepadBridge.tr(
            context,
            "无法终止正在运行的游戏进程，请稍后重试",
            "実行中のゲームを終了できませんでした。しばらくしてから再試行してください",
            "Failed to stop running game process. Please try again."
        )
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun waitForGameProcessExit(
        activity: Activity,
        maxRetries: Int = 30,
        intervalMs: Long = 50L,
        onExited: () -> Unit,
        onTimeout: () -> Unit = { showGameStopFailedToast(activity) }
    ) {
        val handler = Handler(Looper.getMainLooper())
        var attempts = 0
        val checkRunnable = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                if (!isGameProcessRunning(activity)) {
                    clearRunningGameRoot(activity)
                    onExited()
                    return
                }
                attempts++
                if (attempts >= maxRetries) {
                    onTimeout()
                } else {
                    handler.postDelayed(this, intervalMs)
                }
            }
        }
        if (!isGameProcessRunning(activity)) {
            clearRunningGameRoot(activity)
            onExited()
        } else {
            handler.postDelayed(checkRunnable, intervalMs)
        }
    }

    fun getRunningGameRoot(context: Context): String? {
        if (!isGameProcessRunning(context)) {
            clearRunningGameRoot(context)
            return null
        }
        return try {
            val f = File(context.filesDir, RUNNING_GAME_FILE)
            if (f.isFile) f.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun launchGameItem(activity: Activity, item: Item) {
        if (item.error != null) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.error)
                .setMessage(item.error)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        if (item.savedir == null) return
        val selectedBootEntry = System4BootEntry.getSelectedEntry(activity, item.path)
        val launchTarget = System4BootEntry.resolveLaunchTarget(item.path, selectedBootEntry)
        val effectiveBootEntry = selectedBootEntry.takeIf { it.isNotBlank() && it in System4BootEntry.listEntries(item.path) }.orEmpty()
        val saveDir = Item.resolveSaveDirForTarget(activity, item.path, launchTarget).also { it.mkdirs() }
        val targetRoot = item.path.path
        val running = isGameProcessRunning(activity)
        val runningRoot = if (running) getRunningGameRoot(activity) else null
        val runningTarget = if (running) getRunningLaunchTarget(activity) else null
        val targetMismatch = runningRoot != null && (
            runningRoot != targetRoot ||
                (!isSystem3xGameDir(item.path) && runningTarget != null && runningTarget != launchTarget.path)
            )

        if (running && targetMismatch) {
            val cleanName = cleanItemTitle(item.name)
            AlertDialog.Builder(activity)
                .setTitle(R.string.switch_running_game_title)
                .setMessage(activity.getString(R.string.switch_running_game_msg, cleanName))
                .setPositiveButton(R.string.switch_running_game_confirm) { _, _ ->
                    killRunningGameProcess(activity)
                    waitForGameProcessExit(
                        activity = activity,
                        onExited = {
                            startXSystem4Activity(activity, targetRoot, saveDir.path, effectiveBootEntry)
                        }
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        if (running && runningRoot == null) {
            // Previous :game process is in final teardown; wait until it fully exits before launching.
            waitForGameProcessExit(
                activity = activity,
                onExited = {
                    startXSystem4Activity(activity, targetRoot, saveDir.path, effectiveBootEntry)
                }
            )
            return
        }

        startXSystem4Activity(activity, targetRoot, saveDir.path, effectiveBootEntry)
    }

    fun killRunningGameProcess(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val targetProcName = "${context.packageName}:game"
        try {
            am?.runningAppProcesses?.firstOrNull { it.processName == targetProcName }?.let { proc ->
                Process.killProcess(proc.pid)
            }
        } catch (_: Throwable) {
        }
        clearRunningGameRoot(context)
    }

    /** Shows the standard local-game import chooser for an archive/image or a directory. */
    fun showLocalGameImportDialog(
        activity: Activity,
        titleRes: Int,
        archiveReqCode: Int,
        dirReqCode: Int
    ) {
        val options = arrayOf(
            activity.getString(R.string.game_opt_zip),
            activity.getString(R.string.game_opt_dir)
        )
        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        activity.startActivityForResult(
                            Intent.createChooser(i, activity.getString(R.string.choose_a_file)),
                            archiveReqCode
                        )
                    }
                    1 -> {
                        activity.startActivityForResult(
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            dirReqCode
                        )
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startXSystem4Activity(
        context: Context,
        gameRoot: String,
        saveDir: String,
        bootEntry: String = ""
    ) {
        val i = Intent().apply {
            setClass(context.applicationContext, XSystem4Activity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(XSystem4Activity.EXTRA_GAME_ROOT, gameRoot)
            putExtra(XSystem4Activity.EXTRA_SAVE_DIR, saveDir)
            if (bootEntry.isNotBlank()) {
                putExtra(XSystem4Activity.EXTRA_BOOT_ENTRY, bootEntry)
            }
        }
        context.startActivity(i)
    }
}
