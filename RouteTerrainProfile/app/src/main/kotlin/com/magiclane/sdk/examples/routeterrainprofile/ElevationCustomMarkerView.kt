/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routeterrainprofile

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class ElevationCustomMarkerView(
    context: Context,
    layoutResource: Int,
    private val routeProfile: RouteProfile,
) : MarkerView(context, layoutResource) {

    // MarkerView inflates its layout via a plain LayoutInflater in its own constructor, so
    // DataBinding.bind() cannot be used here — it requires tags set during DataBinding-managed
    // inflation. Use findViewById instead.
    private val markerText: TextView = findViewById(R.id.marker_text)
    private val markerImage: ImageView = findViewById(R.id.marker_image)
    private val iconSize = resources.getDimension(R.dimen.pin_size).toInt()

    init
    {
        markerText.apply {
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }

        var markerBmp: Bitmap? = null
        SdkCall.execute {
            markerBmp = GemUtilImages.asBitmap(
                SdkImages.Core.Search_Results_Pin.value,
                iconSize,
                iconSize,
            )
        }

        markerImage.setImageBitmap(markerBmp)
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val y = routeProfile.lastElevationChartValueSelected?.y ?: e?.y

        val text = y?.roundToInt().toString() + " m"
        markerText.text = text

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF = MPPointF((-(width / 2)).toFloat(), (-height + height / 20).toFloat())
}
