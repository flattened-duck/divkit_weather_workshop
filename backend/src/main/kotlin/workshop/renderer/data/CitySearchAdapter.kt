package workshop.renderer.data

import java.net.URLEncoder
import workshop.l10n.Localizer
import workshop.weather.data.CityHit

/** Maps geocoder hits + localized strings into a [CitySearchViewModel]. Moved verbatim from
 *  [workshop.servant.WeatherServant.citySearch]. */
class CitySearchAdapter(private val localizer: Localizer) {

    fun adapt(hits: List<CityHit>): CitySearchViewModel = CitySearchViewModel(
        rows = hits.map { hit ->
            CityRowVm(
                label = hit.name,
                actionUrl = "weather-app://set_city?lat=${hit.lat}&lon=${hit.lon}&name=${
                    urlEncode(
                        hit.name
                    )
                }",
            )
        },
        emptyLabel = localizer.getOrDefault("city.search.empty", "Ничего не найдено"),
    )

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
