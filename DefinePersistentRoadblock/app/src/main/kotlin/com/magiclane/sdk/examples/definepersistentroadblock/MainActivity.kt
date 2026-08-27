/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.definepersistentroadblock

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerRenderSettings
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.RoadblockListItemBinding
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.SetRoadblockDialogLayoutBinding
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.TransportModeDialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.CoordinatesList
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Traffic
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    companion object {
        // Default roadblock validity, like Magic Earth: from now until one hour later.
        private const val DEFAULT_ROADBLOCK_DURATION_MS = 60L * 60 * 1000
    }

    private lateinit var binding: ActivityMainBinding

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView

    // The transport mode the next roadblock applies to; selectable via the settings button.
    private var selectedTransportMode = ERouteTransportMode.Car

    // ---- Roadblock definition state (written on the SDK thread, read on both) ----
    @Volatile
    private var isDefiningRoadblock = false

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

    private lateinit var roadblocksAdapter: RoadblocksAdapter

    // Height-capped sheets currently open; the cap follows orientation changes because
    // the activity handles them itself instead of being recreated.
    private val heightCappedSheets = mutableListOf<BottomSheetDialog>()

    // The open on-map roadblock info panel, re-laid out on orientation changes.
    private var roadblockInfoPanel: BottomSheetDialog? = null
    private var roadblockInfoItem: RoadblockItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // There is no opaque toolbar anymore: pick the status-bar icon color against the map.
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        // With 3-button navigation the system would otherwise paint its own scrim behind
        // the navigation bar, breaking the panel and toolbar colors at the screen edge.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        gemSurfaceView = binding.gemSurfaceView

        setupButtons()
        setupRoadblocksList()
        setupBackHandling()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.no_internet_message))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        super.onDestroy()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        GemSdk.release()
        exitProcess(0)
    }

    // ---- UI wiring -------------------------------------------------------------

    private fun setupButtons() {
        binding.listButton.setOnClickListener { showRoadblocksPanel() }
        binding.settingsButton.setOnClickListener { showTransportModeDialog() }
        binding.cancelButton.setOnClickListener { confirmCancelDefinition() }

        // Roadblock definition toolbar: "✓" commits the roadblock, "+" adds a new segment.
        binding.leftButton.setOnClickListener { finishRoadblockDefinition() }
        binding.rightButton.setOnClickListener { addSegmentPoint() }
    }

    private fun setupRoadblocksList() {
        roadblocksAdapter = RoadblocksAdapter(
            onDeleteTapped = ::confirmDeleteListItem,
            onItemTapped = ::showRoadblockOnMap,
        )
        binding.roadblocksList.layoutManager = LinearLayoutManager(this)
        binding.roadblocksList.adapter = roadblocksAdapter
        binding.roadblocksToolbar.setNavigationOnClickListener { hideRoadblocksPanel() }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.roadblocksPanel.isVisible -> hideRoadblocksPanel()
                isDefiningRoadblock -> confirmCancelDefinition()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
    }

    // ---- SDK listener registration -------------------------------------------

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            updateFocusViewport()

            // runSynced (not execute): this callback already runs in an SDK-locked context,
            // and execute would deadlock waiting on the SDK thread.
            SdkCall.runSynced {
                previewRenderSettings.apply {
                    polylineInnerColor = Rgba(244, 46, 46, 255)
                    polylineInnerSize = 1.5
                }
            }

            mapView.onTouch = { xy ->
                SdkCall.execute { handleMapTouch(xy) }
            }

            // While defining, the preview polyline follows the target in the screen center.
            mapView.onMove = { _, _ -> onMapMoved() }
            mapView.onDoubleTouch = { _ -> onMapMoved() }
            mapView.onTwoTouches = { _ -> onMapMoved() }
            mapView.onSwipe = { _, _, _ -> onMapMoved() }
            mapView.onPinch = { _, _, _, _, _ -> onMapMoved() }

            runOnAliveUi { showExplanationDialog() }
        }

        // Keep the Magic Lane logo viewport in sync whenever the map surface is resized.
        binding.gemSurfaceView.onSurfaceChanged = { _, _ -> updateFocusViewport() }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // ---- Map interaction -------------------------------------------------------

    /** Runs on the SDK thread. */
    private fun handleMapTouch(xy: Xy) {
        val mapView = gemSurfaceView.mapView ?: return

        // Tell the map view where the touch happened so hit-testing is accurate.
        mapView.cursorScreenPosition = xy

        // A tap on an existing roadblock icon opens its info panel.
        val trafficEvents = mapView.cursorSelectionTrafficEvents
        if (!trafficEvents.isNullOrEmpty()) {
            val event = trafficEvents[0]
            if (event.isRoadblock && event.isUserRoadblock && !isDefiningRoadblock) {
                val item = createRoadblockItem(event)
                runOnAliveUi { showRoadblockInfoPanel(item) }
            }
            return
        }

        // While defining, panning drives the process; street taps do not restart it.
        if (isDefiningRoadblock) {
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
        }
    }

    private fun onMapMoved() {
        if (!isDefiningRoadblock) return
        val center = screenCenter()
        SdkCall.execute { updatePreviewPolyline(center) }
    }

    private fun screenCenter() = Xy(gemSurfaceView.width / 2, gemSurfaceView.height / 2)

    // ---- Roadblock definition --------------------------------------------------

    /** Runs on the SDK thread. */
    private fun startRoadblockDefinition(start: Coordinates, streetName: String) {
        val mapView = gemSurfaceView.mapView ?: return

        isDefiningRoadblock = true
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

        runOnAliveUi {
            setDefinitionUiVisible(true)

            // Move the tapped position to the screen center, right underneath the target icon,
            // keeping the current zoom level. Posted outside the touch pipeline, otherwise the
            // tail of the tap gesture cancels the centering animation.
            val center = screenCenter()
            SdkCall.execute {
                gemSurfaceView.mapView?.centerOnCoordinates(
                    coords = start,
                    zoomLevel = zoomLevel,
                    xy = center,
                    animation = Animation(EAnimation.Linear, duration = 900),
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
        if (!isDefiningRoadblock) return
        val mapView = gemSurfaceView.mapView ?: return
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
        val metersPerDegree = 111320.0
        val lonScale = kotlin.math.cos(Math.toRadians(position.latitude))
        for (radiusMeters in intArrayOf(15, 30)) {
            val dLat = radiusMeters / metersPerDegree
            val dLon = radiusMeters / (metersPerDegree * lonScale)
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
    private fun addSegmentPoint() {
        SdkCall.execute {
            if (!isDefiningRoadblock) return@execute

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
    private fun finishRoadblockDefinition() {
        SdkCall.execute {
            if (!isDefiningRoadblock) return@execute

            // Include the current preview end point, like Magic Earth does. Copied into a
            // local list so a failed attempt does not pollute the control points.
            val coords = CoordinatesList(controlPoints)
            previewMarker.getCoordinates()
                ?.takeIf { it.size > 1 }
                ?.last()
                ?.let { Coordinates(it.latitude, it.longitude) }
                ?.takeIf { differs(coords.last(), it) }
                ?.let { coords.add(it) }

            runOnAliveUi { showSetRoadblockDialog(coords) }
        }
    }

    /**
     * The set-roadblock panel, copied from the Magic Earth set roadblock view: roadblock
     * name, From/To toggle selecting which end of the validity interval the date and time
     * buttons edit, and "Done" defining the roadblock. Closing the panel by any other
     * means cancels the definition, like Magic Earth does.
     */
    private fun showSetRoadblockDialog(coords: CoordinatesList) {
        val fromDateTime = Calendar.getInstance()
        val toDateTime = Calendar.getInstance().apply {
            timeInMillis = fromDateTime.timeInMillis + DEFAULT_ROADBLOCK_DURATION_MS
        }
        // The interval end the date and time buttons currently edit.
        var editedDateTime = fromDateTime

        val dialog = BottomSheetDialog(this)
        val dialogBinding = SetRoadblockDialogLayoutBinding.inflate(layoutInflater)

        fun refreshDateTimeButtons() {
            dialogBinding.dateButton.text =
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(editedDateTime.time)
            dialogBinding.timeButton.text =
                DateFormat.getTimeInstance(DateFormat.SHORT).format(editedDateTime.time)
        }

        dialogBinding.apply {
            roadblockNameInput.setText(roadblockName.ifEmpty { getString(R.string.define_roadblock) })
            roadblockNameInput.doAfterTextChanged { doneButton.isEnabled = !it.isNullOrBlank() }
            roadblockNameInput.setOnEditorActionListener { _, _, _ ->
                doneButton.performClick()
                false
            }

            intervalToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                editedDateTime = if (checkedId == R.id.from_button) fromDateTime else toDateTime
                refreshDateTimeButtons()
            }
            refreshDateTimeButtons()

            dateButton.setOnClickListener { showDatePicker(editedDateTime, ::refreshDateTimeButtons) }
            timeButton.setOnClickListener { showTimePicker(editedDateTime, ::refreshDateTimeButtons) }

            closeButton.setOnClickListener { dialog.dismiss() }
            doneButton.setOnClickListener {
                val name = roadblockNameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    defineRoadblock(coords, name, fromDateTime.timeInMillis, toDateTime.timeInMillis, dialog)
                }
            }
        }

        dialog.apply {
            // Like Magic Earth, closing the panel ends the definition without confirmation;
            // this also runs after a successful "Done", where it is a no-op cleanup.
            setOnDismissListener {
                SdkCall.execute { stopRoadblockDefinition() }
                setDefinitionUiVisible(false)
            }
            setCanceledOnTouchOutside(false)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setContentView(dialogBinding.root)
            showEdgeToEdge()
        }
    }

    private fun showDatePicker(target: Calendar, onPicked: () -> Unit) {
        // MaterialDatePicker works in UTC day timestamps: convert back and forth.
        val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(target[Calendar.YEAR], target[Calendar.MONTH], target[Calendar.DAY_OF_MONTH])
        }
        MaterialDatePicker.Builder.datePicker()
            .setSelection(utcDay.timeInMillis)
            .build()
            .apply {
                addOnPositiveButtonClickListener { selection ->
                    val selected = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        .apply { timeInMillis = selection }
                    target.set(selected[Calendar.YEAR], selected[Calendar.MONTH], selected[Calendar.DAY_OF_MONTH])
                    onPicked()
                }
            }
            .show(supportFragmentManager, "DatePicker")
    }

    private fun showTimePicker(target: Calendar, onPicked: () -> Unit) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(target[Calendar.HOUR_OF_DAY])
            .setMinute(target[Calendar.MINUTE])
            .build()
        picker.addOnPositiveButtonClickListener {
            target.set(Calendar.HOUR_OF_DAY, picker.hour)
            target.set(Calendar.MINUTE, picker.minute)
            onPicked()
        }
        picker.show(supportFragmentManager, "TimePicker")
    }

    /** "Done" in the set-roadblock panel: defines the persistent roadblock. */
    private fun defineRoadblock(
        coords: CoordinatesList,
        name: String,
        startMillis: Long,
        endMillis: Long,
        dialog: BottomSheetDialog,
    ) {
        SdkCall.execute {
            val mapView = gemSurfaceView.mapView ?: return@execute

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
                stopRoadblockDefinition()
                roadblock.boundingBox?.let {
                    mapView.centerOnRectArea(
                        area = it,
                        zoomLevel = -1,
                        viewRc = getFreeSpaceRectangle(),
                        animation = Animation(EAnimation.Linear, duration = 900),
                    )
                }
                runOnAliveUi { dialog.dismiss() }
            } else {
                // The engine returns null both when the id is already in use and when no
                // route exists between the points; the id is the event description, so a
                // name clash can be told apart. The panel stays open for a retry.
                val nameInUse = Traffic().persistentRoadblocks
                    ?.any { it.description == name } == true
                val message = getString(
                    if (nameInUse) R.string.roadblock_name_exists else R.string.roadblock_definition_failed,
                )
                runOnAliveUi { showDialog(message) }
            }
        }
    }

    private fun confirmCancelDefinition() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.cancel_roadblock_definition)
            .setPositiveButton(R.string.yes) { _, _ ->
                SdkCall.execute { stopRoadblockDefinition() }
                setDefinitionUiVisible(false)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /** Runs on the SDK thread. Clears the preview polyline and the definition state. */
    private fun stopRoadblockDefinition() {
        isDefiningRoadblock = false
        controlPoints.clear()
        previewCoordinate = null
        roadblockCoordinateIndex = -1
        previewMarker.delPart(0)

        if (previewMarkerIndex >= 0) {
            gemSurfaceView.mapView?.preferences?.markers
                ?.sketches(EMarkerType.Polyline)
                ?.del(previewMarkerIndex)
            previewMarkerIndex = -1
        }
    }

    private fun setDefinitionUiVisible(visible: Boolean) {
        binding.defineRoadblockTopToolbar.isVisible = visible
        binding.target.isVisible = visible
        binding.cancelButton.isVisible = visible
        binding.listButton.isVisible = !visible
        binding.settingsButton.isVisible = !visible
        updateFocusViewport()
    }

    /**
     * True when the points are more than ~10 meters apart. Closer points are treated as the
     * same segment point: a single-point list defines a point located roadblock (both ways),
     * while duplicated path points would force a start -> end path for no reason.
     */
    private fun differs(first: Coordinates, second: Coordinates): Boolean {
        val metersPerDegree = 111320.0
        val dLat = (first.latitude - second.latitude) * metersPerDegree
        val dLon = (first.longitude - second.longitude) * metersPerDegree *
            kotlin.math.cos(Math.toRadians(first.latitude))
        return (dLat * dLat + dLon * dLon) > 10.0 * 10.0
    }

    // ---- Roadblock info panel ----------------------------------------------------

    /**
     * Panel shown when a user defined roadblock is presented (map icon tap or roadblocks
     * list tap): the same card as in the roadblocks list, including the delete button.
     * In portrait it is a full-width bottom sheet and the roadblock is centered in the
     * free map space above it; in landscape it is docked to the start side at half the
     * screen width and the roadblock is centered in the space on its right.
     */
    private fun showRoadblockInfoPanel(item: RoadblockItem) {
        val dialog = BottomSheetDialog(this)
        val itemBinding = RoadblockListItemBinding.inflate(layoutInflater)

        fun bind() = itemBinding.bindRoadblock(item) { confirmDeleteFromMap(it) { dialog.dismiss() } }
        bind()

        // The card info must clear the cutout / side navigation bar at the panel edges.
        ViewCompat.setOnApplyWindowInsetsListener(itemBinding.root) { view, insets ->
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            view.updatePadding(left = sys.left, right = if (isLandscape) 0 else sys.right)
            insets
        }

        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            setContentView(itemBinding.root)
            applyRoadblockPanelLayout(this)
            setOnShowListener { centerRoadblockBesideSheet(item, this) }
            setOnDismissListener {
                roadblockInfoPanel = null
                roadblockInfoItem = null
            }
            roadblockInfoPanel = this
            roadblockInfoItem = item
            showEdgeToEdge()
        }

        // The card details (name, length, from/to, validity) arrive asynchronously; they
        // grow the panel, so the roadblock is re-centered in the reduced free space.
        requestRoadblockDetails(item) {
            if (dialog.isShowing) {
                bind()
                centerRoadblockBesideSheet(item, dialog)
            }
        }
    }

    /**
     * Sizes and places the info panel sheet for the current orientation: full-width at
     * the bottom in portrait, docked to the start side at half the screen width in
     * landscape. Re-applied on orientation changes while the panel is open.
     */
    private fun applyRoadblockPanelLayout(dialog: BottomSheetDialog) {
        val sheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = if (isLandscape) binding.root.width / 2 else ViewGroup.LayoutParams.MATCH_PARENT
        dialog.behavior.maxWidth = width
        sheet.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            this.width = width
            gravity = if (isLandscape) Gravity.START else Gravity.CENTER_HORIZONTAL
        }
    }

    /**
     * Centers the roadblock bounding box in the map area left free by the info panel
     * (native centerOnArea with a view rectangle): above it in portrait, on its right
     * side in landscape.
     */
    private fun centerRoadblockBesideSheet(item: RoadblockItem, dialog: BottomSheetDialog) {
        val sheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        // Pre-draw: the sheet size is final only after the pending layout pass. Its
        // position must be computed, not measured: the show() slide-up animation may
        // still be running, so the settled top is the screen bottom minus the sheet height.
        sheet.doOnPreDraw {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val viewRc = if (isLandscape) {
                getFreeSpaceRectangle(leftLimit = sheet.width)
            } else {
                getFreeSpaceRectangle(bottomLimit = binding.root.height - sheet.height)
            }
            SdkCall.execute {
                item.event.boundingBox?.let {
                    gemSurfaceView.mapView?.centerOnRectArea(
                        area = it,
                        zoomLevel = -1,
                        viewRc = viewRc,
                        animation = Animation(EAnimation.Linear, duration = 900),
                    )
                }
            }
        }
    }

    // ---- Roadblock deletion ----------------------------------------------------

    private fun confirmDeleteFromMap(item: RoadblockItem, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_one_item)
            .setMessage(R.string.action_can_not_be_undone)
            .setPositiveButton(R.string.yes) { _, _ ->
                SdkCall.execute { Traffic().removeUserRoadblock(item.event) }
                onDeleted()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun confirmDeleteListItem(item: RoadblockItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_one_item)
            .setMessage(R.string.action_can_not_be_undone)
            .setPositiveButton(R.string.yes) { _, _ ->
                SdkCall.execute { Traffic().removeUserRoadblock(item.event) }
                val remaining = roadblocksAdapter.removeItem(item)
                binding.emptyView.isVisible = remaining == 0
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    // ---- Roadblocks list -------------------------------------------------------

    private fun showRoadblocksPanel() {
        binding.roadblocksPanel.isVisible = true
        binding.emptyView.isVisible = false
        // The panel's primary-colored toolbar sits under the status bar: white icons.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        loadRoadblocks()
    }

    private fun hideRoadblocksPanel() {
        binding.roadblocksPanel.isVisible = false
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode
    }

    private fun loadRoadblocks() = SdkCall.execute {
        // Newest first, like the Magic Earth roadblocks view.
        val events = Traffic().persistentRoadblocks?.reversed() ?: emptyList()
        val items = events.map(::createRoadblockItem)

        runOnAliveUi {
            roadblocksAdapter.submitItems(items)
            binding.emptyView.isVisible = items.isEmpty()
        }

        // The item details (name, length, from/to, validity) arrive asynchronously.
        items.forEach { item ->
            requestRoadblockDetails(item) { roadblocksAdapter.refreshItem(item) }
        }
    }

    /** Runs on the SDK thread. */
    private fun createRoadblockItem(event: TrafficEvent) = RoadblockItem(event).apply {
        name = event.description ?: ""
        transportMode = when (event.affectedTransportMode) {
            ERouteTransportMode.Lorry.value -> ERouteTransportMode.Lorry
            ERouteTransportMode.Bicycle.value -> ERouteTransportMode.Bicycle
            else -> ERouteTransportMode.Car
        }
    }

    /** Fetches the item details asynchronously; onReady runs on the UI thread when done. */
    private fun requestRoadblockDetails(item: RoadblockItem, onReady: () -> Unit) = SdkCall.execute {
        item.previewDataListener = ProgressListener.create(onCompleted = { errorCode, _ ->
            if (errorCode == GemError.NoError) {
                SdkCall.execute {
                    fillRoadblockItem(item)
                    runOnAliveUi(onReady)
                }
            }
        })
        item.event.getPreviewData(item.parameters, item.previewDataListener!!)
    }

    /** Runs on the SDK thread. Mirrors the fields used by the Magic Earth RoadblocksController. */
    private fun fillRoadblockItem(item: RoadblockItem) {
        var startStamp = 0L
        var endStamp = 0L
        var validFromLabel = ""
        var validUntilLabel = ""

        for (parameter in item.parameters.asArrayList()) {
            when (parameter.key) {
                "id" -> item.name = parameter.valueString

                "distance" -> {
                    val distText = GemUtil.getDistText(parameter.valueLong.toInt(), EUnitSystem.Metric)
                    item.lengthText = getString(R.string.length)
                    item.lengthValue = "${item.lengthText}: ${distText.first} ${distText.second}"
                }

                "from" -> {
                    item.fromText = label(parameter.name, R.string.from)
                    item.fromValue = parameter.valueString
                }

                "to" -> {
                    item.toText = label(parameter.name, R.string.to)
                    item.toValue = parameter.valueString
                }

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
            item.validFromText = validFromLabel.ifEmpty { label(null, R.string.valid_from) }
            item.validFromValue = formatDateTime(startStamp)
        }
        if (endStamp > 0 && endStamp != startStamp) {
            item.validUntilText = validUntilLabel.ifEmpty { label(null, R.string.valid_until) }
            item.validUntilValue = formatDateTime(endStamp)
        }
    }

    private fun label(name: String?, fallback: Int): String {
        val text = name?.takeIf { it.isNotEmpty() } ?: getString(fallback)
        return if (text.endsWith(":")) text else "$text:"
    }

    private fun formatDateTime(epochSeconds: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochSeconds * 1000))

    private fun showRoadblockOnMap(item: RoadblockItem) {
        hideRoadblocksPanel()
        // Same presentation as tapping the roadblock icon on the map: info panel first,
        // then the roadblock centered in the space above it (a fresh item so the panel
        // stays valid after the list is closed).
        SdkCall.execute {
            val freshItem = createRoadblockItem(item.event)
            runOnAliveUi { showRoadblockInfoPanel(freshItem) }
        }
    }

    // ---- Transport mode --------------------------------------------------------

    private fun showTransportModeDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = TransportModeDialogLayoutBinding.inflate(layoutInflater).apply {
            when (selectedTransportMode) {
                ERouteTransportMode.Lorry -> truckChip.isChecked = true
                ERouteTransportMode.Bicycle -> bikeChip.isChecked = true
                else -> carChip.isChecked = true
            }

            // Click listeners instead of a checked-state listener so tapping the already
            // selected chip also dismisses the dialog (selectionRequired keeps it checked
            // without firing a state change).
            mapOf(
                carChip to ERouteTransportMode.Car,
                truckChip to ERouteTransportMode.Lorry,
                bikeChip to ERouteTransportMode.Bicycle,
            ).forEach { (chip, mode) ->
                chip.setOnClickListener {
                    selectedTransportMode = mode
                    dialog.dismiss()
                }
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            setContentView(dialogBinding.root)
            showEdgeToEdge()
        }
    }

    // ---- Logo viewport -------------------------------------------------------

    /** Updates the Magic Lane logo viewport to avoid overlapping with the definition toolbar. */
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

        val left = insets?.left ?: 0
        val top = if (binding.defineRoadblockTopToolbar.isVisible) {
            binding.defineRoadblockTopToolbar.bottom
        } else {
            insets?.top ?: 0
        }
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (height - (insets?.bottom ?: 0)).coerceAtLeast(top)

        return Rect(left, top, right, bottom)
    }

    /**
     * Returns the map area not covered by the system bars and cutouts, inset by extra
     * padding. [bottomLimit] caps the bottom edge and [leftLimit] pushes the left edge,
     * e.g. at the top or the right side of an open panel.
     */
    private fun getFreeSpaceRectangle(bottomLimit: Int = Int.MAX_VALUE, leftLimit: Int = 0): Rect {
        val padding = resources.getDimensionPixelSize(R.dimen.padding_40)
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val left = maxOf(insets?.left ?: 0, leftLimit) + padding
        val right = (binding.root.width - (insets?.right ?: 0) - padding).coerceAtLeast(left + 1)
        val top = (insets?.top ?: 0) + padding
        val bottomEdge = minOf(binding.root.height - (insets?.bottom ?: 0), bottomLimit)
        val bottom = (bottomEdge - padding).coerceAtLeast(top + 1)

        return Rect(left, top, right, bottom)
    }

    // ---- Dialogs ---------------------------------------------------------------

    /**
     * Shows the sheet drawing edge-to-edge, so its background also fills the navigation
     * bar area: with an opaque navigation bar (3-button mode) the sheet would otherwise
     * stop above it and the system would paint that strip a different color. Material
     * only lays the sheet out edge-to-edge when the dialog window's navigation bar is
     * transparent, hence the explicit color.
     */
    private fun BottomSheetDialog.showEdgeToEdge() {
        window?.let { window ->
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightNavigationBars = !isNightMode
        }
        show()
    }

    private fun showExplanationDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.define_roadblock)
            message.text = getString(R.string.explanation_message)
            closeButton.setOnClickListener { dialog.dismiss() }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            limitSheetHeight()
            setContentView(dialogBinding.root)
            showEdgeToEdge()
        }
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            closeButton.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            limitSheetHeight()
            setContentView(dialogBinding.root)
            showEdgeToEdge()
        }
    }

    /**
     * Caps the sheet at half the screen height in portrait and 75% in landscape; the
     * dialog layout keeps its message scrollable so capped content is never cut off.
     * The activity handles orientation changes itself (no recreation), so open sheets
     * are tracked and their cap is recomputed in [onConfigurationChanged].
     */
    private fun BottomSheetDialog.limitSheetHeight() {
        applySheetHeightLimit()
        heightCappedSheets.add(this)
        setOnDismissListener { heightCappedSheets.remove(this) }
    }

    private fun BottomSheetDialog.applySheetHeightLimit() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val fraction = if (isLandscape) 0.75f else 0.5f
        behavior.maxHeight = (resources.displayMetrics.heightPixels * fraction).toInt()
        findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)?.requestLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        heightCappedSheets.forEach { it.applySheetHeightLimit() }
        roadblockInfoPanel?.let { dialog ->
            // Pre-draw: the new screen dimensions are available only after the activity
            // content re-layouts for the new orientation.
            binding.root.doOnPreDraw {
                applyRoadblockPanelLayout(dialog)
                roadblockInfoItem?.let { centerRoadblockBesideSheet(it, dialog) }
            }
        }
    }

    // ---- Utilities -----------------------------------------------------------

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (!isFinishing && !isDestroyed) block()
        }
    }
}
