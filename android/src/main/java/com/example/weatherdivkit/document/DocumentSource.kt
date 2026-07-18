package com.example.weatherdivkit.document

import com.yandex.div2.DivData
import com.yandex.div2.DivPatch

interface DocumentSource {
    fun loadFromAssets(): Map<Screen, DivData>
    fun loadFromNetwork(
        lang: String,
        lat: String? = null,
        lon: String? = null,
        name: String? = null
    ): Map<Screen, DivData>?

    fun loadFromCache(lang: String): Map<Screen, DivData>?
    fun loadCitySearch(query: String, lang: String): DivPatch?
}
