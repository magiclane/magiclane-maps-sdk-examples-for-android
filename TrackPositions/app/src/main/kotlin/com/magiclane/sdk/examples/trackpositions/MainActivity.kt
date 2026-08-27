/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.trackpositions

import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EPathFileFormat
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.trackpositions.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.trackpositions.databinding.DialogLayoutBinding
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.sensordatasource.ExternalDataSource
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import java.io.File
import java.util.Locale
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Records a sequence of GPS positions, exports the resulting track to a file, and
 * visualizes the saved path on the map.
 *
 * Real GPS input is replaced by a built-in dataset ([positions]) that is pushed
 * into an [ExternalDataSource] at a fixed cadence. Tapping the recording FAB
 * toggles position tracking; on stop the captured path is exported in the
 * user-selected format and the camera animates to frame it.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        val positions = arrayOf(
            Pair(48.133931, 11.582914),
            Pair(48.134015, 11.583203),
            Pair(48.134057, 11.583348),
            Pair(48.134085, 11.583499),
            Pair(48.134116, 11.583676),
            Pair(48.134144, 11.583854),
            Pair(48.134166, 11.584010),
            Pair(48.134189, 11.584166),
            Pair(48.134210, 11.584312),
            Pair(48.134231, 11.584458),
            Pair(48.134253, 11.584605),
            Pair(48.134274, 11.584751),
            Pair(48.134295, 11.584897),
            Pair(48.134316, 11.585044),
            Pair(48.134338, 11.585190),
            Pair(48.134361, 11.585335),
            Pair(48.134390, 11.585515),
            Pair(48.134419, 11.585695),
            Pair(48.134443, 11.585846),
            Pair(48.134473, 11.585995),
            Pair(48.134503, 11.586126),
            Pair(48.134467, 11.586266),
            Pair(48.134430, 11.586405),
            Pair(48.134394, 11.586545),
            Pair(48.134357, 11.586684),
            Pair(48.134321, 11.586823),
            Pair(48.134283, 11.586967),
            Pair(48.134297, 11.587086),
            Pair(48.134380, 11.587167),
            Pair(48.134464, 11.587249),
            Pair(48.134563, 11.587345),
            Pair(48.134661, 11.587441),
            Pair(48.134761, 11.587534),
            Pair(48.134861, 11.587626),
            Pair(48.134975, 11.587732),
            Pair(48.135089, 11.587838),
            Pair(48.135204, 11.587943),
            Pair(48.135322, 11.588038),
            Pair(48.135451, 11.588137),
            Pair(48.135581, 11.588231),
            Pair(48.135716, 11.588328),
            Pair(48.135851, 11.588426),
            Pair(48.135972, 11.588513),
            Pair(48.136093, 11.588601),
            Pair(48.136207, 11.588680),
            Pair(48.136322, 11.588759),
            Pair(48.136423, 11.588829),
            Pair(48.136524, 11.588898),
            Pair(48.136615, 11.588962),
            Pair(48.136706, 11.589028),
            Pair(48.136807, 11.589117),
            Pair(48.136905, 11.589215),
            Pair(48.136994, 11.589347),
            Pair(48.137081, 11.589481),
            Pair(48.137164, 11.589608),
            Pair(48.137247, 11.589737),
            Pair(48.137344, 11.589894),
            Pair(48.137444, 11.590049),
            Pair(48.137538, 11.590199),
            Pair(48.137632, 11.590350),
            Pair(48.137730, 11.590508),
            Pair(48.137829, 11.590667),
            Pair(48.137934, 11.590834),
            Pair(48.138038, 11.591002),
            Pair(48.138134, 11.591156),
            Pair(48.138229, 11.591310),
            Pair(48.138316, 11.591454),
            Pair(48.138404, 11.591597),
            Pair(48.138496, 11.591749),
            Pair(48.138589, 11.591900),
            Pair(48.138681, 11.592051),
            Pair(48.138773, 11.592203),
            Pair(48.138867, 11.592351),
            Pair(48.138962, 11.592499),
            Pair(48.139041, 11.592624),
            Pair(48.139126, 11.592740),
            Pair(48.139207, 11.592828),
            Pair(48.139287, 11.592917),
            Pair(48.139374, 11.593012),
            Pair(48.139461, 11.593107),
            Pair(48.139571, 11.593208),
            Pair(48.139685, 11.593301),
            Pair(48.139809, 11.593403),
            Pair(48.139934, 11.593505),
            Pair(48.140077, 11.593604),
            Pair(48.140220, 11.593703),
            Pair(48.140364, 11.593802),
            Pair(48.140514, 11.593876),
            Pair(48.140670, 11.593947),
            Pair(48.140827, 11.594018),
        )

        /** Export format selected via the file-format toggle group. */
        var fileType = EPathFileFormat.Gpx

        /** Paths recorded so far; redrawn together every time tracking stops. */
        val paths = ArrayList<Path>()

        /** Seconds between two consecutive tracked positions; bound to the slider. */
        var intervalValue = 0.5f

        /** Fraction of screen width occupied by the side panel in landscape mode. */
        private const val LANDSCAPE_PANEL_WIDTH_RATIO = 0.45f

        /** Inset, in dp, applied on every edge of [getFreeSpaceRect] so the path leaves breathing room. */
        private const val FREE_SPACE_INFLATE_DP = 30

        /** Duration of the camera animation that frames the saved path on stop. */
        private const val PRESENT_PATH_ANIMATION_MS = 900

        /** Period at which simulated GPS positions are pushed into the SDK. */
        private const val SIMULATION_INTERVAL_MS = 1000L

        /** Polyline color drawn under the tracked positions while recording. */
        private val TRACK_POLYLINE_COLOR = Rgba(120, 0, 255, 255)

        /** Scale factor applied to the marker image used for tracked positions. */
        private const val TRACK_MARKER_IMAGE_SIZE = 2.0

        /** Combined window-inset types that the activity treats as "system chrome". */
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    /**
     * Snapshot of the original (portrait) constraints. Cloned at startup so
     * [applyOrientationLayout] can re-derive either layout deterministically
     * without losing the values declared in XML.
     */
    private lateinit var portraitConstraintSet: ConstraintSet

    /** Timer driving the simulated GPS feed. Null when not running. */
    private var timer: Timer? = null

    /** Source feeding simulated positions to the SDK. Created once the SDK reports the road map is ready. */
    private var externalDataSource: ExternalDataSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Snapshot portrait constraints before any orientation-specific tweaks.
        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }

        setupRecordingInsetListener()
        setupFollowGpsInsetListener()
        applyOrientationLayout()
        registerSdkListeners()
        setupBottomPanelControls()

        binding.recording.setOnClickListener { onRecordingClicked() }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    /** Wires up the file-format toggle and the position-interval slider. */
    private fun setupBottomPanelControls() {
        binding.fileFormatsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            fileType = when (checkedId) {
                R.id.ff_gpx -> EPathFileFormat.Gpx
                R.id.ff_kml -> EPathFileFormat.Kml
                R.id.ff_json -> EPathFileFormat.GeoJson
                R.id.ff_lat_lon -> EPathFileFormat.LatLonTxt
                else -> EPathFileFormat.Gpx
            }
        }
        binding.fileFormatsGroup.check(R.id.ff_gpx)

        binding.intervalSlider.addOnChangeListener { _, value, _ ->
            intervalValue = value
            binding.secondsText.text = formatSeconds(value)
        }
        binding.secondsText.text = formatSeconds(binding.intervalSlider.valueFrom)
    }

    private fun formatSeconds(value: Float): String = String.format(Locale.getDefault(), "%.1f s", value)

    /**
     * Toggles position tracking. Stopping the recording also exports the
     * captured path and animates the camera to frame it.
     */
    private fun onRecordingClicked() {
        val mapView = binding.gemSurfaceView.mapView ?: return
        var isTrackingPositions = false

        SdkCall.execute {
            isTrackingPositions = mapView.isTrackedPositions()
            if (!isTrackingPositions) {
                isTrackingPositions = startTracking(mapView)
            } else {
                stopTracking(mapView)
                isTrackingPositions = false
            }
        }

        setupRecordingButton(isTrackingPositions)
    }

    /** Asks the SDK to begin tracking; @return true if the request was accepted. */
    private fun startTracking(mapView: MapView): Boolean {
        val renderSettings = MarkerCollectionRenderSettings(
            image = Image.produceWithId(SdkImages.Core.GreenBall.value),
            polylineInnerColor = TRACK_POLYLINE_COLOR,
        ).apply { imageSize = TRACK_MARKER_IMAGE_SIZE }

        val error = mapView.startTrackPositions(
            (intervalValue * 1000).toInt(),
            renderSettings,
            externalDataSource,
        )
        return error == GemError.NoError
    }

    private fun stopTracking(mapView: MapView) {
        saveRecording()
        // The bottom panel is restored on the main thread; post-ing onto it
        // lets that layout pass complete before getFreeSpaceRect() reads its
        // position to frame the saved path.
        binding.bottomPanel.post {
            SdkCall.runSynced { presentSavedRecordings() }
        }
        mapView.stopTrackPositions()
    }

    /**
     * Draws every recorded path on the map and animates the camera to frame
     * the union of their geographic areas.
     */
    private fun presentSavedRecordings() {
        if (paths.isEmpty()) return
        binding.gemSurfaceView.mapView?.run {
            displayPaths(paths, Rgba.green(), Rgba.yellow())

            // Union the area of every recorded path so the camera frames them all at once.
            var area = paths[0].area
            paths.forEach { path -> path.area?.let { area = area?.makeUnion(it) } }

            if (area != null && viewport != null) {
                centerOnRectArea(
                    area,
                    viewRc = getFreeSpaceRect(),
                    animation = Animation(EAnimation.Linear, duration = PRESENT_PATH_ANIMATION_MS),
                )
            }
        }
    }

    /**
     * Exports the positions tracked since the last [startTracking] to disk in
     * the currently selected [fileType], and appends the resulting [Path] to
     * [paths]. Shows a toast on success and a dialog on SDK error.
     */
    private fun saveRecording() {
        val (coordinates, err) = binding.gemSurfaceView.mapView?.getTrackedPositions() ?: return
        if (GemError.isError(err)) {
            Util.postOnMain { showDialog(SdkCall.runSynced { GemError.getMessage(err, this) } ?: "") }
            return
        }

        coordinates?.let {
            val path = Path.produceWithCoords(it)
            paths.add(path)

            val bytes = path.exportAs(fileType)?.bytes ?: return
            val extension = when (fileType) {
                EPathFileFormat.Gpx -> "gpx"
                EPathFileFormat.Kml -> "kml"
                EPathFileFormat.GeoJson -> "json"
                EPathFileFormat.LatLonTxt -> "txt"
                else -> "gpx"
            }
            val file = File(
                GemSdk.internalStoragePath + File.separator + "exported",
                "path${paths.size}.$extension",
            )
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()

            if (file.createNewFile()) {
                file.writeBytes(bytes)
                Util.postOnMain {
                    if (isActivityAlive()) {
                        Toast.makeText(this, "Saved route ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        timer?.cancel()
        timer = null
        SdkCall.execute { externalDataSource?.stop() }
        GemSdk.release()
        exitProcess(0)
    }

    /** Shows a non-dismissable bottom-sheet error dialog. */
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
    }

    /**
     * Switches the layout between portrait and landscape variants. The portrait
     * snapshot is the source of truth; landscape repositioning is layered on
     * top. View visibilities are preserved across the constraint swap because
     * applying a fresh [ConstraintSet] resets them.
     */
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Save visibility — applying a ConstraintSet resets it.
        val bottomPanelVis = binding.bottomPanel.visibility
        val recordingVis = binding.recording.visibility
        val followCursorVis = binding.followGpsButton.visibility
        val progressBarVis = binding.progressBar.visibility

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // Anchor the bottom panel to the screen's left side and stretch
                // the recording FAB down to the screen bottom.
                val panelWidth = (resources.displayMetrics.widthPixels * LANDSCAPE_PANEL_WIDTH_RATIO).toInt()
                constrainWidth(R.id.bottom_panel, panelWidth)
                connect(R.id.bottom_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                clear(R.id.bottom_panel, ConstraintSet.END)

                clear(R.id.recording, ConstraintSet.BOTTOM)
                connect(R.id.recording, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            }
        }.applyTo(rootLayout)

        binding.bottomPanel.visibility = bottomPanelVis
        binding.recording.visibility = recordingVis
        binding.followGpsButton.visibility = followCursorVis
        binding.progressBar.visibility = progressBarVis

        // Re-fire insets so margins reflect the new layout.
        ViewCompat.requestApplyInsets(binding.recording)
    }

    /**
     * Adjusts the recording FAB margins so it stays clear of system bars and
     * display cutouts. When the bottom panel is hidden, the FAB also needs a
     * bottom inset since the panel is no longer absorbing it.
     */
    private fun setupRecordingInsetListener() {
        val defaultPadding = resources.getDimensionPixelSize(R.dimen.default_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recording) { v, windowInsets ->
            val sys = windowInsets.getInsets(SYSTEM_INSET_TYPES)
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val panelHidden = !binding.bottomPanel.isVisible
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = if (panelHidden) sys.left else 0
                marginEnd = defaultPadding + sys.right
                bottomMargin = if (isLandscape || panelHidden) defaultPadding + sys.bottom else defaultPadding
            }
            windowInsets
        }
    }

    /**
     * Mirrors the recording inset logic for the follow-GPS FAB. The bottom
     * inset only kicks in when the recording FAB is hidden — otherwise the
     * recording FAB sitting below it already keeps it clear of the system bar.
     */
    private fun setupFollowGpsInsetListener() {
        val defaultPadding = resources.getDimensionPixelSize(R.dimen.default_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.followGpsButton) { v, windowInsets ->
            val sys = windowInsets.getInsets(SYSTEM_INSET_TYPES)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd = defaultPadding + sys.right
                bottomMargin = defaultPadding + if (!binding.recording.isVisible) sys.bottom else 0
            }
            windowInsets
        }
    }

    // this adjusts Magic Lane logo position on map
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val w = viewport.width
            val h = viewport.height
            // Use viewport dimensions to derive orientation — they always
            // reflect the surface the SDK actually draws on.
            val isLandscape = w > h

            mapView.preferences?.focusViewport = if (isLandscape) {
                val left = if (binding.bottomPanel.isVisible) binding.bottomPanel.right else insets?.left ?: 0
                val top = insets?.top ?: 0
                val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
                val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
                Rect(left, top, right, bottom)
            } else {
                val left = insets?.left ?: 0
                val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
                val top = insets?.top ?: 0
                val bottom = if (binding.bottomPanel.isVisible) {
                    binding.bottomPanel.top.coerceAtLeast(top)
                } else {
                    (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
                }
                Rect(left, top, right, bottom)
            }
        }
    }

    /**
     * Returns the free screen rectangle — the area not covered by toolbar,
     * bottom panel (when visible), or system bars/cutouts — inflated inward by
     * [FREE_SPACE_INFLATE_DP] so framed content keeps a visible margin.
     *
     * Differs from [updateFocusViewport] in two ways: the toolbar is excluded
     * from the top (focus viewport accepts the overlap), and the result is
     * always shrunk inward.
     */
    private fun getFreeSpaceRect(): Rect {
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        val w = binding.root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = binding.root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val top = binding.toolbar.bottom
        val inflate = (FREE_SPACE_INFLATE_DP * resources.displayMetrics.density).toInt()

        val left: Int
        val right: Int
        val bottom: Int
        if (isLandscape) {
            left = if (binding.bottomPanel.isVisible) binding.bottomPanel.right else (insets?.left ?: 0)
            right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
        } else {
            left = insets?.left ?: 0
            right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            bottom = if (binding.bottomPanel.isVisible) {
                binding.bottomPanel.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
        }
        return Rect(left + inflate, top + inflate, right - inflate, bottom - inflate)
    }

    /**
     * Anchors the GPS cursor: bottom-right region when the bottom panel is
     * visible (leaving room to read the panel), centered lower on screen when
     * it is hidden.
     */
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hasPanels = binding.bottomPanel.isVisible
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) {
                    XyF(if (hasPanels) 0.7f else 0.5f, 0.75f)
                } else {
                    XyF(0.5f, if (hasPanels) 0.62f else 0.75f)
                }
        }
    }

    /**
     * Wires every SDK callback the activity needs: surface lifecycle, road map
     * readiness (which kicks off the simulated GPS feed), and token validation.
     */
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
            applyCameraFocus()
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
            applyCameraFocus()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // Only react to the first transition into the ready state.
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                startPositionSimulation()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    /**
     * Spins up the simulated GPS pipeline: opens an [ExternalDataSource], waits
     * for the first valid fix to reveal the recording UI, then pushes every
     * entry of [positions] on a fixed-rate timer until exhausted.
     */
    private fun startPositionSimulation() {
        SdkCall.execute {
            externalDataSource = DataSourceFactory.produceExternal(arrayListOf(EDataType.Position)).also {
                it?.start()
                PositionService.dataSource = it
            }

            awaitFirstValidPosition()

            val dataSource = externalDataSource ?: return@execute
            var index = 0
            timer = fixedRateTimer("timer", false, 0L, SIMULATION_INTERVAL_MS) {
                SdkCall.execute { pushSimulatedPosition(dataSource, index) }
                index++
                if (index == positions.size) {
                    timer?.cancel()
                    onSimulationFinished()
                }
            }
        }
    }

    /**
     * Subscribes to the [PositionService] and, on the first valid position,
     * reveals the recording UI and starts the camera following the cursor.
     * The listener self-removes after firing once.
     */
    private fun awaitFirstValidPosition() {
        lateinit var positionListener: PositionListener
        positionListener = PositionListener { position ->
            if (!position.isValid()) return@PositionListener
            PositionService.removeListener(positionListener)

            val mapView = binding.gemSurfaceView.mapView ?: return@PositionListener
            Util.postOnMain {
                binding.recording.isVisible = true
                ViewCompat.requestApplyInsets(binding.followGpsButton)
                setupFollowGpsButton()
            }
            mapView.followPosition()
        }
        PositionService.addListener(positionListener, EDataType.Position)
    }

    /** Pushes a single simulated position from [positions] into [dataSource]. */
    private fun pushSimulatedPosition(dataSource: ExternalDataSource, index: Int) {
        PositionData.produce(
            System.currentTimeMillis(),
            positions[index].first,
            positions[index].second,
            -1.0,
            positions.getBearing(index),
            positions.getSpeed(index),
        )?.let(dataSource::pushData)
    }

    /**
     * Runs after the simulated dataset is exhausted: hides recording chrome,
     * persists the in-progress recording (if any), and frames the camera on
     * the resulting path.
     */
    private fun onSimulationFinished() {
        Util.postOnMain {
            binding.recording.isVisible = false
            binding.bottomPanel.isVisible = false
            updateFocusViewport()
            applyCameraFocus()
            ViewCompat.requestApplyInsets(binding.recording)
            ViewCompat.requestApplyInsets(binding.followGpsButton)

            // Frames the saved path. saveRecording() in the SdkCall.execute
            // below is enqueued on the SDK thread *before* this runSynced is
            // posted from the main thread, so paths is populated by then.
            SdkCall.runSynced { presentSavedRecordings() }
        }

        SdkCall.execute {
            val mapView = binding.gemSurfaceView.mapView ?: return@execute
            if (mapView.isTrackedPositions()) {
                saveRecording()
                mapView.stopTrackPositions()
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

    /** Posts a block to the UI thread only if the activity is still alive. */
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    /**
     * Wires the follow-GPS FAB: toggles its visibility around enter/exit of
     * follow-position mode, and re-enables following on tap.
     */
    private fun setupFollowGpsButton() {
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = { followGpsButton.isVisible = true }
                onEnterFollowingPosition = { followGpsButton.isVisible = false }
                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    /**
     * Updates the recording FAB visuals and surrounding controls to match the
     * given tracking state — red stop icon and hidden panel while recording,
     * green play icon and visible panel otherwise — and re-applies the map
     * focus so the GPS cursor stays in a sensible spot.
     */
    private fun setupRecordingButton(isTracked: Boolean) {
        binding.apply {
            recording.backgroundTintList = ContextCompat.getColorStateList(
                this@MainActivity,
                if (isTracked) R.color.stop_color else R.color.start_color,
            )
            recording.setImageDrawable(
                ContextCompat.getDrawable(
                    this@MainActivity,
                    if (isTracked) R.drawable.stop_24 else R.drawable.play_arrow_34,
                ),
            )
            bottomPanel.isVisible = !isTracked
            intervalSlider.isEnabled = !isTracked
            fileFormatsGroup.isEnabled = !isTracked
        }

        binding.bottomPanel.post {
            updateFocusViewport()
            applyCameraFocus()
        }

        ViewCompat.requestApplyInsets(binding.recording)
    }
}

//region Geodetic helpers used by the simulated GPS feed.

/** Earth radius, in meters, used by the geodetic distance helper. */
private const val EARTH_RADIUS_METERS = 6378100.0

/**
 * Great-circle distance, in meters, between two (lat, lng) pairs.
 *
 * Uses a 3-D dot-product formulation (project both points onto the unit sphere,
 * take the arc-cosine of their dot product). Accurate enough for short
 * distances; for very small angles a haversine formulation would be numerically
 * better, but here the inputs are spaced meters apart and acos is fine.
 */
fun Pair<Double, Double>.getDistanceOnGeoid(to: Pair<Double, Double>): Double {
    val (latitude1, longitude1) = this
    val (latitude2, longitude2) = to

    // Degrees → radians.
    val lat1 = latitude1 * Math.PI / 180.0
    val lon1 = longitude1 * Math.PI / 180.0
    val lat2 = latitude2 * Math.PI / 180.0
    val lon2 = longitude2 * Math.PI / 180.0

    // 3-D Cartesian positions on the sphere.
    val rho1 = EARTH_RADIUS_METERS * cos(lat1)
    val z1 = EARTH_RADIUS_METERS * sin(lat1)
    val x1 = rho1 * cos(lon1)
    val y1 = rho1 * sin(lon1)

    val rho2 = EARTH_RADIUS_METERS * cos(lat2)
    val z2 = EARTH_RADIUS_METERS * sin(lat2)
    val x2 = rho2 * cos(lon2)
    val y2 = rho2 * sin(lon2)

    // Dot product → angular distance → arc length on the surface.
    val dot = x1 * x2 + y1 * y2 + z1 * z2
    val theta = acos(dot / (EARTH_RADIUS_METERS * EARTH_RADIUS_METERS))
    return EARTH_RADIUS_METERS * theta
}

/**
 * Distance between point [index] and the previous point — used as a stand-in
 * for the simulated speed (m/s, since the simulation cadence is one second).
 *
 * @return distance in meters, or -1.0 if [index] has no predecessor.
 */
fun Array<Pair<Double, Double>>.getSpeed(index: Int): Double {
    if (index in 1 until size) {
        return this[index - 1].getDistanceOnGeoid(this[index])
    }
    return -1.0
}

/**
 * Bearing β = atan2(X, Y) from the previous point to the point at [index],
 * expressed in degrees.
 *
 * @return bearing in degrees, or -1.0 if [index] has no predecessor.
 */
fun Array<Pair<Double, Double>>.getBearing(index: Int): Double {
    if (index in 1 until size) {
        val prev = this[index - 1]
        val curr = this[index]
        val x = cos(curr.first) * sin(curr.second - prev.second)
        val y = cos(prev.first) * sin(curr.first) -
            sin(prev.first) * cos(curr.first) * cos(curr.second - prev.second)
        return atan2(x, y) * 180 / Math.PI
    }
    return -1.0
}
//endregion

// region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("TracPositionsIdlingResource")
}
//endregion
