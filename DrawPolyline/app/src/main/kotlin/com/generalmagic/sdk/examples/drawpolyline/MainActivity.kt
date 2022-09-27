// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.drawpolyline

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.d3scene.EMarkerType
import com.generalmagic.sdk.d3scene.Marker
import com.generalmagic.sdk.d3scene.MarkerCollection
import com.generalmagic.sdk.d3scene.MarkerCollectionRenderSettings
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) 
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gemSurfaceView = findViewById(R.id.gem_surface)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the map is ready.
            flyToPolyline()
        }

        SdkSettings.onApiTokenRejected = {/* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
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

    override fun onBackPressed() 
    {
        finish()
        exitProcess(0)
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
            val settings = MarkerCollectionRenderSettings()

            // Add the collection to the desired map view so it can be displayed.
            mapView.preferences?.markers?.add(markerCollection, settings)

            // Center the map on this marker collection's area.
            markerCollection.area?.let { mapView.centerOnArea(it) }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
