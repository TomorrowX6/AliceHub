import Foundation

struct HubCacheFiles {
    let caches: URL
    let games: URL
    private let files = FileManager.default

    func targets(includeDownloads: Bool) -> [URL] {
        let cachedFiles = children(of: caches)
        let gameCaches = children(of: games).filter {
            let name = $0.lastPathComponent
            let isScratch = [".import-", ".mod_online_extract_temp_", ".mod_extract_temp_", ".mod_import_temp_"]
                .contains { name.hasPrefix($0) }
            let modified = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
            return (isScratch && modified.map { Date().timeIntervalSince($0) >= 600 } == true) || (includeDownloads &&
                (name.hasPrefix(".online-game-") || name == ".online_mod_cache" || name == ".uncensored_cache"))
        }
        return cachedFiles + gameCaches
    }

    func size(of urls: [URL]) -> Int64 {
        urls.reduce(0) { $0 + size(of: $1) }
    }

    func clear(includeDownloads: Bool) throws {
        var failure: Error?
        for url in targets(includeDownloads: includeDownloads) {
            do { try files.removeItem(at: url) }
            catch let error as NSError where error.domain == NSCocoaErrorDomain && error.code == NSFileNoSuchFileError { continue }
            catch { failure = error }
        }
        if let failure { throw failure }
    }

    private func children(of directory: URL) -> [URL] {
        let values = try? directory.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
        guard values?.isDirectory == true, values?.isSymbolicLink != true else { return [] }
        return (try? files.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
    }

    private func size(of url: URL) -> Int64 {
        guard let values = try? url.resourceValues(forKeys: [.isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey, .fileSizeKey]),
              values.isSymbolicLink != true else { return 0 }
        return values.isDirectory == true ? size(of: children(of: url)) : Int64(values.fileSize ?? 0)
    }
}

enum HubToolFiles {
    struct Manual {
        static let resourceRules = """
        [
          {"trigger":{"url-filter":".*"},"action":{"type":"block"}},
          {"trigger":{"url-filter":"^file:"},"action":{"type":"ignore-previous-rules"}},
          {"trigger":{"url-filter":"^data:"},"action":{"type":"ignore-previous-rules"}}
        ]
        """

        let root: URL
        let index: URL

        func canNavigate(to url: URL) -> Bool {
            HubToolFiles.contains(url, in: root)
        }
    }

    static func manual(in game: URL) -> Manual? {
        guard game.isFileURL else { return nil }
        let root = canonical(game)
        let index = root.appendingPathComponent("Manual/index.html")
        let manual = Manual(root: root, index: index)
        guard manual.canNavigate(to: index),
              (try? canonical(index).resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else { return nil }
        return manual
    }

    static func contains(_ url: URL, in directory: URL) -> Bool {
        guard url.isFileURL else { return false }
        let root = canonical(directory).pathComponents
        let path = canonical(url).pathComponents
        return path.count > root.count && path.starts(with: root)
    }

    private static func canonical(_ url: URL) -> URL {
        var ancestor = url.standardizedFileURL
        var missing: [String] = []
        while ancestor.path != "/" && !FileManager.default.fileExists(atPath: ancestor.path) {
            missing.append(ancestor.lastPathComponent)
            ancestor.deleteLastPathComponent()
        }
        return missing.reversed().reduce(ancestor.resolvingSymlinksInPath().standardizedFileURL) {
            $0.appendingPathComponent($1)
        }
    }

    static func logTail(_ url: URL, limit: UInt64 = 256 * 1024) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        do {
            let end = try handle.seekToEnd()
            try handle.seek(toOffset: end > limit ? end - limit : 0)
            let data = try handle.read(upToCount: Int(limit)) ?? Data()
            return data.isEmpty ? nil : String(decoding: data, as: UTF8.self)
        } catch { return nil }
    }
}
