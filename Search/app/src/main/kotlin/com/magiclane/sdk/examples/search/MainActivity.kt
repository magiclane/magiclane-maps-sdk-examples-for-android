// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.search

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
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
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity() {
    // ---------------------------------------------------------------------------------------------
    private companion object {
        val diffCallback = object : DiffUtil.ItemCallback<SearchItem>() {
            override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean = false

            override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean = oldItem == newItem
        }

        private const val REQUEST_PERMISSIONS = 110
    }
    // ---------------------------------------------------------------------------------------------

    private lateinit var listView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var noResultText: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private var imageSize: Int = 0
    private var reference: Coordinates? = null

    private val _results = MutableLiveData<List<SearchItem>>()
    private val results: LiveData<List<SearchItem>> get() = _results

    private lateinit var customAdapter: CustomAdapter

    private var searchService = SearchService(
        onCompleted = { results, errorCode, _ ->
            when (errorCode) {
                GemError.NoError -> {
                    // No error encountered, we can handle the results.
                    refreshList(results)
                    noResultText.isVisible = results.isEmpty()
                }

                GemError.Cancel -> {
                    // The search action was cancelled.
                }

                GemError.Busy -> {
                    showDialog("Requested operation cannot be performed. Internal limit reached. Please use an API token in order to avoid this error.")
                }

                else -> {
                    // There was a problem at computing the search operation.
                    showDialog("Search service error: ${GemError.getMessage(errorCode)}")
                }
            }
            EspressoIdlingResource.decrement()
        }
    )

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
        
        imageSize = resources.getDimension(R.dimen.list_image_size).toInt()
        progressBar = findViewById(R.id.progressBar)
        noResultText = findViewById(R.id.no_results_text)
        toolbar = findViewById(R.id.toolbar)
        searchView = findViewById(R.id.search_input)
        SdkSettings.onMapDataReady = { isReady ->
            if (isReady) {
                searchView.isVisible = true
                EspressoIdlingResource.decrement()
            }
        }
        //for testing only
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
            customAdapter = CustomAdapter()
            adapter = customAdapter
            itemAnimator = null
        }
        
        setSupportActionBar(toolbar)

        searchView.apply {
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        val filter = (newText ?: "").trim()
                        if (filter.isNotEmpty())
                            progressBar.visibility = View.VISIBLE
                        // Search the requested filter.
                        search(filter)
                        return true
                    }
                }
            )

            requestFocus()
        }

        //observe the list and update UI
        results.observe(this) {
            customAdapter.submitList(it)
            progressBar.visibility = View.GONE
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
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    // ---------------------------------------------------------------------------------------------

    override fun onStop() {
        super.onStop()
        if (isFinishing)
            GemSdk.release() // Release the SDK.
    }

    // ---------------------------------------------------------------------------------------------

    private fun refreshList(results: ArrayList<Landmark>) = SdkCall.execute {
        val list = results.map { landmark ->
            val meters = reference?.let { landmark.coordinates?.getDistance(it)?.toInt() ?: 0 } ?: 0
            val dist = GemUtil.getDistText(meters, EUnitSystem.Metric, true)
            SearchItem(
                landmark.imageAsBitmap(imageSize), landmark.name.toString(),
                GemUtil.getLandmarkDescription(landmark, true),
                dist.first,
                dist.second
            )
        }
        _results.postValue(list)
    }

    // ---------------------------------------------------------------------------------------------

    private fun search(filter: String) = SdkCall.postAsync({
        // Cancel any search that is in progress now.
        searchService.cancelSearch()
        if (filter.isBlank()) {
            refreshList(arrayListOf())
            noResultText.isVisible = false
        }

        // Give a random position if position is not available
        val position = PositionService.position
        reference = if (position?.isValid() == true)
            position.coordinates
        else
            Coordinates(51.5072, 0.1276) // center London


        val res = searchService.searchByFilter(filter, reference)

        if (GemError.isError(res) && res != GemError.Cancel) {
            showDialog(GemError.getMessage(res))
        }
        //this is for testing only
        EspressoIdlingResource.increment()
    }, 200)

    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(text: String) {
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
     * UI search item data class. 
     */
    data class SearchItem(
        val image: Bitmap? = null,
        val name: String = "",
        val descriptionTxt: String = "",
        val distance: String = "",
        val unit: String = ""
    )


    /**
     * This custom adapter is made to facilitate the displaying of the data from the model
     * and to decide how it is displayed.
     */
    inner class CustomAdapter : ListAdapter<SearchItem, CustomAdapter.CustomViewHolder>(diffCallback) {
        // -----------------------------------------------------------------------------------------

        inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.text)
            val descriptionView: TextView = view.findViewById(R.id.description)
            val imageView: ImageView = view.findViewById(R.id.image)
            val distanceTextView: TextView = view.findViewById(R.id.status_text)
            val unitTextView: TextView = view.findViewById(R.id.status_description)
        }

        // -----------------------------------------------------------------------------------------

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CustomViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item, viewGroup, false)

            return CustomViewHolder(view)
        }

        // -----------------------------------------------------------------------------------------

        override fun onBindViewHolder(viewHolder: CustomViewHolder, position: Int) {
            viewHolder.apply {
                with(getItem(position)) {
                    SdkCall.execute {
                        textView.text = name
                        descriptionView.text = descriptionTxt
                        imageView.setImageBitmap(image)
                        distanceTextView.text = distance
                        unitTextView.text = unit
                    }
                }
            }
        }
        
        
        // -----------------------------------------------------------------------------------------
    }
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------

object EspressoIdlingResource {
    private const val RESOURCE_NAME = "SearchIdlingResource"
    private var count = 0
    val espressoIdlingResource = CountingIdlingResource(RESOURCE_NAME)

    //fun increment() = if (count == 0) espressoIdlingResource.increment().also { count++ } else{}
    fun increment() = espressoIdlingResource.increment().also { ++count }

    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement().also { --count } else {
    }
}
// -------------------------------------------------------------------------------------------------
