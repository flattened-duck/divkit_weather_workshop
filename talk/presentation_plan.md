# BDUI и DivKit: опыт работы в большом проекте

**Аудитория:** Студенты школы мобильной разработки — хорошо знают Android/Kotlin, слабо знакомы с бэкендом и BDUI.
**Формат:** 60 минут, ~38 слайдов.
**Язык:** Русский.

## Нарративная дуга

Лекция начинается с короткого напоминания «что такое BDUI и зачем он», чтобы зафиксировать общий язык и немедленно перейти к реальному опыту. Затем с позиций бэкенда мы разбираем архитектуру: кто кому что говорит и кто вообще принимает решения о верстке. После этого — серия практических тем, каждая из которых берёт одну реальную боль и показывает, как она решена в bromapi и в Android Browser (клиенте). Лекция заканчивается взглядом на паттерны — потому что за всеми отдельными темами стоят одни и те же идеи дизайна.

---

## Логические блоки и слайды

| Блок | Слайды | Тема |
|---|---|---|
| 0. Разогрев | 1–3 | Название, повестка, краткое напоминание BDUI |
| 1. Архитектура системы | 4–9 | AppHost, граф, bromapi как node, servants/renderers, данные |
| 2. Несколько экранов | 10–12 | Multiple screens, RequestType, Flex Document |
| 3. Дозапросы | 13–15 | Lazy/additional requests, NetworkRequestAction |
| 4. Состояние и офлайн | 16–20 | Variables, state, cache, зашитая вёрстка |
| 5. Темы и переводы | 21–25 | Light/dark palette, Tanker/REX локализация |
| 6. Версионирование и A/B | 26–31 | Protocol versioning, AB-flags, Flagman |
| 7. Паттерны | 32–36 | Backend patterns, client patterns |
| 8. Финал | 37–38 | Итог и вопросы |

---

## Слайд за слайдом

---

### Слайд 1 — Титульный
**Лейаут:** `title`
**Key points:**
- BDUI и DivKit: опыт работы в большом проекте
- Как это устроено в Яндекс Браузере и bromapi

**Diagram/visual:** Логотипы DivKit + Браузер + мобильный экран с Мордой.

**Speaker emphasis:** Сегодня не теория — живой продакшн-опыт с кодом и болями.

---

### Слайд 2 — Содержание
**Лейаут:** `agenda`
**Key points:**
- Архитектура: кто что делает
- Несколько экранов, дозапросы, состояние
- Офлайн, зашитая вёрстка, темы, переводы
- Версионирование и A/B-эксперименты
- Паттерны бэкенда и клиента

**Speaker emphasis:** Каждая тема — решение реальной боли.

---

### Слайд 3 — BDUI: быстрое напоминание (2 мин)
**Лейаут:** `compare`
**Key points:**
- Левая колонка «Без BDUI»: UI захардкожен в клиенте, обновление = новый релиз APK
- Правая колонка «BDUI»: UI описан на сервере в декларативном DSL, клиент только рендерит
- DivKit — конкретная реализация: JSON/Protobuf → нативный View
- Flex/Tovarisch — слой между бэкендом и DivKit в Яндексе

**Diagram/visual:** Две колонки: слева — «Клиент диктует», справа — «Бэкенд диктует». В правой: бэкенд → JSON → рендерер → View.

**Speaker emphasis:** Студенты уже знают это — слайд только фиксирует терминологию.

---

### Слайд 4 — [РАЗДЕЛ] Архитектура системы
**Лейаут:** `quote`
**Key points:**
- «Мы не знаем данные. Мы только рендерим.»
- bromapi — узел в графе запросов, не самостоятельный сервис

**Speaker emphasis:** Этот тезис — центральный для понимания архитектуры.

---

### Слайд 5 — AppHost: запрос как граф
**Лейаут:** `blank`
**Key points:**
- AppHost — оркестратор микросервисов Яндекса
- Запрос разбивается на граф вершин; каждая вершина — отдельный сервис
- bromapi — одна из вершин графа Морды; данные получает от Morda-Go

**Diagram/visual:**
```
Клиент (Android Browser)
        │  HTTP/Protobuf
        ▼
    AppHost граф
   ┌─────────────────────────────────────┐
   │  Morda-Go ──────────────► BROMAPI   │
   │     │           proto-блоки    │    │
   │  [другие вершины: данные,    │    │
   │   поиск, персонализация...]  │    │
   └──────────────────────────────┼────┘
                                   │  JSON
                                   ▼
                              Клиент рендерит
```
Стрелки: входные proto-блоки в bromapi от Morda-Go; выходной JSON от bromapi к клиенту.

**Speaker emphasis:** bromapi никогда не ходит за данными сам — данные приносит граф.

---

### Слайд 6 — Изнутри bromapi: Servants и Renderers
**Лейаут:** `stages`
**Key points:**
- AppHostService → Servant (по RequestType) → Renderers → JSON
- Каждый тип запроса — отдельный Servant: MordaResponseServant, SearchappProfileServant, BottomSheetServant, YazekaServant…
- Renderer — composable-кусочек вёрстки: получает данные, возвращает DivKit-структуру
- Зависимости — через Koin (DI); request-scope изолирован от singleton-scope

**Diagram/visual:** Линейная схема:
`[AppHostService]` → `[Servant: определяет тип запроса]` → `[Renderer A]` + `[Renderer B]` + `[Renderer C]` → `[JSON Response]`
Renderer — маленький прямоугольник с подписью «получает proto-данные → возвращает DivData».

**Speaker emphasis:** Один Servant = одна поверхность. Renderers — детали, их много.

---

### Слайд 7 — Откуда данные: MADM и граф
**Лейаут:** `header_text`
**Key points:**
- Morda-Go собирает данные из MADM (key-value хранилище), баз, поиска
- bromapi получает готовые Protobuf-блоки: user_profile_block, morda_header_block, alerts_block…
- Бэкенд не знает, что нужно клиенту — граф конфигурируется снаружи
- Пример: `DivDataBlock` — готовая DivKit-карточка из MADM, бэкенд просто пробрасывает

**Diagram/visual:** Схема потока данных (по мотивам docs/data/index.md):
```
MADM ──► Morda-Go ──► bromapi ──► Клиент
             ▲
         Tanker (i18n)
             ▲
   [REX: runtime обновления]
```

**Speaker emphasis:** MADM — это «настройки» продукта: фиды, div-карточки, AB-флаги — всё там.

---

### Слайд 8 — Модули bromapi: карта проекта
**Лейаут:** `cards`
**Key points:**
- `api/` — Protobuf-схемы входа/выхода
- `app/` — servants + surface-рендереры (только интерфейсы, сами рендереры — в модулях)
- `renderer-commons/` / `morda-renderer/` — переиспользуемая логика рендеринга
- `data/` — модели данных, AB-флаги; `palette/` — цвета; `tovarisch-compat/` — DivKit-сериализация

**Diagram/visual:** Сетка карточек: `api`, `app`, `data`, `renderer-commons`, `morda-renderer`, `palette`, `tovarisch-compat`, `searchapp_services`, `testing/`.

**Speaker emphasis:** Правило: не-servant-ный код в `app/` — это техдолг.

---

### Слайд 9 — DivKit DSL: как пишем вёрстку на Kotlin
**Лейаут:** `header_text`
**Key points:**
- `divan { }` — top-level builder, внутри `DivScope`
- `container()`, `text()`, `image()`, `gallery()` — компоненты
- Templates: объявить один раз → рендерить много раз с разными данными (экономия payload)
- Expressions: `variable equalTo "value"`, `condition.ifElse("visible", "gone")`

**Diagram/visual:** Псевдокод-блок (не реальный код, читабельный):
```
divan {
  data(logId = "block") {
    container {
      text(text = titleRef)     // template reference
      image().evaluate(
        visibility = isVisible.visibleElseGone()
      )
    }
  }
}
```
Стрелка: «Backend → JSON → Client renders».

**Speaker emphasis:** DSL — это Kotlin, но на выходе — JSON. Клиент не видит Kotlin.

---

### Слайд 10 — [РАЗДЕЛ] Несколько экранов
**Лейаут:** `quote`
**Key points:**
- «Один запрос к бэкенду — один экран? Не всегда.»

**Speaker emphasis:** Реальная морда — это несколько "документов" за один запрос.

---

### Слайд 11 — Multiple screens: RequestType → Document
**Лейаут:** `blank`
**Key points:**
- Один запрос к AppHost может вернуть несколько Flex Document
- RequestType разделяет поверхности: `MORDA`, `SEARCHAPP_PROFILE`, `BOTTOM_SHEET`, `YAZEKA`
- Клиент (FlexNtpDocumentLoader) управляет жизненным циклом каждого документа независимо
- Preloader: документы начинают грузиться до того, как пользователь их откроет

**Diagram/visual:** Схема «один запрос, несколько документов»:
```
Запрос пользователя
        │
        ▼
  AppHost Router
  ┌─────┬──────────┬────────────┬──────────┐
  │Morda│ Profile  │BottomSheet │  Yazeka  │
  └──┬──┴────┬─────┴─────┬──────┴─────┬───┘
     │       │           │            │
  ServantA ServantB  ServantC      ServantD
     │       │           │            │
  Doc#1   Doc#2       Doc#3        Doc#4
```

**Speaker emphasis:** Каждый документ — независимый DOM с переменными и состоянием.

---

### Слайд 12 — Предзагрузка и skeleton
**Лейаут:** `two_blocks`
**Key points:**
- Левый блок «Preloader»: FlexNtpPreloader начинает загрузку при старте приложения; документ ждёт в памяти, показывается мгновенно
- Правый блок «Skeleton»: пока документ загружается, показывается skeleton-анимация (SkeletonContentController); skeleton — тоже DivKit-JSON, зашитый в apk

**Speaker emphasis:** Пользователь не видит «пустой экран» — показывается либо skeleton, либо fallback.

---

### Слайд 13 — [РАЗДЕЛ] Дозапросы
**Лейаут:** `quote`
**Key points:**
- «Не всё нужно сразу. Кое-что — только по действию пользователя.»

**Speaker emphasis:** Дозапросы — способ держать первый ответ маленьким и быстрым.

---

### Слайд 14 — NetworkRequestAction: дозапрос из DivKit
**Лейаут:** `blank`
**Key points:**
- Действие пользователя (тап, скролл до элемента) → DivKit-action → NetworkRequestAction
- Клиент (NetworkRequestActionHandler) делает HTTP-запрос к нужному URL
- В ответ приходит новый Flex Document или патч к существующему
- ReferenceReplaceSectionAction: точечная замена секции без перерисовки всего экрана

**Diagram/visual:**
```
[Пользователь: тап на "Показать больше"]
        │
        ▼
[DivKit Action: NetworkRequestAction]
        │  url: /api/more_content
        ▼
[NetworkRequestActionHandler (клиент)]
        │  HTTP GET
        ▼
[bromapi: новый Servant/Renderer]
        │  JSON с новыми блоками
        ▼
[ReferenceReplaceSectionAction → обновить секцию]
```

**Speaker emphasis:** Бэкенд кодирует URL прямо в вёрстке — клиент просто его зовёт.

---

### Слайд 15 — Паттерн «дозапрос с состоянием»
**Лейаут:** `stages`
**Key points:**
- Шаг 1: Backend кладёт в вёрстку переменную `is_loading = false`
- Шаг 2: Тап → SetVariableAction(`is_loading = true`) + NetworkRequestAction
- Шаг 3: Пока грузится — клиент показывает спиннер через expression `is_loading.visibleElseGone()`
- Шаг 4: По успеху — onSuccessAction обновляет данные и сбрасывает `is_loading`

**Speaker emphasis:** Весь UX загрузки описан декларативно в JSON — без нативного кода.

---

### Слайд 16 — [РАЗДЕЛ] Состояние и хранение
**Лейаут:** `quote`
**Key points:**
- «Кто хранит состояние? Клиент. Кто его объявляет? Бэкенд.»

**Speaker emphasis:** Это ключевое разделение ответственности в DivKit.

---

### Слайд 17 — DivKit Variables: объявление и использование
**Лейаут:** `header_text`
**Key points:**
- Бэкенд объявляет переменные: `"theme".stringVariable()`, `"is_tablet".booleanVariable()`
- Клиент управляет значениями: DivVariableController.putOrUpdate(...)
- Expressions читают переменные: `theme equalTo "dark"` → другой цвет фона
- CommonVars: глобальные переменные (тема, ширина экрана, search_engine) — объявлены в `renderer-commons/`, знают и бэкенд, и клиент

**Diagram/visual:** Двусторонняя схема:
```
BACKEND (Kotlin):
  val THEME = "theme".stringVariable()  // объявляет
  container().evaluate(bg = THEME.ifLight("#FFF", "#222"))

CLIENT (Android):
  variableController.putOrUpdate(Variable.StringVariable("theme", "dark"))
  // → DivKit пересчитывает expressions
```

**Speaker emphasis:** Переменная — контракт. Бэкенд создаёт, клиент управляет.

---

### Слайд 18 — Кэширование на клиенте
**Лейаут:** `stages`
**Key points:**
- Шаг 1: Первая загрузка — документ сохраняется в disk cache (FlexNtpNetworkCaching)
- Шаг 2: При повторном открытии — документ из кэша, запрос на обновление в фоне
- Шаг 3: Если кэш протух — показывается hardcoded fallback до загрузки
- LazyNetworkCaching: обновляет кэш только если пользователь открыл экран

**Speaker emphasis:** Cache-first стратегия — пользователь никогда не ждёт с нуля.

---

### Слайд 19 — Зашитая вёрстка (Hardcoded Fallback)
**Лейаут:** `blank`
**Key points:**
- `searchapp_flex_ntp.json`, `mobileyandex_div_profile.json` — лежат прямо в `res/raw/` APK
- FlexNtpDocumentCacheFallbacksProvider: если кэша нет — парсит JSON из ресурсов
- При первом запуске (до первой успешной загрузки) — показывается именно зашитая вёрстка
- Обновляется с релизом APK; не обновляется через сервер

**Diagram/visual:**
```
Открытие экрана
        │
        ▼
  [Есть кэш?] ──YES──► [Показать кэш] ──► [Фоновое обновление]
        │NO
        ▼
  [Есть raw JSON в APK?] ──YES──► [Показать fallback]
        │NO
        ▼
  [Skeleton / Error state]
```
Примечание: `AT_LEAST_ONE_DOCUMENT_LOADED` — флаг в SharedPreferences, отслеживающий первую загрузку.

**Speaker emphasis:** Hardcoded JSON — это страховка, не фича. Он всегда устаревший, но лучше пустого экрана.

---

### Слайд 20 — Офлайн: OfflineNtpFeature
**Лейаут:** `two_blocks`
**Key points:**
- Левый блок «Что происходит без сети»: disk cache → fallback JSON → offline-режим (игры, сохранённые страницы)
- Правый блок «OfflineNtpFeature»: флаг включает offline-контент вместо страницы ошибки сети; параметр `preload_games = true` — игры грузятся заранее

**Speaker emphasis:** Offline не «выключить нет сети», а полноценный экран с контентом.

---

### Слайд 21 — [РАЗДЕЛ] Темы экрана
**Лейаут:** `quote`
**Key points:**
- «Light, dark, incognito — три состояния одного экрана.»

**Speaker emphasis:** Тема — это тоже переменная. Только очень специальная.

---

### Слайд 22 — Как работает темизация в DivKit
**Лейаут:** `blank`
**Key points:**
- Palette — набор именованных цветов: `icon/default/secondary`, `bg/primary`
- Клиент хранит palette в `DivVariableController` как Color-переменные
- Бэкенд пишет выражение: `Colors.Background.PRIMARY.asDefaultPaletteExpression()`
- DivPaletteUpdater следит за сменой темы и обновляет все Color-переменные разом

**Diagram/visual:** Схема обновления темы:
```
[Пользователь: System Dark Mode ON]
        │
        ▼
[DarkThemeStateController]
        │
        ▼
[BaseFlexThemeController.updateThemeInFlex()]
        │ feedSdkComponent.themeController.themeId = "dark"
        ▼
[DivPaletteUpdater.updateTheme()]
        │ variableController.putOrUpdate(bg/primary → #1C1C1E)
        ▼
[DivKit пересчитывает expressions → UI обновляется]
```

**Speaker emphasis:** Смена темы — это одна операция: обновить все Color-переменные.

---

### Слайд 23 — Палитра на бэкенде: `palette/`
**Лейаут:** `header_text`
**Key points:**
- Модуль `palette/` в bromapi — единая палитра light/dark
- `Color.themed()` — extension, возвращающий `ExpressionProperty<Color>` с ветвлением по теме
- `ifThemeIsLight(onMatch, onMismatch)` — helper из `DivExpressionUtils`
- Бэкенд и клиент договорились о названиях переменных — это и есть "дизайн-система"

**Speaker emphasis:** Цвет нельзя захардкодить — только через themed()-expression или palette variable.

---

### Слайд 24 — [РАЗДЕЛ] Переводы (i18n)
**Лейаут:** `quote`
**Key points:**
- «Строка должна жить в Tanker, не в коде.»

**Speaker emphasis:** Перевод — релиз без релиза сервиса.

---

### Слайд 25 — Как работает локализация в bromapi
**Лейаут:** `stages`
**Key points:**
- Шаг 1: Разработчик добавляет ключ в Tanker (проект `home`), пишет текст по-русски
- Шаг 2: 1-го и 15-го — переводчики автоматически получают задачу; переводы проставляются в ~13 языках
- Шаг 3: `lang-auto.json` выкатывается отдельным релизом Morda Localization (~ежедневно)
- Шаг 4: REX доставляет свежий `lang-auto.json` в bromapi без перевыкладки сервиса (за несколько минут)
- Код: `l10n.getOrDefault("keyset.key", "fallback")`

**Diagram/visual:**
```
[Tanker: кейсет/ключ/значение]
        │ авто-задача переводчику
        ▼
[lang-auto.json в Arcadia]
        │ релиз Morda Localization
        ▼
[REX → bromapi (runtime, без рестарта)]
        │
        ▼
[l10n.getOrDefault(...) в Renderer]
```

**Speaker emphasis:** Переводы обновляются независимо от релиза сервиса — это важная архитектурная особенность.

---

### Слайд 26 — [РАЗДЕЛ] Версионирование
**Лейаут:** `quote`
**Key points:**
- «Клиент обновляется медленно. Бэкенд — быстро. Как жить вместе?»

**Speaker emphasis:** Самая больная тема в BDUI-продакшне.

---

### Слайд 27 — Три уровня версионирования
**Лейаут:** `three_blocks`
**Key points:**
- Блок 1 «Протокол»: Protobuf-схемы входа/выхода версионируются отдельно от релизов сервиса; поле может стать obsolete, но не может быть сломано
- Блок 2 «Layout/DSL»: DivKit-компонент (например, `div-action`) — если добавить новое поле, старые клиенты просто его игнорируют
- Блок 3 «Бизнес-логика»: AB-флаги закрывают «новую фичу только для клиентов >= N»; minVersion-проверка в бэкенде

**Speaker emphasis:** Три уровня независимы — можно менять их по отдельности.

---

### Слайд 28 — Версионирование layout: что клиент не поймёт
**Лейаут:** `compare`
**Key points:**
- Левая колонка «Безопасно»: новые опциональные поля, новые типы action, новые переменные, additive changes
- Правая колонка «Опасно»: удалить обязательное поле, изменить семантику существующего action, изменить имя переменной

**Diagram/visual:** Простая таблица 2 столбца: зелёный — безопасно, красный — опасно. По 3 примера в каждом.

**Speaker emphasis:** Всегда думайте: «Что увидит клиент двухлетней давности?»

---

### Слайд 29 — [РАЗДЕЛ] A/B-эксперименты
**Лейаут:** `quote`
**Key points:**
- «Как проверить гипотезу на 1% пользователей, не катив релиз?»

**Speaker emphasis:** AB и флаги — хлеб и масло любого продукта.

---

### Слайд 30 — AB-флаги в bromapi: два пути
**Лейаут:** `two_blocks`
**Key points:**
- Левый блок «Через AB-эксперименты (рекомендуется)»: создаётся выборка с хендлером APPSEARCH; флаг попадает в Core-блок от Morda-Go; bromapi читает `AbFlags` из входного proto; метрики работают автоматически
- Правый блок «Через MADM (легаси)»: добавить в экспорт `ab_flags_v2`; работает, но метрики эксперимента не собираются — нерекомендуемый способ

**Diagram/visual:** Два пути стрелками:
```
ПУТЬ 1: AB-система → Core-блок → Morda-Go → bromapi.AbFlags → if (flag) renderVariantA()
ПУТЬ 2: MADM.ab_flags_v2 → Morda-Go → bromapi.AbFlags → if (flag) renderVariantA()
```

**Speaker emphasis:** Если нет метрик — нет смысла в эксперименте.

---

### Слайд 31 — Flagman: контролируемая раскатка
**Лейаут:** `header_text`
**Key points:**
- Flagman — отдельная система контроля показов и кликов
- `flagman_flags` экспорт в MADM → bromapi проверяет `flagman.isEnabled("flag_id")`
- Позволяет: постепенно раскатывать (%, регион, девайс), откатить в 1 клик без релиза
- Отличие от AB: AB — сравниваем две версии; Flagman — включаем/выключаем фичу

**Speaker emphasis:** Flagman — аварийный рубильник. AB — научный эксперимент.

---

### Слайд 32 — [РАЗДЕЛ] Паттерны проектирования
**Лейаут:** `quote`
**Key points:**
- «За всеми решениями стоят одни и те же идеи.»

**Speaker emphasis:** Паттерны — это не теория, это то, что мы используем каждый день.

---

### Слайд 33 — Паттерны бэкенда: Servant + Renderer
**Лейаут:** `numbered`
**Key points:**
1. **Servant per Surface**: один класс — один тип запроса; нет god-class, обрабатывающего всё
2. **Renderer Composition**: экран = сумма маленьких Renderer-ов; каждый тестируется отдельно через `RendererTest<IMyRenderer>`
3. **Template DSL**: повторяющиеся блоки — template + reference + defer; payload уменьшается в разы
4. **No Business Logic in Renderer**: Renderer получает готовые данные из proto; если нужна логика — она в Adapter, не в Renderer

**Speaker emphasis:** Маленькие кусочки лучше одного монстра — их проще тестировать и менять.

---

### Слайд 34 — Паттерны бэкенда: данные и зависимости
**Лейаут:** `numbered`
**Key points:**
1. **Кoin Request Scope**: зависимости живут ровно один запрос; нет утечек состояния между запросами
2. **Interface + @RequireTests**: каждый renderer обязан иметь интерфейс + интеграционный тест; CI проверяет автоматически
3. **Декларативная версионная логика**: проверка версии клиента — в одном месте (AbFlags или условие в Servant), не размазана по Renderer-ам
4. **Variables as Contract**: переменные — это публичный API между бэкендом и клиентом; менять их имена = breaking change

**Speaker emphasis:** Структура кода — не про красоту, а про поддерживаемость через год.

---

### Слайд 35 — Паттерны клиента: Document lifecycle
**Лейаут:** `numbered`
**Key points:**
1. **Cache-First + Background Refresh**: показать кэш немедленно, обновить в фоне
2. **Fallback Chain**: network → disk cache → hardcoded JSON → skeleton → error
3. **Variables as Single Source of Truth**: состояние UI (тема, флаги, авторизация) — только через DivVariableController; нет параллельных state-holders
4. **Action-driven Requests**: дозапрос кодируется как DivKit-action в JSON; клиент не знает о бизнес-логике

**Speaker emphasis:** Клиент тупой — это хорошо. Вся логика на сервере.

---

### Слайд 36 — Паттерны клиента: тема и offline
**Лейаут:** `numbered`
**Key points:**
1. **Theme as Variables**: тема не перерисовывает View — она обновляет Color-переменные, DivKit пересчитывает сам
2. **OfflineNtpFeature**: offline — это отдельный content-type, а не «ошибка сети»; планировать заранее
3. **Skeleton as DivKit**: loading-анимация — тоже DivKit JSON (SkeletonContent); не нативный View
4. **MordaUpdateMonitor**: следит за тем, когда стоит обновить документ (открыт экран + прошло N минут)

**Speaker emphasis:** Все эти паттерны — про то, чтобы пользователь никогда не видел пустой экран.

---

### Слайд 37 — Итог: что взять с собой
**Лейаут:** `big_thesis`
**Key points:**
- BDUI — это контракт: бэкенд объявляет, клиент исполняет
- Variables + Expressions — замена нативному состоянию
- Fallback chain — страховая сетка; думайте о ней заранее
- Версионирование и AB-флаги — жизнь без синхронного релиза клиента и бэкенда
- Архитектура: маленькие Servant/Renderer лучше, чем монолит

**Speaker emphasis:** Большой проект отличается от tutorial-а именно этими темами.

---

### Слайд 38 — Вопросы
**Лейаут:** `questions`
**Key points:**
- Ссылка: docs.yandex-team.ru/bromapi (если студенты из Яндекса)
- GitHub DivKit: github.com/divkit/divkit
- QR-код на слайд-деке

**Speaker emphasis:** Вопросы — самая ценная часть лекции.

---

## Карта покрытия требуемых тем

| Требуемая тема | Слайды |
|---|---|
| Краткое напоминание BDUI и DSL | 3, 9 |
| Несколько экранов (multiple screens) | 10, 11, 12 |
| Дозапросы (additional/lazy requests) | 13, 14, 15 |
| Хранение состояния (client-side state) | 16, 17, 18 |
| Зашитая вёрстка (hardcoded layout fallback) | 19 |
| Работа offline | 20 |
| Темы экрана (light/dark theming) | 21, 22, 23 |
| Переводы (localization / i18n) | 24, 25 |
| Версионирование (versioning) | 26, 27, 28 |
| Эксперименты (A/B, flags) | 29, 30, 31 |
| Архитектура бэкенда (AppHost node, servants, renderers, данные не наши) | 4, 5, 6, 7, 8 |
| Паттерны BDUI: бэкенд | 32, 33, 34 |
| Паттерны BDUI: клиент | 32, 35, 36 |

---

## Временная разбивка (ориентир)

| Блок | Слайды | Время |
|---|---|---|
| Разогрев | 1–3 | 4 мин |
| Архитектура системы | 4–9 | 12 мин |
| Несколько экранов | 10–12 | 5 мин |
| Дозапросы | 13–15 | 6 мин |
| Состояние и офлайн | 16–20 | 9 мин |
| Темы и переводы | 21–25 | 8 мин |
| Версионирование и A/B | 26–31 | 8 мин |
| Паттерны | 32–36 | 6 мин |
| Итог + вопросы | 37–38 | 2 мин + вопросы |
| **Итого** | **38 слайдов** | **~60 мин** |
