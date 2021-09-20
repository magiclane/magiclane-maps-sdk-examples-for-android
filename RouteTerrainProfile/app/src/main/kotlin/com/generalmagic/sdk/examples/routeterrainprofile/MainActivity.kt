/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.routeterrainprofile

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.RouteTerrainProfile
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, reason, _ ->
            progressBar.visibility = View.GONE

            when (reason) {
                SdkError.NoError -> {
                    if (routes.isNotEmpty()) {
                        val route = routes[0]
                        // Get the terrain profile of the route.
                        val terrain = SdkCall.execute {
                            route.terrainProfile
                        }

                        if (terrain != null) {
                            // The route has a terrain profile so we can display it.
                            displayTerrainInfo(terrain)
                        } else {
                            Toast.makeText(this, "No RouteTerrainProfile!", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
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

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done after the world map is ready.
            calculateRoute()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this, "TOKEN REJECTED", Toast.LENGTH_LONG).show()
        }

        if (!GemSdk.initSdkWithDefaults(this)) {
            // The SDK initialization was not completed.
            finish()
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

    @SuppressLint("SetTextI18n")
    private fun displayTerrainInfo(terrain: RouteTerrainProfile) {
        /*
        The RouteTerrainInfo class contains a lot of information, but we will present here
        just the maximum/ minimum elevation, elevation at certain point, the total number of meters ascending/ going down
        and the number of climbing sections in the selected route.
        If you want to display a chart representing the elevation of the route/ of the upcoming part of the route
        you can use the method "getElevationSamples(samplesCount)" specifying the number of samples that you want.
        For more methods and information about the route terrain profile, please check the documentation.
         */
        var maxElv = .0f
        var minElv = .0f
        var elevationAt = .0f
        var totalUp = .0f
        var totalDown = .0f
        var climbingSections = 0

        SdkCall.execute {
            maxElv = terrain.maxElevation
            minElv = terrain.minElevation
            elevationAt = terrain.getElevation(1000) // 1 KM
            totalUp = terrain.totalUp
            totalDown = terrain.totalDown
            climbingSections = terrain.climbSections?.size ?: 0
        }

        findViewById<TextView>(R.id.text).text =
            "Details: \nMin. Elevation = $minElv m, " +
                "\nMax. Elevation = $maxElv m, " +
                "\nElevation after 1KM = $elevationAt m," +
                "\nTotal Up = $totalUp m, \n" +
                "Total Down = $totalDown m \n\n\n" +
                "Number of Climbing Section: \n$climbingSections"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Bucharest", 44.4268, 26.1025),
            Landmark("Brasov", 45.6427, 25.5887)
        )

        /* 
        Setting this (setBuildTerrainProfile(true)) to the Routing Service preferences is mandatory if you
        want to get data related to the route terrain profile, otherwise the terrain profile would not be calculated
        at the routing process.
         */
        routingService.preferences.buildTerrainProfile = true
        routingService.calculateRoute(waypoints)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
