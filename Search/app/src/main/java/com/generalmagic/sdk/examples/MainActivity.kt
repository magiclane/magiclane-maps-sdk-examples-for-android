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
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null

    private var searchService = SearchService()

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

        val searchInput = findViewById<SearchView>(R.id.searchInput)

        searchInput.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchInput.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    applyFilter(newText ?: "")
                    return true
                }
            }
        )

        /// GENERAL MAGIC
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

    fun applyFilter(filter: String) {
        // Cancel any search that is in progress now.
        cancelSearch()

        listView?.adapter = CustomAdapter(arrayListOf())
        if (filter.trim().isNotEmpty()) {
            progressBar?.visibility = View.VISIBLE
        }

        // Search the requested filter.
        search(filter.trim())
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun cancelSearch() {
        SdkCall.execute { searchService.cancelSearch() }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun search(filter: String): Int {
        return SdkCall.execute {
            // Get the current position of the user.
            val position = PositionService().getPosition()
            val latitude = position?.getLatitude() ?: 0.0
            val longitude = position?.getLongitude() ?: 0.0

            val coordinates = Coordinates(latitude, longitude)
            searchService.preferences.setSearchAddresses(true)
            searchService.preferences.setSearchMapPOIs(true)
            searchService.searchByFilter(filter, coordinates) onCompleted@{ results, reason, _ ->
                val gemError = SdkError.fromInt(reason)
                if (gemError == SdkError.Cancel) return@onCompleted

                val adapter = CustomAdapter(results)
                listView?.adapter = adapter
                progressBar?.visibility = View.GONE

                if (reason == SdkError.NoError.value && results.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity, "No results!", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } ?: SdkError.Cancel.value
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

        val result = grantResults[permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)]
        if (result != PackageManager.PERMISSION_GRANTED) {
            terminateApp(this)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        return PermissionsHelper.requestPermissions(REQUEST_PERMISSIONS, activity, permissions.toTypedArray())
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
class CustomAdapter(private val dataSet: ArrayList<Landmark>) :
    RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item, viewGroup, false)

        return ViewHolder(view)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        viewHolder.textView.text = SdkCall.execute { dataSet[position].getName() }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getItemCount() = dataSet.size

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

