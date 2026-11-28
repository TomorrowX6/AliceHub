import Foundation

enum LocalGameImport {
    private static let files = FileManager.default
    private static let keys: Set<URLResourceKey> = [.isDirectoryKey, .isRegularFileKey, .isSymbolicLinkKey]
    private static let excludedRoots: Set<String> = [
        "backup", "backdata", "补丁", "汉化补丁", "ll解答集", "备份", "备份文件", "原版有码备份",
        "无码补丁备份", "汉化补丁备份", "特典相关", "全cg存档", "広告", "广告", "patchwm", "patchcn",
        "dll", "old", "commonfiles", "updatefiles", "解答集",
    ]

    static func skip(_ path: String) -> Bool {
        let parts = path.replacingOccurrences(of: "\\", with: "/").split(separator: "/").map { $0.lowercased() }
        guard let leaf = parts.last else { return false }
        if parts.contains(where: { ["android-port", "savedata", "directx"].contains($0) }) ||
            parts.dropLast().contains(where: { $0.hasPrefix(".") }) ||
            (leaf.hasPrefix(".") && leaf != ".xsys35rc" && leaf != ".xsys4rc") {
            return true
        }
        return ["opensavefolder.exe", "resetconfig.exe", "uninstaller.exe"].contains(leaf) ||
            ["dll", "lnk", "bat", "cmd", "ps1", "mds"].contains((leaf as NSString).pathExtension)
    }

    private static func skipArchive(_ path: String) -> Bool {
        let leaf = (path.replacingOccurrences(of: "\\", with: "/") as NSString).lastPathComponent.lowercased()
        return skip(path) || (["rar", "zip", "7z"].contains((leaf as NSString).pathExtension) &&
            (["sound album", "(flac)"].contains(where: leaf.contains) ||
             leaf.range(of: #"(?<![a-z0-9])ost(?![a-z0-9])"#, options: .regularExpression) != nil))
    }

    private static func entries(_ url: URL) throws -> [URL] {
        try files.contentsOfDirectory(at: url, includingPropertiesForKeys: Array(keys))
            .filter {
                let values = try $0.resourceValues(forKeys: keys)
                return values.isSymbolicLink != true &&
                    (values.isDirectory == true || values.isRegularFile == true) &&
                    !skip($0.lastPathComponent)
            }.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    private static func directory(_ url: URL) -> Bool {
        (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
    }

    private static func leaf(_ url: URL) -> String { url.lastPathComponent.lowercased() }

    private static func gameDirectories(_ entries: [URL]) -> [URL] {
        entries.filter { directory($0) && !excludedRoots.contains(leaf($0)) &&
            !excludedRoots.contains(ArchiveExtractor.repairSjisAsGbkMojibake($0.lastPathComponent).lowercased()) }
    }

    private static func directories(_ root: URL, forGameDiscovery: Bool = false) throws -> [URL] {
        var result = [root]
        var index = 0
        while index < result.count {
            let children = try entries(result[index])
            result += forGameDiscovery ? gameDirectories(children) : children.filter(directory)
            index += 1
        }
        return result
    }

    private static func names(_ root: URL) throws -> Set<String> {
        Set(try entries(root).filter { !directory($0) }.map(leaf))
    }

    private static func isCompanionData(_ root: URL, names: Set<String>) -> Bool {
        leaf(root) == "data" && names.contains {
            $0 == "polyobj.lin" || $0 == "event.alk" || $0.hasSuffix(".dgn")
        }
    }

    private static func hasSystem4Root(_ names: Set<String>) -> Bool {
        !names.isDisjoint(with: ["system40.ini", "alicestart.ini", "alicestart2.ini", "alicestart.chs"]) &&
            !system4Code(names).isEmpty
    }

    private static func system4Code(_ names: Set<String>) -> Set<String> {
        names.filter {
            ($0.hasSuffix(".ain") && $0 != "system39.ain") ||
                (names.contains("chs.tbl") && $0.hasSuffix(".chs") && $0 != "alicestart.chs")
        }
    }

    private static func scenario(_ names: Set<String>) -> String? {
        names.sorted().first(where: GameLibrary.isSystem35Scenario).map {
            $0.hasSuffix("_chs.ald") ? String($0.dropLast(8)) + ".ald" : $0
        }
    }

    private static func matchesDisc(engine: GameEngine, names: Set<String>, primaryEngine: GameEngine, primaryNames: Set<String>) -> Bool {
        guard engine == primaryEngine, engine != .legacySystem3 else { return false }
        if engine == .system3, let primary = scenario(primaryNames), let other = scenario(names) {
            return primary == other
        }
        if engine == .system4 {
            let primary = system4Code(primaryNames), other = system4Code(names)
            return primary.isEmpty || other.isEmpty || !primary.isDisjoint(with: other)
        }
        return true
    }

    private static func contains(_ child: URL, in parent: URL) -> Bool {
        let parentPath = parent.resolvingSymlinksInPath().standardizedFileURL.path
        let childPath = child.resolvingSymlinksInPath().standardizedFileURL.path
        return childPath == parentPath || childPath.hasPrefix(parentPath + "/")
    }

    private static func discContainer(_ url: URL) -> URL? {
        var parent = url
        while parent.path != "/" {
            if leaf(parent).range(of: #"^(disc|disk|cd)[ _-]*[0-9]+$"#, options: .regularExpression) != nil {
                return parent
            }
            parent.deleteLastPathComponent()
        }
        return nil
    }

    private static func mergeSystem4DiscAssets(from source: URL, names: Set<String>, primary: URL,
                                               primaryNames: Set<String>, into output: URL,
                                               exclude: (String, Bool) -> Bool) throws {
        guard let mainDisc = discContainer(primary), let otherDisc = discContainer(source),
              mainDisc.path != otherDisc.path,
              mainDisc.deletingLastPathComponent().path == otherDisc.deletingLastPathComponent().path,
              !hasSystem4Root(names), system4Code(names).isEmpty else { return }
        let stems = system4Code(primaryNames).map { ($0 as NSString).deletingPathExtension }
        let assets = names.filter { name in name.hasSuffix(".afa") && stems.contains(where: name.hasPrefix) }
        guard !assets.isEmpty else { return }
        try merge(source, into: output, exclude: { path, isDirectory in
            isDirectory || !assets.contains(path.lowercased()) || exclude(path, isDirectory)
        })
    }

    private static func merge(_ source: URL, into destination: URL, overwrite: Bool = false, move: Bool = false,
                              prefix: String = "", exclude: (String, Bool) -> Bool = { _, _ in false }) throws {
        try files.createDirectory(at: destination, withIntermediateDirectories: true)
        var targets = [String: URL]()
        for item in try files.contentsOfDirectory(at: destination, includingPropertiesForKeys: nil) {
            targets[leaf(item)] = targets[leaf(item)] ?? item
        }
        for item in try entries(source) {
            let name = ArchiveExtractor.repairSjisAsGbkMojibake(item.lastPathComponent)
            guard !name.isEmpty, name != ".", name != "..", !name.contains("/"), !name.contains("\\") else {
                throw ArchiveError.invalidPath
            }
            let relative = prefix.isEmpty ? name : prefix + "/" + name
            let isDirectory = directory(item)
            if exclude(relative, isDirectory) { continue }
            let existing = targets[name.lowercased()]
            let target = existing ?? destination.appendingPathComponent(name, isDirectory: isDirectory)
            if isDirectory {
                if existing == nil || directory(target) {
                    try merge(item, into: target, overwrite: overwrite, move: move, prefix: relative, exclude: exclude)
                }
            } else if existing == nil || (overwrite && !directory(target)) {
                if existing != nil { try files.removeItem(at: target) }
                if move { try files.moveItem(at: item, to: target) }
                else { try files.copyItem(at: item, to: target) }
            }
            targets[name.lowercased()] = target
        }
    }

    private static func ixsealPatchRoots(_ patch: URL, folder: Bool) throws -> [URL] {
        var pending = [(patch, 1)]
        var result = [URL]()
        var index = 0
        while index < pending.count {
            let (root, depth) = pending[index]
            index += 1
            let local = try names(root)
            if !local.isDisjoint(with: ["ixseal.ain", "ixsealcg.afa"]) ||
                (folder && hasSystem4Root(local)) {
                result.append(root)
                if folder || depth == 1 { continue }
            }
            if !folder || depth < 3 {
                pending += try entries(root).filter(directory).map { ($0, depth + 1) }
            }
        }
        return result
    }

    static func stageFolder(_ source: URL, to output: URL) throws {
        var primary: (url: URL, engine: GameEngine, names: Set<String>)?
        var fallbackPrimary: (url: URL, engine: GameEngine, names: Set<String>)?
        var sources = [(url: URL, data: Bool, engine: GameEngine?, names: Set<String>)]()
        var patches = [URL]()
        var pending = [(source, 0)]
        var index = 0
        while index < pending.count {
            let (root, depth) = pending[index]
            index += 1
            let children = try entries(root)
            let local = Set(children.filter { !directory($0) }.map(leaf))
            let launch = try? GameLibrary.launchInfo(root)
            let engine = launch?.engine
            let strongSystem4 = engine == .system4 && (hasSystem4Root(local) || launch?.target.pathExtension.lowercased() == "ini")
            if let engine, depth == 0 || strongSystem4 ||
                (engine == .system3 && local.contains(where: GameLibrary.isSystem35Scenario)) ||
                engine == .legacySystem3 {
                if primary == nil {
                    primary = (root, engine, local)
                    if engine == .legacySystem3 { break }
                } else {
                    guard let primary, matchesDisc(engine: engine, names: local, primaryEngine: primary.engine,
                                                   primaryNames: primary.names) else { continue }
                    sources.append((root, false, engine, local))
                }
                if engine == .system4 { patches += children.filter { directory($0) && leaf($0) == "patchcn" } }
                if depth == 0 { break }
            } else {
                if engine == .system4 && !system4Code(local).isEmpty && fallbackPrimary == nil {
                    fallbackPrimary = (root, engine, local)
                }
                if depth > 0 && isCompanionData(root, names: local) {
                    sources.append((root, true, engine, local))
                } else if depth > 0 && local.contains(where: { ["ald", "bgi", "alk"].contains(($0 as NSString).pathExtension) }) {
                    sources.append((root, false, engine, local))
                } else if depth < 3 {
                    patches += children.filter { directory($0) && leaf($0) == "patchcn" }
                    pending += gameDirectories(children).map { ($0, depth + 1) }
                }
            }
        }
        guard let primary = primary ?? fallbackPrimary else { throw LibraryError.notGame }
        let rules = try LocalImportCleanup(root: primary.url)
        if rules.game == .ixseal {
            for patch in patches {
                for root in try ixsealPatchRoots(patch, folder: true) {
                    try merge(root, into: output, exclude: rules.skip)
                }
            }
        }
        try merge(primary.url, into: output, exclude: rules.skip)
        if primary.engine == .system4, let disc = discContainer(primary.url),
           contains(disc.deletingLastPathComponent(), in: source) {
            for sibling in try gameDirectories(entries(disc.deletingLastPathComponent())) where sibling.path != disc.path {
                for payload in try directories(sibling, forGameDiscovery: true)
                    where discContainer(payload)?.path == sibling.path {
                    try mergeSystem4DiscAssets(from: payload, names: names(payload), primary: primary.url,
                                               primaryNames: primary.names, into: output, exclude: rules.skip)
                }
            }
        }
        for (root, data, engine, local) in sources where primary.engine != .legacySystem3 {
            if let engine, !matchesDisc(engine: engine, names: local, primaryEngine: primary.engine, primaryNames: primary.names) {
                continue
            }
            if data && rules.game != .galzoo { continue }
            let target = data ? try dataDirectory(in: output) : output
            try merge(root, into: target, prefix: data ? "Data" : "", exclude: rules.skip)
        }
        if rules.game == .rance02 || rules.game == .galzoo {
            try mergeInstallData(output, searchRoot: output, all: directories(output), rules: rules)
        }
        try GameLibrary.normalizeRance06DungeonData(output)
        guard GameLibrary.isGame(output) else { throw LibraryError.notGame }
    }

    private static func dataDirectory(in root: URL) throws -> URL {
        if directory(root), let data = try entries(root).first(where: { directory($0) && leaf($0) == "data" }) {
            return data
        }
        return root.appendingPathComponent("Data", isDirectory: true)
    }

    private static func archiveBaseName(_ source: URL) -> String {
        let name = source.lastPathComponent
        for suffix in [".tar.gz", ".tar.xz"] where name.lowercased().hasSuffix(suffix) {
            return String(name.dropLast(suffix.count))
        }
        return source.deletingPathExtension().lastPathComponent
    }

    private static func isArchiveGameRoot(_ url: URL) -> Bool {
        guard let launch = try? GameLibrary.launchInfo(url) else { return false }
        switch launch.engine {
        case .system4:
            guard let local = try? names(url) else { return false }
            return hasSystem4Root(local) || launch.target.pathExtension.lowercased() == "ini"
        case .system3, .legacySystem3:
            return true
        }
    }

    static func stageArchive(_ source: URL, in work: URL, onProgress: @escaping (Int64, String) -> Void = { _, _ in })
        throws -> (root: URL, name: String) {
        var extracted: Int64 = 0
        func extract(_ archive: URL, to stage: URL) throws {
            var latest: Int64 = 0
            try ArchiveExtractor.extract(archive, to: stage, shouldSkip: skipArchive, onProgress: { bytes, entry in
                latest = bytes
                onProgress(extracted + bytes, entry)
            })
            extracted += latest
        }
        func find(_ stage: URL, depth: Int) throws -> (URL, URL)? {
            let dirs = try directories(stage, forGameDiscovery: true)
            if let root = dirs.first(where: isArchiveGameRoot) ?? dirs.first(where: GameLibrary.isGame) {
                let installSet = root.deletingLastPathComponent()
                if leaf(root) == "installdata", leaf(installSet) == "installset" {
                    let outer = installSet.deletingLastPathComponent()
                    if try names(outer).contains(where: { $0.hasSuffix(".ain") && $0 != "system39.ain" }) {
                        return (outer, stage)
                    }
                }
                return (root, stage)
            }
            guard depth < 3 else { return nil }
            let items = try dirs.flatMap { try entries($0) }
            let archives = items.filter {
                !directory($0) && ArchiveExtractor.isSupportedArchiveName($0.lastPathComponent)
            }
            let sized: [(url: URL, size: Int)] = try archives.map {
                ($0, try $0.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0)
            }
            let candidates = sized.sorted {
                $0.size == $1.size ? $0.url.path < $1.url.path : $0.size > $1.size
            }.map(\.url)
            let discs = candidates.filter {
                ["iso", "mdf"].contains($0.pathExtension.lowercased()) && (try? Iso9660Extractor.containsGameRoot($0)) == true
            }
            for archive in discs + candidates.filter({ !discs.contains($0) }) {
                let nested = work.appendingPathComponent("stage-\(UUID().uuidString)")
                do {
                    try extract(archive, to: nested)
                } catch ArchiveError.noSpace {
                    throw ArchiveError.noSpace
                } catch {
                    try? files.removeItem(at: nested)
                    continue
                }
                if let found = try find(nested, depth: depth + 1) { return found }
                try files.removeItem(at: nested)
            }
            return nil
        }
        let initial = work.appendingPathComponent("stage0")
        try extract(source, to: initial)
        guard let (root, stage) = try find(initial, depth: 0) else { throw LibraryError.archiveNotGame }
        let name = root.path == stage.path ? archiveBaseName(source) : root.lastPathComponent
        try finishArchive(root, work: work)
        guard GameLibrary.isGame(root) else { throw LibraryError.archiveNotGame }
        return (root, name)
    }

    private static func finishArchive(_ root: URL, work: URL) throws {
        let all = try directories(work)
        if (try? GameLibrary.launchInfo(root).engine) == .system4 {
            let primary = try names(root)
            for sibling in try directories(work, forGameDiscovery: true) {
                try mergeSystem4DiscAssets(from: sibling, names: names(sibling), primary: root,
                                           primaryNames: primary, into: root, exclude: { _, _ in false })
            }
        } else if let engine = try? GameLibrary.launchInfo(root).engine {
            let allowed: Set<String> = ["ald", "bgi", "wai", "alk", "s3l", "ini", "ain", "gr", "tbl"]
            let primary = try names(root)
            for sibling in all where sibling.path != root.path {
                let relative = sibling.path.dropFirst(work.path.count).lowercased()
                guard relative.contains("disc") || relative.contains("disk") || relative.hasSuffix("/gamedata") else { continue }
                if let other = try? GameLibrary.launchInfo(sibling).engine,
                   !matchesDisc(engine: other, names: try names(sibling), primaryEngine: engine, primaryNames: primary) {
                    continue
                }
                try merge(sibling, into: root, move: true, exclude: { name, dir in
                    dir || (!allowed.contains((name as NSString).pathExtension.lowercased()) && name.lowercased() != "alice.exe")
                })
            }
        }
        let ains = Set(try names(root).filter { $0.hasSuffix(".ain") && $0 != "system39.ain" })
        let gameDepth = root.resolvingSymlinksInPath().pathComponents.count
        for patch in all where leaf(patch) == "commonfiles" {
            let inGame = contains(patch, in: root)
            let patchPath = patch.resolvingSymlinksInPath().pathComponents
                .dropFirst(gameDepth).map { $0.lowercased() }
            if inGame && patchPath != ["updatefiles", "files", "commonfiles"] { continue }
            let local = try names(patch)
            let patchAins = local.filter { $0.hasSuffix(".ain") && $0 != "system39.ain" }
            if !ains.isEmpty && !patchAins.isEmpty && ains.isDisjoint(with: patchAins) { continue }
            try merge(patch, into: root, overwrite: true, exclude: { relative, _ in
                inGame && patchPath.starts(with: relative.lowercased().split(separator: "/").map(String.init))
            })
        }
        let rules = try LocalImportCleanup(root: root)
        try mergeInstallData(root, searchRoot: work, all: all, rules: rules)
        if rules.game == .ixseal {
            for patch in all where leaf(patch) == "patchcn" {
                for payload in try ixsealPatchRoots(patch, folder: false) {
                    try merge(payload, into: root, overwrite: true, move: true, exclude: rules.skip)
                }
            }
        }
        try GameLibrary.normalizeRance06DungeonData(root)
        try prune(root, rules: LocalImportCleanup(root: root))
    }

    private static func mergeInstallData(_ root: URL, searchRoot: URL, all: [URL], rules: LocalImportCleanup) throws {
        if rules.game == .rance02 {
            for data in all where leaf(data) == "installdata" && leaf(data.deletingLastPathComponent()) == "installset" {
                guard !contains(root, in: data), directory(data) else { continue }
                try merge(data, into: root, exclude: rules.skip)
                let parent = data.deletingLastPathComponent()
                if contains(parent, in: root), !contains(root, in: parent) { try files.removeItem(at: parent) }
            }
        } else if rules.game == .galzoo {
            let target = try dataDirectory(in: root)
            for data in all where leaf(data) == "data" && !contains(target, in: data) && !contains(data, in: target) {
                let relative = data.path.dropFirst(searchRoot.path.count).lowercased().split(separator: "/")
                guard relative.allSatisfy({ !["backdata", "补丁", "备份", "备份文件"].contains($0) }),
                      directory(data), try isCompanionData(data, names: names(data)) else { continue }
                try merge(data, into: target, prefix: "Data", exclude: rules.skip)
                if contains(data, in: root) {
                    try files.removeItem(at: data)
                    let parent = data.deletingLastPathComponent()
                    if parent.path != root.path, try files.contentsOfDirectory(atPath: parent.path).isEmpty {
                        try files.removeItem(at: parent)
                    }
                }
            }
        }
    }

    private static func prune(_ root: URL, rules: LocalImportCleanup, prefix: String = "") throws {
        for item in try entries(root) {
            let relative = prefix.isEmpty ? item.lastPathComponent : prefix + "/" + item.lastPathComponent
            let isDirectory = directory(item)
            if rules.skip(relative, isDirectory) {
                try files.removeItem(at: item)
            } else if isDirectory {
                try prune(item, rules: rules, prefix: relative)
            }
        }
    }
}
