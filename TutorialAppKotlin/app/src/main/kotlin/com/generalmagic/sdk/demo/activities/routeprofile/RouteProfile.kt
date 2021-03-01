package com.generalmagic.sdk.demo.activities.routeprofile

// -------------------------------------------------------------------------------------------------

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.generalmagic.sdk.demo.R
import com.generalmagic.sdk.demo.activities.routeprofile.GEMRouteProfileView.TElevationProfileButtonType
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.util.AppUtils
import com.generalmagic.sdk.util.GEMSdkCall
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.DefaultFillFormatter
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import de.codecrafters.tableview.toolkit.SimpleTableHeaderAdapter
import java.text.DecimalFormat
import java.util.*

// -------------------------------------------------------------------------------------------------

class ElevationProfile(
    private var mParentActivity: AppCompatActivity,
    view: View,
    private val mElevationChartHeight: Int
) : OnChartGestureListener, OnChartValueSelectedListener {
    private var mElevationChart: CombinedChart? = null
    private var mSurfacesChart: CombinedChart? = null
    private var mRoadsChart: CombinedChart? = null
    private var mSteepnessChart: CombinedChart? = null

    private val blueColor: Int = ContextCompat.getColor(
        GEMApplication.applicationContext(),
        R.color.blue_color_new_design
    )
    private var mElevationChartPlottedValuesCount: Int =
        0 // number of y values which will be visible on the chart after each zoom
    private val highlightColorSelected = Color.rgb(239, 38, 81)
    private val highlightColorUnselected = Color.rgb(100, 100, 100)
    private val lineBarYAxisMaximum = 150f

    private var mScrollView: ScrollView? = null
    private var mButtonsConstraintLayout: ConstraintLayout? = null
    private var mClimbDetailsTitle: TextView? = null
    private var mSurfacesTitle: TextView? = null
    private var mRoadsTitle: TextView? = null
    private var mSteepnessTitle: TextView? = null
    private var mHighlightedSurface: TextView? = null
    private var mHighlightedRoad: TextView? = null
    private var mHighlightedSteepnessText: TextView? = null
    private var mHighlightedSteepnessImage: ImageView? = null
    private var mExitButton: ImageView? = null

    private var mClimbDetailsTableView: SortableClimbTableView? = null
    private var mParamsElevationChartView: LinearLayout.LayoutParams? = null
    private var mParamsClimbDetailsTableView: LinearLayout.LayoutParams? = null
    private var mLastElevationChartValueSelected: Point? = null
    private var mClimbDetailsTableRowHeight: Int = 0
    private val mLineBarChartPlottedValuesCount = 1000
    private val mTableViewRowHeight: Int
    private var mBackgroundColor: Int = 0
    private val mShowLineBarLegend = false
    private val barChartMinX = 0.0
    private val barChartMaxX = 0.1

    private val iconSize = GEMApplication.appResources().getDimension(R.dimen.listIconSize).toInt()

    private val color = Color.BLACK

    val lastElevationChartValueSelected: PointF?
        get() = if (mLastElevationChartValueSelected != null) {
            PointF(mLastElevationChartValueSelected!!.x, mLastElevationChartValueSelected!!.y)
        } else null

    private var mElevationChartMinValueX = -1.0
    private var mElevationChartMaxValueX = -1.0
    private var mChartMinValueY = Int.MIN_VALUE
    private var mChartMaxValueY = Int.MIN_VALUE

    // ---------------------------------------------------------------------------------------------

    init {
        val displayMetrics = DisplayMetrics()
        mParentActivity.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        mTableViewRowHeight =
            displayMetrics.widthPixels.coerceAtMost(displayMetrics.heightPixels) * 3 / 20

        mBackgroundColor =
            ContextCompat.getColor(GEMApplication.applicationContext(), android.R.color.white)

        initViews(view)
        initParams()

        mScrollView?.fullScroll(ScrollView.FOCUS_UP)

        mExitButton?.setOnClickListener {
            GEMSdkCall.execute {
                GEMRouteProfileView.close()
            }
        }

        mScrollView?.run {
            isVerticalScrollBarEnabled = true
        }

        refresh()
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadData() {
        GEMSdkCall.execute {
            mElevationChartMinValueX = GEMRouteProfileView.getElevationChartMinValueX()
            mElevationChartMaxValueX = GEMRouteProfileView.getElevationChartMaxValueX()
            mChartMinValueY = GEMRouteProfileView.getElevationChartMinValueY()
            mChartMaxValueY = GEMRouteProfileView.getElevationChartMaxValueY()
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun refresh() {
        loadData()
        addElevationViews()
        addSurfacesViews()
        addRoadsViews()
        addSteepnessViews()
    }

    // ---------------------------------------------------------------------------------------------

    private fun initViews(view: View?) {
        view?.run {
            mScrollView = findViewById(R.id.scrollViewElevationProfile)
            mElevationChart = findViewById(R.id.elevation_chart)
            mButtonsConstraintLayout = findViewById(R.id.buttons_container)
            mClimbDetailsTitle = findViewById(R.id.climb_details_title)
            mClimbDetailsTableView = findViewById(R.id.table_view)
            mSurfacesChart = findViewById(R.id.surfaces_chart)
            mSurfacesTitle = findViewById(R.id.surfaces_title)
            mHighlightedSurface = findViewById(R.id.highlighted_surface)
            mRoadsChart = findViewById(R.id.roads_chart)
            mRoadsTitle = findViewById(R.id.roads_title)
            mHighlightedRoad = findViewById(R.id.highlighted_road)
            mSteepnessChart = findViewById(R.id.steepness_chart)
            mSteepnessTitle = findViewById(R.id.steepness_title)
            mHighlightedSteepnessText = findViewById(R.id.highlighted_steepness_text)
            mHighlightedSteepnessImage = findViewById(R.id.highlighted_steepness_image)
            mExitButton = findViewById(R.id.exit_button)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun initParams() {
        mParamsElevationChartView = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mParamsClimbDetailsTableView = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // ---------------------------------------------------------------------------------------------

    private fun initLayout(chartHeight: Int, tableViewRowHeight: Int) {
        mParamsElevationChartView?.run {
            width = LinearLayout.LayoutParams.MATCH_PARENT
            height = chartHeight
        }

        mClimbDetailsTableRowHeight = tableViewRowHeight

        var chartVerticalBandsCount = 0
        var climbDetailsRowsCount = 0
        GEMSdkCall.execute {
            chartVerticalBandsCount =
                GEMRouteProfileView.getElevationChartVerticalBandsCount()
            climbDetailsRowsCount = GEMRouteProfileView.getClimbDetailsRowsCount()

        }

        mParamsClimbDetailsTableView?.run {
            if (chartVerticalBandsCount > 0) {
                height = tableViewRowHeight + climbDetailsRowsCount * (tableViewRowHeight + 1)
                width = LinearLayout.LayoutParams.MATCH_PARENT
            } else {
                height = 0
                width = LinearLayout.LayoutParams.MATCH_PARENT
            }
        }

        mElevationChart?.layoutParams = mParamsElevationChartView
        mClimbDetailsTableView?.layoutParams = mParamsClimbDetailsTableView
    }

    // ---------------------------------------------------------------------------------------------

    private fun setAttributesToElevationChart() {
        mElevationChart?.run {
            parent.parent.requestDisallowInterceptTouchEvent(true)
            distanceAroundPin = AppUtils.getSizeInPixelsFromMM(7).toFloat()
            isScaleXEnabled = true
            isScaleYEnabled = false
            isDragEnabled = false
            isDragXEnabled = true
            isDragYEnabled = false
            onChartGestureListener = this@ElevationProfile
            setOnChartValueSelectedListener(this@ElevationProfile)
            setDrawGridBackground(false)
            description.isEnabled = false
            isKeepPositionOnRotation = true
            isDoubleTapToZoomEnabled = false
            setPinchZoom(false)
            setDrawBorders(false)
            xAxis.setDrawGridLines(false)
            setExtraOffsets(
                0f,
                CHART_OFFSET_TOP.toFloat(),
                CHART_OFFSET_RIGHT.toFloat(),
                (-CHART_OFFSET_BOTTOM).toFloat()
            )
            fitScreen()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setElevationChartAxisBounds() {
        mElevationChart?.run {
            xAxis.position = XAxis.XAxisPosition.BOTTOM

            var chartMinValueX = 0f
            var chartMaxValueX = 0f
            var chartMinValueY = 0f
            var chartMaxValueY = 0f
            var horizontalAxisUnit = ""
            var verticalAxisUnit = ""
            var zoomTresholdDistX = 0f
            GEMSdkCall.execute {
                chartMinValueX =
                    GEMRouteProfileView.getElevationChartMinValueX().toFloat()
                chartMaxValueX =
                    GEMRouteProfileView.getElevationChartMaxValueX().toFloat()

                chartMinValueY =
                    GEMRouteProfileView.getElevationChartMinValueY().toFloat()
                chartMaxValueY =
                    GEMRouteProfileView.getElevationChartMaxValueY().toFloat()

                horizontalAxisUnit =
                    GEMRouteProfileView.getElevationChartHorizontalAxisUnit()
                verticalAxisUnit = GEMRouteProfileView.getElevationChartVerticalAxisUnit()

                zoomTresholdDistX =
                    GEMRouteProfileView.getElevationChartZoomThresholdDistX().toFloat()

            }

            xAxis.setDrawLabels(true)
            xAxis.axisMinimum = chartMinValueX
            xAxis.axisMaximum = chartMaxValueX
            xAxis.setLabelCount(4, true)
            xAxis.granularity = 0.1f

            xAxis.valueFormatter = IAxisValueFormatter { value, axis ->
                val df = DecimalFormat("#.#")
                val tempValue = (highestVisibleX - lowestVisibleX) / (axis.labelCount + 2)

                if (value > highestVisibleX - tempValue && value < highestVisibleX + tempValue) {
                    df.format(value.toDouble()) + " " + horizontalAxisUnit
                } else {
                    df.format(value.toDouble())
                }
            }

            xAxis.setAvoidFirstLastClipping(true)

            val yAxis = axisLeft
            yAxis.axisMaximum = chartMaxValueY
            yAxis.axisMinimum = chartMinValueY
            yAxis.setDrawZeroLine(false)
            yAxis.setDrawGridLines(false)
            yAxis.setLabelCount(3, true)
            yAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
            yAxis.spaceTop = 0.5f
            yAxis.granularity = 1f
            yAxis.setDrawLabels(true)
            yAxis.valueFormatter = IAxisValueFormatter { value, _ ->
                val df = DecimalFormat("#")
                if (value == chartMaxValueY) {
                    df.format(value.toDouble()) + " " + verticalAxisUnit
                } else {
                    df.format(value.toDouble())
                }
            }

            val rightAxis = axisRight
            rightAxis.isEnabled = false

            yAxis.textColor = color
            xAxis.textColor = color

            val threshold = zoomTresholdDistX
            setVisibleXRangeMinimum(threshold)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadElevationData() {
        mElevationChart?.run {
            data = null
            highlightValue(null)
            mLastElevationChartValueSelected = null

            val minX = xAxis.axisMinimum
            val maxX = xAxis.axisMaximum

            updateElevationChart(minX, maxX)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setElevationExtraInfo() {
        mButtonsConstraintLayout?.run {
            GEMApplication.topActivity()?.let { topActivity ->
                removeAllViews()

                val totalButtons = 4
                val pointersArray = IntArray(totalButtons)
                val textColor = color
                var textView: TextView?
                var imageView: ImageView?
                var bmp: Bitmap?
                var text: String?

                for (i in 0 until totalButtons) {
                    val buttonCellHorizontalScroll =
                        View.inflate(topActivity, R.layout.image_and_text_button, null)

                    buttonCellHorizontalScroll.id = 100 + i
                    pointersArray[i] = buttonCellHorizontalScroll.id

                    imageView = buttonCellHorizontalScroll.findViewById(R.id.image)
                    textView = buttonCellHorizontalScroll.findViewById(R.id.text)

                    if (imageView != null && textView != null) {
                        bmp = null
                        text = null

                        var startElevationImage: Bitmap? = null
                        var startElevationText = ""
                        var stopElevationImage: Bitmap? = null
                        var stopElevationText = ""
                        var minElevationImage: Bitmap? = null
                        var minElevationText = ""
                        var maxElevationImage: Bitmap? = null
                        var maxElevationText = ""
                        GEMSdkCall.execute {
                            startElevationImage =
                                GEMRouteProfileView.getElevationProfileButtonImage(
                                    TElevationProfileButtonType.EElevationAtDeparture.ordinal,
                                    iconSize,
                                    iconSize
                                )

                            startElevationText =
                                GEMRouteProfileView.getElevationProfileButtonText(
                                    TElevationProfileButtonType.EElevationAtDeparture.ordinal
                                )

                            stopElevationImage = GEMRouteProfileView.getElevationProfileButtonImage(
                                TElevationProfileButtonType.EElevationAtDestination.ordinal,
                                iconSize,
                                iconSize
                            )

                            stopElevationText =
                                GEMRouteProfileView.getElevationProfileButtonText(
                                    TElevationProfileButtonType.EElevationAtDestination.ordinal
                                )

                            minElevationImage = GEMRouteProfileView.getElevationProfileButtonImage(
                                TElevationProfileButtonType.EMinElevation.ordinal,
                                iconSize,
                                iconSize
                            )

                            minElevationText =
                                GEMRouteProfileView.getElevationProfileButtonText(

                                    TElevationProfileButtonType.EMinElevation.ordinal
                                )

                            maxElevationImage = GEMRouteProfileView.getElevationProfileButtonImage(
                                TElevationProfileButtonType.EMaxElevation.ordinal,
                                iconSize,
                                iconSize
                            )

                            maxElevationText =
                                GEMRouteProfileView.getElevationProfileButtonText(

                                    TElevationProfileButtonType.EMaxElevation.ordinal
                                )

                        }

                        when (i) {
                            0 -> {
                                bmp = startElevationImage
                                text = startElevationText
                            }
                            1 -> {
                                bmp = stopElevationImage
                                text = stopElevationText
                            }
                            2 -> {
                                bmp = minElevationImage
                                text = minElevationText
                            }
                            3 -> {
                                bmp = maxElevationImage
                                text = maxElevationText
                            }
                        }

                        imageView.setImageBitmap(bmp)
                        textView.visibility = View.VISIBLE
                        textView.text = text
                        textView.setTextColor(textColor)

                        if (i == 2 || i == 3) {
                            imageView.setColorFilter(textColor)
                        }

                        buttonCellHorizontalScroll.setOnClickListener {
                            when (i) {
                                0 -> {
                                    onButtonClick(TElevationProfileButtonType.EElevationAtDeparture)
                                }
                                1 -> {
                                    onButtonClick(TElevationProfileButtonType.EElevationAtDestination)
                                }
                                2 -> {
                                    onButtonClick(TElevationProfileButtonType.EMinElevation)
                                }
                                3 -> {
                                    onButtonClick(TElevationProfileButtonType.EMaxElevation)
                                }
                            }
                        }
                    }

                    addView(buttonCellHorizontalScroll)
                }

                val constraintSet = ConstraintSet()
                constraintSet.clone(this)

                for (i in 0 until totalButtons) {
                    when {
                        (i == 0) -> {
                            constraintSet.connect(
                                pointersArray[i],
                                ConstraintSet.START,
                                ConstraintSet.PARENT_ID,
                                ConstraintSet.START
                            )
                            constraintSet.connect(
                                pointersArray[i],
                                ConstraintSet.END,
                                pointersArray[i + 1],
                                ConstraintSet.START
                            )
                        }

                        (i == totalButtons - 1) -> {
                            constraintSet.connect(
                                pointersArray[i],
                                ConstraintSet.START,
                                pointersArray[i - 1],
                                ConstraintSet.END
                            )
                            constraintSet.connect(
                                pointersArray[i],
                                ConstraintSet.END,
                                ConstraintSet.PARENT_ID,
                                ConstraintSet.END
                            )
                        }

                        (i > 0 && i < totalButtons - 1) -> {
                            constraintSet.connect(
                                pointersArray[i],
                                ConstraintSet.START,
                                pointersArray[i - 1],
                                ConstraintSet.END
                            )
                            constraintSet.connect(
                                pointersArray[i],
                                ConstraintSet.END,
                                pointersArray[i + 1],
                                ConstraintSet.START
                            )
                        }
                    }
                }

                constraintSet.setHorizontalChainStyle(
                    pointersArray[0],
                    ConstraintSet.CHAIN_SPREAD_INSIDE
                )
                constraintSet.applyTo(this)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setElevationChartTextSize() {
        mScrollView?.isVerticalScrollBarEnabled = true

        mElevationChart?.run {
            val textSize = Utils.convertPixelsToDp(
                GEMApplication.appResources().getDimension(R.dimen.smallTextSize)
            ).toInt()
            xAxis.textSize = textSize.toFloat()
            axisLeft.textSize = textSize.toFloat()

            var chartVerticalBandsCount = 0
            GEMSdkCall.execute {
                chartVerticalBandsCount =
                    GEMRouteProfileView.getElevationChartVerticalBandsCount()
            }

            if (chartVerticalBandsCount > 0 && lineData != null) {
                for (i in 0 until chartVerticalBandsCount) {
                    val obj = lineData.getDataSetByIndex(i + 1)
                    obj?.run {
                        if (obj is LineDataSet) {
                            obj.valueTextSize = textSize.toFloat()
                            obj.valueTextColor = color
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun onButtonClick(type: TElevationProfileButtonType?) {
        mElevationChart?.run {
            if (type != null) {
                highlightValue(null)
                fitScreen()
                GEMSdkCall.execute {
                    GEMRouteProfileView.onElevationChartIntervalUpdate(

                        mElevationChartMinValueX,
                        mElevationChartMaxValueX,
                        true
                    )
                }

                updateElevationChart(
                    mElevationChartMinValueX.toFloat(),
                    mElevationChartMaxValueX.toFloat()
                )

                GEMSdkCall.execute {
                    GEMRouteProfileView.onPushButton(type.ordinal)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setElevationChartPlottedValuesCount() {
        var chartVerticalBandsCount = 0
        GEMSdkCall.execute {
            chartVerticalBandsCount =
                GEMRouteProfileView.getElevationChartVerticalBandsCount()
        }

        mElevationChartPlottedValuesCount = when {
            chartVerticalBandsCount > 9 -> {
                1000
            }
            chartVerticalBandsCount > 4 -> {
                600
            }
            else -> {
                300
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setElevationChartMarkerView(context: Context) {
        val mv = MyMarkerView(context, R.layout.custom_marker_view, this)
        mv.chartView = mElevationChart
        mElevationChart?.marker = mv
    }

    // ---------------------------------------------------------------------------------------------

    private fun setLineBarChartMarkerView(context: Context, chart: CombinedChart?) {
        if (chart != null) {
            val mv = LineBarMarkerView(
                context,
                R.layout.line_bar_marker_view,
                highlightColorUnselected,
                chart
            )
            mv.chartView = chart
            chart.marker = mv
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun customizeInitialDataSet(dataSet: LineDataSet) {
        val colorToFill = Color.parseColor("#D964b1ff")

        dataSet.apply {
            setDrawIcons(false)
            setDrawCircles(false)
            setDrawFilled(true)
            color = blueColor
            fillColor = colorToFill
            lineWidth = 2f
            formSize = 0f
            setDrawValues(false)
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(false)
            fillFormatter = DefaultFillFormatter()
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun createClimbListDetails(): List<Climb> {
        val climbs = ArrayList<Climb>()

        var climbDetailsRowsCount = 0

        GEMSdkCall.execute {
            climbDetailsRowsCount = GEMRouteProfileView.getClimbDetailsRowsCount()
        }

        for (rowCount in 0 until climbDetailsRowsCount) {
            var rating = ""
            var startEndPoints = ""
            var length = ""
            var startEndElevation = ""
            var avgGrade = ""
            GEMSdkCall.execute {
                rating = GEMRouteProfileView.getClimbDetailsItemText(
                    rowCount,
                    GEMRouteProfileView.TClimbDetailsInfoType.ERating.ordinal
                )
                startEndPoints = GEMRouteProfileView.getClimbDetailsItemText(
                    rowCount,
                    GEMRouteProfileView.TClimbDetailsInfoType.EStartEndPoints.ordinal
                )
                length = GEMRouteProfileView.getClimbDetailsItemText(
                    rowCount,
                    GEMRouteProfileView.TClimbDetailsInfoType.ELength.ordinal
                )
                startEndElevation = GEMRouteProfileView.getClimbDetailsItemText(
                    rowCount,
                    GEMRouteProfileView.TClimbDetailsInfoType.EStartEndElevation.ordinal
                )
                avgGrade = GEMRouteProfileView.getClimbDetailsItemText(
                    rowCount,
                    GEMRouteProfileView.TClimbDetailsInfoType.EAvgGrade.ordinal
                )
            }

            val climb = Climb(rating, startEndPoints, length, startEndElevation, avgGrade)
            climbs.add(rowCount, climb)
        }

        return climbs
    }

    // ---------------------------------------------------------------------------------------------

    private fun customizeSteepnessDataSet(dataSetVerticalBars: LineDataSet?, count: Int) {
        if (dataSetVerticalBars == null) {
            return
        }

        var verticalBandText = ""
        GEMSdkCall.execute {
            verticalBandText =
                GEMRouteProfileView.getElevationChartVerticalBandText(count)
        }

        dataSetVerticalBars.apply {
            axisDependency = YAxis.AxisDependency.LEFT
            setDrawIcons(false)
            setDrawValues(true)
            setDrawFilled(true)
            setDrawCircles(false)
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER

            val limit = mElevationChartPlottedValuesCount / 11

            valueFormatter = object : IValueFormatter {
                override fun getFormattedValue(
                    value: Float,
                    entry: Entry?,
                    dataSetIndex: Int,
                    viewPortHandler: ViewPortHandler?
                ): String {
                    val nItems = entries.size
                    if (nItems > 0 && nItems >= limit) {
                        val e = dataSetVerticalBars.entries[nItems / 2]
                        if (e.equalTo(entry)) {
                            return verticalBandText
                        }
                    }
                    return ""
                }
            }

            circleRadius = 10f
            valueTextSize = 13f
            valueTextColor = Color.BLACK
            formSize = 0f
            lineWidth = 3f
            isHighlightEnabled = false
            setDrawCircleHole(false)

            val bgColor = when (verticalBandText) {
                "0" -> Color.parseColor("#FF6428")

                "1" -> Color.parseColor("#FF8C28")

                "2" -> Color.parseColor("#FFB428")

                "3" -> Color.parseColor("#FFDC28")

                "4" -> Color.parseColor("#FFF028")

                else -> Color.WHITE
            }

            fillColor = blueColor
            color = bgColor
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setDataForClimbDetailsTableView() {
        var chartVerticalBandsCount = 0
        var climbDetailsText = ""
        var ratingText = ""
        var startEndPointsText = ""
        var lengthText = ""
        var startEndElevationText = ""
        var avgGradeText = ""
        GEMSdkCall.execute {
            chartVerticalBandsCount =
                GEMRouteProfileView.getElevationChartVerticalBandsCount()

            climbDetailsText = GEMRouteProfileView.getSectionTitle(
                GEMRouteProfileView.TRouteProfileSectionType.EClimbDetails.ordinal
            )

            ratingText = GEMRouteProfileView.getClimbDetailsColumnText(
                GEMRouteProfileView.TClimbDetailsInfoType.ERating.ordinal
            )
            startEndPointsText = GEMRouteProfileView.getClimbDetailsColumnText(
                GEMRouteProfileView.TClimbDetailsInfoType.EStartEndPoints.ordinal
            )
            lengthText = GEMRouteProfileView.getClimbDetailsColumnText(
                GEMRouteProfileView.TClimbDetailsInfoType.ELength.ordinal
            )
            startEndElevationText = GEMRouteProfileView.getClimbDetailsColumnText(
                GEMRouteProfileView.TClimbDetailsInfoType.EStartEndElevation.ordinal
            )
            avgGradeText = GEMRouteProfileView.getClimbDetailsColumnText(
                GEMRouteProfileView.TClimbDetailsInfoType.EAvgGrade.ordinal
            )
        }

        if (chartVerticalBandsCount > 0) {
            val climbTableDataAdapter = ClimbTableDataAdapter(
                mClimbDetailsTableView?.context,
                createClimbListDetails(),
                mClimbDetailsTableView
            )
            val climbHeaderTableAdapter = SimpleTableHeaderAdapter(
                mClimbDetailsTableView?.context,
                ratingText,
                startEndPointsText + "\n" + startEndElevationText,
                lengthText,
                avgGradeText
            )

            climbHeaderTableAdapter.setTextColor(Color.BLACK)

            mClimbDetailsTableView?.run {
                dataAdapter = climbTableDataAdapter
                headerAdapter = climbHeaderTableAdapter
            }

            mClimbDetailsTitle?.run {
                visibility = View.VISIBLE
                text = climbDetailsText
                setTextColor(color)
            }
        } else {
            mClimbDetailsTitle?.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setLineBarChartAttributes(chart: CombinedChart) {
        chart.run {
            setBackgroundColor(mBackgroundColor)
            setExtraOffsets(0f, 3f, 0f, 5f)
            minOffset = 0f
            isKeepPositionOnRotation = true
            dragDecelerationFrictionCoef = 0.5f
            isScaleXEnabled = false
            isScaleYEnabled = false
            isDragXEnabled = true
            isDragYEnabled = false
            setPinchZoom(false)
            setDrawBorders(false)
            setDrawGridBackground(false)
            isDoubleTapToZoomEnabled = false
            isHighlightPerDragEnabled = true
            isHighlightPerTapEnabled = true
            description.isEnabled = false
            fitScreen()

            legend.isEnabled = mShowLineBarLegend
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.xOffset = 0f
            legend.textColor = color
            legend.isWordWrapEnabled = true

            layoutParams?.height = AppUtils.getSizeInPixelsFromMM(10)
        }
    }
    // ---------------------------------------------------------------------------------------------

    private fun updateHighlightedSurfaceLabel(percent: Double) {
        var surfaceTypesCount = 0
        GEMSdkCall.execute {
            surfaceTypesCount = GEMRouteProfileView.getSurfacesCount()
        }

        var index = -1
        var d = 0.0

        for (i in 0 until surfaceTypesCount) {
            var surfacePercentWidth = 0.0
            GEMSdkCall.execute {
                surfacePercentWidth = GEMRouteProfileView.getSurfacePercent(i)
            }


            d += surfacePercentWidth
            if (percent <= d) {
                index = i
                break
            }
        }

        if (mHighlightedSurface != null && index >= 0 && index < surfaceTypesCount) {
            var surfaceTypeName = ""
            GEMSdkCall.execute {
                surfaceTypeName = GEMRouteProfileView.getSurfaceText(index)
            }

            val text =
                surfaceTypeName // mElevationViewData.getSurfaceTypeName(index) + ": " + mElevationViewData.getSurfaceTypeLength(index);
            mHighlightedSurface?.text = text
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateHighlightedRoadLabel(percent: Double) {
        var roadTypesCount = 0
        GEMSdkCall.execute {
            roadTypesCount = GEMRouteProfileView.getWaysCount()
        }

        var index = -1
        var d = 0.0

        for (i in 0 until roadTypesCount) {
            var roadTypePercentWidth = 0.0
            GEMSdkCall.execute {
                roadTypePercentWidth = GEMRouteProfileView.getWayPercent(i)
            }

            d += roadTypePercentWidth
            if (percent <= d) {
                index = i
                break
            }
        }

        if (mHighlightedRoad != null && index >= 0 && index < roadTypesCount) {
            var roadTypeName = ""
            GEMSdkCall.execute {
                roadTypeName = GEMRouteProfileView.getWayText(index)
            }

            val text =
                roadTypeName // mElevationViewData.getRoadTypeName(index) + ": " + mElevationViewData.getRoadTypeLength(index);
            mHighlightedRoad?.text = text
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateHighlightedSteepnessLabel(percent: Double) {
        var steepnessTypesCount = 0
        GEMSdkCall.execute {
            steepnessTypesCount = GEMRouteProfileView.getSteepnessesCount()
        }

        var index = -1
        var d = 0.0

        for (i in 0 until steepnessTypesCount) {
            var steepnessTypePercentWidth = 0.0
            GEMSdkCall.execute {
                steepnessTypePercentWidth = GEMRouteProfileView.getSteepnessPercent(i)
            }

            d += steepnessTypePercentWidth
            if (percent <= d) {
                index = i
                break
            }
        }

        if (mHighlightedSteepnessText != null && index >= 0 && index < steepnessTypesCount) {
            var steepnessTypeName = ""
            var steepnessBmp: Bitmap? = null
            GEMSdkCall.execute {
                steepnessTypeName = GEMRouteProfileView.getSteepnessText(index)

                steepnessBmp =
                    GEMRouteProfileView.getSteepnessImage(index, iconSize, iconSize)
            }

            val text = steepnessTypeName
            mHighlightedSteepnessText?.text = text

            if (mHighlightedSteepnessImage != null) {
                mHighlightedSteepnessImage?.setImageBitmap(steepnessBmp)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setAttributesToSurfacesChart() {
        if (mSurfacesChart != null) {
            setLineBarChartAttributes(mSurfacesChart!!)

            mSurfacesChart?.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry, h: Highlight) {
                    enableSelection(mSurfacesChart)

                    mSurfacesChart?.highlightValue(h)

                    val maxX = mSurfacesChart?.xAxis?.axisMaximum?.toDouble()

                    val percent = e.x / maxX!!

                    GEMSdkCall.execute {
                        GEMRouteProfileView.onTouchSurfacesChart(0, percent)
                    }

                    updateHighlightedSurfaceLabel(percent)

                    removeSelection(mRoadsChart)
                    removeSelection(mSteepnessChart)
                }

                override fun onNothingSelected() {
                }
            })
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setAttributesToRoadsChart() {
        if (mRoadsChart != null) {
            setLineBarChartAttributes(mRoadsChart!!)

            mRoadsChart?.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry, h: Highlight) {
                    enableSelection(mRoadsChart)

                    mRoadsChart?.highlightValue(h)

                    val maxX = mRoadsChart?.xAxis?.axisMaximum?.toDouble()

                    val percent = e.x / maxX!!

                    GEMSdkCall.execute {
                        GEMRouteProfileView.onTouchWaysChart(0, percent)
                    }

                    updateHighlightedRoadLabel(percent)

                    removeSelection(mSurfacesChart)
                    removeSelection(mSteepnessChart)
                }

                override fun onNothingSelected() {
                }
            })
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setAttributesToSteepnessChart() {
        if (mSteepnessChart != null) {
            setLineBarChartAttributes(mSteepnessChart!!)

            mSteepnessChart?.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry, h: Highlight) {
                    enableSelection(mSteepnessChart)

                    mSteepnessChart?.highlightValue(h)

                    val maxX = mSteepnessChart?.xAxis?.axisMaximum?.toDouble()

                    val percent = e.x / maxX!!

                    GEMSdkCall.execute {
                        GEMRouteProfileView.onTouchSteepnessesChart(0, percent)
                    }

                    updateHighlightedSteepnessLabel(percent)

                    removeSelection(mSurfacesChart)
                    removeSelection(mRoadsChart)
                }

                override fun onNothingSelected() {
                }
            })
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setLineBarChartAxisBounds(chart: CombinedChart?) {
        if (chart != null) {
            val xAxis = chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = 1f
            xAxis.setDrawLabels(false)
            xAxis.setDrawAxisLine(false)
            xAxis.setDrawGridLines(false)
            xAxis.isEnabled = false

            val yAxis = chart.axisLeft
            yAxis.axisMinimum = 0f
            yAxis.axisMaximum = lineBarYAxisMaximum
            yAxis.setDrawZeroLine(false)
            yAxis.setDrawLabels(false)
            yAxis.setDrawAxisLine(false)
            yAxis.setDrawGridLines(false)
            yAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
            yAxis.isEnabled = false

            val rightAxis = chart.axisRight
            rightAxis.isEnabled = false
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun addElevationViews() {
        setElevationChartPlottedValuesCount()
        setElevationChartMarkerView(mParentActivity)
        initLayout(mElevationChartHeight, mTableViewRowHeight)
        setAttributesToElevationChart()
        setElevationChartAxisBounds()
        loadElevationData()
        setElevationExtraInfo()
        setElevationChartTextSize()
        addClimbDetailsTableView()
    }

    // ---------------------------------------------------------------------------------------------

    private fun addSurfacesViews() {
        var surfaceTypesCount = 0
        var title = ""
        GEMSdkCall.execute {
            surfaceTypesCount = GEMRouteProfileView.getSurfacesCount()
            title = GEMRouteProfileView.getSectionTitle(
                GEMRouteProfileView.TRouteProfileSectionType.ESurfaces.ordinal
            )
        }

        if (surfaceTypesCount > 0) {
            setAttributesToSurfacesChart()
            setLineBarChartAxisBounds(mSurfacesChart)

            if (mSurfacesTitle != null) {
                mSurfacesTitle?.visibility = View.VISIBLE
                mSurfacesTitle?.setTextColor(color)
                mSurfacesTitle?.text = title
            }

            if (mHighlightedSurface != null) {
                mHighlightedSurface?.visibility = View.VISIBLE
                mHighlightedSurface?.setTextColor(color)
            }

            if (mSurfacesChart != null) {
                mSurfacesChart?.visibility = View.VISIBLE
                loadSurfacesData()
                updateHighlightedSurfaceLabel(0.0)

                val highlight = mSurfacesChart?.getHighlightByTouchPoint(0f, 0f)
                if (highlight != null) {
                    mSurfacesChart?.highlightValue(highlight)
                }

                setLineBarChartMarkerView(mParentActivity, mSurfacesChart)
            }
        } else {
            if (mSurfacesTitle != null) {
                mSurfacesTitle?.visibility = View.GONE
            }

            if (mHighlightedSurface != null) {
                mHighlightedSurface?.visibility = View.GONE
            }

            if (mSurfacesChart != null) {
                mSurfacesChart?.visibility = View.GONE
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun addRoadsViews() {
        var roadTypesCount = 0
        var title = ""
        GEMSdkCall.execute {
            roadTypesCount = GEMRouteProfileView.getWaysCount()
            title = GEMRouteProfileView.getSectionTitle(
                GEMRouteProfileView.TRouteProfileSectionType.EWays.ordinal
            )
        }

        if (roadTypesCount > 0) {
            setAttributesToRoadsChart()
            setLineBarChartAxisBounds(mRoadsChart)

            if (mRoadsTitle != null) {
                mRoadsTitle?.visibility = View.VISIBLE
                mRoadsTitle?.setTextColor(color)
                mRoadsTitle?.text = title
            }

            if (mHighlightedRoad != null) {
                mHighlightedRoad?.visibility = View.VISIBLE
                mHighlightedRoad?.setTextColor(color)
            }

            if (mRoadsChart != null) {
                mRoadsChart?.visibility = View.VISIBLE
                loadRoadsData()
                updateHighlightedRoadLabel(0.0)

                val highlight = mRoadsChart?.getHighlightByTouchPoint(0f, 0f)
                if (highlight != null) {
                    mRoadsChart?.highlightValue(highlight)
                }

                setLineBarChartMarkerView(mParentActivity, mRoadsChart)
            }
        } else {
            if (mRoadsTitle != null) {
                mRoadsTitle?.visibility = View.GONE
            }

            if (mHighlightedRoad != null) {
                mHighlightedRoad?.visibility = View.GONE
            }

            if (mRoadsChart != null) {
                mRoadsChart?.visibility = View.GONE
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun addSteepnessViews() {
        var steepnessTypesCount = 0
        var title = ""
        GEMSdkCall.execute {
            steepnessTypesCount = GEMRouteProfileView.getSteepnessesCount()
            title = GEMRouteProfileView.getSectionTitle(
                GEMRouteProfileView.TRouteProfileSectionType.ESteepnesses.ordinal
            )
        }

        if (steepnessTypesCount > 0) {
            setAttributesToSteepnessChart()
            setLineBarChartAxisBounds(mSteepnessChart)

            if (mSteepnessTitle != null) {
                mSteepnessTitle?.visibility = View.VISIBLE
                mSteepnessTitle?.setTextColor(color)
                mSteepnessTitle?.text = title
            }

            if (mHighlightedSteepnessText != null) {
                mHighlightedSteepnessText?.visibility = View.VISIBLE
                mHighlightedSteepnessText?.setTextColor(color)
            }

            if (mSteepnessChart != null) {
                mSteepnessChart?.visibility = View.VISIBLE
                loadSteepnessData()
                updateHighlightedSteepnessLabel(0.0)

                val highlight = mSteepnessChart?.getHighlightByTouchPoint(0f, 0f)
                if (highlight != null) {
                    mSteepnessChart?.highlightValue(highlight)
                }

                setLineBarChartMarkerView(mParentActivity, mSteepnessChart)
            }
        } else {
            if (mSteepnessTitle != null) {
                mSteepnessTitle?.visibility = View.GONE
            }

            if (mHighlightedSteepnessText != null) {
                mHighlightedSteepnessText?.visibility = View.GONE
            }

            if (mSteepnessChart != null) {
                mSteepnessChart?.visibility = View.GONE
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadSurfacesData() {
        updateSurfacesChart(barChartMinX, barChartMaxX)
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadRoadsData() {
        updateRoadsChart(barChartMinX, barChartMaxX)
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadSteepnessData() {
        updateSteepnessChart(barChartMinX, barChartMaxX)
    }

    // ---------------------------------------------------------------------------------------------

    private fun refreshDataSetHighlightColor(chart: CombinedChart?, color: Int) {
        if (chart != null && chart.data != null) {
            val count = chart.data.dataSetCount
            if (count > 0) {
                for (i in 0 until count) {
                    val lineDataSet = chart.data.getDataSetByIndex(i) as LineDataSet

                    lineDataSet.highLightColor = color
                }
            }

            chart.invalidate()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun enableSelection(chart: CombinedChart?) {
        if (chart != null) {
            refreshDataSetHighlightColor(chart, highlightColorSelected)

            val marker = chart.marker
            if (marker != null && marker is LineBarMarkerView) {
                marker.setColor(highlightColorSelected)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun removeSelection(chart: CombinedChart?) {
        if (chart != null) {
            refreshDataSetHighlightColor(chart, highlightColorUnselected)

            val marker = chart.marker
            if (marker != null && marker is LineBarMarkerView) {
                marker.setColor(highlightColorUnselected)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setLineDataSetAttributes(lineDataSet: LineDataSet, color: Int) {
        lineDataSet.setDrawIcons(false)
        lineDataSet.setDrawCircles(false)
        lineDataSet.setDrawCircleHole(false)
        lineDataSet.setDrawFilled(true)
        lineDataSet.fillAlpha = 255
        lineDataSet.color = color
        lineDataSet.fillColor = color
        lineDataSet.setDrawValues(false)
        lineDataSet.setDrawHorizontalHighlightIndicator(false)
        lineDataSet.setDrawVerticalHighlightIndicator(false)
        lineDataSet.isHighlightEnabled = true
        lineDataSet.highlightLineWidth = 2.5f
        lineDataSet.highLightColor = highlightColorUnselected
        lineDataSet.valueTextColor = color
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateSurfacesChart(minX: Double, maxX: Double) {
        if (mSurfacesChart == null) {
            return
        }

        var surfaceTypesCount = 0
        GEMSdkCall.execute {
            surfaceTypesCount = GEMRouteProfileView.getSurfacesCount()
        }

        val step = (maxX - minX) / (mLineBarChartPlottedValuesCount - 1)
        var x = 0.0
        var limit = 0.0
        val y = lineBarYAxisMaximum
        val dataSets = ArrayList<ILineDataSet>()

        for (i in 0 until surfaceTypesCount) {
            val initialLineValues = ArrayList<Entry>()

            var surfacePercentWidth = 0.0
            var surfaceTypeName = ""
            var surfaceTypeColor = 0
            GEMSdkCall.execute {
                surfacePercentWidth = GEMRouteProfileView.getSurfacePercent(i)
                surfaceTypeName = GEMRouteProfileView.getSurfaceText(i)

                val color = GEMRouteProfileView.getSurfaceColor(i)
                surfaceTypeColor = AppUtils.getColor(color)
            }

            limit += surfacePercentWidth

            while (x <= limit) {
                val lineDataEntry = Entry(x.toFloat(), y)
                x += step

                initialLineValues.add(lineDataEntry)
            }

            val lineDataSet = LineDataSet(initialLineValues, surfaceTypeName)
            setLineDataSetAttributes(lineDataSet, surfaceTypeColor)
            dataSets.add(lineDataSet)
        }

        val lineData = LineData(dataSets)
        val combinedData = CombinedData()

        combinedData.setData(lineData)

        mSurfacesChart?.data = combinedData
        mSurfacesChart?.invalidate()
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateRoadsChart(minX: Double, maxX: Double) {
        if (mRoadsChart == null) {
            return
        }

        var roadTypesCount = 0
        GEMSdkCall.execute {
            roadTypesCount = GEMRouteProfileView.getWaysCount()
        }

        val step = (maxX - minX) / (mLineBarChartPlottedValuesCount - 1)
        var limit = 0.0
        var x = 0.0
        val y = lineBarYAxisMaximum
        val dataSets = ArrayList<ILineDataSet>()

        for (i in 0 until roadTypesCount) {
            val initialLineValues = ArrayList<Entry>()

            var roadTypePercentWidth = 0.0
            var roadTypeName = ""
            var roadTypeColor = 0
            GEMSdkCall.execute {
                roadTypePercentWidth = GEMRouteProfileView.getWayPercent(i)
                roadTypeName = GEMRouteProfileView.getWayText(i)

                val color = GEMRouteProfileView.getWayColor(i)
                roadTypeColor = AppUtils.getColor(color)
            }

            limit += roadTypePercentWidth

            while (x <= limit) {
                val lineDataEntry = Entry(x.toFloat(), y)
                x += step

                initialLineValues.add(lineDataEntry)
            }

            val lineDataSet = LineDataSet(initialLineValues, roadTypeName)
            setLineDataSetAttributes(lineDataSet, roadTypeColor)
            dataSets.add(lineDataSet)
        }

        val lineData = LineData(dataSets)
        val combinedData = CombinedData()

        combinedData.setData(lineData)

        mRoadsChart?.data = combinedData
        mRoadsChart?.invalidate()
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateSteepnessChart(minX: Double, maxX: Double) {
        if (mSteepnessChart == null) {
            return
        }

        var steepnessTypesCount = 0
        GEMSdkCall.execute {
            steepnessTypesCount = GEMRouteProfileView.getSteepnessesCount()
        }

        val step = (maxX - minX) / (mLineBarChartPlottedValuesCount - 1)
        var limit = 0.0
        var x = 0f
        val y = lineBarYAxisMaximum
        val dataSets = ArrayList<ILineDataSet>()

        for (i in 0 until steepnessTypesCount) {
            val initialLineValues = ArrayList<Entry>()

            var steepnessTypePercentWidth = 0.0
            var steepnessTypeName = ""
            var steepnessTypeColor = 0
            GEMSdkCall.execute {
                steepnessTypePercentWidth = GEMRouteProfileView.getSteepnessPercent(i)
                steepnessTypeName = GEMRouteProfileView.getSteepnessText(i)

                val color = GEMRouteProfileView.getSteepnessColor(i)
                steepnessTypeColor = AppUtils.getColor(color)
            }

            limit += steepnessTypePercentWidth

            while (x <= limit) {
                val lineDataEntry = Entry(x, y)
                x += step.toFloat()

                initialLineValues.add(lineDataEntry)
            }

            val lineDataSet = LineDataSet(initialLineValues, steepnessTypeName)
            setLineDataSetAttributes(lineDataSet, steepnessTypeColor)
            dataSets.add(lineDataSet)
        }

        val lineData = LineData(dataSets)
        val combinedData = CombinedData()

        combinedData.setData(lineData)

        mSteepnessChart?.data = combinedData
        mSteepnessChart?.invalidate()
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateElevationChart(minX: Float, maxX: Float) {
        val lineDataSetInitial: LineDataSet
        var lineDataSetSteepness: LineDataSet

        val initialLineValues = ArrayList<Entry>()
        val steepnessArrayGroup = ArrayList<List<Entry>>()

        var yValues: IntArray? = null
        var verticalBandsCount = 0
        GEMSdkCall.execute {
            yValues = GEMRouteProfileView.getElevationChartYValues(
                mElevationChartPlottedValuesCount
            )
            verticalBandsCount = GEMRouteProfileView.getElevationChartVerticalBandsCount()
        }

        val step = (maxX - minX) / (mElevationChartPlottedValuesCount - 1)
        var x = minX
        val lastItemIndex = mElevationChartPlottedValuesCount - 1

        for (i in 0 until lastItemIndex) {
            yValues?.get(i)?.toFloat()?.let { Entry(x, it) }?.let { initialLineValues.add(it) }
            x += step
        }
        yValues?.get(lastItemIndex)?.toFloat()?.let { Entry(maxX, it) }?.let {
            initialLineValues.add(it)
        }

        if (verticalBandsCount > 0) {
            for (i in 0 until verticalBandsCount) {
                val valuesLineSteepness = ArrayList<Entry>()

                var verticalBandMinX = 0.0
                var verticalBandMaxX = 0.0
                GEMSdkCall.execute {
                    verticalBandMinX =
                        GEMRouteProfileView.getElevationChartVerticalBandMinX(i)
                    verticalBandMaxX =
                        GEMRouteProfileView.getElevationChartVerticalBandMaxX(i)
                }

                for (entrySteepness in initialLineValues) {
                    if (entrySteepness.x in verticalBandMinX..verticalBandMaxX) {
                        valuesLineSteepness.add(entrySteepness)
                    }
                }

                steepnessArrayGroup.add(i, valuesLineSteepness)
            }
        }

        mElevationChart?.run {
            if (data != null && data.dataSetCount > 0) {
                lineDataSetInitial = data.getDataSetByIndex(0) as LineDataSet
                lineDataSetInitial.entries = initialLineValues

                val lineData = data.lineData

                lineData?.run {
                    val nCount = data.dataSetCount
                    for (i in nCount - 1 downTo 1) {
                        removeDataSet(i)
                    }

                    val nVerticalBandsCount = verticalBandsCount

                    if (nVerticalBandsCount > 0) {
                        for (i in 0 until nVerticalBandsCount) {
                            if (steepnessArrayGroup[i].isNotEmpty()) {
                                lineDataSetSteepness = LineDataSet(steepnessArrayGroup[i], null)
                                customizeSteepnessDataSet(lineDataSetSteepness, i)
                                addDataSet(lineDataSetSteepness)
                            }
                        }
                    }
                }

                data.notifyDataChanged()
                notifyDataSetChanged()
            } else {
                val combinedData = CombinedData()

                val dataSets = ArrayList<ILineDataSet>()
                val bubbleData = BubbleData()
                lineDataSetInitial = LineDataSet(initialLineValues, null)
                customizeInitialDataSet(lineDataSetInitial)
                dataSets.add(lineDataSetInitial)

                if (verticalBandsCount > 0) {
                    for (i in 0 until verticalBandsCount) {
                        if (steepnessArrayGroup[i].isNotEmpty()) {
                            lineDataSetSteepness = LineDataSet(steepnessArrayGroup[i], null)
                            customizeSteepnessDataSet(lineDataSetSteepness, i)
                            dataSets.add(lineDataSetSteepness)
                        }
                    }
                }

                val lineData = LineData(dataSets)

                combinedData.setData(bubbleData)
                combinedData.setData(lineData)

                data = combinedData
                invalidate()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateElevationChartHighlight() {
        mElevationChart?.run {
            mLastElevationChartValueSelected?.let {
                val highlightedArray = highlighted
                if (!highlightedArray.isNullOrEmpty()) {
                    val highlight = highlightedArray[0]
                    val entry = lineData.getEntryForHighlight(highlight)

                    if (entry != null) {
                        if ((it.x < lowestVisibleX) || (it.x > highestVisibleX)) {
                            isPinOnLeftSide = false
                        } else {
                            if (lineData.dataSetCount > 0) {
                                val x = getClosestElevationChartPlottedXValue(it.x, entry.y)
                                val h = Highlight(x, it.y, 0)
                                h.dataIndex = 0
                                highlightValue(h)
                            }

                            if (it.x < (highestVisibleX - lowestVisibleX) / 20 + lowestVisibleX) {
                                isPinOnLeftSide = true
                                getLastPinTouchedInfo(this)
                            } else {
                                isPinOnLeftSide = false
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getClosestElevationChartPlottedXValue(distance: Float, y: Float): Float {
        mElevationChart?.let {
            if (it.data.dataSetCount > 0) {
                val lineDataSet = it.data.dataSets[0]
                val entry = lineDataSet.getEntryForXValue(distance, y)
                return entry.x
            }
        }

        return distance
    }

    // ---------------------------------------------------------------------------------------------

    internal fun didUpdatePinPosition(distance: Double) {
        mScrollView?.fullScroll(ScrollView.FOCUS_UP)

        val threshold = (mElevationChart!!.highestVisibleX - mElevationChart!!.lowestVisibleX) / 2
        var leftSide = distance - threshold
        var rightSide = distance + threshold

        val isInsideRange =
            distance >= mElevationChart!!.lowestVisibleX && distance <= mElevationChart!!.highestVisibleX

        var chartYValue = 0
        GEMSdkCall.execute {
            chartYValue = GEMRouteProfileView.getElevationChartYValue(distance)
        }

        if (leftSide < mElevationChartMinValueX) {
            leftSide = mElevationChartMinValueX
            rightSide = leftSide + threshold * 2
        }

        if (rightSide > mElevationChartMaxValueX) {
            rightSide = mElevationChartMaxValueX
            leftSide = rightSide - threshold * 2
        }

        val y = chartYValue

        if (mLastElevationChartValueSelected == null) {
            mLastElevationChartValueSelected = Point(0f, 0f)
        }
        mLastElevationChartValueSelected?.x = distance.toFloat()
        mLastElevationChartValueSelected?.y = y.toFloat()

        if (!isInsideRange) {
            updateElevationChart(leftSide.toFloat(), rightSide.toFloat())
            mElevationChart?.moveViewToX(leftSide.toFloat())

            GEMSdkCall.execute {
                GEMRouteProfileView.onElevationChartIntervalUpdate(
                    leftSide,
                    rightSide,
                    false
                )
            }

            mElevationChart?.invalidate()
        }

        val x = getClosestElevationChartPlottedXValue(distance.toFloat(), y.toFloat())
        val h = Highlight(x, y.toFloat(), 0)
        h.dataIndex = 0

        mElevationChart?.highlightValue(h)

        if (distance.toFloat() < (rightSide - leftSide) / 20 + leftSide) {
            mElevationChart?.isPinOnLeftSide = true
            getLastPinTouchedInfo(mElevationChart!!)
        } else {
            mElevationChart?.isPinOnLeftSide = false
        }
    }

    // ---------------------------------------------------------------------------------------------

    internal fun updateElevationChartInterval(minX: Double, maxX: Double) {
        val chartMin = mElevationChart!!.lowestVisibleX
        val chartMax = mElevationChart!!.highestVisibleX
        val mapMin = minX.toFloat()
        val mapMax = maxX.toFloat()

        if (mapMax - mapMin != 0f) {
            val scaleFactor = (chartMax - chartMin) / (mapMax - mapMin)

            mElevationChart?.zoomToCenter(scaleFactor, 1f)
            updateElevationChart(mapMin, mapMax)
            updateElevationChartHighlight()
            mElevationChart?.moveViewToX(mapMin)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun addClimbDetailsTableView() {
        mClimbDetailsTableView?.let { it ->
            setDataForClimbDetailsTableView()
            it.removeAllDataClickListeners()
            it.addDataClickListener { _, climbClicked ->
                if (climbClicked != null) {
                    val startPointString = climbClicked.startEndPoint.split(" ")[0]
                    val s = climbClicked.startEndPoint.substring(
                        climbClicked.startEndPoint.lastIndexOf("/") + 1
                    )
                    val endPointString =
                        s.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()[0]

                    val startPointDouble = startPointString.toDouble() + 0.01
                    val endPointDouble = endPointString.toDouble() - 0.01

                    mElevationChart?.highlightValue(null)
                    GEMSdkCall.execute {
                        GEMRouteProfileView.onTouchElevationChart(

                            0,
                            mElevationChartMinValueX
                        )
                    }
                    GEMSdkCall.execute {
                        GEMRouteProfileView.onElevationChartIntervalUpdate(

                            startPointDouble,
                            endPointDouble,
                            true
                        )
                    }

                    updateElevationChartInterval(startPointDouble, endPointDouble)

                    mScrollView?.fullScroll(ScrollView.FOCUS_UP)
                    removeSelection(mSurfacesChart)
                    removeSelection(mRoadsChart)
                    removeSelection(mSteepnessChart)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getLastPinTouchedInfo(mChart: CombinedChart) {
        mLastElevationChartValueSelected?.let {
            if (mChartMinValueY != mChartMaxValueY) {
                mChart.ratio = (it.y - mChartMinValueY) / (mChartMaxValueY - mChartMinValueY)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartGestureStart(
        me: MotionEvent,
        lastPerformedGesture: ChartTouchListener.ChartGesture
    ) {
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartGestureEnd(
        me: MotionEvent,
        lastPerformedGesture: ChartTouchListener.ChartGesture
    ) {
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartLongPressed(me: MotionEvent) {
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartDoubleTapped(me: MotionEvent) {
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartSingleTapped(me: MotionEvent) {
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartFling(
        me1: MotionEvent,
        me2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ) {
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartScale(me: MotionEvent, scaleX: Float, scaleY: Float) {
        var minX = mElevationChart?.xAxis?.axisMinimum
        var maxX = mElevationChart?.xAxis?.axisMaximum

        if (mElevationChart?.data?.dataSetCount!! > 0) {
            minX = mElevationChart?.lowestVisibleX
            maxX = mElevationChart?.highestVisibleX
        }

        GEMSdkCall.execute {
            if (minX != null && maxX != null) {
                GEMRouteProfileView.onElevationChartIntervalUpdate(

                    minX.toDouble(),
                    maxX.toDouble(),
                    true
                )
            }
        }

        if (minX != null && maxX != null) {
            updateElevationChart(minX, maxX)
        }
        updateElevationChartHighlight()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartTranslate(me: MotionEvent, dX: Float, dY: Float) {
        var minX = mElevationChart?.xAxis?.axisMinimum
        var maxX = mElevationChart?.xAxis?.axisMaximum

        if (mElevationChart?.data?.dataSetCount!! > 0) {
            minX = mElevationChart?.lowestVisibleX
            maxX = mElevationChart?.highestVisibleX
        }

        GEMSdkCall.execute {
            if (minX != null && maxX != null) {
                GEMRouteProfileView.onElevationChartIntervalUpdate(

                    minX.toDouble(),
                    maxX.toDouble(),
                    true
                )
            }
        }

        if (minX != null && maxX != null) {
            updateElevationChart(minX, maxX)
        }

        updateElevationChartHighlight()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onValueSelected(e: Entry, h: Highlight) {
        if (h.dataIndex == 0) {
            removeSelection(mSurfacesChart)
            removeSelection(mRoadsChart)
            removeSelection(mSteepnessChart)

            if (mLastElevationChartValueSelected == null) {
                mLastElevationChartValueSelected = Point(0f, 0f)
            }

            mLastElevationChartValueSelected?.run {
                x = e.x
                y = e.y
            }
            mElevationChart?.highlightValue(h)

            GEMSdkCall.execute {
                GEMRouteProfileView.onTouchElevationChart(0, e.x.toDouble())
            }

            if (mLastElevationChartValueSelected!!.x < (mElevationChart!!.highestVisibleX - mElevationChart!!.lowestVisibleX) / 20 + mElevationChart!!.lowestVisibleX) {
                mElevationChart?.isPinOnLeftSide = true
                getLastPinTouchedInfo(mElevationChart!!)
            } else {
                mElevationChart?.isPinOnLeftSide = false
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onNothingSelected() {
        mLastElevationChartValueSelected = null
    }

    // ---------------------------------------------------------------------------------------------

    companion object {
        private const val CHART_OFFSET_TOP = 23
        private const val CHART_OFFSET_RIGHT = 17
        private const val CHART_OFFSET_BOTTOM = 20
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------

internal class Point(var x: Float, var y: Float)

// -------------------------------------------------------------------------------------------------

// internal class MyBounceInterpolator(private val mAmplitude: Double, private val mFrequency: Double) : android.view.animation.Interpolator
// {
//    override fun getInterpolation(time: Float): Float
//    {
//        return (-1.0 * Math.E.pow(-time / mAmplitude) *
//                cos(mFrequency * time) + 1).toFloat()
//    }
// }

// -------------------------------------------------------------------------------------------------
