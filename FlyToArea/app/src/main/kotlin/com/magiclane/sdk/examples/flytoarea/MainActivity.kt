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

package com.magiclane.sdk.examples.flytoarea

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var statusText: TextView

    private val searchService = SearchService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
            showStatusMessage("Search service has started!")
        },

        onCompleted = { results, errorCode, _ ->
            progressBar.visibility = View.GONE
            showStatusMessage("Search service completed with error code: $errorCode")

            when (errorCode)
            {
                GemError.NoError ->
                {
                    if (results.isNotEmpty())
                    {
                        showStatusMessage("Fly to area started")
                        val landmark = results[0]
                        flyTo(landmark)
                        showStatusMessage("Fly to area completed")
                    }
                    else
                    {
                        // The search completed without errors, but there were no results found.
                        showStatusMessage("The search completed without errors, but there were no results found.")
                    }
                }

                GemError.Cancel ->
                {
                    // The search action was cancelled.
                }

                else ->
                {
                    // There was a problem at computing the search operation.
                    showDialog("Search service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        statusText = findViewById(R.id.status_text)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done after the world map is ready.
            SdkCall.execute {
                val text = "Statue of Liberty New York"
                val coordinates = Coordinates(40.68925476, -74.04456329)

                searchService.searchByFilter(text, coordinates)
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

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.geographicArea?.let { area ->
            gemSurfaceView.mapView?.let { mainMapView ->
                // Define highlight settings for displaying the area contour on map. 
                val settings = HighlightRenderSettings(EHighlightOptions.ShowContour)

                // Center the map on a specific area using the provided animation.
                mainMapView.centerOnArea(area)

                // Highlights a specific area on the map using the provided settings.
                mainMapView.activateHighlightLandmarks(landmark, settings)
            }
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

    private fun showStatusMessage(text: String)
    {
        if (!statusText.isVisible)
        {
            statusText.visibility = View.VISIBLE
        }
        statusText.text = text
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
