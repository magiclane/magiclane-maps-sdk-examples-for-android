// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.speedwatcher

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var currentSpeed: TextView
    private lateinit var speedLimit: TextView
    private lateinit var followCursorButton: FloatingActionButton

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /* 
    Define a navigation listener that will receive notifications from the
    navigation service.
    We will use just the onNavigationStarted method, but for more available
    methods you should check the documentation.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    // Start listening for new positions.
                    PositionService.addListener(positionListener, EDataType.Position)
                    enableGPSButton()
                    mapView.followPosition()
                    EspressoIdlingResource.decrement()
                }
            }
        },
        onNavigationInstructionUpdated = { instr ->
            // From every new navigation instruction get the speed limit.
            val limit = SdkCall.execute execute@{
                val pair = GemUtil.getSpeedText(instr.currentStreetSpeedLimit, EUnitSystem.Metric)
                return@execute pair.first + " " + pair.second
            }

            speedLimit.text = limit
        },
        onDestinationReached = {
            // DON'T FORGET to remove the position listener after the navigation is done.
            PositionService.removeListener(positionListener)
            EspressoIdlingResource.decrement()
        },
        onNavigationError = {
            EspressoIdlingResource.decrement()
        }
    )

    // Define a position listener the will help us get the current speed.
    private val positionListener = object : PositionListener()
    {
        override fun onNewPosition(value: PositionData)
        {
            // Get the current speed for every new position received
            val speed = GemUtil.getSpeedText(value.speed, EUnitSystem.Metric).let { speedPair ->
                speedPair.first + " " + speedPair.second
            }

            Util.postOnMain {
                currentSpeed.text = speed
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

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        EspressoIdlingResource.increment()
        gemSurfaceView = findViewById(R.id.gem_surface)
        progressBar = findViewById(R.id.progressBar)
        currentSpeed = findViewById(R.id.current_speed)
        speedLimit = findViewById(R.id.speed_limit)
        followCursorButton = findViewById(R.id.follow_cursor)

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startSimulation()
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
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------
    override fun onStop()
    {
        super.onStop()
        // Release the SDK.
        if (isFinishing)
            GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Amsterdam", 52.356971, 4.879516),
            Landmark("Paris", 48.856693, 2.351461)
        )

        navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
            speedMultiplier = 0.5f
        )
    }
}

// -------------------------------------------------------------------------------------------------
//region --------------------------------------------------FOR TESTING------------------------------
// -------------------------------------------------------------------------------------------------
object EspressoIdlingResource
{
    val espressoIdlingResource = CountingIdlingResource("SpeedWatcherIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion  ----------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------
