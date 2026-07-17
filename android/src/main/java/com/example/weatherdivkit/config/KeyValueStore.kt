package com.example.weatherdivkit.config

/** Minimal typed key-value seam so AppPreferences is pure-JVM testable (no Android in unit tests). */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}
