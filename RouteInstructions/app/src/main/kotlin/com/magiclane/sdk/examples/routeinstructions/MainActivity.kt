/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routeinstructions

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.routeinstructions.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routeinstructions.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    if (routes.isEmpty()) return@onCompleted

                    // Get the main route from the ones that were found.
                    displayRouteInstructions(routes[0])
                }
                else -> {
                    // There was a problem at computing the routing operation.
                    showDialog(
                        getString(R.string.routing_error, SdkCall.runSynced { GemError.getMessage(errorCode, this) }),
                    )
                    EspressoIdlingResource.decrement()
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force light content in the status bar (white icons/text).
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val error = GemSdk.initSdkWithDefaults(this)
        if (error != GemError.NoError) {
            val errorMessage =
                getString(R.string.sdk_initialization_failed, SdkCall.runSynced { GemError.getMessage(error, this) })
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
            return
        }

        EspressoIdlingResource.increment()
        binding.listView.also {
            it.layoutManager = LinearLayoutManager(this)
            it.addItemDecoration(
                DividerItemDecoration(this, (it.layoutManager as LinearLayoutManager).orientation),
            )
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun startCalculateRoute() = SdkCall.execute {
        val wayPoints = arrayListOf(
            Landmark("Berlin", 52.521944, 13.413056),
            Landmark("Poznan", 52.406374, 16.925168),
            Landmark("Copenhagen", 55.676097, 12.568337),
        )
        routingService.calculateRoute(wayPoints)
    }

    private fun displayRouteInstructions(route: Route) {
        // Get the instructions from the route.
        val imageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        binding.listView.adapter = RouteTimelineAdapter(this, route, imageSize, isDarkThemeOn())
        EspressoIdlingResource.decrement()
    }

    private fun isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun registerSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                startCalculateRoute()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
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

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource =
        CountingIdlingResource("ApplyMapStyleInstrumentedTestsIdlingResource")

    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
