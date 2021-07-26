/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView

    // Define a content store item so we can request the map styles from it.
    private val contentStore = ContentStore()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)

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
            Toast.makeText(this, "TOKEN REJECTED", Toast.LENGTH_LONG).show()
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

    private fun fetchAvailableStyles() = SdkCall.execute {
        // Call to the content store to asynchronously retrieve the list of map styles.
        contentStore.asyncGetStoreContentList(EContentType.ViewStyleHighRes,
            onStarted = {
                progressBar.visibility = View.VISIBLE
            },

            onCompleted = onCompleted@{ styles, reason, _ ->
                progressBar.visibility = View.GONE

                when (reason) {
                    SdkError.NoError -> {
                        if (styles.size == 0) return@onCompleted

                        // The map style items list is not empty or null so we will select an item.
                        var style = styles[0]
                        if (styles.size > 1) // does it have a middle element?
                            style = styles[(styles.size / 2) - 1]

                        startDownloadingStyle(style)
                    }
                    SdkError.Cancel -> {
                        // The action was cancelled.
                        return@onCompleted
                    }

                    else -> {
                        // There was a problem at retrieving the content store items.
                        Toast.makeText(
                            this@MainActivity,
                            "Content store service error: ${reason.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }

    private fun applyStyle(style: ContentStoreItem) = SdkCall.execute {
        // Apply the style to the main map view.
        gemSurfaceView.getDefaultMapView()?.preferences?.setMapStyleById(style.id)
    }

    private fun startDownloadingStyle(style: ContentStoreItem) = SdkCall.execute {
        val styleName = style.name ?: "NO_NAME"

        // Start downloading a map style item.
        style.asyncDownload(
            onStarted = {
                progressBar.visibility = View.VISIBLE
                Toast.makeText(
                    this@MainActivity, "Started downloading $styleName.",
                    Toast.LENGTH_SHORT
                ).show()
            },

            onCompleted = { _, _ ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@MainActivity,
                    "$styleName was downloaded. Applying style...",
                    Toast.LENGTH_SHORT
                ).show()

                applyStyle(style)
            }
        )
    }
}
