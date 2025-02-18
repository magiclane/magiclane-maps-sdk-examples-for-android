// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.drawpolyline

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) 
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gemSurfaceView = findViewById(R.id.gem_surface)

        EspressoIdlingResource.increment()
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the map is ready.
            flyToPolyline()
            lifecycleScope.launch {
                delay(3000)
                EspressoIdlingResource.decrement()
            }
        }

        SdkSettings.onApiTokenRejected = {/* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
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

    @SuppressLint("InflateParams")
    private fun showDialog(text: String)
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun flyToPolyline() = SdkCall.execute {
        gemSurfaceView.mapView?.let { mapView ->
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
            val settings = MarkerCollectionRenderSettings(
                polylineInnerColor = Rgba.magenta(),
                polylineOuterColor = Rgba.black()
            ).apply {
                polylineInnerSize = 1.25
                polylineOuterSize = 0.75
            }

            // Add the collection to the desired map view so it can be displayed.
            mapView.preferences?.markers?.add(markerCollection, settings)

            // Center the map on this marker collection's area.
            markerCollection.area?.let { mapView.centerOnArea(it) }
        }
    }
    // ---------------------------------------------------------------------------------------------------------------------------
}
// ---------------------------------------------------------------------------------------------------------------------------
//region --------------------------------------------------FOR TESTING--------------------------------------------------------------
@VisibleForTesting
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("DrawPolylineIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if(!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion -------------------------------------------------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------------------------------------
