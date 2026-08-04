/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.magiclane.sdk.routesandnavigation.ETransitType

/**
 * Builds the horizontal strip of segment icons/badges of a public-transit route
 * (walk 12 min > [bus 51] > walk 3 min ...), used by the route chips and rows.
 */
object SegmentStrip {

    @DrawableRes
    fun iconRes(type: ETransitType): Int = when (type) {
        ETransitType.Walk -> R.drawable.ic_transit_walk
        ETransitType.Bus -> R.drawable.ic_transit_bus
        ETransitType.Underground -> R.drawable.ic_transit_underground
        ETransitType.Railway -> R.drawable.ic_transit_railway
        ETransitType.Tram -> R.drawable.ic_transit_tram
        ETransitType.WaterTransport -> R.drawable.ic_transit_ferry
        ETransitType.SharedBike, ETransitType.SharedScooter -> R.drawable.ic_transit_bike
        ETransitType.SharedCar -> R.drawable.ic_transit_car
        else -> R.drawable.ic_transit_other
    }

    /**
     * Rebuilds [container] with the segment strip of [item].
     * With [showLineNames] false, transit segments show only their icon (used by the map chips).
     * [onSegmentTap] receives the original segment index within the route.
     */
    fun populate(
        container: LinearLayout,
        item: PTRouteItem,
        showLineNames: Boolean = true,
        onSegmentTap: ((Int) -> Unit)? = null,
    ) {
        container.removeAllViews()

        val context = container.context
        val resources = context.resources
        val iconSize = resources.getDimensionPixelSize(R.dimen.segment_icon_size)
        val separatorSize = resources.getDimensionPixelSize(R.dimen.segment_separator_size)
        val smallPadding = resources.getDimensionPixelSize(R.dimen.small_padding)
        val badgeRadius = resources.getDimension(R.dimen.badge_corner_radius)
        val onSurfaceColor = ContextCompat.getColor(context, R.color.on_surface)
        val defaultBadgeColor = ContextCompat.getColor(context, R.color.default_line_badge)

        // Insignificant intermediate walk legs are collapsed; first/last legs are always kept.
        val visibleSegments = item.segments.withIndex().filter { (index, segment) ->
            !segment.isWalk || segment.isSignificant || index == 0 || index == item.segments.lastIndex
        }

        visibleSegments.forEachIndexed { position, (segmentIndex, segment) ->
            val tapListener = onSegmentTap?.let { callback ->
                View.OnClickListener { callback(segmentIndex) }
            }

            val icon = ImageView(context).apply {
                setImageResource(iconRes(segment.transitType))
                adjustViewBounds = true
                tapListener?.let { setOnClickListener(it) }
                isClickable = tapListener != null
            }
            container.addView(
                icon,
                LinearLayout.LayoutParams(iconSize, iconSize).apply { gravity = Gravity.CENTER_VERTICAL },
            )

            val label: View? = when {
                !segment.isWalk && showLineNames && segment.shortName.isNotEmpty() -> TextView(context).apply {
                    text = " ${segment.shortName} "
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.segment_badge_text_size))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = GradientDrawable().apply {
                        cornerRadius = badgeRadius
                        setColor(if (segment.lineColor != 0) segment.lineColor else defaultBadgeColor)
                    }
                    setTextColor(if (segment.lineTextColor != 0) segment.lineTextColor else onSurfaceColor)
                }

                // Walk time as value over unit ("14" / "min"), like the Magic Earth strip.
                segment.isWalk && segment.travelTimeValueText.isNotEmpty() -> LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    fun stackedText(text: String, sizeRes: Int) = TextView(context).apply {
                        this.text = text
                        includeFontPadding = false
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(sizeRes))
                        setTextColor(onSurfaceColor)
                    }
                    addView(stackedText(segment.travelTimeValueText, R.dimen.segment_walk_value_text_size))
                    addView(stackedText(segment.travelTimeUnitText, R.dimen.segment_walk_unit_text_size))
                }

                else -> null
            }
            label?.let {
                it.setOnClickListener(tapListener)
                container.addView(
                    it,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginStart = smallPadding / 2
                    },
                )
            }

            if (position < visibleSegments.lastIndex) {
                val separator = ImageView(context).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    alpha = 0.6f
                }
                container.addView(
                    separator,
                    LinearLayout.LayoutParams(separatorSize, separatorSize).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginStart = smallPadding
                        marginEnd = smallPadding
                    },
                )
            }
        }
    }
}
