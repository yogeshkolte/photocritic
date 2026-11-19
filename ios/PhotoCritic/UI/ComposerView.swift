import PhotoCriticCore
import SwiftUI

/// The starter buttons; each sends its label as the question (file 38).
struct StarterChips: View {
    let enabled: Bool
    let onAsk: (String, PromptKind) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s) {
                chip(L10n.starterIdentify, "eye", .identify)
                chip(L10n.starterExplain, "lightbulb", .explain)
                chip(L10n.starterCopyText, "text.viewfinder", .extract)
            }
        }
    }

    private func chip(_ label: String, _ icon: String, _ kind: PromptKind) -> some View {
        Button { onAsk(label, kind) } label: {
            Label(label, systemImage: icon).font(.subheadline.weight(.medium))
                .padding(.horizontal, Spacing.m).frame(minHeight: 40)
        }
        .foregroundStyle(enabled ? Tokens.text : Tokens.muted)
        .background(Tokens.field, in: Capsule())
        .disabled(!enabled)
    }
}

/// The question field with Send, or Stop while an answer arrives (G4).
struct ComposerView: View {
    @Binding var text: String
    let canSend: Bool
    let answering: Bool
    let onSend: () -> Void
    let onStop: () -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: Spacing.s) {
            TextField(L10n.composerHint, text: $text, axis: .vertical)
                .lineLimit(1...4)
                .padding(.horizontal, Spacing.m).padding(.vertical, 10)
                .background(Tokens.field, in: RoundedRectangle(cornerRadius: Radii.card))
                .onChange(of: text) { if text.count > Tuning.maxQuestionChars { text = String(text.prefix(Tuning.maxQuestionChars)) } }
                .submitLabel(.send)
            if answering {
                circle("stop.fill", L10n.stop, enabled: true, action: onStop)
            } else {
                let ready = canSend && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                circle("arrow.up", L10n.send, enabled: ready, action: onSend)
            }
        }
    }

    private func circle(_ icon: String, _ label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 17, weight: .bold)).frame(width: 44, height: 44)
        }
        .foregroundStyle(Tokens.onAccent)
        .background(enabled ? Tokens.accent : Tokens.accent.opacity(0.38), in: Circle())
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}
