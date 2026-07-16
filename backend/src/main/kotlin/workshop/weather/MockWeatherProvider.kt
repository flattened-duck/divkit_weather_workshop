package workshop.weather

import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.ConditionCode
import workshop.weather.data.CityParam
import workshop.proto.WeatherDataOuterClass.Current
import workshop.proto.WeatherDataOuterClass.DailyPoint
import workshop.proto.WeatherDataOuterClass.DayForecast
import workshop.proto.WeatherDataOuterClass.HourlyPoint
import workshop.proto.WeatherDataOuterClass.WeatherData

/** Deterministic, offline weather sample used when `weather.source=mock` (tests, offline runs). */
object MockWeatherProvider : WeatherProvider {

    private val WEEKDAY_KEYS = listOf(
        "weekday.mon", "weekday.tue", "weekday.wed", "weekday.thu",
        "weekday.fri", "weekday.sat", "weekday.sun",
    )

    override suspend fun provide(city: CityParam, localizer: Localizer): WeatherData {
        val cityName = city.name ?: localizer.getOrDefault("city.default", "Москва")

        val current = Current.newBuilder()
            .setCity(cityName)
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
            .setBgKey("cloudy_day")
            .build()

        val hourly = (0 until 24).map { hour ->
            HourlyPoint.newBuilder()
                .setTime(if (hour == 0) localizer.getOrDefault("hourly.now", "now") else "%02d:00".format(hour))
                .setTempC(17 - (hour % 6))
                .setCondition(if (hour % 5 == 0) ConditionCode.CLOUDY else ConditionCode.CLEAR)
                .build()
        }

        val daily = (0 until 7).map { i ->
            DailyPoint.newBuilder()
                .setWeekday(localizer.getOrDefault(WEEKDAY_KEYS[i], WEEKDAY_KEYS[i]))
                .setTempMin(12 + i)
                .setTempMax(19 + i)
                .setCondition(if (i % 3 == 0) ConditionCode.CLEAR else ConditionCode.CLOUDY)
                .setPrecipProb((i * 10) % 100)
                .build()
        }

        val today = DayForecast.newBuilder()
            .setTempC(17)
            .setTempFeels(14)
            .setCondition(ConditionCode.CLOUDY)
            .setCity(cityName)
            .build()
        val tomorrow = DayForecast.newBuilder()
            .setTempC(20)
            .setTempFeels(18)
            .setCondition(ConditionCode.CLEAR)
            .setCity(cityName)
            .build()

        return WeatherData.newBuilder()
            .setToday(today)
            .setTomorrow(tomorrow)
            .setCurrent(current)
            .addAllHourly(hourly)
            .addAllDaily(daily)
            .build()
    }
}
