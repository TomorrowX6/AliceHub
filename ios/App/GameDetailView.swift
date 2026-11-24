import SwiftUI

struct GameDetailView: View {
    let game: Game
    @ObservedObject var library: GameLibrary
    @Environment(\.dismiss) private var dismiss
    @HubLocalizedText private var localized
    @State private var destination: Destination?
    @State private var deleting = false
    @State private var modDescriptor: ModGameDescriptor?
    @State private var uncensoredDescriptor: UncensoredGameDescriptor?
    @State private var uncensoredInstalled = false
    @State private var supportsSaveEditor = false
    @State private var loadedCapabilities = false

    private var currentGame: Game {
        library.games.first(where: { $0.id == game.id }) ?? game
    }

    private enum Destination: String, Identifiable {
        case saves, gamepad, mods, uncensored, editor, manual
        var id: String { rawValue }
    }

    var body: some View {
        let active = currentGame
        let hasChinese = OnlineGame.hasChinese(for: active)
        let manual = HubToolFiles.manual(in: active.url)
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(spacing: 16) {
                    GameArtwork(game: active, size: 100)
                    Text(OnlineGame.displayTitle(for: active, in: localized.language))
                        .font(.title2.bold()).multilineTextAlignment(.center)
                    HStack(spacing: 8) {
                        HubBadge(title: "已安装", success: true)
                        HubBadge(title: OnlineGame.chineseBadgeKey(hasChinese: hasChinese), tone: hasChinese ? .primary : .warning)
                        HubBadge(title: active.engine.rawValue)
                    }
                    Button { library.launch(active) } label: {
                        Label("开始游戏", systemImage: "play.fill")
                    }.buttonStyle(HubPrimaryButton()).padding(.top, 8)
                }.frame(maxWidth: .infinity).modifier(HubCard())

                Text("游戏管理").font(.title3.bold())
                VStack(spacing: 0) {
                    action("存档管理", "externaldrive", .saves)
                    Divider()
                    if let descriptor = modDescriptor {
                        action(descriptor.isCnPatch ? "汉化补丁" : "MOD 管理", "shippingbox", .mods)
                        Divider()
                    }
                    if uncensoredDescriptor != nil {
                        action(uncensoredInstalled ? "无码补丁 · 已安装" : "无码补丁", "sparkles", .uncensored)
                        Divider()
                    }
                    if active.engine == .system4 {
                        action("手柄设置", "gamecontroller", .gamepad)
                    } else {
                        HubActionLabel(title: "原生触控", symbol: "hand.tap", navigates: false)
                    }
                    if supportsSaveEditor {
                        Divider()
                        action("修改存档", "slider.horizontal.3", .editor)
                    }
                    if manual != nil {
                        Divider()
                        action("游戏说明书", "book", .manual)
                    }
                }.modifier(HubCard())

                VStack(alignment: .leading, spacing: 12) {
                    Label("本机游戏目录", systemImage: "folder").font(.subheadline.bold())
                    Text(active.name).font(.footnote).foregroundColor(HubTheme.secondary).textSelection(.enabled)
                }.frame(maxWidth: .infinity, alignment: .leading).modifier(HubCard())

                Button(role: .destructive) { deleting = true } label: {
                    Label("删除游戏", systemImage: "trash").frame(maxWidth: .infinity, minHeight: 48)
                }
            }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
        }
        .background(HubTheme.background.ignoresSafeArea())
        .navigationTitle("游戏详情").navigationBarTitleDisplayMode(.inline)
        .disabled(library.launching || library.importing)
        .onAppear {
            if !loadedCapabilities {
                loadedCapabilities = true
                modDescriptor = ModGameDescriptor.classifyDirectory(active.url)
                uncensoredDescriptor = UncensoredGameDescriptor.classifyDirectory(active.url)
                supportsSaveEditor = SaveEditorView.supports(active.url)
            }
            uncensoredInstalled = UncensoredPatchStore.isInstalled(active.url)
        }
        .sheet(item: $destination, onDismiss: {
            uncensoredInstalled = UncensoredPatchStore.isInstalled(currentGame.url)
        }) { target in
            Group {
                switch target {
                case .saves: SaveManagerView(game: active)
                case .gamepad: GamepadEditor(game: active.id)
                case .editor: SaveEditorView(game: active, directory: library.savesDirectory(for: active))
                case .manual:
                    if let manual { HubManualView(manual: manual) }
                case .mods:
                    if let descriptor = modDescriptor ?? ModGameDescriptor.classifyDirectory(active.url) {
                        ModManagerView(game: active, descriptor: descriptor, library: library)
                    }
                case .uncensored:
                    if let unc = uncensoredDescriptor ?? UncensoredGameDescriptor.classifyDirectory(active.url) {
                        UncensoredPatchView(game: active, descriptor: unc, library: library)
                    }
                }
            }.modifier(HubAppearanceModifier())
        }
        .alert("删除游戏？", isPresented: $deleting) {
            Button("取消", role: .cancel) {}
            Button("删除", role: .destructive) {
                library.delete(active)
                if !library.games.contains(where: { $0.id == active.id }) { dismiss() }
            }
        } message: { Text("将删除游戏文件夹，存档不会被删除。") }
    }

    private func action(_ title: String, _ symbol: String, _ target: Destination) -> some View {
        Button { destination = target } label: {
            HubActionLabel(title: title, symbol: symbol)
        }.buttonStyle(.plain)
    }
}
