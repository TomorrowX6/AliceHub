import SwiftUI
import UniformTypeIdentifiers

struct LauncherView: View {
    @StateObject private var library = GameLibrary()
    @State private var importPresented = false
    @State private var folderPresented = false
    @State private var archivePresented = false
    @State private var settingsPresented = false
    @State private var query = ""
    @Environment(\.scenePhase) private var scenePhase

    private var filteredGames: [Game] {
        let search = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return search.isEmpty ? library.games : library.games.filter {
            ModOverlay.displayName($0.url, baseName: $0.name).localizedCaseInsensitiveContains(search)
        }
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    introduction
                    if !library.games.isEmpty { searchField }
                    HStack {
                        Text("我的游戏").font(.title3.bold())
                        Spacer()
                        Text("\(library.games.count) 款游戏").font(.subheadline).foregroundColor(HubTheme.secondary)
                    }
                    if library.games.isEmpty { emptyLibrary }
                    else if filteredGames.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "magnifyingglass").font(.largeTitle).foregroundColor(HubTheme.secondary)
                            Text("没有找到相关游戏").font(.headline)
                            Button("清除搜索") { query = "" }
                        }.frame(maxWidth: .infinity).padding(32).modifier(HubCard())
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 14)], spacing: 14) {
                            ForEach(filteredGames) { game in
                                NavigationLink {
                                    GameDetailView(game: game, library: library)
                                } label: { gameCard(game) }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    Label("游戏保存在本机，随时继续你的冒险", systemImage: "internaldrive")
                        .font(.caption).foregroundColor(HubTheme.secondary)
                        .frame(maxWidth: .infinity).padding(.top, 4)
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
                Button { importPresented = true } label: {
                    Label("导入游戏", systemImage: "plus")
                }.buttonStyle(HubPrimaryButton())
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
        .sheet(isPresented: $settingsPresented) { HubSettingsView() }
        .sheet(isPresented: $folderPresented) { FolderPicker { library.importFolder($0) } }
        .sheet(isPresented: $archivePresented) {
            FolderPicker(contentTypes: ArchivePicker.contentTypes) { library.importArchive($0) }
        }
        .onOpenURL { url in
            if url.isFileURL && ArchiveExtractor.isSupportedArchiveName(url.lastPathComponent) { library.importArchive(url) }
        }
        .alert("操作失败", isPresented: Binding(get: { library.error != nil }, set: { if !$0 { library.error = nil } })) {
            Button("好", role: .cancel) { library.error = nil }
        } message: { Text(library.error ?? "") }
        .onChange(of: scenePhase) { phase in
            guard phase == .active else { return }
            library.refresh()
#if DEBUG
            let arguments = ProcessInfo.processInfo.arguments
            if let index = arguments.firstIndex(of: "-autolaunch"), index + 1 < arguments.count,
               let game = library.games.first(where: { $0.name == arguments[index + 1] }) { library.launch(game) }
#endif
        }
    }

    private var introduction: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("你的游戏，随时启程。").font(.system(.largeTitle, design: .rounded).bold())
                .foregroundColor(HubTheme.text).fixedSize(horizontal: false, vertical: true)
            Text("System 3.x / System 4 · 原生运行")
                .font(.subheadline).foregroundColor(HubTheme.secondary)
        }.padding(.top, 12)
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundColor(HubTheme.secondary)
            TextField("搜索游戏", text: $query).submitLabel(.search)
            if !query.isEmpty {
                Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(HubTheme.secondary) }
                    .accessibilityLabel("清除搜索").frame(minWidth: 32, minHeight: 32)
            }
        }.padding(.horizontal, 16).frame(minHeight: 52)
            .background(HubTheme.surface).clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func gameCard(_ game: Game) -> some View {
        HStack(spacing: 16) {
            GameArtwork(game: game)
            VStack(alignment: .leading, spacing: 10) {
                Text(ModOverlay.displayName(game.url, baseName: game.name))
                    .font(.headline).foregroundColor(HubTheme.text).lineLimit(2)
                HStack(spacing: 6) {
                    HubBadge(title: "已安装", success: true)
                    HubBadge(title: game.engine.rawValue)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundColor(HubTheme.secondary)
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(HubTheme.surface).clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .accessibilityElement(children: .combine)
    }

    private var emptyLibrary: some View {
        VStack(spacing: 18) {
            Image(systemName: "gamecontroller").font(.system(size: 44, weight: .light))
                .foregroundColor(HubTheme.primary).frame(width: 104, height: 104)
                .background(HubTheme.variant).clipShape(RoundedRectangle(cornerRadius: 30))
            Text("从第一款游戏开始").font(.title2.bold())
            Text("导入你拥有的游戏文件，\n在 iPhone 和 iPad 上继续精彩。")
                .font(.subheadline).foregroundColor(HubTheme.secondary).multilineTextAlignment(.center)
            HStack(spacing: 8) {
                HubBadge(title: "文件夹")
                HubBadge(title: "压缩包")
                HubBadge(title: "光盘镜像")
            }
        }.frame(maxWidth: .infinity).padding(.vertical, 32).modifier(HubCard())
    }

    private var progressOverlay: some View {
        ZStack {
            Color.black.opacity(0.25).ignoresSafeArea()
            VStack(spacing: 20) {
                ProgressView().scaleEffect(1.2)
                Text(library.importing ? "正在导入游戏" : "正在启动游戏").font(.headline)
                if library.importing {
                    Text(library.importProgress).font(.caption).foregroundColor(HubTheme.secondary)
                        .multilineTextAlignment(.center).lineLimit(4)
                }
            }.padding(28).frame(maxWidth: 320).background(HubTheme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 24)).padding()
        }.accessibilityAddTraits(.isModal)
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
