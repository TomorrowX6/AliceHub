import Foundation

func SDL_SetMainReady() {}
enum RuntimeGamepad { static func prepare(for game: String) {} }
func xsystem4_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}

@main
struct LibraryChecks {
    static func main() throws {
        checkGamepadProfiles()
        let files = FileManager.default
        let root = files.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? files.removeItem(at: root) }
        let documents = root.appendingPathComponent("Documents")
        let source = root.appendingPathComponent("测试游戏")
        try files.createDirectory(at: source, withIntermediateDirectories: true)
        let library = GameLibrary(documents: documents)
        func samePath(_ first: URL, _ second: URL) -> Bool {
            first.resolvingSymlinksInPath().path == second.resolvingSymlinksInPath().path
        }
        precondition(library.games.isEmpty && library.error == nil)
        precondition(!GameLibrary.isGame(source))
        for marker in ["AliceStart.ini", "System40.ini", "test.AIN"] {
            let file = source.appendingPathComponent(marker)
            try Data().write(to: file)
            precondition(GameLibrary.isGame(source))
            let target = try GameLibrary.launchTarget(source)
            precondition(samePath(target, marker == "test.AIN" ? file : source))
            try files.removeItem(at: file)
        }
        try files.createDirectory(at: source.appendingPathComponent("fake.ain"), withIntermediateDirectories: false)
        precondition(!GameLibrary.isGame(source))
        do {
            _ = try GameLibrary.launchTarget(source)
            preconditionFailure("A marker directory must not be a launch target")
        } catch LibraryError.notGame {}
        do {
            _ = try GameLibrary.launchTarget(root.appendingPathComponent("missing"))
            preconditionFailure("A missing game must not be a launch target")
        } catch LibraryError.notGame {}
        let firstAIN = source.appendingPathComponent("a.ain")
        let secondAIN = source.appendingPathComponent("z.ain")
        try Data().write(to: secondAIN)
        try Data().write(to: firstAIN)
        let selectedAIN = try GameLibrary.launchTarget(source)
        precondition(samePath(selectedAIN, firstAIN))
        try Data().write(to: source.appendingPathComponent("sYsTeM40.InI"))
        let selectedINI = try GameLibrary.launchTarget(source)
        precondition(samePath(selectedINI, source))
        try files.removeItem(at: source.appendingPathComponent("sYsTeM40.InI"))
        try files.removeItem(at: firstAIN)
        try files.removeItem(at: secondAIN)
        try Data().write(to: source.appendingPathComponent("System40.ini"))
        func awaitImport() {
            let deadline = Date().addingTimeInterval(10)
            while library.importing && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            }
            precondition(!library.importing, "Import timed out")
        }
        library.importFolder(source)
        awaitImport()
        precondition(library.games.count == 1 && library.error == nil)
        precondition(files.fileExists(atPath: source.path))
        library.importFolder(source)
        awaitImport()
        precondition(library.games.count == 1 && library.error == LibraryError.alreadyExists.localizedDescription)
        library.delete(library.games[0])
        precondition(library.games.isEmpty)
        try files.removeItem(at: source.appendingPathComponent("System40.ini"))
        library.importFolder(source)
        awaitImport()
        precondition(library.games.isEmpty && library.error == LibraryError.notGame.localizedDescription)
        print("PASS: empty library, INI/AIN launch targets, deterministic multiple AINs, missing/invalid targets, import, duplicate rejection, deletion, invalid import")
    }

    static func checkGamepadProfiles() {
        let suite = "AliceHub.gamepad-tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let profiles = GamepadProfiles(defaults: defaults)
        precondition(profiles.active("game") == GamepadProfile())

        var global = GamepadProfile()
        let bounds = CGRect(x: 10, y: 20, width: 400, height: 200)
        global.virtual.controls[0].move(to: CGPoint(x: 517, y: 87), in: bounds, snap: true)
        precondition(global.virtual.controls[0].x == 0.96 && global.virtual.controls[0].y == 0.325)
        global.virtual.controls[0].scale = 1.4
        global.virtual.controls[0].opacity = 0.42
        global.virtual.controls[0].visible = false
        global.virtual.enabled = true
        profiles.save(global, for: nil)

        var local = global
        local.virtual.usePreset(.empty)
        local.physical.enabled = true
        local.physical.bindings = PhysicalPad.rpgBindings
        profiles.save(local, for: "ゲーム/一")
        precondition(profiles.active("ゲーム/一") == local)
        precondition(profiles.active("ゲーム/二") == global)
        profiles.removeOverride(for: "ゲーム/一")
        precondition(profiles.active("ゲーム/一") == global)

        let json = try! JSONSerialization.jsonObject(with: Data(local.engineJSON().utf8)) as! [String: Any]
        let physical = json["physical"] as! [String: Any]
        let bindings = physical["bindings"] as! [String: [String: Any]]
        precondition(physical["enabled"] as? Bool == true && bindings.count == 17)
        precondition(bindings["0"]?["type"] as? Int == 1 && bindings["0"]?["code"] as? Int == 13)
        precondition(bindings["16"]?["type"] as? Int == 4)
        let virtual = json["virtual"] as! [String: Int]
        precondition(virtual == ["leftStickMode": 0, "rightStickMode": 0])
        let defaultJSON = try! JSONSerialization.jsonObject(with: Data(global.engineJSON().utf8)) as! [String: Any]
        let defaultPhysical = defaultJSON["physical"] as! [String: Any]
        let defaultBindings = defaultPhysical["bindings"] as! [String: [String: Any]]
        precondition(defaultBindings["0"]?["type"] as? Int == 2 && defaultBindings["0"]?["code"] as? Int == 1)
        precondition(defaultBindings["10"]?["code"] as? Int == -1)
        print("Gamepad profile checks passed")
    }
}
