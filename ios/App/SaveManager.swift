import Foundation
import CoreFoundation

final class SaveManager {
    struct FileEntry: Identifiable {
        let url: URL
        let path: String
        let size: Int
        let modified: Date
        var id: String { path }
    }
    struct ExportSummary: Equatable {
        let exported: Int
        let converted: Int
        let xsystem4OnlyFiles: [String]
    }
    enum Failure: LocalizedError {
        case unsafePath, conversion, conversionFile(String), swap, invalidName, nameExists
        var errorDescription: String? {
            switch self {
            case .unsafePath: return hubText("存档路径不安全。")
            case .conversion: return hubText("转换 PC 存档失败。")
            case .conversionFile(let name): return hubText("转换 PC 存档失败：%@", name)
            case .swap: return hubText("无法替换存档目录。")
            case .invalidName: return hubText("文件名无效。")
            case .nameExists: return hubText("同名文件已存在。")
            }
        }
    }
    let game: String
    let directory: URL
    let backupDirectory: URL
    private let files = FileManager.default
    private let converter: (URL, URL) throws -> Void
    private let hasXSystem4OnlyState: (URL) -> Bool
    private let move: (URL, URL) throws -> Void
    static let magic = Data([0x58, 0x34, 0x53, 0x53, 1, 0, 0, 0])

    init(game: String, documents: URL? = nil, support: URL? = nil,
         move: @escaping (URL, URL) throws -> Void = { try FileManager.default.moveItem(at: $0, to: $1) },
         hasXSystem4OnlyState: @escaping (URL) -> Bool = { _ in false },
         converter: @escaping (URL, URL) throws -> Void) {
        self.game = game
        directory = (documents ?? files.urls(for: .documentDirectory, in: .userDomainMask)[0]).appendingPathComponent("Saves").appendingPathComponent(game)
        backupDirectory = (support ?? files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]).appendingPathComponent("save_backups").appendingPathComponent(game)
        self.hasXSystem4OnlyState = hasXSystem4OnlyState
        self.converter = converter
        self.move = move
    }

    static func isExcludedFromPcExport(_ fileName: String) -> Bool {
        let lower = fileName.lowercased()
        let ext = (lower as NSString).pathExtension
        return ["bak", "log", "comment"].contains(ext) || lower.hasSuffix(".mod.absent")
    }

    static func resolveAinPath(in gameDirectory: URL, bootEntry: String = "", isDaiSengokuSave: Bool = false) -> String? {
        let fm = FileManager.default
        let keys: Set<URLResourceKey> = [.isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey]
        guard let contents = try? fm.contentsOfDirectory(
            at: gameDirectory,
            includingPropertiesForKeys: Array(keys),
            options: .skipsHiddenFiles
        ) else { return nil }
        let regularFiles = contents.filter {
            guard let values = try? $0.resourceValues(forKeys: keys) else { return false }
            return values.isRegularFile == true && values.isSymbolicLink != true
        }
        let hasChsTable = regularFiles.contains { $0.lastPathComponent.lowercased() == "chs.tbl" }
        let hasHaruCnAin = regularFiles.contains { $0.lastPathComponent.lowercased() == "haru_c.ain" }
        let gbkEncoding = String.Encoding(
            rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GBK_95.rawValue))
        )
        let codeNamePattern = #"(?m)^[\t ]*CodeName[\t ]*=[\t ]*"([^"\r\n]+)""#

        func isCandidateIni(_ url: URL) -> Bool {
            let lower = url.lastPathComponent.lowercased()
            return !lower.hasPrefix(".") && lower != "system39.ini" && lower != "system3.ini" &&
                (lower.hasSuffix(".ini") || lower == "alicestart.chs")
        }

        func isScriptFile(_ url: URL) -> Bool {
            let lower = url.lastPathComponent.lowercased()
            return (lower.hasSuffix(".ain") && lower != "system39.ain") ||
                (hasChsTable && lower.hasSuffix(".chs") && lower != "alicestart.chs")
        }

        func readBoundedIniData(_ url: URL) -> Data? {
            let limit = 256 * 1024
            guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
            defer { try? handle.close() }
            guard let data = try? handle.read(upToCount: limit + 1), data.count <= limit else { return nil }
            return data
        }

        func extractCodeNames(from iniURL: URL) -> [String] {
            guard let data = readBoundedIniData(iniURL) else { return [] }
            var names: [String] = []
            for decoded in [
                String(data: data, encoding: .utf8),
                String(data: data, encoding: .shiftJIS),
                String(data: data, encoding: gbkEncoding),
            ].compactMap({ $0 }) {
                let text = decoded.trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
                guard let range = text.range(of: codeNamePattern, options: .regularExpression),
                      let firstQuote = text[range].firstIndex(of: "\"") else { continue }
                let code = String(text[text.index(after: firstQuote)..<range.upperBound].dropLast())
                if !code.isEmpty && !names.contains(code) {
                    names.append(code)
                }
            }
            return names
        }

        func iniUsesDaiSengokuSaveFolder(_ iniURL: URL) -> Bool {
            guard let data = readBoundedIniData(iniURL),
                  let rawText = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
                return false
            }
            let text = rawText.trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
            for rawLine in text.split(whereSeparator: \.isNewline) {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                if line.isEmpty || line.hasPrefix(";") || line.hasPrefix("#") || line.hasPrefix("//") { continue }
                guard let eq = line.firstIndex(of: "=") else { continue }
                let key = line[..<eq].trimmingCharacters(in: .whitespaces)
                if key.caseInsensitiveCompare("SaveFolder") != .orderedSame { continue }
                var value = line[line.index(after: eq)...].trimmingCharacters(in: .whitespaces)
                if value.hasPrefix("\"") {
                    let rest = value.dropFirst()
                    if let close = rest.firstIndex(of: "\"") {
                        value = String(rest[..<close])
                    } else {
                        value = String(rest)
                    }
                } else {
                    for marker in [";", "#", "//"] {
                        if let range = value.range(of: marker) {
                            value = String(value[..<range.lowerBound])
                        }
                    }
                    value = value.trimmingCharacters(in: .whitespaces)
                }
                return value.caseInsensitiveCompare("SaveDataDSR") == .orderedSame
            }
            return false
        }

        func resolveConfiguredScript(_ codeName: String) -> URL? {
            let path = codeName.replacingOccurrences(of: "\\", with: "/")
            let components = path.split(separator: "/").filter { $0 != "." }
            guard !path.hasPrefix("/"), !path.contains("\0"), !components.isEmpty,
                  !(components.first?.contains(":") ?? false), !components.contains(".."),
                  let leafLower = components.last?.lowercased(),
                  (leafLower.hasSuffix(".ain") && leafLower != "system39.ain") ||
                    (leafLower.hasSuffix(".chs") && leafLower != "alicestart.chs") else { return nil }
            var directory = gameDirectory
            for (index, component) in components.enumerated() {
                let leaf = String(component)
                guard let children = try? fm.contentsOfDirectory(at: directory, includingPropertiesForKeys: Array(keys)),
                      let match = children.first(where: { $0.lastPathComponent == leaf }) ??
                        children.first(where: { $0.lastPathComponent.caseInsensitiveCompare(leaf) == .orderedSame }),
                      let values = try? match.resourceValues(forKeys: keys),
                      values.isSymbolicLink != true else { return nil }
                if index == components.count - 1 { return values.isRegularFile == true ? match : nil }
                guard values.isDirectory == true else { return nil }
                directory = match
            }
            return nil
        }

        if !bootEntry.isEmpty,
           let chosen = regularFiles.first(where: { $0.lastPathComponent == bootEntry }) {
            if isScriptFile(chosen) { return chosen.path }
            if isCandidateIni(chosen) {
                for code in extractCodeNames(from: chosen) {
                    if let script = resolveConfiguredScript(code) { return script.path }
                }
            }
        }

        let candidateInis = regularFiles.filter(isCandidateIni).sorted { $0.lastPathComponent < $1.lastPathComponent }
        if isDaiSengokuSave {
            for ini in candidateInis where iniUsesDaiSengokuSaveFolder(ini) {
                for code in extractCodeNames(from: ini) {
                    if let script = resolveConfiguredScript(code) { return script.path }
                }
            }
        }

        func findIni(_ name: String) -> URL? {
            candidateInis.first { $0.lastPathComponent == name }
                ?? candidateInis.first { $0.lastPathComponent.caseInsensitiveCompare(name) == .orderedSame }
        }
        var prioritizedInis: [URL] = []
        if hasHaruCnAin, let ini = findIni("AliceStart2.ini") { prioritizedInis.append(ini) }
        if hasChsTable, let ini = findIni("AliceStart.CHS") { prioritizedInis.append(ini) }
        for name in ["System40.ini", "AliceStart.ini", "AliceStart2.ini", "AliceStart.CHS"] {
            if let ini = findIni(name), !prioritizedInis.contains(ini) { prioritizedInis.append(ini) }
        }
        for ini in candidateInis where !prioritizedInis.contains(ini) {
            prioritizedInis.append(ini)
        }
        var sawDsrIni = false
        for ini in prioritizedInis {
            if !isDaiSengokuSave && iniUsesDaiSengokuSaveFolder(ini) {
                sawDsrIni = true
                continue
            }
            for code in extractCodeNames(from: ini) {
                if let script = resolveConfiguredScript(code) { return script.path }
            }
        }
        if sawDsrIni && !isDaiSengokuSave,
           let aliceCn = regularFiles.first(where: { $0.lastPathComponent.lowercased() == "alicecn.ain" }) {
            return aliceCn.path
        }
        return regularFiles
            .filter {
                let lower = $0.lastPathComponent.lowercased()
                return lower.hasSuffix(".ain") && lower != "system39.ain"
            }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .first?.path
    }

    private func children(_ url: URL) throws -> [URL] {
        try files.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey])
    }

    func list(_ root: URL? = nil) throws -> [FileEntry] {
        let root = root ?? directory
        guard files.fileExists(atPath: root.path) else { return [] }
        var result: [FileEntry] = []
        func visit(_ folder: URL, _ prefix: String) throws {
            for url in try children(folder) where !url.lastPathComponent.hasPrefix(".") {
                let values = try url.resourceValues(forKeys: [.isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey, .fileSizeKey, .contentModificationDateKey])
                guard values.isSymbolicLink != true else { throw Failure.unsafePath }
                let path = prefix + url.lastPathComponent
                if values.isDirectory == true { try visit(url, path + "/") }
                else if values.isRegularFile == true {
                    result.append(FileEntry(url: url, path: path, size: values.fileSize ?? 0, modified: values.contentModificationDate ?? Date()))
                }
            }
        }
        try visit(root, "")
        return result.sorted { $0.path.localizedStandardCompare($1.path) == .orderedAscending }
    }

    private func stamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd_HHmmss_SSS"
        return formatter.string(from: Date())
    }

    func backups() throws -> [URL] {
        guard files.fileExists(atPath: backupDirectory.path) else { return [] }
        return try children(backupDirectory).filter { $0.pathExtension == "zip" && !$0.lastPathComponent.hasPrefix(".") }
            .sorted { $0.lastPathComponent > $1.lastPathComponent }
    }

    @discardableResult func backup() throws -> URL? {
        let entries = try list()
        guard !entries.isEmpty else { return nil }
        try files.createDirectory(at: backupDirectory, withIntermediateDirectories: true)
        var destination = backupDirectory.appendingPathComponent("SaveData_\(stamp()).zip")
        while files.fileExists(atPath: destination.path) {
            destination = destination.deletingLastPathComponent().appendingPathComponent("SaveData_\(stamp()).zip")
        }
        let temporary = backupDirectory.appendingPathComponent(".\(UUID().uuidString).tmp")
        defer { try? files.removeItem(at: temporary) }
        try SaveZip.write(try entries.map { SaveZip.Entry(name: "SaveData/" + $0.path, data: try Data(contentsOf: $0.url), modified: $0.modified) }, to: temporary)
        try move(temporary, destination)
        for old in try backups().dropFirst(5) { try files.removeItem(at: old) }
        return destination
    }

    private func safePath(_ name: String) throws -> String? {
        let path = name.replacingOccurrences(of: "\\", with: "/")
        guard !path.hasPrefix("/"), !path.contains("\0") else { throw Failure.unsafePath }
        let parts = path.split(separator: "/", omittingEmptySubsequences: true)
        guard !parts.contains(".."), !parts.contains("."), !(parts.first?.contains(":") ?? false) else { throw Failure.unsafePath }
        guard !parts.isEmpty, !parts.contains(where: { $0.hasPrefix(".") }) else { return nil }
        return parts.joined(separator: "/")
    }

    private func install(_ entry: SaveZip.Entry, path: String, root: URL) throws {
        let target = root.appendingPathComponent(path)
        // resolvingSymlinksInPath only strips "/private" from paths that exist, so
        // compare the target if it exists, otherwise its nearest existing ancestor.
        var existing = target
        while !files.fileExists(atPath: existing.path) { existing.deleteLastPathComponent() }
        let base = root.resolvingSymlinksInPath().standardizedFileURL.path
        let resolved = existing.resolvingSymlinksInPath().standardizedFileURL.path
        guard resolved == base || resolved.hasPrefix(base + "/") else { throw Failure.unsafePath }
        try files.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        try entry.data.write(to: target, options: .atomic)
        try files.setAttributes([.modificationDate: entry.modified], ofItemAtPath: target.path)
    }

    func restore(_ archive: URL) throws {
        let entries = try SaveZip.read(archive)
        let parent = directory.deletingLastPathComponent()
        try files.createDirectory(at: parent, withIntermediateDirectories: true)
        let staging = parent.appendingPathComponent(".restore_staging_\(UUID().uuidString)")
        let old = parent.appendingPathComponent(".restore_old_\(UUID().uuidString)")
        try files.createDirectory(at: staging, withIntermediateDirectories: false)
        defer { try? files.removeItem(at: staging) }
        for entry in entries {
            var name = entry.name.replacingOccurrences(of: "\\", with: "/")
            if name.hasPrefix("SaveData/") { name.removeFirst(9) }
            guard let path = try safePath(name), !name.hasSuffix("/") else { continue }
            try install(entry, path: path, root: staging)
        }
        try backup()
        let existed = files.fileExists(atPath: directory.path)
        if existed {
            func preserveHidden(_ source: URL, _ destination: URL) throws {
                for child in try children(source) {
                    if source == directory && GlobalSaveEditor.Backup.allCases.contains(where: { $0.filename == child.lastPathComponent }) {
                        continue
                    }
                    let values = try child.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
                    guard values.isSymbolicLink != true else { throw Failure.unsafePath }
                    let target = destination.appendingPathComponent(child.lastPathComponent)
                    if child.lastPathComponent.hasPrefix(".") {
                        try files.createDirectory(at: destination, withIntermediateDirectories: true)
                        try files.copyItem(at: child, to: target)
                    } else if values.isDirectory == true {
                        try preserveHidden(child, target)
                    }
                }
            }
            try preserveHidden(directory, staging)
            try move(directory, old)
        }
        do { try move(staging, directory) }
        catch {
            if existed {
                do { try move(old, directory) }
                catch { throw NSError(domain: "SaveManager", code: 1, userInfo: [NSLocalizedDescriptionKey: hubText("恢复失败；原存档保留在 %@", old.path), NSUnderlyingErrorKey: error]) }
            }
            throw error
        }
        try? files.removeItem(at: old)
    }

    private func normalized(_ entries: [SaveZip.Entry]) throws -> [SaveZip.Entry] {
        let cleaned = try entries.compactMap { entry -> SaveZip.Entry? in
            guard let path = try safePath(entry.name) else { return nil }
            return SaveZip.Entry(name: path + (entry.name.hasSuffix("/") || entry.name.hasSuffix("\\") ? "/" : ""), data: entry.data, modified: entry.modified)
        }
        let paths = cleaned.filter { !$0.name.hasSuffix("/") }.map { $0.name.split(separator: "/").map(String.init) }
        var prefix: [String] = []
        if let save = paths.compactMap({ parts -> [String]? in
            guard let i = parts.prefix(3).firstIndex(where: { $0.caseInsensitiveCompare("SaveData") == .orderedSame }), i < parts.count - 1 else { return nil }
            return Array(parts.prefix(i + 1))
        }).sorted(by: { $0.count < $1.count }).first { prefix = save }
        else {
            while let first = paths.first, prefix.count < first.count - 1 {
                let next = Array(first.prefix(prefix.count + 1))
                guard paths.allSatisfy({ $0.count > next.count && Array($0.prefix(next.count)) == next }) else { break }
                prefix = next
            }
        }
        return cleaned.compactMap { entry in
            let parts = entry.name.split(separator: "/").map(String.init)
            guard !entry.name.hasSuffix("/"), parts.count > prefix.count, Array(parts.prefix(prefix.count)) == prefix else { return nil }
            return SaveZip.Entry(name: parts.dropFirst(prefix.count).joined(separator: "/"), data: entry.data, modified: entry.modified)
        }
    }

    func importURLs(_ urls: [URL], folder: Bool = false) throws {
        try backup()
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        for url in urls {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            let entries: [SaveZip.Entry]
            if folder {
                let direct = try children(url)
                let hasSaves = direct.contains { ["asd", "sav", "rsm"].contains($0.pathExtension.lowercased()) && (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
                let root = !hasSaves ? direct.first(where: { $0.lastPathComponent.caseInsensitiveCompare("SaveData") == .orderedSame && (try? $0.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true }) ?? url : url
                entries = try list(root).map { SaveZip.Entry(name: $0.path, data: try Data(contentsOf: $0.url), modified: $0.modified) }
            } else if url.pathExtension.lowercased() == "zip" { entries = try normalized(SaveZip.read(url)) }
            else { entries = [SaveZip.Entry(name: url.lastPathComponent, data: try Data(contentsOf: url))] }
            for entry in entries {
                guard let path = try safePath(entry.name) else { continue }
                try install(entry, path: path, root: directory)
            }
        }
    }

    func exportWithReport(to destination: URL, pc: Bool, zip: Bool, single: FileEntry? = nil) throws -> ExportSummary {
        let entries = try single.map { [$0] } ?? list().filter { !pc || !Self.isExcludedFromPcExport($0.url.lastPathComponent) }
        var converted = 0
        var xsystem4OnlyFiles: [String] = []
        let scratch = files.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try files.createDirectory(at: scratch, withIntermediateDirectories: true)
        defer { try? files.removeItem(at: scratch) }
        var output: [SaveZip.Entry] = []
        for entry in entries {
            let original = try Data(contentsOf: entry.url)
            var data = original
            let exportedName = single == nil ? entry.path : entry.url.lastPathComponent
            if pc {
                if hasXSystem4OnlyState(entry.url) {
                    xsystem4OnlyFiles.append(exportedName)
                }
                let temporary = scratch.appendingPathComponent(UUID().uuidString)
                do {
                    try converter(entry.url, temporary)
                } catch Failure.conversion {
                    throw Failure.conversionFile(entry.path)
                }
                let exportedData = try Data(contentsOf: temporary)
                if exportedData.prefix(8) == Self.magic {
                    throw Failure.conversionFile(entry.path)
                }
                if original.prefix(8) == Self.magic || exportedData != original {
                    converted += 1
                }
                data = exportedData
            }
            output.append(SaveZip.Entry(name: exportedName, data: data, modified: entry.modified))
        }
        if zip { try SaveZip.write(output.map { SaveZip.Entry(name: "SaveData/" + $0.name, data: $0.data, modified: $0.modified) }, to: destination) }
        else if single != nil, let entry = output.first { try entry.data.write(to: destination, options: .atomic) }
        else {
            let root = destination.appendingPathComponent("SaveData")
            try files.createDirectory(at: root, withIntermediateDirectories: true)
            for entry in output { try install(entry, path: entry.name, root: root) }
        }
        return ExportSummary(exported: output.count, converted: converted, xsystem4OnlyFiles: xsystem4OnlyFiles)
    }

    func export(to destination: URL, pc: Bool, zip: Bool, single: FileEntry? = nil) throws -> (Int, Int) {
        let summary = try exportWithReport(to: destination, pc: pc, zip: zip, single: single)
        return (summary.exported, summary.converted)
    }

    func exportName(pc: Bool) -> String { "\(game)_\(pc ? "PC_" : "")SaveData_\(stamp()).zip" }
    func duplicate(_ entry: FileEntry) throws {
        try duplicate(entry, named: entry.url.lastPathComponent + ".bak", overwrite: true)
    }

    func destination(for entry: FileEntry, named name: String) throws -> URL {
        let name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, !name.hasPrefix("."), !name.contains("/"),
              !name.contains("\\"), !name.contains("\0") else { throw Failure.invalidName }
        let source = directory.appendingPathComponent(entry.path)
        let root = directory.resolvingSymlinksInPath().standardizedFileURL.path
        let resolved = source.resolvingSymlinksInPath().standardizedFileURL
        guard resolved == entry.url.resolvingSymlinksInPath().standardizedFileURL,
              resolved.path == root + "/" + entry.path,
              try source.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile == true else {
            throw Failure.unsafePath
        }
        return resolved.deletingLastPathComponent().appendingPathComponent(name)
    }

    func duplicate(_ entry: FileEntry, named name: String, overwrite: Bool = false) throws {
        let target = try destination(for: entry, named: name)
        if files.fileExists(atPath: target.path) {
            let sourceID = try entry.url.resourceValues(forKeys: [.fileResourceIdentifierKey]).fileResourceIdentifier
            let values = try target.resourceValues(forKeys: [.fileResourceIdentifierKey, .isRegularFileKey, .isSymbolicLinkKey])
            guard target.lastPathComponent != entry.url.lastPathComponent,
                  sourceID?.isEqual(values.fileResourceIdentifier) != true else { throw Failure.invalidName }
            guard overwrite, values.isRegularFile == true, values.isSymbolicLink != true else { throw Failure.nameExists }
            try Data(contentsOf: entry.url).write(to: target, options: .atomic)
        } else {
            let staged = target.deletingLastPathComponent().appendingPathComponent(".\(UUID().uuidString).tmp")
            defer { try? files.removeItem(at: staged) }
            try files.copyItem(at: entry.url, to: staged)
            try move(staged, target)
        }
    }

    func rename(_ entry: FileEntry, named name: String) throws {
        let target = try destination(for: entry, named: name)
        if target.lastPathComponent == entry.url.lastPathComponent { return }
        guard !files.fileExists(atPath: target.path) else { throw Failure.nameExists }
        try move(entry.url, target)
    }
    func delete(_ entry: FileEntry) throws { try files.removeItem(at: entry.url) }
    func clear() throws {
        try backup()
        for entry in try list() { try delete(entry) }
        try GlobalSaveEditor(directory: directory).discardBackups()
    }
}
