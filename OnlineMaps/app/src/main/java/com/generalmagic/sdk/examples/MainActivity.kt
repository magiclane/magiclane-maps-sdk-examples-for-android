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
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.examples.util.Util
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.Util.Companion.postOnMain

class MainActivity : AppCompatActivity() {
    var listView: RecyclerView? = null
    private val contentStore = ContentStore()
    var progressBar: ProgressBar? = null

    private val progressListener = object : ProgressListener() {
        override fun notifyStart(hasProgress: Boolean) {
            postOnMain { progressBar?.visibility = View.VISIBLE }
        }

        override fun notifyComplete(reason: Int, hint: String) {
            when (val gemError = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    var models: ArrayList<ContentStoreItem>? = null
                    // Get the list of maps that was retrieved in the content store.
                    val result = contentStore.getStoreContentList(EContentType.RoadMap.value)
                    if (result != null) models = result.first

                    if (!models.isNullOrEmpty()) {
                        // The map items list is not empty or null.
                        val item = models[0]
                        val itemName = item.getName()

                        // Define a listener to the progress of the map download action. 
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
                                        "$itemName was downloaded.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }

                        // Start downloading the first map item.
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
                        displayList(models)
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

    private fun displayList(models: ArrayList<ContentStoreItem>?) {
        if (models != null) {
            val adapter = CustomAdapter(models)
            listView?.adapter = adapter
        }
    }

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
        SdkInitHelper.onNetworkConnected = {
            // Defines an action that should be done after the network is connected.
            // Call to the content store to asynchronously retrieve the list of maps.
            contentStore.asyncGetStoreContentList(EContentType.RoadMap.value, progressListener)
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
            SdkCall.execute { dataSet[position].getName() + " (" + Util.formatSizeAsText(dataSet[position].getTotalSize()) + ")" }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getItemCount() = dataSet.size

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

