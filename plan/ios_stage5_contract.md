# iOS Stage 5 — Parity pass (iOS vs Android) + debug-overlay-off — IMPLEMENTER CONTRACT

FINAL stage. One tiny production change + a rigorous live side-by-side parity capture, ending in a
PASS/divergence report with evidence. You (kopatel) implement strictly; you do NOT commit.

Branch: `ios-client`. iOS app: `ios/WeatherDivKit` (xcodegen, DivKit 32.57). Android app: `app/`
(already built, installed, and running on the live emulator — you make ZERO Android changes this stage).

---

## MUST-NOT-GET-WRONG (read first)

1. Only ONE production file changes: `ios/WeatherDivKit/Config/AppConfig.swift` (+ one added line in
   `App/AppDelegate.swift`). Nothing else in `ios/WeatherDivKit/**` changes UNLESS you confirm a real
   (non-cosmetic) divergence and fix it — and every such fix must be scoped + reported.
2. Zero changes to `backend/`, `app/` (Android), or `talk/**`. Backend is frozen.
3. Debug overlay DEFAULT flips to OFF (`false`). The red error-counter badge must be gone on a normal
   launch. Keep a force-ON escape hatch (`WDK_DEBUG_OVERLAY=1`). Do not break the 14 XCUITests.
4. The LIVE Android emulator is the authoritative parity reference — NOT `talk/screens/*.png`. The
   `main_*` PNGs are STALE (old 2-card design) and will mislead you. Capture live Android for every cell.
5. Parity = LOGICAL equivalence (same content blocks, same layout intent, same behavior), NOT
   pixel-perfect. Intended iOS differences (see the "OK divergences" list) are PASS, not failures.
6. Capture screenshots with the deterministic `simctl`/`adb` commands below (full-res, no device
   ambiguity). Use mobile-mcp only for DRIVING (tap/swipe/type/launch), not for capture.
7. Two iOS simulators are booted — you MUST target iPhone 17 by its UDID and shut the other down, or
   `booted`-based commands and mobile-mcp will hit the wrong device.
8. After the AppConfig change: rebuild+reinstall the iOS app, then re-run the full S4 XCUITest suite
   and confirm 14/14 green before starting parity capture.
9. **HARD CONSTRAINT — busy device.** `adb` serial `emulator-5554` (AVD `Medium_Phone_API_36.1`) is
   BUSY with another task — NEVER install to it, launch it, screenshot it, or run any `adb`/`gradle`
   command that touches it. Also do NOT target the physical device `111dc2d`. The ONLY Android device
   you may use is a SEPARATE fresh instance of AVD `Medium_Phone_API_37.1` (comes up as its own serial,
   e.g. `emulator-5556`). Always pass an explicit `adb -s <that-serial>`; NEVER use bare `adb` (it would
   hit multiple devices) and NEVER `gradle installDebug` (installs to ALL connected devices → hits 5554).

---

## PART 1 — The one production change: debug overlay OFF by default

`WeatherHostViewController.swift:145` renders `DebugParams(isDebugInfoEnabled: AppConfig.debugOverlayEnabled)`.
Currently `debugOverlayEnabled` defaults to `true`, so a normal launch shows a red error-counter badge
(verified: live iOS shows a red "2" badge — the two known backend header-transition warnings).

### Edit A — `ios/WeatherDivKit/Config/AppConfig.swift`
Replace the whole file with:
```swift
enum AppConfig {
    static var baseURL: String = "http://localhost:8080"
    /// Release-like default: error-counter overlay OFF. Force ON with launch env WDK_DEBUG_OVERLAY=1.
    static var debugOverlayEnabled: Bool = false
}
```

### Edit B — `ios/WeatherDivKit/App/AppDelegate.swift`, inside `applyTestEnvironmentIfPresent()`
KEEP the existing `WDK_UITEST` line as-is (now a defensive no-op — default is already `false`).
ADD, immediately after it, the force-ON escape hatch:
```swift
        if env["WDK_DEBUG_OVERLAY"] == "1" { AppConfig.debugOverlayEnabled = true }
```
Result block (for reference — do not restate other lines):
```swift
        if env["WDK_UITEST"] == "1" { AppConfig.debugOverlayEnabled = false }
        if env["WDK_DEBUG_OVERLAY"] == "1" { AppConfig.debugOverlayEnabled = true }
```

### Regression check (mandatory)
- No XCUITest asserts the badge is on. Only `WeatherHostViewController` reads `debugOverlayEnabled`;
  only `BaseUITest.swift` sets `WDK_UITEST=1` (→ false, matches new default). The change is safe.
- Still PROVE it: after rebuilding, run the full suite (Part 2) and confirm 14/14 pass.

---

## PART 2 — Build / install / run methodology (VERIFIED)

### Pinned environment facts (all verified this session)
```
BACKEND      : UP on http://localhost:8080  (GET /document?lang=ru -> 200). Default city = Moscow (Москва).
               If down: (cd backend && ./gradlew run)  # do NOT modify backend
iOS SIM      : iPhone 17, UDID 981B1D02-5102-43C8-B827-A20579A528AF, iOS 26.5, app installed.
               (Also iPhone 16e C682125C-D611-4F08-908E-C2EF8442840D is booted — SHUT IT DOWN.)
               iOS reaches backend at http://localhost:8080. Bundle id: com.example.weatherdivkit
ANDROID EMU  : USE a SEPARATE instance of AVD "Medium_Phone_API_37.1" (phone form factor, closest to
               iPhone 17). It boots as its OWN adb serial (was "emulator-5556" this session — re-derive
               it, it may differ on reboot). Verified this session: boots clean, app installs to that
               serial only, renders the current live layout, reaches backend at http://10.0.2.2:8080.
               NEVER touch serial "emulator-5554" (busy AVD Medium_Phone_API_36.1) or physical "111dc2d".
               Launch activity: com.example.weatherdivkit/.MainActivity
               Debug APK (already built): app/build/outputs/apk/debug/divkit-weather-app-debug.apk
XCODE PROJ   : ios/WeatherDivKit.xcodeproj, scheme "WeatherDivKit". Regenerate (only if project.yml
               changed — it won't this stage): (cd ios && xcodegen generate)
```

### Step 1 — disambiguate the iOS simulator (do this first)
```
xcrun simctl shutdown C682125C-D611-4F08-908E-C2EF8442840D   # shut down iPhone 16e
xcrun simctl bootstatus 981B1D02-5102-43C8-B827-A20579A528AF -b   # ensure iPhone 17 booted
```

### Step 2 — build + install the iOS app after Part 1's change
```
xcodebuild -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,id=981B1D02-5102-43C8-B827-A20579A528AF' \
  -derivedDataPath /tmp/dd_ios build
xcrun simctl install 981B1D02-5102-43C8-B827-A20579A528AF \
  /tmp/dd_ios/Build/Products/Debug-iphonesimulator/WeatherDivKit.app
```

### Step 3 — re-run the S4 XCUITest suite (backend must be UP), confirm 14/14
```
xcodebuild test -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,id=981B1D02-5102-43C8-B827-A20579A528AF' \
  -derivedDataPath /tmp/dd_ios 2>&1 | tail -40
```
GATE: all 14 tests pass. If any fail, STOP and report — do not proceed to parity.

### Step 4 — Android: boot the SAFE emulator + install the pre-built APK (NO gradle build)
This stage changes no Android code — use the already-built debug APK; do NOT `gradle installDebug`
(it hits every connected device, incl. the busy 5554).
NOTE: a `Medium_Phone_API_37.1` instance was left RUNNING from planning (was `emulator-5556`, APK already
installed). First run `adb devices` + `adb -s <serial> emu avd name`; if one already reports
`Medium_Phone_API_37.1`, reuse it (skip boot/install). Otherwise boot a fresh one:
```
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.1 \
  -no-snapshot -gpu swiftshader_indirect -no-audio >/tmp/emu37.log 2>&1 &
# find the NEW serial (the one whose AVD name is Medium_Phone_API_37.1; NEVER emulator-5554):
adb devices                      # note the new emulator-55XX that appeared
adb -s emulator-55XX wait-for-device
adb -s emulator-55XX emu avd name   # MUST print "Medium_Phone_API_37.1" — verify before proceeding
# wait for boot: getprop sys.boot_completed == 1
adb -s emulator-55XX install -r app/build/outputs/apk/debug/divkit-weather-app-debug.apk
```
Set `SAFE=emulator-55XX` (the verified 37.1 serial) and use `adb -s $SAFE` for EVERYTHING below.
Recovery: if `Medium_Phone_API_37.1` will not boot cleanly, do NOT fall back to 5554 — instead use the
PNG-baseline FALLBACK (see ORCHESTRATOR NOTES) and state the live-Android side was skipped.

### Launch + reach a clean fresh state (for popup/offline cells)
```
# iOS launch (normal):
xcrun simctl launch 981B1D02-5102-43C8-B827-A20579A528AF com.example.weatherdivkit
# iOS launch with env (SIMCTL_CHILD_ prefix forwards env into the app):
SIMCTL_CHILD_WDK_RESET_STATE=1 \
  xcrun simctl launch 981B1D02-5102-43C8-B827-A20579A528AF com.example.weatherdivkit   # fresh: popup reappears, cache cleared
SIMCTL_CHILD_WDK_BASE_URL=http://localhost:9 \
  xcrun simctl launch 981B1D02-5102-43C8-B827-A20579A528AF com.example.weatherdivkit   # dead backend (offline)
SIMCTL_CHILD_WDK_RESET_STATE=1 SIMCTL_CHILD_WDK_BASE_URL=http://localhost:9 \
  xcrun simctl launch 981B1D02-5102-43C8-B827-A20579A528AF com.example.weatherdivkit   # fresh + offline -> zero skeleton
# Android launch (SAFE = the verified Medium_Phone_API_37.1 serial; NEVER emulator-5554):
adb -s $SAFE shell am start -n com.example.weatherdivkit/.MainActivity
# Android force-stop / clear (fresh popup + cache) if needed:
adb -s $SAFE shell pm clear com.example.weatherdivkit   # then relaunch
```
(`WDK_RESET_STATE`, `WDK_BASE_URL` are read in `AppDelegate.applyTestEnvironmentIfPresent()` — verified.)

### Capture (deterministic, full-res — use these, not mobile-mcp, for evidence)
```
mkdir -p /tmp/parity_ios /tmp/parity_android
xcrun simctl io 981B1D02-5102-43C8-B827-A20579A528AF screenshot /tmp/parity_ios/<cell>.png   # iOS  (1206x2622)
adb -s $SAFE exec-out screencap -p > /tmp/parity_android/<cell>.png                           # Android (1080x2400)
```
DRIVE the UI (tap the gear/info FABs, toggle theme/lang/compact in Settings, type in city search,
swipe to scroll/collapse header, pull-to-refresh) with mobile-mcp tools targeting the chosen device.
Name each screenshot `<cell>_<platform>.png` so pairs sit next to each other.

---

## PART 3 — Parity matrix (capture BOTH platforms; assert LOGICAL equivalence)

Same backend, city = Moscow (default) unless the cell is city-search. For each cell: reach the state on
both platforms, capture both, and record MATCH / DIVERGENCE(kind). "Reference" is the live Android
render; the PNG name is only a secondary sanity check (and `main_*` PNGs are known-stale — ignore for main).

| # | Cell | How to reach it (both platforms) | Logical-equivalence criterion | PNG (secondary) |
|---|------|----------------------------------|-------------------------------|-----------------|
| 1a | main ru light  | Settings → lang RU, theme Light | Same blocks: city header, hourly gallery, 7-day forecast rows, sunset/sun_phase card, gear+info FABs. Light bg. | main_ru_light / main_light_ru (stale) |
| 1b | main ru dark   | Settings → lang RU, theme Dark  | As 1a, dark bg + night image | main_ru_dark (STALE) |
| 1c | main en light  | Settings → lang EN, theme Light | As 1a, English labels (Now/Today/day names) | main_en (stale) |
| 1d | main en dark   | Settings → lang EN, theme Dark  | As 1b, English labels | main_en_dark (stale) |
| 2  | main compact ON | Settings → compact toggle ON (ru dark) | Denser rows / reduced paddings vs 1b; same blocks present; NO refetch flicker | main_dark_compact, main_ru_dark_compact |
| 3a | settings ru light/dark | tap gear FAB | Theme selector (System/Dark/Light), city search field, compact toggle, language toggle, back/nav | settings_ru*, settings_light/dark |
| 3b | settings en light/dark | as 3a, lang EN | Same controls, English labels | settings_en*, settings_en_dark |
| 4  | about ru + en  | tap info (i) FAB | About content present; localized RU/EN; back works | about_en_dark |
| 5  | header full vs collapsed | full = at very top; collapsed = scroll main up ~1 screen | AT TOP: FULL header (large temperature). SCROLLED: collapsed 1-line header. Both must MATCH Android's two states. **(see SUSPECTED DIVERGENCE #1)** | main_full |
| 6  | sun_phase card | on main, scroll to "Закат"/sunset card | Arc drawn as ellipse; marker sits ON the arc at "now" position; sunrise/sunset times shown | (in main) |
| 7  | city search | Settings → city field → type "Лондон"/"London" → results appear → tap a result | Results list populates (DivPatch into `city_search_results`); tapping sets city → main re-renders for that city (header shows new city) | city_search |
| 8a | offline cold-start: CACHE | launch normally once (populates cache), then relaunch iOS with dead base URL (no reset) | Shows last cached content (real data, not skeleton), NOT a wipe/blank | — |
| 8b | offline cold-start: SKELETON | relaunch iOS fresh + dead base URL (`WDK_RESET_STATE=1` + `WDK_BASE_URL=http://localhost:9`) | Zero skeleton: dashes + shimmering `empty://` image blocks, NO error badge, shimmer keeps animating | — |
| 9  | pull-to-refresh (iOS main) | at top of main, swipe down to trigger PTR | Spinner appears and always ends; content refreshes/stays; layout not wiped on failure. (Android has client-side PTR analog) | — |
| 10 | popup install + persist | fresh state (`WDK_RESET_STATE=1` iOS / `pm clear` Android) → popup "Поставь виджет…" shows → tap Установить (and separately X) → relaunch | Popup shows on fresh launch; after Install OR close-X it stays gone across relaunch (persisted) | — |

Cell 8a/8b are the iOS-specific offline behavior; Android's analog is airplane-mode / backend-down cold
start — capture Android's equivalent where feasible, else note "iOS-specific, Android analog verified in
prior stages" and compare iOS 8a vs 8b sanity only.

---

## PART 4 — How to judge a divergence

### Intended iOS differences — these are PASS (do NOT "fix" them)
- Navigation via on-screen buttons/FABs only; no Android hardware back button (iOS `weather-app://back`
  is wired to buttons). No system back gesture requirement.
- Safe-area / insets handled via iOS mechanism (DivSafeAreaManager or inset vars) — top/bottom spacing
  may differ in pixels but must be visually balanced and not clip/overlap content.
- Status-bar theming is iOS-native (light/dark content follows theme) — cosmetic, not a divergence.
- Fonts/system rendering, corner-radius antialiasing, exact shadow/blur, scroll-bounce, PTR spinner
  style — all cosmetic. PASS.
- The two backend `header` transition_in/out render warnings (the old red "2") — backend-frozen
  techdebt; with the overlay OFF they are invisible. Not a divergence to fix.
- Minor gallery item width/height rounding, as long as the same items are present and legible.

### Must-fix divergences (fix in the iOS client this stage, or record as backend techdebt if backend-rooted)
- A screen/card that renders WRONG (missing block, collapsed-to-empty, clipped/overlapping, wrong state).
- A missing feature or a broken interaction (tap/scroll/search/PTR/theme/lang/compact does nothing or
  crashes; popup not persisting; city search not patching).
- Header showing the WRONG state (e.g. collapsed at the very top) — logical, not cosmetic.
- Offline cold-start wiping the layout instead of showing cache/skeleton (the "layout-wipe" bug).
- Any `DivErrorsStorage` render error that changes what the user sees.

Scope any iOS fix minimally to the responsible file (spine `WeatherHostViewController.swift`,
`GlobalVariables.swift`, an extension handler, or `DocumentLoader.swift`) and report the exact change.
If the divergence is backend-rooted (same broken envelope on both clients), do NOT touch the backend —
record it as backend techdebt in the report.

---

## PART 5 — Suspected divergences found during planning (investigate FIRST)

These were observed on a single probe capture this session; confirm before fixing.

1. **HEADER COLLAPSED AT TOP (high priority, likely MUST-FIX).** On a normal launch the iOS main header
   rendered SMALL ("Москва / 24° | Облачно") with a large empty band above it, while live Android at the
   same scroll position (top) showed the FULL header (large "24°" + ↑24° ↓17°). This is exactly the
   macro-plan §5 `state_id_variable`/`header_state` reactivity risk. Confirm: launch iOS at top → is the
   header FULL or collapsed? Scroll down → does it collapse? Scroll back to top → does it expand to full?
   If iOS is stuck collapsed (or never shows full at top), FIX in the iOS spine: verify the `header_state`
   global var initial value corresponds to the FULL state id, and that the DivKit `state` re-evaluates
   when `header_state` changes (macro §5 item "state_id_variable reactivity"). Reference target = live
   Android full/collapsed states (cell 5).

2. **POPUP already dismissed on iOS.** The probe showed the "install widget" popup on Android but NOT on
   iOS (iOS state had it persisted-dismissed). This is expected persisted state, NOT a bug — for cell 10
   use `WDK_RESET_STATE=1` (iOS) / `pm clear` (Android) to get a fair fresh-launch comparison.

---

## PART 6 — Deliverable + GATE

Produce a PARITY REPORT (as your final message to the orchestrator; do NOT write a report .md file) with:
- The AppConfig/AppDelegate change confirmed applied; confirmation the red badge is GONE on normal launch
  (attach/point to a before/after main screenshot); S4 suite result "14/14 passed".
- The matrix table (Part 3) with MATCH / DIVERGENCE per cell, and the `/tmp/parity_ios|android/<cell>.png`
  paths for each pair captured.
- For every DIVERGENCE: classified as intended-OK (justified) OR must-fix; for each must-fix, the exact
  iOS file+change you made (or "backend techdebt — not fixed, recorded").
- `git diff --stat` showing ONLY the intended files changed (AppConfig.swift + AppDelegate.swift, plus any
  scoped iOS fix files you justified). Nothing under `backend/`, `app/`, `talk/`.

GATE (all must hold to PASS):
- Debug badge OFF on normal launch; `WDK_DEBUG_OVERLAY=1` still forces it on.
- S4 suite green (14/14).
- Every matrix cell either logically MATCHES live Android or is an explicitly justified intended-iOS
  difference. No unresolved must-fix divergence, no render error that changes the UI.
- Side-by-side evidence captured for every cell.
- `git diff` boundary clean (only intended files).

If you cannot make a cell pass and it needs a product decision, STOP and report the specific cell +
options rather than guessing.

---

## ORCHESTRATOR NOTES (appendix — implementer applies the defaults above; skip on first read)

- **Stale baselines (important).** `talk/screens/main_*.png` are from the OLD app design (a 2-card
  "Погода / Сегодня 17°C / Завтра 20°C" layout), verified this session against the current live Android
  render (rich hourly + 7-day + sunset layout). They are NOT valid references for `main`. settings_*/
  about_* are likely closer but still treat live Android as authoritative. Recommend the orchestrator
  note "talk/screens PNGs are stale" as repo techdebt (regenerate or delete) — but that is OUT OF SCOPE
  for this stage (talk/ is off-limits here).
- **Busy-emulator constraint (orchestrator directive).** `emulator-5554` (`Medium_Phone_API_36.1`) is
  busy with another task and is OFF-LIMITS; the physical device `111dc2d` is also off-limits. The
  contract routes ALL Android work to a separate fresh `Medium_Phone_API_37.1` instance (verified this
  session: booted as `emulator-5556`, installed the pre-built debug APK to that serial only, rendered the
  current live layout via `10.0.2.2:8080` — never touching 5554). Serials can shift on reboot, so the
  contract makes the implementer re-derive the safe serial by AVD name and forbids bare `adb`/`gradle
  installDebug`.
- **PNG-baseline FALLBACK** (only if `Medium_Phone_API_37.1` won't boot cleanly): compare the live iOS
  run against the committed `talk/screens/*.png` Android baselines, and STATE clearly in the report that
  the live-Android side was SKIPPED to avoid the busy `emulator-5554`. Caveat: the `main_*` PNGs are
  stale (old 2-card design) — under fallback, the live Android probe captured this session
  (`/tmp/parity_android/_probe37.png`, full RU-dark main with popup) is a better single reference than
  the stale `main_*` PNGs.
- **Live simultaneous run confirmed feasible** on the SAFE device — backend up, iPhone 17 + 37.1
  emulator both render the app. Fallback commands (emulator cold-boot flags) are recovery-only.
- **Two booted iOS sims** is the other foot-gun; the contract shuts down iPhone 16e up front.
- **Risks:** (a) header state reactivity (Part 5 #1) is the most likely real fix and touches the spine —
  keep it minimal and re-run S4 after any spine edit. (b) Any spine edit risks regressing S4 — always
  re-run the suite after a fix. (c) Emulator snapshot corruption on a cold boot (mitigated by the
  `-no-snapshot -gpu swiftshader_indirect` flags). (d) "Logically same" judgment calls — when unsure,
  favor reporting the divergence with both screenshots and letting the orchestrator/user decide, over
  silently passing or over-fixing cosmetics.
- **Deferred techdebt NOT to touch unless trivial:** city_search_input VoiceOver label; header id;
  theme-test baseline (all Stage-4 techdebt). New features are out of scope.
- **This stage is mostly verification + one 2-line change**, but the parity capture is real work and the
  header investigation may require a genuine spine fix — the full planner→kopatel→reviewer→verifier loop
  is warranted (this is the closing gate).
