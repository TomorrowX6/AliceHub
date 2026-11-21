import Foundation

struct ModifierLock: Identifiable {
    let bit: Int32
    let title: String
    var id: Int32 { bit }
}

struct ModifierCheat: Identifiable {
    let title: String
    let action: [String: Any]
    let done: String
    var id: String { title }
}

struct ModifierGame {
    let id: String
    let name: String
    let lockGame: modifier_lock_game?
    let locks: [ModifierLock]
    let cheats: [ModifierCheat]
    let supportsCheatCodes: Bool
}

enum ModifierBridge {
    static let rance10StreamerModeBit: Int32 = 1 << 11
    static let disableStreamerCodes: Set<String> = ["关闭主播模式", "灌注Eins陈谢谢喵", "关注Eins陈谢谢喵"]
    static let enableStreamerCode = "开启主播模式"

    static let cheatCodePresets: [(code: String, title: String)] = [
        ("灌注Eins陈谢谢喵", "关闭主播模式"),
        ("界徐盛", "解禁全部作弊码限制"),
        ("whosyourdaddy", "击杀当前敌人"),
        ("血量全满", "战斗中我方血量全满"),
        ("重开", "重新开始当前战斗"),
        ("打败哈尼王", "开启兰斯世界模式"),
        ("111", "遗物图鉴全展示"),
        ("panzer", "获得游戏管理者卡牌"),
        ("餐券10", "增加10张餐券"),
        ("部队点数up10", "增加10点部队点数"),
        ("exp道具", "获得1个EXP道具"),
        ("难度up4", "难度+4"),
        ("4000flag200", "等级上限额外+200"),
        ("1505flag1", "开启装备解封模式"),
        ("1522flag1", "重置任务技能状态"),
        ("2222flag0", "清空敌方盾与复生"),
        ("2233flag0", "清空敌方自动恢复次数"),
        ("Lv42 兰斯卡牌追加", "追加指定卡牌模板"),
        ("技能识别", "识别正中间角色技能编号"),
        ("装备识别", "识别当前武器与防具代码"),
        ("梗概关闭", "关闭梗概模式"),
    ]

    static let games: [ModifierGame] = [
        ModifierGame(id: "rance10", name: "Rance X", lockGame: MODIFIER_LOCK_RANCE10, locks: [
            ModifierLock(bit: 1 << 0, title: "锁定我方满生命"),
            ModifierLock(bit: 1 << 1, title: "锁定无限AP"),
            ModifierLock(bit: 1 << 2, title: "锁定999连击加成"),
            ModifierLock(bit: 1 << 3, title: "锁定战斗回合为1"),
            ModifierLock(bit: 1 << 4, title: "敌方1血秒杀"),
            ModifierLock(bit: 1 << 5, title: "敌方0攻击0防御"),
            ModifierLock(bit: 1 << 6, title: "全员蓄力必暴连击"),
            ModifierLock(bit: 1 << 7, title: "技能无限使用次数"),
            ModifierLock(bit: 1 << 8, title: "无限悔棋打火机"),
            ModifierLock(bit: 1 << 9, title: "资源不低于999"),
            ModifierLock(bit: 1 << 10, title: "宝箱100%掉落"),
        ], cheats: [], supportsCheatCodes: true),
        ModifierGame(id: "dohnadohna", name: "ドーナドーナ", lockGame: MODIFIER_LOCK_DOHNADOHNA, locks: [
            ModifierLock(bit: 1 << 0, title: "战斗锁定我方全员满HP"),
            ModifierLock(bit: 1 << 1, title: "战斗锁定我方全员满MP"),
            ModifierLock(bit: 1 << 2, title: "战斗锁定我方全员满怒气VP"),
            ModifierLock(bit: 1 << 3, title: "战斗敌方全员1血秒杀"),
            ModifierLock(bit: 1 << 4, title: "无限资金 · 保底99999999"),
            ModifierLock(bit: 1 << 5, title: "无限调教点 · TP不减"),
            ModifierLock(bit: 1 << 6, title: "人材满精神满避孕药且不崩溃不怀孕"),
            ModifierLock(bit: 1 << 7, title: "春销营业配对倒计时锁定不减"),
        ], cheats: [
            ModifierCheat(title: "资金增加 100 万", action: ["ddCheatAddMoney": 1_000_000], done: "已增加 100 万资金"),
            ModifierCheat(title: "资金增加 1000 万", action: ["ddCheatAddMoney": 10_000_000], done: "已增加 1000 万资金"),
            ModifierCheat(title: "资金拉满 9.99 亿并回满调教点", action: ["ddCheatMaxMoney": true], done: "资金已拉满并回满调教点"),
            ModifierCheat(title: "扩建店面单间上限至 10 间并扩充调教点上限", action: ["ddCheatExpandMansion": true], done: "单间上限已扩至 10 间且调教点上限扩至 10"),
            ModifierCheat(title: "当前已持有消耗品与徽章数量补满 99", action: ["ddCheatHeldItems99": true], done: "已持有消耗品与徽章已补满 99 个"),
            ModifierCheat(title: "解锁全消耗品与全 31 种正式徽章各 99 个", action: ["ddCheatAllItemsBadges99": true], done: "已解锁全部消耗品与 31 种正式徽章各 99 个"),
            ModifierCheat(title: "在籍战斗成员全员 HP·MP·怒气回满", action: ["ddCheatPartyFullHeal": true], done: "战斗成员全员状态已回满"),
            ModifierCheat(title: "已入队战斗成员一键满级 Lv99", action: ["ddCheatPartyLv99": true], done: "已入队成员已升至 Lv99"),
            ModifierCheat(title: "已入队成员全能力永久强化并满好感度", action: ["ddCheatPartySuperBoost": true], done: "已入队成员全能力与好感度已强化"),
            ModifierCheat(title: "全员装备专属六阶毕业武器", action: ["ddCheatPartyRank6Weapons": true], done: "已为全员装备六阶毕业武器"),
            ModifierCheat(title: "全体春销人材精神避孕药回满并治愈崩溃怀孕", action: ["ddCheatWorkersHeal": true], done: "全体人材精神避孕药已回满并治愈异常状态"),
            ModifierCheat(title: "全体春销人材一键 SS 评级·UR 稀有度·四维拉满", action: ["ddCheatWorkersMaxStats": true], done: "全体人材已设为 SS 评级·UR 稀有度·四维拉满"),
        ], supportsCheatCodes: false),
        ModifierGame(id: "evenicle", name: "イブニクル", lockGame: MODIFIER_LOCK_EVENICLE, locks: [
            ModifierLock(bit: 1 << 0, title: "禁止随机遇敌 · 安全步数锁定满值"),
            ModifierLock(bit: 1 << 1, title: "我方全员满 HP 锁定"),
            ModifierLock(bit: 1 << 2, title: "无限技能点 · SkillP 至少 99"),
            ModifierLock(bit: 1 << 3, title: "无限 BP · 战斗 BP 满 5 格"),
            ModifierLock(bit: 1 << 4, title: "战斗敌方全员 1 血秒杀"),
            ModifierLock(bit: 1 << 5, title: "无限所持金 · 保底 9999999"),
        ], cheats: [
            ModifierCheat(title: "所持金增加 10 万", action: ["evCheatAddMoney": 100_000], done: "所持金已增加 10 万"),
            ModifierCheat(title: "所持金增加 100 万", action: ["evCheatAddMoney": 1_000_000], done: "所持金已增加 100 万"),
            ModifierCheat(title: "所持金拉满 99999999", action: ["evCheatMaxMoney": true], done: "所持金已拉满 99999999"),
            ModifierCheat(title: "出战角色全员满 HP 与满 5 BP", action: ["evCheatPartyFullHeal": true], done: "出战角色已回满 HP 与 BP"),
            ModifierCheat(title: "出战角色技能点全部补满 999", action: ["evCheatPartySkillP999": true], done: "出战角色技能点已设为 999"),
            ModifierCheat(title: "出战角色一键暴走强化 9999 HP 与 999 四维", action: ["evCheatPartyBoost": true], done: "出战角色一键强化完成"),
            ModifierCheat(title: "立即触发下次移动遇敌", action: ["evCheatTriggerEncounter": true], done: "下次大地图移动将立即遇敌"),
            ModifierCheat(title: "清空当前遇敌危险度至安全状态", action: ["evCheatClearEncounter": true], done: "遇敌危险度已重置为安全状态"),
        ], supportsCheatCodes: false),
    ]

    static var configURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("modifier_config.json")
    }

    static func prepareForLaunch() {
        modifier_api_set_config_path(configURL.path)
    }

    static func currentGame() -> ModifierGame? {
        let id = String(cString: modifier_api_current_game())
        return games.first { $0.id == id }
    }

    static func liveState() -> [String: Any] {
        guard let raw = modifier_api_get_live_state_json() else { return [:] }
        defer { free(raw) }
        return parse(String(cString: raw))
    }

    static func lockMask(_ game: modifier_lock_game) -> Int32 {
        modifier_api_get_lock_mask(game)
    }

    static func setLockMask(_ game: modifier_lock_game, _ mask: Int32) {
        modifier_api_set_lock_mask(game, mask)
    }

    static func send(_ action: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: action),
              let json = String(data: data, encoding: .utf8) else { return }
        modifier_api_apply_live_action(json)
    }

    /// Mirrors the Android overlay: streamer-mode codes toggle the lock bit, everything else goes to the MOD.
    static func cheatAction(for rawCode: String) -> [String: Any]? {
        let code = normalizeCheatCode(rawCode)
        if code.isEmpty { return nil }
        if disableStreamerCodes.contains(code) {
            return ["streamerMode": 0, "streamerPopup": true]
        }
        if code == enableStreamerCode {
            return ["streamerMode": 1]
        }
        return ["cheatCode": code]
    }

    static func normalizeCheatCode(_ code: String) -> String {
        code.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\"“”"))
    }

    static func applyCheatCode(_ rawCode: String) -> Bool {
        guard let action = cheatAction(for: rawCode) else { return false }
        if let mode = action["streamerMode"] as? Int {
            let mask = lockMask(MODIFIER_LOCK_RANCE10)
            let disabled = mode == 0
            setLockMask(MODIFIER_LOCK_RANCE10, disabled ? mask | rance10StreamerModeBit : mask & ~rance10StreamerModeBit)
        }
        send(action)
        return true
    }

    static func readLocalSave(_ url: URL) -> [String: Any] {
        guard let raw = modifier_api_read_local_save_summary(url.path) else { return [:] }
        defer { free(raw) }
        return parse(String(cString: raw))
    }

    static func writeLocalSave(_ url: URL, changes: [String: Int]) throws {
        let backup = url.appendingPathExtension("mod.bak")
        let fm = FileManager.default
        if fm.fileExists(atPath: backup.path) { try fm.removeItem(at: backup) }
        try fm.copyItem(at: url, to: backup)
        let data = try JSONSerialization.data(withJSONObject: changes)
        guard let json = String(data: data, encoding: .utf8),
              modifier_api_write_local_save_mod(url.path, json) else {
            throw CocoaError(.fileWriteUnknown)
        }
    }

    /// Keys `write_local_save_mod_str` in modifier.c applies; other summary fields are read-only.
    static let writableSaveKeys: Set<String> = [
        "addCardCount", "addPerHit", "atkPerMag", "atkPerPhys", "battleCount", "cardAtkBonus", "cardHpBonus",
        "chapter", "countryEnemyArmy", "countryPlayerArmy", "countryState", "curseTurn", "decBadCond",
        "defPerMag", "defPerPhys", "difficulty", "foodTicket", "freeCitiesEArmy", "freeCitiesPArmy",
        "freeCitiesState", "friendPoint", "hannyZippo", "helmanEArmy", "helmanPArmy", "helmanState", "honor",
        "humanAlivePer", "humanCount", "humanDead", "humanDeadPer", "isUsedHannyZippo", "itemStar",
        "leazasEArmy", "leazasPArmy", "leazasState", "partyAtk", "partyAvailCount", "partyBonusMaxPoint",
        "partyHp", "partyIsFixStatus", "partyMaxHp", "partyRank", "perAutoAtk", "perAutoHeal", "perAvoid",
        "perCrit", "perExpUp", "perFindTreasure", "perHpUp", "perInjection", "perMulti", "rareLuck",
        "seaEnemyArmy", "seaPlayerArmy", "setCardCount", "setCharExp", "setCharStar", "statusMaxPer",
        "subArmy", "subDwarf", "subGeneral", "subHanny", "subRemnant", "totalExp", "treasureNotGetCount",
        "turn", "zethEArmy", "zethPArmy", "zethState",
    ]

    /// Integer fields of a save summary that the native writer understands, in display order.
    static func editableFields(_ summary: [String: Any]) -> [(key: String, value: Int)] {
        summary.compactMap { key, value -> (String, Int)? in
            guard writableSaveKeys.contains(key), let number = value as? NSNumber,
                  CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
            return (key, number.intValue)
        }
        .sorted { $0.0 < $1.0 }
        .map { (key: $0.0, value: $0.1) }
    }

    static func parse(_ json: String) -> [String: Any] {
        guard let data = json.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        return object
    }
}
