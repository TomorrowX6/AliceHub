import SwiftUI
import UIKit

enum HubTheme {
    static let background = adaptive(0xF3F4F8, 0x0E1014)
    static let surface = adaptive(0xFFFFFF, 0x181B21)
    static let variant = adaptive(0xECEEF5, 0x242832)
    static let primary = adaptive(0x3F51B5, 0xA9B6FF)
    static let onPrimary = adaptive(0xFFFFFF, 0x0F1A5C)
    static let text = adaptive(0x171A21, 0xECEEF4)
    static let secondary = adaptive(0x5B6170, 0xA4AAB8)
    static let success = adaptive(0x1F7A3D, 0x7FD79A)
    static let successBackground = adaptive(0xE0F4E6, 0x12301D)
    static let primaryChip = adaptive(0x2F3FA6, 0xC3CCFF)
    static let primaryChipBackground = adaptive(0xE4E7F8, 0x2B3568)
    static let warning = adaptive(0x9A5B00, 0xFFCB6B)
    static let warningBackground = adaptive(0xFFF0D1, 0x3A2A0A)

    private static func adaptive(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { traits in
            let value = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((value >> 16) & 255) / 255,
                           green: CGFloat((value >> 8) & 255) / 255,
                           blue: CGFloat(value & 255) / 255, alpha: 1)
        })
    }
}

struct HubCard: ViewModifier {
    func body(content: Content) -> some View {
        content.frame(maxWidth: .infinity, alignment: .leading).padding(20)
            .background(HubTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

struct HubListStyle: ViewModifier {
    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.scrollContentBackground(.hidden).background(HubTheme.background)
        } else {
            content.background(HubTheme.background)
        }
    }
}

struct HubPrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).frame(maxWidth: .infinity, minHeight: 52)
            .foregroundColor(HubTheme.onPrimary)
            .background(HubTheme.primary.opacity(configuration.isPressed ? 0.75 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct HubBadge: View {
    enum Tone {
        case neutral, success, primary, warning
    }

    let title: String
    let tone: Tone

    init(title: String, tone: Tone) {
        self.title = title
        self.tone = tone
    }

    init(title: String, success: Bool = false) {
        self.title = title
        self.tone = success ? .success : .neutral
    }

    private var foreground: Color {
        switch tone {
        case .neutral: return HubTheme.secondary
        case .success: return HubTheme.success
        case .primary: return HubTheme.primaryChip
        case .warning: return HubTheme.warning
        }
    }

    private var background: Color {
        switch tone {
        case .neutral: return HubTheme.variant
        case .success: return HubTheme.successBackground
        case .primary: return HubTheme.primaryChipBackground
        case .warning: return HubTheme.warningBackground
        }
    }

    var body: some View {
        Text(title).font(.caption.weight(.medium))
            .padding(.horizontal, 9).padding(.vertical, 5)
            .foregroundColor(foreground)
            .background(background)
            .clipShape(Capsule())
    }
}

struct HubSectionHeader: View {
    let title: String
    var subtitle: String? = nil
    let symbol: String
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: symbol).font(.title2)
                .foregroundColor(HubTheme.primary).frame(width: 48, height: 48)
                .background(HubTheme.variant).clipShape(RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 5) {
                Text(title).font(.headline).foregroundColor(HubTheme.text)
                if let subtitle {
                    Text(subtitle).font(.subheadline).foregroundColor(HubTheme.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }.padding(.vertical, 8).accessibilityElement(children: .combine)
    }
}

struct HubActionLabel: View {
    let title: String
    let symbol: String
    var destructive = false
    var navigates = true
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: symbol).font(.system(size: 18, weight: .medium))
                .foregroundColor(destructive ? .red : HubTheme.primary)
                .frame(width: 42, height: 42).background(HubTheme.variant)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            Text(title).font(.body.weight(.semibold)).foregroundColor(destructive ? .red : HubTheme.text)
            Spacer(minLength: 8)
            if navigates {
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundColor(HubTheme.secondary)
            }
        }.padding(.vertical, 7).contentShape(Rectangle())
    }
}

enum HubAppearance: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var title: String { self == .system ? "跟随系统" : self == .light ? "浅色" : "深色" }
    var scheme: ColorScheme? { self == .system ? nil : self == .light ? .light : .dark }
}

struct HubAppearanceModifier: ViewModifier {
    @AppStorage("hub.appearance") private var appearance = HubAppearance.system.rawValue
    func body(content: Content) -> some View {
        content.tint(HubTheme.primary).accentColor(HubTheme.primary)
            .preferredColorScheme(HubAppearance(rawValue: appearance)?.scheme)
    }
}
