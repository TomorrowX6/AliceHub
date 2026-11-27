import Foundation

enum SaveZip {
    struct Entry {
        var name: String
        var data: Data
        var modified: Date = Date()
    }
    enum Failure: LocalizedError {
        case invalidArchive, unsupportedArchive
        var errorDescription: String? {
            switch self {
            case .invalidArchive: return hubText("ZIP 压缩包损坏或校验失败。")
            case .unsupportedArchive: return hubText("不支持此 ZIP 格式（仅支持未加密的普通 ZIP）。")
            }
        }
    }

    private static func crc(_ data: Data) -> UInt32 {
        data.withUnsafeBytes { UInt32(crc32(0, $0.bindMemory(to: Bytef.self).baseAddress, uInt(data.count))) }
    }

    private static func transform(_ data: Data, size: Int? = nil) throws -> Data {
        var stream = z_stream()
        let reading = size != nil
        let status = reading
            ? inflateInit2_(&stream, -MAX_WBITS, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
            : deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, -MAX_WBITS, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard status == Z_OK else { throw Failure.invalidArchive }
        defer { if reading { inflateEnd(&stream) } else { deflateEnd(&stream) } }
        var output = Data(count: max(1, size ?? Int(deflateBound(&stream, uLong(data.count)))))
        let capacity = output.count
        let result = data.withUnsafeBytes { input in
            output.withUnsafeMutableBytes { target -> Int32 in
                stream.next_in = UnsafeMutablePointer(mutating: input.bindMemory(to: Bytef.self).baseAddress)
                stream.avail_in = uInt(data.count)
                stream.next_out = target.bindMemory(to: Bytef.self).baseAddress
                stream.avail_out = uInt(capacity)
                return reading ? inflate(&stream, Z_FINISH) : deflate(&stream, Z_FINISH)
            }
        }
        guard result == Z_STREAM_END, stream.total_in == data.count,
              size.map({ Int(stream.total_out) == $0 }) ?? true else { throw Failure.invalidArchive }
        output.count = Int(stream.total_out)
        return output
    }

    private static let gregorian = Calendar(identifier: .gregorian)
    private static let maxTotalDecompressedBytes = 512 * 1024 * 1024

    private static func dos(_ date: Date) -> (UInt16, UInt16) {
        let c = gregorian.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        return (UInt16((c.hour! << 11) | (c.minute! << 5) | (c.second! / 2)),
                UInt16(((min(2107, max(1980, c.year!)) - 1980) << 9) | (c.month! << 5) | c.day!))
    }

    static func write(_ entries: [Entry], to url: URL) throws {
        var out = Data(), central = Data()
        guard entries.count < 65535 else { throw Failure.unsupportedArchive }
        for entry in entries {
            let name = Data(entry.name.utf8), packed = try transform(entry.data)
            guard name.count <= 65535, entry.data.count < Int(UInt32.max), packed.count < Int(UInt32.max), out.count < Int(UInt32.max) else { throw Failure.unsupportedArchive }
            let offset = out.count, checksum = crc(entry.data), (time, date) = dos(entry.modified)
            out.put32(0x04034b50); out.put16(20); out.put16(0x800); out.put16(8)
            out.put16(time); out.put16(date); out.put32(checksum)
            out.put32(UInt32(packed.count)); out.put32(UInt32(entry.data.count)); out.put16(UInt16(name.count)); out.put16(0)
            out.append(name); out.append(packed)
            central.put32(0x02014b50); central.put16(20); central.put16(20); central.put16(0x800); central.put16(8)
            central.put16(time); central.put16(date); central.put32(checksum)
            central.put32(UInt32(packed.count)); central.put32(UInt32(entry.data.count)); central.put16(UInt16(name.count))
            central.put16(0); central.put16(0); central.put16(0); central.put16(0); central.put32(0); central.put32(UInt32(offset)); central.append(name)
        }
        let offset = out.count
        guard offset < Int(UInt32.max), central.count < Int(UInt32.max) else { throw Failure.unsupportedArchive }
        out.append(central)
        out.put32(0x06054b50); out.put16(0); out.put16(0); out.put16(UInt16(entries.count)); out.put16(UInt16(entries.count))
        out.put32(UInt32(central.count)); out.put32(UInt32(offset)); out.put16(0)
        try out.write(to: url, options: .atomic)
    }

    static func read(_ url: URL) throws -> [Entry] {
        let bytes = try Data(contentsOf: url)
        func n(_ p: Int, _ length: Int) throws -> Int {
            guard p >= 0, length <= bytes.count, p <= bytes.count - length else { throw Failure.invalidArchive }
            return (0..<length).reduce(0) { $0 | (Int(bytes[p + $1]) << ($1 * 8)) }
        }
        guard bytes.count >= 22 else { throw Failure.invalidArchive }
        guard let end = try stride(from: bytes.count - 22, through: max(0, bytes.count - 65557), by: -1).first(where: {
            try n($0, 4) == 0x06054b50 && $0 + 22 + n($0 + 20, 2) == bytes.count
        }) else { throw Failure.invalidArchive }
        guard try n(end + 4, 2) == 0, try n(end + 6, 2) == 0,
              try n(end + 8, 2) == n(end + 10, 2) else { throw Failure.unsupportedArchive }
        let count = try n(end + 10, 2), centralSize = try n(end + 12, 4)
        var p = try n(end + 16, 4)
        guard count < 65535, p + centralSize == end else { throw Failure.invalidArchive }
        var entries: [Entry] = []
        var totalDecompressed = 0
        for _ in 0..<count {
            guard try n(p, 4) == 0x02014b50 else { throw Failure.invalidArchive }
            let flags = try n(p + 8, 2), method = try n(p + 10, 2)
            guard flags & 1 == 0, method == 0 || method == 8, try n(p + 34, 2) == 0 else { throw Failure.unsupportedArchive }
            let checksum = try n(p + 16, 4), packed = try n(p + 20, 4), size = try n(p + 24, 4)
            let nameSize = try n(p + 28, 2), extra = try n(p + 30, 2), comment = try n(p + 32, 2)
            guard size <= 256 * 1024 * 1024,
                  size <= Self.maxTotalDecompressedBytes - totalDecompressed,
                  p + 46 + nameSize + extra + comment <= end,
                  let name = String(data: bytes.subdata(in: p + 46..<p + 46 + nameSize), encoding: .utf8) else { throw Failure.invalidArchive }
            let local = try n(p + 42, 4)
            guard try n(local, 4) == 0x04034b50, try n(local + 8, 2) == method,
                  try n(local + 6, 2) == flags else { throw Failure.invalidArchive }
            let start = try local + 30 + n(local + 26, 2) + n(local + 28, 2)
            guard start <= bytes.count, packed <= bytes.count - start else { throw Failure.invalidArchive }
            let compressed = bytes.subdata(in: start..<start + packed)
            let data = method == 0 ? compressed : try transform(compressed, size: size)
            guard data.count == size, crc(data) == checksum else { throw Failure.invalidArchive }
            totalDecompressed += data.count
            let time = try n(p + 12, 2), date = try n(p + 14, 2)
            let modified = gregorian.date(from: DateComponents(year: 1980 + (date >> 9), month: (date >> 5) & 15, day: date & 31, hour: time >> 11, minute: (time >> 5) & 63, second: (time & 31) * 2)) ?? Date(timeIntervalSince1970: 0)
            entries.append(Entry(name: name, data: data, modified: modified))
            p += 46 + nameSize + extra + comment
        }
        guard p == end else { throw Failure.invalidArchive }
        return entries
    }
}

private extension Data {
    mutating func put16(_ value: UInt16) { append(UInt8(value & 255)); append(UInt8(value >> 8)) }
    mutating func put32(_ value: UInt32) { put16(UInt16(value & 65535)); put16(UInt16(value >> 16)) }
}
