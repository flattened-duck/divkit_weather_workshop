package workshop.renderer

import divkit.dsl.Color
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
import divkit.dsl.horizontal
import divkit.dsl.matchParentSize
import divkit.dsl.solidBackground
import divkit.dsl.text
import divkit.dsl.url
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize
import workshop.l10n.Localizer

class WeatherSettingsRenderer(
    private val localizer: Localizer,
) {

    fun render(): Pair<String, Divan> {
        val bgColorExpr = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"
        val titleColorExpr = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"
        val sectionColorExpr = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"

        val card = divan {
            data(
                logId = "main_settings",
                div = container(
                    orientation = vertical,
                    width = matchParentSize(),
                    background = listOf(solidBackground(color("#FFF2F2F7")).evaluate(color = expression<Color>(bgColorExpr))),
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
                        ).evaluate(textColor = expression<Color>(titleColorExpr)),

                        // ── Theme section ──────────────────────────────────────────
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("settings.theme.label", "Theme"),
                            fontSize = 18,
                            fontWeight = bold,
                            textColor = color("#FF1C1C1E"),
                            margins = edgeInsets(bottom = 8),
                        ).evaluate(textColor = expression<Color>(sectionColorExpr)),
                        container(
                            orientation = horizontal,
                            width = matchParentSize(),
                            margins = edgeInsets(bottom = 16),
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

                        // ── Compact mode section ────────────────────────────────────
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("settings.compact.label", "Mode"),
                            fontSize = 18,
                            fontWeight = bold,
                            textColor = color("#FF1C1C1E"),
                            margins = edgeInsets(bottom = 8),
                        ).evaluate(textColor = expression<Color>(sectionColorExpr)),
                        container(
                            orientation = horizontal,
                            width = matchParentSize(),
                            margins = edgeInsets(bottom = 16),
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

                        // ── Language section ────────────────────────────────────────
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("settings.lang.label", "Language"),
                            fontSize = 18,
                            fontWeight = bold,
                            textColor = color("#FF1C1C1E"),
                            margins = edgeInsets(bottom = 8),
                        ).evaluate(textColor = expression<Color>(sectionColorExpr)),
                        container(
                            orientation = horizontal,
                            width = matchParentSize(),
                            margins = edgeInsets(bottom = 24),
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
