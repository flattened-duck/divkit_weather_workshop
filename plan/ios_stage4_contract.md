# CONTRACT — iOS Stage 4: XCUITest suite mirroring the Android instrumented tests

Branch: `ios-client` (has S0–S3). Target app: `ios/WeatherDivKit`. Backend: Ktor on the Mac host
`:8080` (`cd backend && ./gradlew run`). Simulator: **iPhone 17** (`E9C65017-...`, verified present).
Acceptance gate: `xcodebuild ... -scheme WeatherDivKit -only-testing:WeatherDivKitUITests test` GREEN with
backend up; the `WeatherUiTests` health check FAILS loudly with backend down.

Goal (verbatim user): *"Тесты ios должны быть как на андроид."* Port the **invariants** of
`app/src/androidTest/**` to XCUITest — structure / div-id / child-count / theme assertions ONLY, driving the
live app against the real backend. NEVER assert live weather values.

---

## MUST-NOT-GET-WRONG (load-bearing — read first)

1. **A DivKit div `id` reaches the UIKit view as its `accessibilityIdentifier`, automatically, for EVERY div**
   (verified §1). XCUITest locates it type-agnostically via
   `app.descendants(matching: .any).matching(identifier: "<div id>").firstMatch`. This IS DivKit's own
   UI-test pattern (`DivKitUITests/TooltipsUITests.swift:25`). Buttons with a text child also surface as
   `app.buttons["<id>"]`, but the `.any` query above is the ONE primitive to build everything on.
2. **Off-screen elements do NOT exist in the XCUITest tree** (galleries are `UICollectionView`, cells
   recycled) — exactly like Android's RecyclerView. Deep content (`sun_phase`, lower settings buttons) MUST
   be scrolled on-screen (swipe the scroll container) before asserting/tapping.
3. **First render is async** (2-phase cold start: cache/skeleton → network swap). Every first assertion after
   a launch/navigation goes through `waitForDiv(id)` (`XCUIElement.waitForExistence(timeout:)`), never a bare
   `.exists` check.
4. **Assert STRUCTURE / ids / child-count / theme-brightness only** — never temperature, condition text, or
   city string. Live data varies run-to-run.
5. **XCUITest is a separate process; it cannot flip `AppConfig.baseURL` in-process** (unlike Android flipping
   `DocumentLoader.baseUrl`). The app reads launch **environment** at startup and overrides base URL + wipes
   state. This launch-env hook (§4) is the ONLY production change Stage 4 makes.
6. **iOS has NO `clearPackageData`/Orchestrator.** State (UserDefaults, DivKit stored-values file, doc caches)
   PERSISTS across app launches on the simulator. Each test MUST launch with `WDK_RESET_STATE=1` to start
   clean, or state leaks between tests (popup won't reshow, prefs carry over). The ONE exception is the popup
   persistence test, which deliberately relaunches WITHOUT reset to prove disk persistence.
7. **The popup is a permanent stored-value + a session variable** (verified §2). It shows only when
   `!popup_dismissed && !getStoredBooleanValue('widget_set_up',false) && !getStoredBooleanValue('widget_popup_delayed',false)`.
   Reset (§4) deletes the stored-values file → popup reshows next launch. Tapping `popup_install` writes
   `widget_set_up=true` to disk (persists across relaunch); tapping `popup_close` writes `widget_popup_delayed`
   (3-day) to disk.
8. **Backend-down must be a loud failure**, not a silent pass on the bundled skeleton: `WeatherUiTests.setUp`
   pings `http://localhost:8080/document?lang=ru` from the test process and aborts with a clear message if
   unreachable (mirror Android `requireBackendUp`). iOS Simulator shares the host loopback, so `localhost:8080`
   from both the app and the test process reaches the Mac backend (no `10.0.2.2` translation needed).

---

## §1 — VERIFIED FACT: div `id` → `accessibilityIdentifier` (R-32.57)

The chain (all under `/Users/the-leo/divkit_source/divkit/client/ios`, and VGSL at
`~/Library/Developer/Xcode/DerivedData/WeatherDivKit-*/SourcePackages/checkouts/vgsl`):

1. `DivKit/Extensions/DivBase/DivBaseBlockBuilder.swift:187` — every div runs
   `(div.accessibility ?? DivAccessibility()).resolve(expressionResolver, id: context.currentDivId, block:, customParams:)`.
   Note `?? DivAccessibility()`: even a div with **no** accessibility spec gets a default one, resolved with
   `id = context.currentDivId` (the div's `id`).
2. `DivKit/Extensions/DivAccessibilityExtensions.swift:34-41` — `resolve(...)` returns
   `AccessibilityElement(strings: Strings(label:…, identifier: id))`, i.e. `identifier` = the div id.
   (Early-return WITHOUT identifier happens only when `mode == .exclude`; default mode is `.default` —
   `DivKit/generated_sources/DivAccessibility.swift:34,52` — so identifier is set for all normal divs.)
3. The element flows through `addingDecorations(accessibilityElement:)` →
   `DecoratingBlock` → on configure the view calls
   `applyAccessibilityFromScratch(model.accessibility)` (`LayoutKit/.../UI/Blocks/DecoratingBlock+UIViewRenderableBlock.swift:553`
   → `LayoutKit/.../Base/UIViewExtensions.swift:17`).
4. `applyAccessibility(_:)` in VGSL (`VGSLUI/extensions/AccessibilityElementExtensions.swift:81`) sets
   `accessibilityIdentifier = strings.identifier ?? accessibilityIdentifier`. ⇒ **the view's
   `accessibilityIdentifier` becomes the div id.**
5. Container ids (`main_scroll`, `header`, etc.) get identifiers on their decorating wrapper (a `UIView`);
   since they contain accessibility elements they surface to XCUITest as `Other` elements carrying the id —
   found by the `.any`+`matching(identifier:)` query.

**Non-interference caveat (checked):** `DivKit/Views/DivView.swift:349` overrides `accessibilityElements` to
return a curated flat list from `DivAccessibilityElementsStorage`. That storage is populated ONLY for divs
declaring forward-focus (`DivKit/Extensions/DivBase/DivBaseExtensions.swift:184`, guarded by
`focus?.nextFocusIds`). This layout uses none, so `getAccessibilityElements` returns `[]`
(`DivKit/DivAccessibilityElementsStorage.swift`), and `DivView.accessibilityElements` falls through to
`super.accessibilityElements` (standard UIKit tree). ⇒ the override is inert here; standard accessibility
(with our identifiers) is what XCUITest sees.

**Authoritative confirmation:** DivKit's own suite locates divs exactly this way —
`DivKitUITests/TooltipsUITests.swift:25`:
`mainWindow.descendants(matching: .any).matching(identifier: "baseDivView").element`, and
`:139` `app.buttons["testing"].tap()`.

**Reachability of the ids the Android tests use** — all 20 confirmed present in the live layout AND the zero
skeleton (via `curl :8080/document` and `Resources/zero_ru.json`):
`main_scroll, header, hourly_gallery, sun_phase, fab_settings, fab_about, settings_scroll, about_scroll,
city_search_input, city_search_button, city_search_results, theme_btn_light, theme_btn_dark, theme_btn_system,
compact_btn_on, compact_btn_off, lang_btn_ru, lang_btn_en, nav_home, popup_install, popup_close`. The zero
skeleton additionally carries `zero_skeleton`. All are reachable via the primitive above.

---

## §2 — VERIFIED FACT: popup mechanism (from live `/document`)

Popup container `visibility` expression:
```
@{(!popup_dismissed && !getStoredBooleanValue('widget_set_up', false) && !getStoredBooleanValue('widget_popup_delayed', false)) ? 'visible' : 'gone'}
```
- `popup_dismissed` — card-local boolean var, default `0`; set to `1` by BOTH buttons' `set_variable` action
  (hides popup in the current session immediately).
- `getStoredBooleanValue('widget_set_up',…)` — permanent stored value (`lifetime 2147483647`), written by
  `popup_install`'s `set_stored_value`.
- `getStoredBooleanValue('widget_popup_delayed',…)` — 3-day stored value (`lifetime 259200`), written by
  `popup_close` (×).

Stored values persist to **disk**: `DivPersistentValuesStorage` uses `Property(fileName: "divkit.values_storage")`
which writes to `applicationSupportDirectory/divkit.values_storage`
(`DivKit/StoredValues/DivPersistentValuesStorage.swift:5,9`; VGSL
`VGSLFundamentals/Property+Storage.swift:14` → `.applicationSupportDirectory`). ⇒ dismissal survives an app
relaunch; the reset hook must delete this file to reshow the popup.

When the container is `gone`, its child buttons `popup_install`/`popup_close` are removed from the view tree ⇒
`app.div("popup_install").exists == false`. That is the assertion surface.

---

## §3 — FILES TO CREATE (test target)  `ios/WeatherDivKitUITests/`

Package: standard XCTest/XCUITest. All helpers are Swift; the implementer writes the bodies EXCEPT the pinned
files (§3.1 primitive, §3.4 recording backend, §3.5 brightness) whose exact form is load-bearing.

### 3.1 `DivUITest.swift` — locator toolkit (iOS analog of `DivIdMatchers.kt` + `TestHelpers.kt`)

PIN the primitive exactly (load-bearing):
```swift
import XCTest

extension XCUIApplication {
    /// The single element whose DivKit div id was applied as accessibilityIdentifier (R-32.57).
    /// Type-agnostic on purpose: an id may surface as .other / .button / .staticText / .textField.
    func div(_ id: String) -> XCUIElement {
        descendants(matching: .any).matching(identifier: id).firstMatch
    }
}
```

Helpers — signatures + contracts (implementer writes bodies; all take the `XCUIApplication` as `self` via the
extension, or a `BaseUITest` method — implementer's choice, keep consistent):

- `func waitForDiv(_ id: String, timeout: TimeInterval = 10) ` — assert `div(id).waitForExistence(timeout:)`;
  on false `XCTFail("div '\(id)' never appeared")` with `file`/`line` params so failures point at the caller.
- `func assertDivExists(_ id: String)` — `XCTAssertTrue(div(id).exists, "div '\(id)' not present")`.
- `func assertDivAbsent(_ id: String)` — `XCTAssertFalse(div(id).exists, "div '\(id)' unexpectedly present")`.
  (Used for popup-gone and `zero_skeleton`-absent, mirroring Android `assertDivAbsent`.)
- `func waitForDivAbsent(_ id: String, timeout: TimeInterval = 10)` — poll `!div(id).exists` until true or
  timeout; final `assertDivAbsent`. (Polling counterpart; needed because the phase-2 network swap that removes
  `zero_skeleton` is async.)
- `func tapDiv(_ id: String)` — resolve `div(id)`; if not `isHittable`, first call
  `scrollDiv(container:…, toDiv: id)` for the current screen's scroll container (caller passes it, see below);
  then `.tap()`. Simplest form: `scrollDivIntoView(id)` helper that knows the active container, OR the caller
  scrolls first and `tapDiv` just taps. **Decision:** make `tapDiv` tap-only (assume already hittable); expose
  a separate `scrollDiv(_ container:String, to target:String, maxSteps:Int = 24)` the test calls before tapping
  a below-fold element. Keeps behaviour explicit (mirrors Android's explicit `scrollDivIntoView` + `clickDivId`).
- `func typeIntoDiv(_ id: String, _ text: String)` — `let e = div(id); e.tap()` (focus); then prefer a nested
  field: `let field = e.descendants(matching: .textField).firstMatch; (field.exists ? field : e).typeText(text)`.
  Use latin text to avoid IME edge cases (the assertion is structure-only, so any query works).
- `func assertDivChildrenAtLeast(_ id: String, _ n: Int, timeout: TimeInterval = 10)` — poll until
  `div(id).descendants(matching: .staticText).count >= n`; final `XCTAssert`. (City-search rows each carry a
  text; robust to hit-vs-"not found".)
- `func scrollDiv(_ container: String, to target: String, maxSteps: Int = 24)` — loop up to `maxSteps`:
  if `div(target).exists && div(target).isHittable` → return;
  else `div(container).swipeUp(velocity: .default)` (fallback `swipeUp()` on `self` if the container element is
  not hittable); `usleep(150_000)` to let layout settle. On exhaustion return silently (the subsequent
  `waitForDiv`/`tap` fails loudly). Mirrors Android `scrollDivIntoView` (manual scroll, no auto-scroll).
- `func dismissPopupIfPresent(timeout: TimeInterval = 5)` — if `div("popup_close").waitForExistence(timeout:)`,
  `tapDiv("popup_close")` then `waitForDivAbsent("popup_close")`; else no-op. (Used by tests that don't care
  about the popup.)

### 3.2 `BaseUITest.swift` — shared base class (health check + launch)

```swift
import XCTest

class BaseUITest: XCTestCase {
    let app = XCUIApplication()
    static let backendURL = "http://localhost:8080"

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    /// Configure launch env then launch fresh. `resetState` false only for the popup-persistence relaunch.
    func launch(baseURL: String = backendURL, resetState: Bool = true) {
        app.launchEnvironment["WDK_BASE_URL"]  = baseURL
        app.launchEnvironment["WDK_UITEST"]    = "1"
        app.launchEnvironment["WDK_RESET_STATE"] = resetState ? "1" : "0"
        app.launch()
    }

    /// Mirror of Android requireBackendUp: fail loudly if the live backend isn't serving /document.
    func requireBackendUp(file: StaticString = #file, line: UInt = #line) {
        let url = URL(string: "\(Self.backendURL)/document?lang=ru")!
        var req = URLRequest(url: url); req.timeoutInterval = 5
        let sem = DispatchSemaphore(value: 0); var ok = false; var detail = ""
        URLSession.shared.dataTask(with: req) { _, resp, err in
            if let http = resp as? HTTPURLResponse, http.statusCode == 200 { ok = true }
            else { detail = err?.localizedDescription ?? "HTTP \((resp as? HTTPURLResponse)?.statusCode ?? -1)" }
            sem.signal()
        }.resume()
        _ = sem.wait(timeout: .now() + 6)
        XCTAssertTrue(ok, "Backend not reachable at \(Self.backendURL)/document — start it (cd backend && ./gradlew run). \(detail)", file: file, line: line)
        if !ok { fatalError("backend down") } // hard stop; continueAfterFailure=false already set
    }

    func fetchLiveDocument(lang: String) -> Data {
        let url = URL(string: "\(Self.backendURL)/document?lang=\(lang)")!
        let sem = DispatchSemaphore(value: 0); var out = Data()
        URLSession.shared.dataTask(with: url) { d, _, _ in out = d ?? Data(); sem.signal() }.resume()
        _ = sem.wait(timeout: .now() + 6)
        XCTAssertFalse(out.isEmpty, "empty /document body for lang=\(lang)")
        return out
    }
}
```

### 3.3 Test classes (mirror Android; §5 for the case-by-case step lists)
- `WeatherUiTests.swift`  ← `WeatherUiTest.kt`
- `WeatherRefetchTests.swift` ← `WeatherRefetchTest.kt`
- `WeatherOfflineTests.swift` ← `WeatherOfflineTest.kt` + the `zero_skeleton` assertion from `WeatherZeroSkeletonTest.kt`
- `SmokeTests.swift` ← `RegistrationSmokeTest.kt` (launch + custom-view/gallery bind without crash) + a tree dump

### 3.4 `RecordingBackend.swift` — PIN (load-bearing infra; the refetch-count mechanism)

A minimal loopback HTTP/1.1 server run **inside the test process on the simulator**. It serves the two
live-captured `/document` bodies (by `lang`) and records every `/document` request path — the direct analog of
Android's `MockWebServer` + `LangDispatcher`. `Connection: close` per response ⇒ one TCP connection = one
request line ⇒ trivial, robust counting. The app reaches it via `http://127.0.0.1:<port>` (same simulator
loopback).

```swift
import Network
import Foundation

final class RecordingBackend {
    private let listener: NWListener
    private let ruBody: Data
    private let enBody: Data
    private let queue = DispatchQueue(label: "recording.backend")
    private let lock = NSLock()
    private var _paths: [String] = []

    var port: UInt16 { listener.port?.rawValue ?? 0 }
    var requestCount: Int { lock.lock(); defer { lock.unlock() }; return _paths.count }
    func lastPath() -> String { lock.lock(); defer { lock.unlock() }; return _paths.last ?? "" }

    init(ruBody: Data, enBody: Data) throws {
        self.ruBody = ruBody; self.enBody = enBody
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        listener = try NWListener(using: params, on: .any)   // OS-assigned port
        listener.newConnectionHandler = { [weak self] c in self?.accept(c) }
    }

    func start() {
        let sem = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { if case .ready = $0 { sem.signal() } }
        listener.start(queue: queue)
        _ = sem.wait(timeout: .now() + 5)
    }
    func stop() { listener.cancel() }

    private func accept(_ c: NWConnection) { c.start(queue: queue); receive(c, buffer: Data()) }

    private func receive(_ c: NWConnection, buffer: Data) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
            guard let self else { return }
            var buf = buffer; if let data { buf.append(data) }
            if let end = buf.range(of: Data("\r\n\r\n".utf8)) {
                let header = String(decoding: buf[buf.startIndex..<end.lowerBound], as: UTF8.self)
                let reqLine = header.split(separator: "\r\n", maxSplits: 1).first.map(String.init) ?? ""
                let path = reqLine.split(separator: " ").dropFirst().first.map(String.init) ?? ""
                self.respond(c, path: path)
            } else if done || err != nil { c.cancel() }
            else { self.receive(c, buffer: buf) }
        }
    }

    private func respond(_ c: NWConnection, path: String) {
        let body: Data
        if path.hasPrefix("/document") {
            lock.lock(); _paths.append(path); lock.unlock()
            body = path.contains("lang=en") ? enBody : ruBody
        } else {
            body = Data("{}".utf8)   // /city-search etc. — parse fails harmlessly, no-op
        }
        var head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
        head += "Content-Length: \(body.count)\r\nConnection: close\r\n\r\n"
        var out = Data(head.utf8); out.append(body)
        c.send(content: out, completion: .contentProcessed { _ in c.cancel() })
    }
}
```

### 3.5 `Brightness.swift` — PIN (theme assertion, fiddly pixel extraction)

```swift
import XCTest
import UIKit

/// Average brightness (0…1) of a middle horizontal band of the current screen. Theme flips it:
/// light theme is bright, dark theme is dark, regardless of exact content.
func screenBrightness(_ app: XCUIApplication) -> CGFloat {
    guard let cg = app.screenshot().image.cgImage else { return 1 }
    let W = cg.width, H = cg.height
    let crop = CGRect(x: 0, y: Int(Double(H) * 0.30), width: W, height: Int(Double(H) * 0.30))
    guard let region = cg.cropping(to: crop) else { return 1 }
    var px = [UInt8](repeating: 0, count: 4)
    let ctx = CGContext(data: &px, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                        space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.interpolationQuality = .medium
    ctx.draw(region, in: CGRect(x: 0, y: 0, width: 1, height: 1))  // scale whole band → 1px average
    return (CGFloat(px[0]) + CGFloat(px[1]) + CGFloat(px[2])) / 3 / 255
}
```
Theme test uses the **relative** form (more robust than an absolute 0.5 cut): capture brightness after
`theme_btn_light` and after `theme_btn_dark`, assert `light > dark + 0.05`. Poll after each toggle: re-read
brightness up to ~3 s until it stabilises past the previous value (the re-render is async).

---

## §4 — PRODUCTION CHANGE (the ONLY one) — launch-env hook

Two files. Test-only-effecting: the env vars are set solely by the XCUITest `launchEnvironment`; in production
none are present → default base URL, debug overlay on, no wipe.

**`ios/WeatherDivKit/Config/AppConfig.swift`** — add one flag:
```swift
enum AppConfig {
    static var baseURL: String = "http://localhost:8080"
    static var debugOverlayEnabled: Bool = true   // UITests flip off via WDK_UITEST
}
```

**`ios/WeatherDivKit/App/AppDelegate.swift`** — call at the TOP of
`didFinishLaunchingWithOptions`, BEFORE building the window (so the wipe/base-URL land before
`WeatherHostViewController.viewDidLoad` reads Persistence, builds DivKitComponents, and starts `coldStart`):
```swift
func application(_: UIApplication, didFinishLaunchingWithOptions _: ...) -> Bool {
    Self.applyTestEnvironmentIfPresent()          // <-- add
    let window = UIWindow(frame: UIScreen.main.bounds)
    ...
}

private static func applyTestEnvironmentIfPresent() {
    let env = ProcessInfo.processInfo.environment
    if let base = env["WDK_BASE_URL"], !base.isEmpty { AppConfig.baseURL = base }
    if env["WDK_UITEST"] == "1" { AppConfig.debugOverlayEnabled = false }
    if env["WDK_RESET_STATE"] == "1" { wipePersistentState() }
}

private static func wipePersistentState() {
    if let id = Bundle.main.bundleIdentifier {
        UserDefaults.standard.removePersistentDomain(forName: id)   // Persistence prefs
    }
    let fm = FileManager.default
    if let sup = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
        try? fm.removeItem(at: sup.appendingPathComponent("divkit.values_storage"))  // stored values (popup)
    }
    if let caches = fm.urls(for: .cachesDirectory, in: .userDomainMask).first,
       let files = try? fm.contentsOfDirectory(at: caches, includingPropertiesForKeys: nil) {
        for f in files where f.lastPathComponent.hasPrefix("doc_cache_") { try? fm.removeItem(at: f) }
    }
}
```

**`ios/WeatherDivKit/WeatherHostViewController.swift`** — one-line change in `renderScreen`, gate the debug
overlay off under UITests (harmless overlay, but keeps a corner element from being covered / intercepting a tap
on a corner FAB):
```swift
// was: DebugParams(isDebugInfoEnabled: true)
Task { await divView.setSource(source, debugParams: DebugParams(isDebugInfoEnabled: AppConfig.debugOverlayEnabled)) }
```

Do NOT touch anything else in the app. Backend is FROZEN — no `accessibility.identifier` additions needed (the
id already auto-maps, §1).

---

## §5 — TEST CASES (mirror Android; structure/id/theme only)

Each test's setUp: `WeatherUiTests`/`WeatherRefetchTests` call `requireBackendUp()` first;
`WeatherOfflineTests` does not (it needs the app offline, and does not depend on :8080).

### `WeatherUiTests` (← `WeatherUiTest.kt`, 9 tests)
Common preamble unless noted: `requireBackendUp(); launch(); waitForDiv("main_scroll"); dismissPopupIfPresent()`.

1. `mainScreen_coreStructure` — preamble; `assertDivExists("header"); assertDivExists("main_scroll")`.
2. `mainScreen_galleriesAndCustom` — preamble; `assertDivExists("hourly_gallery")`;
   `scrollDiv("main_scroll", to: "sun_phase"); waitForDiv("sun_phase")`.
3. `navigation_settings_back` — preamble; `tapDiv("fab_settings"); waitForDiv("settings_scroll");
   assertDivExists("city_search_input")`; back via `app.swipeRight()` edge-swipe is unreliable → there is NO
   nav bar; **use `nav_home` for the settings return** (Android also has `nav_home`). So: scroll to `nav_home`,
   `tapDiv("nav_home"); waitForDiv("main_scroll")`. (See DIVERGENCE note.)
4. `navigation_about_back` — preamble; `tapDiv("fab_about"); waitForDiv("about_scroll")`; return to main:
   the about screen's back affordance — mirror Android `pressBack()`. iOS has no system back here; if the about
   screen has a back/home control with an id, use it; else assert about rendered and relaunch is not needed —
   **ASK if `about_scroll` has no in-screen back control** (see §7). Provisional: assert `about_scroll` exists;
   if a back id exists (grep the about card for `nav_home`/`fab_*`), tap it and `waitForDiv("main_scroll")`.
5. `citySearch_populatesResults` — preamble; `tapDiv("fab_settings"); waitForDiv("city_search_input");
   typeIntoDiv("city_search_input", "London"); tapDiv("city_search_button");
   assertDivChildrenAtLeast("city_search_results", 1)`. Do NOT assert the row text.
6. `themeToggle_changesBackground` — preamble; `tapDiv("fab_settings"); waitForDiv("settings_scroll")`;
   `scrollDiv("settings_scroll", to: "theme_btn_light"); tapDiv("theme_btn_light")`; poll brightness → `light`;
   `scrollDiv("settings_scroll", to: "theme_btn_dark"); tapDiv("theme_btn_dark")`; poll brightness → `dark`;
   assert `light > dark + 0.05`.
7. `popup_install_persistsAcrossRelaunch` — `requireBackendUp(); launch()` (reset ON);
   `waitForDiv("main_scroll"); waitForDiv("popup_install"); tapDiv("popup_install");
   waitForDivAbsent("popup_close")`; then `launch(resetState: false)` (relaunch, keep disk state);
   `waitForDiv("main_scroll"); assertDivAbsent("popup_install")`.
8. `popup_closeX_persistsAcrossRelaunch` — same as (7) with `popup_close` (× button): tap `popup_close`,
   `waitForDivAbsent("popup_close")`, relaunch `resetState:false`, `assertDivAbsent("popup_close")`.
9. `compactToggle_smoke` — preamble; `tapDiv("fab_settings"); waitForDiv("settings_scroll");
   scrollDiv("settings_scroll", to: "compact_btn_on"); tapDiv("compact_btn_on");
   scrollDiv("settings_scroll", to: "nav_home"); tapDiv("nav_home"); waitForDiv("main_scroll")`.

### `WeatherRefetchTests` (← `WeatherRefetchTest.kt`, 2 tests)
setUp: `requireBackendUp(); let ru = fetchLiveDocument("ru"); let en = fetchLiveDocument("en");
server = try RecordingBackend(ruBody: ru, enBody: en); server.start();
launch(baseURL: "http://127.0.0.1:\(server.port)")`. tearDown: `server.stop()`.

1. `languageSwitch_refetches` — `waitForDiv("main_scroll"); dismissPopupIfPresent();
   tapDiv("fab_settings"); waitForDiv("settings_scroll"); scrollDiv("settings_scroll", to: "lang_btn_en");
   let n = server.requestCount; tapDiv("lang_btn_en")`; poll until `server.requestCount > n` (timeout ~10 s,
   XCTFail on timeout); `XCTAssertTrue(server.lastPath().contains("lang=en"))`.
2. `themeToggle_noRefetch` — `waitForDiv("main_scroll"); let n = server.requestCount; dismissPopupIfPresent();
   tapDiv("fab_settings"); waitForDiv("settings_scroll"); scrollDiv("settings_scroll", to: "theme_btn_dark");
   tapDiv("theme_btn_dark")`; poll brightness → dark (confirms the toggle took effect);
   `XCTAssertEqual(server.requestCount, n)` (theme is a pure client-side global-var mutation, no fetch).

### `WeatherOfflineTests` (← `WeatherOfflineTest.kt` + `WeatherZeroSkeletonTest.kt`, 1 test)
NO `requireBackendUp`. setUp: `launch(baseURL: "http://127.0.0.1:1")` (dead port; reset ON wipes doc caches so
phase-1 falls to the bundled skeleton).
1. `offline_fallsBackToSkeleton` — `waitForDiv("main_scroll"); assertDivExists("header");
   assertDivExists("zero_skeleton")` (the skeleton marker id — proves the bundled `zero_ru.json` rendered, not
   real data). Mirrors Android `zero_skeleton_whenOfflineNoCache`.

### `SmokeTests` (← `RegistrationSmokeTest.kt`, 2 items)
1. `launchesAndBindsCustomViewsWithoutCrash` — `requireBackendUp(); launch(); waitForDiv("main_scroll");
   dismissPopupIfPresent(); scrollDiv("main_scroll", to: "sun_phase"); waitForDiv("sun_phase")` — proves the
   `SunPhaseCustomBlockFactory` custom block + `ScrollStateExtensionHandler` are registered and bind without
   crashing (the iOS analog of the inline-DivData registration smoke).
2. `dumpTree` (diagnostic, keep or mark skipped): `launch(); waitForDiv("main_scroll");
   print(app.debugDescription)` — the implementer runs this FIRST (§6 step 0) to eyeball that div ids appear as
   element identifiers before writing the rest. Attach a screenshot via `XCTAttachment`.

---

## §6 — project.yml ADDITION + build/gate commands

### 6.1 `ios/project.yml` — add the UI-test target + a scheme wiring it in
Under `targets:` add:
```yaml
  WeatherDivKitUITests:
    type: bundle.ui-testing
    platform: iOS
    deploymentTarget: "15.0"
    sources:
      - path: WeatherDivKitUITests
    dependencies:
      - target: WeatherDivKit
    settings:
      base:
        GENERATE_INFOPLIST_FILE: YES
        SWIFT_VERSION: "5.9"
        TARGETED_DEVICE_FAMILY: "1"
        PRODUCT_BUNDLE_IDENTIFIER: com.example.weatherdivkit.uitests
```
Add a top-level `schemes:` block (so `-scheme WeatherDivKit` runs the UI tests):
```yaml
schemes:
  WeatherDivKit:
    build:
      targets:
        WeatherDivKit: all
        WeatherDivKitUITests: [test]
    test:
      targets:
        - WeatherDivKitUITests
      gatherCoverageData: false
```
Then regenerate (the `.xcodeproj` is tracked in git — regenerating updates `project.pbxproj`, which the
orchestrator commits):
```
cd /Users/the-leo/divkit-weather-workshop/ios && xcodegen generate
```

### 6.2 GATE commands
```
# 1. backend up (separate terminal)
cd /Users/the-leo/divkit-weather-workshop/backend && ./gradlew run   # Ktor :8080
curl -s http://localhost:8080/ping   # → pong

# 2. generate + run the UI suite on iPhone 17
cd /Users/the-leo/divkit-weather-workshop/ios
xcodegen generate
xcodebuild \
  -project WeatherDivKit.xcodeproj \
  -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:WeatherDivKitUITests \
  test
```
PASS criteria: all `WeatherDivKitUITests` GREEN.

### 6.3 Negative gate (health check fails loudly with backend down)
Stop the backend, then run only the health-gated class:
```
xcodebuild -project WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:WeatherDivKitUITests/WeatherUiTests/mainScreen_coreStructure test
```
Expected: FAIL with the "Backend not reachable at http://localhost:8080/document — start it …" message
(NOT a silent green on the skeleton). Restart the backend before the full run.

---

## §7 — RISKS / EDGES / what to ASK

- **[PRIMARY, low residual risk] id → XCUITest reachability.** VERIFIED that div id becomes
  `accessibilityIdentifier` and that DivKit's own suite queries it via `descendants(.any).matching(identifier:)`
  (§1). Residual: a leaf action div with NO text child and NO `.button` trait *could* be pruned from the tree.
  All buttons here (`fab_*`, `theme_btn_*`, `nav_home`, `compact_*`, `lang_*`, popup buttons) have a text/icon
  child, so they surface. **Step 0 for the implementer:** run `SmokeTests.dumpTree` and confirm each id used by
  the suite appears in `app.debugDescription` BEFORE writing assertions. If a specific id is missing, fall back
  to querying its child by label text (as DivKit's own test does: `app.staticTexts["…"]`) — but do NOT assert a
  weather VALUE; use a stable static label (e.g. a settings section title). Report any such fallback.
- **[FLAG — needs a quick check] settings/about return control.** Android uses `Espresso.pressBack()`; iOS has
  no hardware back and the app's `goBack()` is driven by in-layout controls (`nav_home` on settings). Test (3)
  uses `nav_home`. For test (4) `navigation_about_back`, VERIFY whether the `about` card carries a back/home id
  (grep the live `about` screen JSON for `nav_home`/`fab_*`/a back button id). If it has one, tap it to return;
  if the about screen has NO in-layout back affordance, the "back to main" half cannot be driven via UI — in
  that case assert only that `about_scroll` renders and note the divergence. **ASK the orchestrator** only if no
  back control exists AND parity with the Android back-navigation is deemed required.
- **[MEDIUM] theme brightness flakiness.** Pixel sampling is inherently softer than id asserts. Mitigations
  already specced: sample a 30%-tall full-width band averaged to 1px; use the RELATIVE assertion
  (`light > dark + 0.05`) not an absolute cut; poll after each toggle. If still flaky, widen the band or raise
  the margin — but keep it a theme (brightness) assertion, never a value assertion.
- **[LOW] RecordingBackend port reachability.** The server binds `127.0.0.1` on the simulator; the app reaches
  it via `http://127.0.0.1:<port>` on the same simulator loopback. `Connection: close` guarantees one request
  per connection (clean counting). If `NWListener(on: .any)` picks an unusable port, `start()`'s 5 s ready-wait
  will expire and `server.port == 0` → the test fails loudly at launch. No special entitlements needed
  (loopback). `NSAppTransportSecurity.NSAllowsLocalNetworking: true` is already in Info.plist (verified) so
  cleartext http to localhost/127.0.0.1 is allowed.
- **[LOW] `UserDefaults.removePersistentDomain` timing.** Runs in `didFinishLaunchingWithOptions` before the
  host VC reads Persistence; fresh process each launch, so no stale in-process cache. Confirmed the DivKit
  stored-values `Property` reads its file lazily on first access (after our delete) → starts empty.
- **[LOW] Cyrillic input.** Avoided by using latin "London" in city search (assertion is structure-only).

---

## §8 — BOUNDARIES / OUT OF SCOPE
- Stage 5 parity work — deferred.
- Any new product feature or UX change — none. Stage 4 is tests + the single launch-env hook (§4) only.
- Backend changes — FROZEN. No `accessibility.identifier` additions (unnecessary, §1). No new endpoints.
- Snapshot/pixel-perfect visual tests, performance tests, VoiceOver/a11y-behaviour tests — out of scope.
- Android suite untouched (`app/src/androidTest/**` must NOT change; verify via `git diff` at review).
- `git diff` at review must show ONLY: `ios/WeatherDivKitUITests/**` (new), `ios/project.yml`,
  `ios/WeatherDivKit.xcodeproj/**` (regenerated), and the three small production edits in §4. Nothing else.

---

## ORCHESTRATOR NOTES (implementer may skip)
- Why the launch-env hook and not a compile-time test flag: XCUITest is a separate process; the only channel
  into the app at launch is `launchEnvironment`/`launchArguments`. Env chosen over args for the base URL string.
- Why RecordingBackend (a serving mock) over a forwarding proxy: the Android test also serves live-captured
  bodies via MockWebServer rather than proxying; serving avoids HTTP keep-alive framing complexity in a raw TCP
  pipe, and `Connection: close` makes request counting exact. The refetch tests never hit `/city-search`.
- Why not assert popup via `recreate()`: XCUITest cannot recreate a VC in-process; the faithful analog is a
  terminate+relaunch that keeps disk state (stored values persist to `divkit.values_storage`), which proves the
  same invariant (dismissal survives) more strongly than Android's config-change recreate.
- Calibration: production method bodies (host VC glue) are NOT pre-written; only the launch-env hook (exact form
  is load-bearing), the locator primitive, RecordingBackend, and Brightness are pinned. Test-case bodies are
  given as ordered step lists — the implementer writes the Swift.
- If Step 0 (`dumpTree`) shows the ids do NOT appear as identifiers (contradicting §1), STOP and escalate — do
  not paper over it with brittle coordinate taps.
