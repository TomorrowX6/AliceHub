import Foundation
import Combine

struct ModReleaseAction {
    let title: String
    let steps: [ModReleaseEntry]
}

enum ModManagerPolicy {
    static func actions(_ releases: [ModReleaseEntry], index: Int, hasMod: Bool, installed: InstalledModInfo?, canRestore: Bool = true) -> [ModReleaseAction] {
        guard releases.indices.contains(index) else { return [] }
        let entry = releases[index]
        let switchingGroup = hasMod && ModCatalog.groupsConflict(entry.group, installed?.group)
        if switchingGroup && !canRestore { return [] }
        let current = hasMod && ModCatalog.matchInstalledIndex(releases, installed) == index
        if !entry.isPatch {
            return [ModReleaseAction(title: current ? "重新安装" : (switchingGroup ? "切换安装" : (hasMod ? "切换版本" : "安装")), steps: [entry])]
        }
        let combo = ModCatalog.computeStepsForSelectedRelease(releases, targetIdx: index, hasMod: hasMod, installed: installed, includeBaseForPatch: true, canRestore: canRestore)
        if switchingGroup {
            return combo.count > 1 ? [ModReleaseAction(title: "组合安装", steps: combo)] : []
        }
        let patch = ModCatalog.computeStepsForSelectedRelease(releases, targetIdx: index, hasMod: hasMod, installed: installed, includeBaseForPatch: false, canRestore: canRestore)
        let installedIndex = hasMod ? ModCatalog.matchInstalledIndex(releases, installed) : -1
        if installedIndex >= 0 && installedIndex < index {
            return combo.count > 1 ? [ModReleaseAction(title: "降级并完整重装", steps: combo)] : []
        }
        if ModCatalog.hasMatchingBaseForPatch(releases, patchIdx: index, hasMod: hasMod, installed: installed) {
            var actions = [ModReleaseAction(title: current ? "重装补丁" : "更新补丁", steps: patch)]
            if combo.count > 1 { actions.append(ModReleaseAction(title: "完整重装", steps: combo)) }
            return actions
        }
        if combo.count > 1 { return [ModReleaseAction(title: "组合安装", steps: combo), ModReleaseAction(title: "仅装补丁", steps: patch)] }
        return [ModReleaseAction(title: "更新补丁", steps: patch)]
    }

    static func syncInstalledMeta(_ directory: URL, releases: [ModReleaseEntry], descriptor: ModGameDescriptor) throws {
        guard ModOverlay.detectInstalledMod(directory), var info = InstalledModInfo.readOrInfer(directory) else { return }
        let index = ModCatalog.matchInstalledIndex(releases, info)
        guard releases.indices.contains(index) else { return }
        let matched = releases[index]
        let needsGroupSync = !matched.group.isEmpty && info.group != matched.group
        guard info.version != matched.version || (info.fileId <= 0 && matched.fileId > 0) || needsGroupSync else { return }
        info.version = matched.version
        info.baseVersion = matched.isPatch ? (matched.baseVersion ?? info.baseVersion) : matched.version
        if info.fileId <= 0 { info.fileId = matched.fileId }
        if info.baseFileId <= 0 { info.baseFileId = matched.isPatch ? (matched.baseFileId ?? info.baseFileId) : matched.fileId }
        info.kind = descriptor.overlayKind
        if !matched.group.isEmpty { info.group = matched.group }
        try info.write(to: directory)
    }

    static func meta(_ entry: ModReleaseEntry, language: HubLanguage) -> String {
        [ModCatalog.formatSize(entry.expectedSize), entry.formattedDate,
         entry.isPatch ? entry.baseVersion.map { language.text("需 %@", arguments: [$0]) } ?? "" : "", entry.fileName].filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

final class ModManagerModel: ObservableObject {
    let descriptor: ModGameDescriptor
    let installer = ModInstaller()
    @Published private(set) var directory: URL
    @Published private(set) var releases: [ModReleaseEntry] = []
    @Published var selectedGroup = ""
    @Published private(set) var loading = false
    @Published private(set) var loadFailed = false
    @Published private(set) var installed: InstalledModInfo?
    @Published private(set) var hasMod = false
    @Published private(set) var canRestore = false
    @Published private(set) var busy = false
    @Published var error: String?
    @Published var notice: String?
    private var userPickedGroup = false
    private var restoring = false
    private var subscriptions = Set<AnyCancellable>()

    init(directory: URL, descriptor: ModGameDescriptor) {
        self.directory = directory
        self.descriptor = descriptor
        refreshInstalled()
        installer.$isRunning.dropFirst().receive(on: DispatchQueue.main).sink { [weak self] running in
            guard let self else { return }
            self.busy = running
            if !running {
                self.refreshInstalled()
                self.error = self.installer.failure
                if self.installer.succeeded {
                    self.notice = self.restoring ? (descriptor.isCnPatch ? hubText("已卸载汉化补丁") : hubText("已卸载 MOD")) : (descriptor.isCnPatch ? hubText("汉化补丁安装成功") : hubText("MOD 安装成功"))
                } else if self.installer.failure == nil { self.notice = hubText("已取消") }
            }
        }.store(in: &subscriptions)
    }

    var availableGroups: [String] { ModCatalog.distinctModGroups(releases) }
    var visibleReleases: [ModReleaseEntry] { ModCatalog.releasesForGroup(releases, group: selectedGroup) }
    var switchBlockedByNoBackup: Bool {
        hasMod && !canRestore && ModCatalog.groupsConflict(visibleReleases.first?.group, installed?.group)
    }
    var installedIndex: Int { hasMod ? ModCatalog.matchInstalledIndex(visibleReleases, installed) : -1 }
    var plan: ModUpdatePlan? { ModCatalog.computeUpdatePlan(visibleReleases, hasMod: hasMod, installed: installed, canRestore: canRestore) }
    func status(in language: HubLanguage) -> String {
        let allIdx = hasMod ? ModCatalog.matchInstalledIndex(releases, installed) : -1
        let version = releases.indices.contains(allIdx) ? releases[allIdx].version : installed?.version
        let installedGroup = (releases.indices.contains(allIdx) && !releases[allIdx].group.isEmpty) ? releases[allIdx].group : (installed?.group ?? "")
        let installedLabel: String?
        if let version, !version.isEmpty {
            if !descriptor.isCnPatch && !installedGroup.isEmpty && installedGroup.caseInsensitiveCompare(version) != .orderedSame {
                installedLabel = installedGroup + " · " + version
            } else {
                installedLabel = version
            }
        } else {
            installedLabel = nil
        }
        let current = !hasMod ? language.text(descriptor.isCnPatch ? "未安装汉化补丁" : "未安装 MOD")
            : installedLabel.map { language.text("已装 %@", arguments: [$0]) } ?? language.text(descriptor.isCnPatch ? "已安装汉化补丁" : "已装 MOD")
        if let latest = visibleReleases.first {
            let latestPart = (availableGroups.count > 1 && !latest.group.isEmpty)
                ? language.text("%@ 最新 %@", arguments: [latest.group, latest.version])
                : language.text("最新 %@", arguments: [latest.version])
            return current + " · " + latestPart
        }
        return current
    }
    func updateTitle(in language: HubLanguage) -> String {
        if busy { return language.text("查看进度") }
        if visibleReleases.isEmpty { return language.text(loading ? "检查中…" : (loadFailed ? "重新获取" : "安装最新")) }
        if switchBlockedByNoBackup { return language.text("需原版切换") }
        guard let plan else { return language.text("已是最新") }
        let switchingGroup = hasMod && ModCatalog.groupsConflict(visibleReleases.first?.group, installed?.group)
        let size = ModCatalog.formatSize(plan.steps.reduce(Int64(0)) { $0 + max(0, $1.expectedSize) })
        let baseLabel = language.text(!hasMod ? "安装最新" : (switchingGroup ? "切换安装" : "立即更新"))
        return baseLabel + (size.isEmpty ? "" : " · " + size)
    }
    var updateDisabled: Bool { busy || switchBlockedByNoBackup || (visibleReleases.isEmpty ? !loadFailed : plan == nil) }

    func selectGroup(_ group: String) {
        selectedGroup = group
        userPickedGroup = true
    }

    private func syncSelectedGroup(forceAuto: Bool = false) {
        let groups = availableGroups
        if groups.count <= 1 {
            selectedGroup = groups.first ?? ""
            return
        }
        let valid = !selectedGroup.isEmpty && groups.contains { $0.caseInsensitiveCompare(selectedGroup) == .orderedSame }
        if !forceAuto && userPickedGroup && valid { return }
        let allIdx = hasMod ? ModCatalog.matchInstalledIndex(releases, installed) : -1
        let installedGroup = (releases.indices.contains(allIdx) && !releases[allIdx].group.isEmpty) ? releases[allIdx].group : (installed?.group ?? "")
        if let matched = groups.first(where: { $0.caseInsensitiveCompare(installedGroup) == .orderedSame }) {
            selectedGroup = matched
        } else if valid, let existing = groups.first(where: { $0.caseInsensitiveCompare(selectedGroup) == .orderedSame }) {
            selectedGroup = existing
        } else {
            selectedGroup = groups[0]
        }
    }

    func refreshInstalled() {
        installed = InstalledModInfo.readOrInfer(directory)
        hasMod = ModOverlay.detectInstalledMod(directory)
        canRestore = ModOverlay.canRestoreVanilla(directory)
    }
    func select(_ directory: URL) {
        guard !busy && !installer.isRunning else { return }
        self.directory = directory
        userPickedGroup = false
        syncMetadata()
        syncSelectedGroup(forceAuto: true)
    }
    private func syncMetadata() {
        do { try ModManagerPolicy.syncInstalledMeta(directory, releases: releases, descriptor: descriptor) }
        catch { self.error = error.localizedDescription }
        refreshInstalled()
    }
    @MainActor func loadCatalog() async {
        guard !loading else { return }
        loading = true; loadFailed = false
        do {
            releases = try await ModCatalog.fetchModCatalog(for: descriptor)
            if !installer.isRunning { syncMetadata() }
            syncSelectedGroup()
        } catch { loadFailed = true }
        loading = false
    }
    func install(_ steps: [ModReleaseEntry]) {
        restoring = false; notice = nil
        installer.installOnlinePlan(steps, to: directory, descriptor: descriptor)
    }
    func installLocal(_ source: URL, folder: Bool, cleanReplace: Bool) {
        restoring = false; notice = nil
        if folder { installer.installLocalDirectory(source, to: directory, descriptor: descriptor, cleanReplace: cleanReplace) }
        else { installer.installLocalArchive(source, to: directory, descriptor: descriptor, cleanReplace: cleanReplace) }
    }
    func restore() {
        restoring = true; notice = nil
        installer.restoreVanilla(directory, descriptor: descriptor)
    }
}
