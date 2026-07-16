package com.example.weatherdivkit.divkit

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.weatherdivkit.divkithost.GlobalVarNames
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
        val orientation = params?.optString("orientation") ?: DEFAULT_ORIENTATION
        val density = rv.resources.displayMetrics.density
        val collapsePx = (COLLAPSE_DP * density).toInt()

        fun updateCollapsed() {
            val offset = if (orientation == "horizontal") {
                rv.computeHorizontalScrollOffset()
            } else {
                rv.computeVerticalScrollOffset()
            }
            // atTop is padding-immune: false exactly when there is no content scrolled off the top.
            val atTop = if (orientation == "horizontal") {
                !rv.canScrollHorizontally(-1)
            } else {
                !rv.canScrollVertically(-1)
            }
            val forced = (variableController.get(GlobalVarNames.COMPACT) as? Variable.BooleanVariable)
                ?.getValue() as? Boolean ?: false
            // Hysteresis without an offset-based expand threshold: collapse when scrolled past
            // collapsePx (offset is clean while the header is still expanded); once collapsed, stay
            // collapsed until we're back at the very top (canScrollVertically(-1)==false). This
            // avoids the offset corruption caused by the reactive top-padding shift on collapse.
            val prev = lastCollapsed[rv] ?: false
            val collapsed = forced || when {
                atTop -> false
                prev -> true
                else -> offset > collapsePx
            }
            if (lastCollapsed[rv] != collapsed) {
                lastCollapsed[rv] = collapsed
                (variableController.get(GlobalVarNames.HEADER_STATE) as? Variable.StringVariable)
                    ?.set(if (collapsed) GlobalVarNames.HeaderState.COLLAPSED else GlobalVarNames.HeaderState.FULL)
            }
        }

        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateCollapsed()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // onScrolled isn't guaranteed to fire at the exact rest position after a fling,
                // so re-evaluate when scrolling settles — this reliably expands the header once
                // the list comes to rest at the very top.
                if (newState == RecyclerView.SCROLL_STATE_IDLE) updateCollapsed()
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
        // Collapse when scrolled past COLLAPSE_DP; expand only when back at the very top
        // (canScrollVertically(-1)==false), which is immune to the reactive top-padding shift.
        private const val COLLAPSE_DP = 24
        private const val DEFAULT_ORIENTATION = "vertical"
    }
}
