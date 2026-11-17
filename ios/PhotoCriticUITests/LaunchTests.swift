import XCTest

final class LaunchTests: XCTestCase {
    @MainActor
    func testLaunchShowsPlaceholder() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["Phase 0 build"].waitForExistence(timeout: 10))
    }
}
