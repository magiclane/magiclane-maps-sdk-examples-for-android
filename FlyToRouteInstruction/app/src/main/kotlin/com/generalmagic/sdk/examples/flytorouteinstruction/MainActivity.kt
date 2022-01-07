/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.flytorouteinstruction

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.RouteInstruction
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.generalmagic.sdk.util.Util.postOnMain
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, gemError, _ ->
            progressBar.visibility = View.GONE

            when (gemError) {
                GemError.NoError -> {
                    if (routes.size == 0) return@onCompleted

                    // Get the main route from the ones that were found.
                    val route = routes[0]

                    SdkCall.execute {
                        val instructions = route.instructions
                        if (instructions.size == 0) {
                            showToast("No route instructions found!")
                            return@execute
                        }

                        // Get an instruction from the main route.
                        val instruction = route.instructions.let {
                            if (it.size >= 4) it[3] else it[0]
                        }

                        // Add the main route to the map so it can be displayed.
                        gemSurfaceView.mapView?.presentRoute(route)

                        flyToInstruction(instruction)
                    }
                }

                GemError.Cancel -> {
                    // The routing action was cancelled.
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    showToast("Routing service error: ${GemError.getMessage(gemError)}")
                }
            }
        }
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            SdkCall.execute {
                val waypoints = arrayListOf(
                    Landmark("London", 51.5073204, -0.1276475),
                    Landmark("Paris", 48.8566932, 2.3514616)
                )

                routingService.calculateRoute(waypoints)
            }
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            showToast("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        finish()
        exitProcess(0)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun flyToInstruction(instruction: RouteInstruction) = SdkCall.execute {
        // Center the map on a specific route instruction using the provided animation.
        gemSurfaceView.mapView?.centerOnRouteInstruction(instruction)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showToast(text: String) = postOnMain {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
