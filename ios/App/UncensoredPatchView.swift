import SwiftUI

struct UncensoredPatchView: View {
    let game: Game
    let descriptor: UncensoredGameDescriptor
    @ObservedObject var library: GameLibrary
    @StateObject private var installer = UncensoredPatchInstaller()
    @State private var manifest: UncensoredPatchManifest?
    @State private var loading = false
    @State private var loadError: String?
    @State private var installed = false
    @State private var installedInfo: InstalledUncensoredInfo?
    @State private var confirmUninstall = false
    @State private var notice: String?
    @State private var alertError: String?
    @Environment(\.dismiss) private var dismiss

    private var statusSubtitle: String {
        if installed {
            if let info = installedInfo {
                let parts = [
                    hubText("已安装"),
                    info.cgCount > 0 ? hubText("%lld 张 CG", info.cgCount) : "",
                    info.size > 0 ? ModCatalog.formatSize(info.size) : ""
                ].filter { !$0.isEmpty }
                return parts.joined(separator: " · ")
            }
            return hubText("已安装")
        }
        return hubText("未安装")
    }

    var body: some View {
        NavigationView {
            List {
                Section {
                    HubSectionHeader(title: descriptor.title, subtitle: statusSubtitle, symbol: "sparkles")
                    if let manifest {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(manifest.summary.isEmpty ? hubText("独立增量无码资源包，不修改游戏本体与存档。") : manifest.summary)
                                .font(.footnote)
                                .foregroundColor(HubTheme.secondary)
                            Text([ModCatalog.formatSize(manifest.size),
                                  manifest.cgCount > 0 ? hubText("%lld 张 CG", manifest.cgCount) : "",
                                  manifest.updatedAt].filter { !$0.isEmpty }.joined(separator: " · "))
                                .font(.caption)
                                .foregroundColor(HubTheme.secondary)
                        }
                        .padding(.vertical, 4)

                        Button {
                            notice = nil
                            installer.install(manifest, to: game.url)
                        } label: {
                            Label(
                                installed ? hubText("重新安装 · %@", ModCatalog.formatSize(manifest.size)) : hubText("下载并安装 · %@", ModCatalog.formatSize(manifest.size)),
                                systemImage: "arrow.down.circle"
                            ).frame(maxWidth: .infinity)
                        }
                        .buttonStyle(HubPrimaryButton())
                        .disabled(installer.isRunning)
                    } else if loading {
                        HStack(spacing: 12) {
                            ProgressView()
                            Text("正在获取无码补丁信息…").foregroundColor(.secondary)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(loadError ?? hubText("无法获取无码补丁信息")).foregroundColor(.secondary)
                            Button("重试") { Task { await loadManifest() } }
                        }
                    }

                    if installed {
                        Button("卸载无码补丁", role: .destructive) { confirmUninstall = true }
                            .disabled(installer.isRunning)
                    }
                }.listRowBackground(HubTheme.surface)

                if let notice {
                    Section {
                        Label(notice, systemImage: "checkmark.circle").foregroundColor(.green)
                    }.listRowBackground(HubTheme.surface)
                }

                Section {
                    Text("无码补丁以独立资源包方式加载，不会修改游戏本体、MOD 或存档，可随时一键卸载还原。")
                        .font(.footnote)
                        .foregroundColor(HubTheme.secondary)
                }.listRowBackground(HubTheme.surface)
            }
            .listStyle(.insetGrouped).modifier(HubListStyle())
            .disabled(installer.isRunning)
            .navigationTitle("无码补丁")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("完成") { dismiss() }.disabled(installer.isRunning)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { Task { await loadManifest() } } label: { Image(systemName: "arrow.clockwise") }
                        .disabled(installer.isRunning || loading)
                }
            }
            .overlay {
                if installer.isRunning {
                    ZStack {
                        Color.black.opacity(0.3).ignoresSafeArea()
                        VStack(spacing: 20) {
                            Text(installer.progressText).font(.subheadline).multilineTextAlignment(.center)
                            if let fraction = installer.fraction { ProgressView(value: fraction) }
                            else { ProgressView() }
                            Button("取消", role: .cancel) { installer.cancel() }
                        }
                        .padding(24)
                        .frame(maxWidth: 320)
                        .background(.regularMaterial)
                        .cornerRadius(16)
                        .padding()
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
        .modifier(HubAppearanceModifier())
        .interactiveDismissDisabled(installer.isRunning)
        .task {
            refreshState()
            await loadManifest()
        }
        .onChange(of: installer.isRunning) { running in
            if !running {
                refreshState()
                library.refresh()
                if installer.succeeded {
                    notice = hubText("无码补丁安装成功")
                } else if let err = installer.failure {
                    alertError = err
                }
            }
        }
        .alert("卸载无码补丁", isPresented: $confirmUninstall) {
            Button("取消", role: .cancel) {}
            Button("确定卸载", role: .destructive) {
                do {
                    try UncensoredPatchStore.uninstall(from: game.url)
                    refreshState()
                    library.refresh()
                    notice = hubText("已卸载无码补丁")
                } catch {
                    alertError = error.localizedDescription
                }
            }
        } message: {
            Text("确定卸载无码补丁吗？仅删除独立无码资源文件，不影响游戏本体、MOD 与存档。")
        }
        .alert("操作失败", isPresented: Binding(get: { alertError != nil }, set: { if !$0 { alertError = nil } })) {
            Button("好", role: .cancel) { alertError = nil }
        } message: {
            Text(alertError ?? "")
        }
    }

    private func refreshState() {
        installed = UncensoredPatchStore.isInstalled(game.url)
        installedInfo = InstalledUncensoredInfo.read(game.url)
    }

    @MainActor
    private func loadManifest() async {
        guard !loading else { return }
        loading = true
        loadError = nil
        do {
            manifest = try await UncensoredPatchManifest.fetch(for: descriptor)
        } catch {
            loadError = error.localizedDescription
        }
        loading = false
    }
}
