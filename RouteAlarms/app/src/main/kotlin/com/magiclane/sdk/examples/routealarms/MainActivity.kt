// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routealarms

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.OverlayService
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.routesandnavigation.AlarmListener
import com.magiclane.sdk.routesandnavigation.AlarmService
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.EStringIds
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener
{
    // ---------------------------------------------------------------------------------------------

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var followCursorButton: FloatingActionButton
    private lateinit var alarmPanel: ConstraintLayout
    private lateinit var alarmText: TextView
    private lateinit var alarmImage: ImageView
    private var alarmImageSize = 0
    private var safetyAlarmId = 0

    companion object
    {
        const val RESOURCE = "GLOBAL"
    }

    private var mainActivityIdlingResource = CountingIdlingResource(RESOURCE, true)
    private var alarmIdlingResource = CountingIdlingResource(RESOURCE, true)

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    // Define an alarm service to be able to track alarms on the map.
    private var alarmService: AlarmService? = null

    /* 
    Define an alarm listener that will receive notifications from the
    alarms service.
    We will use just the onOverlayItemAlarmsUpdated method, but for more available
    methods you should check the documentation at https://magiclane.com/documentation/
     */
    private val alarmListener = AlarmListener.create(
        onOverlayItemAlarmsUpdated = {
            SdkCall.execute execute@{
                // Get the overlay items that are present and relevant.
                val alarmsList = alarmService?.overlayItemAlarms
                if ((alarmsList == null) || (alarmsList.size == 0))
                {
                    return@execute
                }

                // Get the maximum distance until an alarm is reached.
                val maxDistance = alarmService?.alarmDistance ?: 0.0

                // Get the distance to the closest alarm marker.
                val distance = alarmsList.getDistance(0)
                if (distance <= maxDistance)
                {
                    var bmp: Bitmap? = null

                    alarmsList.getItem(0)?.let { alarm ->
                        val id = alarm.overlayUid
                        if (id != safetyAlarmId)
                        {
                            if (safetyAlarmId != 0)
                            {
                                removeHighlightedAlarm()
                            }

                            safetyAlarmId = id

                            alarm.image?.let { image ->
                                bmp = GemUtilImages.asBitmap(image, alarmImageSize, alarmImageSize)

                                alarm.coordinates?.let { coordinates ->
                                    highlightAlarm(image, coordinates)
                                }
                            }

                            if (SoundPlayingService.ttsPlayerIsInitialized)
                            {
                                val warning = String.format(
                                    GemUtil.getTTSString(EStringIds.eStrCaution),
                                    GemUtil.getTTSString(EStringIds.eStrSpeedCamera)
                                )
                                SoundPlayingService.playText(
                                    warning,
                                    SoundPlayingListener(),
                                    SoundPlayingPreferences()
                                )
                            }
                        }
                    }

                    // If you are close enough to the alarm item, notify the user.
                    Util.postOnMain {
                        if (!alarmPanel.isVisible)
                        {
                            alarmPanel.visibility = View.VISIBLE
                        }

                        alarmText.text = getString(R.string.alarm_text, distance.toInt())
                        bmp?.let { alarmImage.setImageBitmap(it) }
                    }

                    // Remove the alarm listener if you want to notify only once.
                    // alarmService?.setAlarmListener(null)
                }
            }
            if (!alarmIdlingResource.isIdleNow)
                alarmIdlingResource.decrement()
        },

        onOverlayItemAlarmsPassedOver = {
            alarmPanel.visibility = View.GONE
            SdkCall.execute {
                removeHighlightedAlarm()
            }
        }
    )

    /* 
    Define a navigation listener that will receive notifications from the
    navigation service.
    We will use just the onNavigationStarted method, but for more available
    methods you should check the documentation.
     */
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                // Set the overlay for which to be notified.
                setAlarmOverlay(ECommonOverlayId.Safety)
                gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                    mainActivityIdlingResource.decrement()
                }
            }
        },

        onDestinationReached = {
            SdkCall.execute {
                gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.routes?.clear()
                }
            }

            followCursorButton.visibility = View.GONE
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

        SoundUtils.addTTSPlayerInitializationListener(this)

        setContentView(R.layout.activity_main)
        mainActivityIdlingResource.increment()
        alarmIdlingResource.increment()
        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        followCursorButton = findViewById(R.id.followCursor)
        alarmPanel = findViewById(R.id.alarm_panel)
        alarmText = findViewById(R.id.alarm_text)
        alarmImage = findViewById(R.id.alarm_image)
        alarmImageSize = resources.getDimensionPixelSize(R.dimen.alarm_image_size)

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startSimulation()
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

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    private fun enableGPSButton()
    {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {
                if (SdkCall.execute { navigationService.isSimulationActive() } == true)
                {
                    followCursorButton.visibility = View.VISIBLE
                }
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

    @Suppress("SameParameterValue")
    private fun setAlarmOverlay(overlay: ECommonOverlayId)
    {
        SdkCall.execute {
            alarmService = AlarmService.produce(alarmListener)
            alarmService?.alarmDistance = 500.0 // meters
            OverlayService().getAvailableOverlays(null)?.first?.let { list ->
                alarmService?.overlays?.add(ArrayList(list.filter { it.uid == overlay.value }))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("A", 53.056306247688326, 8.882596560149098),
            Landmark("B", 53.06178963549359, 8.876610724727849)
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
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

    private fun highlightAlarm(image: Image, coordinates: Coordinates)
    {
        gemSurfaceView.mapView?.let { mapView ->
            val landmark = Landmark()
            landmark.image = image
            landmark.coordinates = coordinates

            val lmkList = LandmarkList()
            lmkList.add(landmark)

            val displaySettings = HighlightRenderSettings().also { settings ->
                settings.setOptions(EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value)
            }

            mapView.activateHighlightLandmarks(lmkList, displaySettings, 0)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun removeHighlightedAlarm()
    {
        gemSurfaceView.mapView?.deactivateHighlight(0)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onTTSPlayerInitialized()
    {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ---------------------------------------------------------------------------------------------

    @VisibleForTesting
    fun getActivityIdlingResource(): IdlingResource
    {
        return mainActivityIdlingResource
    }

    @VisibleForTesting
    fun getAlarmIdlingResource(): IdlingResource
    {
        return alarmIdlingResource
    }
}

// -------------------------------------------------------------------------------------------------