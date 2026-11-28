import SwiftUI

struct OnlineGameDetailView: View {
    let game: OnlineGame
    @ObservedObject var library: GameLibrary
    @ObservedObject var download: OnlineGameDownloadModel
    @HubLocalizedText private var localized
    @AppStorage(OnlineInstall.storageKey) private var onlineInstall = false
    @State private var manifest: OnlineGameManifest?
    @State private var loading = false
    @State private var failure: String?
    @State private var confirmDownload = false
    @State private var confirmCancel = false

    private var selected: Bool { download.activeID == game.id }
    private var installed: Bool {
        let name = manifest?.baseName ?? game.directory
        return FileManager.default.fileExists(atPath: library.gamesDirectory.appendingPathComponent(name).path)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(spacing: 18) {
                    GameArtwork(catalogID: game.artwork, size: 88)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(game.displayTitle(in: localized.language)).font(.title2.bold()).foregroundColor(HubTheme.text)
                            .fixedSize(horizontal: false, vertical: true)
                        HStack(spacing: 6) {
                            HubBadge(title: installed ? "已安装" : "未安装", success: installed)
                            HubBadge(
                                title: OnlineGame.chineseBadgeKey(hasChinese: game.hasChinese),
                                tone: game.hasChinese ? .primary : .warning
                            )
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }.modifier(HubCard())
                if selected && download.running {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("正在下载", systemImage: "arrow.down.circle").font(.headline)
                        ProgressView(value: Double(download.completed), total: Double(max(1, download.total)))
                        Text("\(ModCatalog.formatSize(download.completed)) / \(ModCatalog.formatSize(download.total))")
                            .font(.subheadline.monospacedDigit())
                        Text(download.progressText).font(.footnote).foregroundColor(HubTheme.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("取消下载", role: .destructive) { confirmCancel = true }
                    }.modifier(HubCard())
                }
                if loading && onlineInstall && !(selected && download.running) {
                    HStack { ProgressView(); Text("正在获取游戏清单…") }.frame(maxWidth: .infinity).modifier(HubCard())
                }
                if let failure, onlineInstall && !(selected && download.running) {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("无法获取游戏信息", systemImage: "wifi.exclamationmark").font(.headline)
                        Text(failure).font(.footnote).foregroundColor(HubTheme.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("重试") { Task { await load() } }
                    }.modifier(HubCard())
                }
                if let manifest, onlineInstall {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("下载信息", systemImage: "info.circle").font(.headline)
                        LabeledContentRow(title: "大小", value: ModCatalog.formatSize(manifest.totalBytes))
                        LabeledContentRow(title: "文件", value: "\(manifest.files.count)")
                    }.modifier(HubCard())
                    if !(selected && download.running) && !installed {
                        if selected, let error = download.failure {
                            Text(error).font(.footnote).foregroundColor(.orange)
                                .fixedSize(horizontal: false, vertical: true).modifier(HubCard())
                        }
                        Button { confirmDownload = true } label: {
                            Label(selected && download.failure != nil ? "继续下载" : "下载游戏", systemImage: "arrow.down.circle")
                        }.buttonStyle(HubPrimaryButton()).disabled(download.running || library.importing || library.launching)
                        if download.running { Text("另有下载进行中").font(.subheadline).foregroundColor(HubTheme.secondary) }
                    }
                }
            }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
        }.background(HubTheme.background.ignoresSafeArea())
            .navigationTitle("游戏详情").navigationBarTitleDisplayMode(.inline)
            .task(id: onlineInstall) { if onlineInstall { await load() } }
            .onChange(of: download.running) { running in
                if !running { confirmCancel = false }
            }
            .alert("开始下载游戏？", isPresented: $confirmDownload) {
                Button("取消", role: .cancel) {}
                Button("下载") { if let manifest { download.start(game, manifest: manifest, library: library) } }
            } message: {
                Text("请确认合法拥有此游戏。下载消耗流量，请使用 Wi-Fi 并预留空间。")
            }
            .alert("取消当前下载？", isPresented: $confirmCancel) {
                Button("继续下载", role: .cancel) {}
                Button("取消下载", role: .destructive) { download.cancel() }
            } message: { Text("已下载内容会保留，下次可继续下载。") }
    }

    @MainActor private func load() async {
        guard manifest == nil && !loading else { return }
        loading = true; failure = nil
        defer { loading = false }
        do { manifest = try await OnlineGameManifest.fetch(from: game.baseURL) }
        catch { if !Task.isCancelled { failure = error.localizedDescription } }
    }
}

private struct LabeledContentRow: View {
    let title: LocalizedStringKey
    let value: String
    var body: some View {
        HStack { Text(title).foregroundColor(HubTheme.secondary); Spacer(); Text(value).foregroundColor(HubTheme.text) }
            .font(.subheadline)
    }
}
