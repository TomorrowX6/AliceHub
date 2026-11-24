import SwiftUI
import WebKit

struct HubCacheView: View {
    let games: URL
    @Binding var busy: Bool
    @State private var sizes: (routine: Int64, downloads: Int64)?
    @State private var confirm: Choice?
    @State private var result: String?

    private enum Choice: Int, Identifiable {
        case routine, all
        var id: Int { rawValue }
    }

    private var cache: HubCacheFiles {
        HubCacheFiles(caches: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0], games: games)
    }

    var body: some View {
        List {
            if let sizes {
                Section("缓存") {
                    LabeledCacheSize(title: "临时文件", bytes: sizes.routine)
                    LabeledCacheSize(title: "下载缓存", bytes: sizes.downloads)
                }.listRowBackground(HubTheme.surface)
                Section {
                    Button { confirm = .routine } label: {
                        HubActionLabel(title: "清理临时文件", symbol: "trash", destructive: true, navigates: false)
                    }.buttonStyle(.plain)
                    if sizes.downloads > 0 {
                        Button { confirm = .all } label: {
                            HubActionLabel(title: "清理全部缓存", symbol: "trash", destructive: true, navigates: false)
                        }.buttonStyle(.plain)
                    }
                }.listRowBackground(HubTheme.surface)
            } else {
                ProgressView()
            }
        }
        .listStyle(.insetGrouped).modifier(HubListStyle())
        .navigationTitle("清理缓存").navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(busy)
        .disabled(busy)
        .task { await scan() }
        .alert("清理缓存", isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } }), presenting: confirm) { choice in
            Button("取消", role: .cancel) {}
            Button("清理", role: .destructive) { Task { await clear(choice) } }
        } message: { choice in
            Text(hubText(choice == .all ? "未完成的下载将从头开始。游戏和存档不会被删除。" : "保留下载进度、游戏和存档。"))
        }
        .alert("清理缓存", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) {
            Button("好", role: .cancel) {}
        } message: { Text(result ?? "") }
    }

    @MainActor private func scan() async {
        let cache = cache
        sizes = await Task.detached(priority: .utility) {
            let routine = cache.size(of: cache.targets(includeDownloads: false))
            let total = cache.size(of: cache.targets(includeDownloads: true))
            return (routine, max(0, total - routine))
        }.value
    }

    @MainActor private func clear(_ choice: Choice) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        let cache = cache
        result = await Task.detached(priority: .utility) {
            URLCache.shared.removeAllCachedResponses()
            do {
                try cache.clear(includeDownloads: choice == .all)
                return hubText("清理完成")
            } catch { return hubText("部分缓存无法清理") }
        }.value
        GameArtwork.clearCache()
        await scan()
    }
}

private struct LabeledCacheSize: View {
    @Environment(\.locale) private var locale
    let title: LocalizedStringKey
    let bytes: Int64

    var body: some View {
        HStack {
            Text(title)
            Spacer()
            Text(bytes.formatted(.byteCount(style: .file, spellsOutZero: false).locale(locale)))
                .foregroundColor(HubTheme.secondary)
        }
    }
}

struct HubLicensesView: View {
    private let licenses = Bundle.main.resourceURL?
        .appendingPathComponent("licenses", isDirectory: true)

    private var entries: [URL] {
        guard let licenses else { return [] }
        return ((try? FileManager.default.contentsOfDirectory(at: licenses, includingPropertiesForKeys: nil, options: .skipsHiddenFiles)) ?? [])
            .sorted { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }
    }

    var body: some View {
        List {
            if entries.isEmpty { Text("没有许可证") }
            ForEach(entries, id: \.self) { entry in
                NavigationLink {
                    HubToolTextView(title: entry.lastPathComponent, text: (try? String(contentsOf: entry, encoding: .utf8)) ?? hubText("无法读取文件"))
                } label: {
                    Text(entry.lastPathComponent)
                }
            }
        }.listRowBackground(HubTheme.surface)
            .listStyle(.insetGrouped).modifier(HubListStyle())
            .navigationTitle("开源许可证").navigationBarTitleDisplayMode(.inline)
    }
}

struct HubErrorReportView: View {
    var message: String? = nil
    @State private var logs: [Log] = []

    private struct Log: Identifiable {
        let id: String
        let text: String
    }

    var body: some View {
        List {
            if let message {
                Section("错误") {
                    Text(message).textSelection(.enabled)
                    ReportShareButton(text: report(message))
                }.listRowBackground(HubTheme.surface)
            }
            Section("日志") {
                if logs.isEmpty { Text("暂无日志") }
                ForEach(logs) { log in
                    NavigationLink {
                        HubToolTextView(title: log.id, text: log.text, share: report(log.text))
                    } label: {
                        Text(log.id)
                    }
                }
            }.listRowBackground(HubTheme.surface)
        }.listStyle(.insetGrouped).modifier(HubListStyle())
            .navigationTitle("错误报告").navigationBarTitleDisplayMode(.inline)
            .task {
                let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                    .appendingPathComponent("Logs", isDirectory: true)
                logs = await Task.detached(priority: .utility) {
                    ["crash.log", "engine.log"].compactMap { name in
                        HubToolFiles.logTail(directory.appendingPathComponent(name)).map { Log(id: name, text: $0) }
                    }
                }.value
            }
    }

    private func report(_ text: String) -> String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
        return "AliceHub \(version) · iOS \(UIDevice.current.systemVersion)\n\n\(text)"
    }
}

private struct HubToolTextView: View {
    let title: String
    let text: String
    var share: String? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let share { ReportShareButton(text: share) }
                Text(text).font(.system(.body, design: .monospaced)).textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading).modifier(HubCard())
            }.padding(20)
        }.background(HubTheme.background.ignoresSafeArea())
            .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
    }
}

private struct ReportShareButton: View {
    let text: String
    @State private var sharing = false

    var body: some View {
        Button { sharing = true } label: {
            Label("分享报告", systemImage: "square.and.arrow.up")
        }.buttonStyle(.borderedProminent)
            .sheet(isPresented: $sharing) { ReportShareSheet(text: text) }
    }
}

private struct ReportShareSheet: UIViewControllerRepresentable {
    let text: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct HubManualView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var browser: ManualBrowser

    init(manual: HubToolFiles.Manual) {
        _browser = StateObject(wrappedValue: ManualBrowser(manual: manual))
    }

    var body: some View {
        NavigationView {
            ManualWebView(webView: browser.webView)
                .overlay {
                    if browser.failed {
                        Text("无法打开游戏说明书").padding(20).modifier(HubCard()).allowsHitTesting(false)
                    }
                }
                .navigationTitle("游戏说明书").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button { browser.goBack() } label: { Label("上一页", systemImage: "chevron.backward") }
                            .disabled(!browser.canGoBack)
                    }
                    ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
                }
                .task { await browser.load() }
        }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
    }
}

private struct ManualWebView: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ webView: WKWebView, context: Context) {}
}

@MainActor
private final class ManualBrowser: NSObject, ObservableObject, WKNavigationDelegate {
    let webView: WKWebView
    @Published private(set) var canGoBack = false
    @Published private(set) var failed = false
    private let manual: HubToolFiles.Manual
    private var history: NSKeyValueObservation?
    private var loaded = false

    init(manual: HubToolFiles.Manual) {
        self.manual = manual
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        configuration.websiteDataStore = .nonPersistent()
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        history = webView.observe(\.canGoBack, options: .new) { [weak self] _, change in
            guard let value = change.newValue else { return }
            Task { @MainActor in self?.canGoBack = value }
        }
    }

    func load() async {
        guard !loaded else { return }
        do {
            let rules = try await WKContentRuleListStore.default().compileContentRuleList(
                forIdentifier: "AliceHubOfflineManual",
                encodedContentRuleList: HubToolFiles.Manual.resourceRules
            )
            guard !Task.isCancelled, !loaded else { return }
            guard let rules else { failed = true; return }
            webView.configuration.userContentController.add(rules)
            loaded = true
            webView.loadFileURL(manual.index, allowingReadAccessTo: manual.root)
        } catch {
            if !Task.isCancelled { failed = true }
        }
    }

    func goBack() {
        if webView.canGoBack { webView.goBack() }
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard action.targetFrame != nil, let url = action.request.url, manual.canNavigate(to: url) else {
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { failed = true }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        failed = false
    }
}
