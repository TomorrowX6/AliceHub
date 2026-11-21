import SwiftUI

struct HubSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("hub.appearance") private var appearance = HubAppearance.system.rawValue
    @State private var gamepad = false

    var body: some View {
        NavigationView {
            List {
                Section {
                    HubSectionHeader(title: "让游戏更合你意", subtitle: "外观与操作，按你的习惯设置。", symbol: "gearshape")
                }.listRowBackground(HubTheme.surface)
                Section("外观") {
                    Picker("主题模式", selection: $appearance) {
                        ForEach(HubAppearance.allCases) { Text($0.title).tag($0.rawValue) }
                    }.pickerStyle(.segmented).padding(.vertical, 8)
                }.listRowBackground(HubTheme.surface)
                Section("操作") {
                    Button { gamepad = true } label: {
                        HubActionLabel(title: "虚拟手柄与外设", subtitle: "所有游戏的默认布局与按键", symbol: "gamecontroller")
                    }.buttonStyle(.plain)
                }.listRowBackground(HubTheme.surface)
                Section("关于") {
                    Link(destination: URL(string: "https://github.com/TomorrowX6/AliceHub")!) {
                        HubActionLabel(title: "AliceHub", subtitle: "开源的 AliceSoft 原生运行引擎", symbol: "curlybraces")
                    }
                    Text("请仅导入你合法拥有的游戏资源。游戏和存档均保存在本机。")
                        .font(.footnote).foregroundColor(HubTheme.secondary)
                }.listRowBackground(HubTheme.surface)
            }
            .listStyle(.insetGrouped).modifier(HubListStyle())
            .navigationTitle("设置").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
            .sheet(isPresented: $gamepad) { GamepadEditor(game: nil).modifier(HubAppearanceModifier()) }
    }
}

struct ImportGameView: View {
    let folder: () -> Void
    let archive: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 12) {
                        Image(systemName: "square.and.arrow.down").font(.system(size: 36, weight: .light))
                            .foregroundColor(HubTheme.primary).padding(.bottom, 8)
                        Text("把喜欢的游戏带过来").font(.title2.bold())
                        Text("选择完整的游戏资源，AliceHub 会自动识别引擎并加入游戏库。")
                            .font(.subheadline).foregroundColor(HubTheme.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).modifier(HubCard())
                    VStack(spacing: 0) {
                        Button(action: folder) {
                            HubActionLabel(title: "从文件夹导入", subtitle: "已解压的 System 3.x / System 4 游戏", symbol: "folder.badge.plus")
                        }
                        Divider()
                        Button(action: archive) {
                            HubActionLabel(title: "从压缩包或光盘导入", subtitle: "ZIP / RAR / 7z / TAR / ISO / MDF", symbol: "archivebox")
                        }
                    }.buttonStyle(.plain).modifier(HubCard())
                    VStack(alignment: .leading, spacing: 10) {
                        Label("也可以使用「文件」App", systemImage: "info.circle").font(.subheadline.bold())
                        Text("将游戏文件夹复制到「我的 iPhone / AliceHub / Games」，返回后会自动刷新游戏库。")
                            .font(.footnote).foregroundColor(HubTheme.secondary)
                    }.modifier(HubCard())
                }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
            }.background(HubTheme.background.ignoresSafeArea())
                .navigationTitle("导入游戏").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
        }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
    }
}
