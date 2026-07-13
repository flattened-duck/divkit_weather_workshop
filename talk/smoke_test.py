"""Smoke test: build 3 representative slides to validate the helper + render
pipeline + brand fonts/colors before the real build."""

import deck

prs = deck.new_deck()

# 1) Title
s = deck.add(prs, "title")
deck.set_title(s, "BDUI и DivKit")
deck.fill_bodies(s, ["Опыт большого проекта: bromapi + Android Browser",
                     "Имя Фамилия · бэкенд-разработчик"])

# 2) Blank canvas — a small architecture diagram (cards + arrows)
s = deck.add(prs, "blank")
deck.textbox(s, deck.MARGIN_L, 2.45, 50, 3.5, "Кто что делает", size=34, bold=True)
deck.card_with_text(s, 3.8, 12, 17, 9, "Источники данных",
                    "Сервисы Яндекса собирают данные", fill="lilac_bg")
deck.card_with_text(s, 25.4, 12, 17, 9, "bromapi (мы)",
                    "Только рендерим: данные → BDUI", fill="pink_bg", accent="red")
deck.card_with_text(s, 47, 12, 17, 9, "Клиент",
                    "Browser / Search App рисует UI", fill="yellow_bg")
deck.arrow(s, 20.8, 16.5, 25.4, 16.5)
deck.arrow(s, 42.4, 16.5, 47, 16.5)

# 3) Quote / section divider
s = deck.add(prs, "quote")
deck.set_title(s, "Мы не собираем данные.\nМы их рендерим.")

out = "/Users/the-leo/divkit-weather-workshop/talk/smoke.pptx"
prs.save(out)
print("saved", out, "slides:", len(prs.slides._sldIdLst))
