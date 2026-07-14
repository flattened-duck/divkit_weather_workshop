# iOS Stage 0 — Foundation — IMPLEMENTATION CONTRACT (kopatel)

You are building the FOUNDATION of a brand-new iOS client for the DivKit weather app, under `ios/`.
Behavioral reference = the Android client (`app/src/main/java/com/example/weatherdivkit/`). You port
BEHAVIOR, not code. Stage 0 is deliberately minimal: create the Xcode project, add DivKit via SPM,
freeze the interface seams later stages depend on, and render exactly ONE screen (`main`) from the
live backend. Everything else is OUT OF SCOPE (see §H).

All facts below were verified against the DivKit iOS source at `/Users/the-leo/divkit_source/divkit`
(git branch `R-32.57`, tag `32.57.0`). Citations are `path:line`. Do NOT re-derive; do NOT "improve"
the API calls — use them verbatim.

---

## A. MUST-NOT-GET-WRONG (read first, 8 bullets)

1. **Every** `xcodebuild`/`xcrun`/`simctl`/`xcodegen`-driving command MUST be prefixed with
   `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`. Do NOT run `sudo xcode-select -s`.
2. DivKit SPM dependency = **LOCAL PATH** to `/Users/the-leo/divkit_source/divkit/client/ios`
   (products `DivKit`, `DivKitExtensions`). The GitHub URL `github.com/divkit/divkit` is a MONOREPO
   with `Package.swift` nested at `client/ios/` → it does **NOT** resolve as a git-URL SPM package.
   See §B for the exact spec and the mirror-repo fallback rule.
3. The backend envelope is `{ "templates": {…}, "screens": { "main":…, "settings":…, "about":… } }`.
   DivKit's `.data(Data)` source expects a DIFFERENT shape: `{ "templates": {…}, "card": {…} }`.
   You MUST rebuild it: `{ "templates": <envelope.templates>, "card": <envelope.screens.main> }`.
4. Use the **async** `setSource`: `await divView.setSource(_:debugParams:)`. The synchronous overload
   is `@_spi(Legacy)` — do not use it.
5. Base URL is `http://localhost:8080` (NOT `10.0.2.2` — that is Android-only). It MUST live in a
   single mutable static (`AppConfig.baseURL`) so tests can flip it later.
6. Render ONLY `main` in Stage 0. Do NOT build settings/about, navigation, url actions, global vars,
   extensions, images theming, offline, or tests.
7. Freeze the three interface seams EXACTLY as declared in §D (`DocumentLoading`, `HostActions`,
   `DivComponentsFactory`). Parallel Stage-2/3 tracks compile against these — signatures are a contract.
8. Gate proof = the `main` screen renders on `iPhone 17` simulator against the live backend AND the
   console shows ZERO `DIVKIT_RENDER_ERROR` lines (mechanism in §F). Commit the generated `.xcodeproj`.

---

## B. Dependency spec (SPM) — DECIDED

**PRIMARY (use this):** local Swift Package path dependency.
- Package location: `/Users/the-leo/divkit_source/divkit/client/ios` (this is where `Package.swift`
  lives — verified `Package.swift:26` declares `name: "DivKit"`, `platforms: .iOS(.v13)` at line 29,
  and products `DivKit` (line 33) + `DivKitExtensions` (line 34)).
- Products to link: `DivKit`, `DivKitExtensions`.
- The local checkout is pinned at git tag `32.57.0` (verified: `git describe --tags` → `32.57.0`,
  branch `R-32.57`). This IS the 32.57.0 source — no version drift.
- Transitive deps `vgsl` (`github.com/yandex/vgsl` from `7.21.0-0`) and `swift-markdown` (exact
  `0.6.0`) are fetched from GitHub on first resolve (`Package.swift:6-22`). Network is required for
  the FIRST build only; afterwards SPM caches them. `Package.resolved` will pin their resolved
  versions — commit it.

**FALLBACK (only if the local path is rejected/unavailable):** the SPM distribution mirror
`https://github.com/divkit/divkit-ios` at exact version `32.57.0` (this is DivKit's dedicated iOS
SPM repo with `Package.swift` at its root; the monorepo `divkit/divkit` is NOT usable as an SPM URL).
Decision rule: try PRIMARY first. Only switch to the mirror if `xcodegen`/SPM cannot open the local
`Package.swift`, and if the mirror tag resolves over the network. If NEITHER resolves, STOP and ask
the orchestrator — do not invent another source.

**Do NOT** add `DivKitMarkdownExtension`, `DivKitSVG`, `LayoutKit`, or `Serialization` as explicit
product deps in Stage 0 — `DivKit`/`DivKitExtensions` pull the rest transitively.

---

## C. Project generation — DECIDED: xcodegen

Rationale: `xcodegen`/`tuist` are NOT installed; `brew` is at `/opt/homebrew/bin/brew`. A hand-written
`.pbxproj` is error-prone and unreviewable; a pure Swift Package can't host a runnable iOS **app**
target cleanly with an Info.plist/launch config. A declarative `project.yml` + `xcodegen` is the
smallest reproducible path. The generated `.xcodeproj` MUST be committed (orchestrator commits).

Install once: `/opt/homebrew/bin/brew install xcodegen`.

### C.1 `ios/project.yml` (author this file verbatim; adjust only if the build tells you to)

```yaml
name: WeatherDivKit
options:
  bundleIdPrefix: com.example
  deploymentTarget:
    iOS: "15.0"
  createIntermediateGroups: true
packages:
  DivKit:
    path: /Users/the-leo/divkit_source/divkit/client/ios
targets:
  WeatherDivKit:
    type: application
    platform: iOS
    deploymentTarget: "15.0"
    sources:
      - path: WeatherDivKit
    dependencies:
      - package: DivKit
        product: DivKit
      - package: DivKit
        product: DivKitExtensions
    info:
      path: WeatherDivKit/App/Info.plist
      properties:
        CFBundleDisplayName: WeatherDivKit
        UILaunchScreen: {}
        UIApplicationSupportsIndirectInputEvents: true
        UISupportedInterfaceOrientations:
          - UIInterfaceOrientationPortrait
        NSAppTransportSecurity:
          NSAllowsLocalNetworking: true
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.example.weatherdivkit
        TARGETED_DEVICE_FAMILY: "1"
        SWIFT_VERSION: "5.9"
        INFOPLIST_KEY_UIApplicationSceneManifest_Generation: NO
```

Notes:
- Min deployment target = **iOS 15.0** (умник-decision). DivKit's floor is iOS 13 (`Package.swift:29`);
  15.0 is comfortably above it and below the sim runtimes (26.2/26.5), and gives modern `async/await`
  UIKit. Any value in `[13.0, 26.2]` is legal; use 15.0.
- No `UIMainStoryboardFile` and no `UIApplicationSceneManifest` → the app uses the classic
  `AppDelegate.window` lifecycle (see §E AppDelegate). `UILaunchScreen: {}` gives a blank system
  launch screen with no storyboard file needed.
- `NSAllowsLocalNetworking: true` is a belt-and-suspenders ATS exception for `http://localhost`.
  Loopback is generally ATS-exempt, but this key removes any ambiguity in the Simulator. Remote
  images later load over HTTPS (`raw.githubusercontent.com`) — no further ATS keys needed.

Generate: `cd ios && DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer /opt/homebrew/bin/xcodegen generate`
(xcodegen itself doesn't need `DEVELOPER_DIR`, but keep the prefix habit; it writes
`ios/WeatherDivKit.xcodeproj` and `ios/WeatherDivKit/App/Info.plist`).

---

## D. FROZEN INTERFACE SEAMS (contract — declare EXACTLY; other tracks depend on these)

These three are the load-bearing decoupling. Declare them verbatim. Later stages implement concrete
types and only APPEND to the factory — they never edit these signatures.

### D.1 `Screen` (Document/Screen.swift)

```swift
import DivKit

enum Screen: String, CaseIterable {
    case main
    case settings
    case about

    var cardId: DivCardID { DivCardID(rawValue: rawValue) }
}
```
(`DivCardID = Tagged<CardIDTag, String>` — verified `DivKit/DivCardID.swift:5`; it is
`ExpressibleByStringLiteral`, so `DivCardID(rawValue:)` and string literals both work.)

### D.2 `DocumentLoading` (Document/DocumentLoading.swift)

```swift
import DivKit
import Foundation

/// Fetches + parses the /document envelope into per-screen renderable sources.
/// Stage 0 concrete impl populates only `.main`; Stage 1+ populate all three and add caching.
protocol DocumentLoading {
    func load(
        lang: String,
        lat: String?,
        lon: String?,
        name: String?
    ) async throws -> DocumentBundle
}

struct DocumentBundle {
    /// Renderable source per screen. Stage 0: only `.main` present.
    let sources: [Screen: DivViewSource]
    /// Raw successful response body (for the Stage 3 cache-to-disk seam). Unused in Stage 0.
    let rawBody: Data
}
```
Return type is `DivViewSource` (not `DivData`) on purpose: it abstracts `.data`/`.divData`, so Stage 1
can switch the concrete loader to parse-once `.divData` sources WITHOUT changing this protocol.

### D.3 `HostActions` (DivKitHost/HostActions.swift)

```swift
/// Callback surface the url handler invokes. Stage 0: the host conforms with STUB (log-only) methods;
/// full routing is wired in Stage 1.
protocol HostActions: AnyObject {
    func navigate(to screen: Screen)
    func back()
    func setLang(_ lang: String)
    func setTheme(_ theme: String)
    func setCompact(_ compact: Bool)
    func setCity(lat: String, lon: String, name: String)
    func citySearch(query: String)
}
```

### D.4 `DivComponentsFactory` (DivKitHost/DivComponentsFactory.swift)

Append-only registration seam. Stage 2/3 tracks only mutate these stored properties (append a handler,
set the custom-block factory / patch provider) — never touch `makeComponents()`'s body except the one
line that already forwards each property.

```swift
import DivKit

/// The SINGLE assembly point for DivKitComponents. Configure the stored properties, then call
/// makeComponents() exactly once. Registration is append-only so parallel tracks don't conflict.
final class DivComponentsFactory {
    var extensionHandlers: [DivExtensionHandler] = []      // S2B scroll_state, built-in Shimmers append here
    var customBlockFactory: DivCustomBlockFactory?         // S2A sun_phase sets this
    var patchProvider: DivPatchProvider?                   // S3A city-search may set this
    var urlHandler: DivUrlHandler?                          // S1 sets the real WeatherUrlHandler
    let variablesStorage = DivVariablesStorage()           // S1 seeds global vars here
    var reporter: DivReporter?                             // Stage 0 sets LoggingDivReporter

    func makeComponents() -> DivKitComponents {
        DivKitComponents(
            divCustomBlockFactory: customBlockFactory,
            extensionHandlers: extensionHandlers,
            patchProvider: patchProvider,
            reporter: reporter,
            urlHandler: urlHandler ?? DivUrlHandlerDelegate { _ in },
            variablesStorage: variablesStorage
        )
    }
}
```
Verified against `DivKit/DivKitComponents.swift:100-120` — the init signature has ALL of these as
optional params with defaults: `divCustomBlockFactory: DivCustomBlockFactory? = nil` (l.102),
`extensionHandlers: [DivExtensionHandler] = []` (l.103), `patchProvider: DivPatchProvider? = nil`
(l.108), `reporter: DivReporter? = nil` (l.110), `urlHandler: DivUrlHandler = DivUrlHandlerDelegate { _ in }`
(l.119), `variablesStorage: DivVariablesStorage = DivVariablesStorage()` (l.120). `DivUrlHandlerDelegate`
is public (`DivKit/Actions/DivUrlHandler.swift:37`). `imageHolderFactory` is INTENTIONALLY omitted —
see §G image-holder note (default is auto-provided).

---

## E. Concrete Stage-0 implementations (signatures + rules; you write the bodies)

Write the method BODIES yourself following these rules and the pinned literals in §I. Do not paste
rules as comments verbatim; implement them.

### E.1 `Config/AppConfig.swift`
```swift
enum AppConfig {
    static var baseURL: String = "http://localhost:8080"
}
```
Analog of Android `DocumentLoader.baseUrl` static (`DocumentLoader.kt:172`). Mutable static so tests
flip it later.

### E.2 `DivKitHost/WeatherUrlHandler.swift` — STUB
```swift
import DivKit
import Foundation

final class WeatherUrlHandler: DivUrlHandler {
    weak var actions: HostActions?
    init(actions: HostActions?) { self.actions = actions }

    func handle(_ url: URL, info: DivActionInfo, sender: AnyObject?) {
        // Stage 0 STUB: log only. Full weather-app:// routing → HostActions is Stage 1.
    }
}
```
Rules: implement the `handle(_:info:sender:)` overload (verified required member set at
`DivKit/Actions/DivUrlHandler.swift:10-27`; `handle(_:sender:)` has a default). Body: `print` the URL
with a stable prefix (e.g. `"WeatherUrlHandler: \(url)"`). Do NOT route to `actions` yet.

### E.3 `DivKitHost/LoggingDivReporter.swift`
```swift
import DivKit

final class LoggingDivReporter: DivReporter {
    func reportError(cardId: DivCardID, error: DivError) {
        print("DIVKIT_RENDER_ERROR [\(cardId.rawValue)] \(error)")
    }
}
```
`DivReporter` requires `reportError(cardId:error:)` (verified `DivKit/DivReporter.swift:6-8`); all other
members have default impls (`:24-46`). The `DIVKIT_RENDER_ERROR` prefix is the gate probe in §F.

### E.4 `Document/DocumentLoader.swift` — concrete `DocumentLoading`
```swift
final class DocumentLoader: DocumentLoading {
    func load(lang: String, lat: String?, lon: String?, name: String?) async throws -> DocumentBundle
}
```
Body rules:
1. Build the URL with `URLComponents` from `AppConfig.baseURL + "/document"`. Query items: always
   `lang=<lang>`; append `lat`/`lon`/`name` ONLY when the arg is non-nil AND non-empty (mirror
   `DocumentLoader.kt:66-71`). Stage 0 always calls with `lang:"ru"`, others `nil`.
2. `let (data, response) = try await URLSession.shared.data(from: url)`. Throw if the HTTP status is
   not 200 (cast `response as? HTTPURLResponse`).
3. Parse: `let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]` — throw a
   descriptive error if nil.
4. `let templates = root["templates"] as? [String: Any] ?? [:]`.
   `let screens = root["screens"] as? [String: Any]` — throw if missing.
   `let mainCard = screens["main"] as? [String: Any]` — throw if missing.
5. Rebuild the DivKit source dict EXACTLY: `["templates": templates, "card": mainCard]`. Serialize:
   `let sourceData = try JSONSerialization.data(withJSONObject: sourceDict)`.
6. `let source = DivViewSource(kind: .data(sourceData), cardId: Screen.main.cardId)`
   (verified `DivKit/Views/DivViewSource.swift:6-9,20-25`: `Kind.data(Data)`, `init(kind:cardId:)`).
7. Return `DocumentBundle(sources: [.main: source], rawBody: data)`.
8. Define a small `enum DocumentLoaderError: Error` (e.g. `.badStatus(Int)`, `.malformed(String)`)
   for the throws above.
Do NOT parse or build `.settings`/`.about` — Stage 1.

### E.5 `WeatherHostViewController.swift` — the spine (minimal)
Mirror `Samples/UIKitIntegration/DivKitSample/DivHostViewController.swift` (verified) but fetch from
the network instead of a bundled file.
```swift
import DivKit
import DivKitExtensions
import UIKit

final class WeatherHostViewController: UIViewController, HostActions {
    private let loader: DocumentLoading = DocumentLoader()
    private lazy var divKitComponents: DivKitComponents = makeComponents()
    private lazy var divView = DivView(divKitComponents: divKitComponents)
    // ...
}
```
Body rules:
- `makeComponents()`: build a `DivComponentsFactory`, set `factory.reporter = LoggingDivReporter()`
  and `factory.urlHandler = WeatherUrlHandler(actions: self)`, then `return factory.makeComponents()`.
  (Leave `extensionHandlers`/`customBlockFactory`/`patchProvider` at defaults — later stages.)
- `viewDidLoad()`: `super`; `view.addSubview(divView)`; `Task { await loadAndRender() }`.
- `viewWillLayoutSubviews()`: `super`; `divView.frame = view.bounds.inset(by: view.safeAreaInsets)`
  (verified pattern, `DivHostViewController.swift:18-21`).
- `loadAndRender()` (async): `let bundle = try? await loader.load(lang: "ru", lat: nil, lon: nil,
  name: nil)`; `guard let source = bundle?.sources[.main] else { return }`; then
  `await divView.setSource(source, debugParams: DebugParams(isDebugInfoEnabled: true))`
  (verified async `setSource(_:debugParams:shouldResetPreviousCardData:)` at `DivKit/Views/DivView.swift:148`;
  `DebugParams(isDebugInfoEnabled:)` at `DivKit/.../DebugParams.swift`). On thrown/nil load, do nothing
  visible in Stage 0 (offline handling is Stage 3) — but let the error surface via `print`.
- `HostActions` conformance: all 7 methods are STUBS — each just `print`s its call. No behavior yet.

### E.6 `App/AppDelegate.swift`
```swift
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_: UIApplication,
                     didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = WeatherHostViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
```
(Classic window lifecycle — no scene manifest, no storyboard, matching the Info.plist in §C.1.)

---

## F. Build / run / verify (all commands; keep the DEVELOPER_DIR prefix)

Let `DD=DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` and `SIM='iPhone 17'`.

1. Generate: `cd ios && $DD /opt/homebrew/bin/xcodegen generate`
2. Build:
   ```
   $DD xcodebuild \
     -project ios/WeatherDivKit.xcodeproj \
     -scheme WeatherDivKit \
     -sdk iphonesimulator \
     -destination 'platform=iOS Simulator,name=iPhone 17' \
     build
   ```
   (Run from repo root `/Users/the-leo/divkit-weather-workshop`. First build resolves vgsl +
   swift-markdown from GitHub — allow it network + time.)
3. Confirm the backend is up: `curl -s -o /dev/null -w '%{http_code}' 'http://localhost:8080/document?lang=ru'`
   → expect `200`. If not, start it: `cd backend && ./gradlew run` (do NOT modify backend).
4. Boot + install + launch, capturing console (to read render errors):
   ```
   $DD xcrun simctl boot 'iPhone 17' 2>/dev/null; open -a Simulator
   APP="$($DD xcodebuild -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
        -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' \
        -showBuildSettings 2>/dev/null | awk -F' = ' '/ BUILT_PRODUCTS_DIR /{print $2; exit}')/WeatherDivKit.app"
   $DD xcrun simctl install 'iPhone 17' "$APP"
   $DD xcrun simctl launch --console-pty 'iPhone 17' com.example.weatherdivkit
   ```
   `--console-pty` streams the app's stdout so you can see `DIVKIT_RENDER_ERROR …` lines (and the
   `WeatherUrlHandler:` / stub prints). Let it run a few seconds, then Ctrl-C.
5. Screenshot: `$DD xcrun simctl io 'iPhone 17' screenshot /tmp/ios_stage0_main.png`
   (or use the `mobile-mcp` screenshot tool). Confirm visible weather content for the `main` screen.

### GATE (all must hold)
- Build in step 2 succeeds.
- App launches and the `main` screen shows real weather content (not blank, not just a spinner).
- Console in step 4 shows **ZERO** `DIVKIT_RENDER_ERROR` lines.
- With `DebugParams(isDebugInfoEnabled: true)`, DivKit renders a small red error-counter badge ONLY if
  there are errors — the screenshot must show NO such error badge. (You may set `isDebugInfoEnabled`
  back to `false` after verifying, but leave it `true` for the reviewer/verifier.)

---

## G. Verified API reference (cite when in doubt)

| Fact | Value | Source |
|---|---|---|
| Package name / products | `DivKit`, `DivKitExtensions` | `client/ios/Package.swift:26,33,34` |
| Min iOS floor | `.iOS(.v13)` | `Package.swift:29` |
| Local checkout version | tag `32.57.0`, branch `R-32.57` | `git describe --tags` |
| Host pattern | `DivView(divKitComponents:)`, addSubview, frame in `viewWillLayoutSubviews`, `await divView.setSource(.init(kind:.data(data),cardId:),debugParams:)` | `Samples/UIKitIntegration/DivKitSample/DivHostViewController.swift:5-42` |
| Components init params (all optional) | `divCustomBlockFactory`, `extensionHandlers`, `patchProvider`, `reporter`, `urlHandler`, `variablesStorage` (+ more) | `DivKit/DivKitComponents.swift:100-120` |
| **Image holder** | AUTO-defaulted to `DefaultImageHolderFactory(requestPerformer:)` when `imageHolderFactory == nil` → handles remote (HTTPS) images with built-in caching. **No image holder needed in Stage 0.** | `DivKit/DivKitComponents.swift:106,146-148` |
| Source kinds | `enum Kind { case json([String:Any]); case data(Data); case divData(DivData) }`, `init(kind:cardId:)` | `DivKit/Views/DivViewSource.swift:6-25` |
| `.data` expected shape | `{ "templates": {…}, "card": {…} }` | `Samples/.../Sample.json` top keys `[templates, card]` |
| setSource (async, use this) | `func setSource(_:DivViewSource, debugParams:DebugParams = .init(), shouldResetPreviousCardData:Bool = false) async` | `DivKit/Views/DivView.swift:148-166` |
| setSource (sync) — AVOID | `@_spi(Legacy)` overload | `DivKit/Views/DivView.swift:175-193` |
| DivCardID | `typealias DivCardID = Tagged<CardIDTag, String>` (string-literal expressible) | `DivKit/DivCardID.swift:5` |
| DivUrlHandler | `func handle(_ url:URL, info:DivActionInfo, sender:AnyObject?)` (impl this; `handle(_:sender:)` has default) | `DivKit/Actions/DivUrlHandler.swift:10-35` |
| DivUrlHandlerDelegate | public struct, `init(_ handle:@escaping (URL)->Void)` | `DivKit/Actions/DivUrlHandler.swift:37-51` |
| DivReporter | `func reportError(cardId:DivCardID, error:DivError)` required; rest defaulted | `DivKit/DivReporter.swift:6-46` |
| DebugParams | `init(isDebugInfoEnabled:Bool = false, …)`; when true shows on-screen error counter | `DivKit/.../DebugParams.swift` |
| Live envelope shape | `{templates:{}, screens:{main,settings,about}}`; `main` = `{log_id, states, variables}` | `curl localhost:8080/document?lang=ru` |

---

## H. OUT OF SCOPE for Stage 0 (do NOT implement — later stages)

- Navigation / back stack, rendering `settings` + `about` (Stage 1).
- `weather-app://` url actions routing, `set_lang`/`set_theme`/`set_compact`/`set_city`/`city_search`
  (Stage 1) — Stage 0's `WeatherUrlHandler` and `HostActions` methods are LOG-ONLY stubs.
- Global reactive variables (theme/theme_mode/compact/header_state/status_inset/nav_inset) (Stage 1).
- `sun_phase` DivCustomBlock, `scroll_state` extension, built-in Shimmer registration (Stage 2).
- Image/theme day-night backgrounds, status-bar theming, safe-area/insets wiring (Stage 2 — note the
  default image holder still loads images, but no theming/insets logic in Stage 0).
- City search + DivPatch (Stage 3A). Offline cache + zero skeleton + PTR + graceful lang switch (Stage 3B).
- Any UI/unit tests, the `Resources/zero_ru.json` asset, `GlobalVariables.swift`, `Extensions/*` files
  (later stages create these — do NOT create empty stub files for them now; only create the §I file tree).

---

## I. Exact file tree to create under `ios/` (Stage 0 only)

```
ios/
  project.yml                                  # §C.1 xcodegen spec
  WeatherDivKit.xcodeproj/                      # GENERATED by xcodegen — COMMIT it
  WeatherDivKit/
    App/
      AppDelegate.swift                        # §E.6 window lifecycle
      Info.plist                               # GENERATED by xcodegen from project.yml `info:`
    Config/
      AppConfig.swift                          # §E.1 baseURL static
    Document/
      Screen.swift                             # §D.1 frozen
      DocumentLoading.swift                    # §D.2 frozen (protocol + DocumentBundle)
      DocumentLoader.swift                     # §E.4 concrete, main-only
    DivKitHost/
      HostActions.swift                        # §D.3 frozen
      DivComponentsFactory.swift               # §D.4 frozen registration seam
      WeatherUrlHandler.swift                  # §E.2 stub
      LoggingDivReporter.swift                 # §E.3 render-error probe
    WeatherHostViewController.swift            # §E.5 spine (minimal)
```
Also expect `ios/WeatherDivKit/.../` to gain SPM artifacts under the project's DerivedData — those are
NOT committed. DO commit: `ios/project.yml`, `ios/WeatherDivKit.xcodeproj/**` (incl. generated
`project.pbxproj` and the `Package.resolved` under
`WeatherDivKit.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`), and all `.swift`
sources + `Info.plist`. (You do not commit — you report; the orchestrator commits. But leave the tree
in that committable state and list exactly what changed.)

Do NOT touch anything outside `ios/` (backend, Android `app/`, existing `plan/*`). Confirm with
`git status` before reporting.

---

## J. What to report back

1. Build result (pass/fail + the exact `xcodebuild` tail on failure).
2. Whether PRIMARY (local path) resolved or you fell back to the mirror (and why).
3. Path to the gate screenshot + a one-line description of what's on screen.
4. The captured console section proving ZERO `DIVKIT_RENDER_ERROR` lines (or paste any that appeared).
5. `git status --porcelain` output (prove only `ios/**` changed).
6. Any deviation from this contract and its justification, or any point where you had to ASK.

---

## ORCHESTRATOR NOTES (appendix — implementer applies the chosen defaults; skip on first read)

- **SPM source decision:** I verified `github.com/divkit/divkit` is a monorepo whose `Package.swift`
  is nested at `client/ios/` — SPM cannot consume it via git URL (SPM requires a root manifest). Hence
  the **local path** primary. The `divkit/divkit-ios` mirror is the documented network fallback; I could
  not hit the network to confirm the mirror tag resolves, so the decision rule (local → mirror → ask)
  is explicit in §B. If you (orchestrator) prefer a portable committed project, the mirror is the way,
  but local path is the only path I could VERIFY resolves offline.
- **Top RISK — warnings-as-errors under Xcode 26.5:** DivKit's targets compile with
  `-warnings-as-errors` (`Package.swift:24`). It is source-built here (local path), so the DivKit
  32.57 sources get recompiled by Xcode 26.5's newer Swift compiler, which may emit NEW warnings →
  hard errors. If the build fails inside a `DivKit`/`LayoutKit`/`Serialization` target with warning
  diagnostics, this is the cause. Mitigations to try in order, and REPORT which was needed: (a) confirm
  it reproduces; (b) if it's the DivKit targets, the cleanest offline fix is to consume the mirror as a
  binary/tagged package if available; (c) as a last resort, ask the orchestrator before patching vendor
  sources — do NOT silently edit `/Users/the-leo/divkit_source`. This is the single most likely Stage-0
  blocker; surface it early (run the build before writing much glue).
- **Portability wart:** the committed `.pbxproj` will embed the absolute local package path
  `/Users/the-leo/divkit_source/divkit/client/ios`. Fine for this single-machine workshop; flagged so
  the orchestrator knows the project isn't clone-portable until the mirror is adopted.
- **iOS-divergence items (macro §5) that do NOT bite yet:** insets, `state_id_variable` reactivity,
  shimmer-on-`empty://`, PTR scroll discovery, DivPatch semantics, status-bar timing — all deferred to
  their owning stages. Stage 0 renders `main` statically; none are exercised.
- **Min-deployment-target:** chose 15.0 (rationale in §C notes). If the reviewer wants to match some
  other repo convention, it's a one-line change in `project.yml` + regenerate.
- **Thin-glue check:** Stage 0 is genuinely small but NOT pure glue — the SPM/monorepo trap, the
  envelope→`.data` reshape, and the frozen seams warrant the full planner→implement→review ceremony.
```
