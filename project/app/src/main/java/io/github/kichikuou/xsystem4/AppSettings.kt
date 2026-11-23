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
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_GAME_SORT = "game_sort"
    private const val KEY_ONLINE_INSTALL = "online_install_enabled"
    private const val KEY_DOWNLOAD_ROUTE = "download_route"
    private const val KEY_DOWNLOAD_CONCURRENCY = "download_concurrency"
    private const val KEY_BASE_SIZE_PREFIX = "base_size_"
    private const val LANG_FILE_NAME = ".app_lang"
    private const val THEME_FILE_NAME = ".app_theme"

    const val THEME_AUTO = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val LANG_AUTO = ""
    const val LANG_ZH = "zh"
    const val LANG_JA = "ja"
    const val LANG_EN = "en"

    const val ROUTE_GLOBAL = "global"
    const val ROUTE_CN = "cn"
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
    internal const val DEFAULT_HARUKA_BYTES = 3797821541L
    internal const val DEFAULT_TSUMASHIBORI_BYTES = 2850964030L
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

    fun setOnlineInstallEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLINE_INSTALL, enabled).apply()
    }

    private fun normalizeDownloadRoute(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        ROUTE_CN -> ROUTE_CN
        else -> ROUTE_GLOBAL
    }

    @JvmStatic
    fun getDownloadRoute(context: Context): String =
        normalizeDownloadRoute(prefs(context).getString(KEY_DOWNLOAD_ROUTE, ROUTE_GLOBAL))

    @JvmStatic
    fun setDownloadRoute(context: Context, route: String) {
        prefs(context).edit().putString(KEY_DOWNLOAD_ROUTE, normalizeDownloadRoute(route)).apply()
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

    /** Strips any route prefix (/cn/) to recover the canonical game CDN URL for cache keys and install identity. */
    @JvmStatic
    fun canonicalCdnUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        return if (trimmed.startsWith(CDN_CN_PREFIX)) {
            CDN_ORIGIN_PREFIX + trimmed.removePrefix(CDN_CN_PREFIX)
        } else {
            trimmed
        }
    }

    /** Applies [route] to any AliceHub CDN URL (idempotent); non-AliceHub URLs pass through unchanged. */
    @JvmStatic
    fun applyDownloadRoute(url: String, route: String): String {
        if (!url.startsWith(CDN_ORIGIN_PREFIX)) return url
        val rest = url.removePrefix(CDN_ORIGIN_PREFIX)
        val canonicalRest = if (rest.startsWith("cn/")) rest.removePrefix("cn/") else rest
        return if (normalizeDownloadRoute(route) == ROUTE_CN) {
            CDN_CN_PREFIX + canonicalRest
        } else {
            CDN_ORIGIN_PREFIX + canonicalRest
        }
    }

    @JvmStatic
    fun resolveCdnUrl(context: Context, url: String): String =
        applyDownloadRoute(url, getDownloadRoute(context))

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
    private var appliedLanguageMode: String = AppSettings.LANG_AUTO
    private var appliedEffectiveLang: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrapThemeContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ErrorReportKit.installUncaughtExceptionHandler(this)
        appliedThemeMode = AppSettings.getThemeMode(this)
        appliedNightBits = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        appliedLanguageMode = AppSettings.getLanguageMode(this)
        appliedEffectiveLang = AppSettings.resolveLocale(this).toLanguageTag()
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
        val currentLangMode = AppSettings.getLanguageMode(this)
        val currentEffectiveLang = AppSettings.resolveLocale(this).toLanguageTag()

        if (currentMode != appliedThemeMode ||
            (expectedBits != Configuration.UI_MODE_NIGHT_UNDEFINED && expectedBits != appliedNightBits) ||
            currentLangMode != appliedLanguageMode ||
            currentEffectiveLang != appliedEffectiveLang
        ) {
            recreate()
        }
    }
}
