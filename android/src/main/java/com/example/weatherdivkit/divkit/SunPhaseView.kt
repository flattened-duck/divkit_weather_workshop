package com.example.weatherdivkit.divkit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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

        // The arc is an ellipse (arcRect is 2r wide × r tall → vertical semi-axis is r/2), so the
        // marker must ride that same ellipse, not a circle of radius r — otherwise the dot drifts
        // off the curve (worst at the ends / toward sunset). X already matches; fix Y.
        val ry = r / 2f
        val ellipseCy = cy - ry
        val mx = cx - r * cos(f * PI).toFloat()
        val my = ellipseCy - ry * sin(f * PI).toFloat()
        canvas.drawCircle(mx, my, markerRadiusPx, markerPaint)
    }

    companion object {
        const val DEFAULT_ARC_COLOR = 0xFFFFB74D.toInt()
        const val DEFAULT_TRACK_COLOR = 0x66FFFFFF
        const val DEFAULT_MARKER_COLOR = 0xFFFFFFFF.toInt()
    }
}
