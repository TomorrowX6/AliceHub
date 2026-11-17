package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
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
import android.view.WindowManager
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
import kotlin.math.max
import kotlin.math.min

/**
 * Landscape unified editor for virtual touch layout (Winlator / Yuzu style)
 * and physical gamepad button mapping (DuckStation / Yuzu two-column style).
 */
class GamepadEditorActivity : Activity() {

    companion object {
        const val EXTRA_PROFILE_KEY = "profile_key"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_GAME_TITLE = "game_title"
        const val EXTRA_INITIAL_TAB = "initial_tab"
        const val TAB_VIRTUAL = 0
        const val TAB_PHYSICAL = 1

        // AliceHub Material 3 Night palette tokens
        private const val COL_BG = 0xFF0E1014.toInt()
        private const val COL_SURFACE = 0xFF181B21.toInt()
        private const val COL_FLOATING = 0xF01B1F27.toInt()
        private const val COL_TILE = 0xFF21252E.toInt()
        private const val COL_CHIP = 0xFF282D3A.toInt()
        private const val COL_OUTLINE = 0x1FFFFFFF
        private const val COL_DIVIDER = 0x16FFFFFF
        private const val COL_PRIMARY = 0xFFA9B6FF.toInt()
        private const val COL_TEXT_MAIN = 0xFFECEEF4.toInt()
        private const val COL_TEXT_SUB = 0xFFA4AAB8.toInt()
        private const val COL_TEXT_DIM = 0xFF737988.toInt()
        private const val COL_SUCCESS = 0xFF7FD79A.toInt()
        private const val COL_DANGER = 0xFFFF9C92.toInt()

        // Keycap accent badges (Xbox / Steam Input / Yuzu style)
        private const val BADGE_A = 0xFF34D399.toInt()
        private const val BADGE_B = 0xFFF87171.toInt()
        private const val BADGE_X = 0xFF60A5FA.toInt()
        private const val BADGE_Y = 0xFFFBBF24.toInt()

        fun createIntent(
            context: Context,
            profileKey: String?,
            gameTitle: String?,
            initialTab: Int = TAB_VIRTUAL,
            gameId: String? = null
        ): Intent = Intent(context, GamepadEditorActivity::class.java)
            .putExtra(EXTRA_PROFILE_KEY, profileKey)
            .putExtra(EXTRA_GAME_TITLE, gameTitle)
            .putExtra(EXTRA_INITIAL_TAB, initialTab)
            .putExtra(EXTRA_GAME_ID, gameId)
    }

    private var themePrimary: Int = COL_PRIMARY
    private var themeOnPrimary: Int = 0xFF0F1A5C.toInt()
    private var themeTileActive: Int = 0xFF2B3568.toInt()

    private var profileKey: String? = null
    private var perGameOverride: Boolean = false
    private lateinit var profile: GamepadProfile
    private var activeTab: Int = TAB_VIRTUAL
    private var selectedControlId: String? = null
    private var globalPopoverOpen: Boolean = false
    private var listenPhysicalKey: Boolean = false
    private var pressedMask: Int = 0
    private var prevVisualPressedMask: Int = -1

    private lateinit var backdropView: ViewportFrameView
    private lateinit var virtualLayer: VirtualGamepadView
    private lateinit var elementInspectorCard: LinearLayout
    private lateinit var globalPopoverCard: LinearLayout
    private lateinit var physicalContainer: LinearLayout
    private lateinit var stickScopeView: DualStickScopeView
    private lateinit var leftStickChip: TextView
    private lateinit var rightStickChip: TextView
    private lateinit var dpadDefaultChip: TextView
    private lateinit var physStatusTv: TextView
    private lateinit var listenBtn: TextView

    private lateinit var physEnabledSwitch: Switch
    private lateinit var physSwapSwitch: Switch
    private lateinit var physAutoHideSwitch: Switch
    private lateinit var physRumbleSwitch: Switch
    private lateinit var deadzoneValTv: TextView
    private lateinit var deadzoneSeek: SeekBar
    private lateinit var speedValTv: TextView
    private lateinit var speedSeek: SeekBar

    private lateinit var tabVirtualBtn: TextView
    private lateinit var tabPhysicalBtn: TextView
    private lateinit var presetPill: TextView
    private lateinit var addBtn: TextView
    private lateinit var gridBtn: TextView
    private lateinit var globalCfgBtn: TextView

    private data class SlotTileRefs(
        val container: LinearLayout,
        val keyBadge: TextView,
        val actionTv: TextView
    )
    private val slotTiles = HashMap<Int, SlotTileRefs>()

    private var syncingUi = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val livePollRunnable = object : Runnable {
        override fun run() {
            pollControllerTelemetry()
            uiHandler.postDelayed(this, 60L)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val localeWrapped = AppSettings.wrapThemeContext(newBase)
        val cfg = Configuration(localeWrapped.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        super.attachBaseContext(localeWrapped.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        applyImmersiveDarkWindow()

        profileKey = GamepadBridge.normalizeGameProfileKey(intent.getStringExtra(EXTRA_PROFILE_KEY))
        val explicitGameId = intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
            ?: profileKey?.let { key -> GameCatalog.classifyDirectory(null, key, null, key) }
        if (explicitGameId != null) {
            val rawAcc = GameCatalog.descriptorFor(this, explicitGameId).accentColor
            themePrimary = UiKit.accentOnSurface(this, rawAcc)
            themeOnPrimary = UiKit.contrastOn(themePrimary)
            themeTileActive = UiKit.withAlpha(themePrimary, 0x3D)
        }
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
        window.statusBarColor = COL_BG
        window.navigationBarColor = COL_BG
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
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

    private fun tintSwitch(sw: Switch) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        sw.thumbTintList = ColorStateList(states, intArrayOf(themePrimary, 0xFF9CA3B4.toInt()))
        sw.trackTintList = ColorStateList(states, intArrayOf(UiKit.withAlpha(themePrimary, 0x66), 0xFF2C313E.toInt()))
    }

    private fun tintSeekBar(sb: SeekBar) {
        val accentList = ColorStateList.valueOf(themePrimary)
        sb.thumbTintList = accentList
        sb.progressTintList = accentList
        sb.progressBackgroundTintList = ColorStateList.valueOf(0xFF2F3545.toInt())
    }

    private fun buildLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(COL_BG)
        }

        // 1. Clean 16:9 corner-tick guide (Virtual mode only; zero double-grid clutter)
        backdropView = ViewportFrameView(this, themePrimary)
        root.addView(backdropView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 2. Full-screen virtual touch canvas (1:1 game coordinates)
        virtualLayer = VirtualGamepadView(this).apply {
            setEditorAccentColor(themePrimary)
            listener = object : VirtualGamepadView.Listener {
                override fun onControlTappedInEditor(control: VirtualControlSpec) {
                    globalPopoverOpen = false
                    globalPopoverCard.visibility = View.GONE
                    selectedControlId = control.id
                    setSelectedControlId(control.id)
                    rebuildElementInspector()
                }

                override fun onControlMovedInEditor(control: VirtualControlSpec) {
                    val updated = profile.virtual.controls.map {
                        if (it.id == control.id) control else it
                    }
                    profile = profile.copy(virtual = profile.virtual.copy(controls = updated))
                    positionElementInspector(control)
                }

                override fun onSelectionClearedInEditor() {
                    selectedControlId = null
                    globalPopoverOpen = false
                    globalPopoverCard.visibility = View.GONE
                    elementInspectorCard.visibility = View.GONE
                }

                override fun onControlDragChangedInEditor(dragging: Boolean) {
                    if (elementInspectorCard.visibility == View.VISIBLE) {
                        elementInspectorCard.animate()
                            .alpha(if (dragging) 0.12f else 1.0f)
                            .setDuration(110L)
                            .start()
                    }
                }
            }
        }
        root.addView(virtualLayer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 3. DuckStation / Yuzu Two-Column Physical Controller Studio
        physicalContainer = buildPhysicalStudio()
        root.addView(physicalContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 4. Compact Top-Center Floating Pill (WRAP_CONTENT — never covers top-left/right triggers)
        val topPill = buildTopCommandPill()
        root.addView(topPill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(8)
        })

        // 5. Winlator-style Smart-Positioned Floating Element Inspector Card
        elementInspectorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = floatingCardDrawable(16f)
            visibility = View.GONE
        }
        root.addView(elementInspectorCard, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(12)
        })

        // 6. Global Virtual Settings Popover Card (toggled by ⚙ button)
        globalPopoverCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = floatingCardDrawable(18f)
            visibility = View.GONE
        }
        root.addView(globalPopoverCard, FrameLayout.LayoutParams(
            dp(330),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(56)
        })

        // Apply symmetric cutout safe insets to physical container so camera cutouts never clip cards
        root.setOnApplyWindowInsetsListener { _, insets ->
            var safeSide = dp(16)
            if (Build.VERSION.SDK_INT >= 28) {
                insets.displayCutout?.let { c ->
                    safeSide = max(safeSide, max(c.safeInsetLeft, c.safeInsetRight) + dp(8))
                }
            }
            physicalContainer.setPadding(safeSide, dp(56), safeSide, dp(12))
            insets
        }

        setContentView(root)
    }

    private fun buildTopCommandPill(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            background = floatingCardDrawable(99f)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val backBtn = makePill("←", active = false) { finish() }.apply {
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        bar.addView(backBtn, wrapParams(rightMargin = dp(6)))

        // Segmented switcher: Virtual | Physical
        val segBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(99).toFloat()
                setColor(COL_BG)
                setStroke(dp(1), COL_OUTLINE)
            }
        }
        tabVirtualBtn = makeSegmentItem(tr("虚拟按键", "仮想パッド", "Virtual")) {
            applyTab(TAB_VIRTUAL)
        }
        tabPhysicalBtn = makeSegmentItem(tr("实体手柄", "コントローラ", "Controller")) {
            applyTab(TAB_PHYSICAL)
        }
        segBox.addView(tabVirtualBtn)
        segBox.addView(tabPhysicalBtn)
        bar.addView(segBox, wrapParams(rightMargin = dp(8)))

        bar.addView(makeVertDivider(), wrapParams(rightMargin = dp(8)))

        presetPill = makePill("ADV ▾", active = false) { showPresetPicker() }
        bar.addView(presetPill, wrapParams(rightMargin = dp(6)))

        addBtn = makePill(tr("+ 添加", "+ 追加", "+ Add"), active = false) { showAddControlSheet() }
        bar.addView(addBtn, wrapParams(rightMargin = dp(6)))

        gridBtn = makePill(tr("网格", "グリッド", "Grid"), active = false) {
            profile = profile.copy(
                virtual = profile.virtual.copy(gridSnap = !profile.virtual.gridSnap)
            )
            virtualLayer.setProfile(profile)
            updateTopBarState()
        }
        bar.addView(gridBtn, wrapParams(rightMargin = dp(6)))

        globalCfgBtn = makePill(tr("设置", "全体設定", "Options"), active = false) {
            globalPopoverOpen = !globalPopoverOpen
            if (globalPopoverOpen) {
                selectedControlId = null
                virtualLayer.setSelectedControlId(null)
                elementInspectorCard.visibility = View.GONE
                rebuildGlobalPopover()
                globalPopoverCard.visibility = View.VISIBLE
            } else {
                globalPopoverCard.visibility = View.GONE
            }
            updateTopBarState()
        }
        bar.addView(globalCfgBtn, wrapParams(rightMargin = dp(8)))

        bar.addView(makeVertDivider(), wrapParams(rightMargin = dp(8)))

        val resetBtn = makePill(tr("重置", "初期化", "Reset"), active = false) { confirmReset() }
        bar.addView(resetBtn, wrapParams(rightMargin = dp(6)))

        val saveBtn = makePill(tr("保存", "保存", "Save"), active = true) { saveAndFinish() }
        bar.addView(saveBtn)

        scroll.addView(bar)
        return scroll
    }

    private fun buildPhysicalStudio(): LinearLayout {
        val studio = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(56), dp(16), dp(12))
            visibility = View.GONE
        }

        // Left Pane: Live Dual-Stick Tester + Hardware Options
        val leftScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Card 1: Controller Telemetry & Geometric Dual Stick Scopes
        val testerCard = makeStudioCard().apply {
            val statusHeader = LinearLayout(this@GamepadEditorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
            }
            physStatusTv = TextView(this@GamepadEditorActivity).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            listenBtn = makePill(tr("按键识别", "入力検出", "Detect"), active = false) {
                listenPhysicalKey = !listenPhysicalKey
                stylePill(listenBtn, listenPhysicalKey)
                if (listenPhysicalKey) {
                    Toast.makeText(
                        this@GamepadEditorActivity,
                        tr("请按下实体手柄任意按键", "コントローラのボタンを押してください", "Press any gamepad button"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            statusHeader.addView(physStatusTv)
            statusHeader.addView(listenBtn)
            addView(statusHeader)

            stickScopeView = DualStickScopeView(this@GamepadEditorActivity, themePrimary)
            addView(stickScopeView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(96)
            ))

            val stickModeRow = LinearLayout(this@GamepadEditorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            leftStickChip = makePill("", active = false) {
                KeyPickerDialog.showStickModePicker(
                    this@GamepadEditorActivity,
                    tr("左摇杆功能", "左スティック", "Left Stick"),
                    profile.physical.leftStickMode,
                    accentColor = themePrimary
                ) { m ->
                    profile = profile.copy(physical = profile.physical.copy(leftStickMode = m))
                    refreshPhysicalUi()
                }
            }
            rightStickChip = makePill("", active = false) {
                KeyPickerDialog.showStickModePicker(
                    this@GamepadEditorActivity,
                    tr("右摇杆功能", "右スティック", "Right Stick"),
                    profile.physical.rightStickMode,
                    accentColor = themePrimary
                ) { m ->
                    profile = profile.copy(physical = profile.physical.copy(rightStickMode = m))
                    refreshPhysicalUi()
                }
            }
            stickModeRow.addView(leftStickChip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            })
            stickModeRow.addView(rightStickChip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(stickModeRow)
        }
        leftCol.addView(testerCard, studioCardMargin())

        // Card 2: Hardware Options & Analog Sensitivity
        val hwCard = makeStudioCard().apply {
            physEnabledSwitch = Switch(this@GamepadEditorActivity).also { tintSwitch(it) }
            addView(makeSwitchRow(tr("启用实体手柄", "コントローラ有効", "Enable Controller"), physEnabledSwitch) { checked ->
                profile = profile.copy(physical = profile.physical.copy(enabled = checked))
                GamepadBridge.syncProfileToNative(profile)
            })
            addView(makeHorizDivider())

            physSwapSwitch = Switch(this@GamepadEditorActivity).also { tintSwitch(it) }
            addView(makeSwitchRow("A↔B  X↔Y", physSwapSwitch) { checked ->
                profile = profile.copy(physical = profile.physical.copy(swapAbXy = checked))
                refreshPhysicalUi()
            })
            addView(makeHorizDivider())

            physAutoHideSwitch = Switch(this@GamepadEditorActivity).also { tintSwitch(it) }
            addView(makeSwitchRow(tr("接入隐藏虚拟键", "接続時仮想非表示", "Auto-hide Virtual"), physAutoHideSwitch) { checked ->
                profile = profile.copy(physical = profile.physical.copy(autoHideVirtualPad = checked))
            })
            addView(makeHorizDivider())

            physRumbleSwitch = Switch(this@GamepadEditorActivity).also { tintSwitch(it) }
            addView(makeSwitchRow(tr("手柄震动", "振動", "Rumble"), physRumbleSwitch) { checked ->
                profile = profile.copy(physical = profile.physical.copy(rumbleEnabled = checked))
            })
            addView(makeHorizDivider())

            val dzPair = makeCompactSliderRow(tr("死区", "デッドゾーン", "Deadzone"), 40) { p ->
                val v = (p + 5).coerceIn(5, 45)
                profile = profile.copy(physical = profile.physical.copy(deadzonePct = v))
                deadzoneValTv.text = "${v}%"
                stickScopeView.deadzone = v / 100f
                stickScopeView.invalidate()
            }
            deadzoneSeek = dzPair.first
            deadzoneValTv = dzPair.second
            addView(dzPair.third)

            val spPair = makeCompactSliderRow(tr("指针", "ポインタ", "Pointer"), 220) { p ->
                val v = (p + 30).coerceIn(30, 250)
                profile = profile.copy(physical = profile.physical.copy(cursorSpeedPct = v))
                speedValTv.text = "${v}%"
            }
            speedSeek = spPair.first
            speedValTv = spPair.second
            addView(spPair.third)
        }
        leftCol.addView(hwCard)
        leftScroll.addView(leftCol)
        studio.addView(leftScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.96f).apply {
            rightMargin = dp(12)
        })

        // Right Pane: DuckStation / Yuzu Two-Column Grouped Button Mapping Cards
        val rightScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        slotTiles.clear()

        // Group 1: Face Buttons (A / B / X / Y)
        rightCol.addView(makeMappingGroupCard(
            title = tr("动作按键", "アクションボタン", "Face Buttons"),
            headerRight = null,
            slots = listOf(
                GamepadBridge.PAD_A,
                GamepadBridge.PAD_B,
                GamepadBridge.PAD_X,
                GamepadBridge.PAD_Y
            )
        ), studioCardMargin())

        // Group 2: Bumpers & Triggers (LB / RB / LT / RT)
        rightCol.addView(makeMappingGroupCard(
            title = tr("肩键与扳机", "ショルダー・トリガー", "Shoulders & Triggers"),
            headerRight = null,
            slots = listOf(
                GamepadBridge.PAD_LB,
                GamepadBridge.PAD_RB,
                GamepadBridge.PAD_LT,
                GamepadBridge.PAD_RT
            )
        ), studioCardMargin())

        // Group 3: D-Pad (with Default Mode selector chip in header)
        dpadDefaultChip = makePill("", active = false) {
            KeyPickerDialog.showStickModePicker(
                this@GamepadEditorActivity,
                tr("十字键默认功能", "十字キー動作", "D-Pad Default"),
                profile.physical.dpadMode,
                accentColor = themePrimary
            ) { m ->
                profile = profile.copy(physical = profile.physical.copy(dpadMode = m))
                refreshPhysicalUi()
            }
        }
        rightCol.addView(makeMappingGroupCard(
            title = tr("十字方向键", "十字キー", "D-Pad"),
            headerRight = dpadDefaultChip,
            slots = listOf(
                GamepadBridge.PAD_DPAD_UP,
                GamepadBridge.PAD_DPAD_DOWN,
                GamepadBridge.PAD_DPAD_LEFT,
                GamepadBridge.PAD_DPAD_RIGHT
            )
        ), studioCardMargin())

        // Group 4: System & Stick Clicks (Select / Start / L3 / R3 / Home)
        rightCol.addView(makeMappingGroupCard(
            title = tr("系统与摇杆按压", "システム・押し込み", "System & Stick Clicks"),
            headerRight = null,
            slots = listOf(
                GamepadBridge.PAD_BACK,
                GamepadBridge.PAD_START,
                GamepadBridge.PAD_L3,
                GamepadBridge.PAD_R3,
                GamepadBridge.PAD_GUIDE
            )
        ))

        rightScroll.addView(rightCol)
        studio.addView(rightScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.48f))
        return studio
    }

    private fun makeMappingGroupCard(
        title: String,
        headerRight: View?,
        slots: List<Int>
    ): LinearLayout = makeStudioCard().apply {
        val header = LinearLayout(this@GamepadEditorActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(TextView(this@GamepadEditorActivity).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(COL_TEXT_SUB)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (headerRight != null) {
            header.addView(headerRight)
        }
        addView(header)

        val gap = dp(8)
        var i = 0
        while (i < slots.size) {
            val row = LinearLayout(this@GamepadEditorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                if (i + 2 < slots.size) setPadding(0, 0, 0, gap)
            }
            for (c in 0..1) {
                val slot = slots.getOrNull(i + c)
                if (slot != null) {
                    val tile = buildSlotTile(slot)
                    row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (c == 0) rightMargin = gap
                    })
                } else {
                    row.addView(View(this@GamepadEditorActivity), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
            addView(row)
            i += 2
        }
    }

    private fun buildSlotTile(slot: Int): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = tileDrawable(pressed = false)
            setOnClickListener { openPhysicalSlotPicker(slot) }
        }

        val isCircleCap = slot in GamepadBridge.PAD_A..GamepadBridge.PAD_Y ||
            slot in GamepadBridge.PAD_DPAD_UP..GamepadBridge.PAD_DPAD_RIGHT
        val badge = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCircleCap) 12.5f else 11f)
            setTypeface(null, Typeface.BOLD)
            minWidth = dp(28)
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }
        tile.addView(badge, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (isCircleCap) dp(28) else ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            rightMargin = dp(8)
        })

        val actionTv = TextView(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tile.addView(actionTv)

        slotTiles[slot] = SlotTileRefs(tile, badge, actionTv)
        return tile
    }

    private fun updateSlotTilesUi() {
        for ((slot, refs) in slotTiles) {
            val pressed = (pressedMask and (1 shl slot)) != 0
            refs.container.background = tileDrawable(pressed)

            val capText = slotCapLabel(slot, profile.physical.swapAbXy)
            refs.keyBadge.text = capText
            val capColor = slotCapAccent(slot, profile.physical.swapAbXy)
            if (capColor != null) {
                refs.keyBadge.setTextColor(0xFF0A1018.toInt())
                refs.keyBadge.background = GradientDrawable().apply {
                    cornerRadius = dp(99).toFloat()
                    setColor(capColor)
                }
            } else {
                refs.keyBadge.setTextColor(COL_TEXT_MAIN)
                refs.keyBadge.background = GradientDrawable().apply {
                    cornerRadius = dp(7).toFloat()
                    setColor(0xFF303646.toInt())
                }
            }

            val spec = profile.physical.bindings[slot] ?: GamepadActionSpec(GamepadBridge.ACTION_NONE, 0)
            val isDpad = slot in GamepadBridge.PAD_DPAD_UP..GamepadBridge.PAD_DPAD_RIGHT
            if (spec.type == GamepadBridge.ACTION_NONE && isDpad) {
                refs.actionTv.text = GamepadBridge.stickModeLabel(this, profile.physical.dpadMode)
                refs.actionTv.setTextColor(COL_TEXT_DIM)
                refs.actionTv.setTypeface(null, Typeface.NORMAL)
            } else if (spec.type == GamepadBridge.ACTION_NONE) {
                refs.actionTv.text = tr("未设", "なし", "None")
                refs.actionTv.setTextColor(COL_TEXT_DIM)
                refs.actionTv.setTypeface(null, Typeface.NORMAL)
            } else {
                refs.actionTv.text = GamepadBridge.formatActionLabel(this, spec)
                refs.actionTv.setTextColor(if (pressed) themePrimary else COL_TEXT_MAIN)
                refs.actionTv.setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun slotCapLabel(slot: Int, swap: Boolean): String = when (slot) {
        GamepadBridge.PAD_A -> if (swap) "B" else "A"
        GamepadBridge.PAD_B -> if (swap) "A" else "B"
        GamepadBridge.PAD_X -> if (swap) "Y" else "X"
        GamepadBridge.PAD_Y -> if (swap) "X" else "Y"
        GamepadBridge.PAD_LB -> "LB"
        GamepadBridge.PAD_RB -> "RB"
        GamepadBridge.PAD_LT -> "LT"
        GamepadBridge.PAD_RT -> "RT"
        GamepadBridge.PAD_BACK -> "Select"
        GamepadBridge.PAD_START -> "Start"
        GamepadBridge.PAD_GUIDE -> "Home"
        GamepadBridge.PAD_L3 -> "L3"
        GamepadBridge.PAD_R3 -> "R3"
        GamepadBridge.PAD_DPAD_UP -> "↑"
        GamepadBridge.PAD_DPAD_DOWN -> "↓"
        GamepadBridge.PAD_DPAD_LEFT -> "←"
        GamepadBridge.PAD_DPAD_RIGHT -> "→"
        else -> "#$slot"
    }

    private fun slotCapAccent(slot: Int, swap: Boolean): Int? = when (slot) {
        GamepadBridge.PAD_A -> if (swap) BADGE_B else BADGE_A
        GamepadBridge.PAD_B -> if (swap) BADGE_A else BADGE_B
        GamepadBridge.PAD_X -> if (swap) BADGE_Y else BADGE_X
        GamepadBridge.PAD_Y -> if (swap) BADGE_X else BADGE_Y
        else -> null
    }

    private fun applyTab(tab: Int) {
        activeTab = tab
        val isVirtual = tab == TAB_VIRTUAL
        backdropView.visibility = if (isVirtual) View.VISIBLE else View.GONE
        virtualLayer.visibility = if (isVirtual) View.VISIBLE else View.GONE
        virtualLayer.setEditMode(isVirtual, selectedControlId)
        physicalContainer.visibility = if (isVirtual) View.GONE else View.VISIBLE

        if (!isVirtual) {
            elementInspectorCard.visibility = View.GONE
            globalPopoverOpen = false
            globalPopoverCard.visibility = View.GONE
        } else if (selectedControlId != null) {
            rebuildElementInspector()
        }

        addBtn.visibility = if (isVirtual) View.VISIBLE else View.GONE
        gridBtn.visibility = if (isVirtual) View.VISIBLE else View.GONE
        globalCfgBtn.visibility = if (isVirtual) View.VISIBLE else View.GONE

        styleSegmentItem(tabVirtualBtn, isVirtual)
        styleSegmentItem(tabPhysicalBtn, !isVirtual)
        updateTopBarState()
    }

    private fun refreshAllUi() {
        updateTopBarState()
        virtualLayer.setProfile(profile)
        virtualLayer.setEditMode(activeTab == TAB_VIRTUAL, selectedControlId)
        if (activeTab == TAB_VIRTUAL && selectedControlId != null) {
            rebuildElementInspector()
        } else {
            elementInspectorCard.visibility = View.GONE
        }
        if (globalPopoverOpen) {
            rebuildGlobalPopover()
        }
        refreshPhysicalUi()
    }

    private fun refreshPhysicalUi() {
        syncingUi = true
        physEnabledSwitch.isChecked = profile.physical.enabled
        physSwapSwitch.isChecked = profile.physical.swapAbXy
        physAutoHideSwitch.isChecked = profile.physical.autoHideVirtualPad
        physRumbleSwitch.isChecked = profile.physical.rumbleEnabled
        deadzoneSeek.progress = (profile.physical.deadzonePct - 5).coerceIn(0, 40)
        deadzoneValTv.text = "${profile.physical.deadzonePct}%"
        speedSeek.progress = (profile.physical.cursorSpeedPct - 30).coerceIn(0, 220)
        speedValTv.text = "${profile.physical.cursorSpeedPct}%"
        syncingUi = false

        stickScopeView.deadzone = profile.physical.deadzonePct / 100f
        leftStickChip.text = "L · ${GamepadBridge.stickModeLabel(this, profile.physical.leftStickMode)} ▾"
        rightStickChip.text = "R · ${GamepadBridge.stickModeLabel(this, profile.physical.rightStickMode)} ▾"
        dpadDefaultChip.text = "${tr("默认", "標準", "Mode")} · ${GamepadBridge.stickModeLabel(this, profile.physical.dpadMode)} ▾"

        stylePill(listenBtn, listenPhysicalKey)
        updateSlotTilesUi()
        pollControllerTelemetry()
    }

    private fun updateTopBarState() {
        val presetName = when (profile.virtual.presetId) {
            GamepadBridge.PRESET_RPG_DUNGEON -> "RPG"
            GamepadBridge.PRESET_QUICK_BAR -> tr("极简", "ミニ", "Bar")
            else -> "ADV"
        }
        presetPill.text = if (activeTab == TAB_VIRTUAL) {
            "$presetName ▾"
        } else {
            "${tr("预设", "プリセット", "Preset")} ▾"
        }
        stylePill(gridBtn, profile.virtual.gridSnap)
        stylePill(globalCfgBtn, globalPopoverOpen)
    }

    private fun rebuildGlobalPopover() {
        globalPopoverCard.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(TextView(this).apply {
            text = tr("虚拟按键全局设置", "仮想パッド全体設定", "Overlay Options")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(COL_TEXT_MAIN)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(makePill("✕", active = false) {
            globalPopoverOpen = false
            globalPopoverCard.visibility = View.GONE
            updateTopBarState()
        })
        globalPopoverCard.addView(header)

        val enSw = Switch(this).also {
            tintSwitch(it)
            it.isChecked = profile.virtual.enabled
        }
        globalPopoverCard.addView(makeSwitchRow(tr("启用虚拟按键", "仮想パッド表示", "Enable Virtual Pad"), enSw) { checked ->
            profile = profile.copy(virtual = profile.virtual.copy(enabled = checked))
            virtualLayer.setProfile(profile)
        })
        globalPopoverCard.addView(makeHorizDivider())

        val hapSw = Switch(this).also {
            tintSwitch(it)
            it.isChecked = profile.virtual.hapticFeedback
        }
        globalPopoverCard.addView(makeSwitchRow(tr("按键触感", "タッチ振動", "Touch Haptics"), hapSw) { checked ->
            profile = profile.copy(virtual = profile.virtual.copy(hapticFeedback = checked))
        })

        if (profileKey != null) {
            globalPopoverCard.addView(makeHorizDivider())
            val gameSw = Switch(this).also {
                tintSwitch(it)
                it.isChecked = perGameOverride
            }
            globalPopoverCard.addView(makeSwitchRow(tr("本游戏独立配置", "ゲーム別設定", "Per-Game Profile"), gameSw) { checked ->
                perGameOverride = checked
            })
        }

        globalPopoverCard.addView(makeHorizDivider())
        lateinit var scVal: TextView
        val scRow = makeCompactSliderRow(tr("整体大小", "全体サイズ", "Scale"), 90) { p ->
            val v = (p + 60).coerceIn(60, 150)
            profile = profile.copy(virtual = profile.virtual.copy(globalScalePct = v))
            scVal.text = "${v}%"
            virtualLayer.setProfile(profile)
        }
        scRow.first.progress = (profile.virtual.globalScalePct - 60).coerceIn(0, 90)
        scVal = scRow.second
        scVal.text = "${profile.virtual.globalScalePct}%"
        globalPopoverCard.addView(scRow.third)

        lateinit var opVal: TextView
        val opRow = makeCompactSliderRow(tr("整体透明", "不透明度", "Opacity"), 80) { p ->
            val v = (p + 20).coerceIn(20, 100)
            profile = profile.copy(virtual = profile.virtual.copy(globalOpacityPct = v))
            opVal.text = "${v}%"
            virtualLayer.setProfile(profile)
        }
        opRow.first.progress = (profile.virtual.globalOpacityPct - 20).coerceIn(0, 80)
        opVal = opRow.second
        opVal.text = "${profile.virtual.globalOpacityPct}%"
        globalPopoverCard.addView(opRow.third)
    }

    private fun positionElementInspector(sel: VirtualControlSpec) {
        val lp = elementInspectorCard.layoutParams as? FrameLayout.LayoutParams ?: return
        val placeTop = sel.yNorm >= 0.52f
        val desiredGravity = (if (placeTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
        if (lp.gravity != desiredGravity) {
            lp.gravity = desiredGravity
            lp.topMargin = if (placeTop) dp(54) else 0
            lp.bottomMargin = if (placeTop) 0 else dp(12)
            elementInspectorCard.layoutParams = lp
        }
    }

    private fun rebuildElementInspector() {
        val sel = selectedControlId?.let { id ->
            profile.virtual.controls.firstOrNull { it.id == id }
        }
        if (sel == null) {
            elementInspectorCard.visibility = View.GONE
            return
        }
        positionElementInspector(sel)
        elementInspectorCard.removeAllViews()
        elementInspectorCard.alpha = 1.0f
        elementInspectorCard.visibility = View.VISIBLE

        // Row 1: Name + Action/Mode + Shape + Lock + Show/Hide + Delete + Close
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val nameChip = makePill("${sel.label.ifBlank { "?" }} ✎", active = false) {
            showRenameDialog(sel)
        }
        row1.addView(nameChip, wrapParams(rightMargin = dp(6)))

        if (sel.kind == VirtualControlKind.BUTTON) {
            val actChip = makePill("${GamepadBridge.formatActionLabel(this, sel.action)} ▾", active = true) {
                KeyPickerDialog.showActionPicker(
                    this,
                    "${sel.label} · ${tr("绑定功能", "割当", "Bind")}",
                    sel.action,
                    allowNone = true,
                    accentColor = themePrimary
                ) { chosen ->
                    mutateControl(sel.id) { it.copy(action = chosen) }
                }
            }
            row1.addView(actChip, wrapParams(rightMargin = dp(6)))

            val shapeChip = makePill(GamepadBridge.shapeLabel(this, sel.shape), active = false) {
                mutateControl(sel.id) { it.copy(shape = GamepadBridge.nextButtonShape(it.shape)) }
            }
            row1.addView(shapeChip, wrapParams(rightMargin = dp(6)))

            val lockChip = makePill(tr("锁定", "固定", "Lock"), active = sel.action.toggleMode) {
                mutateControl(sel.id) { it.copy(action = it.action.copy(toggleMode = !it.action.toggleMode)) }
            }
            row1.addView(lockChip, wrapParams(rightMargin = dp(6)))
        } else {
            val modeChip = makePill("${GamepadBridge.stickModeLabel(this, sel.stickMode)} ▾", active = true) {
                KeyPickerDialog.showStickModePicker(
                    this,
                    sel.label,
                    sel.stickMode,
                    allowWheel = true,
                    accentColor = themePrimary
                ) { m ->
                    mutateControl(sel.id) { it.copy(stickMode = m) }
                }
            }
            row1.addView(modeChip, wrapParams(rightMargin = dp(6)))
        }

        val visChip = makePill(
            if (sel.visible) tr("显示", "表示", "Visible") else tr("已隐藏", "非表示", "Hidden"),
            active = sel.visible
        ) {
            mutateControl(sel.id) { it.copy(visible = !it.visible) }
        }
        row1.addView(visChip, wrapParams(rightMargin = dp(6)))

        if (sel.isCustom) {
            val delChip = makePill(tr("删除", "削除", "Del"), active = false) {
                val remaining = profile.virtual.controls.filterNot { it.id == sel.id }
                profile = profile.copy(virtual = profile.virtual.copy(controls = remaining))
                selectedControlId = null
                virtualLayer.setProfile(profile)
                virtualLayer.setEditMode(true, null)
                elementInspectorCard.visibility = View.GONE
            }.apply {
                setTextColor(COL_DANGER)
            }
            row1.addView(delChip, wrapParams(rightMargin = dp(6)))
        }

        val closeBtn = makePill("✕", active = false) {
            selectedControlId = null
            virtualLayer.setSelectedControlId(null)
            elementInspectorCard.visibility = View.GONE
        }
        row1.addView(closeBtn)
        elementInspectorCard.addView(row1)

        // Row 2: Size + Opacity inline sliders
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val curSizePct = (sel.scale * 100f).toInt()
        val sizeValTv = TextView(this).apply {
            text = "${curSizePct}%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(COL_TEXT_SUB)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val sizeSeek = SeekBar(this).apply {
            tintSeekBar(this)
            max = 100
            progress = ((sel.scale - 0.6f) * 100f).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(simpleSeekListener { p ->
                val sc = (0.6f + p / 100f).coerceIn(0.6f, 1.6f)
                sizeValTv.text = "${(sc * 100f).toInt()}%"
                mutateControl(sel.id, rebuildBar = false) { it.copy(scale = sc) }
            })
        }
        row2.addView(TextView(this).apply {
            text = tr("大小", "サイズ", "Size")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COL_TEXT_SUB)
        })
        row2.addView(sizeSeek, LinearLayout.LayoutParams(dp(105), ViewGroup.LayoutParams.WRAP_CONTENT))
        row2.addView(sizeValTv, LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(14)
        })

        val curOpPct = (sel.opacity * 100f).toInt()
        val opValTv = TextView(this).apply {
            text = "${curOpPct}%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(COL_TEXT_SUB)
            gravity = Gravity.END
        }
        val opSeek = SeekBar(this).apply {
            tintSeekBar(this)
            max = 70
            progress = ((sel.opacity - 0.3f) * 100f).toInt().coerceIn(0, 70)
            setOnSeekBarChangeListener(simpleSeekListener { p ->
                val op = (0.3f + p / 100f).coerceIn(0.3f, 1.0f)
                opValTv.text = "${(op * 100f).toInt()}%"
                mutateControl(sel.id, rebuildBar = false) { it.copy(opacity = op) }
            })
        }
        row2.addView(TextView(this).apply {
            text = tr("透明", "不透明", "Alpha")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COL_TEXT_SUB)
        })
        row2.addView(opSeek, LinearLayout.LayoutParams(dp(105), ViewGroup.LayoutParams.WRAP_CONTENT))
        row2.addView(opValTv, LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT))

        elementInspectorCard.addView(row2)
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
        if (rebuildBar) rebuildElementInspector()
    }

    private fun showRenameDialog(spec: VirtualControlSpec) {
        val input = EditText(this).apply {
            setText(spec.label)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(COL_TEXT_MAIN)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(tr("按键标签", "ラベル", "Label"))
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

    private fun showPresetPicker() {
        if (activeTab == TAB_VIRTUAL) {
            val names = arrayOf(
                tr("ADV 鼠键布局", "ADV 標準", "ADV Mouse"),
                tr("RPG 方向布局", "RPG 標準", "RPG Dungeon"),
                tr("极简快捷栏", "ミニバー", "Quick Bar")
            )
            val ids = arrayOf(
                GamepadBridge.PRESET_VN_MOUSE,
                GamepadBridge.PRESET_RPG_DUNGEON,
                GamepadBridge.PRESET_QUICK_BAR
            )
            AlertDialog.Builder(this)
                .setTitle(tr("选择布局预设", "プリセット", "Layout Preset"))
                .setItems(names) { _, which ->
                    val id = ids[which]
                    profile = profile.copy(
                        virtual = profile.virtual.copy(
                            presetId = id,
                            controls = GamepadBridge.buildPresetControls(id)
                        )
                    )
                    selectedControlId = null
                    refreshAllUi()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            val names = arrayOf(
                tr("ADV 鼠键映射", "ADV マップ", "ADV Mapping"),
                tr("RPG 键盘映射", "RPG マップ", "RPG Mapping")
            )
            AlertDialog.Builder(this)
                .setTitle(tr("选择实体手柄预设", "プリセット", "Controller Preset"))
                .setItems(names) { _, which ->
                    profile = if (which == 0) {
                        profile.copy(
                            physical = profile.physical.copy(
                                leftStickMode = GamepadBridge.STICK_MOUSE,
                                rightStickMode = GamepadBridge.STICK_WHEEL,
                                dpadMode = GamepadBridge.STICK_ARROWS,
                                bindings = GamepadBridge.defaultPhysicalBindings()
                            )
                        )
                    } else {
                        profile.copy(
                            physical = profile.physical.copy(
                                leftStickMode = GamepadBridge.STICK_WASD,
                                rightStickMode = GamepadBridge.STICK_MOUSE,
                                dpadMode = GamepadBridge.STICK_ARROWS,
                                bindings = GamepadBridge.rpgPhysicalBindings()
                            )
                        )
                    }
                    refreshPhysicalUi()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
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
            tr("添加自定义按键", "ボタン追加", "Add Button"),
            tr("添加右摇杆", "右スティック追加", "Add Right Stick")
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
                    profile = profile.copy(virtual = profile.virtual.copy(controls = profile.virtual.controls + stick))
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
            "${newBtn.label} · ${tr("绑定功能", "割当", "Bind")}",
            newBtn.action,
            allowNone = true,
            accentColor = themePrimary
        ) { chosen ->
            val shortLabel = GamepadBridge.formatActionLabel(this, chosen).take(5).ifBlank { newBtn.label }
            mutateControl(newBtn.id) { it.copy(action = chosen, label = shortLabel) }
        }
    }

    private fun openPhysicalSlotPicker(slot: Int) {
        val current = profile.physical.bindings[slot] ?: GamepadActionSpec(GamepadBridge.ACTION_NONE, 0)
        val slotLabel = slotCapLabel(slot, profile.physical.swapAbXy)
        KeyPickerDialog.showActionPicker(
            this,
            "$slotLabel · ${tr("按键映射", "ボタン割当", "Button Mapping")}",
            current,
            allowNone = true,
            accentColor = themePrimary
        ) { chosen ->
            val next = profile.physical.bindings.toMutableMap()
            next[slot] = chosen
            profile = profile.copy(physical = profile.physical.copy(bindings = next))
            GamepadBridge.syncProfileToNative(profile)
            updateSlotTilesUi()
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(tr("恢复默认", "初期化", "Reset"))
            .setMessage(tr("恢复默认布局并将所有开关重置为关闭？", "初期状態に戻しますか？", "Restore all defaults?"))
            .setPositiveButton(R.string.ok) { _, _ ->
                if (perGameOverride && profileKey != null) {
                    GamepadBridge.clearPerGameOverride(this, profileKey)
                    perGameOverride = false
                }
                profile = GamepadProfile()
                selectedControlId = null
                globalPopoverOpen = false
                globalPopoverCard.visibility = View.GONE
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
                    pressedMask = if (isDown) pressedMask or (1 shl slot) else pressedMask and (1 shl slot).inv()
                    if (activeTab == TAB_PHYSICAL) {
                        updateSlotTilesUi()
                    }
                    if (isDown && activeTab == TAB_PHYSICAL && listenPhysicalKey) {
                        listenPhysicalKey = false
                        stylePill(listenBtn, false)
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
            System.arraycopy(axes, 0, stickScopeView.axes, 0, 6)
            stickScopeView.invalidate()
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun pollControllerTelemetry() {
        if (activeTab != TAB_PHYSICAL) return
        val pads = GamepadBridge.queryConnectedControllers(this)
        if (pads.isNotEmpty()) {
            physStatusTv.text = "● ${pads.first().name}"
            physStatusTv.setTextColor(COL_SUCCESS)
        } else {
            physStatusTv.text = "○ ${tr("未接入手柄", "未接続", "No controller")}"
            physStatusTv.setTextColor(COL_TEXT_DIM)
        }
        if (!GameModifierBridge.ensureLoaded()) return
        try {
            val raw = GamepadBridge.nativeGetControllerStatusJson()
            if (raw.isNotBlank()) {
                val obj = JSONObject(raw)
                val mask = obj.optInt("buttonsMask", 0)
                if (mask != 0 || pads.isNotEmpty()) {
                    pressedMask = mask
                }
                val axesArr = obj.optJSONArray("axes")
                if (axesArr != null && axesArr.length() >= 6) {
                    for (i in 0 until 6) {
                        stickScopeView.axes[i] = axesArr.optDouble(i, 0.0).toFloat()
                    }
                }
                stickScopeView.invalidate()
                if (pressedMask != prevVisualPressedMask) {
                    prevVisualPressedMask = pressedMask
                    updateSlotTilesUi()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun floatingCardDrawable(radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = UiKit.dp(this@GamepadEditorActivity, radiusDp)
        setColor(COL_FLOATING)
        setStroke(dp(1), COL_OUTLINE)
    }

    private fun makeStudioCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(COL_SURFACE)
            setStroke(dp(1), COL_OUTLINE)
        }
    }

    private fun studioCardMargin(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(10)
    }

    private fun tileDrawable(pressed: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(if (pressed) themeTileActive else COL_TILE)
        setStroke(dp(1), if (pressed) themePrimary else COL_OUTLINE)
    }

    private fun makePill(label: String, active: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(11), dp(5), dp(11), dp(5))
            stylePill(this, active)
            setOnClickListener { onClick() }
        }

    private fun stylePill(tv: TextView, active: Boolean) {
        tv.setTextColor(if (active) themeOnPrimary else COL_TEXT_MAIN)
        tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(99).toFloat()
            setColor(if (active) themePrimary else COL_CHIP)
            if (!active) setStroke(dp(1), COL_OUTLINE)
        }
    }

    private fun makeSegmentItem(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { onClick() }
        }

    private fun styleSegmentItem(tv: TextView, active: Boolean) {
        tv.setTextColor(if (active) themeOnPrimary else COL_TEXT_SUB)
        tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        tv.background = if (active) {
            GradientDrawable().apply {
                cornerRadius = dp(99).toFloat()
                setColor(themePrimary)
            }
        } else {
            null
        }
    }

    private fun makeVertDivider(): View = View(this).apply {
        setBackgroundColor(COL_OUTLINE)
        layoutParams = LinearLayout.LayoutParams(dp(1).coerceAtLeast(1), dp(16))
    }

    private fun makeHorizDivider(): View = View(this).apply {
        setBackgroundColor(COL_DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1).coerceAtLeast(1)
        ).apply {
            topMargin = dp(5)
            bottomMargin = dp(5)
        }
    }

    private fun makeSwitchRow(label: String, sw: Switch, onChanged: (Boolean) -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(COL_TEXT_MAIN)
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

    private fun makeCompactSliderRow(
        label: String,
        maxProgress: Int,
        onProgress: (Int) -> Unit
    ): Triple<SeekBar, TextView, View> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val lbl = TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(COL_TEXT_SUB)
            layoutParams = LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val seek = SeekBar(this).apply {
            tintSeekBar(this)
            max = maxProgress
            setOnSeekBarChangeListener(simpleSeekListener(onProgress))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COL_TEXT_SUB)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        row.addView(lbl)
        row.addView(seek)
        row.addView(valTv)
        return Triple(seek, valTv, row)
    }

    private fun simpleSeekListener(onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !syncingUi) onProgress(progress)
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
     * Minimalist 16:9 game viewport corner brackets for virtual touch alignment.
     */
    private class ViewportFrameView(context: Context, accentColor: Int) : View(context) {
        private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiKit.withAlpha(accentColor, 0x3A)
            style = Paint.Style.STROKE
            strokeWidth = UiKit.dp(context, 1.5f)
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x10FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val ratio = 16f / 9f
            val fw = min(w * 0.92f, h * 0.88f * ratio)
            val fh = fw / ratio
            val l = (w - fw) * 0.5f
            val t = (h - fh) * 0.5f
            val r = l + fw
            val b = t + fh
            rect.set(l, t, r, b)
            canvas.drawRect(rect, borderPaint)

            val tick = UiKit.dp(context, 14f)
            // Top-left
            canvas.drawLine(l, t, l + tick, t, bracketPaint)
            canvas.drawLine(l, t, l, t + tick, bracketPaint)
            // Top-right
            canvas.drawLine(r - tick, t, r, t, bracketPaint)
            canvas.drawLine(r, t, r, t + tick, bracketPaint)
            // Bottom-left
            canvas.drawLine(l, b - tick, l, b, bracketPaint)
            canvas.drawLine(l, b, l + tick, b, bracketPaint)
            // Bottom-right
            canvas.drawLine(r - tick, b, r, b, bracketPaint)
            canvas.drawLine(r, b - tick, r, b, bracketPaint)
        }
    }

    /**
     * DuckStation / GamepadTester style geometric dual analog stick & trigger scope.
     */
    private class DualStickScopeView(context: Context, private val accentColor: Int) : View(context) {
        val axes = FloatArray(6) // lx, ly, rx, ry, lt, rt
        var deadzone: Float = 0.18f

        private val wellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_BG
            style = Paint.Style.FILL
        }
        private val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_OUTLINE
            style = Paint.Style.STROKE
            strokeWidth = UiKit.dp(context, 1.2f)
        }
        private val dzStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiKit.withAlpha(accentColor, 0x26)
            style = Paint.Style.STROKE
            strokeWidth = UiKit.dp(context, 1f)
        }
        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x16FFFFFF
            strokeWidth = 1f
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val barTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF242936.toInt()
        }
        private val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_TEXT_DIM
            textSize = UiKit.dp(context, 9.5f)
        }
        private val tmpRect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            // LT & RT thin horizontal analog bars at top
            val barH = UiKit.dp(context, 5f)
            val halfW = (w - UiKit.dp(context, 16f)) * 0.5f
            drawTriggerBar(canvas, 0f, UiKit.dp(context, 2f), halfW, barH, "LT", axes[4])
            drawTriggerBar(canvas, w - halfW, UiKit.dp(context, 2f), halfW, barH, "RT", axes[5])

            val topArea = UiKit.dp(context, 16f)
            val availH = (h - topArea).coerceAtLeast(10f)
            val r = min(availH * 0.44f, w * 0.20f)
            val cy = topArea + availH * 0.52f
            drawScope(canvas, w * 0.26f, cy, r, axes[0], axes[1])
            drawScope(canvas, w * 0.74f, cy, r, axes[2], axes[3])
        }

        private fun drawTriggerBar(
            canvas: Canvas,
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            label: String,
            value: Float
        ) {
            val lblW = UiKit.dp(context, 18f)
            canvas.drawText(label, x, y + h + UiKit.dp(context, 2f), textPaint)
            val bx = x + lblW
            val bw = (w - lblW).coerceAtLeast(4f)
            tmpRect.set(bx, y, bx + bw, y + h)
            canvas.drawRoundRect(tmpRect, h * 0.5f, h * 0.5f, barTrack)
            val v = value.coerceIn(0f, 1f)
            if (v > 0.01f) {
                tmpRect.set(bx, y, bx + bw * v, y + h)
                canvas.drawRoundRect(tmpRect, h * 0.5f, h * 0.5f, barFill)
            }
        }

        private fun drawScope(canvas: Canvas, cx: Float, cy: Float, r: Float, ax: Float, ay: Float) {
            canvas.drawCircle(cx, cy, r, wellFill)
            canvas.drawLine(cx - r, cy, cx + r, cy, crossPaint)
            canvas.drawLine(cx, cy - r, cx, cy + r, crossPaint)
            canvas.drawCircle(cx, cy, r * deadzone.coerceIn(0.05f, 0.45f), dzStroke)
            canvas.drawCircle(cx, cy, r, ringStroke)

            val mag = hypot(ax.toDouble(), ay.toDouble()).toFloat()
            val px = cx + ax.coerceIn(-1f, 1f) * r * 0.84f
            val py = cy + ay.coerceIn(-1f, 1f) * r * 0.84f
            dotPaint.color = if (mag > deadzone) accentColor else 0xFF6B7385.toInt()
            canvas.drawCircle(px, py, UiKit.dp(context, 4.5f), dotPaint)
        }
    }
}
