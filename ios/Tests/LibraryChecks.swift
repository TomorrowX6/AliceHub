import Foundation

func SDL_SetMainReady() {}
func xsystem4_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}

@main
struct LibraryChecks {
    static func main() throws {
        try ArchiveChecks.run()
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
        let invalid = root.appendingPathComponent("not-game")
        try files.createDirectory(at: invalid, withIntermediateDirectories: true)
        try Data("readme".utf8).write(to: invalid.appendingPathComponent("readme.txt"))
        let invalidZip = try makeArchive(invalid, name: "invalid.zip")
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
}
