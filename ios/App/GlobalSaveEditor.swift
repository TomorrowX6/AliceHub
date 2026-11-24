import Foundation

struct GlobalSaveEditor {
    enum Backup: String, CaseIterable {
        case edit, unlock

        var filename: String { ".global-\(rawValue).plist" }
    }

    enum Failure: LocalizedError {
        case unsafePath, invalidSave, invalidBackup, missingTemplate, write

        var errorDescription: String? {
            switch self {
            case .unsafePath: return "存档路径不安全。"
            case .invalidSave: return "无法读取全局存档。"
            case .invalidBackup: return "无法读取备份。"
            case .missingTemplate: return "无法读取全解锁存档。"
            case .write: return "无法写入全局存档。"
            }
        }
    }

    private struct Entry: Codable, Equatable {
        let path: String
        let data: Data?
    }

    struct Paths {
        let global: String
        let collection: String
    }

    let directory: URL
    private let files = FileManager.default
    private static let global = "GlobalSave.asd"
    private static let collection = "Collection.asd"
    private static let common = "CommonSystemData.sav"
    private static let editable = [global, collection, common]
    private static let unlockable = [global, collection, "AFCGMode.asd", "AFBGMMode.asd"]
    private static let nested = "SaveData/"

    private func backupURL(_ kind: Backup) -> URL {
        directory.appendingPathComponent(kind.filename)
    }

    private func exists(_ url: URL, directory expectedDirectory: Bool = false) throws -> Bool {
        if (try? files.destinationOfSymbolicLink(atPath: url.path)) != nil { throw Failure.unsafePath }
        guard files.fileExists(atPath: url.path) else { return false }
        let values = try url.resourceValues(forKeys: [.isRegularFileKey, .isDirectoryKey])
        guard expectedDirectory ? values.isDirectory == true : values.isRegularFile == true else { throw Failure.unsafePath }
        return true
    }

    private func nestedExists(_ root: URL) throws -> Bool {
        guard try exists(root, directory: true) else { return false }
        return try exists(root.appendingPathComponent("SaveData"), directory: true)
    }

    private func snapshot(_ names: [String], in root: URL) throws -> [Entry] {
        let paths = try nestedExists(root) ? names + names.map { Self.nested + $0 } : names
        return try read(paths, in: root)
    }

    private func read(_ paths: [String], in root: URL) throws -> [Entry] {
        if paths.contains(where: { $0.hasPrefix(Self.nested) }) { _ = try nestedExists(root) }
        return try paths.map { path in
            let url = root.appendingPathComponent(path)
            return Entry(path: path, data: try exists(url) ? Data(contentsOf: url) : nil)
        }
    }

    private func resolve(in root: URL) throws -> Paths? {
        let nested = try nestedExists(root)
        func first(_ name: String) throws -> URL? {
            let direct = root.appendingPathComponent(name)
            if try exists(direct) { return direct }
            let sub = root.appendingPathComponent(Self.nested + name)
            return try nested && exists(sub) ? sub : nil
        }
        let global = try first(Self.global)
        let collection = try first(Self.collection)
        let common = try first(Self.common)
        if global == nil && collection == nil {
            guard let common else { return nil }
            return Paths(global: common.deletingLastPathComponent().appendingPathComponent(Self.global).path, collection: "")
        }
        // Don't let the native resolver read a CommonSystemData file outside the staged layout.
        if let anchor = global ?? collection, anchor.deletingLastPathComponent().path != root.path {
            let deeper = anchor.deletingLastPathComponent().appendingPathComponent("SaveData")
            if try exists(deeper, directory: true), try exists(deeper.appendingPathComponent(Self.common)) {
                throw Failure.unsafePath
            }
        }
        return Paths(global: global?.path ?? "", collection: collection?.path ?? "")
    }

    func paths() throws -> Paths? {
        try resolve(in: directory)
    }

    func hasBackup(_ kind: Backup) throws -> Bool {
        try exists(directory, directory: true) && exists(backupURL(kind))
    }

    func discardBackups() throws {
        for kind in Backup.allCases where try hasBackup(kind) {
            try files.removeItem(at: backupURL(kind))
        }
    }

    private func backupEntries(_ kind: Backup) throws -> [Entry] {
        let entries = try PropertyListDecoder().decode([Entry].self, from: Data(contentsOf: backupURL(kind)))
        let names = kind == .edit ? Self.editable : Self.unlockable
        let validPaths = Set(names + names.map { Self.nested + $0 })
        let paths = Set(entries.map(\.path))
        guard !entries.isEmpty, paths.count == entries.count, paths.isSubset(of: validPaths) else {
            throw Failure.invalidBackup
        }
        return entries
    }

    private func saveBackup(_ kind: Backup, entries: [Entry]) throws {
        let hadBackup = try hasBackup(kind)
        var backup = entries
        if kind == .unlock && hadBackup {
            let previous = try backupEntries(kind)
            let known = Set(previous.map(\.path))
            let added = entries.filter { !known.contains($0.path) }
            if added.isEmpty { return }
            backup = previous + added
        }
        try PropertyListEncoder().encode(backup).write(to: backupURL(kind), options: .atomic)
    }

    private func apply(_ entries: [Entry], to root: URL) throws {
        for entry in entries {
            if entry.path.hasPrefix(Self.nested) { _ = try nestedExists(root) }
            let url = root.appendingPathComponent(entry.path)
            let exists = try exists(url)
            if let data = entry.data {
                try files.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
                try data.write(to: url, options: .atomic)
            } else if exists {
                try files.removeItem(at: url)
            }
        }
    }

    private func replace(_ before: [Entry], with after: [Entry]) throws {
        let oldValues = Dictionary(uniqueKeysWithValues: before.map { ($0.path, $0) })
        var applied: [Entry] = []
        do {
            for entry in after {
                guard let old = oldValues[entry.path], entry != old else { continue }
                try apply([entry], to: directory)
                applied.append(old)
            }
        } catch {
            try apply(applied.reversed(), to: directory)
            throw error
        }
    }

    func edit(_ changes: [String: Double], write: (Paths, String) -> Bool) throws {
        guard try paths() != nil else { throw Failure.invalidSave }
        let before = try snapshot(Self.editable, in: directory)
        let stage = files.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try files.createDirectory(at: stage, withIntermediateDirectories: false)
        defer { try? files.removeItem(at: stage) }
        if try nestedExists(directory) {
            try files.createDirectory(at: stage.appendingPathComponent("SaveData"), withIntermediateDirectories: false)
        }
        try apply(before, to: stage)
        guard let paths = try resolve(in: stage) else { throw Failure.invalidSave }
        let json = String(decoding: try JSONSerialization.data(withJSONObject: changes), as: UTF8.self)
        guard write(paths, json) else { throw Failure.write }
        let after = try snapshot(Self.editable, in: stage)
        let staged = Dictionary(uniqueKeysWithValues: after.map { ($0.path, $0) })
        let changed = before.filter { staged[$0.path] != $0 }
        guard !changed.isEmpty else { return }
        try saveBackup(.edit, entries: changed)
        try replace(before, with: after)
    }

    func unlock(using archive: URL?) throws {
        guard let archive else { throw Failure.missingTemplate }
        let template = try SaveZip.read(archive)
        guard template.count == Self.unlockable.count, Set(template.map(\.name)) == Set(Self.unlockable) else {
            throw Failure.missingTemplate
        }
        let data = Dictionary(uniqueKeysWithValues: template.map { ($0.name, $0.data) })
        let before = try snapshot(Self.unlockable, in: directory)
        let after = before.map { Entry(path: $0.path, data: data[URL(fileURLWithPath: $0.path).lastPathComponent]) }
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        try saveBackup(.unlock, entries: before)
        try replace(before, with: after)
    }

    func restore(_ kind: Backup) throws {
        guard try hasBackup(kind) else { throw Failure.invalidBackup }
        let entries = try backupEntries(kind)
        let before = try read(entries.map(\.path), in: directory)
        try replace(before, with: entries)
        try files.removeItem(at: backupURL(kind))
    }
}
