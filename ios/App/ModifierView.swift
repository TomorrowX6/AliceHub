import SwiftUI

struct ModifierView: View {
    let dismiss: () -> Void
    private let game = ModifierBridge.currentGame()
    @State private var mask: Int32 = 0
    @State private var code = ""
    @State private var status: String?
    @State private var live: [String: Any] = [:]
    @State private var refreshID = 0

    var body: some View {
        NavigationView {
            Form {
                Section {
                    HubSectionHeader(title: "局内修改器", subtitle: game?.name ?? "当前游戏", symbol: "slider.horizontal.3")
                }.listRowBackground(HubTheme.surface)
                if let game {
                    if game.supportsCheatCodes { cheatCodeSection }
                    if let lockGame = game.lockGame, !game.locks.isEmpty { lockSection(game, lockGame) }
                    if !game.cheats.isEmpty { cheatSection(game) }
                } else {
                    Section { Text("当前游戏暂不支持修改器。").foregroundColor(.secondary) }
                }
                liveSection
            }
            .modifier(HubListStyle())
            .navigationTitle(game?.name ?? hubText("修改器"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成", action: dismiss) } }
            .task(id: refreshID) {
                if let lockGame = game?.lockGame { mask = ModifierBridge.lockMask(lockGame) }
                await ModifierBridge.observeLiveState { live = $0 }
            }
        }
        .navigationViewStyle(.stack)
        .modifier(HubAppearanceModifier())
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

    private func lockSection(_ game: ModifierGame, _ lockGame: modifier_lock_game) -> some View {
        Section {
            ForEach(game.locks) { lock in
                Toggle(lock.title, isOn: Binding(
                    get: { mask & lock.bit != 0 },
                    set: { on in setMask(lockGame, on ? mask | lock.bit : mask & ~lock.bit) }))
            }
            Button("全部关闭", role: .destructive) {
                setMask(lockGame, mask & ~game.locks.reduce(0) { $0 | $1.bit })
            }
        } header: { Text("实时锁定") }
    }

    private func cheatSection(_ game: ModifierGame) -> some View {
        Section {
            ForEach(game.cheats) { cheat in
                Button(cheat.title) {
                    ModifierBridge.send(cheat.action)
                    status = cheat.done
                }
            }
            if !game.supportsCheatCodes, let status { Text(status).font(.footnote).foregroundColor(.secondary) }
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

    private func setMask(_ lockGame: modifier_lock_game, _ value: Int32) {
        ModifierBridge.setLockMask(lockGame, value)
        mask = value
    }
}
