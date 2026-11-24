import Foundation

struct modifier_lock_game: Equatable { var rawValue: UInt32 }
let MODIFIER_LOCK_RANCE10 = modifier_lock_game(rawValue: 0)
let MODIFIER_LOCK_DOHNADOHNA = modifier_lock_game(rawValue: 1)
let MODIFIER_LOCK_EVENICLE = modifier_lock_game(rawValue: 2)
let MODIFIER_LOCK_SENGOKURANCE = modifier_lock_game(rawValue: 3)
let MODIFIER_LOCK_RANCE01 = modifier_lock_game(rawValue: 4)
let MODIFIER_LOCK_RANCE02 = modifier_lock_game(rawValue: 5)
let MODIFIER_LOCK_RANCE03 = modifier_lock_game(rawValue: 6)
let MODIFIER_LOCK_RANCE08 = modifier_lock_game(rawValue: 7)
let MODIFIER_LOCK_RANCE09 = modifier_lock_game(rawValue: 8)

var masks: [UInt32: Int32] = [:]
var actions: [String] = []
var writes: [String] = []
var liveReplies = ["{\"inGame\":true,\"money\":5}"]
var system35Reply: String?
func xsystem35_modifier_live_state() -> UnsafeMutablePointer<CChar>? { system35Reply.flatMap { strdup($0) } }
func modifier_api_set_config_path(_ path: UnsafePointer<CChar>!) {}
func modifier_api_current_game() -> UnsafePointer<CChar>! { ("dohnadohna" as NSString).utf8String }
func modifier_api_get_live_state_json() -> UnsafeMutablePointer<CChar>! {
    strdup(liveReplies.count > 1 ? liveReplies.removeFirst() : liveReplies[0])
}
func modifier_api_get_lock_mask(_ game: modifier_lock_game) -> Int32 { masks[game.rawValue] ?? 0 }
func modifier_api_set_lock_mask(_ game: modifier_lock_game, _ mask: Int32) { masks[game.rawValue] = mask }
func modifier_api_apply_live_action(_ json: UnsafePointer<CChar>!) { actions.append(String(cString: json)) }
func modifier_api_read_local_save_summary(_ path: UnsafePointer<CChar>!) -> UnsafeMutablePointer<CChar>! {
    strdup("{\"valid\":true,\"ok\":true,\"turn\":3,\"foodTicket\":10,\"cardCount\":4,\"comment\":\"x\"}")
}
func modifier_api_write_local_save_mod(_ path: UnsafePointer<CChar>!, _ json: UnsafePointer<CChar>!) -> Bool {
    writes.append(String(cString: json)); return true
}

@main
struct ModifierChecks {
    @MainActor
    static func main() async throws {
        precondition(ModifierBridge.cheatAction(for: "  “whosyourdaddy” ")?["cheatCode"] as? String == "whosyourdaddy")
        precondition(ModifierBridge.cheatAction(for: "   ") == nil)
        precondition(ModifierBridge.applyCheatCode("灌注Eins陈谢谢喵"))
        precondition(masks[0] == ModifierBridge.rance10StreamerModeBit && actions.last!.contains("\"streamerMode\":0"))
        precondition(ModifierBridge.applyCheatCode("开启主播模式"))
        precondition(masks[0] == 0 && actions.last!.contains("\"streamerMode\":1"))
        masks[0] = ModifierBridge.rance10TreasureBit
        ModifierBridge.setStreamerMode(false)
        precondition(masks[0] == ModifierBridge.rance10TreasureBit | ModifierBridge.rance10StreamerModeBit)
        precondition(!ModifierBridge.streamerMode(in: [:], mask: masks[0]!))
        precondition(ModifierBridge.streamerMode(in: ["streamerMode": 1], mask: masks[0]!))
        precondition(!ModifierBridge.streamerMode(in: ["streamerMode": 0], mask: 0))
        let unsupported = ModifierBridge.parse(#"{"inGame":false,"streamerModeSupported":false,"streamerMode":0}"#)
        precondition(ModifierBridge.streamerToggleState(in: unsupported, mask: 0) == nil)
        precondition(ModifierBridge.streamerToggleState(in: [:], mask: 0) == nil)
        let supported = ModifierBridge.parse(#"{"inGame":false,"streamerModeSupported":true}"#)
        precondition(ModifierBridge.streamerToggleState(in: supported, mask: 0) == true)
        precondition(ModifierBridge.streamerToggleState(in: supported, mask: masks[0]!) == false)
        let supportedLive = ModifierBridge.parse(#"{"inGame":false,"streamerModeSupported":true,"streamerMode":1}"#)
        precondition(ModifierBridge.streamerToggleState(in: supportedLive, mask: masks[0]!) == true)
        ModifierBridge.setStreamerMode(true)
        precondition(masks[0] == ModifierBridge.rance10TreasureBit)

        precondition(ModifierGamePanel.all.map(\.game.id) == ["sengokurance", "rance01", "rance02", "rance03", "rance08", "rance09"])
        let sr = ModifierGamePanel.all[0]
        precondition(sr.resources.first?.initialValue(in: ["gold": 123]) == "123")
        precondition(sr.resources.first?.initialValue(in: ["gold": true]) == "")
        precondition(ModifierField.changes(from: ["srGold": "123", "srAp": " -5 ", "srApMax": "", "overflow": "2147483648"],
                                           excluding: ["srGold": "123"]) == ["srAp": -5])
        let srCharacters = sr.characters!
        precondition(srCharacters.rows(in: [:]).isEmpty)
        let generals = srCharacters.rows(in: ["srCharacters": [
            ["charIdx": 9, "factionIdx": 2, "name": "兰斯", "troops": 400],
            ["factionIdx": 2, "name": "Invalid"],
        ]])
        precondition(generals.count == 2)
        precondition(generals[0].target == ["charIdx": -1, "factionIdx": 2])
        precondition(generals[1].name == "兰斯")
        precondition(srCharacters.action(for: generals[1], values: ["troops": 600])["srCharEdit"] as? [String: Int]
                     == ["charIdx": 9, "factionIdx": 2, "troops": 600])
        for panel in ModifierGamePanel.all.dropFirst().compactMap(\.characters) {
            let rows = panel.rows(in: ["\(panel.prefix)Characters": [["index": 4, "hp": 20]]])
            precondition(rows.map(\.target) == [["target": -1], ["target": 4]])
            precondition(panel.action(for: rows[0], values: ["hp": 50])["\(panel.prefix)CharEdit"] as? [String: Int]
                         == ["target": -1, "hp": 50])
        }
        precondition(ModifierBridge.system35LiveState() == nil)
        system35Reply = "{\"game\":\"escalayer\",\"hp\":25}"
        precondition(ModifierBridge.system35LiveState().map(ModifierBridge.parse)?["hp"] as? Int == 25)

        for game in ModifierBridge.games {
            precondition(Set(game.locks.map(\.bit)).count == game.locks.count, game.id)
            precondition(Set(game.cheats.map(\.title)).count == game.cheats.count, game.id)
        }
        precondition(ModifierBridge.currentGame()?.lockGame == MODIFIER_LOCK_DOHNADOHNA)
        precondition(ModifierBridge.liveState()["money"] as? Int == 5)

        liveReplies = [4, 4, 14].map { "{\"inGame\":true,\"foodTicket\":\($0)}" }
        var tickets: [Int] = []
        var polling: Task<Void, Never>?
        polling = Task {
            await ModifierBridge.observeLiveState(every: 1_000_000) { state in
                if let value = state["foodTicket"] as? Int {
                    tickets.append(value)
                    if value == 14 { polling?.cancel() }
                }
            }
        }
        let timeout = Task {
            try await Task.sleep(nanoseconds: 1_000_000_000)
            polling?.cancel()
        }
        await polling?.value
        timeout.cancel()
        precondition(tickets == [4, 14])

        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let save = root.appendingPathComponent("save01.asd")
        try Data([1, 2, 3]).write(to: save)
        let summary = ModifierBridge.readLocalSave(save)
        precondition(ModifierBridge.editableFields(summary).map(\.key) == ["foodTicket", "turn"])
        try ModifierBridge.writeLocalSave(save, changes: ["turn": 9])
        try ModifierBridge.writeLocalSave(save, changes: ["turn": 10])
        let backup = try Data(contentsOf: save.appendingPathExtension("mod.bak"))
        precondition(backup == Data([1, 2, 3]))
        precondition(writes == ["{\"turn\":9}", "{\"turn\":10}"])
        print("ModifierChecks passed")
    }
}
