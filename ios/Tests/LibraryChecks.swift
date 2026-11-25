import Foundation
import SwiftUI
import WebKit

func SDL_SetMainReady() {}
func alicehub_ios_begin_game() {}
private var audioShutdownFinished = true
func alicehub_ios_finish_audio_shutdown() -> Bool { audioShutdownFinished }
func alicehub_ios_end_game() {}
func input_set_long_press_ctrl(_ enabled: Bool) {}
enum RuntimeGamepad { static func prepare(for game: String, engine: GameEngine) {} }
enum ModifierBridge { static func prepareForLaunch(gameID: String) {} }
enum SaveSyncSession {
    static func begin(game: String, ready: @escaping () -> Void) { ready() }
    static func end() {}
}

private struct HubLocaleProbe: View {
    let capture: (HubLanguage) -> Void
    @HubLocalizedText private var localized

    var body: some View {
        capture(localized.language)
        return Color.clear.frame(width: 1, height: 1)
    }
}

private var engineLaunches = [String]()
func xsystem4_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    precondition(argc > 1)
    engineLaunches.append(String(cString: argv![Int(argc) - 1]!))
    return 0
}
func xsystem35_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}
func system3_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}

@main
struct LibraryChecks {
    @MainActor
    static func main() throws {
        let commit = "abcdef0123456789abcdef0123456789abcdef01"
        precondition(UpdateCheck.compareURL(commit: commit, tag: "1234567")?.absoluteString ==
                     "https://api.github.com/repos/TomorrowX6/AliceHub/compare/1234567...\(commit)?per_page=1")
        precondition(UpdateCheck.compareURL(commit: commit, tag: "v0.1") == nil)
        precondition(UpdateCheck.compareURL(commit: "unknown", tag: "1234567") == nil)
        precondition(UpdateCheck.isBehind(Data(#"{"status":"behind"}"#.utf8)))
        for status in ["identical", "ahead", "diverged", ""] {
            precondition(!UpdateCheck.isBehind(Data("{\"status\":\"\(status)\"}".utf8)))
        }
        precondition(!UpdateCheck.isBehind(Data("{}".utf8)))
        try ArchiveChecks.run()
        checkGamepadProfiles()
        checkDisplayScales()
        try checkHubTools()
        try checkManualResourceRules()
        checkHubPlurals()
        if #available(macOS 13.0, *) { checkLiveHubPlurals() }
        let files = FileManager.default
        let root = files.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? files.removeItem(at: root) }
        try checkRelaunch(root.appendingPathComponent("Relaunch"))
        let rance = root.appendingPathComponent("R6汉硬1.10")
        let data = rance.appendingPathComponent("dAtA")
        try files.createDirectory(at: data, withIntermediateDirectories: true)
        let copy = data.appendingPathComponent("dUnGeOnDaTa(7).DlF")
        try Data([7]).write(to: copy)
        try GameLibrary.normalizeRance06DungeonData(rance)
        precondition(files.fileExists(atPath: copy.path), "Do not normalize unrelated games")
        try Data().write(to: rance.appendingPathComponent("Rance6.ain"))
        try files.removeItem(at: copy)
        try Data().write(to: data.appendingPathComponent("DungeonData(x).dlf"))
        try files.createDirectory(at: data.appendingPathComponent("DungeonData(0).dlf"), withIntermediateDirectories: false)
        try GameLibrary.normalizeRance06DungeonData(rance)
        let canonical = data.appendingPathComponent("DungeonData.dlf")
        precondition(!files.fileExists(atPath: canonical.path))
        try Data([7]).write(to: copy)
        try GameLibrary.normalizeRance06DungeonData(rance)
        precondition(!files.fileExists(atPath: copy.path))
        let normalizedData = try Data(contentsOf: canonical)
        precondition(normalizedData == Data([7]))
        try Data([8]).write(to: copy)
        try GameLibrary.normalizeRance06DungeonData(rance)
        precondition(files.fileExists(atPath: copy.path))
        let preservedData = try Data(contentsOf: canonical)
        precondition(preservedData == Data([7]))
        do {
            try GameLibrary.normalizeRance06DungeonData(root.appendingPathComponent("missing"))
            preconditionFailure("Missing import root must report an error")
        } catch is CocoaError {}
        let documents = root.appendingPathComponent("Documents")
        let source = root.appendingPathComponent("测试游戏")
        try files.createDirectory(at: source, withIntermediateDirectories: true)
        let library = GameLibrary(documents: documents)
        func samePath(_ first: URL, _ second: URL) -> Bool {
            first.resolvingSymlinksInPath().path == second.resolvingSymlinksInPath().path
        }
        precondition(library.games.isEmpty && library.error == nil)
        precondition(!GameLibrary.isGame(source))
        for marker in ["AliceStart.CHS", "AliceStart.ini", "System40.ini", "test.AIN"] {
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
        let auxChs = source.appendingPathComponent("00_aux.chs")
        try Data().write(to: auxChs)
        precondition(!GameLibrary.isGame(source))
        try Data().write(to: secondAIN)
        try Data().write(to: firstAIN)
        let selectedAIN = try GameLibrary.launchTarget(source)
        precondition(samePath(selectedAIN, firstAIN))
        try files.removeItem(at: auxChs)
        try Data().write(to: source.appendingPathComponent("sYsTeM40.InI"))
        let selectedINI = try GameLibrary.launchTarget(source)
        precondition(samePath(selectedINI, source))
        try files.removeItem(at: source.appendingPathComponent("sYsTeM40.InI"))
        try files.removeItem(at: firstAIN)
        try files.removeItem(at: secondAIN)
        let saves = documents.appendingPathComponent("Saves/test")
        for marker in ["RANCE4SA.ALD", "rance4sa.ald", "RANCE41SA.ALD", "RANCE42SA.ALD"] {
            let file = source.appendingPathComponent(marker)
            try Data().write(to: file)
            let isRance04 = marker.lowercased() == "rance4sa.ald"
            precondition(GameLibrary.isRance04(source) == isRance04)
            precondition(Game(url: source).name == source.lastPathComponent)
            precondition(Game(url: source).displayName == (isRance04 ? "兰斯IV 教团的遗产" : source.lastPathComponent))
            try files.removeItem(at: file)
        }
        let ranceCopy = root.appendingPathComponent("Rance IV copy")
        try files.createDirectory(at: ranceCopy, withIntermediateDirectories: true)
        try Data().write(to: ranceCopy.appendingPathComponent("RANCE4SA.ALD"))
        try Data().write(to: source.appendingPathComponent("RANCE4SA.ALD"))
        let firstCopy = Game(url: source), secondCopy = Game(url: ranceCopy)
        precondition(firstCopy.displayName == secondCopy.displayName)
        precondition(firstCopy.artworkKey == "rance04" && firstCopy.isRance04 && !firstCopy.isRance5d)
        GameLibrary.titleResolver = { _, base in base + " [MOD]" }
        let resolvedCopy = Game(url: source)
        GameLibrary.titleResolver = nil
        precondition(resolvedCopy.resolvedDisplayName == firstCopy.displayName + " [MOD]")
        precondition(firstCopy.name != secondCopy.name)
        precondition(library.savesDirectory(for: firstCopy).lastPathComponent == source.lastPathComponent)
        precondition(library.savesDirectory(for: firstCopy) != library.savesDirectory(for: secondCopy))
        let retainedSave = library.savesDirectory(for: firstCopy).appendingPathComponent("asleep.asd")
        try files.createDirectory(at: retainedSave.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data([1, 2, 3]).write(to: retainedSave)
        let retainedData = try Data(contentsOf: library.savesDirectory(for: firstCopy).appendingPathComponent("asleep.asd"))
        precondition(retainedData == Data([1, 2, 3]))

        // DaiSengoku MOD SaveFolder = "SaveDataDSR" isolates save directory from vanilla Sengoku Rance
        let srDir = root.appendingPathComponent("SengokuRance")
        try files.createDirectory(at: srDir, withIntermediateDirectories: true)
        try Data().write(to: srDir.appendingPathComponent("AliceCN.ain"))
        let srIni = srDir.appendingPathComponent("System40.ini")
        try Data("GameName = \"战国兰斯中文版\"\nCodeName = \"AliceCN.ain\"\nSaveFolder = \"SaveData\"\n".utf8).write(to: srIni)
        let srGame = Game(url: srDir)
        let vanillaSaveDir = library.savesDirectory(for: srGame)
        precondition(vanillaSaveDir.lastPathComponent == "SengokuRance")
        try files.createDirectory(at: vanillaSaveDir, withIntermediateDirectories: true)
        let vanillaSlot = vanillaSaveDir.appendingPathComponent("Save001.asd")
        try Data("vanilla-save".utf8).write(to: vanillaSlot)

        try Data("GameName = \"大战国兰斯 CN Ver.\"\nCodeName = \"DaiSengoku.ain\"\nSaveFolder = \"SaveDataDSR\"\n".utf8).write(to: srIni)
        let dsrSaveDir = library.savesDirectory(for: srGame)
        precondition(dsrSaveDir.lastPathComponent == "SengokuRance\(GameLibrary.daiSengokuSaveDirSuffix)")
        precondition(dsrSaveDir != vanillaSaveDir)
        let dsrArgs = try GameLibrary.launchArguments(srDir, saves: dsrSaveDir)
        precondition(dsrArgs.contains(dsrSaveDir.path))
        try files.createDirectory(at: dsrSaveDir, withIntermediateDirectories: true)
        let dsrSlot = dsrSaveDir.appendingPathComponent("Save001.asd")
        try Data("dsr-save".utf8).write(to: dsrSlot)
        precondition(try Data(contentsOf: vanillaSlot) == Data("vanilla-save".utf8))

        try Data("GameName = \"战国兰斯中文版\"\nCodeName = \"AliceCN.ain\"\nSaveFolder = \"SaveData\"\n".utf8).write(to: srIni)
        let restoredSaveDir = library.savesDirectory(for: srGame)
        precondition(restoredSaveDir == vanillaSaveDir)
        precondition(try Data(contentsOf: restoredSaveDir.appendingPathComponent("Save001.asd")) == Data("vanilla-save".utf8))
        precondition(try Data(contentsOf: dsrSlot) == Data("dsr-save".utf8))
        try files.removeItem(at: source.appendingPathComponent("RANCE4SA.ALD"))
        for marker in ["R5D_SA.ALD", "r5d_sa.ald", "R5D_GA.ALD", "RANCE42SA.ALD"] {
            let file = source.appendingPathComponent(marker)
            try Data().write(to: file)
            let isRance5d = marker.lowercased() == "r5d_sa.ald"
            precondition(GameLibrary.isRance5d(source) == isRance5d)
            precondition(Game(url: source).name == source.lastPathComponent)
            precondition(Game(url: source).displayName == (isRance5d ? "兰斯5D" : source.lastPathComponent))
            if isRance5d {
                try Data().write(to: firstAIN)
                precondition(!GameLibrary.isRance5d(source))
                try files.removeItem(at: firstAIN)
            }
            try files.removeItem(at: file)
        }
        let fakeRance = source.appendingPathComponent("R5D_SA.ALD")
        try files.createDirectory(at: fakeRance, withIntermediateDirectories: false)
        precondition(!GameLibrary.isRance5d(source))
        try files.removeItem(at: fakeRance)
        precondition(!GameLibrary.isRance5d(root.appendingPathComponent("missing")))
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
        let mamanDir = root.appendingPathComponent("Maman")
        try files.createDirectory(at: mamanDir, withIntermediateDirectories: true)
        try Data().write(to: mamanDir.appendingPathComponent("AliceStart.ini"))
        try Data().write(to: mamanDir.appendingPathComponent("Maman.ain"))
        precondition(GameLibrary.isGame(mamanDir))
        let mamanGame = Game(url: mamanDir)
        precondition(mamanGame.engine == .system4 && mamanGame.artworkKey == "maman")
        for (dirName, marker, expectedEngine, expectedKey) in [
            ("EvenicleRance", "EvenicleRance.ain", GameEngine.system4, "eveniclerance"),
            ("Haharanman", "Haharanman.ain", GameEngine.system4, "haharanman"),
            ("Tsumamigui2", "System39.ain", GameEngine.system3, "tsumamigui2"),
            ("Tsumamigui3Jun", "Tsuma3SO_Junai.ain", GameEngine.system4, "tsumamigui3jun"),
            ("Tsumamigui3Ntr", "Tsuma3SO_Netorare.ain", GameEngine.system4, "tsumamigui3ntr"),
            ("DiaboLiQuE", "DIABO_SA.ALD", GameEngine.system3, "diabolique"),
            ("GakuenKINGEx", "学園番外編SA.ald", GameEngine.system3, "gakuenkingex"),
            ("OudouYuusha", "王道SA.ALD", GameEngine.system3, "oudouyuusha"),
            ("Toushin1", "TOUSHINSA.ALD", GameEngine.system3, "toushin1"),
            ("Toushin2", "tt2SA.ALD", GameEngine.system3, "toushin2"),
            ("DARCROWS", "HIME_SA.ALD", GameEngine.system3, "darcrows"),
            ("IkusaMiko", "戦巫女SA.ALD", GameEngine.system3, "ikusamiko"),
            ("Sanbiki", "GBK.ain", GameEngine.system4, "sanbiki"),
            ("PERSIOM", "PERSIOM_SA.ALD", GameEngine.system3, "persiom"),
            ("SeeInAO", "イルカSA.ald", GameEngine.system3, "seeinao"),
            ("HushabyBaby", "幽霊SA.ALD", GameEngine.system3, "hushaby"),
            ("CielClaire", "逃避行SA.ALD", GameEngine.system3, "cielclaire"),
            ("NightDemon", "夢鬼SA.ald", GameEngine.system3, "nightdemon"),
            ("Katsumi", "かつみSA.ALD", GameEngine.system3, "katsumi"),
            ("DrStop", "DS_SA.ALD", GameEngine.system3, "drstop"),
            ("KoreDps", "これＤＰＳ？SA.ALD", GameEngine.system3, "koredps"),
            ("KaeruAlice", "かえるにょ国にょアリスSA.ALD", GameEngine.system3, "kaerualice"),
            ("ProstudentGood", "ＳＲ猿SA.ALD", GameEngine.system3, "prostudentgood"),
            ("PastelChime", "ぱすてるSA.ALD", GameEngine.system3, "pastelchime"),
            ("PastelChime2", "ぱすてる２.ain", GameEngine.system4, "pastelchime2"),
            ("DALK", "DALK_SA.ALD", GameEngine.system3, "dalk"),
            ("NingenGari", "人間狩りSA.ALD", GameEngine.system3, "ningengari"),
            ("PaschaCpp", "ぱすてる＋.ain", GameEngine.system4, "paschacpp"),
            ("Pascha3", "パステルチャイム３.ain", GameEngine.system4, "pascha3"),
            ("Mamatoto", "ママトトSA.ALD", GameEngine.system3, "mamatoto"),
            ("OnlyYouReCross", "オンリーSA.ALD", GameEngine.system3, "onlyyourecross"),
            ("Mamorigami", "守神SA.ALD", GameEngine.system3, "mamorigami"),
            ("GakuenHyouryuu", "学園漂流SA.ALD", GameEngine.system3, "gakuenhyouryuu"),
            ("MaidNoSusume", "メイドSA.ALD", GameEngine.system3, "maidnosusume"),
            ("DalkGaiden", "ＤＡＬＫ外伝.ain", GameEngine.system4, "dalkgaiden"),
            ("Mamanyonyo", "ままにょにょ.ain", GameEngine.system4, "mamanyonyo"),
            ("MajoNoShokuzai", "魔女の贖罪.ain", GameEngine.system4, "majonoshokuzai"),
            ("Saboten", "よくばりサボテン.ain", GameEngine.system4, "saboten"),
            ("DoubleSensei", "だぶる先生らいふ.ain", GameEngine.system4, "doublesensei"),
            ("Ojousama", "お嬢様.ain", GameEngine.system4, "ojousama"),
            ("Edoranze", "エドランゼ.ain", GameEngine.system4, "edoranze"),
            ("Vanish", "ばにしゅ！.ain", GameEngine.system4, "vanish"),
            ("Hokenshitsu", "僕だけの保健室.ain", GameEngine.system4, "hokenshitsu"),
            ("Kamunagara", "神武.ain", GameEngine.system4, "kamunagara"),
            ("Manana1", "まななＶＯＬ１.ain", GameEngine.system4, "manana1"),
            ("Manana2", "まななＶＯＬ２.ain", GameEngine.system4, "manana2"),
            ("Manana3", "まななＶＯＬ３.ain", GameEngine.system4, "manana3"),
            ("Manana4", "まななＶＯＬ４.ain", GameEngine.system4, "manana4"),
            ("Manana5", "まななＶＯＬ５.ain", GameEngine.system4, "manana5"),
            ("TsumaSakura", "妻たちの桜貝.ain", GameEngine.system4, "tsumasakura"),
            ("ShamansSanctuary", "まんくちゅ.ain", GameEngine.system4, "shamanssanctuary"),
            ("AliveZ", "ＡｌｉｖｅＺ.ain", GameEngine.system4, "alivez"),
            ("DungeonsDolls", "ドールズ.ain", GameEngine.system4, "dungeonsdolls"),
            ("Suzumiya", "鈴宮刑務娼館.ain", GameEngine.system4, "suzumiya"),
            ("Shimaima", "しまいま.ain", GameEngine.system4, "shimaima"),
            ("DaibanchouGaiden", "大番長外伝.ain", GameEngine.system4, "daibanchougaiden"),
            ("CosplayLemon", "こすぷれれもん.ain", GameEngine.system4, "cosplaylemon"),
            ("WhipHoney", "ほいっぷハニー.ain", GameEngine.system4, "whiphoney"),
            ("Onimusu", "オニむス.ain", GameEngine.system4, "onimusu"),
            ("ProGrx", "ぷろＧＲＸ.ain", GameEngine.system4, "progrx"),
            ("DpsVerN", "でーぴーえすVer_N.ain", GameEngine.system4, "dpsvern"),
            ("Sukiirono", "透色.ain", GameEngine.system4, "sukiirono"),
            ("Girls", "Ｇｉｒｌｓ.ain", GameEngine.system4, "girls"),
            ("Debut", "ＤＥＢＵＴ.ain", GameEngine.system4, "debut"),
            ("HarukaVsEsca", "ハルカVS.ain", GameEngine.system4, "harukavsesca"),
            ("HarukaNinja", "疾風！？忍者大作戦.ain", GameEngine.system4, "harukaninja"),
            ("Megane", "眼鏡のメイと夏休み.ain", GameEngine.system4, "megane"),
            ("RachelSakura", "レイチェルの桜貝.ain", GameEngine.system4, "rachelsakura"),
            ("SabakuNika", "砂漠のニッカ.ain", GameEngine.system4, "sabakunika"),
            ("VanishPalm", "ばにしゅ！番外.ain", GameEngine.system4, "vanishpalm"),
            ("BladeBriders", "burebura.ain", GameEngine.system4, "bladebriders"),
            ("Dorapeko", "どらぺこ！.ain", GameEngine.system4, "dorapeko"),
            ("HaniwaPetit", "ハニワ闇開発室ぷち.ain", GameEngine.system4, "haniwapetit"),
            ("UnknownYakata456", "４５６_SA.ALD", GameEngine.system3, "yakata456"),
            ("UnknownUmi", "蒼海SA.ald", GameEngine.system3, "uminiochite"),
            ("UnknownGakuen2", "戦記２SA.ALD", GameEngine.system3, "gakuenhyouryuu2"),
            ("UnknownYousei", "妖精SA.ald", GameEngine.system3, "yousei"),
            ("UnknownTobira", "［扉］SA.ALD", GameEngine.system3, "tobira"),
            ("UnknownBuroburo", "壁SA.ALD", GameEngine.system3, "buroburo"),
            ("UnknownKudakero", "当砕SA.ALD", GameEngine.system3, "kudakero"),
            ("UnknownCarol", "キャロルsa.ald", GameEngine.system3, "carol"),
            ("UnknownOthellot", "おせろっとSA.ALD", GameEngine.system3, "othellot"),
            ("UnknownMasquerade", "MQN_sa.ald", GameEngine.system3, "masquerade"),
            ("UnknownDekirukana", "できSA.ALD", GameEngine.system3, "dekirukana"),
            ("UnknownTatakikoroshi", "叩き殺しSA.ALD", GameEngine.system3, "tatakikoroshi"),
            ("UnknownBalloonPop", "風船_SA.ALD", GameEngine.system3, "balloonpop"),
            ("UnknownSelfGame", "SelfSA.ald", GameEngine.system3, "selfgame"),
            ("MomoiroGuardian", "ももんが.ain", GameEngine.system4, "momoiroguardian"),
            ("Rance1Classic", "RANCESA.ALD", GameEngine.system3, "rance1classic"),
            ("Rance2Classic", "RANCE2SA.ALD", GameEngine.system3, "rance2classic"),
            ("Rance3Classic", "RANCE3SA.ALD", GameEngine.system3, "rance3classic")
        ] {
            let gDir = root.appendingPathComponent(dirName)
            try files.createDirectory(at: gDir, withIntermediateDirectories: true)
            try Data().write(to: gDir.appendingPathComponent(marker))
            precondition(GameLibrary.isGame(gDir))
            let g = Game(url: gDir)
            precondition(g.engine == expectedEngine && g.artworkKey == expectedKey)
        }
        for (dirName, legacyId, cgFile, expectedName, expectedKey, checkFn) in [
            ("GakuenKING", "gakuen_king", "ACG.DAT", "学园KING", "gakuenking", GameLibrary.isGakuenKing as (URL) -> Bool),
            ("OnlyYou", "onlyyou", "ACG.DAT", "Only You 世纪末的朱丽叶们", "onlyyou", GameLibrary.isOnlyYou as (URL) -> Bool),
            ("AyumiChan", "ayumi_cd", "ACG0.DAT", "步美物语", "ayumi", GameLibrary.isAyumi as (URL) -> Bool),
            ("DpsAll", "dps_all", "ACG.DAT", "D.P.S. 全部", "dpsall", GameLibrary.isDpsAll as (URL) -> Bool),
            ("ProstudentG", "prog_cd", "ACG.DAT", "职业学生G", "prostudentg", GameLibrary.isProstudentG as (URL) -> Bool),
            ("Yakata3", "yakata3_cd", "ACG.DAT", "爱丽丝之馆3", "yakata3", GameLibrary.isYakata3 as (URL) -> Bool),
            ("AmbivalenZ", "ambivalenz_cd", "ACG.DAT", "AmbivalenZ -二律背反-", "ambivalenz", GameLibrary.isAmbivalenz as (URL) -> Bool),
            ("FunnyBee", "funnybee_cd", "ACG.DAT", "宇宙快盗Funny Bee", "funnybee", GameLibrary.isFunnyBee as (URL) -> Bool),
            ("Toushin2Sp", "toushin2_sp", "ACG.DAT", "斗神都市II 然后，从那以后…", "toushin2sp", GameLibrary.isToushin2Sp as (URL) -> Bool),
            ("MugenHouyou", "mugen", "ACG.DAT", "梦幻泡影", "mugenhouyou", GameLibrary.isMugenHouyou as (URL) -> Bool),
            ("OtomeSenki", "otome", "ACG.DAT", "乙女战记", "otomesenki", GameLibrary.isOtomeSenki as (URL) -> Bool)
        ] {
            let gDir = root.appendingPathComponent(dirName)
            try files.createDirectory(at: gDir, withIntermediateDirectories: true)
            try Data().write(to: gDir.appendingPathComponent("ADISK.DAT"))
            try Data().write(to: gDir.appendingPathComponent(cgFile))
            try Data("game = \(legacyId)\n".utf8).write(to: gDir.appendingPathComponent("system3.ini"))
            precondition(GameLibrary.isGame(gDir) && checkFn(gDir))
            let g = Game(url: gDir)
            precondition(g.engine == .legacySystem3 && g.displayName == expectedName && g.artworkKey == expectedKey)
        }
        let reCrossDir = root.appendingPathComponent("OnlyYou Re-Cross")
        try files.createDirectory(at: reCrossDir, withIntermediateDirectories: true)
        try Data().write(to: reCrossDir.appendingPathComponent("オンリーSA.ALD"))
        precondition(GameLibrary.isGame(reCrossDir) && Game(url: reCrossDir).artworkKey == "onlyyourecross")
        func make256Bytes(crc32 target: UInt32) -> Data {
            var buf = [UInt8](repeating: 0, count: 256)
            var table = [UInt32](repeating: 0, count: 256)
            var rev = [Int](repeating: 0, count: 256)
            for i in 0..<256 {
                var v = UInt32(i)
                for _ in 0..<8 { v = (v & 1) != 0 ? (v >> 1) ^ 0xedb88320 : (v >> 1) }
                table[i] = v
                rev[Int(v >> 24)] = i
            }
            var forward: UInt32 = 0xffffffff
            for i in 0..<252 { forward = (forward >> 8) ^ table[Int((forward ^ UInt32(buf[i])) & 0xff)] }
            var r = target ^ 0xffffffff
            var idx = [Int](repeating: 0, count: 4)
            for i in stride(from: 3, through: 0, by: -1) {
                idx[i] = rev[Int(r >> 24)]
                r = (r ^ table[idx[i]]) << 8
            }
            for i in 0..<4 {
                let b = UInt8((forward ^ UInt32(idx[i])) & 0xff)
                buf[252 + i] = b
                forward = (forward >> 8) ^ table[Int((forward ^ UInt32(b)) & 0xff)]
            }
            return Data(buf)
        }
        for (dirName, crc, cgFile, expectedName, expectedKey, checkFn) in [
            ("UnknownFolder1", UInt32(0xd1bf243b), "ACG.DAT", "学园KING", "gakuenking", GameLibrary.isGakuenKing as (URL) -> Bool),
            ("UnknownFolder2", UInt32(0x832aeb97), "ACG.DAT", "Only You 世纪末的朱丽叶们", "onlyyou", GameLibrary.isOnlyYou as (URL) -> Bool),
            ("UnknownFolder3", UInt32(0xd2bed9ee), "ACG0.DAT", "步美物语", "ayumi", GameLibrary.isAyumi as (URL) -> Bool),
            ("UnknownFolder3FD", UInt32(0x4e2fed2a), "ACG.DAT", "步美物语", "ayumi", GameLibrary.isAyumi as (URL) -> Bool),
            ("UnknownFolder4", UInt32(0xd48b4ec6), "ACG.DAT", "D.P.S. 全部", "dpsall", GameLibrary.isDpsAll as (URL) -> Bool),
            ("UnknownFolder5", UInt32(0xfb0e4a63), "ACG.DAT", "职业学生G", "prostudentg", GameLibrary.isProstudentG as (URL) -> Bool),
            ("UnknownFolder5FD", UInt32(0x5ffbfee7), "ACG.DAT", "职业学生G", "prostudentg", GameLibrary.isProstudentG as (URL) -> Bool),
            ("UnknownFolder6", UInt32(0x7f8f5e2a), "ACG.DAT", "爱丽丝之馆3", "yakata3", GameLibrary.isYakata3 as (URL) -> Bool),
            ("UnknownFolder6FD", UInt32(0x58ebcc99), "ACG.DAT", "爱丽丝之馆3", "yakata3", GameLibrary.isYakata3 as (URL) -> Bool),
            ("UnknownFolder7", UInt32(0x4b10db69), "ACG.DAT", "AmbivalenZ -二律背反-", "ambivalenz", GameLibrary.isAmbivalenz as (URL) -> Bool),
            ("UnknownFolder7FD", UInt32(0xa6b48dfe), "ACG.DAT", "AmbivalenZ -二律背反-", "ambivalenz", GameLibrary.isAmbivalenz as (URL) -> Bool),
            ("UnknownFolder8", UInt32(0xe14e3971), "ACG.DAT", "宇宙快盗Funny Bee", "funnybee", GameLibrary.isFunnyBee as (URL) -> Bool),
            ("UnknownFolder8FD", UInt32(0x731267fa), "ACG.DAT", "宇宙快盗Funny Bee", "funnybee", GameLibrary.isFunnyBee as (URL) -> Bool),
            ("UnknownFolder9", UInt32(0x2172c7b2), "ACG.DAT", "斗神都市II 然后，从那以后…", "toushin2sp", GameLibrary.isToushin2Sp as (URL) -> Bool),
            ("UnknownFolder10", UInt32(0xbb27d1ba), "ACG.DAT", "梦幻泡影", "mugenhouyou", GameLibrary.isMugenHouyou as (URL) -> Bool),
            ("UnknownFolder11", UInt32(0x49a4db15), "ACG.DAT", "乙女战记", "otomesenki", GameLibrary.isOtomeSenki as (URL) -> Bool)
        ] {
            let cDir = root.appendingPathComponent(dirName)
            try files.createDirectory(at: cDir, withIntermediateDirectories: true)
            try make256Bytes(crc32: crc).write(to: cDir.appendingPathComponent("ADISK.DAT"))
            try Data().write(to: cDir.appendingPathComponent(cgFile))
            precondition(GameLibrary.isGame(cDir) && checkFn(cDir))
            let g = Game(url: cDir)
            precondition(g.engine == .legacySystem3 && g.displayName == expectedName && g.artworkKey == expectedKey)
        }
        // Wave 7 D.P.S. SG set 1/2/3 ADISK+BDISK CRC32 detection (even under opaque folder names)
        for (dirName, acrc, bcrc, expectedName, expectedKey) in [
            ("OpaqueSg1A", UInt32(0xab4cda48), UInt32(0xe405d57c), "D.P.S. SG 飞翔的梦", "dpssgfahren"),
            ("OpaqueSg1B", UInt32(0xab4cda48), UInt32(0x23e67d18), "D.P.S. SG 家庭教师是美妙的工作", "dpssgkatei"),
            ("OpaqueSg1C", UInt32(0xab4cda48), UInt32(0x2ec116f2), "D.P.S. SG 信长的淫谋", "dpssgnobunaga"),
            ("OpaqueSg2A", UInt32(0xab4cda48), UInt32(0x41fe8b3d), "D.P.S. SG set2 古董屋", "dpssg2antique"),
            ("OpaqueSg2B", UInt32(0xab4cda48), UInt32(0x6b562c09), "D.P.S. SG set2 不妙的内科体检再临", "dpssg2ikenai"),
            ("OpaqueSg2C", UInt32(0xab4cda48), UInt32(0x1098e78c), "D.P.S. SG set2 朱红之夜", "dpssg2akai"),
            ("OpaqueSg3A", UInt32(0xb77ae133), UInt32(0xa3228b6c), "D.P.S. SG set3 Rabbit P4P", "dpssg3rabbit"),
            ("OpaqueSg3B", UInt32(0xb77ae133), UInt32(0x09b4448a), "D.P.S. SG set3 新婚物语", "dpssg3shinkon"),
            ("OpaqueSg3C", UInt32(0xb77ae133), UInt32(0xbc4525d8), "D.P.S. SG set3 毕业", "dpssg3sotsugyou")
        ] {
            let sgDir = root.appendingPathComponent(dirName)
            try files.createDirectory(at: sgDir, withIntermediateDirectories: true)
            try make256Bytes(crc32: acrc).write(to: sgDir.appendingPathComponent("ADISK.DAT"))
            try make256Bytes(crc32: bcrc).write(to: sgDir.appendingPathComponent("BDISK.DAT"))
            try Data().write(to: sgDir.appendingPathComponent("ACG.DAT"))
            precondition(GameLibrary.isGame(sgDir))
            let g = Game(url: sgDir)
            precondition(g.engine == .legacySystem3 && g.displayName == expectedName && g.artworkKey == expectedKey)
        }
        // Collision guard: Classic Rance 1/2/3 System 3.x must never collide with System 4 remakes or Rance 1 Digest
        let classicR1Opaque = root.appendingPathComponent("OpaqueR1")
        try files.createDirectory(at: classicR1Opaque, withIntermediateDirectories: true)
        try Data().write(to: classicR1Opaque.appendingPathComponent("RANCESA.ALD"))
        precondition(Game(url: classicR1Opaque).artworkKey == "rance1classic")
        let remakeR1 = root.appendingPathComponent("Rance01RemakeCheck")
        try files.createDirectory(at: remakeR1, withIntermediateDirectories: true)
        try Data().write(to: remakeR1.appendingPathComponent("Rance01.ain"))
        precondition(Game(url: remakeR1).artworkKey == "rance01")
        // Collision guards: Sengoku Rance Sanbiki vs Sengoku Rance; KaeruAlice vs Kaerunyo
        let sanbikiTitleDir = root.appendingPathComponent("戦国ランス～三匹が斬ったり突いたり燃やしたり～")
        try files.createDirectory(at: sanbikiTitleDir, withIntermediateDirectories: true)
        try Data().write(to: sanbikiTitleDir.appendingPathComponent("GBK.ain"))
        precondition(Game(url: sanbikiTitleDir).artworkKey == "sanbiki")
        let kaeruAliceDir = root.appendingPathComponent("かえるにょ国にょアリス")
        try files.createDirectory(at: kaeruAliceDir, withIntermediateDirectories: true)
        try Data().write(to: kaeruAliceDir.appendingPathComponent("System39.ain"))
        precondition(Game(url: kaeruAliceDir).artworkKey == "kaerualice")
        let erumeiMainDir = root.appendingPathComponent("UnknownErumei1")
        try files.createDirectory(at: erumeiMainDir, withIntermediateDirectories: true)
        try Data().write(to: erumeiMainDir.appendingPathComponent("エルメイフラワー.ain"))
        try Data(repeating: 1, count: 1803).write(to: erumeiMainDir.appendingPathComponent("エルメイフラワーWA.wai"))
        precondition(Game(url: erumeiMainDir).artworkKey == "erumeiflower")
        let erumeiWakareDir = root.appendingPathComponent("UnknownErumei2")
        try files.createDirectory(at: erumeiWakareDir, withIntermediateDirectories: true)
        try Data().write(to: erumeiWakareDir.appendingPathComponent("エルメイフラワー.ain"))
        try Data(repeating: 1, count: 99).write(to: erumeiWakareDir.appendingPathComponent("エルメイフラワーWA.wai"))
        precondition(Game(url: erumeiWakareDir).artworkKey == "erumeiwakare")
        let vanish2010Dir = root.appendingPathComponent("ばにしゅ！～この手のひらにおっぱいを～")
        try files.createDirectory(at: vanish2010Dir, withIntermediateDirectories: true)
        try Data().write(to: vanish2010Dir.appendingPathComponent("System40.ini"))
        precondition(Game(url: vanish2010Dir).artworkKey == "vanishpalm")
        let harukaVsDir = root.appendingPathComponent("超昂閃忍ハルカ ハルカVSエスカレイヤー")
        try files.createDirectory(at: harukaVsDir, withIntermediateDirectories: true)
        try Data().write(to: harukaVsDir.appendingPathComponent("AliceStart.ini"))
        try Data().write(to: harukaVsDir.appendingPathComponent("HarukaDLL.inc"))
        try Data().write(to: harukaVsDir.appendingPathComponent("ハルカVS.ain"))
        precondition(Game(url: harukaVsDir).artworkKey == "harukavsesca")
        let harukaNinjaDir = root.appendingPathComponent("超昂閃忍ハルカ ―疾風！？忍者大作戦―")
        try files.createDirectory(at: harukaNinjaDir, withIntermediateDirectories: true)
        try Data().write(to: harukaNinjaDir.appendingPathComponent("AliceStart.ini"))
        try Data().write(to: harukaNinjaDir.appendingPathComponent("HarukaDLL.inc"))
        try Data().write(to: harukaNinjaDir.appendingPathComponent("疾風！？忍者大作戦.ain"))
        precondition(Game(url: harukaNinjaDir).artworkKey == "harukaninja")
        let daibanchouGaidenDir = root.appendingPathComponent("大番長外伝")
        try files.createDirectory(at: daibanchouGaidenDir, withIntermediateDirectories: true)
        try Data().write(to: daibanchouGaidenDir.appendingPathComponent("System40.ini"))
        precondition(Game(url: daibanchouGaidenDir).artworkKey == "daibanchougaiden")
        let kamunagara2Dir = root.appendingPathComponent("神武 第弐話")
        try files.createDirectory(at: kamunagara2Dir, withIntermediateDirectories: true)
        try Data().write(to: kamunagara2Dir.appendingPathComponent("System40.ini"))
        precondition(Game(url: kamunagara2Dir).artworkKey == nil)
        let hiyoko2Dir = root.appendingPathComponent("UnknownHiyoko2")
        try files.createDirectory(at: hiyoko2Dir, withIntermediateDirectories: true)
        try Data().write(to: hiyoko2Dir.appendingPathComponent("sa.ald"))
        try Data().write(to: hiyoko2Dir.appendingPathComponent("piyod.dat"))
        precondition(Game(url: hiyoko2Dir).engine == .system3 && Game(url: hiyoko2Dir).artworkKey == "hiyoko2")
        let progOmakeDir = root.appendingPathComponent("UnknownProgOmake")
        try files.createDirectory(at: progOmakeDir, withIntermediateDirectories: true)
        try make256Bytes(crc32: 0xfb0e4a63).write(to: progOmakeDir.appendingPathComponent("ADISK.DAT"))
        try Data().write(to: progOmakeDir.appendingPathComponent("ACG.DAT"))
        try Data("[config]\ngame = prog_omake\n".utf8).write(to: progOmakeDir.appendingPathComponent("system3.ini"))
        precondition(Game(url: progOmakeDir).engine == .legacySystem3 && Game(url: progOmakeDir).artworkKey == "progomake")
        let sdpsTonoDir = root.appendingPathComponent("UnknownSdpsTono")
        try files.createDirectory(at: sdpsTonoDir, withIntermediateDirectories: true)
        try make256Bytes(crc32: 0xc7a20cdf).write(to: sdpsTonoDir.appendingPathComponent("ADISK.DAT"))
        try make256Bytes(crc32: 0xbb1edff1).write(to: sdpsTonoDir.appendingPathComponent("BDISK.DAT"))
        try Data().write(to: sdpsTonoDir.appendingPathComponent("ACG.DAT"))
        precondition(Game(url: sdpsTonoDir).engine == .legacySystem3 && Game(url: sdpsTonoDir).artworkKey == "sdpstono")
        let sdpsKaizokuDir = root.appendingPathComponent("UnknownSdpsKaizoku")
        try files.createDirectory(at: sdpsKaizokuDir, withIntermediateDirectories: true)
        try make256Bytes(crc32: 0xc7a20cdf).write(to: sdpsKaizokuDir.appendingPathComponent("ADISK.DAT"))
        try make256Bytes(crc32: 0xf81829e3).write(to: sdpsKaizokuDir.appendingPathComponent("BDISK.DAT"))
        try Data().write(to: sdpsKaizokuDir.appendingPathComponent("ACG.DAT"))
        precondition(Game(url: sdpsKaizokuDir).engine == .legacySystem3 && Game(url: sdpsKaizokuDir).artworkKey == "sdpskaizoku")
        let ayumiLiveCrcDir = root.appendingPathComponent("UnknownAyumiLive")
        try files.createDirectory(at: ayumiLiveCrcDir, withIntermediateDirectories: true)
        try make256Bytes(crc32: 0x00d15a2b).write(to: ayumiLiveCrcDir.appendingPathComponent("ADISK.DAT"))
        try Data().write(to: ayumiLiveCrcDir.appendingPathComponent("ACG3.DAT"))
        precondition(Game(url: ayumiLiveCrcDir).engine == .legacySystem3 && Game(url: ayumiLiveCrcDir).artworkKey == "ayumilive")
        let yakata3KanaDir = root.appendingPathComponent("アリスの館3")
        try files.createDirectory(at: yakata3KanaDir, withIntermediateDirectories: true)
        try Data().write(to: yakata3KanaDir.appendingPathComponent("ADISK.DAT"))
        try Data().write(to: yakata3KanaDir.appendingPathComponent("ACG.DAT"))
        precondition(Game(url: yakata3KanaDir).artworkKey == "yakata3")
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

        // Directory cloning & name validation checks (Rance 10 multi-version MOD / vanilla switch)
        let r10Src = games.appendingPathComponent("Rance10", isDirectory: true)
        try files.createDirectory(at: r10Src, withIntermediateDirectories: true)
        try Data("GameName = \"Rance10\"\n".utf8).write(to: r10Src.appendingPathComponent("AliceStart.ini"))
        try Data("MOD".utf8).write(to: r10Src.appendingPathComponent("Rance10.ain"))
        try Data("VANILLA".utf8).write(to: r10Src.appendingPathComponent("Rance10.ain.vanilla.bak"))
        try Data("stale".utf8).write(to: r10Src.appendingPathComponent(".mod_apply_1.tmp"))
        library.refresh()
        guard let r10Game = library.games.first(where: { $0.name == "Rance10" }) else {
            preconditionFailure("Expected Rance10 game in library")
        }
        precondition(r10Game.artworkKey == "rance10")
        precondition(library.suggestNextDirectoryName(basePrefix: "Rance10") == "Rance10-1")
        if case .valid(let trimmed) = library.validateNewDirectoryName("  Rance10-原版  ") {
            precondition(trimmed == "Rance10-原版")
        } else {
            preconditionFailure("Expected valid directory name")
        }
        for badName in ["", "   ", ".hidden", "..", "a/b", "a:b", "rance10"] {
            if case .valid = library.validateNewDirectoryName(badName) {
                preconditionFailure("Expected invalid directory name: \(badName)")
            }
        }
        var clonedGame: Game?
        library.cloneDirectory(from: r10Game, named: "Rance10-原版", transformStage: { stage in
            let bak = stage.appendingPathComponent("Rance10.ain.vanilla.bak")
            let ain = stage.appendingPathComponent("Rance10.ain")
            try files.removeItem(at: ain)
            try files.moveItem(at: bak, to: ain)
        }) { created in
            clonedGame = created
        }
        awaitImport()
        precondition(library.error == nil, library.error ?? "Clone failed")
        precondition(clonedGame?.name == "Rance10-原版")
        let clonedDir = games.appendingPathComponent("Rance10-原版")
        precondition((try Data(contentsOf: clonedDir.appendingPathComponent("Rance10.ain"))) == Data("VANILLA".utf8))
        precondition(!files.fileExists(atPath: clonedDir.appendingPathComponent("Rance10.ain.vanilla.bak").path))
        precondition(!files.fileExists(atPath: clonedDir.appendingPathComponent(".mod_apply_1.tmp").path))
        precondition((try Data(contentsOf: r10Src.appendingPathComponent("Rance10.ain"))) == Data("MOD".utf8))
        precondition(library.suggestNextDirectoryName(basePrefix: "Rance10") == "Rance10-1")
        library.cloneDirectory(from: r10Game, named: "Rance10-1")
        awaitImport()
        precondition(library.error == nil)
        precondition(library.suggestNextDirectoryName(basePrefix: "Rance10") == "Rance10-2")
        // Renamed copy whose folder name collides with another game's alias (e.g. "Reboot") must remain Rance 10.
        var renamedR10: Game?
        library.cloneDirectory(from: r10Game, named: "Rance10-Reboot") { created in
            renamedR10 = created
        }
        awaitImport()
        precondition(library.error == nil)
        precondition(renamedR10?.artworkKey == "rance10")
        let cloneLeftovers = try files.contentsOfDirectory(atPath: games.path).filter { $0.hasPrefix(".import-") }
        precondition(cloneLeftovers.isEmpty, "Clone staging directories must be cleaned up")
        print("PASS: Rance 10 directory naming, validation, cloning with clean-vanilla transform, and stage cleanup")

        func fixture(_ base: URL, _ path: String, _ contents: String = "") throws {
            let file = base.appendingPathComponent(path)
            try files.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            try Data(contents.utf8).write(to: file)
        }
        func installed(_ path: String) -> Bool { files.fileExists(atPath: games.appendingPathComponent(path).path) }
        func checkContents(_ path: String, _ expected: String) throws {
            let data = try Data(contentsOf: games.appendingPathComponent(path))
            precondition(String(decoding: data, as: UTF8.self) == expected, path)
        }

        let multi = root.appendingPathComponent("Multi Disc Folder")
        try fixture(multi, "Disc1/GAMEDATA/KichikuRanceSA.ald")
        try fixture(multi, "Disc1/GAMEDATA/dummy.dat", "payload")
        try fixture(multi, "Disc1/GAMEDATA/shared.ald", "first")
        try fixture(multi, "Disc1/GAMEDATA/.xsys35rc")
        try fixture(multi, "Disc1/GAMEDATA/System35.exe")
        try fixture(multi, "Disc2/Shared.ALD", "second")
        try fixture(multi, "Disc2/KichikuRanceGA.ald")
        try fixture(multi, "Disc2/DirectX/setup.exe")
        try fixture(multi, "Disc3/GAMEDATA/OtherSA.ald")
        try fixture(multi, "Disc3/GAMEDATA/OtherGA.ald")
        try fixture(multi, "Disc4/GAMEDATA/Other.ain")
        try fixture(multi, "Disc4/GAMEDATA/System40.ini")
        try fixture(multi, "Disc5/GAMEDATA/KichikuRanceSA_CHS.ald")
        try fixture(multi, "Disc5/GAMEDATA/KichikuRanceWA.ald")
        library.importFolder(multi)
        awaitImport()
        precondition(library.error == nil)
        try checkContents("Multi Disc Folder/shared.ald", "first")
        precondition(installed("Multi Disc Folder/KichikuRanceGA.ald") && installed("Multi Disc Folder/.xsys35rc"))
        precondition(installed("Multi Disc Folder/KichikuRanceWA.ald"))
        precondition(!installed("Multi Disc Folder/OtherGA.ald") && !installed("Multi Disc Folder/Other.ain"))
        precondition(library.games.first { $0.name == "Multi Disc Folder" }?.engine == .system3)
        precondition(!installed("Multi Disc Folder/System35.exe") && !installed("Multi Disc Folder/DirectX"))
        precondition(files.fileExists(atPath: multi.appendingPathComponent("Disc1/GAMEDATA/System35.exe").path))

        try importArchive(makeArchive(multi, name: "multi-disc.zip"), expected: "GAMEDATA")
        try checkContents("GAMEDATA/shared.ald", "first")
        precondition(installed("GAMEDATA/KichikuRanceGA.ald") && !installed("GAMEDATA/OtherGA.ald"))
        precondition(installed("GAMEDATA/KichikuRanceWA.ald") && !installed("GAMEDATA/Other.ain"))

        let system4Discs = root.appendingPathComponent("System 4 Discs")
        try fixture(system4Discs, "Disc 1/Install/System40.ini")
        try fixture(system4Discs, "Disc 1/Install/SplitGame.ain")
        try fixture(system4Discs, "Disc 1/Install/dummy.dat", "payload")
        try fixture(system4Discs, "Disc 1/Install/SplitGameCG.afa", "primary")
        try fixture(system4Discs, "Disc 2/GameData/SplitGameCG.afa", "secondary")
        try fixture(system4Discs, "Disc 2/GameData/SplitGameWA.AFA", "audio")
        try fixture(system4Discs, "Disc 2/GameData/OtherGameCG.afa")
        try fixture(system4Discs, "Disc 3/Other/System40.ini")
        try fixture(system4Discs, "Disc 3/Other/Other.ain")
        try fixture(system4Discs, "Disc 3/Other/SplitGameBG.afa")
        try fixture(system4Discs, "Extras/Disc 4/SplitGameBGM.afa")
        library.importFolder(system4Discs)
        awaitImport()
        precondition(library.error == nil)
        try importArchive(makeArchive(system4Discs, name: "system4-discs.zip"), expected: "Install")
        for game in ["System 4 Discs", "Install"] {
            try checkContents("\(game)/SplitGameCG.afa", "primary")
            try checkContents("\(game)/SplitGameWA.AFA", "audio")
            precondition(!installed("\(game)/OtherGameCG.afa"))
            precondition(!installed("\(game)/SplitGameBG.afa"))
            precondition(!installed("\(game)/SplitGameBGM.afa"))
        }
        library.importFolder(system4Discs.appendingPathComponent("Disc 1"))
        awaitImport()
        precondition(library.error == nil)
        try checkContents("Disc 1/SplitGameCG.afa", "primary")
        precondition(!installed("Disc 1/SplitGameWA.AFA"))

        let ixseal = root.appendingPathComponent("Bundled Ixseal")
        try fixture(ixseal, "Game/System40.ini")
        try fixture(ixseal, "Game/Ixseal.ain", "base")
        try fixture(ixseal, "Game/dummy.dat", "payload")
        try fixture(ixseal, "Game/patchCN/Nested/IXSEAL.AIN", "cn patch")
        try fixture(ixseal, "Game/patchCN/Nested/IxsealCG.afa", "patch data")
        try fixture(ixseal, "Game/patchCN/Nested/setup.exe")
        try fixture(ixseal, "Game/SaveData/slot.dat")
        try fixture(ixseal, "UpdateFiles/Files/CommonFiles/Ixseal.ain", "official update")
        try fixture(ixseal, "UpdateFiles/Files/CommonFiles/update.dat", "official data")
        try fixture(ixseal, "Unrelated/CommonFiles/Unrelated.ain")
        try fixture(ixseal, "Unrelated/CommonFiles/must-not-import.dat")

        library.importFolder(ixseal)
        awaitImport()
        precondition(library.error == nil)
        try checkContents("Bundled Ixseal/IXSEAL.AIN", "cn patch")
        precondition(!installed("Bundled Ixseal/patchCN") && !installed("Bundled Ixseal/setup.exe"))

        try importArchive(makeArchive(ixseal, name: "bundled-patch.zip"), expected: "Game")
        try checkContents("Game/Ixseal.ain", "cn patch")
        try checkContents("Game/update.dat", "official data")
        precondition(installed("Game/IxsealCG.afa") && !installed("Game/patchCN") && !installed("Game/SaveData"))
        precondition(!installed("Game/must-not-import.dat"))

        let innerUpdate = root.appendingPathComponent("in-root-update")
        try fixture(innerUpdate, "System40.ini")
        try fixture(innerUpdate, "Ixseal.ain", "base")
        try fixture(innerUpdate, "dummy.dat", "payload")
        try fixture(innerUpdate, "UpdateFiles/Files/CommonFiles/IXSEAL.AIN", "in-root update")
        try fixture(innerUpdate, "UpdateFiles/Files/CommonFiles/update.dat", "official data")
        try fixture(innerUpdate, "Other/UpdateFiles/Files/CommonFiles/Unrelated.ain")
        try fixture(innerUpdate, "Other/UpdateFiles/Files/CommonFiles/must-not-import.dat")
        try fixture(innerUpdate, "Backup/UpdateFiles/Files/CommonFiles/Ixseal.ain", "old backup")
        try fixture(innerUpdate, "Backup/UpdateFiles/Files/CommonFiles/backup-only.dat")
        try fixture(innerUpdate, "Resources/CommonFiles/not-an-update.dat")
        try importArchive(makeArchive(innerUpdate, name: "In-root Update.zip"), expected: "In-root Update")
        try checkContents("In-root Update/Ixseal.ain", "in-root update")
        try checkContents("In-root Update/update.dat", "official data")
        precondition(!installed("In-root Update/must-not-import.dat") && !installed("In-root Update/not-an-update.dat"))
        precondition(!installed("In-root Update/backup-only.dat"))

        let ostCollection = root.appendingPathComponent("ost-token")
        try fixture(ostCollection, "placeholder.txt")
        try files.copyItem(at: makeArchive(top, name: "ProStudentG.zip"), to: ostCollection.appendingPathComponent("ProStudentG.zip"))
        try files.copyItem(at: makeArchive(top, name: "Game - OST.zip"), to: ostCollection.appendingPathComponent("Game - OST.zip"))
        try importArchive(makeArchive(ostCollection, name: "Game with Soundtrack.zip"), expected: "Game with Soundtrack")
        try files.removeItem(at: ostCollection.appendingPathComponent("ProStudentG.zip"))
        try importArchive(makeArchive(ostCollection, name: "Only Soundtrack.zip"), expected: nil, error: LibraryError.archiveNotGame.localizedDescription)

        let rance02 = root.appendingPathComponent("Bundled Rance02")
        try fixture(rance02, "Rance02.ain")
        try fixture(rance02, "InstallSet/InstallData/extra.ald")
        try fixture(rance02, "junk.bak")
        try fixture(rance02, "saved.vanilla.bak")
        library.importFolder(rance02)
        awaitImport()
        precondition(library.error == nil && installed("Bundled Rance02/extra.ald"))
        precondition(!installed("Bundled Rance02/InstallSet") && !installed("Bundled Rance02/junk.bak"))
        precondition(installed("Bundled Rance02/saved.vanilla.bak"))

        let unknown = root.appendingPathComponent("Unknown Installer Scope")
        try fixture(unknown, "MyGame.ain")
        try fixture(unknown, "install.exe")
        try fixture(unknown, "backup.bak")
        library.importFolder(unknown)
        awaitImport()
        precondition(library.error == nil && installed("Unknown Installer Scope/install.exe") && installed("Unknown Installer Scope/backup.bak"))
        print("PASS: multi-disc folder and archive assembly, patch priority and AIN guard, installer cleanup, companion data, source preservation")
    }

    static func checkDisplayScales() {
        let suite = "AliceHub.DisplayScaleChecks.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let font = GameDisplayScale.font
        let scrollbar = GameDisplayScale.scrollbar
        precondition(font.percent(for: "第一款", defaults: defaults) == 100)
        precondition(scrollbar.percent(for: "第一款", defaults: defaults) == 100)
        font.save(180, for: "第一款", defaults: defaults)
        scrollbar.save(325, for: "第一款", defaults: defaults)
        font.save(50, for: "第二款", defaults: defaults)
        let reopened = UserDefaults(suiteName: suite)!
        precondition(font.percent(for: "第一款", defaults: reopened) == 160)
        precondition(scrollbar.percent(for: "第一款", defaults: reopened) == 300)
        precondition(font.percent(for: "第二款", defaults: reopened) == 75)
        precondition(scrollbar.percent(for: "第二款", defaults: reopened) == 100)
        scrollbar.save(90, for: "第一款", defaults: reopened)
        font.save(100, for: "第一款", defaults: reopened)
        precondition(font.percent(for: "第一款", defaults: reopened) == 100)
        precondition(scrollbar.percent(for: "第一款", defaults: reopened) == 100)
        precondition(font.percent(for: "第二款", defaults: reopened) == 75)
        precondition(font.step == 5 && scrollbar.step == 10)
        print("PASS: per-game font and scrollbar scales, persistence, bounds, defaults and reset")
    }

    static func checkHubTools() throws {
        precondition(HubLanguage.system.resolved(preferredLanguages: ["fr-FR", "ja-JP"]) == .japanese)
        precondition(HubLanguage.system.resolved(preferredLanguages: ["zh-Hant-TW"]) == .chinese)
        precondition(HubLanguage.system.resolved(preferredLanguages: ["de-DE"]) == .english)
        precondition(HubLanguage.english.resolved(preferredLanguages: ["ja"]) == .english)

        let files = FileManager.default
        let root = files.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? files.removeItem(at: root) }
        let caches = root.appendingPathComponent("Caches")
        let games = root.appendingPathComponent("Games")
        let game = games.appendingPathComponent("Installed")
        let manual = game.appendingPathComponent("Manual")
        let saves = root.appendingPathComponent("Saves")
        for directory in [caches, manual, saves] {
            try files.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        let cached = caches.appendingPathComponent("cache")
        let resume = games.appendingPathComponent(".online_mod_cache")
        let scratch = games.appendingPathComponent(".import-remaining")
        let fresh = games.appendingPathComponent(".import-active")
        let index = manual.appendingPathComponent("index.html")
        let save = saves.appendingPathComponent("save.dat")
        for file in [cached, resume, scratch, fresh, index, save] {
            try Data("test data".utf8).write(to: file)
        }
        try files.setAttributes([.modificationDate: Date(timeIntervalSinceNow: -1200)], ofItemAtPath: scratch.path)
        let cleanup = HubCacheFiles(caches: caches, games: games)
        precondition(cleanup.size(of: cleanup.targets(includeDownloads: false)) == 18)
        try cleanup.clear(includeDownloads: false)
        precondition(files.fileExists(atPath: resume.path))
        try cleanup.clear(includeDownloads: true)
        precondition(!files.fileExists(atPath: resume.path))
        precondition(!files.fileExists(atPath: cached.path) && !files.fileExists(atPath: scratch.path))
        precondition(files.fileExists(atPath: fresh.path) && files.fileExists(atPath: save.path))
        let viewer = HubToolFiles.manual(in: game)
        precondition(viewer?.index.path == index.path)
        precondition(!HubToolFiles.contains(save, in: manual))
        let reference = game.appendingPathComponent("quick-reference.html")
        try Data("Quick reference".utf8).write(to: reference)
        let linkedDocuments = root.appendingPathComponent("LinkedDocuments")
        try files.createSymbolicLink(at: linkedDocuments, withDestinationURL: games)
        let linkedGame = linkedDocuments.appendingPathComponent(game.lastPathComponent)
        let linkedViewer = HubToolFiles.manual(in: linkedGame)
        precondition(linkedViewer?.root == viewer?.root)
        let relativeReference = URL(string: "../quick-reference.html", relativeTo: index)!.absoluteURL
        precondition(linkedViewer?.canNavigate(to: relativeReference) == true)
        precondition(linkedViewer?.canNavigate(to: linkedGame.appendingPathComponent("quick-reference.html")) == true)
        precondition(linkedViewer?.canNavigate(to: linkedGame.appendingPathComponent("missing/page.html")) == true)
        let privateGamePath: String
        if game.path.hasPrefix("/var/") {
            privateGamePath = "/private" + game.path
        } else if game.path.hasPrefix("/private/var/") {
            privateGamePath = String(game.path.dropFirst("/private".count))
        } else {
            privateGamePath = linkedGame.path
        }
        let alternateGame = URL(fileURLWithPath: privateGamePath, isDirectory: true)
        precondition(linkedViewer?.canNavigate(to: alternateGame.appendingPathComponent("quick-reference.html")) == true)
        precondition(linkedViewer?.canNavigate(to: alternateGame.appendingPathComponent("missing/page.html")) == true)
        let insideLink = manual.appendingPathComponent("inside.html")
        try files.createSymbolicLink(at: insideLink, withDestinationURL: reference)
        precondition(linkedViewer?.canNavigate(to: insideLink) == true)
        let outsideLink = manual.appendingPathComponent("outside.html")
        try files.createSymbolicLink(at: outsideLink, withDestinationURL: save)
        precondition(linkedViewer?.canNavigate(to: outsideLink) == false)
        precondition(linkedViewer?.canNavigate(to: games.appendingPathComponent("Installed copy/page.html")) == false)
        try files.removeItem(at: index)
        try files.createSymbolicLink(at: index, withDestinationURL: save)
        precondition(HubToolFiles.manual(in: game) == nil)
        precondition(HubToolFiles.logTail(save, limit: 4) == "data")
        print("Language, cache cleanup, local manual, and error report file checks passed")
    }

    @MainActor
    static func checkManualResourceRules() throws {
        var compilation: Result<WKContentRuleList?, Error>?
        WKContentRuleListStore.default().compileContentRuleList(
            forIdentifier: "AliceHubOfflineManualChecks",
            encodedContentRuleList: HubToolFiles.Manual.resourceRules
        ) { rules, error in
            compilation = error.map { .failure($0) } ?? .success(rules)
        }
        let deadline = Date().addingTimeInterval(10)
        while compilation == nil && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        }
        let rules = try compilation?.get()
        precondition(rules != nil, "Offline manual content rules did not compile")
        print("Offline manual content rules compiled")
    }

    static func checkHubPlurals() {
        let resources = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("App", isDirectory: true)
        let bundle = Bundle(url: resources)!
        func text(_ key: String, _ count: Int, language: HubLanguage = .english) -> String {
            language.text(key, arguments: [count], bundle: bundle)
        }
        precondition(text("%lld 款游戏", 0) == "0 games")
        precondition(text("%lld 款游戏", 1) == "1 game")
        precondition(text("%lld 款游戏", 2) == "2 games")
        precondition(text("%lld 个文件", 0) == "0 files")
        precondition(text("%lld 个文件", 1) == "1 file")
        precondition(text("%lld 个文件", 2) == "2 files")
        let confirmation = "确定清空全部 %lld 个存档文件吗？此操作不可恢复。"
        precondition(text(confirmation, 1) == "Delete 1 save file? This can’t be undone.")
        precondition(text(confirmation, 2) == "Delete all 2 save files? This can’t be undone.")
        for count in 0...2 {
            precondition(text("%lld 款游戏", count, language: .japanese) == "ゲーム \(count) 本")
            precondition(text("%lld 个文件", count, language: .japanese) == "\(count) 件のファイル")
            precondition(text(confirmation, count, language: .japanese) == "セーブファイル \(count) 件をすべて削除しますか？この操作は取り消せません。")
            precondition(text("%lld 款游戏", count, language: .chinese) == "\(count) 款游戏")
            precondition(text("%lld 个文件", count, language: .chinese) == "\(count) 个文件")
            precondition(text(confirmation, count, language: .chinese) == "确定清空全部 \(count) 个存档文件吗？此操作不可恢复。")
        }
        print("Game count, file count, and clear-save plural checks passed for en, ja, and zh-Hans")
    }

    @available(macOS 13.0, *)
    @MainActor
    static func checkLiveHubPlurals() {
        let resources = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("App", isDirectory: true)
        let bundle = Bundle(url: resources)!
        var rendered: [String] = []
        let probe = HubLocaleProbe { language in
            rendered = ["%lld 款游戏", "%lld 个文件", "确定清空全部 %lld 个存档文件吗？此操作不可恢复。"].map {
                language.text($0, arguments: [1], bundle: bundle)
            }
        }
        let renderer = ImageRenderer(content: probe.environment(\.locale, HubLanguage.english.locale))
        precondition(renderer.cgImage != nil)
        precondition(rendered == ["1 game", "1 file", "Delete 1 save file? This can’t be undone."])
        renderer.content = probe.environment(\.locale, HubLanguage.japanese.locale)
        precondition(renderer.cgImage != nil)
        precondition(rendered == ["ゲーム 1 本", "1 件のファイル", "セーブファイル 1 件をすべて削除しますか？この操作は取り消せません。"])
        print("SwiftUI plural checks passed after changing the live locale from en to ja")
    }

    static func checkRelaunch(_ documents: URL) throws {
        let library = GameLibrary(documents: documents)
        let originalDirectory = FileManager.default.currentDirectoryPath
        for name in ["First", "Second"] {
            let folder = library.gamesDirectory.appendingPathComponent(name)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let target = folder.appendingPathComponent("\(name).ain")
            try Data().write(to: target)
            library.launch(Game(url: folder))
            precondition(library.launching)
            let deadline = Date().addingTimeInterval(2)
            while library.launching && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.02))
            }
            precondition(!library.launching && library.error == nil)
            let launchedTarget = engineLaunches.last.map { URL(fileURLWithPath: $0).resolvingSymlinksInPath().path }
            precondition(launchedTarget == target.resolvingSymlinksInPath().path)
            precondition(FileManager.default.currentDirectoryPath == originalDirectory)
        }
        precondition(engineLaunches.count == 2)
        audioShutdownFinished = false
        library.launch(Game(url: library.gamesDirectory.appendingPathComponent("First")))
        precondition(!library.launching && library.error == LibraryError.audioStopping.localizedDescription)
        precondition(engineLaunches.count == 2)
        audioShutdownFinished = true
        print("Library relaunch checks passed")
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

        // Legacy saved profiles containing the removed physical.autoHide key must still decode cleanly.
        var legacyDict = try! JSONSerialization.jsonObject(with: try! JSONEncoder().encode(local)) as! [String: Any]
        var legacyPhysical = legacyDict["physical"] as! [String: Any]
        legacyPhysical["autoHide"] = true
        legacyDict["physical"] = legacyPhysical
        let legacyData = try! JSONSerialization.data(withJSONObject: legacyDict)
        let decodedLegacy = try! JSONDecoder().decode(GamepadProfile.self, from: legacyData)
        precondition(decodedLegacy.physical == local.physical && decodedLegacy.virtual.enabled == local.virtual.enabled)
        let reencoded = try! JSONSerialization.jsonObject(with: try! JSONEncoder().encode(decodedLegacy)) as! [String: Any]
        let reencodedPhysical = reencoded["physical"] as! [String: Any]
        precondition(reencodedPhysical["autoHide"] == nil)

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

        var custom = GamepadProfile()
        let combo = PadAction.combo(17, 83, toggle: true)
        precondition(combo.comboTitle == "Ctrl+S")
        custom.virtual.controls[2].action = combo
        custom.physical.bindings[0] = combo
        profiles.save(custom, for: "combo")
        precondition(profiles.active("combo") == custom)
        let customJSON = try! JSONSerialization.jsonObject(with: Data(custom.engineJSON().utf8)) as! [String: Any]
        let customPhysical = customJSON["physical"] as! [String: Any]
        let customBindings = customPhysical["bindings"] as! [String: [String: Any]]
        precondition(customBindings["0"]?["type"] as? Int == 6)
        precondition(customBindings["0"]?["code"] as? Int == 83 && customBindings["0"]?["code2"] as? Int == 17)
        precondition(customBindings["0"]?["toggle"] as? Bool == true)

        let original = custom.virtual.controls[2]
        let deletion = custom.virtual.deleteControl(at: 2)
        precondition(!custom.virtual.controls.contains(original))
        precondition(custom.virtual.addControl(.button, reserving: deletion) != nil)
        precondition(custom.virtual.controls.last?.slot != original.slot)
        precondition(custom.virtual.restore(deletion) == original.id)
        precondition(custom.virtual.controls[2] == original && custom.virtual.restore(deletion) == nil)
        let deletedDPad = custom.virtual.deleteControl(at: 0)
        precondition(!custom.virtual.canAddControl(.dpad, reserving: deletedDPad))
        precondition(custom.virtual.restore(deletedDPad) == deletedDPad.control.id)
        print("Gamepad combo and delete undo checks passed")
    }
}
