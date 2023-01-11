// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routeterrainprofile

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

// -------------------------------------------------------------------------------------------------

@SuppressLint("ViewConstructor")
class LineBarMarkerView(
    context: Context,
    layoutResource: Int,
    color: Int,
    private val chart: CombinedChart
) : MarkerView(context, layoutResource)
{
    // ----------------------------------------------------------------------------------------------
    
    private val markerView: View? = this.findViewById(R.id.marker_view)
    private val markerViewWidth = resources.getDimension(R.dimen.line_marker_width).toInt()
    private val verticalOffset = resources.getDimension(R.dimen.small_padding).toInt()

    // ----------------------------------------------------------------------------------------------
    
    init
    {
        markerView?.setBackgroundColor(color)   
    }
    
    // ----------------------------------------------------------------------------------------------

    override fun refreshContent(e: Entry?, highlight: Highlight?)
    {
        markerView?.let { 
            val chartHeight = chart.viewPortHandler.chartHeight - chart.viewPortHandler.offsetBottom() + verticalOffset.toFloat() + 1f
            it.layoutParams = FrameLayout.LayoutParams(markerViewWidth, chartHeight.toInt())
        }
        
        super.refreshContent(e, highlight)
    }
    
    // ----------------------------------------------------------------------------------------------

    override fun getOffset(): MPPointF = MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    
    // ----------------------------------------------------------------------------------------------

    override fun draw(canvas: Canvas, posX: Float, posY: Float)
    {
        val offset = getOffsetForDrawingAtPoint(posX, posY)
        
        val saveId = canvas.save()
        // translate to the correct position and draw
        canvas.translate(posX + offset.x, posY + offset.y - verticalOffset)
        draw(canvas)
        canvas.restoreToCount(saveId)
    }
    
    // ----------------------------------------------------------------------------------------------
    
    fun setColor(color: Int)
    {
        markerView?.setBackgroundColor(color)
    }
    
    // ----------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
