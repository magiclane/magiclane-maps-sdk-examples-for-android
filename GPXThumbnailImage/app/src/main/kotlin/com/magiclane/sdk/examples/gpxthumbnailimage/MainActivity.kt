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

package com.magiclane.sdk.examples.gpxthumbnailimage

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.routesandnavigation.ELineType
import com.magiclane.sdk.routesandnavigation.RouteRenderSettings
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var progressBar: ProgressBar
    private lateinit var gemOffscreenSurfaceView: GemOffscreenSurfaceView
    private lateinit var mapView: ImageView
    private var mapBitmap: Bitmap? = null
    private var mapWidth = 0
    private var mapHeight = 0
    private var padding = 0

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, errorCode, _ ->
            when (errorCode)
            {
                GemError.NoError ->
                {
                    if (routes.isNotEmpty())
                    {
                        SdkCall.execute {
                            val routeRenderSettings = RouteRenderSettings()
                            routeRenderSettings.innerColor = Rgba.blue()
                            routeRenderSettings.outerColor = Rgba.blue()
                            routeRenderSettings.innerSize = 1.0
                            routeRenderSettings.outerSize = 0.0
                            routeRenderSettings.lineType = ELineType.LT_Solid

                            gemOffscreenSurfaceView.mapView?.presentRoute(routes[0],
                                                                          animation = Animation(listener = ProgressListener.create(onCompleted = { _: ErrorCode, _: String ->
                                                                                                                                   Util.postOnMainDelayed ({
                                                                                                                                        progressBar.visibility = View.GONE
                                                                                                                                        mapView.setImageBitmap(mapBitmap)
                                                                                                                                    }, 1000)
                                                                                                                                  }),
                                                                                                 animation = EAnimation.Linear,
                                                                                                 duration = 100),
                                                                           edgeAreaInsets = Rect(padding, padding, padding, padding),
                                                                           routeRenderSettings = routeRenderSettings)
                        }
                    }
                }
                GemError.Cancel ->
                {
                    progressBar.visibility = View.GONE
                    // No action.
                }
                else ->
                {
                    progressBar.visibility = View.GONE
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map_view)
        mapWidth = resources.getDimensionPixelSize(R.dimen.map_width)
        mapHeight = resources.getDimensionPixelSize(R.dimen.map_height)
        padding = resources.getDimensionPixelSize(R.dimen.padding)

        progressBar = findViewById(R.id.progressBar)

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

        if (!GemSdk.initSdkWithDefaults(this))
        {
            // The SDK initialization was not completed.
            finish()
        }

        gemOffscreenSurfaceView = GemOffscreenSurfaceView(mapWidth, mapHeight, resources.displayMetrics.densityDpi, onDefaultMapViewCreated = {
            calculateRouteFromGPX()
        },
        onMapRendered = { bitmap ->
            mapBitmap = bitmap
        })
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        gemOffscreenSurfaceView.destroy()

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

    private fun calculateRouteFromGPX() = SdkCall.execute {
        val gpxAssetsFilename = "gpx/test_route.gpx"

        // Opens GPX input stream.
        val input = applicationContext.resources.assets.open(gpxAssetsFilename)

        // Produce a Path based on the data in the buffer.
        val track = Path.produceWithGpx(input/*.readBytes()*/) ?: return@execute

        // Set the transport mode to bike and calculate the route.
        routingService.calculateRoute(track, ERouteTransportMode.Car)
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
}

// -------------------------------------------------------------------------------------------------------------------------------
