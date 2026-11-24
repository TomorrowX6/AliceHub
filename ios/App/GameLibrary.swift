import Foundation
import Combine
import Darwin

enum GameEngine: String {
    case legacySystem3 = "System 3"
    case system3 = "System 3.x"
    case system4 = "System 4"
}

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
    case notGame, archiveNotGame, alreadyExists, resourcePath, logFile
    var errorDescription: String? {
        switch self {
        case .notGame: return "所选文件夹不是 System 3.x / System 4 游戏。请确认根目录包含游戏的 INI、AIN、*SA.ALD、ADISK.ALD 或 ADISK.DAT 和 ACG.DAT 文件。"
        case .archiveNotGame: return "压缩包或光盘镜像中未找到 System 3.x / System 4 游戏。"
        case .alreadyExists: return "已存在同名游戏文件夹，请先删除或重命名后再导入。"
        case .resourcePath: return "无法找到游戏引擎资源。"
        case .logFile: return "无法创建运行日志。"
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
        ("sengokurance", ["sengoku", "戦国", "战国"]),
        ("rance1digest", ["rance1digest", "rance1.ain", "兰斯1摘要", "兰斯１摘要", "蘭斯1摘要", "ランス1ダイジェスト", "ランス１ダイジェスト"]),
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
        ("yakata3", ["yakata3", "aliceの館3", "aliceの館３", "aliceの館iii", "alicenoyakata3", "alicenoyakataiii", "爱丽丝之馆3", "爱丽丝之馆３", "愛麗絲之館3", "愛麗絲之館３"]),
        ("yakata2", ["yakata2", "aliceの館ii", "aliceの館2", "alicenoyakata2", "alicenoyakataii", "爱丽丝之馆2", "爱丽丝之馆２", "愛麗絲之館2", "愛麗絲之館２"]),
        ("koredps", ["koredps", "これdps", "これｄｐｓ", "これd.p.s", "这是dps", "這是dps", "这是d.p.s", "這是d.p.s"]),
        ("dpsall", ["dpsall", "d.p.s.全部", "dps全部", "d.p.s.zenbu", "dpszenbu"]),
        ("sdpsmaria", ["sdpsmaria", "superdpsmaria", "superdps", "sdps", "玛利亚和坎潘", "瑪利亞和坎潘", "マリアとカンパン"]),
        ("toushin3", ["toushin3", "toushintoshi3", "toushintoshiiii", "闘神都市3", "闘神都市３", "闘神都市iii", "斗神都市3", "斗神都市３", "斗神都市iii", "鬥神都市3"]),
        ("toushin2sp", ["toushin2sp", "soshitesorekara", "そしてそれから", "然后从那以后", "然後從那以後"]),
        ("toushin2", ["toushin2", "toushintoshi2", "toushintoshiii", "tt2sa", "闘神都市2", "闘神都市２", "闘神都市ii", "斗神都市2", "斗神都市２", "斗神都市ii", "鬥神都市2"]),
        ("toushin1", ["toushin1", "toushinsa", "toushintoshi", "闘神都市", "斗神都市", "鬥神都市"]),
        ("diabolique", ["diabolique", "diabosa", "デアボリカ", "魔鬼恋曲"]),
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
        ("ayumi", ["ayumichan", "ayumi", "あゆみちゃん", "步美物语", "步美物語"]),
        ("cielclaire", ["cielclaire", "chairscruelles", "シェルクレイル", "逃避行sa", "残酷的爱恋", "殘酷的愛戀"]),
        ("nightdemon", ["nightdemon", "ナイトデーモン", "夢鬼", "梦鬼"]),
        ("katsumi", ["katsumi", "いけないかつみ", "かつみsa", "胜美大夫", "勝美大夫"]),
        ("drstop", ["drstop", "dr.stop", "dssa.ald"]),
        ("prostudentgood", ["prostudentgood", "prostu★dent", "sr猿", "ｓｒ猿", "ぷろすちゅ★でんと", "职业学生good", "職業學生good"]),
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
        ("oyakorankan", ["oyakorankan", "母娘乱館", "母娘乱馆", "母娘亂館"]),
        ("yorugakuru", ["yorugakuru", "nightremaster", "夜が来る", "邪夜将至", "邪夜將至", "squareofthemoon"]),
        ("maman", ["maman", "heartfulmaman", "は～とふるママン", "は〜とふるママン", "はーとふるママン", "再婚妈妈", "再婚媽媽"]),
        ("haharanman", ["haharanman", "母爛漫", "母烂漫", "曣椆枤"]),
        ("escalayer", ["escalayer", "doki", "エスカレイヤー"]),
        ("evenicle", ["evenicle", "イブニクル", "夏娃年代记"])
    ]

    private static func launchInfoFromRegularFiles(_ url: URL, regularFiles: [URL]) throws -> (engine: GameEngine, target: URL, system39: Bool) {
        if regularFiles.contains(where: { ["alicestart.chs", "alicestart.ini", "alicestart2.ini", "system40.ini"].contains($0.lastPathComponent.lowercased()) }) {
            return (.system4, url, false)
        }
        if let ain = regularFiles.filter({ $0.pathExtension.lowercased() == "ain" && $0.lastPathComponent.lowercased() != "system39.ain" })
            .sorted(by: { $0.lastPathComponent < $1.lastPathComponent }).first {
            return (.system4, ain, false)
        }
        if regularFiles.contains(where: { $0.lastPathComponent.lowercased() == "chs.tbl" }),
           let chsAin = regularFiles.filter({
               $0.pathExtension.lowercased() == "chs" && $0.lastPathComponent.lowercased() != "alicestart.chs"
           }).sorted(by: { $0.lastPathComponent < $1.lastPathComponent }).first {
            return (.system4, chsAin, false)
        }
        let names = regularFiles.map { $0.lastPathComponent.lowercased() }
        let system39 = names.contains("system39.ini") || names.contains("system39.ain")
        if system39 || names.contains(where: { $0.hasSuffix("sa.ald") || $0.hasSuffix("sa_chs.ald") || $0 == "adisk.ald" }) {
            return (.system3, url, system39)
        }
        if names.contains("adisk.dat") && (names.contains("acg.dat") || names.contains("acg0.dat")) {
            return (.legacySystem3, url, false)
        }
        throw LibraryError.notGame
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

    private static func legacyAdiskCrc32FromRegularFiles(_ regularFiles: [URL]) -> UInt32? {
        guard let adisk = regularFiles.first(where: { $0.lastPathComponent.lowercased() == "adisk.dat" }),
              let handle = try? FileHandle(forReadingFrom: adisk) else { return nil }
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

    private static func isAyumiFromRegularFiles(_ regularNamesLower: Set<String>, legacyId: String?, adiskCrc: UInt32?) -> Bool {
        if let legacyId {
            return legacyId == "ayumi_cd" || legacyId == "ayumi" || legacyId == "ayumi_fd" ||
                legacyId == "ayumi_proto" || legacyId == "ayumi_live_256" || legacyId == "ayumi_live_full"
        }
        let ayumiCrcs: Set<UInt32> = [0xd2bed9ee, 0x4e2fed2a, 0x4e2f5678, 0x00d15a2b, 0x5f66ff1d]
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
              let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]) else {
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

        let rance04 = engine == .system3 && regularNamesLower.contains("rance4sa.ald")
        let rance41 = (engine == .system3 && regularNamesLower.contains("ran41_sa.ald")) ||
            (legacyId.map { $0 == "rance41" || $0 == "rance41_eng" || $0 == "rance4.1" } ?? false)
        let rance42 = (engine == .system3 && regularNamesLower.contains("ran42_sa.ald")) ||
            (legacyId.map { $0 == "rance42" || $0 == "rance42_eng" || $0 == "rance4.2" } ?? false)
        let rance5d = engine == .system3 && regularNamesLower.contains("r5d_sa.ald")
        let yakata2 = legacyId.map { $0 == "yakata2" || $0 == "yakata2_eng" } ?? false
        let sdpsMaria = legacyId.map { $0 == "sdps_maria" || $0 == "sdps_maria_eng" || $0 == "sdps" || $0 == "superdps" } ?? false
        let gakuenKing = engine == .legacySystem3 && isGakuenKingFromRegularFiles(regularNamesLower, legacyId: legacyId, adiskCrc: adiskCrc)
        let onlyYou = engine == .legacySystem3 && isOnlyYouFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let ayumi = engine == .legacySystem3 && isAyumiFromRegularFiles(regularNamesLower, legacyId: legacyId, adiskCrc: adiskCrc)
        let dpsAll = engine == .legacySystem3 && isDpsAllFromRegularFiles(regularNamesLower, legacyId: legacyId, adiskCrc: adiskCrc)
        let prostudentG = engine == .legacySystem3 && isProstudentGFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let yakata3 = engine == .legacySystem3 && isYakata3FromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let ambivalenz = engine == .legacySystem3 && isAmbivalenzFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let funnyBee = engine == .legacySystem3 && isFunnyBeeFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let toushin2Sp = engine == .legacySystem3 && isToushin2SpFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let mugenHouyou = engine == .legacySystem3 && isMugenHouyouFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)
        let otomeSenki = engine == .legacySystem3 && isOtomeSenkiFromRegularFiles(legacyId: legacyId, adiskCrc: adiskCrc)

        let displayName: String
        if rance04 { displayName = "兰斯IV 教团的遗产" }
        else if rance41 { displayName = "兰斯4.1 拯救制药厂！" }
        else if rance42 { displayName = "兰斯4.2 天使组" }
        else if rance5d { displayName = "兰斯5D" }
        else if yakata2 { displayName = "爱丽丝之馆2" }
        else if sdpsMaria { displayName = "Super D.P.S 玛利亚和坎潘" }
        else if gakuenKing { displayName = "学园KING" }
        else if onlyYou { displayName = "Only You 世纪末的朱丽叶们" }
        else if ayumi { displayName = "步美物语" }
        else if dpsAll { displayName = "D.P.S. 全部" }
        else if prostudentG { displayName = "职业学生G" }
        else if yakata3 { displayName = "爱丽丝之馆3" }
        else if ambivalenz { displayName = "AmbivalenZ -二律背反-" }
        else if funnyBee { displayName = "宇宙快盗Funny Bee" }
        else if toushin2Sp { displayName = "斗神都市II 然后，从那以后…" }
        else if mugenHouyou { displayName = "梦幻泡影" }
        else if otomeSenki { displayName = "乙女战记" }
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
        let isGirls = engine == .system4 && (regularNamesLower.contains("ｇｉｒｌｓ.ain") || regularNamesLower.contains("girls.ain") || dirLower == "girls")
        let isDebut = engine == .system4 && (regularNamesLower.contains("ｄｅｂｕｔ.ain") || regularNamesLower.contains("debut.ain") || dirLower == "debut")

        let detectedArtworkID: String?
        if rance41 { detectedArtworkID = "rance41" }
        else if rance42 { detectedArtworkID = "rance42" }
        else if rance04 { detectedArtworkID = "rance04" }
        else if rance5d { detectedArtworkID = "rance5d" }
        else if yakata2 { detectedArtworkID = "yakata2" }
        else if sdpsMaria { detectedArtworkID = "sdpsmaria" }
        else if gakuenKing { detectedArtworkID = "gakuenking" }
        else if onlyYou { detectedArtworkID = "onlyyou" }
        else if ayumi { detectedArtworkID = "ayumi" }
        else if dpsAll { detectedArtworkID = "dpsall" }
        else if prostudentG { detectedArtworkID = "prostudentg" }
        else if yakata3 { detectedArtworkID = "yakata3" }
        else if ambivalenz { detectedArtworkID = "ambivalenz" }
        else if funnyBee { detectedArtworkID = "funnybee" }
        else if toushin2Sp { detectedArtworkID = "toushin2sp" }
        else if mugenHouyou { detectedArtworkID = "mugenhouyou" }
        else if otomeSenki { detectedArtworkID = "otomesenki" }
        else if erumeiWakare { detectedArtworkID = "erumeiwakare" }
        else if erumeiFlower { detectedArtworkID = "erumeiflower" }
        else if isGirls { detectedArtworkID = "girls" }
        else if isDebut { detectedArtworkID = "debut" }
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

        let hasLocalChinese = hasLocalChineseMarkers(fileNames: regularNamesLower)
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

    static func launchInfo(_ url: URL) throws -> (engine: GameEngine, target: URL, system39: Bool) {
        guard (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true,
              let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]) else { throw LibraryError.notGame }
        let regularFiles = contents.filter {
            (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
        return try launchInfoFromRegularFiles(url, regularFiles: regularFiles)
    }

    static func launchArguments(_ url: URL, saves: URL) throws -> [String] {
        let info = try launchInfo(url)
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
        guard !importing && !launching else { return }
        importing = true
        error = nil
        importProgress = "正在复制游戏文件夹…"
        let destination = gamesDirectory.appendingPathComponent(source.lastPathComponent, isDirectory: true)
        DispatchQueue.global(qos: .userInitiated).async {
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            var result: Error?
            let temporary = self.gamesDirectory.appendingPathComponent(".import-\(UUID().uuidString)")
            do {
                let coordinator = NSFileCoordinator()
                var coordinationError: NSError?
                coordinator.coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { readable in
                    do {
                        guard Self.isGame(readable) else { throw LibraryError.notGame }
                        guard !self.files.fileExists(atPath: destination.path) else { throw LibraryError.alreadyExists }
                        try self.files.copyItem(at: readable, to: temporary)
                        try Self.normalizeRance06DungeonData(temporary)
                        try self.files.moveItem(at: temporary, to: destination)
                    } catch { result = error }
                }
                if let coordinationError { throw coordinationError }
            } catch { result = error }
            try? self.files.removeItem(at: temporary)
            let message = result?.localizedDescription
            DispatchQueue.main.async {
                self.importing = false
                self.importProgress = ""
                self.refresh()
                self.error = message
            }
        }
    }

    private static func gameRoot(in stage: URL) throws -> URL? {
        var level = [stage]
        while !level.isEmpty {
            for directory in level where isGame(directory) { return directory }
            level = try level.flatMap { directory in
                try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isDirectoryKey, .isSymbolicLinkKey], options: .skipsHiddenFiles)
                    .filter {
                        let values = try $0.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
                        return values.isDirectory == true && values.isSymbolicLink != true
                    }
            }.sorted { $0.path < $1.path }
        }
        return nil
    }

    private static func containedFiles(in stage: URL) throws -> [URL] {
        var result = [URL]()
        var failure: Error?
        let keys: [URLResourceKey] = [.isRegularFileKey, .isSymbolicLinkKey]
        let enumerator = FileManager.default.enumerator(at: stage, includingPropertiesForKeys: keys,
                                                        options: .skipsHiddenFiles) { _, error in
            failure = error
            return false
        }
        while let file = enumerator?.nextObject() as? URL {
            let values = try file.resourceValues(forKeys: Set(keys))
            if values.isRegularFile == true && values.isSymbolicLink != true { result.append(file) }
        }
        if let failure { throw failure }
        let sized: [(url: URL, size: Int)] = try result.map { url in
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            return (url, size)
        }
        return sized.sorted {
            $0.size == $1.size ? $0.url.path < $1.url.path : $0.size > $1.size
        }.map { $0.url }
    }

    private static func archiveBaseName(_ source: URL) -> String {
        let name = source.lastPathComponent
        for suffix in [".tar.gz", ".tar.xz"] where name.lowercased().hasSuffix(suffix) {
            return String(name.dropLast(suffix.count))
        }
        return source.deletingPathExtension().lastPathComponent
    }

    func importArchive(_ source: URL) {
        guard !importing && !launching else { return }
        importing = true
        error = nil
        importProgress = "正在解压：0 MB"
        DispatchQueue.global(qos: .userInitiated).async {
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            let temporary = self.gamesDirectory.appendingPathComponent(".import-\(UUID().uuidString)")
            var result: Error?
            do {
                try self.files.createDirectory(at: temporary, withIntermediateDirectories: true)
                let coordinator = NSFileCoordinator()
                var coordinationError: NSError?
                coordinator.coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { readable in
                    do {
                        var lastProgress = Date.distantPast
                        var completedBytes: Int64 = 0
                        func extract(_ archive: URL, to stage: URL) throws {
                            var latestBytes: Int64 = 0
                            try ArchiveExtractor.extract(archive, to: stage, onProgress: { bytes, entry in
                                latestBytes = bytes
                                let now = Date()
                                guard now.timeIntervalSince(lastProgress) >= 0.15 else { return }
                                lastProgress = now
                                let text = "正在解压：\((completedBytes + bytes) / (1024 * 1024)) MB\n\(entry)"
                                DispatchQueue.main.async { self.importProgress = text }
                            })
                            completedBytes += latestBytes
                        }
                        func findGame(in stage: URL, depth: Int) throws -> (root: URL, stage: URL)? {
                            if let root = try Self.gameRoot(in: stage) { return (root, stage) }
                            guard depth < 3 else { return nil }
                            let contained = try Self.containedFiles(in: stage)
                            let discs = contained.filter {
                                ["iso", "mdf"].contains($0.pathExtension.lowercased()) && (try? Iso9660Extractor.containsGameRoot($0)) == true
                            }
                            let candidates = discs + contained.filter { !discs.contains($0) && ArchiveExtractor.isSupportedArchiveName($0.lastPathComponent) }
                            for nested in candidates {
                                let next = temporary.appendingPathComponent("stage-\(UUID().uuidString)")
                                do {
                                    try extract(nested, to: next)
                                    if let found = try findGame(in: next, depth: depth + 1) { return found }
                                } catch {}
                                if self.files.fileExists(atPath: next.path) { try self.files.removeItem(at: next) }
                            }
                            return nil
                        }
                        let initial = temporary.appendingPathComponent("stage0")
                        try extract(readable, to: initial)
                        guard let (root, stage) = try findGame(in: initial, depth: 0) else { throw LibraryError.archiveNotGame }
                        let name = root == stage ? Self.archiveBaseName(source) : root.lastPathComponent
                        guard !name.isEmpty && name != "." && name != ".." && !name.hasPrefix(".") else { throw LibraryError.archiveNotGame }
                        let destination = self.gamesDirectory.appendingPathComponent(name, isDirectory: true)
                        guard !self.files.fileExists(atPath: destination.path) else { throw LibraryError.alreadyExists }
                        try Self.normalizeRance06DungeonData(root)
                        try self.files.moveItem(at: root, to: destination)
                    } catch { result = error }
                }
                if let coordinationError { throw coordinationError }
            } catch { result = error }
            try? self.files.removeItem(at: temporary)
            let message = result?.localizedDescription
            DispatchQueue.main.async {
                self.importing = false
                self.importProgress = ""
                self.refresh()
                self.error = message
            }
        }
    }

    func delete(_ game: Game) {
        do {
            try files.removeItem(at: game.url)
            refresh()
        } catch { self.error = error.localizedDescription }
    }

    func savesDirectory(for game: Game) -> URL {
        documents.appendingPathComponent("Saves").appendingPathComponent(game.name)
    }

    func launch(_ game: Game) {
        guard !launching else { return }
        do {
            let info = try Self.launchInfo(game.url)
            guard let resources = Bundle.main.resourceURL else { throw LibraryError.resourcePath }
            let home = files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("xsystem4")
            let saves = savesDirectory(for: game)
            let logs = documents.appendingPathComponent("Logs")
            for directory in [home, saves, logs] {
                try files.createDirectory(at: directory, withIntermediateDirectories: true)
            }
            let log = logs.appendingPathComponent("engine.log")
            let descriptor = open(log.path, O_WRONLY | O_CREAT | O_TRUNC, mode_t(0o644))
            guard descriptor >= 0 else { throw LibraryError.logFile }
            defer { close(descriptor) }
            guard dup2(descriptor, STDOUT_FILENO) >= 0, dup2(descriptor, STDERR_FILENO) >= 0 else { throw LibraryError.logFile }
            setbuf(stdout, nil)
            setbuf(stderr, nil)
            guard chdir(info.engine == .system4 ? resources.path : game.url.path) == 0 else { throw LibraryError.resourcePath }
            setenv("XSYSTEM4_HOME", home.path, 1)
            setenv("XSYSTEM4_CRASH_LOG_PATH", logs.appendingPathComponent("crash.log").path, 1)
            setenv("XSYSTEM35_FONT_GOTHIC", resources.appendingPathComponent("MTLc3m.ttf").path, 1)
            setenv("XSYSTEM35_FONT_MINCHO", resources.appendingPathComponent("mincho.ttf").path, 1)
            setenv("XSYSTEM35_FONT_FALLBACK", resources.appendingPathComponent("fonts/HanaMinA.ttf").path, 1)
            setenv("XSYSTEM35_SOUNDFONT", resources.appendingPathComponent("soundfonts/TimGM6mb.sf2").path, 1)
            let arguments = try Self.launchArguments(game.url, saves: saves)
            ModifierBridge.prepareForLaunch()
            launching = true
            DispatchQueue.main.async {
                Timer.scheduledTimer(withTimeInterval: 0.01, repeats: false) { _ in
                    SDL_SetMainReady()
                    if info.engine == .system4 { RuntimeGamepad.prepare(for: game.id) }
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
                    exit(0)
                }
            }
        } catch { self.error = error.localizedDescription }
    }
}
