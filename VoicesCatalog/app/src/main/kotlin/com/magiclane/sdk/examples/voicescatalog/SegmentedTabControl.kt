/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalog

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.google.android.material.color.MaterialColors

/**
 * Rounded pill-style segmented control used to switch between the Offline and Online
 * catalog tabs — a views port of the maps-compose `SegmentedTabControl`.
 */
class SegmentedTabControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    /** Called with the tapped tab index (also when the already-selected tab is tapped). */
    var onTabSelected: ((Int) -> Unit)? = null

    var selectedIndex = 0
        set(value) {
            field = value
            applySelection()
        }

    private val tabViews = mutableListOf<TextView>()

    private val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
    private val onPrimaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)
    private val onSurfaceVariantColor =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)

    init {
        orientation = HORIZONTAL
        background = GradientDrawable().apply {
            cornerRadius = OUTER_RADIUS_DP * resources.displayMetrics.density
            setColor(
                MaterialColors.getColor(
                    this@SegmentedTabControl,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                ),
            )
        }
        setPadding(0)
        setLabels(listOf(context.getString(R.string.offline), context.getString(R.string.online)))
    }

    /** Replaces the tab labels (two by default: Offline / Online). */
    fun setLabels(labels: List<String>) {
        removeAllViews()
        tabViews.clear()
        labels.forEachIndexed { index, label ->
            val tab = TextView(context).apply {
                text = label
                gravity = android.view.Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SIZE_SP)
                setOnClickListener { onTabSelected?.invoke(index) }
            }
            tabViews += tab
            addView(tab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        applySelection()
    }

    private fun applySelection() {
        tabViews.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            tab.background = if (selected) {
                GradientDrawable().apply {
                    cornerRadius = INNER_RADIUS_DP * resources.displayMetrics.density
                    setColor(primaryColor)
                }
            } else {
                null
            }
            tab.setTextColor(if (selected) onPrimaryColor else onSurfaceVariantColor)
            tab.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private companion object {
        const val OUTER_RADIUS_DP = 22f
        const val INNER_RADIUS_DP = 20f
        const val LABEL_TEXT_SIZE_SP = 14f
    }
}
