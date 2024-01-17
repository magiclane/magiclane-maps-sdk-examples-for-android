// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.downloadingonboardmapsimulation

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    private lateinit var gemSurfaceView: GemSurfaceView

    private lateinit var progressBar: ProgressBar

    private lateinit var followCursorButton: FloatingActionButton

    private lateinit var topPanel: ConstraintLayout

    private lateinit var navInstruction: TextView

    private lateinit var navInstructionDistance: TextView

    private lateinit var navInstructionIcon: ImageView

    private lateinit var bottomPanel: ConstraintLayout

    private lateinit var eta: TextView

    private lateinit var rtt: TextView

    private lateinit var rtd: TextView
    
    private lateinit var mapContainer: ConstraintLayout
    
    private lateinit var flagIcon: ImageView
    
    private lateinit var countryName: TextView
    
    private lateinit var mapDescription: TextView
    
    private lateinit var downloadProgressBar: ProgressBar
    
    private lateinit var downloadedIcon: ImageView
    
    private lateinit var statusText: TextView

    private val mapName = "Luxembourg"

    private val kDefaultToken = "YOUR_TOKEN"

    private var mapsCatalogRequested = false

    private var connected = false

    private var mapReady = false

    private var requiredMapHasBeenDownloaded = false

    // Define a content store that will deliver us the map.
    private val contentStore = ContentStore()

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /*
    Define a navigation listener that will receive notifications from the
    navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }

            topPanel.visibility = View.VISIBLE
            bottomPanel.visibility = View.VISIBLE
            
            showStatusMessage("Simulation started.")
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrIcon: Bitmap? = null
            var instrDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

            SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
                instrText = instr.nextStreetName ?: ""
                instrIcon = instr.nextTurnImage?.asBitmap(100, 100)
                instrDistance = instr.getDistanceInMeters()

                // Fetch data for the navigation bottom panel (route related info).
                navRoute?.apply {
                    etaText = getEta() // estimated time of arrival
                    rttText = getRtt() // remaining travel time
                    rtdText = getRtd() // remaining travel distance
                }
            }

            // Update the navigation panels info.
            navInstruction.text = instrText
            navInstructionIcon.setImageBitmap(instrIcon)
            navInstructionDistance.text = instrDistance

            eta.text = etaText
            rtt.text = rttText
            rtd.text = rtdText

            if (statusText.isVisible)
            {
                statusText.visibility = View.GONE
            }
        }
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
            showStatusMessage("Routing process started.")
        },
        onCompleted = { _, _ ->
            progressBar.visibility = View.GONE
            showStatusMessage("Routing process completed.")
        },
        postOnMain = true
    )

    private val contentListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
            showStatusMessage("Started content store service.")
        },
        onCompleted = { errorCode, _ ->
            progressBar.visibility = View.GONE
            showStatusMessage("Content store service completed with error code: $errorCode")

            when (errorCode)
            {
                GemError.NoError ->
                {
                    // No error encountered, we can handle the results.
                    SdkCall.execute { // Get the list of maps that was retrieved in the content store.
                        val contentListPair = contentStore.getStoreContentList(EContentType.RoadMap) ?: return@execute

                        for (map in contentListPair.first)
                        {
                            val mapName = map.name ?: continue
                            if (mapName.compareTo(this.mapName, true) != 0) // searching another map
                            {
                                continue
                            }

                            if (!map.isCompleted())
                            {
                                // Define a listener to the progress of the map download action.
                                val downloadProgressListener = ProgressListener.create(
                                    onStarted = {
                                        onDownloadStarted(map)
                                        showStatusMessage("Started downloading $mapName.") 
                                    },
                                    onStatusChanged = { status ->
                                        onStatusChanged(status)
                                    },
                                    onProgress = { progress ->
                                        onProgressUpdated(progress)
                                    },
                                    onCompleted = { errorCode, _ ->
                                        if (errorCode == GemError.NoError)
                                        {
                                            showStatusMessage("$mapName was downloaded.")
                                            onOnboardMapReady()
                                        }
                                    })

                                // Start downloading the first map item.
                                map.asyncDownload(downloadProgressListener, GemSdk.EDataSavePolicy.UseDefault, true)
                            }

                            break
                        }
                    }
                }
                GemError.Cancel ->
                { // The action was cancelled.
                }
                else ->
                {
                    // There was a problem at retrieving the content store items.
                    showDialog("Content store service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gemSurfaceView = findViewById(R.id.gem_surface)
        progressBar = findViewById(R.id.progressBar)
        followCursorButton = findViewById(R.id.followCursor)

        topPanel = findViewById(R.id.top_panel)
        navInstruction = findViewById(R.id.nav_instruction)
        navInstructionDistance = findViewById(R.id.instr_distance)
        navInstructionIcon = findViewById(R.id.nav_icon)

        bottomPanel = findViewById(R.id.bottom_panel)
        eta = findViewById(R.id.eta)
        rtt = findViewById(R.id.rtt)
        rtd = findViewById(R.id.rtd)
        
        mapContainer = findViewById(R.id.map_container)
        flagIcon = findViewById(R.id.flag_icon)
        countryName = findViewById(R.id.country_name)
        mapDescription = findViewById(R.id.map_description)
        downloadProgressBar = findViewById(R.id.download_progress_bar)
        downloadedIcon = findViewById(R.id.downloaded_icon)
        
        statusText = findViewById(R.id.status_text)

        val loadMaps = {
            mapsCatalogRequested = true

            val loadMapsCatalog = {
                SdkCall.execute {
                    // Call to the content store to asynchronously retrieve the list of maps.
                    contentStore.asyncGetStoreContentList(EContentType.RoadMap, contentListener)
                }
            }

            val token = GemSdk.getTokenFromManifest(this)

            if (!token.isNullOrEmpty() && (token != kDefaultToken))
            {
                loadMapsCatalog()
            }
            else // if token is not present try to avoid content server requests limitation by delaying the voices catalog request
            {
                progressBar.visibility = View.VISIBLE

                Handler(Looper.getMainLooper()).postDelayed({
                    loadMapsCatalog()
                }, 3000)
            }
        }

        SdkSettings.onMapDataReady = { it ->
            if (!requiredMapHasBeenDownloaded)
            {
                mapReady = it
                if (connected && mapReady && !mapsCatalogRequested)
                {
                    loadMaps()
                }
            }
        }

        SdkSettings.onConnectionStatusUpdated = { it ->
            if (!requiredMapHasBeenDownloaded)
            {
                connected = it
                if (connected && mapReady && !mapsCatalogRequested)
                {
                    loadMaps()
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

        gemSurfaceView.onSdkInitSucceeded = onSdkInitSucceeded@{
            val localMaps = contentStore.getLocalContentList(EContentType.RoadMap) ?: return@onSdkInitSucceeded

            for (map in localMaps)
            {
                val mapName = map.name ?: continue
                if (mapName.compareTo(this.mapName, true) == 0)
                {
                    requiredMapHasBeenDownloaded = map.isCompleted()
                    break
                }
            }

            // Defines an action that should be done when the the sdk had been loaded.
            if (requiredMapHasBeenDownloaded)
            {
                onOnboardMapReady()
            }
        }

        if (!requiredMapHasBeenDownloaded && !Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
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
    
    private fun onDownloadStarted(map: ContentStoreItem)
    {
        mapContainer.visibility = View.VISIBLE
        
        var flagBitmap: Bitmap? = null 
        SdkCall.execute {
            map.countryCodes?.let { codes ->
                val size = resources.getDimension(R.dimen.icon_size).toInt()
                flagBitmap = MapDetails().getCountryFlag(codes[0])?.asBitmap(size, size)
            }
        }
        flagIcon.setImageBitmap(flagBitmap)
        
        countryName.text = SdkCall.execute { map.name }
        mapDescription.text = SdkCall.execute { GemUtil.formatSizeAsText(map.totalSize) }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun onStatusChanged(status: Int)
    {
        when (EContentStoreItemStatus.values()[status])
        {
            EContentStoreItemStatus.Completed ->
            {
                downloadedIcon.visibility = View.VISIBLE
                downloadProgressBar.visibility = View.INVISIBLE
            }
            
            EContentStoreItemStatus.DownloadRunning ->
            {
                downloadedIcon.visibility = View.GONE
                downloadProgressBar.visibility = View.VISIBLE
            }
            
            else -> return
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun onProgressUpdated(progress: Int)
    {
        downloadProgressBar.progress = progress
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onOnboardMapReady()
    {
        startSimulation()
        mapContainer.visibility = View.GONE
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Luxembourg", 49.61588784436375, 6.135843869736401), Landmark("Mersch", 49.74785494642988, 6.103323786692679)
        )

        navigationService.startSimulation(
            waypoints, navigationListener, routingProgressListener
        )
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

    private fun NavigationInstruction.getDistanceInMeters(): String
    {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0, EUnitSystem.Metric
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Route.getEta(): String
    {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue = time.longValue + etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Route.getRtt(): String
    {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Route.getRtd(): String
    {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0, EUnitSystem.Metric
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun enableGPSButton()
    { // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
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

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------

