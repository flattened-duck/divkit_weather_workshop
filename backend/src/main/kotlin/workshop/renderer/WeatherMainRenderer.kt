package workshop.renderer

import divkit.dsl.Action
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
import divkit.dsl.core.bind
import divkit.dsl.core.valueArrayElement
import divkit.dsl.custom
import divkit.dsl.data
import divkit.dsl.divan
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.expression.and
import divkit.dsl.expression.boolean
import divkit.dsl.expression.divanExpression
import divkit.dsl.expression.equalTo
import divkit.dsl.expression.getStoredBooleanValue
import divkit.dsl.expression.ifElse
import divkit.dsl.expression.not
import divkit.dsl.expression.plus
import divkit.dsl.expression.string
import divkit.dsl.extension
import divkit.dsl.fadeTransition
import divkit.dsl.fill
import divkit.dsl.fit
import divkit.dsl.fixedSize
import divkit.dsl.gallery
import divkit.dsl.gone
import divkit.dsl.grid
import divkit.dsl.horizontal
import divkit.dsl.image
import divkit.dsl.linearGradient
import divkit.dsl.matchParentSize
import divkit.dsl.overlap
import divkit.dsl.render
import divkit.dsl.right
import divkit.dsl.scope.DivScope
import divkit.dsl.solidBackground
import divkit.dsl.state
import divkit.dsl.stateItem
import divkit.dsl.text
import divkit.dsl.top
import divkit.dsl.url
import divkit.dsl.vertical
import divkit.dsl.visible
import divkit.dsl.wrapContentSize
import kotlin.math.roundToInt
import workshop.renderer.data.WeatherMainViewModel

class WeatherMainRenderer(
    private val vm: WeatherMainViewModel,
) {

    fun render(): Pair<String, Divan> {
        val card = divan {
            buildCard(this)
        }
        return "main" to card
    }

    private fun buildCard(scope: DivScope) = with(scope) {
        // Stored Values demo: show ⟺ not dismissed this session AND widget not installed
        // AND not currently within the delayed re-show TTL window.
        // getTimestamp/nowLocal don't exist as builtins in open DivKit 32.6.0 (confirmed via
        // div-evaluable bytecode + runtime), so the "3-day delay" uses the native
        // set_stored_value `lifetime` (seconds) TTL/expiration instead of datetime arithmetic.
        val visExpr = (
                !DivVars.POPUP_DISMISSED and
                        !getStoredBooleanValue("widget_set_up".string(), false.boolean()) and
                        !getStoredBooleanValue("widget_popup_delayed".string(), false.boolean())
                ).ifElse("visible", "gone")

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
            id = "popup_close",
            text = "×",
            width = wrapContentSize(),
            fontSize = 24,
            textColor = color(Colors.GRAY),
            alignmentHorizontal = right,
            alignmentVertical = top,
            actions = listOf(actCloseDelay, actCloseDismiss),
        )
        val popupImage = image(
            imageUrl = url(POPUP_IMAGE_URL),
            preview = POPUP_PREVIEW_DATA_URL,
            width = matchParentSize(),
            height = fixedSize(140),
            scale = fit,
            border = border(cornerRadius = 12),
            // Equal breathing room above and below the icon.
            margins = edgeInsets(top = 12, bottom = 12),
        )
        val title = text(
            text = vm.popupTitle,
            width = matchParentSize(),
            fontSize = 18,
            fontWeight = bold,
            textColor = color(Colors.NEAR_BLACK),
        )
        val installBtn = text(
            id = "popup_install",
            text = vm.popupInstall,
            width = matchParentSize(),
            fontSize = 16,
            fontWeight = bold,
            textColor = color(Colors.WHITE),
            textAlignmentHorizontal = center,
            background = listOf(solidBackground(color(Colors.BLUE))),
            border = border(cornerRadius = 10),
            paddings = edgeInsets(start = 16, top = 12, end = 16, bottom = 12),
            margins = edgeInsets(top = 20),
            actions = listOf(actInstallSet, actInstallDismiss),
        )
        // The close × is an overlay (top-right), NOT a flow row above the image — otherwise its
        // line height would add extra space above the icon and the top/bottom padding around the
        // icon wouldn't match. The content flows: image (symmetric 12/12 margins), title, button.
        val popupContent = container(
            orientation = vertical,
            width = matchParentSize(),
            items = listOf(popupImage, title, installBtn),
        )
        val popupCard = container(
            orientation = overlap,
            width = fixedSize(300),
            background = listOf(solidBackground(color(Colors.WHITE))),
            border = border(cornerRadius = 16),
            paddings = edgeInsets(start = 20, top = 16, end = 20, bottom = 20),
            items = listOf(popupContent, closeX),
        )
        val popupOverlay = container(
            orientation = vertical,
            width = matchParentSize(),
            height = matchParentSize(),
            contentAlignmentHorizontal = center,
            contentAlignmentVertical = center,
            background = listOf(solidBackground(color(Colors.SCRIM_BLACK))),
            items = listOf(popupCard),
        ).evaluate(visibility = visExpr.divanExpression())

        // ---- Layer 0: full-screen weather background, theme-driven day/night photo swap ----
        // Selects the _day vs _night photo of the SAME condition base; no real time-of-day
        // dependency. Light theme -> day photo, dark theme -> night photo.
        val bgDayUrl = "$BG_IMAGE_BASE_URL${vm.bgBaseName}_day.png"
        val bgNightUrl = "$BG_IMAGE_BASE_URL${vm.bgBaseName}_night.png"
        val bgUrlExpr = (DivVars.THEME equalTo "dark").ifElse(bgNightUrl, bgDayUrl)
        val backgroundImage = image(
            width = matchParentSize(),
            height = matchParentSize(),
            scale = fill,
        ).evaluate(imageUrl = bgUrlExpr.divanExpression())

        // ---- Layer 1: pinned collapsible header + scroll body ----
        val fullHeader = container(
            orientation = vertical,
            width = matchParentSize(),
            paddings = edgeInsets(start = 20, end = 20, bottom = 8)
                .evaluate(top = (24 + DivVars.STATUS_INSET).divanExpression()),
            transitionIn = fadeTransition(duration = 250),
            transitionOut = fadeTransition(duration = 250),
            items = listOf(
                text(
                    text = vm.city,
                    width = wrapContentSize(),
                    fontSize = 20,
                    fontWeight = bold,
                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()),
                text(
                    text = vm.currentTempLabel,
                    width = wrapContentSize(),
                    fontSize = 72,
                    fontWeight = bold,
                    margins = edgeInsets(top = 4),
                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()),
                text(
                    text = vm.conditionLabel,
                    width = wrapContentSize(),
                    fontSize = 18,
                ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()),
                container(
                    orientation = horizontal,
                    width = wrapContentSize(),
                    margins = edgeInsets(top = 6),
                    items = listOf(
                        text(
                            text = vm.todayMaxArrowLabel,
                            width = wrapContentSize(),
                            fontSize = 16,
                        ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()),
                        text(
                            text = vm.todayMinArrowLabel,
                            width = wrapContentSize(),
                            fontSize = 16,
                            margins = edgeInsets(start = 12),
                        ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()),
                    ),
                ),
            ),
        )

        val compactHeader = container(
            orientation = vertical,
            width = matchParentSize(),
            paddings = edgeInsets(start = 20, end = 20, bottom = 8)
                .evaluate(top = (12 + DivVars.STATUS_INSET).divanExpression()),
            background = listOf(solidBackground().evaluate(color = Theme.HEADER_SCRIM.divanExpression())),
            transitionIn = fadeTransition(duration = 250),
            transitionOut = fadeTransition(duration = 250),
            items = listOf(
                text(
                    text = vm.city,
                    width = wrapContentSize(),
                    fontSize = 17,
                    fontWeight = bold,
                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()),
                text(
                    text = vm.compactSummaryLabel,
                    width = wrapContentSize(),
                    fontSize = 15,
                    margins = edgeInsets(top = 2),
                ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()),
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
            id = "hourly_gallery",
            orientation = horizontal,
            width = matchParentSize(),
            height = wrapContentSize(),
            paddings = edgeInsets(start = 16, end = 16),
            itemSpacing = 12,
            items = vm.hourly.map { h ->
                render(
                    MainTemplates.HourCell.template,
                    MainTemplates.HourCell.time bind h.time,
                    MainTemplates.HourCell.emoji bind h.emoji,
                    MainTemplates.HourCell.temp bind h.tempLabel,
                )
            },
        )

        val weeklyBlock = container(
            orientation = vertical,
            width = matchParentSize(),
            margins = edgeInsets(start = 16, top = 16, end = 16),
            background = listOf(solidBackground().evaluate(color = Theme.CARD_BG.divanExpression())),
            border = border(cornerRadius = 16),
            paddings = edgeInsets(start = 8, top = 8, end = 8, bottom = 8),
            items = vm.daily.map { d ->
                val vis: Visibility = if (d.precipLabel != null) visible else gone
                render(
                    MainTemplates.DailyRow.template,
                    MainTemplates.DailyRow.weekday bind d.weekday,
                    MainTemplates.DailyRow.emoji bind d.emoji,
                    MainTemplates.DailyRow.min bind d.minLabel,
                    MainTemplates.DailyRow.barFill bind fixedSize(d.fillPx),
                    MainTemplates.DailyRow.barOffset bind edgeInsets(start = d.offsetPx),
                    MainTemplates.DailyRow.max bind d.maxLabel,
                    MainTemplates.DailyRow.precip bind (d.precipLabel ?: ""),
                    MainTemplates.DailyRow.precipVis bind vis,
                )
            },
        )

        val sunsetArc = custom(
            id = "sun_phase",
            customType = "sun_phase",
            customProps = mapOf(
                "sunrise" to vm.sunrise,
                "sunset" to vm.sunset,
                "track_color" to Colors.GRAY_LIGHT,
            ),
            width = matchParentSize(),
            height = fixedSize(120),
        )
        val sunsetCard = detailCard(
            icon = "",
            title = vm.sunsetTitle,
            bigValue = vm.sunset,
            body = sunsetArc,
            subtitle = vm.sunsetSubtitle,
            margins = edgeInsets(start = 16, top = 16, end = 16),
        )

        // Two independent vertical columns (masonry) instead of a grid: each card keeps its own
        // natural height, so cards with a scale/subtitle don't force empty space onto their row
        // neighbours (a grid equalises row heights → voids). Cards are split across the columns
        // to keep the two roughly balanced in height.
        val leftColumn = container(
            orientation = vertical,
            width = matchParentSize(weight = 1.0),
            items = listOf(
                detailCard(
                    icon = "🔆",
                    title = vm.uvTitle,
                    bigValue = vm.uvIndexLabel,
                    secondLine = vm.uvBandLabel,
                    body = markerScale(vm.uvFraction, UV_SCALE_HEX),
                ),
                detailCard(
                    icon = "🌧️",
                    title = vm.precipTitle,
                    bigValue = vm.todayPrecipLabel,
                    subtitle = vm.precipSubtitle,
                ),
                detailCard(
                    icon = "💧",
                    title = vm.humidityTitle,
                    bigValue = vm.humidityLabel,
                ),
                detailCard(
                    icon = "💨",
                    title = vm.windTitle,
                    bigValue = vm.windLabel,
                ),
            ),
        )
        val rightColumn = container(
            orientation = vertical,
            width = matchParentSize(weight = 1.0),
            items = listOf(
                detailCard(
                    icon = "🌡️",
                    title = vm.feelsTitle,
                    bigValue = vm.feelsLabel,
                    subtitle = vm.feelsSubtitle,
                ),
                detailCard(
                    icon = "👁️",
                    title = vm.visTitle,
                    bigValue = vm.visLabel,
                    subtitle = vm.visSubtitle,
                ),
                detailCard(
                    icon = "🧭",
                    title = vm.pressureTitle,
                    bigValue = vm.pressureLabel,
                    body = markerScale(vm.pressureFraction, PRESS_SCALE_HEX),
                ),
            ),
        )
        val detailsGrid = container(
            orientation = horizontal,
            width = matchParentSize(),
            margins = edgeInsets(start = 16, top = 16, end = 16),
            items = listOf(leftColumn, rightColumn),
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
            extensions = listOf(
                extension(
                    id = "scroll_state",
                    params = mapOf("orientation" to "vertical")
                )
            ),
            width = matchParentSize(),
            height = matchParentSize(),
            // Constant top padding = full-header height. Content always starts under the FULL
            // header at the top (no overlap). When collapsed the user is already scrolled past
            // this reserved band (it has scrolled off-screen), so content sits under the compact
            // header with no gap. The extension expands the header whenever the list is back at
            // the top (canScrollVertically(-1)==false), so the "collapsed + at-top" gap state is
            // never reachable. A reactive top padding was tried but re-layouts unreliably in the
            // gallery (overlap on expand-at-top), so a constant is used.
            paddings = edgeInsets().evaluate(
                // Reserve the full-header height normally; in compact mode the header is forced
                // compact, so reserve only the compact height (no gap). `compact` changes only on
                // a settings toggle (never during scroll), so this reactive padding can't jitter.
                top = (DivVars.COMPACT.ifElse(
                    HEADER_COMPACT_DP,
                    HEADER_EXPANDED_DP
                ) + DivVars.STATUS_INSET).divanExpression(),
                bottom = (96 + DivVars.NAV_INSET).divanExpression(),
            ),
            items = listOf(hourlyGallery, weeklyBlock, sunsetCard, detailsGrid),
        )

        // ---- Layer 2: bottom overlay FAB row ----
        val fabRow = container(
            orientation = vertical,
            width = wrapContentSize(),
            height = wrapContentSize(),
            alignmentHorizontal = right,
            alignmentVertical = bottom,
            paddings = edgeInsets(end = 16).evaluate(bottom = (20 + DivVars.NAV_INSET).divanExpression()),
            items = listOf(
                fab(
                    "⚙",
                    action(
                        logId = "fab_settings",
                        url = url("weather-app://navigate?screen=settings")
                    ),
                    id = "fab_settings"
                ),
                fab(
                    "ℹ",
                    action(logId = "fab_about", url = url("weather-app://navigate?screen=about")),
                    id = "fab_about"
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
                items = listOf(backgroundImage, scrollBody, headerState, fabRow, popupOverlay),
            ),
        )
    }

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
        background = listOf(solidBackground().evaluate(color = Theme.CARD_BG.divanExpression())),
        border = border(cornerRadius = 16),
        items = buildList {
            add(
                text(
                    text = if (icon.isBlank()) title.uppercase() else "$icon  ${title.uppercase()}",
                    width = wrapContentSize(),
                    fontSize = 12,
                ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()),
            )
            if (bigValue.isNotEmpty()) {
                add(
                    text(
                        text = bigValue,
                        width = wrapContentSize(),
                        fontSize = 28,
                        fontWeight = bold,
                        margins = edgeInsets(top = 8),
                    ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()),
                )
            }
            if (secondLine != null) {
                add(
                    text(
                        text = secondLine,
                        width = wrapContentSize(),
                        fontSize = 15,
                        margins = edgeInsets(top = 2),
                    ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()),
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
                    // matchParent width so a long subtitle wraps within the card instead of
                    // being clipped to one ellipsised line.
                    text(
                        text = subtitle,
                        width = matchParentSize(),
                        fontSize = 13,
                        margins = edgeInsets(top = 8),
                    ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()),
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
                    background = listOf(solidBackground(color(Colors.WHITE))),
                ),
            ),
        )
    }

    private fun DivScope.fab(glyph: String, act: Action?, id: String? = null): Div = text(
        id = id,
        text = glyph,
        width = fixedSize(56),
        height = fixedSize(56),
        fontSize = 30,
        textAlignmentHorizontal = center,
        textAlignmentVertical = center,
        margins = edgeInsets(top = 6, bottom = 6),
        // Light theme: dark-gray FAB + light icon. Dark theme: light-gray FAB + dark icon.
        background = listOf(solidBackground().evaluate(color = Theme.FAB_BG.divanExpression())),
        border = border(cornerRadius = 28),
        actions = if (act == null) null else listOf(act),
    ).evaluate(textColor = Theme.FAB_ICON.divanExpression())

    private companion object {
        // "Install" widget popup re-show delay after tapping ×. Native set_stored_value TTL
        // (seconds); see visExpr/actCloseDelay above for why this replaces datetime arithmetic.
        const val POPUP_DELAY_LIFETIME_SECONDS = 259_200

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
        const val HEADER_EXPANDED_DP = 210

        const val BG_IMAGE_BASE_URL =
            "https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/background_"
        const val POPUP_IMAGE_URL =
            "https://raw.githubusercontent.com/flattened-duck/divkit_weather_workshop/main/S3/popup_image.png"

        // 28px low-quality JPEG preview of S3/popup_image.png (946 bytes), pinned verbatim.
        const val POPUP_PREVIEW_DATA_URL =
            "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAASABIAAD/4QBMRXhpZgAATU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAHKADAAQAAAABAAAAHAAAAAD/7QA4UGhvdG9zaG9wIDMuMAA4QklNBAQAAAAAAAA4QklNBCUAAAAAABDUHYzZjwCyBOmACZjs+EJ+/8AAEQgAHAAcAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/bAEMAExMTExMTIBMTIC0gICAtPS0tLS09TT09PT09TV1NTU1NTU1dXV1dXV1dXXBwcHBwcIODg4ODk5OTk5OTk5OTk//bAEMBFxgYJSMlQCMjQJloVWiZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmf/dAAQAAv/aAAwDAQACEQMRAD8A2NV1SexmWOJFYMuec+tZ8evXsjbVjT9f8afry7rlD/sf1NZ1mywyhmHSurkXs7panO5vntc2JtU1GFdzxIB+P+Nb8LmWFJDwWUE/iK568uo5Yto5Jrftv+PeL/cX+VcsW3G8lY30vZM//9DT1hN06f7v9TWR5Zrtmjjfl1B+opvkQ/3F/IV0wrJK1jnnRbd7nGeWa7K3/wBRH/uj+VL5EP8AcX8hUoAAwKipUUuhVOm47s//2Q=="

        const val SCALE_W = 120
        const val MARKER_W = 4
        val UV_SCALE_HEX = listOf(Colors.GREEN, Colors.YELLOW, Colors.ORANGE, Colors.RED, Colors.PURPLE)
        val PRESS_SCALE_HEX = listOf(Colors.SKY_BLUE, Colors.GREEN, Colors.ORANGE)
    }
}
