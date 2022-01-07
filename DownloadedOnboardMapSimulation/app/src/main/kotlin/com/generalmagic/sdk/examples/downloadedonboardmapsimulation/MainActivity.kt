/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("SameParameterValue")

package com.generalmagic.sdk.examples.downloadedonboardmapsimulation

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.generalmagic.sdk.core.EUnitSystem
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.Time
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
        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one.
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
        }

        gemSurfaceView.onSdkInitSucceeded = {

            // Defines an action that should be done when the the sdk had been loaded.
            startSimulation()
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
}
