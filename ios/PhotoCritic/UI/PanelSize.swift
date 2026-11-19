import Foundation

/// The conversation panel's three sizes, as on Android; drag the bar or use its arrows to move one step.
enum PanelSize: Int, CaseIterable {
    /// Only the bar and the composer, so the photo can be seen.
    case minimized
    /// Sized to its content, up to 45% of the screen.
    case collapsed
    case expanded

    var larger: PanelSize { PanelSize(rawValue: min(rawValue + 1, PanelSize.expanded.rawValue))! }
    var smaller: PanelSize { PanelSize(rawValue: max(rawValue - 1, 0))! }
}
