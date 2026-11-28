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
    private static let big5 = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.big5.rawValue))

    static func isSupportedArchiveName(_ name: String) -> Bool {
        [".zip", ".rar", ".7z", ".tar", ".tar.gz", ".tgz", ".tar.xz", ".txz", ".iso", ".mdf"]
            .contains { name.lowercased().hasSuffix($0) }
    }

    private static func isAsciiLetter(_ b: UInt8) -> Bool {
        (0x41...0x5A).contains(b) || (0x61...0x7A).contains(b)
    }

    private static func isCp1252PunctOrAccented(_ b: UInt8) -> Bool {
        b == 0x85 || (0x91...0x94).contains(b) || b == 0x96 || b == 0x97 || b == 0x99 ||
            b == 0xA9 || b == 0xAE || b == 0xB0 ||
            ((0xC0...0xFF).contains(b) && b != 0xD7 && b != 0xF7)
    }

    static func looksLikeCp1252(_ rawBytes: [UInt8], sjisStrict: String?, gbkStrict: String?) -> Bool {
        var highCount = 0
        var hasAdjacentHigh = false
        var asciiLetters = 0
        var allCp1252Isolated = true
        var flankedByLatinContext = false
        for i in rawBytes.indices {
            let b = rawBytes[i]
            if b < 0x80 {
                if isAsciiLetter(b) { asciiLetters += 1 }
                continue
            }
            highCount += 1
            let prev: Int = i > 0 ? Int(rawBytes[i - 1]) : -1
            let next: Int = i + 1 < rawBytes.count ? Int(rawBytes[i + 1]) : -1
            if prev >= 0x80 || next >= 0x80 {
                hasAdjacentHigh = true
                allCp1252Isolated = false
            }
            if !isCp1252PunctOrAccented(b) {
                allCp1252Isolated = false
            }
            if prev >= 0 && isAsciiLetter(UInt8(prev)) && (next < 0 || next < 0x80) {
                if (0xC0...0xFF).contains(b) {
                    if next < 0 || (0x61...0x7A).contains(next) || next == 0x20 || next == 0x2E || next == 0x2D || next == 0x5F || next == 0x2F || next == 0x5C {
                        flankedByLatinContext = true
                    }
                } else {
                    flankedByLatinContext = true
                }
            } else if next >= 0 && (0x61...0x7A).contains(next) &&
                (prev < 0 || prev == 0x20 || prev == 0x2F || prev == 0x5C || prev == 0x2D || prev == 0x5F) {
                flankedByLatinContext = true
            }
        }
        if highCount == 0 { return false }
        if sjisStrict == nil && gbkStrict == nil {
            return allCp1252Isolated || (asciiLetters >= 2 && !hasAdjacentHigh)
        }
        if !hasAdjacentHigh && allCp1252Isolated && flankedByLatinContext && asciiLetters >= 2 {
            guard let sjisCandidate = sjisStrict else { return true }
            let hasKana = sjisCandidate.unicodeScalars.contains {
                (0x3041...0x309F).contains($0.value) || (0x30A1...0x30FF).contains($0.value)
            }
            if !hasKana { return true }
        }
        return false
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
        let bytes = Array(data)
        let sjisStrict = String(data: data, encoding: String.Encoding(rawValue: sjis))
        let gbkStrict = String(data: data, encoding: String.Encoding(rawValue: gbk))
        if looksLikeCp1252(bytes, sjisStrict: sjisStrict, gbkStrict: gbkStrict),
           let cp1252 = String(data: data, encoding: .windowsCP1252) {
            return cp1252
        }
        let japanese = sjisStrict ?? legacy("SHIFT_JIS")
        let chinese = gbkStrict ?? legacy("GBK")
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

    private enum Cp932ScalarClass {
        case ascii, kanaSym, jis1, jis2, halfwidth, ext, nonCp932, pua
    }

    private static func classifyCp932Scalar(_ scalar: UnicodeScalar) -> Cp932ScalarClass {
        let value = scalar.value
        if value < 0x80 { return .ascii }
        if (0xE000...0xF8FF).contains(value) || value == 0xFFFD { return .pua }
        guard let encoded = String(scalar).data(using: String.Encoding(rawValue: cp932), allowLossyConversion: false) else {
            return .nonCp932
        }
        if encoded.count == 1 { return .halfwidth }
        guard encoded.count == 2 else { return .ext }
        let code = (UInt16(encoded[0]) << 8) | UInt16(encoded[1])
        switch code {
        case 0x8140...0x81AC, 0x824F...0x8258, 0x829F...0x82F1, 0x8340...0x8396: return .kanaSym
        case 0x8260...0x8279, 0x8281...0x829A, 0x889F...0x9872: return .jis1
        case 0x989F...0xEAA4: return .jis2
        default: return .ext
        }
    }

    private static func isCommonBig5Scalar(_ scalar: UnicodeScalar) -> Bool {
        guard let encoded = String(scalar).data(using: String.Encoding(rawValue: big5), allowLossyConversion: false),
              encoded.count == 2 else { return false }
        let code = (UInt16(encoded[0]) << 8) | UInt16(encoded[1])
        return (0xA140...0xA3BF).contains(code) || (0xA440...0xC67E).contains(code)
    }

    private static func scoreJapanesePlausibility(_ text: String) -> Int {
        var score = 0
        for scalar in text.unicodeScalars {
            switch classifyCp932Scalar(scalar) {
            case .ascii: break
            case .kanaSym: score += 4
            case .jis1: score += 3
            case .jis2: score -= 1
            case .halfwidth, .ext, .nonCp932: score -= 4
            case .pua: score -= 20
            }
        }
        return score
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
            let origNonAscii = segment.unicodeScalars.filter { $0.value >= 0x80 }.map(classifyCp932Scalar)
            if origNonAscii.allSatisfy({ $0 == .kanaSym || $0 == .jis1 }) { return segment }
            let decNonAscii = repaired.unicodeScalars.filter { $0.value >= 0x80 }.map(classifyCp932Scalar)
            if decNonAscii.contains(where: { $0 == .pua || $0 == .nonCp932 || $0 == .ext || $0 == .halfwidth }) { return segment }
            let decodedHasKana = repaired.unicodeScalars.contains {
                (0x3041...0x309F).contains($0.value) || (0x30A1...0x30FF).contains($0.value)
            }
            if !decodedHasKana {
                let origScalars = segment.unicodeScalars.filter { $0.value >= 0x80 }
                if !origScalars.isEmpty && origScalars.allSatisfy(isCommonBig5Scalar) { return segment }
            }
            let origScore = scoreJapanesePlausibility(segment)
            let decScore = scoreJapanesePlausibility(repaired)
            guard decScore > origScore && decScore > 0 else { return segment }
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
