import UIKit

/// Syncs the running game's saves with the sync folder at launch, when the app
/// moves to the background and when the process exits. Only launch may pull.
/// Failures are logged and never block play.
enum SaveSyncSession {
    private static var game: String?
    private static var observers: [NSObjectProtocol] = []
    private static var registered = false
    @MainActor private static var backgroundTask: BackgroundTask?

    private final class Cancellation: @unchecked Sendable {
        private let lock = NSLock()
        private var cancelled = false

        var isCancelled: Bool {
            lock.lock()
            defer { lock.unlock() }
            return cancelled
        }

        func cancel() {
            lock.lock()
            cancelled = true
            lock.unlock()
        }
    }

    @MainActor
    private final class BackgroundTask {
        private var id = UIBackgroundTaskIdentifier.invalid
        let cancellation = Cancellation()

        init() {
            id = UIApplication.shared.beginBackgroundTask { [weak self] in self?.cancel() }
        }

        func cancel() {
            cancellation.cancel()
            end()
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
        let notifications = NotificationCenter.default
        observers.append(notifications.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated {
                backgroundTask?.cancel()
                let task = BackgroundTask()
                backgroundTask = task
                let cancellation = task.cancellation
                SaveSyncFolder.enqueue {
                    run(game, "background", mode: .uploadOnly, isCancelled: { cancellation.isCancelled })
                    DispatchQueue.main.async {
                        task.end()
                        if backgroundTask === task { backgroundTask = nil }
                    }
                }
            }
        })
        for name in [UIApplication.willEnterForegroundNotification, UIApplication.didBecomeActiveNotification] {
            observers.append(notifications.addObserver(forName: name, object: nil, queue: .main) { _ in
                MainActor.assumeIsolated {
                    backgroundTask?.cancel()
                    backgroundTask = nil
                }
            })
        }
    }

    private static func end() {
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
        observers.removeAll()
        guard let game else { return }
        Self.game = nil
        SaveSyncFolder.finish {
            run(game, "exit", mode: .uploadOnly)
        }
    }

    private static func run(_ game: String, _ reason: String, mode: SaveSync.Mode, isCancelled: () -> Bool = { false }) {
        do {
            if let summary = try SaveSyncFolder.sync(game: game, mode: mode, isCancelled: isCancelled) {
                print("Save sync (\(reason)): pulled \(summary.pulled), pushed \(summary.pushed), conflicts \(summary.conflicts), skipped \(summary.skipped)")
            }
        } catch {
            print("Save sync (\(reason)) failed: \(error.localizedDescription)")
        }
    }
}
