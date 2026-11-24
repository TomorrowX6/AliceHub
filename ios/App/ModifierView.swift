import SwiftUI

struct ModifierView: View {
    let dismiss: () -> Void
    private let system35: Bool
    private let system4Game: ModifierGame?
    @State private var mask: Int32 = 0
    @State private var code = ""
    @State private var status: String?
    @State private var live: [String: Any] = [:]
    @State private var refreshID = 0

    init(dismiss: @escaping () -> Void) {
        self.dismiss = dismiss
        let snapshot = ModifierBridge.system35LiveState()
        system35 = snapshot != nil
        system4Game = snapshot == nil ? ModifierBridge.currentGame() : nil
        _live = State(initialValue: snapshot.map(ModifierBridge.parse) ?? [:])
    }

    private var game: ModifierGame? {
        system35 && live["game"] as? String == "escalayer" ? ModifierGamePanel.escalayer : system4Game
    }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    HubSectionHeader(title: "局内修改器", subtitle: game?.name ?? "当前游戏", symbol: "slider.horizontal.3")
                }.listRowBackground(HubTheme.surface)
                if let game {
                    if game.id == "rance10" { streamerSection }
                    if game.supportsCheatCodes { cheatCodeSection }
                    if game.id == "rance10" { rance10Panels }
                    if let panel = ModifierGamePanel.all.first(where: { $0.game.id == game.id }) { gamePanels(panel) }
                    if game.id == "escalayer" { escalayerPanel }
                    if !game.locks.isEmpty { lockSection(game) }
                    if !game.cheats.isEmpty { cheatSection(game) }
                } else {
                    Section { Text("当前游戏暂不支持修改器。").foregroundColor(.secondary) }
                }
                if game?.id == "dohnadohna" || game?.id == "evenicle" { liveSection }
            }
            .modifier(HubListStyle())
            .navigationTitle(game?.name ?? "修改器")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成", action: dismiss) } }
        }
        .navigationViewStyle(.stack)
        .modifier(HubAppearanceModifier())
        .task(id: refreshID) {
            if system35 {
                mask = xsystem35_modifier_get_locks()
            } else if let lockGame = game?.lockGame {
                mask = ModifierBridge.lockMask(lockGame)
            }
            if system35 || game != nil {
                await ModifierBridge.observeLiveState(system35: system35) { live = $0 }
            }
        }
    }

    private var streamerSection: some View {
        Section {
            if let enabled = ModifierBridge.streamerToggleState(in: live, mask: mask) {
                Button(enabled ? "主播模式 · 已开启" : "主播模式 · 已关闭") {
                    ModifierBridge.setStreamerMode(!enabled)
                    mask = ModifierBridge.lockMask(MODIFIER_LOCK_RANCE10)
                    live.removeValue(forKey: "streamerMode")
                    refreshID += 1
                }
            } else {
                Text("不支持主播模式").foregroundColor(.secondary)
            }
        }
    }

    private var rance10Panels: some View {
        Section {
            if live["inGame"] as? Bool != true { Text("请先进入游戏").foregroundColor(.secondary) }
            NavigationLink("战斗指令") { battlePanel }
            editorLink("资源与回合", fields: [
                .init("turn", "回合"), .init("difficulty", "难度"), .init("foodTicket", "食券"),
                .init("honor", "勋章"), .init("friendPoint", "友情点"), .init("totalExp", "累计经验"),
                .init("partyRank", "部队等级"), .init("itemStar", "装备星级"), .init("hannyZippo", "悔棋次数"),
            ])
            editorLink("部队与属性", fields: [
                .init("partyHp", "当前 HP"), .init("partyMaxHp", "最大 HP"), .init("partyAvailCount", "出击人数"),
                .init("partyIsFixStatus", "固定属性（0/1）"), .init("partyAtk", "部队攻击力"),
                .init("atkPerPhys", "物攻加成 %"), .init("atkPerMag", "魔攻加成 %"),
                .init("defPerPhys", "物防加成 %"), .init("defPerMag", "魔防加成 %"),
                .init("perCrit", "暴击率 %"), .init("perMulti", "连击率 %"), .init("perExpUp", "经验加成 %"),
                .init("perFindTreasure", "宝箱掉落率 %"),
            ])
            editorLink("四国与人口", fields: [
                .init("humanCount", "存活人口"), .init("humanDead", "死亡人口"), .init("seaPlayerArmy", "船团兵力"),
                .init("leazasPArmy", "利萨斯我军"), .init("helmanPArmy", "赫尔曼我军"),
                .init("zethPArmy", "赛斯我军"), .init("freeCitiesPArmy", "自由都市我军"),
                .init("leazasEArmy", "利萨斯魔军"), .init("helmanEArmy", "赫尔曼魔军"),
                .init("zethEArmy", "赛斯魔军"), .init("freeCitiesEArmy", "自由都市魔军"),
            ])
            editorLink("卡牌与角色", fields: [
                .init("setCardCount", "全卡牌叠卡数"), .init("cardAtkBonus", "全卡牌攻击加成"),
                .init("cardHpBonus", "全卡牌生命加成"), .init("setCharStar", "全角色星级"),
                .init("setCharExp", "全角色经验"),
            ], quickAction: ModifierCheat(title: "全卡牌与角色强化", action: [
                "setCardCount": 99, "cardAtkBonus": 999, "cardHpBonus": 999, "setCharStar": 5, "setCharExp": 999999,
            ], done: ""))
        } header: { Text("实时修改") }
    }

    private var battlePanel: some View {
        let inBattle = live["inBattle"] as? Bool == true
        return Form {
            Section {
                editorLink("战斗数值", fields: [
                    .init("battleAp", "AP"), .init("battleCombo", "连击"), .init("battleRound", "战斗回合"),
                ], requiresBattle: true)
                if !inBattle { Text("请先进入战斗").foregroundColor(.secondary) }
                Button("敌方 HP 设为 1") { ModifierBridge.send(["killEnemy": true]) }.disabled(!inBattle)
                Button("敌方攻防清零") { ModifierBridge.send(["weakenEnemy": true]) }.disabled(!inBattle)
                Button("我方生命回满") { ModifierBridge.send(["refillPartyHp": true]) }.disabled(!inBattle)
                Button("AP 99 · 连击 999 · 回合 1") {
                    ModifierBridge.send(["battleAp": 99, "battleCombo": 999, "battleRound": 1])
                }.disabled(!inBattle)
                Button("全员蓄力并重置技能") { ModifierBridge.send(["cureAndBoostLeaders": true]) }.disabled(!inBattle)
                Button("宝箱 100% 掉落") {
                    setMask(mask | ModifierBridge.rance10TreasureBit)
                    ModifierBridge.send(ModifierBridge.rance10TreasureAction)
                }
            }
        }
        .modifier(HubListStyle())
        .navigationTitle("战斗指令")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func editorLink(_ title: String, fields: [ModifierField], requiresBattle: Bool = false, summary: [String] = [],
                            quickAction: ModifierCheat? = nil) -> some View {
        NavigationLink(title) {
            ModifierEditor(title, fields: fields, state: live, summary: summary,
                           quickAction: quickAction, apply: ModifierBridge.send)
        }.disabled(live[requiresBattle ? "inBattle" : "inGame"] as? Bool != true)
    }

    private func gamePanels(_ panel: ModifierGamePanel) -> some View {
        Section {
            if live["inGame"] as? Bool != true { Text("请先进入游戏").foregroundColor(.secondary) }
            if panel.game.id == "sengokurance" {
                NavigationLink("战斗指令") {
                    Form {
                        ForEach(ModifierGamePanel.srBattleActions) { action in
                            Button(action.title) { ModifierBridge.send(action.action) }
                                .disabled(action.action["srResetAllActions"] == nil && live["inBattle"] as? Bool != true)
                        }
                    }
                    .modifier(HubListStyle())
                    .navigationTitle("战斗指令")
                    .navigationBarTitleDisplayMode(.inline)
                }.disabled(live["inGame"] as? Bool != true)
            }
            editorLink(panel.resourceTitle, fields: panel.resources, summary: resourceSummary(panel.game.id),
                       quickAction: panel.resourceAction)
            if let characters = panel.characters {
                NavigationLink(characters.title) {
                    characterPanel(characters)
                }.disabled(live["inGame"] as? Bool != true)
            }
        } header: { Text("实时修改") }
    }

    private func resourceSummary(_ id: String) -> [String] {
        func value(_ key: String) -> String { ModifierBridge.integer(live[key]).map(String.init) ?? "—" }
        switch id {
        case "rance01":
            return ["手持芯片 \(value("handChips")) / \(value("handChipCap"))", "仓库芯片 \(value("storageChips")) / \(value("storageChipCap"))"]
        case "rance02":
            return ["世界药 \(value("worldDrug")) · 回城树 \(value("returnTree")) · 伤药 \(value("potion"))",
                    "光玉 \(value("light")) · 绳 \(value("rope"))"]
        case "sengokurance":
            return ["回合 \(value("turn"))"]
        default:
            return []
        }
    }

    private func characterPanel(_ characters: ModifierCharacters) -> some View {
        let rows = characters.rows(in: live)
        return Form {
            if rows.isEmpty { Text("暂无角色数据").foregroundColor(.secondary) }
            ForEach(rows) { character in
                NavigationLink {
                    ModifierEditor(character.name, fields: characters.fields, state: character.state,
                                   quickAction: ModifierCheat(title: "一键强化", action: characters.boost, done: "")) {
                        ModifierBridge.send(characters.action(for: character, values: $0))
                    }
                } label: {
                    Text(character.name)
                }
            }
        }
        .modifier(HubListStyle())
        .navigationTitle(characters.title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var escalayerPanel: some View {
        Section {
            NavigationLink("属性与战斗") {
                Form {
                    Section {
                        let fields = [("hp", "当前 HP"), ("hpMax", "最大 HP"), ("enemyHp", "敌方 HP"), ("atk", "攻击"),
                                      ("def", "防御"), ("rsp", "反应"), ("d2", "D2 污染"), ("peace", "和平度"), ("love", "爱情度")]
                        ForEach(fields, id: \.0) { key, label in
                            if let value = ModifierBridge.integer(live[key]) {
                                HStack { Text(label); Spacer(); Text(String(value)).monospacedDigit() }
                            }
                        }
                    }
                    Section {
                        ForEach(ModifierGamePanel.escalayerActions, id: \.1) { label, action in
                            Button(label) { xsystem35_modifier_apply_action(action) }
                        }
                    }
                }
                .modifier(HubListStyle())
                .navigationTitle("属性与战斗")
                .navigationBarTitleDisplayMode(.inline)
            }
        } header: { Text("实时修改") }
    }

    private var cheatCodeSection: some View {
        Section {
            TextField("如 whosyourdaddy / 打败哈尼王 / 餐券10", text: $code)
                .textInputAutocapitalization(.never).disableAutocorrection(true)
                .onSubmit(applyCode)
            HStack {
                Menu("常用作弊码…") {
                    ForEach(ModifierBridge.cheatCodePresets, id: \.code) { preset in
                        Button("\(preset.title) · \(preset.code)") { code = preset.code }
                    }
                }
                Spacer()
                Button("执行", action: applyCode).buttonStyle(.borderedProminent)
            }
            if let status { Text(status).font(.footnote).foregroundColor(.secondary) }
        } header: { Text("作弊码") }
    }

    private func lockSection(_ game: ModifierGame) -> some View {
        Section {
            ForEach(game.locks) { lock in
                Toggle(lock.title, isOn: Binding(
                    get: { mask & lock.bit != 0 },
                    set: { on in setMask(on ? mask | lock.bit : mask & ~lock.bit) }))
            }
            Button("全部关闭", role: .destructive) {
                setMask(mask & ~game.locks.reduce(0) { $0 | $1.bit })
            }
        } header: { Text("实时锁定") }
    }

    private func cheatSection(_ game: ModifierGame) -> some View {
        Section {
            ForEach(game.cheats) { cheat in
                Button(cheat.title) {
                    ModifierBridge.send(cheat.action)
                    status = cheat.done
                }.disabled(ModifierGamePanel.all.contains(where: { $0.game.id == game.id })
                           && live["inGame"] as? Bool != true
                           && cheat.action["r01UnlockGallery"] == nil && cheat.action["r02UnlockGallery"] == nil)
            }
            if !game.supportsCheatCodes, let status { Text(status).foregroundColor(.secondary) }
        } header: { Text("常用作弊") }
    }

    private var liveSection: some View {
        Section {
            let rows = live.compactMap { key, value -> (String, String)? in
                guard value is NSNumber || value is String else { return nil }
                return (key, "\(value)")
            }.sorted { $0.0 < $1.0 }
            ForEach(rows, id: \.0) { row in
                HStack { Text(row.0); Spacer(); Text(row.1).foregroundColor(.secondary).monospacedDigit() }
            }
            Button("刷新") { refreshID += 1 }
        } header: { Text("实时数值") }
    }

    private func applyCode() {
        let trimmed = ModifierBridge.normalizeCheatCode(code)
        if ModifierBridge.applyCheatCode(trimmed) {
            dismiss()
        } else {
            status = "请输入作弊码"
        }
    }

    private func setMask(_ value: Int32) {
        if system35 {
            xsystem35_modifier_set_locks(value)
        } else if let lockGame = game?.lockGame {
            ModifierBridge.setLockMask(lockGame, value)
        }
        mask = value
    }
}
