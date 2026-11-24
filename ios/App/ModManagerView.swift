import SwiftUI
import UniformTypeIdentifiers

private struct ModSelection: Identifiable { let id: Int }
private struct ModImport: Identifiable { let folder: Bool; var id: Bool { folder } }

struct ModManagerView: View {
    @ObservedObject var library: GameLibrary
    @StateObject private var model: ModManagerModel
    @AppStorage(OnlineInstall.storageKey) private var onlineInstall = false
    @State private var localOptions = false
    @State private var switching = false
    @State private var confirmRestore = false
    @State private var selected: ModSelection?
    @State private var importing: ModImport?
    @State private var cleanLocal = false
    @State private var localMode = false
    @Environment(\.dismiss) private var dismiss

    init(game: Game, descriptor: ModGameDescriptor, library: GameLibrary) {
        self.library = library
        _model = StateObject(wrappedValue: ModManagerModel(directory: game.url, descriptor: descriptor))
    }

    var body: some View {
        NavigationView {
            List {
                Section {
                    HubSectionHeader(title: model.directory.lastPathComponent, subtitle: model.status, symbol: "shippingbox")
                    Button { switching = true } label: {
                        HubActionLabel(title: "切换目录", symbol: "folder")
                    }.buttonStyle(.plain)
                    Button {
                        if let plan = model.plan { model.install(plan.steps) }
                        else { Task { await model.loadCatalog() } }
                    } label: {
                        Label(model.updateTitle, systemImage: "arrow.down.circle").frame(maxWidth: .infinity)
                    }.buttonStyle(HubPrimaryButton()).disabled(model.updateDisabled)
                    Button { localMode = true } label: {
                        HubActionLabel(title: model.descriptor.isCnPatch ? "本地安装汉化补丁" : "本地安装 MOD", symbol: "archivebox")
                    }.buttonStyle(.plain)
                    Button(model.descriptor.isCnPatch ? "卸载汉化补丁" : "卸载 MOD", role: .destructive) { confirmRestore = true }
                        .disabled(!model.canRestore)
                    if model.switchBlockedByNoBackup {
                        Text("当前整合包无原版备份，无法原地切换其他 MOD 系列，请先安装原版游戏后再安装。")
                            .font(.footnote)
                            .foregroundColor(.orange)
                    }
                }.listRowBackground(HubTheme.surface)
                if let notice = model.notice {
                    Section { Label(notice, systemImage: "checkmark.circle").foregroundColor(.green) }
                }
                if onlineInstall {
                    Section("在线版本") {
                        if model.availableGroups.count > 1 {
                            Picker("MOD 系列", selection: Binding(
                                get: { model.selectedGroup },
                                set: { model.selectGroup($0) }
                            )) {
                                ForEach(model.availableGroups, id: \.self) { group in
                                    Text(group).tag(group)
                                }
                            }
                            .pickerStyle(.segmented)
                        }
                        if model.visibleReleases.isEmpty {
                            if model.loading { ProgressView("正在加载…") }
                            else { Text(model.loadFailed ? "获取失败，请检查网络" : (model.descriptor.isCnPatch ? "暂无可用汉化补丁" : "暂无可用版本")).foregroundColor(.secondary) }
                        }
                        ForEach(Array(model.visibleReleases.enumerated()), id: \.offset) { index, entry in
                            Button { selected = ModSelection(id: index) } label: {
                                ModReleaseRow(entry: entry, latest: index == 0, installed: model.installedIndex == index, base: isBase(entry, index))
                            }.buttonStyle(.plain)
                        }
                    }.listRowBackground(HubTheme.surface)
                }
            }
            .listStyle(.insetGrouped).modifier(HubListStyle())
            .disabled(model.busy)
            .navigationTitle(model.descriptor.isCnPatch ? "安装汉化补丁" : "MOD 管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() }.disabled(model.busy) }
                ToolbarItem(placement: .primaryAction) {
                    Button { Task { await model.loadCatalog() } } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("重新获取").disabled(model.busy || model.loading)
                }
            }
            .overlay { if model.busy { ModInstallProgress(installer: model.installer) } }
        }
        .navigationViewStyle(.stack)
        .modifier(HubAppearanceModifier())
        .interactiveDismissDisabled(model.busy)
        .task { await model.loadCatalog(); library.refresh() }
        .onChange(of: model.busy) { busy in if !busy { library.refresh() } }
        .onDisappear { library.refresh() }
        .confirmationDialog(model.descriptor.isCnPatch ? "本地安装汉化补丁" : "本地安装 MOD", isPresented: $localOptions, titleVisibility: .visible) {
            Button("从压缩包安装") { importing = ModImport(folder: false) }
            Button("从文件夹安装") { importing = ModImport(folder: true) }
            Button("取消", role: .cancel) {}
        } message: {
            Text(cleanLocal ? "完整安装将先还原原版，再安装所选文件。" : "增量安装保留现有 MOD 文件，请先安装对应基础版本。")
        }
        .confirmationDialog("选择本地安装方式", isPresented: $localMode, titleVisibility: .visible) {
            Button("完整安装（替换现有 MOD）") { cleanLocal = true; localOptions = true }
            Button("增量补丁（保留基础版本）") { cleanLocal = false; localOptions = true }
            Button("取消", role: .cancel) {}
        }
        .confirmationDialog("切换目录", isPresented: $switching, titleVisibility: .visible) {
            ForEach(library.games.filter { ModGameDescriptor.classifyDirectory($0.url) == model.descriptor }) { game in
                Button(directorySwitchLabel(for: game)) { model.select(game.url); library.refresh() }
            }
            Button("取消", role: .cancel) {}
        }
        .alert(model.descriptor.isCnPatch ? "卸载汉化补丁" : "卸载 MOD", isPresented: $confirmRestore) {
            Button("取消", role: .cancel) {}
            Button("确定", role: .destructive) { model.restore() }
        } message: { Text(model.descriptor.isCnPatch ? "确定卸载汉化补丁并还原原版吗？存档会保留。" : "确定卸载 MOD 并还原原版吗？存档会保留。") }
        .alert("操作失败", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
            Button("好", role: .cancel) { model.error = nil }
        } message: { Text(model.error ?? "") }
        .sheet(item: $selected) { selection in ModReleaseDetail(model: model, index: selection.id) }
        .sheet(item: $importing) { selection in
            ModDocumentPicker(folder: selection.folder) { source in
                importing = nil
                if let source { model.installLocal(source, folder: selection.folder, cleanReplace: cleanLocal) }
            }
        }
    }

    private func directorySwitchLabel(for game: Game) -> String {
        guard let info = InstalledModInfo.readOrInfer(game.url) else {
            return game.displayName + " · 原版"
        }
        if !model.descriptor.isCnPatch, !info.group.isEmpty, info.group.caseInsensitiveCompare(info.version) != .orderedSame {
            return game.displayName + " · " + info.group + " · " + info.version
        }
        return game.displayName + " · " + info.version
    }

    private func isBase(_ entry: ModReleaseEntry, _ index: Int) -> Bool {
        guard model.hasMod, model.installedIndex != index, !entry.isPatch, let info = model.installed,
              !ModCatalog.groupsConflict(entry.group, info.group) else { return false }
        return (info.baseFileId > 0 && info.baseFileId == entry.fileId) || info.baseVersion.map { !$0.isEmpty && ModCatalog.normalizeModVersionKey($0) == ModCatalog.normalizeModVersionKey(entry.version) } == true
    }
}

private struct ModInstallProgress: View {
    @ObservedObject var installer: ModInstaller
    var body: some View {
        ZStack {
            Color.black.opacity(0.3).ignoresSafeArea()
            VStack(spacing: 20) {
                Text(installer.progressText).font(.subheadline).multilineTextAlignment(.center)
                if let fraction = installer.fraction { ProgressView(value: fraction) }
                else { ProgressView() }
                Button("取消", role: .cancel) { installer.cancel() }
            }.padding(24).frame(maxWidth: 320).background(.regularMaterial).cornerRadius(16).padding()
        }
    }
}

private struct ModDocumentPicker: UIViewControllerRepresentable {
    let folder: Bool
    let selected: (URL?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(selected: selected) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types = ["zip", "rar", "7z", "tar", "gz", "xz", "tgz", "txz", "iso"].compactMap { UTType(filenameExtension: $0) }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: folder ? [.folder] : types)
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ controller: UIDocumentPickerViewController, context: Context) {}
    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let selected: (URL?) -> Void
        init(selected: @escaping (URL?) -> Void) { self.selected = selected }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { selected(urls.first) }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { selected(nil) }
    }
}
