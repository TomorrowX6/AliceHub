import SwiftUI
import UniformTypeIdentifiers

struct HubSettingsView: View {
    let games: URL
    @Environment(\.dismiss) private var dismiss
    @HubLocalizedText private var localized
    @AppStorage("hub.appearance") private var appearance = HubAppearance.system.rawValue
    @AppStorage(HubLanguage.storageKey) private var language = HubLanguage.system.rawValue
    @AppStorage(DownloadConcurrency.storageKey) private var downloadConcurrency = DownloadConcurrency.defaultValue
    @AppStorage("hub.longPressCtrl") private var longPressCtrl = false
    @AppStorage(OnlineInstall.storageKey) private var onlineInstall = false
    @AppStorage(UpdateCheck.storageKey) private var checkForUpdates = true
    @State private var onlineDisclaimer = false
    @State private var gamepad = false
    @State private var choosingSyncFolder = false
    @State private var syncFolder = SaveSyncFolder.displayName
    @State private var syncing = false
    @State private var syncMessage: String?
    @State private var clearingCache = false

    private var clampedConcurrency: Binding<Int> {
        Binding(
            get: { DownloadConcurrency.clamped(downloadConcurrency) },
            set: { downloadConcurrency = DownloadConcurrency.clamped($0) }
        )
    }

    var body: some View {
        NavigationView {
            List {
                Section("语言") {
                    Picker("应用语言", selection: $language) {
                        ForEach(HubLanguage.allCases) { Text(LocalizedStringKey($0.title)).tag($0.rawValue) }
                    }
                }.listRowBackground(HubTheme.surface)
                Section("下载") {
                    Toggle("检查更新", isOn: $checkForUpdates)
                    Toggle("在线安装", isOn: Binding(
                        get: { onlineInstall },
                        set: { enabled in if enabled { onlineDisclaimer = true } else { onlineInstall = false } }
                    ))
                    Stepper(value: clampedConcurrency, in: DownloadConcurrency.range) {
                        HStack {
                            Text("并发下载数")
                            Spacer()
                            Text("\(DownloadConcurrency.clamped(downloadConcurrency))")
                                .foregroundColor(.secondary)
                        }
                    }
                }.listRowBackground(HubTheme.surface)
                Section("外观") {
                    Picker("主题模式", selection: $appearance) {
                        ForEach(HubAppearance.allCases) { Text(LocalizedStringKey($0.title)).tag($0.rawValue) }
                    }.pickerStyle(.segmented).padding(.vertical, 8)
                }.listRowBackground(HubTheme.surface)
                Section("操作") {
                    Button { gamepad = true } label: {
                        HubActionLabel(title: "手柄设置", symbol: "gamecontroller")
                    }.buttonStyle(.plain)
                    Toggle(padText("Long Press to Skip (Hold Ctrl)"), isOn: $longPressCtrl)
                }.listRowBackground(HubTheme.surface)
                Section {
                    if let syncFolder {
                        HubActionLabel(title: localized("同步文件夹：%@", syncFolder), symbol: "icloud", navigates: false)
                        Button { syncNow() } label: {
                            HubActionLabel(title: syncing ? "正在同步…" : "立即同步全部存档", symbol: "arrow.triangle.2.circlepath", navigates: false)
                        }.buttonStyle(.plain).disabled(syncing)
                        Button { choosingSyncFolder = true } label: {
                            HubActionLabel(title: "更换同步文件夹", symbol: "folder")
                        }.buttonStyle(.plain).disabled(syncing)
                        Button {
                            syncing = true
                            SaveSyncFolder.enqueue {
                                SaveSyncFolder.clear()
                                DispatchQueue.main.async {
                                    self.syncFolder = nil
                                    syncing = false
                                }
                            }
                        } label: {
                            HubActionLabel(title: "停用存档同步", symbol: "icloud.slash", destructive: true, navigates: false)
                        }.buttonStyle(.plain).disabled(syncing)
                    } else {
                        Button { choosingSyncFolder = true } label: {
                            HubActionLabel(title: "选择同步文件夹", symbol: "icloud")
                        }.buttonStyle(.plain).disabled(syncing)
                    }
                } header: {
                    Text("存档同步")
                } footer: {
                    Text("在“文件”中选择一个文件夹（例如 iCloud 云盘）。启动游戏前双向同步；手动同步、切到后台和退出游戏时只上传。两边都有不同修改时保留两份，覆盖本机存档前会自动备份。删除操作不会同步。")
                }.listRowBackground(HubTheme.surface)
                Section("工具") {
                    NavigationLink { HubCacheView(games: games, busy: $clearingCache) } label: {
                        HubActionLabel(title: "清理缓存", symbol: "trash", navigates: false)
                    }
                    NavigationLink { HubErrorReportView() } label: {
                        HubActionLabel(title: "错误报告", symbol: "exclamationmark.bubble", navigates: false)
                    }
                }.listRowBackground(HubTheme.surface)
                Section("关于") {
                    NavigationLink { HubLicensesView() } label: {
                        HubActionLabel(title: "开源许可证", symbol: "doc.text", navigates: false)
                    }
                    Link(destination: URL(string: "https://github.com/TomorrowX6/AliceHub")!) {
                        HubActionLabel(title: "AliceHub", symbol: "curlybraces")
                    }
                }.listRowBackground(HubTheme.surface)
            }
            .listStyle(.insetGrouped).modifier(HubListStyle())
            .navigationTitle(localized("设置")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() }.disabled(clearingCache) } }
        }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
            .interactiveDismissDisabled(clearingCache)
            .sheet(isPresented: $gamepad) { GamepadEditor(game: nil).modifier(HubAppearanceModifier()) }
            .sheet(isPresented: $choosingSyncFolder) {
                SyncFolderPicker { url in
                    choosingSyncFolder = false
                    guard let url else { return }
                    syncNow(picking: url)
                }
            }
            .alert("免责声明与使用条款", isPresented: $onlineDisclaimer) {
                Button("取消", role: .cancel) {}
                Button("已阅读并同意") { onlineInstall = true }
            } message: { Text(LocalizedStringKey(OnlineInstall.disclaimer)) }
            .alert("存档同步", isPresented: Binding(get: { syncMessage != nil }, set: { if !$0 { syncMessage = nil } })) {
                Button("好", role: .cancel) { syncMessage = nil }
            } message: { Text(syncMessage ?? "") }
    }

    private func syncNow(picking folder: URL? = nil) {
        syncing = true
        SaveSyncFolder.enqueue {
            let message: String
            do {
                if let folder { try SaveSyncFolder.set(folder) }
                let summary = try SaveSyncFolder.syncAll()
                message = summary.map {
                    hubText("同步完成：上传 %lld 个文件。", $0.pushed)
                        + ($0.conflicts > 0 ? "\n" + hubText("%lld 个文件两边都有不同修改，已保持不变。", $0.conflicts) : "")
                        + ($0.skipped > 0 ? "\n" + hubText("%lld 个文件正在修改，已跳过。", $0.skipped) : "")
                } ?? hubText("尚未选择同步文件夹。")
            } catch { message = error.localizedDescription }
            let name = SaveSyncFolder.displayName
            DispatchQueue.main.async {
                syncFolder = name
                syncing = false
                syncMessage = message
            }
        }
    }
}

private struct SyncFolderPicker: UIViewControllerRepresentable {
    let picked: (URL?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(picked: picked) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder])
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ controller: UIDocumentPickerViewController, context: Context) {}
    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let picked: (URL?) -> Void
        init(picked: @escaping (URL?) -> Void) { self.picked = picked }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { picked(urls.first) }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { picked(nil) }
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
                    VStack(spacing: 0) {
                        Button(action: folder) {
                            HubActionLabel(title: "文件夹 / 多碟", symbol: "folder.badge.plus")
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
