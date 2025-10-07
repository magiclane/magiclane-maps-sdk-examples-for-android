// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.definepersistentroadblock

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Traffic
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var hint: TextView

    private var roadblock: TrafficEvent? = null

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gemSurfaceView = findViewById(R.id.gem_surface_view)
        hint = findViewById(R.id.hint)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            gemSurfaceView.mapView?.onTouch = { xy ->
                SdkCall.execute {
                    // tell the map view where the touch event happened
                    gemSurfaceView.mapView?.cursorScreenPosition = xy

                    val trafficEvents = gemSurfaceView.mapView?.cursorSelectionTrafficEvents
                    if (!trafficEvents.isNullOrEmpty())
                    {
                        val trafficEvent = trafficEvents[0]
                        if (trafficEvent.isRoadblock())
                        {
                            return@execute
                        }
                    }

                    val streets = gemSurfaceView.mapView?.cursorSelectionStreets
                    if (!streets.isNullOrEmpty())
                    {
                        streets[0].coordinates?.let { addPersistentRoadblock(it) }
                    }
                }
            }

            hint.visibility = View.VISIBLE
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

        onBackPressedDispatcher.addCallback(this){
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

    private fun addPersistentRoadblock(coordinates: Coordinates)
    {
        val startTime = Time.getUniversalTime()
        val endTime = Time.getUniversalTime().also { endTime -> endTime?.let { it.minute += 1 } }

        if (startTime != null && endTime != null)
        {
            val traffic = Traffic()

            roadblock?.let { roadblock ->
                roadblock.referencePoint?.let { coordinates ->
                    traffic.removePersistentRoadblock(coordinates)
                }
            }

            roadblock = traffic.addPersistentRoadblock(
                coords = arrayListOf(coordinates),
                startUTC = startTime,
                expireUTC = endTime,
                transportMode = ERouteTransportMode.Car.value
            )

            if (roadblock?.referencePoint?.valid() == true)
            {
                roadblock?.boundingBox?.let {
                    gemSurfaceView.mapView?.centerOnArea(
                        area = it,
                        zoomLevel = -1,
                        xy = null,
                        animation = Animation(EAnimation.Linear)
                    )
                }

                Util.postOnMain { hint.visibility = View.GONE }
            }
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
}

// -------------------------------------------------------------------------------------------------
