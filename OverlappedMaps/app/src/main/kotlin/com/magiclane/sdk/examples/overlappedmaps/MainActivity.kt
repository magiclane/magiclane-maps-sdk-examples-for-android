// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.overlappedmaps

// -------------------------------------------------------------------------------------------------

import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.RectF
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.MapView
import kotlin.system.exitProcess

// --------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ----------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView
    private var secondMapView: MapView? = null

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /// MAGIC LANE
        gemSurfaceView = findViewById(R.id.gem_surface)
        gemSurfaceView.onDefaultMapViewCreated = {
            gemSurfaceView.gemScreen?.let { screen ->
                val secondViewRect = RectF(0.0f, 0.0f, 0.5f, 0.5f)
                secondMapView = MapView.produce(screen, secondViewRect, null, true)
            }
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
        }

        onBackPressedDispatcher.addCallback(this){
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------