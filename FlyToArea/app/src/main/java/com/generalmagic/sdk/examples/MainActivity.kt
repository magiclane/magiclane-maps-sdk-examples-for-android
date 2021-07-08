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
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.d3scene.EHighlightOptions
import com.generalmagic.sdk.d3scene.HighlightRenderSettings
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView

    private val searchService = SearchService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { results, reason, _ ->
            progressBar.visibility = View.GONE

            when (reason) {
                SdkError.NoError -> {
                    if (results.isNotEmpty()) {
                        val landmark = results[0]
                        flyTo(landmark)
                    } else {
                        // The search completed without errors, but there were no results found.
                        showToast("No results!")
                    }
                }

                SdkError.Cancel -> {
                    // The search action was cancelled.
                }

                else -> {
                    // There was a problem at computing the search operation.
                    showToast("Search service error: ${reason.name}")
                }
            }
        }
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)

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
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            showToast("TOKEN REJECTED")
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

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.getContourGeograficArea()?.let { area ->
            gemSurfaceView.getDefaultMapView()?.let { mainMapView ->
                // Define highlight settings for displaying the area contour on map. 
                val settings = HighlightRenderSettings(EHighlightOptions.ShowContour)

                // Center the map on a specific area using the provided animation.
                mainMapView.centerOnArea(area)

                // Highlights a specific area on the map using the provided settings.
                mainMapView.activateHighlightLandmarks(landmark, settings)
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showToast(text: String) = Util.postOnMain {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
