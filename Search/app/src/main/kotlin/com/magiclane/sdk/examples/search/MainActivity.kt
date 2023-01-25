// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.search

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    
    private lateinit var listView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: Toolbar

    private var searchService = SearchService(
        onCompleted = { results, errorCode, _ ->
            progressBar.visibility = View.GONE
            when (errorCode)
            {
                GemError.NoError ->
                {
                    // No error encountered, we can handle the results.
                    if (results.isNotEmpty())
                    {
                        val imageSize = resources.getDimension(R.dimen.list_image_size).toInt()
                        listView.adapter = CustomAdapter(results, imageSize)
                    }
                    else
                    {
                        // The search completed without errors, but there were no results found.
                        showDialog("No results!")
                    }
                }

                GemError.Cancel ->
                {
                    // The search action was cancelled.
                }
                
                GemError.Busy ->
                {
                    showDialog("Requested operation cannot be performed. Internal limit reached. Please use an API token in order to avoid this error.")
                }

                else ->
                {
                    // There was a problem at computing the search operation.
                    showDialog("Search service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        toolbar = findViewById(R.id.toolbar)
        listView = findViewById<RecyclerView?>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation))
            setBackgroundResource(R.color.background_color)
            
            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
        }
        
        setSupportActionBar(toolbar)

        findViewById<SearchView>(R.id.searchInput).apply {
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener
                {
                    override fun onQueryTextSubmit(query: String?): Boolean
                    {
                        clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean
                    {
                        applyFilter(newText ?: "")
                        return true
                    }
                }
            )
            
            requestFocus()
        }

        /// MAGIC LANE
        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!GemSdk.initSdkWithDefaults(this))
        {
            // The SDK initialization was not completed.
            finish()
        }

        /* 
        The SDK initialization completed with success, but for the search action to be executed
        the app needs some permissions.
        Not requesting this permissions or not granting them will make the search to not work.
         */
        requestPermissions(this)

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }

    // ---------------------------------------------------------------------------------------------

    fun applyFilter(filter: String)
    {
        listView.adapter = CustomAdapter(arrayListOf(), 0)
        if (filter.trim().isNotEmpty())
        {
            progressBar.visibility = View.VISIBLE
        }

        // Search the requested filter.
        search(filter.trim())
    }
    
    // ---------------------------------------------------------------------------------------------

    private fun search(filter: String): Int = SdkCall.execute {
        // Cancel any search that is in progress now.
        searchService.cancelSearch()
        searchService.searchByFilter(filter)
    } ?: GemError.Cancel

    // ---------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    )
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

        val result = grantResults[permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)]
        if (result != PackageManager.PERMISSION_GRANTED)
        {
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun requestPermissions(activity: Activity): Boolean
    {
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

    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------

    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * This custom adapter is made to facilitate the displaying of the data from the model
 * and to decide how it is displayed.
 */
class CustomAdapter(private val dataSet: ArrayList<Landmark>, private val imageSize: Int) :
    RecyclerView.Adapter<CustomAdapter.ViewHolder>()
{
    // ---------------------------------------------------------------------------------------------

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    {
        val textView: TextView = view.findViewById(R.id.text)
        val imageView: ImageView = view.findViewById(R.id.image)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
    {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item, viewGroup, false)

        return ViewHolder(view)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int)
    {
        viewHolder.apply {
            textView.text = SdkCall.execute { dataSet[position].name }
            imageView.setImageBitmap(SdkCall.execute { dataSet[position].imageAsBitmap(imageSize) })
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun getItemCount() = dataSet.size

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
