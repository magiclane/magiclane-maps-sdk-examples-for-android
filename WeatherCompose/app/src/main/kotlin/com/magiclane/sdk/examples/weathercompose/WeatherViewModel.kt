/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weathercompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.ECoverageGeometry
import com.magiclane.sdk.EDaylight
import com.magiclane.sdk.OnWeatherForecastCompleted
import com.magiclane.sdk.WeatherService
import com.magiclane.sdk.WeatherWarning
import com.magiclane.sdk.compose.components.details.LocationDetailsData
import com.magiclane.sdk.compose.components.weather.CurrentWeatherData
import com.magiclane.sdk.compose.components.weather.WeatherForecastData
import com.magiclane.sdk.compose.components.weather.WeatherWarningData
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.RectangleGeographicArea
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.TimeDistanceCoordinate
import com.magiclane.sdk.core.TimezoneResult
import com.magiclane.sdk.core.TimezoneService
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.weathercompose.ui.theme.WarningFallback
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.CoordinatesList
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** A weather warning row plus the SDK object carrying its coverage polygons. */
data class WarningUiItem(
    val data: WeatherWarningData,
    val warning: WeatherWarning,
)

// AndroidViewModel (rather than a plain ViewModel) so that error messages can be resolved with an
// application Context via GemError.getMessage(errorCode, context).
class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val FORECAST_DAYS = 7
        const val FORECAST_HOURS = 48
        const val IMAGE_SIZE = 120
    }

    // Details of the tapped place, shown by the bottom info panel; null hides the panel.
    var locationDetailsInfo: LocationDetailsData? by mutableStateOf(null)

    // The tapped place the forecast buttons act on. Deep copy of the cursor-selection result:
    // that one is backed by native memory the SDK may release once the selection changes.
    private var selectedCoordinates: Coordinates? = null

    // Name of the city the selected place belongs to (falls back to the place name), displayed
    // as the location of the current forecast header.
    private var selectedLocationName = ""

    // Which forecast screen is open (full screen, on top of the map); null means none.
    var forecastType: EForecastType? by mutableStateOf(null)
        private set

    // Rows of the open forecast screen; conditions for CURRENT, one row per day/hour otherwise.
    var forecastItems: List<WeatherForecastData> by mutableStateOf(emptyList())
        private set

    // Header of the CURRENT forecast screen; null until the forecast response arrives.
    var currentForecast: CurrentWeatherData? by mutableStateOf(null)
        private set

    // Warnings issued for the selected place, listed by the warnings panel; null hides the panel.
    var warningItems: List<WarningUiItem>? by mutableStateOf(null)
        private set

    // Coverage polygons currently drawn on the map, one collection per displayed warning.
    private val warningCollections = mutableListOf<Pair<WarningUiItem, MarkerCollection>>()

    // loading state
    var progressBarIsVisible by mutableStateOf(false)

    // messages
    var errorMessage by mutableStateOf("")
    var infoMessage by mutableStateOf("")

    // utils
    var followGpsButtonIsVisible by mutableStateOf(false)
    var invokeHighlight by mutableStateOf(false)
    var highlightEffect: () -> Unit = {}
    var padding = 0
    var detailsPanelImageSize = 0

    // State holder of the hosting GemMap composable; grants safe access to the map view without
    // the UI passing it around, and exposes the free map area kept clear of the panels.
    private var mapState: GemMapState? = null

    // Free map area (clear of the system bars / cutout and the visible panel), deflated by
    // [padding]. Used as the viewport when centering on warning areas / landmarks; null before
    // the first layout.
    private val visibleArea: Rect?
        get() = mapState?.visibleArea(padding)

    private var weatherService: WeatherService? = null
    private var animation = Animation(EAnimation.Linear, 900)

    init {
        val resources = application.resources
        detailsPanelImageSize = resources.getDimension(R.dimen.image_size).toInt()
        padding = resources.getDimension(R.dimen.big_padding).toInt()
    }

    // Resolves an SDK error code to a localized message on the SDK thread.
    private fun errorText(errorCode: Int): String =
        SdkCall.runSynced { GemError.getMessage(errorCode, getApplication()) } ?: ""

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    fun initialize(mapState: GemMapState) {
        this.mapState = mapState
        infoMessage = getString(R.string.tap_on_map_to_see_weather_info)

        // The follow-position enter/exit callbacks stay owned by GemMapState (they feed its
        // isFollowingPosition state); the UI reacts to that state instead of hooking them here.
        mapState.mapView?.apply {
            // Single tap selects, in priority order, the current position arrow, a point of
            // interest, a map overlay, a traffic event, a street or the raw tapped coordinates.
            // Whatever is selected, the info panel offers the weather forecast at its place.
            onTouch = { xy ->
                SdkCall.execute {
                    cursorScreenPosition = xy
                    deactivateAllHighlights()

                    if (selectMyPosition(this)) return@execute
                    if (selectLandmark(this)) return@execute
                    if (selectOverlayItem(this)) return@execute
                    if (selectTrafficEvent(this)) return@execute
                    if (selectStreet(this)) return@execute
                    selectCoordinates(this, xy)
                }
            }
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

    // region MAP TAP SELECTION

    // Must be called on the SDK thread.
    private fun selectMyPosition(mapView: MapView): Boolean {
        val myPosition = mapView.cursorSelectionSceneObject ?: return false
        val positionTracker = MapSceneObject.getDefPositionTracker().first ?: return false
        if (!isSameMapScene(myPosition, positionTracker)) return false

        val coordinates = myPosition.coordinates ?: return false
        showLocationDetails(
            image = null,
            title = getString(R.string.my_position),
            description = getLandmarkDescription(mapView, coordinates),
            coordinates = coordinates,
            mapView = mapView,
        )
        return true
    }

    // Must be called on the SDK thread.
    private fun selectLandmark(mapView: MapView): Boolean {
        val landmark = mapView.cursorSelectionLandmarks?.firstOrNull() ?: return false
        val details = GemUtil.pairFormatLandmarkDetails(landmark, true)
        showLocationDetails(
            image = landmark.image?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)?.asImageBitmap(),
            title = details.first,
            description = details.second,
            coordinates = landmark.coordinates,
            mapView = mapView,
        )
        return true
    }

    // Must be called on the SDK thread.
    private fun selectOverlayItem(mapView: MapView): Boolean {
        val overlay = mapView.cursorSelectionOverlayItems?.firstOrNull() ?: return false
        val coordinates = overlay.coordinates ?: return false

        val name = when {
            !overlay.name.isNullOrEmpty() -> overlay.name!!
            !overlay.overlayInfo?.name.isNullOrEmpty() -> overlay.overlayInfo?.name!!
            else -> "Unknown"
        }
        showLocationDetails(
            image = overlay.image?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)?.asImageBitmap(),
            title = name,
            description = getLandmarkDescription(mapView, coordinates),
            coordinates = coordinates,
            mapView = mapView,
        )
        return true
    }

    // Must be called on the SDK thread.
    private fun selectTrafficEvent(mapView: MapView): Boolean {
        val event = mapView.cursorSelectionTrafficEvents?.firstOrNull() ?: return false
        val coordinates = event.referencePoint ?: return false

        // Keep only the first line of multi-line event descriptions.
        var name = event.description ?: "Traffic event"
        val pos = name.indexOf("\r\n")
        if (pos > 0) name = name.substring(0, pos)

        showLocationDetails(
            image = event.image?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)?.asImageBitmap(),
            title = name,
            description = getLandmarkDescription(mapView, coordinates),
            coordinates = coordinates,
            mapView = mapView,
        )
        return true
    }

    // Must be called on the SDK thread.
    private fun selectStreet(mapView: MapView): Boolean {
        val street = mapView.cursorSelectionStreets?.firstOrNull() ?: return false
        val details = GemUtil.pairFormatLandmarkDetails(street, true)
        showLocationDetails(
            image = street.image?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)?.asImageBitmap(),
            title = details.first,
            description = details.second,
            coordinates = street.coordinates,
            mapView = mapView,
        )
        return true
    }

    // Fallback selection: the raw tapped coordinates. Must be called on the SDK thread.
    private fun selectCoordinates(mapView: MapView, xy: com.magiclane.sdk.core.Xy) {
        val coordinates = mapView.transformScreenToWgs(xy) ?: return
        showLocationDetails(
            image = ImageDatabase().getImageById(SdkImages.Core.Waypoint_Intermediary.value)
                ?.asBitmap(detailsPanelImageSize, detailsPanelImageSize)?.asImageBitmap(),
            title = String.format(Locale.getDefault(), "%.6f, %.6f", coordinates.latitude, coordinates.longitude),
            description = getLandmarkDescription(mapView, coordinates),
            coordinates = coordinates,
            mapView = mapView,
        )
    }

    // Fills the info panel state for the selected place and highlights it on the map (after the
    // panel is laid out, so the pin is centered in the map space the panel leaves free).
    // Must be called on the SDK thread.
    private fun showLocationDetails(
        image: ImageBitmap?,
        title: String,
        description: String,
        coordinates: Coordinates?,
        mapView: MapView,
    ) {
        // The received Coordinates is backed by native memory owned by the transient cursor
        // selection result, which may be released before the forecast buttons are tapped.
        // Copy it into a standalone, Kotlin-owned object for any deferred use.
        val ownedCoordinates = coordinates?.let { Coordinates(it.latitude, it.longitude) }

        closeWarnings()
        selectedCoordinates = ownedCoordinates
        selectedLocationName = ownedCoordinates?.let { getCityName(mapView, it).ifEmpty { title } } ?: title
        locationDetailsInfo = LocationDetailsData(image, title, description)

        ownedCoordinates ?: return
        val landmark = Landmark(title, ownedCoordinates.latitude, ownedCoordinates.longitude)
        invokeHighlight = true
        highlightEffect = {
            // Replace whatever image the landmark has with the standard search-pin so all
            // highlighted places look consistent on the map.
            landmark.image = ImageDatabase.searchResultsPin
            val area = visibleArea
            if (area != null) {
                landmark.coordinates?.let {
                    mapView.centerOnCoordinates(it, -1, area.center, animation, Double.MAX_VALUE, 0.0)
                }
            }
            mapView.activateHighlightLandmarks(
                landmark,
                HighlightRenderSettings(
                    EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                ),
            )
        }
    }

    // Closes the info panel (with the warnings panel possibly stacked on it) and removes
    // everything drawn on the map for it. The panel leaving the composition releases its map
    // obstruction, so GemMap gives the freed space back to the map (and the Magic Lane logo).
    fun hideLocationDetails() {
        closeWarnings()
        locationDetailsInfo = null
        selectedCoordinates = null
        selectedLocationName = ""

        SdkCall.execute { mapState?.mapView?.deactivateAllHighlights() }
    }

    fun isLocationDetailsVisible() = locationDetailsInfo != null

    // endregion

    // region FORECAST SCREENS

    // Opens the given forecast screen for the selected place and requests its data; the screen
    // shows the sky background immediately and the rows appear when the response arrives.
    fun openForecast(type: EForecastType) = SdkCall.execute {
        val coordinates = selectedCoordinates ?: return@execute

        forecastType = type
        forecastItems = emptyList()
        currentForecast = null

        val service = weatherService ?: WeatherService().also { weatherService = it }

        val listener: OnWeatherForecastCompleted = { results, errorCode, _ ->
            if (errorCode != GemError.NoError) {
                errorMessage = errorText(errorCode)
            } else {
                SdkCall.execute {
                    results.firstOrNull()?.let { result ->
                        // Keep the response only if the screen it was requested for is still open.
                        if (forecastType == type) buildForecastItems(type, result, coordinates)
                    }
                }
            }
        }

        val coords = CoordinatesList().apply { add(coordinates) }
        val errorCode = when (type) {
            // Retrieves a single item with in-depth details about the current weather.
            EForecastType.CURRENT -> service.getCurrent(coords, onCompleted = listener)
            // Retrieve one item per day/hour with the main details about the weather then.
            EForecastType.DAILY -> service.getDailyForecast(FORECAST_DAYS, coords, onCompleted = listener)
            EForecastType.HOURLY -> service.getHourlyForecast(FORECAST_HOURS, coords, onCompleted = listener)
        }
        // A non-zero code means the request was rejected upfront and the listener will never fire.
        if (errorCode != GemError.NoError) {
            errorMessage = errorText(errorCode)
        }
    }

    fun closeForecast() {
        forecastType = null
        forecastItems = emptyList()
        currentForecast = null
    }

    // Maps the SDK forecast response to the rows (and, for CURRENT, the header) of the open
    // screen. Forecast timestamps are UTC epochs of the location's wall-clock time: shift them
    // by the location's timezone offset and format with a GMT formatter.
    // Must be called on the SDK thread.
    private fun buildForecastItems(
        type: EForecastType,
        result: com.magiclane.sdk.LocationForecast,
        coordinates: Coordinates,
    ) {
        val conditions = result.forecast ?: return
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val timeOffset = getUTCOffsetInMilliSeconds(coordinates) ?: 0

        val items = mutableListOf<WeatherForecastData>()

        when (type) {
            EForecastType.CURRENT -> {
                val condition = conditions.firstOrNull() ?: return
                var temperature = ""
                var feelsLike = ""

                condition.parameters?.forEach { param ->
                    when (param.type) {
                        "Temperature" -> temperature = String.format(
                            Locale.getDefault(),
                            "%d%s",
                            param.value.roundToInt(),
                            param.unit,
                        )

                        "FeelsLike" -> feelsLike = String.format(
                            Locale.getDefault(),
                            "%s %d%s",
                            param.name,
                            param.value.roundToInt(),
                            param.unit,
                        )

                        // Sunrise/sunset values are epoch seconds of the location's wall clock.
                        "Sunrise", "Sunset" -> items.add(
                            WeatherForecastData(
                                title = param.type ?: "",
                                mainDetail = timeFormatter.format(Date((param.value * 1000).toLong() + timeOffset)),
                            ),
                        )

                        else -> items.add(
                            WeatherForecastData(
                                title = param.type ?: "",
                                mainDetail = if (param.value.rem(1) == 0.0) {
                                    String.format(Locale.getDefault(), "%d %s", param.value.roundToInt(), param.unit)
                                } else {
                                    String.format(Locale.getDefault(), "%.2f %s", param.value, param.unit)
                                },
                            ),
                        )
                    }
                }

                val conditionTimestamp = condition.timestamp?.asLong() ?: 0L
                currentForecast = CurrentWeatherData(
                    locationName = selectedLocationName,
                    temperature = temperature,
                    description = condition.description ?: "",
                    feelsLike = feelsLike,
                    updatedAt = result.updated?.run {
                        "Updated at: " + timeFormatter.format(Date(asLong() + timeOffset))
                    } ?: "",
                    localTime = "Current time: " + timeFormatter.format(Date(conditionTimestamp + timeOffset)),
                    image = condition.image?.asBitmap(IMAGE_SIZE, IMAGE_SIZE)?.asImageBitmap(),
                    isDay = condition.daylight == EDaylight.Day || condition.daylight == EDaylight.NotAvailable,
                )
            }

            EForecastType.DAILY -> conditions.mapTo(items) { condition ->
                val dayOfWeek = condition.timestamp?.run {
                    // SDK: 1 = Sunday … 7 = Saturday; DayOfWeek: MONDAY..SUNDAY.
                    val dayOfWeekIndex = if (dayOfWeek == 1) 6 else dayOfWeek - 2
                    DayOfWeek.entries[dayOfWeekIndex].name
                } ?: ""
                val high = condition.parameters?.find { it.type == "TemperatureHigh" }?.run {
                    String.format(Locale.getDefault(), "%d%s", value.roundToInt(), unit)
                } ?: ""
                val low = condition.parameters?.find { it.type == "TemperatureLow" }?.run {
                    String.format(Locale.getDefault(), "%d%s", value.roundToInt(), unit)
                } ?: ""

                WeatherForecastData(
                    icon = condition.image?.asBitmap(IMAGE_SIZE, IMAGE_SIZE)?.asImageBitmap(),
                    title = dayOfWeek,
                    subtitle = condition.timestamp?.run { dateFormatter.format(Date(asLong() + timeOffset)) } ?: "",
                    mainDetail = high,
                    subDetail = low,
                )
            }

            EForecastType.HOURLY -> conditions.mapTo(items) { condition ->
                WeatherForecastData(
                    icon = condition.image?.asBitmap(IMAGE_SIZE, IMAGE_SIZE)?.asImageBitmap(),
                    title = condition.timestamp?.run { timeFormatter.format(Date(asLong() + timeOffset)) } ?: "",
                    subtitle = condition.timestamp?.run { dateFormatter.format(Date(asLong() + timeOffset)) } ?: "",
                    mainDetail = condition.parameters?.find { it.type == "Temperature" }?.run {
                        String.format(Locale.getDefault(), "%d%s", value.roundToInt(), unit)
                    } ?: "",
                )
            }
        }

        forecastItems = items
    }

    // endregion

    // region WEATHER WARNINGS

    // Requests the weather warnings (with their coverage polygons) issued for the selected place.
    // When there are any, the warnings panel opens stacked over the info panel and all coverage
    // areas are drawn on the map.
    fun requestWarnings() = SdkCall.execute {
        val coordinates = selectedCoordinates ?: return@execute

        progressBarIsVisible = true
        val service = weatherService ?: WeatherService().also { weatherService = it }

        val listener: OnWeatherForecastCompleted = { results, errorCode, _ ->
            progressBarIsVisible = false
            if (errorCode != GemError.NoError) {
                errorMessage = errorText(errorCode)
            } else {
                val items = SdkCall.execute {
                    results.firstOrNull()?.warnings?.let { buildWarningItems(it, coordinates) }
                } ?: emptyList()

                if (items.isEmpty()) {
                    infoMessage = getString(R.string.no_weather_warnings)
                } else {
                    warningItems = items
                    // Draw the polygons only after the panel's layout pass so the centering
                    // uses the map space left free by the visible panel.
                    invokeHighlight = true
                    highlightEffect = { showWarningPolygons(items) }
                }
            }
        }

        // The forecast is requested for a single sample: the selected place at the current time.
        val samples = arrayListOf(TimeDistanceCoordinate().apply { this.coordinates = coordinates })

        val errorCode = service.getForecast(samples, onCompleted = listener, geometry = ECoverageGeometry.Polygons)
        if (errorCode != GemError.NoError) {
            progressBarIsVisible = false
            errorMessage = errorText(errorCode)
        }
    }

    // Closes the warnings panel, revealing the info panel underneath again; the place's pin
    // stays on the map until that panel is closed too.
    fun closeWarnings() {
        warningItems = null
        removeWarningPolygons()
    }

    fun isWarningsPanelVisible() = warningItems != null

    private fun buildWarningItems(warnings: List<WeatherWarning>, coordinates: Coordinates): List<WarningUiItem> {
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
            WarningUiItem(
                data = WeatherWarningData(
                    severityColor = warning.color?.let { Color(it.argbValue) } ?: WarningFallback,
                    name = title,
                    severity = severity,
                    period = period,
                    description = warning.description ?: "",
                ),
                warning = warning,
            )
        }
    }

    // Draws the coverage polygons of all listed warnings and centers the map on the bounding
    // geographic area of the whole set. Must be called on the SDK thread.
    private fun showWarningPolygons(items: List<WarningUiItem>) {
        val mapView = mapState?.mapView ?: return
        removeWarningPolygons()

        val markers = mapView.preferences?.markers ?: return
        var allWarningsArea: RectangleGeographicArea? = null

        for (item in items) {
            val coverage = item.warning.coverage
            if (coverage.isNullOrEmpty()) continue

            val collection = MarkerCollection(EMarkerType.Polygon, item.data.name)
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

        val viewRc = visibleArea ?: return
        allWarningsArea?.let { mapView.centerOnRectArea(it, -1, viewRc, animation) }
    }

    // Centers the map on the polygons of the tapped warning; they are already drawn.
    fun selectWarning(item: WarningUiItem) = SdkCall.execute {
        val collection = warningCollections.firstOrNull { it.first === item }?.second ?: return@execute
        val viewRc = visibleArea ?: return@execute
        collection.area?.takeIf { !it.isEmpty() }?.let { area ->
            mapState?.mapView?.centerOnRectArea(area, -1, viewRc, animation)
        }
    }

    private fun removeWarningPolygons() {
        if (warningCollections.isEmpty()) return
        val collections = warningCollections.map { it.second }
        warningCollections.clear()
        SdkCall.execute {
            val markers = mapState?.mapView?.preferences?.markers
            collections.forEach { markers?.removeCollection(it) }
        }
    }

    // endregion

    // region MAP AREAS / UTILS

    fun invokeHighlightEffect() = SdkCall.execute {
        if (visibleArea != null && invokeHighlight) {
            invokeHighlight = false
            highlightEffect.invoke()
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

    // Tries progressively larger search radii; the last query enables road interpolation
    // to find an address in areas where only road data is available.
    private fun getCityName(mapView: MapView, coordinates: Coordinates): String {
        for (radius in listOf(50, 300, 2500)) {
            val city = mapView.getClosestAddress(coordinates, radius, radius == 2500)
                ?.addressInfo?.getField(EAddressField.City) ?: ""
            if (city.isNotEmpty()) return city
        }
        return ""
    }

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

    // endregion
}
