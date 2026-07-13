# План реализации: Android-приложение DivKit Weather Workshop

## Задача

Создать standalone Android-приложение для воркшопа «BDUI и DivKit». Приложение демонстрирует
полный цикл Backend-Driven UI: три экрана (главная погода, настройки, «О приложении»), которые
приходят с бэкенда в одном JSON-конверте. Клиент парсит `templates` один раз, строит `DivData`
для каждого экрана, навигация переключает активный экран без новых запросов.

Персистенция состояния (`theme`, `compact`, `lang`) реализована через **DivKit Stored Values**
(SQLite, встроен в `div-storage`). SharedPreferences не используются. Смена `lang` запускает
повторный запрос `GET /document?lang=`. Приложение живёт в отдельном репозитории;
используется исключительно open-source DivKit (`com.yandex.div`, Maven Central).

---

## Структура репозитория/проекта

```
divkit-weather-workshop/
  app/                                 # Gradle-проект Android-приложения
    settings.gradle.kts                # rootProject.name = "divkit-weather-app"
    build.gradle.kts                   # buildscript/plugins блок
    gradle/
      libs.versions.toml
    src/main/
      AndroidManifest.xml
      assets/
        document.json                  # bundled fallback-конверт (templates + screens)
      java/com/example/weatherdivkit/
        MainActivity.kt
        WeatherApplication.kt
        di/
          AppContainer.kt
        divkit/
          WeatherDivActionHandler.kt
          DocumentLoader.kt            # парсинг JSON-конверта → DivParsingEnvironment + map<Screen, DivData>
        navigation/
          Screen.kt
          WeatherNavigation.kt
        network/
          WeatherApiService.kt
        screens/
          MainScreenFragment.kt
          SettingsScreenFragment.kt
          AboutScreenFragment.kt
        ui/
          DivScreenFragment.kt         # базовый фрагмент с Div2View
      res/
        layout/
          activity_main.xml
          fragment_div_screen.xml
        values/
          strings.xml
  backend/                             # Gradle-проект Ktor-бэкенда (отдельный)
  plan/
  README.md
```

Нет корневого `build.gradle.kts` / `settings.gradle.kts` на уровне репозитория.
`app/settings.gradle.kts` — единственный Gradle entry point для Android-проекта.

### Gradle-зависимости (`app/build.gradle.kts`)

```kotlin
// DivKit — Maven Central, group = com.yandex.div, version = 32.6.0
implementation("com.yandex.div:div:32.6.0")
implementation("com.yandex.div:div-core:32.6.0")
implementation("com.yandex.div:div-data:32.6.0")
implementation("com.yandex.div:div-json:32.6.0")
implementation("com.yandex.div:div-storage:32.6.0")  // для Stored Values persistence

// Image loader для DivKit (coil3 backend)
implementation("com.yandex.div:coil:32.6.0")
implementation("io.coil-kt.coil3:coil:3.1.0")
implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
implementation("io.coil-kt.coil3:coil-svg:3.1.0")

// Сеть
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// AndroidX
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("androidx.fragment:fragment-ktx:1.8.9")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
implementation("com.google.android.material:material:1.13.0")
```

Java toolchain = 17, Kotlin = `2.1.20`.

---

## Этапы

---

### Этап 1: Scaffolding — структура проекта и DivKit-зависимость

**Что реализовать:**
- Создать `app/settings.gradle.kts` с именем проекта `divkit-weather-app` и подключением
  модуля `:app` (корневой модуль, т.е. `include(":app")` не нужен — это сам root).
  Фактически: `rootProject.name = "divkit-weather-app"`.
- Создать `app/build.gradle.kts` со всеми зависимостями из раздела выше.
  `applicationId = "com.example.weatherdivkit"`, `minSdk = 23`, `targetSdk = 35`.
  Подключить Java toolchain 17: `kotlin { jvmToolchain(17) }`.
- Создать `app/gradle/libs.versions.toml` с версиями DivKit `32.6.0`, Coil `3.1.0`, OkHttp `4.12.0`.
- Создать пустой `WeatherApplication : Application()` и зарегистрировать в `AndroidManifest.xml`.
- Создать `MainActivity : AppCompatActivity()` с минимальным `setContentView`.

**Файлы:**
- `app/settings.gradle.kts` — создать
- `app/build.gradle.kts` — создать
- `app/gradle/libs.versions.toml` — создать
- `app/src/main/AndroidManifest.xml` — создать
- `app/src/main/java/.../WeatherApplication.kt` — создать
- `app/src/main/java/.../MainActivity.kt` — создать

**Референс в demo:**
- `/Users/the-leo/arcadia/divkit/public/client/android/sample/build.gradle.kts` — пример
  минимального build.gradle для sample-приложения
- `/Users/the-leo/arcadia/divkit/public/client/android/gradle/libs.versions.toml` —
  канонические версии всех зависимостей (использовать как справочник; взять версию `32.6.0`)

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Должна получиться APK без ошибок компиляции
```

---

### Этап 2: Инициализация DivKit в Application — Stored Values wiring

**Что реализовать:**
- В `WeatherApplication.onCreate()` вызвать `DivKit.configure(DivKitConfiguration.Builder().build())`.
  Это подключает `DivStorageModule`, который автоматически создаёт `DivStorageComponent`
  (SQLite-хранилище) — stored values будут персистироваться между перезапусками без
  какого-либо дополнительного кода.
- Создать `AppContainer` — singleton-контейнер зависимостей (не DI-фреймворк):
  - Поле `div2Context: Div2Context` — создаётся в `WeatherApplication.onCreate()`.
  - При создании `Div2Context` передаётся `DivConfiguration` с `CoilDivImageLoader(context)`
    и пустым `DivActionHandler()` (заменится на `WeatherDivActionHandler` в Этапе 4).

**Ключевые API:**
```kotlin
// WeatherApplication.kt
class WeatherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализация DivKit — подключает DivStorageModule автоматически.
        // Stored Values будут персистироваться через SQLite (div-storage) без доп. настройки.
        DivKit.configure(DivKitConfiguration.Builder().build())
        AppContainer.init(this)
    }
}
```

**Файлы:**
- `app/src/main/java/.../WeatherApplication.kt` — изменить
- `app/src/main/java/.../di/AppContainer.kt` — создать

**Референс:**
- `/Users/the-leo/arcadia/divkit/public/client/android/divkit-demo-app/src/main/java/com/yandex/divkit/demo/DivkitApplication.kt` —
  строки 64–75: паттерн `DivKit.configure(DivKitConfiguration.Builder()...build())`
- `/Users/the-leo/arcadia/divkit/public/client/android/div/src/main/java/com/yandex/div/core/DivKit.kt` —
  API `DivKit.configure(...)` и `DivKit.getInstance(...)`
- `/Users/the-leo/arcadia/divkit/public/client/android/div/src/main/java/com/yandex/div/core/DivKitConfiguration.kt` —
  `DivKitConfiguration.Builder()` — `divStorageComponent(...)` необязателен

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Запустить на эмуляторе: должен стартовать без краша
```

---

### Этап 3: Базовый DivKit-хост — Div2View в Fragment

**Что реализовать:**
- Создать `DivScreenFragment : Fragment()` — базовый фрагмент с `Div2View`.
  - Поле `div2View: Div2View` создаётся программно через `Div2View(div2Context)`.
  - `div2Context: Div2Context` передаётся из `AppContainer`.
  - Метод `bindDivData(data: DivData, tag: String)` вызывает `div2View.setData(data, DivDataTag(tag))`.
  - В `onCreateView` возвращает `div2View` (или FrameLayout-обёртку).
- Добавить `fragment_div_screen.xml` — пустой `FrameLayout` (Div2View добавляется программно).

**Ключевые API:**
```kotlin
// Создание Div2Context (паттерн из sample/MainPageActivity.kt)
val configuration = DivConfiguration.Builder(CoilDivImageLoader(this))
    .actionHandler(WeatherDivActionHandler(...))
    .visualErrorsEnabled(BuildConfig.DEBUG)
    .build()
val div2Context = Div2Context(
    baseContext = this,
    configuration = configuration,
    lifecycleOwner = this
)

// Создание и привязка Div2View
val div2View = Div2View(div2Context)
div2View.setData(divData, DivDataTag("weather_main"))
```

**Файлы:**
- `app/src/main/java/.../ui/DivScreenFragment.kt` — создать
- `app/src/main/java/.../di/AppContainer.kt` — изменить (добавить div2Context)
- `app/src/main/res/layout/fragment_div_screen.xml` — создать

**Референс:**
- `/Users/the-leo/arcadia/divkit/public/client/android/sample/src/main/java/com/yandex/divkit/sample/MainPageActivity.kt` —
  полный пример создания `Div2Context` + `DivConfiguration` + `Div2View`
- `/Users/the-leo/arcadia/divkit/public/client/android/sample/src/main/java/com/yandex/divkit/sample/DivViewFactory.kt` —
  паттерн `DivData(environment, cardJson)` + `setData`
- `/Users/the-leo/arcadia/divkit/public/client/android/div/src/main/java/com/yandex/div/core/Div2Context.kt` —
  сигнатура конструктора

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Запустить: должен открыться MainActivity без краша
```

---

### Этап 4: Загрузчик JSON-конверта — `DocumentLoader`

**Что реализовать:**
- Создать `DocumentEnvelope` — data class:
  ```kotlin
  data class DocumentEnvelope(
      val environment: DivParsingEnvironment,  // разобранные templates
      val screens: Map<Screen, DivData>         // DivData per screen
  )
  ```
- Создать `DocumentLoader(context: Context)`:
  - `fun loadFromAssets(): DocumentEnvelope` — читает `assets/document.json`,
    парсит templates один раз в `DivParsingEnvironment`, строит `DivData`
    для каждого ключа из `"screens"`.
  - `suspend fun loadFromNetwork(url: String): Result<DocumentEnvelope>` — GET-запрос через OkHttp,
    парсит тот же формат.
- Алгоритм парсинга:
  1. `val json = JSONObject(rawString)`
  2. `val templates = json.optJSONObject("templates")`
  3. `val env = DivParsingEnvironment(ParsingErrorLogger.LOG)`
  4. `if (templates != null) env.parseTemplates(templates)`
  5. `val screensJson = json.getJSONObject("screens")`
  6. Для каждого ключа `main/settings/about`: `DivData(env, screensJson.getJSONObject(key))`
- Добавить `assets/document.json` — минимальный stub-конверт с заглушками для всех трёх экранов.

**Ключевые API:**
```kotlin
// Паттерн из sample/AssetReader.kt и DivUtils.asDiv2DataWithTemplates()
val env = DivParsingEnvironment(ParsingErrorLogger.LOG)
templates?.let { env.parseTemplates(it) }
val mainData  = DivData(env, screensJson.getJSONObject("main"))
val settingsData = DivData(env, screensJson.getJSONObject("settings"))
val aboutData = DivData(env, screensJson.getJSONObject("about"))
```

**Файлы:**
- `app/src/main/java/.../divkit/DocumentLoader.kt` — создать
- `app/src/main/assets/document.json` — создать (stub-конверт)
- `app/src/main/java/.../di/AppContainer.kt` — изменить (добавить DocumentLoader)

**Референс:**
- `/Users/the-leo/arcadia/divkit/public/client/android/sample/src/main/java/com/yandex/divkit/sample/AssetReader.kt` —
  чтение файлов из assets
- `/Users/the-leo/arcadia/divkit/public/client/android/divkit-demo-app/src/main/java/com/yandex/divkit/demo/div/DivUtils.kt` —
  функции `asDiv2Data`, `asDiv2DataWithTemplates` — полный паттерн парсинга

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Убедиться что DocumentLoader.loadFromAssets() не бросает исключений (добавить log в onCreate)
```

---

### Этап 5: Кастомный DivActionHandler — навигация и set_lang

**Что реализовать:**
- Создать `Screen` enum: `MAIN`, `SETTINGS`, `ABOUT`.
- Создать интерфейс `WeatherNavigation`:
  - `fun navigateTo(screen: Screen)`
  - `fun goBack()`
- Создать `WeatherDivActionHandler(navigation: WeatherNavigation, onSetLang: (String) -> Unit) : DivActionHandler()`:
  - Переопределить `handleAction(action: DivAction, view: DivViewFacade, resolver: ExpressionResolver): Boolean`.
  - Получить URL: `val url = action.url?.evaluate(resolver) ?: return super.handleAction(...)`.
  - Проверить схему `weather-app`; обрабатывать:
    - `navigate?screen=main|settings|about` → `navigation.navigateTo(...)`.
    - `back` → `navigation.goBack()`.
    - `set_lang?value=ru|en` → `onSetLang(url.getQueryParameter("value") ?: "ru")`.
  - Возвращать `true` если обработано, иначе `super.handleAction(...)`.

**Ключевые API:**
```kotlin
class WeatherDivActionHandler(
    private val navigation: WeatherNavigation,
    private val onSetLang: (String) -> Unit,
) : DivActionHandler() {
    override fun handleAction(action: DivAction, view: DivViewFacade, resolver: ExpressionResolver): Boolean {
        val url = action.url?.evaluate(resolver)
            ?: return super.handleAction(action, view, resolver)
        return if (url.scheme == SCHEME && handleWeatherAction(url)) true
               else super.handleAction(action, view, resolver)
    }
    companion object { const val SCHEME = "weather-app" }
}
```

**Файлы:**
- `app/src/main/java/.../navigation/Screen.kt` — создать
- `app/src/main/java/.../navigation/WeatherNavigation.kt` — создать
- `app/src/main/java/.../divkit/WeatherDivActionHandler.kt` — создать
- `app/src/main/java/.../di/AppContainer.kt` — изменить (передать handler в DivConfiguration)

**Референс:**
- `/Users/the-leo/arcadia/divkit/public/client/android/sample/src/main/java/com/yandex/divkit/sample/SampleDivActionHandler.kt` —
  минимальный пример custom action handler
- `/Users/the-leo/arcadia/divkit/public/client/android/div/src/main/java/com/yandex/div/core/DivActionHandler.java` —
  полная сигнатура базового класса; правило: вызывать `super` если не обработано

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Добавить action в stub document.json: "url": "weather-app://navigate?screen=settings"
# Нажать — убедиться, что logcat выводит правильный экран
```

---

### Этап 6: Навигация между тремя экранами

**Что реализовать:**
- Реализовать навигацию через `FragmentManager` в `MainActivity`:
  - Три фрагмента: `MainScreenFragment`, `SettingsScreenFragment`, `AboutScreenFragment` —
    каждый наследует `DivScreenFragment`.
  - `MainActivity` реализует `WeatherNavigation`.
  - `navigateTo(screen)`: `supportFragmentManager.commit { replace(R.id.fragment_container, fragment) }`.
  - `goBack()`: `onBackPressedDispatcher.onBackPressed()`.
  - При запуске: показывать `MainScreenFragment`.
- `activity_main.xml`: единственный `FrameLayout` с `id="fragment_container"`.
- Каждый фрагмент при создании вызывает
  `bindDivData(appContainer.envelope.screens[Screen.MAIN]!!, "weather_main")`.

**Файлы:**
- `app/src/main/java/.../MainActivity.kt` — изменить
- `app/src/main/java/.../screens/MainScreenFragment.kt` — создать
- `app/src/main/java/.../screens/SettingsScreenFragment.kt` — создать
- `app/src/main/java/.../screens/AboutScreenFragment.kt` — создать
- `app/src/main/res/layout/activity_main.xml` — создать

**Референс:**
- `/Users/the-leo/arcadia/divkit/public/client/android/divkit-demo-app/src/main/java/com/yandex/divkit/demo/div/Div2Activity.kt` —
  пример Activity со встроенным Div2View и DivContext

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Запустить: нажать кнопку навигации в stub JSON → переход на другой экран
```

---

### Этап 7: Сетевой загрузчик — одиночный запрос GET /document

**Что реализовать:**
- Создать `WeatherApiService(baseUrl: String, okHttpClient: OkHttpClient)`:
  - `suspend fun fetchDocument(lang: String): Result<DocumentEnvelope>` —
    вызывает `DocumentLoader.loadFromNetwork("$baseUrl/document?lang=$lang")`.
- В `MainScreenFragment`:
  - В `onViewCreated`: запустить корутину, вызвать `apiService.fetchDocument(currentLang())`.
  - При успехе: сохранить `envelope` в `AppContainer`, вызвать
    `bindDivData(envelope.screens[Screen.MAIN]!!, "weather_main")`.
    Остальные фрагменты при открытии берут `DivData` из `AppContainer.envelope`.
  - При ошибке: загрузить fallback из assets через `DocumentLoader.loadFromAssets()`.
- `currentLang()` — читает текущее stored value `lang` через
  `StoredValuesController.getStoredValue("lang", "global", "", errorCollector)`.
  Если не установлено — default `"ru"`.
- Добавить `INTERNET` permission в `AndroidManifest.xml`.

**Файлы:**
- `app/src/main/java/.../network/WeatherApiService.kt` — создать
- `app/src/main/java/.../screens/MainScreenFragment.kt` — изменить
- `app/src/main/AndroidManifest.xml` — изменить
- `app/src/main/java/.../di/AppContainer.kt` — изменить (добавить OkHttpClient, WeatherApiService)

**Референс:**
- `/Users/the-leo/arcadia/divkit/public/client/android/divkit-demo-app/src/main/java/com/yandex/divkit/demo/div/DivUtils.kt` —
  паттерн парсинга ответа сервера
- `/Users/the-leo/arcadia/divkit/public/client/android/div/src/main/java/com/yandex/div/core/expression/storedvalues/StoredValuesController.kt` —
  метод `getStoredValue(name, scope, dataTag, errorCollector)`

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Запустить с поднятым бэкендом — главный экран должен загружаться из сети
# Отключить сеть — должен показываться assets-fallback
```

---

### Этап 8: Смена языка — рефетч всего конверта

**Что реализовать:**
- В `WeatherDivActionHandler`: при `set_lang` вызывать `onSetLang(value)`.
- В `MainActivity`/`AppContainer` реализовать `onSetLang(lang: String)`:
  1. Рефетч `apiService.fetchDocument(lang)`.
  2. При успехе: сохранить `envelope` в `AppContainer`, обновить `DivData`
     во всех фрагментах (или только в активном) с новым тегом
     `"weather_main_$lang"` / `"weather_settings_$lang"` / `"weather_about_$lang"`.
- **Педагогический момент** (комментарий в коде): объяснить почему `lang` требует рефетч
  (строки «запечены» сервером), а `theme`/`compact` — нет.
- Текущий `lang` при следующем старте читается из stored value — `DivKit.configure()` уже
  гарантирует персистенцию.

**Файлы:**
- `app/src/main/java/.../screens/MainScreenFragment.kt` — изменить (добавить refetch)
- `app/src/main/java/.../divkit/WeatherDivActionHandler.kt` — проверить вызов onSetLang

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# На Settings нажать "EN" — все экраны должны обновиться английскими строками
# Перезапуск — lang=en сохранён (stored value в SQLite)
```

---

### Этап 9: Stored Values для theme/compact — settings.json

**Что реализовать:**
- Написать полноценный контент экрана settings в `assets/document.json` (stub замените финальным):
  - Переключатель темы: каждый вариант содержит действие типа `set_stored_value`
    с `name = "theme"`, `value = stringValue("light"|"dark")`, `lifetime = Int.MAX_VALUE`, `scope = global`.
    Активный вариант выделяется через выражение
    `@{getStoredStringValue('theme', 'light') == 'light'}`.
  - Переключатель compact: `set_stored_value` с `name = "compact"`, `value = booleanValue(true|false)`.
  - Переключатель языка: action `weather-app://set_lang?value=ru|en`.
  - Кнопка «Назад»: action `weather-app://back`.
- JSON генерируется бэкендом (см. backend_plan.md), но также обновить `assets/document.json`
  как offline-fallback.

**Ключевые API (для JSON-структуры):**
```json
{
  "type": "set_stored_value",
  "name": "theme",
  "value": { "type": "string", "value": "dark" },
  "lifetime": 2147483647,
  "scope": "global"
}
```

**Файлы:**
- `app/src/main/assets/document.json` — обновить (полный контент settings-экрана в конверте)

**Референс:**
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/ActionSetStoredValue.kt` —
  полное определение `actionSetStoredValue` в divan DSL
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/StringValue.kt` —
  конструктор `stringValue(value = "dark")`
- `/Users/the-leo/arcadia/divkit/public/json-builder/kotlin/divan-dsl/src/generated/kotlin/divkit/dsl/EnumValues.kt` —
  строки 118–123: `GlobalEnumValue : ActionSetStoredValue.Scope` — scope `"global"`

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Открыть Settings: нажать Dark — цвета должны измениться (без рефетча!)
# Нажать Compact — элементы скрываются/уменьшаются
# Перезапустить — theme и compact сохранены (stored values в SQLite)
```

---

### Этап 10: Реакция на theme/compact через DivKit-выражения

**Что реализовать:**
- Убедиться что layouts в `assets/document.json` (и на бэкенде) содержат выражения
  с `getStoredStringValue`/`getStoredBooleanValue`:
  - Фоновый цвет: `"@{getStoredStringValue('theme', 'light') == 'dark' ? '#1C1C1E' : '#FFFFFF'}"`.
  - Видимость детального блока: `"@{getStoredBooleanValue('compact', false) ? 'gone' : 'visible'}"`.
  - Размер шрифта: `"@{getStoredBooleanValue('compact', false) ? 14 : 18}"`.
- Убедиться что тот же паттерн применён в settings-экране (для отображения активного переключателя).

**Файлы:**
- `app/src/main/assets/document.json` — дополнить выражениями

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Включить Dark → главный экран потемнел (без перезагрузки!)
# Включить Compact → детали скрылись, текст уменьшился
```

---

### Этап 11: Offline/fallback — обработка отсутствия сети

**Что реализовать:**
- В `MainScreenFragment.loadDocument()` явный fallback:
  ```kotlin
  val result = apiService.fetchDocument(currentLang())
  val envelope = result.getOrElse {
      Log.w("WeatherApp", "Network failed, using fallback", it)
      documentLoader.loadFromAssets()
  }
  appContainer.envelope = envelope
  bindDivData(envelope.screens[Screen.MAIN]!!, "weather_main")
  ```
- Показывать Toast при fallback.

**Файлы:**
- `app/src/main/java/.../screens/MainScreenFragment.kt` — изменить

**Проверка:**
```bash
# Отключить сеть (Airplane mode)
cd app && ./gradlew assembleDebug
# Запустить — fallback из assets, Toast "Offline mode"
```

---

### Этап 12: Финальные JSON-макеты — полноценная погода

**Что реализовать:**
- Написать полноценный `assets/document.json`:
  - `templates`: переиспользуемые `weather_card` и `nav_button`.
  - `screens.main`: header с городом, карточки сегодня/завтра с placeholder-данными,
    навигационные кнопки (`weather-app://navigate?screen=settings`,
    `weather-app://navigate?screen=about`). Выражения для `theme` и `compact`.
  - `screens.settings`: переключатели (см. Этап 9).
  - `screens.about`: название, версия (хардкод), ссылка на репо, кнопка Back.

**Файлы:**
- `app/src/main/assets/document.json` — переписать полностью

**Проверка:**
```bash
cd app && ./gradlew assembleDebug
# Пройти все три экрана вручную
# Переключить тему → реакция без рефетча
# Переключить язык → рефетч (видно по изменению строк)
# Перезапустить → все настройки сохранены
```

---

### Этап 13: Финальная проверка и README

**Что реализовать:**
- Проверить README (обновится в следующем файле) — должны быть инструкции запуска.
- Проверить ProGuard rules — добавить keep-правила для `com.yandex.div.**`.
- Убедиться что `app/build.gradle.kts` содержит:
  ```kotlin
  buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080\"")
  ```

**Файлы:**
- `app/src/main/proguard-rules.pro` — создать/изменить
- `app/build.gradle.kts` — добавить buildConfigField

**Проверка:**
```bash
cd app && ./gradlew assembleRelease
# Release-сборка без ошибок ProGuard
cd app && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
# Полный end-to-end smoke test
```

---

## Открытые вопросы и допущения

1. **Версия `32.6.0` на Maven Central.** Это последняя публично доступная версия в группе
   `com.yandex.div`. Версия `32.54.0` не опубликована на Maven Central. При подготовке
   воркшопа проверять актуальность через
   `https://mvnrepository.com/artifact/com.yandex.div/div`.

2. **Чтение `lang` из StoredValuesController.** `StoredValuesController` — внутренний класс
   Dagger-компонента. Самый чистый способ получить текущий `lang` из кода (не из JSON-выражения)
   — хранить последнее значение в `AppContainer` при каждом рефетче. Альтернатива —
   `div2Context.component.storedValuesController.getStoredValue(...)`, но это внутренний API.

3. **`CoilDivImageLoader` vs собственная реализация.** В sample-приложении используется
   `CoilDivImageLoader` из модуля `com.yandex.div:coil`. Если воркшоп предпочитает минимизм —
   можно реализовать `DivImageLoader` вручную через OkHttp.

4. **Базовый URL бэкенда.** `http://10.0.2.2:8080` — localhost для AVD. Для реального
   воркшопа на физических устройствах нужен реальный хост. Рекомендуется вынести BASE_URL
   в `local.properties` → `BuildConfig`.

5. **Back stack.** Используется `replace` без `addToBackStack` для простоты. При добавлении
   back stack использовать `.addToBackStack(screen.name)`.

6. **Тема Android vs тема DivKit.** `theme` — переменная в DivKit-выражениях, системная тема
   Android не затрагивается. Сознательное упрощение для фокуса на BDUI.

7. **`lifetime = Int.MAX_VALUE` для stored values.** Это `2_147_483_647` секунд ≈ 68 лет.
   Effectively "навсегда" для воркшопа. Проверить что тип поля в divan DSL принимает `Int`
   (да — подтверждено в `ActionSetStoredValue.Properties`: `val lifetime: Property<Int>?`).
