// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapperspectivechange

// -------------------------------------------------------------------------------------------------------------------------------

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMapViewPerspective
import com.magiclane.sdk.util.SdkCall
import com.google.android.material.button.MaterialButton
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    private lateinit var surfaceView: GemSurfaceView
    private lateinit var button: MaterialButton
    private var currentPerspective: EMapViewPerspective = EMapViewPerspective.TwoDimensional

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
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
                currentPerspective = if (currentPerspective == EMapViewPerspective.TwoDimensional)
                {
                    button.text = twoDimensionalText
                    EMapViewPerspective.ThreeDimensional
                }
                else
                {
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

        onBackPressedDispatcher.addCallback(this){
            finish()
            exitProcess(0)
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
}

// -------------------------------------------------------------------------------------------------------------------------------