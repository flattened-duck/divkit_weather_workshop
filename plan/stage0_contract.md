# STAGE 0 CONTRACT — Data spine + real weather source (Open-Meteo)

Implementer: kopatel (Sonnet). Follow literally. Stage 0 edits **ONLY** `backend/**` and
`plan/contract.md`. Do **NOT** touch `app/**`. You never commit.

Ground truth for every divan/DivKit API fact: `/Users/the-leo/divkit_source/divkit`
(branch `R-32.57`, == released `32.57.0`). Do not consult arcadia or memory.

---

## MUST-NOT-GET-WRONG (load-bearing invariants — read first)

1. **Do NOT break the existing `WeatherMainRenderer`.** Keep the proto messages `DayForecast`,
   `WeatherData.today (=1)`, `WeatherData.tomorrow (=2)` intact. You only ADD new fields/messages.
   The provider MUST still populate `today`/`tomorrow` so the unchanged renderer renders.
2. **Bump divan to 32.57.0.** In `build.gradle.kts` change `val divanVersion = "32.6.0"` →
   `"32.57.0"`. The `divanPatch`/`patch`/`patchChange` API used below does not exist in 32.6.0.
3. **Tests must never hit the network.** The Gradle `test` task sets system property
   `weather.source=mock`; `WeatherProvider.create()`/`Geocoder.create()` read it and return the
   offline implementations. Production (`./gradlew run`) uses Open-Meteo.
4. **`bg_key` grammar is exactly** `{base}_{day|night}`, `base ∈ {sunny,cloudy,rain,storm,fog}`.
   Any unmapped WMO code → condition `FOG` → base `fog`. No other strings are legal.
5. **DivPatch response shape is exactly** `{"changes":[{"id":"city_search_results","items":[…]}]}`
   (serialize `divanPatch{…}.patch` with the existing Jackson mapper). No `templates` key unless
   items reference a template (they don't here).
6. **`set_city` URL format is exactly** `weather-app://set_city?lat={lat}&lon={lon}&name={urlEncodedName}`.
   **`city_search` action / `city_query` variable are contract names only** — no client code in Stage 0.
7. **All provider strings are localized server-side** (weekday, "now", sunrise/sunset are `HH:mm`).
   Sunrise/sunset/hourly times are `HH:mm` 24h; weekdays are the short localized forms below.
8. **`/document` param format is `?lang=&lat=&lon=&name=`** with Moscow default when lat/lon absent.

---

## A. Summary & decisions (умник-решения)

- **Legacy fields kept, not removed** (invariant 1). `today`/`tomorrow` become "deprecated, remove in
  Stage 1" but stay so the current renderer compiles and renders real data.
- **New package `workshop.weather`** holds the whole spine. Old `workshop/mock/MockWeatherDataProvider.kt`
  is **deleted** (replaced by `workshop.weather.MockWeatherProvider`).
- **Providers are `suspend`** and called from the (already suspend) Ktor route handlers — no
  `runBlocking`. One shared `HttpClient(CIO)` per process.
- **Debug endpoint `GET /weather-json`** added so Stage 0 has a clean HTTP acceptance surface for
  `current/hourly/daily/bg_key` WITHOUT redesigning the renderer (that is Stage 1). It prints the
  proto as snake_case JSON. Downstream may keep or drop it.
- **Ktor client engine: CIO** (pure-JVM, no native deps). Parse responses as Jackson `JsonNode`
  trees (no DTO classes, no kotlinx-serialization) using a shared `JsonMapper`.
- **Day/night** computed by comparing `current.time` to `daily.sunrise[0]`/`daily.sunset[0]`
  (per plan), NOT by `is_day` (that field is only a cross-check).
- **UV current** = `round(daily.uv_index_max[0])`. **Visibility current** =
  `round(hourly.visibility[currentIdx])` (meters). See §B4.

Open forks needing no orchestrator input (defaults chosen): see ORCHESTRATOR NOTES.

---

## B. IMPLEMENTER SPEC

### B1. `weather_data.proto` — MODIFY
Path: `backend/src/main/proto/weather_data.proto`. Replace file contents with:

```proto
syntax = "proto3";
package workshop;
option java_package = "workshop.proto";

enum ConditionCode {
  CLEAR   = 0;
  CLOUDY  = 1;
  RAIN    = 2;
  SNOW    = 3;
  THUNDER = 4;
  FOG     = 5;
}

// DEPRECATED (remove in Stage 1). Kept so the current WeatherMainRenderer keeps compiling.
message DayForecast {
  int32         temp_c      = 1;
  int32         temp_feels  = 2;
  ConditionCode condition   = 3;
  string        city        = 4;
}

message Current {
  string        city        = 1;
  int32         temp_c      = 2;
  int32         feels_c     = 3;
  ConditionCode condition   = 4;
  int32         uv_index    = 5;   // 0..11+
  int32         humidity    = 6;   // %
  int32         pressure    = 7;   // hPa
  int32         visibility  = 8;   // meters
  int32         wind        = 9;   // km/h
  string        sunrise     = 10;  // "HH:mm" local
  string        sunset      = 11;  // "HH:mm" local
  string        bg_key      = 12;  // "{sunny|cloudy|rain|storm|fog}_{day|night}"
}

message HourlyPoint {
  string        time        = 1;   // "HH:mm" local, or localized "now" for the current hour
  int32         temp_c      = 2;
  ConditionCode condition   = 3;
}

message DailyPoint {
  string        weekday     = 1;   // short localized, e.g. "Пн"/"Mon"
  int32         temp_min    = 2;
  int32         temp_max    = 3;
  ConditionCode condition   = 4;
  int32         precip_prob = 5;   // %
}

message WeatherData {
  DayForecast          today    = 1;   // deprecated, populated for legacy renderer
  DayForecast          tomorrow = 2;   // deprecated, populated for legacy renderer
  Current              current  = 3;
  repeated HourlyPoint hourly   = 4;   // exactly 24
  repeated DailyPoint  daily    = 5;   // exactly 7
}
```

Acceptance: `./gradlew :compileKotlin` regenerates `workshop.proto.WeatherDataOuterClass.{Current,HourlyPoint,DailyPoint}`; existing `WeatherMainRenderer` still compiles unchanged.

### B2. WMO mapping — CREATE `backend/src/main/kotlin/workshop/weather/WmoMapping.kt`
Purpose: single source of truth for code→condition→background.

Provide (package `workshop.weather`):
- `fun wmoToCondition(code: Int): ConditionCode` — table below; **any code not listed → `FOG`**.
- `fun bgBase(condition: ConditionCode): String` — map below.
- `fun bgKey(condition: ConditionCode, isDay: Boolean): String` = `"${bgBase(condition)}_${if (isDay) "day" else "night"}"`.

WMO → ConditionCode (exhaustive; else FOG):
| WMO codes | ConditionCode |
|---|---|
| 0, 1 | CLEAR |
| 2, 3 | CLOUDY |
| 45, 48 | FOG |
| 51, 53, 55, 56, 57 | RAIN |
| 61, 63, 65, 66, 67 | RAIN |
| 80, 81, 82 | RAIN |
| 71, 73, 75, 77, 85, 86 | SNOW |
| 95, 96, 99 | THUNDER |
| (anything else) | FOG |

ConditionCode → bgBase:
`CLEAR→sunny`, `CLOUDY→cloudy`, `RAIN→rain`, `SNOW→cloudy`, `THUNDER→storm`, `FOG→fog`.

### B3. City registry / params — CREATE `backend/src/main/kotlin/workshop/weather/City.kt`
```kotlin
data class CityParam(val lat: Double, val lon: Double, val name: String?)   // name null → default label
data class CityEntry(val nameRu: String, val nameEn: String, val lat: Double, val lon: Double)
data class CityHit(val name: String, val lat: Double, val lon: Double)      // name already display-localized
```
`object CityRegistry`:
- `val DEFAULT = CityParam(55.7558, 37.6173, null)`  // Moscow
- `val CITIES: List<CityEntry>` — exactly these 8:
  Москва/Moscow 55.7558,37.6173 · Санкт-Петербург/Saint Petersburg 59.9386,30.3141 ·
  Новосибирск/Novosibirsk 55.0084,82.9357 · Лондон/London 51.5074,-0.1278 ·
  Париж/Paris 48.8566,2.3522 · Берлин/Berlin 52.52,13.405 ·
  Нью-Йорк/New York 40.7128,-74.006 · Токио/Tokyo 35.6762,139.6503
- `fun displayName(e: CityEntry, lang: String): String = if (lang == "en") e.nameEn else e.nameRu`
- `fun search(query: String, lang: String): List<CityHit>` — case-insensitive `contains` over
  BOTH `nameRu` and `nameEn`; map matches to `CityHit(displayName(e,lang), lat, lon)`; max 8; empty
  query → empty list.

### B4. Open-Meteo client — CREATE `backend/src/main/kotlin/workshop/weather/OpenMeteoClient.kt`
Purpose: thin Ktor-client wrapper returning parsed Jackson `JsonNode`. Hold ONE shared
`HttpClient(CIO)` + one `JsonMapper` (with `KotlinModule`). Expose:
- `suspend fun forecast(lat: Double, lon: Double): JsonNode`
- `suspend fun geocode(query: String, lang: String): JsonNode`
- `fun close()` (closes the HttpClient)

Forecast request — GET `https://api.open-meteo.com/v1/forecast` with query params:
```
latitude={lat}  longitude={lon}
current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,surface_pressure,wind_speed_10m,is_day
hourly=temperature_2m,weather_code,uv_index,visibility
daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max
timezone=auto  forecast_days=7
```
Geocode request — GET `https://geocoding-api.open-meteo.com/v1/search` with:
`name={query}  count=8  language={lang}  format=json`

Use `client.get(urlString).bodyAsText()` then `mapper.readTree(text)`. Build URLs with proper
query encoding (Ktor `URLBuilder` or `parameters {}`); `query` must be URL-encoded.

Forecast JSON paths you will read (all arrays are parallel & index-aligned):
```
current.time (string "yyyy-MM-ddTHH:mm"), current.temperature_2m, current.apparent_temperature,
current.relative_humidity_2m, current.weather_code, current.surface_pressure, current.wind_speed_10m
hourly.time[] , hourly.temperature_2m[], hourly.weather_code[], hourly.uv_index[], hourly.visibility[]
daily.time[] (yyyy-MM-dd), daily.weather_code[], daily.temperature_2m_max[], daily.temperature_2m_min[],
daily.precipitation_probability_max[] (may be null → 0), daily.sunrise[] (…THH:mm), daily.sunset[], daily.uv_index_max[]
```

### B5. Provider interface + impls — CREATE `backend/src/main/kotlin/workshop/weather/WeatherProvider.kt`
```kotlin
interface WeatherProvider {
    suspend fun provide(city: CityParam, localizer: Localizer): WeatherData
    companion object {
        fun create(): WeatherProvider =
            if (System.getProperty("weather.source") == "mock") MockWeatherProvider
            else OpenMeteoWeatherProvider(OpenMeteoClient())   // holds shared client
    }
}
```

**`MockWeatherProvider` (object)** — `backend/.../weather/MockWeatherProvider.kt`. Deterministic sample,
no I/O. Requirements (exact values free EXCEPT where pinned):
- `current.city = city.name ?: localizer.getOrDefault("city.default", "Москва")`
- `current.condition = CLOUDY`, `current.bg_key = "cloudy_day"`, `sunrise="04:45"`, `sunset="21:15"`,
  `uv_index=4`, `humidity=60`, `pressure=1013`, `visibility=10000`, `wind=12`, `temp_c=17`, `feels_c=14`.
- `hourly` = exactly 24 `HourlyPoint`s; the FIRST has `time = localizer.getOrDefault("hourly.now","now")`,
  the rest `time = "%02d:00".format(hour)` for hours 1..23; temps/conditions any deterministic values.
- `daily` = exactly 7 `DailyPoint`s; `weekday` = the 7 short localized names Mon→Sun via keys
  `weekday.mon`…`weekday.sun` (in that fixed order); any deterministic temps/precip.
- `today = DayForecast(temp_c=17, temp_feels=14, condition=CLOUDY, city=current.city)`;
  `tomorrow = DayForecast(temp_c=20, temp_feels=18, condition=CLEAR, city=current.city)`.

**`OpenMeteoWeatherProvider(client)`** — `backend/.../weather/OpenMeteoWeatherProvider.kt`.
`provide()` algorithm (STRICT order):
1. `val lat = city.lat; val lon = city.lon`.
2. `try { val root = client.forecast(lat, lon) } catch (Throwable) { return MockWeatherProvider.provide(city, localizer) }`
   (network/parse failure ⇒ graceful mock fallback).
3. `currentTime = LocalDateTime.parse(current.time)` (ISO_LOCAL_DATE_TIME).
   `sunrise0 = LocalDateTime.parse(daily.sunrise[0])`, `sunset0 = LocalDateTime.parse(daily.sunset[0])`.
   `isDay = !currentTime.isBefore(sunrise0) && currentTime.isBefore(sunset0)`.
4. `condition = wmoToCondition(current.weather_code)`; `bg_key = bgKey(condition, isDay)`.
5. `currentIdx` = index in `hourly.time[]` whose value == `current.time`; if none, `0`.
6. Build `Current`: `temp_c=round(temperature_2m)`, `feels_c=round(apparent_temperature)`, `condition`,
   `uv_index=round(daily.uv_index_max[0])`, `humidity=relative_humidity_2m`,
   `pressure=round(surface_pressure)`, `visibility=round(hourly.visibility[currentIdx])`,
   `wind=round(wind_speed_10m)`, `sunrise=hhmm(daily.sunrise[0])`, `sunset=hhmm(daily.sunset[0])`,
   `city=city.name ?: localizer.getOrDefault("city.default","Москва")`, `bg_key`.
7. `hourly`: 24 points starting at `currentIdx` (clamp end to array size; if fewer than 24 remain,
   take what remains — do NOT wrap). For offset `k` (0-based): `time = if (k==0)
   localizer.getOrDefault("hourly.now","now") else hhmm(hourly.time[currentIdx+k])`;
   `temp_c=round(temperature_2m[i])`; `condition=wmoToCondition(weather_code[i])`.
8. `daily`: 7 points (i=0..6): `weekday=shortWeekday(daily.time[i], localizer)`;
   `temp_min=round(temperature_2m_min[i])`, `temp_max=round(temperature_2m_max[i])`,
   `condition=wmoToCondition(weather_code[i])`, `precip_prob=precipitation_probability_max[i] ?: 0`.
9. `today = DayForecast(temp_c=current.temp_c, temp_feels=current.feels_c, condition=current.condition, city=current.city)`.
   `tomorrow = DayForecast(temp_c=round(daily.temperature_2m_max[1]),
   temp_feels=round(daily.temperature_2m_min[1]), condition=wmoToCondition(daily.weather_code[1]), city=current.city)`.
10. Assemble & return `WeatherData`.

Helpers (put in WmoMapping.kt or a `TimeFmt.kt`):
- `hhmm(iso: String): String = LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH:mm"))`
- `round(d: Double): Int = Math.round(d).toInt()` (read numeric nodes via `node.asDouble()`).
- `shortWeekday(isoDate: String, loc: Localizer): String` — `LocalDate.parse(isoDate).dayOfWeek`
  → key: MONDAY→`weekday.mon`, …, SUNDAY→`weekday.sun` → `loc.getOrDefault(key, fallback)`.

### B6. Geocoder — CREATE `backend/src/main/kotlin/workshop/weather/Geocoder.kt`
```kotlin
interface Geocoder {
    suspend fun search(query: String, lang: String): List<CityHit>
    companion object {
        fun create(): Geocoder =
            if (System.getProperty("weather.source") == "mock") MockGeocoder
            else OpenMeteoGeocoder(OpenMeteoClient())   // may reuse the same shared client; see notes
    }
}
```
- `object MockGeocoder`: `search = CityRegistry.search(query, lang)`.
- `class OpenMeteoGeocoder(client)`: `try` geocode → map `results[]` to `CityHit(name=node.name +
  (", " + admin1 if present), lat=node.latitude, lon=node.longitude)`, cap 8; on empty query return
  `emptyList()`; on any `Throwable` **fall back to `CityRegistry.search(query, lang)`**.
  (Open-Meteo geocoding response: top-level `results` array; each has `name`, `latitude`,
  `longitude`, optional `admin1`, `country`. If `results` missing → empty list.)

### B7. `WeatherServant` — MODIFY `backend/src/main/kotlin/workshop/servant/WeatherServant.kt`
- Constructor gains defaults:
  `class WeatherServant(private val weatherProvider: WeatherProvider = WeatherProvider.create(),
   private val geocoder: Geocoder = Geocoder.create())`.
- Change `fun handle(lang)` → `suspend fun handle(lang: String, city: CityParam): String`:
  `val weatherData = weatherProvider.provide(city, localizer)` (replaces `MockWeatherDataProvider.provide()`);
  rest of envelope assembly unchanged (still calls the 3 renderers; `WeatherMainRenderer(weatherData, localizer)`).
- ADD `suspend fun weatherJson(lang: String, city: CityParam): String`:
  `val wd = weatherProvider.provide(city, localizer(lang));
   return JsonFormat.printer().preservingProtoFieldNames().print(wd)` (snake_case). Import
  `com.google.protobuf.util.JsonFormat`.
- ADD `suspend fun citySearch(query: String, lang: String): String`:
  1. `val hits = geocoder.search(query, lang)`.
  2. Build items: if `hits.isEmpty()` → one `text(text = localizer.getOrDefault("city.search.empty", …),
     width = wrapContentSize())`. Else one `text` per hit with `action = action(logId = "set_city",
     url = url("weather-app://set_city?lat=${hit.lat}&lon=${hit.lon}&name=${urlEncode(hit.name)}"))`,
     `text = hit.name`, `width = wrapContentSize()`. Keep styling MINIMAL (Worktree B restyles).
  3. `val dp = divanPatch { patch(changes = listOf(patchChange(id = "city_search_results", items = items))) }`.
  4. `return mapper.writeValueAsString(dp.patch)`.
  - `urlEncode = java.net.URLEncoder.encode(s, "UTF-8")`.
  - Imports: `divkit.dsl.divanPatch`, `divkit.dsl.patch`, `divkit.dsl.patchChange`, `divkit.dsl.text`,
    `divkit.dsl.action`, `divkit.dsl.url`, `divkit.dsl.wrapContentSize`.
  - NOTE: `items` are built inside the `divanPatch { }` scope receiver (they are `DivScope.text(...)`),
    so construct them INSIDE the lambda (the lambda receiver is the `DivScope`). Restructure so the
    `text(...)` calls run inside `divanPatch { patch(changes = listOf(patchChange(id=…, items = <build here>))) }`.
- Add a private `fun localizer(lang: String) = Localizer(lang)` helper if convenient.

### B8. `Application.kt` — MODIFY routes
Replace the `routing { }` body with (keep `/ping`):
- `GET /document`: parse `lang` (as today: only `ru|en`, else `ru`); parse
  `lat=queryParameters["lat"]?.toDoubleOrNull()`, `lon=…["lon"]?.toDoubleOrNull()`,
  `name=queryParameters["name"]`; `city = if (lat!=null && lon!=null) CityParam(lat,lon,name)
  else CityRegistry.DEFAULT`; `respondText(servant.handle(lang, city), ContentType.Application.Json)`.
- `GET /weather-json`: same lang/city parsing; `respondText(servant.weatherJson(lang, city),
  ContentType.Application.Json)`.
- `GET /city-search`: `q = queryParameters["q"] ?: ""`; lang as above;
  `respondText(servant.citySearch(q, lang), ContentType.Application.Json)`.

### B9. Delete legacy provider
DELETE `backend/src/main/kotlin/workshop/mock/MockWeatherDataProvider.kt` (and the now-empty
`workshop/mock` dir). Nothing else references it after B7.

### B10. Strings — MODIFY `strings_ru.json` and `strings_en.json`
ADD these keys (do not remove existing). Exact values in §D3.

### B11. Build config — MODIFY `backend/build.gradle.kts`
- `val divanVersion = "32.57.0"` (was `32.6.0`).
- Add dependencies:
  `implementation("io.ktor:ktor-client-core:$ktorVersion")`,
  `implementation("io.ktor:ktor-client-cio:$ktorVersion")`,
  `implementation("com.google.protobuf:protobuf-java-util:4.29.3")`.
- In `tasks.test { useJUnit(); systemProperty("weather.source", "mock") }`.
- Mirror the three new libs + `protobuf-java-util` version into `gradle/libs.versions.toml`
  (documentation only; `build.gradle.kts` uses inline coords). Bump the catalog note if needed —
  the catalog already says `divan = "32.57.0"`, leave it.

### B12. Smoke test — MODIFY `backend/src/test/kotlin/workshop/ApplicationSmokeTest.kt`
Keep all existing tests (they stay green because `weather.source=mock` ⇒ deterministic, no network,
`today`/`tomorrow` still present with `condition.CLOUDY`/`CLEAR` and `day.today`). ADD:
- `weather-json exposes current/hourly/daily/bg_key`: GET `/weather-json?lang=ru`, 200, body contains
  `"current"`, `"hourly"`, `"daily"`, `"bg_key"`, and `"cloudy_day"`.
- `city-search returns a divpatch`: GET `/city-search?q=Mos&lang=en`, 200, body contains `"changes"`,
  `city_search_results`, `weather-app://set_city?`, and `Moscow`.
- `city-search empty query returns empty-state patch`: GET `/city-search?q=&lang=ru`, 200, body
  contains `"changes"` and the localized empty string (assert it contains `city_search_results`).
- `document still renders legacy today/tomorrow`: GET `/document?lang=ru`, 200, body contains
  `Сегодня` and `Завтра` (unchanged behavior through the untouched renderer).

---

## C. Files to create / change (summary)
CREATE (all package `workshop.weather`):
`weather/WmoMapping.kt`, `weather/City.kt`, `weather/OpenMeteoClient.kt`, `weather/WeatherProvider.kt`,
`weather/MockWeatherProvider.kt`, `weather/OpenMeteoWeatherProvider.kt`, `weather/Geocoder.kt`.
MODIFY: `proto/weather_data.proto`, `servant/WeatherServant.kt`, `Application.kt`,
`resources/strings/strings_ru.json`, `resources/strings/strings_en.json`, `build.gradle.kts`,
`gradle/libs.versions.toml`, `src/test/kotlin/workshop/ApplicationSmokeTest.kt`, `plan/contract.md`.
DELETE: `mock/MockWeatherDataProvider.kt`.

---

## D. GOLDEN / REFERENCE VALUES (single pinned block)

### D1. bg_key — the only 10 legal values
`sunny_day sunny_night cloudy_day cloudy_night rain_day rain_night storm_day storm_night fog_day fog_night`
Raw image URL for a bg_key (documented for the client; NOT built on the backend):
```
https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_{bg_key}.png
# e.g. bg_key="cloudy_night" → .../S3/background_cloudy_night.png
# popup image (task 7): .../S3/popup_image.png
```

### D2. Exact DivPatch response (with 1 hit, e.g. `/city-search?q=Mos&lang=en`)
```json
{"changes":[{"id":"city_search_results","items":[
  {"type":"text","text":"Moscow","width":{"type":"wrap_content"},
   "action":{"log_id":"set_city","url":"weather-app://set_city?lat=55.7558&lon=37.6173&name=Moscow"}}
]}]}
```
(Field order/extra defaults may differ — assert on substrings, not byte-equality. `name` is
URL-encoded; a space becomes `+`.) Empty results → one text item with the `city.search.empty` string.

### D3. New string keys (ru | en)
```
condition.FOG            Туман                 | Fog
weather.uv               УФ-индекс             | UV index
weather.uv.low           Низкий                | Low
weather.uv.moderate      Умеренный             | Moderate
weather.uv.high          Высокий               | High
weather.uv.very_high     Очень высокий         | Very high
weather.uv.extreme       Экстремальный         | Extreme
weather.sunrise          Восход                | Sunrise
weather.sunset           Закат                 | Sunset
weather.humidity         Влажность             | Humidity
weather.pressure         Давление              | Pressure
weather.visibility       Видимость             | Visibility
weather.precipitation    Осадки                | Precipitation
weather.wind             Ветер                 | Wind
hourly.now               Сейчас                | Now
weekday.mon              Пн                    | Mon
weekday.tue              Вт                    | Tue
weekday.wed              Ср                    | Wed
weekday.thu              Чт                    | Thu
weekday.fri              Пт                    | Fri
weekday.sat              Сб                    | Sat
weekday.sun              Вс                    | Sun
unit.pressure            гПа                   | hPa
unit.visibility          км                    | km
unit.wind                км/ч                  | km/h
city.default             Москва                | Moscow
city.search.placeholder  Поиск города          | Search city
city.search.empty        Ничего не найдено     | Nothing found
```
Reuse existing key `feels_like` (Ощущается как / feels like) — do NOT add a duplicate.
UV band thresholds (for Stage 1, documented only): 0–2 low, 3–5 moderate, 6–7 high, 8–10 very_high, ≥11 extreme.

### D4. Divan API facts (verified in R-32.57 — cite if kopatel is unsure)
- `fun divanPatch(init: DivScope.() -> Patch): DivanPatch` — `divan-dsl/src/main/kotlin/divkit/dsl/DivanPatch.kt`.
  `.patch` serializes (Jackson `@JsonAnyGetter`) to `{"changes":[…]}`.
- `fun DivScope.patch(changes: List<Patch.Change>, …): Patch` and
  `fun DivScope.patchChange(id: String? = null, items: List<Div>? = null): Patch.Change`
  — `divan-dsl/src/generated/kotlin/divkit/dsl/Patch.kt`. Serializes id→`"id"`, items→`"items"`.
- `Text`/`Container` are `Div` (existing renderer already nests `text(...)` in `container(items=…)`).
- Client parses `{"changes":[{"id":…,"items":[{"type":"text",…}]}]}` —
  `div/src/test/java/com/yandex/div/core/view2/PatchErrorsReportingTest.kt`.
- `DivScope` ctor is `internal` — you MUST build items inside the `divanPatch { }` lambda.

---

## E. TEST PLAN

Automated (`cd backend && ./gradlew test` — offline via `weather.source=mock`):
- All pre-existing smoke assertions stay green.
- New assertions in §B12.

Manual curl (needs network; run the server `./gradlew run` in one shell):
```
# 1. Real weather envelope (legacy today/tomorrow now real):
curl -s 'http://localhost:8080/document?lang=ru&lat=51.5074&lon=-0.1278&name=London' | head -c 400
# 2. Real spine as JSON (expect current/hourly[24]/daily[7]/bg_key):
curl -s 'http://localhost:8080/weather-json?lang=en&lat=51.5074&lon=-0.1278&name=London' | python3 -m json.tool | head -40
#   → assert: "bg_key" matches ^(sunny|cloudy|rain|storm|fog)_(day|night)$ ; len(hourly)==24 ; len(daily)==7
# 3. Real geocoding DivPatch:
curl -s 'http://localhost:8080/city-search?q=Berl&lang=en'
#   → {"changes":[{"id":"city_search_results","items":[ … "weather-app://set_city?lat=52.52&lon=13.405&name=Berlin" … ]}]}
# 4. Default city (no lat/lon) → Moscow:
curl -s 'http://localhost:8080/document?lang=ru' | grep -o 'Москва' | head -1
```
Offline curl (`WEATHER=mock`): start with `./gradlew run -Dweather.source=mock` and repeat — must
return the deterministic mock (bg_key `cloudy_day`) without network.

---

## F. RISKS / EDGES / ASK-IF-STUCK
- **32.57.0 artifact resolution.** If `com.yandex.div:kotlin-json-builder:32.57.0` fails to resolve
  from Maven Central at build, STOP and report — do not silently downgrade (the DivPatch API needs it).
- **JsonFormat method.** Use exactly `JsonFormat.printer().preservingProtoFieldNames().print(msg)`.
  If that method is absent for protobuf 4.29.3, report the exact compile error (do not guess an
  alternative that changes field casing — snake_case keys are required by the tests).
- **Ktor client + server coexistence** (CIO client, Netty server) is fine; if any Ktor artifact
  version mismatch appears, keep all Ktor coords on `$ktorVersion` (3.1.3).
- **Suspend chain:** route handler lambdas are already `suspend`; make `handle`/`weatherJson`/
  `citySearch` and provider/geocoder methods `suspend`. Do NOT introduce `runBlocking`.
- **`daily`/`hourly` array bounds:** Open-Meteo returns ≥7 daily and ≥24 hourly with these params;
  still clamp `hourly` slice to `min(currentIdx+24, size)` and never wrap. If `currentIdx` not found,
  use 0.
- **precipitation_probability_max may be `null`** in the JSON array → coerce to 0.
- If any divan signature above does not compile as written, consult the cited source path in §D4
  before improvising; report the mismatch.

---

## G. BOUNDARIES / WHAT DEFERS
- **No renderer redesign.** `WeatherMainRenderer`/`WeatherSettingsRenderer`/`WeatherAboutRenderer`/
  `SharedTemplates` are NOT edited in Stage 0. Consuming `current`/`hourly`/`daily`/`bg_key` in the UI
  is Worktree A (Stage 1). Styling the `/city-search` patch rows is Worktree B. All client code
  (`app/**`, action handlers, Coil, extensions, custom views) is Stage 1 Worktree C.
- Removing the deprecated `today`/`tomorrow`/`DayForecast` is a Stage 1 task once the renderer moves
  to `current`.
- The `/weather-json` debug endpoint may be removed later; it exists only for Stage 0 acceptance.

---

## plan/contract.md UPDATES (also do these — Stage 0 owns this file)
Append/patch `plan/contract.md` with the authoritative glue below (do not delete existing sections):

1. **Name map** (new section) — copy the table verbatim:
   | Entity | Name | Set by | Read by |
   |---|---|---|---|
   | DivCustom sun phase | `custom_type: "sun_phase"` | native (C) | main JSON (A) |
   | Scroll-state extension | `id: "scroll_state"` | native (C) | gallery JSON (A) |
   | Header-collapsed var | `header_collapsed` (Boolean) | extension (C) | header exprs (A) |
   | Weather bg key | `bg_key` (String, §bg grammar) | backend data (Stage 0) | image underlay (A) |
   | Set-city action | `weather-app://set_city?lat=&lon=&name=` | UI (B) | handler (C) |
   | City-search action | `weather-app://city_search?q=` | input (B) | handler (C) → DivPatch |
   | Query variable | `city_query` (String) | input (B) | handler (C) |
2. **`/document` params:** `?lang=ru|en&lat=<double>&lon=<double>&name=<string>`; Moscow default when
   lat/lon absent.
3. **New endpoints:** `GET /city-search?q=&lang=` → DivPatch JSON (shape §D2, target id
   `city_search_results`); `GET /weather-json?lang=&lat=&lon=&name=` → proto-as-snake_case JSON (debug).
4. **DivPatch response shape:** `{"changes":[{"id":"city_search_results","items":[<Div>…]}]}`; each item
   carries `action.url = weather-app://set_city?lat=&lon=&name=<urlencoded>`.
5. **bg_key grammar + raw URL pattern:** `{sunny|cloudy|rain|storm|fog}_{day|night}`;
   `https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_{bg_key}.png`;
   popup image `.../S3/popup_image.png`. Include the WMO→condition→base table from §B2.
6. **Version bump:** update §10 — DivKit/divan `32.57.0`, app Kotlin `2.2.10`, backend Kotlin `2.1.20`.

---

## ORCHESTRATOR NOTES (implementer may skip)
- **Version discrepancy found:** `build.gradle.kts` currently pins divan `32.6.0` inline while the
  (unused) version catalog says `32.57.0`. Stage 0 bumps the inline coord to `32.57.0` per the agreed
  toolchain; this is REQUIRED for the DivPatch DSL. Flag if the orchestrator intended to keep 32.6.0.
- **Debug endpoint `/weather-json`** was added by умник-decision to give Stage 0 a clean HTTP
  acceptance surface for the new data without touching the renderer (renderer redesign is Stage 1).
  If you'd rather not ship a debug endpoint, the alternative acceptance is a `WeatherProviderTest`
  asserting `MockWeatherProvider.provide()` yields `current`, `hourly.size==24`, `daily.size==7`,
  `bg_key=="cloudy_day"`. Kept the endpoint because the modernization plan's verification literally
  asks a curl to show current/hourly/daily/bg_key.
- **Geocoder/OpenMeteoClient sharing:** two `OpenMeteoClient()` instances (one for provider, one for
  geocoder) each hold an HttpClient — acceptable for a workshop. If the orchestrator wants a single
  shared client, inject one `OpenMeteoClient` into both; low priority.
- **`name` param trust:** `name` is echoed into `current.city` and the `set_city` URL unescaped-then-
  re-encoded; it is display-only, no injection surface into DivKit expressions. Fine for a workshop.
- **This stage is NOT thin glue** — it's a real integration (proto regen + new HTTP client + parsing +
  mapping). Full planner→implementer→review ceremony is warranted.
```
