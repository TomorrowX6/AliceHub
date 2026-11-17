package io.github.kichikuou.xsystem4

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Compact dark-glass grid selector for controller buttons, keys, and stick modes.
 */
object KeyPickerDialog {

    private const val BG_SURFACE = 0xF50F172A.toInt()
    private const val BG_CHIP = 0xFF1E293B.toInt()
    private const val BG_CHIP_ACTIVE = 0xFF0EA5E9.toInt()
    private const val STROKE_CARD = 0xFF334155.toInt()
    private const val STROKE_CHIP = 0xFF2B3A4E.toInt()
    private const val TEXT_PRIMARY = 0xFFF8FAFC.toInt()
    private const val TEXT_MUTED = 0xFF94A3B8.toInt()
    private const val TEXT_ON_ACCENT = 0xFF06121F.toInt()

    fun showActionPicker(
        context: Context,
        title: String,
        current: GamepadActionSpec,
        allowNone: Boolean = true,
        onSelected: (GamepadActionSpec) -> Unit
    ) {
        val rawCatalog = GamepadBridge.buildKeyActionCatalog()
        val categories = rawCatalog.map { (catName, entries) ->
            val filtered = if (allowNone) {
                entries
            } else {
                entries.filterNot { it.action.type == GamepadBridge.ACTION_NONE }
            }
            translateCategory(context, catName) to filtered
        }.filter { it.second.isNotEmpty() }

        val lang = AppSettings.resolveLocale(context).language
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) 5 else 3

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiKit.dpInt(context, 14)
            setPadding(pad, UiKit.dpInt(context, 12), pad, UiKit.dpInt(context, 12))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(context, 16f)
                setColor(BG_SURFACE)
                setStroke(UiKit.dpInt(context, 1), STROKE_CARD)
            }
        }

        // Top title + lock toggle + cancel row
        var lockMode = current.toggleMode
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiKit.dpInt(context, 8))
        }
        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(titleTv)

        val lockLabel = TextView(context).apply {
            text = GamepadBridge.tr(context, "锁定", "固定", "Lock")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(TEXT_MUTED)
            setPadding(0, 0, UiKit.dpInt(context, 4), 0)
        }
        val lockSwitch = Switch(context).apply {
            isChecked = lockMode
            setOnCheckedChangeListener { _, isChecked -> lockMode = isChecked }
        }
        topRow.addView(lockLabel)
        topRow.addView(lockSwitch)

        if (allowNone) {
            val clearChip = makePill(
                context,
                GamepadBridge.tr(context, "清除", "クリア", "Clear"),
                selected = false
            ) {
                onSelected(GamepadActionSpec(GamepadBridge.ACTION_NONE, 0, 0, false))
                dialog.dismiss()
            }
            topRow.addView(clearChip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = UiKit.dpInt(context, 8)
            })
        }

        val closeBtn = makePill(context, "✕", selected = false) { dialog.dismiss() }.apply {
            setPadding(UiKit.dpInt(context, 10), UiKit.dpInt(context, 4), UiKit.dpInt(context, 10), UiKit.dpInt(context, 4))
        }
        topRow.addView(closeBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = UiKit.dpInt(context, 8)
        })
        root.addView(topRow)

        // Category tab pills
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, UiKit.dpInt(context, 8))
        }
        val tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        tabScroll.addView(tabRow)
        root.addView(tabScroll)

        // Scrollable grid area
        val gridScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
        }
        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        gridScroll.addView(gridContainer)
        val maxGridH = UiKit.dpInt(context, if (isLandscape) 210 else 320)
        root.addView(gridScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            maxGridH
        ))

        var selectedCat = categories.indexOfFirst { (_, entries) ->
            entries.any { it.action.type == current.type && it.action.code == current.code && it.action.code2 == current.code2 }
        }.coerceAtLeast(0)

        fun renderGrid() {
            gridContainer.removeAllViews()
            tabRow.removeAllViews()
            categories.forEachIndexed { idx, (label, _) ->
                val pill = makePill(context, label, selected = idx == selectedCat) {
                    selectedCat = idx
                    renderGrid()
                }
                tabRow.addView(pill, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = UiKit.dpInt(context, 6)
                })
            }

            val entries = categories.getOrNull(selectedCat)?.second ?: emptyList()
            val gap = UiKit.dpInt(context, 6)
            var idx = 0
            while (idx < entries.size) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, gap)
                }
                for (c in 0 until columns) {
                    val entry = entries.getOrNull(idx + c)
                    if (entry != null) {
                        val isMatch = entry.action.type == current.type &&
                            entry.action.code == current.code &&
                            entry.action.code2 == current.code2
                        val label = when {
                            lang.startsWith("zh") -> entry.labelZh
                            lang.startsWith("ja") -> entry.labelJa
                            else -> entry.labelEn
                        }
                        val btn = TextView(context).apply {
                            text = label
                            gravity = Gravity.CENTER
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                            setTypeface(null, if (isMatch) Typeface.BOLD else Typeface.NORMAL)
                            setTextColor(if (isMatch) TEXT_ON_ACCENT else TEXT_PRIMARY)
                            setPadding(UiKit.dpInt(context, 6), UiKit.dpInt(context, 9), UiKit.dpInt(context, 6), UiKit.dpInt(context, 9))
                            background = GradientDrawable().apply {
                                cornerRadius = UiKit.dp(context, 9f)
                                setColor(if (isMatch) BG_CHIP_ACTIVE else BG_CHIP)
                                setStroke(UiKit.dpInt(context, 1), if (isMatch) BG_CHIP_ACTIVE else STROKE_CHIP)
                            }
                            setOnClickListener {
                                val chosen = entry.action.copy(
                                    toggleMode = lockMode && entry.action.type in listOf(
                                        GamepadBridge.ACTION_VK,
                                        GamepadBridge.ACTION_MOUSE_BTN,
                                        GamepadBridge.ACTION_COMBO
                                    )
                                )
                                onSelected(chosen)
                                dialog.dismiss()
                            }
                        }
                        row.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (c < columns - 1) rightMargin = gap
                        })
                    } else {
                        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply {
                            if (c < columns - 1) rightMargin = gap
                        })
                    }
                }
                gridContainer.addView(row)
                idx += columns
            }
            gridScroll.scrollTo(0, 0)
        }

        renderGrid()
        dialog.setContentView(root)
        val dm = context.resources.displayMetrics
        val targetW = minOf(UiKit.dpInt(context, if (isLandscape) 620 else 360), (dm.widthPixels * 0.92f).toInt())
        dialog.window?.setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun showStickModePicker(
        context: Context,
        title: String,
        currentMode: Int,
        allowWheel: Boolean = true,
        onSelected: (Int) -> Unit
    ) {
        val modes = if (allowWheel) {
            listOf(
                GamepadBridge.STICK_MOUSE,
                GamepadBridge.STICK_ARROWS,
                GamepadBridge.STICK_WASD,
                GamepadBridge.STICK_WHEEL,
                GamepadBridge.STICK_JOYPAD,
                GamepadBridge.STICK_NONE
            )
        } else {
            listOf(
                GamepadBridge.STICK_MOUSE,
                GamepadBridge.STICK_ARROWS,
                GamepadBridge.STICK_WASD,
                GamepadBridge.STICK_JOYPAD,
                GamepadBridge.STICK_NONE
            )
        }

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiKit.dpInt(context, 14)
            setPadding(pad, UiKit.dpInt(context, 12), pad, UiKit.dpInt(context, 14))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(context, 16f)
                setColor(BG_SURFACE)
                setStroke(UiKit.dpInt(context, 1), STROKE_CARD)
            }
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiKit.dpInt(context, 10))
        }
        header.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(makePill(context, "✕", selected = false) { dialog.dismiss() }.apply {
            setPadding(UiKit.dpInt(context, 10), UiKit.dpInt(context, 4), UiKit.dpInt(context, 10), UiKit.dpInt(context, 4))
        })
        root.addView(header)

        val cols = 3
        val gap = UiKit.dpInt(context, 8)
        var i = 0
        while (i < modes.size) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, gap)
            }
            for (c in 0 until cols) {
                val m = modes.getOrNull(i + c)
                if (m != null) {
                    val sel = m == currentMode
                    val chip = TextView(context).apply {
                        text = GamepadBridge.stickModeLabel(context, m)
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                        setTextColor(if (sel) TEXT_ON_ACCENT else TEXT_PRIMARY)
                        setPadding(UiKit.dpInt(context, 8), UiKit.dpInt(context, 10), UiKit.dpInt(context, 8), UiKit.dpInt(context, 10))
                        background = GradientDrawable().apply {
                            cornerRadius = UiKit.dp(context, 10f)
                            setColor(if (sel) BG_CHIP_ACTIVE else BG_CHIP)
                            setStroke(UiKit.dpInt(context, 1), if (sel) BG_CHIP_ACTIVE else STROKE_CHIP)
                        }
                        setOnClickListener {
                            onSelected(m)
                            dialog.dismiss()
                        }
                    }
                    row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (c < cols - 1) rightMargin = gap
                    })
                } else {
                    row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply {
                        if (c < cols - 1) rightMargin = gap
                    })
                }
            }
            root.addView(row)
            i += cols
        }

        dialog.setContentView(root)
        val dm = context.resources.displayMetrics
        val targetW = minOf(UiKit.dpInt(context, 440), (dm.widthPixels * 0.88f).toInt())
        dialog.window?.setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun makePill(
        context: Context,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (selected) TEXT_ON_ACCENT else TEXT_PRIMARY)
        setPadding(UiKit.dpInt(context, 12), UiKit.dpInt(context, 5), UiKit.dpInt(context, 12), UiKit.dpInt(context, 5))
        background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(context, 99f)
            setColor(if (selected) BG_CHIP_ACTIVE else BG_CHIP)
            setStroke(UiKit.dpInt(context, 1), if (selected) BG_CHIP_ACTIVE else STROKE_CHIP)
        }
        setOnClickListener { onClick() }
    }

    private fun translateCategory(context: Context, zhName: String): String = when (zhName) {
        "常用" -> GamepadBridge.tr(context, "常用", "よく使う", "Common")
        "方向" -> GamepadBridge.tr(context, "方向", "方向", "Nav")
        "F1-F12" -> "F1-F12"
        "字母数字" -> GamepadBridge.tr(context, "字母数字", "英数字", "Keys")
        else -> GamepadBridge.tr(context, "高级", "拡張", "Extra")
    }
}
