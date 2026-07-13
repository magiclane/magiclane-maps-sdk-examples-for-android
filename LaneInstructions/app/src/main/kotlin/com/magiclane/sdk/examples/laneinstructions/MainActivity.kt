/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.laneinstructions

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.examples.laneinstructions.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.laneinstructions.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    /** Snapshot of everything the UI needs to render a single navigation instruction. */
    private data class InstructionUiState(
        val instructionText: String,
        val instructionIcon: Bitmap?,
        val sameInstructionIcon: Boolean,
        val instructionDistance: String,
        val lanesBitmap: Bitmap?,
        val etaText: String,
        val rttText: String,
        val rtdText: String,
    )

    private companion object {
        private const val INSTRUCTION_ICON_SIZE_PX = 100

        // Fraction of the screen width occupied by the navigation panels in landscape orientation.
        private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.4f
    }

    private lateinit var binding: ActivityMainBinding

    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var lanePanelHeight = 0

    // Pixel width available to render the lane image. Recomputed per orientation because the lane
    // panel spans the full width in portrait but only the gap to the right of the panels in landscape.
    private var availableWidth = 0

    private var activeDialog: BottomSheetDialog? = null

    private var navigationStatus = ENavigationStatus.Running

    // Captured once in portrait orientation and reused as the base for every orientation change, so
    // the portrait layout never has to be recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    // Latest system bar / display cutout insets. Updated from the window insets listener rather than
    // read synchronously, because the insets are not available immediately and change on rotation
    // (a synchronous read during onConfigurationChanged still returns the pre-rotation values).
    private var barInsets: Insets = Insets.NONE

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }
            applyCameraFocus()
            runOnUiThread { setNavigationPanelsVisible(isVisible = true) }
        },
        onNavigationInstructionUpdated = { instr ->
            SdkCall.execute {
                val uiState = instr.toUiState()
                runOnUiThread {
                    renderInstructionUi(uiState)
                }
            }
        },
        onDestinationReached = { onNavigationEnded() },
        onNotifyStatusChange = { status ->
            navigationStatus = status
            runOnUiThread { refreshStatusMessage() }
        },
        onNavigationError = { error -> onNavigationEnded(error) },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true,
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false
            if (errorCode != GemError.NoError) {
                showDialog(
                    getString(
                        R.string.routing_process_failed,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                    ),
                )
            }
        },
        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Use light (white) status bar symbols so they remain legible over the dark map / toolbar.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        setupTts()
        lanePanelHeight = resources.getDimension(R.dimen.lane_panel_height).toInt()

        // Capture the portrait constraints once, then adapt them to the current orientation.
        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        // When the app is launched directly in landscape, the panels keep their XML start/end
        // margins as side margins (matching the RouteSimulation example).
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
            val setPanelHorizontalMargins = { panel: ConstraintLayout ->
                val params = panel.layoutParams as ConstraintLayout.LayoutParams
                params.marginStart = panelMargin
                params.marginEnd = panelMargin
                panel.layoutParams = params
            }

            setPanelHorizontalMargins(binding.topPanel)
            setPanelHorizontalMargins(binding.bottomPanel)
        }

        // Keep the lane panel's inset-dependent margins correct: the insets arrive after layout and
        // change on rotation, so we update them here whenever the system (re)dispatches insets.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            barInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            applyLaneLandscapeInsets()
            insets
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
        // The new orientation's insets are not known yet; request a fresh dispatch so the window
        // insets listener can correct the lane panel margins (and recompute its width).
        ViewCompat.requestApplyInsets(binding.root)
        binding.root.post { updateFocusViewport() }
    }

    override fun onStop() {
        super.onStop()
        // Release the SDK.
        if (isFinishing) {
            dismissActiveDialog()
            clearSdkListeners()
            SoundUtils.removeTTSPlayerInitializationListener(this)
            GemSdk.release()
            exitProcess(0)
        }
    }

    private fun setupTts() {
        SoundUtils.addTTSPlayerInitializationListener(this)
    }

    /**
     * Rebuilds the panel constraints for the current orientation, starting from the captured
     * portrait set. In landscape the top and bottom panels are docked to the left (mirroring the
     * RouteSimulation example) and the lane panel is centered horizontally inside the free map area
     * (the focus viewport) and pinned to its bottom.
     */
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores the visibility captured at clone time (all panels were
        // GONE then), so save the live visibility state and restore it afterwards.
        val topVis = binding.topPanel.visibility
        val laneVis = binding.laneContainer.visibility
        val bottomVis = binding.bottomPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility

        val screenWidth = resources.displayMetrics.widthPixels
        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        // Best-known insets now; the window insets listener corrects them once they are dispatched.
        val rightInset = barInsets.right
        val bottomInset = barInsets.bottom

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // Dock the top and bottom panels to the left, each taking 40% of the screen width.
                val panelWidth = (screenWidth * LANDSCAPE_PANEL_WIDTH_FRACTION).toInt()

                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(id, ConstraintSet.END)
                }

                // The free map area in landscape is the focus viewport: the region to the right of
                // the docked panels and inside the system insets. Center the lane panel horizontally
                // within it (START at the panels' right edge, END at the screen right minus the right
                // inset, bias 0.5) and pin it to the bottom.
                clear(R.id.lane_container, ConstraintSet.TOP)
                clear(R.id.lane_container, ConstraintSet.START)
                clear(R.id.lane_container, ConstraintSet.END)
                connect(R.id.lane_container, ConstraintSet.START, R.id.top_panel, ConstraintSet.END, 0)
                connect(R.id.lane_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, rightInset)
                connect(
                    R.id.lane_container,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    bottomInset + bigPadding,
                )
                setHorizontalBias(R.id.lane_container, 0.5f)
            } else {
                // Restore the full-width panels explicitly rather than relying on the cloned margins,
                // which may carry stale landscape insets when the app was first launched in landscape.
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
        }.applyTo(rootLayout)

        binding.topPanel.visibility = topVis
        binding.laneContainer.visibility = laneVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis

        updateLaneAvailableWidth(isLandscape, panelMargin, rightInset)
    }

    /** Recomputes the pixel width used to render the lane image for the current orientation. */
    private fun updateLaneAvailableWidth(isLandscape: Boolean, panelMargin: Int, rightInset: Int) {
        val screenWidth = resources.displayMetrics.widthPixels
        val laneInnerPadding = 2 * resources.getDimension(R.dimen.medium_padding).toInt()

        availableWidth = if (isLandscape) {
            // Width of the focus viewport (area to the right of the docked panels, inside the right
            // inset) minus the lane image margins, so the image fits while staying centered in it.
            val panelWidth = (screenWidth * LANDSCAPE_PANEL_WIDTH_FRACTION).toInt()
            (screenWidth - panelMargin - panelWidth - rightInset - laneInnerPadding)
                .coerceAtLeast(resources.getDimensionPixelSize(R.dimen.turn_image_size))
        } else {
            screenWidth -
                2 * resources.getDimension(R.dimen.big_padding).toInt() -
                laneInnerPadding
        }
    }

    /**
     * Applies the current insets to the landscape lane panel: the right inset shifts its right edge
     * to match the focus viewport (so it stays centered inside it), and the bottom inset keeps it
     * clear of the bottom system bar. Also refreshes the lane image width. No-op in portrait, where
     * the lane panel keeps its cloned XML constraints. Driven by the window insets listener so it
     * always uses fresh insets (a synchronous read is stale right after a rotation).
     */
    private fun applyLaneLandscapeInsets() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)

        if (isLandscape) {
            val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
            val params = binding.laneContainer.layoutParams as ConstraintLayout.LayoutParams
            params.marginEnd = barInsets.right
            params.bottomMargin = bigPadding + barInsets.bottom
            binding.laneContainer.layoutParams = params
        }

        updateLaneAvailableWidth(isLandscape, panelMargin, barInsets.right)
    }

    /** Adjusts the GPS arrow position on the map depending on orientation. */
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // Camera focus points (normalized) controlling where the GPS arrow sits on screen. In
            // landscape the panels cover the left side, so the focus is pushed right to keep the
            // arrow inside the visible map area.
            val portraitCameraFocus = XyF(0.5f, 0.725f)
            val landscapeCameraFocus = XyF(0.7f, 0.7f)

            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) landscapeCameraFocus else portraitCameraFocus
        }
    }

    /** Adjusts the Magic Lane logo position by constraining the map's focus viewport to the free map area. */
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    /**
     * Computes the rectangle of the map that is not covered by the toolbar or the navigation
     * panels. The Magic Lane logo and other map decorations are kept inside this rectangle.
     */
    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // The toolbar always spans the top of the screen, so the map content starts below it.
        val belowToolbar = max(binding.toolbar.bottom, insets?.top ?: 0)

        return if (isLandscape) {
            // Guard against root dimensions still reflecting the previous orientation.
            val w = max(width, height)
            val h = min(width, height)

            // Panels are docked to the left, so keep the focus area to the right of the top panel.
            val left = if (binding.topPanel.isVisible) binding.topPanel.right else insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(belowToolbar)
            Rect(left, belowToolbar, right, bottom)
        } else {
            val w = min(width, height)
            val h = max(width, height)

            val left = insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val top = if (binding.topPanel.isVisible) binding.topPanel.bottom else belowToolbar
            // The lane panel is intentionally ignored here; only the bottom panel bounds the area.
            val bottom = if (binding.bottomPanel.isVisible) {
                binding.bottomPanel.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Delay simulation start until the worldwide road map is fully downloaded and up to date;
        // the callback is cleared immediately after firing to avoid repeat invocations.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                startSimulation()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                    setNavigationPanelsVisible(isVisible = false)
                }

                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false

                    val simulationIsActive = SdkCall.execute { navigationService.isSimulationActive() } ?: false
                    if (simulationIsActive) {
                        setNavigationPanelsVisible(isVisible = true)
                    }
                }

                // Set on click action for the GPS button.
                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    private fun disableGPSButton() {
        binding.gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = null
            onEnterFollowingPosition = null
            binding.followGpsButton.setOnClickListener(null)
            binding.followGpsButton.isVisible = false
        }
    }

    /**
     * Handles both reaching the destination and navigation errors. A dialog is only shown for real
     * errors (not for a clean finish or an explicit cancel).
     */
    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: ""
                if (message.isNotEmpty()) {
                    showDialog(message)
                }
            }
            setNavigationPanelsVisible(isVisible = false)
            disableGPSButton()
        }

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
    }

    private fun startSimulation() {
        val waypoints = arrayListOf(
            Landmark("Calea Bucuresti", 45.64924625, 25.6180490625),
            Landmark("Harmanului", 45.6549909375, 25.6161609375),
        )
        val error = navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
        )

        if (error != GemError.NoError) {
            showDialog(
                getString(
                    R.string.failed_to_start_simulation,
                    SdkCall.runSynced { GemError.getMessage(error, this) },
                ),
            )
        }
    }

    /** Shows/hides the top and bottom panels together and keeps the focus viewport in sync. */
    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            binding.laneContainer.isVisible = false
            updateFocusViewport()
        } else {
            // Wait for the panels to lay out before measuring their bounds for the focus viewport.
            binding.root.post { updateFocusViewport() }
        }
    }

    /** Shows a transient status message (e.g. "Calculating…") in place of the turn information. */
    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        binding.turnContainer.isVisible = statusMessage.isEmpty()

        if (statusMessage.isNotEmpty()) {
            binding.navInstruction.text = statusMessage
            binding.laneContainer.isVisible = false
        }
    }

    /** Maps the current navigation status to a user facing message, or an empty string when running. */
    private fun getStatusMessage(): String {
        when (navigationStatus) {
            ENavigationStatus.WaitingRoute -> {
                return when (navRoute?.status) {
                    ERouteStatus.WaitingInternetConnection -> getString(R.string.waiting_for_internet_connection)
                    ERouteStatus.Calculating -> getString(R.string.calculating)
                    ERouteStatus.Ready -> getString(R.string.gps_accuracy_not_good_enough)
                    else -> getString(R.string.calculating)
                }
            }

            ENavigationStatus.WaitingGPS -> {
                return if (navigationService.isSimulationActive()) {
                    getString(R.string.calculating)
                } else {
                    getString(R.string.getting_position)
                }
            }

            else -> {
                // Running (or any other status): no status message, the turn info is shown instead.
            }
        }

        return ""
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        dismissActiveDialog()
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
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) {
                activeDialog = null
            }
        }
    }

    private fun dismissActiveDialog() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun NavigationInstruction.toUiState(): InstructionUiState {
        val lanesBitmap = laneImage?.asBitmap(
            availableWidth,
            lanePanelHeight,
            activeColor = Rgba.white(),
        )

        var sameInstructionIcon = false
        val instrIcon = getNextTurnImage(this, INSTRUCTION_ICON_SIZE_PX, INSTRUCTION_ICON_SIZE_PX) { isSame ->
            sameInstructionIcon = isSame
        }

        val currentRoute = navRoute
        return InstructionUiState(
            instructionText = nextStreetName ?: (nextTurnInstruction ?: ""),
            instructionIcon = instrIcon,
            sameInstructionIcon = sameInstructionIcon,
            instructionDistance = getDistanceInMeters(),
            lanesBitmap = lanesBitmap,
            etaText = currentRoute?.getEta().orEmpty(),
            rttText = currentRoute?.getRtt().orEmpty(),
            rtdText = currentRoute?.getRtd().orEmpty(),
        )
    }

    private fun renderInstructionUi(uiState: InstructionUiState) {
        binding.apply {
            navInstruction.text = uiState.instructionText
            if (!uiState.sameInstructionIcon) {
                navInstructionIcon.setImageBitmap(uiState.instructionIcon)
            }

            navInstructionDistance.text = uiState.instructionDistance

            eta.text = uiState.etaText
            rtt.text = uiState.rttText
            rtd.text = uiState.rtdText

            // Only show the lane panel while the turn information (not a status message) is visible.
            // The lane panel does not affect the focus viewport, so no viewport refresh is needed.
            laneContainer.isVisible = (uiState.lanesBitmap != null) && topPanel.isVisible && turnContainer.isVisible
            uiState.lanesBitmap?.let { bitmap ->
                laneImage.setImageBitmap(bitmap)
                laneImage.layoutParams.width = bitmap.width
                laneImage.layoutParams.height = bitmap.height
            }
        }
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).toDisplayString()
    }

    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format(Locale.getDefault(), getString(R.string.time_format_hh_mm), time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).toDisplayString()
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).toDisplayString()
    }

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        onSameImage: (Boolean) -> Unit = {},
    ): Bitmap? {
        if (!navInstr.hasNextTurnInfo()) return null

        if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
            onSameImage(true)
            return null
        }

        val image = navInstr.nextTurnDetails?.abstractGeometryImage
        if (image != null) {
            lastTurnImageId = image.uid
        }

        // Active turn icon: white fill with black outline; inactive: grey fill and outline.
        val aInner = Rgba(255, 255, 255, 255)
        val aOuter = Rgba(0, 0, 0, 255)
        val iInner = Rgba(128, 128, 128, 255)
        val iOuter = Rgba(128, 128, 128, 255)

        return GemUtilImages.asBitmap(
            image,
            width,
            height,
            aInner,
            aOuter,
            iInner,
            iOuter,
        )
    }

    private fun Pair<String, String>.toDisplayString(): String {
        return "$first $second"
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage(getString(R.string.tts_language_eng_usa))
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}
