import Foundation

enum UpdateCheck {
    static let storageKey = "hub.checkForUpdates"
    private static let endpoint = URL(string: "https://api.github.com/repos/TomorrowX6/AliceHub/releases/latest")!

    private struct Release: Decodable {
        let tag_name: String
    }

    static func releaseURL(tag: String) -> URL {
        URL(string: "https://github.com/TomorrowX6/AliceHub/releases/tag/\(tag)")!
    }

    static func newReleaseTag(commit: String, tag: String) -> String? {
        guard isSHA(commit, length: 40...40), isSHA(tag, length: 7...40),
              !commit.lowercased().hasPrefix(tag.lowercased()) else { return nil }
        return tag
    }

    private static func isSHA(_ value: String, length: ClosedRange<Int>) -> Bool {
        let bytes = value.utf8
        return length.contains(bytes.count) && bytes.allSatisfy {
            (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0)
        }
    }

    @available(macOS 12.0, *)
    static func latestRelease() async -> String? {
        guard let commit = Bundle.main.object(forInfoDictionaryKey: "AliceHubGitCommit") as? String,
              isSHA(commit, length: 40...40) else { return nil }
        var request = URLRequest(url: endpoint, timeoutInterval: 5)
        request.setValue("AliceHub", forHTTPHeaderField: "User-Agent")
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let release = try? JSONDecoder().decode(Release.self, from: data) else { return nil }
        return newReleaseTag(commit: commit, tag: release.tag_name)
    }
}
