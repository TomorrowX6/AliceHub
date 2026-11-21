import Foundation

enum ArchiveChecks {
    static func run() throws {
        let files = FileManager.default
        let root = files.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try files.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? files.removeItem(at: root) }
        func run(_ executable: String, _ args: [String]) throws {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: executable)
            process.arguments = args
            let output = Pipe()
            process.standardOutput = output
            process.standardError = output
            try process.run()
            let data = output.fileHandleForReading.readDataToEndOfFile()
            process.waitUntilExit()
            precondition(process.terminationStatus == 0, String(decoding: data, as: UTF8.self))
        }
        func zip(_ name: Data, body: Data = Data("game".utf8), at url: URL) throws {
            var crc: UInt32 = 0xFFFFFFFF
            for byte in body {
                crc ^= UInt32(byte)
                for _ in 0..<8 { crc = (crc >> 1) ^ (crc & 1 == 1 ? 0xEDB88320 : 0) }
            }
            crc ^= 0xFFFFFFFF
            func u16(_ n: Int) -> Data { Data([UInt8(n & 255), UInt8((n >> 8) & 255)]) }
            func u32(_ n: UInt32) -> Data { Data((0..<4).map { UInt8((n >> ($0 * 8)) & 255) }) }
            var local = u32(0x04034B50) + u16(20) + u16(0) + u16(0) + u16(0) + u16(0)
            local += u32(crc) + u32(UInt32(body.count)) + u32(UInt32(body.count)) + u16(name.count) + u16(0)
            local += name + body
            var central = u32(0x02014B50) + u16(20) + u16(20) + u16(0) + u16(0) + u16(0) + u16(0)
            central += u32(crc) + u32(UInt32(body.count)) + u32(UInt32(body.count))
            central += u16(name.count) + u16(0) + u16(0) + u16(0) + u16(0) + u32(0) + u32(0) + name
            let end = u32(0x06054B50) + u16(0) + u16(0) + u16(1) + u16(1) +
                u32(UInt32(central.count)) + u32(UInt32(local.count)) + u16(0)
            try (local + central + end).write(to: url)
        }
        func extract(_ source: URL, _ label: String, expected: String = "System40.ini") throws {
            let dest = root.appendingPathComponent("out-" + label)
            let count = try ArchiveExtractor.extract(source, to: dest)
            precondition(count > 0)
            let body = try Data(contentsOf: dest.appendingPathComponent(expected))
            precondition(body == Data("game".utf8))
            if ["ustar", "gnutar"].contains(label) {
                let linked = try Data(contentsOf: dest.appendingPathComponent("linked.ini"))
                precondition(linked == body)
            }
        }
        for suffix in ["zip", "rar", "7z", "tar", "tar.gz", "tgz", "tar.xz", "txz", "iso", "mdf"] {
            precondition(ArchiveExtractor.isSupportedArchiveName("GAME." + suffix.uppercased()))
        }
        precondition(!ArchiveExtractor.isSupportedArchiveName("game.exe"))
        let name = "ゲーム/ソフト.ain"
        let sjis = name.data(using: .shiftJIS)!
        precondition(ArchiveExtractor.decodeBestCjkText(sjis) == name)
        let chinese = "测试游戏.ain"
        precondition(ArchiveExtractor.decodeBestCjkText(chinese.data(using: String.Encoding(rawValue: ArchiveExtractor.gbk))!) == chinese)
        precondition(ArchiveExtractor.repairSjisAsGbkMojibake("愴崙儔儞僗_Sys.ASD") == "戦国ランス_Sys.ASD")
        precondition(ArchiveExtractor.decodeBestCjkText(Data(name.utf8)) == name)
        let malformed = ArchiveExtractor.decodeBestCjkText(Data([0xFF]) + Data("suffix.ain".utf8))
        precondition(malformed.hasSuffix("suffix.ain"))
        let archive = root.appendingPathComponent("sjis.zip")
        try zip(sjis, at: archive)
        try extract(archive, "sjis", expected: name)
        let traversal = root.appendingPathComponent("traversal.zip")
        try zip(Data("../escape.ain".utf8), at: traversal)
        do {
            try ArchiveExtractor.extract(traversal, to: root.appendingPathComponent("unsafe"))
            preconditionFailure("Traversal must fail")
        } catch ArchiveError.invalidPath {}
        precondition(!files.fileExists(atPath: root.appendingPathComponent("escape.ain").path))
        let safe = root.appendingPathComponent("safe")
        try files.createDirectory(at: safe, withIntermediateDirectories: true)
        try files.createSymbolicLink(atPath: safe.appendingPathComponent("link").path, withDestinationPath: root.path)
        do {
            _ = try ArchiveExtractor.outputPath("link/escape", size: 0, root: safe)
            preconditionFailure("Symlink escape must fail")
        } catch ArchiveError.invalidPath {}
        let metadata = try ArchiveExtractor.outputPath("__MACOSX/a", size: 0, root: safe)
        let dsStore = try ArchiveExtractor.outputPath("a/.DS_Store", size: 0, root: safe)
        let directory = try ArchiveExtractor.outputPath("./folder\\", size: 0, root: safe)
        precondition(metadata == nil && dsStore == nil && directory == nil)
        do {
            try ArchiveExtractor.checkSpace(Int64.max, at: safe)
            preconditionFailure("Oversized entry must fail")
        } catch ArchiveError.noSpace {}
        do {
            try ArchiveExtractor.extract(archive, to: root.appendingPathComponent("cancel"), checkCancelled: { throw ArchiveError.cancelled })
            preconditionFailure("Cancellation must propagate")
        } catch ArchiveError.cancelled {}
        let corrupt = root.appendingPathComponent("corrupt.zip")
        try Data("not an archive".utf8).write(to: corrupt)
        do {
            try ArchiveExtractor.extract(corrupt, to: root.appendingPathComponent("corrupt"))
            preconditionFailure("Corrupt input must fail")
        } catch ArchiveError.extraction {}
        let empty = root.appendingPathComponent("empty.zip")
        try Data([0x50, 0x4B, 0x05, 0x06] + [UInt8](repeating: 0, count: 18)).write(to: empty)
        let emptyCount = try ArchiveExtractor.extract(empty, to: root.appendingPathComponent("empty"))
        precondition(emptyCount == 0)
        let source = root.appendingPathComponent("source")
        try files.createDirectory(at: source, withIntermediateDirectories: true)
        try Data("game".utf8).write(to: source.appendingPathComponent("System40.ini"))
        try Data("game".utf8).write(to: source.appendingPathComponent("游戏.ain"))
        try files.linkItem(at: source.appendingPathComponent("System40.ini"), to: source.appendingPathComponent("linked.ini"))
        for format in ["7zip", "ustar", "gnutar"] {
            let url = root.appendingPathComponent(format + ".archive")
            var args = ["--format", format, "-cf", url.path, "-C", source.path, "."]
            if format == "gnutar" { args.insert("-z", at: 0) }
            try run("/opt/homebrew/opt/libarchive/bin/bsdtar", args)
            try extract(url, format)
        }
        let iso = root.appendingPathComponent("disc.bin")
        try run("/usr/bin/hdiutil", ["makehybrid", "-iso", "-joliet", "-o", iso.path, source.path])
        let actualISO = files.fileExists(atPath: iso.path) ? iso : URL(fileURLWithPath: iso.path + ".iso")
        let containsGame = try Iso9660Extractor.containsGameRoot(actualISO)
        precondition(containsGame)
        try extract(actualISO, "iso")
        let jolietBody = try Data(contentsOf: root.appendingPathComponent("out-iso/游戏.ain"))
        precondition(jolietBody == Data("game".utf8))
        let isoData = try Data(contentsOf: actualISO)
        for layout in Iso9660Extractor.layouts.dropFirst() {
            var mdf = Data()
            for start in stride(from: 0, to: isoData.count, by: 2048) {
                let sector = isoData.subdata(in: start..<min(start + 2048, isoData.count))
                mdf += Data(repeating: 0, count: layout.offset)
                mdf += sector
                mdf += Data(repeating: 0, count: layout.stride - layout.offset - sector.count)
            }
            let image = root.appendingPathComponent("disc-\(layout.stride)-\(layout.offset).mdf")
            try mdf.write(to: image)
            try extract(image, "\(layout.stride)-\(layout.offset)")
        }
        // Exercise multi-extent aggregation and repeated-directory protection using a minimal ISO.
        func record(_ name: String, lba: Int, size: Int, flags: UInt8) -> Data {
            let nameBytes = Array(name.utf8)
            var bytes = [UInt8](repeating: 0, count: 33 + nameBytes.count + (nameBytes.count % 2 == 0 ? 1 : 0))
            bytes[0] = UInt8(bytes.count)
            for i in 0..<4 {
                bytes[2 + i] = UInt8((lba >> (i * 8)) & 255)
                bytes[10 + i] = UInt8((size >> (i * 8)) & 255)
            }
            bytes[25] = flags
            bytes[32] = UInt8(nameBytes.count)
            bytes.replaceSubrange(33..<(33 + nameBytes.count), with: nameBytes)
            return Data(bytes)
        }
        func minimalISO(_ directory: Data) -> Data {
            var image = Data(repeating: 0, count: 23 * 2048)
            image[16 * 2048] = 1
            image.replaceSubrange((16 * 2048 + 1)..<(16 * 2048 + 6), with: Data("CD001".utf8))
            let rootRecord = record("root", lba: 20, size: 2048, flags: 2)
            image.replaceSubrange((16 * 2048 + 156)..<(16 * 2048 + 156 + rootRecord.count), with: rootRecord)
            image[17 * 2048] = 255
            image.replaceSubrange((17 * 2048 + 1)..<(17 * 2048 + 6), with: Data("CD001".utf8))
            image.replaceSubrange((20 * 2048)..<(20 * 2048 + directory.count), with: directory)
            image.replaceSubrange((21 * 2048)..<(21 * 2048 + 2), with: Data("ga".utf8))
            image.replaceSubrange((22 * 2048)..<(22 * 2048 + 2), with: Data("me".utf8))
            return image
        }
        let multi = root.appendingPathComponent("multi.iso")
        try minimalISO(record("System40.ini;1", lba: 21, size: 2, flags: 128) +
                       record("System40.ini;1", lba: 22, size: 2, flags: 0)).write(to: multi)
        try extract(multi, "multi")
        let cycle = root.appendingPathComponent("cycle.iso")
        try minimalISO(record("loop", lba: 20, size: 2048, flags: 2)).write(to: cycle)
        do {
            try ArchiveExtractor.extract(cycle, to: root.appendingPathComponent("cycle"))
            preconditionFailure("Repeated directory extents must fail")
        } catch ArchiveError.invalidImage {}
        let noGame = root.appendingPathComponent("no-game.iso")
        try minimalISO(Data()).write(to: noGame)
        let containsNoGame = try Iso9660Extractor.containsGameRoot(noGame)
        precondition(!containsNoGame)
        print("PASS: archive formats, strict UTF-8/CJK decoding, Shift-JIS ZIP, traversal/symlink rejection, metadata skipping, free-space rejection, cancellation, corrupt/empty archives, 7z/tar/tar.gz and hardlinks, Joliet ISO, multi-extent files, cyclic directories, empty game probe and 2352/2448 MDF layouts")
    }
}
