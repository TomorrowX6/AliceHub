import SwiftUI

struct ModReleaseDetail: View {
    @ObservedObject var model: ModManagerModel
    let index: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            let releases = model.visibleReleases
            if releases.indices.contains(index) {
                let entry = releases[index]
                let switchingGroup = model.hasMod && ModCatalog.groupsConflict(entry.group, model.installed?.group)
                let switchBlocked = switchingGroup && !model.canRestore
                let detailActions = ModManagerPolicy.actions(releases, index: index, hasMod: model.hasMod, installed: model.installed, canRestore: model.canRestore)
                List {
                    Section {
                        HubSectionHeader(title: entry.version, subtitle: entry.isPatch ? "增量补丁" : "完整版本", symbol: "shippingbox")
                    }.listRowBackground(HubTheme.surface)
                    Section {
                        if !entry.group.isEmpty { Text(hubText("系列：%@", entry.group)) }
                        Text(hubText("文件：%@", entry.fileName)).textSelection(.enabled)
                        if entry.expectedSize > 0 { Text(hubText("大小：%@", ModCatalog.formatSize(entry.expectedSize))) }
                        if !entry.formattedDate.isEmpty { Text(hubText("时间：%@", entry.formattedDate)) }
                        Text(entry.isPatch ? hubText("增量补丁 · 需 %@", entry.baseVersion ?? "-") : hubText(model.descriptor.isCnPatch ? "完整汉化补丁" : "完整版本"))
                    }
                    if switchBlocked {
                        Section {
                            Text("当前整合包无原版备份，无法原地切换其他 MOD 系列，请先安装原版游戏后再安装。")
                                .foregroundColor(.orange)
                        }
                    } else if entry.isPatch, let base = entry.baseVersion,
                       !ModCatalog.hasMatchingBaseForPatch(releases, patchIdx: index, hasMod: model.hasMod, installed: model.installed) {
                        Section { Text(hubText("未安装 %@，建议组合安装", base)).foregroundColor(.orange) }
                    }
                    if !entry.changelog.isEmpty { Section("更新说明") { Text(entry.changelog).textSelection(.enabled) } }
                    if !detailActions.isEmpty {
                        Section {
                            ForEach(Array(detailActions.enumerated()), id: \.offset) { _, action in
                                Button(hubText(action.title)) { dismiss(); model.install(action.steps) }
                            }
                        }.disabled(model.busy)
                    }
                }
                .listStyle(.insetGrouped).modifier(HubListStyle())
                .navigationTitle(entry.version).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
            }
        }.navigationViewStyle(.stack).modifier(HubAppearanceModifier())
    }
}

struct ModReleaseRow: View {
    let entry: ModReleaseEntry
    let latest: Bool
    let installed: Bool
    let base: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(entry.version).font(.headline).foregroundColor(.primary)
            HStack {
                if latest { chip(hubText("最新"), .blue) }
                if installed { chip(hubText("已装"), .green) }
                else if base { chip(hubText("基础"), .green) }
                if entry.isPatch { chip(hubText("补丁"), .secondary) }
            }
            Text(ModManagerPolicy.meta(entry)).font(.caption).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
        }.padding(.vertical, 5)
    }
    private func chip(_ title: String, _ color: Color) -> some View {
        Text(title).font(.caption).padding(.horizontal, 7).padding(.vertical, 3)
            .foregroundColor(color).background(color.opacity(0.12)).clipShape(Capsule())
    }
}
