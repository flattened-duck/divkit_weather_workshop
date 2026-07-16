package com.example.weatherdivkit.document

import com.yandex.div2.DivData
import com.yandex.div2.DivPatch

/** Named load policies over [DocumentSource]. All methods block; call off the main thread. */
class DocumentRepository(private val source: DocumentSource) {
    /** Cold-start phase 1: cache for this lang, else bundled skeleton. Never null. */
    fun coldStartLocal(lang: String): Map<Screen, DivData> =
        source.loadFromCache(lang) ?: source.loadFromAssets()

    /** Network-only. Cold-start ph2, PTR, setCity. Null → caller keeps current. */
    fun fetch(lang: String, lat: String?, lon: String?, name: String?): Map<Screen, DivData>? =
        source.loadFromNetwork(lang, lat, lon, name)

    /** Network, else same-lang cache. setLang. Never falls to bundled asset. */
    fun fetchOrCache(lang: String, lat: String?, lon: String?, name: String?): Map<Screen, DivData>? =
        source.loadFromNetwork(lang, lat, lon, name) ?: source.loadFromCache(lang)

    fun citySearchPatch(query: String, lang: String): DivPatch? =
        source.loadCitySearch(query, lang)
}
