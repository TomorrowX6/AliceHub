import Foundation

final class SaveManager {
    struct FileEntry: Identifiable {
        let url: URL
        let path: String
        let size: Int
        let modified: Date
        var id: String { path }
    }
    enum Failure: LocalizedError {
        case unsafePath, conversion, swap
        var errorDescription: String? {
            switch self {
            case .unsafePath: return "存档路径不安全。"
            case .conversion: return "转换 PC 存档失败。"
            case .swap: return "无法替换存档目录。"
            }
        }
    }
    let game: String
    let directory: URL
    let backupDirectory: URL
    private let files = FileManager.default
    private let converter: (URL, URL) throws -> Void
    private let move: (URL, URL) throws -> Void
    static let magic = Data([0x58, 0x34, 0x53, 0x53, 1, 0, 0, 0])

    init(game: String, documents: URL? = nil, support: URL? = nil,
         move: @escaping (URL, URL) throws -> Void = { try FileManager.default.moveItem(at: $0, to: $1) },
         converter: @escaping (URL, URL) throws -> Void) {
        self.game = game
        directory = (documents ?? files.urls(for: .documentDirectory, in: .userDomainMask)[0]).appendingPathComponent("Saves").appendingPathComponent(game)
        backupDirectory = (support ?? files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]).appendingPathComponent("save_backups").appendingPathComponent(game)
        self.converter = converter
        self.move = move
    }

    private func children(_ url: URL) throws -> [URL] {
        try files.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey])
    }

    func list(_ root: URL? = nil) throws -> [FileEntry] {
        let root = root ?? directory
        guard files.fileExists(atPath: root.path) else { return [] }
        var result: [FileEntry] = []
        func visit(_ folder: URL, _ prefix: String) throws {
            for url in try children(folder) where !url.lastPathComponent.hasPrefix(".") {
                let values = try url.resourceValues(forKeys: [.isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey, .fileSizeKey, .contentModificationDateKey])
                guard values.isSymbolicLink != true else { throw Failure.unsafePath }
                let path = prefix + url.lastPathComponent
                if values.isDirectory == true { try visit(url, path + "/") }
                else if values.isRegularFile == true {
                    result.append(FileEntry(url: url, path: path, size: values.fileSize ?? 0, modified: values.contentModificationDate ?? Date()))
                }
            }
        }
        try visit(root, "")
        return result.sorted { $0.path.localizedStandardCompare($1.path) == .orderedAscending }
    }

    private func stamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd_HHmmss_SSS"
        return formatter.string(from: Date())
    }

    func backups() throws -> [URL] {
        guard files.fileExists(atPath: backupDirectory.path) else { return [] }
        return try children(backupDirectory).filter { $0.pathExtension == "zip" && !$0.lastPathComponent.hasPrefix(".") }
            .sorted { $0.lastPathComponent > $1.lastPathComponent }
    }

    @discardableResult func backup() throws -> URL? {
        let entries = try list()
        guard !entries.isEmpty else { return nil }
        try files.createDirectory(at: backupDirectory, withIntermediateDirectories: true)
        var destination = backupDirectory.appendingPathComponent("SaveData_\(stamp()).zip")
        while files.fileExists(atPath: destination.path) {
            destination = destination.deletingLastPathComponent().appendingPathComponent("SaveData_\(stamp()).zip")
        }
        let temporary = backupDirectory.appendingPathComponent(".\(UUID().uuidString).tmp")
        defer { try? files.removeItem(at: temporary) }
        try SaveZip.write(try entries.map { SaveZip.Entry(name: "SaveData/" + $0.path, data: try Data(contentsOf: $0.url), modified: $0.modified) }, to: temporary)
        try move(temporary, destination)
        for old in try backups().dropFirst(5) { try files.removeItem(at: old) }
        return destination
    }

    private func safePath(_ name: String) throws -> String? {
        let path = name.replacingOccurrences(of: "\\", with: "/")
        guard !path.hasPrefix("/"), !path.contains("\0") else { throw Failure.unsafePath }
        let parts = path.split(separator: "/", omittingEmptySubsequences: true)
        guard !parts.contains(".."), !parts.contains("."), !(parts.first?.contains(":") ?? false) else { throw Failure.unsafePath }
        guard !parts.isEmpty, !parts.contains(where: { $0.hasPrefix(".") }) else { return nil }
        return parts.joined(separator: "/")
    }

    private func install(_ entry: SaveZip.Entry, path: String, root: URL) throws {
        let target = root.appendingPathComponent(path)
        let resolved = target.resolvingSymlinksInPath().standardizedFileURL.path
        guard resolved.hasPrefix(root.resolvingSymlinksInPath().standardizedFileURL.path + "/") else { throw Failure.unsafePath }
        try files.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        try entry.data.write(to: target, options: .atomic)
        try files.setAttributes([.modificationDate: entry.modified], ofItemAtPath: target.path)
    }

    func restore(_ archive: URL) throws {
        let entries = try SaveZip.read(archive)
        let parent = directory.deletingLastPathComponent()
        try files.createDirectory(at: parent, withIntermediateDirectories: true)
        let staging = parent.appendingPathComponent(".restore_staging_\(UUID().uuidString)")
        let old = parent.appendingPathComponent(".restore_old_\(UUID().uuidString)")
        try files.createDirectory(at: staging, withIntermediateDirectories: false)
        defer { try? files.removeItem(at: staging) }
        for entry in entries {
            var name = entry.name.replacingOccurrences(of: "\\", with: "/")
            if name.hasPrefix("SaveData/") { name.removeFirst(9) }
            guard let path = try safePath(name), !name.hasSuffix("/") else { continue }
            try install(entry, path: path, root: staging)
        }
        try backup()
        let existed = files.fileExists(atPath: directory.path)
        if existed {
            func preserveHidden(_ source: URL, _ destination: URL) throws {
                for child in try children(source) {
                    let values = try child.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
                    guard values.isSymbolicLink != true else { throw Failure.unsafePath }
                    let target = destination.appendingPathComponent(child.lastPathComponent)
                    if child.lastPathComponent.hasPrefix(".") {
                        try files.createDirectory(at: destination, withIntermediateDirectories: true)
                        try files.copyItem(at: child, to: target)
                    } else if values.isDirectory == true {
                        try preserveHidden(child, target)
                    }
                }
            }
            try preserveHidden(directory, staging)
            try move(directory, old)
        }
        do { try move(staging, directory) }
        catch {
            if existed {
                do { try move(old, directory) }
                catch { throw NSError(domain: "SaveManager", code: 1, userInfo: [NSLocalizedDescriptionKey: "恢复失败；原存档保留在 \(old.path)", NSUnderlyingErrorKey: error]) }
            }
            throw error
        }
        try? files.removeItem(at: old)
    }

    private func normalized(_ entries: [SaveZip.Entry]) throws -> [SaveZip.Entry] {
        let cleaned = try entries.compactMap { entry -> SaveZip.Entry? in
            guard let path = try safePath(entry.name) else { return nil }
            return SaveZip.Entry(name: path + (entry.name.hasSuffix("/") || entry.name.hasSuffix("\\") ? "/" : ""), data: entry.data, modified: entry.modified)
        }
        let paths = cleaned.filter { !$0.name.hasSuffix("/") }.map { $0.name.split(separator: "/").map(String.init) }
        var prefix: [String] = []
        if let save = paths.compactMap({ parts -> [String]? in
            guard let i = parts.prefix(3).firstIndex(where: { $0.caseInsensitiveCompare("SaveData") == .orderedSame }), i < parts.count - 1 else { return nil }
            return Array(parts.prefix(i + 1))
        }).sorted(by: { $0.count < $1.count }).first { prefix = save }
        else {
            while let first = paths.first, prefix.count < first.count - 1 {
                let next = Array(first.prefix(prefix.count + 1))
                guard paths.allSatisfy({ $0.count > next.count && Array($0.prefix(next.count)) == next }) else { break }
                prefix = next
            }
        }
        return cleaned.compactMap { entry in
            let parts = entry.name.split(separator: "/").map(String.init)
            guard !entry.name.hasSuffix("/"), parts.count > prefix.count, Array(parts.prefix(prefix.count)) == prefix else { return nil }
            return SaveZip.Entry(name: parts.dropFirst(prefix.count).joined(separator: "/"), data: entry.data, modified: entry.modified)
        }
    }

    func importURLs(_ urls: [URL], folder: Bool = false) throws {
        try backup()
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        for url in urls {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            let entries: [SaveZip.Entry]
            if folder {
                let direct = try children(url)
                let hasSaves = direct.contains { ["asd", "sav", "rsm"].contains($0.pathExtension.lowercased()) && (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
                let root = !hasSaves ? direct.first(where: { $0.lastPathComponent.caseInsensitiveCompare("SaveData") == .orderedSame && (try? $0.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true }) ?? url : url
                entries = try list(root).map { SaveZip.Entry(name: $0.path, data: try Data(contentsOf: $0.url), modified: $0.modified) }
            } else if url.pathExtension.lowercased() == "zip" { entries = try normalized(SaveZip.read(url)) }
            else { entries = [SaveZip.Entry(name: url.lastPathComponent, data: try Data(contentsOf: url))] }
            for entry in entries {
                guard let path = try safePath(entry.name) else { continue }
                try install(entry, path: path, root: directory)
            }
        }
    }

    func export(to destination: URL, pc: Bool, zip: Bool, single: FileEntry? = nil) throws -> (Int, Int) {
        let entries = try single.map { [$0] } ?? list().filter { !pc || !["bak", "log"].contains($0.url.pathExtension.lowercased()) }
        var converted = 0
        let scratch = files.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try files.createDirectory(at: scratch, withIntermediateDirectories: true)
        defer { try? files.removeItem(at: scratch) }
        var output: [SaveZip.Entry] = []
        for entry in entries {
            var data = try Data(contentsOf: entry.url)
            if pc {
                let temporary = scratch.appendingPathComponent(UUID().uuidString)
                try converter(entry.url, temporary)
                if data.prefix(8) == Self.magic { converted += 1 }
                data = try Data(contentsOf: temporary)
            }
            output.append(SaveZip.Entry(name: single == nil ? entry.path : entry.url.lastPathComponent, data: data, modified: entry.modified))
        }
        if zip { try SaveZip.write(output.map { SaveZip.Entry(name: "SaveData/" + $0.name, data: $0.data, modified: $0.modified) }, to: destination) }
        else if single != nil, let entry = output.first { try entry.data.write(to: destination, options: .atomic) }
        else {
            let root = destination.appendingPathComponent("SaveData")
            try files.createDirectory(at: root, withIntermediateDirectories: true)
            for entry in output { try install(entry, path: entry.name, root: root) }
        }
        return (output.count, converted)
    }

    func exportName(pc: Bool) -> String { "\(game)_\(pc ? "PC_" : "")SaveData_\(stamp()).zip" }
    func duplicate(_ entry: FileEntry) throws {
        try Data(contentsOf: entry.url).write(to: entry.url.appendingPathExtension("bak"), options: .atomic)
    }
    func delete(_ entry: FileEntry) throws { try files.removeItem(at: entry.url) }
    func clear() throws {
        try backup()
        for entry in try list() { try delete(entry) }
    }
}
