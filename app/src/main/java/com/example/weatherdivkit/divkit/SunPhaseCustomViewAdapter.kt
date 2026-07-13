package com.example.weatherdivkit.divkit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.View
import com.yandex.div.core.DivCustomContainerViewAdapter
import com.yandex.div.core.state.DivStatePath
import com.yandex.div.core.view2.Div2View
import com.yandex.div.json.expressions.ExpressionResolver
import com.yandex.div2.DivCustom
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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

/** Custom Canvas view drawing the sunrise-to-sunset semicircle arc with a "now" marker. */
class SunPhaseView(context: Context) : View(context) {

    private var sunriseMin: Int? = null
    private var sunsetMin: Int? = null
    private var nowMin: Int = 0

    private val density = resources.displayMetrics.density
    private val strokeWidthPx = 6 * density
    private val markerRadiusPx = 6 * density
    private val defaultHeightPx = (96 * density).toInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = DEFAULT_TRACK_COLOR
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = DEFAULT_ARC_COLOR
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = DEFAULT_MARKER_COLOR
    }

    private val arcRect = RectF()

    fun setSunTimes(
        sunriseMin: Int?,
        sunsetMin: Int?,
        nowMin: Int,
        arcColor: Int,
        trackColor: Int,
        markerColor: Int,
    ) {
        this.sunriseMin = sunriseMin
        this.sunsetMin = sunsetMin
        this.nowMin = nowMin
        arcPaint.color = arcColor
        trackPaint.color = trackColor
        markerPaint.color = markerColor
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val resolvedHeightSpec = if (heightMode == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(defaultHeightPx, MeasureSpec.EXACTLY)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, resolvedHeightSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pad = markerRadiusPx + strokeWidthPx
        val cx = width / 2f
        val cy = height - pad
        val r = min(cx, cy) - pad
        if (r <= 0f) return

        arcRect.set(cx - r, cy - r, cx + r, cy)
        canvas.drawArc(arcRect, 180f, 180f, false, trackPaint)

        val sunrise = sunriseMin
        val sunset = sunsetMin
        if (sunrise == null || sunset == null || sunset <= sunrise) return

        val f = ((nowMin - sunrise).toFloat() / (sunset - sunrise)).coerceIn(0f, 1f)
        canvas.drawArc(arcRect, 180f, f * 180f, false, arcPaint)

        val mx = cx - r * cos(f * PI).toFloat()
        val my = cy - r * sin(f * PI).toFloat()
        canvas.drawCircle(mx, my, markerRadiusPx, markerPaint)
    }

    companion object {
        const val DEFAULT_ARC_COLOR = 0xFFFFB74D.toInt()
        const val DEFAULT_TRACK_COLOR = 0x66FFFFFF
        const val DEFAULT_MARKER_COLOR = 0xFFFFFFFF.toInt()
    }
}
