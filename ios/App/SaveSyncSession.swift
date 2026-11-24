import UIKit

/// Syncs the running game's saves with the sync folder at launch, when the app
/// moves to the background (after the engine flushed its saves on deactivate), and
/// when the process exits. Both engines quit through exit(), so the exit sync is an
/// atexit handler registered before the engine adds its own save-on-exit handlers.
/// Failures are logged and never block play.
enum SaveSyncSession {
    private static var game: String?
    private static var observer: NSObjectProtocol?
    private static var registered = false

    static func begin(game: String) {
        guard SaveSyncFolder.isConfigured else { return }
        run(game, "launch")
        Self.game = game
        if !registered {
            registered = true
            atexit { SaveSyncSession.end() }
        }
        observer = NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in
            var task = UIBackgroundTaskIdentifier.invalid
            task = UIApplication.shared.beginBackgroundTask {
                UIApplication.shared.endBackgroundTask(task)
                task = .invalid
            }
            run(game, "background")
            if task != .invalid { UIApplication.shared.endBackgroundTask(task) }
        }
    }

    static func end() {
        if let observer { NotificationCenter.default.removeObserver(observer) }
        observer = nil
        guard let game else { return }
        Self.game = nil
        run(game, "exit")
    }

    private static func run(_ game: String, _ reason: String) {
        do {
            if let summary = try SaveSyncFolder.sync(game: game) {
                print("Save sync (\(reason)): pulled \(summary.pulled), pushed \(summary.pushed), conflicts \(summary.conflicts)")
            }
        } catch {
            print("Save sync (\(reason)) failed: \(error.localizedDescription)")
        }
    }
}
