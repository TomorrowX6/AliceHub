package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * Reusable interactive controller for both the full-screen [GamepadSettingsActivity]
 * and the in-game [GamepadConfigDialog] modal.
 */
class GamepadConfigController(
    private val context: Context,
    profileKey: String?,
    private val gameDisplayName: String? = null,
    private val embeddedInPage: Boolean = false,
    private val onEnterLayoutEditor: ((GamepadProfile) -> Unit)? = null,
    private val onDirtyStateChanged: ((Boolean) -> Unit)? = null,
    private val onSaved: ((GamepadProfile) -> Unit)? = null
) {
    val uiCtx: Context = ContextThemeWrapper(context, R.style.Theme_Launcher)
    val normalizedKey: String? = GamepadBridge.normalizeGameProfileKey(profileKey)

    private var savedBaseline: GamepadProfile = GamepadBridge.loadProfile(context, normalizedKey)
    var working: GamepadProfile = savedBaseline
        private set
    private var savePerGame: Boolean = normalizedKey != null && working.perGameOverride
    private var dirty: Boolean = false

    private var activeTab: Int = 0
    private var captureForAnyButton: Boolean = false
    private var lastCapturedSlot: Int = -1
    private var lastCapturedMs: Long = 0L

    private val localKeyButtonsMask = IntArray(1) { 0 }
    private val localAxes = FloatArray(8) { 0f }

    private val handler = Handler(Looper.getMainLooper())
    private var pollingActive = false
    private var isShownCheck: () -> Boolean = { true }

    private var contentHost: LinearLayout? = null
    private var tabVpadBtn: Button? = null
    private var tabPhysBtn: Button? = null
    private var scopeSwitch: Switch? = null
    private var scopeDescTv: TextView? = null
    private var previewVpadView: VirtualGamepadView? = null
    private var previewHintTv: TextView? = null
    private var monitorConnTv: TextView? = null
    private var monitorInputTv: TextView? = null
    private var captureBtn: Button? = null

    private fun tr(zh: String, ja: String, en: String): String =
        GamepadBridge.tr(context, zh, ja, en)

    private fun dp(v: Int): Int =
        (v * uiCtx.resources.displayMetrics.density).toInt()

    private fun c(resId: Int): Int = uiCtx.getColor(resId)

    private fun makeCardBg(fillColor: Int, strokeColor: Int, radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            setStroke(dp(1).coerceAtLeast(1), strokeColor)
        }
    }

    private fun cardFill(): Int =
        if (embeddedInPage) c(R.color.ui_surface) else c(R.color.ui_surface_variant)

    private fun markDirty(isDirtyNow: Boolean = true) {
        if (dirty != isDirtyNow) {
            dirty = isDirtyNow
            onDirtyStateChanged?.invoke(dirty)
        }
    }

    fun isDirty(): Boolean = dirty

    fun isPerGameMode(): Boolean = savePerGame

    fun saveAndApply(): GamepadProfile {
        val saved = GamepadBridge.saveProfile(context, working, normalizedKey, savePerGame)
        GamepadBridge.syncProfileToNative(saved)
        savedBaseline = saved
        working = saved
        markDirty(false)
        onSaved?.invoke(saved)
        return saved
    }

    fun resetCurrentTabToDefaults() {
        working = if (activeTab == 0) {
            working.copy(virtual = VirtualPadConfig())
        } else {
            working.copy(physical = PhysicalPadConfig())
        }
        markDirty(true)
        renderCurrentTab()
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (pollingActive && isShownCheck()) {
                if (activeTab == 1) {
                    refreshLiveMonitor()
                }
                handler.postDelayed(this, 80L)
            }
        }
    }

    fun startPolling(shownCheck: () -> Boolean = { true }) {
        isShownCheck = shownCheck
        if (!pollingActive) {
            pollingActive = true
            handler.postDelayed(pollRunnable, 80L)
        }
    }

    fun stopPolling() {
        pollingActive = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun syncPreviewView() {
        previewVpadView?.let { pv ->
            pv.setProfile(working)
            pv.setEditMode(true, null)
        }
    }

    private fun makeSectionTitle(text: String): TextView {
        return TextView(uiCtx).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c(R.color.ui_primary))
            setPadding(dp(2), dp(12), dp(2), dp(6))
        }
    }

    private fun makeSwitchRow(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): Pair<LinearLayout, Switch> {
        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = makeCardBg(cardFill(), c(R.color.ui_outline), dp(10).toFloat())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
        }

        val textCol = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(uiCtx).apply {
            text = title
            textSize = 14f
            setTextColor(c(R.color.ui_text_primary))
        })
        if (!subtitle.isNullOrBlank()) {
            textCol.addView(TextView(uiCtx).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(c(R.color.ui_text_secondary))
                setPadding(0, dp(2), 0, 0)
            })
        }
        row.addView(textCol)

        val sw = Switch(uiCtx).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.isChecked = !sw.isChecked }
        return row to sw
    }

    private fun makeSliderRow(
        title: String,
        minVal: Int,
        maxVal: Int,
        initialVal: Int,
        unitSuffix: String,
        onChanged: (Int) -> Unit
    ): Pair<LinearLayout, SeekBar> {
        val box = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = makeCardBg(cardFill(), c(R.color.ui_outline), dp(10).toFloat())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
        }

        val topRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val labelTv = TextView(uiCtx).apply {
            text = title
            textSize = 13.5f
            setTextColor(c(R.color.ui_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTv = TextView(uiCtx).apply {
            text = "$initialVal$unitSuffix"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c(R.color.ui_primary))
        }
        topRow.addView(labelTv)
        topRow.addView(valTv)
        box.addView(topRow)

        val seek = SeekBar(uiCtx).apply {
            max = (maxVal - minVal).coerceAtLeast(1)
            progress = (initialVal - minVal).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = minVal + p
                    valTv.text = "$v$unitSuffix"
                    if (fromUser) onChanged(v)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        box.addView(seek)
        return box to seek
    }

    private fun makePickerRow(
        title: String,
        valueText: String,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val row = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = makeCardBg(cardFill(), c(R.color.ui_outline), dp(10).toFloat())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        val titleTv = TextView(uiCtx).apply {
            text = title
            textSize = 14f
            setTextColor(c(R.color.ui_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueTv = TextView(uiCtx).apply {
            text = valueText
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c(R.color.ui_primary))
            gravity = Gravity.END
        }
        row.addView(titleTv)
        row.addView(valueTv)
        return row to valueTv
    }

    private fun updateScopeText() {
        scopeDescTv?.text = if (savePerGame) {
            tr(
                "已开启本游戏独立配置，保存仅作用于当前游戏",
                "ゲーム別設定が有効（このゲームのみ保存）",
                "Per-game override ON (saved only for this game)"
            )
        } else {
            tr(
                "当前共享全局默认配置（打开右侧开关可为本游戏单独定制）",
                "共通プリセットを使用中（右のスイッチで個別設定）",
                "Using Global Default (turn switch ON to customize per game)"
            )
        }
    }

    fun buildRootView(): View {
        val outer = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            if (!embeddedInPage) {
                setPadding(dp(14), dp(10), dp(14), dp(6))
            }
        }

        // Fixed top chrome (scope card + tab switcher)
        val topHeader = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            if (embeddedInPage) {
                setPadding(dp(16), dp(10), dp(16), dp(6))
            }
        }

        if (normalizedKey != null) {
            val scopeBox = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = makeCardBg(c(R.color.tone_primary_bg), c(R.color.ui_outline), dp(12).toFloat())
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(8)
                layoutParams = lp
            }
            val textCol = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val labelName = gameDisplayName?.takeIf { it.isNotBlank() } ?: normalizedKey
            textCol.addView(TextView(uiCtx).apply {
                text = tr("配置作用域 · $labelName", "プロファイル · $labelName", "Profile · $labelName")
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(c(R.color.tone_primary_fg))
            })
            scopeDescTv = TextView(uiCtx).apply {
                textSize = 11.5f
                setTextColor(c(R.color.ui_text_secondary))
                setPadding(0, dp(2), 0, 0)
            }
            textCol.addView(scopeDescTv)
            scopeBox.addView(textCol)
            scopeSwitch = Switch(uiCtx).apply {
                isChecked = savePerGame
            }
            scopeBox.addView(scopeSwitch)
            topHeader.addView(scopeBox)
        }
        updateScopeText()

        val tabBar = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
        }
        tabVpadBtn = Button(uiCtx).apply {
            text = tr("屏幕虚拟手柄", "仮想ゲームパッド", "Virtual Gamepad")
            textSize = 13f
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        tabPhysBtn = Button(uiCtx).apply {
            text = tr("实体手柄适配", "物理コントローラ", "Physical Controller")
            textSize = 13f
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tabBar.addView(tabVpadBtn)
        tabBar.addView(tabPhysBtn)
        topHeader.addView(tabBar)
        outer.addView(topHeader)

        val contentScroll = ScrollView(uiCtx).apply {
            layoutParams = if (embeddedInPage) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            } else {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380))
            }
        }
        val host = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            if (embeddedInPage) {
                setPadding(dp(16), dp(2), dp(16), dp(16))
            }
        }
        contentHost = host
        contentScroll.addView(host)
        outer.addView(contentScroll)

        scopeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (savePerGame == isChecked) return@setOnCheckedChangeListener
            savePerGame = isChecked
            if (!isChecked) {
                working = GamepadBridge.loadProfile(context, null)
            } else {
                working = working.copy(perGameOverride = true)
            }
            markDirty(true)
            updateScopeText()
            renderCurrentTab()
        }

        tabVpadBtn?.setOnClickListener {
            if (activeTab != 0) {
                activeTab = 0
                captureForAnyButton = false
                renderCurrentTab()
            }
        }
        tabPhysBtn?.setOnClickListener {
            if (activeTab != 1) {
                activeTab = 1
                renderCurrentTab()
            }
        }

        renderCurrentTab()
        return outer
    }

    private fun styleTabButtons() {
        fun applyBtnStyle(btn: Button?, selected: Boolean) {
            btn ?: return
            btn.background = if (selected) {
                makeCardBg(c(R.color.ui_primary), c(R.color.ui_primary), dp(10).toFloat())
            } else {
                makeCardBg(c(R.color.ui_btn_tonal), c(R.color.ui_outline), dp(10).toFloat())
            }
            btn.setTextColor(if (selected) c(R.color.ui_on_primary) else c(R.color.ui_text_primary))
        }
        applyBtnStyle(tabVpadBtn, activeTab == 0)
        applyBtnStyle(tabPhysBtn, activeTab == 1)
    }

    private fun addCustomControlInteractive() {
        val slotId = GamepadBridge.nextFreeCustomSlotId(working.virtual.controls)
        if (slotId < 0) {
            Toast.makeText(
                context,
                tr("已达到自定义按键数量上限", "カスタムボタンの上限に達しました", "Custom button limit reached"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        KeyPickerDialog.showActionPicker(
            context,
            tr("选择新增按键功能", "追加ボタンの機能を選択", "Choose New Button Action"),
            GamepadActionSpec(GamepadBridge.ACTION_VK, GamepadBridge.VK_RETURN)
        ) { actionSpec, shortBadge ->
            val customIndex = slotId - 12
            val nx = (0.30f + (customIndex % 5) * 0.10f).coerceIn(0.10f, 0.90f)
            val ny = (0.16f + (customIndex / 5) * 0.11f).coerceIn(0.10f, 0.88f)
            val newCtrl = VirtualControlSpec(
                id = "custom_$slotId",
                slotId = slotId,
                kind = VirtualControlKind.BUTTON,
                label = shortBadge.ifBlank { "K${customIndex + 1}" },
                xNorm = nx,
                yNorm = ny,
                scale = 0.95f,
                opacity = 1.0f,
                visible = true,
                shape = VirtualButtonShape.PILL,
                action = actionSpec,
                isCustom = true
            )
            val updated = working.virtual.controls.toMutableList().apply { add(newCtrl) }
            working = working.copy(virtual = working.virtual.copy(enabled = true, controls = updated))
            markDirty(true)
            renderCurrentTab()
        }
    }

    private fun openControlEditorForIndex(index: Int) {
        val cSpec = working.virtual.controls.getOrNull(index) ?: return
        KeyPickerDialog.showVirtualControlEditor(
            context = context,
            control = cSpec,
            onSave = { edited ->
                val next = working.virtual.controls.toMutableList()
                next[index] = edited
                working = working.copy(virtual = working.virtual.copy(controls = next))
                markDirty(true)
                renderCurrentTab()
            },
            onDelete = if (cSpec.isCustom) {
                {
                    val next = working.virtual.controls.toMutableList()
                    next.removeAt(index)
                    working = working.copy(virtual = working.virtual.copy(controls = next))
                    markDirty(true)
                    renderCurrentTab()
                }
            } else null
        )
    }

    fun renderCurrentTab() {
        val host = contentHost ?: return
        host.removeAllViews()
        previewVpadView = null
        previewHintTv = null
        monitorConnTv = null
        monitorInputTv = null
        captureBtn = null
        styleTabButtons()
        if (activeTab == 0) {
            buildVpadTab(host)
        } else {
            buildPhysicalTab(host)
        }
    }

    private fun buildEmbeddedPreviewCard(host: LinearLayout) {
        val card = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = makeCardBg(cardFill(), c(R.color.ui_outline), dp(12).toFloat())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }

        val headerTv = TextView(uiCtx).apply {
            text = tr(
                "可视化屏幕预览（可直接拖拽挪位 · 点按任意按键编辑）",
                "画面レイアウトプレビュー（ドラッグで移動・タップで編集）",
                "Interactive Screen Preview (Drag to move · Tap any control to edit)"
            )
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c(R.color.ui_text_primary))
            setPadding(dp(2), 0, dp(2), dp(6))
        }
        card.addView(headerTv)

        val maxPreviewH = dp(260)
        val previewFrame = object : FrameLayout(uiCtx) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val w = MeasureSpec.getSize(widthMeasureSpec)
                val targetH = (w * 9f / 16f).toInt().coerceIn(dp(150), maxPreviewH)
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY)
                )
            }
        }.apply {
            background = makeCardBg(0xFF131722.toInt(), 0x5538BDF8.toInt(), dp(10).toFloat())
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Subtle simulated game canvas label inside preview
        val bgHint = TextView(uiCtx).apply {
            text = tr("16:9 横屏游戏画面区域", "16:9 ゲーム画面プレビュー", "16:9 Landscape Game Area")
            textSize = 11f
            setTextColor(0x44FFFFFF)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewFrame.addView(bgHint)

        val hintTv = TextView(uiCtx).apply {
            text = if (working.virtual.enabled) {
                tr(
                    "提示：按住任意虚拟控件拖动到理想位置，松开即生效",
                    "ヒント：コントロールをドラッグして配置調整できます",
                    "Tip: Drag controls here to position, or tap to edit action/size"
                )
            } else {
                tr(
                    "虚拟手柄当前已关闭（打开下方“启用屏幕虚拟手柄”后在游戏中生效）",
                    "現在仮想ゲームパッドは無効です（下から有効化できます）",
                    "Virtual gamepad is currently OFF (enable below to show in-game)"
                )
            }
            textSize = 11.5f
            setTextColor(c(R.color.ui_text_secondary))
            setPadding(dp(2), dp(6), dp(2), 0)
        }
        previewHintTv = hintTv

        val vpad = VirtualGamepadView(uiCtx).apply {
            setProfile(working)
            setEditMode(true, null)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            listener = object : VirtualGamepadView.Listener {
                override fun onControlTappedInEditor(control: VirtualControlSpec) {
                    val idx = working.virtual.controls.indexOfFirst { it.id == control.id }
                    if (idx >= 0) {
                        setSelectedControlId(control.id)
                        openControlEditorForIndex(idx)
                    }
                }

                override fun onControlMovedInEditor(control: VirtualControlSpec) {
                    val next = working.virtual.controls.map {
                        if (it.id == control.id) control else it
                    }
                    working = working.copy(virtual = working.virtual.copy(controls = next))
                    markDirty(true)
                    hintTv.text = "${control.label} · X:${(control.xNorm * 100).toInt()}%  Y:${(control.yNorm * 100).toInt()}%"
                }
            }
        }
        previewVpadView = vpad
        previewFrame.addView(vpad)
        card.addView(previewFrame)
        card.addView(hintTv)
        host.addView(card)
    }

    private fun buildVpadTab(host: LinearLayout) {
        if (embeddedInPage) {
            buildEmbeddedPreviewCard(host)
        }

        val vp = working.virtual
        host.addView(makeSwitchRow(
            tr("启用屏幕虚拟手柄", "仮想ゲームパッドを表示", "Enable Virtual Gamepad"),
            tr("游戏画面叠加半透明十字键、摇杆与自定义热键", "十字キー・スティック・ホットキーを重ねて表示", "Overlay touch D-pad, stick, and custom hotkeys"),
            vp.enabled
        ) { checked ->
            working = working.copy(virtual = working.virtual.copy(enabled = checked))
            markDirty(true)
            syncPreviewView()
        }.first)

        host.addView(makeSwitchRow(
            tr("接入实体手柄时自动隐藏", "物理コントローラ接続時に自動非表示", "Auto-hide When Controller Connected"),
            tr("检测到手柄接入后隐藏触屏按键，断开后自动恢复", "物理パッド使用時は自動で隠します", "Hide virtual pad automatically when physical pad is active"),
            working.physical.autoHideVirtualPad
        ) { checked ->
            working = working.copy(physical = working.physical.copy(autoHideVirtualPad = checked))
            markDirty(true)
        }.first)

        host.addView(makeSwitchRow(
            tr("触控震动反馈", "バイブレーション", "Haptic Feedback"),
            null,
            vp.hapticFeedback
        ) { checked ->
            working = working.copy(virtual = working.virtual.copy(hapticFeedback = checked))
            markDirty(true)
        }.first)

        host.addView(makeSwitchRow(
            tr("布局编辑器吸附网格", "グリッドスナップ", "Grid Snap in Layout Editor"),
            null,
            vp.gridSnap
        ) { checked ->
            working = working.copy(virtual = working.virtual.copy(gridSnap = checked))
            markDirty(true)
            syncPreviewView()
        }.first)

        host.addView(makeSectionTitle(tr("一键预设方案", "プリセット", "Layout Presets")))

        val presetRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
        }
        val presets = listOf(
            GamepadBridge.PRESET_VN_MOUSE to tr("鼠键畅玩", "マウス・ADV", "VN / Mouse"),
            GamepadBridge.PRESET_RPG_DUNGEON to tr("迷宫 RPG", "ダンジョンRPG", "RPG / Grid"),
            GamepadBridge.PRESET_QUICK_BAR to tr("极简热键条", "クイックバー", "Quick Bar")
        )
        for ((idx, item) in presets.withIndex()) {
            val (pid, title) = item
            val isSelected = vp.presetId == pid
            val btn = Button(uiCtx).apply {
                text = title
                textSize = 12f
                isAllCaps = false
                background = if (isSelected) {
                    makeCardBg(c(R.color.ui_primary), c(R.color.ui_primary), dp(8).toFloat())
                } else {
                    makeCardBg(c(R.color.ui_btn_tonal), c(R.color.ui_outline), dp(8).toFloat())
                }
                setTextColor(if (isSelected) c(R.color.ui_on_primary) else c(R.color.ui_text_primary))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx > 0) marginStart = dp(6)
                }
                setOnClickListener {
                    working = working.copy(
                        virtual = working.virtual.copy(
                            enabled = true,
                            presetId = pid,
                            controls = GamepadBridge.buildPresetControls(pid)
                        )
                    )
                    markDirty(true)
                    renderCurrentTab()
                }
            }
            presetRow.addView(btn)
        }
        host.addView(presetRow)

        host.addView(makeSliderRow(
            tr("全局大小", "全体サイズ", "Global Scale"),
            60, 150, vp.globalScalePct, "%"
        ) { v ->
            working = working.copy(virtual = working.virtual.copy(globalScalePct = v))
            markDirty(true)
            syncPreviewView()
        }.first)

        host.addView(makeSliderRow(
            tr("全局透明度", "全体不透明度", "Global Opacity"),
            20, 100, vp.globalOpacityPct, "%"
        ) { v ->
            working = working.copy(virtual = working.virtual.copy(globalOpacityPct = v))
            markDirty(true)
            syncPreviewView()
        }.first)

        host.addView(makeSectionTitle(tr("可视化编辑与自定义按键", "レイアウト・カスタムボタン", "Layout & Custom Controls")))

        val toolsRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        if (onEnterLayoutEditor != null) {
            val editBtn = Button(uiCtx).apply {
                text = tr("进入屏幕拖拽布局", "画面上で配置を編集", "Screen Layout Editor")
                textSize = 12.5f
                isAllCaps = false
                background = makeCardBg(c(R.color.ui_primary), c(R.color.ui_primary), dp(8).toFloat())
                setTextColor(c(R.color.ui_on_primary))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    working = working.copy(virtual = working.virtual.copy(enabled = true))
                    val saved = saveAndApply()
                    onEnterLayoutEditor.invoke(saved)
                }
            }
            toolsRow.addView(editBtn)
        }

        val addBtn = Button(uiCtx).apply {
            text = tr("+ 添加自定义按键", "+ カスタムボタン追加", "+ Add Custom Key")
            textSize = 12.5f
            isAllCaps = false
            background = makeCardBg(c(R.color.ui_btn_tonal), c(R.color.ui_outline), dp(8).toFloat())
            setTextColor(c(R.color.ui_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { addCustomControlInteractive() }
        }
        toolsRow.addView(addBtn)
        host.addView(toolsRow)

        for (i in vp.controls.indices) {
            val ctrl = vp.controls[i]
            val actionDesc = when (ctrl.kind) {
                VirtualControlKind.DPAD, VirtualControlKind.STICK ->
                    GamepadBridge.stickModeLabel(context, ctrl.stickMode)
                VirtualControlKind.BUTTON ->
                    GamepadBridge.formatActionLabel(context, ctrl.action)
            }
            val visTag = if (ctrl.visible) "" else tr(" · [已隐藏]", " · [非表示]", " · [Hidden]")
            val customTag = if (ctrl.isCustom) tr(" · 自定义", " · カスタム", " · Custom") else ""
            val leftTitle = "${ctrl.label.ifBlank { ctrl.id }}$visTag$customTag"
            val rowPair = makePickerRow(leftTitle, actionDesc) {
                openControlEditorForIndex(i)
            }
            if (!ctrl.visible) {
                rowPair.first.alpha = 0.55f
            }
            host.addView(rowPair.first)
        }

        if (!embeddedInPage) {
            val resetBtn = Button(uiCtx).apply {
                text = tr("恢复默认虚拟手柄布局", "仮想パッドを初期化", "Reset Virtual Pad Layout")
                textSize = 12.5f
                isAllCaps = false
                background = makeCardBg(c(R.color.ui_btn_tonal), c(R.color.ui_outline), dp(8).toFloat())
                setTextColor(c(R.color.ui_text_primary))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(6)
                layoutParams = lp
                setOnClickListener {
                    working = working.copy(virtual = VirtualPadConfig())
                    markDirty(true)
                    renderCurrentTab()
                }
            }
            host.addView(resetBtn)
        }
    }

    fun refreshLiveMonitor() {
        if (activeTab != 1 || monitorConnTv == null || monitorInputTv == null) return
        val devices = GamepadBridge.queryConnectedControllers(context)
        var sdlCount = 0
        var buttonsMask = localKeyButtonsMask[0]
        var lx = localAxes[0]
        var ly = localAxes[1]
        var rx = localAxes[2]
        var ry = localAxes[3]
        var lt = localAxes[4]
        var rt = localAxes[5]
        val sdlNames = ArrayList<String>()

        if (GameModifierBridge.ensureLoaded()) {
            try {
                val statusStr = GamepadBridge.nativeGetControllerStatusJson()
                if (statusStr.isNotBlank()) {
                    val obj = JSONObject(statusStr)
                    sdlCount = obj.optInt("count", 0)
                    buttonsMask = obj.optInt("buttonsMask", 0) or localKeyButtonsMask[0]
                    val nlx = obj.optDouble("lx", 0.0).toFloat()
                    val nly = obj.optDouble("ly", 0.0).toFloat()
                    val nrx = obj.optDouble("rx", 0.0).toFloat()
                    val nry = obj.optDouble("ry", 0.0).toFloat()
                    val nlt = obj.optDouble("lt", 0.0).toFloat()
                    val nrt = obj.optDouble("rt", 0.0).toFloat()
                    if (abs(nlx) > abs(lx)) lx = nlx
                    if (abs(nly) > abs(ly)) ly = nly
                    if (abs(nrx) > abs(rx)) rx = nrx
                    if (abs(nry) > abs(ry)) ry = nry
                    if (nlt > lt) lt = nlt
                    if (nrt > rt) rt = nrt
                    val arr = obj.optJSONArray("controllers")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val nm = item.optString("name", "")
                            if (nm.isNotBlank()) sdlNames.add(nm)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        monitorConnTv?.text = if (devices.isEmpty() && sdlNames.isEmpty()) {
            tr(
                "未检测到实体手柄（请通过蓝牙或 USB/OTG 连接 Xbox / PS / Switch / 通用 HID 手柄）",
                "物理コントローラ未接続（Bluetooth / USB で接続してください）",
                "No controller detected (connect Xbox / PS / Switch / USB HID pad)"
            )
        } else {
            val sb = StringBuilder()
            val displayList = if (devices.isNotEmpty()) {
                devices.map { "• ${it.name} (VID:${"%04X".format(it.vendorId)} PID:${"%04X".format(it.productId)})" }
            } else {
                sdlNames.map { "• $it" }
            }
            sb.append(
                tr(
                    "已连接 ${maxOf(devices.size, sdlCount)} 个手柄：",
                    "${maxOf(devices.size, sdlCount)} 台接続中：",
                    "${maxOf(devices.size, sdlCount)} controller(s) connected:"
                )
            )
            for (line in displayList) {
                sb.append("\n").append(line)
            }
            sb.toString()
        }

        val activeNames = ArrayList<String>()
        for (slot in 0 until GamepadBridge.NR_PAD_SLOTS) {
            if ((buttonsMask and (1 shl slot)) != 0) {
                activeNames.add(GamepadBridge.physicalSlotName(context, slot))
            }
        }
        val nowMs = System.currentTimeMillis()
        val lastCapturedInfo = if (lastCapturedSlot in 0 until GamepadBridge.NR_PAD_SLOTS && nowMs - lastCapturedMs < 3500L) {
            "  |  " + tr("最近按键", "直近入力", "Last") + ": " +
                GamepadBridge.physicalSlotName(context, lastCapturedSlot)
        } else {
            ""
        }
        val pressedStr = if (activeNames.isEmpty()) {
            tr("（无按键按下）", "（押下なし）", "(none pressed)")
        } else {
            activeNames.joinToString(", ")
        }
        monitorInputTv?.text = String.format(
            Locale.US,
            "LX:%+.2f  LY:%+.2f  |  RX:%+.2f  RY:%+.2f  |  LT:%.2f  RT:%.2f\n%s: %s%s",
            lx, ly, rx, ry, lt, rt,
            tr("实时按键", "押下状態", "Pressed"),
            pressedStr,
            lastCapturedInfo
        )
    }

    private fun pickStickMode(title: String, currentMode: Int, onChosen: (Int) -> Unit) {
        val modes = listOf(
            GamepadBridge.STICK_MOUSE,
            GamepadBridge.STICK_ARROWS,
            GamepadBridge.STICK_WASD,
            GamepadBridge.STICK_WHEEL,
            GamepadBridge.STICK_JOYPAD,
            GamepadBridge.STICK_NONE
        )
        val labels = modes.map { GamepadBridge.stickModeLabel(context, it) }.toTypedArray()
        val checked = modes.indexOf(currentMode).coerceAtLeast(0)
        AlertDialog.Builder(uiCtx)
            .setTitle(title)
            .setSingleChoiceItems(labels, checked) { dlg, which ->
                dlg.dismiss()
                onChosen(modes[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openPhysicalSlotPicker(slot: Int) {
        val cur = working.physical.bindings[slot] ?: GamepadActionSpec()
        KeyPickerDialog.showActionPicker(
            context,
            "${GamepadBridge.physicalSlotName(context, slot)} →",
            cur
        ) { picked, _ ->
            val map = working.physical.bindings.toMutableMap()
            map[slot] = picked
            working = working.copy(physical = working.physical.copy(bindings = map))
            markDirty(true)
            renderCurrentTab()
        }
    }

    private fun updateCaptureBtnLabel() {
        captureBtn?.text = if (captureForAnyButton) {
            tr(
                "请立即按下手柄上的任意按键进行改键…（点此取消）",
                "変更したいコントローラボタンを押してください…",
                "Press ANY controller button now to remap… (Tap to cancel)"
            )
        } else {
            tr(
                "一键捕捉物理按键并改键",
                "物理ボタン押下で割り当て変更",
                "Press Physical Button to Remap"
            )
        }
    }

    private fun buildPhysicalTab(host: LinearLayout) {
        val ph = working.physical

        val monitorCard = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = makeCardBg(c(R.color.tone_success_bg), c(R.color.ui_outline), dp(10).toFloat())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        monitorConnTv = TextView(uiCtx).apply {
            textSize = 12.5f
            setTextColor(c(R.color.tone_success_fg))
        }
        monitorInputTv = TextView(uiCtx).apply {
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(c(R.color.ui_text_primary))
            setPadding(0, dp(6), 0, dp(6))
        }
        captureBtn = Button(uiCtx).apply {
            textSize = 12.5f
            isAllCaps = false
            background = makeCardBg(c(R.color.ui_primary), c(R.color.ui_primary), dp(8).toFloat())
            setTextColor(c(R.color.ui_on_primary))
            setOnClickListener {
                captureForAnyButton = !captureForAnyButton
                updateCaptureBtnLabel()
            }
        }
        updateCaptureBtnLabel()
        monitorCard.addView(monitorConnTv)
        monitorCard.addView(monitorInputTv)
        monitorCard.addView(captureBtn)
        host.addView(monitorCard)
        refreshLiveMonitor()

        host.addView(makeSwitchRow(
            tr("启用实体手柄映射", "物理コントローラマッピングを有効化", "Enable Physical Controller Mapping"),
            tr("支持 Xbox / PlayStation / Switch / 通用 USB 与蓝牙手柄", "Xbox / PS / Switch / HID 汎用パッド対応", "Supports Xbox, PlayStation, Switch, and generic HID gamepads"),
            ph.enabled
        ) { checked ->
            working = working.copy(physical = working.physical.copy(enabled = checked))
            markDirty(true)
        }.first)

        host.addView(makeSwitchRow(
            tr("Switch / 日式布局 (互换 A/B 与 X/Y)", "任天堂レイアウト (A/B・X/Y入替)", "Nintendo / Switch Layout (Swap A/B & X/Y)"),
            tr("开启后按 Switch 实体键位习惯映射 A↔B、X↔Y", "A↔B と X↔Y の入力を自動で入れ替えます", "Swaps A↔B and X↔Y face buttons to match Switch labels"),
            ph.swapAbXy
        ) { checked ->
            working = working.copy(physical = working.physical.copy(swapAbXy = checked))
            markDirty(true)
        }.first)

        host.addView(makeSwitchRow(
            tr("启用手柄震动反馈 (Rumble)", "コントローラ振動 (Rumble)", "Controller Rumble Vibration"),
            tr("响应游戏中 JoypadQuake 震动指令", "ゲーム内の振動演出を手柄に転送します", "Forwards game JoypadQuake rumble calls to controller"),
            ph.rumbleEnabled
        ) { checked ->
            working = working.copy(physical = working.physical.copy(rumbleEnabled = checked))
            markDirty(true)
        }.first)

        host.addView(makeSectionTitle(tr("摇杆与十字键功能", "スティック・十字キー設定", "Sticks & D-Pad Modes")))

        host.addView(makePickerRow(
            tr("左摇杆功能", "左スティック", "Left Stick"),
            GamepadBridge.stickModeLabel(context, ph.leftStickMode)
        ) {
            pickStickMode(tr("左摇杆功能", "左スティック", "Left Stick"), ph.leftStickMode) { mode ->
                working = working.copy(physical = working.physical.copy(leftStickMode = mode))
                markDirty(true)
                renderCurrentTab()
            }
        }.first)

        host.addView(makePickerRow(
            tr("右摇杆功能", "右スティック", "Right Stick"),
            GamepadBridge.stickModeLabel(context, ph.rightStickMode)
        ) {
            pickStickMode(tr("右摇杆功能", "右スティック", "Right Stick"), ph.rightStickMode) { mode ->
                working = working.copy(physical = working.physical.copy(rightStickMode = mode))
                markDirty(true)
                renderCurrentTab()
            }
        }.first)

        host.addView(makePickerRow(
            tr("实体十字键 (D-Pad)", "十字キー (D-Pad)", "D-Pad"),
            if (ph.dpadMode == GamepadBridge.STICK_NONE) {
                tr("独立自定义按键（见下方列表）", "個別ボタン割当", "Per-Direction Custom Keys")
            } else {
                GamepadBridge.stickModeLabel(context, ph.dpadMode)
            }
        ) {
            pickStickMode(tr("实体十字键 (D-Pad)", "十字キー (D-Pad)", "D-Pad"), ph.dpadMode) { mode ->
                working = working.copy(physical = working.physical.copy(dpadMode = mode))
                markDirty(true)
                renderCurrentTab()
            }
        }.first)

        host.addView(makeSliderRow(
            tr("摇杆死区 (防漂移)", "デッドゾーン", "Stick Deadzone"),
            5, 45, ph.deadzonePct, "%"
        ) { v ->
            working = working.copy(physical = working.physical.copy(deadzonePct = v))
            markDirty(true)
        }.first)

        host.addView(makeSliderRow(
            tr("摇杆鼠标指针速度", "カーソル移動速度", "Cursor Speed"),
            30, 250, ph.cursorSpeedPct, "%"
        ) { v ->
            working = working.copy(physical = working.physical.copy(cursorSpeedPct = v))
            markDirty(true)
        }.first)

        host.addView(makeSectionTitle(tr("按键映射（点击修改任意手柄按键）", "ボタン割り当て", "Button Bindings (Tap to Remap)")))

        val quickPresetRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
        }
        quickPresetRow.addView(Button(uiCtx).apply {
            text = tr("鼠键 ADV 默认", "マウス標準", "VN / Mouse Preset")
            textSize = 12f
            isAllCaps = false
            background = makeCardBg(c(R.color.ui_btn_tonal), c(R.color.ui_outline), dp(8).toFloat())
            setTextColor(c(R.color.ui_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                working = working.copy(
                    physical = working.physical.copy(
                        leftStickMode = GamepadBridge.STICK_MOUSE,
                        rightStickMode = GamepadBridge.STICK_WHEEL,
                        dpadMode = GamepadBridge.STICK_ARROWS,
                        bindings = GamepadBridge.defaultPhysicalBindings()
                    )
                )
                markDirty(true)
                renderCurrentTab()
            }
        })
        quickPresetRow.addView(Button(uiCtx).apply {
            text = tr("迷宫 RPG 预设", "迷宮RPG標準", "RPG / Dungeon Preset")
            textSize = 12f
            isAllCaps = false
            background = makeCardBg(c(R.color.ui_btn_tonal), c(R.color.ui_outline), dp(8).toFloat())
            setTextColor(c(R.color.ui_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                working = working.copy(
                    physical = working.physical.copy(
                        leftStickMode = GamepadBridge.STICK_MOUSE,
                        rightStickMode = GamepadBridge.STICK_WHEEL,
                        dpadMode = GamepadBridge.STICK_ARROWS,
                        bindings = GamepadBridge.rpgPhysicalBindings()
                    )
                )
                markDirty(true)
                renderCurrentTab()
            }
        })
        host.addView(quickPresetRow)

        for (slot in 0 until GamepadBridge.NR_PAD_SLOTS) {
            val isDpadSlot = slot in GamepadBridge.PAD_DPAD_UP..GamepadBridge.PAD_DPAD_RIGHT
            val spec = ph.bindings[slot] ?: GamepadActionSpec()
            val label = if (isDpadSlot && ph.dpadMode != GamepadBridge.STICK_NONE) {
                "${GamepadBridge.formatActionLabel(context, spec)} (${tr("当前由上方D-Pad模式接管", "D-Padモード優先", "D-Pad mode active")})"
            } else {
                GamepadBridge.formatActionLabel(context, spec)
            }
            val rowPair = makePickerRow(GamepadBridge.physicalSlotName(context, slot), label) {
                openPhysicalSlotPicker(slot)
            }
            if (isDpadSlot && ph.dpadMode != GamepadBridge.STICK_NONE) {
                rowPair.first.alpha = 0.6f
            }
            host.addView(rowPair.first)
        }
    }

    fun handlePhysicalKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (!GamepadBridge.isGamepadKeyEvent(event)) {
            return false
        }
        val slot = GamepadBridge.androidKeyCodeToPadSlot(keyCode) ?: return false
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (isDown) {
            localKeyButtonsMask[0] = localKeyButtonsMask[0] or (1 shl slot)
            lastCapturedSlot = slot
            lastCapturedMs = System.currentTimeMillis()
        } else if (event.action == KeyEvent.ACTION_UP) {
            localKeyButtonsMask[0] = localKeyButtonsMask[0] and (1 shl slot).inv()
        }
        if (GameModifierBridge.ensureLoaded()) {
            try {
                GamepadBridge.nativeSetAndroidControllerKey(event.deviceId, keyCode, isDown)
            } catch (_: Throwable) {
            }
        }
        var consumedCapture = false
        if (activeTab == 1) {
            if (isDown && captureForAnyButton && event.repeatCount == 0) {
                captureForAnyButton = false
                consumedCapture = true
                updateCaptureBtnLabel()
                openPhysicalSlotPicker(slot)
            }
            refreshLiveMonitor()
        }
        return consumedCapture || (keyCode != KeyEvent.KEYCODE_BUTTON_B && keyCode != KeyEvent.KEYCODE_BACK)
    }

    fun handlePhysicalMotionEvent(ev: MotionEvent): Boolean {
        val axes = GamepadBridge.extractMotionAxes(ev) ?: return false
        System.arraycopy(axes, 0, localAxes, 0, 8)
        if (GameModifierBridge.ensureLoaded()) {
            try {
                GamepadBridge.nativeSetAndroidControllerMotion(
                    ev.deviceId,
                    axes[0], axes[1], axes[2], axes[3],
                    axes[4], axes[5], axes[6], axes[7]
                )
            } catch (_: Throwable) {
            }
        }
        if (activeTab == 1) refreshLiveMonitor()
        return true
    }
}

object GamepadConfigDialog {

    fun show(
        context: Context,
        profileKey: String?,
        gameDisplayName: String? = null,
        onProfileUpdated: (GamepadProfile) -> Unit = {},
        onEnterLayoutEditor: ((GamepadProfile) -> Unit)? = null
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
                onEnterLayoutEditor?.invoke(saved)
            },
            onSaved = onProfileUpdated
        )
        val rootView = controller.buildRootView()

        val built = AlertDialog.Builder(controller.uiCtx)
            .setTitle(GamepadBridge.tr(context, "手柄与自定义按键设置", "ゲームパッド・キー設定", "Gamepad & Custom Keys"))
            .setView(rootView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(GamepadBridge.tr(context, "保存并生效", "保存して適用", "Save & Apply")) { _, _ ->
                controller.saveAndApply()
            }
            .create()
        dlgRef = built

        built.setOnKeyListener(DialogInterface.OnKeyListener { _, keyCode, event ->
            controller.handlePhysicalKeyEvent(keyCode, event)
        })

        built.setOnShowListener {
            built.window?.decorView?.setOnGenericMotionListener { _, ev ->
                controller.handlePhysicalMotionEvent(ev)
            }
            controller.startPolling { built.isShowing }
        }

        built.setOnDismissListener {
            controller.stopPolling()
        }

        built.show()
    }
}
