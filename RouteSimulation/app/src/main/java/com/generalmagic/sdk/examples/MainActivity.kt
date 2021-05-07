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
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.isMapReady
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.d3scene.*
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util.postOnMain

class MainActivity : AppCompatActivity() {
    private var mainMapView: MapView? = null
    lateinit var progressBar: ProgressBar

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    /* 
    Define a navigation listener that will receive notifications from the
    navigation service.
    We will use just the onNavigationStarted method, but for more available
    methods you should check the documentation.
     */
    private val navigationListener = object : NavigationListener() {
        override fun onNavigationStarted() {
            SdkCall.execute {
                mainMapView?.preferences()?.enableCursor(false)
                navigationService.getNavigationRoute(this)
                    ?.let { mainMapView?.preferences()?.routes()?.add(it, true) }
                followCursor()
            }
        }
    }

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) = postOnMain {
            progressBar.visibility = View.GONE
        }

        override fun notifyStart(hasProgress: Boolean) = postOnMain {
            progressBar.visibility = View.VISIBLE
        }
    }

    fun followCursor(following: Boolean = true) {
        SdkCall.execute {
            if (!following) {
                // Stop following the cursor if requested.
                mainMapView?.stopFollowingPosition()
                return@execute
            }

            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)
            animation.setDuration(900)

            // Start following the cursor position using the provided animation.
            mainMapView?.startFollowingPosition(animation)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startSimulation() {
        val waypoints = arrayListOf(
            Landmark("London", Coordinates(51.5073204, -0.1276475)),
            Landmark("Paris", Coordinates(48.8566932, 2.3514616))
        )

        navigationService.startSimulation(
            waypoints,
            RoutePreferences(),
            navigationListener,
            routingProgressListener,
            1F
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)

        /// GENERAL MAGIC
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

        val calcDefaultRoute = calcDefaultRoute@{
            if (!isMapReady) return@calcDefaultRoute
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            SdkCall.execute {
                startSimulation()
            }
        }

        SdkInitHelper.onNetworkConnected = {
            // Defines an action that should be done after the network is connected.
            calcDefaultRoute()
        }

        SdkInitHelper.onMapReady = {
            // Defines an action that should be done after the world map is updated.
            calcDefaultRoute()
        }

        SdkInitHelper.onCancel = {
            // Defines what should be executed when the SDK initialization is cancelled.
            SdkCall.execute { navigationService.cancelNavigation(navigationListener) }
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            // The SDK initialization was not completed.
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        SdkInitHelper.deinit()
    }

    override fun onBackPressed() {
        terminateApp(this)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
