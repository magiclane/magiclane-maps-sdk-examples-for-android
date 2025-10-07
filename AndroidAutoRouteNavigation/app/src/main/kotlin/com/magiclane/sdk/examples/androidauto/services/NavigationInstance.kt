/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.services

import android.Manifest
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.androidauto.util.TripModel
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.GemUtil

@Suppress("unused")
object NavigationInstance {
    val service: NavigationService = NavigationService()
    val listeners = mutableListOf<NavigationListener>()

    // ---------------------------------------------------------------------------------------------

    var currentRoute: Route? = null
        get() {
            if (field == null || isNavOrSimActive) {
                return service.getNavigationRoute(navigationListener)
            }
            return field
        }
        private set

    val isNavOrSimActive: Boolean
        get() = service.isNavigationActive(navigationListener)
                || service.isSimulationActive(navigationListener)

    var currentInstruction: NavigationInstruction? = null
        private set

    val remainingDistance: Int
        get() {
            // from last instr
            currentInstruction?.let {
                if (it.navigationStatus == ENavigationStatus.Running)
                    return it.remainingTravelTimeDistance?.totalDistance ?: 0
            }

            // from route
            return currentRoute?.getTimeDistance(true)?.totalDistance ?: 0
        }

    val remainingTime: Int
        get() {
            // from last instr
            currentInstruction?.let {
                if (it.navigationStatus == ENavigationStatus.Running)
                    return it.remainingTravelTimeDistance?.totalTime ?: 0
            }

            // from route
            return currentRoute?.getTimeDistance(true)?.totalTime ?: 0
        }

    val remainingTimeIncludingTraffic: Int
        get() {
            return remainingTime + (currentRoute?.let {
                GemUtil.getTrafficEventsDelay(it, true)
            } ?: 0)
        }

    val eta: Time?
        get() {
            currentRoute ?: return null
            currentInstruction ?: return null

            val arrivalTime = Time()

            arrivalTime.setLocalTime()
            arrivalTime.longValue = arrivalTime.longValue + remainingTimeIncludingTraffic * 1000

            return arrivalTime
        }

    val permissionsRequired = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // ---------------------------------------------------------------------------------------------

    private val progressListener = ProgressListener.create()

    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            listeners.forEach { it.onNavigationStarted() }
        },
        onDestinationReached = { landmark ->
            listeners.forEach { it.onDestinationReached(landmark) }
        },
        onNavigationError = { error ->
            listeners.forEach { it.onNavigationError(error) }
        },
        onNavigationInstructionUpdated = { instr ->
            currentInstruction = instr
            listeners.forEach { it.onNavigationInstructionUpdated(instr) }
        }
    )

    // ---------------------------------------------------------------------------------------------
    fun init() {}

    // ---------------------------------------------------------------------------------------------

    fun startNavigation(route: Route) = startWithRoute(route)

    fun startSimulation(route: Route) = startWithRoute(route, true)

    private fun startWithRoute(route: Route, isSimulation: Boolean = false) = SdkCall.execute {
        // Cancel any navigation in progress.
        service.cancelNavigation(navigationListener)

        currentRoute = route

        val tripModel = TripModel()
        tripModel.set(route, true)

        HistoryInstance.service.saveTrip(tripModel)

        if (isSimulation) {
            service.startSimulationWithRoute(route, navigationListener, progressListener)
        } else {
            service.startNavigationWithRoute(route, navigationListener, progressListener)
        }
    }

    fun stopNavigation() = SdkCall.execute {
        service.cancelNavigation(navigationListener)
    }

    // ---------------------------------------------------------------------------------------------
}
