import Foundation

enum UpdateCheck {
    static let storageKey = "hub.checkForUpdates"
    private static let api = "https://api.github.com/repos/TomorrowX6/AliceHub"

    private struct Release: Decodable {
        let tag_name: String
    }

    private struct Comparison: Decodable {
        let status: String
    }

    static func releaseURL(tag: String) -> URL {
        URL(string: "https://github.com/TomorrowX6/AliceHub/releases/tag/\(tag)")!
    }

    static func compareURL(commit: String, tag: String) -> URL? {
        guard isSHA(commit, length: 40...40), isSHA(tag, length: 7...40) else { return nil }
        return URL(string: "\(api)/compare/\(tag)...\(commit)?per_page=1")
    }

    static func isBehind(_ data: Data) -> Bool {
        (try? JSONDecoder().decode(Comparison.self, from: data))?.status == "behind"
    }

    private static func isSHA(_ value: String, length: ClosedRange<Int>) -> Bool {
        let bytes = value.utf8
        return length.contains(bytes.count) && bytes.allSatisfy {
            (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0)
        }
    }

    @available(macOS 12.0, *)
    private static func fetch(_ url: URL) async -> Data? {
        var request = URLRequest(url: url, timeoutInterval: 5)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return data
    }

    @available(macOS 12.0, *)
    static func latestRelease() async -> String? {
        guard let commit = Bundle.main.object(forInfoDictionaryKey: "AliceHubGitCommit") as? String,
              isSHA(commit, length: 40...40),
              let data = await fetch(URL(string: "\(api)/releases/latest")!),
              let release = try? JSONDecoder().decode(Release.self, from: data),
              let url = compareURL(commit: commit, tag: release.tag_name),
              let comparison = await fetch(url), isBehind(comparison) else { return nil }
        return release.tag_name
    }
}
