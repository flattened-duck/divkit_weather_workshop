# Shared Contract: DivKit Weather Workshop App

Canonical version. Both the Android app and the backend server must conform to this document.

---

## 1. Экраны приложения

| ID экрана  | Описание                                           | Источник JSON                                                                          |
|------------|----------------------------------------------------|----------------------------------------------------------------------------------------|
| `main`     | Сводка погоды: сегодня + завтра, header с кнопками | Бэкенд (`GET /document?lang=`) + fallback из assets (`document.json`)                  |
| `settings` | Переключатели темы, компактного режима, языка       | Бэкенд (`GET /document?lang=`) + fallback из assets (`document.json`)                  |
| `about`    | О приложении: версия, ссылки                        | Бэкенд (`GET /document?lang=`) + fallback из assets (`document.json`)                  |

> Все три экрана приходят в **одном ответе** бэкенда. Клиент разбирает его один раз
> и переключает экраны локально, не делая повторных запросов.
>
> <!-- Альтернатива — отдельные ручки на каждый экран; один общий ответ выгоднее,
>      так как templates/данные переиспользуются, а кол-во round-trip минимально. -->

---

## 2. Формат ответа бэкенда (`GET /document?lang=ru|en`)

Бэкенд возвращает единый JSON-конверт с общими шаблонами и картой экранов:

```json
{
  "templates": {
    "weather_card": { ... },
    "nav_button": { ... }
  },
  "screens": {
    "main":     { "log_id": "main_weather",    "states": [...] },
    "settings": { "log_id": "main_settings",   "states": [...] },
    "about":    { "log_id": "main_about",       "states": [...] }
  }
}
```

`templates` объявляются один раз и переиспользуются всеми экранами.
Каждый экран под ключом `screens.*` — это стандартный DivKit `card`-объект
(`log_id` + `states`). Строки в каждом экране локализованы под запрошенный `lang`.

---

## 3. Глобальные DivKit-переменные → Stored Values

Переменные состояния персистируются через встроенный механизм **DivKit Stored Values**
(SQLite через `div-storage`). SharedPreferences **не используются**.

| Имя переменной | Тип DivKit | Значения              | Персистенция          | Поведение при изменении                                                |
|----------------|------------|-----------------------|-----------------------|------------------------------------------------------------------------|
| `theme`        | `string`   | `"light"` \| `"dark"` | Stored Values (global) | Реакция через DivKit-выражения (цвета) — без рефетча                  |
| `compact`      | `boolean`  | `true` \| `false`     | Stored Values (global) | Реакция через DivKit-выражения (размеры/видимость) — без рефетча      |
| `lang`         | `string`   | `"ru"` \| `"en"`      | Stored Values (global) | **Рефетч** `GET /document?lang={value}`, переключение `DivData`       |

> **Ключевое педагогическое правило:**
> - `theme` и `compact` — чисто клиентские: layout реагирует на них через DivKit-выражения,
>   бэкенд не задействуется и рефетч не нужен.
> - `lang` требует рефетча `GET /document?lang={value}`, потому что строки
>   «запечены» на сервере и не меняются клиентскими выражениями.

---

## 4. Схема пользовательских URL-действий

Обрабатываются в `WeatherDivActionHandler` (переопределяет `handleActionUrl`).

| URL                                      | Семантика                                           | Реализация на клиенте                                |
|------------------------------------------|-----------------------------------------------------|------------------------------------------------------|
| `weather-app://navigate?screen=main`     | Перейти на главный экран                            | `WeatherNavigation.navigateTo(Screen.MAIN)`          |
| `weather-app://navigate?screen=settings` | Перейти на экран настроек                           | `WeatherNavigation.navigateTo(Screen.SETTINGS)`      |
| `weather-app://navigate?screen=about`    | Перейти на экран «О приложении»                     | `WeatherNavigation.navigateTo(Screen.ABOUT)`         |
| `weather-app://back`                     | Назад                                               | `activity.onBackPressedDispatcher.onBackPressed()`   |
| `weather-app://set_lang?value=ru`        | Сменить язык на русский + рефетч                    | Обновить stored value `lang`, сделать рефетч         |
| `weather-app://set_lang?value=en`        | Сменить язык на английский + рефетч                 | То же, `en`                                          |

Встроенные `div-action://set_stored_value` обрабатываются базовым `DivActionHandler` автоматически.
Для `theme` и `compact` бэкенд генерирует действия вида:

```json
{
  "type": "set_stored_value",
  "name": "theme",
  "value": { "type": "string", "value": "dark" },
  "lifetime": 2147483647,
  "scope": "global"
}
```

Для чтения хранимых значений в выражениях используются функции
`getStoredStringValue('theme', 'light')` и `getStoredBooleanValue('compact', false)`.

---

## 5. Персистенция через DivKit Stored Values

Встроенный механизм персистенции в открытом DivKit — **Stored Values** (SQLite).

### Запись (из JSON-действий, генерируется бэкендом)

Тип действия `set_stored_value` (JSON-сериализация `ActionSetStoredValue`).

Открытый DSL: `divkit.dsl.ActionSetStoredValue`
Путь в репо: `divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/ActionSetStoredValue.kt`

```kotlin
// Пример на divan DSL (в WeatherMainRenderer/WeatherSettingsRenderer)
import divkit.dsl.ActionSetStoredValue
import divkit.dsl.StringValue
import divkit.dsl.BooleanValue

// Установить theme = "dark"
actionSetStoredValue(
    name = "theme",
    value = stringValue(value = "dark"),
    lifetime = Int.MAX_VALUE,   // секунды; Int.MAX_VALUE ≈ 68 лет = «навсегда»
    scope = global,             // DivScope.global — константа из EnumValues.kt
)

// Установить compact = true
actionSetStoredValue(
    name = "compact",
    value = booleanValue(value = true),
    lifetime = Int.MAX_VALUE,
    scope = global,
)
```

Поля `ActionSetStoredValue`:
- `name: String` — имя хранимой переменной
- `value: TypedValue` — типизированное значение (`StringValue`, `BooleanValue`, `IntegerValue`, …)
- `lifetime: Int` — время хранения в **секундах** (отсчёт от момента записи); `Int.MAX_VALUE` ≈ навсегда
- `scope: ActionSetStoredValue.Scope` — `global` (не привязан к карточке) или `card` (привязан к `DivDataTag`)

### Чтение (в DivKit-выражениях)

Открытый DSL: функции `getStored*Value` в `divkit.dsl.expression.Functions`
Путь: `divkit/public/json-builder/kotlin/expression-dsl/src/main/kotlin/divkit/dsl/expression/Functions.kt`

```kotlin
// В expression-dsl (для формирования expression-строк на бэкенде)
import divkit.dsl.expression.getStoredStringValue
import divkit.dsl.expression.getStoredBooleanValue
import divkit.dsl.expression.getStoredIntegerValue

// Пример: цвет фона через stored value
val themeExpr = getStoredStringValue("theme".toExpression(), "light".toExpression())
// Генерирует выражение: @{getStoredStringValue('theme', 'light')}

// В raw JSON-строке (для assets-fallback):
// "@{getStoredStringValue('theme', 'light') == 'dark' ? '#FF2C2C2E' : '#FFF2F2F7'}"
```

Сигнатуры (из `Functions.kt`):
```kotlin
fun getStoredStringValue(param0: Expression<String>, param1: Expression<String>): Expression<String>
fun getStoredBooleanValue(param0: Expression<String>, param1: Expression<Boolean>): Expression<Boolean>
fun getStoredIntegerValue(param0: Expression<String>, param1: Expression<Long>): Expression<Long>
```

### Клиентское подключение (wiring)

`StoredValuesController` подключён к `Div2Component` через Dagger/Yatagan.
Путь: `divkit/public/client/android/div/src/main/java/com/yandex/div/core/expression/storedvalues/StoredValuesController.kt`

Хранилище (`div-storage`) создаётся **автоматически** через `DivStorageModule.provideDivStorageComponent(...)`,
который вызывается при инициализации `DivKit.configure(...)` / `Div2Context(...)`.

**Никакого ручного подключения `div-storage` не требуется.** Достаточно вызвать в
`Application.onCreate()`:

```kotlin
// Достаточно для работы stored values с персистенцией через перезапуск:
DivKit.configure(DivKitConfiguration.Builder().build())
```

Путь: `divkit/public/client/android/div/src/main/java/com/yandex/div/core/DivKit.kt`
Путь: `divkit/public/client/android/div/src/main/java/com/yandex/div/core/DivKitConfiguration.kt`

`DivStorageModule` (путь: `divkit/public/client/android/div/src/main/java/com/yandex/div/core/dagger/DivStorageModule.kt`)
создаёт `DivStorageComponent.create(context, ...)` если внешний не передан.
`DivStorageComponent` (путь: `divkit/public/client/android/div-storage/src/main/java/com/yandex/div/storage/DivStorageComponent.kt`)
хранит `rawJsonRepository` (SQLite) — именно через него stored values переживают перезапуск.

### Модель для `theme`/`compact`/`lang`

- **`theme` и `compact`**: переключатели в `settings.json` отправляют `set_stored_value` действия.
  Выражения в layouts читают значения через `getStoredStringValue`/`getStoredBooleanValue`.
  Рефетч **не нужен** — DivKit пересчитывает выражения в реальном времени.
- **`lang`**: кнопка в settings отправляет `weather-app://set_lang?value=en`.
  Клиент перехватывает в `WeatherDivActionHandler`, делает рефетч `GET /document?lang=en`,
  а затем сохраняет новое значение через собственный `set_stored_value`-action или
  напрямую через `StoredValuesController`. Рефетч **обязателен**, так как строки
  генерируются сервером для каждого языка.
- `WeatherVariableManager` **удаляется** — не нужен. Текущий `lang` читается из stored value
  в обработчике рефетча через `StoredValuesController.getStoredValue(...)`.

---

## 6. Backend API

### GET `/document`

| Query-параметр | Тип    | Значения       | Описание                   |
|----------------|--------|----------------|----------------------------|
| `lang`         | string | `ru` \| `en`  | Язык локализации строк     |

**Ответ:** JSON-конверт формата:

```json
{
  "templates": {
    "weather_card": { "type": "container", ... },
    "nav_button":   { "type": "text", "action": "$action", ... }
  },
  "screens": {
    "main":     { "log_id": "main_weather",  "states": [{ "state_id": 0, "div": { ... } }] },
    "settings": { "log_id": "main_settings", "states": [{ "state_id": 0, "div": { ... } }] },
    "about":    { "log_id": "main_about",    "states": [{ "state_id": 0, "div": { ... } }] }
  }
}
```

### GET `/ping`

Возвращает `"pong"` — healthcheck.

---

## 7. Bundled fallback asset

```
app/src/main/assets/
  document.json    # полный конверт (templates + screens) для offline/first-launch
```

Файл структурно идентичен ответу бэкенда. При отсутствии сети клиент парсит этот файл.
Данные в fallback локализованы на `ru` (язык по умолчанию).

---

## 8. Использование stored values в DivKit-выражениях (примеры)

```json
// Цвет фона реагирует на theme (stored value):
"background": [{"type": "solid", "color": "@{getStoredStringValue('theme', 'light') == 'dark' ? '#1C1C1E' : '#FFFFFF'}"}]

// Компактный режим скрывает блок:
"visibility": "@{getStoredBooleanValue('compact', false) ? 'gone' : 'visible'}"

// Размер шрифта в зависимости от compact:
"font_size": "@{getStoredBooleanValue('compact', false) ? 14 : 18}"
```

---

## 9. Структура проекта

Один git-репозиторий, два независимых Gradle-проекта:

```
divkit-weather-workshop/
  app/                # Android-приложение (открывается в Android Studio)
    settings.gradle.kts
    build.gradle.kts
    gradle/libs.versions.toml
    ...
  backend/            # Ktor-бэкенд
    settings.gradle.kts
    build.gradle.kts
    gradle/libs.versions.toml
    ...
  plan/               # Планы реализации
  README.md
```

Нет корневых `build.gradle.kts` / `settings.gradle.kts`. Студент открывает `app/` или
`backend/` как отдельный проект в IDE.

---

## 10. Версии

| Компонент      | Версия                                    |
|----------------|-------------------------------------------|
| DivKit Android | `32.57.0`                                 |
| DivKit group   | `com.yandex.div`                          |
| divan (backend) | `com.yandex.div:kotlin-json-builder:32.57.0` |
| minSdk         | 23                                        |
| targetSdk      | 35                                        |
| Kotlin (app)   | `2.2.10`                                  |
| Kotlin (backend) | `2.1.20`                                |
| Java toolchain | 17                                        |
| AGP            | `8.12.3`                                  |
| Ktor           | `3.1.3`                                   |

> Bumped in Stage 0: divan `32.6.0` → `32.57.0` (required for the `divanPatch`/`patch`/`patchChange`
> DivPatch DSL used by `GET /city-search`). App-side DivKit/Kotlin bump lands with the Stage 1
> client work; recorded here as the target version.

---

## 11. Stage 0 — Weather data spine (Open-Meteo) + city search

Added in Stage 0 (`backend/**` only; no `app/**` changes yet — client consumption is Stage 1).

### 11.1 Name map

| Entity | Name | Set by | Read by |
|---|---|---|---|
| DivCustom sun phase | `custom_type: "sun_phase"` | native (C) | main JSON (A) |
| Scroll-state extension | `id: "scroll_state"` | native (C) | gallery JSON (A) |
| Header-collapsed var | `header_collapsed` (Boolean) | extension (C) | header exprs (A) |
| Weather bg key | `bg_key` (String, §bg grammar) | backend data (Stage 0) | image underlay (A) |
| Set-city action | `weather-app://set_city?lat=&lon=&name=` | UI (B) | handler (C) |
| City-search action | `weather-app://city_search?q=` | input (B) | handler (C) → DivPatch |
| Query variable | `city_query` (String) | input (B) | handler (C) |

### 11.2 `/document` params

`GET /document?lang=ru|en&lat=<double>&lon=<double>&name=<string>`. Moscow default
(`CityRegistry.DEFAULT`, 55.7558/37.6173) when `lat`/`lon` are absent or unparsable.

### 11.3 New endpoints

- `GET /city-search?q=&lang=` → DivPatch JSON (shape §11.4, target id `city_search_results`).
- `GET /weather-json?lang=&lat=&lon=&name=` → `WeatherData` proto as snake_case JSON (debug
  endpoint; may be removed once the client consumes the real screens in Stage 1).

### 11.4 DivPatch response shape

```json
{"changes":[{"id":"city_search_results","items":[<Div>…]}]}
```

Each item carries `action.url = weather-app://set_city?lat=&lon=&name=<urlencoded>`. Empty query
or empty results → a single text item with the localized `city.search.empty` string, same shape.

### 11.5 `bg_key` grammar + raw URL pattern

`bg_key` = `{base}_{day|night}`, `base ∈ {sunny,cloudy,rain,storm,fog}`. 10 legal values:
`sunny_day sunny_night cloudy_day cloudy_night rain_day rain_night storm_day storm_night fog_day fog_night`.

Raw background image URL (documented for the client; NOT built on the backend):
```
https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_{bg_key}.png
# popup image: .../S3/popup_image.png
```

WMO weather code → `ConditionCode` → `bg_key` base (any unmapped code → `FOG` → `fog`):

| WMO codes | ConditionCode | bg base |
|---|---|---|
| 0, 1 | CLEAR | sunny |
| 2, 3 | CLOUDY | cloudy |
| 45, 48 | FOG | fog |
| 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 | RAIN | rain |
| 71, 73, 75, 77, 85, 86 | SNOW | cloudy |
| 95, 96, 99 | THUNDER | storm |
| (anything else) | FOG | fog |

Day/night is computed by comparing `current.time` to `daily.sunrise[0]`/`daily.sunset[0]`, not by
Open-Meteo's `is_day` field (that field is only a cross-check, unused in the mapping).
