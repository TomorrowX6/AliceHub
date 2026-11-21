import SwiftUI

struct ModReleaseDetail: View {
    @ObservedObject var model: ModManagerModel
    let index: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            if model.releases.indices.contains(index) {
                let entry = model.releases[index]
                List {
                    Section {
                        Text("文件：" + entry.fileName).textSelection(.enabled)
                        if entry.expectedSize > 0 { Text("大小：" + ModCatalog.formatSize(entry.expectedSize)) }
                        if !entry.formattedDate.isEmpty { Text("时间：" + entry.formattedDate) }
                        Text(entry.isPatch ? "增量补丁 · 需 " + (entry.baseVersion ?? "-") : (model.descriptor.isCnPatch ? "完整汉化补丁" : "完整版本"))
                    }
                    if entry.isPatch, let base = entry.baseVersion,
                       !ModCatalog.hasMatchingBaseForPatch(model.releases, patchIdx: index, hasMod: model.hasMod, installed: model.installed) {
                        Section { Text("未安装 \(base)，建议组合安装").foregroundColor(.orange) }
                    }
                    if !entry.changelog.isEmpty { Section("更新说明") { Text(entry.changelog).textSelection(.enabled) } }
                    Section {
                        ForEach(Array(ModManagerPolicy.actions(model.releases, index: index, hasMod: model.hasMod, installed: model.installed).enumerated()), id: \.offset) { _, action in
                            Button(action.title) { dismiss(); model.install(action.steps) }
                        }
                    }.disabled(model.busy)
                }
                .navigationTitle(entry.version).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
            }
        }.navigationViewStyle(.stack)
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
                if latest { chip("最新", .blue) }
                if installed { chip("已装", .green) }
                else if base { chip("基础", .green) }
                if entry.isPatch { chip("补丁", .secondary) }
            }
            Text(ModManagerPolicy.meta(entry)).font(.caption).foregroundColor(.secondary).fixedSize(horizontal: false, vertical: true)
        }.padding(.vertical, 5)
    }
    private func chip(_ title: String, _ color: Color) -> some View {
        Text(title).font(.caption).padding(.horizontal, 7).padding(.vertical, 3)
            .foregroundColor(color).background(color.opacity(0.12)).clipShape(Capsule())
    }
}
