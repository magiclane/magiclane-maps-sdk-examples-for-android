/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.util

import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.androidauto.R
import com.magiclane.sdk.examples.androidauto.app.AppProcess
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.routesandnavigation.PTRoute
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RouteBookmarks
import com.magiclane.sdk.routesandnavigation.RoutePreferences
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.Util
import kotlin.math.abs

/**
 * Trip model. Used in [TripsHistory].
 */
class TripModel {
    /**
     * Route Preferences.
     */
    var preferences: RoutePreferences? = null

    /**
     * Name.
     */
    var name: String = ""

    /**
     * Route Waypoints.
     */
    var waypoints: LandmarkList = arrayListOf()

    /**
     * Ignore first waypoint ( departure waypoint )
     */
    var ignoreDeparture: Boolean = false

    /**
     * Timestamp
     */
    var timestamp: Long = 0L

    /**
     * @param ignoreDeparture will remove the first waypoint which previously had been considered as current location.
     */
    fun set(route: Route, ignoreDeparture: Boolean = false) {
        this.ignoreDeparture = ignoreDeparture

        val waypoints = route.waypoints

        if (waypoints != null) {
            this.waypoints = waypoints
            if (ignoreDeparture && (this.waypoints.size > 1)) {
                this.waypoints.removeAt(0)
            }

            preferences = route.preferences
        } else {
            this.waypoints.clear()
        }
    }

    /**
     * @param ignoreDeparture will remove the first waypoint which previously had been considered as current location.
     */
    fun set(route: PTRoute, ignoreDeparture: Boolean = false) {
        this.ignoreDeparture = ignoreDeparture

        val waypoints = route.waypoints

        if (waypoints != null) {
            this.waypoints = waypoints
            if (ignoreDeparture && (this.waypoints.size > 1)) {
                this.waypoints.removeAt(0)
            }

            preferences = route.preferences
        } else {
            this.waypoints.clear()
        }
    }

    /**
     * Equals to method.
     */
    override fun equals(other: Any?): Boolean {
        val otherTrip = other as? TripModel ?: return false

        // check the transport mode
        if (preferences?.transportMode != otherTrip.preferences?.transportMode) {
            return false
        }

        val nCnt1 = waypoints.size
        val nCnt2 = otherTrip.waypoints.size

        if ((nCnt1 == 0) || (nCnt2 == 0) || (nCnt1 != nCnt2)) {
            return false
        }

        val equal = { x: Double, y: Double ->
            (abs(x - y) <= 1e-4)
        }

        var bEquals: Boolean
        for (i in 0 until nCnt1) {
            bEquals = equal(
                waypoints[i].coordinates!!.latitude,
                otherTrip.waypoints[i].coordinates!!.latitude
            ) && equal(
                waypoints[i].coordinates!!.longitude,
                otherTrip.waypoints[i].coordinates!!.longitude
            )

            if (!bEquals) {
                return false
            }
        }

        return true
    }

    fun clear() {
        preferences = null
        waypoints.clear()
        name = ""
        ignoreDeparture = false
    }

    override fun hashCode(): Int {
        var result = preferences?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + waypoints.hashCode()
        result = 31 * result + ignoreDeparture.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    companion object {
        fun getDefaultTripName(
            waypoints: LandmarkList,
            bIsFromAToB: Boolean
        ): String? {
            var departureName = ""
            var destinationName = ""

            val tripWptsCount = waypoints.size
            if (tripWptsCount == 0)
                return null

            // get departure and destination names
            if (bIsFromAToB)
                departureName = GemUtil.formatLandmarkDetails(waypoints[0])

            if (tripWptsCount > 0)
                destinationName = GemUtil.formatLandmarkDetails(waypoints[tripWptsCount - 1])

            // get all intermediate waypoints as one string ("%s, %s, %s...")
            val intermediateWptsName = getIntermediateWaypointsName(waypoints, bIsFromAToB).second

            // prepare default trip name based on the route type and number of waypoints
            val defaultName = if (bIsFromAToB) {
                if (tripWptsCount > 2) {
                    // use "From A to B, via C" string
                    String.format(
                        AppProcess.getAppResources().getString(R.string.from_a_to_b_via_c),
                        departureName,
                        destinationName,
                        intermediateWptsName
                    )
                } else {
                    // use "From A to B" string
                    String.format(
                        AppProcess.getAppResources().getString(R.string.from_a_to_b),
                        departureName,
                        destinationName
                    )
                }
            } else {
                if (tripWptsCount > 1) {
                    // use "To B, via C" string
                    String.format(
                        AppProcess.getAppResources().getString(R.string.to_b_via_c),
                        destinationName,
                        intermediateWptsName
                    )
                } else {
                    // use "To B" string
                    String.format(
                        AppProcess.getAppResources().getString(R.string.to_b),
                        destinationName
                    )
                }
            }

            return defaultName
        }

        private fun getIntermediateWaypointsName(
            waypoints: LandmarkList,
            isFromAToB: Boolean,
            pickShortNames: Boolean = false
        ): Pair<Boolean, String> {
            val tripWptsCount = waypoints.size

            if ((!isFromAToB && (tripWptsCount <= 1)) ||
                (isFromAToB && (tripWptsCount <= 2))
            ) {
                return Pair(false, "")
            }

            val startIndex = if (isFromAToB) 1 else 0
            val endIndex = tripWptsCount - 2

            var wptName = ""
            for (index in startIndex..endIndex) {
                var tmpName: String
                val waypoint = waypoints[index]
                tmpName = GemUtil.getFormattedWaypointName(waypoint)

                if (pickShortNames) {
                    val idx = tmpName.indexOf(";")
                    if (idx > 0) {
                        tmpName = tmpName.substring(0, idx)
                    }
                }

                if (tmpName.isNotEmpty()) {
                    if (wptName.isNotEmpty()) {
                        wptName += ", "
                    }
                    wptName += tmpName
                }
            }

            return Pair(true, wptName)
        }
    }
}

/**
 * Trips history manager.
 */
class TripsHistory {
    private companion object {
        private const val ROUTE_NAME = "Route"
    }

    private lateinit var routeBookmarks: RouteBookmarks

    fun init() {
        routeBookmarks = RouteBookmarks.produce("Trips")!!
    }

    fun isInitialized(): Boolean = this::routeBookmarks.isInitialized

    /**
     * List of trips already saved.
     */
    val trips: ArrayList<TripModel>
        get() {
            val list = ArrayList<TripModel>()

            for (i in 0 until tripsCount)
                loadTrip(i)?.let { list.add(it) }

            return list
        }

    /**
     * [TripModel] count contained by this.
     */
    val tripsCount: Int
        get() = routeBookmarks.size

    /**
     * Removes the [TripModel] at [index].
     */
    fun removeTrip(index: Int): Boolean {
        if (index in 0 until routeBookmarks.size) {
            routeBookmarks.remove(index)
            return true
        }

        return false
    }

    /**
     * Saves provided [TripModel].
     * @param trip Trip to be saved.
     */
    fun saveTrip(trip: TripModel) {
        val nTrips = routeBookmarks.size

        val filledIndexes = IntArray(nTrips) { Int.MAX_VALUE }

        var processed = 0

        for (i in 0 until nTrips) {
            val loaded = loadTrip(i)
            val bRouteExists = loaded != null
            val tmpRoute = loaded ?: TripModel()

            val bIsSameRoute: Boolean = if (trip.name.isEmpty()) {
                // new trip
                trip == tmpRoute
            } else {
                // existing trip
                trip.name.compareTo(tmpRoute.name, true) == 0
            }

            if (bIsSameRoute) {
                var routeName = tmpRoute.name
                if (trip.ignoreDeparture != tmpRoute.ignoreDeparture) {
                    val pos = routeName.indexOfFirst { it == ';' }
                    if (pos > 0) {
                        val subString = routeName.subSequence(0, pos)
                        val ignoreDeparture = (if (trip.ignoreDeparture) 1 else 0)

                        routeName = "$subString;$ignoreDeparture;"
                    }
                }

                routeBookmarks.update(i, routeName, trip.waypoints, tmpRoute.preferences)
                trip.name = routeName
                break
            }

            sortAscending(filledIndexes, nTrips, i, bRouteExists)
            processed++
        }

        if (processed == nTrips) {
            var nFirstFree = 1 // find the first available index
            for (i in 0 until nTrips) {
                if (filledIndexes[i] == nFirstFree) {
                    nFirstFree++
                }
            }

            val routeName = "$ROUTE_NAME$nFirstFree;${(if (trip.ignoreDeparture) 1 else 0)};"

            routeBookmarks.add(routeName, trip.waypoints, trip.preferences)

            // update trip's name
            trip.name = routeName
        }
    }

    /**
     * Loads [TripModel] data by [index].
     */
    fun loadTrip(index: Int): TripModel? {
        val lmkList = routeBookmarks.getWaypoints(index) ?: return null
        val preferences = routeBookmarks.getPreferences(index) ?: return null

        val trip = TripModel()
        trip.waypoints = lmkList
        trip.preferences = preferences
        trip.timestamp = routeBookmarks.getTimestamp(index)?.asLong() ?: 0L

        var fromAToB = false
        val name = routeBookmarks.getName(index)

        if (!name.isNullOrEmpty()) {
            val tokens = name.split(';')

            fromAToB = if (tokens.size > 1) {
                tokens[1].toInt() > 0
            } else {
                (lmkList.size > 1)
            }

            trip.name = name
        }

        // set trip format
        trip.ignoreDeparture = fromAToB

        return trip
    }

    /**
     * Updates stored [TripModel] data by [index].
     * @param index Index.
     * @param tripName New name.
     * @param waypoints New trip waypoints.
     * @param isFromAToB Ignored departure waypoint.
     */
    fun updateTrip(
        index: Int,
        tripName: String,
        waypoints: LandmarkList,
        isFromAToB: Boolean
    ): Int {
        if ((index < 0) || (index >= routeBookmarks.size) || waypoints.isEmpty()) {
            return GemError.InvalidInput
        }

        val result = encodeTripName(index, tripName, isFromAToB)
        if (result.first != GemError.NoError) {
            return result.first
        }

        val encodedTripName = result.second
        if (encodedTripName.isNullOrEmpty())
            return GemError.General

        routeBookmarks.update(index, encodedTripName, waypoints)

        return GemError.NoError
    }

    // --------------------------------------------------

    private fun sortAscending(nNames: IntArray, nTrips: Int, i: Int, bRouteExists: Boolean) {
        var nTmp = 0

        val routeName = routeBookmarks.getName(i)

        if (bRouteExists && !routeName.isNullOrEmpty()) {
            val tokens = routeName.split(";")

            if (tokens.isNotEmpty()) {
                val strName = tokens[0].replace(ROUTE_NAME, "")
                nTmp = strName.toInt()
            }
        }

        if (nTmp > 0) { // sort ascending
            for (j in 0 until nTrips) {
                if (nTmp <= nNames[j]) {
                    for (k in nTrips - 1 downTo (j + 1))
                        nNames[k] = nNames[k - 1]
                    nNames[j] = nTmp
                    break
                }
            }
        }
    }

    private fun encodeTripName(
        index: Int,
        tripName: String,
        isFromAToB: Boolean
    ): Pair<Int, String?> {
        if (tripName.isNotEmpty()) {
            val nTrips = routeBookmarks.size

            for (i in 0 until nTrips) {
                if (i == index) { // skip current route
                    continue
                }

                // check trip name
                val name = getTripName(i) ?: continue
                if (name.compareTo(tripName, ignoreCase = true) == 0) {
                    return Pair(GemError.Exist, null)
                }
            }
        }

        val trip = loadTrip(index) ?: return Pair(GemError.InvalidInput, null)

        val name = trip.name
        val tokens = name.split(";")

        var aliasHexa = ""
        if (tripName.isNotEmpty()) {
            aliasHexa = Util.stringToHexa(tripName)
        }

        if (tokens.isNotEmpty()) {
            val encodedTripName = String.format("%s;%d;%s;", tokens[0], isFromAToB, aliasHexa)
            return Pair(GemError.NoError, encodedTripName)
        }

        return Pair(GemError.NotSupported, null)
    }

    private fun getTripName(index: Int): String? {
        val routeAlias = getRouteAlias(index)
        if (routeAlias?.isNotEmpty() == true)
            return routeAlias

        val trip = loadTrip(index) ?: return null
        return TripModel.getDefaultTripName(trip.waypoints, trip.ignoreDeparture)
    }

    private fun getRouteAlias(index: Int, returnDecodedValue: Boolean = true): String? {
        if (index !in 0 until routeBookmarks.size)
            return null

        val route = loadTrip(index) ?: return null
        val tokens = route.name.split(";")

        for (token in tokens) {
            if (token.contains("0x") || token.contains("0X")) {
                if (returnDecodedValue) {
                    return Util.hexaToString(token)
                }

                return token
            }
        }

        return null
    }
}
