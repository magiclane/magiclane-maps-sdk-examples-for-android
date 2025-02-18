// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.whatsnearbycategory

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil.getDistText
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sdk.util.Util.postOnMain
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.util.GemUtil
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val RESOURCE = "GLOBAL"
    }

    private lateinit var listView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private var imageSize = 0

    private val searchService = SearchService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ results, errorCode, _ ->
            progressBar.visibility = View.GONE
            when (errorCode)
            {
                GemError.NoError ->
                {
                    val reference = reference ?: return@onCompleted
                    if (results.isEmpty())
                    {
                        // The search completed without errors, but there were no results found.
                        showDialog("No results!")
                        return@onCompleted
                    }

                    listView.adapter = CustomAdapter(reference, results, imageSize)
                    decrement()
                }

                GemError.Cancel ->
                {
                    // The search action was cancelled.
                }

                else ->
                {
                    // There was a problem at computing the search operation.
                    showDialog("Search service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    private var reference: Coordinates? = null

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageSize = resources.getDimension(R.dimen.landmark_image_size).toInt()

        listView = findViewById(R.id.list_view)
        progressBar = findViewById(R.id.progressBar)
        val layoutManager = LinearLayoutManager(this)
        listView.layoutManager = layoutManager

        val separator = DividerItemDecoration(applicationContext, layoutManager.orientation)
        listView.addItemDecoration(separator)

        listView.setBackgroundResource(R.color.background_color)
        val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
        listView.setPadding(lateralPadding, 0, lateralPadding, 0)

        increment()

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done after the world map is ready.
            search()
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

        /* 
        The SDK initialization completed with success, but for the search action to be executed
        properly the app needs permission to get your location.
        Not requesting this permission or not granting it will make the search fail.
         */
        requestPermissions(this)

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true)
        {
            override fun handleOnBackPressed()
            {
                finish()
                exitProcess(0)
            }
        })
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    private fun search() = SdkCall.execute {
        // If one of the location permissions is granted, we can do the search around action.
        val hasPermissions =
            PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (!hasPermissions) return@execute

        PositionService.getCurrentPosition()?.let {
            searchAround(it)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun searchAround(reference: Coordinates) = SdkCall.execute {
        this.reference = reference

        // Cancel any search that is in progress now.
        searchService.cancelSearch()

        // Search around position using the provided search preferences and/or filter.
        searchService.searchAroundPosition(EGenericCategoriesIDs.GasStation)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    )
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionsHelper.onRequestPermissionsResult(
            this,
            requestCode,
            grantResults
        )

        val result = grantResults[permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)]
        if (result != PackageManager.PERMISSION_GRANTED)
        {
            finish()
            exitProcess(0)
        }

        postOnMain { search() }
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
        decrement()
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

    //region --------------------------------------------------FOR TESTING--------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------------------------

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    private var mainActivityIdlingResource = CountingIdlingResource(RESOURCE, true)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getActivityIdlingResource(): IdlingResource
    {
        return mainActivityIdlingResource
    }

    // ---------------------------------------------------------------------------------------------
    private fun increment() = mainActivityIdlingResource.increment()

    // ---------------------------------------------------------------------------------------------
    private fun decrement() = mainActivityIdlingResource.decrement()
    //endregion ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------

/**
 * This custom adapter is made to facilitate the displaying of the data from the model
 * and to decide how it is displayed.
 */
class CustomAdapter(
    private val reference: Coordinates,
    private val dataSet: ArrayList<Landmark>,
    private val imageSize: Int
) : RecyclerView.Adapter<CustomAdapter.ViewHolder>()
{
    // ---------------------------------------------------------------------------------------------

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    {
        val image: ImageView = view.findViewById(R.id.image)
        val text: TextView = view.findViewById(R.id.text)
        val description: TextView = view.findViewById(R.id.description)
        val status: TextView = view.findViewById(R.id.status_text)
        val statusDescription: TextView = view.findViewById(R.id.status_description)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
    {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item, viewGroup, false)

        return ViewHolder(view)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) = SdkCall.execute {
        val meters = dataSet[position].coordinates?.getDistance(reference)?.toInt() ?: 0
        val dist = getDistText(meters, EUnitSystem.Metric, true)

        viewHolder.run {
            image.setImageBitmap(dataSet[position].imageAsBitmap(imageSize))
            text.text = dataSet[position].name
            description.text = GemUtil.getLandmarkDescription(dataSet[position], true)
            status.text = dist.first
            statusDescription.text = dist.second
        }
    } ?: Unit

    // ---------------------------------------------------------------------------------------------

    override fun getItemCount() = dataSet.size
}

// -------------------------------------------------------------------------------------------------