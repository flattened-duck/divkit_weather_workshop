package com.example.weatherdivkit

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherdivkit.divkit.DocumentLoader
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        waitForDisplayed(MAIN_TITLE_RU)
        dismissWidgetPopupIfPresent()
        waitForDisplayed(TEMP_TODAY)
        waitForDisplayed(COND_TODAY_RU)
    }
}
