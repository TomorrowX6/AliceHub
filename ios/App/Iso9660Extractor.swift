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
                    if name == "alicestart.ini" || name == "alicestart2.ini" || name == "system40.ini" ||
                        (!name.hasPrefix(".") && name != "system39.ain" && name.hasSuffix(".ain")) { return true }
                }
            }
        }
        return false
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
        var tasks: [(String, URL, [Extent])] = []
        var total: Int64 = 0
        while index < queue.count {
            try checkCancelled()
            let (prefix, extent) = queue[index]; index += 1
            guard visited.insert(extent.lba).inserted else { throw ArchiveError.invalidImage }
            for entry in try entries(directoryBytes(file, desc.layout, extent), joliet: desc.joliet) {
                try checkCancelled()
                let relative = prefix.isEmpty ? entry.name : prefix + "/" + entry.name
                let size = entry.extents.reduce(Int64(0)) { $0 + $1.size }
                let path = try ArchiveExtractor.outputPath(relative + (entry.directory ? "/" : ""),
                                                           size: entry.directory ? 0 : size,
                                                           root: destination, shouldSkip: shouldSkip)
                if entry.directory {
                    if !shouldSkip(relative) && !relative.split(separator: "/").contains("__MACOSX") &&
                        !relative.split(separator: "/").contains(".DS_Store") {
                        queue.append((relative, entry.extents[0]))
                    }
                } else if let path = path {
                    tasks.append((relative, path, entry.extents))
                    total += size
                }
            }
        }
        try ArchiveExtractor.checkSpace(total, at: destination)
        var done: Int64 = 0
        for (name, path, extents) in tasks {
            try checkCancelled()
            try ArchiveExtractor.checkSpace(extents.reduce(0) { $0 + $1.size }, at: destination)
            guard FileManager.default.createFile(atPath: path.path, contents: nil) else {
                throw ArchiveError.extraction("无法创建文件：\(name)")
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
