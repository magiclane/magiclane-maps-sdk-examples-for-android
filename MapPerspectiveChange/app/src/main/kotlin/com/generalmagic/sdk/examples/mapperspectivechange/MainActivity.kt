/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.mapperspectivechange

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.d3scene.EMapViewPerspective
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.button.MaterialButton
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var surfaceView: GemSurfaceView
    private lateinit var button: MaterialButton
    private var currentPerspective: EMapViewPerspective = EMapViewPerspective.TwoDimensional

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.gem_surface)
        button = findViewById(R.id.button)

        val twoDimensionalText = resources.getString(R.string.two_dimensional)
        val threeDimensionalText = resources.getString(R.string.three_dimensional)

        button.setOnClickListener {
            // Get the map view.
            surfaceView.mapView?.let { mapView ->
                // Establish the current map view perspective.
                currentPerspective = if (currentPerspective == EMapViewPerspective.TwoDimensional) {
                    button.text = twoDimensionalText
                    EMapViewPerspective.ThreeDimensional
                } else {
                    button.text = threeDimensionalText
                    EMapViewPerspective.TwoDimensional
                }

                SdkCall.execute {
                    // Change the map view perspective.
                    mapView.preferences?.setMapViewPerspective(
                        currentPerspective,
                        Animation(EAnimation.Linear, 300)
                    )
                }
            }
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
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
