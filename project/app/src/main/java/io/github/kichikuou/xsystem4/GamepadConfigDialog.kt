package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Clean card-based overview controller for [GamepadSettingsActivity] and in-game [GamepadConfigDialog].
 * Detailed layout & button remapping live in landscape [GamepadEditorActivity].
 */
class GamepadConfigController(
    context: Context,
    profileKey: String?,
    private val gameDisplayName: String? = null,
    private val embeddedInPage: Boolean = false,
    private val onEnterLayoutEditor: ((GamepadProfile) -> Unit)? = null,
    private val onEnterPhysicalEditor: ((GamepadProfile) -> Unit)? = null,
    private val onDirtyStateChanged: ((Boolean) -> Unit)? = null,
    private val onSaved: (GamepadProfile) -> Unit = {}
) {
    val uiCtx: Context = if (context is Activity) {
        context
    } else {
        ContextThemeWrapper(AppSettings.wrapThemeContext(context), R.style.Theme_Launcher)
    }
    val normalizedKey: String? = GamepadBridge.normalizeGameProfileKey(profileKey)

    private var perGameMode: Boolean = GamepadBridge.hasPerGameOverride(uiCtx, normalizedKey)
    private var draft: GamepadProfile = GamepadBridge.loadProfile(uiCtx, normalizedKey)
    private var dirty: Boolean = false
    private var syncingUi: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var pollActive = false
    private var pollAliveCheck: () -> Boolean = { true }

    private var scopeRow: LinearLayout? = null
    private var scopeSwitch: Switch? = null
    private var scopeBadgeTv: TextView? = null
    private var restoreGlobalBtn: TextView? = null
    private var padBadgeTv: TextView? = null

    private var vpadEnabledSwitch: Switch? = null
    private var physEnabledSwitch: Switch? = null
    private var swapAbXySwitch: Switch? = null
    private var autoHideSwitch: Switch? = null
    private var hapticSwitch: Switch? = null
    private var rumbleSwitch: Switch? = null

    private var presetBtnVn: TextView? = null
    private var presetBtnRpg: TextView? = null
    private var presetBtnBar: TextView? = null

    private var scaleLabelTv: TextView? = null
    private var scaleSeek: SeekBar? = null
    private var opacityLabelTv: TextView? = null
    private var opacitySeek: SeekBar? = null

    fun isDirty(): Boolean = dirty

    fun isPerGameMode(): Boolean = perGameMode

    fun getDraft(): GamepadProfile = draft

    private fun tr(zh: String, ja: String, en: String): String =
        GamepadBridge.tr(uiCtx, zh, ja, en)

    private fun dp(v: Int): Int = UiKit.dpInt(uiCtx, v)

    private fun markDirty(value: Boolean = true) {
        dirty = value
        onDirtyStateChanged?.invoke(value)
    }

    fun reloadFromStorage() {
        perGameMode = GamepadBridge.hasPerGameOverride(uiCtx, normalizedKey)
        draft = GamepadBridge.loadProfile(uiCtx, normalizedKey)
        markDirty(false)
        refreshAllUi()
    }

    fun saveAndApply(): GamepadProfile {
        val saved = GamepadBridge.saveProfile(uiCtx, draft, normalizedKey, perGameMode)
        draft = saved
        GamepadBridge.syncProfileToNative(saved)
        markDirty(false)
        onSaved(saved)
        return saved
    }

    fun resetToDefaults() {
        if (perGameMode && normalizedKey != null) {
            GamepadBridge.clearPerGameOverride(uiCtx, normalizedKey)
            perGameMode = false
        }
        draft = GamepadProfile()
        GamepadBridge.syncProfileToNative(draft)
        markDirty(true)
        refreshAllUi()
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!pollActive || !pollAliveCheck()) {
                pollActive = false
                return
            }
            refreshPadBadge()
            handler.postDelayed(this, 600L)
        }
    }

    fun startPolling(aliveCheck: () -> Boolean) {
        pollAliveCheck = aliveCheck
        if (!pollActive) {
            pollActive = true
            handler.post(pollRunnable)
        }
    }

    fun stopPolling() {
        pollActive = false
        handler.removeCallbacks(pollRunnable)
    }

    fun buildRootView(): View {
        val outer = ScrollView(uiCtx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
        }
        val body = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            val hPad = dp(16)
            val topPad = if (embeddedInPage) dp(12) else dp(8)
            setPadding(hPad, topPad, hPad, dp(16))
        }

        // 1. Scope & live connection status row
        body.addView(buildStatusRow(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        })

        // 2. Per-game override bar (when a game profile key is present)
        if (normalizedKey != null) {
            val sr = buildScopeCard()
            scopeRow = sr
            body.addView(sr, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            })
        }

        // 3. Master switches (both default OFF)
        val masterCard = makeCard().apply {
            vpadEnabledSwitch = addSwitchRow(
                this,
                tr("虚拟手柄", "仮想パッド", "Virtual Pad"),
                tr("屏幕触控按键", "画面タッチ操作", "On-screen keys")
            ) { checked ->
                draft = draft.copy(virtual = draft.virtual.copy(enabled = checked))
                markDirty(true)
            }
            addDivider(this)
            physEnabledSwitch = addSwitchRow(
                this,
                tr("实体手柄", "コントローラ", "Physical Pad"),
                tr("蓝牙 / USB 手柄映射", "物理入力マッピング", "Hardware mapping")
            ) { checked ->
                draft = draft.copy(physical = draft.physical.copy(enabled = checked))
                markDirty(true)
            }
        }
        body.addView(masterCard, cardMarginParams())

        // 4. Hero CTA: GameSir-style Landscape Editors
        val editorCard = makeCard().apply {
            addView(sectionHeader(tr("横屏按键与布局编辑器", "横画面エディタ", "Landscape Editor")))
            val ctaRow = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val virtualEditBtn = TextView(uiCtx).apply {
                text = tr("自定义虚拟布局", "仮想レイアウト編集", "Virtual Layout")
                gravity = Gravity.CENTER
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = UiKit.accentButtonBackground(uiCtx, uiCtx.getColor(R.color.ui_primary), 12f)
                setOnClickListener {
                    val saved = saveAndApply()
                    onEnterLayoutEditor?.invoke(saved) ?: launchEditorFallback(GamepadEditorActivity.TAB_VIRTUAL)
                }
            }
            ctaRow.addView(virtualEditBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            })

            val physEditBtn = TextView(uiCtx).apply {
                text = tr("实体手柄改键图", "ボタン割当図", "Controller Map")
                gravity = Gravity.CENTER
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(uiCtx.getColor(R.color.ui_primary))
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(uiCtx.getColor(R.color.tone_primary_bg))
                    setStroke(dp(1).coerceAtLeast(1), uiCtx.getColor(R.color.ui_primary))
                }
                setOnClickListener {
                    val saved = saveAndApply()
                    onEnterPhysicalEditor?.invoke(saved) ?: launchEditorFallback(GamepadEditorActivity.TAB_PHYSICAL)
                }
            }
            ctaRow.addView(physEditBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ctaRow)
        }
        body.addView(editorCard, cardMarginParams())

        // 5. Quick options & sliders
        val optCard = makeCard().apply {
            addView(sectionHeader(tr("布局预设", "プリセット", "Preset")))
            val presetRow = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
            val btnVn = makePresetChip("ADV") { applyPreset(GamepadBridge.PRESET_VN_MOUSE) }
            val btnRpg = makePresetChip("RPG") { applyPreset(GamepadBridge.PRESET_RPG_DUNGEON) }
            val btnBar = makePresetChip(tr("极简", "ミニ", "Bar")) { applyPreset(GamepadBridge.PRESET_QUICK_BAR) }
            presetBtnVn = btnVn
            presetBtnRpg = btnRpg
            presetBtnBar = btnBar
            presetRow.addView(btnVn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            presetRow.addView(btnRpg, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            presetRow.addView(btnBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(presetRow)

            addDivider(this)
            swapAbXySwitch = addSwitchRow(this, "A↔B  X↔Y", tr("Switch 布局互换", "Switch配列", "Switch layout")) { checked ->
                draft = draft.copy(physical = draft.physical.copy(swapAbXy = checked))
                markDirty(true)
            }
            addDivider(this)
            autoHideSwitch = addSwitchRow(this, tr("接入隐藏虚拟键", "接続時仮想非表示", "Auto-hide Virtual"), null) { checked ->
                draft = draft.copy(physical = draft.physical.copy(autoHideVirtualPad = checked))
                markDirty(true)
            }
            addDivider(this)
            hapticSwitch = addSwitchRow(this, tr("按键触感", "タッチ振動", "Touch Haptics"), null) { checked ->
                draft = draft.copy(virtual = draft.virtual.copy(hapticFeedback = checked))
                markDirty(true)
            }
            addDivider(this)
            rumbleSwitch = addSwitchRow(this, tr("手柄震动", "振動", "Rumble"), null) { checked ->
                draft = draft.copy(physical = draft.physical.copy(rumbleEnabled = checked))
                markDirty(true)
            }
            addDivider(this)

            scaleLabelTv = TextView(uiCtx).apply {
                textSize = 12.5f
                setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
                setPadding(0, dp(6), 0, dp(2))
            }
            scaleSeek = SeekBar(uiCtx).apply {
                max = 90
                setOnSeekBarChangeListener(simpleSeek { p ->
                    val pct = (p + 60).coerceIn(60, 150)
                    draft = draft.copy(virtual = draft.virtual.copy(globalScalePct = pct))
                    scaleLabelTv?.text = "${tr("按键大小", "サイズ", "Size")} ${pct}%"
                    markDirty(true)
                })
            }
            addView(scaleLabelTv)
            addView(scaleSeek)

            opacityLabelTv = TextView(uiCtx).apply {
                textSize = 12.5f
                setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
                setPadding(0, dp(6), 0, dp(2))
            }
            opacitySeek = SeekBar(uiCtx).apply {
                max = 80
                setOnSeekBarChangeListener(simpleSeek { p ->
                    val pct = (p + 20).coerceIn(20, 100)
                    draft = draft.copy(virtual = draft.virtual.copy(globalOpacityPct = pct))
                    opacityLabelTv?.text = "${tr("透明度", "不透明度", "Opacity")} ${pct}%"
                    markDirty(true)
                })
            }
            addView(opacityLabelTv)
            addView(opacitySeek)
        }
        body.addView(optCard, cardMarginParams())

        outer.addView(body)
        refreshAllUi()
        return outer
    }

    private fun launchEditorFallback(tab: Int) {
        val intent = GamepadEditorActivity.createIntent(uiCtx, normalizedKey, gameDisplayName, tab)
        uiCtx.startActivity(intent)
    }

    private fun applyPreset(presetId: String) {
        val physBindings = if (presetId == GamepadBridge.PRESET_RPG_DUNGEON) {
            GamepadBridge.rpgPhysicalBindings()
        } else {
            GamepadBridge.defaultPhysicalBindings()
        }
        draft = draft.copy(
            virtual = draft.virtual.copy(
                presetId = presetId,
                controls = GamepadBridge.buildPresetControls(presetId)
            ),
            physical = draft.physical.copy(bindings = physBindings)
        )
        markDirty(true)
        refreshAllUi()
    }

    private fun buildStatusRow(): View {
        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scope = TextView(uiCtx).apply {
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        scopeBadgeTv = scope
        row.addView(scope, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            rightMargin = dp(8)
        })

        val padTv = TextView(uiCtx).apply {
            textSize = 11.5f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        padBadgeTv = padTv
        row.addView(padTv)
        return row
    }

    private fun buildScopeCard(): LinearLayout = makeCard().apply {
        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(uiCtx).apply {
            text = tr("本游戏独立配置", "ゲーム別設定", "Per-Game Profile")
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(uiCtx.getColor(R.color.ui_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(uiCtx).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (syncingUi) return@setOnCheckedChangeListener
                perGameMode = isChecked
                if (!isChecked) {
                    draft = GamepadBridge.loadProfile(uiCtx, null)
                }
                markDirty(true)
                refreshAllUi()
            }
        }
        scopeSwitch = sw
        val restore = TextView(uiCtx).apply {
            text = tr("还原全局", "共通へ戻す", "Use Global")
            textSize = 12f
            setTextColor(uiCtx.getColor(R.color.tone_danger_fg))
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener {
                GamepadBridge.clearPerGameOverride(uiCtx, normalizedKey)
                perGameMode = false
                draft = GamepadBridge.loadProfile(uiCtx, null)
                markDirty(false)
                refreshAllUi()
                Toast.makeText(uiCtx, tr("已还原全局配置", "共通設定に戻しました", "Restored global"), Toast.LENGTH_SHORT).show()
            }
        }
        restoreGlobalBtn = restore
        row.addView(label)
        row.addView(restore)
        row.addView(sw)
        addView(row)
    }

    private fun refreshAllUi() {
        syncingUi = true
        scopeSwitch?.isChecked = perGameMode
        restoreGlobalBtn?.visibility = if (perGameMode) View.VISIBLE else View.GONE

        scopeBadgeTv?.let { tv ->
            val label = if (perGameMode) {
                gameDisplayName?.takeIf { it.isNotBlank() } ?: tr("本游戏", "ゲーム別", "Game")
            } else {
                tr("全局默认", "共通", "Global")
            }
            tv.text = label
            UiKit.styleChip(tv, if (perGameMode) UiKit.Tone.PRIMARY else UiKit.Tone.NEUTRAL, dot = true)
        }

        vpadEnabledSwitch?.isChecked = draft.virtual.enabled
        physEnabledSwitch?.isChecked = draft.physical.enabled
        swapAbXySwitch?.isChecked = draft.physical.swapAbXy
        autoHideSwitch?.isChecked = draft.physical.autoHideVirtualPad
        hapticSwitch?.isChecked = draft.virtual.hapticFeedback
        rumbleSwitch?.isChecked = draft.physical.rumbleEnabled

        scaleSeek?.progress = (draft.virtual.globalScalePct - 60).coerceIn(0, 90)
        scaleLabelTv?.text = "${tr("按键大小", "サイズ", "Size")} ${draft.virtual.globalScalePct}%"
        opacitySeek?.progress = (draft.virtual.globalOpacityPct - 20).coerceIn(0, 80)
        opacityLabelTv?.text = "${tr("透明度", "不透明度", "Opacity")} ${draft.virtual.globalOpacityPct}%"

        updatePresetChip(presetBtnVn, draft.virtual.presetId == GamepadBridge.PRESET_VN_MOUSE)
        updatePresetChip(presetBtnRpg, draft.virtual.presetId == GamepadBridge.PRESET_RPG_DUNGEON)
        updatePresetChip(presetBtnBar, draft.virtual.presetId == GamepadBridge.PRESET_QUICK_BAR)
        syncingUi = false

        refreshPadBadge()
    }

    private fun refreshPadBadge() {
        val tv = padBadgeTv ?: return
        val pads = GamepadBridge.queryConnectedControllers(uiCtx)
        if (pads.isNotEmpty()) {
            tv.text = pads.first().name
            UiKit.styleChip(tv, UiKit.Tone.SUCCESS, dot = true)
        } else {
            tv.text = tr("未接入手柄", "未接続", "No controller")
            UiKit.styleChip(tv, UiKit.Tone.NEUTRAL, dot = false)
        }
    }

    private fun makeCard(): LinearLayout = LinearLayout(uiCtx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(uiCtx.getColor(R.color.ui_surface))
            setStroke(dp(1).coerceAtLeast(1), uiCtx.getColor(R.color.ui_outline))
        }
    }

    private fun cardMarginParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(12)
    }

    private fun sectionHeader(text: String): TextView = TextView(uiCtx).apply {
        this.text = text
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
        setPadding(0, 0, 0, dp(8))
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(uiCtx).apply {
            setBackgroundColor(uiCtx.getColor(R.color.ui_divider))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        })
    }

    private fun addSwitchRow(
        parent: LinearLayout,
        title: String,
        subtitle: String?,
        onChanged: (Boolean) -> Unit
    ): Switch {
        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val textCol = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(uiCtx).apply {
            text = title
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(uiCtx.getColor(R.color.ui_text_primary))
        })
        if (!subtitle.isNullOrBlank()) {
            textCol.addView(TextView(uiCtx).apply {
                text = subtitle
                textSize = 12f
                setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
            })
        }
        val sw = Switch(uiCtx).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (!syncingUi) onChanged(isChecked)
            }
        }
        row.addView(textCol)
        row.addView(sw)
        parent.addView(row)
        return sw
    }

    private fun makePresetChip(label: String, onClick: () -> Unit): TextView = TextView(uiCtx).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 12.5f
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setOnClickListener { onClick() }
    }

    private fun updatePresetChip(tv: TextView?, active: Boolean) {
        if (tv == null) return
        val accent = uiCtx.getColor(R.color.ui_primary)
        tv.setTextColor(if (active) Color.WHITE else uiCtx.getColor(R.color.ui_text_primary))
        tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (active) accent else uiCtx.getColor(R.color.ui_btn_tonal))
            if (!active) setStroke(dp(1).coerceAtLeast(1), uiCtx.getColor(R.color.ui_outline))
        }
    }

    private fun simpleSeek(onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !syncingUi) onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
}

object GamepadConfigDialog {

    fun show(
        context: Context,
        profileKey: String?,
        gameDisplayName: String? = null,
        onProfileUpdated: (GamepadProfile) -> Unit = {},
        onEnterLayoutEditor: ((GamepadProfile) -> Unit)? = null,
        onEnterPhysicalEditor: ((GamepadProfile) -> Unit)? = null
    ) {
        var dlgRef: AlertDialog? = null
        val controller = GamepadConfigController(
            context = context,
            profileKey = profileKey,
            gameDisplayName = gameDisplayName,
            embeddedInPage = false,
            onEnterLayoutEditor = { saved ->
                onProfileUpdated(saved)
                dlgRef?.dismiss()
                if (onEnterLayoutEditor != null) {
                    onEnterLayoutEditor.invoke(saved)
                } else {
                    context.startActivity(
                        GamepadEditorActivity.createIntent(
                            context,
                            profileKey,
                            gameDisplayName,
                            GamepadEditorActivity.TAB_VIRTUAL
                        )
                    )
                }
            },
            onEnterPhysicalEditor = { saved ->
                onProfileUpdated(saved)
                dlgRef?.dismiss()
                if (onEnterPhysicalEditor != null) {
                    onEnterPhysicalEditor.invoke(saved)
                } else {
                    context.startActivity(
                        GamepadEditorActivity.createIntent(
                            context,
                            profileKey,
                            gameDisplayName,
                            GamepadEditorActivity.TAB_PHYSICAL
                        )
                    )
                }
            },
            onSaved = onProfileUpdated
        )
        val rootView = controller.buildRootView()

        val built = AlertDialog.Builder(controller.uiCtx)
            .setTitle(GamepadBridge.tr(context, "手柄设置", "パッド設定", "Gamepad"))
            .setView(rootView)
            .setNeutralButton(GamepadBridge.tr(context, "重置", "初期化", "Reset")) { _, _ ->
                controller.resetToDefaults()
                onProfileUpdated(controller.saveAndApply())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(GamepadBridge.tr(context, "保存", "保存", "Save")) { _, _ ->
                controller.saveAndApply()
            }
            .create()
        dlgRef = built

        built.setOnShowListener {
            controller.startPolling { built.isShowing }
        }
        built.setOnDismissListener {
            controller.stopPolling()
        }
        built.show()
    }
}
