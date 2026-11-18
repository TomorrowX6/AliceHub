package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.math.abs

class FloatingModifierOverlay(private val activity: Activity) {

    private var rootOverlay: FrameLayout? = null
    private var floatingBtn: ImageView? = null
    private var lastCheatCode: String = ""

    private val isZh: Boolean
        get() = AppSettings.resolveLocale(activity).language.startsWith("zh")
    private val isJa: Boolean
        get() = AppSettings.resolveLocale(activity).language.startsWith("ja")

    private fun tr(zh: String, ja: String, en: String): String = when {
        isZh -> zh
        isJa -> ja
        else -> en
    }

    private fun dp(v: Int): Int =
        (v * activity.resources.displayMetrics.density).toInt()

    private val trackedDialogs = mutableListOf<android.app.Dialog>()

    private fun trackDialog(dlg: android.app.Dialog) {
        trackedDialogs.removeAll { !it.isShowing }
        if (!trackedDialogs.contains(dlg)) {
            trackedDialogs.add(dlg)
        }
    }

    private fun dismissTrackedDialogs() {
        val copy = trackedDialogs.toList()
        trackedDialogs.clear()
        for (dlg in copy) {
            try {
                if (dlg.isShowing) dlg.dismiss()
            } catch (_: Throwable) {
            }
        }
    }

    private fun dialogBuilder(): AlertDialog.Builder =
        object : AlertDialog.Builder(ContextThemeWrapper(activity, R.style.Theme_Launcher)) {
            override fun create(): AlertDialog {
                val dlg = super.create()
                trackDialog(dlg)
                return dlg
            }

            override fun show(): AlertDialog {
                val dlg = super.show()
                trackDialog(dlg)
                return dlg
            }
        }

    fun attach() {
        if (!GameModifierBridge.isFloatingEnabled(activity)) {
            return
        }

        val container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
        }
        rootOverlay = container

        val btn = buildFloatingButton()
        floatingBtn = btn
        container.addView(btn)

        activity.addContentView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun detach() {
        dismissTrackedDialogs()
        GamepadConfigDialog.dismissForContext(activity)
        floatingBtn = null
        rootOverlay = null
    }

    fun syncVisibility() {
        if (GameModifierBridge.isFloatingEnabled(activity)) {
            if (rootOverlay == null) {
                attach()
            } else {
                floatingBtn?.visibility = View.VISIBLE
            }
        } else {
            floatingBtn?.visibility = View.GONE
        }
    }

    private fun buildFloatingButton(): ImageView {
        val sizePx = dp(40)
        val padPx = dp(6)
        val btn = ImageView(activity).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(padPx, padPx, padPx, padPx)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0x88000000.toInt())
                setStroke(dp(1), 0x55FFFFFF)
            }
            alpha = 0.8f
            contentDescription = tr("悬浮窗修改器", "改造", "Modifier")
        }

        val lp = FrameLayout.LayoutParams(
            sizePx,
            sizePx,
            Gravity.TOP or Gravity.START
        ).apply {
            leftMargin = dp(12)
            topMargin = dp(12)
        }
        btn.layoutParams = lp

        var downRawX = 0f
        var downRawY = 0f
        var startLeft = 0
        var startTop = 0
        var dragging = false

        btn.setOnTouchListener { v, event ->
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
                        val parentW = (rootOverlay?.width ?: activity.resources.displayMetrics.widthPixels)
                        val parentH = (rootOverlay?.height ?: activity.resources.displayMetrics.heightPixels)
                        params.leftMargin = (startLeft + dx).coerceIn(0, (parentW - v.width).coerceAtLeast(0))
                        params.topMargin = (startTop + dy).coerceIn(0, (parentH - v.height).coerceAtLeast(0))
                        v.layoutParams = params
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.8f
                    if (!dragging) {
                        showMainModifierDialog()
                    }
                    true
                }
                else -> false
            }
        }
        return btn
    }

    fun openMainMenu() {
        showMainModifierDialog()
    }

    private fun readLiveJson(): JSONObject {
        return try {
            JSONObject(GameModifierBridge.nativeGetLiveStateJson())
        } catch (_: Throwable) {
            JSONObject()
        }
    }

    private fun sendAction(act: JSONObject, toastMsg: String?) {
        try {
            GameModifierBridge.nativeApplyLiveAction(act.toString())
            if (!toastMsg.isNullOrEmpty()) {
                Toast.makeText(activity, toastMsg, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Throwable) {
        }
    }

    private fun triggerDisableStreamerModeInGame(inGame: Boolean) {
        GameModifierBridge.setDisableStreamerModeEnabled(activity, true)
        sendAction(
            JSONObject()
                .put("streamerMode", 0)
                .put("streamerPopup", true),
            if (inGame) null else tr("已关闭主播模式", "配信者モードを解除しました", "Streamer mode disabled")
        )
    }

    private fun showMainModifierDialog() {
        val xsAct = activity as? XSystem4Activity
        xsAct?.setCtrlSkipLatched(false)
        val ctrlEnabled = GameModifierBridge.isCtrlOverlayEnabled(activity)
        val ctrlVisible = ctrlEnabled && (xsAct?.isCtrlOverlayCurrentlyVisible()
            ?: GameModifierBridge.isCtrlOverlayVisible(activity))
        val j = readLiveJson()
        val isDohnaDohna = j.optBoolean("isDohnaDohna", xsAct?.isDohnaDohnaGame() ?: false)
        val isEvenicle = !isDohnaDohna && j.optBoolean("isEvenicle", xsAct?.isEvenicleGame() ?: false)
        val isSengokuRance = !isDohnaDohna && !isEvenicle && j.optBoolean("isSengokuRance", xsAct?.isSengokuRanceGame() ?: false)
        val isRance03 = !isDohnaDohna && !isEvenicle && !isSengokuRance && j.optBoolean("isRance03", xsAct?.isRance03Game() ?: false)
        val isRance08 = !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance03 && j.optBoolean("isRance08", xsAct?.isRance08Game() ?: false)
        val isRance09 = !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance03 && !isRance08 && j.optBoolean("isRance09", xsAct?.isRance09Game() ?: false)
        val isRance10 = !isDohnaDohna && !isEvenicle && !isSengokuRance && !isRance03 && !isRance08 && !isRance09 && j.optBoolean("isRance10", xsAct?.isRance10Game() ?: false)
        val inGame = j.optBoolean("inGame", false)
        val inBattle = j.optBoolean("inBattle", false)
        val savedMask = GameModifierBridge.getSavedLockMask(activity)
        val lockCount = GameModifierBridge.getLiveLockCount(activity)
        val dohnaLockCount = GameModifierBridge.getDohnaLiveLockCount(activity)
        val evenicleLockCount = GameModifierBridge.getEvenicleLiveLockCount(activity)
        val srLockCount = GameModifierBridge.getSrLiveLockCount(activity)
        val r03LockCount = GameModifierBridge.getR03LiveLockCount(activity)
        val r08LockCount = GameModifierBridge.getR08LiveLockCount(activity)
        val r09LockCount = GameModifierBridge.getR09LiveLockCount(activity)
        val streamerLockOff = (savedMask and GameModifierBridge.LOCK_DISABLE_STREAMER_MODE) != 0
        val liveStreamerMode = j.optInt("streamerMode", if (streamerLockOff) 0 else 1)
        val isStreamerDisabled = if (j.has("streamerMode")) (liveStreamerMode == 0) else streamerLockOff

        val titleText = if (isDohnaDohna) {
            if (!inGame) {
                tr("修改器 · 未进入存档", "改造 · 未ロード", "Modifier · Not Loaded")
            } else if (inBattle) {
                val bTurn = j.optInt("battleTurn", 1)
                val eCnt = j.optInt("enemyCount", 0)
                val eHp = j.optInt("enemyTotalHp", 0)
                tr(
                    "修改器 · 战斗T$bTurn · 敌${eCnt}名 · HP:$eHp",
                    "改造 · 戦闘T$bTurn · 敵${eCnt}体 · HP:$eHp",
                    "Modifier · Battle T$bTurn · Enemy HP:$eHp"
                )
            } else {
                val turn = j.optInt("turn", 1)
                val money = j.optInt("money", 0)
                val tp = j.optInt("tp", 0)
                val tpMax = j.optInt("tpMax", 0)
                tr(
                    "修改器 · 第${turn}天 · 资金:$money · TP:$tp/$tpMax",
                    "改造 · ${turn}日目 · 資金:$money · TP:$tp/$tpMax",
                    "Modifier · Day $turn · Money:$money · TP:$tp/$tpMax"
                )
            }
        } else if (isEvenicle) {
            if (!inGame) {
                tr("修改器 · 未进入游戏", "改造 · 未開始", "Modifier · Not In Game")
            } else if (inBattle) {
                val eCnt = j.optInt("enemyCount", 0)
                val eHp = j.optInt("enemyTotalHp", 0)
                tr(
                    "修改器 · 战斗中 · 敌${eCnt}名 · HP:$eHp",
                    "改造 · 戦闘中 · 敵${eCnt}体 · HP:$eHp",
                    "Modifier · Battle · Enemy HP:$eHp"
                )
            } else {
                val money = j.optInt("money", 0)
                val encNow = j.optInt("encountNow", 0)
                val encMax = j.optInt("encountMax", 0)
                tr(
                    "修改器 · 所持金:$money · 遇敌:$encNow/$encMax",
                    "改造 · 所持金:$money · 遭遇:$encNow/$encMax",
                    "Modifier · Gold:$money · Enc:$encNow/$encMax"
                )
            }
        } else if (isSengokuRance) {
            if (!inGame) {
                tr("修改器 · 未进入游戏", "改造 · 未開始", "Modifier · Not In Game")
            } else if (inBattle) {
                val turn = j.optInt("turn", 0)
                val eCnt = j.optInt("enemyCount", 0)
                val eTroops = j.optInt("enemyTotalTroops", 0)
                tr(
                    "修改器 · 合战中 T$turn · 敌${eCnt}名(兵:$eTroops)",
                    "改造 · 合戦 T$turn · 敵${eCnt}隊(兵:$eTroops)",
                    "Modifier · Battle T$turn · Enemy Troops:$eTroops"
                )
            } else {
                val turn = j.optInt("turn", 0)
                val gold = j.optInt("gold", 0)
                val ap = j.optInt("ap", 0)
                val apMax = j.optInt("apMax", 0)
                val sat = j.optInt("satisfaction", 0)
                tr(
                    "修改器 · T$turn · 金:$gold · 行:$ap/$apMax · 满足:$sat",
                    "改造 · T$turn · 金:$gold · 行:$ap/$apMax · 満足:$sat",
                    "Modifier · T$turn · Gold:$gold · AP:$ap/$apMax"
                )
            }
        } else if (isRance03) {
            if (!inGame) {
                tr("修改器 · 未进入游戏", "改造 · 未開始", "Modifier · Not In Game")
            } else {
                val gold = j.optInt("gold", 0)
                val cnt = j.optInt("r03JoinedCount", 0)
                tr(
                    "修改器 · 出击${cnt}人 · 金:$gold",
                    "改造 · 仲間${cnt}人 · 金:$gold",
                    "Modifier · Party:$cnt · Gold:$gold"
                )
            }
        } else if (isRance08) {
            if (!inGame) {
                tr("修改器 · 未进入游戏", "改造 · 未開始", "Modifier · Not In Game")
            } else {
                val gold = j.optInt("gold", 0)
                val fame = j.optInt("fame", 0)
                val mag = j.optInt("magnum", 0)
                val cnt = j.optInt("r08CharaCount", 0)
                tr(
                    "修改器 · 队友${cnt}人 · 金:$gold · 声望:$fame · 魔剑:$mag",
                    "改造 · 仲間${cnt}人 · 金:$gold · 名声:$fame",
                    "Modifier · Party:$cnt · Gold:$gold · Fame:$fame"
                )
            }
        } else if (isRance09) {
            if (!inGame) {
                tr("修改器 · 未进入游戏", "改造 · 未開始", "Modifier · Not In Game")
            } else {
                val gold = j.optInt("gold", 0)
                val sp = j.optInt("seimaPoint", 0)
                val ss = j.optInt("seimaStone", 0)
                val cnt = j.optInt("r09CharaCount", 0)
                tr(
                    "修改器 · 队员${cnt}人 · 金:$gold · 圣魔:$sp/$ss",
                    "改造 · 隊員${cnt}人 · 金:$gold · 聖魔:$sp/$ss",
                    "Modifier · Party:$cnt · Gold:$gold · Seima:$sp/$ss"
                )
            }
        } else if (!isRance10 || !inGame) {
            tr("修改器", "ゲーム改造", "Modifier")
        } else if (inBattle) {
            val ap = j.optInt("battleAp", 0)
            val eHp = j.optInt("enemyHp", 0)
            val pHp = j.optInt("partyHp", 0)
            tr("修改器 · 战斗 AP:$ap · 敌HP:$eHp · 我HP:$pHp", "改造 · 戦闘 AP:$ap · 敵HP:$eHp", "Modifier · Battle AP:$ap")
        } else {
            val turn = j.optInt("turn", 0)
            val food = j.optInt("foodTicket", 0)
            val pHp = j.optInt("partyHp", 0)
            tr("修改器 · 回合$turn · 食券:$food · HP:$pHp", "改造 · T:$turn · 食券:$food · HP:$pHp", "Modifier · Turn:$turn · HP:$pHp")
        }

        val streamerItemLabel = if (isStreamerDisabled) {
            tr("关闭主播模式 · 已关闭", "配信者モード解除 · OFF", "Disable Streamer Mode · OFF")
        } else {
            tr("关闭主播模式", "配信者モード解除", "Disable Streamer Mode")
        }

        val ctrlItemLabel = if (ctrlVisible) {
            tr("Ctrl 快进键 · 已显示", "Ctrl早送り · 表示中", "Ctrl Skip · Shown")
        } else {
            tr("Ctrl 快进键 · 已隐藏", "Ctrl早送り · 非表示", "Ctrl Skip · Hidden")
        }

        val fontPct = GameModifierBridge.getFontScalePercent(activity)
        val sbPct = GameModifierBridge.getScrollbarScalePercent(activity)

        val menuEntries = mutableListOf<Pair<String, () -> Unit>>()
        if (isRance10) {
            menuEntries.add(
                tr("实时锁定 · $lockCount 项…", "リアルタイムロック · $lockCount 項目…", "Live Locks · $lockCount…") to { showLocksDialog() }
            )
        } else if (isDohnaDohna) {
            menuEntries.addAll(
                listOf(
                    tr("实时锁定 · $dohnaLockCount 项…", "リアルタイムロック · $dohnaLockCount 項目…", "Live Locks · $dohnaLockCount…") to { showDohnaLocksDialog() },
                    tr("常用作弊…", "定番チート…", "Common Cheats…") to { showDohnaCommonCheatsDialog() },
                    tr("战斗指令…", "戦闘クイック…", "Battle Actions…") to { showDohnaBattleQuickDialog() },
                    tr("经营与资源…", "経営・資金…", "Economy & Resources…") to { showDohnaEditEconomyDialog() },
                    tr("战斗成员…", "戦闘メンバー…", "Party Members…") to { showDohnaSelectPlayerDialog() },
                    tr("春销人材…", "ハルウリ人材…", "Brothel Workers…") to { showDohnaSelectWorkerDialog() }
                )
            )
        } else if (isEvenicle) {
            menuEntries.addAll(
                listOf(
                    tr("实时锁定 · $evenicleLockCount 项…", "リアルタイムロック · $evenicleLockCount 項目…", "Live Locks · $evenicleLockCount…") to { showEvenicleLocksDialog() },
                    tr("常用作弊…", "定番チート…", "Common Cheats…") to { showEvenicleCommonCheatsDialog() },
                    tr("战斗指令…", "戦闘クイック…", "Battle Actions…") to { showEvenicleBattleQuickDialog() },
                    tr("修改所持金…", "所持金変更…", "Edit Gold…") to { showEvenicleEditMoneyDialog() },
                    tr("角色属性…", "キャラ編集…", "Edit Characters…") to { showEvenicleSelectCharDialog() }
                )
            )
        } else if (isSengokuRance) {
            menuEntries.addAll(
                listOf(
                    tr("实时锁定 · $srLockCount 项…", "リアルタイムロック · $srLockCount 項目…", "Live Locks · $srLockCount…") to { showSrLocksDialog() },
                    tr("常用作弊…", "定番チート…", "Common Cheats…") to { showSrCommonCheatsDialog() },
                    tr("战斗指令…", "戦闘クイック…", "Battle Actions…") to { showSrBattleQuickDialog() },
                    tr("资源与合战数值…", "資源・行動設定…", "Resources & AP…") to { showSrEditResourcesDialog() },
                    tr("武将属性…", "武将編集…", "Edit Generals…") to { showSrSelectCharDialog() }
                )
            )
        } else if (isRance03) {
            menuEntries.addAll(
                listOf(
                    tr("实时锁定 · $r03LockCount 项…", "リアルタイムロック · $r03LockCount 項目…", "Live Locks · $r03LockCount…") to { showR03LocksDialog() },
                    tr("常用作弊…", "定番チート…", "Common Cheats…") to { showR03CommonCheatsDialog() },
                    tr("修改所持金…", "所持金変更…", "Edit Gold…") to { showR03EditGoldDialog() },
                    tr("角色属性…", "キャラ編集…", "Edit Characters…") to { showR03SelectCharDialog() }
                )
            )
        } else if (isRance08) {
            menuEntries.addAll(
                listOf(
                    tr("实时锁定 · $r08LockCount 项…", "リアルタイムロック · $r08LockCount 項目…", "Live Locks · $r08LockCount…") to { showR08LocksDialog() },
                    tr("常用作弊…", "定番チート…", "Common Cheats…") to { showR08CommonCheatsDialog() },
                    tr("金钱与声望…", "所持金・名声…", "Gold & Fame…") to { showR08EditResourcesDialog() },
                    tr("角色属性…", "キャラ編集…", "Edit Characters…") to { showR08SelectCharDialog() }
                )
            )
        } else if (isRance09) {
            menuEntries.addAll(
                listOf(
                    tr("实时锁定 · $r09LockCount 项…", "リアルタイムロック · $r09LockCount 項目…", "Live Locks · $r09LockCount…") to { showR09LocksDialog() },
                    tr("常用作弊…", "定番チート…", "Common Cheats…") to { showR09CommonCheatsDialog() },
                    tr("金钱与圣魔点…", "所持金・聖魔…", "Gold & Seima…") to { showR09EditResourcesDialog() },
                    tr("角色属性…", "キャラ編集…", "Edit Characters…") to { showR09SelectCharDialog() }
                )
            )
        }
        menuEntries.add(
            tr("字体大小 · $fontPct%…", "文字サイズ · $fontPct%…", "Font Size · $fontPct%…") to { showFontScaleDialog() }
        )
        menuEntries.add(
            tr("滚动条 · $sbPct%…", "スクロールバー · $sbPct%…", "Scrollbar · $sbPct%…") to { showScrollbarScaleDialog() }
        )
        if (isRance10) {
            menuEntries.add(streamerItemLabel to { triggerDisableStreamerModeInGame(inGame) })
        }
        menuEntries.add(
            ctrlItemLabel to {
                val nextVisible = !ctrlVisible
                if (!GameModifierBridge.isCtrlOverlayEnabled(activity) && nextVisible) {
                    GameModifierBridge.setCtrlOverlayEnabled(activity, true)
                }
                val applied = if (xsAct != null) {
                    xsAct.setCtrlOverlayVisible(nextVisible)
                    xsAct.isCtrlOverlayCurrentlyVisible()
                } else {
                    GameModifierBridge.setCtrlOverlayVisible(activity, nextVisible)
                    nextVisible
                }
                Toast.makeText(
                    activity,
                    if (applied) tr("已显示 Ctrl 快进键", "Ctrl早送りを表示しました", "Ctrl skip shown")
                    else tr("已隐藏 Ctrl 快进键", "Ctrl早送りを非表示にしました", "Ctrl skip hidden"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        val curPadProfile = xsAct?.getActiveGamepadProfile() ?: GamepadBridge.loadProfile(activity, null)
        val vpadLabel = if (curPadProfile.virtual.enabled) {
            tr("虚拟手柄 · 开", "仮想パッド · ON", "Virtual Pad · ON")
        } else {
            tr("虚拟手柄 · 关", "仮想パッド · OFF", "Virtual Pad · OFF")
        }
        menuEntries.add(
            vpadLabel to {
                val shown = if (xsAct != null) {
                    xsAct.toggleVirtualGamepadEnabled()
                } else {
                    val next = curPadProfile.copy(
                        virtual = curPadProfile.virtual.copy(enabled = !curPadProfile.virtual.enabled)
                    )
                    GamepadBridge.saveProfile(activity, next, null, false)
                    GamepadBridge.syncProfileToNative(next)
                    next.virtual.enabled
                }
                Toast.makeText(
                    activity,
                    if (shown) tr("虚拟手柄已开启", "仮想パッドON", "Virtual Pad ON")
                    else tr("虚拟手柄已关闭", "仮想パッドOFF", "Virtual Pad OFF"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        menuEntries.add(
            tr("手柄与按键…", "パッド設定…", "Gamepad…") to {
                if (xsAct != null) {
                    xsAct.openGamepadConfigDialog()
                } else {
                    GamepadConfigDialog.show(activity, null, onProfileUpdated = { saved ->
                        GamepadBridge.syncProfileToNative(saved)
                    })
                }
            }
        )
        if (isRance10) {
            menuEntries.addAll(
                listOf(
                    tr("作弊码…", "チートコード…", "Cheat Code…") to { showCheatCodeDialog() },
                    tr("战斗指令…", "戦闘クイック…", "Battle Actions…") to { showBattleQuickDialog() },
                    tr("一键强化…", "一括強化…", "Quick Boosts…") to { showOneClickBoostDialog() },
                    tr("资源与回合…", "リソース・ターン…", "Resources & Turn…") to { showEditResourcesDialog(j) },
                    tr("部队与属性…", "部隊・ステータス…", "Party & Stats…") to { showEditPartyDialog(j) },
                    tr("四国与人口…", "四国・人口…", "World & Armies…") to { showEditWorldDialog(j) },
                    tr("卡牌与角色…", "カード・キャラ…", "Cards & Chars…") to { showEditCardsDialog() }
                )
            )
        }
        menuEntries.add(
            tr("隐藏悬浮图标", "アイコンを非表示", "Hide Floating Icon") to {
                floatingBtn?.visibility = View.GONE
                Toast.makeText(activity, tr("已隐藏悬浮图标", "非表示にしました", "Hidden"), Toast.LENGTH_SHORT).show()
            }
        )

        val items = menuEntries.map { it.first }.toTypedArray()

        dialogBuilder()
            .setTitle(titleText)
            .setItems(items) { _, which ->
                menuEntries.getOrNull(which)?.second?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLocksDialog() {
        val lockDefs = listOf(
            GameModifierBridge.LOCK_PARTY_MAX_HP to tr("锁定我方满生命", "味方HP最大ロック", "Lock Party Max HP"),
            GameModifierBridge.LOCK_INFINITE_AP to tr("锁定无限AP", "AP 99ロック", "Lock Infinite AP"),
            GameModifierBridge.LOCK_BATTLE_COMBO to tr("锁定999连击加成", "999コンボロック", "Lock 999 Combo"),
            GameModifierBridge.LOCK_BATTLE_ROUND_1 to tr("锁定战斗回合为1", "戦闘ラウンド1固定", "Lock Round to 1"),
            GameModifierBridge.LOCK_ONE_HIT_KILL to tr("敌方1血秒杀", "敵HP1・一撃撃破", "One-Hit Kill"),
            GameModifierBridge.LOCK_ENEMY_ZERO_ATK to tr("敌方0攻击0防御", "敵の攻防0化", "Enemy 0 ATK/DEF"),
            GameModifierBridge.LOCK_LEADER_BOOST_CRIT to tr("全员蓄力必暴连击", "全員ブースト・必殺", "All Leaders Boost & Crit"),
            GameModifierBridge.LOCK_INFINITE_SKILL_USE to tr("技能无限使用次数", "スキル回数無限", "Infinite Skill Uses"),
            GameModifierBridge.LOCK_INFINITE_ZIPPO to tr("无限悔棋打火机", "やり直しジッポ無限", "Infinite Retry Zippo"),
            GameModifierBridge.LOCK_MAX_RESOURCES to tr("资源不低于999", "チケット・勲章999維持", "Keep Resources >= 999"),
            GameModifierBridge.LOCK_TREASURE_100 to tr("宝箱100%掉落", "宝箱100%ドロップ", "100% Treasure Drop")
        )
        var mask = GameModifierBridge.getSavedLockMask(activity) and GameModifierBridge.LOCK_DISABLE_STREAMER_MODE.inv()
        val labels = lockDefs.map { it.second }.toTypedArray()
        val checked = BooleanArray(lockDefs.size) { idx -> (mask and lockDefs[idx].first) != 0 }

        dialogBuilder()
            .setTitle(tr("实时锁定", "リアルタイムロック", "Live Locks"))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val bit = lockDefs[which].first
                mask = if (isChecked) (mask or bit) else (mask and bit.inv())
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                GameModifierBridge.setLiveLocksOnlyMask(activity, mask)
                Toast.makeText(activity, tr("已更新锁定项", "ロックを更新しました", "Locks updated"), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(tr("全部关闭", "全解除", "Clear All")) { _, _ ->
                GameModifierBridge.setLiveLocksOnlyMask(activity, 0)
                Toast.makeText(activity, tr("已关闭全部锁定", "全解除しました", "All locks cleared"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCheatCodeDialog(prefill: String = lastCheatCode) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(6))
        }
        val et = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = tr("如 whosyourdaddy / 打败哈尼王 / 餐券10", "例: whosyourdaddy", "e.g. whosyourdaddy")
            setSingleLine(true)
            setText(prefill)
            setSelection(text.length)
        }
        container.addView(et)

        dialogBuilder()
            .setTitle(tr("作弊码", "チートコード", "Cheat Code"))
            .setView(container)
            .setPositiveButton(tr("执行", "実行", "Apply")) { _, _ ->
                val code = et.text.toString().trim().trim('"', '“', '”')
                if (code.isEmpty()) {
                    Toast.makeText(
                        activity,
                        tr("请输入作弊码", "コードを入力してください", "Please enter a cheat code"),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    lastCheatCode = code
                    if (code == "关闭主播模式" || code == "灌注Eins陈谢谢喵" || code == "关注Eins陈谢谢喵") {
                        triggerDisableStreamerModeInGame(readLiveJson().optBoolean("inGame", false))
                    } else if (code == "开启主播模式") {
                        GameModifierBridge.setDisableStreamerModeEnabled(activity, false)
                        sendAction(
                            JSONObject().put("streamerMode", 1),
                            tr("已开启主播模式", "配信者モードをONにしました", "Streamer mode enabled")
                        )
                    } else {
                        sendAction(
                            JSONObject().put("cheatCode", code),
                            null
                        )
                    }
                }
            }
            .setNeutralButton(tr("常用作弊码…", "定型コード…", "Presets…")) { _, _ ->
                showCheatCodePresetsDialog()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCheatCodePresetsDialog() {
        val presets = listOf(
            "灌注Eins陈谢谢喵" to tr("关闭主播模式 · 灌注Eins陈谢谢喵", "配信者モード解除 · 灌注Eins陈谢谢喵", "Disable Streamer Mode · 灌注Eins陈谢谢喵"),
            "界徐盛" to tr("解禁全部作弊码限制 · 界徐盛", "全チート制限解除 · 界徐盛", "Unlock All Cheat Restrictions · 界徐盛"),
            "whosyourdaddy" to tr("击杀当前敌人 · whosyourdaddy", "敵を即撃破 · whosyourdaddy", "Kill Enemy · whosyourdaddy"),
            "血量全满" to tr("战斗中我方血量全满", "戦闘中の味方HP全回復", "Full Party HP in Battle"),
            "重开" to tr("重新开始当前战斗", "現在の戦闘をやり直す", "Restart Battle"),
            "打败哈尼王" to tr("开启兰斯世界模式 · 打败哈尼王", "ランスワールド解放", "Unlock Rance World · 打败哈尼王"),
            "111" to tr("遗物图鉴全展示 · 111", "遺物図鑑全表示 · 111", "Show All Relics · 111"),
            "panzer" to tr("获得游戏管理者卡牌 · panzer", "ゲーム管理者獲得 · panzer", "Get Game Admin Card · panzer"),
            "餐券10" to tr("增加10张餐券 · 餐券10", "食券+10 · 餐券10", "Add 10 Food Tickets · 餐券10"),
            "部队点数up10" to tr("增加10点部队点数 · 部队点数up10", "部隊P+10 · 部队点数up10", "Add 10 Party Points · 部队点数up10"),
            "exp道具" to tr("获得1个EXP道具 · exp道具", "EXPアイテム獲得", "Get 1 EXP Item · exp道具"),
            "难度up4" to tr("难度+4 · 难度up4", "難易度+4 · 难度up4", "Difficulty +4 · 难度up4"),
            "4000flag200" to tr("等级上限额外+200 · 4000flag200", "Lv上限+200 · 4000flag200", "Level Cap +200 · 4000flag200"),
            "1505flag1" to tr("开启装备解封模式 · 1505flag1", "装備解放モードON · 1505flag1", "Unlock Equipment Mode · 1505flag1"),
            "1522flag1" to tr("重置任务技能状态 · 1522flag1", "クエストスキル回数リセット", "Reset Quest Skills · 1522flag1"),
            "2222flag0" to tr("清空敌方盾与复生 · 2222flag0", "敵の盾・蘇生解除 · 2222flag0", "Clear Enemy Shield/Revive · 2222flag0"),
            "2233flag0" to tr("清空敌方自动恢复次数 · 2233flag0", "敵の自動回復解除 · 2233flag0", "Clear Enemy Auto-Heal · 2233flag0"),
            "Lv42 兰斯卡牌追加" to tr("追加指定卡牌模板 · Lv42 兰斯卡牌追加", "指定カード追加テンプレート", "Add Card Template · Lv42 兰斯卡牌追加"),
            "技能识别" to tr("识别正中间角色技能编号", "中央キャラのスキルID確認", "Identify Center Leader Skills"),
            "装备识别" to tr("识别当前武器与防具代码", "現在装備のID確認", "Identify Current Equipment IDs"),
            "梗概关闭" to tr("关闭梗概模式", "ダイジェストモードOFF", "Disable Summary Mode")
        )
        val labels = presets.map { it.second }.toTypedArray()
        dialogBuilder()
            .setTitle(tr("选择常用作弊码", "定型チートコード選択", "Cheat Code Presets"))
            .setItems(labels) { _, which ->
                val chosen = presets[which].first
                lastCheatCode = chosen
                showCheatCodeDialog(chosen)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                showCheatCodeDialog()
            }
            .show()
    }

    private fun showBattleQuickDialog() {
        val items = arrayOf(
            tr("敌方HP设为1", "敵HPを1にする", "Set Enemy HP = 1"),
            tr("废除敌方攻击与防御", "敵の攻防を0にする", "Set Enemy ATK/DEF = 0"),
            tr("我方生命回满", "味方HP全回復", "Refill Party HP"),
            tr("AP设为99 + 连击999 + 回合1", "AP99＋999コンボ＋R1", "AP 99 + Combo 999 + Round 1"),
            tr("全员蓄力必暴 + 重置技能次数", "全員ブースト＋スキル回数回復", "Boost Leaders & Reset Skills"),
            tr("宝箱100%掉落", "宝箱100%ドロップ化", "100% Treasure Drop")
        )
        dialogBuilder()
            .setTitle(tr("战斗指令", "戦闘クイック", "Battle Actions"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendAction(JSONObject().put("killEnemy", true), tr("敌方HP已设为1", "敵HPを1にしました", "Enemy HP = 1"))
                    1 -> sendAction(JSONObject().put("weakenEnemy", true), tr("敌方攻防已清零", "敵攻防を0にしました", "Enemy weakened"))
                    2 -> sendAction(JSONObject().put("refillPartyHp", true), tr("我方生命已回满", "HP全回復しました", "HP refilled"))
                    3 -> sendAction(
                        JSONObject().put("battleAp", 99).put("battleCombo", 999).put("battleRound", 1),
                        tr("已设AP=99、连击=999、回合=1", "AP99・999コンボに設定", "AP=99, Combo=999, Round=1")
                    )
                    4 -> sendAction(
                        JSONObject().put("cureAndBoostLeaders", true),
                        tr("全员已蓄力必暴并重置技能", "全員強化・スキル回復完了", "Leaders boosted")
                    )
                    5 -> sendAction(
                        JSONObject().apply {
                            put("perFindTreasure", 999)
                            put("treasureNotGetCount", 99)
                            put("rareLuck", 999)
                            put("itemStar", 99)
                            put("partyIsFixStatus", 1)
                        },
                        tr("已开启宝箱100%掉落", "宝箱100%ドロップを適用", "100% Treasure Drop applied")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showOneClickBoostDialog() {
        val items = arrayOf(
            tr("一键满资源与经验装备", "全リソース・経験値・装備★最大", "Max Resources, EXP & Item Star"),
            tr("一键宝箱100%掉落", "宝箱100%ドロップ化", "100% Treasure Drop"),
            tr("一键部队满血与全属性+999%", "部隊HP99万＋全ステータス+999%", "Max Party HP & +999% Multipliers"),
            tr("一键全卡牌99叠卡 + 全角色5星", "全カード99枚＋全キャラ★5", "All Cards x99 & All Chars 5★"),
            tr("一键四国满兵力 + 人类零伤亡", "四国兵力最大＋人類被害ゼロ", "Max Armies & Zero Casualties"),
            tr("一键关闭主播模式", "配信者モード解除", "Disable Streamer Mode"),
            tr("打开MOD内置作弊器", "MOD内蔵チートを開く", "Open MOD Cheat Console")
        )
        dialogBuilder()
            .setTitle(tr("一键强化", "一括強化", "Quick Boosts"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendAction(
                        JSONObject().apply {
                            put("difficulty", 0)
                            put("foodTicket", 999)
                            put("honor", 999)
                            put("friendPoint", 999)
                            put("totalExp", 99999999)
                            put("partyRank", 999)
                            put("treasureNotGetCount", 99)
                            put("itemStar", 99)
                            put("isUsedHannyZippo", 0)
                            put("hannyZippo", 99)
                            put("partyBonusMaxPoint", 99)
                        },
                        tr("已生效：满资源与满经验", "全リソース最大を適用", "Max resources applied")
                    )
                    1 -> sendAction(
                        JSONObject().apply {
                            put("perFindTreasure", 999)
                            put("treasureNotGetCount", 99)
                            put("rareLuck", 999)
                            put("itemStar", 99)
                            put("partyIsFixStatus", 1)
                        },
                        tr("已生效：宝箱100%掉落", "宝箱100%ドロップを適用", "100% Treasure Drop applied")
                    )
                    2 -> sendAction(
                        JSONObject().apply {
                            put("partyHp", 999999)
                            put("partyMaxHp", 999999)
                            put("partyAvailCount", 9)
                            put("partyIsFixStatus", 1)
                            put("partyAtk", 99999)
                            put("allStatusPer", 999)
                        },
                        tr("已生效：部队满血与全属性+999%", "部隊強化を適用", "Party boosted")
                    )
                    3 -> sendAction(
                        JSONObject().apply {
                            put("setCardCount", 99)
                            put("cardAtkBonus", 999)
                            put("cardHpBonus", 999)
                            put("setCharStar", 5)
                            put("setCharExp", 999999)
                        },
                        tr("已生效：全卡牌99叠卡与全员5星", "全カード99枚・★5を適用", "Cards & chars maxed")
                    )
                    4 -> sendAction(
                        JSONObject().apply {
                            put("humanCount", 350000)
                            put("humanDead", 0)
                            put("humanAlivePer", 100)
                            put("humanDeadPer", 0)
                            put("curseTurn", 0)
                            put("rareLuck", 999)
                            put("seaPlayerArmy", 99999)
                            put("seaEnemyArmy", 0)
                            put("leazasState", 0)
                            put("helmanState", 0)
                            put("zethState", 0)
                            put("freeCitiesState", 0)
                            put("leazasPArmy", 99999)
                            put("helmanPArmy", 99999)
                            put("zethPArmy", 99999)
                            put("freeCitiesPArmy", 99999)
                            put("leazasEArmy", 100)
                            put("helmanEArmy", 100)
                            put("zethEArmy", 100)
                            put("freeCitiesEArmy", 100)
                            put("subRemnant", 99999)
                            put("subDwarf", 99999)
                            put("subGeneral", 99999)
                            put("subHanny", 99999)
                        },
                        tr("已生效：四国满兵力与人类零伤亡", "四国兵力最大を適用", "World & armies maxed")
                    )
                    5 -> triggerDisableStreamerModeInGame(readLiveJson().optBoolean("inGame", false))
                    6 -> sendAction(
                        JSONObject().put("triggerKey0", true),
                        tr("已发送按键0", "キー0を送信しました", "Sent Key 0")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditResourcesDialog(j: JSONObject) {
        val fields = listOf(
            Triple("turn", tr("当前回合", "現在ターン", "Turn"), j.optInt("turn", 1)),
            Triple("difficulty", tr("难度 0~10", "難易度 0~10", "Difficulty 0~10"), j.optInt("difficulty", 1)),
            Triple("foodTicket", tr("食券数量", "食券", "Food Tickets"), j.optInt("foodTicket", 0)),
            Triple("honor", tr("勋章数量", "勲章", "Medals"), j.optInt("honor", 0)),
            Triple("friendPoint", tr("友情点数", "友好ポイント", "Friend Points"), j.optInt("friendPoint", 0)),
            Triple("totalExp", tr("累计经验值", "累計経験値", "Total EXP"), j.optInt("totalExp", 0)),
            Triple("partyRank", tr("部队等级", "部隊ランク", "Party Rank"), j.optInt("partyRank", 1)),
            Triple("itemStar", tr("装备星级加成", "装備★加算", "Item Star"), j.optInt("itemStar", 0)),
            Triple("hannyZippo", tr("悔棋打火机次数", "ジッポ回数", "Zippo Count"), j.optInt("hannyZippo", 0))
        )
        showNumberInputDialog(tr("修改资源与回合", "リソース・ターン", "Edit Resources"), fields)
    }

    private fun showEditPartyDialog(j: JSONObject) {
        val fields = listOf(
            Triple("partyHp", tr("部队当前生命", "現在HP", "Party HP"), j.optInt("partyHp", 1000)),
            Triple("partyMaxHp", tr("部队最大生命", "最大HP", "Party Max HP"), j.optInt("partyMaxHp", 1000)),
            Triple("partyAvailCount", tr("每回合出击人数", "出撃枠数", "Deploy Slots"), j.optInt("partyAvailCount", 4)),
            Triple("partyIsFixStatus", tr("锁定属性不被重算 0/1", "ステータス固定 0/1", "Lock Stats 0/1"), j.optInt("partyIsFixStatus", 0)),
            Triple("partyAtk", tr("部队总攻击力", "総攻撃力", "Total ATK"), j.optInt("partyAtk", 500)),
            Triple("atkPerPhys", tr("物理攻击加成%", "物理攻撃%", "Phys ATK %"), j.optInt("atkPerPhys", 0)),
            Triple("atkPerMag", tr("魔法攻击加成%", "魔法攻撃%", "Mag ATK %"), j.optInt("atkPerMag", 0)),
            Triple("defPerPhys", tr("物理防御加成%", "物理防御%", "Phys DEF %"), j.optInt("defPerPhys", 0)),
            Triple("defPerMag", tr("魔法防御加成%", "魔法防御%", "Mag DEF %"), j.optInt("defPerMag", 0)),
            Triple("perCrit", tr("暴击率加成%", "クリティカル%", "Crit %"), j.optInt("perCrit", 0)),
            Triple("perMulti", tr("连击率加成%", "連撃率%", "Multi-Hit %"), j.optInt("perMulti", 0)),
            Triple("perExpUp", tr("经验加成%", "経験値%", "EXP %"), j.optInt("perExpUp", 0)),
            Triple("perFindTreasure", tr("宝箱掉落率%", "宝箱発見率%", "Treasure Drop %"), j.optInt("perFindTreasure", 0))
        )
        showNumberInputDialog(tr("修改部队与属性加成", "部隊・ステータス", "Edit Party & Stats"), fields)
    }

    private fun showEditWorldDialog(j: JSONObject) {
        val fields = listOf(
            Triple("humanCount", tr("人类存活人口", "人類生存人口", "Human Alive"), j.optInt("humanCount", 300000)),
            Triple("humanDead", tr("人类死亡人口", "人類死亡人口", "Human Dead"), j.optInt("humanDead", 0)),
            Triple("seaPlayerArmy", tr("兰斯船团兵力", "ランス船団兵力", "Sea Fleet Army"), j.optInt("seaPlayerArmy", 0)),
            Triple("leazasPArmy", tr("利萨斯我军兵力", "リーザス味方兵力", "Leazas Player Army"), j.optInt("leazasPArmy", 300)),
            Triple("helmanPArmy", tr("赫尔曼我军兵力", "ヘルマン味方兵力", "Helman Player Army"), j.optInt("helmanPArmy", 300)),
            Triple("zethPArmy", tr("赛斯我军兵力", "ゼス味方兵力", "Zeth Player Army"), j.optInt("zethPArmy", 300)),
            Triple("freeCitiesPArmy", tr("自由都市我军兵力", "自由都市味方兵力", "Free Cities Player Army"), j.optInt("freeCitiesPArmy", 300)),
            Triple("leazasEArmy", tr("利萨斯魔军兵力", "リーザス魔軍兵力", "Leazas Enemy Army"), j.optInt("leazasEArmy", 500)),
            Triple("helmanEArmy", tr("赫尔曼魔军兵力", "ヘルマン魔軍兵力", "Helman Enemy Army"), j.optInt("helmanEArmy", 500)),
            Triple("zethEArmy", tr("赛斯魔军兵力", "ゼス魔軍兵力", "Zeth Enemy Army"), j.optInt("zethEArmy", 500)),
            Triple("freeCitiesEArmy", tr("自由都市魔军兵力", "自由都市魔軍兵力", "Free Cities Enemy Army"), j.optInt("freeCitiesEArmy", 500))
        )
        showNumberInputDialog(tr("修改四国兵力与人口", "四国・人口", "Edit World & Armies"), fields)
    }

    private fun showEditCardsDialog() {
        val fields = listOf(
            Triple("setCardCount", tr("全卡牌叠卡设为N张", "全カード枚数指定", "Set All Cards Count"), 99),
            Triple("cardAtkBonus", tr("全卡牌攻击加成", "全カード攻撃加算", "All Cards ATK Bonus"), 999),
            Triple("cardHpBonus", tr("全卡牌生命加成", "全カードHP加算", "All Cards HP Bonus"), 999),
            Triple("setCharStar", tr("全角色星级 1~5", "全キャラ★ 1~5", "All Chars Star 1~5"), 5),
            Triple("setCharExp", tr("全角色经验值", "全キャラ経験値", "All Chars EXP"), 999999)
        )
        showNumberInputDialog(tr("修改卡牌与角色", "カード・キャラ", "Edit Cards & Chars"), fields)
    }

    private fun dohnaPhaseName(phase: Int): String = when (phase) {
        0 -> tr("回合初", "ターン開始前", "Turn Start")
        1 -> tr("清晨", "朝", "Morning")
        2 -> tr("基地日常", "拠点日常", "Base")
        3 -> tr("行动选择", "行動選択", "Action Select")
        4 -> tr("春销营业", "ハルウリ", "Brothel Work")
        5 -> tr("迷宫抢夺", "ヒトカリ", "Dungeon")
        6 -> tr("特殊顾客", "特別顧客", "Special Guest")
        7 -> tr("回合结算", "ターン終了", "Turn End")
        else -> tr("进行中", "進行中", "Active")
    }

    private fun dohnaWorkerRankName(rank: Int): String =
        arrayOf("D", "C", "C+", "B", "B+", "A", "A+", "S", "S+", "SS").getOrElse(rank) { "D" }

    private fun dohnaWorkerRarityName(rarity: Int): String =
        arrayOf("N", "R", "SR", "SSR", "SSSR", "UR").getOrElse(rarity) { "N" }

    private fun sendDohnaInGameAction(act: JSONObject, toastMsg: String, requireBattle: Boolean = false) {
        val live = readLiveJson()
        if (!live.optBoolean("inGame", false)) {
            Toast.makeText(
                activity,
                tr("请先进入游戏存档", "ロード後に実行してください", "Load a game first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (requireBattle && !live.optBoolean("inBattle", false)) {
            Toast.makeText(
                activity,
                tr("仅限战斗中使用", "戦闘中のみ使用可能", "Battle only"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        sendAction(act, toastMsg)
    }

    private fun showBitmaskLocksDialog(
        lockDefs: List<Pair<Int, String>>,
        initialMask: Int,
        applyMask: (Int) -> Unit
    ) {
        var mask = initialMask
        val labels = lockDefs.map { it.second }.toTypedArray()
        val checked = BooleanArray(lockDefs.size) { idx -> (mask and lockDefs[idx].first) != 0 }

        dialogBuilder()
            .setTitle(tr("实时锁定", "リアルタイムロック", "Live Locks"))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val bit = lockDefs[which].first
                mask = if (isChecked) (mask or bit) else (mask and bit.inv())
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                applyMask(mask)
                Toast.makeText(activity, tr("已更新锁定项", "ロックを更新しました", "Locks updated"), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(tr("全部关闭", "全解除", "Clear All")) { _, _ ->
                applyMask(0)
                Toast.makeText(activity, tr("已关闭全部锁定", "全解除しました", "All locks cleared"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDohnaLocksDialog() {
        val lockDefs = listOf(
            GameModifierBridge.LOCK_DD_ALLY_MAX_HP to tr("战斗锁定我方全员满HP", "味方全員HP最大ロック", "Lock Party Max HP"),
            GameModifierBridge.LOCK_DD_ALLY_MAX_MP to tr("战斗锁定我方全员满MP", "味方全員MP最大ロック", "Lock Party Max MP"),
            GameModifierBridge.LOCK_DD_ALLY_MAX_VP to tr("战斗锁定我方全员满怒气VP", "味方全員VP最大ロック", "Lock Party Max VP"),
            GameModifierBridge.LOCK_DD_ONE_HIT_KILL to tr("战斗敌方全员1血秒杀", "敵全員HP1・一撃撃破", "Enemy 1 HP One-Hit Kill"),
            GameModifierBridge.LOCK_DD_INFINITE_MONEY to tr("无限资金 · 保底99999999", "資金無限・99999999維持", "Infinite Money"),
            GameModifierBridge.LOCK_DD_INFINITE_TP to tr("无限调教点 · TP不减", "調教ポイント無限", "Infinite Training Points"),
            GameModifierBridge.LOCK_DD_WORKER_BEST_COND to tr("人材满精神满避孕药且不崩溃不怀孕", "人材メンタル・ピル最大維持", "Keep Workers Best Condition"),
            GameModifierBridge.LOCK_DD_WORK_FREEZE_TIMER to tr("春销营业配对倒计时锁定不减", "ハルウリ制限時間ロック", "Freeze Brothel Match Timer")
        )
        showBitmaskLocksDialog(
            lockDefs,
            GameModifierBridge.getSavedDohnaLockMask(activity)
        ) { GameModifierBridge.setSavedDohnaLockMask(activity, it) }
    }

    private fun showEvenicleLocksDialog() {
        val lockDefs = listOf(
            GameModifierBridge.LOCK_EV_NO_ENCOUNT to tr("禁止随机遇敌 · 安全步数锁定满值", "エンカウント無効・エンカウントゲージ最大維持", "Disable Random Encounters"),
            GameModifierBridge.LOCK_EV_ALLY_FULL_HP to tr("我方全员满 HP 锁定", "味方全員HP最大ロック", "Lock Party Full HP"),
            GameModifierBridge.LOCK_EV_INF_SKILL_P to tr("无限技能点 · SkillP 至少 99", "スキルP無限・最低99維持", "Infinite Skill Points"),
            GameModifierBridge.LOCK_EV_INF_BP to tr("无限 BP · 战斗 BP 满 5 格", "BP無限・5維持", "Infinite BP"),
            GameModifierBridge.LOCK_EV_ONE_HIT_KILL to tr("战斗敌方全员 1 血秒杀", "敵全員HP1・一撃撃破", "Enemy 1 HP One-Hit Kill"),
            GameModifierBridge.LOCK_EV_INFINITE_MONEY to tr("无限所持金 · 保底 9999999", "所持金無限・9999999維持", "Infinite Gold")
        )
        showBitmaskLocksDialog(
            lockDefs,
            GameModifierBridge.getSavedEvenicleLockMask(activity)
        ) { GameModifierBridge.setSavedEvenicleLockMask(activity, it) }
    }

    private fun showEvenicleCommonCheatsDialog() {
        val items = arrayOf(
            tr("所持金增加 10 万", "所持金 +100,000", "Add 100,000 Gold"),
            tr("所持金增加 100 万", "所持金 +1,000,000", "Add 1,000,000 Gold"),
            tr("所持金拉满 99999999", "所持金カンスト 99999999", "Max Gold 99,999,999"),
            tr("出战角色全员满 HP 与满 5 BP", "出撃キャラ全員HP・BP全回復", "Full Heal & BP 5 Party"),
            tr("出战角色技能点全部补满 999", "出撃キャラ スキルP 999", "Set Party Skill Points to 999"),
            tr("出战角色一键暴走强化 9999 HP 与 999 四维", "出撃キャラ最強強化 HP9999・能力999", "Super Boost Active Party"),
            tr("立即触发下次移动遇敌", "次の移動で即エンカウント", "Trigger Instant Encounter"),
            tr("清空当前遇敌危险度至安全状态", "エンカウント危険度を安全に戻す", "Clear Encounter Gauge")
        )
        dialogBuilder()
            .setTitle(tr("常用作弊", "定番チート", "Common Cheats"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatAddMoney", 100000),
                        tr("所持金已增加 10 万", "所持金 +100,000 を適用", "Added 100,000 Gold")
                    )
                    1 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatAddMoney", 1000000),
                        tr("所持金已增加 100 万", "所持金 +1,000,000 を適用", "Added 1,000,000 Gold")
                    )
                    2 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatMaxMoney", true),
                        tr("所持金已拉满 99999999", "所持金を最大にしました", "Max Gold applied")
                    )
                    3 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatPartyFullHeal", true),
                        tr("出战角色已回满 HP 与 BP", "HP・BPを全回復しました", "Party healed & BP filled")
                    )
                    4 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatPartySkillP999", true),
                        tr("出战角色技能点已设为 999", "スキルPを999にしました", "Party SkillP set to 999")
                    )
                    5 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatPartyBoost", true),
                        tr("出战角色一键强化完成", "出撃キャラを強化しました", "Party boosted")
                    )
                    6 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatTriggerEncounter", true),
                        tr("下次大地图移动将立即遇敌", "次の移動でエンカウントします", "Instant encounter armed")
                    )
                    7 -> sendDohnaInGameAction(
                        JSONObject().put("evCheatClearEncounter", true),
                        tr("遇敌危险度已重置为安全状态", "エンカウントをリセットしました", "Encounter gauge cleared")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEvenicleBattleQuickDialog() {
        val items = arrayOf(
            tr("敌方全员 HP 设为 1", "敵全員のHPを1にする", "Set All Enemies HP to 1"),
            tr("我方全员满 HP 与满 5 BP", "味方全員HP・BP全回復", "Full Heal & BP 5 Allies")
        )
        dialogBuilder()
            .setTitle(tr("战斗指令", "戦闘クイック", "Battle Actions"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(
                        JSONObject().put("evBattle1HpEnemies", true),
                        tr("敌方全员 HP 已设为 1", "敵全員のHPを1にしました", "Enemies HP set to 1"),
                        requireBattle = true
                    )
                    1 -> sendDohnaInGameAction(
                        JSONObject().put("evBattleHealAllies", true),
                        tr("我方全员已回满 HP 与 BP", "味方全員を全回復しました", "Allies healed"),
                        requireBattle = true
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEvenicleEditMoneyDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val encNow = j.optInt("encountNow", 0)
        val encMax = j.optInt("encountMax", 0)
        showDohnaFormDialog(
            title = tr("修改所持金", "所持金変更", "Edit Gold"),
            summary = tr("当前遇敌安全步数：$encNow / $encMax", "現在のエンカウント残値：$encNow / $encMax", "Encounter bar: $encNow / $encMax"),
            fields = listOf(Triple("evMoney", tr("所持金 GOLD", "所持金 GOLD", "Gold"), j.optInt("money", 0))),
            prefillValues = true
        ) { vals ->
            vals["evMoney"]?.let { m ->
                sendDohnaInGameAction(JSONObject().put("evMoney", m), tr("所持金已修改", "所持金を変更しました", "Gold updated"))
            }
        }
    }

    private fun showSrLocksDialog() {
        val lockDefs = listOf(
            GameModifierBridge.LOCK_SR_INF_GOLD to tr("无限金钱 · 保底 999999", "金無限・999999維持", "Infinite Gold"),
            GameModifierBridge.LOCK_SR_INF_AP to tr("无限行动次数 · 回合行动点满值", "行動回数無限", "Infinite Actions (AP)"),
            GameModifierBridge.LOCK_SR_MAX_SAT to tr("最大满足度 · 保底 999", "満足度999維持", "Max Satisfaction"),
            GameModifierBridge.LOCK_SR_ALLY_FULL_HP to tr("我方合战满兵力与满技数", "味方兵数・技回数維持", "Party Full Troops & Skills"),
            GameModifierBridge.LOCK_SR_ONE_HIT_KILL to tr("敌方 1 兵秒杀 · 合战敌兵设为 1", "敵兵数1・一撃撃破", "Enemy 1 Troop"),
            GameModifierBridge.LOCK_SR_UNLIM_ACTION to tr("我方全员无限行动 · 清除已行动", "味方全員無限行動", "Unlimited Char Actions")
        )
        showBitmaskLocksDialog(
            lockDefs,
            GameModifierBridge.getSavedSrLockMask(activity)
        ) { GameModifierBridge.setSavedSrLockMask(activity, it) }
    }

    private fun showSrCommonCheatsDialog() {
        val items = arrayOf(
            tr("金钱增加 10 万", "金 +100,000", "Add 100,000 Gold"),
            tr("金钱增加 100 万", "金 +1,000,000", "Add 1,000,000 Gold"),
            tr("金钱设为 999999", "金 999999 設定", "Set Gold 999,999"),
            tr("当前回合行动次数补满", "行動回数全回復", "Refill Turn Actions (AP)"),
            tr("行动上限设为 9 并回满", "最大行動回数 9", "Set Max AP = 9 & Refill"),
            tr("满足度拉满 999", "満足度 999", "Max Satisfaction 999"),
            tr("个人兵数上限设为 9999", "個人兵数限界 9999", "Set Troop Limit 9999"),
            tr("重置全阵营角色本回合行动状态", "味方行動済み解除", "Reset All Actions"),
            tr("我方全员兵力与技数补满", "味方全員兵数・技回復", "Heal All Party Troops & Skills"),
            tr("一键全军超强化 (9999兵/99四维/99好感)", "全武将超強化 (兵9999/全能力99)", "Super Boost All Party Generals")
        )
        dialogBuilder()
            .setTitle(tr("常用作弊", "定番チート", "Common Cheats"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(JSONObject().put("srAddGold", 100000), tr("金钱已增加 10 万", "金 +100,000 を適用", "Added 100,000 Gold"))
                    1 -> sendDohnaInGameAction(JSONObject().put("srAddGold", 1000000), tr("金钱已增加 100 万", "金 +1,000,000 を適用", "Added 1,000,000 Gold"))
                    2 -> sendDohnaInGameAction(JSONObject().put("srGold", 999999), tr("金钱已设为 999999", "金を999999に設定", "Gold set to 999,999"))
                    3 -> sendDohnaInGameAction(JSONObject().put("srRefillAp", true), tr("行动次数已补满", "行動回数を回復しました", "Actions refilled"))
                    4 -> sendDohnaInGameAction(JSONObject().put("srApMax", 9).put("srAp", 9), tr("行动上限已设为 9", "最大行動回数を9に設定", "Max AP set to 9"))
                    5 -> sendDohnaInGameAction(JSONObject().put("srSatisfaction", 999), tr("满足度已设为 999", "満足度を999にしました", "Satisfaction set to 999"))
                    6 -> sendDohnaInGameAction(JSONObject().put("srTroopLimit", 9999), tr("兵数上限已设为 9999", "兵数限界を9999に設定", "Troop limit set to 9999"))
                    7 -> sendDohnaInGameAction(JSONObject().put("srResetAllActions", true), tr("已重置全员行动状态", "行動済みを解除しました", "All actions reset"))
                    8 -> sendDohnaInGameAction(JSONObject().put("srHealAllTroops", true), tr("已回满全体兵力与技数", "全員の兵数・技を回復しました", "Party troops healed"))
                    9 -> sendDohnaInGameAction(JSONObject().put("srBoostAllParty", true), tr("全军超强化已完成", "全武将を強化しました", "All party generals boosted"))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSrBattleQuickDialog() {
        val items = arrayOf(
            tr("合战敌方全员兵力设为 1", "敵全員の兵数を1にする", "Set All Enemies Troops to 1"),
            tr("合战我方全员满兵力与技数", "味方全員の兵数・技を回復", "Heal All Ally Troops & Skills"),
            tr("重置我方全员行动状态", "味方全員を行動可能にする", "Reset All Character Actions")
        )
        dialogBuilder()
            .setTitle(tr("战斗指令", "戦闘クイック", "Battle Actions"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(
                        JSONObject().put("srBattle1HpEnemies", true),
                        tr("敌方全员兵力已设为 1", "敵兵数を1にしました", "Enemies set to 1 troop"),
                        requireBattle = true
                    )
                    1 -> sendDohnaInGameAction(
                        JSONObject().put("srHealAllTroops", true),
                        tr("我方已回满兵力与技数", "味方兵数・技を回復しました", "Allies healed"),
                        requireBattle = true
                    )
                    2 -> sendDohnaInGameAction(
                        JSONObject().put("srResetAllActions", true),
                        tr("已重置全员行动状态", "行動状態をリセットしました", "Actions reset")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSrEditResourcesDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val fields = listOf(
            Triple("srGold", tr("金钱 GOLD", "金 GOLD", "Gold"), j.optInt("gold", 0)),
            Triple("srAp", tr("当前行动次数 AP", "現在行動回数", "Current AP"), j.optInt("ap", 0)),
            Triple("srApMax", tr("最大行动次数", "最大行動回数", "Max AP"), j.optInt("apMax", 0)),
            Triple("srSatisfaction", tr("满足度", "満足度", "Satisfaction"), j.optInt("satisfaction", 0)),
            Triple("srTroopLimit", tr("个人兵数上限", "個人兵数限界", "Troop Limit"), j.optInt("troopLimit", 0))
        )
        showDohnaFormDialog(
            title = tr("资源与合战数值", "資源・行動設定", "Resources & AP"),
            summary = tr("当前第 ${j.optInt("turn", 0)} 回合", "現在 ${j.optInt("turn", 0)} ターン", "Turn ${j.optInt("turn", 0)}"),
            fields = fields,
            prefillValues = true
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val act = JSONObject()
            for ((k, v) in vals) act.put(k, v)
            sendDohnaInGameAction(act, tr("资源数值已生效", "リソースを更新しました", "Resources updated"))
        }
    }

    private fun showSrSelectCharDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val arr = j.optJSONArray("srCharacters")
        if (arr == null || arr.length() == 0) {
            Toast.makeText(activity, tr("暂无武将数据", "武将データなし", "No generals"), Toast.LENGTH_SHORT).show()
            return
        }
        val options = mutableListOf<Triple<Int, JSONObject?, String>>()
        val defFaction = arr.optJSONObject(0)?.optInt("factionIdx", 1) ?: 1
        options.add(Triple(-1, null, tr("批量：全阵营在籍武将", "一括：自勢力全武将", "Batch: All Party Generals")))
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val cid = c.optInt("charIdx", -1)
            val name = c.optString("name", "#$cid")
            val trp = c.optInt("troops", 0)
            val tmax = c.optInt("troopsMax", 0)
            val atk = c.optInt("atk", 0)
            val def = c.optInt("def", 0)
            val intel = c.optInt("intel", 0)
            val spd = c.optInt("spd", 0)
            options.add(Triple(cid, c, "$name · 兵 $trp/$tmax · 攻$atk 防$def 智$intel 速$spd"))
        }
        dialogBuilder()
            .setTitle(tr("武将属性", "武将編集", "Generals"))
            .setItems(options.map { it.third }.toTypedArray()) { _, which ->
                val chosen = options.getOrNull(which) ?: return@setItems
                val fIdx = chosen.second?.optInt("factionIdx", defFaction) ?: defFaction
                showSrEditCharDialog(fIdx, chosen.first, chosen.second, chosen.third)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSrEditCharDialog(factionIdx: Int, charIdx: Int, c: JSONObject?, title: String) {
        val single = c != null
        val fields = listOf(
            Triple("troops", tr("当前兵力", "現在兵数", "Troops"), c?.optInt("troops", 100) ?: 9999),
            Triple("troopsMax", tr("最大兵力", "最大兵数", "Max Troops"), c?.optInt("troopsMax", 100) ?: 9999),
            Triple("atk", tr("攻击", "攻撃", "ATK"), c?.optInt("atk", 5) ?: 99),
            Triple("def", tr("防御", "防御", "DEF"), c?.optInt("def", 5) ?: 99),
            Triple("intel", tr("智力", "知力", "INT"), c?.optInt("intel", 5) ?: 99),
            Triple("spd", tr("速度", "速度", "SPD"), c?.optInt("spd", 5) ?: 99),
            Triple("love", tr("好感度", "好感度", "Affection"), c?.optInt("love", 0) ?: 99),
            Triple("wazaNow", tr("当前技数", "現在技回数", "Skill Uses"), c?.optInt("wazaNow", 0) ?: 9),
            Triple("wazaMax", tr("最大技数", "最大技回数", "Max Skill Uses"), c?.optInt("wazaMax", 3) ?: 9),
            Triple("resetAction", tr("重置已行动 (1=清零)", "行動済リセット (1=解除)", "Reset Action (1=Yes)"), 0)
        )
        showDohnaFormDialog(
            title = title,
            summary = if (single) {
                val done = if (c!!.optInt("actionDone", 0) != 0) tr("已行动", "行動済", "Acted") else tr("可行动", "未行動", "Ready")
                tr("当前状态：$done", "現在状態：$done", "Status: $done")
            } else {
                tr("留空的项保持原值不变", "空欄の項目は変更しません", "Leave blank to keep value")
            },
            fields = fields,
            prefillValues = single,
            blankByDefaultKeys = setOf("resetAction"),
            neutralActionLabel = tr("一键强化", "最大強化", "Max Boost"),
            onNeutralAction = {
                val boost = JSONObject().apply {
                    put("factionIdx", factionIdx)
                    put("charIdx", charIdx)
                    put("troops", 9999)
                    put("troopsMax", 9999)
                    put("atk", 99)
                    put("def", 99)
                    put("intel", 99)
                    put("spd", 99)
                    put("love", 99)
                    put("resetAction", 1)
                }
                sendDohnaInGameAction(
                    JSONObject().put("srCharEdit", boost),
                    tr("武将强化已生效", "武将を強化しました", "General boosted")
                )
            }
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val ce = JSONObject().put("factionIdx", factionIdx).put("charIdx", charIdx)
            for ((k, v) in vals) {
                if (k == "resetAction" && v == 0) continue
                ce.put(k, v)
            }
            sendDohnaInGameAction(
                JSONObject().put("srCharEdit", ce),
                tr("武将属性已更新", "武将ステータスを更新しました", "General updated")
            )
        }
    }

    private fun showR03LocksDialog() {
        val lockDefs = listOf(
            GameModifierBridge.LOCK_R03_HP to tr("无限体力 · 我方全员 HP 不减", "味方HP減らない", "Party Infinite HP"),
            GameModifierBridge.LOCK_R03_NO_CD to tr("技能零冷却 · 无回合 CD 与全队 CD", "スキルCT0", "Zero Skill Cooldown"),
            GameModifierBridge.LOCK_R03_INF_ITEMS to tr("无限道具 · 消耗与露营道具不减", "アイテム減らない", "Infinite Items"),
            GameModifierBridge.LOCK_R03_DROP_100 to tr("战利品 100% 掉落", "ドロップ率100%", "100% Item Drop"),
            GameModifierBridge.LOCK_R03_ONE_HIT_KILL to tr("敌方秒杀 · 战斗一击必杀", "敵一撃撃破", "One-Hit Kill")
        )
        showBitmaskLocksDialog(
            lockDefs,
            GameModifierBridge.getSavedR03LockMask(activity)
        ) { GameModifierBridge.setSavedR03LockMask(activity, it) }
    }

    private fun showR03CommonCheatsDialog() {
        val items = arrayOf(
            tr("所持金增加 1 万", "所持金 +10,000", "Add 10,000 Gold"),
            tr("所持金增加 10 万", "所持金 +100,000", "Add 100,000 Gold"),
            tr("所持金设为 9999999", "所持金 9999999", "Set Gold 9,999,999"),
            tr("全队回满 HP 并清零技能冷却", "全員HP全回復・CT0", "Full Heal & Clear Cooldowns"),
            tr("已入队全员经验值拉满 (满级)", "経験値カンスト・最大レベル化", "Max All Party EXP"),
            tr("已入队全员超强化 · HP9999 Lv99 全能力999", "全員超強化 · HP9999 能力999", "Super Boost All Party")
        )
        dialogBuilder()
            .setTitle(tr("常用作弊", "定番チート", "Common Cheats"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(JSONObject().put("r03AddGold", 10000), tr("所持金已增加 1 万", "所持金 +10,000 を適用", "Added 10,000 Gold"))
                    1 -> sendDohnaInGameAction(JSONObject().put("r03AddGold", 100000), tr("所持金已增加 10 万", "所持金 +100,000 を適用", "Added 100,000 Gold"))
                    2 -> sendDohnaInGameAction(JSONObject().put("r03Gold", 9999999), tr("所持金已设为 9999999", "所持金を9999999に設定", "Gold set to 9,999,999"))
                    3 -> sendDohnaInGameAction(JSONObject().put("r03HealAllParty", true), tr("已回满 HP 并清零冷却", "全回復・CT0を適用", "Party healed & CDs cleared"))
                    4 -> sendDohnaInGameAction(JSONObject().put("r03MaxAllExp", true), tr("全员经验值已拉满", "経験値をカンストしました", "Max EXP applied"))
                    5 -> sendDohnaInGameAction(
                        JSONObject().put(
                            "r03CharEdit",
                            JSONObject().put("target", -1).put("hp", 9999).put("hpMax", 9999)
                                .put("lv", 99).put("str", 999).put("mind", 999).put("dex", 999)
                        ),
                        tr("已入队全员超强化完成", "全員超強化を適用しました", "Party super boosted")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showR03EditGoldDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val fields = listOf(Triple("r03Gold", tr("所持金 GOLD", "所持金 GOLD", "Gold"), j.optInt("gold", 0)))
        showDohnaFormDialog(
            title = tr("修改所持金", "所持金変更", "Edit Gold"),
            summary = tr("当前全队冷却：${j.optInt("partyCoolTime", 0)}", "全体CT：${j.optInt("partyCoolTime", 0)}", "Party CD: ${j.optInt("partyCoolTime", 0)}"),
            fields = fields,
            prefillValues = true
        ) { vals ->
            vals["r03Gold"]?.let { g ->
                sendDohnaInGameAction(JSONObject().put("r03Gold", g), tr("所持金已修改", "所持金を変更しました", "Gold updated"))
            }
        }
    }

    private fun showR03SelectCharDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val arr = j.optJSONArray("r03Characters")
        if (arr == null || arr.length() == 0) {
            Toast.makeText(activity, tr("暂无已入队角色", "加入キャラなし", "No joined characters"), Toast.LENGTH_SHORT).show()
            return
        }
        val options = mutableListOf<Triple<Int, JSONObject?, String>>()
        options.add(Triple(-1, null, tr("批量：全部已入队成员", "一括：加入済み全員", "Batch: Joined Party")))
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val idx = c.optInt("index", -1)
            val name = c.optString("name", "#$idx")
            val lv = c.optInt("lv", 1)
            val lvMax = c.optInt("lvMax", 1)
            val hp = c.optInt("hp", 0)
            val hpMax = c.optInt("hpMax", 0)
            val str = c.optInt("str", 0)
            val mind = c.optInt("mind", 0)
            val dex = c.optInt("dex", 0)
            options.add(Triple(idx, c, "$name · Lv$lv/$lvMax · HP $hp/$hpMax · 攻$str 精$mind 敏$dex"))
        }
        dialogBuilder()
            .setTitle(tr("角色属性", "キャラ編集", "Characters"))
            .setItems(options.map { it.third }.toTypedArray()) { _, which ->
                val chosen = options.getOrNull(which) ?: return@setItems
                showR03EditCharDialog(chosen.first, chosen.second, chosen.third)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showR03EditCharDialog(target: Int, c: JSONObject?, title: String) {
        val single = c != null
        val fields = listOf(
            Triple("hp", tr("当前 HP", "現在 HP", "HP"), c?.optInt("hp", 100) ?: 9999),
            Triple("hpMax", tr("最大 HP", "最大 HP", "Max HP"), c?.optInt("hpMax", 100) ?: 9999),
            Triple("lv", tr("等级 Lv", "レベル Lv", "Level"), c?.optInt("lv", 1) ?: 99),
            Triple("exp", tr("经验值 EXP", "経験値 EXP", "EXP"), c?.optInt("exp", 0) ?: 9999999),
            Triple("str", tr("物理 Str", "物理 Str", "STR"), c?.optInt("str", 10) ?: 999),
            Triple("mind", tr("魔法 Mind", "魔法 Mind", "MIND"), c?.optInt("mind", 10) ?: 999),
            Triple("dex", tr("灵巧 Dex", "器用 Dex", "DEX"), c?.optInt("dex", 10) ?: 999)
        )
        showDohnaFormDialog(
            title = title,
            summary = if (single) {
                tr("等级上限：Lv${c!!.optInt("lvMax", 40)}", "レベル上限：Lv${c.optInt("lvMax", 40)}", "Level Cap: Lv${c.optInt("lvMax", 40)}")
            } else {
                tr("留空的项保持原值不变", "空欄の項目は変更しません", "Leave blank to keep value")
            },
            fields = fields,
            prefillValues = single,
            neutralActionLabel = tr("一键强化", "最大強化", "Max Boost"),
            onNeutralAction = {
                val boost = JSONObject().apply {
                    put("target", target)
                    put("hp", 9999)
                    put("hpMax", 9999)
                    put("lv", 99)
                    put("str", 999)
                    put("mind", 999)
                    put("dex", 999)
                }
                sendDohnaInGameAction(
                    JSONObject().put("r03CharEdit", boost),
                    tr("角色强化已生效", "キャラを強化しました", "Character boosted")
                )
            }
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val ce = JSONObject().put("target", target)
            for ((k, v) in vals) ce.put(k, v)
            sendDohnaInGameAction(
                JSONObject().put("r03CharEdit", ce),
                tr("角色属性已更新", "ステータスを更新しました", "Character updated")
            )
        }
    }

    private fun showR08LocksDialog() {
        val lockDefs = listOf(
            GameModifierBridge.LOCK_R08_ALLY_FULL_HP to tr("无限体力 · 我方全员满 HP", "味方HP最大維持", "Party Full HP"),
            GameModifierBridge.LOCK_R08_ONE_HIT_KILL to tr("敌方秒杀 · 一击必杀", "敵一撃撃破", "One-Hit Kill"),
            GameModifierBridge.LOCK_R08_INF_SKILLS to tr("无限技能 · 使用次数不减", "スキル回数無限", "Infinite Skills"),
            GameModifierBridge.LOCK_R08_MAGNUM_MAX to tr("魔剑蓄力 · 万能威力 MAX", "マグナム力最大", "Magnum Force Max"),
            GameModifierBridge.LOCK_R08_INF_GOLD to tr("无限金钱 · 保底 9999999", "所持金無限", "Infinite Gold")
        )
        showBitmaskLocksDialog(
            lockDefs,
            GameModifierBridge.getSavedR08LockMask(activity)
        ) { GameModifierBridge.setSavedR08LockMask(activity, it) }
    }

    private fun showR08CommonCheatsDialog() {
        val items = arrayOf(
            tr("所持金增加 1 万", "所持金 +10,000", "Add 10,000 Gold"),
            tr("所持金增加 10 万", "所持金 +100,000", "Add 100,000 Gold"),
            tr("所持金设为 9999999", "所持金 9999999", "Set Gold 9,999,999"),
            tr("全队回满 HP 并刷新技能次数", "全員HP・スキル回数全回復", "Heal Party & Refresh Skills"),
            tr("魔剑蓄力设为当前上限 MAX", "マグナム力を最大に設定", "Max Magnum Force"),
            tr("已入队全员超强化 · HP9999 Lv99 全能力999", "全員超強化 · HP9999 能力999", "Super Boost All Party")
        )
        dialogBuilder()
            .setTitle(tr("常用作弊", "定番チート", "Common Cheats"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(JSONObject().put("r08AddGold", 10000), tr("所持金已增加 1 万", "所持金 +10,000 を適用", "Added 10,000 Gold"))
                    1 -> sendDohnaInGameAction(JSONObject().put("r08AddGold", 100000), tr("所持金已增加 10 万", "所持金 +100,000 を適用", "Added 100,000 Gold"))
                    2 -> sendDohnaInGameAction(JSONObject().put("r08Gold", 9999999), tr("所持金已设为 9999999", "所持金を9999999に設定", "Gold set to 9,999,999"))
                    3 -> sendDohnaInGameAction(JSONObject().put("r08HealAllParty", true), tr("全队已回满并刷新技能", "全回復・スキル回復を適用", "Party healed & refreshed"))
                    4 -> sendDohnaInGameAction(JSONObject().put("r08MagnumMax", true), tr("魔剑蓄力已设为最大", "マグナム力を最大にしました", "Magnum Force maxed"))
                    5 -> sendDohnaInGameAction(
                        JSONObject().put(
                            "r08CharEdit",
                            JSONObject().put("target", -1).put("hp", 9999).put("hpMax", 9999)
                                .put("lv", 99).put("atk", 999).put("mag", 999)
                                .put("def", 999).put("res", 999).put("spd", 999)
                                .put("hit", 999).put("eva", 99).put("crt", 100)
                                .put("skillPts", 99).put("fullHeal", true)
                        ),
                        tr("已入队全员超强化完成", "全員超強化を適用しました", "Party super boosted")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showR08EditResourcesDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val fields = listOf(
            Triple("r08Gold", tr("所持金 GOLD", "所持金 GOLD", "Gold"), j.optInt("gold", 0)),
            Triple("r08Fame", tr("当前声望 Fame", "名声", "Fame"), j.optInt("fame", 0)),
            Triple("r08FameMax", tr("声望上限 FameMax", "名声上限", "Fame Max"), j.optInt("fameMax", 0))
        )
        showDohnaFormDialog(
            title = tr("金钱与声望", "所持金・名声", "Gold & Fame"),
            summary = tr("当前魔剑：${j.optInt("magnum", 0)}/${j.optInt("magnumMax", 0)}", "マグナム：${j.optInt("magnum", 0)}/${j.optInt("magnumMax", 0)}", "Magnum: ${j.optInt("magnum", 0)}/${j.optInt("magnumMax", 0)}"),
            fields = fields,
            prefillValues = true
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val act = JSONObject()
            for ((k, v) in vals) act.put(k, v)
            sendDohnaInGameAction(act, tr("资源修改已生效", "資源を更新しました", "Resources updated"))
        }
    }

    private fun showR08SelectCharDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val arr = j.optJSONArray("r08Characters")
        if (arr == null || arr.length() == 0) {
            Toast.makeText(activity, tr("暂无我方角色数据", "味方キャラデータなし", "No party characters"), Toast.LENGTH_SHORT).show()
            return
        }
        val options = mutableListOf<Triple<Int, JSONObject?, String>>()
        options.add(Triple(-1, null, tr("批量：全部我方角色", "一括：味方全員", "Batch: All Allies")))
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val idx = c.optInt("index", -1)
            val name = c.optString("name", "#$idx")
            val lv = c.optInt("lv", 1)
            val hp = c.optInt("hp", 0)
            val hpMax = c.optInt("hpMax", 0)
            val atk = c.optInt("atk", 0)
            val mag = c.optInt("mag", 0)
            val tag = if (c.optBoolean("inParty", false)) tr("出战", "出撃", "Party") else tr("待命", "待機", "Wait")
            options.add(Triple(idx, c, "[$tag] $name · Lv$lv · HP $hp/$hpMax · 物$atk 魔$mag"))
        }
        dialogBuilder()
            .setTitle(tr("角色属性", "キャラ編集", "Characters"))
            .setItems(options.map { it.third }.toTypedArray()) { _, which ->
                val chosen = options.getOrNull(which) ?: return@setItems
                showR08EditCharDialog(chosen.first, chosen.second, chosen.third)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showR08EditCharDialog(target: Int, c: JSONObject?, title: String) {
        val single = c != null
        val fields = listOf(
            Triple("hp", tr("当前 HP", "現在 HP", "HP"), c?.optInt("hp", 100) ?: 9999),
            Triple("hpMax", tr("最大 HP", "最大 HP", "Max HP"), c?.optInt("hpMax", 100) ?: 9999),
            Triple("lv", tr("等级 Lv", "レベル Lv", "Level"), c?.optInt("lv", 1) ?: 99),
            Triple("lvMax", tr("等级上限 LvMax", "レベル上限", "Level Cap"), c?.optInt("lvMax", 50) ?: 99),
            Triple("exp", tr("经验值 EXP", "経験値 EXP", "EXP"), c?.optInt("exp", 0) ?: 999999),
            Triple("atk", tr("攻击力 ATK", "攻撃力", "ATK"), c?.optInt("atk", 10) ?: 999),
            Triple("mag", tr("魔法力 MAG", "魔力", "MAG"), c?.optInt("mag", 0) ?: 999),
            Triple("def", tr("物理防御 DEF", "防御力", "DEF"), c?.optInt("def", 10) ?: 999),
            Triple("res", tr("魔法防御 RES", "魔防", "RES"), c?.optInt("res", 10) ?: 999),
            Triple("spd", tr("速度 SPD", "速度", "SPD"), c?.optInt("spd", 10) ?: 999),
            Triple("hit", tr("命中率 HIT", "命中", "HIT"), c?.optInt("hit", 100) ?: 999),
            Triple("eva", tr("回避率 EVA", "回避", "EVA"), c?.optInt("eva", 0) ?: 99),
            Triple("crt", tr("必杀率 CRT", "必殺", "CRT"), c?.optInt("crt", 0) ?: 100),
            Triple("skillPts", tr("技能点 SkillP", "スキルP", "Skill Pts"), c?.optInt("skillPts", 0) ?: 99)
        )
        showDohnaFormDialog(
            title = title,
            summary = if (single) {
                tr("角色编号 #${c!!.optInt("charaNo", 0)}", "キャラNo.${c.optInt("charaNo", 0)}", "Chara #${c.optInt("charaNo", 0)}")
            } else {
                tr("留空的项保持原值不变", "空欄の項目は変更しません", "Leave blank to keep value")
            },
            fields = fields,
            prefillValues = single,
            neutralActionLabel = tr("一键强化并回满", "最大強化・全回復", "Max Boost & Heal"),
            onNeutralAction = {
                val boost = JSONObject().apply {
                    put("target", target)
                    put("hp", 9999)
                    put("hpMax", 9999)
                    put("lv", 99)
                    put("atk", 999)
                    put("mag", 999)
                    put("def", 999)
                    put("res", 999)
                    put("spd", 999)
                    put("hit", 999)
                    put("eva", 99)
                    put("crt", 100)
                    put("skillPts", 99)
                    put("fullHeal", true)
                }
                sendDohnaInGameAction(
                    JSONObject().put("r08CharEdit", boost),
                    tr("角色强化已生效", "キャラを強化しました", "Character boosted")
                )
            }
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val ce = JSONObject().put("target", target)
            for ((k, v) in vals) ce.put(k, v)
            sendDohnaInGameAction(
                JSONObject().put("r08CharEdit", ce),
                tr("角色属性已更新", "ステータスを更新しました", "Character updated")
            )
        }
    }

    private fun showR09LocksDialog() {
        val lockDefs = listOf(
            GameModifierBridge.LOCK_R09_ALLY_GOD to tr("无限防御 · 我方全员无伤满血", "味方無敵・HP最大", "Party God Mode & Full HP"),
            GameModifierBridge.LOCK_R09_ONE_HIT_KILL to tr("敌方秒杀 · 一击必杀", "敵一撃撃破", "One-Hit Kill"),
            GameModifierBridge.LOCK_R09_INF_ACTION to tr("无限行动 · 回合不消耗行动", "無限行動", "Infinite Actions"),
            GameModifierBridge.LOCK_R09_DROP_100 to tr("金币必掉 · 战利金币高额掉落", "所持金ドロップ最大", "Max Gold Drop"),
            GameModifierBridge.LOCK_R09_INF_RESOURCES to tr("无限资源 · 金币与圣魔点保底", "資源無限維持", "Infinite Gold & Seima")
        )
        showBitmaskLocksDialog(
            lockDefs,
            GameModifierBridge.getSavedR09LockMask(activity)
        ) { GameModifierBridge.setSavedR09LockMask(activity, it) }
    }

    private fun showR09CommonCheatsDialog() {
        val items = arrayOf(
            tr("军资金增加 1 万", "軍資金 +10,000", "Add 10,000 Gold"),
            tr("军资金设为 999999", "軍資金 999999", "Set Gold 999,999"),
            tr("圣魔点 9999 / 圣魔石 999", "聖魔P 9999 / 聖魔石 999", "Seima Pt 9999 & Stone 999"),
            tr("全军回满 HP 与全部护盾", "全員HP・防壁全回復", "Heal Party HP & Shields"),
            tr("全员技能强化全部拉满 MAX", "全員スキル強化 MAX", "Max All Skill Upgrades"),
            tr("全员超强化 · HP9999 破坏力9999 移动12", "全員超強化 · HP9999 移動12", "Super Boost All Characters")
        )
        dialogBuilder()
            .setTitle(tr("常用作弊", "定番チート", "Common Cheats"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(JSONObject().put("r09AddGold", 10000), tr("军资金已增加 1 万", "軍資金 +10,000 を適用", "Added 10,000 Gold"))
                    1 -> sendDohnaInGameAction(JSONObject().put("r09Gold", 999999), tr("军资金已设为 999999", "軍資金を999999に設定", "Gold set to 999,999"))
                    2 -> sendDohnaInGameAction(
                        JSONObject().put("r09SeimaPoint", 9999).put("r09SeimaStone", 999),
                        tr("圣魔点与圣魔石已拉满", "聖魔ポイント・聖魔石を設定しました", "Seima maxed")
                    )
                    3 -> sendDohnaInGameAction(JSONObject().put("r09HealAllParty", true), tr("全军生命与护盾已回满", "全員全回復しました", "Party healed"))
                    4 -> sendDohnaInGameAction(JSONObject().put("r09MaxAllSkillUp", true), tr("全员技能强化已拉满", "全員のスキル強化を最大にしました", "All skill upgrades maxed"))
                    5 -> sendDohnaInGameAction(
                        JSONObject().put(
                            "r09CharEdit",
                            JSONObject().put("target", -1).put("hp", 9999).put("hpMax", 9999)
                                .put("atkMin", 5000).put("atkMax", 9999).put("magic", 9999)
                                .put("resist", 999).put("move", 12).put("critical", 100)
                                .put("speed", 999).put("evadeMax", 999).put("fendMax", 999)
                                .put("armoredMax", 999).put("fullHeal", true)
                        ),
                        tr("全员超强化完成", "全員超強化を適用しました", "Party super boosted")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showR09EditResourcesDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val fields = listOf(
            Triple("r09Gold", tr("军资金 GOLD", "軍資金 GOLD", "Gold"), j.optInt("gold", 0)),
            Triple("r09SeimaPoint", tr("圣魔点数 SeimaPt", "聖魔ポイント", "Seima Points"), j.optInt("seimaPoint", 0)),
            Triple("r09SeimaStone", tr("圣魔石 SeimaStone", "聖魔石", "Seima Stones"), j.optInt("seimaStone", 0))
        )
        showDohnaFormDialog(
            title = tr("金钱与圣魔点", "所持金・聖魔", "Gold & Seima"),
            summary = tr("已加载角色 ${j.optInt("r09CharaCount", 0)} 名", "ロード済みキャラ ${j.optInt("r09CharaCount", 0)} 人", "Characters: ${j.optInt("r09CharaCount", 0)}"),
            fields = fields,
            prefillValues = true
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val act = JSONObject()
            for ((k, v) in vals) act.put(k, v)
            sendDohnaInGameAction(act, tr("资源修改已生效", "資源を更新しました", "Resources updated"))
        }
    }

    private fun showR09SelectCharDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val arr = j.optJSONArray("r09Characters")
        if (arr == null || arr.length() == 0) {
            Toast.makeText(activity, tr("暂无角色数据", "キャラデータなし", "No character data"), Toast.LENGTH_SHORT).show()
            return
        }
        val options = mutableListOf<Triple<Int, JSONObject?, String>>()
        options.add(Triple(-1, null, tr("批量：全部角色", "一括：全キャラ", "Batch: All Characters")))
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val idx = c.optInt("index", -1)
            val name = c.optString("name", "#$idx")
            val hp = c.optInt("hp", 0)
            val hpMax = c.optInt("hpMax", 0)
            val atkMin = c.optInt("atkMin", 0)
            val atkMax = c.optInt("atkMax", 0)
            val move = c.optInt("move", 0)
            options.add(Triple(idx, c, "$name · HP $hp/$hpMax · 破坏$atkMin~$atkMax · 移$move"))
        }
        dialogBuilder()
            .setTitle(tr("角色属性", "キャラ編集", "Characters"))
            .setItems(options.map { it.third }.toTypedArray()) { _, which ->
                val chosen = options.getOrNull(which) ?: return@setItems
                showR09EditCharDialog(chosen.first, chosen.second, chosen.third)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showR09EditCharDialog(target: Int, c: JSONObject?, title: String) {
        val single = c != null
        val fields = listOf(
            Triple("hp", tr("当前 HP", "現在 HP", "HP"), c?.optInt("hp", 100) ?: 9999),
            Triple("hpMax", tr("最大 HP", "最大 HP", "Max HP"), c?.optInt("hpMax", 100) ?: 9999),
            Triple("atkMin", tr("最小破坏力", "最小破壊力", "Min ATK"), c?.optInt("atkMin", 50) ?: 5000),
            Triple("atkMax", tr("最大破坏力", "最大破壊力", "Max ATK"), c?.optInt("atkMax", 100) ?: 9999),
            Triple("magic", tr("魔法力", "魔法力", "Magic"), c?.optInt("magic", 0) ?: 9999),
            Triple("resist", tr("异常耐性", "状態耐性", "Resist"), c?.optInt("resist", 0) ?: 999),
            Triple("move", tr("移动力", "移動力", "Move"), c?.optInt("move", 4) ?: 12),
            Triple("range", tr("射程", "射程", "Range"), c?.optInt("range", 1) ?: 8),
            Triple("critical", tr("必杀率", "クリティカル", "Critical"), c?.optInt("critical", 0) ?: 100),
            Triple("speed", tr("行动速度", "行動速度", "Speed"), c?.optInt("speed", 10) ?: 999),
            Triple("evadeMax", tr("回避计数上限", "見切り上限", "Evade Max"), c?.optInt("evadeMax", 0) ?: 999),
            Triple("fendMax", tr("招架计数上限", "弾き上限", "Fend Max"), c?.optInt("fendMax", 0) ?: 999),
            Triple("armoredMax", tr("装甲计数上限", "装甲上限", "Armored Max"), c?.optInt("armoredMax", 0) ?: 999),
            Triple("skillBonus", tr("技能强化点", "スキル強化P", "Skill Bonus"), c?.optInt("skillBonus", 0) ?: 9999)
        )
        showDohnaFormDialog(
            title = title,
            summary = if (single) {
                tr("角色ID #${c!!.optInt("charaId", 0)}", "キャラID #${c.optInt("charaId", 0)}", "CharaID #${c.optInt("charaId", 0)}")
            } else {
                tr("留空的项保持原值不变", "空欄の項目は変更しません", "Leave blank to keep value")
            },
            fields = fields,
            prefillValues = single,
            neutralActionLabel = tr("一键强化并回满", "最大強化・全回復", "Max Boost & Heal"),
            onNeutralAction = {
                val boost = JSONObject().apply {
                    put("target", target)
                    put("hp", 9999)
                    put("hpMax", 9999)
                    put("atkMin", 5000)
                    put("atkMax", 9999)
                    put("magic", 9999)
                    put("resist", 999)
                    put("move", 12)
                    put("critical", 100)
                    put("speed", 999)
                    put("evadeMax", 999)
                    put("fendMax", 999)
                    put("armoredMax", 999)
                    put("fullHeal", true)
                }
                sendDohnaInGameAction(
                    JSONObject().put("r09CharEdit", boost),
                    tr("角色强化已生效", "キャラを強化しました", "Character boosted")
                )
            }
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val ce = JSONObject().put("target", target)
            for ((k, v) in vals) ce.put(k, v)
            sendDohnaInGameAction(
                JSONObject().put("r09CharEdit", ce),
                tr("角色属性已更新", "ステータスを更新しました", "Character updated")
            )
        }
    }

    private fun showEvenicleSelectCharDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(activity, tr("请先进入游戏", "先にゲームを開始してください", "Start game first"), Toast.LENGTH_SHORT).show()
            return
        }
        val charsArr = j.optJSONArray("evCharacters") ?: org.json.JSONArray()
        val entries = mutableListOf<Triple<Int, JSONObject?, String>>()
        entries.add(Triple(-1, null, tr("所有出战角色 批量修改", "出撃キャラ全員 一括編集", "All Active Party Batch Edit")))
        entries.add(Triple(-2, null, tr("全部角色 批量修改", "全キャラ 一括編集", "All Characters Batch Edit")))
        for (i in 0 until charsArr.length()) {
            val c = charsArr.optJSONObject(i) ?: continue
            val idx = c.optInt("index", i)
            val nm = c.optString("name", "#$idx")
            val lv = c.optInt("lv", 1)
            val hp = c.optInt("hp", 0)
            val hpMax = c.optInt("hpMax", 0)
            val atk = c.optInt("atk", 0)
            val tag = if (c.optBoolean("inParty", false)) {
                tr("出战", "出撃", "Party")
            } else {
                tr("待命", "控え", "Reserve")
            }
            entries.add(Triple(idx, c, "[$tag] $nm · Lv$lv · HP:$hp/$hpMax · ATK:$atk"))
        }
        val labels = entries.map { it.third }.toTypedArray()
        dialogBuilder()
            .setTitle(tr("选择要修改的角色", "編集するキャラを選択", "Select Character"))
            .setItems(labels) { _, which ->
                val chosen = entries[which]
                showEvenicleEditCharDialog(chosen.first, chosen.second, chosen.third)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEvenicleEditCharDialog(target: Int, c: JSONObject?, labelTitle: String) {
        val isBatch = target < 0
        val title = if (isBatch) {
            labelTitle
        } else {
            val nm = c?.optString("name", "#$target") ?: "#$target"
            tr("修改属性 · $nm", "ステータス編集 · $nm", "Edit · $nm")
        }
        val fields = listOf(
            Triple("lv", tr("等级 Lv", "レベル Lv", "Level"), c?.optInt("lv", 1) ?: 1),
            Triple("hp", tr("当前 HP", "現在 HP", "Current HP"), c?.optInt("hp", 100) ?: 100),
            Triple("hpMax", tr("最大 HP", "最大 HP", "Max HP"), c?.optInt("hpMax", 100) ?: 100),
            Triple("atk", tr("攻击力 ATK", "攻撃力 ATK", "ATK"), c?.optInt("atk", 10) ?: 10),
            Triple("mag", tr("魔法力 MAG", "魔法力 MAG", "MAG"), c?.optInt("mag", 10) ?: 10),
            Triple("def", tr("防御力 DEF", "防御力 DEF", "DEF"), c?.optInt("def", 10) ?: 10),
            Triple("spd", tr("速度 SPD", "素早さ SPD", "SPD"), c?.optInt("spd", 10) ?: 10),
            Triple("skillP", tr("技能点 SkillP", "スキルポイント", "SkillP"), c?.optInt("skillP", 0) ?: 0),
            Triple("bp", tr("斗志 BP 上限5", "BP 上限5", "BP max 5"), c?.optInt("bp", 0) ?: 0),
            Triple("exp", tr("经验值 EXP", "経験値 EXP", "EXP"), c?.optInt("exp", 0) ?: 0)
        )
        showDohnaFormDialog(
            title = title,
            summary = if (isBatch) {
                tr("留空的项保持原值不变", "空欄の項目は変更しません", "Leave blank to keep current value")
            } else {
                val sp = c?.optInt("skillP", 0) ?: 0
                val bp = c?.optInt("bp", 0) ?: 0
                tr("当前 SkillP:$sp · BP:$bp", "現在 SkillP:$sp · BP:$bp", "Current SkillP:$sp · BP:$bp")
            },
            fields = fields,
            prefillValues = !isBatch,
            neutralActionLabel = if (!isBatch) tr("一键强化并回满", "全回復・強化", "Full Heal & Boost") else null,
            onNeutralAction = if (!isBatch) {
                {
                    val boost = JSONObject()
                        .put("target", target)
                        .put("hp", 9999)
                        .put("hpMax", 9999)
                        .put("atk", 999)
                        .put("mag", 999)
                        .put("def", 999)
                        .put("spd", 999)
                        .put("skillP", 999)
                        .put("bp", 6)
                    sendDohnaInGameAction(
                        JSONObject().put("evCharEdit", boost),
                        tr("角色已满状态强化", "キャラを強化しました", "Character boosted")
                    )
                }
            } else null
        ) { vals ->
            if (vals.isEmpty()) return@showDohnaFormDialog
            val ce = JSONObject().put("target", target)
            for ((k, v) in vals) ce.put(k, v)
            sendDohnaInGameAction(
                JSONObject().put("evCharEdit", ce),
                tr("角色属性修改已生效", "ステータスを更新しました", "Character updated")
            )
        }
    }

    private fun showDohnaBattleQuickDialog() {
        val items = arrayOf(
            tr("敌方全员HP设为1", "敵全員のHPを1にする", "Set All Enemies HP to 1"),
            tr("敌方清除增益并清空MP与怒气", "敵の強化解除＋MP・VPを0化", "Strip Enemy Buffs & Drain MP/VP"),
            tr("我方全员满HP满MP满怒气并净化异常", "味方全員全回復＋弱体解除", "Full Heal & Cleanse All Allies"),
            tr("我方全员满状态并附加攻技速暴击强化", "味方全員全回復＋全能力バフ付与", "Full Heal & Super Buff All Allies")
        )
        dialogBuilder()
            .setTitle(tr("战斗指令", "戦闘クイック", "Battle Actions"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(
                        JSONObject().put("ddBattle1HpEnemies", true),
                        tr("敌方全员HP已设为1", "敵全員のHPを1にしました", "Enemies HP set to 1"),
                        requireBattle = true
                    )
                    1 -> sendDohnaInGameAction(
                        JSONObject().put("ddBattleWeakenEnemies", true),
                        tr("已清除敌方增益并清空MP与怒气", "敵の強化解除・MP/VPゼロ化完了", "Enemy buffs & MP/VP cleared"),
                        requireBattle = true
                    )
                    2 -> sendDohnaInGameAction(
                        JSONObject().put("ddBattleHealAllies", true),
                        tr("我方全员已回满状态并净化异常", "味方全員を全回復しました", "Allies healed & cleansed"),
                        requireBattle = true
                    )
                    3 -> sendDohnaInGameAction(
                        JSONObject().put("ddBattleBoostAllies", true),
                        tr("我方全员已满状态并附加战技强化", "味方全員を全回復・強化しました", "Allies healed & boosted"),
                        requireBattle = true
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDohnaCommonCheatsDialog() {
        val items = arrayOf(
            tr("资金增加 100 万", "資金 +1,000,000", "Add 1,000,000 Money"),
            tr("资金增加 1000 万", "資金 +10,000,000", "Add 10,000,000 Money"),
            tr("资金拉满 9.99 亿并回满调教点", "資金カンスト＋調教P全回復", "Max Money & Refill TP"),
            tr("扩建店面单间上限至 10 间并扩充调教点上限", "部屋枠10拡張＋調教P上限10", "Expand Rooms to 10 & TP Max 10"),
            tr("当前已持有消耗品与徽章数量补满 99", "所持済みアイテム・バッジを99個化", "Refill Held Items & Badges to 99"),
            tr("解锁全消耗品与全 31 种正式徽章各 99 个", "全消費アイテム・全31種バッジ×99解放", "Unlock All Consumables & 31 Badges x99"),
            tr("在籍战斗成员全员 HP·MP·怒气回满", "戦闘メンバー全員HP・MP・VP全回復", "Full Heal All Party Members"),
            tr("已入队战斗成员一键满级 Lv99", "加入済みメンバー全員Lv99", "Level 99 All Joined Members"),
            tr("已入队成员全能力永久强化并满好感度", "加入済みメンバー全能力強化＋好感度最大", "Super Boost Joined Members & Max Feel"),
            tr("全员装备专属六阶毕业武器", "全員にランク6最強武器を装備", "Equip Rank 6 Weapons for All Members"),
            tr("全体春销人材精神避孕药回满并治愈崩溃怀孕", "全人材メンタル・ピル回復＋異常治療", "Heal All Workers Mental, Pill & Status"),
            tr("全体春销人材一键 SS 评级·UR 稀有度·四维拉满", "全人材ランクSS・UR・全ステータス最大", "Max All Workers Rank SS, UR & Stats")
        )
        dialogBuilder()
            .setTitle(tr("常用作弊", "定番チート", "Common Cheats"))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatAddMoney", 1000000),
                        tr("已增加 100 万资金", "資金 +100万を適用しました", "Added 1,000,000 Money")
                    )
                    1 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatAddMoney", 10000000),
                        tr("已增加 1000 万资金", "資金 +1000万を適用しました", "Added 10,000,000 Money")
                    )
                    2 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatMaxMoney", true),
                        tr("资金已拉满并回满调教点", "資金最大・調教P全回復を適用", "Max Money & TP applied")
                    )
                    3 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatExpandMansion", true),
                        tr("单间上限已扩至 10 间且调教点上限扩至 10", "部屋枠10・調教P上限10を適用", "Expanded rooms & TP max to 10")
                    )
                    4 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatHeldItems99", true),
                        tr("已持有消耗品与徽章已补满 99 个", "所持アイテム・バッジを99個にしました", "Held items & badges set to 99")
                    )
                    5 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatAllItemsBadges99", true),
                        tr("已解锁全部消耗品与 31 种正式徽章各 99 个", "全消費アイテム・バッジ99個を解放", "Unlocked all consumables & badges x99")
                    )
                    6 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatPartyFullHeal", true),
                        tr("战斗成员全员状态已回满", "全員全回復しました", "Party fully healed")
                    )
                    7 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatPartyLv99", true),
                        tr("已入队成员已升至 Lv99", "加入済み全員Lv99を適用", "Joined members set to Lv99")
                    )
                    8 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatPartySuperBoost", true),
                        tr("已入队成员全能力与好感度已强化", "加入済み全員の能力・好感度を強化", "Joined members super boosted")
                    )
                    9 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatPartyRank6Weapons", true),
                        tr("已为全员装备六阶毕业武器", "全員にランク6武器を装備しました", "Equipped Rank 6 weapons")
                    )
                    10 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatWorkersHeal", true),
                        tr("全体人材精神避孕药已回满并治愈异常状态", "全人材の状態を回復しました", "All workers healed")
                    )
                    11 -> sendDohnaInGameAction(
                        JSONObject().put("ddCheatWorkersMaxStats", true),
                        tr("全体人材已设为 SS 评级·UR 稀有度·四维拉满", "全人材ステータス最大を適用", "All workers maxed")
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDohnaEditEconomyDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(
                activity,
                tr("请先进入游戏存档", "ロード後に編集してください", "Load a game first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val phaseStr = dohnaPhaseName(j.optInt("phase", 0))
        val wCnt = j.optInt("workerCount", 0)
        val wLim = j.optInt("workerLimit", 5)
        val gCnt = j.optInt("garageCount", 0)
        val itemKinds = j.optInt("heldItemKinds", 0)
        val badgeKinds = j.optInt("heldBadgeKinds", 0)

        val summary = tr(
            "$phaseStr · 店面：$wCnt/$wLim · 库房：$gCnt · 物品：$itemKinds · 徽章：$badgeKinds",
            "$phaseStr · 店舗：$wCnt/$wLim · 保管：$gCnt · アイテム：$itemKinds · バッジ：$badgeKinds",
            "$phaseStr · Shop: $wCnt/$wLim · Garage: $gCnt · Items: $itemKinds · Badges: $badgeKinds"
        )
        val fields = listOf(
            Triple("ddMoney", tr("持有资金 0~999999999", "所持資金", "Money"), j.optInt("money", 0)),
            Triple("ddTurn", tr("当前天数 1~99999", "現在日数", "Current Day"), j.optInt("turn", 1)),
            Triple("ddTp", tr("当前调教点 TP", "現在調教P", "Current TP"), j.optInt("tp", 0)),
            Triple("ddTpMax", tr("调教点上限 1~99", "調教P上限", "Max TP"), j.optInt("tpMax", 3)),
            Triple("ddWorkerLimit", tr("店面单间上限 1~10", "部屋枠上限 1~10", "Room Limit 1~10"), wLim)
        )
        showDohnaFormDialog(
            title = tr("经营与资源", "経営・資金・日数", "Economy & Resources"),
            summary = summary,
            fields = fields,
            prefillValues = true,
            onApply = { values ->
                val act = JSONObject()
                for ((k, v) in values) act.put(k, v)
                sendDohnaInGameAction(act, tr("修改已生效", "適用しました", "Applied"))
            }
        )
    }

    private fun showDohnaSelectPlayerDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(
                activity,
                tr("请先进入游戏存档", "ロード後に編集してください", "Load a game first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val arr = j.optJSONArray("ddPlayers")
        if (arr == null || arr.length() == 0) {
            Toast.makeText(activity, tr("暂无战斗成员数据", "メンバーデータなし", "No party members"), Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<Triple<Int, JSONObject?, String>>()
        options.add(Triple(-1, null, tr("批量：全部已入队成员", "一括：加入済み全員", "Batch: Joined Members")))
        options.add(Triple(-2, null, tr("批量：全体可操作角色", "一括：全キャラ", "Batch: All Characters")))

        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val realIdx = p.optInt("index", i)
            val name = p.optString("name", "").ifBlank { p.optString("id", "#$realIdx") }
            val joined = p.optBoolean("joined", false)
            val lv = p.optInt("level", 1)
            val hp = p.optInt("hp", 0)
            val hpMax = p.optInt("hpMax", 0)
            val joinTag = if (joined) tr("已入队", "加入済", "Joined") else tr("未入队", "未加入", "Not Joined")
            options.add(Triple(realIdx, p, "$name · $joinTag · Lv$lv · HP $hp/$hpMax"))
        }

        val labels = options.map { it.third }.toTypedArray()
        dialogBuilder()
            .setTitle(tr("战斗成员", "戦闘メンバー", "Party Members"))
            .setItems(labels) { _, which ->
                val chosen = options.getOrNull(which) ?: return@setItems
                showDohnaEditPlayerDialog(chosen.first, chosen.second, chosen.third)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDohnaEditPlayerDialog(target: Int, p: JSONObject?, labelTitle: String) {
        val single = p != null
        val summary = if (p != null) {
            val wp = p.optString("weaponId", "").ifBlank { tr("无", "なし", "None") }
            val pow = p.optInt("pow", 0)
            val tec = p.optInt("tec", 0)
            val spd = p.optInt("spd", 0)
            val exp = p.optInt("exp", 0)
            tr(
                "武器：$wp · 力量:$pow · 技巧:$tec · 速度:$spd · 经验:$exp",
                "武器：$wp · POW:$pow · TEC:$tec · SPD:$spd · EXP:$exp",
                "Weapon: $wp · POW:$pow · TEC:$tec · SPD:$spd · EXP:$exp"
            )
        } else {
            tr(
                "留空项保持原值",
                "空欄の項目は元の値を維持します",
                "Blank fields keep current values"
            )
        }

        val fields = listOf(
            Triple("level", tr("等级 1~99", "レベル 1~99", "Level 1~99"), p?.optInt("level", 1) ?: 99),
            Triple("addExp", tr("追加升级经验 留空不加", "追加経験値 空欄で加算なし", "Add EXP"), -1),
            Triple("hp", tr("当前HP", "現在HP", "Current HP"), p?.optInt("hp", 0) ?: 9999),
            Triple("mp", tr("当前MP", "現在MP", "Current MP"), p?.optInt("mp", 100) ?: 100),
            Triple("vp", tr("当前怒气VP", "現在VP", "Current VP"), p?.optInt("vp", 0) ?: 100),
            Triple("alterPow", tr("力量永久加成 POW", "POW永久加算", "Bonus POW"), p?.optInt("alterPow", 0) ?: 9999),
            Triple("alterTec", tr("技巧永久加成 TEC", "TEC永久加算", "Bonus TEC"), p?.optInt("alterTec", 0) ?: 9999),
            Triple("alterSpd", tr("速度永久加成 SPD", "SPD永久加算", "Bonus SPD"), p?.optInt("alterSpd", 0) ?: 3000),
            Triple("alterHp", tr("最大HP永久加成", "最大HP永久加算", "Bonus Max HP"), p?.optInt("alterHp", 0) ?: 20000),
            Triple("alterMp", tr("最大MP永久加成", "最大MP永久加算", "Bonus Max MP"), p?.optInt("alterMp", 0) ?: 100),
            Triple("alterVp", tr("最大怒气永久加成", "最大VP永久加算", "Bonus Max VP"), p?.optInt("alterVp", 0) ?: 100),
            Triple("feel", tr("感情好感度 0~99999", "好感度 0~99999", "Affection Feel"), p?.optInt("feel", 0) ?: 9999)
        )

        showDohnaFormDialog(
            title = labelTitle,
            summary = summary,
            fields = fields,
            prefillValues = single,
            blankByDefaultKeys = setOf("addExp"),
            neutralActionLabel = tr("一键强化此目标", "この対象を最大強化", "Max Target"),
            onNeutralAction = {
                val pe = JSONObject().apply {
                    put("target", target)
                    put("level", 99)
                    put("alterPow", 9999)
                    put("alterTec", 9999)
                    put("alterSpd", 3000)
                    put("alterHp", 20000)
                    put("alterMp", 100)
                    put("alterVp", 100)
                    put("hp", 999999)
                    put("mp", 9999)
                    put("vp", 9999)
                    put("feel", 9999)
                }
                sendDohnaInGameAction(
                    JSONObject().put("ddPlayerEdit", pe),
                    tr("战斗成员一键强化已生效", "最大強化を適用しました", "Member maxed")
                )
            },
            onApply = { values ->
                if (values.isEmpty()) {
                    Toast.makeText(activity, tr("未输入需要修改的数值", "変更する数値が入力されていません", "No values entered"), Toast.LENGTH_SHORT).show()
                    return@showDohnaFormDialog
                }
                val pe = JSONObject().put("target", target)
                for ((k, v) in values) {
                    if (k == "addExp" && v <= 0) continue
                    pe.put(k, v)
                }
                sendDohnaInGameAction(
                    JSONObject().put("ddPlayerEdit", pe),
                    tr("战斗成员数值修改已生效", "適用しました", "Applied")
                )
            }
        )
    }

    private fun showDohnaSelectWorkerDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(
                activity,
                tr("请先进入游戏存档", "ロード後に編集してください", "Load a game first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val arr = j.optJSONArray("ddWorkers")
        val totalCnt = j.optInt("workerCount", 0) + j.optInt("garageCount", 0)
        if (totalCnt <= 0 || arr == null || arr.length() == 0) {
            Toast.makeText(
                activity,
                tr("暂无春销人材", "ハルウリ人材なし", "No brothel workers"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        data class WorkerTarget(val scope: Int, val index: Int, val obj: JSONObject?, val label: String)
        val options = mutableListOf(
            WorkerTarget(0, -1, null, tr("批量：店面全体人材", "一括：店舗の全人材", "Batch: Shop Workers")),
            WorkerTarget(1, -1, null, tr("批量：库房全体人材", "一括：保管庫の全人材", "Batch: Garage Workers")),
            WorkerTarget(2, -1, null, tr("批量：全体在籍人材", "一括：全在籍人材", "Batch: All Workers"))
        )

        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i) ?: continue
            val sc = w.optInt("scope", 0)
            val idx = w.optInt("index", 0)
            val locTag = if (sc == 0) tr("店面", "店舗", "Shop") else tr("库房", "保管", "Garage")
            val name = w.optString("name", "").ifBlank { "#$idx" }
            val rk = dohnaWorkerRankName(w.optInt("rank", 0))
            val rr = dohnaWorkerRarityName(w.optInt("rarity", 0))
            val lks = w.optInt("looks", 0)
            val tec = w.optInt("technic", 0)
            val men = w.optInt("mental", 0)
            options.add(WorkerTarget(sc, idx, w, "$locTag · $name · 评级$rk · $rr · 貌$lks 技$tec 心$men"))
        }

        val labels = options.map { it.label }.toTypedArray()
        dialogBuilder()
            .setTitle(tr("春销人材", "ハルウリ人材", "Brothel Workers"))
            .setItems(labels) { _, which ->
                val chosen = options.getOrNull(which) ?: return@setItems
                showDohnaEditWorkerDialog(chosen.scope, chosen.index, chosen.obj, chosen.label)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDohnaEditWorkerDialog(scope: Int, index: Int, w: JSONObject?, labelTitle: String) {
        val single = w != null
        val summary = tr(
            "评级 0~9：D~SS · 稀有度 0~5：N~UR · 状态 0/1${if (!single) " · 留空保持原值" else ""}",
            "ランク 0~9：D〜SS · レア度 0~5：N〜UR · 状態 0/1",
            "Rank 0~9: D..SS · Rarity 0~5: N..UR · State 0/1"
        )
        val fields = listOf(
            Triple("rank", tr("综合评级 0~9", "総合ランク 0~9", "Rank 0~9"), w?.optInt("rank", 0) ?: 9),
            Triple("rarity", tr("稀有度 0~5", "レア度 0~5", "Rarity 0~5"), w?.optInt("rarity", 0) ?: 5),
            Triple("looks", tr("容貌 LKS 0~9999", "ルックス 0~9999", "Looks 0~9999"), w?.optInt("looks", 0) ?: 9999),
            Triple("technic", tr("技巧 TEC 0~9999", "テクニック 0~9999", "Technic 0~9999"), w?.optInt("technic", 0) ?: 9999),
            Triple("mental", tr("精神 MEN 0~9999", "メンタル 0~9999", "Mental 0~9999"), w?.optInt("mental", 0) ?: 9999),
            Triple("pill", tr("避孕药 DEF 0~255", "ピル効果 0~255", "Pill DEF 0~255"), w?.optInt("pill", 0) ?: 255),
            Triple("virgin", tr("处女状态 0或1", "処女状態 0/1", "Virgin 0/1"), if (w?.optBoolean("virgin", false) == true) 1 else 0),
            Triple("pregnance", tr("怀孕状态 0或1", "妊娠状態 0/1", "Pregnant 0/1"), if (w?.optBoolean("pregnance", false) == true) 1 else 0),
            Triple("insane", tr("精神崩溃 0或1", "精神崩壊 0/1", "Insane 0/1"), if (w?.optBoolean("insane", false) == true) 1 else 0)
        )

        showDohnaFormDialog(
            title = labelTitle,
            summary = summary,
            fields = fields,
            prefillValues = single,
            neutralActionLabel = tr("一键拉满此目标", "この対象を最大化", "Max Target"),
            onNeutralAction = {
                val we = JSONObject().apply {
                    put("scope", scope)
                    put("index", index)
                    put("rank", 9)
                    put("rarity", 5)
                    put("looks", 9999)
                    put("technic", 9999)
                    put("mental", 9999)
                    put("pill", 255)
                    put("pregnance", 0)
                    put("insane", 0)
                }
                sendDohnaInGameAction(
                    JSONObject().put("ddWorkerEdit", we),
                    tr("春销人材一键拉满已生效", "人材最大化を適用しました", "Worker maxed")
                )
            },
            onApply = { values ->
                if (values.isEmpty()) {
                    Toast.makeText(activity, tr("未输入需要修改的数值", "変更する数値が入力されていません", "No values entered"), Toast.LENGTH_SHORT).show()
                    return@showDohnaFormDialog
                }
                val we = JSONObject()
                    .put("scope", scope)
                    .put("index", index)
                for ((k, v) in values) we.put(k, v)
                sendDohnaInGameAction(
                    JSONObject().put("ddWorkerEdit", we),
                    tr("春销人材数值修改已生效", "適用しました", "Applied")
                )
            }
        )
    }

    private fun showDohnaFormDialog(
        title: String,
        summary: String?,
        fields: List<Triple<String, String, Int>>,
        prefillValues: Boolean,
        blankByDefaultKeys: Set<String> = emptySet(),
        neutralActionLabel: String? = null,
        onNeutralAction: (() -> Unit)? = null,
        onApply: (Map<String, Int>) -> Unit
    ) {
        val scroll = ScrollView(activity)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(6))
        }
        if (!summary.isNullOrBlank()) {
            val tip = TextView(activity).apply {
                text = summary
                setPadding(0, 0, 0, dp(8))
            }
            container.addView(tip)
        }
        val inputs = LinkedHashMap<String, EditText>()
        for ((key, label, defVal) in fields) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val tv = TextView(activity).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f)
            }
            val shouldPrefill = prefillValues && !blankByDefaultKeys.contains(key)
            val et = EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                if (shouldPrefill) {
                    setText(defVal.toString())
                } else {
                    hint = tr("留空保持不变", "空欄で変更なし", "Blank = keep")
                }
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f)
            }
            inputs[key] = et
            row.addView(tv)
            row.addView(et)
            container.addView(row)
        }
        scroll.addView(container)

        val builder = dialogBuilder()
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                val out = LinkedHashMap<String, Int>()
                for ((key, et) in inputs) {
                    val v = et.text.toString().trim().toIntOrNull()
                    if (v != null) out[key] = v
                }
                onApply(out)
            }
            .setNegativeButton(R.string.cancel, null)
        if (!neutralActionLabel.isNullOrBlank() && onNeutralAction != null) {
            builder.setNeutralButton(neutralActionLabel) { _, _ -> onNeutralAction.invoke() }
        }
        builder.show()
    }

    private fun showNumberInputDialog(
        title: String,
        fields: List<Triple<String, String, Int>>
    ) {
        val scroll = ScrollView(activity)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(6))
        }
        val inputs = LinkedHashMap<String, EditText>()
        for ((key, label, defVal) in fields) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val tv = TextView(activity).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            val et = EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setText(defVal.toString())
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)
            }
            inputs[key] = et
            row.addView(tv)
            row.addView(et)
            container.addView(row)
        }
        scroll.addView(container)

        dialogBuilder()
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                val act = JSONObject()
                for ((key, et) in inputs) {
                    val v = et.text.toString().trim().toIntOrNull()
                    if (v != null) act.put(key, v)
                }
                sendAction(act, tr("修改已生效", "適用しました", "Applied"))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFontScaleDialog() {
        showScaleSliderDialog(
            title = tr("字体大小", "文字サイズ", "Font Size"),
            description = tr(
                "范围 75%~160%，默认 100%",
                "範囲 75%~160% · 標準 100%",
                "Range 75%~160% · Default 100%"
            ),
            minPct = GameModifierBridge.MIN_FONT_SCALE_PCT,
            maxPct = GameModifierBridge.MAX_FONT_SCALE_PCT,
            defaultPct = GameModifierBridge.DEFAULT_FONT_SCALE_PCT,
            stepPct = 5,
            currentPct = GameModifierBridge.getFontScalePercent(activity),
            presets = listOf(80, 90, 100, 115, 130, 150),
            applyPct = { pct -> GameModifierBridge.setFontScalePercent(activity, pct) },
            onConfirmedToast = { pct, isReset ->
                val msg = if (isReset) {
                    tr("已恢复默认 100%", "100% に戻しました", "Reset to 100%")
                } else {
                    tr("已设为 $pct%", "$pct% に設定しました", "Set to $pct%")
                }
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showScrollbarScaleDialog() {
        showScaleSliderDialog(
            title = tr("滚动条", "スクロールバー", "Scrollbar"),
            description = tr(
                "范围 100%~300%，默认 100%",
                "範囲 100%~300% · 標準 100%",
                "Range 100%~300% · Default 100%"
            ),
            minPct = GameModifierBridge.MIN_SCROLLBAR_SCALE_PCT,
            maxPct = GameModifierBridge.MAX_SCROLLBAR_SCALE_PCT,
            defaultPct = GameModifierBridge.DEFAULT_SCROLLBAR_SCALE_PCT,
            stepPct = 10,
            currentPct = GameModifierBridge.getScrollbarScalePercent(activity),
            presets = listOf(100, 130, 160, 200, 250, 300),
            applyPct = { pct -> GameModifierBridge.setScrollbarScalePercent(activity, pct) },
            onConfirmedToast = { pct, isReset ->
                val msg = if (isReset) {
                    tr("已恢复默认 100%", "100% に戻しました", "Reset to 100%")
                } else {
                    tr("已设为 $pct%", "$pct% に設定しました", "Set to $pct%")
                }
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showScaleSliderDialog(
        title: String,
        description: String,
        minPct: Int,
        maxPct: Int,
        defaultPct: Int,
        stepPct: Int,
        currentPct: Int,
        presets: List<Int>,
        applyPct: (Int) -> Unit,
        onConfirmedToast: (Int, Boolean) -> Unit
    ) {
        val initialPct = currentPct.coerceIn(minPct, maxPct)
        var selectedPct = initialPct
        var lastAppliedPct = initialPct
        var confirmed = false
        var updatingBar = false

        fun applyLivePreview(pct: Int) {
            val clamped = pct.coerceIn(minPct, maxPct)
            if (clamped != lastAppliedPct) {
                lastAppliedPct = clamped
                applyPct(clamped)
            }
        }

        val scroll = ScrollView(activity)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        val descTv = TextView(activity).apply {
            text = description
            setPadding(0, 0, 0, dp(10))
        }
        container.addView(descTv)

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        val valueTv = TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 16f
        }
        val seekBar = SeekBar(activity).apply {
            max = maxPct - minPct
            progress = selectedPct - minPct
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }

        fun updateSelected(newPct: Int, syncSeek: Boolean, previewNow: Boolean) {
            selectedPct = newPct.coerceIn(minPct, maxPct)
            valueTv.text = tr(
                "当前比例：$selectedPct%",
                "現在倍率：$selectedPct%",
                "Current: $selectedPct%"
            )
            if (syncSeek) {
                updatingBar = true
                seekBar.progress = selectedPct - minPct
                updatingBar = false
            }
            if (previewNow) {
                applyLivePreview(selectedPct)
            }
        }

        val minusBtn = Button(activity).apply {
            text = "-$stepPct%"
            setOnClickListener { updateSelected(selectedPct - stepPct, true, true) }
        }
        val plusBtn = Button(activity).apply {
            text = "+$stepPct%"
            setOnClickListener { updateSelected(selectedPct + stepPct, true, true) }
        }
        headerRow.addView(valueTv)
        headerRow.addView(minusBtn)
        headerRow.addView(plusBtn)
        container.addView(headerRow)
        container.addView(seekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingBar) return
                updateSelected(minPct + progress, false, false)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {
                applyLivePreview(selectedPct)
            }
        })

        for (chunk in presets.chunked(3)) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            for (preset in chunk) {
                val btn = Button(activity).apply {
                    text = "$preset%"
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { updateSelected(preset, true, true) }
                }
                row.addView(btn)
            }
            container.addView(row)
        }

        updateSelected(selectedPct, true, false)
        scroll.addView(container)

        dialogBuilder()
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                confirmed = true
                lastAppliedPct = selectedPct
                applyPct(selectedPct)
                onConfirmedToast(selectedPct, false)
            }
            .setNeutralButton(tr("恢复默认 $defaultPct%", "標準 $defaultPct%", "Reset $defaultPct%")) { _, _ ->
                confirmed = true
                lastAppliedPct = defaultPct
                applyPct(defaultPct)
                onConfirmedToast(defaultPct, true)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (!confirmed && lastAppliedPct != initialPct) {
                    lastAppliedPct = initialPct
                    applyPct(initialPct)
                }
            }
            .setOnCancelListener {
                if (!confirmed && lastAppliedPct != initialPct) {
                    lastAppliedPct = initialPct
                    applyPct(initialPct)
                }
            }
            .show()
    }
}
