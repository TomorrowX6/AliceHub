import SwiftUI

struct HubSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("hub.appearance") private var appearance = HubAppearance.system.rawValue
    @AppStorage(DownloadRoute.storageKey) private var downloadRoute = DownloadRoute.global.rawValue
    @State private var gamepad = false

    var body: some View {
        NavigationView {
            List {
                Section("下载线路") {
                    Picker("下载线路", selection: $downloadRoute) {
                        ForEach(DownloadRoute.allCases) { Text($0.title).tag($0.rawValue) }
                    }.pickerStyle(.segmented).padding(.vertical, 8)
                }.listRowBackground(HubTheme.surface)
                Section("外观") {
                    Picker("主题模式", selection: $appearance) {
                        ForEach(HubAppearance.allCases) { Text($0.title).tag($0.rawValue) }
                    }.pickerStyle(.segmented).padding(.vertical, 8)
                }.listRowBackground(HubTheme.surface)
                Section("操作") {
                    Button { gamepad = true } label: {
                        HubActionLabel(title: "手柄设置", symbol: "gamecontroller")
                    }.buttonStyle(.plain)
                }.listRowBackground(HubTheme.surface)
                Section("关于") {
                    Link(destination: URL(string: "https://github.com/TomorrowX6/AliceHub")!) {
                        HubActionLabel(title: "AliceHub", symbol: "curlybraces")
                    }
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
    let online: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(spacing: 0) {
                        Button(action: online) {
                            HubActionLabel(title: "在线下载", symbol: "arrow.down.circle")
                        }
                        Divider()
                        Button(action: folder) {
                            HubActionLabel(title: "文件夹", symbol: "folder.badge.plus")
                        }
                        Divider()
                        Button(action: archive) {
                            HubActionLabel(title: "压缩包 / 光盘", symbol: "archivebox")
                        }
                    }.buttonStyle(.plain).modifier(HubCard())
                }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
            }.background(HubTheme.background.ignoresSafeArea())
                .navigationTitle("导入游戏").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
        }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
    }
}
