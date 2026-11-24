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
        precondition(fetched.totalBytes == manifest.totalBytes && fetched.files.count == 6)
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
        precondition(OnlineGame.all.count == 158 && Set(OnlineGame.all.map(\.id)).count == 158)
        let chineseIds = Set(OnlineGame.all.filter(\.hasChinese).map(\.id))
        precondition(chineseIds.count == 53)
        for cnId in ["rance10", "dohnadohna", "evenicle", "evenicle2", "sengokurance", "rance01", "rance09", "escalayerreboot", "galzoo", "ixseal", "tsumashibori", "tsumamigui", "tsumamigui2", "tsumamigui3", "darcrows", "sanbiki", "pascha3", "bladebriders", "dorapeko", "haniwapetit"] {
            precondition(chineseIds.contains(cnId), "Expected Chinese: \(cnId)")
        }
        for rawId in ["escalayer", "haruka", "toushin1", "toushin2", "paschacpp", "mamatoto", "mamorigami", "dalkgaiden", "vanishpalm", "yakata456", "ayumilive", "carol", "selfgame", "momoiroguardian"] {
            precondition(!chineseIds.contains(rawId), "Expected raw: \(rawId)")
        }
        let cnMarkerDir = root.appendingPathComponent("custom-cn-markers")
        try files.createDirectory(at: cnMarkerDir, withIntermediateDirectories: true)
        precondition(!OnlineGame.hasLocalChineseMarkers(in: cnMarkerDir))
        try Data().write(to: cnMarkerDir.appendingPathComponent("AliceStart.ini"))
        try Data().write(to: cnMarkerDir.appendingPathComponent("Custom_cn.afa"))
        precondition(OnlineGame.hasLocalChineseMarkers(in: cnMarkerDir))
        precondition(OnlineGame.hasChinese(for: Game(url: cnMarkerDir)))
        let uifMarkerDir = root.appendingPathComponent("custom-uif-cn")
        try files.createDirectory(at: uifMarkerDir, withIntermediateDirectories: true)
        try Data().write(to: uifMarkerDir.appendingPathComponent("AliceStart.ini"))
        try Data().write(to: uifMarkerDir.appendingPathComponent("burebura.ain"))
        try Data().write(to: uifMarkerDir.appendingPathComponent("uif_config.json"))
        let uifGame = Game(url: uifMarkerDir)
        precondition(uifGame.artworkKey == "bladebriders" && uifGame.hasLocalChineseMarkers)
        precondition(OnlineGame.hasChinese(for: uifGame))
        let haniwaDir = root.appendingPathComponent("HaniwaPetit")
        try files.createDirectory(at: haniwaDir, withIntermediateDirectories: true)
        try Data().write(to: haniwaDir.appendingPathComponent("AliceStart.ini"))
        try Data().write(to: haniwaDir.appendingPathComponent("ハニワ闇開発室ぷち.ain"))
        let haniwaGame = Game(url: haniwaDir)
        precondition(haniwaGame.artworkKey == "haniwapetit" && OnlineGame.hasChinese(for: haniwaGame))
        let ayumiLiveDir = root.appendingPathComponent("AyumiLiveTest")
        try files.createDirectory(at: ayumiLiveDir, withIntermediateDirectories: true)
        try Data().write(to: ayumiLiveDir.appendingPathComponent("ADISK.DAT"))
        try Data().write(to: ayumiLiveDir.appendingPathComponent("ACG3.DAT"))
        let ayumiLiveGame = Game(url: ayumiLiveDir)
        precondition(ayumiLiveGame.engine == .legacySystem3 && ayumiLiveGame.artworkKey == "ayumilive")
        precondition(!OnlineGame.hasChinese(for: ayumiLiveGame))
        let rawCustomDir = root.appendingPathComponent("custom-raw-game")
        try files.createDirectory(at: rawCustomDir, withIntermediateDirectories: true)
        try Data().write(to: rawCustomDir.appendingPathComponent("System40.ini"))
        try Data().write(to: rawCustomDir.appendingPathComponent("CustomGame.ain"))
        try Data("guide".utf8).write(to: rawCustomDir.appendingPathComponent("中文说明.txt"))
        try Data("guide".utf8).write(to: rawCustomDir.appendingPathComponent("汉化指南.md"))
        precondition(!OnlineGame.hasLocalChineseMarkers(in: rawCustomDir))
        precondition(!OnlineGame.hasChinese(for: Game(url: rawCustomDir)))
        let engRance41Dir = root.appendingPathComponent("rance41-eng")
        try files.createDirectory(at: engRance41Dir, withIntermediateDirectories: true)
        try Data().write(to: engRance41Dir.appendingPathComponent("ADISK.DAT"))
        try Data().write(to: engRance41Dir.appendingPathComponent("ACG.DAT"))
        try Data("[config]\ngame = rance41_eng\n".utf8).write(to: engRance41Dir.appendingPathComponent("system3.ini"))
        let engRance41 = Game(url: engRance41Dir)
        precondition(engRance41.artworkKey == "rance41" && engRance41.hasExplicitNonChineseEdition)
        precondition(!OnlineGame.hasChinese(for: engRance41))
        let cnRance41Dir = root.appendingPathComponent("rance41-cn")
        try files.createDirectory(at: cnRance41Dir, withIntermediateDirectories: true)
        try Data().write(to: cnRance41Dir.appendingPathComponent("ADISK.DAT"))
        try Data().write(to: cnRance41Dir.appendingPathComponent("ACG.DAT"))
        try Data().write(to: cnRance41Dir.appendingPathComponent("simhei.ttf"))
        try Data("[config]\ngame = rance41\nfontfile = simhei.ttf\n".utf8).write(to: cnRance41Dir.appendingPathComponent("system3.ini"))
        let cnRance41 = Game(url: cnRance41Dir)
        precondition(cnRance41.artworkKey == "rance41" && !cnRance41.hasExplicitNonChineseEdition)
        precondition(OnlineGame.hasChinese(for: cnRance41))
        precondition(InstalledModInfo.metaFileName == ".mod_version.json")
        let patchedCustomDir = root.appendingPathComponent("custom-cn-patch-meta")
        try files.createDirectory(at: patchedCustomDir, withIntermediateDirectories: true)
        try Data().write(to: patchedCustomDir.appendingPathComponent("System40.ini"))
        try Data().write(to: patchedCustomDir.appendingPathComponent("CustomGame.ain"))
        try InstalledModInfo(version: "1.0", kind: "cn_patch").write(to: patchedCustomDir)
        let patchedCustom = Game(url: patchedCustomDir)
        precondition(patchedCustom.hasInstalledCnPatch && !patchedCustom.hasLocalChineseMarkers)
        precondition(OnlineGame.hasChinese(for: patchedCustom))
        let invalidPatchDir = root.appendingPathComponent("custom-cn-patch-empty-ver")
        try files.createDirectory(at: invalidPatchDir, withIntermediateDirectories: true)
        try Data().write(to: invalidPatchDir.appendingPathComponent("System40.ini"))
        try Data().write(to: invalidPatchDir.appendingPathComponent("CustomGame.ain"))
        try InstalledModInfo(version: "", kind: "cn_patch").write(to: invalidPatchDir)
        precondition(!Game(url: invalidPatchDir).hasInstalledCnPatch)
        precondition(!OnlineGame.hasChinese(for: Game(url: invalidPatchDir)))
        precondition(OnlineGame.all.contains { $0.id == "maman" && $0.directory == "Maman" })
        precondition(OnlineGame.all.contains { $0.id == "eveniclerance" && $0.directory == "EvenicleRance" })
        precondition(OnlineGame.all.contains { $0.id == "haharanman" && $0.directory == "Haharanman" })
        precondition(OnlineGame.all.contains { $0.id == "tsumamigui2" && $0.directory == "Tsumamigui2" })
        precondition(OnlineGame.all.contains { $0.id == "tsumamigui3jun" && $0.directory == "Tsumamigui3Jun" })
        precondition(OnlineGame.all.contains { $0.id == "tsumamigui3ntr" && $0.directory == "Tsumamigui3Ntr" })
        precondition(OnlineGame.all.contains { $0.id == "diabolique" && $0.directory == "DiaboLiQuE" })
        precondition(OnlineGame.all.contains { $0.id == "gakuenking" && $0.directory == "GakuenKING" })
        precondition(OnlineGame.all.contains { $0.id == "gakuenkingex" && $0.directory == "GakuenKINGEx" })
        precondition(OnlineGame.all.contains { $0.id == "oudouyuusha" && $0.directory == "OudouYuusha" })
        precondition(OnlineGame.all.contains { $0.id == "toushin1" && $0.directory == "Toushin1" })
        precondition(OnlineGame.all.contains { $0.id == "toushin2" && $0.directory == "Toushin2" })
        precondition(OnlineGame.all.contains { $0.id == "darcrows" && $0.directory == "DARCROWS" })
        precondition(OnlineGame.all.contains { $0.id == "ikusamiko" && $0.directory == "IkusaMiko" })
        precondition(OnlineGame.all.contains { $0.id == "onlyyou" && $0.directory == "OnlyYou" })
        precondition(OnlineGame.all.contains { $0.id == "sanbiki" && $0.directory == "Sanbiki" })
        precondition(OnlineGame.all.contains { $0.id == "persiom" && $0.directory == "PERSIOM" })
        precondition(OnlineGame.all.contains { $0.id == "seeinao" && $0.directory == "SeeInAO" })
        precondition(OnlineGame.all.contains { $0.id == "hushaby" && $0.directory == "HushabyBaby" })
        precondition(OnlineGame.all.contains { $0.id == "ayumi" && $0.directory == "AyumiChan" })
        precondition(OnlineGame.all.contains { $0.id == "cielclaire" && $0.directory == "CielClaire" })
        precondition(OnlineGame.all.contains { $0.id == "nightdemon" && $0.directory == "NightDemon" })
        precondition(OnlineGame.all.contains { $0.id == "katsumi" && $0.directory == "Katsumi" })
        precondition(OnlineGame.all.contains { $0.id == "drstop" && $0.directory == "DrStop" })
        precondition(OnlineGame.all.contains { $0.id == "dpsall" && $0.directory == "DpsAll" })
        precondition(OnlineGame.all.contains { $0.id == "koredps" && $0.directory == "KoreDps" })
        precondition(OnlineGame.all.contains { $0.id == "kaerualice" && $0.directory == "KaeruAlice" })
        precondition(OnlineGame.all.contains { $0.id == "prostudentg" && $0.directory == "ProstudentG" })
        precondition(OnlineGame.all.contains { $0.id == "prostudentgood" && $0.directory == "ProstudentGood" })
        precondition(OnlineGame.all.contains { $0.id == "pastelchime" && $0.directory == "PastelChime" })
        precondition(OnlineGame.all.contains { $0.id == "pastelchime2" && $0.directory == "PastelChime2" })
        precondition(OnlineGame.all.contains { $0.id == "yakata3" && $0.directory == "Yakata3" })
        precondition(OnlineGame.all.contains { $0.id == "dalk" && $0.directory == "DALK" })
        precondition(OnlineGame.all.contains { $0.id == "ningengari" && $0.directory == "NingenGari" })
        precondition(OnlineGame.all.contains { $0.id == "paschacpp" && $0.directory == "PaschaCpp" })
        precondition(OnlineGame.all.contains { $0.id == "pascha3" && $0.directory == "Pascha3" })
        precondition(OnlineGame.all.contains { $0.id == "mamatoto" && $0.directory == "Mamatoto" })
        precondition(OnlineGame.all.contains { $0.id == "onlyyourecross" && $0.directory == "OnlyYouReCross" })
        precondition(OnlineGame.all.contains { $0.id == "mamorigami" && $0.directory == "Mamorigami" })
        precondition(OnlineGame.all.contains { $0.id == "ambivalenz" && $0.directory == "AmbivalenZ" })
        precondition(OnlineGame.all.contains { $0.id == "funnybee" && $0.directory == "FunnyBee" })
        precondition(OnlineGame.all.contains { $0.id == "toushin2sp" && $0.directory == "Toushin2Sp" })
        precondition(OnlineGame.all.contains { $0.id == "mugenhouyou" && $0.directory == "MugenHouyou" })
        precondition(OnlineGame.all.contains { $0.id == "otomesenki" && $0.directory == "OtomeSenki" })
        precondition(OnlineGame.all.contains { $0.id == "gakuenhyouryuu" && $0.directory == "GakuenHyouryuu" })
        precondition(OnlineGame.all.contains { $0.id == "maidnosusume" && $0.directory == "MaidNoSusume" })
        precondition(OnlineGame.all.contains { $0.id == "dalkgaiden" && $0.directory == "DalkGaiden" })
        precondition(OnlineGame.all.contains { $0.id == "mamanyonyo" && $0.directory == "Mamanyonyo" })
        precondition(OnlineGame.all.contains { $0.id == "majonoshokuzai" && $0.directory == "MajoNoShokuzai" })
        precondition(OnlineGame.all.contains { $0.id == "saboten" && $0.directory == "Saboten" })
        precondition(OnlineGame.all.contains { $0.id == "doublesensei" && $0.directory == "DoubleSensei" })
        precondition(OnlineGame.all.contains { $0.id == "ojousama" && $0.directory == "Ojousama" })
        precondition(OnlineGame.all.contains { $0.id == "edoranze" && $0.directory == "Edoranze" })
        precondition(OnlineGame.all.contains { $0.id == "vanish" && $0.directory == "Vanish" })
        precondition(OnlineGame.all.contains { $0.id == "hokenshitsu" && $0.directory == "Hokenshitsu" })
        precondition(OnlineGame.all.contains { $0.id == "kamunagara" && $0.directory == "Kamunagara" })
        precondition(OnlineGame.all.contains { $0.id == "erumeiflower" && $0.directory == "ErumeiFlower" })
        precondition(OnlineGame.all.contains { $0.id == "erumeiwakare" && $0.directory == "ErumeiWakare" })
        precondition(OnlineGame.all.contains { $0.id == "manana1" && $0.directory == "Manana1" })
        precondition(OnlineGame.all.contains { $0.id == "manana2" && $0.directory == "Manana2" })
        precondition(OnlineGame.all.contains { $0.id == "manana3" && $0.directory == "Manana3" })
        precondition(OnlineGame.all.contains { $0.id == "manana4" && $0.directory == "Manana4" })
        precondition(OnlineGame.all.contains { $0.id == "manana5" && $0.directory == "Manana5" })
        precondition(OnlineGame.all.contains { $0.id == "tsumasakura" && $0.directory == "TsumaSakura" })
        precondition(OnlineGame.all.contains { $0.id == "shamanssanctuary" && $0.directory == "ShamansSanctuary" })
        precondition(OnlineGame.all.contains { $0.id == "alivez" && $0.directory == "AliveZ" })
        precondition(OnlineGame.all.contains { $0.id == "dungeonsdolls" && $0.directory == "DungeonsDolls" })
        precondition(OnlineGame.all.contains { $0.id == "suzumiya" && $0.directory == "Suzumiya" })
        precondition(OnlineGame.all.contains { $0.id == "shimaima" && $0.directory == "Shimaima" })
        precondition(OnlineGame.all.contains { $0.id == "daibanchougaiden" && $0.directory == "DaibanchouGaiden" })
        precondition(OnlineGame.all.contains { $0.id == "cosplaylemon" && $0.directory == "CosplayLemon" })
        precondition(OnlineGame.all.contains { $0.id == "whiphoney" && $0.directory == "WhipHoney" })
        precondition(OnlineGame.all.contains { $0.id == "onimusu" && $0.directory == "Onimusu" })
        precondition(OnlineGame.all.contains { $0.id == "progrx" && $0.directory == "ProGrx" })
        precondition(OnlineGame.all.contains { $0.id == "dpsvern" && $0.directory == "DpsVerN" })
        precondition(OnlineGame.all.contains { $0.id == "sukiirono" && $0.directory == "Sukiirono" })
        precondition(OnlineGame.all.contains { $0.id == "girls" && $0.directory == "Girls" })
        precondition(OnlineGame.all.contains { $0.id == "debut" && $0.directory == "Debut" })
        precondition(OnlineGame.all.contains { $0.id == "harukavsesca" && $0.directory == "HarukaVsEsca" })
        precondition(OnlineGame.all.contains { $0.id == "harukaninja" && $0.directory == "HarukaNinja" })
        precondition(OnlineGame.all.contains { $0.id == "megane" && $0.directory == "Megane" })
        precondition(OnlineGame.all.contains { $0.id == "rachelsakura" && $0.directory == "RachelSakura" })
        precondition(OnlineGame.all.contains { $0.id == "sabakunika" && $0.directory == "SabakuNika" })
        precondition(OnlineGame.all.contains { $0.id == "vanishpalm" && $0.directory == "VanishPalm" })
        precondition(OnlineGame.all.contains { $0.id == "bladebriders" && $0.directory == "BladeBriders" && $0.hasChinese })
        precondition(OnlineGame.all.contains { $0.id == "dorapeko" && $0.directory == "Dorapeko" && $0.hasChinese })
        precondition(OnlineGame.all.contains { $0.id == "haniwapetit" && $0.directory == "HaniwaPetit" && $0.hasChinese })
        precondition(OnlineGame.all.contains { $0.id == "yakata456" && $0.directory == "Yakata456" })
        precondition(OnlineGame.all.contains { $0.id == "uminiochite" && $0.directory == "UmiNiOchite" })
        precondition(OnlineGame.all.contains { $0.id == "gakuenhyouryuu2" && $0.directory == "GakuenHyouryuu2" })
        precondition(OnlineGame.all.contains { $0.id == "yousei" && $0.directory == "Yousei" })
        precondition(OnlineGame.all.contains { $0.id == "tobira" && $0.directory == "Tobira" })
        precondition(OnlineGame.all.contains { $0.id == "buroburo" && $0.directory == "Buroburo" })
        precondition(OnlineGame.all.contains { $0.id == "kudakero" && $0.directory == "Kudakero" })
        precondition(OnlineGame.all.contains { $0.id == "littlevampire" && $0.directory == "LittleVampire" })
        precondition(OnlineGame.all.contains { $0.id == "intruder" && $0.directory == "Intruder" })
        precondition(OnlineGame.all.contains { $0.id == "crescent" && $0.directory == "CrescentMoonGirl" })
        precondition(OnlineGame.all.contains { $0.id == "tengu" && $0.directory == "AbunaiTengu" })
        precondition(OnlineGame.all.contains { $0.id == "dps" && $0.directory == "DPS" })
        precondition(OnlineGame.all.contains { $0.id == "yakata1" && $0.directory == "Yakata1" })
        precondition(OnlineGame.all.contains { $0.id == "sdpstono" && $0.directory == "SuperDpsTono" })
        precondition(OnlineGame.all.contains { $0.id == "sdpskaizoku" && $0.directory == "SuperDpsKaizoku" })
        precondition(OnlineGame.all.contains { $0.id == "progomake" && $0.directory == "ProstudentGOmake" })
        precondition(OnlineGame.all.contains { $0.id == "ayumihint" && $0.directory == "AyumiHint" })
        precondition(OnlineGame.all.contains { $0.id == "ayumilive" && $0.directory == "AyumiLive" })
        precondition(OnlineGame.all.contains { $0.id == "nisenaguri" && $0.directory == "NiseNaguri" })
        precondition(OnlineGame.all.contains { $0.id == "carol" && $0.directory == "Carol" })
        precondition(OnlineGame.all.contains { $0.id == "othellot" && $0.directory == "Othellot" })
        precondition(OnlineGame.all.contains { $0.id == "masquerade" && $0.directory == "Masquerade" })
        precondition(OnlineGame.all.contains { $0.id == "dekirukana" && $0.directory == "Dekirukana" })
        precondition(OnlineGame.all.contains { $0.id == "tatakikoroshi" && $0.directory == "Tatakikoroshi" })
        precondition(OnlineGame.all.contains { $0.id == "balloonpop" && $0.directory == "BalloonPop" })
        precondition(OnlineGame.all.contains { $0.id == "hiyoko2" && $0.directory == "Hiyoko2" })
        precondition(OnlineGame.all.contains { $0.id == "selfgame" && $0.directory == "SelfGame" })
        precondition(OnlineGame.all.contains { $0.id == "momoiroguardian" && $0.directory == "MomoiroGuardian" && !$0.hasChinese })
        UserDefaults.standard.removeObject(forKey: DownloadRoute.storageKey)
        precondition(OnlineGame.all[0].baseURL.absoluteString == "https://alicehub.000.moe/rance10/")
        UserDefaults.standard.set(DownloadRoute.cn.rawValue, forKey: DownloadRoute.storageKey)
        precondition(OnlineGame.all[0].baseURL.absoluteString == "https://alicehub.000.moe/cn/rance10/")
        UserDefaults.standard.removeObject(forKey: DownloadRoute.storageKey)
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
        var independentStorage = false, protectedCache = false
        let destination = try installer.install(game, manifest: manifest, in: games, baseURL: base) { _, done, total in
            precondition(done >= last && done <= total); last = done
            let staged = stage.appendingPathComponent("game.ain")
            if files.fileExists(atPath: staged.path) {
                let cachedAttributes = try! files.attributesOfItem(atPath: part.path)
                let stagedAttributes = try! files.attributesOfItem(atPath: staged.path)
                precondition((cachedAttributes[.systemFileNumber] as? NSNumber) != (stagedAttributes[.systemFileNumber] as? NSNumber))
                independentStorage = true
            }
            let overlap = stage.appendingPathComponent("overlap.txt")
            if (try? String(contentsOf: overlap, encoding: .utf8)) == "overlaid" {
                let cached = cache.appendingPathComponent("mod_" + String(manifest.files[2].sha256.prefix(16)) + ".rar.part")
                precondition((try? String(contentsOf: cached, encoding: .utf8)) == "original")
                protectedCache = true
            }
        }
        precondition(last == manifest.totalBytes && independentStorage && protectedCache)
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
        let twin = destination.appendingPathComponent("twin.ain")
        try check(try Data(contentsOf: twin) == payload)
        let writer = try FileHandle(forWritingTo: destination.appendingPathComponent("game.ain"))
        try writer.write(contentsOf: Data([0xFF]))
        try writer.close()
        try check(try Data(contentsOf: twin) == payload)
        print("PASS: cloned staging has independent inodes; archive overlays preserve cache; identical-hash installed files have isolated writes")

        let cancelledGames = root.appendingPathComponent("cancelled")
        let stopping = OnlineGameInstaller()
        do {
            _ = try stopping.install(game, manifest: manifest, in: cancelledGames, baseURL: base) { text, done, _ in
                if text.hasPrefix("正在下载") && done >= 4096 { stopping.cancel() }
            }
            preconditionFailure("Cancellation was ignored")
        } catch is CancellationError {}
        precondition(!files.fileExists(atPath: cancelledGames.appendingPathComponent(manifest.baseName).path))
        let resumed = try OnlineGameInstaller().install(game, manifest: manifest, in: cancelledGames, baseURL: base) { _, _, _ in }
        try check(try Data(contentsOf: resumed.appendingPathComponent("game.ain")) == payload)
        print("PASS: cancellation keeps partial downloads hidden; retry resumes and commits atomically")

        // Verify DownloadConcurrency setting defaults to 8, clamps 1...16, and works with concurrency=1 and concurrency=8
        UserDefaults.standard.removeObject(forKey: DownloadConcurrency.storageKey)
        precondition(DownloadConcurrency.current == 8)
        UserDefaults.standard.set(0, forKey: DownloadConcurrency.storageKey)
        precondition(DownloadConcurrency.current == 1)
        UserDefaults.standard.set(99, forKey: DownloadConcurrency.storageKey)
        precondition(DownloadConcurrency.current == 16)
        UserDefaults.standard.set(4, forKey: DownloadConcurrency.storageKey)
        precondition(DownloadConcurrency.current == 4)
        UserDefaults.standard.removeObject(forKey: DownloadConcurrency.storageKey)

        let serialGames = root.appendingPathComponent("serial-mode")
        let serialDest = try OnlineGameInstaller().install(game, manifest: manifest, in: serialGames, baseURL: base, concurrency: 1) { _, _, _ in }
        try check(try Data(contentsOf: serialDest.appendingPathComponent("game.ain")) == payload)
        try check(try String(contentsOf: serialDest.appendingPathComponent("overlap.txt"), encoding: .utf8) == "overlaid")
        print("PASS: DownloadConcurrency defaults/clamping and serial vs concurrent multi-file install parity")

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
