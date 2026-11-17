import Foundation

/// The provider hosts PhotoCritic may call (design §4, decision 23).
public enum Allowlist {
    public static let providerHosts: Set<String> = [
        "api.openai.com",
        "api.anthropic.com",
        "generativelanguage.googleapis.com",
    ]

    /// Base URLs as Remote Config must give them (`providers.<p>.baseUrl`).
    public static let baseURLs: [String: String] = [
        "openai": "https://api.openai.com",
        "anthropic": "https://api.anthropic.com",
        "gemini": "https://generativelanguage.googleapis.com",
    ]
}

/// Which hosts a `ProviderHTTP` may reach. Tests use a local policy for a stub.
public struct HostPolicy: Sendable {
    public let hosts: Set<String>
    public let requireHTTPS: Bool

    public init(hosts: Set<String>, requireHTTPS: Bool) {
        self.hosts = hosts
        self.requireHTTPS = requireHTTPS
    }

    public static let production = HostPolicy(hosts: Allowlist.providerHosts, requireHTTPS: true)

    public func allows(_ url: URL) -> Bool {
        guard let host = url.host?.lowercased(), hosts.contains(host) else { return false }
        return !requireHTTPS || url.scheme?.lowercased() == "https"
    }
}
