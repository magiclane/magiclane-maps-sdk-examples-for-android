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
import com.magiclane.sdk.ECoverageGeometry
import com.magiclane.sdk.OnWeatherForecastCompleted
import com.magiclane.sdk.WeatherService
import com.magiclane.sdk.WeatherWarning
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.RectangleGeographicArea
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.TimeDistanceCoordinate
import com.magiclane.sdk.core.TimezoneResult
import com.magiclane.sdk.core.TimezoneService
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    private data class MapFreeSpace(val rect: Rect, val center: Xy)

    private lateinit var binding: ActivityMainBinding
    private lateinit var portraitConstraintSet: ConstraintSet
    private lateinit var warningsAdapter: WarningsListAdapter
    private var imageSize = 0
    private var weatherService: WeatherService? = null

    // Coverage polygons currently drawn on the map, one collection per displayed warning.
    private val warningCollections = mutableListOf<Pair<WarningItem, MarkerCollection>>()

    // region OVERRIDDEN METHODS
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        imageSize = resources.getDimension(R.dimen.image_size).toInt()

        portraitConstraintSet = ConstraintSet().also { it.clone(binding.root as ConstraintLayout) }

        warningsAdapter = WarningsListAdapter { warningItem -> highlightWarningOnMap(warningItem) }
        binding.warningsList.adapter = warningsAdapter
        binding.warningsCloseButton.setOnClickListener { closeWarningsPanel() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            updateFollowGpsButtonBottomMargin()
            insets
        }

        binding.root.post {
            applyOrientationLayout()
            updateFollowGpsButtonBottomMargin()
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.warningsContainer.isVisible -> closeWarningsPanel()
                binding.locationDetailsContainer.isVisible -> {
                    binding.locationDetailsContainer.visibility = View.GONE
                    updateFollowGpsButtonBottomMargin()
                    updateFocusViewport()
                    deactivateHighlights()
                }
                else -> finish()
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
            updateFollowGpsButtonBottomMargin()
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

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
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
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
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
        if (binding.warningsContainer.isVisible) {
            binding.warningsContainer.visibility = View.GONE
            removeWarningPolygons()
        }
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
                updateFollowGpsButtonBottomMargin()
                updateFocusViewport()
                onViewClosed?.invoke()
            }

            locationDetailsContainer.visibility = View.VISIBLE
            root.post {
                updateFollowGpsButtonBottomMargin()
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
            // The received Coordinates is backed by native memory owned by the transient cursor
            // selection result, which may be released before the click listeners below run.
            // Copy it into a standalone, Kotlin-owned object for any deferred use.
            val ownedCoordinates = Coordinates(coordinates.latitude, coordinates.longitude)
            intent.putExtra(ForecastActivity.LATITUDE_ARG_ID, ownedCoordinates.latitude)
            intent.putExtra(ForecastActivity.LONGITUDE_ARG_ID, ownedCoordinates.longitude)
            val mapView = binding.gemSurfaceView.mapView
            val cityName = if (mapView != null) getCityName(mapView, ownedCoordinates).ifEmpty { name } else name
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
                warningsButton.setOnClickListener {
                    requestWeatherWarnings(ownedCoordinates)
                }
            }
        }
    }

    private fun requestWeatherWarnings(coordinates: Coordinates) {
        binding.progressBar.visibility = View.VISIBLE
        SdkCall.execute {
            val service = weatherService ?: WeatherService().also { weatherService = it }

            val listener: OnWeatherForecastCompleted = { results, errorCode, _ ->
                if (errorCode != GemError.NoError) {
                    runOnAliveUi {
                        binding.progressBar.visibility = View.GONE
                        showDialog(SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: "")
                    }
                } else {
                    val warningItems = SdkCall.execute {
                        results.firstOrNull()?.warnings?.let { buildWarningItems(it, coordinates) }
                    } ?: emptyList()
                    runOnAliveUi {
                        binding.progressBar.visibility = View.GONE
                        if (warningItems.isEmpty()) {
                            showDialog(getString(R.string.no_weather_warnings), DialogType.INFO)
                        } else {
                            showWarningsPanel(warningItems)
                        }
                    }
                }
            }

            // The forecast is requested for a single sample: the selected location at the current time.
            val samples = arrayListOf(
                TimeDistanceCoordinate().apply {
                    this.coordinates = coordinates
                },
            )

            val errorCode = service.getForecast(samples, onCompleted = listener, geometry = ECoverageGeometry.Polygons)
            if (errorCode != GemError.NoError) {
                runOnAliveUi {
                    binding.progressBar.visibility = View.GONE
                    showDialog(SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: "")
                }
            }
        }
    }

    private fun buildWarningItems(warnings: List<WeatherWarning>, coordinates: Coordinates): List<WarningItem> {
        // Warning timestamps are UTC epochs; shift them by the location's timezone offset
        // and format with a GMT formatter, as done for the forecast timestamps.
        val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val timeOffset = getUTCOffsetInMilliSeconds(coordinates) ?: 0
        return warnings.map { warning ->
            val title = listOf(warning.name, warning.phenomenon, warning.type)
                .firstOrNull { !it.isNullOrEmpty() } ?: getString(R.string.weather_warning)
            val severity = listOf(warning.severity, warning.phenomenon)
                .filter { !it.isNullOrEmpty() }
                .joinToString(" • ")
            val period = listOf(warning.startTimestamp, warning.endTimestamp)
                .filter { it?.isValid() == true }
                .joinToString(" - ") { formatter.format(Date(it!!.asLong() + timeOffset)) }
            WarningItem(
                title = title,
                severity = severity,
                period = period,
                description = warning.description ?: "",
                color = warning.color?.argbValue ?: Rgba(255, 98, 0, 255).argbValue,
                warning = warning,
            )
        }
    }

    private fun getUTCOffsetInMilliSeconds(coordinates: Coordinates): Int? = SdkCall.execute {
        val timezoneResult = TimezoneResult()
        val time = Time()

        time.setUniversalTime()

        TimezoneService.getTimezoneInfoWithCoordinates(
            timezoneResult,
            coordinates,
            time,
            ProgressListener(),
        )

        timezoneResult.offset * 1000
    }

    private fun showWarningsPanel(items: List<WarningItem>) {
        // The warnings panel is stacked on top of the location details panel, which stays
        // visible underneath (declared earlier in the layout) and is revealed again when
        // the warnings panel closes.
        warningsAdapter.clearSelection()
        warningsAdapter.submitList(items)
        binding.warningsList.scrollToPosition(0)
        binding.warningsContainer.visibility = View.VISIBLE
        binding.root.post {
            updateFollowGpsButtonBottomMargin()
            updateFocusViewport()
            // Draw the polygons only after the layout pass so the centering
            // uses the map space left free by the visible panel.
            showWarningPolygons(items)
        }
    }

    private fun closeWarningsPanel() {
        binding.warningsContainer.visibility = View.GONE
        removeWarningPolygons()
        // The location details panel underneath becomes the visible panel again; its pin
        // stays on the map until that panel is closed too.
        updateFollowGpsButtonBottomMargin()
        updateFocusViewport()
    }

    private fun removeWarningPolygons() {
        if (warningCollections.isEmpty()) return
        val collections = warningCollections.map { it.second }
        warningCollections.clear()
        SdkCall.execute {
            val markers = binding.gemSurfaceView.mapView?.preferences?.markers
            collections.forEach { markers?.removeCollection(it) }
        }
    }

    // Draws the coverage polygons of all listed warnings and centers the map
    // on the bounding geographic area of the whole set.
    private fun showWarningPolygons(items: List<WarningItem>) = SdkCall.execute {
        val mapView = binding.gemSurfaceView.mapView ?: return@execute
        removeWarningPolygons()

        val markers = mapView.preferences?.markers ?: return@execute
        var allWarningsArea: RectangleGeographicArea? = null

        for (item in items) {
            val coverage = item.warning.coverage
            if (coverage.isNullOrEmpty()) continue

            val collection = MarkerCollection(EMarkerType.Polygon, item.title)
            coverage.forEach { marker -> collection.add(marker) }

            val warningColor = item.warning.color ?: Rgba(255, 98, 0, 255)
            val settings = MarkerCollectionRenderSettings().apply {
                polygonFillColor = Rgba(warningColor.red, warningColor.green, warningColor.blue, 110)
                polylineInnerColor = Rgba(warningColor.red, warningColor.green, warningColor.blue, 255)
                polylineInnerSize = 1.5
            }

            markers.add(collection, settings)
            warningCollections.add(item to collection)

            collection.area?.takeIf { !it.isEmpty() }?.let { area ->
                val union = allWarningsArea
                if (union == null) allWarningsArea = area else union.setUnion(area)
            }
        }

        allWarningsArea?.let { centerOnArea(it) }
    }

    // Centers the map on the polygons of the tapped warning; they are already drawn.
    private fun highlightWarningOnMap(item: WarningItem) = SdkCall.execute {
        val collection = warningCollections.firstOrNull { it.first === item }?.second ?: return@execute
        collection.area?.takeIf { !it.isEmpty() }?.let { centerOnArea(it) }
    }

    // Must be called from the SDK thread.
    private fun centerOnArea(area: RectangleGeographicArea) {
        binding.gemSurfaceView.mapView?.centerOnRectArea(
            area,
            -1,
            getMapFreeSpace().rect,
            Animation(EAnimation.Linear, 900),
        )
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

    // The location details and the weather warnings panels share the same screen area,
    // so at most one of them is visible at a time.
    private fun visiblePanel(): View? = when {
        binding.warningsContainer.isVisible -> binding.warningsContainer
        binding.locationDetailsContainer.isVisible -> binding.locationDetailsContainer
        else -> null
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

        val panel = visiblePanel()
        if (isLandscape) {
            left = if (panel != null) {
                (panel.right - binding.gemSurfaceView.left).coerceAtLeast(leftInset)
            } else {
                leftInset
            }
            right = (mapWidth - rightInset).coerceAtLeast(left + 1)
            bottom = (mapHeight - bottomInset).coerceAtLeast(topLimit + 1)
        } else {
            left = leftInset.coerceIn(0, (mapWidth - 1).coerceAtLeast(0))
            right = (mapWidth - rightInset).coerceIn(left + 1, mapWidth)
            val bottomLimitRaw = if (panel != null) {
                panel.top - binding.gemSurfaceView.top
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

    private fun updateFollowGpsButtonBottomMargin() {
        val params = binding.followGpsButton.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val bottomInset = insets?.bottom ?: 0
        val rightInset = insets?.right ?: 0
        val panel = visiblePanel()
        // In landscape the FAB sits above the system bar regardless of panel visibility;
        // in portrait it sits just above the visible bottom panel when one is open.
        val targetBottomMargin = if (!isLandscape && panel != null) bigPadding else bottomInset + bigPadding
        val targetEndMargin = rightInset + bigPadding
        var changed = false
        if (!isLandscape && panel != null && params.bottomToTop != panel.id) {
            params.bottomToTop = panel.id
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            changed = true
        }
        if (params.bottomMargin != targetBottomMargin || params.marginEnd != targetEndMargin) {
            params.bottomMargin = targetBottomMargin
            params.marginEnd = targetEndMargin
            changed = true
        }
        if (changed) {
            binding.followGpsButton.layoutParams = params
        }
    }

    private fun enableGPSButton() {
        binding.gemSurfaceView.mapView?.apply {
            val isFollowingPosition = SdkCall.execute { isFollowingPosition() }
            binding.followGpsButton.visibility = if (isFollowingPosition == true) View.GONE else View.VISIBLE
            updateFollowGpsButtonBottomMargin()

            onExitFollowingPosition = {
                binding.followGpsButton.visibility = View.VISIBLE
                updateFollowGpsButtonBottomMargin()
            }

            onEnterFollowingPosition = {
                binding.followGpsButton.visibility = View.GONE
                if (binding.warningsContainer.isVisible) {
                    closeWarningsPanel()
                }
                if (binding.locationDetailsContainer.isVisible) {
                    binding.locationDetailsContainer.visibility = View.GONE
                    updateFollowGpsButtonBottomMargin()
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
        val warningsVis = binding.warningsContainer.visibility
        val fabVis = binding.followGpsButton.visibility

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelId = R.id.location_details_container
                val panelWidth = (resources.displayMetrics.widthPixels * 0.45f).toInt()
                constrainWidth(panelId, panelWidth)
                constrainHeight(panelId, 0)
                connect(panelId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                connect(panelId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
                connect(panelId, ConstraintSet.TOP, R.id.toolbar, ConstraintSet.BOTTOM, 0)
                clear(panelId, ConstraintSet.END)

                clear(R.id.follow_gps_button, ConstraintSet.BOTTOM)
                connect(R.id.follow_gps_button, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            }

            // The warnings panel exactly overlaps the location details panel in both
            // orientations: pin all four edges to it and let the size match.
            val warningsId = R.id.warnings_container
            val detailsId = R.id.location_details_container
            connect(warningsId, ConstraintSet.START, detailsId, ConstraintSet.START, 0)
            connect(warningsId, ConstraintSet.END, detailsId, ConstraintSet.END, 0)
            connect(warningsId, ConstraintSet.TOP, detailsId, ConstraintSet.TOP, 0)
            connect(warningsId, ConstraintSet.BOTTOM, detailsId, ConstraintSet.BOTTOM, 0)
            constrainWidth(warningsId, 0)
            constrainHeight(warningsId, 0)
        }.applyTo(rootLayout)

        val scrollParams = binding.forecastButtonsLayout.layoutParams as ConstraintLayout.LayoutParams
        scrollParams.height = if (isLandscape) 0 else ConstraintLayout.LayoutParams.WRAP_CONTENT
        binding.forecastButtonsLayout.layoutParams = scrollParams

        binding.locationDetailsContainer.visibility = panelVis
        binding.warningsContainer.visibility = warningsVis
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

        val panel = visiblePanel()

        return if (isLandscape) {
            val left = panel?.right ?: (insets?.left ?: 0)
            val top = binding.toolbar.bottom
            val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            Rect(left, top, right, bottom)
        } else {
            val left = insets?.left ?: 0
            val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
            val top = binding.toolbar.bottom
            val bottom = panel?.top?.coerceAtLeast(top) ?: (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
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
