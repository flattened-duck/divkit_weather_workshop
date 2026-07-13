# STAGE 1 — WORKTREE B CONTRACT: Settings + About redesign + city-search UI

Implementer: kopatel (Sonnet). Follow literally. You never commit.
Ground truth for every divan/DivKit fact: `/Users/the-leo/divkit_source/divkit` (branch `R-32.57`
== released `32.57.0`). Do NOT consult arcadia or memory. All source paths below are verified.

## FILE OWNERSHIP — do not stray
MODIFY exactly these:
- `backend/src/main/kotlin/workshop/renderer/WeatherSettingsRenderer.kt`
- `backend/src/main/kotlin/workshop/renderer/WeatherAboutRenderer.kt`
- ONLY the `citySearch(...)` method body inside `backend/src/main/kotlin/workshop/servant/WeatherServant.kt`
  (do NOT touch `handle`, `weatherJson`, ctor, mapper, `localizer`, `urlEncode`).
- `backend/src/main/resources/strings/strings_ru.json` and `strings_en.json` — APPEND 2 keys each in a
  clearly delimited block (see §D2). Worktree A also appends here → keep your block separate & minimal.

Do NOT touch: proto, `Application.kt`, build files, `gradle/libs.versions.toml`, `SharedTemplates.kt`,
`WeatherMainRenderer.kt`, the smoke test, or anything under `app/**`.

---

## MUST-NOT-GET-WRONG (load-bearing — read first)

1. Container id for the DivPatch target is EXACTLY `city_search_results`. Input text-variable is EXACTLY
   `city_query`. Actions are EXACTLY `weather-app://city_search?q=@{city_query}` and (in the servant)
   `weather-app://set_city?lat=&lon=&name=`. These are the cross-worktree contract — verbatim, no renames.
2. Keep theme reactivity as PLAIN variable expressions `@{theme == 'dark' ? … : …}`. Do NOT introduce
   `getStoredStringValue` anywhere in these renderers — a smoke test asserts `/document` contains
   `@{theme == 'dark'` AND does NOT contain `getStoredStringValue`.
3. Preserve EVERY existing control, its `logId`, its action `url`, and its reactive expression verbatim
   (theme_mode/compact/lang buttons, nav back/home, About GitHub link + version). You RESTYLE only —
   never delete functionality, never change a `logId` or a `url` string.
4. Your `citySearch` restyle must KEEP these substrings present in the response: `changes`,
   `city_search_results`, `weather-app://set_city?`, the city name (`Moscow` for `q=Mos&lang=en`), and
   the `city.search.empty` localized string for the empty case. Only styling params (colors, paddings,
   border, background, sizes) may change; the patch shape, target id, action url, and text stay.
5. The results container starts EMPTY (`items = emptyList()`) — the DivPatch from `/city-search`
   (Worktree C applies it) replaces its children. It must still exist in the tree with the id above.
6. `city_query` is declared as a LOCAL DivData variable of the settings card (initial `""`), NOT global.
7. Do NOT edit the smoke test. `cd backend && ./gradlew clean test` must stay green unchanged.

---

## A. DESIGN SYSTEM (умник-decisions — pin these constants, reuse everywhere)

Declare these as `val` expression strings / colors at the top of each `render()` (same style the files
already use). Reuse across both renderers.

```
screenBgExpr   = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"   // screen background (already used)
surfaceExpr    = "@{theme == 'dark' ? '#FF2C2C2E' : '#FFFFFFFF'}"   // card surface
primaryTextExpr= "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"   // titles / body text
inputFieldExpr = "@{theme == 'dark' ? '#FF3A3A3C' : '#FFF2F2F7'}"   // input field fill
secondaryText  = color("#FF8E8E93")   // captions/hints — theme-neutral gray, static
accentBlue     = color("#FF007AFF")   // primary accent (buttons/links)
```

Typography scale (fontSize): screen title `28` bold · section header `17` bold · body/control `16` ·
caption/version `13`. Spacing: card `paddings = edgeInsets(16,16,16,16)`; between cards
`margins = edgeInsets(bottom = 12)`; corner radius `16` for cards, `10` for controls/inputs.

**Card-surface wrapper rule** — group each logical section into ONE container styled like this
(theme-aware surface, rounded, padded), and put the section's existing controls inside it:
```
container(
    orientation = vertical,
    width = matchParentSize(),
    margins = edgeInsets(bottom = 12),
    paddings = edgeInsets(start = 16, top = 16, end = 16, bottom = 16),
    background = listOf(solidBackground(color("#FFFFFFFF")).evaluate(color = expression<Color>(surfaceExpr))),
    border = border(cornerRadius = 16),
    items = listOf( /* section header text + existing controls, restyled */ ),
)
```
Section header = `text(fontSize=17, fontWeight=bold, textColor=color("#FF1C1C1E"), margins=edgeInsets(bottom=8))
.evaluate(textColor = expression<Color>(primaryTextExpr))`.

The screen root stays a vertical `container` with `width=matchParentSize()`, `paddings=edgeInsets(16,16,16,16)`,
`background = listOf(solidBackground(...).evaluate(color = expression<Color>(screenBgExpr)))`. The screen
title (`settings.title` / `about.title`, fontSize 28 bold) keeps its existing `.evaluate(textColor=…)`.

You may add a private helper `fun DivScope.card(header: String, items: List<Div>): Container` to reduce
repetition — your choice; behaviour is what matters.

---

## B. IMPLEMENTER SPEC

### B1. WeatherSettingsRenderer.kt — sections top→bottom
Render the screen root (theme-aware bg + padding) containing, in order:
1. Screen title `settings.title` (28 bold, theme-aware textColor) — keep existing.
2. **NEW city-search card** (§B2) — placed FIRST after the title.
3. Theme card — wrap the existing 3 theme buttons (System/Dark/Light, logIds `set_theme_system/dark/light`,
   urls `weather-app://set_theme?mode=…`, and their `@{theme_mode == …}` reactive backgrounds) in a card
   with header `settings.theme.label`. Controls unchanged; you may keep their pill styling.
4. Compact card — existing 2 buttons (`set_compact_on/off`, `weather-app://set_compact?value=…`,
   `@{compact ? …}` backgrounds), header `settings.compact.label`.
5. Language card — existing 2 buttons (`set_lang_ru/en`, `weather-app://set_lang?value=…`), header
   `settings.lang.label`.
6. Navigation row — existing `back` (`weather-app://back`, logId `nav_back`) and `nav.main`
   (`weather-app://navigate?screen=main`, logId `nav_main`). Keep as a horizontal row (no card needed).

The settings `data(...)` call MUST gain two params (keep `logId = "main_settings"`, keep `div = <root>`):
```
variables = listOf(stringVariable(name = "city_query", value = "")),
variableTriggers = listOf(
    trigger(actions = listOf(searchAction), mode = on_variable)
        .evaluate(condition = expression<Boolean>("@{city_query != ''}")),
),
```
where `searchAction = action(logId = "city_search", url = url("weather-app://city_search?q=@{city_query}"))`
(define it once, above the tree; reuse it in the trigger, in the input `enterKeyActions`, and on the button).

### B2. City-search card contents (NEW — build exactly this; it is the cross-worktree contract)
Inside a card-surface container (§A) with header text `settings.city.label` (new key §D2), put, in order:

(a) The input:
```
input(
    width = matchParentSize(),
    height = fixedSize(44),
    textVariable = "city_query",
    hintText = localizer.getOrDefault("city.search.placeholder", "Поиск города"),
    hintColor = color("#FF8E8E93"),
    textColor = color("#FF1C1C1E"),
    fontSize = 16,
    keyboardType = single_line_text,          // optional; import divkit.dsl.single_line_text
    paddings = edgeInsets(start = 12, top = 10, end = 12, bottom = 10),
    background = listOf(solidBackground(color("#FFF2F2F7")).evaluate(color = expression<Color>(inputFieldExpr))),
    border = border(cornerRadius = 10),
    enterKeyActions = listOf(searchAction),
).evaluate(textColor = expression<Color>(primaryTextExpr))
```

(b) The visible search button (robust fallback firing the SAME action):
```
text(
    width = matchParentSize(),
    text = localizer.getOrDefault("city.search.button", "Найти"),
    fontSize = 16, fontWeight = bold, textAlignmentHorizontal = center,
    textColor = color("#FFFFFFFF"),
    background = listOf(solidBackground(color("#FF007AFF"))),
    border = border(cornerRadius = 10),
    paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
    margins = edgeInsets(top = 8),
    action = searchAction,
)
```

(c) The results container (DivPatch target — starts empty):
```
container(
    id = "city_search_results",
    orientation = vertical,
    width = matchParentSize(),
    margins = edgeInsets(top = 8),
    items = emptyList(),
)
```

### B3. WeatherAboutRenderer.kt
Render the screen root (theme-aware bg + padding) containing:
1. Screen title `about.title` (28 bold, theme-aware) — keep existing `.evaluate`.
2. Info card (card-surface §A, no header or header = app name): app name `"DivKit Weather Workshop"`
   (20 bold, `.evaluate(textColor = primaryTextExpr)`), and version `about.version` (13, `secondaryText`).
3. GitHub link — keep existing `text` control: logId `open_github`, url `https://github.com/divkit/divkit`,
   text `about.repo`. Restyle to a full-width accent button (blue bg `#FF007AFF`, white text, radius 10,
   `width = matchParentSize()`, `textAlignmentHorizontal = center`) OR keep the tinted style — your call,
   but the logId + url + text key are unchanged.
4. Navigation row — existing `back` (`nav_back`, `weather-app://back`) and `nav.main` (`nav_main`,
   `weather-app://navigate?screen=main`). Keep horizontal row.

### B4. WeatherServant.citySearch(...) — restyle the patch rows ONLY
Keep the method signature, the `divanPatch { … patch(changes = listOf(patchChange(id = "city_search_results",
items = items))) }` structure, the empty-vs-hits branching, the `set_city` url (`"weather-app://set_city?lat=
${hit.lat}&lon=${hit.lon}&name=${urlEncode(hit.name)}"`), `logId = "set_city"`, and `city.search.empty`
fallback text — all VERBATIM. Only enrich the `text(...)` styling so rows read as tappable list cells:

- Each HIT row `text(...)`: `text = hit.name`, `action = <existing set_city action>`, PLUS
  `width = matchParentSize()`, `fontSize = 16`, `textColor = color("#FF007AFF")` (accent — signals tappable),
  `paddings = edgeInsets(start = 14, top = 12, end = 14, bottom = 12)`,
  `margins = edgeInsets(top = 6)`, `border = border(cornerRadius = 10)`,
  `background = listOf(solidBackground(color("#FFFFFFFF")).evaluate(color = expression<Color>(rowSurfaceExpr)))`.
- The EMPTY-state row `text(...)`: keep `text = localizer.getOrDefault("city.search.empty", "Ничего не найдено")`,
  add `width = matchParentSize()`, `fontSize = 15`, `textColor = color("#FF8E8E93")`,
  `textAlignmentHorizontal = center`, `paddings = edgeInsets(start = 14, top = 12, end = 14, bottom = 12)`.

Use `rowSurfaceExpr = "@{theme == 'dark' ? '#FF2C2C2E' : '#FFFFFFFF'}"`. NEW imports to add to WeatherServant.kt:
`divkit.dsl.Color`, `divkit.dsl.border`, `divkit.dsl.center`, `divkit.dsl.color`, `divkit.dsl.container`
(only if you wrap — you do NOT need to wrap; rows stay flat inside the patch), `divkit.dsl.core.expression`,
`divkit.dsl.evaluate`, `divkit.dsl.matchParentSize`, `divkit.dsl.solidBackground`, `divkit.dsl.edgeInsets`,
`divkit.dsl.vertical` (only if used). Add only the ones you actually reference; unused imports fail lint if
the build treats warnings as errors — check by compiling. `text(...)`/`action(...)`/`url(...)`/
`wrapContentSize`/`divanPatch`/`patch`/`patchChange`/`URLEncoder` imports already exist.

NOTE: the `text(...)` rows are built inside the `divanPatch { … }` lambda (receiver = DivScope), so
`.evaluate(...)`, `solidBackground(...)`, `expression<Color>(...)` all resolve there — same as elsewhere.

---

## C. NEW IMPORTS for the renderers
WeatherSettingsRenderer.kt — ADD: `divkit.dsl.input`, `divkit.dsl.stringVariable`, `divkit.dsl.trigger`,
`divkit.dsl.on_variable`, `divkit.dsl.fixedSize`, `divkit.dsl.single_line_text` (if you set keyboardType),
`divkit.dsl.Div` (only if you write a `card(...)` helper returning/taking `List<Div>`). Existing imports
(`action, bold, border, center, color, container, core.expression, data, divan, edgeInsets, evaluate,
horizontal, matchParentSize, solidBackground, text, url, vertical, wrapContentSize, Color, Divan`) stay.
WeatherAboutRenderer.kt — no new element imports required (reuses existing set); add `Div` only if you
add a `card` helper.

---

## D. PINNED LITERALS (single block)

### D1. Contract strings (verbatim — cross-worktree)
```
input text_variable      : city_query
results container id      : city_search_results
search action url         : weather-app://city_search?q=@{city_query}
search action logId       : city_search
set_city action url       : weather-app://set_city?lat={lat}&lon={lon}&name={urlEncodedName}   (unchanged, in servant)
set_city action logId     : set_city                                                            (unchanged)
trigger mode              : on_variable   ;  trigger condition: @{city_query != ''}
```

### D2. NEW string keys — APPEND to strings_{ru,en}.json in a delimited block
Put a marker comment is not possible in JSON; instead append these AT THE END, and LIST them here so the
orchestrator reconciles with Worktree A. Exactly TWO new keys:
```
settings.city.label   :  Город   |  City
city.search.button    :  Найти   |  Search
```
Reuse existing keys `city.search.placeholder` (input hint) and `city.search.empty` (empty state) — do NOT
duplicate them. (If, at merge, Worktree A already added either of your two keys with a different value,
flag to the orchestrator; otherwise keep yours.)

### D3. Expected `/city-search?q=Mos&lang=en` shape after restyle (assert on substrings, not bytes)
```json
{"changes":[{"id":"city_search_results","items":[
  {"type":"text","text":"Moscow","width":{"type":"match_parent"}, … styling …,
   "action":{"log_id":"set_city","url":"weather-app://set_city?lat=55.7558&lon=37.6173&name=Moscow"}}
]}]}
```

---

## E. VERIFIED DIVKIT API FACTS (cite if a signature seems off)
- `input()` — `json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/Input.kt:556`. Params used:
  `textVariable: String?`, `hintText: String?`, `hintColor: Color?`, `textColor: Color?`, `fontSize: Int?`,
  `keyboardType: Input.KeyboardType?`, `enterKeyActions: List<Action>?`, `background`, `border`, `paddings`,
  `width`, `height`, `variableTriggers`. `Input.evaluate(textColor/hintColor/highlightColor/… : ExpressionProperty<…>)`
  supports theme-reactive `textColor`/`hintColor` (Input.kt, `fun Input.evaluate`). `background` is NOT in
  `evaluate` → make it theme-aware via `solidBackground(...).evaluate(color = expression<Color>(...))`.
- Trigger — `divan-dsl/src/generated/kotlin/divkit/dsl/Trigger.kt`. `fun DivScope.trigger(actions: List<Action>?,
  condition: Boolean?, mode: Trigger.Mode?)` (:85); `fun Trigger.evaluate(condition: ExpressionProperty<Boolean>?,
  mode: ExpressionProperty<Trigger.Mode>?)` (:194). Mode instance `on_variable` = extension val on DivScope,
  `EnumValues.kt:1118` (`val DivScope.on_variable`), serialized `"on_variable"`. Semantics (doc): on_variable
  fires when the condition is met AND the watched variable changes → fires on each `city_query` change while
  non-empty. `condition` is a required trigger field; supplied via `.evaluate(condition = expression<Boolean>(…))`.
- `data(logId, variables: List<Variable>?, variableTriggers: List<Trigger>?, div)` — `Data.kt:137`
  (variableTriggers → `variable_triggers`). `stringVariable(name, value)` — `StringVariable.kt:71`.
- `container(id: String?, items: List<Div>, …)` — primary `Container.kt:709` (`items` non-null → pass
  `emptyList()` for the empty results box). id serializes to `"id"`.
- Action `url` is `Expression<Uri>`; `url(s: String)` (`core/src/main/kotlin/divkit/dsl/Url.kt`) wraps the
  string VERBATIM (JsonValue) — so `@{city_query}` is emitted literally and resolved at runtime by the client.
- DivPatch builders (`divanPatch`/`patch`/`patchChange`, `DivScope` receiver, build items inside the lambda)
  — already used correctly in `citySearch`; unchanged here.

---

## F. ACCEPTANCE / TEST PLAN
1. `cd /Users/the-leo/divkit-weather-workshop/backend && ./gradlew clean test` → GREEN (do not edit tests).
   The city-search + `@{theme == 'dark'` + no-`getStoredStringValue` assertions still pass.
2. `./gradlew run` (separate shell), then:
   - `curl -s 'http://localhost:8080/city-search?q=Mos&lang=en'` → contains `changes`,
     `city_search_results`, `weather-app://set_city?lat=55.7558&lon=37.6173&name=Moscow`, `Moscow`.
   - `curl -s 'http://localhost:8080/city-search?q=&lang=ru'` → contains `city_search_results` and
     `Ничего не найдено`.
   - `curl -s 'http://localhost:8080/document?lang=ru' | grep -o 'city_search_results'` → prints the id
     (settings screen now embeds the results container).
   - `curl -s 'http://localhost:8080/document?lang=ru'` → contains `city_query`,
     `weather-app://city_search?q=@{city_query}`, `"on_variable"`, `@{theme == 'dark'`; does NOT contain
     `getStoredStringValue`.
3. (Cannot render server-side.) Visual verification of the redesigned screens defers to Stage 2 e2e on the
   emulator (Worktree C wires the client). Confirm the JSON is well-formed: pipe `/document` through
   `python3 -m json.tool`.

---

## CONTRACT FOR WORKTREE C (client handler — implement in parallel)
- **Query variable:** `city_query` (String), declared LOCAL to the settings DivData card (initial `""`),
  bound to the input's `text_variable`. C does NOT declare it globally and does NOT read it directly — it
  arrives already substituted inside the action URL.
- **`city_search` action:** URL `weather-app://city_search?q=@{city_query}`. Fires on: (a) each keystroke
  that leaves `city_query` non-empty (DivData `variable_triggers`, mode `on_variable`); (b) the keyboard
  enter/search key (`enterKeyActions`); (c) tapping the visible "Search" button. All three dispatch the
  SAME url. C's handler must intercept `weather-app://city_search`, read `q` (see caveat), call backend
  `GET /city-search?q=<q>&lang=<current lang>`, and apply the returned DivPatch via
  `div2View.applyPatch(...)`. ⚠️ CAVEAT: DivKit substitutes `@{city_query}` into the url VERBATIM (not
  percent-encoded) — so for "New York" the url contains a literal space. Parse with `Uri.parse(url)
  .getQueryParameter("q")` (handles it) and re-encode when building the backend request; do not assume the
  incoming `q` is already percent-encoded. C may debounce keystroke fires if desired (optional).
- **DivPatch target:** container id `city_search_results` in the settings card — exists, initially empty;
  `applyPatch` replaces its children with the backend rows.
- **`set_city` action:** URL `weather-app://set_city?lat=<double>&lon=<double>&name=<urlEncoded>` (emitted by
  the backend rows). On tap C persists lat/lon/name and refetches `GET /document?lang=&lat=&lon=&name=`
  (mirror the existing `set_lang` refetch flow).

---

## ORCHESTRATOR NOTES (implementer may skip)
- **Change-trigger mechanism decision:** primary = DivData `variable_triggers` with `mode = on_variable`,
  `condition = @{city_query != ''}` (verified present in R-32.57: Trigger.kt / EnumValues.kt:1118). This is
  a real 32.57 API, not a guess. The keyboard `enterKeyActions` + the visible "Search" button are included
  as the robust fallbacks the task requested — all three fire the identical `city_search` url, so the flow
  works even if a device debounces or drops the on-change trigger.
- **`city_query` scoping:** chose LOCAL DivData variable (not global) because nothing outside the settings
  card needs it and the value reaches C pre-substituted in the url. Keeps Worktree B self-contained; C does
  not have to declare a global. If the orchestrator later wants the search box on other screens, promote it
  to a global variable — not needed now.
- **New string keys:** only 2 (`settings.city.label`, `city.search.button`). Reuses the Stage-0
  `city.search.placeholder`/`city.search.empty`. Both files also get appended by Worktree A → potential
  merge overlap; keys are namespaced and unlikely to collide. Listed in §D2 for reconciliation.
- **No smoke-test change needed:** the restyle preserves every asserted substring (§MUST-NOT-GET-WRONG 4)
  and the theme-expression invariants. If the build unexpectedly reddens on an unused-import warning-as-error,
  trim imports (§B4) — do not add functional changes to chase it.
- **Verify-at-runtime item for C (not B):** expression resolution of `@{city_query}` inside the action url
  and the non-percent-encoded `q` caveat above — B emits the literal correctly; C owns runtime parsing.
</content>
</invoke>
