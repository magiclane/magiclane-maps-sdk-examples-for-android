/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routenavigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textview.MaterialTextView
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
import com.magiclane.sdk.core.TimezoneResult
import com.magiclane.sdk.core.TimezoneService
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.examples.routenavigation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routenavigation.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RouteTrafficEvent
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private data class NavigationUiData(
        val instructionText: String = "",
        val instructionIcon: Bitmap? = null,
        val hasSameTurnImage: Boolean = false,
        val instructionDistance: String = "",
        val etaText: String = "",
        val rttText: String = "",
        val rttColor: Int = Color.WHITE,
        val rtdText: String = "",
        val signPostBitmap: Bitmap? = null,
        val roadCodeBitmap: Bitmap? = null,
        val lanesBitmap: Bitmap? = null,
    )

    /** Where the lane guidance is displayed. */
    private enum class LaneInfoPlacement {
        /** Along the bottom side of the top navigation instruction panel. */
        TOP_PANEL,

        /** Standalone panel near the ETA panel: above it in portrait, right of it in landscape. */
        NEAR_ETA_PANEL,
    }

    private lateinit var binding: ActivityMainBinding

    // Permission handling
    private var shouldCheckLocationPermissionOnResume = false

    private var turnPadding: Int = 0
    private var turnImageSize: Int = 0
    private var navigationPanelPadding: Int = 0
    private var signPostImageSize: Int = 0
    private var topPanelWidth: Int = 0
    private var navigationImageSize: Int = 0
    private var lanePanelHeight: Int = 0
    private var lanePanelPadding: Int = 0

    // Where the lane guidance is displayed; pick the placement here at compile time.
    private val laneInfoPlacement = LaneInfoPlacement.NEAR_ETA_PANEL

    // Whether the standalone lane panel is currently shown; drives the landscape camera focus.
    // Volatile: written on the UI thread, read from SDK-thread callbacks.
    @Volatile
    private var laneInfoVisible = false

    // Pixel width available to render the lane image. Recomputed per orientation because the lane
    // panel spans the full width in portrait but only the gap to the right of the panels in landscape.
    private var laneAvailableWidth = 0

    // Latest system bar / display cutout insets. Updated from the window insets listener rather than
    // read synchronously, because the insets are not available immediately and change on rotation
    // (a synchronous read during onConfigurationChanged still returns the pre-rotation values).
    private var barInsets: Insets = Insets.NONE

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    // Long.MAX_VALUE ensures the first real image UID never matches, so it is always rendered.
    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var lastTrafficImageId: Long = Long.MAX_VALUE

    private val trafficPanelBackgroundColor = Color.rgb(255, 175, 63)
    private var sameTrafficImage = false
    private var trafficBmp: Bitmap? = null
    private var endOfSectionBmp: Bitmap? = null
    private var distToTrafficEvent = 0
    private var remainingDistInsideTrafficEvent = 0
    private var insideTrafficEvent = false
    private var trafficEventDescriptionText = ""
    private var distanceToTrafficPrefixText = ""
    private var trafficDelayTimeText = ""
    private var trafficDelayTimeUnitText = ""
    private var trafficDelayDistanceText = ""
    private var trafficDelayDistanceUnitText = ""
    private var distanceToTrafficText = ""
    private var distanceToTrafficUnitText = ""

    // Captured once at portrait orientation and reused as the base for all subsequent orientation
    // constraint adjustments, so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private var navigationStatus = ENavigationStatus.Running

    private var firstTime = true

    // Modern permissions launcher
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            showDialog(getString(R.string.location_permission_required)) {
                finish()
            }
            return@registerForActivityResult
        }

        onLocationPermissionsGranted()
    }

    // Define a navigation service from which we will start the navigation.
    private val navigationService = NavigationService()

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

                    mapView.followPosition()
                }
            }
            applyCameraFocus()
            endOfSectionBmp = ContextCompat.getDrawable(this@MainActivity, R.drawable.end_of_traffic_section)
                ?.toBitmap(navigationImageSize, navigationImageSize)
            binding.apply {
                setNavigationPanelsVisible(isVisible = true)
            }
        },
        onNavigationInstructionUpdated = { instr -> updateNavigationInstruction(instr) },
        onDestinationReached = { onNavigationEnded() },
        onNotifyStatusChange = { status ->
            navigationStatus = status
            refreshStatusMessage()
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
            if (firstTime) {
                binding.progressBar.visibility = View.VISIBLE
                firstTime = false
            }
        },

        onCompleted = { _, _ ->
            binding.progressBar.visibility = View.GONE
        },

        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SoundUtils.addTTSPlayerInitializationListener(this)

        turnPadding = resources.getDimension(R.dimen.nav_top_panel_turn_margin).toInt()
        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        navigationImageSize = resources.getDimension(R.dimen.navigation_image_size).toInt()
        navigationPanelPadding = resources.getDimension(R.dimen.nav_top_panel_padding).toInt()
        signPostImageSize = resources.getDimension(R.dimen.sign_post_image_size).toInt()
        lanePanelHeight = resources.getDimension(R.dimen.lane_panel_height).toInt()
        lanePanelPadding = resources.getDimension(R.dimen.lane_panel_padding).toInt()

        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

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
            setPanelHorizontalMargins(binding.trafficPanel)
        }

        // Keep the lane panel's inset-dependent margins correct: the insets arrive after layout and
        // change on rotation, so we update them here whenever the system (re)dispatches insets.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            barInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            applyLaneLandscapeInsets()
            // The camera focus X depends on the insets (a left bar/cutout offsets the panels), and
            // the insets read during onConfigurationChanged are stale — recompute with fresh ones.
            applyCameraFocus()
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
    }

    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels were
        // GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val laneVis = binding.laneContainer.visibility
        val bottomVis = binding.bottomPanel.visibility
        val trafficVis = binding.trafficPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelWidth = (resources.displayMetrics.widthPixels * LANDSCAPE_PANEL_WIDTH_FRACTION).toInt()
                topPanelWidth = panelWidth

                for (id in intArrayOf(R.id.top_panel, R.id.traffic_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(id, ConstraintSet.END)
                }

                // The standalone lane panel moves into the free map area: the region to the right
                // of the docked panels and inside the system insets, minus a small margin on each
                // side. It is pinned to the bottom, aligned with the bottom panel; horizontally it
                // centers on the followed position when it fits there (see
                // updateLandscapeLanePosition()), starting from a free-space-centered bias.
                clear(R.id.lane_container, ConstraintSet.TOP)
                clear(R.id.lane_container, ConstraintSet.START)
                clear(R.id.lane_container, ConstraintSet.END)
                connect(R.id.lane_container, ConstraintSet.START, R.id.top_panel, ConstraintSet.END, bigPadding)
                connect(
                    R.id.lane_container,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END,
                    barInsets.right + bigPadding,
                )
                connect(
                    R.id.lane_container,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    barInsets.bottom + bigPadding,
                )
                setHorizontalBias(R.id.lane_container, 0.5f)
            } else {
                topPanelWidth = resources.displayMetrics.widthPixels - 2 * navigationPanelPadding

                for (id in intArrayOf(R.id.top_panel, R.id.traffic_panel, R.id.bottom_panel)) {
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
        }.applyTo(rootLayout)

        binding.topPanel.visibility = topVis
        binding.laneContainer.visibility = laneVis
        binding.bottomPanel.visibility = bottomVis
        binding.trafficPanel.visibility = trafficVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis

        updateLaneAvailableWidth(isLandscape, panelMargin, barInsets.right)

        // Recompute the GPS-centered bias right away: the 0.5 bias set above is only a fallback,
        // and waiting for the next insets/instruction pass would let the first post-rotation
        // frame render off-center relative to the followed position.
        if (laneInfoVisible) {
            updateLandscapeLanePosition(binding.laneImage.layoutParams.width + 2 * lanePanelPadding)
        }
    }

    /** Recomputes the pixel width used to render the lane image for the current orientation. */
    private fun updateLaneAvailableWidth(isLandscape: Boolean, panelMargin: Int, rightInset: Int) {
        val screenWidth = resources.displayMetrics.widthPixels
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        val laneInnerPadding = 2 * lanePanelPadding

        laneAvailableWidth = when {
            // Inside the top panel the lane image spans the panel width minus its padding.
            laneInfoPlacement == LaneInfoPlacement.TOP_PANEL ->
                (topPanelWidth - laneInnerPadding).coerceAtLeast(turnImageSize)

            // Width of the free space (area to the right of the docked panels, inside the right
            // inset, minus the small side margins) reduced by the lane image padding, so the panel
            // always fits in it.
            isLandscape ->
                (screenWidth - panelMargin - topPanelWidth - rightInset - 2 * bigPadding - laneInnerPadding)
                    .coerceAtLeast(turnImageSize)

            else -> screenWidth - 2 * bigPadding - laneInnerPadding
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
            params.marginEnd = barInsets.right + bigPadding
            params.bottomMargin = bigPadding + barInsets.bottom
            binding.laneContainer.layoutParams = params
        }

        updateLaneAvailableWidth(isLandscape, panelMargin, barInsets.right)

        if (laneInfoVisible) {
            updateLandscapeLanePosition(binding.laneImage.layoutParams.width + 2 * lanePanelPadding)
        }
    }

    /**
     * In landscape, centers the lane panel horizontally on the followed position (the camera focus
     * X fraction of the full screen width) whenever it fits there without crossing the free space
     * bounds; otherwise it falls back to centering inside the free space. No-op in portrait, where
     * the lane panel stays centered on the screen.
     */
    private fun updateLandscapeLanePosition(laneWidthPx: Int) {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return

        val screenWidth = resources.displayMetrics.widthPixels
        val params = binding.laneContainer.layoutParams as ConstraintLayout.LayoutParams

        // Analytic right edge of the docked panels, matching landscapeCameraFocusX(). Reading
        // binding.topPanel.right here would use the pre-rotation layout for one frame right after
        // an orientation change, briefly misplacing the panel relative to the followed position.
        val panelRight = barInsets.left + (screenWidth * LANDSCAPE_PANEL_WIDTH_FRACTION).toInt()
        val freeLeft = panelRight + params.marginStart
        val freeRight = screenWidth - params.marginEnd
        val space = freeRight - freeLeft - laneWidthPx

        val gpsCenteredLeft = (screenWidth * landscapeCameraFocusX()).toInt() - laneWidthPx / 2
        params.horizontalBias =
            if (space > 0 && gpsCenteredLeft >= freeLeft && gpsCenteredLeft + laneWidthPx <= freeRight) {
                (gpsCenteredLeft - freeLeft).toFloat() / space
            } else {
                0.5f
            }
        binding.laneContainer.layoutParams = params
    }

    /**
     * Horizontal camera focus in landscape: the middle of the free map area right of the docked
     * panels, as a fraction of the full screen width. Only the left inset matters: a left system
     * bar / cutout pushes the panels right, while on the right the map keeps drawing edge-to-edge
     * under the bar, so nothing is offset there and the free area ends at the screen edge.
     */
    private fun landscapeCameraFocusX(): Float {
        val screenWidth = resources.displayMetrics.widthPixels
        val panelRight = barInsets.left + (screenWidth * LANDSCAPE_PANEL_WIDTH_FRACTION).toInt()
        return (panelRight + screenWidth) / 2f / screenWidth
    }

    // this adjusts GPS arrow position on map
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hasLanePanel = laneInfoVisible
        val focusX = landscapeCameraFocusX()
        SdkCall.execute {
            // In landscape the navigation panels cover the left side of the screen, so shift the
            // camera focus point right to the middle of the remaining map area; while the bottom
            // lane panel is present the arrow is also elevated (0.63) so the panel cannot cover it.
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                when {
                    !isLandscape -> XyF(0.5f, 0.75f)
                    hasLanePanel -> XyF(focusX, 0.63f)
                    else -> XyF(focusX, 0.75f)
                }
        }
    }

    /** Shows/hides the standalone lane panel and keeps the camera focus in sync with its presence. */
    private fun setLanePanelVisible(isVisible: Boolean) {
        if (binding.laneContainer.isVisible == isVisible) return
        binding.laneContainer.isVisible = isVisible
        laneInfoVisible = isVisible
        applyCameraFocus()
    }

    /**
     * Shows/hides the lane strip inside the top panel and refreshes the focus viewport, since the
     * panel grows/shrinks with it. Unlike the standalone panel, this placement never covers the
     * followed position, so the camera focus is left alone.
     */
    private fun setTopPanelLaneVisible(isVisible: Boolean) {
        if (binding.topLaneImage.isVisible == isVisible) return
        binding.topLaneImage.isVisible = isVisible
        binding.root.post { updateFocusViewport() }
    }

    // this adjusts Magic Lane logo position on map
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    fun getFocusViewport(): Rect {
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
            val top = when {
                binding.topPanel.isVisible && binding.trafficPanel.isVisible -> binding.trafficPanel.bottom
                binding.topPanel.isVisible -> binding.topPanel.bottom
                else -> insets?.top ?: 0
            }
            val bottom = if (binding.bottomPanel.isVisible) {
                binding.bottomPanel.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
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

    override fun onResume() {
        super.onResume()
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions()
            } else {
                showDialog(getString(R.string.location_services_required)) {
                    finish()
                }
            }
        }
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
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Delay navigation start until the worldwide road map is fully downloaded and up to date;
        // the callback is cleared immediately after firing to avoid repeat invocations.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }

                if (checkLocationStatus()) {
                    requestPermissions()
                }
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

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    private fun checkLocationStatus(): Boolean {
        if (isLocationEnabled()) return true

        showLocationDialog(
            message = getString(R.string.location_disabled),
            settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        )
        return false
    }

    private fun showLocationDialog(message: String, settingsIntent: Intent) {
        showBottomSheetDialog(
            title = getString(R.string.location_status),
            message = message,
            buttonText = getString(R.string.open_settings),
            onButtonClick = { dialog ->
                dialog.dismiss()
                startActivity(settingsIntent)
                shouldCheckLocationPermissionOnResume = true
            },
        )
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            followGpsButton.visibility = View.VISIBLE

            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.visibility = View.VISIBLE
                    setNavigationPanelsVisible(isVisible = false)
                }

                onEnterFollowingPosition = {
                    followGpsButton.visibility = View.GONE

                    val navigationIsActive = SdkCall.execute {
                        navigationService.isNavigationActive(
                            navigationListener,
                        )
                    } ?: false
                    if (navigationIsActive) {
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

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: ""
                if (message.isNotEmpty()) {
                    showDialog(message)
                }
            }
            setNavigationPanelsVisible(isVisible = false)
        }

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
    }

    private fun updateNavigationInstruction(instruction: NavigationInstruction) {
        val distanceToNextTurnWidth = getTextViewWidth(binding.instrDistance, "9000 km")
        val availableWidth = topPanelWidth - max(turnImageSize, distanceToNextTurnWidth) - 3 * turnPadding

        val navData = SdkCall.execute { collectNavigationUiData(instruction, availableWidth) } ?: return

        binding.apply {
            if (!navData.hasSameTurnImage) {
                navIcon.setImageBitmap(navData.instructionIcon)
            }
            instrDistance.text = navData.instructionDistance
            eta.text = navData.etaText
            rtt.text = navData.rttText
            rtt.setTextColor(navData.rttColor)
            rtd.text = navData.rtdText

            val hasSignPost = navData.signPostBitmap != null
            val hasRoadCode = navData.roadCodeBitmap != null

            signPost.isVisible = hasSignPost
            navData.signPostBitmap?.let { bmp ->
                signPost.setImageBitmap(bmp)
                signPost.layoutParams.width = bmp.width
                signPost.layoutParams.height = bmp.height
            }

            roadCode.isVisible = hasRoadCode
            navData.roadCodeBitmap?.let { bmp ->
                roadCode.setImageBitmap(bmp)
                if (bmp.height > 0) {
                    val ratio = bmp.width.toFloat() / bmp.height
                    roadCode.layoutParams.width = (roadCode.layoutParams.height * ratio).toInt()
                }
            }

            navInstruction.isVisible = !hasSignPost
            if (!hasSignPost) {
                navInstruction.text = navData.instructionText
                navInstruction.maxLines = if (hasRoadCode) 1 else 3
            }

            // Only show the lane info while the turn information (not a status message) is visible.
            val laneVisible =
                (navData.lanesBitmap != null) && topPanel.isVisible && turnContainer.isVisible
            if (laneInfoPlacement == LaneInfoPlacement.TOP_PANEL) {
                setTopPanelLaneVisible(laneVisible)
            } else {
                setLanePanelVisible(laneVisible)
            }
            navData.lanesBitmap?.let { bitmap ->
                val target =
                    if (laneInfoPlacement == LaneInfoPlacement.TOP_PANEL) topLaneImage else laneImage
                target.setImageBitmap(bitmap)
                target.layoutParams.width = bitmap.width
                target.layoutParams.height = bitmap.height
                if (laneInfoPlacement == LaneInfoPlacement.NEAR_ETA_PANEL) {
                    updateLandscapeLanePosition(bitmap.width + 2 * lanePanelPadding)
                }
            }
        }

        updateTrafficPanel(instruction)
    }

    private fun collectNavigationUiData(instruction: NavigationInstruction, availableWidth: Int): NavigationUiData {
        val instructionText =
            instruction.nextStreetName?.takeIf { it.isNotEmpty() } ?: instruction.nextTurnInstruction.orEmpty()

        var hasSameTurnImage = false
        val instructionIcon = getNextTurnImage(instruction, turnImageSize, turnImageSize) { isSame ->
            hasSameTurnImage = isSame
        }

        val trafficDelayInMinutes = navRoute?.let {
            GemUtil.getTrafficEventsDelay(it, true) / 60
        } ?: 0
        val (etaText, rttText, rtdText) = navRoute?.let {
            Triple(it.getEta(), it.getRtt(), it.getRtd())
        } ?: Triple("", "", "")

        val signPostBitmap = getSignpostImage(instruction, availableWidth, signPostImageSize)
        val roadCodeBitmap = if (signPostBitmap == null) {
            getRoadCodeImage(instruction, availableWidth, navigationImageSize)
        } else {
            null
        }

        // Trimmed to the visible arrows: the renderer keeps the diagram's aspect inside the
        // requested canvas, so an untrimmed bitmap carries invisible slack that would misalign
        // the diagram inside the lane panel.
        val lanesBitmap = instruction.laneImage?.asBitmap(
            laneAvailableWidth,
            lanePanelHeight,
            activeColor = Rgba.white(),
        )?.trimmedToContent()

        return NavigationUiData(
            instructionText = instructionText,
            instructionIcon = instructionIcon,
            hasSameTurnImage = hasSameTurnImage,
            instructionDistance = instruction.getDistanceInMeters(),
            etaText = etaText,
            rttText = rttText,
            rttColor = getTrafficColor(trafficDelayInMinutes),
            rtdText = rtdText,
            signPostBitmap = signPostBitmap,
            roadCodeBitmap = roadCodeBitmap,
            lanesBitmap = lanesBitmap,
        )
    }

    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        binding.turnContainer.isVisible = statusMessage.isEmpty()

        if (statusMessage.isNotEmpty()) {
            binding.navInstruction.text = statusMessage
            setLanePanelVisible(false)
            setTopPanelLaneVisible(false)
        }
    }

    private fun getStatusMessage(): String {
        when (navigationStatus) {
            ENavigationStatus.WaitingRoute -> {
                val routeStatus = navRoute?.status

                return when (routeStatus) {
                    ERouteStatus.WaitingInternetConnection -> {
                        getString(R.string.waiting_for_internet_connection)
                    }

                    ERouteStatus.Calculating -> {
                        getString(R.string.calculating)
                    }

                    ERouteStatus.Ready -> {
                        getString(R.string.gps_accuracy_not_good_enough)
                    }

                    else -> {
                        getString(R.string.calculating)
                    }
                }
            }
            ENavigationStatus.WaitingGPS -> {
                if (navigationService.isSimulationActive()) {
                    return getString(R.string.calculating)
                }
                return getString(R.string.getting_position)
            }
            else -> {
                // Do nothing for other statuses
            }
        }

        return ""
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        permissionsLauncher.launch(permissions)
    }

    private fun onLocationPermissionsGranted() {
        SdkCall.execute {
            // Keep SDK permission helper in sync with the runtime permission state.
            PermissionsHelper.onRequestPermissionsResult(
                this,
                REQUEST_PERMISSIONS,
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
            waitForValidImprovedPositionAndStartNavigation()
        }
    }

    private fun waitForValidImprovedPositionAndStartNavigation() {
        if (PositionService.improvedPosition?.isValid() == true) {
            startNavigation()
            return
        }

        lateinit var positionListener: PositionListener
        positionListener = PositionListener { position ->
            if (!position.isValid()) return@PositionListener

            PositionService.removeListener(positionListener)
            startNavigation()
        }

        // Wait for first valid improved position before starting navigation.
        PositionService.addListener(positionListener, EDataType.ImprovedPosition)
    }

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            binding.trafficPanel.isVisible = false
            setLanePanelVisible(false)
            setTopPanelLaneVisible(false)
            updateFocusViewport()
        } else {
            binding.root.post { updateFocusViewport() }
        }
    }

    private fun startNavigation() {
        val destination = Landmark("Paris", 48.8566932, 2.3514616)

        // Cancel any navigation in progress.
        navigationService.cancelNavigation(navigationListener)
        // Start the new navigation.
        val error = navigationService.startNavigation(
            destination,
            navigationListener,
            routingProgressListener,
        )

        if (error != GemError.NoError) {
            runOnUiThread {
                showDialog(
                    getString(R.string.route_navigation_error, SdkCall.runSynced { GemError.getMessage(error, this) }),
                )
            }
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

    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val remainingTimeInSeconds = (
            this.getTimeDistance(
                true,
            )?.totalTime ?: 0
            ) + GemUtil.getTrafficEventsDelay(this, true)

        val destinationCoords = this.waypoints?.lastOrNull()?.coordinates
        if (destinationCoords == null) {
            val time = Time()
            time.setLocalTime()
            time.longValue += remainingTimeInSeconds * 1000L
            return String.format("%d:%02d", time.hour, time.minute)
        }

        // Get current local time at the destination based on its timezone.
        val destTimezoneResult = TimezoneResult()
        TimezoneService.getTimezoneInfoWithCoordinates(
            destTimezoneResult,
            destinationCoords,
            Time().apply { setUniversalTime() },
            ProgressListener(),
        )
        val arrivalTime = Time().apply {
            setUniversalTime()
            longValue += destTimezoneResult.offset * 1000L
        }

        // Correct for any discrepancy between the OS-set timezone and the GPS-derived timezone
        // at the current position (normally zero when OS timezone matches location).
        var localTimeOffset = 0L
        PositionService.improvedPosition?.let { currentPos ->
            if (currentPos.isValid()) {
                val currPosTimezoneResult = TimezoneResult()
                TimezoneService.getTimezoneInfoWithCoordinates(
                    currPosTimezoneResult,
                    currentPos.coordinates,
                    Time().apply { setUniversalTime() },
                    ProgressListener(),
                )
                val deviceLocalTimeMs = Time().apply { setLocalTime() }.longValue
                val tzServiceLocalTimeMs = Time().apply {
                    setUniversalTime()
                    longValue += currPosTimezoneResult.offset * 1000L
                }.longValue
                localTimeOffset = (deviceLocalTimeMs - tzServiceLocalTimeMs) / 1000L
            }
        }

        arrivalTime.longValue += (remainingTimeInSeconds.toLong() + localTimeOffset) * 1000L
        return String.format("%d:%02d", arrivalTime.hour, arrivalTime.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            (this.getTimeDistance(true)?.totalTime ?: 0) + GemUtil.getTrafficEventsDelay(this, true),
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

    private fun getTrafficColor(trafficDelayInMinutes: Int): Int {
        val bigTrafficDelayInMinutes = 10
        return when {
            trafficDelayInMinutes == 0 -> Color.rgb(0, 0, 0)
            trafficDelayInMinutes < bigTrafficDelayInMinutes -> Color.rgb(255, 100, 0)
            else -> Color.rgb(235, 0, 0)
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

    private fun getSignpostImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        if (!navInstr.hasSignpostInfo()) return null

        val distToNextTurn = navInstr.timeDistanceToNextTurn?.totalDistance ?: 0
        // Suppress the signpost until the driver is within 1 km of the turn to avoid
        // visual clutter on long straight segments.
        if (distToNextTurn > 1000) {
            return null
        }

        return navInstr.signpostDetails?.image?.let { image ->
            GemUtilImages.asBitmap(image, width, height)
        }
    }

    private fun getRoadCodeImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        val roadsInfo = navInstr.nextRoadInformation ?: return null
        if (roadsInfo.isEmpty()) return null
        // Road code shields are typically wider than tall; 2.5× height is a reasonable default
        // aspect ratio when no explicit width constraint is available.
        val resultWidth = if (width == 0) (2.5 * height).toInt() else width
        val image = navInstr.getRoadInfoImage(roadsInfo)
        return GemUtilImages.asBitmap(image, resultWidth, height)
    }

    /**
     * Crops the bitmap to the bounding box of its non-transparent pixels; null when the bitmap is
     * fully transparent.
     */
    private fun Bitmap.trimmedToContent(): Bitmap? {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (pixels[row + x] ushr 24 != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX) return null
        if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) return this
        return Bitmap.createBitmap(this, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    // Measures the pixel width the given text would occupy in textView, then restores the original
    // text. Used to calculate how much space the distance label can take before layout happens.
    @Suppress("SameParameterValue")
    private fun getTextViewWidth(textView: MaterialTextView, text: String): Int {
        val previous = textView.text
        textView.text = text
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(Short.MAX_VALUE.toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val width = textView.measuredWidth
        textView.text = previous
        return width
    }

    private fun updateTrafficPanel(instruction: NavigationInstruction) {
        if (!binding.topPanel.isVisible) {
            binding.trafficPanel.isVisible = false
            return
        }

        val route = navRoute ?: run {
            binding.trafficPanel.isVisible = false
            return
        }

        val trafficEvent = getTrafficEvent(instruction, route)
        if (trafficEvent == null) {
            binding.trafficPanel.isVisible = false
            trafficBmp = null
            return
        }

        updateTrafficEventInfo(trafficEvent)
        val bmp = trafficBmp ?: run {
            binding.trafficPanel.isVisible = false
            return
        }

        binding.apply {
            trafficPanel.isVisible = true
            trafficPanel.setBackgroundColor(trafficPanelBackgroundColor)

            if (!sameTrafficImage) {
                trafficImage.setImageBitmap(bmp)
            }

            endOfSectionImage.isVisible = insideTrafficEvent && endOfSectionBmp != null
            if (insideTrafficEvent) {
                endOfSectionBmp?.let { endOfSectionImage.setImageBitmap(it) }
            }

            trafficEventDescription.text = trafficEventDescriptionText

            var prefix = distanceToTrafficPrefixText
            if (prefix.isNotEmpty()) prefix = "$prefix "
            distanceToTrafficPrefix.text = prefix

            distanceToTraffic.text = distanceToTrafficText
            distanceToTrafficUnit.text = distanceToTrafficUnitText

            trafficDelay.text = buildTrafficDelayText()
        }
    }

    // Delay time + event length as one string (values big, units small) so the single-line
    // traffic_delay view ellipsizes the whole text instead of the parts wrapping separately.
    private fun buildTrafficDelayText(): CharSequence {
        val unitTextSize = resources.getDimensionPixelSize(R.dimen.nav_top_panel_traffic_small_text_size)

        return SpannableStringBuilder().apply {
            fun appendValueWithUnit(value: String, unit: String) {
                if (value.isEmpty()) return
                if (isNotEmpty()) append(" ")
                append(value)
                if (unit.isNotEmpty()) {
                    append(unit)
                    setSpan(
                        AbsoluteSizeSpan(unitTextSize),
                        length - unit.length,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }

            appendValueWithUnit(trafficDelayTimeText, trafficDelayTimeUnitText)
            appendValueWithUnit(trafficDelayDistanceText, trafficDelayDistanceUnitText)
        }
    }

    private fun getTrafficEvent(navInstr: NavigationInstruction, route: Route): RouteTrafficEvent? = SdkCall.execute {
        if (navInstr.navigationStatus != ENavigationStatus.Running) return@execute null
        val trafficEventsList = route.trafficEvents ?: return@execute null

        val remainingTravelDistance = navInstr.remainingTravelTimeDistance?.totalDistance ?: 0

        for (event in trafficEventsList) {
            if (event.delay != 0) {
                val distToDest = event.distanceToDestination
                // Positive value means the event is ahead; negative means we have already passed
                // the start of the event and are potentially inside it.
                distToTrafficEvent = remainingTravelDistance - distToDest

                insideTrafficEvent = false

                if (distToTrafficEvent <= 0) {
                    // How many metres of the event still remain in front of us.
                    remainingDistInsideTrafficEvent = event.length - (distToDest - remainingTravelDistance)
                    if (remainingDistInsideTrafficEvent >= 0) {
                        insideTrafficEvent = true
                    }
                }

                if ((distToTrafficEvent >= 0) || (remainingDistInsideTrafficEvent >= 0)) {
                    return@execute event
                }
            }
        }

        return@execute null
    }

    private fun updateTrafficEventInfo(trafficEvent: RouteTrafficEvent) = SdkCall.execute {
        trafficEventDescriptionText = trafficEvent.description ?: ""

        val distance = if (insideTrafficEvent) remainingDistInsideTrafficEvent else distToTrafficEvent
        val distancePair = GemUtil.getDistText(distance, EUnitSystem.Metric, true)
        distanceToTrafficText = distancePair.first
        distanceToTrafficUnitText = distancePair.second

        val theFormat = getString(if (insideTrafficEvent) R.string.out_in_str else R.string.in_str)
        distanceToTrafficPrefixText = String.format(theFormat, "").trim()

        trafficDelayDistanceText = ""
        trafficDelayDistanceUnitText = ""
        trafficDelayTimeText = ""
        trafficDelayTimeUnitText = ""

        if (!trafficEvent.isRoadblock) {
            if (insideTrafficEvent) {
                if (trafficEvent.length > 0) {
                    // Scale the full event delay proportionally to the distance still to travel
                    // through it, so the displayed delay shrinks as the driver progresses.
                    val remainingDelay = (trafficEvent.delay * remainingDistInsideTrafficEvent) / trafficEvent.length
                    val timePair = GemUtil.getTimeText(remainingDelay)
                    trafficDelayTimeText = timePair.first
                    trafficDelayTimeUnitText = timePair.second
                }
            } else {
                val distPair = GemUtil.getDistText(trafficEvent.length, SdkSettings.unitSystem, true)
                trafficDelayDistanceText = distPair.first
                trafficDelayDistanceUnitText = distPair.second

                val timePair = GemUtil.getTimeText(trafficEvent.delay)
                trafficDelayTimeText = String.format("+%s", timePair.first)
                trafficDelayTimeUnitText = timePair.second
            }
        }

        val newBmp = getTrafficImage(trafficEvent, navigationImageSize, navigationImageSize)
        if (newBmp != null) {
            trafficBmp = newBmp
            sameTrafficImage = false
        } else {
            sameTrafficImage = true
        }
    }

    private fun getTrafficImage(from: RouteTrafficEvent?, width: Int, height: Int): Bitmap? = SdkCall.execute {
        if ((from?.image?.uid ?: 0) == lastTrafficImageId) return@execute null

        val image = from?.image
        if (image != null) {
            lastTrafficImageId = image.uid
        }

        GemUtilImages.asBitmap(image, width, height)
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        showBottomSheetDialog(
            title = getString(R.string.error),
            message = text,
            buttonText = null,
            onButtonClick = { dialog ->
                onDismiss?.invoke()
                dialog.dismiss()
            },
        )
    }

    private fun showBottomSheetDialog(
        title: String,
        message: String,
        buttonText: String?,
        onButtonClick: (BottomSheetDialog) -> Unit,
    ) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
            this.message.text = message
            buttonText?.let { button.text = it }
            button.setOnClickListener {
                onButtonClick(dialog)
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

    companion object {
        private const val REQUEST_PERMISSIONS = 110

        // Fraction of the screen width occupied by the navigation panels in landscape orientation.
        private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.45f
    }
}
