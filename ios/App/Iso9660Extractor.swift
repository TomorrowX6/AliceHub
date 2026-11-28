import Foundation

enum Iso9660Extractor {
    struct Layout { let stride: Int; let offset: Int }
    struct Extent { let lba: Int64; let size: Int64 }
    struct Descriptor { let layout: Layout; let root: Extent; let joliet: Bool }
    struct Entry {
        let name: String
        let directory: Bool
        var extents: [Extent]
        var multi: Bool
    }
    static let layouts = [Layout(stride: 2048, offset: 0), Layout(stride: 2352, offset: 16),
                          Layout(stride: 2352, offset: 24), Layout(stride: 2448, offset: 16),
                          Layout(stride: 2448, offset: 24)]

    static func read(_ file: FileHandle, offset: Int64, count: Int) throws -> Data {
        guard offset >= 0, count >= 0 else { throw ArchiveError.invalidImage }
        try file.seek(toOffset: UInt64(offset))
        let data = try file.read(upToCount: count) ?? Data()
        guard data.count == count else { throw ArchiveError.invalidImage }
        return data
    }

    static func le32(_ data: Data, _ offset: Int) -> Int64 {
        (0..<4).reduce(Int64(0)) { $0 | Int64(data[offset + $1]) << ($1 * 8) }
    }

    static func descriptor(_ file: FileHandle) throws -> Descriptor? {
        for layout in layouts {
            guard let header = try? read(file, offset: Int64(16 * layout.stride + layout.offset), count: 7),
                  header.subdata(in: 1..<6) == Data("CD001".utf8) else { continue }
            var primary: Extent?, joliet: Extent?
            for sector in 16...64 {
                let data = try read(file, offset: Int64(sector * layout.stride + layout.offset), count: 2048)
                guard data.subdata(in: 1..<6) == Data("CD001".utf8) else { break }
                if data[0] == 255 { break }
                if data[0] == 1 { primary = Extent(lba: le32(data, 158), size: le32(data, 166)) }
                if data[0] == 2 && data[88] == 0x25 && data[89] == 0x2F && [0x40, 0x43, 0x45].contains(data[90]) {
                    joliet = Extent(lba: le32(data, 158), size: le32(data, 166))
                }
            }
            if let root = joliet ?? primary, root.size > 0 {
                return Descriptor(layout: layout, root: root, joliet: joliet != nil)
            }
        }
        return nil
    }

    static func isDiscImage(_ source: URL) throws -> Bool {
        let file = try FileHandle(forReadingFrom: source)
        defer { try? file.close() }
        return try descriptor(file) != nil
    }

    static func directoryBytes(_ file: FileHandle, _ layout: Layout, _ extent: Extent) throws -> Data {
        var data = Data()
        var remaining = min(extent.size, 32 * 1024 * 1024), lba = extent.lba
        while remaining > 0 {
            let count = Int(min(remaining, 2048))
            data.append(try read(file, offset: lba * Int64(layout.stride) + Int64(layout.offset), count: count))
            remaining -= Int64(count)
            lba += 1
        }
        return data
    }

    static func entries(_ data: Data, joliet: Bool) throws -> [Entry] {
        var result = [Entry](), pos = 0
        while pos + 33 <= data.count {
            let length = Int(data[pos])
            if length == 0 { pos = (pos / 2048 + 1) * 2048; continue }
            guard length >= 33, pos + length <= data.count else { throw ArchiveError.invalidImage }
            let idLength = Int(data[pos + 32])
            guard pos + 33 + idLength <= pos + length else { throw ArchiveError.invalidImage }
            let id = data.subdata(in: (pos + 33)..<(pos + 33 + idLength))
            defer { pos += length }
            if id.isEmpty || (id.count == 1 && (id[0] == 0 || id[0] == 1)) { continue }
            var name = joliet ? (String(data: id, encoding: .utf16BigEndian) ?? "") :
                ArchiveExtractor.decodeBestCjkText(id, preferGbkOnTie: false)
            while name.hasSuffix("\0") { name.removeLast() }
            if let semi = name.lastIndex(of: ";"), semi != name.startIndex,
               name[name.index(after: semi)...].allSatisfy({ $0 >= "0" && $0 <= "9" }) {
                name = String(name[..<semi])
            }
            if name.isEmpty || name == "." || name == ".." || name.contains("/") || name.contains("\\") || name.contains("\0") { continue }
            let flags = data[pos + 25], directory = flags & 2 != 0, multi = flags & 128 != 0
            let extent = Extent(lba: le32(data, pos + 2), size: le32(data, pos + 10))
            if !directory, let last = result.last, !last.directory, last.multi, last.name == name {
                result[result.count - 1].extents.append(extent)
                result[result.count - 1].multi = multi
            } else { result.append(Entry(name: name, directory: directory, extents: [extent], multi: multi)) }
        }
        return result
    }

    static func containsGameRoot(_ source: URL, checkCancelled: () throws -> Void = {}) throws -> Bool {
        let file = try FileHandle(forReadingFrom: source)
        defer { try? file.close() }
        guard let desc = try descriptor(file) else { return false }
        var queue = [desc.root], visited = Set<Int64>(), index = 0
        while index < queue.count {
            try checkCancelled()
            let extent = queue[index]; index += 1
            guard visited.insert(extent.lba).inserted else { throw ArchiveError.invalidImage }
            for entry in try entries(directoryBytes(file, desc.layout, extent), joliet: desc.joliet) {
                if entry.directory { queue.append(entry.extents[0]) }
                else {
                    let name = entry.name.lowercased()
                    if name == "alicestart.chs" || name == "alicestart.ini" || name == "alicestart2.ini" || name == "system40.ini" ||
                        (!name.hasPrefix(".") && name != "system39.ain" && name.hasSuffix(".ain")) { return true }
                }
            }
        }
        return false
    }

    private static func sumExtents(_ extents: [Extent]) throws -> Int64 {
        var sum: Int64 = 0
        for extent in extents {
            guard extent.size >= 0 else { throw ArchiveError.invalidImage }
            let (next, overflow) = sum.addingReportingOverflow(extent.size)
            guard !overflow else { throw ArchiveError.invalidImage }
            sum = next
        }
        return sum
    }

    @discardableResult
    static func extract(_ source: URL, to destination: URL,
                        shouldSkip: (String) -> Bool = { _ in false },
                        checkCancelled: () throws -> Void = {},
                        onProgress: (Int64, String) -> Void = { _, _ in }) throws -> Int {
        let file = try FileHandle(forReadingFrom: source)
        defer { try? file.close() }
        guard let desc = try descriptor(file) else { throw ArchiveError.invalidImage }
        try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
        var queue: [(String, Extent)] = [("", desc.root)], visited = Set<Int64>(), index = 0
        var tasks: [(String, URL, [Extent], Int64)] = []
        var total: Int64 = 0
        while index < queue.count {
            try checkCancelled()
            let (prefix, extent) = queue[index]; index += 1
            guard visited.insert(extent.lba).inserted else { throw ArchiveError.invalidImage }
            for entry in try entries(directoryBytes(file, desc.layout, extent), joliet: desc.joliet) {
                try checkCancelled()
                let relative = prefix.isEmpty ? entry.name : prefix + "/" + entry.name
                let size = try sumExtents(entry.extents)
                let path = try ArchiveExtractor.outputPath(relative + (entry.directory ? "/" : ""),
                                                           size: entry.directory ? 0 : size,
                                                           root: destination, shouldSkip: shouldSkip)
                if entry.directory {
                    if !shouldSkip(relative) && !relative.split(separator: "/").contains("__MACOSX") &&
                        !relative.split(separator: "/").contains(".DS_Store") {
                        queue.append((relative, entry.extents[0]))
                    }
                } else if let path = path {
                    let (nextTotal, overflow) = total.addingReportingOverflow(size)
                    guard !overflow else { throw ArchiveError.invalidImage }
                    total = nextTotal
                    tasks.append((relative, path, entry.extents, size))
                }
            }
        }
        try ArchiveExtractor.checkSpace(total, at: destination)
        var done: Int64 = 0
        for (name, path, extents, size) in tasks {
            try checkCancelled()
            try ArchiveExtractor.checkSpace(size, at: destination)
            guard FileManager.default.createFile(atPath: path.path, contents: nil) else {
                throw ArchiveError.extraction(hubText("无法创建文件：%@", name))
            }
            let output = try FileHandle(forWritingTo: path)
            do {
                for extent in extents {
                    var remaining = extent.size, lba = extent.lba
                    while remaining > 0 {
                        try checkCancelled()
                        let count = Int(min(remaining, desc.layout.stride == 2048 ? 256 * 1024 : 2048))
                        let chunk = try read(file, offset: lba * Int64(desc.layout.stride) + Int64(desc.layout.offset), count: count)
                        try output.write(contentsOf: chunk)
                        remaining -= Int64(count)
                        lba += Int64((count + 2047) / 2048)
                        done += Int64(count)
                        onProgress(done, name)
                    }
                }
                try output.close()
            } catch {
                try? output.close()
                try? FileManager.default.removeItem(at: path)
                throw error
            }
        }
        return tasks.count
    }
}

enum Pc98FloppyExtractor {
    private static let maxHeaderBytes: Int64 = 65536
    private static let maxFloppyBytes: Int64 = 32 * 1024 * 1024
    private static let maxDepth = 16
    private static let dosBootFiles: Set<String> = ["io.sys", "msdos.sys", "command.com", "autoexec.bat", "config.sys"]

    private struct Volume {
        let headerSize: Int64
        let fddSize: Int64
        let bps: Int
        let spc: Int
        let rootOffset: Int64
        let rootBytes: Int
        let dataOffset: Int64
        let totalClusters: Int
        let isFat16: Bool
        let fat: Data
    }

    private struct FatEntry {
        let name: String
        let isDir: Bool
        let startCluster: Int
        let size: Int64
    }

    private static func le16(_ data: Data, _ offset: Int) -> Int {
        Int(data[offset]) | (Int(data[offset + 1]) << 8)
    }

    private static func le32(_ data: Data, _ offset: Int) -> Int64 {
        (0..<4).reduce(Int64(0)) { $0 | Int64(data[offset + $1]) << ($1 * 8) }
    }

    private static func le32Signed(_ data: Data, _ offset: Int) -> Int32 {
        Int32(truncatingIfNeeded: le32(data, offset))
    }

    private static func readExact(_ file: FileHandle, offset: Int64, count: Int) -> Data? {
        guard offset >= 0, count >= 0 else { return nil }
        do {
            try file.seek(toOffset: UInt64(offset))
            let data = try file.read(upToCount: count) ?? Data()
            return data.count == count ? data : nil
        } catch {
            return nil
        }
    }

    private static func fileSize(_ file: FileHandle) -> Int64 {
        (try? Int64(file.seekToEnd())) ?? 0
    }

    private static func openVolume(_ file: FileHandle) -> Volume? {
        let totalSize = fileSize(file)
        guard totalSize >= 512, let hdr = readExact(file, offset: 0, count: 32) else { return nil }
        let zero = le32(hdr, 0)
        let fddType = le32(hdr, 4)
        let headerSize = le32(hdr, 8)
        let fddSize = le32(hdr, 12)
        let sectorSize = le32(hdr, 16)
        let sectors = le32(hdr, 20)
        let heads = le32(hdr, 24)
        let cyls = le32(hdr, 28)

        guard zero == 0, fddType > 0, fddType <= 0xFF,
              headerSize >= 32, headerSize <= maxHeaderBytes,
              fddSize > 0, fddSize <= maxFloppyBytes,
              headerSize + fddSize <= totalSize,
              [256, 512, 1024].contains(sectorSize),
              (1...64).contains(sectors), (1...2).contains(heads), (1...256).contains(cyls) else { return nil }
        let geom = sectorSize * sectors * heads * cyls
        guard geom > 0, geom <= fddSize,
              let boot = readExact(file, offset: headerSize, count: 64) else { return nil }

        let bps = le16(boot, 11)
        let spc = Int(boot[13])
        let rsv = le16(boot, 14)
        let nfats = Int(boot[16])
        let rootEnt = le16(boot, 17)
        let tot16 = le16(boot, 19)
        let spf = le16(boot, 22)
        let tot32 = le32(boot, 32)

        guard [256, 512, 1024].contains(bps),
              spc > 0, spc <= 128, (spc & (spc - 1)) == 0,
              rsv >= 1, (1...2).contains(nfats), rootEnt >= 1, spf >= 1 else { return nil }

        let totSectors = tot16 != 0 ? Int64(tot16) : tot32
        guard totSectors > 0, totSectors * Int64(bps) <= fddSize else { return nil }

        let fatOffset = headerSize + Int64(rsv * bps)
        let fatBytes = spf * bps
        let rootOffset = fatOffset + Int64(nfats * fatBytes)
        let rootBytes = rootEnt * 32
        let rootSecs = (rootBytes + bps - 1) / bps
        let dataOffset = rootOffset + Int64(rootSecs * bps)
        let nonData = Int64(rsv + nfats * spf + rootSecs)
        guard totSectors > nonData, dataOffset < headerSize + fddSize else { return nil }
        let totalClusters = Int((totSectors - nonData) / Int64(spc))
        guard totalClusters >= 2, totalClusters < 65525,
              let fat = readExact(file, offset: fatOffset, count: fatBytes) else { return nil }

        return Volume(
            headerSize: headerSize, fddSize: fddSize, bps: bps, spc: spc,
            rootOffset: rootOffset, rootBytes: rootBytes, dataOffset: dataOffset,
            totalClusters: totalClusters, isFat16: totalClusters >= 4085, fat: fat
        )
    }

    static func isFloppyImage(_ source: URL) -> Bool {
        guard let file = try? FileHandle(forReadingFrom: source) else { return false }
        defer { try? file.close() }
        return openVolume(file) != nil
    }

    static func isValidPc98FontBmp(_ url: URL) -> Bool {
        guard let file = try? FileHandle(forReadingFrom: url) else { return false }
        defer { try? file.close() }
        let total = fileSize(file)
        guard total >= 62 + 256 * 2048, let hdr = readExact(file, offset: 0, count: 62) else { return false }
        guard hdr[0] == 0x42, hdr[1] == 0x4D else { return false }
        let dataOff = le32(hdr, 10)
        let dibSize = le32(hdr, 14)
        let width = le32Signed(hdr, 18)
        let height = le32Signed(hdr, 22)
        let planes = le16(hdr, 26)
        let bpp = le16(hdr, 28)
        let compression = le32(hdr, 30)
        return dibSize >= 40 && width == 2048 && (height == 2048 || height == -2048) &&
            planes == 1 && bpp == 1 && compression == 0 &&
            dataOff >= 14 + dibSize + 8 && dataOff + 256 * 2048 <= total
    }

    static func isChinesePc98FontBmp(_ url: URL) -> Bool {
        guard isValidPc98FontBmp(url), let file = try? FileHandle(forReadingFrom: url) else { return false }
        defer { try? file.close() }
        guard let hdr = readExact(file, offset: 0, count: 62) else { return false }
        let dataOff = le32(hdr, 10)
        let dibSize = le32(hdr, 14)
        let height = le32Signed(hdr, 22)
        guard let pal = readExact(file, offset: 14 + dibSize, count: 8),
              let raw = readExact(file, offset: dataOff, count: 256 * 2048) else { return false }
        let lum0 = Int(pal[2]) * 30 + Int(pal[1]) * 59 + Int(pal[0]) * 11
        let lum1 = Int(pal[6]) * 30 + Int(pal[5]) * 59 + Int(pal[4]) * 11
        let bg: UInt8 = lum0 < lum1 ? 0xFF : 0x00
        let topDown = height < 0
        var occupied = 0
        raw.withUnsafeBytes { buf in
            let p = buf.bindMemory(to: UInt8.self)
            for cx in 85...87 {
                let colByte = cx * 2
                for ry in 0x21...0x7E {
                    let gy = ry * 16
                    var nonEmpty = false
                    for dy in 0..<16 {
                        let y = gy + dy
                        let srcY = topDown ? y : (2047 - y)
                        let rowOff = srcY * 256 + colByte
                        if p[rowOff] != bg || p[rowOff + 1] != bg {
                            nonEmpty = true
                            break
                        }
                    }
                    if nonEmpty { occupied += 1 }
                }
            }
        }
        return occupied >= 32
    }

    static func copyCompanionFontIfNeeded(searchRoot: URL, into gameRoot: URL) {
        let fm = FileManager.default
        if let existing = try? fm.contentsOfDirectory(at: gameRoot, includingPropertiesForKeys: nil),
           existing.contains(where: { $0.lastPathComponent.lowercased() == "font.bmp" }) {
            return
        }
        let targetCanonical = gameRoot.resolvingSymlinksInPath().standardizedFileURL.path
        guard let enumerator = fm.enumerator(at: searchRoot, includingPropertiesForKeys: [.isRegularFileKey]) else { return }
        for case let url as URL in enumerator {
            guard url.lastPathComponent.lowercased() == "font.bmp",
                  (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true,
                  !url.resolvingSymlinksInPath().standardizedFileURL.path.hasPrefix(targetCanonical + "/"),
                  isValidPc98FontBmp(url) else { continue }
            let dest = gameRoot.appendingPathComponent("FONT.bmp")
            if !fm.fileExists(atPath: dest.path) {
                try? fm.copyItem(at: url, to: dest)
            }
            return
        }
    }

    private static func nextCluster(_ vol: Volume, _ c: Int) -> Int {
        if vol.isFat16 {
            let off = c * 2
            return off + 2 > vol.fat.count ? 0xFFFF : le16(vol.fat, off)
        }
        let off = (c * 3) / 2
        if off + 2 > vol.fat.count { return 0xFFF }
        let raw = le16(vol.fat, off)
        return (c & 1) != 0 ? (raw >> 4) : (raw & 0x0FFF)
    }

    private static func readChain(_ file: FileHandle, _ vol: Volume, start: Int, limit: Int64?,
                                  checkCancelled: () throws -> Void) throws -> Data {
        let clusterBytes = vol.spc * vol.bps
        let maxBytes = limit ?? vol.fddSize
        let eof = vol.isFat16 ? 0xFFF8 : 0x0FF8
        var out = Data()
        var visited = Set<Int>()
        var c = start
        while c >= 2 && c < eof && Int64(out.count) < maxBytes {
            try checkCancelled()
            if c >= vol.totalClusters + 2 { break }
            guard visited.insert(c).inserted else { throw ArchiveError.invalidImage }
            let pos = vol.dataOffset + Int64(c - 2) * Int64(clusterBytes)
            let count = Int(min(Int64(clusterBytes), maxBytes - Int64(out.count)))
            guard let chunk = readExact(file, offset: pos, count: count) else { throw ArchiveError.invalidImage }
            out.append(chunk)
            c = nextCluster(vol, c)
        }
        return out
    }

    private static func parseDirectory(_ data: Data) -> [FatEntry] {
        var result: [FatEntry] = []
        var off = 0
        while off + 32 <= data.count {
            let first = data[off]
            if first == 0x00 { break }
            if first == 0xE5 { off += 32; continue }
            let attr = data[off + 11]
            if attr == 0x0F || (attr & 0x08) != 0 { off += 32; continue }
            var nameRaw = Array(data[off..<(off + 8)])
            if nameRaw[0] == 0x05 { nameRaw[0] = 0xE5 }
            let extRaw = Array(data[(off + 8)..<(off + 11)])
            let nTrim = nameRaw.reversed().drop(while: { $0 == 0x20 }).reversed()
            let eTrim = extRaw.reversed().drop(while: { $0 == 0x20 }).reversed()
            if !nTrim.isEmpty {
                var raw = Data(nTrim)
                if !eTrim.isEmpty {
                    raw.append(0x2E)
                    raw.append(contentsOf: eTrim)
                }
                let name = (String(data: raw, encoding: .shiftJIS) ?? ArchiveExtractor.decodeBestCjkText(raw, preferGbkOnTie: false))
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !name.isEmpty && name != "." && name != ".." && !name.contains("/") && !name.contains("\\") && !name.contains("\0") {
                    let isDir = (attr & 0x10) != 0
                    let start = le16(data, off + 26)
                    let size = isDir ? 0 : le32(data, off + 28)
                    result.append(FatEntry(name: name, isDir: isDir, startCluster: start, size: size))
                }
            }
            off += 32
        }
        return result
    }

    @discardableResult
    static func extract(_ source: URL, to destination: URL,
                        shouldSkip: (String) -> Bool = { _ in false },
                        checkCancelled: () throws -> Void = {},
                        onProgress: (Int64, String) -> Void = { _, _ in }) throws -> Int {
        let file = try FileHandle(forReadingFrom: source)
        defer { try? file.close() }
        guard let vol = openVolume(file),
              let rootBytes = readExact(file, offset: vol.rootOffset, count: vol.rootBytes) else {
            throw ArchiveError.invalidImage
        }
        try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
        var tasks: [(String, URL, FatEntry)] = []
        var total: Int64 = 0
        var visitedDirs = Set<Int>()

        func walk(_ dirBytes: Data, prefix: String, depth: Int) throws {
            guard depth <= maxDepth else { throw ArchiveError.invalidImage }
            for entry in parseDirectory(dirBytes) {
                try checkCancelled()
                let relative = prefix.isEmpty ? entry.name : prefix + "/" + entry.name
                if !entry.isDir && depth == 0 && dosBootFiles.contains(entry.name.lowercased()) { continue }
                let path = try ArchiveExtractor.outputPath(relative + (entry.isDir ? "/" : ""),
                                                           size: entry.isDir ? 0 : entry.size,
                                                           root: destination, shouldSkip: shouldSkip)
                if entry.isDir {
                    if !shouldSkip(relative) && entry.startCluster >= 2 {
                        guard visitedDirs.insert(entry.startCluster).inserted else { throw ArchiveError.invalidImage }
                        let sub = try readChain(file, vol, start: entry.startCluster, limit: nil, checkCancelled: checkCancelled)
                        try walk(sub, prefix: relative, depth: depth + 1)
                    }
                } else if let path {
                    guard entry.size <= vol.fddSize else { throw ArchiveError.invalidImage }
                    total += entry.size
                    guard total <= vol.fddSize * 2 else { throw ArchiveError.invalidImage }
                    tasks.append((relative, path, entry))
                }
            }
        }

        try walk(rootBytes, prefix: "", depth: 0)
        try ArchiveExtractor.checkSpace(total, at: destination)
        var done: Int64 = 0
        for (name, path, entry) in tasks {
            try checkCancelled()
            let payload: Data
            if entry.size == 0 || entry.startCluster < 2 {
                payload = Data()
            } else {
                payload = try readChain(file, vol, start: entry.startCluster, limit: entry.size, checkCancelled: checkCancelled)
            }
            try payload.write(to: path, options: .atomic)
            done += Int64(payload.count)
            onProgress(done, name)
        }
        return tasks.count
    }
}
