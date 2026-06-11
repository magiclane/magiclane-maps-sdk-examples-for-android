/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxroutesimulation

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class GPXRouteSimulationInstrumentedTests {

    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun simulateRoute(): Unit = runBlocking {
        val channel = Channel<Unit>()
        val navigationService = NavigationService()
        val navigationListener = NavigationListener.create(
            onNavigationInstructionUpdated = {
                SdkCall.execute {
                    navigationService.cancelNavigation()
                }
            },
            onNavigationError = { error ->
                if (error == GemError.Cancel) {
                    launch {
                        Log.d("BLABLA", "canceled ")
                        channel.send(Unit)
                    }
                }
            },
            postOnMain = false,
        )
        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                when (errorCode) {
                    GemError.NoError ->
                        {
                            val route = routes[0]
                            SdkCall.execute {
                                val result = navigationService.startSimulationWithRoute(
                                    route,
                                    navigationListener,
                                    ProgressListener.create(
                                        onCompleted = { code, _ ->
                                            assert(
                                                code == GemError.NoError,
                                            ) { GemError.getMessage(code) }
                                        },
                                    ),
                                )
                                assert(!GemError.isError(result)) { GemError.getMessage(result) }
                            }
                        }

                    else -> Assert.fail(GemError.getMessage(errorCode))
                }
            },
        )
        launch {
            delay(3000)
            channel.send(Unit)
        }
        /**
         * delay(20000)
         */
        withTimeout(60000) {
            val l = arrayListOf(
                "1.gpx",
                "2.gpx",
                "test_route.gpx",
                "3.gpx",
                "test_route_old.gpx",
                "4.gpx",
                "5.gpx",
                "test.gpx",
            )
            l.forEach { gpxAssetPath ->
                channel.receive()
                Log.d("BLABLA", "started $gpxAssetPath")
                calculateRouteFromGPX(routingService, "gpx/$gpxAssetPath")
            }
        }
    }

    private fun calculateRouteFromGPX(routingService: RoutingService, gpxAssetPath: String) = SdkCall.execute {
        // Opens GPX input stream.
        val input = appContext.resources.assets.open(gpxAssetPath)

        // Produce a Path based on the data in the buffer.
        val track = Path.produceWithGpx(input) ?: return@execute

        // Set the transport mode to bike and calculate the route.
        val result = routingService.calculateRoute(track, ERouteTransportMode.Bicycle)
        assert(result == GemError.NoError) { GemError.getMessage(result) }
    }
}
