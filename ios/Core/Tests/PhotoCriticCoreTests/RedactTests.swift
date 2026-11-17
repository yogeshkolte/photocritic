import Foundation
import Testing
@testable import PhotoCriticCore

struct RedactTests {
    private struct Case: Decodable {
        let name: String
        let input: String
        let expected: String
        let userKey: String?
    }

    private struct Cases: Decodable { let cases: [Case] }

    @Test func everyCaseInSharedRedactionCases() throws {
        let cases = try JSONDecoder().decode(Cases.self, from: RepoRoot.shared("redaction/cases.json")).cases
        for item in cases {
            #expect(Redact.text(item.input, userKey: item.userKey) == item.expected, "\(item.name)")
        }
    }
}
