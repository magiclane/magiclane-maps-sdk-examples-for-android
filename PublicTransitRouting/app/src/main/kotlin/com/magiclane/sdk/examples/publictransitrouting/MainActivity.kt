/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EWatermarkPosition
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.publictransitrouting.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.publictransitrouting.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess
import androidx.core.view.isVisible

/**
 * Displays public-transit routes like the Magic Earth app: only the selected route is drawn on
 * the map, a horizontal routes list sits at the bottom with route description / settings buttons
 * above it, and a "Routes" button at the top right opens the full routes list. In portrait the
 * panel spans the full width and routes are presented above it; in landscape it takes half the
 * screen and routes are presented in the other, panel-free half.
 */
class MainActivity : AppCompatActivity(), PTRouteSession.Controller {

    private companion object {
        // Window insets that the map UI must stay clear of (status/navigation bars and cutout).
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        // Waypoints for the example public-transit route.
        private const val SAN_FRANCISCO_NAME = "San Francisco"
        private const val SAN_FRANCISCO_LAT = 37.77903
        private const val SAN_FRANCISCO_LON = -122.41991

        private const val SAN_JOSE_NAME = "San Jose"
        private const val SAN_JOSE_LAT = 37.33619
        private const val SAN_JOSE_LON = -121.89058

        private const val EARLIER_LATER_STEP_MILLIS = 60_000L

        // Bottom panel width as a fraction of the screen: full-width in portrait, half in
        // landscape so the selected route can be presented in the other half of the screen.
        private const val PANEL_WIDTH_PERCENT_PORTRAIT = 1.0f
        private const val PANEL_WIDTH_PERCENT_LANDSCAPE = 0.5f

        // Duration of the camera flight when centering on a route or one of its segments.
        private const val FLY_ANIMATION_DURATION_MS = 900

        // Magic Lane watermark logo size (millimeters) and opacity.
        private const val LOGO_SIZE_MM = 20.0f
        private const val LOGO_ALPHA = 1.0f
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var chipAdapter: RouteChipAdapter

    // Extra margin kept around the presented route so it is not glued to the screen edges.
    private var freeSpacePaddingPx = 0

    private val routingService = RoutingService(
        onStarted = { onRoutingStarted() },
        onCompleted = { routes, errorCode, _ -> onRoutingCompleted(routes, errorCode) },
    )

    // UI space unavailable to the map, captured on the main thread for SDK-thread camera calls.
    private data class UiChrome(
        val insetLeft: Int,
        val insetTop: Int,
        val insetRight: Int,
        val insetBottom: Int,
        val topBarBottom: Int,
        val bottomPanelHeight: Int,
        // Horizontal band covered by the bottom panel: the full width in portrait, one half of
        // the screen in landscape (the other half is then free for the camera).
        val bottomPanelLeft: Int,
        val bottomPanelRight: Int,
        val bottomPanelFillsWidth: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Without a toolbar the status-bar icons sit on the map: dark icons on the light map
        // style, light icons when the night theme (dark map style) is active.
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        freeSpacePaddingPx = resources.getDimension(R.dimen.map_free_space_padding).toInt()

        // The manifest handles orientation changes (the activity is not recreated on rotation),
        // so the landscape variant of the bottom panel is applied in code, both here for the
        // launch orientation and again from onConfigurationChanged.
        applyOrientationLayout()
        applyBottomPanelInsets()

        chipAdapter = RouteChipAdapter(
            onRouteTap = { routeIndex -> selectRoute(routeIndex) },
            onSegmentTap = { routeIndex, segmentIndex -> onSegmentIconTap(routeIndex, segmentIndex) },
        )
        binding.routesList.adapter = chipAdapter

        // One full-width route card per page; the card that settles into view becomes the
        // displayed route on the map (as in Magic Earth).
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.routesList)
        binding.routesList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val layoutManager = recyclerView.layoutManager ?: return
                val snapView = snapHelper.findSnapView(layoutManager) ?: return
                val position = layoutManager.getPosition(snapView)
                if (position != PTRouteSession.selectedRouteIndex) selectRoute(position)
            }
        })

        binding.routesButton.setOnClickListener { startActivity(Intent(this, PTRoutesActivity::class.java)) }
        binding.descriptionButton.setOnClickListener {
            startActivity(Intent(this, PTRouteDescriptionActivity::class.java))
        }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, PTSettingsActivity::class.java)) }

        // Keep the map attributions clear of the bottom panel as its size changes.
        binding.bottomPanel.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) updateFocusViewport()
        }

        PTRouteSession.controller = this

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        applyOrientationLayout()
        // The padded sides depend on the orientation, so re-evaluate them.
        ViewCompat.requestApplyInsets(binding.bottomPanel)

        // Re-center on the selected route once the panel is laid out at its new size, so the
        // route lands in the space the panel no longer covers.
        if (binding.bottomPanel.isVisible) {
            binding.root.doOnPreDraw { displaySelectedRoute() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PTRouteSession.clear()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    /**
     * Sizes the bottom panel for the current orientation: full-width in portrait, half the
     * screen in landscape — one route card is then half a screen wide and the selected route is
     * presented in the other, panel-free half.
     */
    private fun applyOrientationLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.bottomPanel.updateLayoutParams<ConstraintLayout.LayoutParams> {
            matchConstraintPercentWidth =
                if (isLandscape) PANEL_WIDTH_PERCENT_LANDSCAPE else PANEL_WIDTH_PERCENT_PORTRAIT
        }
    }

    /**
     * Keeps the bottom panel clear of the system bars and display cutout. Done in code instead
     * of the usual XML insets attributes because the padded sides change with the orientation:
     * the half-width landscape panel hugs the start edge and must not absorb the far-side inset,
     * otherwise its route card would shrink for a system bar it does not even touch.
     */
    private fun applyBottomPanelInsets() {
        val bottomPaddingPx = resources.getDimensionPixelSize(R.dimen.bottom_panel_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomPanel) { panel, windowInsets ->
            val insets = windowInsets.getInsets(SYSTEM_INSET_TYPES)
            val fillsWidth = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val startIsLeft = panel.layoutDirection != View.LAYOUT_DIRECTION_RTL
            panel.updatePadding(
                left = if (fillsWidth || startIsLeft) insets.left else 0,
                right = if (fillsWidth || !startIsLeft) insets.right else 0,
                bottom = insets.bottom + bottomPaddingPx,
            )
            windowInsets
        }
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            // SDK is not initialized here, so resolve the message directly (no SdkCall needed).
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            // Pin the Magic Lane logo to the top-left corner of the visible map area.
            mapView.setWatermarkLogoProperties(EWatermarkPosition.EWPTopLeft, LOGO_SIZE_MM, LOGO_ALPHA)

            // Align the map decorations with the system window insets on first map creation.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // Only calculate once the worldwide road map is ready.
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                runOnAliveUi { startCalculation() }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // region calculation

    /**
     * Starts a (re)calculation with the current [PTSettings]: the map is cleared of the previous
     * route and all route UI is hidden until the calculation completes successfully.
     */
    private fun startCalculation() {
        setRouteUiVisible(false)
        binding.progressBar.visibility = View.VISIBLE
        PTRouteSession.notifyCalculationStarted()

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.preferences?.routes?.clear()

            routingService.preferences.transportMode = ERouteTransportMode.Public
            PTSettings.applyTo(routingService.preferences)

            val waypoints = arrayListOf(
                Landmark(SAN_FRANCISCO_NAME, SAN_FRANCISCO_LAT, SAN_FRANCISCO_LON),
                Landmark(SAN_JOSE_NAME, SAN_JOSE_LAT, SAN_JOSE_LON),
            )

            // calculateRoute returns synchronously whether the calculation could be started. On
            // failure onRoutingCompleted never fires, so report the error here.
            val errorCode = routingService.calculateRoute(waypoints)
            if (errorCode != GemError.NoError) {
                val message = GemError.getMessage(errorCode, this)
                runOnAliveUi {
                    binding.progressBar.visibility = View.GONE
                    PTRouteSession.notifyCalculationCompleted(errorCode)
                    showDialog(getString(R.string.routing_failed_to_start, message))
                }
            }
        }
    }

    private fun onRoutingStarted() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun onRoutingCompleted(routes: ArrayList<Route>, errorCode: Int) {
        binding.progressBar.visibility = View.GONE

        when (errorCode) {
            GemError.NoError -> SdkCall.execute {
                PTRouteSession.routes = routes
                PTRouteSession.items = routes.mapIndexed { index, route ->
                    PTRouteItem.build(applicationContext, index, route)
                }
                PTRouteSession.selectedRouteIndex = 0

                runOnAliveUi {
                    chipAdapter.submit(PTRouteSession.items)
                    binding.routesList.scrollToPosition(0)
                    setRouteUiVisible(true)
                    // Center once the bottom panel is laid out, so its height is accounted for.
                    binding.bottomPanel.doOnPreDraw { displaySelectedRoute() }
                    PTRouteSession.notifyCalculationCompleted(GemError.NoError)
                }
            }

            GemError.Cancel -> {
                PTRouteSession.notifyCalculationCompleted(errorCode)
                showDialog(getString(R.string.routing_cancelled))
            }

            else -> {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                PTRouteSession.notifyCalculationCompleted(errorCode)
                showDialog(getString(R.string.routing_error, message))
            }
        }
    }

    // endregion

    // region PTRouteSession.Controller

    override fun selectRoute(routeIndex: Int) {
        PTRouteSession.selectedRouteIndex = routeIndex
        binding.routesList.smoothScrollToPosition(routeIndex)
        displaySelectedRoute()
    }

    override fun flyToSegment(routeIndex: Int, segmentIndex: Int) {
        val chrome = captureUiChrome()
        SdkCall.execute {
            val mapView = binding.gemSurfaceView.mapView ?: return@execute
            val route = PTRouteSession.routes.getOrNull(routeIndex) ?: return@execute
            val area = route.segments?.getOrNull(segmentIndex)?.geographicArea ?: return@execute
            mapView.centerOnRectArea(
                area,
                -1,
                freeSpaceRect(mapView, chrome),
                Animation(EAnimation.Linear, FLY_ANIMATION_DURATION_MS),
            )
        }
    }

    override fun recalculate() {
        startCalculation()
    }

    override fun requestEarlier() {
        // "Arrive by one minute before the earliest current arrival" (as in Magic Earth).
        val minArrival = PTRouteSession.items.filter { it.arrivalMillis > 0 }.minOfOrNull { it.arrivalMillis }
            ?: return
        PTSettings.timeMode = PTSettings.TimeMode.Arrive
        PTSettings.customTimeMillis = minArrival - EARLIER_LATER_STEP_MILLIS
        startCalculation()
    }

    override fun requestLater() {
        // "Depart one minute after the latest current departure" (as in Magic Earth).
        val maxDeparture = PTRouteSession.items.filter { it.departureMillis > 0 }.maxOfOrNull { it.departureMillis }
            ?: return
        PTSettings.timeMode = PTSettings.TimeMode.Depart
        PTSettings.customTimeMillis = maxDeparture + EARLIER_LATER_STEP_MILLIS
        startCalculation()
    }

    // endregion

    // region map display

    /** Tapping a segment icon: make sure its route is the displayed one, then fly to the segment. */
    private fun onSegmentIconTap(routeIndex: Int, segmentIndex: Int) {
        if (routeIndex != PTRouteSession.selectedRouteIndex) {
            PTRouteSession.selectedRouteIndex = routeIndex
            displaySelectedRoute(centerOnRoute = false)
        }
        flyToSegment(routeIndex, segmentIndex)
    }

    /** Shows only the selected route on the map and optionally centers on it. */
    private fun displaySelectedRoute(centerOnRoute: Boolean = true) {
        val chrome = captureUiChrome()
        SdkCall.execute {
            val mapView = binding.gemSurfaceView.mapView ?: return@execute
            val route = PTRouteSession.routes.getOrNull(PTRouteSession.selectedRouteIndex) ?: return@execute

            mapView.preferences?.routes?.apply {
                clear()
                add(route, true)
            }

            if (centerOnRoute) {
                mapView.centerOnRoute(
                    route,
                    freeSpaceRect(mapView, chrome),
                    Animation(EAnimation.Linear, FLY_ANIMATION_DURATION_MS),
                )
            }
        }
    }

    private fun setRouteUiVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        binding.routesButton.visibility = visibility
        binding.bottomPanel.visibility = visibility
    }

    /** Captures (on the main thread) the screen space covered by UI, for SDK-thread camera calls. */
    private fun captureUiChrome(): UiChrome {
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        val panelVisible = binding.bottomPanel.isVisible
        return UiChrome(
            insetLeft = insets?.left ?: 0,
            insetTop = insets?.top ?: 0,
            insetRight = insets?.right ?: 0,
            insetBottom = insets?.bottom ?: 0,
            topBarBottom = if (binding.routesButton.isVisible) binding.routesButton.bottom else 0,
            bottomPanelHeight = if (panelVisible) binding.bottomPanel.height else 0,
            bottomPanelLeft = if (panelVisible) binding.bottomPanel.left else 0,
            bottomPanelRight = if (panelVisible) binding.bottomPanel.right else 0,
            bottomPanelFillsWidth = binding.bottomPanel.width >= binding.root.width,
        )
    }

    /**
     * The screen rectangle left free by the UI (system bars, "Routes" button, bottom panel) plus
     * a small padding — the equivalent of Magic Earth's free-space rectangle. In portrait the
     * free space is above the full-width panel; in landscape the panel covers only one side of
     * the screen, so routes/segments are presented in the other, panel-free side.
     */
    private fun freeSpaceRect(mapView: MapView, chrome: UiChrome): Rect {
        val viewport = mapView.viewport ?: return Rect()

        var left = chrome.insetLeft
        var right = viewport.width - chrome.insetRight
        var bottom = viewport.height - chrome.insetBottom
        val top = maxOf(chrome.insetTop, chrome.topBarBottom) + freeSpacePaddingPx

        if (chrome.bottomPanelHeight > 0) {
            if (chrome.bottomPanelFillsWidth) {
                bottom -= chrome.bottomPanelHeight
            } else if (viewport.width - chrome.bottomPanelRight >= chrome.bottomPanelLeft) {
                left = maxOf(left, chrome.bottomPanelRight) // panel on the left (LTR)
            } else {
                right = minOf(right, chrome.bottomPanelLeft) // panel on the right (RTL)
            }
        }

        left += freeSpacePaddingPx
        right = (right - freeSpacePaddingPx).coerceAtLeast(left + 1)
        bottom = (bottom - freeSpacePaddingPx).coerceAtLeast(top + 1)

        return Rect(left, top, right, bottom)
    }

    // Positions the Magic Lane logo (and other map decorations) inside the visible map area,
    // clear of the system window insets and the bottom panel.
    private fun updateFocusViewport() {
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        val bottomPanelHeight =
            if (binding.bottomPanel.isVisible) binding.bottomPanel.height else 0

        SdkCall.runSynced {
            val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced

            val left = insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (viewport.height - (insets?.bottom ?: 0) - bottomPanelHeight).coerceAtLeast(top)
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    // endregion

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

    // Runs the block on the main thread only if the activity is still alive.
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
