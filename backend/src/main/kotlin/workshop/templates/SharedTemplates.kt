package workshop.templates

import divkit.dsl.Action
import divkit.dsl.Color
import divkit.dsl.Container
import divkit.dsl.Template
import divkit.dsl.Text
import divkit.dsl.Visibility
import divkit.dsl.border
import divkit.dsl.bold
import divkit.dsl.center
import divkit.dsl.color
import divkit.dsl.container
import divkit.dsl.core.expression
import divkit.dsl.core.reference
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.fixedSize
import divkit.dsl.scope.TemplateScope
import divkit.dsl.solidBackground
import divkit.dsl.template
import divkit.dsl.text
import divkit.dsl.textRefs
import divkit.dsl.vertical
import divkit.dsl.wrapContentSize

/**
 * Shared DivKit templates reused across all three screens.
 *
 * Pedagogical note: the client parses these templates once via DivParsingEnvironment.parseTemplates()
 * and reuses them for each screen card — saving bandwidth and parse time.
 */
object SharedTemplates {

    // Theme expression — evaluated at render time via the reactive "theme" div variable
    private const val THEME_IS_DARK = "theme == 'dark'"

    // Compact expression — evaluated at render time via the reactive "compact" div variable
    private const val IS_COMPACT = "compact"

    /**
     * weather_card: vertical card showing day label, temperature, and condition.
     * Template refs: "day_label" (String), "temp_text" (String), "condition_text" (String).
     *
     * Theme-aware: card background and text colors react to the "theme" div variable.
     * Compact-aware: condition text is hidden when "compact" div variable is true.
     */
    val weatherCard: Template<Container> = template("weather_card") {
        val scope: TemplateScope = this
        val dayLabelRef = reference<String>("day_label")
        val tempTextRef = reference<String>("temp_text")
        val conditionTextRef = reference<String>("condition_text")

        // Card background: dark → #FF2C2C2E, light → #FFFFFFFF
        val cardBgColorExpr = "@{$THEME_IS_DARK ? '#FF2C2C2E' : '#FFFFFFFF'}"

        // Day label color: same in both themes (secondary gray)
        val dayLabelColorExpr = "@{$THEME_IS_DARK ? '#FF8E8E93' : '#FF8E8E93'}"

        // Primary text (temperature): dark → white, light → black
        val tempColorExpr = "@{$THEME_IS_DARK ? '#FFFFFFFF' : '#FF000000'}"

        // Condition text: dark → light gray, light → dark gray
        val conditionColorExpr = "@{$THEME_IS_DARK ? '#FFB0B0B5' : '#FF3C3C43'}"

        // Temperature font size: compact → 24, normal → 36
        val tempFontSizeExpr = "@{$IS_COMPACT ? 24 : 36}"

        // Condition text visibility: compact → gone, normal → visible
        val conditionVisibilityExpr = "@{$IS_COMPACT ? 'gone' : 'visible'}"

        container(
            orientation = vertical,
            width = fixedSize(160),
            margins = edgeInsets(start = 8, top = 8, end = 8, bottom = 8),
            paddings = edgeInsets(start = 12, top = 12, end = 12, bottom = 12),
            background = listOf(solidBackground(color("#FFFFFFFF")).evaluate(color = expression<Color>(cardBgColorExpr))),
            border = border(cornerRadius = 12),
            items = listOf(
                // Day label — secondary color, always visible
                with(scope) {
                    text(
                        width = wrapContentSize(),
                        fontSize = 14,
                        textColor = color("#FF8E8E93"),
                    ).evaluate(textColor = expression<Color>(dayLabelColorExpr)) + textRefs(text = dayLabelRef)
                },
                // Temperature — primary text, compact-aware font size
                with(scope) {
                    text(
                        width = wrapContentSize(),
                        fontSize = 36,
                        fontWeight = bold,
                        textColor = color("#FF000000"),
                        margins = edgeInsets(top = 4),
                    ).evaluate(
                        textColor = expression<Color>(tempColorExpr),
                        fontSize = expression<Int>(tempFontSizeExpr),
                    ) + textRefs(text = tempTextRef)
                },
                // Condition text — hidden in compact mode
                with(scope) {
                    text(
                        width = wrapContentSize(),
                        fontSize = 14,
                        textColor = color("#FF3C3C43"),
                        margins = edgeInsets(top = 4),
                    ).evaluate(
                        textColor = expression<Color>(conditionColorExpr),
                        visibility = expression<Visibility>(conditionVisibilityExpr),
                    ) + textRefs(text = conditionTextRef)
                },
            ),
        )
    }

    /**
     * nav_button: a styled button with text and a customizable action.
     * Template refs: "nav_text" (String), "nav_action" (Action).
     */
    val navButton: Template<Text> = template("nav_button") {
        val scope: TemplateScope = this
        val navTextRef = reference<String>("nav_text")
        val navActionRef = reference<Action>("nav_action")

        with(scope) {
            text(
                width = wrapContentSize(),
                fontSize = 16,
                textAlignmentHorizontal = center,
                margins = edgeInsets(start = 6, top = 6, end = 6, bottom = 6),
                paddings = edgeInsets(start = 16, top = 10, end = 16, bottom = 10),
                background = listOf(solidBackground(color("#FF007AFF"))),
                border = border(cornerRadius = 10),
                textColor = color("#FFFFFFFF"),
            ) + textRefs(text = navTextRef, action = navActionRef)
        }
    }
}
