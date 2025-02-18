// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.search

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.session.SoundSession.requestFocus
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    private lateinit var listView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var noResultText: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private var imageSize: Int = 0
    private var reference: Coordinates? = null

    private var searchService = SearchService(
        onCompleted = { results, errorCode, _ ->
            progressBar.visibility = View.GONE
            when (errorCode)
            {
                GemError.NoError ->
                {
                    // No error encountered, we can handle the results.
                    refreshList(results)
                    noResultText.isVisible = results.isEmpty()
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
            EspressoIdlingResource.decrement()
        }
    )

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        imageSize = resources.getDimension(R.dimen.list_image_size).toInt()
        progressBar = findViewById(R.id.progressBar)
        noResultText = findViewById(R.id.no_results_text)
        toolbar = findViewById(R.id.toolbar)
        searchView = findViewById(R.id.search_input)
        SdkSettings.onMapDataReady = { isReady ->
            if(isReady){
                searchView.isVisible = true
                EspressoIdlingResource.decrement()
            }
        }
        EspressoIdlingResource.increment()
        listView = findViewById<RecyclerView?>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(
                DividerItemDecoration(
                    applicationContext,
                    (layoutManager as LinearLayoutManager).orientation
                )
            )
            setBackgroundResource(R.color.background_color)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
            adapter = CustomAdapter(arrayListOf(), imageSize)
        }
        setSupportActionBar(toolbar)

        searchView.apply {
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

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true)
        {
            override fun handleOnBackPressed()
            {
                finish()
            }
        })
    }

    // ---------------------------------------------------------------------------------------------

    override fun onStop()
    {
        super.onStop()
        if (isFinishing)
            GemSdk.release() // Release the SDK.
    }

    // ---------------------------------------------------------------------------------------------

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshList(results: ArrayList<Landmark>)
    {
        (listView.adapter)?.let {
            (it as CustomAdapter).dataSet = results
            it.notifyDataSetChanged()
        }
    }
    // ---------------------------------------------------------------------------------------------

    fun applyFilter(filter: String)
    {
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
        if (filter.isBlank())
        {
            refreshList(arrayListOf())
            noResultText.isVisible = false
        }

        val position = PositionService.position
        reference = if (position?.isValid() == true)
        {
            position.coordinates
        } else
        {
            Coordinates(51.5072, 0.1276) // center London
        }

        val  res = searchService.searchByFilter(filter, reference)
        Log.d("!Q@W#E","....incremented,filter : $filter ,res = $res")
        EspressoIdlingResource.increment()
        res
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

    /**
     * This custom adapter is made to facilitate the displaying of the data from the model
     * and to decide how it is displayed.
     */
    inner class CustomAdapter(var dataSet: ArrayList<Landmark>, private val imageSize: Int) :
        RecyclerView.Adapter<CustomAdapter.CustomViewHolder>()
    {
        // -----------------------------------------------------------------------------------------

        inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            val textView: TextView = view.findViewById(R.id.text)
            val description: TextView = view.findViewById(R.id.description)
            val imageView: ImageView = view.findViewById(R.id.image)
            val statusText: TextView = view.findViewById(R.id.status_text)
            val statusDescription: TextView = view.findViewById(R.id.status_description)
        }

        // -----------------------------------------------------------------------------------------

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CustomViewHolder
        {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item, viewGroup, false)

            return CustomViewHolder(view)
        }

        // -----------------------------------------------------------------------------------------

        override fun onBindViewHolder(viewHolder: CustomViewHolder, position: Int)
        {
            viewHolder.apply {
                SdkCall.execute {
                    textView.text = dataSet[position].name
                    description.text = GemUtil.getLandmarkDescription(dataSet[position], true)
                    imageView.setImageBitmap(dataSet[position].imageAsBitmap(imageSize))

                    val meters = reference?.let { dataSet[position].coordinates?.getDistance(it)?.toInt() ?: 0 } ?: 0
                    val dist = GemUtil.getDistText(meters, EUnitSystem.Metric, true)

                    statusText.text = dist.first
                    statusDescription.text = dist.second
                }
            }
        }

        // -----------------------------------------------------------------------------------------

        override fun getItemCount() = dataSet.size

        // -----------------------------------------------------------------------------------------
    }

    // ---------------------------------------------------------------------------------------------

    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
    }
}

// -------------------------------------------------------------------------------------------------

object EspressoIdlingResource
{
    private const val RESOURCE_NAME = "SearchIdlingResource"
    private var count = 0
    val espressoIdlingResource = CountingIdlingResource(RESOURCE_NAME)

    //fun increment() = if (count == 0) espressoIdlingResource.increment().also { count++ } else{}
    fun increment() = espressoIdlingResource.increment().also { ++count }

    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement().also { --count } else
    {
    }
}
// -------------------------------------------------------------------------------------------------
