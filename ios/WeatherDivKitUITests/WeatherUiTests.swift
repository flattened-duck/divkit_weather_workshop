import XCTest

/// Real-backend, id-based UI tests. The app renders whatever the live backend on :8080 serves —
/// structure/id assertions only, never live weather values, so the suite stays green as the
/// layout evolves. Mirrors WeatherUiTest.kt.
final class WeatherUiTests: BaseUITest {

    override func setUp() {
        super.setUp()
        requireBackendUp()
    }

    private func launchToMain() {
        launch()
        waitForDiv("main_scroll")
        dismissPopupIfPresent()
    }

    func test_mainScreen_coreStructure() {
        launchToMain()
        assertDivExists("header")
        assertDivExists("main_scroll")
    }

    func test_mainScreen_galleriesAndCustom() {
        launchToMain()
        assertDivExists("hourly_gallery")
        scrollDiv("main_scroll", to: "sun_phase")
        waitForDiv("sun_phase")
    }

    /// DIVERGENCE from Android: iOS has no hardware back / nav bar; the app's goBack() is driven
    /// purely by in-layout controls. Settings' only return affordance is `nav_home`.
    func test_navigation_settings_back() {
        launchToMain()
        tapDiv("fab_settings")
        waitForDiv("settings_scroll")
        assertDivExists("city_search_input")

        scrollDiv("settings_scroll", to: "nav_home")
        tapDiv("nav_home")
        waitForDiv("main_scroll")
    }

    /// DIVERGENCE from Android: Android drives the return via Espresso.pressBack(); iOS has no
    /// system back, but the `about` card (verified live: /document lang=ru screens.about) carries
    /// its own `nav_home` id (below the GitHub link) that navigates back to main — used here.
    func test_navigation_about_back() {
        launchToMain()
        tapDiv("fab_about")
        waitForDiv("about_scroll")

        scrollDiv("about_scroll", to: "nav_home")
        tapDiv("nav_home")
        waitForDiv("main_scroll")
    }

    func test_citySearch_populatesResults() {
        launchToMain()
        tapDiv("fab_settings")
        waitForDiv("city_search_input")

        typeIntoDiv("city_search_input", "London")
        tapDiv("city_search_button")
        // Data-robust: any query yields >=1 child — a hit row OR the "not found" row.
        assertDivChildrenAtLeast("city_search_results", 1)
    }

    func test_themeToggle_changesBackground() {
        launchToMain()
        tapDiv("fab_settings")
        waitForDiv("settings_scroll")

        scrollDiv("settings_scroll", to: "theme_btn_light")
        tapDiv("theme_btn_light")
        let light = pollBrightnessChanged(app, from: nil)

        scrollDiv("settings_scroll", to: "theme_btn_dark")
        tapDiv("theme_btn_dark")
        let dark = pollBrightnessChanged(app, from: light)

        XCTAssertGreaterThan(light, dark + 0.05, "expected light theme brighter than dark by > 0.05 (light=\(light), dark=\(dark))")
    }

    func test_popup_install_persistsAcrossRelaunch() {
        requireBackendUp()
        launch() // reset ON
        waitForDiv("main_scroll")
        waitForDiv("popup_install")

        tapDiv("popup_install")
        waitForDivAbsent("popup_close")

        launch(resetState: false) // relaunch, keep disk state
        waitForDiv("main_scroll")
        assertDivAbsent("popup_install")
    }

    func test_popup_closeX_persistsAcrossRelaunch() {
        requireBackendUp()
        launch() // reset ON
        waitForDiv("main_scroll")
        waitForDiv("popup_close")

        tapDiv("popup_close")
        waitForDivAbsent("popup_close")

        launch(resetState: false) // relaunch, keep disk state
        waitForDiv("main_scroll")
        assertDivAbsent("popup_close")
    }

    func test_compactToggle_smoke() {
        launchToMain()
        tapDiv("fab_settings")
        waitForDiv("settings_scroll")

        scrollDiv("settings_scroll", to: "compact_btn_on")
        tapDiv("compact_btn_on")

        scrollDiv("settings_scroll", to: "nav_home")
        tapDiv("nav_home")
        waitForDiv("main_scroll")
    }
}
