"""build_deck.py — 38-slide BDUI/DivKit lecture deck.

Lessons learned from render inspection:
- `stages` layout: only 2 pill body placeholders work; remaining go to narrow
  non-pill spots. Use blank for all stage-style slides.
- `numbered` layout: has a decorative image placeholder + ultra-narrow text
  placeholder. Use blank for all numbered slides.
- `agenda` layout: body placeholder is ~1cm wide. Use blank.
- `three_blocks` layout: first block text goes to wrong placeholder. Use blank.
- `big_thesis` layout: text too large. Use blank.
- `blank` + free-form cards is the safe universal approach.
"""

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

import deck

OUT = "/Users/the-leo/divkit-weather-workshop/talk/bdui_divkit.pptx"

prs = deck.new_deck()

# ---------------------------------------------------------------------------
# Helpers for blank-canvas slides
# ---------------------------------------------------------------------------

def slide_title(s, text, size=30):
    """Standard title for blank slides."""
    deck.textbox(s, 3.8, 1.4, 60.0, 3.0, text, size=size, bold=True, color="ink")


def step_cards(s, steps, start_y=5.5, box_w=52.0, box_x=7.5,
               box_h=4.0, gap=5.2, colors=None):
    """Vertical sequence of cards with red arrows between them."""
    default_colors = ["pink_bg", "lilac_bg", "yellow_bg", "lilac_bg",
                      "pink_bg", "yellow_bg"]
    if colors is None:
        colors = default_colors
    for i, label in enumerate(steps):
        y = start_y + i * gap
        fill = colors[i % len(colors)]
        deck.card_with_text(s, box_x, y, box_w, box_h,
                            title=label, fill=fill, title_size=16)
        if i < len(steps) - 1:
            mid_x = box_x + box_w / 2
            deck.arrow(s, mid_x, y + box_h, mid_x, y + gap, color="red")


def numbered_cards(s, items, title_text,
                   card_w=58.0, card_h=5.8, start_y=5.5, gap=6.5,
                   colors=None):
    """4 numbered horizontal cards stacked vertically."""
    default_colors = ["lilac_bg", "pink_bg", "yellow_bg", "lilac_bg"]
    if colors is None:
        colors = default_colors
    slide_title(s, title_text)
    for i, (heading, body) in enumerate(items):
        y = start_y + i * gap
        fill = colors[i % len(colors)]
        deck.card(s, 3.8, y, card_w, card_h, fill=fill)
        deck.number_badge(s, 3.8, y + card_h / 2 - 0.8, i + 1)
        deck.textbox(s, 7.0, y + 0.8, card_w - 3.5, 2.2,
                     heading, size=17, bold=True, color="ink")
        deck.textbox(s, 7.0, y + 3.0, card_w - 3.5, 2.4,
                     body, size=14, color="ink2")


# ---------------------------------------------------------------------------
# SLIDE 1 — Title
# ---------------------------------------------------------------------------
s = deck.add(prs, "title_plain")
deck.set_title(s, "BDUI и DivKit")
deck.fill_bodies(s, [
    "Опыт работы в большом проекте",
    "Яндекс Браузер · bromapi",
])

# ---------------------------------------------------------------------------
# SLIDE 2 — Agenda (blank, free-form numbered list)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Содержание", size=34)

items = [
    ("Архитектура: кто что делает", "AppHost, bromapi, servants, renderers"),
    ("Несколько экранов, дозапросы, состояние", "RequestType, NetworkRequestAction, Variables"),
    ("Офлайн, зашитая вёрстка, темы, переводы", "Fallback chain, palette, Tanker/REX"),
    ("Версионирование и A/B-эксперименты", "Protocol versions, AB-flags, Flagman"),
    ("Паттерны бэкенда и клиента", "Servant/Renderer, Cache-First, Action-driven"),
]

for i, (main, sub) in enumerate(items):
    y = 6.0 + i * 5.8
    deck.card(s, 3.8, y, 60.0, 5.0, fill="lilac_bg" if i % 2 == 0 else "pink_bg")
    deck.number_badge(s, 3.8, y + 1.0, i + 1)
    deck.textbox(s, 7.2, y + 0.6, 55.0, 2.2,
                 main, size=18, bold=True, color="ink")
    deck.textbox(s, 7.2, y + 2.8, 55.0, 1.8,
                 sub, size=14, color="ink2")

# ---------------------------------------------------------------------------
# SLIDE 3 — BDUI: быстрое напоминание
# ---------------------------------------------------------------------------
s = deck.add(prs, "compare")
deck.set_title(s, "BDUI: быстрое напоминание")
deck.fill_bodies(s, [
    [
        "Без BDUI",
        "UI захардкожен в клиенте",
        "Обновление = новый релиз APK",
        "Бэкенд не управляет вёрсткой",
    ],
    [
        "С BDUI (DivKit)",
        "UI описан на сервере в JSON",
        "Клиент только рендерит",
        "Flex/Tovarisch — слой над DivKit",
    ],
])

# ---------------------------------------------------------------------------
# SLIDE 4 — [РАЗДЕЛ] Архитектура системы
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Архитектура системы")
deck.fill_bodies(s, [
    "«Мы не знаем данные.\nМы только рендерим.»",
])

# ---------------------------------------------------------------------------
# SLIDE 5 — AppHost: запрос как граф
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "AppHost: запрос как граф")

# Client box
deck.card_with_text(s, 20.0, 4.8, 28.0, 4.5,
                    title="Клиент (Android Browser)",
                    body="HTTP / Protobuf запрос",
                    fill="lilac_bg", title_size=18, body_size=14)

# Arrow down
deck.arrow(s, 34.0, 9.3, 34.0, 11.5, color="ink2")

# AppHost graph border
deck.card(s, 5.0, 11.5, 57.0, 14.0, fill="pink_bg")
deck.textbox(s, 6.0, 12.2, 20.0, 2.0,
             "AppHost граф", size=16, bold=True, color="ink")

# Morda-Go box inside AppHost
deck.card_with_text(s, 8.0, 14.5, 18.0, 8.0,
                    title="Morda-Go",
                    body="данные, поиск,\nперсонализация,\nMADM",
                    fill="yellow_bg", title_size=17, body_size=14)

# Arrow right Morda-Go → BROMAPI
deck.arrow(s, 26.0, 18.5, 33.0, 18.5, color="red")
deck.textbox(s, 26.5, 16.8, 6.0, 1.8, "proto-блоки", size=12, color="ink2")

# BROMAPI box inside AppHost
deck.card_with_text(s, 33.0, 14.5, 18.0, 8.0,
                    title="BROMAPI",
                    body="Servant →\nRenderer → JSON",
                    fill="lilac_bg", title_size=17, body_size=14)

# Arrow down from BROMAPI out of AppHost
deck.arrow(s, 42.0, 25.5, 42.0, 28.0, color="ink2")

# Client renders
deck.card_with_text(s, 28.0, 28.0, 24.0, 5.5,
                    title="Клиент рендерит",
                    body="JSON → DivKit → нативный View",
                    fill="lilac_bg", title_size=18, body_size=14)

deck.textbox(s, 3.8, 34.5, 60.0, 2.0,
             "bromapi никогда не ходит за данными сам — данные приносит граф",
             size=14, color="ink2", align="center")

# ---------------------------------------------------------------------------
# SLIDE 6 — Изнутри bromapi: Servants и Renderers (blank, step cards)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Изнутри bromapi: Servants и Renderers")

steps_6 = [
    "AppHostService — получает входящий запрос",
    "Servant — определяет тип запроса (RequestType)",
    "Renderers — компонуемые кусочки вёрстки (получают proto → возвращают DivData)",
    "JSON Response — DivKit-структура отправляется клиенту",
]
step_cards(s, steps_6, start_y=5.5, box_w=58.0, box_x=4.5, box_h=5.0, gap=6.5)

# ---------------------------------------------------------------------------
# SLIDE 7 — Откуда данные: MADM и граф
# ---------------------------------------------------------------------------
s = deck.add(prs, "header_text")
deck.set_title(s, "Откуда данные: MADM и граф")
deck.fill_bodies(s, [[
    "MADM — key-value хранилище: фиды, div-карточки, AB-флаги",
    "Morda-Go собирает данные → отдаёт bromapi готовые Protobuf-блоки",
    "bromapi не знает, что нужно клиенту — граф конфигурируется снаружи",
    "DivDataBlock — готовая DivKit-карточка из MADM, бэкенд пробрасывает",
    "Поток: MADM → Morda-Go → bromapi → Клиент",
]])

# ---------------------------------------------------------------------------
# SLIDE 8 — Модули bromapi: карта проекта
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Модули bromapi: карта проекта")

modules_row1 = [
    ("api/", "Protobuf-схемы\nвхода/выхода"),
    ("app/", "Servants +\nSurface renderers"),
    ("data/", "Модели данных,\nAB-флаги"),
    ("palette/", "Цвета\nlight/dark"),
]
modules_row2 = [
    ("renderer-commons/", "Базовые классы\nрендеринга"),
    ("morda-renderer/", "Рендереры\nМорды"),
    ("tovarisch-compat/", "DivKit\nсериализация"),
    ("testing/", "Моки,\nинтегр. тесты"),
]

card_w = 14.0
card_h = 7.5
gap = 1.3
start_x = 3.8

for i, (mod_name, mod_body) in enumerate(modules_row1):
    x = start_x + i * (card_w + gap)
    deck.card_with_text(s, x, 5.0, card_w, card_h,
                        title=mod_name, body=mod_body,
                        fill="lilac_bg", title_size=16, body_size=13)

for i, (mod_name, mod_body) in enumerate(modules_row2):
    x = start_x + i * (card_w + gap)
    deck.card_with_text(s, x, 13.5, card_w, card_h,
                        title=mod_name, body=mod_body,
                        fill="pink_bg", title_size=16, body_size=13)

deck.textbox(s, 3.8, 22.2, 56.0, 2.2,
             "Правило: не-servant-ный код в app/ — это техдолг",
             size=15, color="red", bold=True)

deck.chip(s, 3.8, 25.0, "searchapp_services/", fill="yellow", color="ink")
deck.chip(s, 25.0, 25.0, "yazeka-app/", fill="yellow", color="ink")
deck.chip(s, 40.0, 25.0, "util/ · logging/ · koin-compat/", fill="grey", color="ink")

# ---------------------------------------------------------------------------
# SLIDE 9 — DivKit DSL: как пишем вёрстку на Kotlin
# ---------------------------------------------------------------------------
s = deck.add(prs, "header_text")
deck.set_title(s, "DivKit DSL: вёрстка на Kotlin")
deck.fill_bodies(s, [[
    "divan { } — top-level builder, внутри DivScope",
    "container(), text(), image(), gallery() — компоненты",
    "Templates: объявить один раз → рендерить с разными данными",
    "Expressions: variable equalTo \"value\" → другой цвет/видимость",
    "Выход DSL: JSON. Клиент не видит Kotlin.",
]])

# ---------------------------------------------------------------------------
# SLIDE 10 — [РАЗДЕЛ] Несколько экранов
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Несколько экранов")
deck.fill_bodies(s, [
    "«Один запрос к бэкенду —\nодин экран? Не всегда.»",
])

# ---------------------------------------------------------------------------
# SLIDE 11 — Multiple screens
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Multiple screens: RequestType → Document", size=28)

deck.card_with_text(s, 19.0, 4.8, 30.0, 4.5,
                    title="Запрос пользователя",
                    body="Один HTTP запрос к AppHost",
                    fill="lilac_bg", title_size=18, body_size=14)
deck.arrow(s, 34.0, 9.3, 34.0, 11.5, color="ink2")

# AppHost Router bar
deck.card(s, 3.5, 11.5, 60.0, 3.5, fill="pink_bg")
deck.textbox(s, 4.5, 12.3, 20.0, 2.0,
             "AppHost Router", size=16, bold=True, color="ink")

servant_labels = ["Morda", "Profile", "BottomSheet", "Yazeka"]
servant_colors = ["lilac_bg", "pink_bg", "yellow_bg", "lilac_bg"]
sx_list = [4.5, 18.5, 32.5, 46.5]
sw = 12.5

for i, (label, col, sx) in enumerate(zip(servant_labels, servant_colors, sx_list)):
    mid = sx + sw / 2
    # Arrow from router bar to servant
    deck.arrow(s, mid, 15.0, mid, 16.5, color="ink2")
    # Servant box
    deck.card_with_text(s, sx, 16.5, sw, 5.5,
                        title=f"Servant", body=label,
                        fill=col, title_size=15, body_size=16)
    # Arrow servant → doc
    deck.arrow(s, mid, 22.0, mid, 23.5, color="ink2")
    # Doc box
    deck.card_with_text(s, sx, 23.5, sw, 5.5,
                        title=f"Doc #{i+1}", body=f"Flex Document\n{label}",
                        fill="yellow_bg", title_size=14, body_size=13)

deck.textbox(s, 3.8, 30.2, 60.0, 2.0,
             "Каждый документ — независимый DOM с переменными и состоянием",
             size=14, color="ink2", align="center")

# ---------------------------------------------------------------------------
# SLIDE 12 — Предзагрузка и skeleton
# ---------------------------------------------------------------------------
s = deck.add(prs, "two_blocks")
deck.set_title(s, "Предзагрузка и skeleton")
deck.fill_bodies(s, [
    [
        "Preloader",
        "FlexNtpPreloader стартует при запуске",
        "Документ ждёт в памяти",
        "Показывается мгновенно при открытии",
    ],
    [
        "Skeleton",
        "Пока документ грузится — skeleton-анимация",
        "SkeletonContentController управляет показом",
        "Skeleton — тоже DivKit-JSON, зашитый в apk",
    ],
])

# ---------------------------------------------------------------------------
# SLIDE 13 — [РАЗДЕЛ] Дозапросы
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Дозапросы")
deck.fill_bodies(s, [
    "«Не всё нужно сразу.\nКое-что — только по действию пользователя.»",
])

# ---------------------------------------------------------------------------
# SLIDE 14 — NetworkRequestAction (blank, flowchart)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "NetworkRequestAction: дозапрос из DivKit", size=27)

steps_14 = [
    "Пользователь: тап «Показать больше»",
    "DivKit Action: NetworkRequestAction\nurl: /api/more_content",
    "NetworkRequestActionHandler (клиент) — HTTP GET",
    "bromapi: новый Servant/Renderer → JSON с новыми блоками",
    "ReferenceReplaceSectionAction → обновить секцию",
]
step_cards(s, steps_14, start_y=5.0, box_w=54.0, box_x=6.5,
           box_h=4.2, gap=5.5)

deck.textbox(s, 3.8, 33.5, 60.0, 2.0,
             "Бэкенд кодирует URL прямо в вёрстке — клиент просто его зовёт",
             size=14, color="ink2", align="center")

# ---------------------------------------------------------------------------
# SLIDE 15 — Паттерн: дозапрос с состоянием (blank, 4 steps)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Паттерн: дозапрос с состоянием")

steps_15 = [
    "Шаг 1: Backend кладёт переменную is_loading = false в вёрстку",
    "Шаг 2: Тап → SetVariableAction(is_loading = true) + NetworkRequestAction",
    "Шаг 3: Спиннер через expression is_loading.visibleElseGone()",
    "Шаг 4: onSuccessAction обновляет данные + сбрасывает is_loading",
]
step_cards(s, steps_15, start_y=5.5, box_w=58.0, box_x=4.5,
           box_h=5.5, gap=7.0)

# ---------------------------------------------------------------------------
# SLIDE 16 — [РАЗДЕЛ] Состояние и хранение
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Состояние и хранение")
deck.fill_bodies(s, [
    "«Кто хранит состояние? Клиент.\nКто его объявляет? Бэкенд.»",
])

# ---------------------------------------------------------------------------
# SLIDE 17 — DivKit Variables
# ---------------------------------------------------------------------------
s = deck.add(prs, "header_text")
deck.set_title(s, "DivKit Variables: объявление и использование")
deck.fill_bodies(s, [[
    "Бэкенд объявляет: \"theme\".stringVariable(), \"is_tablet\".booleanVariable()",
    "Клиент управляет: DivVariableController.putOrUpdate(...)",
    "Expressions: theme equalTo \"dark\" → другой цвет фона",
    "CommonVars: глобальные переменные (тема, ширина экрана)",
    "Переменная — контракт. Бэкенд создаёт, клиент управляет.",
]])

# ---------------------------------------------------------------------------
# SLIDE 18 — Кэширование на клиенте (blank, 4 steps)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Кэширование на клиенте")

steps_18 = [
    "Шаг 1: Первая загрузка — документ сохраняется в disk cache",
    "Шаг 2: Повторное открытие — документ из кэша, запрос на обновление в фоне",
    "Шаг 3: Кэш протух — показывается hardcoded fallback до загрузки",
    "LazyNetworkCaching: обновляет кэш только если экран был открыт",
]
step_cards(s, steps_18, start_y=5.5, box_w=58.0, box_x=4.5,
           box_h=5.5, gap=7.0)

# ---------------------------------------------------------------------------
# SLIDE 19 — Зашитая вёрстка (blank, flowchart with YES branches inside)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Зашитая вёрстка (Hardcoded Fallback)", size=28)

bw = 40.0
bh = 4.0
bx = 4.0

# Row 1: start
deck.card_with_text(s, bx, 4.8, bw, bh,
                    title="Открытие экрана",
                    fill="pink_bg", title_size=17)
deck.arrow(s, bx + bw/2, 8.8, bx + bw/2, 10.3, color="ink2")

# Row 2: cache?
deck.card_with_text(s, bx, 10.3, bw, bh,
                    title="Есть кэш?",
                    fill="lilac_bg", title_size=17)
# YES → right branch
deck.arrow(s, bx + bw, 12.3, bx + bw + 1.5, 12.3, color="red")
deck.card_with_text(s, bx + bw + 1.5, 10.3, 19.0, bh,
                    title="YES: показать кэш",
                    body="+ фоновое обновление",
                    fill="yellow_bg", title_size=14, body_size=13)
# NO → down
deck.arrow(s, bx + bw/2, 14.3, bx + bw/2, 15.8, color="ink2")
deck.textbox(s, bx + bw/2 + 0.5, 13.8, 3.5, 1.5, "NO", size=13, color="ink2")

# Row 3: raw JSON?
deck.card_with_text(s, bx, 15.8, bw, bh,
                    title="Есть raw JSON в APK?",
                    fill="lilac_bg", title_size=17)
# YES → right branch
deck.arrow(s, bx + bw, 17.8, bx + bw + 1.5, 17.8, color="red")
deck.card_with_text(s, bx + bw + 1.5, 15.8, 19.0, bh,
                    title="YES: показать fallback",
                    body="зашитый JSON из res/raw/",
                    fill="yellow_bg", title_size=14, body_size=13)
# NO → down
deck.arrow(s, bx + bw/2, 19.8, bx + bw/2, 21.3, color="ink2")
deck.textbox(s, bx + bw/2 + 0.5, 19.3, 3.5, 1.5, "NO", size=13, color="ink2")

# Row 4: skeleton
deck.card_with_text(s, bx, 21.3, bw, bh,
                    title="Skeleton / Error state",
                    fill="grey", title_size=17)

deck.textbox(s, 3.8, 27.0, 60.0, 2.5,
             "searchapp_flex_ntp.json, mobileyandex_div_profile.json — лежат в res/raw/ APK",
             size=14, color="ink2", align="center")

# ---------------------------------------------------------------------------
# SLIDE 20 — Офлайн: OfflineNtpFeature
# ---------------------------------------------------------------------------
s = deck.add(prs, "two_blocks")
deck.set_title(s, "Офлайн: OfflineNtpFeature")
deck.fill_bodies(s, [
    [
        "Без сети",
        "disk cache → fallback JSON",
        "→ offline-режим",
        "Игры, сохранённые страницы",
    ],
    [
        "OfflineNtpFeature",
        "Флаг включает offline-контент",
        "Вместо страницы ошибки сети",
        "preload_games = true: игры заранее",
    ],
])

# ---------------------------------------------------------------------------
# SLIDE 21 — [РАЗДЕЛ] Темы экрана
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Темы экрана")
deck.fill_bodies(s, [
    "«Light, dark, incognito —\nтри состояния одного экрана.»",
])

# ---------------------------------------------------------------------------
# SLIDE 22 — Как работает темизация (blank, flowchart)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Как работает темизация в DivKit")

steps_22 = [
    "Пользователь: System Dark Mode ON",
    "DarkThemeStateController",
    "BaseFlexThemeController.updateThemeInFlex()\nfeedSdkComponent.themeController.themeId = \"dark\"",
    "DivPaletteUpdater.updateTheme()\nvariableController.putOrUpdate(bg/primary → #1C1C1E)",
    "DivKit пересчитывает expressions → UI обновляется",
]
step_cards(s, steps_22, start_y=5.0, box_w=54.0, box_x=6.5,
           box_h=4.5, gap=5.8)

deck.textbox(s, 3.8, 34.0, 60.0, 2.0,
             "Смена темы — одна операция: обновить все Color-переменные",
             size=14, color="ink2", align="center")

# ---------------------------------------------------------------------------
# SLIDE 23 — Палитра на бэкенде
# ---------------------------------------------------------------------------
s = deck.add(prs, "header_text")
deck.set_title(s, "Палитра на бэкенде: palette/")
deck.fill_bodies(s, [[
    "Модуль palette/ — единая палитра light/dark в bromapi",
    "Color.themed() — extension с ветвлением по теме",
    "ifThemeIsLight(onMatch, onMismatch) — helper из DivExpressionUtils",
    "Бэкенд и клиент договорились о названиях переменных",
    "Цвет нельзя захардкодить — только через themed() или palette variable",
]])

# ---------------------------------------------------------------------------
# SLIDE 24 — [РАЗДЕЛ] Переводы
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Переводы (i18n)")
deck.fill_bodies(s, [
    "«Строка должна жить в Tanker,\nне в коде.»",
])

# ---------------------------------------------------------------------------
# SLIDE 25 — Локализация в bromapi (blank, 4 steps)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Локализация в bromapi")

steps_25 = [
    "Шаг 1: Добавить ключ в Tanker\nПроект «home», текст по-русски",
    "Шаг 2: Переводчики получают задачу автоматически\n~13 языков, 1-го и 15-го числа",
    "Шаг 3: lang-auto.json выкатывается отдельным релизом (~ежедневно)",
    "Шаг 4: REX доставляет lang-auto.json в bromapi без перевыкладки сервиса",
]
step_cards(s, steps_25, start_y=5.5, box_w=58.0, box_x=4.5,
           box_h=5.5, gap=7.0)

# ---------------------------------------------------------------------------
# SLIDE 26 — [РАЗДЕЛ] Версионирование
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Версионирование")
deck.fill_bodies(s, [
    "«Клиент обновляется медленно.\nБэкенд — быстро.\nКак жить вместе?»",
])

# ---------------------------------------------------------------------------
# SLIDE 27 — Три уровня версионирования (blank, 3 columns)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Три уровня версионирования")

cols = [
    ("Протокол", "Protobuf-схемы версионируются\nПоле obsolete ≠ сломано\nОбратная совместимость всегда"),
    ("Layout / DSL", "Новое поле в div-action\nСтарые клиенты игнорируют\nAdditive changes — безопасно"),
    ("Бизнес-логика", "AB-флаги: фича только для\nклиентов версии >= N\nminVersion-проверка в Servant"),
]

col_w = 19.0
col_h = 22.0
gap = 1.5
y0 = 5.5
fills = ["lilac_bg", "pink_bg", "yellow_bg"]

for i, (col_title, col_body) in enumerate(cols):
    x = 3.8 + i * (col_w + gap)
    deck.card_with_text(s, x, y0, col_w, col_h,
                        title=col_title, body=col_body,
                        fill=fills[i], title_size=20, body_size=15)

deck.textbox(s, 3.8, 29.0, 60.0, 2.0,
             "Три уровня независимы — можно менять их по отдельности",
             size=15, color="ink2", align="center")

# ---------------------------------------------------------------------------
# SLIDE 28 — Версионирование layout
# ---------------------------------------------------------------------------
s = deck.add(prs, "compare")
deck.set_title(s, "Что безопасно, что опасно")
deck.fill_bodies(s, [
    [
        "Безопасно",
        "Новые опциональные поля",
        "Новые типы action",
        "Новые переменные",
        "Additive changes",
    ],
    [
        "Опасно",
        "Удалить обязательное поле",
        "Изменить семантику action",
        "Переименовать переменную",
        "Breaking changes",
    ],
])

# ---------------------------------------------------------------------------
# SLIDE 29 — [РАЗДЕЛ] A/B-эксперименты
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "A/B-эксперименты")
deck.fill_bodies(s, [
    "«Как проверить гипотезу\nна 1% пользователей,\nне катив релиз?»",
])

# ---------------------------------------------------------------------------
# SLIDE 30 — AB-флаги: два пути
# ---------------------------------------------------------------------------
s = deck.add(prs, "two_blocks")
deck.set_title(s, "AB-флаги в bromapi: два пути")
deck.fill_bodies(s, [
    [
        "Через AB-систему (рекомендуется)",
        "Выборка с хендлером APPSEARCH",
        "Флаг → Core-блок → Morda-Go",
        "bromapi читает AbFlags из proto",
        "Метрики работают автоматически",
    ],
    [
        "Через MADM (легаси)",
        "Добавить в экспорт ab_flags_v2",
        "Работает, но без метрик",
        "Нерекомендуемый способ",
        "Без метрик — нет смысла в A/B",
    ],
])

# ---------------------------------------------------------------------------
# SLIDE 31 — Flagman
# ---------------------------------------------------------------------------
s = deck.add(prs, "header_text")
deck.set_title(s, "Flagman: контролируемая раскатка")
deck.fill_bodies(s, [[
    "Flagman — система контроля показов и кликов",
    "flagman_flags в MADM → bromapi: flagman.isEnabled(\"flag_id\")",
    "Постепенная раскатка: %, регион, тип девайса",
    "Откат в 1 клик без релиза сервиса",
    "AB vs Flagman: AB сравнивает два варианта; Flagman включает/выключает фичу",
]])

# ---------------------------------------------------------------------------
# SLIDE 32 — [РАЗДЕЛ] Паттерны
# ---------------------------------------------------------------------------
s = deck.add(prs, "quote")
deck.set_title(s, "Паттерны проектирования")
deck.fill_bodies(s, [
    "«За всеми решениями\nстоят одни и те же идеи.»",
])

# ---------------------------------------------------------------------------
# SLIDE 33 — Паттерны бэкенда: Servant + Renderer (blank, numbered cards)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
numbered_cards(s, [
    ("Servant per Surface",
     "Один класс — один тип запроса. Нет god-class, обрабатывающего всё."),
    ("Renderer Composition",
     "Экран = сумма маленьких Renderer-ов. Каждый тестируется через RendererTest<I>."),
    ("Template DSL",
     "Повторяющиеся блоки — template + reference + defer. Payload уменьшается в разы."),
    ("No Business Logic in Renderer",
     "Renderer получает готовые данные из proto. Логика — в Adapter, не в Renderer."),
], "Паттерны бэкенда: Servant + Renderer")

# ---------------------------------------------------------------------------
# SLIDE 34 — Паттерны бэкенда: данные и зависимости
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
numbered_cards(s, [
    ("Koin Request Scope",
     "Зависимости живут ровно один запрос. Нет утечек состояния между запросами."),
    ("Interface + @RequireTests",
     "Каждый renderer обязан иметь интерфейс + интеграционный тест. CI проверяет."),
    ("Декларативная версионная логика",
     "Проверка версии клиента — в одном месте (AbFlags или условие в Servant)."),
    ("Variables as Contract",
     "Переменные — публичный API между бэкендом и клиентом. Переименовать = breaking change."),
], "Паттерны бэкенда: данные и зависимости")

# ---------------------------------------------------------------------------
# SLIDE 35 — Паттерны клиента: Document lifecycle
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
numbered_cards(s, [
    ("Cache-First + Background Refresh",
     "Показать кэш немедленно. Запрос на обновление идёт в фоне."),
    ("Fallback Chain",
     "network → disk cache → hardcoded JSON → skeleton → error. Каждый уровень страхует."),
    ("Variables as Single Source of Truth",
     "Состояние UI (тема, флаги, авторизация) — только через DivVariableController."),
    ("Action-driven Requests",
     "Дозапрос кодируется как DivKit-action в JSON. Клиент не знает о бизнес-логике."),
], "Паттерны клиента: Document lifecycle")

# ---------------------------------------------------------------------------
# SLIDE 36 — Паттерны клиента: тема и offline
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
numbered_cards(s, [
    ("Theme as Variables",
     "Тема обновляет Color-переменные, не перерисовывает View. DivKit пересчитывает сам."),
    ("OfflineNtpFeature",
     "Offline — отдельный content-type, не «ошибка сети». Планировать заранее."),
    ("Skeleton as DivKit",
     "Loading-анимация — тоже DivKit JSON (SkeletonContent). Не нативный View."),
    ("MordaUpdateMonitor",
     "Следит: экран открыт + прошло N минут → обновить документ."),
], "Паттерны клиента: тема и offline")

# ---------------------------------------------------------------------------
# SLIDE 37 — Итог (blank, bullet cards)
# ---------------------------------------------------------------------------
s = deck.add(prs, "blank")
slide_title(s, "Итог: что взять с собой", size=32)

takeaways = [
    ("BDUI — контракт", "Бэкенд объявляет, клиент исполняет"),
    ("Variables + Expressions", "Замена нативному состоянию"),
    ("Fallback chain", "Думайте о ней с самого начала проекта"),
    ("Версионирование и AB", "Жизнь без синхронного релиза клиента и бэкенда"),
    ("Архитектура", "Маленькие Servant/Renderer лучше монолита"),
]

card_w_t = 28.0
card_h_t = 5.0
gap_t = 5.5
fills_t = ["lilac_bg", "pink_bg", "yellow_bg", "lilac_bg", "pink_bg"]

for i, (heading, body) in enumerate(takeaways):
    col = i % 2
    row = i // 2
    if i == 4:  # last item centered
        x = 3.8 + (card_w_t + 1.5) * 0.5
    else:
        x = 3.8 + col * (card_w_t + 3.5)
    y = 5.5 + row * gap_t
    deck.card_with_text(s, x if i < 4 else 18.5, y, card_w_t, card_h_t,
                        title=heading, body=body,
                        fill=fills_t[i], title_size=18, body_size=14)

# ---------------------------------------------------------------------------
# SLIDE 38 — Вопросы
# ---------------------------------------------------------------------------
s = deck.add(prs, "questions")
deck.set_title(s, "Вопросы?")
deck.fill_bodies(s, [
    "docs.yandex-team.ru/bromapi",
    "github.com/divkit/divkit",
])

# ---------------------------------------------------------------------------
prs.save(OUT)
print(f"Saved {OUT} ({len(prs.slides)} slides)")
