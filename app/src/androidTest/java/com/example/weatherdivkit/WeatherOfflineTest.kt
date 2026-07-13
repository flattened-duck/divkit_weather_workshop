package com.example.weatherdivkit

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherdivkit.divkit.DocumentLoader
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// app/src/main/assets/document.json is a live snapshot (curl /document?lang=ru) of the default
// city, not a synthetic fixture — the city name is deterministic (CityRegistry.DEFAULT =
// Moscow), but the weather figures are whatever was live when the asset was last regenerated.
// Pin to what's actually in the committed asset; re-sync if it's regenerated.
private const val OFFLINE_CITY_RU = "Москва"
private const val OFFLINE_TEMP_TODAY = "21°"
private const val OFFLINE_COND_TODAY_RU = "Облачно"

@RunWith(AndroidJUnit4::class)
class WeatherOfflineTest {
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() { DocumentLoader.baseUrl = "http://127.0.0.1:1" }

    @After
    fun tearDown() { scenario?.close(); DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL }

    @Test
    fun offline_fallsBackToAssets() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForDisplayed(OFFLINE_CITY_RU)
        dismissWidgetPopupIfPresent()
        waitForDisplayed(OFFLINE_TEMP_TODAY)
        waitForDisplayed(OFFLINE_COND_TODAY_RU)
    }
}
