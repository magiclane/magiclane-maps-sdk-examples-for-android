/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.mapcompass

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.d3scene.EViewCameraTransitionStatus
import com.generalmagic.sdk.d3scene.EViewDataTransitionStatus
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.util.SdkCall
import com.google.android.material.button.MaterialButton
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var surfaceView: GemSurfaceView
    private lateinit var compass: ImageView

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.gem_surface)
        compass = findViewById(R.id.compass)

        SdkSettings.onMapDataReady = {
            // Get the map view.
            surfaceView.mapView?.let { mapView ->
                // Change the compass icon rotation based on the map rotation at redndering.
                mapView.onViewRendered =
                    { _: EViewDataTransitionStatus, _: EViewCameraTransitionStatus ->
                        SdkCall.execute {
                            mapView.preferences?.rotationAngle?.let { compass.rotation = -it.toFloat() }
                        }
                    }
                
                // Align the map to north if the compass icon is pressed. 
                compass.setOnClickListener {
                    SdkCall.execute {
                        mapView.alignNorthUp(Animation(EAnimation.Linear, 300))
                    }
                }
            }
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
}
