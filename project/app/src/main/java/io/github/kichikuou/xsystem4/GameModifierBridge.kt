package io.github.kichikuou.xsystem4

import android.content.Context
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

object GameModifierBridge {
    private const val TAG = "GameModifierBridge"
    private const val PREFS_NAME = "game_modifier_prefs"
    private const val CONFIG_FILE_NAME = "modifier_config.json"
    private val configLock = Any()
    private val configTmpSeq = AtomicLong(0L)
    private const val KEY_FLOATING_ENABLED = "floating_enabled"
    private const val KEY_FLOATING_DEFAULT_ON_V1 = "floating_default_on_v1"
    private const val KEY_CTRL_OVERLAY_ENABLED = "ctrl_overlay_enabled"
    private const val KEY_CTRL_DEFAULT_ON_V1 = "ctrl_default_on_v1"
    private const val KEY_CTRL_DEFAULT_OFF_V2 = "ctrl_default_off_v2"
    private const val KEY_CTRL_OVERLAY_VISIBLE = "ctrl_overlay_visible"
    private const val KEY_DISABLE_STREAMER_MODE = "disable_streamer_mode"
    private const val KEY_LOCK_MASK = "lock_mask"
    private const val KEY_DOHNA_LOCK_MASK = "dohna_lock_mask"
    private const val KEY_EVENICLE_LOCK_MASK = "evenicle_lock_mask"
    private const val KEY_SR_LOCK_MASK = "sengokurance_lock_mask"
    private const val KEY_R01_LOCK_MASK = "rance01_lock_mask"
    private const val KEY_R02_LOCK_MASK = "rance02_lock_mask"
    private const val KEY_R03_LOCK_MASK = "rance03_lock_mask"
    private const val KEY_R08_LOCK_MASK = "rance08_lock_mask"
    private const val KEY_R09_LOCK_MASK = "rance09_lock_mask"
    private const val KEY_ESC_LOCK_MASK = "escalayer_lock_mask"
    private const val KEY_EXTRA_LOCKS = "extra_locks"
    private const val KEY_FONT_SCALE_PCT = "font_scale_pct"
    private const val KEY_SCROLLBAR_SCALE_PCT = "scrollbar_scale_pct"
    private const val KEY_GAME_FONT_SCALE_PCT = "game_font_scale_pct"
    private const val KEY_GAME_SCROLLBAR_SCALE_PCT = "game_scrollbar_scale_pct"
    private const val KEY_CONFIG_SEQ = "config_seq"
    private const val UNLOCK_BAK_SUFFIX = ".unlock.bak"
    private const val MOD_BAK_SUFFIX = ".mod.bak"

    const val DEFAULT_FONT_SCALE_PCT = 100
    const val MIN_FONT_SCALE_PCT = 75
    const val MAX_FONT_SCALE_PCT = 160
    const val DEFAULT_SCROLLBAR_SCALE_PCT = 100
    const val MIN_SCROLLBAR_SCALE_PCT = 100
    const val MAX_SCROLLBAR_SCALE_PCT = 300

    const val LOCK_PARTY_MAX_HP = 1 shl 0
    const val LOCK_INFINITE_AP = 1 shl 1
    const val LOCK_BATTLE_COMBO = 1 shl 2
    const val LOCK_BATTLE_ROUND_1 = 1 shl 3
    const val LOCK_ONE_HIT_KILL = 1 shl 4
    const val LOCK_ENEMY_ZERO_ATK = 1 shl 5
    const val LOCK_LEADER_BOOST_CRIT = 1 shl 6
    const val LOCK_INFINITE_SKILL_USE = 1 shl 7
    const val LOCK_INFINITE_ZIPPO = 1 shl 8
    const val LOCK_MAX_RESOURCES = 1 shl 9
    const val LOCK_TREASURE_100 = 1 shl 10
    const val LOCK_DISABLE_STREAMER_MODE = 1 shl 11

    const val LOCK_DD_ALLY_MAX_HP = 1 shl 0
    const val LOCK_DD_ALLY_MAX_MP = 1 shl 1
    const val LOCK_DD_ALLY_MAX_VP = 1 shl 2
    const val LOCK_DD_ONE_HIT_KILL = 1 shl 3
    const val LOCK_DD_INFINITE_MONEY = 1 shl 4
    const val LOCK_DD_INFINITE_TP = 1 shl 5
    const val LOCK_DD_WORKER_BEST_COND = 1 shl 6
    const val LOCK_DD_WORK_FREEZE_TIMER = 1 shl 7
    const val LOCK_DD_ALL_MASK = 0xFF

    const val LOCK_EV_NO_ENCOUNT = 1 shl 0
    const val LOCK_EV_ALLY_FULL_HP = 1 shl 1
    const val LOCK_EV_INF_SKILL_P = 1 shl 2
    const val LOCK_EV_INF_BP = 1 shl 3
    const val LOCK_EV_ONE_HIT_KILL = 1 shl 4
    const val LOCK_EV_INFINITE_MONEY = 1 shl 5
    const val LOCK_EV_ALL_MASK = 0x3F

    const val LOCK_SR_INF_GOLD = 1 shl 0
    const val LOCK_SR_INF_AP = 1 shl 1
    const val LOCK_SR_MAX_SAT = 1 shl 2
    const val LOCK_SR_ALLY_FULL_HP = 1 shl 3
    const val LOCK_SR_ONE_HIT_KILL = 1 shl 4
    const val LOCK_SR_UNLIM_ACTION = 1 shl 5
    const val LOCK_SR_ALL_MASK = 0x3F

    const val LOCK_R01_FULL_HP = 1 shl 0
    const val LOCK_R01_ONE_HIT_KILL = 1 shl 1
    const val LOCK_R01_INF_GOLD = 1 shl 2
    const val LOCK_R01_DROP_100 = 1 shl 3
    const val LOCK_R01_NO_DEBUFF = 1 shl 4
    const val LOCK_R01_ALL_MASK = 0x1F

    const val LOCK_R02_ALLY_FULL_HP_SP = 1 shl 0
    const val LOCK_R02_ONE_HIT_KILL = 1 shl 1
    const val LOCK_R02_INF_GOLD = 1 shl 2
    const val LOCK_R02_INF_ITEMS = 1 shl 3
    const val LOCK_R02_ALL_MASK = 0x0F

    const val LOCK_R03_HP = 1 shl 0
    const val LOCK_R03_NO_CD = 1 shl 1
    const val LOCK_R03_INF_ITEMS = 1 shl 2
    const val LOCK_R03_DROP_100 = 1 shl 3
    const val LOCK_R03_ONE_HIT_KILL = 1 shl 4
    const val LOCK_R03_ALL_MASK = 0x1F

    const val LOCK_R08_ALLY_FULL_HP = 1 shl 0
    const val LOCK_R08_ONE_HIT_KILL = 1 shl 1
    const val LOCK_R08_INF_SKILLS = 1 shl 2
    const val LOCK_R08_MAGNUM_MAX = 1 shl 3
    const val LOCK_R08_INF_GOLD = 1 shl 4
    const val LOCK_R08_ALL_MASK = 0x1F

    const val LOCK_R09_ALLY_GOD = 1 shl 0
    const val LOCK_R09_ONE_HIT_KILL = 1 shl 1
    const val LOCK_R09_INF_ACTION = 1 shl 2
    const val LOCK_R09_DROP_100 = 1 shl 3
    const val LOCK_R09_INF_RESOURCES = 1 shl 4
    const val LOCK_R09_ALL_MASK = 0x1F
    const val LOCK_ESC_HP_FULL = 1 shl 0
    const val LOCK_ESC_LIBIDO_MAX = 1 shl 1
    const val LOCK_ESC_ONEHIT = 1 shl 2
    const val LOCK_ESC_LOVE_MAX = 1 shl 3
    const val LOCK_ESC_ALL_MASK = 0x0F

    @Volatile
    private var loaded = false

    @Volatile
    private var activeUiScaleGameKey: String? = null

    fun markAlreadyLoaded() {
        loaded = true
    }

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("SDL2")
            System.loadLibrary("xsystem4")
            loaded = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load native libraries for GameModifierBridge", t)
            false
        }
    }

    fun getConfigFilePath(context: Context): String {
        return File(context.filesDir, CONFIG_FILE_NAME).absolutePath
    }

    private fun hasRance10SaveMarkers(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val subSaveData = File(dir, "SaveData")
        if (File(dir, "GlobalSave.asd").isFile ||
            File(dir, "Collection.asd").isFile ||
            File(subSaveData, "GlobalSave.asd").isFile ||
            File(subSaveData, "Collection.asd").isFile
        ) {
            return true
        }
        if (File(dir, "GlobalSaveData.asd").isFile) {
            return false
        }
        return dir.listFiles()?.any {
            it.isFile &&
                it.name.startsWith("LocalSave", ignoreCase = true) &&
                it.name.endsWith(".asd", ignoreCase = true)
        } == true
    }

    private fun matchesRance10Name(rawName: String?): Boolean {
        if (rawName.isNullOrBlank()) return false
        val lower = ArchiveExtractor.repairSjisAsGbkMojibake(rawName).trim().lowercase(Locale.ROOT)
        return lower == "rance10" ||
            lower.startsWith("rance10-") ||
            lower.startsWith("rance10_") ||
            lower.contains("rance10") ||
            lower.contains("rance 10") ||
            lower == "rancex" ||
            lower.startsWith("rancex-") ||
            lower.startsWith("rancex_") ||
            lower.contains("rance x") ||
            lower.contains("ランス10") ||
            lower.contains("ランス１０") ||
            lower.contains("兰斯10") ||
            lower.contains("兰斯１０") ||
            lower.contains("儔儞僗10")
    }

    @JvmOverloads
    fun isRance10GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (GameCatalog.isSystem3xGameDir(gameDir) ||
            isEscalayerGameDir(gameDir, dirNameFallback, saveDir) ||
            isEvenicle2GameDir(gameDir, dirNameFallback, saveDir) ||
            isEscalayerRebootGameDir(gameDir, dirNameFallback, saveDir) ||
            isRance06GameDir(gameDir, dirNameFallback, saveDir) ||
            isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir)
        ) {
            return false
        }
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }) {
                return files.any {
                    if (!it.isFile) return@any false
                    val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it.name)
                    it.name.equals("Rance10.ain", ignoreCase = true) ||
                        repaired.equals("Rance10.ain", ignoreCase = true) ||
                        it.name.contains("ランス10") ||
                        repaired.contains("ランス10") ||
                        it.name.contains("兰斯10")
                }
            }
            if (hasRance10SaveMarkers(gameDir)) {
                return true
            }
        }
        if (saveDir != null && hasRance10SaveMarkers(saveDir)) {
            return true
        }
        if (matchesRance10Name(dirNameFallback) || matchesRance10Name(gameDir?.name)) {
            return true
        }
        return false
    }

    fun isDohnaDohnaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (isEvenicle2GameDir(gameDir, dirNameFallback, saveDir) ||
            isEscalayerRebootGameDir(gameDir, dirNameFallback, saveDir) ||
            isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir)
        ) {
            return false
        }
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }) {
                return files.any { it.isFile && it.name.equals("dohnadohna.ain", ignoreCase = true) }
            }
        }
        if (saveDir != null && saveDir.isDirectory) {
            if ((File(saveDir, "GlobalSaveData.asd").isFile ||
                    File(saveDir, "AFInfo.asd").isFile ||
                    File(saveDir, "SaveData5000.asd").isFile) &&
                !File(saveDir, "GlobalSave.asd").isFile
            ) {
                return true
            }
        }
        if (!dirNameFallback.isNullOrBlank()) {
            val lower = dirNameFallback.lowercase()
            if (lower == "dohnadohna" || lower.startsWith("dohnadohna-") || lower.contains("多娜多娜")) {
                return true
            }
        }
        return false
    }

    fun isRance03GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (isEvenicle2GameDir(gameDir, dirNameFallback, saveDir) ||
            isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir)
        ) {
            return false
        }
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }) {
                return files.any { it.isFile && it.name.equals("Rance03.ain", ignoreCase = true) }
            }
        }
        if (saveDir != null && saveDir.isDirectory) {
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
                return true
            }
        }
        if (!dirNameFallback.isNullOrBlank()) {
            val lower = dirNameFallback.lowercase()
            if (lower == "rance03" || lower.startsWith("rance03-") || lower.contains("rance 03") ||
                lower.contains("兰斯03") || lower.contains("兰斯3") || lower.contains("ランス03")
            ) {
                return true
            }
        }
        return false
    }

    fun isRance01GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_RANCE01

    fun isRance02GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_RANCE02

    @JvmOverloads
    fun isRance06GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_RANCE06

    @JvmOverloads
    fun isGalzooGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_GALZOO

    fun isSengokuRanceGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir)) {
            return false
        }
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }) {
                return files.any {
                    it.isFile && (
                        it.name.equals("SengokuRance.ain", ignoreCase = true) ||
                            it.name.equals("AliceCN.ain", ignoreCase = true) ||
                            it.name.contains("愴崙儔儞僗") ||
                            it.name.contains("戦国ランス")
                        )
                } || files.any {
                    it.isFile && it.name.endsWith("GA.ald", ignoreCase = true) && it.name.contains("愴崙")
                }
            }
        }
        if (saveDir != null && saveDir.isDirectory) {
            if ((File(saveDir, "key0104.dat").isFile ||
                    saveDir.listFiles()?.any { it.isFile && it.name.endsWith("_Sys.ASD", ignoreCase = true) } == true) &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return true
            }
        }
        if (!dirNameFallback.isNullOrBlank()) {
            val lower = dirNameFallback.lowercase()
            if (lower == "sengokurance" || lower.startsWith("sengokurance-") || lower.contains("sengoku") ||
                lower.contains("战国兰斯") || lower.contains("戦国ランス") || lower.contains("愴崙儔儞僗")
            ) {
                return true
            }
        }
        return false
    }

    fun isRance08GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir)) {
            return false
        }
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }) {
                return files.any {
                    if (!it.isFile) return@any false
                    val repaired = ArchiveExtractor.repairSjisAsGbkMojibake(it.name)
                    it.name.equals("Rance08.ain", ignoreCase = true) ||
                        it.name.equals("Rance8.ain", ignoreCase = true) ||
                        it.name.equals("RanceQuest.ain", ignoreCase = true) ||
                        it.name.contains("ランス・クエスト") ||
                        it.name.contains("儔儞僗丒僋僄僗僩") ||
                        repaired.contains("ランス・クエスト")
                }
            }
        }
        if (saveDir != null && saveDir.isDirectory) {
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
                return true
            }
        }
        if (!dirNameFallback.isNullOrBlank()) {
            val lower = ArchiveExtractor.repairSjisAsGbkMojibake(dirNameFallback).lowercase()
            if (lower == "rance08" || lower.startsWith("rance08-") ||
                lower == "rance8" || lower.startsWith("rance8-") ||
                lower.contains("rance 08") || lower.contains("rancequest") || lower.contains("rance quest") ||
                lower.contains("兰斯08") || lower.contains("兰斯8") || lower.contains("ランス08") ||
                lower.contains("ランス8") || lower.contains("ランス・クエスト")
            ) {
                return true
            }
        }
        return false
    }

    fun isRance09GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (isEvenicle2GameDir(gameDir, dirNameFallback, saveDir) ||
            isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir)
        ) {
            return false
        }
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }) {
                return files.any {
                    it.isFile && (
                        it.name.equals("Rance09.ain", ignoreCase = true) ||
                            it.name.equals("Rance9.ain", ignoreCase = true)
                        )
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
                return true
            }
        }
        if (!dirNameFallback.isNullOrBlank()) {
            val lower = ArchiveExtractor.repairSjisAsGbkMojibake(dirNameFallback).lowercase()
            if (lower == "rance09" || lower.startsWith("rance09-") ||
                lower == "rance9" || lower.startsWith("rance9-") ||
                lower.contains("rance 09") || lower.contains("rance 9") || lower.contains("rance ix") ||
                lower.contains("兰斯09") || lower.contains("兰斯9") || lower.contains("ランス09") ||
                lower.contains("ランス9") || lower.contains("ヘルマン") || lower.contains("赫尔曼")
            ) {
                return true
            }
        }
        return false
    }

    private const val MOD_ABSENT_SUFFIX = ".mod.absent"

    fun isIxsealGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_IXSEAL

    @JvmOverloads
    fun isHarukaGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_HARUKA

    @JvmOverloads
    fun isTsumashiboriGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TSUMASHIBORI

    @JvmOverloads
    fun isTsumamigui3GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TSUMAMIGUI3

    @JvmOverloads
    fun isDaibanchouGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_DAIBANCHOU

    @JvmOverloads
    fun isDaiteikokuGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_DAITEIKOKU

    @JvmOverloads
    fun isTsumamiguiGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TSUMAMIGUI

    @JvmOverloads
    fun isKaerunyoGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_KAERUNYO

    @JvmOverloads
    fun isAtlachnachaGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_ATLACHNACHA

    @JvmOverloads
    fun isReishikiGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_REISHIKI

    @JvmOverloads
    fun isHaruurareGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_HARUURARE

    @JvmOverloads
    fun isOkusamaJunGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_OKUSAMAJUN

    @JvmOverloads
    fun isOkusamaNtrGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_OKUSAMANTR

    @JvmOverloads
    fun isEvenicle2RanceGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_EVENICLE2RANCE

    @JvmOverloads
    fun isEvenicleGaidenGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_EVENICLEGAIDEN

    @JvmOverloads
    fun isWaidonyoGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_WAIDONYO

    @JvmOverloads
    fun isRance6AfterGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_RANCE6AFTER

    @JvmOverloads
    fun isYakata2GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_YAKATA2

    @JvmOverloads
    fun isSdpsMariaGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_SDPSMARIA

    @JvmOverloads
    fun isToushin3GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TOUSHIN3

    @JvmOverloads
    fun isOyakorankanGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_OYAKORANKAN

    @JvmOverloads
    fun isYorugakuruGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_YORUGAKURU

    @JvmOverloads
    fun isRance1DigestGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_RANCE1DIGEST

    @JvmOverloads
    fun isMamanGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_MAMAN

    @JvmOverloads
    fun isEvenicleRanceGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_EVENICLERANCE

    @JvmOverloads
    fun isHaharanmanGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_HAHARANMAN

    @JvmOverloads
    fun isTsumamigui2GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TSUMAMIGUI2

    @JvmOverloads
    fun isTsumamigui3JunGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TSUMAMIGUI3JUN

    @JvmOverloads
    fun isTsumamigui3NtrGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TSUMAMIGUI3NTR

    @JvmOverloads
    fun isDiaboliqueGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_DIABOLIQUE

    @JvmOverloads
    fun isGakuenKingGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_GAKUENKING

    @JvmOverloads
    fun isGakuenKingExGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_GAKUENKINGEX

    @JvmOverloads
    fun isOudouYuushaGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_OUDOUYUUSHA

    @JvmOverloads
    fun isToushin1GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TOUSHIN1

    @JvmOverloads
    fun isToushin2GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_TOUSHIN2

    @JvmOverloads
    fun isDarcrowsGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_DARCROWS

    @JvmOverloads
    fun isIkusaMikoGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_IKUSAMIKO

    @JvmOverloads
    fun isOnlyYouGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_ONLYYOU

    @JvmOverloads
    fun isSanbikiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SANBIKI

    @JvmOverloads
    fun isPersiomGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PERSIOM

    @JvmOverloads
    fun isSeeInAoGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SEEINAO

    @JvmOverloads
    fun isHushabyGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_HUSHABY

    @JvmOverloads
    fun isAyumiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_AYUMI

    @JvmOverloads
    fun isCielClaireGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_CIELCLAIRE

    @JvmOverloads
    fun isNightDemonGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_NIGHTDEMON

    @JvmOverloads
    fun isKatsumiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_KATSUMI

    @JvmOverloads
    fun isDrStopGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DRSTOP

    @JvmOverloads
    fun isDpsAllGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DPSALL

    @JvmOverloads
    fun isKoreDpsGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_KOREDPS

    @JvmOverloads
    fun isKaeruAliceGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_KAERUALICE

    @JvmOverloads
    fun isProstudentGGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PROSTUDENTG

    @JvmOverloads
    fun isProstudentGoodGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PROSTUDENTGOOD

    @JvmOverloads
    fun isPastelChimeGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PASTELCHIME

    @JvmOverloads
    fun isPastelChime2GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PASTELCHIME2

    @JvmOverloads
    fun isYakata3GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_YAKATA3

    @JvmOverloads
    fun isDalkGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DALK

    @JvmOverloads
    fun isNingenGariGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_NINGENGARI

    @JvmOverloads
    fun isPaschaCppGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PASCHACPP

    @JvmOverloads
    fun isPascha3GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PASCHA3

    @JvmOverloads
    fun isMamatotoGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MAMATOTO

    @JvmOverloads
    fun isOnlyYouReCrossGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_ONLYYOURECROSS

    @JvmOverloads
    fun isMamorigamiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MAMORIGAMI

    @JvmOverloads
    fun isAmbivalenzGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_AMBIVALENZ

    @JvmOverloads
    fun isFunnyBeeGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_FUNNYBEE

    @JvmOverloads
    fun isToushin2SpGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_TOUSHIN2SP

    @JvmOverloads
    fun isMugenHouyouGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MUGENHOUYOU

    @JvmOverloads
    fun isOtomeSenkiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_OTOMESENKI

    @JvmOverloads
    fun isGakuenHyouryuuGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_GAKUENHYOURYUU

    @JvmOverloads
    fun isMaidNoSusumeGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MAIDNOSUSUME

    @JvmOverloads
    fun isDalkGaidenGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DALKGAIDEN

    @JvmOverloads
    fun isMamanyonyoGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MAMANYONYO

    @JvmOverloads
    fun isMajoNoShokuzaiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MAJONOSHOKUZAI

    @JvmOverloads
    fun isSabotenGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SABOTEN

    @JvmOverloads
    fun isDoubleSenseiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DOUBLESENSEI

    @JvmOverloads
    fun isOjousamaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_OJOUSAMA

    @JvmOverloads
    fun isEdoranzeGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_EDORANZE

    @JvmOverloads
    fun isVanishGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_VANISH

    @JvmOverloads
    fun isHokenshitsuGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_HOKENSHITSU

    @JvmOverloads
    fun isKamunagaraGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_KAMUNAGARA

    @JvmOverloads
    fun isErumeiFlowerGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_ERUMEIFLOWER

    @JvmOverloads
    fun isErumeiWakareGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_ERUMEIWAKARE

    @JvmOverloads
    fun isManana1GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MANANA1

    @JvmOverloads
    fun isManana2GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MANANA2

    @JvmOverloads
    fun isManana3GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MANANA3

    @JvmOverloads
    fun isManana4GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MANANA4

    @JvmOverloads
    fun isManana5GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MANANA5

    @JvmOverloads
    fun isTsumaSakuraGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_TSUMASAKURA

    @JvmOverloads
    fun isShamansSanctuaryGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SHAMANSSANCTUARY

    @JvmOverloads
    fun isAliveZGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_ALIVEZ

    @JvmOverloads
    fun isDungeonsDollsGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DUNGEONSDOLLS

    @JvmOverloads
    fun isSuzumiyaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SUZUMIYA

    @JvmOverloads
    fun isShimaimaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SHIMAIMA

    @JvmOverloads
    fun isDaibanchouGaidenGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DAIBANCHOUGAIDEN

    @JvmOverloads
    fun isCosplayLemonGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_COSPLAYLEMON

    @JvmOverloads
    fun isWhipHoneyGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_WHIPHONEY

    @JvmOverloads
    fun isOnimusuGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_ONIMUSU

    @JvmOverloads
    fun isProGrxGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PROGRX

    @JvmOverloads
    fun isDpsVerNGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DPSVERN

    @JvmOverloads
    fun isSukiironoGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SUKIIRONO

    @JvmOverloads
    fun isGirlsGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_GIRLS

    @JvmOverloads
    fun isDebutGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DEBUT

    @JvmOverloads
    fun isHarukaVsEscaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_HARUKAVSESCA

    @JvmOverloads
    fun isHarukaNinjaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_HARUKANINJA

    @JvmOverloads
    fun isMeganeGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MEGANE

    @JvmOverloads
    fun isRachelSakuraGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_RACHELSAKURA

    @JvmOverloads
    fun isSabakuNikaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SABAKUNIKA

    @JvmOverloads
    fun isVanishPalmGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_VANISHPALM

    @JvmOverloads
    fun isBladeBridersGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_BLADEBRIDERS

    @JvmOverloads
    fun isDorapekoGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DORAPEKO

    @JvmOverloads
    fun isHaniwaPetitGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_HANIWAPETIT

    @JvmOverloads
    fun isYakata456GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_YAKATA456

    @JvmOverloads
    fun isUmiNiOchiteGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_UMINIOCHITE

    @JvmOverloads
    fun isGakuenHyouryuu2GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_GAKUENHYOURYUU2

    @JvmOverloads
    fun isYouseiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_YOUSEI

    @JvmOverloads
    fun isTobiraGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_TOBIRA

    @JvmOverloads
    fun isBuroburoGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_BUROBURO

    @JvmOverloads
    fun isKudakeroGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_KUDAKERO

    @JvmOverloads
    fun isLittleVampireGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_LITTLEVAMPIRE

    @JvmOverloads
    fun isIntruderGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_INTRUDER

    @JvmOverloads
    fun isCrescentGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_CRESCENT

    @JvmOverloads
    fun isTenguGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_TENGU

    @JvmOverloads
    fun isDpsGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DPS

    @JvmOverloads
    fun isYakata1GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_YAKATA1

    @JvmOverloads
    fun isSdpsTonoGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SDPSTONO

    @JvmOverloads
    fun isSdpsKaizokuGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SDPSKAIZOKU

    @JvmOverloads
    fun isProgOmakeGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_PROGOMAKE

    @JvmOverloads
    fun isAyumiHintGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_AYUMIHINT

    @JvmOverloads
    fun isAyumiLiveGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_AYUMILIVE

    @JvmOverloads
    fun isNiseNaguriGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_NISENAGURI

    @JvmOverloads
    fun isCarolGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_CAROL

    @JvmOverloads
    fun isOthellotGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_OTHELLOT

    @JvmOverloads
    fun isMasqueradeGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MASQUERADE

    @JvmOverloads
    fun isDekirukanaGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_DEKIRUKANA

    @JvmOverloads
    fun isTatakikoroshiGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_TATAKIKOROSHI

    @JvmOverloads
    fun isBalloonPopGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_BALLOONPOP

    @JvmOverloads
    fun isHiyoko2GameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_HIYOKO2

    @JvmOverloads
    fun isSelfGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_SELFGAME

    @JvmOverloads
    fun isMomoiroGuardianGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_MOMOIROGUARDIAN

    @JvmOverloads
    fun isRance1ClassicGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_RANCE1CLASSIC

    @JvmOverloads
    fun isRance2ClassicGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_RANCE2CLASSIC

    @JvmOverloads
    fun isRance3ClassicGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean =
        GameCatalog.classifyDirectory(gameDir = gameDir, dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "", saveDir = saveDir, displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "") == GameCatalog.ID_RANCE3CLASSIC

    @JvmOverloads
    fun isNonModifierBuiltInGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean = when (
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        )
    ) {
        GameCatalog.ID_GALZOO,
        GameCatalog.ID_IXSEAL,
        GameCatalog.ID_HARUKA,
        GameCatalog.ID_TSUMASHIBORI,
        GameCatalog.ID_TSUMAMIGUI3,
        GameCatalog.ID_DAIBANCHOU,
        GameCatalog.ID_DAITEIKOKU,
        GameCatalog.ID_TSUMAMIGUI,
        GameCatalog.ID_KAERUNYO,
        GameCatalog.ID_ATLACHNACHA,
        GameCatalog.ID_REISHIKI,
        GameCatalog.ID_HARUURARE,
        GameCatalog.ID_OKUSAMAJUN,
        GameCatalog.ID_OKUSAMANTR,
        GameCatalog.ID_EVENICLE2RANCE,
        GameCatalog.ID_EVENICLEGAIDEN,
        GameCatalog.ID_WAIDONYO,
        GameCatalog.ID_RANCE6AFTER,
        GameCatalog.ID_YAKATA2,
        GameCatalog.ID_SDPSMARIA,
        GameCatalog.ID_TOUSHIN3,
        GameCatalog.ID_OYAKORANKAN,
        GameCatalog.ID_YORUGAKURU,
        GameCatalog.ID_RANCE1DIGEST,
        GameCatalog.ID_MAMAN,
        GameCatalog.ID_EVENICLERANCE,
        GameCatalog.ID_HAHARANMAN,
        GameCatalog.ID_TSUMAMIGUI2,
        GameCatalog.ID_TSUMAMIGUI3JUN,
        GameCatalog.ID_TSUMAMIGUI3NTR,
        GameCatalog.ID_DIABOLIQUE,
        GameCatalog.ID_GAKUENKING,
        GameCatalog.ID_GAKUENKINGEX,
        GameCatalog.ID_OUDOUYUUSHA,
        GameCatalog.ID_TOUSHIN1,
        GameCatalog.ID_TOUSHIN2,
        GameCatalog.ID_DARCROWS,
        GameCatalog.ID_IKUSAMIKO,
        GameCatalog.ID_ONLYYOU,
        GameCatalog.ID_SANBIKI,
        GameCatalog.ID_PERSIOM,
        GameCatalog.ID_SEEINAO,
        GameCatalog.ID_HUSHABY,
        GameCatalog.ID_AYUMI,
        GameCatalog.ID_CIELCLAIRE,
        GameCatalog.ID_NIGHTDEMON,
        GameCatalog.ID_KATSUMI,
        GameCatalog.ID_DRSTOP,
        GameCatalog.ID_DPSALL,
        GameCatalog.ID_KOREDPS,
        GameCatalog.ID_KAERUALICE,
        GameCatalog.ID_PROSTUDENTG,
        GameCatalog.ID_PROSTUDENTGOOD,
        GameCatalog.ID_PASTELCHIME,
        GameCatalog.ID_PASTELCHIME2,
        GameCatalog.ID_YAKATA3,
        GameCatalog.ID_DALK,
        GameCatalog.ID_NINGENGARI,
        GameCatalog.ID_PASCHACPP,
        GameCatalog.ID_PASCHA3,
        GameCatalog.ID_MAMATOTO,
        GameCatalog.ID_ONLYYOURECROSS,
        GameCatalog.ID_MAMORIGAMI,
        GameCatalog.ID_AMBIVALENZ,
        GameCatalog.ID_FUNNYBEE,
        GameCatalog.ID_TOUSHIN2SP,
        GameCatalog.ID_MUGENHOUYOU,
        GameCatalog.ID_OTOMESENKI,
        GameCatalog.ID_GAKUENHYOURYUU,
        GameCatalog.ID_MAIDNOSUSUME,
        GameCatalog.ID_DALKGAIDEN,
        GameCatalog.ID_MAMANYONYO,
        GameCatalog.ID_MAJONOSHOKUZAI,
        GameCatalog.ID_SABOTEN,
        GameCatalog.ID_DOUBLESENSEI,
        GameCatalog.ID_OJOUSAMA,
        GameCatalog.ID_EDORANZE,
        GameCatalog.ID_VANISH,
        GameCatalog.ID_HOKENSHITSU,
        GameCatalog.ID_KAMUNAGARA,
        GameCatalog.ID_ERUMEIFLOWER,
        GameCatalog.ID_ERUMEIWAKARE,
        GameCatalog.ID_MANANA1,
        GameCatalog.ID_MANANA2,
        GameCatalog.ID_MANANA3,
        GameCatalog.ID_MANANA4,
        GameCatalog.ID_MANANA5,
        GameCatalog.ID_TSUMASAKURA,
        GameCatalog.ID_SHAMANSSANCTUARY,
        GameCatalog.ID_ALIVEZ,
        GameCatalog.ID_DUNGEONSDOLLS,
        GameCatalog.ID_SUZUMIYA,
        GameCatalog.ID_SHIMAIMA,
        GameCatalog.ID_DAIBANCHOUGAIDEN,
        GameCatalog.ID_COSPLAYLEMON,
        GameCatalog.ID_WHIPHONEY,
        GameCatalog.ID_ONIMUSU,
        GameCatalog.ID_PROGRX,
        GameCatalog.ID_DPSVERN,
        GameCatalog.ID_SUKIIRONO,
        GameCatalog.ID_GIRLS,
        GameCatalog.ID_DEBUT,
        GameCatalog.ID_HARUKAVSESCA,
        GameCatalog.ID_HARUKANINJA,
        GameCatalog.ID_MEGANE,
        GameCatalog.ID_RACHELSAKURA,
        GameCatalog.ID_SABAKUNIKA,
        GameCatalog.ID_VANISHPALM,
        GameCatalog.ID_BLADEBRIDERS,
        GameCatalog.ID_DORAPEKO,
        GameCatalog.ID_HANIWAPETIT,
        GameCatalog.ID_YAKATA456,
        GameCatalog.ID_UMINIOCHITE,
        GameCatalog.ID_GAKUENHYOURYUU2,
        GameCatalog.ID_YOUSEI,
        GameCatalog.ID_TOBIRA,
        GameCatalog.ID_BUROBURO,
        GameCatalog.ID_KUDAKERO,
        GameCatalog.ID_LITTLEVAMPIRE,
        GameCatalog.ID_INTRUDER,
        GameCatalog.ID_CRESCENT,
        GameCatalog.ID_TENGU,
        GameCatalog.ID_DPS,
        GameCatalog.ID_YAKATA1,
        GameCatalog.ID_SDPSTONO,
        GameCatalog.ID_SDPSKAIZOKU,
        GameCatalog.ID_PROGOMAKE,
        GameCatalog.ID_AYUMIHINT,
        GameCatalog.ID_AYUMILIVE,
        GameCatalog.ID_NISENAGURI,
        GameCatalog.ID_CAROL,
        GameCatalog.ID_OTHELLOT,
        GameCatalog.ID_MASQUERADE,
        GameCatalog.ID_DEKIRUKANA,
        GameCatalog.ID_TATAKIKOROSHI,
        GameCatalog.ID_BALLOONPOP,
        GameCatalog.ID_HIYOKO2,
        GameCatalog.ID_SELFGAME,
        GameCatalog.ID_MOMOIROGUARDIAN,
        GameCatalog.ID_RANCE1CLASSIC,
        GameCatalog.ID_RANCE2CLASSIC,
        GameCatalog.ID_RANCE3CLASSIC,
        GameCatalog.ID_DPSSGFAHREN,
        GameCatalog.ID_DPSSGKATEI,
        GameCatalog.ID_DPSSGNOBUNAGA,
        GameCatalog.ID_DPSSG2ANTIQUE,
        GameCatalog.ID_DPSSG2IKENAI,
        GameCatalog.ID_DPSSG2AKAI,
        GameCatalog.ID_DPSSG3RABBIT,
        GameCatalog.ID_DPSSG3SHINKON,
        GameCatalog.ID_DPSSG3SOTSUGYOU -> true
        else -> false
    }

    fun isEscalayerRebootGameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_ESCALAYER_REBOOT

    fun isEscalayerGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (isEscalayerRebootGameDir(gameDir, dirNameFallback, saveDir) ||
            isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir)
        ) return false
        if (GameCatalog.isEscalayerGameDir(gameDir)) return true
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) && !it.name.equals("System39.ain", ignoreCase = true) }) {
                return false
            }
        }
        if (saveDir != null && saveDir.isDirectory) {
            if (saveDir.listFiles()?.any {
                    it.isFile && it.name.lowercase().startsWith("doki_s") && it.name.lowercase().endsWith(".asd")
                } == true &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "GlobalSaveData.asd").isFile
            ) {
                return true
            }
        }
        if (!dirNameFallback.isNullOrBlank()) {
            val lower = dirNameFallback.lowercase()
            if (GameCatalog.isEscalayerRebootAlias(lower)) {
                return false
            }
            if (lower == "escalayer" || lower.startsWith("escalayer-") || lower.contains("escalayer") ||
                lower.contains("超昂天使") || lower.contains("エスカレイヤー")
            ) {
                return true
            }
        }
        return false
    }

    fun isEvenicle2GameDir(
        gameDir: File?,
        dirNameFallback: String? = null,
        saveDir: File? = null
    ): Boolean =
        GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        ) == GameCatalog.ID_EVENICLE2

    fun isEvenicleGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        if (isRance10GameDir(gameDir, dirNameFallback, saveDir) ||
            isEvenicle2GameDir(gameDir, dirNameFallback, saveDir) ||
            isEscalayerRebootGameDir(gameDir, dirNameFallback, saveDir) ||
            isNonModifierBuiltInGameDir(gameDir, dirNameFallback, saveDir) ||
            isRance01GameDir(gameDir, dirNameFallback, saveDir) ||
            isRance02GameDir(gameDir, dirNameFallback, saveDir) ||
            isRance03GameDir(gameDir, dirNameFallback, saveDir) ||
            isRance06GameDir(gameDir, dirNameFallback, saveDir) ||
            isSengokuRanceGameDir(gameDir, dirNameFallback, saveDir) ||
            isRance08GameDir(gameDir, dirNameFallback, saveDir) ||
            isRance09GameDir(gameDir, dirNameFallback, saveDir) ||
            isEscalayerGameDir(gameDir, dirNameFallback, saveDir)
        ) {
            return false
        }
        if (gameDir != null && gameDir.isDirectory) {
            val files = gameDir.listFiles()
            if (files != null && files.any { it.isFile && it.name.endsWith(".ain", ignoreCase = true) }) {
                return files.any { it.isFile && it.name.equals("Evenicle.ain", ignoreCase = true) }
            }
        }
        if (saveDir != null && saveDir.isDirectory) {
            if ((File(saveDir, "CommonSystemData.sav").isFile || File(saveDir, "CommonOtherData.sav").isFile) &&
                !File(saveDir, "AFCommon.asd").isFile &&
                !File(saveDir, "AFBGMMode.asd").isFile &&
                !File(saveDir, "GlobalSave.asd").isFile &&
                !File(saveDir, "Collection.asd").isFile
            ) {
                return true
            }
        }
        if (!dirNameFallback.isNullOrBlank()) {
            val lower = dirNameFallback.lowercase()
            if (lower == "evenicle" || lower.startsWith("evenicle-") ||
                lower.contains("イブニクル") || lower.contains("夏娃年代记")
            ) {
                return true
            }
        }
        return false
    }

    @JvmOverloads
    fun isEvenicleFamilyGameDir(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): Boolean {
        val id = GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        )
        if (GenericModifierRegistry.isEvenicleFamily(id)) return true
        return isEvenicleGameDir(gameDir, dirNameFallback, saveDir)
    }

    @JvmOverloads
    fun resolveGenericModifierGameId(gameDir: File?, dirNameFallback: String? = null, saveDir: File? = null): String? {
        val id = GameCatalog.classifyDirectory(
            gameDir = gameDir,
            dirName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: "",
            saveDir = saveDir,
            displayName = dirNameFallback ?: gameDir?.name ?: saveDir?.name ?: ""
        )
        return GenericModifierRegistry.find(id)?.id
    }

    fun syncNativeConfigPath(context: Context) {
        if (!loaded) return
        try {
            nativeSetConfigPath(getConfigFilePath(context))
        } catch (_: Throwable) {
        }
    }

    private fun readConfigJson(context: Context): JSONObject {
        synchronized(configLock) {
            val cfgFile = File(context.filesDir, CONFIG_FILE_NAME)
            if (cfgFile.isFile) {
                try {
                    val json = JSONObject(cfgFile.readText(Charsets.UTF_8))
                    val migratedV1 = json.optBoolean(KEY_FLOATING_DEFAULT_ON_V1, false)
                    val ctrlDefaultOffV2 = json.optBoolean(KEY_CTRL_DEFAULT_OFF_V2, false)
                    val disableStreamer = if (json.has(KEY_DISABLE_STREAMER_MODE)) {
                        json.optBoolean(KEY_DISABLE_STREAMER_MODE, false)
                    } else {
                        (json.optInt(KEY_LOCK_MASK, 0) and LOCK_DISABLE_STREAMER_MODE) != 0
                    }
                    val rawMask = json.optInt(KEY_LOCK_MASK, 0)
                    val mask = if (disableStreamer) {
                        rawMask or LOCK_DISABLE_STREAMER_MODE
                    } else {
                        rawMask and LOCK_DISABLE_STREAMER_MODE.inv()
                    }
                    val dohnaMask = json.optInt(KEY_DOHNA_LOCK_MASK, 0) and LOCK_DD_ALL_MASK
                    val evenicleMask = json.optInt(KEY_EVENICLE_LOCK_MASK, 0) and LOCK_EV_ALL_MASK
                    val srMask = json.optInt(KEY_SR_LOCK_MASK, 0) and LOCK_SR_ALL_MASK
                    val r01Mask = json.optInt(KEY_R01_LOCK_MASK, 0) and LOCK_R01_ALL_MASK
                    val r02Mask = json.optInt(KEY_R02_LOCK_MASK, 0) and LOCK_R02_ALL_MASK
                    val r03Mask = json.optInt(KEY_R03_LOCK_MASK, 0) and LOCK_R03_ALL_MASK
                    val r08Mask = json.optInt(KEY_R08_LOCK_MASK, 0) and LOCK_R08_ALL_MASK
                    val r09Mask = json.optInt(KEY_R09_LOCK_MASK, 0) and LOCK_R09_ALL_MASK
                    val escMask = json.optInt(KEY_ESC_LOCK_MASK, 0) and LOCK_ESC_ALL_MASK
                    json.put(KEY_DISABLE_STREAMER_MODE, disableStreamer)
                    json.put(KEY_LOCK_MASK, mask)
                    json.put(KEY_DOHNA_LOCK_MASK, dohnaMask)
                    json.put(KEY_EVENICLE_LOCK_MASK, evenicleMask)
                    json.put(KEY_SR_LOCK_MASK, srMask)
                    json.put(KEY_R01_LOCK_MASK, r01Mask)
                    json.put(KEY_R02_LOCK_MASK, r02Mask)
                    json.put(KEY_R03_LOCK_MASK, r03Mask)
                    json.put(KEY_R08_LOCK_MASK, r08Mask)
                    json.put(KEY_R09_LOCK_MASK, r09Mask)
                    json.put(KEY_ESC_LOCK_MASK, escMask)
                    val hasLegacyUiScale = json.has(KEY_FONT_SCALE_PCT) || json.has(KEY_SCROLLBAR_SCALE_PCT)
                    val fontScaleMap = sanitizeScaleMap(
                        json.optJSONObject(KEY_GAME_FONT_SCALE_PCT),
                        DEFAULT_FONT_SCALE_PCT,
                        ::clampFontScale
                    )
                    val sbScaleMap = sanitizeScaleMap(
                        json.optJSONObject(KEY_GAME_SCROLLBAR_SCALE_PCT),
                        DEFAULT_SCROLLBAR_SCALE_PCT,
                        ::clampScrollbarScale
                    )
                    json.remove(KEY_FONT_SCALE_PCT)
                    json.remove(KEY_SCROLLBAR_SCALE_PCT)
                    json.put(KEY_GAME_FONT_SCALE_PCT, fontScaleMap)
                    json.put(KEY_GAME_SCROLLBAR_SCALE_PCT, sbScaleMap)
                    var needsSave = hasLegacyUiScale
                    if (!migratedV1) {
                        json.put(KEY_FLOATING_ENABLED, true)
                        json.put(KEY_FLOATING_DEFAULT_ON_V1, true)
                        needsSave = true
                    } else if (!json.has(KEY_FLOATING_ENABLED)) {
                        json.put(KEY_FLOATING_ENABLED, true)
                    }
                    if (!ctrlDefaultOffV2) {
                        json.put(KEY_CTRL_OVERLAY_ENABLED, false)
                        json.put(KEY_CTRL_OVERLAY_VISIBLE, false)
                        json.put(KEY_CTRL_DEFAULT_ON_V1, true)
                        json.put(KEY_CTRL_DEFAULT_OFF_V2, true)
                        needsSave = true
                    } else {
                        if (!json.has(KEY_CTRL_OVERLAY_ENABLED)) {
                            json.put(KEY_CTRL_OVERLAY_ENABLED, false)
                        }
                        if (!json.optBoolean(KEY_CTRL_OVERLAY_ENABLED, false)) {
                            json.put(KEY_CTRL_OVERLAY_VISIBLE, false)
                        }
                    }
                    if (needsSave) {
                        writeConfigJson(context, json)
                    }
                    return json
                } catch (_: Throwable) {
                }
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val migratedV1 = prefs.getBoolean(KEY_FLOATING_DEFAULT_ON_V1, false)
            val ctrlDefaultOffV2 = prefs.getBoolean(KEY_CTRL_DEFAULT_OFF_V2, false)
            val floatingEnabled = if (!migratedV1) true else prefs.getBoolean(KEY_FLOATING_ENABLED, true)
            val ctrlEnabled = if (!ctrlDefaultOffV2) false else prefs.getBoolean(KEY_CTRL_OVERLAY_ENABLED, false)
            val ctrlVisible = if (!ctrlDefaultOffV2) false else (ctrlEnabled && prefs.getBoolean(KEY_CTRL_OVERLAY_VISIBLE, false))
            val disableStreamer = if (prefs.contains(KEY_DISABLE_STREAMER_MODE)) {
                prefs.getBoolean(KEY_DISABLE_STREAMER_MODE, false)
            } else {
                (prefs.getInt(KEY_LOCK_MASK, 0) and LOCK_DISABLE_STREAMER_MODE) != 0
            }
            val rawMask = prefs.getInt(KEY_LOCK_MASK, 0)
            val mask = if (disableStreamer) {
                rawMask or LOCK_DISABLE_STREAMER_MODE
            } else {
                rawMask and LOCK_DISABLE_STREAMER_MODE.inv()
            }
            val dohnaMask = prefs.getInt(KEY_DOHNA_LOCK_MASK, 0) and LOCK_DD_ALL_MASK
            val evenicleMask = prefs.getInt(KEY_EVENICLE_LOCK_MASK, 0) and LOCK_EV_ALL_MASK
            val srMask = prefs.getInt(KEY_SR_LOCK_MASK, 0) and LOCK_SR_ALL_MASK
            val r01Mask = prefs.getInt(KEY_R01_LOCK_MASK, 0) and LOCK_R01_ALL_MASK
            val r02Mask = prefs.getInt(KEY_R02_LOCK_MASK, 0) and LOCK_R02_ALL_MASK
            val r03Mask = prefs.getInt(KEY_R03_LOCK_MASK, 0) and LOCK_R03_ALL_MASK
            val r08Mask = prefs.getInt(KEY_R08_LOCK_MASK, 0) and LOCK_R08_ALL_MASK
            val r09Mask = prefs.getInt(KEY_R09_LOCK_MASK, 0) and LOCK_R09_ALL_MASK
            val escMask = prefs.getInt(KEY_ESC_LOCK_MASK, 0) and LOCK_ESC_ALL_MASK
            val extraLocksMap = parseExtraLocksMap(prefs.getString(KEY_EXTRA_LOCKS, null))
            val fontScaleMap = parseScaleMapFromPrefs(
                prefs.getString(KEY_GAME_FONT_SCALE_PCT, null),
                DEFAULT_FONT_SCALE_PCT,
                ::clampFontScale
            )
            val sbScaleMap = parseScaleMapFromPrefs(
                prefs.getString(KEY_GAME_SCROLLBAR_SCALE_PCT, null),
                DEFAULT_SCROLLBAR_SCALE_PCT,
                ::clampScrollbarScale
            )
            val json = JSONObject().apply {
                put(KEY_FLOATING_ENABLED, floatingEnabled)
                put(KEY_FLOATING_DEFAULT_ON_V1, true)
                put(KEY_CTRL_OVERLAY_ENABLED, ctrlEnabled)
                put(KEY_CTRL_DEFAULT_ON_V1, true)
                put(KEY_CTRL_DEFAULT_OFF_V2, true)
                put(KEY_CTRL_OVERLAY_VISIBLE, ctrlVisible)
                put(KEY_DISABLE_STREAMER_MODE, disableStreamer)
                put(KEY_LOCK_MASK, mask)
                put(KEY_DOHNA_LOCK_MASK, dohnaMask)
                put(KEY_EVENICLE_LOCK_MASK, evenicleMask)
                put(KEY_SR_LOCK_MASK, srMask)
                put(KEY_R01_LOCK_MASK, r01Mask)
                put(KEY_R02_LOCK_MASK, r02Mask)
                put(KEY_R03_LOCK_MASK, r03Mask)
                put(KEY_R08_LOCK_MASK, r08Mask)
                put(KEY_R09_LOCK_MASK, r09Mask)
                put(KEY_ESC_LOCK_MASK, escMask)
                if (extraLocksMap.length() > 0) put(KEY_EXTRA_LOCKS, extraLocksMap)
                put(KEY_GAME_FONT_SCALE_PCT, fontScaleMap)
                put(KEY_GAME_SCROLLBAR_SCALE_PCT, sbScaleMap)
                put(KEY_CONFIG_SEQ, prefs.getInt(KEY_CONFIG_SEQ, 0))
            }
            if (!migratedV1 || !ctrlDefaultOffV2 ||
                prefs.contains(KEY_FONT_SCALE_PCT) || prefs.contains(KEY_SCROLLBAR_SCALE_PCT)
            ) {
                writeConfigJson(context, json)
            }
            return json
        }
    }

    private fun writeConfigJson(context: Context, json: JSONObject) {
        synchronized(configLock) {
            val floatingEnabled = json.optBoolean(KEY_FLOATING_ENABLED, true)
            val ctrlEnabled = json.optBoolean(KEY_CTRL_OVERLAY_ENABLED, false)
            val ctrlVisible = ctrlEnabled && json.optBoolean(KEY_CTRL_OVERLAY_VISIBLE, false)
            val disableStreamer = if (json.has(KEY_DISABLE_STREAMER_MODE)) {
                json.optBoolean(KEY_DISABLE_STREAMER_MODE, false)
            } else {
                (json.optInt(KEY_LOCK_MASK, 0) and LOCK_DISABLE_STREAMER_MODE) != 0
            }
            val rawMask = json.optInt(KEY_LOCK_MASK, 0)
            val mask = if (disableStreamer) {
                rawMask or LOCK_DISABLE_STREAMER_MODE
            } else {
                rawMask and LOCK_DISABLE_STREAMER_MODE.inv()
            }
            val dohnaMask = json.optInt(KEY_DOHNA_LOCK_MASK, 0) and LOCK_DD_ALL_MASK
            val evenicleMask = json.optInt(KEY_EVENICLE_LOCK_MASK, 0) and LOCK_EV_ALL_MASK
            val srMask = json.optInt(KEY_SR_LOCK_MASK, 0) and LOCK_SR_ALL_MASK
            val r01Mask = json.optInt(KEY_R01_LOCK_MASK, 0) and LOCK_R01_ALL_MASK
            val r02Mask = json.optInt(KEY_R02_LOCK_MASK, 0) and LOCK_R02_ALL_MASK
            val r03Mask = json.optInt(KEY_R03_LOCK_MASK, 0) and LOCK_R03_ALL_MASK
            val r08Mask = json.optInt(KEY_R08_LOCK_MASK, 0) and LOCK_R08_ALL_MASK
            val r09Mask = json.optInt(KEY_R09_LOCK_MASK, 0) and LOCK_R09_ALL_MASK
            val escMask = json.optInt(KEY_ESC_LOCK_MASK, 0) and LOCK_ESC_ALL_MASK
            val fontScaleMap = sanitizeScaleMap(
                json.optJSONObject(KEY_GAME_FONT_SCALE_PCT),
                DEFAULT_FONT_SCALE_PCT,
                ::clampFontScale
            )
            val sbScaleMap = sanitizeScaleMap(
                json.optJSONObject(KEY_GAME_SCROLLBAR_SCALE_PCT),
                DEFAULT_SCROLLBAR_SCALE_PCT,
                ::clampScrollbarScale
            )
            val nextSeq = json.optInt(KEY_CONFIG_SEQ, 0) + 1
            json.put(KEY_FLOATING_ENABLED, floatingEnabled)
            json.put(KEY_FLOATING_DEFAULT_ON_V1, true)
            json.put(KEY_CTRL_OVERLAY_ENABLED, ctrlEnabled)
            json.put(KEY_CTRL_DEFAULT_ON_V1, true)
            json.put(KEY_CTRL_DEFAULT_OFF_V2, true)
            json.put(KEY_CTRL_OVERLAY_VISIBLE, ctrlVisible)
            json.put(KEY_DISABLE_STREAMER_MODE, disableStreamer)
            json.put(KEY_LOCK_MASK, mask)
            json.put(KEY_DOHNA_LOCK_MASK, dohnaMask)
            json.put(KEY_EVENICLE_LOCK_MASK, evenicleMask)
            json.put(KEY_SR_LOCK_MASK, srMask)
            json.put(KEY_R01_LOCK_MASK, r01Mask)
            json.put(KEY_R02_LOCK_MASK, r02Mask)
            json.put(KEY_R03_LOCK_MASK, r03Mask)
            json.put(KEY_R08_LOCK_MASK, r08Mask)
            json.put(KEY_R09_LOCK_MASK, r09Mask)
            json.put(KEY_ESC_LOCK_MASK, escMask)
            json.remove(KEY_FONT_SCALE_PCT)
            json.remove(KEY_SCROLLBAR_SCALE_PCT)
            json.put(KEY_GAME_FONT_SCALE_PCT, fontScaleMap)
            json.put(KEY_GAME_SCROLLBAR_SCALE_PCT, sbScaleMap)
            json.put(KEY_CONFIG_SEQ, nextSeq)
            val pid = Process.myPid()
            val tid = Thread.currentThread().id
            val seq = configTmpSeq.incrementAndGet()
            val cfgFile = File(context.filesDir, CONFIG_FILE_NAME)
            val tmpFile = File(context.filesDir, "$CONFIG_FILE_NAME.$pid.$tid.$seq.tmp")
            val payload = json.toString()
            try {
                context.filesDir.mkdirs()
                tmpFile.writeText(payload, Charsets.UTF_8)
                if (!tmpFile.renameTo(cfgFile)) {
                    cfgFile.writeText(payload, Charsets.UTF_8)
                }
            } catch (_: Throwable) {
            } finally {
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_FLOATING_ENABLED, floatingEnabled)
                .putBoolean(KEY_FLOATING_DEFAULT_ON_V1, true)
                .putBoolean(KEY_CTRL_OVERLAY_ENABLED, ctrlEnabled)
                .putBoolean(KEY_CTRL_DEFAULT_ON_V1, true)
                .putBoolean(KEY_CTRL_DEFAULT_OFF_V2, true)
                .putBoolean(KEY_CTRL_OVERLAY_VISIBLE, ctrlVisible)
                .putBoolean(KEY_DISABLE_STREAMER_MODE, disableStreamer)
                .putInt(KEY_LOCK_MASK, mask)
                .putInt(KEY_DOHNA_LOCK_MASK, dohnaMask)
                .putInt(KEY_EVENICLE_LOCK_MASK, evenicleMask)
                .putInt(KEY_SR_LOCK_MASK, srMask)
                .putInt(KEY_R01_LOCK_MASK, r01Mask)
                .putInt(KEY_R02_LOCK_MASK, r02Mask)
                .putInt(KEY_R03_LOCK_MASK, r03Mask)
                .putInt(KEY_R08_LOCK_MASK, r08Mask)
                .putInt(KEY_R09_LOCK_MASK, r09Mask)
                .putInt(KEY_ESC_LOCK_MASK, escMask)
                .putString(KEY_EXTRA_LOCKS, (json.optJSONObject(KEY_EXTRA_LOCKS) ?: JSONObject()).toString())
                .remove(KEY_FONT_SCALE_PCT)
                .remove(KEY_SCROLLBAR_SCALE_PCT)
                .putString(KEY_GAME_FONT_SCALE_PCT, fontScaleMap.toString())
                .putString(KEY_GAME_SCROLLBAR_SCALE_PCT, sbScaleMap.toString())
                .putInt(KEY_CONFIG_SEQ, nextSeq)
                .commit()
        }
    }

    fun normalizeUiScaleGameKey(rawKey: String?): String? {
        if (rawKey.isNullOrBlank()) return null
        val trimmed = rawKey.trim().trimEnd('/', '\\')
        if (trimmed.isEmpty()) return null
        val sep = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        val base = (if (sep >= 0) trimmed.substring(sep + 1) else trimmed).trim()
        if (base.isEmpty() || base == "." || base == "..") return null
        return base
    }

    private fun sanitizeScaleMap(
        raw: JSONObject?,
        defaultPct: Int,
        clamp: (Int) -> Int
    ): JSONObject {
        val out = JSONObject()
        if (raw == null) return out
        val keys = raw.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val normKey = normalizeUiScaleGameKey(rawKey) ?: continue
            val value = raw.opt(rawKey) as? Number ?: continue
            val clamped = clamp(value.toInt())
            if (clamped != defaultPct) {
                out.put(normKey, clamped)
            }
        }
        return out
    }

    private fun parseScaleMapFromPrefs(
        rawStr: String?,
        defaultPct: Int,
        clamp: (Int) -> Int
    ): JSONObject {
        if (rawStr.isNullOrBlank()) return JSONObject()
        return try {
            sanitizeScaleMap(JSONObject(rawStr), defaultPct, clamp)
        } catch (_: Throwable) {
            JSONObject()
        }
    }

    fun clampFontScale(pct: Int): Int = pct.coerceIn(MIN_FONT_SCALE_PCT, MAX_FONT_SCALE_PCT)

    fun clampScrollbarScale(pct: Int): Int = pct.coerceIn(MIN_SCROLLBAR_SCALE_PCT, MAX_SCROLLBAR_SCALE_PCT)

    fun clearActiveUiScaleGameKey() {
        activeUiScaleGameKey = null
    }

    fun getFontScalePercent(context: Context, gameKey: String?): Int {
        val key = normalizeUiScaleGameKey(gameKey) ?: return DEFAULT_FONT_SCALE_PCT
        val map = readConfigJson(context).optJSONObject(KEY_GAME_FONT_SCALE_PCT) ?: return DEFAULT_FONT_SCALE_PCT
        val value = map.opt(key) as? Number ?: return DEFAULT_FONT_SCALE_PCT
        return clampFontScale(value.toInt())
    }

    fun setFontScalePercent(context: Context, gameKey: String?, pct: Int) {
        val key = normalizeUiScaleGameKey(gameKey) ?: return
        val clamped = clampFontScale(pct)
        synchronized(configLock) {
            val json = readConfigJson(context)
            val map = json.optJSONObject(KEY_GAME_FONT_SCALE_PCT) ?: JSONObject()
            if (clamped == DEFAULT_FONT_SCALE_PCT) {
                map.remove(key)
            } else {
                map.put(key, clamped)
            }
            json.put(KEY_GAME_FONT_SCALE_PCT, map)
            writeConfigJson(context, json)
        }
        syncUiScaleToNativeIfActive(context, key)
    }

    fun getScrollbarScalePercent(context: Context, gameKey: String?): Int {
        val key = normalizeUiScaleGameKey(gameKey) ?: return DEFAULT_SCROLLBAR_SCALE_PCT
        val map = readConfigJson(context).optJSONObject(KEY_GAME_SCROLLBAR_SCALE_PCT) ?: return DEFAULT_SCROLLBAR_SCALE_PCT
        val value = map.opt(key) as? Number ?: return DEFAULT_SCROLLBAR_SCALE_PCT
        return clampScrollbarScale(value.toInt())
    }

    fun setScrollbarScalePercent(context: Context, gameKey: String?, pct: Int) {
        val key = normalizeUiScaleGameKey(gameKey) ?: return
        val clamped = clampScrollbarScale(pct)
        synchronized(configLock) {
            val json = readConfigJson(context)
            val map = json.optJSONObject(KEY_GAME_SCROLLBAR_SCALE_PCT) ?: JSONObject()
            if (clamped == DEFAULT_SCROLLBAR_SCALE_PCT) {
                map.remove(key)
            } else {
                map.put(key, clamped)
            }
            json.put(KEY_GAME_SCROLLBAR_SCALE_PCT, map)
            writeConfigJson(context, json)
        }
        syncUiScaleToNativeIfActive(context, key)
    }

    private fun syncUiScaleToNativeIfActive(context: Context, key: String) {
        if (!loaded || activeUiScaleGameKey != key) return
        syncUiScaleToNative(context, key)
    }

    fun syncUiScaleToNative(context: Context, gameKey: String?) {
        val key = normalizeUiScaleGameKey(gameKey)
        activeUiScaleGameKey = key
        if (!loaded) return
        try {
            val (fontPct, sbPct) = synchronized(configLock) {
                if (key == null) {
                    DEFAULT_FONT_SCALE_PCT to DEFAULT_SCROLLBAR_SCALE_PCT
                } else {
                    val json = readConfigJson(context)
                    val fMap = json.optJSONObject(KEY_GAME_FONT_SCALE_PCT)
                    val sMap = json.optJSONObject(KEY_GAME_SCROLLBAR_SCALE_PCT)
                    val f = (fMap?.opt(key) as? Number)?.toInt()?.let(::clampFontScale) ?: DEFAULT_FONT_SCALE_PCT
                    val s = (sMap?.opt(key) as? Number)?.toInt()?.let(::clampScrollbarScale) ?: DEFAULT_SCROLLBAR_SCALE_PCT
                    f to s
                }
            }
            nativeSetConfigPath(getConfigFilePath(context))
            nativeSetUiScaleGameKey(key)
            nativeSetUiScale(fontPct, sbPct)
        } catch (_: Throwable) {
        }
    }

    fun getConfigSeq(context: Context): Int {
        return readConfigJson(context).optInt(KEY_CONFIG_SEQ, 0)
    }

    fun syncLockMaskFromNative(context: Context): Int {
        if (!loaded) return getConfigSeq(context)
        return try {
            val liveMask = nativeGetLiveLocks()
            val dohnaLiveMask = getDohnaLiveLocks()
            val evenicleLiveMask = getEvenicleLiveLocks()
            val srLiveMask = getSrLiveLocks()
            val r01LiveMask = getR01LiveLocks()
            val r02LiveMask = getR02LiveLocks()
            val r03LiveMask = getR03LiveLocks()
            val r08LiveMask = getR08LiveLocks()
            val r09LiveMask = getR09LiveLocks()
            synchronized(configLock) {
                val json = readConfigJson(context)
                val savedMask = json.optInt(KEY_LOCK_MASK, 0)
                val savedDohnaMask = json.optInt(KEY_DOHNA_LOCK_MASK, 0) and LOCK_DD_ALL_MASK
                val savedEvenicleMask = json.optInt(KEY_EVENICLE_LOCK_MASK, 0) and LOCK_EV_ALL_MASK
                val savedSrMask = json.optInt(KEY_SR_LOCK_MASK, 0) and LOCK_SR_ALL_MASK
                val savedR01Mask = json.optInt(KEY_R01_LOCK_MASK, 0) and LOCK_R01_ALL_MASK
                val savedR02Mask = json.optInt(KEY_R02_LOCK_MASK, 0) and LOCK_R02_ALL_MASK
                val savedR03Mask = json.optInt(KEY_R03_LOCK_MASK, 0) and LOCK_R03_ALL_MASK
                val savedR08Mask = json.optInt(KEY_R08_LOCK_MASK, 0) and LOCK_R08_ALL_MASK
                val savedR09Mask = json.optInt(KEY_R09_LOCK_MASK, 0) and LOCK_R09_ALL_MASK
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val prefsMask = prefs.getInt(KEY_LOCK_MASK, -1)
                val prefsDohnaMask = prefs.getInt(KEY_DOHNA_LOCK_MASK, -1)
                val prefsEvenicleMask = prefs.getInt(KEY_EVENICLE_LOCK_MASK, -1)
                val prefsSrMask = prefs.getInt(KEY_SR_LOCK_MASK, -1)
                val prefsR01Mask = prefs.getInt(KEY_R01_LOCK_MASK, -1)
                val prefsR02Mask = prefs.getInt(KEY_R02_LOCK_MASK, -1)
                val prefsR03Mask = prefs.getInt(KEY_R03_LOCK_MASK, -1)
                val prefsR08Mask = prefs.getInt(KEY_R08_LOCK_MASK, -1)
                val prefsR09Mask = prefs.getInt(KEY_R09_LOCK_MASK, -1)
                if (liveMask != savedMask || liveMask != prefsMask ||
                    dohnaLiveMask != savedDohnaMask || dohnaLiveMask != prefsDohnaMask ||
                    evenicleLiveMask != savedEvenicleMask || evenicleLiveMask != prefsEvenicleMask ||
                    srLiveMask != savedSrMask || srLiveMask != prefsSrMask ||
                    r01LiveMask != savedR01Mask || r01LiveMask != prefsR01Mask ||
                    r02LiveMask != savedR02Mask || r02LiveMask != prefsR02Mask ||
                    r03LiveMask != savedR03Mask || r03LiveMask != prefsR03Mask ||
                    r08LiveMask != savedR08Mask || r08LiveMask != prefsR08Mask ||
                    r09LiveMask != savedR09Mask || r09LiveMask != prefsR09Mask
                ) {
                    json.put(KEY_DISABLE_STREAMER_MODE, (liveMask and LOCK_DISABLE_STREAMER_MODE) != 0)
                    json.put(KEY_LOCK_MASK, liveMask)
                    json.put(KEY_DOHNA_LOCK_MASK, dohnaLiveMask)
                    json.put(KEY_EVENICLE_LOCK_MASK, evenicleLiveMask)
                    json.put(KEY_SR_LOCK_MASK, srLiveMask)
                    json.put(KEY_R01_LOCK_MASK, r01LiveMask)
                    json.put(KEY_R02_LOCK_MASK, r02LiveMask)
                    json.put(KEY_R03_LOCK_MASK, r03LiveMask)
                    json.put(KEY_R08_LOCK_MASK, r08LiveMask)
                    json.put(KEY_R09_LOCK_MASK, r09LiveMask)
                    writeConfigJson(context, json)
                }
                json.optInt(KEY_CONFIG_SEQ, 0)
            }
        } catch (_: Throwable) {
            getConfigSeq(context)
        }
    }

    fun isFloatingEnabled(context: Context): Boolean {
        return readConfigJson(context).optBoolean(KEY_FLOATING_ENABLED, true)
    }

    fun setFloatingEnabled(context: Context, enabled: Boolean) {
        synchronized(configLock) {
            val json = readConfigJson(context)
            json.put(KEY_FLOATING_ENABLED, enabled)
            writeConfigJson(context, json)
        }
    }

    fun isCtrlOverlayEnabled(context: Context): Boolean {
        return readConfigJson(context).optBoolean(KEY_CTRL_OVERLAY_ENABLED, false)
    }

    fun setCtrlOverlayEnabled(context: Context, enabled: Boolean) {
        synchronized(configLock) {
            val json = readConfigJson(context)
            json.put(KEY_CTRL_OVERLAY_ENABLED, enabled)
            json.put(KEY_CTRL_OVERLAY_VISIBLE, enabled)
            writeConfigJson(context, json)
        }
    }

    fun isCtrlOverlayVisible(context: Context): Boolean {
        synchronized(configLock) {
            val json = readConfigJson(context)
            return json.optBoolean(KEY_CTRL_OVERLAY_ENABLED, false) &&
                json.optBoolean(KEY_CTRL_OVERLAY_VISIBLE, false)
        }
    }

    fun setCtrlOverlayVisible(context: Context, visible: Boolean) {
        synchronized(configLock) {
            val json = readConfigJson(context)
            val enabled = json.optBoolean(KEY_CTRL_OVERLAY_ENABLED, false)
            json.put(KEY_CTRL_OVERLAY_VISIBLE, enabled && visible)
            writeConfigJson(context, json)
        }
    }

    fun getSavedLockMask(context: Context): Int {
        return readConfigJson(context).optInt(KEY_LOCK_MASK, 0)
    }

    fun getLiveLockCount(context: Context): Int {
        return Integer.bitCount(getSavedLockMask(context) and LOCK_DISABLE_STREAMER_MODE.inv())
    }

    fun setSavedLockMask(context: Context, mask: Int) {
        synchronized(configLock) {
            val json = readConfigJson(context)
            json.put(KEY_DISABLE_STREAMER_MODE, (mask and LOCK_DISABLE_STREAMER_MODE) != 0)
            json.put(KEY_LOCK_MASK, mask)
            writeConfigJson(context, json)
        }
        if (loaded) {
            try {
                nativeSetConfigPath(getConfigFilePath(context))
                nativeSetLiveLocks(mask)
            } catch (_: Throwable) {
            }
        }
    }

    fun setLiveLocksOnlyMask(context: Context, liveMask: Int) {
        val merged = synchronized(configLock) {
            val keepStreamer = isDisableStreamerModeEnabled(context)
            if (keepStreamer) {
                (liveMask and LOCK_DISABLE_STREAMER_MODE.inv()) or LOCK_DISABLE_STREAMER_MODE
            } else {
                liveMask and LOCK_DISABLE_STREAMER_MODE.inv()
            }
        }
        setSavedLockMask(context, merged)
    }

    /**
     * Returns the Rance 10 field updates required for 100% treasure drops without mutating
     * combat stats such as `itemStar` or `partyIsFixStatus`.
     */
    fun treasure100ActionParams(): Map<String, Int> = linkedMapOf(
        "perFindTreasure" to 999,
        "treasureNotGetCount" to 99,
        "rareLuck" to 999,
    )

    /**
     * Builds the Rance 10 action / save-mod JSON for 100% treasure drops from
     * [treasure100ActionParams].
     */
    fun createTreasure100ActionJson(): JSONObject = JSONObject().apply {
        for ((key, value) in treasure100ActionParams()) {
            put(key, value)
        }
    }

    /**
     * Computes the updated Rance 10 bitmask when enabling a live lock bit, preserving all
     * existing live lock bits and [LOCK_DISABLE_STREAMER_MODE].
     */
    fun computeEnabledLiveLockMask(
        currentMask: Int,
        disableStreamerMode: Boolean,
        lockBit: Int,
    ): Int {
        val liveBits = (currentMask or lockBit) and LOCK_DISABLE_STREAMER_MODE.inv()
        val keepStreamer = disableStreamerMode || (currentMask and LOCK_DISABLE_STREAMER_MODE) != 0
        return if (keepStreamer) (liveBits or LOCK_DISABLE_STREAMER_MODE) else liveBits
    }

    /**
     * Persistently enables a Rance 10 live lock bit while preserving existing live locks and
     * the streamer-mode flag, pushing the merged mask to native when loaded.
     */
    fun enableLiveLockBit(context: Context, lockBit: Int): Int {
        val merged = synchronized(configLock) {
            computeEnabledLiveLockMask(
                getSavedLockMask(context),
                isDisableStreamerModeEnabled(context),
                lockBit,
            )
        }
        setSavedLockMask(context, merged)
        return merged
    }

    fun isDisableStreamerModeEnabled(context: Context): Boolean {
        return readConfigJson(context).optBoolean(KEY_DISABLE_STREAMER_MODE, false)
    }

    fun setDisableStreamerModeEnabled(context: Context, enabled: Boolean) {
        val next = synchronized(configLock) {
            val cur = getSavedLockMask(context)
            if (enabled) (cur or LOCK_DISABLE_STREAMER_MODE) else (cur and LOCK_DISABLE_STREAMER_MODE.inv())
        }
        setSavedLockMask(context, next)
    }

    fun getSavedDohnaLockMask(context: Context): Int {
        return readConfigJson(context).optInt(KEY_DOHNA_LOCK_MASK, 0) and LOCK_DD_ALL_MASK
    }

    fun getDohnaLiveLockCount(context: Context): Int {
        return Integer.bitCount(getSavedDohnaLockMask(context))
    }

    fun setSavedDohnaLockMask(context: Context, mask: Int) {
        val clean = mask and LOCK_DD_ALL_MASK
        synchronized(configLock) {
            val json = readConfigJson(context)
            json.put(KEY_DOHNA_LOCK_MASK, clean)
            writeConfigJson(context, json)
        }
        if (loaded) {
            try {
                nativeSetConfigPath(getConfigFilePath(context))
                setDohnaLiveLocks(clean)
            } catch (_: Throwable) {
            }
        }
    }

    fun setDohnaLiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetDohnaLiveLocks(mask and LOCK_DD_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getDohnaLiveLocks(): Int {
        if (!loaded) return 0
        return try {
            nativeGetDohnaLiveLocks() and LOCK_DD_ALL_MASK
        } catch (_: Throwable) {
            0
        }
    }

    fun getSavedEvenicleLockMask(context: Context): Int {
        return readConfigJson(context).optInt(KEY_EVENICLE_LOCK_MASK, 0) and LOCK_EV_ALL_MASK
    }

    fun getEvenicleLiveLockCount(context: Context): Int {
        return Integer.bitCount(getSavedEvenicleLockMask(context))
    }

    fun setSavedEvenicleLockMask(context: Context, mask: Int) {
        val clean = mask and LOCK_EV_ALL_MASK
        synchronized(configLock) {
            val json = readConfigJson(context)
            json.put(KEY_EVENICLE_LOCK_MASK, clean)
            writeConfigJson(context, json)
        }
        if (loaded) {
            try {
                nativeSetConfigPath(getConfigFilePath(context))
                setEvenicleLiveLocks(clean)
            } catch (_: Throwable) {
            }
        }
    }

    fun setEvenicleLiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetEvenicleLiveLocks(mask and LOCK_EV_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getEvenicleLiveLocks(): Int {
        if (!loaded) return 0
        return try {
            nativeGetEvenicleLiveLocks() and LOCK_EV_ALL_MASK
        } catch (_: Throwable) {
            0
        }
    }

    private fun getSavedMaskedLock(context: Context, key: String, allMask: Int): Int {
        return readConfigJson(context).optInt(key, 0) and allMask
    }

    private fun setSavedMaskedLock(context: Context, key: String, allMask: Int, mask: Int, pushNative: (Int) -> Unit) {
        val clean = mask and allMask
        synchronized(configLock) {
            val json = readConfigJson(context)
            json.put(key, clean)
            writeConfigJson(context, json)
        }
        if (loaded) {
            try {
                nativeSetConfigPath(getConfigFilePath(context))
                pushNative(clean)
            } catch (_: Throwable) {
            }
        }
    }

    private fun safeGetNativeMask(allMask: Int, reader: () -> Int): Int {
        if (!loaded) return 0
        return try {
            reader() and allMask
        } catch (_: Throwable) {
            0
        }
    }

    fun getSavedSrLockMask(context: Context): Int = getSavedMaskedLock(context, KEY_SR_LOCK_MASK, LOCK_SR_ALL_MASK)
    fun getSrLiveLockCount(context: Context): Int = Integer.bitCount(getSavedSrLockMask(context))
    fun setSavedSrLockMask(context: Context, mask: Int) =
        setSavedMaskedLock(context, KEY_SR_LOCK_MASK, LOCK_SR_ALL_MASK, mask, ::setSrLiveLocks)

    fun setSrLiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetSrLiveLocks(mask and LOCK_SR_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getSrLiveLocks(): Int = safeGetNativeMask(LOCK_SR_ALL_MASK) { nativeGetSrLiveLocks() }

    fun getSavedR01LockMask(context: Context): Int = getSavedMaskedLock(context, KEY_R01_LOCK_MASK, LOCK_R01_ALL_MASK)
    fun getR01LiveLockCount(context: Context): Int = Integer.bitCount(getSavedR01LockMask(context))
    fun setSavedR01LockMask(context: Context, mask: Int) =
        setSavedMaskedLock(context, KEY_R01_LOCK_MASK, LOCK_R01_ALL_MASK, mask, ::setR01LiveLocks)

    fun setR01LiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetR01LiveLocks(mask and LOCK_R01_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getR01LiveLocks(): Int {
        if (!loaded) return 0
        return try {
            nativeGetR01LiveLocks() and LOCK_R01_ALL_MASK
        } catch (_: Throwable) {
            0
        }
    }

    fun getSavedR02LockMask(context: Context): Int = getSavedMaskedLock(context, KEY_R02_LOCK_MASK, LOCK_R02_ALL_MASK)
    fun getR02LiveLockCount(context: Context): Int = Integer.bitCount(getSavedR02LockMask(context))
    fun setSavedR02LockMask(context: Context, mask: Int) =
        setSavedMaskedLock(context, KEY_R02_LOCK_MASK, LOCK_R02_ALL_MASK, mask, ::setR02LiveLocks)

    fun setR02LiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetR02LiveLocks(mask and LOCK_R02_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getR02LiveLocks(): Int {
        if (!loaded) return 0
        return try {
            nativeGetR02LiveLocks() and LOCK_R02_ALL_MASK
        } catch (_: Throwable) {
            0
        }
    }

    fun getSavedR03LockMask(context: Context): Int = getSavedMaskedLock(context, KEY_R03_LOCK_MASK, LOCK_R03_ALL_MASK)
    fun getR03LiveLockCount(context: Context): Int = Integer.bitCount(getSavedR03LockMask(context))
    fun setSavedR03LockMask(context: Context, mask: Int) =
        setSavedMaskedLock(context, KEY_R03_LOCK_MASK, LOCK_R03_ALL_MASK, mask, ::setR03LiveLocks)

    fun setR03LiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetR03LiveLocks(mask and LOCK_R03_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getR03LiveLocks(): Int = safeGetNativeMask(LOCK_R03_ALL_MASK) { nativeGetR03LiveLocks() }

    fun getSavedR08LockMask(context: Context): Int = getSavedMaskedLock(context, KEY_R08_LOCK_MASK, LOCK_R08_ALL_MASK)
    fun getR08LiveLockCount(context: Context): Int = Integer.bitCount(getSavedR08LockMask(context))
    fun setSavedR08LockMask(context: Context, mask: Int) =
        setSavedMaskedLock(context, KEY_R08_LOCK_MASK, LOCK_R08_ALL_MASK, mask, ::setR08LiveLocks)

    fun setR08LiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetR08LiveLocks(mask and LOCK_R08_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getR08LiveLocks(): Int = safeGetNativeMask(LOCK_R08_ALL_MASK) { nativeGetR08LiveLocks() }

    fun getSavedR09LockMask(context: Context): Int = getSavedMaskedLock(context, KEY_R09_LOCK_MASK, LOCK_R09_ALL_MASK)
    fun getR09LiveLockCount(context: Context): Int = Integer.bitCount(getSavedR09LockMask(context))
    fun setSavedR09LockMask(context: Context, mask: Int) =
        setSavedMaskedLock(context, KEY_R09_LOCK_MASK, LOCK_R09_ALL_MASK, mask, ::setR09LiveLocks)

    fun setR09LiveLocks(mask: Int) {
        if (!loaded) return
        try {
            nativeSetR09LiveLocks(mask and LOCK_R09_ALL_MASK)
        } catch (_: Throwable) {
        }
    }

    fun getR09LiveLocks(): Int = safeGetNativeMask(LOCK_R09_ALL_MASK) { nativeGetR09LiveLocks() }

    fun getSavedEscLockMask(context: Context): Int = getSavedMaskedLock(context, KEY_ESC_LOCK_MASK, LOCK_ESC_ALL_MASK)
    fun getEscLiveLockCount(context: Context): Int = Integer.bitCount(getSavedEscLockMask(context))
    fun setSavedEscLockMask(context: Context, mask: Int) =
        setSavedMaskedLock(context, KEY_ESC_LOCK_MASK, LOCK_ESC_ALL_MASK, mask) { clean ->
            if (loaded) {
                try {
                    nativeSetLiveLocks(clean)
                } catch (_: Throwable) {
                }
            }
        }

    private fun parseExtraLocksMap(raw: String?): JSONObject {
        if (raw.isNullOrBlank()) return JSONObject()
        return try {
            val parsed = JSONObject(raw)
            val clean = JSONObject()
            val it = parsed.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = parsed.optInt(k, 0)
                if (v != 0) clean.put(k, v)
            }
            clean
        } catch (_: Throwable) {
            JSONObject()
        }
    }

    private fun normalizeGenericLockKey(gameId: String): String =
        if (gameId == GameCatalog.ID_OKUSAMANTR) GameCatalog.ID_OKUSAMAJUN else gameId

    fun getSavedGenericLockMask(context: Context, gameId: String): Int {
        val spec = GenericModifierRegistry.find(gameId)
        val key = normalizeGenericLockKey(gameId)
        val map = readConfigJson(context).optJSONObject(KEY_EXTRA_LOCKS) ?: return 0
        val raw = map.optInt(key, map.optInt(gameId, 0))
        return if (spec != null) (raw and spec.allLocksMask) else raw
    }

    fun getGenericLiveLockCount(context: Context, gameId: String): Int =
        Integer.bitCount(getSavedGenericLockMask(context, gameId))

    fun setSavedGenericLockMask(context: Context, gameId: String, mask: Int, isSystem35: Boolean = false) {
        val spec = GenericModifierRegistry.find(gameId)
        val clean = if (spec != null) (mask and spec.allLocksMask) else mask
        val key = normalizeGenericLockKey(gameId)
        synchronized(configLock) {
            val json = readConfigJson(context)
            val map = json.optJSONObject(KEY_EXTRA_LOCKS) ?: JSONObject()
            if (clean == 0) {
                map.remove(key)
                map.remove(gameId)
            } else {
                map.put(key, clean)
            }
            json.put(KEY_EXTRA_LOCKS, map)
            writeConfigJson(context, json)
        }
        if (loaded) {
            try {
                if (isSystem35) {
                    nativeSetLiveLocks(clean)
                } else {
                    nativeSetConfigPath(getConfigFilePath(context))
                    nativeSetGameLiveLocks(key, clean)
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun setGenericLiveLocks(gameId: String, mask: Int) {
        if (!loaded) return
        val spec = GenericModifierRegistry.find(gameId)
        val clean = if (spec != null) (mask and spec.allLocksMask) else mask
        try {
            nativeSetGameLiveLocks(normalizeGenericLockKey(gameId), clean)
        } catch (_: Throwable) {
        }
    }

    fun syncEscLockMaskFromNative(context: Context): Int {
        if (!loaded) return getConfigSeq(context)
        return try {
            val escLive = nativeGetLiveLocks() and LOCK_ESC_ALL_MASK
            synchronized(configLock) {
                val json = readConfigJson(context)
                val savedEsc = json.optInt(KEY_ESC_LOCK_MASK, 0) and LOCK_ESC_ALL_MASK
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val prefsEsc = prefs.getInt(KEY_ESC_LOCK_MASK, -1)
                if (escLive != savedEsc || escLive != prefsEsc) {
                    json.put(KEY_ESC_LOCK_MASK, escLive)
                    writeConfigJson(context, json)
                }
                json.optInt(KEY_CONFIG_SEQ, 0)
            }
        } catch (_: Throwable) {
            getConfigSeq(context)
        }
    }

    fun readStreamerModeForDir(saveDir: File): Int {
        val f1 = File(saveDir, "GameConfig.asd")
        val f2 = File(File(saveDir, "SaveData"), "GameConfig.asd")
        val target = if (f1.isFile) f1 else if (f2.isFile) f2 else return -1
        if (!ensureLoaded()) return -1
        return try {
            nativeReadStreamerMode(target.absolutePath)
        } catch (_: Throwable) {
            -1
        }
    }

    fun writeStreamerModeForDir(saveDir: File, streamerModeEnabled: Boolean): Boolean {
        val f1 = File(saveDir, "GameConfig.asd")
        val f2 = File(File(saveDir, "SaveData"), "GameConfig.asd")
        if (!f1.isFile && !f2.isFile) return false
        if (!ensureLoaded()) return false
        var anyOk = false
        try {
            if (f1.isFile && nativeWriteStreamerMode(f1.absolutePath, streamerModeEnabled)) {
                anyOk = true
            }
            if (f2.isFile && nativeWriteStreamerMode(f2.absolutePath, streamerModeEnabled)) {
                anyOk = true
            }
        } catch (_: Throwable) {
        }
        return anyOk
    }

    fun installFullUnlockSaves(context: Context, saveDir: File): Int {
        saveDir.mkdirs()
        val subSaveData = File(saveDir, "SaveData")
        var count = 0
        context.resources.openRawResource(R.raw.full_unlock_saves).use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val fileName = File(entry.name).name
                    if (fileName.isEmpty()) continue
                    val bytes = zip.readBytes()

                    val targetFile = File(saveDir, fileName)
                    val bakFile = File(saveDir, fileName + UNLOCK_BAK_SUFFIX)
                    if (targetFile.isFile && !bakFile.exists()) {
                        targetFile.copyTo(bakFile, overwrite = true)
                    }
                    targetFile.writeBytes(bytes)

                    if (subSaveData.isDirectory) {
                        val subTarget = File(subSaveData, fileName)
                        val subBak = File(subSaveData, fileName + UNLOCK_BAK_SUFFIX)
                        if (subTarget.isFile && !subBak.exists()) {
                            subTarget.copyTo(subBak, overwrite = true)
                        }
                        subTarget.writeBytes(bytes)
                    }
                    count++
                }
            }
        }
        return count
    }

    fun hasFullUnlockBackup(saveDir: File): Boolean {
        val names = listOf("Collection.asd", "GlobalSave.asd", "AFCGMode.asd", "AFBGMMode.asd")
        val subSaveData = File(saveDir, "SaveData")
        return names.any {
            File(saveDir, it + UNLOCK_BAK_SUFFIX).isFile ||
                File(subSaveData, it + UNLOCK_BAK_SUFFIX).isFile
        }
    }

    fun restoreFullUnlockBackup(saveDir: File): Int {
        val names = listOf("Collection.asd", "GlobalSave.asd", "AFCGMode.asd", "AFBGMMode.asd")
        var restored = 0
        val subSaveData = File(saveDir, "SaveData")
        for (name in names) {
            val bak = File(saveDir, name + UNLOCK_BAK_SUFFIX)
            val dst = File(saveDir, name)
            if (bak.isFile) {
                bak.copyTo(dst, overwrite = true)
                bak.delete()
                restored++
            }
            if (subSaveData.isDirectory) {
                val subBak = File(subSaveData, name + UNLOCK_BAK_SUFFIX)
                val subDst = File(subSaveData, name)
                if (subBak.isFile) {
                    subBak.copyTo(subDst, overwrite = true)
                    subBak.delete()
                }
            }
        }
        return restored
    }

    @JvmOverloads
    fun backupSaveFileBeforeMod(file: File, recordAbsent: Boolean = false): File {
        val bak = File(file.parentFile, file.name + MOD_BAK_SUFFIX)
        val absent = File(file.parentFile, file.name + MOD_ABSENT_SUFFIX)
        if (file.isFile) {
            absent.delete()
            file.copyTo(bak, overwrite = true)
        } else if (recordAbsent && !bak.isFile) {
            file.parentFile?.mkdirs()
            absent.writeText("", Charsets.UTF_8)
        }
        return bak
    }

    fun hasModBackup(file: File): Boolean {
        if (File(file.parentFile, file.name + MOD_BAK_SUFFIX).isFile) return true
        return File(file.parentFile, file.name + MOD_ABSENT_SUFFIX).isFile && file.isFile
    }

    fun restoreModBackup(file: File): Boolean {
        val bak = File(file.parentFile, file.name + MOD_BAK_SUFFIX)
        val absent = File(file.parentFile, file.name + MOD_ABSENT_SUFFIX)
        if (bak.isFile) {
            bak.copyTo(file, overwrite = true)
            absent.delete()
            return true
        }
        if (absent.isFile) {
            val deleted = !file.exists() || file.delete()
            absent.delete()
            return deleted
        }
        return false
    }

    private fun resolveActiveGlobalSaveDir(saveDir: File): File {
        val sub = File(saveDir, "SaveData")
        if (File(saveDir, "GlobalSave.asd").isFile ||
            File(saveDir, "Collection.asd").isFile ||
            File(saveDir, "CommonSystemData.sav").isFile
        ) {
            return saveDir
        }
        if (File(sub, "GlobalSave.asd").isFile ||
            File(sub, "Collection.asd").isFile ||
            File(sub, "CommonSystemData.sav").isFile
        ) {
            return sub
        }
        return saveDir
    }

    fun hasGlobalSaveFiles(saveDir: File): Boolean {
        val sub = File(saveDir, "SaveData")
        return File(saveDir, "GlobalSave.asd").isFile ||
            File(saveDir, "Collection.asd").isFile ||
            File(saveDir, "CommonSystemData.sav").isFile ||
            File(sub, "GlobalSave.asd").isFile ||
            File(sub, "Collection.asd").isFile ||
            File(sub, "CommonSystemData.sav").isFile
    }

    fun isGlobalSaveModFieldAvailable(saveDir: File, key: String): Boolean {
        val name = when (key) {
            "gamePlayCount", "itemStarRank", "setAllCharRank", "setTakeOverCardCount" -> "GlobalSave.asd"
            "leaderAtkRatio", "trophyExpBonus", "trophyStatusMaxPer" -> "Collection.asd"
            else -> return hasGlobalSaveFiles(saveDir)
        }
        return File(saveDir, name).isFile || File(File(saveDir, "SaveData"), name).isFile
    }

    fun parseGlobalSaveFlagEdit(input: String, initial: String): Int? {
        val value = input.toIntOrNull()
        if (value == 0 || value == 1) return value
        if (value != null && value == initial.toIntOrNull()) return null
        throw IllegalArgumentException()
    }

    fun backupGlobalSaveFilesBeforeMod(saveDir: File) {
        val activeDir = resolveActiveGlobalSaveDir(saveDir)
        val sub = File(saveDir, "SaveData")
        for (dir in listOf(saveDir, sub)) {
            if (!dir.isDirectory && dir != activeDir) continue
            val g = File(dir, "GlobalSave.asd")
            val c = File(dir, "Collection.asd")
            val s = File(dir, "CommonSystemData.sav")
            if (g.isFile) backupSaveFileBeforeMod(g)
            if (c.isFile) backupSaveFileBeforeMod(c)
            if (s.isFile || dir == activeDir) {
                backupSaveFileBeforeMod(s, recordAbsent = true)
            }
        }
    }

    fun hasGlobalSaveModBackup(saveDir: File): Boolean {
        val sub = File(saveDir, "SaveData")
        val names = listOf("GlobalSave.asd", "Collection.asd", "CommonSystemData.sav")
        return names.any { name ->
            hasModBackup(File(saveDir, name)) ||
                (sub.isDirectory && hasModBackup(File(sub, name)))
        }
    }

    fun restoreGlobalSaveModBackup(saveDir: File): Boolean {
        val sub = File(saveDir, "SaveData")
        val names = listOf("GlobalSave.asd", "Collection.asd", "CommonSystemData.sav")
        var anyOk = false
        for (name in names) {
            if (restoreModBackup(File(saveDir, name))) anyOk = true
            if (sub.isDirectory && restoreModBackup(File(sub, name))) anyOk = true
        }
        return anyOk
    }

    fun readGlobalSaveSummaryForDir(saveDir: File): String {
        val sub = File(saveDir, "SaveData")
        val g1 = File(saveDir, "GlobalSave.asd")
        val g2 = File(sub, "GlobalSave.asd")
        val c1 = File(saveDir, "Collection.asd")
        val c2 = File(sub, "Collection.asd")
        val s1 = File(saveDir, "CommonSystemData.sav")
        val s2 = File(sub, "CommonSystemData.sav")
        var gp = if (g1.isFile) g1.absolutePath else if (g2.isFile) g2.absolutePath else ""
        val cp = if (c1.isFile) c1.absolutePath else if (c2.isFile) c2.absolutePath else ""
        if (gp.isEmpty() && cp.isEmpty()) {
            if (s1.isFile) {
                gp = g1.absolutePath
            } else if (s2.isFile) {
                gp = g2.absolutePath
            }
        }
        return nativeReadGlobalSaveSummary(gp, cp)
    }

    fun writeGlobalSaveModForDir(saveDir: File, modSpec: String): Boolean {
        val sub = File(saveDir, "SaveData")
        val g1 = File(saveDir, "GlobalSave.asd")
        val g2 = File(sub, "GlobalSave.asd")
        val c1 = File(saveDir, "Collection.asd")
        val c2 = File(sub, "Collection.asd")
        val s1 = File(saveDir, "CommonSystemData.sav")
        val s2 = File(sub, "CommonSystemData.sav")
        var gp = if (g1.isFile) g1.absolutePath else if (g2.isFile) g2.absolutePath else ""
        val cp = if (c1.isFile) c1.absolutePath else if (c2.isFile) c2.absolutePath else ""
        if (gp.isEmpty() && cp.isEmpty()) {
            if (s1.isFile) {
                gp = g1.absolutePath
            } else if (s2.isFile) {
                gp = g2.absolutePath
            } else {
                return false
            }
        }
        return nativeWriteGlobalSaveMod(gp, cp, modSpec)
    }

    @JvmStatic
    external fun nativeReadLocalSaveSummary(path: String): String

    @JvmStatic
    external fun nativeWriteLocalSaveMod(path: String, modSpec: String): Boolean

    @JvmStatic
    external fun nativeReadGlobalSaveSummary(globalPath: String, collectionPath: String): String

    @JvmStatic
    external fun nativeWriteGlobalSaveMod(globalPath: String, collectionPath: String, modSpec: String): Boolean

    @JvmStatic
    external fun nativeGetLiveStateJson(): String

    @JvmStatic
    external fun nativeSetLiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetLiveLocks(): Int

    @JvmStatic
    external fun nativeSetDohnaLiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetDohnaLiveLocks(): Int

    @JvmStatic
    external fun nativeSetEvenicleLiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetEvenicleLiveLocks(): Int

    @JvmStatic
    external fun nativeSetSrLiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetSrLiveLocks(): Int

    @JvmStatic
    external fun nativeSetR01LiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetR01LiveLocks(): Int

    @JvmStatic
    external fun nativeSetR02LiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetR02LiveLocks(): Int

    @JvmStatic
    external fun nativeSetR03LiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetR03LiveLocks(): Int

    @JvmStatic
    external fun nativeSetR08LiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetR08LiveLocks(): Int

    @JvmStatic
    external fun nativeSetR09LiveLocks(mask: Int)

    @JvmStatic
    external fun nativeGetR09LiveLocks(): Int

    @JvmStatic
    external fun nativeGetCurrentGame(): String

    @JvmStatic
    external fun nativeSetGameLiveLocks(gameId: String, mask: Int)

    @JvmStatic
    external fun nativeGetGameLiveLocks(gameId: String): Int

    @JvmStatic
    external fun nativeApplyLiveAction(actionSpec: String)

    @JvmStatic
    external fun nativeReadStreamerMode(configPath: String): Int

    @JvmStatic
    external fun nativeWriteStreamerMode(configPath: String, enabled: Boolean): Boolean

    @JvmStatic
    external fun nativeSetConfigPath(path: String)

    @JvmStatic
    external fun nativeSetCtrlSkip(enabled: Boolean)

    @JvmStatic
    external fun nativeGetCtrlSkip(): Boolean

    @JvmStatic
    external fun nativeSetUiScaleGameKey(gameKey: String?)

    @JvmStatic
    external fun nativeSetUiScale(fontPct: Int, scrollbarPct: Int)

    @JvmStatic
    external fun nativeGetFontScale(): Int

    @JvmStatic
    external fun nativeGetScrollbarScale(): Int

    @JvmStatic
    external fun nativeExportPcSaveFile(srcPath: String, dstPath: String): Boolean

    fun isX4ssSaveFile(file: File): Boolean {
        if (!file.isFile || file.length() < 8L) return false
        return try {
            file.inputStream().use { input ->
                val hdr = ByteArray(8)
                if (input.read(hdr) != 8) return@use false
                hdr[0] == 'X'.code.toByte() &&
                    hdr[1] == '4'.code.toByte() &&
                    hdr[2] == 'S'.code.toByte() &&
                    hdr[3] == 'S'.code.toByte() &&
                    hdr[4].toInt() == 1 &&
                    hdr[5].toInt() == 0 &&
                    hdr[6].toInt() == 0 &&
                    hdr[7].toInt() == 0
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Streams [srcFile] to [dstStream], converting X4SS StructSave files to Windows PC
     * GD\x01\x01 serialize_struct format when needed. Returns true when converted.
     */
    fun copySaveFileForPc(srcFile: File, dstStream: java.io.OutputStream, scratchDir: File): Boolean {
        if (!isX4ssSaveFile(srcFile)) {
            srcFile.inputStream().buffered().use { input ->
                input.copyTo(dstStream)
            }
            return false
        }
        if (!ensureLoaded()) {
            throw java.io.IOException("Failed to load native library for PC save conversion")
        }
        scratchDir.mkdirs()
        val tmpOut = File.createTempFile("pc_conv_", ".bin", scratchDir)
        try {
            if (!nativeExportPcSaveFile(srcFile.absolutePath, tmpOut.absolutePath)) {
                throw java.io.IOException("Failed to convert ${srcFile.name} to PC format")
            }
            tmpOut.inputStream().buffered().use { input ->
                input.copyTo(dstStream)
            }
            return true
        } finally {
            tmpOut.delete()
        }
    }
}
