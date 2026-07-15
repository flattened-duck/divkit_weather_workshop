import XCTest

/// Hermetic request-count coverage that the real-backend suite can't provide (weather data varies
/// run-to-run, so counting requests against the live server isn't reliable). Document bodies are
/// captured LIVE from the real backend at setUp (never a committed/stale fixture) and served by a
/// local loopback server, so this suite also verifies the backend is reachable. Mirrors
/// WeatherRefetchTest.kt.
final class WeatherRefetchTests: BaseUITest {
    private var server: RecordingBackend!

    override func setUp() {
        super.setUp()
        requireBackendUp()
        let ru = fetchLiveDocument(lang: "ru")
        let en = fetchLiveDocument(lang: "en")
        server = try! RecordingBackend(ruBody: ru, enBody: en)
        server.start()
        launch(baseURL: "http://127.0.0.1:\(server.port)")
    }

    override func tearDown() {
        server.stop()
        super.tearDown()
    }

    private func waitForRequestCountAbove(_ n: Int, timeout: TimeInterval = 10, file: StaticString = #file, line: UInt = #line) {
        let end = Date().addingTimeInterval(timeout)
        while Date() < end {
            if server.requestCount > n { return }
            usleep(100_000)
        }
        XCTAssertGreaterThan(server.requestCount, n, file: file, line: line)
    }

    func test_languageSwitch_refetches() {
        waitForDiv("main_scroll")
        dismissPopupIfPresent()

        tapDiv("fab_settings")
        waitForDiv("settings_scroll")
        scrollDiv("settings_scroll", to: "lang_btn_en")

        let n = server.requestCount
        tapDiv("lang_btn_en")
        waitForRequestCountAbove(n)

        XCTAssertTrue(server.lastPath().contains("lang=en"))
    }

    func test_themeToggle_noRefetch() {
        waitForDiv("main_scroll")
        let n = server.requestCount
        dismissPopupIfPresent()

        tapDiv("fab_settings")
        waitForDiv("settings_scroll")
        scrollDiv("settings_scroll", to: "theme_btn_dark")
        tapDiv("theme_btn_dark")
        _ = pollBrightnessChanged(app, from: nil) // confirms the toggle took effect

        XCTAssertEqual(server.requestCount, n) // theme is a pure client-side global-var mutation, no fetch
    }
}
