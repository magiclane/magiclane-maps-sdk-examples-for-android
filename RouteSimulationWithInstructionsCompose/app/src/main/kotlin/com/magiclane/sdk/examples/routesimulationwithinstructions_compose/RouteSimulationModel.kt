// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routesimulationwithinstructions_compose

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------------------------------------

class RouteSimulationModel : ViewModel()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    class TSameImage(var value: Boolean = false)

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBarIsVisible = true
        },
        onCompleted = { errorCode, _ ->
            progressBarIsVisible = false

            if (errorCode != GemError.NoError)
            {
                errorMessage = GemError.getMessage(errorCode)
            }
        },
        postOnMain = true
    )

    /*
    Define a navigation listener that will receive notifications from the
    navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                surfaceView?.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    mapView.onExitFollowingPosition = {
                        followGpsButtonIsVisible = true
                    }

                    mapView.onEnterFollowingPosition = {
                        followGpsButtonIsVisible = false
                    }

                    mapView.followPosition()
                }
            }

            navigationPanelsAreVisible = true
        },
        onNavigationInstructionUpdated = { navigationInstruction ->
            refresh(navigationInstruction)
        }
    )

    var turnImageSize: Int = 0

    var lastTurnImageId by mutableStateOf(Long.MAX_VALUE)

    var progressBarIsVisible by mutableStateOf(false)

    var followGpsButtonIsVisible by mutableStateOf(false)

    var navigationPanelsAreVisible by mutableStateOf(false)

    var newTurnImage by mutableStateOf(false)

    var instrText by mutableStateOf("")

    var instrDistance by mutableStateOf("")

    var etaText by mutableStateOf("")

    var rttText by mutableStateOf("")

    var rtdText by mutableStateOf("")

    var errorMessage by mutableStateOf("")

    var turnImage: ImageBitmap = ImageBitmap(1, 1)

    @SuppressLint("StaticFieldLeak")
    var surfaceView: GemSurfaceView? = null

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun refresh(navigationInstruction: NavigationInstruction)
    {
        SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
            var navInstrText = navigationInstruction.nextStreetName ?: ""

            if (navInstrText.isEmpty())
            {
                navInstrText = navigationInstruction.nextTurnInstruction ?: ""
            }

            instrText = navInstrText

            instrDistance = navigationInstruction.getDistanceInMeters()

            // Fetch data for the navigation bottom panel (route related info).
            navRoute?.apply {
                etaText = getEta() // estimated time of arrival
                rttText = getRtt() // remaining travel time
                rtdText = getRtd() // remaining travel distance
            }

            val sameTurnImage = TSameImage()
            val turnBmp = getNextTurnImage(navigationInstruction, turnImageSize, turnImageSize, sameTurnImage)

            newTurnImage = !sameTurnImage.value

            if (newTurnImage)
            {
                turnImage = turnBmp?.asImageBitmap() ?: ImageBitmap(1, 1)
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
        time.longValue += etaNumber * 1000
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

    private fun getNextTurnImage(navInstr: NavigationInstruction, width: Int, height: Int, bSameImage: TSameImage): Bitmap?
    {
        if (!navInstr.hasNextTurnInfo()) return null
        if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId)
        {
            bSameImage.value = true
            return null
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

        return GemUtilImages.asBitmap(
            image, width, height, aInner, aOuter, iInner, iOuter
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    fun startFollowingPosition() = SdkCall.execute {
        surfaceView?.mapView?.followPosition()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(Landmark("Amsterdam", 52.3585050, 4.8803423), Landmark("Paris", 48.8566932, 2.3514616))

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
