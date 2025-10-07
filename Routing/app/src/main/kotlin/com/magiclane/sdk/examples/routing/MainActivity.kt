// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routing

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity() 
{
    // ---------------------------------------------------------------------------------------------
    
    private lateinit var progressBar: ProgressBar

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode)
            {
                GemError.NoError ->
                {
                    if (routes.size == 0) return@onCompleted

                    // Get the main route from the ones that were found.
                    displayRouteInfo(formatRouteName(routes[0]))
                }

                GemError.Cancel ->
                {
                    // The routing action was cancelled.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
                }

                else ->
                {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
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
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!GemSdk.initSdkWithDefaults(this))
        {
            // The SDK initialization was not completed.
            finish()
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }
        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }


    // ---------------------------------------------------------------------------------------------

    private fun displayRouteInfo(routeName: String)
    {
        findViewById<TextView>(R.id.text).text = routeName
    }

    // ---------------------------------------------------------------------------------------------

    private fun calculateRoute() = SdkCall.execute {
        val wayPoints = arrayListOf(
            Landmark("Frankfurt am Main", 50.11428, 8.68133),
            Landmark("Karlsruhe", 49.0069, 8.4037),
            Landmark("Munich", 48.1351, 11.5820)
        )

        routingService.calculateRoute(wayPoints)
    }

    // ---------------------------------------------------------------------------------------------

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
            for (point in wayPoints)
            {
                wayPointsText += (point.name + "\n")
            }
        }

        return@execute String.format(
            "$wayPointsText \n\n ${distTextPair.first} ${distTextPair.second} \n " +
                "${timeTextPair.first} ${timeTextPair.second}"
        )
    } ?: ""

    // ---------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(text: String)
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------