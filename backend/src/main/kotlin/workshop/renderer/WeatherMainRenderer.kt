package workshop.renderer

import divkit.dsl.Action
import divkit.dsl.Color
import divkit.dsl.Div
import divkit.dsl.Divan
import divkit.dsl.EdgeInsets
import divkit.dsl.Url
import divkit.dsl.Visibility
import divkit.dsl.action
import divkit.dsl.actionSetStoredValue
import divkit.dsl.actionSetVariable
import divkit.dsl.bold
import divkit.dsl.booleanValue
import divkit.dsl.booleanVariable
import divkit.dsl.border
import divkit.dsl.bottom
import divkit.dsl.center
import divkit.dsl.changeBoundsTransition
import divkit.dsl.color
import divkit.dsl.container
import divkit.dsl.core.expression
import divkit.dsl.core.valueArrayElement
import divkit.dsl.custom
import divkit.dsl.data
import divkit.dsl.divan
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.extension
import divkit.dsl.fadeTransition
import divkit.dsl.fill
import divkit.dsl.fixedSize
import divkit.dsl.gallery
import divkit.dsl.grid
import divkit.dsl.horizontal
import divkit.dsl.image
import divkit.dsl.linearGradient
import divkit.dsl.matchParentSize
import divkit.dsl.overlap
import divkit.dsl.right
import divkit.dsl.scope.DivScope
import divkit.dsl.solidBackground
import divkit.dsl.state
import divkit.dsl.stateItem
import divkit.dsl.text
import divkit.dsl.top
import divkit.dsl.url
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize
import kotlin.math.roundToInt
import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.ConditionCode
import workshop.proto.WeatherDataOuterClass.DailyPoint
import workshop.proto.WeatherDataOuterClass.HourlyPoint
import workshop.proto.WeatherDataOuterClass.WeatherData
import workshop.weather.bgBase

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

    private fun loc(key: String, fallback: String): String = localizer.getOrDefault(key, fallback)

    private fun uvBand(uv: Int): String = when {
        uv <= 2 -> loc("weather.uv.low", "Low")
        uv <= 5 -> loc("weather.uv.moderate", "Moderate")
        uv <= 7 -> loc("weather.uv.high", "High")
        uv <= 10 -> loc("weather.uv.very_high", "Very high")
        else -> loc("weather.uv.extreme", "Extreme")
    }

    private fun buildCard(scope: DivScope) = with(scope) {
        val current = weatherData.current
        val daily = weatherData.dailyList
        val hourly = weatherData.hourlyList
        val daily0 = daily[0]

        val weekMin = daily.minOf { it.tempMin }
        val weekMax = daily.maxOf { it.tempMax }
        val span = maxOf(1, weekMax - weekMin)

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
        val popupImage = image(
            imageUrl = url(POPUP_IMAGE_URL),
            preview = POPUP_PREVIEW_DATA_URL,
            width = matchParentSize(),
            height = fixedSize(140),
            scale = fill,
            border = border(cornerRadius = 12),
            margins = edgeInsets(top = 8),
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
            items = listOf(closeX, popupImage, title, installBtn),
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

        // ---- Layer 0: full-screen weather background, theme-driven day/night photo swap ----
        // Selects the _day vs _night photo of the SAME condition base; no real time-of-day
        // dependency. Light theme -> day photo, dark theme -> night photo.
        val bgBaseName = bgBase(current.condition)
        val bgDayUrl = "$BG_IMAGE_BASE_URL${bgBaseName}_day.png"
        val bgNightUrl = "$BG_IMAGE_BASE_URL${bgBaseName}_night.png"
        val bgUrlExpr = "@{theme == 'dark' ? '$bgNightUrl' : '$bgDayUrl'}"
        val backgroundImage = image(
            width = matchParentSize(),
            height = matchParentSize(),
            scale = fill,
        ).evaluate(imageUrl = expression<Url>(bgUrlExpr))

        // ---- Layer 1: pinned collapsible header + scroll body ----
        val fullHeader = container(
            orientation = vertical,
            width = matchParentSize(),
            paddings = edgeInsets(start = 20, end = 20, bottom = 8)
                .evaluate(top = expression<Int>("@{24 + status_inset}")),
            transitionIn = fadeTransition(duration = 250),
            transitionOut = fadeTransition(duration = 250),
            items = listOf(
                text(
                    text = current.city,
                    width = wrapContentSize(),
                    fontSize = 20,
                    fontWeight = bold,
                ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
                text(
                    text = "${current.tempC}°",
                    width = wrapContentSize(),
                    fontSize = 72,
                    fontWeight = bold,
                    margins = edgeInsets(top = 4),
                ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
                text(
                    text = loc("condition.${current.condition.name}", current.condition.name),
                    width = wrapContentSize(),
                    fontSize = 18,
                ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
                container(
                    orientation = horizontal,
                    width = wrapContentSize(),
                    margins = edgeInsets(top = 6),
                    items = listOf(
                        text(
                            text = "↑ ${daily0.tempMax}°",
                            width = wrapContentSize(),
                            fontSize = 16,
                        ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
                        text(
                            text = "  ↓ ${daily0.tempMin}°",
                            width = wrapContentSize(),
                            fontSize = 16,
                            margins = edgeInsets(start = 12),
                        ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
                    ),
                ),
            ),
        )

        val compactHeader = container(
            orientation = vertical,
            width = matchParentSize(),
            paddings = edgeInsets(start = 20, end = 20, bottom = 8)
                .evaluate(top = expression<Int>("@{12 + status_inset}")),
            background = listOf(solidBackground().evaluate(color = expression<Color>(HEADER_SCRIM_EXPR))),
            transitionIn = fadeTransition(duration = 250),
            transitionOut = fadeTransition(duration = 250),
            items = listOf(
                text(
                    text = current.city,
                    width = wrapContentSize(),
                    fontSize = 17,
                    fontWeight = bold,
                ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
                text(
                    text = "${current.tempC}°  |  " + loc("condition.${current.condition.name}", current.condition.name),
                    width = wrapContentSize(),
                    fontSize = 15,
                    margins = edgeInsets(top = 2),
                ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
            ),
        )

        // Collapse variable "header_state" is a GLOBAL card variable declared by the
        // native client (Worktree C), same convention as the existing "theme"/"compact"
        // variables: never declared locally here (would shadow/conflict).
        // `default_state_id` as an expression is evaluated once and does NOT react to variable
        // writes; `state_id_variable` is the reactive binding DivKit's engine subscribes to
        // (see DivStateBinder.observeStateIdVariable in R-32.57, which calls switchToState on
        // every write of the named variable).
        // Overlays the gallery (root-level sibling, drawn after it) instead of sharing a
        // vertical column with it: a collapse-driven height change here must NOT re-layout
        // scrollBody, or the resulting scroll-offset shift flips the scroll_state collapse
        // threshold back and forth (collapse/expand feedback loop = jitter). See scrollBody's
        // top padding (HEADER_COMPACT_DP) for the matching reserved space, and the root
        // overlap's item order below.
        // The scrim lives on compactHeader only (see above) — fullHeader stays fully
        // transparent over the background photo (iOS large-title look); this wrapper carries
        // no background of its own.
        val headerState = state(
            id = "header",
            defaultStateId = "full",
            stateIdVariable = HEADER_STATE_VAR,
            transitionChange = changeBoundsTransition(duration = 250),
            states = listOf(
                stateItem(stateId = "full", div = fullHeader),
                stateItem(stateId = "collapsed", div = compactHeader),
            ),
            width = matchParentSize(),
            height = wrapContentSize(),
            alignmentVertical = top,
        )

        val hourlyGallery = gallery(
            orientation = horizontal,
            width = matchParentSize(),
            height = wrapContentSize(),
            itemSpacing = 12,
            items = hourly.map { hourCell(it) },
        )

        val weeklyBlock = container(
            orientation = vertical,
            width = matchParentSize(),
            margins = edgeInsets(top = 16),
            background = listOf(solidBackground().evaluate(color = expression<Color>(CARD_BG_EXPR))),
            border = border(cornerRadius = 16),
            paddings = edgeInsets(start = 8, top = 8, end = 8, bottom = 8),
            items = daily.map { dailyRow(it, weekMin, span) },
        )

        val uvFrac = current.uvIndex.coerceIn(0, 11) / 11.0
        val pressFrac = ((current.pressure - PRESS_MIN).toDouble() / (PRESS_MAX - PRESS_MIN))

        val feelsDelta = current.feelsC - current.tempC
        val feelsSubtitle = when {
            kotlin.math.abs(feelsDelta) <= 1 -> loc("feels.similar", "Similar to the actual temperature")
            feelsDelta > 1 -> loc("feels.warmer", "Feels warmer than it actually is")
            else -> loc("feels.cooler", "Feels cooler than it actually is")
        }
        val visSubtitle = when {
            current.visibility >= 20000 -> loc("vis.perfect", "Perfectly clear")
            current.visibility >= 10000 -> loc("vis.good", "Good visibility")
            else -> loc("vis.reduced", "Reduced visibility")
        }

        val sunsetArc = custom(
            customType = "sun_phase",
            customProps = mapOf("sunrise" to current.sunrise, "sunset" to current.sunset),
            width = matchParentSize(),
            height = fixedSize(120),
        )
        val sunsetCard = detailCard(
            icon = "🌇",
            title = loc("weather.sunset", "Sunset"),
            bigValue = current.sunset,
            body = sunsetArc,
            subtitle = loc("card.sunrise_at", "Sunrise at") + " " + current.sunrise,
            margins = edgeInsets(top = 16),
        )

        val detailsGrid = grid(
            columnCount = 2,
            width = matchParentSize(),
            margins = edgeInsets(top = 16),
            items = listOf(
                detailCard(
                    icon = "🔆",
                    title = loc("weather.uv", "UV index"),
                    bigValue = "${current.uvIndex}",
                    secondLine = uvBand(current.uvIndex),
                    body = markerScale(uvFrac, UV_SCALE_HEX),
                ),
                detailCard(
                    icon = "🌡️",
                    title = loc("feels_like", "feels like"),
                    bigValue = "${current.feelsC}°",
                    subtitle = feelsSubtitle,
                ),
                detailCard(
                    icon = "🌧️",
                    title = loc("weather.precipitation", "Precipitation"),
                    bigValue = "${daily0.precipProb}%",
                    subtitle = loc("precip.subtitle", "Chance today"),
                ),
                detailCard(
                    icon = "👁️",
                    title = loc("weather.visibility", "Visibility"),
                    bigValue = "${current.visibility / 1000} " + loc("unit.visibility", "km"),
                    subtitle = visSubtitle,
                ),
                detailCard(
                    icon = "💧",
                    title = loc("weather.humidity", "Humidity"),
                    bigValue = "${current.humidity}%",
                ),
                detailCard(
                    icon = "🧭",
                    title = loc("weather.pressure", "Pressure"),
                    bigValue = "${current.pressure} " + loc("unit.pressure", "hPa"),
                    body = markerScale(pressFrac, PRESS_SCALE_HEX),
                ),
                detailCard(
                    icon = "💨",
                    title = loc("weather.wind", "Wind"),
                    bigValue = "${current.wind} " + loc("unit.wind", "km/h"),
                ),
            ),
        )

        // The gallery's top padding reserves space for the COMPACT header height, which now
        // overlays it (see headerState above) instead of sitting above it in a shared vertical
        // column. Decoupling them stops the collapse-driven header resize from re-laying-out
        // (and thus re-scrolling) the gallery beneath it, which used to cause a collapse/expand
        // feedback loop via the scroll_state threshold (jitter). In expanded/normal mode the
        // transparent full header intentionally overlaps the first ~114dp of content — the
        // iOS large-title look, not a bug.
        val scrollBody = gallery(
            id = "main_scroll",
            orientation = vertical,
            extensions = listOf(extension(id = "scroll_state", params = mapOf("orientation" to "vertical"))),
            width = matchParentSize(),
            height = matchParentSize(),
            paddings = edgeInsets(start = 16, end = 16).evaluate(
                top = expression<Int>("@{$HEADER_COMPACT_DP + status_inset}"),
                bottom = expression<Int>("@{96 + nav_inset}"),
            ),
            items = listOf(hourlyGallery, weeklyBlock, sunsetCard, detailsGrid),
        )

        // ---- Layer 2: bottom overlay FAB row ----
        val fabRow = container(
            orientation = horizontal,
            width = matchParentSize(),
            height = wrapContentSize(),
            alignmentVertical = bottom,
            contentAlignmentHorizontal = center,
            paddings = edgeInsets().evaluate(bottom = expression<Int>("@{20 + nav_inset}")),
            items = listOf(
                fab("⚙️", action(logId = "fab_settings", url = url("weather-app://navigate?screen=settings"))),
                fab("☰", action(logId = "fab_about", url = url("weather-app://navigate?screen=about"))),
            ),
        )

        data(
            logId = "main_weather",
            variables = listOf(booleanVariable(name = "popup_dismissed", value = false)),
            div = container(
                orientation = overlap,
                width = matchParentSize(),
                height = matchParentSize(),
                items = listOf(backgroundImage, scrollBody, headerState, fabRow, popupOverlay),
            ),
        )
    }

    private fun DivScope.hourCell(h: HourlyPoint): Div = container(
        orientation = vertical,
        width = fixedSize(64),
        paddings = edgeInsets(start = 8, top = 8, end = 8, bottom = 8),
        background = listOf(solidBackground().evaluate(color = expression<Color>(CARD_BG_EXPR))),
        border = border(cornerRadius = 16),
        contentAlignmentHorizontal = center,
        items = listOf(
            text(
                text = h.time,
                width = wrapContentSize(),
                fontSize = 13,
                textAlignmentHorizontal = center,
            ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
            text(
                text = conditionEmoji(h.condition),
                width = wrapContentSize(),
                fontSize = 22,
                margins = edgeInsets(top = 4),
            ),
            text(
                text = "${h.tempC}°",
                width = wrapContentSize(),
                fontSize = 16,
                fontWeight = bold,
                margins = edgeInsets(top = 4),
            ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
        ),
    )

    private fun DivScope.dailyRow(d: DailyPoint, weekMin: Int, span: Int): Div {
        var offsetPx = ((d.tempMin - weekMin) * 100) / span
        val fillPx = maxOf(6, ((d.tempMax - d.tempMin) * 100) / span)
        if (offsetPx + fillPx > 100) offsetPx = 100 - fillPx

        val items = buildList<Div> {
            add(
                text(
                    text = d.weekday,
                    width = fixedSize(44),
                    fontSize = 16,
                ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
            )
            add(
                text(
                    text = conditionEmoji(d.condition),
                    width = fixedSize(32),
                    fontSize = 18,
                    textAlignmentHorizontal = center,
                ),
            )
            add(
                text(
                    text = "${d.tempMin}°",
                    width = fixedSize(40),
                    fontSize = 15,
                    textAlignmentHorizontal = right,
                ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
            )
            add(rangeBar(offsetPx, fillPx))
            add(
                text(
                    text = "${d.tempMax}°",
                    width = fixedSize(40),
                    fontSize = 15,
                    fontWeight = bold,
                ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
            )
            if (d.precipProb > 0) {
                add(
                    text(
                        text = "💧${d.precipProb}%",
                        width = wrapContentSize(),
                        fontSize = 12,
                        margins = edgeInsets(start = 6),
                    ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
                )
            }
        }

        return container(
            orientation = horizontal,
            width = matchParentSize(),
            paddings = edgeInsets(top = 10, bottom = 10, start = 8, end = 8),
            contentAlignmentVertical = center,
            items = items,
        )
    }

    private fun DivScope.rangeBar(offsetPx: Int, fillPx: Int): Div = container(
        orientation = overlap,
        width = fixedSize(100),
        height = fixedSize(6),
        margins = edgeInsets(start = 8, end = 8),
        border = border(cornerRadius = 3),
        background = listOf(solidBackground(color("#33FFFFFF"))),
        items = listOf(
            container(
                width = fixedSize(fillPx),
                height = fixedSize(6),
                margins = edgeInsets(start = offsetPx),
                border = border(cornerRadius = 3),
                background = listOf(solidBackground(color("#FFFF9500"))),
            ),
        ),
    )

    private fun DivScope.detailCard(
        icon: String,
        title: String,
        bigValue: String,
        secondLine: String? = null,
        body: Div? = null,
        subtitle: String? = null,
        margins: EdgeInsets = edgeInsets(start = 6, top = 6, end = 6, bottom = 6),
    ): Div = container(
        orientation = vertical,
        // weight is required for match_parent width to distribute evenly across grid columns
        // on Android (client/android/div GridContainer only splits free space among weighted
        // match_parent children); without it, columns collapse to near-zero width. Harmless when
        // detailCard is used standalone (sunsetCard, a single vertical-gallery item, not a grid
        // cell) since weight only matters among match_parent siblings sharing one parent.
        width = matchParentSize(weight = 1.0),
        margins = margins,
        paddings = edgeInsets(start = 14, top = 14, end = 14, bottom = 14),
        background = listOf(solidBackground().evaluate(color = expression<Color>(CARD_BG_EXPR))),
        border = border(cornerRadius = 16),
        items = buildList {
            add(
                text(
                    text = "$icon  ${title.uppercase()}",
                    width = wrapContentSize(),
                    fontSize = 12,
                ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
            )
            if (bigValue.isNotEmpty()) {
                add(
                    text(
                        text = bigValue,
                        width = wrapContentSize(),
                        fontSize = 28,
                        fontWeight = bold,
                        margins = edgeInsets(top = 8),
                    ).evaluate(textColor = expression<Color>(TITLE_COLOR_EXPR)),
                )
            }
            if (secondLine != null) {
                add(
                    text(
                        text = secondLine,
                        width = wrapContentSize(),
                        fontSize = 15,
                        margins = edgeInsets(top = 2),
                    ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
                )
            }
            if (body != null) {
                add(
                    container(
                        width = matchParentSize(),
                        margins = edgeInsets(top = 10),
                        items = listOf(body),
                    ),
                )
            }
            if (subtitle != null) {
                add(
                    text(
                        text = subtitle,
                        width = wrapContentSize(),
                        fontSize = 13,
                        margins = edgeInsets(top = 8),
                    ).evaluate(textColor = expression<Color>(SUB_COLOR_EXPR)),
                )
            }
        },
    )

    private fun DivScope.markerScale(fraction: Double, gradientHex: List<String>): Div {
        val offset = (fraction.coerceIn(0.0, 1.0) * (SCALE_W - MARKER_W)).roundToInt()
        return container(
            orientation = overlap,
            width = fixedSize(SCALE_W),
            height = fixedSize(16),
            items = listOf(
                container(
                    // gradient track
                    width = matchParentSize(),
                    height = fixedSize(6),
                    alignmentVertical = center,
                    border = border(cornerRadius = 3),
                    background = listOf(
                        linearGradient(
                            angle = 0,
                            colors = gradientHex.map { valueArrayElement(color(it)) },
                        ),
                    ),
                ),
                container(
                    // marker (white bar)
                    width = fixedSize(MARKER_W),
                    height = fixedSize(16),
                    margins = edgeInsets(start = offset),
                    border = border(cornerRadius = 2),
                    background = listOf(solidBackground(color("#FFFFFFFF"))),
                ),
            ),
        )
    }

    private fun DivScope.fab(glyph: String, act: Action?): Div = text(
        text = glyph,
        width = fixedSize(56),
        height = fixedSize(56),
        fontSize = 22,
        textAlignmentHorizontal = center,
        textAlignmentVertical = center,
        margins = edgeInsets(start = 8, end = 8),
        background = listOf(solidBackground(color("#CC1C1C1E"))),
        border = border(cornerRadius = 28),
        actions = if (act == null) null else listOf(act),
    )

    private companion object {
        // "Install" widget popup re-show delay after tapping ×. Native set_stored_value TTL
        // (seconds); see visExpr/actCloseDelay above for why this replaces datetime arithmetic.
        const val POPUP_DELAY_LIFETIME_SECONDS = 259_200

        const val TITLE_COLOR_EXPR = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"
        const val SUB_COLOR_EXPR = "@{theme == 'dark' ? '#FF9E9EA3' : '#FF6E6E73'}"
        const val CARD_BG_EXPR = "@{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}"
        const val HEADER_SCRIM_EXPR = "@{theme == 'dark' ? '#99000000' : '#99FFFFFF'}"

        // header_state ("full"/"collapsed") is a global card variable owned/written by the
        // native client (Worktree C); this card only references it by name via
        // state_id_variable, never declares it (see headerState above).
        const val HEADER_STATE_VAR = "header_state"

        // Dp reserved at the top of the scrollable gallery so content always starts below the
        // COMPACT header, which now overlays it instead of sharing a layout column (see
        // headerState/scrollBody above). Approximates compactHeader's own measured height
        // (paddings + one/two-line text) — tuned against an on-device screenshot. In normal
        // (expanded) mode the transparent full header intentionally overlaps content using this
        // same reserved space (iOS large-title look).
        const val HEADER_COMPACT_DP = 76

        const val BG_IMAGE_BASE_URL =
            "https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_"
        const val POPUP_IMAGE_URL =
            "https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/popup_image.png"

        // 28px low-quality JPEG preview of S3/popup_image.png (946 bytes), pinned verbatim.
        const val POPUP_PREVIEW_DATA_URL =
            "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAASABIAAD/4QBMRXhpZgAATU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAHKADAAQAAAABAAAAHAAAAAD/7QA4UGhvdG9zaG9wIDMuMAA4QklNBAQAAAAAAAA4QklNBCUAAAAAABDUHYzZjwCyBOmACZjs+EJ+/8AAEQgAHAAcAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/bAEMAExMTExMTIBMTIC0gICAtPS0tLS09TT09PT09TV1NTU1NTU1dXV1dXV1dXXBwcHBwcIODg4ODk5OTk5OTk5OTk//bAEMBFxgYJSMlQCMjQJloVWiZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmf/dAAQAAv/aAAwDAQACEQMRAD8A2NV1SexmWOJFYMuec+tZ8evXsjbVjT9f8afry7rlD/sf1NZ1mywyhmHSurkXs7panO5vntc2JtU1GFdzxIB+P+Nb8LmWFJDwWUE/iK568uo5Yto5Jrftv+PeL/cX+VcsW3G8lY30vZM//9DT1hN06f7v9TWR5Zrtmjjfl1B+opvkQ/3F/IV0wrJK1jnnRbd7nGeWa7K3/wBRH/uj+VL5EP8AcX8hUoAAwKipUUuhVOm47s//2Q=="

        const val SCALE_W = 120
        const val MARKER_W = 4
        const val PRESS_MIN = 980
        const val PRESS_MAX = 1040
        val UV_SCALE_HEX = listOf("#FF34C759", "#FFFFCC00", "#FFFF9500", "#FFFF3B30", "#FFAF52DE")
        val PRESS_SCALE_HEX = listOf("#FF5AC8FA", "#FF34C759", "#FFFF9500")

        fun conditionEmoji(condition: ConditionCode): String = when (condition) {
            ConditionCode.CLEAR -> "☀️"
            ConditionCode.CLOUDY -> "☁️"
            ConditionCode.RAIN -> "🌧️"
            ConditionCode.SNOW -> "❄️"
            ConditionCode.THUNDER -> "⛈️"
            ConditionCode.FOG -> "🌫️"
            else -> "🌡️" // ConditionCode.UNRECOGNIZED / future proto values
        }
    }
}
