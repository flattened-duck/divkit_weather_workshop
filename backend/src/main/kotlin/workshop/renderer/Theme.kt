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
    val PRIMARY_TEXT: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#FFFFFFFF", "#FF1C1C1E")
    val SECONDARY_TEXT: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#FF9E9EA3", "#FF6E6E73")
    val CARD_BG: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#CC1C1C1E", "#CCFFFFFF")
    val FAB_BG: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#E6D8D8DD", "#E63A3A3C")
    val FAB_ICON: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#FF1C1C1E", "#FFFFFFFF")
    val HEADER_SCRIM: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#99000000", "#99FFFFFF")
    val SCREEN_BG: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#FF1C1C1E", "#FFF2F2F7")
    val SURFACE: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#FF2C2C2E", "#FFFFFFFF")
    val INPUT_FIELD: Expression<String> = (DivVars.THEME equalTo "dark").ifElse("#FF3A3A3C", "#FFF2F2F7")
}
