# CONTRACT — Modernise Android instrumented (Espresso) tests: real-server layout + find-by-div-id

Scope: `app/src/androidTest/**` only (plus a SEPARATE, clearly-marked list of backend `id=` requests for
the renderer owner). Target device: `emulator-5556` with the backend live on host `:8080`
(reachable from the emulator as `http://10.0.2.2:8080`). Acceptance gate: `./gradlew connectedDebugAndroidTest`
green on `emulator-5556` with the backend up.

---

## MUST-NOT-GET-WRONG (load-bearing invariants)

1. A DivKit div `id` reaches the Android view as the **plain view tag**: `view.getTag() == "<div id string>"`.
   Match on `view.tag`, NOT on `view.id` (see §1 for the source proof and why `view.id` is unusable).
2. `view.id` (the int) is a **runtime `View.generateViewId()`** value, NOT an `R.id` constant — it changes
   every run. Espresso `withId(...)` CANNOT be used to find divs. Only the string tag is stable.
3. Only `DivBase.id` becomes a tag. **Action `log_id`, gallery `extension` ids, and `custom_type` do NOT
   become tags.** A div with no `id` has `tag == null`. So elements the tests click MUST have a real `id=`
   in the backend DSL (see §4 BACKEND IDS REQUESTED) — a `log_id` alone is not findable.
4. DivKit galleries are **RecyclerViews** (`DivRecyclerView : … : RecyclerView`). Off-screen gallery items
   are NOT in the view tree until scrolled into view. Deep content (`sun_phase`, lower settings cards) MUST be
   scrolled into view before asserting (§2 `scrollDivIntoView`). `Espresso.scrollTo()` does NOT work on a
   RecyclerView — use the provided manual-scroll helper.
5. Document load is **async** (network on IO thread → render on main thread). Every first-assertion after
   `launch()`/navigation MUST go through the **polling** waiter `waitForDivDisplayed(id)`, never a bare
   `onView(...).check(...)`.
6. Tests assert **STRUCTURE / ids / child-count / pixel-theme**, NEVER live weather values (temperature,
   condition text, city name). Live data varies run-to-run; asserting it reintroduces staleness.
7. Runner is **AndroidX Test Orchestrator with `clearPackageData=true`** (verified in `app/build.gradle`):
   every `@Test` runs in a fresh process with app data wiped. Therefore: popup shows on EVERY cold start;
   prefs reset to defaults (lang=ru, theme=system, default city) each test; `DocumentLoader.baseUrl` (a static)
   is back at `DEFAULT_BASE_URL` each test. Do NOT rely on cross-test state.
8. Backend-down must be a **loud failure**, not a silent pass on the stale asset fallback: `@Before`
   health-check (§3) fails with a clear message if `http://10.0.2.2:8080/document?lang=ru` is not reachable.

---

## §1 — VERIFIED FACT: how a div `id` reaches the view (R-32.57)

Source (local `R-32.57` checkout, commit `05f511ca3`):
`/Users/the-leo/divkit_source/divkit/client/android/div/src/main/java/com/yandex/div/core/view2/divs/DivBaseBinder.kt`

```
L92   internal fun bindId(divView: Div2View, target: View, id: String?) {
L93       val viewId = divView.viewComponent.viewIdProvider.getViewId(id)
L94       target.applyId(id, viewId)
L95   }
...
L105  private fun View.applyId(divId: String?, viewId: Int = View.NO_ID) {
L107      tag = divId          // <-- plain View.setTag(Object): the div id STRING lands on view.tag
L108      id = viewId          // <-- runtime int (see below)
L109  }
```

`bindId` is called from `DivBaseBinder.bind()` (L68) for **every** div (all type binders extend
`DivViewBinder(baseBinder)` and route through `baseBinder.bindView`; confirmed e.g.
`DivStateBinder.kt:112`, `DivCustomBinder`). So the mechanism is universal.

`viewId` provenance —
`/Users/the-leo/divkit_source/divkit/client/android/div/src/main/java/com/yandex/div/core/view2/DivViewIdProvider.kt`:
`getViewId(id)` returns `View.NO_ID` for null, else `cache.getOrPut(id) { View.generateViewId() }`.
→ a per-session runtime int, NOT an `R.id`. Confirms invariant #2: match the tag, not the id.

Other `setTag` callers in div-core use the **keyed** overload `setTag(R.id.xxx, value)`
(`DivCustomBinder.kt:78`, `DivBackgroundBinder.kt:292/299/305`, `DivActionBinder.kt:439`,
`DivLayoutProviderBinder.kt:102`) — these do NOT touch `getTag()`/the plain tag. So `view.tag` is
**exclusively** the div id string (or null). No collision.

Custom divs — `DivCustomBinder.kt` (verified): binds base on the wrapper then **overwrites the wrapper tag to
null** (`baseBinder.bindId(divView, view, null)`) and sets the div id on the INNER host view
(`baseBinder.bindId(divView, customView, div.id)`). ⇒ for a `custom` with `id="sun_phase"`, ONLY the inner
`SunPhaseView` carries `tag=="sun_phase"` — no wrapper ambiguity.

State divs — `DivStateBinder.kt:112` routes through `baseBinder.bindView`, so a `state(id="header")` view
(`DivStateLayout`) carries `tag=="header"`. (State-item ids "full"/"collapsed" are NOT div ids and are not tags.)

View classes (for helper design):
- Gallery view: `DivRecyclerView : BackHandlingRecyclerView : androidx…RecyclerView` (verified). `scrollBy` valid.
- Input view: `DivInputView : SuperLineHeightEditText : AppCompatEditText` (verified). Espresso `replaceText`/
  `typeText` valid; typing fires the `text_variable` TextWatcher → updates the bound `city_query` variable.

**Conclusion: the user's "id == view tag" is CORRECT for the plain tag. Build the matcher on `view.tag`.**

---

## §2 — FILE TO CREATE: `app/src/androidTest/java/com/example/weatherdivkit/DivIdMatchers.kt`

Purpose: the find-by-div-id Espresso toolkit. Package `com.example.weatherdivkit`.

### 2.1 Matcher (PIN this body exactly — it is the load-bearing primitive)

```kotlin
import android.view.View
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/** Matches the single view whose DivKit div id was applied as the plain view tag
 *  (DivBaseBinder.applyId: `tag = divId`, R-32.57). */
fun withDivId(divId: String): Matcher<View> = object : TypeSafeMatcher<View>() {
    override fun matchesSafely(v: View): Boolean = v.tag == divId
    override fun describeTo(d: Description) { d.appendText("view with DivKit id (view.tag == \"$divId\")") }
}
```

### 2.2 Helpers — signatures + contracts (implementer writes bodies)

All operate against the resumed activity's view root via `Espresso.onView`. Import Espresso
`ViewActions`/`ViewMatchers`/`ViewAssertions`.

- `fun assertDivDisplayed(divId: String)` — `onView(withDivId(divId)).check(matches(isDisplayed()))`.
- `fun assertDivNotDisplayed(divId: String)` — `onView(withDivId(divId)).check(matches(not(isDisplayed())))`.
  (Popup views stay in the tree when hidden — visibility GONE — so this asserts the hidden state.)
- `fun clickDivId(divId: String)` — `onView(withDivId(divId)).perform(click())`.
- `fun typeIntoDivId(divId: String, text: String)` — `onView(withDivId(divId)).perform(replaceText(text), closeSoftKeyboard())`.
  Use `replaceText` (deterministic; still fires the TextWatcher) NOT `typeText` (IME-timing flaky).
- `fun waitForDivDisplayed(divId: String, timeoutMs: Long = 10_000)` — poll every 100 ms until
  `onView(withDivId(divId)).check(matches(isDisplayed()))` succeeds; on timeout do one final unguarded
  `check(matches(isDisplayed()))` so the failure carries a real Espresso diff. (Mirror the existing
  `waitForDisplayed` polling pattern.) Needed because first render is async (invariant #5).
- `fun waitForDivGone(divId: String, timeoutMs: Long = 5_000)` — poll until not-displayed; final `assertDivNotDisplayed`.
- `fun assertDivHasChildren(divId: String, min: Int = 1)` — assert the matched view is a `ViewGroup` with
  `childCount >= min`. Implement as `onView(withDivId(divId)).check(matches(hasMinChildCount(min)))` with a
  local matcher `hasMinChildCount(min)` (TypeSafeMatcher<View>: `v is ViewGroup && v.childCount >= min`).
- `fun waitForDivChildren(divId: String, min: Int = 1, timeoutMs: Long = 10_000)` — polling variant of the above
  (city-search patch application is async).

### 2.3 Scroll-into-view helper (RecyclerView-aware) — signature + contract

```kotlin
fun scrollDivIntoView(
    scenario: ActivityScenario<MainActivity>,
    scrollDivId: String,   // the gallery/RecyclerView tag, e.g. "main_scroll" / "settings_scroll"
    targetDivId: String,   // the descendant tag to bring on-screen
    maxSteps: Int = 24,
)
```
Contract / algorithm (no `Espresso.scrollTo`; DivKit galleries are RecyclerViews, invariant #4):
1. Loop up to `maxSteps`:
   a. `scenario.onActivity`: DFS the tree under `R.id.divContainer` for a view whose `tag == targetDivId`.
      If found AND fully on screen (`getGlobalVisibleRect` covers its full width×height), set a found flag.
   b. If found → return.
   c. `scenario.onActivity`: DFS for `tag == scrollDivId`; cast to `androidx.recyclerview.widget.RecyclerView`;
      `rv.scrollBy(0, rv.height / 2)`.
   d. `SystemClock.sleep(150)` to let the bind/layout pass run.
2. On exhaustion, return silently (the caller's subsequent `waitForDivDisplayed(targetDivId)` will fail loudly).
Provide a private `findViewByTag(root: View, tag: String): View?` DFS used by this helper.
Use `com.example.weatherdivkit.R.id.divContainer` for the host (see `TestHelpers.sampleContainerTopLeft`).

---

## §3 — HEALTH CHECK + real-server acquisition (decision + spec)

**Decision (умник):** PRIMARY mechanism = point the app at the **real backend** and let it render the live
layout — i.e. leave `DocumentLoader.baseUrl == DEFAULT_BASE_URL` (`http://10.0.2.2:8080`).
Rationale: goal #1 verbatim ("take layout dumps from the real server; when the UI evolves the tests follow
automatically"). The default already IS the real backend, so no app change is needed. Weather data varies →
tests assert structure/ids only (invariant #6). Approach (a) MockWebServer and (c) fixture-regen are rejected as
the PRIMARY: (a) is not needed for structural asserts and forces live-proxying `/city-search`; (c) reintroduces a
committed fixture that goes stale — exactly what we are removing. MockWebServer IS retained for ONE narrow purpose
(request-count assertions) in `WeatherRefetchTest`, where its body is **captured live at `@Before`** (never
committed, never stale) — see §3.2.

### 3.1 Shared `@Before` health check (used by real-backend tests)

Add to `TestHelpers.kt`:
```kotlin
const val REAL_BACKEND = DocumentLoader.DEFAULT_BASE_URL   // "http://10.0.2.2:8080"

/** Fails loudly if the backend isn't serving /document — prevents a green run on the stale asset fallback. */
fun requireBackendUp() {
    val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()   // emulator DHCP proxy bypass (see DocumentLoader)
    val req = Request.Builder().url("$REAL_BACKEND/document?lang=ru").build()
    try {
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AssertionError("Backend /document returned HTTP ${r.code} — start the backend on :8080")
        }
    } catch (e: java.io.IOException) {
        throw AssertionError("Backend not reachable at $REAL_BACKEND (emulator host :8080). Start it before connectedDebugAndroidTest. Cause: ${e.message}")
    }
}

/** Fetches a live document body from the real backend for a given lang (used by the hermetic refetch test). */
fun fetchLiveDocument(lang: String): String {
    val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    val req = Request.Builder().url("$REAL_BACKEND/document?lang=$lang").build()
    client.newCall(req).execute().use { r ->
        return r.body?.string() ?: throw AssertionError("Empty /document body (lang=$lang) from real backend")
    }
}
```
`Proxy.NO_PROXY` is mandatory (matches `DocumentLoader`/`MainActivity`): the emulator DHCP sets an HTTP proxy
that breaks direct host calls.

### 3.2 Dismiss-popup-by-id helper (fresh process ⇒ popup shows every cold start)

```kotlin
fun dismissPopupIfPresent(timeoutMs: Long = 5_000)  // if withDivId("popup_close") becomes displayed within
                                                     // timeout, clickDivId("popup_close") then waitForDivGone("popup_close"); else return.
```
(Uses requested id `popup_close`; see §4.)

---

## §4 — BACKEND IDS REQUESTED (reconcile with renderer — SEPARATE OWNER / FILES)

These divs currently have NO `id=` (only `log_id`/`custom_type`), so the tests cannot find/click them by tag.
Add a single `id = "<name>"` argument to each. Prefer these EXACT names (the tests hard-code them).
`WeatherMainRenderer.kt` is being edited concurrently — treat its rows as "reconcile", keep minimal.

### Group A — `backend/.../renderer/WeatherMainRenderer.kt`  (CONTESTED — coordinate)
| id name | attach to (current code anchor) | needed by test |
|---|---|---|
| `hourly_gallery` | `hourlyGallery = gallery(...)` (~L304) | `mainScreen_galleriesAndCustom_render` |
| `sun_phase`      | `sunsetArc = custom(customType="sun_phase", …)` (~L338) | main render + pentest |
| `fab_settings`   | `fab("⚙", action(logId="fab_settings", …))` (~L462) — pass id THROUGH `fab()` to the `text(...)` | navigation |
| `fab_about`      | `fab("ℹ", action(logId="fab_about", …))` (~L463) | navigation |
| `popup_install`  | `installBtn = text(...)` (~L157) | popup / stored-values coverage |
| `popup_close`    | `closeX = text(text="×", …)` (~L133) | popup dismiss |

Already present (reuse, no change): `header` (state), `main_scroll` (gallery).

Note for `fab()` (~L691): it takes `glyph, act`; add a nullable `id: String? = null` param and forward it to the
inner `text(id = id, …)`. Two call-sites pass `fab_settings`/`fab_about`.

### Group B — `backend/.../renderer/WeatherSettingsRenderer.kt`  (not contested)
| id name | attach to (line) | needed by test |
|---|---|---|
| `city_search_input`  | `input(...)` L108 | city search |
| `city_search_button` | `text(... "Найти" ..., action = searchAction)` L124 | city search |
| `theme_btn_system`   | theme "System" `text` L156 | (optional) |
| `theme_btn_dark`     | theme "Dark" `text` L175 | theme toggle |
| `theme_btn_light`    | theme "Light" `text` L194 | theme toggle |
| `compact_btn_on`     | compact "Compact" `text` L225 | (optional) compact smoke |
| `compact_btn_off`    | compact "Normal" `text` L244 | (optional) |
| `lang_btn_ru`        | lang "Russian" `text` L274 | (optional) |
| `lang_btn_en`        | lang "English" `text` L289 | language refetch |
| `nav_home`           | Home `text` L311 | back-to-main by id |

Already present (reuse): `settings_scroll` (gallery), `city_search_results` (container).

### Group C — `backend/.../renderer/WeatherAboutRenderer.kt`  (not contested)
| id name | attach to (line) | needed by test |
|---|---|---|
| `nav_home` | Home `text` L132 (same name as Group B; screens never coexist ⇒ no ambiguity) | back-to-main from About |

Already present (reuse): `about_scroll` (gallery).

**Minimum-viable subset** (if the renderer owner wants the smallest change and the rest deferred): Group A
`fab_settings`,`fab_about`,`popup_install`,`popup_close`; Group B `city_search_input`,`city_search_button`,
`theme_btn_dark`,`theme_btn_light`,`lang_btn_en`,`nav_home`; Group C `nav_home`. The `hourly_gallery`/`sun_phase`
and the "(optional)" rows only gate the correspondingly-marked assertions/tests.

---

## §5 — TESTS TO WRITE

Global rules for every test class hitting the real backend: `@Before { requireBackendUp(); DocumentLoader.baseUrl = REAL_BACKEND }`,
`@After { scenario?.close(); DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL }`.
`launch()` = `scenario = ActivityScenario.launch(MainActivity::class.java)`.
Always `waitForDivDisplayed("main_scroll")` right after `launch()` (first render is async), then
`dismissPopupIfPresent()` before interacting with main content.

### 5.1 REWRITE `WeatherUiTest.kt` (real backend, id-based)

1. `mainScreen_coreStructure_rendersFromLiveServer` — **no backend change needed (green today).**
   launch → `waitForDivDisplayed("main_scroll")` → `dismissPopupIfPresent()` →
   `assertDivDisplayed("header")`, `assertDivDisplayed("main_scroll")`.
   (Proves: the live server layout rendered and is located purely by div id.)

2. `mainScreen_galleriesAndCustom_render` — *needs Group A `hourly_gallery`,`sun_phase`.*
   launch → wait → dismiss → `assertDivDisplayed("hourly_gallery")` →
   `scrollDivIntoView(scenario!!, "main_scroll", "sun_phase")` → `waitForDivDisplayed("sun_phase")`.

3. `navigation_settings_back` — *needs `fab_settings`, `nav_home`.*
   launch → wait → dismiss → `clickDivId("fab_settings")` → `waitForDivDisplayed("settings_scroll")` →
   `assertDivDisplayed("city_search_input")` → `androidx.test.espresso.Espresso.pressBack()` →
   `waitForDivDisplayed("main_scroll")`. Then a second leg using the button:
   `clickDivId("fab_settings")` → wait settings → `scrollDivIntoView(…, "settings_scroll", "nav_home")` →
   `clickDivId("nav_home")` → `waitForDivDisplayed("main_scroll")`.

4. `navigation_about_back` — *needs `fab_about`.*
   launch → wait → dismiss → `clickDivId("fab_about")` → `waitForDivDisplayed("about_scroll")` →
   `Espresso.pressBack()` → `waitForDivDisplayed("main_scroll")`.

5. `citySearch_populatesResults` — *needs `city_search_input`,`city_search_button` (reuses `city_search_results`).*
   launch → wait → dismiss → `clickDivId("fab_settings")` → `waitForDivDisplayed("city_search_input")` →
   assert results empty first: `assertDivHasChildren("city_search_results", 0)` is `childCount==0` — express as
   `assertDivDisplayed(... )` optional; then `typeIntoDivId("city_search_input", "Лондон")` →
   `clickDivId("city_search_button")` → `waitForDivChildren("city_search_results", min = 1)`.
   (Data-robust: any query yields ≥1 child — a hit row OR the "not found" row — proving the search round-trip +
   patch application. Do NOT assert the result text.)

6. `themeToggle_changesBackground` — *needs `theme_btn_dark`,`theme_btn_light` (reuses pixel sampler).*
   launch → wait → dismiss → `clickDivId("fab_settings")` → `waitForDivDisplayed("settings_scroll")` →
   `scrollDivIntoView(…, "settings_scroll", "theme_btn_light")` → `clickDivId("theme_btn_light")` →
   `waitForBackground(scenario!!, expectDark = false)` → `scrollDivIntoView(…, "settings_scroll", "theme_btn_dark")`
   → `clickDivId("theme_btn_dark")` → `waitForBackground(scenario!!, expectDark = true)`.
   (Reuse `waitForBackground`/`sampleContainerTopLeft`/`isDarkBackground` from `TestHelpers` — pixel-based,
   data-independent. Note the pixel sampler reads `R.id.divContainer` top-left; the settings screen bg is
   theme-driven, so sampling still works while on the settings screen.)

7. `popup_install_dismiss_persistsAcrossRecreate` — *needs `popup_install`,`popup_close`.*
   launch → `waitForDivDisplayed("main_scroll")` → `waitForDivDisplayed("popup_install")` →
   `clickDivId("popup_install")` → `waitForDivGone("popup_close")` → `scenario!!.recreate()` →
   `waitForDivDisplayed("main_scroll")` → `assertDivNotDisplayed("popup_install")`.
   (Proves the stored-value dismiss persists across Activity recreation within the same process — recreate does
   NOT wipe app data; only orchestrator's per-test `clearPackageData` does.)

8. `popup_closeX_persistsAcrossRecreate` — *needs `popup_close`.*
   launch → wait main → `waitForDivDisplayed("popup_close")` → `clickDivId("popup_close")` →
   `waitForDivGone("popup_close")` → `recreate()` → wait main → `assertDivNotDisplayed("popup_close")`.

OPTIONAL (include only if the "(optional)" Group B ids land):
9. `compactToggle_smoke` — open settings → scroll to `compact_btn_on` → click → `clickDivId("nav_home")` →
   `waitForDivDisplayed("main_scroll")` (asserts the toggle path doesn't crash and main re-renders). Do NOT try to
   assert the hidden-condition visual by id (header text has no id) — that assertion is intentionally dropped.

### 5.2 CREATE `WeatherRefetchTest.kt` (hermetic MockWebServer, live-captured bodies — request-count coverage)

Purpose: preserve the two behaviours that the real-backend suite cannot count — "language switch triggers a
refetch" and "theme toggle does NOT refetch (reactive)". Bodies are captured LIVE at `@Before` (never stale).

`@Before`:
```
val ru = fetchLiveDocument("ru"); val en = fetchLiveDocument("en")   // also acts as backend-up check
dispatcher = LangDispatcher(ru, en)                                  // kept in TestHelpers; counts requests
server = MockWebServer().apply { this.dispatcher = this@…dispatcher; start() }
DocumentLoader.baseUrl = "http://127.0.0.1:${server.port}"
```
`@After`: `scenario?.close(); server.shutdown(); DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL`.
`LangDispatcher` must serve ONLY `/document` (serve `en` when `path.contains("lang=en")`, else `ru`) and count
requests. It must NOT be hit by `/city-search` in these tests (these tests never search).

Tests (all id-based clicks; no text; needs `fab_settings`, `lang_btn_en`, `theme_btn_dark`):
1. `languageSwitch_refetches` — launch → wait main → dismiss → `clickDivId("fab_settings")` → wait settings →
   `scrollDivIntoView(…, "settings_scroll", "lang_btn_en")` → record `n = dispatcher.requestCount` →
   `clickDivId("lang_btn_en")` → poll until `dispatcher.requestCount > n` (a refetch happened) →
   `waitForDivDisplayed("settings_scroll")` → assert `dispatcher.lastPath().contains("lang=en")`.
2. `themeToggle_noRefetch` — launch → wait main (record `n = dispatcher.requestCount`) → dismiss →
   `clickDivId("fab_settings")` → wait settings → `scrollDivIntoView(…, "settings_scroll", "theme_btn_dark")` →
   `clickDivId("theme_btn_dark")` → `waitForBackground(scenario!!, expectDark = true)` →
   `assertEquals(n, dispatcher.requestCount)` (theme is a reactive div-variable — zero network).

### 5.3 REWRITE `WeatherOfflineTest.kt` (assets fallback, id-based)

Keep the value: prove the offline asset-fallback path renders. Drop the temp/condition/city string pins.
`@Before { DocumentLoader.baseUrl = "http://127.0.0.1:1" }` (dead address → forces `loadFromAssets()`),
`@After` reset. Do NOT call `requireBackendUp()` here.
`offline_fallsBackToAssets`: launch → `waitForDivDisplayed("main_scroll")` → `dismissPopupIfPresent()` →
`assertDivDisplayed("header")`, `assertDivDisplayed("main_scroll")`.
(Verified: the committed `app/src/main/assets/document.json` contains ids `header`,`main_scroll`,`settings_scroll`,
`about_scroll`,`city_search_results` — so id asserts hold on the asset too and survive asset regeneration.)

### 5.4 KEEP `RegistrationSmokeTest.kt` (essentially unchanged)

It does not use the stale fixtures — it binds an inline `SMOKE_DIV_JSON` and asserts native registration
(Coil / `SunPhaseCustomViewAdapter` / `ScrollStateExtensionHandler`) by finding `SunPhaseView` by CLASS in a
manually-built, detached `Div2View`. `withDivId` cannot see it (the tree is added+removed inside one
`onActivity`, never visible to Espresso), so the class-based check remains correct. Leave as-is.
OPTIONAL demonstration: add `"id": "sun_phase"` to the custom node in `SMOKE_DIV_JSON` and additionally assert
`findViewByTag(divView, "sun_phase") is SunPhaseView` inside the same `onActivity` (unit-level proof of the tag
mechanism, independent of Espresso). Mark optional; do not let it change the test's primary intent.

---

## §6 — `TestHelpers.kt` edits

REMOVE (now dead / text-based / stale-fixture):
- All PINNED string constants (`POPUP_*`, `MAIN_TITLE_*`, `TEMP_*`, `COND_*`, `NAV_*`, `SET_*`, `THEME_*`,
  `COMPACT_*`, `LANG_*`, `ABOUT_*`).
- Text-based helpers: `isDisplayedNow`, `waitForDisplayed(text)` (both overloads), `clickText`,
  `assertNotVisible(text)` (both), `waitUntilGone(text)`, `dismissWidgetPopupIfPresent`.
- `readTestAsset` (only used to load the deleted fixtures).

KEEP (still used):
- `waitForBackground`, `sampleContainerTopLeft`, `isDarkBackground` (pixel theme checks).
- `LangDispatcher` (used by `WeatherRefetchTest`; bodies now live-captured, not fixture-loaded).

ADD: `REAL_BACKEND`, `requireBackendUp()`, `fetchLiveDocument(lang)` (§3.1). New imports:
`okhttp3.OkHttpClient`, `okhttp3.Request`, `java.net.Proxy`.

---

## §7 — FILES TO DELETE

- `app/src/androidTest/assets/document_ru.json`
- `app/src/androidTest/assets/document_en.json`
(Stale hand-frozen dumps; replaced by live capture in `WeatherRefetchTest` and by the real backend elsewhere. The
`app/src/androidTest/assets/` dir may then be empty — that is fine.)

---

## §8 — GRADLE / DEPENDENCIES

No new dependencies. Verified present in `app/build.gradle`: `espresso-core` (matcher/actions/`Espresso.pressBack`),
`androidx.test.runner`, `okhttp-mockwebserver`, `androidx.test.orchestrator` (androidTestUtil) with
`clearPackageData=true` + `ANDROIDX_TEST_ORCHESTRATOR`, `androidx.recyclerview` (implementation → `RecyclerView`
type available to test code). Do NOT add `espresso-contrib` — the manual `scrollDivIntoView` avoids
`RecyclerViewActions`. Hamcrest (`TypeSafeMatcher`, `Matchers.not`) is transitively provided by espresso.

---

## §9 — ACCEPTANCE CRITERIA

1. `./gradlew connectedDebugAndroidTest` (or `-Pandroid.testInstrumentationRunnerArguments.…` targeting
   `emulator-5556`) is GREEN with the backend live on `:8080`, AFTER the §4 backend ids land.
2. With the backend DOWN, real-backend tests FAIL with the `requireBackendUp()` message (no silent green).
3. No test asserts any live weather value (temperature/condition/city) — grep the new test sources for `°`, and
   for Cyrillic/EN condition literals ⇒ none in assertions (search input query "Лондон" is an INPUT, allowed).
4. Every element the tests locate is found via `withDivId(...)` (view.tag) — no `withText(...)`/`withId(...)` in
   the new suite except: the search INPUT payload, and (if the renderer owner rejects `fab_*`) an explicitly-noted
   glyph fallback (see ORCHESTRATOR NOTES).
5. `app/src/androidTest/assets/document_{ru,en}.json` are deleted; no test references them.
6. Build not broken: `./gradlew assembleDebug` and `assembleDebugAndroidTest` compile.

---

## §10 — RISKS / EDGES / WHAT TO ASK

- **R1 (sequencing):** tests 2–9 depend on §4 backend ids that live in files owned elsewhere (Group A is
  actively edited). Test 1 + offline + registration are green with ZERO backend change. IMPLEMENTER: implement
  everything; if the Group A ids have not landed at run time, tests 2/3/4/7/8 will red — this is expected until
  the orchestrator reconciles the ids. Do NOT weaken them to text to force green; report the blocked ids instead.
- **R2 (settings viewport):** whether theme/compact/lang cards are on-screen without scrolling depends on device
  height. `scrollDivIntoView` is applied unconditionally before deep-settings clicks; it is a no-op when the
  target is already fully visible. Do not skip it.
- **R3 (city-search geocoder):** if the backend geocoder returns 0 hits, `city_search_results` still gets the
  "not found" child ⇒ `waitForDivChildren(min=1)` still passes. The test intentionally asserts round-trip, not hit
  quality. If it must assert a real hit, ASK the orchestrator for a guaranteed-resolvable query.
- **R4 (input variable binding):** `replaceText` must fire the `text_variable` watcher so `@{city_query}`
  resolves to the typed value at click time. If the search fires with an empty `q` (patch returns default),
  fall back to `typeText` + `closeSoftKeyboard`, and if still failing, ASK — do not hard-code `q`.
- **R5 (ambiguity):** `withDivId` throws Espresso `AmbiguousViewMatcher` if two attached views share a tag. Our
  documents use unique ids and only one screen/Div2View is attached at a time, so this should not occur; if it
  does, it signals a duplicate `id=` in the backend — report it, do not disambiguate with position.
- **R6 (pixel sampler on settings):** `waitForBackground` samples `R.id.divContainer` top-left; ensure the sample
  is taken while the settings screen (theme-driven bg) is shown. It is, in tests 6 / refetch-2.

---

## §11 — BOUNDARIES / WHAT DEFERS

- IN SCOPE: everything under `app/src/androidTest/**` + the §4 id request list.
- OUT OF SCOPE (for the implementer of THIS contract): editing any `backend/**` file — the §4 ids are a request
  the ORCHESTRATOR lands (coordinating with the concurrent `WeatherMainRenderer.kt` editor). Do NOT edit
  `WeatherMainRenderer.kt`.
- Deferred / intentionally dropped coverage (record as techdebt): exact-request-count assertions on the real
  backend (kept only in the hermetic `WeatherRefetchTest`); the compact-mode "condition hidden" visual assertion
  (no id on header text); deep language-text verification (labels are localized text — asserting them
  reintroduces staleness). Add a header condition/text id later ONLY if that coverage is deemed necessary.

---

## ORCHESTRATOR NOTES (implementer may skip)

- Fork taken: PRIMARY = app→real backend (default baseUrl); MockWebServer retained ONLY for request-count
  behaviours with live-captured bodies. This maximises fidelity to goal #1 while preserving the reactive/refetch
  coverage the real backend can't count.
- Fork taken: backend-down ⇒ hard FAIL (AssertionError) not JUnit `assumeTrue` skip — a skipped suite can mask a
  dead backend in CI; the user asked for "skip/fail clearly", and clear-fail is the safer default here. Flip to
  `Assume.assumeTrue` if you prefer CI to treat a down backend as "not run".
- `fab_*` fallback: if the renderer owner rejects `fab_settings`/`fab_about` ids, the FABs can be clicked by their
  language-independent glyph via `onView(withText("⚙"))` / `withText("ℹ")` — the only sanctioned text exception
  (stable symbols, not localized). Prefer the ids.
- Requested-id budget is deliberately front-loaded onto the NON-contested settings/about files; only 6 ids touch
  the contested `WeatherMainRenderer.kt`, and 2 of those (`hourly_gallery`,`sun_phase`) gate only one enrichment
  test. See the "Minimum-viable subset" in §4 if the owner wants the smallest possible main-renderer diff.
- This stage is mostly test infra over already-working app/backend behaviour; the plan→implement→review ceremony
  is warranted here because the find-by-id mechanism and RecyclerView scrolling are subtle (invariants #1–#4).
