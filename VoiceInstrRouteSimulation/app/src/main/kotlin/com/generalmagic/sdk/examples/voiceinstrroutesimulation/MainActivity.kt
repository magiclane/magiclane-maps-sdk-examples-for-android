/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.voiceinstrroutesimulation

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.SoundPlayingListener
import com.generalmagic.sdk.core.SoundPlayingPreferences
import com.generalmagic.sdk.core.SoundPlayingService
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.routesandnavigation.NavigationService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var followCursorButton: FloatingActionButton

    private lateinit var soundService: SoundPlayingService

    private val soundPreference = SoundPlayingPreferences()
    private val playingListener = object : SoundPlayingListener() {
        override fun notifyStart(hasProgress: Boolean) {}
    }

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private var contentStore: ContentStore? = null

    /* 
    Define a navigation listener that will receive notifications from the
    navigation service.
    We will use just the onNavigationStarted method, but for more available
    methods you should check the documentation.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = { onNavigationStarted() },
        onNavigationSound = { sound ->
            SdkCall.execute {
                soundService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true,
        postOnMain = true
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

    private fun onNavigationStarted() = SdkCall.execute {
        gemSurfaceView.mapView?.let { mapView ->
            mapView.preferences?.enableCursor = false
            navigationService.getNavigationRoute(navigationListener)?.let { route ->
                mapView.presentRoute(route)
            }

            enableGPSButton()
            mapView.followPosition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        followCursorButton = findViewById(R.id.followCursor)

        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            SdkCall.execute {
                contentStore = ContentStore()

                val type = EContentType.HumanVoice
                val countryCode = "DEU"

                val onVoiceReady = { voiceFilePath: String ->
                    SdkSettings().setVoiceByPath(voiceFilePath)
                    startSimulation()
                }

                // check if already exists locally
                contentStore?.getLocalContentList(type)?.let { localList ->
                    for (item in localList) {
                        if (item.countryCodes?.contains(countryCode) == true) {
                            onVoiceReady(item.filename!!)
                            return@execute // already exists
                        }
                    }
                }

                // download the voice
                contentStore?.asyncGetStoreContentList(type, onCompleted = { result, _, _ ->
                    SdkCall.execute {
                        for (item in result) {
                            if (item.countryCodes?.contains(countryCode) == true) {
                                item.asyncDownload(onCompleted = { _, _ ->
                                    SdkCall.execute {
                                        onVoiceReady(item.filename!!)
                                    }
                                })
                                break
                            }
                        }
                    }
                })
            }
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

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
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
                followCursorButton.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                followCursorButton.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Start", 51.50338075949678, -0.11946124784612752),
            Landmark("Destination", 51.500996060519896, -0.12461566914005363)
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
