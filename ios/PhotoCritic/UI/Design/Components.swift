import PhotoCriticCore
import SwiftUI

enum Spacing {
    static let xs: CGFloat = 4
    static let s: CGFloat = 8
    static let m: CGFloat = 12
    static let l: CGFloat = 16
    static let xl: CGFloat = 24
}

enum Radii {
    static let small: CGFloat = 8
    static let card: CGFloat = 12
    static let sheet: CGFloat = 20
}

struct PrimaryButton: View {
    let title: String
    var enabled = true
    let action: () -> Void

    init(_ title: String, enabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.enabled = enabled
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title).font(.body.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 48)
        }
        .foregroundStyle(Tokens.onAccent)
        .background(enabled ? Tokens.accent : Tokens.accent.opacity(0.38), in: RoundedRectangle(cornerRadius: Radii.card))
        .disabled(!enabled)
    }
}

struct QuietButton: View {
    let title: String
    let action: () -> Void

    init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title).font(.body.weight(.medium)).frame(maxWidth: .infinity, minHeight: 48)
        }
        .foregroundStyle(Tokens.text)
        .background(Tokens.field, in: RoundedRectangle(cornerRadius: Radii.card))
    }
}

/// An icon button over the photo: a 65% black circle, 48 points.
struct PhotoIconButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage).font(.system(size: 18, weight: .medium)).frame(width: 48, height: 48)
        }
        .foregroundStyle(Tokens.text)
        .background(Color.black.opacity(0.65), in: Circle())
        .accessibilityLabel(label)
    }
}

/// A small icon-and-label action, such as Copy or Share.
struct SmallAction: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(label, systemImage: systemImage).font(.footnote)
        }
        .foregroundStyle(Tokens.muted)
        .buttonStyle(.borderless)
    }
}

/// The generic message with Dismiss and "Show details" (G12, §7).
struct ErrorBlock: View {
    let onShowDetails: () -> Void
    var onDismiss: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Label(UserMessages.generic, systemImage: "exclamationmark.circle")
                .foregroundStyle(Tokens.danger)
                .font(.subheadline)
            HStack(spacing: Spacing.l) {
                Button(L10n.showDetails, action: onShowDetails)
                if let onDismiss { Button(L10n.dismiss, action: onDismiss) }
            }
            .font(.subheadline.weight(.medium))
            .foregroundStyle(Tokens.accent)
            .buttonStyle(.borderless)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.m)
        .background(Tokens.danger.opacity(0.12), in: RoundedRectangle(cornerRadius: Radii.small))
    }
}

/// "Show details": the error's phase, status, provider code, request ID and redacted detail (§7).
struct ErrorDetailsView: View {
    let error: PcError
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                row(UserMessages.labelWhere, UserMessages.phase(error.phase))
                if let status = error.httpStatus { row(UserMessages.labelHTTPStatus, String(status)) }
                if let code = error.providerCode { row(UserMessages.labelProviderCode, code) }
                if let id = error.requestID { row(UserMessages.labelRequestID, id) }
                row(UserMessages.labelDetails, error.detail)
            }
            .navigationTitle(L10n.detailsTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L10n.ok) { dismiss() } } }
        }
        .presentationDetents([.medium])
    }

    private func row(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).textSelection(.enabled)
        }
    }
}

extension PcError: @retroactive Identifiable {
    public var id: String { "\(phase.rawValue)|\(httpStatus ?? 0)|\(providerCode ?? "")|\(requestID ?? "")|\(detail)" }
}
