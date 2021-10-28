/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.mapgestures

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.Xy
import com.generalmagic.sdk.examples.R
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

            /**
             * For all the map gestures callbacks please check the SDK documentation
             * available at https://generalmagic.com/documentation/
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

                mapView.onPinch = { start1: Xy, start2: Xy, end1: Xy, end2: Xy ->
                    SdkCall.execute {
                        Log.i(
                            "Gesture",
                            "onPinch from " +
                                    "(${start1.x}, ${start1.y}) and (${start2.x}, ${start2.y}) " +
                                    "to " +
                                    "(${end1.x}, ${end1.y}) and (${end2.x}, ${end2.y})."
                        )
                    }
                }

                mapView.onRotate =
                    { start1: Xy, start2: Xy, end1: Xy, end2: Xy, center: Xy, deltaAngleDeg: Double ->
                        SdkCall.execute {
                            Log.i(
                                "Gesture",
                                "onRotate from " +
                                        "(${start1.x}, ${start1.y}) and (${start2.x}, ${start2.y}) " +
                                        "to " +
                                        "(${end1.x}, ${end1.y}) and (${end2.x}, ${end2.y}) " +
                                        "with center " +
                                        "(${center.x}, ${center.y}) " +
                                        "and $deltaAngleDeg degrees."
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
