package workshop.renderer

import divkit.dsl.expression.Var
import divkit.dsl.expression.booleanVariable
import divkit.dsl.expression.integerVariable
import divkit.dsl.expression.stringVariable

/**
 * Typed handles to the reactive div-variables the layouts reference by name. These variables are
 * declared/written elsewhere — by the native client (theme, compact, theme_mode, insets) or by a
 * screen's own data() block (popup_dismissed) — never by this object. DivVars only provides typed
 * Var<T> references so expressions can be built with the typed DSL. Names are frozen and must match
 * the client/data variable names byte-for-byte.
 */
object DivVars {
    val THEME: Var<String> = "theme".stringVariable()
    val THEME_MODE: Var<String> = "theme_mode".stringVariable()
    val COMPACT: Var<Boolean> = "compact".booleanVariable()
    val POPUP_DISMISSED: Var<Boolean> = "popup_dismissed".booleanVariable()
    val STATUS_INSET: Var<Long> = "status_inset".integerVariable()
    val NAV_INSET: Var<Long> = "nav_inset".integerVariable()
}
