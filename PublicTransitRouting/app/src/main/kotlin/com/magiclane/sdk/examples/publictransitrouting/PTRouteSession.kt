/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.content.Context
import com.magiclane.sdk.routesandnavigation.ETransitType
import com.magiclane.sdk.routesandnavigation.Route

/**
 * One segment of a public-transit route, pre-formatted for the UI thread
 * (all SDK values are resolved on the SDK thread when the item is built).
 */
class PTSegmentItem(
    val transitType: ETransitType,
    val isWalk: Boolean,
    val shortName: String,
    val lineColor: Int,
    val lineTextColor: Int,
    // Walk travel time split into value ("14") and unit ("min"), stacked in the segment strip.
    val travelTimeValueText: String,
    val travelTimeUnitText: String,
    val isSignificant: Boolean,
)

/** One public-transit route, pre-formatted for the UI thread. */
class PTRouteItem(
    val routeIndex: Int,
    val timeIntervalText: String,
    val durationText: String,
    val transfersText: String,
    val walkingInfoText: String,
    val fareText: String,
    val frequencyText: String,
    val warningText: String,
    val departureMillis: Long,
    val arrivalMillis: Long,
    val durationSeconds: Int,
    val segments: List<PTSegmentItem>,
) {
    companion object {
        /** Builds the UI model of [route]; must be called on the SDK thread. */
        fun build(context: Context, routeIndex: Int, route: Route): PTRouteItem {
            val segments = route.segments ?: arrayListOf()

            var walkDistance = 0
            var walkTime = 0
            var transitCount = 0
            val segmentItems = segments.map { segment ->
                val isWalk = !segment.isCommon()
                val ptSegment = segment.toPTRouteSegment()
                val timeDistance = segment.timeDistance

                if (isWalk) {
                    walkDistance += timeDistance?.totalDistance ?: 0
                    walkTime += timeDistance?.totalTime ?: 0
                } else {
                    ++transitCount
                }

                val walkTimeSeconds = if (isWalk) timeDistance?.totalTime ?: 0 else 0
                PTSegmentItem(
                    transitType = ptSegment?.transitType ?: ETransitType.Unknown,
                    isWalk = isWalk,
                    shortName = ptSegment?.shortName.orEmpty(),
                    lineColor = Formatters.colorOf(ptSegment?.lineColor),
                    lineTextColor = Formatters.colorOf(ptSegment?.lineTextColor),
                    travelTimeValueText = if (walkTimeSeconds > 0) {
                        Formatters.minutesValue(walkTimeSeconds).toString()
                    } else {
                        ""
                    },
                    travelTimeUnitText = if (walkTimeSeconds > 0) {
                        context.getString(R.string.minutes_unit)
                    } else {
                        ""
                    },
                    isSignificant = ptSegment?.isSignificant() ?: true,
                )
            }

            val firstPtSegment = segments.firstOrNull()?.toPTRouteSegment()
            val lastPtSegment = segments.lastOrNull()?.toPTRouteSegment()
            val departureTime = firstPtSegment?.departureTime
            val arrivalTime = lastPtSegment?.arrivalTime

            val ptRoute = if (route.isPTRoute()) route.toPTRoute() else null
            val frequencySeconds = ptRoute?.ptFrequency ?: 0

            return PTRouteItem(
                routeIndex = routeIndex,
                timeIntervalText = context.getString(
                    R.string.time_interval,
                    Formatters.timeText(departureTime),
                    Formatters.timeText(arrivalTime),
                ),
                durationText = Formatters.durationText(context, route.timeDistance?.totalTime ?: 0),
                transfersText = if (transitCount > 1) {
                    context.resources.getQuantityString(R.plurals.transfers, transitCount - 1, transitCount - 1)
                } else {
                    ""
                },
                walkingInfoText = if (walkDistance > 0) {
                    context.getString(
                        R.string.walking_info,
                        Formatters.distanceText(context, walkDistance),
                        Formatters.durationText(context, walkTime),
                    )
                } else {
                    ""
                },
                fareText = ptRoute?.ptFare.orEmpty(),
                frequencyText = if (frequencySeconds > 0) {
                    context.getString(R.string.every_x, Formatters.durationText(context, frequencySeconds))
                } else {
                    ""
                },
                warningText = if (ptRoute?.ptRespectsAllConditions() == false) {
                    context.getString(R.string.not_all_preferences_met)
                } else {
                    ""
                },
                departureMillis = departureTime?.asLong() ?: 0L,
                arrivalMillis = arrivalTime?.asLong() ?: 0L,
                durationSeconds = route.timeDistance?.totalTime ?: 0,
                segments = segmentItems,
            )
        }
    }
}

/**
 * Shared state of the calculated public-transit routes, used by the map screen (which owns the
 * routing service and the map) and by the routes / settings / description screens.
 */
object PTRouteSession {

    /** Actions implemented by the map screen. */
    interface Controller {
        /** Makes [routeIndex] the displayed route and centers the map on it. */
        fun selectRoute(routeIndex: Int)

        /** Flies the map to the given segment of [routeIndex]. */
        fun flyToSegment(routeIndex: Int, segmentIndex: Int)

        /** Recalculates the routes with the current [PTSettings]. */
        fun recalculate()

        /** Requests routes arriving before the earliest current arrival (Earlier). */
        fun requestEarlier()

        /** Requests routes departing after the latest current departure (Later). */
        fun requestLater()
    }

    /** Route-calculation lifecycle, observed by the routes screen; called on the main thread. */
    interface CalculationListener {
        fun onCalculationStarted()
        fun onCalculationCompleted(errorCode: Int)
    }

    var controller: Controller? = null

    /** SDK routes of the last successful calculation; touch only on the SDK thread. */
    var routes: ArrayList<Route> = arrayListOf()

    /** Pre-formatted UI models matching [routes] by index. */
    var items: List<PTRouteItem> = emptyList()

    var selectedRouteIndex = 0

    var isCalculating = false
        private set

    private val listeners = mutableSetOf<CalculationListener>()

    fun addListener(listener: CalculationListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CalculationListener) {
        listeners.remove(listener)
    }

    fun notifyCalculationStarted() {
        isCalculating = true
        listeners.toList().forEach { it.onCalculationStarted() }
    }

    fun notifyCalculationCompleted(errorCode: Int) {
        isCalculating = false
        listeners.toList().forEach { it.onCalculationCompleted(errorCode) }
    }

    fun clear() {
        controller = null
        routes = arrayListOf()
        items = emptyList()
        selectedRouteIndex = 0
        isCalculating = false
        listeners.clear()
    }
}
