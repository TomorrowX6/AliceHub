import SwiftUI

struct GameDetailView: View {
    let game: Game
    @ObservedObject var library: GameLibrary
    @ObservedObject var download: OnlineGameDownloadModel
    let catalog: OnlineGame?
    let installations: [Game]
    @Binding var selectedID: String?
    @State private var destination: Destination?
    @State private var deleting = false
    @State private var modDescriptor: ModGameDescriptor?
    @State private var uncensoredDescriptor: UncensoredGameDescriptor?
    @State private var uncensoredInstalled = false
    @AppStorage(OnlineInstall.storageKey) private var onlineInstall = false
    @State private var supportsSaveEditor = false
    @State private var loadedCapabilities = false

    private var currentGame: Game {
        library.games.first(where: { $0.id == game.id }) ?? game
    }

    private enum Destination: String, Identifiable {
        case saves, gamepad, mods, uncensored, editor
        var id: String { rawValue }
    }

    var body: some View {
        let active = currentGame
        let hasChinese = OnlineGame.hasChinese(for: active)
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(spacing: 16) {
                    GameArtwork(game: active, catalogID: catalog?.artwork, size: 100)
                    Text(catalog?.title ?? active.resolvedDisplayName)
                        .font(.title2.bold()).multilineTextAlignment(.center)
                    HStack(spacing: 8) {
                        HubBadge(title: "已安装", success: true)
                        HubBadge(title: hasChinese ? "熟肉" : "生肉", tone: hasChinese ? .primary : .warning)
                        HubBadge(title: active.engine.rawValue)
                    }
                    Button { library.launch(active) } label: {
                        Label("开始游戏", systemImage: "play.fill")
                    }.buttonStyle(HubPrimaryButton()).padding(.top, 8)
                }.frame(maxWidth: .infinity).modifier(HubCard())

                VStack(alignment: .leading, spacing: 12) {
                    Label("本机游戏目录", systemImage: "folder").font(.subheadline.bold())
                    Text(active.name).foregroundColor(HubTheme.text).textSelection(.enabled)
                    if installations.count > 1 {
                        Menu {
                            Picker("切换目录", selection: Binding(
                                get: { active.id }, set: { selectedID = $0 }
                            )) {
                                ForEach(installations) { installation in
                                    Text(installationLabel(installation)).tag(installation.id)
                                }
                            }
                        } label: {
                            Label("切换目录", systemImage: "arrow.left.arrow.right")
                        }
                    }
                }.frame(maxWidth: .infinity, alignment: .leading).modifier(HubCard())

                Text("游戏管理").font(.title3.bold())
                VStack(spacing: 0) {
                    if let catalog {
                        NavigationLink {
                            OnlineGameDetailView(game: catalog, library: library, download: download)
                        } label: {
                            HubActionLabel(title: "在线下载", symbol: "arrow.down.circle")
                        }.buttonStyle(.plain)
                        Divider()
                    }
                    action("存档管理", "externaldrive", .saves)
                    Divider()
                    if let descriptor = modDescriptor {
                        action(descriptor.isCnPatch ? "汉化补丁" : "MOD 管理", "shippingbox", .mods)
                        Divider()
                    }
                    if uncensoredDescriptor != nil && (onlineInstall || uncensoredInstalled) {
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
                }.modifier(HubCard())

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
                if !library.games.contains(where: { $0.id == active.id }) { selectedID = nil }
            }
        } message: { Text("将删除游戏文件夹，存档不会被删除。") }
    }

    private func installationLabel(_ game: Game) -> String {
        guard let mod = InstalledModInfo.read(game.url) else { return game.name }
        let group = mod.kind != "cn_patch" && mod.group.caseInsensitiveCompare(mod.version) != .orderedSame
            ? mod.group : ""
        return [game.name, group, mod.version].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    private func action(_ title: String, _ symbol: String, _ target: Destination) -> some View {
        Button { destination = target } label: {
            HubActionLabel(title: title, symbol: symbol)
        }.buttonStyle(.plain)
    }
}
