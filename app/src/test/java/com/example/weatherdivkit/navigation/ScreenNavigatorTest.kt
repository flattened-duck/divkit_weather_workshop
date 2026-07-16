package com.example.weatherdivkit.navigation

import com.example.weatherdivkit.document.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenNavigatorTest {

    private val rendered = mutableListOf<Screen>()
    private var available: Set<Screen> = Screen.entries.toSet()
    private var exitCount = 0

    private val navigator = ScreenNavigator(
        availableScreens = { available },
        render = { rendered.add(it) },
        onExit = { exitCount++ },
    )

    @Test
    fun initialState_isMainWithEmptyBackStackAndNoRender() {
        assertEquals(Screen.MAIN, navigator.currentScreen)
        assertEquals(emptyList<Screen>(), navigator.backStackSnapshot())
        assertEquals(emptyList<Screen>(), rendered)
    }

    @Test
    fun showScreen_onEmptyBackStack_pushesAndRenders() {
        navigator.showScreen(Screen.MAIN)

        assertEquals(listOf(Screen.MAIN), navigator.backStackSnapshot())
        assertEquals(listOf(Screen.MAIN), rendered)
        assertEquals(Screen.MAIN, navigator.currentScreen)
    }

    @Test
    fun showScreen_topDiffers_pushesNewScreen() {
        navigator.showScreen(Screen.MAIN)
        navigator.showScreen(Screen.SETTINGS)

        assertEquals(listOf(Screen.MAIN, Screen.SETTINGS), navigator.backStackSnapshot())
        assertEquals(listOf(Screen.MAIN, Screen.SETTINGS), rendered)
        assertEquals(Screen.SETTINGS, navigator.currentScreen)
    }

    @Test
    fun showScreen_sameAsTop_doesNotPushButRendersAgain() {
        navigator.showScreen(Screen.MAIN)
        navigator.showScreen(Screen.MAIN)

        assertEquals(listOf(Screen.MAIN), navigator.backStackSnapshot())
        assertEquals(listOf(Screen.MAIN, Screen.MAIN), rendered)
    }

    @Test
    fun goBack_popsAndRendersPrevious() {
        navigator.showScreen(Screen.MAIN)
        navigator.showScreen(Screen.SETTINGS)
        rendered.clear()

        navigator.goBack()

        assertEquals(listOf(Screen.MAIN), navigator.backStackSnapshot())
        assertEquals(listOf(Screen.MAIN), rendered)
        assertEquals(Screen.MAIN, navigator.currentScreen)
        assertEquals(0, exitCount)
    }

    @Test
    fun goBack_atRoot_exitsWithoutRendering() {
        navigator.showScreen(Screen.MAIN)
        rendered.clear()

        navigator.goBack()

        assertEquals(1, exitCount)
        assertEquals(emptyList<Screen>(), rendered)
        assertEquals(Screen.MAIN, navigator.currentScreen)
    }

    @Test
    fun renderScreen_unavailable_doesNotRenderOrUpdateCurrent() {
        navigator.showScreen(Screen.MAIN)
        available = setOf(Screen.MAIN)
        rendered.clear()

        navigator.renderScreen(Screen.SETTINGS)

        assertEquals(emptyList<Screen>(), rendered)
        assertEquals(Screen.MAIN, navigator.currentScreen)
    }

    @Test
    fun showScreen_unavailable_isPushedButNotRendered_currentScreenUnchanged() {
        navigator.showScreen(Screen.MAIN)
        available = setOf(Screen.MAIN)
        rendered.clear()

        navigator.showScreen(Screen.SETTINGS)

        assertEquals(listOf(Screen.MAIN, Screen.SETTINGS), navigator.backStackSnapshot())
        assertEquals(emptyList<Screen>(), rendered)
        assertEquals(Screen.MAIN, navigator.currentScreen)
    }

    @Test
    fun duplicateSequence_mainSettingsSettings_pushesOnceButRendersTwice() {
        navigator.showScreen(Screen.MAIN)
        navigator.showScreen(Screen.SETTINGS)
        navigator.showScreen(Screen.SETTINGS)

        assertEquals(listOf(Screen.MAIN, Screen.SETTINGS), navigator.backStackSnapshot())
        assertEquals(listOf(Screen.MAIN, Screen.SETTINGS, Screen.SETTINGS), rendered)
    }

    @Test
    fun renderCurrent_reRendersCurrentScreen() {
        navigator.showScreen(Screen.SETTINGS)
        rendered.clear()

        navigator.renderCurrent()

        assertEquals(listOf(Screen.SETTINGS), rendered)
        assertEquals(Screen.SETTINGS, navigator.currentScreen)
    }
}
