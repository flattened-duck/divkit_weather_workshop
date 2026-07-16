package workshop.weather

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.ConditionCode

private class FakeForecastClient(private val node: JsonNode) : ForecastClient {
    override suspend fun forecast(lat: Double, lon: Double): JsonNode = node
}

private class ThrowingForecastClient : ForecastClient {
    override suspend fun forecast(lat: Double, lon: Double): JsonNode = throw java.io.IOException("boom")
}

private const val HAPPY_JSON = """
{
  "current": {
    "time": "2024-06-01T12:00",
    "temperature_2m": 17.4,
    "apparent_temperature": 14.6,
    "weather_code": 2,
    "relative_humidity_2m": 60,
    "surface_pressure": 1013.2,
    "wind_speed_10m": 11.7
  },
  "hourly": {
    "time": ["2024-06-01T12:00", "2024-06-01T13:00", "2024-06-01T14:00"],
    "temperature_2m": [17.4, 18.0, 19.0],
    "weather_code": [2, 0, 61],
    "visibility": [10000.0, 9000.0, 8000.0]
  },
  "daily": {
    "time": ["2024-06-01","2024-06-02","2024-06-03","2024-06-04","2024-06-05","2024-06-06","2024-06-07"],
    "weather_code": [2, 0, 3, 61, 71, 95, 45],
    "temperature_2m_max": [20.0, 21.4, 22.0, 23.0, 24.0, 25.0, 26.0],
    "temperature_2m_min": [10.0, 11.6, 12.0, 13.0, 14.0, 15.0, 16.0],
    "precipitation_probability_max": [0, 10, 20, 30, 40, 50, 60],
    "sunrise": ["2024-06-01T04:45","2024-06-02T04:45","2024-06-03T04:45","2024-06-04T04:45","2024-06-05T04:45","2024-06-06T04:45","2024-06-07T04:45"],
    "sunset": ["2024-06-01T21:15","2024-06-02T21:15","2024-06-03T21:15","2024-06-04T21:15","2024-06-05T21:15","2024-06-06T21:15","2024-06-07T21:15"],
    "uv_index_max": [4.3, 4.0, 4.0, 4.0, 4.0, 4.0, 4.0]
  }
}
"""

private const val EMPTY_DAILY_JSON = """
{
  "current": {
    "time": "2024-06-01T12:00",
    "temperature_2m": 17.4,
    "apparent_temperature": 14.6,
    "weather_code": 2,
    "relative_humidity_2m": 60,
    "surface_pressure": 1013.2,
    "wind_speed_10m": 11.7
  },
  "hourly": {
    "time": ["2024-06-01T12:00", "2024-06-01T13:00", "2024-06-01T14:00"],
    "temperature_2m": [17.4, 18.0, 19.0],
    "weather_code": [2, 0, 61],
    "visibility": [10000.0, 9000.0, 8000.0]
  },
  "daily": []
}
"""

private const val MISSING_FIELD_JSON = """
{
  "current": {
    "time": "2024-06-01T12:00",
    "temperature_2m": 17.4,
    "apparent_temperature": 14.6,
    "weather_code": 2,
    "relative_humidity_2m": 60,
    "surface_pressure": 1013.2,
    "wind_speed_10m": 11.7
  },
  "hourly": {
    "time": ["2024-06-01T12:00", "2024-06-01T13:00", "2024-06-01T14:00"],
    "temperature_2m": [17.4, 18.0, 19.0],
    "weather_code": [2, 0, 61],
    "visibility": [10000.0, 9000.0, 8000.0]
  },
  "daily": {
    "time": ["2024-06-01","2024-06-02","2024-06-03","2024-06-04","2024-06-05","2024-06-06","2024-06-07"],
    "weather_code": [2, 0, 3, 61, 71, 95, 45],
    "temperature_2m_min": [10.0, 11.6, 12.0, 13.0, 14.0, 15.0, 16.0],
    "precipitation_probability_max": [0, 10, 20, 30, 40, 50, 60],
    "sunrise": ["2024-06-01T04:45","2024-06-02T04:45","2024-06-03T04:45","2024-06-04T04:45","2024-06-05T04:45","2024-06-06T04:45","2024-06-07T04:45"],
    "sunset": ["2024-06-01T21:15","2024-06-02T21:15","2024-06-03T21:15","2024-06-04T21:15","2024-06-05T21:15","2024-06-06T21:15","2024-06-07T21:15"],
    "uv_index_max": [4.3, 4.0, 4.0, 4.0, 4.0, 4.0, 4.0]
  }
}
"""

class OpenMeteoWeatherProviderTest {

    private val mapper = JsonMapper.builder().build()
    private fun node(json: String): JsonNode = mapper.readTree(json)

    private val loc = Localizer("ru")
    private val city = CityParam(55.0, 37.0, "Testville")

    private fun provide(client: ForecastClient) = runBlocking { OpenMeteoWeatherProvider(client).provide(city, loc) }
    private fun mockResult() = runBlocking { MockWeatherProvider.provide(city, loc) }

    @Test
    fun `happy path maps a well-formed payload`() {
        val result = provide(FakeForecastClient(node(HAPPY_JSON)))

        val current = result.current
        assertEquals(17, current.tempC)
        assertEquals(15, current.feelsC)
        assertEquals(ConditionCode.CLOUDY, current.condition)
        assertEquals(4, current.uvIndex)
        assertEquals(60, current.humidity)
        assertEquals(1013, current.pressure)
        assertEquals(10000, current.visibility)
        assertEquals(12, current.wind)
        assertEquals("04:45", current.sunrise)
        assertEquals("21:15", current.sunset)
        assertEquals("Testville", current.city)
        assertEquals("cloudy_day", current.bgKey)

        assertEquals(3, result.hourlyList.size)
        assertTrue(result.hourlyList[0].time.isNotEmpty())
        assertEquals(17, result.hourlyList[0].tempC)
        assertEquals(ConditionCode.CLOUDY, result.hourlyList[0].condition)
        assertEquals("13:00", result.hourlyList[1].time)
        assertEquals(18, result.hourlyList[1].tempC)
        assertEquals(ConditionCode.CLEAR, result.hourlyList[1].condition)
        assertEquals("14:00", result.hourlyList[2].time)
        assertEquals(19, result.hourlyList[2].tempC)
        assertEquals(ConditionCode.RAIN, result.hourlyList[2].condition)

        assertEquals(7, result.dailyList.size)
        assertTrue(result.dailyList[0].weekday.isNotEmpty())
        assertEquals(10, result.dailyList[0].tempMin)
        assertEquals(20, result.dailyList[0].tempMax)
        assertEquals(ConditionCode.CLOUDY, result.dailyList[0].condition)
        assertEquals(0, result.dailyList[0].precipProb)
        assertEquals(12, result.dailyList[1].tempMin)
        assertEquals(21, result.dailyList[1].tempMax)
        assertEquals(ConditionCode.CLEAR, result.dailyList[1].condition)
        assertEquals(10, result.dailyList[1].precipProb)
        assertEquals(16, result.dailyList[6].tempMin)
        assertEquals(26, result.dailyList[6].tempMax)
        assertEquals(ConditionCode.FOG, result.dailyList[6].condition)
        assertEquals(60, result.dailyList[6].precipProb)

        assertEquals(17, result.today.tempC)
        assertEquals(15, result.today.tempFeels)
        assertEquals(ConditionCode.CLOUDY, result.today.condition)
        assertEquals("Testville", result.today.city)

        assertEquals(21, result.tomorrow.tempC)
        assertEquals(12, result.tomorrow.tempFeels)
        assertEquals(ConditionCode.CLEAR, result.tomorrow.condition)
        assertEquals("Testville", result.tomorrow.city)
    }

    @Test
    fun `empty daily array falls back to mock`() {
        assertEquals(mockResult(), provide(FakeForecastClient(node(EMPTY_DAILY_JSON))))
    }

    @Test
    fun `missing temperature_2m_max field falls back to mock`() {
        assertEquals(mockResult(), provide(FakeForecastClient(node(MISSING_FIELD_JSON))))
    }

    @Test
    fun `network failure falls back to mock`() {
        assertEquals(mockResult(), provide(ThrowingForecastClient()))
    }
}
