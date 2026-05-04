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

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, gemError, _ ->
            binding.progressBar.visibility = View.GONE

            when (gemError) {
                GemError.NoError ->
                    {
                        if (routes.isEmpty()) return@onCompleted

                        val route = routes[0]

                        // Get Traffic events from the main route.
                        val events = SdkCall.execute { route.trafficEvents }

                        if (events.isNullOrEmpty()) {
                            showDialog(getString(R.string.no_traffic_events))
                            return@onCompleted
                        }

                        SdkCall.execute {
                            // Add the main route to the map so it can be displayed.
                            binding.gemSurfaceView.mapView?.presentRoute(route, centerMapView = false)

                            flyToTraffic(events[0])
                        }
                    }
                else ->
                    {
                        // There was a problem at computing the routing operation.
                        showDialog(getString(R.string.routing_error, GemError.getMessage(gemError, this)))
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

                SdkCall.execute {
                    val waypoints = arrayListOf(
                        Landmark("London", 51.5073204, -0.1276475),
                        Landmark("Paris", 48.8566932, 2.3514616),
                    )

                    routingService.calculateRoute(waypoints)
                }
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

    private fun flyToTraffic(trafficEvent: RouteTrafficEvent) = SdkCall.execute {
        // Center the map on a specific traffic event using the provided animation.
        binding.gemSurfaceView.mapView?.centerOnRouteTrafficEvent(
            trafficEvent,
            rc = getFreeSpaceRect(),
            animation = Animation(EAnimation.Linear, 900),
            viewAngle = 0.0,
        )
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

    fun getFreeSpaceRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

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
}
