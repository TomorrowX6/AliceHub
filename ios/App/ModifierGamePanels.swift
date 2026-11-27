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

    func rows(in state: [String: Any], localize: (String) -> String = { $0 }) -> [ModifierCharacter] {
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
        return [ModifierCharacter(target: target, name: localize(isSengoku ? "全体武将" : "全体角色"), state: [:])] + characters
    }

    func action(for character: ModifierCharacter, values: [String: Any]) -> [String: Any] {
        var payload = values
        if prefix == "sr", let resetAction = ModifierBridge.integer(payload["resetAction"]) {
            payload["resetAction"] = resetAction != 0
        }
        for (key, value) in character.target { payload[key] = value }
        return ["\(prefix)CharEdit": payload]
    }
}

struct System35ModifierPanel {
    let game: ModifierGame
    let fields: [(String, String)]
    let actions: [(String, String)]
}

struct ModifierGamePanel {
    let game: ModifierGame
    let resourceTitle: String
    let resources: [ModifierField]
    let resourceAction: ModifierCheat?
    let characters: ModifierCharacters?

    init(_ id: String, _ name: String, _ lockGame: modifier_lock_game? = nil, locks: [String],
         cheats: [ModifierCheat], resourceTitle: String = "资源修改", resources: [ModifierField] = [],
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

    private static func cheat(_ title: String, _ action: [String: Any], amount: Int? = nil) -> ModifierCheat {
        ModifierCheat(title: title, action: action, done: "已提交修改", amount: amount)
    }

    private static func resource(_ key: String, _ label: String, _ stateKey: String) -> ModifierField {
        ModifierField(key, label, stateKey: stateKey)
    }

    private static func goldCheats(_ prefix: String, max: Int, add: [Int] = [10_000, 100_000]) -> [ModifierCheat] {
        add.map { cheat("金钱 +%lld 万", ["\(prefix)AddGold": $0], amount: $0 / 10_000) }
            + [cheat("金钱设为 %lld", ["\(prefix)Gold": max], amount: max)]
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

    private static func exGoldCheats(max: Int = 9_999_999, add: [Int] = [100_000]) -> [ModifierCheat] {
        add.map { cheat("金钱 +%lld 万", ["addGold": $0], amount: $0 / 10_000) }
            + [cheat("金钱设为 %lld", ["gold": max], amount: max)]
    }

    private static let exR06 = ModifierCharacters(prefix: "ex", fields: [
        level, .init("lvMax", "等级上限"), .init("hp", "当前 HP"), .init("hpMax", "最大 HP"),
        .init("ap", "行动点"), .init("apMax", "行动上限"), experience,
        .init("atk", "攻击"), .init("def", "防御"), .init("mag", "魔力"), .init("res", "魔防"),
        .init("hit", "命中"), .init("eva", "回避"),
    ], boost: ["lv": 99, "lvMax": 99, "hp": 9999, "hpMax": 9999, "ap": 99, "apMax": 99,
               "atk": 999, "def": 999, "mag": 999, "res": 999, "hit": 200, "eva": 100, "fullHeal": true])

    private static let exEv2 = ModifierCharacters(prefix: "ex", fields: [level] + hp + [
        .init("atk", "攻击"), .init("mag", "魔力"), .init("def", "防御"), .init("spd", "速度"),
        experience, .init("skillP", "技能点"), .init("bp", "BP"),
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "atk": 999, "mag": 999, "def": 999,
               "spd": 999, "skillP": 999, "bp": 5, "fullHeal": true])

    private static let exDb = ModifierCharacters(prefix: "ex", fields: [level, experience] + hp + [
        .init("vit", "当前精力"), .init("vitMax", "最大精力"),
        .init("atk", "攻击"), .init("hit", "命中"), .init("eva", "回避"), .init("love", "好感度"),
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "vit": 999, "vitMax": 999,
               "atk": 999, "hit": 200, "eva": 200, "love": 100, "fullHeal": true])

    private static let exDt = ModifierCharacters(prefix: "ex", fields: [level, experience] + hp + [
        .init("atk", "攻击"), .init("scout", "索敌"),
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "atk": 999, "scout": 999, "fullHeal": true])

    private static let exGz = ModifierCharacters(prefix: "ex", fields: [
        level, .init("hp", "当前 HP"), .init("atk", "攻击"), .init("def", "防御"),
        .init("mag", "魔力"), .init("res", "魔防"), .init("eva", "回避"), .init("love", "好感度"),
    ], boost: ["lv": 99, "hp": 9999, "atk": 999, "def": 999, "mag": 999, "res": 999, "eva": 100, "love": 999, "boostBase": 1])

    private static let exBb = ModifierCharacters(prefix: "ex", fields: [level, experience] + hp + [
        .init("atk", "攻击"), .init("def", "防御"), .init("hit", "命中"), .init("eva", "回避"), .init("move", "移动力"),
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "atk": 999, "def": 999, "hit": 200, "eva": 200, "move": 15, "fullHeal": true])

    private static let exYk = ModifierCharacters(prefix: "ex", fields: [level] + hp + [
        experience, .init("atk", "攻击"), .init("spd", "速度"), .init("hit", "命中"), .init("mag", "魔力"),
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "atk": 999, "spd": 999, "hit": 999, "mag": 999, "fullHeal": true])

    private static let exWd = ModifierCharacters(prefix: "ex", fields: [level] + hp + [
        .init("atk", "攻击"), .init("def", "防御"), .init("mag", "魔力"), .init("res", "魔防"), .init("hit", "命中"),
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "atk": 999, "def": 999, "mag": 999, "res": 999, "hit": 999, "fullHeal": true])

    private static let exSb = ModifierCharacters(prefix: "ex", fields: hp + [
        .init("atk", "攻击"), .init("def", "防御"), .init("mag", "魔力"), .init("spd", "速度"),
    ], boost: ["hp": 9999, "hpMax": 9999, "atk": 999, "def": 999, "mag": 999, "spd": 999, "fullHeal": true])

    private static let exDp = ModifierCharacters(prefix: "ex", fields: [level] + hp + [
        .init("atk", "攻击"), .init("love", "好感度"), experience,
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "atk": 999, "love": 100, "fullHeal": true])

    private static let exHu = ModifierCharacters(prefix: "ex", fields: [
        .init("hp", "当前 HP"), .init("skill", "技巧"), .init("visual", "魅力"),
    ], boost: ["hp": 100, "skill": 999, "visual": 999])

    private static let exOy = ModifierCharacters(prefix: "ex", fields: [
        .init("hp", "防卫值"), .init("star", "星级"), .init("fert", "受孕槽"),
    ], boost: ["hp": 999, "star": 99, "fert": 0])

    private static let exOk = ModifierCharacters(prefix: "ex", fields: [level, experience] + hp + [
        .init("mp", "当前 MP"), .init("mpMax", "最大 MP"), .init("atk", "攻击"), .init("def", "防御"),
    ], boost: ["lv": 99, "hp": 9999, "hpMax": 9999, "mp": 999, "mpMax": 999, "atk": 999, "def": 999, "fullHeal": true])

    private static let exIx = ModifierCharacters(prefix: "ex", fields: hp + [
        .init("atk", "攻击"), .init("def", "防御"), .init("spd", "速度"),
        .init("energy", "能量"), .init("love", "好感度"), .init("pollute", "污染度"),
    ], boost: ["hp": 9999, "hpMax": 9999, "atk": 999, "def": 999, "spd": 999, "energy": 100, "love": 999, "pollute": 0])

    private static let exP3 = ModifierCharacters(prefix: "ex", fields: [
        level, experience, .init("bp", "BP"), .init("hp", "当前 HP"), .init("sp", "当前 SP"),
    ], boost: ["lv": 99, "bp": 999, "hp": 9999, "sp": 999])

    private static let exT3g = ModifierCharacters(prefix: "ex", fields: [
        level, .init("love", "好感度"), .init("seed", "主角进度"), .init("rival", "情敌值"),
    ], boost: ["lv": 99, "love": 999, "rival": 0])

    private static func evenicle2Panel(_ id: String, _ name: String) -> ModifierGamePanel {
        ModifierGamePanel(id, name,
            locks: ["禁止随机遇敌", "我方满 HP", "无限技能点", "无限 BP", "敌方 1 HP", "无限金钱"],
            cheats: exGoldCheats(max: 99_999_999) + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("敌方 HP 设为 1", ["cheat": "enemy1Hp"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                boostAll(exEv2),
            ],
            resources: [ModifierField("gold", "所持金")], characters: exEv2)
    }

    private static func okusamaPanel(_ id: String, _ name: String) -> ModifierGamePanel {
        ModifierGamePanel(id, name,
            locks: ["无限金钱", "我方满 HP 与 MP"],
            cheats: exGoldCheats() + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                boostAll(exOk),
            ],
            resources: [ModifierField("gold", "金钱")], characters: exOk)
    }

    static let extraPanels: [ModifierGamePanel] = [
        ModifierGamePanel("rance06", "Rance 06",
            locks: ["无限金钱", "我方满 HP 与行动力", "敌方 1 HP", "无限消耗品", "最高幸运"],
            cheats: exGoldCheats() + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("敌方 HP 设为 1", ["cheat": "enemy1Hp"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                boostAll(exR06),
            ],
            resources: [ModifierField("gold", "金钱"), ModifierField("luck", "幸运")], characters: exR06),
        evenicle2Panel("evenicle2", "Evenicle 2"),
        evenicle2Panel("evenicle2rance", "Evenicle 2 Rance"),
        ModifierGamePanel("daibanchou", "大番长",
            locks: ["无限金钱", "部下满体力与精力", "部下满忠诚度", "锁定回合为 1"],
            cheats: exGoldCheats() + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                boostAll(exDb),
            ],
            resources: [ModifierField("gold", "金钱"), ModifierField("turn", "回合")], characters: exDb),
        ModifierGamePanel("daiteikoku", "大帝国",
            locks: ["无限资源", "无限技术", "我方提督满 HP", "我方无限行动", "我方星域满治安"],
            cheats: [
                cheat("资源 +10 万", ["addResource": 100_000]),
                cheat("技术 +10 万", ["addTechnology": 100_000]),
                cheat("资源与技术拉满", ["resource": 9_999_999, "technology": 9_999_999]),
                cheat("全员满血并重置行动", ["cheat": "fullHeal"]),
                cheat("解锁全部战舰", ["cheat": "unlockShips"]),
                boostAll(exDt),
            ],
            resources: [ModifierField("resource", "资源"), ModifierField("technology", "技术")], characters: exDt),
        ModifierGamePanel("galzoo", "萌兽岛",
            locks: ["无限金钱", "敌方 1 HP", "100% 掉落", "禁止随机遇敌", "无限消耗品"],
            cheats: exGoldCheats() + [
                cheat("敌方 HP 设为 1", ["cheat": "enemy1Hp"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                boostAll(exGz),
            ],
            resources: [ModifierField("gold", "金钱"), ModifierField("exp", "经验")], characters: exGz),
        ModifierGamePanel("toushin3", "斗神都市 III",
            locks: ["无限金钱", "锁定满 HP", "敌方 1 HP", "禁止随机遇敌", "无限消耗品"],
            cheats: exGoldCheats() + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("敌方 HP 设为 1", ["cheat": "enemy1Hp"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                cheat("全能力强化", ["cheat": "boostAll"]),
            ],
            resources: [
                ModifierField("gold", "金钱"), level, .init("hp", "当前 HP"), .init("hpMax", "最大 HP"),
                .init("atk", "攻击"), .init("def", "防御"), .init("hit", "命中"), .init("eva", "回避"), experience,
            ]),
        ModifierGamePanel("bladebriders", "武想少女",
            locks: ["无限金钱", "我方满 HP", "敌方 1 HP", "无限行动次数", "开启成长加成"],
            cheats: exGoldCheats() + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("敌方 HP 设为 1", ["cheat": "enemy1Hp"]),
                boostAll(exBb),
            ],
            resources: [ModifierField("gold", "金钱"), ModifierField("ap", "行动点")], characters: exBb),
        ModifierGamePanel("yorugakuru", "夜来了！",
            locks: ["无限金钱", "我方满 HP", "免疫异常状态", "开启休闲增益"],
            cheats: exGoldCheats(max: 999_999) + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                boostAll(exYk),
            ],
            resources: [ModifierField("gold", "金钱")], characters: exYk),
        ModifierGamePanel("waidonyo", "猫娘大冒险",
            locks: ["我方满 HP", "无限金钱", "敌方 1 HP"],
            cheats: [
                cheat("金钱设为 %lld", ["gold": 9_999_999], amount: 9_999_999),
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("敌方 HP 设为 1", ["cheat": "enemy1Hp"]),
                boostAll(exWd),
            ],
            resources: [ModifierField("gold", "金钱")], characters: exWd),
        ModifierGamePanel("sanbiki", "三匹的野望",
            locks: ["无限金钱", "我方满 HP", "最高幸运", "无限消耗品"],
            cheats: exGoldCheats() + [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                boostAll(exSb),
            ],
            resources: [ModifierField("gold", "金钱"), ModifierField("luck", "幸运")], characters: exSb),
        ModifierGamePanel("dorapeko", "饥饿的龙",
            locks: ["我方满 HP", "锁定高爱情度", "我方无限行动"],
            cheats: [
                cheat("重置我方行动", ["cheat": "fullHeal"]),
                boostAll(exDp),
            ], characters: exDp),
        ModifierGamePanel("haruurare", "春风物语",
            locks: ["无限金钱", "我方满 HP", "最高信用度"],
            cheats: exGoldCheats() + [
                cheat("上缴额度清零", ["quota": 0]),
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("解锁全部商品", ["cheat": "unlockItems"]),
                boostAll(exHu),
            ],
            resources: [
                ModifierField("gold", "金钱"), ModifierField("credit", "信用点数"), ModifierField("quota", "上缴额度"),
            ], characters: exHu),
        ModifierGamePanel("oyakorankan", "母女乱馆",
            locks: ["满爱情防卫值", "满星级评价", "清空受孕槽", "敌方能力清零"],
            cheats: [
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("敌方攻防清零", ["cheat": "weakenEnemy"]),
            ], characters: exOy),
        ModifierGamePanel("tsumashibori", "榨精人妻",
            locks: ["无限金钱", "我方满 HP 与 SP", "女主满好感零嫉妒", "情敌精力清零", "无限消耗品"],
            cheats: exGoldCheats() + [
                cheat("全员回满 HP 与 SP", ["cheat": "fullHeal"]),
                cheat("情敌精力清零", ["cheat": "weakenEnemy"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
            ],
            resources: [
                ModifierField("gold", "金钱"), .init("hp", "当前 HP"), .init("hpMax", "最大 HP"),
                .init("sp", "当前 SP"), .init("spMax", "最大 SP"), level, experience,
            ]),
        okusamaPanel("okusamajun", "人妻管理员 纯爱篇"),
        okusamaPanel("okusamantr", "人妻管理员 NTR篇"),
        ModifierGamePanel("escalayerreboot", "超昂天使 R",
            locks: ["满能量与 Libido", "满和平点与爱情", "常驻 Boost 强化", "全能力强化"],
            cheats: [
                cheat("和平点 +50000", ["addPeace": 50_000]),
                cheat("Libido 全满", ["cheat": "fullHeal"]),
                cheat("全能力强化", ["cheat": "boostAll"]),
            ],
            resources: [
                .init("peace", "和平度"), .init("love", "爱情度"), .init("libido", "Libido"),
                .init("energy", "能量"), .init("shield", "护盾"),
                .init("atk", "攻击"), .init("def", "防御"), .init("spd", "速度"),
            ]),
        ModifierGamePanel("ixseal", "超昂神骑",
            locks: ["我方满 HP", "清除污染度", "锁定高爱情度", "无限魔石板"],
            cheats: [
                cheat("魔石板拉满 (12)", ["gold": 12]),
                cheat("恢复满 HP", ["cheat": "fullHeal"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                boostAll(exIx),
            ],
            resources: [ModifierField("gold", "魔石板"), ModifierField("energy", "能量")], characters: exIx),
        ModifierGamePanel("pascha3", "粉彩铃音 3",
            locks: ["无限金钱", "无限行动次数", "我方满 HP 与 SP", "锁定高爱情度", "无限消耗品"],
            cheats: exGoldCheats() + [
                cheat("全员回满 HP 与 SP", ["cheat": "fullHeal"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                boostAll(exP3),
            ],
            resources: [ModifierField("gold", "金钱"), ModifierField("ap", "行动点")], characters: exP3),
        ModifierGamePanel("haharanman", "母烂漫",
            locks: ["无限行动次数", "满精神与恢复点", "免消耗与经验加成", "解除时段限制"],
            cheats: [
                cheat("补满行动次数", ["cheat": "fullHeal"]),
                cheat("全能力强化", ["cheat": "boostAll"]),
            ],
            resources: [
                .init("ap", "行动点"), level, experience,
                .init("mental", "精神力"), .init("refresh", "恢复点数"), .init("days", "剩余天数"),
            ]),
        ModifierGamePanel("tsumamigui3", "妻中蜜 3",
            locks: ["无限金钱", "无限精力", "清除情敌痕迹", "无限消耗品"],
            cheats: exGoldCheats() + [
                cheat("回满精力并清除情敌", ["cheat": "fullHeal"]),
                cheat("补满消耗品", ["cheat": "maxItems"]),
                boostAll(exT3g),
            ],
            resources: [ModifierField("gold", "金钱"), ModifierField("energy", "精力")], characters: exT3g),
        ModifierGamePanel("maman", "再婚妈妈",
            locks: ["无限道具", "满野性与心情", "锁定高爱情度"],
            cheats: [
                cheat("补满消耗品", ["cheat": "fullHeal"]),
                cheat("全能力强化", ["cheat": "boostAll"]),
            ],
            resources: [
                .init("condom", "道具数"), .init("wild", "野性度"),
                .init("mcon", "恋母度"), .init("humor", "心情"),
            ]),
    ]

    static let all: [ModifierGamePanel] = corePanels + extraPanels

    static let corePanels: [ModifierGamePanel] = [
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

    static let system35Panels: [System35ModifierPanel] = [
        System35ModifierPanel(
            game: escalayer,
            fields: [("hp", "当前 HP"), ("hpMax", "最大 HP"), ("enemyHp", "敌方 HP"), ("atk", "攻击"),
                     ("def", "防御"), ("rsp", "反应"), ("d2", "D2 污染"), ("peace", "和平度"), ("love", "爱情度")],
            actions: escalayerActions
        ),
        System35ModifierPanel(
            game: ModifierGame(id: "daiakuji", name: "大恶司", lockGame: nil,
                locks: locks(["无限金钱", "债务清零", "我方满 HP", "锁定满忠诚与人气"]),
                cheats: [], supportsCheatCodes: false),
            fields: [("gold", "金钱"), ("debt", "债务"), ("subsidy", "补助金"), ("popular", "人气"), ("hp", "当前 HP")],
            actions: [
                ("资金 +10000", "da_gold_add=10000"), ("资金拉满", "da_gold_max"),
                ("债务清零", "da_debt_zero"), ("全员体力强化 999", "da_hp_boost"),
                ("忠诚与人气拉满", "da_love_max"), ("区域治安最佳", "da_security_max"),
                ("解锁全部特殊道具", "da_items_unlock"),
            ]
        ),
        System35ModifierPanel(
            game: ModifierGame(id: "rance5d", name: "Rance 5D", lockGame: nil,
                locks: locks(["我方满 HP", "无限行动次数", "道具满耐久", "金块与经验玉满额"]),
                cheats: [], supportsCheatCodes: false),
            fields: [("hp", "当前 HP"), ("hpMax", "最大 HP"), ("ap", "行动次数"), ("gold", "金块"), ("expOrb", "经验玉")],
            actions: [
                ("恢复满 HP", "r5d_heal"), ("全能力强化", "r5d_stats_boost"),
                ("补满金块与经验玉", "r5d_gold_exp"), ("女主好感度全满", "r5d_love_max"),
                ("补满道具耐久", "r5d_durability"),
            ]
        ),
        System35ModifierPanel(
            game: ModifierGame(id: "tsumamigui", name: "妻中蜜", lockGame: nil,
                locks: locks(["无限体力", "技巧等级全满"]),
                cheats: [], supportsCheatCodes: false),
            fields: [("stamina", "耐久力"), ("lv", "等级"), ("exp", "经验"), ("kanaeLv", "香苗等级"), ("chihoLv", "千穗等级")],
            actions: [
                ("耐久力设为 999", "tm1_stamina_max"), ("技巧等级全满", "tm1_tech_max"),
                ("女主等级全满", "tm1_heroine_max"), ("经验 +500", "tm1_exp_add=500"),
            ]
        ),
        System35ModifierPanel(
            game: ModifierGame(id: "kichikuou", name: "鬼畜王兰斯", lockGame: nil,
                locks: locks(["无限金钱", "我方无限行动"]),
                cheats: [], supportsCheatCodes: false),
            fields: [("gold", "金钱"), ("month", "月份"), ("week", "周")],
            actions: [
                ("资金 +1000", "kr_gold_add=1000"), ("资金拉满", "kr_gold_max"),
                ("重置我方行动", "kr_reset_actions"),
            ]
        ),
        System35ModifierPanel(
            game: ModifierGame(id: "kaerunyo", name: "青蛙王国", lockGame: nil,
                locks: locks(["无限金钱", "无限道具"]),
                cheats: [], supportsCheatCodes: false),
            fields: [("gold", "金钱"), ("ropes", "捕获绳")],
            actions: [
                ("资金 +10000", "kn_gold_add=10000"), ("资金拉满", "kn_gold_max"),
                ("补满消耗品", "kn_items_max"),
            ]
        ),
    ]
}
