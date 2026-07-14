# CONTRACT — Offline layout cache + zero-state shimmer skeleton + graceful language switch

Target implementer: `kopatel` (Sonnet). Follow literally. Verify on emulator-5554 with backend live on `:8080`.
Keep backend 17/17 and instrumented 13 (+ new) green.

---

## MUST-NOT-GET-WRONG (load-bearing invariants)

1. **Shimmer ONLY works on `image`/`gif` divs, and only while the image is UNLOADED.** (`DivShimmerExtensionHandler.matches` requires `DivImage`/`DivGifImage`; `bindView` returns early if `isImageLoaded||isImagePreview`.) The skeleton's shimmering blocks MUST be `image` divs whose `image_url` **never loads** (use the reserved `.invalid` URL below). Never a data:/real URL (loads instantly → shimmer disappears). Never put shimmer on `text`/`container`.
2. **Cold start = network → cache(lang) → bundled zero asset.** In-session refresh (lang / city / pull-to-refresh) = **network ONLY**; on failure KEEP the current rendered layout — never fall back to cache/asset there, never blank the screen.
3. **Language switch must not wipe the layout.** Fetch new-lang in background; swap ONLY on success. On failure try `cache(lang)`; if that also misses, keep the current layout untouched.
4. `/zero` returns the **exact same envelope shape** as `/document` (`{"templates":{...},"screens":{"main":…,"settings":…,"about":…}}`) so the client parses it identically. `/zero` must NOT call the weather provider or geocoder (skeleton = no real data).
5. Skeleton must keep the structural ids the client depends on: `header` (state, `state_id_variable:"header_state"`), `main_scroll` (vertical gallery with `scroll_state` extension), `hourly_gallery`, and settings/about scroll ids (via reusing the real settings/about renderers). Add a NEW id `zero_skeleton` on the skeleton root (test marker; real docs never carry it).
6. Register `DivShimmerExtensionHandler()` in `divConfiguration` and add the `div-shimmer:32.57.0` dependency. Do NOT touch the existing `scroll_state`/`sun_phase` registrations.
7. Do NOT add any `ConnectivityManager`/`NetworkCallback` auto-refetch. Refresh stays manual.
8. Cache write happens INSIDE `loadFromNetwork` on parse success, atomically (tmp+rename), keyed by lang. Never cache an error/partial body.

---

# CLIENT

## C1. `DocumentLoader.kt` — add cache read/write

File: `app/src/main/java/com/example/weatherdivkit/divkit/DocumentLoader.kt`

Add (bodies are yours; obey the rules):

```
private fun cacheFile(lang: String): File = File(context.filesDir, "doc_cache_$lang.json")

/** Reads doc_cache_<lang>.json if present; parseEnvelope; null on missing/parse error. */
fun loadFromCache(lang: String): Map<String, DivData>?
```

Rules:
- In `loadFromNetwork`, **after** `parseEnvelope(JSONObject(body))` succeeds and **before** returning it, persist the raw `body` string to `cacheFile(lang)` **atomically**: write to `File(context.filesDir, "doc_cache_$lang.json.tmp")` then `renameTo(cacheFile(lang))`. Wrap the write in try/catch — a cache-write failure must NOT fail the network path (log at WARN, still return the parsed map). Cache ONLY when parse succeeded (i.e. write after the parse call returns without throwing).
- `loadFromCache(lang)`: if `!cacheFile(lang).exists()` return null; else read text, `parseEnvelope(JSONObject(text))`, return the map; on ANY exception log WARN and return null (a corrupt cache must degrade to the zero asset, never crash).
- Do not change `loadFromAssets()`, `parseEnvelope`, `loadCitySearch`, `baseUrl`, or the network URL building.
- Add `import java.io.File`.

Acceptance: a successful network fetch leaves a well-formed `doc_cache_<lang>.json` in `filesDir`; `loadFromCache` round-trips it to the same screen map.

## C2. `MainActivity.kt` — two-phase cold start, graceful refreshes

File: `app/src/main/java/com/example/weatherdivkit/MainActivity.kt`

### C2.1 Imports / registration
- `import com.yandex.div.shimmer.DivShimmerExtensionHandler`
- In the `divConfiguration` builder chain, add exactly one line **after** `.extension(ScrollStateExtensionHandler(variableController))`:
  `.extension(DivShimmerExtensionHandler())`
  (Order among extensions is irrelevant; each handler's `matches` gates it. Keep all existing lines.)

### C2.2 Cold start — replace the single `onCreate` load with two phases
Replace the current `onCreate` load block (the `lifecycleScope.launch(Dispatchers.IO){ loadDocument… showScreen(MAIN) }`) with a two-phase flow:

- **Phase 1 (instant local):** off-main, `val initial = loader.loadFromCache(lang) ?: loader.loadFromAssets()`; back on main `screens = buildScreensMap(initial); showScreen(Screen.MAIN)`. (Keep it in a `lifecycleScope.launch(Dispatchers.IO)` → `withContext(Main)`; the file read is cheap.)
- **Phase 2 (network swap):** after phase 1's main-thread block, launch a SECOND off-main fetch: `val fresh = loader.loadFromNetwork(lang, lat, lon, name)`; on `fresh != null` → main-thread `screens = buildScreensMap(fresh); renderScreen(currentScreen)`. On `null` do nothing (keep phase-1 layout).
- Phase 2 MUST call `renderScreen(currentScreen)` (NOT `showScreen`) so it preserves `currentScreen`/`backStack` if the user already navigated.
- You may nest phase 2 inside phase 1's completion, or chain them; either is fine as long as phase 1 renders first and phase 2 never runs before phase 1 has assigned `screens`.
- Read `lang`, city triple once (as today via `readLangPref()`, `readCity()`).

### C2.3 `loadDocument(lang)` — becomes cold-start-only fallback chain
Change the body to: **network → cache(lang) → assets**. (Currently network → assets.) i.e. after the `loader.loadFromNetwork(...)` non-null return, insert a `loader.loadFromCache(lang)?.let { return it }` before the assets fallback. Keep the existing log lines; add one for the cache branch. NOTE: with C2.2 you may no longer call `loadDocument` from `onCreate` (phases call the loader directly). Keep `loadDocument` only if some caller still uses it; otherwise delete it. Do NOT use `loadDocument` (which falls through to assets) from any in-session refresh — see C2.4/C2.5/C2.6.

### C2.4 `onSetLang(lang)` — graceful, never wipes
Rewrite to network-only with keep-current semantics:
1. `saveLangPref(lang)` (persist intent immediately — keep as today).
2. off-main: `val fresh = DocumentLoader(this).loadFromNetwork(lang, lat, lon, name)`.
3. main:
   - `fresh != null` → `screens = buildScreensMap(fresh); renderScreen(currentScreen)`.
   - else → `val cached = loader.loadFromCache(lang)`; if non-null → swap+`renderScreen(currentScreen)`; else → **do nothing** (log INFO "offline, keeping current layout"). Never call `loadFromAssets` here.

### C2.5 `onSetCity(lat,lon,name)` — network only, keep current on failure
1. `saveCity(...)` as today.
2. off-main: `loadFromNetwork(lang, lat, lon, name)`.
3. main: non-null → swap + `renderScreen(currentScreen)`; null → do nothing (log INFO). Do NOT use cache here (cache is lang-keyed and would show the OTHER city's data) and do NOT fall back to assets.

### C2.6 `onPullToRefresh()` — network only, keep current on failure
Keep the spinner logic (`finally { isRefreshing = false }`). Change the fetch to network-only: `loadFromNetwork(lang, lat, lon, name)`; non-null → swap + `renderScreen(currentScreen)`; null → keep current (do not blank). Do NOT fall back to cache/assets.

### C2.7 No auto-refetch
Add NOTHING network-connectivity-driven. (Verified: no `ConnectivityManager`/`NetworkCallback` exists today — keep it that way.)

Acceptance (client): offline cold start with a cache shows the cached layout; offline cold start with no cache shows the zero skeleton (has `zero_skeleton`); offline language switch keeps the current layout on screen (no skeleton, no blank); pull-to-refresh offline keeps the current layout.

## C3. `app/gradle/libs.versions.toml` — shimmer dependency
- Add under `[libraries]`: `divkit-shimmer = { group = "com.yandex.div", name = "div-shimmer", version.ref = "divkit" }`
- In `app/build.gradle.kts` dependencies, add: `implementation(libs.divkit.shimmer)` next to the other `divkit.*` lines.
- `divkit = "32.57.0"` release of `div-shimmer` is confirmed on mavenCentral — no extra repo needed.

---

# BACKEND

## B1. New `WeatherZeroRenderer.kt` (skeleton for the MAIN screen)
File: `backend/src/main/kotlin/workshop/renderer/WeatherZeroRenderer.kt`
Signature: `class WeatherZeroRenderer(private val localizer: Localizer) { fun render(): Pair<String, Divan> }` returning `"main" to divan { buildCard(this) }`.

It reproduces the OUTER structure of `WeatherMainRenderer` (so the skeleton matches prod layout and keeps the required ids) but fills every DATA leaf with either a dash (`"—"`) or a shimmer image bar. It is SELF-CONTAINED — do NOT modify or import from `WeatherMainRenderer` (keep prod renderer untouched; boundary). Reuse the same DSL builders (`container`, `image`, `text`, `gallery`, `state`, `stateItem`, `border`, `edgeInsets`, `solidBackground`, `color`, `extension`, `url`, `fixedSize`, `matchParentSize`, `wrapContentSize`, expression helpers) as `WeatherMainRenderer`.

Required structure (ids/exprs are load-bearing — reproduce EXACTLY):

- Root `data(logId = "main_weather", div = container(orientation = overlap, width = matchParentSize(), height = matchParentSize(), id = "zero_skeleton", items = [background, scrollBody, headerState, fabRow]))`. No `popup`, no local `variables`.
- **background** = `container(width=matchParentSize(), height=matchParentSize(), background = listOf(solidBackground().evaluate(color = expression<Color>(BG_EXPR))))` with `BG_EXPR = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"`. (No photo — a neutral theme-aware backdrop.)
- **headerState** = `state(id = "header", defaultStateId = "full", stateIdVariable = "header_state", states = listOf(stateItem("full", fullHeader), stateItem("collapsed", compactHeader)), width = matchParentSize(), height = wrapContentSize(), alignmentVertical = top)`.
  - `fullHeader` = vertical container, `paddings = edgeInsets(start=20,end=20,bottom=8).evaluate(top = expression<Int>("@{24 + status_inset}"))`, items = dashed texts: city `"—"` (fontSize 20, bold, TITLE_COLOR_EXPR), `"—°"` (fontSize 72, bold, top margin 4, TITLE_COLOR_EXPR), `"—"` (fontSize 18, SUB_COLOR_EXPR), and a horizontal row `text("↑ —°", 16, TITLE) + text("  ↓ —°", 16, SUB, start margin 12)`.
  - `compactHeader` = vertical container, `paddings = edgeInsets(start=20,end=20,bottom=8).evaluate(top = expression<Int>("@{12 + status_inset}"))`, `background = listOf(solidBackground().evaluate(color = expression<Color>(HEADER_SCRIM_EXPR)))`, items = `text("—", 17, bold, TITLE)` + `text("—  |  —", 15, top margin 2, SUB)`.
- **scrollBody** = `gallery(id = "main_scroll", orientation = vertical, extensions = listOf(extension(id = "scroll_state", params = mapOf("orientation" to "vertical"))), width = matchParentSize(), height = matchParentSize(), paddings = edgeInsets().evaluate(top = expression<Int>("@{(compact ? 76 : 210) + status_inset}"), bottom = expression<Int>("@{96 + nav_inset}")), items = listOf(hourlyGallery, weeklyBlock, sunsetCard, detailsGrid))`.
  - **hourlyGallery** = `gallery(id = "hourly_gallery", orientation = horizontal, width = matchParentSize(), height = wrapContentSize(), paddings = edgeInsets(start=16,end=16), itemSpacing = 12, items = List(8){ shimmerBar(width = fixedSize(64), height = fixedSize(90)) })`.
  - **weeklyBlock** = vertical container card (`margins = edgeInsets(start=16,top=16,end=16)`, `background = solidBackground().evaluate(color = CARD_BG_EXPR)`, `border = border(cornerRadius=16)`, `paddings = edgeInsets(all 8)`) with `items = List(7){ container(orientation=horizontal, width=matchParentSize(), paddings=edgeInsets(top=10,bottom=10,start=8,end=8), items = listOf(shimmerBar(width=matchParentSize(), height=fixedSize(20)))) }`.
  - **detailsGrid** = horizontal container (`margins = edgeInsets(start=16,top=16,end=16)`) of two vertical columns `matchParentSize(weight=1.0)`; each contains `detailSkeletonCard(...)` entries mirroring prod counts: LEFT 4 (titles: `weather.uv`, `weather.precipitation`, `weather.humidity`, `weather.wind`), RIGHT 3 (`feels_like`, `weather.visibility`, `weather.pressure`). Icons same emoji as prod (🔆 🌧️ 💧 💨 / 🌡️ 👁️ 🧭).
  - **sunsetCard** = `detailSkeletonCard(icon="", title = loc("weather.sunset","Sunset"), margins = edgeInsets(start=16,top=16,end=16), bodyHeight = 120)` — its body shimmer bar replaces the `sun_phase` custom (custom needs data; skeleton uses shimmer). Optional subtitle `loc("card.sunrise_at","Sunrise at") + " —"`.
- **fabRow** = REPRODUCE prod's FAB row verbatim (static chrome): vertical container, `alignmentHorizontal=right`, `alignmentVertical=bottom`, `paddings = edgeInsets(end=16).evaluate(bottom = expression<Int>("@{20 + nav_inset}"))`, items = two `fab(...)`: `fab("⚙", action(logId="fab_settings", url=url("weather-app://navigate?screen=settings")), id="fab_settings")` and `fab("ℹ", action(logId="fab_about", url=url("weather-app://navigate?screen=about")), id="fab_about")`. Copy the prod `fab(...)` helper (uses FAB_BG_EXPR/FAB_ICON_EXPR).

Helpers to write in this renderer:
- `private fun DivScope.shimmerBar(width: Size, height: Size, cornerRadius: Int = 12): Div` →
  `image(imageUrl = url(SKELETON_IMAGE_URL), width = width, height = height, scale = fill, border = border(cornerRadius = cornerRadius), extensions = listOf(extension(id = "shimmer")))`. (No `params` → shimmer uses its gray defaults. No `preview`/`placeholder`.) The `image()` `width`/`height` param type is `Size` — pass `fixedSize(n)` or `matchParentSize()`.
- `private fun DivScope.detailSkeletonCard(icon, title, margins, bodyHeight? = null, subtitle? = null): Div` → same card chrome as prod `detailCard` (vertical container, `matchParentSize(weight=1.0)`, `paddings edgeInsets(all 14)`, `background = CARD_BG_EXPR`, `border cornerRadius 16`): first child = the localized label `text(if(icon.isBlank()) title.uppercase() else "$icon  ${title.uppercase()}", fontSize 12, SUB_COLOR_EXPR)`, then a **big-value shimmer bar** `shimmerBar(width = fixedSize(90), height = fixedSize(28), margins top 8)` (give the bar a top margin via wrapping container or an `edgeInsets` if the DSL image supports `margins` — it does), then optional body shimmer bar `shimmerBar(width=matchParentSize(), height=fixedSize(bodyHeight))` when `bodyHeight != null`, then optional dashed subtitle text.
- `private fun DivScope.fab(...)` copied from prod.
- `private fun loc(key, fallback) = localizer.getOrDefault(key, fallback)`.

Companion literals to pin (copy from prod so colors match):
```
TITLE_COLOR_EXPR  = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"
SUB_COLOR_EXPR    = "@{theme == 'dark' ? '#FF9E9EA3' : '#FF6E6E73'}"
CARD_BG_EXPR      = "@{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}"
HEADER_SCRIM_EXPR = "@{theme == 'dark' ? '#99000000' : '#99FFFFFF'}"
FAB_BG_EXPR       = "@{theme == 'dark' ? '#E6D8D8DD' : '#E63A3A3C'}"
FAB_ICON_EXPR     = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFFFFFFF'}"
BG_EXPR           = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"
SKELETON_IMAGE_URL = "https://divkit.invalid/skeleton.png"   // .invalid TLD (RFC 2606) → never resolves → image never loads → shimmer persists
```

## B2. `WeatherServant.zero(lang)` — assemble the zero envelope
File: `backend/src/main/kotlin/workshop/servant/WeatherServant.kt`
Add: `suspend fun zero(lang: String): String`. Body mirrors `handle(...)` EXCEPT: no `weatherProvider.provide`, no city. Build `(mainKey, mainDivan) = WeatherZeroRenderer(localizer(lang)).render()`; reuse the existing `WeatherSettingsRenderer(localizer)` and `WeatherAboutRenderer(localizer)` verbatim; collect templates from all three divans (`buildMap { putAll(...) }`); serialize `{"templates":…, "screens":{main→mainDivan.card, settings→settingsDivan.card, about→aboutDivan.card}}` with the existing `mapper`. Same envelope shape as `handle`.

## B3. `Application.kt` — `/zero` route
File: `backend/src/main/kotlin/workshop/Application.kt`
Add a route mirroring `/document`'s lang handling but with no city:
```
get("/zero") {
    val lang = call.request.queryParameters["lang"]?.takeIf { it in listOf("ru","en") } ?: "ru"
    call.respondText(text = servant.zero(lang), contentType = ContentType.Application.Json)
}
```

Acceptance (backend): `GET /zero?lang=ru` returns HTTP 200 JSON with `templates` + `screens.{main,settings,about}`; `main` contains `id:"zero_skeleton"`, `id:"header"` (with `state_id_variable:"header_state"`), `id:"main_scroll"` (with a `scroll_state` extension), and ≥1 `image` div carrying `extensions:[{"id":"shimmer"}]` with `image_url` = the `.invalid` URL; no real temperatures/city names appear (dashes only). `GET /zero?lang=en` localizes the settings/about chrome + detail labels.

---

# TOOLING

## T1. `scripts/update-bundled-layout.sh`
New file `scripts/update-bundled-layout.sh` (chmod +x). Purpose: regenerate the bundled skeleton asset from a running backend in one command.
Requirements:
- `#!/usr/bin/env bash` + `set -euo pipefail`.
- Vars: `BASE_URL="${BASE_URL:-http://localhost:8080}"`, `LANG_CODE="${LANG_CODE:-ru}"`, `OUT` = repo-relative `app/src/main/assets/document.json` resolved from the script's own dir (`SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"`, `OUT="$SCRIPT_DIR/../app/src/main/assets/document.json"`).
- `curl -fsS "$BASE_URL/zero?lang=$LANG_CODE"` into a temp file; validate it is non-empty and contains `"screens"` and `"zero_skeleton"` (grep); on success `mv` temp → `$OUT`; else print an error and exit 1 (do NOT clobber the existing asset on failure).
- Echo a success line with byte count and a reminder that the backend must be running (`./gradlew :backend:run` or the built jar).
- Add a short usage comment at top: how to run (start backend, then `./scripts/update-bundled-layout.sh`), and that `LANG_CODE=en BASE_URL=… ` overrides.

Acceptance: with backend up, `./scripts/update-bundled-layout.sh` overwrites `app/src/main/assets/document.json` with the `/zero?lang=ru` body; the file contains `zero_skeleton` and no real weather values.

## T2. Regenerate the asset as part of this stage
After B1–B3 build green, RUN the script against a live backend so the committed `app/src/main/assets/document.json` is the NEW skeleton (replacing the stale data asset). Verify the new asset contains `zero_skeleton`, `header`, `main_scroll`, `hourly_gallery`, `"id":"shimmer"`, and NO real temps/city. This satisfies req 4 (stale asset replaced, no real data).

---

# TESTS

Backend (`backend/src/test/kotlin/workshop/ApplicationSmokeTest.kt` or a new sibling):
- Add a test hitting `/zero?lang=ru`: assert 200, body parses as JSON, has `templates` + `screens.main/settings/about`, `main` JSON contains substrings `"zero_skeleton"`, `"header"`, `"main_scroll"`, `"scroll_state"`, `"shimmer"`, and does NOT contain a real city/temperature (e.g. assert it does NOT contain `"17°"`/`"Москва"`; assert it DOES contain `"—"`). Keep the existing 17 green.

Instrumented (`app/src/androidTest/...`), using the existing `DivIdMatchers`/`TestHelpers`:
- **NEW `zero_skeleton_whenOfflineNoCache`** (new file or in `WeatherOfflineTest`): `@Before` set `DocumentLoader.baseUrl` to a dead address AND delete any `doc_cache_*.json` from `context.filesDir` (clear cache). Launch → `waitForDivDisplayed("main_scroll")` → `assertDivDisplayed("zero_skeleton")`, `assertDivDisplayed("header")`. Proves offline-no-cache shows the skeleton.
- **NEW `cacheBeatsSkeletonOfflineRestart`** (requires backend live): (1) with default `baseUrl`, launch → `waitForDivDisplayed("main_scroll")`; `assertDivNotDisplayed("zero_skeleton")` (real data cached now) → close scenario. (2) set `baseUrl` dead → relaunch → `waitForDivDisplayed("main_scroll")`; `assertDivNotDisplayed("zero_skeleton")` (phase-1 used cache, not the skeleton). Proves cache beats the zero asset offline. NOTE: `assertDivNotDisplayed` uses the `not(isDisplayed())` matcher which passes when the view is absent; confirm it tolerates absence (it should — the matcher fails only if a displayed match exists). If absence throws NoMatchingViewException, wrap in a helper `assertDivAbsent(id)` that treats NoMatchingView as pass.
- **Existing `WeatherOfflineTest.offline_fallsBackToAssets`** stays green (skeleton keeps `header`+`main_scroll`). Update its doc comment to say the asset is now the zero skeleton.
- Graceful offline language switch: OPTIONAL instrumented (asserting "layout unchanged" is awkward via Espresso); the FINAL VERIFIER covers it manually. If attempted: launch online → wait `main_scroll` → set `baseUrl` dead → drive the settings language toggle (navigate to settings via `clickDivId("fab_settings")`, tap the EN/RU control) → return to main → assert `main_scroll` still displayed and `zero_skeleton` NOT displayed.

Acceptance (tests): backend `/zero` test + the two new instrumented tests pass; existing 13 instrumented and 17 backend stay green.

---

# GOLDEN / PINNED LITERALS (single source of truth)

| Thing | Value |
|---|---|
| Shimmer extension id | `"shimmer"` |
| Shimmer handler class | `com.yandex.div.shimmer.DivShimmerExtensionHandler` (ctor `()` ok, `animationStartTime` defaults 0) |
| Shimmer artifact | `com.yandex.div:div-shimmer:32.57.0` (mavenCentral, confirmed 200) |
| Shimmer default colors (no params) | `[#FF7F7F7F, #FFAAAAAA, #FF7F7F7F]`, angle 0, duration 1.6s, locations `[0.3,0.5,0.7]` |
| Skeleton placeholder URL | `https://divkit.invalid/skeleton.png` (never loads) |
| Skeleton root id | `zero_skeleton` |
| Preserved structural ids | `header`, `main_scroll`, `hourly_gallery`, `fab_settings`, `fab_about` |
| header state var | `header_state` (`state_id_variable`), default `full` |
| scroll_state ext params | `{"orientation":"vertical"}` |
| scrollBody top padding expr | `@{(compact ? 76 : 210) + status_inset}`; bottom `@{96 + nav_inset}` |
| Cache file | `filesDir/doc_cache_<lang>.json` (atomic tmp+rename) |
| Cold-start order | network → cache(lang) → assets(zero) |
| In-session refresh order | network only; keep current on fail (lang additionally tries cache(lang)) |
| Zero endpoint | `GET /zero?lang=ru|en` (default ru), no city, no weather provider |
| Update script | `scripts/update-bundled-layout.sh` → curl `/zero?lang=ru` → `app/src/main/assets/document.json` |

Color/expr literals for the skeleton: see B1 companion block (copy prod verbatim).

---

# VERIFIED DIVKIT FACTS (source paths, R-32.57 @ `/Users/the-leo/divkit_source/divkit`)

- Shimmer matches only image/gif & only while unloaded — `client/android/div-shimmer/src/main/java/com/yandex/div/shimmer/DivShimmerExtensionHandler.kt:37-45` (`matches`: `div !is DivGifImage && div !is DivImage → false`) and `:58-61` (`view as? LoadableImage ?: return`; `if (isImageLoaded || isImagePreview) return`). Extension id `:24` = `"shimmer"`.
- Shimmer params + defaults — `client/android/div-data/src/main/java/com/yandex/div/internal/extensions/ShimmerExtensionParams.kt` (`colors`/`locations`/`angle`/`duration`/`corner_radius`; defaults gray/1.6s/0/[0.3,0.5,0.7]). Empty/absent params → all defaults.
- Handler ctor — same file `:33-35` `open class DivShimmerExtensionHandler(private var animationStartTime: Long = 0L)`.
- Regular image view is a `LoadableImage` — `client/android/div/src/main/java/com/yandex/div/internal/view/DivImageView.kt` → `com/yandex/div/core/widget/LoadableImageView.kt` (implements `com/yandex/div/core/view2/divs/widgets/LoadableImage.kt`). `isImageLoaded` only set true on successful load; a failed/never-resolving URL keeps it false → shimmer persists.
- `DivConfiguration.Builder.extension(...)` accepts a `DivExtensionHandler` (already used for `scroll_state`); handler interface `client/android/div/src/main/java/com/yandex/div/core/extension/DivExtensionHandler.kt`.
- divan `image(imageUrl: Url? = null, …, extensions: List<Extension>? = null, …)` — `json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/Image.kt:462-483`. `extension(id: String? = null, params: Map<String,Any>? = null)` — `.../Extension.kt:69-72`.
- Client parse path unchanged — `DocumentLoader.parseEnvelope` (one `DivParsingEnvironment`, templates parsed once). Do not alter.

---

# ORCHESTRATOR NOTES (implementer may skip; defaults already chosen)

- **Header dashes vs shimmer:** header text values are DASHES (`"—"`), the scrollable data sections (hourly/weekly/detail values/sun) are SHIMMER bars. Chosen because the user listed "dashes instead of city/temperature/etc." AND "shimmer on the dynamic blocks" — this split satisfies both and keeps header height stable.
- **Why a separate `WeatherZeroRenderer` (not a `zero=true` flag through the main renderer):** shimmer requires `image` leaves, so the skeleton's leaves are structurally different from the data renderer's `text` leaves — a threaded flag would branch at every leaf (pervasive, risky) and would force touching the tested prod renderer. Separate renderer = clean diff, prod main renderer untouched (boundary). Settings/About renderers ARE reused verbatim (they are static localized chrome), so duplication is limited to the one main-skeleton file.
- **Cache is lang-keyed only:** an offline language switch to a previously-fetched lang works; but that cached doc reflects whatever CITY was current at its last successful fetch, so an offline lang-switch after an offline city-change could show the older city under the new language. Accepted edge (offline, best-effort). `onSetCity` deliberately does NOT read cache to avoid showing the wrong city.
- **Pref-on-intent:** `onSetLang` saves the lang pref immediately even if the fetch fails; the on-screen layout stays the old language until a later successful fetch. This matches "setting changed, refresh pending." If you prefer save-on-success, that's a one-line move — flagged, not blocking.
- **Bundled asset lang:** the skeleton asset is generated for `ru` (matches `DEFAULT_LANG`). First-launch users in an `en` locale briefly see ru chrome in the skeleton until the first successful fetch. Acceptable for a throwaway skeleton; generating per-locale assets is out of scope.
- **div-shimmer version:** pinned to the shared `divkit=32.57.0`. Release AAR confirmed on mavenCentral (curl 200). If a future bump makes it unresolvable, the cached fallback is the nightly `32.57.0-20260701.130351-NIGHTLY` (would need the yandex nightly maven repo added) or `32.56.0` release — but 32.57.0 release resolves today, so no repo change is needed. Do NOT silently downgrade; escalate if it fails.
- **This stage is NOT thin glue** — it needs the full planner→implementer→review ceremony (new backend renderer + non-trivial client load-order state machine + shimmer integration whose failure modes are subtle).

# BOUNDARIES / OUT OF SCOPE
- Do not modify `WeatherMainRenderer.kt`, `WeatherSettingsRenderer.kt`, `WeatherAboutRenderer.kt`, the weather providers, geocoder, `/document`/`/city-search`/`/weather-json` routes, `parseEnvelope`, or the `scroll_state`/`sun_phase` handlers.
- Do not add connectivity-driven auto-refetch (req 7).
- Do not change the envelope shape or the single-templates-parse path.
- No commits.
