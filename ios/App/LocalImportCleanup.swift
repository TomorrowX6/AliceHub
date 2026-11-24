import Foundation

struct LocalImportCleanup {
    enum Game {
        case rance02, rance04, kichikuou, daiakuji, evenicle2, escalayerReboot, rance06
        case galzoo, ixseal, haruka, tsumashibori, tsumamigui3
    }

    let game: Game?
    private let hasDDemo: Bool

    init(root: URL) throws {
        let entries = try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: [.isRegularFileKey, .isSymbolicLinkKey])
        let regular = try entries.filter {
            let info = try $0.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
            return info.isRegularFile == true && info.isSymbolicLink != true
        }
        let names = Set(regular.flatMap {
            [$0.lastPathComponent.lowercased(), ArchiveExtractor.repairSjisAsGbkMojibake($0.lastPathComponent).lowercased()]
        })
        hasDDemo = names.contains("ddemo.alk")
        guard names.contains(where: { [".ain", ".ald", ".afa"].contains(where: $0.hasSuffix) }) else {
            game = nil
            return
        }
        game = Self.classify(names, regular: regular, root: root)
    }

    private static func classify(_ names: Set<String>, regular: [URL], root: URL) -> Game? {
        func has(_ signatures: [String]) -> Bool { !names.isDisjoint(with: signatures) }
        func matches(_ pattern: String) -> Bool {
            names.contains { $0.range(of: pattern, options: .regularExpression) != nil }
        }
        func read(_ url: URL) -> String? {
            guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
            defer { try? handle.close() }
            guard let data = try? handle.read(upToCount: 256 * 1024 + 1), data.count <= 256 * 1024 else { return nil }
            return ArchiveExtractor.decodeBestCjkText(data).lowercased()
        }
        let isSystem35 = (try? GameLibrary.launchInfo(root).engine) == .system3
        if isSystem35 {
            if has(["rance4sa.ald", "rance4sa_chs.ald"]) { return .rance04 }
            if has(["kichikurancesa.ald", "kichikurancega.ald", "鬼畜王sa.ald", "鬼畜王ga.ald"]) { return .kichikuou }
            if matches(#"^(大悪司|大恶司|daiakuji_?|daiakuzi|戭埆巌|戝埆巌)(sa|ga|wa|da)\.ald$"#) { return .daiakuji }
            let audio = regular.filter { ["playlist.txt", "_inmm.ini"].contains($0.lastPathComponent.lowercased()) }
                .compactMap(read).joined()
            let bgm = ((try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: [.isDirectoryKey, .isSymbolicLinkKey])) ?? [])
                .first {
                    let values = try? $0.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
                    return $0.lastPathComponent.lowercased() == "bgm" && values?.isDirectory == true && values?.isSymbolicLink != true
                }.flatMap { try? FileManager.default.contentsOfDirectory(atPath: $0.path) } ?? []
            if audio.contains("kichiku_") || bgm.contains(where: { $0.lowercased().hasPrefix("kichiku_") }) { return .kichikuou }
            if ["daiakuji_", "daiakuzi_"].contains(where: { prefix in
                audio.contains(prefix) || bgm.contains { $0.lowercased().hasPrefix(prefix) }
            }) { return .daiakuji }
            return nil
        }

        let ini = regular.first { ["system40.ini", "alicestart.ini", "alicestart2.ini", "alicestart.chs"].contains($0.lastPathComponent.lowercased()) }
        let title = ini.flatMap(read)?.split(whereSeparator: \.isNewline).first { line in
            let key = line.split(separator: "=", maxSplits: 1).first?.trimmingCharacters(in: .whitespaces)
            return key == "gamename"
        }.flatMap { $0.split(separator: "=", maxSplits: 1).dropFirst().first }.map(String.init) ?? ""
        func titleHas(_ tokens: [String]) -> Bool { tokens.contains(where: title.contains) }
        if has(["evenicle2rance.ain", "evenicle2rancecg.afa", "evenicle2rancechs.afa", "evenicle2rancechs2.afa", "evenicle2rancepact.afa"]) ||
            titleHas(["evenicle2rance", "イブニクル2ランス", "イブニクル２ランス", "夏娃年代记2兰斯"]) { return nil }
        if has(["evenicle2.ain"]) || titleHas(["evenicle2", "イブニクル2", "イブニクル２", "夏娃年代记2"]) { return .evenicle2 }
        if has(["escalayerreboot.ain", "エスカレイヤー・リブート.ain", "エスカレイヤーリブート.ain"]) ||
            titleHas(["escalayer reboot", "escalayerreboot", "エスカレイヤー・リブート", "エスカレイヤーリブート"]) { return .escalayerReboot }
        if has(["rance02.ain", "ランス02.ain", "ランス０２.ain"]) ||
            matches(#"^ランス(02|０２).*\.ald$"#) || titleHas(["rance02", "ランス02", "ランス０２", "兰斯02"]) { return .rance02 }
        if !matches(#"(rance6after|ranceviafter|rancevisonogo|ランス[6６]その後|兰斯0?6后日谈)"#),
           !titleHas(["after", "その後", "后日谈"]),
           has(["rance6.ain", "rance06.ain", "rancevi.ain", "ランス6.ain", "ランス６.ain", "ランス06.ain", "ランス０６.ain"]) ||
           matches(#"(ゼス崩壊|赛斯崩坏)"#) || titleHas(["rance vi", "rance06", "ランス6", "ランス６", "兰斯6"]) { return .rance06 }
        if has(["galzoo.ain", "galzooisland.ain", "galzoo island.ain", "galzoo_island.ain", "galzooアイランド.ain", "ギャルズーアイランド.ain"]) ||
            titleHas(["galzoo", "ギャルズーアイランド"]) { return .galzoo }
        if has(["ixseal.ain", "エクシール.ain", "超昂神騎エクシール.ain", "超昂神骑.ain", "超昂神騎.ain", "ixsealcg.afa"]) ||
            titleHas(["ixseal", "エクシール", "超昂神骑"]) { return .ixseal }
        if !matches(#"(harukavsesca|ハルカ[vｖ][sｓ]|harukaninja|shippuu|疾[風风]|忍者大作[戦战])"#),
           !titleHas(["vs", "ｖｓ", "疾風", "疾风"]),
           has(["haruka.ain", "ハルカ.ain", "超昂閃忍ハルカ.ain", "超昂闪忍遥.ain", "超昂閃忍遥.ain", "beatbladesharuka.ain", "choukousenninharuka.ain", "harukadll.inc"]) ||
           matches(#"^(超昂閃忍ハルカ|超昂闪忍遥|haruka)[a-z]{2}\.ald$"#) ||
           titleHas(["haruka", "ハルカ", "超昂闪忍遥"]) { return .haruka }
        if has(["tsumashibori.ain", "tsumasibori.ain", "tumashibori.ain", "tumasibori.ain", "妻しぼり.ain", "嵢偟傏傝.ain", "ツマシボリ.ain", "妻管严.ain"]) ||
            matches(#"^(妻しぼり|嵢偟傏傝|tsumashibori)[a-z]{2}\.ald$"#) || titleHas(["tsumashibori", "妻しぼり", "妻管严"]) { return .tsumashibori }
        if !matches(#"(tsumamigui3(jun|ntr)|tsuma3so)"#), !titleHas(["スピンオフ", "番外"]),
           has(["tsumamigui3.ain", "tumamigui3.ain"]) ||
           titleHas(["tsumamigui3", "妻みぐい3", "妻みぐい３", "妻中蜜3"]) { return .tsumamigui3 }
        return nil
    }

    func skip(_ path: String, _ isDirectory: Bool) -> Bool {
        guard let game else { return false }
        let parts = path.lowercased().split(separator: "/").map(String.init)
        guard let leaf = parts.last else { return false }
        let ext = (leaf as NSString).pathExtension
        let nonVanillaBackup = ext == "bak" && !leaf.hasSuffix(".vanilla.bak")
        let windowsFile = ["exe", "dll", "bat", "cmd", "lnk"].contains(ext)
        let commonJunk = windowsFile || ["autorun.inf", "desktop.ini"].contains(leaf)
        func inPaths(_ dirs: [String]) -> Bool { parts.contains(where: dirs.contains) }
        switch game {
        case .rance02:
            return inPaths(["cgpatch", "directx", "补丁"]) || (!isDirectory && (nonVanillaBackup || leaf.contains("翻墙软件")))
        case .rance04:
            return !isDirectory && leaf == "rance4cn.exe"
        case .kichikuou:
            return !isDirectory && ["xsystem35.exe", "system35.exe"].contains(leaf)
        case .daiakuji:
            return inPaths(["dll", "全cg存档", "old"]) || (!isDirectory && (
                ["exe", "dll", "bat"].contains(ext) ||
                ["_inmmcnf.ini", "staff.jpg", "请读我.jpg", "运行游戏前必看进入方法.txt", "@vieira.txt"].contains(leaf) ||
                (hasDDemo && leaf == "ddemo0.alk")))
        case .evenicle2:
            return inPaths(["directx"]) || (!isDirectory && [
                "evenicle2.exe", "opensavefolder.exe", "resetconfig.exe", "uninstaller.exe", "installer.exe",
                "regclean.exe", "startmenu.exe", "autorun.inf", "startmenu.bmp", "installer.ini", "installfiles.ini",
            ].contains(leaf))
        case .escalayerReboot:
            return inPaths(["directx", "dll", "备份文件", "savedata"]) || (!isDirectory && (
                ["exe", "dll", "docx", "rar"].contains(ext) ||
                ["autorun.inf", "startmenu.bmp", "战力对比数字对照表.txt", "战斗技能表.txt"].contains(leaf)))
        case .rance06:
            return inPaths(["backdata", "dll", "cgpatch", "directx", "备份", "备份文件", "补丁"]) ||
                (!isDirectory && (commonJunk || nonVanillaBackup || leaf == "system40tcn.ini"))
        case .galzoo:
            return inPaths(["backdata", "dll", "savedata", "cgpatch", "directx", "备份", "备份文件", "补丁"]) ||
                (!isDirectory && (commonJunk || nonVanillaBackup || ext == "mrk" || leaf == "galzoo.ico"))
        case .ixseal:
            return inPaths(["directx", "dll", "backdata", "savedata", "备份", "备份文件", "原版有码备份",
                            "无码补丁备份", "汉化补丁备份", "特典相关", "全cg存档", "patchwm", "patchcn"]) ||
                commonJunk || ["@vieira.txt", "game uploaded by the user vieira"].contains(leaf)
        case .haruka:
            let repaired = parts.map(ArchiveExtractor.repairSjisAsGbkMojibake)
            return inPaths(["directx", "dll", "backdata", "savedata", "広告", "广告", "备份", "备份文件", "特典相关", "全cg存档"]) ||
                repaired.contains("広告") || commonJunk || nonVanillaBackup || [
                    "setup.bmp", "超昂閃忍ハルカ.ico", "超昂闪忍遥.ico", "haruka.ico",
                    "超昂閃忍ハルカdm.dat", "超昂闪忍遥dm.dat", "harukadm.dat",
                ].contains(repaired.last ?? leaf)
        case .tsumashibori:
            let dirs = ["backup", "backdata", "dll", "directx", "savedata", "汉化补丁", "解答集", "补丁", "备份", "备份文件", "全cg存档"]
            let repaired = parts.map(ArchiveExtractor.repairSjisAsGbkMojibake)
            return inPaths(dirs) || repaired.contains(where: dirs.contains) || commonJunk || nonVanillaBackup ||
                ["请读我.txt", "妻しぼり.ico", "tsumashibori.ico"].contains(repaired.last ?? leaf) || leaf == "嵢偟傏傝.ico"
        case .tsumamigui3:
            return inPaths(["backup", "backdata", "dll", "directx", "savedata", "manual", "installinfo", "license",
                            "补丁", "汉化补丁", "攻略", "备份", "备份文件", "全cg存档"]) ||
                parts.contains(where: { $0.hasSuffix("汉化原版") }) || commonJunk || nonVanillaBackup ||
                leaf.hasSuffix(".rar去掉") || (leaf.hasPrefix("读我") && ext == "txt") || leaf == "tsumamigui3.ico"
        }
    }
}
