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
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.d3scene.Screen
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
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
            Handler(mainLooper).postDelayed({
                SdkCall.execute {
                    addSecondMapView(mainMapView?.getScreen())
                }
            }, 1000)
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            // The SDK initialization was not completed.
            finish()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun addSecondMapView(screen: Screen?) {
        // Define the rectangle for the second map view.
        val secondViewRect = RectF(0.0f, 0.0f, 0.5f, 0.5f)
        // Produce the map view in the same screen.
        screen?.let { MapView.produce(it, secondViewRect) }
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
}
