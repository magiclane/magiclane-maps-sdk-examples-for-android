/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.searchalongroute

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.core.EGenericCategoriesIDs
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.routesandnavigation.NavigationService
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchButton: FloatingActionButton
    private lateinit var followCursorButton: FloatingActionButton

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    // Define a service to search along the route.
    private val searchService = SearchService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ results, errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    // Display results in AlertDialog
                    onSearchCompleted(results)
                }

                GemError.Cancel -> {
                    // The search action was cancelled.
                }

                else -> {
                    // There was a problem at computing the search operation.
                    Toast.makeText(
                        this@MainActivity,
                        "Search service error: ${GemError.getMessage(errorCode)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )

    /* 
    Define a navigation listener that will receive notifications from the
    navigation service.
    We will use just the onNavigationStarted method, but for more available
    methods you should check the documentation.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    getNavRoute()?.let { route ->
                        mapView.presentRoute(route)

                        // Make the search button visible and add a click listener to do the search.
                        Util.postOnMain {
                            searchButton.visibility = View.VISIBLE
                            searchButton.setOnClickListener { searchAlongRoute(route) }
                        }
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }
        }
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            progressBar.visibility = View.GONE
        },

        postOnMain = true
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        searchButton = findViewById(R.id.search_button)
        followCursorButton = findViewById(R.id.followCursor)

        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startSimulation()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        finish()
        exitProcess(0)
    }

    private fun getNavRoute(): Route? = navigationService.getNavigationRoute(navigationListener)

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {
                Util.postOnMain { followCursorButton.visibility = View.VISIBLE }
            }

            onEnterFollowingPosition = {
                Util.postOnMain { followCursorButton.visibility = View.GONE }
            }

            // Set on click action for the GPS button.
            followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616)
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun searchAlongRoute(route: Route) = SdkCall.execute {
        // Set the maximum number of results to 25.
        searchService.preferences.maxMatches = 25

        // Search Gas Stations along the route.
        searchService.searchAlongRoute(route, EGenericCategoriesIDs.GasStation)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    
    private fun onSearchCompleted(results: ArrayList<Landmark>) {
        val builder = AlertDialog.Builder(this)

        val convertView = layoutInflater.inflate(R.layout.dialog_list, null)
        convertView.findViewById<RecyclerView>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            addItemDecoration(DividerItemDecoration(
                applicationContext,
                (layoutManager as LinearLayoutManager).orientation
            ))

            setBackgroundResource(R.color.white)

            val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
            
            adapter = CustomAdapter(results)
        }
        
        builder.setView(convertView)
        
        builder.create().show()
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    
    inner class CustomAdapter(private val dataSet: ArrayList<Landmark>) : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val text: TextView = view.findViewById(R.id.search_text)

            fun bind(position: Int)
            {
                text.text = SdkCall.execute { dataSet[position].name }
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)
            
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = dataSet.size
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
}
