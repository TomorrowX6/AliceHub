import Foundation

enum AliceHubCDN {
    static let origin = "https://alicehub.000.moe"

    static func gameBaseURLString(for id: String) -> String {
        origin + "/" + id
    }

    static func canonicalURL(_ raw: String) -> String {
        let prefix = origin + "/"
        guard raw.hasPrefix(prefix) else { return raw }
        let rest = String(raw.dropFirst(prefix.count))
        let canonicalRest = rest.hasPrefix("cn/") ? String(rest.dropFirst(3)) : rest
        return prefix + canonicalRest
    }
}

enum OnlineInstall {
    static let storageKey = "hub.onlineInstall"
    static var enabled: Bool { UserDefaults.standard.bool(forKey: storageKey) }
    static let disclaimer = "开启并使用「在线安装」功能前，请您完整阅读并确认以下全部条款。点击「已阅读并同意」即视为您已充分理解并自愿受本条款约束。\n\n第一条 · 应用性质与非商业声明\n1. AliceHub 为免费开源的跨平台运行环境与本地文件管理工具，仅用于引擎兼容性研究、个人技术学习及本地资源管理。\n2. 本应用安装包本身不包含、不内置、不附带任何商业游戏本体、剧本、图像、音频、字体或其他受著作权保护的资产。\n3. 本项目为非营利开源项目，开发者不提供任何付费会员、有偿资源、代下载或商业化服务。任何以本应用名义进行的收费、倒卖或引流行为均与开发者无关。\n\n第二条 · 远程资源与知识产权归属\n1. 「在线安装」默认处于关闭状态，仅在用户主动阅读同意本条款并手动触发下载后，按用户指令连接远程索引与文件节点获取数据。\n2. 通过该通道获取的数据包、汉化补丁、MOD、扩展补丁及相关元信息均整理或来源于互联网公开渠道及第三方社区，开发者不制作、不主张、不享有上述内容的任何权利。\n3. 相关游戏作品及其美术、音乐、文本、商标与程序之全部知识产权均归原著作权人、发行商及对应制作团队所有。AliceHub 及其开发者与原权利人无任何隶属、授权、背书或合作关系。\n\n第三条 · 合法授权与禁止行为\n1. 用户声明并保证，仅在已通过合法渠道取得对应作品正版授权的前提下，将本功能用于个人跨平台运行验证、兼容性测试与个人备份恢复。\n2. 若您尚未取得对应作品的合法授权，请通过官方渠道购买正版，不得下载、使用或留存相关文件；如不慎下载，应立即停止使用并彻底删除本地全部相关数据。\n3. 严禁将本应用及通过本功能获取的任何文件用于商业用途、有偿代装、打包售卖、付费群分发、广告引流、二次打包再分发、公开传播下载链接，或用于公开直播、公开录屏及其他侵犯权利人权益的行为。\n\n第四条 · 年龄分级与地区法律合规\n1. 部分游戏作品及相关扩展内容可能包含仅面向成年人的题材或画面。\n2. 用户确认自身已年满所在国家或司法辖区规定的法定成年年龄（不低于 18 周岁，如当地法律规定更高年龄标准则从其规定），具备完全民事行为能力，并承诺不向未成年人展示、提供或传播相关内容。\n3. 用户须自行确认并严格遵守所在国家或地区关于数字内容访问、下载、存储及使用的全部适用法律法规。如当地法律禁止使用相关功能或持有相关内容，请立即停止使用。\n\n第五条 · 权利人通知与下架处理\n如任何著作权人或合法权利人认为本应用在线索引中包含涉嫌侵犯其合法权益的条目或链接，请通过项目 GitHub 仓库或公开联系方式提供有效权属证明及具体条目信息。开发者在核实后将第一时间断开对应索引或移除相关条目，且不因此承担任何额外赔偿责任。\n\n第六条 · 隐私、网络与风险免责\n1. 开启本功能后，应用仅在用户主动刷新目录、检查资源或下载文件时发起必要网络请求，不收集用户个人身份信息，不上传用户本地游戏文件或个人存档。\n2. 在线通道、第三方补丁及 MOD 均按「现状」与「现有可用状态」提供，开发者不对网络连通性、下载速度、文件完整性、安全性、适配稳定性及持续可用性作任何明示或暗示担保。\n3. 开发者有权在无需事先通知的情况下，随时对在线通道进行维护、限流、调整条目、暂停或永久终止服务。\n4. 因使用本功能或第三方补丁/MOD 导致的设备异常、存储占用、流量费用、存档损坏或数据丢失，以及因用户违反法律法规或本条款引发的一切纠纷、索赔与法律责任，均由用户本人独立承担，与 AliceHub 及其开发者无关。\n\n第七条 · 条款生效、变更与可分割性\n1. 点击「已阅读并同意」即表示您已完整阅读、充分理解并无条件接受本条款全部内容；如不同意任何一条，请点击「取消」保持关闭状态。\n2. 您可随时在应用设置中关闭「在线安装」功能，关闭后已下载到本地的文件需由您自行管理或删除。\n3. 开发者有权根据法律法规变化或项目需要随时修订本条款，修订后继续使用本功能即视为接受更新后的条款。\n4. 如本条款中任何部分在特定司法辖区被认定为无效或不可执行，该部分应在法律允许范围内作最小限度调整或视为可分割，不影响其余条款的效力与执行。"
}

enum DownloadConcurrency {
    static let storageKey = "hub.downloadConcurrency"
    static let minValue = 1
    static let maxValue = 16
    static let defaultValue = 8
    static let range = minValue...maxValue

    static func clamped(_ value: Int) -> Int {
        min(maxValue, max(minValue, value))
    }

    static var current: Int {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: storageKey) != nil else { return defaultValue }
        return clamped(defaults.integer(forKey: storageKey))
    }
}

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
    var group = ""
    var formattedDate: String { String(uploadedAt.replacingOccurrences(of: "T", with: " ").prefix(16)) }

    static func read(_ directory: URL) -> Self? {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent(metaFileName)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let raw = modString(json, "version")
        guard !raw.isEmpty else { return nil }
        let base = modString(json, "baseVersion")
        let fileName = modString(json, "fileName")
        let source = modString(json, "source", fallback: "online").nonempty ?? "online"
        let kind = modString(json, "kind", fallback: "mod").nonempty ?? "mod"
        let local = source != "online" && kind != "cn_patch"
        let patch = !base.isEmpty && base != raw
        let group: String
        if json["group"] != nil {
            group = modString(json, "group")
        } else if kind == "mod" {
            group = ModCatalog.inferModGroup(fileName: fileName, directory: directory)
        } else {
            group = ""
        }
        return Self(version: local ? ModCatalog.inferVersionFromFileName(raw, isPatchHint: base.isEmpty ? nil : patch, fallbackIniDir: patch ? nil : directory) : raw,
                    baseVersion: base.isEmpty ? nil : (local ? ModCatalog.inferVersionFromFileName(base, isPatchHint: false, fallbackIniDir: directory) : base),
                    fileId: modInt(json, "fileId", fallback: -1), baseFileId: modInt(json, "baseFileId", fallback: -1),
                    fileName: fileName, uploadedAt: modString(json, "uploadedAt"), source: source,
                    installedAtMs: (json["installedAtMs"] as? NSNumber)?.int64Value ?? 0, kind: kind, group: group)
    }

    static func readOrInfer(_ directory: URL) -> Self? {
        if let info = read(directory) { return info }
        guard ModOverlay.detectInstalledMod(directory) else { return nil }
        let inferredGroup = ModCatalog.inferModGroup(fileName: "", directory: directory)
        let version = ModCatalog.extractCanonicalModVersionFromDir(directory) ?? (inferredGroup.isEmpty ? nil : inferredGroup)
        guard let version else { return nil }
        return Self(version: version, baseVersion: version, source: "preinstalled", group: inferredGroup)
    }

    func write(to directory: URL) throws {
        let json: [String: Any] = ["version": version, "baseVersion": baseVersion ?? "", "fileId": fileId,
            "baseFileId": baseFileId, "fileName": fileName, "uploadedAt": uploadedAt, "source": source,
            "installedAtMs": installedAtMs, "kind": kind, "group": group]
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
    var isCnPatch: Bool { id == "evenicle" || id == "evenicle2" }
    var overlayKind: String { isCnPatch ? "cn_patch" : "mod" }
    var overlayURL: String { AliceHubCDN.gameBaseURLString(for: id) }
    var catalogPath: String { isCnPatch ? "files/cn-patch/index.json" : "files/mods/index.json" }

    static let supported = [
        Self(id: "rance10", title: "兰斯10 决战"),
        Self(id: "evenicle", title: "夏娃年代记"),
        Self(id: "evenicle2", title: "夏娃年代记2"),
        Self(id: "sengokurance", title: "战国兰斯")
    ]

    static func classifyGameRootDir(_ directory: URL) -> Self? {
        let names = ModFiles.children(directory).filter(ModFiles.isFile).map { $0.lastPathComponent.lowercased() }
        for id in ["rance10", "evenicle2", "evenicle"] where names.contains(id + ".ain") {
            return supported.first { $0.id == id }
        }
        if names.contains("sengokurance.ain") || names.contains("rance7.ain") ||
            names.contains("daisengoku.ain") || names.contains("愴崙儔儞僗.ain") ||
            names.contains("戦国ランス.ain") || names.contains("战国兰斯.ain") ||
            names.contains(where: { $0.hasSuffix("ga.ald") && ($0 == "daisengokuga.ald" || $0.contains("愴崙") || $0.contains("戦国") || $0.contains("战国") || $0.contains("sengoku")) }) {
            return supported.first { $0.id == "sengokurance" }
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
        if text.contains("sengoku") || text.contains("daisengoku") || text.contains("大战国") || text.contains("大戦国") || text.contains("战国兰斯") || text.contains("戦国ランス") || text.contains("愴崙儔儞僗") { return supported[3] }
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
            for prefix in ["兰斯10日不落mod", "rance10日不落mod", "日不落mod", "日不落", "兰斯10全系列mod", "rance10全系列mod", "r10全系列mod", "全系列mod", "全系列", "bfyy", "大战国兰斯mod", "大战国兰斯", "大战国mod", "大战国", "daisengokurancemod", "daisengokurance", "daisengokumod", "daisengoku", "战国兰斯mod", "战国兰斯", "sengokurancemod", "sengokurance", "兰斯10mod", "rance10mod", "r10mod", "兰斯10", "rance10", "r10"] where text.hasPrefix(prefix) {
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
            for prefix in ["兰斯10", "兰斯１０", "决战", "rance10", "rance 10", "r10", "日不落mod", "日不落", "全系列mod", "全系列", "bfyy", "大战国兰斯mod", "大战国兰斯", "大战国mod", "大战国", "daisengokurancemod", "daisengokurance", "daisengokumod", "daisengoku", "战国兰斯mod", "战国兰斯", "sengokurancemod", "sengokurance", "mod"] where text.lowercased().hasPrefix(prefix) {
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

    static let modGroupRibuluo = "日不落MOD"
    static let modGroupBfyy = "全系列MOD"
    static let modGroupDaiSengoku = "大战国兰斯MOD"

    static func inferModGroup(fileName: String = "", directory: URL? = nil) -> String {
        let lowerName = fileName.lowercased()
        if lowerName.contains("全系列") || lowerName.contains("bfyy") || lowerName.contains("波风幽影") {
            return modGroupBfyy
        }
        if lowerName.contains("日不落") {
            return modGroupRibuluo
        }
        if lowerName.contains("大战国") || lowerName.contains("大戦国") || lowerName.contains("daisengoku") {
            return modGroupDaiSengoku
        }
        if let directory, ModFiles.isDirectory(directory) {
            for file in ModFiles.children(directory).filter(ModFiles.isFile) {
                let n = file.lastPathComponent.lowercased()
                if (n.hasPrefix("rance10cgbfyy") || n.hasPrefix("rance10flatbfyy")) && n.hasSuffix(".afa") {
                    return modGroupBfyy
                }
                if (n.hasPrefix("rance10modcg") || n.hasPrefix("rance10modflat")) && n.hasSuffix(".afa") {
                    return modGroupRibuluo
                }
                if n == "daisengoku.ain" || n == "daisengokugm.ald" || n == "daisengokubm.ald" ||
                    (n.hasSuffix("gm.ald") && (n.contains("愴崙") || n.contains("戦国") || n.contains("sengoku"))) {
                    return modGroupDaiSengoku
                }
            }
            let ini = ModFiles.ini(directory)
            if ini["savefolder"]?.lowercased() == "savedatabfyy" {
                return modGroupBfyy
            }
            if ini["savefolder"]?.lowercased() == "savedatadsr" {
                return modGroupDaiSengoku
            }
            let gn = ini["gamename"] ?? ""
            if gn.contains("全系列") || gn.lowercased().contains("bfyy") {
                return modGroupBfyy
            }
            if gn.contains("日不落") || ini["savefolder"]?.lowercased() == "modsavedata" {
                return modGroupRibuluo
            }
            if gn.contains("大战国") || gn.contains("大戦国") || gn.lowercased().contains("daisengoku") {
                return modGroupDaiSengoku
            }
        }
        return ""
    }

    static func distinctModGroups(_ releases: [ModReleaseEntry]) -> [String] {
        var result: [String] = []
        var seen = Set<String>()
        for release in releases {
            let group = release.group.trimmingCharacters(in: .whitespacesAndNewlines)
            if !group.isEmpty && seen.insert(group).inserted {
                result.append(group)
            }
        }
        return result
    }

    static func releasesForGroup(_ releases: [ModReleaseEntry], group: String?) -> [ModReleaseEntry] {
        let groups = distinctModGroups(releases)
        guard groups.count > 1, let group, !group.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return releases
        }
        let filtered = releases.filter { $0.group.caseInsensitiveCompare(group) == .orderedSame }
        return filtered.isEmpty ? releases : filtered
    }

    static func groupsConflict(_ a: String?, _ b: String?) -> Bool {
        let ga = (a ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let gb = (b ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return !ga.isEmpty && !gb.isEmpty && ga.caseInsensitiveCompare(gb) != .orderedSame
    }

    private static func sameReleaseGroup(_ a: ModReleaseEntry, _ b: ModReleaseEntry) -> Bool {
        !groupsConflict(a.group, b.group)
    }

    static func matchInstalledIndex(_ releases: [ModReleaseEntry], _ installed: InstalledModInfo?) -> Int {
        guard let installed else { return -1 }
        if installed.fileId > 0, let index = releases.firstIndex(where: { $0.fileId == installed.fileId && !groupsConflict($0.group, installed.group) }) { return index }
        if !installed.fileName.isEmpty {
            if let index = releases.firstIndex(where: { $0.fileName.lowercased() == installed.fileName.lowercased() && !groupsConflict($0.group, installed.group) }) { return index }
            let key = normalizeModVersionKey(installed.fileName)
            if !key.isEmpty, let index = releases.firstIndex(where: { !groupsConflict($0.group, installed.group) && (normalizeModVersionKey($0.fileName) == key || normalizeModVersionKey($0.version) == key) }) { return index }
        }
        let key = normalizeModVersionKey(installed.version)
        return key.isEmpty ? -1 : (releases.firstIndex { !groupsConflict($0.group, installed.group) && (normalizeModVersionKey($0.version) == key || normalizeModVersionKey($0.fileName) == key) } ?? -1)
    }

    private static func matchesBase(_ base: ModReleaseEntry, _ installed: InstalledModInfo?) -> Bool {
        guard let installed, !groupsConflict(base.group, installed.group) else { return false }
        return (installed.baseFileId > 0 && installed.baseFileId == base.fileId) || (!normalizeModVersionKey(base.version).isEmpty && installed.baseVersion.map { normalizeModVersionKey($0) == normalizeModVersionKey(base.version) } == true)
    }

    static func hasMatchingBaseForPatch(_ releases: [ModReleaseEntry], patchIdx: Int, hasMod: Bool, installed: InstalledModInfo?) -> Bool {
        guard hasMod, releases.indices.contains(patchIdx), releases[patchIdx].isPatch,
              !groupsConflict(releases[patchIdx].group, installed?.group),
              let base = releases.indices.first(where: { $0 > patchIdx && !releases[$0].isPatch && sameReleaseGroup(releases[patchIdx], releases[$0]) }) else { return false }
        let idx = matchInstalledIndex(releases, installed)
        return ((patchIdx...base).contains(idx) && sameReleaseGroup(releases[patchIdx], releases[idx])) || matchesBase(releases[base], installed)
    }

    static func computeUpdatePlan(_ releases: [ModReleaseEntry], hasMod: Bool, installed: InstalledModInfo?, canRestore: Bool = true) -> ModUpdatePlan? {
        guard let latest = releases.first else { return nil }
        let switchingGroup = hasMod && groupsConflict(latest.group, installed?.group)
        if switchingGroup && !canRestore { return nil }
        let index = (hasMod && !switchingGroup) ? matchInstalledIndex(releases, installed) : -1
        if hasMod && !switchingGroup && index == 0 { return nil }
        if hasMod && !switchingGroup, let installed, installed.fileId > 0, latest.fileId > 0, index < 0, installed.fileId >= latest.fileId { return nil }
        if !latest.isPatch { return ModUpdatePlan(targetRelease: latest, steps: [latest], isIncrementalOnly: false) }
        guard let base = releases.indices.first(where: { $0 > 0 && !releases[$0].isPatch && sameReleaseGroup(latest, releases[$0]) }) else {
            return (hasMod && !switchingGroup) ? ModUpdatePlan(targetRelease: latest, steps: [latest], isIncrementalOnly: true) : nil
        }
        if hasMod && !switchingGroup && (((1...base).contains(index) && sameReleaseGroup(latest, releases[index])) || matchesBase(releases[base], installed)) {
            let patches: [ModReleaseEntry]
            if (1...base).contains(index) { patches = releases[..<index].reversed().filter { $0.isPatch && sameReleaseGroup(latest, $0) } }
            else { patches = releases[..<base].reversed().filter { $0.isPatch && sameReleaseGroup(latest, $0) && ((installed?.fileId ?? -1) <= 0 || $0.fileId <= 0 || $0.fileId > installed!.fileId) } }
            if !patches.isEmpty { return ModUpdatePlan(targetRelease: latest, steps: patches, isIncrementalOnly: true) }
        }
        return ModUpdatePlan(targetRelease: latest, steps: [releases[base]] + releases[..<base].reversed().filter { $0.isPatch && sameReleaseGroup(latest, $0) }, isIncrementalOnly: false)
    }

    static func computeStepsForSelectedRelease(_ releases: [ModReleaseEntry], targetIdx: Int, hasMod: Bool, installed: InstalledModInfo?, includeBaseForPatch: Bool, canRestore: Bool = true) -> [ModReleaseEntry] {
        guard releases.indices.contains(targetIdx) else { return [] }
        let target = releases[targetIdx]
        let switchingGroup = hasMod && groupsConflict(target.group, installed?.group)
        if switchingGroup && !canRestore { return [] }
        if !target.isPatch { return [target] }
        let base = releases.indices.first { $0 > targetIdx && !releases[$0].isPatch && sameReleaseGroup(target, releases[$0]) }
        if switchingGroup && (!includeBaseForPatch || base == nil) { return [] }
        if !includeBaseForPatch || base == nil {
            if hasMod, let base, !switchingGroup {
                let index = matchInstalledIndex(releases, installed)
                if index > targetIdx && index <= base && sameReleaseGroup(target, releases[index]) {
                    let patches = releases[targetIdx..<index].reversed().filter { $0.isPatch && sameReleaseGroup(target, $0) }
                    if !patches.isEmpty { return patches }
                }
            }
            return [target]
        }
        return [releases[base!]] + releases[targetIdx..<base!].reversed().filter { $0.isPatch && sameReleaseGroup(target, $0) }
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
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any], let rows = json["releases"] as? [Any] else { throw ModError.message(hubText("获取失败，请检查网络")) }
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*")
        var releases: [ModReleaseEntry] = []
        for (index, row) in rows.enumerated() {
            guard let row = row as? [String: Any] else { continue }
            let key = modString(row, "key"), file = modString(row, "fileName")
            let version = sanitizeParenFree(modString(row, "version"), multiline: false)
            guard !key.isEmpty, !file.isEmpty, !version.isEmpty, ModArchiveExtractor.isSupportedArchiveName(file) else { continue }
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
        guard !releases.isEmpty else { throw ModError.message(hubText("暂无可用版本")) }
        for index in releases.indices where releases[index].isPatch {
            if let base = releases.indices.first(where: { $0 > index && !releases[$0].isPatch && sameReleaseGroup(releases[index], releases[$0]) }) {
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
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw ModError.message(hubText("获取失败，请检查网络")) }
        return try parseModCatalog(data, cdnBaseURL: game.overlayURL)
    }
}
