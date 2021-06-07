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
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView

    // Define a content store item so we can request the map styles from it.
    private val contentStore = ContentStore()

    // Define a listener that will let us know the progress of the map styles retrieving process.
    private val progressListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ reason, _ ->
            progressBar.visibility = View.GONE

            when (reason) {
                SdkError.NoError -> SdkCall.execute {
                    // No error encountered, we can handle the results.
                    // Get the list of map styles that was retrieved in the content store.
                    contentStore.getStoreContentList(EContentType.ViewStyleHighRes.value)?.first?.let { styles ->
                        // The map style items list is not empty or null so we will select an item.
                        val style = if (styles.size > 1) styles[(styles.size / 2) - 1] else styles[0]
                        val styleName = style.getName() ?: "NO_NAME"

                        // Define a listener to the progress of the map style download action. 
                        val downloadProgressListener = ProgressListener.create(
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

                                SdkCall.execute {
                                    // Apply the style to the main map view.
                                    gemSurfaceView.getDefaultMapView()?.preferences()
                                        ?.setMapStyleById(style.getId())
                                }
                            },

                            postOnMain = true
                        )

                        // Start downloading a map style item.
                        style.asyncDownload(
                            downloadProgressListener, GemSdk.EDataSavePolicy.UseDefault, true
                        )
                    }
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
        },

        postOnMain = true
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)

        SdkSettings.onMapDataReady = {
            // Call to the content store to asynchronously retrieve the list of map styles.
            contentStore.asyncGetStoreContentList(
                EContentType.ViewStyleHighRes.value,
                progressListener
            )
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
}
