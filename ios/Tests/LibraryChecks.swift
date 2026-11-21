import Foundation

func SDL_SetMainReady() {}
func xsystem4_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}
func xsystem35_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}

@main
struct LibraryChecks {
    static func main() throws {
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
        print("PASS: empty library, System 3 markers/arguments/import, System39 saves, System 4 precedence, INI/AIN targets, deterministic AINs, missing/invalid targets, import, duplicate rejection, deletion, invalid import")
    }
}
