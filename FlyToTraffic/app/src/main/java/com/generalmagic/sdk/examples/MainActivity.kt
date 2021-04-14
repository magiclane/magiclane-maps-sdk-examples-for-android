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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteTrafficEvent
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.Util.Companion.postOnMain

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private var mainMapView: MapView? = null
    lateinit var progressBar: ProgressBar

    private val routingService = RoutingService()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun flyToTraffic(trafficEvent: RouteTrafficEvent) {
        val animation = Animation()
        animation.setType(EAnimation.Fly)

        // Center the map on a specific traffic event using the provided animation.
        mainMapView?.centerOnRouteTrafficEvent(trafficEvent, -1, Rect(), animation)
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

        routingService.onCompleted = onCompleted@{ routes, reason, hint ->
            progressBar.visibility = View.GONE

            when (val gemError = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    SdkCall.execute {
                        // Get the main route from the ones that were found.
                        val mainRoute: Route? = if (routes.size > 0) {
                            routes[0]
                        } else {
                            null
                        }

                        // Get the traffic events along the main route.
                        val trafficEventsLists = mainRoute?.getTrafficEvents() ?: ArrayList()
                        // Get the first traffic event from the main route.
                        val trafficEvent = if (trafficEventsLists.size == 0) {
                            null
                        } else {
                            trafficEventsLists[0]
                        }

                        if (trafficEvent == null || mainRoute == null) {
                            postOnMain {
                                Toast.makeText(
                                    this@MainActivity,
                                    "No traffic events!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@execute
                        }

                        // Add the main route to the map so it can be displayed.
                        mainMapView?.preferences()?.routes()?.add(mainRoute, true)
                        flyToTraffic(trafficEvent)
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

            SdkCall.execute {
                val waypoints = arrayListOf(
                    Landmark("London", Coordinates(51.5073204, -0.1276475)),
                    Landmark("Paris", Coordinates(48.8566932, 2.3514616))
                )

                routingService.calculateRoute(waypoints)
            }
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

        val mapSurface = findViewById<GemSurfaceView>(R.id.gem_surface)
        mapSurface.onScreenCreated = { screen ->
            // Defines an action that should be done after the screen is created.
            SdkCall.execute {
                /* 
                Define a rectangle in which the map view will expand.
                Predefined value of the offsets is 0.
                Value 1 means the offset will take 100% of available space.
                 */
                val mainViewRect = RectF(0.0f, 0.0f, 1.0f, 1.0f)
                // Produce a map view and establish that it is the main map view.
                val mapView = MapView.produce(screen, mainViewRect)
                mainMapView = mapView
            }
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
