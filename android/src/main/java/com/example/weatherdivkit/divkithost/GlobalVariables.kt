package com.example.weatherdivkit.divkithost

import com.yandex.div.core.expression.variables.DivVariableController
import com.yandex.div.data.Variable

/** Single choke point over the 6 reactive global div-variables + the DivVariableController. */
class GlobalVariables(themeMode: String, effectiveTheme: String, compact: Boolean) {
    private val themeModeVar = Variable.StringVariable(GlobalVarNames.THEME_MODE, themeMode)
    private val themeVar = Variable.StringVariable(GlobalVarNames.THEME, effectiveTheme)
    private val compactVar = Variable.BooleanVariable(GlobalVarNames.COMPACT, compact)
    private val headerStateVar =
        Variable.StringVariable(GlobalVarNames.HEADER_STATE, GlobalVarNames.HeaderState.FULL)
    private val statusInsetVar = Variable.IntegerVariable(GlobalVarNames.STATUS_INSET, 0L)
    private val navInsetVar = Variable.IntegerVariable(GlobalVarNames.NAV_INSET, 0L)

    val controller: DivVariableController = DivVariableController().apply {
        declare(themeModeVar, themeVar, compactVar, headerStateVar, statusInsetVar, navInsetVar)
    }

    fun setThemeMode(mode: String) = themeModeVar.set(mode)

    fun setTheme(effective: String) = themeVar.set(effective)

    fun setCompact(value: Boolean) {
        compactVar.set(value)
        if (value) headerStateVar.set(GlobalVarNames.HeaderState.COLLAPSED)
    }

    fun setInsets(statusDp: Long, navDp: Long) {
        statusInsetVar.set(statusDp)
        navInsetVar.set(navDp)
    }

    fun currentThemeMode(): String = themeModeVar.getValue() as String
}
