import Foundation

func SDL_SetMainReady() {}
enum RuntimeGamepad { static func prepare(for game: String) {} }
enum ModifierBridge { static func prepareForLaunch() {} }
func xsystem4_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}
func xsystem35_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}
func system3_main(_ argc: Int32, _ argv: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?) -> Int32 {
    fatalError("Library checks must not launch the engine")
}

@main
struct LibraryChecks {
    static func main() throws {
        try ArchiveChecks.run()
        checkGamepadProfiles()
        let files = FileManager.default
        let root = files.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? files.removeItem(at: root) }
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
            ("MomoiroGuardian", "ももんが.ain", GameEngine.system4, "momoiroguardian")
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
    }
}
