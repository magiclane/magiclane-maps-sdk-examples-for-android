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
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.downloadingonboardmapsimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.Locale
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    private lateinit var binding: ActivityMainBinding

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
                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }

            binding.topPanel.isVisible = true
            binding.bottomPanel.isVisible = true

            showStatusMessage("Simulation started.")
            //EspressoIdlingResource.decrementNavigationResource()
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
            binding.apply {
                navInstruction.text = instrText
                navInstructionIcon.setImageBitmap(instrIcon)
                navInstructionDistance.text = instrDistance

                eta.text = etaText
                rtt.text = rttText
                rtd.text = rtdText

                if (statusText.isVisible)
                {
                    statusText.isVisible = false
                }
            }
            EspressoIdlingResource.decrementNavigationResource()
        }
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
            showStatusMessage("Routing process started.")
        },
        onCompleted = { _, _ ->
            binding.progressBar.isVisible = false
            showStatusMessage("Routing process completed.")
        },
        postOnMain = true
    )

    private val contentListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
            showStatusMessage("Started content store service.")
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false
            showStatusMessage("Content store service completed with error code: $errorCode")

            when (errorCode)
            {
                GemError.NoError ->
                {
                    // No error encountered, we can handle the results.
                    SdkCall.execute { // Get the list of maps that was retrieved in the content store.
                        val contentListPair =
                            contentStore.getStoreContentList(EContentType.RoadMap) ?: return@execute

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
                                        } else
                                            EspressoIdlingResource.decrementDownloadingResource()
                                    })

                                // Start downloading the first map item.
                                map.asyncDownload(
                                    downloadProgressListener,
                                    GemSdk.EDataSavePolicy.UseDefault,
                                    true
                                )
                            }

                            break
                        }
                    }
                }

                GemError.Cancel ->
                { // The action was cancelled.
                    showStatusMessage("Content store service completed with error code: $errorCode")
                    EspressoIdlingResource.decrementDownloadingResource()
                }

                else ->
                {
                    // There was a problem at retrieving the content store items.
                    showStatusMessage("Content store service completed with error code: $errorCode")
                    showDialog("Content store service error: ${GemError.getMessage(errorCode)}")
                    EspressoIdlingResource.decrementDownloadingResource()
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (EspressoIdlingResource.isDownloadingTest)
            EspressoIdlingResource.incrementDownloadingResource()
        else
            EspressoIdlingResource.incrementNavigationResource()
        val loadMaps = {
            mapsCatalogRequested = true
            val loadMapsCatalog = {
                SdkCall.execute {
                    // Call to the content store to asynchronously retrieve the list of maps.
                    contentStore.asyncGetStoreContentList(EContentType.RoadMap, contentListener)
                }
            }

            val token = GemSdk.getTokenFromManifest(this)

            if (!token.isNullOrEmpty() && (token != kDefaultToken)) loadMapsCatalog()
            else // if token is not present try to avoid content server requests limitation by delaying the voices catalog request
            {
                binding.progressBar.isVisible = true

                Handler(Looper.getMainLooper()).postDelayed({
                    loadMapsCatalog()
                }, 3000)
            }
        }

        SdkSettings.onMapDataReady = { it ->
            if (!requiredMapHasBeenDownloaded)
            {
                mapReady = it
                if (connected && mapReady && !mapsCatalogRequested) loadMaps()
            }
        }

        SdkSettings.onConnectionStatusUpdated = { it ->
            if (!requiredMapHasBeenDownloaded)
            {
                connected = it
                if (connected && mapReady && !mapsCatalogRequested) loadMaps()
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

        binding.gemSurfaceView.onSdkInitSucceeded = onSdkInitSucceeded@{
            val localMaps =
                contentStore.getLocalContentList(EContentType.RoadMap) ?: return@onSdkInitSucceeded

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
            if (requiredMapHasBeenDownloaded) onOnboardMapReady()
        }

        if (!requiredMapHasBeenDownloaded && !Util.isInternetConnected(this))
            showDialog("You must be connected to the internet!")

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }

    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onStop()
    {
        super.onStop()

        // Release the SDK.
        if (isFinishing)
            GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onDownloadStarted(map: ContentStoreItem)
    {
        binding.apply {
            mapContainer.isVisible = true

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
        EspressoIdlingResource.decrementDownloadingResource()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onStatusChanged(status: Int)
    {
        binding.downloadedIcon.isVisible =
            EContentStoreItemStatus.entries.toTypedArray()[status] == EContentStoreItemStatus.Completed
        binding.downloadProgressBar.isInvisible =
            EContentStoreItemStatus.entries.toTypedArray()[status] == EContentStoreItemStatus.Completed
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onProgressUpdated(progress: Int)
    {
        binding.downloadProgressBar.progress = progress
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onOnboardMapReady()
    {
        startSimulation()
        binding.mapContainer.isVisible = false
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Luxembourg", 49.61588784436375, 6.135843869736401),
            Landmark("Mersch", 49.74785494642988, 6.103323786692679)
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
        binding.statusText.isVisible = true
        binding.statusText.text = text
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
        time.longValue += etaNumber * 1000
        return String.format(Locale.getDefault(), "%d:%02d", time.hour, time.minute)
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
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followCursorButton.isVisible = true
                }
                onEnterFollowingPosition = {
                    followCursorButton.isVisible = false
                }
                // Set on click action for the GPS button.
                followCursorButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }
}
// -------------------------------------------------------------------------------------------------------------------------------

@VisibleForTesting(VisibleForTesting.PRIVATE)
object EspressoIdlingResource
{
    var isDownloadingTest = false
    val navigationIdlingResource = CountingIdlingResource("NavigationIdlingResource")
    val downloadingIdlingResource = CountingIdlingResource("DownloadingIdlingResource")
    fun incrementNavigationResource() = navigationIdlingResource.increment()
    fun incrementDownloadingResource() = downloadingIdlingResource.increment()
    fun decrementNavigationResource() = if (!navigationIdlingResource.isIdleNow) navigationIdlingResource.decrement() else Unit
    fun decrementDownloadingResource() = if (!downloadingIdlingResource.isIdleNow) downloadingIdlingResource.decrement() else Unit
}
// -------------------------------------------------------------------------------------------------------------------------------
