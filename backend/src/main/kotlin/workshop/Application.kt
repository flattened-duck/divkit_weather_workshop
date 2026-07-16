package workshop

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import workshop.l10n.LanguageSupport
import workshop.servant.WeatherServant
import workshop.weather.CityRegistry
import workshop.weather.data.CityParam

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    val servant = WeatherServant()

    routing {
        get("/document") {
            val lang = LanguageSupport.normalize(call.request.queryParameters["lang"])
            val city = cityFromParams(call.request.queryParameters)
            val json = servant.handle(lang, city)
            call.respondText(
                text = json,
                contentType = ContentType.Application.Json,
            )
        }

        get("/zero") {
            val lang = LanguageSupport.normalize(call.request.queryParameters["lang"])
            call.respondText(text = servant.zero(lang), contentType = ContentType.Application.Json)
        }

        get("/weather-json") {
            val lang = LanguageSupport.normalize(call.request.queryParameters["lang"])
            val city = cityFromParams(call.request.queryParameters)
            val json = servant.weatherJson(lang, city)
            call.respondText(
                text = json,
                contentType = ContentType.Application.Json,
            )
        }

        get("/city-search") {
            val q = call.request.queryParameters["q"] ?: ""
            val lang = LanguageSupport.normalize(call.request.queryParameters["lang"])
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
