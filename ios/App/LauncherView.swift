import SwiftUI
import UniformTypeIdentifiers

private enum GamepadEditorTarget: Identifiable {
    case global, game(String)
    var id: String { gameID.map { "game:" + $0 } ?? "global" }
    var gameID: String? {
        if case let .game(id) = self { return id }
        return nil
    }
}

struct LauncherView: View {
    @StateObject private var library = GameLibrary()
    @State private var pickerPresented = false
    @State private var archivePickerPresented = false
    @State private var pendingDelete: Game?
    @State private var saveGame: Game?
    @State private var gamepadEditor: GamepadEditorTarget?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if library.games.isEmpty {
                    Spacer()
                    Image(systemName: "gamecontroller").font(.system(size: 56)).foregroundColor(.accentColor)
                    Text("还没有游戏").font(.title2).bold().padding(.top)
                    Text("导入 System 4 游戏文件夹、压缩包或光盘镜像即可开始。\n也可以通过“文件”App，将游戏文件夹复制到\n“我的 iPhone / AliceHub / Games”中。")
                        .font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center).padding()
                    Spacer()
                } else {
                    List {
                        ForEach(library.games) { game in
                            Button { library.launch(game) } label: {
                                Label(game.name, systemImage: "gamecontroller.fill").padding(.vertical, 8)
                            }
                            .swipeActions(edge: .leading, allowsFullSwipe: false) {
                                Button { gamepadEditor = .game(game.id) } label: {
                                    Label(padText("Gamepad"), systemImage: "gamecontroller")
                                }
                            }
                            .swipeActions {
                                Button("存档管理") { saveGame = game }.tint(.blue)
                                Button(role: .destructive) { pendingDelete = game } label: { Label("删除", systemImage: "trash") }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
                VStack(spacing: 12) {
                    Button { pickerPresented = true } label: {
                        Label("导入游戏文件夹", systemImage: "folder.badge.plus")
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                    }
                    Button { archivePickerPresented = true } label: {
                        Label("导入压缩包/光盘镜像", systemImage: "archivebox")
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                    }
                }
                .buttonStyle(.borderedProminent).padding()
            }
            .navigationTitle("AliceHub")
            .toolbar {
                Button { gamepadEditor = .global } label: {
                    Label(padText("Gamepad"), systemImage: "gamecontroller")
                }
            }
            .disabled(library.importing || library.launching)
            .overlay {
                if library.importing || library.launching {
                    ZStack {
                        Color.black.opacity(0.2).ignoresSafeArea()
                        ProgressView(library.importing ? library.importProgress : "正在启动…")
                            .lineLimit(4).multilineTextAlignment(.center)
                            .padding(28).background(.regularMaterial).cornerRadius(16)
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
        .sheet(isPresented: $pickerPresented) { FolderPicker { library.importFolder($0) } }
        .sheet(item: $saveGame) { SaveManagerView(game: $0) }
        .sheet(isPresented: $archivePickerPresented) {
            FolderPicker(contentTypes: ArchivePicker.contentTypes) { library.importArchive($0) }
        }
        .onOpenURL { url in
            if url.isFileURL && ArchiveExtractor.isSupportedArchiveName(url.lastPathComponent) {
                library.importArchive(url)
            }
        }
        .sheet(item: $gamepadEditor) { GamepadEditor(game: $0.gameID) }
        .alert("删除游戏？", isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } })) {
            Button("取消", role: .cancel) { pendingDelete = nil }
            Button("删除", role: .destructive) {
                if let game = pendingDelete { library.delete(game) }
                pendingDelete = nil
            }
        } message: { Text("将删除游戏文件夹，存档不会被删除。") }
        .alert("操作失败", isPresented: Binding(get: { library.error != nil }, set: { if !$0 { library.error = nil } })) {
            Button("好", role: .cancel) { library.error = nil }
        } message: { Text(library.error ?? "") }
        .onChange(of: scenePhase) { phase in
            guard phase == .active else { return }
            library.refresh()
#if DEBUG
            let arguments = ProcessInfo.processInfo.arguments
            if let index = arguments.firstIndex(of: "-autolaunch"), index + 1 < arguments.count,
               let game = library.games.first(where: { $0.name == arguments[index + 1] }) {
                library.launch(game)
            }
#endif
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
