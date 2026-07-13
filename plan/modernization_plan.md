# План модернизации DivKit Weather Workshop

> Собран из 8 задач заказчика. Ведётся оркестрацией (умник→копатель→ревьюер→умник-верификатор),
> коммиты — за оркестратором. Параллельные ветки — в отдельных git worktree.

## Утверждённые решения

- **Источник погоды:** Open-Meteo (keyless, открытый). Forecast + UV + geocoding.
- **Доставка картинок:** `raw.githubusercontent.com` — ✅ проверено, репо публичный и запушен, raw даёт 200.
  База (branch): `https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/`.
  Для неизменяемости можно пиннить коммит вместо `main`
  (пример: `.../divkit_weather_workshop/3a2dc21.../S3/background_cloudy_day.png`).
- **Выбор города:** DivKit `input` + кастомный action-handler → на изменение текста запрос на бэк →
  бэк возвращает **DivPatch** со списком найденных городов → клиент применяет патч.
  Тап по городу → `set_city` → полный рефетч `/document?city=`.
- **Порядок:** утверждаем план → старт со Стадии 0.
- **Image loader:** переезд с `PicassoDivImageLoader` (deprecated в 32.57.0) на **`CoilDivImageLoader`**
  (`com.yandex.div:coil` + `io.coil-kt` deps, свап в `DivConfiguration`). Делается в Worktree C.
- **Тулчейн:** DivKit поднят до `32.57.0`, app Kotlin до `2.2.10` (см. [[divkit-3257-kotlin-cascade]]).

## Карта имён (контракт-глей между параллельными ветками) — фиксируется в Стадии 0

| Сущность | Имя | Кто выставляет | Кто читает |
|---|---|---|---|
| DivCustom фазы солнца | `custom_type: "sun_phase"` | натив (ветка C) | JSON главного (ветка A) |
| Extension состояния скролла | `id: "scroll_state"` | натив (ветка C) | JSON галереи (ветка A) |
| Переменная «шапка сжата» | `header_collapsed` (Boolean) | extension (C) | выражения шапки (A) |
| Ключ фона погоды | `bg_key` (String: sunny/cloudy/rain/storm/fog + `_day`/`_night`) | бэк-данные (Стадия 0) | image-подложка (A) |
| Экшен смены города | `weather-app://set_city?...` | UI (B) | handler (C) |
| Экшен поиска (input change) | `weather-app://city_search?q=...` | input (B) | handler (C) → DivPatch |
| Переменная запроса | `city_query` (String) | input (B) | handler (C) |

---

## СТАДИЯ 0 — Хребет: контракт данных + реальный источник (СОЛО, блокирует всё)

**Задачи:** фундамент для 1, 2, 3, 8.

- Расширить `weather_data.proto`:
  - `Current` — temp_c, feels_c, condition, city, uv_index, humidity, pressure, visibility, wind, sunrise, sunset.
  - `Hourly[]` — time, temp_c, condition (24 точки).
  - `Daily[]` — weekday, temp_min, temp_max, condition, precip_prob (7 дней).
  - Поле `bg_key` в Current (маппинг WMO→фон + day/night по sunrise/sunset; нет совпадения → `fog`).
- `MockWeatherDataProvider` → `WeatherProvider` поверх `OpenMeteoClient` (Ktor client):
  geocoding → forecast(hourly,daily,current) → UV. Маппинг WMO-кодов → `ConditionCode` и `bg_key`.
- Реестр городов + дефолт; `GET /document?lang=&city=` (city как id/имя/lat,lon — определить в контракте).
- `Localizer` + строки: новые ключи (uv, sunrise/sunset, humidity, pressure, visibility, feels, осадки, weekdays, поиск города).
- Обновить `plan/contract.md`: карта имён выше + формат конверта + формат DivPatch-ответа поиска.

**Проверка:** `curl '/document?lang=ru&city=...'` отдаёт конверт с current/hourly[]/daily[]/bg_key.

---

## СТАДИЯ 1 — Три параллельных worktree (непересекающиеся файлы)

### Worktree A — «Главный экран» (бэк: `WeatherMainRenderer` + `SharedTemplates`)
Задачи **3 + 8 + 7 + 4(FAB)**.
- Шапка: город, крупная температура, состояние, макс/мин.
- Почасовой прогноз — `gallery` (горизонтальный скролл) с extension `id:"scroll_state"`.
- Недельный прогноз — список строк (иконка, min/max, тем-шкала).
- **Сжатие шапки:** полная/сжатая версии переключаются по `header_collapsed` через
  анимации DivKit (`transition_change`/visibility+animators). Сжатая — как на картинке 2.
- Нижний блок (картинка 2): UV-индекс, **фаза солнца** (`DivCustom type:"sun_phase"`),
  «ощущается как», осадки, видимость, влажность, давление.
- **Задача 8:** фон — `image` во всю подложку по url из `bg_key` (raw.githubusercontent), дефолт `fog`.
- **Задача 7:** в попапе над текстом — `image` (raw url) + `preview` в base64 (низкое качество).
- **Задача 4:** нижние FAB (карта/локация/настройки/список) — оверлей, экшены `navigate`.

### Worktree B — «Настройки + О приложении» (бэк: `WeatherSettingsRenderer` + `WeatherAboutRenderer`)
Задачи **1(UI) + 5**.
- **Задача 1:** секция поиска города — DivKit `input` (`text_variable: city_query`),
  контейнер-приёмник для результатов (заполняется DivPatch), экшены `city_search`/`set_city`.
- Новый бэк-эндпоинт `GET /city-search?q=&lang=` → **DivPatch** со списком городов.
- **Задача 5:** визуально подружить оба экрана с главным (типографика, карточки, подложка, отступы).

### Worktree C — «Клиентские расширения и хром» (модуль `app`)
Задачи **3(натив) + 6 + 1(клиент) + 8(клиент) + переезд на Coil**.
- **Picasso→Coil:** `com.yandex.div:coil` (+ coil deps) вместо `picasso`/`utils`;
  `CoilDivImageLoader(this)` в `DivConfiguration`. Убирает deprecation, нужен для фонов/картинок задач 7/8.
- `DivExtensionHandler` `scroll_state`: слушает скролл галереи → пишет `header_collapsed`.
- `DivCustomViewFactory` `sun_phase`: нативный вью, локально рисует дугу восход→закат
  (время/sunrise/sunset из данных). Регистрация в `DivConfiguration`.
- **Задача 6:** статусбар реагирует на `theme` — `WindowInsetsControllerCompat`
  (`isAppearanceLightStatusBars`) + цвет окна в `onSetTheme`/`onConfigurationChanged`.
- **Задача 1 (клиент):** в `WeatherDivActionHandler` — `city_search` (запрос → `div2View.applyPatch`)
  и `set_city` (персист + рефетч, по образцу `set_lang`). Прокинуть `city` в `DocumentLoader`.
- **Задача 8 (клиент):** проверить загрузку фонов Picasso по url (диагностика).

> Клей A↔C — только строковые имена из карты имён. Файлы не пересекаются → чистый merge.
> ⚠️ Все факты DivKit 32.6.0 (input, applyPatch, custom, extension, animators) —
> сверять с декомпилированным опубликованным jar, а не с arcadia.

---

## СТАДИЯ 2 — Интеграция (СОЛО)

- Слить worktree A/B/C.
- Поднять бэк + эмулятор, e2e: скролл→сжатие шапки, поиск города→DivPatch→set_city→рефетч,
  фон по погоде, статусбар по теме, попап с картинкой+preview.
- Обновить Espresso-автотесты под новый UI.
- Скриншоты light/dark, compact, ru/en.

---

## Открытые вопросы

- Формат `city` в `/document` (id реестра vs `lat,lon` из geocoding) — финализировать в Стадии 0.
- Точный API DivKit `input` on-change / `applyPatch` в 32.6.0 — исследование планировщика (умник) по jar.
