package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.io.File

/**
 * Shared look-and-feel helpers for the launcher screens.
 *
 * The launcher only uses framework widgets (no AndroidX / Material Components), so the
 * pieces that cannot be expressed as static resources (per-game accent colors, status
 * chips, icon badges) are built here. Static colors always come from resources so that
 * they follow day/night automatically.
 */
interface GameAccentHost {
    fun activeGameAccentColor(): Int?
}

internal object UiKit {

    /** Semantic color pair (container + content) used by chips. */
    enum class Tone(val bgRes: Int, val fgRes: Int) {
        NEUTRAL(R.color.tone_neutral_bg, R.color.tone_neutral_fg),
        INFO(R.color.tone_info_bg, R.color.tone_info_fg),
        SUCCESS(R.color.tone_success_bg, R.color.tone_success_fg),
        WARNING(R.color.tone_warning_bg, R.color.tone_warning_fg),
        DANGER(R.color.tone_danger_bg, R.color.tone_danger_fg),
        PRIMARY(R.color.tone_primary_bg, R.color.tone_primary_fg),
    }

    class Chip(val label: CharSequence, val tone: Tone)

    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    fun dpInt(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun themeColor(context: Context, attrRes: Int, fallbackColorRes: Int): Int {
        val tv = TypedValue()
        return if (context.theme != null && context.theme.resolveAttribute(attrRes, tv, true)) {
            if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
        } else {
            context.getColor(fallbackColorRes)
        }
    }

    fun primaryColor(context: Context): Int = themeColor(context, R.attr.uiPrimary, R.color.ui_primary)
    fun onPrimaryColor(context: Context): Int = themeColor(context, R.attr.uiOnPrimary, R.color.ui_on_primary)
    fun bgColor(context: Context): Int = themeColor(context, R.attr.uiBg, R.color.ui_bg)
    fun surfaceColor(context: Context): Int = themeColor(context, R.attr.uiSurface, R.color.ui_surface)
    fun surfaceVariantColor(context: Context): Int = themeColor(context, R.attr.uiSurfaceVariant, R.color.ui_surface_variant)
    fun surfaceDialogColor(context: Context): Int = themeColor(context, R.attr.uiSurfaceDialog, R.color.ui_surface_dialog)
    fun btnTonalColor(context: Context): Int = themeColor(context, R.attr.uiBtnTonal, R.color.ui_btn_tonal)
    fun primaryToneBgColor(context: Context): Int = themeColor(context, R.attr.tonePrimaryBg, R.color.tone_primary_bg)
    fun primaryToneFgColor(context: Context): Int = themeColor(context, R.attr.tonePrimaryFg, R.color.tone_primary_fg)

    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.rgb(
            (Color.red(from) * inv + Color.red(to) * ratio).toInt(),
            (Color.green(from) * inv + Color.green(to) * ratio).toInt(),
            (Color.blue(from) * inv + Color.blue(to) * ratio).toInt()
        )
    }

    /**
     * A game's accent color tuned for use as a foreground (icons, text) on the current
     * surface: the catalog colors are picked for light backgrounds and get too dim on dark.
     */
    fun accentOnSurface(context: Context, accent: Int): Int =
        if (isNight(context)) blend(accent, Color.WHITE, 0.38f) else accent

    fun contrastOn(color: Int): Int {
        val luma = (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
        return if (luma > 0.62f) 0xFF10141C.toInt() else Color.WHITE
    }

    fun resolveGameAccentColor(
        context: Context,
        gameId: String?,
        gameDir: File? = null,
        dirName: String? = null,
        saveDir: File? = null
    ): Int {
        val resolvedId = gameId?.takeIf { it.isNotBlank() }
            ?: if (gameDir != null || !dirName.isNullOrBlank() || saveDir != null) {
                GameCatalog.classifyDirectory(
                    gameDir = gameDir,
                    dirName = dirName ?: "",
                    saveDir = saveDir,
                    displayName = dirName ?: ""
                )
            } else {
                null
            }
        return if (!resolvedId.isNullOrBlank()) {
            GameCatalog.descriptorFor(context, resolvedId).accentColor
        } else {
            primaryColor(context)
        }
    }

    fun tintSwitch(sw: Switch, accent: Int) {
        sw.showText = false
        val ctx = sw.context
        val onColor = accentOnSurface(ctx, accent)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val offThumb = if (isNight(ctx)) 0xFF9CA3B4.toInt() else 0xFFECEEF3.toInt()
        val offTrack = if (isNight(ctx)) 0xFF343946.toInt() else 0xFFB8BDC9.toInt()
        sw.thumbTintList = ColorStateList(states, intArrayOf(onColor, offThumb))
        sw.trackTintList = ColorStateList(states, intArrayOf(withAlpha(onColor, 0x66), offTrack))
    }

    fun tintSeekBar(sb: SeekBar, accent: Int) {
        val ctx = sb.context
        val onColor = accentOnSurface(ctx, accent)
        sb.thumbTintList = ColorStateList.valueOf(onColor)
        sb.progressTintList = ColorStateList.valueOf(onColor)
        sb.progressBackgroundTintList = ColorStateList.valueOf(
            withAlpha(onColor, if (isNight(ctx)) 0x3A else 0x26)
        )
    }

    /**
     * Tints an already-shown [AlertDialog]'s action buttons, single/multi-choice list checkmarks,
     * and standard interactive widgets inside its custom view to follow a per-game [accent] color.
     */
    fun tintAlertDialog(dialog: AlertDialog, accent: Int): AlertDialog {
        val ctx = dialog.context
        val onColor = accentOnSurface(ctx, accent)
        val disabledColor = ctx.getColor(R.color.ui_text_disabled)
        val uncheckedColor = ctx.getColor(R.color.ui_text_secondary)
        val dangerColor = ctx.getColor(R.color.tone_danger_fg)

        val btnColors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), IntArray(0)),
            intArrayOf(disabledColor, onColor)
        )
        for (which in intArrayOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL
        )) {
            val btn = dialog.getButton(which) ?: continue
            if (btn.currentTextColor != dangerColor) {
                btn.setTextColor(btnColors)
            }
        }

        val checkTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), IntArray(0)),
            intArrayOf(onColor, uncheckedColor)
        )
        val editTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), IntArray(0)),
            intArrayOf(onColor, ctx.getColor(R.color.ui_text_tertiary))
        )

        fun tintViewTree(view: View) {
            when (view) {
                is CheckedTextView -> {
                    view.checkMarkTintList = checkTint
                    view.compoundDrawableTintList = checkTint
                }
                is Switch -> tintSwitch(view, accent)
                is CompoundButton -> view.buttonTintList = checkTint
                is SeekBar -> tintSeekBar(view, accent)
                is ProgressBar -> {
                    val tint = ColorStateList.valueOf(onColor)
                    view.progressTintList = tint
                    view.indeterminateTintList = tint
                }
                is EditText -> {
                    view.backgroundTintList = editTint
                    view.highlightColor = withAlpha(onColor, 0x4D)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        view.textCursorDrawable?.mutate()?.setTint(onColor)
                    }
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    tintViewTree(view.getChildAt(i))
                }
            }
        }

        dialog.listView?.let { lv ->
            tintViewTree(lv)
            lv.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    if (child != null) tintViewTree(child)
                }

                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
        }

        dialog.findViewById<ViewGroup>(android.R.id.custom)?.let(::tintViewTree)
        return dialog
    }

    fun styleTonalAccentButton(button: Button, accent: Int) {
        val ctx = button.context
        val onColor = accentOnSurface(ctx, accent)
        val radius = ctx.resources.getDimension(R.dimen.ui_radius_button)
        val bgTint = blend(surfaceColor(ctx), accent, if (isNight(ctx)) 0.22f else 0.11f)
        val bgStates = android.graphics.drawable.StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(ctx.getColor(R.color.ui_btn_disabled))
                }
            )
            addState(
                IntArray(0),
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(bgTint)
                }
            )
        }
        button.background = RippleDrawable(
            ColorStateList.valueOf(ctx.getColor(R.color.ui_ripple)), bgStates, null
        )
        button.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), IntArray(0)),
                intArrayOf(ctx.getColor(R.color.ui_text_disabled), onColor)
            )
        )
    }

    /** Surface/tonal background + crisp danger label button with subtle danger-tinted border. */
    fun styleWhiteDangerButton(button: Button) {
        val ctx = button.context
        val night = isNight(ctx)
        val radius = ctx.resources.getDimension(R.dimen.ui_radius_button)
        val dangerFg = ctx.getColor(R.color.tone_danger_fg)
        val surfaceBg = if (night) {
            ctx.getColor(R.color.tone_danger_bg)
        } else {
            surfaceColor(ctx)
        }
        val borderColor = withAlpha(dangerFg, if (night) 0x55 else 0x44)
        val bgStates = android.graphics.drawable.StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(ctx.getColor(R.color.ui_btn_disabled))
                }
            )
            addState(
                IntArray(0),
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(surfaceBg)
                    setStroke(dpInt(ctx, 1).coerceAtLeast(1), borderColor)
                }
            )
        }
        button.background = RippleDrawable(
            ColorStateList.valueOf(ctx.getColor(R.color.ui_ripple)), bgStates, null
        )
        button.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), IntArray(0)),
                intArrayOf(ctx.getColor(R.color.ui_text_disabled), dangerFg)
            )
        )
    }

    fun styleAccentChip(view: TextView, accent: Int, dot: Boolean = false) {
        val ctx = view.context
        val fg = accentOnSurface(ctx, accent)
        val bg = blend(surfaceColor(ctx), accent, if (isNight(ctx)) 0.30f else 0.15f)
        view.setTextColor(fg)
        view.background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 100f)
            setColor(bg)
        }
        if (dot) {
            val dotDrawable = ctx.getDrawable(R.drawable.chip_dot)!!.mutate()
            dotDrawable.setTint(fg)
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(dotDrawable, null, null, null)
            view.compoundDrawablePadding = dpInt(ctx, 5)
        } else {
            view.setCompoundDrawablesRelative(null, null, null, null)
        }
    }

    /** Pill-shaped status/tag chip. With [dot], a small leading status dot is shown. */
    fun styleChip(view: TextView, tone: Tone, dot: Boolean = false) {
        val ctx = view.context
        val fg = if (tone == Tone.PRIMARY) primaryToneFgColor(ctx) else ctx.getColor(tone.fgRes)
        val bg = if (tone == Tone.PRIMARY) primaryToneBgColor(ctx) else ctx.getColor(tone.bgRes)
        view.setTextColor(fg)
        view.background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 100f)
            setColor(bg)
        }
        if (dot) {
            val dotDrawable = ctx.getDrawable(R.drawable.chip_dot)!!.mutate()
            dotDrawable.setTint(fg)
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(dotDrawable, null, null, null)
            view.compoundDrawablePadding = dpInt(ctx, 5)
        } else {
            view.setCompoundDrawablesRelative(null, null, null, null)
        }
    }

    /** Binds up to three trailing chips of a `list_item_entry` row; unused slots are hidden. */
    fun bindChips(row: View, chips: List<Chip>) {
        val ids = intArrayOf(R.id.chip1, R.id.chip2, R.id.chip3)
        for ((index, id) in ids.withIndex()) {
            val view = row.findViewById<TextView>(id) ?: continue
            val chip = chips.getOrNull(index)
            if (chip == null) {
                view.visibility = View.GONE
            } else {
                view.visibility = View.VISIBLE
                view.text = chip.label
                styleChip(view, chip.tone)
            }
        }
    }

    /**
     * Card surface with a soft wash of the game's accent color fading out from the start
     * edge. When [clickable], the card also shows a ripple bounded by its rounded shape.
     */
    fun accentCard(context: Context, accent: Int, radiusRes: Int, clickable: Boolean): Drawable {
        val radius = context.resources.getDimension(radiusRes)
        val surface = GradientDrawable().apply {
            cornerRadius = radius
            setColor(surfaceColor(context))
            setStroke(dpInt(context, 1), context.getColor(R.color.ui_outline))
        }
        val washAlpha = if (isNight(context)) 0x30 else 0x1C
        val wash = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(withAlpha(accent, washAlpha), withAlpha(accent, 0), withAlpha(accent, 0))
        ).apply { cornerRadius = radius }
        val layers = LayerDrawable(arrayOf<Drawable>(surface, wash))
        if (!clickable) return layers
        return RippleDrawable(
            ColorStateList.valueOf(context.getColor(R.color.ui_ripple)), layers, null
        )
    }

    private val builtInIconCache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private fun decodeCachedBuiltInIcon(context: Context, resId: Int, targetPx: Int): Bitmap? {
        val clampedPx = targetPx.coerceIn(64, 512)
        val cacheKey = "$resId:$clampedPx"
        synchronized(builtInIconCache) {
            builtInIconCache.get(cacheKey)?.takeIf { !it.isRecycled }
        }?.let { return it }

        return try {
            val res = context.resources
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(res, resId, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return null
            var sampleSize = 1
            while ((srcW / (sampleSize * 2)) >= clampedPx && (srcH / (sampleSize * 2)) >= clampedPx) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeResource(res, resId, opts) ?: return null
            synchronized(builtInIconCache) {
                builtInIconCache.put(cacheKey, decoded)
            }
            decoded
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Shows a game's icon inside its rounded frame (game list and game hub).
     *
     * Rance 10 ships full-bleed square artwork, so it fills the frame and is clipped to the
     * rounded corners. Every other icon is free-form (often touching its own edges) and
     * keeps a margin so the corners do not cut into it.
     */
    fun bindGameIcon(
        frame: View,
        iconView: ImageView,
        desc: GameDescriptor,
        item: Item?,
        radiusDp: Float
    ) {
        val ctx = frame.context
        val frameTag = "${desc.iconFrameColor}:$radiusDp"
        if (frame.tag != frameTag) {
            frame.background = GradientDrawable().apply {
                cornerRadius = dp(ctx, radiusDp)
                setColor(desc.iconFrameColor)
            }
            frame.clipToOutline = true
            frame.tag = frameTag
        }

        val builtIn = GameCatalog.isBuiltInGameId(desc.id)
        val frameWidth = frame.layoutParams?.width?.takeIf { it > 0 } ?: dpInt(ctx, 56)
        val pad = when {
            desc.id == GameCatalog.ID_DOHNA_DOHNA -> dpInt(ctx, 2)
            builtIn -> 0
            else -> frameWidth / 10
        }
        if (frame.paddingLeft != pad || frame.paddingTop != pad ||
            frame.paddingRight != pad || frame.paddingBottom != pad
        ) {
            frame.setPadding(pad, pad, pad, pad)
        }

        val targetPx = (frameWidth - pad * 2).coerceAtLeast(dpInt(ctx, 48))
        val bitmap = if (builtIn) {
            decodeCachedBuiltInIcon(ctx, desc.iconRes, targetPx)
        } else {
            item?.getIconBitmap(targetPx)
        }
        if (bitmap != null) {
            iconView.setImageBitmap(bitmap)
        } else {
            iconView.setImageResource(desc.iconRes)
        }
    }

    /** Icon centered in a softly tinted rounded square (leading icon of action rows). */
    fun tonalIcon(context: Context, iconRes: Int, color: Int): Drawable {
        val size = dpInt(context, 38)
        val iconSize = dpInt(context, 20)
        val plate = GradientDrawable().apply {
            cornerRadius = dp(context, 12f)
            setColor(withAlpha(color, if (isNight(context)) 0x30 else 0x1F))
            setSize(size, size)
        }
        val icon = context.getDrawable(iconRes)!!.mutate()
        icon.setTint(color)
        return LayerDrawable(arrayOf(plate, icon)).apply {
            setLayerSize(1, iconSize, iconSize)
            setLayerGravity(1, Gravity.CENTER)
        }
    }

    /** Turns a button into a settings-like row: tinted icon at the start, chevron at the end. */
    fun styleActionRow(button: Button, iconRes: Int, accent: Int) {
        val ctx = button.context
        val chevron = ctx.getDrawable(R.drawable.ic_chevron_right)!!.mutate()
        chevron.setTint(ctx.getColor(R.color.ui_text_tertiary))
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(
            tonalIcon(ctx, iconRes, accent), null, chevron, null
        )
    }

    /** Solid accent-colored button background with a light ripple. */
    fun accentButtonBackground(context: Context, color: Int, radiusDp: Float): Drawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp)
            setColor(color)
        }
        return RippleDrawable(
            ColorStateList.valueOf(context.getColor(R.color.ui_ripple_on_color)), shape, null
        )
    }

    /**
     * Label with a leading icon that stays centered together with the text (a compound
     * drawable would stick to the button edge instead).
     */
    fun iconLabel(
        context: Context,
        iconRes: Int,
        tint: Int,
        label: CharSequence,
        iconDp: Int = 22,
        gapDp: Int = 6
    ): CharSequence {
        val size = dpInt(context, iconDp)
        val icon = context.getDrawable(iconRes)!!.mutate()
        icon.setTint(tint)
        icon.setBounds(0, 0, size, size)
        return SpannableStringBuilder()
            .append(" ", CenteredIconSpan(icon, dpInt(context, gapDp)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            .append(label)
    }

    /** Wraps a dialog content view (e.g. an EditText) so it lines up with the dialog title. */
    fun dialogContent(context: Context, child: View): View =
        FrameLayout(context).apply {
            setPadding(dpInt(context, 20), dpInt(context, 8), dpInt(context, 20), 0)
            addView(
                child,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

    /**
     * Material You tri-tone circular palette swatch: top half = primary, bottom-left = tonal
     * container, bottom-right = neutral surface tint, with an outer ring and centered check badge
     * when selected.
     */
    fun paletteSwatchDrawable(
        context: Context,
        primary: Int,
        onPrimary: Int,
        tonalBg: Int,
        canvasBg: Int,
        selected: Boolean
    ): Drawable = object : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val outlineColor = context.getColor(R.color.ui_outline)
        private val ringWidth = dp(context, 2.2f)
        private val ringGap = dp(context, 2.5f)
        private val outlineWidth = dp(context, 1f).coerceAtLeast(1f)
        private val arcRect = RectF()
        private val checkPath = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val outerR = minOf(b.width(), b.height()) / 2f
            val innerR = (outerR - ringWidth - ringGap).coerceAtLeast(1f)

            if (selected) {
                strokePaint.color = primary
                strokePaint.strokeWidth = ringWidth
                canvas.drawCircle(cx, cy, outerR - ringWidth / 2f, strokePaint)
            }

            arcRect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)

            // Top semicircle: primary color
            fillPaint.color = primary
            canvas.drawArc(arcRect, 180f, 180f, true, fillPaint)

            // Bottom-left quarter: tonal container
            fillPaint.color = tonalBg
            canvas.drawArc(arcRect, 90f, 90f, true, fillPaint)

            // Bottom-right quarter: page canvas tint
            fillPaint.color = canvasBg
            canvas.drawArc(arcRect, 0f, 90f, true, fillPaint)

            // Crisp hairline outline around the color disc
            strokePaint.color = outlineColor
            strokePaint.strokeWidth = outlineWidth
            canvas.drawCircle(cx, cy, innerR - outlineWidth / 2f, strokePaint)

            if (selected) {
                val badgeR = innerR * 0.44f
                fillPaint.color = primary
                canvas.drawCircle(cx, cy, badgeR, fillPaint)

                checkPaint.color = onPrimary
                checkPaint.strokeWidth = dp(context, 1.9f)
                checkPath.reset()
                checkPath.moveTo(cx - badgeR * 0.46f, cy + badgeR * 0.04f)
                checkPath.lineTo(cx - badgeR * 0.12f, cy + badgeR * 0.36f)
                checkPath.lineTo(cx + badgeR * 0.48f, cy - badgeR * 0.32f)
                canvas.drawPath(checkPath, checkPaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
            checkPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
            checkPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** Centered icon stacked above a single concise empty-state label. */
    fun bindEmptyState(view: TextView, iconRes: Int, text: CharSequence) {
        val ctx = view.context
        val size = dpInt(ctx, 36)
        val icon = ctx.getDrawable(iconRes)!!.mutate()
        icon.setTint(ctx.getColor(R.color.ui_text_tertiary))
        icon.setBounds(0, 0, size, size)
        view.setCompoundDrawables(null, icon, null, null)
        view.compoundDrawablePadding = dpInt(ctx, 10)
        view.gravity = Gravity.CENTER
        view.text = text
    }

    /** Draws an icon vertically centered on the text without changing the line height. */
    private class CenteredIconSpan(
        private val icon: Drawable,
        private val gapPx: Int
    ) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int = icon.bounds.width() + gapPx

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val metrics = paint.fontMetricsInt
            val centerY = y + (metrics.ascent + metrics.descent) / 2f
            canvas.save()
            canvas.translate(x, centerY - icon.bounds.height() / 2f)
            icon.draw(canvas)
            canvas.restore()
        }
    }
}
