/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxroutesimulation

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.examples.gpxroutesimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.gpxroutesimulation.databinding.DialogLayoutBinding
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private lateinit var binding: ActivityMainBinding

    private var turnImageSize: Int = 0

    private val playingListener = object : SoundPlayingListener() {}
    private val soundPreference = SoundPlayingPreferences()

    // Long.MAX_VALUE ensures the first real image UID never matches, so it is always rendered.
    private var lastTurnImageId: Long = Long.MAX_VALUE

    // Captured once at portrait orientation and reused as the base for all subsequent orientation
    // constraint adjustments, so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private var navigationStatus = ENavigationStatus.Running

    // Navigation service drives the GPX route simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Receives navigation events: started, instruction updates, destination reached,
     * status changes, errors, and sound cues.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    navRoute?.let { route -> mapView.presentRoute(route) }
                    enableGPSButton()
                    mapView.followPosition()
                }
            }
            applyCameraFocus()
            setNavigationPanelsVisible(isVisible = true)
        },
        onNavigationInstructionUpdated = { instr -> updateNavigationInstruction(instr) },
        onDestinationReached = { onNavigationEnded() },
        onNotifyStatusChange = { status ->
            navigationStatus = status
            refreshStatusMessage()
        },
        onNavigationError = { error -> onNavigationEnded(error) },
        onNavigationSound = { sound ->
            SdkCall.execute { SoundPlayingService.play(sound, playingListener, soundPreference) }
        },
        canPlayNavigationSound = true,
    )

    // Passed to startSimulationWithRoute as a required handle; no callbacks needed here.
    private val routingProgressListener = ProgressListener.create()

    // Calculates the route from GPX data; on success, immediately starts the simulation.
    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },
        onCompleted = { routes, errorCode, _ ->
            binding.progressBar.visibility = View.GONE
            when (errorCode) {
                GemError.NoError -> {
                    val route = routes[0]
                    SdkCall.execute {
                        val error = navigationService.startSimulationWithRoute(
                            route,
                            navigationListener,
                            routingProgressListener,
                        )
                        if (error != GemError.NoError) {
                            val message = GemError.getMessage(error, this@MainActivity)
                            Util.postOnMain {
                                showDialog(getString(R.string.route_simulation_error, message))
                            }
                        }
                    }
                }
                else -> {
                    val message = SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) } ?: ""
                    showDialog(getString(R.string.routing_error, message))
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SoundUtils.addTTSPlayerInitializationListener(this)
        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        // Release the SDK.
        GemSdk.release()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            // onSdkInitFailed runs in SDK context already — no SdkCall.runSynced needed here.
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
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                calculateRouteFromGPX()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
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

    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels
        // were GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val bottomVis = binding.bottomPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // In landscape the navigation panel occupies the left 40% of the screen.
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(id, ConstraintSet.END)
                }
            } else {
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
        }.applyTo(rootLayout)

        // Restore visibility since applyTo() resets it to the cloned (GONE) state.
        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis
    }

    // Shifts the camera focus point in landscape so the GPS arrow stays in the visible
    // (non-panel) map area on the right side of the screen.
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // In landscape the navigation panel occupies the left 40% of the screen, so shift the
            // camera focus point right (0.7) to keep the arrow in the visible map area.
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    // Adjusts the Magic Lane logo position to stay inside the visible map area, outside
    // navigation panels.
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
            val w = max(width, height)
            val h = min(width, height)

            val left = if (binding.topPanel.isVisible) binding.topPanel.right else insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            Rect(left, top, right, bottom)
        } else {
            val w = min(width, height)
            val h = max(width, height)

            val left = insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val top = if (binding.topPanel.isVisible) binding.topPanel.bottom else insets?.top ?: 0
            val bottom = if (binding.bottomPanel.isVisible) {
                binding.bottomPanel.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    private fun enableGPSButton() {
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.visibility = View.VISIBLE
                    setNavigationPanelsVisible(isVisible = false)
                }

                onEnterFollowingPosition = {
                    followGpsButton.visibility = View.GONE
                    val simulationIsActive = SdkCall.execute { navigationService.isSimulationActive() } ?: false
                    if (simulationIsActive) {
                        setNavigationPanelsVisible(isVisible = true)
                    }
                }

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

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            updateFocusViewport()
        } else {
            // Defer until the panels have been laid out so their bounds are available.
            binding.root.post { updateFocusViewport() }
        }
    }

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) } ?: ""
                showDialog(message)
            }
            setNavigationPanelsVisible(isVisible = false)
            disableGPSButton()
        }
        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
    }

    private fun updateNavigationInstruction(instruction: NavigationInstruction) {
        var instrText = ""
        var instrIcon: Bitmap? = null
        var hasSameTurnImage = false
        var instrDistance = ""
        var etaText = ""
        var rttText = ""
        var rtdText = ""

        SdkCall.execute {
            instrText = instruction.nextStreetName?.takeIf { it.isNotEmpty() }
                ?: instruction.nextTurnInstruction.orEmpty()
            instrIcon = getNextTurnImage(instruction, turnImageSize, turnImageSize) { isSame ->
                hasSameTurnImage = isSame
            }
            instrDistance = instruction.getDistanceInMeters()
            navRoute?.apply {
                etaText = getEta()
                rttText = getRtt()
                rtdText = getRtd()
            }
        }

        binding.apply {
            if (!hasSameTurnImage) {
                navIcon.setImageBitmap(instrIcon)
            }
            instructionDistance.text = instrDistance
            navInstruction.text = instrText
            eta.text = etaText
            rtt.text = rttText
            rtd.text = rtdText
        }
    }

    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        binding.turnContainer.isVisible = statusMessage.isEmpty()
        if (statusMessage.isNotEmpty()) {
            binding.navInstruction.text = statusMessage
        }
    }

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
                if (navigationService.isSimulationActive()) {
                    return getString(R.string.calculating)
                }
                return getString(R.string.getting_position)
            }
            else -> {}
        }
        return ""
    }

    private fun calculateRouteFromGPX() = SdkCall.execute {
        val gpxAssetsFilename = "gpx/test_route.gpx"

        val input = applicationContext.resources.assets.open(gpxAssetsFilename)
        val track = Path.produceWithGpx(input) ?: return@execute

        val error = routingService.calculateRoute(track, ERouteTransportMode.Bicycle)
        if (error != GemError.NoError) {
            val message = GemError.getMessage(error, this@MainActivity)
            Util.postOnMain {
                showDialog(getString(R.string.routing_error, message))
            }
        }
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

        return GemUtilImages.asBitmap(image, width, height, aInner, aOuter, iInner, iOuter)
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
    }

    /** Estimated time of arrival, formatted as HH:MM. */
    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val etaNumber = (this.getTimeDistance(true)?.totalTime ?: 0) + GemUtil.getTrafficEventsDelay(this, true)
        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    /** Remaining travel time, including traffic delay. */
    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            (this.getTimeDistance(true)?.totalTime ?: 0) + GemUtil.getTrafficEventsDelay(this, true),
        ).let { pair -> pair.first + " " + pair.second }
    }

    /** Remaining travel distance. */
    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
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

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}
