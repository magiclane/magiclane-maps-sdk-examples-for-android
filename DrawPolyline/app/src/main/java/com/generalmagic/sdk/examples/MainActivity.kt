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
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.Image
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.d3scene.EMarkerType
import com.generalmagic.sdk.d3scene.Marker
import com.generalmagic.sdk.d3scene.MarkerCollection
import com.generalmagic.sdk.d3scene.MarkerCollectionDisplaySettings
import com.generalmagic.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var gemSurfaceView: GemSurfaceView

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gemSurfaceView = findViewById(R.id.gem_surface)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the map is ready.
            flyToPolyline()
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

    private fun flyToPolyline() = SdkCall.execute {
        gemSurfaceView.getDefaultMapView()?.let { mapView ->
            /* 
            Make a MarkerCollection and a Marker item that will be stored in the collection.
            You can create multiple Marker items that can be added in the same collection.
             */
            val markerCollection = MarkerCollection(EMarkerType.Polyline, "My marker collection")

            // Define a market item and add the necessary coordinates to it.
            val marker = Marker().apply {
                add(52.360234, 4.886782)
                add(52.360495, 4.886266)
                add(52.360854, 4.885539)
                add(52.361184, 4.884849)
                add(52.361439, 4.884344)
                add(52.361593, 4.883986)
            }

            // Add the marker item to the collection.
            markerCollection.add(marker)

            // Make a list of settings that will decide how each marker collection will be displayed on the map.
            val settings = MarkerCollectionDisplaySettings(image = Image())

            // Add the collection to the desired map view so it can be displayed.
            mapView.preferences()?.markers()?.add(markerCollection, settings)

            // Center the map on this marker collection's area.
            markerCollection.getArea()?.let { mapView.centerOnArea(it) }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
