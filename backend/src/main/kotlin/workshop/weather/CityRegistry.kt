package workshop.weather

import workshop.l10n.LanguageSupport
import workshop.weather.data.CityEntry
import workshop.weather.data.CityHit
import workshop.weather.data.CityParam

object CityRegistry {

    val DEFAULT = CityParam(55.7558, 37.6173, null)

    val CITIES: List<CityEntry> = listOf(
        CityEntry("Москва", "Moscow", 55.7558, 37.6173),
        CityEntry("Санкт-Петербург", "Saint Petersburg", 59.9386, 30.3141),
        CityEntry("Новосибирск", "Novosibirsk", 55.0084, 82.9357),
        CityEntry("Лондон", "London", 51.5074, -0.1278),
        CityEntry("Париж", "Paris", 48.8566, 2.3522),
        CityEntry("Берлин", "Berlin", 52.52, 13.405),
        CityEntry("Нью-Йорк", "New York", 40.7128, -74.006),
        CityEntry("Токио", "Tokyo", 35.6762, 139.6503),
    )

    fun displayName(e: CityEntry, lang: String): String =
        if (LanguageSupport.isEnglish(lang)) e.nameEn else e.nameRu

    fun search(query: String, lang: String): List<CityHit> {
        if (query.isEmpty()) return emptyList()
        return CITIES
            .filter { it.nameRu.contains(query, ignoreCase = true) || it.nameEn.contains(query, ignoreCase = true) }
            .take(8)
            .map { CityHit(displayName(it, lang), it.lat, it.lon) }
    }
}
