import Foundation
import ImageIO

enum GameExecutableIcon {
    static func image(in directory: URL) -> CGImage? {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey]
        )) ?? []
        let regular = files.filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }

        for file in regular where file.pathExtension.lowercased() == "ico" {
            guard let size = try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize,
                  size <= 32 * 1024 * 1024,
                  let source = CGImageSourceCreateWithURL(file as CFURL, nil),
                  let image = image(from: source) else { continue }
            return image
        }

        let helpers: Set<String> = ["opensavefolder.exe", "resetconfig.exe", "uninstaller.exe"]
        for file in regular where file.pathExtension.lowercased() == "exe" {
            guard !helpers.contains(file.lastPathComponent.lowercased()),
                  let data = extract(from: file),
                  let source = CGImageSourceCreateWithData(data as CFData, nil),
                  let image = image(from: source) else { continue }
            return image
        }
        return nil
    }

    private static func image(from source: CGImageSource) -> CGImage? {
        let sizes = (0..<min(CGImageSourceGetCount(source), 256)).compactMap { index -> (Int, Int)? in
            guard let info = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any],
                  let width = info[kCGImagePropertyPixelWidth] as? Int, (1...256).contains(width),
                  let height = info[kCGImagePropertyPixelHeight] as? Int, (1...256).contains(height) else { return nil }
            return (index, width * height)
        }.sorted { $0.1 > $1.1 }
        return sizes.lazy.compactMap { CGImageSourceCreateImageAtIndex(source, $0.0, nil) }.first
    }

    static func extract(from url: URL) -> Data? {
        guard let file = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? file.close() }
        guard let length = try? file.seekToEnd(), length <= UInt64(Int.max) else { return nil }

        func read(_ offset: Int, _ size: Int) -> Data? {
            guard offset >= 0, size >= 0, offset <= Int(length) - size else { return nil }
            do {
                try file.seek(toOffset: UInt64(offset))
                guard let data = try file.read(upToCount: size), data.count == size else { return nil }
                return data
            } catch { return nil }
        }

        guard let dos = read(0, 64), dos.iconLE(0, 2) == 0x5a4d else { return nil }
        let peOffset = dos.iconLE(60, 4)
        guard let pe = read(peOffset, 24), pe.iconLE(0, 4) == 0x4550 else { return nil }
        let count = pe.iconLE(6, 2), optionalSize = pe.iconLE(20, 2)
        guard (1...512).contains(count),
              let table = read(peOffset + 24 + optionalSize, count * 40) else { return nil }
        var resourceRVA = 0
        if let optional = read(peOffset + 24, min(optionalSize, 256)), optional.count >= 2 {
            let magic = optional.iconLE(0, 2)
            let directory = magic == 0x10b ? 96 : (magic == 0x20b ? 112 : 0)
            if directory > 0, optional.count >= directory + 24, optional.iconLE(directory - 4, 4) >= 3 {
                resourceRVA = optional.iconLE(directory + 16, 4)
            }
        }

        let sections = (0..<count).compactMap { index -> Section? in
            let base = index * 40
            let address = table.iconLE(base + 12, 4)
            let size = table.iconLE(base + 16, 4), start = table.iconLE(base + 20, 4)
            guard size > 0, start < Int(length) else { return nil }
            return Section(address: address, start: start, size: min(size, Int(length) - start),
                           isResource: table[base..<(base + 8)].elementsEqual([46, 114, 115, 114, 99, 0, 0, 0]))
        }
        let identified = resourceRVA == 0 ? nil : sections.first { $0.contains(resourceRVA, size: 16) }
        guard let resource = identified ?? sections.first(where: \.isResource) else { return nil }
        let root = identified == nil ? resource.start : resource.offset(of: resourceRVA)
        let end = resource.start + resource.size

        func resourceRead(_ offset: Int, _ size: Int) -> Data? {
            guard offset <= end - root - size else { return nil }
            return read(root + offset, size)
        }

        func entries(at offset: Int) -> [ResourceEntry] {
            guard let header = resourceRead(offset, 16) else { return [] }
            let count = header.iconLE(12, 2) + header.iconLE(14, 2)
            guard (1...4096).contains(count), let table = resourceRead(offset + 16, count * 8) else { return [] }
            return (0..<count).map { index in
                let name = table.iconLE(index * 8, 4), target = table.iconLE(index * 8 + 4, 4)
                return ResourceEntry(id: name, offset: target & 0x7fff_ffff, isDirectory: target & 0x8000_0000 != 0)
            }
        }

        func payload(_ entry: ResourceEntry, maxSize: Int) -> Data? {
            let leaf: ResourceEntry
            if entry.isDirectory {
                guard let language = entries(at: entry.offset).first(where: { !$0.isDirectory }) else { return nil }
                leaf = language
            } else {
                leaf = entry
            }
            guard let data = resourceRead(leaf.offset, 16) else { return nil }
            let address = data.iconLE(0, 4), size = data.iconLE(4, 4)
            guard size > 0, size <= maxSize,
                  let section = sections.first(where: { $0.contains(address, size: size) }) else { return nil }
            return read(section.offset(of: address), size)
        }

        let types = entries(at: 0)
        // RT_GROUP_ICON (14) references the image data stored in RT_ICON (3).
        guard let groups = types.first(where: { $0.id == 14 && $0.isDirectory }),
              let icons = types.first(where: { $0.id == 3 && $0.isDirectory }) else { return nil }
        let group = entries(at: groups.offset).lazy
            .compactMap { payload($0, maxSize: 6 + 256 * 14) }.first
        guard let group, group.count >= 6, group.iconLE(0, 2) == 0, group.iconLE(2, 2) == 1 else { return nil }
        let imageCount = group.iconLE(4, 2)
        guard (1...256).contains(imageCount), group.count >= 6 + imageCount * 14 else { return nil }

        let images = entries(at: icons.offset)
        var header = Data([0, 0, 1, 0, 0, 0])
        var contents = Data()
        var included = 0
        for i in 0..<imageCount {
            let index = 6 + i * 14
            let id = group.iconLE(index + 12, 2)
            guard let entry = images.first(where: { $0.id == id }),
                  let image = payload(entry, maxSize: min(16 * 1024 * 1024, 32 * 1024 * 1024 - contents.count))
            else { continue }
            header.append(group[index..<(index + 8)])
            header.appendIconLE(image.count)
            header.appendIconLE(contents.count)
            contents.append(image)
            included += 1
        }
        guard included > 0 else { return nil }
        header[4] = UInt8(truncatingIfNeeded: included)
        header[5] = UInt8(truncatingIfNeeded: included >> 8)
        for i in 0..<included {
            let offset = 6 + i * 16 + 12
            let start = header.iconLE(offset, 4) + header.count
            let bytes = (0..<4).map { UInt8(truncatingIfNeeded: start >> (8 * $0)) }
            header.replaceSubrange(offset..<(offset + 4), with: bytes)
        }
        header.append(contents)
        return header
    }

    private struct Section {
        let address: Int
        let start: Int
        let size: Int
        let isResource: Bool

        func contains(_ rva: Int, size requested: Int) -> Bool {
            rva >= address && rva - address <= size - requested
        }

        func offset(of rva: Int) -> Int { start + (rva - address) }
    }

    private struct ResourceEntry {
        let id: Int
        let offset: Int
        let isDirectory: Bool
    }
}

private extension Data {
    func iconLE(_ offset: Int, _ count: Int) -> Int {
        (0..<count).reduce(0) { result, i in result | (Int(self[offset + i]) << (i * 8)) }
    }

    mutating func appendIconLE(_ value: Int) {
        for i in 0..<4 { append(UInt8(truncatingIfNeeded: value >> (i * 8))) }
    }
}
