import SwiftUI
import Testing
@testable import PhotoCritic

@MainActor
struct TokensTests {
    @Test func placeholderViewBuilds() {
        _ = PlaceholderView().body
    }
}
