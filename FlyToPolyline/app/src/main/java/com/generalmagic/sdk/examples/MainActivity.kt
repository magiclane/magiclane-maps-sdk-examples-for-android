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
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.Image
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.core.Xy
import com.generalmagic.sdk.d3scene.*
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.util.SdkCall

class MainActivity : AppCompatActivity() {
    private var mainMapView: MapView? = null

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /// GENERAL MAGIC
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

        SdkInitHelper.onNetworkConnected = {
            // Defines an action that should be done after the network is connected.
            flyToPolyline()
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

    private fun flyToPolyline() {
        SdkCall.execute {
            /* 
            Make a VectorDataSource and a VectorItem that will be stored in it.
            You can create multiple vector items that can be added in the same data source.
             */
            val vectorDataSource =
                MarkerCollection(EMarkerType.Polyline.value, "My polylines data source")
            val marker = Marker()

            // Add the necessary coordinates to the vector item.
            marker.add(Coordinates(52.360234, 4.886782))
            marker.add(Coordinates(52.360495, 4.886266))
            marker.add(Coordinates(52.360854, 4.885539))
            marker.add(Coordinates(52.361184, 4.884849))
            marker.add(Coordinates(52.361439, 4.884344))
            marker.add(Coordinates(52.361593, 4.883986))

            // Add the vector item to the vector data source.
            vectorDataSource.add(marker)

            // Make a list of settings that will decide how each data source will be displayed on the map.
            val settings = MarkerCollectionDisplaySettings()
            settings.setImage(Image())

            // Add the vector data source to the desired map view so it can be displayed.
            mainMapView?.preferences()?.markers()?.add(vectorDataSource, settings)

            val area = vectorDataSource.getArea()
            if (area != null) {
                val animation = Animation()
                animation.setType(EAnimation.Fly)

                // Center the map on a specific area using the provided animation.
                mainMapView?.centerOnArea(area, -1, Xy(), animation)
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
