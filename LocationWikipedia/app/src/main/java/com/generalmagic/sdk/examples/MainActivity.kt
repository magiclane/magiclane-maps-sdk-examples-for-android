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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.core.ExternalInfo
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError

class MainActivity : AppCompatActivity() {
    private var locationName: TextView? = null
    private var locationWiki: TextView? = null
    private var progressBar: ProgressBar? = null

    private val externalInfoService = ExternalInfo()

    private val searchService = SearchService()

    private lateinit var wikipediaProgressListener: ProgressListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationName = findViewById(R.id.locationName)
        locationWiki = findViewById(R.id.locationWiki)
        progressBar = findViewById(R.id.progressBar)

        /// General magic
        SdkInitHelper.onNetworkConnected = {
            search()
        }

        wikipediaProgressListener = ProgressListener.create(
            onStarted = {
                progressBar?.visibility = View.VISIBLE
            },
            onCompleted = { _, _ ->
                val wikiDescription = SdkCall.execute {
                    "(" + getWikiPageURL() + ")\n" + getWikiPageDescription()
                }
                locationWiki?.text = wikiDescription
                progressBar?.visibility = View.GONE
            })

        searchService.onStarted = {
            progressBar?.visibility = View.VISIBLE
        }

        searchService.onCompleted = onCompleted@{ results, reason, _ ->
            val gemError = SdkError.fromInt(reason)
            if (gemError == SdkError.Cancel) return@onCompleted

            if (results.isNotEmpty()) {
                val name = SdkCall.execute { results[0].getName() }
                locationName?.text = name
                requestWiki(results[0])
            }

            progressBar?.visibility = View.GONE

            if (reason == SdkError.NoError.value && results.isEmpty()) {
                Toast.makeText(
                    this@MainActivity, "No results!", Toast.LENGTH_SHORT
                ).show()
            }
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            terminateApp(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SdkInitHelper.deinit()
    }

    override fun onBackPressed() {
        terminateApp(this)
    }

    private fun search() = SdkCall.execute {
        val name = "Statue Of Liberty"
        val coordinates = Coordinates(40.68917616239407, -74.04452185917404)
        SdkCall.execute {
            searchService.preferences.setSearchMapPOIs(true)
            searchService.searchByFilter(name, coordinates)
        }
    }

    private fun requestWiki(value: Landmark) {
        SdkCall.execute { externalInfoService.requestWikiInfo(value, wikipediaProgressListener) }
    }

    private fun getWikiPageDescription(): String {
        return SdkCall.execute { externalInfoService.getWikiPageDescription() } ?: ""
    }

    private fun getWikiPageURL(): String {
        return SdkCall.execute { externalInfoService.getWikiPageURL() } ?: ""
    }

//    private fun cancelSearch() = SdkCall.execute {
//        search.service.cancelSearch(search.listener)
//    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray
//    ) {
//        PermissionsHelper.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
//        search()
//    }
}

//class CustomAdapter(private val reference: Coordinates, private val dataSet: ArrayList<Landmark>) :
//    RecyclerView.Adapter<CustomAdapter.ViewHolder>() {
//    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val text: TextView = view.findViewById(R.id.text)
//        val status: TextView = view.findViewById(R.id.status_text)
//        val description: TextView = view.findViewById(R.id.status_description)
//    }
//
//    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
//
//        val view = LayoutInflater.from(viewGroup.context)
//            .inflate(R.layout.list_item, viewGroup, false)
//
//        return ViewHolder(view)
//    }
//
//    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
//        val dist: Pair<String, String>
//        val meters = SdkCall.execute {
//            return@execute dataSet[position].getCoordinates()?.getDistance(reference)?.toInt()
//        } ?: 0
//
//        dist = Util.getDistText(meters, EUnitSystem.Metric, true)
//
//        viewHolder.text.text = SdkCall.execute { dataSet[position].getName() }
//        viewHolder.status.text = SdkCall.execute { dist.first }
//        viewHolder.description.text = SdkCall.execute { dist.second }
//    }
//
//    override fun getItemCount() = dataSet.size
//}

