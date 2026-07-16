package workshop.weather

import workshop.weather.data.CityHit

interface Geocoder {
    suspend fun search(query: String, lang: String): List<CityHit>

    companion object {
        fun create(): Geocoder =
            if (System.getProperty("weather.source") == "mock") MockGeocoder
            else OpenMeteoGeocoder(OpenMeteoClient())
    }
}
