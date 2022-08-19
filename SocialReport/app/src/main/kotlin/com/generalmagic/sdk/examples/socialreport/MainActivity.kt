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
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.SocialOverlay
import com.generalmagic.sdk.sensordatasource.PositionListener
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.sensordatasource.enums.EDataType
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView
    
    private lateinit var statusText: TextView
    
    private lateinit var statusProgressBar: ProgressBar

    private val socialReportListener = ProgressListener.create()

    private lateinit var positionListener: PositionListener

    private val kRequestLocationPermissionCode = 110

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        

        gemSurfaceView = findViewById(R.id.gem_surface)
        statusText = findViewById(R.id.status_text)
        statusProgressBar = findViewById(R.id.status_progress_bar)

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
            showDialog("TOKEN REJECTED")
        }

        requestPermissions(this)

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to internet!")
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

    private fun showStatusMessage(text: String, withProgress: Boolean = false)
    {
        if (!statusText.isVisible)
        {
            statusText.visibility = View.VISIBLE
        }
        statusText.text = text

        if (withProgress)
        {
            statusProgressBar.visibility = View.VISIBLE
        }
        else
        {
            statusProgressBar.visibility = View.GONE
        }
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
            Util.postOnMain { showDialog(errorMsg) }

            return@execute
        }

        val error = SocialOverlay.report(prepareIdOrError, subCategory.uid, socialReportListener)

        if (GemError.isError(error))
        {
            Util.postOnMain { showDialog("Report Error: ${GemError.getMessage(error)}") }
        }
        else
        {
            Util.postOnMain { showStatusMessage("Report Sent!") }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun waitForNextImprovedPosition(onEvent: (() -> Unit))
    {
        positionListener = PositionListener {
            if (it.isValid())
            {
                Util.postOnMain { showStatusMessage("On valid position") }
                onEvent()

                PositionService().removeListener(positionListener)
            }
        }

        PositionService().addListener(positionListener, EDataType.ImprovedPosition)

        // listen for first valid position to start the nav
        Util.postOnMain { showStatusMessage("Waiting for valid position", true) }
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
