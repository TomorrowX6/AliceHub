import Foundation
import CoreFoundation

enum ArchiveError: LocalizedError {
    case invalidPath, noSpace, cancelled, invalidImage
    case extraction(String)
    var errorDescription: String? {
        switch self {
        case .invalidPath: return hubText("压缩包包含不安全的文件路径。")
        case .noSpace: return hubText("存储空间不足，请清理空间后重试。")
        case .cancelled: return hubText("已取消导入。")
        case .invalidImage: return hubText("光盘镜像损坏或格式不受支持。")
        case .extraction(let message): return hubText("解压失败：%@", hubText(message))
        }
    }
}

enum ArchiveExtractor {
    static let gbk = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GBK_95.rawValue))
    static let sjis = String.Encoding.shiftJIS.rawValue
    private static let gb18030 = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue))
    private static let cp932 = CFStringConvertEncodingToNSStringEncoding(CFStringConvertWindowsCodepageToEncoding(932))

    static func isSupportedArchiveName(_ name: String) -> Bool {
        [".zip", ".rar", ".7z", ".tar", ".tar.gz", ".tgz", ".tar.xz", ".txz", ".iso", ".mdf"]
            .contains { name.lowercased().hasSuffix($0) }
    }

    static func decodeBestCjkText(_ data: Data, preferGbkOnTie: Bool = true) -> String {
        if let utf8 = String(data: data, encoding: .utf8) { return utf8 }
        func legacy(_ encoding: String) -> String {
            data.withUnsafeBytes { bytes in
                guard let decoded = AHDecodeLegacyText(bytes.bindMemory(to: UInt8.self).baseAddress, data.count, encoding)
                else { return "\u{FFFD}" }
                defer { free(decoded) }
                return String(cString: decoded)
            }
        }
        let japanese = String(data: data, encoding: String.Encoding(rawValue: sjis)) ?? legacy("SHIFT_JIS")
        let chinese = String(data: data, encoding: String.Encoding(rawValue: gbk)) ?? legacy("GBK")
        func score(_ text: String) -> Int {
            text.unicodeScalars.reduce(0) { value, scalar in
                switch scalar.value {
                case 0xFFFD: return value - 25
                case 0x3041...0x309F, 0x30A1...0x30FF: return value + 4
                case 0xFF61...0xFF9F: return value - 6
                case 0xFF01...0xFF5E: return value + 2
                case 0x4E00...0x9FFF: return value + 1
                default: return value
                }
            }
        }
        let bytes = Array(data)
        var bonus = 0, penalty = 0, i = 0
        while i < bytes.count {
            let a = bytes[i]
            if a < 0x80 { i += 1; continue }
            if i + 1 < bytes.count {
                let b = bytes[i + 1]
                if (0xB0...0xF7).contains(a) && (0xA1...0xFE).contains(b) { bonus += 3 }
                else if (0x81...0x9F).contains(a) && b < 0xA1 { bonus -= 3 }
                i += 2
            } else { bonus -= 10; i += 1 }
        }
        i = 0
        while i + 1 < bytes.count {
            let a = bytes[i], b = bytes[i + 1]
            if a < 0x80 { i += 1; continue }
            if (0xB0...0xDF).contains(a) && (0xA1...0xFE).contains(b) { penalty -= 6; i += 2 }
            else { i += (0xA1...0xDF).contains(a) ? 1 : 2 }
        }
        let j = score(japanese) + penalty, c = score(chinese) + bonus
        return j > c || (j == c && !preferGbkOnTie) ? japanese : chinese
    }

    static func repairSjisAsGbkMojibake(_ input: String) -> String {
        let separator = input.contains("\\") && !input.contains("/") ? "\\" : "/"
        return input.replacingOccurrences(of: "\\", with: "/").components(separatedBy: "/").map { segment in
            guard segment.unicodeScalars.contains(where: { $0.value >= 128 }),
                  let data = segment.data(using: String.Encoding(rawValue: gb18030), allowLossyConversion: false)
            else { return segment }
            let bytes = Array(data)
            var i = 0, pairs = 0
            while i < bytes.count {
                let a = bytes[i]
                if a < 0x80 { i += 1; continue }
                guard !(0xA1...0xDF).contains(a),
                      ((0x81...0x9F).contains(a) || (0xE0...0xEF).contains(a)), i + 1 < bytes.count else { return segment }
                let b = bytes[i + 1]
                guard ((0x40...0x7E).contains(b) || (0x80...0xFC).contains(b)), a < 0xA1 || b < 0xA1 else { return segment }
                pairs += 1
                i += 2
            }
            guard pairs > 0,
                  let repaired = String(data: data, encoding: String.Encoding(rawValue: cp932)) ?? String(data: data, encoding: .shiftJIS),
                  !repaired.contains("\u{FFFD}")
            else { return segment }
            return repaired
        }.joined(separator: separator)
    }

    static func checkSpace(_ size: Int64, at root: URL) throws {
        guard size > 0 else { return }
        let available = try root.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
            .volumeAvailableCapacityForImportantUsage
        if let available = available, size > available - 32 * 1024 * 1024 { throw ArchiveError.noSpace }
    }

    static func outputPath(_ decoded: String, size: Int64, root: URL,
                           shouldSkip: (String) -> Bool = { _ in false }) throws -> URL? {
        guard !decoded.contains("\0") else { throw ArchiveError.invalidPath }
        let parts = decoded.replacingOccurrences(of: "\\", with: "/").split(separator: "/").filter { $0 != "." }
        if parts.isEmpty || parts.contains("__MACOSX") || parts.contains(".DS_Store") { return nil }
        guard !parts.contains("..") else { throw ArchiveError.invalidPath }
        let relative = parts.joined(separator: "/")
        if shouldSkip(relative) { return nil }
        let canonical = root.resolvingSymlinksInPath().standardizedFileURL
        let target = parts.reduce(canonical) {
            $0.appendingPathComponent(String($1)).resolvingSymlinksInPath().standardizedFileURL
        }
        guard target.path.hasPrefix(canonical.path + "/") else { throw ArchiveError.invalidPath }
        if decoded.hasSuffix("/") || decoded.hasSuffix("\\") {
            try FileManager.default.createDirectory(at: target, withIntermediateDirectories: true)
            return nil
        }
        try checkSpace(size, at: canonical)
        try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        return target
    }

    @discardableResult
    static func extract(_ source: URL, to destination: URL,
                        shouldSkip: @escaping (String) -> Bool = { _ in false },
                        checkCancelled: @escaping () throws -> Void = {},
                        onProgress: @escaping (Int64, String) -> Void = { _, _ in }) throws -> Int {
        try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
        if try Iso9660Extractor.isDiscImage(source) {
            return try Iso9660Extractor.extract(source, to: destination, shouldSkip: shouldSkip,
                                                checkCancelled: checkCancelled, onProgress: onProgress)
        }
        let state = ArchiveExtractionState(root: destination, skip: shouldSkip, cancel: checkCancelled, progress: onProgress)
        let fd = open(source.path, O_RDONLY | O_CLOEXEC)
        guard fd >= 0 else { throw ArchiveError.extraction(String(cString: strerror(errno))) }
        defer { close(fd) }
        var message = [CChar](repeating: 0, count: 1024)
        let count = AHExtractArchiveFD(fd, Unmanaged.passUnretained(state).toOpaque(), { context, raw, length, size, output in
            let state = Unmanaged<ArchiveExtractionState>.fromOpaque(context!).takeUnretainedValue()
            do {
                try state.cancel()
                let name = ArchiveExtractor.repairSjisAsGbkMojibake(ArchiveExtractor.decodeBestCjkText(Data(bytes: raw!, count: length)))
                guard let path = try ArchiveExtractor.outputPath(name, size: size, root: state.root, shouldSkip: state.skip) else { return 0 }
                state.current = name
                output!.pointee = strdup(path.path)
                return 1
            } catch { state.error = error; return -1 }
        }, { context, bytes in
            let state = Unmanaged<ArchiveExtractionState>.fromOpaque(context!).takeUnretainedValue()
            do { try state.cancel(); state.progress(bytes, state.current); return 1 }
            catch { state.error = error; return 0 }
        }, &message, message.count)
        if let error = state.error { throw error }
        guard count >= 0 else { throw ArchiveError.extraction(String(cString: message)) }
        return Int(count)
    }
}

private final class ArchiveExtractionState {
    let root: URL
    let skip: (String) -> Bool
    let cancel: () throws -> Void
    let progress: (Int64, String) -> Void
    var current = ""
    var error: Error?
    init(root: URL, skip: @escaping (String) -> Bool, cancel: @escaping () throws -> Void,
         progress: @escaping (Int64, String) -> Void) {
        self.root = root; self.skip = skip; self.cancel = cancel; self.progress = progress
    }
}
