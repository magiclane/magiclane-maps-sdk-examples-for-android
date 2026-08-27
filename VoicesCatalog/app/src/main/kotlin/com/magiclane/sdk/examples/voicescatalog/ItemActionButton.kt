/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalog

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

/**
 * The per-item circular action button state machine, a views port of the maps-compose
 * `ItemActionButton`: download when idle, a stop square with a progress ring while
 * downloading, resume with an orange ring when paused, and a green downloaded badge
 * when completed. While [downloadEnabled] is false the download/resume affordances
 * grey out and ignore taps (e.g. during a map update); pause stays available.
 */
class ItemActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onDownload: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null

    private var state = CatalogItemState.Idle
    private var progress = 0
    private var downloadEnabled = true

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_DP * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val ringBounds = RectF()

    private val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
    private val disabledColor = ColorUtils.setAlphaComponent(
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface),
        DISABLED_ALPHA,
    )
    private val pausedColor = ContextCompat.getColor(context, R.color.catalog_paused)
    private val completedColor = ContextCompat.getColor(context, R.color.catalog_completed)

    private val downloadIcon = ContextCompat.getDrawable(context, R.drawable.ml_download_24)?.mutate()
    private val playIcon = ContextCompat.getDrawable(context, R.drawable.ml_play_24)?.mutate()
    private val checkIcon = ContextCompat.getDrawable(context, R.drawable.ml_check_24)?.mutate()

    init {
        setOnClickListener {
            when (state) {
                CatalogItemState.Idle -> if (downloadEnabled) onDownload?.invoke()
                CatalogItemState.Downloading -> onPause?.invoke()
                CatalogItemState.Paused -> if (downloadEnabled) onResume?.invoke()
                CatalogItemState.Completed -> Unit
            }
        }
        contentDescription = context.getString(R.string.download)
    }

    /** Applies the [state]/[progress] to render and whether downloads are allowed. */
    fun bind(state: CatalogItemState, progress: Int, downloadEnabled: Boolean = true) {
        this.state = state
        this.progress = progress.coerceIn(0, PERCENT_MAX)
        this.downloadEnabled = downloadEnabled
        isClickable = state != CatalogItemState.Completed
        contentDescription = context.getString(
            if (state == CatalogItemState.Completed) R.string.downloaded else R.string.download,
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = width.coerceAtMost(height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density

        val accent = when (state) {
            CatalogItemState.Completed -> completedColor
            CatalogItemState.Downloading -> primaryColor
            CatalogItemState.Paused -> if (downloadEnabled) pausedColor else disabledColor
            CatalogItemState.Idle -> if (downloadEnabled) primaryColor else disabledColor
        }

        val showRing = state == CatalogItemState.Downloading || state == CatalogItemState.Paused

        if (showRing) {
            val inset = ringPaint.strokeWidth / 2f
            ringBounds.set(
                cx - size / 2f + inset,
                cy - size / 2f + inset,
                cx + size / 2f - inset,
                cy + size / 2f - inset,
            )
            ringPaint.color = ColorUtils.setAlphaComponent(accent, TRACK_ALPHA)
            canvas.drawOval(ringBounds, ringPaint)
            ringPaint.color = accent
            canvas.drawArc(ringBounds, ARC_START_DEGREES, ARC_FULL_DEGREES * progress / PERCENT_MAX, false, ringPaint)
        }

        // Inner filled circle (shrunk under a progress ring, like the Compose version).
        val innerRadius = (if (showRing) size - RING_GAP_DP * density else size) / 2f
        fillPaint.color = ColorUtils.setAlphaComponent(accent, BACKGROUND_ALPHA)
        canvas.drawCircle(cx, cy, innerRadius, fillPaint)

        when (state) {
            CatalogItemState.Downloading -> {
                // The pause affordance is a small "stop" square, like the platform download UIs.
                val half = STOP_SQUARE_DP / 2f * density
                fillPaint.color = accent
                canvas.drawRoundRect(
                    cx - half,
                    cy - half,
                    cx + half,
                    cy + half,
                    STOP_CORNER_DP * density,
                    STOP_CORNER_DP * density,
                    fillPaint,
                )
            }

            CatalogItemState.Idle -> drawIcon(canvas, downloadIcon, accent, DOWNLOAD_ICON_DP, cx, cy)
            CatalogItemState.Paused -> drawIcon(canvas, playIcon, accent, ICON_DP, cx, cy)
            CatalogItemState.Completed -> drawIcon(canvas, checkIcon, accent, ICON_DP, cx, cy)
        }
    }

    private fun drawIcon(
        canvas: Canvas,
        icon: android.graphics.drawable.Drawable?,
        tint: Int,
        sizeDp: Float,
        cx: Float,
        cy: Float,
    ) {
        icon ?: return
        val half = sizeDp / 2f * resources.displayMetrics.density
        icon.setTint(tint)
        icon.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon.draw(canvas)
    }

    private companion object {
        const val PERCENT_MAX = 100
        const val RING_WIDTH_DP = 3f
        const val RING_GAP_DP = 8f
        const val STOP_SQUARE_DP = 11f
        const val STOP_CORNER_DP = 2f
        const val ICON_DP = 20f
        const val DOWNLOAD_ICON_DP = 18f
        const val ARC_START_DEGREES = -90f
        const val ARC_FULL_DEGREES = 360f
        const val BACKGROUND_ALPHA = 38 // 0.15 * 255
        const val TRACK_ALPHA = 46 // 0.18 * 255
        const val DISABLED_ALPHA = 97 // 0.38 * 255
    }
}
