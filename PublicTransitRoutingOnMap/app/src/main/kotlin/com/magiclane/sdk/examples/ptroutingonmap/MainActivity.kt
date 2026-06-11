/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.ptroutingonmap

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
import com.magiclane.sdk.examples.ptroutingonmap.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.ptroutingonmap.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private companion object {
        // Window insets that the map area must stay clear of (status/navigation bars and cutout).
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        // Waypoints for the example public-transit route.
        private const val SAN_FRANCISCO_NAME = "San Francisco"
        private const val SAN_FRANCISCO_LAT = 37.77903
        private const val SAN_FRANCISCO_LON = -122.41991

        private const val SAN_JOSE_NAME = "San Jose"
        private const val SAN_JOSE_LAT = 37.33619
        private const val SAN_JOSE_LON = -121.89058
    }

    private lateinit var binding: ActivityMainBinding

    // Extra margin kept around the presented route so it is not drawn under the toolbar/system bars.
    private var mapInsetPaddingPx = 0

    private val routingService = RoutingService(
        onStarted = { onRoutingStarted() },
        onCompleted = { routes, errorCode, _ -> onRoutingCompleted(routes, errorCode) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        mapInsetPaddingPx = resources.getDimension(R.dimen.padding_40).toInt()

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
            // SDK is not initialized here, so resolve the message directly (no SdkCall needed).
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            // Align the Magic Lane logo with system window insets on first map creation.
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
                calculateRoute()
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

    private fun onRoutingStarted() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun onRoutingCompleted(routes: ArrayList<Route>, errorCode: Int) {
        binding.progressBar.visibility = View.GONE

        when (errorCode) {
            GemError.NoError -> displayRoutesOnMap(routes)
            GemError.Cancel -> showDialog(getString(R.string.routing_cancelled))
            else -> {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                showDialog(getString(R.string.routing_error, message))
            }
        }
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark(SAN_FRANCISCO_NAME, SAN_FRANCISCO_LAT, SAN_FRANCISCO_LON),
            Landmark(SAN_JOSE_NAME, SAN_JOSE_LAT, SAN_JOSE_LON),
        )

        routingService.preferences.transportMode = ERouteTransportMode.Public
        routingService.calculateRoute(waypoints)
    }

    private fun displayRoutesOnMap(routes: ArrayList<Route>) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.presentRoutes(routes, edgeAreaInsets = getFreeSpaceInsetsRect())
    }

    // Positions the Magic Lane logo (and other map decorations) inside the visible map area,
    // clear of the toolbar and system window insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val left = insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    // Edge insets (margins from each screen edge) used to keep the presented route within the
    // visible map area: below the toolbar and clear of the system bars/cutout, plus a small padding.
    private fun getFreeSpaceInsetsRect(): Rect {
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

        val left = (insets?.left ?: 0) + mapInsetPaddingPx
        val right = (insets?.right ?: 0) + mapInsetPaddingPx
        val bottom = (insets?.bottom ?: 0) + mapInsetPaddingPx
        val top = (binding.toolbar.bottom.takeIf { it > 0 } ?: (insets?.top ?: 0)) + mapInsetPaddingPx

        return Rect(left, top, right, bottom)
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

    // Runs the block on the main thread only if the activity is still alive.
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
