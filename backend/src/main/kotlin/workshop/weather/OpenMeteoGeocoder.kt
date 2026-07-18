package workshop.weather

import workshop.weather.data.CityHit

class OpenMeteoGeocoder(private val client: OpenMeteoClient) : Geocoder {
    override suspend fun search(query: String, lang: String): List<CityHit> {
        if (query.isEmpty()) return emptyList()
        return try {
            val root = client.geocode(query, lang)
            val results = root.path("results")
            if (!results.isArray) return emptyList()
            results.take(8).map { node ->
                val name = node.path("name").asText()
                val admin1 =
                    node.path("admin1").takeIf { !it.isMissingNode && !it.isNull }?.asText()
                val displayName = if (!admin1.isNullOrEmpty()) "$name, $admin1" else name
                CityHit(
                    name = displayName,
                    lat = node.path("latitude").asDouble(),
                    lon = node.path("longitude").asDouble(),
                )
            }
        } catch (t: Throwable) {
            CityRegistry.search(query, lang)
        }
    }
}
