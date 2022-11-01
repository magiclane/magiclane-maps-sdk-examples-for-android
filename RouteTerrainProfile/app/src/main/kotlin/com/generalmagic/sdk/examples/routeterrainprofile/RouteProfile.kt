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

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.generalmagic.sdk.core.ImageDatabase
import com.generalmagic.sdk.core.Path
import com.generalmagic.sdk.core.Rect
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EHighlightOptions
import com.generalmagic.sdk.d3scene.EMapViewPerspective
import com.generalmagic.sdk.d3scene.HighlightRenderSettings
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.ERoadType
import com.generalmagic.sdk.routesandnavigation.ESurfaceType
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteTerrainProfile
import com.generalmagic.sdk.util.EStringIds
import com.generalmagic.sdk.util.GemUtil
import com.generalmagic.sdk.util.GemUtilImages
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.Util
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BubbleData
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.DefaultFillFormatter
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ViewPortHandler
import de.codecrafters.tableview.toolkit.SimpleTableHeaderAdapter
import java.text.DecimalFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.round

// -------------------------------------------------------------------------------------------------

class RouteProfile(
    private val parentActivity: MainActivity,
    private val route: Route,
) : OnChartGestureListener, OnChartValueSelectedListener
{
    // ---------------------------------------------------------------------------------------------

    enum class TRouteProfileSectionType
    {
        EElevation,
        EClimbDetails,
        EWays,
        ESurfaces,
        ESteepnesses
    }

    // ---------------------------------------------------------------------------------------------

    enum class TElevationProfileButtonType
    {
        EElevationAtDeparture,
        EElevationAtDestination,
        EMinElevation,
        EMaxElevation
    }

    // ---------------------------------------------------------------------------------------------

    enum class TClimbDetailsInfoType
    {
        ERating,
        EStartEndPoints,
        EStartEndElevation,
        ELength,
        EAvgGrade
    }

    // ---------------------------------------------------------------------------------------------

    enum class TTouchChartEvent
    {
        EDown,
        EMove,
        EUp
    }

    // ---------------------------------------------------------------------------------------------

    data class CSectionItem(var mStartDistanceM: Int, var mLengthM: Int)

    // ---------------------------------------------------------------------------------------------

    enum class TSteepnessImageType
    {
        EUnknown,
        EDown,
        EPlain,
        EUp
    }

    // ---------------------------------------------------------------------------------------------
    
    private lateinit var routeTerrainProfile: RouteTerrainProfile
    
    private val elevationChart: CombinedChart
    private val buttonsContainer: ConstraintLayout
    private val climbDetailsTitle: TextView
    private val tableView: SortableClimbTableView
    private val surfacesTitle: TextView
    private val highlightedSurface: TextView
    private val surfacesChart: CombinedChart
    private val roadsTitle: TextView
    private val highlightedRoad: TextView
    private val roadsChart: CombinedChart
    private val steepnessTitle: TextView
    private val steepnessImage: ImageView
    private val highlightedSteepness: TextView
    private val steepnessChart: CombinedChart
    
    private val tableViewRowHeight: Int

    private val lineBarYAxisMaximum = 150f
    private val lineBarChartPlottedValuesCount = 1000
    private val barChartMinX = 0.0
    private val barChartMaxX = 0.1
    
    private var _lastElevationChartValueSelected: Point? = null

    private val steepnessIconSize = parentActivity.resources.getDimension(R.dimen.steepness_icon_size).toInt()
    private val elevationIconSize = parentActivity.resources.getDimension(R.dimen.elevation_button_size).toInt()
    
    private var routeLength = 0
    
    private var chartMinX = 0.0
    private var chartMaxX = 0.0
    
    private var previousTouchXMeters = -1

    private var highlightedLandmarkList = arrayListOf<Landmark>()
    private var highlightPathsColor = Rgba()
    private val highlightColorSelected = Color.rgb(239, 38, 81)
    private val highlightColorUnselected = Color.rgb(100, 100, 100)
    private var elevationChartPlottedValuesCount: Int = 0 // number of y values which will be visible on the chart after each zoom

    private var surfacesTypes = mutableMapOf<ESurfaceType, ArrayList<CSectionItem>>()
    private var roadsTypes = mutableMapOf<ERoadType, ArrayList<CSectionItem>>()
    private var steepnessTypes = mutableMapOf<Int, ArrayList<CSectionItem>>()

    private var highlightedSurfaceType = -1
    private var highlightedSurfacePaths = arrayListOf<Path>()
    private var highlightedRoadType = -1
    private var highlightedRoadPaths = arrayListOf<Path>()
    private var highlightedSteepnessType = -1
    private var highlightedSteepnessPaths = arrayListOf<Path>()
    
    val lastElevationChartValueSelected: PointF?
        get() = if (_lastElevationChartValueSelected != null)
        {
            _lastElevationChartValueSelected?.x?.let { x -> 
                _lastElevationChartValueSelected?.y?.let { y -> 
                    PointF(x, y)
                }
            }
        }
        else null
    
    // ---------------------------------------------------------------------------------------------
    
    init
    {
        parentActivity.apply {
            SdkCall.execute { 
                routeTerrainProfile = route.terrainProfile!!
                routeLength = route.timeDistance?.totalDistance ?: 0
                highlightPathsColor = Rgba(239, 38, 81, 255)
            }
            
            elevationChart = findViewById(R.id.elevation_chart)
            buttonsContainer = findViewById(R.id.buttons_container)
            climbDetailsTitle = findViewById(R.id.climb_details_title)
            tableView = findViewById(R.id.table_view)
            surfacesTitle = findViewById(R.id.surfaces_title)
            highlightedSurface = findViewById(R.id.highlighted_surface)
            surfacesChart = findViewById(R.id.surfaces_chart)
            roadsTitle = findViewById(R.id.roads_title)
            highlightedRoad = findViewById(R.id.highlighted_road)
            roadsChart = findViewById(R.id.roads_chart)
            steepnessTitle = findViewById(R.id.steepness_title)
            steepnessImage = findViewById(R.id.steepness_image)
            highlightedSteepness = findViewById(R.id.highlighted_steepness)
            steepnessChart = findViewById(R.id.steepness_chart)
            
            val displayMetrics = Resources.getSystem().displayMetrics
            tableViewRowHeight = displayMetrics.widthPixels.coerceAtMost(displayMetrics.heightPixels) * 3 / 20

            loadData()
            addElevationViews()
            addSurfacesViews()
            addRoadsViews()
            addSteepnessViews()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) = Unit

    // ---------------------------------------------------------------------------------------------

    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) = Unit

    // ---------------------------------------------------------------------------------------------

    override fun onChartLongPressed(me: MotionEvent?) = Unit

    // ---------------------------------------------------------------------------------------------

    override fun onChartDoubleTapped(me: MotionEvent?) = Unit

    // ---------------------------------------------------------------------------------------------

    override fun onChartSingleTapped(me: MotionEvent?) = Unit

    // ---------------------------------------------------------------------------------------------

    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) = Unit

    // ---------------------------------------------------------------------------------------------

    override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float)
    {
        var minX = elevationChart.xAxis.axisMinimum
        var maxX = elevationChart.xAxis.axisMaximum
        
        if (elevationChart.data.dataSetCount > 0)
        {
            minX = elevationChart.lowestVisibleX
            maxX = elevationChart.highestVisibleX
        }
        
        SdkCall.execute {  onElevationChartIntervalUpdate(minX.toDouble(), maxX.toDouble(), true)  }
        updateElevationChartInterval(minX.toDouble(), maxX.toDouble())
        updateElevationChartHighlight()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float)
    {
        var minX = elevationChart.xAxis.axisMinimum
        var maxX = elevationChart.xAxis.axisMaximum

        if (elevationChart.data.dataSetCount > 0)
        {
            minX = elevationChart.lowestVisibleX
            maxX = elevationChart.highestVisibleX
        }
        
        SdkCall.execute {  onElevationChartIntervalUpdate(minX.toDouble(), maxX.toDouble(), true)  }
        updateElevationChartInterval(minX.toDouble(), maxX.toDouble())
        updateElevationChartHighlight()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onValueSelected(e: Entry, h: Highlight)
    {
        if (h.dataIndex == 0)
        {
            removeSelection(surfacesChart)
            removeSelection(roadsChart)
            removeSelection(steepnessChart)
            
            if (_lastElevationChartValueSelected == null)
            {
                _lastElevationChartValueSelected = Point(0f, 0f)
            }
            
            _lastElevationChartValueSelected?.run { 
                x = e.x
                y = e.y
            }
            elevationChart.highlightValue(h)
            
            SdkCall.execute {  onTouchElevationChart(0, e.x.toDouble())  }
        }
    }

    // ---------------------------------------------------------------------------------------------
    
    override fun onNothingSelected()
    {
        _lastElevationChartValueSelected = null
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun loadData() = SdkCall.execute {
        chartMinX = 0.0
        chartMaxX = routeLength.toDouble()
        surfacesTypes.clear()
        roadsTypes.clear()
        steepnessTypes.clear()
        removeHighlightedSurfacePathsFromMap()
        removeHighlightedRoadPathsFromMap()
        removeHighlightedSteepnessPathsFromMap()
        
        val steepnessIntervals = arrayListOf(-16f, -10f, -7f, -4f, -1f, 1f, 4f, 7f, 10f, 16f)
        routeTerrainProfile.surfaceSections?.let { surfacesSectionList ->
            var length: Int
            for ((i, item) in surfacesSectionList.withIndex())
            {
                length = if (i < surfacesSectionList.size - 1)
                {
                    surfacesSectionList[i + 1].startDistanceM - item.startDistanceM
                }
                else
                {
                    routeLength - item.startDistanceM
                }
                
                if (surfacesTypes.containsKey(item.type))
                {
                    surfacesTypes[item.type]?.add(CSectionItem(item.startDistanceM, length))
                }
                else
                {
                    surfacesTypes[item.type] = arrayListOf(CSectionItem(item.startDistanceM, length))
                }
            }
        }
        
        routeTerrainProfile.roadTypeSections?.let { roadTypeSectionList -> 
            var length: Int
            for ((i, item) in roadTypeSectionList.withIndex())
            {
                length = if (i < roadTypeSectionList.size - 1)
                {
                    roadTypeSectionList[i + 1].startDistanceM - item.startDistanceM
                }
                else
                {
                    routeLength - item.startDistanceM
                }
                
                if (roadsTypes.containsKey(item.type))
                {
                    roadsTypes[item.type]?.add(CSectionItem(item.startDistanceM, length))
                }
                else
                {
                    roadsTypes[item.type] = arrayListOf(CSectionItem(item.startDistanceM, length))
                }
            }
        }
        
        routeTerrainProfile.getSteepSections(steepnessIntervals)?.let { steepnessSectionList -> 
            var length: Int
            
            for ((i, item) in steepnessSectionList.withIndex())
            {
                length = if (i < steepnessSectionList.size - 1)
                {
                    steepnessSectionList[i + 1].startDistanceM - item.startDistanceM
                }
                else
                {
                    routeLength - item.startDistanceM
                }
                
                if (steepnessTypes.containsKey(item.category))
                {
                    steepnessTypes[item.category]?.add(CSectionItem(item.startDistanceM, length))
                }
                else
                {
                    steepnessTypes[item.category] = arrayListOf(CSectionItem(item.startDistanceM, length))
                }
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------

    private fun addElevationViews()
    {
        setElevationChartPlottedValuesCount()
        setElevationChartMarkerView(parentActivity)
        initLayout(tableViewRowHeight)
        setAttributesToElevationChart()
        setElevationChartAxisBounds()
        loadElevationData()
        setElevationExtraInfo()
        setElevationChartTextSize()
        addClimbDetailsTableView()
    }

    // ---------------------------------------------------------------------------------------------
    
    private fun addSurfacesViews()
    {
        val surfaceTypesCount = surfacesTypes.size
        var title = ""
        SdkCall.execute { title = getSectionTitle(TRouteProfileSectionType.ESurfaces.ordinal) }
        
        if (surfaceTypesCount > 0)
        {
            setAttributesToSurfacesChart()
            setLineBarChartAxisBounds(surfacesChart)
            
            surfacesTitle.apply { 
                visibility = View.VISIBLE
                text = title
            }
            highlightedSurface.visibility = View.VISIBLE
            surfacesChart.visibility = View.VISIBLE
            loadSurfacesData()
            updateHighlightedSurfaceLabel(0.0)
            
            val highlight = surfacesChart.getHighlightByTouchPoint(0f, 0f)
            highlight?.let { surfacesChart.highlightValue(it) }
            
            setLineBarChartMarkerView(parentActivity, surfacesChart)
        }
        else
        {
            surfacesTitle.visibility = View.GONE
            highlightedSurface.visibility = View.GONE
            surfacesChart.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------------------------------------
    
    private fun addRoadsViews()
    {
        val roadTypesCount = roadsTypes.size
        var title = ""
        SdkCall.execute { title = getSectionTitle(TRouteProfileSectionType.EWays.ordinal) }
        
        if (roadTypesCount > 0)
        {
            setAttributesToRoadsChart()
            setLineBarChartAxisBounds(roadsChart)
            
            roadsTitle.apply { 
                visibility = View.VISIBLE
                text = title
            }
            highlightedRoad.visibility = View.VISIBLE
            roadsChart.visibility = View.VISIBLE
            loadRoadsData()
            updateHighlightedRoadLabel(0.0)
            
            val highlight = roadsChart.getHighlightByTouchPoint(0f, 0f)
            highlight?.let { roadsChart.highlightValue(it) }
            
            setLineBarChartMarkerView(parentActivity, roadsChart)
        }
        else
        {
            roadsTitle.visibility = View.GONE
            highlightedRoad.visibility = View.GONE
            roadsChart.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------------------------------------
    
    private fun addSteepnessViews()
    {
        val steepnessTypeCount = steepnessTypes.size
        var title = ""
        SdkCall.execute { title = getSectionTitle(TRouteProfileSectionType.ESteepnesses.ordinal) }
        
        if (steepnessTypeCount > 0)
        {
            setAttributesToSteepnessChart()
            setLineBarChartAxisBounds(steepnessChart)
            
            steepnessTitle.apply { 
                visibility = View.VISIBLE
                text = title
            }
            highlightedSteepness.visibility = View.VISIBLE
            steepnessChart.visibility = View.VISIBLE
            loadSteepnessData()
            updateHighlightedSteepnessLabel(0.0)
            
            val highlight = steepnessChart.getHighlightByTouchPoint(0f, 0f)
            highlight?.let { steepnessChart.highlightValue(it) }
            
            setLineBarChartMarkerView(parentActivity, steepnessChart)
        }
    }

    // ---------------------------------------------------------------------------------------------
    
    private fun setElevationChartPlottedValuesCount()
    {
        var chartVerticalBandsCount = 0
        SdkCall.execute { chartVerticalBandsCount = getElevationChartVerticalBandsCount() }
        
        elevationChartPlottedValuesCount = when
        {
            chartVerticalBandsCount > 9 -> 1000
            chartVerticalBandsCount > 4 -> 600
            else -> 300
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getClosestElevationChartPlottedXValue(distance: Float, y: Float): Float {
        if (elevationChart.data.dataSetCount > 0)
        {
            val lineDataSet = elevationChart.data.dataSets[0]
            val entry = lineDataSet.getEntryForXValue(distance, y)
            return entry.x
        }

        return distance
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setElevationChartMarkerView(context: Context)
    {
        val markerView = ElevationCustomMarkerView(context, R.layout.elevation_custom_marker_view, this)
        markerView.chartView = elevationChart
        elevationChart.marker = markerView
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun loadElevationData()
    {
        elevationChart.apply { 
            data = null
            highlightValue(null)
            _lastElevationChartValueSelected = null
            
            val minX = xAxis.axisMinimum
            val maxX = xAxis.axisMaximum
            
            updateElevationChart(minX, maxX)
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setElevationExtraInfo()
    {
        buttonsContainer.removeAllViews()
        
        val buttonsCount = 4
        val buttonIdsArray = IntArray(buttonsCount)
        var bmp: Bitmap? = null
        var text = ""
        
        for (i in 0 until buttonsCount)
        {
            val buttonContainer = View.inflate(parentActivity, R.layout.image_and_text_button, null).also { 
                it.id = i + 100
                buttonIdsArray[i] = it.id
            }
            
            val imageView = buttonContainer.findViewById<ImageView>(R.id.image)
            val textView = buttonContainer.findViewById<TextView>(R.id.text)
            
            SdkCall.execute { 
                bmp = getElevationProfileButtonImage(i, elevationIconSize, elevationIconSize)
                text = getElevationProfileButtonText(i)
            }
            
            imageView.apply {
                setImageBitmap(bmp)
                if (i == 2 || i == 3)
                {
                    if (parentActivity.isDarkThemeOn())
                    {
                        setColorFilter(Color.WHITE)
                    }
                    else
                    {
                        clearColorFilter()
                    }
                }
            }
            textView.text = text
            
            buttonContainer.setOnClickListener { 
                onButtonClick(i)
            }
            
            buttonsContainer.addView(buttonContainer)
        }
        
        val constraintSet = ConstraintSet().also { it.clone(buttonsContainer) }
        
        for (i in 0 until buttonsCount)
        {
            when (i)
            {
                0 ->
                {
                    constraintSet.connect(
                        buttonIdsArray[i],
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START
                    )
                    constraintSet.connect(
                        buttonIdsArray[i],
                        ConstraintSet.END,
                        buttonIdsArray[1],
                        ConstraintSet.START
                    )
                }
                
                buttonsCount - 1 ->
                {
                    constraintSet.connect(
                        buttonIdsArray[i],
                        ConstraintSet.START,
                        buttonIdsArray[i - 1],
                        ConstraintSet.END
                    )
                    constraintSet.connect(
                        buttonIdsArray[i],
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END
                    )
                }
                
                else ->
                {
                    constraintSet.connect(
                        buttonIdsArray[i],
                        ConstraintSet.START,
                        buttonIdsArray[i - 1],
                        ConstraintSet.END
                    )
                    constraintSet.connect(
                        buttonIdsArray[i],
                        ConstraintSet.END,
                        buttonIdsArray[i + 1],
                        ConstraintSet.START
                    )
                }
            }
        }
        
        constraintSet.apply {
            setHorizontalChainStyle(buttonIdsArray[0], ConstraintSet.CHAIN_SPREAD_INSIDE)
            applyTo(buttonsContainer)
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun onButtonClick(index: Int)
    {
        elevationChart.apply { 
            highlightValue(null)
            SdkCall.execute {
                onElevationChartIntervalUpdate(0.0, routeLength.toDouble(), true)
                onPushButton(index)
            }
            updateElevationChartInterval(0.0, routeLength.toDouble())
            updateElevationChart(0f, routeLength.toFloat())
            fitScreen()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun onPushButton(index: Int)
    {
        val distance = when (index)
        {
            TElevationProfileButtonType.EElevationAtDeparture.ordinal -> 0.0
            TElevationProfileButtonType.EElevationAtDestination.ordinal -> route.getTimeDistance(false)?.totalDistance?.toDouble() ?: 0.0
            TElevationProfileButtonType.EMinElevation.ordinal -> routeTerrainProfile.minElevationDistance.toDouble()
            TElevationProfileButtonType.EMaxElevation.ordinal -> routeTerrainProfile.maxElevationDistance.toDouble()
            else -> 0.0
        }
        
        val landmark = Landmark()
        val landmarkList = arrayListOf<Landmark>()
        val mapView = parentActivity.gemSurfaceView.mapView
        
        val image = ImageDatabase().getImageById(SdkImages.Engine_Misc.LocationDetails_PlacePushpin.value)
        image?.let { 
            landmark.image = image
            highlightedLandmarkList.add(landmark)
            route.getCoordinateOnRoute(distance.toInt())?.let { 
                highlightedLandmarkList.first().coordinates = it
                mapView?.deactivateHighlight()

                val settings = HighlightRenderSettings().also { settings ->
                    settings.setOptions(
                        EHighlightOptions.ShowLandmark.value
                            or EHighlightOptions.Overlap.value
                            or EHighlightOptions.NoFading.value
                    )
                }
                mapView?.activateHighlightLandmarks(landmarkList, settings)
                
                Util.postOnMain { updateElevationChartPinPosition(distance.toFloat()) }
                
                onTouchElevationChart(TTouchChartEvent.EDown.ordinal, distance)
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setElevationChartTextSize()
    {
        elevationChart.apply { 
            val textSize = 12f
            xAxis.textSize = textSize
            axisLeft.textSize = textSize
            
            var chartVerticalBandsCount = 0
            SdkCall.execute { chartVerticalBandsCount = getElevationChartVerticalBandsCount() }
            
            if (chartVerticalBandsCount > 0 && lineData != null)
            {
                for (i in 0 until chartVerticalBandsCount)
                {
                    val lineDataSet = lineData.getDataSetByIndex(i + 1)
                    lineDataSet?.let { 
                        if (it is LineDataSet)
                        {
                            it.valueTextSize = textSize
                        }
                    }
                }
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun updateElevationChart(minX: Float, maxX: Float)
    {
        val initialLineDataSet: LineDataSet
        var elevationLineDataSet: LineDataSet
        
        val initialLineValues = ArrayList<Entry>()
        val elevationArrayGroup = ArrayList<List<Entry>>()
        
        var yValues: IntArray? = null
        var verticalBandsCount = 0
        SdkCall.execute { 
            yValues = getElevationChartYValues(elevationChartPlottedValuesCount)
            verticalBandsCount = getElevationChartVerticalBandsCount()
        }
        
        val step = (maxX - minX) / (elevationChartPlottedValuesCount - 1)
        var x = minX
        val lastItemIndex = elevationChartPlottedValuesCount - 1
        
        for (i in 0 until lastItemIndex)
        {
            yValues?.get(i)?.toFloat()?.let { Entry(x, it) }?.let { initialLineValues.add(it) }
            x += step
        }
        yValues?.get(lastItemIndex)?.toFloat()?.let { Entry(maxX, it) }?.let {
            initialLineValues.add(it)
        }
        
        if (verticalBandsCount > 0)
        {
            for (i in 0 until verticalBandsCount)
            {
                val valuesLineSteepness = ArrayList<Entry>()
                
                var verticalBandMinX = 0.0
                var verticalBandMaxX = 0.0
                SdkCall.execute { 
                    verticalBandMinX = getElevationChartVerticalBandMinX(i)
                    verticalBandMaxX = getElevationChartVerticalBandMaxX(i)
                }
                
                for (entry in initialLineValues)
                {
                    if (entry.x in verticalBandMinX..verticalBandMaxX)
                    {
                        valuesLineSteepness.add(entry)
                    }
                }
                
                elevationArrayGroup.add(i, valuesLineSteepness)
            }
        }
        
        elevationChart.apply { 
            if (data != null && data.dataSetCount > 0)
            {
                initialLineDataSet = data.getDataSetByIndex(0) as LineDataSet
                initialLineDataSet.entries = initialLineValues
                
                data.lineData?.let { 
                    val count = data.dataSetCount
                    for (i in count - 1 downTo 1)
                    {
                        it.removeDataSet(i)
                    }
                    
                    if (verticalBandsCount > 0)
                    {
                        for (i in 0 until verticalBandsCount)
                        {
                            if (elevationArrayGroup[i].isNotEmpty())
                            {
                                elevationLineDataSet = LineDataSet(elevationArrayGroup[i], null)
                                customizeElevationDataSet(elevationLineDataSet, i)
                                it.addDataSet(elevationLineDataSet)
                            }
                        }
                    }
                }
                
                data.notifyDataChanged()
                notifyDataSetChanged()
            }
            else
            {
                val combinedData = CombinedData()
                val dataSets = ArrayList<ILineDataSet>()
                val bubbleData = BubbleData()
                
                initialLineDataSet = LineDataSet(initialLineValues, null)
                customizeInitialDataSet(initialLineDataSet)
                dataSets.add(initialLineDataSet)
                
                if (verticalBandsCount > 0)
                {
                    for (i in 0 until verticalBandsCount)
                    {
                        if (elevationArrayGroup[i].isNotEmpty())
                        {
                            elevationLineDataSet = LineDataSet(elevationArrayGroup[i], null)
                            customizeElevationDataSet(elevationLineDataSet, i)
                            dataSets.add(elevationLineDataSet)
                        }
                    }
                }
                
                val lineData = LineData(dataSets)
                combinedData.apply { 
                    setData(bubbleData)
                    setData(lineData)
                }
                
                data = combinedData
                invalidate()
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun updateElevationChartHighlight()
    {
        elevationChart.apply {
            _lastElevationChartValueSelected?.let { lastElevationChartValueSelected ->
                val highlightedArray = highlighted
                if (!highlightedArray.isNullOrEmpty())
                {
                    val highlight = highlightedArray[0]
                    val entry = lineData.getEntryForHighlight(highlight)

                    entry?.let {
                        if (lineData.dataSetCount > 0)
                        {
                            val x = getClosestElevationChartPlottedXValue(lastElevationChartValueSelected.x, it.y)
                            val h = Highlight(x, lastElevationChartValueSelected.y, 0)
                            h.dataIndex = 0
                            highlightValue(h)
                        }
                    }
                }
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun updateElevationChartPinPosition(distance: Float)
    {
        val threshold = (elevationChart.highestVisibleX - elevationChart.lowestVisibleX) / 2
        var leftSide = distance - threshold
        var rightSide = distance + threshold
        
        val isInsideRange = distance >= elevationChart.lowestVisibleX && distance <= elevationChart.highestVisibleX
        
        var chartYValue = 0
        SdkCall.execute { chartYValue = getElevationChartYValue(distance.toDouble()) }
        
        if (leftSide < chartMinX)
        {
            leftSide = chartMinX.toFloat()
            rightSide = leftSide + threshold * 2
        }
        
        if (rightSide > chartMaxX)
        {
            rightSide = chartMaxX.toFloat()
            leftSide = rightSide - threshold * 2
        }
        
        if (_lastElevationChartValueSelected == null)
        {
            _lastElevationChartValueSelected = Point(0f, 0f)
        }
        _lastElevationChartValueSelected?.x = distance
        _lastElevationChartValueSelected?.y = chartYValue.toFloat()
        
        if (!isInsideRange)
        {
            updateElevationChart(leftSide, rightSide)
            elevationChart.moveViewToX(leftSide)

            SdkCall.execute { onElevationChartIntervalUpdate(leftSide.toDouble(), rightSide.toDouble(), false) }

            elevationChart.invalidate()
        }
        
        val x = getClosestElevationChartPlottedXValue(distance, chartYValue.toFloat())
        val h = Highlight(x, chartYValue.toFloat(), 0)
        h.dataIndex = 0
        elevationChart.highlightValue(h)
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun updateElevationChartInterval(minX: Double, maxX: Double)
    {
        val chartMin = elevationChart.lowestVisibleX
        val chartMax = elevationChart.highestVisibleX
        
        val mapMin = minX.toFloat()
        val mapMax = maxX.toFloat()
        
        if (mapMax - mapMin != 0f)
        {
            val scaleFactor = (chartMax - chartMin) / (mapMax - mapMin)
            
            elevationChart.zoomToCenter(scaleFactor, 1f)
            updateElevationChartHighlight()
            elevationChart.moveViewToX(mapMin)
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun customizeInitialDataSet(dataSet: LineDataSet)
    {
        val colorToFill = Color.parseColor("#D964b1ff")

        dataSet.apply {
            setDrawIcons(false)
            setDrawCircles(false)
            setDrawFilled(true)
            color = ContextCompat.getColor(parentActivity, R.color.blue_color)
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
    
    private fun customizeElevationDataSet(dataSetVerticalBars: LineDataSet, count: Int)
    {
        var verticalBandText = ""
        SdkCall.execute { verticalBandText = getElevationChartVerticalBandText(count) }
        
        dataSetVerticalBars.apply {
            axisDependency = YAxis.AxisDependency.LEFT
            setDrawIcons(false)
            setDrawValues(true)
            setDrawFilled(true)
            setDrawCircles(false)
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
            
            val limit = elevationChartPlottedValuesCount / 11

            valueFormatter = object : IValueFormatter
            {
                override fun getFormattedValue(value: Float, entry: Entry?, dataSetIndex: Int, viewPortHandler: ViewPortHandler?): String
                {
                    val itemsCount = entries.size
                    if (itemsCount > 0 && itemsCount >= limit)
                    {
                        val e = dataSetVerticalBars.entries[itemsCount / 2]
                        if (e.equalTo(entry))
                        {
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

            val bgColor = when (verticalBandText)
            {
                "0" -> Color.parseColor("#FF6428")

                "1" -> Color.parseColor("#FF8C28")

                "2" -> Color.parseColor("#FFB428")

                "3" -> Color.parseColor("#FFDC28")

                "4" -> Color.parseColor("#FFF028")

                else -> Color.WHITE
            }

            fillColor = ContextCompat.getColor(parentActivity, R.color.blue_color)
            color = bgColor
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun addClimbDetailsTableView()
    {
        setDataForClimbDetailsTableView()
        tableView.addDataClickListener { _, climbClicked -> 
            climbClicked?.let { climb ->
                val startPointString = climb.startEndPoint.split(" ")[0]
                val s = climbClicked.startEndPoint.substring(climbClicked.startEndPoint.lastIndexOf("/") + 1)
                val endPointString = s.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                val startPointDouble = startPointString.toDouble() + 0.01
                val endPointDouble = endPointString.toDouble() - 0.01
                
                elevationChart.highlightValue(null)
                SdkCall.execute { 
                    onTouchElevationChart(0, chartMinX)
                    onElevationChartIntervalUpdate(startPointDouble, endPointDouble, true)
                }
                
                updateElevationChartInterval(startPointDouble, endPointDouble)
                
                removeSelection(surfacesChart)
                removeSelection(roadsChart)
                removeSelection(steepnessChart)
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setDataForClimbDetailsTableView()
    {
        var chartVerticalBandsCount = 0
        var climbDetailsText = ""
        var ratingText = ""
        var startEndPointsText = ""
        var lengthText = ""
        var startEndElevationText = ""
        var avgGradeText = ""
        
        SdkCall.execute { 
            chartVerticalBandsCount = getElevationChartVerticalBandsCount()
            climbDetailsText = getSectionTitle(TRouteProfileSectionType.EClimbDetails.ordinal)
            ratingText = getClimbDetailsColumnText(TClimbDetailsInfoType.ERating)
            startEndPointsText = getClimbDetailsColumnText(TClimbDetailsInfoType.EStartEndPoints)
            lengthText = getClimbDetailsColumnText(TClimbDetailsInfoType.ELength)
            startEndElevationText = getClimbDetailsColumnText(TClimbDetailsInfoType.EStartEndElevation)
            avgGradeText = getClimbDetailsColumnText(TClimbDetailsInfoType.EAvgGrade)
        }
        
        if (chartVerticalBandsCount > 0)
        {
            val climbDataAdapter = ClimbTableDataAdapter(
                tableView.context,
                createClimbListDetails(),
                tableView
            )
            
            val climbHeaderTableAdapter = SimpleTableHeaderAdapter(
                tableView.context,
                ratingText,
                "$startEndPointsText \n $startEndElevationText",
                lengthText,
                avgGradeText
            ).also { 
                it.setTextColor(Color.BLACK)
            }
            
            tableView.apply { 
                dataAdapter = climbDataAdapter
                headerAdapter = climbHeaderTableAdapter
            }
            
            climbDetailsTitle.apply { 
                visibility = View.VISIBLE
                text = climbDetailsText
            }
        }
        else
        {
            climbDetailsTitle.visibility = View.GONE
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun createClimbListDetails() : List<Climb>
    {
        val climbs = ArrayList<Climb>()
        var climbDetailsRowsCount = 0
        
        SdkCall.execute { 
            climbDetailsRowsCount = routeTerrainProfile.climbSections?.size ?: 0
        }
        
        for (rowCount in 0 until climbDetailsRowsCount)
        {
            var rating = ""
            var startEndPoints = ""
            var length = ""
            var startEndElevation = ""
            var avgGrade = ""
            
            SdkCall.execute { 
                rating = getClimbDetailsItemText(rowCount, TClimbDetailsInfoType.ERating)
                startEndPoints = getClimbDetailsItemText(rowCount, TClimbDetailsInfoType.EStartEndPoints)
                length = getClimbDetailsItemText(rowCount, TClimbDetailsInfoType.ELength)
                startEndElevation = getClimbDetailsItemText(rowCount, TClimbDetailsInfoType.EStartEndElevation)
                avgGrade = getClimbDetailsItemText(rowCount, TClimbDetailsInfoType.EAvgGrade)
            }
            
            val climb = Climb(rating, startEndPoints, length, startEndElevation, avgGrade)
            climbs.add(rowCount, climb)
        }
        
        return climbs
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setLineBarChartMarkerView(context: Context, chart: CombinedChart)
    {
        val markerView = LineBarMarkerView(context, R.layout.line_bar_marker_view, highlightColorUnselected, chart)
        markerView.chartView = chart
        chart.marker = markerView
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setLineDataSetAttributes(lineDataSet: LineDataSet, color: Int)
    {
        lineDataSet.apply {
            setDrawIcons(false)
            setDrawCircles(false)
            setDrawCircleHole(false)
            setDrawFilled(true)
            fillAlpha = 255
            this.color = color
            fillColor = color
            setDrawValues(false)
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(false)
            isHighlightEnabled = true
            highlightLineWidth = 2.5f
            highLightColor = highlightColorUnselected
            valueTextColor = color
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun loadSurfacesData()
    {
        val surfaceTypesCount = surfacesTypes.size
        
        val step = (barChartMaxX - barChartMinX) / (lineBarChartPlottedValuesCount - 1)
        var x = 0.0
        var limit = 0.0
        val dataSets = ArrayList<ILineDataSet>()
        
        for (i in 0 until surfaceTypesCount)
        {
            val initialLineValues = ArrayList<Entry>()
            
            var surfacePercentWidth = 0.0
            var surfaceTypeName = ""
            var surfaceTypeColor = 0
            SdkCall.execute { 
                surfacePercentWidth = getSurfacePercent(i)
                surfaceTypeName = getSurfaceText(i)
                surfaceTypeColor = getColor(getSurfaceColor(i))
            }
            
            limit += surfacePercentWidth
            
            while (x <= limit)
            {
                val lineDataEntry = Entry(x.toFloat(), lineBarYAxisMaximum)
                x += step
                    
                initialLineValues.add(lineDataEntry)
            }
            
            val lineDataSet = LineDataSet(initialLineValues, surfaceTypeName)
            setLineDataSetAttributes(lineDataSet, surfaceTypeColor)
            dataSets.add(lineDataSet)
        }
        
        val combinedData = CombinedData().also { it.setData(LineData(dataSets)) }
        surfacesChart.apply { 
            data = combinedData
            invalidate()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun loadRoadsData()
    {
        val roadTypesCount = roadsTypes.size

        val step = (barChartMaxX - barChartMinX) / (lineBarChartPlottedValuesCount - 1)
        var x = 0.0
        var limit = 0.0
        val dataSets = ArrayList<ILineDataSet>()
        
        for (i in 0 until roadTypesCount)
        {
            val initialLineValues = ArrayList<Entry>()
            
            var roadTypePercentWidth = 0.0
            var roadTypeName = ""
            var roadTypeColor = 0
            SdkCall.execute { 
                roadTypePercentWidth = getRoadPercent(i)
                roadTypeName = getRoadText(i)
                roadTypeColor = getColor(getRoadColor(i))
            }
            
            limit += roadTypePercentWidth
            
            while (x <= limit)
            {
                val lineDataEntry = Entry(x.toFloat(), lineBarYAxisMaximum)
                x += step
                
                initialLineValues.add(lineDataEntry)
            }
            
            val lineDataSet = LineDataSet(initialLineValues, roadTypeName)
            setLineDataSetAttributes(lineDataSet, roadTypeColor)
            dataSets.add(lineDataSet)
        }
        
        val combinedData = CombinedData().also { it.setData(LineData(dataSets)) }
        roadsChart.apply { 
            data = combinedData
            invalidate()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun loadSteepnessData()
    {
        val steepnessTypesCount = steepnessTypes.size

        val step = (barChartMaxX - barChartMinX) / (lineBarChartPlottedValuesCount - 1)
        var x = 0.0
        var limit = 0.0
        val dataSets = ArrayList<ILineDataSet>()
        
        for (i in 0 until steepnessTypesCount)
        {
            val initialLineValues = ArrayList<Entry>()

            var steepnessTypePercentWidth = 0.0
            var steepnessTypeName = ""
            var steepnessTypeColor = 0
            SdkCall.execute { 
                steepnessTypePercentWidth = getSteepnessPercent(i)
                steepnessTypeName = getSteepnessText(i)
                steepnessTypeColor = getColor(getSteepnessColor(i))
            }

            limit += steepnessTypePercentWidth

            while (x <= limit)
            {
                val lineDataEntry = Entry(x.toFloat(), lineBarYAxisMaximum)
                x += step

                initialLineValues.add(lineDataEntry)
            }

            val lineDataSet = LineDataSet(initialLineValues, steepnessTypeName)
            setLineDataSetAttributes(lineDataSet, steepnessTypeColor)
            dataSets.add(lineDataSet)
        }

        val combinedData = CombinedData().also { it.setData(LineData(dataSets)) }
        steepnessChart.apply {
            data = combinedData
            invalidate()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun updateHighlightedSurfaceLabel(percent: Double)
    {
        val surfaceTypesCount = surfacesTypes.size
        
        var index = -1
        var d = 0.0
        
        for (i in 0 until surfaceTypesCount)
        {
            var surfacePercentWidth = 0.0
            SdkCall.execute { surfacePercentWidth = getSurfacePercent(i)  }
            d += surfacePercentWidth
            if (percent <= d)
            {
                index = i
                break
            }
        }
        
        if (index in 0 until surfaceTypesCount)
        {
            var surfaceTypeName = ""
            SdkCall.execute { surfaceTypeName = getSurfaceText(index) }
            highlightedSurface.text = surfaceTypeName
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun updateHighlightedRoadLabel(percent: Double)
    {
        val roadTypesCount = roadsTypes.size
        
        var index = -1
        var d = 0.0
        
        for (i in 0 until roadTypesCount)
        {
            var roadTypePercentWidth = 0.0
            SdkCall.execute { roadTypePercentWidth = getRoadPercent(i) }
            d += roadTypePercentWidth
            if (percent <= d)
            {
                index = i
                break
            }
        }
        
        if (index in 0 until roadTypesCount)
        {
            var roadTypeName = ""
            SdkCall.execute { roadTypeName = getRoadText(index) }
            highlightedRoad.text = roadTypeName
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun updateHighlightedSteepnessLabel(percent: Double)
    {
        val steepnessTypesCount = steepnessTypes.size

        var index = -1
        var d = 0.0
        
        for (i in 0 until steepnessTypesCount)
        {
            var steepnessTypePercentWidth = 0.0
            SdkCall.execute { steepnessTypePercentWidth = getSteepnessPercent(i) }
            d += steepnessTypePercentWidth
            if (percent <= d)
            {
                index = i
                break
            }
        }
        
        if (index in 0 until steepnessTypesCount)
        {
            var steepnessTypeName = ""
            var steepnessBmp: Bitmap? = null
            
            SdkCall.execute { 
                steepnessTypeName = getSteepnessText(index)
                steepnessBmp = getSteepnessImage(index, steepnessIconSize, steepnessIconSize)
            }
            
            highlightedSteepness.text = steepnessTypeName
            steepnessImage.setImageBitmap(steepnessBmp)
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun initLayout(tableViewRowHeight: Int)
    {
        var chartVerticalBandsCount = 0
        SdkCall.execute { chartVerticalBandsCount = getElevationChartVerticalBandsCount() }
        
        tableView.layoutParams.apply {
            width = LinearLayout.LayoutParams.MATCH_PARENT
            height = if (chartVerticalBandsCount > 0)
            {
                tableViewRowHeight + chartVerticalBandsCount * (tableViewRowHeight + 1)
            }
            else
            {
                0
            }
        }
        tableView.requestLayout()
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setAttributesToElevationChart()
    {
        elevationChart.apply { 
            parent.parent.requestDisallowInterceptTouchEvent(true)
            isScaleXEnabled = true
            isScaleYEnabled = false
            isDragEnabled = false
            isDragXEnabled = true
            isDragYEnabled = false
            onChartGestureListener = this@RouteProfile
            setOnChartValueSelectedListener(this@RouteProfile)
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
    
    private fun setAttributesToSurfacesChart()
    {
        setLineBarChartAttributes(surfacesChart)
        
        surfacesChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener 
        {
            override fun onValueSelected(e: Entry?, h: Highlight?)
            {
                enableSelection(surfacesChart)
                
                surfacesChart.highlightValue(h)
                
                val maxX = surfacesChart.xAxis.axisMaximum.toDouble()
                val percent = e?.x?.div(maxX)
                SdkCall.execute { percent?.let { onTouchSurfacesChart(0, it) } }

                percent?.let { updateHighlightedSurfaceLabel(it) }
                
                removeSelection(roadsChart)
                removeSelection(steepnessChart)
            }

            override fun onNothingSelected() = Unit
        })
    }
    
    // ---------------------------------------------------------------------------------------------

    private fun setAttributesToRoadsChart()
    {
        setLineBarChartAttributes(roadsChart)

        roadsChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener
        {
            override fun onValueSelected(e: Entry?, h: Highlight?)
            {
                enableSelection(roadsChart)

                roadsChart.highlightValue(h)

                val maxX = roadsChart.xAxis.axisMaximum.toDouble()
                val percent = e?.x?.div(maxX)
                SdkCall.execute { percent?.let { onTouchRoadsChart(0, it) } }

                percent?.let { updateHighlightedRoadLabel(it) }

                removeSelection(surfacesChart)
                removeSelection(steepnessChart)
            }

            override fun onNothingSelected() = Unit
        })
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setAttributesToSteepnessChart()
    {
        setLineBarChartAttributes(steepnessChart)
        
        steepnessChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener
        {
            override fun onValueSelected(e: Entry?, h: Highlight?)
            {
                enableSelection(steepnessChart)
                
                steepnessChart.highlightValue(h)
                
                val maxX = steepnessChart.xAxis.axisMaximum.toDouble()
                val percent = e?.x?.div(maxX)
                SdkCall.execute { percent?.let { onTouchSteepnessesChart(0, it) } }

                percent?.let { updateHighlightedSteepnessLabel(it) }
                
                removeSelection(surfacesChart)
                removeSelection(roadsChart)
            }

            override fun onNothingSelected() = Unit
        })
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun onElevationChartIntervalUpdate(minX: Double, maxX: Double, updateMap: Boolean)
    {
        if (updateMap)
        {
            if (abs(minX - chartMinX) > 0.000001 || abs(maxX - chartMaxX) > 0.000001)
            {
                zoomRoute(minX, maxX)
            }
        }
        else
        {
            chartMinX = minX
            chartMaxX = maxX

            removeHighlightedSurfacePathsFromMap()
            removeHighlightedRoadPathsFromMap()
            removeHighlightedSteepnessPathsFromMap()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun onTouchElevationChart(event: Int, x: Double)
    {
        removeHighlightedSurfacePathsFromMap()
        removeHighlightedRoadPathsFromMap()
        removeHighlightedSteepnessPathsFromMap()
        
        val mapView = parentActivity.gemSurfaceView.mapView 
        mapView?.deactivateHighlight()
        
        if (event == TTouchChartEvent.EDown.ordinal || event == TTouchChartEvent.EMove.ordinal)
        {
            highlightedLandmarkList.clear()
            
            val landmark = Landmark()
            ImageDatabase().getImageById(SdkImages.Engine_Misc.LocationDetails_PlacePushpin.value)?.let { 
                landmark.image = it
            }
            highlightedLandmarkList.add(landmark)
            
            val xM = x.toInt().coerceIn(0, route.getTimeDistance(false)?.totalDistance)
            
            route.getCoordinateOnRoute(xM)?.let { 
                highlightedLandmarkList.first().coordinates = it
            }
            
            if (previousTouchXMeters == xM && event == TTouchChartEvent.EDown.ordinal)
            {
                mapView?.deactivateHighlight()
                
                val settings = HighlightRenderSettings().also { 
                    it.setOptions(
                        EHighlightOptions.ShowContour.value  
                        or EHighlightOptions.Overlap.value
                        or EHighlightOptions.NoFading.value
                    )
                }
                
                val task = {
                    parentActivity.gemSurfaceView.mapView?.activateHighlightLandmarks(highlightedLandmarkList, settings)
                }
                Executors.newSingleThreadScheduledExecutor().schedule(task, 500, TimeUnit.MILLISECONDS)
            }
            else
            {
                val settings = HighlightRenderSettings().also {
                    it.setOptions(
                        EHighlightOptions.ShowLandmark.value
                            or EHighlightOptions.Overlap.value
                            or EHighlightOptions.NoFading.value
                    )
                }
                mapView?.activateHighlightLandmarks(highlightedLandmarkList, settings)
                
                previousTouchXMeters = if (event == TTouchChartEvent.EDown.ordinal)
                {
                    xM
                }
                else
                {
                    -1
                }
            }
            
            val viewport = mapView?.viewport
            val xy = highlightedLandmarkList.first().coordinates?.let { 
                mapView?.transformWgsToScreen(it)
            }
            
            viewport?.let { 
                xy?.let { xy -> 
                    if (!pointInRectangle(it, xy.x, xy.y))
                    {
                        zoomRoute(chartMinX, chartMaxX)
                    }
                }
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun onTouchSurfacesChart(event: Int, x: Double)
    {
        if (event == TTouchChartEvent.EUp.ordinal)
        {
            return
        }
        
        if (event == TTouchChartEvent.EDown.ordinal)
        {
            removeHighlightedRoadPathsFromMap()
            removeHighlightedSteepnessPathsFromMap()
            
            if (highlightedSurfacePaths.isEmpty())
            {
                parentActivity.zoomToRoute()

                chartMinX = 0.0
                chartMaxX = routeLength.toDouble()

                 Util.postOnMain { updateElevationChartInterval(chartMinX, chartMaxX) } 
            }
        }
        
        val mapView = parentActivity.gemSurfaceView.mapView?.also { it.deactivateHighlight() }
        
        var percent: Double
        var length: Double
        var prevPercent = 0.0
        
        for (item in surfacesTypes)
        {
            length = 0.0
            for (it in item.value)
            {
                length += it.mLengthM
            }
            
            percent = prevPercent + length / routeLength
            
            if (x in prevPercent..percent)
            {
                if (highlightedSurfaceType != item.key.value)
                {
                    removeHighlightedSurfacePathsFromMap()
                    highlightedSurfaceType = item.key.value
                }
                else
                {
                    break
                }
                
                val pathsCollection = mapView?.preferences?.paths
                for (it in item.value)
                {
                    val path = route.getPath(it.mStartDistanceM, it.mStartDistanceM + it.mLengthM)
                    path?.let { 
                        pathsCollection?.add(it, highlightPathsColor, highlightPathsColor)
                        highlightedSurfacePaths.add(it)
                    }
                }
                
                break
            }
            
            prevPercent = percent
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun onTouchRoadsChart(event: Int, x: Double)
    {
        if (event == TTouchChartEvent.EUp.ordinal)
        {
            return
        }
        
        if (event == TTouchChartEvent.EDown.ordinal)
        {
            removeHighlightedSurfacePathsFromMap()
            removeHighlightedSteepnessPathsFromMap()
            
            if (highlightedRoadPaths.isEmpty())
            {
                parentActivity.zoomToRoute()

                chartMinX = 0.0
                chartMaxX = routeLength.toDouble()

                 Util.postOnMain { updateElevationChartInterval(chartMinX, chartMaxX) } 
            }
        }

        val mapView = parentActivity.gemSurfaceView.mapView?.also { it.deactivateHighlight() }

        var percent: Double
        var length: Double
        var prevPercent = 0.0
        
        for (item in roadsTypes)
        {
            length = 0.0
            for (it in item.value)
            {
                length += it.mLengthM
            }
            
            percent = prevPercent + length / routeLength
            
            if (x in prevPercent..percent)
            {
                if (highlightedRoadType != item.key.value)
                {
                    removeHighlightedRoadPathsFromMap()
                    highlightedRoadType = item.key.value
                }
                else
                {
                    break
                }
                
                val pathsCollection = mapView?.preferences?.paths
                for (it in item.value)
                {
                    val path = route.getPath(it.mStartDistanceM, it.mStartDistanceM + it.mLengthM)
                    path?.let { 
                        pathsCollection?.add(it, highlightPathsColor, highlightPathsColor)
                        highlightedRoadPaths.add(it)
                    }
                }
                
                break
            }
            
            prevPercent = percent
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun onTouchSteepnessesChart(event: Int, x: Double)
    {
        if (event == TTouchChartEvent.EUp.ordinal)
        {
            return
        }
        
        if (event == TTouchChartEvent.EDown.ordinal)
        {
            removeHighlightedSurfacePathsFromMap()
            removeHighlightedRoadPathsFromMap()
            
            if (highlightedSteepnessPaths.isEmpty())
            {
                parentActivity.zoomToRoute()
                
                chartMinX = 0.0
                chartMaxX = routeLength.toDouble()

                 Util.postOnMain { updateElevationChartInterval(chartMinX, chartMaxX) } 
            }
        }

        val mapView = parentActivity.gemSurfaceView.mapView?.also { it.deactivateHighlight() }

        var percent: Double
        var length: Double
        var prevPercent = 0.0
        
        for (item in steepnessTypes)
        {
            length = 0.0
            for (it in item.value)
            {
                length += it.mLengthM
            }
            
            percent = prevPercent + length / routeLength
            
            if (x in prevPercent..percent)
            {
                if (highlightedSteepnessType != item.key)
                {
                    removeHighlightedSteepnessPathsFromMap()
                    highlightedSteepnessType = item.key
                }
                else
                {
                    break
                }
                
                val pathsCollection = mapView?.preferences?.paths
                for (it in item.value)
                {
                    val path = route.getPath(it.mStartDistanceM, it.mStartDistanceM + it.mLengthM)
                    path?.let { 
                        pathsCollection?.add(it, highlightPathsColor, highlightPathsColor)
                        highlightedSteepnessPaths.add(it)
                    }
                }
                
                break
            }
            
            prevPercent = percent
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun removeHighlightedSurfacePathsFromMap()
    {
        removeHighlightedPathsFromMap(highlightedSurfacePaths)
        highlightedSurfaceType = -1
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun removeHighlightedRoadPathsFromMap()
    {
        removeHighlightedPathsFromMap(highlightedRoadPaths)
        highlightedRoadType = -1
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun removeHighlightedSteepnessPathsFromMap()
    {
        removeHighlightedPathsFromMap(highlightedSteepnessPaths)
        highlightedSteepnessType = -1
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun removeHighlightedPathsFromMap(paths : ArrayList<Path>)
    {
        parentActivity.gemSurfaceView.mapView?.let {
            if (paths.isNotEmpty())
            {
                val pathCollection = it.preferences?.paths
                pathCollection?.let {
                    for (path in paths)
                    {
                        pathCollection.remove(path)
                    }
                }
                paths.clear()
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun zoomRoute(minX: Double, maxX: Double)
    {
        removeHighlightedSurfacePathsFromMap()
        removeHighlightedRoadPathsFromMap()
        removeHighlightedSteepnessPathsFromMap()
        
        val mapView = parentActivity.gemSurfaceView.mapView
        
        mapView?.preferences?.setMapViewPerspective(EMapViewPerspective.TwoDimensional)
        
        var automaticZoomToRoute = false
        if (minX == 0.0)
        {
            val max = routeLength
            if (abs(max - maxX) < 0.0001)
            {
                automaticZoomToRoute = true
            }
        }
        
        if (automaticZoomToRoute)
        {
            val mainRoute = mapView?.preferences?.routes?.mainRoute
            parentActivity.flyToRoute(mainRoute)
        }
        else
        {
            mapView?.centerOnDistRoute(route, minX.toInt(), maxX.toInt(), Rect(), Animation())
        }

        chartMinX = minX
        chartMaxX = maxX
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSurfacePercent(index: Int): Double
    {
        var auxIndex = index
        if (routeLength > 0 && index in 0 until surfacesTypes.size)
        {
            for (surface in surfacesTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    var length = 0.0
                    for (it in surface.value)
                    {
                        length += it.mLengthM
                    }
                    
                    return length / routeLength
                }
            }
        }
        
        return 0.0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSurfaceText(index: Int) : String
    {
        var auxIndex = index
        if (index in 0 until surfacesTypes.size)
        {
            for (surface in surfacesTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    val temp = getSurfaceName(surface.key)
                    return String.format("%s (%.2f%%)", temp, getSurfacePercent(index) * 100)
                }
            }
        }
        
        return ""
    }

    // ---------------------------------------------------------------------------------------------

    private fun getSurfaceColor(index: Int) : Int
    {
        var auxIndex = index
        if (index in 0 until surfacesTypes.size)
        {
            for (surface in surfacesTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    return getSurfaceColor(surface.key)
                }
            }
        }
        
        return 0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getClimbDetailsColumnText(type: TClimbDetailsInfoType) : String = when (type)
    {
        TClimbDetailsInfoType.ERating -> GemUtil.getUIString(EStringIds.eStrRating)
        TClimbDetailsInfoType.EStartEndPoints -> GemUtil.getUIString(EStringIds.eStartEndPoints)
        TClimbDetailsInfoType.EStartEndElevation -> GemUtil.getUIString(EStringIds.eStrStartEndElevation)
        TClimbDetailsInfoType.ELength -> GemUtil.getUIString(EStringIds.eStrLength)
        TClimbDetailsInfoType.EAvgGrade -> GemUtil.getUIString(EStringIds.eStrAvgGrade)
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getClimbDetailsItemText(row: Int, type: TClimbDetailsInfoType) : String
    {
        var climbDetailsRowsCount = 0
        SdkCall.execute { climbDetailsRowsCount = routeTerrainProfile.climbSections?.size ?: 0 }
        
        if (row in 0 until climbDetailsRowsCount)
        {
            return when (type)
            {
                TClimbDetailsInfoType.ERating -> getElevationChartVerticalBandText(row)
                TClimbDetailsInfoType.EStartEndPoints -> String.format(
                    "%.2f %s/%.2f %s", 
                    getElevationChartVerticalBandMinX(row), 
                    "m",
                    getElevationChartVerticalBandMaxX(row),
                    "m"
                )
                TClimbDetailsInfoType.EStartEndElevation -> String.format(
                    "%d %s/%d %s",
                    getElevationChartYValue(getElevationChartVerticalBandMinX(row)),
                    "m",
                    getElevationChartYValue(getElevationChartVerticalBandMaxX(row)),
                    "m"
                )
                TClimbDetailsInfoType.ELength -> String.format(
                    "%.2f %s",
                    getElevationChartVerticalBandMaxX(row) - getElevationChartVerticalBandMinX(row),
                    "m"
                )
                TClimbDetailsInfoType.EAvgGrade -> String.format(
                    "%.1f%%",
                    routeTerrainProfile.climbSections?.get(row)?.slope
                )
            }
        }
        
        return ""
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartMinValueY() : Int
    {
        var diff = routeTerrainProfile.maxElevation - routeTerrainProfile.minElevation
        diff = 0.1f * abs(diff)

        val round = if (diff <= 5) 2 else if (diff <= 25) 10 else 50
        var intDiff = 1f.coerceAtMost(diff).toInt()
        val elevation = (routeTerrainProfile.minElevation - intDiff).toDouble()
        var result = round(elevation).toInt()
        
        if (result < 0)
        {
            if (routeTerrainProfile.minElevation >= 0)
            {
                result = 0
            }
            else
            {
                diff = routeTerrainProfile.minElevation
                diff = 0.1f * abs(diff)
                intDiff = diff.coerceIn(1f, 100f).toInt()
                
                result = ((routeTerrainProfile.minElevation - intDiff).toInt())
            }
        }

        if ((result % round) != 0)
        {
            result = if (result < 0)
            {
                ((result - round) / round) * round
            }
            else
            {
                (result / round) * round
            }
        }
        
        return result
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartMaxValueY(): Int
    {
        var diff = routeTerrainProfile.maxElevation - routeTerrainProfile.minElevation
        diff = 0.1f * abs(diff)
        
        val round = if (diff <= 5) 2 else if (diff <= 25) 10 else 50
        val intDiff = 1f.coerceAtMost(diff).toInt()
        val elevation = routeTerrainProfile.maxElevation + intDiff
        var result = round(elevation).toInt()
        
        if (result % round != 0)
        {
            result = if (result < 0)
            {
                (result / round) * round
            }
            else
            {
                ((result + round) / round) * round
            }
        }
        
        return result
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartYValue(distance: Double) : Int
    {
        return routeTerrainProfile.getElevation(distance.toInt()).toInt() 
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartYValues(valuesCount: Int) : IntArray
    {
        val yValuesArray = IntArray(valuesCount)
        
        val samples = routeTerrainProfile.getElevationSamples(valuesCount, chartMinX.toInt(), chartMaxX.toInt())
        samples?.first?.size?.let { 
            if (valuesCount <= it)
            {
                for (i in 0 until valuesCount)
                {
                    yValuesArray[i] = (samples.first[i]).toInt()
                }
            }
        }
        
        return yValuesArray
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartVerticalBandText(index: Int) : String
    {
        routeTerrainProfile.climbSections?.let { 
            if (index in 0 until it.size)
            {
                return String.format("%d", it[index].grade.value)
            }
        }
        
        return ""
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartVerticalBandMinX(index: Int) : Double
    {
        routeTerrainProfile.climbSections?.let { 
            if (index in 0 until it.size)
            {
                return it[index].startDistanceM.toDouble()
            }
        }
        
        return 0.0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartVerticalBandMaxX(index: Int) : Double
    {
        routeTerrainProfile.climbSections?.let {
            if (index in 0 until it.size)
            {
                return it[index].endDistanceM.toDouble()
            }
        }

        return 0.0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationProfileButtonImage(index: Int, width: Int, height: Int) : Bitmap? = when (index)
    {
        TElevationProfileButtonType.EElevationAtDeparture.ordinal -> GemUtilImages.asBitmap(
            SdkImages.Engine_Misc.WaypointFlag_PointStart.value,
            width,
            height
        )
        
        TElevationProfileButtonType.EElevationAtDestination.ordinal -> GemUtilImages.asBitmap(
            SdkImages.Engine_Misc.WaypointFlag_PointFinish.value,
            width,
            height
        )

        TElevationProfileButtonType.EMinElevation.ordinal -> GemUtilImages.asBitmap(
            SdkImages.UI.HeightProfile_Lowest_Point.value,
            width,
            height
        )

        TElevationProfileButtonType.EMaxElevation.ordinal -> GemUtilImages.asBitmap(
            SdkImages.UI.HeightProfile_Highest_Point.value,
            width,
            height
        )
        
        else -> null
    } 
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationProfileButtonText(index: Int) : String = when (index)
    {
        TElevationProfileButtonType.EElevationAtDeparture.ordinal -> getElevationString(routeTerrainProfile.getElevation(0).toInt())
        TElevationProfileButtonType.EElevationAtDestination.ordinal -> getElevationString(routeTerrainProfile.getElevation(route.getTimeDistance(false)?.totalDistance ?: 0).toInt())
        TElevationProfileButtonType.EMinElevation.ordinal -> getElevationString(routeTerrainProfile.minElevation.toInt())
        TElevationProfileButtonType.EMaxElevation.ordinal -> getElevationString(routeTerrainProfile.maxElevation.toInt())
        else -> ""
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationString(distance: Int) = String.format("%d %s", distance, GemUtil.getUIString(EStringIds.eStrMeter))
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSurfaceName(type: ESurfaceType) : String = when (type)
    {
        ESurfaceType.Asphalt -> GemUtil.getUIString(EStringIds.eStrAsphalt)
        ESurfaceType.Paved -> GemUtil.getUIString(EStringIds.eStrPaved)
        ESurfaceType.Unpaved -> GemUtil.getUIString(EStringIds.eStrUnpaved)
        ESurfaceType.Unknown -> GemUtil.getUIString(EStringIds.eStrUnknown)
        else -> ""
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSurfaceColor(type: ESurfaceType) : Int = when (type)
    {
        ESurfaceType.Asphalt -> Rgba(127, 137, 149, 255).value
        ESurfaceType.Paved -> Rgba(212, 212, 212, 255).value
        ESurfaceType.Unpaved -> Rgba(157, 133, 104, 255).value
        ESurfaceType.Unknown -> Rgba(0, 0, 0, 255).value
        else -> 0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getRoadPercent(index: Int) : Double
    {
        var auxIndex = index
        if (routeLength > 0 && index in 0 until roadsTypes.size)
        {
            for (surface in roadsTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    var length = 0.0
                    for (it in surface.value)
                    {
                        length += it.mLengthM
                    }

                    return length / routeLength
                }
            }
        }

        return 0.0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getRoadText(index: Int) : String
    {
        var auxIndex = index
        if (index in 0 until roadsTypes.size)
        {
            for (road in roadsTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    val temp = getRoadName(road.key)
                    return String.format("%s (%.2f%%)", temp, getRoadPercent(index) * 100)
                }
            }
        }

        return ""
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getRoadColor(index: Int) : Int
    {
        var auxIndex = index
        if (index in 0 until roadsTypes.size)
        {
            for (road in roadsTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    return getRoadColor(road.key)
                }
            }
        }

        return 0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getRoadColor(type: ERoadType) : Int = when (type)
    {
        ERoadType.Motorways -> Rgba(242, 144, 99, 255).value
        ERoadType.StateRoad -> Rgba(242, 216, 99, 255).value
        ERoadType.Road -> Rgba(153, 163, 175, 255).value
        ERoadType.Street -> Rgba(175, 185, 193, 255).value
        ERoadType.Cycleway -> Rgba(15, 175, 135, 255).value
        ERoadType.Path -> Rgba(196, 200, 211, 255).value
        ERoadType.SingleTrack -> Rgba(166, 133, 96, 255).value
        
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getRoadName(type: ERoadType) : String = when (type)
    {
        ERoadType.Motorways -> GemUtil.getUIString(EStringIds.eStrMotorway)
        ERoadType.StateRoad -> GemUtil.getUIString(EStringIds.eStrStateRoad)
        ERoadType.Road -> GemUtil.getUIString(EStringIds.eStrRoad)
        ERoadType.Street -> GemUtil.getUIString(EStringIds.eStrStreet)
        ERoadType.Cycleway -> GemUtil.getUIString(EStringIds.eStrCycleway)
        ERoadType.Path -> GemUtil.getUIString(EStringIds.eStrPath)
        ERoadType.SingleTrack -> GemUtil.getUIString(EStringIds.eStrSingleTrack)
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSteepnessPercent(index: Int) : Double
    {
        var auxIndex = index
        if (routeLength > 0 && index in 0 until steepnessTypes.size)
        {
            for (steepness in steepnessTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    var length = 0.0
                    for (it in steepness.value)
                    {
                        length += it.mLengthM
                    }

                    return length / routeLength
                }
            }
        }

        return 0.0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSteepnessText(index: Int) : String
    {
        var auxIndex = index
        if (index in 0 until steepnessTypes.size)
        {
            for (steepness in steepnessTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    val temp = getSteepnessName(steepness.key)
                    return String.format("%s (%.2f%%)", temp, getSteepnessPercent(index) * 100)
                }
            }
        }

        return ""
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSteepnessColor(index: Int) : Int
    {
        var auxIndex = index
        if (index in 0 until steepnessTypes.size)
        {
            for (steepness in steepnessTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    return when (steepness.key)
                    {
                        0 -> { // < -16
                            Rgba(4, 120, 8, 255).value
                        }

                        1 -> { // [-16, -10]
                            Rgba(38, 151, 41, 255).value
                        }

                        2 -> { // [-10, -7]
                            Rgba(73, 183, 76, 255).value
                        }

                        3 -> { // [-7, -4]
                            Rgba(112, 216, 115, 255).value
                        }

                        4 -> { // [-4, -1]
                            Rgba(154, 250, 156, 255).value
                        }

                        5 -> { // [-1, 1]
                            Rgba(255, 197, 142, 255).value
                        }

                        6 -> { // [1, 4]
                            Rgba(240, 141, 141, 255).value
                        }

                        7 -> { // [4, 7]
                            Rgba(220, 105, 106, 255).value
                        }

                        8 -> { // [7, 10]
                            Rgba(201, 73, 72, 255).value
                        }

                        9 -> { // [10, 16]
                            Rgba(182, 43, 42, 255).value
                        }

                        10 -> { // > 16
                            Rgba(164, 16, 15, 255).value
                        }

                        else -> {
                            0
                        }
                    }
                }
            }
        }

        return 0
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSteepnessImage(index: Int, width: Int, height: Int) : Bitmap?
    {
        var imageType = TSteepnessImageType.EUnknown
        var auxIndex = index
        
        if (index in 0 until steepnessTypes.size)
        {
            for (steepness in steepnessTypes)
            {
                --auxIndex
                if (auxIndex < 0)
                {
                    if (steepness.key in 0 until 5)
                    {
                        imageType = TSteepnessImageType.EDown
                        break
                    }
                    
                    if (steepness.key == 5)
                    {
                        imageType = TSteepnessImageType.EPlain
                        break
                    }
                    
                    imageType = TSteepnessImageType.EUp
                    break
                }
            }
        }
        
        return when (imageType)
        {
            TSteepnessImageType.EUp -> GemUtilImages.asBitmap(SdkImages.UI.SteepnessUp.value, width, height)
            TSteepnessImageType.EDown -> GemUtilImages.asBitmap(SdkImages.UI.SteepnessDown.value, width, height)
            TSteepnessImageType.EPlain -> GemUtilImages.asBitmap(SdkImages.UI.SteepnessPlain.value, width, height)
            TSteepnessImageType.EUnknown -> null
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSteepnessName(index: Int) : String = when (index)
    {
        0 -> { // < -16
            "16%+"
        }

        1 -> { // [-16, -10]
            "10-15%"
        }

        2 -> { // [-10, -7]
            "7-9%"
        }

        3 -> { // [-7, -4]
            "4-6%"
        }

        4 -> { // [-4, -1]
            "1-3%"
        }

        5 -> { // [-1, 1]
            "0%"
        }

        6 -> { // [1, 4]
            "1-3%"
        }

        7 -> { // [4, 7]
            "4-6%"
        }

        8 -> { // [7, 10]
            "7-9%"
        }

        9 -> { // [10, 16]
            "10-15%"
        }

        10 -> { // > 16
            "16%+"
        }

        else -> {
            String()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setLineBarChartAttributes(chart: CombinedChart)
    {
        chart.apply {
            setBackgroundColor(Color.WHITE)
            setExtraOffsets(0f, 0f, 0f, 0f)
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

            legend.isEnabled = false
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.xOffset = 0f
            legend.isWordWrapEnabled = true

            layoutParams?.height = parentActivity.resources.getDimension(R.dimen.bar_chart_height).toInt()
        }
    }
    
    // ---------------------------------------------------------------------------------------------

    private fun setLineBarChartAxisBounds(chart: CombinedChart)
    {
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
        yAxis.axisMaximum = 150f
        yAxis.setDrawZeroLine(false)
        yAxis.setDrawLabels(false)
        yAxis.setDrawAxisLine(false)
        yAxis.setDrawGridLines(false)
        yAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
        yAxis.isEnabled = false

        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun setElevationChartAxisBounds()
    {
        elevationChart.apply { 
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            
            val horizontalAxisUnit = "m"
            val verticalAxisUnit = "m"
            var chartMinValueY = 0f
            var chartMaxValueY = 0f
            val zoomThresholdDistX = 0.5f
            SdkCall.execute { 
                chartMinValueY = getElevationChartMinValueY().toFloat()
                chartMaxValueY = getElevationChartMaxValueY().toFloat()
            }

            xAxis.setDrawLabels(true)
            xAxis.axisMinimum = chartMinX.toFloat()
            xAxis.axisMaximum = chartMaxX.toFloat()
            xAxis.setLabelCount(4, true)
            xAxis.granularity = 0.1f

            xAxis.valueFormatter = IAxisValueFormatter { value, axis ->
                val df = DecimalFormat("#.#")
                val tempValue = (highestVisibleX - lowestVisibleX) / (axis.labelCount + 2)

                if (value > highestVisibleX - tempValue && value < highestVisibleX + tempValue)
                {
                    df.format(value.toDouble()) + " " + horizontalAxisUnit
                }
                else
                {
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
                if (value == chartMaxValueY)
                {
                    df.format(value.toDouble()) + " " + verticalAxisUnit
                }
                else
                {
                    df.format(value.toDouble())
                }
            }
            
            axisRight.isEnabled = false
            
            val textColor = if (parentActivity.isDarkThemeOn())
            {
                Color.WHITE
            }
            else
            {
                Color.BLACK
            }

            yAxis.textColor = textColor
            xAxis.textColor = textColor

            setVisibleXRangeMinimum(zoomThresholdDistX)
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun refreshDataSetHighlightColor(chart: CombinedChart, color: Int)
    {
        val count = chart.data.dataSetCount
        if (count > 0)
        {
            for (i in 0 until count)
            {
                (chart.data.getDataSetByIndex(i) as LineDataSet).highLightColor = color 
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun enableSelection(chart: CombinedChart)
    {
        refreshDataSetHighlightColor(chart, highlightColorSelected)
        
        val marker = chart.marker
        if (marker != null && marker is LineBarMarkerView)
        {
            marker.setColor(highlightColorSelected)
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun removeSelection(chart: CombinedChart)
    {
        refreshDataSetHighlightColor(chart, highlightColorUnselected)
        
        val marker = chart.marker
        if (marker != null && marker is LineBarMarkerView)
        {
            marker.setColor(highlightColorUnselected)
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun getSectionTitle(section: Int): String = when (section)
    {
        TRouteProfileSectionType.EElevation.ordinal -> GemUtil.getUIString(EStringIds.eStrElevation)
        TRouteProfileSectionType.EClimbDetails.ordinal -> GemUtil.getUIString(EStringIds.eStrClimbDetails)
        TRouteProfileSectionType.EWays.ordinal -> GemUtil.getUIString(EStringIds.eStrWays)
        TRouteProfileSectionType.ESurfaces.ordinal -> GemUtil.getUIString(EStringIds.eStrSurfaces)
        TRouteProfileSectionType.ESteepnesses.ordinal -> GemUtil.getUIString(EStringIds.eStrSteepness)
        else -> ""
    }

    // ---------------------------------------------------------------------------------------------

    private fun getColor(gemSdkColor: Int): Int {
        val r = 0x000000ff and gemSdkColor
        val g = 0x000000ff and (gemSdkColor shr 8)
        val b = 0x000000ff and (gemSdkColor shr 16)
        val a = 0x000000ff and (gemSdkColor shr 24)

        return Color.argb(a, r, g, b)
    }

    // ---------------------------------------------------------------------------------------------
    
    private fun getElevationChartVerticalBandsCount(): Int = routeTerrainProfile.climbSections?.size ?: 0
    
    // ---------------------------------------------------------------------------------------------
    
    private fun pointInRectangle(rectangle: Rect, x: Int, y: Int): Boolean
    {
        return x >= rectangle.x && x <= rectangle.right && y >= rectangle.y && y <= rectangle.bottom
    }
    
    // ---------------------------------------------------------------------------------------------

    internal class Point(var x: Float, var y: Float)

    // ---------------------------------------------------------------------------------------------
    
    companion object
    {
        private const val CHART_OFFSET_TOP = 23
        private const val CHART_OFFSET_RIGHT = 17
        private const val CHART_OFFSET_BOTTOM = 20
    }
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
