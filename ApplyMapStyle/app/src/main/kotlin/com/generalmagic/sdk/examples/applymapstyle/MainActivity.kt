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

package com.generalmagic.sdk.examples.applymapstyle

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var statusText: TextView
    private lateinit var statusProgressBar: ProgressBar
    private lateinit var styleContainer: ConstraintLayout
    private lateinit var styleName: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadedIcon: ImageView

    // Define a content store item so we can request the map styles from it.
    private val contentStore = ContentStore()

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progress_bar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        statusText = findViewById(R.id.status_text)
        statusProgressBar = findViewById(R.id.status_progress_bar)
        styleContainer = findViewById(R.id.style_container)
        styleName = findViewById(R.id.style_name)
        downloadProgressBar = findViewById(R.id.download_progress_bar)
        downloadedIcon = findViewById(R.id.downloaded_icon)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            fetchAvailableStyles()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to internet!")
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

    private fun showStatusMessage(text: String, withProgress: Boolean = false)
    {
        if (!statusText.isVisible)
        {
            statusText.visibility = View.VISIBLE
        }
        statusText.text = text
        
        if (withProgress)
        {
            statusProgressBar.visibility = View.VISIBLE   
        }
        else
        {
            statusProgressBar.visibility = View.GONE
        }
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

    private fun fetchAvailableStyles() = SdkCall.execute {
        // Call to the content store to asynchronously retrieve the list of map styles.
        contentStore.asyncGetStoreContentList(EContentType.ViewStyleHighRes,
            onStarted = {
                progressBar.visibility = View.VISIBLE
                showStatusMessage("Started content store service.")
            },

            onCompleted = onCompleted@{ styles, errorCode, _ ->
                progressBar.visibility = View.GONE
                showStatusMessage("Content store service completed with error code: $errorCode")

                when (errorCode) {
                    GemError.NoError -> {
                        if (styles.size == 0) return@onCompleted

                        // The map style items list is not empty or null so we will select an item.
                        var style = styles[0]
                        if (styles.size > 1) // does it have a middle element?
                            style = styles[(styles.size / 2) - 1]
                        
                        showStatusMessage("Preparing download.", true)
                        startDownloadingStyle(style)
                    }
                    GemError.Cancel -> {
                        // The action was cancelled.
                        return@onCompleted
                    }

                    else -> {
                        // There was a problem at retrieving the content store items.
                        showDialog("Content store service error: ${GemError.getMessage(errorCode)}")
                    }
                }
            })
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun applyStyle(style: ContentStoreItem) = SdkCall.execute {
        // Apply the style to the main map view.
        gemSurfaceView.mapView?.preferences?.setMapStyleById(style.id)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startDownloadingStyle(style: ContentStoreItem) = SdkCall.execute {
        val styleName = style.name ?: "NO_NAME"

        // Start downloading a map style item.
        style.asyncDownload(
            onStarted = {
                showStatusMessage("Started downloading $styleName.")
                onDownloadStarted(style)
            },

            onStatusChanged = { status ->
                onStatusChanged(status)
            },

            onProgress = { progress ->
                onProgressUpdated(progress)
            },

            onCompleted = { _, _ ->
                showStatusMessage("$styleName was downloaded. Applying style...")
                applyStyle(style)
                showStatusMessage("Style $styleName was applied.")
                styleContainer.visibility = View.GONE
            }
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun onDownloadStarted(style: ContentStoreItem)
    {
        styleContainer.visibility = View.VISIBLE
        styleName.text = SdkCall.execute { style.name }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun onStatusChanged(status: Int)
    {
        when (EContentStoreItemStatus.values()[status])
        {
            EContentStoreItemStatus.Completed ->
            {
                downloadedIcon.visibility = View.VISIBLE
                downloadProgressBar.visibility = View.INVISIBLE
            }

            EContentStoreItemStatus.DownloadRunning ->
            {
                downloadedIcon.visibility = View.GONE
                downloadProgressBar.visibility = View.VISIBLE
            }

            else -> return
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun onProgressUpdated(progress: Int)
    {
        downloadProgressBar.progress = progress
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
