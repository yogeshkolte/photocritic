import Foundation

public enum ConfigResult: Sendable {
    case valid(Snapshot)
    /// Shown as the config-unavailable screen (Q9). `path` names the first failing value.
    case unavailable(path: String, reason: String)
}

/// Checks the Remote Config values against `shared/remote-config/schema.json`
/// and the cross-field rules of `tools/validate-config.ts` (design §8, file
/// 26), by hand: there is no schema library at run time. Any invalid value
/// makes the whole config unavailable; there is no per-value fallback (Q9).
/// The same rules as the Android `ConfigValidator`.
public enum ConfigValidator {
    public static let parameters: Set<String> = [
        "config_meta", "kill_switch", "providers", "models", "consent_texts", "privacy_policy",
    ]

    struct Invalid: Error {
        let path: String
        let reason: String
    }

    /// `raw` maps each Remote Config parameter to its JSON string value.
    public static func validate(_ raw: [String: String], appVersion: AppVersion) -> ConfigResult {
        do {
            return .valid(Snapshot(values: try read(raw), appVersion: appVersion))
        } catch let error as Invalid {
            return .unavailable(path: error.path, reason: error.reason)
        } catch {
            return .unavailable(path: "", reason: "unexpected")
        }
    }

    private static func read(_ raw: [String: String]) throws -> RemoteConfigValues {
        if let extra = Set(raw.keys).subtracting(parameters).sorted().first { throw Invalid(path: extra, reason: "unknown parameter") }
        if let missing = parameters.subtracting(raw.keys).sorted().first { throw Invalid(path: missing, reason: "missing") }

        func parsed(_ name: String) throws -> Node {
            guard let data = raw[name]?.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
            else { throw Invalid(path: name, reason: "not JSON") }
            return Node(path: name, json: json)
        }
        let providers = try providers(try parsed("providers").obj())
        let consentTexts = try consentTexts(try parsed("consent_texts").obj())
        let models = try models(try parsed("models"))
        try crossCheck(providers, consentTexts, models)
        return RemoteConfigValues(
            meta: try meta(try parsed("config_meta").obj()),
            killSwitch: try killSwitch(try parsed("kill_switch").obj()),
            providers: providers,
            models: models,
            consentTexts: consentTexts,
            privacyPolicy: try privacyPolicy(try parsed("privacy_policy").obj())
        )
    }

    private static func meta(_ o: Obj) throws -> ConfigMeta {
        try o.keys(["configVersion", "publishedAt"])
        return ConfigMeta(configVersion: try o["configVersion"].int(min: 1), publishedAt: try o["publishedAt"].string(pattern: dateTime))
    }

    private static func killSwitch(_ o: Obj) throws -> KillSwitchConfig {
        try o.keys([
            "appDisabled", "message", "minSupportedAppVersion", "updateUrl",
            "disabledProviders", "disabledModels", "bannerMessage", "reportsDisabled",
        ])
        return KillSwitchConfig(
            appDisabled: try o["appDisabled"].bool(),
            message: try o["message"].string(maxLength: 500),
            minSupportedAppVersion: try o["minSupportedAppVersion"].semver(),
            updateURL: try o["updateUrl"].httpsURLOrEmpty(),
            disabledProviders: Set(
                try o["disabledProviders"].uniqueStrings().map { try Node(path: $0.path, json: $0.value).providerID() }
            ),
            disabledModels: Set(try o["disabledModels"].uniqueStrings(minLength: 1, maxLength: 100).map(\.value)),
            bannerMessage: try o["bannerMessage"].string(maxLength: 300),
            reportsDisabled: try o["reportsDisabled"].bool()
        )
    }

    private static func providers(_ o: Obj) throws -> [ProviderID: ProviderConfig] {
        try o.keys(providerKeys)
        var result: [ProviderID: ProviderConfig] = [:]
        for id in ProviderID.allCases {
            let p = try o[id.rawValue].obj()
            try p.keys(["enabled", "displayName", "baseUrl", "keyHelpUrl", "consentTextVersion"])
            result[id] = ProviderConfig(
                id: id,
                enabled: try p["enabled"].bool(),
                displayName: try p["displayName"].nonEmpty(),
                baseURL: try p["baseUrl"].httpsURL(),
                keyHelpURL: try p["keyHelpUrl"].httpsURL(),
                consentTextVersion: try p["consentTextVersion"].int(min: 1)
            )
        }
        return result
    }

    private static func models(_ node: Node) throws -> [ModelConfig] {
        try node.array().map { item in
            let m = try item.obj()
            try m.keys([
                "provider", "id", "displayName", "label", "maxLongEdgePx", "maxBytes", "imageDetail",
                "maxOutputTokens", "thinkingLevel", "contextTokens", "status", "replacementId", "minAppVersion",
            ])
            return ModelConfig(
                provider: try m["provider"].providerID(),
                id: try m["id"].string(pattern: modelID),
                displayName: try m["displayName"].nonEmpty(),
                label: try m["label"].oneOf(labels),
                maxLongEdgePx: try m["maxLongEdgePx"].int(min: 256, max: 8192),
                maxBytes: try m["maxBytes"].int(min: 10000, max: 20_000_000),
                imageDetail: try m["imageDetail"].oneOf(imageDetails),
                maxOutputTokens: try m["maxOutputTokens"].int(min: 1, max: 128_000),
                thinkingLevel: try m["thinkingLevel"].oneOf(thinkingLevels),
                contextTokens: try m["contextTokens"].int(min: 1000, max: 10_000_000),
                status: ModelStatus(rawValue: try m["status"].oneOf(ModelStatus.allCases.map(\.rawValue)))!,
                replacementID: try m["replacementId"].string(maxLength: 100),
                minAppVersion: try m["minAppVersion"].semver()
            )
        }
    }

    private static func consentTexts(_ o: Obj) throws -> [ProviderID: ConsentText] {
        try o.keys(providerKeys)
        var result: [ProviderID: ConsentText] = [:]
        for id in ProviderID.allCases {
            let c = try o[id.rawValue].obj()
            try c.keys(["version", "effectiveDate", "summary", "policyUrl"])
            let summary = try c["summary"].array()
            guard (1...maxSummaryItems).contains(summary.count) else {
                throw Invalid(path: c["summary"].path, reason: "needs 1 to \(maxSummaryItems) items")
            }
            result[id] = ConsentText(
                version: try c["version"].int(min: 1),
                effectiveDate: try c["effectiveDate"].string(pattern: date),
                summary: try summary.map { try $0.nonEmpty() },
                policyURL: try c["policyUrl"].httpsURL()
            )
        }
        return result
    }

    private static func privacyPolicy(_ o: Obj) throws -> PrivacyPolicyConfig {
        try o.keys(["version", "url"])
        return PrivacyPolicyConfig(version: try o["version"].int(min: 1), url: try o["url"].httpsURLOrEmpty())
    }

    private static func crossCheck(
        _ providers: [ProviderID: ProviderConfig],
        _ consentTexts: [ProviderID: ConsentText],
        _ models: [ModelConfig]
    ) throws {
        func failIf(_ condition: Bool, _ path: String, _ reason: String) throws {
            if condition { throw Invalid(path: path, reason: reason) }
        }
        for id in ProviderID.allCases {
            let provider = providers[id]!
            try failIf(provider.baseURL != Allowlist.baseURLs[id.rawValue], "providers.\(id.rawValue).baseUrl", "not the allowlisted host")
            try failIf(
                provider.consentTextVersion != consentTexts[id]!.version,
                "providers.\(id.rawValue).consentTextVersion",
                "differs from consent_texts.\(id.rawValue).version"
            )
        }
        let active = Set(models.filter { $0.status == .active }.map(\.id))
        var seen = Set<String>()
        for model in models {
            try failIf(!seen.insert(model.id).inserted, "models.\(model.id)", "duplicate id")
            try failIf(
                !model.replacementID.isEmpty && !active.contains(model.replacementID),
                "models.\(model.id).replacementId",
                "names no active model"
            )
            try failIf(
                model.status == .deprecated && model.replacementID.isEmpty, "models.\(model.id)", "deprecated without a replacementId"
            )
        }
    }

    /// A JSON value and its path, for the error message. One reader per schema type.
    struct Node {
        let path: String
        let json: Any?

        func fail(_ reason: String) -> Invalid { Invalid(path: path, reason: reason) }

        func obj() throws -> Obj {
            guard let dict = json as? [String: Any] else { throw fail("not an object") }
            return Obj(path: path, json: dict)
        }

        func array() throws -> [Node] {
            guard let items = json as? [Any] else { throw fail("not an array") }
            return items.enumerated().map { Node(path: "\(path)[\($0.offset)]", json: $0.element) }
        }

        private var number: NSNumber? {
            guard let n = json as? NSNumber, !(json is String) else { return nil }
            return n
        }

        private var isBool: Bool { number.map { CFGetTypeID($0) == CFBooleanGetTypeID() } ?? false }

        func bool() throws -> Bool {
            guard isBool, let n = number else { throw fail("not a boolean") }
            return n.boolValue
        }

        func int(min: Int, max: Int = Int.max) throws -> Int {
            // Floating-point JSON numbers (1.0, 1e3) decode with a "d" or "f" type.
            guard let n = number, !isBool, !["d", "f"].contains(String(cString: n.objCType)) else { throw fail("not an integer") }
            let value = n.int64Value
            guard value >= Int64(min), value <= Int64(max) else { throw fail("out of range \(min)..\(max)") }
            return Int(value)
        }

        func string(minLength: Int = 0, maxLength: Int = Int.max, pattern: NSRegularExpression? = nil) throws -> String {
            guard let s = json as? String else { throw fail(json == nil ? "missing or not a value" : "not a string") }
            // Kotlin String.length counts UTF-16 units; match it.
            guard (minLength...maxLength).contains(s.utf16.count) else { throw fail("length outside \(minLength)..\(maxLength)") }
            if let pattern, !pattern.matchesWhole(s) { throw fail("wrong format") }
            return s
        }

        func nonEmpty() throws -> String { try string(minLength: 1, maxLength: 500) }

        func httpsURL() throws -> String { try string(maxLength: 500, pattern: ConfigValidator.httpsURL) }

        func httpsURLOrEmpty() throws -> String {
            let s = try string(maxLength: 500)
            if !s.isEmpty { _ = try httpsURL() }
            return s
        }

        func semver() throws -> AppVersion {
            guard let version = AppVersion.parse(try string(pattern: ConfigValidator.semver)) else { throw fail("not a version") }
            return version
        }

        func oneOf(_ allowed: [String]) throws -> String {
            let s = try string()
            guard allowed.contains(s) else { throw fail("not one of \(allowed)") }
            return s
        }

        func providerID() throws -> ProviderID {
            ProviderID(rawValue: try oneOf(ProviderID.allCases.map(\.rawValue)))!
        }

        /// String items with no repeats, as (path, value).
        func uniqueStrings(minLength: Int = 0, maxLength: Int = Int.max) throws -> [(path: String, value: String)] {
            let items = try array().map { (path: $0.path, value: try $0.string(minLength: minLength, maxLength: maxLength)) }
            guard Set(items.map(\.value)).count == items.count else { throw fail("repeated item") }
            return items
        }
    }

    struct Obj {
        let path: String
        let json: [String: Any]

        subscript(name: String) -> Node { Node(path: "\(path).\(name)", json: json[name]) }

        /// The object must have exactly these keys.
        func keys(_ names: [String]) throws {
            if let extra = Set(json.keys).subtracting(names).sorted().first {
                throw Invalid(path: "\(path).\(extra)", reason: "unknown field")
            }
            if let missing = names.first(where: { json[$0] == nil }) { throw Invalid(path: "\(path).\(missing)", reason: "missing") }
        }
    }

    private static let maxSummaryItems = 8
    private static let providerKeys = ProviderID.allCases.map(\.rawValue)
    private static let labels = ["Fast", "Best", ""]
    private static let thinkingLevels = ["", "low", "medium", "high"]
    private static let imageDetails = [
        "low", "high", "auto", "MEDIA_RESOLUTION_LOW", "MEDIA_RESOLUTION_MEDIUM", "MEDIA_RESOLUTION_HIGH", "none",
    ]
    static let httpsURL = regex(#"https://\S+"#)
    static let semver = regex(#"\d+\.\d+\.\d+"#)
    static let date = regex(#"\d{4}-\d{2}-\d{2}"#)
    static let dateTime = regex(#"\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}:\d{2}(\.\d+)?([Zz]|[+-]\d{2}:\d{2})"#)
    static let modelID = regex(#"[a-z0-9][a-z0-9._-]{0,99}"#)

    private static func regex(_ pattern: String) -> NSRegularExpression {
        // The patterns are constants; a bad one is a programming error.
        try! NSRegularExpression(pattern: "^(?:\(pattern))$") // swiftlint:disable:this force_try
    }
}

extension NSRegularExpression {
    /// True when the pattern (anchored at both ends) matches `text`, like Kotlin's `Regex.matches`.
    func matchesWhole(_ text: String) -> Bool {
        firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }
}
