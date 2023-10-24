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

package com.magiclane.sdk.examples.downloadingonboardmap

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity: AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var listView: RecyclerView

    private lateinit var statusText: TextView

    private lateinit var progressBar: ProgressBar

    private val contentStore = ContentStore()

    private val flagBitmapsMap = HashMap<String, Bitmap?>()

    private val kDefaultToken = "YOUR_TOKEN"

    private val progressListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
            showStatusMessage("Started content store service.")
        },
        onCompleted = { errorCode, _ ->
            progressBar.visibility = View.GONE
            showStatusMessage("Content store service completed with error code: $errorCode")

            when (errorCode)
            {
                GemError.NoError ->
                {
                    SdkCall.execute {
                        // No error encountered, we can handle the results.
                        val models = contentStore.getStoreContentList(EContentType.RoadMap)?.first

                        if (!models.isNullOrEmpty())
                        {
                            // The map items list is not empty or null.
                            val mapItem = models[0]
                            val itemName = mapItem.name

                            // Define a listener to the progress of the map download action.
                            val downloadProgressListener = ProgressListener.create(onStarted = {
                                                                                        showStatusMessage("Started downloading $itemName.")
                                                                                   },
                                                                                   onProgress = { 
                                                                                       listView.adapter?.notifyItemChanged(0)
                                                                                   }, 
                                                                                   onCompleted = { errorCode, _ ->
                                                                                       listView.adapter?.notifyItemChanged(0)
                                                                                       if (errorCode == GemError.NoError)
                                                                                       {
                                                                                           showStatusMessage("$itemName was downloaded.")
                                                                                       }
                                                                                       else
                                                                                       {
                                                                                           showDialog("Download item error: ${GemError.getMessage(errorCode)}")
                                                                                       }
                                                                                   })

                            // Start downloading the first map item.
                            SdkCall.execute {
                                mapItem.asyncDownload(downloadProgressListener, GemSdk.EDataSavePolicy.UseDefault, true)
                            }
                        }

                        displayList(models)
                    }
                }
                GemError.Cancel ->
                {
                    // The action was cancelled.
                }
                else ->
                {
                    // There was a problem at retrieving the content store items.
                    showDialog("Content store service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.status_text)
        listView = findViewById<RecyclerView?>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            val separator = DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation)
            addItemDecoration(separator)

            val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
            
            itemAnimator = null
        }

        val loadMaps = {
            val loadMapsCatalog = {
                SdkCall.execute {
                    // Call to the content store to asynchronously retrieve the list of maps.
                    contentStore.asyncGetStoreContentList(EContentType.RoadMap, progressListener)
                }
            }

            val token = GemSdk.getTokenFromManifest(this)

            if (!token.isNullOrEmpty() && (token != kDefaultToken))
            {
                loadMapsCatalog()
            }
            else // if token is not present try to avoid content server requests limitation by delaying the maps catalog request
            {
                Handler(Looper.getMainLooper()).postDelayed({
                    loadMapsCatalog()
                }, 3000)
            }
        }

        SdkSettings.onMapDataReady = { mapReady ->
            if (mapReady)
            {
                loadMaps()
            }
        }

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (!GemSdk.initSdkWithDefaults(this))
        {
            // The SDK initialization was not completed.
            finish()
        }

        if (!Util.isInternetConnected(this))
        {
            progressBar.visibility = View.GONE
            showDialog("You must be connected to the internet!")
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

    private fun displayList(models: ArrayList<ContentStoreItem>?)
    {
        if (models != null)
        {
            val adapter = CustomAdapter(models)
            listView.adapter = adapter
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

    private fun showStatusMessage(text: String)
    {
        if (!statusText.isVisible)
        {
            statusText.visibility = View.VISIBLE
        }
        statusText.text = text
    }

    // ---------------------------------------------------------------------------------------------------------------------------
    
    inner class CustomAdapter(private val dataSet: ArrayList<ContentStoreItem>): RecyclerView.Adapter<CustomAdapter.ViewHolder>()
    {
        // -----------------------------------------------------------------------------------------------------------------------

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            val text: TextView = view.findViewById(R.id.text)
            val description: TextView = view.findViewById(R.id.description)
            val imageView: ImageView = view.findViewById(R.id.icon)
            val progressBar: ProgressBar = view.findViewById(R.id.item_progress_bar)
            val statusImageView: ImageView = view.findViewById(R.id.status_icon)
        }
        
        // -----------------------------------------------------------------------------------------------------------------------

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
        {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)
            return ViewHolder(view)
        }
        
        // -----------------------------------------------------------------------------------------------------------------------

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int)
        {
            viewHolder.apply {
                text.text = SdkCall.execute { dataSet[position].name }
                description.text = SdkCall.execute { GemUtil.formatSizeAsText(dataSet[position].totalSize) }
                imageView.setImageBitmap(SdkCall.execute { getFlagBitmap(dataSet[position]) })

                statusImageView.visibility = View.GONE
                progressBar.visibility = View.INVISIBLE
                when (SdkCall.execute { dataSet[position].status })
                {
                    EContentStoreItemStatus.Completed ->
                    {
                        statusImageView.visibility = View.VISIBLE
                        progressBar.visibility = View.INVISIBLE
                    }

                    EContentStoreItemStatus.DownloadRunning ->
                    {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = SdkCall.execute { dataSet[position].downloadProgress } ?: 0
                    }

                    else -> return
                }
            }
        }
        
        // -----------------------------------------------------------------------------------------------------------------------

        override fun getItemCount() = dataSet.size

        // -----------------------------------------------------------------------------------------------------------------------

        private fun getFlagBitmap(item: ContentStoreItem): Bitmap?
        {
            item.countryCodes?.let { codes ->
                if (codes.isNotEmpty())
                {
                    val isoCode = codes[0]
                    if (!flagBitmapsMap.containsKey(isoCode))
                    {
                        val size = resources.getDimension(R.dimen.icon_size).toInt()
                        flagBitmapsMap[isoCode] = MapDetails().getCountryFlag(isoCode)?.asBitmap(size, size)
                    }

                    return flagBitmapsMap[isoCode]
                }
                return null
            }
            return null
        }

        // -----------------------------------------------------------------------------------------------------------------------
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------

