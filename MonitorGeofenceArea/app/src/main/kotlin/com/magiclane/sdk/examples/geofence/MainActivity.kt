// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.geofence

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.CircleGeographicArea
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Geofence
import com.magiclane.sdk.core.GeofenceArea
import com.magiclane.sdk.core.GeofenceAreaList
import com.magiclane.sdk.core.GeofenceListener
import com.magiclane.sdk.core.Login
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.PositionPublishingPreferences
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView
    
    private lateinit var progressBar: ProgressBar
    
    private lateinit var followGPSButton: FloatingActionButton
    
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)
    
    private val geofence = Geofence()
    
    private var geofenceAreas: GeofenceAreaList = arrayListOf()
    
    private val addAreasProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) 
        {
            showDialog("AddAreaProgressListener.onCompleted(): error = ${GemError.getMessage(error)}")
        }
        else
        {
            SdkCall.execute {
                gemSurfaceView.mapView?.let { mapView ->
                    val polygonSettings = MarkerCollectionRenderSettings(polylineInnerColor = Rgba.magenta(), polygonFillColor = Rgba(255, 0, 0, 128))
                    polygonSettings.polylineInnerSize = 1.0//mm
                    val polygonCollection = MarkerCollection(EMarkerType.Polygon, "Polygon")

                    for (geofenceArea in geofenceAreas)
                    {
                        if (geofenceArea.area is CircleGeographicArea)
                        {
                            geofenceArea.area?.centerPoint?.let {
                                val marker = Marker(it, 25)
                                polygonCollection.add(marker)
                            }
                        }
                    }

                    mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

                    PositionService.positionPublishingPreferences = PositionPublishingPreferences(true, 1, false, EDataType.ImprovedPosition)
                    geofence.startMonitoringAreas(monitoringAreasListener, arrayListOf("My Circle Area 1", "My Circle Area 2"))
                    startSimulation()
                }
            }
        }
    })

    private val loginProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError)
        {
            showDialog("LoginProgressListener.onCompleted(): error = ${GemError.getMessage(error)}")
        }
        else
        {
            SdkCall.execute {
                geofenceAreas = arrayListOf(GeofenceArea(CircleGeographicArea(Coordinates(45.65189844, 25.60438562), 25), "My Circle Area 1"),
                                            GeofenceArea(CircleGeographicArea(Coordinates(45.65264, 25.60697719), 25), "My Circle Area 2"))
                val error = geofence.addAreas(geofenceAreas, addAreasProgressListener)
                if (error != GemError.NoError)
                {
                    Util.postOnMain { showDialog("Error adding areas to geofence: ${GemError.getMessage(error)}") }
                }
            }
        }
    })
    
    private val monitoringAreasListener = GeofenceListener.create(onEnterArea = { userId, areaId ->
        showToast("GeofenceListener.onEnterArea(): user = $userId entered area with ID = $areaId")
    }, onExitArea = { userId, areaId ->
        showToast("GeofenceListener.onExitArea(): user = $userId exited area with ID = $areaId")
    })
    
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                gemSurfaceView.mapView?.let { mapView ->
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    setFollowGPSButton()
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

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        followGPSButton = findViewById(R.id.followCursor)

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            SdkCall.execute {
                val error = Login.registerExternalLogin("__my_spceial_login_id__", loginProgressListener)
                if (error != GemError.NoError)
                {
                    Util.postOnMain { showDialog("Error registering external login: ${GemError.getMessage(error)}") }
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

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
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

    private fun setFollowGPSButton()
    {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {
                followGPSButton.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                followGPSButton.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            followGPSButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(Landmark("Brasov", 45.65094531, 25.60403406), Landmark("Predeal", 45.5052, 25.5742))
        val error = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (error != GemError.NoError)
        {
            Util.postOnMain { showDialog("Error starting simulation: ${GemError.getMessage(error)}") }
        }
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

    private fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) 
    {
        Util.postOnMain {
            Toast.makeText(this, message, length).show()
        }
    }
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
