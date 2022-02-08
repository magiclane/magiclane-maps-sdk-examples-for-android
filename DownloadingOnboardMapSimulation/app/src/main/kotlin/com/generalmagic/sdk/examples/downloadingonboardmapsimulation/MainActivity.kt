/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.downloadingonboardmapsimulation

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.NavigationInstruction
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.routesandnavigation.NavigationService
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkUtil
import com.generalmagic.sdk.util.Util
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
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

    val MAP_NAME = "Luxembourg"

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
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrIcon: Bitmap? = null
            var instrDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

            SdkCall.execute {
                // Fetch data for the navigation top panel (instruction related info).
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

    private val contentListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    // No error encountered, we can handle the results.

                    SdkCall.execute execute@{
                        // Get the list of maps that was retrieved in the content store.
                        val contentListPair =
                            contentStore.getStoreContentList(EContentType.RoadMap) ?: return@execute

                        for (map in contentListPair.first) {
                            val mapName = map.name ?: continue
                            if (mapName.compareTo(MAP_NAME, true) != 0)
                            // searching another map
                                continue

                            if (map.isCompleted())
                            // already downloaded
                                return@execute

                            // Define a listener to the progress of the map download action.
                            val downloadProgressListener = ProgressListener.create(
                                onStarted = {
                                    progressBar.visibility = View.VISIBLE
                                    showToast("Started downloading $mapName.")
                                },

                                onCompleted = { errorCode, _ ->
                                    progressBar.visibility = View.GONE

                                    if (errorCode == GemError.NoError) {
                                        showToast("$mapName was downloaded.")
                                        onOnboardMapReady()
                                    }
                                }
                            )

                            // Start downloading the first map item.
                            map.asyncDownload(
                                downloadProgressListener,
                                GemSdk.EDataSavePolicy.UseDefault,
                                true
                            )

                            break
                        }
                    }
                }

                GemError.Cancel -> {
                    // The action was cancelled.
                }

                else -> {
                    // There was a problem at retrieving the content store items.
                    showToast("Content store service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
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

        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            SdkCall.execute {
                // Defines an action that should be done after the network is connected.
                // Call to the content store to asynchronously retrieve the list of maps.
                contentStore.asyncGetStoreContentList(EContentType.RoadMap, contentListener)
            }
        }

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one.
             */
            showToast("TOKEN REJECTED")
        }

        gemSurfaceView.onSdkInitSucceeded = onSdkInitSucceeded@{
            var mapFound = false

            val localMaps =
                contentStore.getLocalContentList(EContentType.RoadMap) ?: return@onSdkInitSucceeded

            for (map in localMaps) {
                val mapName = map.name ?: continue
                if (mapName.compareTo(MAP_NAME, true) != 0)
                // searching another map
                    continue

                if (!map.isCompleted()) {
                    // can't continue with incomplete map.
                    break
                }

                mapFound = true
            }

            // Defines an action that should be done when the the sdk had been loaded.
            if (mapFound)
                onOnboardMapReady()
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onOnboardMapReady() {
        startSimulation()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Luxembourg", 49.61588784436375, 6.135843869736401),
            Landmark("Mersch", 49.74785494642988, 6.103323786692679)
        )

        navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return SdkUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0, EUnitSystem.Metric
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue = time.longValue + etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun Route.getRtt(): String {
        return SdkUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun Route.getRtd(): String {
        return SdkUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0, EUnitSystem.Metric
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

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
}

