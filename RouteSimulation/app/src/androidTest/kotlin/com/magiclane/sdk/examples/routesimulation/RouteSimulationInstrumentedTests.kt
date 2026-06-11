/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimulation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
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
class RouteSimulationInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun checkRoutingProgressListenerCallbacks() = runBlocking {
        var destinationReachedPassed = false
        var onProgressCompletedPassed = false
        var onProgressStartedPassed = false
        var onProgressStatusChangedPassed = false
        val channel = Channel<Unit>()

        SdkCall.execute {
            val navigationService = NavigationService()

            val waypoints = arrayListOf(
                Landmark("StartPoint", 45.654789, 25.612160),
                Landmark("EndPoint", 45.650643, 25.606352),
            )

            val routingProgressListener = ProgressListener.create(
                onStarted = {
                    onProgressStartedPassed = true
                },
                onCompleted = { errorCode, _ ->
                    assert(errorCode == GemError.NoError) {
                        "Progress Completed with error: ${
                            GemError.getMessage(
                                errorCode,
                            )
                        }"
                    }
                    onProgressCompletedPassed = true
                },
                onStatusChanged = { _ ->
                    onProgressStatusChangedPassed = true
                },
            )
            val navigationListener = NavigationListener.create(
                onDestinationReached = { _: Landmark ->
                    destinationReachedPassed = true
                    launch { channel.send(Unit) }
                },
            )

            navigationService.startSimulation(
                waypoints,
                navigationListener,
                routingProgressListener,
                speedMultiplier = 10f,
            )
        }
        withTimeout(300000) {
            channel.receive()
            assert(destinationReachedPassed) { "Destination reached callback not called" }
            assert(onProgressStartedPassed) { "Progress onCStarted callback not called" }
            assert(onProgressCompletedPassed) { "Progress onCompleted callback not called" }
            assert(onProgressStatusChangedPassed) { "Progress onStatusChanged callback not called" }
        }
    }

    @Test
    fun checkNavigationListenerCallbacks(): Unit = runBlocking {
        var destinationReachedPassed = false
        var onNavStartedPassed = false
        var onNavInstrUpdatedPassed = false
        var onWaypointReachedPassed = false
        var onNavStatusChangedPassed = false
        var onNavSoundPassed = false
        val channel = Channel<Unit>()

        SdkCall.execute {
            val navigationService = NavigationService()

            val waypoints = arrayListOf(
                Landmark("StartPoint", 45.654789, 25.612160),
                Landmark("StartPoint", 45.653831, 25.609548),
                Landmark("EndPoint", 45.650643, 25.606352),
            )

            val routingProgressListener = ProgressListener.create()

            val navigationListener = NavigationListener.create(
                onNavigationStarted = {
                    onNavStartedPassed = true
                },
                onNavigationInstructionUpdated = {
                    onNavInstrUpdatedPassed = true
                },
                onWaypointReached = {
                    onWaypointReachedPassed = true
                },
                onDestinationReached = {
                    destinationReachedPassed = true
                    launch { channel.send(Unit) }
                },
                onNavigationSound = {
                    onNavSoundPassed = true
                },
                canPlayNavigationSound = true,
                onNotifyStatusChange = {
                    onNavStatusChangedPassed = true
                },
            )

            navigationService.startSimulation(
                waypoints,
                navigationListener,
                routingProgressListener,
                speedMultiplier = 5f,
            )
        }

        withTimeout(600000) {
            channel.receive()
            assert(onNavStartedPassed) { "OnNavigationStarted call back not called" }
            assert(onNavInstrUpdatedPassed) { "OnNavInstrUpdatedPassed call back not called" }
            assert(onWaypointReachedPassed) { "OnWaypointReachedPassed  call back not called" }
            assert(onNavSoundPassed) { "OnNavSoundPassed call back not called" }
            assert(onNavStatusChangedPassed) { "OnNavStatusChangedPassed call back not called" }
            assert(destinationReachedPassed) { "Destination reached callback not called" }
        }
    }
}
