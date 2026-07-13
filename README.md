# DivKit Weather Workshop

Учебный пример для воркшопа **«BDUI и DivKit»**: маленькое приложение погоды,
целиком построенное на **открытом DivKit** — Android-клиент, который рендерит
BDUI-вёрстку, и Kotlin/Ktor-бэкенд, который эту вёрстку отдаёт.

> Здесь нет проприетарных технологий Яндекса (Flex, Tovarisch, AppHost). Всё —
> на открытом стеке: [DivKit](https://github.com/divkit/divkit) (`com.yandex.div`)
> на клиенте и `divan` (`com.yandex.div:kotlin-json-builder`) на сервере.

---

## Что внутри

| Папка | Что это |
|---|---|
| `app/` | Android-приложение (Gradle, Android Studio). Рендерит DivKit, кастомная навигация и обработчики экшенов, персистенция состояния через Stored Values. |
| `backend/` | Kotlin/Ktor-сервис. Мокает входные protobuf-данные, собирает DivKit JSON через `divan`, локализует строки по `?lang=`, отдаёт все три экрана в одном ответе. |
| `talk/` | Доклад: презентация (38 слайдов, `.pptx`) + материалы лекции (планы, тезисы-подсказки, скрипты сборки колоды). |
| `plan/` | Планы реализации (контракт, app, backend) + `ROADMAP.md` — общий план действий. |

---

## Архитектура

```
┌────────────────────────────────┐      GET /document?lang=ru       ┌─────────────────────────────┐
│  Android app (open DivKit)     │ ─────────────────────────────►   │  Ktor backend               │
│  • Div2View рендерит JSON      │                                   │  • мок protobuf WeatherData │
│  • WeatherDivActionHandler     │ ◄─────────────────────────────── │  • divan строит 3 экрана    │
│  • Stored Values (SQLite):     │   { "templates": {...},           │  • Localizer ru/en          │
│    theme, compact, lang        │     "screens": {                  │  • строки «запечены» в JSON │
│  • Один конверт → 3 DivData    │       "main":     {...},          └─────────────────────────────┘
│  • Навигация без рефетча       │       "settings": {...},
│  • Рефетч только при lang-смене│       "about":    {...} } }
└────────────────────────────────┘
             ▲
             │ assets/document.json (offline / first-launch fallback — тот же формат конверта)
```

### Ключевые архитектурные решения

- **Один запрос — три экрана.** Бэкенд возвращает единый JSON-конверт
  (`templates` + `screens.{main,settings,about}`). Клиент парсит `templates` один раз
  в `DivParsingEnvironment`, строит `DivData` для каждого экрана. Навигация переключает
  активный `DivData` в `Div2View` без новых сетевых запросов.

- **Персистенция через DivKit Stored Values.** Состояние (`theme`, `compact`, `lang`)
  хранится в SQLite через встроенный механизм `div-storage`. SharedPreferences не используются.
  Переключатели в settings-экране отправляют `set_stored_value` действия; выражения
  `getStoredStringValue('theme', 'light')` / `getStoredBooleanValue('compact', false)` читают
  их напрямую в layouts.

- **`theme` и `compact` — без рефетча.** DivKit пересчитывает выражения в реальном времени
  при изменении stored value. Android-код ничего не делает.

- **`lang` требует рефетча.** Строки «запечены» в JSON на сервере. Смена языка запускает
  новый `GET /document?lang=en`, клиент получает новый конверт и переключает все три `DivData`.

---

## Версии

| Компонент       | Версия                                      |
|-----------------|---------------------------------------------|
| DivKit Android  | `32.6.0` (latest на Maven Central)          |
| DivKit group    | `com.yandex.div`                            |
| divan (backend) | `com.yandex.div:kotlin-json-builder:32.6.0` |
| Kotlin          | `2.1.20` (app и backend)                    |
| Java toolchain  | 17 (app и backend)                          |
| AGP             | `8.12.3`                                    |
| minSdk          | 23                                          |
| targetSdk       | 35                                          |
| Ktor            | `3.1.3`                                     |

---

## Как запускать (после реализации по планам)

### Бэкенд

```bash
cd backend
./gradlew run
# Ktor стартует на :8080

curl 'http://localhost:8080/ping'                   # → "pong"
curl 'http://localhost:8080/document?lang=ru' | python3 -m json.tool
# → конверт: {"templates":{...},"screens":{"main":{...},"settings":{...},"about":{...}}}
curl 'http://localhost:8080/document?lang=en' | python3 -m json.tool | grep Today
# → строка с "Today"
```

### Android-приложение

```bash
cd app
./gradlew assembleDebug
# или открыть папку app/ в Android Studio и запустить на эмуляторе (AVD)
```

> URL бэкенда по умолчанию: `http://10.0.2.2:8080` (localhost для AVD).
> Для физического устройства — изменить `BASE_URL` в `app/build.gradle.kts`.

### Важно: `app/` и `backend/` — независимые Gradle-проекты

Открывать их **по отдельности** в Android Studio / IntelliJ IDEA.
Корневого `settings.gradle.kts` нет. Студент может запустить только клиент
на bundled fallback-конверте (`assets/document.json`) — без бэкенда.

---

## Ключевые правила (контракт)

Полный канонический контракт — [`plan/contract.md`](plan/contract.md). Кратко:

- **3 экрана**, все три приходят в одном ответе бэкенда: `main` (погода), `settings`, `about`.
- **Bundled fallback** `app/src/main/assets/document.json` — тот же формат конверта, offline.
- **Stored Values** (DivKit, SQLite): `theme` (light/dark), `compact` (bool), `lang` (ru/en).
  Инициализируются автоматически при `DivKit.configure(DivKitConfiguration.Builder().build())`.
- **Навигация — свой BDUI-формат**:
  `weather-app://navigate?screen=main|settings|about`,
  `weather-app://back`,
  `weather-app://set_lang?value=ru|en`.
- **`theme` и `compact`** — клиентские, реагируют через DivKit-выражения без рефетча.
- **`lang`** — требует рефетча `GET /document?lang={value}` (строки генерирует сервер).

---

## Планы реализации

0. [`plan/ROADMAP.md`](plan/ROADMAP.md) — общий план действий и фазы работ.
1. [`plan/contract.md`](plan/contract.md) — общий контракт клиент↔сервер (источник истины).
2. [`plan/mobile_app_plan.md`](plan/mobile_app_plan.md) — Android-приложение, 13 этапов.
3. [`plan/backend_plan.md`](plan/backend_plan.md) — Ktor-бэкенд, 10 этапов.

---

## Лицензия / происхождение

Демо для образовательных целей. DivKit — открытый проект Яндекса
(Apache-2.0, github.com/divkit/divkit). Этот репозиторий не содержит
внутреннего кода Яндекса.
