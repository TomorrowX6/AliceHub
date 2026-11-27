import UIKit

/// Syncs the running game's saves with the sync folder at launch, when the app
/// moves to the background and when the game ends. Only launch may pull.
/// Failures are logged and never block play.
enum SaveSyncSession {
    @MainActor private static var game: String?
    @MainActor private static var observers: [NSObjectProtocol] = []
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

    private final class BackgroundTask: @unchecked Sendable {
        private let lock = NSLock()
        private var id = UIBackgroundTaskIdentifier.invalid
        let cancellation = Cancellation()

        @MainActor
        init() {
            let cancellation = self.cancellation
            let taskID = UIApplication.shared.beginBackgroundTask { [weak self] in
                cancellation.cancel()
                if Thread.isMainThread {
                    MainActor.assumeIsolated { self?.end() }
                } else {
                    DispatchQueue.main.async {
                        MainActor.assumeIsolated { self?.end() }
                    }
                }
            }
            lock.lock()
            id = taskID
            lock.unlock()
        }

        @MainActor
        func cancel() {
            cancellation.cancel()
            end()
        }

        @MainActor
        func end() {
            lock.lock()
            let current = id
            id = .invalid
            lock.unlock()
            guard current != .invalid else { return }
            UIApplication.shared.endBackgroundTask(current)
        }
    }

    static func begin(game: String, ready: @escaping () -> Void) {
        SaveSyncFolder.enqueue {
            run(game, "launch", mode: .twoWay)
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    observe(game)
                }
                ready()
            }
        }
    }

    @MainActor
    private static func removeObservers() {
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
        observers.removeAll()
    }

    @MainActor
    private static func observe(_ game: String) {
        removeObservers()
        guard SaveSyncFolder.isConfigured else { return }
        Self.game = game
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
                        MainActor.assumeIsolated {
                            task.end()
                            if backgroundTask === task { backgroundTask = nil }
                        }
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

    @MainActor
    static func end() {
        removeObservers()
        backgroundTask?.cancel()
        backgroundTask = nil
        guard let game else { return }
        Self.game = nil
        let task = BackgroundTask()
        backgroundTask = task
        let cancellation = task.cancellation
        SaveSyncFolder.enqueue {
            run(game, "exit", mode: .uploadOnly, isCancelled: { cancellation.isCancelled })
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    task.end()
                    if backgroundTask === task { backgroundTask = nil }
                }
            }
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
