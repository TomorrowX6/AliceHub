import SwiftUI
import UniformTypeIdentifiers

struct SaveManagerView: View {
    let game: Game
    @Environment(\.dismiss) private var dismiss
    @State private var entries: [SaveManager.FileEntry] = []
    @State private var backups: [URL] = []
    @State private var picker: SavePickerRequest?
    @State private var busy = false
    @State private var message: String?
    @State private var confirmation: SaveConfirmation?
    @State private var naming: SaveNaming?

    private var manager: SaveManager {
        SaveManager(game: game.name) { source, destination in
            guard savedata_export_pc_file(source.path, destination.path) else { throw SaveManager.Failure.conversion }
        }
    }

    var body: some View {
        NavigationView {
            List {
                Section {
                    HubSectionHeader(title: game.displayName, symbol: "externaldrive")
                }.listRowBackground(HubTheme.surface)
                Section {
                    Button { confirmImport(folder: false) } label: {
                        HubActionLabel(title: "导入文件 / 压缩包", symbol: "square.and.arrow.down")
                    }.buttonStyle(.plain)
                    Button { confirmImport(folder: true) } label: {
                        HubActionLabel(title: "导入文件夹", symbol: "folder")
                    }.buttonStyle(.plain)
                    Menu {
                        Button("导出 PC 存档 · ZIP") { prepareExport(pc: true, zip: true) }
                        Button("导出 PC 存档到文件夹") { prepareExport(pc: true, zip: false) }
                        Button("导出原始存档 · ZIP") { prepareExport(pc: false, zip: true) }
                        Button("导出原始存档到文件夹") { prepareExport(pc: false, zip: false) }
                    } label: {
                        HubActionLabel(title: "导出存档", symbol: "square.and.arrow.up")
                    }.disabled(entries.isEmpty)
                    Button("清空存档", role: .destructive) {
                        confirmation = SaveConfirmation(text: "确定清空全部 \(entries.count) 个存档文件吗？此操作不可恢复。") {
                            operate { try manager.clear(); return "已清空存档" }
                        }
                    }.disabled(entries.isEmpty)
                }.listRowBackground(HubTheme.surface)
                if !backups.isEmpty {
                    Section(header: Text("恢复备份")) {
                        ForEach(backups, id: \.path) { backup in
                            Button("恢复备份（\(backupTime(backup))）") {
                                confirmation = SaveConfirmation(text: "用 \(backupTime(backup)) 的备份替换当前存档吗？当前存档会先自动备份。") {
                                    operate { try manager.restore(backup); return "已恢复备份" }
                                }
                            }
                        }
                    }
                }
                Section(header: Text("\(entries.count) 个文件")) {
                    if entries.isEmpty {
                        HubSectionHeader(title: "暂无存档", symbol: "doc")
                    }
                    ForEach(entries) { entry in
                        VStack(alignment: .leading, spacing: 7) {
                            Label(entry.path, systemImage: "doc.text").font(.subheadline.weight(.medium))
                            Text("\(ByteCountFormatter.string(fromByteCount: Int64(entry.size), countStyle: .file)) · \(entry.modified.formatted())")
                                .font(.caption).foregroundColor(.secondary)
                        }.padding(.vertical, 6)
                        .contextMenu {
                            Button("导出原始存档") { prepareExport(pc: false, zip: false, single: entry) }
                            Button("导出 PC 存档") { prepareExport(pc: true, zip: false, single: entry) }
                            Button("备份存档") { askName(for: entry, copy: true) }
                            Button("重命名") { askName(for: entry, copy: false) }
                            Button("删除存档", role: .destructive) {
                                confirmation = SaveConfirmation(text: "确定删除 \(entry.path)？") {
                                    operate { try manager.delete(entry); return "已删除 \(entry.path)" }
                                }
                            }
                        }
                    }
                }.listRowBackground(HubTheme.surface)
            }
            .listStyle(.insetGrouped).modifier(HubListStyle())
            .navigationTitle("存档管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() }.disabled(busy) } }
            .disabled(busy)
            .overlay { if busy { ProgressView("正在处理，请稍候…").padding().background(.regularMaterial).cornerRadius(12) } }
        }
        .navigationViewStyle(.stack).modifier(HubAppearanceModifier())
        .interactiveDismissDisabled(busy)
        .onAppear { refresh() }
        .sheet(item: $naming) { item in
            SaveNameView(item: item, manager: manager) { name, overwrite in
                naming = nil
                if !item.copy && name == item.entry.url.lastPathComponent { return }
                operate {
                    if item.copy {
                        try manager.duplicate(item.entry, named: name, overwrite: overwrite)
                        return "已备份为 \(name)"
                    }
                    try manager.rename(item.entry, named: name)
                    return "已重命名为 \(name)"
                }
            }
        }
        .sheet(item: $picker) { request in
            SaveDocumentPicker(request: request) { urls in
                picker = nil
                switch request.kind {
                case .importFiles, .importFolder:
                    guard !urls.isEmpty else { return }
                    operate { try manager.importURLs(urls, folder: request.kind == .importFolder); return "存档导入完成" }
                case .folder(let pc):
                    guard let url = urls.first else { return }
                    operate {
                        let access = url.startAccessingSecurityScopedResource()
                        defer { if access { url.stopAccessingSecurityScopedResource() } }
                        return exportMessage(try manager.export(to: url, pc: pc, zip: false), pc: pc)
                    }
                case .exportFile(let url, let result):
                    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
                    if !urls.isEmpty { message = result }
                }
            }
        }
        .alert("存档管理", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("好", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
        .alert(item: $confirmation) { item in
            Alert(title: Text("存档管理"), message: Text(item.text), primaryButton: .default(Text("确定"), action: item.action), secondaryButton: .cancel(Text("取消")))
        }
    }

    private func backupTime(_ url: URL) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.string(from: (try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date())
    }

    private func askName(for entry: SaveManager.FileEntry, copy: Bool) {
        naming = SaveNaming(entry: entry, copy: copy)
    }

    private func refresh() {
        do { entries = try manager.list(); backups = try manager.backups() }
        catch { message = error.localizedDescription }
    }

    private func operate(_ work: @escaping () throws -> String) {
        busy = true
        DispatchQueue.global(qos: .userInitiated).async {
            let result: String
            do { result = try work() } catch { result = "操作失败：\(error.localizedDescription)" }
            DispatchQueue.main.async { busy = false; refresh(); message = result }
        }
    }

    private func confirmImport(folder: Bool) {
        let action = { picker = SavePickerRequest(kind: folder ? .importFolder : .importFiles) }
        if entries.isEmpty { action() }
        else {
            confirmation = SaveConfirmation(text: "同名存档会被覆盖。导入前会先自动备份当前存档，可在“导入存档”里选择“恢复备份”撤销。", action: action)
        }
    }

    private func exportMessage(_ counts: (Int, Int), pc: Bool) -> String {
        pc ? "已导出 \(counts.0) 个 PC 兼容存档文件，其中 \(counts.1) 个已自动转换" : "已导出 \(counts.0) 个存档文件"
    }

    private func prepareExport(pc: Bool, zip: Bool, single: SaveManager.FileEntry? = nil) {
        guard zip || single != nil else { picker = SavePickerRequest(kind: .folder(pc: pc)); return }
        busy = true
        DispatchQueue.global(qos: .userInitiated).async {
            let temporary = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            do {
                try FileManager.default.createDirectory(at: temporary, withIntermediateDirectories: true)
                let url = temporary.appendingPathComponent(single?.url.lastPathComponent ?? manager.exportName(pc: pc))
                let counts = try manager.export(to: url, pc: pc, zip: zip, single: single)
                DispatchQueue.main.async {
                    busy = false
                    picker = SavePickerRequest(kind: .exportFile(url, exportMessage(counts, pc: pc)))
                }
            } catch {
                try? FileManager.default.removeItem(at: temporary)
                DispatchQueue.main.async { busy = false; message = error.localizedDescription }
            }
        }
    }
}

private struct SaveNaming: Identifiable {
    let entry: SaveManager.FileEntry
    let copy: Bool
    var id: String { entry.id }
    var title: String { copy ? "备份存档" : "重命名" }
    var suggested: String { entry.url.lastPathComponent + (copy ? ".bak" : "") }
}

private struct SaveNameView: View {
    let item: SaveNaming
    let manager: SaveManager
    let submit: (String, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var filename: String
    @State private var message: String?
    @State private var existing: String?

    init(item: SaveNaming, manager: SaveManager, submit: @escaping (String, Bool) -> Void) {
        self.item = item
        self.manager = manager
        self.submit = submit
        _filename = State(initialValue: item.suggested)
    }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    TextField("文件名", text: $filename)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .onSubmit(confirm)
                }.listRowBackground(HubTheme.surface)
                if let message { Text(message).foregroundColor(.red) }
            }
            .modifier(HubListStyle())
            .navigationTitle(item.title).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("确定", action: confirm) }
            }
            .alert("覆盖备份？", isPresented: Binding(get: { existing != nil }, set: { if !$0 { existing = nil } }), presenting: existing) { name in
                Button("覆盖", role: .destructive) { submit(name, true) }
                Button("取消", role: .cancel) {}
            } message: { name in Text("文件“\(name)”已存在。") }
        }
        .navigationViewStyle(.stack).modifier(HubAppearanceModifier())
    }

    private func confirm() {
        do {
            let target = try manager.destination(for: item.entry, named: filename)
            let name = target.lastPathComponent
            let same = name == item.entry.url.lastPathComponent
            if item.copy && same { throw SaveManager.Failure.invalidName }
            if !same && FileManager.default.fileExists(atPath: target.path) {
                guard item.copy else { throw SaveManager.Failure.nameExists }
                existing = name
            } else {
                submit(name, false)
            }
        } catch { message = error.localizedDescription }
    }
}

private struct SaveConfirmation: Identifiable {
    let id = UUID()
    let text: String
    let action: () -> Void
}

private struct SavePickerRequest: Identifiable {
    enum Kind: Equatable {
        case importFiles, importFolder, folder(pc: Bool), exportFile(URL, String)
    }
    let id = UUID()
    let kind: Kind
}

private struct SaveDocumentPicker: UIViewControllerRepresentable {
    let request: SavePickerRequest
    let selected: ([URL]) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(selected: selected) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker: UIDocumentPickerViewController
        switch request.kind {
        case .exportFile(let url, _): picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        case .importFiles:
            picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item])
            picker.allowsMultipleSelection = true
        default: picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder])
        }
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ controller: UIDocumentPickerViewController, context: Context) {}
    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let selected: ([URL]) -> Void
        init(selected: @escaping ([URL]) -> Void) { self.selected = selected }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { selected(urls) }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { selected([]) }
    }
}
