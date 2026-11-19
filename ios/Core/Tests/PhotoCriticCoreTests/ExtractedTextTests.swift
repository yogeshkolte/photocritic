import Foundation
import Testing
@testable import PhotoCriticCore

/// `shared/vectors/extracted-text.json`, the same cases the Android tests read.
struct ExtractedTextTests {
    struct Seg: Decodable {
        let type: String
        let text: String
    }

    struct Case: Decodable {
        let name: String
        let answer: String
        let segments: [Seg]
    }

    struct Vectors: Decodable {
        let cases: [Case]
    }

    @Test func everySharedCase() throws {
        let vectors = try JSONDecoder().decode(Vectors.self, from: try RepoRoot.shared("vectors/extracted-text.json"))
        #expect(!vectors.cases.isEmpty)
        for c in vectors.cases {
            let expected = c.segments.map { $0.type == "card" ? Segment.card($0.text) : Segment.text($0.text) }
            #expect(ExtractedText.parse(c.answer) == expected, "\(c.name)")
        }
    }
}
