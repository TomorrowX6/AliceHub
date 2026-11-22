import SwiftUI

struct GameArtwork: View {
    var game: Game? = nil
    var catalogID: String? = nil
    var size: CGFloat = 64

    private var artwork: UIImage? {
        let names = game.map { ((try? FileManager.default.contentsOfDirectory(atPath: $0.url.path)) ?? []) + [$0.name] } ?? []
        let normalized = names.joined(separator: " ").lowercased()
            .replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "_", with: "")
        let aliases: [(String, [String])] = [
            ("escalayer_reboot", ["escalayerreboot", "reboot"]),
            ("evenicle2rance", ["evenicle2rance", "イブニクル2ランス", "イブニクル２ランス", "夏娃年代记2兰斯", "夏娃年代記2蘭斯", "evenicle2兰斯"]),
            ("evenicle2", ["evenicle2", "イブニクル2", "夏娃年代记2"]),
            ("eveniclegaiden", ["eveniclegaiden", "eveniclegaiten", "evenicleapc", "イブニクル外典", "夏娃年代记外典", "夏娃年代記外典"]),
            ("rance10", ["rance10", "rancex", "兰斯10", "ランス10"]),
            ("dohnadohna", ["dohnadohna", "多娜多娜", "ドーナドーナ"]),
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
            ("daibanchou", ["daibanchou", "大番長", "大番长", "bigbangage"]),
            ("daiteikoku", ["daiteikoku", "大帝国", "大帝國"]),
            ("galzoo", ["galzoo"]), ("ixseal", ["ixseal", "イクシール"]),
            ("haruka", ["haruka", "ハルカ"]), ("tsumashibori", ["tsumashibori", "妻しぼり"]),
            ("tsumamigui3", ["tsumamigui3", "妻みぐい3", "妻みぐい３", "妻中蜜3", "妻中蜜３"]),
            ("tsumamigui", ["tsumamigui", "妻みぐい", "妻中蜜", "嵢傒偖偄"]),
            ("kaerunyo", ["kaerunyo", "かえるにょ", "ぱにょ", "回家扭"]),
            ("atlachnacha", ["atlachnacha", "atlach", "アトラク", "阿特拉克", "蜘蛛sa"]),
            ("reishiki", ["reishiki", "零式", "zeroshiki", "楇幃"]),
            ("haruurare", ["haruurare", "はるうられ", "haru_c", "校内赤线", "校内赤線"]),
            ("okusamajun", ["okusamajun", "太太的恢复术纯爱", "奥さまの回復術純愛", "纯爱篇", "純愛編"]),
            ("okusamantr", ["okusamantr", "太太的恢复术夺爱", "奥さまの回復術奪愛", "夺爱篇", "奪愛編"]),
            ("waidonyo", ["waidonyo", "わいどにょ", "异空窟探险", "異空窟探險"]),
            ("yakata2", ["yakata2", "aliceの館ii", "aliceの館2", "alicenoyakata2", "alicenoyakataii", "爱丽丝之馆2", "爱丽丝之馆２", "愛麗絲之館2", "愛麗絲之館２"]),
            ("sdpsmaria", ["sdpsmaria", "superdpsmaria", "superdps", "sdps", "玛利亚和坎潘", "瑪利亞和坎潘", "マリアとカンパン"]),
            ("toushin3", ["toushin3", "toushintoshi3", "toushintoshiiii", "闘神都市3", "闘神都市３", "闘神都市iii", "斗神都市3", "斗神都市３", "斗神都市iii", "鬥神都市3"]),
            ("oyakorankan", ["oyakorankan", "母娘乱館", "母娘乱馆", "母娘亂館"]),
            ("yorugakuru", ["yorugakuru", "nightremaster", "夜が来る", "邪夜将至", "邪夜將至", "squareofthemoon"]),
            ("escalayer", ["escalayer", "doki", "エスカレイヤー"]),
            ("evenicle", ["evenicle", "イブニクル", "夏娃年代记"])
        ]
        let detectedID: String? = game.flatMap { g in
            if GameLibrary.isRance41(g.url) { return "rance41" }
            if GameLibrary.isRance42(g.url) { return "rance42" }
            if GameLibrary.isRance04(g.url) { return "rance04" }
            if GameLibrary.isRance5d(g.url) { return "rance5d" }
            if GameLibrary.isYakata2(g.url) { return "yakata2" }
            if GameLibrary.isSdpsMaria(g.url) { return "sdpsmaria" }
            return nil
        }
        guard let key = catalogID ?? detectedID ?? aliases.first(where: { $0.1.contains(where: normalized.contains) })?.0,
              let path = Bundle.main.path(forResource: "ic_game_" + key, ofType: "png", inDirectory: "drawable-nodpi") else { return nil }
        return UIImage(contentsOfFile: path)
    }

    var body: some View {
        ZStack {
            HubTheme.variant
            if let artwork {
                Image(uiImage: artwork).resizable().scaledToFit().padding(3)
            } else if let game, GameLibrary.isRance04(game.url) {
                Text("IV").font(.system(size: size * 0.5, weight: .bold, design: .serif))
                    .foregroundColor(HubTheme.primary)
            } else if let game, GameLibrary.isRance5d(game.url) {
                Text("5D").font(.system(size: size * 0.5, weight: .bold, design: .serif))
                    .foregroundColor(HubTheme.primary)
            } else {
                Image(systemName: "gamecontroller.fill").font(.system(size: size * 0.4))
                    .foregroundColor(HubTheme.primary)
            }
        }.frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.24, style: .continuous))
            .accessibilityHidden(true)
    }
}
