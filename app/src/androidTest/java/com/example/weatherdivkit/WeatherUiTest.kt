package com.example.weatherdivkit

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherdivkit.divkit.DocumentLoader
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherUiTest {
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: LangDispatcher
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        dispatcher = LangDispatcher(readTestAsset("document_ru.json"), readTestAsset("document_en.json"))
        server = MockWebServer().apply { this.dispatcher = this@WeatherUiTest.dispatcher; start() }
        DocumentLoader.baseUrl = "http://127.0.0.1:${server.port}"
    }

    @After
    fun tearDown() {
        scenario?.close(); server.shutdown()
        DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    @Test
    fun cold_start_ru_showsMainContent() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        dismissWidgetPopupIfPresent()
        waitForDisplayed(TEMP_TODAY, COND_TODAY_RU, NAV_SETTINGS_RU, NAV_ABOUT_RU)
        assertEquals(1, dispatcher.requestCount)
        assertTrue(dispatcher.lastPath().contains("lang=ru"))
    }

    @Test
    fun navigation_settings_and_about() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        dismissWidgetPopupIfPresent()

        clickText(NAV_SETTINGS_RU)
        waitForDisplayed(SET_THEME_LABEL_RU, SET_MODE_LABEL_RU, SET_LANG_LABEL_RU, THEME_SYSTEM_RU, THEME_DARK_RU, THEME_LIGHT_RU)

        clickText(NAV_HOME_RU)
        waitForDisplayed(MAIN_TITLE_RU)

        clickText(NAV_ABOUT_RU)
        waitForDisplayed(ABOUT_VERSION, ABOUT_GITHUB)

        clickText(NAV_BACK_RU)
        waitForDisplayed(MAIN_TITLE_RU)

        assertEquals(1, dispatcher.requestCount)
    }

    @Test
    fun reactive_theme_sameScreen_noRefetch() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        dismissWidgetPopupIfPresent()

        clickText(NAV_SETTINGS_RU)
        waitForDisplayed(THEME_LIGHT_RU)

        clickText(THEME_LIGHT_RU)
        waitForBackground(scenario!!, expectDark = false)

        clickText(THEME_DARK_RU)
        waitForBackground(scenario!!, expectDark = true)

        assertEquals(1, dispatcher.requestCount)
    }

    @Test
    fun compact_hidesCondition_reactive() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        dismissWidgetPopupIfPresent()

        clickText(NAV_SETTINGS_RU)
        waitForDisplayed(COMPACT_ON_RU)
        clickText(COMPACT_ON_RU)

        clickText(NAV_HOME_RU)
        waitForDisplayed(MAIN_TITLE_RU)
        assertNotVisible(COND_TODAY_RU, COND_TOMORROW_RU)
        waitForDisplayed(TEMP_TODAY)

        clickText(NAV_SETTINGS_RU)
        waitForDisplayed(COMPACT_OFF_RU)
        clickText(COMPACT_OFF_RU)

        clickText(NAV_HOME_RU)
        waitForDisplayed(COND_TODAY_RU, COND_TOMORROW_RU)

        assertEquals(1, dispatcher.requestCount)
    }

    @Test
    fun language_switch_en_refetch() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        dismissWidgetPopupIfPresent()

        clickText(NAV_SETTINGS_RU)
        waitForDisplayed(SET_LANG_LABEL_RU)
        clickText(LANG_EN_BTN)

        waitForDisplayed(SET_TITLE_EN, SET_THEME_LABEL_EN, SET_MODE_LABEL_EN, SET_LANG_LABEL_EN)

        assertEquals(2, dispatcher.requestCount)
        assertTrue(dispatcher.lastPath().contains("lang=en"))
    }

    @Test
    fun persistence_dark_compact_en_survivesRecreate() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        dismissWidgetPopupIfPresent()

        clickText(NAV_SETTINGS_RU)
        waitForDisplayed(THEME_DARK_RU)
        clickText(THEME_DARK_RU)
        clickText(COMPACT_ON_RU)
        clickText(LANG_EN_BTN)
        waitForDisplayed(SET_TITLE_EN)

        scenario!!.recreate()

        waitForDisplayed(MAIN_TITLE_EN)
        waitForBackground(scenario!!, expectDark = true)
        assertNotVisible(COND_TODAY_EN, COND_TOMORROW_EN)
        waitForDisplayed(TEMP_TODAY)
        assertNotVisible(POPUP_TITLE_RU)

        assertEquals(3, dispatcher.requestCount)
    }

    @Test
    fun popup_install_dismiss_persistsAcrossRecreate() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        waitForDisplayed(POPUP_TITLE_RU, POPUP_INSTALL_RU, POPUP_CLOSE)

        clickText(POPUP_INSTALL_RU)
        waitUntilGone(POPUP_TITLE_RU)

        scenario!!.recreate()

        waitForDisplayed(MAIN_TITLE_RU)
        assertNotVisible(POPUP_TITLE_RU, POPUP_INSTALL_RU)
    }

    @Test
    fun popup_closeX_persistsAcrossRecreate() {
        launch()
        waitForDisplayed(MAIN_TITLE_RU)
        waitForDisplayed(POPUP_CLOSE)

        clickText(POPUP_CLOSE)
        waitUntilGone(POPUP_TITLE_RU)

        scenario!!.recreate()

        waitForDisplayed(MAIN_TITLE_RU)
        assertNotVisible(POPUP_TITLE_RU)
    }
}
