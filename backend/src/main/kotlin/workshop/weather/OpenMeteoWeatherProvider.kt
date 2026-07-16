package workshop.weather

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.Current
import workshop.proto.WeatherDataOuterClass.DailyPoint
import workshop.proto.WeatherDataOuterClass.DayForecast
import workshop.proto.WeatherDataOuterClass.HourlyPoint
import workshop.proto.WeatherDataOuterClass.WeatherData

/** Real weather source: calls Open-Meteo, maps the response onto [WeatherData]. */
class OpenMeteoWeatherProvider(private val client: ForecastClient) : WeatherProvider {

    private companion object {
        private val log = LoggerFactory.getLogger(OpenMeteoWeatherProvider::class.java)
    }

    override suspend fun provide(city: CityParam, localizer: Localizer): WeatherData = try {
        val lat = city.lat
        val lon = city.lon

        val root: JsonNode = client.forecast(lat, lon)

        val currentNode = root.path("current")
        val dailyNode = root.path("daily")
        val hourlyNode = root.path("hourly")

        val currentTime = LocalDateTime.parse(currentNode.path("time").asText())
        val sunrise0Iso = dailyNode.path("sunrise").get(0).asText()
        val sunset0Iso = dailyNode.path("sunset").get(0).asText()
        val sunrise0 = LocalDateTime.parse(sunrise0Iso)
        val sunset0 = LocalDateTime.parse(sunset0Iso)
        val isDay = !currentTime.isBefore(sunrise0) && currentTime.isBefore(sunset0)

        val condition = wmoToCondition(currentNode.path("weather_code").asInt())
        val bgKeyValue = bgKey(condition, isDay)

        val hourlyTimeArray = hourlyNode.path("time")
        val currentTimeText = currentNode.path("time").asText()
        var currentIdx = 0
        for (i in 0 until hourlyTimeArray.size()) {
            if (hourlyTimeArray.get(i).asText() == currentTimeText) {
                currentIdx = i
                break
            }
        }

        val cityName = city.name ?: localizer.getOrDefault("city.default", "Москва")

        val current = Current.newBuilder()
            .setCity(cityName)
            .setTempC(round(currentNode.path("temperature_2m").asDouble()))
            .setFeelsC(round(currentNode.path("apparent_temperature").asDouble()))
            .setCondition(condition)
            .setUvIndex(round(dailyNode.path("uv_index_max").get(0).asDouble()))
            .setHumidity(currentNode.path("relative_humidity_2m").asInt())
            .setPressure(round(currentNode.path("surface_pressure").asDouble()))
            .setVisibility(round(hourlyNode.path("visibility").get(currentIdx).asDouble()))
            .setWind(round(currentNode.path("wind_speed_10m").asDouble()))
            .setSunrise(hhmm(sunrise0Iso))
            .setSunset(hhmm(sunset0Iso))
            .setBgKey(bgKeyValue)
            .build()

        val hourlyTemps = hourlyNode.path("temperature_2m")
        val hourlyCodes = hourlyNode.path("weather_code")
        val hourlySize = hourlyTimeArray.size()
        val hourlyCount = minOf(24, hourlySize - currentIdx)
        val hourly = (0 until hourlyCount).map { k ->
            val i = currentIdx + k
            HourlyPoint.newBuilder()
                .setTime(if (k == 0) localizer.getOrDefault("hourly.now", "now") else hhmm(hourlyTimeArray.get(i).asText()))
                .setTempC(round(hourlyTemps.get(i).asDouble()))
                .setCondition(wmoToCondition(hourlyCodes.get(i).asInt()))
                .build()
        }

        val dailyTimes = dailyNode.path("time")
        val dailyCodes = dailyNode.path("weather_code")
        val dailyMax = dailyNode.path("temperature_2m_max")
        val dailyMin = dailyNode.path("temperature_2m_min")
        val dailyPrecip = dailyNode.path("precipitation_probability_max")
        val daily = (0 until 7).map { i ->
            DailyPoint.newBuilder()
                .setWeekday(shortWeekday(dailyTimes.get(i).asText(), localizer))
                .setTempMin(round(dailyMin.get(i).asDouble()))
                .setTempMax(round(dailyMax.get(i).asDouble()))
                .setCondition(wmoToCondition(dailyCodes.get(i).asInt()))
                .setPrecipProb(dailyPrecip.get(i)?.asInt(0) ?: 0)
                .build()
        }

        val today = DayForecast.newBuilder()
            .setTempC(current.tempC)
            .setTempFeels(current.feelsC)
            .setCondition(current.condition)
            .setCity(current.city)
            .build()
        val tomorrow = DayForecast.newBuilder()
            .setTempC(round(dailyMax.get(1).asDouble()))
            .setTempFeels(round(dailyMin.get(1).asDouble()))
            .setCondition(wmoToCondition(dailyCodes.get(1).asInt()))
            .setCity(current.city)
            .build()

        WeatherData.newBuilder()
            .setToday(today)
            .setTomorrow(tomorrow)
            .setCurrent(current)
            .addAllHourly(hourly)
            .addAllDaily(daily)
            .build()
    } catch (e: Exception) {
        log.warn(
            "OpenMeteo forecast/mapping failed for city={} lat={} lon={}; falling back to mock",
            city.name, city.lat, city.lon, e,
        )
        MockWeatherProvider.provide(city, localizer)
    }
}
