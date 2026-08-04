/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routingonmap

import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RouteList
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SDK-level tests for the routing feature demonstrated by this example.
 *
 * These exercise [RoutingService] directly (no UI), mirroring the London -> Paris route the
 * example calculates in [MainActivity]. UI behaviour is covered by [UIRoutingOnMapInstrumentedTests].
 */
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RoutingOnMapInstrumentedTest {
    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()

        private const val ROUTE_TIMEOUT_MS = 30_000L

        // The same waypoints the example routes between.
        private fun londonToParisWaypoints() = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )
    }

    /** Calculates a route and returns (returnCode, completionErrorCode, routes). */
    private fun calculateRouteBlocking(
        waypoints: ArrayList<Landmark>,
        timeoutMs: Long = ROUTE_TIMEOUT_MS,
    ): Triple<Int, Int, RouteList> = runBlocking {
        var completionError = GemError.NoError
        var completedRoutes = RouteList()
        val channel = Channel<Unit>(Channel.RENDEZVOUS)

        val service = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                completionError = errorCode
                completedRoutes = routes
                launch { channel.send(Unit) }
            },
        )

        val returnCode = SdkCall.execute {
            service.calculateRoute(waypoints)
        } ?: GemError.General

        // onCompleted only fires when the calculation was successfully started.
        if (returnCode == GemError.NoError) {
            withTimeout(timeoutMs) { channel.receive() }
        }

        Triple(returnCode, completionError, completedRoutes)
    }

    @Test
    fun londonToParisRouteCalculationSucceeds() {
        val (returnCode, completionError, routes) = calculateRouteBlocking(londonToParisWaypoints())

        assert(returnCode == GemError.NoError) {
            "calculateRoute did not start: ${GemError.getMessage(returnCode)}"
        }
        assert(completionError == GemError.NoError) {
            "Routing completed with error: ${GemError.getMessage(completionError)}"
        }
        assert(routes.isNotEmpty()) { "Routing service returned no routes." }
    }

    @Test
    fun calculatedRouteHasWaypointsAndPositiveMetrics() {
        val (_, completionError, routes) = calculateRouteBlocking(londonToParisWaypoints())
        assert(completionError == GemError.NoError) {
            "Routing completed with error: ${GemError.getMessage(completionError)}"
        }
        assert(routes.isNotEmpty()) { "Routing service returned no routes." }

        SdkCall.execute {
            val mainRoute = routes.first()

            val waypointCount = mainRoute.waypoints?.size ?: 0
            assert(waypointCount >= 2) {
                "Expected at least departure and destination waypoints, got $waypointCount."
            }

            val timeDistance = mainRoute.timeDistance
            assert(timeDistance != null) { "Route has no time/distance information." }
            assert((timeDistance?.totalDistance ?: 0) > 0) { "Route distance should be positive." }
            assert((timeDistance?.totalTime ?: 0) > 0) { "Route travel time should be positive." }
        }
    }

    @Test
    fun emptyWaypointListDoesNotProduceRoutes() {
        val (returnCode, _, routes) = calculateRouteBlocking(ArrayList(), timeoutMs = 5_000L)

        assert(returnCode != GemError.NoError) {
            "Calculating a route with no waypoints should not start successfully."
        }
        assert(routes.isEmpty()) { "No routes should be produced for an empty waypoint list." }
    }

    @Test
    fun singleWaypointDoesNotProduceRoutes() {
        val waypoints = arrayListOf(Landmark("London", 51.5073204, -0.1276475))
        val (returnCode, _, routes) = calculateRouteBlocking(waypoints, timeoutMs = 5_000L)

        assert(returnCode != GemError.NoError) {
            "Calculating a route with a single waypoint should not start successfully."
        }
        assert(routes.isEmpty()) { "No routes should be produced for a single waypoint." }
    }
}
