package com.example.weatherdivkit

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherdivkit.document.DocumentLoader
import com.example.weatherdivkit.divkit.ScrollStateExtensionHandler
import com.example.weatherdivkit.divkit.SunPhaseCustomViewAdapter
import com.example.weatherdivkit.divkit.SunPhaseView
import com.yandex.div.DivDataTag
import com.yandex.div.coil.CoilDivImageLoader
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import com.yandex.div.core.expression.variables.DivVariableController
import com.yandex.div.core.view2.Div2View
import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.data.Variable
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.DivData
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that native registration (Coil image loader, [SunPhaseCustomViewAdapter],
 * [ScrollStateExtensionHandler]) binds an inline DivData without crashing —
 * independent of any live backend from Worktrees A/B.
 */
@RunWith(AndroidJUnit4::class)
class RegistrationSmokeTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        // MainActivity's own document load is irrelevant here; keep it fast/offline.
        DocumentLoader.baseUrl = "http://127.0.0.1:1"
    }

    @After
    fun tearDown() {
        scenario?.close()
        DocumentLoader.baseUrl = DocumentLoader.DEFAULT_BASE_URL
    }

    @Test
    fun customViewAndExtension_bindWithoutCrashing() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        var sunPhaseViewFound = false

        scenario!!.onActivity { activity ->
            val variableController = DivVariableController().apply {
                declare(Variable.StringVariable("header_state", "full"))
            }
            val configuration = DivConfiguration.Builder(CoilDivImageLoader(activity))
                .divVariableController(variableController)
                .divCustomContainerViewAdapter(SunPhaseCustomViewAdapter())
                .extension(ScrollStateExtensionHandler(variableController))
                .visualErrorsEnabled(true)
                .build()

            val divContext = Div2Context(
                baseContext = activity,
                configuration = configuration,
                lifecycleOwner = activity,
            )
            val env = DivParsingEnvironment(ParsingErrorLogger.ASSERT)
            val divData = DivData(env, JSONObject(SMOKE_DIV_JSON))

            val divView = Div2View(divContext).apply {
                setData(divData, DivDataTag("registration_smoke"))
            }

            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root.addView(divView)
            // Force a synchronous measure/layout pass so any view creation deferred
            // to layout (e.g. gallery/RecyclerView children) actually happens now.
            divView.measure(
                View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY),
            )
            divView.layout(0, 0, divView.measuredWidth, divView.measuredHeight)

            sunPhaseViewFound = containsSunPhaseView(divView)
            root.removeView(divView)
        }

        assertTrue("Expected a SunPhaseView in the bound view tree", sunPhaseViewFound)
    }

    private fun containsSunPhaseView(view: View): Boolean {
        if (view is SunPhaseView) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsSunPhaseView(view.getChildAt(i))) return true
            }
        }
        return false
    }

    private companion object {
        val SMOKE_DIV_JSON = """
            {
              "log_id": "registration_smoke",
              "states": [
                {
                  "state_id": 0,
                  "div": {
                    "type": "container",
                    "orientation": "vertical",
                    "height": { "type": "match_parent" },
                    "width": { "type": "match_parent" },
                    "items": [
                      {
                        "type": "gallery",
                        "orientation": "vertical",
                        "extensions": [{ "id": "scroll_state" }],
                        "items": [
                          { "type": "text", "text": "Item 1" },
                          { "type": "text", "text": "Item 2" }
                        ]
                      },
                      {
                        "type": "custom",
                        "custom_type": "sun_phase",
                        "custom_props": {
                          "sunrise": "06:00",
                          "sunset": "20:00",
                          "now": "13:00"
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
