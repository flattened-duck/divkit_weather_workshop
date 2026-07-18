package com.example.weatherdivkit.navigation

import com.example.weatherdivkit.document.Screen

class ScreenNavigator(
    private val availableScreens: () -> Set<Screen>,
    private val render: (Screen) -> Unit,
    private val onExit: () -> Unit,
) {
    var currentScreen: Screen = Screen.MAIN
        private set

    private val backStack = mutableListOf<Screen>()

    fun showScreen(screen: Screen) {
        val top = backStack.lastOrNull()
        if (top != null && top != screen) {
            backStack.add(screen)
        } else if (backStack.isEmpty()) {
            backStack.add(screen)
        }
        renderScreen(screen) // may bail below AFTER the push above — preserve order
    }

    fun renderScreen(screen: Screen) {
        if (screen !in availableScreens()) return
        currentScreen = screen
        render(screen)
    }

    fun renderCurrent() = renderScreen(currentScreen)

    fun goBack() {
        if (backStack.size <= 1) {
            onExit(); return
        }
        backStack.removeAt(backStack.lastIndex)
        val previous = backStack.lastOrNull() ?: run { onExit(); return }
        renderScreen(previous)
    }

    internal fun backStackSnapshot(): List<Screen> = backStack.toList()
}
