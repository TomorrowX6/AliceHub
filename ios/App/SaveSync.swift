import CryptoKit
import Foundation

/// Two-way mirror between Documents/Saves/<game> and
/// <sync folder>/<game>, where the sync folder is any folder the user picked in
/// Files (typically iCloud Drive). A file counts as changed only when its content
/// differs from the last synced version. Conflicting copies are left in place.
/// Ordinary deletions are not propagated.
final class SaveSync {
    struct Summary: Equatable {
        var pulled = 0
        var pushed = 0
        var conflicts = 0
        var skipped = 0
    }
    enum Failure: LocalizedError {
        case folderUnavailable, unsafePath, unreadableCloudFile, targetChanged, renameConflict
        var errorDescription: String? {
            switch self {
            case .folderUnavailable: return hubText("无法访问同步文件夹，请在设置中重新选择。")
            case .unsafePath: return hubText("同步文件夹中的存档路径不安全。")
            case .unreadableCloudFile: return hubText("云端存档暂不可读，请在“文件”中下载后重试。")
            case .targetChanged: return hubText("同步时存档已被修改，未覆盖该文件。请重试。")
            case .renameConflict: return hubText("同步文件夹中的存档不同或已有同名文件，未重命名。")
            }
        }
    }

    static let bookmarkKey = "hub.saveSync.bookmark"
    enum Mode: Equatable { case twoWay, uploadOnly }

    private let files = FileManager.default
    private let coordinator = NSFileCoordinator()
    let local: URL
    let remote: URL
    let state: URL?
    private let documents: URL
    private let folder: URL
    private let game: String
    private let beforeLocalChange: () throws -> Void

    init(game: String, documents: URL, folder: URL,
         state: URL? = nil, beforeLocalChange: @escaping () throws -> Void = {}) {
        self.game = game
        self.documents = documents
        self.folder = folder
        local = documents.appendingPathComponent("Saves").appendingPathComponent(game)
        remote = folder.appendingPathComponent(game)
        self.state = state
        self.beforeLocalChange = beforeLocalChange
    }

    private struct Snapshot {
        let data: Data
        let modified: Date
    }

    private struct FileStamp: Equatable {
        let size: Int
        let modified: Date
    }

    private enum WriteResult: Equatable { case written, sourceChanged, cancelled }

    private struct Record: Codable {
        var hash: String
        var conflict: [String]?
    }

    private struct Rename: Codable {
        let source: String
        let destination: String
        let hash: String
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

    private func canonical(_ url: URL) -> URL {
        var ancestor = url
        var suffix: [String] = []
        while !files.fileExists(atPath: ancestor.path), ancestor.path != "/" {
            suffix.append(ancestor.lastPathComponent)
            ancestor.deleteLastPathComponent()
        }
        return suffix.reversed().reduce(ancestor.resolvingSymlinksInPath().standardizedFileURL) {
            $0.appendingPathComponent($1)
        }
    }

    private func checkedURL(_ parts: [String], under root: URL) throws -> URL {
        var url = root
        for part in parts {
            guard !part.isEmpty, part != ".", part != "..", !part.contains("/") else { throw Failure.unsafePath }
            url.appendPathComponent(part)
            guard (try? files.destinationOfSymbolicLink(atPath: url.path)) == nil else { throw Failure.unsafePath }
        }
        let parent = canonical(root).pathComponents
        guard canonical(url).pathComponents.starts(with: parent) else { throw Failure.unsafePath }
        return url
    }

    private func scan(_ root: URL) throws -> Set<String> {
        guard files.fileExists(atPath: root.path) else { return [] }
        var result: Set<String> = []
        let keys: [URLResourceKey] = [.isDirectoryKey, .isSymbolicLinkKey]
        func visit(_ folder: URL, _ prefix: String) throws {
            for child in try files.contentsOfDirectory(at: folder, includingPropertiesForKeys: keys) {
                guard let name = Self.logicalName(child.lastPathComponent) else { continue }
                let url = try checkedURL([name], under: folder)
                let values = try child.resourceValues(forKeys: Set(keys))
                guard values.isSymbolicLink != true else { throw Failure.unsafePath }
                if values.isDirectory == true && name == child.lastPathComponent {
                    try visit(url, prefix + name + "/")
                } else {
                    result.insert(prefix + name)
                }
            }
        }
        try visit(root, "")
        return result
    }

    private func snapshot(_ url: URL) throws -> Snapshot {
        let data = try Data(contentsOf: url)
        let modified = try url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate ?? .distantPast
        return Snapshot(data: data, modified: modified)
    }

    private func stamp(_ url: URL) -> FileStamp? {
        guard let attributes = try? files.attributesOfItem(atPath: url.path),
              let size = attributes[.size] as? NSNumber,
              let modified = attributes[.modificationDate] as? Date else { return nil }
        return FileStamp(size: size.intValue, modified: modified)
    }

    private func stableLocalSnapshot(_ url: URL) -> Snapshot? {
        guard let before = stamp(url), let data = try? Data(contentsOf: url),
              data.count == before.size, stamp(url) == before else { return nil }
        return Snapshot(data: data, modified: before.modified)
    }

    private func coordinatedRead(path: [String], under root: URL) throws -> Snapshot {
        let url = try checkedURL(path, under: root)
        var error: NSError?
        var result: Result<Snapshot, Error> = .failure(Failure.folderUnavailable)
        coordinator.coordinate(readingItemAt: url, options: [], error: &error) { coordinatedURL in
            result = Result {
                let target = try checkedURL(path, under: root)
                guard canonical(coordinatedURL).pathComponents == canonical(target).pathComponents else { throw Failure.unsafePath }
                return try snapshot(coordinatedURL)
            }
        }
        if let error { throw error }
        return try result.get()
    }

    private func existingBytes(at url: URL) throws -> Data? {
        do { return try Data(contentsOf: url) }
        catch let error as CocoaError where error.code == .fileReadNoSuchFile || error.code == .fileNoSuchFile {
            let siblings = try files.contentsOfDirectory(atPath: url.deletingLastPathComponent().path)
            guard !siblings.contains(".\(url.lastPathComponent).icloud") else { throw Failure.unreadableCloudFile }
            return nil
        }
    }

    private func coordinatedWrite(_ snapshot: Snapshot, path: [String], under root: URL, expected: Data?,
                                  isCancelled: () -> Bool, sourceStillMatches: () -> Bool = { true }) throws -> WriteResult {
        let url = try checkedURL(path, under: root)
        try files.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        var error: NSError?
        var result: Result<WriteResult, Error> = .failure(Failure.folderUnavailable)
        coordinator.coordinate(writingItemAt: url, options: .forReplacing, error: &error) { coordinatedURL in
            result = Result {
                guard !isCancelled() else { return .cancelled }
                let target = try checkedURL(path, under: root)
                guard canonical(coordinatedURL).pathComponents == canonical(target).pathComponents else { throw Failure.unsafePath }
                guard try existingBytes(at: coordinatedURL) == expected else { throw Failure.targetChanged }
                guard sourceStillMatches() else { return .sourceChanged }
                guard !isCancelled() else { return .cancelled }
                try snapshot.data.write(to: coordinatedURL, options: .atomic)
                try files.setAttributes([.modificationDate: snapshot.modified], ofItemAtPath: coordinatedURL.path)
                return .written
            }
        }
        if let error { throw error }
        return try result.get()
    }

    private func renameRecordPath(source: String, hash: String) -> [String] {
        [game, ".alicehub-renames", Self.digest(Data((source + "\n" + hash).utf8)) + ".json"]
    }

    private func publishedRename(source: String, hash: String) throws -> Bool {
        let path = renameRecordPath(source: source, hash: hash)
        let url = try checkedURL(path, under: folder)
        let placeholder = url.deletingLastPathComponent().appendingPathComponent("." + url.lastPathComponent + ".icloud")
        guard files.fileExists(atPath: url.path) || files.fileExists(atPath: placeholder.path) else { return false }
        let rename = try JSONDecoder().decode(Rename.self, from: coordinatedRead(path: path, under: folder).data)
        return rename.source == source && rename.hash == hash && rename.destination != source
    }

    func rename(_ entry: SaveManager.FileEntry, named name: String, using manager: SaveManager) throws {
        let localTarget = try manager.destination(for: entry, named: name)
        let newName = localTarget.lastPathComponent
        guard entry.url.lastPathComponent != newName else { return }
        let parts = entry.path.split(separator: "/").map(String.init)
        let newParts = Array(parts.dropLast()) + [newName]
        let oldPath = [game] + parts
        let newPath = [game] + newParts
        let source = try checkedURL(oldPath, under: folder)
        let target = try checkedURL(newPath, under: folder)
        guard files.fileExists(atPath: source.deletingLastPathComponent().path) else {
            try manager.rename(entry, named: name)
            return
        }

        var error: NSError?
        var result: Result<Void, Error> = .failure(Failure.folderUnavailable)
        coordinator.coordinate(writingItemAt: source, options: .forMoving,
                               writingItemAt: target, options: .forReplacing, error: &error) { sourceURL, targetURL in
            result = Result {
                guard canonical(sourceURL) == canonical(try checkedURL(oldPath, under: folder)),
                      canonical(targetURL) == canonical(try checkedURL(newPath, under: folder)) else {
                    throw Failure.unsafePath
                }
                guard try existingBytes(at: targetURL) == nil else { throw Failure.renameConflict }
                guard let bytes = try existingBytes(at: sourceURL) else {
                    try manager.rename(entry, named: name)
                    return
                }
                var records = loadRecords()
                guard let base = records[entry.path], base.conflict == nil, Self.digest(bytes) == base.hash else {
                    throw Failure.renameConflict
                }
                try manager.rename(entry, named: name)
                var recordToRemove: URL?
                do {
                    let destination = newParts.joined(separator: "/")
                    let markerPath = renameRecordPath(source: entry.path, hash: base.hash)
                    let markerURL = try checkedURL(markerPath, under: folder)
                    if try !publishedRename(source: entry.path, hash: base.hash) {
                        let rename = Rename(source: entry.path, destination: destination, hash: base.hash)
                        let marker = Snapshot(data: try JSONEncoder().encode(rename), modified: Date())
                        guard try coordinatedWrite(marker, path: markerPath, under: folder, expected: nil,
                                                   isCancelled: { false }) == .written else { throw Failure.targetChanged }
                        recordToRemove = markerURL
                    }
                    coordinator.item(at: sourceURL, willMoveTo: targetURL)
                    try files.moveItem(at: sourceURL, to: targetURL)
                    coordinator.item(at: sourceURL, didMoveTo: targetURL)
                } catch {
                    if let recordToRemove { try? files.removeItem(at: recordToRemove) }
                    try files.moveItem(at: localTarget, to: entry.url)
                    throw error
                }
                records.removeValue(forKey: entry.path)
                records[newParts.joined(separator: "/")] = base
                try? saveRecords(records)
            }
        }
        if let error { throw error }
        try result.get()
    }

    @discardableResult func sync(mode: Mode = .twoWay, isCancelled: () -> Bool = { false }) throws -> Summary {
        var summary = Summary()
        guard !isCancelled() else { return summary }
        _ = try checkedURL(["Saves", game], under: documents)
        _ = try checkedURL([game], under: folder)
        let mine = try scan(local)
        let theirs = try scan(remote)
        var records = loadRecords()
        defer { try? saveRecords(records) }
        var backedUp = false
        paths: for path in mine.union(theirs).sorted() {
            guard !isCancelled() else { break }
            if mode == .uploadOnly && !mine.contains(path) { continue }
            let parts = path.split(separator: "/").map(String.init)
            let localPath = ["Saves", game] + parts
            let remotePath = [game] + parts
            let a: Snapshot?
            if mine.contains(path) {
                let source = try checkedURL(localPath, under: documents)
                guard let snapshot = stableLocalSnapshot(source) else {
                    summary.skipped += 1
                    continue
                }
                a = snapshot
            } else {
                a = nil
            }
            let b = theirs.contains(path) ? try coordinatedRead(path: remotePath, under: folder) : nil
            let base = records[path]
            if let a, b == nil, let base, try publishedRename(source: path, hash: base.hash) {
                guard Self.digest(a.data) == base.hash, base.conflict == nil else {
                    summary.conflicts += 1
                    continue
                }
                guard mode == .twoWay else { continue }
                if !backedUp {
                    try beforeLocalChange()
                    backedUp = true
                }
                let source = try checkedURL(localPath, under: documents)
                guard !isCancelled() else { break }
                guard let current = stableLocalSnapshot(source), current.data == a.data else {
                    summary.skipped += 1
                    continue
                }
                try files.removeItem(at: source)
                records.removeValue(forKey: path)
                continue
            }
            let pull: Bool
            if let a, let b {
                let localHash = Self.digest(a.data)
                let remoteHash = Self.digest(b.data)
                if localHash == remoteHash {
                    records[path] = Record(hash: localHash)
                    continue
                }
                let localChanged = localHash != base?.hash
                let remoteChanged = remoteHash != base?.hash
                if base?.conflict != nil || (localChanged && remoteChanged) {
                    records[path] = Record(hash: base?.hash ?? "", conflict: [localHash, remoteHash])
                    summary.conflicts += 1
                    continue
                }
                pull = remoteChanged
            } else {
                pull = a == nil
            }
            if pull, let b {
                guard mode == .twoWay else { continue }
                if a != nil && !backedUp {
                    try beforeLocalChange()
                    backedUp = true
                }
                guard try coordinatedWrite(b, path: localPath, under: documents, expected: a?.data,
                                           isCancelled: isCancelled) == .written else { break }
                records[path] = Record(hash: Self.digest(b.data))
                summary.pulled += 1
            } else if let a {
                let hash = Self.digest(a.data)
                let result = try coordinatedWrite(a, path: remotePath, under: folder, expected: b?.data,
                                                  isCancelled: isCancelled) {
                    guard let source = try? checkedURL(localPath, under: documents),
                          let current = stableLocalSnapshot(source) else { return false }
                    return current.data.count == a.data.count && current.modified == a.modified
                        && Self.digest(current.data) == hash
                }
                switch result {
                case .written:
                    records[path] = Record(hash: hash)
                    summary.pushed += 1
                case .sourceChanged:
                    summary.skipped += 1
                case .cancelled:
                    break paths
                }
            }
        }
        return summary
    }
}

/// The user's chosen sync folder, kept as a security-scoped bookmark.
enum SaveSyncFolder {
    private static let queue = DispatchQueue(label: "io.github.alicehub.saveSync", qos: .userInitiated)

    static func enqueue(_ work: @escaping () -> Void) {
        queue.async { work() }
    }

    static func finish(_ work: @escaping () -> Void) {
        let done = DispatchSemaphore(value: 0)
        enqueue { work(); done.signal() }
        done.wait()
    }

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
        let root = documents ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let manager = SaveManager(game: game, documents: root, support: support) { _, _ in }
        return SaveSync(game: game, documents: root, folder: folder, state: state(game: game, support: support)) { try manager.backup() }
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
    @discardableResult static func sync(game: String, mode: SaveSync.Mode = .twoWay,
                                       documents: URL? = nil, support: URL? = nil,
                                       isCancelled: () -> Bool = { false }) throws -> SaveSync.Summary? {
        guard !isCancelled() else { return nil }
        return try withFolder { folder in
            try make(game: game, folder: folder, documents: documents, support: support).sync(mode: mode, isCancelled: isCancelled)
        }
    }

    static func rename(_ entry: SaveManager.FileEntry, named name: String, using manager: SaveManager,
                       documents: URL? = nil, support: URL? = nil) throws {
        let synced: Void? = try withFolder { folder in
            try make(game: manager.game, folder: folder, documents: documents, support: support)
                .rename(entry, named: name, using: manager)
        }
        if synced == nil { try manager.rename(entry, named: name) }
    }

    /// Uploads every game that has local saves.
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
            for game in names(saves).sorted() {
                let result = try make(game: game, folder: folder, documents: documents, support: support).sync(mode: .uploadOnly)
                total.pushed += result.pushed
                total.conflicts += result.conflicts
                total.skipped += result.skipped
            }
            return total
        }
    }
}
