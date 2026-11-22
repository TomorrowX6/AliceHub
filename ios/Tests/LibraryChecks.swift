import Foundation

func SDL_SetMainReady() {}
enum RuntimeGamepad { static func prepare(for game: String) {} }
enum ModifierBridge { static func prepareForLaunch() {} }
func xsystem4_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}
func xsystem35_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}
func system3_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}

@main
struct LibraryChecks {
    static func main() throws {
        try ArchiveChecks.run()
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
        let saves = documents.appendingPathComponent("Saves/test")
        for marker in ["testSA.ALD", "aDiSk.AlD", "sYsTeM39.iNi", "System39.ain"] {
            let file = source.appendingPathComponent(marker)
            try Data().write(to: file)
            precondition(GameLibrary.isGame(source))
            precondition(Game(url: source).engine == .system3)
            let info = try GameLibrary.launchInfo(source)
            precondition(info.engine == .system3 && samePath(info.target, source))
            let sys39 = marker.lowercased().hasPrefix("system39")
            let arguments = try GameLibrary.launchArguments(source, saves: saves)
            precondition(arguments == ["xsystem35", "-gamedir", source.path, "-savedir", saves.path] + (sys39 ? ["-saveformat", "system39"] : []))
            try Data().write(to: firstAIN)
            let mixed = try GameLibrary.launchInfo(source)
            precondition(mixed.engine == .system4 && samePath(mixed.target, firstAIN))
            try files.removeItem(at: firstAIN)
            try Data().write(to: source.appendingPathComponent("AliceStart.ini"))
            precondition(Game(url: source).engine == .system4)
            try files.removeItem(at: source.appendingPathComponent("AliceStart.ini"))
            try files.removeItem(at: file)
        }
        let legacyDisk = source.appendingPathComponent("ADISK.dat")
        let legacyCg = source.appendingPathComponent("aCg.DaT")
        try Data().write(to: legacyDisk)
        precondition(!GameLibrary.isGame(source))
        try Data().write(to: legacyCg)
        precondition(Game(url: source).engine == .legacySystem3)
        let legacyArguments = try GameLibrary.launchArguments(source, saves: saves)
        precondition(legacyArguments == ["system3", "-gamedir", source.path, "-savedir", saves.path])
        let newerDisk = source.appendingPathComponent("testSA.ALD")
        try Data().write(to: newerDisk)
        precondition(Game(url: source).engine == .system3)
        try files.removeItem(at: newerDisk)
        try Data().write(to: firstAIN)
        precondition(Game(url: source).engine == .system4)
        try files.removeItem(at: firstAIN)
        try files.removeItem(at: legacyDisk)
        try files.removeItem(at: legacyCg)
        for marker in ["random.ald", "ADISK", "something.ini"] {
            let file = source.appendingPathComponent(marker)
            try Data().write(to: file)
            precondition(!GameLibrary.isGame(source))
            try files.removeItem(at: file)
        }
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
        try Data().write(to: source.appendingPathComponent("TESTsa.ald"))
        library.importFolder(source)
        awaitImport()
        precondition(library.games.count == 1 && library.games[0].engine == .system3 && library.error == nil)
        library.delete(library.games[0])
        try files.removeItem(at: source.appendingPathComponent("TESTsa.ald"))
        library.importFolder(source)
        awaitImport()
        precondition(library.games.isEmpty && library.error == LibraryError.notGame.localizedDescription)
        print("PASS: empty library, INI/AIN launch targets, deterministic multiple AINs, missing/invalid targets, import, duplicate rejection, deletion, invalid import")
        func run(_ executable: String, _ arguments: [String]) throws {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: executable)
            process.arguments = arguments
            let output = Pipe()
            process.standardOutput = output
            process.standardError = output
            try process.run()
            let data = output.fileHandleForReading.readDataToEndOfFile()
            process.waitUntilExit()
            precondition(process.terminationStatus == 0, String(decoding: data, as: UTF8.self))
        }
        func makeArchive(_ directory: URL, name: String, format: String = "zip") throws -> URL {
            let archive = root.appendingPathComponent(name)
            var arguments = ["--format", format, "-cf", archive.path, "-C", directory.path, "."]
            if name.hasSuffix(".gz") { arguments.insert("-z", at: 0) }
            try run("/opt/homebrew/opt/libarchive/bin/bsdtar", arguments)
            return archive
        }
        let games = documents.appendingPathComponent("Games")
        func importArchive(_ archive: URL, expected: String?, error: String? = nil) throws {
            library.importArchive(archive)
            precondition(library.importing)
            awaitImport()
            precondition(library.error == error, library.error ?? "Expected an error")
            precondition(library.importProgress.isEmpty)
            if let expected {
                precondition(library.games.contains { $0.name == expected })
                let body = try Data(contentsOf: games.appendingPathComponent(expected + "/dummy.dat"))
                precondition(body == Data("payload".utf8))
            }
            let leftovers = try files.contentsOfDirectory(atPath: games.path).filter { $0.hasPrefix(".import-") }
            precondition(leftovers.isEmpty, "Temporary import stages must always be removed")
            precondition(files.fileExists(atPath: archive.path), "Picked source must not be consumed")
        }
        let top = root.appendingPathComponent("top")
        try files.createDirectory(at: top, withIntermediateDirectories: true)
        try Data().write(to: top.appendingPathComponent("System40.ini"))
        try Data("payload".utf8).write(to: top.appendingPathComponent("dummy.dat"))
        let topZip = try makeArchive(top, name: "Archive Top.zip")
        try importArchive(topZip, expected: "Archive Top")
        let beforeDuplicate = library.games.count
        try importArchive(topZip, expected: "Archive Top", error: LibraryError.alreadyExists.localizedDescription)
        precondition(library.games.count == beforeDuplicate)
        let sub = root.appendingPathComponent("sub")
        try files.createDirectory(at: sub, withIntermediateDirectories: true)
        try files.copyItem(at: top, to: sub.appendingPathComponent("游戏子目录"))
        let subZip = try makeArchive(sub, name: "ignored-name.zip")
        try importArchive(subZip, expected: "游戏子目录")
        // Prefer the shallowest root, then deterministic path order at that depth.
        try files.createDirectory(at: sub.appendingPathComponent("a"), withIntermediateDirectories: true)
        try files.copyItem(at: top, to: sub.appendingPathComponent("a/deeper"))
        try files.copyItem(at: top, to: sub.appendingPathComponent("A-first"))
        let ordered = try makeArchive(sub, name: "ordered.zip")
        try importArchive(ordered, expected: "A-first")
        let isoContents = root.appendingPathComponent("disc-files")
        try files.createDirectory(at: isoContents, withIntermediateDirectories: true)
        let image = isoContents.appendingPathComponent("game.iso")
        try run("/usr/bin/hdiutil", ["makehybrid", "-iso", "-joliet", "-o", image.path, top.path])
        let nested = try makeArchive(isoContents, name: "Nested Disc.tar.gz", format: "gnutar")
        try importArchive(nested, expected: "Nested Disc")
        let discAndPatch = root.appendingPathComponent("disc-and-patch")
        let patchFiles = root.appendingPathComponent("patch-files")
        try files.createDirectory(at: discAndPatch, withIntermediateDirectories: true)
        try files.createDirectory(at: patchFiles, withIntermediateDirectories: true)
        try files.copyItem(at: image, to: discAndPatch.appendingPathComponent("game.iso"))
        try Data().write(to: patchFiles.appendingPathComponent("patch.ain"))
        try Data("patch without base game".utf8).write(to: patchFiles.appendingPathComponent("dummy.dat"))
        let imageSize = try image.resourceValues(forKeys: [.fileSizeKey]).fileSize!
        try Data((0..<(imageSize + 65536)).map { _ in UInt8.random(in: 0...255) }).write(to: patchFiles.appendingPathComponent("padding.dat"))
        let patchZip = try makeArchive(patchFiles, name: "larger-patch.zip")
        let patchSize = try patchZip.resourceValues(forKeys: [.fileSizeKey]).fileSize!
        precondition(patchSize > imageSize)
        try files.copyItem(at: patchZip, to: discAndPatch.appendingPathComponent("patch.zip"))
        let discWithPatch = try makeArchive(discAndPatch, name: "Disc with Patch.zip")
        try importArchive(discWithPatch, expected: "Disc with Patch")
        let invalid = root.appendingPathComponent("not-game")
        try files.createDirectory(at: invalid, withIntermediateDirectories: true)
        try Data("readme".utf8).write(to: invalid.appendingPathComponent("readme.txt"))
        let invalidZip = try makeArchive(invalid, name: "invalid.zip")
        let candidates = root.appendingPathComponent("candidates")
        try files.createDirectory(at: candidates, withIntermediateDirectories: true)
        try files.copyItem(at: topZip, to: candidates.appendingPathComponent("game.zip"))
        try Data((0..<65536).map { _ in UInt8.random(in: 0...255) }).write(to: invalid.appendingPathComponent("padding.dat"))
        let decoy = try makeArchive(invalid, name: "decoy.zip")
        try files.copyItem(at: decoy, to: candidates.appendingPathComponent("decoy.zip"))
        try Data(repeating: 0xFF, count: 131072).write(to: candidates.appendingPathComponent("broken.zip"))
        let siblings = try makeArchive(candidates, name: "Multiple Candidates.zip")
        try importArchive(siblings, expected: "Multiple Candidates")
        let beforeInvalid = library.games.count
        try importArchive(invalidZip, expected: nil, error: LibraryError.archiveNotGame.localizedDescription)
        precondition(library.games.count == beforeInvalid)
        let bad = root.appendingPathComponent("bad.zip")
        try Data("broken archive".utf8).write(to: bad)
        library.importArchive(bad)
        awaitImport()
        precondition(library.error?.hasPrefix("解压失败") == true)
        let remaining = try files.contentsOfDirectory(atPath: games.path)
        precondition(!remaining.contains { $0.hasPrefix(".import-") })
        print("PASS: archive-root naming, single game folder, shallowest/deterministic roots, tar.gz → ISO → game, duplicate rejection, no-game/corrupt errors, source preservation and stage cleanup")
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
