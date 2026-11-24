import Foundation

struct ModifierCharacter: Identifiable {
    let target: [String: Int]
    let name: String
    let state: [String: Any]
    var id: [String: Int] { target }
}

struct ModifierCharacters {
    let prefix: String
    let fields: [ModifierField]
    let boost: [String: Any]

    var title: String { prefix == "sr" ? "武将属性" : "角色属性" }

    func rows(in state: [String: Any]) -> [ModifierCharacter] {
        let isSengoku = prefix == "sr"
        let characters = (state["\(prefix)Characters"] as? [[String: Any]] ?? []).compactMap { row -> ModifierCharacter? in
            guard let index = ModifierBridge.integer(row[isSengoku ? "charIdx" : "index"]), index >= 0 else { return nil }
            var target = [isSengoku ? "charIdx" : "target": index]
            if isSengoku {
                guard let faction = ModifierBridge.integer(row["factionIdx"]) else { return nil }
                target["factionIdx"] = faction
            }
            let name = row["name"] as? String ?? ""
            return ModifierCharacter(target: target, name: name.isEmpty ? "#\(index)" : name, state: row)
        }
        guard let first = characters.first else { return [] }
        var target = [isSengoku ? "charIdx" : "target": -1]
        target["factionIdx"] = first.target["factionIdx"]
        return [ModifierCharacter(target: target, name: isSengoku ? "全体武将" : "全体角色", state: [:])] + characters
    }

    func action(for character: ModifierCharacter, values: [String: Any]) -> [String: Any] {
        var payload = values
        for (key, value) in character.target { payload[key] = value }
        return ["\(prefix)CharEdit": payload]
    }
}

struct ModifierGamePanel {
    let game: ModifierGame
    let resourceTitle: String
    let resources: [ModifierField]
    let resourceAction: ModifierCheat?
    let characters: ModifierCharacters?

    init(_ id: String, _ name: String, _ lockGame: modifier_lock_game, locks: [String],
         cheats: [ModifierCheat], resourceTitle: String = "资源修改", resources: [ModifierField],
         resourceAction: ModifierCheat? = nil, characters: ModifierCharacters? = nil) {
        game = ModifierGame(id: id, name: name, lockGame: lockGame, locks: Self.locks(locks),
                            cheats: cheats, supportsCheatCodes: false)
        self.resourceTitle = resourceTitle
        self.resources = resources
        self.resourceAction = resourceAction
        self.characters = characters
    }

    static func locks(_ titles: [String]) -> [ModifierLock] {
        titles.enumerated().map { ModifierLock(bit: 1 << $0.offset, title: $0.element) }
    }

    private static func cheat(_ title: String, _ action: [String: Any]) -> ModifierCheat {
        ModifierCheat(title: title, action: action, done: "已提交修改")
    }

    private static func resource(_ key: String, _ label: String, _ stateKey: String) -> ModifierField {
        ModifierField(key, label, stateKey: stateKey)
    }

    private static func goldCheats(_ prefix: String, max: Int, add: [Int] = [10_000, 100_000]) -> [ModifierCheat] {
        add.map { cheat("金钱 +\($0 / 10_000) 万", ["\(prefix)AddGold": $0]) }
            + [cheat("金钱设为 \(max)", ["\(prefix)Gold": max])]
    }

    private static func boostAll(_ characters: ModifierCharacters) -> ModifierCheat {
        let party = ModifierCharacter(target: ["target": -1], name: "", state: [:])
        return cheat("全员一键强化", characters.action(for: party, values: characters.boost))
    }

    private static let hp = [ModifierField("hp", "当前 HP"), ModifierField("hpMax", "最大 HP")]
    private static let level = ModifierField("lv", "等级")
    private static let experience = ModifierField("exp", "经验")
    private static let sr = ModifierCharacters(prefix: "sr", fields: [
        .init("troops", "当前兵力"), .init("troopsMax", "最大兵力"), .init("atk", "攻击"), .init("def", "防御"),
        .init("intel", "智力"), .init("spd", "速度"), .init("love", "好感度"), .init("wazaNow", "当前技数"),
        .init("wazaMax", "最大技数"), .init("resetAction", "重置行动（1）"),
    ], boost: ["troops": 9999, "troopsMax": 9999, "atk": 99, "def": 99, "intel": 99, "spd": 99, "love": 99, "resetAction": 1])
    private static let r02 = ModifierCharacters(prefix: "r02", fields: [level, experience] + hp + [
        .init("sp", "当前 SP"), .init("spMax", "最大 SP"), .init("atk", "攻击"), .init("def", "防御"),
        .init("know", "魔力知性"), .init("hit", "命中"), .init("eva", "回避"), .init("luck", "幸运"),
    ], boost: ["lv": 50, "hp": 999, "hpMax": 999, "sp": 200, "spMax": 200, "atk": 500, "def": 500, "know": 500,
               "hit": 200, "eva": 100, "luck": 99, "fullHeal": true])
    private static let r03 = ModifierCharacters(prefix: "r03", fields: hp + [level, experience,
        .init("str", "物理"), .init("mind", "魔法"), .init("dex", "灵巧"),
    ], boost: ["hp": 9999, "hpMax": 9999, "lv": 99, "str": 999, "mind": 999, "dex": 999])
    private static let r08 = ModifierCharacters(prefix: "r08", fields: hp + [level, .init("lvMax", "等级上限"), experience,
        .init("atk", "攻击"), .init("mag", "魔力"), .init("def", "物防"), .init("res", "魔防"), .init("spd", "速度"),
        .init("hit", "命中"), .init("eva", "回避"), .init("crt", "必杀率"), .init("skillPts", "技能点"),
    ], boost: ["hp": 9999, "hpMax": 9999, "lv": 99, "atk": 999, "mag": 999, "def": 999, "res": 999, "spd": 999,
               "hit": 999, "eva": 99, "crt": 100, "skillPts": 99, "fullHeal": true])
    private static let r09 = ModifierCharacters(prefix: "r09", fields: hp + [
        .init("atkMin", "最小破坏力"), .init("atkMax", "最大破坏力"), .init("magic", "魔力"), .init("resist", "异常耐性"),
        .init("move", "移动力"), .init("range", "射程"), .init("critical", "必杀率"), .init("speed", "行动速度"),
        .init("evadeMax", "回避上限"), .init("fendMax", "招架上限"), .init("armoredMax", "装甲上限"),
        .init("skillBonus", "技能强化点"),
    ], boost: ["hp": 9999, "hpMax": 9999, "atkMin": 5000, "atkMax": 9999, "magic": 9999, "resist": 999,
               "move": 12, "critical": 100, "speed": 999, "evadeMax": 999, "fendMax": 999, "armoredMax": 999, "fullHeal": true])

    static let all: [ModifierGamePanel] = [
        ModifierGamePanel("sengokurance", "战国兰斯", MODIFIER_LOCK_SENGOKURANCE,
            locks: ["无限金钱", "无限行动次数", "最大满足度", "我方满兵力与技数", "敌方 1 兵", "我方无限行动"],
            cheats: goldCheats("sr", max: 999999, add: [100_000, 1_000_000]) + [
                cheat("补满行动次数", ["srRefillAp": true]), cheat("行动次数设为 9", ["srApMax": 9, "srAp": 9]),
                cheat("满足度设为 999", ["srSatisfaction": 999]), cheat("兵数上限设为 9999", ["srTroopLimit": 9999]),
                srResetActions, srHealTroops, cheat("全军一键强化", ["srBoostAllParty": true]),
            ], resourceTitle: "资源与行动", resources: [
                resource("srGold", "金钱", "gold"), resource("srAp", "行动次数", "ap"),
                resource("srApMax", "行动上限", "apMax"), resource("srSatisfaction", "满足度", "satisfaction"),
                resource("srTroopLimit", "兵数上限", "troopLimit"),
            ], characters: sr),
        ModifierGamePanel("rance01", "Rance 01", MODIFIER_LOCK_RANCE01,
            locks: ["锁定满 HP", "敌方 1 HP", "无限金钱", "100% 掉落", "免疫异常状态"],
            cheats: goldCheats("r01", max: 999999) + [
                cheat("恢复满 HP", ["r01HealFull": true]), cheat("最大芯片容量", ["r01MaxChipCap": true]),
                cheat("兰斯一键强化", ["r01MaxLevelCap": 999, "r01Level": 50, "r01MaxHp": 999, "r01Hp": 999, "r01Luck": 99]),
                cheat("解锁鉴赏", ["r01UnlockGallery": true]),
            ], resourceTitle: "数值与芯片", resources: [
                resource("r01Gold", "金钱", "gold"), resource("r01Level", "等级", "level"),
                resource("r01MaxLevelCap", "等级上限", "maxLevel"), resource("r01Exp", "经验", "exp"),
                resource("r01Hp", "当前 HP", "hp"), resource("r01MaxHp", "最大 HP", "maxHp"), resource("r01Luck", "幸运", "luck"),
            ], resourceAction: cheat("扩充芯片并回满 HP", ["r01MaxChipCap": true, "r01HealFull": true])),
        ModifierGamePanel("rance02", "Rance 02", MODIFIER_LOCK_RANCE02,
            locks: ["我方满 HP 与 SP", "敌方一击必杀", "无限金钱", "无限消耗品"],
            cheats: goldCheats("r02", max: 999999, add: [10_000]) + [
                cheat("补满消耗品", ["r02SupplyItems": true]), cheat("全员回满 HP 与 SP", ["r02HealAll": true]),
                boostAll(r02), cheat("解锁鉴赏", ["r02UnlockGallery": true]),
            ], resourceTitle: "金币与因果律", resources: [
                resource("r02Gold", "金钱", "gold"), resource("r02Karma", "因果律", "karma"),
            ], resourceAction: cheat("补满道具并回血", ["r02SupplyItems": true, "r02HealAll": true]), characters: r02),
        ModifierGamePanel("rance03", "Rance 03", MODIFIER_LOCK_RANCE03,
            locks: ["无限体力", "技能零冷却", "无限道具", "100% 掉落", "敌方一击必杀"],
            cheats: goldCheats("r03", max: 9999999) + [
                cheat("全员回血并清除冷却", ["r03HealAllParty": true]), cheat("全员满经验", ["r03MaxAllExp": true]), boostAll(r03),
            ], resourceTitle: "所持金", resources: [resource("r03Gold", "金钱", "gold")], characters: r03),
        ModifierGamePanel("rance08", "Rance 08", MODIFIER_LOCK_RANCE08,
            locks: ["我方满 HP", "敌方一击必杀", "无限技能次数", "魔剑满蓄力", "无限金钱"],
            cheats: goldCheats("r08", max: 9999999) + [
                cheat("全员回血并刷新技能", ["r08HealAllParty": true]), cheat("魔剑满蓄力", ["r08MagnumMax": true]), boostAll(r08),
            ], resources: [
                resource("r08Gold", "金钱", "gold"), resource("r08Fame", "声望", "fame"), resource("r08FameMax", "声望上限", "fameMax"),
            ], characters: r08),
        ModifierGamePanel("rance09", "Rance 09", MODIFIER_LOCK_RANCE09,
            locks: ["我方无伤满 HP", "敌方一击必杀", "无限行动", "金币必掉", "无限资源"],
            cheats: goldCheats("r09", max: 999999, add: [10_000]) + [
                cheat("圣魔点 9999 · 圣魔石 999", ["r09SeimaPoint": 9999, "r09SeimaStone": 999]),
                cheat("全员回满 HP 与护盾", ["r09HealAllParty": true]),
                cheat("全员技能强化拉满", ["r09MaxAllSkillUp": true]), boostAll(r09),
            ], resources: [
                resource("r09Gold", "军资金", "gold"), resource("r09SeimaPoint", "圣魔点", "seimaPoint"),
                resource("r09SeimaStone", "圣魔石", "seimaStone"),
            ], characters: r09),
    ]

    private static let srResetActions = cheat("重置我方行动", ["srResetAllActions": true])
    private static let srHealTroops = cheat("我方回满兵力与技数", ["srHealAllTroops": true])
    static let srBattleActions = [
        cheat("敌方兵力设为 1", ["srBattle1HpEnemies": true]), srHealTroops, srResetActions,
    ]

    static let escalayer = ModifierGame(id: "escalayer", name: "超昂天使", lockGame: nil,
        locks: locks(["锁定满 HP", "锁定满 Libido", "敌方 1 HP", "锁定高爱情度"]),
        cheats: [], supportsCheatCodes: false)
    static let escalayerActions = [
        ("恢复满 HP", "esc_heal"), ("Libido 全满", "esc_libido_max"), ("敌方 HP 设为 1", "esc_enemy_onehit"),
        ("全能力强化", "esc_stats_boost"), ("攻击 +100", "esc_atk_add=100"), ("防御 +100", "esc_def_add=100"),
        ("反应 +50", "esc_rsp_add=50"), ("清除 D2 污染", "esc_d2_set=0"), ("和平度设为 100", "esc_peace_100"),
        ("爱情与调教强化", "esc_love_max"), ("解锁鉴赏", "esc_unlock_gallery"),
    ]
}
