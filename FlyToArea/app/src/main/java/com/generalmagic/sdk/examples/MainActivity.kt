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

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.d3scene.*
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private var mainMapView: MapView? = null
    private var progressBar: ProgressBar? = null

    private val searchService = SearchService()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)

        /// GENERAL MAGIC
        searchService.onStarted = {
            progressBar?.visibility = View.VISIBLE
        }

        searchService.onCompleted = onCompleted@{ results, reason, _ ->
            progressBar?.visibility = View.GONE

            when (val gemError = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
                    if (results.isNotEmpty()) {
                        val value = results[0]
                        flyTo(value)
                    }

                    if (results.isEmpty()) {
                        // The search completed without errors, but there were no results found.
                        Toast.makeText(
                            this@MainActivity, "No results!", Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                SdkError.Cancel -> {
                    // The search action was cancelled.
                    return@onCompleted
                }

                else -> {
                    // There was a problem at computing the search operation.
                    Toast.makeText(
                        this@MainActivity,
                        "Search service error: ${gemError.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val mapSurface = findViewById<GemSurfaceView>(R.id.gem_surface)
        mapSurface.onScreenCreated = { screen ->
            // Defines an action that should be done after the screen is created.
            SdkCall.execute {
                /* 
                Define a rectangle in which the map view will expand.
                Predefined value of the offsets is 0.
                Value 1 means the offset will take 100% of available space.
                 */
                val mainViewRect = RectF(0.0f, 0.0f, 1.0f, 1.0f)
                // Produce a map view and establish that it is the main map view.
                val mapView = MapView.produce(screen, mainViewRect)
                mainMapView = mapView
            }
        }
        
        SdkInitHelper.onMapReady = {
            // Defines an action that should be done after the world map is ready.
            SdkCall.execute {
                if (!SdkInitHelper.isMapReady) return@execute
                val text = "Statue of Liberty New York"
                val coordinates = Coordinates(40.68925476, -74.04456329)

                searchService.preferences.setSearchMapPOIs(true)
                searchService.searchByFilter(text, coordinates)
            }
        }

        SdkInitHelper.onNetworkConnected = {
            // Defines an action that should be done after the network is connected.
            SdkInitHelper.onMapReady()
        }

        SdkInitHelper.onCancel = {
            // Defines what should be executed when the SDK initialization is cancelled.
            SdkCall.execute { searchService.cancelSearch() }
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            // The SDK initialization was not completed.
            finish()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        SdkInitHelper.deinit()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        terminateApp(this)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.getContourGeograficArea()?.let { area ->
            val settings = HighlightRenderSettings()
            settings.setOptions(
                if (area.isEmpty()) {
                    EHighlightOptions.ShowLandmark.value
                } else {
                    EHighlightOptions.ShowContour.value
                }
            )

            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)

            // Center the map on a specific area using the provided animation.
            mainMapView?.centerOnArea(area, -1, Xy(), animation)
            // Highlights a specific area on the map using the provided settings.
            mainMapView?.activateHighlightLandmarks(arrayListOf(landmark), settings)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
