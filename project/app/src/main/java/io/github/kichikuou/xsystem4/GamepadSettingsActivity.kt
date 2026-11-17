package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class GamepadSettingsActivity : ThemedActivity() {

    companion object {
        const val EXTRA_PROFILE_KEY = "EXTRA_PROFILE_KEY"
        const val EXTRA_GAME_ID = "EXTRA_GAME_ID"
        const val EXTRA_GAME_TITLE = "EXTRA_GAME_TITLE"
    }

    private lateinit var controller: GamepadConfigController
    private var statusTv: TextView? = null
    private var saveBtn: Button? = null
    private var backInvokedCallback: Any? = null

    private fun tr(zh: String, ja: String, en: String): String =
        GamepadBridge.tr(this, zh, ja, en)

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val profileKey = intent.getStringExtra(EXTRA_PROFILE_KEY)?.takeIf { it.isNotBlank() }
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
        val gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE)?.takeIf { it.isNotBlank() }
            ?: gameId?.let { GameCatalog.descriptorFor(this, it).title }

        val pageLabel = getString(R.string.settings_gamepad)
        title = if (!gameTitle.isNullOrBlank()) "$gameTitle · $pageLabel" else pageLabel

        val accentColor = if (!gameId.isNullOrBlank()) {
            UiKit.accentOnSurface(this, GameCatalog.descriptorFor(this, gameId).accentColor)
        } else {
            getColor(R.color.ui_primary)
        }

        controller = GamepadConfigController(
            context = this,
            profileKey = profileKey,
            gameDisplayName = gameTitle,
            embeddedInPage = true,
            onDirtyStateChanged = { refreshBottomBar() },
            onSaved = { refreshBottomBar() }
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.ui_bg))
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

        // Divider above persistent bottom bar
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
            setBackgroundColor(getColor(R.color.ui_surface))
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
            text = tr("恢复默认", "初期化", "Reset")
            textSize = 13f
            isAllCaps = false
            setTextColor(getColor(R.color.ui_text_primary))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(getColor(R.color.ui_btn_tonal))
                setStroke(dp(1).coerceAtLeast(1), getColor(R.color.ui_outline))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                AlertDialog.Builder(this@GamepadSettingsActivity)
                    .setTitle(tr("恢复默认配置", "デフォルトに戻す", "Reset to Defaults"))
                    .setMessage(
                        tr(
                            "确定将当前标签页的手柄与按键配置恢复为默认值吗？",
                            "現在のタブの設定をデフォルトに戻しますか？",
                            "Reset the current tab's gamepad settings to defaults?"
                        )
                    )
                    .setPositiveButton(R.string.ok) { _, _ ->
                        controller.resetCurrentTabToDefaults()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        bottomBar.addView(resetBtn)

        val primarySaveBtn = Button(this).apply {
            text = tr("保存并应用", "保存して適用", "Save & Apply")
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = UiKit.accentButtonBackground(this@GamepadSettingsActivity, accentColor, 10f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                controller.saveAndApply()
                Toast.makeText(
                    this@GamepadSettingsActivity,
                    tr("手柄与按键配置已保存", "設定を保存しました", "Gamepad settings saved"),
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

    private fun refreshBottomBar() {
        val stTv = statusTv ?: return
        val scopeName = if (controller.normalizedKey != null && controller.isPerGameMode()) {
            tr("本游戏独立配置", "ゲーム別設定", "Per-game")
        } else {
            tr("全局默认配置", "共通プリセット", "Global")
        }
        if (controller.isDirty()) {
            stTv.text = tr("$scopeName · 有未保存修改", "$scopeName · 未保存の変更", "$scopeName · Unsaved")
            stTv.setTextColor(getColor(R.color.tone_warning_fg))
            saveBtn?.alpha = 1.0f
        } else {
            stTv.text = tr("$scopeName · 已保存", "$scopeName · 保存済み", "$scopeName · Saved")
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
            .setTitle(tr("保存配置修改？", "変更を保存しますか？", "Save changes?"))
            .setMessage(
                tr(
                    "手柄或自定义按键配置已修改，是否在退出前保存？",
                    "変更が保存されていません。保存して戻りますか？",
                    "You have unsaved gamepad configuration changes. Save before leaving?"
                )
            )
            .setPositiveButton(tr("保存并退出", "保存して戻る", "Save")) { _, _ ->
                controller.saveAndApply()
                finish()
            }
            .setNeutralButton(tr("不保存退出", "破棄して戻る", "Discard")) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        unregisterModernBackCallback()
        controller.stopPolling()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GamepadBridge.isGamepadKeyEvent(event)) {
            if (controller.handlePhysicalKeyEvent(event.keyCode, event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (controller.handlePhysicalMotionEvent(ev)) {
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}
