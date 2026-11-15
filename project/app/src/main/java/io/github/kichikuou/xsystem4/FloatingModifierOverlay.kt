package io.github.kichikuou.xsystem4

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.text.InputType
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
import java.util.Locale
import kotlin.math.abs

class FloatingModifierOverlay(private val activity: Activity) {

    private var rootOverlay: FrameLayout? = null
    private var floatingBtn: ImageView? = null
    private var lastCheatCode: String = ""

    private val isZh: Boolean
        get() = Locale.getDefault().language.startsWith("zh")
    private val isJa: Boolean
        get() = Locale.getDefault().language.startsWith("ja")

    private fun tr(zh: String, ja: String, en: String): String = when {
        isZh -> zh
        isJa -> ja
        else -> en
    }

    private fun dp(v: Int): Int =
        (v * activity.resources.displayMetrics.density).toInt()

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
        val isRance10 = !isDohnaDohna && j.optBoolean("isRance10", xsAct?.isRance10Game() ?: true)
        val inGame = j.optBoolean("inGame", false)
        val inBattle = j.optBoolean("inBattle", false)
        val savedMask = GameModifierBridge.getSavedLockMask(activity)
        val lockCount = GameModifierBridge.getLiveLockCount(activity)
        val dohnaLockCount = GameModifierBridge.getDohnaLiveLockCount(activity)
        val streamerLockOff = (savedMask and GameModifierBridge.LOCK_DISABLE_STREAMER_MODE) != 0
        val liveStreamerMode = j.optInt("streamerMode", if (streamerLockOff) 0 else 1)
        val isStreamerDisabled = if (j.has("streamerMode")) (liveStreamerMode == 0) else streamerLockOff

        val titleText = if (isDohnaDohna) {
            if (!inGame) {
                tr("多娜多娜修改器 · 请先进入游戏存档", "ドーナドーナ改造 · ロード後に有効", "Dohna Modifier · Enter Game First")
            } else if (inBattle) {
                val bTurn = j.optInt("battleTurn", 1)
                val eCnt = j.optInt("enemyCount", 0)
                val eHp = j.optInt("enemyTotalHp", 0)
                tr(
                    "多娜多娜修改器 [战斗T$bTurn · 敌${eCnt}名 · 敌HP:$eHp]",
                    "改造 [戦闘T$bTurn · 敵${eCnt}体 · 敵HP:$eHp]",
                    "Modifier [Battle T$bTurn · Enemy HP:$eHp]"
                )
            } else {
                val turn = j.optInt("turn", 1)
                val money = j.optInt("money", 0)
                val tp = j.optInt("tp", 0)
                val tpMax = j.optInt("tpMax", 0)
                tr(
                    "多娜多娜修改器 [第${turn}天 · 资金:$money · TP:$tp/$tpMax]",
                    "改造 [${turn}日目 · 資金:$money · TP:$tp/$tpMax]",
                    "Modifier [Day $turn · Money:$money · TP:$tp/$tpMax]"
                )
            }
        } else if (!isRance10 || !inGame) {
            tr("悬浮窗修改器", "ゲーム改造", "Floating Modifier")
        } else if (inBattle) {
            val ap = j.optInt("battleAp", 0)
            val eHp = j.optInt("enemyHp", 0)
            val pHp = j.optInt("partyHp", 0)
            tr("悬浮窗修改器 [战斗 · AP:$ap · 敌HP:$eHp · 我HP:$pHp]", "改造 [戦闘 · AP:$ap · 敵HP:$eHp]", "Modifier [Battle · AP:$ap]")
        } else {
            val turn = j.optInt("turn", 0)
            val food = j.optInt("foodTicket", 0)
            val pHp = j.optInt("partyHp", 0)
            tr("悬浮窗修改器 [回合:$turn · 食券:$food · HP:$pHp]", "改造 [T:$turn · 食券:$food · HP:$pHp]", "Modifier [Turn:$turn · HP:$pHp]")
        }

        val streamerItemLabel = if (isStreamerDisabled) {
            tr("关闭主播模式 · 已关闭", "配信者モード解除 · 現在OFF", "Disable Streamer Mode · OFF")
        } else {
            tr("关闭主播模式", "配信者モード解除", "Disable Streamer Mode")
        }

        val ctrlItemLabel = if (ctrlVisible) {
            tr("Ctrl 快进悬浮键：已显示 [点击隐藏]", "Ctrl早送りボタン：表示中", "Ctrl Skip Button: Shown")
        } else {
            tr("Ctrl 快进悬浮键：已隐藏 [点击显示]", "Ctrl早送りボタン：非表示", "Ctrl Skip Button: Hidden")
        }

        val fontPct = GameModifierBridge.getFontScalePercent(activity)
        val sbPct = GameModifierBridge.getScrollbarScalePercent(activity)

        val menuEntries = mutableListOf<Pair<String, () -> Unit>>()
        if (isRance10) {
            menuEntries.add(
                tr("实时锁定开关 · 已开启 $lockCount 项…", "リアルタイムロック · $lockCount 項目…", "Live Locks · $lockCount ON…") to { showLocksDialog() }
            )
        } else if (isDohnaDohna) {
            menuEntries.addAll(
                listOf(
                    tr("实时锁定开关 · 已开启 $dohnaLockCount 项…", "リアルタイムロック · $dohnaLockCount 項目…", "Live Locks · $dohnaLockCount ON…") to { showDohnaLocksDialog() },
                    tr("常用作弊器…", "定番チート機能…", "Common Cheats…") to { showDohnaCommonCheatsDialog() },
                    tr("战斗快捷指令…", "戦闘クイック操作…", "Battle Quick Actions…") to { showDohnaBattleQuickDialog() },
                    tr("修改经营与资源数值…", "経営・資金・日数編集…", "Edit Economy & Resources…") to { showDohnaEditEconomyDialog() },
                    tr("修改战斗成员数值…", "戦闘メンバー数値編集…", "Edit Party Members…") to { showDohnaSelectPlayerDialog() },
                    tr("修改春销人材数值…", "ハルウリ人材数値編集…", "Edit Brothel Workers…") to { showDohnaSelectWorkerDialog() }
                )
            )
        }
        menuEntries.add(
            tr("字体大小修改 · 当前 $fontPct%…", "文字サイズ変更 · 現在 $fontPct%…", "Font Size · $fontPct%…") to { showFontScaleDialog() }
        )
        menuEntries.add(
            tr("滚动条修改 · 当前 $sbPct%…", "スクロールバー変更 · 現在 $sbPct%…", "Scrollbar Size · $sbPct%…") to { showScrollbarScaleDialog() }
        )
        if (isRance10) {
            menuEntries.add(streamerItemLabel to { triggerDisableStreamerModeInGame(inGame) })
        }
        if (ctrlEnabled) {
            menuEntries.add(
                ctrlItemLabel to {
                    val nextVisible = xsAct?.toggleCtrlOverlay() ?: (!ctrlVisible).also {
                        GameModifierBridge.setCtrlOverlayVisible(activity, it)
                    }
                    Toast.makeText(
                        activity,
                        if (nextVisible) tr("已显示 Ctrl 快进悬浮键", "Ctrl早送りボタンを表示しました", "Ctrl skip button shown")
                        else tr("已隐藏 Ctrl 快进悬浮键", "Ctrl早送りボタンを非表示にしました", "Ctrl skip button hidden"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
        if (isRance10) {
            menuEntries.addAll(
                listOf(
                    tr("输入作弊码…", "チートコード入力…", "Enter Cheat Code…") to { showCheatCodeDialog() },
                    tr("战斗快捷指令…", "戦闘クイック操作…", "Battle Quick Actions…") to { showBattleQuickDialog() },
                    tr("一键全局强化…", "一括強化プリセット…", "One-Click Boosts…") to { showOneClickBoostDialog() },
                    tr("修改资源与回合…", "リソース・ターン編集…", "Edit Resources & Turn…") to { showEditResourcesDialog(j) },
                    tr("修改部队与属性加成…", "部隊HP・ステータス編集…", "Edit Party & Stats…") to { showEditPartyDialog(j) },
                    tr("修改四国兵力与人口…", "四国兵力・人口編集…", "Edit World & Armies…") to { showEditWorldDialog(j) },
                    tr("修改卡牌叠卡与角色星级…", "カード・キャラ★編集…", "Edit Cards & Chars…") to { showEditCardsDialog() }
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

        AlertDialog.Builder(activity)
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

        AlertDialog.Builder(activity)
            .setTitle(tr("实时锁定开关", "リアルタイムロック", "Live Locks"))
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
        val tip = TextView(activity).apply {
            text = tr(
                "输入后自动解禁限制并提交生效：",
                "入力すると自動的に制限を解除してMODチートを実行します：",
                "Automatically unlocks restrictions and submits MOD cheat:"
            )
            setPadding(0, 0, 0, dp(8))
        }
        val et = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = tr("例如：whosyourdaddy / 打败哈尼王 / 餐券10", "例: whosyourdaddy", "e.g. whosyourdaddy")
            setSingleLine(true)
            setText(prefill)
            setSelection(text.length)
        }
        container.addView(tip)
        container.addView(et)

        AlertDialog.Builder(activity)
            .setTitle(tr("输入作弊码", "チートコード入力", "Enter Cheat Code"))
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
        AlertDialog.Builder(activity)
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
        AlertDialog.Builder(activity)
            .setTitle(tr("战斗快捷指令", "戦闘クイック操作", "Battle Quick Actions"))
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
        AlertDialog.Builder(activity)
            .setTitle(tr("一键全局强化", "一括強化プリセット", "One-Click Boosts"))
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
                tr("请先开始游戏或读取存档后再执行", "ゲームを開始またはロードしてから実行してください", "Start or load a game first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (requireBattle && !live.optBoolean("inBattle", false)) {
            Toast.makeText(
                activity,
                tr("当前不在战斗中，进入战斗后方可执行", "現在は戦闘中ではありません", "Only available during battle"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        sendAction(act, toastMsg)
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
        var mask = GameModifierBridge.getSavedDohnaLockMask(activity)
        val labels = lockDefs.map { it.second }.toTypedArray()
        val checked = BooleanArray(lockDefs.size) { idx -> (mask and lockDefs[idx].first) != 0 }

        AlertDialog.Builder(activity)
            .setTitle(tr("多娜多娜实时锁定开关", "リアルタイムロック", "Dohna Live Locks"))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val bit = lockDefs[which].first
                mask = if (isChecked) (mask or bit) else (mask and bit.inv())
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                GameModifierBridge.setSavedDohnaLockMask(activity, mask)
                Toast.makeText(activity, tr("已更新锁定项", "ロックを更新しました", "Locks updated"), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(tr("全部关闭", "全解除", "Clear All")) { _, _ ->
                GameModifierBridge.setSavedDohnaLockMask(activity, 0)
                Toast.makeText(activity, tr("已关闭全部锁定", "全解除しました", "All locks cleared"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDohnaBattleQuickDialog() {
        val items = arrayOf(
            tr("敌方全员HP设为1", "敵全員のHPを1にする", "Set All Enemies HP to 1"),
            tr("敌方清除增益并清空MP与怒气", "敵の強化解除＋MP・VPを0化", "Strip Enemy Buffs & Drain MP/VP"),
            tr("我方全员满HP满MP满怒气并净化异常", "味方全員全回復＋弱体解除", "Full Heal & Cleanse All Allies"),
            tr("我方全员满状态并附加攻技速暴击强化", "味方全員全回復＋全能力バフ付与", "Full Heal & Super Buff All Allies")
        )
        AlertDialog.Builder(activity)
            .setTitle(tr("战斗快捷指令", "戦闘クイック操作", "Battle Quick Actions"))
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
        AlertDialog.Builder(activity)
            .setTitle(tr("多娜多娜常用作弊器", "定番チート機能", "Common Cheats"))
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
                tr("请先开始游戏或读取存档后再修改数值", "ゲームを開始またはロードしてから編集してください", "Start or load a game first"),
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
            "当前阶段：$phaseStr · 店面人材：$wCnt/$wLim · 库房人材：$gCnt · 已持有物品种类：$itemKinds · 徽章种类：$badgeKinds",
            "進行：$phaseStr · 店舗人材：$wCnt/$wLim · 保管：$gCnt · アイテム種：$itemKinds · バッジ種：$badgeKinds",
            "Phase: $phaseStr · Shop: $wCnt/$wLim · Garage: $gCnt · Items: $itemKinds · Badges: $badgeKinds"
        )
        val fields = listOf(
            Triple("ddMoney", tr("持有资金 0~999999999", "所持資金", "Money"), j.optInt("money", 0)),
            Triple("ddTurn", tr("当前天数 1~99999", "現在日数", "Current Day"), j.optInt("turn", 1)),
            Triple("ddTp", tr("当前调教点 TP", "現在調教P", "Current TP"), j.optInt("tp", 0)),
            Triple("ddTpMax", tr("调教点上限 1~99", "調教P上限", "Max TP"), j.optInt("tpMax", 3)),
            Triple("ddWorkerLimit", tr("店面单间上限 1~10", "部屋枠上限 1~10", "Room Limit 1~10"), wLim)
        )
        showDohnaFormDialog(
            title = tr("修改经营与资源数值", "経営・資金・日数編集", "Edit Economy & Resources"),
            summary = summary,
            fields = fields,
            prefillValues = true,
            onApply = { values ->
                val act = JSONObject()
                for ((k, v) in values) act.put(k, v)
                sendDohnaInGameAction(act, tr("经营与资源数值修改已生效", "適用しました", "Applied"))
            }
        )
    }

    private fun showDohnaSelectPlayerDialog() {
        val j = readLiveJson()
        if (!j.optBoolean("inGame", false)) {
            Toast.makeText(
                activity,
                tr("请先开始游戏或读取存档后再修改成员数值", "ゲームを開始またはロードしてから編集してください", "Start or load a game first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val arr = j.optJSONArray("ddPlayers")
        if (arr == null || arr.length() == 0) {
            Toast.makeText(activity, tr("暂无可修改的战斗成员数据", "メンバーデータがありません", "No party members found"), Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<Triple<Int, JSONObject?, String>>()
        options.add(Triple(-1, null, tr("批量修改：全部已入队成员", "一括編集：加入済みメンバー全員", "Batch: All Joined Members")))
        options.add(Triple(-2, null, tr("批量修改：全体10名可操作角色", "一括編集：全プレイアブルキャラ", "Batch: All Playable Characters")))

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
        AlertDialog.Builder(activity)
            .setTitle(tr("选择要修改的战斗成员", "編集する戦闘メンバーを選択", "Select Party Member"))
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
                "装备武器：$wp · 当前总能力 力量:$pow · 技巧:$tec · 速度:$spd · 累计经验:$exp",
                "武器：$wp · 現在能力 POW:$pow · TEC:$tec · SPD:$spd · EXP:$exp",
                "Weapon: $wp · POW:$pow · TEC:$tec · SPD:$spd · EXP:$exp"
            )
        } else {
            tr(
                "批量修改提示：仅修改填写了数字的项目，留空的项目将保持各成员原有数值不变。",
                "一括編集：数値を入力した項目のみ変更されます。空欄の項目は元の値を維持します。",
                "Batch mode: only filled fields are updated; blank fields keep current values."
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
                tr("请先开始游戏或读取存档后再修改人材数值", "ゲームを開始またはロードしてから編集してください", "Start or load a game first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val arr = j.optJSONArray("ddWorkers")
        val totalCnt = j.optInt("workerCount", 0) + j.optInt("garageCount", 0)
        if (totalCnt <= 0 || arr == null || arr.length() == 0) {
            Toast.makeText(
                activity,
                tr("当前店面与库房暂无春销人材", "現在ハルウリ人材がいません", "No brothel workers yet"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        data class WorkerTarget(val scope: Int, val index: Int, val obj: JSONObject?, val label: String)
        val options = mutableListOf(
            WorkerTarget(0, -1, null, tr("批量修改：店面全体人材", "一括編集：店舗の全人材", "Batch: All Shop Workers")),
            WorkerTarget(1, -1, null, tr("批量修改：库房全体人材", "一括編集：保管庫の全人材", "Batch: All Garage Workers")),
            WorkerTarget(2, -1, null, tr("批量修改：店面与库房全体人材", "一括編集：全在籍人材", "Batch: All Workers"))
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
        AlertDialog.Builder(activity)
            .setTitle(tr("选择要修改的春销人材", "編集するハルウリ人材を選択", "Select Brothel Worker"))
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
            "评级 0~9 对应 D C C+ B B+ A A+ S S+ SS · 稀有度 0~5 对应 N R SR SSR SSSR UR · 状态 0为否 1为是${if (!single) " · 批量模式留空项保持原值" else ""}",
            "ランク 0~9 は D〜SS · レア度 0~5 は N〜UR · 状態 0=OFF 1=ON",
            "Rank 0~9 = D..SS · Rarity 0~5 = N..UR · State 0=No 1=Yes"
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

        val builder = AlertDialog.Builder(activity)
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

        AlertDialog.Builder(activity)
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
            title = tr("字体大小修改", "文字サイズ変更", "Font Size"),
            description = tr(
                "松开滑块或点击预设即可在下方画面实时预览文字缩放，范围 75%~160%，默认 100%，不改动存档文件。",
                "スライダーやプリセットで画面内の文字倍率を即時プレビューできます。範囲 75%~160%、標準 100%。",
                "Release slider or tap a preset to preview font scale live. Range 75% to 160%. Default 100%."
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
                    tr("字体大小已恢复默认 100%", "文字サイズを 100% に戻しました", "Font size reset to 100%")
                } else {
                    tr("字体大小已设为 $pct%", "文字サイズを $pct% に設定しました", "Font size set to $pct%")
                }
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showScrollbarScaleDialog() {
        showScaleSliderDialog(
            title = tr("滚动条修改", "スクロールバー変更", "Scrollbar Size"),
            description = tr(
                "松开滑块或点击预设即可实时放大游戏内列表与日志滚动条宽度及滑块触控区域，范围 100%~300%，默认 100%。",
                "リストやログのスクロールバー幅とつまみ操作領域を即時拡大します。範囲 100%~300%、標準 100%。",
                "Enlarge in-game scrollbar width and touch thumb area live from 100% to 300%. Default 100%."
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
                    tr("滚动条比例已恢复默认 100%", "スクロールバーを 100% に戻しました", "Scrollbar reset to 100%")
                } else {
                    tr("滚动条比例已设为 $pct%", "スクロールバーを $pct% に設定しました", "Scrollbar set to $pct%")
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

        AlertDialog.Builder(activity)
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
