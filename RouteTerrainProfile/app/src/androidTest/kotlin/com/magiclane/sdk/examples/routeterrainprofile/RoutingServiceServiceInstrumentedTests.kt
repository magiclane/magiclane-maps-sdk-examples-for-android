/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routeterrainprofile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RouteList
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RoutingServiceServiceInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun routingServiceShouldReturnRoutes(): Unit = runBlocking {
        var onCompletedPassed = false
        var error: ErrorCode
        val channel = Channel<Unit>()
        var routeList: RouteList? = null
        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                error = errorCode
                onCompletedPassed = true
                routeList = routes
                launch { channel.send(Unit) }
            },
        )

        error = async {
            SdkCall.execute {
                val waypoints = ArrayList<Landmark>()
                waypoints.add(Landmark("Brasov", 45.6427, 25.5887))
                waypoints.add(Landmark("Bucharest", 44.4268, 26.1025))
                routingService.calculateRoute(waypoints = waypoints)
            }
        }.await() as ErrorCode

        assert(error == GemError.NoError) { GemError.getMessage(error) }

        withTimeout(12000) {
            channel.receive()
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(routeList?.isNotEmpty() == true) { "Routing service returned no results." }
        }
    }
}
