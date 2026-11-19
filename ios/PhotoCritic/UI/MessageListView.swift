import PhotoCriticCore
import SwiftUI
import UIKit

/// The conversation (file 38): each question, its answer as text and
/// "Text in photo" cards, and its state. Scrolls to the newest message.
struct MessageListView: View {
    let messages: [VisibleMessage]
    let errorDismissable: Bool
    let onDismiss: () -> Void
    let onDetails: (PcError) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Spacing.l) {
                    ForEach(messages) { message in
                        MessageItem(
                            message: message,
                            onDismiss: errorDismissable && message.id == messages.last?.id ? onDismiss : nil,
                            onDetails: onDetails
                        )
                        .id(message.id)
                    }
                    Color.clear.frame(height: 1).id("bottom")
                }
            }
            .onChange(of: messages.last?.answer) { proxy.scrollTo("bottom", anchor: .bottom) }
            .onChange(of: messages.count) { withAnimation { proxy.scrollTo("bottom", anchor: .bottom) } }
            .onAppear { proxy.scrollTo("bottom", anchor: .bottom) }
        }
    }
}

private struct MessageItem: View {
    let message: VisibleMessage
    let onDismiss: (() -> Void)?
    let onDetails: (PcError) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text(message.question)
                .padding(.horizontal, Spacing.m).padding(.vertical, Spacing.s)
                .background(Tokens.field, in: RoundedRectangle(cornerRadius: Radii.card))
                .frame(maxWidth: .infinity, alignment: .trailing)
            ForEach(Array(message.segments.enumerated()), id: \.offset) { _, segment in
                switch segment {
                case let .text(text):
                    Text(verbatim: text).textSelection(.enabled)
                case let .card(text):
                    TextCard(text: text)
                }
            }
            switch message.status {
            case .streaming:
                ProgressView().tint(Tokens.accent).accessibilityLabel(L10n.answering)
            case .stopped:
                tag(L10n.tagStopped)
            case .failed:
                tag(L10n.tagFailed)
                if let error = message.error { ErrorBlock(onShowDetails: { onDetails(error) }, onDismiss: onDismiss) }
            case .done:
                SmallAction(systemImage: "doc.on.doc", label: L10n.copyAnswer) { UIPasteboard.general.string = message.answer }
            }
        }
        .foregroundStyle(Tokens.text)
    }

    private func tag(_ text: String) -> some View {
        Text(text).font(.caption.weight(.semibold)).foregroundStyle(Tokens.muted)
    }
}

/// Text copied from the photo, with Copy and Share (G3).
private struct TextCard: View {
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Label(L10n.cardTitle, systemImage: "text.viewfinder").font(.caption.weight(.semibold)).foregroundStyle(Tokens.accent)
            Text(text).font(.body.monospaced()).textSelection(.enabled)
            HStack(spacing: Spacing.l) {
                SmallAction(systemImage: "doc.on.doc", label: L10n.copy) { UIPasteboard.general.string = text }
                ShareLink(item: text) { Label(L10n.share, systemImage: "square.and.arrow.up").font(.footnote) }
                    .foregroundStyle(Tokens.muted)
            }
        }
        .padding(Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Tokens.surface, in: RoundedRectangle(cornerRadius: Radii.card))
        .overlay(RoundedRectangle(cornerRadius: Radii.card).stroke(Tokens.accent.opacity(0.4)))
    }
}
