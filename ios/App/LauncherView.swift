import SwiftUI
import UniformTypeIdentifiers

struct LauncherView: View {
    @StateObject private var library = GameLibrary()
    @StateObject private var onlineDownload = OnlineGameDownloadModel()
    @State private var importPresented = false
    @State private var folderPresented = false
    @State private var archivePresented = false
    @State private var settingsPresented = false
    @State private var reportPresented = false
    @State private var reportMessage: String?
    @State private var query = ""
    @AppStorage("gameSortMode") private var sortMode: LibrarySortMode = .catalog
    @Environment(\.scenePhase) private var scenePhase
    @HubLocalizedText private var localized

    private var filteredGames: [LibraryEntry] {
        let search = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let language = localized.language
        return LibraryEntry.build(games: library.games, sort: sortMode, locale: language.locale, language: language)
            .filter { $0.matches(search, language: language) }
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    searchField
                    HStack {
                        Text("游戏库").font(.title3.bold())
                        Spacer()
                        sortMenu
                    }
                    if filteredGames.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "magnifyingglass").font(.largeTitle).foregroundColor(HubTheme.secondary)
                            Text("没有找到相关游戏").font(.headline)
                            Button("清除搜索") { query = "" }
                        }.frame(maxWidth: .infinity).padding(32).modifier(HubCard())
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 14)], spacing: 14) {
                            ForEach(filteredGames) { entry in
                                NavigationLink {
                                    LibraryGameDestination(entry: entry, library: library, download: onlineDownload)
                                } label: { gameCard(entry) }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(20).frame(maxWidth: 1000).frame(maxWidth: .infinity)
            }
            .background(HubTheme.background.ignoresSafeArea())
            .navigationTitle("AliceHub").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { settingsPresented = true } label: {
                        Image(systemName: "gearshape").frame(width: 32, height: 32)
                    }.accessibilityLabel("设置")
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button { importPresented = true } label: { Label("导入游戏", systemImage: "plus") }
                    .buttonStyle(HubPrimaryButton()).disabled(onlineDownload.running)
                    .padding(.horizontal, 20).padding(.vertical, 12)
                    .frame(maxWidth: 620).frame(maxWidth: .infinity)
                    .background(HubTheme.background.opacity(0.96))
            }
            .disabled(library.importing || library.launching)
            .overlay { if library.importing || library.launching { progressOverlay } }
        }
        .navigationViewStyle(.stack)
        .modifier(HubAppearanceModifier())
        .sheet(isPresented: $importPresented) {
            ImportGameView {
                importPresented = false
                DispatchQueue.main.async { folderPresented = true }
            } archive: {
                importPresented = false
                DispatchQueue.main.async { archivePresented = true }
            }
        }
        .sheet(isPresented: $settingsPresented) { HubSettingsView(games: library.gamesDirectory) }
        .sheet(isPresented: $reportPresented, onDismiss: { reportMessage = nil }) {
            NavigationView {
                HubErrorReportView(message: reportMessage)
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { reportPresented = false } } }
            }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
        }
        .sheet(isPresented: $folderPresented) { FolderPicker { library.importFolder($0) } }
        .sheet(isPresented: $archivePresented) {
            FolderPicker(contentTypes: ArchivePicker.contentTypes) { library.importArchive($0) }
        }
        .onOpenURL { url in
            if url.isFileURL && ArchiveExtractor.isSupportedArchiveName(url.lastPathComponent) { library.importArchive(url) }
        }
        .alert("操作失败", isPresented: Binding(get: { library.error != nil }, set: { if !$0 { library.error = nil } })) {
            Button("错误报告") {
                reportMessage = library.error
                library.error = nil
                reportPresented = true
            }
            Button("好", role: .cancel) { library.error = nil }
        } message: { Text(library.error ?? "") }
        .onChange(of: scenePhase) { phase in
            guard phase == .active else { return }
            if OnlineInstall.enabled { onlineDownload.resumePaused(library: library) }
            library.refreshAsync {
#if DEBUG
                let arguments = ProcessInfo.processInfo.arguments
                if let index = arguments.firstIndex(of: "-autolaunch"), index + 1 < arguments.count,
                   let game = library.games.first(where: { $0.name == arguments[index + 1] }) { library.launch(game) }
#endif
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundColor(HubTheme.secondary)
            TextField("搜索游戏", text: $query)
                .submitLabel(.search)
                .disableAutocorrection(true)
                .textInputAutocapitalization(.never)
            if !query.isEmpty {
                Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(HubTheme.secondary) }
                    .accessibilityLabel("清除搜索").frame(minWidth: 32, minHeight: 32)
            }
        }.padding(.horizontal, 16).frame(minHeight: 52)
            .background(HubTheme.surface).clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var sortMenu: some View {
        Menu {
            Picker("排序", selection: $sortMode) {
                ForEach(LibrarySortMode.allCases, id: \.self) { mode in
                    Text(localized(mode.title)).tag(mode)
                }
            }
        } label: {
            Label(localized(sortMode.shortTitle), systemImage: "arrow.up.arrow.down")
                .font(.subheadline.bold()).foregroundColor(HubTheme.primary)
                .padding(.horizontal, 12).frame(minHeight: 44)
                .background(HubTheme.surface).clipShape(Capsule())
        }
        .accessibilityLabel("排序")
        .accessibilityValue(localized(sortMode.shortTitle))
    }

    private func gameCard(_ entry: LibraryEntry) -> some View {
        let installing = onlineDownload.running && onlineDownload.activeID == entry.catalog?.id
        let hasChinese = entry.hasChinese
        return HStack(spacing: 16) {
            GameArtwork(game: entry.primary, catalogID: entry.catalog?.artwork)
            VStack(alignment: .leading, spacing: 10) {
                Text(entry.displayTitle(in: localized.language))
                    .font(.headline).foregroundColor(HubTheme.text).lineLimit(2)
                HStack(spacing: 6) {
                    HubBadge(
                        title: installing ? "下载中" : (entry.primary == nil ? "未安装" : "已安装"),
                        success: !installing && entry.primary != nil
                    )
                    HubBadge(title: OnlineGame.chineseBadgeKey(hasChinese: hasChinese), tone: hasChinese ? .primary : .warning)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundColor(HubTheme.secondary)
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(HubTheme.surface).clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .accessibilityElement(children: .combine)
    }

    private var progressOverlay: some View {
        ZStack {
            Color.black.opacity(0.25).ignoresSafeArea()
            VStack(spacing: 20) {
                ProgressView().scaleEffect(1.2)
                Text(localized(library.importing ? "正在导入游戏" : "正在启动游戏")).font(.headline)
                if library.importing {
                    Text(library.importProgress).font(.caption).foregroundColor(HubTheme.secondary)
                        .multilineTextAlignment(.center).lineLimit(4)
                }
            }.padding(28).frame(maxWidth: 320).background(HubTheme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 24)).padding()
        }.accessibilityAddTraits(.isModal)
    }
}

private struct LibraryGameDestination: View {
    let entry: LibraryEntry
    @ObservedObject var library: GameLibrary
    @ObservedObject var download: OnlineGameDownloadModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedID: String?

    var body: some View {
        let installations = entry.matchingInstallations(in: library.games)
        if let active = LibraryEntry.installation(in: installations, selectedID: selectedID) {
            GameDetailView(game: active, library: library, download: download, catalog: entry.catalog,
                           installations: installations, selectedID: $selectedID)
                .id(active.id)
        } else if let catalog = entry.catalog {
            OnlineGameDetailView(game: catalog, library: library, download: download)
        } else {
            Color.clear.onAppear { dismiss() }
        }
    }
}

struct FolderPicker: UIViewControllerRepresentable {
    var contentTypes: [UTType] = [.folder]
    let selected: (URL) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(selected: selected) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: contentTypes, asCopy: false)
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }
    func updateUIViewController(_ controller: UIDocumentPickerViewController, context: Context) {}
    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let selected: (URL) -> Void
        init(selected: @escaping (URL) -> Void) { self.selected = selected }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            if let url = urls.first { selected(url) }
        }
    }
}

private enum ArchivePicker {
    static let contentTypes: [UTType] = [.zip, .archive, .gzip] +
        ["7z", "rar", "tar", "tgz", "txz", "xz", "iso", "mdf"].map {
            UTType(filenameExtension: $0) ?? .data
        }
}
