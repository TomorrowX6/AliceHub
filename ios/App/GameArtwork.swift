import SwiftUI

struct GameArtwork: View {
    var game: Game? = nil
    var catalogID: String? = nil
    var size: CGFloat = 64

    private static let imageCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 64
        return cache
    }()

    private static func cachedImage(forKey key: String) -> UIImage? {
        let nsKey = key as NSString
        if let cached = imageCache.object(forKey: nsKey) {
            return cached
        }
        guard let path = Bundle.main.path(forResource: "ic_game_" + key, ofType: "png", inDirectory: "drawable-nodpi"),
              let image = UIImage(contentsOfFile: path) else { return nil }
        imageCache.setObject(image, forKey: nsKey)
        return image
    }

    private var artwork: UIImage? {
        guard let key = catalogID ?? game?.artworkKey else { return nil }
        return Self.cachedImage(forKey: key)
    }

    var body: some View {
        ZStack {
            HubTheme.variant
            if let artwork {
                Image(uiImage: artwork).resizable().scaledToFit().padding(3)
            } else if let game, game.isRance04 {
                Text("IV").font(.system(size: size * 0.5, weight: .bold, design: .serif))
                    .foregroundColor(HubTheme.primary)
            } else if let game, game.isRance5d {
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
