// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.speedttswarningtracking

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.*
import com.magiclane.sdk.routesandnavigation.*
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.*
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
    
    private var alarmService: AlarmService? = null

    //region members for testing
    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
        const val RESOURCE = "GLOBAL"
    }

    private var mainActivityIdlingResource = CountingIdlingResource(RESOURCE, true)
    //endregion

    // Define a position listener the will help us get the current speed.
    private val positionListener = object : PositionListener()
    {
        override fun onNewPosition(value: PositionData)
        {
            if (!value.isValid()) return
            
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

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            
            SdkCall.execute {
                alarmService = AlarmService.produce(object : AlarmListener()
                {
                    override fun onSpeedLimit(speed: Double, limit: Double, insideCityArea: Boolean)
                    {
                        val pair = GemUtil.getSpeedText(limit, EUnitSystem.Metric)
                        speedLimitValue = pair.first.toInt()
                        val speedLimitStr = if (speedLimitValue > 0)
                        {
                            pair.first + " " + pair.second
                        }
                        else
                        {
                            getString(R.string.not_applicable)
                        }

                        Util.postOnMain { speedLimit.text = speedLimitStr }
                    }
                })
                
                startPositionService()
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

        requestPermissions(this)

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(this){
            finish()
            exitProcess(0)
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
            
            startPositionService()
        }
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
                followCursorButton.isVisible = true
            }

            onEnterFollowingPosition = {
                followCursorButton.isVisible = false
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

    private fun startPositionService()
    {
        val hasPermissions = PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (hasPermissions && PositionService.position?.isValid() == true)
        {
            // Start listening for new positions.
            PositionService.addListener(positionListener, EDataType.Position)
            enableGPSButton()
            gemSurfaceView.mapView?.followPosition()
        }
    }

    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------