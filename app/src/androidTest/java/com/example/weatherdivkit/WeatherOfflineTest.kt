package com.example.weatherdivkit

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherdivkit.divkit.DocumentLoader
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the offline asset-fallback path (app/src/main/assets/document.json) renders when the
 * network is unreachable. Structure/id assertions only — the committed asset contains the same
 * ids (header, main_scroll, settings_scroll, about_scroll, city_search_results) as the live
 * backend, so this stays green independent of the asset's weather content.
 */
@RunWith(AndroidJUnit4::class)
class WeatherOfflineTest {
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() { DocumentLoader.baseUrl = "http://127.0.0.1:1" } // dead address -> forces loadFromAssets()

    @After
    fun tearDown() { scenario?.close(); DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL }

    @Test
    fun offline_fallsBackToAssets() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        assertDivDisplayed("header")
        assertDivDisplayed("main_scroll")
    }
}
