// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.searchalongroute

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchButton: FloatingActionButton
    private var routesList = ArrayList<Route>()
    private var mainRoute = Route()
    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    // Define a service to search along the route.
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
                    // Display results in AlertDialog
                    onSearchCompleted(results)
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


    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode)
            {
                GemError.NoError ->
                {
                    if(routes.isNotEmpty())
                    {
                        routesList = routes
                        mainRoute = routes[0]
                        Util.postOnMain {
                            searchButton.visibility = View.VISIBLE
                        }
                        SdkCall.execute {
                            gemSurfaceView.mapView?.presentRoutes(routes, displayBubble = true)
                        }
                    }
                }

                GemError.Cancel ->
                {
                    // The routing action was cancelled.
                }

                else ->
                {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
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
        gemSurfaceView = findViewById(R.id.gem_surface)
        searchButton = findViewById(R.id.search_button)

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            calculateRoute()

            // onTouch event callback
            gemSurfaceView.mapView?.onTouch = { xy ->
                // xy are the coordinates of the touch event
                SdkCall.execute {
                    // tell the map view where the touch event happened
                    gemSurfaceView.mapView?.cursorScreenPosition = xy

                    // get the visible routes at the touch event point
                    val routes = gemSurfaceView.mapView?.cursorSelectionRoutes
                    // check if there is any route
                    if (!routes.isNullOrEmpty())
                    {
                        // set the touched route as the main route and center on it
                        mainRoute = routes[0]
                        gemSurfaceView.mapView?.apply {
                            preferences?.routes?.mainRoute = mainRoute
                            centerOnRoutes(routesList)
                        }
                    }
                }
            }

        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }

        searchButton.setOnClickListener {
            searchAlongRoute(mainRoute)
        }

        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true){
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
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Folkestone", 51.0814, 1.1695),
            Landmark("Paris", 48.8566932, 2.3514616)
        )
        routingService.calculateRoute(waypoints)
    }

    // ---------------------------------------------------------------------------------------------

    private fun searchAlongRoute(route: Route) = SdkCall.execute {
        // Set the maximum number of results to 25.
        searchService.preferences.maxMatches = 25

        // Search Gas Stations along the route.
        searchService.searchAlongRoute(route, EGenericCategoriesIDs.GasStation)
    }

    // ---------------------------------------------------------------------------------------------
    
    private fun onSearchCompleted(results: ArrayList<Landmark>)
    {
        val builder = AlertDialog.Builder(this)

        val convertView = layoutInflater.inflate(R.layout.dialog_list, null)
        convertView.findViewById<RecyclerView>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            addItemDecoration(DividerItemDecoration(
                applicationContext,
                (layoutManager as LinearLayoutManager).orientation
            ))

            setBackgroundResource(R.color.background_color)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
            
            adapter = CustomAdapter(results)
        }
        
        builder.setView(convertView)
        
        builder.create().show()
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
    
    inner class CustomAdapter(private val dataSet: ArrayList<Landmark>) : RecyclerView.Adapter<CustomAdapter.ViewHolder>()
    {
        private val imageSize = resources.getDimension(R.dimen.list_item_image_size).toInt()
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val text: TextView = view.findViewById(R.id.text)
            private val description: TextView = view.findViewById(R.id.description)
            private val statusText: TextView = view.findViewById(R.id.status_text)
            private val statusDescription: TextView = view.findViewById(R.id.status_description)
            private val image: ImageView = view.findViewById(R.id.image)
            private val side: ImageView = view.findViewById(R.id.side)

            fun bind(position: Int)
            {
                text.text = SdkCall.execute { dataSet[position].name }
                description.text = GemUtil.getLandmarkDescription(dataSet[position], true)
                image.setImageBitmap(SdkCall.execute { dataSet[position].imageAsBitmap(imageSize) })

                var distance = Pair("", "")
                var sideBmp: Bitmap? = null

                SdkCall.execute {
                    PositionService.improvedPosition?.let { improvedPosition ->
                        if (improvedPosition.isValid())
                        {
                            distance = GemUtil.getDistText(improvedPosition.coordinates.getDistance(dataSet[position].coordinates!!).toInt(), SdkSettings.unitSystem)
                        }
                    }

                    var sideIconId = 0
                    val side = dataSet[position].findExtraInfo("gm_search_result_side = ")
                    if (side.contentEquals("Left side", true))
                    {
                        sideIconId = SdkImages.Engine_Misc.Poi_ToLeft.value
                    }

                    if (side.contentEquals("Right side", true))
                    {
                        sideIconId = SdkImages.Engine_Misc.Poi_ToRight.value
                    }

                    if (sideIconId != 0)
                    {
                        sideBmp = GemUtilImages.asBitmap(sideIconId, imageSize, imageSize)
                    }
                }

                statusText.text = distance.first
                statusDescription.text = distance.second

                sideBmp?.let {
                    side.setImageBitmap(it)
                    side.visibility = View.VISIBLE
                } ?: run {
                    side.visibility = View.GONE
                }
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
        {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int)
        {
            holder.bind(position)
        }

        override fun getItemCount(): Int = dataSet.size
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------