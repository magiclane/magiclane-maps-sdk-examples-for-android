package com.generalmagic.sdk.examples.demo.activities.routeprofile

import android.graphics.Bitmap
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.d3scene.*
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.BaseUiRouteController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.common.PickLocationController
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.TutorialsOpener
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.ERoadType
import com.generalmagic.sdk.routesandnavigation.ESurfaceType
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.SdkUtil.getUIString
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.round

object GEMRouteProfileView {
    private var mCrtMinXInUnitType = 0.0
    private var mCrtMaxXInUnitType = 0.0
    private var mAltitudeFactor = 0.0
    private lateinit var mRoute: Route
    private var mCrtUnitType = TUnitType.EMeter
    private lateinit var mMapView: MapView
    private var mHighlightedLmkList = arrayListOf<Landmark>()
    private var mPrevTouchXMeters = -1
    private lateinit var mTimeOutOperation: ScheduledFuture<*>
    private var mDeactivateHighlights = false
    private var mRouteLengthInMeters = 0

    private var mHighlightedSurfaceType = -1
    private var mHighlightedSurfacePaths = arrayListOf<Path>()
    private var mHighlightedWayType = -1
    private var mHighlightedWayPaths = arrayListOf<Path>()
    private var mHighlightedSteepnessType = -1
    private var mHighlightedSteepnessPaths = arrayListOf<Path>()

    private lateinit var mView: RouteProfileView

    private var mSurfacesTypes = mutableMapOf<ESurfaceType, ArrayList<CSectionItem>>()
    private var mRoadsTypes = mutableMapOf<ERoadType, ArrayList<CSectionItem>>()
    private var mSteepnessTypes = mutableMapOf<Int, ArrayList<CSectionItem>>()

    private val KHighlightPathsColor = Rgba(239, 38, 81, 255)

    private const val THRESHOLD_DIST_M = 500.0

    private const val KM_TO_METERS = 1000.0
    private const val MILES_TO_METERS = 1609.26939169
    private const val YARDS_TO_METERS = 0.9144
    private const val FT_TO_METERS = 0.3048
    private const val METERS_TO_MILES = 0.0006214

    //private const val METERS_TO_YARDS = 1.0936
    private const val METERS_TO_FT = 3.2808
    private const val MILES_TO_YARDS = 1760


    enum class TRouteProfileSectionType {
        EElevation,
        EClimbDetails,
        EWays,
        ESurfaces,
        ESteepnesses
    }


    enum class TElevationProfileButtonType {
        EElevationAtDeparture,
        EElevationAtDestination,
        EMinElevation,
        EMaxElevation
    }


    enum class TTouchChartEvent {
        EDown,
        EMove,
        EUp
    }


    enum class TClimbDetailsInfoType {
        ERating,
        EStartEndPoints,
        EStartEndElevation,
        ELength,
        EAvgGrade
    }

    enum class TUnitType {
        EMeter,
        EKm,
        EYard,
        EFoot,
        EMile
    }

    data class CSectionItem(var mStartDistanceM: Int, var mLengthM: Int)

    enum class TSteepnessImageType {
        EUnknown,
        EDown,
        EPlain,
        EUp
    }

    private fun refresh() {
        mRouteLengthInMeters = 0
        mCrtMinXInUnitType = 0.0
        mCrtMaxXInUnitType = 0.0
        mSurfacesTypes.clear()
        mRoadsTypes.clear()
        mSteepnessTypes.clear()
        removeHighlightedSurfacePathsFromMap()
        removeHighlightedWayPathsFromMap()
        removeHighlightedSteepnessPathsFromMap()

        mRoute =
            mMapView.preferences()?.routes()?.getMainRoute() ?: Route() // TODO maybe route nullable

        val timeDistance = mRoute.getTimeDistance(false)

        mRouteLengthInMeters = timeDistance?.getTotalDistance() ?: 0
        mCrtMaxXInUnitType = getDist(mRouteLengthInMeters)

        val steepnessIntervals = arrayListOf<Float>()
        mRoute.getTerrainProfile().let { routeTerrainProfile ->
            val values = arrayListOf(-16f, -10f, -7f, -4f, -1f, 1f, 4f, 7f, 10f, 16f)

            for (i in values) {
                steepnessIntervals.add(i)
            }

            routeTerrainProfile?.getSurfaceSections()?.let { surfaceSectionList ->
                var nLength: Int

                for ((i, item) in surfaceSectionList.withIndex()) {
                    nLength = if (i < surfaceSectionList.size - 1) {
                        surfaceSectionList[i + 1].getStartDistanceM() - item.getStartDistanceM()
                    } else {
                        mRouteLengthInMeters - item.getStartDistanceM()
                    }

                    if (mSurfacesTypes.containsKey(item.getType())) {
                        mSurfacesTypes[item.getType()]?.add(
                            CSectionItem(
                                item.getStartDistanceM(),
                                nLength
                            )
                        )
                    } else {
                        mSurfacesTypes[item.getType()] = arrayListOf(
                            CSectionItem(
                                item.getStartDistanceM(),
                                nLength
                            )
                        )
                    }
                }
            }

            routeTerrainProfile?.getRoadTypeSections()?.let { roadTypeSectionList ->
                var nLength: Int

                for ((i, item) in roadTypeSectionList.withIndex()) {
                    nLength = if (i < roadTypeSectionList.size - 1) {
                        roadTypeSectionList[i + 1].getStartDistanceM() - item.getStartDistanceM()
                    } else {
                        mRouteLengthInMeters - item.getStartDistanceM()
                    }

                    if (mRoadsTypes.containsKey(item.getType())) {
                        mRoadsTypes[item.getType()]?.add(
                            CSectionItem(
                                item.getStartDistanceM(),
                                nLength
                            )
                        )
                    } else {
                        mRoadsTypes[item.getType()] = arrayListOf(
                            CSectionItem(
                                item.getStartDistanceM(),
                                nLength
                            )
                        )
                    }
                }
            }

            routeTerrainProfile?.getSteepSections(steepnessIntervals)?.let { steepnessSectionList ->
                var nLength: Int

                for ((i, item) in steepnessSectionList.withIndex()) {
                    nLength = if (i < steepnessSectionList.size - 1) {
                        steepnessSectionList[i + 1].getStartDistanceM() - item.getStartDistanceM()
                    } else {
                        mRouteLengthInMeters - item.getStartDistanceM()
                    }

                    if (mSteepnessTypes.containsKey(item.getCategory())) {
                        mSteepnessTypes[item.getCategory()]?.add(
                            CSectionItem(
                                item.getStartDistanceM(),
                                nLength
                            )
                        )
                    } else {
                        mSteepnessTypes[item.getCategory()] = arrayListOf(
                            CSectionItem(
                                item.getStartDistanceM(),
                                nLength
                            )
                        )
                    }
                }
            }
        }
    }

    fun init(routeProfileView: RouteProfileView, mapView: MapView) {
        mView = routeProfileView
        mMapView = mapView
        mAltitudeFactor = 1.0

        when (SdkSettings().getUnitSystem()) {
            EUnitSystem.ImperialUk,
            EUnitSystem.ImperialUs -> {
                mAltitudeFactor = METERS_TO_FT
            }
            else -> {
            }
        }

        refresh()
    }

    fun open(routeProfileView: RouteProfileView?, mapView: MapView) {
        routeProfileView?.let {
            init(it, mapView)
            GEMApplication.postOnMain {
                var currentController = TutorialsOpener.getCurrentTutorial()

                if (currentController is PickLocationController) {
                    TutorialsOpener.onTutorialDestroyed(currentController)
                    currentController = TutorialsOpener.getCurrentTutorial()
                }

                if (currentController is BaseUiRouteController) {
                    currentController.hideAllButtons()
                    GEMApplication.topActivity()?.run {
                        setSystemBarsVisible(false)
                        setAppBarVisible(false)
                    }
                    mView.showRouteProfile()
                }
            }
        }
    }

    //
//    
//
    fun close() {
        GEMApplication.postOnMain {
            val currentController = TutorialsOpener.getCurrentTutorial()
            if (currentController is BaseUiRouteController) {
                currentController.showAllButtons()

                SdkCall.execute {
                    removeHighlightedSteepnessPathsFromMap()
                    removeHighlightedSurfacePathsFromMap()
                    removeHighlightedWayPathsFromMap()
                    mMapView.deactivateHighlight()
                }

                GEMApplication.topActivity()?.run {
                    setSystemBarsVisible(true)
                    setAppBarVisible(true)
                }
                mView.hideRouteProfile()
                currentController.onRouteProfileViewIsClosed()
            }
        }
    }

    fun getTitle(): String {
        return getUIString(StringIds.eStrRouteProfile)
    }

    fun getSectionTitle(section: Int): String {
        return when (section) {
            TRouteProfileSectionType.EElevation.ordinal -> {
                getUIString(StringIds.eStrElevation)
            }

            TRouteProfileSectionType.EClimbDetails.ordinal -> {
                getUIString(StringIds.eStrClimbDetails)
            }

            TRouteProfileSectionType.EWays.ordinal -> {
                getUIString(StringIds.eStrWays)
            }

            TRouteProfileSectionType.ESurfaces.ordinal -> {
                getUIString(StringIds.eStrSurfaces)
            }

            TRouteProfileSectionType.ESteepnesses.ordinal -> {
                getUIString(StringIds.eStrSteepness)
            }

            else -> {
                String()
            }
        }
    }


    fun getElevationChartMinValueX(): Double {
        return mCrtMinXInUnitType
    }


    fun getElevationChartMaxValueX(): Double {
        return mCrtMaxXInUnitType
    }


    fun getElevationChartMinValueY(): Int {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        routeTerrainProfile?.let { terrainProfile ->

            var diff = terrainProfile.getMaxElevation() - terrainProfile.getMinElevation()
            diff = 0.1f * abs(diff)

            val nRound = if (diff <= 5) 2 else (if (diff <= 25) 10 else 50)
            var nDiff = 1f.coerceAtLeast(diff).toInt()
            val elevation = (terrainProfile.getMinElevation() - nDiff).toDouble() * mAltitudeFactor
            var result = round(elevation).toInt()

            if (result < 0) {
                if (terrainProfile.getMinElevation() >= 0) {
                    result = 0
                } else {
                    diff = terrainProfile.getMinElevation()
                    diff = 0.1f * abs(diff)
                    nDiff = diff.coerceIn(1f, 100f).toInt()

                    result =
                        ((terrainProfile.getMinElevation() - nDiff).toInt() * mAltitudeFactor).toInt()
                }
            }

            if ((result % nRound) != 0) {
                result =
                    if (result < 0) {
                        ((result - nRound) / nRound) * nRound
                    } else {
                        (result / nRound) * nRound
                    }
            }

            return result
        }

        return 0
    }


    fun getElevationChartMaxValueY(): Int {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        routeTerrainProfile?.let { terrainProfile ->
            var diff = terrainProfile.getMaxElevation() - terrainProfile.getMinElevation()
            diff = 0.1f * abs(diff)

            val nRound = if (diff <= 5) 2 else (if (diff <= 25) 10 else 50)
            val nDiff = 1f.coerceAtLeast(diff).toInt()
            val elevation = (terrainProfile.getMaxElevation() + nDiff) * mAltitudeFactor
            var result = round(elevation).toInt()

            if ((result % nRound) != 0) {
                result = if (result < 0) {
                    (result / nRound) * nRound
                } else {
                    ((result + nRound) / nRound) * nRound
                }
            }

            return result
        }

        return 0
    }


    fun getElevationChartZoomThresholdDistX(): Double {
        val distanceFactor = getCurrentUnitToMetersDistanceFactor()
        var distInMeters = THRESHOLD_DIST_M

        val timeDistance = mRoute.getTimeDistance(false)
        distInMeters = distInMeters.coerceAtMost(
            timeDistance?.getTotalDistance()?.toDouble() ?: Double.MAX_VALUE
        )

        return distInMeters / distanceFactor
    }

    private fun getCurrentUnitToMetersDistanceFactor(): Double {
        return when (mCrtUnitType) {
            TUnitType.EMeter -> {
                1.0
            }

            TUnitType.EKm -> {
                KM_TO_METERS
            }

            TUnitType.EYard -> {
                YARDS_TO_METERS
            }

            TUnitType.EFoot -> {
                FT_TO_METERS
            }

            TUnitType.EMile -> {
                MILES_TO_METERS
            }
        }
    }


    fun getElevationChartYValues(nValues: Int): IntArray {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        val yValuesArray = IntArray(nValues)
        routeTerrainProfile.let { terrainProfile ->
            val distanceFactor = getCurrentUnitToMetersDistanceFactor()

            val distBegin = (mCrtMinXInUnitType * distanceFactor).toInt()
            val distEnd = (mCrtMaxXInUnitType * distanceFactor).toInt()

            val samples = terrainProfile?.getElevationSamples(nValues, distBegin, distEnd)

            val nSamplesCount = samples?.first?.size
            nSamplesCount?.let {
                if (nValues <= nSamplesCount) {
                    if (mAltitudeFactor > 1f) {
                        for (i in 0 until nValues) {
                            yValuesArray[i] = (samples.first[i] * mAltitudeFactor).toInt()
                        }
                    } else {
                        for (i in 0 until nValues) {
                            yValuesArray[i] = (samples.first[i]).toInt()
                        }
                    }
                }
            }
        }

        return yValuesArray
    }


    fun getElevationChartYValue(dist: Double): Int {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        routeTerrainProfile?.let { terrainProfile ->
            val distanceFactor = getCurrentUnitToMetersDistanceFactor()

            val nDist = (dist * distanceFactor).toInt()
            val elevation = terrainProfile.getElevation(nDist)

            return (elevation * mAltitudeFactor).toInt()
        }

        return 0
    }


    fun getElevationChartHorizontalAxisUnit(): String {
        return when (mCrtUnitType) {
            TUnitType.EMeter -> {
                getUIString(StringIds.eStrMeter)
            }

            TUnitType.EKm -> {
                getUIString(StringIds.eStrKm)
            }

            TUnitType.EYard -> {
                getUIString(StringIds.eStrYd)
            }

            TUnitType.EFoot -> {
                getUIString(StringIds.eStrFt)
            }

            TUnitType.EMile -> {
                getUIString(StringIds.eStrMi)
            }
        }
    }


    fun getElevationChartVerticalAxisUnit(): String {
        return when (SdkSettings().getUnitSystem()) {
            EUnitSystem.ImperialUk,
            EUnitSystem.ImperialUs -> {
                getUIString(StringIds.eStrFt)
            }

            else -> {
                getUIString(StringIds.eStrMeter)
            }
        }
    }


    fun getElevationChartVerticalBandsCount(): Int {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        routeTerrainProfile?.let { terrainProfile ->
            val list = terrainProfile.getClimbSections()
            return list?.size ?: 0
        }

        return 0
    }


    fun getElevationChartVerticalBandMinX(index: Int): Double {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        routeTerrainProfile?.let { terrainProfile ->
            val list = terrainProfile.getClimbSections()
            list?.let {
                if (indexIsValid(index, list.size)) {
                    return (list[index].getStartDistanceM() * (1.0 / getCurrentUnitToMetersDistanceFactor()))
                }
            }
        }

        return 0.0
    }


    fun getElevationChartVerticalBandMaxX(index: Int): Double {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        routeTerrainProfile?.let { terrainProfile ->
            val list = terrainProfile.getClimbSections()
            list?.let {
                if (indexIsValid(index, list.size)) {
                    return (list[index].getEndDistanceM() * (1.0 / getCurrentUnitToMetersDistanceFactor()))
                }
            }
        }

        return 0.0
    }


    fun getElevationChartVerticalBandText(index: Int): String {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        routeTerrainProfile?.let { terrainProfile ->
            val list = terrainProfile.getClimbSections()
            list?.let {
                if (indexIsValid(index, list.size)) {
                    return String.format("%d", list[index].getGrade().value)
                }
            }
        }

        return String()
    }


    fun getElevationChartPinImage(width: Int, height: Int): Bitmap? {
        return Utils.getImageAsBitmap(SdkIcons.Other_UI.Search_Results_Pin.value, width, height)
    }


    fun onTouchElevationChart(evt: Int, x: Double) {
        mMapView.let { m_mapView ->

            removeHighlightedSurfacePathsFromMap()
            removeHighlightedWayPathsFromMap()
            removeHighlightedSteepnessPathsFromMap()

            m_mapView.deactivateHighlight()

            if ((evt == TTouchChartEvent.EDown.ordinal) || (evt == TTouchChartEvent.EMove.ordinal)) {
                mHighlightedLmkList.clear()

                val landmark = Landmark()
                ImageDatabase().getImageById(SdkIcons.Other_UI.Search_Results_Pin.value)
                    ?.let { image ->
                        landmark.setImage(image)
                    }
                mHighlightedLmkList.add(landmark)

                val xM = (x * getCurrentUnitToMetersDistanceFactor()).toInt().coerceIn(
                    0,
                    mRoute.getTimeDistance(false)?.getTotalDistance()
                )

                mRoute.getCoordinateOnRoute(xM)?.let {
                    mHighlightedLmkList.first().setCoordinates(it)
                }

                if ((mPrevTouchXMeters == xM) && (evt == TTouchChartEvent.EDown.ordinal)) {
                    m_mapView.deactivateHighlight()

                    val settings = HighlightRenderSettings()
                    settings.setOptions(
                        EHighlightOptions.ShowContour.value
                            or EHighlightOptions.Overlap.value
                            or EHighlightOptions.NoFading.value
                    )

                    val f = {
                        m_mapView.activateHighlightLandmarks(mHighlightedLmkList, settings)
                    }

                    mTimeOutOperation = Executors.newSingleThreadScheduledExecutor().schedule(
                        f, 500, TimeUnit.MILLISECONDS
                    )
                    // mTimeOutOperation.cancel(true)
                    // TODO use this to cancel

                } else {
                    val settings = HighlightRenderSettings()
                    settings.setOptions(
                        EHighlightOptions.ShowLandmark.value
                            or EHighlightOptions.Overlap.value
                            or EHighlightOptions.NoFading.value
                    )
                    m_mapView.activateHighlightLandmarks(mHighlightedLmkList, settings)

                    mPrevTouchXMeters = if (evt == TTouchChartEvent.EDown.ordinal) {
                        xM
                    } else {
                        -1
                    }
                }

                mDeactivateHighlights = true

                val viewport = m_mapView.getViewport()
                val xy = mHighlightedLmkList.first().getCoordinates()?.let {
                    m_mapView.transformWgsToScreen(it)
                }

                if (viewport != null && xy != null) {
                    if (!pointInEnvelope(viewport, xy.x(), xy.y())) {
                        zoomRoute(mCrtMinXInUnitType, mCrtMaxXInUnitType)
                    }
                }
            }
        }
    }

    private fun pointInEnvelope(rect: Rect, x: Int, y: Int): Boolean {
        return ((x >= rect.x()) &&
            (x <= rect.right()) &&
            (y >= rect.y()) &&
            (y <= rect.bottom()))
    }

    private fun zoomRoute(minX: Double, maxX: Double) {
        removeHighlightedSurfacePathsFromMap()
        removeHighlightedWayPathsFromMap()
        removeHighlightedSteepnessPathsFromMap()

//        val controller = CAppController.getInstance();
        mMapView.let { m_mapView ->

            val distanceFactor = getCurrentUnitToMetersDistanceFactor()
            val distBegin = (minX * distanceFactor).toInt()
            val distEnd = (maxX * distanceFactor).toInt()

            m_mapView.preferences()?.setMapViewPerspective(EMapViewPerspective.TwoDimensional)

            var bAutomaticZoomToRoute = false
            if (minX == 0.0) {
                val max = getDist(mRouteLengthInMeters)
                if (abs((max - maxX)) < 0.0001) {
                    bAutomaticZoomToRoute = true
                }
            }

            if (bAutomaticZoomToRoute) {
                val mainRoute = m_mapView.preferences()?.routes()?.getMainRoute()
                flyToRoute(mainRoute)
            } else {
//                setAutomaticZoomToRoute(false) TODO
                m_mapView.centerOnDistRoute(mRoute, distBegin, distEnd, Rect(), Animation())
            }

            mCrtMinXInUnitType = minX
            mCrtMaxXInUnitType = maxX
        }
    }

    private fun getDist(meters: Int): Double {
        val unitsSystem = SdkSettings().getUnitSystem()

        var miles = 0.0
        val yardsOrFeet: Double
        val result: Double

        if (unitsSystem != EUnitSystem.Metric) {
            miles = (meters * METERS_TO_MILES)
//            yardsOrFeet =
//                if (unitsSystem == EUnitSystem.ImperialUk) (miles * MILES_TO_YARDS) else (miles * MILES_TO_METERS * METERS_TO_FT)
        }

        if (unitsSystem == EUnitSystem.Metric) {
            if (meters >= 1000) {
                mCrtUnitType = TUnitType.EKm
                result = meters.toDouble() / 1000
            } else {
                result = meters.toDouble()
                mCrtUnitType = TUnitType.EMeter
            }
        } else {
            mCrtUnitType = TUnitType.EYard

            var fMilesThreshold = 0.25

            if (unitsSystem == EUnitSystem.ImperialUk) {
                yardsOrFeet = miles * MILES_TO_YARDS
            } else {
                fMilesThreshold = 0.1
                yardsOrFeet = miles * MILES_TO_METERS * METERS_TO_FT
                mCrtUnitType = TUnitType.EFoot
            }

            if (miles >= fMilesThreshold) {
                mCrtUnitType = TUnitType.EMile
                result = miles
            } else {
                result = yardsOrFeet
            }
        }

        return result
    }

    private fun removeHighlightedSurfacePathsFromMap() {
        removeHighlightedPathsFromMap(mHighlightedSurfacePaths)
        mHighlightedSurfaceType = -1
    }

    private fun removeHighlightedWayPathsFromMap() {
        removeHighlightedPathsFromMap(mHighlightedWayPaths)
        mHighlightedWayType = -1
    }

    private fun removeHighlightedSteepnessPathsFromMap() {
        removeHighlightedPathsFromMap(mHighlightedSteepnessPaths)
        mHighlightedSteepnessType = -1
    }

    private fun removeHighlightedPathsFromMap(paths: ArrayList<Path>) {
        mMapView.let {
            if (paths.isNotEmpty()) {
                val pathCollection = mMapView.preferences()?.paths()
                pathCollection?.let {
                    for (path in paths) {
                        pathCollection.remove(path)
                    }
                }
                paths.clear()
            }
        }
    }

    @Suppress("unused")
    fun onZoomElevationChart(minX: Double, maxX: Double) {
        zoomRoute(minX, maxX)
    }

    @Suppress("unused")
    fun onScrollElevationChart(minX: Double, maxX: Double) {
        zoomRoute(minX, maxX)
    }

    fun onElevationChartIntervalUpdate(
        minX: Double,
        maxX: Double,
        bUpdateMap: Boolean
    ) {
        if (bUpdateMap) {
            if ((abs(minX - mCrtMinXInUnitType) > 0.000001) ||
                (abs(maxX - mCrtMaxXInUnitType) > 0.000001)
            ) {
                zoomRoute(minX, maxX)
            }
        } else {
            mCrtMinXInUnitType = minX
            mCrtMaxXInUnitType = maxX

            removeHighlightedSurfacePathsFromMap()
            removeHighlightedWayPathsFromMap()
            removeHighlightedSteepnessPathsFromMap()
        }
    }


    fun getElevationProfileButtonText(buttonType: Int): String {
        val routeTerrainProfile = mRoute.getTerrainProfile()

        routeTerrainProfile?.let { terrainProfile ->
            return when (buttonType) {

                TElevationProfileButtonType.EElevationAtDeparture.ordinal -> {
                    getElevationString(terrainProfile.getElevation(0))
                }

                TElevationProfileButtonType.EElevationAtDestination.ordinal -> {
                    getElevationString(
                        terrainProfile.getElevation(
                            mRoute.getTimeDistance(false)?.getTotalDistance() ?: 0
                        )
                    )
                }

                TElevationProfileButtonType.EMinElevation.ordinal -> {
                    getElevationString(terrainProfile.getMinElevation())
                }

                TElevationProfileButtonType.EMaxElevation.ordinal -> {
                    getElevationString(terrainProfile.getMaxElevation())
                }

                else -> {
                    String()
                }
            }
        }

        return String()
    }

    private fun getElevationString(elevation: Float): String {
        var tmp: String
        val routeTerrainProfile = mRoute.getTerrainProfile()

        routeTerrainProfile.let {
            val unitsSystem = SdkSettings().getUnitSystem()

            tmp = when (unitsSystem) {
                EUnitSystem.Metric -> {
                    String.format(
                        "%d %s",
                        elevation.toInt(),
                        getUIString(StringIds.eStrMeter)
                    )
                }
                EUnitSystem.ImperialUk,
                EUnitSystem.ImperialUs -> {
                    String.format(
                        "%d %s",
                        (elevation * METERS_TO_FT).toInt(),
                        getUIString(StringIds.eStrFt)
                    )
                }
            }
        }

        return tmp
    }


    fun getElevationProfileButtonImage(
        buttonType: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        val routeTerrainProfile = mRoute.getTerrainProfile()

        routeTerrainProfile?.let {
            return when (buttonType) {

                TElevationProfileButtonType.EElevationAtDeparture.ordinal -> {
                    Utils.getImageAsBitmap(
                        SdkIcons.Other_Engine.WaypointFlag_PointStart.value,
                        width,
                        height
                    )
                }

                TElevationProfileButtonType.EElevationAtDestination.ordinal -> {
                    Utils.getImageAsBitmap(
                        SdkIcons.Other_Engine.WaypointFlag_PointFinish.value,
                        width,
                        height
                    )
                }

                TElevationProfileButtonType.EMinElevation.ordinal -> {
                    Utils.getImageAsBitmap(
                        SdkIcons.Other_UI.HeightProfile_Lowest_Point.value,
                        width,
                        height
                    )
                }

                TElevationProfileButtonType.EMaxElevation.ordinal -> {
                    Utils.getImageAsBitmap(
                        SdkIcons.Other_UI.HeightProfile_Highest_Point.value,
                        width,
                        height
                    )
                }

                else -> {
                    null
                }
            }
        }

        return null
    }


    fun onPushButton(buttonType: Int) {
        val routeTerrainProfile = mRoute.getTerrainProfile()
//        if (m_view && m_mapView && routeTerrainProfile)
        routeTerrainProfile?.let { terrainProfile ->
            var distance = 0.0
            when (buttonType) {
                TElevationProfileButtonType.EElevationAtDeparture.ordinal -> {
                }
                TElevationProfileButtonType.EElevationAtDestination.ordinal -> {
                    distance = mRoute.getTimeDistance(false)?.getTotalDistance()?.toDouble() ?: 0.0
                }

                TElevationProfileButtonType.EMinElevation.ordinal -> {
                    distance = terrainProfile.getMinElevationDistance().toDouble()
                }

                TElevationProfileButtonType.EMaxElevation.ordinal -> {
                    distance = terrainProfile.getMaxElevationDistance().toDouble()
                }
            }

            val landmark = Landmark()
            val lmkList = arrayListOf<Landmark>()

            val image = ImageDatabase().getImageById(SdkIcons.Other_UI.Search_Results_Pin.value)
            image?.let {
                landmark.setImage(image)
                mHighlightedLmkList.add(landmark)
                mRoute.getCoordinateOnRoute(distance.toInt())?.let {
                    mHighlightedLmkList.first().setCoordinates(it)
                    mMapView.deactivateHighlight()
                    val settings = HighlightRenderSettings()
                    settings.setOptions(
                        EHighlightOptions.ShowLandmark.value
                            or EHighlightOptions.Overlap.value
                            or EHighlightOptions.NoFading.value
                    )
                    mMapView.activateHighlightLandmarks(lmkList, settings)

                    GEMApplication.postOnMain { mView.updateElevationChartPinPosition((1.0 / getCurrentUnitToMetersDistanceFactor()) * distance) }

                    mDeactivateHighlights = true

                    onTouchElevationChart(
                        TTouchChartEvent.EDown.ordinal,
                        (1.0 / getCurrentUnitToMetersDistanceFactor()) * distance
                    )
                }
            }
        }
    }


    fun getClimbDetailsColumnText(infoType: Int): String {
        return when (infoType) {

            TClimbDetailsInfoType.ERating.ordinal -> {
                getUIString(StringIds.eStrRating)
            }

            TClimbDetailsInfoType.EStartEndPoints.ordinal -> {
                getUIString(StringIds.eStartEndPoints)
            }

            TClimbDetailsInfoType.EStartEndElevation.ordinal -> {
                getUIString(StringIds.eStrStartEndElevation)
            }

            TClimbDetailsInfoType.ELength.ordinal -> {
                getUIString(StringIds.eStrLength)
            }

            TClimbDetailsInfoType.EAvgGrade.ordinal -> {
                getUIString(StringIds.eStrAvgGrade)
            }

            else -> {
                String()
            }
        }
    }


    fun getClimbDetailsRowsCount(): Int {
        val routeTerrainProfile = mRoute.getTerrainProfile()
        return routeTerrainProfile?.getClimbSections()?.size ?: 0
    }


    fun getClimbDetailsItemText(row: Int, infoType: Int): String {
        var result = ""

        val nRows = getClimbDetailsRowsCount()
        if ((row >= 0) && (row < nRows)) {
            result = when (infoType) {

                TClimbDetailsInfoType.ERating.ordinal -> {
                    getElevationChartVerticalBandText(row)
                }

                TClimbDetailsInfoType.EStartEndPoints.ordinal -> // Start/End Points
                {
                    val unit = getElevationChartHorizontalAxisUnit()
                    String.format(
                        "%.2f %s/%.2f %s",
                        getElevationChartVerticalBandMinX(row),
                        unit,
                        getElevationChartVerticalBandMaxX(row),
                        unit
                    )
                }

                TClimbDetailsInfoType.EStartEndElevation.ordinal -> // Start/End Elevation
                {
                    val unit = getElevationChartVerticalAxisUnit()
                    String.format(
                        "%d %s/%d %s",
                        getElevationChartYValue(

                            getElevationChartVerticalBandMinX(row)
                        ),
                        unit,
                        getElevationChartYValue(

                            getElevationChartVerticalBandMaxX(row)
                        ),
                        unit
                    )
                }

                TClimbDetailsInfoType.ELength.ordinal -> // Length
                {
                    val unit = getElevationChartHorizontalAxisUnit()
                    String.format(
                        "%.2f %s",
                        getElevationChartVerticalBandMaxX(

                            row
                        ) - getElevationChartVerticalBandMinX(

                            row
                        ),
                        unit
                    )
                }

                TClimbDetailsInfoType.EAvgGrade.ordinal -> // Avg Grade
                {
                    val routeTerrainProfile = mRoute.getTerrainProfile()
                    if (routeTerrainProfile != null) {
                        val list = routeTerrainProfile.getClimbSections()
                        String.format("%.1f%%", list?.get(row)?.getSlope())
                    } else {
                        String()
                    }
                }

                else -> {
                    String()
                }
            }
        }

        return result
    }


    fun getSurfacesCount(): Int {
        return mSurfacesTypes.size
    }


    fun getSurfaceColor(index: Int): Int {
        var auxIndex = index
        if (indexIsValid(index, mSurfacesTypes.size)) {
            for (item in mSurfacesTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    return getSurfaceColor(item.key)
                }
            }
        }

        return 0
    }


    fun getSurfaceText(index: Int): String {
        if (indexIsValid(index, mSurfacesTypes.size)) {
            var auxIndex = index
            for (item in mSurfacesTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    val tmp = getSurfaceName(item.key)
                    return String.format("%s (%.2f%%)", tmp, getSurfacePercent(index) * 100)
                }
            }
        }

        return String()
    }


    fun getSurfacePercent(index: Int): Double {
        var auxIndex = index
        if ((mRouteLengthInMeters > 0) && indexIsValid(index, mSurfacesTypes.size)) {
            for (item in mSurfacesTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    var length = 0.0
                    for (it in item.value) {
                        length += it.mLengthM
                    }

                    return length / mRouteLengthInMeters
                }
            }
        }

        return 0.0
    }


    fun onTouchSurfacesChart(evt: Int, x: Double) {
        if (evt == TTouchChartEvent.EUp.ordinal) {
            return
        }

        if (evt == TTouchChartEvent.EDown.ordinal) {
            removeHighlightedWayPathsFromMap()
            removeHighlightedSteepnessPathsFromMap()

            if (mHighlightedSurfacePaths.isEmpty()) {
                zoomToRoute()

                mCrtMinXInUnitType = 0.0
                mCrtMaxXInUnitType = getDist(mRouteLengthInMeters)

                GEMApplication.postOnMain {
                    mView.updateElevationChartInterval(
                        mCrtMinXInUnitType,
                        mCrtMaxXInUnitType
                    )
                }
            }
        }

        mMapView.deactivateHighlight()

        var percent: Double
        var length: Double
        var prevPercent = 0.0

        for (item in mSurfacesTypes) {
            length = 0.0
            for (it in item.value) {
                length += it.mLengthM
            }

            percent = prevPercent + length / mRouteLengthInMeters

            if ((prevPercent <= x) && (x <= percent)) {
                if (mHighlightedSurfaceType != item.key.value) {
                    removeHighlightedSurfacePathsFromMap()
                    mHighlightedSurfaceType = item.key.value
                } else {
                    break
                }

                val pathsCollection = mMapView.preferences()?.paths()
                for (it in item.value) {
                    val path = mRoute.getPath(it.mStartDistanceM, it.mStartDistanceM + it.mLengthM)
                    path?.let { itPath ->
                        if (pathsCollection != null) {
                            pathsCollection.add(itPath, KHighlightPathsColor, KHighlightPathsColor)
                            mHighlightedSurfacePaths.add(itPath)
                        }
                    }
                }

                break
            }

            prevPercent = percent
        }
    }

    private fun getSurfaceColor(type: ESurfaceType): Int {
        return when (type) {

            ESurfaceType.Asphalt -> {
                Rgba(127, 137, 149, 255).value()
            }

            ESurfaceType.Paved -> {
                Rgba(212, 212, 212, 255).value()
            }

            ESurfaceType.Unpaved -> {
                Rgba(157, 133, 104, 255).value()
            }

            ESurfaceType.Unknown -> {
                Rgba(0, 0, 0, 255).value()
            }

            else -> {
                0
            }
        }
    }

    private fun getSurfaceName(type: ESurfaceType): String {
        return when (type) {

            ESurfaceType.Asphalt -> {
                getUIString(StringIds.eStrAsphalt)
            }

            ESurfaceType.Paved -> {
                getUIString(StringIds.eStrPaved)
            }

            ESurfaceType.Unpaved -> {
                getUIString(StringIds.eStrUnpaved)
            }

            ESurfaceType.Unknown -> {
                getUIString(StringIds.eStrUnknown)
            }

            else -> {
                String()
            }
        }
    }

    private fun zoomToRoute() {
        mMapView.preferences()?.setMapViewPerspective(EMapViewPerspective.TwoDimensional)
        val mainRoute = mMapView.preferences()?.routes()?.getMainRoute()
        flyToRoute(mainRoute)
    }


    fun getWaysCount(): Int {
        return mRoadsTypes.size
    }


    fun getWayColor(index: Int): Int {
        var auxIndex = index
        if (indexIsValid(index, mRoadsTypes.size)) {
            for (item in mRoadsTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    return getWayColor(item.key)
                }
            }
        }

        return 0
    }

    private fun getWayColor(type: ERoadType): Int {
        return when (type) {
            ERoadType.Motorways -> {
                Rgba(242, 144, 99, 255).value()
            }

            ERoadType.StateRoad -> {
                Rgba(242, 216, 99, 255).value()
            }

            ERoadType.Road -> {
                Rgba(153, 163, 175, 255).value()
            }

            ERoadType.Street -> {
                Rgba(175, 185, 193, 255).value()
            }

            ERoadType.Cycleway -> {
                Rgba(15, 175, 135, 255).value()
            }

            ERoadType.Path -> {
                Rgba(196, 200, 211, 255).value()
            }

            ERoadType.SingleTrack -> {
                Rgba(166, 133, 96, 255).value()
            }
        }
    }


    fun getWayText(index: Int): String {
        if (indexIsValid(index, mRoadsTypes.size)) {
            var auxIndex = index
            for (item in mRoadsTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    val tmp = getWayName(item.key)

//                    if (tmp.isRightToLeft())
//                    {
//                        result.format("(%%%.2f) %s", getWayPercent(index) * 100, tmp)
//                    }
//                    else
//                    {
                    return String.format("%s (%.2f%%)", tmp, getWayPercent(index) * 100)
//                    }
                }
            }
        }

        return String()
    }

    private fun getWayName(type: ERoadType): String {
        return when (type) {
            ERoadType.Motorways -> {
                getUIString(StringIds.eStrMotorway)
            }

            ERoadType.StateRoad -> {
                getUIString(StringIds.eStrStateRoad)
            }

            ERoadType.Road -> {
                getUIString(StringIds.eStrRoad)
            }

            ERoadType.Street -> {
                getUIString(StringIds.eStrStreet)
            }

            ERoadType.Cycleway -> {
                getUIString(StringIds.eStrCycleway)
            }

            ERoadType.Path -> {
                getUIString(StringIds.eStrPath)
            }

            ERoadType.SingleTrack -> {
                getUIString(StringIds.eStrSingleTrack)
            }
        }
    }


    fun getWayPercent(index: Int): Double {
        if ((mRouteLengthInMeters > 0) && indexIsValid(index, mRoadsTypes.size)) {
            var auxIndex = index
            for (item in mRoadsTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    var length = 0.0
                    for (it in item.value) {
                        length += it.mLengthM
                    }

                    return length / mRouteLengthInMeters
                }
            }
        }

        return 0.0
    }


    fun onTouchWaysChart(evt: Int, x: Double) {
        if (evt == TTouchChartEvent.EUp.ordinal) {
            return
        }

        if (evt == TTouchChartEvent.EDown.ordinal) {
            removeHighlightedSurfacePathsFromMap()
            removeHighlightedSteepnessPathsFromMap()

            if (mHighlightedWayPaths.isEmpty()) {
                zoomToRoute()

                mCrtMinXInUnitType = 0.0
                mCrtMaxXInUnitType = getDist(mRouteLengthInMeters)

                GEMApplication.postOnMain {
                    mView.updateElevationChartInterval(mCrtMinXInUnitType, mCrtMaxXInUnitType)
                }
            }
        }

        mMapView.deactivateHighlight()

        var percent: Double
        var length: Double
        var prevPercent = 0.0

        for (item in mRoadsTypes) {
            length = 0.0
            for (it in item.value) {
                length += it.mLengthM
            }

            percent = prevPercent + length / mRouteLengthInMeters

            if ((prevPercent <= x) && (x <= percent)) {
                if (mHighlightedWayType != item.key.value) {
                    removeHighlightedWayPathsFromMap()
                    mHighlightedWayType = item.key.value
                } else {
                    break
                }

                val pathsCollection = mMapView.preferences()?.paths()
                for (it in item.value) {
                    val path = mRoute.getPath(it.mStartDistanceM, it.mStartDistanceM + it.mLengthM)
                    path?.let { itPath ->
                        if (pathsCollection != null) {
                            pathsCollection.add(itPath, KHighlightPathsColor, KHighlightPathsColor)
                            mHighlightedWayPaths.add(itPath)
                        }
                    }

                }

                break
            }

            prevPercent = percent
        }
    }


    fun getSteepnessesCount(): Int {
        return mSteepnessTypes.size
    }


    fun getSteepnessColor(index: Int): Int {
        if (indexIsValid(index, mSteepnessTypes.size)) {
            var auxIndex = index
            for (item in mSteepnessTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    return when (item.key) {
                        0 -> { // < -16
                            Rgba(4, 120, 8, 255).value()
                        }

                        1 -> { // [-16, -10]
                            Rgba(38, 151, 41, 255).value()
                        }

                        2 -> { // [-10, -7]
                            Rgba(73, 183, 76, 255).value()
                        }

                        3 -> { // [-7, -4]
                            Rgba(112, 216, 115, 255).value()
                        }

                        4 -> { // [-4, -1]
                            Rgba(154, 250, 156, 255).value()
                        }

                        5 -> { // [-1, 1]
                            Rgba(255, 197, 142, 255).value()
                        }

                        6 -> { // [1, 4]
                            Rgba(240, 141, 141, 255).value()
                        }

                        7 -> { // [4, 7]
                            Rgba(220, 105, 106, 255).value()
                        }

                        8 -> { // [7, 10]
                            Rgba(201, 73, 72, 255).value()
                        }

                        9 -> { // [10, 16]
                            Rgba(182, 43, 42, 255).value()
                        }

                        10 -> { // > 16
                            Rgba(164, 16, 15, 255).value()
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


    fun getSteepnessText(index: Int): String {
        if (indexIsValid(index, mRoadsTypes.size)) {
            var auxIndex = index
            for (item in mSteepnessTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    val tmp = getSteepnessName(item.key)

//                    if (tmp.isRightToLeft())
//                    {
//                        result.format("(%%%.2f) %s", getWayPercent(index) * 100, tmp)
//                    }
//                    else
//                    {
                    return String.format(
                        "%s (%.2f%%)",
                        tmp,
                        getSteepnessPercent(index) * 100
                    )
//                    }
                }
            }
        }

        return String()
    }

    private fun getSteepnessName(index: Int): String {
        return when (index) {
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
    }


    fun getSteepnessPercent(index: Int): Double {
        if ((mRouteLengthInMeters > 0) && indexIsValid(index, mSteepnessTypes.size)) {
            var auxIndex = index
            for (item in mSteepnessTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    var length = 0.0
                    for (it in item.value) {
                        length += it.mLengthM
                    }

                    return length / mRouteLengthInMeters
                }
            }
        }

        return 0.0
    }


    fun getSteepnessImage(index: Int, width: Int, height: Int): Bitmap? {
        var imageType = TSteepnessImageType.EUnknown

        if (indexIsValid(index, mSteepnessTypes.size)) {
            var auxIndex = index
            for (item in mSteepnessTypes) {
                --auxIndex
                if (auxIndex < 0) {
                    if ((item.key >= 0) && (item.key < 5)) {
                        imageType = TSteepnessImageType.EDown
                        break
                    }

                    if (item.key == 5) {
                        imageType = TSteepnessImageType.EPlain
                        break
                    }

                    imageType = TSteepnessImageType.EUp
                    break
                }
            }
        }

        return when (imageType) {
            TSteepnessImageType.EUp -> {
                Utils.getImageAsBitmap(SdkIcons.Other_UI.SteepnessUp.value, width, height)
            }

            TSteepnessImageType.EDown -> {
                Utils.getImageAsBitmap(SdkIcons.Other_UI.SteepnessDown.value, width, height)
            }

            TSteepnessImageType.EPlain -> {
                Utils.getImageAsBitmap(SdkIcons.Other_UI.SteepnessPlain.value, width, height)
            }

            TSteepnessImageType.EUnknown -> {
                null
            }
        }
    }


    fun onTouchSteepnessesChart(evt: Int, x: Double) {
        if (evt == TTouchChartEvent.EUp.ordinal) {
            return
        }

        if (evt == TTouchChartEvent.EDown.ordinal) {
            removeHighlightedSurfacePathsFromMap()
            removeHighlightedWayPathsFromMap()

            if (mHighlightedSteepnessPaths.isEmpty()) {
                zoomToRoute()

                mCrtMinXInUnitType = 0.0
                mCrtMaxXInUnitType = getDist(mRouteLengthInMeters)

                GEMApplication.postOnMain {
                    mView.updateElevationChartInterval(mCrtMinXInUnitType, mCrtMaxXInUnitType)
                }

            }
        }
        mMapView.deactivateHighlight()

        var percent: Double
        var length: Double
        var prevPercent = 0.0

        for (item in mSteepnessTypes) {
            length = 0.0
            for (it in item.value) {
                length += it.mLengthM
            }

            percent = prevPercent + length / mRouteLengthInMeters

            if ((prevPercent <= x) && (x <= percent)) {
                if (mHighlightedSteepnessType != item.key) {
                    removeHighlightedSteepnessPathsFromMap()
                    mHighlightedSteepnessType = item.key
                } else {
                    break
                }

                val pathsCollection = mMapView.preferences()?.paths()
                for (it in item.value) {
                    val path = mRoute.getPath(it.mStartDistanceM, it.mStartDistanceM + it.mLengthM)
                    path?.let { itPath ->
                        if (pathsCollection != null) {
                            pathsCollection.add(itPath, KHighlightPathsColor, KHighlightPathsColor)
                            mHighlightedSteepnessPaths.add(itPath)
                        }
                    }

                }

                break
            }

            prevPercent = percent
        }
    }

    private fun indexIsValid(index: Int, listSize: Int): Boolean {
        return ((index >= 0) && (index < listSize))
    }

    fun flyToRoute(route: Route?) {
        route?.let {
            mMapView.centerOnRoute(it)
        }
    }
}
