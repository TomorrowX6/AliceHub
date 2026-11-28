package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import java.io.File
import java.util.Locale

object AppSettings {
    private const val PREFS_NAME = "alicehub_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_PALETTE = "color_palette"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_GAME_SORT = "game_sort"
    private const val KEY_ONLINE_INSTALL = "online_install_enabled"
    private const val KEY_LEGACY_DOWNLOAD_ROUTE = "download_route"
    private const val KEY_CHECK_FOR_UPDATES = "check_for_updates"
    private const val KEY_DOWNLOAD_CONCURRENCY = "download_concurrency"
    private const val KEY_BASE_SIZE_PREFIX = "base_size_"
    private const val LANG_FILE_NAME = ".app_lang"
    private const val THEME_FILE_NAME = ".app_theme"
    private const val PALETTE_FILE_NAME = ".app_palette"

    const val THEME_AUTO = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val PALETTE_MONET = "monet"
    const val PALETTE_INDIGO = "indigo"
    const val PALETTE_SAKURA = "sakura"
    const val PALETTE_OCEAN = "ocean"
    const val PALETTE_FOREST = "forest"
    const val PALETTE_AMBER = "amber"
    const val PALETTE_VIOLET = "violet"

    const val LANG_AUTO = ""
    const val LANG_ZH = "zh"
    const val LANG_JA = "ja"
    const val LANG_EN = "en"

    const val MIN_DOWNLOAD_CONCURRENCY = 1
    const val MAX_DOWNLOAD_CONCURRENCY = 16
    const val DEFAULT_DOWNLOAD_CONCURRENCY = 8
    private const val CDN_ORIGIN_PREFIX = "https://alicehub.000.moe/"
    private const val CDN_CN_PREFIX = "https://alicehub.000.moe/cn/"

    private const val DEFAULT_RANCE10_BYTES = 4028714690L
    private const val DEFAULT_DOHNA_BYTES = 3310231215L
    private const val DEFAULT_EVENICLE_BYTES = 2453622110L
    private const val DEFAULT_EVENICLE2_BYTES = 3399191886L
    private const val DEFAULT_SENGOKU_RANCE_BYTES = 1885221320L
    private const val DEFAULT_RANCE01_BYTES = 611603977L
    private const val DEFAULT_RANCE02_BYTES = 273186363L
    private const val DEFAULT_RANCE03_BYTES = 2223244774L
    internal const val DEFAULT_RANCE04_BYTES = 113194039L
    internal const val DEFAULT_RANCE41_BYTES = 76386618L
    internal const val DEFAULT_RANCE42_BYTES = 179080854L
    internal const val DEFAULT_RANCE5D_BYTES = 403478563L
    internal const val DEFAULT_RANCE06_BYTES = 2260544882L
    private const val DEFAULT_RANCE08_BYTES = 1964252126L
    private const val DEFAULT_RANCE09_BYTES = 2738534355L
    private const val DEFAULT_KICHIKUOU_BYTES = 198596468L
    private const val DEFAULT_DAIAKUJI_BYTES = 923623502L
    internal const val DEFAULT_DAIBANCHOU_BYTES = 1895126876L
    internal const val DEFAULT_DAITEIKOKU_BYTES = 2416202457L
    private const val DEFAULT_ESCALAYER_BYTES = 1563994084L
    private const val DEFAULT_ESCALAYER_REBOOT_BYTES = 2470174002L
    internal const val DEFAULT_GALZOO_BYTES = 4027583522L
    internal const val DEFAULT_IXSEAL_BYTES = 4729863037L
    internal const val DEFAULT_HARUKA_BYTES = 1037465188L
    internal const val DEFAULT_TSUMASHIBORI_BYTES = 2877493822L
    internal const val DEFAULT_TSUMAMIGUI_BYTES = 1400592703L
    internal const val DEFAULT_TSUMAMIGUI3_BYTES = 2403975825L
    internal const val DEFAULT_KAERUNYO_BYTES = 105838567L
    internal const val DEFAULT_ATLACHNACHA_BYTES = 148510731L
    internal const val DEFAULT_REISHIKI_BYTES = 88875020L
    internal const val DEFAULT_HARUURARE_BYTES = 264968018L
    internal const val DEFAULT_OKUSAMAJUN_BYTES = 433754405L
    internal const val DEFAULT_OKUSAMANTR_BYTES = 407324160L
    internal const val DEFAULT_EVENICLE2RANCE_BYTES = 675181447L
    internal const val DEFAULT_EVENICLEGAIDEN_BYTES = 344150541L
    internal const val DEFAULT_WAIDONYO_BYTES = 554965092L
    internal const val DEFAULT_RANCE6AFTER_BYTES = 38024313L
    internal const val DEFAULT_YAKATA2_BYTES = 14072286L
    internal const val DEFAULT_SDPSMARIA_BYTES = 11185724L
    internal const val DEFAULT_TOUSHIN3_BYTES = 3406621760L
    internal const val DEFAULT_OYAKORANKAN_BYTES = 2961641642L
    internal const val DEFAULT_YORUGAKURU_BYTES = 1565514386L
    internal const val DEFAULT_RANCE1DIGEST_BYTES = 11945316L
    internal const val DEFAULT_MAMAN_BYTES = 1562216703L
    internal const val DEFAULT_EVENICLERANCE_BYTES = 397398161L
    internal const val DEFAULT_HAHARANMAN_BYTES = 1231870701L
    internal const val DEFAULT_TSUMAMIGUI2_BYTES = 760017305L
    internal const val DEFAULT_TSUMAMIGUI3JUN_BYTES = 215949267L
    internal const val DEFAULT_TSUMAMIGUI3NTR_BYTES = 244591288L
    internal const val DEFAULT_DIABOLIQUE_BYTES = 212543299L
    internal const val DEFAULT_GAKUENKING_BYTES = 83874519L
    internal const val DEFAULT_GAKUENKINGEX_BYTES = 6975808L
    internal const val DEFAULT_OUDOUYUUSHA_BYTES = 162434751L
    internal const val DEFAULT_TOUSHIN1_BYTES = 6276307L
    internal const val DEFAULT_TOUSHIN2_BYTES = 168804761L
    internal const val DEFAULT_DARCROWS_BYTES = 558857840L
    internal const val DEFAULT_IKUSAMIKO_BYTES = 381340553L
    internal const val DEFAULT_ONLYYOU_BYTES = 77592471L
    internal const val DEFAULT_SANBIKI_BYTES = 160062187L
    internal const val DEFAULT_PERSIOM_BYTES = 287307616L
    internal const val DEFAULT_SEEINAO_BYTES = 197954141L
    internal const val DEFAULT_HUSHABY_BYTES = 32337216L
    internal const val DEFAULT_AYUMI_BYTES = 89331318L
    internal const val DEFAULT_CIELCLAIRE_BYTES = 772056801L
    internal const val DEFAULT_NIGHTDEMON_BYTES = 761609079L
    internal const val DEFAULT_KATSUMI_BYTES = 127134186L
    internal const val DEFAULT_DRSTOP_BYTES = 2372912L
    internal const val DEFAULT_DPSALL_BYTES = 48806657L
    internal const val DEFAULT_KOREDPS_BYTES = 34941173L
    internal const val DEFAULT_KAERUALICE_BYTES = 114809179L
    internal const val DEFAULT_PROSTUDENTG_BYTES = 74947167L
    internal const val DEFAULT_PROSTUDENTGOOD_BYTES = 144813947L
    internal const val DEFAULT_PASTELCHIME_BYTES = 128679015L
    internal const val DEFAULT_PASTELCHIME2_BYTES = 2725818511L
    internal const val DEFAULT_YAKATA3_BYTES = 14582865L
    internal const val DEFAULT_DALK_BYTES = 13360976L
    internal const val DEFAULT_NINGENGARI_BYTES = 17006400L
    internal const val DEFAULT_PASCHACPP_BYTES = 1391660611L
    internal const val DEFAULT_PASCHA3_BYTES = 3638072646L
    internal const val DEFAULT_MAMATOTO_BYTES = 186917297L
    internal const val DEFAULT_ONLYYOURECROSS_BYTES = 548617730L
    internal const val DEFAULT_MAMORIGAMI_BYTES = 147669840L
    internal const val DEFAULT_AMBIVALENZ_BYTES = 82305908L
    internal const val DEFAULT_FUNNYBEE_BYTES = 68093371L
    internal const val DEFAULT_TOUSHIN2SP_BYTES = 41383183L
    internal const val DEFAULT_MUGENHOUYOU_BYTES = 50736493L
    internal const val DEFAULT_OTOMESENKI_BYTES = 2849367L
    internal const val DEFAULT_GAKUENHYOURYUU_BYTES = 3931456L
    internal const val DEFAULT_MAIDNOSUSUME_BYTES = 21891136L
    internal const val DEFAULT_DALKGAIDEN_BYTES = 460876218L
    internal const val DEFAULT_MAMANYONYO_BYTES = 335070846L
    internal const val DEFAULT_MAJONOSHOKUZAI_BYTES = 974991851L
    internal const val DEFAULT_SABOTEN_BYTES = 1157904094L
    internal const val DEFAULT_DOUBLESENSEI_BYTES = 1403588942L
    internal const val DEFAULT_OJOUSAMA_BYTES = 553143699L
    internal const val DEFAULT_EDORANZE_BYTES = 327142146L
    internal const val DEFAULT_VANISH_BYTES = 936198458L
    internal const val DEFAULT_HOKENSHITSU_BYTES = 1125275837L
    internal const val DEFAULT_KAMUNAGARA_BYTES = 68102129L
    internal const val DEFAULT_ERUMEIFLOWER_BYTES = 149238805L
    internal const val DEFAULT_ERUMEIWAKARE_BYTES = 36214242L
    internal const val DEFAULT_MANANA1_BYTES = 322325810L
    internal const val DEFAULT_MANANA2_BYTES = 346673076L
    internal const val DEFAULT_MANANA3_BYTES = 360676229L
    internal const val DEFAULT_MANANA4_BYTES = 284601476L
    internal const val DEFAULT_MANANA5_BYTES = 319631754L
    internal const val DEFAULT_TSUMASAKURA_BYTES = 33875467L
    internal const val DEFAULT_SHAMANSSANCTUARY_BYTES = 1464069450L
    internal const val DEFAULT_ALIVEZ_BYTES = 3724684828L
    internal const val DEFAULT_DUNGEONSDOLLS_BYTES = 790324801L
    internal const val DEFAULT_SUZUMIYA_BYTES = 694041858L
    internal const val DEFAULT_SHIMAIMA_BYTES = 959573915L
    internal const val DEFAULT_DAIBANCHOUGAIDEN_BYTES = 179008967L
    internal const val DEFAULT_COSPLAYLEMON_BYTES = 59713023L
    internal const val DEFAULT_WHIPHONEY_BYTES = 10895556L
    internal const val DEFAULT_ONIMUSU_BYTES = 45327000L
    internal const val DEFAULT_PROGRX_BYTES = 25585442L
    internal const val DEFAULT_DPSVERN_BYTES = 95418388L
    internal const val DEFAULT_SUKIIRONO_BYTES = 113630600L
    internal const val DEFAULT_GIRLS_BYTES = 135973798L
    internal const val DEFAULT_DEBUT_BYTES = 27789639L
    internal const val DEFAULT_HARUKAVSESCA_BYTES = 667745059L
    internal const val DEFAULT_HARUKANINJA_BYTES = 213542665L
    internal const val DEFAULT_MEGANE_BYTES = 44386500L
    internal const val DEFAULT_RACHELSAKURA_BYTES = 192743017L
    internal const val DEFAULT_SABAKUNIKA_BYTES = 253327874L
    internal const val DEFAULT_VANISHPALM_BYTES = 336117791L
    internal const val DEFAULT_BLADEBRIDERS_BYTES = 1897558326L
    internal const val DEFAULT_DORAPEKO_BYTES = 2610901983L
    internal const val DEFAULT_HANIWAPETIT_BYTES = 63435418L
    internal const val DEFAULT_YAKATA456_BYTES = 73754160L
    internal const val DEFAULT_UMINIOCHITE_BYTES = 9445936L
    internal const val DEFAULT_GAKUENHYOURYUU2_BYTES = 4151616L
    internal const val DEFAULT_YOUSEI_BYTES = 29600823L
    internal const val DEFAULT_TOBIRA_BYTES = 23143915L
    internal const val DEFAULT_BUROBURO_BYTES = 21702464L
    internal const val DEFAULT_KUDAKERO_BYTES = 13232381L
    internal const val DEFAULT_LITTLEVAMPIRE_BYTES = 974162L
    internal const val DEFAULT_INTRUDER_BYTES = 1285632L
    internal const val DEFAULT_CRESCENT_BYTES = 2551428L
    internal const val DEFAULT_TENGU_BYTES = 2701911L
    internal const val DEFAULT_DPS_BYTES = 1672245L
    internal const val DEFAULT_YAKATA1_BYTES = 2717929L
    internal const val DEFAULT_SDPSTONO_BYTES = 1803170L
    internal const val DEFAULT_SDPSKAIZOKU_BYTES = 1861026L
    internal const val DEFAULT_PROGOMAKE_BYTES = 8388471L
    internal const val DEFAULT_AYUMIHINT_BYTES = 1024768L
    internal const val DEFAULT_AYUMILIVE_BYTES = 80554098L
    internal const val DEFAULT_NISENAGURI_BYTES = 13989899L
    internal const val DEFAULT_CAROL_BYTES = 20732992L
    internal const val DEFAULT_OTHELLOT_BYTES = 20293696L
    internal const val DEFAULT_MASQUERADE_BYTES = 286016L
    internal const val DEFAULT_DEKIRUKANA_BYTES = 2525744L
    internal const val DEFAULT_TATAKIKOROSHI_BYTES = 542256L
    internal const val DEFAULT_BALLOONPOP_BYTES = 35380L
    internal const val DEFAULT_HIYOKO2_BYTES = 102330L
    internal const val DEFAULT_SELFGAME_BYTES = 3694656L
    internal const val DEFAULT_MOMOIROGUARDIAN_BYTES = 904356575L
    internal const val DEFAULT_RANCE1CLASSIC_BYTES = 2775360L
    internal const val DEFAULT_RANCE2CLASSIC_BYTES = 5358400L
    internal const val DEFAULT_RANCE3CLASSIC_BYTES = 9186896L
    internal const val DEFAULT_DPSSGFAHREN_BYTES = 847104L
    internal const val DEFAULT_DPSSGKATEI_BYTES = 938240L
    internal const val DEFAULT_DPSSGNOBUNAGA_BYTES = 1074176L
    internal const val DEFAULT_DPSSG2ANTIQUE_BYTES = 1299712L
    internal const val DEFAULT_DPSSG2IKENAI_BYTES = 1173760L
    internal const val DEFAULT_DPSSG2AKAI_BYTES = 1177856L
    internal const val DEFAULT_DPSSG3RABBIT_BYTES = 1313792L
    internal const val DEFAULT_DPSSG3SHINKON_BYTES = 1176832L
    internal const val DEFAULT_DPSSG3SOTSUGYOU_BYTES = 1286656L

    private val initialSystemLocale: Locale = try {
        Resources.getSystem()?.configuration?.locales?.takeIf { !it.isEmpty }?.get(0)
            ?: Locale.getDefault()
    } catch (_: Throwable) {
        Locale.getDefault()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): Int {
        try {
            val f = File(context.filesDir, THEME_FILE_NAME)
            if (f.isFile) {
                val parsed = f.readText(Charsets.UTF_8).trim().toIntOrNull()
                if (parsed != null) {
                    return parsed.coerceIn(THEME_AUTO, THEME_DARK)
                }
            }
        } catch (_: Throwable) {
        }
        return prefs(context).getInt(KEY_THEME_MODE, THEME_AUTO).coerceIn(THEME_AUTO, THEME_DARK)
    }

    fun setThemeMode(context: Context, mode: Int) {
        val valid = mode.coerceIn(THEME_AUTO, THEME_DARK)
        prefs(context).edit().putInt(KEY_THEME_MODE, valid).apply()
        try {
            File(context.filesDir, THEME_FILE_NAME).writeText(valid.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
        applyPlatformNightMode(context, valid)
    }

    fun isMonetSupported(): Boolean = Build.VERSION.SDK_INT >= 31

    fun defaultColorPalette(): String = if (isMonetSupported()) PALETTE_MONET else PALETTE_INDIGO

    fun availableColorPalettes(): List<String> = buildList {
        if (isMonetSupported()) add(PALETTE_MONET)
        add(PALETTE_INDIGO)
        add(PALETTE_SAKURA)
        add(PALETTE_OCEAN)
        add(PALETTE_FOREST)
        add(PALETTE_AMBER)
        add(PALETTE_VIOLET)
    }

    private fun normalizeColorPalette(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        PALETTE_MONET -> if (isMonetSupported()) PALETTE_MONET else PALETTE_INDIGO
        PALETTE_INDIGO -> PALETTE_INDIGO
        PALETTE_SAKURA -> PALETTE_SAKURA
        PALETTE_OCEAN -> PALETTE_OCEAN
        PALETTE_FOREST -> PALETTE_FOREST
        PALETTE_AMBER -> PALETTE_AMBER
        PALETTE_VIOLET -> PALETTE_VIOLET
        else -> defaultColorPalette()
    }

    fun getColorPalette(context: Context): String {
        try {
            val f = File(context.filesDir, PALETTE_FILE_NAME)
            if (f.isFile) {
                return normalizeColorPalette(f.readText(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }
        return normalizeColorPalette(prefs(context).getString(KEY_COLOR_PALETTE, null))
    }

    fun setColorPalette(context: Context, palette: String) {
        val valid = normalizeColorPalette(palette)
        prefs(context).edit().putString(KEY_COLOR_PALETTE, valid).apply()
        try {
            File(context.filesDir, PALETTE_FILE_NAME).writeText(valid, Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    fun resolveThemeRes(context: Context, palette: String = getColorPalette(context)): Int =
        when (normalizeColorPalette(palette)) {
            PALETTE_MONET -> R.style.Theme_Launcher_Palette_Monet
            PALETTE_SAKURA -> R.style.Theme_Launcher_Palette_Sakura
            PALETTE_OCEAN -> R.style.Theme_Launcher_Palette_Ocean
            PALETTE_FOREST -> R.style.Theme_Launcher_Palette_Forest
            PALETTE_AMBER -> R.style.Theme_Launcher_Palette_Amber
            PALETTE_VIOLET -> R.style.Theme_Launcher_Palette_Violet
            else -> R.style.Theme_Launcher
        }

    fun applyPlatformNightMode(context: Context, mode: Int = getThemeMode(context)) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val mgr = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                val nightMode = when (mode) {
                    THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                    THEME_DARK -> UiModeManager.MODE_NIGHT_YES
                    else -> UiModeManager.MODE_NIGHT_AUTO
                }
                mgr?.setApplicationNightMode(nightMode)
            } catch (_: Throwable) {
            }
        }
    }

    private fun normalizeLang(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        LANG_ZH -> LANG_ZH
        LANG_JA -> LANG_JA
        LANG_EN -> LANG_EN
        else -> LANG_AUTO
    }

    fun getLanguageMode(context: Context): String {
        try {
            val f = File(context.filesDir, LANG_FILE_NAME)
            if (f.isFile) {
                return normalizeLang(f.readText(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }
        return normalizeLang(prefs(context).getString(KEY_LANGUAGE, LANG_AUTO))
    }

    fun setLanguageMode(context: Context, lang: String) {
        val valid = normalizeLang(lang)
        prefs(context).edit().putString(KEY_LANGUAGE, valid).apply()
        try {
            val f = File(context.filesDir, LANG_FILE_NAME)
            f.writeText(valid, Charsets.UTF_8)
        } catch (_: Throwable) {
        }
        Locale.setDefault(resolveLocaleForMode(valid))
    }

    fun systemLocale(): Locale =
        Resources.getSystem().configuration.locales.takeIf { !it.isEmpty }?.get(0)
            ?: initialSystemLocale

    private fun resolveLocaleForMode(mode: String): Locale = when (mode) {
        LANG_ZH -> Locale.SIMPLIFIED_CHINESE
        LANG_JA -> Locale.JAPANESE
        LANG_EN -> Locale.ENGLISH
        else -> systemLocale()
    }

    fun resolveLocale(context: Context): Locale =
        resolveLocaleForMode(getLanguageMode(context))

    fun getGameSortMode(context: Context): GameSortMode = try {
        val raw = prefs(context).getString(KEY_GAME_SORT, null)
        val mode = GameSortMode.fromKey(raw)
        if (raw == "popularity") {
            prefs(context).edit().putString(KEY_GAME_SORT, mode.key).apply()
        }
        mode
    } catch (_: ClassCastException) {
        GameSortMode.CATALOG
    }

    fun setGameSortMode(context: Context, mode: GameSortMode) {
        prefs(context).edit().putString(KEY_GAME_SORT, mode.key).apply()
    }

    fun isOnlineInstallEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLINE_INSTALL, false)

    fun isOnboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean("onboarding_complete", false)

    fun completeOnboarding(context: Context, onlineInstallEnabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ONLINE_INSTALL, onlineInstallEnabled)
            .putBoolean("onboarding_complete", true)
            .apply()
    }

    fun prefersPublicStorage(context: Context): Boolean =
        prefs(context).getBoolean("public_storage", false)

    fun setPublicStorage(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("public_storage", enabled).apply()
    }

    fun isUpdateCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHECK_FOR_UPDATES, true)

    fun setUpdateCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHECK_FOR_UPDATES, enabled).apply()
    }

    fun setOnlineInstallEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLINE_INSTALL, enabled).apply()
    }

    @JvmStatic
    fun normalizeDownloadConcurrency(raw: Int): Int =
        raw.coerceIn(MIN_DOWNLOAD_CONCURRENCY, MAX_DOWNLOAD_CONCURRENCY)

    @JvmStatic
    fun getDownloadConcurrency(context: Context): Int =
        normalizeDownloadConcurrency(prefs(context).getInt(KEY_DOWNLOAD_CONCURRENCY, DEFAULT_DOWNLOAD_CONCURRENCY))

    @JvmStatic
    fun setDownloadConcurrency(context: Context, concurrency: Int) {
        prefs(context).edit().putInt(KEY_DOWNLOAD_CONCURRENCY, normalizeDownloadConcurrency(concurrency)).apply()
    }

    /** Removes deprecated pre-PR#108 China-route preferences and migrates any legacy /cn/ size cache keys. */
    @JvmStatic
    fun clearLegacyDownloadRoutePref(context: Context) {
        val sp = prefs(context)
        val hasLegacyRoute = sp.contains(KEY_LEGACY_DOWNLOAD_ROUTE)
        val legacySizePrefix = KEY_BASE_SIZE_PREFIX + CDN_CN_PREFIX
        val legacySizeKeys = sp.all.keys.filter { it.startsWith(legacySizePrefix) }
        if (!hasLegacyRoute && legacySizeKeys.isEmpty()) return
        val editor = sp.edit()
        if (hasLegacyRoute) {
            editor.remove(KEY_LEGACY_DOWNLOAD_ROUTE)
        }
        for (legacyKey in legacySizeKeys) {
            val saved = try {
                sp.getLong(legacyKey, -1L)
            } catch (_: ClassCastException) {
                -1L
            }
            val canonicalKey = KEY_BASE_SIZE_PREFIX + canonicalCdnUrl(legacyKey.removePrefix(KEY_BASE_SIZE_PREFIX))
            val existingCanonical = try {
                sp.getLong(canonicalKey, -1L)
            } catch (_: ClassCastException) {
                -1L
            }
            if (saved > 0L && existingCanonical <= 0L) {
                editor.putLong(canonicalKey, saved)
            }
            editor.remove(legacyKey)
        }
        editor.apply()
    }

    /** Strips any legacy route prefix (/cn/) to recover the canonical game CDN URL for cache keys and install identity. */
    @JvmStatic
    fun canonicalCdnUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        return if (trimmed.startsWith(CDN_CN_PREFIX)) {
            CDN_ORIGIN_PREFIX + trimmed.removePrefix(CDN_CN_PREFIX)
        } else {
            trimmed
        }
    }

    @JvmStatic
    fun defaultBaseGameSizeForUrl(cdnBaseUrl: String): Long {
        return when (canonicalCdnUrl(cdnBaseUrl)) {
            OnlineInstaller.RANCE10_CDN_URL -> DEFAULT_RANCE10_BYTES
            OnlineInstaller.DOHNA_DOHNA_CDN_URL -> DEFAULT_DOHNA_BYTES
            OnlineInstaller.EVENICLE_CDN_URL -> DEFAULT_EVENICLE_BYTES
            OnlineInstaller.EVENICLE2_CDN_URL -> DEFAULT_EVENICLE2_BYTES
            OnlineInstaller.SENGOKU_RANCE_CDN_URL -> DEFAULT_SENGOKU_RANCE_BYTES
            OnlineInstaller.RANCE01_CDN_URL -> DEFAULT_RANCE01_BYTES
            OnlineInstaller.RANCE02_CDN_URL -> DEFAULT_RANCE02_BYTES
            OnlineInstaller.RANCE03_CDN_URL -> DEFAULT_RANCE03_BYTES
            OnlineInstaller.RANCE04_CDN_URL -> DEFAULT_RANCE04_BYTES
            OnlineInstaller.RANCE41_CDN_URL -> DEFAULT_RANCE41_BYTES
            OnlineInstaller.RANCE42_CDN_URL -> DEFAULT_RANCE42_BYTES
            OnlineInstaller.RANCE5D_CDN_URL -> DEFAULT_RANCE5D_BYTES
            OnlineInstaller.RANCE06_CDN_URL -> DEFAULT_RANCE06_BYTES
            OnlineInstaller.RANCE08_CDN_URL -> DEFAULT_RANCE08_BYTES
            OnlineInstaller.RANCE09_CDN_URL -> DEFAULT_RANCE09_BYTES
            OnlineInstaller.KICHIKUOU_CDN_URL -> DEFAULT_KICHIKUOU_BYTES
            OnlineInstaller.DAIAKUJI_CDN_URL -> DEFAULT_DAIAKUJI_BYTES
            OnlineInstaller.DAIBANCHOU_CDN_URL -> DEFAULT_DAIBANCHOU_BYTES
            OnlineInstaller.DAITEIKOKU_CDN_URL -> DEFAULT_DAITEIKOKU_BYTES
            OnlineInstaller.ESCALAYER_CDN_URL -> DEFAULT_ESCALAYER_BYTES
            OnlineInstaller.ESCALAYER_REBOOT_CDN_URL -> DEFAULT_ESCALAYER_REBOOT_BYTES
            OnlineInstaller.GALZOO_CDN_URL -> DEFAULT_GALZOO_BYTES
            OnlineInstaller.IXSEAL_CDN_URL -> DEFAULT_IXSEAL_BYTES
            OnlineInstaller.HARUKA_CDN_URL -> DEFAULT_HARUKA_BYTES
            OnlineInstaller.TSUMASHIBORI_CDN_URL -> DEFAULT_TSUMASHIBORI_BYTES
            OnlineInstaller.TSUMAMIGUI_CDN_URL -> DEFAULT_TSUMAMIGUI_BYTES
            OnlineInstaller.TSUMAMIGUI3_CDN_URL -> DEFAULT_TSUMAMIGUI3_BYTES
            OnlineInstaller.KAERUNYO_CDN_URL -> DEFAULT_KAERUNYO_BYTES
            OnlineInstaller.ATLACHNACHA_CDN_URL -> DEFAULT_ATLACHNACHA_BYTES
            OnlineInstaller.REISHIKI_CDN_URL -> DEFAULT_REISHIKI_BYTES
            OnlineInstaller.HARUURARE_CDN_URL -> DEFAULT_HARUURARE_BYTES
            OnlineInstaller.OKUSAMAJUN_CDN_URL -> DEFAULT_OKUSAMAJUN_BYTES
            OnlineInstaller.OKUSAMANTR_CDN_URL -> DEFAULT_OKUSAMANTR_BYTES
            OnlineInstaller.EVENICLE2RANCE_CDN_URL -> DEFAULT_EVENICLE2RANCE_BYTES
            OnlineInstaller.EVENICLEGAIDEN_CDN_URL -> DEFAULT_EVENICLEGAIDEN_BYTES
            OnlineInstaller.WAIDONYO_CDN_URL -> DEFAULT_WAIDONYO_BYTES
            OnlineInstaller.RANCE6AFTER_CDN_URL -> DEFAULT_RANCE6AFTER_BYTES
            OnlineInstaller.YAKATA2_CDN_URL -> DEFAULT_YAKATA2_BYTES
            OnlineInstaller.SDPSMARIA_CDN_URL -> DEFAULT_SDPSMARIA_BYTES
            OnlineInstaller.TOUSHIN3_CDN_URL -> DEFAULT_TOUSHIN3_BYTES
            OnlineInstaller.OYAKORANKAN_CDN_URL -> DEFAULT_OYAKORANKAN_BYTES
            OnlineInstaller.YORUGAKURU_CDN_URL -> DEFAULT_YORUGAKURU_BYTES
            OnlineInstaller.RANCE1DIGEST_CDN_URL -> DEFAULT_RANCE1DIGEST_BYTES
            OnlineInstaller.MAMAN_CDN_URL -> DEFAULT_MAMAN_BYTES
            OnlineInstaller.EVENICLERANCE_CDN_URL -> DEFAULT_EVENICLERANCE_BYTES
            OnlineInstaller.HAHARANMAN_CDN_URL -> DEFAULT_HAHARANMAN_BYTES
            OnlineInstaller.TSUMAMIGUI2_CDN_URL -> DEFAULT_TSUMAMIGUI2_BYTES
            OnlineInstaller.TSUMAMIGUI3JUN_CDN_URL -> DEFAULT_TSUMAMIGUI3JUN_BYTES
            OnlineInstaller.TSUMAMIGUI3NTR_CDN_URL -> DEFAULT_TSUMAMIGUI3NTR_BYTES
            OnlineInstaller.DIABOLIQUE_CDN_URL -> DEFAULT_DIABOLIQUE_BYTES
            OnlineInstaller.GAKUENKING_CDN_URL -> DEFAULT_GAKUENKING_BYTES
            OnlineInstaller.GAKUENKINGEX_CDN_URL -> DEFAULT_GAKUENKINGEX_BYTES
            OnlineInstaller.OUDOUYUUSHA_CDN_URL -> DEFAULT_OUDOUYUUSHA_BYTES
            OnlineInstaller.TOUSHIN1_CDN_URL -> DEFAULT_TOUSHIN1_BYTES
            OnlineInstaller.TOUSHIN2_CDN_URL -> DEFAULT_TOUSHIN2_BYTES
            OnlineInstaller.DARCROWS_CDN_URL -> DEFAULT_DARCROWS_BYTES
            OnlineInstaller.IKUSAMIKO_CDN_URL -> DEFAULT_IKUSAMIKO_BYTES
            OnlineInstaller.ONLYYOU_CDN_URL -> DEFAULT_ONLYYOU_BYTES
            OnlineInstaller.SANBIKI_CDN_URL -> DEFAULT_SANBIKI_BYTES
            OnlineInstaller.PERSIOM_CDN_URL -> DEFAULT_PERSIOM_BYTES
            OnlineInstaller.SEEINAO_CDN_URL -> DEFAULT_SEEINAO_BYTES
            OnlineInstaller.HUSHABY_CDN_URL -> DEFAULT_HUSHABY_BYTES
            OnlineInstaller.AYUMI_CDN_URL -> DEFAULT_AYUMI_BYTES
            OnlineInstaller.CIELCLAIRE_CDN_URL -> DEFAULT_CIELCLAIRE_BYTES
            OnlineInstaller.NIGHTDEMON_CDN_URL -> DEFAULT_NIGHTDEMON_BYTES
            OnlineInstaller.KATSUMI_CDN_URL -> DEFAULT_KATSUMI_BYTES
            OnlineInstaller.DRSTOP_CDN_URL -> DEFAULT_DRSTOP_BYTES
            OnlineInstaller.DPSALL_CDN_URL -> DEFAULT_DPSALL_BYTES
            OnlineInstaller.KOREDPS_CDN_URL -> DEFAULT_KOREDPS_BYTES
            OnlineInstaller.KAERUALICE_CDN_URL -> DEFAULT_KAERUALICE_BYTES
            OnlineInstaller.PROSTUDENTG_CDN_URL -> DEFAULT_PROSTUDENTG_BYTES
            OnlineInstaller.PROSTUDENTGOOD_CDN_URL -> DEFAULT_PROSTUDENTGOOD_BYTES
            OnlineInstaller.PASTELCHIME_CDN_URL -> DEFAULT_PASTELCHIME_BYTES
            OnlineInstaller.PASTELCHIME2_CDN_URL -> DEFAULT_PASTELCHIME2_BYTES
            OnlineInstaller.YAKATA3_CDN_URL -> DEFAULT_YAKATA3_BYTES
            OnlineInstaller.DALK_CDN_URL -> DEFAULT_DALK_BYTES
            OnlineInstaller.NINGENGARI_CDN_URL -> DEFAULT_NINGENGARI_BYTES
            OnlineInstaller.PASCHACPP_CDN_URL -> DEFAULT_PASCHACPP_BYTES
            OnlineInstaller.PASCHA3_CDN_URL -> DEFAULT_PASCHA3_BYTES
            OnlineInstaller.MAMATOTO_CDN_URL -> DEFAULT_MAMATOTO_BYTES
            OnlineInstaller.ONLYYOURECROSS_CDN_URL -> DEFAULT_ONLYYOURECROSS_BYTES
            OnlineInstaller.MAMORIGAMI_CDN_URL -> DEFAULT_MAMORIGAMI_BYTES
            OnlineInstaller.AMBIVALENZ_CDN_URL -> DEFAULT_AMBIVALENZ_BYTES
            OnlineInstaller.FUNNYBEE_CDN_URL -> DEFAULT_FUNNYBEE_BYTES
            OnlineInstaller.TOUSHIN2SP_CDN_URL -> DEFAULT_TOUSHIN2SP_BYTES
            OnlineInstaller.MUGENHOUYOU_CDN_URL -> DEFAULT_MUGENHOUYOU_BYTES
            OnlineInstaller.OTOMESENKI_CDN_URL -> DEFAULT_OTOMESENKI_BYTES
            OnlineInstaller.GAKUENHYOURYUU_CDN_URL -> DEFAULT_GAKUENHYOURYUU_BYTES
            OnlineInstaller.MAIDNOSUSUME_CDN_URL -> DEFAULT_MAIDNOSUSUME_BYTES
            OnlineInstaller.DALKGAIDEN_CDN_URL -> DEFAULT_DALKGAIDEN_BYTES
            OnlineInstaller.MAMANYONYO_CDN_URL -> DEFAULT_MAMANYONYO_BYTES
            OnlineInstaller.MAJONOSHOKUZAI_CDN_URL -> DEFAULT_MAJONOSHOKUZAI_BYTES
            OnlineInstaller.SABOTEN_CDN_URL -> DEFAULT_SABOTEN_BYTES
            OnlineInstaller.DOUBLESENSEI_CDN_URL -> DEFAULT_DOUBLESENSEI_BYTES
            OnlineInstaller.OJOUSAMA_CDN_URL -> DEFAULT_OJOUSAMA_BYTES
            OnlineInstaller.EDORANZE_CDN_URL -> DEFAULT_EDORANZE_BYTES
            OnlineInstaller.VANISH_CDN_URL -> DEFAULT_VANISH_BYTES
            OnlineInstaller.HOKENSHITSU_CDN_URL -> DEFAULT_HOKENSHITSU_BYTES
            OnlineInstaller.KAMUNAGARA_CDN_URL -> DEFAULT_KAMUNAGARA_BYTES
            OnlineInstaller.ERUMEIFLOWER_CDN_URL -> DEFAULT_ERUMEIFLOWER_BYTES
            OnlineInstaller.ERUMEIWAKARE_CDN_URL -> DEFAULT_ERUMEIWAKARE_BYTES
            OnlineInstaller.MANANA1_CDN_URL -> DEFAULT_MANANA1_BYTES
            OnlineInstaller.MANANA2_CDN_URL -> DEFAULT_MANANA2_BYTES
            OnlineInstaller.MANANA3_CDN_URL -> DEFAULT_MANANA3_BYTES
            OnlineInstaller.MANANA4_CDN_URL -> DEFAULT_MANANA4_BYTES
            OnlineInstaller.MANANA5_CDN_URL -> DEFAULT_MANANA5_BYTES
            OnlineInstaller.TSUMASAKURA_CDN_URL -> DEFAULT_TSUMASAKURA_BYTES
            OnlineInstaller.SHAMANSSANCTUARY_CDN_URL -> DEFAULT_SHAMANSSANCTUARY_BYTES
            OnlineInstaller.ALIVEZ_CDN_URL -> DEFAULT_ALIVEZ_BYTES
            OnlineInstaller.DUNGEONSDOLLS_CDN_URL -> DEFAULT_DUNGEONSDOLLS_BYTES
            OnlineInstaller.SUZUMIYA_CDN_URL -> DEFAULT_SUZUMIYA_BYTES
            OnlineInstaller.SHIMAIMA_CDN_URL -> DEFAULT_SHIMAIMA_BYTES
            OnlineInstaller.DAIBANCHOUGAIDEN_CDN_URL -> DEFAULT_DAIBANCHOUGAIDEN_BYTES
            OnlineInstaller.COSPLAYLEMON_CDN_URL -> DEFAULT_COSPLAYLEMON_BYTES
            OnlineInstaller.WHIPHONEY_CDN_URL -> DEFAULT_WHIPHONEY_BYTES
            OnlineInstaller.ONIMUSU_CDN_URL -> DEFAULT_ONIMUSU_BYTES
            OnlineInstaller.PROGRX_CDN_URL -> DEFAULT_PROGRX_BYTES
            OnlineInstaller.DPSVERN_CDN_URL -> DEFAULT_DPSVERN_BYTES
            OnlineInstaller.SUKIIRONO_CDN_URL -> DEFAULT_SUKIIRONO_BYTES
            OnlineInstaller.GIRLS_CDN_URL -> DEFAULT_GIRLS_BYTES
            OnlineInstaller.DEBUT_CDN_URL -> DEFAULT_DEBUT_BYTES
            OnlineInstaller.HARUKAVSESCA_CDN_URL -> DEFAULT_HARUKAVSESCA_BYTES
            OnlineInstaller.HARUKANINJA_CDN_URL -> DEFAULT_HARUKANINJA_BYTES
            OnlineInstaller.MEGANE_CDN_URL -> DEFAULT_MEGANE_BYTES
            OnlineInstaller.RACHELSAKURA_CDN_URL -> DEFAULT_RACHELSAKURA_BYTES
            OnlineInstaller.SABAKUNIKA_CDN_URL -> DEFAULT_SABAKUNIKA_BYTES
            OnlineInstaller.VANISHPALM_CDN_URL -> DEFAULT_VANISHPALM_BYTES
            OnlineInstaller.BLADEBRIDERS_CDN_URL -> DEFAULT_BLADEBRIDERS_BYTES
            OnlineInstaller.DORAPEKO_CDN_URL -> DEFAULT_DORAPEKO_BYTES
            OnlineInstaller.HANIWAPETIT_CDN_URL -> DEFAULT_HANIWAPETIT_BYTES
            OnlineInstaller.YAKATA456_CDN_URL -> DEFAULT_YAKATA456_BYTES
            OnlineInstaller.UMINIOCHITE_CDN_URL -> DEFAULT_UMINIOCHITE_BYTES
            OnlineInstaller.GAKUENHYOURYUU2_CDN_URL -> DEFAULT_GAKUENHYOURYUU2_BYTES
            OnlineInstaller.YOUSEI_CDN_URL -> DEFAULT_YOUSEI_BYTES
            OnlineInstaller.TOBIRA_CDN_URL -> DEFAULT_TOBIRA_BYTES
            OnlineInstaller.BUROBURO_CDN_URL -> DEFAULT_BUROBURO_BYTES
            OnlineInstaller.KUDAKERO_CDN_URL -> DEFAULT_KUDAKERO_BYTES
            OnlineInstaller.LITTLEVAMPIRE_CDN_URL -> DEFAULT_LITTLEVAMPIRE_BYTES
            OnlineInstaller.INTRUDER_CDN_URL -> DEFAULT_INTRUDER_BYTES
            OnlineInstaller.CRESCENT_CDN_URL -> DEFAULT_CRESCENT_BYTES
            OnlineInstaller.TENGU_CDN_URL -> DEFAULT_TENGU_BYTES
            OnlineInstaller.DPS_CDN_URL -> DEFAULT_DPS_BYTES
            OnlineInstaller.YAKATA1_CDN_URL -> DEFAULT_YAKATA1_BYTES
            OnlineInstaller.SDPSTONO_CDN_URL -> DEFAULT_SDPSTONO_BYTES
            OnlineInstaller.SDPSKAIZOKU_CDN_URL -> DEFAULT_SDPSKAIZOKU_BYTES
            OnlineInstaller.PROGOMAKE_CDN_URL -> DEFAULT_PROGOMAKE_BYTES
            OnlineInstaller.AYUMIHINT_CDN_URL -> DEFAULT_AYUMIHINT_BYTES
            OnlineInstaller.AYUMILIVE_CDN_URL -> DEFAULT_AYUMILIVE_BYTES
            OnlineInstaller.NISENAGURI_CDN_URL -> DEFAULT_NISENAGURI_BYTES
            OnlineInstaller.CAROL_CDN_URL -> DEFAULT_CAROL_BYTES
            OnlineInstaller.OTHELLOT_CDN_URL -> DEFAULT_OTHELLOT_BYTES
            OnlineInstaller.MASQUERADE_CDN_URL -> DEFAULT_MASQUERADE_BYTES
            OnlineInstaller.DEKIRUKANA_CDN_URL -> DEFAULT_DEKIRUKANA_BYTES
            OnlineInstaller.TATAKIKOROSHI_CDN_URL -> DEFAULT_TATAKIKOROSHI_BYTES
            OnlineInstaller.BALLOONPOP_CDN_URL -> DEFAULT_BALLOONPOP_BYTES
            OnlineInstaller.HIYOKO2_CDN_URL -> DEFAULT_HIYOKO2_BYTES
            OnlineInstaller.SELFGAME_CDN_URL -> DEFAULT_SELFGAME_BYTES
            OnlineInstaller.MOMOIROGUARDIAN_CDN_URL -> DEFAULT_MOMOIROGUARDIAN_BYTES
            OnlineInstaller.RANCE1CLASSIC_CDN_URL -> DEFAULT_RANCE1CLASSIC_BYTES
            OnlineInstaller.RANCE2CLASSIC_CDN_URL -> DEFAULT_RANCE2CLASSIC_BYTES
            OnlineInstaller.RANCE3CLASSIC_CDN_URL -> DEFAULT_RANCE3CLASSIC_BYTES
            OnlineInstaller.DPSSGFAHREN_CDN_URL -> DEFAULT_DPSSGFAHREN_BYTES
            OnlineInstaller.DPSSGKATEI_CDN_URL -> DEFAULT_DPSSGKATEI_BYTES
            OnlineInstaller.DPSSGNOBUNAGA_CDN_URL -> DEFAULT_DPSSGNOBUNAGA_BYTES
            OnlineInstaller.DPSSG2ANTIQUE_CDN_URL -> DEFAULT_DPSSG2ANTIQUE_BYTES
            OnlineInstaller.DPSSG2IKENAI_CDN_URL -> DEFAULT_DPSSG2IKENAI_BYTES
            OnlineInstaller.DPSSG2AKAI_CDN_URL -> DEFAULT_DPSSG2AKAI_BYTES
            OnlineInstaller.DPSSG3RABBIT_CDN_URL -> DEFAULT_DPSSG3RABBIT_BYTES
            OnlineInstaller.DPSSG3SHINKON_CDN_URL -> DEFAULT_DPSSG3SHINKON_BYTES
            OnlineInstaller.DPSSG3SOTSUGYOU_CDN_URL -> DEFAULT_DPSSG3SOTSUGYOU_BYTES
            else -> -1L
        }
    }

    fun getCachedBaseGameSize(context: Context, cdnBaseUrl: String): Long {
        val clean = canonicalCdnUrl(cdnBaseUrl)
        val saved = prefs(context).getLong(KEY_BASE_SIZE_PREFIX + clean, -1L)
        if (saved > 0L) return saved
        return defaultBaseGameSizeForUrl(clean)
    }

    fun setCachedBaseGameSize(context: Context, cdnBaseUrl: String, totalBytes: Long) {
        if (totalBytes <= 0L) return
        val clean = canonicalCdnUrl(cdnBaseUrl)
        prefs(context).edit().putLong(KEY_BASE_SIZE_PREFIX + clean, totalBytes).apply()
    }

    fun systemNightBits(): Int =
        Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    fun wrapThemeContext(base: Context): Context {
        val mode = getThemeMode(base)
        val desiredNightBits = when (mode) {
            THEME_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            THEME_DARK -> Configuration.UI_MODE_NIGHT_YES
            else -> systemNightBits()
        }
        val desiredLocale = resolveLocale(base)
        Locale.setDefault(desiredLocale)

        val currentCfg = base.resources.configuration
        val currentBits = currentCfg.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val currentLocale = currentCfg.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()

        val nightMatches = desiredNightBits == Configuration.UI_MODE_NIGHT_UNDEFINED || currentBits == desiredNightBits
        val localeMatches = currentLocale.language == desiredLocale.language &&
            (desiredLocale.country.isEmpty() || currentLocale.country == desiredLocale.country)

        if (nightMatches && localeMatches) {
            return base
        }

        val overrideCfg = Configuration(currentCfg)
        if (!nightMatches) {
            overrideCfg.uiMode = (overrideCfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or desiredNightBits
        }
        if (!localeMatches) {
            overrideCfg.setLocale(desiredLocale)
            overrideCfg.setLayoutDirection(desiredLocale)
        }
        return base.createConfigurationContext(overrideCfg)
    }
}

open class ThemedActivity : Activity() {
    private var appliedThemeMode: Int = AppSettings.THEME_AUTO
    private var appliedNightBits: Int = Configuration.UI_MODE_NIGHT_UNDEFINED
    private var appliedColorPalette: String = AppSettings.PALETTE_INDIGO
    private var appliedLanguageMode: String = AppSettings.LANG_AUTO
    private var appliedEffectiveLang: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrapThemeContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ErrorReportKit.installUncaughtExceptionHandler(this)
        appliedThemeMode = AppSettings.getThemeMode(this)
        appliedNightBits = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        appliedColorPalette = AppSettings.getColorPalette(this)
        appliedLanguageMode = AppSettings.getLanguageMode(this)
        appliedEffectiveLang = AppSettings.resolveLocale(this).toLanguageTag()
        setTheme(AppSettings.resolveThemeRes(this, appliedColorPalette))
        super.onCreate(savedInstanceState)
        try {
            val info = packageManager.getActivityInfo(componentName, 0)
            if (info.labelRes != 0) {
                title = getString(info.labelRes)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        ErrorReportKit.handleActivityResult(this, requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        val currentMode = AppSettings.getThemeMode(this)
        val expectedBits = when (currentMode) {
            AppSettings.THEME_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            AppSettings.THEME_DARK -> Configuration.UI_MODE_NIGHT_YES
            else -> AppSettings.systemNightBits()
        }
        val currentPalette = AppSettings.getColorPalette(this)
        val currentLangMode = AppSettings.getLanguageMode(this)
        val currentEffectiveLang = AppSettings.resolveLocale(this).toLanguageTag()

        if (currentMode != appliedThemeMode ||
            (expectedBits != Configuration.UI_MODE_NIGHT_UNDEFINED && expectedBits != appliedNightBits) ||
            currentPalette != appliedColorPalette ||
            currentLangMode != appliedLanguageMode ||
            currentEffectiveLang != appliedEffectiveLang
        ) {
            recreate()
        }
    }
}
