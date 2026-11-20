import Foundation
import Combine
import Darwin

struct Game: Identifiable {
    let url: URL
    var id: String { url.lastPathComponent }
    var name: String { url.lastPathComponent }
}

enum LibraryError: LocalizedError {
    case notGame, alreadyExists, resourcePath, logFile
    var errorDescription: String? {
        switch self {
        case .notGame: return "所选文件夹不是 System 4 游戏。请确认根目录包含 AliceStart.ini、System40.ini 或 .ain 文件。"
        case .alreadyExists: return "已存在同名游戏文件夹，请先删除或重命名后再导入。"
        case .resourcePath: return "无法找到游戏引擎资源。"
        case .logFile: return "无法创建运行日志。"
        }
    }
}

final class GameLibrary: ObservableObject {
    @Published var games: [Game] = []
    @Published var importing = false
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
        guard (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true,
              let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey]) else { throw LibraryError.notGame }
        let regularFiles = contents.filter {
            (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        }
        if regularFiles.contains(where: { ["alicestart.ini", "system40.ini"].contains($0.lastPathComponent.lowercased()) }) {
            return url
        }
        guard let ain = regularFiles.filter({ $0.pathExtension.lowercased() == "ain" })
            .sorted(by: { $0.lastPathComponent < $1.lastPathComponent }).first else { throw LibraryError.notGame }
        return ain
    }

    func refresh() {
        do {
            try files.createDirectory(at: gamesDirectory, withIntermediateDirectories: true)
            games = try files.contentsOfDirectory(at: gamesDirectory, includingPropertiesForKeys: [.isDirectoryKey], options: .skipsHiddenFiles)
                .filter(Self.isGame).map { Game(url: $0) }.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        } catch { self.error = error.localizedDescription }
    }

    func importFolder(_ source: URL) {
        importing = true
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

    func launch(_ game: Game) {
        guard !launching else { return }
        do {
            let target = try Self.launchTarget(game.url)
            guard let resources = Bundle.main.resourceURL else { throw LibraryError.resourcePath }
            let home = files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("xsystem4")
            let saves = documents.appendingPathComponent("Saves").appendingPathComponent(game.name)
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
            guard chdir(resources.path) == 0 else { throw LibraryError.resourcePath }
            setenv("XSYSTEM4_HOME", home.path, 1)
            setenv("XSYSTEM4_CRASH_LOG_PATH", logs.appendingPathComponent("crash.log").path, 1)
            let arguments = ["xsystem4", "--save-folder", saves.path, "--save-format=rsm", target.path]
            launching = true
            DispatchQueue.main.async {
                Timer.scheduledTimer(withTimeInterval: 0.01, repeats: false) { _ in
                    SDL_SetMainReady()
                    var argv = arguments.map { strdup($0) }
                    argv.append(nil)
                    argv.withUnsafeMutableBufferPointer { buffer in
                        _ = xsystem4_main(Int32(arguments.count), buffer.baseAddress)
                    }
                    for argument in argv { free(argument) }
                    exit(0)
                }
            }
        } catch { self.error = error.localizedDescription }
    }
}
