package workshop.renderer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.ConditionCode
import workshop.proto.WeatherDataOuterClass.Current
import workshop.proto.WeatherDataOuterClass.DailyPoint
import workshop.proto.WeatherDataOuterClass.HourlyPoint
import workshop.proto.WeatherDataOuterClass.WeatherData

class WeatherMainAdapterTest {

    private val localizer = Localizer("en")
    private val adapter = WeatherMainAdapter(localizer)

    private fun mockWeatherData(): WeatherData {
        val current = Current.newBuilder()
            .setCity("Testville")
            .setTempC(17)
            .setFeelsC(14)
            .setCondition(ConditionCode.CLOUDY)
            .setUvIndex(4)
            .setHumidity(60)
            .setPressure(1013)
            .setVisibility(10000)
            .setWind(12)
            .setSunrise("04:45")
            .setSunset("21:15")
            .build()

        val hourly = (0 until 24).map { hour ->
            HourlyPoint.newBuilder()
                .setTime(if (hour == 0) "Now" else "%02d:00".format(hour))
                .setTempC(17 - (hour % 6))
                .setCondition(if (hour % 5 == 0) ConditionCode.CLOUDY else ConditionCode.CLEAR)
                .build()
        }

        val daily = (0 until 7).map { i ->
            DailyPoint.newBuilder()
                .setWeekday("Day$i")
                .setTempMin(12 + i)
                .setTempMax(19 + i)
                .setCondition(if (i % 3 == 0) ConditionCode.CLEAR else ConditionCode.CLOUDY)
                .setPrecipProb((i * 10) % 100)
                .build()
        }

        return WeatherData.newBuilder()
            .setCurrent(current)
            .addAllHourly(hourly)
            .addAllDaily(daily)
            .build()
    }

    @Test
    fun `uv and pressure fractions`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals(4.0 / 11.0, vm.uvFraction)
        assertEquals(33.0 / 60.0, vm.pressureFraction)
        assertEquals(0.55, vm.pressureFraction)
    }

    @Test
    fun `uv band label`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals("Moderate", vm.uvBandLabel)
    }

    @Test
    fun `feels subtitle branch - cooler`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals("Feels cooler than it actually is", vm.feelsSubtitle)
    }

    @Test
    fun `feels subtitle branch - warmer`() {
        val data = mockWeatherData().toBuilder()
            .setCurrent(mockWeatherData().current.toBuilder().setFeelsC(20).build())
            .build()
        val vm = adapter.adapt(data)
        assertEquals("Feels warmer than it actually is", vm.feelsSubtitle)
    }

    @Test
    fun `feels subtitle branch - similar`() {
        val data = mockWeatherData().toBuilder()
            .setCurrent(mockWeatherData().current.toBuilder().setFeelsC(17).build())
            .build()
        val vm = adapter.adapt(data)
        assertEquals("Similar to the actual temperature", vm.feelsSubtitle)
    }

    @Test
    fun `vis subtitle branch - good`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals("Good visibility", vm.visSubtitle)
    }

    @Test
    fun `vis subtitle branch - perfect`() {
        val data = mockWeatherData().toBuilder()
            .setCurrent(mockWeatherData().current.toBuilder().setVisibility(20000).build())
            .build()
        val vm = adapter.adapt(data)
        assertEquals("Perfectly clear", vm.visSubtitle)
    }

    @Test
    fun `vis subtitle branch - reduced`() {
        val data = mockWeatherData().toBuilder()
            .setCurrent(mockWeatherData().current.toBuilder().setVisibility(5000).build())
            .build()
        val vm = adapter.adapt(data)
        assertEquals("Reduced visibility", vm.visSubtitle)
    }

    @Test
    fun `bg base name and condition label`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals("cloudy", vm.bgBaseName)
        assertEquals("Cloudy", vm.conditionLabel)
    }

    @Test
    fun `wind vis pressure labels`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals("12 km/h", vm.windLabel)
        assertEquals("10 km", vm.visLabel)
        assertEquals("1013 hPa", vm.pressureLabel)
    }

    @Test
    fun `daily row offsets and fills`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals(7, vm.daily.size)
        val expectedOffsets = listOf(0, 7, 15, 23, 30, 38, 46)
        vm.daily.forEachIndexed { i, row ->
            assertEquals(53, row.fillPx, "fillPx mismatch at index $i")
            assertEquals(expectedOffsets[i], row.offsetPx, "offsetPx mismatch at index $i")
        }
    }

    @Test
    fun `daily row precip labels`() {
        val vm = adapter.adapt(mockWeatherData())
        assertNull(vm.daily[0].precipLabel)
        assertEquals("💧10%", vm.daily[1].precipLabel)
    }

    @Test
    fun `hourly cells`() {
        val vm = adapter.adapt(mockWeatherData())
        assertEquals(HourCellVm("Now", "☁️", "17°"), vm.hourly[0])
        assertEquals(HourCellVm("01:00", "☀️", "16°"), vm.hourly[1])
    }

    @Test
    fun `daily row offset-fill clamp`() {
        val current = Current.newBuilder()
            .setCity("Clampville")
            .setTempC(17)
            .setFeelsC(14)
            .setCondition(ConditionCode.CLOUDY)
            .setUvIndex(4)
            .setHumidity(60)
            .setPressure(1013)
            .setVisibility(10000)
            .setWind(12)
            .setSunrise("04:45")
            .setSunset("21:15")
            .build()

        val daily = listOf(
            DailyPoint.newBuilder().setWeekday("A").setTempMin(0).setTempMax(10).setCondition(ConditionCode.CLEAR).setPrecipProb(0).build(),
            DailyPoint.newBuilder().setWeekday("B").setTempMin(98).setTempMax(99).setCondition(ConditionCode.CLEAR).setPrecipProb(0).build(),
            DailyPoint.newBuilder().setWeekday("C").setTempMin(50).setTempMax(100).setCondition(ConditionCode.CLEAR).setPrecipProb(0).build(),
        )
        val hourly = listOf(
            HourlyPoint.newBuilder().setTime("Now").setTempC(17).setCondition(ConditionCode.CLEAR).build(),
        )
        val data = WeatherData.newBuilder()
            .setCurrent(current)
            .addAllHourly(hourly)
            .addAllDaily(daily)
            .build()

        val vm = adapter.adapt(data)
        val clampedRow = vm.daily[1]
        assertEquals(94, clampedRow.offsetPx)
        assertEquals(6, clampedRow.fillPx)
    }
}
