import SwiftUI

struct OnlineGamesView: View {
    @ObservedObject var library: GameLibrary
    @ObservedObject var download: OnlineGameDownloadModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var games: [OnlineGame] {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? OnlineGame.all : OnlineGame.all.filter {
            let translationTag = $0.hasChinese ? "熟肉" : "生肉"
            return $0.title.localizedCaseInsensitiveContains(text) ||
                $0.id.localizedCaseInsensitiveContains(text) ||
                translationTag.localizedCaseInsensitiveContains(text)
        }
    }

    var body: some View {
        let installedNames = Set(library.games.map { $0.name.lowercased() })
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 14)], spacing: 14) {
                        ForEach(games) { game in
                            NavigationLink {
                                OnlineGameDetailView(game: game, library: library, download: download)
                            } label: {
                                HStack(spacing: 14) {
                                    GameArtwork(catalogID: game.artwork)
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(game.title).font(.headline).foregroundColor(HubTheme.text)
                                            .fixedSize(horizontal: false, vertical: true)
                                        HStack(spacing: 6) {
                                            if let status = status(game, installedNames: installedNames) {
                                                HubBadge(title: status, success: status == "已安装")
                                            }
                                            HubBadge(
                                                title: game.hasChinese ? "熟肉" : "生肉",
                                                tone: game.hasChinese ? .primary : .warning
                                            )
                                        }
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

    private func status(_ game: OnlineGame, installedNames: Set<String>) -> String? {
        if download.running && download.activeID == game.id { return "下载中" }
        return installedNames.contains(game.directory.lowercased()) ? "已安装" : nil
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
                            .fixedSize(horizontal: false, vertical: true)
                        HStack(spacing: 6) {
                            HubBadge(title: installed ? "已安装" : "在线游戏", success: installed)
                            HubBadge(
                                title: game.hasChinese ? "熟肉" : "生肉",
                                tone: game.hasChinese ? .primary : .warning
                            )
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }.modifier(HubCard())
                if loading {
                    HStack { ProgressView(); Text("正在获取游戏清单…") }.frame(maxWidth: .infinity).modifier(HubCard())
                }
                if let failure {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("无法获取游戏信息", systemImage: "wifi.exclamationmark").font(.headline)
                        Text(failure).font(.footnote).foregroundColor(HubTheme.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("重试") { Task { await load() } }
                    }.modifier(HubCard())
                }
                if let manifest {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("下载信息", systemImage: "info.circle").font(.headline)
                        LabeledContentRow(title: "大小", value: ModCatalog.formatSize(manifest.totalBytes))
                        LabeledContentRow(title: "文件", value: "\(manifest.files.count)")
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
                    } else if !installed {
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
            .task { await load() }
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
    let title: String
    let value: String
    var body: some View {
        HStack { Text(title).foregroundColor(HubTheme.secondary); Spacer(); Text(value).foregroundColor(HubTheme.text) }
            .font(.subheadline)
    }
}
