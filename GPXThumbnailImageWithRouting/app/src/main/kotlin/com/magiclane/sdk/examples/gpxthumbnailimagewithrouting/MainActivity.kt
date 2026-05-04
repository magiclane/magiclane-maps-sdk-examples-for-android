/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxthumbnailimagewithrouting

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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

    private val thumbnailWidth by lazy {
        resources.getDimension(R.dimen.thumbnail_width).toInt()
    }

    private val thumbnailHeight by lazy {
        resources.getDimension(R.dimen.thumbnail_height).toInt()
    }

    private val padding by lazy {
        resources.getDimension(R.dimen.big_padding).toInt()
    }

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

                    SdkCall.execute {
                        gemOffscreenSurfaceView.mapView?.let { mapView ->
                            mapView.onViewRendered = onViewRendered@{ tivStatus, camStatus ->
                                if (screenshotTaken) return@onViewRendered

                                if ((tivStatus == EViewDataTransitionStatus.Complete) &&
                                    (camStatus == EViewCameraTransitionStatus.Stationary)
                                ) {
                                    Util.postOnMain {
                                        binding.statusText.text = getString(R.string.taking_screenshot)
                                    }
                                    gemOffscreenSurfaceView.takeScreenshot { bitmap ->
                                        Util.postOnMain {
                                            binding.apply {
                                                mapThumbnailImageView.setImageBitmap(bitmap)
                                                progressBar.isVisible = false
                                                statusText.text = getString(
                                                    R.string.screenshot_taken,
                                                )
                                            }
                                        }
                                        screenshotTaken = true
                                        gemOffscreenSurfaceView.destroy()
                                    }

                                    mapView.onViewRendered = null
                                }
                            }

                            val routeRenderSettings = RouteRenderSettings().also {
                                it.innerSize = 1.0
                            }
                            mapView.presentRoute(
                                routes[0],
                                edgeAreaInsets = Rect(padding, padding, padding, padding),
                                routeRenderSettings = routeRenderSettings,
                            )
                        }
                    }
                }
                else -> {
                    binding.progressBar.isVisible = false
                    // There was a problem at computing the routing operation.
                    showDialog(getString(R.string.routing_error, GemError.getMessage(errorCode, this)))
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        val error = GemSdk.initSdkWithDefaults(this)
        if (error != GemError.NoError) {
            Util.postOnMain {
                showDialog(getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        gemOffscreenSurfaceView = GemOffscreenSurfaceView(thumbnailWidth, thumbnailHeight, resources.displayMetrics.densityDpi, onDefaultMapViewCreated = { mapView ->
            mapView.preferences?.apply {
                mapLabelsFading = false
            }
        })

        binding.statusText.text = getString(R.string.waiting_for_data)

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                binding.statusText.text = getString(R.string.map_data_ready)

                calculateRouteFromGPX()
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected_message))
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun calculateRouteFromGPX() = SdkCall.execute {
        val gpxAssetsFilename = "gpx/test_route.gpx"

        // Opens GPX input stream.
        val input = applicationContext.resources.assets.open(gpxAssetsFilename)

        // Produce a Path based on the data in the buffer.
        val track = Path.produceWithGpx(input) ?: return@execute

        // Set the transport mode to car and calculate the route.
        val error = routingService.calculateRoute(track, ERouteTransportMode.Car)
        if (error != GemError.NoError) {
            Util.postOnMain {
                showDialog(getString(R.string.routing_error, GemError.getMessage(error, this)))
            }
        }
    }

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
