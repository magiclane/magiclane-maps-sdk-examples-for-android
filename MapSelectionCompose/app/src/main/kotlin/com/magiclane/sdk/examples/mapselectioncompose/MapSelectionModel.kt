/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.details.CardinalDirection as ECardinalDirections
import com.magiclane.sdk.compose.components.details.LocationDetailsData as LocationDetailsInfo
import com.magiclane.sdk.compose.components.details.SafetyCameraData as SafetyCameraInfo
import com.magiclane.sdk.compose.components.details.SocialReportData as SocialReportInfo
import com.magiclane.sdk.compose.components.details.TrafficEventData as TrafficEventInfo
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Parameter
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.RectangleGeographicArea
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.TimezoneResult
import com.magiclane.sdk.core.TimezoneService
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.d3scene.MarkerRenderSettings
import com.magiclane.sdk.d3scene.OverlayItem
import com.magiclane.sdk.d3scene.PTShape
import com.magiclane.sdk.d3scene.PTStopInfo
import com.magiclane.sdk.d3scene.PTStopScheduleFilter
import com.magiclane.sdk.d3scene.PTTrip
import com.magiclane.sdk.examples.mapselectioncompose.PTUi.lineName
import com.magiclane.sdk.examples.mapselectioncompose.data.PublicTransportStationInfo
import com.magiclane.sdk.examples.mapselectioncompose.data.RouteInfo
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTGrayLight
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemList
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.IdentityHashMap
import java.util.Locale

// AndroidViewModel (rather than a plain ViewModel) so that error messages can be resolved with an
// application Context via GemError.getMessage(errorCode, context).
class MapSelectionModel(application: Application) : AndroidViewModel(application) {

    // data
    var safetyCameraInfo: SafetyCameraInfo? by mutableStateOf(null)
    var routeInfo: RouteInfo? by mutableStateOf(null)
    var locationDetailsInfo: LocationDetailsInfo? by mutableStateOf(null)
    var trafficEventInfo: TrafficEventInfo? by mutableStateOf(null)
    var socialReportInfo: SocialReportInfo? by mutableStateOf(null)

    private var trafficEvent: TrafficEvent? = null

    // Tapped public transport station shown by the half screen station panel; its extended stop
    // data (lines and departures) is kept separately so the minute refresh can swap it in place.
    var ptStationInfo: PublicTransportStationInfo? by mutableStateOf(null)
    var ptStopInfo: PTStopInfo? by mutableStateOf(null)

    // The opened station's shapes-bearing stop info, kept for redrawing the shapes when the
    // panel's line filter changes — the periodic refreshes don't request shapes again, so
    // [ptStopInfo] loses them after the first refresh.
    private var ptStopInfoWithShapes: PTStopInfo? = null

    // Marker collections drawing the station's route shapes, one per line color.
    private val ptShapeCollections = ArrayList<MarkerCollection>()

    // Line chips selection last mirrored on the map's route shapes, to redraw on real changes
    // only — the station panel also reports the (unchanged) selection on periodic refreshes.
    private var ptLastNotifiedSelection: Set<String> = emptySet()

    // Departures listed by the station screen when one of them was tapped (after line filtering)
    // and the index of the tapped one; non-null while the trip view is open.
    var ptTripViewTrips: List<PTTrip>? by mutableStateOf(null)
    var ptTripViewIndex = 0
        private set

    // loading state
    var progressBarIsVisible by mutableStateOf(true)

    // error
    var errorMessage by mutableStateOf("")

    // routing service
    private lateinit var routingService: RoutingService

    // utils
    var followGpsButtonIsVisible by mutableStateOf(false)
    private var routesList = ArrayList<Route>()
    private lateinit var animation: Animation
    var padding = 0
    var detailsPanelImageSize: Int = 0
    var overlayImageSize: Int = 0
    var invokeHighlight by mutableStateOf(false)
    var highlightEffect: () -> Unit = {}

    // State holder of the hosting GemMap composable; grants safe access to the map view without
    // the UI passing it around, and exposes the free map area kept clear of the panels.
    private var mapState: GemMapState? = null

    // Free map area (clear of the system bars / cutout and the panels), deflated by [padding].
    // Used as the viewport when centering on routes / landmarks; null before the first layout.
    private val visibleArea: Rect?
        get() = mapState?.visibleArea(padding)

    init {
        val resources = application.resources
        detailsPanelImageSize = resources.getDimension(R.dimen.image_size).toInt()
        overlayImageSize = resources.getDimension(R.dimen.overlay_image_size).toInt()
        padding = resources.getDimension(R.dimen.big_padding).toInt()
    }

    // Resolves an SDK error code to a localized message on the SDK thread.
    private fun errorText(errorCode: Int): String =
        SdkCall.runSynced { GemError.getMessage(errorCode, getApplication()) } ?: ""

    fun initialize(mapState: GemMapState) {
        this.mapState = mapState

        // create routing service
        routingService = RoutingService(
            onStarted = {
            },
            onCompleted = { routes, errorCode, _ ->
                progressBarIsVisible = false
                when (errorCode) {
                    GemError.NoError ->
                        {
                            routesList = routes
                            SdkCall.execute {
                                mapState.mapView?.let { mapView ->
                                    mapView.presentRoutes(routes, displayBubble = true)
                                    mapView.preferences?.routes?.mainRoute?.let {
                                        selectRoute(
                                            it,
                                            mapView,
                                        )
                                    }
                                }
                            }
                        }
                    GemError.Cancel -> {
                        // The routing action was cancelled.
                    }
                    else ->
                        {
                            // There was a problem at computing the routing operation.
                            errorMessage = errorText(errorCode)
                        }
                }
            },
        )

        // The follow-position enter/exit callbacks stay owned by GemMapState (they feed its
        // isFollowingPosition state); the UI reacts to that state instead of hooking them here.
        mapState.mapView?.apply {
            // Single tap selects, in priority order, a route, traffic event, the current
            // position, a point of interest or a map overlay under the cursor.
            onTouch = { xy ->
                SdkCall.execute {
                    cursorScreenPosition = xy

                    if (highlightRoute(this)) {
                        return@execute
                    }

                    if (highlightTrafficEvent(this)) {
                        return@execute
                    }

                    if (highlightMyPosition(this)) {
                        return@execute
                    }

                    if (highlightPointOfInterest(this)) {
                        return@execute
                    }

                    highlightOverlay(this)
                }
            }

            // Long press selects the closest street under the cursor.
            onLongDown = { xy ->
                SdkCall.execute {
                    cursorScreenPosition = xy
                    highlightStreet(this)
                }
            }
        }
    }

    fun calculateRoutes() = SdkCall.execute {
        // start route calculation
        animation = Animation(EAnimation.Linear)
        animation.duration = 900
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        // calculateRoute returns synchronously whether the calculation could be started. On
        // failure, onCompleted won't fire, so report the error and hide the progress bar here.
        val errorCode = routingService.calculateRoute(waypoints)
        if (errorCode != GemError.NoError) {
            errorMessage = errorText(errorCode)
            progressBarIsVisible = false
        }
    }

    private fun highlightRoute(mapView: MapView): Boolean = SdkCall.execute {
        // get the visible routes at the touch event point
        val routes = mapView.cursorSelectionRoutes
        // check if there is any route
        if (!routes.isNullOrEmpty()) {
            deactivateHighlights(mapView)

            // set the touched route as the main route and center on it
            selectRoute(routes[0], mapView)
            return@execute true
        }

        return@execute false
    } ?: false

    private fun highlightMyPosition(mapView: MapView): Boolean = SdkCall.execute {
        val myPosition = mapView.cursorSelectionSceneObject
        MapSceneObject.getDefPositionTracker().first?.let { sceneObject -> // the default my position image
            if ((myPosition != null) && isSameMapScene(myPosition, sceneObject)) {
                myPosition.coordinates?.let { coordinates ->
                    deactivateHighlights(mapView)
                    val description = getLandmarkDescription(mapView, coordinates, isMyPosition = true)

                    fillLocationDetailsInfo(
                        null,
                        "My position",
                        description,
                    )

                    myPosition.coordinates?.let { coordinates ->
                        highlightPlace(coordinates, ImageDatabase.searchResultsPin!!, mapView)
                    }
                    return@execute true
                }
            }
        }
        return@execute false
    } ?: false

    private fun highlightPointOfInterest(mapView: MapView): Boolean = SdkCall.execute {
        val landmarks = mapView.cursorSelectionLandmarks
        if (!landmarks.isNullOrEmpty()) {
            deactivateHighlights(mapView)
            highlightLandmark(landmarks[0], mapView)
            return@execute true
        }
        return@execute false
    } ?: false

    private var parameters = GemList(Parameter::class)

    private val trafficEventProgressListener = ProgressListener.create(
        onCompleted = { errorCode: Int, _ ->
            if (errorCode == GemError.NoError) {
                SdkCall.execute {
                    trafficEvent?.let { event ->
                        fillTrafficEventInfo(event, parameters)

                        // Highlight only now that the panel state is set: the highlight effect
                        // is deferred until the panel has been laid out (and the free map area
                        // updated for its height), so the event is centered in what remains
                        // visible of the map above the panel, not in the screen center.
                        val mapView = mapState?.mapView
                        val referencePoint = event.referencePoint
                        if (mapView != null && referencePoint != null) {
                            highlightPlace(referencePoint, event.image!!, mapView)
                        }
                    }
                }
            } else if (errorCode != GemError.Cancel) {
                errorMessage = errorText(errorCode)
            }

            progressBarIsVisible = false
        },
    )

    private fun highlightTrafficEvent(mapView: MapView): Boolean = SdkCall.execute {
        val trafficEvents = mapView.cursorSelectionTrafficEvents
        if (!trafficEvents.isNullOrEmpty()) {
            trafficEvent = trafficEvents[0]

            parameters.clear()
            val errorCode = trafficEvent?.getPreviewData(
                parameters,
                trafficEventProgressListener,
            ) ?: GemError.NoError

            if (errorCode != GemError.NoError) {
                errorMessage = errorText(errorCode)
            } else {
                progressBarIsVisible = true
                // Only clear the previous highlight here: the new one is placed by the preview
                // data listener, after the traffic panel is displayed and its height is known.
                deactivateHighlights(mapView)
            }
            return@execute true
        }
        return@execute false
    } ?: false

    private fun highlightOverlay(mapView: MapView): Boolean = SdkCall.execute {
        val overlays = mapView.cursorSelectionOverlayItems
        if (!overlays.isNullOrEmpty()) {
            deactivateHighlights(mapView)

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
                ECommonOverlayId.PublicTransport.value -> {
                    // Public transport stations get a dedicated half screen panel with the lines
                    // crossing the station and its upcoming departures.
                    openPublicTransportStation(overlay, mapView)
                }
                else -> {
                    overlay.coordinates?.let {
                        val name = when {
                            !overlay.name.isNullOrEmpty() -> overlay.name!!
                            !overlay.overlayInfo?.name.isNullOrEmpty() -> overlay.overlayInfo?.name!!
                            else -> "Unknown"
                        }

                        val description = getLandmarkDescription(mapView, it)

                        fillLocationDetailsInfo(
                            overlay.image?.asBitmap(
                                detailsPanelImageSize,
                                detailsPanelImageSize,
                            )?.asImageBitmap() ?: ImageBitmap(
                                1,
                                1,
                            ),
                            name,
                            description,
                        )
                    }

                    highlightOverlay(mapView, overlay, false)
                }
            }
            return@execute true
        }
        return@execute false
    } ?: false

    // Handles a tap on a public transport station: fetches the station's extended data (the
    // lines crossing it, its upcoming departures and the routes' shapes) and opens the half
    // screen station panel. Falls back to the regular details panel when the extended data is
    // unavailable. Must be called on the SDK thread.
    private fun openPublicTransportStation(overlay: OverlayItem, mapView: MapView) {
        val name = when {
            !overlay.name.isNullOrEmpty() -> overlay.name!!
            !overlay.overlayInfo?.name.isNullOrEmpty() -> overlay.overlayInfo?.name!!
            else -> "Unknown"
        }
        val address = overlay.coordinates?.let { getLandmarkDescription(mapView, it) } ?: ""
        val icon = overlay.image
            ?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)
            ?.asImageBitmap()

        // The station gets highlighted on the map like a regular landmark while its panel is up.
        val stationLandmark = overlay.coordinates?.let { Landmark(name, it.latitude, it.longitude) }

        // The station's UTC offset, needed to compare its wall-clock departure times with "now".
        val utcOffsetMs = overlay.coordinates?.let { PTUi.stationUtcOffsetMs(it) }

        val fallbackToDetailsPanel = {
            fillLocationDetailsInfo(icon ?: ImageBitmap(1, 1), name, address)
            SdkCall.execute { highlightOverlay(mapView, overlay, false) }
        }

        progressBarIsVisible = true

        // The shapes are requested here only — the periodic refresh skips them, they are static
        // and the largest part of the payload.
        val error = overlay.getPTStopInfo(PTStopScheduleFilter(shapes = true)) { stopInfo ->
            // The SDK delivers this callback on the main thread.
            progressBarIsVisible = false

            if (stopInfo == null || (stopInfo.stops.isEmpty() && stopInfo.trips.isEmpty())) {
                fallbackToDetailsPanel()
            } else {
                // The station panel replaces any visible info panel.
                hideInfoPanelStates()

                ptStopInfoWithShapes = stopInfo
                ptLastNotifiedSelection = emptySet()
                ptStopInfo = stopInfo
                ptStationInfo = PublicTransportStationInfo(overlay, name, address, icon, utcOffsetMs)

                // Once the panel is laid out (and the free map area updated), draw the route
                // shapes and highlight the station within what remained visible of the map. The
                // shapes are drawn first: when the station has drawable line shapes, the camera
                // frames their bounding area instead of just the station coordinates.
                invokeHighlight = true
                highlightEffect = {
                    val shapesArea = showPublicTransportShapes(stopInfo)
                    highlightPublicTransportStation(stationLandmark, shapesArea, mapView)
                }
            }
        }

        if (error != GemError.NoError) {
            // The request never started, so the callback won't fire.
            progressBarIsVisible = false
            fallbackToDetailsPanel()
        }
    }

    // Re-requests the station's extended data so the realtime departures stay fresh.
    fun refreshPublicTransportStation() = SdkCall.execute {
        ptStationInfo?.overlayItem?.getPTStopInfo { stopInfo ->
            // The SDK delivers this callback on the main thread.
            if (ptStationInfo != null && stopInfo != null) {
                ptStopInfo = stopInfo
            }
        }
    }

    // Opens the trip view for a tapped departure: the currently listed (filtered) departures are
    // handed over so the trip view can page through the other trips of the same line.
    fun openPublicTransportTripView(trips: List<PTTrip>, tappedIndex: Int) {
        ptTripViewIndex = tappedIndex
        ptTripViewTrips = trips
    }

    fun closePublicTransportTripView() {
        ptTripViewTrips = null
    }

    // Closes the station panel (with any trip view stacked on it), removes everything drawn on
    // the map for it (the landmark highlight and the route shapes) and releases the tapped
    // station's data.
    fun closePublicTransportStationView() {
        ptTripViewTrips = null
        ptStationInfo = null
        ptStopInfo = null
        ptStopInfoWithShapes = null

        SdkCall.execute {
            removePublicTransportShapes()
            mapState?.mapView?.let { deactivateHighlights(it) }
        }
        // The panel leaving the composition releases its map obstruction, so GemMap gives the
        // freed space back to the map (and the Magic Lane logo) on its own.
    }

    // Mirrors the station panel's line chips selection on the map: only the selected lines'
    // shapes stay drawn (an empty set means "all lines") and the camera re-frames on what is
    // left visible. The initial "all lines" state of a freshly opened station is not redrawn —
    // the opening path draws it itself.
    fun onPublicTransportLinesSelectionChanged(selectedLines: Set<String>) {
        if (selectedLines == ptLastNotifiedSelection) return
        ptLastNotifiedSelection = selectedLines

        val stopInfo = ptStopInfoWithShapes ?: return
        SdkCall.execute {
            val shapesArea = showPublicTransportShapes(stopInfo, selectedLines)
            val area = visibleArea
            if (shapesArea != null && !shapesArea.isEmpty() && area != null) {
                mapState?.mapView?.centerOnRectArea(shapesArea, -1, area, animation)
            }
        }
    }

    // Draws the station's route shapes on top of the map, colored like their line chips so map
    // and list agree. Shape instances are shared between PTStopInfo.shapes and the stop
    // catalogue's routes, so the line color of each deduplicated shape is resolved by identity
    // through the routes referencing it (first referencing route wins). Render settings are per
    // collection, so the shapes are grouped into one polyline collection per color.
    // A non-empty [selectedLines] restricts the drawing to the shapes of those lines (the same
    // filter the panel applies to its departures); an empty set draws every shape, including
    // the ones no route references (with the fallback color).
    // Returns the bounding geographic area of everything drawn (null when nothing was drawn),
    // so the caller can frame the camera on the station's lines.
    // Must be called on the SDK thread.
    private fun showPublicTransportShapes(
        stopInfo: PTStopInfo,
        selectedLines: Set<String> = emptySet(),
    ): RectangleGeographicArea? {
        val preferences = mapState?.mapView?.preferences ?: return null

        removePublicTransportShapes()

        val shapeColors = IdentityHashMap<PTShape, Color>()
        stopInfo.stops.asSequence().flatMap { it.routes }.forEach { route ->
            val shape = route.shape ?: return@forEach
            if (selectedLines.isNotEmpty() && route.lineName !in selectedLines) return@forEach
            if (shape !in shapeColors) {
                shapeColors[shape] = PTUi.parseColor(route.routeColor, PTGrayLight)
            }
        }

        var shapesArea: RectangleGeographicArea? = null

        stopInfo.shapes
            .filter { selectedLines.isEmpty() || it in shapeColors }
            .mapNotNull { shape ->
                // toMarker() is null for shapes that failed to decode (nothing drawable).
                shape.toMarker()?.let { marker -> (shapeColors[shape] ?: PTGrayLight) to marker }
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (color, markers) ->
                val collection = MarkerCollection(EMarkerType.Polyline, "PT shapes")
                markers.forEach { collection.add(it) }

                val argb = color.toArgb()
                val settings = MarkerCollectionRenderSettings(
                    polylineInnerColor = Rgba(
                        android.graphics.Color.red(argb),
                        android.graphics.Color.green(argb),
                        android.graphics.Color.blue(argb),
                        255,
                    ),
                    polylineOuterColor = Rgba.black(),
                ).apply {
                    polylineInnerSize = 1.25
                    polylineOuterSize = 0.5
                }

                preferences.markers?.add(collection, settings)
                ptShapeCollections.add(collection)

                collection.area?.takeIf { !it.isEmpty() }?.let { area ->
                    shapesArea = shapesArea?.makeUnion(area) ?: area
                }
            }

        return shapesArea
    }

    // Removes the station's route shapes from the map. Must be called on the SDK thread.
    private fun removePublicTransportShapes() {
        if (ptShapeCollections.isEmpty()) return

        mapState?.mapView?.preferences?.markers?.let { markers ->
            ptShapeCollections.forEach { markers.removeCollection(it) }
        }
        ptShapeCollections.clear()
    }

    // Highlights the station with the search-results pin. The camera frames the bounding area
    // of the station's drawn line shapes when there is one, the station coordinates otherwise.
    // Must be called on the SDK thread.
    private fun highlightPublicTransportStation(
        stationLandmark: Landmark?,
        shapesArea: RectangleGeographicArea?,
        mapView: MapView,
    ) {
        stationLandmark ?: return
        stationLandmark.image = ImageDatabase.searchResultsPin

        val area = visibleArea ?: return
        if (shapesArea != null && !shapesArea.isEmpty()) {
            mapView.centerOnRectArea(shapesArea, -1, area, animation)
        } else {
            stationLandmark.coordinates?.let {
                mapView.centerOnCoordinates(it, -1, area.center, animation, Double.MAX_VALUE, 0.0)
            }
        }

        mapView.activateHighlightLandmarks(
            stationLandmark,
            HighlightRenderSettings(
                EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
            ),
        )
    }

    private fun highlightStreet(mapView: MapView): Boolean = SdkCall.execute {
        val streets = mapView.cursorSelectionStreets
        if (!streets.isNullOrEmpty()) {
            deactivateHighlights(mapView)
            highlightLandmark(streets[0], mapView)
            return@execute true
        }
        return@execute false
    } ?: false

    fun selectRoute(route: Route, mapView: MapView?) = SdkCall.execute {
        mapView?.apply {
            route.apply {
                summary?.let {
                    var routeType: String
                    var routeDescription = ""

                    val pos = it.indexOf(") ")
                    if (pos > 0) {
                        routeType = it.substring(0, pos + 1)
                        if ((pos + 2) < it.length) {
                            routeDescription = it.substring(pos + 2)
                        }
                    } else {
                        routeType = it
                    }

                    fillRouteInfo(routeType, routeDescription)
                }
            }

            preferences?.routes?.mainRoute = route
            invokeHighlight = true
            highlightEffect = effect@{
                val area = visibleArea ?: return@effect
                centerOnRoutes(routesList, animation = animation, viewRc = area)
            }
        }
    }

    // Entering GPS-following mode dismisses whatever is selected: the info panel or the station
    // panel, with everything drawn on the map for them.
    fun onEnterFollowingPosition() {
        hideBottomView()
        if (ptStationInfo != null) {
            closePublicTransportStationView()
        }
    }

    // Called when the user grants the location permission: shows the follow-GPS button as soon
    // as a valid position is available.
    fun showFollowGpsOnFirstValidPosition() = SdkCall.execute {
        lateinit var positionListener: PositionListener
        if (PositionService.position?.isValid() == true) {
            Util.postOnMain { followGpsButtonIsVisible = true }
        } else {
            positionListener = PositionListener {
                if (it.isValid()) {
                    PositionService.removeListener(positionListener)
                    Util.postOnMain { followGpsButtonIsVisible = true }
                }
            }

            PositionService.addListener(positionListener, EDataType.Position)
        }
    }

    // Clears every info panel state (hiding the panel) without touching the map.
    private fun hideInfoPanelStates() {
        socialReportInfo = null
        trafficEventInfo = null
        safetyCameraInfo = null
        routeInfo = null
        locationDetailsInfo = null
    }

    // The info panel and the station panel are mutually exclusive: opening the info panel for a
    // newly tapped element dismisses a visible station panel (and everything drawn for it).
    private fun hideStationPanelForInfoPanel() {
        if (ptStationInfo != null) {
            closePublicTransportStationView()
        }
    }

    private fun fillLocationDetailsInfo(image: ImageBitmap?, text: String, description: String) {
        hideStationPanelForInfoPanel()
        hideInfoPanelStates()
        locationDetailsInfo = LocationDetailsInfo(image, text, description)
    }

    private fun fillRouteInfo(routeType: String, routeDescription: String) {
        hideStationPanelForInfoPanel()
        hideInfoPanelStates()
        routeInfo = RouteInfo(routeType, routeDescription)
    }

    private fun fillSafetyCameraInfo(overlayItem: OverlayItem) {
        hideStationPanelForInfoPanel()
        hideInfoPanelStates()

        overlayItem.getPreviewData()?.let { parameters ->
            var imageBitmap = ImageBitmap(1, 1)

            overlayItem.image?.let { image ->
                GemUtilImages.asBitmap(
                    image,
                    (overlayImageSize * (image.aspectRatio!!.width / image.aspectRatio!!.height)).toInt(),
                    overlayImageSize,
                )?.let { bmp ->
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
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.N, ECardinalDirections.S)
                                } else {
                                    mutableListOf(ECardinalDirections.N)
                                }
                            }

                            (towards >= 30) && (towards < 60) -> {
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.NE, ECardinalDirections.SW)
                                } else {
                                    mutableListOf(ECardinalDirections.NE)
                                }
                            }

                            (towards >= 60) && (towards < 120) -> {
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.E, ECardinalDirections.W)
                                } else {
                                    mutableListOf(ECardinalDirections.E)
                                }
                            }

                            (towards >= 120) && (towards < 150) -> {
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.NW, ECardinalDirections.SE)
                                } else {
                                    mutableListOf(ECardinalDirections.SE)
                                }
                            }

                            (towards >= 150) && (towards < 210) -> {
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.N, ECardinalDirections.S)
                                } else {
                                    mutableListOf(ECardinalDirections.S)
                                }
                            }

                            (towards >= 210) && (towards < 240) -> {
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.NE, ECardinalDirections.SW)
                                } else {
                                    mutableListOf(ECardinalDirections.SW)
                                }
                            }

                            (towards >= 240) && (towards < 300) -> {
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.E, ECardinalDirections.W)
                                } else {
                                    mutableListOf(ECardinalDirections.W)
                                }
                            }

                            (towards >= 300) -> {
                                if (bothDirections) {
                                    mutableListOf(ECardinalDirections.NW, ECardinalDirections.SE)
                                } else {
                                    mutableListOf(ECardinalDirections.NW)
                                }
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
                            addedToDatabaseValue = String.format(
                                Locale.getDefault(),
                                "%d/%d/%d",
                                time.month,
                                time.day,
                                time.year,
                            )
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
                addedToDatabaseValue,
            )
        }
    }

    @SuppressLint("DefaultLocale")
    private fun fillSocialReportInfo(overlayItem: OverlayItem, mapView: MapView) {
        hideStationPanelForInfoPanel()
        hideInfoPanelStates()

        overlayItem.getPreviewData()?.let { parameters ->
            var imageBitmap = ImageBitmap(1, 1)

            overlayItem.image?.let { image ->
                GemUtilImages.asBitmap(
                    image,
                    (overlayImageSize * (image.aspectRatio!!.width / image.aspectRatio!!.height)).toInt(),
                    overlayImageSize,
                )?.let { bmp ->
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
                        localTime.longValue = parameter.valueLong * 1000 + Time.getTimeZoneMilliseconds()

                        val now = Time()
                        now.setLocalTime()

                        date = if ((now.year == localTime.year) &&
                            (now.month == localTime.month) &&
                            (now.day == localTime.day)
                        ) {
                            String.format("%d:%02d", localTime.hour, localTime.minute)
                        } else {
                            String.format(
                                "%d/%d/%d",
                                localTime.month,
                                localTime.day,
                                localTime.year,
                            )
                        }
                    }
                }
            }

            socialReportInfo = SocialReportInfo(
                imageBitmap,
                description,
                address,
                date,
                score,
            )
        }
    }

    private fun fillTrafficEventInfo(event: TrafficEvent, parameters: GemList<Parameter>) {
        hideStationPanelForInfoPanel()
        hideInfoPanelStates()

        var imageBitmap = ImageBitmap(1, 1)

        event.image?.let { image ->
            GemUtilImages.asBitmap(
                image,
                (overlayImageSize * (image.aspectRatio!!.width / image.aspectRatio!!.height)).toInt(),
                overlayImageSize,
            )?.let { bmp ->
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
            validUntilValue,
        )
    }

    private fun getUTCOffsetInMilliSeconds(coordinates: Coordinates): Int {
        val timezoneResult = TimezoneResult()
        val time = Time()

        time.setUniversalTime()

        TimezoneService.getTimezoneInfoWithCoordinates(
            timezoneResult,
            coordinates,
            time,
            ProgressListener(),
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

        return String.format("%s %s", date, time)
    }

    private fun highlightOverlay(mapView: MapView, overlayItem: OverlayItem, safetyCamera: Boolean) {
        if (safetyCamera) {
            val markerRenderSettings = MarkerRenderSettings()
            markerRenderSettings.polylineInnerColor = Rgba(243, 51, 243, 128)
            markerRenderSettings.polylineOuterColor = Rgba(142, 36, 170, 128)
            markerRenderSettings.polylineInnerSize = 0.0
            markerRenderSettings.polylineOuterSize = 0.35
            markerRenderSettings.polygonFillColor = Rgba(243, 51, 243, 128)

            mapView.displayOverlayItemFieldOfView(markerRenderSettings, overlayItem, "safety_fov")
        }

        highlightPlace(overlayItem.coordinates!!, ImageDatabase.searchResultsPin!!, mapView)
    }

    private fun highlightPlace(coordinates: Coordinates, image: Image, mapView: MapView) {
        invokeHighlight = true
        highlightEffect = effect@{
            val area = visibleArea ?: return@effect
            val landmark = Landmark()
            landmark.coordinates = coordinates
            landmark.image = image

            landmark.coordinates?.let {
                mapView.centerOnCoordinates(
                    it,
                    84,
                    area.center,
                    animation,
                    Double.MAX_VALUE,
                    0.0,
                )

                val displaySettings =
                    HighlightRenderSettings(
                        EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                    )

                mapView.activateHighlightLandmarks(landmark, displaySettings)
            }
        }
    }

    private fun highlightLandmark(landmark: Landmark, mapView: MapView) {
        landmark.run {
            val details = GemUtil.pairFormatLandmarkDetails(this, true)
            fillLocationDetailsInfo(
                image?.asBitmap(
                    detailsPanelImageSize,
                    detailsPanelImageSize,
                )?.asImageBitmap() ?: ImageBitmap(
                    1,
                    1,
                ),
                details.first,
                details.second,
            )
            landmark.image = ImageDatabase.searchResultsPin
        }

        invokeHighlight = true
        highlightEffect = effect@{
            val area = visibleArea ?: return@effect
            val contour = landmark.getContourGeographicArea()
            if ((contour != null) && !contour.isEmpty()) {
                contour.let {
                    mapView.centerOnRectArea(it, -1, area, animation)

                    val displaySettings = HighlightRenderSettings(
                        EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                        Rgba(255, 98, 0, 255),
                        Rgba(255, 98, 0, 255),
                        0.75,
                    )

                    mapView.activateHighlightLandmarks(landmark, displaySettings)
                }
            } else {
                landmark.coordinates?.let {
                    mapView.centerOnCoordinates(
                        it,
                        -1,
                        area.center,
                        animation,
                        Double.MAX_VALUE,
                        0.0,
                    )

                    val displaySettings =
                        HighlightRenderSettings(
                            EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                        )

                    mapView.activateHighlightLandmarks(landmark, displaySettings)
                }
            }
        }
    }

    fun deactivateHighlights(mapView: MapView) = SdkCall.execute {
        mapView.deactivateAllHighlights()
        mapView.hideCustomMarkers("safety_fov")
    }

    fun hideBottomView() = SdkCall.execute {
        hideInfoPanelStates()
        mapState?.mapView?.let {
            deactivateHighlights(it)
        }
        // The panel leaving the composition releases its map obstruction, so GemMap gives the
        // freed space back to the map (and the Magic Lane logo) on its own.
    }

    fun isBottomViewVisible() =
        socialReportInfo != null || locationDetailsInfo != null || safetyCameraInfo != null || trafficEventInfo != null || routeInfo != null

    fun invokeHighlightEffect() = SdkCall.execute {
        if (visibleArea != null && invokeHighlight) {
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

    @SuppressLint("DefaultLocale")
    private fun getLandmarkDescription(
        mapView: MapView,
        coordinates: Coordinates,
        isMyPosition: Boolean = false,
    ): String {
        var description = ""
        var descriptionContainsLatLon = false

        var address = mapView.getClosestAddress(coordinates, 50, false)
        if (address != null) {
            description = GemUtil.formatLandmarkDetails(address, true)
        }

        if (description.isEmpty()) {
            address = mapView.getClosestAddress(coordinates, 300, false)
            if (address != null) {
                description = address.addressInfo?.getField(EAddressField.City) ?: ""
            }

            if (description.isEmpty()) {
                address = mapView.getClosestAddress(coordinates, 2500, true)
                if (address != null) {
                    val city = address.addressInfo?.getField(EAddressField.City) ?: ""
                    if (city.isNotEmpty()) {
                        description = "Near $city"
                    }
                }

                if (description.isEmpty()) {
                    description = String.format("%.5f, %.5f", coordinates.latitude, coordinates.longitude)
                    descriptionContainsLatLon = true
                }
            }
        }

        if (isMyPosition) {
            if (!descriptionContainsLatLon) {
                description += "\nLatitude: ${String.format("%.5f", coordinates.latitude)}"
                description += "\nLongitude: ${String.format("%.5f", coordinates.longitude)}"
            }

            description += "\nAltitude: ${coordinates.altitude.toInt()}m"
        }

        return description
    }
}
