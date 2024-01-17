// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.displaycurrentstreetname

// -------------------------------------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.sensordatasource.*
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity() 
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var currentStreetNameView: TextView
    private lateinit var followCursorButton: FloatingActionButton

    private var dataSource: DataSource? = null
    private val dataSourceListener = object : DataSourceListener() {
        override fun onNewData(data: SenseData) {
            var text = ""
            SdkCall.execute execute@{
                val improvedPositionData = ImprovedPositionData(data)
                val roadAddress = improvedPositionData.roadAddress ?: return@execute

                roadAddress.format()?.let let@{
                    if (it.isEmpty())
                    {
                        text = "Current street name not available."
                        return@let   
                    }

                    text = "Current street name: $it"
                    
                    val speedLimit = (improvedPositionData.roadSpeedLimit * 3.6).toInt()
                    if (speedLimit != 0)
                    {
                        text += "\nRoad speed limit: $speedLimit km/h"
                    }
                }
            }
            Util.postOnMain { 
                currentStreetNameView.apply { 
                    if (!isVisible)
                    {
                        visibility = View.VISIBLE
                    }
                    this.text = text
                } 
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gemSurfaceView = findViewById(R.id.gem_surface)
        currentStreetNameView = findViewById(R.id.current_street_name)
        followCursorButton = findViewById(R.id.followCursor)

        gemSurfaceView.onDefaultMapViewCreated = {
            enableGPSButton()
        }

        SdkSettings.onMapDataReady = {
            val hasPermissions = PermissionsHelper.hasPermissions(this, permissions)

            if (hasPermissions) {
                SdkCall.execute {
                    startImprovedPositionListener()
                }
            } else {
                requestPermissions(this)
            }
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
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

    private fun startImprovedPositionListener()
    {
        if (gemSurfaceView.mapView?.isFollowingPosition() != true)
            gemSurfaceView.mapView?.followPosition()

        if (dataSource == null)
            dataSource = DataSourceFactory.produceLive()

        dataSource?.addListener(dataSourceListener, EDataType.ImprovedPosition)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            // Notice permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

            startImprovedPositionListener()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private val permissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun requestPermissions(activity: Activity): Boolean
    {
        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS, activity, permissions
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
