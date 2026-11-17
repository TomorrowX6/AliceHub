package io.github.kichikuou.xsystem4

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class VirtualGamepadView(context: Context) : View(context) {

    interface Listener {
        fun onControlTappedInEditor(control: VirtualControlSpec)
        fun onControlMovedInEditor(control: VirtualControlSpec)
    }

    private data class ControlGeometry(
        val spec: VirtualControlSpec,
        val cx: Float,
        val cy: Float,
        val halfW: Float,
        val halfH: Float,
        val radius: Float
    ) {
        fun contains(x: Float, y: Float): Boolean {
            return if (spec.kind == VirtualControlKind.BUTTON && spec.shape != VirtualButtonShape.CIRCLE) {
                val pad = radius * 0.14f
                x >= cx - halfW - pad && x <= cx + halfW + pad &&
                    y >= cy - halfH - pad && y <= cy + halfH + pad
            } else {
                hypot((x - cx).toDouble(), (y - cy).toDouble()) <= radius * 1.18
            }
        }
    }

    private data class ActiveTouch(
        val controlId: String,
        val kind: VirtualControlKind,
        val startX: Float,
        val startY: Float,
        val origXNorm: Float,
        val origYNorm: Float,
        var dragging: Boolean = false
    )

    var listener: Listener? = null

    private var profile: GamepadProfile = GamepadProfile()
    private var editMode: Boolean = false
    private var selectedControlId: String? = null
    private var ctrlToggleLatched: Boolean = false
    private var autoHiddenByPhysicalPad: Boolean = false
    private var hiddenByMovie: Boolean = false

    private val activeTouches = SparseArray<ActiveTouch>()
    private val pressedButtonIds = HashSet<String>()
    private val toggledLatchedIds = HashSet<String>()
    private val stickPositions = HashMap<String, Pair<Float, Float>>() // id -> (-1..1, -1..1)
    private var dpadDirs = BooleanArray(4) // L, R, U, D

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val editorDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
        color = 0x26FFFFFF
    }
    private val tmpRect = RectF()
    private val tmpPath = Path()

    init {
        isClickable = false
        isFocusable = false
    }

    fun setProfile(newProfile: GamepadProfile) {
        releaseAllInputs()
        profile = newProfile
        updateVisibilityState()
        invalidate()
    }

    fun getProfile(): GamepadProfile = profile

    fun setEditMode(enabled: Boolean, selectedId: String? = selectedControlId) {
        if (editMode == enabled && selectedControlId == selectedId) return
        releaseAllInputs()
        editMode = enabled
        selectedControlId = if (enabled) selectedId else null
        updateVisibilityState()
        invalidate()
    }

    fun isLayoutEditMode(): Boolean = editMode

    fun setSelectedControlId(id: String?) {
        selectedControlId = id
        invalidate()
    }

    fun setCtrlToggleLatched(latched: Boolean) {
        if (ctrlToggleLatched != latched) {
            ctrlToggleLatched = latched
            invalidate()
        }
    }

    fun setAutoHiddenByPhysicalPad(hidden: Boolean) {
        if (autoHiddenByPhysicalPad != hidden) {
            autoHiddenByPhysicalPad = hidden
            if (hidden && !editMode) {
                releaseAllInputs()
            }
            updateVisibilityState()
        }
    }

    fun setHiddenByMovie(hidden: Boolean) {
        if (hiddenByMovie != hidden) {
            hiddenByMovie = hidden
            if (hidden && !editMode) {
                releaseAllInputs()
            }
            updateVisibilityState()
        }
    }

    private fun updateVisibilityState() {
        val shouldShow = editMode ||
            (profile.virtual.enabled && !autoHiddenByPhysicalPad && !hiddenByMovie)
        visibility = if (shouldShow) VISIBLE else GONE
    }

    fun releaseAllInputs() {
        activeTouches.clear()
        pressedButtonIds.clear()
        stickPositions.clear()
        dpadDirs = BooleanArray(4)
        if (GameModifierBridge.ensureLoaded()) {
            try {
                GamepadBridge.nativeClearVirtualInputs()
            } catch (_: Throwable) {
            }
        }
        invalidate()
    }

    private fun computeGeometries(): List<ControlGeometry> {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return emptyList()
        val shortEdge = min(w, h)
        val globalScale = profile.virtual.globalScalePct / 100f
        val list = ArrayList<ControlGeometry>(profile.virtual.controls.size)
        for (spec in profile.virtual.controls) {
            if (!spec.visible && !editMode) continue
            val s = spec.scale * globalScale
            val baseRadius = when (spec.kind) {
                VirtualControlKind.DPAD -> (shortEdge * 0.145f).coerceIn(dp(18f), dp(74f))
                VirtualControlKind.STICK -> (shortEdge * 0.140f).coerceIn(dp(18f), dp(70f))
                VirtualControlKind.BUTTON -> when (spec.shape) {
                    VirtualButtonShape.CIRCLE -> (shortEdge * 0.068f).coerceIn(dp(10f), dp(36f))
                    VirtualButtonShape.PILL -> (shortEdge * 0.044f).coerceIn(dp(7f), dp(24f))
                    VirtualButtonShape.SQUARE -> (shortEdge * 0.062f).coerceIn(dp(10f), dp(34f))
                }
            }
            val r = baseRadius * s
            val hw = if (spec.kind == VirtualControlKind.BUTTON && spec.shape == VirtualButtonShape.PILL) {
                r * 1.75f
            } else {
                r
            }
            val hh = r
            val cx = (spec.xNorm * w).coerceIn(hw + dp(4f), (w - hw - dp(4f)).coerceAtLeast(hw))
            val cy = (spec.yNorm * h).coerceIn(hh + dp(4f), (h - hh - dp(4f)).coerceAtLeast(hh))
            list.add(ControlGeometry(spec, cx, cy, hw, hh, r))
        }
        return list
    }

    private fun hitTestControl(geoms: List<ControlGeometry>, x: Float, y: Float): ControlGeometry? {
        for (i in geoms.indices.reversed()) {
            if (geoms[i].contains(x, y)) return geoms[i]
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        val geoms = computeGeometries()
        if (geoms.isEmpty() && !editMode) return false

        val action = event.actionMasked
        val actionIndex = event.actionIndex

        if (editMode) {
            return handleEditorTouch(event, action, geoms)
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pid = event.getPointerId(actionIndex)
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val hit = hitTestControl(geoms, x, y) ?: return (action == MotionEvent.ACTION_POINTER_DOWN && activeTouches.size() > 0)
                activeTouches.put(
                    pid,
                    ActiveTouch(
                        controlId = hit.spec.id,
                        kind = hit.spec.kind,
                        startX = x,
                        startY = y,
                        origXNorm = hit.spec.xNorm,
                        origYNorm = hit.spec.yNorm
                    )
                )
                if (profile.virtual.hapticFeedback) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } catch (_: Throwable) {
                    }
                }
                applyControlTouchUpdate(hit, x, y, true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTouches.size() == 0) return false
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val active = activeTouches.get(pid) ?: continue
                    val geom = geoms.firstOrNull { it.spec.id == active.controlId } ?: continue
                    if (geom.spec.kind == VirtualControlKind.DPAD || geom.spec.kind == VirtualControlKind.STICK) {
                        applyControlTouchUpdate(geom, event.getX(i), event.getY(i), true)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(actionIndex)
                val active = activeTouches.get(pid)
                if (active != null) {
                    activeTouches.remove(pid)
                    val geom = geoms.firstOrNull { it.spec.id == active.controlId }
                    if (geom != null) {
                        applyControlTouchRelease(geom)
                    }
                    invalidate()
                    return true
                }
                return activeTouches.size() > 0
            }
            MotionEvent.ACTION_CANCEL -> {
                releaseAllInputs()
                return false
            }
        }
        return activeTouches.size() > 0
    }

    private fun handleEditorTouch(
        event: MotionEvent,
        action: Int,
        geoms: List<ControlGeometry>
    ): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                val hit = hitTestControl(geoms, x, y)
                if (hit == null) {
                    selectedControlId = null
                    invalidate()
                    return true
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                selectedControlId = hit.spec.id
                activeTouches.put(
                    event.getPointerId(0),
                    ActiveTouch(
                        controlId = hit.spec.id,
                        kind = hit.spec.kind,
                        startX = x,
                        startY = y,
                        origXNorm = hit.spec.xNorm,
                        origYNorm = hit.spec.yNorm
                    )
                )
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pid = event.getPointerId(0)
                val active = activeTouches.get(pid) ?: return true
                val dx = event.x - active.startX
                val dy = event.y - active.startY
                if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) {
                    active.dragging = true
                }
                if (active.dragging) {
                    var nx = (active.origXNorm + dx / w).coerceIn(0.04f, 0.96f)
                    var ny = (active.origYNorm + dy / h).coerceIn(0.06f, 0.94f)
                    if (profile.virtual.gridSnap) {
                        nx = ((nx / 0.025f).roundToInt() * 0.025f).coerceIn(0.04f, 0.96f)
                        ny = ((ny / 0.025f).roundToInt() * 0.025f).coerceIn(0.06f, 0.94f)
                    }
                    updateControlSpec(active.controlId) { it.copy(xNorm = nx, yNorm = ny) }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val pid = event.getPointerId(0)
                val active = activeTouches.get(pid)
                activeTouches.remove(pid)
                if (active != null) {
                    val current = profile.virtual.controls.firstOrNull { it.id == active.controlId }
                    if (current != null) {
                        if (active.dragging) {
                            listener?.onControlMovedInEditor(current)
                        } else if (action == MotionEvent.ACTION_UP) {
                            listener?.onControlTappedInEditor(current)
                        }
                    }
                }
                invalidate()
                return true
            }
        }
        return true
    }

    private fun updateControlSpec(id: String, transform: (VirtualControlSpec) -> VirtualControlSpec) {
        val nextControls = profile.virtual.controls.map { spec ->
            if (spec.id == id) transform(spec) else spec
        }
        profile = profile.copy(virtual = profile.virtual.copy(controls = nextControls))
    }

    private fun applyControlTouchUpdate(geom: ControlGeometry, x: Float, y: Float, pressed: Boolean) {
        val spec = geom.spec
        when (spec.kind) {
            VirtualControlKind.BUTTON -> {
                val wasDown = pressedButtonIds.contains(spec.id)
                if (pressed && !wasDown) {
                    pressedButtonIds.add(spec.id)
                    if (spec.action.toggleMode) {
                        if (toggledLatchedIds.contains(spec.id)) {
                            toggledLatchedIds.remove(spec.id)
                        } else {
                            toggledLatchedIds.add(spec.id)
                        }
                    }
                    sendButtonNative(spec, true)
                }
            }
            VirtualControlKind.DPAD -> {
                val dx = x - geom.cx
                val dy = y - geom.cy
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val dirs = BooleanArray(4)
                if (dist > geom.radius * 0.16f) {
                    val threshold = dist * 0.38f
                    dirs[0] = dx < -threshold // Left
                    dirs[1] = dx > threshold  // Right
                    dirs[2] = dy < -threshold // Up
                    dirs[3] = dy > threshold  // Down
                }
                dpadDirs = dirs
                if (GameModifierBridge.ensureLoaded()) {
                    try {
                        GamepadBridge.nativeSetVirtualDPad(
                            spec.stickMode, dirs[0], dirs[1], dirs[2], dirs[3]
                        )
                    } catch (_: Throwable) {
                    }
                }
            }
            VirtualControlKind.STICK -> {
                val travel = (geom.radius * 0.68f).coerceAtLeast(1f)
                var dx = x - geom.cx
                var dy = y - geom.cy
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > travel) {
                    dx = dx / dist * travel
                    dy = dy / dist * travel
                }
                val nx = (dx / travel).coerceIn(-1f, 1f)
                val ny = (dy / travel).coerceIn(-1f, 1f)
                stickPositions[spec.id] = nx to ny
                val stickIdx = if (spec.id == "stick_r") 1 else 0
                if (GameModifierBridge.ensureLoaded()) {
                    try {
                        GamepadBridge.nativeSetVirtualStick(stickIdx, nx, ny)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    private fun applyControlTouchRelease(geom: ControlGeometry) {
        val spec = geom.spec
        when (spec.kind) {
            VirtualControlKind.BUTTON -> {
                if (pressedButtonIds.remove(spec.id)) {
                    sendButtonNative(spec, false)
                }
            }
            VirtualControlKind.DPAD -> {
                dpadDirs = BooleanArray(4)
                if (GameModifierBridge.ensureLoaded()) {
                    try {
                        GamepadBridge.nativeSetVirtualDPad(spec.stickMode, false, false, false, false)
                    } catch (_: Throwable) {
                    }
                }
            }
            VirtualControlKind.STICK -> {
                stickPositions.remove(spec.id)
                val stickIdx = if (spec.id == "stick_r") 1 else 0
                if (GameModifierBridge.ensureLoaded()) {
                    try {
                        GamepadBridge.nativeSetVirtualStick(stickIdx, 0f, 0f)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    private fun sendButtonNative(spec: VirtualControlSpec, pressed: Boolean) {
        if (!GameModifierBridge.ensureLoaded()) return
        try {
            GamepadBridge.nativeSetVirtualButton(
                spec.slotId,
                spec.action.type,
                spec.action.code,
                spec.action.code2,
                spec.action.toggleMode,
                pressed
            )
        } catch (_: Throwable) {
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        if (editMode) {
            drawEditorGrid(canvas)
        }

        val geoms = computeGeometries()
        val globalOpacity = profile.virtual.globalOpacityPct / 100f

        for (geom in geoms) {
            val spec = geom.spec
            val baseAlpha = (globalOpacity * spec.opacity).coerceIn(0.15f, 1.0f)
            val alphaInt = if (!spec.visible) 90 else (baseAlpha * 255).toInt().coerceIn(30, 255)
            when (spec.kind) {
                VirtualControlKind.DPAD -> drawDPad(canvas, geom, alphaInt)
                VirtualControlKind.STICK -> drawStick(canvas, geom, alphaInt)
                VirtualControlKind.BUTTON -> drawButton(canvas, geom, alphaInt)
            }
            if (editMode) {
                drawEditorFrame(canvas, geom)
            }
        }
    }

    private fun drawEditorGrid(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val step = 0.05f
        var f = step
        while (f < 1f) {
            canvas.drawLine(w * f, 0f, w * f, h, gridPaint)
            canvas.drawLine(0f, h * f, w, h * f, gridPaint)
            f += step
        }
        gridPaint.color = 0x554FC3F7
        canvas.drawLine(w * 0.5f, 0f, w * 0.5f, h, gridPaint)
        canvas.drawLine(0f, h * 0.5f, w, h * 0.5f, gridPaint)
        gridPaint.color = 0x26FFFFFF
    }

    private fun drawEditorFrame(canvas: Canvas, geom: ControlGeometry) {
        val isSel = geom.spec.id == selectedControlId
        editorDashPaint.color = when {
            isSel -> 0xFF00E5FF.toInt()
            !geom.spec.visible -> 0xAAFF8A65.toInt()
            else -> 0x88FFFFFF.toInt()
        }
        editorDashPaint.strokeWidth = if (isSel) dp(2.2f) else dp(1.2f)
        val pad = dp(4f)
        tmpRect.set(geom.cx - geom.halfW - pad, geom.cy - geom.halfH - pad, geom.cx + geom.halfW + pad, geom.cy + geom.halfH + pad)
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), editorDashPaint)
    }

    private fun drawDPad(canvas: Canvas, geom: ControlGeometry, alphaInt: Int) {
        val r = geom.radius
        val cx = geom.cx
        val cy = geom.cy
        val armW = r * 0.34f

        // Base backdrop circle
        fillPaint.color = Color.argb((alphaInt * 0.35f).toInt(), 12, 16, 24)
        canvas.drawCircle(cx, cy, r * 0.96f, fillPaint)

        // Cross outline
        tmpPath.reset()
        tmpPath.moveTo(cx - armW, cy - r * 0.92f)
        tmpPath.lineTo(cx + armW, cy - r * 0.92f)
        tmpPath.lineTo(cx + armW, cy - armW)
        tmpPath.lineTo(cx + r * 0.92f, cy - armW)
        tmpPath.lineTo(cx + r * 0.92f, cy + armW)
        tmpPath.lineTo(cx + armW, cy + armW)
        tmpPath.lineTo(cx + armW, cy + r * 0.92f)
        tmpPath.lineTo(cx - armW, cy + r * 0.92f)
        tmpPath.lineTo(cx - armW, cy + armW)
        tmpPath.lineTo(cx - r * 0.92f, cy + armW)
        tmpPath.lineTo(cx - r * 0.92f, cy - armW)
        tmpPath.lineTo(cx - armW, cy - armW)
        tmpPath.close()

        fillPaint.color = Color.argb((alphaInt * 0.68f).toInt(), 24, 28, 38)
        canvas.drawPath(tmpPath, fillPaint)
        strokePaint.color = Color.argb((alphaInt * 0.85f).toInt(), 210, 224, 248)
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawPath(tmpPath, strokePaint)

        // Direction highlight wedges + arrows
        val labels = arrayOf("◀", "▶", "▲", "▼")
        val offsetsX = floatArrayOf(-r * 0.62f, r * 0.62f, 0f, 0f)
        val offsetsY = floatArrayOf(0f, 0f, -r * 0.62f, r * 0.62f)
        textPaint.textSize = (r * 0.30f).coerceIn(dp(10f), dp(18f))
        val fm = textPaint.fontMetrics
        val textCenterOffset = -(fm.ascent + fm.descent) * 0.5f

        for (dirIdx in 0 until 4) {
            val active = dpadDirs[dirIdx]
            if (active) {
                fillPaint.color = Color.argb(alphaInt.coerceAtLeast(160), 66, 165, 245)
                when (dirIdx) {
                    0 -> tmpRect.set(cx - r * 0.90f, cy - armW, cx - armW, cy + armW)
                    1 -> tmpRect.set(cx + armW, cy - armW, cx + r * 0.90f, cy + armW)
                    2 -> tmpRect.set(cx - armW, cy - r * 0.90f, cx + armW, cy - armW)
                    else -> tmpRect.set(cx - armW, cy + armW, cx + armW, cy + r * 0.90f)
                }
                canvas.drawRoundRect(tmpRect, dp(4f), dp(4f), fillPaint)
            }
            textPaint.color = if (active) Color.WHITE else Color.argb((alphaInt * 0.9f).toInt(), 230, 238, 250)
            canvas.drawText(labels[dirIdx], cx + offsetsX[dirIdx], cy + offsetsY[dirIdx] + textCenterOffset, textPaint)
        }
    }

    private fun drawStick(canvas: Canvas, geom: ControlGeometry, alphaInt: Int) {
        val r = geom.radius
        val cx = geom.cx
        val cy = geom.cy
        val pos = stickPositions[geom.spec.id]
        val nx = pos?.first ?: 0f
        val ny = pos?.second ?: 0f
        val active = pos != null

        // Outer ring
        fillPaint.color = Color.argb((alphaInt * 0.45f).toInt(), 16, 20, 30)
        canvas.drawCircle(cx, cy, r, fillPaint)
        strokePaint.color = if (active) {
            Color.argb(alphaInt.coerceAtLeast(180), 100, 181, 246)
        } else {
            Color.argb((alphaInt * 0.72f).toInt(), 210, 224, 248)
        }
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawCircle(cx, cy, r, strokePaint)

        // Subtle inner deadzone ring
        strokePaint.color = Color.argb((alphaInt * 0.30f).toInt(), 255, 255, 255)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, r * 0.30f, strokePaint)

        // Thumb cap
        val travel = r * 0.68f
        val kx = cx + nx * travel
        val ky = cy + ny * travel
        val knobR = r * 0.44f
        fillPaint.color = if (active) {
            Color.argb(alphaInt.coerceAtLeast(190), 56, 142, 230)
        } else {
            Color.argb((alphaInt * 0.80f).toInt(), 45, 56, 74)
        }
        canvas.drawCircle(kx, ky, knobR, fillPaint)
        strokePaint.color = Color.argb(alphaInt.coerceAtLeast(140), 235, 245, 255)
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawCircle(kx, ky, knobR, strokePaint)

        if (geom.spec.label.isNotBlank()) {
            subTextPaint.textSize = (r * 0.24f).coerceIn(dp(9f), dp(13f))
            subTextPaint.color = Color.argb((alphaInt * 0.85f).toInt(), 240, 246, 255)
            val fm = subTextPaint.fontMetrics
            canvas.drawText(geom.spec.label, kx, ky - (fm.ascent + fm.descent) * 0.5f, subTextPaint)
        }
    }

    private fun drawButton(canvas: Canvas, geom: ControlGeometry, alphaInt: Int) {
        val spec = geom.spec
        val isCtrlLatched = spec.action.type == GamepadBridge.ACTION_CTRL_TOGGLE && ctrlToggleLatched
        val isCustomLatched = toggledLatchedIds.contains(spec.id) || isCtrlLatched
        val isPressed = pressedButtonIds.contains(spec.id) || isCustomLatched

        val bgColor = when {
            isCustomLatched -> Color.argb(alphaInt.coerceAtLeast(195), 31, 122, 69)
            isPressed -> Color.argb(alphaInt.coerceAtLeast(195), 53, 122, 214)
            else -> Color.argb((alphaInt * 0.72f).toInt(), 18, 23, 33)
        }
        val borderColor = when {
            isCustomLatched -> Color.argb(240, 129, 230, 165)
            isPressed -> Color.argb(240, 144, 202, 249)
            else -> Color.argb((alphaInt * 0.82f).toInt(), 215, 226, 242)
        }

        fillPaint.color = bgColor
        strokePaint.color = borderColor
        strokePaint.strokeWidth = if (isPressed) dp(2.0f) else dp(1.3f)

        if (spec.shape == VirtualButtonShape.CIRCLE) {
            canvas.drawCircle(geom.cx, geom.cy, geom.radius, fillPaint)
            canvas.drawCircle(geom.cx, geom.cy, geom.radius, strokePaint)
        } else {
            val cr = if (spec.shape == VirtualButtonShape.PILL) geom.halfH else dp(10f)
            tmpRect.set(geom.cx - geom.halfW, geom.cy - geom.halfH, geom.cx + geom.halfW, geom.cy + geom.halfH)
            canvas.drawRoundRect(tmpRect, cr, cr, fillPaint)
            canvas.drawRoundRect(tmpRect, cr, cr, strokePaint)
        }

        val label = spec.label.ifBlank { "?" }
        val targetSize = if (spec.shape == VirtualButtonShape.PILL) {
            (geom.halfH * 0.76f).coerceIn(dp(7f), dp(15f))
        } else {
            (geom.radius * (if (label.length > 2) 0.54f else 0.78f)).coerceIn(dp(7f), dp(18f))
        }
        textPaint.textSize = targetSize
        textPaint.color = Color.argb(
            if (isPressed) 255 else alphaInt.coerceAtLeast(170),
            255, 255, 255
        )
        val fm = textPaint.fontMetrics
        canvas.drawText(label, geom.cx, geom.cy - (fm.ascent + fm.descent) * 0.5f, textPaint)
    }
}

/**
 * Lightweight on-screen virtual mouse cursor overlay driven by physical or virtual analog sticks.
 */
class VirtualCursorOverlayView(context: Context) : View(context) {
    private var normX = 0.5f
    private var normY = 0.5f
    private var cursorVisible = false
    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xF5FFFFFF.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
        strokeJoin = Paint.Join.ROUND
        color = 0xDD111827.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        color = 0xAA4FC3F7.toInt()
    }
    private val arrowPath = Path()

    init {
        isClickable = false
        isFocusable = false
        visibility = GONE
    }

    fun updateFromPackedParam(param: Int) {
        val ix = param and 0x3FFF
        val iy = (param ushr 14) and 0x3FFF
        val vis = ((param ushr 28) and 1) != 0
        normX = (ix / 16383f).coerceIn(0f, 1f)
        normY = (iy / 16383f).coerceIn(0f, 1f)
        cursorVisible = vis
        visibility = if (vis) VISIBLE else GONE
        if (vis) invalidate()
    }

    fun hide() {
        cursorVisible = false
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cursorVisible || width <= 0 || height <= 0) return
        val x = normX * (width - 1)
        val y = normY * (height - 1)
        val s = 18f * density

        canvas.drawCircle(x, y, 7f * density, ringPaint)

        arrowPath.reset()
        arrowPath.moveTo(x, y)
        arrowPath.lineTo(x, y + s)
        arrowPath.lineTo(x + s * 0.30f, y + s * 0.72f)
        arrowPath.lineTo(x + s * 0.52f, y + s * 1.10f)
        arrowPath.lineTo(x + s * 0.68f, y + s * 1.02f)
        arrowPath.lineTo(x + s * 0.46f, y + s * 0.64f)
        arrowPath.lineTo(x + s * 0.80f, y + s * 0.62f)
        arrowPath.close()

        canvas.drawPath(arrowPath, fillPaint)
        canvas.drawPath(arrowPath, strokePaint)
    }
}
