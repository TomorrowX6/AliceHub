import SwiftUI

struct GameDetailView: View {
    let game: Game
    @ObservedObject var library: GameLibrary
    @Environment(\.dismiss) private var dismiss
    @State private var destination: Destination?
    @State private var deleting = false

    private enum Destination: String, Identifiable {
        case saves, gamepad, mods, editor
        var id: String { rawValue }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(spacing: 16) {
                    GameArtwork(game: game, size: 100)
                    Text(ModOverlay.displayName(game.url, baseName: game.name))
                        .font(.title2.bold()).multilineTextAlignment(.center)
                    HStack(spacing: 8) {
                        HubBadge(title: "已安装", success: true)
                        HubBadge(title: game.engine.rawValue)
                    }
                    Button { library.launch(game) } label: {
                        Label("开始游戏", systemImage: "play.fill")
                    }.buttonStyle(HubPrimaryButton()).padding(.top, 8)
                }.frame(maxWidth: .infinity).modifier(HubCard())

                Text("游戏管理").font(.title3.bold())
                VStack(spacing: 0) {
                    action("存档管理", "externaldrive", .saves)
                    Divider()
                    if let descriptor = ModGameDescriptor.classifyDirectory(game.url) {
                        action(descriptor.isCnPatch ? "汉化补丁" : "MOD 管理", "shippingbox", .mods)
                        Divider()
                    }
                    if game.engine == .system4 {
                        action("手柄设置", "gamecontroller", .gamepad)
                    } else {
                        HubActionLabel(title: "原生触控", symbol: "hand.tap", navigates: false)
                    }
                    if SaveEditorView.supports(game.url) {
                        Divider()
                        action("修改存档", "slider.horizontal.3", .editor)
                    }
                }.modifier(HubCard())

                VStack(alignment: .leading, spacing: 12) {
                    Label("本机游戏目录", systemImage: "folder").font(.subheadline.bold())
                    Text(game.name).font(.footnote).foregroundColor(HubTheme.secondary).textSelection(.enabled)
                }.frame(maxWidth: .infinity, alignment: .leading).modifier(HubCard())

                Button(role: .destructive) { deleting = true } label: {
                    Label("删除游戏", systemImage: "trash").frame(maxWidth: .infinity, minHeight: 48)
                }
            }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
        }
        .background(HubTheme.background.ignoresSafeArea())
        .navigationTitle("游戏详情").navigationBarTitleDisplayMode(.inline)
        .disabled(library.launching || library.importing)
        .sheet(item: $destination) { target in
            Group {
                switch target {
                case .saves: SaveManagerView(game: game)
                case .gamepad: GamepadEditor(game: game.id)
                case .editor: SaveEditorView(game: game, directory: library.savesDirectory(for: game))
                case .mods:
                    if let descriptor = ModGameDescriptor.classifyDirectory(game.url) {
                        ModManagerView(game: game, descriptor: descriptor, library: library)
                    }
                }
            }.modifier(HubAppearanceModifier())
        }
        .alert("删除游戏？", isPresented: $deleting) {
            Button("取消", role: .cancel) {}
            Button("删除", role: .destructive) {
                library.delete(game)
                if !library.games.contains(where: { $0.id == game.id }) { dismiss() }
            }
        } message: { Text("将删除游戏文件夹，存档不会被删除。") }
    }

    private func action(_ title: String, _ symbol: String, _ target: Destination) -> some View {
        Button { destination = target } label: {
            HubActionLabel(title: title, symbol: symbol)
        }.buttonStyle(.plain)
    }
}
