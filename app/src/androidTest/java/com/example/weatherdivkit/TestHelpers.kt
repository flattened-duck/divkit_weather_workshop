package com.example.weatherdivkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import com.example.weatherdivkit.divkit.DocumentLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import java.net.Proxy
import java.util.Collections

const val REAL_BACKEND = DocumentLoader.DEFAULT_BASE_URL // "http://10.0.2.2:8080"

/** Fails loudly if the backend isn't serving /document — prevents a green run on the stale asset fallback. */
fun requireBackendUp() {
    val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build() // emulator DHCP proxy bypass (see DocumentLoader)
    val req = Request.Builder().url("$REAL_BACKEND/document?lang=ru").build()
    try {
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AssertionError("Backend /document returned HTTP ${r.code} — start the backend on :8080")
        }
    } catch (e: java.io.IOException) {
        throw AssertionError("Backend not reachable at $REAL_BACKEND (emulator host :8080). Start it before connectedDebugAndroidTest. Cause: ${e.message}")
    }
}

/** Fetches a live document body from the real backend for a given lang (used by the hermetic refetch test). */
fun fetchLiveDocument(lang: String): String {
    val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    val req = Request.Builder().url("$REAL_BACKEND/document?lang=$lang").build()
    client.newCall(req).execute().use { r ->
        return r.body?.string() ?: throw AssertionError("Empty /document body (lang=$lang) from real backend")
    }
}

class LangDispatcher(private val ruBody: String, private val enBody: String) : Dispatcher() {
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path ?: ""
        paths.add(path)
        val body = if (path.contains("lang=en")) enBody else ruBody
        return MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody(body)
    }
    val requestCount: Int get() = paths.size
    fun lastPath(): String = synchronized(paths) { paths.lastOrNull() ?: "" }
}

/** If `popup_close` becomes displayed within [timeoutMs], click it and wait for it to go away; else no-op. */
fun dismissPopupIfPresent(timeoutMs: Long = 5_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
        val displayed = try { assertDivDisplayed("popup_close"); true } catch (t: Throwable) { false }
        if (displayed) {
            clickDivId("popup_close")
            waitForDivGone("popup_close")
            return
        }
        SystemClock.sleep(100)
    }
}

fun sampleContainerTopLeft(scenario: ActivityScenario<MainActivity>): Int {
    var color = 0
    scenario.onActivity { act ->
        val v = act.findViewById<View>(com.example.weatherdivkit.R.id.divContainer)
        val w = v.width.coerceAtLeast(1); val h = v.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        color = bmp.getPixel(w / 2, minOf(8, h - 1))
        bmp.recycle()
    }
    return color
}

fun isDarkBackground(c: Int): Boolean = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < 128

fun waitForBackground(scenario: ActivityScenario<MainActivity>, expectDark: Boolean, timeoutMs: Long = 5_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
        if (isDarkBackground(sampleContainerTopLeft(scenario)) == expectDark) return
        SystemClock.sleep(100)
    }
    assertEquals(expectDark, isDarkBackground(sampleContainerTopLeft(scenario)))
}
