/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation

import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.androidautoroutenavigation.services.RoutingInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.services.SearchInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.services.SettingsInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.util.Util
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.util.SdkCall
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SDK-level tests for the services this example builds on top of the GEM SDK.
 *
 * These exercise the example's own singletons directly (no UI, no Android Auto host):
 * [RoutingInstance] (route calculation + persisted routing preferences), [SearchInstance]
 * (free-text search used by the car search screen) and the geo-URI helpers in [Util] that back
 * the `geo:` intent filter. UI behaviour is covered by
 * [UIAndroidAutoRouteNavigationInstrumentedTests].
 */
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class AndroidAutoRouteNavigationInstrumentedTest {
    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()

        private const val ROUTE_TIMEOUT_MS = 30_000L
        private const val SEARCH_TIMEOUT_MS = 30_000L

        private fun londonToParisWaypoints(): LandmarkList = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )
    }

    @Before
    fun initExampleServices() {
        // Mirrors the service wiring AppProcess.init() performs after SDK init. The SDK itself is
        // initialized by the class rule; all inits below are idempotent.
        SdkCall.execute {
            SettingsInstance.init()
            RoutingInstance.init()
            SearchInstance.init()
        }
    }

    /**
     * Calculates a route through [RoutingInstance]'s service (whose onStarted/onCompleted wiring
     * fills [RoutingInstance.results] and notifies listeners) and returns
     * (returnCode, completionErrorCode).
     */
    private fun calculateRouteBlocking(waypoints: LandmarkList, timeoutMs: Long = ROUTE_TIMEOUT_MS): Pair<Int, Int> =
        runBlocking {
            var completionError = GemError.NoError
            val channel = Channel<Unit>(Channel.RENDEZVOUS)

            val listener = ProgressListener.create(
                onCompleted = { errorCode, _ ->
                    completionError = errorCode
                    launch { channel.send(Unit) }
                },
            )
            RoutingInstance.listeners.add(listener)

            try {
                // Stale results from a previous test would otherwise survive a calculation that never
                // starts (results are only cleared by the service's onStarted callback).
                val returnCode = SdkCall.execute {
                    RoutingInstance.results.clear()
                    RoutingInstance.service.calculateRoute(waypoints)
                } ?: GemError.General

                // onCompleted only fires when the calculation was successfully started.
                if (returnCode == GemError.NoError) {
                    withTimeout(timeoutMs) { channel.receive() }
                }

                Pair(returnCode, completionError)
            } finally {
                RoutingInstance.listeners.remove(listener)
            }
        }

    @Test
    fun londonToParisRouteCalculationSucceeds() {
        val (returnCode, completionError) = calculateRouteBlocking(londonToParisWaypoints())

        assert(returnCode == GemError.NoError) {
            "calculateRoute did not start: ${GemError.getMessage(returnCode)}"
        }
        assert(completionError == GemError.NoError) {
            "Routing completed with error: ${GemError.getMessage(completionError)}"
        }
        assert(RoutingInstance.results.isNotEmpty()) { "Routing service returned no routes." }
    }

    @Test
    fun calculatedRouteHasWaypointsAndPositiveMetrics() {
        val (_, completionError) = calculateRouteBlocking(londonToParisWaypoints())
        assert(completionError == GemError.NoError) {
            "Routing completed with error: ${GemError.getMessage(completionError)}"
        }
        assert(RoutingInstance.results.isNotEmpty()) { "Routing service returned no routes." }

        SdkCall.execute {
            val mainRoute = RoutingInstance.results.first()

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
        val (returnCode, _) = calculateRouteBlocking(arrayListOf(), timeoutMs = 5_000L)

        assert(returnCode != GemError.NoError) {
            "Calculating a route with no waypoints should not start successfully."
        }
        assert(RoutingInstance.results.isEmpty()) {
            "No routes should be produced for an empty waypoint list."
        }
    }

    @Test
    fun singleWaypointDoesNotProduceRoutes() {
        val waypoints: LandmarkList = arrayListOf(Landmark("London", 51.5073204, -0.1276475))
        val (returnCode, _) = calculateRouteBlocking(waypoints, timeoutMs = 5_000L)

        assert(returnCode != GemError.NoError) {
            "Calculating a route with a single waypoint should not start successfully."
        }
        assert(RoutingInstance.results.isEmpty()) {
            "No routes should be produced for a single waypoint."
        }
    }

    @Test
    fun searchByFilterAroundParisReturnsResults(): Unit = runBlocking {
        var completionError = GemError.NoError
        val results: LandmarkList = arrayListOf()
        val channel = Channel<Unit>(Channel.RENDEZVOUS)

        // Same call shape as the car app's search screen (SearchTextController.doSearch).
        SdkCall.execute {
            SearchInstance.service.cancelSearch()
            SearchInstance.service.onCompleted = { found, errorCode, _ ->
                completionError = errorCode
                results.addAll(found)
                launch { channel.send(Unit) }
            }
            SearchInstance.service.searchByFilter("Eiffel Tower", Coordinates(48.8566932, 2.3514616))
        }

        try {
            withTimeout(SEARCH_TIMEOUT_MS) { channel.receive() }
        } finally {
            SdkCall.execute {
                SearchInstance.service.onCompleted = null
                SearchInstance.service.cancelSearch()
            }
        }

        assert(completionError == GemError.NoError) {
            "Search completed with error: ${GemError.getMessage(completionError)}"
        }
        assert(results.isNotEmpty()) { "Search returned no results for a well-known landmark." }
        SdkCall.execute {
            assert(results.first().coordinates?.valid() == true) {
                "Search result has no valid coordinates."
            }
        }
    }

    @Test
    fun geoUriHelpersParseCoordinatesAndParameters() {
        // The helpers below back the geo: intent filter handled by AppProcess.handleGeoUri.
        assert(Util.isGeoIntent("geo:48.8566,2.3514")) { "geo: URI should be detected." }
        assert(!Util.isGeoIntent("https://magiclane.com")) { "Plain URL is not a geo intent." }

        val coordinates = Util.parseCoordinates("48.8566,2.3514")
        assert(coordinates != null) { "Valid \"lat,lon\" text should parse." }
        assert(abs((coordinates?.latitude ?: 0.0) - 48.8566) < 1e-6) { "Latitude was not parsed correctly." }
        assert(abs((coordinates?.longitude ?: 0.0) - 2.3514) < 1e-6) { "Longitude was not parsed correctly." }

        assert(Util.parseCoordinates("not,coordinates") == null) { "Non-numeric text must not parse." }
        assert(Util.parseCoordinates("48.8566") == null) { "A single value is not a coordinate pair." }

        val parameters = Util.getParameters("q=Eiffel+Tower&z=17")
        assert(parameters.size == 2) { "Expected two query parameters, got ${parameters.size}." }
        assert(parameters[0] == Pair("q", "Eiffel+Tower")) { "First parameter was ${parameters[0]}." }
        assert(parameters[1] == Pair("z", "17")) { "Second parameter was ${parameters[1]}." }
    }

    @Test
    fun routingPreferencesArePersistedAndReloaded() {
        SdkCall.execute {
            val original = RoutingInstance.avoidTollRoads
            try {
                // The setter must persist the value; loadSettings must restore it after the
                // in-memory preference is overwritten (what happens on a fresh app start).
                RoutingInstance.avoidTollRoads = !original
                RoutingInstance.service.preferences.avoidTollRoads = original
                RoutingInstance.loadSettings()

                assert(RoutingInstance.service.preferences.avoidTollRoads == !original) {
                    "avoidTollRoads was not restored from persisted settings."
                }
            } finally {
                RoutingInstance.avoidTollRoads = original
            }
        }
    }
}
