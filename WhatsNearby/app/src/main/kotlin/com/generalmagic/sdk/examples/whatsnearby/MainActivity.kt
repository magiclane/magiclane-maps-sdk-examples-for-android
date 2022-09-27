/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.whatsnearby

import android.Manifest
import android.app.Activity
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
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.util.GemUtil
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.generalmagic.sdk.util.Util.postOnMain
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private lateinit var listView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private var reference: Coordinates? = null
    private val searchService = SearchService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ results, errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    // No error encountered, we can handle the results.
                    if (results.isNotEmpty()) {
                        reference?.let { listView.adapter = CustomAdapter(it, results) }
                    } else {
                        // The search completed without errors, but there were no results found.
                        showToast("No results!")
                    }
                }

                GemError.Cancel -> {
                    // The search action was cancelled.
                }

                else -> {
                    // There was a problem at computing the search operation.
                    showToast("Search service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        listView = findViewById<RecyclerView?>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            addItemDecoration(DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation))

            setBackgroundResource(R.color.white)
            val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
        }


        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done after the network is connected.
            search()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/sign in and generate one. 
             */
            showToast("TOKEN REJECTED")
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (!GemSdk.initSdkWithDefaults(this)) {
            // The SDK initialization was not completed.
            finish()
        }

        /* 
        The SDK initialization completed with success, but for the search action to be executed
        properly the app needs permission to get your location.
        Not requesting this permission or not granting it will make the search fail.
         */
        requestPermissions(this)

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to the internet!", Toast.LENGTH_LONG).show()
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
        // If one of the location permissions is granted, we can do the search around action.
        val hasPermissions =
            PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (!hasPermissions) return@execute

        // Cancel any search that is in progress now.
        searchService.cancelSearch()

        PositionService.getCurrentPosition()?.let {
            reference = it

            // Search around position using the provided search preferences and/ or filter.
            searchService.searchAroundPosition(it)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

        postOnMain { search() }
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

    private fun showToast(text: String) = postOnMain {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
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

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) = SdkCall.execute {
        val meters = dataSet[position].coordinates?.getDistance(reference)?.toInt() ?: 0
        val dist = GemUtil.getDistText(meters, EUnitSystem.Metric, true)

        viewHolder.text.text = dataSet[position].name
        viewHolder.status.text = dist.first
        viewHolder.description.text = dist.second
    } ?: Unit

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getItemCount() = dataSet.size

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

