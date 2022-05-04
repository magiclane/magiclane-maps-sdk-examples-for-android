/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.voicedownloading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.GemUtil
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private var listView: RecyclerView? = null
    private val contentStore = ContentStore()
    private var progressBar: ProgressBar? = null

    private val progressListener = ProgressListener.create(
        onStarted = {
            progressBar?.visibility = View.VISIBLE
        },

        onCompleted = { errorCode, _ ->
            progressBar?.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    SdkCall.execute {
                        // No error encountered, we can handle the results.
                        var models: ArrayList<ContentStoreItem>? = null
                        // Get the list of voices that was retrieved in the content store.
                        val result = contentStore.getStoreContentList(EContentType.HumanVoice)
                        if (result != null)
                            models = result.first

                        if (!models.isNullOrEmpty()) {
                            // The voice items list is not empty or null.
                            val voiceItem = models[0]
                            val itemName = voiceItem.name

                            // Start downloading the first voice item.
                            SdkCall.execute {
                                voiceItem.asyncDownload(
                                    GemSdk.EDataSavePolicy.UseDefault,
                                    true,
                                    onStarted = {
                                        progressBar?.visibility = View.VISIBLE
                                        showToast("Started downloading $itemName.")
                                    },
                                    onCompleted = { _, _ ->
                                        progressBar?.visibility = View.GONE
                                        showToast("$itemName was downloaded.")
                                    }
                                )
                            }
                        }

                        displayList(models)
                    }
                }

                GemError.Cancel -> {
                    // The action was cancelled.
                }

                else -> {
                    // There was a problem at retrieving the content store items.
                    showToast("Content store service error: ${GemError.getMessage(errorCode)}")
                }
            }
        },

        postOnMain = true
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.list_view)
        progressBar = findViewById(R.id.progressBar)
        val layoutManager = LinearLayoutManager(this)
        listView?.layoutManager = layoutManager

        val separator = DividerItemDecoration(applicationContext, layoutManager.orientation)
        listView?.addItemDecoration(separator)

        listView?.setBackgroundResource(R.color.white)
        val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
        listView?.setPadding(lateralPadding, 0, lateralPadding, 0)

        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            SdkCall.execute {
                // Defines an action that should be done after the network is connected.
                // Call to the content store to asynchronously retrieve the list of voices.
                contentStore.asyncGetStoreContentList(
                    EContentType.HumanVoice,
                    progressListener
                )
            }
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
            // The SDK initialization was not completed.
            finish()
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
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

    private fun displayList(models: ArrayList<ContentStoreItem>?) {
        if (models != null) {
            val adapter = CustomAdapter(models)
            listView?.adapter = adapter
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showToast(text: String) = Util.postOnMain {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This custom adapter is made to facilitate the displaying of the data from the model
 * and to decide how it is displayed.
 */
class CustomAdapter(private val dataSet: ArrayList<ContentStoreItem>) :
    RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.text)
        val statusText: TextView = view.findViewById(R.id.status_text)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item, viewGroup, false)

        return ViewHolder(view)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.text.text =
            SdkCall.execute { dataSet[position].name + " (" + GemUtil.formatSizeAsText(dataSet[position].totalSize) + ")" }

        viewHolder.statusText.text =
            SdkCall.execute { getCountryName(dataSet[position]) + " - " + getLanguage(dataSet[position]) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getItemCount() = dataSet.size

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun getCountryName(item: ContentStoreItem): String {
        item.countryCodes?.let { codes ->
            return MapDetails().getCountryName(codes[0]) ?: ""
        }
        return ""
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun getLanguage(item: ContentStoreItem): String {
        item.contentParameters?.let { parameters ->
            val nativeLanguageParam = "native_language"
            for (param in parameters) {
                if (param.name?.lowercase()?.compareTo(nativeLanguageParam) == 0) {
                    return param.valueString
                }
            }
        }
        return ""
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

