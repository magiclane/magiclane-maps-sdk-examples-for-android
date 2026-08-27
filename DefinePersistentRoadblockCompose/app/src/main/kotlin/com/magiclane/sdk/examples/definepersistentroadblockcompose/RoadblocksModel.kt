/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.definepersistentroadblockcompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.traffic.RoadblockCardData
import com.magiclane.sdk.compose.components.traffic.RoadblockTransportMode
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Parameter
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerRenderSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.CoordinatesList
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Traffic
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.util.GemList
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.text.DateFormat
import java.util.Date

/**
 * One active persistent roadblock: the underlying traffic event plus the snapshot-state
 * card data feeding the library's `RoadblockCard` (in the roadblocks list and in the
 * on-map info panel). The card details (length, from/to, validity) arrive asynchronously
 * via [data] updates.
 */
class RoadblockItem(val event: TrafficEvent) {
    var data by mutableStateOf(RoadblockCardData())

    // Kept as fields so the native objects stay alive while the async preview data request runs.
    val parameters = GemList(Parameter::class)
    var previewDataListener: ProgressListener? = null
}

/**
 * Owns the persistent-roadblock definition flow ported from the View-based
 * DefinePersistentRoadblock example: the road-following preview polyline driven by map
 * panning, snapping taps onto roads, committing segment points and defining/removing
 * roadblocks — feeding the maps-compose library composables (`RoadblockCard`,
 * `RoadblockDefinitionToolbar`) with UI data.
 */
class RoadblocksModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    // ---- UI state --------------------------------------------------------------

    var errorMessage by mutableStateOf("")

    /** Startup explanation dialog; shown once when the map becomes ready. */
    var explanationVisible by mutableStateOf(false)

    /** True while a roadblock is being defined (toolbar, target and cancel button shown). */
    var isDefining by mutableStateOf(false)
        private set

    /** Whether the full-screen roadblocks list is open. */
    var isListVisible by mutableStateOf(false)
        private set

    /** The active persistent roadblocks, newest first, shown by the roadblocks list. */
    val roadblocks = mutableStateListOf<RoadblockItem>()

    /** The roadblock presented by the on-map info panel, or null while none is. */
    var infoItem by mutableStateOf<RoadblockItem?>(null)
        private set

    /** Whether the set-roadblock panel (name + validity interval) is open. */
    var isSetRoadblockPanelVisible by mutableStateOf(false)
        private set

    /** Name prefilled in the set-roadblock panel (the tapped street). */
    var setRoadblockDefaultName = ""
        private set

    /** Index into [TRANSPORT_MODES] of the transport mode the next roadblock applies to. */
    var selectedTransportIndex by mutableIntStateOf(0)
        private set

    // ---- Roadblock definition state (written on the SDK thread, read on both) ----

    @Volatile
    private var isDefinitionActive = false

    // Committed control points; the engine map-matches the final path between them.
    private val controlPoints = CoordinatesList()

    // The last committed point; the live road-following preview starts from it.
    private var previewCoordinate: Coordinates? = null

    // Marker index where the current preview tail starts; -1 while none was drawn yet.
    private var roadblockCoordinateIndex = -1

    private var roadblockName = ""
    private val previewMarker = Marker()
    private val previewRenderSettings = MarkerRenderSettings()
    private var previewMarkerIndex = -1

    // The points collected by the last finished definition, consumed by defineRoadblock.
    private var pendingDefinitionCoords: CoordinatesList? = null

    private var mapState: GemMapState? = null
    private var explanationShown = false

    // The map surface center in pixels, kept up to date by the UI; the definition target
    // icon sits there and the preview polyline follows it.
    @Volatile
    private var mapCenter = Xy(0, 0)

    // Padding kept between centered map content and the free map area edges.
    private val mapPadding = (MAP_PADDING_DP * application.resources.displayMetrics.density).toInt()

    private val centerAnimation
        get() = Animation(EAnimation.Linear, duration = CENTER_ANIMATION_MS)

    private val selectedTransportMode
        get() = TRANSPORT_MODES[selectedTransportIndex]

    // ---- Wiring ----------------------------------------------------------------

    /** Called on the main thread once the hosting GemMap has created its map view. */
    fun initialize(mapState: GemMapState) {
        this.mapState = mapState

        SdkCall.execute {
            previewRenderSettings.apply {
                polylineInnerColor = Rgba(244, 46, 46, 255)
                polylineInnerSize = 1.5
            }
        }

        mapState.mapView?.apply {
            onTouch = { xy ->
                SdkCall.execute { handleMapTouch(xy) }
            }

            // While defining, the preview polyline follows the target in the screen center.
            onMove = { _, _ -> onMapMoved() }
            onDoubleTouch = { _ -> onMapMoved() }
            onTwoTouches = { _ -> onMapMoved() }
            onSwipe = { _, _, _ -> onMapMoved() }
            onPinch = { _, _, _, _, _ -> onMapMoved() }
        }

        if (!explanationShown) {
            explanationShown = true
            explanationVisible = true
        }
    }

    /** Keeps the definition target position in sync with the map surface size. */
    fun updateMapCenter(width: Int, height: Int) {
        mapCenter = Xy(width / 2, height / 2)
    }

    fun selectTransportMode(index: Int) {
        selectedTransportIndex = index
    }

    // ---- Map interaction ---------------------------------------------------------

    /** Runs on the SDK thread. */
    private fun handleMapTouch(xy: Xy) {
        val mapView = mapState?.mapView ?: return

        // Tell the map view where the touch happened so hit-testing is accurate.
        mapView.cursorScreenPosition = xy

        // A tap on an existing roadblock icon opens its info panel.
        val trafficEvents = mapView.cursorSelectionTrafficEvents
        if (!trafficEvents.isNullOrEmpty()) {
            val event = trafficEvents[0]
            if (event.isRoadblock && event.isUserRoadblock && !isDefinitionActive) {
                presentRoadblock(event)
            }
            return
        }

        // While defining, panning drives the process; street taps do not restart it.
        if (isDefinitionActive) {
            return
        }

        val streets = mapView.cursorSelectionStreets
        if (!streets.isNullOrEmpty()) {
            // Deep-copy: transformation results are native-backed views that must not be
            // kept after this call returns.
            val tapped = mapView.transformScreenToWgs(xy)
                ?.let { Coordinates(it.latitude, it.longitude) } ?: return

            // The roadblock start must lie on the road: the engine matches roadblock points
            // within ~10 m only, while the tap hit-test radius is much larger. Snap the
            // tapped position onto the street before starting the definition.
            val start = snapToRoad(tapped) ?: return
            startRoadblockDefinition(start, streets[0].name ?: "")
            return
        }

        // A tap on an empty map area dismisses the info panel, like tapping outside a sheet.
        if (infoItem != null) {
            Util.postOnMain { infoItem = null }
        }
    }

    private fun onMapMoved() {
        if (!isDefinitionActive) return
        val center = mapCenter
        SdkCall.execute { updatePreviewPolyline(center) }
    }

    // ---- Roadblock definition ------------------------------------------------------

    /** Runs on the SDK thread. */
    private fun startRoadblockDefinition(start: Coordinates, streetName: String) {
        val mapView = mapState?.mapView ?: return

        isDefinitionActive = true
        roadblockName = streetName
        controlPoints.clear()
        controlPoints.add(start)
        previewCoordinate = start
        roadblockCoordinateIndex = -1

        previewMarker.setCoordinates(arrayListOf(start))
        previewMarkerIndex = mapView.preferences?.markers
            ?.sketches(EMarkerType.Polyline)
            ?.add(previewMarker, previewRenderSettings) ?: -1

        val zoomLevel = mapView.zoomLevel

        Util.postOnMain {
            isDefining = true

            // Move the tapped position to the screen center, right underneath the target icon,
            // keeping the current zoom level. Posted outside the touch pipeline, otherwise the
            // tail of the tap gesture cancels the centering animation.
            val center = mapCenter
            SdkCall.execute {
                mapState?.mapView?.centerOnCoordinates(
                    coords = start,
                    zoomLevel = zoomLevel,
                    xy = center,
                    animation = centerAnimation,
                )
            }
        }
    }

    /**
     * Runs on the SDK thread. Magic Earth's onMove: drops the previous preview tail from the
     * marker, then asks the engine for the road-following path preview between the last
     * committed point and the position underneath the target, and appends it to the marker.
     */
    private fun updatePreviewPolyline(center: Xy) {
        if (!isDefinitionActive) return
        val mapView = mapState?.mapView ?: return
        val from = previewCoordinate ?: return
        val centerCoordinates = mapView.transformScreenToWgs(center) ?: return

        val coordinatesCount = previewMarker.getCoordinates()?.size ?: 0
        if (roadblockCoordinateIndex > 0) {
            for (i in coordinatesCount - 1 downTo roadblockCoordinateIndex) {
                previewMarker.del(i)
            }
        }

        val path = Traffic().getPersistentRoadblockPathPreview(
            from = from,
            to = centerCoordinates,
            transportMode = selectedTransportMode.value,
        )
        if (path != null) {
            roadblockCoordinateIndex = previewMarker.getCoordinates()?.size ?: 0
            for (coordinate in path) {
                previewMarker.add(coordinate)
            }
        }
    }

    /**
     * Runs on the SDK thread. Snaps a position onto the nearest road usable for roadblocks.
     * A path preview with from == to returns the road-clipped point when the position is
     * within the engine's ~10 m match distance; when the exact position is too far, nearby
     * probe points are tried in two rings so a tap close to a road still snaps onto it.
     */
    private fun snapToRoad(position: Coordinates): Coordinates? {
        val traffic = Traffic()

        val probe = { latitude: Double, longitude: Double ->
            val point = Coordinates(latitude, longitude)
            traffic.getPersistentRoadblockPathPreview(point, point, selectedTransportMode.value)
                ?.lastOrNull()
                ?.let { Coordinates(it.latitude, it.longitude) }
        }

        probe(position.latitude, position.longitude)?.let { return it }

        // ~15 m and ~30 m rings around the position.
        val lonScale = kotlin.math.cos(Math.toRadians(position.latitude))
        for (radiusMeters in intArrayOf(15, 30)) {
            val dLat = radiusMeters / METERS_PER_DEGREE
            val dLon = radiusMeters / (METERS_PER_DEGREE * lonScale)
            for (direction in 0 until 8) {
                val angle = Math.toRadians(direction * 45.0)
                probe(
                    position.latitude + dLat * kotlin.math.sin(angle),
                    position.longitude + dLon * kotlin.math.cos(angle),
                )?.let { return it }
            }
        }

        return null
    }

    /** "+" button: Magic Earth's addPoint — the preview tail becomes a committed segment. */
    fun addSegmentPoint() {
        SdkCall.execute {
            if (!isDefinitionActive) return@execute

            val coordinates = previewMarker.getCoordinates() ?: return@execute
            val count = coordinates.size
            if (count > 1) {
                roadblockCoordinateIndex = count

                // Deep-copy: marker coordinates are native-backed views.
                val last = coordinates[count - 1]
                val point = Coordinates(last.latitude, last.longitude)
                previewCoordinate = point
                controlPoints.add(point)
            }
        }
    }

    /**
     * "✓" button: collects the committed points and opens the set-roadblock panel, where
     * the name and the validity interval are chosen — like Magic Earth's set roadblock view.
     */
    fun finishRoadblockDefinition() {
        SdkCall.execute {
            if (!isDefinitionActive) return@execute

            // Include the current preview end point, like Magic Earth does. Copied into a
            // local list so a failed attempt does not pollute the control points.
            val coords = CoordinatesList(controlPoints)
            previewMarker.getCoordinates()
                ?.takeIf { it.size > 1 }
                ?.last()
                ?.let { Coordinates(it.latitude, it.longitude) }
                ?.takeIf { differs(coords.last(), it) }
                ?.let { coords.add(it) }

            pendingDefinitionCoords = coords

            Util.postOnMain {
                setRoadblockDefaultName = roadblockName.ifEmpty { app.getString(R.string.define_roadblock) }
                isSetRoadblockPanelVisible = true
            }
        }
    }

    /**
     * Closing the set-roadblock panel by any means other than "Done" cancels the
     * definition, like Magic Earth does.
     */
    fun onSetRoadblockPanelClosed() {
        isSetRoadblockPanelVisible = false
        cancelDefinition()
    }

    /** "Done" in the set-roadblock panel: defines the persistent roadblock. */
    fun defineRoadblock(name: String, startMillis: Long, endMillis: Long) {
        SdkCall.execute {
            val coords = pendingDefinitionCoords ?: return@execute
            val mapView = mapState?.mapView ?: return@execute

            val startTime = Time.getUniversalTime()
            val endTime = Time.getUniversalTime()
            if (startTime == null || endTime == null) return@execute
            startTime.longValue = startMillis
            endTime.longValue = endMillis

            // The id doubles as the roadblock name in the list and info panels.
            val roadblock = Traffic().addPersistentRoadblock(
                coords = coords,
                startUTC = startTime,
                expireUTC = endTime,
                transportMode = selectedTransportMode.value,
                id = name,
            )

            if (roadblock != null && roadblock.referencePoint?.valid() == true) {
                pendingDefinitionCoords = null
                stopRoadblockDefinition()
                val viewRc = mapState?.visibleArea(mapPadding)
                if (viewRc != null) {
                    roadblock.boundingBox?.let {
                        mapView.centerOnRectArea(
                            area = it,
                            zoomLevel = -1,
                            viewRc = viewRc,
                            animation = centerAnimation,
                        )
                    }
                }
                Util.postOnMain {
                    isSetRoadblockPanelVisible = false
                    isDefining = false
                }
            } else {
                // The engine returns null both when the id is already in use and when no
                // route exists between the points; the id is the event description, so a
                // name clash can be told apart. The panel stays open for a retry.
                val nameInUse = Traffic().persistentRoadblocks
                    ?.any { it.description == name } == true
                val message = app.getString(
                    if (nameInUse) R.string.roadblock_name_exists else R.string.roadblock_definition_failed,
                )
                Util.postOnMain { errorMessage = message }
            }
        }
    }

    /** Ends the definition (confirmed cancel, back press or set-roadblock panel close). */
    fun cancelDefinition() {
        isDefining = false
        SdkCall.execute { stopRoadblockDefinition() }
    }

    /** Runs on the SDK thread. Clears the preview polyline and the definition state. */
    private fun stopRoadblockDefinition() {
        isDefinitionActive = false
        controlPoints.clear()
        previewCoordinate = null
        roadblockCoordinateIndex = -1
        previewMarker.delPart(0)

        if (previewMarkerIndex >= 0) {
            mapState?.mapView?.preferences?.markers
                ?.sketches(EMarkerType.Polyline)
                ?.del(previewMarkerIndex)
            previewMarkerIndex = -1
        }
    }

    /**
     * True when the points are more than ~10 meters apart. Closer points are treated as the
     * same segment point: a single-point list defines a point located roadblock (both ways),
     * while duplicated path points would force a start -> end path for no reason.
     */
    private fun differs(first: Coordinates, second: Coordinates): Boolean {
        val dLat = (first.latitude - second.latitude) * METERS_PER_DEGREE
        val dLon = (first.longitude - second.longitude) * METERS_PER_DEGREE *
            kotlin.math.cos(Math.toRadians(first.latitude))
        return (dLat * dLat + dLon * dLon) > 10.0 * 10.0
    }

    // ---- Roadblock info panel ------------------------------------------------------

    /**
     * Runs on the SDK thread. Presents a roadblock in the on-map info panel (map icon
     * tap or roadblocks list tap) — a fresh item, so the panel stays valid after the
     * list is closed; the async details grow the card, and the UI re-centers the
     * roadblock in the remaining free map space on each data update.
     */
    private fun presentRoadblock(event: TrafficEvent) {
        val item = createRoadblockItem(event)
        Util.postOnMain { infoItem = item }
        requestRoadblockDetails(item)
    }

    fun dismissInfoPanel() {
        infoItem = null
    }

    /**
     * Centers the roadblock bounding box in the map area left free by the info panel
     * (which registers itself as a map obstruction): above it in portrait, on its right
     * side in landscape.
     */
    fun centerOnRoadblock(item: RoadblockItem) {
        val viewRc = mapState?.visibleArea(mapPadding) ?: return
        SdkCall.execute {
            item.event.boundingBox?.let {
                mapState?.mapView?.centerOnRectArea(
                    area = it,
                    zoomLevel = -1,
                    viewRc = viewRc,
                    animation = centerAnimation,
                )
            }
        }
    }

    // ---- Roadblocks list -------------------------------------------------------

    fun showList() {
        isListVisible = true
        loadRoadblocks()
    }

    fun hideList() {
        isListVisible = false
    }

    /** Opens the on-map info panel for a roadblock tapped in the list. */
    fun showRoadblockOnMap(item: RoadblockItem) {
        hideList()
        SdkCall.execute { presentRoadblock(item.event) }
    }

    fun deleteRoadblock(item: RoadblockItem) {
        SdkCall.execute { Traffic().removeUserRoadblock(item.event) }
        roadblocks.remove(item)
        if (infoItem === item) {
            infoItem = null
        }
    }

    private fun loadRoadblocks() = SdkCall.execute {
        // Newest first, like the Magic Earth roadblocks view.
        val events = Traffic().persistentRoadblocks?.reversed() ?: emptyList()
        val items = events.map(::createRoadblockItem)

        Util.postOnMain {
            roadblocks.clear()
            roadblocks.addAll(items)
        }

        // The item details (name, length, from/to, validity) arrive asynchronously.
        items.forEach(::requestRoadblockDetails)
    }

    /** Runs on the SDK thread. */
    private fun createRoadblockItem(event: TrafficEvent) = RoadblockItem(event).apply {
        data = RoadblockCardData(
            name = event.description ?: "",
            transportMode = when (event.affectedTransportMode) {
                ERouteTransportMode.Lorry.value -> RoadblockTransportMode.Lorry
                ERouteTransportMode.Bicycle.value -> RoadblockTransportMode.Bicycle
                else -> RoadblockTransportMode.Car
            },
        )
    }

    /** Fetches the item details asynchronously; [RoadblockItem.data] updates when done. */
    private fun requestRoadblockDetails(item: RoadblockItem) = SdkCall.execute {
        item.previewDataListener = ProgressListener.create(onCompleted = { errorCode, _ ->
            if (errorCode == GemError.NoError) {
                SdkCall.execute {
                    val data = buildCardData(item)
                    Util.postOnMain { item.data = data }
                }
            }
        })
        item.event.getPreviewData(item.parameters, item.previewDataListener!!)
    }

    /** Runs on the SDK thread. Mirrors the fields used by the Magic Earth RoadblocksController. */
    private fun buildCardData(item: RoadblockItem): RoadblockCardData {
        var data = item.data
        var startStamp = 0L
        var endStamp = 0L
        var validFromLabel = ""
        var validUntilLabel = ""

        for (parameter in item.parameters.asArrayList()) {
            when (parameter.key) {
                "id" -> data = data.copy(name = parameter.valueString)

                "distance" -> {
                    val distText = GemUtil.getDistText(parameter.valueLong.toInt(), EUnitSystem.Metric)
                    data = data.copy(
                        length = "${app.getString(R.string.length)}: ${distText.first} ${distText.second}",
                    )
                }

                "from" -> data = data.copy(
                    fromLabel = label(parameter.name, R.string.from),
                    from = parameter.valueString,
                )

                "to" -> data = data.copy(
                    toLabel = label(parameter.name, R.string.to),
                    to = parameter.valueString,
                )

                "start_stamp" -> {
                    startStamp = parameter.valueLong
                    validFromLabel = label(parameter.name, R.string.valid_from)
                }

                "end_stamp" -> {
                    endStamp = parameter.valueLong
                    validUntilLabel = label(parameter.name, R.string.valid_until)
                }
            }
        }

        if (startStamp == 0L) {
            startStamp = (item.event.startTime?.longValue ?: 0) / 1000
        }
        if (endStamp == 0L) {
            endStamp = (item.event.endTime?.longValue ?: 0) / 1000
        }

        if (startStamp > 0) {
            data = data.copy(
                validFromLabel = validFromLabel.ifEmpty { label(null, R.string.valid_from) },
                validFrom = formatDateTime(startStamp),
            )
        }
        if (endStamp > 0 && endStamp != startStamp) {
            data = data.copy(
                validUntilLabel = validUntilLabel.ifEmpty { label(null, R.string.valid_until) },
                validUntil = formatDateTime(endStamp),
            )
        }

        return data
    }

    private fun label(name: String?, fallback: Int): String {
        val text = name?.takeIf { it.isNotEmpty() } ?: app.getString(fallback)
        return if (text.endsWith(":")) text else "$text:"
    }

    private fun formatDateTime(epochSeconds: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochSeconds * 1000))

    companion object {
        /** The transport modes offered by the settings dialog, in chip order. */
        val TRANSPORT_MODES = listOf(
            ERouteTransportMode.Car,
            ERouteTransportMode.Lorry,
            ERouteTransportMode.Bicycle,
        )

        private const val METERS_PER_DEGREE = 111320.0
        private const val CENTER_ANIMATION_MS = 900
        private const val MAP_PADDING_DP = 40
    }
}
