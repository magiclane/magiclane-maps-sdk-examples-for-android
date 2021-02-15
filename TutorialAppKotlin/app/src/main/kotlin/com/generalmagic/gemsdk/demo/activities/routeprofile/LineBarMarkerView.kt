package com.generalmagic.gemsdk.demo.activities.routeprofile

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.generalmagic.gemsdk.demo.R
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Utils

class LineBarMarkerView(
    context: Context,
    layoutResource: Int,
    color: Int,
    private val mChart: CombinedChart?
) : MarkerView(context, layoutResource) {
    private val markerView: View? = this.findViewById(R.id.marker_view)
    private var chartHeight: Float = 0.toFloat()
    private var markerViewWidth: Int = 0
    private var verticalOffset: Int = 0

    init {
        markerView?.setBackgroundColor(color)

        markerViewWidth = Utils.convertDpToPixel(2.5f).toInt()
        verticalOffset = Utils.convertDpToPixel(4f).toInt()
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (markerView != null && mChart != null) {
            chartHeight =
                mChart.viewPortHandler.chartHeight - mChart.viewPortHandler.offsetBottom() + verticalOffset.toFloat() + 1f
            markerView.layoutParams = LayoutParams(markerViewWidth, chartHeight.toInt())
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }

    override fun draw(canvas: Canvas, posX: Float, posY: Float) {
        val offset = getOffsetForDrawingAtPoint(posX, posY)

        val saveId = canvas.save()
        // translate to the correct position and draw
        canvas.translate(posX + offset.x, posY + offset.y - verticalOffset)
        draw(canvas)
        canvas.restoreToCount(saveId)
    }

    fun setColor(color: Int) {
        markerView?.setBackgroundColor(color)
    }
}
