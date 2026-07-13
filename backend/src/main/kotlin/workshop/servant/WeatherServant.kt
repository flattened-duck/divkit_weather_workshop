package workshop.servant

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import divkit.dsl.Divan
import workshop.l10n.Localizer
import workshop.mock.MockWeatherDataProvider
import workshop.renderer.WeatherAboutRenderer
import workshop.renderer.WeatherMainRenderer
import workshop.renderer.WeatherSettingsRenderer

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
class WeatherServant {

    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    fun handle(lang: String): String {
        val localizer = Localizer(lang)
        val weatherData = MockWeatherDataProvider.provide()

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
}
