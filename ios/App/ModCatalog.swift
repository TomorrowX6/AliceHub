import Foundation

struct ModReleaseEntry: Equatable {
    var fileId: Int
    var version: String
    var fileName: String
    var summary = ""
    var changelog = ""
    var uploadedAt = ""
    var downloadUrl = ""
    var isPatch = false
    var baseFileId: Int?
    var baseVersion: String?
    var expectedSize: Int64 = -1
    var expectedSha256: String?
    var group = ""
    var formattedDate: String { String(uploadedAt.replacingOccurrences(of: "T", with: " ").prefix(16)) }
    var isFullMarked: Bool { version.contains("完整") || fileName.contains("完整") || version.contains("正式版") }
}

struct InstalledModInfo: Equatable {
    static let metaFileName = ".mod_version.json"
    var version: String
    var baseVersion: String?
    var fileId = -1
    var baseFileId = -1
    var fileName = ""
    var uploadedAt = ""
    var source = "online"
    var installedAtMs: Int64 = 0
    var kind = "mod"
    var formattedDate: String { String(uploadedAt.replacingOccurrences(of: "T", with: " ").prefix(16)) }

    static func read(_ directory: URL) -> Self? {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent(metaFileName)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let raw = modString(json, "version")
        guard !raw.isEmpty else { return nil }
        let base = modString(json, "baseVersion")
        let source = modString(json, "source", fallback: "online").nonempty ?? "online"
        let kind = modString(json, "kind", fallback: "mod").nonempty ?? "mod"
        let local = source != "online" && kind != "cn_patch"
        let patch = !base.isEmpty && base != raw
        return Self(version: local ? ModCatalog.inferVersionFromFileName(raw, isPatchHint: base.isEmpty ? nil : patch, fallbackIniDir: patch ? nil : directory) : raw,
                    baseVersion: base.isEmpty ? nil : (local ? ModCatalog.inferVersionFromFileName(base, isPatchHint: false, fallbackIniDir: directory) : base),
                    fileId: modInt(json, "fileId", fallback: -1), baseFileId: modInt(json, "baseFileId", fallback: -1),
                    fileName: modString(json, "fileName"), uploadedAt: modString(json, "uploadedAt"), source: source,
                    installedAtMs: (json["installedAtMs"] as? NSNumber)?.int64Value ?? 0, kind: kind)
    }

    static func readOrInfer(_ directory: URL) -> Self? {
        if let info = read(directory) { return info }
        guard ModOverlay.detectInstalledMod(directory), let version = ModCatalog.extractCanonicalModVersionFromDir(directory) else { return nil }
        return Self(version: version, baseVersion: version, source: "preinstalled")
    }

    func write(to directory: URL) throws {
        let json: [String: Any] = ["version": version, "baseVersion": baseVersion ?? "", "fileId": fileId,
            "baseFileId": baseFileId, "fileName": fileName, "uploadedAt": uploadedAt, "source": source,
            "installedAtMs": installedAtMs, "kind": kind]
        try JSONSerialization.data(withJSONObject: json, options: [.sortedKeys]).write(to: directory.appendingPathComponent(Self.metaFileName), options: .atomic)
    }

    static func delete(_ directory: URL) throws {
        try ModFiles.removeIfPresent(directory.appendingPathComponent(metaFileName))
    }
}

struct ModUpdatePlan {
    let targetRelease: ModReleaseEntry
    let steps: [ModReleaseEntry]
    let isIncrementalOnly: Bool
}

struct ModGameDescriptor: Equatable {
    let id: String
    let title: String
    var isCnPatch: Bool { id != "rance10" }
    var overlayKind: String { isCnPatch ? "cn_patch" : "mod" }
    var overlayURL: String { "https://alicehub.000.moe/" + id }
    var catalogPath: String { isCnPatch ? "files/cn-patch/index.json" : "files/mods/index.json" }

    static let supported = [Self(id: "rance10", title: "兰斯10 决战"), Self(id: "evenicle", title: "夏娃年代记"), Self(id: "evenicle2", title: "夏娃年代记2")]

    static func classifyGameRootDir(_ directory: URL) -> Self? {
        let names = ModFiles.children(directory).filter(ModFiles.isFile).map { $0.lastPathComponent.lowercased() }
        for id in ["rance10", "evenicle2", "evenicle"] where names.contains(id + ".ain") {
            return supported.first { $0.id == id }
        }
        return nil
    }

    static func classifyDirectory(_ directory: URL) -> Self? {
        if let exact = classifyGameRootDir(directory) { return exact }
        if ModFiles.children(directory).contains(where: { ModFiles.isFile($0) && $0.pathExtension.lowercased() == "ain" && $0.lastPathComponent.lowercased() != "system39.ain" }) { return nil }
        let text = ((ModFiles.ini(directory)["gamename"] ?? "") + " " + directory.lastPathComponent).lowercased()
        let evenicle2Aliases = ["evenicle2", "evenicle 2", "evenicle_2", "evenicle ii", "evenicleii", "evenicle ⅱ", "evenicleⅱ", "イブニクル2", "イブニクル２", "イブニクル 2", "イブニクル ２", "イブニクルⅱ", "夏娃年代记2", "夏娃年代记 2", "夏娃年代记ii", "夏娃年代记ⅱ", "夏娃年代記2", "夏娃2"]
        if evenicle2Aliases.contains(where: text.contains) { return supported[2] }
        if text.contains("evenicle") || text.contains("夏娃") || text.contains("イブニクル") { return supported[1] }
        if text.contains("rance10") || text.contains("rance 10") || text.contains("兰斯10") || text.contains("兰斯１０") || text.contains("ランス10") || text.contains("ランス１０") { return supported[0] }
        return nil
    }
}

extension String {
    fileprivate var nonempty: String? { isEmpty ? nil : self }
    func modReplacing(_ pattern: String, _ replacement: String) -> String {
        replacingOccurrences(of: pattern, with: replacement, options: .regularExpression)
    }
    func modMatch(_ pattern: String, group: Int = 1) -> String? {
        guard let expression = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = expression.firstMatch(in: self, range: NSRange(startIndex..., in: self)),
              group < match.numberOfRanges, let range = Range(match.range(at: group), in: self) else { return nil }
        return String(self[range])
    }
}

private func modString(_ json: [String: Any], _ key: String, fallback: String = "") -> String {
    (json[key] as? String ?? fallback).trimmingCharacters(in: .whitespacesAndNewlines)
}
private func modInt(_ json: [String: Any], _ key: String, fallback: Int) -> Int {
    (json[key] as? NSNumber)?.intValue ?? Int(json[key] as? String ?? "") ?? fallback
}

enum ModCatalog {
    private static let number = #"v?(\d+\.\d+(?:\.\d+)?[a-zA-Z]?)"#
    private static func strip(_ raw: String) -> String {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        for ext in [".tar.gz", ".tar.xz", ".rar", ".7z", ".zip", ".tgz", ".txz"] where text.lowercased().hasSuffix(ext) {
            text = String(text.dropLast(ext.count)).trimmingCharacters(in: .whitespacesAndNewlines); break
        }
        return text.modReplacing(#"(?:\s*[\(（]\d+[\)）]|\s*[-_]?\s*副本)+$"#, "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func normalizeModVersionKey(_ raw: String) -> String {
        var text = strip(raw.lowercased()).modReplacing(#"[()（）\[\] _]"#, "")
        var previous = ""
        while previous != text {
            previous = text
            for prefix in ["兰斯10日不落mod", "rance10日不落mod", "日不落mod", "日不落", "兰斯10mod", "rance10mod", "兰斯10", "rance10"] where text.hasPrefix(prefix) {
                text = String(text.dropFirst(prefix.count)).modReplacing(#"^[-.]+"#, "")
            }
        }
        text = text.modReplacing(#"^v(?=\d)"#, "")
        for word in ["bug修复补丁", "修复补丁", "增量补丁", "patch"] { text = text.replacingOccurrences(of: word, with: "补丁") }
        if !text.contains("补丁") {
            previous = ""
            while previous != text {
                previous = text
                for suffix in ["完整版", "正式版", "完整包", "整合版", "本体", "更新"] where text.hasSuffix(suffix) && text.count > suffix.count {
                    text = String(text.dropLast(suffix.count)).modReplacing(#"[-.]+$"#, "")
                }
            }
        }
        return text
    }

    static func extractCanonicalModVersion(_ raw: String, isPatchHint: Bool? = nil) -> String? {
        var text = strip(raw.replacingOccurrences(of: "\\", with: "/").components(separatedBy: "/").last ?? "")
            .modReplacing(#"[（）()\[\]【】]"#, " ")
        for word in ["收费必是倒卖", "免费分享", "严禁倒卖"] { text = text.replacingOccurrences(of: word, with: " ") }
        var previous = ""
        while previous != text {
            previous = text
            text = text.modReplacing(#"^[ \-_·:：]+"#, "")
            for prefix in ["兰斯10", "兰斯１０", "决战", "rance10", "rance 10", "日不落mod", "日不落", "mod"] where text.lowercased().hasPrefix(prefix) {
                text = String(text.dropFirst(prefix.count)).modReplacing(#"^[ \-_·:：]+"#, "")
            }
        }
        guard let version = text.modMatch(number), let matched = text.modMatch(number, group: 0), let range = text.range(of: matched) else { return nil }
        let lower = text.lowercased()
        let patch = ["补丁", "patch", "hotfix", "热更", "修复"].contains { lower.contains($0) }
        let full = ["完整", "正式", "整合", "本体"].contains { lower.contains($0) }
        let isPatch = patch && !full ? true : (full && !patch ? false : (isPatchHint ?? patch))
        if isPatch {
            let sequence = String(text[range.upperBound...]).modMatch(#"(?:补丁|patch|hotfix)\s*(\d+)"#) ?? ""
            return "v" + version + (lower.contains("增量") ? "增量补丁" : "修复补丁") + sequence
        }
        for (words, label) in [(["内测"], "内测版"), (["公测"], "公测版"), (["测试", "beta"], "测试版"), (["先行"], "先行版"), (["体验"], "体验版")] where words.contains(where: { lower.contains($0) }) {
            return "v" + version + label
        }
        return "v" + version + "正式版"
    }

    static func extractCanonicalModVersionFromDir(_ directory: URL) -> String? {
        if let name = ModFiles.ini(directory)["gamename"], let version = extractCanonicalModVersion(name, isPatchHint: false) { return version }
        return extractCanonicalModVersion(directory.lastPathComponent, isPatchHint: false)
    }

    static func inferVersionFromFileName(_ fileName: String, isPatchHint: Bool? = nil, fallbackIniDir: URL? = nil) -> String {
        if let version = extractCanonicalModVersion(fileName, isPatchHint: isPatchHint) { return version }
        if let directory = fallbackIniDir, let version = extractCanonicalModVersionFromDir(directory) { return version }
        let text = strip(fileName.replacingOccurrences(of: "\\", with: "/").components(separatedBy: "/").last ?? "").modReplacing("[()（）]", " ").modReplacing(#"\s+"#, " ").trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return "MOD" }
        if ["mod", "mods", "rance10", "兰斯10"].contains(text.lowercased()) { return "MOD" }
        return text
    }

    static func inferLocalOverlayVersion(_ fileName: String, isPatchHint: Bool, fallbackIniDir: URL?, isCnPatch: Bool) -> String {
        if !isCnPatch { return inferVersionFromFileName(fileName, isPatchHint: isPatchHint, fallbackIniDir: isPatchHint ? nil : fallbackIniDir) }
        let ini = fallbackIniDir.map { ModFiles.ini($0)["gamename"] ?? "" } ?? ""
        return (fileName.modMatch(number) ?? ini.modMatch(number)).map { "V" + $0 } ?? "V1.00"
    }

    static func matchInstalledIndex(_ releases: [ModReleaseEntry], _ installed: InstalledModInfo?) -> Int {
        guard let installed else { return -1 }
        if installed.fileId > 0, let index = releases.firstIndex(where: { $0.fileId == installed.fileId }) { return index }
        if !installed.fileName.isEmpty {
            if let index = releases.firstIndex(where: { $0.fileName.lowercased() == installed.fileName.lowercased() }) { return index }
            let key = normalizeModVersionKey(installed.fileName)
            if !key.isEmpty, let index = releases.firstIndex(where: { normalizeModVersionKey($0.fileName) == key || normalizeModVersionKey($0.version) == key }) { return index }
        }
        let key = normalizeModVersionKey(installed.version)
        return key.isEmpty ? -1 : (releases.firstIndex { normalizeModVersionKey($0.version) == key || normalizeModVersionKey($0.fileName) == key } ?? -1)
    }

    private static func matchesBase(_ base: ModReleaseEntry, _ installed: InstalledModInfo?) -> Bool {
        guard let installed else { return false }
        return (installed.baseFileId > 0 && installed.baseFileId == base.fileId) || (!normalizeModVersionKey(base.version).isEmpty && installed.baseVersion.map { normalizeModVersionKey($0) == normalizeModVersionKey(base.version) } == true)
    }

    static func hasMatchingBaseForPatch(_ releases: [ModReleaseEntry], patchIdx: Int, hasMod: Bool, installed: InstalledModInfo?) -> Bool {
        guard hasMod, releases.indices.contains(patchIdx), releases[patchIdx].isPatch,
              let base = releases.indices.first(where: { $0 > patchIdx && !releases[$0].isPatch }) else { return false }
        return (patchIdx...base).contains(matchInstalledIndex(releases, installed)) || matchesBase(releases[base], installed)
    }

    static func computeUpdatePlan(_ releases: [ModReleaseEntry], hasMod: Bool, installed: InstalledModInfo?) -> ModUpdatePlan? {
        guard let latest = releases.first else { return nil }
        let index = hasMod ? matchInstalledIndex(releases, installed) : -1
        if hasMod && index == 0 { return nil }
        if hasMod, let installed, installed.fileId > 0, latest.fileId > 0, index < 0, installed.fileId >= latest.fileId { return nil }
        if !latest.isPatch { return ModUpdatePlan(targetRelease: latest, steps: [latest], isIncrementalOnly: false) }
        guard let base = releases.indices.first(where: { $0 > 0 && !releases[$0].isPatch }) else {
            return hasMod ? ModUpdatePlan(targetRelease: latest, steps: [latest], isIncrementalOnly: true) : nil
        }
        if hasMod && ((1...base).contains(index) || matchesBase(releases[base], installed)) {
            let patches: [ModReleaseEntry]
            if (1...base).contains(index) { patches = releases[..<index].reversed().filter(\.isPatch) }
            else { patches = releases[..<base].reversed().filter { $0.isPatch && ((installed?.fileId ?? -1) <= 0 || $0.fileId <= 0 || $0.fileId > installed!.fileId) } }
            if !patches.isEmpty { return ModUpdatePlan(targetRelease: latest, steps: patches, isIncrementalOnly: true) }
        }
        return ModUpdatePlan(targetRelease: latest, steps: [releases[base]] + releases[..<base].reversed().filter(\.isPatch), isIncrementalOnly: false)
    }

    static func computeStepsForSelectedRelease(_ releases: [ModReleaseEntry], targetIdx: Int, hasMod: Bool, installed: InstalledModInfo?, includeBaseForPatch: Bool) -> [ModReleaseEntry] {
        guard releases.indices.contains(targetIdx) else { return [] }
        let target = releases[targetIdx]
        if !target.isPatch { return [target] }
        let base = releases.indices.first { $0 > targetIdx && !releases[$0].isPatch }
        if !includeBaseForPatch || base == nil {
            let index = matchInstalledIndex(releases, installed)
            if hasMod, let base, index > targetIdx, index <= base {
                let patches = releases[targetIdx..<index].reversed().filter(\.isPatch)
                if !patches.isEmpty { return patches }
            }
            return [target]
        }
        return [releases[base!]] + releases[targetIdx..<base!].reversed().filter(\.isPatch)
    }

    static func sanitizeParenFree(_ raw: String, multiline: Bool) -> String {
        let text = raw.modReplacing("[()（）]", " ")
        if multiline { return text.replacingOccurrences(of: "\r", with: "").components(separatedBy: "\n").map { $0.modReplacing(#"\s+"#, " ").trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.joined(separator: "\n") }
        return text.modReplacing(#"\s+"#, " ").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func formatSize(_ bytes: Int64) -> String {
        guard bytes > 0 else { return "" }
        for (unit, size, digits) in [("GB", 1073741824.0, 2), ("MB", 1048576.0, 1), ("KB", 1024.0, 0)] where Double(bytes) >= size {
            return String(format: "%.*f %@", locale: Locale(identifier: "en_US_POSIX"), digits, Double(bytes) / size, unit)
        }
        return "\(bytes) B"
    }

    static func parseModCatalog(_ data: Data, cdnBaseURL: String) throws -> [ModReleaseEntry] {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any], let rows = json["releases"] as? [Any] else { throw ModError.message("获取失败，请检查网络") }
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*")
        var releases: [ModReleaseEntry] = []
        for (index, row) in rows.enumerated() {
            guard let row = row as? [String: Any] else { continue }
            let key = modString(row, "key"), file = modString(row, "fileName")
            let version = sanitizeParenFree(modString(row, "version"), multiline: false)
            guard !key.isEmpty, !file.isEmpty, !version.isEmpty, ArchiveExtractor.isSupportedArchiveName(file) else { continue }
            let summary = sanitizeParenFree(modString(row, "summary"), multiline: false)
            let changelog = sanitizeParenFree(modString(row, "changelog"), multiline: true)
            let encoded = key.components(separatedBy: "/").map { $0.addingPercentEncoding(withAllowedCharacters: allowed)! }.joined(separator: "/")
            releases.append(ModReleaseEntry(fileId: modInt(row, "fileId", fallback: 1000 - index), version: version, fileName: file,
                summary: summary, changelog: changelog.isEmpty ? summary : changelog, uploadedAt: modString(row, "uploadedAt"),
                downloadUrl: cdnBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/files/" + encoded,
                isPatch: row["isPatch"] as? Bool ?? (version.contains("补丁") || file.contains("补丁")),
                expectedSize: (row["size"] as? NSNumber)?.int64Value ?? -1, expectedSha256: modString(row, "sha256").nonempty,
                group: sanitizeParenFree(modString(row, "group"), multiline: false)))
        }
        guard !releases.isEmpty else { throw ModError.message("暂无可用版本") }
        for index in releases.indices where releases[index].isPatch {
            if let base = releases.indices.first(where: { $0 > index && !releases[$0].isPatch }) {
                releases[index].baseFileId = releases[base].fileId; releases[index].baseVersion = releases[base].version
            }
        }
        return releases
    }

    static func fetchModCatalog(for game: ModGameDescriptor) async throws -> [ModReleaseEntry] {
        var request = URLRequest(url: URL(string: game.overlayURL + "/" + game.catalogPath)!)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw ModError.message("获取失败，请检查网络") }
        return try parseModCatalog(data, cdnBaseURL: game.overlayURL)
    }
}
