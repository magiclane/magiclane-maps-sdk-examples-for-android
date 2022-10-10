// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.routesimulationwithinstructions

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.generalmagic.sdk.core.EUnitSystem
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.Time
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.NavigationInstruction
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.routesandnavigation.NavigationService
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.GemUtil
import com.generalmagic.sdk.util.GemUtilImages
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    class TSameImage(var value: Boolean = false)

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

    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var turnImageSize: Int = 0

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
            var instrDistance = ""
    
            var etaText = ""
            var rttText = ""
            var rtdText = ""
    
            SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
                instrText = instr.nextStreetName ?: ""
    
                if (instrText.isEmpty())
                {
                    instrText = instr.nextTurnInstruction ?: ""
                }
    
                instrDistance = instr.getDistanceInMeters()
    
                // Fetch data for the navigation bottom panel (route related info).
                navRoute?.apply {
                    etaText = getEta() // estimated time of arrival
                    rttText = getRtt() // remaining travel time
                    rtdText = getRtd() // remaining travel distance
                }
            }
    
            // Update the navigation panels info.
            val sameTurnImage = TSameImage()
            val newTurnImage = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
            if (!sameTurnImage.value)
            {
                navInstructionIcon.setImageBitmap(newTurnImage)
            }
    
            navInstruction.text = instrText
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

        onCompleted = { errorCode, _ ->
            progressBar.visibility = View.GONE

            if (errorCode != GemError.NoError)
            {
                showDialog(GemError.getMessage(errorCode))
            }
        },

        postOnMain = true
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getNextTurnImage(navInstr: NavigationInstruction, width: Int, height: Int, bSameImage: TSameImage): Bitmap?
    {
        return SdkCall.execute {
            if (!navInstr.hasNextTurnInfo()) return@execute null
            if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId)
            {
                bSameImage.value = true
                return@execute null
            }

            val image = navInstr.nextTurnDetails?.abstractGeometryImage
            if (image != null)
            {
                lastTurnImageId = image.uid
            }

            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            GemUtilImages.asBitmap(
                image, width, height, aInner, aOuter, iInner, iOuter
            )
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

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

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startSimulation()
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

    private fun NavigationInstruction.getDistanceInMeters(): String
    {
        return GemUtil.getDistText(this.timeDistanceToNextTurn?.totalDistance ?: 0, EUnitSystem.Metric).let { pair ->
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
        return GemUtil.getTimeText(this.getTimeDistance(true)?.totalTime ?: 0).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Route.getRtd(): String
    {
        return GemUtil.getDistText(this.getTimeDistance(true)?.totalDistance ?: 0, EUnitSystem.Metric).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(Landmark("Amsterdam", 52.3585050, 4.8803423), Landmark("Paris", 48.8566932, 2.3514616))

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
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
}
