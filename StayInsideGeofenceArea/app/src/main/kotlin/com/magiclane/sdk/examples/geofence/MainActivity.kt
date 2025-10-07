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
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Geofence
import com.magiclane.sdk.core.GeofenceArea
import com.magiclane.sdk.core.GeofenceAreaList
import com.magiclane.sdk.core.Login
import com.magiclane.sdk.core.PolygonGeographicArea
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView
    
    private lateinit var progressBar: ProgressBar
    
    private var inset = 0
    
    private val routingService = RoutingService(
        onCompleted = onCompleted@{ routes, errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode)
            {
                GemError.NoError ->
                {
                    SdkCall.execute {
                        
                        gemSurfaceView.mapView?.presentRoutes(routes, edgeAreaInsets = Rect(inset, inset, inset, inset))
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
                        if (geofenceArea.area is PolygonGeographicArea)
                        {
                            val polygonArea = geofenceArea.area as PolygonGeographicArea
                            polygonArea.coordinates?.let { coordinates ->
                                val marker = Marker(coordinates)
                                polygonCollection.add(marker)
                            }
                        }
                    }

                    mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

                    calculateRoute()
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
                geofenceAreas = arrayListOf(GeofenceArea(PolygonGeographicArea(arrayListOf(Coordinates(45.650094, 25.610541),
                                                                                                                 Coordinates(45.670987, 25.598290),
                                                                                                                 Coordinates(45.674495, 25.522401),
                                                                                                                 Coordinates(45.676566, 25.495187),
                                                                                                                 Coordinates(45.629350, 25.467848),
                                                                                                                 Coordinates(45.594964, 25.440387),
                                                                                                                 Coordinates(45.566928, 25.432997),
                                                                                                                 Coordinates(45.503191, 25.489674),
                                                                                                                 Coordinates(45.469958, 25.574339),
                                                                                                                 Coordinates(45.489716, 25.596981),
                                                                                                                 Coordinates(45.522181, 25.559891),
                                                                                                                 Coordinates(45.519453, 25.525565),
                                                                                                                 Coordinates(45.557287, 25.506421),
                                                                                                                 Coordinates(45.582583, 25.486980),
                                                                                                                 Coordinates(45.621080, 25.503182),
                                                                                                                 Coordinates(45.638192, 25.536653),
                                                                                                                 Coordinates(45.652250, 25.567659),
                                                                                                                 Coordinates(45.661346, 25.585036),
                                                                                                                 Coordinates(45.655824, 25.593762),
                                                                                                                 Coordinates(45.648166, 25.599659),
                                                                                                                 Coordinates(45.650094, 25.610541))), "My Polygon Area 4"))

                val error = geofence.addAreas(geofenceAreas, addAreasProgressListener)
                if (error != GemError.NoError)
                {
                    Util.postOnMain { showDialog("Error adding areas to geofence: ${GemError.getMessage(error)}") }
                }
            }
        }
    })
    
    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        inset = getSizeInPixels(30)

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
    
    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(Landmark("Brasov", 45.65094531, 25.60403406), Landmark("Predeal", 45.5052, 25.5742)) 
        
        routingService.preferences.stickInsideGeofenceAreas = arrayListOf("My Polygon Area 4")
        
        val error = routingService.calculateRoute(waypoints)
        if (error != GemError.NoError)
        {
            Util.postOnMain { showDialog("Calculate route error: ${GemError.getMessage(error)}") }
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

    private fun getSizeInPixels(dpi: Int): Int
    {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpi.toFloat(), resources.displayMetrics).toInt()
    }
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
