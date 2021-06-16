/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.ERouteTransportMode
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, reason, _ ->
            progressBar.visibility = View.GONE

            when (reason) {
                SdkError.NoError -> {
                    displayRoutesOnMap(routes)
                }

                SdkError.Cancel -> {
                    // The routing action was cancelled.
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    Toast.makeText(
                        this@MainActivity,
                        "Routing service error: ${reason.name}",
                        Toast.LENGTH_SHORT
                    ).show()
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

        SdkSettings.onMapDataReady = {
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            calculateRoute()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
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

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("San Francisco", 37.77903, -122.41991),
            Landmark("San Jose", 37.33619, -121.89058)
        )

        routingService.preferences.setTransportMode(ERouteTransportMode.Public)
        routingService.calculateRoute(waypoints)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun displayRoutesOnMap(routes: ArrayList<Route>) = SdkCall.execute {
        gemSurfaceView.getDefaultMapView()?.presentRoutes(routes)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
