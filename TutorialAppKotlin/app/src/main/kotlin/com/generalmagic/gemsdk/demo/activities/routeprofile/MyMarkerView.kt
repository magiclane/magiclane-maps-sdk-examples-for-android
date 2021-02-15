package com.generalmagic.gemsdk.demo.activities.routeprofile

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import kotlin.math.roundToInt

class MyMarkerView(
    context: Context,
    layoutResource: Int,
    private val elevationProfile: ElevationProfile
) : MarkerView(context, layoutResource) {
    private val markerText: TextView? = findViewById(R.id.marker_text)
    private val markerImage: ImageView? = findViewById(R.id.marker_image)
    val iconSize = GEMApplication.appResources().getDimension(R.dimen.statusIconSize).toInt()

    init {
        if (markerText != null) {
            val backgroundColorId = android.R.color.white
            val backgroundColor = ContextCompat.getColor(context, backgroundColorId)

            markerText.setBackgroundColor(backgroundColor)
        }

        var markerBmp: Bitmap? = null
        GEMSdkCall.execute {
            markerBmp = GEMRouteProfileView.getElevationChartPinImage(iconSize, iconSize)
        }

        markerImage?.setImageBitmap(markerBmp)
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (markerText != null) {
            val y: Float
            val point = elevationProfile.lastElevationChartValueSelected
            y = point?.y ?: e?.y!!

            var verticalAxisUnit = ""
            GEMSdkCall.execute {
                verticalAxisUnit = GEMRouteProfileView.getElevationChartVerticalAxisUnit()
            }

            val text = y.roundToInt().toString() + " " + verticalAxisUnit
            markerText.text = text
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height + height / 20).toFloat())
    }

    fun refreshTextColors() {
        if (markerText != null) {
            val backgroundColorId = android.R.color.white
            val backgroundColor = ContextCompat.getColor(context, backgroundColorId)

            // 			markerText.setTextAppearance(context, R.style.TextStyleOutlinedWhite);
            markerText.setBackgroundColor(backgroundColor)
        }
    }
}
