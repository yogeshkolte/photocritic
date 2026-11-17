import SwiftUI
import Testing
@testable import PhotoCritic

struct TokensTests {
    @Test func placeholderViewBuilds() {
        _ = PlaceholderView().body
    }
}
