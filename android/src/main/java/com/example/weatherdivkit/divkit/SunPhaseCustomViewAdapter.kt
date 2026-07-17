package com.example.weatherdivkit.divkit

import android.graphics.Color
import android.util.Log
import android.view.View
import com.yandex.div.core.DivCustomContainerViewAdapter
import com.yandex.div.core.state.DivStatePath
import com.yandex.div.core.view2.Div2View
import com.yandex.div.json.expressions.ExpressionResolver
import com.yandex.div2.DivCustom
import java.util.Calendar

/**
 * Registers the `sun_phase` custom div: a sunrise→sunset arc with a "now" marker.
 * `custom_props` are RAW (non-expression) JSON literals — see contract for the authoritative
 * key list (`sunrise`/`sunset` required "HH:mm", optional `now` + colors).
 */
class SunPhaseCustomViewAdapter : DivCustomContainerViewAdapter {

    override fun createView(
        div: DivCustom,
        divView: Div2View,
        expressionResolver: ExpressionResolver,
        path: DivStatePath,
    ): View = SunPhaseView(divView.context)

    override fun bindView(
        view: View,
        div: DivCustom,
        divView: Div2View,
        expressionResolver: ExpressionResolver,
        path: DivStatePath,
    ) {
        val sunPhaseView = view as? SunPhaseView ?: return
        val props = div.customProps

        val sunriseMin = parseHhMm(props?.optString("sunrise"))
        val sunsetMin = parseHhMm(props?.optString("sunset"))
        val nowRaw = props?.optString("now")
        val nowMin = parseHhMm(nowRaw) ?: nowFromDeviceClock()

        val arcColor = parseColorOrNull(props?.optString("arc_color")) ?: SunPhaseView.DEFAULT_ARC_COLOR
        val trackColor = parseColorOrNull(props?.optString("track_color")) ?: SunPhaseView.DEFAULT_TRACK_COLOR
        val markerColor = parseColorOrNull(props?.optString("marker_color")) ?: SunPhaseView.DEFAULT_MARKER_COLOR

        if (sunriseMin == null || sunsetMin == null) {
            Log.w(TAG, "sun_phase: missing/unparseable sunrise or sunset — rendering empty arc")
        }
        sunPhaseView.setSunTimes(sunriseMin, sunsetMin, nowMin, arcColor, trackColor, markerColor)
    }

    override fun isCustomTypeSupported(type: String): Boolean = type == CUSTOM_TYPE

    override fun release(view: View, div: DivCustom) = Unit

    private fun parseHhMm(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun nowFromDeviceClock(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun parseColorOrNull(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return try {
            Color.parseColor(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        const val CUSTOM_TYPE = "sun_phase"
        private const val TAG = "SunPhaseAdapter"
    }
}
