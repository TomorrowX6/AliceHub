package io.github.kichikuou.xsystem4

import org.json.JSONObject

data class TrText(val zh: String, val ja: String, val en: String) {
    fun resolve(isZh: Boolean, isJa: Boolean): String = when {
        isZh -> zh
        isJa -> ja
        else -> en
    }
}

data class ModLockSpec(val bit: Int, val label: TrText)

data class ModFieldSpec(val key: String, val label: TrText)

data class ModCheatSpec(
    val label: TrText,
    val jsonAction: Map<String, Any>? = null,
    val s35Action: String? = null
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        jsonAction?.forEach { (k, v) ->
            when (v) {
                is Map<*, *> -> {
                    val sub = JSONObject()
                    v.forEach { (sk, sv) -> if (sk is String && sv != null) sub.put(sk, sv) }
                    obj.put(k, sub)
                }
                else -> obj.put(k, v)
            }
        }
        return obj.toString()
    }
}

data class GenericModifierSpec(
    val id: String,
    val name: TrText,
    val isSystem35: Boolean = false,
    val locks: List<ModLockSpec>,
    val resources: List<ModFieldSpec> = emptyList(),
    val cheats: List<ModCheatSpec> = emptyList(),
    val charFields: List<ModFieldSpec> = emptyList(),
    val charBoost: Map<String, Any> = emptyMap(),
    val summaryFields: List<Pair<String, TrText>> = emptyList()
) {
    val allLocksMask: Int
        get() = locks.fold(0) { acc, l -> acc or l.bit }
}

object GenericModifierRegistry {
    private fun t(zh: String, ja: String, en: String) = TrText(zh, ja, en)
    private fun lock(bitIndex: Int, zh: String, ja: String, en: String) =
        ModLockSpec(1 shl bitIndex, TrText(zh, ja, en))

    private fun field(key: String, zh: String, ja: String, en: String) =
        ModFieldSpec(key, TrText(zh, ja, en))

    private fun cheat(zh: String, ja: String, en: String, vararg pairs: Pair<String, Any>) =
        ModCheatSpec(TrText(zh, ja, en), jsonAction = linkedMapOf(*pairs))

    private fun s35Cheat(zh: String, ja: String, en: String, cmd: String) =
        ModCheatSpec(TrText(zh, ja, en), s35Action = cmd)

    private fun boostCheat(boost: Map<String, Any>) = ModCheatSpec(
        t("全员一键强化", "全員一括強化", "Boost All Characters"),
        jsonAction = mapOf("exCharEdit" to (mapOf("target" to -1) + boost))
    )

    private val fLv = field("lv", "等级", "レベル", "Level")
    private val fLvMax = field("lvMax", "等级上限", "レベル上限", "Level Cap")
    private val fExp = field("exp", "经验", "経験値", "EXP")
    private val fHp = field("hp", "当前 HP", "現在 HP", "Current HP")
    private val fHpMax = field("hpMax", "最大 HP", "最大 HP", "Max HP")
    private val fSp = field("sp", "当前 SP", "現在 SP", "Current SP")
    private val fSpMax = field("spMax", "最大 SP", "最大 SP", "Max SP")
    private val fMp = field("mp", "当前 MP", "現在 MP", "Current MP")
    private val fMpMax = field("mpMax", "最大 MP", "最大 MP", "Max MP")
    private val fAp = field("ap", "行动点", "行動力", "AP")
    private val fApMax = field("apMax", "行动上限", "行動上限", "Max AP")
    private val fAtk = field("atk", "攻击", "攻撃", "ATK")
    private val fDef = field("def", "防御", "防御", "DEF")
    private val fMag = field("mag", "魔力", "魔力", "MAG")
    private val fRes = field("res", "魔防", "魔防", "RES")
    private val fSpd = field("spd", "速度", "速度", "SPD")
    private val fHit = field("hit", "命中", "命中", "HIT")
    private val fEva = field("eva", "回避", "回避", "EVA")
    private val fLove = field("love", "好感度", "好感度", "Affection")
    private val fGold = field("gold", "金钱", "所持金", "Gold")

    private val evenicle2Boost = mapOf(
        "lv" to 99, "hp" to 9999, "hpMax" to 9999, "atk" to 999, "mag" to 999,
        "def" to 999, "spd" to 999, "skillP" to 999, "bp" to 5, "fullHeal" to true
    )

    private fun makeEvenicle2Spec(id: String, name: TrText) = GenericModifierSpec(
        id = id,
        name = name,
        locks = listOf(
            lock(0, "禁止随机遇敌", "ランダムエンカウント無効", "Disable Encounters"),
            lock(1, "我方全员满 HP", "味方全員HP最大", "Party Full HP"),
            lock(2, "无限技能点", "スキルP無限", "Infinite Skill Points"),
            lock(3, "无限 BP", "BP無限", "Infinite BP"),
            lock(4, "敌方 1 HP 秒杀", "敵全員HP1", "Enemy 1 HP"),
            lock(5, "无限所持金", "所持金無限", "Infinite Gold")
        ),
        resources = listOf(fGold),
        cheats = listOf(
            cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
            cheat("金钱设为 99999999", "所持金 99999999", "Set Gold 99,999,999", "gold" to 99_999_999),
            cheat("全员回满 HP 与 BP", "全員HP・BP全回復", "Full Heal & BP", "cheat" to "fullHeal"),
            cheat("敌方 HP 设为 1", "敵HPを1にする", "Enemy HP = 1", "cheat" to "enemy1Hp"),
            cheat("已持有道具补满 99", "所持アイテム99補充", "Refill Held Items to 99", "cheat" to "maxItems"),
            boostCheat(evenicle2Boost)
        ),
        charFields = listOf(
            fLv, fHp, fHpMax, fAtk, fMag, fDef, fSpd, fExp,
            field("skillP", "技能点", "スキルP", "Skill P"),
            field("bp", "BP", "BP", "BP")
        ),
        charBoost = evenicle2Boost,
        summaryFields = listOf("gold" to t("金", "金", "Gold"))
    )

    private val okusamaBoost = mapOf(
        "lv" to 99, "hp" to 9999, "hpMax" to 9999, "mp" to 999, "mpMax" to 999,
        "atk" to 999, "def" to 999, "fullHeal" to true
    )

    private fun makeOkusamaSpec(id: String, name: TrText) = GenericModifierSpec(
        id = id,
        name = name,
        locks = listOf(
            lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
            lock(1, "全员满 HP 与 MP", "全員HP・MP最大", "Party Full HP & MP")
        ),
        resources = listOf(fGold),
        cheats = listOf(
            cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
            cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
            cheat("全员回满 HP 与 MP", "全員HP・MP全回復", "Full Heal HP & MP", "cheat" to "fullHeal"),
            boostCheat(okusamaBoost)
        ),
        charFields = listOf(fLv, fExp, fHp, fHpMax, fMp, fMpMax, fAtk, fDef),
        charBoost = okusamaBoost,
        summaryFields = listOf("gold" to t("金", "金", "Gold"))
    )

    val all: List<GenericModifierSpec> = listOf(
        // =====================================================================
        // System 4 Extended Games (23 games)
        // =====================================================================
        run {
            val boost = mapOf(
                "lv" to 99, "lvMax" to 99, "hp" to 9999, "hpMax" to 9999,
                "ap" to 99, "apMax" to 99, "atk" to 999, "def" to 999,
                "mag" to 999, "res" to 999, "hit" to 200, "eva" to 100, "fullHeal" to true
            )
            GenericModifierSpec(
                id = GameCatalog.ID_RANCE06,
                name = t("兰斯 6", "ランス6", "Rance 06"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "我方满 HP 与行动力", "味方HP・活動力最大", "Party Full HP & AP"),
                    lock(2, "敌方 1 HP 秒杀", "敵全員HP1", "Enemy 1 HP"),
                    lock(3, "无限消耗品", "消費アイテム無限", "Infinite Items"),
                    lock(4, "最高开运值", "開運最大", "Max Luck")
                ),
                resources = listOf(fGold, field("luck", "开运值", "開運", "Luck")),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("全员回满 HP 与行动力", "全員HP・活動力全回復", "Full Heal Party", "cheat" to "fullHeal"),
                    cheat("敌方 HP 设为 1", "敵HPを1にする", "Enemy HP = 1", "cheat" to "enemy1Hp"),
                    cheat("持有道具补满 99", "所持アイテム99補充", "Refill Held Items to 99", "cheat" to "maxItems"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fLvMax, fHp, fHpMax, fAp, fApMax, fExp, fAtk, fDef, fMag, fRes, fHit, fEva),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "luck" to t("运", "運", "Luck"))
            )
        },
        makeEvenicle2Spec(GameCatalog.ID_EVENICLE2, t("夏娃年代记 2", "イブニクル2", "Evenicle 2")),
        makeEvenicle2Spec(GameCatalog.ID_EVENICLE2RANCE, t("夏娃年代记 2 兰斯版", "イブニクル2 ランス", "Evenicle 2 Rance")),
        run {
            val boost = mapOf(
                "lv" to 99, "hp" to 9999, "hpMax" to 9999, "vit" to 999, "vitMax" to 999,
                "atk" to 999, "hit" to 200, "eva" to 200, "love" to 100, "fullHeal" to true
            )
            GenericModifierSpec(
                id = GameCatalog.ID_DAIBANCHOU,
                name = t("大番长", "大番長", "Daibanchou"),
                locks = listOf(
                    lock(0, "无限资金", "資金無限", "Infinite Gold"),
                    lock(1, "部下满体力与精力", "配下HP・気力最大", "Full Servant HP & Vit"),
                    lock(2, "部下满忠诚度", "忠誠度最大", "Max Loyalty"),
                    lock(3, "锁定回合为 1", "ターン1固定", "Lock Turn to 1")
                ),
                resources = listOf(fGold, field("turn", "回合", "ターン", "Turn")),
                cheats = listOf(
                    cheat("资金 +10 万", "資金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("资金设为 9999999", "資金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("部下满状态与忠诚", "配下全回復・忠誠最大", "Full Heal & Loyalty", "cheat" to "fullHeal"),
                    boostCheat(boost)
                ),
                charFields = listOf(
                    fLv, fExp, fHp, fHpMax,
                    field("vit", "当前精力", "現在気力", "Vit"),
                    field("vitMax", "最大精力", "最大気力", "Max Vit"),
                    fAtk, fHit, fEva, field("love", "忠诚度", "忠誠度", "Loyalty")
                ),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "turn" to t("T", "T", "T"))
            )
        },
        run {
            val boost = mapOf("lv" to 99, "hp" to 9999, "hpMax" to 9999, "atk" to 999, "scout" to 999, "fullHeal" to true)
            GenericModifierSpec(
                id = GameCatalog.ID_DAITEIKOKU,
                name = t("大帝国", "大帝国", "Daiteikoku"),
                locks = listOf(
                    lock(0, "无限资源", "資源無限", "Infinite Resources"),
                    lock(1, "无限技术", "技術無限", "Infinite Technology"),
                    lock(2, "我方提督满 HP", "提督HP最大", "Full Admiral HP"),
                    lock(3, "我方无限行动", "味方無限行動", "Unlimited Actions"),
                    lock(4, "我方星域满治安", "自国治安100", "Max Star Peace")
                ),
                resources = listOf(
                    field("resource", "资源", "資源", "Resource"),
                    field("technology", "技术", "技術", "Technology")
                ),
                cheats = listOf(
                    cheat("资源 +10 万", "資源 +10万", "Resource +100K", "addResource" to 100_000),
                    cheat("技术 +10 万", "技術 +10万", "Tech +100K", "addTechnology" to 100_000),
                    cheat("资源与技术拉满", "資源・技術最大", "Max Resource & Tech", "resource" to 9_999_999, "technology" to 9_999_999),
                    cheat("提督回满 HP 并重置行动", "提督全回復・行動リセット", "Heal & Reset Actions", "cheat" to "fullHeal"),
                    cheat("解锁全部战舰并补满 99", "全艦船解放・99補充", "Unlock All Ships x99", "cheat" to "unlockShips"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fExp, fHp, fHpMax, fAtk, field("scout", "索敌", "索敵", "Scout")),
                charBoost = boost,
                summaryFields = listOf("resource" to t("资源", "資源", "Res"), "technology" to t("技术", "技術", "Tech"))
            )
        },
        run {
            val boost = mapOf("lv" to 99, "hp" to 9999, "atk" to 999, "def" to 999, "mag" to 999, "res" to 999, "eva" to 100, "love" to 999)
            GenericModifierSpec(
                id = GameCatalog.ID_GALZOO,
                name = t("萌兽岛", "GALZOOアイランド", "Galzoo Island"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "敌方 1 HP 秒杀", "敵全員HP1", "Enemy 1 HP"),
                    lock(2, "100% 掉落", "ドロップ率100%", "100% Drop Rate"),
                    lock(3, "禁止随机遇敌", "エンカウント無効", "No Encounters"),
                    lock(4, "无限消耗品", "消費アイテム無限", "Infinite Items")
                ),
                resources = listOf(fGold, field("exp", "迷宫经验", "迷宮EXP", "Dungeon EXP")),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("敌方 HP 设为 1", "敵HPを1にする", "Enemy HP = 1", "cheat" to "enemy1Hp"),
                    cheat("持有道具补满 99", "所持アイテム99補充", "Refill Held Items to 99", "cheat" to "maxItems"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fHp, fAtk, fDef, fMag, fRes, fEva, fLove),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "exp" to t("经验", "EXP", "EXP"))
            )
        },
        GenericModifierSpec(
            id = GameCatalog.ID_TOUSHIN3,
            name = t("斗神都市 III", "闘神都市III", "Toushin Toshi III"),
            locks = listOf(
                lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                lock(1, "主角满 HP", "主人公HP最大", "Full Hero HP"),
                lock(2, "敌方 1 HP 秒杀", "敵HP1", "Enemy 1 HP"),
                lock(3, "禁止随机遇敌", "エンカウント無効", "No Encounters"),
                lock(4, "无限消耗品", "消費アイテム無限", "Infinite Items")
            ),
            resources = listOf(fGold, fLv, fHp, fHpMax, fAtk, fDef, fHit, fEva, fExp),
            cheats = listOf(
                cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                cheat("主角回满 HP", "主人公HP全回復", "Full Heal Hero", "cheat" to "fullHeal"),
                cheat("敌方 HP 设为 1", "敵HPを1にする", "Enemy HP = 1", "cheat" to "enemy1Hp"),
                cheat("持有道具补满 99", "所持アイテム99補充", "Refill Held Items to 99", "cheat" to "maxItems"),
                cheat("主角一键强化", "主人公一括強化", "Boost Hero Stats", "cheat" to "boostAll")
            ),
            summaryFields = listOf("gold" to t("金", "金", "Gold"), "hp" to t("HP", "HP", "HP"))
        ),
        run {
            val boost = mapOf("lv" to 99, "hp" to 9999, "hpMax" to 9999, "atk" to 999, "def" to 999, "hit" to 200, "eva" to 200, "move" to 15, "fullHeal" to true)
            GenericModifierSpec(
                id = GameCatalog.ID_BLADEBRIDERS,
                name = t("武想少女", "ブレードブライダース", "Blade Briders"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "我方满 HP", "味方HP最大", "Party Full HP"),
                    lock(2, "敌方 1 HP 秒杀", "敵HP1", "Enemy 1 HP"),
                    lock(3, "无限行动点数", "行動P無限", "Infinite AP"),
                    lock(4, "开启成长加成", "成長ボーナス有効", "Growth Bonus ON")
                ),
                resources = listOf(fGold, fAp),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("我方回满 HP", "味方HP全回復", "Full Heal Party", "cheat" to "fullHeal"),
                    cheat("敌方 HP 设为 1", "敵HPを1にする", "Enemy HP = 1", "cheat" to "enemy1Hp"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fExp, fHp, fHpMax, fAtk, fDef, fHit, fEva, field("move", "移动力", "移動力", "Move")),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "ap" to t("AP", "AP", "AP"))
            )
        },
        run {
            val boost = mapOf("lv" to 99, "hp" to 9999, "hpMax" to 9999, "atk" to 999, "spd" to 999, "hit" to 999, "mag" to 999, "fullHeal" to true)
            GenericModifierSpec(
                id = GameCatalog.ID_YORUGAKURU,
                name = t("夜来了！", "夜が来る！", "Yoru ga Kuru!"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "我方满 HP", "味方HP最大", "Party Full HP"),
                    lock(2, "免疫异常状态", "状態異常無効", "Debuff Immunity"),
                    lock(3, "开启休闲双倍收益", "カジュアル強化有効", "Casual Mode Boost")
                ),
                resources = listOf(fGold),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 999999", "所持金 999999", "Set Gold 999,999", "gold" to 999_999),
                    cheat("全员回血并清除异常", "全員全回復・異常解除", "Full Heal & Cure", "cheat" to "fullHeal"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fHp, fHpMax, fExp, fAtk, fSpd, fHit, fMag),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"))
            )
        },
        run {
            val boost = mapOf("lv" to 99, "hp" to 9999, "hpMax" to 9999, "atk" to 999, "def" to 999, "mag" to 999, "res" to 999, "hit" to 999, "fullHeal" to true)
            GenericModifierSpec(
                id = GameCatalog.ID_WAIDONYO,
                name = t("猫娘大冒险", "わいどにょ", "Waidonyo"),
                locks = listOf(
                    lock(0, "我方满 HP", "味方HP最大", "Party Full HP"),
                    lock(1, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(2, "敌方 1 HP 秒杀", "敵HP1", "Enemy 1 HP")
                ),
                resources = listOf(fGold),
                cheats = listOf(
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("我方回满 HP", "味方HP全回復", "Full Heal Party", "cheat" to "fullHeal"),
                    cheat("敌方 HP 设为 1", "敵HPを1にする", "Enemy HP = 1", "cheat" to "enemy1Hp"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fHp, fHpMax, fAtk, fDef, fMag, fRes, fHit),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"))
            )
        },
        run {
            val boost = mapOf("hp" to 9999, "hpMax" to 9999, "atk" to 999, "def" to 999, "mag" to 999, "spd" to 999, "fullHeal" to true)
            GenericModifierSpec(
                id = GameCatalog.ID_SANBIKI,
                name = t("三匹的野望", "三匹が斬る！", "Sanbiki"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "我方满 HP", "味方HP最大", "Party Full HP"),
                    lock(2, "满幸运且零厄运", "幸運最大・不運0", "Max Luck & 0 Bad Luck"),
                    lock(3, "无限消耗品", "消費アイテム無限", "Infinite Items")
                ),
                resources = listOf(fGold, field("luck", "幸运", "幸運", "Luck")),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("全员回满 HP", "全員HP全回復", "Full Heal Party", "cheat" to "fullHeal"),
                    cheat("持有道具补满", "所持アイテム補充", "Refill Held Items", "cheat" to "maxItems"),
                    boostCheat(boost)
                ),
                charFields = listOf(fHp, fHpMax, fAtk, fDef, fMag, fSpd),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "luck" to t("运", "運", "Luck"))
            )
        },
        run {
            val boost = mapOf("lv" to 99, "hp" to 9999, "hpMax" to 9999, "atk" to 999, "love" to 100, "fullHeal" to true)
            GenericModifierSpec(
                id = GameCatalog.ID_DORAPEKO,
                name = t("饥饿的龙", "どらぺこ！", "Dorapeko"),
                locks = listOf(
                    lock(0, "我方满 HP", "味方HP最大", "Party Full HP"),
                    lock(1, "满好感度", "好感度最大", "Max Affection"),
                    lock(2, "我方无限行动", "味方無限行動", "Unlimited Actions")
                ),
                cheats = listOf(
                    cheat("全员回满 HP 并重置行动", "全員全回復・行動リセット", "Full Heal & Reset Actions", "cheat" to "fullHeal"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fHp, fHpMax, fAtk, fLove, fExp),
                charBoost = boost
            )
        },
        run {
            val boost = mapOf("hp" to 100, "skill" to 999, "visual" to 999)
            GenericModifierSpec(
                id = GameCatalog.ID_HARUURARE,
                name = t("春风物语", "はるうられ", "Haruurare"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "成员满体力", "メンバー体力最大", "Full Member HP"),
                    lock(2, "最高信用等级", "信用度最大", "Max Credit")
                ),
                resources = listOf(
                    fGold,
                    field("credit", "信用点数", "信用ポイント", "Credit"),
                    field("quota", "上缴额度", "上納額", "Quota")
                ),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("上缴额度清零", "上納額ゼロ", "Zero Quota", "quota" to 0),
                    cheat("全员体力回满", "全員体力全回復", "Full Heal Members", "cheat" to "fullHeal"),
                    cheat("解锁全部商品购买", "全アイテム購入解放", "Unlock All Shop Items", "cheat" to "unlockItems"),
                    boostCheat(boost)
                ),
                charFields = listOf(
                    fHp,
                    field("skill", "技巧", "テクニック", "Skill"),
                    field("visual", "魅力", "ビジュアル", "Visual")
                ),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "credit" to t("信用", "信用", "Credit"))
            )
        },
        GenericModifierSpec(
            id = GameCatalog.ID_OYAKORANKAN,
            name = t("母女乱馆", "母娘乱館", "Oyakorankan"),
            locks = listOf(
                lock(0, "满爱情防卫值", "ラブガード最大", "Max Love Guard"),
                lock(1, "满星级评价", "勝ち星最大", "Max Stars"),
                lock(2, "清空受孕槽", "受精ゲージ0", "Zero Fertilization"),
                lock(3, "敌方能力清零", "敵能力ゼロ", "Weaken Enemies")
            ),
            cheats = listOf(
                cheat("全员满防卫与星级并清零受孕槽", "全員全回復・受精ゼロ", "Full Guard & Clear Fert", "cheat" to "fullHeal"),
                cheat("敌方攻击与精力清零", "敵の攻撃・精力ゼロ", "Weaken Enemies", "cheat" to "weakenEnemy")
            ),
            charFields = listOf(
                field("hp", "防卫值", "ラブガード", "Love Guard"),
                field("star", "星级", "勝ち星", "Stars"),
                field("fert", "受孕槽", "受精ゲージ", "Fert Gauge")
            ),
            charBoost = mapOf("hp" to 999, "star" to 99, "fert" to 0),
            summaryFields = listOf("day" to t("第", "日", "Day"))
        ),
        GenericModifierSpec(
            id = GameCatalog.ID_TSUMASHIBORI,
            name = t("榨精人妻", "妻しぼり", "Tsumashibori"),
            locks = listOf(
                lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                lock(1, "满体力与精力", "体力・精力最大", "Full HP & SP"),
                lock(2, "女主满好感零嫉妒", "ヒロイン愛情最大・嫉妬0", "Max Love & 0 Jealousy"),
                lock(3, "情敌精力清零", "ライバル精力0", "Weaken Rivals"),
                lock(4, "无限消耗品", "アイテム無限", "Infinite Items")
            ),
            resources = listOf(fGold, fHp, fHpMax, fSp, fSpMax, fLv, fExp),
            cheats = listOf(
                cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                cheat("回满体力精力与好感", "体力・精力・愛情回復", "Full Heal & Love", "cheat" to "fullHeal"),
                cheat("情敌精力与好感清零", "ライバル無力化", "Weaken Rivals", "cheat" to "weakenEnemy"),
                cheat("持有道具补满 99", "所持アイテム99補充", "Refill Held Items to 99", "cheat" to "maxItems")
            ),
            summaryFields = listOf("gold" to t("金", "金", "Gold"), "hp" to t("体力", "体力", "HP"))
        ),
        makeOkusamaSpec(GameCatalog.ID_OKUSAMAJUN, t("人妻管理员 纯爱篇", "奥様管理人 純愛", "Okusama Junai")),
        makeOkusamaSpec(GameCatalog.ID_OKUSAMANTR, t("人妻管理员 NTR篇", "奥様管理人 NTR", "Okusama NTR")),
        GenericModifierSpec(
            id = GameCatalog.ID_ESCALAYER_REBOOT,
            name = t("超昂天使 R", "エスカレイヤーR", "Escalayer Reboot"),
            locks = listOf(
                lock(0, "满能量与 Libido", "エネルギー・リビドー最大", "Max Energy & Libido"),
                lock(1, "满和平点与爱情", "ピース・愛情最大", "Max Peace & Love"),
                lock(2, "常驻 Boost 强化", "ブースト常時有効", "Always Boosted"),
                lock(3, "强化护盾与攻防速", "シールド・能力強化", "Boost Combat Stats")
            ),
            resources = listOf(
                field("peace", "和平点", "ピース", "Peace"),
                field("love", "爱情", "愛情", "Love"),
                field("libido", "Libido", "リビドー", "Libido"),
                field("energy", "能量", "エネルギー", "Energy"),
                field("shield", "护盾", "シールド", "Shield"),
                fAtk, fDef, fSpd
            ),
            cheats = listOf(
                cheat("和平点 +50000", "ピース +50000", "Peace +50,000", "addPeace" to 50_000),
                cheat("回满能量并开启 Boost", "エネルギー全快・ブースト", "Full Energy & Boost", "cheat" to "fullHeal"),
                cheat("全属性一键强化", "全ステータス強化", "Boost All Stats", "cheat" to "boostAll")
            ),
            summaryFields = listOf("peace" to t("和平", "P", "Peace"), "energy" to t("能量", "EN", "EN"))
        ),
        run {
            val boost = mapOf("hp" to 9999, "hpMax" to 9999, "atk" to 999, "def" to 999, "spd" to 999, "energy" to 100, "love" to 999, "pollute" to 0)
            GenericModifierSpec(
                id = GameCatalog.ID_IXSEAL,
                name = t("超昂神骑", "イクシール", "Ixseal"),
                locks = listOf(
                    lock(0, "女主满 HP 与能量", "ヒロインHP・エネルギー最大", "Full HP & Energy"),
                    lock(1, "清除污染度", "汚染度ゼロ", "Zero Pollution"),
                    lock(2, "女主满好感度", "好感度最大", "Max Favorability"),
                    lock(3, "无限魔石板", "マジックプレート無限", "Infinite Magic Plates")
                ),
                resources = listOf(
                    field("gold", "魔石板", "マジックプレート", "Magic Plates"),
                    field("energy", "能源", "エネルギー", "Energy")
                ),
                cheats = listOf(
                    cheat("魔石板 +50000", "マジックプレート +50000", "Magic Plates +50K", "addGold" to 50_000),
                    cheat("全员回满 HP/能量并清除污染", "全員全回復・汚染除去", "Full Heal & Purify", "cheat" to "fullHeal"),
                    boostCheat(boost)
                ),
                charFields = listOf(
                    fHp, fHpMax, fAtk, fDef, fSpd,
                    field("energy", "能量", "エネルギー", "Energy"),
                    fLove,
                    field("pollute", "污染度", "汚染度", "Pollution")
                ),
                charBoost = boost,
                summaryFields = listOf("gold" to t("魔石", "板", "Plate"), "energy" to t("能源", "EN", "EN"))
            )
        },
        run {
            val boost = mapOf("lv" to 99, "bp" to 999, "hp" to 9999, "sp" to 999)
            GenericModifierSpec(
                id = GameCatalog.ID_PASCHA3,
                name = t("粉彩铃音 3", "パステルチャイム3", "Pastel Chime 3"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "无限行动点数", "行動P無限", "Infinite AP"),
                    lock(2, "全员满 HP/SP 与士气", "全員HP・SP・テンション最大", "Full HP/SP & Tension"),
                    lock(3, "女主满好感度", "ヒロイン好感度最大", "Max Favorability"),
                    lock(4, "无限消耗品", "アイテム無限", "Infinite Items")
                ),
                resources = listOf(fGold, fAp),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("全员回满 HP/SP 与 AP", "全員HP・SP・AP全回復", "Full Heal & AP", "cheat" to "fullHeal"),
                    cheat("持有道具补满 99", "所持アイテム99補充", "Refill Held Items to 99", "cheat" to "maxItems"),
                    boostCheat(boost)
                ),
                charFields = listOf(fLv, fExp, field("bp", "BP", "BP", "BP"), fHp, fSp),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "ap" to t("AP", "AP", "AP"))
            )
        },
        GenericModifierSpec(
            id = GameCatalog.ID_HAHARANMAN,
            name = t("母烂漫", "母爛漫", "Haharanman"),
            locks = listOf(
                lock(0, "无限行动点数", "行動P無限", "Infinite AP"),
                lock(1, "满精神与恢复点", "メンタル・回復P最大", "Max Mental & Refresh"),
                lock(2, "开启免消耗与经验加成", "消費無効・経験値UP", "No Spend & EXP Boost"),
                lock(3, "解除时段与行动限制", "時間帯・制限解除", "Remove Restrictions")
            ),
            resources = listOf(
                fAp, fLv, fExp,
                field("mental", "精神力", "メンタル", "Mental"),
                field("refresh", "恢复点数", "リフレッシュP", "Refresh Point"),
                field("days", "剩余天数", "残り日数", "Days Left")
            ),
            cheats = listOf(
                cheat("回满行动点与精神", "行動P・メンタル全回復", "Full AP & Mental", "cheat" to "fullHeal"),
                cheat("开启全部官方辅助", "全アシスト機能有効化", "Enable All Assists", "cheat" to "boostAll")
            ),
            summaryFields = listOf("ap" to t("AP", "AP", "AP"), "mental" to t("精神", "心", "Mental"))
        ),
        run {
            val boost = mapOf("lv" to 99, "love" to 999, "rival" to 0)
            GenericModifierSpec(
                id = GameCatalog.ID_TSUMAMIGUI3,
                name = t("妻中蜜 3", "妻みぐい3", "Tsumamigui 3"),
                locks = listOf(
                    lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                    lock(1, "无限精力", "精力無限", "Infinite Energy"),
                    lock(2, "清除情敌痕迹", "ライバル値ゼロ", "Clear Rival Traces"),
                    lock(3, "无限消耗品", "アイテム無限", "Infinite Items")
                ),
                resources = listOf(fGold, field("energy", "精力", "精力", "Energy")),
                cheats = listOf(
                    cheat("金钱 +10 万", "所持金 +10万", "Gold +100K", "addGold" to 100_000),
                    cheat("金钱设为 9999999", "所持金 9999999", "Set Gold 9,999,999", "gold" to 9_999_999),
                    cheat("回满精力并清除情敌", "精力全快・ライバル除去", "Full Energy & Clear Rival", "cheat" to "fullHeal"),
                    cheat("持有道具补满 99", "所持アイテム99補充", "Refill Held Items to 99", "cheat" to "maxItems"),
                    boostCheat(boost)
                ),
                charFields = listOf(
                    fLv, fLove,
                    field("seed", "主角进度", "進行値", "Seed"),
                    field("rival", "情敌值", "敵値", "Rival")
                ),
                charBoost = boost,
                summaryFields = listOf("gold" to t("金", "金", "Gold"), "energy" to t("精力", "精力", "EN"))
            )
        },
        GenericModifierSpec(
            id = GameCatalog.ID_MAMAN,
            name = t("再婚妈妈", "は〜とふるママン", "Heartful Maman"),
            locks = listOf(
                lock(0, "无限道具", "アイテム無限", "Infinite Items"),
                lock(1, "满野性与心情", "ワイルド・機嫌最大", "Max Wild & Humor"),
                lock(2, "女主满好感度", "好感度最大", "Max Fondness")
            ),
            resources = listOf(
                field("condom", "道具数", "アイテム数", "Items"),
                field("wild", "野性度", "ワイルド", "Wild"),
                field("mcon", "恋母度", "マザコン度", "Mother Complex"),
                field("humor", "心情", "機嫌", "Humor")
            ),
            cheats = listOf(
                cheat("补满道具与心情", "アイテム・機嫌全快", "Refill Items & Humor", "cheat" to "fullHeal"),
                cheat("全属性与好感强化", "全能力・好感度最大", "Boost All & Fondness", "cheat" to "boostAll")
            ),
            summaryFields = listOf("condom" to t("道具", "道具", "Item"), "wild" to t("野性", "W", "Wild"))
        ),

        // =====================================================================
        // System 3.5 Extended Games (5 games; Escalayer keeps its dedicated UI)
        // =====================================================================
        GenericModifierSpec(
            id = GameCatalog.ID_DAIAKUJI,
            name = t("大恶司", "大悪司", "Daiakuji"),
            isSystem35 = true,
            locks = listOf(
                lock(0, "无限资金", "資金無限", "Infinite Gold"),
                lock(1, "债务清零", "借金ゼロ", "Zero Debt"),
                lock(2, "全员高体力", "全員HP維持", "Keep High HP"),
                lock(3, "满好感与人气", "愛情・人気最大", "Max Love & Popularity")
            ),
            cheats = listOf(
                s35Cheat("资金 +10000", "資金 +10000", "Gold +10,000", "da_gold_add=10000"),
                s35Cheat("资金设为 60000", "資金 60000", "Max Gold (60,000)", "da_gold_max"),
                s35Cheat("债务清零", "借金ゼロ", "Clear Debt", "da_debt_zero"),
                s35Cheat("全员体力强化 999", "全員HP999強化", "Boost All HP to 999", "da_hp_boost"),
                s35Cheat("满好感与店铺人气", "愛情・人気最大", "Max Love & Popularity", "da_love_max"),
                s35Cheat("区域治安拉满", "治安レベル最大", "Max Region Security", "da_security_max"),
                s35Cheat("解锁 EX 道具", "EXアイテム解放", "Unlock EX Items", "da_items_unlock")
            ),
            summaryFields = listOf("gold" to t("资金", "金", "Gold"), "debt" to t("债务", "借", "Debt"))
        ),
        GenericModifierSpec(
            id = GameCatalog.ID_RANCE5D,
            name = t("兰斯 5D", "ランス5D", "Rance 5D"),
            isSystem35 = true,
            locks = listOf(
                lock(0, "全员满 HP", "全員HP最大", "Party Full HP"),
                lock(1, "无限迷宫行动", "迷宮行動回数無限", "Infinite Actions"),
                lock(2, "道具满耐久", "持ち物耐久最大", "Max Durability"),
                lock(3, "金块与经验玉满额", "金塊・超経験玉99", "Max Gold & EXP Orbs")
            ),
            cheats = listOf(
                s35Cheat("全员回满 HP", "全員HP全回復", "Full Heal Party", "r5d_heal"),
                s35Cheat("全员能力大幅强化", "全員ステータス強化", "Boost All Stats", "r5d_stats_boost"),
                s35Cheat("金块与经验玉补满 99", "金塊・経験玉99", "99 Gold Bars & EXP Orbs", "r5d_gold_exp"),
                s35Cheat("全女主好感度 100", "ヒロイン愛P最大", "Max Heroine Affection", "r5d_love_max"),
                s35Cheat("道具耐久度补满 99", "持ち物耐久99", "Max Item Durability", "r5d_durability")
            ),
            summaryFields = listOf("hp" to t("HP", "HP", "HP"), "ap" to t("行动", "行", "AP"))
        ),
        GenericModifierSpec(
            id = GameCatalog.ID_TSUMAMIGUI,
            name = t("妻中蜜", "妻みぐい", "Tsumamigui"),
            isSystem35 = true,
            locks = listOf(
                lock(0, "无限耐久力", "耐久力無限", "Infinite Stamina"),
                lock(1, "技巧等级全满", "技巧レベル最大", "Max Tech Levels")
            ),
            cheats = listOf(
                s35Cheat("耐久力设为 999", "耐久力 999", "Stamina = 999", "tm1_stamina_max"),
                s35Cheat("全技巧等级设为 10", "全技巧レベル10", "All Tech Lv 10", "tm1_tech_max"),
                s35Cheat("女主等级设为 10", "ヒロインレベル10", "Heroine Lv 10", "tm1_heroine_max"),
                s35Cheat("经验值 +500", "EXP +500", "EXP +500", "tm1_exp_add=500")
            ),
            summaryFields = listOf("stamina" to t("耐久", "耐久", "Sta"), "lv" to t("Lv", "Lv", "Lv"))
        ),
        GenericModifierSpec(
            id = GameCatalog.ID_KICHIKUOU,
            name = t("鬼畜王兰斯", "鬼畜王ランス", "Kichikuou Rance"),
            isSystem35 = true,
            locks = listOf(
                lock(0, "无限资金", "資金無限", "Infinite Gold"),
                lock(1, "我方无限行动", "味方無限行動", "Unlimited Actions")
            ),
            cheats = listOf(
                s35Cheat("资金 +1000（×10万G）", "資金 +1000", "Gold +1,000", "kr_gold_add=1000"),
                s35Cheat("资金拉满 60000", "資金最大", "Max Gold", "kr_gold_max"),
                s35Cheat("重置部队行动", "部隊行動リセット", "Reset Unit Actions", "kr_reset_actions")
            ),
            summaryFields = listOf("gold" to t("资金", "金", "Gold"), "month" to t("月", "月", "M"))
        ),
        GenericModifierSpec(
            id = GameCatalog.ID_KAERUNYO,
            name = t("青蛙王国", "かえるにょ・ぱにょ〜ん", "Kaerunyo Panyon"),
            isSystem35 = true,
            locks = listOf(
                lock(0, "无限金钱", "所持金無限", "Infinite Gold"),
                lock(1, "无限捕获绳", "捕獲ロープ無限", "Infinite Ropes")
            ),
            cheats = listOf(
                s35Cheat("金钱 +10000", "所持金 +10000", "Gold +10,000", "kn_gold_add=10000"),
                s35Cheat("金钱拉满 60000", "所持金最大", "Max Gold", "kn_gold_max"),
                s35Cheat("捕获绳与命中羽补满 99", "ロープ・羽 99補充", "Refill Ropes & Feathers", "kn_items_max")
            ),
            summaryFields = listOf("gold" to t("金", "金", "Gold"), "ropes" to t("绳", "縄", "Ropes"))
        )
    )

    private val byId: Map<String, GenericModifierSpec> = all.associateBy { it.id }

    @JvmStatic
    fun find(gameId: String?): GenericModifierSpec? {
        if (gameId.isNullOrBlank()) return null
        byId[gameId]?.let { return it }
        if (gameId == "escalayerreboot") return byId[GameCatalog.ID_ESCALAYER_REBOOT]
        return null
    }

    @JvmStatic
    fun isEvenicleFamily(gameId: String?): Boolean =
        gameId == GameCatalog.ID_EVENICLE ||
            gameId == GameCatalog.ID_EVENICLERANCE ||
            gameId == GameCatalog.ID_EVENICLEGAIDEN
}
