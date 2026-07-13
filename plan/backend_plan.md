# Backend Plan: DivKit Weather Workshop

## Задача

Разработать standalone Kotlin/Ktor бэкенд для воркшопа «BDUI и DivKit». Бэкенд имитирует
реальный поток обработки запросов как в MAPI/bromapi, но без проприетарной инфраструктуры
Arcadia: нет AppHost, нет пакета `tovarisch`, нет Koin, нет REX.

Единственный основной эндпоинт — `GET /document?lang=ru|en` — возвращает **единый JSON-конверт**
с общими `templates` и картой трёх экранов (`main`, `settings`, `about`). Все строки
локализованы под запрошенный `lang`. Шаблоны переиспользуются всеми тремя экранами
(демонстрирует выигрыш в размере ответа). Логика темы (light/dark) и compact живёт на клиенте
через DivKit Stored Values — бэкенд лишь генерирует `set_stored_value`-действия для переключателей
в экране settings.

---

## Структура проекта

```
divkit-weather-workshop/
├── backend/
│   ├── settings.gradle.kts           ← rootProject.name = "divkit-weather-backend"
│   ├── build.gradle.kts              ← Ktor + divan + jackson + protobuf
│   ├── gradle/
│   │   └── libs.versions.toml        ← version catalog
│   └── src/
│       └── main/
│           ├── kotlin/
│           │   └── workshop/
│           │       ├── Application.kt             ← Ktor embeddedServer, routing
│           │       ├── servant/
│           │       │   └── WeatherServant.kt       ← оркестрация: lang → DocumentEnvelope
│           │       ├── renderer/
│           │       │   ├── WeatherMainRenderer.kt  ← divan-DSL: экран main
│           │       │   ├── WeatherSettingsRenderer.kt ← divan-DSL: экран settings
│           │       │   └── WeatherAboutRenderer.kt ← divan-DSL: экран about
│           │       ├── templates/
│           │       │   └── SharedTemplates.kt      ← переиспользуемые шаблоны divan
│           │       ├── l10n/
│           │       │   └── Localizer.kt            ← таблицы строк ru/en + getOrDefault
│           │       └── mock/
│           │           └── MockWeatherDataProvider.kt ← фиктивный proto-ответ
│           ├── proto/
│           │   └── weather_data.proto
│           └── resources/
│               └── strings/
│                   ├── strings_ru.json
│                   └── strings_en.json
└── app/                               # отдельный Gradle-проект Android-клиента
```

### Ключевые зависимости (`build.gradle.kts`)

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"
    id("com.google.protobuf") version "0.9.4"
    application
}

kotlin {
    jvmToolchain(17)
}

val ktorVersion = "3.1.3"
val divanVersion = "32.6.0"

dependencies {
    // Ktor
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    // DivKit JSON-builder (divan) — публично доступен на Maven Central
    // группа: com.yandex.div, артефакт: kotlin-json-builder
    // (подтверждено в: divkit/public/json-builder/kotlin/buildSrc/src/main/kotlin/publishing-project.gradle.kts)
    implementation("com.yandex.div:kotlin-json-builder:$divanVersion")

    // JSON-сериализация (divan использует Jackson внутри)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    // Protobuf runtime
    implementation("com.google.protobuf:protobuf-kotlin:4.29.3")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.29.3" }
    generateProtoTasks { all().forEach { it.builtins { id("kotlin") } } }
}

application {
    mainClass.set("workshop.ApplicationKt")
}
```

**`divan` на Maven Central?** Да. Координаты `com.yandex.div:kotlin-json-builder` публикуются
в Maven Central (Sonatype Nexus). Версия `32.6.0` — последняя публично доступная.
Версия `32.54.0` не опубликована. Вендоринг не требуется.

---

## Открытые DSL-модули (подтверждено в репо)

| Модуль | Пакет | Путь в репо |
|--------|-------|-------------|
| `divan-dsl` | `divkit.dsl.*` | `divkit/public/json-builder/kotlin/divan-dsl/src/` |
| `expression-dsl` | `divkit.dsl.expression.*` | `divkit/public/json-builder/kotlin/expression-dsl/src/` |

Публичные хелперы из `divan-dsl`:
- `data`, `container`, `text`, `image`, `action`, `url`, `solidBackground`, `border`,
  `edgeInsets`, `fixedSize`, `wrapContentSize`, `color`, `divan`
- `divkitVariable(variable: Var<T>, value: T): Variable` — объявление переменной
  (из `divan-dsl/src/main/kotlin/divkit/dsl/expression/Converters.kt`)
- `actionSetStoredValue(name, value, lifetime, scope)` — действие записи stored value
  (из `divan-dsl/src/generated/kotlin/divkit/dsl/ActionSetStoredValue.kt`)
- `stringValue(value)`, `booleanValue(value)` — типы для `actionSetStoredValue`
  (из `divan-dsl/src/generated/kotlin/divkit/dsl/StringValue.kt`, `BooleanValue.kt`)
- `global` — значение scope (из `divan-dsl/src/generated/kotlin/divkit/dsl/EnumValues.kt`, строки 118–123)

Публичные хелперы из `expression-dsl`:
- `"theme".stringVariable()`, `"compact".booleanVariable()` — `Var<T>` для переменных
  (из `expression-dsl/src/main/kotlin/divkit/dsl/expression/Expression.kt`)
- `equalTo`, `ifElse`, `divanExpression`, `divkitVariable` — выражения
- `getStoredStringValue`, `getStoredBooleanValue`, `getStoredIntegerValue` — чтение stored values
  (из `expression-dsl/src/main/kotlin/divkit/dsl/expression/Functions.kt`)

Все эти хелперы — **открытый код**, без зависимости от mapi-specific расширений.

---

## Этапы

---

### Этап 1: Scaffolding — Gradle-проект, Ktor Hello World

**Что реализовать:**

- Создать `backend/settings.gradle.kts` с `rootProject.name = "divkit-weather-backend"`.
- Создать `backend/build.gradle.kts` с зависимостями из раздела «Структура проекта».
  Подключить Java toolchain 17: `kotlin { jvmToolchain(17) }`.
- Создать `backend/gradle/libs.versions.toml` (ktor `3.1.3`, divan `32.6.0`, jackson `2.18.3`, protobuf `4.29.3`).
- Создать `Application.kt`:
  ```kotlin
  package workshop

  import io.ktor.server.engine.embeddedServer
  import io.ktor.server.netty.Netty
  import io.ktor.server.application.Application
  import io.ktor.server.routing.routing
  import io.ktor.server.response.respondText
  import io.ktor.server.routing.get

  fun main() {
      embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
  }

  fun Application.module() {
      routing {
          get("/ping") { call.respondText("pong") }
      }
  }
  ```

**Файлы:**
- `backend/settings.gradle.kts` — создать
- `backend/build.gradle.kts` — создать
- `backend/gradle/libs.versions.toml` — создать
- `backend/src/main/kotlin/workshop/Application.kt` — создать

**Референс в mapi:**
- `/Users/the-leo/arcadia/portal/mapi/app/src/main/java/Application.kt` — структура
  `embeddedServer(Netty, port = 8080)` + установка модулей

**Проверка:**
```bash
cd backend && ./gradlew run &
curl http://localhost:8080/ping   # → "pong"
```

---

### Этап 2: Protobuf-схема `WeatherData` и mock-провайдер

**Что реализовать:**

```protobuf
// src/main/proto/weather_data.proto
syntax = "proto3";
package workshop;
option java_package = "workshop.proto";

enum ConditionCode {
  CLEAR   = 0;
  CLOUDY  = 1;
  RAIN    = 2;
  SNOW    = 3;
  THUNDER = 4;
}

message DayForecast {
  int32         temp_c      = 1;
  int32         temp_feels  = 2;
  ConditionCode condition   = 3;
  string        city        = 4;
}

message WeatherData {
  DayForecast today    = 1;
  DayForecast tomorrow = 2;
}
```

Создать `MockWeatherDataProvider.kt` с хардкодированными данными (Москва, +17°C).

**Файлы:**
- `backend/src/main/proto/weather_data.proto` — создать
- `backend/src/main/kotlin/workshop/mock/MockWeatherDataProvider.kt` — создать

**Референс в mapi:**
- `/Users/the-leo/arcadia/portal/mapi/api/` — исходные `.proto` файлы MAPI для понимания
  структуры proto-блоков

**Проверка:**
```bash
cd backend && ./gradlew generateProto
./gradlew compileKotlin
```

---

### Этап 3: Localizer — таблицы строк ru/en с getOrDefault

**Что реализовать:**

Создать `Localizer(lang: String)` с методом `getOrDefault(key: String, default: String): String`.
Загружает строки из `resources/strings/strings_$lang.json` (Jackson).

Строковые таблицы:

`strings_ru.json`:
```json
{
  "screen.main.title": "Погода",
  "day.today": "Сегодня",
  "day.tomorrow": "Завтра",
  "condition.CLEAR": "Ясно",
  "condition.CLOUDY": "Облачно",
  "condition.RAIN": "Дождь",
  "condition.SNOW": "Снег",
  "condition.THUNDER": "Гроза",
  "feels_like": "ощущается как",
  "nav.settings": "Настройки",
  "nav.about": "О приложении",
  "settings.title": "Настройки",
  "settings.theme.light": "Светлая",
  "settings.theme.dark": "Тёмная",
  "settings.compact.on": "Компактный",
  "settings.compact.off": "Обычный",
  "settings.lang.ru": "Русский",
  "settings.lang.en": "English",
  "about.title": "О приложении",
  "about.version": "Версия 1.0.0",
  "about.repo": "GitHub"
}
```

`strings_en.json` — аналогично на английском.

**Файлы:**
- `backend/src/main/kotlin/workshop/l10n/Localizer.kt` — создать
- `backend/src/main/resources/strings/strings_ru.json` — создать
- `backend/src/main/resources/strings/strings_en.json` — создать

**Референс в mapi:**
- `/Users/the-leo/arcadia/portal/mapi/data/src/main/kotlin/Localization.kt` — метод
  `getOrDefault(key, default)` — идентичный контракт

**Проверка:**
```bash
cd backend && ./gradlew test --tests "workshop.l10n.LocalizerTest"
```

---

### Этап 4: SharedTemplates — переиспользуемые шаблоны divan

**Что реализовать:**

Создать `SharedTemplates.kt` — объект с двумя переиспользуемыми шаблонами:

1. `weather_card` — шаблон карточки погоды (используется в экране `main`):
   ```kotlin
   // В divan-DSL шаблон — это container с параметрами через $-ссылки
   // Минимальная реализация: именованный контейнер с фиксированной структурой
   ```

2. `nav_button` — шаблон навигационной кнопки (используется в `main`, `settings`):
   ```kotlin
   // text с action, параметры $action и $text
   ```

Сериализация шаблонов — ключ в `"templates"` объекте конверта.

**Педагогический смысл**: один и тот же `templates` объект разделяется между тремя экранами.
Клиент парсит его один раз через `DivParsingEnvironment.parseTemplates(templates)` и
переиспользует при создании `DivData` для каждого экрана — выигрыш в размере ответа и
времени парсинга.

**Ключевые API (divan DSL):**
```kotlin
import divkit.dsl.*
import divkit.dsl.expression.*
import com.fasterxml.jackson.databind.json.JsonMapper

// Шаблоны объявляются в divan-блоке через template(...)
// Ссылки на переменные шаблона в divan DSL — через TemplateScope
```

**Файлы:**
- `backend/src/main/kotlin/workshop/templates/SharedTemplates.kt` — создать

**Референс в open DivKit:**
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/test/kotlin/divkit/dsl/` —
  тесты divan DSL с примерами шаблонов
- `/Users/the-leo/arcadia/divkit/public/samples/cloud-functions/kotlin-dsl/src/main/kotlin/cloud/divkit/Handler.kt` —
  минимальный рабочий пример `divan { data(logId=..., div=container(...)) }` + полный список imports

**Проверка:**
```bash
cd backend && ./gradlew compileKotlin
```

---

### Этап 5: WeatherMainRenderer — divan-DSL layout экрана main

**Что реализовать:**

Создать `WeatherMainRenderer(weatherData: WeatherData, localizer: Localizer)`.
Метод `render(): Pair<String, Divan>` → ключ `"main"` + объект `Divan`.

Ключевые аспекты:

1. **Выражения через stored values** (не DivKit-переменные, а читаемые хранимые значения):

   ```kotlin
   import divkit.dsl.expression.*

   // Цвет фона через getStoredStringValue
   val isDark = getStoredStringValue("theme".toExpression(), "light".toExpression())
       .equalTo("dark".toExpression())
   val cardBg = isDark
       .ifElse(onMatch = "#FF2C2C2E", onMismatch = "#FFF2F2F7")
       .divanExpression<divkit.dsl.Color>()

   solidBackground().evaluate(color = cardBg)
   ```

   Путь: `divkit/public/json-builder/kotlin/expression-dsl/src/main/kotlin/divkit/dsl/expression/Functions.kt`

2. **Навигационные кнопки** (URL через `weather-app://navigate?screen=`):

   ```kotlin
   private fun DivScope.navButton(labelKey: String, screen: String): Div =
       text(
           text = l10n.getOrDefault(labelKey, labelKey),
           fontSize = 14,
           action = action(
               logId = "nav_$screen",
               url = url("weather-app://navigate?screen=$screen"),
           )
       )
   // Вызов:
   navButton("nav.settings", "settings")   // → "weather-app://navigate?screen=settings"
   navButton("nav.about",    "about")      // → "weather-app://navigate?screen=about"
   ```

3. **Структура экрана** (вертикальный контейнер):
   ```
   container(vertical)
     ├── text: заголовок (локализован)
     ├── container(horizontal) ← карточки today + tomorrow (через шаблон weather_card)
     └── container(horizontal) ← навигационные кнопки (через шаблон nav_button)
   ```

**Файлы:**
- `backend/src/main/kotlin/workshop/renderer/WeatherMainRenderer.kt` — создать

**Референс в open DivKit:**
- `/Users/the-leo/arcadia/divkit/public/samples/cloud-functions/kotlin-dsl/src/main/kotlin/cloud/divkit/Handler.kt` —
  минимальный рабочий пример с полным списком imports из `divkit.dsl.*`
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/test/kotlin/divkit/dsl/Utils.kt` —
  паттерн сериализации `JsonMapper.builder().build().writeValueAsString(card)`
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/expression-dsl/src/main/kotlin/divkit/dsl/expression/Functions.kt` —
  `getStoredStringValue`, `getStoredBooleanValue`

**Проверка:**
```bash
cd backend && ./gradlew test --tests "workshop.renderer.WeatherMainRendererTest"
# JSON содержит "weather-app://navigate?screen=settings"
# JSON содержит "weather-app://navigate?screen=about"
# JSON содержит выражение getStoredStringValue
```

---

### Этап 6: WeatherSettingsRenderer — экран настроек с set_stored_value

**Что реализовать:**

Создать `WeatherSettingsRenderer(localizer: Localizer)`. Метод `render(): Pair<String, Divan>`.

Ключевые аспекты:

1. **Переключатель темы** — два варианта (`light`/`dark`), каждый содержит действие
   `actionSetStoredValue`:

   ```kotlin
   import divkit.dsl.ActionSetStoredValue
   import divkit.dsl.stringValue
   import divkit.dsl.booleanValue

   // Кнопка "Светлая тема"
   action(
       logId = "set_theme_light",
       url = url("div-action://set_stored_value"),  // или как typed action
       typed = actionSetStoredValue(
           name = "theme",
           value = stringValue(value = "light"),
           lifetime = Int.MAX_VALUE,
           scope = global,      // DivScope.global — из EnumValues.kt
       )
   )
   ```

   Активный вариант выделяется через выражение:
   `@{getStoredStringValue('theme', 'light') == 'light'}`

2. **Переключатель compact** — аналогично с `booleanValue(true|false)`.

3. **Переключатель языка** — кнопки с `weather-app://set_lang?value=ru|en`.

4. **Кнопка «Назад»** — `weather-app://back`.

**Файлы:**
- `backend/src/main/kotlin/workshop/renderer/WeatherSettingsRenderer.kt` — создать

**Референс в open DivKit:**
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/ActionSetStoredValue.kt` —
  полное определение: поля `name`, `value`, `lifetime` (Int, секунды), `scope`
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/StringValue.kt` —
  `fun DivScope.stringValue(value: String): StringValue`
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/BooleanValue.kt` —
  `fun DivScope.booleanValue(value: Boolean): BooleanValue`
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/EnumValues.kt`
  строки 118–123 — `GlobalEnumValue` (scope `"global"`) и `CardEnumValue` (scope `"card"`)

**Проверка:**
```bash
cd backend && ./gradlew test --tests "workshop.renderer.WeatherSettingsRendererTest"
# JSON содержит "set_stored_value" и "name":"theme"
# JSON содержит "weather-app://set_lang?value=en"
```

---

### Этап 7: WeatherAboutRenderer — экран «О приложении»

**Что реализовать:**

Создать `WeatherAboutRenderer(localizer: Localizer)`. Метод `render(): Pair<String, Divan>`.

Содержимое: название приложения (локализовано), хардкодированная версия `1.0.0`,
ссылка на GitHub-репозиторий воркшопа, кнопка «Назад» (`weather-app://back`).

**Файлы:**
- `backend/src/main/kotlin/workshop/renderer/WeatherAboutRenderer.kt` — создать

**Проверка:**
```bash
cd backend && ./gradlew compileKotlin
```

---

### Этап 8: WeatherServant — оркестрация, единый конверт

**Что реализовать:**

`WeatherServant.kt` — принимает `lang: String`, возвращает `String` (JSON-конверт).

Алгоритм:
1. Создать `Localizer(lang)`.
2. Получить `MockWeatherDataProvider.provide()`.
3. Получить рендеры всех трёх экранов:
   - `WeatherMainRenderer(weatherData, localizer).render()` → `("main", Divan)`
   - `WeatherSettingsRenderer(localizer).render()` → `("settings", Divan)`
   - `WeatherAboutRenderer(localizer).render()` → `("about", Divan)`
4. Получить `SharedTemplates.build()` → `Map<String, Any>` (templates-объект).
5. Собрать JSON-конверт вручную через `JsonMapper`:
   ```kotlin
   val envelope = mapOf(
       "templates" to sharedTemplates,
       "screens" to mapOf(
           "main"     to mainMapper.writeValueAsString(mainDivan),  // или напрямую JsonNode
           "settings" to settingsMapper.writeValueAsString(settingsDivan),
           "about"    to aboutMapper.writeValueAsString(aboutDivan),
       )
   )
   return mapper.writeValueAsString(envelope)
   ```

**Файлы:**
- `backend/src/main/kotlin/workshop/servant/WeatherServant.kt` — создать

**Референс в mapi:**
- `/Users/the-leo/arcadia/portal/mapi/app/src/main/java/servants/MordaResponseServant.kt` —
  паттерн servant: разбор входных данных → рендеры → JSON

**Проверка:**
```bash
cd backend && ./gradlew test --tests "workshop.servant.WeatherServantTest"
# handle("ru") возвращает JSON с ключами "templates", "screens"
# screens содержит ключи "main", "settings", "about"
# handle("en") содержит "Today" вместо "Сегодня"
```

---

### Этап 9: HTTP-роутинг — эндпоинт `/document`

**Что реализовать:**

```kotlin
fun Application.module() {
    val servant = WeatherServant()

    routing {
        get("/document") {
            val lang = call.request.queryParameters["lang"]
                ?.takeIf { it in listOf("ru", "en") } ?: "ru"
            val json = servant.handle(lang)
            call.respondText(
                text = json,
                contentType = ContentType.Application.Json,
            )
        }
        get("/ping") { call.respondText("pong") }
    }
}
```

**Файлы:**
- `backend/src/main/kotlin/workshop/Application.kt` — дополнить routing + servant

**Проверка:**
```bash
cd backend && ./gradlew run &
curl "http://localhost:8080/document?lang=ru" | python3 -m json.tool | grep -c '"screens"'
# → 1 (ключ screens присутствует)
curl "http://localhost:8080/document?lang=ru" | python3 -m json.tool | grep templates
# → ключ templates
curl "http://localhost:8080/document?lang=en" | python3 -m json.tool | grep Today
# → строка с "Today"
curl "http://localhost:8080/document?lang=ru" | python3 -m json.tool | grep navigate
# → "weather-app://navigate?screen=settings" и "weather-app://navigate?screen=about"
curl "http://localhost:8080/ping"
# → "pong"
```

---

### Этап 10: Финальная проверка и smoke-тест

**Что реализовать:**

```kotlin
// src/test/kotlin/workshop/ApplicationSmokeTest.kt
class ApplicationSmokeTest {
    @Test
    fun `document returns templates and all three screens`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=ru")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"templates\""))
        assertTrue(body.contains("\"main\""))
        assertTrue(body.contains("\"settings\""))
        assertTrue(body.contains("\"about\""))
        assertTrue(body.contains("set_stored_value"))
    }

    @Test
    fun `lang=en returns english strings`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=en")
        val body = resp.bodyAsText()
        assertTrue(body.contains("Today"))
        assertFalse(body.contains("Сегодня"))
    }

    @Test
    fun `navigate urls use correct format`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=ru")
        val body = resp.bodyAsText()
        assertTrue(body.contains("weather-app://navigate?screen=settings"))
        assertTrue(body.contains("weather-app://navigate?screen=about"))
        assertTrue(body.contains("weather-app://navigate?screen=main"))
    }
}
```

**Файлы:**
- `backend/src/test/kotlin/workshop/ApplicationSmokeTest.kt` — создать

**Проверка:**
```bash
cd backend && ./gradlew test
# Все тесты GREEN. Посмотреть build/reports/tests/test/index.html
```

---

## Открытые вопросы / допущения

1. **`divan` версия `32.6.0` vs Maven Central.** Версия `32.6.0` — последняя публично
   доступная. При подготовке воркшопа верифицировать через
   `https://mvnrepository.com/artifact/com.yandex.div/kotlin-json-builder`.

2. **Сборка JSON-конверта.** Серийная сборка `templates` + `screens` в одну JSON-строку
   через Jackson. Если divan возвращает не `JsonNode` а строку — использовать
   `mapper.readTree(json)` для встраивания.

3. **Город в заголовке.** `DayForecast.city` содержит "Москва" в mock-е. Для `lang=en`
   нет "Moscow" — mock намеренно упрощён.

4. **Compact через выражение.** Compact-компактный вид — упражнение для студента:
   добавить `getStoredBooleanValue('compact', false)` в выражения `font_size` и `visibility`.

5. **Kotlin version alignment.** App и Backend оба используют Kotlin `2.1.20` и
   Java toolchain 17 — единый стек без конфликтов.
