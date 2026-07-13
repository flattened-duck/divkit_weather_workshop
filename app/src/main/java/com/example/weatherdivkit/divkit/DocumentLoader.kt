package com.example.weatherdivkit.divkit

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.DivData
import com.yandex.div2.DivPatch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy

/**
 * Loads the DivKit document envelope (templates + screens map).
 *
 * The envelope format:
 * {
 *   "templates": { ... },
 *   "screens": {
 *     "main":     { "log_id": "...", "states": [...] },
 *     "settings": { "log_id": "...", "states": [...] },
 *     "about":    { "log_id": "...", "states": [...] }
 *   }
 * }
 *
 * Templates are parsed ONCE into a shared DivParsingEnvironment.
 * Each screen's DivData reuses that same environment.
 */
class DocumentLoader(private val context: Context) {

    private val httpClient by lazy(LazyThreadSafetyMode.PUBLICATION) {
        // The Android emulator's DHCP configures an HTTP proxy at 10.0.2.2:8888.
        // We bypass it with Proxy.NO_PROXY so the app connects directly to the backend.
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    /**
     * Reads assets/document.json, parses templates once,
     * and returns a map of screen id → DivData.
     */
    fun loadFromAssets(): Map<String, DivData> {
        val raw = context.assets.open("document.json")
            .bufferedReader()
            .use { it.readText() }
        return parseEnvelope(JSONObject(raw))
    }

    /**
     * Fetches the document from the backend at http://10.0.2.2:8080/document?lang=[lang]
     * (optionally with city coordinates/name), parses the same envelope shape, and returns
     * a map of screen id → DivData.
     * Returns null on network failure or parse error (use [loadFromAssets] as fallback).
     */
    fun loadFromNetwork(
        lang: String,
        lat: String? = null,
        lon: String? = null,
        name: String? = null,
    ): Map<String, DivData>? {
        val uriBuilder = Uri.parse("$baseUrl/document").buildUpon()
            .appendQueryParameter("lang", lang)
        if (!lat.isNullOrBlank()) uriBuilder.appendQueryParameter("lat", lat)
        if (!lon.isNullOrBlank()) uriBuilder.appendQueryParameter("lon", lon)
        if (!name.isNullOrBlank()) uriBuilder.appendQueryParameter("name", name)
        val url = uriBuilder.build().toString()
        val request = Request.Builder()
            .url(url)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: run {
                Log.w(TAG, "Empty response body from network")
                return null
            }
            Log.i(TAG, "Loaded document from network (lang=$lang, ${body.length} bytes)")
            parseEnvelope(JSONObject(body))
        } catch (e: IOException) {
            Log.w(TAG, "Network unavailable, will fall back to assets: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse network document (lang=$lang)", e)
            null
        }
    }

    /**
     * Fetches `/city-search?q=&lang=` and parses the response body as a [DivPatch]
     * (envelope shape `{"changes":[…]}`, optionally with a `templates` object).
     * Must be called off the main thread; returns null on network/parse failure.
     */
    fun loadCitySearch(query: String, lang: String): DivPatch? {
        val url = Uri.parse("$baseUrl/city-search").buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("lang", lang)
            .build().toString()
        val request = Request.Builder().url(url).build()
        return try {
            val body = httpClient.newCall(request).execute().body?.string() ?: return null
            val json = JSONObject(body)
            val env = DivParsingEnvironment(ParsingErrorLogger.ASSERT)
            json.optJSONObject("templates")?.let { env.parseTemplates(it) }
            DivPatch(env, json)
        } catch (e: Exception) {
            Log.e(TAG, "city-search failed (q='$query')", e)
            null
        }
    }

    private fun parseEnvelope(json: JSONObject): Map<String, DivData> {
        val templatesJson = json.optJSONObject("templates")
        val screensJson = json.getJSONObject("screens")

        val env = DivParsingEnvironment(ParsingErrorLogger.ASSERT)
        if (templatesJson != null) {
            env.parseTemplates(templatesJson)
        }

        return buildMap {
            val keys = screensJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, DivData(env, screensJson.getJSONObject(key)))
            }
        }
    }

    companion object {
        const val TAG = "DocumentLoader"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
        @Volatile var baseUrl: String = DEFAULT_BASE_URL
    }
}
