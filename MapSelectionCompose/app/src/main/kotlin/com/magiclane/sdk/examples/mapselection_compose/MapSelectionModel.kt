// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapselection_compose

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Parameter
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.TimezoneResult
import com.magiclane.sdk.core.TimezoneService
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.MarkerRenderSettings
import com.magiclane.sdk.d3scene.OverlayItem
import com.magiclane.sdk.examples.mapselection_compose.data.ECardinalDirections
import com.magiclane.sdk.examples.mapselection_compose.data.LocationDetailsInfo
import com.magiclane.sdk.examples.mapselection_compose.data.SafetyCameraInfo
import com.magiclane.sdk.examples.mapselection_compose.data.SocialReportInfo
import com.magiclane.sdk.examples.mapselection_compose.data.TrafficEventInfo
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.util.GemList
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall

class MapSelectionModel : ViewModel() {

    //data
    var safetyCameraInfo: SafetyCameraInfo? by mutableStateOf(null)
    var locationDetailsInfo: LocationDetailsInfo? by mutableStateOf(null)
    var trafficEventInfo: TrafficEventInfo? by mutableStateOf(null)
    var socialReportInfo: SocialReportInfo? by mutableStateOf(null)

    private var trafficEvent: TrafficEvent? = null

    //loading state
    var progressBarIsVisible by mutableStateOf(false)

    //error
    var errorMessage by mutableStateOf("")

    //routing service 
    private lateinit var routingService: RoutingService

    //utils
    var followGpsButtonIsVisible by mutableStateOf(false)
    var flyToRoutesButtonIsVisible by mutableStateOf(false)
    private lateinit var visibleArea: Rect
    private var routesList = ArrayList<Route>()
    private lateinit var animation: Animation
    var bottomPartHeight: Int = 0
    var detailsPanelImageSize: Int = 0
    var overlayImageSize: Int = 0
    var invokeHighlight by mutableStateOf(false)
    var highlightEffect: () -> Unit = {}

    fun initialize(context: Context, gemSurfaceView: GemSurfaceView) = SdkCall.execute {
        //create routing service
        routingService = RoutingService(
            onStarted = {
                //progressBarIsVisible = true
            },
            onCompleted = { routes, errorCode, _ ->
                //progressBarIsVisible = false //todo handle progress
                when (errorCode) {
                    GemError.NoError -> {
                        routesList = routes
                        SdkCall.execute {
                            gemSurfaceView.mapView?.let { mapView ->
                                mapView.presentRoutes(routes, displayBubble = true)
                                mapView.preferences?.routes?.mainRoute?.let { selectRoute(it, mapView) }
                            }
                        }
                        flyToRoutesButtonIsVisible = true
                    }

                    GemError.Cancel -> {
                        // The routing action was cancelled.
                    }

                    else -> {
                        // There was a problem at computing the routing operation.
                        errorMessage = GemError.getMessage(errorCode)
                    }
                }
            }
        )
        gemSurfaceView.mapView?.apply {
            //handle follow position
            followGpsButtonIsVisible = SdkCall.execute { isFollowingPosition() } != true

            onExitFollowingPosition = {
                followGpsButtonIsVisible = true
            }

            onEnterFollowingPosition = {
                followGpsButtonIsVisible = false
                SdkCall.execute {
                    deactivateHighlights(this)
                }
            }
            //set visible area
            //setVisibleArea(gemSurfaceView)
            //set on map touch
            onTouch = { xy ->
                SdkCall.execute {
                    cursorScreenPosition = xy
                    deactivateHighlights(this)
                    hideBottomView()
                    val centerXy = visibleArea.center!!
                    //my position
                    if (centerOnMyPosition(this)) return@execute
                    //center on landmark
                    val landmarks = cursorSelectionLandmarks
                    if (!landmarks.isNullOrEmpty()) {
                        highlightLandmark(landmarks[0],  this)
                        return@execute
                    }
                    //center on traffic event
                    if (centerOnTrafficEvent(this)) return@execute

                    //center on overlay
                    if (centerOnOverlayItem(this)) return@execute

                    //center on routes
                    // get the visible routes at the touch event point
                    val routes = cursorSelectionRoutes
                    // check if there is any route
                    if (!routes.isNullOrEmpty()) {
                        // set the touched route as the main route and center on it
                        selectRoute(routes[0], this)
                        return@execute
                    }

                    //center on street position
                    val streets = cursorSelectionStreets
                    if (!streets.isNullOrEmpty()) {
                        highlightLandmark(streets[0],  this)
                    }
                }
            }
        }
    }

    fun calculateRoutes() = SdkCall.execute {
        //start route calculation
        animation = Animation(EAnimation.Linear)
        animation.duration = 900
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616)
        )
        routingService.calculateRoute(waypoints)
    }

    private fun centerOnMyPosition(mapView: MapView): Boolean = SdkCall.execute {
        val myPosition = mapView.cursorSelectionSceneObject
        MapSceneObject.getDefPositionTracker().first?.let { sceneObject -> //the default my position image
            if ((myPosition != null) && isSameMapScene(myPosition, sceneObject)) {
                fillLocationDetailsInfo(
                    null,
                    "My position",
                    ""
                )

                myPosition.coordinates?.let { coordinates ->
                    highlightPlace(coordinates, ImageDatabase.Companion.searchResultsPin!!, mapView)
                }
                return@execute true
            }
        }
        return@execute false
    }!!

    private var parameters = GemList(Parameter::class)

    private val trafficEventProgressListener = ProgressListener.create(
        onCompleted = { errorCode: Int, _ ->
            if (errorCode == GemError.NoError) {
                SdkCall.execute {
                    trafficEvent?.let {
                        fillTrafficEventInfo(it, parameters)
                    }
                }
            } else if (errorCode != GemError.Cancel) {
                errorMessage = GemError.getMessage(errorCode)
            }

            progressBarIsVisible = false
        })

    private fun centerOnTrafficEvent(mapView: MapView): Boolean = SdkCall.execute {
        val trafficEvents = mapView.cursorSelectionTrafficEvents
        if (!trafficEvents.isNullOrEmpty()) {
            trafficEvent = trafficEvents[0]

            parameters.clear()
            val errorCode = trafficEvent?.getPreviewData(parameters, trafficEventProgressListener) ?: GemError.NoError

            if (errorCode != GemError.NoError) {
                errorMessage = GemError.getMessage(errorCode)
            } else {
                progressBarIsVisible = true
                trafficEvent?.referencePoint?.let {
                    highlightPlace(
                        it, trafficEvent?.image!!, mapView
                    )
                }
            }
            return@execute true
        }
        return@execute false
    }!!

    private fun centerOnOverlayItem(mapView: MapView): Boolean = SdkCall.execute {
        val overlays = mapView.cursorSelectionOverlayItems
        if (!overlays.isNullOrEmpty()) {
            val overlay = overlays[0]
            when (overlay.overlayInfo?.uid) {
                ECommonOverlayId.Safety.value -> {
                    fillSafetyCameraInfo(overlay)
                    highlightOverlay(mapView, overlay, true)
                }

                ECommonOverlayId.SocialReports.value -> {
                    fillSocialReportInfo(overlay, mapView)
                    highlightOverlay(mapView, overlay, false)
                }

                else -> {
                    overlay.run {
                        fillLocationDetailsInfo(
                            image?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)?.asImageBitmap() ?: ImageBitmap(
                                1,
                                1
                            ),
                            name.toString(),
                            overlayInfo?.name.toString()
                        )
                    }

                    highlightOverlay(mapView, overlay, false)
                }
            }
            return@execute true
        }
        return@execute false
    }!!

    fun showRoutes(mapView: MapView) = SdkCall.execute {
        mapView.preferences?.routes?.mainRoute?.let { selectRoute(it, mapView) }
        highlightEffect.invoke()
    }

    fun selectRoute(route: Route, mapView: MapView?) = SdkCall.execute {
        mapView?.apply {
            route.apply {
                summary?.let {
                    var text = ""
                    var description = ""
                    val pos = it.indexOf(") ")
                    if (pos > 0) {
                        text = it.substring(0, pos + 1)
                        if ((pos + 2) < it.length) {
                            description = it.substring(pos + 2, it.length)
                        }
                    } else
                        text = it
                    fillLocationDetailsInfo(
                        null,
                        text,
                        description
                    )
                }
            }

            preferences?.routes?.mainRoute = route
            invokeHighlight = true
            highlightEffect = { centerOnRoutes(routesList, animation = animation, viewRc = visibleArea) }
        }
    }

    fun startFollowingPosition(surfaceView: GemSurfaceView?) = SdkCall.execute {
        surfaceView?.mapView?.followPosition()
    }

    private fun fillLocationDetailsInfo(image: ImageBitmap?, text: String, description: String) {
        socialReportInfo = null
        trafficEventInfo = null
        safetyCameraInfo = null
        locationDetailsInfo = LocationDetailsInfo(image, text, description)
    }

    private fun fillSafetyCameraInfo(overlayItem: OverlayItem) {
        socialReportInfo = null
        trafficEventInfo = null
        locationDetailsInfo = null
        overlayItem.getPreviewData()?.let { parameters ->
            var imageBitmap = ImageBitmap(1, 1)

            overlayItem.image?.let { image ->
                GemUtilImages.asBitmap(image, (overlayImageSize * (image.aspectRatio!!.width / image.aspectRatio!!.height)).toInt(), overlayImageSize)?.let { bmp ->
                    imageBitmap = bmp.asImageBitmap()
                }
            }

            var bothDirections = false
            for (parameter in parameters) {
                if (parameter.key == "eStrDrivingDirectionFlag") {
                    bothDirections = parameter.valueBoolean
                    break
                }
            }

            var country = ""
            for (parameter in parameters) {
                if (parameter.key == "Country") {
                    country = parameter.valueString
                    break
                }
            }

            var type = ""
            var speedLimitValue = ""
            var speedLimitUnit = ""
            var cameraStatusText = ""
            var cameraStatusValue = ""
            var drivingDirectionText = ""
            var drivingDirectionValue = ""
            var locationText = ""
            var locationValue = ""
            var towardsText = ""
            var towardsValue = mutableListOf<ECardinalDirections>()
            var addedToDatabaseText = ""
            var addedToDatabaseValue = ""

            for (parameter in parameters) {
                when (parameter.key) {
                    "type" -> {
                        type = parameter.valueString
                    }

                    "speedValue" -> {
                        speedLimitValue = parameter.valueString
                    }

                    "speedUnit" -> {
                        speedLimitUnit = parameter.valueString
                    }

                    "eStrCameraStatus" -> {
                        cameraStatusText = parameter.name.toString()
                        cameraStatusValue = parameter.valueString
                    }

                    "eStrDrivingDirection" -> {
                        drivingDirectionText = parameter.name.toString()
                        drivingDirectionValue = parameter.valueString
                    }

                    "eStrLocation" -> {
                        locationText = parameter.name.toString()
                        locationValue = parameter.valueString

                        if (country.isNotEmpty()) {
                            locationValue = if (locationValue.isNotEmpty()) {
                                String.format("%s, %s", locationValue, country)
                            } else {
                                country
                            }
                        }
                    }

                    "eStrTowards" -> {
                        towardsText = parameter.name.toString()
                        val towards = parameter.valueLong
                        towardsValue = when {
                            ((towards >= 0) && (towards < 30)) || (towards >= 330) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.N, ECardinalDirections.S)
                                else mutableListOf(ECardinalDirections.N)
                            }

                            (towards >= 30) && (towards < 60) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.NE, ECardinalDirections.SW)
                                else mutableListOf(ECardinalDirections.NE)
                            }

                            (towards >= 60) && (towards < 120) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.E, ECardinalDirections.W)
                                else mutableListOf(ECardinalDirections.E)
                            }

                            (towards >= 120) && (towards < 150) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.NW, ECardinalDirections.SE)
                                else mutableListOf(ECardinalDirections.SE)
                            }

                            (towards >= 150) && (towards < 210) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.N, ECardinalDirections.S)
                                else mutableListOf(ECardinalDirections.S)
                            }

                            (towards >= 210) && (towards < 240) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.NE, ECardinalDirections.SW)
                                else mutableListOf(ECardinalDirections.SW)
                            }

                            (towards >= 240) && (towards < 300) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.E, ECardinalDirections.W)
                                else mutableListOf(ECardinalDirections.W)
                            }

                            (towards >= 300) && (towards < 330) -> {
                                if (bothDirections) mutableListOf(ECardinalDirections.NW, ECardinalDirections.SE)
                                else mutableListOf(ECardinalDirections.NW)
                            }

                            else -> mutableListOf()
                        }
                    }

                    "create_stamp_utc" -> {
                        val value = parameter.valueLong
                        if (value > 0) {
                            addedToDatabaseText = parameter.name.toString()

                            val time = Time()
                            time.longValue = value * 1000
                            addedToDatabaseValue = String.format("%d/%d/%d", time.month, time.day, time.year)
                        }
                    }
                }
            }

            safetyCameraInfo = SafetyCameraInfo(
                imageBitmap,
                type,
                speedLimitValue,
                speedLimitUnit,
                cameraStatusText,
                cameraStatusValue,
                drivingDirectionText,
                drivingDirectionValue,
                locationText,
                locationValue,
                towardsText,
                towardsValue,
                addedToDatabaseText,
                addedToDatabaseValue
            )
        }
    }

    @SuppressLint("DefaultLocale")
    private fun fillSocialReportInfo(overlayItem: OverlayItem, mapView: MapView) {
        locationDetailsInfo = null
        trafficEventInfo = null
        safetyCameraInfo = null
        overlayItem.getPreviewData()?.let { parameters ->
            var imageBitmap = ImageBitmap(1, 1)

            overlayItem.image?.let { image ->
                GemUtilImages.asBitmap(image, (overlayImageSize * (image.aspectRatio!!.width / image.aspectRatio!!.height)).toInt(), overlayImageSize)?.let { bmp ->
                    imageBitmap = bmp.asImageBitmap()
                }
            }

            var description = ""
            var address = ""
            var date = ""
            var score = ""

            overlayItem.coordinates?.let { coordinates ->
                mapView.getClosestAddress(coordinates, 50, false)?.let { landmark ->
                    landmark.addressInfo?.let { addressInfo ->
                        val street = addressInfo.getField(EAddressField.StreetName)
                        val number = addressInfo.getField(EAddressField.StreetNumber)
                        var place = addressInfo.getField(EAddressField.City)

                        if (place.isNullOrEmpty()) {
                            place = addressInfo.getField(EAddressField.Settlement)
                        }

                        address = if (!street.isNullOrEmpty() && !place.isNullOrEmpty()) {
                            if (!number.isNullOrEmpty()) {
                                String.format("%s %s, %s", street, number, place)
                            } else {
                                String.format("%s, %s", street, place)
                            }
                        } else {
                            place ?: ""
                        }
                    }
                }

                if (address.isEmpty()) {
                    mapView.getNearestLocations(coordinates)?.let { landmarks ->
                        if (landmarks.isNotEmpty()) {
                            address = landmarks[0].name ?: ""
                        }
                    }
                }
            }

            for (parameter in parameters) {
                val key = parameter.key
                when (key) {
                    "description" -> {
                        description = parameter.valueString
                    }

                    "score" -> {
                        score = parameter.valueString
                    }

                    "create_stamp_utc" -> {
                        val localTime = Time()
                        localTime.longValue = parameter.valueLong * 1000 + Time.Companion.getTimeZoneMilliseconds()

                        val now = Time()
                        now.setLocalTime()

                        date = if ((now.year == localTime.year) &&
                            (now.month == localTime.month) &&
                            (now.day == localTime.day)
                        ) {
                            String.format("%d:%02d", localTime.hour, localTime.minute)
                        } else {
                            String.format("%d/%d/%d", localTime.month, localTime.day, localTime.year)
                        }
                    }
                }
            }

            socialReportInfo = SocialReportInfo(
                imageBitmap,
                description,
                address,
                date,
                score
            )
        }
    }

    private fun fillTrafficEventInfo(event: TrafficEvent, parameters: GemList<Parameter>) {
        socialReportInfo = null
        locationDetailsInfo = null
        safetyCameraInfo = null
        var imageBitmap = ImageBitmap(1, 1)

        event.image?.let { image ->
            GemUtilImages.asBitmap(image, (overlayImageSize * (image.aspectRatio!!.width / image.aspectRatio!!.height)).toInt(), overlayImageSize)?.let { bmp ->
                imageBitmap = bmp.asImageBitmap()
            }
        }

        var description = ""
        var delayText = ""
        var delayValue = ""
        var delayUnit = ""
        var lengthText = ""
        var lengthValue = ""
        var lengthUnit = ""
        var fromText = ""
        var fromValue = ""
        var toText = ""
        var toValue = ""
        var validFromText = ""
        var validFromValue = ""
        var validUntilText = ""
        var validUntilValue = ""

        val parametersArray = parameters.asArrayList()
        for (parameter in parametersArray) {
            when (parameter.key) {
                "description" -> {
                    description = parameter.valueString
                }

                "delay" -> {
                    val delay = parameter.valueLong
                    if (delay.toULong() != ULong.MAX_VALUE) {
                        delayText = parameter.name.toString()
                        val pair = GemUtil.getTimeText(delay.toInt())
                        delayValue = pair.first
                        delayUnit = pair.second
                    }
                }

                "distance" -> {
                    lengthText = parameter.name.toString()
                    val pair = GemUtil.getDistText(parameter.valueLong.toInt(), EUnitSystem.Metric)
                    lengthValue = pair.first
                    lengthUnit = pair.second
                }

                "from" -> {
                    fromText = parameter.name.toString()
                    fromValue = parameter.valueString
                }

                "to" -> {
                    toText = parameter.name.toString()
                    toValue = parameter.valueString
                }

                "start_stamp" -> {
                    val value = parameter.valueLong
                    if (value > 0) {
                        validFromText = parameter.name.toString()

                        val fromTime = Time()
                        fromTime.longValue = value * 1000

                        validFromValue = getDateTime(fromTime, event.referencePoint!!)

                        for (p in parametersArray) {
                            if (p.key == "end_stamp") {
                                val v = p.valueLong
                                if ((v > 0) && (value != v)) {
                                    validUntilText = p.name.toString()

                                    val untilTime = Time()
                                    untilTime.longValue = v * 1000

                                    validUntilValue = getDateTime(untilTime, event.referencePoint!!)
                                }

                                break
                            }
                        }
                    }
                }
            }
        }

        trafficEventInfo = TrafficEventInfo(
            imageBitmap,
            description,
            delayText,
            delayValue,
            delayUnit,
            lengthText,
            lengthValue,
            lengthUnit,
            fromText,
            fromValue,
            toText,
            toValue,
            validFromText,
            validFromValue,
            validUntilText,
            validUntilValue
        )
    }

    private fun getUTCOffsetInMilliSeconds(coordinates: Coordinates): Int {
        val timezoneResult = TimezoneResult()
        val time = Time()

        time.setUniversalTime()

        TimezoneService.getTimezoneInfoWithCoordinates(
            timezoneResult, coordinates, time,
            ProgressListener()
        )

        return (timezoneResult.offset * 1000)
    }

    @SuppressLint("DefaultLocale")
    private fun getDateTime(t: Time, coordinates: Coordinates): String {
        val utcOffset = getUTCOffsetInMilliSeconds(coordinates)
        val localTime = Time()

        localTime.longValue = t.longValue + utcOffset

        val date = String.format("%d/%d/%d", localTime.month, localTime.day, localTime.year)
        val time = String.format("%d:%02d", localTime.hour, localTime.minute)

        return String.format("%s %s", date, time);
    }

    private fun highlightOverlay(mapView: MapView, overlayItem: OverlayItem, safetyCamera: Boolean) {

        if (safetyCamera) {
            val markerRenderSettings = MarkerRenderSettings()
            markerRenderSettings.polylineInnerColor = Rgba(243, 51, 243, 128)
            markerRenderSettings.polylineOuterColor = Rgba(142, 36, 170, 128)
            markerRenderSettings.polylineInnerSize = 0.0
            markerRenderSettings.polylineOuterSize = 0.35
            markerRenderSettings.polygonFillColor = Rgba(243, 51, 243, 128);

            mapView.displayOverlayItemFieldOfView(markerRenderSettings, overlayItem, "safety_fov")
        }

        highlightPlace(overlayItem.coordinates!!,overlayItem.image!!, mapView)
    }


    private fun highlightPlace(coordinates: Coordinates, image: Image, mapView: MapView) {
        invokeHighlight = true
        highlightEffect = {
            val landmark = Landmark()
            landmark.coordinates = coordinates
            landmark.image = image

            landmark.coordinates?.let {
                mapView.centerOnCoordinates(it, 84, visibleArea.center, animation, Double.MAX_VALUE, 0.0)

                val displaySettings = HighlightRenderSettings(EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value)

                mapView.activateHighlightLandmarks(landmark, displaySettings)
            }
        }
    }

    private fun highlightLandmark(landmark: Landmark, mapView: MapView) {
        landmark.run {
            val details = GemUtil.pairFormatLandmarkDetails(this, false)
            fillLocationDetailsInfo(
                image?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)?.asImageBitmap() ?: ImageBitmap(
                    1,
                    1
                ),
                details.first,
                details.second
            )
            landmark.image = ImageDatabase.searchResultsPin
        }

        invokeHighlight = true
        highlightEffect = {
            val contour = landmark.getContourGeographicArea()
            if ((contour != null) && !contour.isEmpty()) {
                contour.let {
                    mapView.centerOnRectArea(it, -1, visibleArea, animation)

                    val displaySettings = HighlightRenderSettings(
                        EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                        Rgba(255, 98, 0, 255),
                        Rgba(255, 98, 0, 255),
                        0.75
                    )

                    mapView.activateHighlightLandmarks(landmark, displaySettings)
                }
            } else {
                landmark.coordinates?.let {
                    mapView.centerOnCoordinates(it, -1, visibleArea.center, animation, Double.MAX_VALUE, 0.0)

                    val displaySettings = HighlightRenderSettings(EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value)

                    mapView.activateHighlightLandmarks(landmark, displaySettings)
                }
            }
        }
    }

    fun deactivateHighlights(mapView: MapView) = SdkCall.execute {
        mapView.deactivateAllHighlights()
        mapView.hideCustomMarkers("safety_fov")
        hideBottomView()
    }

    fun hideBottomView() {
        socialReportInfo = null
        locationDetailsInfo = null
        safetyCameraInfo = null
        trafficEventInfo = null
    }

    fun isBottomViewVisible() = socialReportInfo == null || locationDetailsInfo == null || safetyCameraInfo == null || trafficEventInfo == null

    fun setVisibleArea(gemSurfaceView: GemSurfaceView) = SdkCall.execute {
        gemSurfaceView.mapView?.viewport?.let { vp ->
            visibleArea = Rect(
                vp.x,
                vp.y,
                vp.width,
                vp.height - bottomPartHeight
            )
        }
        invokeHighlight = true
    }
    
    fun invokeHighlightEffect() = SdkCall.execute {
        if (invokeHighlight) {
            invokeHighlight = false
            highlightEffect.invoke()
        }
    }

    private fun isSameMapScene(first: MapSceneObject, second: MapSceneObject): Boolean =
        first.maxScaleFactor == second.maxScaleFactor &&
            first.scaleFactor == second.scaleFactor &&
            first.visibility == second.visibility &&
            first.coordinates?.latitude == second.coordinates?.latitude &&
            first.coordinates?.longitude == second.coordinates?.longitude &&
            first.coordinates?.altitude == second.coordinates?.altitude &&
            first.orientation?.x == second.orientation?.x &&
            first.orientation?.y == second.orientation?.y &&
            first.orientation?.z == second.orientation?.z &&
            first.orientation?.w == second.orientation?.w
}
