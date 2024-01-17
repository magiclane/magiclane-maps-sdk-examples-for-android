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

package com.magiclane.sdk.examples.gpxthumbnailimage

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EViewCameraTransitionStatus
import com.magiclane.sdk.d3scene.EViewDataTransitionStatus
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var mapThumbnailImageView: ShapeableImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: MaterialTextView
    private lateinit var gemOffscreenSurfaceView: GemOffscreenSurfaceView
    
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
            
            SdkCall.execute {
                val gpxAssetsFileName = "gpx/test_route.gpx"

                // Opens GPX input stream.
                val input = applicationContext.resources.assets.open(gpxAssetsFileName)

                // Produce a Path based on the data in the buffer.
                val path = Path.produceWithGpx(input) ?: return@execute
                
                showPath(path)
                
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
                }
            }
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
    
    private fun showPath(path: Path)
    {
        gemOffscreenSurfaceView.mapView?.let { mapView ->
            val coordinatesList = path.coordinates
            if (!coordinatesList.isNullOrEmpty())
            {
                val departureLmk = Landmark("", coordinatesList.first()).also { 
                    it.image = ImageDatabase().getImageById(SdkImages.Core.Waypoint_Start.value)
                }
                val destinationLmk = Landmark("", coordinatesList.last()).also {
                    it.image = ImageDatabase().getImageById(SdkImages.Core.Waypoint_Finish.value)
                }
                
                val highlightSettings = HighlightRenderSettings().also { 
                    it.options = EHighlightOptions.ShowLandmark
                    it.imageSize = 4.0
                }
                
                mapView.activateHighlightLandmarks(arrayListOf(departureLmk, destinationLmk), highlightSettings)
            }

            val pathCollection = mapView.preferences?.paths
            pathCollection?.add(
                path,
                colorBorder = Rgba.black(),
                colorInner = Rgba.orange(),
                szBorder = 0.5,
                szInner = 1.0
            )
            
            path.area?.let { area -> 
                val margin = 2 * padding
                mapView.centerOnRectArea(
                    area = area,
                    viewRc = Rect(margin, margin, thumbnailWidth - margin, thumbnailHeight - margin),
                    animation = Animation(EAnimation.Linear, 10)
                )
            }
        }
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
