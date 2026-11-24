import SwiftUI
import UIKit

@_cdecl("alicehub_ios_localize_modifier_button")
@MainActor
func alicehub_ios_localize_modifier_button(_ opaqueButton: UnsafeMutableRawPointer) {
    Unmanaged<UIButton>.fromOpaque(opaqueButton).takeUnretainedValue().accessibilityLabel = hubText("修改器")
}

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
        observers.append(NotificationCenter.default.addObserver(forName: Notification.Name("AliceHubOpenManual"), object: nil, queue: .main) { note in
            guard let host = note.object as? UIViewController, host.presentedViewController == nil,
                  let path = note.userInfo?["path"] as? String else { return }
            let game = URL(fileURLWithPath: path).deletingLastPathComponent().deletingLastPathComponent()
            guard let manual = HubToolFiles.manual(in: game) else { return }
            let panel = UIHostingController(rootView: HubManualView(manual: manual).environment(\.locale, HubLanguage.current.locale))
            panel.modalPresentationStyle = .formSheet
            host.present(panel, animated: true)
        })
        observers.append(NotificationCenter.default.addObserver(forName: Notification.Name("AliceHubOpenModifier"), object: nil, queue: .main) { note in
            guard var host = note.object as? UIViewController else { return }
            while let presented = host.presentedViewController { host = presented }
            weak var weakPanel: UIViewController?
            weak var engineWindow = (note.object as? UIViewController)?.view.window
            let panel = UIHostingController(rootView: ModifierView {
                weakPanel?.dismiss(animated: true) {
                    // Dismissal can hand key/front status back to the launcher window, hiding the engine and wand.
                    guard let window = engineWindow else { return }
                    window.makeKeyAndVisible()
                    if let wand = window.rootViewController?.view.viewWithTag(0x4D4F44) {
                        wand.superview?.bringSubviewToFront(wand)
                    }
                }
            }.environment(\.locale, HubLanguage.current.locale))
            weakPanel = panel
            panel.modalPresentationStyle = .formSheet
            panel.isModalInPresentation = true
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
    @AppStorage(HubLanguage.storageKey) private var language = HubLanguage.system.rawValue

    init() {
        GameLibrary.titleResolver = { url, base in
            ModOverlay.displayName(url, baseName: base)
        }
        GameLibrary.cnPatchResolver = { url in
            InstalledModInfo.read(url)?.kind == "cn_patch"
        }
    }

    var body: some Scene {
        WindowGroup {
            LauncherView()
                .environment(\.locale, (HubLanguage(rawValue: language) ?? .system).locale)
                .modifier(HubAppearanceModifier())
        }
    }
}
