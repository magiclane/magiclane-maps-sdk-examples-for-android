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

import android.Manifest
import android.app.Activity
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
import com.generalmagic.sdk.core.EUnitSystem
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.examples.util.Util
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    var listView: RecyclerView? = null
    var progressBar: ProgressBar? = null

    private val searchService = SearchService()

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
            search()
        }

        searchService.onCompleted = onCompleted@{ results, reason, _ ->
            progressBar?.visibility = View.GONE

            when (val gemError = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    val reference = Util.getMyPosition() ?: return@onCompleted

                    val adapter = CustomAdapter(reference, results)
                    listView?.adapter = adapter

                    if (results.isEmpty()) {
                        // The search completed without errors, but there were no results found.
                        Toast.makeText(
                            this@MainActivity, "No results!", Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                SdkError.Cancel -> {
                    // The search action was cancelled.
                    return@onCompleted
                }

                else -> {
                    // There was a problem at computing the search operation.
                    Toast.makeText(
                        this@MainActivity,
                        "Search service error: ${gemError.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        SdkInitHelper.onCancel = {
            // Defines what should be executed when the SDK initialization is cancelled.
            cancelSearch()
        }

        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            // The SDK initialization was not completed.
            finish()
        }

        /* 
        The SDK initialization completed with success, but for the search action to be executed
        the app needs some permissions.
        Not requesting this permissions or not granting them will make the search to not work.
         */
        requestPermissions(this)
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

    private fun search() {
        // If one of the location permissions is granted, we can do the search around action.
        val hasPermissions = 
            PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (hasPermissions) {
            Util.getMyPosition()?.let { myPosition ->
                searchAround(myPosition)
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun searchAround(reference: Coordinates) {
        SdkCall.execute {
            // Cancel any search that is in progress now.
            cancelSearch()

            // Set the necessary preferences.
            searchService.preferences.setSearchAddresses(true)
            searchService.preferences.setSearchMapPOIs(true)

            // Search around position using the provided search preferences and/ or filter.
            searchService.searchAroundPosition(
                reference, ""
            )
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun cancelSearch() = SdkCall.execute {
        searchService.cancelSearch()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        com.generalmagic.sdk.util.Util.postOnMain { search() }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions.toTypedArray()
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This custom adapter is made to facilitate the displaying of the data from the model
 * and to decide how it is displayed.
 */
class CustomAdapter(private val reference: Coordinates, private val dataSet: ArrayList<Landmark>) :
    RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.text)
        val status: TextView = view.findViewById(R.id.status_text)
        val description: TextView = view.findViewById(R.id.status_description)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item, viewGroup, false)

        return ViewHolder(view)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val dist: Pair<String, String>
        val meters = SdkCall.execute {
            return@execute dataSet[position].getCoordinates()?.getDistance(reference)?.toInt()
        } ?: 0

        dist = Util.getDistText(meters, EUnitSystem.Metric, true)

        viewHolder.text.text = SdkCall.execute { dataSet[position].getName() }
        viewHolder.status.text = SdkCall.execute { dist.first }
        viewHolder.description.text = SdkCall.execute { dist.second }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getItemCount() = dataSet.size

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

