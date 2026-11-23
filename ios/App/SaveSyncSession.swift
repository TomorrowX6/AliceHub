import UIKit

/// Syncs the running game's saves with the sync folder at launch, when the app
/// moves to the background (after the engine flushed its saves on deactivate), and
/// when the game exits. Failures are logged and never block play.
enum SaveSyncSession {
    private static var observer: NSObjectProtocol?

    static func begin(game: String) {
        guard SaveSyncFolder.isConfigured else { return }
        run(game, "launch")
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

    static func end(game: String) {
        if let observer { NotificationCenter.default.removeObserver(observer) }
        observer = nil
        guard SaveSyncFolder.isConfigured else { return }
        run(game, "exit")
    }

    private static func run(_ game: String, _ reason: String) {
        do {
            if let summary = try SaveSyncFolder.sync(game: game) {
                print("Save sync (\(reason)): pulled \(summary.pulled), pushed \(summary.pushed)")
            }
        } catch {
            print("Save sync (\(reason)) failed: \(error.localizedDescription)")
        }
    }
}
