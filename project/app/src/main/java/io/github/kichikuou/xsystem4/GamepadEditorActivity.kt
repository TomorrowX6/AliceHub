package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min

/**
 * Landscape GameSir-style full-screen editor for touch virtual controls and physical gamepad mapping.
 */
class GamepadEditorActivity : Activity() {

    companion object {
        const val EXTRA_PROFILE_KEY = "profile_key"
        const val EXTRA_GAME_TITLE = "game_title"
        const val EXTRA_INITIAL_TAB = "initial_tab"
        const val TAB_VIRTUAL = 0
        const val TAB_PHYSICAL = 1

        private const val BG_CANVAS = 0xFF0B0F17.toInt()
        private const val BG_GLASS = 0xE6131B29.toInt()
        private const val BG_CARD = 0xFF141D2E.toInt()
        private const val BG_CHIP = 0xFF1E293B.toInt()
        private const val STROKE_GLASS = 0xFF2B3B54.toInt()
        private const val ACCENT_CYAN = 0xFF38BDF8.toInt()
        private const val ACCENT_GREEN = 0xFF34D399.toInt()
        private const val ACCENT_RED = 0xFFF87171.toInt()
        private const val TEXT_MAIN = 0xFFF8FAFC.toInt()
        private const val TEXT_SUB = 0xFF94A3B8.toInt()
        private const val TEXT_DARK = 0xFF06131F.toInt()

        fun createIntent(
            context: Context,
            profileKey: String?,
            gameTitle: String?,
            initialTab: Int = TAB_VIRTUAL
        ): Intent = Intent(context, GamepadEditorActivity::class.java)
            .putExtra(EXTRA_PROFILE_KEY, profileKey)
            .putExtra(EXTRA_GAME_TITLE, gameTitle)
            .putExtra(EXTRA_INITIAL_TAB, initialTab)
    }

    private var profileKey: String? = null
    private var perGameOverride: Boolean = false
    private lateinit var profile: GamepadProfile
    private var activeTab: Int = TAB_VIRTUAL
    private var selectedControlId: String? = null
    private var selectedPadSlot: Int? = null
    private var listenPhysicalKey: Boolean = false

    private lateinit var backdropView: BlueprintBackdropView
    private lateinit var virtualLayer: VirtualGamepadView
    private lateinit var bottomInspectorBar: LinearLayout
    private lateinit var bottomScroll: HorizontalScrollView
    private lateinit var physicalContainer: LinearLayout
    private lateinit var controllerDiagram: ControllerDiagramView
    private lateinit var physStatusTv: TextView
    private lateinit var physEnabledSwitch: Switch
    private lateinit var physListenSwitch: Switch
    private lateinit var physSwapSwitch: Switch
    private lateinit var physAutoHideSwitch: Switch
    private lateinit var physRumbleSwitch: Switch
    private lateinit var deadzoneLabelTv: TextView
    private lateinit var deadzoneSeek: SeekBar
    private lateinit var speedLabelTv: TextView
    private lateinit var speedSeek: SeekBar

    private lateinit var scopePill: TextView
    private lateinit var tabVirtualBtn: TextView
    private lateinit var tabPhysicalBtn: TextView
    private lateinit var presetPill: TextView
    private lateinit var addBtn: TextView
    private lateinit var gridBtn: TextView

    private var syncingUi = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val livePollRunnable = object : Runnable {
        override fun run() {
            pollControllerTelemetry()
            uiHandler.postDelayed(this, 60L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        applyImmersiveDarkWindow()

        profileKey = GamepadBridge.normalizeGameProfileKey(intent.getStringExtra(EXTRA_PROFILE_KEY))
        activeTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_VIRTUAL)
        perGameOverride = GamepadBridge.hasPerGameOverride(this, profileKey)
        profile = GamepadBridge.loadProfile(this, profileKey)

        buildLayout()
        applyTab(activeTab)
        refreshAllUi()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveDarkWindow()
        uiHandler.removeCallbacks(livePollRunnable)
        uiHandler.post(livePollRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(livePollRunnable)
    }

    private fun applyImmersiveDarkWindow() {
        window.statusBarColor = BG_CANVAS
        window.navigationBarColor = BG_CANVAS
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { ic ->
                ic.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ic.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    private fun tr(zh: String, ja: String, en: String): String =
        GamepadBridge.tr(this, zh, ja, en)

    private fun dp(v: Int): Int = UiKit.dpInt(this, v)

    private fun buildLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(BG_CANVAS)
        }

        // 1. Dark blueprint backdrop + 16:9 safe guide
        backdropView = BlueprintBackdropView(this)
        root.addView(backdropView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 2. Full-screen virtual touch canvas (exact 1:1 coordinate space)
        virtualLayer = VirtualGamepadView(this).apply {
            listener = object : VirtualGamepadView.Listener {
                override fun onControlTappedInEditor(control: VirtualControlSpec) {
                    selectedControlId = control.id
                    setSelectedControlId(control.id)
                    rebuildBottomInspector()
                }

                override fun onControlMovedInEditor(control: VirtualControlSpec) {
                    val updated = profile.virtual.controls.map {
                        if (it.id == control.id) control else it
                    }
                    profile = profile.copy(virtual = profile.virtual.copy(controls = updated))
                }

                override fun onSelectionClearedInEditor() {
                    selectedControlId = null
                    rebuildBottomInspector()
                }

                override fun onControlDragChangedInEditor(dragging: Boolean) {
                    bottomScroll.animate()
                        .alpha(if (dragging) 0.14f else 1.0f)
                        .setDuration(120L)
                        .start()
                }
            }
        }
        root.addView(virtualLayer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 3. Physical Controller visual canvas + compact side panel
        physicalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(56), dp(14), dp(12))
            visibility = View.GONE
        }
        controllerDiagram = ControllerDiagramView(this).apply {
            onSlotClicked = { slot ->
                selectedPadSlot = slot
                invalidate()
                openPhysicalSlotPicker(slot)
            }
            onStickClicked = { stickIdx ->
                val title = if (stickIdx == 0) tr("左摇杆", "左スティック", "L Stick") else tr("右摇杆", "右スティック", "R Stick")
                val curMode = if (stickIdx == 0) profile.physical.leftStickMode else profile.physical.rightStickMode
                KeyPickerDialog.showStickModePicker(this@GamepadEditorActivity, title, curMode) { chosen ->
                    profile = profile.copy(
                        physical = if (stickIdx == 0) {
                            profile.physical.copy(leftStickMode = chosen)
                        } else {
                            profile.physical.copy(rightStickMode = chosen)
                        }
                    )
                    refreshPhysicalUi()
                }
            }
            onDpadDefaultClicked = {
                KeyPickerDialog.showStickModePicker(
                    this@GamepadEditorActivity,
                    tr("十字键默认功能", "十字キー動作", "D-Pad Default"),
                    profile.physical.dpadMode
                ) { chosen ->
                    profile = profile.copy(physical = profile.physical.copy(dpadMode = chosen))
                    refreshPhysicalUi()
                }
            }
        }
        physicalContainer.addView(
            controllerDiagram,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.9f).apply {
                rightMargin = dp(10)
            }
        )

        val sidePanel = buildPhysicalSidePanel()
        physicalContainer.addView(
            sidePanel,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.05f)
        )
        root.addView(physicalContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 4. Floating Top Glass Bar
        val topBar = buildTopGlassBar()
        root.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(8)
            leftMargin = dp(10)
            rightMargin = dp(10)
        })

        // 5. Floating Bottom Contextual Inspector Pill (Virtual Tab)
        bottomInspectorBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = glassDrawable(22f)
        }
        bottomScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(bottomInspectorBar)
        }
        root.addView(bottomScroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(10)
            leftMargin = dp(14)
            rightMargin = dp(14)
        })

        setContentView(root)
    }

    private fun buildTopGlassBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = glassDrawable(24f)
        }

        val backBtn = makePill("←", accent = false) { finish() }
        bar.addView(backBtn, wrapParams(rightMargin = dp(6)))

        scopePill = makePill(tr("全局", "共通", "Global"), accent = false) {
            if (profileKey != null) {
                perGameOverride = !perGameOverride
                updateScopePill()
            }
        }
        if (profileKey == null) scopePill.visibility = View.GONE
        bar.addView(scopePill, wrapParams(rightMargin = dp(6)))

        // Segmented Tabs: Virtual | Physical
        val segGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(99).toFloat()
                setColor(0xFF0A101B.toInt())
                setStroke(dp(1), STROKE_GLASS)
            }
        }
        tabVirtualBtn = makePill(tr("虚拟按键", "仮想パッド", "Virtual"), accent = false) {
            applyTab(TAB_VIRTUAL)
        }
        tabPhysicalBtn = makePill(tr("实体手柄", "コントローラ", "Controller"), accent = false) {
            applyTab(TAB_PHYSICAL)
        }
        segGroup.addView(tabVirtualBtn)
        segGroup.addView(tabPhysicalBtn)
        bar.addView(segGroup, wrapParams(rightMargin = dp(8)))

        // Scrollable middle tools (preset, + add, grid) so narrow screens never clip Save button
        val toolsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        presetPill = makePill("ADV", accent = false) { cyclePresetWithConfirm() }
        toolsRow.addView(presetPill, wrapParams(rightMargin = dp(6)))

        addBtn = makePill(tr("+ 按键", "+ 追加", "+ Add"), accent = false) { showAddControlSheet() }
        toolsRow.addView(addBtn, wrapParams(rightMargin = dp(6)))

        gridBtn = makePill(tr("网格", "グリッド", "Grid"), accent = false) {
            profile = profile.copy(
                virtual = profile.virtual.copy(gridSnap = !profile.virtual.gridSnap)
            )
            virtualLayer.setProfile(profile)
            updateVirtualTopButtons()
        }
        toolsRow.addView(gridBtn, wrapParams(rightMargin = dp(6)))

        toolsScroll.addView(toolsRow)
        bar.addView(toolsScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val resetBtn = makePill(tr("重置", "初期化", "Reset"), accent = false) { confirmReset() }
        bar.addView(resetBtn, wrapParams(leftMargin = dp(6), rightMargin = dp(6)))

        val saveBtn = makePill(tr("保存", "保存", "Save"), accent = true) { saveAndFinish() }
        bar.addView(saveBtn)

        return bar
    }

    private fun buildPhysicalSidePanel(): View {
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = glassDrawable(16f)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(12)
            setPadding(p, dp(10), p, dp(10))
        }

        physStatusTv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(BG_CHIP)
            }
        }
        col.addView(physStatusTv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        })

        physEnabledSwitch = Switch(this)
        col.addView(makeSwitchRow(tr("实体映射", "物理マップ", "Gamepad Map"), physEnabledSwitch) { checked ->
            profile = profile.copy(physical = profile.physical.copy(enabled = checked))
            GamepadBridge.syncProfileToNative(profile)
        })

        physListenSwitch = Switch(this)
        col.addView(makeSwitchRow(tr("监听改键", "押し当て設定", "Press to Bind"), physListenSwitch) { checked ->
            listenPhysicalKey = checked
        })

        physSwapSwitch = Switch(this)
        col.addView(makeSwitchRow("A↔B  X↔Y", physSwapSwitch) { checked ->
            profile = profile.copy(physical = profile.physical.copy(swapAbXy = checked))
            controllerDiagram.profile = profile
            controllerDiagram.invalidate()
        })

        physAutoHideSwitch = Switch(this)
        col.addView(makeSwitchRow(tr("接入隐藏虚拟键", "接続時仮想非表示", "Hide Virtual"), physAutoHideSwitch) { checked ->
            profile = profile.copy(physical = profile.physical.copy(autoHideVirtualPad = checked))
        })

        physRumbleSwitch = Switch(this)
        col.addView(makeSwitchRow(tr("震动", "振動", "Rumble"), physRumbleSwitch) { checked ->
            profile = profile.copy(physical = profile.physical.copy(rumbleEnabled = checked))
        })

        // Preset row for Physical bindings
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(8))
        }
        val advMapBtn = makePill("ADV", accent = false) {
            profile = profile.copy(
                physical = profile.physical.copy(
                    leftStickMode = GamepadBridge.STICK_MOUSE,
                    rightStickMode = GamepadBridge.STICK_WHEEL,
                    dpadMode = GamepadBridge.STICK_ARROWS,
                    bindings = GamepadBridge.defaultPhysicalBindings()
                )
            )
            refreshPhysicalUi()
        }
        val rpgMapBtn = makePill("RPG", accent = false) {
            profile = profile.copy(
                physical = profile.physical.copy(
                    leftStickMode = GamepadBridge.STICK_WASD,
                    rightStickMode = GamepadBridge.STICK_MOUSE,
                    dpadMode = GamepadBridge.STICK_ARROWS,
                    bindings = GamepadBridge.rpgPhysicalBindings()
                )
            )
            refreshPhysicalUi()
        }
        presetRow.addView(advMapBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
        })
        presetRow.addView(rpgMapBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(presetRow)

        deadzoneLabelTv = makeSliderLabel()
        deadzoneSeek = SeekBar(this).apply { max = 40 } // 5..45
        deadzoneSeek.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = (p + 5).coerceIn(5, 45)
            profile = profile.copy(physical = profile.physical.copy(deadzonePct = v))
            deadzoneLabelTv.text = "${tr("死区", "デッドゾーン", "Deadzone")} ${v}%"
        })
        col.addView(deadzoneLabelTv)
        col.addView(deadzoneSeek)

        speedLabelTv = makeSliderLabel()
        speedSeek = SeekBar(this).apply { max = 220 } // 30..250
        speedSeek.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = (p + 30).coerceIn(30, 250)
            profile = profile.copy(physical = profile.physical.copy(cursorSpeedPct = v))
            speedLabelTv.text = "${tr("指针速度", "カーソル速度", "Pointer")} ${v}%"
        })
        col.addView(speedLabelTv)
        col.addView(speedSeek)

        scroll.addView(col)
        return scroll
    }

    private fun applyTab(tab: Int) {
        activeTab = tab
        val isVirtual = tab == TAB_VIRTUAL
        backdropView.showSafeFrame = isVirtual
        backdropView.invalidate()
        virtualLayer.visibility = if (isVirtual) View.VISIBLE else View.GONE
        virtualLayer.setEditMode(isVirtual, selectedControlId)
        bottomScroll.visibility = if (isVirtual) View.VISIBLE else View.GONE
        physicalContainer.visibility = if (isVirtual) View.GONE else View.VISIBLE

        presetPill.visibility = if (isVirtual) View.VISIBLE else View.GONE
        addBtn.visibility = if (isVirtual) View.VISIBLE else View.GONE
        gridBtn.visibility = if (isVirtual) View.VISIBLE else View.GONE

        styleTabPill(tabVirtualBtn, isVirtual)
        styleTabPill(tabPhysicalBtn, !isVirtual)
    }

    private fun refreshAllUi() {
        updateScopePill()
        updateVirtualTopButtons()
        virtualLayer.setProfile(profile)
        virtualLayer.setEditMode(activeTab == TAB_VIRTUAL, selectedControlId)
        rebuildBottomInspector()
        refreshPhysicalUi()
    }

    private fun refreshPhysicalUi() {
        syncingUi = true
        physEnabledSwitch.isChecked = profile.physical.enabled
        physListenSwitch.isChecked = listenPhysicalKey
        physSwapSwitch.isChecked = profile.physical.swapAbXy
        physAutoHideSwitch.isChecked = profile.physical.autoHideVirtualPad
        physRumbleSwitch.isChecked = profile.physical.rumbleEnabled
        deadzoneSeek.progress = (profile.physical.deadzonePct - 5).coerceIn(0, 40)
        deadzoneLabelTv.text = "${tr("死区", "デッドゾーン", "Deadzone")} ${profile.physical.deadzonePct}%"
        speedSeek.progress = (profile.physical.cursorSpeedPct - 30).coerceIn(0, 220)
        speedLabelTv.text = "${tr("指针速度", "カーソル速度", "Pointer")} ${profile.physical.cursorSpeedPct}%"
        syncingUi = false

        controllerDiagram.profile = profile
        controllerDiagram.selectedSlot = selectedPadSlot
        controllerDiagram.invalidate()
        pollControllerTelemetry()
    }

    private fun updateScopePill() {
        if (profileKey == null) return
        scopePill.text = if (perGameOverride) tr("本游戏", "専用", "Game") else tr("全局", "共通", "Global")
        stylePill(scopePill, active = perGameOverride)
    }

    private fun updateVirtualTopButtons() {
        presetPill.text = when (profile.virtual.presetId) {
            GamepadBridge.PRESET_RPG_DUNGEON -> "RPG"
            GamepadBridge.PRESET_QUICK_BAR -> tr("极简", "ミニ", "Bar")
            else -> "ADV"
        }
        stylePill(gridBtn, active = profile.virtual.gridSnap)
    }

    private fun rebuildBottomInspector() {
        bottomInspectorBar.removeAllViews()
        val sel = selectedControlId?.let { id ->
            profile.virtual.controls.firstOrNull { it.id == id }
        }
        if (sel == null) {
            val enabledChip = makePill(
                if (profile.virtual.enabled) tr("虚拟按键 · 开", "仮想 · ON", "Virtual · ON")
                else tr("虚拟按键 · 关", "仮想 · OFF", "Virtual · OFF"),
                accent = profile.virtual.enabled
            ) {
                profile = profile.copy(virtual = profile.virtual.copy(enabled = !profile.virtual.enabled))
                virtualLayer.setProfile(profile)
                rebuildBottomInspector()
            }
            bottomInspectorBar.addView(enabledChip, wrapParams(rightMargin = dp(8)))

            val hapticChip = makePill(
                if (profile.virtual.hapticFeedback) tr("触感 · 开", "振動 · ON", "Haptic · ON")
                else tr("触感 · 关", "振動 · OFF", "Haptic · OFF"),
                accent = profile.virtual.hapticFeedback
            ) {
                profile = profile.copy(virtual = profile.virtual.copy(hapticFeedback = !profile.virtual.hapticFeedback))
                rebuildBottomInspector()
            }
            bottomInspectorBar.addView(hapticChip, wrapParams(rightMargin = dp(10)))

            bottomInspectorBar.addView(makeInlineSlider(
                tr("大小", "サイズ", "Size"),
                (profile.virtual.globalScalePct - 60).coerceIn(0, 90),
                90
            ) { p ->
                val v = (p + 60).coerceIn(60, 150)
                profile = profile.copy(virtual = profile.virtual.copy(globalScalePct = v))
                virtualLayer.setProfile(profile)
            }, wrapParams(rightMargin = dp(8)))

            bottomInspectorBar.addView(makeInlineSlider(
                tr("透明", "不透明", "Alpha"),
                (profile.virtual.globalOpacityPct - 20).coerceIn(0, 80),
                80
            ) { p ->
                val v = (p + 20).coerceIn(20, 100)
                profile = profile.copy(virtual = profile.virtual.copy(globalOpacityPct = v))
                virtualLayer.setProfile(profile)
            }, wrapParams(rightMargin = dp(8)))

            val hint = TextView(this).apply {
                text = tr("拖动改位 · 点按改键", "ドラッグ移動 · タップ編集", "Drag move · Tap edit")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(TEXT_SUB)
            }
            bottomInspectorBar.addView(hint)
            return
        }

        // Control selected -> inline GameSir contextual inspector
        val nameChip = makePill(sel.label.ifBlank { "?" }, accent = false) {
            showRenameDialog(sel)
        }
        bottomInspectorBar.addView(nameChip, wrapParams(rightMargin = dp(6)))

        if (sel.kind == VirtualControlKind.BUTTON) {
            val actionLabel = GamepadBridge.formatActionLabel(this, sel.action)
            val actionChip = makePill("$actionLabel ▾", accent = true) {
                KeyPickerDialog.showActionPicker(
                    this,
                    "${sel.label} · ${tr("绑定按键", "割当", "Bind")}",
                    sel.action,
                    allowNone = true
                ) { chosen ->
                    mutateControl(sel.id) { it.copy(action = chosen) }
                }
            }
            bottomInspectorBar.addView(actionChip, wrapParams(rightMargin = dp(6)))

            val lockChip = makePill(
                tr("锁", "固定", "Lock"),
                accent = sel.action.toggleMode
            ) {
                mutateControl(sel.id) { it.copy(action = it.action.copy(toggleMode = !it.action.toggleMode)) }
            }
            bottomInspectorBar.addView(lockChip, wrapParams(rightMargin = dp(6)))

            val shapeChip = makePill(GamepadBridge.shapeLabel(this, sel.shape), accent = false) {
                mutateControl(sel.id) { it.copy(shape = GamepadBridge.nextButtonShape(it.shape)) }
            }
            bottomInspectorBar.addView(shapeChip, wrapParams(rightMargin = dp(8)))
        } else {
            val modeChip = makePill(
                "${GamepadBridge.stickModeLabel(this, sel.stickMode)} ▾",
                accent = true
            ) {
                KeyPickerDialog.showStickModePicker(
                    this,
                    sel.label,
                    sel.stickMode,
                    allowWheel = true
                ) { m ->
                    mutateControl(sel.id) { it.copy(stickMode = m) }
                }
            }
            bottomInspectorBar.addView(modeChip, wrapParams(rightMargin = dp(8)))
        }

        bottomInspectorBar.addView(makeInlineSlider(
            tr("大小", "サイズ", "Size"),
            ((sel.scale - 0.6f) * 100f).toInt().coerceIn(0, 100),
            100
        ) { p ->
            val sc = (0.6f + p / 100f).coerceIn(0.6f, 1.6f)
            mutateControl(sel.id, rebuildBar = false) { it.copy(scale = sc) }
        }, wrapParams(rightMargin = dp(8)))

        bottomInspectorBar.addView(makeInlineSlider(
            tr("透明", "不透明", "Alpha"),
            ((sel.opacity - 0.3f) * 100f).toInt().coerceIn(0, 70),
            70
        ) { p ->
            val op = (0.3f + p / 100f).coerceIn(0.3f, 1.0f)
            mutateControl(sel.id, rebuildBar = false) { it.copy(opacity = op) }
        }, wrapParams(rightMargin = dp(6)))

        val visChip = makePill(
            if (sel.visible) tr("显示", "表示", "Show") else tr("隐藏", "非表示", "Hide"),
            accent = sel.visible
        ) {
            mutateControl(sel.id) { it.copy(visible = !it.visible) }
        }
        bottomInspectorBar.addView(visChip, wrapParams(rightMargin = dp(6)))

        if (sel.isCustom) {
            val delChip = makePill(tr("删", "削除", "Del"), accent = false) {
                val remaining = profile.virtual.controls.filterNot { it.id == sel.id }
                profile = profile.copy(virtual = profile.virtual.copy(controls = remaining))
                selectedControlId = null
                virtualLayer.setProfile(profile)
                virtualLayer.setEditMode(true, null)
                rebuildBottomInspector()
            }.apply {
                setTextColor(ACCENT_RED)
            }
            bottomInspectorBar.addView(delChip, wrapParams(rightMargin = dp(6)))
        }

        val closeSel = makePill("✕", accent = false) {
            selectedControlId = null
            virtualLayer.setSelectedControlId(null)
            rebuildBottomInspector()
        }
        bottomInspectorBar.addView(closeSel)
    }

    private fun mutateControl(
        id: String,
        rebuildBar: Boolean = true,
        transform: (VirtualControlSpec) -> VirtualControlSpec
    ) {
        val nextList = profile.virtual.controls.map { if (it.id == id) transform(it) else it }
        profile = profile.copy(virtual = profile.virtual.copy(controls = nextList))
        virtualLayer.setProfile(profile)
        virtualLayer.setEditMode(true, id)
        if (rebuildBar) rebuildBottomInspector()
    }

    private fun showRenameDialog(spec: VirtualControlSpec) {
        val input = EditText(this).apply {
            setText(spec.label)
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(tr("按键名称", "ラベル名", "Label"))
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = input.text?.toString()?.trim()?.take(8).orEmpty()
                if (text.isNotEmpty()) {
                    mutateControl(spec.id) { it.copy(label = text) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun cyclePresetWithConfirm() {
        val presets = arrayOf("ADV", "RPG", tr("极简", "ミニ", "Bar"))
        val presetIds = arrayOf(
            GamepadBridge.PRESET_VN_MOUSE,
            GamepadBridge.PRESET_RPG_DUNGEON,
            GamepadBridge.PRESET_QUICK_BAR
        )
        AlertDialog.Builder(this)
            .setTitle(tr("切换布局预设", "プリセット選択", "Presets"))
            .setItems(presets) { _, which ->
                val id = presetIds[which]
                profile = profile.copy(
                    virtual = profile.virtual.copy(
                        enabled = true,
                        presetId = id,
                        controls = GamepadBridge.buildPresetControls(id)
                    )
                )
                selectedControlId = null
                refreshAllUi()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddControlSheet() {
        val hasRightStick = profile.virtual.controls.any { it.id == "stick_r" }
        val slotId = GamepadBridge.nextFreeCustomSlotId(profile.virtual.controls)
        if (slotId < 0) {
            Toast.makeText(this, tr("自定义按键已达上限", "上限に達しました", "Limit reached"), Toast.LENGTH_SHORT).show()
            return
        }
        if (hasRightStick) {
            spawnCustomButton(slotId)
            return
        }
        val items = arrayOf(
            tr("新增按键", "ボタン追加", "Add Button"),
            tr("新增右摇杆", "右スティック追加", "Add R Stick")
        )
        AlertDialog.Builder(this)
            .setTitle(tr("添加控件", "追加", "Add Control"))
            .setItems(items) { _, which ->
                if (which == 0) {
                    spawnCustomButton(slotId)
                } else {
                    val stick = VirtualControlSpec(
                        id = "stick_r",
                        slotId = slotId,
                        kind = VirtualControlKind.STICK,
                        label = "R",
                        xNorm = 0.72f,
                        yNorm = 0.76f,
                        scale = 0.92f,
                        opacity = 1.0f,
                        visible = true,
                        shape = VirtualButtonShape.CIRCLE,
                        stickMode = GamepadBridge.STICK_WHEEL,
                        isCustom = true
                    )
                    val nextList = profile.virtual.controls + stick
                    profile = profile.copy(virtual = profile.virtual.copy(controls = nextList))
                    selectedControlId = stick.id
                    refreshAllUi()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun spawnCustomButton(slotId: Int) {
        val num = profile.virtual.controls.count { it.isCustom } + 1
        val newBtn = VirtualControlSpec(
            id = "custom_$slotId",
            slotId = slotId,
            kind = VirtualControlKind.BUTTON,
            label = "K$num",
            xNorm = 0.50f,
            yNorm = 0.50f,
            scale = 0.95f,
            opacity = 1.0f,
            visible = true,
            shape = VirtualButtonShape.PILL,
            action = GamepadActionSpec(GamepadBridge.ACTION_VK, GamepadBridge.VK_RETURN),
            isCustom = true
        )
        profile = profile.copy(virtual = profile.virtual.copy(controls = profile.virtual.controls + newBtn))
        selectedControlId = newBtn.id
        refreshAllUi()
        KeyPickerDialog.showActionPicker(
            this,
            "${newBtn.label} · ${tr("绑定按键", "割当", "Bind")}",
            newBtn.action,
            allowNone = true
        ) { chosen ->
            val shortLabel = GamepadBridge.formatActionLabel(this, chosen).take(5).ifBlank { newBtn.label }
            mutateControl(newBtn.id) { it.copy(action = chosen, label = shortLabel) }
        }
    }

    private fun openPhysicalSlotPicker(slot: Int) {
        val current = profile.physical.bindings[slot] ?: GamepadActionSpec(GamepadBridge.ACTION_NONE, 0)
        val slotLabel = GamepadBridge.physicalSlotName(this, slot)
        KeyPickerDialog.showActionPicker(
            this,
            "$slotLabel · ${tr("映射按键", "割当", "Map")}",
            current,
            allowNone = true
        ) { chosen ->
            val next = profile.physical.bindings.toMutableMap()
            next[slot] = chosen
            profile = profile.copy(physical = profile.physical.copy(bindings = next))
            GamepadBridge.syncProfileToNative(profile)
            controllerDiagram.profile = profile
            controllerDiagram.invalidate()
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(tr("重置设置", "初期化", "Reset"))
            .setMessage(tr("恢复全部开关默认关闭并还原默认布局？", "すべてデフォルトに戻しますか？", "Restore defaults?"))
            .setPositiveButton(R.string.ok) { _, _ ->
                if (perGameOverride && profileKey != null) {
                    GamepadBridge.clearPerGameOverride(this, profileKey)
                    perGameOverride = false
                }
                profile = GamepadProfile()
                selectedControlId = null
                selectedPadSlot = null
                refreshAllUi()
                GamepadBridge.syncProfileToNative(profile)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveAndFinish() {
        val saved = GamepadBridge.saveProfile(this, profile, profileKey, perGameOverride)
        GamepadBridge.syncProfileToNative(saved)
        setResult(RESULT_OK)
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GamepadBridge.isGamepadKeyEvent(event)) {
            val isDown = event.action == KeyEvent.ACTION_DOWN
            val isUp = event.action == KeyEvent.ACTION_UP
            val code = if (event.keyCode == KeyEvent.KEYCODE_BACK) KeyEvent.KEYCODE_BUTTON_B else event.keyCode
            if ((isDown && event.repeatCount == 0) || isUp) {
                try {
                    GamepadBridge.nativeSetAndroidControllerKey(event.deviceId, code, isDown)
                } catch (_: Throwable) {
                }
                GamepadBridge.androidKeyCodeToPadSlot(code)?.let { slot ->
                    if (isDown) controllerDiagram.pressedMask = controllerDiagram.pressedMask or (1 shl slot)
                    else controllerDiagram.pressedMask = controllerDiagram.pressedMask and (1 shl slot).inv()
                    controllerDiagram.invalidate()
                    if (isDown && activeTab == TAB_PHYSICAL && listenPhysicalKey) {
                        listenPhysicalKey = false
                        syncingUi = true
                        physListenSwitch.isChecked = false
                        syncingUi = false
                        selectedPadSlot = slot
                        controllerDiagram.selectedSlot = slot
                        controllerDiagram.invalidate()
                        openPhysicalSlotPicker(slot)
                        return true
                    }
                }
            }
            if (code != KeyEvent.KEYCODE_BACK && GamepadBridge.androidKeyCodeToPadSlot(code) != null) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        GamepadBridge.extractMotionAxes(ev)?.let { axes ->
            try {
                GamepadBridge.nativeSetAndroidControllerMotion(
                    ev.deviceId,
                    axes[0], axes[1], axes[2], axes[3],
                    axes[4], axes[5], axes[6], axes[7]
                )
            } catch (_: Throwable) {
            }
            System.arraycopy(axes, 0, controllerDiagram.axes, 0, 6)
            controllerDiagram.invalidate()
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun pollControllerTelemetry() {
        val pads = GamepadBridge.queryConnectedControllers(this)
        if (pads.isNotEmpty()) {
            physStatusTv.text = "● ${pads.first().name}"
            physStatusTv.setTextColor(ACCENT_GREEN)
        } else {
            physStatusTv.text = tr("未检测到实体手柄", "コントローラ未接続", "No controller")
            physStatusTv.setTextColor(TEXT_SUB)
        }
        if (!GameModifierBridge.ensureLoaded()) return
        try {
            val raw = GamepadBridge.nativeGetControllerStatusJson()
            if (raw.isNotBlank()) {
                val obj = JSONObject(raw)
                val mask = obj.optInt("buttonsMask", 0)
                if (mask != 0 || pads.isNotEmpty()) {
                    controllerDiagram.pressedMask = mask
                }
                val axesArr = obj.optJSONArray("axes")
                if (axesArr != null && axesArr.length() >= 6) {
                    for (i in 0 until 6) {
                        controllerDiagram.axes[i] = axesArr.optDouble(i, 0.0).toFloat()
                    }
                }
                controllerDiagram.invalidate()
            }
        } catch (_: Throwable) {
        }
    }

    private fun glassDrawable(radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = UiKit.dp(this@GamepadEditorActivity, radiusDp)
        setColor(BG_GLASS)
        setStroke(dp(1), STROKE_GLASS)
    }

    private fun makePill(label: String, accent: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(11), dp(5), dp(11), dp(5))
            stylePill(this, accent)
            setOnClickListener { onClick() }
        }

    private fun stylePill(tv: TextView, active: Boolean) {
        tv.setTextColor(if (active) TEXT_DARK else TEXT_MAIN)
        tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(99).toFloat()
            setColor(if (active) ACCENT_CYAN else BG_CHIP)
            setStroke(dp(1), if (active) ACCENT_CYAN else STROKE_GLASS)
        }
    }

    private fun styleTabPill(tv: TextView, active: Boolean) {
        tv.setTextColor(if (active) TEXT_DARK else TEXT_SUB)
        tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        tv.background = if (active) {
            GradientDrawable().apply {
                cornerRadius = dp(99).toFloat()
                setColor(ACCENT_CYAN)
            }
        } else {
            null
        }
    }

    private fun makeSwitchRow(label: String, sw: Switch, onChanged: (Boolean) -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(TEXT_MAIN)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            sw.setOnCheckedChangeListener { _, isChecked ->
                if (!syncingUi) onChanged(isChecked)
            }
            addView(sw)
        }
    }

    private fun makeSliderLabel(): TextView = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setTextColor(TEXT_SUB)
        setPadding(0, dp(4), 0, dp(2))
    }

    private fun makeInlineSlider(
        label: String,
        initialProgress: Int,
        maxProgress: Int,
        onChanged: (Int) -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(TEXT_SUB)
                setPadding(0, 0, dp(4), 0)
            })
            addView(SeekBar(context).apply {
                max = maxProgress
                progress = initialProgress
                setOnSeekBarChangeListener(simpleSeekListener(onChanged))
            }, LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun simpleSeekListener(onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    private fun wrapParams(
        leftMargin: Int = 0,
        rightMargin: Int = 0
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        this.leftMargin = leftMargin
        this.rightMargin = rightMargin
    }

    /**
     * Dark blueprint background with subtle 16:9 viewport reference guide.
     */
    private class BlueprintBackdropView(context: Context) : View(context) {
        var showSafeFrame = true

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x161E293B
            strokeWidth = 1.5f
        }
        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0x3A38BDF8
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x6694A3B8
            textSize = UiKit.dp(context, 10f)
        }
        private val safeRect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val step = UiKit.dp(context, 28f)
            var x = step
            while (x < w) {
                canvas.drawLine(x, 0f, x, h, gridPaint)
                x += step
            }
            var y = step
            while (y < h) {
                canvas.drawLine(0f, y, w, y, gridPaint)
                y += step
            }
            if (showSafeFrame && w > 0f && h > 0f) {
                val targetRatio = 16f / 9f
                val maxH = h * 0.78f
                val maxW = w * 0.88f
                val frameW = min(maxW, maxH * targetRatio)
                val frameH = frameW / targetRatio
                val left = (w - frameW) * 0.5f
                val top = (h - frameH) * 0.5f
                safeRect.set(left, top, left + frameW, top + frameH)
                canvas.drawRoundRect(safeRect, UiKit.dp(context, 8f), UiKit.dp(context, 8f), framePaint)
                canvas.drawText("16:9", left + UiKit.dp(context, 8f), top + UiKit.dp(context, 16f), tagPaint)
            }
        }
    }

    /**
     * Interactive GameSir-style Xbox/Switch vector controller diagram with live highlights.
     */
    private class ControllerDiagramView(context: Context) : View(context) {
        var profile: GamepadProfile = GamepadProfile()
        var selectedSlot: Int? = null
        var pressedMask: Int = 0
        val axes = FloatArray(6)

        var onSlotClicked: ((Int) -> Unit)? = null
        var onStickClicked: ((Int) -> Unit)? = null
        var onDpadDefaultClicked: (() -> Unit)? = null

        private data class Hotspot(
            val kind: Int, // 0=slot, 1=stick0, 2=stick1, 3=dpadDefault
            val id: Int,
            val nx: Float,
            val ny: Float,
            val nr: Float,
            val label: String
        )

        private val hotspots = listOf(
            // Triggers & Bumpers
            Hotspot(0, GamepadBridge.PAD_LT, 0.21f, 0.13f, 0.068f, "LT"),
            Hotspot(0, GamepadBridge.PAD_LB, 0.22f, 0.25f, 0.068f, "LB"),
            Hotspot(0, GamepadBridge.PAD_RB, 0.78f, 0.25f, 0.068f, "RB"),
            Hotspot(0, GamepadBridge.PAD_RT, 0.79f, 0.13f, 0.068f, "RT"),
            // Left Stick
            Hotspot(1, 0, 0.24f, 0.44f, 0.095f, "L"),
            // D-Pad 4 ways + default pill
            Hotspot(0, GamepadBridge.PAD_DPAD_UP, 0.355f, 0.58f, 0.045f, "↑"),
            Hotspot(0, GamepadBridge.PAD_DPAD_DOWN, 0.355f, 0.74f, 0.045f, "↓"),
            Hotspot(0, GamepadBridge.PAD_DPAD_LEFT, 0.295f, 0.66f, 0.045f, "←"),
            Hotspot(0, GamepadBridge.PAD_DPAD_RIGHT, 0.415f, 0.66f, 0.045f, "→"),
            Hotspot(3, 0, 0.355f, 0.85f, 0.056f, "十"),
            // Center Start/Select/Home
            Hotspot(0, GamepadBridge.PAD_BACK, 0.42f, 0.42f, 0.048f, "SEL"),
            Hotspot(0, GamepadBridge.PAD_GUIDE, 0.50f, 0.37f, 0.048f, "HOME"),
            Hotspot(0, GamepadBridge.PAD_START, 0.58f, 0.42f, 0.048f, "STA"),
            // ABXY Diamond
            Hotspot(0, GamepadBridge.PAD_Y, 0.76f, 0.34f, 0.052f, "Y"),
            Hotspot(0, GamepadBridge.PAD_X, 0.695f, 0.44f, 0.052f, "X"),
            Hotspot(0, GamepadBridge.PAD_B, 0.825f, 0.44f, 0.052f, "B"),
            Hotspot(0, GamepadBridge.PAD_A, 0.76f, 0.54f, 0.052f, "A"),
            // Right Stick
            Hotspot(2, 1, 0.645f, 0.66f, 0.092f, "R"),
            // L3 & R3 below sticks
            Hotspot(0, GamepadBridge.PAD_L3, 0.16f, 0.63f, 0.052f, "L3"),
            Hotspot(0, GamepadBridge.PAD_R3, 0.84f, 0.66f, 0.052f, "R3")
        )

        private val bodyPath = Path()
        private val bodyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BG_CARD
            style = Paint.Style.FILL
        }
        private val bodyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B3E5E.toInt()
            style = Paint.Style.STROKE
            strokeWidth = UiKit.dp(context, 2f)
        }
        private val chipFill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val chipStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        private val tmpRect = RectF()
        private val boxRect = RectF()

        private fun computeBox() {
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            val ratio = 1.66f
            val bw = min(w * 0.96f, h * ratio)
            val bh = bw / ratio
            val lx = (w - bw) * 0.5f
            val ty = (h - bh) * 0.5f
            boxRect.set(lx, ty, lx + bw, ty + bh)
        }

        private fun visualAbxyLabel(slot: Int): String {
            if (!profile.physical.swapAbXy) {
                return when (slot) {
                    GamepadBridge.PAD_A -> "A"
                    GamepadBridge.PAD_B -> "B"
                    GamepadBridge.PAD_X -> "X"
                    else -> "Y"
                }
            }
            return when (slot) {
                GamepadBridge.PAD_A -> "B"
                GamepadBridge.PAD_B -> "A"
                GamepadBridge.PAD_X -> "Y"
                else -> "X"
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            computeBox()
            val bw = boxRect.width()
            val bh = boxRect.height()
            fun px(nx: Float) = boxRect.left + nx * bw
            fun py(ny: Float) = boxRect.top + ny * bh

            // Draw ergonomic controller silhouette
            bodyPath.reset()
            bodyPath.moveTo(px(0.24f), py(0.27f))
            bodyPath.lineTo(px(0.76f), py(0.27f))
            bodyPath.cubicTo(px(0.89f), py(0.27f), px(0.95f), py(0.49f), px(0.92f), py(0.79f))
            bodyPath.cubicTo(px(0.90f), py(0.93f), px(0.80f), py(0.93f), px(0.74f), py(0.82f))
            bodyPath.cubicTo(px(0.68f), py(0.72f), px(0.62f), py(0.69f), px(0.50f), py(0.69f))
            bodyPath.cubicTo(px(0.38f), py(0.69f), px(0.32f), py(0.72f), px(0.26f), py(0.82f))
            bodyPath.cubicTo(px(0.20f), py(0.93f), px(0.10f), py(0.93f), px(0.08f), py(0.79f))
            bodyPath.cubicTo(px(0.05f), py(0.49f), px(0.11f), py(0.27f), px(0.24f), py(0.27f))
            bodyPath.close()

            canvas.drawPath(bodyPath, bodyFill)
            canvas.drawPath(bodyPath, bodyStroke)

            for (hs in hotspots) {
                val cx = px(hs.nx)
                val cy = py(hs.ny)
                val r = hs.nr * bw * 0.56f

                when (hs.kind) {
                    1, 2 -> {
                        val isLeft = hs.kind == 1
                        val axX = if (isLeft) axes[0] else axes[2]
                        val axY = if (isLeft) axes[1] else axes[3]
                        val active = hypot(axX.toDouble(), axY.toDouble()) > 0.18
                        chipFill.color = 0xFF0A101B.toInt()
                        canvas.drawCircle(cx, cy, r, chipFill)
                        chipStroke.color = if (active) ACCENT_CYAN else STROKE_GLASS
                        chipStroke.strokeWidth = UiKit.dp(context, 1.8f)
                        canvas.drawCircle(cx, cy, r, chipStroke)

                        val kx = cx + axX.coerceIn(-1f, 1f) * r * 0.46f
                        val ky = cy + axY.coerceIn(-1f, 1f) * r * 0.46f
                        chipFill.color = if (active) ACCENT_CYAN else 0xFF283548.toInt()
                        canvas.drawCircle(kx, ky, r * 0.54f, chipFill)

                        val mode = if (isLeft) profile.physical.leftStickMode else profile.physical.rightStickMode
                        val sub = GamepadBridge.stickModeLabel(context, mode)
                        keyPaint.textSize = (r * 0.42f).coerceIn(UiKit.dp(context, 9f), UiKit.dp(context, 14f))
                        keyPaint.color = if (active) TEXT_DARK else TEXT_MAIN
                        canvas.drawText(hs.label, kx, ky + keyPaint.textSize * 0.34f, keyPaint)
                        subPaint.textSize = (r * 0.36f).coerceIn(UiKit.dp(context, 8.5f), UiKit.dp(context, 12f))
                        subPaint.color = ACCENT_CYAN
                        canvas.drawText(sub, cx, cy + r + subPaint.textSize * 1.1f, subPaint)
                    }
                    3 -> {
                        val sub = "${GamepadBridge.tr(context, "十", "十", "D")}·${GamepadBridge.stickModeLabel(context, profile.physical.dpadMode)}"
                        tmpRect.set(cx - r * 1.55f, cy - r * 0.72f, cx + r * 1.55f, cy + r * 0.72f)
                        chipFill.color = BG_CHIP
                        chipStroke.color = STROKE_GLASS
                        chipStroke.strokeWidth = UiKit.dp(context, 1.1f)
                        canvas.drawRoundRect(tmpRect, r * 0.72f, r * 0.72f, chipFill)
                        canvas.drawRoundRect(tmpRect, r * 0.72f, r * 0.72f, chipStroke)
                        subPaint.textSize = (r * 0.56f).coerceIn(UiKit.dp(context, 9f), UiKit.dp(context, 12f))
                        subPaint.color = ACCENT_CYAN
                        canvas.drawText(sub, cx, cy + subPaint.textSize * 0.34f, subPaint)
                    }
                    else -> {
                        val pressed = (pressedMask and (1 shl hs.id)) != 0
                        val selected = selectedSlot == hs.id
                        val spec = profile.physical.bindings[hs.id] ?: GamepadActionSpec(GamepadBridge.ACTION_NONE, 0)
                        val boundText = GamepadBridge.formatActionLabel(context, spec)
                        val primaryLabel = if (hs.id in GamepadBridge.PAD_A..GamepadBridge.PAD_Y) {
                            visualAbxyLabel(hs.id)
                        } else {
                            hs.label
                        }

                        val isWidePill = hs.id in listOf(
                            GamepadBridge.PAD_LT, GamepadBridge.PAD_LB,
                            GamepadBridge.PAD_RB, GamepadBridge.PAD_RT,
                            GamepadBridge.PAD_BACK, GamepadBridge.PAD_START,
                            GamepadBridge.PAD_L3, GamepadBridge.PAD_R3
                        )
                        chipFill.color = when {
                            pressed -> ACCENT_CYAN
                            spec.type == GamepadBridge.ACTION_NONE -> 0xFF0E1624.toInt()
                            else -> BG_CHIP
                        }
                        chipStroke.color = when {
                            pressed -> ACCENT_CYAN
                            selected -> ACCENT_CYAN
                            else -> STROKE_GLASS
                        }
                        chipStroke.strokeWidth = UiKit.dp(context, if (selected || pressed) 2f else 1.2f)

                        if (isWidePill) {
                            tmpRect.set(cx - r * 1.28f, cy - r * 0.72f, cx + r * 1.28f, cy + r * 0.72f)
                            canvas.drawRoundRect(tmpRect, r * 0.65f, r * 0.65f, chipFill)
                            canvas.drawRoundRect(tmpRect, r * 0.65f, r * 0.65f, chipStroke)
                        } else {
                            canvas.drawCircle(cx, cy, r, chipFill)
                            canvas.drawCircle(cx, cy, r, chipStroke)
                        }

                        keyPaint.textSize = (r * 0.56f).coerceIn(UiKit.dp(context, 9f), UiKit.dp(context, 13f))
                        keyPaint.color = if (pressed) TEXT_DARK else TEXT_MAIN
                        subPaint.textSize = (r * 0.42f).coerceIn(UiKit.dp(context, 7.5f), UiKit.dp(context, 11f))
                        subPaint.color = when {
                            pressed -> TEXT_DARK
                            spec.type == GamepadBridge.ACTION_NONE -> TEXT_SUB
                            else -> ACCENT_CYAN
                        }

                        if (hs.id in GamepadBridge.PAD_DPAD_UP..GamepadBridge.PAD_DPAD_RIGHT) {
                            val txt = if (spec.type == GamepadBridge.ACTION_NONE) primaryLabel else boundText.take(4)
                            canvas.drawText(txt, cx, cy + keyPaint.textSize * 0.34f, keyPaint)
                        } else {
                            canvas.drawText(primaryLabel, cx, cy - subPaint.textSize * 0.15f, keyPaint)
                            canvas.drawText(boundText.take(6), cx, cy + subPaint.textSize * 0.95f, subPaint)
                        }
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return event.action == MotionEvent.ACTION_DOWN
            computeBox()
            val bw = boxRect.width().coerceAtLeast(1f)
            val bh = boxRect.height().coerceAtLeast(1f)
            val x = event.x
            val y = event.y

            var best: Hotspot? = null
            var bestDist = Float.MAX_VALUE
            for (hs in hotspots) {
                val cx = boxRect.left + hs.nx * bw
                val cy = boxRect.top + hs.ny * bh
                val hitR = hs.nr * bw * 0.72f
                val d = hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
                if (d <= hitR && d < bestDist) {
                    bestDist = d
                    best = hs
                }
            }
            best?.let { hs ->
                when (hs.kind) {
                    0 -> onSlotClicked?.invoke(hs.id)
                    1 -> onStickClicked?.invoke(0)
                    2 -> onStickClicked?.invoke(1)
                    else -> onDpadDefaultClicked?.invoke()
                }
                return true
            }
            return false
        }
    }
}
