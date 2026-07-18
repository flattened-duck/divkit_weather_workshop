package com.example.weatherdivkit.document

import com.yandex.div2.DivData
import com.yandex.div2.DivPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Records call order/args; never constructs real DivData/DivPatch (not parseable off-device). */
private class FakeDocumentSource : DocumentSource {
    val calls = mutableListOf<String>()
    var lastNetworkArgs: List<String?>? = null
    var lastCacheLang: String? = null

    var networkResult: Map<Screen, DivData>? = null
    var cacheResult: Map<Screen, DivData>? = null
    val assetsResult: Map<Screen, DivData> = LinkedHashMap()

    override fun loadFromAssets(): Map<Screen, DivData> {
        calls.add("assets")
        return assetsResult
    }

    override fun loadFromNetwork(
        lang: String,
        lat: String?,
        lon: String?,
        name: String?
    ): Map<Screen, DivData>? {
        calls.add("network")
        lastNetworkArgs = listOf(lang, lat, lon, name)
        return networkResult
    }

    override fun loadFromCache(lang: String): Map<Screen, DivData>? {
        calls.add("cache")
        lastCacheLang = lang
        return cacheResult
    }

    override fun loadCitySearch(query: String, lang: String): DivPatch? {
        calls.add("search:$query:$lang")
        return null
    }
}

class DocumentRepositoryTest {

    @Test
    fun coldStartLocal_cacheHit_returnsCache() {
        val fake = FakeDocumentSource()
        val cache = LinkedHashMap<Screen, DivData>()
        fake.cacheResult = cache
        val repo = DocumentRepository(fake)

        assertSame(cache, repo.coldStartLocal("ru"))
        assertEquals(listOf("cache"), fake.calls)
    }

    @Test
    fun coldStartLocal_cacheMiss_fallsBackToAssets() {
        val fake = FakeDocumentSource()
        val repo = DocumentRepository(fake)

        assertSame(fake.assetsResult, repo.coldStartLocal("ru"))
        assertEquals(listOf("cache", "assets"), fake.calls)
    }

    @Test
    fun fetch_returnsNetworkResult() {
        val fake = FakeDocumentSource()
        val network = LinkedHashMap<Screen, DivData>()
        fake.networkResult = network
        val repo = DocumentRepository(fake)

        assertSame(network, repo.fetch("ru", "1", "2", "X"))
        assertEquals(listOf("network"), fake.calls)
    }

    @Test
    fun fetch_nullOnFailure() {
        val fake = FakeDocumentSource()
        val repo = DocumentRepository(fake)

        assertNull(repo.fetch("ru", null, null, null))
        assertEquals(listOf("network"), fake.calls)
    }

    @Test
    fun fetch_forwardsArgsVerbatim() {
        val fake = FakeDocumentSource()
        val repo = DocumentRepository(fake)

        repo.fetch("ru", "1", "2", "X")

        assertEquals(listOf("ru", "1", "2", "X"), fake.lastNetworkArgs)
    }

    @Test
    fun fetchOrCache_networkHit_doesNotTouchCache() {
        val fake = FakeDocumentSource()
        val network = LinkedHashMap<Screen, DivData>()
        fake.networkResult = network
        val repo = DocumentRepository(fake)

        assertSame(network, repo.fetchOrCache("ru", null, null, null))
        assertEquals(listOf("network"), fake.calls)
    }

    @Test
    fun fetchOrCache_networkNull_fallsBackToCache() {
        val fake = FakeDocumentSource()
        val cache = LinkedHashMap<Screen, DivData>()
        fake.cacheResult = cache
        val repo = DocumentRepository(fake)

        assertSame(cache, repo.fetchOrCache("ru", null, null, null))
        assertEquals(listOf("network", "cache"), fake.calls)
        assertEquals("ru", fake.lastCacheLang)
    }

    @Test
    fun fetchOrCache_bothNull_returnsNull() {
        val fake = FakeDocumentSource()
        val repo = DocumentRepository(fake)

        assertNull(repo.fetchOrCache("ru", null, null, null))
        assertEquals(listOf("network", "cache"), fake.calls)
    }

    @Test
    fun citySearchPatch_forwardsArgs() {
        val fake = FakeDocumentSource()
        val repo = DocumentRepository(fake)

        repo.citySearchPatch("moscow", "ru")

        assertEquals(listOf("search:moscow:ru"), fake.calls)
    }
}
