import SwiftUI
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    private var observers: [NSObjectProtocol] = []

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let events: [(Notification.Name, () -> Void)] = [
            (UIScene.willDeactivateNotification, { SDL_OnApplicationWillResignActive() }),
            (UIScene.didEnterBackgroundNotification, { SDL_OnApplicationDidEnterBackground() }),
            (UIScene.willEnterForegroundNotification, { SDL_OnApplicationWillEnterForeground() }),
            (UIScene.didActivateNotification, { SDL_OnApplicationDidBecomeActive() })
        ]
        observers = events.map { name, callback in
            NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { _ in callback() }
        }
        observers.append(NotificationCenter.default.addObserver(forName: Notification.Name("AliceHubOpenModifier"), object: nil, queue: .main) { note in
            guard var host = note.object as? UIViewController else { return }
            while let presented = host.presentedViewController { host = presented }
            weak var weakPanel: UIViewController?
            let panel = UIHostingController(rootView: ModifierView { weakPanel?.dismiss(animated: true) })
            weakPanel = panel
            panel.modalPresentationStyle = .formSheet
            host.present(panel, animated: true)
        })
        return true
    }

    func applicationWillTerminate(_ application: UIApplication) { SDL_OnApplicationWillTerminate() }
    func applicationDidReceiveMemoryWarning(_ application: UIApplication) { SDL_OnApplicationDidReceiveMemoryWarning() }
}

@main
struct AliceHubApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup { LauncherView() }
    }
}
