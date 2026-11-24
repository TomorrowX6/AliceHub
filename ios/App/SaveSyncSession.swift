import UIKit

/// Syncs the running game's saves with the sync folder at launch, when the app
/// moves to the background and when the process exits. Only launch may pull.
/// Failures are logged and never block play.
enum SaveSyncSession {
    private static var game: String?
    private static var observer: NSObjectProtocol?
    private static var registered = false

    @MainActor
    private final class BackgroundTask {
        private var id = UIBackgroundTaskIdentifier.invalid

        init() {
            id = UIApplication.shared.beginBackgroundTask { [weak self] in self?.end() }
        }

        func end() {
            guard id != .invalid else { return }
            UIApplication.shared.endBackgroundTask(id)
            id = .invalid
        }
    }

    static func begin(game: String, ready: @escaping () -> Void) {
        SaveSyncFolder.enqueue {
            run(game, "launch", mode: .twoWay)
            DispatchQueue.main.async {
                observe(game)
                ready()
            }
        }
    }

    @MainActor
    private static func observe(_ game: String) {
        guard SaveSyncFolder.isConfigured else { return }
        Self.game = game
        if !registered {
            registered = true
            atexit { SaveSyncSession.end() }
        }
        observer = NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in
            DispatchQueue.main.async {
                let task = BackgroundTask()
                SaveSyncFolder.enqueue {
                    run(game, "background", mode: .uploadOnly)
                    DispatchQueue.main.async { task.end() }
                }
            }
        }
    }

    private static func end() {
        if let observer { NotificationCenter.default.removeObserver(observer) }
        observer = nil
        guard let game else { return }
        Self.game = nil
        SaveSyncFolder.finish {
            run(game, "exit", mode: .uploadOnly)
        }
    }

    private static func run(_ game: String, _ reason: String, mode: SaveSync.Mode) {
        do {
            if let summary = try SaveSyncFolder.sync(game: game, mode: mode) {
                print("Save sync (\(reason)): pulled \(summary.pulled), pushed \(summary.pushed), conflicts \(summary.conflicts)")
            }
        } catch {
            print("Save sync (\(reason)) failed: \(error.localizedDescription)")
        }
    }
}
