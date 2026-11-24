import SwiftUI

struct GlobalSaveEditorView: View {
    private struct Field: Identifiable {
        let id: String
        let title: String
        let maximum: Double
        var source: String? = nil
        var batch = false
        var decimal = false
    }

    private enum Action: String, Identifiable {
        case unlock = "写入全解锁存档"
        case maximize = "一键拉满数值"
        case restoreEdit = "恢复修改前存档"
        case restoreUnlock = "恢复解锁前存档"

        var id: String { rawValue }
    }

    private static let fields: [Field] = [
        .init(id: "totalClearPoint", title: "周回 CP", maximum: 999),
        .init(id: "clearBPointMax", title: "第二部最高 CP", maximum: 999),
        .init(id: "AutoBattleMedal", title: "自动战斗勋章", maximum: 999),
        .init(id: "AchievementPoint", title: "实绩点数", maximum: 999),
        .init(id: "TotalClearCount", title: "总通关次数", maximum: 99),
        .init(id: "Clear_A_Count", title: "A 路线通关", maximum: 99),
        .init(id: "Clear_B_Count", title: "B 路线通关", maximum: 99),
        .init(id: "Clear_C_Count", title: "C 路线通关", maximum: 99),
        .init(id: "BestScore", title: "最高得分", maximum: 999999),
        .init(id: "LeaderSmallestTurn", title: "魔王最少回合", maximum: 1),
        .init(id: "RealSmallestTurn", title: "第二部最少回合", maximum: 1),
        .init(id: "CollectionClearB", title: "第二部通关（0/1）", maximum: 1),
        .init(id: "OpenMonsterpedia", title: "魔物图鉴（0/1）", maximum: 1),
        .init(id: "TrophyComplete", title: "实绩全达成（0/1）", maximum: 1),
        .init(id: "CardComplete", title: "卡牌全收集（0/1）", maximum: 1),
        .init(id: "gamePlayCount", title: "周回次数", maximum: 50, source: "hasGlobal"),
        .init(id: "itemStarRank", title: "装备星级上限", maximum: 20, source: "hasGlobal"),
        .init(id: "leaderAtkRatio", title: "首领攻击倍率", maximum: 10, source: "hasCollection", decimal: true),
        .init(id: "trophyExpBonus", title: "实绩经验加成 %", maximum: 9999, source: "hasCollection"),
        .init(id: "trophyStatusMaxPer", title: "属性上限加成 %", maximum: 999, source: "hasCollection"),
        .init(id: "setAllCharRank", title: "全角色阶级", maximum: 10, source: "hasGlobal", batch: true),
        .init(id: "setTakeOverCardCount", title: "继承卡张数", maximum: 99, source: "hasGlobal", batch: true),
    ]

    let editor: GlobalSaveEditor
    @State private var fields: [Field] = []
    @State private var original: [String: String] = [:]
    @State private var values: [String: String] = [:]
    @State private var editBackup = false
    @State private var unlockBackup = false
    @State private var action: Action?
    @State private var message: String?
    @State private var notice = "暂无全局存档"

    var body: some View {
        Form {
            Section {
                Button(Action.unlock.rawValue) { action = .unlock }
                if !fields.isEmpty { Button(Action.maximize.rawValue) { action = .maximize } }
                if editBackup { Button(Action.restoreEdit.rawValue) { action = .restoreEdit } }
                if unlockBackup { Button(Action.restoreUnlock.rawValue) { action = .restoreUnlock } }
                if let message { Text(message) }
            }.listRowBackground(HubTheme.surface)
            if fields.isEmpty {
                Text(notice).listRowBackground(HubTheme.surface)
            } else {
                Section {
                    ForEach(fields) { field in
                        HStack {
                            Text(field.title)
                            Spacer()
                            TextField(field.title, text: Binding(get: { values[field.id] ?? "" }, set: { values[field.id] = $0 }))
                                .labelsHidden().keyboardType(.numbersAndPunctuation)
                                .multilineTextAlignment(.trailing).frame(maxWidth: 120)
                        }
                    }
                } header: { Text("自定义数值") }
                .listRowBackground(HubTheme.surface)
            }
        }
        .modifier(HubListStyle())
        .navigationTitle("全局存档").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) {
            Button("保存", action: save).disabled(values == original)
        } }
        .onAppear(perform: load)
        .alert(action?.rawValue ?? "", isPresented: Binding(get: { action != nil }, set: { if !$0 { action = nil } }), presenting: action) { item in
            Button("确定") { perform(item) }
            Button("取消", role: .cancel) {}
        } message: { item in
            Text(item == .unlock ? "将替换全局解锁进度，不影响进度存档。" : "此操作仅修改全局存档。")
        }
    }

    private func load() {
        fields = []
        original = [:]
        values = [:]
        notice = "暂无全局存档"
        do {
            editBackup = try editor.hasBackup(.edit)
            unlockBackup = try editor.hasBackup(.unlock)
            guard let paths = try editor.paths() else { return }
            let info = ModifierBridge.readGlobalSave(paths.global, paths.collection)
            let sources = Set(["hasGlobal", "hasCollection", "hasCommonSystemData"].filter { info[$0] as? Bool == true })
            guard !sources.isEmpty else { throw GlobalSaveEditor.Failure.invalidSave }
            fields = Self.fields.filter { $0.source.map(sources.contains) ?? true }
            original = Dictionary(uniqueKeysWithValues: fields.map { field in
                (field.id, field.batch ? "" : (info[field.id] as? NSNumber)?.stringValue ?? "0")
            })
            values = original
        } catch { notice = error.localizedDescription }
    }

    private func save() {
        var changes: [String: Double] = [:]
        for field in fields {
            let value = (values[field.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty, value != original[field.id] else { continue }
            if field.decimal, let number = Float(value), number.isFinite {
                changes[field.id] = Double(number)
            } else if !field.decimal, let number = Int32(value), !field.batch || number >= 0 {
                changes[field.id] = Double(number)
            } else {
                message = "\(field.title)数值无效。"
                return
            }
        }
        guard !changes.isEmpty else { return }
        update("已保存") { try edit(changes) }
    }

    private func perform(_ action: Action) {
        switch action {
        case .unlock:
            update("已写入全解锁存档") {
                try editor.unlock(using: Bundle.main.url(forResource: "full_unlock_saves", withExtension: "zip"))
            }
        case .maximize:
            let changes = Dictionary(uniqueKeysWithValues: fields.map { ($0.id, $0.maximum) })
            update("已拉满数值") { try edit(changes) }
        case .restoreEdit: update("已恢复存档") { try editor.restore(.edit) }
        case .restoreUnlock: update("已恢复存档") { try editor.restore(.unlock) }
        }
    }

    private func edit(_ changes: [String: Double]) throws {
        try editor.edit(changes) { paths, json in
            ModifierBridge.writeGlobalSave(paths.global, paths.collection, json)
        }
    }

    private func update(_ success: String, operation: () throws -> Void) {
        do {
            try operation()
            load()
            message = success
        } catch {
            load()
            message = error.localizedDescription
        }
    }
}
