/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.history

import com.generalmagic.sdk.examples.demo.util.UtilUITexts
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteBookmarks
import com.generalmagic.sdk.routesandnavigation.RoutePreferences
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.SdkUtil.getUIString
import java.util.*
import kotlin.math.abs

class Trip {
    var mPreferences: RoutePreferences? = null
    var mWaypoints: ArrayList<Landmark>? = arrayListOf()
    var mName: String = ""
    var mIsFromAToB = false
    var mTimeStamp = 0L

    fun set(route: Route?, isFromAToB: Boolean) {
        mIsFromAToB = isFromAToB

        route.let {
            mWaypoints = it?.getWaypoints()
            val preferences = it?.preferences()
            val waypointsSize = mWaypoints?.size ?: 0
            if (!mIsFromAToB && waypointsSize > 1) {
                mWaypoints?.remove(mWaypoints?.first())
            }

            preferences.let {
                mPreferences = preferences
            }
        }
    }

    fun clear() {
        mPreferences = RoutePreferences()
        mWaypoints?.clear()
        mName = String()
        mIsFromAToB = false
    }
}

class TripsHistory {
    private var mRouteBookmarks = RouteBookmarks.produce("Trips")

    fun getTripsCount(): Int {
        return mRouteBookmarks?.size() ?: 0
    }

    fun saveTrip(trip: Trip) {
        mRouteBookmarks.let { routeBookmarks ->
            val nTrips = routeBookmarks?.size() ?: 0
            var tmpRoute: Trip?
            var filledIndexes = IntArray(nTrips) { Int.MAX_VALUE }

            var bRouteExists: Boolean
            var i = 0

            for (index in 0 until nTrips) {
                val result = loadTrip(index)
                bRouteExists = result.first
                tmpRoute = result.second

                if (tmpRoute == null) break

                val isTheSameRoute: Boolean = if (trip.mName.isEmpty()) {
                    isSameTrip(trip, tmpRoute)
                } else {
                    trip.mName.equals(tmpRoute.mName, ignoreCase = true)
                }

                if (isTheSameRoute) {
                    val tripWaypoints = tmpRoute.mWaypoints ?: arrayListOf()
                    val tripPreferences = tmpRoute.mPreferences ?: RoutePreferences()
                    routeBookmarks?.update(
                        index,
                        tmpRoute.mName,
                        tripWaypoints,
                        tripPreferences
                    )
                    trip.mName = tmpRoute.mName
                    break
                }

                filledIndexes = sortAscending(filledIndexes, nTrips, index, bRouteExists)

                i = index + 1
            }

            if (i == nTrips) {
                var nFirstFree = 1
                for (index in 0 until nTrips) {
                    if (filledIndexes[index] == nFirstFree) {
                        nFirstFree++
                    }
                }

                val routeName = String.format("%s%d;%b;", ROUTE_NAME, nFirstFree, trip.mIsFromAToB)

                val tripWaypoints = trip.mWaypoints ?: arrayListOf()
                val tripPreferences = trip.mPreferences ?: RoutePreferences()
                routeBookmarks?.add(routeName, tripWaypoints, tripPreferences)

                trip.mName = routeName
            }
        }
    }

    fun loadTrip(index: Int): Pair<Boolean, Trip?> {
        mRouteBookmarks.let { routeBookmarks ->
            val trip = Trip()
            val lmkList = routeBookmarks?.getWaypoints(index)
            val preferences = routeBookmarks?.getPreferences(index)
            val bRouteExists = (lmkList != null) && (preferences != null)

            if (!bRouteExists) {
                return Pair(false, null)
            }

            preferences.let {
                trip.mPreferences = preferences
            }

            trip.mWaypoints = lmkList
            trip.mTimeStamp = routeBookmarks?.getTimestamp(index)?.asInt() ?: 0L

            var fromAToB = false
            val name = routeBookmarks?.getName(index)

            if (!name.isNullOrEmpty()) {
                val tokens = name.split(";")

                fromAToB = if (tokens.size > 1) {
                    tokens[1].toBoolean()
                } else {
                    val lmkSize = lmkList?.size ?: 0
                    lmkSize > 1
                }

                trip.mName = name
            }

            trip.mIsFromAToB = fromAToB

            return Pair(true, trip)

        }
    }

    fun removeTrip(index: Int): Boolean {
        mRouteBookmarks.let { routeBookmarks ->
            val nTrips = routeBookmarks?.size() ?: 0
            if (index in 0 until nTrips) {
                routeBookmarks?.remove(index)
                return true
            }
        }

        return false
    }

    private fun isSameTrip(trip1: Trip?, trip2: Trip?): Boolean {
        if (trip1 == null || trip2 == null) {
            return false
        }

        if (trip1.mPreferences?.getTransportMode() != trip1.mPreferences?.getTransportMode()) {
            return false
        }

        val nCnt1 = trip1.mWaypoints?.size ?: 0
        val nCnt2 = trip2.mWaypoints?.size ?: 0

        if (nCnt1 == 0 || nCnt2 == 0 || nCnt1 != nCnt2) {
            return false
        }

        val equal = { x: Double, y: Double ->
            abs(x - y) <= 1e-4
        }

        var bEquals: Boolean
        for (index in 0 until nCnt1) {
            val trip1Lat =
                trip1.mWaypoints?.elementAt(index)?.getCoordinates()?.getLatitude() ?: 0.0
            val trip1Lon =
                trip1.mWaypoints?.elementAt(index)?.getCoordinates()?.getLongitude() ?: 0.0
            val trip2Lat =
                trip2.mWaypoints?.elementAt(index)?.getCoordinates()?.getLatitude() ?: 0.0
            val trip2Lon =
                trip2.mWaypoints?.elementAt(index)?.getCoordinates()?.getLongitude() ?: 0.0
            bEquals = equal(trip1Lat, trip2Lat) && equal(trip1Lon, trip2Lon)

            if (!bEquals) {
                return false
            }
        }

        return true
    }

    private fun sortAscending(
        nNames: IntArray,
        nTrips: Int,
        index: Int,
        bRouteExists: Boolean
    ): IntArray {
        var nTmp = 0
        mRouteBookmarks.let { routeBookmarks ->
            val routeName = routeBookmarks?.getName(index)

            if (bRouteExists && !routeName.isNullOrEmpty()) {
                val tokens = routeName.split(";")

                if (tokens.isNotEmpty()) {
                    var strName = tokens[0]
                    strName = strName.replace(ROUTE_NAME, "")
                    nTmp = strName.toInt()
                }
            }

            if (nTmp > 0) {
                for (j in 0 until nTrips) {
                    if (nTmp <= nNames[j]) {
                        for (k in nTrips - 1 downTo j + 1) {
                            nNames[k] = nNames[k - 1]
                        }
                        nNames[j] = nTmp
                        break
                    }
                }
            }
        }

        return nNames
    }

    companion object {
        fun getDefaultTripName(
            waypoints: ArrayList<Landmark>,
            isFromAToB: Boolean,
            isToCurrentLocation: Boolean
        ): Pair<Boolean, String> {
            val defaultName: String
            var departureName = ""
            var destinationName = ""

            val tripWptsCount = waypoints.size
            if (tripWptsCount == 0) {
                return Pair(false, "")
            }

            if (isFromAToB) {
                departureName = UtilUITexts.formatLandmarkDetails(waypoints[0])
            }

            if (tripWptsCount > 0) {
                destinationName = UtilUITexts.formatLandmarkDetails(waypoints[tripWptsCount - 1])
            }

            //getIntermediateWaypointsName(waypoints, bIsFromAToB, intermediateWptsName);
            val intermediateWptsName = getIntermediateWaypointsName(waypoints, isFromAToB).second

            if (isFromAToB) {
                defaultName = if (tripWptsCount > 2) {
                    String.format(
                        getUIString(StringIds.eStrFromAToBViaC),
                        departureName,
                        destinationName,
                        intermediateWptsName
                    )
                } else {
                    String.format(
                        getUIString(StringIds.eStrFromAtoB),
                        departureName,
                        destinationName
                    )
                }
            } else {
                defaultName = if (tripWptsCount > 1) {
                    if (isToCurrentLocation) {
                        destinationName = getUIString(StringIds.eStrMyPosition)
                    }

                    String.format(
                        getUIString(StringIds.eStrToBViaC),
                        destinationName,
                        intermediateWptsName
                    )
                } else {
                    if (isToCurrentLocation) {
                        destinationName = getUIString(StringIds.eStrMyPosition)
                    }

                    String.format(
                        getUIString(StringIds.eStrToB),
                        destinationName
                    )
                }
            }

            return Pair(true, defaultName)
        }

        private fun getIntermediateWaypointsName(
            waypoints: ArrayList<Landmark>,
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
                tmpName = Utils.getFormattedWaypointName(waypoint)

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

        const val ROUTE_NAME = "Route"
    }
}
