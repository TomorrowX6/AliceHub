import SwiftUI

@propertyWrapper
struct HubLocalizedText: DynamicProperty {
    @Environment(\.locale) private var locale

    var wrappedValue: Self { self }

    var language: HubLanguage { HubLanguage(locale: locale) }

    func callAsFunction(_ key: String, _ arguments: CVarArg...) -> String {
        language.text(key, arguments: arguments)
    }
}
