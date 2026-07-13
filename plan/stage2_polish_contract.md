# Stage 2 — Polish Fixes CONTRACT (7 fixes)

Target: DivKit Weather Workshop, branch `main`. Post-redesign code.
DivKit/divan source of truth verified against `/Users/the-leo/divkit_source/divkit` @ `R-32.57`.

---

## MUST-NOT-GET-WRONG (read first)

0. Fix #1 is a THEME-driven day/night BACKGROUND-PHOTO swap (NOT a scrim, NOT real time-of-day): the main background `image` url becomes a `theme` expression selecting the `_day` vs `_night` photo. Light theme ⇒ `_day`; Dark theme ⇒ `_night` (darker). WeatherMainRenderer-only.
1. `EdgeInsets.evaluate(top=…, bottom=…, …)`, `FixedSize.evaluate(value=…)` and `Image.evaluate(imageUrl=…)` EXIST and accept `ExpressionProperty<…>`. Use them for reactive insets and the reactive background url — NO spacer-element fallback is needed. (Verified paths below.)
2. `.evaluate(...)` MERGES: it overrides only the fields you pass and keeps the rest. So `edgeInsets(start=20, end=20, bottom=8).evaluate(top = expression<Int>("@{24 + status_inset}"))` keeps start/end/bottom static and makes only `top` reactive. Never re-list a field in both.
3. Reference the four NEW/EXISTING global vars by NAME ONLY inside expressions (`status_inset`, `nav_inset`, `theme`, `compact`). Do NOT declare them as local `variables=` in any renderer `data(...)` — the client owns them globally (same rule as `header_state`; a local decl shadows the global and breaks it). The smoke test asserts `header_state` is NOT locally declared — keep that invariant for the inset vars too.
4. The header scrim currently lives on the `state` wrapper (applies to BOTH states). Move it: scrim goes ONLY on `compactHeader`; `fullHeader` stays fully transparent (fix #3+#4). Remove `background=` from `headerState`.
5. `main_scroll` gallery top padding drops from ~190dp to the COMPACT header height (76dp) + `status_inset`. In expanded/normal mode the transparent full header intentionally overlaps the top of the scroll content — that is the desired iOS large-title behavior, NOT a bug.
6. Inset variables are `Variable.IntegerVariable(name, 0L)` (default `0L`, `.set(Long)`), declared in `MainActivity`’s existing `DivVariableController`. Values are px→dp converted, `.roundToInt().toLong()`.
7. Fix #7 removes the per-keystroke `variable_triggers` trigger from settings. The `city_search` action itself STAYS (button + enter key). Update `ApplicationSmokeTest` accordingly (drop the `on_variable` assert, add an assertFalse guard).
8. Do not regress: header collapse-on-scroll (normal mode), detail cards, weather background, popup overlay, 2 FABs, offline asset fallback, language refetch.

---

## VERIFIED DivKit/divan API FACTS (cite when in doubt)

- `EdgeInsets.evaluate(bottom/end/left/right/start/top/unit: ExpressionProperty<Int>? = null): EdgeInsets`
  → `json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/EdgeInsets.kt:281`. Merges into existing Properties.
- `FixedSize.evaluate(value: ExpressionProperty<Int>? = null, …)`
  → `json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/FixedSize.kt:164` (fallback only; NOT used here).
- `Image.evaluate(imageUrl: ExpressionProperty<Url>? = null, …): Image`
  → `json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/Image.kt:1488` (imageUrl at `:1490`). DivImage `image_url` is `Property<Url>` (`Image.kt:104`) → `Expression<Uri>` on the client. So a theme-expression background url is supported. Use `expression<Url>("@{…}")`.
- `bgBase(condition: ConditionCode): String` — public top-level in package `workshop.weather`
  → `backend/src/main/kotlin/workshop/weather/WmoMapping.kt:29`. Returns the photo base WITHOUT day/night suffix: CLEAR→`sunny`, CLOUDY→`cloudy`, RAIN→`rain`, SNOW→`cloudy`, THUNDER→`storm`, FOG/else→`fog`. Import `workshop.weather.bgBase` into the renderer.
- `fun <T : Any> expression(expression: String): ExpressionProperty<T>`
  → `json-builder/kotlin/divan-dsl/src/main/kotlin/divkit/dsl/core/Property.kt:27`. Already imported in all 3 renderers as `divkit.dsl.core.expression`.
- `Variable.IntegerVariable(name: String, defaultValue: Long)` with `fun set(newValue: Long)` and `getValue(): Any` returning `Long`.
  → `client/android/div-data/src/main/java/com/yandex/div/data/Variable.kt:46`.
- `Variable.BooleanVariable.getValue()` returns `Boolean`.
- `DivVariableController.get(name): Variable?` and `.declare(vararg Variable)`
  → `client/.../DivVariableController.kt:69` / `:40`. Extension already reads via `variableController.get(...)`.
- Reactive color/inset expressions re-evaluate on variable writes (already proven by `HEADER_SCRIM_EXPR`, `CARD_BG_EXPR`, etc. in the current renderer). Integer global vars used in `@{76 + status_inset}` arithmetic evaluate fine.

---

## IMPLEMENTER SPEC

### File ownership matrix
| Fix | Files |
|---|---|
| 1 theme day/night background photo | `WeatherMainRenderer.kt` |
| 2 compact forces collapsed | `MainActivity.kt`, `ScrollStateExtensionHandler.kt` |
| 3 full header transparent | `WeatherMainRenderer.kt` |
| 4 gallery top pad = compact header | `WeatherMainRenderer.kt` |
| 5 inset vars | `MainActivity.kt` (declare+listener), `WeatherMainRenderer.kt` (header top / fab bottom / gallery top+bottom), `WeatherSettingsRenderer.kt` (root top/bottom), `WeatherAboutRenderer.kt` (root top/bottom) |
| 6 hourly cell bg | `WeatherMainRenderer.kt` |
| 7 debounce city search | `WeatherSettingsRenderer.kt`, `backend/src/test/kotlin/workshop/ApplicationSmokeTest.kt` |

No new user-facing strings.

---

### Fix 5 — global inset variables (do this FIRST; #1/#4 depend on the var names existing)

**`MainActivity.kt`**
1. Add fields:
   ```
   private lateinit var statusInsetVar: Variable.IntegerVariable
   private lateinit var navInsetVar: Variable.IntegerVariable
   ```
2. In `onCreate`, after the other vars are built and BEFORE `variableController.declare(...)`:
   ```
   statusInsetVar = Variable.IntegerVariable("status_inset", 0L)
   navInsetVar = Variable.IntegerVariable("nav_inset", 0L)
   ```
   Add both to the existing declare call:
   `variableController.declare(themeModeVar, themeVar, compactVar, headerStateVar, statusInsetVar, navInsetVar)`
3. Enable edge-to-edge so systemBars insets are non-zero: in `onCreate` right after `setContentView(binding.root)` add
   `WindowCompat.setDecorFitsSystemWindows(window, false)` (import `androidx.core.view.WindowCompat` — already imported).
4. Install the insets listener on `binding.root` in `onCreate` (after `setContentView`):
   - Use `ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets -> … ; insets }`.
   - Read `val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())`.
   - Convert px→dp: `val d = resources.displayMetrics.density`.
     `statusInsetVar.set((bars.top / d).roundToInt().toLong())`
     `navInsetVar.set((bars.bottom / d).roundToInt().toLong())`
   - Return `insets` (do NOT consume; let children still receive them).
   - After registering the listener call `ViewCompat.requestApplyInsets(binding.root)`.
   - Imports to add: `androidx.core.view.ViewCompat`, `androidx.core.view.WindowInsetsCompat`, `kotlin.math.roundToInt`.
   RULE: `IntegerVariable.set` already dedups identical values — no manual change-guard needed.

**Consumers** (expressions, applied via `EdgeInsets.evaluate`):
- `WeatherMainRenderer` `fullHeader.paddings`: build as `edgeInsets(start = 20, end = 20, bottom = 8).evaluate(top = expression<Int>("@{24 + status_inset}"))`.
- `WeatherMainRenderer` `compactHeader.paddings`: `edgeInsets(start = 20, end = 20, bottom = 8).evaluate(top = expression<Int>("@{12 + status_inset}"))`.
- `WeatherMainRenderer` `fabRow.paddings`: `edgeInsets().evaluate(bottom = expression<Int>("@{20 + nav_inset}"))`.
- `WeatherMainRenderer` `scrollBody.paddings`: `edgeInsets(start = 16, end = 16).evaluate(top = expression<Int>("@{$HEADER_COMPACT_DP + status_inset}"), bottom = expression<Int>("@{96 + nav_inset}"))` (see #4 for `HEADER_COMPACT_DP`; the string must render `@{76 + status_inset}`).
- `WeatherSettingsRenderer` `settings_scroll.paddings` AND `WeatherAboutRenderer` `about_scroll.paddings`: change the existing `edgeInsets(start = 16, top = 16, end = 16, bottom = 16)` to
  `edgeInsets(start = 16, end = 16).evaluate(top = expression<Int>("@{16 + status_inset}"), bottom = expression<Int>("@{16 + nav_inset}"))`.

---

### Fix 1 — theme-driven day/night background photo (`WeatherMainRenderer.kt` only)
The background image url becomes a `theme` expression choosing the `_day` vs `_night` photo of the SAME condition base. No real time-of-day dependency; `current.bgKey` is no longer read by the renderer (backend still emits it — leave backend untouched).

- Add imports: `divkit.dsl.Url` (the TYPE, for `expression<Url>`), `workshop.weather.bgBase`. (`divkit.dsl.url` function is no longer needed by `backgroundImage` but stays used elsewhere.)
- Replace the current `backgroundImage` builder. Compute the url expression from the condition base:
  ```
  val bgBaseName = bgBase(current.condition)
  val bgDayUrl = "$BG_IMAGE_BASE_URL${bgBaseName}_day.png"
  val bgNightUrl = "$BG_IMAGE_BASE_URL${bgBaseName}_night.png"
  val bgUrlExpr = "@{theme == 'dark' ? '$bgNightUrl' : '$bgDayUrl'}"
  val backgroundImage = image(
      width = matchParentSize(),
      height = matchParentSize(),
      scale = fill,
  ).evaluate(imageUrl = expression<Url>(bgUrlExpr))
  ```
  - Do NOT pass `imageUrl =` inside `image(...)`; set it only via `.evaluate(imageUrl = …)` so `image_url` is the expression string.
  - `BG_IMAGE_BASE_URL` companion constant is unchanged (`…/S3/background_`).
- Root `overlap` items list stays exactly `listOf(backgroundImage, scrollBody, headerState, fabRow, popupOverlay)` (NO extra scrim element).

RULE: light theme ⇒ `_day` photo; dark theme ⇒ `_night` photo. Coil (NO_PROXY) already loads raw GitHub urls; toggling `theme` re-evaluates the url and swaps the photo. No app-side change for #1.

Concrete example (mock provider, current condition = CLOUDY ⇒ base `cloudy`), the emitted `image_url`:
`@{theme == 'dark' ? 'https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_cloudy_night.png' : 'https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_cloudy_day.png'}`
— the day branch still contains substring `background_cloudy_day.png`, keeping the existing smoke test green.

---

### Fix 3 + Fix 4 — header scrim placement & gallery top padding (`WeatherMainRenderer.kt`)
1. On `headerState` (the `state(...)` builder): REMOVE the `background = listOf(solidBackground().evaluate(color = expression<Color>(HEADER_SCRIM_EXPR)))` line entirely. Keep everything else (id, defaultStateId, stateIdVariable, transitionChange, states, width, height, alignmentVertical).
2. `fullHeader` container: NO `background` (already none) — leave transparent.
3. `compactHeader` container: ADD `background = listOf(solidBackground().evaluate(color = expression<Color>(HEADER_SCRIM_EXPR)))`. Keep `HEADER_SCRIM_EXPR` constant unchanged (`@{theme == 'dark' ? '#99000000' : '#99FFFFFF'}`). No border.
4. Replace companion constant `HEADER_EXPANDED_DP = 190` with `const val HEADER_COMPACT_DP = 76` (update its doc comment to say it reserves the COMPACT header height). Update the only usage (scrollBody top padding) to the expression form in Fix 5. There must be NO remaining reference to `HEADER_EXPANDED_DP`.

RULE: expanded (full) header transparent + overlaps content top (intended); compact header translucent + content starts below it.

---

### Fix 2 — Compact mode forces the header permanently collapsed

**`ScrollStateExtensionHandler.kt`** — inside `updateCollapsed()`, before computing `collapsed`, read the global `compact`:
```
val forced = (variableController.get("compact") as? Variable.BooleanVariable)?.getValue() as? Boolean ?: false
val collapsed = forced || offset > thresholdPx
```
- Add `const val COMPACT_VAR = "compact"` to the companion and use it instead of the literal (style parity with `HEADER_STATE_VAR`).
- Everything else in the method (the `lastCollapsed` dedup + write of "collapsed"/"full") stays. When `forced` is true, `collapsed` is always true ⇒ only "collapsed" is ever written.

**`MainActivity.kt`** — in `onSetCompact(value)`, after `compactVar.set(value)`:
```
if (value) headerStateVar.set("collapsed")
```
Do NOT force "full" when value is false — the extension recomputes from scroll offset on the next main bind (navigation re-renders a fresh Div2View, which re-runs `bindView`→`updateCollapsed`).

RULE (why it works): the toggle lives on the Settings screen; returning to Main calls `renderScreen(MAIN)` → new `Div2View` → extension `bindView` → `updateCollapsed()` reads current `compact` and sets state. Setting `header_state="collapsed"` immediately in `onSetCompact(true)` guarantees the state is correct even before the scroll listener first fires.

---

### Fix 6 — hourly cell surface matches other cards (`WeatherMainRenderer.kt`, `hourCell`)
- Change `background = listOf(solidBackground(color("#22FFFFFF")))` → `background = listOf(solidBackground().evaluate(color = expression<Color>(CARD_BG_EXPR)))`.
- Change `border = border(cornerRadius = 14)` → `border = border(cornerRadius = 16)`.
- `CARD_BG_EXPR` is the existing companion constant (`@{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}`) — do not redefine.

---

### Fix 7 — remove per-keystroke search trigger (`WeatherSettingsRenderer.kt` + smoke test)

**`WeatherSettingsRenderer.kt`**
- In the `data(...)` call, DELETE the entire `variableTriggers = listOf( trigger(...).evaluate(...) )` argument.
- Keep `searchAction` (still referenced by the input’s `enterKeyActions` and the Search button’s `action`).
- Remove now-unused imports: `divkit.dsl.on_variable`, `divkit.dsl.trigger`. Keep `divkit.dsl.core.expression` (still used by color expressions).
- Everything else in settings stays byte-identical.

**`ApplicationSmokeTest.kt`**, test `settings screen wires the city-search input`:
- KEEP asserts: `body.contains("city_query")`, `body.contains("weather-app://city_search?q=@{city_query}")` (still present via enter/button action), `body.contains("city_search_results")`.
- DELETE assert: `assertTrue(body.contains("\"on_variable\""), …)`.
- ADD guard: `assertFalse(body.contains("\"on_variable\""), "per-keystroke on_variable trigger must be removed (search fires on button/enter only)")`.

---

## GOLDEN / LITERAL VALUES (pin verbatim)

| Constant / expr | Value |
|---|---|
| background url expr (main, per condition base) | `@{theme == 'dark' ? '<BASE>_night.png' : '<BASE>_day.png'}` full urls; mock base = `cloudy` |
| `HEADER_SCRIM_EXPR` (unchanged, now on compact only) | `@{theme == 'dark' ? '#99000000' : '#99FFFFFF'}` |
| `HEADER_COMPACT_DP` | `76` |
| fullHeader top pad expr | `@{24 + status_inset}` |
| compactHeader top pad expr | `@{12 + status_inset}` |
| scrollBody top pad expr | `@{76 + status_inset}` |
| scrollBody bottom pad expr | `@{96 + nav_inset}` |
| fabRow bottom pad expr | `@{20 + nav_inset}` |
| settings/about scroll top pad expr | `@{16 + status_inset}` |
| settings/about scroll bottom pad expr | `@{16 + nav_inset}` |
| inset var names | `status_inset`, `nav_inset` (IntegerVariable, default `0L`) |
| compact var name read in extension | `compact` |
| CARD_BG_EXPR (reused for hourCell) | `@{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}` |

---

## TEST PLAN

### Build / unit
- `cd backend && ./gradlew test` — the two edited smoke assertions must pass; all others unchanged & green.
- `./gradlew :app:assembleDebug` (or the repo’s build) — Kotlin must compile with no unresolved refs (esp. `EdgeInsets.evaluate`, `ViewCompat`, `WindowInsetsCompat`, `Variable.IntegerVariable`).

### Backend JSON assertions (curl `http://localhost:8080/document?lang=ru`)
- Background `image_url` is the theme expression: contains BOTH `background_cloudy_day.png` AND `background_cloudy_night.png`, inside a single `@{theme == 'dark' ? …}` string. No bare `background_cloudy_day.png` as a non-expression url.
- Contains `@{24 + status_inset}`, `@{12 + status_inset}`, `@{76 + status_inset}`, `@{96 + nav_inset}`, `@{20 + nav_inset}` (main insets/padding).
- Contains `@{16 + status_inset}` and `@{16 + nav_inset}` (settings + about).
- hourly cell now carries `@{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}` and `"corner_radius":16` (no more `#22FFFFFF`).
- Does NOT contain `"on_variable"`. Still contains `weather-app://city_search?q=@{city_query}`.
- The `main_scroll` gallery no longer has a plain `"top":190`.
- `state_id_variable":"header_state"` still present; `"name":"header_state"` still absent (unregressed).

### On-device (emulator-5554, backend on :8080)
1. Cold start Main. Status bar area: header text sits BELOW the status bar (not under it); FAB row sits ABOVE the nav bar. Rotate / toggle gesture-vs-3-button nav to confirm insets update live.
2. Theme: Settings → Dark. Return to Main → background swaps to the NIGHT photo (`_night`, darker). Switch to Light → swaps to the DAY photo (`_day`, brighter). System → follows OS effective theme. Confirm both photos actually load (Coil), no broken image.
3. Compact ON (Settings → Compact) → return to Main → header is the small compact bar with translucent scrim; scrolling never expands it to the big header. Compact OFF → return to Main → header starts big/transparent and collapses to translucent compact bar as you scroll (original behavior).
4. Full (expanded) header has NO scrim (transparent over photo); compact header HAS the translucent scrim; content starts just under the compact header and slides under it (never past it) on scroll.
5. Hourly "next hours" cells now visually match the weekly/detail cards (same surface tint + 16 corner radius) in both themes.
6. City search: type a multi-letter query quickly → NO flicker/results churn while typing; tap Найти/Search (or keyboard enter) → results populate `city_search_results` once, deterministically; tapping a result navigates.
7. Regression sweep: popup shows/dismisses, both FABs navigate, language switch refetches, offline (kill backend, relaunch) still renders.

---

## ORCHESTRATOR NOTES (implementer may skip)

- **Fork taken — insets via `EdgeInsets.evaluate`, not spacer elements.** The risky item resolved cleanly: `EdgeInsets.evaluate(top=…)` accepts `ExpressionProperty<Int>` (`EdgeInsets.kt:281`), so no spacer-container fallback is required. If a future divan bump removes this overload, the fallback is a `container(height = fixedSize().evaluate(value = expression<Int>("@{status_inset}")))` placed as the first item above the header / a matching one below the FABs — but that is NOT this stage.
- **Edge-to-edge assumption.** Fix #5 assumes the app draws under the system bars. `WindowCompat.setDecorFitsSystemWindows(window, false)` is added defensively; on targetSdk 35/36 (API 35+) edge-to-edge is already enforced, so this is idempotent. If the app was NOT edge-to-edge before, this call is what makes the weather photo full-bleed AND makes insets non-zero — desired.
- **HEADER_COMPACT_DP = 76 is an estimate** (compact header = 12 top pad + ~56 two-line content + 8 bottom pad). Tune on-device in step 4: content should start flush under the compact bar. Acceptable range ~72–88.
- **Intended overlap in normal mode.** With top padding at the compact height, the big transparent header overlaps the first ~114dp of scroll content when expanded. This matches the iOS large-title pattern and is explicitly desired; do not "fix" it by restoring 190dp.
- **`onSetCompact(false)` deliberately does not write `header_state`.** Forcing "full" would fight the extension; letting the next bind recompute is correct because navigation always rebuilds the Main `Div2View`.
- **Stale androidTest offline assets** (`app/src/androidTest/assets/document_*.json`) are the OLD design and are NOT touched by any fix; they don’t exercise city-search-per-keystroke, so no Espresso change is needed for #7. Leave them alone (boundary).

## BOUNDARIES / OUT OF SCOPE
- No changes to backend routes, DocumentLoader, proto, CityRegistry, custom `sun_phase` view, or the popup/stored-values logic.
- No new strings, no localization file edits.
- No changes to `app/src/main/assets` offline fallback or `app/src/androidTest` assets/tests.
- Do not commit. Only the 6 files in the ownership matrix change.
