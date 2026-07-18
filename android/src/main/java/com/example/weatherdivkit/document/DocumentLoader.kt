package com.example.weatherdivkit.document

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.weatherdivkit.net.HttpClients
import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.DivData
import com.yandex.div2.DivPatch
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

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
class DocumentLoader(private val context: Context) : DocumentSource {

    /**
     * Reads assets/document.json, parses templates once,
     * and returns a map of screen id → DivData.
     */
    override fun loadFromAssets(): Map<Screen, DivData> {
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
    override fun loadFromNetwork(
        lang: String,
        lat: String?,
        lon: String?,
        name: String?,
    ): Map<Screen, DivData>? {
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
            val response = HttpClients.noProxy.newCall(request).execute()
            val body = response.body?.string() ?: run {
                Log.w(TAG, "Empty response body from network")
                return null
            }
            Log.i(TAG, "Loaded document from network (lang=$lang, ${body.length} bytes)")
            val parsed = parseEnvelope(JSONObject(body))
            writeCache(lang, body)
            parsed
        } catch (e: IOException) {
            Log.w(TAG, "Network unavailable, will fall back to assets: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse network document (lang=$lang)", e)
            null
        }
    }

    /**
     * Reads `doc_cache_<lang>.json` (written by a prior successful [loadFromNetwork]) and parses
     * it the same way as the network/asset paths. Returns null if the file is missing or corrupt
     * — a corrupt cache must degrade to the bundled zero asset, never crash.
     */
    override fun loadFromCache(lang: String): Map<Screen, DivData>? {
        val file = cacheFile(lang)
        if (!file.exists()) return null
        return try {
            parseEnvelope(JSONObject(file.readText()))
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt cache for lang=$lang, ignoring", e)
            null
        }
    }

    /**
     * Atomically persists a successfully-parsed network response body so [loadFromCache] can
     * serve it offline later. A write failure must never fail the network path that just
     * succeeded — log and move on.
     */
    private fun writeCache(lang: String, body: String) {
        try {
            val tmp = File(context.filesDir, "doc_cache_$lang.json.tmp")
            tmp.writeText(body)
            tmp.renameTo(cacheFile(lang))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write cache for lang=$lang", e)
        }
    }

    private fun cacheFile(lang: String): File = File(context.filesDir, "doc_cache_$lang.json")

    /**
     * Fetches `/city-search?q=&lang=` and parses the response body as a [DivPatch]
     * (envelope shape `{"changes":[…]}`, optionally with a `templates` object).
     * Must be called off the main thread; returns null on network/parse failure.
     */
    override fun loadCitySearch(query: String, lang: String): DivPatch? {
        val url = Uri.parse("$baseUrl/city-search").buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("lang", lang)
            .build().toString()
        val request = Request.Builder().url(url).build()
        return try {
            val body = HttpClients.noProxy.newCall(request).execute().body?.string() ?: return null
            val json = JSONObject(body)
            val env = DivParsingEnvironment(ParsingErrorLogger.ASSERT)
            json.optJSONObject("templates")?.let { env.parseTemplates(it) }
            DivPatch(env, json)
        } catch (e: Exception) {
            Log.e(TAG, "city-search failed (q='$query')", e)
            null
        }
    }

    private fun parseEnvelope(json: JSONObject): Map<Screen, DivData> {
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
                Screen.fromWireId(key)
                    ?.let { put(it, DivData(env, screensJson.getJSONObject(key))) }
            }
        }
    }

    companion object {
        const val TAG = "DocumentLoader"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
        @Volatile
        var baseUrl: String = DEFAULT_BASE_URL
    }
}
