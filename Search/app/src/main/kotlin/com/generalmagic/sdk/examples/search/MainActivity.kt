/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.search

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
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var toolbar: Toolbar? = null

    private var searchService = SearchService(
        onCompleted = { results, errorCode, _ ->
            progressBar?.visibility = View.GONE
            when (errorCode) {
                GemError.NoError -> {
                    // No error encountered, we can handle the results.
                    if (results.isNotEmpty()) {
                        listView?.adapter = CustomAdapter(results)
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
        toolbar = findViewById(R.id.toolbar)
        listView = findViewById<RecyclerView?>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            addItemDecoration(DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation))

            setBackgroundResource(R.color.white)
            
            val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
        }
        
        setSupportActionBar(toolbar)

        findViewById<SearchView>(R.id.searchInput).apply {
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        applyFilter(newText ?: "")
                        return true
                    }
                }
            )
            
            requestFocus()
        }

        /// GENERAL MAGIC
        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/sign in and generate one. 
             */
            showToast("TOKEN REJECTED")
        }

        if (!GemSdk.initSdkWithDefaults(this)) {
            // The SDK initialization was not completed.
            finish()
        }

        /* 
        The SDK initialization completed with success, but for the search action to be executed
        the app needs some permissions.
        Not requesting this permissions or not granting them will make the search to not work.
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

    private fun search(filter: String): Int = SdkCall.execute {
        searchService.searchByFilter(filter)
    } ?: GemError.Cancel

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

        val result = grantResults[permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)]
        if (result != PackageManager.PERMISSION_GRANTED) {
            finish()
            exitProcess(0)
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

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions.toTypedArray()
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showToast(text: String) = Util.postOnMain {
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

        viewHolder.textView.text = SdkCall.execute { dataSet[position].name }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getItemCount() = dataSet.size

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

