import XCTest

/// Mirrors RegistrationSmokeTest.kt: proves native registration (custom block, extension handler)
/// binds without crashing — here via the live app rather than an inline DivData construction,
/// since XCUITest drives the real process end to end.
final class SmokeTests: BaseUITest {

    func test_launchesAndBindsCustomViewsWithoutCrash() {
        requireBackendUp()
        launch()
        waitForDiv("main_scroll")
        dismissPopupIfPresent()

        // Proves SunPhaseCustomBlockFactory + ScrollStateExtensionHandler are registered and bind
        // without crashing (the iOS analog of the inline-DivData registration smoke).
        scrollDiv("main_scroll", to: "sun_phase")
        waitForDiv("sun_phase")
    }

    /// Diagnostic, not an assertion: dumps the element tree so div ids surfacing as
    /// accessibilityIdentifiers can be eyeballed (this is Step 0 of the implementation contract,
    /// run before any assertions were written). Kept in the suite as a standing sanity check.
    func test_dumpTree() {
        launch()
        waitForDiv("main_scroll")
        let dump = app.debugDescription
        print(dump)

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.lifetime = .keepAlways
        attachment.name = "main_screen_dump"
        add(attachment)

        let textAttachment = XCTAttachment(string: dump)
        textAttachment.lifetime = .keepAlways
        textAttachment.name = "debugDescription"
        add(textAttachment)
    }
}
