import SwiftUI

/// The original photo at its own aspect ratio, fitted whole, black around it;
/// pinch to zoom, double tap to reset (file 38); a single tap calls `onTap`.
struct PhotoZoomView: View {
    let image: CGImage
    var onTap: () -> Void = {}
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    private let maxZoom: CGFloat = 5

    var body: some View {
        Image(decorative: image, scale: 1)
            .resizable()
            .scaledToFit()
            .scaleEffect(scale)
            .offset(offset)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
            .contentShape(Rectangle())
            .gesture(
                MagnifyGesture()
                    .onChanged { scale = min(max(lastScale * $0.magnification, 1), maxZoom) }
                    .onEnded { _ in
                        lastScale = scale
                        if scale == 1 { reset() }
                    }
                    .simultaneously(with: DragGesture()
                        .onChanged { value in
                            guard scale > 1 else { return }
                            offset = CGSize(
                                width: lastOffset.width + value.translation.width,
                                height: lastOffset.height + value.translation.height
                            )
                        }
                        .onEnded { _ in lastOffset = offset })
            )
            .onTapGesture(count: 2) { withAnimation { reset() } }
            .onTapGesture(count: 1) { onTap() }
            .accessibilityLabel(L10n.photoDescription)
            .accessibilityAddTraits(.isImage)
    }

    private func reset() {
        scale = 1
        lastScale = 1
        offset = .zero
        lastOffset = .zero
    }
}
