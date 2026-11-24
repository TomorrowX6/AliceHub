import Foundation
#if os(iOS)
import BackgroundTasks
import UIKit
import UserNotifications
#endif

/// Keeps a user-started download or install running after the app leaves the foreground.
/// iOS 26+ uses a continued processing task (system progress UI, also on the lock screen);
/// older systems get the usual short background time and pause when it runs out.
final class BackgroundActivity {
    enum Outcome { case succeeded, failed(String), paused }

    static let taskPrefix = "io.github.alicehub.download."

    private let title: String
    private let onExpire: () -> Void
    private let lock = NSLock()
    private var ended = false
    private var didExpire = false
    private var resumable = false
    private var fraction: Double?
    private var detail = ""
    private var lastPush = Date.distantPast
#if os(iOS)
    private var legacyTask = UIBackgroundTaskIdentifier.invalid
    private var continuedTask: AnyObject?
#endif

    /// Set once the system took the time back; the caller's cancellation then means "paused".
    var expired: Bool { lock.lock(); defer { lock.unlock() }; return didExpire }
    /// Only the short legacy grace period ending may auto-resume; a continued task can also end because the user cancelled it.
    var autoResumes: Bool { lock.lock(); defer { lock.unlock() }; return didExpire && resumable }

    static var appActive: Bool {
#if os(iOS)
        UIApplication.shared.applicationState == .active
#else
        true
#endif
    }

    init(title: String, onExpire: @escaping () -> Void) {
        self.title = title
        self.onExpire = onExpire
#if os(iOS)
        if Thread.isMainThread { begin() } else { DispatchQueue.main.async { self.begin() } }
#endif
    }

    func update(fraction: Double?, detail: String) {
        lock.lock()
        if let fraction { self.fraction = min(1, max(0, fraction)) }
        self.detail = detail.split(separator: "\n").first.map(String.init) ?? ""
        let now = Date()
        let push = !ended && now.timeIntervalSince(lastPush) >= 0.5
        if push { lastPush = now }
        lock.unlock()
#if os(iOS)
        if push { pushProgress() }
#endif
    }

    func end(_ outcome: Outcome) {
        lock.lock()
        guard !ended else { lock.unlock(); return }
        ended = true
        lock.unlock()
#if os(iOS)
        let finish = { self.finish(outcome) }
        if Thread.isMainThread { finish() } else { DispatchQueue.main.async(execute: finish) }
#endif
    }

    private func expire(resumable: Bool) {
        lock.lock()
        guard !ended && !didExpire else { lock.unlock(); return }
        didExpire = true
        self.resumable = resumable
        lock.unlock()
        onExpire()
    }

#if os(iOS)
    private func begin() {
        legacyTask = UIApplication.shared.beginBackgroundTask(withName: title) { [weak self] in
            guard let self else { return }
            if self.continuedTask == nil { self.expire(resumable: true) }
            self.endLegacyTask()
        }
#if compiler(>=6.2)
        if #available(iOS 26.0, *) { submitContinuedTask() }
#endif
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

#if compiler(>=6.2)
    @available(iOS 26.0, *)
    private func submitContinuedTask() {
        let identifier = Self.taskPrefix + UUID().uuidString
        let registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { [weak self] task in
            guard let task = task as? BGContinuedProcessingTask, let self else {
                task.setTaskCompleted(success: false)
                return
            }
            self.attach(task)
        }
        guard registered else { return }
        let request = BGContinuedProcessingTaskRequest(identifier: identifier, title: title, subtitle: hubText("正在准备…"))
        request.strategy = .fail
        try? BGTaskScheduler.shared.submit(request)
    }

    @available(iOS 26.0, *)
    private func attach(_ task: BGContinuedProcessingTask) {
        lock.lock()
        let finished = ended
        lock.unlock()
        if finished {
            task.setTaskCompleted(success: !expired)
            return
        }
        task.progress.totalUnitCount = 1000
        task.expirationHandler = { [weak self] in self?.expire(resumable: false) }
        continuedTask = task
        pushProgress()
    }
#endif

    private func pushProgress() {
#if compiler(>=6.2)
        guard #available(iOS 26.0, *) else { return }
        let apply = {
            guard let task = self.continuedTask as? BGContinuedProcessingTask else { return }
            self.lock.lock()
            let fraction = self.fraction, detail = self.detail
            self.lock.unlock()
            if let fraction { task.progress.completedUnitCount = Int64(fraction * 1000) }
            task.updateTitle(self.title, subtitle: detail)
        }
        if Thread.isMainThread { apply() } else { DispatchQueue.main.async(execute: apply) }
#endif
    }

    private func finish(_ outcome: Outcome) {
#if compiler(>=6.2)
        if #available(iOS 26.0, *), let task = continuedTask as? BGContinuedProcessingTask {
            if case .succeeded = outcome { task.progress.completedUnitCount = task.progress.totalUnitCount }
            var success = false
            if case .succeeded = outcome { success = true }
            task.setTaskCompleted(success: success)
        }
#endif
        continuedTask = nil
        if UIApplication.shared.applicationState != .active { notify(outcome) }
        endLegacyTask()
    }

    private func endLegacyTask() {
        guard legacyTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(legacyTask)
        legacyTask = .invalid
    }

    private func notify(_ outcome: Outcome) {
        let content = UNMutableNotificationContent()
        switch outcome {
        case .succeeded:
            content.title = hubText("下载完成")
            content.body = title
        case .failed(let message):
            content.title = hubText("下载失败")
            content.body = title + "\n" + message
        case .paused:
            content.title = hubText("下载已暂停")
            content.body = hubText("%@\n回到 AliceHub 继续下载", title)
        }
        content.sound = .default
        let request = UNNotificationRequest(identifier: Self.taskPrefix + "result", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
#endif
}
