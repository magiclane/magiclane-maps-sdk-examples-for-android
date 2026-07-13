/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routingonmap

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.examples.routingonmap.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routingonmap.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        // System insets (status/navigation bars plus display cutout) used to keep map content
        // and the Magic Lane logo clear of system UI.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    private var routesList = ArrayList<Route>()

    // Shared animation duration used for both presenting and re-centering routes.
    private val routeAnimationDurationMs = 900

    private val routingService = RoutingService(
        onStarted = {
            runOnAliveUi { binding.progressBar.visibility = View.VISIBLE }
        },

        onCompleted = { routes, errorCode, _ ->
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE

                when (errorCode) {
                    GemError.NoError -> onRoutesReady(routes)
                    GemError.Cancel -> { /* Routing canceled — no action needed. */ }
                    else -> showDialog(
                        getString(R.string.routing_completed_with_error, GemError.getMessage(errorCode, this)),
                    )
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status bar icons light so they are visible over the map.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()

        // Release the SDK before the activity is fully destroyed.
        GemSdk.release()

        super.onDestroy()
        exitProcess(0)
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        // calculateRoute returns synchronously whether the calculation could be started. On
        // failure onCompleted never fires, so report the error and hide the progress bar here.
        val errorCode = routingService.calculateRoute(waypoints)
        if (errorCode != GemError.NoError) {
            val message = GemError.getMessage(errorCode, this)
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE
                showDialog(
                    getString(R.string.routing_failed_to_start, message),
                )
            }
        }
    }

    // Called when routing succeeds. Stores the result and displays all routes on the map.
    private fun onRoutesReady(routes: ArrayList<Route>) {
        routesList = routes
        SdkCall.execute {
            // Present routes centred within the free screen area, avoiding toolbar and system bars.
            binding.gemSurfaceView.mapView?.presentRoutes(
                routes,
                displayBubble = true,
                animation = Animation(EAnimation.Linear, routeAnimationDurationMs),
                edgeAreaInsets = getEdgeAreaInsets(),
            )
        }
    }

    // Registers the map touch listener. Tapping a route makes it the main route and re-centres
    // the map on the full route list.
    private fun setupTouchHandler() {
        binding.gemSurfaceView.mapView?.onTouch = { xy ->
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.cursorScreenPosition = xy

                val routes = binding.gemSurfaceView.mapView?.cursorSelectionRoutes
                if (!routes.isNullOrEmpty()) {
                    binding.gemSurfaceView.mapView?.apply {
                        preferences?.routes?.mainRoute = routes[0]
                        centerOnRoutes(
                            routesList,
                            animation = Animation(EAnimation.Linear, routeAnimationDurationMs),
                            viewRc = getRouteViewRect(),
                        )
                    }
                }
            }
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

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        // Align the Magic Lane logo with the system window insets as soon as the map is created.
        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Triggered when the worldwide road map changes readiness state. Route calculation and
        // touch handling are set up only once the map data is fully available (UpToDate). The
        // listener clears itself after the first successful fire to avoid re-triggering.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                calculateRoute()
                setupTouchHandler()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    // Positions the Magic Lane logo (and other map UI) inside the area left free by the system
    // bars and display cutout, so it is never hidden behind system UI. Called when the map view
    // is first created and whenever the surface is resized.
    private fun updateFocusViewport() = SdkCall.runSynced {
        val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
        val viewport = mapView.viewport ?: return@runSynced
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

        val left = insets?.left ?: 0
        val top = insets?.top ?: 0
        val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
        mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
    }

    // Returns edge insets (px) for presentRoutes: the SDK uses these to keep routes inside the
    // visible area, clear of the toolbar, system bars and display cutouts.
    private fun getEdgeAreaInsets(): Rect {
        val (left, top, right, bottom) = resolveMapPadding()
        return Rect(left, top, right, bottom)
    }

    // Returns the free-screen rectangle (absolute px coordinates) for centerOnRoutes: the SDK
    // fits the route collection inside this rect when re-centering after a touch.
    private fun getRouteViewRect(): Rect {
        val mapWidth = binding.gemSurfaceView.width.takeIf { it > 0 } ?: binding.gemSurfaceView.measuredWidth
        val mapHeight = binding.gemSurfaceView.height.takeIf { it > 0 } ?: binding.gemSurfaceView.measuredHeight
        val (left, top, right, bottom) = resolveMapPadding()
        return Rect(
            left,
            top,
            (mapWidth - right).coerceAtLeast(left),
            (mapHeight - bottom).coerceAtLeast(top),
        )
    }

    // Resolves the four padding values (px) shared by getEdgeAreaInsets and getRouteViewRect.
    // Top uses toolbar.bottom instead of the status-bar inset because the toolbar absorbs the
    // status-bar height via paddingTopWithSystemWindowInsets. takeIf guards against pre-layout.
    private fun resolveMapPadding(): Array<Int> {
        val sysInsets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        val padding = resources.getDimensionPixelSize(R.dimen.big_padding)
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        return arrayOf(
            (sysInsets?.left ?: 0) + padding, // left
            toolbarBottom + padding, // top
            (sysInsets?.right ?: 0) + padding, // right
            (sysInsets?.bottom ?: 0) + padding, // bottom
        )
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
