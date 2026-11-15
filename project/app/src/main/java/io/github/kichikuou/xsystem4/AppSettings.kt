package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle

object AppSettings {
    private const val PREFS_NAME = "alicehub_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ONLINE_INSTALL = "online_install_enabled"
    private const val KEY_BASE_SIZE_PREFIX = "base_size_"

    const val THEME_AUTO = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private const val DEFAULT_RANCE10_BYTES = 4028714690L
    private const val DEFAULT_DOHNA_BYTES = 3310231215L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_AUTO).coerceIn(THEME_AUTO, THEME_DARK)

    fun setThemeMode(context: Context, mode: Int) {
        val valid = mode.coerceIn(THEME_AUTO, THEME_DARK)
        prefs(context).edit().putInt(KEY_THEME_MODE, valid).apply()
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
        val currentCfg = base.resources.configuration
        val currentBits = currentCfg.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (desiredNightBits == Configuration.UI_MODE_NIGHT_UNDEFINED || currentBits == desiredNightBits) {
            return base
        }
        val overrideCfg = Configuration(currentCfg)
        overrideCfg.uiMode = (overrideCfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or desiredNightBits
        return base.createConfigurationContext(overrideCfg)
    }
}

open class ThemedActivity : Activity() {
    private var appliedThemeMode: Int = AppSettings.THEME_AUTO
    private var appliedNightBits: Int = Configuration.UI_MODE_NIGHT_UNDEFINED

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrapThemeContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedThemeMode = AppSettings.getThemeMode(this)
        appliedNightBits = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val currentMode = AppSettings.getThemeMode(this)
        val expectedBits = when (currentMode) {
            AppSettings.THEME_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            AppSettings.THEME_DARK -> Configuration.UI_MODE_NIGHT_YES
            else -> AppSettings.systemNightBits()
        }
        if (currentMode != appliedThemeMode ||
            (expectedBits != Configuration.UI_MODE_NIGHT_UNDEFINED && expectedBits != appliedNightBits)
        ) {
            recreate()
        }
    }
}
