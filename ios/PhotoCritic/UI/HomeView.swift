import PhotoCriticCore
import PhotosUI
import SwiftUI

/// Home, phone layout (file 38, G19): the photo fills the screen; the top
/// bar has only Settings and New photo; the conversation panel can be
/// minimized (bar and composer only, no tint), collapsed (sized to its content up to
/// 45% of the screen) or expanded, on a 72% or 94% dark tint. Tapping the
/// photo minimizes it. The wide two-pane layout arrives in Phase 1.4.
struct HomeView: View {
    let session: SessionModel
    let onOpenSettings: () -> Void

    @State private var question = ""
    @State private var size = PanelSize.collapsed
    @State private var details: PcError?
    @State private var confirmNewPhoto = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var showPicker = false
    @State private var showCamera = false
    @State private var noCamera = false

    private var state: SessionState { session.state }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                Color.black.ignoresSafeArea()
                if state.phase == .noPhoto {
                    emptyState
                } else if let photo = session.photo {
                    PhotoZoomView(image: photo) { withAnimation { size = .minimized } }.ignoresSafeArea()
                }
                VStack { topControls; Spacer() }
                if state.phase != .noPhoto { panel(screenHeight: geo.size.height + geo.safeAreaInsets.top + geo.safeAreaInsets.bottom) }
            }
        }
        .foregroundStyle(Tokens.text)
        .photosPicker(isPresented: $showPicker, selection: $pickerItem, matching: .images, preferredItemEncoding: .current)
        .onChange(of: pickerItem) { _, item in load(item) }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { data in
                showCamera = false
                if let data { choose(data, source: "camera") }
            }
            .ignoresSafeArea()
        }
        .alert(L10n.cameraUnavailable, isPresented: $noCamera) { Button(L10n.ok) {} }
        .alert(L10n.confirmClear, isPresented: $confirmNewPhoto) {
            Button(L10n.cancel, role: .cancel) {}
            Button(L10n.confirmContinue, role: .destructive) { newPhoto() }
        }
        .sheet(item: $details) { ErrorDetailsView(error: $0) }
        .sheet(isPresented: consentShown) {
            if let consent = session.consent {
                ConsentSheetView(
                    consent: consent, appPolicyURL: session.appPolicyURL, onAgree: session.agreeConsent, onNotNow: session.declineConsent
                )
            }
        }
        // The question leaves the composer once its message exists; "Not now" keeps it (§5).
        .onChange(of: state.messages.count) {
            if state.messages.last?.question == question.trimmingCharacters(in: .whitespacesAndNewlines) { question = "" }
        }
    }

    /// Shown while the session waits for consent; swiping it away counts as "Not now".
    private var consentShown: Binding<Bool> {
        Binding(
            get: { state.phase == .awaitingConsent },
            set: { shown in if !shown && state.phase == .awaitingConsent { session.declineConsent() } }
        )
    }

    // MARK: Photo

    private var emptyState: some View {
        VStack(spacing: Spacing.l) {
            Spacer()
            Image(systemName: "text.viewfinder").font(.system(size: 56)).foregroundStyle(Tokens.accent)
            Text(L10n.homeEmptyTitle).font(.title.weight(.semibold)).accessibilityAddTraits(.isHeader)
            Text(L10n.homeEmptyBody).foregroundStyle(Tokens.muted).multilineTextAlignment(.center)
            PrimaryButton(L10n.takePhoto) { if CameraPicker.isAvailable { showCamera = true } else { noCamera = true } }
            QuietButton(L10n.choosePhoto) { showPicker = true }
            Spacer()
        }
        .padding(Spacing.xl)
        .frame(maxWidth: 480)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Tokens.bg.ignoresSafeArea())
    }

    private func load(_ item: PhotosPickerItem?) {
        guard let item else { return }
        pickerItem = nil
        Task {
            if let data = try? await item.loadTransferable(type: Data.self) { choose(data, source: "picker") }
        }
    }

    private func choose(_ data: Data, source: String) {
        size = .collapsed
        session.photoChosen(data, source: source, displayMaxPx: Self.displayMaxPx)
    }

    private func newPhoto() {
        size = .collapsed
        question = ""
        session.newPhoto()
    }

    /// The longest edge the photo is decoded to for the screen: twice the screen, at most 4096 (§6).
    private static var displayMaxPx: Int {
        let bounds = UIScreen.main.nativeBounds
        return min(Int(max(bounds.width, bounds.height)) * 2, 4096)
    }

    // MARK: Top bar

    private var topControls: some View {
        VStack(spacing: Spacing.s) {
            HStack {
                PhotoIconButton(systemImage: "gearshape", label: L10n.settings, action: onOpenSettings)
                Spacer()
                if state.phase != .noPhoto {
                    PhotoIconButton(systemImage: "photo.badge.plus", label: L10n.newPhoto) {
                        if state.messages.isEmpty { newPhoto() } else { confirmNewPhoto = true }
                    }
                }
            }
            if !session.bannerMessage.isEmpty { banner(session.bannerMessage, icon: nil) }
            if !session.online { banner(L10n.offline, icon: "wifi.slash") }
        }
        .padding(Spacing.m)
    }

    private func banner(_ text: String, icon: String?) -> some View {
        HStack(spacing: Spacing.s) {
            if let icon { Image(systemName: icon) }
            Text(text).font(.subheadline)
            Spacer(minLength: 0)
        }
        .padding(Spacing.m)
        .background(Color.black.opacity(0.72), in: RoundedRectangle(cornerRadius: Radii.small))
    }

    // MARK: Panel

    private func panel(screenHeight: CGFloat) -> some View {
        let expanded = size == .expanded
        let limit = screenHeight * (expanded ? 0.92 : 0.45)
        return VStack(alignment: .leading, spacing: Spacing.m) {
            panelBar
            if size != .minimized && !state.messages.isEmpty {
                MessageListView(
                    messages: state.messages,
                    errorDismissable: state.phase == .error,
                    onDismiss: session.dismiss,
                    onDetails: { details = $0 }
                )
                .frame(maxHeight: expanded ? .infinity : nil)
                .fixedSize(horizontal: false, vertical: !expanded && state.messages.count == 1 && state.messages[0].answer.count < 400)
            }
            phaseStatus
            if state.hasPhoto {
                let canSend = state.canSend && session.online
                if !state.answering && size != .minimized { StarterChips(enabled: canSend) { ask($0, $1) } }
                ComposerView(text: $question, canSend: canSend, answering: state.answering,
                             onSend: { ask(question.trimmingCharacters(in: .whitespacesAndNewlines), .free) },
                             onStop: session.stop)
            }
        }
        .padding(.horizontal, Spacing.l)
        .padding(.bottom, Spacing.m)
        .frame(maxWidth: .infinity, maxHeight: expanded ? limit : nil, alignment: .top)
        .frame(maxHeight: limit, alignment: .bottom)
        .background(
            // Minimized, nothing covers the photo; the bar's controls carry their own backing.
            Tokens.bg.opacity(size == .minimized ? 0 : (expanded ? 0.94 : 0.72)),
            in: UnevenRoundedRectangle(topLeadingRadius: Radii.sheet, topTrailingRadius: Radii.sheet)
        )
        .animation(.easeOut(duration: 0.2), value: size)
    }

    private func ask(_ text: String, _ kind: PromptKind) {
        // A new answer is shown: Copy text opens the panel fully, any other question at least partly.
        size = kind == .extract ? .expanded : max(size, .collapsed)
        session.ask(text, kind: kind)
    }

    /// The bar: drag it, tap it, or use the arrows to change size one step.
    private var panelBar: some View {
        ZStack {
            Capsule().fill(size == .minimized ? Tokens.text : Tokens.muted).frame(width: 36, height: 4)
                .shadow(color: .black.opacity(0.6), radius: 2).frame(maxHeight: .infinity, alignment: .top).padding(.top, Spacing.s)
            HStack {
                if size == .minimized && !state.messages.isEmpty {
                    Text(L10n.panelHidden).font(.footnote).foregroundStyle(Tokens.text)
                        .padding(.horizontal, Spacing.m).padding(.vertical, 6)
                        .background(Color.black.opacity(0.65), in: Capsule())
                }
                Spacer()
                if size != .expanded { arrow("chevron.up", L10n.panelShowMore) { size = size.larger } }
                if size != .minimized { arrow("chevron.down", L10n.panelShowLess) { size = size.smaller } }
            }
            .padding(.top, Spacing.s)
        }
        .frame(height: 44)
        .contentShape(Rectangle())
        .onTapGesture { size = size == .expanded ? .collapsed : size.larger }
        .gesture(DragGesture(minimumDistance: 12).onEnded { value in
            if value.translation.height < -24 { size = size.larger }
            if value.translation.height > 24 { size = size.smaller }
        })
        .accessibilityElement(children: .contain)
        .accessibilityLabel(L10n.panelHandle)
    }

    private func arrow(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).font(.system(size: 15, weight: .semibold)).frame(width: 40, height: 40) }
            .foregroundStyle(size == .minimized ? Tokens.text : Tokens.muted)
            .background(Color.black.opacity(size == .minimized ? 0.65 : 0), in: Circle())
            .accessibilityLabel(label)
    }

    /// Preparing, the image error, or an error with no failed message (a consent record that could not be saved).
    @ViewBuilder
    private var phaseStatus: some View {
        switch state.phase {
        case .preparing:
            HStack(spacing: Spacing.s) {
                ProgressView().tint(Tokens.accent)
                Text(L10n.preparingPhoto).foregroundStyle(Tokens.muted)
            }
        case .imageError:
            if let error = state.error { ErrorBlock(onShowDetails: { details = error }, onDismiss: session.dismiss) }
        case .error:
            if let error = state.error, !state.messages.contains(where: { $0.status == .failed && $0.error == error }) {
                ErrorBlock(onShowDetails: { details = error }, onDismiss: session.dismiss)
            }
        default:
            EmptyView()
        }
    }
}

extension PanelSize: Comparable {
    static func < (lhs: PanelSize, rhs: PanelSize) -> Bool { lhs.rawValue < rhs.rawValue }
}
