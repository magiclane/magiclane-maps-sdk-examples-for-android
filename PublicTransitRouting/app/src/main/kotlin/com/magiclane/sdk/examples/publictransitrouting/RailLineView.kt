/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Vertical piece of the "rail" connecting the stations of the route description view:
 * a solid gray line for transit legs, a dotted line for walk legs (as in Magic Earth).
 */
class RailLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.on_background)
    }

    private val stopDotRadius = resources.getDimension(R.dimen.rail_stop_dot_radius)

    var isWalkStyle: Boolean = false
        set(value) {
            field = value
            configurePaint()
            invalidate()
        }

    /** Y centers (px, in this view's coordinates) of the intermediate-stop dots on the line. */
    var dotCenters: List<Float> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    init {
        configurePaint()
    }

    private fun configurePaint() {
        if (isWalkStyle) {
            paint.color = ContextCompat.getColor(context, R.color.walk_dots)
            paint.strokeWidth = resources.getDimension(R.dimen.rail_dot_size)
            // Zero-length dashes with a round cap render as a column of dots.
            paint.pathEffect = DashPathEffect(
                floatArrayOf(0.01f, resources.getDimension(R.dimen.rail_dot_gap)),
                0f,
            )
        } else {
            paint.color = ContextCompat.getColor(context, R.color.rail_line)
            paint.strokeWidth = resources.getDimension(R.dimen.rail_line_width)
            paint.pathEffect = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), paint)
        dotCenters.forEach { centerY ->
            canvas.drawCircle(centerX, centerY, stopDotRadius, dotPaint)
        }
    }
}
