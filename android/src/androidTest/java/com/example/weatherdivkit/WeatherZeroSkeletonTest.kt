package com.example.weatherdivkit

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherdivkit.document.DocumentLoader
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the two offline-cache invariants of the offline-cache contract:
 *  - no network AND no cache -> the bundled ZERO SKELETON renders (`zero_skeleton` marker id,
 *    only ever present on [workshop.renderer.WeatherZeroRenderer]'s output, never on the real
 *    [workshop.renderer.WeatherMainRenderer] one).
 *  - a prior successful network fetch leaves a cache that beats the skeleton on a later offline
 *    cold start (MainActivity's phase 1 reads cache before falling back to the bundled asset).
 */
@RunWith(AndroidJUnit4::class)
class WeatherZeroSkeletonTest {
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        clearDocCache()
        DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL
    }

    @After
    fun tearDown() {
        scenario?.close()
        DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL
    }

    private fun clearDocCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.filesDir.listFiles { f -> f.name.startsWith("doc_cache_") }?.forEach { it.delete() }
    }

    @Test
    fun zero_skeleton_whenOfflineNoCache() {
        DocumentLoader.baseUrl =
            "http://127.0.0.1:1" // dead address; cache already cleared in setUp()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForDivDisplayed("main_scroll")

        assertDivDisplayed("zero_skeleton")
        assertDivDisplayed("header")
    }

    @Test
    fun cacheBeatsSkeletonOfflineRestart() {
        requireBackendUp()

        // (1) Online cold start: no cache yet, so phase 1 briefly shows the skeleton, then phase
        // 2's network fetch swaps in real data AND writes doc_cache_<lang>.json as a side effect.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForDivDisplayed("main_scroll")
        waitForDivAbsent("zero_skeleton") // wait for the phase-2 swap to real data (proves cache got written)
        assertDivAbsent("zero_skeleton")
        scenario?.close()
        scenario = null

        // (2) Offline cold start: phase 1 must read the cache written above directly — the
        // skeleton must never appear, not even transiently.
        DocumentLoader.baseUrl = "http://127.0.0.1:1"
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForDivDisplayed("main_scroll")
        assertDivAbsent("zero_skeleton")
    }
}
