// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.speedttswarning

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.TAG
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.EStringIds
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity: AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var currentSpeed: TextView
    private lateinit var speedLimit: TextView
    private lateinit var followCursorButton: FloatingActionButton
    
    private var currentSpeedValue = 0
    private var speedLimitValue = 0
    private var wasSpeedWarningPlayed = false

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
                }
            }
        },
        onNavigationInstructionUpdated = { instr ->
            // From every new navigation instruction get the speed limit.
            val limit = SdkCall.execute execute@{
                val pair = GemUtil.getSpeedText(instr.currentStreetSpeedLimit, EUnitSystem.Metric)
                speedLimitValue = pair.first.toInt()
                return@execute pair.first + " " + pair.second
            }

            speedLimit.text = limit
        },
        onDestinationReached = {
            // DON'T FORGET to remove the position listener after the navigation is done.
            PositionService.removeListener(positionListener)
        }
    )

    // Define a position listener tht will help us get the current speed.
    private val positionListener = object : PositionListener()
    {
        override fun onNewPosition(value: PositionData)
        {
            // Get the current speed for every new position received
            val speed = GemUtil.getSpeedText(value.speed, EUnitSystem.Metric).let { speedPair ->
                currentSpeedValue = speedPair.first.toInt()
                speedPair.first + " " + speedPair.second
            }

            if (currentSpeedValue > speedLimitValue)
            {
                if (!wasSpeedWarningPlayed)
                {
                    SoundPlayingService.playText(GemUtil.getTTSString(EStringIds.eStrMindYourSpeed), SoundPlayingListener(), SoundPlayingPreferences())
                    wasSpeedWarningPlayed = true
                }
            }
            else
            {
                wasSpeedWarningPlayed = false
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

        gemSurfaceView = findViewById(R.id.gem_surface)
        progressBar = findViewById(R.id.progressBar)
        currentSpeed = findViewById(R.id.current_speed)
        speedLimit = findViewById(R.id.speed_limit)
        followCursorButton = findViewById(R.id.followCursor)

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startNavigation()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }
        
        requestPermissions(this)

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }
    }
    
    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }
    
    // ---------------------------------------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults)
        {
            if (item != PackageManager.PERMISSION_GRANTED)
            {
                finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            // Notice permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        }

        startNavigation()
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
    
    // ---------------------------------------------------------------------------------------------
    
    private fun startNavigation()
    {
        val startNavigationTask = {
            val hasPermissions = PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

            if (hasPermissions)
            {
                val destination = Landmark("Paris", 48.8566932, 2.3514616)

                // Cancel any navigation in progress.
                navigationService.cancelNavigation(navigationListener)
                // Start the new navigation.
                Log.i(TAG, "MainActivity.startNavigation: before")
                val error = navigationService.startNavigation(
                    destination,
                    navigationListener,
                    routingProgressListener,
                )
                Log.i(TAG, "MainActivity.startNavigation: after = $error")
            }         
        }

        SdkCall.execute {
            lateinit var positionListener: PositionListener
            if (PositionService.position?.isValid() == true)
            {
                startNavigationTask()
            }
            else
            {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    startNavigationTask()
                }

                // listen for first valid position to start the nav
                PositionService.addListener(positionListener)
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------

    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
    }
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
