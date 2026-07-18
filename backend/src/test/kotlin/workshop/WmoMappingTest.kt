package workshop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import workshop.proto.WeatherDataOuterClass.ConditionCode
import workshop.weather.bgBase
import workshop.weather.bgKey
import workshop.weather.hhmm
import workshop.weather.wmoToCondition

class WmoMappingTest {

    @Test
    fun `wmoToCondition maps representative codes per band`() {
        assertEquals(ConditionCode.CLEAR, wmoToCondition(0))
        assertEquals(ConditionCode.CLEAR, wmoToCondition(1))
        assertEquals(ConditionCode.CLOUDY, wmoToCondition(2))
        assertEquals(ConditionCode.CLOUDY, wmoToCondition(3))
        assertEquals(ConditionCode.FOG, wmoToCondition(45))
        assertEquals(ConditionCode.FOG, wmoToCondition(48))
        assertEquals(ConditionCode.RAIN, wmoToCondition(51))
        assertEquals(ConditionCode.RAIN, wmoToCondition(56))
        assertEquals(ConditionCode.RAIN, wmoToCondition(61))
        assertEquals(ConditionCode.RAIN, wmoToCondition(66))
        assertEquals(ConditionCode.RAIN, wmoToCondition(80))
        assertEquals(ConditionCode.RAIN, wmoToCondition(82))
        assertEquals(ConditionCode.SNOW, wmoToCondition(71))
        assertEquals(ConditionCode.SNOW, wmoToCondition(77))
        assertEquals(ConditionCode.SNOW, wmoToCondition(85))
        assertEquals(ConditionCode.THUNDER, wmoToCondition(95))
        assertEquals(ConditionCode.THUNDER, wmoToCondition(96))
        assertEquals(ConditionCode.THUNDER, wmoToCondition(99))
    }

    @Test
    fun `wmoToCondition falls back to FOG for unknown or illegal codes`() {
        assertEquals(ConditionCode.FOG, wmoToCondition(-1))
        assertEquals(ConditionCode.FOG, wmoToCondition(100))
        assertEquals(ConditionCode.FOG, wmoToCondition(7))
    }

    @Test
    fun `bgBase maps all six conditions`() {
        assertEquals("sunny", bgBase(ConditionCode.CLEAR))
        assertEquals("cloudy", bgBase(ConditionCode.CLOUDY))
        assertEquals("rain", bgBase(ConditionCode.RAIN))
        assertEquals("cloudy", bgBase(ConditionCode.SNOW))
        assertEquals("storm", bgBase(ConditionCode.THUNDER))
        assertEquals("fog", bgBase(ConditionCode.FOG))
    }

    @Test
    fun `bgKey pins representative values`() {
        assertEquals("sunny_day", bgKey(ConditionCode.CLEAR, true))
        assertEquals("cloudy_night", bgKey(ConditionCode.SNOW, false))
    }

    @Test
    fun `bgKey is always a legal grammar value for every condition and day-night combo`() {
        val legalRegex = Regex("^(sunny|cloudy|rain|storm|fog)_(day|night)$")
        val conditions = listOf(
            ConditionCode.CLEAR,
            ConditionCode.CLOUDY,
            ConditionCode.RAIN,
            ConditionCode.SNOW,
            ConditionCode.THUNDER,
            ConditionCode.FOG,
        )
        for (condition in conditions) {
            for (isDay in listOf(true, false)) {
                val key = bgKey(condition, isDay)
                assertTrue(
                    legalRegex.matches(key),
                    "bgKey($condition, $isDay) = '$key' is not a legal bg_key"
                )
            }
        }
    }

    @Test
    fun `hhmm formats an ISO local date-time as 24h HH-mm`() {
        assertEquals("04:45", hhmm("2024-06-01T04:45"))
        assertEquals("21:15", hhmm("2024-06-01T21:15:00"))
    }
}
