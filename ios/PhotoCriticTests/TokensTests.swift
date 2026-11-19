import SwiftUI
import Testing
@testable import PhotoCritic

@MainActor
struct TokensTests {
    @Test func panelSizesStepOneAtATime() {
        #expect(PanelSize.minimized.larger == .collapsed)
        #expect(PanelSize.collapsed.larger == .expanded)
        #expect(PanelSize.expanded.larger == .expanded)
        #expect(PanelSize.expanded.smaller == .collapsed)
        #expect(PanelSize.minimized.smaller == .minimized)
    }
}
