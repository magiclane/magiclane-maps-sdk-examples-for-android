// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapgestures

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    private lateinit var gemSurfaceView: GemSurfaceView

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gemSurfaceView = findViewById(R.id.gem_surface)
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            /**
             * For all the map gestures callbacks please check the SDK documentation
             * available at https://magiclane.com/documentation/
             */
            gemSurfaceView.mapView?.let { mapView ->
                mapView.onDoubleTouch = {
                    SdkCall.execute {
                        Log.i("Gesture", "onDoubleTouch at (${it.x}, ${it.y}).")
                    }
                }

                mapView.onLongDown = {
                    SdkCall.execute {
                        Log.i("Gesture", "onLongDown at (${it.x}, ${it.y}).")
                    }
                }

                mapView.onMove = { start: Xy, end: Xy ->
                    SdkCall.execute {
                        Log.i(
                            "Gesture",
                            "onMove from (${start.x}, ${start.y}) to (${end.x}, ${end.y})."
                        )
                    }
                }

                mapView.onPinch = { start1: Xy, start2: Xy, end1: Xy, end2: Xy, center: Xy ->
                    SdkCall.execute {
                        Log.i(
                            "Gesture",
                            "onPinch from " +
                                    "(${start1.x}, ${start1.y}) and (${start2.x}, ${start2.y}) " +
                                    "to " +
                                    "(${end1.x}, ${end1.y}) and (${end2.x}, ${end2.y})" +
                                    "center " +
                                    "(${center.x}, ${center.y})."
                        )
                    }
                }

                mapView.onSwipe = { distX: Int, distY: Int, speedMMPerSec: Double ->
                    SdkCall.execute {
                        Log.i(
                            "Gesture", "onSwipe with " +
                                    "$distX pixels on X and " +
                                    "$distY pixels on Y and " +
                                    "the speed of $speedMMPerSec mm/s."
                        )
                    }
                }

                mapView.onTouch = {
                    SdkCall.execute {
                        Log.i("Gesture", "onTouch at (${it.x}, ${it.y}).")
                    }
                }

                mapView.onTwoTouches = {
                    SdkCall.execute {
                        Log.i("Gesture", "onTwoTouches with middle point (${it.x}, ${it.y}).")
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            /*
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
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }

    // -------------------------------------------------------------------------------------------------------------------------------

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
    
    // -------------------------------------------------------------------------------------------------------------------------------
}

// -----------------------------------------------------------------------------------------------------------------------------------
