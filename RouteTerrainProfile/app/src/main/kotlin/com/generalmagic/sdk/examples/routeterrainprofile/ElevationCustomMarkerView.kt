// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.routeterrainprofile

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import com.generalmagic.sdk.util.GemUtilImages
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import kotlin.math.roundToInt

// -------------------------------------------------------------------------------------------------

@SuppressLint("ViewConstructor")
class ElevationCustomMarkerView(
    context: Context,
    layoutResource: Int,
    private val routeProfile: RouteProfile
) : MarkerView(context, layoutResource)
{
    // ---------------------------------------------------------------------------------------------
    
    private val markerText: TextView = findViewById(R.id.marker_text)
    private val markerImage: ImageView = findViewById(R.id.marker_image)
    private val iconSize = resources.getDimension(R.dimen.pin_size).toInt()
    
    // ---------------------------------------------------------------------------------------------
    
    init
    {
        markerText.apply {
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }
        
        var markerBmp: Bitmap? = null
        SdkCall.execute { markerBmp = GemUtilImages.asBitmap(SdkImages.Engine_Misc.LocationDetails_PlacePushpin.value, iconSize, iconSize) }
        
        markerImage.setImageBitmap(markerBmp)
    }
    
    // ---------------------------------------------------------------------------------------------

    override fun refreshContent(e: Entry?, highlight: Highlight?)
    {
        val y = routeProfile.lastElevationChartValueSelected?.y ?: e?.y
        
        val text = y?.roundToInt().toString() + " m"
        markerText.text = text
        
        super.refreshContent(e, highlight)
    }
    
    // ---------------------------------------------------------------------------------------------

    override fun getOffset(): MPPointF = MPPointF((-(width / 2)).toFloat(), (-height + height / 20).toFloat())
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
