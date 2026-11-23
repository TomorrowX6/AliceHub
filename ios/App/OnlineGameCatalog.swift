import Foundation

struct OnlineGame: Identifiable {
    let id: String
    let title: String
    let directory: String
    var artwork: String { id == "escalayerreboot" ? "escalayer_reboot" : id }
    var baseURL: URL { URL(string: DownloadRoute.current.gameBaseURLString(for: id) + "/")! }

    static let all: [Self] = [
        Self(id: "rance10", title: "兰斯10 决战", directory: "Rance10"),
        Self(id: "dohnadohna", title: "多娜多娜 一起来干坏事吧", directory: "dohnadohna"),
        Self(id: "evenicle", title: "夏娃年代记", directory: "Evenicle"),
        Self(id: "evenicle2", title: "夏娃年代记2", directory: "Evenicle2"),
        Self(id: "sengokurance", title: "战国兰斯", directory: "SengokuRance"),
        Self(id: "rance01", title: "兰斯01 寻找小光", directory: "Rance01"),
        Self(id: "rance02", title: "兰斯02 反叛的少女们", directory: "Rance02"),
        Self(id: "rance03", title: "兰斯03 利萨斯陷落", directory: "Rance03"),
        Self(id: "rance04", title: "兰斯IV 教团的遗产", directory: "Rance04"),
        Self(id: "rance41", title: "兰斯4.1 拯救制药厂！", directory: "Rance41"),
        Self(id: "rance42", title: "兰斯4.2 天使组", directory: "Rance42"),
        Self(id: "rance5d", title: "兰斯5D", directory: "Rance5D"),
        Self(id: "rance06", title: "兰斯06 赛斯崩坏", directory: "Rance06"),
        Self(id: "rance08", title: "兰斯08 兰斯Quest Magnum", directory: "Rance08"),
        Self(id: "rance09", title: "兰斯09 赫尔曼革命", directory: "Rance09"),
        Self(id: "escalayer", title: "超昂天使 艾斯卡蕾雅", directory: "Escalayer"),
        Self(id: "escalayerreboot", title: "超昂天使 艾斯卡蕾雅 Reboot", directory: "EscalayerReboot"),
        Self(id: "galzoo", title: "GALZOO艾兰德", directory: "GALZOO"),
        Self(id: "ixseal", title: "超昂神骑", directory: "Ixseal"),
        Self(id: "haruka", title: "超昂闪忍遥", directory: "Haruka"),
        Self(id: "tsumashibori", title: "妻管严", directory: "Tsumashibori"),
        Self(id: "tsumamigui", title: "妻中蜜", directory: "Tsumamigui"),
        Self(id: "tsumamigui3", title: "妻中蜜３", directory: "Tsumamigui3"),
        Self(id: "kichikuou", title: "鬼畜王兰斯", directory: "KichikuRance"),
        Self(id: "daiakuji", title: "大恶司", directory: "Daiakuji"),
        Self(id: "daibanchou", title: "大番长 -Big Bang Age-", directory: "Daibanchou"),
        Self(id: "daiteikoku", title: "大帝国", directory: "Daiteikoku"),
        Self(id: "kaerunyo", title: "回家扭·帕扭", directory: "Kaerunyo"),
        Self(id: "atlachnacha", title: "阿特拉克=纳克亚", directory: "AtlachNacha"),
        Self(id: "reishiki", title: "零式", directory: "Reishiki"),
        Self(id: "haruurare", title: "校内赤线区", directory: "Haruurare"),
        Self(id: "okusamajun", title: "太太的恢复术 纯爱篇", directory: "OkusamaJun"),
        Self(id: "okusamantr", title: "太太的恢复术 夺爱篇", directory: "OkusamaNtr"),
        Self(id: "evenicle2rance", title: "夏娃年代记2 兰斯版", directory: "Evenicle2Rance"),
        Self(id: "eveniclegaiden", title: "夏娃年代记外典", directory: "EvenicleGaiden"),
        Self(id: "waidonyo", title: "异空窟探险", directory: "Waidonyo"),
        Self(id: "rance6after", title: "兰斯06 后日谈", directory: "Rance6After"),
        Self(id: "yakata2", title: "爱丽丝之馆2", directory: "Yakata2"),
        Self(id: "sdpsmaria", title: "Super D.P.S 玛利亚和坎潘", directory: "SuperDpsMaria"),
        Self(id: "toushin3", title: "斗神都市3", directory: "Toushin3"),
        Self(id: "oyakorankan", title: "母娘乱馆", directory: "Oyakorankan"),
        Self(id: "yorugakuru", title: "邪夜将至 Remastered", directory: "Yorugakuru"),
        Self(id: "rance1digest", title: "兰斯1 摘要版", directory: "Rance1Digest"),
        Self(id: "maman", title: "再婚妈妈", directory: "Maman")
    ]
}

struct OnlineGameManifest: Decodable {
    struct Entry: Decodable {
        let path: String
        let key: String
        let size: Int64
        let sha256: String
        let extract: Bool?
    }
    let gameName: String
    let baseName: String
    let files: [Entry]
    var totalBytes: Int64 { files.reduce(0) { $0 + $1.size } }

    static func safePath(_ path: String) -> Bool {
        !path.isEmpty && !path.contains("\\") && !path.contains(":") &&
        !path.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) &&
        path.split(separator: "/", omittingEmptySubsequences: false).allSatisfy {
            !$0.isEmpty && $0 != "." && $0 != ".."
        }
    }

    static func parse(_ data: Data) throws -> Self {
        guard data.count <= 4 * 1048576 else { throw ModError.message("下载清单过大") }
        let manifest = try JSONDecoder().decode(Self.self, from: data)
        guard safePath(manifest.baseName), !manifest.baseName.contains("/"), !manifest.baseName.hasPrefix("."),
              !manifest.files.isEmpty, manifest.files.count <= 10000 else {
            throw ModError.message("游戏清单无效")
        }
        var paths = Set<String>(), total: Int64 = 0
        for entry in manifest.files {
            let path = entry.path.precomposedStringWithCanonicalMapping.lowercased()
            let sum = total.addingReportingOverflow(entry.size)
            guard safePath(entry.path), safePath(entry.key), entry.size >= 0, !sum.overflow,
                  entry.sha256.range(of: "^[0-9a-fA-F]{64}$", options: .regularExpression) != nil,
                  paths.insert(path).inserted else { throw ModError.message("游戏清单包含不安全或重复的文件") }
            total = sum.partialValue
        }
        for path in paths {
            var parts = path.split(separator: "/"); parts.removeLast()
            while !parts.isEmpty {
                guard !paths.contains(parts.joined(separator: "/")) else { throw ModError.message("游戏清单文件路径冲突") }
                parts.removeLast()
            }
        }
        guard total > 0 else { throw ModError.message("游戏清单为空") }
        return manifest
    }

    static func fetch(from baseURL: URL) async throws -> Self {
        var request = URLRequest(url: baseURL.appendingPathComponent("manifest.json"),
                                 cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let response = response as? HTTPURLResponse, response.statusCode == 200 else {
            throw ModError.message("无法获取游戏清单，请检查网络后重试")
        }
        return try parse(data)
    }
}
