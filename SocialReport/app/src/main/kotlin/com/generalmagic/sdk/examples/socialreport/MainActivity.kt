// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.socialreport

// -------------------------------------------------------------------------------------------------------------------------------

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.sensordatasource.PositionListener
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.sensordatasource.enums.EDataType
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView

    private val socialReportListener = ProgressListener.create()

    private lateinit var positionListener: PositionListener

    private val kRequestLocationPermissionCode = 110

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gemSurfaceView = findViewById(R.id.gem_surface)

        SdkSettings.onMapDataReady = { isReady ->
            if (isReady)
            {
                // Defines an action that should be done when the world map is ready (Updated/ loaded).
                SdkCall.execute {
                    gemSurfaceView.mapView?.followPosition()

                    waitForNextImprovedPosition {
                        submitReport()
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {/* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
        }

        requestPermissions(this)

        if (!Util.isInternetConnected(this))
        {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun submitReport() = SdkCall.execute {
        val overlayInfo = SocialOverlay.reportsOverlayInfo ?: return@execute
        val countryISOCode = MapDetails().isoCodeForCurrentPosition ?: return@execute
        val categories = overlayInfo.getCategories(countryISOCode) ?: return@execute

        val mainCategory = categories[0] // Police

        val subcategories = mainCategory.subcategories ?: return@execute

        val subCategory = subcategories[0] // My side

        val prepareIdOrError = SocialOverlay.prepareReporting()
        if (prepareIdOrError <= 0)
        {
            val errorMsg = "Prepare error: ${GemError.getMessage(prepareIdOrError)}"
            Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()

            return@execute
        }

        val error = SocialOverlay.report(prepareIdOrError, subCategory.uid, socialReportListener)

        var errorMsg = "Report Sent!"
        if (GemError.isError(error))
        {
            errorMsg = "Report Error: ${GemError.getMessage(error)}"
        }

        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun waitForNextImprovedPosition(onEvent: (() -> Unit))
    {
        positionListener = PositionListener {
            if (it.isValid())
            {
                Toast.makeText(this@MainActivity, "On valid position", Toast.LENGTH_SHORT).show()
                onEvent()

                PositionService().removeListener(positionListener)
            }
        }

        PositionService().addListener(positionListener, EDataType.ImprovedPosition)

        // listen for first valid position to start the nav
        Toast.makeText(this, "Waiting for valid position", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun requestPermissions(activity: Activity): Boolean
    {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        return PermissionsHelper.requestPermissions(kRequestLocationPermissionCode, activity, permissions)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == kRequestLocationPermissionCode)
        {
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
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}
