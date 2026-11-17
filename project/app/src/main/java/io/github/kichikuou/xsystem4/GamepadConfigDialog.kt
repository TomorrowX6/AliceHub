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
 * Overview controller for [GamepadSettingsActivity] and in-game [GamepadConfigDialog].
 * Single unified entry button launches landscape [GamepadEditorActivity].
 */
class GamepadConfigController(
    context: Context,
    profileKey: String?,
    private val gameId: String? = null,
    private val gameDisplayName: String? = null,
    private val accentColor: Int? = null,
    private val embeddedInPage: Boolean = false,
    private val onEnterEditor: ((GamepadProfile, Int) -> Unit)? = null,
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

    private var scopeSwitch: Switch? = null
    private var scopeBadgeTv: TextView? = null
    private var restoreGlobalBtn: TextView? = null
    private var padBadgeTv: TextView? = null

    private var vpadEnabledSwitch: Switch? = null
    private var physEnabledSwitch: Switch? = null
    private var swapAbXySwitch: Switch? = null
    private var autoHideSwitch: Switch? = null
    private var hapticSwitch: Switch? = null

    private var presetBtnVn: TextView? = null
    private var presetBtnRpg: TextView? = null
    private var presetBtnEmpty: TextView? = null

    private var scaleValTv: TextView? = null
    private var scaleSeek: SeekBar? = null
    private var opacityValTv: TextView? = null
    private var opacitySeek: SeekBar? = null

    fun isDirty(): Boolean = dirty

    fun isPerGameMode(): Boolean = perGameMode

    private fun tr(zh: String, ja: String, en: String): String =
        GamepadBridge.tr(uiCtx, zh, ja, en)

    private fun dp(v: Int): Int = UiKit.dpInt(uiCtx, v)

    private fun rawAccent(): Int = accentColor ?: uiCtx.getColor(R.color.ui_primary)

    private fun onAccentColor(fillColor: Int): Int =
        if (accentColor != null) UiKit.contrastOn(fillColor) else uiCtx.getColor(R.color.ui_on_primary)

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
        // Only reset the draft; the per-game / global file is written on save, in the
        // current scope, so cancelling the dialog loses nothing.
        draft = GamepadProfile()
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
            isVerticalScrollBarEnabled = false
        }
        val body = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            val hPad = dp(16)
            val topPad = if (embeddedInPage) dp(12) else dp(8)
            setPadding(hPad, topPad, hPad, dp(20))
        }

        // 1. Scope & live connection status row
        body.addView(buildStatusRow(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        })

        // 2. Per-game override card (when launched for a specific game)
        if (normalizedKey != null) {
            body.addView(buildScopeCard(), cardMarginParams())
        }

        // 3. Master switches + Single Custom Key & Layout Button
        val rawAcc = rawAccent()
        val masterCard = makeCard().apply {
            if (accentColor != null) {
                background = UiKit.accentCard(uiCtx, rawAcc, R.dimen.ui_radius_card, clickable = false)
            }
            vpadEnabledSwitch = addSwitchRow(
                this,
                tr("虚拟手柄", "仮想パッド", "Virtual Pad"),
                tr("屏幕触控按键", "画面タッチ操作", "On-screen overlay")
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

            // Single unified CTA button -> enters Landscape Editor (switches Virtual / Physical inside)
            val openEditorBtn = TextView(uiCtx).apply {
                text = tr("自定义按键与布局", "キー配置・レイアウト編集", "Customize Keys & Layout")
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(onAccentColor(rawAcc))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = UiKit.accentButtonBackground(uiCtx, rawAcc, 12f)
                setOnClickListener {
                    val saved = saveAndApply()
                    val initialTab = if (saved.physical.enabled && !saved.virtual.enabled) {
                        GamepadEditorActivity.TAB_PHYSICAL
                    } else {
                        GamepadEditorActivity.TAB_VIRTUAL
                    }
                    if (onEnterEditor != null) {
                        onEnterEditor.invoke(saved, initialTab)
                    } else {
                        uiCtx.startActivity(
                            GamepadEditorActivity.createIntent(
                                uiCtx,
                                normalizedKey,
                                gameDisplayName,
                                initialTab,
                                gameId
                            )
                        )
                    }
                }
            }
            addView(openEditorBtn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            })
        }
        body.addView(masterCard, cardMarginParams())

        // 4. Concise options card (compact inline sliders so nothing overflows)
        val optCard = makeCard().apply {
            addView(sectionHeader(tr("布局预设", "プリセット", "Preset")))
            val presetRow = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(4))
            }
            val btnVn = makePresetChip("ADV") { confirmApplyPreset(GamepadBridge.PRESET_VN_MOUSE) }
            val btnRpg = makePresetChip("RPG") { confirmApplyPreset(GamepadBridge.PRESET_RPG_DUNGEON) }
            val btnEmpty = makePresetChip(tr("空布局", "空", "Empty")) { confirmApplyPreset(GamepadBridge.PRESET_EMPTY) }
            presetBtnVn = btnVn
            presetBtnRpg = btnRpg
            presetBtnEmpty = btnEmpty
            presetRow.addView(btnVn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            presetRow.addView(btnRpg, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            presetRow.addView(btnEmpty, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
            // Rumble toggle hidden: rumbleEnabled is stored but not implemented natively yet.

            val scalePair = addInlineSliderRow(this, tr("按键大小", "サイズ", "Size"), GamepadBridge.GLOBAL_SCALE_PCT_MAX - GamepadBridge.GLOBAL_SCALE_PCT_MIN) { p ->
                val pct = (p + GamepadBridge.GLOBAL_SCALE_PCT_MIN).coerceIn(GamepadBridge.GLOBAL_SCALE_PCT_MIN, GamepadBridge.GLOBAL_SCALE_PCT_MAX)
                draft = draft.copy(virtual = draft.virtual.copy(globalScalePct = pct))
                scaleValTv?.text = "${pct}%"
                markDirty(true)
            }
            scaleSeek = scalePair.first
            scaleValTv = scalePair.second

            val opPair = addInlineSliderRow(this, tr("透明度", "不透明度", "Opacity"), GamepadBridge.GLOBAL_OPACITY_PCT_MAX - GamepadBridge.GLOBAL_OPACITY_PCT_MIN) { p ->
                val pct = (p + GamepadBridge.GLOBAL_OPACITY_PCT_MIN).coerceIn(GamepadBridge.GLOBAL_OPACITY_PCT_MIN, GamepadBridge.GLOBAL_OPACITY_PCT_MAX)
                draft = draft.copy(virtual = draft.virtual.copy(globalOpacityPct = pct))
                opacityValTv?.text = "${pct}%"
                markDirty(true)
            }
            opacitySeek = opPair.first
            opacityValTv = opPair.second
        }
        body.addView(optCard, cardMarginParams())

        outer.addView(body)
        refreshAllUi()
        return outer
    }

    private fun presetPhysicalBindings(presetId: String): Map<Int, GamepadActionSpec> = when (presetId) {
        GamepadBridge.PRESET_RPG_DUNGEON -> GamepadBridge.rpgPhysicalBindings()
        // Empty layout only clears the virtual pad; keep current controller bindings.
        GamepadBridge.PRESET_EMPTY -> draft.physical.bindings
        else -> GamepadBridge.defaultPhysicalBindings()
    }

    /** Applies a preset, confirming first if it would discard a customised layout or mapping. */
    private fun confirmApplyPreset(presetId: String) {
        val losesLayout = GamepadBridge.presetSwitchLosesLayout(draft.virtual, presetId)
        val losesMapping = draft.physical.bindings != presetPhysicalBindings(presetId) &&
            draft.physical.bindings != GamepadBridge.defaultPhysicalBindings() &&
            draft.physical.bindings != GamepadBridge.rpgPhysicalBindings()
        if (!losesLayout && !losesMapping) {
            applyPreset(presetId)
            return
        }
        val msg = when {
            losesLayout && losesMapping -> tr(
                "当前虚拟按键布局和实体手柄映射的修改都会被替换，确定吗？",
                "現在の仮想パッド配置とコントローラ割当の変更は置き換えられます。よろしいですか？",
                "This replaces your custom virtual layout and controller mapping. Continue?"
            )
            losesLayout -> tr(
                "当前虚拟按键布局的修改会被替换，确定吗？",
                "現在の仮想パッド配置の変更は置き換えられます。よろしいですか？",
                "This replaces your custom virtual layout. Continue?"
            )
            else -> tr(
                "当前实体手柄映射的修改会被替换，确定吗？",
                "現在のコントローラ割当の変更は置き換えられます。よろしいですか？",
                "This replaces your custom controller mapping. Continue?"
            )
        }
        AlertDialog.Builder(uiCtx)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok) { _, _ -> applyPreset(presetId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyPreset(presetId: String) {
        val physBindings = presetPhysicalBindings(presetId)
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
            if (accentColor != null) UiKit.tintSwitch(this, rawAccent())
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
            if (perGameMode && accentColor != null) {
                UiKit.styleAccentChip(tv, rawAccent(), dot = true)
            } else if (perGameMode) {
                UiKit.styleChip(tv, UiKit.Tone.PRIMARY, dot = true)
            } else {
                UiKit.styleChip(tv, UiKit.Tone.NEUTRAL, dot = true)
            }
        }

        vpadEnabledSwitch?.isChecked = draft.virtual.enabled
        physEnabledSwitch?.isChecked = draft.physical.enabled
        swapAbXySwitch?.isChecked = draft.physical.swapAbXy
        autoHideSwitch?.isChecked = draft.physical.autoHideVirtualPad
        hapticSwitch?.isChecked = draft.virtual.hapticFeedback

        scaleSeek?.progress = (draft.virtual.globalScalePct - GamepadBridge.GLOBAL_SCALE_PCT_MIN)
            .coerceIn(0, GamepadBridge.GLOBAL_SCALE_PCT_MAX - GamepadBridge.GLOBAL_SCALE_PCT_MIN)
        scaleValTv?.text = "${draft.virtual.globalScalePct}%"
        opacitySeek?.progress = (draft.virtual.globalOpacityPct - GamepadBridge.GLOBAL_OPACITY_PCT_MIN)
            .coerceIn(0, GamepadBridge.GLOBAL_OPACITY_PCT_MAX - GamepadBridge.GLOBAL_OPACITY_PCT_MIN)
        opacityValTv?.text = "${draft.virtual.globalOpacityPct}%"

        updatePresetChip(presetBtnVn, draft.virtual.presetId == GamepadBridge.PRESET_VN_MOUSE)
        updatePresetChip(presetBtnRpg, draft.virtual.presetId == GamepadBridge.PRESET_RPG_DUNGEON)
        updatePresetChip(presetBtnEmpty, draft.virtual.presetId == GamepadBridge.PRESET_EMPTY)
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
            if (accentColor != null) UiKit.tintSwitch(this, rawAccent())
            setOnCheckedChangeListener { _, isChecked ->
                if (!syncingUi) onChanged(isChecked)
            }
        }
        row.addView(textCol)
        row.addView(sw)
        parent.addView(row)
        return sw
    }

    private fun addInlineSliderRow(
        parent: LinearLayout,
        label: String,
        maxProgress: Int,
        onProgress: (Int) -> Unit
    ): Pair<SeekBar, TextView> {
        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val lbl = TextView(uiCtx).apply {
            text = label
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(uiCtx.getColor(R.color.ui_text_primary))
            layoutParams = LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val seek = SeekBar(uiCtx).apply {
            if (accentColor != null) UiKit.tintSeekBar(this, rawAccent())
            max = maxProgress
            setOnSeekBarChangeListener(simpleSeek(onProgress))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTv = TextView(uiCtx).apply {
            textSize = 12.5f
            gravity = Gravity.END
            setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(lbl)
        row.addView(seek)
        row.addView(valTv)
        parent.addView(row)
        return seek to valTv
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
        val acc = rawAccent()
        tv.setTextColor(if (active) onAccentColor(acc) else uiCtx.getColor(R.color.ui_text_primary))
        tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (active) acc else uiCtx.getColor(R.color.ui_btn_tonal))
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
        gameId: String? = null,
        gameDisplayName: String? = null,
        accentColor: Int? = null,
        onProfileUpdated: (GamepadProfile) -> Unit = {},
        onEnterEditor: ((GamepadProfile, Int) -> Unit)? = null
    ) {
        var dlgRef: AlertDialog? = null
        val controller = GamepadConfigController(
            context = context,
            profileKey = profileKey,
            gameId = gameId,
            gameDisplayName = gameDisplayName,
            accentColor = accentColor,
            embeddedInPage = false,
            onEnterEditor = { saved, initialTab ->
                onProfileUpdated(saved)
                dlgRef?.dismiss()
                if (onEnterEditor != null) {
                    onEnterEditor.invoke(saved, initialTab)
                } else {
                    context.startActivity(
                        GamepadEditorActivity.createIntent(
                            context,
                            profileKey,
                            gameDisplayName,
                            initialTab,
                            gameId
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
