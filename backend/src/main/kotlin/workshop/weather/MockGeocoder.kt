package workshop.weather

import workshop.weather.data.CityHit

object MockGeocoder : Geocoder {
    override suspend fun search(query: String, lang: String): List<CityHit> =
        CityRegistry.search(query, lang)
}
