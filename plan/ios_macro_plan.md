# iOS client — staged MACRO plan (orchestration + parallelization)

This is the **execution plan** for building the iOS client. It sits on top of the technical
brief `plan/ios_plan.md` (anchors, API surface, offline rules) and the shared `plan/contract.md`
(name map, endpoints, DivPatch shape). Read those two first — this file does NOT restate them;
it decides **order, ownership, what runs in parallel, and the git-worktree protocol**.

Behavioral source of truth is the Android client (`app/src/main/java/com/example/weatherdivkit/`)
+ the memories `app-requirements` / `divkit-android-gotchas`. Port BEHAVIOR, not code.

---

## 0. Ground truth already verified (so implementers don't re-derive)

- **DivKit iOS 32.57.0**, source at `/Users/the-leo/divkit_source/divkit/client/ios` @ `R-32.57`.
- Host pattern from `Samples/UIKitIntegration/DivKitSample/DivHostViewController.swift`:
  `DivView(divKitComponents:)` added as subview, `divView.setSource(.init(kind:.data(data), cardId:))`,
  `divView.frame = view.bounds.inset(by: view.safeAreaInsets)` in `viewWillLayoutSubviews`.
- `DivKitComponents.init(...)` (verified) exposes exactly the hooks we need:
  `divCustomBlockFactory: DivCustomBlockFactory?`, `extensionHandlers: [DivExtensionHandler]`,
  `patchProvider: DivPatchProvider?`, `urlHandler: DivUrlHandler`, `variablesStorage: DivVariablesStorage`.
  It also derives `safeAreaManager` / `themeManager` from `variablesStorage`.
- Built-ins present: `DivKitExtensions/Shimmers` (shimmer, no custom code), `DivKitExtensions/SizeProvider`
  + `ExtensionHandlers/` (working `DivExtensionHandler` examples → model for `scroll_state`).
- Simulator networking: base URL = `http://localhost:8080` (NOT `10.0.2.2`); no emulator proxy,
  so the Android `Proxy.NO_PROXY` workaround is dropped. Base URL MUST stay configurable
  (mirrors `DocumentLoader.baseUrl` static — tests flip it).
- Reference-only Swift scaffold to crib SPM/host wiring from:
  `/Users/the-leo/div_summer_workshop_ref/ios/LightDivkitPlayground` (SwiftUI `DivHostingView` variant).

---

## 1. Target module layout (the interface skeleton Stage 0 freezes)

```
ios/WeatherDivKit/                      # new Xcode project (SPM-managed DivKit)
  WeatherDivKitApp / AppDelegate        # entry
  WeatherHostViewController.swift       # THE SPINE: owns DivView, DivKitComponents, nav, PTR, insets, status bar
  Document/
    DocumentLoader.swift                # network + envelope→[Screen:DivData] + cache + city-search patch fetch
    Screen.swift                        # enum main/settings/about
  DivKitHost/
    WeatherUrlHandler.swift             # DivUrlHandler → weather-app:// hosts → callback protocol
    GlobalVariables.swift               # declares theme/theme_mode/compact/header_state/status_inset/nav_inset
    DivComponentsFactory.swift          # single place that assembles DivKitComponents (registration seam)
  Extensions/
    SunPhaseCustomBlock.swift           # DivCustomBlockFactory (CoreGraphics arc, ellipse marker)
    ScrollStateExtensionHandler.swift   # DivExtensionHandler → header_state
  Resources/
    zero_ru.json                        # bundled zero skeleton (generated from /zero?lang=ru)
  WeatherDivKitUITests/                 # mirrors app/src/androidTest/** (div-id based)
```

**The load-bearing decoupling:** Stage 0 freezes the *interfaces* — the `HostActions`
callback protocol the url handler calls, the `DocumentLoading` API, and the
`DivComponentsFactory` registration seam (arrays of extension handlers / a custom-block factory /
a patch provider it wires in). Parallel tracks then implement concrete types against those stubs
and only ever *append* to the factory seam → near-zero merge conflict.

---

## 2. Dependency graph (what gates what)

```
S0 Foundation ─┬─> S1 Spine ─┬─> S2A sun_phase        ┐
 (project,      │             ├─> S2B scroll+shimmer   ├─ parallel (worktrees)
  SPM, stubs,   │             ├─> S2C images/theme/insets ┘
  1 screen)     │             │
               └(interfaces)  ├─> S3A city search (DivPatch)   ┐ parallel
                              ├─> S3B offline+cache+PTR         ┘ (worktrees)
                              │
                              └─> S4 UI tests (starts after S1, fills as features land)
                                        │
   everything ────────────────────────> S5 Parity pass (iOS vs Android, sequential)
```

- **S0, S1 are sequential and single-owner** — they are the integration heart; parallelizing them
  buys nothing but merge pain.
- **S2 A/B/C are mutually independent** (disjoint files; each only appends one line to the factory
  seam) → 3 parallel worktrees.
- **S3 A/B** both extend `DocumentLoader` + the spine. Parallelizable in worktrees **iff** the
  `DocumentLoading` interface from S0 is stable (it is). 3B (offline) is the riskier of the two.
- **S4** can begin as soon as S1 lands (div-id lookup harness first), then grows per feature.
- **S5** is the final sequential gate.

---

## 3. Stage detail, gates, and ownership

Each stage runs the mandated loop: **umnik(PLANNER) → kopatel(implement) → reviewer(APPROVE/CHANGES)
→ umnik(FINAL VERIFIER: tests + manual run + pentest)**. Orchestrator owns all commits. Parallel
tracks each get their OWN kopatel in their OWN worktree (§4).

### Stage 0 — Foundation (SEQUENTIAL · 1 track)
Xcode project under `ios/`, SPM dependency on `divkit/divkit` @ tag `32.57.0` (products `DivKit`,
`DivKitExtensions`; fall back to the local checkout if the tag isn't on the registry). Create the
§1 skeleton with **stub** implementations + the frozen interface contracts. Wire a minimal
`WeatherHostViewController` that fetches `GET /document?lang=ru` once and renders the `main` card.
Configurable base URL (default `http://localhost:8080`). ATS: localhost HTTP allowed in Simulator.
- **Gate:** `main` screen renders in the iOS Simulator against the live backend; no `DivErrorsStorage`
  errors. Capture the Android baseline screenshots (all themes/langs) now for later parity diff.

### Stage 1 — App spine (SEQUENTIAL · 1 track, builds on S0)
Full envelope parse (templates once → `[Screen: DivData]`), in-memory 3-screen navigation + back
stack **without refetch** (port `MainActivity.showScreen/renderScreen/goBack`); `weather-app://`
`WeatherUrlHandler` for ALL hosts (navigate/back/set_lang/set_theme/set_compact/set_city/city_search
— port `WeatherDivActionHandler`); global reactive vars via `DivVariablesStorage`
(theme/theme_mode/compact/header_state/status_inset/nav_inset). theme/compact react with **no
refetch**; set_lang/set_city trigger refetch + card swap; `set_theme` also refreshes status-bar style.
- **Gate:** navigate all 3 screens; toggle theme + compact reactively (no network); switch lang →
  refetch swaps card; back stack + `weather-app://back` behave like Android.

### Stage 2 — Native enrichers (PARALLEL · 3 worktrees, after S1)
- **2A `sun_phase` DivCustomBlock** — `DivCustomBlockFactory` drawing the sunrise→sunset arc with
  CoreGraphics from `custom_props {sunrise,sunset}` (compute "now" from device clock). **Ellipse
  marker gotcha** (`my = (cy - r/2) − (r/2)·sinθ`) — port `SunPhaseView.onDraw` exactly.
- **2B `scroll_state` + Shimmer** — `DivExtensionHandler` matching `extensions:[{id:"scroll_state"}]`
  on the vertical gallery; observe the UICollectionView `contentOffset`; write `header_state`
  "full"/"collapsed" with the Android hysteresis (collapse past ~24pt, expand only at `contentOffset.y<=0`,
  re-check on scroll-end). Register the built-in `Shimmers` handler. Verify shimmer keeps animating
  on an `empty://` image with no error.
- **2C Images + theme bg + status bar + insets** — wire the default remote image holder
  (`DivImageHolderFactory` / `CachedRemoteImageHolder`); confirm the theme-driven day/night bg URL,
  popup `scale=fit` + base64 preview render. Status-bar style follows effective theme. Insets:
  **decide** `DivSafeAreaManager` vs feeding `view.safeAreaInsets` into `status_inset`/`nav_inset`
  vars (verify on a notched sim); keep header/FAB/settings paddings correct.
- **Gate (each):** feature renders correctly on device; integration is one appended line in
  `DivComponentsFactory`. Merge sequentially after each APPROVE.

### Stage 3 — City search · Offline · PTR (PARALLEL · 2 worktrees, after S1)
- **3A City search (DivPatch)** — `city_search` action → `GET /city-search?q=&lang=` (URL-encode `q`
  from resolved `city_query`) → apply patch to the live card targeting container `city_search_results`.
  Anchors: `DivKit/Patch*` / `DivPatchProvider` + how a Sample applies a patch. Watch the
  "patch replaces" gotcha (backend already wraps rows in a same-id container).
- **3B Offline + PTR** — cache last good body to `doc_cache_<lang>.json`; two-phase cold start
  (cache/skeleton instantly → network swap on success); bundle `zero_ru.json` (generate from
  `/zero?lang=ru`); graceful lang/city/PTR = **network-only, keep-current on failure** (never fall
  through to bundle/cache mid-refresh — that's the layout-wipe bug; set_lang may try the lang cache).
  No connectivity auto-refetch. `UIRefreshControl` on the main scroll only; locate the gallery's
  underlying scroll view (div id `main_scroll` — verify how iOS exposes a div id, analog of the
  Android view-tag). Port `DocumentLoader` + `MainActivity.onPullToRefresh/onSetLang/onSetCity`.
- **Gate:** city search round-trips + patches; offline cold-start shows cache then skeleton;
  refresh failures keep the current layout; PTR spinner always ends.

### Stage 4 — UI tests (PARALLEL from S1 · own worktree)
Mirror the Android suite (`app/src/androidTest/**` + `plan/tests_contract.md` invariants): find-by-div-id,
structure/id/child-count assertions only (NEVER live weather values), real backend on `:8080`,
`@Before` health check, offline-fallback test, refetch-count test (language switch refetches; theme
toggle does not). **First task:** verify how iOS DivKit exposes a div `id` on its view
(`DivKit/IdToPath.swift` / `DivAccessibilityElementsStorage.swift` → likely `accessibilityIdentifier`)
and build the `withDivId` analog on that. Then port: `WeatherUiTest` (nav/search/theme/popup),
`WeatherRefetchTest` (URLProtocol stub or a local counting server for request counts),
`WeatherOfflineTest` (dead base URL → skeleton), a registration smoke test.
- **Gate:** XCUITest suite green in the Simulator with the backend up; fails loudly when backend down.

### Stage 5 — Parity pass (SEQUENTIAL · 1 track, final)
Run iOS Simulator + Android emulator side-by-side. Matrix: light/dark, compact on/off, ru/en,
offline cold-start (cache vs skeleton), city switch, PTR, popup install/dismiss persistence, header
collapse. Fix iOS-specific layout divergences (see §5). Logical parity, not pixel-perfect.
- **Gate:** every matrix cell matches the Android behavior/screenshot logically; no render errors.

---

## 4. Git-worktree protocol (for the parallel stages S2, S3, S4)

1. Orchestrator, before fanning out a parallel stage, ensures the S0/S1 interface seam is committed
   on `main` so every worktree branches from the same base.
2. Each parallel track gets an isolated worktree + branch:
   `git worktree add ../wt-ios-<track> -b ios/<track>` (e.g. `ios/sunphase`, `ios/scrollshimmer`,
   `ios/imagestheme`, `ios/citysearch`, `ios/offline`, `ios/uitests`). Spawn each `kopatel` with
   `run_in_background: true`, `isolation: worktree`, pointed at its branch.
3. **Conflict-avoidance rule:** a track may only (a) ADD new files under its `Extensions/`/`Document/`
   area and (b) append its registration to the `DivComponentsFactory` seam. No two tracks edit the
   same existing line. The factory seam is designed as append-only arrays for exactly this reason.
4. Merge order per stage: as each track passes reviewer APPROVE + umnik FINAL VERIFIER, orchestrator
   merges its branch into `main`, resolving the (tiny, expected) factory-seam conflict centrally, then
   re-runs the build once integrated. `git worktree remove` after merge.
5. Never let two background tracks touch `WeatherHostViewController.swift` (the spine) concurrently —
   spine changes are sequential, owned by S1/S5.

---

## 5. iOS-specific divergence watchlist (the user's "часть вёрстки ведёт себя не как на Android")

Verify each on-device; these are where iOS is most likely to differ from Android and where the
parity pass must look hardest:

- **Insets:** `DivSafeAreaManager` may make the `status_inset`/`nav_inset` vars redundant — pick ONE
  path and keep paddings identical to Android. Notched vs non-notched.
- **`state_id_variable` reactivity:** confirm the iOS `state` re-evaluates when `header_state` changes
  (Android needed the *variable*, not a `default_state_id` expression). If not, adjust the collapse wiring.
- **Shimmer on `empty://`:** must keep animating and emit NO error/badge (Android needed the sentinel).
- **PTR scroll-view discovery:** DivKit galleries are UICollectionView-backed; the `main_scroll`
  scroll view must be located to host `UIRefreshControl` and to gate `canChildScrollUp`-equivalent.
- **DivPatch application:** the iOS patch API differs from Android's `view.applyPatch`; confirm the
  same-id container replacement semantics hold for `city_search_results`.
- **Gallery clipping / heights:** re-check the Android gotchas (gallery-in-scroll collapse,
  `clipToPadding` analog) on iOS layout.
- **Status-bar style timing:** `preferredStatusBarStyle` + `setNeedsStatusBarAppearanceUpdate()` on
  theme change (analog of `isAppearanceLightStatusBars`).

---

## 6. Environment / how to run during each stage

- Backend: `cd backend && ./gradlew run` on `:8080` (do NOT modify `backend/`).
- iOS: build/run in the iOS Simulator (`xcodebuild`/`simctl`; drive with the `mobile-mcp` tools for
  screenshots + interaction). Base URL `http://localhost:8080`.
- Android baseline for parity: boot an AVD (memory `emulator-cold-boot`: `-no-snapshot -gpu
  swiftshader_indirect`) and install the existing `app/` for side-by-side comparison.
- Zero skeleton asset: `curl 'http://localhost:8080/zero?lang=ru' > ios/.../Resources/zero_ru.json`
  (mirror of `scripts/update-bundled-layout.sh`).

---

## 7. Open questions to resolve DURING implementation (verify in iOS source/Samples — do not guess)

Carried from `plan/ios_plan.md §Open questions`, still the live unknowns:
1. Exact `DivPatchProvider` wiring + how a Sample applies a patch to a live `DivView` in 32.57.
2. `DivSafeAreaManager` vs manual inset vars — which keeps paddings correct with the existing layout.
3. iOS `state` reactivity to `state_id_variable`.
4. How iOS exposes a div `id` on its view (for PTR scroll lookup AND the test harness).
5. Built-in `Shimmers` behavior on `empty://`.

Resolve each by reading the iOS source/Samples at first contact in the owning stage; record the
answer in the stage's contract so downstream tracks inherit it.
```
