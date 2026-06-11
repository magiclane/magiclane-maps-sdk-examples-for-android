/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routing

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.routing.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routing.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.routing.databinding.RouteInfoRowBinding
import com.magiclane.sdk.examples.routing.databinding.RouteSectionHeaderBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val routingService = RoutingService(
        onStarted = {
            runOnAliveUi {
                binding.progressBar.visibility = View.VISIBLE
            }
        },

        onCompleted = { routes, errorCode, _ ->
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE

                when (errorCode) {
                    GemError.NoError -> routes.firstOrNull()?.let { displayRouteInfo(it) }
                    else -> showDialog(
                        getString(
                            R.string.routing_service_error,
                            SdkCall.runSynced {
                                GemError.getMessage(errorCode, this)
                            },
                        ),
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

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()

        val initError = GemSdk.initSdkWithDefaults(this)
        if (initError != GemError.NoError) {
            showDialog(
                getString(
                    R.string.sdk_initialization_failed,
                    SdkCall.runSynced { GemError.getMessage(initError, this) },
                ),
            ) {
                finish()
            }
            return
        }

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

    private fun registerSdkListeners() {
        // Self-cleared on first fire to avoid recalculating on subsequent map updates.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                calculateRoute()
            }
        }

        SdkSettings.onApiTokenRejected = {
            // The TOKEN you provided in the AndroidManifest.xml file was rejected.
            // Make sure you provide the correct value, or if you don't have a TOKEN,
            // check the magiclane.com website, sign up/sign in and generate one.
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
    }

    private fun displayRouteInfo(route: Route) {
        SdkCall.execute {
            val wayPoints = route.waypoints ?: return@execute
            val timeDistance = route.timeDistance ?: return@execute

            val distTextPair = GemUtil.getDistText(
                timeDistance.totalDistance,
                SdkSettings.unitSystem,
                bHighResolution = true,
            )
            val timeTextPair = GemUtil.getTimeText(
                timeDistance.totalTime + GemUtil.getTrafficEventsDelay(route, true),
            )

            val waypointNames = wayPoints.map { it.name ?: "" }
            val distText = "${distTextPair.first} ${distTextPair.second}"
            val timeText = "${timeTextPair.first} ${timeTextPair.second}"

            runOnAliveUi {
                binding.routeInfoContainer.removeAllViews()

                addSectionHeader(getString(R.string.route_waypoints))
                waypointNames.forEachIndexed { index, name ->
                    addInfoRow(
                        when (index) {
                            0 -> R.drawable.departure_waypoint
                            waypointNames.lastIndex -> R.drawable.destination_waypoint
                            else -> R.drawable.intermediate_waypoint
                        },
                        name,
                    )
                }

                addSectionHeader(getString(R.string.route_info))
                addInfoRow(R.drawable.distance, distText)
                addInfoRow(R.drawable.time_duration, timeText)
            }
        }
    }

    private fun addSectionHeader(title: String) {
        RouteSectionHeaderBinding.inflate(layoutInflater, binding.routeInfoContainer, true).apply {
            sectionTitle.text = title
        }
    }

    private fun addInfoRow(@DrawableRes iconRes: Int, text: String) {
        RouteInfoRowBinding.inflate(layoutInflater, binding.routeInfoContainer, true).apply {
            icon.setImageResource(iconRes)
            label.text = text
        }
    }

    private fun calculateRoute() = SdkCall.execute {
        val wayPoints = arrayListOf(
            Landmark("Frankfurt", 50.11428, 8.68133),
            Landmark("Karlsruhe", 49.0069, 8.4037),
            Landmark("Munich", 48.1351, 11.5820),
        )

        routingService.calculateRoute(wayPoints)
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
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }
}
