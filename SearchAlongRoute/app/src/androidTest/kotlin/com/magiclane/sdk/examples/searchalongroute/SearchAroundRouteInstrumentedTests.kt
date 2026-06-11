/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchalongroute

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchAroundRouteInstrumentedTests {

    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun routingServiceShouldReturnRoutesList(): Unit = runBlocking {
        var onCompletedPassed = false
        val channel = Channel<Unit>(Channel.RENDEZVOUS)
        var error: ErrorCode
        var routesList: ArrayList<Route>? = null
        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                onCompletedPassed = true
                error = errorCode
                routesList = routes
                launch { channel.send(Unit) }
            },
        )

        error = async {
            SdkCall.execute {
                val waypoints = arrayListOf(
                    Landmark("Folkestone", 51.0814, 1.1695),
                    Landmark("Paris", 48.8566932, 2.3514616),
                )
                routingService.calculateRoute(waypoints)
            }
        }.await() as ErrorCode

        withTimeout(600000) {
            channel.receive()
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(routesList != null)
        }
    }

    @Test
    fun searchServiceSearchAlongRouteShouldReturnListOfNearbyGasStationsOnFirstFoundRoute(): Unit = runBlocking {
        var onCompletedPassed = false
        var error = GemError.NoError
        var routesList: ArrayList<Route>? = null
        var landmarkList: LandmarkList? = null
        val channel = Channel<Unit>()
        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                routesList = routes
                error = errorCode
                launch { channel.send(Unit) }
            },
        )
        val searchService = SearchService(
            onCompleted = { results, errorCode, _ ->
                onCompletedPassed = true
                error = errorCode
                landmarkList = results
                launch { channel.send(Unit) }
            },
        )

        val job1 = launch {
            SdkCall.execute {
                val waypoints = arrayListOf(
                    Landmark("Folkestone", 51.0814, 1.1695),
                    Landmark("Paris", 48.8566932, 2.3514616),
                )
                error = routingService.calculateRoute(waypoints)
            }
            channel.receive()
        }

        withTimeout(12000) {
            withTimeout(12000) {
                while (job1.isActive) delay(500)
            }
            SdkCall.execute {
                // Set the maximum number of results to 25.
                searchService.preferences.maxMatches = 25

                // Search Gas Stations along the route.
                routesList?.let {
                    searchService.searchAlongRoute(it[0], EGenericCategoriesIDs.GasStation)
                }
            }
            channel.receive()
            assert(onCompletedPassed) {
                "OnCompleted not passed : ${
                    GemError.getMessage(
                        error,
                    )
                }"
            }
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(!routesList.isNullOrEmpty()) { "Route lists were empty" }
            assert(!landmarkList.isNullOrEmpty()) { "Search around route " }
        }
    }
}
