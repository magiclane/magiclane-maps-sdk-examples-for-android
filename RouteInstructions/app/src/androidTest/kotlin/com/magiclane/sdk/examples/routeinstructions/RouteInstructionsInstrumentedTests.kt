/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routeinstructions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RouteInstruction
import com.magiclane.sdk.routesandnavigation.RouteList
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GemCall.lock
import com.magiclane.sdk.util.SdkCall
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RouteInstructionsInstrumentedTests {

    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun routingServiceShouldReturnRoutesWithInstructions() {
        var onCompletedPassed = false
        var error = GemError.NoError
        val objSync = Object()
        var routeList: RouteList? = null
        var routeInstructions: ArrayList<RouteInstruction>? = null
        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                error = errorCode
                onCompletedPassed = true
                SdkCall.execute {
                    routeList = routes
                    if (routes.size > 0) {
                        routeInstructions = routes[0].instructions
                    }

                    notify(objSync)
                }
            },
        )

        SdkCall.execute {
            val waypoints = ArrayList<Landmark>()
            waypoints.add(Landmark("London", 51.5073204, -0.1276475))
            waypoints.add(Landmark("Paris", 48.8566932, 2.3514616))

            error = routingService.calculateRoute(waypoints = waypoints)
        }
        wait(objSync, 12000)
        assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
        assert(error == GemError.NoError) { GemError.getMessage(error) }
        assert(routeList?.isNotEmpty() == true) { "Routing service returned no results." }
        assert(routeInstructions?.isNotEmpty() == true) {
            "Routes instructions returned $routeInstructions with size of ${routeInstructions?.size}"
        }
    }

    /**NOT TEST*/
    private fun notify(lock: Object) = synchronized(lock) { lock.notify() }
    private fun wait(lock: Object, timeout: Long) = synchronized(lock) { lock.wait(timeout) }
}
