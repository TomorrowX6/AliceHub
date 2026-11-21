import SwiftUI

struct SaveEditorView: View {
    let game: Game
    let directory: URL
    @Environment(\.dismiss) private var dismiss

    private var saves: [URL] {
        let keys: [URLResourceKey] = [.isRegularFileKey]
        guard let walker = FileManager.default.enumerator(at: directory, includingPropertiesForKeys: keys) else { return [] }
        return walker.compactMap { $0 as? URL }
            .filter { (try? $0.resourceValues(forKeys: Set(keys)).isRegularFile) == true }
            .filter { !$0.lastPathComponent.hasSuffix(".bak") && !$0.lastPathComponent.hasPrefix(".") }
            .sorted { $0.path < $1.path }
    }

    var body: some View {
        NavigationView {
            List {
                let files = saves
                if files.isEmpty {
                    Text("还没有存档。先进入游戏存档一次。").foregroundColor(.secondary)
                }
                ForEach(files, id: \.self) { url in
                    NavigationLink(String(url.path.dropFirst(directory.path.count + 1))) {
                        SaveFileEditor(url: url)
                    }
                }
            }
            .navigationTitle("修改存档 · \(game.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } } }
        }
        .navigationViewStyle(.stack)
    }
}

struct SaveFileEditor: View {
    let url: URL
    @State private var original: [String: Int] = [:]
    @State private var values: [String: String] = [:]
    @State private var keys: [String] = []
    @State private var error: String?
    @State private var message: String?

    var body: some View {
        Form {
            if let error {
                Text(error).foregroundColor(.red)
            } else {
                Section {
                    ForEach(keys, id: \.self) { key in
                        HStack {
                            Text(key)
                            Spacer()
                            TextField("", text: Binding(get: { values[key] ?? "" }, set: { values[key] = $0 }))
                                .keyboardType(.numbersAndPunctuation).multilineTextAlignment(.trailing).frame(maxWidth: 140)
                        }
                    }
                } footer: { Text("保存前会在同目录生成 .mod.bak 备份。") }
                if let message { Text(message).foregroundColor(.secondary) }
            }
        }
        .navigationTitle(url.lastPathComponent)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("保存", action: save).disabled(error != nil) } }
        .onAppear(perform: load)
    }

    private func load() {
        let summary = ModifierBridge.readLocalSave(url)
        guard summary["valid"] as? Bool == true else {
            error = "无法读取存档：\(summary["error"] as? String ?? "未知错误")"
            return
        }
        let fields = ModifierBridge.editableFields(summary)
        original = Dictionary(uniqueKeysWithValues: fields.map { ($0.key, $0.value) })
        values = original.mapValues(String.init)
        keys = fields.map(\.key)
        error = nil
    }

    private func save() {
        var changes: [String: Int] = [:]
        for key in keys {
            guard let text = values[key], let value = Int(text.trimmingCharacters(in: .whitespaces)) else {
                message = "\(key) 不是有效整数"
                return
            }
            if value != original[key] { changes[key] = value }
        }
        guard !changes.isEmpty else { message = "没有修改"; return }
        do {
            try ModifierBridge.writeLocalSave(url, changes: changes)
            load()
            let rejected = changes.filter { original[$0.key] != $0.value }.map(\.key).sorted()
            message = rejected.isEmpty
                ? "已保存 \(changes.count) 项"
                : "以下字段未能写入：\(rejected.joined(separator: "、"))"
        } catch {
            message = "保存失败"
        }
    }
}
