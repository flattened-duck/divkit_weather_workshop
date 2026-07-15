# iOS Stage 1 — App spine — IMPLEMENTATION CONTRACT (kopatel)

You extend the Stage-0 iOS client (branch `ios-client`) from "renders `main` only" to the full
3-screen spine: parse the envelope ONCE into all three screens, navigate between them in memory with a
back stack (NO refetch), wire real `weather-app://` routing, seed + reactively mutate SIX global
div-variables, and refetch on language/city change. Behavioral reference = the Android client
(`app/src/main/java/com/example/weatherdivkit/MainActivity.kt` + `WeatherDivActionHandler.kt`). Port
BEHAVIOR, not code.

All DivKit facts below were VERIFIED against `/Users/the-leo/divkit_source/divkit` @ tag `32.57.0`
(branch `R-32.57`). Citations are `path:line` relative to `client/ios/`. Do NOT re-derive; use the
calls verbatim. Do NOT edit the DivKit sources.

---

## A. MUST-NOT-GET-WRONG (read first, 8 bullets)

1. **Globals MUST survive card swaps.** The SIX globals live in `factory.variablesStorage` (one instance,
   shared by the one `DivView`). Navigation calls `setSource` with the DEFAULT
   `shouldResetPreviousCardData: false` — never pass `true`. Global vars are in `globalStorage` and are
   never touched by `reset(cardId:)`. Do NOT create a second `DivKitComponents`/`DivView` per screen.
2. **Seed the six globals BEFORE the first `setSource`** (in `viewDidLoad`, synchronously), or the first
   render logs "variable not declared" for all of them. Seeding all six is what makes the current batch
   of global-var render errors disappear (gate §F).
3. **These six are HOST-PROVIDED reactive div-variables, NOT Stored Values.** The backend expressions read
   them as bare `@{theme ...}` (verified: 157 refs to `theme`). Do NOT use `getStoredValue`/persistent
   storage for them. Persist their SEED VALUES yourself in `UserDefaults`; feed them into
   `DivVariablesStorage` at launch and on every change.
4. **theme / compact / header_state change = reactive, ZERO network.** Only mutate the global var; the live
   card re-evaluates automatically (verified render path §D.3). `set_lang` and `set_city` are the ONLY
   actions that refetch.
5. **Refetch keeps the CURRENT screen and keeps-current on failure.** After a `set_lang`/`set_city`
   refetch, rebuild the sources map and re-render `currentScreen` (not always `main`). On network failure,
   do NOT clear the screen, do NOT fall back to a bundle/cache (there is none yet) — just log and leave the
   current layout up.
6. **weather-app:// routing runs on the MAIN thread already** (UIKit tap → `DivActionHandler` →
   `urlHandler.handle` on main). Do NOT re-dispatch the routing to a background queue. Only the network
   fetch inside `setLang`/`setCity` is async; hop back to the main actor before touching `DivView`.
7. **Implement `handle(_:info:sender:)` (3-arg)** — that is the overload DivKit calls for weather-app://
   URLs (verified `DivActionHandler.swift:424`). Also implement `handle(_:sender:)` (2-arg) forwarding to
   the same router, as belt-and-suspenders. `div-action://` (set_stored_value/set_variable) is handled by
   DivKit upstream and never reaches you — do NOT route it.
8. **Keep the frozen seams' signatures intact.** Do NOT change `DocumentLoading`, `HostActions`, `Screen`,
   or the `DivComponentsFactory` property signatures. You MODIFY method BODIES (`DocumentLoader.load`,
   `WeatherUrlHandler.handle`, `WeatherHostViewController`) and ADD new files (`GlobalVariables.swift`,
   `Persistence.swift`, `WeatherNavigation` may be inline). `factory.variablesStorage` stays a
   `DivVariablesStorage()` created by the factory — you seed INTO it, you do not replace it.

---

## B. IMPLEMENTER SPEC (imperative — do exactly this)

### B.1 `Document/DocumentLoader.swift` — parse all three screens (DECISION: `.data(Data)` per screen)

Change only the body of `load(...)`. Keep the `DocumentLoading` protocol and `DocumentBundle` frozen.

Rules:
1. Build the URL and fetch EXACTLY as today (query `lang` always; append `lat`/`lon`/`name` only when
   non-nil AND non-empty; throw on non-200). Do not change this part.
2. Parse root once: `templates = root["templates"] as? [String:Any] ?? [:]`,
   `screens = root["screens"] as? [String:Any]` (throw `.malformed` if missing).
3. Build a `[Screen: DivViewSource]` by iterating `Screen.allCases`. For each screen whose card is present
   (`screens[screen.rawValue] as? [String:Any]`), build the DivKit source dict
   `["templates": templates, "card": card]`, serialize with `JSONSerialization.data(withJSONObject:)`, and
   make `DivViewSource(kind: .data(data), cardId: screen.cardId)`. Skip screens whose card is absent (do
   not throw for settings/about being absent — but they ARE present in this backend).
4. Require `.main` to be present (throw `.malformed("missing screens.main")` if not).
5. Return `DocumentBundle(sources: <map>, rawBody: data)`.

Rationale (skip): `.data` re-serializes `templates` per screen — negligible for 3 screens, zero
new-API risk, identical to the proven Stage-0 path. `.divData` parse-once is possible via
`DivData.resolve` but risks double-variable-registration (`DivKitComponents.swift:250` warns
"use DivData.resolve to avoid adding variables twice") — not worth it here. The protocol return type
(`DivViewSource`) is unchanged either way, so a later stage can switch if profiling ever demands.

### B.2 `Config/Persistence.swift` (NEW) — UserDefaults wrapper

Create a small type (`enum Persistence` with static computed vars, or a `struct` around
`UserDefaults.standard`) exposing typed get/set for these keys. Mirror Android `MainActivity` companion
keys/defaults (values below are the STRING keys to use; pick any stable keys, these match Android):

| property | UserDefaults key | type | default |
|---|---|---|---|
| lang | `pref_lang` | String | `"ru"` |
| themeMode | `pref_theme_mode` | String | `"system"` |
| compact | `pref_compact` | Bool | `false` |
| cityLat | `pref_lat` | String? | `nil` |
| cityLon | `pref_lon` | String? | `nil` |
| cityName | `pref_city_name` | String? | `nil` |

Rules: reading an absent String key returns the default (or `nil` for city). Writing persists
immediately. City is read/written as a `(lat: String?, lon: String?, name: String?)` triple.

### B.3 `DivKitHost/GlobalVariables.swift` (NEW) — seed + reactive mutation of the six globals

This is the single choke point for the SIX global div-variables. It holds a reference to the frozen
`factory.variablesStorage` (a `DivVariablesStorage`) and exposes seed/mutate helpers.

Shape:
```swift
import DivKit

final class GlobalVariables {
    static let theme = "theme"
    static let themeMode = "theme_mode"
    static let compact = "compact"
    static let headerState = "header_state"
    static let statusInset = "status_inset"
    static let navInset = "nav_inset"

    private let storage: DivVariablesStorage
    init(storage: DivVariablesStorage) { self.storage = storage }

    /// Initial declaration of ALL globals. Call ONCE before first render. No re-render.
    func seed(_ variables: DivVariables) { /* rule 1 */ }

    /// Mutate one or more globals at runtime. Triggers reactive re-render of affected cards.
    func set(_ variables: DivVariables) { /* rule 2 */ }
}
```
Rules:
1. `seed`: call `storage.append(variables: variables, triggerUpdate: false)`
   (`DivVariablesStorage.swift:157-162` → `globalStorage.put(..., notifyObservers: false)`).
2. `set`: call `storage.append(variables: variables, triggerUpdate: true)` (same method, `notifyObservers:
   true`) — this updates existing global values and fires `changeEvents.global`, which DivKit observes and
   turns into a re-render of exactly the cards that read those variables.

`append(variables:triggerUpdate:)` is marked deprecated in 32.57 (the non-deprecated path is
`outerStorage` injection, which the frozen seam does not use). It is fully functional and is the ONLY
public way to seed/mutate globals on an existing `DivVariablesStorage()`. Wrapping it here localizes the
one deprecation warning. Do NOT sprinkle the deprecated call elsewhere — always go through
`GlobalVariables`.

Build `DivVariables` (= `[DivVariableName: DivVariableValue]`, `DivVariableName.swift:5-7`) with
string-literal keys (`DivVariableName` is `ExpressibleByStringLiteral`) and these value cases
(`DivVariableValue.swift:5-13`):
- `theme`, `theme_mode`, `header_state` → `.string(...)`
- `compact` → `.bool(...)`
- `status_inset`, `nav_inset` → `.integer(Int)` (points, rounded)

### B.4 `DivKitHost/WeatherUrlHandler.swift` — real routing (replace the Stage-0 stub body)

Keep the type/`init`/`weak var actions` and the `handle(_:info:sender:)` signature. Replace the body with
routing. Add a private router and a 2-arg overload.

Rules:
1. Add `func handle(_ url: URL, sender: AnyObject?) { _ = route(url) }` and make
   `handle(_ url:URL, info:DivActionInfo, sender:AnyObject?) { _ = route(url) }`.
2. `private func route(_ url: URL) -> Bool`:
   - Parse with `URLComponents(url: url, resolvingAgainstBaseURL: false)`. Read `url.host` (the weather-app
     host) and query items into a `[String:String]` (last-wins).
   - If `url.scheme != "weather-app"` → for `http`/`https` call `UIApplication.shared.open(url)` and return
     `true`; otherwise return `false` (unhandled; DivKit's default no-op). (This makes the About-screen
     `https://github.com/divkit/divkit` link open, matching Android's `super.handleAction`.)
   - Switch on `url.host`. Validate EXACTLY as the table below; on any validation miss, return `false`
     (do NOT call the action) — mirrors Android returning `false`.
   - On a valid host, call the matching `actions?.<method>(...)` and return `true`.
3. Do NOT wrap the `actions?` calls in `DispatchQueue.main.async` — you are already on main. (If Swift
   concurrency isolation complains because `actions` is `@MainActor`, hop with `MainActor.assumeIsolated`
   or make `route` `@MainActor`; do not introduce async here.)

Host → validation → action (query-param names are load-bearing, verified against the live document):

| host | required params | validation | call |
|---|---|---|---|
| `navigate` | `screen` | `Screen(rawValue: screen.lowercased())` non-nil | `actions?.navigate(to: screen)` |
| `back` | — | — | `actions?.back()` |
| `set_lang` | `value` | non-nil, non-empty | `actions?.setLang(value)` |
| `set_theme` | `mode` | ∈ {`system`,`dark`,`light`} | `actions?.setTheme(mode)` |
| `set_compact` | `value` | `Bool(value)` non-nil (strict "true"/"false") | `actions?.setCompact(Bool(value)!)` |
| `set_city` | `lat`,`lon` | `lat`,`lon` non-nil; `name` defaults to `""` | `actions?.setCity(lat:lon:name:)` |
| `city_search` | `q` (default `""`) | — | `actions?.citySearch(query: q)` |
| (other host) | — | — | return `false` |

`import UIKit` for `UIApplication`. Keep `import DivKit`, `import Foundation`.

### B.5 `WeatherHostViewController.swift` — the spine (the bulk of the work)

Mark the class `@MainActor` (it is a `UIViewController`; this keeps `DivView`/UIKit access main-isolated
and lets `Task { }` inside methods inherit isolation). Add the state and behavior below. Keep the existing
`loader`, `divKitComponents`, `divView` `lazy` properties.

**New stored state (port from `MainActivity`):**
- `private var sources: [Screen: DivViewSource] = [:]` — current parsed screens (replaced atomically on
  refetch). Analog of Android `screens: Map<Screen, DivData>`.
- `private var currentScreen: Screen = .main` — survives refetch.
- `private var backStack: [Screen] = []`.
- `private var globals: GlobalVariables!` — built in `makeComponents`/`viewDidLoad` from
  `factory.variablesStorage`.
- `private var themeMode: String` and `private var effectiveTheme: String` — cached for status bar +
  system-dark re-resolution.

**`makeComponents()`** — keep setting `reporter` + `urlHandler`; additionally capture the factory's
`variablesStorage` so you can build `globals`. Because `divKitComponents`/`divView` are `lazy`, the
cleanest order is: build the `DivComponentsFactory` in `viewDidLoad` BEFORE first render, hold it (or hold
`globals`) as a property. Concretely: change `makeComponents()` to store `let factory = ...; self.globals
= GlobalVariables(storage: factory.variablesStorage); return factory.makeComponents()`. (`globals` is set
as a side effect the first time `divKitComponents` is accessed — access it explicitly early in
`viewDidLoad` so `globals` is ready before you seed.)

**`viewDidLoad()`** (port `MainActivity.onCreate`, MINUS PTR/two-phase cache which are later stages):
1. `super.viewDidLoad()`; `view.addSubview(divView)` (this forces `divKitComponents` init → `globals` set).
2. Read persistence: `themeMode = Persistence.themeMode`; `let compact = Persistence.compact`.
3. `effectiveTheme = resolveEffectiveTheme(themeMode)`.
4. `globals.seed([...])` with ALL SIX: `theme`→`.string(effectiveTheme)`, `theme_mode`→`.string(themeMode)`,
   `compact`→`.bool(compact)`, `header_state`→`.string("full")`, `status_inset`→`.integer(0)`,
   `nav_inset`→`.integer(0)`. (Insets get real values in `viewSafeAreaInsetsDidChange`.)
5. `setNeedsStatusBarAppearanceUpdate()` (initial status-bar contrast).
6. Kick the cold start: `Task { await coldStart() }`.

**`coldStart()` (async)** — Stage 1 is SIMPLE: network → render (no cache/skeleton; that is Stage 3):
- `let lang = Persistence.lang`; `let (lat, lon, name) = Persistence.city`.
- `await refetchAndRender(lang: lang, lat: lat, lon: lon, name: name, initial: true)`.
- On the `initial: true` path, after sources are set, `showScreen(.main)` (pushes main, renders). On the
  non-initial path (`set_lang`/`set_city`), re-render `currentScreen` instead (see `refetchAndRender`).

**`refetchAndRender(lang:lat:lon:name:initial:)` (async)** — single network+render path used by cold start,
`setLang`, `setCity`:
- `do { let bundle = try await loader.load(lang:lang, lat:lat, lon:lon, name:name) }`
  `catch { log; if initial { log "cold start failed" }; return }` — keep-current on failure (do nothing to
  the screen).
- `self.sources = bundle.sources` (on the main actor).
- If `initial` → `showScreen(.main)`; else → `renderScreen(currentScreen)`.

**Navigation (port `showScreen`/`renderScreen`/`goBack` verbatim, iOS divergences noted):**
- `func showScreen(_ screen: Screen)`:
  - `let top = backStack.last`; `if let top, top != screen { backStack.append(screen) } else if
    backStack.isEmpty { backStack.append(screen) }`; then `renderScreen(screen)`.
- `func renderScreen(_ screen: Screen)`:
  - `guard let source = sources[screen] else { log "No source for \(screen)"; return }`.
  - `currentScreen = screen`.
  - `Task { await divView.setSource(source, debugParams: DebugParams(isDebugInfoEnabled: true)) }`
    (default `shouldResetPreviousCardData: false`). Do NOT rebuild `divView`. (Keep
    `isDebugInfoEnabled: true` for the verifier; the error badge must be gone for the six globals.)
- `func goBack()`:
  - `if backStack.count <= 1 { return }` — **iOS DIVERGENCE:** Android calls `finish()` to exit the
    Activity; iOS has no Activity to finish, so back at the root is a NO-OP (log it).
  - else `backStack.removeLast()`; `guard let previous = backStack.last else { return }`;
    `renderScreen(previous)`.

**`HostActions` conformance (replace the Stage-0 log stubs with real behavior):**
- `navigate(to:)` → `showScreen(screen)`.
- `back()` → `goBack()`.
- `setLang(_ lang:)` → `Persistence.lang = lang`; `let (lat,lon,name) = Persistence.city`;
  `Task { await refetchAndRender(lang: lang, lat: lat, lon: lon, name: name, initial: false) }`.
- `setCity(lat:lon:name:)` → `Persistence.city = (lat,lon,name)`; `let lang = Persistence.lang`;
  `Task { await refetchAndRender(lang: lang, lat: lat, lon: lon, name: name, initial: false) }`.
- `setTheme(_ mode:)` → `Persistence.themeMode = mode`; `themeMode = mode`;
  `effectiveTheme = resolveEffectiveTheme(mode)`;
  `globals.set([GlobalVariables.themeMode: .string(mode), GlobalVariables.theme: .string(effectiveTheme)])`;
  `setNeedsStatusBarAppearanceUpdate()`. NO refetch.
- `setCompact(_ value:)` → `Persistence.compact = value`;
  `globals.set([GlobalVariables.compact: .bool(value)])`;
  `if value { globals.set([GlobalVariables.headerState: .string("collapsed")]) }`. NO refetch.
  (Android forces `header_state="collapsed"` when compact turns on; do the same. Whether the header state
  visually switches is a Stage-2B concern — you just set the var.)
- `citySearch(query:)` → **KEEP the Stage-0 log-only stub** (`print`). Full DivPatch is Stage 3A.

**Theme resolution + system-dark tracking (port `resolveEffectiveTheme`/`isSystemDark`/`onConfigurationChanged`):**
- `func resolveEffectiveTheme(_ mode: String) -> String`:
  `mode == "system" ? (isSystemDark() ? "dark" : "light") : mode`.
- `func isSystemDark() -> Bool`: `traitCollection.userInterfaceStyle == .dark`.
- **System-dark change observation (iOS DIVERGENCE from Android `onConfigurationChanged`):** override
  `override func traitCollectionDidChange(_ previous: UITraitCollection?)`:
  - `super.traitCollectionDidChange(previous)`.
  - `guard traitCollection.userInterfaceStyle != previous?.userInterfaceStyle else { return }`.
  - `guard themeMode == "system" else { return }`.
  - `let eff = isSystemDark() ? "dark" : "light"`; `effectiveTheme = eff`;
    `globals.set([GlobalVariables.theme: .string(eff)])`; `setNeedsStatusBarAppearanceUpdate()`.
  - (`traitCollectionDidChange` is deprecated on iOS 17 but functional and the correct choice for the
    15.0 min target; `registerForTraitChanges` is iOS 17-only. The deprecation warning in the app target
    is acceptable.)

**Status bar (port `applyStatusBarTheme`):**
- `override var preferredStatusBarStyle: UIStatusBarStyle { effectiveTheme == "dark" ? .lightContent :
  .darkContent }` (light content on dark bg, dark content on light bg).
- Trigger updates via `setNeedsStatusBarAppearanceUpdate()` (already called on theme/trait change and at
  launch). The root VC honors `preferredStatusBarStyle` with the default Info.plist
  (`UIViewControllerBasedStatusBarAppearance` unset = YES) — do NOT add that key set to NO.

**Insets (port the `setOnApplyWindowInsetsListener` seed):**
- `override func viewSafeAreaInsetsDidChange()`:
  - `super.viewSafeAreaInsetsDidChange()`.
  - `let top = Int(view.safeAreaInsets.top.rounded())`; `let bottom = Int(view.safeAreaInsets.bottom.rounded())`.
  - `globals.set([GlobalVariables.statusInset: .integer(top), GlobalVariables.navInset: .integer(bottom)])`.
  - (iOS `safeAreaInsets` are already in points/density-independent — do NOT divide by a density like
    Android does. The `DivSafeAreaManager`-vs-manual decision and notch verification are Stage 2C; seeding
    these two vars now is enough to clear the inset render-errors.)
- Keep the existing `viewWillLayoutSubviews` (`divView.frame = view.bounds.inset(by: view.safeAreaInsets)`).

Threading note: every `Task { }` inside these methods runs on the main actor (class is `@MainActor`), so
`divView.setSource`, `sources = ...`, `globals.set(...)` are all main-isolated. The network `await` inside
`loader.load` suspends off-main and resumes on the main actor — correct.

---

## C. FILES TO CREATE / MODIFY

Create:
- `ios/WeatherDivKit/Config/Persistence.swift` (B.2)
- `ios/WeatherDivKit/DivKitHost/GlobalVariables.swift` (B.3)

Modify (bodies only; signatures/seams frozen):
- `ios/WeatherDivKit/Document/DocumentLoader.swift` — `load` builds all present screens (B.1)
- `ios/WeatherDivKit/DivKitHost/WeatherUrlHandler.swift` — real routing (B.4)
- `ios/WeatherDivKit/WeatherHostViewController.swift` — the spine (B.5)

Do NOT touch: `DocumentLoading.swift`, `HostActions.swift`, `Screen.swift`, `DivComponentsFactory.swift`,
`LoggingDivReporter.swift`, `AppConfig.swift`, `project.yml`, anything outside `ios/`. `WeatherNavigation`
back-stack logic lives inline in the VC (do not over-engineer a separate type unless you prefer to; if you
extract one, keep it in `DivKitHost/` and do not change the seams). Regenerate the project only if you add
files and the build cannot see them: `cd ios && DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
/opt/homebrew/bin/xcodegen generate` (xcodegen auto-includes new files under `WeatherDivKit/`).

---

## D. VERIFIED API REFERENCE (cite when unsure; do NOT re-derive)

### D.1 Global variable seed + mutate (the load-bearing unknown — RESOLVED)
| fact | value | source |
|---|---|---|
| `DivVariables` type | `typealias DivVariables = [DivVariableName: DivVariableValue]` | `DivKit/Variables/DivVariableName.swift:7` |
| `DivVariableName` | `Tagged<DivVariableNameTag,String>` — string-literal expressible | `.../DivVariableName.swift:4-5` |
| value cases | `.string .number .integer(Int) .bool .color .url .dict .array` | `.../DivVariableValue.swift:5-13` |
| **seed globals** | `variablesStorage.append(variables: DivVariables, triggerUpdate: false)` → `globalStorage.put(_, notifyObservers:false)` | `.../DivVariablesStorage.swift:157-162` |
| **mutate globals** | same `append(..., triggerUpdate: true)` → fires `globalStorage.changeEvents` | `.../DivVariablesStorage.swift:157-162`, `135-156` |
| both methods deprecated but functional | doc comment steers to `outerStorage`; no removal in 32.57 | `.../DivVariablesStorage.swift:148,156` |
| globals propagate to a re-render | `changeEvents` merges `globalStorage.changeEvents` as `.global(...)` | `.../DivVariablesStorage.swift:57-72` |
| DivKit observes + re-renders affected cards | `variablesStorage.changeEvents.addObserver { onVariablesChanged }` → `.global` → `variableTracker.getAffectedCards` → `updateCard(.variable(...))` | `DivKit/DivKitComponents.swift:202-204, 432-442` |

### D.2 `setSource` card swap + global persistence (RESOLVED)
| fact | value | source |
|---|---|---|
| async setSource | `func setSource(_:DivViewSource, debugParams:DebugParams = .init(), shouldResetPreviousCardData:Bool = false) async` | `DivKit/Views/DivView.swift:148-166` |
| default does NOT reset | `shouldResetPreviousCardData:false` → no `reset(cardId:)` call | `DivKit/Views/DivView.swift:153-154` |
| components/storage constant across setSource | `DivView` holds one `divKitComponents` (`private let`) for its lifetime | `DivKit/Views/DivView.swift:45, 86-97` |
| `reset(cardId:)` never touches globals | resets only `localStorages`/`propertiesStorages` for that card | `DivKit/DivKitComponents.swift:236-248`, `DivVariablesStorage.swift:180-189` |
| **conclusion** | re-calling `setSource(sources[other], shouldResetPreviousCardData:false)` swaps the card; the six globals in `globalStorage` PERSIST and stay reactive | — |

### D.3 URL handling chain (RESOLVED)
| fact | value | source |
|---|---|---|
| weather-app:// dispatch | non-typed action (`.none`) → `handleUrl` → not a `DivActionIntent` → `urlHandler.handle(url, info:, sender:)` (**3-arg**) | `DivKit/Actions/DivActionHandler.swift:297-303, 315-424` |
| `div-action://` intercepted upstream | `set_stored_value`/`set_variable`/`set_state`/scroll/etc. handled inside `handleUrl` BEFORE the urlHandler call — never reaches you | `DivKit/Actions/DivActionHandler.swift:325-416` |
| protocol requires | `handle(_:sender:)` (default no-op) + `handle(_:info:sender:)` (default forwards to 2-arg) | `DivKit/Actions/DivUrlHandler.swift:10-35` |
| `.url` payload path (secondary) | `DivView.perform` → `divKitComponents.urlHandler.handle(url, sender:)` (**2-arg**) — implement it too, forward to router | `DivKit/Views/DivView.swift:333-345` |
| `DivActionInfo` | public struct | `DivKit/Actions/DivActionInfo.swift:4` |
| no default URL opener | base `DivUrlHandlerDelegate { _ in }` is a no-op; to open http(s) links you must call `UIApplication.shared.open` | `DivKit/Actions/DivUrlHandler.swift:37-51` |

### D.4 Live-document facts (verified via `curl localhost:8080/document?lang=ru`)
- Root keys `[templates, screens]`; `screens = [main, settings, about]`.
- Card-local `variables`: `main`→`[popup_dismissed]`, `settings`→`[city_query]`, `about`→none. The SIX
  globals are NOT card-declared → no shadowing; seeding them globally is correct.
- The six globals are read via bare `@{...}` expressions (theme 157×, status_inset 5×, nav_inset 4×,
  theme_mode 3×, compact 3×). `header_state` is used as a `state_id_variable`.
- weather-app:// hosts currently emitted: `navigate`, `set_lang`, `set_theme` (param `mode=`),
  `set_compact` (param `value=`), `city_search`. (`back` and `set_city` are wired but only fire later —
  `set_city` comes from Stage-3A city-search patch results.) Param names confirmed: `navigate?screen=`,
  `set_theme?mode=`, `set_compact?value=`, `set_lang?value=`.
- External link present on About: `https://github.com/divkit/divkit` (why B.4 opens http(s) URLs).

---

## E. PORT MAP (Android method → iOS)

| Android (`MainActivity.kt` / `WeatherDivActionHandler.kt`) | iOS | divergence |
|---|---|---|
| `onCreate` seed vars via `DivVariableController.declare` | `viewDidLoad` → `globals.seed([...])` | deprecated `append` instead of a controller |
| `Variable.set(...)` runtime mutation | `globals.set([...])` | — |
| `onSetLang` refetch+swap | `setLang` → `refetchAndRender(initial:false)` | no cache fallback (Stage 3) |
| `onSetCity` refetch+swap | `setCity` → `refetchAndRender(initial:false)` | — |
| `onSetTheme` persist+set theme/theme_mode+status bar | `setTheme` | — |
| `onSetCompact` persist+set compact+header_state collapse | `setCompact` | — |
| `resolveEffectiveTheme` / `isSystemDark` | same | `traitCollection.userInterfaceStyle == .dark` |
| `onConfigurationChanged` | `traitCollectionDidChange(_:)` | iOS-17 deprecation OK (min 15) |
| `applyStatusBarTheme` (`isAppearanceLightStatusBars`) | `preferredStatusBarStyle` + `setNeedsStatusBarAppearanceUpdate()` | root VC honors it |
| window-insets listener → `status_inset`/`nav_inset` | `viewSafeAreaInsetsDidChange` | points, no density divide |
| `showScreen`/`renderScreen`/`goBack` + `backStack` | same, inline | `goBack` at root = NO-OP (no `finish()`) |
| `renderScreen` builds a fresh `Div2View` per screen | one `DivView`, `setSource(sources[screen])` | in-memory swap |
| `WeatherDivActionHandler.handleWeatherAction` host switch | `WeatherUrlHandler.route` | already on main; no `mainHandler.post` |
| `mainHandler.post { ... }` (Android off-main safety) | direct call | iOS taps are main |
| `city_search` → `onCitySearch` (DivPatch) | log-only stub | Stage 3A |
| two-phase cache cold start / PTR | network-only cold start | Stage 3B |

---

## F. GATE / HOW TO VERIFY (run these; all must hold)

Let `DD=DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` and `SIM='iPhone 17'`.

1. Backend up: `curl -s -o /dev/null -w '%{http_code}' 'http://localhost:8080/document?lang=ru'` → `200`.
   If not: `cd backend && ./gradlew run` (do NOT modify backend).
2. Regenerate (new files) + build:
   ```
   cd ios && $DD /opt/homebrew/bin/xcodegen generate
   cd /Users/the-leo/divkit-weather-workshop && $DD xcodebuild \
     -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
     -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
   ```
3. Boot + install + launch capturing console:
   ```
   $DD xcrun simctl boot 'iPhone 17' 2>/dev/null; open -a Simulator
   APP="$($DD xcodebuild -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
        -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' \
        -showBuildSettings 2>/dev/null | awk -F' = ' '/ BUILT_PRODUCTS_DIR /{print $2; exit}')/WeatherDivKit.app"
   $DD xcrun simctl install 'iPhone 17' "$APP"
   $DD xcrun simctl launch --console-pty 'iPhone 17' com.example.weatherdivkit
   ```
   Watch the `DIVKIT_RENDER_ERROR [...]` lines. Drive the UI with `mobile-mcp` tools (screenshot,
   `mobile_list_elements_on_screen`, tap by coordinates) or `simctl io ... screenshot`.

**GATE checks:**
- **Build** succeeds.
- **Cold start:** `main` renders from the live backend (real weather content, not blank).
- **Global-var errors GONE:** the console no longer emits "variable not declared/not found" errors for
  `theme`, `theme_mode`, `compact`, `header_state`, `status_inset`, `nav_inset`. Prove by grepping the
  captured console: those six names must NOT appear in any `DIVKIT_RENDER_ERROR` line. The ONLY remaining
  render errors permitted are `sun_phase` (no custom-block factory — Stage 2A) and `scroll_state` (no
  extension handler — Stage 2B), plus at most a few cosmetic ones. With `isDebugInfoEnabled:true` the
  on-screen red error-count badge must be dramatically reduced vs Stage 0 (only the sun_phase/scroll_state
  residue).
- **Navigation, no refetch:** from `main`, tap the settings nav button → `settings` renders; tap the about
  nav button → `about`; use the back button and/or fire `weather-app://back` → pops back through the
  stack to `main`. To prove ZERO network refetch during navigation, watch the backend log (no new
  `/document` requests) or note there is exactly ONE `/document` request at cold start. `goBack` at root
  is a no-op (app stays on `main`).
- **Theme toggle reactive, ZERO network:** in settings, tap theme = dark → colors flip live; = light →
  flip back; = system → follows the simulator's appearance. No `/document` request fires. Status-bar
  contrast updates (dark bg → light status text). Toggle `compact` → sizes/visibility react live, no
  network.
- **System theme tracking:** with theme = system, flip the simulator to Dark (Settings app, or
  `xcrun simctl ui 'iPhone 17' appearance dark`) → the card re-colors to dark and status bar updates,
  with NO refetch.
- **Language switch refetches + keeps screen:** navigate to a non-main screen, then fire
  `weather-app://set_lang?value=en` (drive from the settings language control) → exactly ONE new
  `/document?lang=en` request fires, the visible strings switch to English, and you STAY on the current
  screen (not bounced to main).
- **Screenshots to capture** for the reviewer/verifier: `main` light, `main` dark, `settings`, `about`.

Report: build result; the console section proving the six globals are error-free (paste any residual
`DIVKIT_RENDER_ERROR` lines and classify each as sun_phase/scroll_state/cosmetic); screenshot paths;
`/document` request count during nav vs lang-switch; `git status --porcelain` (only the C-listed files
changed).

---

## G. RISKS / OPEN QUESTIONS

1. **Deprecated `append(variables:triggerUpdate:)`.** It is the only public seed path on the frozen
   `DivVariablesStorage()`; verified functional in 32.57. If a future DivKit bump removes it, switch to
   the `outerStorage` approach (create a `DivVariableStorage`, `put`/`update` on it, inject via
   `DivVariablesStorage(outerStorage:)` — this would require making the factory's `variablesStorage`
   injectable, a seam change to coordinate with Stage 2/3). Not needed now.
2. **`header_state` state switch on iOS.** Setting `header_state="collapsed"` mutates the var; whether the
   `state_id_variable`-bound state actually re-renders to the collapsed state on iOS is unverified here
   (macro §5 / Stage 2B). Stage 1 only needs the var declared + settable; do not chase the visual collapse.
3. **`traitCollectionDidChange` deprecation.** Correct for min-target 15.0; if the reviewer insists on the
   iOS-17 `registerForTraitChanges`, gate it behind `if #available(iOS 17, *)` with the deprecated path as
   the else-branch. Cosmetic.
4. **External-link open.** `UIApplication.shared.open` for the About github link is the Android-parity
   choice; if the reviewer considers external browser launch out of Stage-1 scope, downgrade the
   non-weather-app branch to a no-op + log (the link simply won't open). Low stakes.
5. **`sun_phase` / `scroll_state` residual errors** are EXPECTED to remain (Stages 2A/2B). Do not attempt
   to silence them here.

---

## H. OUT OF SCOPE for Stage 1 (do NOT implement)

- `sun_phase` DivCustomBlock (2A), `scroll_state` extension + header collapse wiring + Shimmer (2B),
  day/night background images & image theming beyond the default holder, `DivSafeAreaManager` decision &
  notch verification (2C).
- City search + DivPatch — `citySearch` stays the log-only stub (3A).
- Offline cache, two-phase cold start, zero skeleton, pull-to-refresh, graceful cache fallback on refetch
  failure (3B). Stage 1 refetch is network-only, keep-current on failure.
- Any UI/unit tests (Stage 4).
- Do NOT create empty stub files for later-stage types.

---

## ORCHESTRATOR NOTES (appendix — implementer applies the defaults; skip on first read)

- **DocumentLoader `.data` vs `.divData`:** chose `.data` per screen (re-serialize templates 3×) — zero
  new-API risk, identical to the proven Stage-0 path, and the `DocumentLoading` return type is unchanged
  so a later switch to parse-once `.divData` (via `DivData.resolve`, NOT `parseDivDataWithTemplates`,
  to avoid the double-variable-registration warned at `DivKitComponents.swift:250`) needs no seam change.
- **Global seed via deprecated API:** verified this is the ONLY public seed path on the frozen
  `DivVariablesStorage()`; the deprecation is a nudge toward `outerStorage` for multi-scope setups, which
  we don't have. Localized to `GlobalVariables`. Flagged as risk G.1.
- **Reactive-mutation confidence: HIGH.** Fully traced in source: `append(triggerUpdate:true)` →
  `globalStorage.put` → `changeEvents.global` → `DivKitComponents.onVariablesChanged` →
  `variableTracker.getAffectedCards` → `updateCard(.variable(...))`. No live-device dependency for the
  mechanism, but the FINAL VERIFIER must still confirm on-device that theme flips with zero network.
- **Global persistence across `setSource`: HIGH confidence** — `reset(cardId:)` provably spares globals and
  we never pass `shouldResetPreviousCardData:true`. The verifier should still navigate away and back after
  a theme change to confirm the theme survives.
- **Thin-glue check:** Stage 1 is NOT thin glue — it introduces the reactive-variable contract, the
  navigation/refetch state machine, and the URL router, each with correctness traps (globals surviving
  swaps, keep-current-on-failure, current-screen-survives-refetch). Full planner→implement→review→verify
  ceremony is warranted.
