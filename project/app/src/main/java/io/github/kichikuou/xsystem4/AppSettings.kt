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
    private const val KEY_ONLINE_INSTALL = "online_install_enabled"
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

    private const val DEFAULT_RANCE10_BYTES = 4028714690L
    private const val DEFAULT_DOHNA_BYTES = 3310231215L
    private const val DEFAULT_EVENICLE_BYTES = 2453622110L
    private const val DEFAULT_SENGOKU_RANCE_BYTES = 1885221320L
    private const val DEFAULT_RANCE03_BYTES = 2223244774L

    private val initialSystemLocale: Locale =
        Resources.getSystem().configuration.locales.takeIf { !it.isEmpty }?.get(0)
            ?: Locale.getDefault()

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

    fun isOnlineInstallEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLINE_INSTALL, false)

    fun setOnlineInstallEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLINE_INSTALL, enabled).apply()
    }

    fun getCachedBaseGameSize(context: Context, cdnBaseUrl: String): Long {
        val clean = cdnBaseUrl.trimEnd('/')
        val saved = prefs(context).getLong(KEY_BASE_SIZE_PREFIX + clean, -1L)
        if (saved > 0L) return saved
        return when (clean) {
            OnlineInstaller.RANCE10_CDN_URL -> DEFAULT_RANCE10_BYTES
            OnlineInstaller.DOHNA_DOHNA_CDN_URL -> DEFAULT_DOHNA_BYTES
            OnlineInstaller.EVENICLE_CDN_URL -> DEFAULT_EVENICLE_BYTES
            OnlineInstaller.SENGOKU_RANCE_CDN_URL -> DEFAULT_SENGOKU_RANCE_BYTES
            OnlineInstaller.RANCE03_CDN_URL -> DEFAULT_RANCE03_BYTES
            else -> -1L
        }
    }

    fun setCachedBaseGameSize(context: Context, cdnBaseUrl: String, totalBytes: Long) {
        if (totalBytes <= 0L) return
        val clean = cdnBaseUrl.trimEnd('/')
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
