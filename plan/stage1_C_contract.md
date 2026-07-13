# Stage 1 · Worktree C — Client extensions + chrome — IMPLEMENTATION CONTRACT

Scope: `app/**` only. Do NOT touch `backend/**` or `plan/contract.md`.
Toolchain frozen: DivKit `32.57.0`, app Kotlin `2.2.10`, AGP `8.11.0`, minSdk 26, targetSdk/compileSdk 36. Only ADD Coil + recyclerview deps.

All DivKit APIs below were verified against local source `/Users/the-leo/divkit_source/divkit` @ branch `R-32.57`. Source paths are cited so you can re-confirm; do not re-derive signatures from memory.

---

## MUST-NOT-GET-WRONG (load-bearing invariants)
1. Native names are a hard contract with Worktrees A/B. Register EXACTLY: custom_type `sun_phase`; extension id `scroll_state`; Boolean global var `header_collapsed`; DivPatch target id `city_search_results`; query var `city_query`; actions `weather-app://set_city?lat=&lon=&name=` and `weather-app://city_search?q=`.
2. `header_collapsed` is declared ONCE, globally, in `MainActivity`'s existing `DivVariableController` as `Variable.BooleanVariable("header_collapsed", false)`. The extension writes it through that same controller. A must NOT declare it in JSON.
3. Coil swap: constructor is `CoilDivImageLoader(context)` — package `com.yandex.div.coil`. Remove `PicassoDivImageLoader`. (verified: `client/android/coil/src/main/java/com/yandex/div/coil/CoilDivImageLoader.kt`)
4. DivPatch from JSON = `DivPatch(env, jsonObject)` where jsonObject is the whole `{"changes":[…]}` body; apply with `div2View.applyPatch(patch): Boolean` ON THE MAIN THREAD. Network fetch OFF the main thread. (verified: `Div2View.kt:507`, `DivPatchApplyItemsTest.kt:61`)
5. `set_city` mirrors the existing `set_lang` refetch flow: persist lat/lon/name → refetch `/document?lang=&lat=&lon=&name=` → rebuild screens → re-render current screen. Re-URL-encode `name` when building the refetch URL.
6. Status bar follows `theme`: `WindowCompat.getInsetsController(window, decorView).isAppearanceLightStatusBars = (effectiveTheme == "light")`, applied at initial render, in `onSetTheme`, and in `onConfigurationChanged`.
7. Custom-view registration uses `DivConfiguration.Builder.divCustomContainerViewAdapter(...)`; extension uses `.extension(...)`. (verified: `DivConfiguration.java:541,594`)
8. Do not change any existing backend-driven asset/layout JSON. The 9 Espresso tests must still pass unchanged.

---

## IMPLEMENTER SPEC

### FILE 1 — `app/gradle/libs.versions.toml`  (edit)
Add under `[versions]`:
```
coil = "3.1.0"
recyclerview = "1.3.2"
```
Add under `[libraries]`:
```
divkit-coil = { group = "com.yandex.div", name = "coil", version.ref = "divkit" }
androidx-recyclerview = { module = "androidx.recyclerview:recyclerview", version.ref = "recyclerview" }
```
Leave the existing `divkit-picasso` / `divkit-utils` catalog entries in place (unused is harmless) OR delete them; they must NOT be referenced from `build.gradle.kts` anymore. The `coil` and `recyclerview` version keys exist only for the two new library entries above. (The `com.yandex.div:coil` POM transitively brings coil3 core/svg at compile scope and gif/network-okhttp/network-cache-control at runtime scope — no per-artifact coil deps needed. Verified against `client/android/coil/build.gradle.kts` + `client/android/gradle/libs.versions.toml`.)

### FILE 2 — `app/build.gradle.kts`  (edit `dependencies {}`)
- REMOVE: `implementation(libs.divkit.picasso)` and `implementation(libs.divkit.utils)` (grep confirms no `com.yandex.div.utils` / `com.yandex.div.picasso` usage remains after FILE 3).
- ADD: `implementation(libs.divkit.coil)` and `implementation(libs.androidx.recyclerview)`.
- Everything else unchanged.

### FILE 3 — `app/src/main/java/com/example/weatherdivkit/MainActivity.kt`  (edit)
Imports: drop `com.yandex.div.picasso.PicassoDivImageLoader`; add `com.yandex.div.coil.CoilDivImageLoader`, `androidx.core.view.WindowCompat`, and the two new classes from FILE 6/7.

Add field: `private lateinit var headerCollapsedVar: Variable.BooleanVariable`.

In `onCreate`, in the variable-setup block (next to theme/compact):
```
headerCollapsedVar = Variable.BooleanVariable("header_collapsed", false)
variableController.declare(themeModeVar, themeVar, compactVar, headerCollapsedVar)
```

Replace the `DivConfiguration.Builder(...)` construction with:
```
divConfiguration = DivConfiguration.Builder(CoilDivImageLoader(this))
    .actionHandler(WeatherDivActionHandler(
        ::showScreen, ::goBack, ::onSetLang, ::onSetTheme, ::onSetCompact,
        ::onCitySearch, ::onSetCity))
    .divVariableController(variableController)
    .divCustomContainerViewAdapter(SunPhaseCustomViewAdapter())
    .extension(ScrollStateExtensionHandler(variableController))
    .visualErrorsEnabled(true)
    .build()
```

Status bar theming — add a private method and call it:
```
private fun applyStatusBarTheme(effectiveTheme: String) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    val light = effectiveTheme == "light"
    controller.isAppearanceLightStatusBars = light
    controller.isAppearanceLightNavigationBars = light
}
```
Call sites (pass the EFFECTIVE theme, i.e. `themeVar.getValue() as String` or the freshly computed value):
- End of `onCreate` (after variable setup): `applyStatusBarTheme(effective)`.
- In `onSetTheme(mode)`: after `themeVar.set(...)`, call `applyStatusBarTheme(resolveEffectiveTheme(mode))`.
- In `onConfigurationChanged`: inside the `system` branch, after `themeVar.set(...)`, call `applyStatusBarTheme(if (dark) "dark" else "light")`.
Rule: initial-render call is unconditional (any mode); it uses the already-computed `effective`.

City preferences — add companion consts `PREF_LAT`, `PREF_LON`, `PREF_CITY_NAME` (values `"pref_lat"`, `"pref_lon"`, `"pref_city_name"`). Add helpers:
- `private fun readCity(): Triple<String?, String?, String?>` reading the three prefs (null if absent).
- `private fun saveCity(lat: String, lon: String, name: String)` persisting all three.

Change `loadDocument(lang)` to also read city and pass it through:
```
private fun loadDocument(lang: String): Map<String, DivData> {
    val (lat, lon, name) = readCity()
    val loader = DocumentLoader(this)
    val fromNetwork = loader.loadFromNetwork(lang, lat, lon, name)
    ...unchanged fallback...
}
```

Add two new activity callbacks (mirror `onSetLang`):
```
// weather-app://city_search?q=<resolved> — off-main fetch, main-thread applyPatch on the firing view
private fun onCitySearch(query: String, view: Div2View) {
    val lang = readLangPref()
    lifecycleScope.launch(Dispatchers.IO) {
        val patch = DocumentLoader(this@MainActivity).loadCitySearch(query, lang)
        withContext(Dispatchers.Main) {
            if (patch != null) view.applyPatch(patch)
            else Log.w(TAG, "city_search patch null (q='$query')")
        }
    }
}

// weather-app://set_city?lat=&lon=&name= — persist + full refetch, like onSetLang
private fun onSetCity(lat: String, lon: String, name: String) {
    saveCity(lat, lon, name)
    val lang = readLangPref()
    lifecycleScope.launch(Dispatchers.IO) {
        val rawScreens = loadDocument(lang)
        withContext(Dispatchers.Main) {
            screens = buildScreensMap(rawScreens)
            renderScreen(currentScreen)
        }
    }
}
```
Import `com.yandex.div.core.view2.Div2View` (already imported) and `com.yandex.div2.DivPatch` is not needed here (DocumentLoader returns the parsed patch).

### FILE 4 — `app/src/main/java/com/example/weatherdivkit/WeatherDivActionHandler.kt`  (edit)
Extend the constructor (append two params; keep existing order):
```
class WeatherDivActionHandler(
    private val onNavigate: (Screen) -> Unit,
    private val onBack: () -> Unit,
    private val onSetLang: (String) -> Unit,
    private val onSetTheme: (String) -> Unit,
    private val onSetCompact: (Boolean) -> Unit,
    private val onCitySearch: (String, Div2View) -> Unit,
    private val onSetCity: (lat: String, lon: String, name: String) -> Unit,
) : DivActionHandler() {
```
Add import `com.yandex.div.core.view2.Div2View`.
Add two branches inside `handleWeatherAction(url)` (the handler still receives `view: DivViewFacade` and `resolver` in `handleAction`; you must thread `view` into `handleWeatherAction`, or read it from a field — simplest: change `handleWeatherAction(url)` to `handleWeatherAction(url, view)` and pass `view` from `handleAction`).
```
"city_search" -> {
    val q = url.getQueryParameter("q") ?: ""          // resolved value of @{city_query}; empty allowed
    val div2View = view as? Div2View ?: return false
    mainHandler.post { onCitySearch(q, div2View) }
    true
}
"set_city" -> {
    val lat = url.getQueryParameter("lat") ?: return false
    val lon = url.getQueryParameter("lon") ?: return false
    val name = url.getQueryParameter("name") ?: ""     // already URL-decoded by Uri.getQueryParameter
    mainHandler.post { onSetCity(lat, lon, name) }
    true
}
```
Rules: `url.getQueryParameter(...)` returns the already-expression-resolved, URL-DECODED value (the base handler evaluates `action.url` before we see the `Uri`). Do not decode again.

### FILE 5 — `app/src/main/java/com/example/weatherdivkit/divkit/DocumentLoader.kt`  (edit)
Change `loadFromNetwork` signature and URL building:
```
fun loadFromNetwork(lang: String, lat: String? = null, lon: String? = null, name: String? = null): Map<String, DivData>?
```
Build the URL with `Uri.parse("$baseUrl/document").buildUpon()` and `appendQueryParameter("lang", lang)`, then conditionally append `lat`/`lon`/`name` only when non-null/non-blank. `appendQueryParameter` URL-encodes `name` for you. Everything else in the method (OkHttp call, parse, fallbacks) unchanged. Import `android.net.Uri`.

Add a city-search fetch that returns a parsed `DivPatch` (off-main-thread caller):
```
fun loadCitySearch(query: String, lang: String): DivPatch? {
    val url = Uri.parse("$baseUrl/city-search").buildUpon()
        .appendQueryParameter("q", query)
        .appendQueryParameter("lang", lang)
        .build().toString()
    val request = Request.Builder().url(url).build()
    return try {
        val body = httpClient.newCall(request).execute().body?.string() ?: return null
        val json = JSONObject(body)
        val env = DivParsingEnvironment(ParsingErrorLogger.ASSERT)
        json.optJSONObject("templates")?.let { env.parseTemplates(it) }   // defensive; §11.4 has none
        DivPatch(env, json)
    } catch (e: Exception) {
        Log.e(TAG, "city-search failed (q='$query')", e); null
    }
}
```
Add imports `com.yandex.div2.DivPatch`. `DivParsingEnvironment` / `ParsingErrorLogger` / `JSONObject` / `Request` already imported. (Parse pattern verified: `DivPatch(env, JSONObject(body))` — `DivPatchApplyItemsTest.kt:61`, `DivUtils.kt:270`.)

### FILE 6 — NEW `app/src/main/java/com/example/weatherdivkit/divkit/ScrollStateExtensionHandler.kt`
Implements `com.yandex.div.core.extension.DivExtensionHandler`. (Interface verified: `.../core/extension/DivExtensionHandler.kt`; matching semantics: `DivExtensionController.kt` iterates handlers and calls `matches`, then `bindView`/`unbindView` on the main thread.)

Contract:
- Constructor takes the shared `DivVariableController`.
- Constant `EXTENSION_ID = "scroll_state"`.
- `matches(div)` = `div.extensions?.any { it.id == EXTENSION_ID } == true`. (Pattern verified in `DivSizeProviderExtensionHandler.kt:27-29`.)
- `bindView(divView, resolver, view, div)`:
  - `val rv = view as? RecyclerView ?: return` (the view of a DivKit `gallery`/`pager` is a `RecyclerView` subclass). Import `androidx.recyclerview.widget.RecyclerView`.
  - Read params from the matched extension: `val params = div.extensions?.first { it.id == EXTENSION_ID }?.params` (JSONObject or null).
    - `thresholdDp = params?.optInt("threshold_dp", 48) ?: 48`
    - `orientation = params?.optString("orientation") ?: "vertical"` (values `"vertical"` | `"horizontal"`)
  - `val thresholdPx = (thresholdDp * rv.resources.displayMetrics.density).toInt()`.
  - Create a `RecyclerView.OnScrollListener`; in `onScrolled` compute
    `offset = if (orientation == "horizontal") rv.computeHorizontalScrollOffset() else rv.computeVerticalScrollOffset()`,
    then `val collapsed = offset > thresholdPx`, and write the variable ONLY when it changed vs. the last written value (keep a nullable `Boolean` field / per-view tag).
  - Write the variable through the controller:
    `(variableController.get("header_collapsed") as? Variable.BooleanVariable)?.set(collapsed)`
    (Imports `com.yandex.div.data.Variable`.) This runs on the main thread (scroll callbacks are main-thread). Setting a global declared BooleanVariable triggers DivKit expression recomputation reactively — same mechanism as theme/compact.
  - Register the listener with `rv.addOnScrollListener(listener)` and store it in a view tag (use a stable resource-free key, e.g. `rv.setTag(TAG_KEY, listener)` with a private `View.generateViewId()`-derived constant, OR a `WeakHashMap<RecyclerView, OnScrollListener>` field) so `unbindView` can remove exactly that listener. Prefer the `WeakHashMap` approach — no resource id needed.
  - Immediately push an initial value: compute offset once and set `header_collapsed` (so state is correct on first bind / after rebind).
- `unbindView(...)`: `(view as? RecyclerView)?.let { rv -> listeners.remove(rv)?.let(rv::removeOnScrollListener) }`.
- Leave `preprocess`/`beforeBindView`/media hooks at their interface defaults (do not override).

Write the actual listener/lifecycle body yourself per the rules above.

### FILE 7 — NEW `app/src/main/java/com/example/weatherdivkit/divkit/SunPhaseCustomViewAdapter.kt`  (+ inner or sibling `SunPhaseView`)
Adapter implements `com.yandex.div.core.DivCustomContainerViewAdapter`. (Interface verified: `.../core/DivCustomContainerViewAdapter.kt`; dispatch pattern: `DemoDivCustomViewAdapter.kt`.)

Adapter contract:
- `CUSTOM_TYPE = "sun_phase"`.
- `isCustomTypeSupported(type) = type == CUSTOM_TYPE`.
- `createView(div, divView, resolver, path): View` → `return SunPhaseView(divView.context)` (a fresh custom `View`). Do NOT touch `div.items` (sun_phase has none).
- `bindView(view, div, divView, resolver, path)`: read `div.customProps` (a `JSONObject?`, verified field name `customProps` on the generated `com.yandex.div2.DivCustom`) and call `(view as SunPhaseView).setSunTimes(sunriseMin, sunsetMin, nowMin, colors)`.
  - `custom_props` are RAW JSON (NOT expression-resolved) — read literals directly.
  - Keys read (see CONTRACT BACK for the authoritative list):
    - `sunrise` (String "HH:mm", 24h) — REQUIRED.
    - `sunset` (String "HH:mm", 24h) — REQUIRED.
    - `now` (String "HH:mm") — OPTIONAL; if absent/blank, use the device wall clock (`Calendar` HOUR_OF_DAY*60 + MINUTE).
    - `arc_color`, `track_color`, `marker_color` (String "#AARRGGBB" or "#RRGGBB") — OPTIONAL; fall back to theme-neutral defaults below.
  - Parse "HH:mm" → minutes-since-midnight Int (0..1439). If a required key is missing/unparseable → render a neutral empty arc (no marker); do not crash.
- `release(view, div)` = no-op. `preload` = default (`DivPreloader.PreloadReference.EMPTY`, inherited — do not override unless needed).

`SunPhaseView` (a `View` subclass, custom Canvas draw) contract:
- Fields: `sunriseMin`, `sunsetMin`, `nowMin` (Int), plus arc/track/marker `Paint`s (ANTI_ALIAS).
- `setSunTimes(...)` stores values, `invalidate()`.
- Default colors (theme-neutral, look acceptable on light+dark): track `#33FFFFFF`-over? — use `arc = 0xFFFFB74D.toInt()` (warm amber), `track = 0x66FFFFFF` (semi-white), `marker = 0xFFFFFFFF.toInt()`. (Theme-reactive coloring is deferred techdebt — see ORCHESTRATOR NOTES.)
- `onDraw(canvas)` geometry (PIN this math — it is the load-bearing spec):
  - Let `pad` = markerRadius + strokeWidth (e.g. strokeWidth = 6dp, markerRadius = 6dp).
  - `cx = width / 2f`; `cy = height - pad`; `r = min(cx, cy) - pad`.
  - Draw the horizon-to-horizon semicircle (upper half): sweep an arc on rect `[cx-r, cy-r, cx+r, cy]` from `startAngle = 180f` sweep `180f` (top half). Draw once as the `track` paint (full), then draw the traversed portion `[180f .. 180f + f*180f]` in `arc` paint where `f` is the fraction below.
  - Fraction: `val f = ((nowMin - sunriseMin).toFloat() / (sunsetMin - sunriseMin)).coerceIn(0f, 1f)` (guard `sunsetMin > sunriseMin`; if not, treat as day-length 0 → skip marker).
  - Marker position (parametric, verified to hit left@f=0, top@f=0.5, right@f=1):
    `val mx = cx - r * cos(f * PI).toFloat()`; `val my = cy - r * sin(f * PI).toFloat()`; draw filled circle radius markerRadius at (mx,my) in `marker` paint.
  - Optionally draw small sunrise/sunset tick dots at the two endpoints. Keep it simple.
- Provide a sane default size: override `onMeasure` to give a default height (e.g. 96dp) if the parent gives UNSPECIFIED; otherwise respect the given spec.

Write the full `onDraw`/`onMeasure`/`setSunTimes` bodies yourself per these rules.

---

## PINNED LITERALS / GOLDEN VALUES (single block)
```
# Names (contract with A/B — verbatim)
custom_type            = "sun_phase"
extension id           = "scroll_state"
global bool variable   = "header_collapsed"   (default false)
divpatch target id     = "city_search_results"
query variable         = "city_query"
action set_city        = weather-app://set_city?lat=<double>&lon=<double>&name=<urlencoded>
action city_search     = weather-app://city_search?q=<string>

# Coil (verified R-32.57)
coil version           = 3.1.0
yandex coil artifact   = com.yandex.div:coil:32.57.0   (libs.divkit.coil)
image loader class     = com.yandex.div.coil.CoilDivImageLoader ; ctor CoilDivImageLoader(context: Context)
recyclerview           = androidx.recyclerview:recyclerview:1.3.2

# sun_phase custom_props keys read by native (C)
required: sunrise (String "HH:mm"), sunset (String "HH:mm")
optional: now (String "HH:mm"), arc_color, track_color, marker_color (String "#[AA]RRGGBB")

# scroll_state extension params read by native (C) — all optional
threshold_dp (int, default 48), orientation ("vertical"|"horizontal", default "vertical")

# arc marker parametric point (f in [0,1], sunrise→sunset)
mx = cx - r*cos(f*PI) ; my = cy - r*sin(f*PI)   # f=0→left, f=0.5→top, f=1→right
```

## VERIFIED API REFERENCE (cite if you doubt a signature)
| API | Signature / fact | Source path (R-32.57) |
|---|---|---|
| CoilDivImageLoader | `class CoilDivImageLoader; constructor(context: Context)` | `client/android/coil/src/main/java/com/yandex/div/coil/CoilDivImageLoader.kt:41` |
| DivConfiguration.Builder ctor | `Builder(@NonNull DivImageLoader)` | `client/android/div/src/main/java/com/yandex/div/core/DivConfiguration.java:488` |
| register custom | `Builder divCustomContainerViewAdapter(DivCustomContainerViewAdapter)` | `DivConfiguration.java:541` |
| register extension | `Builder extension(DivExtensionHandler)` | `DivConfiguration.java:594` |
| DivExtensionHandler | `matches/bindView/unbindView(divView,resolver,view,div)` | `client/android/div/src/main/java/com/yandex/div/core/extension/DivExtensionHandler.kt` |
| extension match by id | `div.extensions?.find{ it.id == ID }`, `.params: JSONObject?` | `client/android/div-size-provider/.../DivSizeProviderExtensionHandler.kt:27-39` |
| DivCustomContainerViewAdapter | `createView/bindView/isCustomTypeSupported/release/preload` | `client/android/div/src/main/java/com/yandex/div/core/DivCustomContainerViewAdapter.kt` |
| DivCustom.customProps | field `customProps: JSONObject?`, `customType: String` | generated `com.yandex.div2.DivCustom` (DSL model `json-builder/.../model/DivCustom.kt:20`) |
| Div2View.applyPatch | `fun applyPatch(patch: DivPatch): Boolean` (main thread) | `client/android/div/src/main/java/com/yandex/div/core/view2/Div2View.kt:507` |
| DivPatch from JSON | `DivPatch(env: DivParsingEnvironment, json: JSONObject)` | `DivPatchApplyItemsTest.kt:61`, `DivUtils.kt:270` |
| DivVariableController | `get(name): Variable?` ; `Variable.BooleanVariable.set(Boolean)` | `client/android/div-data/.../expression/variables/DivVariableController.kt:69` |

---

## TASK 8 (background image) — no new code
Coil migration is sufficient. `bg_key`→raw url is emitted by A and loaded by DivKit `image` via `CoilDivImageLoader`. Network allowance: `raw.githubusercontent.com` is HTTPS, so the existing `network_security_config.xml` (which only whitelists cleartext for `10.0.2.2`) needs NO change — HTTPS to public hosts is allowed by default. Do NOT add cleartext entries for githubusercontent. (Confirmed: `app/src/main/res/xml/network_security_config.xml`.)

---

## ACCEPTANCE (must NOT block on A/B merge)
1. `cd app && ./gradlew clean assembleDebug` compiles with zero errors.
2. `cd app && ./gradlew connectedDebugAndroidTest` (emulator-5554): the existing 9 Espresso tests (`WeatherUiTest`, `WeatherOfflineTest`) still pass — your changes touch native registration + chrome only, not the asset/layout JSON they assert on.
3. RECOMMENDED smoke test (proves registration works without A/B) — new instrumentation test `app/src/androidTest/java/com/example/weatherdivkit/RegistrationSmokeTest.kt`:
   - Build a `DivConfiguration` exactly like `MainActivity` (Coil loader + `SunPhaseCustomViewAdapter` + `ScrollStateExtensionHandler(controller)` + a controller declaring `header_collapsed`).
   - Build a `Div2Context` + `Div2View`, `setData(...)` an INLINE `DivData` containing (a) a vertical `gallery` carrying `"extensions":[{"id":"scroll_state"}]` with a couple text items, and (b) a `custom` of `"custom_type":"sun_phase"` with `"custom_props":{"sunrise":"06:00","sunset":"20:00","now":"13:00"}`.
   - Assert: no exception thrown; the view tree contains a `SunPhaseView` (recurse children). This proves both the custom factory and extension registration bind without crashing, independent of the backend.
   - Run on `emulator-5554`.
4. Manual (optional, if a live backend from A/B is up): tap a city result → `set_city` refetch; type in the city input → `city_search` DivPatch fills `city_search_results`; toggle theme → status-bar icons flip.

---

## CONTRACT BACK TO A / B  (native side registered by C — must equal the name map)
- **Custom view**: `custom_type = "sun_phase"`. A must emit a `custom` div with:
  `"custom_props": { "sunrise": "HH:mm", "sunset": "HH:mm", "now"?: "HH:mm", "arc_color"?, "track_color"?, "marker_color"? }`.
  `sunrise`/`sunset` are REQUIRED, 24-hour `"HH:mm"` local-time STRINGS (bake the current forecast's sunrise/sunset). `now` is OPTIONAL (city-local wall clock as "HH:mm"); if omitted, C uses the device clock. Colors optional "#AARRGGBB". custom_props are literals, NOT expressions.
- **Scroll extension**: `id = "scroll_state"`. A attaches it to the scroll container that drives header collapse (a `gallery`, typically the vertical root scroll — the bound view MUST be a RecyclerView-backed `gallery`/`pager`). Optional `params`: `{"threshold_dp": <int, default 48>, "orientation": "vertical"|"horizontal" (default "vertical")}`.
- **Header variable**: `header_collapsed` (Boolean) is DECLARED GLOBALLY BY C and set true/false from scroll. A reads it in expressions (e.g. `@{header_collapsed}`). A must NOT declare it in JSON `variables`.
- **DivPatch target**: C applies the backend `/city-search` patch to the div with `id = "city_search_results"` on the firing screen's Div2View. B must give the results container that exact id.
- **Actions consumed by C**: `weather-app://city_search?q=@{city_query}` (q = resolved query text; empty allowed → empty-state patch) and `weather-app://set_city?lat=&lon=&name=` (name URL-encoded by B; C decodes, persists, refetches `/document?lang=&lat=&lon=&name=`). Query variable read: `city_query` (String) — B owns the `input.text_variable`.

---

## ORCHESTRATOR NOTES (implementer may skip)
- `plan/stage1_A_contract.md` did NOT exist at planning time, so `sun_phase` custom_props keys are defined authoritatively HERE (`sunrise`/`sunset` required "HH:mm", optional `now`/colors). If A's contract lands with different keys, reconcile toward THIS spec or update both — C is the reader, A must match C.
- Fork resolved (time format): chose `"HH:mm"` strings over epoch/minutes-int because the backend already has ISO sunrise/sunset and this is timezone/DST-robust for a local computation; `now` is optional to let the backend pass city-accurate local time (device clock differs when viewing a foreign city). If the workshop wants strict city-time accuracy always, make A pass `now` — no C change needed.
- Fork resolved (variable write path): `header_collapsed` declared in the shared global `DivVariableController` and written via `controller.get(...) as BooleanVariable`, rather than per-view `divView.setVariable("header_collapsed", "true")`. Reason: type-safe, single source of truth, mirrors theme/compact. Both work; the global path is cleaner.
- Fork resolved (Coil deps): only `com.yandex.div:coil` added; its POM pulls coil3 gif/network/cache-control at runtime scope. If a runtime `ClassNotFoundException: coil3.network.*` ever appears on a stripped build, add `implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")` explicitly — not expected.
- Techdebt (deferred, record it): `SunPhaseView` colors are theme-neutral defaults, NOT reactive to the `theme` global variable (custom_props aren't expression-resolved and the view isn't re-bound on theme toggle). If theme-accurate arc colors are wanted, a later stage can inject the controller into the adapter and repaint on a `theme` variable observer. Out of scope for Stage 1.
- Status bar on API 35+/36: `window.statusBarColor` is a deprecated no-op under enforced edge-to-edge; the real lever is `isAppearanceLightStatusBars` (icon contrast) with the DivKit content background showing through. This is intentional; do not fight edge-to-edge.
- This stage is genuinely native + glue (no exotic algorithm); the risky parts are the DivKit API surfaces, all verified above. The implement→review ceremony is warranted because it touches the action handler (auth-ish routing) and money-free but state-changing refetch flow.

## BOUNDARIES / WHAT DEFERS
- No changes to `backend/**`, `plan/contract.md`, or any layout/asset JSON.
- C does NOT author the main/settings JSON — A/B own the `sun_phase` div, the `scroll_state` gallery, the `city_search_results` container, the `city_query` input, and all `header_collapsed` expressions. C only registers native handlers matching those names.
- e2e wiring across A+B+C (full scroll→collapse, search→patch→set_city→refetch on a live backend) is Stage 2 integration.
</content>
</invoke>
