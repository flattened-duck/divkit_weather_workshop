package workshop

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import workshop.servant.WeatherServant

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    val servant = WeatherServant()

    routing {
        get("/document") {
            val lang = call.request.queryParameters["lang"]
                ?.takeIf { it in listOf("ru", "en") } ?: "ru"
            val json = servant.handle(lang)
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
