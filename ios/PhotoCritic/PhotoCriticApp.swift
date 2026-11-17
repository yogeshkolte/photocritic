import SwiftUI

/// Phase 0 placeholder; the startup sequence and routing arrive in Phase 1
/// (design file 46).
@main
struct PhotoCriticApp: App {
    var body: some Scene {
        WindowGroup {
            PlaceholderView()
        }
    }
}

struct PlaceholderView: View {
    var body: some View {
        ZStack {
            Tokens.bg.ignoresSafeArea()
            Text("Phase 0 build").foregroundStyle(Tokens.text)
        }
    }
}
