package workshop.weather

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url

/** Thin Ktor-client wrapper over the Open-Meteo forecast/geocoding APIs. */
class OpenMeteoClient {

    private val client = HttpClient(CIO) { expectSuccess = true }
    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    suspend fun forecast(lat: Double, lon: Double): JsonNode {
        val url = URLBuilder(FORECAST_URL).apply {
            parameters.append("latitude", lat.toString())
            parameters.append("longitude", lon.toString())
            parameters.append(
                "current",
                "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,surface_pressure,wind_speed_10m,is_day",
            )
            parameters.append("hourly", "temperature_2m,weather_code,uv_index,visibility")
            parameters.append(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max",
            )
            parameters.append("timezone", "auto")
            parameters.append("forecast_days", "7")
        }.build()
        return fetch(url)
    }

    suspend fun geocode(query: String, lang: String): JsonNode {
        val url = URLBuilder(GEOCODE_URL).apply {
            parameters.append("name", query)
            parameters.append("count", "8")
            parameters.append("language", lang)
            parameters.append("format", "json")
        }.build()
        return fetch(url)
    }

    fun close() = client.close()

    private suspend fun fetch(url: Url): JsonNode {
        val text = client.get(url).bodyAsText()
        return mapper.readTree(text)
    }

    private companion object {
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search"
    }
}
