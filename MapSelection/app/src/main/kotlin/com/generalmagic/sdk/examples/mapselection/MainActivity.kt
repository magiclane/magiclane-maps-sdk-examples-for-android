// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.mapselection

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ImageDatabase
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.Size
import com.generalmagic.sdk.core.Xy
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.d3scene.ECommonOverlayId
import com.generalmagic.sdk.d3scene.MapSceneObject
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.sensordatasource.PositionListener
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.sensordatasource.enums.EDataType
import com.generalmagic.sdk.util.EStringIds
import com.generalmagic.sdk.util.GemUtil
import com.generalmagic.sdk.util.GemUtilImages
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView
    
    private lateinit var overlayContainer: ConstraintLayout
    private lateinit var name: TextView
    private lateinit var description: TextView
    private lateinit var image: ImageView

    private lateinit var followCursorButton: FloatingActionButton
    private lateinit var flyToRoutesButton: FloatingActionButton

    private var imageSize = 0

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
                    SdkCall.execute {
                        gemSurfaceView.mapView?.presentRoutes(routes, displayBubble = true)
                        gemSurfaceView.mapView?.preferences?.routes?.mainRoute?.let { selectRoute(it) }
                    }
                    flyToRoutesButton.visibility = View.VISIBLE
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

        progressBar = findViewById(R.id.progress_bar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        
        overlayContainer = findViewById(R.id.overlay_container)
        name = findViewById(R.id.name)
        description = findViewById(R.id.description)
        image = findViewById(R.id.image)
        
        followCursorButton = findViewById(R.id.follow_cursor)
        
        flyToRoutesButton = findViewById<FloatingActionButton?>(R.id.fly_to_route).also { 
            it.setOnClickListener {
                SdkCall.execute {
                    gemSurfaceView.mapView?.let { mapView ->
                        mapView.preferences?.routes?.mainRoute?.let { mainRoute ->
                            selectRoute(mainRoute)
                        }
                    }
                }
            }
        }
        
        imageSize = resources.getDimension(R.dimen.image_size).toInt()

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            calculateRoute()

            // Set GPS button if location permission is granted, otherwise request permission
            SdkCall.execute {
                val hasLocationPermission = PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                if (hasLocationPermission)
                {
                    Util.postOnMain { enableGPSButton() }
                }
                else
                {
                    requestPermissions(this)
                }
            }

            // onTouch event callback
            gemSurfaceView.mapView?.onTouch = { xy ->
                // xy are the coordinates of the touch event
                SdkCall.execute {
                    // tell the map view where the touch event happened
                    gemSurfaceView.mapView?.cursorScreenPosition = xy
                    
                    val centerXy = Xy(gemSurfaceView.measuredWidth / 2, gemSurfaceView.measuredHeight / 2)

                    val myPosition = gemSurfaceView.mapView?.cursorSelectionSceneObject
                    if (myPosition != null && isSameMapScene(myPosition, MapSceneObject.getDefPositionTracker().first!!))
                    {
                        showOverlayContainer(
                            GemUtil.getUIString(EStringIds.eStrMyPosition),
                            "",
                            GemUtilImages.asBitmap(ImageDatabase().getImageById(SdkImages.UI.SearchForCurrentLocation.value), imageSize, imageSize)
                        )

                        myPosition.coordinates?.let {
                            gemSurfaceView.mapView?.centerOnCoordinates(
                                it,
                                -1,
                                centerXy,
                                Animation(EAnimation.Linear),
                                Double.MAX_VALUE,
                                0.0
                            )
                        }

                        return@execute
                    }

                    val landmarks = gemSurfaceView.mapView?.cursorSelectionLandmarks
                    if (!landmarks.isNullOrEmpty())
                    {
                        val landmark = landmarks[0]
                        landmark.run {
                            showOverlayContainer(
                                name.toString(),
                                description.toString(),
                                image?.asBitmap(imageSize, imageSize)
                            )
                        }

                        landmark.coordinates?.let {
                            gemSurfaceView.mapView?.centerOnCoordinates(
                                it,
                                -1,
                                centerXy,
                                Animation(EAnimation.Linear),
                                Double.MAX_VALUE,
                                0.0
                            )
                        }

                        gemSurfaceView.mapView?.centerOnLocation(landmark)

                        return@execute
                    }

                    val trafficEvents = gemSurfaceView.mapView?.cursorSelectionTrafficEvents
                    if (!trafficEvents.isNullOrEmpty())
                    {
                        hideOverlayContainer()
                        openWebActivity(trafficEvents[0].previewUrl.toString())

                        return@execute
                    }
                    
                    val overlays = gemSurfaceView.mapView?.cursorSelectionOverlayItems
                    if (!overlays.isNullOrEmpty())
                    {
                        val overlay = overlays[0]
                        if (overlay.overlayInfo?.uid == ECommonOverlayId.Safety.value)
                        {
                            hideOverlayContainer()
                            openWebActivity(overlay.getPreviewUrl(Size()).toString())
                        }
                        else
                        {
                            overlay.run { 
                                showOverlayContainer(
                                    name.toString(), 
                                    overlayInfo?.name.toString(), 
                                    image?.asBitmap(imageSize, imageSize)
                                ) 
                            }
                            
                            overlay.coordinates?.let {
                                gemSurfaceView.mapView?.centerOnCoordinates(
                                    it,
                                    -1,
                                    centerXy,
                                    Animation(EAnimation.Linear),
                                    Double.MAX_VALUE,
                                    0.0
                                )
                            }
                        }

                        return@execute
                    }

                    // get the visible routes at the touch event point 
                    val routes = gemSurfaceView.mapView?.cursorSelectionRoutes
                    // check if there is any route
                    if (!routes.isNullOrEmpty())
                    {
                        // set the touched route as the main route and center on it
                        val route = routes[0]
                        selectRoute(route)

                        return@execute
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults)
        {
            if (item != PackageManager.PERMISSION_GRANTED)
            {
                showDialog("Location permission is required in order to select the current position cursor.")
                return
            }
        }

        SdkCall.execute {
            // Notify permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
            
            lateinit var positionListener: PositionListener
            if (PositionService.position?.isValid() == true)
            {
                Util.postOnMain { enableGPSButton() }
            }
            else
            {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    Util.postOnMain { enableGPSButton() }
                }
                PositionService.addListener(positionListener, EDataType.Position)
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616)
        )

        routingService.calculateRoute(waypoints)
    }

    // ---------------------------------------------------------------------------------------------
    
    private fun isSameMapScene(first: MapSceneObject, second: MapSceneObject) : Boolean = 
        first.maxScaleFactor == second.maxScaleFactor &&
        first.scaleFactor == second.scaleFactor &&
        first.visibility == second.visibility &&
        first.coordinates?.latitude == second.coordinates?.latitude &&
        first.coordinates?.longitude == second.coordinates?.longitude &&
        first.coordinates?.altitude == second.coordinates?.altitude &&
        first.orientation?.x == second.orientation?.x &&
        first.orientation?.y == second.orientation?.y &&
        first.orientation?.z == second.orientation?.z &&
        first.orientation?.w == second.orientation?.w
            
    
    // ---------------------------------------------------------------------------------------------
    
    private fun showOverlayContainer(name: String, description: String, image: Bitmap?) = Util.postOnMain {
        if (!overlayContainer.isVisible)
        {
            overlayContainer.visibility = View.VISIBLE
        }

        this.name.text = name
        if (description.isNotEmpty())
        {
            this.description.apply {
                text = description
                visibility = View.VISIBLE
            }
        }
        else
        {
            this.description.visibility = View.GONE
        }
        
        this.image.setImageBitmap(image)
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun hideOverlayContainer() = Util.postOnMain { overlayContainer.visibility = View.GONE }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun openWebActivity(url: String)
    {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }
    
    // ---------------------------------------------------------------------------------------------
    
    private fun enableGPSButton()
    {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            val isFollowingPosition = SdkCall.execute { isFollowingPosition() }
            followCursorButton.visibility = if (isFollowingPosition == true)
            {
                View.GONE
            }
            else
            {
                View.VISIBLE
            }
            
            onExitFollowingPosition = {
                followCursorButton.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                followCursorButton.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS, activity, permissions.toTypedArray()
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
    
    private fun selectRoute(route: Route)
    {
        gemSurfaceView.mapView?.apply {
            route.apply {
                showOverlayContainer(
                    summary.toString(),
                    "",
                    GemUtilImages.asBitmap(
                        ImageDatabase().getImageById(SdkImages.UI.RouteShape2.value),
                        imageSize,
                        imageSize
                    )
                )
            }
            preferences?.routes?.mainRoute = route
        }

        gemSurfaceView.mapView?.centerOnRoute(route)
    }
    
    // ---------------------------------------------------------------------------------------------

    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
    }
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
