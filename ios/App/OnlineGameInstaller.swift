import Foundation
import Combine
import Darwin

final class OnlineGameInstaller {
    private let transfer = ModInstaller()
    private let lock = NSLock()
    private var cancelled = false
    private let files = FileManager.default

    func cancel() {
        lock.lock(); cancelled = true; lock.unlock()
        transfer.cancel()
    }

    private func isCancelled() -> Bool {
        lock.lock(); defer { lock.unlock() }
        return cancelled
    }

    private func checkCancelled() throws {
        if isCancelled() { throw CancellationError() }
    }

    func install(_ game: OnlineGame, manifest: OnlineGameManifest, in games: URL,
                 baseURL: URL? = nil,
                 concurrency: Int = DownloadConcurrency.current,
                 minSegmentedBytes: Int64 = ModInstaller.defaultMinSegmentedBytes,
                 progress: @escaping (String, Int64, Int64) -> Void) throws -> URL {
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

        let total = manifest.totalBytes
        let totalFiles = manifest.files.count
        let effectiveConcurrency = DownloadConcurrency.clamped(concurrency)

        struct DownloadGroup {
            let release: ModReleaseEntry
            let cachePath: String
            let entryIndices: [Int]
            let entrySize: Int64
            let displayPath: String
        }

        // Deduplicate entries sharing identical cache files (e.g. identical SHA-256)
        // so concurrent workers never race on the same .part file or issue duplicate HTTP requests.
        var groupIndexByCachePath: [String: Int] = [:]
        var groups: [DownloadGroup] = []
        for (index, entry) in manifest.files.enumerated() {
            var url = (baseURL ?? game.baseURL).appendingPathComponent("files")
            for component in entry.key.split(separator: "/") { url.appendPathComponent(String(component)) }
            let release = ModReleaseEntry(fileId: index, version: entry.path, fileName: entry.path,
                                          downloadUrl: url.absoluteString, expectedSize: entry.size,
                                          expectedSha256: entry.sha256.lowercased())
            let cachePath = ModInstaller.cacheFileURL(for: release, in: cache).path
            if let existingIdx = groupIndexByCachePath[cachePath] {
                let prev = groups[existingIdx]
                groups[existingIdx] = DownloadGroup(
                    release: prev.release,
                    cachePath: prev.cachePath,
                    entryIndices: prev.entryIndices + [index],
                    entrySize: prev.entrySize,
                    displayPath: prev.displayPath
                )
            } else {
                groupIndexByCachePath[cachePath] = groups.count
                groups.append(DownloadGroup(
                    release: release,
                    cachePath: cachePath,
                    entryIndices: [index],
                    entrySize: entry.size,
                    displayPath: entry.path
                ))
            }
        }

        var downloadedByEntry = Array<URL?>(repeating: nil, count: totalFiles)

        if effectiveConcurrency <= 1 || groups.count <= 1 {
            var completed: Int64 = 0
            var lastProgress = Date.distantPast
            let perFileConns = groups.count == 1 ? effectiveConcurrency : 1
            for group in groups {
                try checkCancelled()
                let firstIndex = group.entryIndices[0]
                progress(hubText("正在校验 / 下载 %lld / %lld\n%@", firstIndex + 1, totalFiles, group.displayPath), completed, total)
                let before = completed
                let multiplier = Int64(group.entryIndices.count)
                let downloaded = try transfer.downloadVerifiedFile(
                    group.release,
                    cache: cache,
                    maxConnections: perFileConns,
                    minSegmentedBytes: minSegmentedBytes
                ) { done, _ in
                    guard Date().timeIntervalSince(lastProgress) >= 0.08 else { return }
                    lastProgress = Date()
                    let credited = min(before + min(done, group.entrySize) * multiplier, total)
                    progress(hubText("正在下载 %lld / %lld\n%@", firstIndex + 1, totalFiles, group.displayPath), credited, total)
                }
                for idx in group.entryIndices {
                    downloadedByEntry[idx] = downloaded
                }
                completed = min(completed + group.entrySize * multiplier, total)
            }
        } else {
            let stateLock = NSLock()
            let progressEmitLock = NSLock()
            var entryDone = Array<Int64>(repeating: 0, count: totalFiles)
            var maxReported: Int64 = 0
            var finishedFiles = 0
            var activePaths: [String] = []
            var lastProgress = Date.distantPast
            var firstError: Error?

            func emitDownloadProgress(force: Bool = false) {
                progressEmitLock.lock()
                defer { progressEmitLock.unlock() }
                var payload: (String, Int64)?
                stateLock.lock()
                let now = Date()
                if force || now.timeIntervalSince(lastProgress) >= 0.08 {
                    lastProgress = now
                    let sum = min(entryDone.reduce(0, +), total)
                    if sum > maxReported { maxReported = sum }
                    let displayIdx = min(finishedFiles + 1, totalFiles)
                    let activeCount = min(activePaths.count, effectiveConcurrency)
                    let activeName = activePaths.first ?? groups[0].displayPath
                    let title = activeCount > 1
                        ? hubText("正在下载 %lld / %lld · %lld 并发\n%@", displayIdx, totalFiles, activeCount, activeName)
                        : hubText("正在下载 %lld / %lld\n%@", displayIdx, totalFiles, activeName)
                    payload = (title, maxReported)
                }
                stateLock.unlock()
                if let payload {
                    progress(payload.0, payload.1, total)
                }
            }

            let opQueue = OperationQueue()
            opQueue.maxConcurrentOperationCount = effectiveConcurrency
            let dispatchGroup = DispatchGroup()

            // Schedule largest unique files first while preserving manifest-order staging below
            let scheduledGroups = groups.sorted { $0.entrySize > $1.entrySize }
            for group in scheduledGroups {
                stateLock.lock()
                activePaths.append(group.displayPath)
                stateLock.unlock()
                transfer.downloadVerifiedFileOnQueue(
                    group.release,
                    cache: cache,
                    operationQueue: opQueue,
                    group: dispatchGroup,
                    maxConnections: effectiveConcurrency,
                    minSegmentedBytes: minSegmentedBytes,
                    progress: { done, _ in
                        let clamped = min(done, group.entrySize)
                        stateLock.lock()
                        for idx in group.entryIndices {
                            if clamped > entryDone[idx] { entryDone[idx] = clamped }
                        }
                        stateLock.unlock()
                        emitDownloadProgress(force: false)
                    },
                    completion: { result in
                        stateLock.lock()
                        if let pos = activePaths.firstIndex(of: group.displayPath) {
                            activePaths.remove(at: pos)
                        }
                        switch result {
                        case .success(let fileURL):
                            for idx in group.entryIndices {
                                downloadedByEntry[idx] = fileURL
                                entryDone[idx] = group.entrySize
                            }
                            finishedFiles += group.entryIndices.count
                            stateLock.unlock()
                            emitDownloadProgress(force: true)
                        case .failure(let error):
                            let isFirst = firstError == nil
                            if isFirst { firstError = error }
                            stateLock.unlock()
                            if isFirst { self.transfer.cancel() }
                        }
                    }
                )
            }

            dispatchGroup.wait()
            stateLock.lock()
            let batchError = firstError
            stateLock.unlock()
            if let batchError {
                if self.isCancelled() { throw CancellationError() }
                throw batchError
            }
            try checkCancelled()
        }

        // Phase 2: Stage and extract strictly in manifest order so overlays are deterministic
        for (index, entry) in manifest.files.enumerated() {
            try checkCancelled()
            guard let downloaded = downloadedByEntry[index] else {
                throw ModError.message(hubText("下载文件缺失：%@", entry.path))
            }
            if entry.extract == true {
                progress(hubText("正在解压并安装\n%@", entry.path), total, total)
                _ = try ArchiveExtractor.extract(downloaded, to: staging, checkCancelled: self.checkCancelled)
            } else {
                guard let output = try ArchiveExtractor.outputPath(entry.path, size: 0, root: staging) else {
                    throw ArchiveError.invalidPath
                }
                if files.fileExists(atPath: output.path) { try files.removeItem(at: output) }
                if clonefile(downloaded.path, output.path, 0) != 0 {
                    try ArchiveExtractor.checkSpace(entry.size, at: staging)
                    try files.copyItem(at: downloaded, to: output)
                }
            }
            progress(hubText("已校验 %lld / %lld", index + 1, totalFiles), total, total)
        }
        try checkCancelled()
        guard GameLibrary.isGame(staging) else { throw LibraryError.notGame }
        try GameLibrary.normalizeRance06DungeonData(staging)
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
    private var paused: (game: OnlineGame, manifest: OnlineGameManifest)?

    func cancel() { paused = nil; installer?.cancel() }

    /// Continues a download the system paused while the app was in the background.
    func resumePaused(library: GameLibrary) {
        guard let paused, !running else { return }
        start(paused.game, manifest: paused.manifest, library: library)
    }

    func start(_ game: OnlineGame, manifest: OnlineGameManifest, library: GameLibrary) {
        guard !running && !library.importing && !library.launching else { return }
        let installer = OnlineGameInstaller()
        self.installer = installer
        paused = nil
        activeID = game.id; running = true; succeeded = false; failure = nil
        completed = 0; total = manifest.totalBytes; progressText = hubText("正在准备下载…")
        let games = library.gamesDirectory
        let activity = BackgroundActivity(title: game.displayTitle(in: .current)) { installer.cancel() }
        DispatchQueue.global(qos: .userInitiated).async {
            var result: Error?
            do {
                _ = try installer.install(game, manifest: manifest, in: games) { message, completed, total in
                    activity.update(fraction: total > 0 ? Double(completed) / Double(total) : nil, detail: message)
                    DispatchQueue.main.async {
                        self.progressText = message; self.completed = completed; self.total = total
                    }
                }
            } catch { result = error }
            let wasPaused = result is CancellationError && activity.expired
            let autoResume = wasPaused && activity.autoResumes
            let message = result.map {
                autoResume ? hubText("下载已暂停，回到前台会继续。")
                    : wasPaused ? hubText("下载已暂停；重试时会复用已下载的文件。")
                    : $0 is CancellationError ? hubText("已取消下载；重试时会复用已下载的文件。") : $0.localizedDescription
            }
            activity.end(wasPaused ? .paused : message.map { .failed($0) } ?? .succeeded)
            DispatchQueue.main.async {
                self.running = false; self.installer = nil; self.failure = message; self.succeeded = message == nil
                library.refresh()
                if autoResume {
                    self.paused = (game, manifest)
                    if BackgroundActivity.appActive && OnlineInstall.enabled { self.resumePaused(library: library) }
                }
            }
        }
    }
}
