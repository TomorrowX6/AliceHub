import Foundation

/// Two-way, newest-file-wins mirror between Documents/Saves/<game> and
/// <sync folder>/<game>, where the sync folder is any folder the user picked in
/// Files (typically iCloud Drive). Deletions are not propagated.
final class SaveSync {
    struct Summary: Equatable {
        var pulled = 0
        var pushed = 0
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
    private let beforeLocalChange: () throws -> Void

    init(local: URL, remote: URL, beforeLocalChange: @escaping () throws -> Void = {}) {
        self.local = local
        self.remote = remote
        self.beforeLocalChange = beforeLocalChange
    }

    private struct Item {
        let url: URL
        let modified: Date
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
                    result[prefix + name] = Item(url: url, modified: modified)
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
        var summary = Summary()
        var backedUp = false
        for path in Set(mine.keys).union(theirs.keys).sorted() {
            let parts = path.split(separator: "/")
            guard !parts.contains(".."), !parts.contains(".") else { throw Failure.unsafePath }
            let a = mine[path], b = theirs[path]
            if let b, a == nil || b.modified.timeIntervalSince(a!.modified) > Self.tolerance {
                if a != nil && !backedUp {
                    try beforeLocalChange()
                    backedUp = true
                }
                try coordinatedWrite(try coordinatedRead(b.url), to: local.appendingPathComponent(path), modified: b.modified)
                summary.pulled += 1
            } else if let a, b == nil || a.modified.timeIntervalSince(b!.modified) > Self.tolerance {
                try coordinatedWrite(try Data(contentsOf: a.url), to: remote.appendingPathComponent(path), modified: a.modified)
                summary.pushed += 1
            }
        }
        return summary
    }
}

/// The user's chosen sync folder, kept as a security-scoped bookmark.
enum SaveSyncFolder {
    static var isConfigured: Bool { UserDefaults.standard.data(forKey: SaveSync.bookmarkKey) != nil }

    static func set(_ url: URL) throws {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        UserDefaults.standard.set(try url.bookmarkData(), forKey: SaveSync.bookmarkKey)
    }

    static func clear() { UserDefaults.standard.removeObject(forKey: SaveSync.bookmarkKey) }

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
        try withFolder { folder in
            let manager = SaveManager(game: game, documents: documents, support: support) { _, _ in }
            let sync = SaveSync(local: manager.directory, remote: folder.appendingPathComponent(game)) {
                try manager.backup()
            }
            return try sync.sync()
        }
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
                let manager = SaveManager(game: game, documents: documents, support: support) { _, _ in }
                let result = try SaveSync(local: manager.directory, remote: folder.appendingPathComponent(game)) {
                    try manager.backup()
                }.sync()
                total.pulled += result.pulled
                total.pushed += result.pushed
            }
            return total
        }
    }
}
