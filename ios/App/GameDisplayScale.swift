import Foundation

enum GameDisplayScale: String, CaseIterable, Identifiable {
    case font, scrollbar

    static let defaultPercent = 100

    var id: Self { self }
    var range: ClosedRange<Int> { self == .font ? 75...160 : 100...300 }
    var step: Int { self == .font ? 5 : 10 }
    var presets: [Int] { self == .font ? [80, 90, 100, 115, 130, 150] : [100, 130, 160, 200, 250, 300] }

    func clamped(_ percent: Int) -> Int {
        min(max(percent, range.lowerBound), range.upperBound)
    }

    func percent(for game: String, defaults: UserDefaults = .standard) -> Int {
        clamped(defaults.object(forKey: key(for: game)) as? Int ?? Self.defaultPercent)
    }

    func save(_ percent: Int, for game: String, defaults: UserDefaults = .standard) {
        let value = clamped(percent)
        if value == Self.defaultPercent {
            defaults.removeObject(forKey: key(for: game))
        } else {
            defaults.set(value, forKey: key(for: game))
        }
    }

    private func key(for game: String) -> String {
        "hub.displayScale.\(rawValue).\(game)"
    }
}
