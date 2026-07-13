package com.example.weatherdivkit.divkit

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.yandex.div.core.expression.variables.DivVariableController
import com.yandex.div.core.extension.DivExtensionHandler
import com.yandex.div.core.view2.Div2View
import com.yandex.div.data.Variable
import com.yandex.div.json.expressions.ExpressionResolver
import com.yandex.div2.DivBase
import java.util.WeakHashMap

/**
 * Drives the global `header_state` String variable ("full"/"collapsed") from the scroll offset
 * of the `gallery`/`pager` div carrying `extensions: [{"id": "scroll_state"}]`.
 *
 * `header_state` is declared once, globally, by MainActivity — this handler only writes it. The
 * backend's `header` state div binds to it via `state_id_variable`, which DivKit's engine
 * observes reactively (unlike a `default_state_id` expression, which is evaluated once).
 */
class ScrollStateExtensionHandler(
    private val variableController: DivVariableController,
) : DivExtensionHandler {

    private val listeners = WeakHashMap<RecyclerView, RecyclerView.OnScrollListener>()
    private val lastCollapsed = WeakHashMap<RecyclerView, Boolean>()

    override fun matches(div: DivBase): Boolean =
        div.extensions?.any { it.id == EXTENSION_ID } == true

    override fun bindView(
        divView: Div2View,
        expressionResolver: ExpressionResolver,
        view: View,
        div: DivBase,
    ) {
        val rv = view as? RecyclerView ?: return

        val params = div.extensions?.first { it.id == EXTENSION_ID }?.params
        val thresholdDp = params?.optInt("threshold_dp", DEFAULT_THRESHOLD_DP) ?: DEFAULT_THRESHOLD_DP
        val orientation = params?.optString("orientation") ?: DEFAULT_ORIENTATION
        val thresholdPx = (thresholdDp * rv.resources.displayMetrics.density).toInt()

        fun updateCollapsed() {
            val offset = if (orientation == "horizontal") {
                rv.computeHorizontalScrollOffset()
            } else {
                rv.computeVerticalScrollOffset()
            }
            val forced = (variableController.get(COMPACT_VAR) as? Variable.BooleanVariable)
                ?.getValue() as? Boolean ?: false
            val collapsed = forced || offset > thresholdPx
            if (lastCollapsed[rv] != collapsed) {
                lastCollapsed[rv] = collapsed
                (variableController.get(HEADER_STATE_VAR) as? Variable.StringVariable)
                    ?.set(if (collapsed) "collapsed" else "full")
            }
        }

        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateCollapsed()
            }
        }
        rv.addOnScrollListener(listener)
        listeners[rv] = listener

        // Push the initial value so state is correct on first bind / after rebind.
        updateCollapsed()
    }

    override fun unbindView(
        divView: Div2View,
        expressionResolver: ExpressionResolver,
        view: View,
        div: DivBase,
    ) {
        val rv = view as? RecyclerView ?: return
        listeners.remove(rv)?.let(rv::removeOnScrollListener)
        lastCollapsed.remove(rv)
    }

    companion object {
        const val EXTENSION_ID = "scroll_state"
        const val HEADER_STATE_VAR = "header_state"
        const val COMPACT_VAR = "compact"
        private const val DEFAULT_THRESHOLD_DP = 48
        private const val DEFAULT_ORIENTATION = "vertical"
    }
}
