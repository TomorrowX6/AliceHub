package io.github.kichikuou.xsystem4

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.SparseArray
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.libsdl.app.SDLActivity
import kotlin.math.abs

// Intent for this activity must have the following extras:
// - EXTRA_GAME_ROOT (string): A path to the game installation.
// - EXTRA_SAVE_DIR (string): A path to a directory where save files are stored.
class XSystem4Activity : SDLActivity() {
    companion object {
        const val EXTRA_GAME_ROOT = "GAME_ROOT"
        const val EXTRA_SAVE_DIR = "SAVE_DIR"
        const val COMMAND_OPEN_PLAYING_MANUAL = 0x8000  // xsystem4/src/hll/SystemService.c
        const val COMMAND_SHOW_WAIT_MESSAGE = 0x8001
        const val COMMAND_TOGGLE_CTRL_OVERLAY = 0x8002  // xsystem4/src/input.c
        const val COMMAND_MOVIE_SKIP_STATE = 0x8003  // xsystem4/src/input.c
        const val COMMAND_CURSOR_STATE = 0x8004      // xsystem4/src/input.c
        const val COMMAND_GAMEPAD_UI_EVENT = 0x8005  // xsystem4/src/input.c

        private const val GAMEPAD_UI_EMU_CMD = 1
        private const val GAMEPAD_UI_CONTROLLER_COUNT = 2
        private const val MOVIE_SKIP_UI_HIDDEN = 0
        private const val SDL_MESSAGEBOX_ERROR = 0x00000010
        private const val SDL_BUTTON_RETURNKEY_DEFAULT = 0x00000001
        private const val SDL_BUTTON_ESCAPEKEY_DEFAULT = 0x00000002
        private const val SENTINEL_ALICEHUB_QUIT = "__ALICEHUB_QUIT__"
    }

    private var loadingLabel: TextView? = null
    private var modifierOverlay: FloatingModifierOverlay? = null
    private var ctrlOverlayContainer: FrameLayout? = null
    private var ctrlPill: TextView? = null
    private var ctrlSkipLatched: Boolean = false
    private var lastSyncedConfigSeq: Int = -1
    private var activeMessageBoxDialog: Dialog? = null
    private var lastBackDispatchMs: Long = 0L
    private var backHandledInCurrentGesture: Boolean = false
    private var backInvokedCallback: Any? = null

    private var virtualCursorView: VirtualCursorOverlayView? = null
    private var virtualGamepadView: VirtualGamepadView? = null
    private var layoutEditorContainer: FrameLayout? = null
    private var layoutEditorGridBtn: Button? = null
    private var layoutEditorHintTv: TextView? = null
    private var activeGamepadProfile: GamepadProfile = GamepadProfile()
    private var nativePadCount: Int = 0
    private var lastReportedConnectedPads: Int = 0
    private var inputDeviceListener: InputManager.InputDeviceListener? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrapThemeContext(newBase))
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    fun isRance10Game(): Boolean {
        val root = intent?.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveName = intent?.getStringExtra(EXTRA_SAVE_DIR)?.takeIf { it.isNotBlank() }?.let { java.io.File(it).name }
        return GameModifierBridge.isRance10GameDir(root, saveName)
    }

    fun isDohnaDohnaGame(): Boolean {
        val root = intent?.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveDir = intent?.getStringExtra(EXTRA_SAVE_DIR)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        return GameModifierBridge.isDohnaDohnaGameDir(root, saveDir?.name, saveDir)
    }

    fun isEvenicleGame(): Boolean {
        val root = intent?.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveDir = intent?.getStringExtra(EXTRA_SAVE_DIR)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        return GameModifierBridge.isEvenicleGameDir(root, saveDir?.name, saveDir)
    }

    fun isSengokuRanceGame(): Boolean {
        val root = intent?.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveDir = intent?.getStringExtra(EXTRA_SAVE_DIR)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        return GameModifierBridge.isSengokuRanceGameDir(root, saveDir?.name, saveDir)
    }

    fun isRance03Game(): Boolean {
        val root = intent?.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveDir = intent?.getStringExtra(EXTRA_SAVE_DIR)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        return GameModifierBridge.isRance03GameDir(root, saveDir?.name, saveDir)
    }

    fun isRance08Game(): Boolean {
        val root = intent?.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveDir = intent?.getStringExtra(EXTRA_SAVE_DIR)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        return GameModifierBridge.isRance08GameDir(root, saveDir?.name, saveDir)
    }

    fun isRance09Game(): Boolean {
        val root = intent?.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveDir = intent?.getStringExtra(EXTRA_SAVE_DIR)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        return GameModifierBridge.isRance09GameDir(root, saveDir?.name, saveDir)
    }

    private fun syncAllLocksToNative() {
        GameModifierBridge.nativeSetLiveLocks(GameModifierBridge.getSavedLockMask(this))
        GameModifierBridge.setDohnaLiveLocks(GameModifierBridge.getSavedDohnaLockMask(this))
        GameModifierBridge.setEvenicleLiveLocks(GameModifierBridge.getSavedEvenicleLockMask(this))
        GameModifierBridge.setSrLiveLocks(GameModifierBridge.getSavedSrLockMask(this))
        GameModifierBridge.setR03LiveLocks(GameModifierBridge.getSavedR03LockMask(this))
        GameModifierBridge.setR08LiveLocks(GameModifierBridge.getSavedR08LockMask(this))
        GameModifierBridge.setR09LiveLocks(GameModifierBridge.getSavedR09LockMask(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ErrorReportKit.installUncaughtExceptionHandler(this)
        ErrorReportKit.ensureSeenBaselineInitialized(this, offsetMs = -1000L)
        super.onCreate(savedInstanceState)
        // Workaround for https://github.com/libsdl-org/SDL/issues/8995
        SDLActivity.setWindowStyle(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val label = TextView(this).apply {
            text = getString(R.string.loading_game)
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            val padding = dp(12)
            setPadding(padding, padding, padding, padding)
            visibility = View.GONE
        }
        loadingLabel = label
        addContentView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        GameModifierBridge.markAlreadyLoaded()
        try {
            GameModifierBridge.syncNativeConfigPath(this)
            GameModifierBridge.syncUiScaleToNative(this)
            syncAllLocksToNative()
            lastSyncedConfigSeq = GameModifierBridge.getConfigSeq(this)
            activeGamepadProfile = GamepadBridge.loadAndSyncToNative(this, currentGamepadProfileKey())
        } catch (_: Throwable) {
        }
        registerControllerListener()
        window.decorView.postDelayed({ ensureOverlaysAttached() }, 1800L)
        registerModernBackCallback()
    }

    fun currentGamepadProfileKey(): String? =
        GamepadBridge.normalizeGameProfileKey(intent?.getStringExtra(EXTRA_GAME_ROOT))

    fun getActiveGamepadProfile(): GamepadProfile = activeGamepadProfile

    fun applyGamepadProfile(profile: GamepadProfile) {
        activeGamepadProfile = profile
        GamepadBridge.syncProfileToNative(profile)
        virtualGamepadView?.setProfile(profile)
        updatePhysicalPadPresence(showToast = false)
    }

    fun toggleVirtualGamepadEnabled(): Boolean {
        val toggled = activeGamepadProfile.copy(
            virtual = activeGamepadProfile.virtual.copy(enabled = !activeGamepadProfile.virtual.enabled)
        )
        val saved = GamepadBridge.saveProfile(
            this,
            toggled,
            currentGamepadProfileKey(),
            activeGamepadProfile.perGameOverride
        )
        applyGamepadProfile(saved)
        return saved.virtual.enabled
    }

    fun openGamepadConfigDialog() {
        ensureOverlaysAttached()
        val key = currentGamepadProfileKey()
        val displayTitle = intent?.getStringExtra(EXTRA_GAME_ROOT)
            ?.takeIf { it.isNotBlank() }
            ?.let { java.io.File(it).name }
        GamepadConfigDialog.show(
            context = this,
            profileKey = key,
            gameDisplayName = displayTitle,
            onProfileUpdated = { saved -> applyGamepadProfile(saved) },
            onEnterLayoutEditor = { updated ->
                applyGamepadProfile(updated)
                enterLayoutEditorMode()
            }
        )
    }

    private fun registerControllerListener() {
        if (inputDeviceListener != null) return
        val im = getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                window.decorView.post { updatePhysicalPadPresence(showToast = true) }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                try {
                    GamepadBridge.nativeSetAndroidControllerKey(deviceId, -1, false)
                } catch (_: Throwable) {
                }
                window.decorView.post { updatePhysicalPadPresence(showToast = false) }
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                window.decorView.post { updatePhysicalPadPresence(showToast = false) }
            }
        }
        im.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        inputDeviceListener = listener
        lastReportedConnectedPads = GamepadBridge.queryConnectedControllers(this).size
    }

    private fun unregisterControllerListener() {
        val listener = inputDeviceListener ?: return
        inputDeviceListener = null
        try {
            val im = getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return
            im.unregisterInputDeviceListener(listener)
        } catch (_: Throwable) {
        }
    }

    private fun updatePhysicalPadPresence(showToast: Boolean) {
        val connected = GamepadBridge.queryConnectedControllers(this)
        val effectiveCount = maxOf(connected.size, nativePadCount)
        val hideVpad = effectiveCount > 0 && activeGamepadProfile.physical.autoHideVirtualPad
        virtualGamepadView?.setAutoHiddenByPhysicalPad(hideVpad)
        if (showToast && effectiveCount > lastReportedConnectedPads && connected.isNotEmpty()) {
            val name = connected.last().name
            val msg = GamepadBridge.tr(
                this,
                "已连接手柄：$name",
                "コントローラ接続：$name",
                "Controller connected: $name"
            )
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        lastReportedConnectedPads = effectiveCount
    }

    private fun attachVirtualGamepadOverlays() {
        if (virtualCursorView == null) {
            val cursor = VirtualCursorOverlayView(this)
            virtualCursorView = cursor
            addContentView(
                cursor,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        if (virtualGamepadView == null) {
            val pad = VirtualGamepadView(this).apply {
                setProfile(activeGamepadProfile)
                setCtrlToggleLatched(ctrlSkipLatched)
                listener = object : VirtualGamepadView.Listener {
                    override fun onControlTappedInEditor(control: VirtualControlSpec) {
                        setSelectedControlId(control.id)
                        layoutEditorHintTv?.text = "${control.label} (${(control.xNorm * 100).toInt()}%, ${(control.yNorm * 100).toInt()}%) · " +
                            GamepadBridge.tr(this@XSystem4Activity, "点击编辑属性", "タップで設定編集", "Editing control")
                        KeyPickerDialog.showVirtualControlEditor(
                            context = this@XSystem4Activity,
                            control = control,
                            onSave = { edited ->
                                val list = activeGamepadProfile.virtual.controls.toMutableList()
                                val idx = list.indexOfFirst { it.id == control.id }
                                if (idx >= 0) {
                                    list[idx] = edited
                                    activeGamepadProfile = activeGamepadProfile.copy(
                                        virtual = activeGamepadProfile.virtual.copy(controls = list)
                                    )
                                    virtualGamepadView?.setProfile(activeGamepadProfile)
                                    virtualGamepadView?.setEditMode(true, edited.id)
                                }
                            },
                            onDelete = if (control.isCustom) {
                                {
                                    val list = activeGamepadProfile.virtual.controls.filterNot { it.id == control.id }
                                    activeGamepadProfile = activeGamepadProfile.copy(
                                        virtual = activeGamepadProfile.virtual.copy(controls = list)
                                    )
                                    virtualGamepadView?.setProfile(activeGamepadProfile)
                                    virtualGamepadView?.setEditMode(true, null)
                                }
                            } else null
                        )
                    }

                    override fun onControlMovedInEditor(control: VirtualControlSpec) {
                        val list = activeGamepadProfile.virtual.controls.toMutableList()
                        val idx = list.indexOfFirst { it.id == control.id }
                        if (idx >= 0) {
                            list[idx] = control
                            activeGamepadProfile = activeGamepadProfile.copy(
                                virtual = activeGamepadProfile.virtual.copy(controls = list)
                            )
                        }
                        layoutEditorHintTv?.text =
                            "${control.label}  X:${(control.xNorm * 100).toInt()}%  Y:${(control.yNorm * 100).toInt()}%"
                    }
                }
            }
            virtualGamepadView = pad
            addContentView(
                pad,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            updatePhysicalPadPresence(showToast = false)
        }
    }

    fun enterLayoutEditorMode() {
        ensureOverlaysAttached()
        if (!activeGamepadProfile.virtual.enabled) {
            activeGamepadProfile = activeGamepadProfile.copy(
                virtual = activeGamepadProfile.virtual.copy(enabled = true)
            )
            virtualGamepadView?.setProfile(activeGamepadProfile)
        }
        virtualGamepadView?.setEditMode(true, null)
        if (layoutEditorContainer == null) {
            val container = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isClickable = false
                isFocusable = false
            }

            val topBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0xDD111827.toInt())
                    setStroke(dp(1), 0x8838BDF8.toInt())
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply {
                    topMargin = dp(10)
                }
            }

            fun barBtn(label: String, bgColor: Int, onClick: () -> Unit): Button {
                return Button(this).apply {
                    text = label
                    textSize = 11.5f
                    setTextColor(Color.WHITE)
                    minHeight = 0
                    minWidth = 0
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(bgColor)
                    }
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginStart = dp(4)
                    lp.marginEnd = dp(4)
                    layoutParams = lp
                    setOnClickListener { onClick() }
                }
            }

            val addBtn = barBtn(
                GamepadBridge.tr(this, "+ 添加按键", "+ 追加", "+ Add"),
                0xFF2563EB.toInt()
            ) {
                val slotId = GamepadBridge.nextFreeCustomSlotId(activeGamepadProfile.virtual.controls)
                if (slotId < 0) {
                    Toast.makeText(
                        this,
                        GamepadBridge.tr(this, "已达自定义按键上限", "上限に達しました", "Limit reached"),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@barBtn
                }
                KeyPickerDialog.showActionPicker(
                    this,
                    GamepadBridge.tr(this, "选择新增按键功能", "機能を選択", "New Button Action"),
                    GamepadActionSpec(GamepadBridge.ACTION_VK, GamepadBridge.VK_RETURN)
                ) { spec, shortBadge ->
                    val idx = slotId - 12
                    val newCtrl = VirtualControlSpec(
                        id = "custom_$slotId",
                        slotId = slotId,
                        kind = VirtualControlKind.BUTTON,
                        label = shortBadge.ifBlank { "K${idx + 1}" },
                        xNorm = (0.35f + (idx % 5) * 0.08f).coerceIn(0.12f, 0.88f),
                        yNorm = (0.30f + (idx / 5) * 0.12f).coerceIn(0.15f, 0.85f),
                        scale = 0.95f,
                        opacity = 1.0f,
                        visible = true,
                        shape = VirtualButtonShape.PILL,
                        action = spec,
                        isCustom = true
                    )
                    val list = activeGamepadProfile.virtual.controls.toMutableList().apply { add(newCtrl) }
                    activeGamepadProfile = activeGamepadProfile.copy(
                        virtual = activeGamepadProfile.virtual.copy(controls = list)
                    )
                    virtualGamepadView?.setProfile(activeGamepadProfile)
                    virtualGamepadView?.setEditMode(true, newCtrl.id)
                }
            }

            val gridLabel = {
                val on = activeGamepadProfile.virtual.gridSnap
                GamepadBridge.tr(this, if (on) "网格:开" else "网格:关", if (on) "グリッド:ON" else "グリッド:OFF", if (on) "Grid:ON" else "Grid:OFF")
            }
            val gridBtn = barBtn(gridLabel(), 0xFF374151.toInt()) {
                val next = !activeGamepadProfile.virtual.gridSnap
                activeGamepadProfile = activeGamepadProfile.copy(
                    virtual = activeGamepadProfile.virtual.copy(gridSnap = next)
                )
                virtualGamepadView?.setProfile(activeGamepadProfile)
                virtualGamepadView?.setEditMode(true, null)
                layoutEditorGridBtn?.text = gridLabel()
            }
            layoutEditorGridBtn = gridBtn

            val resetBtn = barBtn(
                GamepadBridge.tr(this, "重置", "初期化", "Reset"),
                0xFF7C2D12.toInt()
            ) {
                val presetId = activeGamepadProfile.virtual.presetId
                activeGamepadProfile = activeGamepadProfile.copy(
                    virtual = activeGamepadProfile.virtual.copy(
                        controls = GamepadBridge.buildPresetControls(presetId)
                    )
                )
                virtualGamepadView?.setProfile(activeGamepadProfile)
                virtualGamepadView?.setEditMode(true, null)
            }

            val doneBtn = barBtn(
                GamepadBridge.tr(this, "保存完成", "完了・保存", "Done"),
                0xFF059669.toInt()
            ) {
                exitLayoutEditorMode(save = true)
            }

            topBar.addView(addBtn)
            topBar.addView(gridBtn)
            topBar.addView(resetBtn)
            topBar.addView(doneBtn)

            val hintTv = TextView(this).apply {
                text = GamepadBridge.tr(
                    this@XSystem4Activity,
                    "拖拽调整按键位置 · 点按任意按键编辑大小/动作/隐藏 · 完成后点“保存完成”",
                    "ドラッグで位置調整 · タップで機能・サイズ編集 · 完了で保存",
                    "Drag to move · Tap any control to edit size/action · Tap Done to save"
                )
                textSize = 11.5f
                setTextColor(0xFFE0F2FE.toInt())
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(0xCC111827.toInt())
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = dp(10)
                }
            }
            layoutEditorHintTv = hintTv

            container.addView(topBar)
            container.addView(hintTv)
            addContentView(
                container,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            layoutEditorContainer = container
        } else {
            layoutEditorContainer?.visibility = View.VISIBLE
            layoutEditorGridBtn?.text = GamepadBridge.tr(
                this,
                if (activeGamepadProfile.virtual.gridSnap) "网格:开" else "网格:关",
                if (activeGamepadProfile.virtual.gridSnap) "グリッド:ON" else "グリッド:OFF",
                if (activeGamepadProfile.virtual.gridSnap) "Grid:ON" else "Grid:OFF"
            )
        }
    }

    fun exitLayoutEditorMode(save: Boolean) {
        virtualGamepadView?.setEditMode(false, null)
        layoutEditorContainer?.visibility = View.GONE
        if (save) {
            val saved = GamepadBridge.saveProfile(
                this,
                activeGamepadProfile,
                currentGamepadProfileKey(),
                activeGamepadProfile.perGameOverride
            )
            applyGamepadProfile(saved)
            Toast.makeText(
                this,
                GamepadBridge.tr(this, "虚拟手柄布局已保存", "レイアウトを保存しました", "Layout saved"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun captureGameScreenshot() {
        val surfaceView = SDLActivity.getContentView()?.let { root ->
            var found: SurfaceView? = null
            if (root is ViewGroup) {
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    if (child is SurfaceView) {
                        found = child
                        break
                    }
                }
            }
            found
        }
        val targetW = (surfaceView?.width?.takeIf { it > 0 } ?: window.decorView.width).coerceAtLeast(1)
        val targetH = (surfaceView?.height?.takeIf { it > 0 } ?: window.decorView.height).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())
        val onCopied = PixelCopy.OnPixelCopyFinishedListener { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: java.io.File(filesDir, "screenshots")
                    dir.mkdirs()
                    val name = "screenshot_${System.currentTimeMillis()}.png"
                    val outFile = java.io.File(dir, name)
                    java.io.FileOutputStream(outFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    bmp.recycle()
                    Toast.makeText(
                        this,
                        GamepadBridge.tr(
                            this,
                            "截图已保存: ${outFile.name}",
                            "スクショ保存: ${outFile.name}",
                            "Saved: ${outFile.name}"
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Throwable) {
                    bmp.recycle()
                }
            } else {
                bmp.recycle()
            }
        }
        try {
            if (surfaceView != null && surfaceView.holder.surface.isValid) {
                PixelCopy.request(surfaceView, bmp, onCopied, handler)
            } else {
                PixelCopy.request(window, bmp, onCopied, handler)
            }
        } catch (_: Throwable) {
            bmp.recycle()
        }
    }

    private fun registerModernBackCallback() {
        if (Build.VERSION.SDK_INT >= 33 && backInvokedCallback == null) {
            val cb = OnBackInvokedCallback {
                handleGameBackPressed()
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

    private fun ensureOverlaysAttached() {
        if (isFinishing || isDestroyed) return
        attachVirtualGamepadOverlays()
        if (ctrlOverlayContainer == null) {
            attachCtrlOverlay()
        }
        if (modifierOverlay == null) {
            modifierOverlay = FloatingModifierOverlay(this).also { it.attach() }
        }
    }

    private fun attachCtrlOverlay() {
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
        }
        val pill = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            visibility = if (GameModifierBridge.isCtrlOverlayVisible(this@XSystem4Activity)) View.VISIBLE else View.GONE
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            dp(36),
            Gravity.TOP or Gravity.START
        ).apply {
            leftMargin = dp(64)
            topMargin = dp(14)
        }
        pill.layoutParams = lp

        var downRawX = 0f
        var downRawY = 0f
        var startLeft = 0
        var startTop = 0
        var dragging = false

        pill.setOnTouchListener { v, event ->
            val params = v.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startLeft = params.leftMargin
                    startTop = params.topMargin
                    dragging = false
                    v.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        dragging = true
                    }
                    if (dragging) {
                        val parentW = container.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                        val parentH = container.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                        params.leftMargin = (startLeft + dx).coerceIn(0, (parentW - v.width).coerceAtLeast(0))
                        params.topMargin = (startTop + dy).coerceIn(0, (parentH - v.height).coerceAtLeast(0))
                        v.layoutParams = params
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        setCtrlSkipLatched(!ctrlSkipLatched)
                    } else {
                        updateCtrlPillAppearance()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    updateCtrlPillAppearance()
                    true
                }
                else -> false
            }
        }

        ctrlOverlayContainer = container
        ctrlPill = pill
        updateCtrlPillAppearance()
        container.addView(pill)
        addContentView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun updateCtrlPillAppearance() {
        val pill = ctrlPill ?: return
        if (ctrlSkipLatched) {
            pill.text = "⏩ Ctrl ON"
            pill.setTextColor(0xFFE8FFF0.toInt())
            pill.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xCC1B6E3D.toInt())
                setStroke(dp(1), 0xAA7CFC9A.toInt())
            }
            pill.alpha = 0.95f
        } else {
            pill.text = "⏩ Ctrl"
            pill.setTextColor(Color.WHITE)
            pill.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0x88000000.toInt())
                setStroke(dp(1), 0x55FFFFFF)
            }
            pill.alpha = 0.8f
        }
    }

    fun setCtrlSkipLatched(latched: Boolean) {
        ctrlSkipLatched = latched
        try {
            GameModifierBridge.nativeSetCtrlSkip(latched)
        } catch (_: Throwable) {
        }
        virtualGamepadView?.setCtrlToggleLatched(latched)
        updateCtrlPillAppearance()
    }

    fun isCtrlOverlayCurrentlyVisible(): Boolean {
        if (!GameModifierBridge.isCtrlOverlayEnabled(this)) return false
        val pill = ctrlPill
        return if (pill != null) pill.visibility == View.VISIBLE else GameModifierBridge.isCtrlOverlayVisible(this)
    }

    fun setCtrlOverlayVisible(visible: Boolean) {
        val effectiveVisible = visible && GameModifierBridge.isCtrlOverlayEnabled(this)
        ensureOverlaysAttached()
        if (!effectiveVisible) {
            setCtrlSkipLatched(false)
        }
        ctrlPill?.visibility = if (effectiveVisible) View.VISIBLE else View.GONE
        GameModifierBridge.setCtrlOverlayVisible(this, effectiveVisible)
        lastSyncedConfigSeq = GameModifierBridge.getConfigSeq(this)
    }

    fun toggleCtrlOverlay(): Boolean {
        if (!GameModifierBridge.isCtrlOverlayEnabled(this)) {
            setCtrlOverlayVisible(false)
            return false
        }
        val next = !isCtrlOverlayCurrentlyVisible()
        setCtrlOverlayVisible(next)
        return next
    }

    private fun syncOverlayPreferences() {
        val ctrlVisible = GameModifierBridge.isCtrlOverlayEnabled(this) &&
            GameModifierBridge.isCtrlOverlayVisible(this)
        if (!ctrlVisible) {
            setCtrlSkipLatched(false)
        }
        ctrlPill?.visibility = if (ctrlVisible) View.VISIBLE else View.GONE
        modifierOverlay?.syncVisibility()
    }

    private fun resetMovieSkipOverlay() {
        virtualGamepadView?.setHiddenByMovie(false)
    }

    private fun updateMovieSkipOverlay(stateParam: Int) {
        if (isFinishing || isDestroyed) return
        val state = stateParam and 0xFF
        val active = state != MOVIE_SKIP_UI_HIDDEN
        virtualGamepadView?.setHiddenByMovie(active)
        if (active) {
            virtualCursorView?.hide()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            GameModifierBridge.syncNativeConfigPath(this)
            val curSeq = GameModifierBridge.getConfigSeq(this)
            if (curSeq != lastSyncedConfigSeq) {
                GameModifierBridge.syncUiScaleToNative(this)
                syncAllLocksToNative()
                syncOverlayPreferences()
                lastSyncedConfigSeq = curSeq
            }
            applyGamepadProfile(GamepadBridge.loadProfile(this, currentGamepadProfileKey()))
        } catch (_: Throwable) {
        }
    }

    override fun onPause() {
        setCtrlSkipLatched(false)
        virtualGamepadView?.releaseAllInputs()
        try {
            GamepadBridge.nativeClearVirtualInputs()
        } catch (_: Throwable) {
        }
        resetMovieSkipOverlay()
        lastSyncedConfigSeq = GameModifierBridge.syncLockMaskFromNative(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        try {
            GameModifierBridge.syncNativeConfigPath(this)
            val curSeq = GameModifierBridge.getConfigSeq(this)
            if (curSeq != lastSyncedConfigSeq) {
                GameModifierBridge.syncUiScaleToNative(this)
                syncAllLocksToNative()
                syncOverlayPreferences()
                lastSyncedConfigSeq = curSeq
            }
            applyGamepadProfile(GamepadBridge.loadProfile(this, currentGamepadProfileKey()))
        } catch (_: Throwable) {
        }
    }

    private fun handleGameBackPressed() {
        if (virtualGamepadView?.isLayoutEditMode() == true) {
            exitLayoutEditorMode(save = false)
            return
        }
        setCtrlSkipLatched(false)
        if (activeMessageBoxDialog?.isShowing == true) {
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackDispatchMs < 350L) {
            return
        }
        lastBackDispatchMs = now
        SDLActivity.nativeSendQuit()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GamepadBridge.isGamepadKeyEvent(event)) {
            val isDown = event.action == KeyEvent.ACTION_DOWN
            val isUp = event.action == KeyEvent.ACTION_UP
            val mappedCode = if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                KeyEvent.KEYCODE_BUTTON_B
            } else {
                event.keyCode
            }
            if ((isDown && event.repeatCount == 0) || isUp) {
                try {
                    GamepadBridge.nativeSetAndroidControllerKey(event.deviceId, mappedCode, isDown)
                } catch (_: Throwable) {
                }
            }
            val handled = super.dispatchKeyEvent(event)
            if (handled || event.keyCode == KeyEvent.KEYCODE_BACK ||
                GamepadBridge.androidKeyCodeToPadSlot(mappedCode) != null
            ) {
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
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            (event.source and InputDevice.SOURCE_MOUSE) != InputDevice.SOURCE_MOUSE &&
            !GamepadBridge.isGamepadKeyEvent(event)
        ) {
            if (event.repeatCount == 0) {
                backHandledInCurrentGesture = false
            }
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            (event.source and InputDevice.SOURCE_MOUSE) != InputDevice.SOURCE_MOUSE &&
            !GamepadBridge.isGamepadKeyEvent(event)
        ) {
            if (!event.isCanceled && !backHandledInCurrentGesture) {
                backHandledInCurrentGesture = true
                handleGameBackPressed()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        backHandledInCurrentGesture = true
        handleGameBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (ErrorReportKit.handleActivityResult(this, requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun messageboxCreateAndShow(args: Bundle) {
        if (isFinishing || isDestroyed) {
            synchronized(messageboxSelection) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (messageboxSelection as Object).notifyAll()
            }
            return
        }
        activeMessageBoxDialog?.let { prev ->
            if (prev.isShowing) {
                try {
                    prev.dismiss()
                } catch (_: Throwable) {
                }
            }
        }

        val uiCtx = ContextThemeWrapper(AppSettings.wrapThemeContext(this), R.style.Theme_Launcher)
        val flags = args.getInt("flags", 0)
        val rawTitle = args.getString("title").orEmpty()
        val rawMessage = args.getString("message").orEmpty()
        val buttonFlags = args.getIntArray("buttonFlags") ?: IntArray(0)
        val buttonIds = args.getIntArray("buttonIds") ?: IntArray(0)
        val buttonTexts = args.getStringArray("buttonTexts") ?: emptyArray()

        val isSentinelQuit = rawTitle == SENTINEL_ALICEHUB_QUIT
        val isQuitDialog = isSentinelQuit || (
            rawTitle.equals("xsystem4", ignoreCase = true) && (
                rawMessage.contains("结束游戏") ||
                    rawMessage.contains("終了しますか") ||
                    rawMessage.contains("Exit", ignoreCase = true) ||
                    rawMessage.contains("Quit", ignoreCase = true)
                )
            )
        val isErrorDialog = (flags and SDL_MESSAGEBOX_ERROR) != 0 ||
            rawTitle.contains("Error", ignoreCase = true)

        val displayTitle = when {
            isQuitDialog -> uiCtx.getString(R.string.game_exit_title)
            isErrorDialog -> uiCtx.getString(R.string.error)
            rawTitle.isBlank() || rawTitle.equals("xsystem4", ignoreCase = true) ->
                uiCtx.getString(R.string.app_name)
            else -> rawTitle
        }

        val displayMessage = if (isSentinelQuit) {
            uiCtx.getString(R.string.game_exit_message)
        } else {
            rawMessage
                .replace("\r\n", "\n")
                .replace(Regex("\n{2,}"), "\n")
                .trim()
        }

        data class MsgBtnSpec(
            val id: Int,
            val bFlags: Int,
            val label: String,
            val isPrimary: Boolean,
            val isEscape: Boolean
        )

        val rawSpecs = buttonTexts.indices.map { idx ->
            val rawLabel = buttonTexts[idx].orEmpty()
            val bFlag = buttonFlags.getOrElse(idx) { 0 }
            val bId = buttonIds.getOrElse(idx) { idx }
            val localized = when {
                rawLabel.equals("OK", ignoreCase = true) -> uiCtx.getString(R.string.ok)
                rawLabel.equals("Cancel", ignoreCase = true) -> uiCtx.getString(R.string.cancel)
                rawLabel.equals("Stop", ignoreCase = true) -> uiCtx.getString(R.string.dialog_btn_stop)
                rawLabel.equals("Continue", ignoreCase = true) -> uiCtx.getString(R.string.dialog_btn_continue)
                else -> rawLabel
            }
            val isEsc = (bFlag and SDL_BUTTON_ESCAPEKEY_DEFAULT) != 0 ||
                rawLabel.equals("Cancel", ignoreCase = true)
            val isPrim = !isEsc && (
                (bFlag and SDL_BUTTON_RETURNKEY_DEFAULT) != 0 ||
                    buttonTexts.size == 1 ||
                    rawLabel.equals("OK", ignoreCase = true)
                )
            MsgBtnSpec(bId, bFlag, localized, isPrim, isEsc)
        }

        val orderedSpecs = rawSpecs.sortedBy { spec ->
            when {
                spec.isEscape -> 0
                !spec.isPrimary -> 1
                else -> 2
            }
        }

        if (isErrorDialog) {
            val report = ErrorReportKit.buildFromGameMessageBox(
                context = this,
                rawTitle = rawTitle,
                rawMessage = rawMessage,
                gameRoot = intent?.getStringExtra(EXTRA_GAME_ROOT),
                saveDir = intent?.getStringExtra(EXTRA_SAVE_DIR),
                hasMultipleChoices = orderedSpecs.size > 1
            )
            val errorSpecs = orderedSpecs.map { spec ->
                ErrorDialogButtonSpec(
                    id = spec.id,
                    label = spec.label,
                    isPrimary = spec.isPrimary,
                    isEscape = spec.isEscape,
                    isReturnDefault = (spec.bFlags and SDL_BUTTON_RETURNKEY_DEFAULT) != 0
                )
            }
            val dlg = ErrorReportKit.showInGameErrorDialog(
                activity = this,
                report = report,
                decisionSpecs = errorSpecs,
                onSelected = { selectedId ->
                    messageboxSelection[0] = selectedId
                },
                onDismissed = {
                    activeMessageBoxDialog = null
                    synchronized(messageboxSelection) {
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                        (messageboxSelection as Object).notifyAll()
                    }
                }
            )
            activeMessageBoxDialog = dlg
            return
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        activeMessageBoxDialog = dialog

        val keyMapping = SparseArray<Button>()
        var escapeButtonId: Int? = null
        var selectionMade = false

        val dm = resources.displayMetrics
        val cardWidth = dp(332).coerceAtMost((dm.widthPixels - dp(48)).coerceAtLeast(dp(260)))
        val cardRadius = uiCtx.resources.getDimension(R.dimen.ui_radius_dialog)

        val card = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(24)
            setPadding(padH, dp(22), padH, dp(20))
            elevation = dp(20).toFloat()
            background = GradientDrawable().apply {
                cornerRadius = cardRadius
                setColor(uiCtx.getColor(R.color.ui_surface_dialog))
                setStroke(dp(1), uiCtx.getColor(R.color.ui_outline))
            }
            clipToOutline = true
            isClickable = true
        }

        val titleView = TextView(uiCtx).apply {
            text = displayTitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(uiCtx.getColor(R.color.ui_text_primary))
        }
        card.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (displayMessage.isNotEmpty()) {
            val messageView = TextView(uiCtx).apply {
                text = displayMessage
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setTextColor(uiCtx.getColor(R.color.ui_text_secondary))
                setLineSpacing(dp(3).toFloat(), 1.18f)
            }
            val msgScroll = ScrollView(uiCtx).apply {
                isVerticalScrollBarEnabled = true
                addView(
                    messageView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            card.addView(
                msgScroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }
            )
        }

        if (orderedSpecs.isNotEmpty()) {
            val btnRow = LinearLayout(uiCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            val mediumFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)

            orderedSpecs.forEachIndexed { index, spec ->
                val btn = Button(uiCtx).apply {
                    text = spec.label
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = mediumFont
                    gravity = Gravity.CENTER
                    minHeight = dp(42)
                    minimumHeight = dp(42)
                    elevation = 0f
                    stateListAnimator = null
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    if (spec.isPrimary) {
                        if (isErrorDialog) {
                            background = uiCtx.getDrawable(R.drawable.bg_btn_danger)?.mutate()
                            setTextColor(uiCtx.getColor(R.color.tone_danger_fg))
                        } else {
                            background = uiCtx.getDrawable(R.drawable.bg_btn_filled)?.mutate()
                            setTextColor(uiCtx.getColor(R.color.ui_on_primary))
                        }
                    } else {
                        background = uiCtx.getDrawable(R.drawable.bg_btn_tonal)?.mutate()
                        setTextColor(uiCtx.getColor(R.color.ui_text_primary))
                    }
                    setOnClickListener {
                        selectionMade = true
                        messageboxSelection[0] = spec.id
                        dialog.dismiss()
                    }
                }
                if ((spec.bFlags and SDL_BUTTON_RETURNKEY_DEFAULT) != 0 || spec.isPrimary) {
                    keyMapping.put(KeyEvent.KEYCODE_ENTER, btn)
                    keyMapping.put(KeyEvent.KEYCODE_NUMPAD_ENTER, btn)
                }
                if (spec.isEscape) {
                    escapeButtonId = spec.id
                    keyMapping.put(KeyEvent.KEYCODE_ESCAPE, btn)
                    keyMapping.put(KeyEvent.KEYCODE_BACK, btn)
                }
                val lp = if (orderedSpecs.size == 1) {
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                } else {
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = dp(10)
                    }
                }
                btnRow.addView(btn, lp)
            }

            card.addView(
                btnRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(22)
                }
            )
        }

        val backdrop = FrameLayout(this).apply {
            setOnClickListener {
                val escId = escapeButtonId
                if (escId != null) {
                    selectionMade = true
                    messageboxSelection[0] = escId
                    dialog.dismiss()
                }
            }
            addView(
                card,
                FrameLayout.LayoutParams(
                    cardWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        dialog.setContentView(backdrop)
        dialog.setCancelable(escapeButtonId != null)
        dialog.setCanceledOnTouchOutside(escapeButtonId != null)
        dialog.setOnCancelListener {
            val escId = escapeButtonId
            if (!selectionMade && escId != null) {
                selectionMade = true
                messageboxSelection[0] = escId
            }
        }
        dialog.setOnDismissListener {
            if (activeMessageBoxDialog === dialog) {
                activeMessageBoxDialog = null
            }
            synchronized(messageboxSelection) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (messageboxSelection as Object).notifyAll()
            }
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            val mapped = keyMapping.get(keyCode)
            if (mapped != null) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    mapped.performClick()
                }
                true
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                true
            } else {
                false
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.55f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }

        dialog.show()

        dialog.window?.let { dlgWin ->
            @Suppress("DEPRECATION")
            dlgWin.decorView.systemUiVisibility = window.decorView.systemUiVisibility
            dlgWin.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    override fun getLibraries(): Array<String> {
        return arrayOf("SDL2", "xsystem4")
    }

    override fun getArguments(): Array<String> {
        val saveFolder = intent.getStringExtra(EXTRA_SAVE_DIR)!!
        val gameRoot = intent.getStringExtra(EXTRA_GAME_ROOT)!!
        GameCatalog.writeRunningGameRoot(this, gameRoot)
        if (Build.SUPPORTED_ABIS.any { it == "x86_64" || it == "x86" }) {
            // x86/x86_64 Android emulators can hang in AAudio STARTING on first write;
            // fall back to SDL's Java AudioTrack backend on x86 hosts.
            SDLActivity.nativeSetenv("SDL_AUDIODRIVER", "android")
        }
        GameModifierBridge.markAlreadyLoaded()
        try {
            GameModifierBridge.syncNativeConfigPath(this)
            GameModifierBridge.syncUiScaleToNative(this)
            val lockMask = GameModifierBridge.getSavedLockMask(this)
            GameModifierBridge.nativeSetLiveLocks(lockMask)
            lastSyncedConfigSeq = GameModifierBridge.getConfigSeq(this)
            activeGamepadProfile = GamepadBridge.loadAndSyncToNative(this, currentGamepadProfileKey())
        } catch (_: Throwable) {
        }
        return arrayOf("--save-folder", saveFolder, "--save-format=rsm", gameRoot)
    }

    override fun onDestroy() {
        unregisterModernBackCallback()
        unregisterControllerListener()
        virtualGamepadView?.releaseAllInputs()
        try {
            GamepadBridge.nativeClearVirtualInputs()
        } catch (_: Throwable) {
        }
        activeMessageBoxDialog?.let { dlg ->
            if (dlg.isShowing) {
                try {
                    dlg.dismiss()
                } catch (_: Throwable) {
                }
            }
        }
        activeMessageBoxDialog = null
        setCtrlSkipLatched(false)
        resetMovieSkipOverlay()
        GameCatalog.clearRunningGameRoot(this)
        GameModifierBridge.syncLockMaskFromNative(this)
        virtualGamepadView = null
        virtualCursorView = null
        layoutEditorContainer = null
        layoutEditorGridBtn = null
        layoutEditorHintTv = null
        ctrlPill = null
        ctrlOverlayContainer = null
        modifierOverlay?.detach()
        try {
            // Let SDL stop and join its native thread before terminating the game process.
            super.onDestroy()
        } finally {
            Process.killProcess(Process.myPid())
        }
    }

    override fun onUnhandledMessage(command: Int, param: Any?): Boolean {
        when (command) {
            COMMAND_SHOW_WAIT_MESSAGE -> {
                val show = ((param as? Number)?.toInt() ?: 0) != 0
                loadingLabel?.visibility = if (show) View.VISIBLE else View.GONE
                if (!show) {
                    ensureOverlaysAttached()
                }
                return true
            }
            COMMAND_TOGGLE_CTRL_OVERLAY -> {
                if (GameModifierBridge.isCtrlOverlayEnabled(this)) {
                    toggleCtrlOverlay()
                }
                return true
            }
            COMMAND_MOVIE_SKIP_STATE -> {
                updateMovieSkipOverlay((param as? Number)?.toInt() ?: MOVIE_SKIP_UI_HIDDEN)
                return true
            }
            COMMAND_CURSOR_STATE -> {
                virtualCursorView?.updateFromPackedParam((param as? Number)?.toInt() ?: 0)
                return true
            }
            COMMAND_GAMEPAD_UI_EVENT -> {
                val p = (param as? Number)?.toInt() ?: 0
                val evType = p and 0xFF
                val arg = (p ushr 8) and 0xFF
                when (evType) {
                    GAMEPAD_UI_EMU_CMD -> when (arg) {
                        GamepadBridge.EMU_TOGGLE_VPAD -> {
                            val shown = toggleVirtualGamepadEnabled()
                            Toast.makeText(
                                this,
                                GamepadBridge.tr(
                                    this,
                                    if (shown) "已显示屏幕虚拟手柄" else "已隐藏屏幕虚拟手柄",
                                    if (shown) "仮想ゲームパッドを表示" else "仮想ゲームパッドを非表示",
                                    if (shown) "Virtual Gamepad shown" else "Virtual Gamepad hidden"
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        GamepadBridge.EMU_OPEN_MENU -> {
                            ensureOverlaysAttached()
                            modifierOverlay?.openMainMenu()
                        }
                        GamepadBridge.EMU_SCREENSHOT -> captureGameScreenshot()
                        GamepadBridge.EMU_OPEN_EDITOR -> enterLayoutEditorMode()
                    }
                    GAMEPAD_UI_CONTROLLER_COUNT -> {
                        nativePadCount = arg
                        updatePhysicalPadPresence(showToast = false)
                    }
                }
                return true
            }
            COMMAND_OPEN_PLAYING_MANUAL -> {
                val gameRoot = intent.getStringExtra(EXTRA_GAME_ROOT)!!
                val manualDir = java.io.File(gameRoot, "Manual")
                if (manualDir.isDirectory) {
                    val intent = android.content.Intent(this, ManualActivity::class.java).apply {
                        val url = "file://${manualDir.absolutePath}/index.html"
                        putExtra(ManualActivity.EXTRA_URL, url)
                    }
                    startActivity(intent)
                }
                return true
            }
        }
        return super.onUnhandledMessage(command, param)
    }
}
