/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routeterrainprofile

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMapViewPerspective
import com.magiclane.sdk.examples.routeterrainprofile.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routeterrainprofile.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityMainBinding

    //region TESTING
    @VisibleForTesting
    lateinit var routingProfile: RouteProfile
    //endregion

    val gemSurfaceView: GemSurfaceView get() = binding.gemSurfaceView

    // Routing service that calculates a route with a terrain profile and reports the result.
    private val routingService = RoutingService(
        onStarted = {
            runOnAliveUi { binding.progressBar.visibility = View.VISIBLE }
        },

        onCompleted = { routes, errorCode, _ ->
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE

                when (errorCode) {
                    GemError.NoError -> {
                        val route = routes.firstOrNull()
                        // The terrain profile is only available when buildTerrainProfile was
                        // enabled and the SDK could compute elevation data for the route.
                        val terrain = route?.let { SdkCall.execute { it.terrainProfile } }
                        when {
                            route != null && terrain != null -> displayTerrainInfo(route)
                            route != null -> {
                                showDialog(getString(R.string.terrain_profile_not_available))
                                EspressoIdlingResource.decrement()
                            }
                            else -> EspressoIdlingResource.decrement()
                        }
                    }

                    GemError.Cancel -> {
                        showDialog(getString(R.string.routing_cancelled))
                        EspressoIdlingResource.decrement()
                    }

                    else -> {
                        showDialog(
                            getString(
                                R.string.routing_error,
                                SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                            ),
                        )
                        EspressoIdlingResource.decrement()
                    }
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Increment idling resource so UI tests wait until route calculation completes.
        EspressoIdlingResource.increment()

        ViewCompat.setOnApplyWindowInsetsListener(binding.routeProfilePanel) { _, insets ->
            applyInsetsToRouteProfilePanel(insets)
            insets
        }

        setConstraints(resources.configuration.orientation)
        registerSdkListeners()

        // The GemSurfaceView initializes the SDK on its own; initialization failures are reported
        // through gemSurfaceView.onSdkInitFailed (registered above).

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        setConstraints(newConfig.orientation)
    }

    override fun onDestroy() {
        clearSdkListeners()

        // Release the SDK before the activity is fully destroyed.
        GemSdk.release()

        super.onDestroy()
        // The SDK holds native threads that do not stop on their own when the activity is
        // destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    fun zoomToRoute() = SdkCall.execute {
        gemSurfaceView.mapView?.let { mapView ->
            mapView.preferences?.routes?.mainRoute?.let { mainRoute ->
                mapView.preferences?.setMapViewPerspective(EMapViewPerspective.TwoDimensional)
                mapView.centerOnRoute(
                    mainRoute,
                    getMapFreeRect(resources.getDimensionPixelSize(R.dimen.map_edge_padding)),
                    Animation(animation = EAnimation.Linear, duration = 900),
                )
            }
        }
    }

    fun isDarkThemeOn(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun registerSdkListeners() {
        // The surface failed to initialize the SDK. The SDK is not available here, so the error
        // message is resolved directly, without an enclosing SdkCall.runSynced block.
        gemSurfaceView.onSdkInitFailed = { error ->
            val message = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(message) { finish() } }
        }

        // Position the Magic Lane logo within the visible map area once the map view exists.
        gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        // Re-position the logo whenever the surface is resized (orientation change, or the route
        // profile panel appearing/disappearing).
        gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Delay route calculation until the worldwide road map data is ready and up to date;
        // the callback is cleared immediately after firing to avoid repeat invocations.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                calculateRoute()
            }
        }

        SdkSettings.onApiTokenRejected = {
            // The TOKEN in AndroidManifest.xml was rejected. Provide a valid token from
            // magiclane.com to proceed.
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    private fun displayRoute(route: Route) = SdkCall.execute {
        gemSurfaceView.mapView?.presentRoute(route, edgeAreaInsets = getMapEdgeAreaInsets())
    }

    private fun displayTerrainInfo(route: Route) {
        // Show the layout that contains the elevation views.
        binding.routeProfilePanel.visibility = View.VISIBLE

        // Present the route after the panel is visible so edge insets are computed correctly.
        binding.routeProfilePanel.post {
            displayRoute(route)
        }

        routingProfile = RouteProfile(this, route)
        EspressoIdlingResource.decrement()
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Brasov", 45.65085, 25.60471),
            Landmark("Bucharest", 44.43614, 26.10268),
        )

        /**
         * Setting buildTerrainProfile to true is mandatory to receive terrain profile data;
         * without it the terrain profile is not calculated during routing.
         */
        routingService.preferences.buildTerrainProfile = true

        // calculateRoute returns synchronously whether the calculation could be started. On
        // failure onCompleted never fires, so report the error and stop the idling wait here.
        val errorCode = routingService.calculateRoute(waypoints)
        if (errorCode != GemError.NoError) {
            val errorMessage = GemError.getMessage(errorCode, this)
            runOnAliveUi { showDialog(getString(R.string.routing_failed_to_start, errorMessage)) }
            EspressoIdlingResource.decrement()
        }
    }

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

    // Posts [block] to the main thread, skipping it if the activity is already tearing down.
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun setConstraints(orientation: Int) {
        val rootView = binding.rootView
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                ConstraintSet().apply {
                    clone(rootView)

                    connect(R.id.route_profile_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.route_profile_panel, ConstraintSet.END, R.id.gem_surface_view, ConstraintSet.START)
                    connect(R.id.route_profile_panel, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(
                        R.id.route_profile_panel,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                    )

                    connect(R.id.gem_surface_view, ConstraintSet.START, R.id.route_profile_panel, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    applyTo(rootView)
                }

                binding.routeProfilePanel.layoutParams.apply {
                    width = (resources.displayMetrics.widthPixels * 0.55f).toInt()
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                }
                binding.routeProfilePanel.requestLayout()

                binding.gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
                binding.gemSurfaceView.requestLayout()
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                ConstraintSet().apply {
                    clone(rootView)

                    connect(R.id.gem_surface_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.gem_surface_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, R.id.route_profile_panel, ConstraintSet.TOP)

                    connect(R.id.route_profile_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.route_profile_panel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.route_profile_panel, ConstraintSet.TOP, R.id.gem_surface_view, ConstraintSet.BOTTOM)
                    connect(
                        R.id.route_profile_panel,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                    )

                    applyTo(rootView)
                }

                binding.routeProfilePanel.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                }
                binding.routeProfilePanel.requestLayout()

                binding.gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                }
                binding.gemSurfaceView.requestLayout()
            }
        }
    }

    // Adjusts the Magic Lane logo position so it stays within the visible map area, clear of the
    // system bars and display cutout. Wrapped in SdkCall.runSynced as it touches map preferences.
    private fun updateFocusViewport() = SdkCall.runSynced {
        gemSurfaceView.mapView?.preferences?.focusViewport = getMapFreeRect()
    }

    // Edge insets (px) handed to presentRoute so the route is fitted clear of the system bars,
    // display cutout and the route profile panel edge.
    private fun getMapEdgeAreaInsets(): Rect {
        val sys = ViewCompat.getRootWindowInsets(gemSurfaceView)?.getInsets(SYSTEM_INSET_TYPES)
        val p = resources.getDimensionPixelSize(R.dimen.map_edge_padding)
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Rect(p, (sys?.top ?: 0) + p, (sys?.right ?: 0) + p, (sys?.bottom ?: 0) + p)
        } else {
            Rect((sys?.left ?: 0) + p, (sys?.top ?: 0) + p, (sys?.right ?: 0) + p, p)
        }
    }

    // Returns the free map area in surface coordinates: the part of the GemSurfaceView left
    // visible after subtracting the system bars and display cutout. A non-zero [padding] deflates
    // the rect, which is useful to inset camera animations away from the screen edges.
    private fun getMapFreeRect(padding: Int = 0): Rect {
        val mapWidth = gemSurfaceView.width.takeIf { it > 0 } ?: gemSurfaceView.measuredWidth
        val mapHeight = gemSurfaceView.height.takeIf { it > 0 } ?: gemSurfaceView.measuredHeight
        val sys = ViewCompat.getRootWindowInsets(gemSurfaceView)?.getInsets(SYSTEM_INSET_TYPES)
        val left = sys?.left ?: 0
        val top = sys?.top ?: 0
        val right = sys?.right ?: 0
        val bottom = sys?.bottom ?: 0

        // Once visible, the route profile panel covers the screen edge adjacent to the surface
        // (left in landscape, bottom in portrait), so that inset is already off-surface and must
        // not be subtracted again. Until the panel appears (e.g. at startup, before the route is
        // computed) the surface fills the whole screen, so every system bar inset can overlap the
        // map and all four must be honoured.
        val panelVisible = binding.routeProfilePanel.isVisible
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val leftInset = if (panelVisible && isLandscape) 0 else left
        val bottomInset = if (panelVisible && !isLandscape) 0 else bottom

        return Rect(
            leftInset + padding,
            top + padding,
            mapWidth - (right + padding),
            mapHeight - (bottomInset + padding),
        )
    }

    private fun applyInsetsToRouteProfilePanel(insets: WindowInsetsCompat) {
        val systemBars = insets.getInsets(SYSTEM_INSET_TYPES)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.routeProfilePanel.setPadding(systemBars.left, systemBars.top, 0, systemBars.bottom)
        } else {
            binding.routeProfilePanel.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
        }
    }

    private companion object {
        // System bars + display cutout: the regions that may overlap the map surface.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("RouteTerrainIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
