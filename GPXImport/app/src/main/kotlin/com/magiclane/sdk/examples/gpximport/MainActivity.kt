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
                    // There was a problem at computing the routing operation.
                    showDialog(getString(R.string.routing_error, GemError.getMessage(errorCode, this)))
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inflate = resources.getDimension(R.dimen.padding_40).toInt()

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

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

        val mapView = binding.gemSurfaceView.mapView ?: return@execute

        // Set the line color to red and display the path on the map.
        val lineColor = Rgba.red()
        mapView.presentPath(track, lineColor, lineColor, 0.0, 0.6, false)

        // Set the transport mode to bike and calculate the route.
        routingService.calculateRoute(track, ERouteTransportMode.Bicycle)
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

    fun getInsetsRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val left = (insets?.left ?: 0) + inflate
        val right = (insets?.right ?: 0) + inflate
        val topInset = (insets?.top ?: 0) + inflate
        val toolbarBottom = (binding.toolbar.bottom.takeIf { it > 0 } ?: 0) + inflate
        val top = maxOf(topInset, toolbarBottom)
        val bottom = (insets?.bottom ?: 0) + inflate

        return Rect(left, top, right, bottom)
    }
}
