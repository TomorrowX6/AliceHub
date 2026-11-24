import Foundation
import Combine
import CryptoKit
import Darwin

final class ModInstaller: ObservableObject {
    static let defaultMinSegmentedBytes: Int64 = 8 * 1024 * 1024
    private static let minSegmentSizeBytes: Int64 = 4 * 1024 * 1024
    private static let segmentMetaPersistBytes: Int64 = 2 * 1024 * 1024

    @Published private(set) var isRunning = false
    @Published private(set) var progressText = ""
    @Published private(set) var fraction: Double?
    @Published private(set) var succeeded = false
    @Published private(set) var failure: String?
    private let queue = DispatchQueue(label: "io.github.alicehub.mod-install", qos: .userInitiated)
    private let lock = NSLock()
    private var running = false
    private var cancelled = false
    private var activeTasks: [ObjectIdentifier: URLSessionTask] = [:]
    private var activity: BackgroundActivity?

    func cancel() {
        lock.lock()
        cancelled = true
        let tasks = Array(activeTasks.values)
        activeTasks.removeAll()
        lock.unlock()
        for task in tasks { task.cancel() }
    }
    private func isCancelled() -> Bool { lock.lock(); defer { lock.unlock() }; return cancelled }
    private func checkCancelled() throws { if isCancelled() { throw CancellationError() } }
    fileprivate func registerTask(_ task: URLSessionTask) {
        lock.lock()
        let stop = cancelled
        if !stop { activeTasks[ObjectIdentifier(task)] = task }
        lock.unlock()
        if stop { task.cancel() }
    }
    fileprivate func unregisterTask(_ task: URLSessionTask) {
        lock.lock()
        activeTasks.removeValue(forKey: ObjectIdentifier(task))
        lock.unlock()
    }
    private func report(_ text: String, _ fraction: Double? = nil) {
        lock.lock(); let activity = activity; lock.unlock()
        activity?.update(fraction: fraction, detail: text)
        DispatchQueue.main.async { self.progressText = text; self.fraction = fraction.map { min(1, max(0, $0)) } }
    }
    @discardableResult
    private func start(_ work: @escaping () throws -> Void) -> Bool {
        lock.lock()
        guard !running else { lock.unlock(); return false }
        running = true; cancelled = false; lock.unlock()
        let publishStart = { self.isRunning = true; self.succeeded = false; self.failure = nil; self.progressText = "正在加载…"; self.fraction = nil }
        if Thread.isMainThread { publishStart() } else { DispatchQueue.main.async(execute: publishStart) }
        let activity = BackgroundActivity(title: "MOD 安装") { [weak self] in self?.cancel() }
        lock.lock(); self.activity = activity; lock.unlock()
        queue.async {
            var failure: Error?
            do { try self.checkCancelled(); try work() } catch { failure = error }
            let result = failure
            self.lock.lock(); self.activity = nil; self.lock.unlock()
            let wasPaused = result is CancellationError && activity.expired
            activity.end(wasPaused ? .paused : result is CancellationError ? .failed("已取消")
                         : result.map { .failed($0.localizedDescription) } ?? .succeeded)
            DispatchQueue.main.async {
                self.isRunning = false; self.succeeded = result == nil
                self.failure = wasPaused ? "安装已暂停，请重新安装。" : result is CancellationError ? nil : result?.localizedDescription
                if result is CancellationError { self.progressText = "已取消" }
                else if result == nil { self.fraction = 1 }
                self.lock.lock(); self.running = false; self.activeTasks.removeAll(); self.lock.unlock()
            }
        }
        return true
    }

    @discardableResult
    func installOnlinePlan(_ steps: [ModReleaseEntry], to game: URL, descriptor: ModGameDescriptor) -> Bool {
        guard !steps.isEmpty else { return false }
        return start {
            for (index, release) in steps.enumerated() {
                try self.checkCancelled()
                let prefix = steps.count > 1 ? "[\(index + 1)/\(steps.count)] " : ""
                self.report(prefix + "准备下载 \(release.version)…", 0)
                let archive = try self.download(release, games: game.deletingLastPathComponent(), prefix: prefix)
                let staging = game.deletingLastPathComponent().appendingPathComponent(".mod_online_extract_temp_" + UUID().uuidString)
                defer { try? ModFiles.removeIfPresent(staging) }
                try self.extract(archive, to: staging)
                try self.checkCancelled()
                let previous = InstalledModInfo.readOrInfer(game)
                if !descriptor.isCnPatch && ModOverlay.detectInstalledMod(game) && !ModOverlay.canRestoreVanilla(game) {
                    let storedGroup = previous?.group ?? ""
                    let existingGroup = storedGroup.isEmpty ? ModCatalog.inferModGroup(directory: game) : storedGroup
                    if ModCatalog.groupsConflict(release.group, existingGroup) {
                        throw ModError.message("整合包无原版备份，无法原地切换其他 MOD 系列，请先安装原版游戏后再安装。")
                    }
                }
                try ModOverlay.applyModStaging(staging, to: game, cleanReplace: !release.isPatch, isCnPatch: descriptor.isCnPatch) { full in
                    let resolvedGroup = release.group.isEmpty ? ((!release.isPatch || full) ? "" : (previous?.group ?? "")) : release.group
                    try InstalledModInfo(version: release.version,
                    baseVersion: !release.isPatch || full ? release.version : (release.baseVersion ?? previous?.baseVersion),
                    fileId: release.fileId, baseFileId: !release.isPatch || full ? release.fileId : (release.baseFileId ?? previous?.baseFileId ?? -1),
                    fileName: release.fileName, uploadedAt: release.uploadedAt, source: "online",
                        installedAtMs: Int64(Date().timeIntervalSince1970 * 1000), kind: descriptor.overlayKind, group: resolvedGroup).write(to: game)
                }
                try ModFiles.removeIfPresent(archive)
                try ModFiles.removeIfPresent(archive.appendingPathExtension("meta"))
            }
        }
    }

    @discardableResult
    func installLocalArchive(_ source: URL, to game: URL, descriptor: ModGameDescriptor, cleanReplace: Bool = false) -> Bool {
        start {
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            let staging = game.deletingLastPathComponent().appendingPathComponent(".mod_extract_temp_" + UUID().uuidString)
            defer { try? ModFiles.removeIfPresent(staging) }
            try self.extract(source, to: staging)
            try self.applyLocal(staging, label: source.lastPathComponent, game: game, descriptor: descriptor, cleanReplace: cleanReplace)
        }
    }

    @discardableResult
    func installLocalDirectory(_ source: URL, to game: URL, descriptor: ModGameDescriptor, cleanReplace: Bool = false) -> Bool {
        start {
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            let staging = game.deletingLastPathComponent().appendingPathComponent(".mod_import_temp_" + UUID().uuidString)
            defer { try? ModFiles.removeIfPresent(staging) }
            var coordinationError: NSError?, copyError: Error?
            NSFileCoordinator().coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { readable in
                do {
                    try ModFiles.files.createDirectory(at: staging, withIntermediateDirectories: true)
                    var total: Int64 = 0
                    for file in try ModFiles.walk(readable) {
                        try self.checkCancelled()
                        let relative = try ModFiles.relative(file, to: readable)
                        if NativeGameImport.skip(relative) { continue }
                        let destination = try ModFiles.checked(staging, relative)
                        if ModFiles.isDirectory(file) { try ModFiles.files.createDirectory(at: destination, withIntermediateDirectories: true) }
                        else if ModFiles.isFile(file) {
                            try ModFiles.files.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
                            try ModFiles.files.copyItem(at: file, to: destination)
                            total += Int64((try file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
                            self.report("已复制 \(total / 1048576) MB\n\(relative)")
                        }
                    }
                } catch { copyError = error }
            }
            if let error = coordinationError ?? copyError { throw error }
            try self.applyLocal(staging, label: source.lastPathComponent, game: game, descriptor: descriptor, cleanReplace: cleanReplace)
        }
    }

    @discardableResult
    func restoreVanilla(_ game: URL, descriptor: ModGameDescriptor) -> Bool {
        start {
            guard ModOverlay.canRestoreVanilla(game) else { throw ModError.message(descriptor.isCnPatch ? "当前无原版备份，请删除游戏本体后重装原版。" : "整合包无原版备份，请删除游戏本体后重装原版。") }
            try self.checkCancelled()
            try ModOverlay.restoreVanilla(game)
            self.report(descriptor.isCnPatch ? "已卸载汉化补丁" : "已卸载 MOD", 1)
        }
    }

    private func applyLocal(_ staging: URL, label: String, game: URL, descriptor: ModGameDescriptor, cleanReplace: Bool) throws {
        try checkCancelled()
        let previous = InstalledModInfo.readOrInfer(game)
        if !descriptor.isCnPatch && ModOverlay.detectInstalledMod(game) && !ModOverlay.canRestoreVanilla(game) {
            let storedGroup = previous?.group ?? ""
            let existingGroup = storedGroup.isEmpty ? ModCatalog.inferModGroup(directory: game) : storedGroup
            let incomingGroup = ModCatalog.inferModGroup(fileName: label, directory: staging)
            if ModCatalog.groupsConflict(incomingGroup, existingGroup) {
                throw ModError.message("整合包无原版备份，无法原地切换其他 MOD 系列，请先安装原版游戏后再安装。")
            }
        }
        try ModOverlay.applyModStaging(staging, to: game, cleanReplace: cleanReplace, isCnPatch: descriptor.isCnPatch) { full in
            let version = ModCatalog.inferLocalOverlayVersion(label, isPatchHint: !full, fallbackIniDir: full || descriptor.isCnPatch ? game : nil, isCnPatch: descriptor.isCnPatch)
            let inferredGroup: String
            if descriptor.isCnPatch {
                inferredGroup = ""
            } else {
                let detected = ModCatalog.inferModGroup(fileName: label, directory: game)
                inferredGroup = detected.isEmpty ? (full ? "" : (previous?.group ?? "")) : detected
            }
            try InstalledModInfo(version: version, baseVersion: full ? version : previous?.baseVersion,
            baseFileId: full ? -1 : (previous?.baseFileId ?? -1), fileName: label, source: "local",
                installedAtMs: Int64(Date().timeIntervalSince1970 * 1000), kind: descriptor.overlayKind, group: inferredGroup).write(to: game)
        }
    }
    private func extract(_ archive: URL, to staging: URL) throws {
        try ModArchiveExtractor.extract(file: archive, to: staging, progress: { bytes, path in self.report("已解压 \(bytes / 1048576) MB\n\(path)") }, isCancelled: { self.isCancelled() })
    }

    static func computeSegmentCount(remainingBytes: Int64, maxConnections: Int,
                                    minSegmentedBytes: Int64 = defaultMinSegmentedBytes) -> Int {
        let capped = DownloadConcurrency.clamped(maxConnections)
        guard capped > 1, remainingBytes >= minSegmentedBytes, minSegmentedBytes > 0 else { return 1 }
        let minChunk = min(minSegmentSizeBytes, max(1, minSegmentedBytes / 2))
        let bySize = Int(min(Int64(capped), max(1, remainingBytes / minChunk)))
        return min(capped, max(1, bySize))
    }

    static func cacheFileURL(for release: ModReleaseEntry, in cache: URL) -> URL {
        let hash = release.expectedSha256 ?? ""
        let effectiveUrl = DownloadRoute.current.resolveURL(release.downloadUrl)
        let identity = hash.count >= 16 ? String(hash.prefix(16)) : String(SHA256.hash(data: Data(DownloadRoute.global.resolveURL(effectiveUrl).utf8)).map { String(format: "%02x", $0) }.joined().prefix(16))
        let ext = [".tar.gz", ".tar.xz", ".rar", ".7z", ".zip", ".tgz", ".txz", ".afa", ".ald"].first { release.fileName.lowercased().hasSuffix($0) } ?? ".rar"
        return cache.appendingPathComponent("mod_" + identity + ext + ".part")
    }

    func downloadVerifiedFile(_ release: ModReleaseEntry, cache: URL,
                              maxConnections: Int = DownloadConcurrency.current,
                              minSegmentedBytes: Int64 = defaultMinSegmentedBytes,
                              progress: @escaping (Int64, Int64) -> Void) throws -> URL {
        try checkCancelled()
        return try download(release, games: cache, prefix: "", cacheDirectory: cache,
                            maxConnections: maxConnections, minSegmentedBytes: minSegmentedBytes,
                            progress: progress)
    }

    func downloadVerifiedFileOnQueue(_ release: ModReleaseEntry, cache: URL,
                                     operationQueue: OperationQueue,
                                     group: DispatchGroup,
                                     maxConnections: Int,
                                     minSegmentedBytes: Int64 = defaultMinSegmentedBytes,
                                     progress: @escaping (Int64, Int64) -> Void,
                                     completion: @escaping (Result<URL, Error>) -> Void) {
        group.enter()
        operationQueue.addOperation {
            defer { group.leave() }
            do {
                try self.checkCancelled()
                let url = try self.download(
                    release,
                    games: cache,
                    prefix: "",
                    cacheDirectory: cache,
                    maxConnections: maxConnections,
                    minSegmentedBytes: minSegmentedBytes,
                    externalQueue: operationQueue,
                    externalGroup: group,
                    progress: progress,
                    asyncCompletion: completion
                )
                if let url {
                    completion(.success(url))
                }
            } catch {
                completion(.failure(error))
            }
        }
    }

    private func download(_ release: ModReleaseEntry, games: URL, prefix: String,
                          cacheDirectory: URL? = nil,
                          maxConnections: Int = DownloadConcurrency.current,
                          minSegmentedBytes: Int64 = defaultMinSegmentedBytes,
                          progress: ((Int64, Int64) -> Void)? = nil) throws -> URL {
        guard let url = try download(
            release,
            games: games,
            prefix: prefix,
            cacheDirectory: cacheDirectory,
            maxConnections: maxConnections,
            minSegmentedBytes: minSegmentedBytes,
            externalQueue: nil,
            externalGroup: nil,
            progress: progress,
            asyncCompletion: nil
        ) else {
            throw ModError.message("下载失败，请检查网络与可用空间")
        }
        return url
    }

    private func download(_ release: ModReleaseEntry, games: URL, prefix: String,
                          cacheDirectory: URL? = nil,
                          maxConnections: Int,
                          minSegmentedBytes: Int64,
                          externalQueue: OperationQueue?,
                          externalGroup: DispatchGroup?,
                          progress: ((Int64, Int64) -> Void)?,
                          asyncCompletion: ((Result<URL, Error>) -> Void)?) throws -> URL? {
        let cache = cacheDirectory ?? games.appendingPathComponent(".online_mod_cache")
        try ModFiles.files.createDirectory(at: cache, withIntermediateDirectories: true)
        let hash = release.expectedSha256 ?? ""
        guard hash.isEmpty || hash.modMatch(#"^([0-9a-f]{64})$"#) != nil else { throw ModError.message("SHA-256 校验值无效") }
        let effectiveUrl = DownloadRoute.current.resolveURL(release.downloadUrl)
        let resolvedRelease: ModReleaseEntry = {
            var copy = release
            copy.downloadUrl = effectiveUrl
            return copy
        }()
        let part = Self.cacheFileURL(for: release, in: cache)
        let meta = part.appendingPathExtension("meta")
        let normalizedConns = DownloadConcurrency.clamped(maxConnections)

        func readSavedMeta() -> [String: Any]? {
            guard let data = try? Data(contentsOf: meta), let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            if !hash.isEmpty && (json["sha256"] as? String)?.lowercased() == hash.lowercased() { return json }
            return json["url"] as? String == effectiveUrl ? json : nil
        }

        func parseSegments(_ json: [String: Any]?, expectedTotal: Int64) -> [DownloadSegment]? {
            guard let json, json["segmented"] as? Bool == true else { return nil }
            let total = (json["totalBytes"] as? NSNumber)?.int64Value ?? -1
            guard total > 0, expectedTotal <= 0 || total == expectedTotal,
                  let raw = json["segments"] as? [[NSNumber]], !raw.isEmpty else { return nil }
            var list: [DownloadSegment] = []
            var nextStart: Int64 = 0
            for item in raw {
                guard item.count >= 3 else { return nil }
                let s = item[0].int64Value, e = item[1].int64Value, d = item[2].int64Value
                guard s == nextStart, e >= s else { return nil }
                let len = e - s + 1
                guard d >= 0 && d <= len else { return nil }
                list.append(DownloadSegment(start: s, end: e, done: d))
                nextStart = e + 1
            }
            guard nextStart == total else { return nil }
            return list
        }

        func reportDownloadProgress(_ done: Int64, _ total: Int64) {
            if let progress { progress(done, total) }
            else {
                let fraction = total > 0 ? Double(done) / Double(total) : nil
                self.report(prefix + (fraction.map { "下载中 · \(Int($0 * 100))% · -- MB/s\n\(done / 1048576) / \(max(1, total / 1048576)) MB · \(release.version)" } ?? "下载中 · \(done / 1048576) MB · -- MB/s\n\(release.version)"), fraction)
            }
        }

        let initialMeta = readSavedMeta()
        let initialSegments = ModFiles.exists(part) ? parseSegments(initialMeta, expectedTotal: release.expectedSize) : nil
        if initialMeta?["segmented"] as? Bool == true && initialSegments == nil {
            try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
        }
        let knownTotal: Int64 = {
            if release.expectedSize > 0 { return release.expectedSize }
            return (initialMeta?["totalBytes"] as? NSNumber)?.int64Value ?? -1
        }()
        let curLen = initialSegments == nil ? ModFiles.fileSize(part) : 0
        let remaining = initialSegments.map { $0.reduce(Int64(0)) { $0 + ($1.length - $1.done) } } ?? (knownTotal > 0 ? max(0, knownTotal - curLen) : -1)
        let wantSegmented = initialSegments != nil ||
            (normalizedConns > 1 && knownTotal > 0 && Self.computeSegmentCount(remainingBytes: remaining, maxConnections: normalizedConns, minSegmentedBytes: minSegmentedBytes) >= 2)

        if wantSegmented {
            return try downloadSegmented(
                release: resolvedRelease,
                part: part,
                meta: meta,
                savedMeta: initialMeta,
                existingSegments: initialSegments,
                knownTotal: knownTotal,
                maxConnections: normalizedConns,
                minSegmentedBytes: minSegmentedBytes,
                externalQueue: externalQueue,
                externalGroup: externalGroup,
                progress: reportDownloadProgress,
                asyncCompletion: asyncCompletion
            )
        }

        return try downloadSingleStream(
            release: resolvedRelease,
            part: part,
            meta: meta,
            progress: reportDownloadProgress
        )
    }

    private func downloadSingleStream(release resolvedRelease: ModReleaseEntry,
                                      part: URL,
                                      meta: URL,
                                      progress: @escaping (Int64, Int64) -> Void) throws -> URL {
        let hash = resolvedRelease.expectedSha256 ?? ""
        let effectiveUrl = resolvedRelease.downloadUrl
        func metadata() -> [String: Any]? {
            guard let data = try? Data(contentsOf: meta), let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            if !hash.isEmpty && (json["sha256"] as? String)?.lowercased() == hash.lowercased() { return json }
            return json["url"] as? String == effectiveUrl ? json : nil
        }
        var lastError: Error = ModError.message("下载失败，请检查网络与可用空间")
        for attempt in 0..<8 {
            try checkCancelled()
            let saved = metadata(), etag = saved?["etag"] as? String
            if saved?["segmented"] as? Bool == true {
                try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
            }
            if (saved == nil && (ModFiles.exists(meta) || hash.isEmpty)) || (hash.isEmpty && !ModDownloadTransfer.strong(etag)) {
                try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
            }
            let length = ModFiles.fileSize(part)
            let expected = resolvedRelease.expectedSize > 0 ? resolvedRelease.expectedSize : ((saved?["totalBytes"] as? NSNumber)?.int64Value ?? -1)
            if expected > 0 && length == expected {
                if hash.isEmpty { return part }
                if try fileHash(part) == hash.lowercased() { return part }
            }
            if expected > 0 && length >= expected { try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta) }
            do {
                let transfer = ModDownloadTransfer(part: part, release: resolvedRelease, saved: metadata(), cancelled: { self.isCancelled() }, progress: progress)
                var activeTask: URLSessionTask?
                defer { if let activeTask { self.unregisterTask(activeTask) } }
                try transfer.run { task in activeTask = task; self.registerTask(task) }
                try checkCancelled()
                let downloaded = ModFiles.fileSize(part)
                if resolvedRelease.expectedSize > 0 && downloaded != resolvedRelease.expectedSize { throw ModError.message("下载文件大小不匹配") }
                let hashMatches = hash.isEmpty ? true : try fileHash(part) == hash.lowercased()
                if !hashMatches {
                    try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
                    throw ModError.message("SHA-256 校验失败")
                }
                return part
            } catch {
                try checkCancelled(); lastError = error
                if attempt < 7 { for _ in 0..<(min(attempt + 1, 5) * 5) { try checkCancelled(); Thread.sleep(forTimeInterval: 0.2) } }
            }
        }
        throw lastError
    }

    private func downloadSegmented(release: ModReleaseEntry,
                                   part: URL,
                                   meta: URL,
                                   savedMeta: [String: Any]?,
                                   existingSegments: [DownloadSegment]?,
                                   knownTotal: Int64,
                                   maxConnections: Int,
                                   minSegmentedBytes: Int64,
                                   externalQueue: OperationQueue?,
                                   externalGroup: DispatchGroup?,
                                   progress: @escaping (Int64, Int64) -> Void,
                                   asyncCompletion: ((Result<URL, Error>) -> Void)?) throws -> URL? {
        let hash = release.expectedSha256 ?? ""
        let hashless = hash.isEmpty
        var saved = savedMeta
        if (saved == nil && (ModFiles.exists(meta) || hashless)) ||
            (hashless && ModFiles.exists(part) && !ModDownloadTransfer.strong(saved?["etag"] as? String)) {
            try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
            saved = nil
        }

        let alreadySegmented = existingSegments != nil && saved != nil
        let segments: [DownloadSegment]
        let startedWithPartial: Bool
        if let existingSegments, alreadySegmented {
            segments = existingSegments
            startedWithPartial = true
            if segments.allSatisfy(\.isComplete) && ModFiles.fileSize(part) == knownTotal {
                if try hashless || fileHash(part) == hash.lowercased() {
                    try writeSegmentMeta(meta: meta, url: release.downloadUrl, etag: saved?["etag"] as? String, total: knownTotal, sha256: hash, segmented: false, segments: [])
                    progress(knownTotal, knownTotal)
                    return part
                }
                try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
                return try downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
            }
        } else {
            var curLen = ModFiles.fileSize(part)
            if knownTotal > 0 && curLen > knownTotal {
                try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
                curLen = 0; saved = nil
            }
            if knownTotal > 0 && curLen == knownTotal {
                if try hashless || fileHash(part) == hash.lowercased() {
                    progress(curLen, knownTotal)
                    return part
                }
                try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
                curLen = 0; saved = nil
            }
            let remaining = knownTotal - curLen
            let segCount = Self.computeSegmentCount(remainingBytes: remaining, maxConnections: maxConnections, minSegmentedBytes: minSegmentedBytes)
            if segCount < 2 {
                return try downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
            }
            var built: [DownloadSegment] = []
            if curLen > 0 { built.append(DownloadSegment(start: 0, end: curLen - 1, done: curLen)) }
            let base = remaining / Int64(segCount)
            let rem = remaining % Int64(segCount)
            var cursor = curLen
            for i in 0..<segCount {
                let len = base + (Int64(i) < rem ? 1 : 0)
                if len <= 0 { continue }
                built.append(DownloadSegment(start: cursor, end: cursor + len - 1, done: 0))
                cursor += len
            }
            segments = built
            startedWithPartial = curLen > 0
        }

        let unfinished = segments.filter { !$0.isComplete }
        if unfinished.isEmpty {
            return try downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
        }
        let needBytes = unfinished.reduce(Int64(0)) { $0 + ($1.length - $1.done) }
        if needBytes > 0,
           let free = try ModFiles.files.attributesOfFileSystem(forPath: part.deletingLastPathComponent().path)[.systemFreeSize] as? NSNumber,
           needBytes > free.int64Value - 64 * 1048576 {
            throw ModError.message("存储空间不足")
        }

        let stateLock = NSLock()
        var activeEtag = saved?["etag"] as? String
        var hasCurrentUrlEtag = (saved?["url"] as? String) == release.downloadUrl && ModDownloadTransfer.strong(activeEtag)
        var fileDoneBytes = segments.reduce(Int64(0)) { $0 + $1.done }
        var bytesSinceMeta: Int64 = 0
        var lastProgressEmit = Date.distantPast
        var fileAborted = false
        var needsCleanRestart = false
        var firstError: Error?
        var activeSubTasks = 1
        var fileTasks: [ObjectIdentifier: URLSessionTask] = [:]
        var sharedFd: Int32 = -1

        func isFileStopped() -> Bool {
            if self.isCancelled() { return true }
            stateLock.lock(); defer { stateLock.unlock() }
            return fileAborted
        }
        func abortFile() {
            stateLock.lock()
            fileAborted = true
            let tasks = Array(fileTasks.values)
            fileTasks.removeAll()
            stateLock.unlock()
            for task in tasks { task.cancel() }
        }
        func registerFileTask(_ task: URLSessionTask) {
            stateLock.lock()
            let stop = fileAborted
            if !stop { fileTasks[ObjectIdentifier(task)] = task }
            stateLock.unlock()
            if stop { task.cancel(); return }
            self.registerTask(task)
        }
        func unregisterFileTask(_ task: URLSessionTask) {
            stateLock.lock(); fileTasks.removeValue(forKey: ObjectIdentifier(task)); stateLock.unlock()
            self.unregisterTask(task)
        }
        func snapshotMeta(segmented: Bool) {
            stateLock.lock()
            let etag = activeEtag
            let segSnapshot = segments.map { DownloadSegment(start: $0.start, end: $0.end, done: $0.done) }
            stateLock.unlock()
            try? self.writeSegmentMeta(meta: meta, url: release.downloadUrl, etag: etag, total: knownTotal, sha256: hash, segmented: segmented, segments: segmented ? segSnapshot : [])
        }
        func ensureOpenFd() throws -> Int32 {
            stateLock.lock(); defer { stateLock.unlock() }
            if sharedFd >= 0 { return sharedFd }
            let fd = Darwin.open(part.path, O_RDWR | O_CREAT, 0o644)
            guard fd >= 0 else { throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno)) }
            sharedFd = fd
            return fd
        }
        func closeFd(sync: Bool) {
            stateLock.lock()
            let fd = sharedFd
            sharedFd = -1
            stateLock.unlock()
            if fd >= 0 {
                if sync { _ = Darwin.fsync(fd) }
                Darwin.close(fd)
            }
        }
        func recordDelta(_ delta: Int64) {
            var emitDone: Int64?
            stateLock.lock()
            fileDoneBytes += delta
            bytesSinceMeta += delta
            let now = Date()
            if now.timeIntervalSince(lastProgressEmit) >= 0.08 {
                lastProgressEmit = now
                emitDone = min(fileDoneBytes, knownTotal)
            }
            let shouldPersist = bytesSinceMeta >= Self.segmentMetaPersistBytes
            if shouldPersist { bytesSinceMeta = 0 }
            stateLock.unlock()
            if shouldPersist { snapshotMeta(segmented: true) }
            if let emitDone { progress(emitDone, knownTotal) }
        }

        let localGroup: DispatchGroup?
        let targetQueue: OperationQueue?
        let targetGroup: DispatchGroup?
        if let externalQueue, let externalGroup {
            localGroup = nil
            targetQueue = externalQueue; targetGroup = externalGroup
        } else if maxConnections > 1 {
            let q = OperationQueue()
            q.maxConcurrentOperationCount = maxConnections
            localGroup = DispatchGroup()
            targetQueue = q; targetGroup = localGroup
        } else {
            localGroup = nil
            targetQueue = nil; targetGroup = nil
        }

        var syncResult: Result<URL, Error>?
        let finishFile: (Result<URL, Error>) -> Void = { result in
            if let asyncCompletion {
                asyncCompletion(result)
            } else {
                syncResult = result
            }
        }

        func runSegmentWithRetries(_ seg: DownloadSegment) throws {
            var lastErr: Error = ModError.message("下载失败，请检查网络与可用空间")
            for attempt in 0..<8 {
                if isFileStopped() { throw CancellationError() }
                if seg.isComplete { return }
                let fd = try ensureOpenFd()
                stateLock.lock()
                let reqStart = seg.start + seg.done
                let reqEnd = seg.end
                let etag = activeEtag
                let sendIfRange = hasCurrentUrlEtag && ModDownloadTransfer.strong(etag)
                stateLock.unlock()
                let transfer = ModSegmentTransfer(
                    urlString: release.downloadUrl,
                    fd: fd,
                    reqStart: reqStart,
                    reqEnd: reqEnd,
                    expectedTotal: knownTotal,
                    ifRange: sendIfRange ? etag : nil,
                    expectedEtag: hashless ? etag : nil,
                    cancelled: isFileStopped,
                    onBytes: { count in
                        stateLock.lock(); seg.done += count; stateLock.unlock()
                        recordDelta(count)
                    }
                )
                var taskRef: URLSessionTask?
                do {
                    let outcome = try transfer.run { task in taskRef = task; registerFileTask(task) }
                    if let taskRef { unregisterFileTask(taskRef) }
                    switch outcome {
                    case .partialComplete(let respEtag):
                        if let respEtag, !respEtag.isEmpty {
                            stateLock.lock()
                            if !hasCurrentUrlEtag {
                                activeEtag = respEtag
                                if ModDownloadTransfer.strong(respEtag) { hasCurrentUrlEtag = true }
                            }
                            stateLock.unlock()
                        }
                        snapshotMeta(segmented: true)
                        return
                    case .restartFull:
                        stateLock.lock(); needsCleanRestart = true; stateLock.unlock()
                        abortFile()
                        return
                    }
                } catch {
                    if let taskRef { unregisterFileTask(taskRef) }
                    snapshotMeta(segmented: true)
                    if isFileStopped() { throw CancellationError() }
                    lastErr = error
                    if attempt < 7 {
                        for _ in 0..<(min(attempt + 1, 5) * 5) {
                            if isFileStopped() { throw CancellationError() }
                            Thread.sleep(forTimeInterval: 0.2)
                        }
                    }
                }
            }
            if !seg.isComplete { throw lastErr }
        }

        func onTaskDone(_ error: Error?) {
            stateLock.lock()
            if let error, !needsCleanRestart, firstError == nil {
                firstError = error
            }
            let shouldAbort = error != nil && !needsCleanRestart
            activeSubTasks -= 1
            let remainingTasks = activeSubTasks
            let restart = needsCleanRestart
            let recordedErr = firstError
            stateLock.unlock()

            if shouldAbort { abortFile() }
            if remainingTasks != 0 { return }

            closeFd(sync: recordedErr == nil && !restart && !self.isCancelled())
            if self.isCancelled() {
                snapshotMeta(segmented: true)
                finishFile(.failure(CancellationError()))
                return
            }
            if restart {
                try? ModFiles.removeIfPresent(part); try? ModFiles.removeIfPresent(meta)
                do {
                    let url = try self.downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
                    finishFile(.success(url))
                } catch {
                    finishFile(.failure(error))
                }
                return
            }
            if let recordedErr {
                snapshotMeta(segmented: true)
                finishFile(.failure(recordedErr))
                return
            }
            do {
                guard segments.allSatisfy(\.isComplete), ModFiles.fileSize(part) == knownTotal else {
                    throw ModError.message("下载文件大小不匹配")
                }
                if !hashless {
                    let actual = try self.fileHash(part)
                    if actual != hash.lowercased() {
                        try? ModFiles.removeIfPresent(part); try? ModFiles.removeIfPresent(meta)
                        if startedWithPartial {
                            let url = try self.downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
                            finishFile(.success(url))
                            return
                        }
                        throw ModError.message("SHA-256 校验失败")
                    }
                }
                snapshotMeta(segmented: false)
                progress(knownTotal, knownTotal)
                finishFile(.success(part))
            } catch {
                finishFile(.failure(error))
            }
        }

        func spawnRemaining(_ rest: [DownloadSegment]) {
            for seg in rest {
                stateLock.lock(); activeSubTasks += 1; stateLock.unlock()
                let work = {
                    var err: Error?
                    do { try runSegmentWithRetries(seg) } catch { err = error }
                    onTaskDone(err)
                }
                if let targetQueue, let targetGroup {
                    targetGroup.enter()
                    targetQueue.addOperation {
                        defer { targetGroup.leave() }
                        work()
                    }
                } else {
                    work()
                }
            }
        }

        let probeSeg = unfinished[0]
        let restSegs = Array(unfinished.dropFirst())

        if alreadySegmented {
            // Ensure segmented meta & open fd ready before spawning all unfinished segments
            snapshotMeta(segmented: true)
            _ = try ensureOpenFd()
            spawnRemaining(restSegs)
            var err: Error?
            do { try runSegmentWithRetries(probeSeg) } catch { err = error }
            onTaskDone(err)
            if let localGroup { localGroup.wait() }
            if asyncCompletion != nil { return nil }
            switch syncResult {
            case .success(let url): return url
            case .failure(let error): throw error
            case nil: throw ModError.message("下载失败，请检查网络与可用空间")
            }
        }

        // Probe first unfinished segment before committing segmented mode!
        let probeStart = probeSeg.start + probeSeg.done
        let probeEnd = probeSeg.end
        let probeIfRange = (startedWithPartial && hasCurrentUrlEtag) ? activeEtag : nil
        var spawnedSiblings = false
        let probe = ModProbeSegmentTransfer(
            urlString: release.downloadUrl,
            part: part,
            reqStart: probeStart,
            reqEnd: probeEnd,
            expectedTotal: knownTotal,
            ifRange: probeIfRange,
            hashless: hashless,
            resumeEtag: startedWithPartial ? activeEtag : nil,
            cancelled: isFileStopped,
            onPartialConfirmed: { respEtag in
                stateLock.lock()
                if let respEtag, !respEtag.isEmpty { activeEtag = respEtag }
                if ModDownloadTransfer.strong(respEtag) { hasCurrentUrlEtag = true }
                stateLock.unlock()
                // Write segmented meta atomically BEFORE opening/writing part!
                snapshotMeta(segmented: true)
                let fd = try ensureOpenFd()
                spawnedSiblings = true
                spawnRemaining(restSegs)
                return fd
            },
            onPartialBytes: { count in
                stateLock.lock(); probeSeg.done += count; stateLock.unlock()
                recordDelta(count)
            },
            onFull200Confirmed: { respEtag, total in
                try? self.writeSegmentMeta(meta: meta, url: release.downloadUrl, etag: respEtag, total: total, sha256: hash, segmented: false, segments: [])
            },
            onFull200Progress: { done, total in
                progress(done, total)
            }
        )
        var probeTaskRef: URLSessionTask?
        do {
            let outcome = try probe.run { task in probeTaskRef = task; registerFileTask(task) }
            if let probeTaskRef { unregisterFileTask(probeTaskRef) }
            switch outcome {
            case .full200Complete(let written, let respTotal):
                let expected = release.expectedSize > 0 ? release.expectedSize : respTotal
                if expected > 0 && written != expected {
                    return try downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
                }
                if try !hashless && fileHash(part) != hash.lowercased() {
                    try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(meta)
                    throw ModError.message("SHA-256 校验失败")
                }
                progress(written, expected > 0 ? expected : written)
                return part
            case .fallbackSingleStream:
                return try downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
            case .partialComplete:
                snapshotMeta(segmented: true)
                onTaskDone(nil)
            }
        } catch {
            if let probeTaskRef { unregisterFileTask(probeTaskRef) }
            if spawnedSiblings {
                // Retry remaining bytes of probeSeg before exiting task
                var retryErr: Error?
                if !isFileStopped() && !probeSeg.isComplete {
                    do { try runSegmentWithRetries(probeSeg) } catch { retryErr = error }
                } else {
                    retryErr = error
                }
                onTaskDone(retryErr)
            } else {
                closeFd(sync: false)
                if isCancelled() { throw CancellationError() }
                // Probe failed before starting segmented mode -> fall back cleanly to single-stream retry loop
                return try downloadSingleStream(release: release, part: part, meta: meta, progress: progress)
            }
        }

        if let localGroup { localGroup.wait() }
        if asyncCompletion != nil { return nil }
        switch syncResult {
        case .success(let url): return url
        case .failure(let error): throw error
        case nil: throw ModError.message("下载失败，请检查网络与可用空间")
        }
    }

    private func writeSegmentMeta(meta: URL, url: String, etag: String?, total: Int64, sha256: String,
                                  segmented: Bool, segments: [DownloadSegment]) throws {
        var json: [String: Any] = ["url": url, "totalBytes": total]
        if let etag, !etag.isEmpty { json["etag"] = etag }
        if !sha256.isEmpty { json["sha256"] = sha256 }
        if segmented && !segments.isEmpty {
            json["segmented"] = true
            json["segments"] = segments.map { [$0.start, $0.end, $0.done] }
        }
        try JSONSerialization.data(withJSONObject: json).write(to: meta, options: .atomic)
    }
    private func fileHash(_ file: URL) throws -> String {
        let input = try FileHandle(forReadingFrom: file)
        defer { try? input.close() }
        var hash = SHA256()
        while true {
            try checkCancelled()
            let data = try input.read(upToCount: 1048576) ?? Data()
            if data.isEmpty { break }; hash.update(data: data)
        }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }
}

private final class ModDownloadTransfer: NSObject, URLSessionDataDelegate {
    let part: URL
    let release: ModReleaseEntry
    let saved: [String: Any]?
    let cancelled: () -> Bool
    let progress: (Int64, Int64) -> Void
    let done = DispatchSemaphore(value: 0)
    var handle: FileHandle?
    var error: Error?
    var received: Int64 = 0
    var total: Int64 = -1
    var rangeHeader: String?
    var ifRangeHeader: String?
    init(part: URL, release: ModReleaseEntry, saved: [String: Any]?, cancelled: @escaping () -> Bool, progress: @escaping (Int64, Int64) -> Void) {
        self.part = part; self.release = release; self.saved = saved; self.cancelled = cancelled; self.progress = progress
    }
    static func strong(_ etag: String?) -> Bool { etag.map { !$0.isEmpty && !$0.trimmingCharacters(in: .whitespaces).lowercased().hasPrefix("w/") } ?? false }
    func run(register: (URLSessionTask) -> Void) throws {
        guard let url = URL(string: release.downloadUrl), ["http", "https"].contains(url.scheme) else { throw ModError.message("无效下载地址") }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 60)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        received = ModFiles.fileSize(part)
        if received > 0 {
            let range = "bytes=\(received)-"
            rangeHeader = range
            request.setValue(range, forHTTPHeaderField: "Range")
            let sameUrl = (saved?["url"] as? String) == release.downloadUrl
            if sameUrl, let etag = saved?["etag"] as? String, Self.strong(etag) {
                ifRangeHeader = etag
                request.setValue(etag, forHTTPHeaderField: "If-Range")
            }
        }
        let session = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let task = session.dataTask(with: request)
        register(task); task.resume(); done.wait()
        if let error { throw error }
        if total > 0 && received != total { throw ModError.message("下载文件大小不匹配") }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard !cancelled(), let scheme = request.url?.scheme?.lowercased(), ["http", "https"].contains(scheme) else {
            completionHandler(nil)
            return
        }
        var redirected = request
        redirected.cachePolicy = .reloadIgnoringLocalCacheData
        redirected.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        redirected.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        if let rangeHeader { redirected.setValue(rangeHeader, forHTTPHeaderField: "Range") }
        if let ifRangeHeader { redirected.setValue(ifRangeHeader, forHTTPHeaderField: "If-Range") }
        completionHandler(redirected)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        do {
            guard !cancelled(), let response = response as? HTTPURLResponse else { throw CancellationError() }
            let etag = response.value(forHTTPHeaderField: "ETag")
            if response.statusCode == 206 {
                let range = response.value(forHTTPHeaderField: "Content-Range") ?? ""
                guard let start = range.modMatch(#"^bytes (\d+)-\d+/\d+$"#), Int64(start) == received,
                      !(release.expectedSha256 ?? "").isEmpty || (received > 0 && Self.strong(etag) && etag == saved?["etag"] as? String) else {
                    try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(part.appendingPathExtension("meta")); throw ModError.message("下载续传校验失败")
                }
                total = Int64(range.components(separatedBy: "/").last ?? "") ?? -1
            } else if response.statusCode == 200 {
                received = 0; total = response.expectedContentLength
            } else {
                if response.statusCode == 416 { try ModFiles.removeIfPresent(part); try ModFiles.removeIfPresent(part.appendingPathExtension("meta")) }
                throw ModError.message("下载失败 HTTP \(response.statusCode)")
            }
            if release.expectedSize >= 0 && total >= 0 && total != release.expectedSize {
                throw ModError.message("下载文件大小不匹配")
            }
            if total > received,
               let free = try ModFiles.files.attributesOfFileSystem(forPath: part.deletingLastPathComponent().path)[.systemFreeSize] as? NSNumber,
               total - received > free.int64Value - 64 * 1048576 { throw ModError.message("存储空间不足") }
            if !ModFiles.exists(part) { guard ModFiles.files.createFile(atPath: part.path, contents: nil) else { throw ModError.message("下载失败，请检查网络与可用空间") } }
            handle = try FileHandle(forWritingTo: part)
            if received == 0 { try handle!.truncate(atOffset: 0) }
            try handle!.seekToEnd()
            var json: [String: Any] = ["url": release.downloadUrl, "totalBytes": total]
            if let etag { json["etag"] = etag }
            if let sha = release.expectedSha256 { json["sha256"] = sha }
            try JSONSerialization.data(withJSONObject: json).write(to: part.appendingPathExtension("meta"), options: .atomic)
            completionHandler(.allow)
        } catch { self.error = error; completionHandler(.cancel) }
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        do {
            if cancelled() { throw CancellationError() }
            if release.expectedSize >= 0 && Int64(data.count) > release.expectedSize - received {
                throw ModError.message("下载文件大小不匹配")
            }
            try handle?.write(contentsOf: data); received += Int64(data.count); progress(received, total)
        } catch { self.error = error; dataTask.cancel() }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        do { try handle?.synchronize(); try handle?.close() } catch { self.error = self.error ?? error }
        self.error = self.error ?? error
        done.signal()
    }
}

private final class DownloadSegment {
    let start: Int64
    let end: Int64
    var done: Int64
    var length: Int64 { end - start + 1 }
    var isComplete: Bool { done >= length }
    init(start: Int64, end: Int64, done: Int64) {
        self.start = start; self.end = end; self.done = done
    }
}

private func parseContentRangeTriple(_ header: String) -> (Int64, Int64, Int64)? {
    let trimmed = header.trimmingCharacters(in: .whitespacesAndNewlines)
    guard trimmed.lowercased().hasPrefix("bytes ") else { return nil }
    let body = String(trimmed.dropFirst(6))
    let slashParts = body.split(separator: "/", maxSplits: 1)
    guard slashParts.count == 2 else { return nil }
    let dashParts = slashParts[0].split(separator: "-", maxSplits: 1)
    guard dashParts.count == 2,
          let start = Int64(dashParts[0]),
          let end = Int64(dashParts[1]) else { return nil }
    let total = Int64(slashParts[1]) ?? -1
    return (start, end, total)
}

private func pwriteAll(fd: Int32, data: Data, offset: Int64) throws {
    if data.isEmpty { return }
    try data.withUnsafeBytes { rawBuf in
        guard let base = rawBuf.baseAddress else { return }
        var written = 0
        while written < data.count {
            let n = Darwin.pwrite(fd, base.advanced(by: written), data.count - written, off_t(offset + Int64(written)))
            if n < 0 {
                if errno == EINTR { continue }
                throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno))
            }
            if n == 0 {
                throw ModError.message("写入文件失败")
            }
            written += n
        }
    }
}

private final class ModSegmentTransfer: NSObject, URLSessionDataDelegate {
    enum Outcome {
        case partialComplete(etag: String?)
        case restartFull
    }

    let urlString: String
    let fd: Int32
    let reqStart: Int64
    let reqEnd: Int64
    let expectedTotal: Int64
    let ifRange: String?
    let expectedEtag: String?
    let cancelled: () -> Bool
    let onBytes: (Int64) -> Void
    let done = DispatchSemaphore(value: 0)
    var writePos: Int64
    var remaining: Int64
    var respEtag: String?
    var outcome: Outcome? = nil
    var error: Error?
    var rangeHeader: String

    init(urlString: String, fd: Int32, reqStart: Int64, reqEnd: Int64, expectedTotal: Int64,
         ifRange: String?, expectedEtag: String?, cancelled: @escaping () -> Bool,
         onBytes: @escaping (Int64) -> Void) {
        self.urlString = urlString
        self.fd = fd
        self.reqStart = reqStart
        self.reqEnd = reqEnd
        self.expectedTotal = expectedTotal
        self.ifRange = ifRange
        self.expectedEtag = expectedEtag
        self.cancelled = cancelled
        self.onBytes = onBytes
        self.writePos = reqStart
        self.remaining = reqEnd - reqStart + 1
        self.rangeHeader = "bytes=\(reqStart)-\(reqEnd)"
    }

    func run(register: (URLSessionTask) -> Void) throws -> Outcome {
        guard let url = URL(string: urlString), ["http", "https"].contains(url.scheme) else {
            throw ModError.message("无效下载地址")
        }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 60)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        request.setValue(rangeHeader, forHTTPHeaderField: "Range")
        if let ifRange { request.setValue(ifRange, forHTTPHeaderField: "If-Range") }
        let session = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let task = session.dataTask(with: request)
        register(task); task.resume(); done.wait()
        if let error { throw error }
        if case .restartFull = outcome { return .restartFull }
        guard case .partialComplete(let etag) = outcome, remaining == 0 else {
            throw ModError.message("下载分片提前结束")
        }
        return .partialComplete(etag: etag)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard !cancelled(), let scheme = request.url?.scheme?.lowercased(), ["http", "https"].contains(scheme) else {
            completionHandler(nil); return
        }
        var redirected = request
        redirected.cachePolicy = .reloadIgnoringLocalCacheData
        redirected.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        redirected.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        redirected.setValue(rangeHeader, forHTTPHeaderField: "Range")
        if let ifRange { redirected.setValue(ifRange, forHTTPHeaderField: "If-Range") }
        completionHandler(redirected)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard !cancelled(), let http = response as? HTTPURLResponse else {
            self.error = CancellationError(); completionHandler(.cancel); return
        }
        let etag = http.value(forHTTPHeaderField: "ETag")
        if http.statusCode == 206 {
            let cr = http.value(forHTTPHeaderField: "Content-Range") ?? ""
            guard let (s, e, t) = parseContentRangeTriple(cr),
                  s == reqStart, e == reqEnd, t <= 0 || t == expectedTotal else {
                self.outcome = .restartFull; completionHandler(.cancel); return
            }
            if let expectedEtag, !(ModDownloadTransfer.strong(etag) && etag == expectedEtag) {
                self.outcome = .restartFull; completionHandler(.cancel); return
            }
            self.respEtag = etag
            self.outcome = .partialComplete(etag: etag)
            completionHandler(.allow)
        } else if http.statusCode == 200 || http.statusCode == 416 {
            self.outcome = .restartFull
            completionHandler(.cancel)
        } else {
            self.error = ModError.message("下载失败 HTTP \(http.statusCode)")
            completionHandler(.cancel)
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        do {
            if cancelled() { throw CancellationError() }
            let count = Int64(data.count)
            guard count <= remaining else { throw ModError.message("下载分片大小不匹配") }
            try pwriteAll(fd: fd, data: data, offset: writePos)
            writePos += count
            remaining -= count
            onBytes(count)
        } catch {
            self.error = error; dataTask.cancel()
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if self.error == nil, case .restartFull = outcome {
            // Intentional cancel for full restart
        } else if self.error == nil {
            self.error = error
        }
        done.signal()
    }
}

private final class ModProbeSegmentTransfer: NSObject, URLSessionDataDelegate {
    enum Outcome {
        case partialComplete
        case full200Complete(written: Int64, total: Int64)
        case fallbackSingleStream
    }

    let urlString: String
    let part: URL
    let reqStart: Int64
    let reqEnd: Int64
    let expectedTotal: Int64
    let ifRange: String?
    let hashless: Bool
    let resumeEtag: String?
    let cancelled: () -> Bool
    let onPartialConfirmed: (String?) throws -> Int32
    let onPartialBytes: (Int64) -> Void
    let onFull200Confirmed: (String?, Int64) -> Void
    let onFull200Progress: (Int64, Int64) -> Void
    let done = DispatchSemaphore(value: 0)
    let rangeHeader: String

    var mode: Int = 0 // 0 = undecided, 1 = partial, 2 = full200, 3 = fallback
    var fd: Int32 = -1
    var writePos: Int64
    var remaining: Int64
    var fullHandle: FileHandle?
    var fullWritten: Int64 = 0
    var fullTotal: Int64 = -1
    var error: Error?

    init(urlString: String, part: URL, reqStart: Int64, reqEnd: Int64, expectedTotal: Int64,
         ifRange: String?, hashless: Bool, resumeEtag: String?, cancelled: @escaping () -> Bool,
         onPartialConfirmed: @escaping (String?) throws -> Int32,
         onPartialBytes: @escaping (Int64) -> Void,
         onFull200Confirmed: @escaping (String?, Int64) -> Void,
         onFull200Progress: @escaping (Int64, Int64) -> Void) {
        self.urlString = urlString
        self.part = part
        self.reqStart = reqStart
        self.reqEnd = reqEnd
        self.expectedTotal = expectedTotal
        self.ifRange = ifRange
        self.hashless = hashless
        self.resumeEtag = resumeEtag
        self.cancelled = cancelled
        self.onPartialConfirmed = onPartialConfirmed
        self.onPartialBytes = onPartialBytes
        self.onFull200Confirmed = onFull200Confirmed
        self.onFull200Progress = onFull200Progress
        self.writePos = reqStart
        self.remaining = reqEnd - reqStart + 1
        self.rangeHeader = "bytes=\(reqStart)-\(reqEnd)"
    }

    func run(register: (URLSessionTask) -> Void) throws -> Outcome {
        guard let url = URL(string: urlString), ["http", "https"].contains(url.scheme) else {
            throw ModError.message("无效下载地址")
        }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 60)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        request.setValue(rangeHeader, forHTTPHeaderField: "Range")
        if let ifRange { request.setValue(ifRange, forHTTPHeaderField: "If-Range") }
        let session = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let task = session.dataTask(with: request)
        register(task); task.resume(); done.wait()
        if mode == 3 { return .fallbackSingleStream }
        if let error { throw error }
        if mode == 2 { return .full200Complete(written: fullWritten, total: fullTotal) }
        guard mode == 1, remaining == 0 else { throw ModError.message("下载分片提前结束") }
        return .partialComplete
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard !cancelled(), let scheme = request.url?.scheme?.lowercased(), ["http", "https"].contains(scheme) else {
            completionHandler(nil); return
        }
        var redirected = request
        redirected.cachePolicy = .reloadIgnoringLocalCacheData
        redirected.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        redirected.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        redirected.setValue(rangeHeader, forHTTPHeaderField: "Range")
        if let ifRange { redirected.setValue(ifRange, forHTTPHeaderField: "If-Range") }
        completionHandler(redirected)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        do {
            guard !cancelled(), let http = response as? HTTPURLResponse else { throw CancellationError() }
            let etag = http.value(forHTTPHeaderField: "ETag")
            if http.statusCode == 206 {
                let cr = http.value(forHTTPHeaderField: "Content-Range") ?? ""
                let parsed = parseContentRangeTriple(cr)
                let validRange = parsed.map { $0.0 == reqStart && $0.1 == reqEnd && ($0.2 <= 0 || $0.2 == expectedTotal) } ?? false
                let hashlessSafe = !hashless || (ModDownloadTransfer.strong(etag) && (resumeEtag == nil || etag == resumeEtag))
                if validRange && hashlessSafe {
                    fd = try onPartialConfirmed(etag)
                    mode = 1
                    completionHandler(.allow)
                } else {
                    mode = 3
                    completionHandler(.cancel)
                }
            } else if http.statusCode == 200 {
                fullTotal = http.expectedContentLength > 0 ? http.expectedContentLength : expectedTotal
                if expectedTotal > 0 && http.expectedContentLength > 0 && http.expectedContentLength != expectedTotal {
                    throw ModError.message("下载文件大小不匹配")
                }
                if !ModFiles.exists(part) {
                    guard ModFiles.files.createFile(atPath: part.path, contents: nil) else {
                        throw ModError.message("下载失败，请检查网络与可用空间")
                    }
                }
                fullHandle = try FileHandle(forWritingTo: part)
                try fullHandle!.truncate(atOffset: 0)
                onFull200Confirmed(etag, fullTotal)
                mode = 2
                completionHandler(.allow)
            } else if http.statusCode == 416 {
                try? ModFiles.removeIfPresent(part)
                try? ModFiles.removeIfPresent(part.appendingPathExtension("meta"))
                mode = 3
                completionHandler(.cancel)
            } else {
                throw ModError.message("下载失败 HTTP \(http.statusCode)")
            }
        } catch {
            self.error = error
            completionHandler(.cancel)
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        do {
            if cancelled() { throw CancellationError() }
            if mode == 1 {
                let count = Int64(data.count)
                guard count <= remaining else { throw ModError.message("下载分片大小不匹配") }
                try pwriteAll(fd: fd, data: data, offset: writePos)
                writePos += count
                remaining -= count
                onPartialBytes(count)
            } else if mode == 2 {
                if expectedTotal > 0 && Int64(data.count) > expectedTotal - fullWritten {
                    throw ModError.message("下载文件大小不匹配")
                }
                try fullHandle?.write(contentsOf: data)
                fullWritten += Int64(data.count)
                onFull200Progress(fullWritten, fullTotal)
            }
        } catch {
            self.error = error; dataTask.cancel()
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if mode == 2 {
            do { try fullHandle?.synchronize(); try fullHandle?.close() } catch { self.error = self.error ?? error }
        }
        if mode != 3 && self.error == nil {
            self.error = error
        }
        done.signal()
    }
}
