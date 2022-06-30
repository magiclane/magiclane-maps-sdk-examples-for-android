/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.mapcompass

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.d3scene.EViewCameraTransitionStatus
import com.generalmagic.sdk.d3scene.EViewDataTransitionStatus
import com.generalmagic.sdk.sensordatasource.CompassData
import com.generalmagic.sdk.sensordatasource.DataSource
import com.generalmagic.sdk.sensordatasource.DataSourceFactory
import com.generalmagic.sdk.sensordatasource.DataSourceListener
import com.generalmagic.sdk.sensordatasource.enums.EDataType
import com.generalmagic.sdk.util.GemCall
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var surfaceView: GemSurfaceView
    private lateinit var compass: ImageView
    private lateinit var btnEnableLiveHeading: FloatingActionButton
    private var isLiveHeadingEnabled = AtomicBoolean(false)

    var dataSource: DataSource? = null

    val headingSmoother = HeadingSmoother()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.gem_surface)
        compass = findViewById(R.id.compass)
        btnEnableLiveHeading = findViewById(R.id.btnEnableLiveHeading)

        // start stop btn

        buttonAsStart(this, btnEnableLiveHeading)

        btnEnableLiveHeading.setOnClickListener {
            isLiveHeadingEnabled.set(!isLiveHeadingEnabled.get())

            if (isLiveHeadingEnabled.get()) {
                buttonAsStop(this, btnEnableLiveHeading)
            } else {
                buttonAsStart(this, btnEnableLiveHeading)
            }

            Toast.makeText(
                this, "Live heading update, enabled=$isLiveHeadingEnabled ", Toast.LENGTH_SHORT
            ).show()

            GemCall.execute {
                if (isLiveHeadingEnabled.get()) {
                    startLiveHeading()
                } else {
                    stopLiveHeading()
                }
            }
        }

        // compass sync with mapView's rotation angle

        SdkSettings.onMapDataReady = {
            // Get the map view.
            surfaceView.mapView?.let { mapView ->
                // Change the compass icon rotation based on the map rotation at redndering.
                mapView.onViewRendered =
                    { _: EViewDataTransitionStatus, _: EViewCameraTransitionStatus ->
                        SdkCall.execute {
                            mapView.preferences?.rotationAngle?.let {
                                compass.rotation = -it.toFloat()
                            }
                        }
                    }

                // Align the map to north if the compass icon is pressed. 
                compass.setOnClickListener {
                    GemCall.execute {
                        mapView.alignNorthUp(Animation(EAnimation.Linear, 300))
                    }
                }
            }
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Will start listening for compass data. Compass's data needs to be smoothed by [HeadingSmoother].
     * The result, as rotation angle, will be applied to the map view.
     */
    private fun startLiveHeading() = GemCall.execute {
        dataSource = DataSourceFactory.produceLive()

        // start listening for compass data
        dataSource?.addListener(object : DataSourceListener() {
            override fun onNewData(dataType: EDataType) {
                GemCall.execute {
                    dataSource?.getLatestData(dataType)?.let {

                        // smooth new compass data
                        val heading = headingSmoother.update(CompassData(it).heading)

                        // update map view based on the recent changes
                        surfaceView.mapView?.preferences?.rotationAngle = heading
                    }
                }
            }
        }, EDataType.Compass)
    }

    /**
     * Will stop listening for compass data.
     */
    private fun stopLiveHeading() = GemCall.execute {
        dataSource?.release()
        dataSource = null
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

    companion object {
        fun buttonAsStart(context: Context, button: FloatingActionButton?) {
            button ?: return

            val tag = "start"
            val backgroundTintList =
                AppCompatResources.getColorStateList(context, R.color.green)
            val drawable = ContextCompat.getDrawable(
                context, android.R.drawable.ic_media_play
            )

            button.tag = tag
            button.setImageDrawable(drawable)
            button.backgroundTintList = backgroundTintList
        }

        fun buttonAsStop(context: Context, button: FloatingActionButton?) {
            button ?: return

            val tag = "stop"
            val backgroundTintList =
                AppCompatResources.getColorStateList(context, R.color.red)
            val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_media_play)

            button.tag = tag
            button.setImageDrawable(drawable)
            button.backgroundTintList = backgroundTintList
        }
    }
}
