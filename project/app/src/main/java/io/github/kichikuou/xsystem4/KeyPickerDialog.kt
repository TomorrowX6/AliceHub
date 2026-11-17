package io.github.kichikuou.xsystem4

import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

object KeyPickerDialog {

    private fun tr(ctx: Context, zh: String, ja: String, en: String): String =
        GamepadBridge.tr(ctx, zh, ja, en)

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    fun showActionPicker(
        context: Context,
        title: String,
        initialAction: GamepadActionSpec,
        onPicked: (GamepadActionSpec, String) -> Unit
    ) {
        val uiCtx = ContextThemeWrapper(context, R.style.Theme_Launcher)
        val catalog = GamepadBridge.buildKeyActionCatalog()
        var currentCatIdx = 0
        for (i in catalog.indices) {
            if (catalog[i].second.any {
                    it.action.type == initialAction.type &&
                        it.action.code == initialAction.code &&
                        it.action.code2 == initialAction.code2
                }
            ) {
                currentCatIdx = i
                break
            }
        }

        var selectedToggle = initialAction.toggleMode

        val root = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(uiCtx, 16), dp(uiCtx, 12), dp(uiCtx, 16), dp(uiCtx, 8))
        }

        val toggleCheck = CheckBox(uiCtx).apply {
            text = tr(
                context,
                "切换开关模式（点一次保持按住，再点松开）",
                "トグルモード（1回押しでホールド切替）",
                "Toggle Latch Mode (tap to hold/release)"
            )
            isChecked = selectedToggle
            setOnCheckedChangeListener { _, isChecked -> selectedToggle = isChecked }
        }
        root.addView(toggleCheck)

        val tabScroll = HorizontalScrollView(uiCtx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(uiCtx, 8), 0, dp(uiCtx, 8))
        }
        val tabRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        tabScroll.addView(tabRow)
        root.addView(tabScroll)

        val itemsScroll = ScrollView(uiCtx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(uiCtx, 280)
            )
        }
        val itemsList = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
        }
        itemsScroll.addView(itemsList)
        root.addView(itemsScroll)

        var dialog: AlertDialog? = null
        val tabButtons = ArrayList<Button>()

        fun renderCategory(idx: Int) {
            currentCatIdx = idx
            for (i in tabButtons.indices) {
                tabButtons[i].alpha = if (i == idx) 1.0f else 0.55f
            }
            itemsList.removeAllViews()
            val entries = catalog[idx].second
            for (entry in entries) {
                val label = when {
                    AppSettings.resolveLocale(context).language.startsWith("zh") -> entry.labelZh
                    AppSettings.resolveLocale(context).language.startsWith("ja") -> entry.labelJa
                    else -> entry.labelEn
                }
                val isCur = entry.action.type == initialAction.type &&
                    entry.action.code == initialAction.code &&
                    entry.action.code2 == initialAction.code2
                val btn = Button(uiCtx).apply {
                    text = if (isCur) "✓ $label" else label
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(uiCtx, 4)
                    }
                    setOnClickListener {
                        val canToggle = entry.action.type == GamepadBridge.ACTION_VK ||
                            entry.action.type == GamepadBridge.ACTION_MOUSE_BTN ||
                            entry.action.type == GamepadBridge.ACTION_COMBO
                        val finalAct = entry.action.copy(toggleMode = canToggle && selectedToggle)
                        dialog?.dismiss()
                        onPicked(finalAct, entry.shortBadge)
                    }
                }
                itemsList.addView(btn)
            }
        }

        for (i in catalog.indices) {
            val catTitle = catalog[i].first
            val tabBtn = Button(uiCtx).apply {
                text = catTitle
                isAllCaps = false
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(uiCtx, 6)
                }
                setOnClickListener { renderCategory(i) }
            }
            tabButtons.add(tabBtn)
            tabRow.addView(tabBtn)
        }

        renderCategory(currentCatIdx)

        dialog = AlertDialog.Builder(uiCtx)
            .setTitle(title)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showVirtualControlEditor(
        context: Context,
        control: VirtualControlSpec,
        onSave: (VirtualControlSpec) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val uiCtx = ContextThemeWrapper(context, R.style.Theme_Launcher)
        var draft = control

        val scroll = ScrollView(uiCtx)
        val root = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(uiCtx, 18), dp(uiCtx, 12), dp(uiCtx, 18), dp(uiCtx, 8))
        }

        // Visible switch
        val visRow = LinearLayout(uiCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(uiCtx, 4), 0, dp(uiCtx, 6))
        }
        val visLabel = TextView(uiCtx).apply {
            text = tr(context, "在屏幕上显示此控件", "画面に表示", "Show on Screen")
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val visSwitch = Switch(uiCtx).apply {
            isChecked = draft.visible
            setOnCheckedChangeListener { _, checked -> draft = draft.copy(visible = checked) }
        }
        visRow.addView(visLabel)
        visRow.addView(visSwitch)
        root.addView(visRow)

        // Custom label
        val labelTitle = TextView(uiCtx).apply {
            text = tr(context, "按键显示名称", "ボタン表示名", "Display Label")
            setPadding(0, dp(uiCtx, 6), 0, dp(uiCtx, 2))
        }
        val labelInput = EditText(uiCtx).apply {
            setText(draft.label)
            setSingleLine(true)
            isEnabled = draft.kind == VirtualControlKind.BUTTON || draft.kind == VirtualControlKind.STICK
        }
        root.addView(labelTitle)
        root.addView(labelInput)

        // Binding / Stick Mode selector button
        val actionTitle = TextView(uiCtx).apply {
            text = if (draft.kind == VirtualControlKind.BUTTON) {
                tr(context, "绑定按键 / 鼠标动作", "割り当てキー / 動作", "Bound Key / Action")
            } else {
                tr(context, "方向控制模式", "入力モード", "Direction Mode")
            }
            setPadding(0, dp(uiCtx, 10), 0, dp(uiCtx, 4))
        }
        root.addView(actionTitle)

        val actionBtn = Button(uiCtx).apply {
            isAllCaps = false
            fun refreshText() {
                text = if (draft.kind == VirtualControlKind.BUTTON) {
                    GamepadBridge.formatActionLabel(context, draft.action)
                } else {
                    GamepadBridge.stickModeLabel(context, draft.stickMode)
                }
            }
            refreshText()
            setOnClickListener {
                if (draft.kind == VirtualControlKind.BUTTON) {
                    showActionPicker(
                        context,
                        tr(context, "选择目标按键/动作", "キー・動作を選択", "Pick Key / Action"),
                        draft.action
                    ) { newAct, badge ->
                        val prevAutoBadge = labelInput.text.toString().trim()
                        val shouldUpdateLabel = prevAutoBadge.isEmpty() || draft.isCustom
                        draft = draft.copy(action = newAct)
                        if (shouldUpdateLabel) {
                            labelInput.setText(badge)
                        }
                        refreshText()
                    }
                } else {
                    val modes = listOf(
                        GamepadBridge.STICK_MOUSE,
                        GamepadBridge.STICK_ARROWS,
                        GamepadBridge.STICK_WASD,
                        GamepadBridge.STICK_WHEEL,
                        GamepadBridge.STICK_JOYPAD,
                        GamepadBridge.STICK_NONE
                    )
                    val labels = modes.map { GamepadBridge.stickModeLabel(context, it) }.toTypedArray()
                    AlertDialog.Builder(uiCtx)
                        .setTitle(tr(context, "选择方向模式", "モード選択", "Select Mode"))
                        .setItems(labels) { _, which ->
                            draft = draft.copy(stickMode = modes[which])
                            refreshText()
                        }
                        .show()
                }
            }
        }
        root.addView(actionBtn)

        // Shape picker for buttons
        if (draft.kind == VirtualControlKind.BUTTON) {
            val shapeTitle = TextView(uiCtx).apply {
                text = tr(context, "按键外观样式", "ボタン形状", "Button Shape")
                setPadding(0, dp(uiCtx, 10), 0, dp(uiCtx, 4))
            }
            root.addView(shapeTitle)
            val shapeRow = LinearLayout(uiCtx).apply { orientation = LinearLayout.HORIZONTAL }
            val shapes = listOf(
                VirtualButtonShape.CIRCLE to tr(context, "圆形", "丸", "Circle"),
                VirtualButtonShape.PILL to tr(context, "胶囊", "ピル", "Pill"),
                VirtualButtonShape.SQUARE to tr(context, "圆角方框", "角丸", "Square")
            )
            val shapeBtns = ArrayList<Button>()
            fun refreshShapes() {
                for (i in shapes.indices) {
                    shapeBtns[i].alpha = if (draft.shape == shapes[i].first) 1.0f else 0.5f
                }
            }
            for ((sh, lbl) in shapes) {
                val b = Button(uiCtx).apply {
                    text = lbl
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(uiCtx, 4)
                    }
                    setOnClickListener {
                        draft = draft.copy(shape = sh)
                        refreshShapes()
                    }
                }
                shapeBtns.add(b)
                shapeRow.addView(b)
            }
            refreshShapes()
            root.addView(shapeRow)
        }

        // Size slider
        val scalePct = (draft.scale * 100f).toInt().coerceIn(55, 180)
        val scaleTv = TextView(uiCtx).apply {
            text = "${tr(context, "控件大小", "サイズ", "Size")}: $scalePct%"
            setPadding(0, dp(uiCtx, 10), 0, dp(uiCtx, 2))
        }
        val scaleBar = SeekBar(uiCtx).apply {
            max = 180 - 55
            progress = scalePct - 55
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    val pct = 55 + p
                    scaleTv.text = "${tr(context, "控件大小", "サイズ", "Size")}: $pct%"
                    draft = draft.copy(scale = pct / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(scaleTv)
        root.addView(scaleBar)

        // Individual opacity slider
        val opPct = (draft.opacity * 100f).toInt().coerceIn(20, 100)
        val opTv = TextView(uiCtx).apply {
            text = "${tr(context, "独立不透明度", "不透明度", "Opacity")}: $opPct%"
            setPadding(0, dp(uiCtx, 8), 0, dp(uiCtx, 2))
        }
        val opBar = SeekBar(uiCtx).apply {
            max = 80
            progress = opPct - 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    val pct = 20 + p
                    opTv.text = "${tr(context, "独立不透明度", "不透明度", "Opacity")}: $pct%"
                    draft = draft.copy(opacity = pct / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(opTv)
        root.addView(opBar)

        scroll.addView(root)

        val builder = AlertDialog.Builder(uiCtx)
            .setTitle("${tr(context, "编辑控件", "コントロール編集", "Edit Control")} · ${control.label}")
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                val finalLabel = labelInput.text.toString().trim().ifEmpty { draft.label }
                onSave(draft.copy(label = finalLabel))
            }
            .setNegativeButton(R.string.cancel, null)

        if (control.isCustom && onDelete != null) {
            builder.setNeutralButton(tr(context, "删除此按键", "削除", "Delete")) { _, _ ->
                onDelete()
            }
        }
        builder.show()
    }
}
