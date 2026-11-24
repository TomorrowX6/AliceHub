import SwiftUI

struct GameDetailView: View {
    let game: Game
    @ObservedObject var library: GameLibrary
    @Environment(\.dismiss) private var dismiss
    @State private var selectedGameID: String
    @State private var destination: Destination?
    @State private var deleting = false
    @State private var switchingInstance = false
    @State private var creatingInstance = false
    @State private var newDirName = ""
    @State private var cloneSourceID = ""
    @State private var cleanVanilla = true
    @State private var createError: String?
    @State private var modDescriptor: ModGameDescriptor?
    @State private var uncensoredDescriptor: UncensoredGameDescriptor?
    @State private var uncensoredInstalled = false
    @State private var supportsSaveEditor = false
    @State private var loadedCapabilities = false

    init(game: Game, library: GameLibrary) {
        self.game = game
        self.library = library
        _selectedGameID = State(initialValue: game.id)
    }

    private var currentGame: Game {
        library.games.first(where: { $0.id == selectedGameID })
            ?? library.games.first(where: { $0.id == game.id })
            ?? game
    }

    private var rance10Instances: [Game] {
        library.games.filter { $0.artworkKey == "rance10" }
    }

    private enum Destination: String, Identifiable {
        case saves, gamepad, mods, uncensored, editor
        var id: String { rawValue }
    }

    var body: some View {
        let active = currentGame
        let isRance10 = active.artworkKey == "rance10"
        let hasChinese = OnlineGame.hasChinese(for: active)
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(spacing: 16) {
                    GameArtwork(game: active, size: 100)
                    Text(active.resolvedDisplayName)
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
                }.modifier(HubCard())

                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Label("本机游戏目录", systemImage: "folder").font(.subheadline.bold())
                        Spacer()
                        if isRance10 {
                            HubBadge(title: instanceStatusBadge(active), tone: ModOverlay.detectInstalledMod(active.url) ? .primary : .neutral)
                        }
                    }
                    Text(active.name).font(.footnote).foregroundColor(HubTheme.secondary).textSelection(.enabled)
                    if isRance10 {
                        HStack(spacing: 10) {
                            Button {
                                switchingInstance = true
                            } label: {
                                Label("切换目录", systemImage: "arrow.left.arrow.right")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 40)
                                    .foregroundColor(HubTheme.primary)
                                    .background(HubTheme.variant)
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            }
                            .buttonStyle(.plain)

                            Button {
                                prepareCreateInstanceSheet()
                            } label: {
                                Label("新建目录", systemImage: "folder.badge.plus")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 40)
                                    .foregroundColor(HubTheme.primary)
                                    .background(HubTheme.variant)
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.top, 2)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading).modifier(HubCard())

                Button(role: .destructive) { deleting = true } label: {
                    Label("删除游戏", systemImage: "trash").frame(maxWidth: .infinity, minHeight: 48)
                }
            }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
        }
        .background(HubTheme.background.ignoresSafeArea())
        .navigationTitle("游戏详情").navigationBarTitleDisplayMode(.inline)
        .disabled(library.launching || library.importing)
        .overlay {
            if library.importing {
                ZStack {
                    Color.black.opacity(0.25).ignoresSafeArea()
                    VStack(spacing: 16) {
                        ProgressView().scaleEffect(1.2)
                        Text(library.importProgress.isEmpty ? "正在处理…" : library.importProgress)
                            .font(.subheadline).foregroundColor(HubTheme.text)
                            .multilineTextAlignment(.center)
                    }
                    .padding(24).frame(maxWidth: 300)
                    .background(HubTheme.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                }
            }
        }
        .onAppear {
            if !loadedCapabilities {
                loadedCapabilities = true
                reloadCapabilities(for: active)
            } else {
                uncensoredInstalled = UncensoredPatchStore.isInstalled(active.url)
            }
        }
        .onChange(of: selectedGameID) { _ in
            reloadCapabilities(for: currentGame)
        }
        .confirmationDialog("选择游戏目录", isPresented: $switchingInstance, titleVisibility: .visible) {
            ForEach(rance10Instances) { instance in
                Button(instance.id == active.id ? "✓ \(instanceLabel(instance))" : instanceLabel(instance)) {
                    selectedGameID = instance.id
                }
            }
            Button("新建目录") {
                prepareCreateInstanceSheet()
            }
            Button("取消", role: .cancel) {}
        }
        .sheet(isPresented: $creatingInstance) {
            createInstanceSheet
                .modifier(HubAppearanceModifier())
        }
        .sheet(item: $destination, onDismiss: {
            library.refresh()
            reloadCapabilities(for: currentGame)
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
                let wasRance10 = active.artworkKey == "rance10"
                let deletedID = active.id
                library.delete(active)
                if !library.games.contains(where: { $0.id == deletedID }) {
                    if wasRance10, let remaining = library.games.first(where: { $0.artworkKey == "rance10" }) {
                        selectedGameID = remaining.id
                    } else {
                        dismiss()
                    }
                }
            }
        } message: { Text("将删除游戏文件夹，存档不会被删除。") }
    }

    private func reloadCapabilities(for target: Game) {
        modDescriptor = ModGameDescriptor.classifyDirectory(target.url)
        uncensoredDescriptor = UncensoredGameDescriptor.classifyDirectory(target.url)
        supportsSaveEditor = SaveEditorView.supports(target.url)
        uncensoredInstalled = UncensoredPatchStore.isInstalled(target.url)
    }

    private func instanceStatusBadge(_ item: Game) -> String {
        if ModOverlay.detectInstalledMod(item.url) {
            if let info = InstalledModInfo.readOrInfer(item.url) {
                if !info.group.isEmpty && info.group.caseInsensitiveCompare(info.version) != .orderedSame {
                    return "\(info.group) · \(info.version)"
                }
                return info.version
            }
            return "MOD"
        }
        return "原版"
    }

    private func instanceLabel(_ item: Game) -> String {
        "\(item.name) · \(instanceStatusBadge(item))"
    }

    private func prepareCreateInstanceSheet() {
        let instances = rance10Instances
        guard !instances.isEmpty else { return }
        let defaultSource = instances.first(where: { !ModOverlay.detectInstalledMod($0.url) }) ?? currentGame
        cloneSourceID = defaultSource.id
        newDirName = library.suggestNextDirectoryName(basePrefix: "Rance10")
        cleanVanilla = true
        createError = nil
        creatingInstance = true
    }

    private var selectedCloneSource: Game {
        rance10Instances.first(where: { $0.id == cloneSourceID }) ?? currentGame
    }

    private var createInstanceSheet: some View {
        let instances = rance10Instances
        let source = selectedCloneSource
        let sourceHasMod = ModOverlay.detectInstalledMod(source.url)
        let canRestoreVanilla = sourceHasMod && ModOverlay.canRestoreVanilla(source.url)
        let willBeVanilla = !sourceHasMod || (canRestoreVanilla && cleanVanilla)
        return NavigationView {
            Form {
                Section(header: Text("目录名称")) {
                    TextField("例如：Rance10-原版、Rance10-日不落", text: $newDirName)
                        .disableAutocorrection(true)
                        .textInputAutocapitalization(.never)
                    if let err = createError {
                        Text(err).font(.footnote).foregroundColor(.red)
                    }
                }
                if instances.count > 1 {
                    Section(header: Text("基于现有目录")) {
                        Picker("来源目录", selection: $cloneSourceID) {
                            ForEach(instances) { item in
                                Text(instanceLabel(item)).tag(item.id)
                            }
                        }
                        .onChange(of: cloneSourceID) { _ in
                            cleanVanilla = true
                        }
                    }
                }
                if canRestoreVanilla {
                    Section {
                        Toggle("去除 MOD，创建为纯净原版（推荐）", isOn: $cleanVanilla)
                    }
                }
                Section {
                    Text(
                        willBeVanilla
                            ? "将基于「\(source.name)」创建独立原版目录，用于原版游玩或安装其他版本 MOD。不同目录的存档与 MOD 互不影响。"
                            : (!canRestoreVanilla && sourceHasMod
                                ? "当前目录为整合包且无原版备份，将完整复制其游戏文件作为新目录。不同目录的存档互不影响。"
                                : "将复制「\(source.name)」的当前游戏文件（含已安装 MOD）作为新目录。不同目录的存档与 MOD 互不影响。")
                    )
                    .font(.footnote)
                    .foregroundColor(HubTheme.secondary)
                }
            }
            .navigationTitle("新建兰斯10目录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { creatingInstance = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建") {
                        switch library.validateNewDirectoryName(newDirName) {
                        case .invalid(let reason):
                            createError = reason
                        case .valid(let sanitized):
                            createError = nil
                            creatingInstance = false
                            let shouldClean = canRestoreVanilla && cleanVanilla
                            let transform: ((URL) throws -> Void)? = shouldClean ? { stage in
                                if ModOverlay.canRestoreVanilla(stage) {
                                    try ModOverlay.restoreVanilla(stage)
                                }
                            } : nil
                            library.cloneDirectory(from: source, named: sanitized, transformStage: transform) { created in
                                if let created {
                                    selectedGameID = created.id
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func action(_ title: String, _ symbol: String, _ target: Destination) -> some View {
        Button { destination = target } label: {
            HubActionLabel(title: title, symbol: symbol)
        }.buttonStyle(.plain)
    }
}
