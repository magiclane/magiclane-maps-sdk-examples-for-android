/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.routing

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.GemUtil
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    if (routes.size == 0) return@onCompleted

                    // Get the main route from the ones that were found.
                    displayRouteInfo(formatRouteName(routes[0]))
                }

                GemError.Cancel -> {
                    // The routing action was cancelled.
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    Toast.makeText(
                        this@MainActivity,
                        "Routing service error: ${GemError.getMessage(errorCode)}",
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

            // Defines an action that should be done after the world map is updated.
            calculateRoute()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/sign in and generate one. 
             */
            Toast.makeText(this, "TOKEN REJECTED", Toast.LENGTH_LONG).show()
        }

        if (!GemSdk.initSdkWithDefaults(this)) {
            // The SDK initialization was not completed.
            finish()
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to the internet!", Toast.LENGTH_LONG).show()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        finish()
        exitProcess(0)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun displayRouteInfo(routeName: String) {
        findViewById<TextView>(R.id.text).text = routeName
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun calculateRoute() = SdkCall.execute {
        val wayPoints = arrayListOf(
            Landmark("Frankfurt am Main", 50.11428, 8.68133),
            Landmark("Karlsruhe", 49.0069, 8.4037),
            Landmark("Munich", 48.1351, 11.5820)
        )

        routingService.calculateRoute(wayPoints)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun formatRouteName(route: Route): String = SdkCall.execute {
        val timeDistance = route.timeDistance ?: return@execute ""
        val distInMeters = timeDistance.totalDistance
        val timeInSeconds = timeDistance.totalTime

        val distTextPair = GemUtil.getDistText(
            distInMeters,
            SdkSettings.unitSystem,
            bHighResolution = true
        )

        val timeTextPair = GemUtil.getTimeText(timeInSeconds)

        var wayPointsText = ""
        route.waypoints?.let { wayPoints ->
            for (point in wayPoints) {
                wayPointsText += (point.name + "\n")
            }
        }

        return@execute String.format(
            "$wayPointsText \n\n ${distTextPair.first} ${distTextPair.second} \n " +
                "${timeTextPair.first} ${timeTextPair.second}"
        )
    } ?: ""
}
