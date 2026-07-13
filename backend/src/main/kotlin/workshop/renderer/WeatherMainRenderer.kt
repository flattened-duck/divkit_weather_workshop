package workshop.renderer

import divkit.dsl.Color
import divkit.dsl.Divan
import divkit.dsl.Visibility
import divkit.dsl.action
import divkit.dsl.actionSetStoredValue
import divkit.dsl.actionSetVariable
import divkit.dsl.bold
import divkit.dsl.booleanValue
import divkit.dsl.booleanVariable
import divkit.dsl.border
import divkit.dsl.center
import divkit.dsl.color
import divkit.dsl.container
import divkit.dsl.core.bind
import divkit.dsl.core.expression
import divkit.dsl.core.reference
import divkit.dsl.data
import divkit.dsl.divan
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.fixedSize
import divkit.dsl.horizontal
import divkit.dsl.matchParentSize
import divkit.dsl.overlap
import divkit.dsl.render
import divkit.dsl.right
import divkit.dsl.solidBackground
import divkit.dsl.text
import divkit.dsl.url
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize
import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.WeatherData
import workshop.templates.SharedTemplates

class WeatherMainRenderer(
    private val weatherData: WeatherData,
    private val localizer: Localizer,
) {

    fun render(): Pair<String, Divan> {
        val card = divan {
            buildCard(this)
        }
        return "main" to card
    }

    private fun buildCard(scope: divkit.dsl.scope.DivScope) = with(scope) {
        val today = weatherData.today
        val tomorrow = weatherData.tomorrow

        val dayLabelRef = reference<String>("day_label")
        val tempTextRef = reference<String>("temp_text")
        val conditionTextRef = reference<String>("condition_text")
        val navTextRef = reference<String>("nav_text")
        val navActionRef = reference<divkit.dsl.Action>("nav_action")

        // Theme-reactive background: dark → very dark surface, light → system gray
        val bgColorExpr = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"

        // Theme-reactive title color: dark → white, light → near-black
        val titleColorExpr = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"

        // Stored Values demo: show ⟺ not dismissed this session AND widget not installed
        // AND not currently within the delayed re-show TTL window.
        // getTimestamp/nowLocal don't exist as builtins in open DivKit 32.6.0 (confirmed via
        // div-evaluable bytecode + runtime), so the "3-day delay" uses the native
        // set_stored_value `lifetime` (seconds) TTL/expiration instead of datetime arithmetic.
        val visExpr = "@{(!popup_dismissed && !getStoredBooleanValue('widget_set_up', false) && " +
            "!getStoredBooleanValue('widget_popup_delayed', false)) ? 'visible' : 'gone'}"

        val actInstallSet = action(
            logId = "popup_install",
            url = url("div-action://set_stored_value"),
            typed = actionSetStoredValue(
                name = "widget_set_up",
                value = booleanValue(value = true),
                lifetime = Int.MAX_VALUE,
            ),
        )
        val actCloseDelay = action(
            logId = "popup_delay",
            url = url("div-action://set_stored_value"),
            typed = actionSetStoredValue(
                name = "widget_popup_delayed",
                value = booleanValue(value = true),
                lifetime = POPUP_DELAY_LIFETIME_SECONDS,
            ),
        )
        val actInstallDismiss = action(
            logId = "popup_close_install",
            url = url("div-action://set_variable"),
            typed = actionSetVariable(
                variableName = "popup_dismissed",
                value = booleanValue(value = true),
            ),
        )
        val actCloseDismiss = action(
            logId = "popup_close_x",
            url = url("div-action://set_variable"),
            typed = actionSetVariable(
                variableName = "popup_dismissed",
                value = booleanValue(value = true),
            ),
        )

        val closeX = text(
            text = "×",
            width = wrapContentSize(),
            fontSize = 24,
            textColor = color("#FF8E8E93"),
            alignmentHorizontal = right,
            actions = listOf(actCloseDelay, actCloseDismiss),
        )
        val title = text(
            text = localizer.getOrDefault("popup.widget.title", "Add the weather widget to your home screen"),
            width = matchParentSize(),
            fontSize = 18,
            fontWeight = bold,
            textColor = color("#FF1C1C1E"),
            margins = edgeInsets(top = 8),
        )
        val installBtn = text(
            text = localizer.getOrDefault("popup.widget.install", "Install"),
            width = matchParentSize(),
            fontSize = 16,
            fontWeight = bold,
            textColor = color("#FFFFFFFF"),
            textAlignmentHorizontal = center,
            background = listOf(solidBackground(color("#FF007AFF"))),
            border = border(cornerRadius = 10),
            paddings = edgeInsets(start = 16, top = 12, end = 16, bottom = 12),
            margins = edgeInsets(top = 20),
            actions = listOf(actInstallSet, actInstallDismiss),
        )
        val popupCard = container(
            orientation = vertical,
            width = fixedSize(300),
            background = listOf(solidBackground(color("#FFFFFFFF"))),
            border = border(cornerRadius = 16),
            paddings = edgeInsets(start = 20, top = 16, end = 20, bottom = 20),
            items = listOf(closeX, title, installBtn),
        )
        val popupOverlay = container(
            orientation = vertical,
            width = matchParentSize(),
            height = matchParentSize(),
            contentAlignmentHorizontal = center,
            contentAlignmentVertical = center,
            background = listOf(solidBackground(color("#99000000"))),
            items = listOf(popupCard),
        ).evaluate(visibility = expression<Visibility>(visExpr))

        val rootContent = container(
            orientation = vertical,
            width = matchParentSize(),
            background = listOf(
                solidBackground().evaluate(color = expression<Color>(bgColorExpr)),
            ),
            paddings = edgeInsets(start = 16, top = 16, end = 16, bottom = 16),
            items = listOf(
                // Screen title — theme-aware text color
                text(
                    width = wrapContentSize(),
                    text = localizer.getOrDefault("screen.main.title", "Weather"),
                    fontSize = 28,
                    fontWeight = bold,
                    textColor = color("#FF1C1C1E"),
                    margins = edgeInsets(bottom = 16),
                ).evaluate(textColor = expression<Color>(titleColorExpr)),
                // Weather cards row: today + tomorrow via shared template
                container(
                    orientation = horizontal,
                    width = matchParentSize(),
                    items = listOf(
                        render(
                            SharedTemplates.weatherCard,
                            dayLabelRef bind localizer.getOrDefault("day.today", "Today"),
                            tempTextRef bind "${today.tempC}°C",
                            conditionTextRef bind localizer.getOrDefault(
                                "condition.${today.condition.name}",
                                today.condition.name,
                            ),
                        ),
                        render(
                            SharedTemplates.weatherCard,
                            dayLabelRef bind localizer.getOrDefault("day.tomorrow", "Tomorrow"),
                            tempTextRef bind "${tomorrow.tempC}°C",
                            conditionTextRef bind localizer.getOrDefault(
                                "condition.${tomorrow.condition.name}",
                                tomorrow.condition.name,
                            ),
                        ),
                    ),
                ),
                // Navigation buttons row via shared template
                container(
                    orientation = horizontal,
                    width = matchParentSize(),
                    margins = edgeInsets(top = 16),
                    items = listOf(
                        render(
                            SharedTemplates.navButton,
                            navTextRef bind localizer.getOrDefault("nav.settings", "Settings"),
                            navActionRef bind action(
                                logId = "nav_settings",
                                url = url("weather-app://navigate?screen=settings"),
                            ),
                        ),
                        render(
                            SharedTemplates.navButton,
                            navTextRef bind localizer.getOrDefault("nav.about", "About"),
                            navActionRef bind action(
                                logId = "nav_about",
                                url = url("weather-app://navigate?screen=about"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        data(
            logId = "main_weather",
            variables = listOf(booleanVariable(name = "popup_dismissed", value = false)),
            div = container(
                orientation = overlap,
                width = matchParentSize(),
                height = matchParentSize(),
                items = listOf(rootContent, popupOverlay),
            ),
        )
    }

    private companion object {
        // "Install" widget popup re-show delay after tapping ×. Native set_stored_value TTL
        // (seconds); see visExpr/actCloseDelay above for why this replaces datetime arithmetic.
        const val POPUP_DELAY_LIFETIME_SECONDS = 259_200
    }
}
