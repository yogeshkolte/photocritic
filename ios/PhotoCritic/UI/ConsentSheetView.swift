import SwiftUI

/// The consent sheet before the first send to a provider (G13, file 31).
/// Dismissing it counts as "Not now".
struct ConsentSheetView: View {
    let consent: ConsentUI
    let appPolicyURL: String
    let onAgree: () -> Void
    let onNotNow: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                Text(L10n.consentTitle(consent.providerName)).font(.title2.weight(.semibold)).accessibilityAddTraits(.isHeader)
                VStack(alignment: .leading, spacing: Spacing.s) {
                    ForEach(consent.text.summary, id: \.self) { item in
                        HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                            Text("•")
                            Text(item)
                        }
                    }
                }
                Text(L10n.consentEffective(consent.text.effectiveDate)).font(.footnote).foregroundStyle(Tokens.muted)
                link(L10n.consentPolicy(consent.providerName), consent.text.policyURL)
                if !appPolicyURL.isEmpty { link(L10n.consentAppPolicy, appPolicyURL) }
                PrimaryButton(L10n.consentAgree, action: onAgree)
                QuietButton(L10n.consentNotNow, action: onNotNow)
            }
            .padding(Spacing.xl)
        }
        .foregroundStyle(Tokens.text)
        .background(Tokens.surface.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private func link(_ title: String, _ url: String) -> some View {
        if let target = URL(string: url) {
            Button(title) { openURL(target) }.foregroundStyle(Tokens.accent).font(.subheadline.weight(.medium))
        }
    }
}
