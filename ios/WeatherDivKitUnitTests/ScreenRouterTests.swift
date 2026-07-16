import XCTest
@testable import WeatherDivKit

@MainActor
final class ScreenRouterTests: XCTestCase {
    @MainActor
    private final class Fixture {
        var rendered: [Screen] = []
        var available: Set<Screen> = Set(Screen.allCases)
        var exitCount = 0

        func makeSUT() -> ScreenRouter {
            ScreenRouter(
                availableScreens: { [unowned self] in self.available },
                render: { [unowned self] screen in self.rendered.append(screen) },
                onExit: { [unowned self] in self.exitCount += 1 }
            )
        }
    }

    func test_initial_state() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()

        XCTAssertEqual(sut.currentScreen, .main)
        XCTAssertEqual(sut.backStackSnapshot(), [])
        XCTAssertEqual(fixture.rendered, [])
    }

    func test_showScreen_main_pushesAndRenders() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()

        sut.showScreen(.main)

        XCTAssertEqual(sut.backStackSnapshot(), [.main])
        XCTAssertEqual(fixture.rendered, [.main])
    }

    func test_showScreen_mainThenSettings_pushesBoth() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()

        sut.showScreen(.main)
        sut.showScreen(.settings)

        XCTAssertEqual(sut.backStackSnapshot(), [.main, .settings])
        XCTAssertEqual(sut.currentScreen, .settings)
    }

    func test_showScreen_sameScreenTwice_doesNotDuplicateStackButRendersEachTime() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()

        sut.showScreen(.main)
        sut.showScreen(.main)

        XCTAssertEqual(sut.backStackSnapshot(), [.main])
        XCTAssertEqual(fixture.rendered, [.main, .main])
    }

    func test_goBack_fromTwoDeep_popsAndRendersPrevious_noExit() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()
        sut.showScreen(.main)
        sut.showScreen(.settings)
        fixture.rendered = []

        sut.goBack()

        XCTAssertEqual(sut.backStackSnapshot(), [.main])
        XCTAssertEqual(fixture.rendered, [.main])
        XCTAssertEqual(fixture.exitCount, 0)
    }

    func test_goBack_atRoot_exits() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()
        sut.showScreen(.main)
        fixture.rendered = []

        sut.goBack()

        XCTAssertEqual(fixture.exitCount, 1)
        XCTAssertEqual(fixture.rendered, [])
        XCTAssertEqual(sut.currentScreen, .main)
    }

    func test_renderScreen_unavailableScreen_isNoOp() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()
        sut.showScreen(.main)
        fixture.available = [.main]
        fixture.rendered = []

        sut.renderScreen(.settings)

        XCTAssertEqual(fixture.rendered, [])
        XCTAssertEqual(sut.currentScreen, .main)
    }

    func test_showScreen_unavailableScreen_stillPushesButDoesNotRender() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()
        sut.showScreen(.main)
        fixture.available = [.main]
        fixture.rendered = []

        sut.showScreen(.settings)

        XCTAssertEqual(sut.backStackSnapshot(), [.main, .settings])
        XCTAssertEqual(fixture.rendered, [])
        XCTAssertEqual(sut.currentScreen, .main)
    }

    func test_showScreen_mainSettingsSettings_pushesAllRendersAll() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()

        sut.showScreen(.main)
        sut.showScreen(.settings)
        sut.showScreen(.settings)

        XCTAssertEqual(sut.backStackSnapshot(), [.main, .settings])
        XCTAssertEqual(fixture.rendered, [.main, .settings, .settings])
    }

    func test_renderCurrent_reRendersCurrentScreen() {
        let fixture = Fixture()
        let sut = fixture.makeSUT()
        sut.showScreen(.settings)
        fixture.rendered = []

        sut.renderCurrent()

        XCTAssertEqual(fixture.rendered, [.settings])
    }
}
