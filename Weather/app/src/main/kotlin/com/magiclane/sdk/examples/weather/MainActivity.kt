/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.weather.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.places.Landmark
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

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    private data class MapFreeSpace(val rect: Rect, val center: Xy)

    private lateinit var binding: ActivityMainBinding
    private lateinit var portraitConstraintSet: ConstraintSet
    private var imageSize = 0

    // region OVERRIDDEN METHODS
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        imageSize = resources.getDimension(R.dimen.image_size).toInt()

        portraitConstraintSet = ConstraintSet().also { it.clone(binding.root as ConstraintLayout) }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)
            insets
        }

        binding.root.post {
            applyOrientationLayout()
            updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.locationDetailsContainer.isVisible) {
                binding.locationDetailsContainer.visibility = View.GONE
                updateFollowGpsButtonBottomMargin(isDetailsVisible = false)
                updateFocusViewport()
                deactivateHighlights()
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        super.onDestroy()
        GemSdk.release()
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.root.post {
            applyOrientationLayout()
            updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        if (grantResults.none { it == PackageManager.PERMISSION_GRANTED }) return

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
    // endregion

    // region PRIVATE FUNCTIONS
    @SuppressLint("DefaultLocale")
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
            runOnAliveUi { showDialog(getString(R.string.tap_on_map_to_see_weather_info), DialogType.INFO) }
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        EspressoIdlingResource.increment()
        // Invoked once onWorldwideRoadMapSupportStatus reports UpToDate, meaning the map
        // tiles are available and the SDK is ready for user interaction.
        val onReady = {
            SdkCall.execute {
                val hasLocationPermission =
                    PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                if (hasLocationPermission) {
                    Util.postOnMain { enableGPSButton() }
                } else {
                    requestPermissions(this)
                }
            }

            binding.gemSurfaceView.mapView?.let { mapView ->
                mapView.onTouch = { xy ->
                    SdkCall.execute {
                        mapView.cursorScreenPosition = xy
                        mapView.deactivateAllHighlights()

                        // Priority: GPS position marker > landmark > overlay item > traffic event > street > raw coordinates.
                        val myPosition = mapView.cursorSelectionSceneObject
                        if (myPosition != null && isSameMapScene(
                                myPosition,
                                MapSceneObject.getDefPositionTracker().first!!,
                            )
                        ) {
                            val coords = myPosition.coordinates ?: return@execute
                            val description = getLandmarkDescription(mapView, coords)
                            val positionLandmark = Landmark("", coords)
                            showLocationDetails(
                                ContextCompat.getDrawable(this, R.drawable.ic_current_location_arrow)
                                    ?.toBitmap(imageSize, imageSize),
                                getString(R.string.my_position),
                                description,
                                coords,
                                getString(R.string.my_position),
                                onViewCreated = { highlightLandmarkOnMap(positionLandmark) },
                                onViewClosed = { deactivateHighlights() },
                            )
                            return@execute
                        }

                        var landmark: Landmark? = null

                        val landmarks = binding.gemSurfaceView.mapView?.cursorSelectionLandmarks
                        if (!landmarks.isNullOrEmpty()) {
                            landmark = landmarks[0]
                        } else {
                            val overlays = mapView.cursorSelectionOverlayItems
                            if (!overlays.isNullOrEmpty()) {
                                val overlay = overlays[0]

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
                            } else {
                                val trafficEvents = mapView.cursorSelectionTrafficEvents
                                if (!trafficEvents.isNullOrEmpty()) {
                                    trafficEvents[0].referencePoint?.let { coordinates ->
                                        landmark = Landmark(
                                            name = trafficEvents[0].description ?: "Traffic event",
                                            latitude = coordinates.latitude,
                                            longitude = coordinates.longitude,
                                        ).apply {
                                            name?.let {
                                                val pos = it.indexOf("\r\n")
                                                if (pos > 0) {
                                                    name = it.substring(0, pos)
                                                }
                                            }
                                            image = trafficEvents[0].image
                                            description = getLandmarkDescription(mapView, coordinates)
                                        }
                                    }
                                } else {
                                    val streets = mapView.cursorSelectionStreets
                                    if (!streets.isNullOrEmpty()) {
                                        landmark = streets[0]
                                    } else {
                                        mapView.transformScreenToWgs(xy)?.let { coordinates ->
                                            landmark = Landmark(
                                                name = String.format(
                                                    "%.6f, %.6f",
                                                    coordinates.latitude,
                                                    coordinates.longitude,
                                                ),
                                                latitude = coordinates.latitude,
                                                longitude = coordinates.longitude,
                                            ).apply {
                                                image = ImageDatabase().getImageById(
                                                    SdkImages.Core.Waypoint_Intermediary.value,
                                                )
                                                description = getLandmarkDescription(mapView, coordinates)
                                            }
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
                                landmark.coordinates,
                                details.first,
                                onViewCreated = { highlightLandmarkOnMap(landmark) },
                                onViewClosed = { deactivateHighlights() },
                            )
                            return@execute
                        }
                    }
                }
                EspressoIdlingResource.decrement()
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                runOnAliveUi { onReady() }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    private fun showLocationDetails(
        image: Bitmap?,
        text: String,
        description: String,
        coordinates: Coordinates? = null,
        locationName: String = "",
        onViewCreated: (() -> Unit)? = null,
        onViewClosed: (() -> Unit)? = null,
    ) = Util.postOnMain {
        if (!isActivityAlive()) return@postOnMain
        binding.apply {
            name.text = text
            if (description.isNotEmpty()) {
                this.description.also {
                    it.text = description
                    it.visibility = View.VISIBLE
                }
            } else {
                this.description.visibility = View.GONE
            }
            this.image.setImageBitmap(image)

            if (coordinates != null) {
                forecastButtonsLayout.visibility = View.VISIBLE
                setupForecastButtons(coordinates, locationName)
            } else {
                forecastButtonsLayout.visibility = View.GONE
            }

            closeButton.setOnClickListener {
                locationDetailsContainer.visibility = View.GONE
                updateFollowGpsButtonBottomMargin(isDetailsVisible = false)
                updateFocusViewport()
                onViewClosed?.invoke()
            }

            locationDetailsContainer.visibility = View.VISIBLE
            root.post {
                updateFollowGpsButtonBottomMargin(isDetailsVisible = true)
                updateFocusViewport()
                onViewCreated?.invoke()
            }
        }
    }

    private fun getCityName(mapView: MapView, coordinates: Coordinates): String {
        for (radius in listOf(50, 300, 2500)) {
            val city = mapView.getClosestAddress(coordinates, radius, radius == 2500)
                ?.addressInfo?.getField(EAddressField.City) ?: ""
            if (city.isNotEmpty()) return city
        }
        return ""
    }

    private fun setupForecastButtons(coordinates: Coordinates, name: String) {
        val intent = Intent(this@MainActivity, ForecastActivity::class.java)
        SdkCall.execute {
            intent.putExtra(ForecastActivity.LATITUDE_ARG_ID, coordinates.latitude)
            intent.putExtra(ForecastActivity.LONGITUDE_ARG_ID, coordinates.longitude)
            val mapView = binding.gemSurfaceView.mapView
            val cityName = if (mapView != null) getCityName(mapView, coordinates).ifEmpty { name } else name
            binding.apply {
                forecastButton.setOnClickListener {
                    intent.putExtra(ForecastActivity.FORECAST_TYPE_ID, EForecastType.CURRENT.ordinal)
                    intent.putExtra(ForecastActivity.LOCATION_NAME, cityName)
                    startActivity(intent)
                }
                hourlyForecastButton.setOnClickListener {
                    intent.putExtra(ForecastActivity.FORECAST_TYPE_ID, EForecastType.HOURLY.ordinal)
                    startActivity(intent)
                }
                dailyForecastButton.setOnClickListener {
                    intent.putExtra(ForecastActivity.FORECAST_TYPE_ID, EForecastType.DAILY.ordinal)
                    startActivity(intent)
                }
            }
        }
    }

    private fun showDialog(text: String, type: DialogType = DialogType.ERROR, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return
        Utils.showDialog(text, this, type, onDismiss)
    }

    // MapSceneObject has no equality operator, so compare all positional and visual fields manually.
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

    // Tries progressively larger search radii; the last query enables road interpolation
    // to find an address in areas where only road data is available.
    private fun getLandmarkDescription(mapView: MapView, coordinates: Coordinates): String {
        var address = mapView.getClosestAddress(coordinates, 50, false)
        if (address != null) {
            return GemUtil.formatLandmarkDetails(address, true)
        }

        address = mapView.getClosestAddress(coordinates, 300, false)
        if (address != null) {
            val city = address.addressInfo?.getField(EAddressField.City) ?: ""
            if (city.isNotEmpty()) return city
        }

        address = mapView.getClosestAddress(coordinates, 2500, true)
        if (address != null) {
            val city = address.addressInfo?.getField(EAddressField.City) ?: ""
            if (city.isNotEmpty()) return "Near $city"
        }

        return ""
    }

    private fun deactivateHighlights() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.deactivateAllHighlights()
    }

    private fun highlightLandmarkOnMap(landmark: Landmark) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            val rect = getMapFreeSpace().rect

            mapView.deactivateAllHighlights()

            // Replace whatever image the landmark has with the standard search-pin so all
            // highlighted items look consistent on the map.
            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()
            val highlightSettings: HighlightRenderSettings

            @Suppress("VerboseNullabilityAndEmptiness")
            if (contour != null && !contour.isEmpty()) {
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
                ).also { it.imageSize = 6.0 }
            } else {
                highlightSettings = HighlightRenderSettings(
                    EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                ).also { it.imageSize = 6.0 }

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

            mapView.activateHighlightLandmarks(landmark, highlightSettings)
        }
    }

    private fun getMapFreeSpace(): MapFreeSpace {
        val mapWidth = binding.gemSurfaceView.width
        val mapHeight = binding.gemSurfaceView.height
        // width/height are 0 before the first layout pass; fall back to measured dimensions.
        if (mapWidth <= 0 || mapHeight <= 0) {
            val fallbackWidth = binding.gemSurfaceView.measuredWidth
            val fallbackHeight = binding.gemSurfaceView.measuredHeight
            val fallbackRect = Rect(0, 0, fallbackWidth, fallbackHeight)
            return MapFreeSpace(
                fallbackRect,
                Xy(fallbackRect.x + fallbackRect.width / 2, fallbackRect.y + fallbackRect.height / 2),
            )
        }

        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val leftInset = insets?.left ?: 0
        val rightInset = insets?.right ?: 0
        val bottomInset = insets?.bottom ?: 0

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val padding = resources.getDimensionPixelSize(R.dimen.map_free_space_padding)

        val left: Int
        val right: Int
        val bottom: Int

        val topLimit: Int = (binding.toolbar.bottom - binding.gemSurfaceView.top).coerceIn(
            0,
            (mapHeight - 1).coerceAtLeast(0),
        )

        if (isLandscape) {
            left = if (binding.locationDetailsContainer.isVisible) {
                (binding.locationDetailsContainer.right - binding.gemSurfaceView.left)
                    .coerceAtLeast(leftInset)
            } else {
                leftInset
            }
            right = (mapWidth - rightInset).coerceAtLeast(left + 1)
            bottom = (mapHeight - bottomInset).coerceAtLeast(topLimit + 1)
        } else {
            left = leftInset.coerceIn(0, (mapWidth - 1).coerceAtLeast(0))
            right = (mapWidth - rightInset).coerceIn(left + 1, mapWidth)
            val bottomLimitRaw = if (binding.locationDetailsContainer.isVisible) {
                binding.locationDetailsContainer.top - binding.gemSurfaceView.top
            } else {
                mapHeight
            }
            bottom = bottomLimitRaw.coerceIn(topLimit + 1, mapHeight)
        }

        val paddedLeft = (left + padding).coerceAtMost(right - 1)
        val paddedRight = (right - padding).coerceAtLeast(paddedLeft + 1)
        val paddedTop = (topLimit + padding).coerceAtMost(bottom - 1)
        val paddedBottom = (bottom - padding).coerceAtLeast(paddedTop + 1)

        val rect = Rect(paddedLeft, paddedTop, paddedRight, paddedBottom)
        return MapFreeSpace(rect, Xy(rect.x + rect.width / 2, rect.y + rect.height / 2))
    }

    private fun updateFollowGpsButtonBottomMargin(isDetailsVisible: Boolean) {
        val params = binding.followGpsButton.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val bottomInset = insets?.bottom ?: 0
        val rightInset = insets?.right ?: 0
        // In landscape the FAB sits above the system bar regardless of panel visibility;
        // in portrait it sits just above the bottom sheet when it's open.
        val targetBottomMargin = if (!isLandscape && isDetailsVisible) bigPadding else bottomInset + bigPadding
        val targetEndMargin = rightInset + bigPadding
        if (params.bottomMargin != targetBottomMargin || params.marginEnd != targetEndMargin) {
            params.bottomMargin = targetBottomMargin
            params.marginEnd = targetEndMargin
            binding.followGpsButton.layoutParams = params
        }
    }

    private fun enableGPSButton() {
        binding.gemSurfaceView.mapView?.apply {
            val isFollowingPosition = SdkCall.execute { isFollowingPosition() }
            binding.followGpsButton.visibility = if (isFollowingPosition == true) View.GONE else View.VISIBLE
            updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)

            onExitFollowingPosition = {
                binding.followGpsButton.visibility = View.VISIBLE
                updateFollowGpsButtonBottomMargin(binding.locationDetailsContainer.isVisible)
            }

            onEnterFollowingPosition = {
                binding.followGpsButton.visibility = View.GONE
                if (binding.locationDetailsContainer.isVisible) {
                    binding.locationDetailsContainer.visibility = View.GONE
                    updateFollowGpsButtonBottomMargin(isDetailsVisible = false)
                    updateFocusViewport()
                    deactivateHighlights()
                }
            }

            binding.followGpsButton.setOnClickListener {
                SdkCall.execute {
                    deactivateAllHighlights()
                    followPosition()
                }
            }
        }
    }

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions.toTypedArray(),
        )
    }

    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() resets view visibility, so save and restore it manually.
        val panelVis = binding.locationDetailsContainer.visibility
        val fabVis = binding.followGpsButton.visibility

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelWidth = (resources.displayMetrics.widthPixels * 0.45f).toInt()
                constrainWidth(R.id.location_details_container, panelWidth)
                constrainHeight(R.id.location_details_container, 0)
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
                connect(R.id.location_details_container, ConstraintSet.TOP, R.id.toolbar, ConstraintSet.BOTTOM, 0)
                clear(R.id.location_details_container, ConstraintSet.END)

                clear(R.id.follow_gps_button, ConstraintSet.BOTTOM)
                connect(R.id.follow_gps_button, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            }
        }.applyTo(rootLayout)

        val scrollParams = binding.forecastButtonsLayout.layoutParams as ConstraintLayout.LayoutParams
        scrollParams.height = if (isLandscape) 0 else ConstraintLayout.LayoutParams.WRAP_CONTENT
        binding.forecastButtonsLayout.layoutParams = scrollParams

        binding.locationDetailsContainer.visibility = panelVis
        binding.followGpsButton.visibility = fabVis

        updateFocusViewport()
    }

    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            val left = if (binding.locationDetailsContainer.isVisible) {
                binding.locationDetailsContainer.right
            } else {
                insets?.left ?: 0
            }
            val top = binding.toolbar.bottom
            val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            Rect(left, top, right, bottom)
        } else {
            val left = insets?.left ?: 0
            val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
            val top = binding.toolbar.bottom
            val bottom = if (binding.locationDetailsContainer.isVisible) {
                binding.locationDetailsContainer.top.coerceAtLeast(top)
            } else {
                (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
    // endregion
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("MapSelectionIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
