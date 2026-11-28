package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class GamepadSettingsActivity : ThemedActivity(), GameAccentHost {

    companion object {
        const val EXTRA_PROFILE_KEY = "EXTRA_PROFILE_KEY"
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"
        const val EXTRA_GAME_TITLE = "EXTRA_GAME_TITLE"
        private const val REQ_GAMEPAD_EDITOR = 4096
    }

    private lateinit var controller: GamepadConfigController
    private var profileKey: String? = null
    private var gameId: String? = null
    private var gameTitle: String? = null
    private var activeGameAccent: Int? = null
    private var statusTv: TextView? = null
    private var saveBtn: Button? = null
    private var backInvokedCallback: Any? = null
    private val trackedDialogs = mutableListOf<android.app.Dialog>()

    override fun activeGameAccentColor(): Int? = activeGameAccent

    private fun trackDialog(dlg: android.app.Dialog) {
        if (dlg is AlertDialog) {
            activeGameAccent?.let { UiKit.tintAlertDialog(dlg, it) }
        }
        trackedDialogs.removeAll { !it.isShowing }
        trackedDialogs.add(dlg)
    }

    private fun dismissTrackedDialogs() {
        val copy = trackedDialogs.toList()
        trackedDialogs.clear()
        for (dlg in copy) {
            try {
                if (dlg.isShowing) dlg.dismiss()
            } catch (_: Throwable) {
            }
        }
    }

    private fun tr(zh: String, ja: String, en: String): String =
        GamepadBridge.tr(this, zh, ja, en)

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        profileKey = intent.getStringExtra(EXTRA_PROFILE_KEY)?.takeIf { it.isNotBlank() }
        val rawGameId = intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
            ?: profileKey?.let { key -> GameCatalog.classifyDirectory(gameDir = null, dirName = key, saveDir = null, displayName = key) }
        gameId = rawGameId
        gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE)?.takeIf { it.isNotBlank() }
            ?: rawGameId?.let { GameCatalog.descriptorFor(this, it).title }

        val pageLabel = getString(R.string.settings_gamepad)
        title = if (!gameTitle.isNullOrBlank()) "$gameTitle · $pageLabel" else pageLabel

        val rawGameAccent = rawGameId?.let { GameCatalog.descriptorFor(this, it).accentColor }
        activeGameAccent = rawGameAccent
        val accentColor = rawGameAccent?.let { UiKit.accentOnSurface(this, it) } ?: UiKit.primaryColor(this)

        controller = GamepadConfigController(
            context = this,
            profileKey = profileKey,
            gameId = rawGameId,
            gameDisplayName = gameTitle,
            accentColor = rawGameAccent,
            embeddedInPage = true,
            onEnterEditor = { _, tab -> launchEditor(tab) },
            onDirtyStateChanged = { refreshBottomBar() },
            onSaved = { refreshBottomBar() }
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.bgColor(this@GamepadSettingsActivity))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val configBody = controller.buildRootView().apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(configBody)

        val divider = View(this).apply {
            setBackgroundColor(getColor(R.color.ui_divider))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            )
        }
        root.addView(divider)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiKit.surfaceColor(this@GamepadSettingsActivity))
            setPadding(dp(16), dp(10), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val stTv = TextView(this).apply {
            textSize = 12.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        statusTv = stTv
        bottomBar.addView(stTv)

        val resetBtn = Button(this).apply {
            text = tr("重置", "初期化", "Reset")
            textSize = 13f
            isAllCaps = false
            setTextColor(getColor(R.color.ui_text_primary))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            if (rawGameAccent != null) {
                UiKit.styleTonalAccentButton(this, rawGameAccent)
            } else {
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(UiKit.btnTonalColor(this@GamepadSettingsActivity))
                    setStroke(dp(1).coerceAtLeast(1), getColor(R.color.ui_outline))
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                AlertDialog.Builder(this@GamepadSettingsActivity)
                    .setTitle(tr("恢复默认", "初期化", "Reset"))
                    .setMessage(tr("全部开关将恢复关闭状态", "初期状態に戻します", "Restore all defaults"))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        controller.resetToDefaults()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                    .also(::trackDialog)
            }
        }
        bottomBar.addView(resetBtn)

        val saveBgColor = rawGameAccent ?: accentColor
        val onAccentColor = if (rawGameAccent != null) {
            UiKit.contrastOn(saveBgColor)
        } else {
            UiKit.onPrimaryColor(this)
        }

        val primarySaveBtn = Button(this).apply {
            text = tr("保存", "保存", "Save")
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            setTextColor(onAccentColor)
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = UiKit.accentButtonBackground(this@GamepadSettingsActivity, saveBgColor, 10f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                controller.saveAndApply()
                Toast.makeText(
                    this@GamepadSettingsActivity,
                    tr("已保存", "保存済み", "Saved"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        saveBtn = primarySaveBtn
        bottomBar.addView(primarySaveBtn)

        root.addView(bottomBar)
        setContentView(root)
        refreshBottomBar()
        registerModernBackCallback()
    }

    private fun launchEditor(tab: Int) {
        val intent = GamepadEditorActivity.createIntent(this, profileKey, gameTitle, tab, gameId)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_GAMEPAD_EDITOR)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_GAMEPAD_EDITOR) {
            controller.reloadFromStorage()
            refreshBottomBar()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun refreshBottomBar() {
        val stTv = statusTv ?: return
        val scopeName = if (controller.normalizedKey != null && controller.isPerGameMode()) {
            tr("本游戏", "専用", "Game")
        } else {
            tr("全局", "共通", "Global")
        }
        if (controller.isDirty()) {
            stTv.text = tr("$scopeName · 未保存", "$scopeName · 未保存", "$scopeName · Unsaved")
            stTv.setTextColor(getColor(R.color.tone_warning_fg))
            saveBtn?.alpha = 1.0f
        } else {
            stTv.text = tr("$scopeName · 已保存", "$scopeName · 保存済", "$scopeName · Saved")
            stTv.setTextColor(getColor(R.color.ui_text_secondary))
            saveBtn?.alpha = 0.88f
        }
    }

    private fun registerModernBackCallback() {
        if (Build.VERSION.SDK_INT >= 33 && backInvokedCallback == null) {
            val cb = OnBackInvokedCallback {
                handleNavigateBack()
            }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                cb
            )
            backInvokedCallback = cb
        }
    }

    private fun unregisterModernBackCallback() {
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = backInvokedCallback as? OnBackInvokedCallback ?: return
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb)
            } catch (_: Throwable) {
            }
            backInvokedCallback = null
        }
    }

    private fun handleNavigateBack() {
        if (!controller.isDirty()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tr("保存修改？", "保存しますか？", "Save changes?"))
            .setPositiveButton(tr("保存", "保存", "Save")) { _, _ ->
                controller.saveAndApply()
                finish()
            }
            .setNeutralButton(tr("不保存", "破棄", "Discard")) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also(::trackDialog)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleNavigateBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleNavigateBack()
    }

    override fun onResume() {
        super.onResume()
        controller.startPolling { !isFinishing && !isDestroyed }
    }

    override fun onPause() {
        controller.stopPolling()
        super.onPause()
    }

    override fun onDestroy() {
        dismissTrackedDialogs()
        unregisterModernBackCallback()
        if (::controller.isInitialized) {
            controller.stopPolling()
        }
        super.onDestroy()
    }
}
