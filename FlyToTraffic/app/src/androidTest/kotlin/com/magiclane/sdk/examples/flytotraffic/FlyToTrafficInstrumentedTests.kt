/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.flytotraffic

import android.util.Log
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class FlyToTrafficInstrumentedTests {
    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun getTrafficEvent(): Unit = runBlocking {
        val channel = Channel<Unit>()
        var error = GemError.General

        val routingService = RoutingService(

            onCompleted = onCompleted@{ routes, gemError, _ ->

                error = gemError

                if (routes.size == 0) return@onCompleted

                val route = routes[0]

                // Get Traffic events from the main route.
                runBlocking {
                    val events = async { SdkCall.execute { route.trafficEvents } }.await()
                    // Traffic events depend on real-time conditions — an empty list
                    // is valid when there is no traffic on the route at this time.
                    if (events.isNullOrEmpty()) {
                        Log.w("FlyToTrafficTest", "No traffic events on route (expected when there is no traffic).")
                    }
                    channel.send(Unit)
                }
            },
        )

        SdkCall.execute {
            val waypoints = arrayListOf(
                Landmark("Paris", 48.8566932, 2.3514616),
                Landmark("Bucharest", 44.4268, 26.1025),
            )
            routingService.calculateRoute(waypoints)
        }

        withTimeout(120000) {
            channel.receive()
            assert(!GemError.isError(error)) { GemError.getMessage(error) }
        }
    }
}
