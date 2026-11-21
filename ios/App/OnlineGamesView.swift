import SwiftUI

struct OnlineGamesView: View {
    @ObservedObject var library: GameLibrary
    @ObservedObject var download: OnlineGameDownloadModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var games: [OnlineGame] {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? OnlineGame.all : OnlineGame.all.filter {
            $0.title.localizedCaseInsensitiveContains(text) || $0.id.localizedCaseInsensitiveContains(text)
        }
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    HubSectionHeader(title: "下一段冒险，从这里开始", subtitle: "与 Android 版相同的游戏资源目录，下载后自动加入本机游戏库。", symbol: "arrow.down.circle")
                        .modifier(HubCard())
                    Text("请仅下载你合法拥有的游戏资源。建议使用 Wi-Fi，并保持 App 在前台。")
                        .font(.footnote).foregroundColor(HubTheme.secondary)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 290), spacing: 14)], spacing: 14) {
                        ForEach(games) { game in
                            NavigationLink {
                                OnlineGameDetailView(game: game, library: library, download: download)
                            } label: {
                                HStack(spacing: 14) {
                                    GameArtwork(catalogID: game.artwork)
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(game.title).font(.headline).foregroundColor(HubTheme.text)
                                        Text(status(game)).font(.caption).foregroundColor(HubTheme.secondary)
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                    Image(systemName: "chevron.right").font(.caption.bold()).foregroundColor(HubTheme.secondary)
                                }.modifier(HubCard())
                            }.buttonStyle(.plain)
                        }
                    }
                    if games.isEmpty { Text("没有找到相关游戏").foregroundColor(HubTheme.secondary) }
                }.padding(20).frame(maxWidth: 1000).frame(maxWidth: .infinity)
            }.background(HubTheme.background.ignoresSafeArea())
                .searchable(text: $query, prompt: "搜索在线游戏")
                .disableAutocorrection(true)
                .navigationTitle("在线下载").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() }.disabled(download.running) }
                }
        }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
            .interactiveDismissDisabled(download.running)
    }

    private func status(_ game: OnlineGame) -> String {
        if download.running && download.activeID == game.id { return "正在下载 · 点击查看进度" }
        return library.games.contains { $0.name.caseInsensitiveCompare(game.directory) == .orderedSame } ? "已在游戏库" : "查看大小与下载详情"
    }
}

private struct OnlineGameDetailView: View {
    let game: OnlineGame
    @ObservedObject var library: GameLibrary
    @ObservedObject var download: OnlineGameDownloadModel
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
                        Text(game.title).font(.title2.bold()).foregroundColor(HubTheme.text)
                        HubBadge(title: installed ? "已在游戏库" : "在线游戏", success: installed)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }.modifier(HubCard())
                if loading {
                    HStack { ProgressView(); Text("正在获取游戏清单…") }.frame(maxWidth: .infinity).modifier(HubCard())
                }
                if let failure {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("无法获取游戏信息", systemImage: "wifi.exclamationmark").font(.headline)
                        Text(failure).font(.footnote).foregroundColor(HubTheme.secondary)
                        Button("重试") { Task { await load() } }
                    }.modifier(HubCard())
                }
                if let manifest {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("下载信息", systemImage: "info.circle").font(.headline)
                        LabeledContentRow(title: "下载大小", value: ModCatalog.formatSize(manifest.totalBytes))
                        LabeledContentRow(title: "文件数量", value: "\(manifest.files.count)")
                        Text("每个文件均验证大小与 SHA-256，全部完成后才会加入游戏库；不会覆盖已有游戏或存档。")
                            .font(.footnote).foregroundColor(HubTheme.secondary)
                    }.modifier(HubCard())
                    if selected && download.running {
                        VStack(alignment: .leading, spacing: 12) {
                            Label("正在下载", systemImage: "arrow.down.circle").font(.headline)
                            ProgressView(value: Double(download.completed), total: Double(max(1, download.total)))
                            Text("\(ModCatalog.formatSize(download.completed)) / \(ModCatalog.formatSize(download.total))")
                                .font(.subheadline.monospacedDigit())
                            Text(download.progressText).font(.footnote).foregroundColor(HubTheme.secondary)
                            Button("取消下载", role: .destructive) { confirmCancel = true }
                        }.modifier(HubCard())
                    } else if installed {
                        Label("已在游戏库，可以返回主页开始游戏", systemImage: "checkmark.circle.fill")
                            .font(.subheadline).foregroundColor(HubTheme.primary).modifier(HubCard())
                    } else {
                        if selected, let error = download.failure {
                            Text(error).font(.footnote).foregroundColor(.orange).modifier(HubCard())
                        }
                        Button { confirmDownload = true } label: {
                            Label(selected && download.failure != nil ? "继续下载" : "下载游戏", systemImage: "arrow.down.circle")
                        }.buttonStyle(HubPrimaryButton()).disabled(download.running || library.importing || library.launching)
                        if download.running { Text("请先完成或取消当前下载。 ").font(.footnote).foregroundColor(HubTheme.secondary) }
                    }
                }
                Text("取消或中断后可继续下载，已校验的内容会保留。下载时请保持 App 在前台；切到后台会自动暂停。")
                    .font(.footnote).foregroundColor(HubTheme.secondary)
            }.padding(20).frame(maxWidth: 700).frame(maxWidth: .infinity)
        }.background(HubTheme.background.ignoresSafeArea())
            .navigationTitle("游戏详情").navigationBarTitleDisplayMode(.inline)
            .task { await load() }
            .alert("开始下载游戏？", isPresented: $confirmDownload) {
                Button("取消", role: .cancel) {}
                Button("下载") { if let manifest { download.start(game, manifest: manifest, library: library) } }
            } message: {
                Text("请确认你合法拥有此游戏。下载可能消耗较多流量，请预留足够空间并使用 Wi-Fi。")
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
    let title: String
    let value: String
    var body: some View {
        HStack { Text(title).foregroundColor(HubTheme.secondary); Spacer(); Text(value).foregroundColor(HubTheme.text) }
            .font(.subheadline)
    }
}
