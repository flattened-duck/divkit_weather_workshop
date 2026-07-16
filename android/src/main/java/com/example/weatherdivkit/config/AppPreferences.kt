package com.example.weatherdivkit.config

class AppPreferences(private val store: KeyValueStore) {
    var lang: String
        get() = store.getString(PREF_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
        set(value) { store.putString(PREF_LANG, value) }
    var themeMode: String
        get() = store.getString(PREF_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
        set(value) { store.putString(PREF_THEME_MODE, value) }
    var compact: Boolean
        get() = store.getBoolean(PREF_COMPACT, false)
        set(value) { store.putBoolean(PREF_COMPACT, value) }
    /** (lat, lon, name) — each null when unset, matching MainActivity.readCity(). */
    fun readCity(): Triple<String?, String?, String?> =
        Triple(store.getString(PREF_LAT, null), store.getString(PREF_LON, null), store.getString(PREF_CITY_NAME, null))
    fun saveCity(lat: String, lon: String, name: String) {
        store.putString(PREF_LAT, lat); store.putString(PREF_LON, lon); store.putString(PREF_CITY_NAME, name)
    }
    private companion object {
        const val PREF_LANG = "pref_lang";             const val DEFAULT_LANG = "ru"
        const val PREF_THEME_MODE = "pref_theme_mode"; const val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        const val PREF_COMPACT = "pref_compact"
        const val PREF_LAT = "pref_lat"; const val PREF_LON = "pref_lon"; const val PREF_CITY_NAME = "pref_city_name"
    }
}
