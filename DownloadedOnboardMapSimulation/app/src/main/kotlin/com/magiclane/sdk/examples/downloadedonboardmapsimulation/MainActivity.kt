/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.downloadedonboardmapsimulation

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.examples.downloadedonboardmapsimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.downloadedonboardmapsimulation.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sound.SoundUtils
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    // Holds all data needed to refresh the navigation UI panels from a single SDK read pass.
    private data class InstructionUiData(
        val text: String = "",
        val icon: Bitmap? = null,
        val distance: String = "",
        val eta: String = "",
        val rtt: String = "",
        val rtd: String = "",
    )

    private lateinit var binding: ActivityMainBinding

    // Captured once at portrait orientation and reused as the base for all subsequent orientation
    // constraint adjustments, so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private val playingListener = object : SoundPlayingListener() {}
    private val soundPreference = SoundPlayingPreferences()

    // Navigation service used to start and manage the route simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define a navigation listener that will receive notifications from the navigation service.
     * We use onNavigationStarted, onNavigationInstructionUpdated, onDestinationReached,
     * onNavigationError, and onNavigationSound.
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
        onNavigationError = { error -> onNavigationEnded(error) },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true,
    )

    // Tracks routing progress; drives the progress bar and the status text.
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
        onStatusChanged = { status ->
            if (status == ERouteStatus.WaitingInternetConnection.value) {
                showDialog(getString(R.string.valid_token_required), getString(R.string.info)) {
                    finish()
                }
            }
        },
        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SoundUtils.addTTSPlayerInitializationListener(this)
        EspressoIdlingResource.init(this)

        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        // When starting directly in landscape, also fix the panel LayoutParams margins so they
        // survive ConstraintSet re-application on subsequent orientation changes.
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

        registerSdkListeners()

        EspressoIdlingResource.increment()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        SoundUtils.removeTTSPlayerInitializationListener(this)
        // Release the SDK.
        GemSdk.release()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
            startSimulation()
        }

        // Recompute the logo viewport whenever the surface dimensions change (e.g. rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
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

    // Switches between portrait (full-width panels) and landscape (left 40 % panels) layouts.
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
                // Constrain nav panels to the left 40 % so the map stays visible on the right.
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

        // Restore visibility state overwritten by ConstraintSet.applyTo().
        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis
    }

    // Adjusts the GPS arrow's on-map position to stay inside the visible map area.
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // In landscape the nav panel occupies the left 40 % of the screen, so shift the
            // camera focus point right (0.7) to keep the arrow in the visible map area.
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    // Repositions the Magic Lane logo so it never overlaps the navigation panels.
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

            // Exclude the nav panel on the left so the logo stays in the visible map area.
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
            // When the top panel is visible its bottom is the correct upper boundary.
            // Otherwise, fall back to the top inset so the logo clears the status bar.
            val top = when {
                binding.topPanel.isVisible -> binding.topPanel.bottom
                else -> insets?.top ?: 0
            }
            val bottom = when {
                binding.bottomPanel.isVisible -> binding.bottomPanel.top.coerceAtLeast(top)
                else -> (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            updateFocusViewport()
        } else {
            // Post so the panels have been laid out before we read their dimensions.
            binding.root.post { updateFocusViewport() }
        }
    }

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                if (message?.isNotEmpty() == true) {
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

    private fun updateNavigationInstruction(instr: NavigationInstruction) {
        val data = SdkCall.execute {
            InstructionUiData(
                text = instr.nextStreetName?.takeIf { it.isNotEmpty() } ?: instr.nextTurnInstruction.orEmpty(),
                icon = instr.nextTurnImage?.asBitmap(100, 100),
                distance = instr.getDistanceInMeters(),
                eta = navRoute?.getEta() ?: "",
                rtt = navRoute?.getRtt() ?: "",
                rtd = navRoute?.getRtd() ?: "",
            )
        } ?: return

        binding.apply {
            navInstruction.text = data.text
            navInstructionIcon.setImageBitmap(data.icon)
            navInstructionDistance.text = data.distance
            eta.text = data.eta
            rtt.text = data.rtt
            rtd.text = data.rtd
        }
        EspressoIdlingResource.decrement()
    }

    private fun enableGPSButton() {
        binding.apply {
            gemSurfaceView.mapView?.apply {
                // Panning away from the GPS position hides the nav panels to expose the map.
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                    setNavigationPanelsVisible(isVisible = false)
                }

                // Re-entering GPS follow restores the nav panels if simulation is still running.
                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false
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

    private fun startSimulation() = SdkCall.runSynced {
        val waypoints = arrayListOf(
            Landmark("Luxembourg", 49.61588784436375, 6.135843869736401),
            Landmark("Mersch", 49.74785494642988, 6.103323786692679),
        )
        val error = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (error != GemError.NoError) {
            runOnUiThread {
                showDialog(
                    getString(
                        R.string.failed_to_start_simulation,
                        SdkCall.runSynced { GemError.getMessage(error, this) },
                    ),
                )
            }
        }
    }

    private fun showDialog(text: String, title: String = getString(R.string.error), onDismiss: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
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

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
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

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage(getString(R.string.tts_language_eng_usa))
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}

//region TESTING
object EspressoIdlingResource {
    private var resource: CountingIdlingResource? = null

    fun init(context: android.content.Context) {
        if (resource == null) {
            resource = CountingIdlingResource(context.getString(R.string.idling_resource_name))
        }
    }

    val espressoIdlingResource: CountingIdlingResource
        get() = checkNotNull(resource)

    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
