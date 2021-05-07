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

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.examples.util.Util
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.Util.postOnMain

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private var mainRoute: Route? = null
    private lateinit var progressBar: ProgressBar

    private val routingService = RoutingService()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun displayRouteInfo(routeName: String) {
        findViewById<TextView>(R.id.text).text = routeName
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun calculateRoute() {
        if (!SdkInitHelper.isMapReady) return

        val wayPoints = arrayListOf(
            Landmark("Frankfurt am Main", Coordinates(50.11428, 8.68133)),
            Landmark("Karlsruhe", Coordinates(49.0069, 8.4037)),
            Landmark("Munich", Coordinates(48.1351, 11.5820))
        )

        routingService.calculateRoute(wayPoints)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)

        /// GENERAL MAGIC
        routingService.onStarted = {
            progressBar.visibility = View.VISIBLE

        }

        routingService.onCompleted = onCompleted@{ routes, reason, _ ->
            progressBar.visibility = View.GONE

            when (val gemError = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    SdkCall.execute {
                        // Get the main route from the ones that were found.
                        mainRoute = if (routes.size > 0) {
                            routes[0]
                        } else {
                            null
                        }

                        // Get formatted name of the main route.
                        val routeName = mainRoute?.let { Util.formatRouteName(it) } ?: ""
                        postOnMain { displayRouteInfo(routeName) }
                    }
                }

                SdkError.Cancel -> {
                    // The routing action was cancelled.
                    return@onCompleted
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    Toast.makeText(
                        this@MainActivity,
                        "Routing service error: ${gemError.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val calcDefaultRoute = calcDefaultRoute@{
            if (!SdkInitHelper.isMapReady) return@calcDefaultRoute

            // Defines an action that should be done when the world map is ready. (Updated/ loaded)
            SdkCall.execute { calculateRoute() }
        }

        SdkInitHelper.onMapReady = {
            // Defines an action that should be done after the world map is updated.
            calcDefaultRoute()
        }

        SdkInitHelper.onNetworkConnected = {
            // Defines an action that should be done after the network is connected.
            calcDefaultRoute()
        }

        SdkInitHelper.onCancel = {
            // Defines what should be executed when the SDK initialization is cancelled.
            SdkCall.execute { routingService.cancelRoute() }
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            // The SDK initialization was not completed.
            finish()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        SdkInitHelper.deinit()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        terminateApp(this)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
