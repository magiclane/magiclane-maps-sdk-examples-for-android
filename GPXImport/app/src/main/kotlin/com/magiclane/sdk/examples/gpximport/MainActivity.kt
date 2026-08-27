/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpximport

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
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.gpximport.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.gpximport.databinding.DialogLayoutBinding
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    private var inflate = 0

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    if (!routes.isEmpty()) {
                        val route = routes[0]
                        SdkCall.execute {
                            binding.gemSurfaceView.mapView?.presentRoute(
                                route,
                                displayBubble = true,
                                displayRouteName = true,
                                displayTrafficIcon = false,
                                displayFerryIcon = false,
                                displayTollIcon = false,
                                edgeAreaInsets = getInsetsRect(),
                            )
                        }
                    }
                }
                else -> {
                    val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                    runOnAliveUi { showDialog(getString(R.string.routing_error, message)) }
                }
            }
        },
    )

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

        // Re-align the logo whenever the surface is resized (e.g. rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Wait for the worldwide road map to be ready before calculating the route from GPX.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                calculateRouteFromGPX()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
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

    private fun calculateRouteFromGPX() = SdkCall.execute {
        val gpxAssetsFilename = "gpx/test_route.gpx"

        // Opens GPX input stream.
        val input = applicationContext.resources.assets.open(gpxAssetsFilename)

        // Produce a Path based on the input stream data
        val track = Path.produceWithGpx(input) ?: return@execute

        val mapView = binding.gemSurfaceView.mapView ?: return@execute

        // Set the line color to red and display the path on the map.
        val lineColor = Rgba.red()
        mapView.presentPath(track, lineColor, lineColor, 0.0, 0.6, false)

        // Set the transport mode to bike and calculate the route.
        val error = routingService.calculateRoute(track, ERouteTransportMode.Bicycle)
        if (GemError.isError(error)) {
            val message = GemError.getMessage(error, this)
            runOnAliveUi { showDialog(getString(R.string.routing_error, message)) }
        }
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

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    fun getInsetsRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(SYSTEM_INSET_TYPES)

        val left = (insets?.left ?: 0) + inflate
        val right = (insets?.right ?: 0) + inflate
        val topInset = (insets?.top ?: 0) + inflate
        val toolbarBottom = (binding.toolbar.bottom.takeIf { it > 0 } ?: 0) + inflate
        val top = maxOf(topInset, toolbarBottom)
        val bottom = (insets?.bottom ?: 0) + inflate

        return Rect(left, top, right, bottom)
    }
}
