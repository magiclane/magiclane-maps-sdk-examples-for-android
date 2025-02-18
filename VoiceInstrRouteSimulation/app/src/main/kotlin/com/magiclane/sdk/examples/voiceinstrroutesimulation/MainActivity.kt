// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.voiceinstrroutesimulation

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.*
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var followCursorButton: FloatingActionButton
    private val soundPreference = SoundPlayingPreferences()
    private val kDefaultToken = "YOUR_TOKEN"
    private val playingListener = object : SoundPlayingListener()
    {
        override fun notifyStart(hasProgress: Boolean)
        {
        }
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
                SoundPlayingService.play(sound, playingListener, soundPreference)
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

    // ---------------------------------------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        followCursorButton = findViewById(R.id.followCursor)

        progressBar.visibility = View.VISIBLE

        SdkSettings.onMapDataReady = { mapReady ->
            if (mapReady)
            {
                progressBar.visibility = View.VISIBLE

                val type = EContentType.HumanVoice
                val countryCode = "DEU"
                var voiceHasBeenDownloaded = false

                val onVoiceReady = { voiceFilePath: String ->
                    SdkSettings.setVoiceByPath(voiceFilePath)
                    startSimulation()
                }

                SdkCall.execute {
                    contentStore = ContentStore()

                    // check if already exists locally
                    contentStore?.getLocalContentList(type)?.let { localList ->
                        for (item in localList)
                        {
                            if (item.countryCodes?.contains(countryCode) == true)
                            {
                                voiceHasBeenDownloaded = true
                                onVoiceReady(item.filename!!)
                                return@execute // already exists
                            }
                        }
                    }
                }

                if (!voiceHasBeenDownloaded)
                {
                    val downloadVoice = {
                        SdkCall.execute {
                            contentStore?.asyncGetStoreContentList(
                                type,
                                onCompleted = { result, _, _ ->
                                    SdkCall.execute {
                                        for (item in result)
                                        {
                                            if (item.countryCodes?.contains(countryCode) == true)
                                            {
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

                    val token = GemSdk.getTokenFromManifest(this)

                    if (!token.isNullOrEmpty() && (token != kDefaultToken))
                    {
                        downloadVoice()
                    }
                    else // if token is not present try to avoid content server requests limitation by delaying the voices catalog request
                    {
                        Handler(Looper.getMainLooper()).postDelayed({
                            downloadVoice()
                        }, 3000)
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            progressBar.visibility = View.GONE
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true)
        {
            override fun handleOnBackPressed()
            {
                finish()
                exitProcess(0)
            }
        })
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun enableGPSButton()
    {
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

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Start", 51.50338075949678, -0.11946124784612752),
            Landmark("Destination", 51.500996060519896, -0.12461566914005363)
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(text: String)
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------