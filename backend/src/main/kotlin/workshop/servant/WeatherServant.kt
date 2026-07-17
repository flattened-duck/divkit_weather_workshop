package workshop.servant

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.protobuf.util.JsonFormat
import workshop.l10n.Localizer
import workshop.renderer.CitySearchRenderer
import workshop.renderer.WeatherAboutRenderer
import workshop.renderer.WeatherMainRenderer
import workshop.renderer.WeatherSettingsRenderer
import workshop.renderer.WeatherZeroRenderer
import workshop.renderer.data.CitySearchAdapter
import workshop.renderer.data.WeatherMainAdapter
import workshop.weather.Geocoder
import workshop.weather.WeatherProvider
import workshop.weather.data.CityParam

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

        val mainVm = WeatherMainAdapter(localizer).adapt(weatherData)
        val (mainKey, mainDivan) = WeatherMainRenderer(mainVm).render()
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

    /**
     * Assembles the zero-state skeleton envelope for the MAIN screen — same shape as [handle]
     * but with no weather fetch and no city (the skeleton carries no real data).
     */
    suspend fun zero(lang: String): String {
        val localizer = localizer(lang)

        val (mainKey, mainDivan) = WeatherZeroRenderer(localizer).render()
        val (settingsKey, settingsDivan) = WeatherSettingsRenderer(localizer).render()
        val (aboutKey, aboutDivan) = WeatherAboutRenderer(localizer).render()

        val allTemplates: Map<String, Any> = buildMap {
            putAll(mainDivan.templates)
            putAll(settingsDivan.templates)
            putAll(aboutDivan.templates)
        }

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
        val vm = CitySearchAdapter(localizer).adapt(hits)
        return mapper.writeValueAsString(CitySearchRenderer(vm).render())
    }

    private fun localizer(lang: String) = Localizer(lang)
}
