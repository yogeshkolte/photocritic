import PhotoCriticCore
import SwiftUI

/// Picks the screen for the startup route (file 38).
struct RootView: View {
    let app: AppModel

    var body: some View {
        Group {
            switch app.route {
            case .loading:
                Tokens.bg.ignoresSafeArea().accessibilityLabel(L10n.loading)
            case let .disabled(reason):
                DisabledView(reason: reason)
            case let .setup(snapshot, message, edit, visit):
                SetupRoute(app: app, snapshot: snapshot, killSwitchMessage: message, edit: edit).id(visit)
            case let .home(snapshot):
                HomeView(session: app.session(for: snapshot)) { app.openSettings(snapshot) }
            }
        }
        .preferredColorScheme(.dark)
        .tint(Tokens.accent)
        .task { await app.start() }
    }
}

private struct SetupRoute: View {
    let app: AppModel
    let snapshot: Snapshot
    let killSwitchMessage: String?
    let edit: Bool

    var body: some View {
        // In edit mode the conversation lives on behind setup; on first run there is none yet.
        let session = edit ? app.session(for: snapshot) : nil
        SetupView(
            model: SetupModel(c: app.container, snapshot: snapshot, firstRun: !edit),
            killSwitchMessage: killSwitchMessage,
            conversationActive: session.map { !$0.state.messages.isEmpty } ?? false,
            onSaved: { changed in
                Task {
                    if changed { session?.newPhoto() }
                    await session?.refreshSelection()
                    app.open(.home(snapshot))
                }
            },
            onBack: edit ? { app.open(.home(snapshot)) } : nil
        )
    }
}

/// Config unavailable, app disabled, or update required (CUJ 7, CUJ 9).
struct DisabledView: View {
    let reason: DisabledReason
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: Spacing.l) {
            Image(systemName: "exclamationmark.circle").font(.system(size: 44)).foregroundStyle(Tokens.accent)
            Text(message).multilineTextAlignment(.center)
            if case let .updateRequired(url) = reason, let target = URL(string: url), !url.isEmpty {
                PrimaryButton(L10n.updateButton) { openURL(target) }.frame(maxWidth: 320)
            }
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Tokens.bg.ignoresSafeArea())
        .foregroundStyle(Tokens.text)
    }

    private var message: String {
        switch reason {
        case .configUnavailable: L10n.configUnavailable
        case let .appDisabled(message): message.isEmpty ? L10n.appDisabledDefault : message
        case .updateRequired: L10n.updateRequired
        }
    }
}
