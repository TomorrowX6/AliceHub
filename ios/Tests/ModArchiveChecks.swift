import Foundation

@main
struct ModArchiveChecks {
    static func main() throws {
        setbuf(stdout, nil)
        let root = URL(fileURLWithPath: CommandLine.arguments[1])
        let files = FileManager.default
        let output = root.appendingPathComponent("output")
        var total: Int64 = 0
        let count = try ModArchiveExtractor.extract(file: root.appendingPathComponent("names.zip"), to: output,
            shouldSkip: { $0 == "custom.txt" }, progress: { total = $0; _ = $1 })
        precondition(count == 4)
        print("Extracted names:", try files.contentsOfDirectory(atPath: output.path))
        for name in ["中文游戏.txt", "ソフト.txt", ".xsys35rc", "empty.txt"] {
            precondition(files.fileExists(atPath: output.appendingPathComponent(name).path), name)
        }
        precondition(total == 3)
        precondition(!files.fileExists(atPath: output.appendingPathComponent("SaveData").path))
        precondition(ModArchiveExtractor.decodeEntryPath(Data("愴崙儔儞僗_Sys.ASD".utf8)) == "戦国ランス_Sys.ASD")
        let emptyCount = try ModArchiveExtractor.extract(file: root.appendingPathComponent("empty.zip"), to: output)
        precondition(emptyCount == 0)
        for name in ["traversal", "absolute", "drive", "corrupt"] {
            do {
                try ModArchiveExtractor.extract(file: root.appendingPathComponent(name + ".zip"), to: output)
                preconditionFailure("Accepted \(name)")
            } catch is ArchiveExtractionError {}
        }
        do {
            try ModArchiveExtractor.extract(file: root.appendingPathComponent("names.zip"), to: output, isCancelled: { true })
            preconditionFailure("Ignored cancellation")
        } catch is CancellationError {}
        let outside = root.appendingPathComponent("outside")
        try files.createDirectory(at: outside, withIntermediateDirectories: true)
        try files.createSymbolicLink(at: output.appendingPathComponent("link"), withDestinationURL: outside)
        do {
            try ModArchiveExtractor.extract(file: root.appendingPathComponent("symlink.zip"), to: output)
            preconditionFailure("Followed escaping symlink")
        } catch is ArchiveExtractionError {}
        precondition(ModArchiveExtractor.isSupportedArchiveName("A.TAR.XZ"))
        precondition(!ModArchiveExtractor.isSupportedArchiveName("a.exe"))
        let linkCount = try ModArchiveExtractor.extract(file: root.appendingPathComponent("links.tar"), to: output)
        precondition(linkCount == 2)
        let copied = try Data(contentsOf: output.appendingPathComponent("copy.txt"))
        precondition(copied == Data("data".utf8))
        do {
            try ModArchiveExtractor.extract(file: root.appendingPathComponent("bad-links.tar"), to: output)
            preconditionFailure("Accepted escaping hard link")
        } catch is ArchiveExtractionError {}
        print("PASS: GBK/Shift-JIS ZIP names, mojibake repair, empty archive/file, skip rules, progress, traversal/absolute/drive/symlink rejection, corrupt archive, cancellation, TAR hard links")
    }
}
