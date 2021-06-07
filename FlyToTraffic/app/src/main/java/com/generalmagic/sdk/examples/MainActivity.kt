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
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteTrafficEvent
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
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

        onCompleted = { routes, gemError, _ ->
            progressBar.visibility = View.GONE

            when (gemError) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    SdkCall.execute {
                        // Get the main route from the ones that were found.
                        val mainRoute: Route? = if (routes.size > 0) routes[0] else null

                        // Get the traffic events along the main route.
                        val trafficEventsLists = mainRoute?.getTrafficEvents() ?: ArrayList()
                        // Get the first traffic event from the main route.
                        val trafficEvent =
                            if (trafficEventsLists.size == 0) null else trafficEventsLists[0]

                        if (trafficEvent != null && mainRoute != null) {
                            // Add the main route to the map so it can be displayed.
                            gemSurfaceView.getDefaultMapView()
                                ?.presentRoutes(arrayListOf(mainRoute), displayLabel = false)

                            flyToTraffic(trafficEvent)
                        } else {
                            showToast("No traffic events!")
                        }
                    }
                }

                SdkError.Cancel -> {
                    // The routing action was cancelled.
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    showToast("Routing service error: ${gemError.name}")
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
            SdkCall.execute {
                val waypoints = arrayListOf(
                    Landmark("London", Coordinates(51.5073204, -0.1276475)),
                    Landmark("Paris", Coordinates(48.8566932, 2.3514616))
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

    private fun flyToTraffic(trafficEvent: RouteTrafficEvent) = SdkCall.execute {
        // Center the map on a specific traffic event using the provided animation.
        gemSurfaceView.getDefaultMapView()?.centerOnRouteTrafficEvent(
            trafficEvent, animation = Animation(EAnimation.AnimationLinear)
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showToast(text: String) = postOnMain {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
