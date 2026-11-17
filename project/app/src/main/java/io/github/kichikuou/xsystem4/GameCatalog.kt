package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
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
    const val ID_SENGOKU_RANCE = "sengokurance"
    const val ID_RANCE03 = "rance03"
    const val ID_RANCE08 = "rance08"
    const val ID_RANCE09 = "rance09"
    const val ID_ESCALAYER = "escalayer"
    const val PREFIX_CUSTOM = "custom:"
    const val GITHUB_URL = "https://github.com/TomorrowX6/AliceHub"

    fun isSystem3xGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        val hasSys4Ain = files.any { it.name.endsWith(".ain", true) && !it.name.equals("System39.ain", true) }
        val hasSys4Ini = files.any { it.name.equals("System40.ini", true) || it.name.equals("AliceStart.ini", true) }
        if (hasSys4Ain || hasSys4Ini) return false
        return files.any {
            it.name.endsWith("sa.ald", true) ||
                it.name.equals("adisk.ald", true) ||
                it.name.equals("System39.ini", true) ||
                it.name.equals("System39.ain", true)
        }
    }

    fun isEscalayerGameDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        if (files.any { it.name.equals("DOKI_SA.ALD", true) }) return true
        val sys39Ini = files.firstOrNull { it.name.equals("System39.ini", true) }
        if (sys39Ini != null) {
            val title = System40Ini.parse(sys39Ini).gameName?.lowercase() ?: ""
            if (title.contains("エスカレイヤー") || title.contains("escalayer") || title.contains("超昂天使")) {
                return true
            }
        }
        return false
    }

    private const val RUNNING_GAME_FILE = ".running_game_root"

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
                val ainFiles = files.filter { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }
                if (ainFiles.any { it.name.equals("Rance10.ain", ignoreCase = true) }) {
                    return ID_RANCE10
                }
                if (ainFiles.any { it.name.equals("dohnadohna.ain", ignoreCase = true) }) {
                    return ID_DOHNA_DOHNA
                }
                if (ainFiles.any { it.name.equals("Evenicle.ain", ignoreCase = true) }) {
                    return ID_EVENICLE
                }
                if (ainFiles.any { it.name.equals("Rance03.ain", ignoreCase = true) }) {
                    return ID_RANCE03
                }
                if (ainFiles.any {
                        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it.name)
                        it.name.equals("Rance09.ain", ignoreCase = true) ||
                            it.name.equals("Rance9.ain", ignoreCase = true) ||
                            repaired.equals("Rance9.ain", ignoreCase = true)
                    }
                ) {
                    return ID_RANCE09
                }
                if (ainFiles.any {
                        val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it.name)
                        it.name.equals("Rance08.ain", ignoreCase = true) ||
                            it.name.equals("Rance8.ain", ignoreCase = true) ||
                            it.name.equals("RanceQuest.ain", ignoreCase = true) ||
                            it.name.contains("ランス・クエスト") ||
                            it.name.contains("儔儞僗丒僋僄僗僩") ||
                            repaired.contains("ランス・クエスト")
                    }
                ) {
                    return ID_RANCE08
                }
                if (ainFiles.any {
                        it.name.equals("SengokuRance.ain", ignoreCase = true) ||
                            it.name.equals("AliceCN.ain", ignoreCase = true) ||
                            it.name.contains("愴崙儔儞僗") ||
                            it.name.contains("戦国ランス")
                    } || files.any {
                        it.isFile && it.name.endsWith("GA.ald", ignoreCase = true) &&
                            (it.name.contains("愴崙") || it.name.contains("戦国"))
                    }
                ) {
                    return ID_SENGOKU_RANCE
                }
                if (isEscalayerGameDir(gameDir)) {
                    return ID_ESCALAYER
                }
                if (ainFiles.any { !it.name.equals("System39.ain", ignoreCase = true) }) {
                    return "$PREFIX_CUSTOM$dirName"
                }
                if (isSystem3xGameDir(gameDir)) {
                    return "$PREFIX_CUSTOM$dirName"
                }
            }
        }

        if (saveDir != null && saveDir.isDirectory) {
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
            if ((File(saveDir, "AFBGMMode.asd").isFile ||
                    File(saveDir, "AFCommon.asd").isFile ||
                    saveDir.listFiles()?.any {
                        it.isFile && it.name.startsWith("AFSaveData", ignoreCase = true) && it.name.endsWith(".asd", ignoreCase = true)
                    } == true) &&
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
                !File(saveDir, "嫟桳.asd").isFile
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
        }

        val lowerDir = ArchiveExtractor.repairSjisAsGbkMojibake(dirName).lowercase()
        if (lowerDir == "rance09" || lowerDir.startsWith("rance09-") ||
            lowerDir == "rance9" || lowerDir.startsWith("rance9-") ||
            lowerDir.contains("rance 09") || lowerDir.contains("rance 9") || lowerDir.contains("rance ix") ||
            lowerDir.contains("兰斯09") || lowerDir.contains("兰斯9") || lowerDir.contains("ランス09") ||
            lowerDir.contains("ランス9") || lowerDir.contains("ヘルマン") || lowerDir.contains("赫尔曼")
        ) {
            return ID_RANCE09
        }
        if (lowerDir == "rance08" || lowerDir.startsWith("rance08-") ||
            lowerDir == "rance8" || lowerDir.startsWith("rance8-") ||
            lowerDir.contains("rance 08") || lowerDir.contains("rancequest") || lowerDir.contains("rance quest") ||
            lowerDir.contains("兰斯08") || lowerDir.contains("兰斯8") || lowerDir.contains("ランス08") ||
            lowerDir.contains("ランス8") || lowerDir.contains("ランス・クエスト")
        ) {
            return ID_RANCE08
        }
        if (lowerDir == "rance03" || lowerDir.startsWith("rance03-") || lowerDir.contains("rance 03") ||
            lowerDir.contains("兰斯03") || lowerDir.contains("兰斯3") || lowerDir.contains("ランス03")
        ) {
            return ID_RANCE03
        }
        if (lowerDir == "sengokurance" || lowerDir.startsWith("sengokurance-") || lowerDir.contains("sengoku") ||
            lowerDir.contains("战国兰斯") || lowerDir.contains("戦国ランス") || lowerDir.contains("愴崙儔儞僗")
        ) {
            return ID_SENGOKU_RANCE
        }
        if (lowerDir == "evenicle" || lowerDir.startsWith("evenicle-") || lowerDir.contains("イブニクル") || lowerDir.contains("夏娃")) {
            return ID_EVENICLE
        }
        if (lowerDir == "dohnadohna" || lowerDir.startsWith("dohnadohna-") || lowerDir.contains("多娜多娜")) {
            return ID_DOHNA_DOHNA
        }
        if (lowerDir == "rance10" || lowerDir.startsWith("rance10-") || lowerDir.contains("兰斯10")) {
            return ID_RANCE10
        }
        if (lowerDir == "escalayer" || lowerDir.startsWith("escalayer-") || lowerDir.contains("escalayer") ||
            lowerDir.contains("超昂天使") || lowerDir.contains("エスカレイヤー")
        ) {
            return ID_ESCALAYER
        }

        val lowerDisp = displayName?.let { ArchiveExtractor.repairSjisAsGbkMojibake(it) }?.lowercase() ?: ""
        if (lowerDisp.contains("escalayer") || lowerDisp.contains("超昂天使") ||
            lowerDisp.contains("エスカレイヤー") || lowerDisp.contains("艾斯卡蕾雅")
        ) {
            return ID_ESCALAYER
        }
        if (lowerDisp.contains("rance09") || lowerDisp.contains("rance 09") ||
            lowerDisp.contains("rance9") || lowerDisp.contains("rance 9") || lowerDisp.contains("rance ix") ||
            lowerDisp.contains("兰斯09") || lowerDisp.contains("兰斯9") || lowerDisp.contains("ランス09") ||
            lowerDisp.contains("ランス9") || lowerDisp.contains("ヘルマン") || lowerDisp.contains("赫尔曼")
        ) {
            return ID_RANCE09
        }
        if (lowerDisp.contains("rance08") || lowerDisp.contains("rance 08") ||
            lowerDisp.contains("rancequest") || lowerDisp.contains("rance quest") ||
            lowerDisp.contains("兰斯08") || lowerDisp.contains("兰斯8") || lowerDisp.contains("ランス08") ||
            lowerDisp.contains("ランス8") || lowerDisp.contains("ランス・クエスト") ||
            lowerDisp.contains("ランスクエスト") || lowerDisp.contains("マグナム") || lowerDisp.contains("magnum")
        ) {
            return ID_RANCE08
        }
        if (lowerDisp.contains("rance03") || lowerDisp.contains("rance 03") ||
            lowerDisp.contains("兰斯03") || lowerDisp.contains("兰斯3") || lowerDisp.contains("ランス03") ||
            lowerDisp.contains("リーザス") || lowerDisp.contains("利萨斯")
        ) {
            return ID_RANCE03
        }
        if (lowerDisp.contains("sengoku") || lowerDisp.contains("战国兰斯") || lowerDisp.contains("戰國蘭斯") ||
            lowerDisp.contains("戦国ランス")
        ) {
            return ID_SENGOKU_RANCE
        }
        if (lowerDisp.contains("イブニクル") || lowerDisp.contains("evenicle") || lowerDisp.contains("夏娃")) {
            return ID_EVENICLE
        }
        if (lowerDisp.contains("多娜多娜") || lowerDisp.contains("ドーナドーナ") || lowerDisp.contains("dohna")) {
            return ID_DOHNA_DOHNA
        }
        if (lowerDisp.contains("兰斯10") || lowerDisp.contains("兰斯１０") || lowerDisp.contains("ランス10") ||
            lowerDisp.contains("rance10") || lowerDisp.contains("rance 10")
        ) {
            return ID_RANCE10
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
                supportsOnlineBaseInstall = false,
                onlineBaseCdnUrl = null,
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

        val srDesc = descriptorFor(context, ID_SENGOKU_RANCE, gameList)
        val srItems = matchingItems(gameList, ID_SENGOKU_RANCE)
        entries.add(GameSelectEntry(srDesc, srItems, srItems.firstOrNull()))

        val r03Desc = descriptorFor(context, ID_RANCE03, gameList)
        val r03Items = matchingItems(gameList, ID_RANCE03)
        entries.add(GameSelectEntry(r03Desc, r03Items, r03Items.firstOrNull()))

        val r08Desc = descriptorFor(context, ID_RANCE08, gameList)
        val r08Items = matchingItems(gameList, ID_RANCE08)
        entries.add(GameSelectEntry(r08Desc, r08Items, r08Items.firstOrNull()))

        val r09Desc = descriptorFor(context, ID_RANCE09, gameList)
        val r09Items = matchingItems(gameList, ID_RANCE09)
        entries.add(GameSelectEntry(r09Desc, r09Items, r09Items.firstOrNull()))

        val escDesc = descriptorFor(context, ID_ESCALAYER, gameList)
        val escItems = matchingItems(gameList, ID_ESCALAYER)
        entries.add(GameSelectEntry(escDesc, escItems, escItems.firstOrNull()))

        for (i in 0 until gameList.size) {
            val item = gameList[i]
            val cid = classifyItem(item)
            if (cid != ID_RANCE10 && cid != ID_DOHNA_DOHNA && cid != ID_EVENICLE &&
                cid != ID_SENGOKU_RANCE && cid != ID_RANCE03 &&
                cid != ID_RANCE08 && cid != ID_RANCE09 && cid != ID_ESCALAYER
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
        val runningRoot = getRunningGameRoot(activity)

        if (isGameProcessRunning(activity) && runningRoot != null && runningRoot != targetRoot) {
            val cleanName = cleanItemTitle(item.name)
            AlertDialog.Builder(activity)
                .setTitle(R.string.switch_running_game_title)
                .setMessage(activity.getString(R.string.switch_running_game_msg, cleanName))
                .setPositiveButton(R.string.switch_running_game_confirm) { _, _ ->
                    killRunningGameProcess(activity)
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            startXSystem4Activity(activity, targetRoot, saveDir.path)
                        }
                    }, 160L)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        startXSystem4Activity(activity, targetRoot, saveDir.path)
    }

    private fun killRunningGameProcess(context: Context) {
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
