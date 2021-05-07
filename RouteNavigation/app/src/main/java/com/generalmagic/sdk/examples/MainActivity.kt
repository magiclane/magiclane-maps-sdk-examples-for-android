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

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.d3scene.*
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.isMapReady
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util.postOnMain

class MainActivity : AppCompatActivity() {
    private var mapView: MapView? = null
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
                mapView?.preferences()?.enableCursor(false)
                navigationService.getNavigationRoute(this)
                    ?.let { mapView?.preferences()?.routes()?.add(it, true) }
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
                mapView?.stopFollowingPosition()
                return@execute
            }

            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)
            animation.setDuration(900)

            // Start following the cursor position using the provided animation.
            mapView?.startFollowingPosition(animation)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startNavigation() {
        if (!isMapReady) return

        val hasPermissions =
            PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (!hasPermissions) {
            return
        }

        val waypoints = arrayListOf(
            Landmark("Paris", Coordinates(48.8566932, 2.3514616))
        )

        navigationService.startNavigation(
            waypoints,
            RoutePreferences(),
            navigationListener,
            routingProgressListener,
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
                val mainMapView = MapView.produce(screen, mainViewRect)
                mapView = mainMapView
            }
        }

        val calcDefaultRoute = calcDefaultRoute@{
            if (!isMapReady) return@calcDefaultRoute
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            SdkCall.execute {
                startNavigation()
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
            SdkCall.execute { navigationService.cancelNavigation(navigationListener) }
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            finish()
        }

        requestPermissions(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        SdkInitHelper.deinit()
    }

    override fun onBackPressed() {
        terminateApp(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode != REQUEST_PERMISSIONS) return

        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                terminateApp(this)
                return
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            SdkCall.execute {
                startNavigation()
            }
        }, 500)
    }

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS, activity, permissions.toTypedArray()
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}
