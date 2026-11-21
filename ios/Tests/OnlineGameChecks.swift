import Foundation
import CryptoKit

func SDL_SetMainReady() {}
enum RuntimeGamepad { static func prepare(for game: String) {} }
enum ModifierBridge { static func prepareForLaunch() {} }
func xsystem4_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 { fatalError() }
func xsystem35_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 { fatalError() }

@main
struct OnlineGameChecks {
    static func check(_ condition: @autoclosure () throws -> Bool) rethrows {
        let passed = try condition()
        precondition(passed)
    }

    static func main() async throws {
        let files = FileManager.default
        let root = URL(fileURLWithPath: CommandLine.arguments[1])
        let base = URL(string: CommandLine.arguments[2])!
        let manifestData = try Data(contentsOf: root.appendingPathComponent("manifest.json"))
        let manifest = try OnlineGameManifest.parse(manifestData)
        let fetched = try await OnlineGameManifest.fetch(from: base)
        precondition(fetched.totalBytes == manifest.totalBytes && fetched.files.count == 5)
        let original = try JSONSerialization.jsonObject(with: manifestData) as! [String: Any]
        let entry = (original["files"] as! [[String: Any]])[0]
        func rejects(_ changed: [String: Any]) throws {
            do {
                _ = try OnlineGameManifest.parse(JSONSerialization.data(withJSONObject: changed))
                preconditionFailure("Unsafe manifest accepted: \(changed)")
            } catch is DecodingError {} catch is ModError {}
        }
        for path in ["../escape", "/escape", "a\\b", "C:/escape", "a//b", "a/./b", "a/../b", "a\u{0}b"] {
            var changed = original, badEntry = entry; badEntry["path"] = path; changed["files"] = [badEntry]
            try rejects(changed)
        }
        for value in ["../escape", ".hidden", "a/b", "C:foo"] {
            var changed = original; changed["baseName"] = value; try rejects(changed)
        }
        for (key, value) in [("key", "../escape" as Any), ("size", -1), ("sha256", "bad")] {
            var changed = original, badEntry = entry; badEntry[key] = value; changed["files"] = [badEntry]
            try rejects(changed)
        }
        for names in [["Data.AIN", "data.ain"], ["data", "data/file"], ["é.ain", "e\u{301}.ain"]] {
            var changed = original
            changed["files"] = names.map { path -> [String: Any] in var result = entry; result["path"] = path; return result }
            try rejects(changed)
        }
        var overflow = original, huge = entry
        huge["size"] = Int64.max; overflow["files"] = [huge, entry]; try rejects(overflow)
        var empty = original; empty["files"] = []; try rejects(empty)
        precondition(OnlineGame.all.count == 19 && Set(OnlineGame.all.map(\.id)).count == 19)
        print("PASS: catalog, HTTP manifest, invalid paths/keys/hashes/sizes, Unicode/case duplicates, prefix collisions and overflow")

        let game = OnlineGame(id: "fixture", title: "测试游戏", directory: manifest.baseName)
        let documents = root.appendingPathComponent("Documents")
        let library = GameLibrary(documents: documents)
        let games = library.gamesDirectory
        let work = games.appendingPathComponent(".online-game-fixture")
        let cache = work.appendingPathComponent("cache")
        try files.createDirectory(at: cache, withIntermediateDirectories: true)
        let payload = try Data(contentsOf: root.appendingPathComponent("game.ain"))
        let part = cache.appendingPathComponent("mod_" + String(manifest.files[0].sha256.prefix(16)) + ".rar.part")
        try payload.prefix(64).write(to: part)
        let configPart = cache.appendingPathComponent("mod_" + String(manifest.files[1].sha256.prefix(16)) + ".rar.part")
        try Data(repeating: 0, count: Int(manifest.files[1].size)).write(to: configPart)
        let stage = work.appendingPathComponent("staging")
        try files.createDirectory(at: stage, withIntermediateDirectories: true)
        try Data("old manifest".utf8).write(to: stage.appendingPathComponent("stale.ain"))
        let installer = OnlineGameInstaller()
        var last: Int64 = 0
        var sharedStorage = false, protectedCache = false
        let destination = try installer.install(game, manifest: manifest, in: games, baseURL: base) { _, done, total in
            precondition(done >= last && done <= total); last = done
            let staged = stage.appendingPathComponent("game.ain")
            if files.fileExists(atPath: staged.path) {
                let cachedAttributes = try! files.attributesOfItem(atPath: part.path)
                let stagedAttributes = try! files.attributesOfItem(atPath: staged.path)
                precondition((cachedAttributes[.systemFileNumber] as? NSNumber) == (stagedAttributes[.systemFileNumber] as? NSNumber))
                sharedStorage = true
            }
            let overlap = stage.appendingPathComponent("overlap.txt")
            if (try? String(contentsOf: overlap, encoding: .utf8)) == "overlaid" {
                let cached = cache.appendingPathComponent("mod_" + String(manifest.files[2].sha256.prefix(16)) + ".rar.part")
                precondition((try? String(contentsOf: cached, encoding: .utf8)) == "original")
                protectedCache = true
            }
        }
        precondition(last == manifest.totalBytes && sharedStorage && protectedCache)
        try check(try Data(contentsOf: destination.appendingPathComponent("game.ain")) == payload)
        try check(try String(contentsOf: destination.appendingPathComponent(".xsys35rc"), encoding: .utf8) == "hidden config")
        let extracted = try files.subpathsOfDirectory(atPath: destination.path)
        precondition(extracted.contains("Manual/说明.txt"), "Extracted files: \(extracted)")
        try check(try String(contentsOf: destination.appendingPathComponent("Manual/说明.txt"), encoding: .utf8) == "verified archive")
        precondition(files.fileExists(atPath: destination.appendingPathComponent("empty.txt").path))
        precondition(!files.fileExists(atPath: destination.appendingPathComponent("stale.ain").path))
        precondition(!files.fileExists(atPath: work.path))
        library.refresh(); precondition(library.games.count == 1)
        try Data("save".utf8).write(to: destination.appendingPathComponent("save.rsm"))
        do {
            _ = try OnlineGameInstaller().install(game, manifest: manifest, in: games, baseURL: base) { _, _, _ in }
            preconditionFailure("Overwrote an existing installation")
        } catch LibraryError.alreadyExists {}
        try check(try String(contentsOf: destination.appendingPathComponent("save.rsm"), encoding: .utf8) == "save")
        print("PASS: Range resume, SHA-256/size checks, archive extraction, hidden and empty files, stale-stage cleanup and no-overwrite safety")
        print("PASS: staging shares the verified cache inode without duplicate allocation; archive overlays preserve cached bytes")

        let cancelledGames = root.appendingPathComponent("cancelled")
        let stopping = OnlineGameInstaller()
        do {
            _ = try stopping.install(game, manifest: manifest, in: cancelledGames, baseURL: base) { text, done, _ in
                if text.hasPrefix("正在下载") && done > 0 { stopping.cancel() }
            }
            preconditionFailure("Cancellation was ignored")
        } catch is CancellationError {}
        precondition(!files.fileExists(atPath: cancelledGames.appendingPathComponent(manifest.baseName).path))
        let resumed = try OnlineGameInstaller().install(game, manifest: manifest, in: cancelledGames, baseURL: base) { _, _, _ in }
        try check(try Data(contentsOf: resumed.appendingPathComponent("game.ain")) == payload)
        print("PASS: cancellation keeps partial downloads hidden; retry resumes and commits atomically")

        var noGameJSON = original
        noGameJSON["baseName"] = "NotAGame"
        noGameJSON["files"] = [(original["files"] as! [[String: Any]])[1]]
        let noGame = try OnlineGameManifest.parse(JSONSerialization.data(withJSONObject: noGameJSON))
        do {
            _ = try OnlineGameInstaller().install(game, manifest: noGame, in: games, baseURL: base) { _, _, _ in }
            preconditionFailure("Non-game directory was installed")
        } catch LibraryError.notGame {}
        precondition(!files.fileExists(atPath: games.appendingPathComponent("NotAGame").path))
        precondition(!files.fileExists(atPath: stage.path))
        print("PASS: non-game payload stays hidden and failed staging is cleaned")

        let outside = root.appendingPathComponent("outside")
        try files.createDirectory(at: outside, withIntermediateDirectories: true)
        try files.createSymbolicLink(at: root.appendingPathComponent("linked-games"), withDestinationURL: outside)
        do {
            _ = try OnlineGameInstaller().install(game, manifest: manifest, in: root.appendingPathComponent("linked-games"), baseURL: base) { _, _, _ in }
            preconditionFailure("Symlink storage accepted")
        } catch ArchiveError.invalidPath {}
        try check(try files.contentsOfDirectory(atPath: outside.path).isEmpty)
        print("PASS: symlink staging rejected before writes")
    }
}
