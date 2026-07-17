package workshop.renderer

import divkit.dsl.Action
import divkit.dsl.Color
import divkit.dsl.Div
import divkit.dsl.Divan
import divkit.dsl.EdgeInsets
import divkit.dsl.Size
import divkit.dsl.action
import divkit.dsl.bold
import divkit.dsl.border
import divkit.dsl.bottom
import divkit.dsl.center
import divkit.dsl.container
import divkit.dsl.core.expression
import divkit.dsl.data
import divkit.dsl.divan
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.expression.divanExpression
import divkit.dsl.extension
import divkit.dsl.fill
import divkit.dsl.fixedSize
import divkit.dsl.gallery
import divkit.dsl.horizontal
import divkit.dsl.image
import divkit.dsl.matchParentSize
import divkit.dsl.overlap
import divkit.dsl.render
import divkit.dsl.right
import divkit.dsl.scope.DivScope
import divkit.dsl.solidBackground
import divkit.dsl.state
import divkit.dsl.stateItem
import divkit.dsl.template
import divkit.dsl.text
import divkit.dsl.top
import divkit.dsl.url
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize
import workshop.l10n.Localizer

/**
 * Renders the zero-state skeleton for the MAIN screen: same outer structure/ids as
 * [WeatherMainRenderer] (so the client parses/collapses/scrolls it identically) but every data
 * leaf is a dash or a shimmer bar instead of real weather data. Self-contained on purpose — does
 * not import from [WeatherMainRenderer] so the prod renderer stays untouched.
 */
class WeatherZeroRenderer(
    private val localizer: Localizer,
) {

    fun render(): Pair<String, Divan> {
        val card = divan {
            buildCard(this)
        }
        return "main" to card
    }

    private fun loc(key: String, fallback: String): String = localizer.getOrDefault(key, fallback)

    private fun buildCard(scope: DivScope) = with(scope) {
        val hourCellSkeletonTemplate = template("hour_cell_skeleton") {
            shimmerBar(width = fixedSize(64), height = fixedSize(90))
        }

        val dailyRowSkeletonTemplate = template("daily_row_skeleton") {
            container(
                orientation = horizontal,
                width = matchParentSize(),
                paddings = edgeInsets(top = 10, bottom = 10, start = 8, end = 8),
                items = listOf(shimmerBar(width = matchParentSize(), height = fixedSize(20))),
            )
        }

        val background = container(
            width = matchParentSize(),
            height = matchParentSize(),
            background = listOf(solidBackground().evaluate(color = Theme.SCREEN_BG.divanExpression<Color>())),
        )

        val fullHeader = container(
            orientation = vertical,
            width = matchParentSize(),
            paddings = edgeInsets(start = 20, end = 20, bottom = 8)
                .evaluate(top = expression<Int>("@{24 + status_inset}")),
            items = listOf(
                text(
                    text = "—",
                    width = wrapContentSize(),
                    fontSize = 20,
                    fontWeight = bold,
                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression<Color>()),
                text(
                    text = "—°",
                    width = wrapContentSize(),
                    fontSize = 72,
                    fontWeight = bold,
                    margins = edgeInsets(top = 4),
                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression<Color>()),
                text(
                    text = "—",
                    width = wrapContentSize(),
                    fontSize = 18,
                ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression<Color>()),
                container(
                    orientation = horizontal,
                    width = wrapContentSize(),
                    margins = edgeInsets(top = 6),
                    items = listOf(
                        text(
                            text = "↑ —°",
                            width = wrapContentSize(),
                            fontSize = 16,
                        ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression<Color>()),
                        text(
                            text = "  ↓ —°",
                            width = wrapContentSize(),
                            fontSize = 16,
                            margins = edgeInsets(start = 12),
                        ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression<Color>()),
                    ),
                ),
            ),
        )

        val compactHeader = container(
            orientation = vertical,
            width = matchParentSize(),
            paddings = edgeInsets(start = 20, end = 20, bottom = 8)
                .evaluate(top = expression<Int>("@{12 + status_inset}")),
            background = listOf(solidBackground().evaluate(color = Theme.HEADER_SCRIM.divanExpression<Color>())),
            items = listOf(
                text(
                    text = "—",
                    width = wrapContentSize(),
                    fontSize = 17,
                    fontWeight = bold,
                ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression<Color>()),
                text(
                    text = "—  |  —",
                    width = wrapContentSize(),
                    fontSize = 15,
                    margins = edgeInsets(top = 2),
                ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression<Color>()),
            ),
        )

        val headerState = state(
            id = "header",
            defaultStateId = "full",
            stateIdVariable = "header_state",
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
            items = List(8) { render(hourCellSkeletonTemplate) },
        )

        val weeklyBlock = container(
            orientation = vertical,
            width = matchParentSize(),
            margins = edgeInsets(start = 16, top = 16, end = 16),
            background = listOf(solidBackground().evaluate(color = Theme.CARD_BG.divanExpression<Color>())),
            border = border(cornerRadius = 16),
            paddings = edgeInsets(start = 8, top = 8, end = 8, bottom = 8),
            items = List(7) { render(dailyRowSkeletonTemplate) },
        )

        val sunsetCard = detailSkeletonCard(
            icon = "",
            title = loc("weather.sunset", "Sunset"),
            margins = edgeInsets(start = 16, top = 16, end = 16),
            bodyHeight = 120,
            subtitle = loc("card.sunrise_at", "Sunrise at") + " —",
        )

        val leftColumn = container(
            orientation = vertical,
            width = matchParentSize(weight = 1.0),
            items = listOf(
                detailSkeletonCard(icon = "🔆", title = loc("weather.uv", "UV index")),
                detailSkeletonCard(icon = "🌧️", title = loc("weather.precipitation", "Precipitation")),
                detailSkeletonCard(icon = "💧", title = loc("weather.humidity", "Humidity")),
                detailSkeletonCard(icon = "💨", title = loc("weather.wind", "Wind")),
            ),
        )
        val rightColumn = container(
            orientation = vertical,
            width = matchParentSize(weight = 1.0),
            items = listOf(
                detailSkeletonCard(icon = "🌡️", title = loc("feels_like", "feels like")),
                detailSkeletonCard(icon = "👁️", title = loc("weather.visibility", "Visibility")),
                detailSkeletonCard(icon = "🧭", title = loc("weather.pressure", "Pressure")),
            ),
        )
        val detailsGrid = container(
            orientation = horizontal,
            width = matchParentSize(),
            margins = edgeInsets(start = 16, top = 16, end = 16),
            items = listOf(leftColumn, rightColumn),
        )

        val scrollBody = gallery(
            id = "main_scroll",
            orientation = vertical,
            extensions = listOf(extension(id = "scroll_state", params = mapOf("orientation" to "vertical"))),
            width = matchParentSize(),
            height = matchParentSize(),
            paddings = edgeInsets().evaluate(
                top = expression<Int>("@{(compact ? 76 : 210) + status_inset}"),
                bottom = expression<Int>("@{96 + nav_inset}"),
            ),
            items = listOf(hourlyGallery, weeklyBlock, sunsetCard, detailsGrid),
        )

        val fabRow = container(
            orientation = vertical,
            width = wrapContentSize(),
            height = wrapContentSize(),
            alignmentHorizontal = right,
            alignmentVertical = bottom,
            paddings = edgeInsets(end = 16).evaluate(bottom = expression<Int>("@{20 + nav_inset}")),
            items = listOf(
                fab("⚙", action(logId = "fab_settings", url = url("weather-app://navigate?screen=settings")), id = "fab_settings"),
                fab("ℹ", action(logId = "fab_about", url = url("weather-app://navigate?screen=about")), id = "fab_about"),
            ),
        )

        data(
            logId = "main_weather",
            div = container(
                orientation = overlap,
                width = matchParentSize(),
                height = matchParentSize(),
                id = "zero_skeleton",
                items = listOf(background, scrollBody, headerState, fabRow),
            ),
        )
    }

    /** A shimmering placeholder bar: an `image` div pointed at an image that never resolves, so
     *  [com.yandex.div.shimmer.DivShimmerExtensionHandler] never sees a loaded image and the
     *  shimmer animation never stops. */
    private fun DivScope.shimmerBar(
        width: Size,
        height: Size,
        cornerRadius: Int = 12,
        margins: EdgeInsets? = null,
    ): Div = image(
        imageUrl = url(SKELETON_IMAGE_URL),
        width = width,
        height = height,
        scale = fill,
        border = border(cornerRadius = cornerRadius),
        margins = margins,
        extensions = listOf(extension(id = "shimmer")),
    )

    private fun DivScope.detailSkeletonCard(
        icon: String,
        title: String,
        margins: EdgeInsets = edgeInsets(start = 6, top = 6, end = 6, bottom = 6),
        bodyHeight: Int? = null,
        subtitle: String? = null,
    ): Div = container(
        orientation = vertical,
        width = matchParentSize(weight = 1.0),
        margins = margins,
        paddings = edgeInsets(start = 14, top = 14, end = 14, bottom = 14),
        background = listOf(solidBackground().evaluate(color = Theme.CARD_BG.divanExpression<Color>())),
        border = border(cornerRadius = 16),
        items = buildList {
            add(
                text(
                    text = if (icon.isBlank()) title.uppercase() else "$icon  ${title.uppercase()}",
                    width = wrapContentSize(),
                    fontSize = 12,
                ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression<Color>()),
            )
            add(shimmerBar(width = fixedSize(90), height = fixedSize(28), margins = edgeInsets(top = 8)))
            if (bodyHeight != null) {
                add(
                    container(
                        width = matchParentSize(),
                        margins = edgeInsets(top = 10),
                        items = listOf(shimmerBar(width = matchParentSize(), height = fixedSize(bodyHeight))),
                    ),
                )
            }
            if (subtitle != null) {
                add(
                    text(
                        text = subtitle,
                        width = matchParentSize(),
                        fontSize = 13,
                        margins = edgeInsets(top = 8),
                    ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression<Color>()),
                )
            }
        },
    )

    private fun DivScope.fab(glyph: String, act: Action?, id: String? = null): Div = text(
        id = id,
        text = glyph,
        width = fixedSize(56),
        height = fixedSize(56),
        fontSize = 30,
        textAlignmentHorizontal = center,
        textAlignmentVertical = center,
        margins = edgeInsets(top = 6, bottom = 6),
        background = listOf(solidBackground().evaluate(color = Theme.FAB_BG.divanExpression<Color>())),
        border = border(cornerRadius = 28),
        actions = if (act == null) null else listOf(act),
    ).evaluate(textColor = Theme.FAB_ICON.divanExpression<Color>())

    private companion object {
        // DivKit treats the `empty://` scheme as "no image": the image is never loaded (so the
        // shimmer extension, which early-returns once isImageLoaded/isImagePreview, keeps
        // animating) AND it does not count as a load error, so no visual-error badge appears
        // (unlike a real DNS-failing URL such as an .invalid TLD).
        const val SKELETON_IMAGE_URL = "empty://"
    }
}
