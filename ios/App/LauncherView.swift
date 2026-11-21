import SwiftUI
import UniformTypeIdentifiers

struct LauncherView: View {
    @StateObject private var library = GameLibrary()
    @State private var pickerPresented = false
    @State private var pendingDelete: Game?
    @State private var managedGame: Game?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if library.games.isEmpty {
                    Spacer()
                    Image(systemName: "gamecontroller").font(.system(size: 56)).foregroundColor(.accentColor)
                    Text("还没有游戏").font(.title2).bold().padding(.top)
                    Text("导入 System 4 游戏文件夹即可开始。\n也可以通过“文件”App，将游戏文件夹复制到\n“我的 iPhone / AliceHub / Games”中。")
                        .font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center).padding()
                    Spacer()
                } else {
                    List {
                        ForEach(library.games) { game in
                            Button { library.launch(game) } label: {
                                Label(ModOverlay.displayName(game.url, baseName: game.name), systemImage: "gamecontroller.fill").padding(.vertical, 8)
                            }
                            .swipeActions {
                                Button(role: .destructive) { pendingDelete = game } label: { Label("删除", systemImage: "trash") }
                            }
                            .swipeActions(edge: .leading, allowsFullSwipe: false) {
                                if let descriptor = ModGameDescriptor.classifyDirectory(game.url) {
                                    Button { managedGame = game } label: {
                                        Label(descriptor.isCnPatch ? "汉化补丁" : "MOD 管理", systemImage: "shippingbox")
                                    }.tint(.blue)
                                }
                            }
                            .contextMenu {
                                if let descriptor = ModGameDescriptor.classifyDirectory(game.url) {
                                    Button(descriptor.isCnPatch ? "汉化补丁" : "MOD 管理") { managedGame = game }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
                Button { pickerPresented = true } label: {
                    Label("导入游戏文件夹", systemImage: "folder.badge.plus")
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent).padding()
            }
            .navigationTitle("AliceHub")
            .disabled(library.importing || library.launching)
            .overlay {
                if library.importing || library.launching {
                    ZStack {
                        Color.black.opacity(0.2).ignoresSafeArea()
                        ProgressView(library.importing ? "正在导入，请稍候…" : "正在启动…")
                            .padding(28).background(.regularMaterial).cornerRadius(16)
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
        .sheet(isPresented: $pickerPresented) { FolderPicker { library.importFolder($0) } }
        .sheet(item: $managedGame) { game in
            if let descriptor = ModGameDescriptor.classifyDirectory(game.url) {
                ModManagerView(game: game, descriptor: descriptor, library: library)
            }
        }
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
    let selected: (URL) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(selected: selected) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder])
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
