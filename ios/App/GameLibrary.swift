import Foundation
import Combine
import Darwin

enum GameEngine: String {
    case system3 = "System 3.x"
    case system4 = "System 4"
}

struct Game: Identifiable {
    let url: URL
    var id: String { url.lastPathComponent }
    var name: String { url.lastPathComponent }
    var engine: GameEngine { (try? GameLibrary.launchInfo(url).engine) ?? .system4 }
}

enum LibraryError: LocalizedError {
    case notGame, archiveNotGame, alreadyExists, resourcePath, logFile
    var errorDescription: String? {
        switch self {
        case .notGame: return "所选文件夹不是 System 3.x / System 4 游戏。请确认根目录包含游戏的 INI、AIN、*SA.ALD 或 ADISK.ALD 文件。"
        case .archiveNotGame: return "压缩包或光盘镜像中未找到 System 3.x / System 4 游戏。"
        case .alreadyExists: return "已存在同名游戏文件夹，请先删除或重命名后再导入。"
        case .resourcePath: return "无法找到游戏引擎资源。"
        case .logFile: return "无法创建运行日志。"
        }
    }
}

final class GameLibrary: ObservableObject {
    @Published var games: [Game] = []
    @Published var importing = false
    @Published var importProgress = ""
    @Published var launching = false
    @Published var error: String?
    private let files = FileManager.default
    private let documents: URL
    private var gamesDirectory: URL { documents.appendingPathComponent("Games", isDirectory: true) }

    init(documents: URL? = nil) {
        self.documents = documents ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        refresh()
    }

    static func isGame(_ url: URL) -> Bool {
        (try? launchTarget(url)) != nil
    }

    static func launchTarget(_ url: URL) throws -> URL {
        try launchInfo(url).target
    }

    static func launchInfo(_ url: URL) throws -> (engine: GameEngine, target: URL, system39: Bool) {
        guard (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true,
              let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]) else { throw LibraryError.notGame }
        let regularFiles = contents.filter {
            (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
        if regularFiles.contains(where: { ["alicestart.ini", "system40.ini"].contains($0.lastPathComponent.lowercased()) }) {
            return (.system4, url, false)
        }
        if let ain = regularFiles.filter({ $0.pathExtension.lowercased() == "ain" && $0.lastPathComponent.lowercased() != "system39.ain" })
            .sorted(by: { $0.lastPathComponent < $1.lastPathComponent }).first {
            return (.system4, ain, false)
        }
        let names = regularFiles.map { $0.lastPathComponent.lowercased() }
        let system39 = names.contains("system39.ini") || names.contains("system39.ain")
        guard system39 || names.contains(where: { $0.hasSuffix("sa.ald") || $0 == "adisk.ald" }) else { throw LibraryError.notGame }
        return (.system3, url, system39)
    }

    static func launchArguments(_ url: URL, saves: URL) throws -> [String] {
        let info = try launchInfo(url)
        if info.engine == .system3 {
            return ["xsystem35", "-gamedir", url.path, "-savedir", saves.path] + (info.system39 ? ["-saveformat", "system39"] : [])
        }
        return ["xsystem4", "--save-folder", saves.path, "--save-format=rsm", info.target.path]
    }

    func refresh() {
        do {
            try files.createDirectory(at: gamesDirectory, withIntermediateDirectories: true)
            games = try files.contentsOfDirectory(at: gamesDirectory, includingPropertiesForKeys: [.isDirectoryKey], options: .skipsHiddenFiles)
                .filter(Self.isGame).map { Game(url: $0) }.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        } catch { self.error = error.localizedDescription }
    }

    func importFolder(_ source: URL) {
        guard !importing && !launching else { return }
        importing = true
        error = nil
        importProgress = "正在复制游戏文件夹…"
        let destination = gamesDirectory.appendingPathComponent(source.lastPathComponent, isDirectory: true)
        DispatchQueue.global(qos: .userInitiated).async {
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            var result: Error?
            let temporary = self.gamesDirectory.appendingPathComponent(".import-\(UUID().uuidString)")
            do {
                let coordinator = NSFileCoordinator()
                var coordinationError: NSError?
                coordinator.coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { readable in
                    do {
                        guard Self.isGame(readable) else { throw LibraryError.notGame }
                        guard !self.files.fileExists(atPath: destination.path) else { throw LibraryError.alreadyExists }
                        try self.files.copyItem(at: readable, to: temporary)
                        try self.files.moveItem(at: temporary, to: destination)
                    } catch { result = error }
                }
                if let coordinationError { throw coordinationError }
            } catch { result = error }
            try? self.files.removeItem(at: temporary)
            let message = result?.localizedDescription
            DispatchQueue.main.async {
                self.importing = false
                self.importProgress = ""
                self.refresh()
                self.error = message
            }
        }
    }

    private static func gameRoot(in stage: URL) throws -> URL? {
        var level = [stage]
        while !level.isEmpty {
            for directory in level where isGame(directory) { return directory }
            level = try level.flatMap { directory in
                try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isDirectoryKey, .isSymbolicLinkKey], options: .skipsHiddenFiles)
                    .filter {
                        let values = try $0.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
                        return values.isDirectory == true && values.isSymbolicLink != true
                    }
            }.sorted { $0.path < $1.path }
        }
        return nil
    }

    private static func containedFiles(in stage: URL) throws -> [URL] {
        var result = [URL]()
        var failure: Error?
        let keys: [URLResourceKey] = [.isRegularFileKey, .isSymbolicLinkKey]
        let enumerator = FileManager.default.enumerator(at: stage, includingPropertiesForKeys: keys,
                                                        options: .skipsHiddenFiles) { _, error in
            failure = error
            return false
        }
        while let file = enumerator?.nextObject() as? URL {
            let values = try file.resourceValues(forKeys: Set(keys))
            if values.isRegularFile == true && values.isSymbolicLink != true { result.append(file) }
        }
        if let failure { throw failure }
        let sized: [(url: URL, size: Int)] = try result.map { url in
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            return (url, size)
        }
        return sized.sorted {
            $0.size == $1.size ? $0.url.path < $1.url.path : $0.size > $1.size
        }.map { $0.url }
    }

    private static func archiveBaseName(_ source: URL) -> String {
        let name = source.lastPathComponent
        for suffix in [".tar.gz", ".tar.xz"] where name.lowercased().hasSuffix(suffix) {
            return String(name.dropLast(suffix.count))
        }
        return source.deletingPathExtension().lastPathComponent
    }

    func importArchive(_ source: URL) {
        guard !importing && !launching else { return }
        importing = true
        error = nil
        importProgress = "正在解压：0 MB"
        DispatchQueue.global(qos: .userInitiated).async {
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            let temporary = self.gamesDirectory.appendingPathComponent(".import-\(UUID().uuidString)")
            var result: Error?
            do {
                try self.files.createDirectory(at: temporary, withIntermediateDirectories: true)
                let coordinator = NSFileCoordinator()
                var coordinationError: NSError?
                coordinator.coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { readable in
                    do {
                        var lastProgress = Date.distantPast
                        var completedBytes: Int64 = 0
                        func extract(_ archive: URL, to stage: URL) throws {
                            var latestBytes: Int64 = 0
                            try ArchiveExtractor.extract(archive, to: stage, onProgress: { bytes, entry in
                                latestBytes = bytes
                                let now = Date()
                                guard now.timeIntervalSince(lastProgress) >= 0.15 else { return }
                                lastProgress = now
                                let text = "正在解压：\((completedBytes + bytes) / (1024 * 1024)) MB\n\(entry)"
                                DispatchQueue.main.async { self.importProgress = text }
                            })
                            completedBytes += latestBytes
                        }
                        var stage = temporary.appendingPathComponent("stage0")
                        try extract(readable, to: stage)
                        var root = try Self.gameRoot(in: stage)
                        for number in 1...3 where root == nil {
                            let contained = try Self.containedFiles(in: stage)
                            let discs = contained.filter { ["iso", "mdf"].contains($0.pathExtension.lowercased()) }
                            let disc = discs.first { (try? Iso9660Extractor.containsGameRoot($0)) == true }
                            guard let nested = disc ?? contained.first(where: { ArchiveExtractor.isSupportedArchiveName($0.lastPathComponent) }) else { break }
                            let next = temporary.appendingPathComponent("stage\(number)")
                            try extract(nested, to: next)
                            try self.files.removeItem(at: nested)
                            if ["iso", "mdf"].contains(nested.pathExtension.lowercased()) {
                                for sidecar in contained where sidecar.pathExtension.lowercased() == "mds" {
                                    try self.files.removeItem(at: sidecar)
                                }
                            }
                            stage = next
                            root = try Self.gameRoot(in: stage)
                        }
                        guard let root else { throw LibraryError.archiveNotGame }
                        let name = root == stage ? Self.archiveBaseName(source) : root.lastPathComponent
                        guard !name.isEmpty && name != "." && name != ".." && !name.hasPrefix(".") else { throw LibraryError.archiveNotGame }
                        let destination = self.gamesDirectory.appendingPathComponent(name, isDirectory: true)
                        guard !self.files.fileExists(atPath: destination.path) else { throw LibraryError.alreadyExists }
                        try self.files.moveItem(at: root, to: destination)
                    } catch { result = error }
                }
                if let coordinationError { throw coordinationError }
            } catch { result = error }
            try? self.files.removeItem(at: temporary)
            let message = result?.localizedDescription
            DispatchQueue.main.async {
                self.importing = false
                self.importProgress = ""
                self.refresh()
                self.error = message
            }
        }
    }

    func delete(_ game: Game) {
        do {
            try files.removeItem(at: game.url)
            refresh()
        } catch { self.error = error.localizedDescription }
    }

    func savesDirectory(for game: Game) -> URL {
        documents.appendingPathComponent("Saves").appendingPathComponent(game.name)
    }

    func launch(_ game: Game) {
        guard !launching else { return }
        do {
            let info = try Self.launchInfo(game.url)
            guard let resources = Bundle.main.resourceURL else { throw LibraryError.resourcePath }
            let home = files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("xsystem4")
            let saves = savesDirectory(for: game)
            let logs = documents.appendingPathComponent("Logs")
            for directory in [home, saves, logs] {
                try files.createDirectory(at: directory, withIntermediateDirectories: true)
            }
            let log = logs.appendingPathComponent("engine.log")
            let descriptor = open(log.path, O_WRONLY | O_CREAT | O_TRUNC, mode_t(0o644))
            guard descriptor >= 0 else { throw LibraryError.logFile }
            defer { close(descriptor) }
            guard dup2(descriptor, STDOUT_FILENO) >= 0, dup2(descriptor, STDERR_FILENO) >= 0 else { throw LibraryError.logFile }
            setbuf(stdout, nil)
            setbuf(stderr, nil)
            guard chdir(info.engine == .system3 ? game.url.path : resources.path) == 0 else { throw LibraryError.resourcePath }
            setenv("XSYSTEM4_HOME", home.path, 1)
            setenv("XSYSTEM4_CRASH_LOG_PATH", logs.appendingPathComponent("crash.log").path, 1)
            setenv("XSYSTEM35_FONT_GOTHIC", resources.appendingPathComponent("MTLc3m.ttf").path, 1)
            setenv("XSYSTEM35_FONT_MINCHO", resources.appendingPathComponent("mincho.ttf").path, 1)
            let arguments = try Self.launchArguments(game.url, saves: saves)
            ModifierBridge.prepareForLaunch()
            launching = true
            DispatchQueue.main.async {
                Timer.scheduledTimer(withTimeInterval: 0.01, repeats: false) { _ in
                    SDL_SetMainReady()
                    RuntimeGamepad.prepare(for: game.id)
                    var argv = arguments.map { strdup($0) }
                    argv.append(nil)
                    argv.withUnsafeMutableBufferPointer { buffer in
                        if info.engine == .system3 {
                            _ = xsystem35_main(Int32(arguments.count), buffer.baseAddress)
                        } else {
                            _ = xsystem4_main(Int32(arguments.count), buffer.baseAddress)
                        }
                    }
                    for argument in argv { free(argument) }
                    exit(0)
                }
            }
        } catch { self.error = error.localizedDescription }
    }
}
