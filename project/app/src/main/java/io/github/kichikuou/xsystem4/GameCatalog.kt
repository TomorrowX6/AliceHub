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

data class GameDescriptor(
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
    val overlayCatalogRelPath: String = "files/mods/index.json"
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
    const val ID_RANCE08 = "rance08"
    const val ID_RANCE09 = "rance09"
    const val ID_KICHIKUOU = "kichikuou"
    const val ID_ESCALAYER = "escalayer"
    const val ID_ESCALAYER_REBOOT = "escalayer_reboot"
    const val PREFIX_CUSTOM = "custom:"
    const val GITHUB_URL = "https://github.com/TomorrowX6/AliceHub"

    fun isSystem3xGameFiles(fileNames: Collection<String>): Boolean {
        val hasSys4Ain = fileNames.any { it.endsWith(".ain", true) && !it.equals("System39.ain", true) }
        val hasSys4Ini = fileNames.any { it.equals("System40.ini", true) || it.equals("AliceStart.ini", true) }
        if (hasSys4Ain || hasSys4Ini) return false
        return fileNames.any {
            it.endsWith("sa.ald", true) ||
                it.equals("adisk.ald", true) ||
                it.equals("System39.ini", true) ||
                it.equals("System39.ain", true)
        }
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

    /**
     * Classifies a game root strictly from its files (never directory/display names), returning
     * a built-in game ID if recognized or null for custom/unrecognized roots.
     */
    @JvmOverloads
    fun classifyGameRootFiles(
        fileNames: Collection<String>,
        bgmFileNames: Collection<String> = emptyList(),
        readSmallFile: ((String) -> ByteArray?)? = null
    ): String? {
        val ainNames = fileNames.filter { it.endsWith(".ain", ignoreCase = true) }
        if (ainNames.any { it.equals("Rance10.ain", ignoreCase = true) }) {
            return ID_RANCE10
        }
        if (ainNames.any { it.equals("dohnadohna.ain", ignoreCase = true) }) {
            return ID_DOHNA_DOHNA
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
        if (ainNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.equals("SengokuRance.ain", ignoreCase = true) ||
                    it.equals("AliceCN.ain", ignoreCase = true) ||
                    it.contains("愴崙儔儞僗") ||
                    it.contains("戦国ランス") ||
                    repaired.contains("戦国ランス")
            } || fileNames.any {
                val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it)
                it.endsWith("GA.ald", ignoreCase = true) &&
                    (it.contains("愴崙") || it.contains("戦国") || repaired.contains("戦国"))
            }
        ) {
            return ID_SENGOKU_RANCE
        }
        if (isEscalayerGameFiles(fileNames, readSmallFile)) {
            return ID_ESCALAYER
        }
        if (isKichikuouGameFiles(fileNames, bgmFileNames, readSmallFile)) {
            return ID_KICHIKUOU
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
        return classifyGameRootFiles(fileNames, bgmFileNames) { name ->
            val f = files.firstOrNull { it.name.equals(name, true) }
            if (f != null && f.length() <= 256L * 1024L) {
                try { f.readBytes() } catch (_: Throwable) { null }
            } else {
                null
            }
        }
    }

    @JvmOverloads
    fun matchesTargetGameFiles(
        targetGameId: String?,
        fileNames: Collection<String>,
        bgmFileNames: Collection<String> = emptyList(),
        readSmallFile: ((String) -> ByteArray?)? = null
    ): Boolean {
        if (targetGameId.isNullOrBlank() || targetGameId.startsWith(PREFIX_CUSTOM)) {
            return true
        }
        return classifyGameRootFiles(fileNames, bgmFileNames, readSmallFile) == targetGameId
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
                it.isFile && it.name.equals(expectedAinName, ignoreCase = true)
            } == true
            if (!hasExpected) return false
        }
        return matchesTargetGame(targetGameId, gameDir)
    }

    private const val RUNNING_GAME_FILE = ".running_game_root"
    private val RANCE01_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?1(?![0-9０-９])")
    private val RANCE02_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?2(?![0-9０-９])")
    private val RANCE03_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?3(?![0-9０-９])")
    private val RANCE08_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?8(?![0-9０-９])")
    private val RANCE09_CN_ALIAS_REGEX = Regex("(?<![0-9０-９])兰斯0?9(?![0-9０-９])")
    private val ESCALAYER_R_JA_ALIAS_REGEX = Regex("エスカレイヤー[\\s・_-]*r(?![a-z0-9])")
    private val ESCALAYER_R_CN_ALIAS_REGEX = Regex("艾斯卡蕾雅[\\s・_-]*r(?![a-z0-9])")
    private val ESCALAYER_R_EN_ALIAS_REGEX = Regex("(?<![a-z0-9])escalayer[\\s._-]*r(?![a-z0-9])")

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

    fun isEvenicle2Alias(lowerText: String): Boolean {
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

    /** Strips any trailing overlay badge tag ([MOD ...] / [汉化 ...]) from a game's display name. */
    fun cleanItemTitle(rawName: String): String {
        val cut = listOf(" [MOD", " [汉化")
            .map { rawName.indexOf(it) }
            .filter { it > 0 }
            .minOrNull()
        return if (cut != null) rawName.substring(0, cut) else rawName
    }

    fun classifyItem(item: Item): String {
        return classifyDirectory(
            gameDir = item.path,
            dirName = item.path.name,
            saveDir = item.savedir,
            displayName = item.name
        )
    }

    fun classifyDirectory(
        gameDir: File?,
        dirName: String,
        saveDir: File? = null,
        displayName: String? = null
    ): String {
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null) {
                classifyGameRootDir(gameDir)?.let { return it }
                val ainFiles = files.filter { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }
                if (ainFiles.any { !it.name.equals("System39.ain", ignoreCase = true) }) {
                    return "$PREFIX_CUSTOM$dirName"
                }
                if (isSystem3xGameDir(gameDir)) {
                    return "$PREFIX_CUSTOM$dirName"
                }
            }
        }

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
            val saveFiles = saveDir.listFiles()?.filter { it.isFile }.orEmpty()
            val hasGlobalSave = saveFiles.any {
                it.name.equals("GlobalSave.asd", ignoreCase = true) ||
                    it.name.equals("GlobalSaveData.asd", ignoreCase = true)
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
        if (lowerDir == "sengokurance" || lowerDir.startsWith("sengokurance-") || lowerDir.contains("sengoku") ||
            lowerDir.contains("战国兰斯") || lowerDir.contains("戦国ランス") || lowerDir.contains("愴崙儔儞僗")
        ) {
            return ID_SENGOKU_RANCE
        }
        if (isEvenicle2Alias(lowerDir)) {
            return ID_EVENICLE2
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
        if (lowerDir == "escalayer" || lowerDir.startsWith("escalayer-") || lowerDir.contains("escalayer") ||
            lowerDir.contains("超昂天使") || lowerDir.contains("エスカレイヤー")
        ) {
            return ID_ESCALAYER
        }
        if (lowerDir == "kichikurance" || lowerDir.startsWith("kichikurance-") ||
            lowerDir == "kichikuou" || lowerDir.startsWith("kichikuou-") ||
            lowerDir.contains("kichikuou") || lowerDir.contains("kichiku") ||
            lowerDir.contains("鬼畜王")
        ) {
            return ID_KICHIKUOU
        }

        val lowerDisp = displayName?.let { ArchiveExtractor.repairSjisAsGbkMojibake(it) }?.lowercase() ?: ""
        if (isEscalayerRebootAlias(lowerDisp)) {
            return ID_ESCALAYER_REBOOT
        }
        if (lowerDisp.contains("escalayer") || lowerDisp.contains("超昂天使") ||
            lowerDisp.contains("エスカレイヤー") || lowerDisp.contains("艾斯卡蕾雅")
        ) {
            return ID_ESCALAYER
        }
        if (lowerDisp.contains("kichikuou") || lowerDisp.contains("kichikurance") || lowerDisp.contains("鬼畜王")) {
            return ID_KICHIKUOU
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
        if (lowerDisp.contains("sengoku") || lowerDisp.contains("战国兰斯") || lowerDisp.contains("戰國蘭斯") ||
            lowerDisp.contains("戦国ランス")
        ) {
            return ID_SENGOKU_RANCE
        }
        if (isEvenicle2Alias(lowerDisp)) {
            return ID_EVENICLE2
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

    fun descriptorFor(context: Context, gameId: String, gameList: GameList? = null): GameDescriptor {
        return when (gameId) {
            ID_RANCE10 -> GameDescriptor(
                id = ID_RANCE10,
                title = context.getString(R.string.game_rance10_title),
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
                isRance10 = true
            )
            ID_DOHNA_DOHNA -> GameDescriptor(
                id = ID_DOHNA_DOHNA,
                title = context.getString(R.string.game_dohnadohna_title),
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
                isRance10 = false
            )
            ID_EVENICLE -> GameDescriptor(
                id = ID_EVENICLE,
                title = context.getString(R.string.game_evenicle_title),
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
                overlayCatalogRelPath = "files/cn-patch/index.json"
            )
            ID_EVENICLE2 -> GameDescriptor(
                id = ID_EVENICLE2,
                title = context.getString(R.string.game_evenicle2_title),
                defaultDirName = "Evenicle2",
                expectedAinName = "Evenicle2.ain",
                iconRes = R.drawable.ic_game_evenicle2,
                accentColor = 0xFF00838F.toInt(),
                accentBorderColor = 0x6626C6DA,
                iconFrameColor = 0xFF0F2026.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.EVENICLE2_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_SENGOKU_RANCE -> GameDescriptor(
                id = ID_SENGOKU_RANCE,
                title = context.getString(R.string.game_sengokurance_title),
                defaultDirName = "SengokuRance",
                expectedAinName = null,
                iconRes = R.drawable.ic_game_sengokurance,
                accentColor = 0xFFD84315.toInt(),
                accentBorderColor = 0x66FF7043,
                iconFrameColor = 0xFF1F1410.toInt(),
                supportsOnlineBaseInstall = true,
                onlineBaseCdnUrl = OnlineInstaller.SENGOKU_RANCE_CDN_URL,
                supportsOnlineMod = false,
                supportsLocalMod = false,
                isRance10 = false
            )
            ID_RANCE01 -> GameDescriptor(
                id = ID_RANCE01,
                title = context.getString(R.string.game_rance01_title),
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
                isRance10 = false
            )
            ID_RANCE02 -> GameDescriptor(
                id = ID_RANCE02,
                title = context.getString(R.string.game_rance02_title),
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
                isRance10 = false
            )
            ID_RANCE03 -> GameDescriptor(
                id = ID_RANCE03,
                title = context.getString(R.string.game_rance03_title),
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
                isRance10 = false
            )
            ID_RANCE08 -> GameDescriptor(
                id = ID_RANCE08,
                title = context.getString(R.string.game_rance08_title),
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
                isRance10 = false
            )
            ID_RANCE09 -> GameDescriptor(
                id = ID_RANCE09,
                title = context.getString(R.string.game_rance09_title),
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
                isRance10 = false
            )
            ID_ESCALAYER -> GameDescriptor(
                id = ID_ESCALAYER,
                title = context.getString(R.string.game_escalayer_title),
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
                title = context.getString(R.string.game_escalayer_reboot_title),
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
                isRance10 = false
            )
            ID_KICHIKUOU -> GameDescriptor(
                id = ID_KICHIKUOU,
                title = context.getString(R.string.game_kichikuou_title),
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

    fun buildSelectEntries(context: Context, gameList: GameList): List<GameSelectEntry> {
        val entries = ArrayList<GameSelectEntry>()

        val r10Desc = descriptorFor(context, ID_RANCE10, gameList)
        val r10Items = matchingItems(gameList, ID_RANCE10)
        entries.add(GameSelectEntry(r10Desc, r10Items, r10Items.firstOrNull()))

        val ddDesc = descriptorFor(context, ID_DOHNA_DOHNA, gameList)
        val ddItems = matchingItems(gameList, ID_DOHNA_DOHNA)
        entries.add(GameSelectEntry(ddDesc, ddItems, ddItems.firstOrNull()))

        val evDesc = descriptorFor(context, ID_EVENICLE, gameList)
        val evItems = matchingItems(gameList, ID_EVENICLE)
        entries.add(GameSelectEntry(evDesc, evItems, evItems.firstOrNull()))

        val ev2Desc = descriptorFor(context, ID_EVENICLE2, gameList)
        val ev2Items = matchingItems(gameList, ID_EVENICLE2)
        entries.add(GameSelectEntry(ev2Desc, ev2Items, ev2Items.firstOrNull()))

        val srDesc = descriptorFor(context, ID_SENGOKU_RANCE, gameList)
        val srItems = matchingItems(gameList, ID_SENGOKU_RANCE)
        entries.add(GameSelectEntry(srDesc, srItems, srItems.firstOrNull()))

        val r01Desc = descriptorFor(context, ID_RANCE01, gameList)
        val r01Items = matchingItems(gameList, ID_RANCE01)
        entries.add(GameSelectEntry(r01Desc, r01Items, r01Items.firstOrNull()))

        val r02Desc = descriptorFor(context, ID_RANCE02, gameList)
        val r02Items = matchingItems(gameList, ID_RANCE02)
        entries.add(GameSelectEntry(r02Desc, r02Items, r02Items.firstOrNull()))

        val r03Desc = descriptorFor(context, ID_RANCE03, gameList)
        val r03Items = matchingItems(gameList, ID_RANCE03)
        entries.add(GameSelectEntry(r03Desc, r03Items, r03Items.firstOrNull()))

        val r08Desc = descriptorFor(context, ID_RANCE08, gameList)
        val r08Items = matchingItems(gameList, ID_RANCE08)
        entries.add(GameSelectEntry(r08Desc, r08Items, r08Items.firstOrNull()))

        val r09Desc = descriptorFor(context, ID_RANCE09, gameList)
        val r09Items = matchingItems(gameList, ID_RANCE09)
        entries.add(GameSelectEntry(r09Desc, r09Items, r09Items.firstOrNull()))

        val kichikuDesc = descriptorFor(context, ID_KICHIKUOU, gameList)
        val kichikuItems = matchingItems(gameList, ID_KICHIKUOU)
        entries.add(GameSelectEntry(kichikuDesc, kichikuItems, kichikuItems.firstOrNull()))

        val escDesc = descriptorFor(context, ID_ESCALAYER, gameList)
        val escItems = matchingItems(gameList, ID_ESCALAYER)
        entries.add(GameSelectEntry(escDesc, escItems, escItems.firstOrNull()))

        val escRebootDesc = descriptorFor(context, ID_ESCALAYER_REBOOT, gameList)
        val escRebootItems = matchingItems(gameList, ID_ESCALAYER_REBOOT)
        entries.add(GameSelectEntry(escRebootDesc, escRebootItems, escRebootItems.firstOrNull()))

        for (i in 0 until gameList.size) {
            val item = gameList[i]
            val cid = classifyItem(item)
            if (cid != ID_RANCE10 && cid != ID_DOHNA_DOHNA && cid != ID_EVENICLE &&
                cid != ID_EVENICLE2 && cid != ID_SENGOKU_RANCE && cid != ID_RANCE01 && cid != ID_RANCE02 &&
                cid != ID_RANCE03 && cid != ID_RANCE08 && cid != ID_RANCE09 &&
                cid != ID_KICHIKUOU && cid != ID_ESCALAYER && cid != ID_ESCALAYER_REBOOT
            ) {
                val desc = descriptorFor(context, cid, gameList)
                entries.add(GameSelectEntry(desc, listOf(item), item))
            }
        }
        return entries
    }

    fun writeRunningGameRoot(context: Context, gameRoot: String) {
        try {
            File(context.filesDir, RUNNING_GAME_FILE).writeText(gameRoot, Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    fun clearRunningGameRoot(context: Context) {
        try {
            File(context.filesDir, RUNNING_GAME_FILE).delete()
        } catch (_: Throwable) {
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
        val saveDir = item.savedir ?: return
        val targetRoot = item.path.path
        val running = isGameProcessRunning(activity)
        val runningRoot = if (running) getRunningGameRoot(activity) else null

        if (running && runningRoot != null && runningRoot != targetRoot) {
            val cleanName = cleanItemTitle(item.name)
            AlertDialog.Builder(activity)
                .setTitle(R.string.switch_running_game_title)
                .setMessage(activity.getString(R.string.switch_running_game_msg, cleanName))
                .setPositiveButton(R.string.switch_running_game_confirm) { _, _ ->
                    killRunningGameProcess(activity)
                    waitForGameProcessExit(
                        activity = activity,
                        onExited = {
                            startXSystem4Activity(activity, targetRoot, saveDir.path)
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
                    startXSystem4Activity(activity, targetRoot, saveDir.path)
                }
            )
            return
        }

        startXSystem4Activity(activity, targetRoot, saveDir.path)
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

    private fun startXSystem4Activity(context: Context, gameRoot: String, saveDir: String) {
        val i = Intent().apply {
            setClass(context.applicationContext, XSystem4Activity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(XSystem4Activity.EXTRA_GAME_ROOT, gameRoot)
            putExtra(XSystem4Activity.EXTRA_SAVE_DIR, saveDir)
        }
        context.startActivity(i)
    }
}
