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

@file:Suppress("SameParameterValue")

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.externalpositionsourcenavigation

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.*
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.*
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.concurrent.fixedRateTimer
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    companion object
    {
        val positions = arrayOf(
            Pair(48.133931, 11.582914),
            Pair(48.134015, 11.583203),
            Pair(48.134057, 11.583348),
            Pair(48.134085, 11.583499),
            Pair(48.134116, 11.583676),
            Pair(48.134144, 11.583854),
            Pair(48.134166, 11.584010),
            Pair(48.134189, 11.584166),
            Pair(48.134210, 11.584312),
            Pair(48.134231, 11.584458),
            Pair(48.134253, 11.584605),
            Pair(48.134274, 11.584751),
            Pair(48.134295, 11.584897),
            Pair(48.134316, 11.585044),
            Pair(48.134338, 11.585190),
            Pair(48.134361, 11.585335),
            Pair(48.134390, 11.585515),
            Pair(48.134419, 11.585695),
            Pair(48.134443, 11.585846),
            Pair(48.134473, 11.585995),
            Pair(48.134503, 11.586126),
            Pair(48.134467, 11.586266),
            Pair(48.134430, 11.586405),
            Pair(48.134394, 11.586545),
            Pair(48.134357, 11.586684),
            Pair(48.134321, 11.586823),
            Pair(48.134283, 11.586967),
            Pair(48.134297, 11.587086),
            Pair(48.134380, 11.587167),
            Pair(48.134464, 11.587249),
            Pair(48.134563, 11.587345),
            Pair(48.134661, 11.587441),
            Pair(48.134761, 11.587534),
            Pair(48.134861, 11.587626),
            Pair(48.134975, 11.587732),
            Pair(48.135089, 11.587838),
            Pair(48.135204, 11.587943),
            Pair(48.135322, 11.588038),
            Pair(48.135451, 11.588137),
            Pair(48.135581, 11.588231),
            Pair(48.135716, 11.588328),
            Pair(48.135851, 11.588426),
            Pair(48.135972, 11.588513),
            Pair(48.136093, 11.588601),
            Pair(48.136207, 11.588680),
            Pair(48.136322, 11.588759),
            Pair(48.136423, 11.588829),
            Pair(48.136524, 11.588898),
            Pair(48.136615, 11.588962),
            Pair(48.136706, 11.589028),
            Pair(48.136807, 11.589117),
            Pair(48.136905, 11.589215),
            Pair(48.136994, 11.589347),
            Pair(48.137081, 11.589481),
            Pair(48.137164, 11.589608),
            Pair(48.137247, 11.589737),
            Pair(48.137344, 11.589894),
            Pair(48.137444, 11.590049),
            Pair(48.137538, 11.590199),
            Pair(48.137632, 11.590350),
            Pair(48.137730, 11.590508),
            Pair(48.137829, 11.590667),
            Pair(48.137934, 11.590834),
            Pair(48.138038, 11.591002),
            Pair(48.138134, 11.591156),
            Pair(48.138229, 11.591310),
            Pair(48.138316, 11.591454),
            Pair(48.138404, 11.591597),
            Pair(48.138496, 11.591749),
            Pair(48.138589, 11.591900),
            Pair(48.138681, 11.592051),
            Pair(48.138773, 11.592203),
            Pair(48.138867, 11.592351),
            Pair(48.138962, 11.592499),
            Pair(48.139041, 11.592624),
            Pair(48.139126, 11.592740),
            Pair(48.139207, 11.592828),
            Pair(48.139287, 11.592917),
            Pair(48.139374, 11.593012),
            Pair(48.139461, 11.593107),
            Pair(48.139571, 11.593208),
            Pair(48.139685, 11.593301),
            Pair(48.139809, 11.593403),
            Pair(48.139934, 11.593505),
            Pair(48.140077, 11.593604),
            Pair(48.140220, 11.593703),
            Pair(48.140364, 11.593802),
            Pair(48.140514, 11.593876),
            Pair(48.140670, 11.593947),
            Pair(48.140827, 11.594018)
        )
        val destination = Pair(48.17192581, 11.80789822)
    }

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var followCursorButton: FloatingActionButton

    private lateinit var topPanel: ConstraintLayout
    private lateinit var navInstruction: TextView
    private lateinit var navInstructionDistance: TextView
    private lateinit var navInstructionIcon: ImageView
    private lateinit var statusText: TextView

    private lateinit var bottomPanel: ConstraintLayout
    private lateinit var eta: TextView
    private lateinit var rtt: TextView
    private lateinit var rtd: TextView

    private lateinit var positionListener: PositionListener

    // Define a navigation service from which we will start navigation
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

            topPanel.isVisible = true
            bottomPanel.isVisible = true

            showStatusMessage("Navigation started.")
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

            statusText.isVisible = false
        }
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBar.isVisible = true
            showStatusMessage("Routing process started.")
        },
        onCompleted = { _, _ ->
            progressBar.isVisible = false
            showStatusMessage("Routing process completed.")
        },
        postOnMain = true
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gemSurfaceView = findViewById(R.id.gem_surface)
        progressBar = findViewById(R.id.progress_bar)
        followCursorButton = findViewById(R.id.follow_cursor)

        topPanel = findViewById(R.id.top_panel)
        navInstruction = findViewById(R.id.nav_instruction)
        navInstructionDistance = findViewById(R.id.instr_distance)
        navInstructionIcon = findViewById(R.id.nav_icon)
        statusText = findViewById(R.id.status_text)

        bottomPanel = findViewById(R.id.bottom_panel)
        eta = findViewById(R.id.eta)
        rtt = findViewById(R.id.rtt)
        rtd = findViewById(R.id.rtd)

        /// MAGIC LANE
        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one.
             */
            showDialog("TOKEN REJECTED")
        }

        SdkSettings.onMapDataReady = { mapReady ->
            if (mapReady)
            {
                var externalDataSource: ExternalDataSource?

                SdkCall.execute {
                    externalDataSource =
                        DataSourceFactory.produceExternal(arrayListOf(EDataType.Position))
                    externalDataSource?.start()

                    positionListener = PositionListener { position: PositionData ->
                        if (position.isValid())
                        {
                            navigationService.startNavigation(
                                Landmark("Poing", destination.first, destination.second),
                                navigationListener,
                                routingProgressListener
                            )

                            PositionService.removeListener(positionListener)
                        }
                    }

                    PositionService.dataSource = externalDataSource
                    PositionService.addListener(positionListener)

                    var index = 0
                    externalDataSource?.let { dataSource ->
                        fixedRateTimer("timer", false, 0L, 1000) {
                            SdkCall.execute {
                                val externalPosition = ExternalPosition.produceExternalPosition(
                                    System.currentTimeMillis(),
                                    positions[index].first,
                                    positions[index].second,
                                    -1.0,
                                    positions.getBearing(index),
                                    positions.getSpeed(index)
                                )
                                externalPosition?.let { pos ->
                                    dataSource.pushData(pos)
                                }

                            }
                            index++
                            if (index == positions.size)
                              index = 0
                        }
                    }
                }
            }
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

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
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
        statusText.isVisible = true
        statusText.text = text
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun enableGPSButton()
    {
        // Set actions for entering/ exiting following position mode.
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

    /**
     * @return estimated time of arrival
     */
    private fun Route.getEta(): String
    {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue = time.longValue + etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    // ---------------------------------------------------------------------------------------------------------------------------
    /**
     * @return remaining travel time
     */
    private fun Route.getRtt(): String
    {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * @return remaining travel distance
     */
    private fun Route.getRtd(): String
    {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0, EUnitSystem.Metric
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
/**
 * Mathematical formula for calculating real distance between 2 coordinates
 * @return real distance between 2 geographical points
 */
fun Pair<Double, Double>.getDistanceOnGeoid(to: Pair<Double, Double>): Double
{
    val (latitude1, longitude1) = this
    val (latitude2, longitude2) = to
    // convert degrees to radians
    val lat1 = latitude1 * Math.PI / 180.0
    val lon1 = longitude1 * Math.PI / 180.0
    val lat2 = latitude2 * Math.PI / 180.0
    val lon2 = longitude2 * Math.PI / 180.0

    // radius of earth in metres
    val r = 6378100.0
    // P
    val rho1 = r * cos(lat1)
    val z1 = r * sin(lat1)
    val x1 = rho1 * cos(lon1)
    val y1 = rho1 * sin(lon1)
    // Q
    val rho2 = r * cos(lat2)
    val z2 = r * sin(lat2)
    val x2 = rho2 * cos(lon2)
    val y2 = rho2 * sin(lon2)
    // dot product
    val dot = (x1 * x2 + y1 * y2 + z1 * z2)
    val cosTheta = dot / (r * r)
    val theta = acos(cosTheta)
    // distance in Metres
    return (r * theta)
}

// -------------------------------------------------------------------------------------------------------------------------------
/**
 * @return speed value equal with distance between point at [index] and previous point.
 * If there is no previous point returns -1.0
 */
fun Array<Pair<Double, Double>>.getSpeed(index: Int): Double
{
    if ((index > 0) && (index < size))
        return this[index - 1].getDistanceOnGeoid(this[index])
    return -1.0
}

// -------------------------------------------------------------------------------------------------------------------------------
/**
 * Calculates bearing between 2 points Formula β = atan2(X,Y) where  X and Y are two quantities
 * that can be calculated based on the given latitude and longitude
 * @return Bearing value between point at [index] and previous point.
 * If there is no previous point returns -1.0
 */
fun Array<Pair<Double, Double>>.getBearing(index: Int): Double
{
    if ((index > 0) && (index < size))
    {
        val x = cos(this[index].first) * sin(this[index].second - this[index - 1].second)
        val y =
            cos(this[index - 1].first) * sin(this[index].first) - sin(this[index - 1].first) * cos(
                this[index].first
            ) * cos(this[index].second - this[index - 1].second)
        return (atan2(x, y) * 180) / Math.PI
    }
    return -1.0
}

// -------------------------------------------------------------------------------------------------------------------------------