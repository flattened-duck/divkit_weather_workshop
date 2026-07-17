package com.example.weatherdivkit.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory KeyValueStore so AppPreferences can be exercised without Android SharedPreferences. */
private class FakeKeyValueStore : KeyValueStore {
    val backing = HashMap<String, Any>()

    override fun getString(key: String, default: String?): String? =
        backing[key] as? String ?: default

    override fun putString(key: String, value: String) {
        backing[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        backing[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) {
        backing[key] = value
    }
}

class AppPreferencesTest {

    @Test
    fun defaults_onFreshStore() {
        val prefs = AppPreferences(FakeKeyValueStore())

        assertEquals("ru", prefs.lang)
        assertEquals("system", prefs.themeMode)
        assertFalse(prefs.compact)
        assertEquals(Triple(null, null, null), prefs.readCity())
    }

    @Test
    fun roundTrip_setThenGet() {
        val prefs = AppPreferences(FakeKeyValueStore())

        prefs.lang = "en"
        prefs.themeMode = "dark"
        prefs.compact = true
        prefs.saveCity("55.7", "37.6", "Moscow")

        assertEquals("en", prefs.lang)
        assertEquals("dark", prefs.themeMode)
        assertTrue(prefs.compact)
        assertEquals(Triple("55.7", "37.6", "Moscow"), prefs.readCity())
    }

    @Test
    fun setters_mapToExpectedKeys() {
        val store = FakeKeyValueStore()
        val prefs = AppPreferences(store)

        prefs.lang = "en"
        prefs.themeMode = "dark"
        prefs.compact = true
        prefs.saveCity("55.7", "37.6", "Moscow")

        assertEquals(
            mapOf(
                "pref_lang" to "en",
                "pref_theme_mode" to "dark",
                "pref_compact" to true,
                "pref_lat" to "55.7",
                "pref_lon" to "37.6",
                "pref_city_name" to "Moscow",
            ),
            store.backing,
        )
    }
}
