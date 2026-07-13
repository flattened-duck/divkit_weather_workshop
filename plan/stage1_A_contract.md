# STAGE 1 — WORKTREE A CONTRACT — Main screen redesign

Implementer: kopatel (Sonnet). Follow literally. You never commit.

**File ownership (do NOT stray):** you may only CREATE/MODIFY
`backend/src/main/kotlin/workshop/renderer/WeatherMainRenderer.kt`,
`backend/src/main/kotlin/workshop/templates/**` (SharedTemplates.kt + any new template file).
You may NOT edit `strings_*.json` (this stage needs **zero** new keys — see §7),
`WeatherServant`, `Application`, `WeatherSettingsRenderer`, `WeatherAboutRenderer`, proto, build
files, `app/**`, or the test file. Test edits are an ORCHESTRATOR action (see §8).

Ground truth for every divan/DivKit API fact: `/Users/the-leo/divkit_source/divkit`
(branch `R-32.57` == released `32.57.0`). Every builder used below is verified there; source
paths are cited where non-obvious.

---

## MUST-NOT-GET-WRONG (load-bearing — read first)

1. **Output stays a valid card.** `WeatherMainRenderer.render()` still returns `"main" to divan{…}`
   and the `data(logId = "main_weather", …)` envelope is preserved.
2. **KEEP the popup + all its stored-value machinery verbatim** (actions, `widget_set_up`,
   `widget_popup_delayed`, `popup_dismissed`, `lifetime=259200`, the `getStoredBooleanValue` reads,
   the `overlap` root). Smoke test `main screen contains stored-values widget popup` asserts all of
   it. You only ADD an `image` above the popup text (§6).
3. **Header collapse is driven ONLY by the boolean div-variable `header_collapsed`.** You READ it via
   a `default_state_id` expression on a `state` element. You DECLARE it (default `false`) in
   `data(variables=…)`. You never write it — Worktree C writes it natively.
4. **The `scroll_state` extension goes on the OUTER vertical scroll gallery** (`id="main_scroll"`),
   NOT on the horizontal hourly gallery. Rationale + C contract in §5 and §9.
5. **Background image url is built server-side from `current.bg_key`** — a plain string, no
   expression: `…/S3/background_{bg_key}.png` (§ literal block).
6. **Theme reactivity uses `@{theme == 'dark' ? … : …}` expressions only.** Never `getStoredStringValue`
   (smoke test `reactive theme variable expression is present` asserts `@{theme == 'dark'` present and
   `getStoredStringValue` absent).
7. **Popup `image.preview` is the pinned base64 data-url literal** in the literal block — paste it
   exactly. Do not regenerate unless told.
8. **Two pre-existing smoke assertions WILL fail** (they assert the removed legacy today/tomorrow
   cards). Do NOT edit the test file (out of scope). Report them as EXPECTED failures per §8.

---

## 1. Proto accessors you will read (protobuf-java camelCase)

`weatherData.current` → `.city:String`, `.tempC:Int`, `.feelsC:Int`, `.condition:ConditionCode`,
`.uvIndex:Int`, `.humidity:Int`, `.pressure:Int`, `.visibility:Int`(meters), `.wind:Int`,
`.sunrise:String`("HH:mm"), `.sunset:String`, `.bgKey:String`.
`weatherData.dailyList: List<DailyPoint>` — each `.weekday:String`, `.tempMin:Int`, `.tempMax:Int`,
`.condition:ConditionCode`, `.precipProb:Int`.
`weatherData.hourlyList: List<HourlyPoint>` — each `.time:String`, `.tempC:Int`, `.condition:ConditionCode`.
`ConditionCode` enum from `workshop.proto.WeatherDataOuterClass.ConditionCode`; `.name` ∈
`{CLEAR,CLOUDY,RAIN,SNOW,THUNDER,FOG}` (matches `condition.*` string keys).

Legacy `weatherData.today`/`weatherData.tomorrow` are NO LONGER RENDERED (removed from the card).
They still exist in the proto — just stop referencing them.

---

## 2. Root card structure (imperative)

Rebuild `buildCard` so `data(...)` renders this tree. Root is an `overlap` container (4 layers,
bottom-to-top). `data` declares BOTH variables.

```
data(
  logId = "main_weather",
  variables = listOf(
    booleanVariable(name = "popup_dismissed",  value = false),   // keep
    booleanVariable(name = "header_collapsed", value = false),   // NEW — C writes it
  ),
  div = container(orientation = overlap, width = matchParentSize(), height = matchParentSize(),
    items = listOf(
      backgroundImage,   // L0  full-screen weather bg
      mainColumn,        // L1  [ headerState ][ scrollBody ]
      fabRow,            // L2  bottom overlay FAB row
      popupOverlay,      // L3  existing popup (now with image) — UNCHANGED except the added image
    )))
```

`popupOverlay` = exactly the current popup subtree, with `popupImage` inserted as the first child of
`popupCard.items` ABOVE `title` (see §6). Keep everything else (visExpr, all actions, `overlap`,
`#99000000` scrim) byte-for-byte.

---

## 3. Layer 0 — background image

```
val bgUrl = "https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_${weatherData.current.bgKey}.png"
backgroundImage = image(
  imageUrl = url(bgUrl),
  width = matchParentSize(), height = matchParentSize(),
  scale = fill,                 // ImageScale.fill = cover/crop
)
```
No expression, no preview. `bg_key` is already one of the 10 legal values (server-defaulted to fog).
Verified: `image(imageUrl:Url?, scale:ImageScale?, …)` — `Image.kt:462`; `scale=fill` accessor
`EnumValues.kt:954`.

---

## 4. Layer 1 — mainColumn = pinned header + scroll body

```
mainColumn = container(orientation = vertical, width = matchParentSize(), height = matchParentSize(),
  items = listOf(headerState, scrollBody))
```

### 4a. headerState (the collapsible header) — CORE of task 3

A `state` element with two states switched reactively by `header_collapsed`, animated.

```
headerState = state(
    id = "header",
    defaultStateId = "full",
    transitionChange = changeBoundsTransition(duration = 250),   // animate height shrink
    states = listOf(
        stateItem(stateId = "full",      div = fullHeader),
        stateItem(stateId = "collapsed", div = compactHeader),
    ),
    width = matchParentSize(),
  ).evaluate(
    defaultStateId = expression<String>("@{header_collapsed ? 'collapsed' : 'full'}")
  )
```

- `state(...)` — `State.kt:474`; `.evaluate(defaultStateId = ExpressionProperty<String>)` —
  `State.kt:1374/1382`; `stateItem(...)` — `State.kt:2014`; `changeBoundsTransition(duration,…)` —
  `ChangeBoundsTransition.kt:81`.
- Reactivity + animation verified in client `DivStateBinder.kt` (lines ~100–205): on state change it
  reads `getDefaultState(resolver)` and runs `transitionChange`/`transition_in`/`transition_out`.
- **Both state divs are theme-aware** via `titleColorExpr` (below). Give each state div its OWN
  appearance transition so the swap fades:
  - `fullHeader` and `compactHeader` each pass `transitionIn = fadeTransition(duration = 250)` and
    `transitionOut = fadeTransition(duration = 250)` on their root container.
    (`fadeTransition(alpha,duration,interpolator,startDelay)` — `FadeTransition.kt:89`; container
    accepts `transitionIn/transitionOut` — `Container.kt:583–584`.)

Theme color expr (reuse existing pattern): `titleColorExpr = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"`
Secondary color expr: `subColorExpr = "@{theme == 'dark' ? '#FF9E9EA3' : '#FF6E6E73'}"`

**fullHeader** = `container(vertical, width=matchParent, paddings top=24/start=20/end=20/bottom=8,
transitionIn/Out=fade)` items in order:
1. city — `text(text = current.city, fontSize=20, fontWeight=bold, width=wrapContent)`
   `.evaluate(textColor = expression<Color>(titleColorExpr))`
2. big temp — `text(text = "${current.tempC}°", fontSize=72, fontWeight=bold, width=wrapContent, margins top=4)`
   `.evaluate(textColor = expression<Color>(titleColorExpr))`
3. condition — `text(text = loc("condition.${current.condition.name}", current.condition.name),
   fontSize=18, width=wrapContent)` `.evaluate(textColor = expression<Color>(subColorExpr))`
4. max/min row — `container(horizontal, width=wrapContent, margins top=6)` items:
   `text("↑ ${daily0.tempMax}°", fontSize=16, width=wrapContent)` .evaluate(titleColor),
   `text("  ↓ ${daily0.tempMin}°", fontSize=16, width=wrapContent, margins start=12)` .evaluate(subColor)
   where `daily0 = weatherData.dailyList[0]`.

**compactHeader** (one-liner like screenshot 2) = `container(vertical, width=matchParent,
paddings top=12/start=20/end=20/bottom=8, transitionIn/Out=fade)` items:
1. `text(text = current.city, fontSize=17, fontWeight=bold, width=wrapContent)` .evaluate(titleColor)
2. `text(text = "${current.tempC}°  |  " + loc("condition.${current.condition.name}", current.condition.name),
   fontSize=15, width=wrapContent, margins top=2)` .evaluate(subColor)

`loc(key, fb)` == `localizer.getOrDefault(key, fb)` (helper you already have via `localizer`).

### 4b. scrollBody (the vertical scroll region + scroll_state extension) — task 3/2

```
scrollBody = gallery(
    id = "main_scroll",
    orientation = vertical,
    extensions = listOf(extension(id = "scroll_state")),   // C reads THIS gallery's vertical scroll
    width = matchParentSize(),
    height = matchParentSize(),          // fills remaining vertical space under the pinned header
    paddings = edgeInsets(start = 16, top = 8, end = 16, bottom = 96),  // bottom clears the FABs
    items = listOf(hourlyBlock, weeklyBlock, sunPhaseBlock, metricsGrid),
)
```
- `gallery(...)` with `orientation`, `extensions:List<Extension>`, `items:List<Div>` — `Gallery.kt:429`.
- `extension(id=…)` — `Extension.kt:69`.
- NOTE on `height=matchParentSize()` inside a vertical container with a wrap-height header sibling:
  DivKit gives the single match_parent-height child the remaining space (weighted fill). If Stage-2
  integration shows the gallery does NOT scroll, the fallback is a fixed `height=fixedSize(N)` — flag,
  do not silently change. This is a layout detail A cannot exercise without the client.

---

## 5. scrollBody children

### 5a. hourlyBlock — horizontal hourly gallery (24 cells) — task 3

```
hourlyBlock = container(vertical, width=matchParent, margins top=8, items = listOf(
   sectionLabel(loc("hourly.now","Now") … NO — see below),
   hourlyGallery))
```
Actually: no section title needed for hourly. `hourlyBlock = hourlyGallery` directly (a horizontal
gallery). Build:
```
hourlyGallery = gallery(
   orientation = horizontal,     // default; set explicitly
   width = matchParentSize(),
   height = wrapContentSize(),
   itemSpacing = 12,
   items = weatherData.hourlyList.map { hourCell(it) },   // exactly 24
)
```
NO `scroll_state` extension here (that lives on `main_scroll`).

`hourCell(h)` = `container(vertical, width=fixedSize(64), paddings all=8,
background=[solidBackground(color("#22FFFFFF"))], border=border(cornerRadius=14),
contentAlignmentHorizontal=center)` items:
1. `text(h.time, fontSize=13, width=wrapContent, textAlignmentHorizontal=center)` .evaluate(subColor)
2. `text(conditionEmoji(h.condition), fontSize=22, width=wrapContent, margins top=4)`
3. `text("${h.tempC}°", fontSize=16, fontWeight=bold, width=wrapContent, margins top=4)` .evaluate(titleColor)

Icon decision (justified): **emoji glyph rendered as `text`**, NOT an image url. Reasons: no
per-condition icon assets exist (only backgrounds + popup), emoji needs no Coil load per cell (24
cells), is theme-neutral, and requires zero new endpoints/strings. Map (pin in a private helper
`conditionEmoji`):
`CLEAR→☀️  CLOUDY→☁️  RAIN→🌧️  SNOW→❄️  THUNDER→⛈️  FOG→🌫️`.

### 5b. weeklyBlock — 7-row weekly list with range bar — task 3

Compute once (Kotlin, server-side): `weekMin = dailyList.minOf{it.tempMin}`,
`weekMax = dailyList.maxOf{it.tempMax}`, `span = max(1, weekMax - weekMin)`.

```
weeklyBlock = container(vertical, width=matchParent, margins top=16,
   background=[solidBackground().evaluate(color=expression<Color>(cardBgExpr))],
   border=border(cornerRadius=16), paddings all=8,
   items = weatherData.dailyList.map { dailyRow(it, weekMin, span) })
```
`cardBgExpr = "@{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}"` (semi-opaque card over the bg image).

`dailyRow(d, weekMin, span)` = `container(horizontal, width=matchParent, paddings top=10/bottom=10/start=8/end=8,
contentAlignmentVertical=center)` items:
1. weekday — `text(d.weekday, fontSize=16, width=fixedSize(44))` .evaluate(titleColor)
2. icon — `text(conditionEmoji(d.condition), fontSize=18, width=fixedSize(32), textAlignmentHorizontal=center)`
3. minTemp — `text("${d.tempMin}°", fontSize=15, width=fixedSize(40), textAlignmentHorizontal=right)` .evaluate(subColor)
4. rangeBar (see below) — `margins start=8/end=8`
5. maxTemp — `text("${d.tempMax}°", fontSize=15, fontWeight=bold, width=fixedSize(40))` .evaluate(titleColor)
6. precip — ONLY when `d.precipProb > 0`: `text("💧${d.precipProb}%", fontSize=12, width=wrapContent,
   margins start=6)` .evaluate(subColor). If `precipProb == 0`, omit this item entirely (build the
   items list conditionally).

**rangeBar** — proportional track+fill, pixel values computed server-side (Int):
```
offsetPx = ((d.tempMin - weekMin) * 100) / span
fillPx   = max(6, ((d.tempMax - d.tempMin) * 100) / span)
if (offsetPx + fillPx > 100) offsetPx = 100 - fillPx      // clamp
```
```
rangeBar = container(orientation = overlap, width = fixedSize(100), height = fixedSize(6),
   border = border(cornerRadius = 3),
   background = [solidBackground(color("#33FFFFFF"))],          // track
   items = listOf(
     container(width = fixedSize(fillPx), height = fixedSize(6), margins = edgeInsets(start = offsetPx),
        border = border(cornerRadius = 3),
        background = [solidBackground(color("#FFFF9500"))])       // warm fill
   ))
```

### 5c. sunPhaseBlock — DivCustom `sun_phase` — task 3 (below-fold)

```
sunPhaseBlock = container(vertical, width=matchParent, margins top=16,
   background=[solidBackground().evaluate(color=expression<Color>(cardBgExpr))],
   border=border(cornerRadius=16), paddings all=12,
   items = listOf(
     sunPhaseCustom,
     container(horizontal, width=matchParent, margins top=8, items = listOf(
        text("↑ " + loc("weather.sunrise","Sunrise") + " ${current.sunrise}", fontSize=13, width=wrapContent).evaluate(subColor),
        text("  ↓ " + loc("weather.sunset","Sunset") + " ${current.sunset}", fontSize=13, width=wrapContent, margins start=16).evaluate(subColor),
     )),
   ))

sunPhaseCustom = custom(
   customType = "sun_phase",
   customProps = mapOf("sunrise" to current.sunrise, "sunset" to current.sunset),   // both "HH:mm"
   width = matchParentSize(),
   height = fixedSize(120),
)
```
- `custom(customType:String?, customProps:Map<String,Any>?, items, width, height, …)` — `Custom.kt:313`.
- The native view (C) draws the sunrise→now→sunset arc; it computes "now" from the device clock (proto
  carries no current-time field). See the exact C contract in §9.

### 5d. metricsGrid — 2-column grid of metric cards — task 3 (below-fold)

```
metricsGrid = grid(columnCount = 2, width = matchParentSize(), margins = edgeInsets(top = 16),
   items = listOf(
     metricCard(loc("weather.uv","UV index"),        "${current.uvIndex}  " + uvBand(current.uvIndex)),
     metricCard(loc("feels_like","feels like"),      "${current.feelsC}°"),
     metricCard(loc("weather.precipitation","Precip"), "${daily0.precipProb}%"),
     metricCard(loc("weather.visibility","Vis"),     kmValue(current.visibility) + " " + loc("unit.visibility","km")),
     metricCard(loc("weather.humidity","Humidity"),  "${current.humidity}%"),
     metricCard(loc("weather.pressure","Pressure"),  "${current.pressure} " + loc("unit.pressure","hPa")),
     metricCard(loc("weather.wind","Wind"),          "${current.wind} " + loc("unit.wind","km/h")),
   ))
```
- `grid(columnCount:Int?, items:List<Div>?, …)` — `Grid.kt:394`. 7 items over 2 cols = 4 rows (last
  cell alone) — acceptable.
- `uvBand(uv)` = `loc(key, fb)` per thresholds (§ literal block): 0–2 low, 3–5 moderate, 6–7 high,
  8–10 very_high, ≥11 extreme.
- `kmValue(meters)` = `(meters / 1000).toString()` (Int km; e.g. 10000 → "10").
- `daily0 = dailyList[0]`.

`metricCard(label, value)` = `container(vertical, width=matchParent, margins all=6, paddings all=14,
background=[solidBackground().evaluate(color=expression<Color>(cardBgExpr))], border=border(cornerRadius=16))`
items:
1. `text(label, fontSize=13, width=wrapContent)` .evaluate(subColor)
2. `text(value, fontSize=22, fontWeight=bold, width=wrapContent, margins top=6)` .evaluate(titleColor)

---

## 6. Layer 3 — popup image (task 7)

In the EXISTING `popupCard`, change `items = listOf(closeX, title, installBtn)` →
`items = listOf(closeX, popupImage, title, installBtn)`. Everything else in the popup stays identical.

```
popupImage = image(
   imageUrl = url("https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/popup_image.png"),
   preview  = POPUP_PREVIEW_DATA_URL,     // pinned literal below — paste verbatim
   width  = matchParentSize(),
   height = fixedSize(140),
   scale  = fill,
   border = border(cornerRadius = 12),
   margins = edgeInsets(top = 8),
)
```
`image.preview` accepts a base64 data-url; verified format `data:[;base64],<data>` (`Image.kt:257`),
client `DecodeBase64ImageTask.kt` strips the `data:…,` prefix then `Base64.decode` — so the pinned
data-url form works.

---

## 7. Layer 2 — bottom FAB overlay (task 4)

```
fabRow = container(orientation = horizontal, width = matchParentSize(), height = wrapContentSize(),
   alignmentVertical = bottom, contentAlignmentHorizontal = center,
   paddings = edgeInsets(bottom = 20),
   items = listOf(
     fab("🗺️", null),                                             // decorative
     fab("📍", null),                                             // decorative
     fab("⚙️", action(logId="fab_settings", url=url("weather-app://navigate?screen=settings"))),
     fab("☰",  action(logId="fab_about",    url=url("weather-app://navigate?screen=about"))),
   ))
```
`fab(glyph, act)` = a circular button:
```
text(text = glyph, fontSize = 22, width = fixedSize(56), height = fixedSize(56),
     textAlignmentHorizontal = center, textAlignmentVertical = center,
     margins = edgeInsets(start = 8, end = 8),
     background = listOf(solidBackground(color("#CC1C1C1E"))),
     border = border(cornerRadius = 28),           // 28 = half of 56 → circle
     actions = if (act == null) null else listOf(act))
```
`alignmentVertical = bottom` positions the row at the bottom of the overlap root (`EnumValues.kt:770`).
`text` accepts `height`, `textAlignmentVertical`, `actions` (existing renderer uses these).
Do NOT add a Coil/image dependency for FABs — emoji glyphs only.

**No new string keys are required for this whole stage** — city/temp/condition come from data; every
label reuses an existing Stage-0 key (§ literal block lists them). This deliberately avoids editing
the shared `strings_*.json` (Worktree B also edits them).

---

## 8. Acceptance + the two EXPECTED smoke failures

Run: `cd /Users/the-leo/divkit-weather-workshop/backend && ./gradlew clean test`
(tests run with `weather.source=mock` → deterministic `current.bgKey="cloudy_day"`,
`current.city="Москва"`, `condition=CLOUDY`, 24 hourly, 7 daily).

STAY GREEN: `document returns templates and all three screens`,
`navigate urls use correct format`, `reactive theme variable expression is present`,
`ping`, `main screen contains stored-values widget popup`, all `weather-json`/`city-search` tests.

WILL FAIL (expected — do NOT edit the test file, REPORT to orchestrator):
- **`lang=en returns english strings`** — asserts en body contains `"Today"`. The literal
  today/tomorrow cards are gone, so `day.today` ("Today") is no longer rendered.
  Proposed minimal orchestrator edit: assert `body.contains("Weather")` (en `screen.main.title`) and
  `assertFalse(body.contains("Погода"))`.
- **`document still renders legacy today, tomorrow`** — asserts `"Сегодня"`+`"Завтра"`. Both gone.
  Proposed minimal orchestrator edit: rename to `document renders new weather main card` and assert
  `body.contains("sun_phase")` and `body.contains("main_scroll")` and
  `body.contains("background_cloudy_day.png")`.

Also run `./gradlew run` in one shell and:
```
curl -s 'http://localhost:8080/document?lang=ru' | grep -o 'sun_phase\|scroll_state\|background_cloudy_day.png\|header_collapsed' | sort -u
# expect all four substrings; confirms the new main card serializes.
```

---

## 9. CONTRACT FOR WORKTREE C (native side — implement in parallel, no shared code)

Everything C must know to build the native side without seeing A's code. Glue = these strings only.

| Thing | Exact value | Direction |
|---|---|---|
| DivCustom type | `custom_type = "sun_phase"` | A emits → C renders |
| Custom props | `custom_props = { "sunrise": "HH:mm", "sunset": "HH:mm" }` (both Strings) | A emits → C reads |
| Extension id | `extension.id = "scroll_state"`, attached to the **vertical** gallery `id="main_scroll"` | A emits → C reads |
| Collapse variable | `header_collapsed` — **Boolean**, declared in the card (default `false`) | C writes → A reads |

Details for C:
- **`sun_phase` custom view**: receives `custom_props["sunrise"]` and `custom_props["sunset"]` as
  `"HH:mm"` local-time strings. Compute "now" from the device clock (no time field is passed). Draw
  the sunrise→now→sunset arc. The view is laid out `match_parent × 120dp`.
- **`scroll_state` extension**: bound to the RecyclerView-backed vertical gallery `main_scroll`.
  Observe its vertical scroll offset; set the card variable `header_collapsed = true` once scrolled
  past a small threshold (suggest ~24–48dp) and `false` when back near the top. Write via the view's
  variable controller (the local card variable `header_collapsed` is already declared by A). Toggling
  it flips the header `state` between `full`/`collapsed` (A wires the animation).
- A does NOT touch `header_collapsed` beyond declaring it; C fully owns writing it.
- If C prefers to hook a different scroll source, only the extension's attach point moves — the
  Boolean-variable read contract on A's side is unchanged.

---

## 10. LITERAL / PINNED VALUES (single source of truth)

```
BG_IMAGE_URL   = https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_${current.bgKey}.png
POPUP_IMAGE_URL= https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/popup_image.png

# condition → emoji (private fun conditionEmoji)
CLEAR ☀️ | CLOUDY ☁️ | RAIN 🌧️ | SNOW ❄️ | THUNDER ⛈️ | FOG 🌫️

# UV band → string key (private fun uvBand → loc(key, fallback))
0..2  → weather.uv.low        (Низкий / Low)
3..5  → weather.uv.moderate   (Умеренный / Moderate)
6..7  → weather.uv.high       (Высокий / High)
8..10 → weather.uv.very_high  (Очень высокий / Very high)
>=11  → weather.uv.extreme    (Экстремальный / Extreme)

# theme expressions (constants at top of buildCard)
titleColorExpr = @{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}
subColorExpr   = @{theme == 'dark' ? '#FF9E9EA3' : '#FF6E6E73'}
cardBgExpr     = @{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}
headerStateExpr= @{header_collapsed ? 'collapsed' : 'full'}

# existing string keys reused (NO new keys added):
condition.{CLEAR,CLOUDY,RAIN,SNOW,THUNDER,FOG}, weather.uv, weather.uv.{low,moderate,high,very_high,extreme},
weather.sunrise, weather.sunset, weather.humidity, weather.pressure, weather.visibility,
weather.precipitation, weather.wind, feels_like, hourly.now, unit.pressure, unit.visibility, unit.wind

# POPUP_PREVIEW_DATA_URL — paste verbatim (28px low-q JPEG of S3/popup_image.png, 946 bytes):
data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAASABIAAD/4QBMRXhpZgAATU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAHKADAAQAAAABAAAAHAAAAAD/7QA4UGhvdG9zaG9wIDMuMAA4QklNBAQAAAAAAAA4QklNBCUAAAAAABDUHYzZjwCyBOmACZjs+EJ+/8AAEQgAHAAcAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/bAEMAExMTExMTIBMTIC0gICAtPS0tLS09TT09PT09TV1NTU1NTU1dXV1dXV1dXXBwcHBwcIODg4ODk5OTk5OTk5OTk//bAEMBFxgYJSMlQCMjQJloVWiZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmf/dAAQAAv/aAAwDAQACEQMRAD8A2NV1SexmWOJFYMuec+tZ8evXsjbVjT9f8afry7rlD/sf1NZ1mywyhmHSurkXs7panO5vntc2JtU1GFdzxIB+P+Nb8LmWFJDwWUE/iK568uo5Yto5Jrftv+PeL/cX+VcsW3G8lY30vZM//9DT1hN06f7v9TWR5Zrtmjjfl1B+opvkQ/3F/IV0wrJK1jnnRbd7nGeWa7K3/wBRH/uj+VL5EP8AcX8hUoAAwKipUUuhVOm47s//2Q==
```

---

## 11. Imports the renderer will add (divan DSL)

`divkit.dsl.gallery`, `divkit.dsl.image`, `divkit.dsl.custom`, `divkit.dsl.grid`,
`divkit.dsl.extension`, `divkit.dsl.state`, `divkit.dsl.stateItem`, `divkit.dsl.fadeTransition`,
`divkit.dsl.changeBoundsTransition`, `divkit.dsl.fill`, `divkit.dsl.bottom`, `divkit.dsl.horizontal`
(already), and keep existing imports. `expression<String>` via existing `divkit.dsl.core.expression`.
`ConditionCode` via `workshop.proto.WeatherDataOuterClass.ConditionCode`.

---

## 12. Test plan (what the reviewer/verifier will check)

Automated (`./gradlew clean test`): the green set in §8 passes; the 2 flagged tests fail EXACTLY as
predicted (orchestrator applies the §8 edits, then all green).

JSON-shape assertions (add mentally / the verifier greps the `/document?lang=ru` body):
- contains `"custom_type":"sun_phase"`, `"custom_props"` with `sunrise`/`sunset`.
- contains `"id":"scroll_state"` inside the gallery with `id`=`main_scroll`.
- contains `header_collapsed` (both the variable decl and the `@{header_collapsed ...}` expr).
- contains `background_cloudy_day.png` (mock bg) and `popup_image.png` + a `"preview":"data:image/jpeg;base64,`.
- still contains the whole popup stored-value block (unchanged).
- `overlap` root still present.

Manual (needs client — Stage 2): scroll `main_scroll` → header animates full↔collapsed; sun arc
renders; bg image loads; popup shows the image with the base64 preview flashing first.

---

## 13. ORCHESTRATOR NOTES (implementer may skip)

- **Extension attach point deviates from the literal task wording.** Task 2 said "extension on the
  (hourly) gallery"; a *horizontal* scroll cannot drive a header collapse. умник-decision: attach
  `scroll_state` to the OUTER *vertical* scroll gallery (`main_scroll`). This is the only design where
  "scroll down → header collapses" is correct. A/C stay decoupled (glue = the variable name), so if C
  implemented against the hourly gallery, moving the extension id is a one-line change with no impact
  on A's read contract. FLAGGED for the orchestrator to confirm with Worktree C.
- **Header collapse mechanism confidence: MEDIUM-HIGH.** `state.default_state_id` bound to an
  expression is the canonical DivKit reactive+animated switch (verified in `DivStateBinder.kt`). Small
  residual risk that `default_state_id` doesn't re-evaluate on a *variable* change without a state
  action. If Stage-2 integration shows the header doesn't switch, the robust fallback is the
  purpose-built `state.state_id_variable` path (`DivStateBinder.observeStateIdVariable`, line 328),
  which needs a **String** variable holding the state id. That would require adding a `header_state`
  String var to the name map (C writes `"full"`/`"collapsed"` instead of/in addition to the Boolean).
  Not doing that now to honor the fixed Boolean name-map; recorded as a known lever.
- **`gallery height=matchParent` weighted-fill** (§4b) is the one layout behavior A cannot verify
  headless. If the vertical gallery won't scroll in Stage 2, switch it to a fixed height. Flagged.
- **No new string keys / no `strings_*.json` edits** — eliminates the merge-conflict risk with
  Worktree B entirely. If a reviewer wants a localized "%"/"°" unit, that is a deliberate non-goal
  here (hard-coded symbols) to keep the JSON untouched.
- **This stage is real UI work** (state machine + galleries + custom + grid + transitions), not thin
  glue — full planner→implementer→review ceremony is warranted.
```
