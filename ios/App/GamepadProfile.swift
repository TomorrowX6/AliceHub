import Foundation
import CoreGraphics

func padText(_ text: String) -> String {
    let chinese = Bundle.main.path(forResource: "zh-Hans", ofType: "lproj").flatMap(Bundle.init(path:))
    return NSLocalizedString(text, tableName: "Gamepad", bundle: chinese ?? .main, comment: "")
}

enum PadMode: Int, Codable, CaseIterable, Identifiable {
    case off, mouse, arrows, wasd, wheel, joystick
    var id: Int { rawValue }
    var title: String { padText(["None", "Mouse", "Arrow keys", "WASD", "Scroll", "Gamepad"][rawValue]) }
}

struct PadAction: Codable, Equatable, Hashable {
    var type: Int32
    var code: Int32
    var code2: Int32 = 0
    var toggle = false

    static let none = Self(type: 0, code: 0)
    static let leftClick = Self(type: 2, code: 1)
    static let rightClick = Self(type: 2, code: 2)
    static let middleClick = Self(type: 2, code: 4)
    static let scrollUp = Self(type: 3, code: 1)
    static let scrollDown = Self(type: 3, code: -1)
    static let ctrl = Self(type: 4, code: 0)
    static let togglePad = Self(type: 7, code: 1)
    static let menu = Self(type: 7, code: 2)
    static let cursor = Self(type: 7, code: 3)
    static let screenshot = Self(type: 7, code: 4)
    static func key(_ code: Int32, toggle: Bool = false) -> Self { Self(type: 1, code: code, toggle: toggle) }

    static let choices: [(String, Self)] = [
        ("None", .none), ("Left click", .leftClick), ("Right click", .rightClick),
        ("Middle click", .middleClick), ("Scroll up", .scrollUp), ("Scroll down", .scrollDown),
        ("Ctrl (toggle)", .ctrl), ("Enter", .key(13)), ("Esc", .key(27)), ("Space", .key(32)),
        ("Tab", .key(9)), ("Shift", .key(16)), ("Shift (toggle)", .key(16, toggle: true)),
        ("Page Up", .key(33)), ("Page Down", .key(34)),
        ("↑", .key(38)), ("↓", .key(40)), ("←", .key(37)), ("→", .key(39)),
        ("Toggle Virtual Gamepad", .togglePad), ("Menu", .menu), ("Cursor", .cursor),
        ("Screenshot", .screenshot)
    ]
}

struct PadControl: Identifiable, Codable, Equatable {
    enum Kind: String, Codable { case dpad, leftStick, rightStick, button }
    enum Shape: String, Codable { case circle, square, pill }
    var id: String
    var slot: Int32
    var kind: Kind
    var label: String
    var x: Double
    var y: Double
    var scale: Double
    var opacity: Double
    var visible: Bool
    var shape: Shape
    var mode: PadMode
    var action: PadAction

    init(_ id: String, _ slot: Int32, _ label: String, _ x: Double, _ y: Double,
         _ scale: Double, _ opacity: Double, _ shape: Shape, _ action: PadAction = .none,
         kind: Kind = .button, mode: PadMode = .off, visible: Bool = true) {
        self.id = id; self.slot = slot; self.label = label
        self.x = x; self.y = y; self.scale = scale; self.opacity = opacity
        self.shape = shape; self.action = action; self.kind = kind; self.mode = mode; self.visible = visible
    }

    func center(in bounds: CGRect) -> CGPoint {
        CGPoint(x: bounds.minX + x * bounds.width, y: bounds.minY + y * bounds.height)
    }

    mutating func move(to point: CGPoint, in bounds: CGRect, snap: Bool) {
        guard bounds.width > 0, bounds.height > 0 else { return }
        func normalized(_ value: Double) -> Double { snap ? (value * 40).rounded() / 40 : value }
        x = min(0.96, max(0.04, normalized((point.x - bounds.minX) / bounds.width)))
        y = min(0.94, max(0.06, normalized((point.y - bounds.minY) / bounds.height)))
    }

    func size(in bounds: CGRect, globalScale: Double) -> CGSize {
        let unit = min(bounds.height, bounds.width * 0.55) * scale * globalScale
        switch (kind, shape) {
        case (.button, .pill): return CGSize(width: unit * 0.16, height: unit * 0.10)
        case (.button, _): return CGSize(width: unit * 0.14, height: unit * 0.14)
        default: return CGSize(width: unit * 0.25, height: unit * 0.25)
        }
    }
}

enum PadPreset: String, Codable, CaseIterable, Identifiable {
    case visualNovel, rpg, empty
    var id: Self { self }
    var title: String {
        switch self {
        case .visualNovel: return padText("Visual Novel / Mouse")
        case .rpg: return padText("RPG / Dungeon")
        case .empty: return padText("Empty Layout")
        }
    }

    var controls: [PadControl] {
        switch self {
        case .empty: return []
        case .visualNovel: return [
            .init("dpad", 0, "D-Pad", 0.11, 0.45, 0.90, 1, .square, kind: .dpad, mode: .arrows),
            .init("stick_l", 1, "Mouse", 0.13, 0.77, 0.96, 1, .circle, kind: .leftStick, mode: .mouse),
            .init("btn_l2", 2, "Esc", 0.06, 0.22, 0.90, 1, .pill, .key(27)),
            .init("btn_l1", 3, "Scroll up", 0.15, 0.22, 0.90, 1, .pill, .scrollUp),
            .init("btn_r1", 4, "Scroll down", 0.85, 0.22, 0.90, 1, .pill, .scrollDown),
            .init("btn_r2", 5, "Ctrl", 0.94, 0.22, 0.90, 1, .pill, .ctrl),
            .init("btn_y", 6, "Y", 0.87, 0.49, 0.96, 1, .circle, .key(13)),
            .init("btn_x", 7, "X", 0.80, 0.65, 0.96, 1, .circle, .key(32)),
            .init("btn_b", 8, "B", 0.94, 0.65, 0.96, 1, .circle, .rightClick),
            .init("btn_a", 9, "A", 0.87, 0.81, 1.02, 1, .circle, .leftClick),
            .init("btn_select", 10, "Cursor", 0.31, 0.92, 0.85, 0.9, .pill, .cursor),
            .init("btn_start", 11, "Menu", 0.69, 0.92, 0.85, 0.9, .pill, .menu)
        ]
        case .rpg: return [
            .init("dpad", 0, "D-Pad", 0.12, 0.68, 1.05, 1, .square, kind: .dpad, mode: .arrows),
            .init("stick_l", 1, "L", 0.23, 0.44, 0.85, 0.85, .circle, kind: .leftStick, mode: .mouse, visible: false),
            .init("btn_l2", 2, "L2", 0.07, 0.22, 0.90, 1, .pill, .rightClick),
            .init("btn_l1", 3, "L1", 0.16, 0.22, 0.90, 1, .pill, .scrollUp),
            .init("btn_r1", 4, "R1", 0.84, 0.22, 0.90, 1, .pill, .scrollDown),
            .init("btn_r2", 5, "Ctrl", 0.93, 0.22, 0.90, 1, .pill, .ctrl),
            .init("btn_y", 6, "Y", 0.87, 0.48, 0.96, 1, .circle, .key(9)),
            .init("btn_x", 7, "X", 0.80, 0.64, 0.96, 1, .circle, .key(32)),
            .init("btn_b", 8, "B", 0.94, 0.64, 0.96, 1, .circle, .key(27)),
            .init("btn_a", 9, "A", 0.87, 0.80, 1.02, 1, .circle, .key(13)),
            .init("btn_select", 10, "SEL", 0.34, 0.92, 0.85, 0.9, .pill, .key(16, toggle: true)),
            .init("btn_start", 11, "Menu", 0.66, 0.92, 0.85, 0.9, .pill, .menu)
        ]
        }
    }
}

struct VirtualPad: Codable, Equatable {
    var enabled = false
    var preset: PadPreset = .visualNovel
    var scale = 1.0
    var opacity = 0.58
    var snap = false
    var controls = PadPreset.visualNovel.controls

    mutating func usePreset(_ preset: PadPreset) {
        self.preset = preset
        controls = preset.controls
    }

    mutating func addControl(_ kind: PadControl.Kind) -> String? {
        guard kind == .button || !controls.contains(where: { $0.kind == kind }),
              let slot = (Int32(0)..<32).first(where: { slot in !controls.contains(where: { $0.slot == slot }) })
        else { return nil }
        let id = UUID().uuidString
        switch kind {
        case .button: controls.append(.init(id, slot, "Button", 0.5, 0.5, 1, 1, .circle, .key(13)))
        case .dpad: controls.append(.init(id, slot, "D-Pad", 0.13, 0.6, 1, 1, .square, kind: kind, mode: .arrows))
        case .leftStick: controls.append(.init(id, slot, "L", 0.13, 0.77, 1, 1, .circle, kind: kind, mode: .mouse))
        case .rightStick: controls.append(.init(id, slot, "R", 0.87, 0.77, 1, 1, .circle, kind: kind, mode: .wheel))
        }
        return id
    }

    func mode(for kind: PadControl.Kind) -> PadMode {
        controls.first { $0.kind == kind && $0.visible }?.mode ?? .off
    }
}

struct PhysicalPad: Codable, Equatable {
    static let slots = ["A", "B", "X", "Y", "Back", "Guide", "Start", "L3", "R3", "L1", "R1", "↑", "↓", "←", "→", "L2", "R2"]
    static let defaults: [PadAction] = [
        .leftClick, .rightClick, .key(32), .key(13), .cursor, .togglePad, .menu, .key(16),
        .middleClick, .scrollUp, .scrollDown, .none, .none, .none, .none, .key(27), .ctrl
    ]
    static let rpgBindings: [PadAction] = [
        .key(13), .key(27), .key(32), .key(9), .cursor, .togglePad, .menu, .key(16),
        .leftClick, .key(33), .key(34), .none, .none, .none, .none, .rightClick, .ctrl
    ]

    var enabled = false
    var autoHide = false
    var swapAbXy = false
    var leftStickMode: PadMode = .mouse
    var rightStickMode: PadMode = .wheel
    var dpadMode: PadMode = .arrows
    var deadzonePct = 18.0
    var cursorSpeedPct = 100.0
    var bindings = defaults
}

struct GamepadProfile: Codable, Equatable {
    var virtual = VirtualPad()
    var physical = PhysicalPad()

    func engineJSON() -> String {
        struct VirtualConfig: Encodable {
            let leftStickMode: PadMode
            let rightStickMode: PadMode
        }
        struct PhysicalConfig: Encodable {
            let enabled: Bool
            let swapAbXy: Bool
            let leftStickMode: PadMode
            let rightStickMode: PadMode
            let dpadMode: PadMode
            let deadzonePct: Int
            let cursorSpeedPct: Int
            let bindings: [String: PadAction]
        }
        struct Config: Encodable {
            let virtual: VirtualConfig
            let physical: PhysicalConfig
        }
        let config = Config(
            virtual: VirtualConfig(leftStickMode: virtual.mode(for: .leftStick), rightStickMode: virtual.mode(for: .rightStick)),
            physical: PhysicalConfig(enabled: physical.enabled, swapAbXy: physical.swapAbXy,
                leftStickMode: physical.leftStickMode, rightStickMode: physical.rightStickMode,
                dpadMode: physical.dpadMode, deadzonePct: min(45, max(5, Int(physical.deadzonePct.rounded()))),
                cursorSpeedPct: min(300, max(20, Int(physical.cursorSpeedPct.rounded()))),
                bindings: Dictionary(uniqueKeysWithValues: physical.bindings.enumerated().map { (String($0.offset), $0.element) })))
        guard let data = try? JSONEncoder().encode(config) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }
}

struct GamepadProfiles {
    var defaults = UserDefaults.standard

    private func key(_ game: String?) -> String {
        guard let game else { return "gamepad.global" }
        return "gamepad.game." + Data(game.utf8).base64EncodedString()
    }

    func saved(_ game: String?) -> GamepadProfile? {
        guard let data = defaults.data(forKey: key(game)),
              let profile = try? JSONDecoder().decode(GamepadProfile.self, from: data),
              profile.physical.bindings.count == PhysicalPad.slots.count else { return nil }
        return profile
    }

    func active(_ game: String?) -> GamepadProfile {
        saved(game) ?? saved(nil) ?? GamepadProfile()
    }

    func save(_ profile: GamepadProfile, for game: String?) {
        guard let data = try? JSONEncoder().encode(profile) else { return }
        defaults.set(data, forKey: key(game))
    }

    func removeOverride(for game: String) {
        defaults.removeObject(forKey: key(game))
    }
}
