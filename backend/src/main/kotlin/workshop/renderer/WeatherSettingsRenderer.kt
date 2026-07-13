package workshop.renderer

import divkit.dsl.Color
import divkit.dsl.Div
import divkit.dsl.Divan
import divkit.dsl.action
import divkit.dsl.bold
import divkit.dsl.border
import divkit.dsl.center
import divkit.dsl.color
import divkit.dsl.container
import divkit.dsl.core.expression
import divkit.dsl.data
import divkit.dsl.divan
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.fixedSize
import divkit.dsl.horizontal
import divkit.dsl.input
import divkit.dsl.matchParentSize
import divkit.dsl.on_variable
import divkit.dsl.scope.DivScope
import divkit.dsl.single_line_text
import divkit.dsl.solidBackground
import divkit.dsl.stringVariable
import divkit.dsl.text
import divkit.dsl.trigger
import divkit.dsl.url
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize
import workshop.l10n.Localizer

class WeatherSettingsRenderer(
    private val localizer: Localizer,
) {

    // design-system constants shared by all cards
    private val screenBgExpr = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"
    private val surfaceExpr = "@{theme == 'dark' ? '#FF2C2C2E' : '#FFFFFFFF'}"
    private val primaryTextExpr = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"
    private val inputFieldExpr = "@{theme == 'dark' ? '#FF3A3A3C' : '#FFF2F2F7'}"

    /** Card-surface wrapper: theme-aware rounded container with an optional section header. */
    private fun DivScope.card(header: String?, items: List<Div>): Div = container(
        orientation = vertical,
        width = matchParentSize(),
        margins = edgeInsets(bottom = 12),
        paddings = edgeInsets(start = 16, top = 16, end = 16, bottom = 16),
        background = listOf(solidBackground(color("#FFFFFFFF")).evaluate(color = expression<Color>(surfaceExpr))),
        border = border(cornerRadius = 16),
        items = if (header != null) {
            listOf(
                text(
                    width = wrapContentSize(),
                    text = header,
                    fontSize = 17,
                    fontWeight = bold,
                    textColor = color("#FF1C1C1E"),
                    margins = edgeInsets(bottom = 8),
                ).evaluate(textColor = expression<Color>(primaryTextExpr)),
            ) + items
        } else {
            items
        },
    )

    fun render(): Pair<String, Divan> {
        val card = divan {
            // shared "search" action: fired by the on-change trigger, the keyboard enter key,
            // and the visible Search button — all three dispatch the identical url.
            val searchAction = action(
                logId = "city_search",
                url = url("weather-app://city_search?q=@{city_query}"),
            )

            data(
                logId = "main_settings",
                variables = listOf(stringVariable(name = "city_query", value = "")),
                variableTriggers = listOf(
                    trigger(actions = listOf(searchAction), mode = on_variable)
                        .evaluate(condition = expression<Boolean>("@{city_query != ''}")),
                ),
                div = container(
                    orientation = vertical,
                    width = matchParentSize(),
                    background = listOf(solidBackground(color("#FFF2F2F7")).evaluate(color = expression<Color>(screenBgExpr))),
                    paddings = edgeInsets(start = 16, top = 16, end = 16, bottom = 16),
                    items = listOf(
                        // Title — theme-aware
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("settings.title", "Settings"),
                            fontSize = 28,
                            fontWeight = bold,
                            textColor = color("#FF1C1C1E"),
                            margins = edgeInsets(bottom = 16),
                        ).evaluate(textColor = expression<Color>(primaryTextExpr)),

                        // ── City-search card ────────────────────────────────────────
                        card(
                            header = localizer.getOrDefault("settings.city.label", "City"),
                            items = listOf(
                                input(
                                    width = matchParentSize(),
                                    height = fixedSize(44),
                                    textVariable = "city_query",
                                    hintText = localizer.getOrDefault("city.search.placeholder", "Поиск города"),
                                    hintColor = color("#FF8E8E93"),
                                    textColor = color("#FF1C1C1E"),
                                    fontSize = 16,
                                    keyboardType = single_line_text,
                                    paddings = edgeInsets(start = 12, top = 10, end = 12, bottom = 10),
                                    background = listOf(
                                        solidBackground(color("#FFF2F2F7")).evaluate(color = expression<Color>(inputFieldExpr)),
                                    ),
                                    border = border(cornerRadius = 10),
                                    enterKeyActions = listOf(searchAction),
                                ).evaluate(textColor = expression<Color>(primaryTextExpr)),
                                text(
                                    width = matchParentSize(),
                                    text = localizer.getOrDefault("city.search.button", "Найти"),
                                    fontSize = 16,
                                    fontWeight = bold,
                                    textAlignmentHorizontal = center,
                                    textColor = color("#FFFFFFFF"),
                                    background = listOf(solidBackground(color("#FF007AFF"))),
                                    border = border(cornerRadius = 10),
                                    paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                    margins = edgeInsets(top = 8),
                                    action = searchAction,
                                ),
                                container(
                                    id = "city_search_results",
                                    orientation = vertical,
                                    width = matchParentSize(),
                                    margins = edgeInsets(top = 8),
                                    items = emptyList(),
                                ),
                            ),
                        ),

                        // ── Theme section ──────────────────────────────────────────
                        card(
                            header = localizer.getOrDefault("settings.theme.label", "Theme"),
                            items = listOf(
                                container(
                                    orientation = horizontal,
                                    width = matchParentSize(),
                                    items = listOf(
                                        // reactive: theme_mode = "system"
                                        text(
                                            width = wrapContentSize(),
                                            text = localizer.getOrDefault("settings.theme.system", "System"),
                                            fontSize = 16,
                                            textAlignmentHorizontal = center,
                                            margins = edgeInsets(end = 8),
                                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                            background = listOf(
                                                solidBackground(color("#FF3A3A3C"))
                                                    .evaluate(color = expression<Color>("@{theme_mode == 'system' ? '#FF007AFF' : '#FF3A3A3C'}")),
                                            ),
                                            border = border(cornerRadius = 10),
                                            textColor = color("#FFFFFFFF"),
                                            action = action(
                                                logId = "set_theme_system",
                                                url = url("weather-app://set_theme?mode=system"),
                                            ),
                                        ),
                                        // reactive: theme_mode = "dark"
                                        text(
                                            width = wrapContentSize(),
                                            text = localizer.getOrDefault("settings.theme.dark", "Dark"),
                                            fontSize = 16,
                                            textAlignmentHorizontal = center,
                                            margins = edgeInsets(end = 8),
                                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                            background = listOf(
                                                solidBackground(color("#FF3A3A3C"))
                                                    .evaluate(color = expression<Color>("@{theme_mode == 'dark' ? '#FF007AFF' : '#FF3A3A3C'}")),
                                            ),
                                            border = border(cornerRadius = 10),
                                            textColor = color("#FFFFFFFF"),
                                            action = action(
                                                logId = "set_theme_dark",
                                                url = url("weather-app://set_theme?mode=dark"),
                                            ),
                                        ),
                                        // reactive: theme_mode = "light"
                                        text(
                                            width = wrapContentSize(),
                                            text = localizer.getOrDefault("settings.theme.light", "Light"),
                                            fontSize = 16,
                                            textAlignmentHorizontal = center,
                                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                            background = listOf(
                                                solidBackground(color("#FF3A3A3C"))
                                                    .evaluate(color = expression<Color>("@{theme_mode == 'light' ? '#FF007AFF' : '#FF3A3A3C'}")),
                                            ),
                                            border = border(cornerRadius = 10),
                                            textColor = color("#FFFFFFFF"),
                                            action = action(
                                                logId = "set_theme_light",
                                                url = url("weather-app://set_theme?mode=light"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),

                        // ── Compact mode section ────────────────────────────────────
                        card(
                            header = localizer.getOrDefault("settings.compact.label", "Mode"),
                            items = listOf(
                                container(
                                    orientation = horizontal,
                                    width = matchParentSize(),
                                    items = listOf(
                                        // reactive: compact = true
                                        text(
                                            width = wrapContentSize(),
                                            text = localizer.getOrDefault("settings.compact.on", "Compact"),
                                            fontSize = 16,
                                            textAlignmentHorizontal = center,
                                            margins = edgeInsets(end = 8),
                                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                            background = listOf(
                                                solidBackground(color("#FF3A3A3C"))
                                                    .evaluate(color = expression<Color>("@{compact ? '#FF34C759' : '#FF3A3A3C'}")),
                                            ),
                                            border = border(cornerRadius = 10),
                                            textColor = color("#FFFFFFFF"),
                                            action = action(
                                                logId = "set_compact_on",
                                                url = url("weather-app://set_compact?value=true"),
                                            ),
                                        ),
                                        // reactive: compact = false
                                        text(
                                            width = wrapContentSize(),
                                            text = localizer.getOrDefault("settings.compact.off", "Normal"),
                                            fontSize = 16,
                                            textAlignmentHorizontal = center,
                                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                            background = listOf(
                                                solidBackground(color("#FF3A3A3C"))
                                                    .evaluate(color = expression<Color>("@{compact ? '#FF3A3A3C' : '#FF34C759'}")),
                                            ),
                                            border = border(cornerRadius = 10),
                                            textColor = color("#FFFFFFFF"),
                                            action = action(
                                                logId = "set_compact_off",
                                                url = url("weather-app://set_compact?value=false"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),

                        // ── Language section ────────────────────────────────────────
                        card(
                            header = localizer.getOrDefault("settings.lang.label", "Language"),
                            items = listOf(
                                container(
                                    orientation = horizontal,
                                    width = matchParentSize(),
                                    items = listOf(
                                        text(
                                            width = wrapContentSize(),
                                            text = localizer.getOrDefault("settings.lang.ru", "Russian"),
                                            fontSize = 16,
                                            textAlignmentHorizontal = center,
                                            margins = edgeInsets(end = 8),
                                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                            background = listOf(solidBackground(color("#FF5856D6"))),
                                            border = border(cornerRadius = 10),
                                            textColor = color("#FFFFFFFF"),
                                            action = action(
                                                logId = "set_lang_ru",
                                                url = url("weather-app://set_lang?value=ru"),
                                            ),
                                        ),
                                        text(
                                            width = wrapContentSize(),
                                            text = localizer.getOrDefault("settings.lang.en", "English"),
                                            fontSize = 16,
                                            textAlignmentHorizontal = center,
                                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                            background = listOf(solidBackground(color("#FFFF2D55"))),
                                            border = border(cornerRadius = 10),
                                            textColor = color("#FFFFFFFF"),
                                            action = action(
                                                logId = "set_lang_en",
                                                url = url("weather-app://set_lang?value=en"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),

                        // ── Navigation ──────────────────────────────────────────────
                        container(
                            orientation = horizontal,
                            width = matchParentSize(),
                            items = listOf(
                                text(
                                    width = wrapContentSize(),
                                    text = localizer.getOrDefault("back", "Back"),
                                    fontSize = 16,
                                    textAlignmentHorizontal = center,
                                    margins = edgeInsets(end = 8),
                                    paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                    background = listOf(solidBackground(color("#FF8E8E93"))),
                                    border = border(cornerRadius = 10),
                                    textColor = color("#FFFFFFFF"),
                                    action = action(
                                        logId = "nav_back",
                                        url = url("weather-app://back"),
                                    ),
                                ),
                                text(
                                    width = wrapContentSize(),
                                    text = localizer.getOrDefault("nav.main", "Home"),
                                    fontSize = 16,
                                    textAlignmentHorizontal = center,
                                    paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                                    background = listOf(solidBackground(color("#FF007AFF"))),
                                    border = border(cornerRadius = 10),
                                    textColor = color("#FFFFFFFF"),
                                    action = action(
                                        logId = "nav_main",
                                        url = url("weather-app://navigate?screen=main"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        return "settings" to card
    }
}
