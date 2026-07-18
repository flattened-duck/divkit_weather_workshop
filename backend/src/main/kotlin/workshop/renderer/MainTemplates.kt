package workshop.renderer

import divkit.dsl.Container
import divkit.dsl.EdgeInsets
import divkit.dsl.Size
import divkit.dsl.Template
import divkit.dsl.Visibility
import divkit.dsl.bold
import divkit.dsl.border
import divkit.dsl.center
import divkit.dsl.color
import divkit.dsl.container
import divkit.dsl.containerRefs
import divkit.dsl.core.reference
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.expression.divanExpression
import divkit.dsl.fixedSize
import divkit.dsl.horizontal
import divkit.dsl.matchParentSize
import divkit.dsl.overlap
import divkit.dsl.right
import divkit.dsl.solidBackground
import divkit.dsl.template
import divkit.dsl.text
import divkit.dsl.textRefs
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize

/**
 * Process-lifetime singleton templates for the MAIN card's repeated rows (hourly gallery cells,
 * daily forecast rows). Built once via `by lazy`; [WeatherMainRenderer] only binds/renders them
 * per request, matching the Yandex mapi `Templates.kt` convention.
 */
object MainTemplates {

    object HourCell {
        val time = reference<String>("time")
        val emoji = reference<String>("emoji")
        val temp = reference<String>("temp")

        val template: Template<Container> by lazy {
            template(name = "hour_cell") {
                container(
                    orientation = vertical,
                    width = fixedSize(64),
                    paddings = edgeInsets(start = 8, top = 8, end = 8, bottom = 8),
                    background = listOf(solidBackground().evaluate(color = Theme.CARD_BG.divanExpression())),
                    border = border(cornerRadius = 16),
                    contentAlignmentHorizontal = center,
                    items = listOf(
                        text(
                            width = wrapContentSize(),
                            fontSize = 13,
                            textAlignmentHorizontal = center,
                        ).evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()) + textRefs(text = time),
                        text(
                            width = wrapContentSize(),
                            fontSize = 22,
                            margins = edgeInsets(top = 4),
                        ) + textRefs(text = emoji),
                        text(
                            width = wrapContentSize(),
                            fontSize = 16,
                            fontWeight = bold,
                            margins = edgeInsets(top = 4),
                        ).evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()) + textRefs(text = temp),
                    ),
                )
            }
        }
    }

    object DailyRow {
        val weekday = reference<String>("weekday")
        val emoji = reference<String>("emoji")
        val min = reference<String>("min")
        val max = reference<String>("max")
        val barFill = reference<Size>("bar_fill")
        val barOffset = reference<EdgeInsets>("bar_offset")
        val precip = reference<String>("precip")
        val precipVis = reference<Visibility>("precip_vis")

        val template: Template<Container> by lazy {
            template(name = "daily_row") {
                container(
                    orientation = horizontal,
                    width = matchParentSize(),
                    paddings = edgeInsets(top = 10, bottom = 10, start = 8, end = 8),
                    contentAlignmentVertical = center,
                    items = listOf(
                        text(width = fixedSize(44), fontSize = 16)
                            .evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()) + textRefs(text = weekday),
                        text(width = fixedSize(32), fontSize = 18, textAlignmentHorizontal = center)
                                + textRefs(text = emoji),
                        text(width = fixedSize(40), fontSize = 15, textAlignmentHorizontal = right)
                            .evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression()) + textRefs(
                            text = min
                        ),
                        // rangeBar inlined: outer track constant; inner fill's width+margins are refs
                        container(
                            orientation = overlap,
                            width = fixedSize(100),
                            height = fixedSize(6),
                            margins = edgeInsets(start = 8, end = 8),
                            border = border(cornerRadius = 3),
                            background = listOf(solidBackground(color(Colors.TRACK_WHITE))),
                            items = listOf(
                                container(
                                    height = fixedSize(6),
                                    border = border(cornerRadius = 3),
                                    background = listOf(solidBackground(color(Colors.ORANGE))),
                                ) + containerRefs(width = barFill, margins = barOffset),
                            ),
                        ),
                        text(width = fixedSize(40), fontSize = 15, fontWeight = bold)
                            .evaluate(textColor = Theme.PRIMARY_TEXT.divanExpression()) + textRefs(text = max),
                        // precip cell: ALWAYS present; text + visibility are refs (templates can't
                        // conditionally include children; visibility = gone reserves no space)
                        text(width = wrapContentSize(), fontSize = 12, margins = edgeInsets(start = 6))
                            .evaluate(textColor = Theme.SECONDARY_TEXT.divanExpression())
                                + textRefs(text = precip, visibility = precipVis),
                    ),
                )
            }
        }
    }
}
