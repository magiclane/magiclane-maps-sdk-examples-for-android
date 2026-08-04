/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselection

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.RectangleGeographicArea
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Size
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.ERouteDisplayMode
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.d3scene.OverlayItem
import com.magiclane.sdk.d3scene.PTShape
import com.magiclane.sdk.d3scene.PTStopInfo
import com.magiclane.sdk.d3scene.PTStopScheduleFilter
import com.magiclane.sdk.examples.mapselection.PTUi.lineName
import com.magiclane.sdk.examples.mapselection.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.mapselection.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import java.util.IdentityHashMap
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var routesList = ArrayList<Route>()

    // Portrait ConstraintSet captured once at creation; the landscape layout is derived from it.
    private lateinit var portraitConstraintSet: ConstraintSet

    // Pixel size used for the location-detail thumbnails.
    private var imageSize = 0

    // Content controller of the half screen public transport station panel.
    private lateinit var ptStationPanel: PublicTransportStationPanel

    // Marker collections drawing the station's route shapes, one per line color.
    private val ptShapeCollections = ArrayList<MarkerCollection>()

    // The opened station's shapes-bearing stop info, kept for redrawing the shapes when the
    // panel's line filter changes — the panel's periodic refreshes don't request shapes again,
    // so the store's stop info loses them after the first refresh.
    private var ptStopInfoWithShapes: PTStopInfo? = null

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    routesList = routes

                    SdkCall.execute {
                        if (routes.isNotEmpty()) {
                            selectRoute(routes[0])
                        }
                    }
                }
                else -> {
                    // There was a problem computing the route.
                    val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                    showDialog(getString(R.string.routing_service_error, message))
                }
            }
            EspressoIdlingResource.decrement()
        },
    )

    private lateinit var positionListener: PositionListener

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        gemSurfaceView = binding.gemSurface

        // The status bar sits directly on the map / panel surface (there is no toolbar), so its
        // icon appearance follows the theme.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkThemeOn()

        imageSize = resources.getDimension(R.dimen.image_size).toInt()

        ptStationPanel = PublicTransportStationPanel(
            this,
            binding,
            onCloseRequested = { hidePublicTransportStationPanel() },
            onLinesSelectionChanged = { selectedLines ->
                // Redraw the route shapes restricted to the selected lines and re-frame the
                // camera on what is left visible.
                ptStopInfoWithShapes?.let { stopInfo ->
                    SdkCall.execute {
                        val shapesArea = showPublicTransportShapes(stopInfo, selectedLines)
                        if (shapesArea != null && !shapesArea.isEmpty()) {
                            binding.gemSurface.mapView?.centerOnRectArea(
                                shapesArea,
                                zoomLevel = -1,
                                viewRc = getMapFreeRect(mapFreeSpacePadding()),
                                Animation(EAnimation.Linear, 900),
                            )
                        }
                    }
                }
            },
        )

        // Re-apply the GPS button margins on every inset dispatch (e.g. after rotation, when the
        // navigation bar moves between the side and the bottom of the screen).
        ViewCompat.setOnApplyWindowInsetsListener(binding.followGpsButton) { _, insets ->
            updateFollowGpsButtonMargins(insets)
            insets
        }

        // Clone portrait constraints before any runtime (landscape) changes are applied.
        portraitConstraintSet = ConstraintSet().also { it.clone(binding.root as ConstraintLayout) }

        // Apply orientation-specific layout once the root view has been measured.
        binding.root.post { applyOrientationLayout() }

        EspressoIdlingResource.increment()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        // Back press first dismisses the station panel or the details panel (if shown),
        // otherwise closes the app.
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.ptStationContainer.isVisible -> hidePublicTransportStationPanel()
                binding.locationDetailsContainer.isVisible -> {
                    hideLocationDetails()
                    deactivateHighlights()
                }
                else -> finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.root.post { applyOrientationLayout() }
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            // The SDK isn't initialized yet here, so resolve the message directly (no SdkCall wrapping).
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) { finish() }
            }
        }

        gemSurfaceView.onDefaultMapViewCreated = {
            // Position the Magic Lane logo respecting system insets and any visible panel.
            updateFocusViewport()
            // Enable the "follow position" button as soon as a valid GPS fix is available.
            enableGPSButtonWhenPositionReady()
        }

        // Re-align the Magic Lane logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                // The world map is ready: compute the demo route.
                calculateRoute()

                // Request the location permission if it hasn't been granted yet.
                SdkCall.execute {
                    val hasLocationPermission = PermissionsHelper.hasPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                    if (!hasLocationPermission) {
                        requestPermissions(this)
                    }
                }

                registerMapSelectionListeners()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = null
        }
    }

    // Wires the map touch handlers used to select routes, the current position, traffic events,
    // landmarks and overlays. Invoked once the world map is ready and the map view exists.
    private fun registerMapSelectionListeners() = SdkCall.execute {
        val mapView = binding.gemSurface.mapView ?: return@execute

        // Single tap: inspect whatever is under the cursor and show its details.
        mapView.onTouch = { xy ->
            SdkCall.execute {
                // Tell the map view where the touch event happened.
                mapView.cursorScreenPosition = xy

                // 1. A route was tapped: make it the main route and center on it.
                val routes = mapView.cursorSelectionRoutes
                if (!routes.isNullOrEmpty()) {
                    selectRoute(routes[0], false)
                    return@execute
                }

                // 2. The current-position marker was tapped: show "My position" details.
                val myPosition = mapView.cursorSelectionSceneObject
                if ((myPosition != null) && isSameMapScene(
                        myPosition,
                        MapSceneObject.getDefPositionTracker().first!!,
                    )
                ) {
                    myPosition.coordinates?.let {
                        val description = getLandmarkDescription(mapView, it, true)

                        val landmark = Landmark("", it)
                        showLocationDetails(
                            ContextCompat.getDrawable(
                                this,
                                R.drawable.ic_current_location_arrow,
                            )?.toBitmap(imageSize, imageSize),
                            getString(R.string.my_position),
                            description,
                            onViewCreated = {
                                highlightLandmarkOnMap(landmark)
                            },
                            onViewClosed = {
                                deactivateHighlights()
                            },
                        )

                        return@execute
                    }
                }

                // 3. A traffic event was tapped: open its preview page in the web view.
                val trafficEvents = mapView.cursorSelectionTrafficEvents
                if (!trafficEvents.isNullOrEmpty()) {
                    openWebActivity(trafficEvents[0].previewUrl.toString())
                    return@execute
                }

                // 4. A landmark or map overlay was tapped.
                var landmark: Landmark? = null

                val landmarks = mapView.cursorSelectionLandmarks
                if (!landmarks.isNullOrEmpty()) {
                    landmark = landmarks[0]
                } else {
                    val overlays = mapView.cursorSelectionOverlayItems
                    if (!overlays.isNullOrEmpty()) {
                        val overlay = overlays[0]

                        when (overlay.overlayInfo?.uid) {
                            ECommonOverlayId.Safety.value -> {
                                // Safety overlays (e.g. speed cameras) have their own preview page.
                                openWebActivity(overlay.getPreviewUrl(Size()).toString())
                            }
                            ECommonOverlayId.PublicTransport.value -> {
                                // Public transport stations get a dedicated half screen panel with
                                // the lines crossing the station and its upcoming departures.
                                openPublicTransportStation(overlay, mapView)
                            }
                            else -> {
                                overlay.coordinates?.let {
                                    val name = when {
                                        !overlay.name.isNullOrEmpty() -> overlay.name!!
                                        !overlay.overlayInfo?.name.isNullOrEmpty() -> overlay.overlayInfo?.name!!
                                        else -> getString(R.string.unknown)
                                    }

                                    landmark = Landmark(
                                        name = name,
                                        latitude = it.latitude,
                                        longitude = it.longitude,
                                    ).apply {
                                        image = overlay.image
                                        description = getLandmarkDescription(mapView, it)
                                    }
                                }
                            }
                        }
                    }
                }

                landmark?.let { selected ->
                    val details = GemUtil.pairFormatLandmarkDetails(selected, true)
                    showLocationDetails(
                        selected.image?.asBitmap(imageSize, imageSize),
                        details.first,
                        details.second,
                        onViewCreated = {
                            highlightLandmarkOnMap(selected)
                        },
                        onViewClosed = {
                            deactivateHighlights()
                        },
                    )
                }
            }
        }

        // Long press: show details for the closest street.
        mapView.onLongDown = { xy ->
            SdkCall.execute {
                mapView.cursorScreenPosition = xy

                val streets = mapView.cursorSelectionStreets
                if (!streets.isNullOrEmpty()) {
                    val street = streets[0]
                    showLocationDetails(
                        street.image?.asBitmap(imageSize, imageSize),
                        GemUtil.formatName(street),
                        GemUtil.getLandmarkDescription(street, true),
                        onViewCreated = {
                            highlightLandmarkOnMap(street)
                        },
                        onViewClosed = {
                            deactivateHighlights()
                        },
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                showDialog(getString(R.string.location_permission_required))
                return
            }
        }

        SdkCall.execute {
            // Notify the SDK that the permission status has changed.
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
            enableGPSButtonWhenPositionReady()
        }
    }

    // Enables the "follow position" button immediately if a valid position is already known,
    // otherwise waits for the first valid position update before doing so.
    private fun enableGPSButtonWhenPositionReady() {
        if (PositionService.position?.isValid() == true) {
            Util.postOnMain { enableGPSButton() }
            return
        }

        positionListener = PositionListener {
            if (!it.isValid()) return@PositionListener

            PositionService.removeListener(positionListener)
            Util.postOnMain { enableGPSButton() }
        }
        PositionService.addListener(positionListener, EDataType.Position)
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        val error = routingService.calculateRoute(waypoints)
        if (error != GemError.NoError) {
            // The computation never started, so onCompleted won't fire: report the error and
            // release the idling resource that onCreate incremented for this route request.
            val message = GemError.getMessage(error, this)
            runOnAliveUi {
                showDialog(getString(R.string.routing_service_error, message))
            }
            EspressoIdlingResource.decrement()
        }
    }

    // Handles a tap on a public transport station: fetches the station's extended data (the
    // lines crossing it, its upcoming departures and the routes' shapes) and opens the half
    // screen station panel. Falls back to the regular details panel when the extended data is
    // unavailable. Must be called on the SDK thread.
    private fun openPublicTransportStation(overlay: OverlayItem, mapView: MapView) {
        val name = when {
            !overlay.name.isNullOrEmpty() -> overlay.name!!
            !overlay.overlayInfo?.name.isNullOrEmpty() -> overlay.overlayInfo?.name!!
            else -> getString(R.string.unknown)
        }
        val address = overlay.coordinates?.let { getLandmarkDescription(mapView, it) } ?: ""
        val icon = overlay.image?.asBitmap(imageSize, imageSize)

        // The station gets highlighted on the map like a regular landmark while its panel is up.
        val stationLandmark = overlay.coordinates?.let { Landmark(name, it.latitude, it.longitude) }

        // The station's UTC offset, needed to compare its wall-clock departure times with "now".
        val utcOffsetMs = overlay.coordinates?.let { PTUi.stationUtcOffsetMs(it) }

        val fallbackToDetailsPanel = { showLocationDetails(icon, name, address) }

        runOnAliveUi { binding.progressBar.visibility = View.VISIBLE }
        EspressoIdlingResource.increment()

        // The shapes are requested here only — the panel's periodic refresh skips them, they
        // are static and the largest part of the payload.
        val error = overlay.getPTStopInfo(PTStopScheduleFilter(shapes = true)) { stopInfo ->
            // The SDK delivers this callback on the main thread.
            EspressoIdlingResource.decrement()
            if (!isActivityAlive()) return@getPTStopInfo

            binding.progressBar.visibility = View.GONE

            if (stopInfo == null || (stopInfo.stops.isEmpty() && stopInfo.trips.isEmpty())) {
                fallbackToDetailsPanel()
            } else {
                PTStationStore.set(overlay, name, address, icon, stopInfo, utcOffsetMs)
                showPublicTransportStationPanel(stationLandmark, stopInfo)
            }
        }

        if (error != GemError.NoError) {
            // The request never started, so the callback won't fire.
            EspressoIdlingResource.decrement()
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE
                fallbackToDetailsPanel()
            }
        }
    }

    // Shows the half screen station panel, pins the station on the map like a regular landmark
    // and draws the shapes of the lines crossing it. Runs on the main thread.
    private fun showPublicTransportStationPanel(stationLandmark: Landmark?, stopInfo: PTStopInfo) {
        if (binding.locationDetailsContainer.isVisible) {
            hideLocationDetails()
        }

        ptStopInfoWithShapes = stopInfo

        ptStationPanel.show()
        binding.ptStationContainer.visibility = View.VISIBLE

        // Once the panel is laid out, recompute the free map area, then draw the route shapes
        // and highlight the station within what remained visible of the map. The shapes are
        // drawn first: when the station has drawable line shapes, the camera frames their
        // bounding area instead of just the station coordinates.
        binding.root.post {
            updateFollowGpsButtonMargins()
            updateFocusViewport()
            SdkCall.execute {
                val shapesArea = showPublicTransportShapes(stopInfo)
                stationLandmark?.let { highlightLandmarkOnMap(it, shapesArea) }
            }
        }
    }

    // Hides the station panel and removes everything drawn on the map for it (the landmark
    // highlight and the route shapes).
    private fun hidePublicTransportStationPanel() {
        ptStationPanel.hide()
        binding.ptStationContainer.visibility = View.GONE
        ptStopInfoWithShapes = null

        SdkCall.execute { removePublicTransportShapes() }
        deactivateHighlights()

        updateFollowGpsButtonMargins()
        updateFocusViewport()
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
        val preferences = binding.gemSurface.mapView?.preferences ?: return null

        removePublicTransportShapes()

        val fallbackColor = ContextCompat.getColor(this, R.color.gray)

        val shapeColors = IdentityHashMap<PTShape, Int>()
        stopInfo.stops.asSequence().flatMap { it.routes }.forEach { route ->
            val shape = route.shape ?: return@forEach
            if (selectedLines.isNotEmpty() && route.lineName !in selectedLines) return@forEach
            if (shape !in shapeColors) {
                shapeColors[shape] = PTUi.parseColor(route.routeColor, fallbackColor)
            }
        }

        var shapesArea: RectangleGeographicArea? = null

        stopInfo.shapes
            .filter { selectedLines.isEmpty() || it in shapeColors }
            .mapNotNull { shape ->
                // toMarker() is null for shapes that failed to decode (nothing drawable).
                shape.toMarker()?.let { marker -> (shapeColors[shape] ?: fallbackColor) to marker }
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (color, markers) ->
                val collection = MarkerCollection(EMarkerType.Polyline, "PT shapes")
                markers.forEach { collection.add(it) }

                val settings = MarkerCollectionRenderSettings(
                    polylineInnerColor = Rgba(Color.red(color), Color.green(color), Color.blue(color), 255),
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

        binding.gemSurface.mapView?.preferences?.markers?.let { markers ->
            ptShapeCollections.forEach { markers.removeCollection(it) }
        }
        ptShapeCollections.clear()
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

    private fun showLocationDetails(
        image: Bitmap?,
        text: String,
        description: String,
        onViewCreated: (() -> Unit)? = null,
        onViewClosed: (() -> Unit)? = null,
    ) = Util.postOnMain {
        if (!isActivityAlive()) return@postOnMain

        // The two bottom panels are mutually exclusive.
        if (binding.ptStationContainer.isVisible) {
            hidePublicTransportStationPanel()
        }

        binding.apply {
            name.text = text
            if (description.isNotEmpty()) {
                binding.description.also {
                    it.text = description
                    it.visibility = View.VISIBLE
                }
            } else {
                binding.description.visibility = View.GONE
            }

            binding.image.setImageBitmap(image)

            // Set up the close button.
            closeButton.setOnClickListener {
                hideLocationDetails()
                onViewClosed?.invoke()
            }

            // Show the panel.
            locationDetailsContainer.visibility = View.VISIBLE

            // Once the panel is laid out, recompute the free map area then run the caller's hook.
            root.post {
                updateFollowGpsButtonMargins()
                updateFocusViewport()
                onViewCreated?.invoke()
            }
        }
    }

    // Hides the details panel and restores the map free area / GPS button placement.
    private fun hideLocationDetails() {
        binding.locationDetailsContainer.visibility = View.GONE
        updateFollowGpsButtonMargins()
        updateFocusViewport()
    }

    private fun openWebActivity(url: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }

    // Re-applies constraints for the current orientation.
    // Landscape: the details panel becomes a half-width card pinned to the bottom-left corner,
    // the station panel becomes a half-width card spanning the full screen height and the GPS
    // button moves to the bottom-right corner.
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() resets view visibility; capture and restore it afterwards.
        val gpsVis = binding.followGpsButton.visibility
        val panelVis = binding.locationDetailsContainer.visibility
        val stationPanelVis = binding.ptStationContainer.visibility
        val progressVis = binding.progressBar.visibility

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelWidth = (resources.displayMetrics.widthPixels * 0.5f).toInt()

                // Details panel: bottom-left card, half the screen width, height wraps content.
                constrainWidth(R.id.location_details_container, panelWidth)
                constrainHeight(R.id.location_details_container, ConstraintSet.WRAP_CONTENT)
                connect(
                    R.id.location_details_container,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    0,
                )
                connect(
                    R.id.location_details_container,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    0,
                )
                clear(R.id.location_details_container, ConstraintSet.TOP)
                clear(R.id.location_details_container, ConstraintSet.END)

                // Station panel: left-side card, half the screen width, spanning the full screen
                // height — the status bar is cleared with padding (see updatePanelInsets), so
                // the panel is anchored to the parent's edges (spread replaces the portrait
                // 50%-of-parent height). Resetting the default height mode alone is not enough:
                // LayoutParams.validate() forces percent mode back while the percent value stays
                // below 1, so the percent itself is reset too.
                constrainWidth(R.id.pt_station_container, panelWidth)
                constrainPercentHeight(R.id.pt_station_container, 1f)
                constrainDefaultHeight(R.id.pt_station_container, ConstraintSet.MATCH_CONSTRAINT_SPREAD)
                connect(R.id.pt_station_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
                connect(
                    R.id.pt_station_container,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    0,
                )
                connect(
                    R.id.pt_station_container,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    0,
                )
                clear(R.id.pt_station_container, ConstraintSet.END)

                // GPS button: anchor to the bottom-right corner instead of above the (left-side) panels.
                clear(R.id.follow_gps_button, ConstraintSet.BOTTOM)
                connect(R.id.follow_gps_button, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            }
        }.applyTo(rootLayout)

        binding.followGpsButton.visibility = gpsVis
        binding.locationDetailsContainer.visibility = panelVis
        binding.ptStationContainer.visibility = stationPanelVis
        binding.progressBar.visibility = progressVis

        updatePanelInsets(isLandscape)
        updateFollowGpsButtonMargins()
        updateFocusViewport()
    }

    // Applies system-window insets to the bottom panels as padding. In landscape the panels sit
    // against the left edge, so the right inset must not be applied as right padding; the
    // station panel additionally reaches the top of the screen there, so its content must
    // clear the status bar.
    private fun updatePanelInsets(isLandscape: Boolean) {
        for (panel in listOf(binding.locationDetailsContainer, binding.ptStationContainer)) {
            ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
                val sys = insets.getInsets(SYSTEM_INSET_TYPES)
                v.updatePadding(
                    left = sys.left,
                    top = if (isLandscape && v === binding.ptStationContainer) sys.top else 0,
                    right = if (isLandscape) 0 else sys.right,
                    bottom = sys.bottom,
                )
                insets
            }
            panel.requestApplyInsets()
        }
    }

    // Updates the GPS button margins so it always clears the system bars / display cutout.
    // Pass the insets delivered to an OnApplyWindowInsetsListener; when null (on-demand calls,
    // e.g. when the details panel is toggled) the current root insets are read instead.
    private fun updateFollowGpsButtonMargins(insets: WindowInsetsCompat? = null) {
        val params = binding.followGpsButton.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val detailsVisible = binding.locationDetailsContainer.isVisible || binding.ptStationContainer.isVisible

        val sys = (insets ?: ViewCompat.getRootWindowInsets(binding.root))?.getInsets(SYSTEM_INSET_TYPES)
        val rightInset = sys?.right ?: 0
        val bottomInset = sys?.bottom ?: 0

        // The button is always end-aligned, so it must clear a side navigation bar / cutout.
        val targetEndMargin = bigPadding + rightInset
        // In portrait the button sits directly on top of the visible bottom panel, so no extra
        // inset is needed when one is visible. In every other case it is anchored to the bottom
        // of the screen and must clear the system navigation bar.
        val targetBottomMargin = if (!isLandscape && detailsVisible) {
            bigPadding
        } else {
            bigPadding + bottomInset
        }

        if (params.bottomMargin != targetBottomMargin || params.marginEnd != targetEndMargin) {
            params.bottomMargin = targetBottomMargin
            params.marginEnd = targetEndMargin
            binding.followGpsButton.layoutParams = params
        }
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following-position mode.
        gemSurfaceView.mapView?.apply {
            val isFollowingPosition = SdkCall.execute { isFollowingPosition() }
            binding.followGpsButton.visibility = if (isFollowingPosition == true) {
                View.GONE
            } else {
                View.VISIBLE
            }
            updateFollowGpsButtonMargins()

            onExitFollowingPosition = {
                binding.followGpsButton.visibility = View.VISIBLE
                updateFollowGpsButtonMargins()
            }

            onEnterFollowingPosition = {
                binding.followGpsButton.visibility = View.GONE

                if (binding.locationDetailsContainer.isVisible) {
                    hideLocationDetails()
                    deactivateHighlights()
                }
                if (binding.ptStationContainer.isVisible) {
                    hidePublicTransportStationPanel()
                }
            }

            // Set the on-click action for the GPS button.
            binding.followGpsButton.setOnClickListener {
                SdkCall.execute {
                    followPosition()
                }
            }
        }
    }

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions.toTypedArray(),
        )
    }

    @SuppressLint("InflateParams")
    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun selectRoute(route: Route, presentRoutes: Boolean = true) {
        gemSurfaceView.mapView?.apply {
            route.apply {
                showLocationDetails(
                    ContextCompat.getDrawable(
                        this@MainActivity,
                        if (isDarkThemeOn()) R.drawable.ic_baseline_route_24_night else R.drawable.ic_baseline_route_24,
                    )?.toBitmap(imageSize, imageSize),
                    "From London to Paris",
                    "${route.getRtd()}, ${route.getRtt()}",
                    onViewCreated = {
                        deactivateAllHighlights()
                        if (presentRoutes) {
                            binding.locationDetailsContainer.post {
                                val rect = getMapFreeRect(mapFreeSpacePadding())
                                val edgeAreaInsets = getEdgeAreaInsets(rect)
                                SdkCall.execute {
                                    gemSurfaceView.mapView?.presentRoutes(
                                        routesList,
                                        displayBubble = true,
                                        animation = Animation(EAnimation.Linear, 900),
                                        edgeAreaInsets = edgeAreaInsets,
                                    )
                                }
                            }
                        } else {
                            centerOnRoutes(
                                routesList,
                                ERouteDisplayMode.Full,
                                getMapFreeRect(mapFreeSpacePadding()),
                                Animation(EAnimation.Linear, 900),
                            )
                        }
                    },
                    onViewClosed = {
                        deactivateAllHighlights()
                    },
                )
            }
            preferences?.routes?.mainRoute = route
        }
    }

    // Highlights the landmark with the search-results pin. The camera frames, in order of
    // precedence: the landmark's contour, the caller-provided focus area (e.g. the bounding
    // area of a station's line shapes), or the landmark's coordinates.
    private fun highlightLandmarkOnMap(landmark: Landmark, focusArea: RectangleGeographicArea? = null) = SdkCall.execute {
        binding.gemSurface.mapView?.let { mapView ->
            val rect = getMapFreeRect(mapFreeSpacePadding())

            mapView.deactivateAllHighlights()

            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()
            var highlightSettings: HighlightRenderSettings

            if ((contour != null) && !contour.isEmpty()) {
                mapView.centerOnRectArea(
                    contour,
                    zoomLevel = -1,
                    viewRc = rect,
                    Animation(EAnimation.Linear, 900),
                )

                highlightSettings = HighlightRenderSettings(
                    EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                    Rgba(255, 98, 0, 255),
                    Rgba(255, 98, 0, 255),
                    0.75,
                ).also {
                    it.imageSize = 6.0
                }
            } else {
                highlightSettings = HighlightRenderSettings(
                    EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                ).also {
                    it.imageSize = 6.0
                }

                if (focusArea != null && !focusArea.isEmpty()) {
                    mapView.centerOnRectArea(
                        focusArea,
                        zoomLevel = -1,
                        viewRc = rect,
                        Animation(EAnimation.Linear, 900),
                    )
                } else {
                    landmark.coordinates?.let {
                        mapView.centerOnCoordinates(
                            it,
                            -1,
                            rect.center,
                            Animation(EAnimation.Linear, 900),
                            0.0,
                            0.0,
                        )
                    }
                }
            }

            mapView.activateHighlightLandmarks(
                landmark,
                highlightSettings,
            )
        }
    }

    // Returns the visible map area in surface-view coordinates, accounting for the system
    // bars/display cutout and any visible bottom panel. An optional padding deflates the
    // rect on all sides (useful for camera-centering animations).
    // Portrait: the panel sits at the bottom → it restricts the bottom edge.
    // Landscape: the panel sits on the left → it restricts the left edge.
    private fun getMapFreeRect(padding: Int = 0): Rect {
        val surfaceWidth = gemSurfaceView.width.takeIf { it > 0 } ?: gemSurfaceView.measuredWidth
        val surfaceHeight = gemSurfaceView.height.takeIf { it > 0 } ?: gemSurfaceView.measuredHeight

        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Whichever bottom panel is currently shown restricts the map area (the details panel
        // and the station panel are never visible together).
        val panel = if (binding.ptStationContainer.isVisible) {
            binding.ptStationContainer
        } else {
            binding.locationDetailsContainer
        }

        // The map area starts just below the status bar / display cutout.
        val top = (insets?.top ?: 0).coerceIn(0, (surfaceHeight - 1).coerceAtLeast(0))

        val left: Int
        val right: Int
        val bottom: Int

        if (isLandscape) {
            left = if (panel.isVisible) {
                (panel.right - gemSurfaceView.left).coerceIn(insets?.left ?: 0, surfaceWidth - 1)
            } else {
                (insets?.left ?: 0)
            }
            right = (surfaceWidth - (insets?.right ?: 0)).coerceAtLeast(left + 1)
            bottom = (surfaceHeight - (insets?.bottom ?: 0)).coerceAtLeast(top + 1)
        } else {
            left = insets?.left ?: 0
            right = (surfaceWidth - (insets?.right ?: 0)).coerceAtLeast(left + 1)
            bottom = if (panel.isVisible) {
                (panel.top - gemSurfaceView.top).coerceIn(top + 1, surfaceHeight)
            } else {
                (surfaceHeight - (insets?.bottom ?: 0)).coerceAtLeast(top + 1)
            }
        }

        // Apply symmetric padding while keeping the rect valid (non-empty).
        val paddedLeft = (left + padding).coerceAtMost(right - 1)
        val paddedRight = (right - padding).coerceAtLeast(paddedLeft + 1)
        val paddedTop = (top + padding).coerceAtMost(bottom - 1)
        val paddedBottom = (bottom - padding).coerceAtLeast(paddedTop + 1)

        return Rect(paddedLeft, paddedTop, paddedRight, paddedBottom)
    }

    // Converts a free-area rect into the edge insets expected by MapView.presentRoutes
    // (distances from each surface edge to the free area).
    private fun getEdgeAreaInsets(freeSpaceRect: Rect): Rect {
        val mapWidth = gemSurfaceView.width.takeIf { it > 0 } ?: gemSurfaceView.measuredWidth
        val mapHeight = gemSurfaceView.height.takeIf { it > 0 } ?: gemSurfaceView.measuredHeight

        val leftInset = freeSpaceRect.x.coerceIn(0, mapWidth.coerceAtLeast(0))
        val topInset = freeSpaceRect.y.coerceIn(0, mapHeight.coerceAtLeast(0))
        val rightInset = (mapWidth - (freeSpaceRect.x + freeSpaceRect.width)).coerceAtLeast(0)
        val bottomInset = (mapHeight - (freeSpaceRect.y + freeSpaceRect.height)).coerceAtLeast(0)

        return Rect(leftInset, topInset, rightInset, bottomInset)
    }

    // Aligns the Magic Lane logo (anchored to the focus viewport) with the visible map area so it
    // is never hidden behind the system bars or a visible details panel.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurface.mapView?.preferences?.focusViewport = getMapFreeRect()
        }
    }

    private fun mapFreeSpacePadding() = resources.getDimensionPixelSize(R.dimen.map_free_space_padding)

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

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

    private fun deactivateHighlights() = SdkCall.execute {
        binding.gemSurface.mapView?.deactivateAllHighlights()
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    // Posts the block to the main thread, running it only while the activity is still alive.
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    companion object {
        private const val REQUEST_PERMISSIONS = 110

        // Window insets that the map's focus viewport should stay clear of.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("MapSelectionIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
