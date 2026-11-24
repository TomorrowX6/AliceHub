import SwiftUI
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    private var observers: [NSObjectProtocol] = []

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UITableView.appearance().backgroundColor = UIColor(HubTheme.background)
        UITableViewCell.appearance().backgroundColor = UIColor(HubTheme.surface)
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
            guard let engine = note.object as? UIViewController else { return }
            var host = engine
            while let presented = host.presentedViewController { host = presented }
            weak var weakPanel: UIViewController?
            weak var engineWindow = engine.view.window
            let restoreEngine = {
                // Dismissal can hand key/front status back to the launcher window, hiding the engine and wand.
                guard let window = engineWindow else { return }
                window.makeKeyAndVisible()
                if let wand = window.rootViewController?.view.viewWithTag(0x4D4F44) {
                    wand.superview?.bringSubviewToFront(wand)
                }
            }
            let panel = UIHostingController(rootView: ModifierView(dismiss: {
                weakPanel?.dismiss(animated: true, completion: restoreEngine)
            }, editScale: { [weak engine] gameID, scale in
                weakPanel?.dismiss(animated: false) {
                    restoreEngine()
                    guard let engine else { return }
                    Self.presentScalePreview(on: engine, gameID: gameID, scale: scale, restoreEngine: restoreEngine)
                }
            }))
            weakPanel = panel
            panel.modalPresentationStyle = .formSheet
            panel.isModalInPresentation = true
            host.present(panel, animated: true)
        })
        return true
    }

    private static func presentScalePreview(on engine: UIViewController, gameID: String, scale: GameDisplayScale,
                                            restoreEngine: @escaping () -> Void) {
        weak var weakOverlay: UIViewController?
        let overlay = UIHostingController(rootView: GameDisplayScaleEditor(
            scale: scale, initialPercent: scale.percent(for: gameID), live: true, preview: { value in
                ModifierBridge.applyDisplayScale(for: gameID, preview: (scale, value))
            }, finish: { value in
                if let value { scale.save(value, for: gameID) }
                ModifierBridge.applyDisplayScale(for: gameID)
                weakOverlay?.dismiss(animated: false, completion: restoreEngine)
            }
        ).modifier(HubAppearanceModifier()))
        weakOverlay = overlay
        overlay.view.backgroundColor = .clear
        overlay.view.accessibilityViewIsModal = true
        overlay.modalPresentationStyle = .overFullScreen
        overlay.isModalInPresentation = true
        engine.present(overlay, animated: false)
    }

    func applicationWillTerminate(_ application: UIApplication) { SDL_OnApplicationWillTerminate() }
    func applicationDidReceiveMemoryWarning(_ application: UIApplication) { SDL_OnApplicationDidReceiveMemoryWarning() }
}

@main
struct AliceHubApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    init() {
        GameLibrary.titleResolver = { url, base in
            ModOverlay.displayName(url, baseName: base)
        }
        GameLibrary.cnPatchResolver = { url in
            InstalledModInfo.read(url)?.kind == "cn_patch"
        }
    }

    var body: some Scene {
        WindowGroup { LauncherView().modifier(HubAppearanceModifier()) }
    }
}
