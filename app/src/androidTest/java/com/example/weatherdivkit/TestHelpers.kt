package com.example.weatherdivkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import java.util.Collections

// ---------------------------------------------------------------------------
// PINNED-СТРОКИ — сверены с реальными фикстурами document_ru.json / document_en.json,
// снятыми с живого backend (curl /document?lang=ru|en).
// ---------------------------------------------------------------------------
const val POPUP_CLOSE = "×"
const val POPUP_TITLE_RU = "Поставь виджет погоды на главный экран"
const val POPUP_INSTALL_RU = "Установить"
const val MAIN_TITLE_RU = "Погода"
const val MAIN_TITLE_EN = "Weather"
const val TEMP_TODAY = "17°C"
const val TEMP_TOMORROW = "20°C"
const val COND_TODAY_RU = "Облачно"
const val COND_TODAY_EN = "Cloudy"
const val COND_TOMORROW_RU = "Ясно"
const val COND_TOMORROW_EN = "Clear"
const val NAV_SETTINGS_RU = "Настройки"
const val NAV_ABOUT_RU = "О приложении"
const val NAV_HOME_RU = "Главная"
const val NAV_BACK_RU = "Назад"
const val SET_THEME_LABEL_RU = "Тема"
const val SET_MODE_LABEL_RU = "Режим"
const val SET_LANG_LABEL_RU = "Язык"
const val THEME_SYSTEM_RU = "Системная"
const val THEME_DARK_RU = "Тёмная"
const val THEME_LIGHT_RU = "Светлая"
const val COMPACT_ON_RU = "Компактный"
const val COMPACT_OFF_RU = "Обычный"
const val LANG_EN_BTN = "English"
const val SET_TITLE_EN = "Settings"
const val SET_THEME_LABEL_EN = "Theme"
const val SET_MODE_LABEL_EN = "Mode"
const val SET_LANG_LABEL_EN = "Language"
const val ABOUT_VERSION = "Версия 1.0.0"
const val ABOUT_GITHUB = "GitHub"

fun readTestAsset(name: String): String =
    InstrumentationRegistry.getInstrumentation().context.assets
        .open(name).bufferedReader().use { it.readText() }

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

private fun isDisplayedNow(text: String): Boolean =
    try { onView(allOf(withText(text), isDisplayed())).check(matches(isDisplayed())); true }
    catch (t: Throwable) { false }

fun waitForDisplayed(text: String, timeoutMs: Long = 10_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) { if (isDisplayedNow(text)) return; SystemClock.sleep(100) }
    onView(allOf(withText(text), isDisplayed())).check(matches(isDisplayed()))
}

fun waitForDisplayed(vararg texts: String, timeoutMs: Long = 10_000) {
    for (text in texts) waitForDisplayed(text, timeoutMs)
}

fun clickText(text: String) = onView(allOf(withText(text), isDisplayed())).perform(click())
fun assertNotVisible(text: String) = onView(allOf(withText(text), isDisplayed())).check(doesNotExist())

fun assertNotVisible(vararg texts: String) {
    for (text in texts) assertNotVisible(text)
}

fun waitUntilGone(text: String, timeoutMs: Long = 5_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) { if (!isDisplayedNow(text)) return; SystemClock.sleep(100) }
    assertNotVisible(text)
}

fun dismissWidgetPopupIfPresent() {
    val end = SystemClock.uptimeMillis() + 4_000
    while (SystemClock.uptimeMillis() < end) {
        if (isDisplayedNow(POPUP_CLOSE)) { clickText(POPUP_CLOSE); waitUntilGone(POPUP_CLOSE); return }
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
