package com.example.weatherdivkit

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherdivkit.divkit.DocumentLoader
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-backend, id-based UI tests. The app renders whatever the live backend on :8080 serves
 * (DocumentLoader.DEFAULT_BASE_URL) — structure/id assertions only, never live weather values
 * (invariant #6 of tests_contract.md), so the suite stays green as the layout evolves.
 */
@RunWith(AndroidJUnit4::class)
class WeatherUiTest {
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        requireBackendUp()
        DocumentLoader.baseUrl = REAL_BACKEND
    }

    @After
    fun tearDown() {
        scenario?.close()
        DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    @Test
    fun mainScreen_coreStructure_rendersFromLiveServer() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        assertDivDisplayed("header")
        assertDivDisplayed("main_scroll")
    }

    @Test
    fun mainScreen_galleriesAndCustom_render() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        assertDivDisplayed("hourly_gallery")
        scrollDivIntoView(scenario!!, "main_scroll", "sun_phase")
        waitForDivDisplayed("sun_phase")
    }

    @Test
    fun navigation_settings_back() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        clickDivId("fab_settings")
        waitForDivDisplayed("settings_scroll")
        assertDivDisplayed("city_search_input")
        Espresso.pressBack()
        waitForDivDisplayed("main_scroll")

        clickDivId("fab_settings")
        waitForDivDisplayed("settings_scroll")
        scrollDivIntoView(scenario!!, "settings_scroll", "nav_home")
        clickDivId("nav_home")
        waitForDivDisplayed("main_scroll")
    }

    @Test
    fun navigation_about_back() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        clickDivId("fab_about")
        waitForDivDisplayed("about_scroll")
        Espresso.pressBack()
        waitForDivDisplayed("main_scroll")
    }

    @Test
    fun citySearch_populatesResults() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        clickDivId("fab_settings")
        waitForDivDisplayed("city_search_input")

        // (Skip asserting the pre-search empty state: the results container has 0 children and
        // therefore 0 height, which Espresso's isDisplayed() may not consider "displayed" — the
        // contract marks this pre-check optional; the round-trip below is the load-bearing assert.)
        typeIntoDivId("city_search_input", "Лондон")
        clickDivId("city_search_button")
        // Data-robust: any query yields >=1 child — a hit row OR the "not found" row — proving
        // the search round-trip + patch application. Do NOT assert the result text.
        waitForDivChildren("city_search_results", min = 1)
    }

    @Test
    fun themeToggle_changesBackground() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        clickDivId("fab_settings")
        waitForDivDisplayed("settings_scroll")

        scrollDivIntoView(scenario!!, "settings_scroll", "theme_btn_light")
        clickDivId("theme_btn_light")
        waitForBackground(scenario!!, expectDark = false)

        scrollDivIntoView(scenario!!, "settings_scroll", "theme_btn_dark")
        clickDivId("theme_btn_dark")
        waitForBackground(scenario!!, expectDark = true)
    }

    @Test
    fun popup_install_dismiss_persistsAcrossRecreate() {
        launch()
        waitForDivDisplayed("main_scroll")
        waitForDivDisplayed("popup_install")

        clickDivId("popup_install")
        waitForDivGone("popup_close")

        scenario!!.recreate()

        waitForDivDisplayed("main_scroll")
        assertDivNotDisplayed("popup_install")
    }

    @Test
    fun popup_closeX_persistsAcrossRecreate() {
        launch()
        waitForDivDisplayed("main_scroll")
        waitForDivDisplayed("popup_close")

        clickDivId("popup_close")
        waitForDivGone("popup_close")

        scenario!!.recreate()

        waitForDivDisplayed("main_scroll")
        assertDivNotDisplayed("popup_close")
    }

    @Test
    fun compactToggle_smoke() {
        launch()
        waitForDivDisplayed("main_scroll")
        dismissPopupIfPresent()

        clickDivId("fab_settings")
        waitForDivDisplayed("settings_scroll")
        scrollDivIntoView(scenario!!, "settings_scroll", "compact_btn_on")
        clickDivId("compact_btn_on")

        scrollDivIntoView(scenario!!, "settings_scroll", "nav_home")
        clickDivId("nav_home")
        waitForDivDisplayed("main_scroll")
    }
}
