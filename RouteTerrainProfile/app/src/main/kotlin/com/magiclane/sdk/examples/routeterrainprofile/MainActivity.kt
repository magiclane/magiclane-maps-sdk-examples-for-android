// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routeterrainprofile

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.icu.number.Precision.increment
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.widget.NestedScrollView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMapViewPerspective
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{


    // ---------------------------------------------------------------------------------------------
    companion object
    {
        const val RESOURCE = "GLOBAL"
    }

    private var mainActivityIdlingResource = CountingIdlingResource(RESOURCE, true)

    @VisibleForTesting
    lateinit var routingProfile : RouteProfile

    lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var routeProfileContainer: NestedScrollView
    private lateinit var progressBar: ProgressBar

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
                    if (routes.isNotEmpty())
                    {
                        val route = routes[0]
                        
                        // Presents the route in the map view
                        displayRoute(route)
                        
                        // Get the terrain profile of the route.
                        val terrain = SdkCall.execute { route.terrainProfile }

                        if (terrain != null)
                        {
                            // The route has a terrain profile so we can display it.
                            displayTerrainInfo(route)
                        }
                        else
                        {
                            showDialog("Route terrain profile is not available!")
                            EspressoIdlingResource.decrement()
                        }
                    }
                }

                GemError.Cancel ->
                {
                    // The routing action was cancelled.
                    showDialog("The routing action was cancelled.")
                    EspressoIdlingResource.decrement()
                }

                else ->
                {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
                    EspressoIdlingResource.decrement()
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        EspressoIdlingResource.increment()
        gemSurfaceView = findViewById(R.id.gem_surface_view)
        routeProfileContainer = findViewById(R.id.route_profile_scroll_view)
        progressBar = findViewById(R.id.progressBar)

        setConstraints(resources.configuration.orientation)
        
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done after the world map is ready.
            calculateRoute()
        }

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

    override fun onConfigurationChanged(newConfig: Configuration)
    {
        super.onConfigurationChanged(newConfig)
        
        setConstraints(newConfig.orientation)
    }
    
    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }
    
    // ---------------------------------------------------------------------------------------------

    fun zoomToRoute() = SdkCall.execute {
        gemSurfaceView.mapView?.let {
            it.preferences?.setMapViewPerspective(EMapViewPerspective.TwoDimensional)
            val mainRoute = it.preferences?.routes?.mainRoute
            flyToRoute(mainRoute)
        }
    }

    // ---------------------------------------------------------------------------------------------
    
    fun flyToRoute(route: Route?)
    {
        route?.let { gemSurfaceView.mapView?.centerOnRoute(it, animation = Animation(animation = EAnimation.Linear, duration = 200)) }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    fun isDarkThemeOn() : Boolean = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    
    // ---------------------------------------------------------------------------------------------
    
    private fun displayRoute(route: Route) = SdkCall.execute { 
        gemSurfaceView.mapView?.let { 
            it.presentRoute(route)
            it.centerOnRoute(route)
        }
    }
    
    // ---------------------------------------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private fun displayTerrainInfo(route: Route)
    {
        // show the layout that contains the elevation views
        routeProfileContainer.visibility = View.VISIBLE
        
        // creates the instance of the class that operates the elevation data
        routingProfile =  RouteProfile(this, route)
        EspressoIdlingResource.decrement()
    }

    // ---------------------------------------------------------------------------------------------

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Brasov", 45.6427, 25.5887),
            Landmark("Bucharest", 44.4268, 26.1025)
        )

        /* 
        Setting this (setBuildTerrainProfile(true)) to the Routing Service preferences is mandatory if you
        want to get data related to the route terrain profile, otherwise the terrain profile would not be calculated
        at the routing process.
         */
        routingService.preferences.buildTerrainProfile = true
        routingService.calculateRoute(waypoints)
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
    
    private fun setConstraints(orientation: Int)
    {
        val rootView = findViewById<ConstraintLayout>(R.id.root_view)
        when (orientation)
        {
            Configuration.ORIENTATION_LANDSCAPE ->
            {
                ConstraintSet().apply {
                    clone(rootView)

                    connect(R.id.route_profile_scroll_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.route_profile_scroll_view, ConstraintSet.END, R.id.gem_surface_view, ConstraintSet.START)
                    connect(R.id.route_profile_scroll_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.route_profile_scroll_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    connect(R.id.gem_surface_view, ConstraintSet.START, R.id.route_profile_scroll_view, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    applyTo(rootView)
                }

                routeProfileContainer.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
                routeProfileContainer.requestLayout()

                gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
                gemSurfaceView.requestLayout()
            }

            Configuration.ORIENTATION_PORTRAIT ->
            {
                ConstraintSet().apply { 
                    clone(rootView)

                    connect(R.id.gem_surface_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.gem_surface_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, R.id.route_profile_scroll_view, ConstraintSet.TOP)

                    connect(R.id.route_profile_scroll_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.route_profile_scroll_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.route_profile_scroll_view, ConstraintSet.TOP, R.id.gem_surface_view, ConstraintSet.BOTTOM)
                    connect(R.id.route_profile_scroll_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    
                    applyTo(rootView)
                }

                routeProfileContainer.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                }
                routeProfileContainer.requestLayout()

                gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                }
                gemSurfaceView.requestLayout()
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
//region --------------------------------------------------FOR TESTING--------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------------
object EspressoIdlingResource
{
    val espressoIdlingResource = CountingIdlingResource("RouteTerrainIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion  -------------------------------------------------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------
