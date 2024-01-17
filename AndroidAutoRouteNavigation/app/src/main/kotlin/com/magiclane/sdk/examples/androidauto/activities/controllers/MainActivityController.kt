/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.androidauto.activities.controllers

import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.androidauto.activities.MainActivity
import com.magiclane.sdk.examples.androidauto.app.AppProcess
import com.magiclane.sdk.examples.androidauto.app.REQUEST_PERMISSIONS
import com.magiclane.sdk.examples.androidauto.services.NavigationInstance
import com.magiclane.sdk.examples.androidauto.services.RoutingInstance
import com.magiclane.sdk.examples.androidauto.util.Util
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util.postOnMain
import kotlin.system.exitProcess

class MainActivityController(val context: MainActivity) {
    /*
    Define a navigation listener that will receive notifications from the
    navigation service.
    We will use just the onNavigationStarted method, but for more available
    methods you should check the documentation.
     */
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            updateMapView()
        },
        onDestinationReached = {
            updateMapView()
        },
        onNavigationError = {
            updateMapView()
        }
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            context.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            context.progressBar.visibility = View.GONE
        },

        postOnMain = true
    )

    fun onCreate() {
        AppProcess.init(context)

        NavigationInstance.listeners.add(navigationListener)
        RoutingInstance.listeners.add(routingProgressListener)
    }

    fun onDestroy() {
        NavigationInstance.listeners.remove(navigationListener)
        RoutingInstance.listeners.remove(routingProgressListener)
    }

    fun onDefaultMapViewCreated() {
        postOnMain {
            updateMapView()
        }
    }

    private fun updateMapView() = SdkCall.execute {
        if (NavigationInstance.service.isNavigationActive()) {
            context.mapView?.let { mapView ->
                mapView.preferences?.enableCursor = false

                NavigationInstance.currentRoute?.let { route ->
                    mapView.presentRoute(route)

                    Toast.makeText(
                        context,
                        "Remaining distance ${NavigationInstance.remainingDistance} m",
                        Toast.LENGTH_LONG
                    ).show()
                }

                mapView.followPosition()
            }
        } else {
            context.mapView?.hideRoutes()
            context.mapView?.deactivateAllHighlights()
            context.mapView?.followPosition()
        }
    }

    fun onBackPressed() {
        context.finish()
        exitProcess(0)
    }

    // ---------------------------------------------------------------------------------------------

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ) {
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                context.finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            // Notice permission status had changed
            PermissionsHelper.onRequestPermissionsResult(context, REQUEST_PERMISSIONS, grantResults)
        }
    }

    fun onNewIntent(intent: Intent) {
        val uriString = intent.dataString ?: return
        if (Util.isGeoIntent(uriString))
            AppProcess.handleGeoUri(uriString)
    }
}