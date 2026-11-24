import Foundation

@main
struct SaveSyncChecks {
    static func check(_ value: Bool, _ line: UInt = #line) { precondition(value, "line \(line)") }
    static func main() throws {
        let files = FileManager.default
        let root = URL(fileURLWithPath: CommandLine.arguments[1])
        let documents = root.appendingPathComponent("Documents")
        let support = root.appendingPathComponent("Support")
        let cloud = URL(fileURLWithPath: "/private" + root.path).appendingPathComponent("iCloud Drive/AliceHub")
        try files.createDirectory(at: cloud, withIntermediateDirectories: true)
        func put(_ base: URL, _ path: String, _ value: String, _ age: TimeInterval) throws {
            let url = base.appendingPathComponent(path)
            try files.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try Data(value.utf8).write(to: url)
            try files.setAttributes([.modificationDate: Date(timeIntervalSinceNow: -age)], ofItemAtPath: url.path)
        }
        func get(_ base: URL, _ path: String) -> String? {
            (try? Data(contentsOf: base.appendingPathComponent(path))).map { String(decoding: $0, as: UTF8.self) }
        }
        let game = "ランス10"
        let local = documents.appendingPathComponent("Saves/\(game)")
        let remote = cloud.appendingPathComponent(game)

        SaveSyncFolder.clear()
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support) == nil)
        try SaveSyncFolder.set(cloud)
        check(SaveSyncFolder.isConfigured && SaveSyncFolder.displayName == "AliceHub")

        try put(local, "LocalSave1.asd", "local-new", 10)
        try put(remote, "LocalSave1.asd", "remote-old", 3600)
        try put(local, "sub/only-local.sav", "only-local", 50)
        try put(remote, "LocalSave2.asd", "remote-new", 10)
        try put(local, "LocalSave2.asd", "local-old", 3600)
        try put(remote, "only-remote.rsm", "only-remote", 50)
        try put(local, "same.asd", "same", 100)
        try put(remote, "same.asd", "same", 300)
        try put(local, ".hidden", "hidden", 10)

        let first = try SaveSyncFolder.sync(game: game, documents: documents, support: support)!
        check(first == SaveSync.Summary(pulled: 2, pushed: 2))
        check(get(remote, "LocalSave1.asd") == "local-new")
        check(get(remote, "sub/only-local.sav") == "only-local")
        check(get(local, "LocalSave2.asd") == "remote-new")
        check(get(local, "only-remote.rsm") == "only-remote")
        check(get(local, "same.asd") == "same" && get(remote, "same.asd") == "same")
        check(get(remote, ".hidden") == nil)
        let backups = try files.contentsOfDirectory(atPath: support.appendingPathComponent("save_backups/\(game)").path)
        check(backups.count == 1)
        let backup = try SaveZip.read(support.appendingPathComponent("save_backups/\(game)/\(backups[0])"))
        check(backup.first { $0.name == "SaveData/LocalSave2.asd" }?.data == Data("local-old".utf8))

        let second = try SaveSyncFolder.sync(game: game, documents: documents, support: support)!
        check(second == SaveSync.Summary())

        // A background flush rewrites unchanged data with a fresh timestamp; newer remote progress still wins.
        try put(remote, "LocalSave1.asd", "other-device", 60)
        try put(local, "LocalSave1.asd", "local-new", 0)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 1, pushed: 0))
        check(get(local, "LocalSave1.asd") == "other-device")

        // Restoring an old save keeps its old timestamp but is still the newest local change.
        try put(local, "LocalSave2.asd", "restored-old", 7200)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 1))
        check(get(remote, "LocalSave2.asd") == "restored-old")

        // When both sides changed, the newer one wins.
        try put(local, "same.asd", "local-edit", 30)
        try put(remote, "same.asd", "remote-edit", 20)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 1, pushed: 0))
        check(get(local, "same.asd") == "remote-edit")

        // An edit that keeps the previous timestamp (e.g. a ZIP import) is still a change.
        let stamp = try files.attributesOfItem(atPath: local.appendingPathComponent("same.asd").path)[.modificationDate] as! Date
        try Data("imported".utf8).write(to: local.appendingPathComponent("same.asd"))
        try files.setAttributes([.modificationDate: stamp], ofItemAtPath: local.appendingPathComponent("same.asd").path)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 1))
        check(get(remote, "same.asd") == "imported")

        // Different copies with no sync record and tied timestamps are left alone.
        try put(local, "tie.asd", "tie-local", 40)
        try put(remote, "tie.asd", "tie-remote", 40)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 0, conflicts: 1))
        check(get(local, "tie.asd") == "tie-local" && get(remote, "tie.asd") == "tie-remote")

        // The conflict stays until a side's content changes, even if a timestamp-only rewrite moves it.
        try put(local, "tie.asd", "tie-local", 0)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 0, conflicts: 1))
        check(get(remote, "tie.asd") == "tie-remote")
        try put(local, "tie.asd", "tie-local-edit", 0)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 1))
        check(get(remote, "tie.asd") == "tie-local-edit")

        // Three versions with the same timestamp: last synced, cloud, and a local import.
        let tieStamp = Date(timeIntervalSinceNow: -500)
        func stamped(_ base: URL, _ value: String) throws {
            try Data(value.utf8).write(to: base.appendingPathComponent("three.asd"))
            try files.setAttributes([.modificationDate: tieStamp], ofItemAtPath: base.appendingPathComponent("three.asd").path)
        }
        try stamped(local, "A")
        try stamped(remote, "A")
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary())
        try stamped(remote, "B")
        try stamped(local, "C")
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 0, conflicts: 1))
        check(get(local, "three.asd") == "C" && get(remote, "three.asd") == "B")
        for base in [local, remote] {
            try files.removeItem(at: base.appendingPathComponent("tie.asd"))
            try files.removeItem(at: base.appendingPathComponent("three.asd"))
        }

        // An unchanged local file next to a not-yet-downloaded cloud file transfers nothing
        // without reading it; a local edit must read the cloud copy before replacing it.
        let placeholder = remote.appendingPathComponent(".only-remote.rsm.icloud")
        try files.moveItem(at: remote.appendingPathComponent("only-remote.rsm"), to: placeholder)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary())
        try put(local, "only-remote.rsm", "edited-locally", 0)
        do { try SaveSyncFolder.sync(game: game, documents: documents, support: support); preconditionFailure("pushed over an unread placeholder") } catch {}
        try files.moveItem(at: placeholder, to: remote.appendingPathComponent("only-remote.rsm"))
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 1))

        try put(cloud, "其他游戏/LocalSave1.asd", "other", 5)
        try put(documents.appendingPathComponent("Saves"), "第三个/x.sav", "third", 5)
        let all = try SaveSyncFolder.syncAll(documents: documents, support: support)!
        check(all == SaveSync.Summary(pulled: 1, pushed: 1))
        check(get(documents, "Saves/其他游戏/LocalSave1.asd") == "other")
        check(get(cloud, "第三个/x.sav") == "third")

        try files.createSymbolicLink(at: remote.appendingPathComponent("escape"), withDestinationURL: root)
        do { try SaveSyncFolder.sync(game: game, documents: documents, support: support); preconditionFailure("symlink accepted") } catch {}

        SaveSyncFolder.clear()
        check(!SaveSyncFolder.isConfigured)
        print("PASS: pull/push, unchanged rewrite, restore and same-timestamp edits, conflicts, ties and persisted conflicts, hidden skip, backup before overwrite, idempotence, iCloud placeholder names, sync-all, symlink rejection, /private folder")
    }
}
