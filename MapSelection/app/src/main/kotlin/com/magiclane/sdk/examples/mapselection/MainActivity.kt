/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
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
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
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
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Size
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.ERouteDisplayMode
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.MapView
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
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var routesList = ArrayList<Route>()

    private var imageSize = 0

    private data class MapFreeSpace(val rect: Rect, val center: Xy)

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
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode, this)}")
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

        // Ensure initial placement is above nav/system bar when details panel is hidden.
        binding.root.post {
            updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)
        }

        imageSize = resources.getDimension(R.dimen.image_size).toInt()

        EspressoIdlingResource.increment()

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        gemSurfaceView.onDefaultMapViewCreated = {
            val position = PositionService.position
            if (position?.isValid() == true) {
                Util.postOnMain { enableGPSButton() }
            } else {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    Util.postOnMain { enableGPSButton() }
                }
                PositionService.addListener(positionListener, EDataType.Position)
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                // Defines an action that should be done when the world map is ready (Updated/ loaded).
                calculateRoute()

                // Set GPS button if location permission is granted, otherwise request permission
                SdkCall.execute {
                    val hasLocationPermission = PermissionsHelper.hasPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                    if (!hasLocationPermission) {
                        requestPermissions(this)
                    }
                }

                binding.gemSurface.mapView?.let { mapView ->
                    // onTouch event callback
                    mapView.onTouch = { xy ->
                        // xy are the coordinates of the touch event
                        SdkCall.execute {
                            // tell the map view where the touch event happened
                            mapView.cursorScreenPosition = xy

                            // get the visible routes at the touch event point
                            val routes = mapView.cursorSelectionRoutes
                            // check if there is any route
                            if (!routes.isNullOrEmpty()) {
                                // set the touched route as the main route and center on it
                                val route = routes[0]
                                selectRoute(route, false)

                                return@execute
                            }

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

                            val trafficEvents = gemSurfaceView.mapView?.cursorSelectionTrafficEvents
                            if (!trafficEvents.isNullOrEmpty()) {
                                openWebActivity(trafficEvents[0].previewUrl.toString())
                                return@execute
                            }

                            var landmark: Landmark? = null

                            val landmarks = mapView.cursorSelectionLandmarks
                            if (!landmarks.isNullOrEmpty()) {
                                landmark = landmarks[0]
                            } else {
                                val overlays = mapView.cursorSelectionOverlayItems
                                if (!overlays.isNullOrEmpty()) {
                                    val overlay = overlays[0]

                                    if (overlay.overlayInfo?.uid == ECommonOverlayId.Safety.value) {
                                        openWebActivity(overlay.getPreviewUrl(Size()).toString())
                                    } else {
                                        overlay.coordinates?.let {
                                            val name = when {
                                                !overlay.name.isNullOrEmpty() -> overlay.name!!
                                                !overlay.overlayInfo?.name.isNullOrEmpty() -> overlay.overlayInfo?.name!!
                                                else -> "Unknown"
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

                            landmark?.let { landmark ->
                                val details = GemUtil.pairFormatLandmarkDetails(landmark, true)
                                showLocationDetails(
                                    landmark.image?.asBitmap(imageSize, imageSize),
                                    details.first,
                                    details.second,
                                    onViewCreated = {
                                        highlightLandmarkOnMap(landmark)
                                    },
                                    onViewClosed = {
                                        deactivateHighlights()
                                    },
                                )
                            }
                        }
                    }
                    mapView.onLongDown = { xy ->
                        // xy are the coordinates of the touch event
                        SdkCall.execute {
                            // tell the map view where the touch event happened
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
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.locationDetailsContainer.isVisible) {
                binding.locationDetailsContainer.visibility = View.GONE
                updateFollowGpsButtonBottomMargin(isDetailsVisible = false)
                deactivateHighlights()
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                showDialog(
                    "Location permission required for current position",
                )
                return
            }
        }

        SdkCall.execute {
            // Notify permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

            lateinit var positionListener: PositionListener
            if (PositionService.position?.isValid() == true) {
                Util.postOnMain { enableGPSButton() }
            } else {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    Util.postOnMain { enableGPSButton() }
                }
                PositionService.addListener(positionListener, EDataType.Position)
            }
        }
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        routingService.calculateRoute(waypoints)
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

            // Set up close button
            closeButton.setOnClickListener {
                locationDetailsContainer.visibility = View.GONE
                updateFollowGpsButtonBottomMargin(isDetailsVisible = false)
                onViewClosed?.invoke()
            }

            // Show the panel
            locationDetailsContainer.visibility = View.VISIBLE

            // Measure height after it's shown
            root.post {
                updateFollowGpsButtonBottomMargin(isDetailsVisible = true)
                onViewCreated?.invoke()
            }
        }
    }

    private fun openWebActivity(url: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }

    private fun updateFollowGpsButtonBottomMargin(isDetailsVisible: Boolean) {
        val params = binding.followGpsButton.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        val targetBottomMargin = if (isDetailsVisible) {
            bigPadding
        } else {
            getSystemBottomInset() + bigPadding
        }

        if (params.bottomMargin != targetBottomMargin) {
            params.bottomMargin = targetBottomMargin
            binding.followGpsButton.layoutParams = params
        }
    }

    private fun getSystemBottomInset(): Int {
        return ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.bottom ?: 0
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            val isFollowingPosition = SdkCall.execute { isFollowingPosition() }
            binding.followGpsButton.visibility = if (isFollowingPosition == true) {
                View.GONE
            } else {
                View.VISIBLE
            }
            updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)

            onExitFollowingPosition = {
                binding.followGpsButton.visibility = View.VISIBLE
                updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)
            }

            onEnterFollowingPosition = {
                binding.followGpsButton.visibility = View.GONE

                if (binding.locationDetailsContainer.isVisible) {
                    binding.locationDetailsContainer.visibility = View.GONE
                    deactivateHighlights()
                }
            }

            // Set on click action for the GPS button.
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

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
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
                                val mapFreeSpace = getMapFreeSpace()
                                val edgeAreaInsets = getEdgeAreaInsets(mapFreeSpace.rect)
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
                                getMapFreeSpace().rect,
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

    private fun highlightLandmarkOnMap(landmark: Landmark) = SdkCall.execute {
        binding.gemSurface.mapView?.let { mapView ->
            val rect = getMapFreeSpace().rect

            mapView.deactivateAllHighlights()

            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()
            var highlightSettings: HighlightRenderSettings

            @Suppress("VerboseNullabilityAndEmptiness")
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

            mapView.activateHighlightLandmarks(
                landmark,
                highlightSettings,
            )
        }
    }

    private fun getMapFreeSpace(): MapFreeSpace {
        val mapWidth = gemSurfaceView.width
        val mapHeight = gemSurfaceView.height
        if (mapWidth <= 0 || mapHeight <= 0) {
            val fallbackWidth = gemSurfaceView.measuredWidth
            val fallbackHeight = gemSurfaceView.measuredHeight
            val fallbackRect = Rect(0, 0, fallbackWidth, fallbackHeight)
            val fallbackCenter = Xy(
                fallbackRect.x + fallbackRect.width / 2,
                fallbackRect.y + fallbackRect.height / 2,
            )
            return MapFreeSpace(fallbackRect, fallbackCenter)
        }

        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        val leftInset = insets?.left ?: 0
        val rightInset = insets?.right ?: 0

        val left = leftInset.coerceIn(0, (mapWidth - 1).coerceAtLeast(0))
        val right = (mapWidth - rightInset).coerceIn(left + 1, mapWidth)

        val topLimit = (binding.toolbar.bottom - gemSurfaceView.top)
            .coerceIn(0, (mapHeight - 1).coerceAtLeast(0))
        val bottomLimitRaw = if (binding.locationDetailsContainer.isVisible) {
            binding.locationDetailsContainer.top - gemSurfaceView.top
        } else {
            mapHeight
        }
        val bottom = bottomLimitRaw.coerceIn(topLimit + 1, mapHeight)

        val padding = resources.getDimensionPixelSize(R.dimen.map_free_space_padding)
        val paddedLeft = (left + padding).coerceAtMost(right - 1)
        val paddedRight = (right - padding).coerceAtLeast(paddedLeft + 1)
        val paddedTop = (topLimit + padding).coerceAtMost(bottom - 1)
        val paddedBottom = (bottom - padding).coerceAtLeast(paddedTop + 1)

        val rect = Rect(
            paddedLeft,
            paddedTop,
            paddedRight,
            paddedBottom,
        )
        val center = Xy(
            rect.x + rect.width / 2,
            rect.y + rect.height / 2,
        )
        return MapFreeSpace(rect, center)
    }

    private fun getEdgeAreaInsets(freeSpaceRect: Rect): Rect {
        val mapWidth = gemSurfaceView.width.takeIf { it > 0 } ?: gemSurfaceView.measuredWidth
        val mapHeight = gemSurfaceView.height.takeIf { it > 0 } ?: gemSurfaceView.measuredHeight

        val leftInset = freeSpaceRect.x.coerceIn(0, mapWidth.coerceAtLeast(0))
        val topInset = freeSpaceRect.y.coerceIn(0, mapHeight.coerceAtLeast(0))
        val rightInset = (mapWidth - (freeSpaceRect.x + freeSpaceRect.width)).coerceAtLeast(0)
        val bottomInset = (mapHeight - (freeSpaceRect.y + freeSpaceRect.height)).coerceAtLeast(0)

        return Rect(leftInset, topInset, rightInset, bottomInset)
    }

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

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("MapSelectionIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
