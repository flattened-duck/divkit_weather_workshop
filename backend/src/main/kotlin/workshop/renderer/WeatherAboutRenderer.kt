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
import divkit.dsl.data
import divkit.dsl.divan
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.expression.divanExpression
import divkit.dsl.expression.plus
import divkit.dsl.gallery
import divkit.dsl.matchParentSize
import divkit.dsl.scope.DivScope
import divkit.dsl.solidBackground
import divkit.dsl.text
import divkit.dsl.url
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize
import workshop.l10n.Localizer

class WeatherAboutRenderer(
    private val localizer: Localizer,
) {

    /** Card-surface wrapper: theme-aware rounded container with an optional section header. */
    private fun DivScope.card(header: String?, items: List<Div>): Div = container(
        orientation = vertical,
        width = matchParentSize(),
        margins = edgeInsets(bottom = 12),
        paddings = edgeInsets(start = 16, top = 16, end = 16, bottom = 16),
        background = listOf(solidBackground(color("#FFFFFFFF")).evaluate(color = Theme.SURFACE.divanExpression<Color>())),
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
                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression<Color>()),
            ) + items
        } else {
            items
        },
    )

    fun render(): Pair<String, Divan> {
        val card = divan {
            data(
                logId = "main_about",
                div = container(
                    orientation = vertical,
                    width = matchParentSize(),
                    height = matchParentSize(),
                    background = listOf(solidBackground(color("#FFF2F2F7")).evaluate(color = Theme.SCREEN_BG.divanExpression<Color>())),
                    items = listOf(
                        gallery(
                            id = "about_scroll",
                            orientation = vertical,
                            width = matchParentSize(),
                            height = matchParentSize(),
                            paddings = edgeInsets(start = 16, end = 16).evaluate(
                                top = (16 + DivVars.STATUS_INSET).divanExpression<Int>(),
                                bottom = (16 + DivVars.NAV_INSET).divanExpression<Int>(),
                            ),
                            items = listOf(
                        // Title — theme-aware
                        text(
                            width = wrapContentSize(),
                            text = localizer.getOrDefault("about.title", "About"),
                            fontSize = 28,
                            fontWeight = bold,
                            textColor = color("#FF1C1C1E"),
                            margins = edgeInsets(bottom = 16),
                        ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression<Color>()),

                        // ── Info card: app name + version ───────────────────────────
                        card(
                            header = null,
                            items = listOf(
                                text(
                                    width = wrapContentSize(),
                                    text = "DivKit Weather Workshop",
                                    fontSize = 20,
                                    fontWeight = bold,
                                    textColor = color("#FF1C1C1E"),
                                    margins = edgeInsets(bottom = 4),
                                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression<Color>()),
                                text(
                                    width = wrapContentSize(),
                                    text = localizer.getOrDefault("about.version", "Version 1.0.0"),
                                    fontSize = 13,
                                    textColor = color("#FF8E8E93"),
                                ),
                            ),
                        ),

                        // ── GitHub link — full-width accent button ──────────────────
                        text(
                            width = matchParentSize(),
                            text = localizer.getOrDefault("about.repo", "GitHub"),
                            fontSize = 16,
                            fontWeight = bold,
                            textColor = color("#FFFFFFFF"),
                            textAlignmentHorizontal = center,
                            margins = edgeInsets(bottom = 24),
                            paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                            background = listOf(solidBackground(color("#FF007AFF"))),
                            border = border(cornerRadius = 10),
                            action = action(
                                logId = "open_github",
                                url = url("https://github.com/divkit/divkit"),
                            ),
                        ),

                        // ── Navigation ──────────────────────────────────────────────
                        // Single button: every sub-screen is reached only from main, so a
                        // separate "Back" action would be a redundant duplicate of "Home".
                        text(
                            id = "nav_home",
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
