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
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Coordinates
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

            // Defines an action that should be done after the world map is ready.
            flyTo(Coordinates(45.65112176095828, 25.60473923113322))
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

    private fun flyTo(coordinates: Coordinates) = SdkCall.execute {
        // Center the map on a specific set of coordinates using the provided animation.
        gemSurfaceView.getDefaultMapView()?.centerOnCoordinates(coordinates)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
