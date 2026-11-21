import Foundation

@main
struct SaveManagerChecks {
    static func check(_ value: Bool) { precondition(value) }
    static func main() throws {
        let files = FileManager.default
        let root = URL(fileURLWithPath: CommandLine.arguments[1])
        let documents = root.appendingPathComponent("Documents")
        let support = root.appendingPathComponent("Support")
        var calls: [String] = []
        let manager = SaveManager(game: "测试游戏", documents: documents, support: support) { source, target in
            calls.append(source.lastPathComponent)
            let data = try Data(contentsOf: source)
            try (data.prefix(8) == SaveManager.magic ? Data("GD-converted".utf8) : data).write(to: target)
        }
        func write(_ name: String, _ data: Data, under directory: URL? = nil) throws {
            let target = (directory ?? manager.directory).appendingPathComponent(name)
            try files.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: target)
        }
        func text(_ name: String, _ value: String, under directory: URL? = nil) throws {
            try write(name, Data(value.utf8), under: directory)
        }
        func read(_ name: String) throws -> String { String(decoding: try Data(contentsOf: manager.directory.appendingPathComponent(name)), as: UTF8.self) }
        func rejects(_ work: () throws -> Void) {
            do { try work(); preconditionFailure("Expected rejection") } catch {}
        }
        let zip = root.appendingPathComponent("roundtrip.zip")
        let original = [SaveZip.Entry(name: "SaveData/日本語/测试.asd", data: Data("hello".utf8)), SaveZip.Entry(name: "SaveData/empty", data: Data())]
        try SaveZip.write(original, to: zip)
        let roundtrip = try SaveZip.read(zip)
        check(roundtrip.map(\.name) == original.map(\.name) && roundtrip.map(\.data) == original.map(\.data))
        let emptyZip = root.appendingPathComponent("empty.zip")
        try SaveZip.write([], to: emptyZip)
        check(try SaveZip.read(emptyZip).isEmpty)
        var corrupt = try Data(contentsOf: zip)
        let central = corrupt.range(of: Data([0x50, 0x4b, 0x01, 0x02]))!.lowerBound
        corrupt[central + 16] ^= 1
        let bad = root.appendingPathComponent("bad.zip")
        try corrupt.write(to: bad)
        rejects { _ = try SaveZip.read(bad) }
        let sizeBad = root.appendingPathComponent("bad-size.zip")
        var badSize = try Data(contentsOf: zip)
        badSize[central + 24] ^= 1
        try badSize.write(to: sizeBad)
        rejects { _ = try SaveZip.read(sizeBad) }
        check(try manager.backup() == nil)
        try text("slot.asd", "old")
        try text(".secret", "secret")
        try text(".settings/config", "settings")
        try text("nested/.hidden/data", "hidden")
        check(try manager.list().map(\.path) == ["slot.asd"])
        for _ in 0..<7 { try manager.backup() }
        check(try manager.backups().count == 5)
        let backup = try manager.backups()[0]
        try files.copyItem(at: backup, to: root.appendingPathComponent("interop.zip"))
        try text("slot.asd", "changed")
        try text("extra.asd", "remove")
        try manager.restore(backup)
        check(try read("slot.asd") == "old")
        check(!files.fileExists(atPath: manager.directory.appendingPathComponent("extra.asd").path))
        check(try read(".secret") == "secret" && read(".settings/config") == "settings")
        check(try read("nested/.hidden/data") == "hidden")
        let attack = root.appendingPathComponent("attack.zip")
        try SaveZip.write([.init(name: "SaveData/../escape", data: Data())], to: attack)
        rejects { try manager.restore(attack) }
        rejects { try manager.restore(bad) }
        check(try read("slot.asd") == "old" && read(".secret") == "secret")
        var failedSwap = false
        let rollback = SaveManager(game: "测试游戏", documents: documents, support: support, move: { source, target in
            if source.lastPathComponent.hasPrefix(".restore_staging_") && !failedSwap {
                failedSwap = true
                throw SaveManager.Failure.swap
            }
            try files.moveItem(at: source, to: target)
        }, converter: { try files.copyItem(at: $0, to: $1) })
        rejects { try rollback.restore(zip) }
        check(try failedSwap && read("slot.asd") == "old")
        let folder = root.appendingPathComponent("Import")
        try text("SaveData/slot.asd", "merged", under: folder)
        try text("SaveData/sub/new.sav", "new", under: folder)
        try manager.importURLs([folder], folder: true)
        check(try read("slot.asd") == "merged" && read("sub/new.sav") == "new")
        try manager.importURLs([root.appendingPathComponent("android.zip")])
        check(try read("android.asd") == "android" && read("slot.asd") == "merged")
        try manager.restore(root.appendingPathComponent("android.zip"))
        check(try manager.list().map(\.path) == ["android.asd"])
        try manager.importURLs([root.appendingPathComponent("wrapped.zip")])
        check(try read("wrapped.sav") == "wrapped")
        try manager.importURLs([root.appendingPathComponent("discovery.zip")])
        check(try read("depth.asd") == "depth")
        check(!files.fileExists(atPath: manager.directory.appendingPathComponent("readme.txt").path))
        rejects { try manager.importURLs([attack]) }
        rejects { try manager.importURLs([bad]) }
        check(try read("android.asd") == "android")
        let stored = try SaveZip.read(root.appendingPathComponent("stored.zip"))
        check(stored[0].data == Data("stored".utf8))
        try text("keep.bak", "bak")
        try text("debug.LOG", "log")
        try write("struct.asd", SaveManager.magic + Data([2, 3]))
        let pcZip = root.appendingPathComponent("pc.zip")
        let counts = try manager.export(to: pcZip, pc: true, zip: true)
        check(counts == (4, 1) && calls.sorted() == ["android.asd", "depth.asd", "struct.asd", "wrapped.sav"])
        let pcEntries = try SaveZip.read(pcZip)
        check(pcEntries.first { $0.name == "SaveData/android.asd" }!.data == Data("android".utf8))
        check(pcEntries.first { $0.name == "SaveData/struct.asd" }!.data == Data("GD-converted".utf8))
        let rawFolder = root.appendingPathComponent("RawExport")
        _ = try manager.export(to: rawFolder, pc: false, zip: false)
        check(try Data(contentsOf: rawFolder.appendingPathComponent("SaveData/struct.asd")).prefix(8) == SaveManager.magic)
        let entry = try manager.list().first { $0.path == "android.asd" }!
        let single = root.appendingPathComponent("single.asd")
        check(try manager.export(to: single, pc: false, zip: false, single: entry) == (1, 0))
        check(try Data(contentsOf: single) == Data("android".utf8))
        let pcFolder = root.appendingPathComponent("PCExport")
        check(try manager.export(to: pcFolder, pc: true, zip: false) == (4, 1))
        check(try Data(contentsOf: pcFolder.appendingPathComponent("SaveData/struct.asd")) == Data("GD-converted".utf8))
        try manager.duplicate(entry)
        check(try read("android.asd.bak") == "android")
        try text("android.asd.bak", "overwrite")
        try manager.duplicate(entry)
        check(try read("android.asd.bak") == "android")
        try manager.delete(entry)
        check(!files.fileExists(atPath: entry.url.path))
        try manager.clear()
        check(try manager.list().isEmpty && read(".secret") == "secret")
        check(try manager.backup() == nil)
        let hiddenZip = root.appendingPathComponent("hidden.zip")
        try SaveZip.write([.init(name: "SaveData/.secret", data: Data("replace".utf8))], to: hiddenZip)
        try manager.restore(hiddenZip)
        check(try manager.list().isEmpty && read(".secret") == "secret")
        let broken = SaveManager(game: "失败", documents: documents, support: support) { _, _ in throw SaveManager.Failure.conversion }
        try text("x.asd", "x", under: broken.directory)
        rejects { _ = try broken.export(to: root.appendingPathComponent("failed.zip"), pc: true, zip: true) }
        print("PASS: ZIP round-trip/CRC/stored, empty backup, retention, restore replacement/hidden/traversal/corruption/rollback, import merge/discovery, PC conversion/filtering, raw export, duplicate/delete/clear")
    }
}
