import Foundation
import Testing
@testable import PhotoCriticCore

/// Config checks against `shared/vectors/app-version.json` and the
/// `shared/remote-config/samples` files, the same files Android reads.
struct ConfigTests {
    static let app = AppVersion(major: 0, minor: 1, patch: 0)

    struct Comparison: Decodable {
        let a: String
        let b: String
        let expected: Int
    }

    struct VersionVectors: Decodable {
        let comparisons: [Comparison]
        let invalid: [String]
    }

    @Test func versionsCompareByNumber() throws {
        let v = try JSONDecoder().decode(VersionVectors.self, from: try RepoRoot.shared("vectors/app-version.json"))
        for c in v.comparisons {
            let a = try #require(AppVersion.parse(c.a))
            let b = try #require(AppVersion.parse(c.b))
            #expect((a < b ? -1 : (a == b ? 0 : 1)) == c.expected, "\(c.a) vs \(c.b)")
        }
        for text in v.invalid { #expect(AppVersion.parse(text) == nil, "\(text)") }
    }

    /// A sample file holds each parameter as a JSON value; Remote Config gives each as a string.
    static func raw(_ path: String) throws -> [String: String] {
        let object = try #require(try JSONSerialization.jsonObject(with: try RepoRoot.shared(path)) as? [String: Any])
        return try object.mapValues { value in
            String(decoding: try JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed]), as: UTF8.self)
        }
    }

    static func snapshot(_ change: (inout [String: Any]) -> Void = { _ in }) throws -> Snapshot {
        let template = try JSONSerialization.jsonObject(with: try RepoRoot.shared("remote-config/template.json"))
        var object = try #require(template as? [String: Any])
        change(&object)
        let raw = try object.mapValues {
            String(decoding: try JSONSerialization.data(withJSONObject: $0, options: [.fragmentsAllowed]), as: UTF8.self)
        }
        guard case let .valid(snapshot) = ConfigValidator.validate(raw, appVersion: app) else {
            Issue.record("template is not valid")
            throw CancellationError()
        }
        return snapshot
    }

    @Test func validSamplesPass() throws {
        let files = RepoRoot.sharedJSONFiles("remote-config/samples/valid")
        #expect(!files.isEmpty)
        for file in files {
            let result = ConfigValidator.validate(try Self.raw("remote-config/samples/valid/\(file)"), appVersion: Self.app)
            guard case .valid = result else { Issue.record("\(file): \(result)"); continue }
        }
    }

    @Test func invalidSamplesFail() throws {
        let files = RepoRoot.sharedJSONFiles("remote-config/samples/invalid")
        #expect(!files.isEmpty)
        for file in files {
            let result = ConfigValidator.validate(try Self.raw("remote-config/samples/invalid/\(file)"), appVersion: Self.app)
            guard case .unavailable = result else { Issue.record("\(file) passed"); continue }
        }
    }

    @Test func aFloatIsNotAnInteger() throws {
        var raw = try Self.raw("remote-config/template.json")
        raw["config_meta"] = #"{"configVersion": 1.0, "publishedAt": "2026-11-17T00:00:00Z"}"#
        guard case let .unavailable(path, _) = ConfigValidator.validate(raw, appVersion: Self.app) else {
            Issue.record("accepted 1.0")
            return
        }
        #expect(path == "config_meta.configVersion")
    }

    @Test func theTemplateListsGeminiModels() throws {
        let snapshot = try Self.snapshot()
        #expect(snapshot.enabledProviders.contains(.gemini))
        #expect(snapshot.modelChoices(.gemini).map(\.model.displayName) == ["Gemini 4 Hydrogen", "Gemini 4 Argon"])
    }

    @Test func killSwitchRoutes() throws {
        let snapshot = try Self.snapshot()
        #expect(KillSwitch.evaluate(snapshot, SavedChoice(provider: nil, modelID: nil, hasKey: false)) == .setup(killSwitchMessage: nil))
        #expect(KillSwitch.evaluate(snapshot, SavedChoice(provider: .gemini, modelID: "gemini-4-hydrogen", hasKey: true)) == .home)
        let noKey = SavedChoice(provider: .gemini, modelID: "gemini-4-hydrogen", hasKey: false)
        #expect(KillSwitch.evaluate(snapshot, noKey) == .setup(killSwitchMessage: nil))
        #expect(
            KillSwitch.evaluate(snapshot, SavedChoice(provider: .gemini, modelID: "gone", hasKey: true)) ==
                .setup(killSwitchMessage: KillSwitch.defaultMessage)
        )
        let disabled = try Self.snapshot { o in
            var ks = o["kill_switch"] as! [String: Any] // swiftlint:disable:this force_cast
            ks["appDisabled"] = true
            o["kill_switch"] = ks
        }
        #expect(KillSwitch.evaluate(disabled, SavedChoice(provider: nil, modelID: nil, hasKey: false)) == .appDisabled(message: ""))
    }
}
