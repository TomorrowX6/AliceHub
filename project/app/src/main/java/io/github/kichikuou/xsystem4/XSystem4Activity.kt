package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.InputType
import android.util.Log
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
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
        private const val REQ_GAMEPAD_EDITOR = 4096
        private const val MOVIE_SKIP_UI_HIDDEN = 0
        private const val SDL_MESSAGEBOX_ERROR = 0x00000010
        private const val SDL_BUTTON_RETURNKEY_DEFAULT = 0x00000001
        private const val SDL_BUTTON_ESCAPEKEY_DEFAULT = 0x00000002
        private const val SENTINEL_ALICEHUB_QUIT = "__ALICEHUB_QUIT__"
        private const val SENTINEL_ALICEHUB_RESET = "__ALICEHUB_RESET__"

        private val screenshotExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "XSystem4Screenshot").apply { isDaemon = true }
        }
    }

    @Volatile
    private var launchedGameRoot: String? = null
    @Volatile
    private var launchedSaveDir: String? = null

    private external fun nativeRequestRedraw()

    private fun requestNativeRedrawSafely() {
        if (isFinishing || isDestroyed || isSystem3xGame()) {
            return
        }
        try {
            nativeRequestRedraw()
        } catch (_: UnsatisfiedLinkError) {
        } catch (_: Throwable) {
        }
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
    private var activeGamepadProfile: GamepadProfile = GamepadProfile()
    private var nativePadCount: Int = 0
    private var lastReportedConnectedPads: Int = 0
    private var inputDeviceListener: InputManager.InputDeviceListener? = null
    private val xsystem35Midi by lazy { XSystem35MidiPlayer(this) }

    fun currentGameRoot(): String? = launchedGameRoot

    fun currentSaveDir(): String? = launchedSaveDir

    private fun effectiveGameRootFile(): java.io.File? =
        (currentGameRoot() ?: intent?.getStringExtra(EXTRA_GAME_ROOT))
            ?.takeIf { it.isNotBlank() }
            ?.let { java.io.File(it) }

    private fun effectiveSaveDirFile(): java.io.File? =
        (currentSaveDir() ?: intent?.getStringExtra(EXTRA_SAVE_DIR))
            ?.takeIf { it.isNotBlank() }
            ?.let { java.io.File(it) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrapThemeContext(newBase))
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    fun isRance10Game(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isRance10GameDir(root, saveDir?.name, saveDir)
    }

    fun isDohnaDohnaGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isDohnaDohnaGameDir(root, saveDir?.name, saveDir)
    }

    fun isEvenicleGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isEvenicleGameDir(root, saveDir?.name, saveDir)
    }

    fun isSengokuRanceGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isSengokuRanceGameDir(root, saveDir?.name, saveDir)
    }

    fun isRance01Game(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isRance01GameDir(root, saveDir?.name, saveDir)
    }

    fun isRance02Game(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isRance02GameDir(root, saveDir?.name, saveDir)
    }

    fun isRance03Game(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isRance03GameDir(root, saveDir?.name, saveDir)
    }

    fun isRance06Game(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isRance06GameDir(root, saveDir?.name, saveDir)
    }

    fun isRance08Game(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isRance08GameDir(root, saveDir?.name, saveDir)
    }

    fun isRance09Game(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isRance09GameDir(root, saveDir?.name, saveDir)
    }

    fun isEscalayerGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isEscalayerGameDir(root, saveDir?.name, saveDir)
    }

    fun isEscalayerRebootGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isEscalayerRebootGameDir(root, saveDir?.name, saveDir)
    }

    fun isGalzooGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isGalzooGameDir(root, saveDir?.name, saveDir)
    }

    fun isIxsealGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isIxsealGameDir(root, saveDir?.name, saveDir)
    }

    fun isHarukaGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isHarukaGameDir(root, saveDir?.name, saveDir)
    }

    fun isTsumashiboriGame(): Boolean {
        val root = effectiveGameRootFile()
        val saveDir = effectiveSaveDirFile()
        return GameModifierBridge.isTsumashiboriGameDir(root, saveDir?.name, saveDir)
    }

    fun isSystem3xGame(): Boolean {
        val root = effectiveGameRootFile()
        return GameCatalog.isSystem3xGameDir(root)
    }

    private fun syncAllLocksToNative() {
        if (isSystem3xGame()) {
            GameModifierBridge.nativeSetLiveLocks(GameModifierBridge.getSavedEscLockMask(this))
            return
        }
        GameModifierBridge.nativeSetLiveLocks(GameModifierBridge.getSavedLockMask(this))
        GameModifierBridge.setDohnaLiveLocks(GameModifierBridge.getSavedDohnaLockMask(this))
        GameModifierBridge.setEvenicleLiveLocks(GameModifierBridge.getSavedEvenicleLockMask(this))
        GameModifierBridge.setSrLiveLocks(GameModifierBridge.getSavedSrLockMask(this))
        GameModifierBridge.setR01LiveLocks(GameModifierBridge.getSavedR01LockMask(this))
        GameModifierBridge.setR02LiveLocks(GameModifierBridge.getSavedR02LockMask(this))
        GameModifierBridge.setR03LiveLocks(GameModifierBridge.getSavedR03LockMask(this))
        GameModifierBridge.setR08LiveLocks(GameModifierBridge.getSavedR08LockMask(this))
        GameModifierBridge.setR09LiveLocks(GameModifierBridge.getSavedR09LockMask(this))
    }

    private fun applyImmersiveFullscreen() {
        val win = window ?: return
        @Suppress("DEPRECATION")
        val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        @Suppress("DEPRECATION")
        win.decorView.systemUiVisibility = flags
        win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        win.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        mFullscreenModeActive = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ErrorReportKit.installUncaughtExceptionHandler(this)
        ErrorReportKit.ensureSeenBaselineInitialized(this, offsetMs = -1000L)
        val root = (intent?.getStringExtra(EXTRA_GAME_ROOT)
            ?: savedInstanceState?.getString(EXTRA_GAME_ROOT))
            ?.takeIf { it.isNotBlank() }
        val saveDir = (intent?.getStringExtra(EXTRA_SAVE_DIR)
            ?: savedInstanceState?.getString(EXTRA_SAVE_DIR))
            ?.takeIf { it.isNotBlank() }
        if (root.isNullOrEmpty() || saveDir.isNullOrEmpty()) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        launchedGameRoot = root
        launchedSaveDir = saveDir
        GameCatalog.writeRunningGameRoot(this, root)

        super.onCreate(savedInstanceState)
        // Apply immersive fullscreen directly without blocking UI thread on SDL wait(500)
        applyImmersiveFullscreen()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        launchedGameRoot?.let { outState.putString(EXTRA_GAME_ROOT, it) }
        launchedSaveDir?.let { outState.putString(EXTRA_SAVE_DIR, it) }
    }

    fun currentGamepadProfileKey(): String? =
        GamepadBridge.normalizeGameProfileKey(currentGameRoot())

    fun getActiveGamepadProfile(): GamepadProfile = activeGamepadProfile

    fun applyGamepadProfile(profile: GamepadProfile) {
        activeGamepadProfile = profile
        traceEditorReturn("profile.begin")
        GamepadBridge.syncProfileToNative(profile)
        virtualGamepadView?.setProfile(profile)
        traceEditorReturn("profile.end")
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

    fun currentGamepadGameId(): String? {
        val rootFile = currentGameRoot()?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val saveFile = currentSaveDir()?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        val name = rootFile?.name ?: saveFile?.name ?: return null
        return GameCatalog.classifyDirectory(rootFile, name, saveFile, name)
    }

    fun openGamepadConfigDialog() {
        // Button/layout customization is launcher/settings only; do not open the
        // in-game config dialog or editor from the floating overlay or pad commands.
        GamepadBridge.notifyButtonsAndLayoutEditingBlocked(this)
    }

    private val resumeTraceVersion by lazy {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName
    }

    private fun traceEditorReturn(phase: String) {
        if (!android.util.Log.isLoggable("AliceHubResume", android.util.Log.DEBUG)) return
        SDLActivity.traceResumeState("game.$phase version=${resumeTraceVersion} " +
            "finishing=$isFinishing pad=${virtualGamepadView?.resumeVisibilityState()}")
    }

    @Suppress("UNUSED_PARAMETER")
    fun openGamepadEditorActivity(initialTab: Int = GamepadEditorActivity.TAB_VIRTUAL) {
        GamepadBridge.notifyButtonsAndLayoutEditingBlocked(this)
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
        val phys = activeGamepadProfile.physical
        val connected = GamepadBridge.queryConnectedControllers(this)
        val effectiveCount = maxOf(connected.size, nativePadCount)
        val hideVpad = phys.enabled && phys.autoHideVirtualPad && effectiveCount > 0
        virtualGamepadView?.setAutoHiddenByPhysicalPad(hideVpad)
        if (phys.enabled && showToast && effectiveCount > lastReportedConnectedPads && connected.isNotEmpty()) {
            val name = connected.last().name
            val msg = GamepadBridge.tr(
                this,
                "已连接手柄：$name",
                "コントローラ接続：$name",
                "Connected: $name"
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
                passthroughTargetProvider = { findGameSurfaceView() }
                setProfile(activeGamepadProfile)
                setCtrlToggleLatched(ctrlSkipLatched)
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
        GamepadBridge.notifyButtonsAndLayoutEditingBlocked(this)
    }

    private fun findGameSurfaceView(): SurfaceView? {
        val root = SDLActivity.getContentView() as? ViewGroup ?: return null
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is SurfaceView) return child
        }
        return null
    }

    private fun captureGameScreenshot() {
        val surfaceView = findGameSurfaceView()
        val targetW = (surfaceView?.width?.takeIf { it > 0 } ?: window.decorView.width).coerceAtLeast(1)
        val targetH = (surfaceView?.height?.takeIf { it > 0 } ?: window.decorView.height).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())
        val appCtx = applicationContext
        val picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: java.io.File(filesDir, "screenshots")
        val onCopied = PixelCopy.OnPixelCopyFinishedListener { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    screenshotExecutor.execute {
                        try {
                            picturesDir.mkdirs()
                            val name = "screenshot_${System.currentTimeMillis()}.png"
                            val outFile = java.io.File(picturesDir, name)
                            java.io.FileOutputStream(outFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            }
                            handler.post {
                                Toast.makeText(
                                    appCtx,
                                    GamepadBridge.tr(
                                        appCtx,
                                        "截图已保存: ${outFile.name}",
                                        "スクショ保存: ${outFile.name}",
                                        "Saved: ${outFile.name}"
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (_: Throwable) {
                        } finally {
                            bmp.recycle()
                        }
                    }
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
        traceEditorReturn("onNewIntent.begin")
        super.onNewIntent(intent)
        val incomingRoot = intent.getStringExtra(EXTRA_GAME_ROOT)?.takeIf { it.isNotBlank() }
        val activeRoot = launchedGameRoot
        if (incomingRoot != null && activeRoot != null && incomingRoot == activeRoot) {
            setIntent(intent)
        } else if (incomingRoot != null && activeRoot != null && incomingRoot != activeRoot) {
            android.util.Log.w(
                "XSystem4Activity",
                "Ignoring mismatched EXTRA_GAME_ROOT in onNewIntent while VM is active"
            )
            return
        }
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
            requestNativeRedrawSafely()
        } catch (_: Throwable) {
        }
    }

    override fun onPause() {
        traceEditorReturn("onPause.begin")
        setCtrlSkipLatched(false)
        virtualGamepadView?.releaseAllInputs()
        try {
            GamepadBridge.nativeClearVirtualInputs()
        } catch (_: Throwable) {
        }
        resetMovieSkipOverlay()
        if (isSystem3xGame()) {
            xsystem35Midi.onActivityPause()
        }
        if (launchedGameRoot != null) {
            lastSyncedConfigSeq = if (isSystem3xGame()) {
                GameModifierBridge.syncEscLockMaskFromNative(this)
            } else {
                GameModifierBridge.syncLockMaskFromNative(this)
            }
        }
        super.onPause()
        traceEditorReturn("onPause.end")
    }

    override fun onResume() {
        traceEditorReturn("onResume.begin")
        super.onResume()
        if (isSystem3xGame()) {
            xsystem35Midi.onActivityResume()
        }
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
            requestNativeRedrawSafely()
        } catch (_: Throwable) {
        }
    }

    private fun handleGameBackPressed() {
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
        if (activeGamepadProfile.physical.enabled && GamepadBridge.isGamepadKeyEvent(event)) {
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

    private fun restoreWindowAndFrameRate(reapplyStyle: Boolean) {
        if (isFinishing || isDestroyed || !hasWindowFocus()) {
            return
        }
        if (reapplyStyle) {
            try {
                applyImmersiveFullscreen()
            } catch (_: Throwable) {
            }
        }
        mSurface?.reapplyPreferredFrameRate()
        requestNativeRedrawSafely()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing && !isDestroyed) {
            restoreWindowAndFrameRate(reapplyStyle = true)
            window.decorView.post { restoreWindowAndFrameRate(reapplyStyle = false) }
        }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (activeGamepadProfile.physical.enabled) {
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
        if (requestCode == REQ_GAMEPAD_EDITOR) {
            traceEditorReturn("editor.result=$resultCode")
            try {
                applyGamepadProfile(GamepadBridge.loadProfile(this, currentGamepadProfileKey()))
                requestNativeRedrawSafely()
            } catch (_: Throwable) {
            }
            return
        }
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
        val isSentinelReset = rawTitle == SENTINEL_ALICEHUB_RESET
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
            isSentinelReset -> uiCtx.getString(R.string.game_reset_title)
            isQuitDialog -> uiCtx.getString(R.string.game_exit_title)
            isErrorDialog -> uiCtx.getString(R.string.error)
            rawTitle.isBlank() || rawTitle.equals("xsystem4", ignoreCase = true) ->
                uiCtx.getString(R.string.app_name)
            else -> rawTitle
        }

        val displayMessage = when {
            isSentinelReset -> uiCtx.getString(R.string.game_reset_message)
            isSentinelQuit -> uiCtx.getString(R.string.game_exit_message)
            else -> rawMessage
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
                gameRoot = currentGameRoot(),
                saveDir = currentSaveDir(),
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
        return if (isSystem3xGame()) arrayOf("SDL2", "xsystem35") else arrayOf("SDL2", "xsystem4")
    }

    override fun getArguments(): Array<String> {
        val saveFolder = currentSaveDir()?.takeIf { it.isNotEmpty() } ?: run {
            runOnUiThread { finish() }
            return emptyArray()
        }
        val gameRoot = currentGameRoot()?.takeIf { it.isNotEmpty() } ?: run {
            runOnUiThread { finish() }
            return emptyArray()
        }
        GameCatalog.writeRunningGameRoot(this, gameRoot)
        val crashLogPath = java.io.File(getExternalFilesDir(null) ?: filesDir, "crash.log").absolutePath
        SDLActivity.nativeSetenv("XSYSTEM4_CRASH_LOG_PATH", crashLogPath)
        if (Build.SUPPORTED_ABIS.any { it == "x86_64" || it == "x86" }) {
            // x86/x86_64 Android emulators can hang in AAudio STARTING on first write;
            // fall back to SDL's Java AudioTrack backend on x86 hosts.
            SDLActivity.nativeSetenv("SDL_AUDIODRIVER", "android")
        }
        GameModifierBridge.markAlreadyLoaded()
        try {
            GameModifierBridge.syncNativeConfigPath(this)
            GameModifierBridge.syncUiScaleToNative(this)
            syncAllLocksToNative()
            lastSyncedConfigSeq = GameModifierBridge.getConfigSeq(this)
            activeGamepadProfile = GamepadBridge.loadAndSyncToNative(this, currentGamepadProfileKey())
        } catch (_: Throwable) {
        }
        if (isSystem3xGame()) {
            val rootFile = java.io.File(gameRoot)
            val isSys39 = rootFile.listFiles()?.any { f ->
                f.isFile && (
                    f.name.equals("System39.ini", ignoreCase = true) ||
                        f.name.equals("System39.ain", ignoreCase = true)
                    )
            } == true
            return if (isSys39) {
                arrayOf("-gamedir", gameRoot, "-savedir", saveFolder, "-saveformat", "system39")
            } else {
                arrayOf("-gamedir", gameRoot, "-savedir", saveFolder)
            }
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
        if (launchedGameRoot != null) {
            GameCatalog.clearRunningGameRoot(this)
            try {
                if (isSystem3xGame()) {
                    GameModifierBridge.syncEscLockMaskFromNative(this)
                } else {
                    GameModifierBridge.syncLockMaskFromNative(this)
                }
            } catch (_: Throwable) {
            }
        }
        virtualGamepadView = null
        virtualCursorView = null
        ctrlPill = null
        ctrlOverlayContainer = null
        modifierOverlay?.detach()
        try {
            // Let SDL stop and join its native thread before terminating the game process.
            super.onDestroy()
        } finally {
            try {
                if (isSystem3xGame()) {
                    xsystem35Midi.release()
                }
            } finally {
                Process.killProcess(Process.myPid())
            }
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
                val gameRoot = launchedGameRoot?.takeIf { it.isNotEmpty() } ?: return true
                val manualDir = java.io.File(gameRoot, "Manual")
                if (manualDir.isDirectory) {
                    val manualIntent = android.content.Intent(this, ManualActivity::class.java).apply {
                        val url = android.net.Uri.fromFile(java.io.File(manualDir, "index.html")).toString()
                        putExtra(ManualActivity.EXTRA_URL, url)
                    }
                    startActivity(manualIntent)
                }
                return true
            }
        }
        return super.onUnhandledMessage(command, param)
    }

    // Native JNI helpers invoked from xsystem35's SDL thread (midi.android.c / input_modal.c)
    @Suppress("unused")
    fun inputString(msg: String, oldVal: String, maxLen: Int): String? {
        val result = arrayOfNulls<String?>(1)
        runOnUiThread {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setText(oldVal)
            }
            AlertDialog.Builder(this)
                .setMessage(msg)
                .setView(input)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val s = input.text.toString()
                    result[0] = if (maxLen <= 0) "" else if (s.length <= maxLen) s else s.substring(0, maxLen)
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener {
                    synchronized(result) {
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (result as Object).notify()
                    }
                }
                .show()
        }
        synchronized(result) {
            try {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (result as Object).wait()
            } catch (_: InterruptedException) {
            }
        }
        return result[0]
    }

    @Suppress("unused")
    fun inputNumber(msg: String, min: Int, max: Int, initial: Int): Int {
        val result = intArrayOf(-1)
        runOnUiThread {
            val input = NumberPicker(this).apply {
                minValue = min
                maxValue = max
                value = initial.coerceIn(min, max)
            }
            AlertDialog.Builder(this)
                .setMessage(msg)
                .setView(input)
                .setPositiveButton(R.string.ok) { _, _ ->
                    input.clearFocus()
                    result[0] = input.value
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener {
                    synchronized(result) {
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (result as Object).notify()
                    }
                }
                .show()
        }
        synchronized(result) {
            try {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (result as Object).wait()
            } catch (_: InterruptedException) {
            }
        }
        return result[0]
    }

    @Suppress("unused")
    fun midiStart(path: String, loop: Int) = xsystem35Midi.start(path, loop)

    @Suppress("unused")
    fun midiStop() = xsystem35Midi.stop()

    @Suppress("unused")
    fun midiPause() = xsystem35Midi.pause()

    @Suppress("unused")
    fun midiUnpause() = xsystem35Midi.unpause()

    @Suppress("unused")
    fun midiSetVolume(gain: Float) = xsystem35Midi.setVolume(gain)

    @Suppress("unused")
    fun midiFadeStart(durationMs: Int, targetGain: Float, stopAfter: Boolean) =
        xsystem35Midi.fadeStart(durationMs, targetGain, stopAfter)

    @Suppress("unused")
    fun midiIsFading(): Boolean = xsystem35Midi.isFading()

    @Suppress("unused")
    fun midiCurrentPosition(): Int = xsystem35Midi.currentPosition()

    private class XSystem35MidiPlayer(context: Context) {
        private val lock = Any()
        private val appContext = context.applicationContext
        private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        private val mainHandler = Handler(Looper.getMainLooper())
        private var player: MediaPlayer? = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        }
        private var focusRequest: AudioFocusRequest? = null
        private var hasAudioFocus = false
        private var active = false
        private var pausedByGame = false
        private var pausedByActivity = false
        private var pausedByFocus = false
        private var currentGain = 1.0f
        private var duckGain = 1.0f
        private var fadeGen = 0
        private var startGen = 0
        private var remainingRepeats = 0
        @Volatile
        private var fadingFlag = false

        private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            synchronized(lock) {
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasAudioFocus = true
                        pausedByFocus = false
                        duckGain = 1.0f
                        applyVolumeLocked()
                        syncPlaybackStateLocked()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        duckGain = 0.25f
                        applyVolumeLocked()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        pausedByFocus = true
                        syncPlaybackStateLocked()
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        hasAudioFocus = false
                        pausedByFocus = true
                        syncPlaybackStateLocked()
                    }
                }
            }
        }

        private fun requestFocusLocked(): Boolean {
            val am = audioManager ?: return true
            if (hasAudioFocus) return true
            val res = if (Build.VERSION.SDK_INT >= 26) {
                val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusListener, mainHandler)
                    .build().also { focusRequest = it }
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            return hasAudioFocus
        }

        private fun abandonFocusLocked() {
            val am = audioManager ?: return
            if (!hasAudioFocus) return
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    focusRequest?.let { am.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus(focusListener)
                }
            } catch (_: Exception) {
            }
            hasAudioFocus = false
        }

        private fun applyVolumeLocked() {
            val mp = player ?: return
            val g = (currentGain * duckGain).coerceIn(0.0f, 1.0f)
            try {
                mp.setVolume(g, g)
            } catch (_: Exception) {
            }
        }

        private fun syncPlaybackStateLocked() {
            val mp = player ?: return
            if (!active) return
            val shouldPlay = !pausedByGame && !pausedByActivity && !pausedByFocus
            try {
                if (shouldPlay && !mp.isPlaying) {
                    mp.start()
                } else if (!shouldPlay && mp.isPlaying) {
                    mp.pause()
                }
            } catch (_: Exception) {
            }
        }

        fun start(path: String, loopCount: Int) {
            synchronized(lock) {
                val mp = player ?: return
                fadeGen++
                startGen++
                val myStartGen = startGen
                fadingFlag = false
                active = false
                pausedByGame = false
                pausedByFocus = false
                val infinite = loopCount <= 0
                remainingRepeats = if (infinite) 0 else (loopCount - 1).coerceAtLeast(0)
                try {
                    mp.reset()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    mp.setDataSource(path)
                    mp.isLooping = infinite
                    mp.prepare()
                    mp.setOnCompletionListener {
                        synchronized(lock) {
                            if (myStartGen != startGen || !active || infinite) {
                                return@setOnCompletionListener
                            }
                            if (remainingRepeats > 0) {
                                remainingRepeats--
                                try {
                                    mp.seekTo(0)
                                    syncPlaybackStateLocked()
                                } catch (e: Exception) {
                                    Log.w("XSystem35Midi", "Cannot repeat midi: $path", e)
                                    active = false
                                    fadingFlag = false
                                    remainingRepeats = 0
                                }
                            } else {
                                active = false
                                fadingFlag = false
                            }
                        }
                    }
                    duckGain = 1.0f
                    applyVolumeLocked()
                    requestFocusLocked()
                    active = true
                    syncPlaybackStateLocked()
                } catch (e: Exception) {
                    Log.w("XSystem35Midi", "Cannot play midi: $path", e)
                    active = false
                    remainingRepeats = 0
                    mp.reset()
                }
            }
        }

        fun stop() {
            synchronized(lock) {
                fadeGen++
                startGen++
                fadingFlag = false
                remainingRepeats = 0
                val mp = player ?: return
                if (active) {
                    try {
                        mp.stop()
                    } catch (_: Exception) {
                    }
                    active = false
                }
                pausedByGame = false
                pausedByFocus = false
                abandonFocusLocked()
            }
        }

        fun pause() {
            synchronized(lock) {
                pausedByGame = true
                syncPlaybackStateLocked()
            }
        }

        fun unpause() {
            synchronized(lock) {
                pausedByGame = false
                if (active && !pausedByActivity) {
                    requestFocusLocked()
                    pausedByFocus = false
                }
                syncPlaybackStateLocked()
            }
        }

        fun setVolume(gain: Float) {
            synchronized(lock) {
                fadeGen++
                fadingFlag = false
                currentGain = gain.coerceIn(0.0f, 1.0f)
                applyVolumeLocked()
            }
        }

        fun fadeStart(durationMs: Int, targetGain: Float, stopAfter: Boolean) {
            val clampedTarget = targetGain.coerceIn(0.0f, 1.0f)
            synchronized(lock) {
                fadeGen++
                val myGen = fadeGen
                if (durationMs <= 0 || !active) {
                    fadingFlag = false
                    currentGain = clampedTarget
                    applyVolumeLocked()
                    if (stopAfter) stop()
                    return
                }
                val startGain = currentGain
                val startMs = SystemClock.uptimeMillis()
                val dur = durationMs.toLong().coerceAtLeast(1L)
                fadingFlag = true
                val step = object : Runnable {
                    override fun run() {
                        synchronized(lock) {
                            if (myGen != fadeGen || !active) {
                                if (myGen == fadeGen) fadingFlag = false
                                return
                            }
                            val elapsed = SystemClock.uptimeMillis() - startMs
                            val frac = (elapsed.toFloat() / dur.toFloat()).coerceIn(0.0f, 1.0f)
                            currentGain = startGain + (clampedTarget - startGain) * frac
                            applyVolumeLocked()
                            if (frac >= 1.0f) {
                                fadingFlag = false
                                if (stopAfter) {
                                    stop()
                                }
                            } else {
                                mainHandler.postDelayed(this, 20L)
                            }
                            Unit
                        }
                    }
                }
                mainHandler.post(step)
            }
        }

        fun isFading(): Boolean = fadingFlag

        fun currentPosition(): Int {
            synchronized(lock) {
                val mp = player ?: return -1
                return if (active) {
                    try {
                        mp.currentPosition
                    } catch (_: Exception) {
                        -1
                    }
                } else {
                    -1
                }
            }
        }

        fun onActivityPause() {
            synchronized(lock) {
                pausedByActivity = true
                syncPlaybackStateLocked()
            }
        }

        fun onActivityResume() {
            synchronized(lock) {
                pausedByActivity = false
                if (active && !pausedByGame) {
                    requestFocusLocked()
                    pausedByFocus = false
                }
                syncPlaybackStateLocked()
            }
        }

        fun release() {
            synchronized(lock) {
                fadeGen++
                startGen++
                fadingFlag = false
                remainingRepeats = 0
                active = false
                abandonFocusLocked()
                player?.let { mp ->
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                }
                player = null
                pausedByGame = false
                pausedByActivity = false
                pausedByFocus = false
            }
        }
    }
}
