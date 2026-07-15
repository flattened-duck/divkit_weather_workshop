import XCTest

extension XCUIApplication {
    /// The single element whose DivKit div id was applied as accessibilityIdentifier (R-32.57).
    /// Type-agnostic on purpose: an id may surface as .other / .button / .staticText / .textField.
    func div(_ id: String) -> XCUIElement {
        descendants(matching: .any).matching(identifier: id).firstMatch
    }
}

/// Locator/interaction helpers, the iOS analog of Android's DivIdMatchers.kt + TestHelpers.kt.
/// All operate on `app` (set by BaseUITest); XCTFail messages carry `file`/`line` so failures
/// point at the caller, not this helper.
extension BaseUITest {
    @discardableResult
    func waitForDiv(_ id: String, timeout: TimeInterval = 10, file: StaticString = #file, line: UInt = #line) -> XCUIElement {
        let el = app.div(id)
        if !el.waitForExistence(timeout: timeout) {
            XCTFail("div '\(id)' never appeared", file: file, line: line)
        }
        return el
    }

    func assertDivExists(_ id: String, file: StaticString = #file, line: UInt = #line) {
        XCTAssertTrue(app.div(id).exists, "div '\(id)' not present", file: file, line: line)
    }

    func assertDivAbsent(_ id: String, file: StaticString = #file, line: UInt = #line) {
        XCTAssertFalse(app.div(id).exists, "div '\(id)' unexpectedly present", file: file, line: line)
    }

    /// Polling counterpart to `assertDivAbsent`; needed because the phase-2 network swap that
    /// removes e.g. `zero_skeleton` is async.
    func waitForDivAbsent(_ id: String, timeout: TimeInterval = 10, file: StaticString = #file, line: UInt = #line) {
        let end = Date().addingTimeInterval(timeout)
        while Date() < end {
            if !app.div(id).exists { return }
            usleep(100_000)
        }
        assertDivAbsent(id, file: file, line: line)
    }

    /// Tap-only: assumes the target is already hittable. Callers scroll it into view first via
    /// `scrollDiv` for below-fold elements (mirrors Android's explicit scrollDivIntoView + clickDivId).
    func tapDiv(_ id: String, file: StaticString = #file, line: UInt = #line) {
        let el = app.div(id)
        guard el.waitForExistence(timeout: 10) else {
            XCTFail("div '\(id)' never appeared to tap", file: file, line: line)
            return
        }
        el.tap()
    }

    /// DivKit's `TextInputBlockView` wraps a real `UITextField`, but the div carries no
    /// accessibility LABEL (only an id), so `applyAccessibility` (VGSL
    /// AccessibilityElementExtensions.swift:62) computes `isAccessibilityElement = (label != nil)
    /// == false` for the wrapper — and the inner UITextField itself never appears anywhere in the
    /// app's accessibility snapshot either (confirmed empirically: tapping it DOES focus it and
    /// raise the keyboard — a raw coordinate tap + IOHID key injection successfully types into it —
    /// but it is invisible to both XCUITest's and a second, independent accessibility-tree walker).
    /// `XCUIElement.typeText(_:)` therefore fails ("no descendant has keyboard focus") no matter
    /// which element it's called on, since XCUITest can't find ANY focused element in its snapshot.
    /// Workaround: the on-screen system keyboard IS fully accessible, so type by tapping its keys.
    func typeIntoDiv(_ id: String, _ text: String, file: StaticString = #file, line: UInt = #line) {
        let el = app.div(id)
        guard el.waitForExistence(timeout: 10) else {
            XCTFail("div '\(id)' never appeared to type into", file: file, line: line)
            return
        }
        el.tap()
        let field = el.descendants(matching: .textField).firstMatch
        if field.exists {
            field.typeText(text)
            return
        }
        guard app.keyboards.firstMatch.waitForExistence(timeout: 5) else {
            XCTFail("keyboard never appeared after tapping div '\(id)'", file: file, line: line)
            return
        }
        for ch in text {
            tapKeyboardKey(ch, file: file, line: line)
        }
    }

    private func tapKeyboardKey(_ ch: Character, file: StaticString, line: UInt) {
        let lower = String(ch).lowercased()
        let upper = String(ch).uppercased()
        if tapIfHittable(app.keys[lower]) { return }
        if tapIfHittable(app.keys[upper]) { return }
        // Case mismatch against the keyboard's current shift state — toggle and retry once.
        let shift = app.buttons["shift"]
        if shift.exists, shift.isHittable { shift.tap() }
        if tapIfHittable(app.keys[lower]) { return }
        if tapIfHittable(app.keys[upper]) { return }
        XCTFail("keyboard key for '\(ch)' not found/hittable", file: file, line: line)
    }

    @discardableResult
    private func tapIfHittable(_ el: XCUIElement) -> Bool {
        guard el.exists, el.isHittable else { return false }
        el.tap()
        return true
    }

    /// City-search rows each carry a text; robust to hit-vs-"not found" without asserting content.
    func assertDivChildrenAtLeast(_ id: String, _ n: Int, timeout: TimeInterval = 10, file: StaticString = #file, line: UInt = #line) {
        let end = Date().addingTimeInterval(timeout)
        var count = 0
        while Date() < end {
            count = app.div(id).descendants(matching: .staticText).count
            if count >= n { return }
            usleep(100_000)
        }
        XCTAssertGreaterThanOrEqual(count, n, "div '\(id)' has \(count) staticText descendants, expected >= \(n)", file: file, line: line)
    }

    /// Manual scroll (no auto-scroll), mirrors Android's scrollDivIntoView. No-op if already hittable;
    /// on exhaustion returns silently — the subsequent waitForDiv/tap fails loudly.
    func scrollDiv(_ container: String, to target: String, maxSteps: Int = 24) {
        for _ in 0..<maxSteps {
            let t = app.div(target)
            if t.exists && t.isHittable { return }
            let c = app.div(container)
            if c.exists && c.isHittable {
                c.swipeUp(velocity: .default)
            } else {
                app.swipeUp()
            }
            usleep(150_000)
        }
    }

    /// Used by tests that don't care about the popup.
    func dismissPopupIfPresent(timeout: TimeInterval = 5) {
        if app.div("popup_close").waitForExistence(timeout: timeout) {
            tapDiv("popup_close")
            waitForDivAbsent("popup_close")
        }
    }
}
