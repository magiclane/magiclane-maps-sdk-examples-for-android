// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.favourites

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.RectangleGeographicArea
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.LandmarkStore
import com.generalmagic.sdk.places.LandmarkStoreService
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var locationDetails: ConstraintLayout
    private lateinit var statusText: TextView

    // Define a Landmark Store so we can write the favourite landmarks in the data folder.
    private lateinit var store: LandmarkStore

    private val searchService = SearchService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
            showStatusMessage("Search service has started!")
        },

        onCompleted = { results, errorCode, _ ->
            progressBar.visibility = View.GONE
            showStatusMessage("Search service completed with error code: $errorCode")
            
            when (errorCode) 
            {
                GemError.NoError -> 
                {
                    if (results.isNotEmpty()) 
                    {
                        val landmark = results[0]
                        flyTo(landmark)
                        displayLocationInfo(landmark)
                        showStatusMessage("The search completed without errors.")
                    }
                    else
                    {
                        // The search completed without errors, but there were no results found.
                        showStatusMessage("The search completed without errors, but there were no results found.")
                    }
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

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        locationDetails = findViewById(R.id.location_details)
        statusText = findViewById(R.id.status_text)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done after the world map is ready.
            SdkCall.execute {
                createStore()

                val text = "Statue of Liberty New York"
                val coordinates = Coordinates(40.68925476, -74.04456329)

                searchService.searchByFilter(text, coordinates)
            }
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to internet!")
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

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.geographicArea?.let { area ->
            gemSurfaceView.mapView?.let { mainMapView ->
                // Center the map on a specific area using the provided animation.
                mainMapView.centerOnArea(area)

                // Highlights a specific area on the map using the provided settings.
                mainMapView.activateHighlightLandmarks(landmark)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun createStore()
    {
        store = LandmarkStoreService().createLandmarkStore("Favourites")?.first!!
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun displayLocationInfo(landmark: Landmark)
    {
        // Display a view containing the necessary information about the landmark.
        var name = ""
        var coordinates = ""

        SdkCall.execute {
            name = landmark.name ?: "Unnamed Location"
            landmark.coordinates?.apply { coordinates = "$latitude, $longitude" }
        }

        Util.postOnMain {
            locationDetails.apply {
                val nameView = findViewById<TextView>(R.id.name)
                val coordinatesView = findViewById<TextView>(R.id.coordinates)
                val imageView = findViewById<ImageView>(R.id.favourites_icon)

                // Update the favourites icon based on the status of the landmark.
                updateFavouritesIcon(imageView, getFavouriteId(landmark) != -1)

                // Display the name and coordinates of the landmark.
                nameView.text = name
                coordinatesView.text = coordinates

                // Treat favourites icon click event (Add/ Remove from favourites)
                imageView.setOnClickListener {
                    val landmarkId = getFavouriteId(landmark)
                    if (landmarkId != -1)
                    {
                        deleteFromFavourites(landmarkId)
                        updateFavouritesIcon(imageView, false)
                        showStatusMessage("The landmark was deleted from favourites.")
                    }
                    else
                    {
                        addToFavourites(landmark)
                        updateFavouritesIcon(imageView, true)
                        showStatusMessage("The landmark was added to favourites.")
                    }
                }

                this.visibility = View.VISIBLE
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getFavouriteId(landmark: Landmark): Int = SdkCall.execute {
        /*
        Get the ID of the landmark saved in the store so we can use it to remove it 
        or to check if it's already a favourite.
         */
        val radius = 5.0 // meters
        val area = landmark.coordinates?.let { RectangleGeographicArea(it, radius, radius) }
        val landmarks = area?.let { store.getLandmarksByArea(it) } ?: return@execute -1

        val threshold = 0.00001
        landmarks.forEach {
            val itCoordinates = it.coordinates
            val landmarkCoordinates = landmark.coordinates

            if (itCoordinates != null && landmarkCoordinates != null)
            {
                if ((itCoordinates.latitude - landmarkCoordinates.latitude < threshold) &&
                    (itCoordinates.longitude - landmarkCoordinates.longitude < threshold))
                    return@execute it.id
            }
            else 
                return@execute -1
        }
        -1
    } ?: -1

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun addToFavourites(landmark: Landmark) = SdkCall.execute {
        // Add the landmark to the desired LandmarkStore.
        store.addLandmark(landmark)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun deleteFromFavourites(landmarkId: Int) = SdkCall.execute {
        // Remove the landmark associated to this ID from the LandmarkStore.
        store.removeLandmark(landmarkId)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun updateFavouritesIcon(imageView: ImageView, isFavourite: Boolean)
    {
        imageView.setImageDrawable(
            if (isFavourite)
            {
                getMainDrawable(R.drawable.ic_baseline_favorite_24)
            }
            else
            {
                getMainDrawable(R.drawable.ic_baseline_favorite_border_24)
            }
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getMainDrawable(id: Int): Drawable
    {
        return ContextCompat.getDrawable(this@MainActivity, id) ?: ColorDrawable(Color.TRANSPARENT)
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
