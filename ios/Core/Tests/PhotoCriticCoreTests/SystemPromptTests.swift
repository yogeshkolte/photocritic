import Foundation
import Testing
@testable import PhotoCriticCore

struct SystemPromptTests {
    private func shared(_ path: String) throws -> String {
        String(decoding: try RepoRoot.shared(path), as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    @Test func promptsMatchSharedPrompts() throws {
        #expect(SystemPrompt.base == (try shared("prompts/system.txt")))
        #expect(SystemPrompt.version == (try shared("prompts/VERSION")))
        for kind in PromptKind.allCases {
            #expect(SystemPrompt.forKind(kind).hasSuffix(try shared("prompts/\(kind.rawValue).txt")), "\(kind)")
        }
    }
}
