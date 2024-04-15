// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.gpxthumbnailimagewithrouting

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EViewCameraTransitionStatus
import com.magiclane.sdk.d3scene.EViewDataTransitionStatus
import com.magiclane.sdk.routesandnavigation.ELineType
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RouteRenderSettings
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var gemOffscreenSurfaceView: GemOffscreenSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: MaterialTextView
    private lateinit var mapThumbnailImageView: ShapeableImageView

    private var screenshotTaken = false

    private val thumbnailWidth by lazy {
        resources.getDimension(R.dimen.thumbnail_width).toInt()
    }

    private val thumbnailHeight by lazy {
        resources.getDimension(R.dimen.thumbnail_height).toInt()
    }

    private val padding by lazy {
        resources.getDimension(R.dimen.padding).toInt()
    }

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
            statusText.text = getString(R.string.calculating_route)
        },

        onCompleted = onCompleted@{ routes, errorCode, _ ->
            when (errorCode)
            {
                GemError.NoError ->
                {
                    if (routes.isEmpty()) return@onCompleted
                    
                    statusText.text = getString(R.string.route_calculation_completed)

                    SdkCall.execute {
                        gemOffscreenSurfaceView.mapView?.let { mapView ->
                            mapView.preferences?.mapLabelsFading = false
                            mapView.onViewRendered = onViewRendered@{ tivStatus, camStatus ->
                                if (screenshotTaken) return@onViewRendered

                                if (tivStatus == EViewDataTransitionStatus.Complete && camStatus == EViewCameraTransitionStatus.Stationary)
                                {
                                    Util.postOnMain { statusText.text = getString(R.string.taking_screenshot) } 
                                    gemOffscreenSurfaceView.takeScreenshot { bitmap ->
                                        Util.postOnMain {
                                            mapThumbnailImageView.setImageBitmap(bitmap)
                                            progressBar.isVisible = false
                                            statusText.text = getString(R.string.screenshot_taken)
                                        }
                                        screenshotTaken = true
                                    }

                                    gemOffscreenSurfaceView.mapView?.onViewRendered = null
                                }
                            }

                            val margin = 2 * padding
                            val routeRenderSettings = RouteRenderSettings().also {
                                it.innerColor = Rgba.orange()
                                it.outerColor = Rgba.black()
                                it.innerSize = 1.0
                                it.outerSize = 0.5
                                it.lineType = ELineType.LT_Solid
                            }
                            mapView.presentRoute(
                                routes[0],
                                animation = Animation(animation = EAnimation.Linear, duration = 10),
                                edgeAreaInsets = Rect(margin, margin, margin, margin),
                                routeRenderSettings = routeRenderSettings
                            )
                        }
                    }
                }

                GemError.Cancel ->
                {
                    progressBar.isVisible = false
                    // No action.
                }

                else ->
                {
                    progressBar.isVisible = false
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

        mapThumbnailImageView = findViewById(R.id.map_thumbnail_image)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)

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
            showDialog("You must be connected to the internet!") {
                exitProcess(0)
            }
        }

        if (!GemSdk.initSdkWithDefaults(this))
        {
            // The SDK initialization was not completed.
            finish()
        }

        gemOffscreenSurfaceView = GemOffscreenSurfaceView(
            thumbnailWidth, 
            thumbnailHeight, 
            resources.displayMetrics.densityDpi
        )

        statusText.text = getString(R.string.waiting_for_data)
        
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->  
            if (!isReady) return@onMapDataReady

            statusText.text = getString(R.string.map_data_ready)
            
            calculateRouteFromGPX()
        }

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back is pressed... Finishing the activity
                finish()
                exitProcess(0)
            }
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
    private fun showDialog(text: String, dialogButtonCallback : () -> Unit = {})
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
                dialogButtonCallback()
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