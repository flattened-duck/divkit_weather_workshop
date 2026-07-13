# ROADMAP — DivKit Weather Workshop

> План действий ассистента по проекту. Живёт в репозитории, обновляется по ходу работы.

## Цель

Собрать в одном **открытом** репозитории всё для воркшопа «BDUI и DivKit»:

- **доклад** — презентация + материалы лекции (`talk/`),
- **демо-приложение** Android на открытом DivKit (`app/`),
- **демо-бэкенд** на Kotlin/Ktor (`backend/`).

Никакой проприетарщины (Flex / Tovarisch / AppHost) — только открытый стек DivKit.

## Текущее состояние

| Часть | Статус |
|---|---|
| Доклад: 38-слайдная презентация (`talk/BDUI_DivKit_лекция.pptx`) + тезисы лекции | ✅ готово (до правок под открытый стек) |
| Контракт клиент↔сервер (`plan/contract.md`) | ✅ v2 |
| План Android-приложения (`plan/mobile_app_plan.md`, 13 этапов) | ✅ v2 — реализован |
| План бэкенда (`plan/backend_plan.md`, 10 этапов) | ✅ v2 — реализован |
| Реализация бэкенда | ✅ готова — 5 smoke-тестов зелёные, `/document?lang=ru\|en` отдаёт конверт |
| Реализация приложения | ✅ готова — APK собран; архитектура упрощена (одна `MainActivity` со свопом `Div2View`, Picasso, `lang` через SharedPreferences) |
| Интеграция e2e (Фаза 3) | ✅ проверено на эмуляторе `Medium_Phone_API_36.1`: навигация по 3 экранам, theme/compact без рефетча, смена языка с рефетчем, персистенция после перезапуска. Скриншоты в `talk/screens/` |
| Реализация iOS | ⬜ не начата — следующий шаг |
| Правки доклада под открытый стек (скриншоты/код) | ⬜ отложено до готовых приложений |

> **Наблюдение (Фаза 3):** stored-value `theme`/`compact` подхватываются при **ре-рендере** экрана
> (навигация пересоздаёт `Div2View`), а не мгновенно на текущем видимом `Div2View`. Сеть при этом не
> дёргается — обещание «без рефетча» выполнено. Мгновенная реактивность на том же экране — потенциальное
> улучшение (через div-переменные вместо чтения stored value в выражениях).

## Структура репозитория

```
divkit-weather-workshop/
  app/        # Android-приложение (открытый DivKit) — будет собрано в Фазе 2
  backend/    # Kotlin/Ktor-бэкенд — будет собран в Фазе 1
  talk/       # презентация (.pptx) + материалы лекции + скрипты сборки колоды
  plan/       # contract.md, mobile_app_plan.md, backend_plan.md, ROADMAP.md
  README.md
  .gitignore
```

## Фазы

### Фаза 1 — Бэкенд (первым)

Реализовать `backend/` по `plan/backend_plan.md` (10 этапов): Ktor scaffold → `weather_data.proto` + mock → `Localizer` (ru/en) → рендереры `main`/`settings`/`about` + общие `SharedTemplates` → `WeatherServant` (сборка конверта) → эндпоинт `GET /document?lang=` → `set_stored_value`-экшены в settings → smoke-тест.

**Проверка:** `cd backend && ./gradlew run`; `curl 'localhost:8080/document?lang=ru'` отдаёт конверт `{templates, screens:{main,settings,about}}`; `lang=en` меняет строки. Java toolchain 17.

### Фаза 2 — Приложение

Реализовать `app/` по `plan/mobile_app_plan.md` (13 этапов): scaffold + DivKit `32.6.0` → Div2View-хост → `DocumentLoader` (конверт → `Map<Screen, DivData>`, общий парсинг templates) → кастомный `WeatherDivActionHandler` (`navigate`/`back`/`set_lang`) → навигация по 3 экранам → stored-values (авто-персист через `div-storage`) → сеть `GET /document` → смена языка (рефетч) → выражения `theme`/`compact` → offline-fallback (`assets/document.json`) → финальные макеты.

**Проверка:** сборка + запуск на эмуляторе; навигация работает; тема/компакт меняются **без** рефетча; смена языка — **с** рефетчем; перезапуск сохраняет настройки.

### Фаза 3 — Интеграция (e2e)

Поднять бэкенд + клиент вместе (`BASE_URL=http://10.0.2.2:8080`), проверить полный путь. Снять **скриншоты всех экранов**: light/dark, compact on/off, ru/en — это ассеты для доклада.

### Фаза 4 — Правки доклада (отложенная задача)

Убрать проприетарные детали (оставить «в общих словах»); вставить слайды со **скриншотами** (Фаза 3) и **кодом** (divan-layout, `WeatherDivActionHandler`, `set_stored_value`, локализация); добавить тему «один ответ со всеми экранами + переиспользование templates» vs «ручка на экран». Пересобрать `.pptx` скриптами в `talk/`.

### Фаза 5 — Финал репозитория

README, лицензия, инструкции запуска; (по запросу) коммит и пуш на GitHub.

## Порядок и принципы

- Порядок: **бэкенд → приложение → интеграция → доклад**.
- Реализация — оркестрацией Sonnet-агентов поэтапно, с проверками; перед пушем — ревью.
- Только открытый стек; DivKit `32.6.0` (Maven Central); тяжёлую реализацию веду на Sonnet (бюджет Opus).

## Тулчейн (проверено на машине)

- JDK 23 установлен; проекты пиннят **Java toolchain 17** (AGP 8.12 дружит с 17/21).
- Android SDK: `~/Library/Android/sdk`; Android Studio есть; `adb` есть.
- Шаблон презентации: `/Users/the-leo/Downloads/Shablon_ЯTeam…pptx` (вне репо, ~91 МБ; на него ссылается `talk/deck.py`).

## Открытые решения

- Локализация `settings`/`about` — **решена**: генерятся бэкендом, локализуются.
- `compact`-выражения (конкретные `font_size`/`visibility`) — финализировать при сборке.
- `git-lfs` для `talk/*.pptx` (~30 МБ) — опционально перед пушем на GitHub.

## Референсы (внешние примеры)

- **Прошлогодний воркшоп** склонирован в `/Users/the-leo/div_summer_workshop_ref`
  (github.com/flattened-duck/div_summer_workshop) — android + backend + ios.
  - **Backend:** Spring Boot, отдаёт готовый `sample.json` с подстановкой переводов (БЕЗ divan). Проще нашего; для divan **не образец**.
  - **Android:** скелет (`DataLoader.get()` — TODO-заглушка, `Div2View` не дописан). Даёт рабочий набор зависимостей и паттерн сети, но не саму провязку.
  - **Полезное для нашего app:** image loader на **Picasso** (`com.yandex.div:picasso` + `utils`) — легче Coil, **переходим на Picasso**; набор `div/div-core/div-json/picasso/utils` (DivKit 32.5/32.6); `DataLoader` на okhttp к `10.0.2.2:8080`; `sample.json` как пример DivKit JSON.
  - **Реальную** провязку `Div2View`/`DivActionHandler`/переменных/stored-values берём из arcadia-демо (`/arcadia/divkit/public/client/android`), не из референса.
