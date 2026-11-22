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
            ("evenicle2", ["evenicle2", "イブニクル2", "夏娃年代记2"]),
            ("rance10", ["rance10", "rancex", "兰斯10", "ランス10"]),
            ("dohnadohna", ["dohnadohna", "多娜多娜", "ドーナドーナ"]),
            ("sengokurance", ["sengoku", "戦国", "战国"]),
            ("rance01", ["rance01", "兰斯01", "ランス01"]),
            ("rance02", ["rance02", "兰斯02", "ランス02"]),
            ("rance03", ["rance03", "兰斯03", "ランス03"]),
            ("rance06", ["rance06", "兰斯6", "ランス6"]),
            ("rance08", ["rance08", "rance8", "兰斯8", "ランスクエスト"]),
            ("rance09", ["rance09", "rance9", "兰斯9", "ランス9"]),
            ("kichikuou", ["kichiku", "鬼畜王"]),
            ("daiakuji", ["daiakuji", "大悪司", "大恶司"]),
            ("galzoo", ["galzoo"]), ("ixseal", ["ixseal", "イクシール"]),
            ("haruka", ["haruka", "ハルカ"]), ("tsumashibori", ["tsumashibori", "妻しぼり"]),
            ("tsumamigui3", ["tsumamigui3", "妻みぐい3", "妻みぐい３", "妻中蜜3", "妻中蜜３"]),
            ("escalayer", ["escalayer", "doki", "エスカレイヤー"]),
            ("evenicle", ["evenicle", "イブニクル", "夏娃年代记"])
        ]
        guard let key = catalogID ?? aliases.first(where: { $0.1.contains(where: normalized.contains) })?.0,
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
