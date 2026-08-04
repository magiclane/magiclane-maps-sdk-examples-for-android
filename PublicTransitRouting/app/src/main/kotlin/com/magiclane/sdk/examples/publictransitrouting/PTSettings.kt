/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import com.magiclane.sdk.core.Time
import com.magiclane.sdk.routesandnavigation.EPTAlgorithmType
import com.magiclane.sdk.routesandnavigation.EPTRouteTypePreference
import com.magiclane.sdk.routesandnavigation.EPTSortingStrategy
import com.magiclane.sdk.routesandnavigation.RoutePreferences

/**
 * In-memory public-transit route settings, mirroring Magic Earth's public-transport settings:
 * date & time anchor, travel mode, min transfer time, preferred means of transport and
 * accessibility options.
 */
object PTSettings {

    enum class TimeMode {
        /** Depart at the given time (or now, when no custom time is set). */
        Depart,

        /** Arrive by the given time. */
        Arrive,

        /** Arrive with the last connection of today (23:59). */
        Last,
    }

    var timeMode = TimeMode.Depart

    /** Wall-clock anchor encoded as UTC epoch millis; null means "now" (automatic). */
    var customTimeMillis: Long? = null

    var sortingStrategy = EPTSortingStrategy.BestTime
    var minTransferTimeMinutes = 1

    var useBus = true
    var useUnderground = true
    var useRailway = true
    var useTram = true
    var useFerry = true
    var useOther = true

    var wheelchair = false
    var bicycle = false

    /** Snapshot of all values, used to detect changes when the settings screen closes. */
    fun snapshot(): List<Any?> = listOf(
        timeMode, customTimeMillis, sortingStrategy, minTransferTimeMinutes,
        useBus, useUnderground, useRailway, useTram, useFerry, useOther,
        wheelchair, bicycle,
    )

    /** Writes the settings into the routing [preferences]; must be called on the SDK thread. */
    fun applyTo(preferences: RoutePreferences) {
        preferences.sortingStrategy = sortingStrategy
        preferences.minimumTransferTimeInMinutes = minTransferTimeMinutes
        preferences.useWheelchair = wheelchair
        preferences.useBikes = bicycle

        var typePreferences = 0
        if (useBus) typePreferences = typePreferences or EPTRouteTypePreference.Bus.value
        if (useUnderground) typePreferences = typePreferences or EPTRouteTypePreference.Underground.value
        if (useRailway) typePreferences = typePreferences or EPTRouteTypePreference.Railway.value
        if (useTram) typePreferences = typePreferences or EPTRouteTypePreference.Tram.value
        if (useFerry) typePreferences = typePreferences or EPTRouteTypePreference.WaterTransport.value
        if (useOther) typePreferences = typePreferences or EPTRouteTypePreference.Misc.value
        preferences.routeTypePreferences = typePreferences

        preferences.algorithmType = if (timeMode == TimeMode.Depart) {
            EPTAlgorithmType.Departure
        } else {
            EPTAlgorithmType.Arrival
        }

        val millis = customTimeMillis
        if (millis != null) {
            preferences.timestamp = Time().apply { fromLong(millis) }
        } else {
            preferences.setIsAutomaticTimestamp()
        }
    }
}
