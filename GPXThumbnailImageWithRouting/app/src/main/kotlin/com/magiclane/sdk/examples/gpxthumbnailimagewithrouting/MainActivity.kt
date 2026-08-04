/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxthumbnailimagewithrouting

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EViewCameraTransitionStatus
import com.magiclane.sdk.d3scene.EViewDataTransitionStatus
import com.magiclane.sdk.examples.gpxthumbnailimagewithrouting.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.gpxthumbnailimagewithrouting.databinding.DialogLayoutBinding
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RouteRenderSettings
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var gemOffscreenSurfaceView: GemOffscreenSurfaceView

    private var screenshotTaken = false

    private val thumbnailWidth by lazy { resources.getDimension(R.dimen.thumbnail_width).toInt() }
    private val thumbnailHeight by lazy { resources.getDimension(R.dimen.thumbnail_height).toInt() }
    private val padding by lazy { resources.getDimension(R.dimen.big_padding).toInt() }

    // Routing service with callbacks for the route calculation lifecycle.
    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.calculating_route)
        },

        onCompleted = onCompleted@{ routes, errorCode, _ ->
            when (errorCode) {
                GemError.NoError -> {
                    if (routes.isEmpty()) return@onCompleted

                    binding.statusText.text = getString(R.string.route_calculation_completed)

                    // Switch to the SDK thread to access the map view and present the route.
                    SdkCall.execute {
                        gemOffscreenSurfaceView.mapView?.let { mapView ->

                            val routeRenderSettings = RouteRenderSettings().also {
                                it.innerSize = 1.0
                            }
                            mapView.presentRoute(
                                routes[0],
                                edgeAreaInsets = Rect(padding, padding, padding, padding),
                                routeRenderSettings = routeRenderSettings,
                                animation = Animation(EAnimation.None)
                            )

                            // Wait for the map to finish rendering before capturing the screenshot.
                            mapView.onViewRendered = onViewRendered@{ tivStatus, camStatus ->
                                if (screenshotTaken) return@onViewRendered

                                if (tivStatus == EViewDataTransitionStatus.Complete &&
                                    camStatus == EViewCameraTransitionStatus.Stationary
                                ) {
                                    Util.postOnMain {
                                        binding.statusText.text = getString(R.string.taking_screenshot)
                                    }
                                    gemOffscreenSurfaceView.takeScreenshot { bitmap ->
                                        Util.postOnMain {
                                            binding.apply {
                                                mapThumbnailImageView.setImageBitmap(bitmap)
                                                progressBar.isVisible = false
                                                statusText.text = getString(R.string.screenshot_taken)
                                            }
                                        }
                                        screenshotTaken = true
                                        gemOffscreenSurfaceView.destroy()
                                    }
                                    mapView.onViewRendered = null
                                }
                            }
                        }
                    }
                }
                else -> {
                    val errorMessage = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                    runOnAliveUi {
                        binding.progressBar.isVisible = false
                        showDialog(getString(R.string.routing_error, errorMessage))
                    }
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val error = GemSdk.initSdkWithDefaults(this)
        if (error != GemError.NoError) {
            val errorMessage = SdkCall.runSynced { GemError.getMessage(error, this) }
            showDialog(getString(R.string.sdk_initialization_failed, errorMessage)) {
                finish()
                exitProcess(0)
            }
            return
        }

        gemOffscreenSurfaceView = GemOffscreenSurfaceView(
            thumbnailWidth,
            thumbnailHeight,
            resources.displayMetrics.densityDpi,
            onDefaultMapViewCreated = { mapView ->
                mapView.preferences?.apply {
                    mapLabelsFading = false
                }
            },
        )

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

    // Registers SDK-level callbacks for map data readiness and token validation.
    private fun registerSdkListeners() {
        binding.statusText.text = getString(R.string.waiting_for_data)

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // Unregister immediately so the callback fires only once.
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                binding.statusText.text = getString(R.string.map_data_ready)
                calculateRouteFromGPX()
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
    }

    // Loads the bundled GPX file and starts a car route calculation along its track.
    private fun calculateRouteFromGPX() = SdkCall.execute {
        val input = applicationContext.resources.assets.open("gpx/test_route.gpx")
        val track = Path.produceWithGpx(input) ?: return@execute

        val error = routingService.calculateRoute(track, ERouteTransportMode.Car)
        if (error != GemError.NoError) {
            val errorMessage = GemError.getMessage(error, this)
            runOnAliveUi { showDialog(getString(R.string.routing_error, errorMessage)) }
        }
    }

    // Shows a non-dismissable bottom-sheet error dialog with an optional dismiss callback.
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
