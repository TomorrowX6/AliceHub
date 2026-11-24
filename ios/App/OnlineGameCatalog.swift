import Foundation

struct OnlineGame: Identifiable {
    let id: String
    let title: String
    let directory: String
    let hasChinese: Bool
    var artwork: String { id == "escalayerreboot" ? "escalayer_reboot" : id }
    var baseURL: URL { URL(string: AliceHubCDN.gameBaseURLString(for: id) + "/")! }

    init(id: String, title: String, directory: String, hasChinese: Bool = false) {
        self.id = id
        self.title = title
        self.directory = directory
        self.hasChinese = hasChinese
    }

    private static let byArtworkKey: [String: OnlineGame] = Dictionary(uniqueKeysWithValues: all.map { ($0.artwork, $0) })
    private static let byDirectory = Dictionary(uniqueKeysWithValues: all.map { ($0.directory.lowercased(), $0) })

    static func catalogEntry(for game: Game) -> OnlineGame? {
        game.artworkKey.flatMap { byArtworkKey[$0] } ?? byDirectory[game.name.lowercased()]
    }

    func displayTitle(in language: HubLanguage, bundle: Bundle = .main) -> String {
        language.text(title, bundle: bundle)
    }

    static func displayTitle(for game: Game, in language: HubLanguage, bundle: Bundle = .main) -> String {
        let name = game.resolvedDisplayName
        guard let key = game.artworkKey, let entry = byArtworkKey[key] else { return name }
        let title = entry.displayTitle(in: language, bundle: bundle)
        for base in [entry.title, entry.directory] {
            if name.caseInsensitiveCompare(base) == .orderedSame { return title }
            if let prefix = name.range(of: base + " [", options: [.anchored, .caseInsensitive]),
               name.hasSuffix("]") {
                return title + " [" + name[prefix.upperBound...]
            }
        }
        return name
    }

    static func chineseBadgeKey(hasChinese: Bool) -> String {
        hasChinese ? "熟肉" : "生肉"
    }

    static func matchesChineseBadge(_ query: String, hasChinese: Bool, in language: HubLanguage, bundle: Bundle = .main) -> Bool {
        let key = chineseBadgeKey(hasChinese: hasChinese)
        return query.localizedCaseInsensitiveCompare(key) == .orderedSame ||
            query.localizedCaseInsensitiveCompare(language.text(key, bundle: bundle)) == .orderedSame
    }

    static func hasLocalChineseMarkers(in directory: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: [.isRegularFileKey]
        ) else { return false }
        let fileNames = contents.compactMap { item -> String? in
            (try? item.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true ? item.lastPathComponent : nil
        }
        return GameLibrary.hasLocalChineseMarkers(fileNames: fileNames)
    }

    static func hasChinese(for game: Game) -> Bool {
        if game.hasInstalledCnPatch || game.hasLocalChineseMarkers {
            return true
        }
        if game.hasExplicitNonChineseEdition {
            return false
        }
        if let entry = catalogEntry(for: game) {
            return entry.hasChinese
        }
        return false
    }

    static func defaultSorted(_ list: [OnlineGame]) -> [OnlineGame] {
        let cn = list.filter(\.hasChinese)
        let raw = list.filter { !$0.hasChinese }
        return cn + raw
    }

    static func defaultSortedInstalled(_ list: [Game]) -> [Game] {
        let cn = list.filter { hasChinese(for: $0) }
        let raw = list.filter { !hasChinese(for: $0) }
        return cn + raw
    }

    static let all: [Self] = [
        Self(id: "rance10", title: "兰斯10 决战", directory: "Rance10", hasChinese: true),
        Self(id: "dohnadohna", title: "多娜多娜 一起来干坏事吧", directory: "dohnadohna", hasChinese: true),
        Self(id: "evenicle", title: "夏娃年代记", directory: "Evenicle", hasChinese: true),
        Self(id: "evenicle2", title: "夏娃年代记2", directory: "Evenicle2", hasChinese: true),
        Self(id: "sengokurance", title: "战国兰斯", directory: "SengokuRance", hasChinese: true),
        Self(id: "rance01", title: "兰斯01 寻找小光", directory: "Rance01", hasChinese: true),
        Self(id: "rance02", title: "兰斯02 反叛的少女们", directory: "Rance02", hasChinese: true),
        Self(id: "rance03", title: "兰斯03 利萨斯陷落", directory: "Rance03", hasChinese: true),
        Self(id: "rance04", title: "兰斯IV 教团的遗产", directory: "Rance04", hasChinese: true),
        Self(id: "rance41", title: "兰斯4.1 拯救制药厂！", directory: "Rance41", hasChinese: true),
        Self(id: "rance42", title: "兰斯4.2 天使组", directory: "Rance42", hasChinese: true),
        Self(id: "rance5d", title: "兰斯5D", directory: "Rance5D", hasChinese: true),
        Self(id: "rance06", title: "兰斯06 赛斯崩坏", directory: "Rance06", hasChinese: true),
        Self(id: "rance08", title: "兰斯08 兰斯Quest Magnum", directory: "Rance08", hasChinese: true),
        Self(id: "rance09", title: "兰斯09 赫尔曼革命", directory: "Rance09", hasChinese: true),
        Self(id: "escalayer", title: "超昂天使 艾斯卡蕾雅", directory: "Escalayer"),
        Self(id: "escalayerreboot", title: "超昂天使 艾斯卡蕾雅 Reboot", directory: "EscalayerReboot", hasChinese: true),
        Self(id: "galzoo", title: "GALZOO艾兰德", directory: "GALZOO", hasChinese: true),
        Self(id: "ixseal", title: "超昂神骑", directory: "Ixseal", hasChinese: true),
        Self(id: "haruka", title: "超昂闪忍遥", directory: "Haruka"),
        Self(id: "tsumashibori", title: "妻管严", directory: "Tsumashibori", hasChinese: true),
        Self(id: "tsumamigui", title: "妻中蜜", directory: "Tsumamigui", hasChinese: true),
        Self(id: "tsumamigui3", title: "妻中蜜３", directory: "Tsumamigui3", hasChinese: true),
        Self(id: "kichikuou", title: "鬼畜王兰斯", directory: "KichikuRance", hasChinese: true),
        Self(id: "daiakuji", title: "大恶司", directory: "Daiakuji", hasChinese: true),
        Self(id: "daibanchou", title: "大番长 -Big Bang Age-", directory: "Daibanchou", hasChinese: true),
        Self(id: "daiteikoku", title: "大帝国", directory: "Daiteikoku", hasChinese: true),
        Self(id: "kaerunyo", title: "回家扭·帕扭", directory: "Kaerunyo", hasChinese: true),
        Self(id: "atlachnacha", title: "阿特拉克=纳克亚", directory: "AtlachNacha", hasChinese: true),
        Self(id: "reishiki", title: "零式", directory: "Reishiki", hasChinese: true),
        Self(id: "haruurare", title: "校内赤线区", directory: "Haruurare", hasChinese: true),
        Self(id: "okusamajun", title: "太太的恢复术 纯爱篇", directory: "OkusamaJun", hasChinese: true),
        Self(id: "okusamantr", title: "太太的恢复术 夺爱篇", directory: "OkusamaNtr", hasChinese: true),
        Self(id: "evenicle2rance", title: "夏娃年代记2 兰斯版", directory: "Evenicle2Rance", hasChinese: true),
        Self(id: "eveniclegaiden", title: "夏娃年代记外典", directory: "EvenicleGaiden", hasChinese: true),
        Self(id: "waidonyo", title: "异空窟探险", directory: "Waidonyo", hasChinese: true),
        Self(id: "rance6after", title: "兰斯06 后日谈", directory: "Rance6After", hasChinese: true),
        Self(id: "yakata2", title: "爱丽丝之馆2", directory: "Yakata2", hasChinese: true),
        Self(id: "sdpsmaria", title: "Super D.P.S 玛利亚和坎潘", directory: "SuperDpsMaria", hasChinese: true),
        Self(id: "toushin3", title: "斗神都市3", directory: "Toushin3", hasChinese: true),
        Self(id: "oyakorankan", title: "母娘乱馆", directory: "Oyakorankan", hasChinese: true),
        Self(id: "yorugakuru", title: "邪夜将至 Remastered", directory: "Yorugakuru", hasChinese: true),
        Self(id: "rance1digest", title: "兰斯1 摘要版", directory: "Rance1Digest", hasChinese: true),
        Self(id: "maman", title: "再婚妈妈", directory: "Maman", hasChinese: true),
        Self(id: "eveniclerance", title: "夏娃年代记 兰斯版", directory: "EvenicleRance", hasChinese: true),
        Self(id: "haharanman", title: "母烂漫", directory: "Haharanman", hasChinese: true),
        Self(id: "tsumamigui2", title: "妻中蜜2", directory: "Tsumamigui2", hasChinese: true),
        Self(id: "tsumamigui3jun", title: "妻中蜜3 番外 纯爱篇", directory: "Tsumamigui3Jun", hasChinese: true),
        Self(id: "tsumamigui3ntr", title: "妻中蜜3 番外 寝取篇", directory: "Tsumamigui3Ntr", hasChinese: true),
        Self(id: "diabolique", title: "魔鬼恋曲 DiaboLiQuE", directory: "DiaboLiQuE"),
        Self(id: "gakuenking", title: "学园KING", directory: "GakuenKING"),
        Self(id: "gakuenkingex", title: "学园KING 番外篇", directory: "GakuenKINGEx"),
        Self(id: "oudouyuusha", title: "王道勇者", directory: "OudouYuusha"),
        Self(id: "toushin1", title: "斗神都市", directory: "Toushin1"),
        Self(id: "toushin2", title: "斗神都市II", directory: "Toushin2"),
        Self(id: "darcrows", title: "暗鸦 DARCROWS", directory: "DARCROWS", hasChinese: true),
        Self(id: "ikusamiko", title: "战巫女", directory: "IkusaMiko"),
        Self(id: "onlyyou", title: "Only You 世纪末的朱丽叶们", directory: "OnlyYou"),
        Self(id: "sanbiki", title: "战国兰斯 三匹が斬ったり突いたり燃やしたり", directory: "Sanbiki", hasChinese: true),
        Self(id: "persiom", title: "PERSIOM 约定汇聚之所", directory: "PERSIOM"),
        Self(id: "seeinao", title: "SeeIn青 ～Scene AO～", directory: "SeeInAO"),
        Self(id: "hushaby", title: "HUSHABY BABY", directory: "HushabyBaby"),
        Self(id: "ayumi", title: "步美物语", directory: "AyumiChan"),
        Self(id: "cielclaire", title: "残酷的爱恋", directory: "CielClaire"),
        Self(id: "nightdemon", title: "梦鬼 Night Demon", directory: "NightDemon"),
        Self(id: "katsumi", title: "不靠谱的胜美大夫", directory: "Katsumi"),
        Self(id: "drstop", title: "Dr.STOP!", directory: "DrStop"),
        Self(id: "dpsall", title: "D.P.S. 全部", directory: "DpsAll"),
        Self(id: "koredps", title: "这是D.P.S.？", directory: "KoreDps"),
        Self(id: "kaerualice", title: "蛙国爱丽丝", directory: "KaeruAlice"),
        Self(id: "prostudentg", title: "职业学生G", directory: "ProstudentG"),
        Self(id: "prostudentgood", title: "职业学生Good", directory: "ProstudentGood"),
        Self(id: "pastelchime", title: "粉彩铃音", directory: "PastelChime"),
        Self(id: "pastelchime2", title: "粉彩铃音 Continue", directory: "PastelChime2"),
        Self(id: "yakata3", title: "爱丽丝之馆3", directory: "Yakata3"),
        Self(id: "dalk", title: "DALK", directory: "DALK"),
        Self(id: "ningengari", title: "人间狩猎", directory: "NingenGari"),
        Self(id: "paschacpp", title: "粉彩铃音 C++", directory: "PaschaCpp"),
        Self(id: "pascha3", title: "粉彩铃音3 封神追寻者", directory: "Pascha3", hasChinese: true),
        Self(id: "mamatoto", title: "妈妈托托 ～a record of war～", directory: "Mamatoto"),
        Self(id: "onlyyourecross", title: "Only You -Re Cross-", directory: "OnlyYouReCross"),
        Self(id: "mamorigami", title: "守护神大人", directory: "Mamorigami"),
        Self(id: "ambivalenz", title: "AmbivalenZ -二律背反-", directory: "AmbivalenZ"),
        Self(id: "funnybee", title: "宇宙快盗Funny Bee", directory: "FunnyBee"),
        Self(id: "toushin2sp", title: "斗神都市II 然后，从那以后…", directory: "Toushin2Sp"),
        Self(id: "mugenhouyou", title: "梦幻泡影", directory: "MugenHouyou"),
        Self(id: "otomesenki", title: "乙女战记", directory: "OtomeSenki"),
        Self(id: "gakuenhyouryuu", title: "学园漂流战记", directory: "GakuenHyouryuu"),
        Self(id: "maidnosusume", title: "女仆的推荐", directory: "MaidNoSusume"),
        Self(id: "dalkgaiden", title: "DALK外传", directory: "DalkGaiden"),
        Self(id: "mamanyonyo", title: "妈妈软软", directory: "Mamanyonyo"),
        Self(id: "majonoshokuzai", title: "魔女的赎罪", directory: "MajoNoShokuzai"),
        Self(id: "saboten", title: "贪心仙人掌", directory: "Saboten"),
        Self(id: "doublesensei", title: "双倍教师生活", directory: "DoubleSensei"),
        Self(id: "ojousama", title: "让大小姐言听计从的游戏", directory: "Ojousama"),
        Self(id: "edoranze", title: "江户乱世 ～Come on!水户姬大人～", directory: "Edoranze"),
        Self(id: "vanish", title: "Vanish! 消失的王国", directory: "Vanish"),
        Self(id: "hokenshitsu", title: "只属于我的保健室", directory: "Hokenshitsu"),
        Self(id: "kamunagara", title: "神武 -卡姆纳加拉-", directory: "Kamunagara"),
        Self(id: "erumeiflower", title: "艾尔梅花", directory: "ErumeiFlower"),
        Self(id: "erumeiwakare", title: "艾尔梅花 ～别离之夜～", directory: "ErumeiWakare"),
        Self(id: "manana1", title: "超级魔法少女玛娜娜 Vol.1", directory: "Manana1"),
        Self(id: "manana2", title: "超级魔法少女玛娜娜 Vol.2", directory: "Manana2"),
        Self(id: "manana3", title: "超级魔法少女玛娜娜 Vol.3", directory: "Manana3"),
        Self(id: "manana4", title: "超级魔法少女玛娜娜 Vol.4", directory: "Manana4"),
        Self(id: "manana5", title: "超级魔法少女玛娜娜 Vol.5", directory: "Manana5"),
        Self(id: "tsumasakura", title: "妻们的樱贝", directory: "TsumaSakura"),
        Self(id: "shamanssanctuary", title: "巫女的圣域", directory: "ShamansSanctuary"),
        Self(id: "alivez", title: "AliveZ", directory: "AliveZ"),
        Self(id: "dungeonsdolls", title: "DUNGEONS & DOLLS", directory: "DungeonsDolls"),
        Self(id: "suzumiya", title: "铃宫刑务娼馆", directory: "Suzumiya"),
        Self(id: "shimaima", title: "姐妹。", directory: "Shimaima"),
        Self(id: "daibanchougaiden", title: "大番长外传", directory: "DaibanchouGaiden"),
        Self(id: "cosplaylemon", title: "Cosplay柠檬", directory: "CosplayLemon"),
        Self(id: "whiphoney", title: "奶油哈尼", directory: "WhipHoney"),
        Self(id: "onimusu", title: "鬼女", directory: "Onimusu"),
        Self(id: "progrx", title: "职业GRX", directory: "ProGrx"),
        Self(id: "dpsvern", title: "D.P.S. Ver_N", directory: "DpsVerN"),
        Self(id: "sukiirono", title: "透色", directory: "Sukiirono"),
        Self(id: "girls", title: "Girls", directory: "Girls"),
        Self(id: "debut", title: "DEBUT", directory: "Debut"),
        Self(id: "harukavsesca", title: "超昂闪忍遥 遥VS艾斯卡蕾雅", directory: "HarukaVsEsca"),
        Self(id: "harukaninja", title: "超昂闪忍遥 疾风！？忍者大作战", directory: "HarukaNinja"),
        Self(id: "megane", title: "眼镜梅与暑假", directory: "Megane"),
        Self(id: "rachelsakura", title: "蕾切尔的樱贝", directory: "RachelSakura"),
        Self(id: "sabakunika", title: "沙漠的妮卡", directory: "SabakuNika"),
        Self(id: "vanishpalm", title: "Vanish! 掌中奇缘", directory: "VanishPalm")
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
        guard data.count <= 4 * 1048576 else { throw ModError.message(hubText("下载清单过大")) }
        let manifest = try JSONDecoder().decode(Self.self, from: data)
        guard safePath(manifest.baseName), !manifest.baseName.contains("/"), !manifest.baseName.hasPrefix("."),
              !manifest.files.isEmpty, manifest.files.count <= 10000 else {
            throw ModError.message(hubText("游戏清单无效"))
        }
        var paths = Set<String>(), total: Int64 = 0
        for entry in manifest.files {
            let path = entry.path.precomposedStringWithCanonicalMapping.lowercased()
            let sum = total.addingReportingOverflow(entry.size)
            guard safePath(entry.path), safePath(entry.key), entry.size >= 0, !sum.overflow,
                  entry.sha256.range(of: "^[0-9a-fA-F]{64}$", options: .regularExpression) != nil,
                  paths.insert(path).inserted else { throw ModError.message(hubText("游戏清单包含不安全或重复的文件")) }
            total = sum.partialValue
        }
        for path in paths {
            var parts = path.split(separator: "/"); parts.removeLast()
            while !parts.isEmpty {
                guard !paths.contains(parts.joined(separator: "/")) else { throw ModError.message(hubText("游戏清单文件路径冲突")) }
                parts.removeLast()
            }
        }
        guard total > 0 else { throw ModError.message(hubText("游戏清单为空")) }
        return manifest
    }

    static func fetch(from baseURL: URL) async throws -> Self {
        var request = URLRequest(url: baseURL.appendingPathComponent("manifest.json"),
                                 cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let response = response as? HTTPURLResponse, response.statusCode == 200 else {
            throw ModError.message(hubText("无法获取游戏清单，请检查网络后重试"))
        }
        return try parse(data)
    }
}
