import Foundation

enum HubLanguage: String, CaseIterable, Identifiable {
    static let storageKey = "hub.language"

    case system, chinese = "zh-Hans", japanese = "ja", english = "en"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: return "跟随系统"
        case .chinese: return "简体中文"
        case .japanese: return "日本語"
        case .english: return "English"
        }
    }

    static var current: Self {
        Self(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .system
    }

    func resolved(preferredLanguages: [String] = Locale.preferredLanguages) -> Self {
        guard self == .system else { return self }
        for language in preferredLanguages {
            switch Locale(identifier: language).languageCode {
            case "zh": return .chinese
            case "ja": return .japanese
            case "en": return .english
            default: continue
            }
        }
        return .english
    }

    var locale: Locale { Locale(identifier: resolved().rawValue) }

    func text(_ key: String, table: String? = nil, arguments: [CVarArg] = [], bundle: Bundle = .main) -> String {
        let language = resolved()
        let url = bundle.resourceURL?.appendingPathComponent(language.rawValue + ".lproj", isDirectory: true)
        let localizedBundle = url.flatMap(Bundle.init(url:))
        let format = localizedBundle?.localizedString(forKey: key, value: key, table: table) ?? key
        return arguments.isEmpty ? format : String(format: format, locale: language.locale, arguments: arguments)
    }
}

func hubText(_ key: String, _ arguments: CVarArg...) -> String {
    HubLanguage.current.text(key, arguments: arguments)
}
