import Foundation
import Combine

struct UncensoredGameDescriptor: Equatable {
    let id: String
    let title: String

    var cdnBaseURL: String { AliceHubCDN.gameBaseURLString(for: id) }
    var manifestURL: URL { URL(string: cdnBaseURL + "/files/uncensored/manifest.json")! }

    static let supported: [Self] = [
        Self(id: "rance01", title: "兰斯01 寻找小光"),
        Self(id: "rance02", title: "兰斯02 反叛的少女们"),
        Self(id: "rance03", title: "兰斯03 利萨斯陷落"),
        Self(id: "rance5d", title: "兰斯5D"),
        Self(id: "rance06", title: "兰斯06 赛斯崩坏"),
        Self(id: "sengokurance", title: "战国兰斯"),
        Self(id: "rance08", title: "兰斯08 兰斯Quest Magnum"),
        Self(id: "rance09", title: "兰斯09 赫尔曼革命"),
        Self(id: "rance10", title: "兰斯10 决战")
    ]

    static func descriptor(for id: String) -> Self? {
        supported.first { $0.id == id }
    }

    static func classifyDirectory(_ directory: URL) -> Self? {
        let names = Set(ModFiles.children(directory).filter(ModFiles.isFile).map { $0.lastPathComponent.lowercased() })
        if names.contains("rance6after.ain") || names.contains("rance1.ain") { return nil }
        if names.contains("rance10.ain") { return descriptor(for: "rance10") }
        if names.contains("rance9.ain") || names.contains("rance09.ain") { return descriptor(for: "rance09") }
        if names.contains("rance03.ain") { return descriptor(for: "rance03") }
        if names.contains("rance01.ain") { return descriptor(for: "rance01") }
        if names.contains("rance6.ain") || names.contains("rance06.ain") || names.contains("rance6.chs") ||
            names.contains("rance6cg.afa") || names.contains("rance6ga.ald") || names.contains("rance6dll.inc") {
            return descriptor(for: "rance06")
        }
        if names.contains("r5d_sa.ald") { return descriptor(for: "rance5d") }
        if names.contains("rance02.ain") || names.contains("ランス０２.ain") || names.contains("ランス０２ga.ald") ||
            names.contains("儔儞僗俷俲.ain") || names.contains("儔儞僗俷俲ga.ald") {
            return descriptor(for: "rance02")
        }
        if names.contains("rance7.ain") || names.contains("sengokurance.ain") ||
            names.contains("愴崙儔儞僗.ain") || names.contains("愴崙儔儞僗ga.ald") ||
            names.contains("戦国ランス.ain") || names.contains("戦国ランスga.ald") ||
            names.contains("战国兰斯.ain") || names.contains("战国兰斯ga.ald") {
            return descriptor(for: "sengokurance")
        }
        if names.contains("rance08.ain") || names.contains("rance8.ain") || names.contains("rancequest.ain") ||
            names.contains("ランス・クエスト.ain") || names.contains("ランス・クエストcg.afa") ||
            names.contains("儔儞僗丒僋僄僗僩.ain") || names.contains("儔儞僗丒僋僄僗僩cg.afa") {
            return descriptor(for: "rance08")
        }

        let game = Game(url: directory)
        if let key = game.artworkKey, let match = descriptor(for: key) {
            return match
        }
        return nil
    }
}

struct UncensoredPatchManifest: Equatable {
    let version: String
    let gameId: String
    let title: String
    let summary: String
    let fileName: String
    let key: String
    let size: Int64
    let sha256: String
    let cgCount: Int
    let updatedAt: String
    let downloadUrl: String

    static let maxManifestBytes = 1024 * 1024
    static let managedOverlayNames: Set<String> = [
        "alicehubuncensoredcg.afa",
        "alicehubuncensoredcg.ald"
    ]

    static func isManagedOverlayFileName(_ name: String) -> Bool {
        managedOverlayNames.contains(name.lowercased())
    }

    static func isSafeOverlayFileName(_ name: String) -> Bool {
        guard !name.isEmpty, !name.hasPrefix("."),
              !name.contains("/"), !name.contains("\\"),
              !name.contains(".."), !name.contains(":"),
              !name.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else {
            return false
        }
        let lower = name.lowercased()
        guard !lower.hasSuffix(".vanilla.bak"), !lower.hasSuffix(".part"), !lower.hasSuffix(".tmp") else {
            return false
        }
        guard lower.hasSuffix(".afa") || lower.hasSuffix(".ald") else { return false }
        return lower.contains("uncensored")
    }

    static func isSafeKey(_ key: String) -> Bool {
        guard key.hasPrefix("uncensored/"), !key.contains("\\"), !key.contains(":"),
              !key.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else {
            return false
        }
        return key.split(separator: "/", omittingEmptySubsequences: false).allSatisfy {
            !$0.isEmpty && $0 != "." && $0 != ".."
        }
    }

    static func parse(_ data: Data, cdnBaseURL: String) throws -> Self {
        guard data.count <= 1024 * 1024,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ModError.message("无码补丁清单无效")
        }
        let version = (json["version"] as? String ?? "1.0").trimmingCharacters(in: .whitespacesAndNewlines)
        let gameId = (json["gameId"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let title = (json["title"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let summary = (json["summary"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let fileName = (json["fileName"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let key = (json["key"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let size: Int64 = (json["size"] is Bool) ? -1 : ((json["size"] as? NSNumber)?.int64Value ?? -1)
        let sha256 = (json["sha256"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cgCount: Int = (json["cgCount"] is Bool) ? 0 : ((json["cgCount"] as? NSNumber)?.intValue ?? 0)
        let updatedAt = (json["updatedAt"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

        guard isSafeOverlayFileName(fileName), isSafeKey(key), size > 0,
              sha256.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil else {
            throw ModError.message("无码补丁清单字段不安全或无效")
        }
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*")
        let encoded = key.split(separator: "/").map {
            String($0).addingPercentEncoding(withAllowedCharacters: allowed) ?? String($0)
        }.joined(separator: "/")
        let base = cdnBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return Self(
            version: version.isEmpty ? "1.0" : version,
            gameId: gameId,
            title: title,
            summary: summary,
            fileName: fileName,
            key: key,
            size: size,
            sha256: sha256,
            cgCount: cgCount,
            updatedAt: updatedAt,
            downloadUrl: base + "/files/" + encoded
        )
    }

    static func fetch(for descriptor: UncensoredGameDescriptor) async throws -> Self {
        var request = URLRequest(url: descriptor.manifestURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        let (bytes, response) = try await URLSession.shared.bytes(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw ModError.message("无法获取无码补丁信息，请检查网络")
        }
        if http.expectedContentLength > Int64(maxManifestBytes) {
            throw ModError.message("无码补丁清单过大")
        }
        var data = Data()
        if http.expectedContentLength > 0 {
            data.reserveCapacity(Int(http.expectedContentLength))
        }
        for try await byte in bytes {
            if data.count >= maxManifestBytes {
                throw ModError.message("无码补丁清单过大")
            }
            data.append(byte)
        }
        return try parse(data, cdnBaseURL: descriptor.cdnBaseURL)
    }
}

struct InstalledUncensoredInfo: Equatable {
    static let metaFileName = ".uncensored_patch.json"
    let version: String
    let gameId: String
    let fileName: String
    let size: Int64
    let sha256: String
    let cgCount: Int
    let updatedAt: String
    let installedAtMs: Int64

    static func read(_ directory: URL) -> Self? {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent(metaFileName)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let fileName = (json["fileName"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard UncensoredPatchManifest.isSafeOverlayFileName(fileName),
              ModFiles.isFile(directory.appendingPathComponent(fileName)) else { return nil }
        let ver = (json["version"] as? String ?? "1.0").trimmingCharacters(in: .whitespacesAndNewlines)
        return Self(
            version: ver.isEmpty ? "1.0" : ver,
            gameId: (json["gameId"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
            fileName: fileName,
            size: (json["size"] as? NSNumber)?.int64Value ?? 0,
            sha256: (json["sha256"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            cgCount: (json["cgCount"] as? NSNumber)?.intValue ?? 0,
            updatedAt: (json["updatedAt"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
            installedAtMs: (json["installedAtMs"] as? NSNumber)?.int64Value ?? 0
        )
    }

    func write(to directory: URL) throws {
        let json: [String: Any] = [
            "version": version,
            "gameId": gameId,
            "fileName": fileName,
            "size": size,
            "sha256": sha256,
            "cgCount": cgCount,
            "updatedAt": updatedAt,
            "installedAtMs": installedAtMs
        ]
        try JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])
            .write(to: directory.appendingPathComponent(Self.metaFileName), options: .atomic)
    }

    static func delete(_ directory: URL) {
        try? ModFiles.removeIfPresent(directory.appendingPathComponent(metaFileName))
    }
}

enum UncensoredPatchStore {
    static func isInstalled(_ directory: URL) -> Bool {
        if InstalledUncensoredInfo.read(directory) != nil { return true }
        return ModFiles.children(directory).contains {
            ModFiles.isFile($0) && UncensoredPatchManifest.isManagedOverlayFileName($0.lastPathComponent)
        }
    }

    static func uninstall(from directory: URL) throws {
        if let info = InstalledUncensoredInfo.read(directory),
           UncensoredPatchManifest.isSafeOverlayFileName(info.fileName) {
            try ModFiles.removeIfPresent(directory.appendingPathComponent(info.fileName))
        }
        for child in ModFiles.children(directory) where ModFiles.isFile(child) {
            if UncensoredPatchManifest.isManagedOverlayFileName(child.lastPathComponent) {
                try ModFiles.removeIfPresent(child)
            }
        }
        InstalledUncensoredInfo.delete(directory)
    }
}

final class UncensoredPatchInstaller: ObservableObject {
    @Published private(set) var isRunning = false
    @Published private(set) var progressText = ""
    @Published private(set) var fraction: Double?
    @Published private(set) var succeeded = false
    @Published private(set) var failure: String?

    private var activeTransfer: ModInstaller?
    private let queue = DispatchQueue(label: "io.github.alicehub.uncensored-install", qos: .userInitiated)
    private let lock = NSLock()
    private var cancelled = false

    func cancel() {
        lock.lock()
        cancelled = true
        let transfer = activeTransfer
        lock.unlock()
        transfer?.cancel()
    }

    private func isCancelled() -> Bool {
        lock.lock(); defer { lock.unlock() }; return cancelled
    }

    func install(_ manifest: UncensoredPatchManifest, to directory: URL) {
        guard !isRunning else { return }
        let transfer = ModInstaller()
        lock.lock()
        cancelled = false
        activeTransfer = transfer
        lock.unlock()
        isRunning = true; succeeded = false; failure = nil
        progressText = "准备下载无码补丁…"; fraction = 0

        let cache = directory.deletingLastPathComponent().appendingPathComponent(".uncensored_cache", isDirectory: true)
        queue.async {
            var error: Error?
            do {
                let release = ModReleaseEntry(
                    fileId: 1,
                    version: manifest.version,
                    fileName: manifest.fileName,
                    downloadUrl: AliceHubCDN.canonicalURL(manifest.downloadUrl),
                    expectedSize: manifest.size,
                    expectedSha256: manifest.sha256
                )
                let downloaded = try transfer.downloadVerifiedFile(release, cache: cache) { done, total in
                    let effTotal = total > 0 ? total : manifest.size
                    let frac = effTotal > 0 ? min(1, max(0, Double(done) / Double(effTotal))) : nil
                    let doneMB = done / 1048576
                    let totalMB = max(1, effTotal / 1048576)
                    let pct = frac.map { Int($0 * 100) } ?? 0
                    DispatchQueue.main.async {
                        self.progressText = "下载中 · \(pct)%\n\(doneMB) / \(totalMB) MB · \(manifest.fileName)"
                        self.fraction = frac
                    }
                }
                if self.isCancelled() { throw CancellationError() }
                let dest = try ModFiles.checked(directory, manifest.fileName)
                let prev = InstalledUncensoredInfo.read(directory)
                let destOwned = !ModFiles.exists(dest) ||
                    UncensoredPatchManifest.isManagedOverlayFileName(manifest.fileName) ||
                    prev?.fileName.lowercased() == manifest.fileName.lowercased()
                guard destOwned else {
                    throw ModError.message("目标文件已存在且不属于 AliceHub 无码补丁，已取消覆盖")
                }
                if let prev, UncensoredPatchManifest.isSafeOverlayFileName(prev.fileName),
                   prev.fileName.lowercased() != manifest.fileName.lowercased() {
                    let prevURL = try ModFiles.checked(directory, prev.fileName)
                    if ModFiles.isFile(prevURL) {
                        try ModFiles.removeIfPresent(prevURL)
                    }
                }
                try ModFiles.replace(downloaded, dest)
                try? ModFiles.removeIfPresent(downloaded.appendingPathExtension("meta"))
                try InstalledUncensoredInfo(
                    version: manifest.version,
                    gameId: manifest.gameId,
                    fileName: manifest.fileName,
                    size: manifest.size,
                    sha256: manifest.sha256,
                    cgCount: manifest.cgCount,
                    updatedAt: manifest.updatedAt,
                    installedAtMs: Int64(Date().timeIntervalSince1970 * 1000)
                ).write(to: directory)
            } catch let err {
                error = err
            }
            self.lock.lock()
            self.activeTransfer = nil
            self.lock.unlock()
            let finalError = error
            DispatchQueue.main.async {
                if finalError is CancellationError {
                    self.succeeded = false
                    self.failure = nil
                    self.progressText = "已取消"
                } else if let finalError {
                    self.succeeded = false
                    self.failure = finalError.localizedDescription
                } else {
                    self.succeeded = true
                    self.fraction = 1
                }
                self.isRunning = false
            }
        }
    }
}
