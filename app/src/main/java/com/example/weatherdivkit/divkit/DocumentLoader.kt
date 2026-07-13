package com.example.weatherdivkit.divkit

import android.content.Context
import android.util.Log
import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.DivData
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
     * Fetches the document from the backend at http://10.0.2.2:8080/document?lang=[lang],
     * parses the same envelope shape, and returns a map of screen id → DivData.
     * Returns null on network failure or parse error (use [loadFromAssets] as fallback).
     */
    fun loadFromNetwork(lang: String): Map<String, DivData>? {
        val url = "http://10.0.2.2:8080/document?lang=$lang"
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

    private companion object {
        const val TAG = "DocumentLoader"
    }
}
