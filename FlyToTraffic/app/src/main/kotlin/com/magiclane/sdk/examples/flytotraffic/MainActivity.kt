/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.flytotraffic

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
import com.magiclane.sdk.examples.flytotraffic.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.flytotraffic.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RouteTrafficEvent
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, gemError, _ ->
            binding.progressBar.visibility = View.GONE

            when (gemError) {
                GemError.NoError -> {
                    if (routes.isEmpty()) return@onCompleted

                    val route = routes[0]

                    SdkCall.runSynced {
                        // Retrieve traffic events from the main route.
                        val events = route.trafficEvents

                        if (events.isNullOrEmpty()) {
                            runOnAliveUi { showDialog(getString(R.string.no_traffic_events)) }
                            return@runSynced
                        }

                        // Display the route on the map without auto-centering.
                        binding.gemSurfaceView.mapView?.presentRoute(route, centerMapView = false)

                        flyToTraffic(events[0])
                    }
                }
                else -> {
                    val errorMessage = SdkCall.runSynced { GemError.getMessage(gemError, this) }
                    runOnAliveUi { showDialog(getString(R.string.routing_error, errorMessage)) }
                }
            }
        },
    )

    private lateinit var binding: ActivityMainBinding

    private var inflate = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        inflate = resources.getDimension(R.dimen.padding_40).toInt()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { _ ->
            // Align the Magic Lane logo with system window insets on first map creation.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // Clear the listener immediately to avoid repeated route calculations.
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }

                SdkCall.runSynced {
                    val waypoints = arrayListOf(
                        Landmark("London", 51.5073204, -0.1276475),
                        Landmark("Paris", 48.8566932, 2.3514616),
                    )

                    // calculateRoute returns synchronously whether the calculation could be
                    // started. On failure onCompleted never fires, so report the error here.
                    val errorCode = routingService.calculateRoute(waypoints)
                    if (errorCode != GemError.NoError) {
                        val errorMessage = GemError.getMessage(errorCode, this)
                        runOnAliveUi { showDialog(getString(R.string.routing_failed_to_start, errorMessage)) }
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to prevent callbacks from reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val w = viewport.width
            val h = viewport.height
            val left = insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    // Centers the camera on the given traffic event with a smooth animation.
    private fun flyToTraffic(trafficEvent: RouteTrafficEvent) = SdkCall.runSynced {
        binding.gemSurfaceView.mapView?.centerOnRouteTrafficEvent(
            trafficEvent,
            rc = getFreeSpaceRect(),
            animation = Animation(EAnimation.Linear, 900),
            viewAngle = 0.0,
        )
    }

    // Returns the usable screen area excluding system bars and the toolbar.
    private fun getFreeSpaceRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(SYSTEM_INSET_TYPES)

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val left = (insets?.left ?: 0) + inflate
        val right = (width - (insets?.right ?: 0) - inflate).coerceAtLeast(left)

        val topInset = (insets?.top ?: 0) + inflate
        val toolbarBottom = (binding.toolbar.bottom.takeIf { it > 0 } ?: 0) + inflate
        val top = maxOf(topInset, toolbarBottom)
        val bottom = (height - (insets?.bottom ?: 0) - inflate).coerceAtLeast(top)

        return Rect(left, top, right, bottom)
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

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
