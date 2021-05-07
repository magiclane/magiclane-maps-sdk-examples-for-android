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

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.Util.postOnMain

class MainActivity : AppCompatActivity() {
    private var mainMapView: MapView? = null
    private var progressBar: ProgressBar? = null
    
    // Define a content store item so we can request the map styles from it.
    private val contentStore = ContentStore()

    // Define a listener that will let us know the progress of the map styles retrieving process.
    private val progressListener = object : ProgressListener() {
        override fun notifyStart(hasProgress: Boolean) {
            postOnMain { progressBar?.visibility = View.VISIBLE }
        }

        override fun notifyComplete(reason: Int, hint: String) {
            when (val gemError = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    var models: ArrayList<ContentStoreItem>? = null
                    // Get the list of map styles that was retrieved in the content store.
                    val result =
                        contentStore.getStoreContentList(EContentType.ViewStyleHighRes.value)
                    if (result != null) models = result.first

                    if (!models.isNullOrEmpty()) {
                        // The map items list is not empty or null so we will select an item.
                        val item = if (models.size > 1) models[(models.size / 2) - 1] else models[0]
                        val itemId = item.getId()
                        val itemName = item.getName()

                        // Define a listener to the progress of the style download action. 
                        val downloadProgressListener = object : ProgressListener() {
                            override fun notifyStart(hasProgress: Boolean) {
                                postOnMain {
                                    progressBar?.visibility = View.VISIBLE
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Started downloading $itemName.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            override fun notifyComplete(reason: Int, hint: String) {
                                postOnMain {
                                    progressBar?.visibility = View.GONE
                                    Toast.makeText(
                                        this@MainActivity,
                                        "$itemName was downloaded. Applying style...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                // Apply the style to the main map view.
                                mainMapView?.preferences()?.setMapStyleById(itemId)
                            }
                        }

                        // Start downloading a map style item.
                        SdkCall.execute {
                            item.asyncDownload(
                                downloadProgressListener,
                                GemSdk.EDataSavePolicy.UseDefault,
                                true
                            )
                        }
                    }

                    postOnMain {
                        progressBar?.visibility = View.GONE
                    }
                }

                SdkError.Cancel -> {
                    // The action was cancelled.
                    return
                }

                else -> {
                    // There was a problem at retrieving the content store items.
                    Toast.makeText(
                        this@MainActivity,
                        "Content store service error: ${gemError.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)

        /// GENERAL MAGIC
        val mapSurface = findViewById<GemSurfaceView>(R.id.gem_surface)
        mapSurface.onScreenCreated = { screen ->
            // Defines an action that should be done after the screen is created.
            SdkCall.execute {
                /* 
                Define a rectangle in which the map view will expand.
                Predefined value of the offsets is 0.
                Value 1 means the offset will take 100% of available space.
                 */
                val mainViewRect = RectF(0.0f, 0.0f, 1.0f, 1.0f)
                // Produce a map view and establish that it is the main map view.
                val mapView = MapView.produce(screen, mainViewRect)
                mainMapView = mapView
            }
        }

        SdkInitHelper.onNetworkConnected = {
            SdkCall.execute {
                // Defines an action that should be done after the network is connected.
                // Call to the content store to asynchronously retrieve the list of map styles.
                contentStore.asyncGetStoreContentList(
                    EContentType.ViewStyleHighRes.value,
                    progressListener
                )
            }
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            // The SDK initialization was not completed.
            finish()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        SdkInitHelper.deinit()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        terminateApp(this)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
