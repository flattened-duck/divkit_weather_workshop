# Stage 2 — Below-the-fold metric cards (iOS "screen 2" detail cards)

Target file (ONLY): `backend/src/main/kotlin/workshop/renderer/WeatherMainRenderer.kt`
Plus: `backend/src/main/resources/strings/strings_ru.json` and `strings_en.json` (append keys).
Optional: one assertion in `backend/src/test/kotlin/workshop/ApplicationSmokeTest.kt`.

DO NOT touch: the header/state code (`headerState`, `fullHeader`, `compactHeader`, `HEADER_STATE_VAR`), hourly, weekly, background, popup, FAB, `data(...)` wrapper, proto, provider, app/**, other renderers.

---

## MUST-NOT-GET-WRONG (load-bearing)
1. Keep these tokens present in the rendered `main` card (smoke tests grep them): `sun_phase`, `"sunrise"`, `"sunset"` (custom_props keys), `main_scroll`, `scroll_state`, `background_cloudy_day.png`, `state_id_variable`/`header_state`. You are NOT editing those producers — just don't delete the sun_phase custom; the new Sunset card re-creates it with the SAME `custom_props`.
2. Theme colors come ONLY from the existing companion exprs `CARD_BG_EXPR`, `TITLE_COLOR_EXPR`, `SUB_COLOR_EXPR`. Do not hardcode surface/text colors. UV/pressure GRADIENT hexes are fixed literals (theme-independent) — that is intentional.
3. Marker offset is an integer dp margin computed SERVER-SIDE (DivKit has no percent margins). Use the exact formula and the pinned golden values below.
4. `linearGradient(angle = 0, ...)` renders horizontal left→right (verified). Left color = low end. Do not change the angle.
5. The Sunset card is FULL-WIDTH (own scroll item), NOT a grid cell — its 120dp arc must not couple a normal card's height.
6. Do not declare any new global/local `data` variable. Cards are pure server-rendered from `weatherData`.
7. `String.uppercase()` (no-arg) only — locale-independent. Uppercase the localized title inside the card helper.

---

## IMPLEMENTER SPEC

### 1. Imports to ADD (top of file, alphabetical with existing)
```
import divkit.dsl.EdgeInsets
import divkit.dsl.linearGradient
import divkit.dsl.core.valueArrayElement
import kotlin.math.roundToInt
```
(`center`, `overlap`, `fixedSize`, `matchParentSize`, `wrapContentSize`, `solidBackground`, `color`, `border`, `edgeInsets`, `expression`, `evaluate`, `bold`, `text`, `container`, `grid`, `custom`, `Div`, `Color` are already imported.)

### 2. DELETE from `buildCard`
- the `val sunPhaseCustom = custom(...)` block AND the `val sunPhaseBlock = container(...)` block (lines ~296–330).
- the `val metricsGrid = grid(...)` block (lines ~332–348).
- the whole old helper `private fun DivScope.metricCard(label, value): Div` (lines ~502–523).

### 3. `scrollBody.items` — change ONLY the last two entries
Old: `items = listOf(hourlyGallery, weeklyBlock, sunPhaseBlock, metricsGrid)`
New: `items = listOf(hourlyGallery, weeklyBlock, sunsetCard, detailsGrid)`
(`hourlyGallery`, `weeklyBlock` untouched.)

### 4. New `val`s inside `buildCard` (place where the deleted blocks were, before `scrollBody`)
Compute derived values first:
```
val uvFrac = current.uvIndex.coerceIn(0, 11) / 11.0
val pressFrac = ((current.pressure - PRESS_MIN).toDouble() / (PRESS_MAX - PRESS_MIN))

val feelsDelta = current.feelsC - current.tempC
val feelsSubtitle = when {
    kotlin.math.abs(feelsDelta) <= 1 -> loc("feels.similar", "Similar to the actual temperature")
    feelsDelta > 1 -> loc("feels.warmer", "Feels warmer than it actually is")
    else -> loc("feels.cooler", "Feels cooler than it actually is")
}
val visSubtitle = when {
    current.visibility >= 20000 -> loc("vis.perfect", "Perfectly clear")
    current.visibility >= 10000 -> loc("vis.good", "Good visibility")
    else -> loc("vis.reduced", "Reduced visibility")
}
```

Sunset card (FULL WIDTH, own item):
```
val sunsetArc = custom(
    customType = "sun_phase",
    customProps = mapOf("sunrise" to current.sunrise, "sunset" to current.sunset),
    width = matchParentSize(),
    height = fixedSize(120),
)
val sunsetCard = detailCard(
    icon = "🌇",
    title = loc("weather.sunset", "Sunset"),
    bigValue = current.sunset,
    body = sunsetArc,
    subtitle = loc("card.sunrise_at", "Sunrise at") + " " + current.sunrise,
    margins = edgeInsets(top = 16),
)
```

Grid of the remaining 7 cards (2 columns; last cell sits alone in the left column — accepted):
```
val detailsGrid = grid(
    columnCount = 2,
    width = matchParentSize(),
    margins = edgeInsets(top = 16),
    items = listOf(
        detailCard(
            icon = "🔆",
            title = loc("weather.uv", "UV index"),
            bigValue = "${current.uvIndex}",
            secondLine = uvBand(current.uvIndex),
            body = markerScale(uvFrac, UV_SCALE_HEX),
        ),
        detailCard(
            icon = "🌡️",
            title = loc("feels_like", "feels like"),
            bigValue = "${current.feelsC}°",
            subtitle = feelsSubtitle,
        ),
        detailCard(
            icon = "🌧️",
            title = loc("weather.precipitation", "Precipitation"),
            bigValue = "${daily0.precipProb}%",
            subtitle = loc("precip.subtitle", "Chance today"),
        ),
        detailCard(
            icon = "👁️",
            title = loc("weather.visibility", "Visibility"),
            bigValue = "${current.visibility / 1000} " + loc("unit.visibility", "km"),
            subtitle = visSubtitle,
        ),
        detailCard(
            icon = "💧",
            title = loc("weather.humidity", "Humidity"),
            bigValue = "${current.humidity}%",
        ),
        detailCard(
            icon = "🧭",
            title = loc("weather.pressure", "Pressure"),
            bigValue = "${current.pressure} " + loc("unit.pressure", "hPa"),
            body = markerScale(pressFrac, PRESS_SCALE_HEX),
        ),
        detailCard(
            icon = "💨",
            title = loc("weather.wind", "Wind"),
            bigValue = "${current.wind} " + loc("unit.wind", "km/h"),
        ),
    ),
)
```

### 4b. Remove the two dead FABs from `fabRow`
In the `val fabRow = container(... items = listOf(...))` block, DELETE the first two entries (they have `null` actions and do nothing):
- `fab("🗺️", null),`
- `fab("📍", null),`
Keep ONLY the two functional FABs, unchanged, in this order:
```
items = listOf(
    fab("⚙️", action(logId = "fab_settings", url = url("weather-app://navigate?screen=settings"))),
    fab("☰", action(logId = "fab_about", url = url("weather-app://navigate?screen=about"))),
),
```
Do NOT change `fabRow`'s own props (`contentAlignmentHorizontal = center`, paddings, etc.) or the `fab(...)` helper — they stay centered/styled as-is. This is the ONLY FAB change.
Note: the smoke test `navigate urls use correct format` still passes (both settings/about navigate URLs remain present); `weather-app://navigate?screen=main` comes from another renderer, not `fabRow`.

### 5. New helper `detailCard` (replaces old `metricCard`)
Signature — exactly:
```
private fun DivScope.detailCard(
    icon: String,
    title: String,
    bigValue: String,
    secondLine: String? = null,
    body: Div? = null,
    subtitle: String? = null,
    margins: EdgeInsets = edgeInsets(start = 6, top = 6, end = 6, bottom = 6),
): Div
```
Rules for the returned node — a single vertical `container`:
- `width = matchParentSize()`, `margins = margins`, `paddings = edgeInsets(start = 14, top = 14, end = 14, bottom = 14)`, `border = border(cornerRadius = 16)`, `background = listOf(solidBackground().evaluate(color = expression<Color>(CARD_BG_EXPR)))`.
- `items` built with `buildList<Div>` in THIS order, each element only if applicable:
  1. Title row (always): `text(text = "$icon  ${title.uppercase()}", width = wrapContentSize(), fontSize = 12).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR))`.
  2. Big value (if `bigValue.isNotEmpty()`): `text(text = bigValue, width = wrapContentSize(), fontSize = 28, fontWeight = bold, margins = edgeInsets(top = 8)).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR))`.
  3. Second line (if `secondLine != null`): `text(text = secondLine, width = wrapContentSize(), fontSize = 15, margins = edgeInsets(top = 2)).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR))`.
  4. Body (if `body != null`): wrap it → `container(width = matchParentSize(), margins = edgeInsets(top = 10), items = listOf(body))`.
  5. Subtitle (if `subtitle != null`): `text(text = subtitle, width = wrapContentSize(), fontSize = 13, margins = edgeInsets(top = 8)).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR))`.

### 6. New helper `markerScale` (UV + pressure scale with positioned marker)
```
private fun DivScope.markerScale(fraction: Double, gradientHex: List<String>): Div {
    val offset = (fraction.coerceIn(0.0, 1.0) * (SCALE_W - MARKER_W)).roundToInt()
    return container(
        orientation = overlap,
        width = fixedSize(SCALE_W),
        height = fixedSize(16),
        items = listOf(
            container(                                  // gradient track
                width = matchParentSize(),
                height = fixedSize(6),
                alignmentVertical = center,
                border = border(cornerRadius = 3),
                background = listOf(
                    linearGradient(
                        angle = 0,
                        colors = gradientHex.map { valueArrayElement(color(it)) },
                    ),
                ),
            ),
            container(                                  // marker (white bar)
                width = fixedSize(MARKER_W),
                height = fixedSize(16),
                margins = edgeInsets(start = offset),
                border = border(cornerRadius = 2),
                background = listOf(solidBackground(color("#FFFFFFFF"))),
            ),
        ),
    )
}
```

### 7. Companion constants to ADD
```
const val SCALE_W = 120
const val MARKER_W = 4
const val PRESS_MIN = 980
const val PRESS_MAX = 1040
val UV_SCALE_HEX = listOf("#FF34C759", "#FFFFCC00", "#FFFF9500", "#FFFF3B30", "#FFAF52DE")
val PRESS_SCALE_HEX = listOf("#FF5AC8FA", "#FF34C759", "#FFFF9500")
```
(These are plain data — put them in the existing `private companion object`. `UV_SCALE_HEX`/`PRESS_SCALE_HEX` are `List<String>` hex; `color(...)` is applied inside `markerScale`, which has `DivScope`.)

---

## NEW STRING KEYS (append to BOTH json files; keep existing trailing key valid — add comma)

`strings_ru.json`:
```
"feels.similar": "Примерно как фактическая температура",
"feels.warmer": "Ощущается теплее, чем на самом деле",
"feels.cooler": "Ощущается холоднее, чем на самом деле",
"vis.perfect": "Идеальная видимость",
"vis.good": "Хорошая видимость",
"vis.reduced": "Пониженная видимость",
"precip.subtitle": "Вероятность осадков сегодня",
"card.sunrise_at": "Восход в"
```

`strings_en.json`:
```
"feels.similar": "Similar to the actual temperature",
"feels.warmer": "Feels warmer than it actually is",
"feels.cooler": "Feels cooler than it actually is",
"vis.perfect": "Perfectly clear",
"vis.good": "Good visibility",
"vis.reduced": "Reduced visibility",
"precip.subtitle": "Chance today",
"card.sunrise_at": "Sunrise at"
```
No new title keys needed — Sunset/UV/Feels/Precip/Vis/Humidity/Pressure/Wind titles reuse existing keys (`weather.sunset`, `weather.uv`, `feels_like`, `weather.precipitation`, `weather.visibility`, `weather.humidity`, `weather.pressure`, `weather.wind`). UV band reuses existing `uvBand()` + `weather.uv.*`.

---

## GOLDEN / REFERENCE VALUES (mock data: uv=4, pressure=1013, visibility=10000, feels=14, temp=17, sunrise="04:45", sunset="21:15", daily0.precipProb=0, humidity=60, wind=12)
- `uvFrac = 4/11 = 0.363636…`; UV marker `offset = round(0.363636 * (120-4)) = round(42.18) = 42` → JSON `"margins":{"start":42}` inside the UV card's marker.
- `pressFrac = (1013-980)/(1040-980) = 0.55`; pressure marker `offset = round(0.55 * 116) = round(63.8) = 64` → `"margins":{"start":64}`.
- UV band (uv=4 ≤5) → `weather.uv.moderate` = "Умеренный" / "Moderate" (as `secondLine`).
- feelsDelta = 14-17 = -3 → `feels.cooler` → "Ощущается холоднее, чем на самом деле".
- visibility=10000 → `vis.good` → "Хорошая видимость"; bigValue "10 км".
- Sunset card: bigValue "21:15", subtitle "Восход в 04:45".
- precip bigValue "0%"; humidity "60%"; wind "12 км/ч"; pressure bigValue "1013 гПа".

---

## VERIFIED DIVKIT BUILDERS (R-32.57, ground truth `/Users/the-leo/divkit_source/divkit`)
- `linearGradient(angle: Int?, colorMap, colors: List<ArrayElement<Color>>)` — `json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/LinearGradient.kt:124`. Returns `LinearGradient : Background`, so it goes directly in `background = listOf(...)`.
- Gradient colors literal form `colors = listOf(valueArrayElement(color("#RRGGBB")), …)` and JSON emits a bare string array under `"colors"` with `"type":"gradient"` — `divan-dsl/src/test/kotlin/divkit/dsl/ArrayElementsTest.kt:13`; `valueArrayElement` at `divan-dsl/src/main/kotlin/divkit/dsl/core/Arrays.kt:30`.
- angle semantics: `angle=0` → horizontal left→right shader (start x=0, end x=width) — `client/android/div/src/main/java/com/yandex/div/internal/drawable/LinearGradientDrawable.kt:57` (cos0=1 ⇒ widthDelta=width/2, heightDelta=0).
- `alignmentVertical = center` valid on a container child: `center` is `CenterEnumValue` implementing `AlignmentVertical` — `divan-dsl/src/generated/kotlin/divkit/dsl/EnumValues.kt:192`. Container exposes `alignmentVertical` — `Container.kt:544`.
- `edgeInsets(...)` returns `EdgeInsets` (`divan-dsl/src/generated/kotlin/divkit/dsl/EdgeInsets.kt:27,105`) — usable as a param type/default.
- `overlap` orientation, empty-`items` colored `container`, `fixedSize`/`matchParentSize`, `border(cornerRadius=…)`, `solidBackground().evaluate(color=…)`, `custom(customType, customProps, width, height)` — all already used verbatim in this file (rangeBar/backgroundImage/sunPhase). No new risk.

---

## ACCEPTANCE CRITERIA
1. `cd backend && ./gradlew clean test` → GREEN (all existing smoke tests still pass; `sun_phase`, `main_scroll`, `background_cloudy_day.png`, `state_id_variable`/`header_state`, `"sunrise"`, `"sunset"`, `scroll_state` still present).
2. Run server, `curl -s "http://localhost:PORT/document?lang=ru" | python3 -m json.tool` → valid JSON, no error.
3. Grep the RU document body for: `"type":"gradient"` (2 hits: UV + pressure), `"margins":{"start":42}` (UV marker), `"margins":{"start":64}` (pressure marker), `ЗАКАТ`, `УФ-ИНДЕКС`, `ОЩУЩАЕТСЯ КАК`, `Хорошая видимость`, `Ощущается холоднее, чем на самом деле`.
4. Grep EN document (`lang=en`) → `SUNSET`, `UV INDEX`, `Good visibility`, `Feels cooler than it actually is`.
5. `git diff --name-only` shows ONLY `WeatherMainRenderer.kt`, `strings_ru.json`, `strings_en.json` (+ optionally `ApplicationSmokeTest.kt`).
6. FAB row: rendered `main` document contains exactly the ⚙️/☰ FAB glyphs and both `weather-app://navigate?screen=settings` and `...=about` URLs; the 🗺️ and 📍 glyphs are gone. `navigate urls use correct format` smoke test still GREEN.

### OPTIONAL smoke assertion (recommended, add to `document renders new weather main card`)
```
assertTrue(body.contains("\"type\":\"gradient\""), "UV/pressure scale gradient must be present")
```

---

## ORCHESTRATOR NOTES (implementer may skip)
- Pressure "gauge": spec uses a lightweight DivKit horizontal `markerScale` (decorative blue→green→orange gradient, marker at `(pressure-980)/60`). A true semicircular dial would require a NEW native `pressure_gauge` DivCustom — an A↔C contract (proto/customProps + client-side renderer). Marked OPTIONAL / OUT OF SCOPE; recommend NOT doing it this stage.
- Precipitation uses the probability `%` we already have (`daily[0].precip_prob`); an mm value would need a new proto+provider field `precip_mm` — OPTIONAL, out of scope, not recommended.
- 7 cards in a 2-col grid ⇒ the last (Wind) sits alone left. Accepted; matches many real weather apps. If a perfectly even grid is later desired, drop Wind or promote one card to full-width — a cosmetic follow-up, not this stage.
- Humidity/Wind subtitles intentionally omitted (task said optional/simple); dew-point text is out of scope.
- Concurrent header edit (another agent switching `header_collapsed`→`header_state`): this contract touches none of the header/state code, so it composes regardless. If the header edit lands first and the file's line numbers shift, locate blocks by name (`sunPhaseBlock`, `metricsGrid`, `metricCard`) not by line number.
