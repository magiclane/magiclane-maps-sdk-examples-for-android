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
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.routesandnavigation.NavigationService
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar

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
                gemSurfaceView.getDefaultMapView()?.let { mapView ->
                    mapView.preferences()?.enableCursor(false)
                    navigationService.getNavigationRoute(this)?.let { route ->
                        mapView.presentRoute(route)
                        val remainingDistance = route.getTimeDistance(true)?.getTotalDistance() ?: 0
                        Toast.makeText(
                            this@MainActivity,
                            "Remaining distance $remainingDistance m",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    followCursor()
                }
            }
        }
    }

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            progressBar.visibility = View.GONE
        },

        postOnMain = true
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)

        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = {
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startNavigation()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
        }

        requestPermissions(this)
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        }

        startNavigation()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

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

    fun followCursor(following: Boolean = true) = SdkCall.execute {
        gemSurfaceView.getDefaultMapView()?.let { mapView ->
            if (following) {
                // Start following the cursor position using the provided animation.
                mapView.startFollowingPosition()
            } else {
                // Stop following the cursor if requested.
                mapView.stopFollowingPosition()
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startNavigation() = SdkCall.execute {
        val hasPermissions =
            PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (hasPermissions) {
            val destination = Landmark("Paris", 48.8566932, 2.3514616)

            // Cancel any navigation in progress.
            navigationService.cancelNavigation(navigationListener)
            // Start the new navigation.
            navigationService.startNavigation(
                destination,
                navigationListener,
                routingProgressListener,
            )
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
