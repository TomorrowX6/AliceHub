import Foundation
#if canImport(Darwin)
import Darwin
#endif

struct GlobalSaveEditor {
    enum Backup: String, CaseIterable {
        case edit, unlock

        var filename: String { ".global-\(rawValue).plist" }
        var pendingFilename: String { "\(filename).pending" }
    }

    enum Failure: LocalizedError {
        case unsafePath, invalidSave, invalidBackup, missingTemplate, write

        var errorDescription: String? {
            switch self {
            case .unsafePath: return "存档路径不安全。"
            case .invalidSave: return hubText("无法读取全局存档。")
            case .invalidBackup: return hubText("无法读取备份。")
            case .missingTemplate: return hubText("无法读取全解锁存档。")
            case .write: return hubText("无法写入全局存档。")
            }
        }
    }

    private struct Entry: Codable, Equatable {
        let path: String
        let data: Data?
    }

    private struct PendingBackup: Codable, Equatable {
        let baseline: [Entry]
        let undo: [Entry]
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

    private func pendingBackupURL(_ kind: Backup) -> URL {
        directory.appendingPathComponent(kind.pendingFilename)
    }

    private func validateEntries(_ entries: [Entry], kind: Backup) throws -> [Entry] {
        let names = kind == .edit ? Self.editable : Self.unlockable
        let validPaths = Set(names + names.map { Self.nested + $0 })
        let paths = Set(entries.map(\.path))
        guard !entries.isEmpty, paths.count == entries.count, paths.isSubset(of: validPaths) else {
            throw Failure.invalidBackup
        }
        return entries
    }

    private func decodeBackupEntries(at url: URL, kind: Backup) throws -> [Entry] {
        let data = try Data(contentsOf: url)
        let entries = try PropertyListDecoder().decode([Entry].self, from: data)
        return try validateEntries(entries, kind: kind)
    }

    private func decodePendingBackup(at url: URL, kind: Backup) throws -> PendingBackup {
        let data = try Data(contentsOf: url)
        let decoder = PropertyListDecoder()
        if let pending = try? decoder.decode(PendingBackup.self, from: data) {
            return PendingBackup(
                baseline: try validateEntries(pending.baseline, kind: kind),
                undo: try validateEntries(pending.undo, kind: kind)
            )
        }
        let legacy = try validateEntries(try decoder.decode([Entry].self, from: data), kind: kind)
        return PendingBackup(baseline: legacy, undo: legacy)
    }

    private func writeCommittedBackup(_ entries: [Entry], for kind: Backup) throws {
        let dest = backupURL(kind)
        _ = try exists(dest)
        let data = try PropertyListEncoder().encode(entries)
        try data.write(to: dest, options: .atomic)
    }

    /// Reconcile an interrupted `.pending` transaction record before exposing undo backups.
    /// - Only discard `.pending` if decoding itself reveals corrupt/invalid backup data;
    ///   never discard it on transient file-read errors or unreadable live paths.
    /// - Compare live files against `pending.baseline` (exact pre-transaction live state),
    ///   NOT the merged `pending.undo` payload: if live files still match `baseline`, the
    ///   interrupted transaction never wrote live files, so discard `.pending` and preserve
    ///   any prior committed undo backup intact.
    /// - If live files differ from `baseline`, promote `pending.undo` to the committed backup
    ///   (or return `.pending` directly if promotion fails so restore can still recover it).
    private func activeBackupURL(_ kind: Backup) throws -> URL? {
        guard try exists(directory, directory: true) else { return nil }
        let pending = pendingBackupURL(kind)
        let committed = backupURL(kind)
        if try exists(pending) {
            let pendingRecord: PendingBackup
            do {
                pendingRecord = try decodePendingBackup(at: pending, kind: kind)
            } catch is DecodingError {
                try? files.removeItem(at: pending)
                return (try exists(committed)) ? committed : nil
            } catch Failure.invalidBackup {
                try? files.removeItem(at: pending)
                return (try exists(committed)) ? committed : nil
            }
            let liveEntries = try read(pendingRecord.baseline.map(\.path), in: directory)
            if liveEntries == pendingRecord.baseline {
                try files.removeItem(at: pending)
            } else {
                do {
                    try writeCommittedBackup(pendingRecord.undo, for: kind)
                    try? files.removeItem(at: pending)
                    return committed
                } catch {
                    return pending
                }
            }
        }
        if try exists(committed) { return committed }
        return nil
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
        try activeBackupURL(kind) != nil
    }

    func discardBackups() throws {
        guard try exists(directory, directory: true) else { return }
        for kind in Backup.allCases {
            for url in [pendingBackupURL(kind), backupURL(kind)] where try exists(url) {
                try files.removeItem(at: url)
            }
        }
    }

    private func backupEntries(_ kind: Backup) throws -> [Entry] {
        guard let url = try activeBackupURL(kind) else { throw Failure.invalidBackup }
        if url == pendingBackupURL(kind) {
            return try decodePendingBackup(at: url, kind: kind).undo
        }
        return try decodeBackupEntries(at: url, kind: kind)
    }

    private func stageBackup(_ kind: Backup, baseline: [Entry], undoEntries: [Entry]) throws -> (url: URL, undo: [Entry])? {
        let hadBackup = try hasBackup(kind)
        var undo = undoEntries
        if kind == .unlock && hadBackup {
            let previous = try backupEntries(kind)
            let known = Set(previous.map(\.path))
            let added = undoEntries.filter { !known.contains($0.path) }
            if added.isEmpty { return nil }
            undo = previous + added
        }
        let staged = pendingBackupURL(kind)
        _ = try exists(staged)
        _ = try exists(backupURL(kind))
        let record = PendingBackup(baseline: baseline, undo: undo)
        let data = try PropertyListEncoder().encode(record)
        try data.write(to: staged, options: .atomic)
        return (staged, undo)
    }

    private func commitStagedBackup(_ staged: (url: URL, undo: [Entry])?, for kind: Backup) throws {
        guard let staged else { return }
        try writeCommittedBackup(staged.undo, for: kind)
        try? files.removeItem(at: staged.url)
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

    private func rollback(_ entries: [Entry]) -> Bool {
        var succeeded = true
        for rollbackEntry in entries.reversed() {
            do {
                try apply([rollbackEntry], to: directory)
            } catch {
                succeeded = false
            }
        }
        return succeeded
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
            _ = rollback(applied)
            throw error
        }
    }

    private func replaceAndCommit(_ before: [Entry], with after: [Entry], stagedBackup: (url: URL, undo: [Entry])?, for kind: Backup) throws {
        let oldValues = Dictionary(uniqueKeysWithValues: before.map { ($0.path, $0) })
        var applied: [Entry] = []
        do {
            for entry in after {
                guard let old = oldValues[entry.path], entry != old else { continue }
                try apply([entry], to: directory)
                applied.append(old)
            }
        } catch {
            if rollback(applied), let stagedBackup {
                try? files.removeItem(at: stagedBackup.url)
            }
            throw error
        }
        do {
            try commitStagedBackup(stagedBackup, for: kind)
        } catch {
            if rollback(applied), let stagedBackup {
                try? files.removeItem(at: stagedBackup.url)
            }
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
        let stagedBackup = try stageBackup(.edit, baseline: changed, undoEntries: changed)
        try replaceAndCommit(before, with: after, stagedBackup: stagedBackup, for: .edit)
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
        let stagedBackup = try stageBackup(.unlock, baseline: before, undoEntries: before)
        try replaceAndCommit(before, with: after, stagedBackup: stagedBackup, for: .unlock)
    }

    func restore(_ kind: Backup) throws {
        guard let url = try activeBackupURL(kind) else { throw Failure.invalidBackup }
        let entries = url == pendingBackupURL(kind)
            ? try decodePendingBackup(at: url, kind: kind).undo
            : try decodeBackupEntries(at: url, kind: kind)
        let before = try read(entries.map(\.path), in: directory)
        try replace(before, with: entries)
        if try exists(url) {
            try files.removeItem(at: url)
        }
    }
}
