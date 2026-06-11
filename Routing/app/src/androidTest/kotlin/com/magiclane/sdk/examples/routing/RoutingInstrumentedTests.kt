/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routing

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

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RoutingInstrumentedTests {
    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun routingServiceShouldReturnRoutes(): Unit = runBlocking {
        var onCompletedPassed = false
        var error = GemError.NoError
        val channel = Channel<Unit>(Channel.RENDEZVOUS)
        var routeList: RouteList? = null
        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                error = errorCode
                onCompletedPassed = true
                SdkCall.execute {
                    routeList = routes
                    launch { channel.send(Unit) }
                }
            },
        )

        SdkCall.execute {
            val waypoints = arrayListOf(
                Landmark("Frankfurt am Main", 50.11428, 8.68133),
                Landmark("Karlsruhe", 49.0069, 8.4037),
                Landmark("Munich", 48.1351, 11.5820),
            )
            error = routingService.calculateRoute(waypoints = waypoints)
        }
        withTimeout(12000) {
            channel.receive()
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(routeList?.isNotEmpty() == true) { "Routing service returned no results." }
        }
    }
}
