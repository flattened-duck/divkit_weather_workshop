package workshop.servant

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.protobuf.util.JsonFormat
import divkit.dsl.Color
import divkit.dsl.action
import divkit.dsl.border
import divkit.dsl.center
import divkit.dsl.color
import divkit.dsl.container
import divkit.dsl.core.expression
import divkit.dsl.divanPatch
import divkit.dsl.edgeInsets
import divkit.dsl.evaluate
import divkit.dsl.matchParentSize
import divkit.dsl.patch
import divkit.dsl.patchChange
import divkit.dsl.solidBackground
import divkit.dsl.text
import divkit.dsl.url
import divkit.dsl.vertical
import java.net.URLEncoder
import workshop.l10n.Localizer
import workshop.renderer.WeatherAboutRenderer
import workshop.renderer.WeatherMainRenderer
import workshop.renderer.WeatherSettingsRenderer
import workshop.weather.CityParam
import workshop.weather.Geocoder
import workshop.weather.WeatherProvider

/**
 * WeatherServant orchestrates the three screen renderers and assembles the single JSON envelope.
 *
 * Envelope format (matches contract.md §2):
 * ```json
 * {
 *   "templates": { "weather_card": {...}, "nav_button": {...} },
 *   "screens": {
 *     "main":     { "log_id": "main_weather",  "states": [...] },
 *     "settings": { "log_id": "main_settings", "states": [...] },
 *     "about":    { "log_id": "main_about",    "states": [...] }
 *   }
 * }
 * ```
 */
class WeatherServant(
    private val weatherProvider: WeatherProvider = WeatherProvider.create(),
    private val geocoder: Geocoder = Geocoder.create(),
) {

    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    suspend fun handle(lang: String, city: CityParam): String {
        val localizer = localizer(lang)
        val weatherData = weatherProvider.provide(city, localizer)

        val (mainKey, mainDivan) = WeatherMainRenderer(weatherData, localizer).render()
        val (settingsKey, settingsDivan) = WeatherSettingsRenderer(localizer).render()
        val (aboutKey, aboutDivan) = WeatherAboutRenderer(localizer).render()

        // Collect all templates from all three Divan instances
        val allTemplates: Map<String, Any> = buildMap {
            putAll(mainDivan.templates)
            putAll(settingsDivan.templates)
            putAll(aboutDivan.templates)
        }

        // Serialize each card (the "card" field of Divan, which is `Data` = log_id + states)
        val envelope = mapOf(
            "templates" to allTemplates,
            "screens" to mapOf(
                mainKey to mainDivan.card,
                settingsKey to settingsDivan.card,
                aboutKey to aboutDivan.card,
            ),
        )

        return mapper.writeValueAsString(envelope)
    }

    suspend fun weatherJson(lang: String, city: CityParam): String {
        val wd = weatherProvider.provide(city, localizer(lang))
        return JsonFormat.printer().preservingProtoFieldNames().print(wd)
    }

    suspend fun citySearch(query: String, lang: String): String {
        val localizer = localizer(lang)
        val hits = geocoder.search(query, lang)
        val rowSurfaceExpr = "@{theme == 'dark' ? '#FF2C2C2E' : '#FFFFFFFF'}"

        val dp = divanPatch {
            val items = if (hits.isEmpty()) {
                listOf(
                    text(
                        text = localizer.getOrDefault("city.search.empty", "Ничего не найдено"),
                        width = matchParentSize(),
                        fontSize = 15,
                        textColor = color("#FF8E8E93"),
                        textAlignmentHorizontal = center,
                        paddings = edgeInsets(start = 14, top = 12, end = 14, bottom = 12),
                    ),
                )
            } else {
                hits.map { hit ->
                    text(
                        text = hit.name,
                        width = matchParentSize(),
                        fontSize = 16,
                        textColor = color("#FF007AFF"),
                        paddings = edgeInsets(start = 14, top = 12, end = 14, bottom = 12),
                        margins = edgeInsets(top = 6),
                        border = border(cornerRadius = 10),
                        background = listOf(
                            solidBackground(color("#FFFFFFFF")).evaluate(color = expression<Color>(rowSurfaceExpr)),
                        ),
                        action = action(
                            logId = "set_city",
                            url = url(
                                "weather-app://set_city?lat=${hit.lat}&lon=${hit.lon}&name=${urlEncode(hit.name)}",
                            ),
                        ),
                    )
                }
            }
            // A DivPatch change REPLACES the matched div with `items` (Patch.Change.id = "id of the
            // element to be replaced"). Replacing the results container with the bare rows would
            // discard the container AND its id, so re-target/find would break. Wrap the rows in a
            // fresh container carrying the same id so `city_search_results` survives each patch.
            patch(
                changes = listOf(
                    patchChange(
                        id = "city_search_results",
                        items = listOf(
                            container(
                                id = "city_search_results",
                                orientation = vertical,
                                width = matchParentSize(),
                                margins = edgeInsets(top = 8),
                                items = items,
                            ),
                        ),
                    ),
                ),
            )
        }

        return mapper.writeValueAsString(dp.patch)
    }

    private fun localizer(lang: String) = Localizer(lang)

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
