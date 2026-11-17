import Foundation

/// The base prompt and prompt-kind texts from `shared/prompts/` (design file 11),
/// bundled through the copy written by `tools/sync-shared.sh`.
public enum SystemPrompt {
    public static let base: String = read("system")
    public static let version: String = read("VERSION", ext: nil)

    public static func forKind(_ kind: PromptKind) -> String {
        base + "\n\n" + read(kind.fileName)
    }

    private static func read(_ name: String, ext: String? = "txt") -> String {
        guard let url = Bundle.module.url(forResource: name, withExtension: ext, subdirectory: "prompts"),
              let text = try? String(contentsOf: url, encoding: .utf8)
        else { fatalError("missing prompt resource \(name)") }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
