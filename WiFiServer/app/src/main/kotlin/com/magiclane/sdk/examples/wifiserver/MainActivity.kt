/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wifiserver

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
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
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.examples.wifiserver.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.wifiserver.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ETurnEvent
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding
    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var turnImageSize: Int = 0
    private var padding: Int = 0

    // Captured once at portrait orientation; all subsequent constraint updates are applied on top
    // of this baseline so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    /** Navigation service used to start and control the route simulation. */
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    // ---- WiFi ------------------------------------------------------------------

    /**
     * Snapshot of the last data sent to clients. Also read from the server's background threads
     * (to bring newly connected clients up to date), hence @Volatile. The turn event array is
     * replaced wholesale, never mutated in place, so a stale read is always a consistent message.
     */
    @Volatile
    private var turnEvent = IntArray(4)

    @Volatile
    private var lastInstruction = ""

    @Volatile
    private var lastDistance = ""

    @Volatile
    private var lastRemainingTime = -1

    @Volatile
    private var lastRemainingDistance = -1

    @Volatile
    private var lastSpeedMps = -1.0

    /**
     * TCP server advertised via DNS-SD; it pushes the navigation data to connected
     * WiFiClient apps (see [NavProtocol] for the message format).
     */
    private val navWifiServer by lazy {
        NavWifiServer(
            applicationContext,
            snapshotProvider = {
                buildList {
                    add(turnEvent.toTurnMessage())
                    add(NavProtocol.instructionMessage(lastInstruction))
                    add(NavProtocol.distanceMessage(lastDistance))
                    if ((lastRemainingTime >= 0) && (lastRemainingDistance >= 0)) {
                        add(NavProtocol.routeMessage(lastRemainingTime, lastRemainingDistance))
                    }
                    if (lastSpeedMps >= 0) {
                        add(NavProtocol.speedMessage(lastSpeedMps))
                    }
                }
            },
            onClientCountChanged = { count ->
                runOnUiThread { updateToolbarSubtitle(count) }
            },
        )
    }

    private fun IntArray.toTurnMessage() = NavProtocol.turnMessage(this[0], this[1], this[2], this[3])

    private fun updateToolbarSubtitle(clientCount: Int = 0) {
        binding.toolbar.subtitle = if (navWifiServer.isRunning) {
            getString(R.string.server_status, navWifiServer.localPort, clientCount)
        } else {
            null
        }
    }

    private fun startWifiServer() {
        try {
            navWifiServer.start()
            updateToolbarSubtitle()
        } catch (e: IOException) {
            showDialog(getString(R.string.server_start_error, e.localizedMessage ?: e.toString()))
        }
    }

    // ---- Navigation ------------------------------------------------------------

    /**
     * Receives navigation events: instruction updates, destination reached, and errors.
     * Updates both the on-screen panels and the connected WiFi clients.
     */
    private val navigationListener: NavigationListener =
        NavigationListener.create(
            onNavigationStarted = {
                SdkCall.execute {
                    binding.gemSurface.mapView?.let { mapView ->
                        mapView.preferences?.enableCursor = false
                        navRoute?.let { route -> mapView.presentRoute(route) }
                        enableGPSButton()
                        mapView.followPosition()
                    }
                }
                applyCameraFocus()
                setNavigationPanelsVisible(isVisible = true)
            },
            onNavigationInstructionUpdated = { instr ->
                var instrText = ""
                var instrDistance = ""
                var etaText = ""
                var rttText = ""
                var rtdText = ""
                var remainingTravelTime = -1
                var remainingTravelDistance = -1
                var speed = -1.0

                SdkCall.execute {
                    // Collect instruction text for the top panel.
                    instrText = instr.nextStreetName ?: ""
                    if (instrText.isEmpty()) {
                        instrText = instr.nextTurnInstruction ?: ""
                    }
                    instrDistance = instr.getDistanceInMeters()

                    // Collect route summary values for the bottom panel.
                    navRoute?.apply {
                        etaText = getEta()
                        rttText = getRtt()
                        rtdText = getRtd()
                    }

                    // Collect the raw values sent to the WiFi clients, which format them
                    // on their side (like the BLEServer2 / BLEClient2 example pair does).
                    instr.remainingTravelTimeDistance?.let {
                        remainingTravelTime = it.totalTime
                        remainingTravelDistance = it.totalDistance
                    }

                    PositionService.improvedPosition?.let {
                        if (it.isValid()) {
                            speed = it.speed
                        }
                    }
                }

                // Check whether the turn icon changed and notify WiFi clients if so.
                val sameTurnImage = TSameImage()
                val newTurnImage = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
                if (!sameTurnImage.value) {
                    val newTurnEvent = IntArray(4)

                    SdkCall.execute {
                        instr.nextTurnDetails?.let {
                            newTurnEvent[0] = it.event.value

                            if (it.event.value == ETurnEvent.IntoRoundabout.value) {
                                it.abstractGeometry?.let { abstractGeometry ->
                                    newTurnEvent[3] = abstractGeometry.driveSide.value
                                    abstractGeometry.items?.let { items ->
                                        if (items.size > 1) {
                                            newTurnEvent[1] = items.last().beginSlot
                                            newTurnEvent[2] = items.last().endSlot
                                        }
                                    }
                                }
                            }
                        }
                    }

                    turnEvent = newTurnEvent
                    binding.navIcon.setImageBitmap(newTurnImage)
                    navWifiServer.broadcast(newTurnEvent.toTurnMessage())
                }

                if (instrText != binding.navInstruction.text) {
                    binding.navInstruction.text = instrText
                    lastInstruction = instrText
                    navWifiServer.broadcast(NavProtocol.instructionMessage(instrText))
                }

                if (instrDistance != binding.instrDistance.text) {
                    binding.instrDistance.text = instrDistance
                    lastDistance = instrDistance
                    navWifiServer.broadcast(NavProtocol.distanceMessage(instrDistance))
                }

                binding.eta.text = etaText
                binding.rtt.text = rttText
                binding.rtd.text = rtdText

                if ((remainingTravelTime >= 0) && (remainingTravelDistance >= 0)) {
                    lastRemainingTime = remainingTravelTime
                    lastRemainingDistance = remainingTravelDistance
                    navWifiServer.broadcast(
                        NavProtocol.routeMessage(remainingTravelTime, remainingTravelDistance),
                    )
                }

                if (speed >= 0) {
                    lastSpeedMps = speed
                    updateSpeedView(speed)
                    navWifiServer.broadcast(NavProtocol.speedMessage(speed))
                }
            },
            onDestinationReached = { onNavigationEnded() },
            onNavigationError = { error -> onNavigationEnded(error) },
        )

    /** Shows the current speed in km/h, like the BLEClient2 example's speed box. */
    private fun updateSpeedView(speedMps: Double) {
        val speedInKmH = (speedMps * 3.6 + 0.5).toInt()
        binding.speed.isVisible = true
        binding.speed.text = getString(R.string.speed_format, speedInKmH)
    }

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if (errorCode != GemError.NoError) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                if (message?.isEmpty() == false) {
                    showDialog(message)
                }
            }
            setNavigationPanelsVisible(isVisible = false)
            disableGPSButton()
            binding.speed.isVisible = false
            lastRemainingTime = -1
            lastRemainingDistance = -1
            lastSpeedMps = -1.0

            // Reset the turn event and notify connected WiFi clients.
            turnEvent = IntArray(4)
            navWifiServer.broadcast(turnEvent.toTurnMessage())
        }

        SdkCall.execute { binding.gemSurface.mapView?.hideRoutes() }
    }

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        bSameImage: TSameImage,
    ): Bitmap? {
        return SdkCall.execute {
            if (!navInstr.hasNextTurnInfo()) return@execute null
            if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
                bSameImage.value = true
                return@execute null
            }

            val image = navInstr.nextTurnDetails?.abstractGeometryImage
            if (image != null) lastTurnImageId = image.uid

            // Active turn icon: white fill with black outline; inactive: grey fill and outline.
            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            GemUtilImages.asBitmap(image, width, height, aInner, aOuter, iInner, iOuter)
        }
    }

    /** Listens for routing progress to show/hide the loading indicator. */
    private val routingProgressListener = ProgressListener.create(
        onStarted = { binding.progressBar.visibility = View.VISIBLE },
        onCompleted = { errorCode, _ ->
            binding.progressBar.visibility = View.GONE
            if (errorCode != GemError.NoError) {
                showDialog(
                    getString(
                        R.string.start_simulation_error,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) },
                    ),
                )
            }
        },
        postOnMain = true,
    )

    // ---- Lifecycle -----------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        padding = resources.getDimension(R.dimen.big_padding).toInt()

        // Keep the screen on while navigation is running.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Snapshot portrait constraints so landscape adjustments always start from a clean baseline.
        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        // Re-apply orientation layout when window insets first arrive so landscape cold-starts
        // and orientation changes both get the correct left / bottom system bar offsets.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyOrientationLayout()
            insets
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        // Unlike Bluetooth, a local TCP server needs no runtime permissions: start right away.
        startWifiServer()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()
        navWifiServer.stop()

        // Release the SDK.
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        GemSdk.release()
        exitProcess(0)
    }

    // ---- SDK listener registration -------------------------------------------

    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        // Update the Magic Lane logo viewport whenever the map surface is created or resized.
        binding.gemSurface.onDefaultMapViewCreated = { updateFocusViewport() }
        binding.gemSurface.onSurfaceChanged = { _, _ -> updateFocusViewport() }

        // Delay simulation start until the worldwide road map is fully ready;
        // the callback is cleared immediately after firing to prevent repeat invocations.
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
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // ---- Camera / viewport ---------------------------------------------------

    /** Shifts the camera focus point to keep the GPS arrow in the visible map area in landscape. */
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // In landscape the navigation panel occupies the left 40 % of the screen, so shift
            // the camera focus point right (0.7) to keep the arrow in the visible map area.
            binding.gemSurface.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    /** Updates the Magic Lane logo viewport to avoid overlapping with navigation panels. */
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurface.mapView?.preferences?.focusViewport = getFocusViewport()
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

            // In landscape the panels are on the left, so exclude that area from the logo viewport.
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
            // In portrait, account for the toolbar even when the nav panel is hidden.
            val top = if (binding.topPanel.isVisible) binding.topPanel.bottom else binding.toolbar.bottom
            val bottom = if (binding.bottomPanel.isVisible) {
                binding.bottomPanel.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    // ---- Layout orientation --------------------------------------------------

    /**
     * Adjusts panel constraint widths and horizontal positions for portrait/landscape.
     * In landscape, panels occupy the left 40 % of the screen so the map remains visible
     * on the right. Restores live visibility state after ConstraintSet.applyTo(), which
     * would otherwise reset all views to their cloned (GONE) visibility.
     */
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels
        // were GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val bottomVis = binding.bottomPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility
        val speedVis = binding.speed.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)

        // Read current window insets to account for system bars and display cutouts.
        // All panel insets are applied here (not via binding adapters) to keep ConstraintSet
        // as the sole owner of panel margins and avoid margin resets on orientation change.
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val sysLeft = insets?.left ?: 0
        val sysBottom = insets?.bottom ?: 0

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // Panels occupy the left 40 % of the screen; offset by the left system bar / cutout.
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(
                        id,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                        sysLeft + panelMargin,
                    )
                    clear(id, ConstraintSet.END)
                }
                // In landscape the bottom panel only covers the left 40 %, so the FAB (right-aligned)
                // must be anchored to the screen bottom rather than the panel top.
                connect(
                    R.id.follow_gps_button,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    padding,
                )
            } else {
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    // Restore MATCH_CONSTRAINT width explicitly so it is not left at the
                    // absolute pixel value that was set in the landscape branch.
                    constrainWidth(id, ConstraintSet.MATCH_CONSTRAINT)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
                // Restore the FAB to sit just above the speed box (which itself sits on the
                // bottom panel's top-right corner), so the two never overlap.
                connect(
                    R.id.follow_gps_button,
                    ConstraintSet.BOTTOM,
                    R.id.speed,
                    ConstraintSet.TOP,
                    padding,
                )
            }
            // Bottom panel always needs clearance for the bottom system bar / gesture indicator.
            connect(
                R.id.bottom_panel,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                panelMargin + sysBottom,
            )
        }.applyTo(rootLayout)

        // Restore live visibility after ConstraintSet.applyTo() overwrote it.
        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis
        binding.speed.visibility = speedVis
    }

    // ---- Navigation panel visibility -----------------------------------------

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            updateFocusViewport()
        } else {
            // Post so the viewport update runs after the panels are measured and laid out.
            binding.root.post { updateFocusViewport() }
        }
    }

    // ---- GPS follow button ---------------------------------------------------

    private fun enableGPSButton() {
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = { binding.followGpsButton.visibility = View.VISIBLE }
            onEnterFollowingPosition = { binding.followGpsButton.visibility = View.GONE }
            binding.followGpsButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    private fun disableGPSButton() {
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = null
            onEnterFollowingPosition = null
            binding.followGpsButton.setOnClickListener(null)
            binding.followGpsButton.isVisible = false
        }
    }

    // ---- Navigation data helpers ---------------------------------------------

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
    }

    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0
        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).let { pair -> pair.first + " " + pair.second }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
    }

    // ---- Simulation ----------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Amsterdam", 52.3585050, 4.8803423),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        val errorCode = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (errorCode != GemError.NoError) {
            runOnUiThread {
                showDialog(
                    getString(
                        R.string.start_simulation_error,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                    ),
                )
            }
        }
    }

    // ---- Dialog --------------------------------------------------------------

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
}
