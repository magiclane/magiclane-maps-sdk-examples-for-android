/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.locationwikipedia

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.ExternalInfo
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private var locationName: TextView? = null
    private var locationWiki: TextView? = null
    private var progressBar: ProgressBar? = null

    private val externalInfoService = ExternalInfo()

    private val searchService = SearchService(
        onStarted = {
            progressBar?.visibility = View.VISIBLE
        },

        onCompleted = { results, errorCode, _ ->
            progressBar?.visibility = View.GONE

            if (errorCode == GemError.NoError) {
                if (results.isNotEmpty()) {
                    val name = SdkCall.execute { results[0].name }
                    locationName?.text = name
                    requestWiki(results[0])
                } else {
                    // The search completed without errors, but there were no results found.
                    showToast("No results!")
                }
            }
        }
    )

    private val wikipediaProgressListener = ProgressListener.create(
        onStarted = {
            progressBar?.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            val wikiDescription = SdkCall.execute {
                "(" + getWikiPageURL() + ")\n" + getWikiPageDescription()
            }
            locationWiki?.text = wikiDescription
            progressBar?.visibility = View.GONE
        },

        postOnMain = true
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationName = findViewById(R.id.locationName)
        locationWiki = findViewById(R.id.locationWiki)
        progressBar = findViewById(R.id.progressBar)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            search()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            showToast("TOKEN REJECTED")
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (!GemSdk.initSdkWithDefaults(this)) {
            finish()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        finish()
        exitProcess(0)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun search() = SdkCall.execute {
        val name = "Statue Of Liberty"
        val coordinates = Coordinates(40.68917616239407, -74.04452185917404)
        searchService.searchByFilter(name, coordinates)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun requestWiki(value: Landmark) = SdkCall.execute {
        externalInfoService.requestWikiInfo(value, wikipediaProgressListener)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun getWikiPageDescription(): String {
        return SdkCall.execute { externalInfoService.wikiPageDescription } ?: ""
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun getWikiPageURL(): String {
        return SdkCall.execute { externalInfoService.wikiPageURL } ?: ""
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showToast(text: String) = Util.postOnMain {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}


