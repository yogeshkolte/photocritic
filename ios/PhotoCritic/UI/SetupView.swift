import PhotoCriticCore
import SwiftUI

/// The setup screen (file 38, CUJ 1): provider, model, key and, on first
/// run, the sharing switch. The typed key lives only in this view's state
/// until it is checked and stored. In edit mode a change of provider or
/// model asks first when a conversation exists (CUJ 5).
struct SetupView: View {
    @State var model: SetupModel
    let killSwitchMessage: String?
    let conversationActive: Bool
    /// Called after a save; true when the provider or model changed.
    let onSaved: (Bool) -> Void
    var onBack: (() -> Void)?

    @State private var key = ""
    @State private var showKey = false
    @State private var confirmClear = false
    @State private var details: PcError?
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                header
                if let killSwitchMessage { banner(killSwitchMessage) }
                if model.loaded && model.providers.isEmpty {
                    Text(L10n.setupNoProviders).foregroundStyle(Tokens.muted)
                } else {
                    providerSection
                    if model.provider != nil {
                        modelSection
                        keySection
                    }
                    if model.firstRun { shareSwitch }
                    footer
                }
            }
            .padding(Spacing.l)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Tokens.bg.ignoresSafeArea())
        .foregroundStyle(Tokens.text)
        .task { await model.load() }
        .sheet(item: $details) { ErrorDetailsView(error: $0) }
        .alert(L10n.confirmClear, isPresented: $confirmClear) {
            Button(L10n.cancel, role: .cancel) {}
            Button(L10n.confirmContinue, role: .destructive) { save() }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack {
                if let onBack {
                    Button(action: onBack) { Image(systemName: "chevron.left").frame(width: 44, height: 44) }
                        .accessibilityLabel(L10n.back)
                        .padding(.leading, -Spacing.m)
                }
                Text(model.firstRun ? L10n.setupTitle : L10n.setupTitleEdit)
                    .font(.title.weight(.semibold))
                    .accessibilityAddTraits(.isHeader)
            }
            if model.firstRun { Text(L10n.setupBYOK).font(.subheadline).foregroundStyle(Tokens.muted) }
        }
    }

    private func banner(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .padding(Spacing.m)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Tokens.accent.opacity(0.16), in: RoundedRectangle(cornerRadius: Radii.small))
    }

    private var providerSection: some View {
        section(L10n.setupProvider) {
            ForEach(model.providers) { option in
                choiceRow(title: option.displayName, subtitle: nil, badge: nil, selected: model.provider == option.id) {
                    Task { await model.select(option.id) }
                }
            }
        }
    }

    private var modelSection: some View {
        section(L10n.setupModel) {
            ForEach(model.models, id: \.model.id) { choice in
                choiceRow(
                    title: choice.model.displayName,
                    subtitle: subtitle(choice.tag),
                    badge: choice.model.label.isEmpty ? nil : choice.model.label,
                    selected: model.modelID == choice.model.id
                ) {
                    model.modelID = choice.model.id
                    model.status = .idle
                }
            }
            if model.modelID == nil { Text(L10n.setupPickModel).font(.footnote).foregroundStyle(Tokens.muted) }
        }
    }

    private func subtitle(_ tag: ModelTag) -> String? {
        switch tag {
        case .none: nil
        case .preview: L10n.tagPreview
        case let .endingSoon(name): L10n.tagEndingSoon(name)
        }
    }

    private var keySection: some View {
        section(L10n.setupKey) {
            HStack {
                Group {
                    if showKey {
                        TextField(L10n.setupKey, text: $key)
                    } else {
                        SecureField(L10n.setupKey, text: $key)
                    }
                }
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.password)
                .privacySensitive()
                Button(showKey ? L10n.setupKeyHide : L10n.setupKeyShow) { showKey.toggle() }
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Tokens.accent)
            }
            .padding(Spacing.m)
            .background(Tokens.field, in: RoundedRectangle(cornerRadius: Radii.small))
            .onChange(of: key) { if case .failed = model.status { model.status = .idle } }
            if model.hasSavedKey { Text(L10n.setupKeySaved).font(.footnote).foregroundStyle(Tokens.muted) }
            if let url = URL(string: model.keyHelpURL), !model.keyHelpURL.isEmpty {
                Button(L10n.setupKeyHelp(model.providerName)) { openURL(url) }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Tokens.accent)
            }
            if model.provider == .gemini { Text(L10n.setupGeminiCost).font(.footnote).foregroundStyle(Tokens.muted) }
        }
    }

    private var shareSwitch: some View {
        Toggle(isOn: $model.shareUsage) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(L10n.setupShare)
                Text(L10n.setupShareDetail).font(.footnote).foregroundStyle(Tokens.muted)
            }
        }
        .tint(Tokens.accent)
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            if case let .failed(error) = model.status {
                ErrorBlock(onShowDetails: { details = error }, onDismiss: { model.status = .idle })
            }
            if model.status == .checking {
                HStack(spacing: Spacing.s) {
                    ProgressView().tint(Tokens.accent)
                    Text(L10n.setupChecking).foregroundStyle(Tokens.muted)
                }
                .frame(maxWidth: .infinity, minHeight: 48)
            } else {
                PrimaryButton(model.firstRun ? L10n.setupContinue : L10n.setupSave, enabled: model.canSave(key)) {
                    if !model.firstRun && conversationActive && model.changesSelection { confirmClear = true } else { save() }
                }
            }
        }
    }

    private func save() {
        let changed = model.changesSelection
        let typed = key
        Task {
            if await model.save(typed) {
                key = ""
                onSaved(changed)
            }
        }
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text(title).font(.headline).accessibilityAddTraits(.isHeader)
            content()
        }
    }

    private func choiceRow(title: String, subtitle: String?, badge: String?, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.m) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(selected ? Tokens.accent : Tokens.muted)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: Spacing.s) {
                        Text(title).foregroundStyle(Tokens.text)
                        if let badge {
                            Text(badge).font(.caption.weight(.semibold))
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .foregroundStyle(Tokens.onAccent)
                                .background(Tokens.accent, in: Capsule())
                        }
                    }
                    if let subtitle { Text(subtitle).font(.footnote).foregroundStyle(Tokens.muted) }
                }
                Spacer()
            }
            .padding(Spacing.m)
            .frame(minHeight: 48)
            .background(selected ? Tokens.field : Tokens.surface, in: RoundedRectangle(cornerRadius: Radii.small))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
