package com.example.weatherdivkit

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherdivkit.document.DocumentLoader
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic request-count coverage that the real-backend suite can't provide (weather data varies
 * run-to-run, so counting requests against the live server isn't reliable). Document bodies are
 * captured LIVE from the real backend at @Before (never a committed/stale fixture) and served by
 * a local MockWebServer, so this suite also verifies the backend is reachable.
 */
@RunWith(AndroidJUnit4::class)
class WeatherRefetchTest {
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: LangDispatcher
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val ru = fetchLiveDocument("ru")
        val en = fetchLiveDocument("en")
        dispatcher = LangDispatcher(ru, en)
        server =
            MockWebServer().apply { this.dispatcher = this@WeatherRefetchTest.dispatcher; start() }
        DocumentLoader.baseUrl = "http://127.0.0.1:${server.port}"
    }

    @After
    fun tearDown() {
        scenario?.close()
        server.shutdown()
        DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForRequestCountAbove(n: Int, timeoutMs: Long = 10_000) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (dispatcher.requestCount > n) return
            SystemClock.sleep(100)
        }
        assertTrue(
            "expected requestCount > $n, was ${dispatcher.requestCount}",
            dispatcher.requestCount > n
        )
    }

    @Test
    fun languageSwitch_refetches() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        clickDivId("fab_settings")
        waitForDivDisplayed("settings_scroll")
        scrollDivIntoView(scenario!!, "settings_scroll", "lang_btn_en")

        val n = dispatcher.requestCount
        clickDivId("lang_btn_en")
        waitForRequestCountAbove(n)

        waitForDivDisplayed("settings_scroll")
        assertTrue(dispatcher.lastPath().contains("lang=en"))
    }

    @Test
    fun themeToggle_noRefetch() {
        launch()
        waitForDivDisplayed("main_scroll")
        val n = dispatcher.requestCount
        dismissPopupIfPresent()

        clickDivId("fab_settings")
        waitForDivDisplayed("settings_scroll")
        scrollDivIntoView(scenario!!, "settings_scroll", "theme_btn_dark")
        clickDivId("theme_btn_dark")
        waitForBackground(scenario!!, expectDark = true)

        assertEquals(n, dispatcher.requestCount)
    }
}
