# iOS Stage 3A — City search (DivPatch) — IMPLEMENTATION CONTRACT

Branch: worktree off `ios-client` (has S0–S2). Simulator: **iPhone 17**. You implement; you never commit.
This track is METHOD-SCOPED and additive (a parallel Stage-3B track edits the same two files for
offline/PTR). Touch ONLY the two edit points named in section C. Do NOT rewrite
`coldStart` / `viewDidLoad` / `refetchAndRender` / `load(...)` / the `loader` property / the
`DocumentLoading` protocol.

---

## MUST-NOT-GET-WRONG (read first)

1. Apply the patch with `divView.applyPatch(patch, cardId: currentScreen.cardId)` — the cardId MUST
   equal the CURRENTLY-shown card's id, or DivKit silently no-ops (verified below). At fire time the
   settings screen is on top, so `currentScreen.cardId == DivCardID("settings")`. Capture it BEFORE the
   `await`.
2. The iOS `parseDivPatch(_:)` expects the wrapper shape `{"patch":{…},"templates":{…}}`, but the
   backend returns the BARE `{"changes":[…]}` at root (no `patch` key, no `templates`). You MUST reshape
   the body into `{"patch": <root>}` before calling `parseDivPatch`. Feeding the raw body to
   `parseDivPatch` throws (empty `changes`). This is the #1 trap.
3. Fetch OFF main, apply ON main. `loadCitySearch` is `async` on the (non‑MainActor) `DocumentLoader`
   so the network await runs off-main; the apply happens back in the `@MainActor` `citySearch` Task.
4. Percent-encode `q`. Build the URL with `URLComponents` + `URLQueryItem` (auto percent-encodes query
   values) — never string-concatenate `q`.
5. Keep-current on failure: `loadCitySearch` returns `nil` on any network/status/parse error; `citySearch`
   then logs and does nothing to the screen. Never blank or reset the card.
6. Use the RESOLVED `q` the handler already hands you (`params["q"]`), NOT the literal `@{city_query}`.
   DivKit resolves the expression before the URL reaches the handler; you receive the typed text.
7. Add NO new file. Put the loader method inside the existing `DocumentLoader.swift`. (No `xcodegen`
   regen, no `project.pbxproj` change.)

---

## A. What you are building

Wire the stub `WeatherHostViewController.citySearch(query:)` so that a
`weather-app://city_search?q=<text>` action:
1. fetches `GET {baseURL}/city-search?q=<text>&lang=<lang>` off-main,
2. parses the response body into a `DivKit.DivPatch`,
3. applies that patch to the LIVE `divView` on the settings card, which replaces the
   `city_search_results` container with a fresh same-id container full of result rows (or a single
   localized "not found" row).

Tapping a result row fires `weather-app://set_city?lat=&lon=&name=` which is ALREADY wired
(`setCity` → `refetchAndRender`, Stage 1) — you only confirm the row action reaches it; you change
nothing in `setCity`.

Android behavior being ported: `MainActivity.onCitySearch(query, view)` (off-main fetch, on-main
`view.applyPatch`) + `DocumentLoader.loadCitySearch(query, lang)` (fetch `/city-search`, parse body as
`DivPatch`).

---

## B. Verified DivKit 32.57 facts (source = /Users/the-leo/divkit_source/divkit/client/ios, R-32.57)

- **Apply API (the make-or-break):**
  `public func applyPatch(_ patch: DivPatch, cardId: DivCardID)` —
  `client/ios/DivKit/Views/DivView.swift:226`. Body: `blockProvider?.update(reasons: [.patch(cardId, patch)])`.
  This is the iOS analog of Android `Div2View.applyPatch(patch)` — apply directly to the live card. No
  `patchProvider` / `div-action://download` machinery is needed for this flow.
- **cardId must match the current card:** `DivBlockProvider.update(reasons:)` extracts a patch only via
  `reasons.compactMap { $0.patch(for: self.cardId) }` — `client/ios/DivKit/Views/DivBlockProvider.swift:132`.
  `self.cardId` is the blockProvider of the currently-shown source. A mismatched cardId → empty compactMap
  → no-op. → pass the current screen's cardId.
- **Effective replace happens via** `divData.applyPatchWithActions(patch, …)` —
  `DivBlockProvider.swift:133`, defined in `client/ios/DivKit/Extensions/DivData/DivDataPatchExtensions.swift`.
  Replace semantics: a `change{id, items}` replaces the div whose `id == change.id` with `items`
  (`applyMultipleItemsPatch`, same file). Because the backend wraps the rows in a NEW container that
  re-uses `id = "city_search_results"`, the container id survives every patch (verified backend
  `WeatherServant.kt:158-177`). You rely on this; you do not manufacture it.
- **Parse API:** `public func parseDivPatch(_ data: Data) throws -> DivPatch` —
  `client/ios/DivKit/Patches/DivPatchProvider.swift`. It reads `dataJson["templates"]` and
  `dataJson["patch"]` — i.e. it wants `{"patch": {"changes":[…]}, "templates": {…}}`. Public and
  callable from the app module.
- **DivPatch model:** `changes: [Change]` (≥1 required, validator `minItems:1`); `Change { id: String,
  items: [Div]? }` — `client/ios/DivKit/generated_sources/DivPatch.swift`. An empty/absent `changes`
  fails parse → this is why the raw body must be reshaped (fact 2 above).

- **Backend response shape (already shipped, do not touch):** `WeatherServant.citySearch` returns
  `mapper.writeValueAsString(dp.patch)` — `backend/.../servant/WeatherServant.kt:180`. On the wire:

  ```json
  {"changes":[{"id":"city_search_results","items":[
    {"type":"container","id":"city_search_results","orientation":"vertical", "items":[ <row Divs> ]}
  ]}]}
  ```
  Root-level `changes`; NO `patch` wrapper; NO `templates`. Rows are full inline Divs (`text`), each hit
  row carrying `action.url = "weather-app://set_city?lat=&lon=&name=<urlencoded>"`; empty query or zero
  hits → a single centered "not found" text row (`WeatherServant.kt:124-157`).

- **Input/action binding (already shipped):** `city_search_input` (DivInput) has
  `text_variable = "city_query"`; the card declares `variables: [stringVariable("city_query","")]`; both
  the button `city_search_button` and the input's enter-key fire
  `action.url = "weather-app://city_search?q=@{city_query}"`
  (`backend/.../renderer/WeatherSettingsRenderer.kt:70-144`). DivKit substitutes `@{city_query}` with the
  typed text before the URL reaches `WeatherUrlHandler`, which passes `params["q"]` into
  `citySearch(query:)` (`ios/.../DivKitHost/WeatherUrlHandler.swift:67-69`).

---

## C. Files & exact edits (TWO edit points, both additive)

### C1. `ios/WeatherDivKit/Document/DocumentLoader.swift` — ADD one method

Add this method to the existing `final class DocumentLoader` (do not modify `load(...)`, do not touch the
`DocumentLoading` protocol). It stays off the protocol on purpose — the host constructs a fresh
`DocumentLoader()` for it (Android-faithful: `onCitySearch` builds `DocumentLoader(this)`), which keeps
this track from touching the shared `loader` property line that Stage-3B may also edit.

Signature & contract (write the body; rules are load-bearing):

```
func loadCitySearch(query: String, lang: String) async -> DivPatch?
```

Rules:
- Build URL via `URLComponents(string: AppConfig.baseURL + "/city-search")`; set
  `queryItems = [URLQueryItem(name:"q", value: query), URLQueryItem(name:"lang", value: lang)]`; take
  `components.url`. (URLComponents percent-encodes the values — this satisfies MUST-NOT #4.)
- `URLSession.shared.data(from: url)`; require `HTTPURLResponse.statusCode == 200`, else return `nil`.
- `JSONSerialization.jsonObject(with: data) as? [String: Any]` → call it `root`; if that cast fails,
  return `nil`.
- Reshape for `parseDivPatch` (MUST-NOT #2): build `var wrapper: [String: Any] = ["patch": root]`; if
  `root["templates"]` exists, also set `wrapper["templates"] = root["templates"]`. Serialize with
  `JSONSerialization.data(withJSONObject: wrapper)` and `return try parseDivPatch(wrapperData)`.
- Wrap the whole fetch+parse in `do { … } catch { print(...); return nil }`. Any throw → `nil`.
- No `@MainActor` on this method / class — it must run off-main.

Import note: `parseDivPatch`, `DivPatch`, `URLQueryItem` are available via the existing
`import DivKit` / `import Foundation` at the top of the file. `AppConfig.baseURL` is already used by
`load(...)` in this same file.

### C2. `ios/WeatherDivKit/WeatherHostViewController.swift` — REPLACE the `citySearch` stub

Replace ONLY the body of the existing stub:

```swift
func citySearch(query: String) {
    // Stage 0 stub kept: full DivPatch wiring is Stage 3A.
    print("HostActions.citySearch(query: \(query))")
}
```

New contract (write the body; rules load-bearing). The type is `@MainActor`, so a `Task {}` here is
MainActor-isolated; the `await` on the non-MainActor loader hops off-main for the fetch, then resumes on
main for the apply:
- Read `let lang = Persistence.lang`.
- Capture the target card id BEFORE any await: `let cardId = currentScreen.cardId` (== settings at fire
  time; capturing pins "apply to the firing screen" and makes a late navigation a safe no-op).
- `Task { let patch = await DocumentLoader().loadCitySearch(query: query, lang: lang); … }`.
- If `patch == nil`: `print(...)` a one-line warning including `query`; return (keep-current).
- Else: `divView.applyPatch(patch, cardId: cardId)`.

Do not add `@MainActor` hops beyond the enclosing type; do not call `loader` (the shared property) — use a
fresh `DocumentLoader()`.

---

## D. Port notes vs Android (divergences to be aware of)

| Concern | Android | iOS (this track) |
|---|---|---|
| Apply target | `view.applyPatch(patch)` on the firing `Div2View` (single card) | `divView.applyPatch(patch, cardId: currentScreen.cardId)` — iOS needs the explicit cardId; single host DivView is reused across screens |
| Off-main | `Dispatchers.IO` launch, `withContext(Main)` apply | `async` loader on cooperative pool, apply in `@MainActor` Task continuation |
| Body → DivPatch | `DivPatch(env, json)` reads `changes` from body ROOT (+ optional `templates`) | `parseDivPatch(data)` reads `data["patch"]`/`data["templates"]` → **must reshape** `{"patch": root}` |
| Loader instance | fresh `DocumentLoader(this)` per call | fresh `DocumentLoader()` per call (same pattern) |
| baseURL | `http://10.0.2.2:8080` (+`Proxy.NO_PROXY`) | `AppConfig.baseURL` = `http://localhost:8080`; simulator reaches host localhost directly, no proxy dance |
| Failure | logs, keeps current | logs, keeps current (identical) |

---

## E. GATE — build + live round-trip (iPhone 17, bare tools)

Prereqs: backend running on `localhost:8080` (`cd backend && ./gradlew run` or the project's usual start;
confirm `curl -s "http://localhost:8080/ping"` → `pong`). Verify the endpoint directly first:

```
curl -s "http://localhost:8080/city-search?q=Mos&lang=en" | head -c 300   # expect {"changes":[{"id":"city_search_results",...
curl -s "http://localhost:8080/city-search?q=&lang=ru"    | head -c 300   # expect the "not found" single-row patch
```

Build & install (no new file → no xcodegen regen needed):

```
cd /Users/the-leo/divkit-weather-workshop/ios
xcodebuild -project WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -derivedDataPath build build | tail -20
xcrun simctl boot "iPhone 17" 2>/dev/null; open -a Simulator
xcrun simctl install "iPhone 17" \
  "$(find build/Build/Products -name 'WeatherDivKit.app' -maxdepth 3 | head -1)"
xcrun simctl launch "iPhone 17" com.example.weatherdivkit
```

Drive with mobile-mcp (bare tools; iPhone 17 the selected device):
1. `mobile_list_available_devices` → select the iPhone 17 simulator.
2. `mobile_take_screenshot` — main screen renders.
3. Navigate to Settings (tap the settings/gear entry; use `mobile_list_elements_on_screen` to find it).
4. `mobile_list_elements_on_screen` → find the city-search input; tap it, then `mobile_type_keys`
   `"London"` (or `"Лондон"` with lang=ru).
5. Tap `city_search_button` ("Найти"/"Search"). `mobile_take_screenshot`.
   - EXPECT: the `city_search_results` container now shows ≥1 tappable row (a real hit). This proves
     fetch + reshape + parse + applyPatch end-to-end.
6. Tap the first result row. `mobile_take_screenshot`.
   - EXPECT: navigation/refetch — the main screen now reflects the chosen city (proves the row's
     `set_city` reaches the already-wired `setCity` → `refetchAndRender`).
7. Empty-query case: back to Settings, clear the input (or leave empty), tap the button.
   - EXPECT: exactly one centered "not found" / "Ничего не найдено" row (proves the empty-results patch
     applies, not a crash/blank).
8. Confirm in logs (`xcrun simctl spawn "iPhone 17" log stream` or Xcode console) that `q` arrived
   NON-EMPTY (i.e. the `@{city_query}` binding resolved). If `q` is empty on a non-empty field, the
   input→variable binding is broken — STOP and report (see RISK R3).

PASS = steps 5, 6, 7 all hold and no crash.

---

## F. RISKS / edges / when to ASK

- **R1 (primary): the reshape.** If `parseDivPatch(rawBody)` were used unchanged it would throw on empty
  `changes`. The reshape `{"patch": root}` is the fix. If, against expectation, the live body already
  contains a top-level `patch` key (it should NOT — verified it's bare `changes`), do NOT double-wrap;
  re-check with the `curl` in E and report. FALLBACK if `parseDivPatch` still refuses: parse directly with
  the public template API — `DivTemplates(dictionary: root["templates"] as? [String:Any] ?? [:])
  .parseValue(type: DivPatchTemplate.self, from: root).unwrap()` (all public: `DivTemplates`
  `client/ios/DivKit/Templates/DivTemplates.swift:5,56`; `DivPatchTemplate`
  `generated_sources/DivPatchTemplate.swift:7`). Prefer the reshape+`parseDivPatch` path; use this only if
  blocked.
- **R2: cardId mismatch → silent no-op.** If the patch appears not to apply, log
  `currentScreen` and the passed cardId; confirm it equals `settings`. Do not "fix" by resetting the card.
- **R3: `q` empty at tap.** The input→`city_query`→`@{q}` chain is backend-declared and DivKit-native; if
  `q` arrives empty, the DivInput `text_variable` binding didn't update the card variable — that is NOT
  fixable in this track's two files; report to the orchestrator with the log evidence.
- **R4: special chars in `q`** (`&`, `=`, `+`) can corrupt the action URL at the DivKit expression-
  substitution layer (pre-existing, same class on Android). Out of scope; note if you hit it, don't patch.

---

## G. OUT OF SCOPE (do not implement here)

- Offline cache / two-phase cold start / zero skeleton / pull-to-refresh / graceful language switch —
  the parallel Stage-3B track. Keep your edits to the two method points in section C so the orchestrator's
  merge is trivial.
- Tests — Stage 4.
- `setCity` refetch — already wired (Stage 1); you only confirm the result-row action reaches it.
- Any change to `DocumentLoading` protocol, `load(...)`, `coldStart`, `viewDidLoad`, `refetchAndRender`,
  the shared `loader` property, or the backend.

---

## ORCHESTRATOR NOTES (implementer may skip)

- Fork on where to put `loadCitySearch`: (a) extend the `DocumentLoading` protocol additively, (b) concrete
  method + fresh `DocumentLoader()` per call. Chose (b): it touches ZERO shared lines (no protocol edit, no
  `loader`-property retype), minimizing the merge conflict against Stage-3B (which also edits
  `DocumentLoader.swift`/`WeatherHostViewController.swift`), and it mirrors Android's `onCitySearch`
  constructing a fresh `DocumentLoader(this)`. Cost: a throwaway stateless instance per search — negligible.
- Fork on apply mechanism: `patchProvider` + `div-action://download` vs direct `DivView.applyPatch`. Chose
  direct apply — it is the exact Android analog, needs no factory/seam change (`DivComponentsFactory
  .patchProvider` stays untouched/`nil`), and the `download`-flow would require re-modelling the card's
  action into a `download` action (backend change, out of scope). The `DivComponentsFactory.patchProvider`
  seam is irrelevant to this flow and stays unset.
- The whole track is ~2 small methods over already-verified DivKit APIs; if the orchestrator prefers, it is
  thin enough to implement directly and review rather than run the full kopatel ceremony — but the reshape
  trap (F/R1) is the one place a careless implementation silently fails, so a review pass is warranted.
</content>
</invoke>
