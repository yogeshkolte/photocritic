import XCTest

/// A fresh install with the dev config opens setup (CUJ 1). Needs the network for Remote Config.
final class LaunchTests: XCTestCase {
    @MainActor
    func testFirstLaunchShowsSetup() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["Set up PhotoCritic"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.buttons["Check key and continue"].exists)
    }
}
