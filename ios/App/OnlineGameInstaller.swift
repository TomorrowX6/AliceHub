import Foundation
import Combine

final class OnlineGameInstaller {
    private let transfer = ModInstaller()
    private let lock = NSLock()
    private var cancelled = false
    private let files = FileManager.default

    func cancel() {
        lock.lock(); cancelled = true; lock.unlock()
        transfer.cancel()
    }

    private func checkCancelled() throws {
        lock.lock(); let stopped = cancelled; lock.unlock()
        if stopped { throw CancellationError() }
    }

    func install(_ game: OnlineGame, manifest: OnlineGameManifest, in games: URL,
                 baseURL: URL? = nil, progress: @escaping (String, Int64, Int64) -> Void) throws -> URL {
        try checkCancelled()
        let destination = games.appendingPathComponent(manifest.baseName, isDirectory: true)
        guard !files.fileExists(atPath: destination.path) else { throw LibraryError.alreadyExists }
        try files.createDirectory(at: games, withIntermediateDirectories: true)
        let work = games.appendingPathComponent(".online-game-" + game.id, isDirectory: true)
        let cache = work.appendingPathComponent("cache", isDirectory: true)
        let staging = work.appendingPathComponent("staging", isDirectory: true)
        for directory in [games, work, cache, staging] {
            if files.fileExists(atPath: directory.path) {
                let values = try directory.resourceValues(forKeys: [.isSymbolicLinkKey, .isDirectoryKey])
                guard values.isSymbolicLink != true && values.isDirectory == true else { throw ArchiveError.invalidPath }
            }
        }
        try files.createDirectory(at: cache, withIntermediateDirectories: true)
        if files.fileExists(atPath: staging.path) { try files.removeItem(at: staging) }
        try files.createDirectory(at: staging, withIntermediateDirectories: true)
        defer { try? files.removeItem(at: staging) }
        var completed: Int64 = 0
        var lastProgress = Date.distantPast
        let total = manifest.totalBytes
        for (index, entry) in manifest.files.enumerated() {
            try checkCancelled()
            progress("正在校验 / 下载 \(index + 1) / \(manifest.files.count)\n" + entry.path, completed, total)
            var url = (baseURL ?? game.baseURL).appendingPathComponent("files")
            for component in entry.key.split(separator: "/") { url.appendPathComponent(String(component)) }
            let release = ModReleaseEntry(fileId: index, version: entry.path, fileName: entry.path,
                                          downloadUrl: url.absoluteString, expectedSize: entry.size,
                                          expectedSha256: entry.sha256.lowercased())
            let before = completed
            let downloaded = try transfer.downloadVerifiedFile(release, cache: cache) { done, _ in
                guard Date().timeIntervalSince(lastProgress) >= 0.15 else { return }
                lastProgress = Date()
                progress("正在下载 \(index + 1) / \(manifest.files.count)\n" + entry.path,
                         before + min(done, entry.size), total)
            }
            try checkCancelled()
            if entry.extract == true {
                progress("正在解压并安装\n" + entry.path, completed, total)
                _ = try ArchiveExtractor.extract(downloaded, to: staging, checkCancelled: self.checkCancelled)
            } else {
                let output = staging.appendingPathComponent(entry.path)
                _ = try ArchiveExtractor.outputPath(entry.path, size: entry.size, root: staging, shouldSkip: { _ in false })
                try files.createDirectory(at: output.deletingLastPathComponent(), withIntermediateDirectories: true)
                if files.fileExists(atPath: output.path) { try files.removeItem(at: output) }
                try files.copyItem(at: downloaded, to: output)
            }
            completed += entry.size
            progress("已校验 \(index + 1) / \(manifest.files.count)", completed, total)
        }
        try checkCancelled()
        guard GameLibrary.isGame(staging) else { throw LibraryError.notGame }
        guard !files.fileExists(atPath: destination.path) else { throw LibraryError.alreadyExists }
        try files.moveItem(at: staging, to: destination)
        try? files.removeItem(at: work)
        return destination
    }
}

final class OnlineGameDownloadModel: ObservableObject {
    @Published private(set) var running = false
    @Published private(set) var activeID: String?
    @Published private(set) var progressText = ""
    @Published private(set) var completed: Int64 = 0
    @Published private(set) var total: Int64 = 0
    @Published private(set) var succeeded = false
    @Published private(set) var failure: String?
    private var installer: OnlineGameInstaller?

    func cancel() { installer?.cancel() }

    func start(_ game: OnlineGame, manifest: OnlineGameManifest, library: GameLibrary) {
        guard !running && !library.importing && !library.launching else { return }
        let installer = OnlineGameInstaller()
        self.installer = installer
        activeID = game.id; running = true; succeeded = false; failure = nil
        completed = 0; total = manifest.totalBytes; progressText = "正在准备下载…"
        let games = library.gamesDirectory
        DispatchQueue.global(qos: .userInitiated).async {
            var result: Error?
            do {
                _ = try installer.install(game, manifest: manifest, in: games) { message, completed, total in
                    DispatchQueue.main.async {
                        self.progressText = message; self.completed = completed; self.total = total
                    }
                }
            } catch { result = error }
            let message = result.map { $0 is CancellationError ? "已取消下载；重试时会复用已下载的文件。" : $0.localizedDescription }
            DispatchQueue.main.async {
                self.running = false; self.installer = nil; self.failure = message; self.succeeded = message == nil
                library.refresh()
            }
        }
    }
}
