"""build_deck_v2.py — 38-slide deck for the v2 structure skeleton.

Structure source: presentation_structure_v2.md (38 slides)
Uses deck.py helpers.  Save → render → inspect → fix cycle.
"""

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

import deck

OUT = "/Users/the-leo/divkit-weather-workshop/talk/bdui_divkit_v2.pptx"

prs = deck.new_deck()

# ---------------------------------------------------------------------------
# Local helpers
# ---------------------------------------------------------------------------

def slide_title(s, text, size=32):
    """Standard title textbox for blank-canvas slides."""
    deck.textbox(s, 3.8, 2.45, 60.0, 3.0, text, size=size, bold=True, color="ink")


# ---------------------------------------------------------------------------
# ■ БЛОК 0. ВСТУПЛЕНИЕ
# ---------------------------------------------------------------------------

# ── SLIDE 1 — Титул ─────────────────────────────────────────────────────────
s = deck.add(prs, "title_plain")
deck.set_title(s, "BDUI и DivKit")
deck.fill_bodies(s, [
    "Делаем приложение, которое рисует сервер",
    "Воркшоп · Android Meetup",
])

# ── SLIDE 2 — О чём доклад (agenda / маршрут) ───────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "О чём доклад", size=34)

agenda_items = [
    ("1. Показываем приложение", "Живое BDUI-приложение — сразу в деле"),
    ("2. Общая картина", "Как клиент и бэкенд общаются"),
    ("3. Несколько экранов", "Один JSON — много экранов"),
    ("4. Персистентность, языки, темы", "Stored values, рефетч, выражения"),
    ("5. Offline и A/B", "Зашитая вёрстка, эксперименты"),
]

for i, (title, body) in enumerate(agenda_items):
    col = i % 2
    row = i // 2
    x = 3.8 + col * 31.0
    y = 6.5 + row * 9.0
    if i == 4:
        x = 3.8 + 15.5  # center last item
    fill = ["lilac_bg", "pink_bg", "yellow_bg", "lilac_bg", "pink_bg"][i]
    deck.card(s, x, y, 28.0, 7.5, fill=fill)
    deck.number_badge(s, x + 0.5, y + 2.5, i + 1)
    deck.textbox(s, x + 3.5, y + 0.8, 23.5, 2.2,
                 title, size=16, bold=True, color="ink")
    deck.textbox(s, x + 3.5, y + 3.2, 23.5, 3.5,
                 body, size=13, color="ink2")

# ── SLIDE 3 — Что такое BDUI (compare) ──────────────────────────────────────
s = deck.add(prs, "compare")
deck.set_title(s, "Что такое BDUI")
deck.fill_bodies(s, [
    [
        "Без BDUI",
        "UI захардкожен в клиенте",
        "Обновление = новый релиз",
        "Бэкенд не управляет вёрсткой",
    ],
    [
        "С BDUI (DivKit)",
        "UI описан сервером в JSON",
        "Клиент только рендерит",
        "Обновление без релиза APK",
    ],
])

# ---------------------------------------------------------------------------
# ■ БЛОК 1. ЗНАКОМСТВО — вот наше приложение
# ---------------------------------------------------------------------------

# ── SLIDE 4 — «Мы собрали погодное приложение» ──────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Мы собрали погодное приложение", size=30)

# Large portrait phone screenshot placeholder centered
deck.placeholder(s, 20.0, 6.0, 28.0, 28.0,
                 kind="screen", caption="main, light")
deck.textbox(s, 3.8, 35.0, 60.0, 2.0,
             "Это всё нарисовано из JSON с сервера",
             size=16, color="ink2", align="center")

# ── SLIDE 5 — Несколько экранов ──────────────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Несколько экранов — одно приложение", size=30)

# Three portrait phone screenshot placeholders in a row
screens = [
    ("screen", "main"),
    ("screen", "settings"),
    ("screen", "about"),
]
phone_w = 16.0
phone_h = 26.0
total_w = 3 * phone_w + 2 * 2.0  # 3 boxes + 2 gaps
start_x = (67.74 - total_w) / 2
for i, (kind, caption) in enumerate(screens):
    x = start_x + i * (phone_w + 2.0)
    deck.placeholder(s, x, 6.0, phone_w, phone_h, kind=kind, caption=caption)

# ── SLIDE 6 — Все экраны BDUI, они связаны (big_thesis) ─────────────────────
s = deck.add(prs, "big_thesis")
deck.set_title(s, "Все экраны — это BDUI")
deck.fill_bodies(s, [
    "Ни одного нативного экрана.\nНавигация между ними — тоже часть BDUI.",
])

# ── SLIDE 7 — Демо: тема и язык на лету ─────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Демо: тема и язык на лету", size=32)

deck.placeholder(s, 10.0, 6.0, 48.0, 26.0,
                 kind="screencast", caption="theme+lang switch")
deck.textbox(s, 3.8, 33.5, 60.0, 2.0,
             "Переключаем light ↔ dark и ru ↔ en без перезапуска",
             size=15, color="ink2", align="center")

# ── SLIDE 8 — Карта подтем ───────────────────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Что разберём по частям", size=32)

topics = [
    ("1", "Несколько экранов",      "один JSON → много экранов"),
    ("2", "Персистентность",        "stored values между перезапусками"),
    ("3", "Языки",                  "строки живут на сервере"),
    ("4", "Темы",                   "выражения вместо перерисовки"),
    ("5", "Offline",                "зашитая вёрстка как запасной план"),
    ("6", "Бэкенд рендерит → A/B", "кто принимает решение?"),
]

cols = 2
card_w = 28.5
card_h = 6.5
gap_x = 3.0
gap_y = 1.5
start_x = 3.8
start_y = 6.0
fills = ["lilac_bg", "pink_bg", "yellow_bg", "lilac_bg", "pink_bg", "yellow_bg"]

for i, (num, title, body) in enumerate(topics):
    col = i % cols
    row = i // cols
    x = start_x + col * (card_w + gap_x)
    y = start_y + row * (card_h + gap_y)
    deck.card(s, x, y, card_w, card_h, fill=fills[i])
    deck.number_badge(s, x + 0.4, y + card_h / 2 - 0.8, int(num))
    deck.textbox(s, x + 3.0, y + 0.8, card_w - 3.4, 2.2,
                 title, size=16, bold=True, color="ink")
    deck.textbox(s, x + 3.0, y + 3.2, card_w - 3.4, 2.8,
                 body, size=13, color="ink2")

deck.textbox(s, 3.8, 33.5, 60.0, 2.0,
             "Начнём с общей картины клиент ↔ бэкенд",
             size=14, color="red", bold=True, align="center")

# ---------------------------------------------------------------------------
# ■ БЛОК 2. ОБЩАЯ КАРТИНА
# ---------------------------------------------------------------------------

# ── SLIDE 9 — Клиент ↔ бэкенд (diagram) ─────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Клиент ↔ бэкенд", size=34)

# Layout: three cards in a horizontal row with data source on right
# Android App (left)
deck.card_with_text(s, 3.8, 7.0, 19.0, 8.0,
                    title="Android App",
                    body="DivKit рендерит JSON\nв нативный View",
                    fill="lilac_bg", title_size=18, body_size=14)

# Arrow right: GET /document
deck.arrow(s, 22.8, 10.5, 28.0, 10.5, color="red")
deck.textbox(s, 22.5, 8.5, 6.0, 2.0,
             "GET /document\n?lang=ru", size=11, color="ink2", align="center")

# BDUI backend (center)
deck.card_with_text(s, 28.0, 7.0, 19.0, 8.0,
                    title="BDUI-бэкенд",
                    body="данные → вёрстка\nKtor + divan DSL",
                    fill="pink_bg", title_size=18, body_size=14)

# Arrow back: JSON envelope (below the forward arrow)
deck.arrow(s, 28.0, 13.0, 22.8, 13.0, color="ink2")
deck.textbox(s, 22.5, 13.2, 6.0, 2.0,
             "JSON конверт\n(один ответ)", size=11, color="ink2", align="center")

# Arrow right: to data
deck.arrow(s, 47.0, 11.0, 52.0, 11.0, color="ink2")

# Data source (right)
deck.card_with_text(s, 52.0, 7.0, 12.0, 8.0,
                    title="Данные",
                    body="погода\nAPI",
                    fill="yellow_bg", title_size=15, body_size=13)

# Vertical arrow down from Android App to Экран
deck.arrow(s, 13.3, 15.0, 13.3, 18.5, color="ink2")

# Render result — below the Android App card
deck.card_with_text(s, 3.8, 18.5, 19.0, 7.5,
                    title="Экран",
                    body="клиент рендерит DivData\nбез перезапуска",
                    fill="lilac_bg", title_size=17, body_size=13)

deck.textbox(s, 3.8, 28.0, 60.0, 2.0,
             "Один конверт JSON → все экраны приложения",
             size=14, color="red", bold=True, align="center")

# ── SLIDE 10 — Мы не собираем данные — мы рендерим ──────────────────────────
s = deck.add(prs, "header_text")
deck.set_title(s, "Бэкенд рендерит — не собирает данные")
deck.fill_bodies(s, [[
    "Данные приходят снаружи: weather API, настройки",
    "BDUI-бэкенд: данные → вёрстку (JSON)",
    "В проде Яндекса внутренний слой: Flex/Tovarisch/AppHost",
    "У нас открытый стек: divan DSL + Ktor",
    "«Мы — узел, который рисует, а не добывает»",
]])
deck.placeholder(s, 38.0, 18.0, 26.0, 12.0,
                 kind="code", caption="divan-рендерер, фрагмент")

# ── SLIDE 11 — Вёрстка как код (divan) ──────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Вёрстка как код — divan DSL", size=32)

# divan code placeholder on left
deck.placeholder(s, 3.8, 6.5, 34.0, 16.0,
                 kind="code", caption="divan snippet")

# Arrow
deck.arrow(s, 37.8, 14.5, 41.5, 14.5, color="red")
deck.textbox(s, 37.5, 12.5, 4.5, 2.0, "→", size=22, color="red", align="center")

# Screen result placeholder on right
deck.placeholder(s, 42.0, 6.5, 22.0, 26.0,
                 kind="screen", caption="результат")

deck.textbox(s, 3.8, 24.5, 34.0, 3.5,
             "Kotlin DSL → JSON → рендерится на клиенте",
             size=14, color="ink2")

# ── SLIDE 12 — Кастомные экшены и навигация ──────────────────────────────────
s = deck.add(prs, "header_text")
deck.set_title(s, "Кастомные экшены и навигация")
deck.fill_bodies(s, [[
    "Свой URI-формат: weather-app://navigate?screen=settings",
    "Кнопка в JSON → клиент ловит в DivActionHandler",
    "Навигация без нативного кода на каждый переход",
    "WeatherDivActionHandler маршрутизирует по scheme",
]])
deck.placeholder(s, 38.0, 18.0, 26.0, 14.0,
                 kind="code", caption="WeatherDivActionHandler")

# ---------------------------------------------------------------------------
# ■ БЛОК 3. ПОДТЕМА 1 — Несколько экранов
# ---------------------------------------------------------------------------

# ── SLIDE 13 — Раздел: Несколько экранов ────────────────────────────────────
s = deck.add(prs, "quote")
deck.set_title(s, "Несколько экранов")
deck.fill_bodies(s, ["«Один запрос — несколько экранов»"])

# ── SLIDE 14 — Один ответ — три экрана (diagram) ─────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Один ответ — три экрана", size=32)

# Envelope card (left)
deck.card(s, 3.8, 6.5, 27.0, 16.0, fill="pink_bg")
deck.textbox(s, 4.8, 7.0, 16.0, 2.0, "JSON конверт", size=16, bold=True, color="ink")
deck.card_with_text(s, 5.5, 9.5, 23.0, 3.5,
                    title="templates", body="общие компоненты",
                    fill="white", title_size=14, body_size=12)
deck.card_with_text(s, 5.5, 13.5, 10.0, 3.0,
                    title="screens.main", body="главный",
                    fill="lilac_bg", title_size=13, body_size=11)
deck.card_with_text(s, 16.0, 13.5, 13.0, 3.0,
                    title="screens.settings", body="настройки",
                    fill="lilac_bg", title_size=13, body_size=11)
deck.card_with_text(s, 5.5, 17.0, 10.0, 3.0,
                    title="screens.about", body="о прил.",
                    fill="lilac_bg", title_size=13, body_size=11)

# Arrow from envelope → parse card (horizontal, midpoint of envelope)
deck.arrow(s, 30.8, 11.5, 35.0, 11.5, color="red")

# Parse once card (top right)
deck.card_with_text(s, 35.0, 7.0, 19.5, 8.0,
                    title="Клиент парсит templates один раз",
                    body="ID → DivData\nбыстрое переключение",
                    fill="yellow_bg", title_size=15, body_size=13)

# Vertical arrows from parse card down to 3 screen results
deck.arrow(s, 37.5, 15.0, 37.5, 17.5, color="ink2")
deck.arrow(s, 45.5, 15.0, 45.5, 17.5, color="ink2")
deck.arrow(s, 53.5, 15.0, 53.5, 17.5, color="ink2")

deck.card_with_text(s, 35.0, 17.5, 7.0, 5.5,
                    title="main", fill="lilac_bg", title_size=14)
deck.card_with_text(s, 43.5, 17.5, 8.5, 5.5,
                    title="settings", fill="lilac_bg", title_size=14)
deck.card_with_text(s, 53.0, 17.5, 7.0, 5.5,
                    title="about", fill="lilac_bg", title_size=14)

# Code placeholder at bottom — fits below the diagram
deck.placeholder(s, 3.8, 25.0, 27.0, 5.5,
                 kind="code", caption="envelope JSON структура")

# ── SLIDE 15 — Переиспользование templates ───────────────────────────────────
# two_blocks: bodies[0] renders left, bodies[1] renders right in this template.
# "Один ответ" should appear LEFT; "Альтернатива" RIGHT.
# Prior render had them swapped, so swap the order here.
s = deck.add(prs, "two_blocks")
deck.set_title(s, "Переиспользование templates")
deck.fill_bodies(s, [
    [
        "Альтернатива",
        "Отдельная ручка на каждый экран",
        "Проще поддерживать по отдельности",
        "Но: больше сетевых запросов",
    ],
    [
        "Один ответ — выгоднее",
        "Общие шаблоны парсятся один раз",
        "Меньше round-trip до сервера",
        "Экраны переключаются мгновенно",
    ],
])

# ── SLIDE 16 — Навигация между экранами ─────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Навигация между экранами", size=32)

# Flow diagram
deck.card_with_text(s, 3.8, 7.0, 20.0, 5.5,
                    title="Кнопка в JSON",
                    body="navigate_action\nweather-app://navigate?screen=settings",
                    fill="pink_bg", title_size=15, body_size=12)
deck.arrow(s, 23.8, 9.75, 28.0, 9.75, color="red")

deck.card_with_text(s, 28.0, 7.0, 20.0, 5.5,
                    title="DivActionHandler",
                    body="ловит URI\nмаршрутизирует",
                    fill="lilac_bg", title_size=15, body_size=12)
deck.arrow(s, 48.0, 9.75, 52.0, 9.75, color="red")

deck.card_with_text(s, 52.0, 7.0, 12.0, 5.5,
                    title="Показать\nexran",
                    body="DivData[id]\nбез нового запроса",
                    fill="yellow_bg", title_size=14, body_size=11)

deck.placeholder(s, 3.8, 15.0, 28.0, 14.0,
                 kind="code", caption="navButton + handler")
deck.placeholder(s, 34.0, 15.0, 28.0, 14.0,
                 kind="screen", caption="переход между экранами")

# ---------------------------------------------------------------------------
# ■ БЛОК 4. ПОДТЕМА 2 — Персистентность
# ---------------------------------------------------------------------------

# ── SLIDE 17 — Раздел: Персистентность ──────────────────────────────────────
s = deck.add(prs, "quote")
deck.set_title(s, "Персистентность")
deck.fill_bodies(s, ["«Кто помнит настройки между запусками?»"])

# ── SLIDE 18 — DivKit Stored Values ─────────────────────────────────────────
s = deck.add(prs, "header_text")
deck.set_title(s, "DivKit Stored Values")
deck.fill_bodies(s, [[
    "set_stored_value — записать значение в SQLite",
    "getStoredStringValue('key', 'default') — прочитать",
    "div-storage — встроенный механизм, без SharedPreferences",
    "Аналогия для Android-разработчика: ≈ DataStore, но декларативно в JSON",
    "Значение переживает рестарт приложения",
]])
deck.placeholder(s, 38.0, 18.0, 26.0, 14.0,
                 kind="code", caption="set_stored_value + getStored*")

# ── SLIDE 19 — Состояние живёт в вёрстке (diagram) ──────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Состояние живёт в вёрстке", size=32)

# Flow diagram
steps = [
    ("Тап пользователя", "pink_bg"),
    ("set_stored_value\n'theme' = 'dark'", "lilac_bg"),
    ("Выражение пересчитывается\nautomatically", "yellow_bg"),
    ("UI обновляется\n(без рестарта)", "lilac_bg"),
    ("Приложение перезапущено:\ngetStoredStringValue → 'dark' ✓", "pink_bg"),
]

box_w = 46.0
box_h = 3.8
box_x = 11.0  # center horizontally: (67.74 - 46) / 2 ≈ 10.87
for i, (label, fill) in enumerate(steps):
    y = 6.0 + i * 4.8
    deck.card_with_text(s, box_x, y, box_w, box_h,
                        title=label, fill=fill, title_size=15)
    if i < len(steps) - 1:
        mid_x = box_x + box_w / 2
        deck.arrow(s, mid_x, y + box_h, mid_x, y + box_h + 1.0, color="red")

# Screencast placeholder below the flow — fits within canvas
deck.placeholder(s, 3.8, 31.5, 27.0, 4.5,
                 kind="screencast", caption="рестарт сохраняет тему")

# ---------------------------------------------------------------------------
# ■ БЛОК 5. ПОДТЕМА 3 — Языки
# ---------------------------------------------------------------------------

# ── SLIDE 20 — Раздел: Языки ─────────────────────────────────────────────────
s = deck.add(prs, "quote")
deck.set_title(s, "Языки")
deck.fill_bodies(s, ["«Строка живёт на сервере»"])

# ── SLIDE 21 — Смена языка = рефетч (diagram) ────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Смена языка = рефетч", size=32)

# Diagram: set_lang → GET → new envelope
deck.card_with_text(s, 3.8, 7.0, 16.0, 6.0,
                    title="Пользователь\nвыбрал EN",
                    body="set_stored_value\n'lang' = 'en'",
                    fill="pink_bg", title_size=15, body_size=13)
deck.arrow(s, 19.8, 10.0, 25.0, 10.0, color="red")

deck.card_with_text(s, 25.0, 7.0, 20.0, 6.0,
                    title="GET /document\n?lang=en",
                    body="новый HTTP запрос",
                    fill="lilac_bg", title_size=15, body_size=13)
deck.arrow(s, 45.0, 10.0, 50.0, 10.0, color="red")

deck.card_with_text(s, 50.0, 7.0, 14.0, 6.0,
                    title="Новый\nконверт",
                    body="все строки\nна английском",
                    fill="yellow_bg", title_size=15, body_size=13)
deck.arrow(s, 57.0, 10.0, 57.0, 14.0, color="ink2")

deck.card_with_text(s, 43.0, 14.5, 21.0, 5.5,
                    title="Все экраны\nпереключаются",
                    body="DivData заменяется целиком",
                    fill="yellow_bg", title_size=15, body_size=13)

# Why refetch explanation
deck.card(s, 3.8, 17.0, 60.0, 9.0, fill="lilac_bg")
deck.textbox(s, 4.8, 17.8, 58.0, 2.5,
             "Почему рефетч, а не переменные?", size=17, bold=True, color="ink")
deck.textbox(s, 4.8, 20.5, 28.0, 5.0,
             "Строки печёт сервер\n→ нужен новый JSON с правильным языком\n→ рефетч логичен",
             size=14, color="ink2")
deck.textbox(s, 34.0, 20.5, 28.0, 5.0,
             "Тема/компакт — НЕ рефетч:\nменяются через stored_value,\nвыражение пересчитывает UI",
             size=14, color="ink2")

deck.chip(s, 3.8, 28.0, "рефетч = новый JSON", fill="red", color="white")
deck.chip(s, 28.0, 28.0, "тема = stored_value expression", fill="lilac_bg", color="ink")

# ── SLIDE 22 — Локализация на бэке ──────────────────────────────────────────
s = deck.add(prs, "header_text")
deck.set_title(s, "Локализация на бэкенде")
deck.fill_bodies(s, [[
    "Localizer: ru/en словари на бэкенде",
    "getOrDefault(key, fallback) — надёжный fallback",
    "Коды погоды → читаемый текст + emoji",
    "Язык приходит в запросе ?lang=ru",
    "Бэкенд «печёт» строки сам — клиент не переводит",
]])
deck.placeholder(s, 38.0, 18.0, 26.0, 14.0,
                 kind="code", caption="Localizer + строковые таблицы")

# ---------------------------------------------------------------------------
# ■ БЛОК 6. ПОДТЕМА 4 — Темы
# ---------------------------------------------------------------------------

# ── SLIDE 23 — Раздел: Темы ──────────────────────────────────────────────────
s = deck.add(prs, "quote")
deck.set_title(s, "Темы")
deck.fill_bodies(s, ["«Тема — это выражение, а не перерисовка»"])

# ── SLIDE 24 — Тема через выражения ─────────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Тема через выражения", size=32)

# Expression explanation card
deck.card(s, 3.8, 6.5, 30.0, 10.0, fill="lilac_bg")
deck.textbox(s, 4.6, 7.0, 28.0, 2.0,
             "Выражение в JSON:", size=15, bold=True, color="ink")
deck.textbox(s, 4.6, 9.3, 28.0, 6.5,
             "@{getStoredStringValue('theme','light')\n  == 'dark'\n  ? '#1C1C1E'\n  : '#FFFFFF'}",
             size=13, color="ink2", font="Courier New")

deck.textbox(s, 3.8, 17.5, 30.0, 3.0,
             "DivKit пересчитывает сам — код ничего не делает",
             size=14, color="red", bold=True)

# Two screen placeholders: light / dark
deck.placeholder(s, 36.0, 6.5, 13.0, 22.0,
                 kind="screen", caption="light")
deck.placeholder(s, 51.0, 6.5, 13.0, 22.0,
                 kind="screen", caption="dark")

deck.placeholder(s, 3.8, 22.0, 30.0, 7.0,
                 kind="code", caption="expression")

# ── SLIDE 25 — Компактный режим ──────────────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Компактный режим", size=32)

# Two screenshot placeholders
deck.placeholder(s, 3.8, 6.5, 28.0, 24.0,
                 kind="screen", caption="compact off")
deck.placeholder(s, 35.5, 6.5, 28.0, 24.0,
                 kind="screen", caption="compact on")

deck.card(s, 3.8, 32.0, 60.0, 4.5, fill="yellow_bg")
deck.textbox(s, 4.6, 32.6, 58.0, 3.0,
             "compact — тоже выражение (видимость/размер блока)\nМеняется без рефетча — через stored_value",
             size=15, color="ink")

# ---------------------------------------------------------------------------
# ■ БЛОК 7. OFFLINE И ЗАШИТАЯ ВЁРСТКА
# ---------------------------------------------------------------------------

# ── SLIDE 26 — Раздел: Offline ───────────────────────────────────────────────
s = deck.add(prs, "quote")
deck.set_title(s, "Offline")
deck.fill_bodies(s, ["«А что показать без сети?»"])

# ── SLIDE 27 — Демо: сброс приложения ───────────────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Демо: сброс приложения", size=32)

deck.placeholder(s, 10.0, 6.0, 48.0, 26.0,
                 kind="screencast", caption="reset → зашитый экран")
deck.textbox(s, 3.8, 33.5, 60.0, 2.0,
             "Зашитый JSON из APK — та же BDUI-вёрстка, просто локальная",
             size=14, color="ink2", align="center")

# ── SLIDE 28 — Проблема и решение (compare) ──────────────────────────────────
s = deck.add(prs, "compare")
deck.set_title(s, "Зашитая вёрстка: проблема и решение")
deck.fill_bodies(s, [
    [
        "Проблема зашитой вёрстки",
        "Нет живых данных",
        "Быстро устаревает",
        "Может сильно отличаться от актуального UI",
    ],
    [
        "Решение",
        "Кешируем целые вёрстки с данными",
        "Зашитая — только на первом запуске",
        "После первого ответа: кеш вместо зашитой",
    ],
])

# ---------------------------------------------------------------------------
# ■ БЛОК 8. БЭКЕНД ТОЛЬКО РЕНДЕРИТ → A/B
# ---------------------------------------------------------------------------

# ── SLIDE 29 — Раздел: бэкенд не собирает данные ────────────────────────────
s = deck.add(prs, "quote")
deck.set_title(s, "Бэкенд только рендерит")
deck.fill_bodies(s, [
    "«BDUI-бэкенд не добывает данные —\nон их рендерит»",
])

# ── SLIDE 30 — Откуда данные и флаги (diagram) ───────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Откуда данные и флаги", size=32)

# Data backend
deck.card_with_text(s, 3.8, 7.0, 18.0, 9.0,
                    title="Data-бэкенд",
                    body="погода, данные\n+ AB-флаги\n\n(в проде: AppHost граф\n— внутренний слой)",
                    fill="yellow_bg", title_size=17, body_size=13)
deck.arrow(s, 21.8, 11.5, 27.0, 11.5, color="red")
deck.textbox(s, 21.5, 9.5, 6.0, 2.0, "данные\n+ флаги", size=12, color="ink2", align="center")

# BDUI backend
deck.card_with_text(s, 27.0, 7.0, 20.0, 9.0,
                    title="BDUI-бэкенд",
                    body="данные → JSON вёрстка\nKtor + divan DSL\n\n«Мы — узел,\nкоторый рисует»",
                    fill="pink_bg", title_size=17, body_size=13)
deck.arrow(s, 47.0, 11.5, 52.0, 11.5, color="red")

# Client
deck.card_with_text(s, 52.0, 7.0, 12.5, 9.0,
                    title="Клиент",
                    body="DivKit\nрендерит JSON",
                    fill="lilac_bg", title_size=17, body_size=13)

# Bottom note
deck.card(s, 3.8, 19.5, 60.0, 5.5, fill="lilac_bg")
deck.textbox(s, 4.6, 20.2, 58.0, 4.0,
             "«Мы — узел, который рисует»\nBDUI-бэкенд не знает бизнес-логику — он только превращает данные в вёрстку",
             size=15, color="ink")

# ── SLIDE 31 — A/B: бэкенд решает что отрендерить ────────────────────────────
s = deck.add(prs, "header_text")
deck.set_title(s, "A/B: бэкенд решает, что отрендерить")
deck.fill_bodies(s, [[
    "BDUI-бэкенд получает AB-флаги от data-бэкенда",
    "По флагу выбирает вариант вёрстки",
    "Клиент не знает, что есть A/B — он просто рендерит",
    "Для самого BDUI-сервиса A/B особого значения не имеют",
]])
deck.placeholder(s, 38.0, 18.0, 26.0, 12.0,
                 kind="code", caption="if по флагу — 2 строки")

# ── SLIDE 32 — Лучший путь: флаг растворяется в данных ───────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Лучший путь: флаг растворяется в данных", size=28)

# Left: "better" path
deck.card(s, 3.8, 7.0, 28.0, 22.0, fill="yellow_bg")
deck.textbox(s, 4.6, 7.6, 26.0, 2.0, "Чаще лучше", size=15, bold=True, color="ink")
deck.card_with_text(s, 5.5, 10.0, 24.0, 6.0,
                    title="AB-флаг влияет на данные",
                    body="data-бэкенд отдаёт другие данные",
                    fill="white", title_size=14, body_size=12)
deck.arrow(s, 17.5, 16.0, 17.5, 18.0, color="red")
deck.card_with_text(s, 5.5, 18.0, 24.0, 5.0,
                    title="BDUI-бэкенд не знает о флаге",
                    body="просто рендерит данные",
                    fill="white", title_size=14, body_size=12)

# Right: "sometimes" path
deck.card(s, 35.0, 7.0, 28.0, 22.0, fill="pink_bg")
deck.textbox(s, 35.8, 7.6, 26.0, 2.0, "Иногда проще", size=15, bold=True, color="ink")
deck.card_with_text(s, 36.5, 10.0, 24.0, 6.0,
                    title="Флаг доходит до BDUI-бэкенда",
                    body="if (flag.isEnabled) ...",
                    fill="white", title_size=14, body_size=12)
deck.arrow(s, 48.5, 16.0, 48.5, 18.0, color="ink2")
deck.card_with_text(s, 36.5, 18.0, 24.0, 5.0,
                    title="BDUI-бэкенд ветвится сам",
                    body="вёрстка A или вёрстка B",
                    fill="white", title_size=14, body_size=12)

# ── SLIDE 33 — Прагматика: best vs convenient ────────────────────────────────
s = deck.add(prs, "two_blocks")
deck.set_title(s, "Прагматика: чистота vs удобство")
deck.fill_bodies(s, [
    [
        "«Растворить флаг в данных»",
        "Чище архитектурно",
        "BDUI-бэкенд не знает о флаге",
        "Требует контроля data-бэкенда",
        "Не всегда доступно",
    ],
    [
        "«if прямо в BDUI-бэкенде»",
        "Проще реализовать",
        "2 строки кода",
        "Флаг-зависимость в вёрстке",
        "Честный trade-off",
    ],
])

# ---------------------------------------------------------------------------
# ■ БЛОК 9. КРАТКО: ДОЗАПРОСЫ И ВЕРСИОНИРОВАНИЕ
# ---------------------------------------------------------------------------

# ── SLIDE 34 — Дозапросы — в двух словах ─────────────────────────────────────
s = deck.add(prs, "header_text")
deck.set_title(s, "Дозапросы — в двух словах")
deck.fill_bodies(s, [[
    "Кастомный экшен в JSON тянет ответ из ручки",
    "Клиент применяет полученный DivData к текущей вёрстке",
    "Сервер кодирует URL прямо в кнопке",
    "Клиент не знает бизнес-логики — просто зовёт action",
    "Идею показали — идите дальше, дальше там страшнее",
]])

# Simple flow diagram instead of placeholder
deck.card(s, 3.8, 28.0, 60.0, 7.0, fill="lilac_bg")
deck.textbox(s, 4.6, 28.8, 20.0, 2.0, "Тап по кнопке", size=14, color="ink", bold=True)
deck.arrow(s, 18.0, 32.0, 22.0, 32.0, color="red")
deck.textbox(s, 22.0, 28.8, 16.0, 2.0, "custom action\n→ HTTP GET", size=13, color="ink2")
deck.arrow(s, 38.0, 32.0, 42.0, 32.0, color="red")
deck.textbox(s, 42.0, 28.8, 16.0, 2.0, "новый DivData\nприменяется", size=13, color="ink2")

# ── SLIDE 35 — Версионирование — в двух словах ───────────────────────────────
s = deck.add(prs, "header_text")
deck.set_title(s, "Версионирование — в двух словах")
deck.fill_bodies(s, [[
    "В DivKit нет встроенного версионирования",
    "Обычно версионируем экспериментами",
    "Новый лейаут: ограничен минимальной версией приложения с экспериментом",
    "Additive changes безопасны — старые клиенты игнорируют неизвестные поля",
    "Breaking changes = минимальный минВерсия + флаг",
]])

deck.card(s, 3.8, 28.0, 60.0, 7.0, fill="yellow_bg")
deck.textbox(s, 4.6, 28.8, 24.0, 2.0, "Новый лейаут", size=14, color="ink", bold=True)
deck.arrow(s, 20.0, 32.0, 24.0, 32.0, color="red")
deck.textbox(s, 24.0, 28.8, 20.0, 2.0, "AB-эксперимент\nmin_version ≥ X", size=13, color="ink2")
deck.arrow(s, 44.0, 32.0, 48.0, 32.0, color="red")
deck.textbox(s, 48.0, 28.8, 14.0, 2.0, "Раскатка\nпостепенно", size=13, color="ink2")

# ---------------------------------------------------------------------------
# ■ БЛОК 10. ФИНАЛ
# ---------------------------------------------------------------------------

# ── SLIDE 36 — Паттерны = наш опыт (big_thesis) ──────────────────────────────
s = deck.add(prs, "big_thesis")
deck.set_title(s, "Паттерны — это наш опыт")
deck.fill_bodies(s, [
    "Никаких «паттернов из учебника».\n"
    "Рендеринг, языки, темы, персистентность, A/B —\n"
    "удобный путь, который мы нашли и которым делимся.",
])

# ── SLIDE 37 — Итог: что взять с собой (numbered) ────────────────────────────
s = deck.add(prs, "blank")
slide_title(s, "Итог: что взять с собой", size=32)

takeaways = [
    ("1", "Бэкенд объявляет — клиент исполняет",
     "UI = JSON с сервера, клиент только рендерит"),
    ("2", "stored-values вместо ручного state",
     "Персистентность без SharedPreferences"),
    ("3", "Один ответ — много экранов",
     "templates + screens, меньше round-trip"),
    ("4", "Выражения вместо перерисовки",
     "Тема, компакт — меняются без рефетча"),
    ("5", "Бэкенд рендерит, не собирает данные",
     "Чистая роль: данные → вёрстка"),
]

card_w = 27.0
card_h = 6.0
gap_x = 3.0
gap_y = 1.8
start_x_t = 3.8
start_y_t = 6.5
fills_t = ["lilac_bg", "pink_bg", "yellow_bg", "pink_bg", "lilac_bg"]

for i, (num, title, body) in enumerate(takeaways):
    col = i % 2
    row = i // 2
    x = start_x_t + col * (card_w + gap_x)
    y = start_y_t + row * (card_h + gap_y)
    if i == 4:
        x = start_x_t + (card_w + gap_x) * 0.5
    deck.card(s, x, y, card_w, card_h, fill=fills_t[i])
    deck.number_badge(s, x + 0.4, y + card_h / 2 - 0.8, int(num))
    deck.textbox(s, x + 3.0, y + 0.8, card_w - 3.4, 2.2,
                 title, size=15, bold=True, color="ink")
    deck.textbox(s, x + 3.0, y + 3.2, card_w - 3.4, 2.4,
                 body, size=12, color="ink2")

# ── SLIDE 38 — Код и вопросы ─────────────────────────────────────────────────
s = deck.add(prs, "questions")
deck.set_title(s, "Код и вопросы")
deck.fill_bodies(s, [
    "github.com/divkit/divkit-weather",
    "Клонируйте, запускайте, ломайте",
])

# QR placeholder — positioned bottom-right, below the template body text zone
# Template body[0] (URL) lands ~63% height = ~24cm; place QR below that
deck.placeholder(s, 44.0, 27.5, 18.0, 9.0,
                 kind="screen", caption="QR: репозиторий")

# ---------------------------------------------------------------------------
prs.save(OUT)
print(f"Saved {OUT} ({len(prs.slides)} slides)")
