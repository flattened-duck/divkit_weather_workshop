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

class WeatherAboutRenderer(
    private val localizer: Localizer,
) {

    fun render(): Pair<String, Divan> {
        val bgColorExpr = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"
        val titleColorExpr = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"
        val bodyColorExpr = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"

        val card = divan {
            data(
                logId = "main_about",
                div = container(
                    orientation = vertical,
                    width = matchParentSize(),
                    background = listOf(solidBackground(color("#FFF2F2F7")).evaluate(color = expression<Color>(bgColorExpr))),
                    paddings = edgeInsets(start = 16, top = 16, end = 16, bottom = 16),
                    items = listOf(
                        // Title — theme-aware
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("about.title", "About"),
                            fontSize = 28,
                            fontWeight = bold,
                            textColor = color("#FF1C1C1E"),
                            margins = edgeInsets(bottom = 16),
                        ).evaluate(textColor = expression<Color>(titleColorExpr)),
                        // App name — theme-aware
                        text(
                            width = wrapContentSize(),
                            text = "DivKit Weather Workshop",
                            fontSize = 20,
                            fontWeight = bold,
                            textColor = color("#FF1C1C1E"),
                            margins = edgeInsets(bottom = 8),
                        ).evaluate(textColor = expression<Color>(bodyColorExpr)),
                        // Version
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("about.version", "Version 1.0.0"),
                            fontSize = 16,
                            textColor = color("#FF8E8E93"),
                            margins = edgeInsets(bottom = 24),
                        ),
                        // GitHub link
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("about.repo", "GitHub"),
                            fontSize = 16,
                            textColor = color("#FF007AFF"),
                            textAlignmentHorizontal = center,
                            margins = edgeInsets(bottom = 24),
                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                            background = listOf(solidBackground(color("#FFE5F0FF"))),
                            border = border(cornerRadius = 10),
                            action = action(
                                logId = "open_github",
                                url = url("https://github.com/divkit/divkit"),
                            ),
                        ),
                        // Navigation buttons
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
        return "about" to card
    }
}
