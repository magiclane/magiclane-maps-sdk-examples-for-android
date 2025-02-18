/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.services

import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.OnStarted
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SettingsService
import com.magiclane.sdk.examples.androidauto.app.AppProcess
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.routesandnavigation.*
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.EnumHelp


object RoutingInstance {
    val service: RoutingService = RoutingService()
    val results: RouteList = arrayListOf()
    val listeners = mutableListOf<ProgressListener>()

    // ---------------------------------------------------------------------------------------------

    private val settingsService: SettingsService
        get() {
            if (!SettingsInstance.isInitialized())
                SettingsInstance.init()
            return SettingsInstance.service
        }

    private val onStarted: OnStarted = { hasProgress ->
        results.clear()

        listeners.forEach { it.notifyStart(hasProgress) }
    }

    private val onCompleted: OnRoutingCompleted = { routes, error, hint ->
        results.addAll(routes)

        listeners.forEach { it.notifyComplete(error, hint) }
    }

    // ---------------------------------------------------------------------------------------------

    fun init() {
        service.onStarted = onStarted
        service.onCompleted = onCompleted
        loadSettings()
    }

    // ---------------------------------------------------------------------------------------------

    fun calculateRoute(waypoints: LandmarkList, type: ERouteTransportMode): Int {
        val position = PositionService.position

        if (position?.isValid() == true) {
            return service.calculateRoute(waypoints, type, true)
        } else {
            AppProcess.waitForNextPosition {
                service.calculateRoute(waypoints, type, true)
            }
        }
        return GemError.NoError
    }

    // ---------------------------------------------------------------------------------------------
    // Settings

    var travelMode: ERouteType
        get() = service.preferences.routeType
        set(value) {
            settingsService.setIntValue("travelMode", value.value)
            service.preferences.routeType = value
        }

    var avoidTraffic: ETrafficAvoidance
        get() = service.preferences.avoidTraffic
        set(value) {
            settingsService.setIntValue("avoidTraffic", value.value)
            service.preferences.avoidTraffic = value
        }

    var avoidMotorways: Boolean
        get() = service.preferences.avoidMotorways
        set(value) {
            settingsService.setBooleanValue("avoidMotorways", value)
            service.preferences.avoidMotorways = value
        }

    var avoidTollRoads: Boolean
        get() = service.preferences.avoidTollRoads
        set(value) {
            settingsService.setBooleanValue("avoidTollRoads", value)
            service.preferences.avoidTollRoads = value
        }

    var avoidFerries: Boolean
        get() = service.preferences.avoidFerries
        set(value) {
            settingsService.setBooleanValue("avoidFerries", value)
            service.preferences.avoidFerries = value
        }

    var avoidUnpavedRoads: Boolean
        get() = service.preferences.avoidUnpavedRoads
        set(value) {
            settingsService.setBooleanValue("avoidUnpavedRoads", value)
            service.preferences.avoidUnpavedRoads = value
        }

    internal fun loadSettings() {
        service.preferences.routeType = EnumHelp.fromInt(
            settingsService.getIntValue("travelMode", ERouteType.Fastest.value)
        )
        service.preferences.avoidTraffic =
            EnumHelp.fromInt(settingsService.getIntValue("avoidTraffic", ETrafficAvoidance.None.value))
        service.preferences.avoidMotorways =
            settingsService.getBooleanValue("avoidMotorways", false)
        service.preferences.avoidTollRoads =
            settingsService.getBooleanValue("avoidTollRoads", false)
        service.preferences.avoidFerries =
            settingsService.getBooleanValue("avoidFerries", false)
        service.preferences.avoidUnpavedRoads =
            settingsService.getBooleanValue("avoidUnpavedRoads", true)
    }

    // ---------------------------------------------------------------------------------------------
}