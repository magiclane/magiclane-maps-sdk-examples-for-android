/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.activities.controllers

import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.activities.MainActivity
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AppProcess
import com.magiclane.sdk.examples.androidautoroutenavigation.app.REQUEST_PERMISSIONS
import com.magiclane.sdk.examples.androidautoroutenavigation.services.NavigationInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.services.RoutingInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.util.Util
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util.postOnMain
import kotlin.system.exitProcess

class MainActivityController(val context: MainActivity) {
    // Keeps the phone map view in sync with the current navigation state.
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = { updateMapView() },
        onDestinationReached = { updateMapView() },
        onNavigationError = { updateMapView() },
    )

    // Shows/hides the progress bar while a route is being calculated.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            context.binding.progressBar.visibility = View.VISIBLE
        },
        onCompleted = { _, _ ->
            context.binding.progressBar.visibility = View.GONE
        },
        postOnMain = true,
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
        postOnMain { updateMapView() }
    }

    private fun updateMapView() = SdkCall.execute {
        val mapView = context.mapView ?: return@execute

        if (NavigationInstance.service.isNavigationActive()) {
            mapView.preferences?.enableCursor = false
            NavigationInstance.currentRoute?.let { route ->
                mapView.presentRoute(route)
                Toast.makeText(
                    context,
                    context.getString(R.string.remaining_distance, NavigationInstance.remainingDistance),
                    Toast.LENGTH_LONG,
                ).show()
            }
            mapView.followPosition()
        } else {
            mapView.hideRoutes()
            mapView.deactivateAllHighlights()
            mapView.followPosition()
        }
    }

    fun onBackPressed() {
        context.finish()
        exitProcess(0)
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_PERMISSIONS) return

        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                context.finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            PermissionsHelper.onRequestPermissionsResult(context, REQUEST_PERMISSIONS, grantResults)
        }
    }

    fun onNewIntent(intent: Intent) {
        val uriString = intent.dataString ?: return
        if (Util.isGeoIntent(uriString)) {
            AppProcess.handleGeoUri(uriString)
        }
    }
}
