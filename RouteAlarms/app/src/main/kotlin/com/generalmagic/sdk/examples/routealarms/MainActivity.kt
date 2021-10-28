/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.routealarms

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.d3scene.ECommonOverlayId
import com.generalmagic.sdk.d3scene.OverlayService
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.AlarmListener
import com.generalmagic.sdk.routesandnavigation.AlarmService
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.routesandnavigation.NavigationService
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var followCursorButton: FloatingActionButton

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    // Define an alarm service to be able to track alarms on the map.
    private var alarmService: AlarmService? = null

    /* 
    Define an alarm listener that will receive notifications from the
    alarms service.
    We will use just the onOverlayItemAlarmsUpdated method, but for more available
    methods you should check the documentation at https://generalmagic.com/documentation/
     */
    private val alarmListener = AlarmListener.create(
        onOverlayItemAlarmsUpdated = {
            SdkCall.execute execute@{
                // Get the maximum distance until an alarm is reached.
                val maxDistance = alarmService?.alarmDistance ?: 0.0

                // Get the overlay items that are present and relevant.
                val markerList = alarmService?.overlayItemAlarms
                if (markerList == null || markerList.size == 0) return@execute

                // Get the distance to the closest alarm marker.
                val distance = markerList.getDistance(0)
                if (distance <= maxDistance) {
                    // If you are close enough to the alarm item, notify the user.
                    Toast.makeText(
                        this@MainActivity,
                        "Speed camera in $distance m",
                        Toast.LENGTH_LONG
                    ).show()

                    // Remove the alarm listener if you want to notify only once.
                    alarmService?.setAlarmListener(null)
                }
            }
        }
    )

    /* 
    Define a navigation listener that will receive notifications from the
    navigation service.
    We will use just the onNavigationStarted method, but for more available
    methods you should check the documentation.
     */
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                // Set the overlay for which to be notified.
                setAlarmOverlay(ECommonOverlayId.Safety)
                gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }
        }
    )

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
        followCursorButton = findViewById(R.id.followCursor)

        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startSimulation()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
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

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {
                Util.postOnMain { followCursorButton.visibility = View.VISIBLE }
            }

            onEnterFollowingPosition = {
                Util.postOnMain { followCursorButton.visibility = View.GONE }
            }

            // Set on click action for the GPS button.
            followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Suppress("SameParameterValue")
    private fun setAlarmOverlay(overlay: ECommonOverlayId) {
        SdkCall.execute {
            alarmService = AlarmService.produce(alarmListener)
            alarmService?.alarmDistance = 500.0 // meters
            val availableOverlays = OverlayService().getAvailableOverlays(null)?.first
            if (availableOverlays != null) {
                for (item in availableOverlays) {
                    if (item.uid == overlay.value) {
                        alarmService?.overlays?.add(item.uid)
                    }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("A", 53.056306247688326, 8.882596560149098),
            Landmark("B", 53.06178963549359, 8.876610724727849)
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
