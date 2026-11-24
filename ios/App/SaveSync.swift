import CryptoKit
import Foundation

/// Two-way mirror between Documents/Saves/<game> and
/// <sync folder>/<game>, where the sync folder is any folder the user picked in
/// Files (typically iCloud Drive). A file counts as changed only when its content
/// differs from the last synced version; when both sides changed, the clearly newer
/// one wins and near-ties are left alone. Deletions are not propagated.
final class SaveSync {
    struct Summary: Equatable {
        var pulled = 0
        var pushed = 0
        var conflicts = 0
    }
    enum Failure: LocalizedError {
        case folderUnavailable, unsafePath
        var errorDescription: String? {
            switch self {
            case .folderUnavailable: return "无法访问同步文件夹，请在设置中重新选择。"
            case .unsafePath: return "同步文件夹中的存档路径不安全。"
            }
        }
    }

    static let bookmarkKey = "hub.saveSync.bookmark"
    /// Files written within this many seconds of each other count as the same version,
    /// which absorbs timestamp rounding on file provider volumes.
    static let tolerance: TimeInterval = 2

    private let files = FileManager.default
    private let coordinator = NSFileCoordinator()
    let local: URL
    let remote: URL
    let state: URL?
    private let beforeLocalChange: () throws -> Void

    init(local: URL, remote: URL, state: URL? = nil, beforeLocalChange: @escaping () throws -> Void = {}) {
        self.local = local
        self.remote = remote
        self.state = state
        self.beforeLocalChange = beforeLocalChange
    }

    private struct Item {
        let url: URL
        let modified: Date
        let downloaded: Bool
    }

    private struct Record: Codable {
        var hash: String
        var remote: Date
    }

    private static func digest(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private func loadRecords() -> [String: Record] {
        guard let state, let data = try? Data(contentsOf: state) else { return [:] }
        return (try? JSONDecoder().decode([String: Record].self, from: data)) ?? [:]
    }

    private func saveRecords(_ records: [String: Record]) throws {
        guard let state else { return }
        try files.createDirectory(at: state.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(records).write(to: state, options: .atomic)
    }


    /// iCloud Drive lists files that are not downloaded yet as ".<name>.icloud".
    private static func logicalName(_ name: String) -> String? {
        if name.hasPrefix("."), name.hasSuffix(".icloud"), name.count > 8 {
            return String(name.dropFirst().dropLast(7))
        }
        return name.hasPrefix(".") ? nil : name
    }

    private func scan(_ root: URL) throws -> [String: Item] {
        guard files.fileExists(atPath: root.path) else { return [:] }
        var result: [String: Item] = [:]
        let keys: [URLResourceKey] = [.isDirectoryKey, .isSymbolicLinkKey, .contentModificationDateKey]
        func visit(_ folder: URL, _ prefix: String) throws {
            for child in try files.contentsOfDirectory(at: folder, includingPropertiesForKeys: keys) {
                guard let name = Self.logicalName(child.lastPathComponent) else { continue }
                let url = folder.appendingPathComponent(name)
                let values = try child.resourceValues(forKeys: Set(keys))
                guard values.isSymbolicLink != true else { throw Failure.unsafePath }
                if values.isDirectory == true {
                    try visit(url, prefix + name + "/")
                } else {
                    let modified = (try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate)
                        ?? values.contentModificationDate ?? .distantPast
                    result[prefix + name] = Item(url: url, modified: modified, downloaded: name == child.lastPathComponent)
                }
            }
        }
        try visit(root, "")
        return result
    }

    private func coordinatedRead(_ url: URL) throws -> Data {
        var error: NSError?
        var result: Result<Data, Error> = .failure(Failure.folderUnavailable)
        coordinator.coordinate(readingItemAt: url, options: [], error: &error) { url in
            result = Result { try Data(contentsOf: url) }
        }
        if let error { throw error }
        return try result.get()
    }

    private func coordinatedWrite(_ data: Data, to url: URL, modified: Date) throws {
        try files.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        var error: NSError?
        var result: Result<Void, Error> = .success(())
        coordinator.coordinate(writingItemAt: url, options: .forReplacing, error: &error) { url in
            result = Result {
                try data.write(to: url, options: .atomic)
                try files.setAttributes([.modificationDate: modified], ofItemAtPath: url.path)
            }
        }
        if let error { throw error }
        try result.get()
    }

    @discardableResult func sync() throws -> Summary {
        let mine = try scan(local)
        let theirs = try scan(remote)
        var records = loadRecords()
        defer { try? saveRecords(records) }
        var summary = Summary()
        var backedUp = false
        for path in Set(mine.keys).union(theirs.keys).sorted() {
            let parts = path.split(separator: "/")
            guard !parts.contains(".."), !parts.contains(".") else { throw Failure.unsafePath }
            let a = mine[path], b = theirs[path], base = records[path]
            var localData: Data?, remoteData: Data?
            func localBytes() throws -> Data {
                if localData == nil { localData = try Data(contentsOf: a!.url) }
                return localData!
            }
            func remoteBytes() throws -> Data {
                if remoteData == nil { remoteData = try coordinatedRead(b!.url) }
                return remoteData!
            }
            let pull: Bool
            if let a, let b {
                // A cloud file that is not downloaded yet and still matches the last sync is
                // treated as unchanged, so a sync does not download every save.
                let remoteHash: String?
                if !b.downloaded, let base, abs(b.modified.timeIntervalSince(base.remote)) <= Self.tolerance {
                    remoteHash = base.hash
                } else {
                    remoteHash = Self.digest(try remoteBytes())
                }
                let localHash = Self.digest(try localBytes())
                if localHash == remoteHash {
                    records[path] = Record(hash: localHash, remote: b.modified)
                    continue
                }
                let localChanged = localHash != base?.hash
                let remoteChanged = remoteHash != base?.hash
                if localChanged && remoteChanged {
                    if abs(b.modified.timeIntervalSince(a.modified)) <= Self.tolerance {
                        summary.conflicts += 1
                        continue
                    }
                    pull = b.modified > a.modified
                } else {
                    pull = remoteChanged
                }
            } else {
                pull = a == nil
            }
            if pull, let b {
                if a != nil && !backedUp {
                    try beforeLocalChange()
                    backedUp = true
                }
                let data = try remoteBytes()
                try coordinatedWrite(data, to: local.appendingPathComponent(path), modified: b.modified)
                records[path] = Record(hash: Self.digest(data), remote: b.modified)
                summary.pulled += 1
            } else if let a {
                let data = try localBytes()
                try coordinatedWrite(data, to: remote.appendingPathComponent(path), modified: a.modified)
                records[path] = Record(hash: Self.digest(data), remote: a.modified)
                summary.pushed += 1
            }
        }
        return summary
    }
}

/// The user's chosen sync folder, kept as a security-scoped bookmark.
enum SaveSyncFolder {
    static var isConfigured: Bool { UserDefaults.standard.data(forKey: SaveSync.bookmarkKey) != nil }

    private static let folderIDKey = "hub.saveSync.folderID"

    static func set(_ url: URL) throws {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        UserDefaults.standard.set(try url.bookmarkData(), forKey: SaveSync.bookmarkKey)
        UserDefaults.standard.set(UUID().uuidString, forKey: folderIDKey)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: SaveSync.bookmarkKey)
        UserDefaults.standard.removeObject(forKey: folderIDKey)
    }

    /// Last synced versions, kept per folder so picking a new folder starts fresh.
    private static func state(game: String, support: URL?) -> URL {
        let id = UserDefaults.standard.string(forKey: folderIDKey) ?? "default"
        return (support ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0])
            .appendingPathComponent("save_sync/\(id)/\(game).json")
    }

    private static func make(game: String, folder: URL, documents: URL?, support: URL?) -> SaveSync {
        let manager = SaveManager(game: game, documents: documents, support: support) { _, _ in }
        return SaveSync(local: manager.directory, remote: folder.appendingPathComponent(game),
                        state: state(game: game, support: support)) { try manager.backup() }
    }

    static var displayName: String? {
        guard let data = UserDefaults.standard.data(forKey: SaveSync.bookmarkKey) else { return nil }
        var stale = false
        return (try? URL(resolvingBookmarkData: data, bookmarkDataIsStale: &stale))?.lastPathComponent
    }

    /// Runs work with the sync folder opened, or returns nil when none is configured.
    static func withFolder<T>(_ work: (URL) throws -> T) throws -> T? {
        guard let data = UserDefaults.standard.data(forKey: SaveSync.bookmarkKey) else { return nil }
        var stale = false
        let url = try URL(resolvingBookmarkData: data, bookmarkDataIsStale: &stale)
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        guard FileManager.default.fileExists(atPath: url.path) else { throw SaveSync.Failure.folderUnavailable }
        if stale { UserDefaults.standard.set(try url.bookmarkData(), forKey: SaveSync.bookmarkKey) }
        return try work(url)
    }

    /// Syncs one game's saves. Local files are backed up through SaveManager before
    /// the first one is overwritten.
    @discardableResult static func sync(game: String, documents: URL? = nil, support: URL? = nil) throws -> SaveSync.Summary? {
        try withFolder { folder in try make(game: game, folder: folder, documents: documents, support: support).sync() }
    }

    /// Syncs every game that has saves on either side.
    static func syncAll(documents: URL? = nil, support: URL? = nil) throws -> SaveSync.Summary? {
        let files = FileManager.default
        let saves = (documents ?? files.urls(for: .documentDirectory, in: .userDomainMask)[0]).appendingPathComponent("Saves")
        return try withFolder { folder in
            func names(_ url: URL) -> [String] {
                ((try? files.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isDirectoryKey])) ?? [])
                    .filter { !$0.lastPathComponent.hasPrefix(".") && (try? $0.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true }
                    .map(\.lastPathComponent)
            }
            var total = SaveSync.Summary()
            for game in Set(names(saves)).union(names(folder)).sorted() {
                let result = try make(game: game, folder: folder, documents: documents, support: support).sync()
                total.pulled += result.pulled
                total.pushed += result.pushed
                total.conflicts += result.conflicts
            }
            return total
        }
    }
}
