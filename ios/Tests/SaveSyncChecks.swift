import Foundation

@main
struct SaveSyncChecks {
    static func check(_ value: Bool, _ line: UInt = #line) { precondition(value, "line \(line)") }
    static func main() throws {
        let files = FileManager.default
        let path = CommandLine.arguments[1]
        let root = URL(fileURLWithPath: path.hasPrefix("/private/var/") ? String(path.dropFirst("/private".count)) : path)
        let documents = root.appendingPathComponent("Documents")
        let support = root.appendingPathComponent("Support")
        let cloudRoot = root.path.hasPrefix("/var/") ? URL(fileURLWithPath: "/private" + root.path) : root
        let cloud = cloudRoot.appendingPathComponent("iCloud Drive/AliceHub")
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

        try put(local, "LocalSave1.asd", "remote-old", 3600)
        try put(remote, "LocalSave1.asd", "remote-old", 3600)
        try put(local, "LocalSave2.asd", "local-old", 3600)
        try put(remote, "LocalSave2.asd", "local-old", 3600)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary())
        try put(local, "LocalSave1.asd", "local-new", 10)
        try put(local, "sub/only-local.sav", "only-local", 50)
        try put(remote, "LocalSave2.asd", "remote-new", 10)
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
        try put(remote, "incoming.asd", "cloud-only", 50)
        try put(local, "outgoing.asd", "local-only", 50)
        check(try SaveSyncFolder.sync(game: game, mode: .uploadOnly, documents: documents, support: support)! == SaveSync.Summary(pushed: 1))
        check(get(local, "LocalSave1.asd") == "local-new" && get(remote, "LocalSave1.asd") == "other-device")
        check(get(local, "incoming.asd") == nil && get(remote, "outgoing.asd") == "local-only")
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 2, pushed: 0))
        check(get(local, "LocalSave1.asd") == "other-device")

        // Restoring an old save keeps its old timestamp but is still the newest local change.
        try put(local, "LocalSave2.asd", "restored-old", 7200)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 1))
        check(get(remote, "LocalSave2.asd") == "restored-old")

        // When both sides changed, retain both even when timestamps differ.
        try put(local, "same.asd", "local-edit", 30)
        try put(remote, "same.asd", "remote-edit", 20)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(conflicts: 1))
        check(get(local, "same.asd") == "local-edit" && get(remote, "same.asd") == "remote-edit")
        try put(local, "same.asd", "remote-edit", 20)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary())

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

        // A timestamp-only rewrite or another one-sided edit doesn't resolve the conflict.
        try put(local, "tie.asd", "tie-local", 0)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 0, conflicts: 1))
        check(get(remote, "tie.asd") == "tie-remote")
        try put(local, "tie.asd", "tie-local-edit", 0)
        check(try SaveSyncFolder.sync(game: game, mode: .uploadOnly, documents: documents, support: support)! == SaveSync.Summary(conflicts: 1))
        check(get(local, "tie.asd") == "tie-local-edit" && get(remote, "tie.asd") == "tie-remote")
        try put(remote, "tie.asd", "tie-local-edit", 0)
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary())

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

        // Neither launch nor upload may treat an unreadable placeholder as a known version.
        let placeholder = remote.appendingPathComponent(".only-remote.rsm.icloud")
        try files.moveItem(at: remote.appendingPathComponent("only-remote.rsm"), to: placeholder)
        do { try SaveSyncFolder.sync(game: game, documents: documents, support: support); preconditionFailure("ignored an unread placeholder") } catch {}
        try put(local, "only-remote.rsm", "edited-locally", 0)
        do { try SaveSyncFolder.sync(game: game, mode: .uploadOnly, documents: documents, support: support); preconditionFailure("pushed over an unread placeholder") } catch {}
        try files.moveItem(at: placeholder, to: remote.appendingPathComponent("only-remote.rsm"))
        check(try SaveSyncFolder.sync(game: game, documents: documents, support: support)! == SaveSync.Summary(pulled: 0, pushed: 1))

        try put(cloud, "其他游戏/LocalSave1.asd", "other", 5)
        try put(documents.appendingPathComponent("Saves"), "第三个/x.sav", "third", 5)
        let all = try SaveSyncFolder.syncAll(documents: documents, support: support)!
        check(all == SaveSync.Summary(pushed: 1))
        check(get(documents, "Saves/其他游戏/LocalSave1.asd") == nil)
        check(try SaveSyncFolder.sync(game: "其他游戏", documents: documents, support: support)! == SaveSync.Summary(pulled: 1))
        check(get(documents, "Saves/其他游戏/LocalSave1.asd") == "other")
        check(get(cloud, "第三个/x.sav") == "third")

        let renames = "rename-checks"
        let localRenames = documents.appendingPathComponent("Saves/" + renames)
        let remoteRenames = cloud.appendingPathComponent(renames)
        let manager = SaveManager(game: renames, documents: documents, support: support) { _, _ in }
        func rename(_ source: String, to name: String) throws {
            let entry = try manager.list().first { $0.path == source }!
            try SaveSyncFolder.rename(entry, named: name, using: manager, documents: documents, support: support)
        }
        try put(localRenames, "sub/old.asd", "last synced", 20)
        try SaveSyncFolder.sync(game: renames, documents: documents, support: support)
        let peer = SaveSync(game: renames, documents: root.appendingPathComponent("Peer"), folder: cloud,
                            state: support.appendingPathComponent("peer-renames.json"))
        let editedPeer = SaveSync(game: renames, documents: root.appendingPathComponent("EditedPeer"), folder: cloud,
                                  state: support.appendingPathComponent("edited-peer-renames.json"))
        check(try peer.sync() == SaveSync.Summary(pulled: 1))
        check(try editedPeer.sync() == SaveSync.Summary(pulled: 1))
        try put(editedPeer.local, "sub/old.asd", "independent progress", 0)
        try put(localRenames, "sub/old.asd", "new local progress", 10)
        try rename("sub/old.asd", to: "renamed.asd")
        check(get(localRenames, "sub/old.asd") == nil && get(remoteRenames, "sub/old.asd") == nil)
        check(get(localRenames, "sub/renamed.asd") == "new local progress")
        check(get(remoteRenames, "sub/renamed.asd") == "last synced")
        check(try SaveSyncFolder.sync(game: renames, documents: documents, support: support)! == SaveSync.Summary(pushed: 1))
        check(get(remoteRenames, "sub/renamed.asd") == "new local progress")
        check(try peer.sync(mode: .uploadOnly) == SaveSync.Summary())
        check(get(peer.local, "sub/old.asd") == "last synced")
        check(try peer.sync() == SaveSync.Summary(pulled: 1))
        check(get(peer.local, "sub/old.asd") == nil)
        check(get(peer.local, "sub/renamed.asd") == "new local progress")
        check(try editedPeer.sync() == SaveSync.Summary(pulled: 1, conflicts: 1))
        check(get(editedPeer.local, "sub/old.asd") == "independent progress")
        check(get(editedPeer.local, "sub/renamed.asd") == "new local progress")
        check(get(remoteRenames, "sub/old.asd") == nil)

        try put(remoteRenames, "sub/occupied.asd", "keep destination", 0)
        do { try rename("sub/renamed.asd", to: "occupied.asd"); preconditionFailure("renamed over occupied cloud path") }
        catch SaveSync.Failure.renameConflict {}
        check(get(localRenames, "sub/renamed.asd") == "new local progress")
        check(get(remoteRenames, "sub/occupied.asd") == "keep destination")
        try put(remoteRenames, "sub/renamed.asd", "new cloud progress", 0)
        do { try rename("sub/renamed.asd", to: "conflict.asd"); preconditionFailure("moved changed cloud path") }
        catch SaveSync.Failure.renameConflict {}
        check(get(localRenames, "sub/renamed.asd") == "new local progress")
        check(get(remoteRenames, "sub/renamed.asd") == "new cloud progress")
        check(get(localRenames, "sub/conflict.asd") == nil && get(remoteRenames, "sub/conflict.asd") == nil)

        try put(localRenames, "sub/local-only.asd", "local only", 0)
        try rename("sub/local-only.asd", to: "unpublished.asd")
        check(get(localRenames, "sub/local-only.asd") == nil && get(localRenames, "sub/unpublished.asd") == "local only")
        try files.removeItem(at: localRenames.appendingPathComponent("sub/renamed.asd"))
        check(try SaveSyncFolder.sync(game: renames, documents: documents, support: support)! == SaveSync.Summary(pulled: 2, pushed: 1))
        check(get(localRenames, "sub/renamed.asd") == "new cloud progress")
        print("PASS: synced rename carries version history; cloud conflicts are preserved; ordinary deletion still does not propagate")

        try files.createSymbolicLink(at: remote.appendingPathComponent("escape"), withDestinationURL: root)
        do { try SaveSyncFolder.sync(game: game, documents: documents, support: support); preconditionFailure("symlink accepted") } catch {}

        let escaped = root.appendingPathComponent("outside")
        try put(escaped, "slot.sav", "outside", 5)
        try files.createSymbolicLink(at: cloud.appendingPathComponent("remote-link"), withDestinationURL: escaped)
        try put(documents, "Saves/remote-link/slot.sav", "inside", 5)
        do {
            try SaveSyncFolder.sync(game: "remote-link", mode: .uploadOnly, documents: documents, support: support)
            preconditionFailure("remote game directory symlink accepted")
        } catch SaveSync.Failure.unsafePath {}
        try files.createSymbolicLink(at: documents.appendingPathComponent("Saves/local-link"), withDestinationURL: escaped)
        try put(cloud, "local-link/slot.sav", "cloud", 5)
        do {
            try SaveSyncFolder.sync(game: "local-link", documents: documents, support: support)
            preconditionFailure("local game directory symlink accepted")
        } catch SaveSync.Failure.unsafePath {}
        check(get(escaped, "slot.sav") == "outside")

        let alias = root.appendingPathComponent("Documents alias")
        try files.createSymbolicLink(at: alias, withDestinationURL: documents)
        try put(cloud, "new-game/sub/slot.sav", "new", 5)
        check(try SaveSyncFolder.sync(game: "new-game", documents: alias, support: support)! == SaveSync.Summary(pulled: 1))
        check(get(documents, "Saves/new-game/sub/slot.sav") == "new")

        let racing = SaveSync(game: "race", documents: documents, folder: cloud,
                              state: support.appendingPathComponent("race.json")) {
            try put(documents, "Saves/race/slot.sav", "just written", 0)
        }
        try put(documents, "Saves/race/slot.sav", "base", 20)
        try put(cloud, "race/slot.sav", "base", 20)
        check(try racing.sync() == SaveSync.Summary())
        try put(cloud, "race/slot.sav", "cloud edit", 10)
        do { try racing.sync(); preconditionFailure("target changed after scan was overwritten") }
        catch SaveSync.Failure.targetChanged {}
        check(get(documents, "Saves/race/slot.sav") == "just written")
        check(get(cloud, "race/slot.sav") == "cloud edit")

        let uploading = SaveSync(game: "upload-race", documents: documents, folder: cloud,
                                 state: support.appendingPathComponent("upload-race.json"))
        let uploadingLocal = documents.appendingPathComponent("Saves/upload-race")
        let uploadingRemote = cloud.appendingPathComponent("upload-race")
        try put(uploadingLocal, "a.sav", "base", 30)
        try put(uploadingRemote, "a.sav", "base", 30)
        check(try uploading.sync() == SaveSync.Summary())
        try put(uploadingLocal, "a.sav", "first", 20)
        try put(uploadingLocal, "b.sav", "another save", 20)
        let changing = uploadingLocal.appendingPathComponent("a.sav")
        let originalStamp = try files.attributesOfItem(atPath: changing.path)[.modificationDate] as! Date
        var checks = 0
        check(try uploading.sync(mode: .uploadOnly) {
            checks += 1
            if checks == 3 {
                // The first write accessor sees different bytes at the same size and mtime.
                try! Data("later".utf8).write(to: changing)
                try! files.setAttributes([.modificationDate: originalStamp], ofItemAtPath: changing.path)
            }
            return false
        } == SaveSync.Summary(pushed: 1, skipped: 1))
        check(get(uploadingRemote, "a.sav") == "base" && get(uploadingRemote, "b.sav") == "another save")
        check(try uploading.sync(mode: .uploadOnly) == SaveSync.Summary(pushed: 1))
        check(get(uploadingRemote, "a.sav") == "later")

        let cancelling = SaveSync(game: "cancel-upload", documents: documents, folder: cloud)
        let cancellingLocal = documents.appendingPathComponent("Saves/cancel-upload")
        let cancellingRemote = cloud.appendingPathComponent("cancel-upload")
        try put(cancellingLocal, "a.sav", "first", 20)
        try put(cancellingLocal, "b.sav", "second", 20)
        check(try cancelling.sync(mode: .uploadOnly, isCancelled: { true }) == SaveSync.Summary())
        checks = 0
        check(try cancelling.sync(mode: .uploadOnly) {
            checks += 1
            return checks == 4
        } == SaveSync.Summary())
        check(get(cancellingRemote, "a.sav") == nil && get(cancellingRemote, "b.sav") == nil)
        check(try cancelling.sync(mode: .uploadOnly) {
            get(cancellingRemote, "a.sav") != nil
        } == SaveSync.Summary(pushed: 1))
        check(get(cancellingRemote, "a.sav") == "first" && get(cancellingRemote, "b.sav") == nil)

        SaveSyncFolder.clear()
        check(!SaveSyncFolder.isConfigured)
        print("PASS: pull/push, upload only, unchanged rewrite, restores, conflicts, hidden skip, backups, idempotence, placeholder safety, sync-all, symlink roots, /private paths, target/source rechecks, upload cancellation")
    }
}
