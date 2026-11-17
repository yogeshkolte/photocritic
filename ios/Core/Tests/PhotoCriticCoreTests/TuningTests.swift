import Foundation
import Testing
@testable import PhotoCriticCore

struct TuningTests {
    private struct Entry: Decodable { let value: Double }

    private func shared() throws -> [String: Entry] {
        try JSONDecoder().decode([String: Entry].self, from: RepoRoot.shared("tuning.json"))
    }

    @Test func namesMatchSharedTuningJSON() throws {
        #expect(Set(try shared().keys) == Set(Tuning.all.keys))
    }

    @Test func valuesMatchSharedTuningJSON() throws {
        let shared = try shared()
        for (name, value) in Tuning.all {
            #expect(shared[name]?.value == value, "\(name)")
        }
    }
}
