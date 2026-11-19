import FirebaseCore
import SwiftUI

/// The app entry: Firebase, the container, and the startup route (design file 46).
@main
struct PhotoCriticApp: App {
    @State private var app: AppModel

    init() {
        FirebaseApp.configure()
        _app = State(initialValue: AppModel(container: AppContainer.live()))
    }

    var body: some Scene {
        WindowGroup {
            RootView(app: app)
        }
    }
}
