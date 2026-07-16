package workshop.weather

import workshop.proto.WeatherDataOuterClass.ConditionCode

/** Single source of truth for WMO weather code → [ConditionCode] → background key mapping. */

private val CLEAR_CODES = setOf(0, 1)
private val CLOUDY_CODES = setOf(2, 3)
private val FOG_CODES = setOf(45, 48)
private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
private val THUNDER_CODES = setOf(95, 96, 99)

fun wmoToCondition(code: Int): ConditionCode = when (code) {
    in CLEAR_CODES -> ConditionCode.CLEAR
    in CLOUDY_CODES -> ConditionCode.CLOUDY
    in FOG_CODES -> ConditionCode.FOG
    in RAIN_CODES -> ConditionCode.RAIN
    in SNOW_CODES -> ConditionCode.SNOW
    in THUNDER_CODES -> ConditionCode.THUNDER
    else -> ConditionCode.FOG
}

fun bgBase(condition: ConditionCode): String = when (condition) {
    ConditionCode.CLEAR -> "sunny"
    ConditionCode.CLOUDY -> "cloudy"
    ConditionCode.RAIN -> "rain"
    ConditionCode.SNOW -> "cloudy"
    ConditionCode.THUNDER -> "storm"
    ConditionCode.FOG -> "fog"
    else -> "fog"
}

fun bgKey(condition: ConditionCode, isDay: Boolean): String =
    "${bgBase(condition)}_${if (isDay) "day" else "night"}"
