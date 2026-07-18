package workshop.renderer

import divkit.dsl.expression.Expression
import divkit.dsl.expression.equalTo
import divkit.dsl.expression.ifElse

/**
 * Reactive theme-color expressions shared by every screen. Each resolves to an ARGB hex string that
 * switches on the `theme` div-variable (dark value first, light value second). Values are frozen —
 * do not change the hex literals or the dark/light order. Each compiles to
 * `@{(theme == 'dark') ? '<dark>' : '<light>'}`.
 */
object Theme {
    val PRIMARY_TEXT: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.WHITE, Colors.NEAR_BLACK)
    val SECONDARY_TEXT: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.GRAY_LIGHT, Colors.GRAY_DARK)
    val CARD_BG: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.CARD_DARK, Colors.CARD_LIGHT)
    val FAB_BG: Expression<String> = (DivVars.THEME equalTo "dark").ifElse(Colors.FAB_DARK, Colors.FAB_LIGHT)
    val FAB_ICON: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.NEAR_BLACK, Colors.WHITE)
    val HEADER_SCRIM: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.SCRIM_BLACK, Colors.SCRIM_WHITE)
    val SCREEN_BG: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.NEAR_BLACK, Colors.LIGHT_SURFACE)
    val SURFACE: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.DARK_SURFACE, Colors.WHITE)
    val INPUT_FIELD: Expression<String> =
        (DivVars.THEME equalTo "dark").ifElse(Colors.CONTROL_INACTIVE, Colors.LIGHT_SURFACE)
}
