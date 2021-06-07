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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkUtil
import com.generalmagic.sdk.util.Util.postOnMain
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {
    private var mainRoute: Route? = null
    private lateinit var progressBar: ProgressBar

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, reason, _ ->
            progressBar.visibility = View.GONE

            when (reason) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    SdkCall.execute {
                        // Get the main route from the ones that were found.
                        mainRoute = if (routes.size > 0) routes[0] else null

                        // Get formatted name of the main route.
                        val routeName = mainRoute?.let { formatRouteName(it) } ?: ""
                        postOnMain { displayRouteInfo(routeName) }
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

        SdkSettings.onMapDataReady = {
            // Defines an action that should be done after the world map is updated.
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
            Landmark("Frankfurt am Main", Coordinates(50.11428, 8.68133)),
            Landmark("Karlsruhe", Coordinates(49.0069, 8.4037)),
            Landmark("Munich", Coordinates(48.1351, 11.5820))
        )

        routingService.calculateRoute(wayPoints)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun formatRouteName(route: Route): String {
        val timeDistance = route.getTimeDistance() ?: return ""
        val distInMeters = timeDistance.getTotalDistance()
        val timeInSeconds = timeDistance.getTotalTime()

        val distTextPair = SdkUtil.getDistText(
            distInMeters,
            SdkSettings.getUnitSystem(),
            bHighResolution = true
        )

        val timeTextPair = SdkUtil.getTimeText(timeInSeconds)

        var wayPointsText = ""
        route.getWaypoints()?.let { wayPoints ->
            for (point in wayPoints) {
                wayPointsText += (point.getName() + "\n")
            }
        }

        return String.format(
            "$wayPointsText \n\n ${distTextPair.first} ${distTextPair.second} \n " +
                "${timeTextPair.first} ${timeTextPair.second}"
        )
    }
}
