/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routerestrictions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.RoutePreferences
import com.magiclane.sdk.routesandnavigation.TruckProfile
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RouteRestrictionsInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun simulationStartsSuccessfully(): Unit = runBlocking {
        val channel = Channel<Unit>()
        val navigationService = NavigationService()
        val navigationListener = NavigationListener.create(
            onNavigationStarted = {
                launch { channel.send(Unit) }
            },
            onDestinationReached = {},
        )

        val routingProgressListener = ProgressListener.create(
            onStarted = {},
            onCompleted = { _, _ -> },
            postOnMain = false,
        )

        val deferredResult = async {
            SdkCall.execute {
                val waypoints = arrayListOf(
                    Landmark("Start", 45.65894, 25.57802),
                    Landmark("Stop", 45.66619, 25.61499),
                )
                val routePreferences = RoutePreferences().apply {
                    transportMode = ERouteTransportMode.Lorry
                    truckProfile = TruckProfile(massKg = 4000)
                }
                navigationService.startSimulation(
                    waypoints,
                    navigationListener,
                    routingProgressListener,
                    routePreferences,
                )
            }
        }

        val startResult = deferredResult.await()

        withTimeout(120_000) {
            launch {
                channel.receive()
                assertEquals(GemError.NoError, startResult)
                navigationService.cancelNavigation(navigationListener)
            }
        }
    }
}
