package workshop

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import workshop.servant.WeatherServant
import workshop.weather.CityParam
import workshop.weather.CityRegistry

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    val servant = WeatherServant()

    routing {
        get("/document") {
            val lang = call.request.queryParameters["lang"]
                ?.takeIf { it in listOf("ru", "en") } ?: "ru"
            val city = cityFromParams(call.request.queryParameters)
            val json = servant.handle(lang, city)
            call.respondText(
                text = json,
                contentType = ContentType.Application.Json,
            )
        }

        get("/zero") {
            val lang = call.request.queryParameters["lang"]?.takeIf { it in listOf("ru", "en") } ?: "ru"
            call.respondText(text = servant.zero(lang), contentType = ContentType.Application.Json)
        }

        get("/weather-json") {
            val lang = call.request.queryParameters["lang"]
                ?.takeIf { it in listOf("ru", "en") } ?: "ru"
            val city = cityFromParams(call.request.queryParameters)
            val json = servant.weatherJson(lang, city)
            call.respondText(
                text = json,
                contentType = ContentType.Application.Json,
            )
        }

        get("/city-search") {
            val q = call.request.queryParameters["q"] ?: ""
            val lang = call.request.queryParameters["lang"]
                ?.takeIf { it in listOf("ru", "en") } ?: "ru"
            val json = servant.citySearch(q, lang)
            call.respondText(
                text = json,
                contentType = ContentType.Application.Json,
            )
        }

        get("/ping") {
            call.respondText("pong")
        }
    }
}

private fun cityFromParams(params: Parameters): CityParam {
    val lat = params["lat"]?.toDoubleOrNull()
    val lon = params["lon"]?.toDoubleOrNull()
    val name = params["name"]
    return if (lat != null && lon != null) CityParam(lat, lon, name) else CityRegistry.DEFAULT
}
