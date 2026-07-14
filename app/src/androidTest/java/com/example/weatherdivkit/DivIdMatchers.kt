package com.example.weatherdivkit

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Description
import org.hamcrest.Matchers.not
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/** Matches the single view whose DivKit div id was applied as the plain view tag
 *  (DivBaseBinder.applyId: `tag = divId`, R-32.57). */
fun withDivId(divId: String): Matcher<View> = object : TypeSafeMatcher<View>() {
    override fun matchesSafely(v: View): Boolean = v.tag == divId
    override fun describeTo(d: Description) { d.appendText("view with DivKit id (view.tag == \"$divId\")") }
}

private fun hasMinChildCount(min: Int): Matcher<View> = object : TypeSafeMatcher<View>() {
    override fun matchesSafely(v: View): Boolean = v is ViewGroup && v.childCount >= min
    override fun describeTo(d: Description) { d.appendText("a ViewGroup with childCount >= $min") }
}

fun assertDivDisplayed(divId: String) {
    onView(withDivId(divId)).check(matches(isDisplayed()))
}

fun assertDivNotDisplayed(divId: String) {
    onView(withDivId(divId)).check(matches(not(isDisplayed())))
}

fun clickDivId(divId: String) {
    onView(withDivId(divId)).perform(click())
}

fun typeIntoDivId(divId: String, text: String) {
    onView(withDivId(divId)).perform(replaceText(text), closeSoftKeyboard())
}

private fun isDivDisplayedNow(divId: String): Boolean =
    try { onView(withDivId(divId)).check(matches(isDisplayed())); true }
    catch (t: Throwable) { false }

fun waitForDivDisplayed(divId: String, timeoutMs: Long = 10_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
        if (isDivDisplayedNow(divId)) return
        SystemClock.sleep(100)
    }
    onView(withDivId(divId)).check(matches(isDisplayed()))
}

fun waitForDivGone(divId: String, timeoutMs: Long = 5_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
        if (!isDivDisplayedNow(divId)) { assertDivNotDisplayed(divId); return }
        SystemClock.sleep(100)
    }
    assertDivNotDisplayed(divId)
}

fun assertDivHasChildren(divId: String, min: Int = 1) {
    onView(withDivId(divId)).check(matches(hasMinChildCount(min)))
}

private fun isDivChildrenNow(divId: String, min: Int): Boolean =
    try { onView(withDivId(divId)).check(matches(hasMinChildCount(min))); true }
    catch (t: Throwable) { false }

fun waitForDivChildren(divId: String, min: Int = 1, timeoutMs: Long = 10_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < end) {
        if (isDivChildrenNow(divId, min)) return
        SystemClock.sleep(100)
    }
    assertDivHasChildren(divId, min)
}

/** DFS for the (single) view carrying `tag == tag` under [root]. */
private fun findViewByTag(root: View, tag: String): View? {
    if (root.tag == tag) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            findViewByTag(root.getChildAt(i), tag)?.let { return it }
        }
    }
    return null
}

private fun isFullyOnScreen(v: View): Boolean {
    val rect = Rect()
    val visible = v.getGlobalVisibleRect(rect)
    return visible && rect.width() >= v.width && rect.height() >= v.height && v.width > 0 && v.height > 0
}

/**
 * Scrolls the RecyclerView-backed DivKit gallery [scrollDivId] until [targetDivId] is fully
 * on-screen, or [maxSteps] is exhausted. No-op if the target is already fully visible.
 * `Espresso.scrollTo()` does not work on DivKit galleries (they are RecyclerViews); manual
 * `scrollBy` + a layout-settle sleep is required instead.
 */
fun scrollDivIntoView(
    scenario: ActivityScenario<MainActivity>,
    scrollDivId: String,
    targetDivId: String,
    maxSteps: Int = 24,
) {
    repeat(maxSteps) {
        var found = false
        scenario.onActivity { activity ->
            val root = activity.findViewById<View>(R.id.divContainer)
            val target = findViewByTag(root, targetDivId)
            found = target != null && isFullyOnScreen(target)
        }
        if (found) return

        scenario.onActivity { activity ->
            val root = activity.findViewById<View>(R.id.divContainer)
            val scrollView = findViewByTag(root, scrollDivId)
            (scrollView as? RecyclerView)?.scrollBy(0, scrollView.height / 2)
        }
        SystemClock.sleep(150)
    }
}
