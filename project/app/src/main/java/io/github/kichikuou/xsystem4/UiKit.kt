package io.github.kichikuou.xsystem4

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * Shared look-and-feel helpers for the launcher screens.
 *
 * The launcher only uses framework widgets (no AndroidX / Material Components), so the
 * pieces that cannot be expressed as static resources (per-game accent colors, status
 * chips, icon badges) are built here. Static colors always come from resources so that
 * they follow day/night automatically.
 */
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

    /** Pill-shaped status/tag chip. With [dot], a small leading status dot is shown. */
    fun styleChip(view: TextView, tone: Tone, dot: Boolean = false) {
        val ctx = view.context
        val fg = ctx.getColor(tone.fgRes)
        view.setTextColor(fg)
        view.background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 100f)
            setColor(ctx.getColor(tone.bgRes))
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
            setColor(context.getColor(R.color.ui_surface))
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
        frame.background = GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp)
            setColor(desc.iconFrameColor)
        }
        frame.clipToOutline = true

        val pad = when (desc.id) {
            GameCatalog.ID_RANCE10, GameCatalog.ID_EVENICLE -> 0
            GameCatalog.ID_DOHNA_DOHNA -> dpInt(ctx, 2)
            else -> frame.layoutParams.width / 10
        }
        frame.setPadding(pad, pad, pad, pad)

        val builtIn = desc.id == GameCatalog.ID_RANCE10 ||
            desc.id == GameCatalog.ID_DOHNA_DOHNA ||
            desc.id == GameCatalog.ID_EVENICLE
        val bitmap = if (builtIn) null else item?.getIconBitmap(frame.layoutParams.width - pad * 2)
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
