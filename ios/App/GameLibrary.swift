import Foundation
import Combine
import CoreText
import CryptoKit
import Darwin

enum GameEngine: String {
    case legacySystem3 = "System 3"
    case system3 = "System 3.x"
    case system4 = "System 4"
}

typealias GameLaunchInfo = (engine: GameEngine, target: URL, system39: Bool)

struct Game: Identifiable, Equatable {
    let url: URL
    let displayName: String
    let resolvedDisplayName: String
    let engine: GameEngine
    let artworkKey: String?
    let isRance04: Bool
    let isRance5d: Bool
    let hasLocalChineseMarkers: Bool
    let hasExplicitNonChineseEdition: Bool
    let hasInstalledCnPatch: Bool
    var id: String { url.lastPathComponent }
    var name: String { url.lastPathComponent }

    init(url: URL) {
        let meta = GameLibrary.inspect(url)
        self.url = url
        self.displayName = meta.displayName
        self.resolvedDisplayName = GameLibrary.titleResolver?(url, meta.displayName) ?? meta.displayName
        self.engine = meta.engine
        self.artworkKey = meta.artworkKey
        self.isRance04 = meta.isRance04
        self.isRance5d = meta.isRance5d
        self.hasLocalChineseMarkers = meta.hasLocalChineseMarkers
        self.hasExplicitNonChineseEdition = meta.hasExplicitNonChineseEdition
        self.hasInstalledCnPatch = meta.hasInstalledCnPatch
    }

    fileprivate init(url: URL, meta: GameLibrary.InspectedGame) {
        self.url = url
        self.displayName = meta.displayName
        self.resolvedDisplayName = GameLibrary.titleResolver?(url, meta.displayName) ?? meta.displayName
        self.engine = meta.engine
        self.artworkKey = meta.artworkKey
        self.isRance04 = meta.isRance04
        self.isRance5d = meta.isRance5d
        self.hasLocalChineseMarkers = meta.hasLocalChineseMarkers
        self.hasExplicitNonChineseEdition = meta.hasExplicitNonChineseEdition
        self.hasInstalledCnPatch = meta.hasInstalledCnPatch
    }
}

enum LibraryError: LocalizedError {
    case notGame, archiveNotGame, alreadyExists, resourcePath, logFile, audioStopping
    var errorDescription: String? {
        switch self {
        case .notGame: return hubText("所选文件夹不是 System 3.x / System 4 游戏。请确认根目录包含游戏的 INI、AIN、*SA.ALD、ADISK.ALD 或 ADISK.DAT 和 ACG.DAT 文件。")
        case .archiveNotGame: return hubText("压缩包或光盘镜像中未找到 System 3.x / System 4 游戏。")
        case .alreadyExists: return hubText("已存在同名游戏文件夹，请先删除或重命名后再导入。")
        case .resourcePath: return hubText("无法找到游戏引擎资源。")
        case .logFile: return hubText("无法创建运行日志。")
        case .audioStopping: return hubText("音频仍在关闭，请稍后重试。")
        }
    }
}

final class GameLibrary: ObservableObject {
    // Registered by the app entrypoint to apply ModOverlay display names without coupling
    // GameLibrary to ModOverlay in standalone library checks.
    static var titleResolver: ((URL, String) -> String)? = nil
    // Registered by the app entrypoint to reuse InstalledModInfo.read without coupling
    // GameLibrary to ModCatalog in standalone library checks.
    static var cnPatchResolver: ((URL) -> Bool)? = nil
    // Registered by the app entrypoint to resolve OnlineGame catalog IDs without coupling
    // GameLibrary to OnlineGameCatalog in standalone library checks.
    static var catalogIDResolver: ((Game) -> String?)? = nil

    private static func resolvedCatalogID(for game: Game) -> String? {
        if let resolver = catalogIDResolver {
            return resolver(game)
        }
        guard let key = game.artworkKey else { return nil }
        return key == "escalayer_reboot" ? "escalayerreboot" : key
    }

    fileprivate struct InspectedGame {
        let isValidGame: Bool
        let displayName: String
        let engine: GameEngine
        let artworkKey: String?
        let isRance04: Bool
        let isRance5d: Bool
        let hasLocalChineseMarkers: Bool
        let hasExplicitNonChineseEdition: Bool
        let hasInstalledCnPatch: Bool
    }

    private static let chineseMarkerAssetExtensions: Set<String> = [
        "ain", "afa", "ald", "chs", "ex", "fnl", "ini", "dat", "ttf", "otf"
    ]

    static func hasLocalChineseMarkers<S: Sequence>(fileNames: S) -> Bool where S.Element == String {
        for rawName in fileNames {
            let lower = rawName.lowercased()
            if lower == "alicestart.chs" || lower == "chs.tbl" || lower == "alicecn.ain" ||
                lower == "haru_c.ain" || lower == "gbk.ain" || lower == "simhei.ttf" ||
                lower == "uif_config.json" ||
                lower.hasSuffix("_chs.ald") || lower.hasSuffix("chs.afa") ||
                lower.hasSuffix("chs2.afa") || lower.hasSuffix("_cn.afa") ||
                lower.hasSuffix("_cn.ain") || lower.hasSuffix("_cn.ald") {
                return true
            }
            let ext = (lower as NSString).pathExtension
            guard chineseMarkerAssetExtensions.contains(ext) else { continue }
            if lower.hasPrefix("sourcehansanscn") || lower.contains("_cn_") ||
                lower.contains("汉化") || lower.contains("中文") {
                return true
            }
        }
        return false
    }

    static func hasLocalChineseMarkerDirectories(_ contents: [URL]) -> Bool {
        contents.contains { item in
            guard item.lastPathComponent.lowercased() == "nspic",
                  let values = try? item.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey]) else {
                return false
            }
            return values.isDirectory == true && values.isSymbolicLink != true
        }
    }

    static func hasLocalChineseMarkers(regularFiles: [URL], directoryContents: [URL] = []) -> Bool {
        hasLocalChineseMarkers(fileNames: regularFiles.map(\.lastPathComponent)) ||
            regularFiles.contains(where: isHarukaChineseScript) ||
            hasLocalChineseMarkerDirectories(directoryContents)
    }

    private static func isHarukaChineseScript(_ file: URL) -> Bool {
        // The cloud CN edition keeps the English INI and script filename.
        guard file.lastPathComponent.lowercased() == "haruka.ain",
              (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) == 2759127,
              let data = try? Data(contentsOf: file, options: .mappedIfSafe) else { return false }
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() ==
            "c05bb31b0e46c276c7766dd00a76e90367c65fa5d395d4cc45d11983e03ee576"
    }

    private static func hasInstalledCnPatch(_ url: URL, regularFiles: [URL]) -> Bool {
        if let resolver = cnPatchResolver {
            return resolver(url)
        }
        guard let meta = regularFiles.first(where: { $0.lastPathComponent == ".mod_version.json" }),
              let data = try? Data(contentsOf: meta), data.count <= 256 * 1024,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let version = json["version"] as? String,
              !version.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let kind = json["kind"] as? String else { return false }
        return kind.trimmingCharacters(in: .whitespacesAndNewlines) == "cn_patch"
    }

    private static let artworkAliases: [(String, [String])] = [
        ("escalayer_reboot", ["escalayerreboot", "reboot"]),
        ("evenicle2rance", ["evenicle2rance", "イブニクル2ランス", "イブニクル２ランス", "夏娃年代记2兰斯", "夏娃年代記2蘭斯", "evenicle2兰斯"]),
        ("evenicle2", ["evenicle2", "イブニクル2", "夏娃年代记2"]),
        ("eveniclerance", ["eveniclerance", "イブニクルランス", "夏娃年代记兰斯", "夏娃年代記蘭斯", "夏娃兰斯"]),
        ("eveniclegaiden", ["eveniclegaiden", "eveniclegaiten", "evenicleapc", "イブニクル外典", "夏娃年代记外典", "夏娃年代記外典"]),
        ("rance10", ["rance10", "rancex", "兰斯10", "ランス10"]),
        ("dohnadohna", ["dohnadohna", "多娜多娜", "ドーナドーナ"]),
        ("sanbiki", ["sanbiki", "三匹が", "三匹", "slash!thrust!burn!"]),
        ("sengokurance", ["sengoku", "戦国", "战国", "戰國", "rance7", "愴崙", "daisengoku", "大战国", "大戦国"]),
        ("rance1digest", ["rance1digest", "rance1.ain", "兰斯1摘要", "兰斯１摘要", "蘭斯1摘要", "ランス1ダイジェスト", "ランス１ダイジェスト"]),
        ("rance1classic", ["rance1classic", "rancesa.ald", "rancega.ald", "兰斯1原版", "兰斯1（原版）", "兰斯i寻找小光"]),
        ("rance2classic", ["rance2classic", "rance2sa.ald", "rance2ga.ald", "兰斯2原版", "兰斯2（原版）", "兰斯ii反叛的少女"]),
        ("rance3classic", ["rance3classic", "rance3sa.ald", "rance3ga.ald", "兰斯3原版", "兰斯3（原版）", "兰斯iii利萨斯陥落", "兰斯iii利萨斯陷落"]),
        ("rance01", ["rance01", "兰斯01", "ランス01"]),
        ("rance02", ["rance02", "兰斯02", "ランス02"]),
        ("rance03", ["rance03", "兰斯03", "ランス03"]),
        ("rance41", ["rance41", "rance4.1", "ran41sa", "兰斯4.1", "兰斯41", "ランス4.1", "拯救制药厂", "お薬工場"]),
        ("rance42", ["rance42", "rance4.2", "ran42sa", "兰斯4.2", "兰斯42", "ランス4.2", "天使组", "天使組", "エンジェル組"]),
        ("rance04", ["rance04", "rance4sa", "ranceiv", "兰斯iv", "兰斯4", "ランスiv", "ランス4", "教团的遗产", "教団の遺産"]),
        ("rance5d", ["rance5d", "r5dsa", "兰斯5d", "ランス5d", "ランス５ｄ"]),
        ("rance6after", ["rance6after", "ranceviaftermath", "rancevisonogo", "ランス6その後", "ランス６その後", "兰斯06后日谈", "兰斯6后日谈", "兰斯vi后日谈", "儔儞僗俇偦偺屻"]),
        ("rance06", ["rance06", "兰斯6", "ランス6"]),
        ("rance08", ["rance08", "rance8", "兰斯8", "ランスクエスト"]),
        ("rance09", ["rance09", "rance9", "兰斯9", "ランス9"]),
        ("kichikuou", ["kichiku", "鬼畜王"]),
        ("daiakuji", ["daiakuji", "大悪司", "大恶司"]),
        ("daibanchougaiden", ["daibanchougaiden", "大番長外伝", "大番长外传", "大番長外傳"]),
        ("daibanchou", ["daibanchou", "大番長", "大番长", "bigbangage"]),
        ("daiteikoku", ["daiteikoku", "大帝国", "大帝國"]),
        ("galzoo", ["galzoo"]), ("ixseal", ["ixseal", "イクシール"]),
        ("harukavsesca", ["harukavsesca", "ハルカvs", "ハルカｖｓ", "遥vs艾斯卡", "遙vs艾斯卡", "harukavsescalayer"]),
        ("harukaninja", ["harukaninja", "疾風!?忍者大作戦", "疾風！？忍者大作戦", "疾风!?忍者大作战", "疾风！？忍者大作战", "忍者大作戦", "忍者大作战", "shippuu"]),
        ("haruka", ["haruka", "ハルカ"]), ("tsumashibori", ["tsumashibori", "妻しぼり"]),
        ("tsumamigui3jun", ["tsumamigui3jun", "tsuma3sojunai", "妻みぐい3スピンオフ純愛", "妻みぐい３スピンオフ純愛", "妻みぐい3純愛", "妻中蜜3番外纯爱", "妻中蜜3纯爱"]),
        ("tsumamigui3ntr", ["tsumamigui3ntr", "tsuma3sonetorare", "妻みぐい3スピンオフ寝取", "妻みぐい３スピンオフ寝取", "妻みぐい3寝取", "妻中蜜3番外寝取", "妻中蜜3寝取"]),
        ("tsumamigui3", ["tsumamigui3", "妻みぐい3", "妻みぐい３", "妻中蜜3", "妻中蜜３"]),
        ("tsumamigui2", ["tsumamigui2", "妻みぐい2", "妻みぐい２", "妻中蜜2", "妻中蜜２", "嵢傒偖偄俀"]),
        ("tsumamigui", ["tsumamigui", "妻みぐい", "妻中蜜", "嵢傒偖偄"]),
        ("kaerualice", ["kaerualice", "かえるにょ国にょアリス", "国にょアリス", "蛙国爱丽丝", "蛙國愛麗絲"]),
        ("kaerunyo", ["kaerunyo", "かえるにょ", "ぱにょ", "回家扭"]),
        ("atlachnacha", ["atlachnacha", "atlach", "アトラク", "阿特拉克", "蜘蛛sa"]),
        ("reishiki", ["reishiki", "零式", "zeroshiki", "楇幃"]),
        ("haruurare", ["haruurare", "はるうられ", "haru_c", "校内赤线", "校内赤線"]),
        ("okusamajun", ["okusamajun", "太太的恢复术纯爱", "奥さまの回復術純愛", "纯爱篇", "純愛編"]),
        ("okusamantr", ["okusamantr", "太太的恢复术夺爱", "奥さまの回復術奪愛", "夺爱篇", "奪愛編"]),
        ("waidonyo", ["waidonyo", "わいどにょ", "异空窟探险", "異空窟探險"]),
        ("yakata456", ["yakata456", "456sa.ald", "４５６sa.ald", "456ga.ald", "４５６ga.ald", "アリスの館4", "アリスの館４", "aliceの館4", "aliceの館４", "爱丽丝之馆4", "愛麗絲之館4"]),
        ("yakata3", ["yakata3", "aliceの館3", "aliceの館３", "aliceの館iii", "アリスの館3", "アリスの館３", "アリスの館iii", "alicenoyakata3", "alicenoyakataiii", "爱丽丝之馆3", "爱丽丝之馆３", "愛麗絲之館3", "愛麗絲之館３"]),
        ("yakata2", ["yakata2", "aliceの館ii", "aliceの館2", "アリスの館2", "アリスの館２", "アリスの館ii", "alicenoyakata2", "alicenoyakataii", "爱丽丝之馆2", "爱丽丝之馆２", "愛麗絲之館2", "愛麗絲之館２"]),
        ("yakata1", ["yakata1", "aliceの館", "アリスの館", "alicenoyakata", "爱丽丝之馆", "愛麗絲之館"]),
        ("koredps", ["koredps", "これdps", "これｄｐｓ", "これd.p.s", "这是dps", "這是dps", "这是d.p.s", "這是d.p.s"]),
        ("dpsall", ["dpsall", "d.p.s.全部", "dps全部", "d.p.s.zenbu", "dpszenbu"]),
        ("sdpstono", ["sdpstono", "superdpstono", "遠野の森", "远野之森", "遠野之森", "tononomori"]),
        ("sdpskaizoku", ["sdpskaizoku", "superdpskaizoku", "海賊稼業", "海盗生涯", "海盜生涯", "kaizokukagyou"]),
        ("sdpsmaria", ["sdpsmaria", "superdpsmaria", "superdps", "sdps", "玛利亚和坎潘", "瑪利亞和坎潘", "マリアとカンパン"]),
        ("dpssgfahren", ["dpssgfahren", "fahrenfliegen", "fahnenfliegen", "飞翔的梦", "飛翔的夢"]),
        ("dpssgkatei", ["dpssgkatei", "家庭教師はステキなお仕事", "家庭教师是美妙的工作", "家庭教師是美妙的工作", "kateikyoushi"]),
        ("dpssgnobunaga", ["dpssgnobunaga", "信長の淫謀", "信长的淫谋", "信長的淫謀", "nobunaganoinbou"]),
        ("dpssg2antique", ["dpssg2antique", "antiquehouse", "古董屋"]),
        ("dpssg2ikenai", ["dpssg2ikenai", "いけない内科検診", "不妙的内科体检", "不妙的內科體檢", "naikakenshin"]),
        ("dpssg2akai", ["dpssg2akai", "朱い夜", "朱红之夜", "朱紅之夜", "akaiyoru"]),
        ("dpssg3rabbit", ["dpssg3rabbit", "rabbitp4p"]),
        ("dpssg3shinkon", ["dpssg3shinkon", "しんこんさんものがたり", "新婚物语", "新婚物語", "shinkonsan"]),
        ("dpssg3sotsugyou", ["dpssg3sotsugyou", "d.p.s.sgset3sotsugyou", "d.p.s.sgset3毕业", "d.p.s.sgset3畢業", "d.p.s.sgset3－卒業－"]),
        ("dps", ["dreamprogramsystem", "梦幻程序系统", "夢幻程序系統"]),
        ("toushin3", ["toushin3", "toushintoshi3", "toushintoshiiii", "闘神都市3", "闘神都市３", "闘神都市iii", "斗神都市3", "斗神都市３", "斗神都市iii", "鬥神都市3"]),
        ("toushin2sp", ["toushin2sp", "soshitesorekara", "そしてそれから", "然后从那以后", "然後從那以後"]),
        ("toushin2", ["toushin2", "toushintoshi2", "toushintoshiii", "tt2sa", "闘神都市2", "闘神都市２", "闘神都市ii", "斗神都市2", "斗神都市２", "斗神都市ii", "鬥神都市2"]),
        ("toushin1", ["toushin1", "toushinsa", "toushintoshi", "闘神都市", "斗神都市", "鬥神都市"]),
        ("diabolique", ["diabolique", "diabosa", "デアボリカ", "魔鬼恋曲"]),
        ("gakuenhyouryuu2", ["gakuenhyouryuu2", "学園漂流戦記第2", "学園漂流戦記2", "学园漂流战记第2", "学园漂流战记2", "戦記2sa.ald", "戦記２sa.ald"]),
        ("gakuenhyouryuu", ["gakuenhyouryuu", "学園漂流", "学园漂流", "學園漂流"]),
        ("gakuenkingex", ["gakuenkingex", "gakuenkingbangai", "学園番外編", "学園king番外", "学园king番外"]),
        ("gakuenking", ["gakuenking", "学園king", "学园king", "gakuen.com"]),
        ("oudouyuusha", ["oudouyuusha", "王道勇者", "王道sa"]),
        ("darcrows", ["darcrows", "himesa", "ダークロウズ", "暗鸦"]),
        ("ikusamiko", ["ikusamiko", "戦巫女", "战巫女", "vestalvirgin"]),
        ("onlyyourecross", ["onlyyourecross", "recross", "リクルス", "オンリーsa"]),
        ("onlyyou", ["onlyyou", "世紀末のジュリエット", "世纪末的朱丽叶"]),
        ("mamatoto", ["mamatoto", "ママトト", "妈妈托托", "媽媽托托"]),
        ("mamorigami", ["mamorigami", "守り神様", "守神sa", "守护神大人", "守護神大人"]),
        ("ambivalenz", ["ambivalenz", "二律背反", "niritsuhaihan"]),
        ("funnybee", ["funnybee", "ファニーbee", "宇宙快盗"]),
        ("mugenhouyou", ["mugenhouyou", "夢幻泡影", "梦幻泡影"]),
        ("otomesenki", ["otomesenki", "乙女戦記", "乙女战记", "乙女戰記"]),
        ("maidnosusume", ["maidnosusume", "メイドのススメ", "メイドsa", "女仆的推荐", "女僕的推薦"]),
        ("persiom", ["persiom", "約束の集う場所", "约定汇聚之所"]),
        ("seeinao", ["seeinao", "seein青", "イルカsa", "シーンao"]),
        ("hushaby", ["hushaby", "幽霊sa"]),
        ("ayumilive", ["ayumilive", "実写版", "jisshaban", "liveaction", "步美物语真人", "步美物語真人"]),
        ("ayumihint", ["ayumihint", "ヒントディスク", "步美物语提示", "步美物語提示"]),
        ("ayumi", ["ayumichan", "ayumi", "あゆみちゃん", "步美物语", "步美物語"]),
        ("cielclaire", ["cielclaire", "chairscruelles", "シェルクレイル", "逃避行sa", "残酷的爱恋", "殘酷的愛戀"]),
        ("nightdemon", ["nightdemon", "ナイトデーモン", "夢鬼", "梦鬼"]),
        ("katsumi", ["katsumi", "いけないかつみ", "かつみsa", "胜美大夫", "勝美大夫"]),
        ("drstop", ["drstop", "dr.stop", "dssa.ald"]),
        ("prostudentgood", ["prostudentgood", "prostu★dent", "sr猿", "ｓｒ猿", "ぷろすちゅ★でんと", "职业学生good", "職業學生good"]),
        ("progomake", ["progomake", "prostudentgomake", "ぷろすちゅーでんとgおまけ", "职业学生g附赠", "職業學生g附贈"]),
        ("prostudentg", ["prostudentg", "progcd", "ぷろすちゅーでんとg", "职业学生g", "職業學生g"]),
        ("pascha3", ["pascha3", "pastelchime3", "bindseeker", "パステルチャイム3", "パステルチャイム３", "バインドシーカー", "粉彩铃音3", "粉彩铃音３", "粉彩鈴音3", "封神追寻者", "pc3特典"]),
        ("paschacpp", ["paschacpp", "paschac++", "ぱすちゃc++", "ぱすちゃc＋＋", "ぱすてる+", "ぱすてる＋", "粉彩铃音c++", "粉彩鈴音c++"]),
        ("pastelchime2", ["pastelchime2", "pastelchimecontinue", "pastelcc", "ぱすてるチャイムcontinue", "ぱすてる2", "ぱすてる２", "粉彩铃音continue", "粉彩铃音2", "粉彩鈴音2"]),
        ("pastelchime", ["pastelchime", "ぱすてるチャイム", "ぱすてるsa", "粉彩铃音", "粉彩鈴音"]),
        ("dalkgaiden", ["dalkgaiden", "dalk外伝", "ｄａｌｋ外伝", "dalk外传", "dalk外傳"]),
        ("dalk", ["dalksa", "dalkga", "dalk"]),
        ("ningengari", ["ningengari", "人間狩り", "人间狩猎", "人間狩獵"]),
        ("mamanyonyo", ["mamanyonyo", "ままにょにょ", "妈妈软软", "媽媽軟軟", "mamanyodemo"]),
        ("majonoshokuzai", ["majonoshokuzai", "魔女の贖罪", "魔女的赎罪", "魔女的贖罪"]),
        ("saboten", ["saboten", "よくばりサボテン", "贪心仙人掌", "貪心仙人掌"]),
        ("doublesensei", ["doublesensei", "だぶる先生らいふ", "双倍教师生活", "雙倍教師生活"]),
        ("ojousama", ["ojousama", "お嬢様をいいなり", "お嬢様.ain", "お嬢様ga.ald", "让大小姐言听计从", "讓大小姐言聽計從"]),
        ("edoranze", ["edoranze", "エドランゼ", "水戸姫様", "江户乱世", "江戶亂世", "水户姬大人"]),
        ("vanishpalm", ["vanishpalm", "この手のひら", "konotenohira", "ばにしゅ！番外", "ばにしゅ!番外", "掌中奇缘", "掌中奇緣"]),
        ("vanish", ["vanish", "ばにしゅ", "おっぱいの消えた王国", "消失的王国", "消失的王國"]),
        ("hokenshitsu", ["hokenshitsu", "僕だけの保健室", "只属于我的保健室", "只屬於我的保健室", "hoken.alm"]),
        ("kamunagara", ["kamunagara", "カムナガラ", "神武.ain", "神武ga.ald", "神武ba.ald", "神武wa.ald", "神武"]),
        ("erumeiwakare", ["erumeiwakare", "wakarenoyoru", "別れの夜", "别离之夜", "別離之夜"]),
        ("erumeiflower", ["erumeiflower", "elmayflower", "エルメイフラワー", "艾尔梅花", "艾爾梅花"]),
        ("manana1", ["manana1", "mananavol1", "mananavol.1", "まななvol1", "まななｖｏｌ１", "まななvol.1", "まなな1", "玛娜娜vol.1", "玛娜娜vol1", "玛娜娜1"]),
        ("manana2", ["manana2", "mananavol2", "mananavol.2", "まななvol2", "まななｖｏｌ２", "まななvol.2", "まなな2", "玛娜娜vol.2", "玛娜娜vol2", "玛娜娜2"]),
        ("manana3", ["manana3", "mananavol3", "mananavol.3", "まななvol3", "まななｖｏｌ３", "まななvol.3", "まなな3", "玛娜娜vol.3", "玛娜娜vol3", "玛娜娜3"]),
        ("manana4", ["manana4", "mananavol4", "mananavol.4", "まななvol4", "まななｖｏｌ４", "まななvol.4", "まなな4", "玛娜娜vol.4", "玛娜娜vol4", "玛娜娜4"]),
        ("manana5", ["manana5", "mananavol5", "mananavol.5", "まななvol5", "まななｖｏｌ５", "まななvol.5", "まなな5", "玛娜娜vol.5", "玛娜娜vol5", "玛娜娜5"]),
        ("tsumasakura", ["tsumasakura", "tsumatachinosakuragai", "妻たちの桜貝", "妻们的樱贝", "妻們的櫻貝"]),
        ("shamanssanctuary", ["shamanssanctuary", "shaman'ssanctuary", "しゃーまんず", "さんくちゅあり", "まんくちゅ", "巫女の聖域", "巫女的圣域", "巫女的聖域"]),
        ("alivez", ["alivez", "ａｌｉｖｅｚ"]),
        ("dungeonsdolls", ["dungeonsdolls", "dungeons&dolls", "ドールズ"]),
        ("suzumiya", ["suzumiya", "鈴宮刑務娼館", "铃宫刑务娼馆"]),
        ("shimaima", ["shimaima", "しまいま"]),
        ("cosplaylemon", ["cosplaylemon", "こすぷれれもん", "コスプレれもん", "cosplay柠檬", "cosplay檸檬"]),
        ("whiphoney", ["whiphoney", "ほいっぷハニー", "ホイップハニー", "奶油哈尼"]),
        ("onimusu", ["onimusu", "オニむス"]),
        ("progrx", ["progrx", "ぷろgrx", "ぷろｇｒｘ", "职业grx", "職業grx"]),
        ("dpsvern", ["dpsvern", "d.p.s.vern", "でーぴーえす"]),
        ("sukiirono", ["sukiirono", "sukiiro", "透色"]),
        ("girls", ["girls.ain", "ｇｉｒｌｓ.ain", "girlsga.ald", "ｇｉｒｌｓga.ald"]),
        ("debut", ["debut.ain", "ｄｅｂｕｔ.ain", "debutga.ald", "ｄｅｂｕｔga.ald"]),
        ("megane", ["megane", "眼鏡のメイ", "眼镜梅与暑假", "眼鏡梅與暑假"]),
        ("rachelsakura", ["rachelsakura", "rachellnosakuragai", "レイチェルの桜貝", "蕾切尔的樱贝", "蕾切爾的櫻貝"]),
        ("sabakunika", ["sabakunika", "sabakunonikka", "砂漠のニッカ", "沙漠的妮卡"]),
        ("bladebriders", ["bladebriders", "blade☆briders", "burebura", "ぶれいど", "ブライダーズ", "武想少女隊", "武想少女队"]),
        ("dorapeko", ["dorapeko", "drapeko", "どらぺこ", "おねだりドラゴン", "饥饿龙娘", "飢餓龍娘"]),
        ("haniwapetit", ["haniwapetit", "ハニワ闇開発室", "闇開発室ぷち", "哈尼瓦暗黑开发室", "哈尼瓦闇開發室"]),
        ("uminiochite", ["uminiochite", "蒼海に堕ちて", "蒼海sa.ald", "坠入苍海", "墜入蒼海"]),
        ("yousei", ["yousei", "妖精sa.ald", "妖精ga.ald"]),
        ("tobira", ["tobira", "[扉]", "［扉］", "[扉]sa.ald", "［扉］sa.ald", "[扉]ga.ald", "［扉］ga.ald"]),
        ("buroburo", ["buroburo", "ぶろぶろ", "壁sa.ald", "噗咯噗咯"]),
        ("kudakero", ["kudakero", "当たって!!kudakero", "当砕sa.ald", "撞碎吧"]),
        ("littlevampire", ["littlevampire", "リトルヴァンパイア", "小吸血鬼"]),
        ("intruder", ["intruder", "桜屋敷の探索", "樱屋敷的探索", "櫻屋敷的探索"]),
        ("crescent", ["crescentmoongirl", "crescentmoon", "クレセントムーン", "新月少女"]),
        ("tengu", ["abunaitengu", "あぶないてんぐ", "あぶない天狗", "危险的天狗", "危險的天狗"]),
        ("nisenaguri", ["nisenaguri", "にせなぐり", "偽殴打", "伪殴打"]),
        ("carol", ["キャロル", "聖なる鐘が響く夜", "卡萝尔", "圣钟鸣响之夜"]),
        ("othellot", ["othellot", "おせろっと", "黑白棋娘"]),
        ("masquerade", ["masquerade", "マスカレイド", "mqnsa.ald", "假面舞会", "假面舞會"]),
        ("dekirukana", ["dekirukana", "できるかな", "できsa.ald", "能做到吗", "能做到嗎"]),
        ("tatakikoroshi", ["tatakikoroshi", "たたき殺し", "叩き殺し", "敲杀太郎", "敲殺太郎"]),
        ("balloonpop", ["balloonpop", "ばるーんぽっぷ", "風船sa.ald", "气球爆破", "氣球爆破"]),
        ("hiyoko2", ["hiyoko2", "kousokuhiyoko", "高速ひよこ", "高速小鸡", "高速小雞"]),
        ("selfgame", ["selfgame", "selfsa.ald", "selfga.ald"]),
        ("momoiroguardian", ["momoiroguardian", "momoiro_guardian", "ももいろガーディアン", "桃色守护者", "桃色守護者", "ももんが", "momonga"]),
        ("oyakorankan", ["oyakorankan", "母娘乱館", "母娘乱馆", "母娘亂館"]),
        ("yorugakuru", ["yorugakuru", "nightremaster", "夜が来る", "邪夜将至", "邪夜將至", "squareofthemoon"]),
        ("maman", ["maman", "heartfulmaman", "は～とふるママン", "は〜とふるママン", "はーとふるママン", "再婚妈妈", "再婚媽媽"]),
        ("haharanman", ["haharanman", "母爛漫", "母烂漫", "曣椆枤"]),
        ("escalayer", ["escalayer", "doki", "エスカレイヤー"]),
        ("evenicle", ["evenicle", "イブニクル", "夏娃年代记"])
    ]

    private static let system4BootIniNames: Set<String> = ["alicestart.chs", "alicestart.ini", "alicestart2.ini", "system40.ini"]

    private static func system4ScriptFiles(_ regularFiles: [URL]) -> [URL] {
        let hasChsTable = regularFiles.contains { $0.lastPathComponent.lowercased() == "chs.tbl" }
        return regularFiles.filter {
            let name = $0.lastPathComponent.lowercased()
            return (name.hasSuffix(".ain") && name != "system39.ain") ||
                (hasChsTable && name.hasSuffix(".chs") && name != "alicestart.chs")
        }.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    private static func system4ConfiguredScript(_ codeName: String, in root: URL) -> URL? {
        let path = codeName.replacingOccurrences(of: "\\", with: "/")
        let components = path.split(separator: "/").filter { $0 != "." }
        guard !path.hasPrefix("/"), !path.contains("\0"), components.first?.contains(":") == false,
              !components.contains(".."), let name = components.last?.lowercased(),
              (name.hasSuffix(".ain") && name != "system39.ain") ||
                (name.hasSuffix(".chs") && name != "alicestart.chs") else { return nil }
        let keys: Set<URLResourceKey> = [.isDirectoryKey, .isRegularFileKey, .isSymbolicLinkKey]
        var directory = root
        for (index, component) in components.enumerated() {
            let leaf = String(component)
            let repairedLeaf = ArchiveExtractor.repairSjisAsGbkMojibake(leaf)
            guard let children = try? FileManager.default.contentsOfDirectory(
                at: directory, includingPropertiesForKeys: Array(keys)
            ), let match = children.first(where: { $0.lastPathComponent == leaf }) ??
                children.first(where: { $0.lastPathComponent.caseInsensitiveCompare(leaf) == .orderedSame }) ??
                children.first(where: { ArchiveExtractor.repairSjisAsGbkMojibake($0.lastPathComponent).caseInsensitiveCompare(repairedLeaf) == .orderedSame }),
                let values = try? match.resourceValues(forKeys: keys),
                values.isSymbolicLink != true else { return nil }
            if index == components.count - 1 { return values.isRegularFile == true ? match : nil }
            guard values.isDirectory == true else { return nil }
            directory = match
        }
        return nil
    }

    private static func system4BootConfigurations(_ regularFiles: [URL], in root: URL) -> [(ini: URL, script: URL)] {
        let codeName = #"(?m)^[\t ]*CodeName[\t ]*=[\t ]*"([^"\r\n]+)""#
        return regularFiles.sorted { $0.lastPathComponent < $1.lastPathComponent }.compactMap { file in
            let name = file.lastPathComponent.lowercased()
            guard !name.hasPrefix("."), name != "system39.ini", name != "system3.ini",
                  name.hasSuffix(".ini") || name == "alicestart.chs",
                  (try? file.resourceValues(forKeys: [.isSymbolicLinkKey]).isSymbolicLink) != true,
                  let handle = try? FileHandle(forReadingFrom: file) else { return nil }
            defer { try? handle.close() }
            let limit = 256 * 1024
            guard let data = try? handle.read(upToCount: limit + 1), data.count <= limit else { return nil }
            for decoded in [
                ArchiveExtractor.decodeBestCjkText(data),
                String(data: data, encoding: .shiftJIS),
                String(data: data, encoding: String.Encoding(rawValue: ArchiveExtractor.gbk)),
            ].compactMap({ $0 }) {
                let text = decoded.trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
                guard let range = text.range(of: codeName, options: .regularExpression),
                      let firstQuote = text[range].firstIndex(of: "\"") else { continue }
                let code = text[text.index(after: firstQuote)..<range.upperBound].dropLast()
                if let script = system4ConfiguredScript(String(code), in: root) {
                    return (file, script)
                }
            }
            return nil
        }
    }

    private static func launchInfoFromRegularFiles(_ url: URL, regularFiles: [URL]) throws -> GameLaunchInfo {
        if regularFiles.contains(where: { system4BootIniNames.contains($0.lastPathComponent.lowercased()) }) {
            return (.system4, url, false)
        }
        let scripts = system4ScriptFiles(regularFiles)
        if let config = system4BootConfigurations(regularFiles, in: url).first {
            return (.system4, config.ini, false)
        }
        if let ain = scripts.first(where: { $0.pathExtension.lowercased() == "ain" }) ?? scripts.first {
            return (.system4, ain, false)
        }
        let names = regularFiles.map { $0.lastPathComponent.lowercased() }
        let system39 = names.contains("system39.ini") || names.contains("system39.ain")
        if system39 || names.contains(where: isSystem35Scenario) {
            return (.system3, url, system39)
        }
        if names.contains("adisk.dat") && (names.contains("acg.dat") || names.contains("acg0.dat") || names.contains("acg3.dat")) {
            return (.legacySystem3, url, false)
        }
        throw LibraryError.notGame
    }

    static func isSystem35Scenario(_ name: String) -> Bool {
        let lower = name.lowercased()
        return lower.hasSuffix("sa.ald") || lower.hasSuffix("sa_chs.ald") || lower == "adisk.ald"
    }

    private static func legacySystem3GameIdFromRegularFiles(_ regularFiles: [URL]) -> String? {
        guard let ini = regularFiles.first(where: { $0.lastPathComponent.lowercased() == "system3.ini" }),
              let data = try? Data(contentsOf: ini), data.count <= 256 * 1024,
              let text = String(data: data, encoding: .utf8) else { return nil }
        for line in text.split(whereSeparator: \.isNewline) {
            let parts = line.split(separator: "=", maxSplits: 1)
            if parts.count == 2 && parts[0].trimmingCharacters(in: .whitespaces).lowercased() == "game" {
                let value = parts[1].trimmingCharacters(in: .whitespaces).lowercased()
                if !value.isEmpty { return value }
            }
        }
        return nil
    }

    private static func legacyDiskPrefixCrc32FromRegularFiles(_ regularFiles: [URL], fileNameLower: String) -> UInt32? {
        guard let disk = regularFiles.first(where: { $0.lastPathComponent.lowercased() == fileNameLower }),
              let handle = try? FileHandle(forReadingFrom: disk) else { return nil }
        defer { handle.closeFile() }
        let data = handle.readData(ofLength: 256)
        guard data.count == 256 else { return nil }
        var crc: UInt32 = 0xffffffff
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xedb88320 : (crc >> 1)
            }
        }
        return crc ^ 0xffffffff
    }

    private static func legacyAdiskCrc32FromRegularFiles(_ regularFiles: [URL]) -> UInt32? {
        legacyDiskPrefixCrc32FromRegularFiles(regularFiles, fileNameLower: "adisk.dat")
    }

    private static func legacyBdiskCrc32FromRegularFiles(_ regularFiles: [URL]) -> UInt32? {
        legacyDiskPrefixCrc32FromRegularFiles(regularFiles, fileNameLower: "bdisk.dat")
    }

    private static func isGakuenKingFromRegularFiles(_ regularNamesLower: Set<String>, legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "gakuen_king" || legacyId == "gakuenking"
        }
        return adiskCrc == 0xd1bf243b || regularNamesLower.contains("gakuen.com")
    }

    private static func isOnlyYouFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "onlyyou" || legacyId == "only_you"
        }
        return adiskCrc == 0x832aeb97
    }

    private static func isAyumiLiveFromRegularFiles(_ regularNamesLower: Set<String>, legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "ayumi_live_256" || legacyId == "ayumi_live_full" || legacyId == "ayumi_live" || legacyId == "ayumilive"
        }
        return adiskCrc == 0x00d15a2b || adiskCrc == 0x5f66ff1d ||
            (regularNamesLower.contains("acg3.dat") && regularNamesLower.contains("adisk.dat"))
    }

    private static func isAyumiHintFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "ayumi_hint" || legacyId == "ayumihint"
        }
        return adiskCrc == 0xf6bd963a
    }

    private static func isAyumiFromRegularFiles(_ regularNamesLower: Set<String>, legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "ayumi_cd" || legacyId == "ayumi" || legacyId == "ayumi_fd" || legacyId == "ayumi_proto"
        }
        let ayumiCrcs: Set<UInt32> = [0xd2bed9ee, 0x4e2fed2a, 0x4e2f5678]
        return (adiskCrc.map { ayumiCrcs.contains($0) } ?? false) ||
            (regularNamesLower.contains("acg0.dat") && regularNamesLower.contains("adisk.dat"))
    }

    private static func isDpsAllFromRegularFiles(_ regularNamesLower: Set<String>, legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "dps_all" || legacyId == "dpsall"
        }
        return adiskCrc == 0xd48b4ec6 || (regularNamesLower.contains("acg1_1.dat") && regularNamesLower.contains("acgs1_16.dat"))
    }

    private static func isProstudentGFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "prog_cd" || legacyId == "prog_fd" || legacyId == "prog" || legacyId == "prostudentg" || legacyId == "prostudent_g"
        }
        return adiskCrc == 0xfb0e4a63 || adiskCrc == 0x5ffbfee7
    }

    private static func isYakata3FromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "yakata3_cd" || legacyId == "yakata3" || legacyId == "yakata3_fd"
        }
        return adiskCrc == 0x7f8f5e2a || adiskCrc == 0x58ebcc99
    }

    private static func isAmbivalenzFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "ambivalenz_cd" || legacyId == "ambivalenz_fd" || legacyId == "ambivalenz"
        }
        return adiskCrc == 0x4b10db69 || adiskCrc == 0xa6b48dfe
    }

    private static func isFunnyBeeFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "funnybee_cd" || legacyId == "funnybee_fd" || legacyId == "funnybee"
        }
        return adiskCrc == 0xe14e3971 || adiskCrc == 0x731267fa
    }

    private static func isToushin2SpFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "toushin2_sp" || legacyId == "toushin2sp"
        }
        return adiskCrc == 0x2172c7b2
    }

    private static func isMugenHouyouFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "mugen" || legacyId == "mugenhouyou" || legacyId == "mugen_houyou"
        }
        return adiskCrc == 0xbb27d1ba
    }

    private static func isOtomeSenkiFromRegularFiles(legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "otome" || legacyId == "otomesenki" || legacyId == "otome_senki"
        }
        return adiskCrc == 0x49a4db15
    }

    fileprivate static func inspect(_ url: URL) -> InspectedGame {
        guard (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true,
              let contents = try? FileManager.default.contentsOfDirectory(
                  at: url, includingPropertiesForKeys: [.isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey]
              ) else {
            return InspectedGame(isValidGame: false, displayName: url.lastPathComponent, engine: .system4, artworkKey: nil, isRance04: false, isRance5d: false, hasLocalChineseMarkers: false, hasExplicitNonChineseEdition: false, hasInstalledCnPatch: false)
        }
        let regularFiles = contents.filter {
            (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
        let info = try? launchInfoFromRegularFiles(url, regularFiles: regularFiles)
        let engine = info?.engine ?? .system4
        let regularNamesLower = Set(regularFiles.map { $0.lastPathComponent.lowercased() })
        let legacyId = engine == .legacySystem3 ? legacySystem3GameIdFromRegularFiles(regularFiles) : nil
        let adiskCrc = (engine == .legacySystem3 && legacyId == nil) ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        let bdiskCrc = (engine == .legacySystem3 && legacyId == nil) ? legacyBdiskCrc32FromRegularFiles(regularFiles) : nil

        let rance04 = engine == .system3 && regularNamesLower.contains("rance4sa.ald")
        let rance1Classic = (engine == .system3 && regularNamesLower.contains("rancesa.ald")) ||
            (engine == .legacySystem3 && (legacyId == "rance" || (legacyId == nil && adiskCrc == 0x2fffbd60)))
        let rance2Classic = (engine == .system3 && regularNamesLower.contains("rance2sa.ald")) ||
            (engine == .legacySystem3 && (legacyId == "rance2" || (legacyId == nil && adiskCrc == 0x28f8298f)))
        let rance3Classic = (engine == .system3 && regularNamesLower.contains("rance3sa.ald")) ||
            (engine == .legacySystem3 && (legacyId == "rance3" || (legacyId == nil && adiskCrc == 0x47a399a1)))
        let dpsSgFahren = engine == .legacySystem3 && ((legacyId == "dps_sg_fahren" || legacyId == "dpssgfahren") || (legacyId == nil && adiskCrc == 0xab4cda48 && bdiskCrc == 0xe405d57c))
        let dpsSgKatei = engine == .legacySystem3 && ((legacyId == "dps_sg_katei" || legacyId == "dpssgkatei") || (legacyId == nil && adiskCrc == 0xab4cda48 && bdiskCrc == 0x23e67d18))
        let dpsSgNobunaga = engine == .legacySystem3 && ((legacyId == "dps_sg_nobunaga" || legacyId == "dpssgnobunaga") || (legacyId == nil && adiskCrc == 0xab4cda48 && bdiskCrc == 0x2ec116f2))
        let dpsSg2Antique = engine == .legacySystem3 && ((legacyId == "dps_sg2_antique" || legacyId == "dpssg2antique") || (legacyId == nil && adiskCrc == 0xab4cda48 && bdiskCrc == 0x41fe8b3d))
        let dpsSg2Ikenai = engine == .legacySystem3 && ((legacyId == "dps_sg2_ikenai" || legacyId == "dpssg2ikenai") || (legacyId == nil && adiskCrc == 0xab4cda48 && bdiskCrc == 0x6b562c09))
        let dpsSg2Akai = engine == .legacySystem3 && ((legacyId == "dps_sg2_akai" || legacyId == "dpssg2akai") || (legacyId == nil && adiskCrc == 0xab4cda48 && bdiskCrc == 0x1098e78c))
        let dpsSg3Rabbit = engine == .legacySystem3 && ((legacyId == "dps_sg3_rabbit" || legacyId == "dpssg3rabbit") || (legacyId == nil && adiskCrc == 0xb77ae133 && bdiskCrc == 0xa3228b6c))
        let dpsSg3Shinkon = engine == .legacySystem3 && ((legacyId == "dps_sg3_shinkon" || legacyId == "dpssg3shinkon") || (legacyId == nil && adiskCrc == 0xb77ae133 && bdiskCrc == 0x09b4448a))
        let dpsSg3Sotsugyou = engine == .legacySystem3 && ((legacyId == "dps_sg3_sotsugyou" || legacyId == "dpssg3sotsugyou") || (legacyId == nil && adiskCrc == 0xb77ae133 && bdiskCrc == 0xbc4525d8))
        let rance41 = (engine == .system3 && regularNamesLower.contains("ran41_sa.ald")) ||
            (legacyId.map { $0 == "rance41" || $0 == "rance41_eng" || $0 == "rance4.1" } ?? false)
        let rance42 = (engine == .system3 && regularNamesLower.contains("ran42_sa.ald")) ||
            (legacyId.map { $0 == "rance42" || $0 == "rance42_eng" || $0 == "rance4.2" } ?? false)
        let rance5d = engine == .system3 && regularNamesLower.contains("r5d_sa.ald")
        let yakata2 = legacyId.map { $0 == "yakata2" || $0 == "yakata2_eng" } ?? false
        let yakata1 = engine == .legacySystem3 && ((legacyId == "yakata" || legacyId == "yakata1") || (legacyId == nil && (adiskCrc == 0x8cef6fa6 || adiskCrc == 0xd8f84e34)))
        let littleVampire = engine == .legacySystem3 && ((legacyId == "vampire" || legacyId == "little_vampire" || legacyId == "littlevampire") || (legacyId == nil && adiskCrc == 0x957bcfbf))
        let intruder = engine == .legacySystem3 && (legacyId == "intruder" || (legacyId == nil && adiskCrc == 0xa7520fb2))
        let crescent = engine == .legacySystem3 && (legacyId == "crescent" || (legacyId == nil && adiskCrc == 0x42351f2c))
        let tengu = engine == .legacySystem3 && ((legacyId == "tengu" || legacyId == "abunai_tengu") || (legacyId == nil && adiskCrc == 0xc942ff58))
        let dps = engine == .legacySystem3 && (legacyId == "dps" || (legacyId == nil && adiskCrc == 0x69ea4865))
        let sdpsTono = engine == .legacySystem3 && ((legacyId == "sdps_tono" || legacyId == "sdpstono") || (legacyId == nil && adiskCrc == 0xc7a20cdf && bdiskCrc == 0xbb1edff1))
        let sdpsKaizoku = engine == .legacySystem3 && ((legacyId == "sdps_kaizoku" || legacyId == "sdpskaizoku") || (legacyId == nil && adiskCrc == 0xc7a20cdf && bdiskCrc == 0xf81829e3))
        let sdpsMaria = legacyId.map { $0 == "sdps_maria" || $0 == "sdps_maria_eng" || $0 == "sdps" || $0 == "superdps" } ?? false
        let gakuenKing = engine == .legacySystem3 && isGakuenKingFromRegularFiles(regularNamesLower, legacyId: legacyId, adiskCrc: adiskCrc)
        let onlyYou = engine == .legacySystem3 && isOnlyYouFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let ayumiLive = engine == .legacySystem3 && isAyumiLiveFromRegularFiles(regularNamesLower, legacyId: legacyId, adiskCrc: adiskCrc)
        let ayumiHint = engine == .legacySystem3 && isAyumiHintFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let ayumi = engine == .legacySystem3 && !ayumiLive && !ayumiHint && isAyumiFromRegularFiles(regularNamesLower, legacyId: legacyId, adiskCrc: adiskCrc)
        let dpsAll = engine == .legacySystem3 && isDpsAllFromRegularFiles(regularNamesLower, legacyId: legacyId, adiskCrc: adiskCrc)
        let progOmake = engine == .legacySystem3 && (legacyId == "prog_omake" || legacyId == "progomake")
        let prostudentG = engine == .legacySystem3 && !progOmake && isProstudentGFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let niseNaguri = engine == .legacySystem3 && ((legacyId == "nise_naguri" || legacyId == "nisenaguri") || (legacyId == nil && adiskCrc == 0xfabe6302))
        let yakata3 = engine == .legacySystem3 && isYakata3FromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let ambivalenz = engine == .legacySystem3 && isAmbivalenzFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let funnyBee = engine == .legacySystem3 && isFunnyBeeFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let toushin2Sp = engine == .legacySystem3 && isToushin2SpFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let mugenHouyou = engine == .legacySystem3 && isMugenHouyouFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let otomeSenki = engine == .legacySystem3 && isOtomeSenkiFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let hiyoko2 = engine == .system3 && regularNamesLower.contains("piyod.dat") && (regularNamesLower.contains("sa.ald") || regularNamesLower.contains("ga.ald"))

        let displayName: String
        if rance04 { displayName = "兰斯IV 教团的遗产" }
        else if rance1Classic { displayName = "兰斯I 寻找小光（原版）" }
        else if rance2Classic { displayName = "兰斯II 反叛的少女们（原版）" }
        else if rance3Classic { displayName = "兰斯III 利萨斯陷落（原版）" }
        else if dpsSgFahren { displayName = "D.P.S. SG 飞翔的梦" }
        else if dpsSgKatei { displayName = "D.P.S. SG 家庭教师是美妙的工作" }
        else if dpsSgNobunaga { displayName = "D.P.S. SG 信长的淫谋" }
        else if dpsSg2Antique { displayName = "D.P.S. SG set2 古董屋" }
        else if dpsSg2Ikenai { displayName = "D.P.S. SG set2 不妙的内科体检再临" }
        else if dpsSg2Akai { displayName = "D.P.S. SG set2 朱红之夜" }
        else if dpsSg3Rabbit { displayName = "D.P.S. SG set3 Rabbit P4P" }
        else if dpsSg3Shinkon { displayName = "D.P.S. SG set3 新婚物语" }
        else if dpsSg3Sotsugyou { displayName = "D.P.S. SG set3 毕业" }
        else if rance41 { displayName = "兰斯4.1 拯救制药厂！" }
        else if rance42 { displayName = "兰斯4.2 天使组" }
        else if rance5d { displayName = "兰斯5D" }
        else if yakata2 { displayName = "爱丽丝之馆2" }
        else if yakata1 { displayName = "爱丽丝之馆" }
        else if littleVampire { displayName = "小吸血鬼 Little Vampire" }
        else if intruder { displayName = "侵入者 -樱屋敷的探索-" }
        else if crescent { displayName = "新月少女" }
        else if tengu { displayName = "危险的天狗传说" }
        else if dps { displayName = "D.P.S. 梦幻程序系统" }
        else if sdpsTono { displayName = "Super D.P.S 远野之森" }
        else if sdpsKaizoku { displayName = "Super D.P.S 欢乐海盗生涯" }
        else if sdpsMaria { displayName = "Super D.P.S 玛利亚和坎潘" }
        else if gakuenKing { displayName = "学园KING" }
        else if onlyYou { displayName = "Only You 世纪末的朱丽叶们" }
        else if ayumiLive { displayName = "步美物语 真人版" }
        else if ayumiHint { displayName = "步美物语 提示盘" }
        else if ayumi { displayName = "步美物语" }
        else if dpsAll { displayName = "D.P.S. 全部" }
        else if progOmake { displayName = "职业学生G 附赠盘" }
        else if prostudentG { displayName = "职业学生G" }
        else if niseNaguri { displayName = "伪殴打狂魔塔" }
        else if yakata3 { displayName = "爱丽丝之馆3" }
        else if ambivalenz { displayName = "AmbivalenZ -二律背反-" }
        else if funnyBee { displayName = "宇宙快盗Funny Bee" }
        else if toushin2Sp { displayName = "斗神都市II 然后，从那以后…" }
        else if mugenHouyou { displayName = "梦幻泡影" }
        else if otomeSenki { displayName = "乙女战记" }
        else if hiyoko2 { displayName = "高速小鸡2" }
        else { displayName = url.lastPathComponent }

        let erumeiWakare: Bool
        if engine == .system4 && regularNamesLower.contains("エルメイフラワー.ain") {
            let waiSize = regularFiles.first(where: { $0.lastPathComponent.lowercased() == "エルメイフラワーwa.wai" })
                .flatMap { try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize }
            let bgiSize = regularFiles.first(where: { $0.lastPathComponent.lowercased() == "エルメイフラワーba.bgi" })
                .flatMap { try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize }
            let ainSize = regularFiles.first(where: { $0.lastPathComponent.lowercased() == "エルメイフラワー.ain" })
                .flatMap { try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize }
            if let waiSize, waiSize > 0 {
                erumeiWakare = waiSize <= 256
            } else if let bgiSize, bgiSize > 0 {
                erumeiWakare = bgiSize <= 600
            } else if let ainSize, ainSize > 0 {
                erumeiWakare = ainSize <= 500_000
            } else {
                erumeiWakare = false
            }
        } else {
            erumeiWakare = false
        }
        let erumeiFlower = engine == .system4 && regularNamesLower.contains("エルメイフラワー.ain") && !erumeiWakare

        let dirLower = url.lastPathComponent.lowercased()
        let isRance10 = engine == .system4 && regularNamesLower.contains("rance10.ain")
        let isGirls = engine == .system4 && (regularNamesLower.contains("ｇｉｒｌｓ.ain") || regularNamesLower.contains("girls.ain") || dirLower == "girls")
        let isDebut = engine == .system4 && (regularNamesLower.contains("ｄｅｂｕｔ.ain") || regularNamesLower.contains("debut.ain") || dirLower == "debut")
        let isCarol = engine == .system3 && (regularNamesLower.contains("キャロルsa.ald") || dirLower == "carol")
        let isDpsDir = engine == .legacySystem3 && dirLower == "dps"
        let isTenguDir = engine == .legacySystem3 && dirLower == "tengu"

        let detectedArtworkID: String?
        if isRance10 { detectedArtworkID = "rance10" }
        else if rance41 { detectedArtworkID = "rance41" }
        else if rance42 { detectedArtworkID = "rance42" }
        else if rance04 { detectedArtworkID = "rance04" }
        else if rance1Classic { detectedArtworkID = "rance1classic" }
        else if rance2Classic { detectedArtworkID = "rance2classic" }
        else if rance3Classic { detectedArtworkID = "rance3classic" }
        else if dpsSgFahren { detectedArtworkID = "dpssgfahren" }
        else if dpsSgKatei { detectedArtworkID = "dpssgkatei" }
        else if dpsSgNobunaga { detectedArtworkID = "dpssgnobunaga" }
        else if dpsSg2Antique { detectedArtworkID = "dpssg2antique" }
        else if dpsSg2Ikenai { detectedArtworkID = "dpssg2ikenai" }
        else if dpsSg2Akai { detectedArtworkID = "dpssg2akai" }
        else if dpsSg3Rabbit { detectedArtworkID = "dpssg3rabbit" }
        else if dpsSg3Shinkon { detectedArtworkID = "dpssg3shinkon" }
        else if dpsSg3Sotsugyou { detectedArtworkID = "dpssg3sotsugyou" }
        else if rance5d { detectedArtworkID = "rance5d" }
        else if yakata2 { detectedArtworkID = "yakata2" }
        else if yakata1 { detectedArtworkID = "yakata1" }
        else if littleVampire { detectedArtworkID = "littlevampire" }
        else if intruder { detectedArtworkID = "intruder" }
        else if crescent { detectedArtworkID = "crescent" }
        else if tengu { detectedArtworkID = "tengu" }
        else if dps { detectedArtworkID = "dps" }
        else if sdpsTono { detectedArtworkID = "sdpstono" }
        else if sdpsKaizoku { detectedArtworkID = "sdpskaizoku" }
        else if sdpsMaria { detectedArtworkID = "sdpsmaria" }
        else if gakuenKing { detectedArtworkID = "gakuenking" }
        else if onlyYou { detectedArtworkID = "onlyyou" }
        else if ayumiLive { detectedArtworkID = "ayumilive" }
        else if ayumiHint { detectedArtworkID = "ayumihint" }
        else if ayumi { detectedArtworkID = "ayumi" }
        else if dpsAll { detectedArtworkID = "dpsall" }
        else if progOmake { detectedArtworkID = "progomake" }
        else if prostudentG { detectedArtworkID = "prostudentg" }
        else if niseNaguri { detectedArtworkID = "nisenaguri" }
        else if yakata3 { detectedArtworkID = "yakata3" }
        else if ambivalenz { detectedArtworkID = "ambivalenz" }
        else if funnyBee { detectedArtworkID = "funnybee" }
        else if toushin2Sp { detectedArtworkID = "toushin2sp" }
        else if mugenHouyou { detectedArtworkID = "mugenhouyou" }
        else if otomeSenki { detectedArtworkID = "otomesenki" }
        else if hiyoko2 { detectedArtworkID = "hiyoko2" }
        else if erumeiWakare { detectedArtworkID = "erumeiwakare" }
        else if erumeiFlower { detectedArtworkID = "erumeiflower" }
        else if isGirls { detectedArtworkID = "girls" }
        else if isDebut { detectedArtworkID = "debut" }
        else if isCarol { detectedArtworkID = "carol" }
        else if isDpsDir { detectedArtworkID = "dps" }
        else if isTenguDir { detectedArtworkID = "tengu" }
        else { detectedArtworkID = nil }

        let artworkKey: String?
        if let detectedArtworkID {
            artworkKey = detectedArtworkID
        } else {
            let names = contents.map { $0.lastPathComponent } + [url.lastPathComponent]
            let normalized = names.joined(separator: " ").lowercased()
                .replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "_", with: "")
                .replacingOccurrences(of: "-", with: "").replacingOccurrences(of: "・", with: "")
            let isDalkGaiden = normalized.contains("gaiden") || normalized.contains("外伝") || normalized.contains("外传") || normalized.contains("外傳") || normalized.contains("dalkdemo")
            let isDaibanchouGaiden = normalized.contains("gaiden") || normalized.contains("外伝") || normalized.contains("外传") || normalized.contains("外傳")
            let isHarukaSpinoff = normalized.contains("harukavsesca") || normalized.contains("ハルカvs") || normalized.contains("ハルカｖｓ") || normalized.contains("harukaninja") || normalized.contains("shippuu") || normalized.contains("疾風") || normalized.contains("疾风") || normalized.contains("忍者大作戦") || normalized.contains("忍者大作战")
            let isVanishTeNoHira = normalized.contains("この手のひら") || normalized.contains("konotenohira") || normalized.contains("vanishpalm") || normalized.contains("ばにしゅ！番外") || normalized.contains("ばにしゅ!番外")
            let isKamunagara2 = normalized.contains("神武2") || normalized.contains("神武２") || normalized.contains("第弐話") || normalized.contains("第2話")
            artworkKey = artworkAliases.first(where: { key, tokens in
                if key == "dalk" && isDalkGaiden { return false }
                if key == "daibanchou" && isDaibanchouGaiden { return false }
                if (key == "haruka" || key == "escalayer") && isHarukaSpinoff { return false }
                if key == "vanish" && isVanishTeNoHira { return false }
                if key == "kamunagara" && isKamunagara2 { return false }
                return tokens.contains(where: normalized.contains)
            })?.0
        }

        let hasLocalChinese = hasLocalChineseMarkers(regularFiles: regularFiles, directoryContents: contents)
        let hasExplicitNonChinese = legacyId?.hasSuffix("_eng") == true
        let hasCnPatch = hasInstalledCnPatch(url, regularFiles: regularFiles)

        return InspectedGame(
            isValidGame: info != nil,
            displayName: displayName,
            engine: engine,
            artworkKey: artworkKey,
            isRance04: rance04,
            isRance5d: rance5d,
            hasLocalChineseMarkers: hasLocalChinese,
            hasExplicitNonChineseEdition: hasExplicitNonChinese,
            hasInstalledCnPatch: hasCnPatch
        )
    }

    @Published var games: [Game] = []
    @Published var importing = false
    @Published var importProgress = ""
    @Published var launching = false
    @Published var error: String?
    private let files = FileManager.default
    private let documents: URL
    private var refreshGeneration: Int = 0
    var gamesDirectory: URL { documents.appendingPathComponent("Games", isDirectory: true) }

    init(documents: URL? = nil) {
        self.documents = documents ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        refresh()
    }

    static func isGame(_ url: URL) -> Bool {
        (try? launchTarget(url)) != nil
    }

    static func isRance04(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .system3 else { return false }
        return contents.contains {
            $0.lastPathComponent.lowercased() == "rance4sa.ald" &&
                (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
    }

    private static func legacySystem3GameId(_ url: URL) -> String? {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3,
              let ini = contents.first(where: {
                  $0.lastPathComponent.lowercased() == "system3.ini" &&
                      (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
              }),
              let data = try? Data(contentsOf: ini), data.count <= 256 * 1024,
              let text = String(data: data, encoding: .utf8) else { return nil }
        for line in text.split(whereSeparator: \.isNewline) {
            let parts = line.split(separator: "=", maxSplits: 1)
            if parts.count == 2 && parts[0].trimmingCharacters(in: .whitespaces).lowercased() == "game" {
                let value = parts[1].trimmingCharacters(in: .whitespaces).lowercased()
                if !value.isEmpty { return value }
            }
        }
        return nil
    }

    static func isRance41(_ url: URL) -> Bool {
        if let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
           let info = try? launchInfo(url), info.engine == .system3,
           contents.contains(where: {
               $0.lastPathComponent.lowercased() == "ran41_sa.ald" &&
                   (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
           }) {
            return true
        }
        guard let game = legacySystem3GameId(url) else { return false }
        return game == "rance41" || game == "rance41_eng" || game == "rance4.1"
    }

    static func isRance42(_ url: URL) -> Bool {
        if let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
           let info = try? launchInfo(url), info.engine == .system3,
           contents.contains(where: {
               $0.lastPathComponent.lowercased() == "ran42_sa.ald" &&
                   (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
           }) {
            return true
        }
        guard let game = legacySystem3GameId(url) else { return false }
        return game == "rance42" || game == "rance42_eng" || game == "rance4.2"
    }

    static func isYakata2(_ url: URL) -> Bool {
        guard let game = legacySystem3GameId(url) else { return false }
        return game == "yakata2" || game == "yakata2_eng"
    }

    static func isSdpsMaria(_ url: URL) -> Bool {
        guard let game = legacySystem3GameId(url) else { return false }
        return game == "sdps_maria" || game == "sdps_maria_eng" || game == "sdps" || game == "superdps"
    }

    static func isGakuenKing(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let names = Set(regularFiles.map { $0.lastPathComponent.lowercased() })
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isGakuenKingFromRegularFiles(names, legacyId: legacyId, adiskCrc: crc)
    }

    static func isOnlyYou(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isOnlyYouFromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isAyumi(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let names = Set(regularFiles.map { $0.lastPathComponent.lowercased() })
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isAyumiFromRegularFiles(names, legacyId: legacyId, adiskCrc: crc)
    }

    static func isDpsAll(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let names = Set(regularFiles.map { $0.lastPathComponent.lowercased() })
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isDpsAllFromRegularFiles(names, legacyId: legacyId, adiskCrc: crc)
    }

    static func isProstudentG(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isProstudentGFromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isYakata3(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isYakata3FromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isAmbivalenz(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isAmbivalenzFromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isFunnyBee(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isFunnyBeeFromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isToushin2Sp(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isToushin2SpFromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isMugenHouyou(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isMugenHouyouFromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isOtomeSenki(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .legacySystem3 else { return false }
        let regularFiles = contents.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        let legacyId = legacySystem3GameIdFromRegularFiles(regularFiles)
        let crc = legacyId == nil ? legacyAdiskCrc32FromRegularFiles(regularFiles) : nil
        return isOtomeSenkiFromRegularFiles(legacyId: legacyId, adiskCrc: crc)
    }

    static func isRance5d(_ url: URL) -> Bool {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]),
              let info = try? launchInfo(url), info.engine == .system3 else { return false }
        return contents.contains {
            $0.lastPathComponent.lowercased() == "r5d_sa.ald" &&
                (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
    }

    static func launchTarget(_ url: URL) throws -> URL {
        try launchInfo(url).target
    }

    static func launchInfo(_ url: URL, bootEntry: String = "") throws -> GameLaunchInfo {
        guard (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true,
              let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]) else { throw LibraryError.notGame }
        let regularFiles = contents.filter {
            (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
        let info = try launchInfoFromRegularFiles(url, regularFiles: regularFiles)
        guard info.engine == .system4, !bootEntry.isEmpty, system4BootEntries(url).contains(bootEntry) else { return info }
        return (.system4, url.appendingPathComponent(bootEntry), false)
    }

    static func system4BootEntries(_ url: URL) -> [String] {
        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: url, includingPropertiesForKeys: [.isRegularFileKey, .isSymbolicLinkKey], options: .skipsHiddenFiles
        ) else { return [] }
        let regularFiles = contents.filter {
            guard let values = try? $0.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey]) else { return false }
            return values.isRegularFile == true && values.isSymbolicLink != true
        }
        let scripts = system4ScriptFiles(regularFiles)
        let configurations = system4BootConfigurations(regularFiles, in: url)
        let configured = Set(configurations.map { $0.script.path })
        let hasIsolatedSaves = usesDaiSengokuSaveFolder(url) ||
            configurations.contains { iniUsesDaiSengokuSaveFolder($0.ini) == true }
        let entries = configurations.map(\.ini) + scripts.filter {
            !configured.contains($0.path) && (!hasIsolatedSaves || $0.lastPathComponent.lowercased() == "alicecn.ain")
        }
        let hasStandardIni = regularFiles.contains { system4BootIniNames.contains($0.lastPathComponent.lowercased()) }
        guard entries.count > 1 ||
                (hasStandardIni && entries.contains { !system4BootIniNames.contains($0.lastPathComponent.lowercased()) }) else { return [] }
        return entries.map(\.lastPathComponent)
    }

    private static func bootEntryKey(_ game: Game) -> String { "hub.system4BootEntry." + game.id }

    func bootEntry(for game: Game) -> String {
        UserDefaults.standard.string(forKey: Self.bootEntryKey(game)) ?? ""
    }

    func setBootEntry(_ entry: String, for game: Game) {
        objectWillChange.send()
        UserDefaults.standard.set(entry.isEmpty ? nil : entry, forKey: Self.bootEntryKey(game))
    }

    private static func selectedInstallationKey(_ catalogID: String) -> String { "hub.selectedInstallation." + catalogID }

    func selectedInstallation(for catalogID: String) -> String? {
        guard !catalogID.isEmpty,
              let value = UserDefaults.standard.string(forKey: Self.selectedInstallationKey(catalogID)),
              !value.isEmpty else { return nil }
        return value
    }

    func setSelectedInstallation(_ installationID: String?, for catalogID: String) {
        guard !catalogID.isEmpty else { return }
        objectWillChange.send()
        let key = Self.selectedInstallationKey(catalogID)
        if let installationID, !installationID.isEmpty {
            UserDefaults.standard.set(installationID, forKey: key)
        } else {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }

    static func launchArguments(_ url: URL, saves: URL, bootEntry: String = "") throws -> [String] {
        launchArguments(url, saves: saves, info: try launchInfo(url, bootEntry: bootEntry))
    }

    private static func launchArguments(_ url: URL, saves: URL, info: GameLaunchInfo) -> [String] {
        switch info.engine {
        case .legacySystem3:
            return ["system3", "-gamedir", url.path, "-savedir", saves.path]
        case .system3:
            return ["xsystem35", "-gamedir", url.path, "-savedir", saves.path] + (info.system39 ? ["-saveformat", "system39"] : [])
        case .system4:
            return ["xsystem4", "--joypad=on", "--save-folder", saves.path, "--save-format=rsm", info.target.path]
        }
    }

    private static func scanGames(in directory: URL) throws -> [Game] {
        let fm = FileManager.default
        try fm.createDirectory(at: directory, withIntermediateDirectories: true)
        return try fm.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isDirectoryKey], options: .skipsHiddenFiles)
            .compactMap { url -> Game? in
                let meta = inspect(url)
                return meta.isValidGame ? Game(url: url, meta: meta) : nil
            }
            .sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
    }

    func refresh() {
        refreshGeneration += 1
        do {
            let scanned = try Self.scanGames(in: gamesDirectory)
            if scanned != games { games = scanned }
        } catch { self.error = error.localizedDescription }
    }

    func refreshAsync(completion: (() -> Void)? = nil) {
        refreshGeneration += 1
        let generation = refreshGeneration
        let directory = gamesDirectory
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let scanned = try Self.scanGames(in: directory)
                DispatchQueue.main.async {
                    if generation == self.refreshGeneration && scanned != self.games {
                        self.games = scanned
                    }
                    completion?()
                }
            } catch {
                DispatchQueue.main.async {
                    if generation == self.refreshGeneration {
                        self.error = error.localizedDescription
                    }
                    completion?()
                }
            }
        }
    }

    static func normalizeRance06DungeonData(_ root: URL) throws {
        let files = FileManager.default
        let children = try files.contentsOfDirectory(at: root, includingPropertiesForKeys: [.isRegularFileKey, .isDirectoryKey])
        let ainNames = ["rance6.ain", "rance06.ain", "rancevi.ain", "ランス6.ain", "ランス６.ain", "ランス06.ain", "ランス０６.ain"]
        guard children.contains(where: { ainNames.contains($0.lastPathComponent.lowercased()) && (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }),
              let data = children.first(where: { $0.lastPathComponent.lowercased() == "data" && (try? $0.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true }) else { return }
        let entries = try files.contentsOfDirectory(at: data, includingPropertiesForKeys: [.isRegularFileKey])
        guard !entries.contains(where: { $0.lastPathComponent.lowercased() == "dungeondata.dlf" }) else { return }
        let candidates = entries.filter {
            $0.lastPathComponent.range(of: #"^DungeonData\([0-9]+\)\.dlf$"#, options: [.regularExpression, .caseInsensitive]) != nil &&
                (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
        if let source = candidates.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }).first {
            try files.moveItem(at: source, to: data.appendingPathComponent("DungeonData.dlf"))
        }
    }

    func importFolder(_ source: URL) {
        importLocal(source, progress: hubText("正在复制游戏文件夹…")) { readable, work in
            let staged = work.appendingPathComponent("folder", isDirectory: true)
            try LocalGameImport.stageFolder(readable, to: staged)
            return (staged, source.lastPathComponent)
        }
    }

    func importArchive(_ source: URL) {
        importLocal(source, progress: hubText("正在解压：%lld MB", 0)) { readable, work in
            var lastProgress = Date.distantPast
            return try LocalGameImport.stageArchive(readable, in: work) { bytes, entry in
                let now = Date()
                guard now.timeIntervalSince(lastProgress) >= 0.15 else { return }
                lastProgress = now
                let text = hubText("正在解压：%lld MB\n%@", bytes / (1024 * 1024), entry)
                DispatchQueue.main.async { self.importProgress = text }
            }
        }
    }

    private func importLocal(_ source: URL, progress: String,
                             stage: @escaping (URL, URL) throws -> (root: URL, name: String)) {
        guard !importing && !launching else { return }
        importing = true
        error = nil
        importProgress = progress
        DispatchQueue.global(qos: .userInitiated).async {
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            let work = self.gamesDirectory.appendingPathComponent(".import-\(UUID().uuidString)")
            var result: Error?
            var importedDestination: URL?
            let coordinator = NSFileCoordinator()
            var coordinationError: NSError?
            coordinator.coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { readable in
                do {
                    let (root, name) = try stage(readable, work)
                    guard !name.isEmpty, !name.hasPrefix("."), !name.contains("/") else { throw LibraryError.notGame }
                    let destination = self.gamesDirectory.appendingPathComponent(name, isDirectory: true)
                    guard !self.files.fileExists(atPath: destination.path) else { throw LibraryError.alreadyExists }
                    try self.files.moveItem(at: root, to: destination)
                    importedDestination = destination
                } catch { result = error }
            }
            try? self.files.removeItem(at: work)
            let message = (result ?? coordinationError)?.localizedDescription
            let imported = (result == nil && coordinationError == nil) ? importedDestination.map(Game.init(url:)) : nil
            DispatchQueue.main.async {
                self.importing = false
                self.importProgress = ""
                if let imported, let catalogID = Self.resolvedCatalogID(for: imported) {
                    self.setSelectedInstallation(imported.id, for: catalogID)
                }
                self.refresh()
                self.error = message
            }
        }
    }

    enum DirectoryNameValidation: Equatable {
        case valid(String)
        case invalid(String)
    }

    func suggestNextDirectoryName(basePrefix: String = "Rance10") -> String {
        let existing = Set(((try? files.contentsOfDirectory(atPath: gamesDirectory.path)) ?? []).map { $0.lowercased() })
        for index in 1...999 {
            let candidate = "\(basePrefix)-\(index)"
            if !existing.contains(candidate.lowercased()) {
                return candidate
            }
        }
        return "\(basePrefix)-\(Int(Date().timeIntervalSince1970))"
    }

    func validateNewDirectoryName(_ raw: String) -> DirectoryNameValidation {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .invalid(hubText("请输入目录名称")) }
        guard trimmed.count <= 80 else { return .invalid(hubText("目录名称过长（最多 80 个字符）")) }
        guard trimmed != "." && trimmed != ".." && !trimmed.hasPrefix(".") else {
            return .invalid(hubText("目录名称不能包含非法字符或以点号开头"))
        }
        let forbidden = CharacterSet(charactersIn: "/\\:*?\"<>|").union(.controlCharacters)
        if trimmed.rangeOfCharacter(from: forbidden) != nil {
            return .invalid(hubText("目录名称不能包含非法字符或以点号开头"))
        }
        let existing = (try? files.contentsOfDirectory(atPath: gamesDirectory.path)) ?? []
        let lower = trimmed.lowercased()
        if existing.contains(where: { $0.lowercased() == lower }) {
            return .invalid(hubText("已存在同名目录，请换一个名称"))
        }
        return .valid(trimmed)
    }

    func cloneDirectory(
        from source: Game,
        named targetName: String,
        transformStage: ((URL) throws -> Void)? = nil,
        completion: ((Game?) -> Void)? = nil
    ) {
        guard !importing && !launching else {
            completion?(nil)
            return
        }
        let sanitized: String
        switch validateNewDirectoryName(targetName) {
        case .valid(let name):
            sanitized = name
        case .invalid(let reason):
            error = reason
            completion?(nil)
            return
        }
        importing = true
        error = nil
        importProgress = hubText("正在创建目录：%@", sanitized)
        let sourceURL = source.url
        let gamesDir = gamesDirectory
        DispatchQueue.global(qos: .userInitiated).async {
            let temporary = gamesDir.appendingPathComponent(".import-\(UUID().uuidString)", isDirectory: true)
            let destination = gamesDir.appendingPathComponent(sanitized, isDirectory: true)
            var result: Error?
            do {
                try self.files.createDirectory(at: gamesDir, withIntermediateDirectories: true)
                let existing = (try? self.files.contentsOfDirectory(atPath: gamesDir.path)) ?? []
                if existing.contains(where: { $0.lowercased() == sanitized.lowercased() }) || self.files.fileExists(atPath: destination.path) {
                    throw LibraryError.alreadyExists
                }
                guard Self.isGame(sourceURL) else { throw LibraryError.notGame }
                try self.files.copyItem(at: sourceURL, to: temporary)
                let keys: [URLResourceKey] = [.isRegularFileKey, .isSymbolicLinkKey]
                if let enumerator = self.files.enumerator(at: temporary, includingPropertiesForKeys: keys) {
                    var tempFiles: [URL] = []
                    while let item = enumerator.nextObject() as? URL {
                        let values = try? item.resourceValues(forKeys: Set(keys))
                        if values?.isRegularFile == true && values?.isSymbolicLink != true &&
                            item.lastPathComponent.lowercased().hasSuffix(".tmp") {
                            tempFiles.append(item)
                        }
                    }
                    for tmpFile in tempFiles {
                        try? self.files.removeItem(at: tmpFile)
                    }
                }
                if let transformStage {
                    try transformStage(temporary)
                }
                guard Self.isGame(temporary) else { throw LibraryError.notGame }
                try self.files.moveItem(at: temporary, to: destination)
            } catch {
                result = error
            }
            if self.files.fileExists(atPath: temporary.path) {
                try? self.files.removeItem(at: temporary)
            }
            let message = result?.localizedDescription
            DispatchQueue.main.async {
                self.importing = false
                self.importProgress = ""
                self.refresh()
                self.error = message
                let created = result == nil ? self.games.first(where: { $0.name == sanitized }) : nil
                completion?(created)
            }
        }
    }

    func delete(_ game: Game) {
        do {
            try files.removeItem(at: game.url)
            setBootEntry("", for: game)
            if let catalogID = Self.resolvedCatalogID(for: game),
               selectedInstallation(for: catalogID) == game.id {
                setSelectedInstallation(nil, for: catalogID)
            }
            refresh()
        } catch { self.error = error.localizedDescription }
    }

    /// Returns "<file>#<PostScript name>" for an installed system font, or nil when
    /// CoreText would substitute a different font.
    static func systemFontSpec(_ postScriptName: String) -> String? {
        let font = CTFontCreateWithName(postScriptName as CFString, 12, nil)
        guard CTFontCopyPostScriptName(font) as String == postScriptName,
              let url = CTFontCopyAttribute(font, kCTFontURLAttribute) as? URL,
              FileManager.default.isReadableFile(atPath: url.path) else { return nil }
        return url.path + "#" + postScriptName
    }

    private static func setSystemSansFontDefaults() {
        let japanese = systemFontSpec("HiraginoSans-W3")
        let chinese = systemFontSpec("PingFangSC-Medium") ?? japanese
        let defaults = [
            "XSYSTEM4_FONT_GOTHIC": japanese, "XSYSTEM4_FONT_MINCHO": japanese,
            "XSYSTEM4_FONT_GOTHIC_SC": chinese, "XSYSTEM4_FONT_MINCHO_SC": chinese,
        ]
        for (name, value) in defaults {
            if let value { setenv(name, value, 1) } else { unsetenv(name) }
        }
    }

    static let daiSengokuSaveDirSuffix = "__SaveDataDSR"

    private static func usesDaiSengokuSaveFolder(_ gameURL: URL) -> Bool {
        let keys: [URLResourceKey] = [.isRegularFileKey, .isSymbolicLinkKey]
        let regularFiles = ((try? FileManager.default.contentsOfDirectory(
            at: gameURL, includingPropertiesForKeys: keys, options: .skipsHiddenFiles
        )) ?? []).filter {
            guard let values = try? $0.resourceValues(forKeys: Set(keys)) else { return false }
            return values.isRegularFile == true && values.isSymbolicLink != true
        }
        for fileName in ["System40.ini", "AliceStart.ini"] {
            guard let iniURL = regularFiles.first(where: { $0.lastPathComponent == fileName }) ??
                regularFiles.first(where: { $0.lastPathComponent.caseInsensitiveCompare(fileName) == .orderedSame }) else {
                continue
            }
            if let usesDSR = iniUsesDaiSengokuSaveFolder(iniURL) { return usesDSR }
        }
        return false
    }

    private static func iniUsesDaiSengokuSaveFolder(_ iniURL: URL) -> Bool? {
        guard let data = try? Data(contentsOf: iniURL) else { return nil }
        let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) ?? ""
        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty || line.hasPrefix(";") || line.hasPrefix("#") || line.hasPrefix("//") {
                continue
            }
            guard let eq = line.firstIndex(of: "=") else { continue }
            let key = line[..<eq].trimmingCharacters(in: .whitespacesAndNewlines)
            guard key.caseInsensitiveCompare("SaveFolder") == .orderedSame else { continue }
            var value = line[line.index(after: eq)...].trimmingCharacters(in: .whitespacesAndNewlines)
            if value.hasPrefix("\"") {
                let rest = value.dropFirst()
                if let close = rest.firstIndex(of: "\"") {
                    value = String(rest[..<close])
                } else {
                    value = String(rest)
                }
            } else {
                for marker in [";", "#", "//"] {
                    if let range = value.range(of: marker) {
                        value = String(value[..<range.lowerBound])
                    }
                }
                value = value.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            return value.caseInsensitiveCompare("SaveDataDSR") == .orderedSame
        }
        return false
    }

    private static func saveDirectoryName(for gameURL: URL, target: URL) -> String {
        let base = gameURL.lastPathComponent
        let usesDSR: Bool
        if target.path == gameURL.path {
            usesDSR = usesDaiSengokuSaveFolder(gameURL)
        } else if target.pathExtension.lowercased() == "ini" || target.lastPathComponent.lowercased() == "alicestart.chs" {
            usesDSR = iniUsesDaiSengokuSaveFolder(target) == true
        } else {
            usesDSR = false
        }
        return usesDSR ? "\(base)\(daiSengokuSaveDirSuffix)" : base
    }

    func saveDirectoryName(for game: Game) -> String {
        let target = (try? Self.launchInfo(game.url, bootEntry: bootEntry(for: game)))?.target ?? game.url
        return Self.saveDirectoryName(for: game.url, target: target)
    }

    private func savesDirectory(named name: String) -> URL {
        documents.appendingPathComponent("Saves").appendingPathComponent(name)
    }

    func savesDirectory(for game: Game) -> URL {
        savesDirectory(named: saveDirectoryName(for: game))
    }

    func launch(_ game: Game) {
        guard !launching else { return }
        do {
            guard alicehub_ios_finish_audio_shutdown() else { throw LibraryError.audioStopping }
            let info = try Self.launchInfo(game.url, bootEntry: bootEntry(for: game))
            guard let resources = Bundle.main.resourceURL else { throw LibraryError.resourcePath }
            let home = files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("xsystem4")
            let saveName = Self.saveDirectoryName(for: game.url, target: info.target)
            let saves = savesDirectory(named: saveName)
            let logs = documents.appendingPathComponent("Logs")
            for directory in [home, saves, logs] {
                try files.createDirectory(at: directory, withIntermediateDirectories: true)
            }
            let log = logs.appendingPathComponent("engine.log")
            let descriptor = open(log.path, O_WRONLY | O_CREAT | O_TRUNC, mode_t(0o644))
            guard descriptor >= 0 else { throw LibraryError.logFile }
            let workingDirectory = info.engine == .system4 ? resources.path : game.url.path
            let arguments = Self.launchArguments(game.url, saves: saves, info: info)
            setenv("XSYSTEM4_HOME", home.path, 1)
            setenv("XSYSTEM4_CRASH_LOG_PATH", logs.appendingPathComponent("crash.log").path, 1)
            Self.setSystemSansFontDefaults()
            setenv("XSYSTEM35_FONT_GOTHIC", resources.appendingPathComponent("MTLc3m.ttf").path, 1)
            setenv("XSYSTEM35_FONT_MINCHO", resources.appendingPathComponent("mincho.ttf").path, 1)
            setenv("XSYSTEM35_FONT_FALLBACK", resources.appendingPathComponent("fonts/HanaMinA.ttf").path, 1)
            setenv("XSYSTEM35_SOUNDFONT", resources.appendingPathComponent("soundfonts/TimGM6mb.sf2").path, 1)
            launching = true
            SaveSyncSession.begin(game: saveName) {
                Timer.scheduledTimer(withTimeInterval: 0.01, repeats: false) { _ in
                    let previousDirectory = open(".", O_RDONLY)
                    let previousStdout = dup(STDOUT_FILENO)
                    let previousStderr = dup(STDERR_FILENO)
                    defer {
                        if previousDirectory >= 0 { fchdir(previousDirectory); close(previousDirectory) }
                        fflush(stdout)
                        fflush(stderr)
                        if previousStdout >= 0 { dup2(previousStdout, STDOUT_FILENO); close(previousStdout) }
                        if previousStderr >= 0 { dup2(previousStderr, STDERR_FILENO); close(previousStderr) }
                        close(descriptor)
                        SaveSyncSession.end()
                        self.launching = false
                    }
                    guard previousDirectory >= 0, chdir(workingDirectory) == 0 else {
                        self.error = LibraryError.resourcePath.localizedDescription
                        return
                    }
                    guard previousStdout >= 0, previousStderr >= 0,
                          dup2(descriptor, STDOUT_FILENO) >= 0, dup2(descriptor, STDERR_FILENO) >= 0 else {
                        self.error = LibraryError.logFile.localizedDescription
                        return
                    }
                    SDL_SetMainReady()
                    alicehub_ios_begin_game()
                    defer { alicehub_ios_end_game() }
                    if info.engine != .legacySystem3 {
                        RuntimeGamepad.prepare(for: game.id, engine: info.engine)
                    }
                    if info.engine == .system4 {
                        ModifierBridge.prepareForLaunch(gameID: game.id)
                        input_set_long_press_ctrl(UserDefaults.standard.bool(forKey: "hub.longPressCtrl"))
                    }
                    var argv = arguments.map { strdup($0) }
                    argv.append(nil)
                    argv.withUnsafeMutableBufferPointer { buffer in
                        switch info.engine {
                        case .legacySystem3:
                            _ = system3_main(Int32(arguments.count), buffer.baseAddress)
                        case .system3:
                            _ = xsystem35_main(Int32(arguments.count), buffer.baseAddress)
                        case .system4:
                            _ = xsystem4_main(Int32(arguments.count), buffer.baseAddress)
                        }
                    }
                    for argument in argv { free(argument) }
                }
            }
        } catch { self.error = error.localizedDescription }
    }
}
