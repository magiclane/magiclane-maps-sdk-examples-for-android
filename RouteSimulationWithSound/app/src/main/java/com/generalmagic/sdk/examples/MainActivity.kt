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
import com.generalmagic.sdk.core.ISound
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.SoundPlayingListener
import com.generalmagic.sdk.core.SoundPlayingPreferences
import com.generalmagic.sdk.core.SoundPlayingService
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.routesandnavigation.NavigationService
import com.generalmagic.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar

    private lateinit var soundService: SoundPlayingService

    private val soundPreference = SoundPlayingPreferences()
    private val playingListener = object : SoundPlayingListener() {
        override fun notifyStart(hasProgress: Boolean) {}
    }

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
                    mapView.preferences?.enableCursor = false
                    navigationService.getNavigationRoute(this)?.let { route ->
                        mapView.presentRoute(route)
                    }

                    followCursor()
                }
            }
        }

        override fun onNavigationSound(sound: ISound) {
            soundService.play(sound, playingListener, soundPreference)
        }

        override fun canPlayNavigationSound(): Boolean = true
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

        gemSurfaceView.onSdkInitSucceeded = { soundService = SoundPlayingService() }
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

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616)
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
