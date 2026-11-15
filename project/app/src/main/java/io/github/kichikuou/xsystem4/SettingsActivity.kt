package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.settings_general_card).clipToOutline = true
        findViewById<View>(R.id.settings_about_card).clipToOutline = true

        val accent = getColor(R.color.ui_primary)
        findViewById<ImageView>(R.id.icon_settings_theme)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_theme, accent))
        findViewById<ImageView>(R.id.icon_settings_online)
            .setImageDrawable(UiKit.tonalIcon(this, R.drawable.ic_cloud_download, accent))

        val btnLicenses = findViewById<Button>(R.id.btn_settings_licenses)
        val btnGithub = findViewById<Button>(R.id.btn_settings_github)
        UiKit.styleActionRow(btnLicenses, R.drawable.ic_licenses, accent)
        UiKit.styleActionRow(btnGithub, R.drawable.ic_github, accent)

        findViewById<View>(R.id.row_settings_theme).setOnClickListener {
            showThemeDialog()
        }
        findViewById<View>(R.id.row_settings_online_install).setOnClickListener {
            onOnlineInstallClicked()
        }
        btnLicenses.setOnClickListener {
            startActivity(Intent(this, LicensesMenuActivity::class.java))
        }
        btnGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameCatalog.GITHUB_URL)))
        }

        refreshSettingsUi()
    }

    override fun onResume() {
        super.onResume()
        refreshSettingsUi()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshSettingsUi() {
        val mode = AppSettings.getThemeMode(this)
        findViewById<TextView>(R.id.value_settings_theme).text = when (mode) {
            AppSettings.THEME_LIGHT -> getString(R.string.theme_light)
            AppSettings.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_auto)
        }
        findViewById<Switch>(R.id.switch_online_install).isChecked =
            AppSettings.isOnlineInstallEnabled(this)
    }

    private fun showThemeDialog() {
        val modes = intArrayOf(
            AppSettings.THEME_AUTO,
            AppSettings.THEME_LIGHT,
            AppSettings.THEME_DARK
        )
        val labels = arrayOf(
            getString(R.string.theme_auto),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val currentMode = AppSettings.getThemeMode(this)
        val checkedIdx = modes.indexOf(currentMode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, checkedIdx) { dlg, which ->
                val nextMode = modes.getOrElse(which) { AppSettings.THEME_AUTO }
                dlg.dismiss()
                if (nextMode != currentMode) {
                    AppSettings.setThemeMode(this, nextMode)
                    recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onOnlineInstallClicked() {
        val currentlyEnabled = AppSettings.isOnlineInstallEnabled(this)
        if (currentlyEnabled) {
            AppSettings.setOnlineInstallEnabled(this, false)
            refreshSettingsUi()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.online_disclaimer_title)
            .setMessage(R.string.online_disclaimer_msg)
            .setPositiveButton(R.string.online_disclaimer_agree) { _, _ ->
                AppSettings.setOnlineInstallEnabled(this, true)
                refreshSettingsUi()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                refreshSettingsUi()
            }
            .setOnCancelListener {
                refreshSettingsUi()
            }
            .show()
    }
}
