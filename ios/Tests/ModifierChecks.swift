import Foundation

struct modifier_lock_game: Equatable { var rawValue: UInt32 }
let MODIFIER_LOCK_RANCE10 = modifier_lock_game(rawValue: 0)
let MODIFIER_LOCK_DOHNADOHNA = modifier_lock_game(rawValue: 1)
let MODIFIER_LOCK_EVENICLE = modifier_lock_game(rawValue: 2)

var masks: [UInt32: Int32] = [:]
var actions: [String] = []
var writes: [String] = []
func modifier_api_set_config_path(_ path: UnsafePointer<CChar>!) {}
func modifier_api_current_game() -> UnsafePointer<CChar>! { ("dohnadohna" as NSString).utf8String }
func modifier_api_get_live_state_json() -> UnsafeMutablePointer<CChar>! { strdup("{\"inGame\":true,\"money\":5}") }
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
    static func main() throws {
        precondition(ModifierBridge.cheatAction(for: "  “whosyourdaddy” ")?["cheatCode"] as? String == "whosyourdaddy")
        precondition(ModifierBridge.cheatAction(for: "   ") == nil)
        precondition(ModifierBridge.applyCheatCode("灌注Eins陈谢谢喵"))
        precondition(masks[0] == ModifierBridge.rance10StreamerModeBit && actions.last!.contains("\"streamerMode\":0"))
        precondition(ModifierBridge.applyCheatCode("开启主播模式"))
        precondition(masks[0] == 0 && actions.last!.contains("\"streamerMode\":1"))

        for game in ModifierBridge.games {
            precondition(Set(game.locks.map(\.bit)).count == game.locks.count, game.id)
            precondition(Set(game.cheats.map(\.title)).count == game.cheats.count, game.id)
        }
        precondition(ModifierBridge.currentGame()?.lockGame == MODIFIER_LOCK_DOHNADOHNA)
        precondition(ModifierBridge.liveState()["money"] as? Int == 5)

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
