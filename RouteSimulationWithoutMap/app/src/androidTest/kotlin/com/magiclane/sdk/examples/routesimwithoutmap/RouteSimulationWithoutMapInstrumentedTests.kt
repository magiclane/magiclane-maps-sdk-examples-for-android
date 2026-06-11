/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimwithoutmap

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
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RouteSimulationWithoutMapInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun checkRoutingProgressListenerCallbacks() {
        var destinationReachedPassed = false
        val objSync = Object()
        var onProgressCompletedPassed = false
        var onProgressStartedPassed = false
        var onProgressStatusChangedPassed = false

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
                            SdkCall.runSynced {
                                GemError.getMessage(
                                    errorCode,
                                    appContext,
                                )
                            }
                        }"
                    }
                    onProgressCompletedPassed = true
                },
                onStatusChanged = { status ->
                    onProgressStatusChangedPassed = true
                },
            )
            val navigationListener = NavigationListener.create(
                onDestinationReached = { _: Landmark ->
                    destinationReachedPassed = true
                    notify(objSync)
                },
            )

            navigationService.startSimulation(
                waypoints,
                navigationListener,
                routingProgressListener,
            )
        }
        wait(objSync, 300000)

        assert(destinationReachedPassed) { "Destination reached callback not called" }
        assert(onProgressStartedPassed) { "Progress onCStarted callback not called" }
        assert(onProgressCompletedPassed) { "Progress onCompleted callback not called" }
        assert(onProgressStatusChangedPassed) { "Progress onStatusChanged callback not called" }
    }

    @Test
    fun checkNavigationListenerCallbacks() {
        var destinationReachedPassed = false
        var onNavStartedPassed = false
        var onNavInstrUpdatedPassed = false
        var onWaypointReachedPassed = false
        var onNavStatusChangedPassed = false
        var onNavSoundPassed = false
        val objSync = Object()

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
                    notify(objSync)
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
            )
        }
        wait(objSync, 300000)

        assert(onNavStartedPassed) { "OnNavigationStarted call back not called" }
        assert(onNavInstrUpdatedPassed) { "OnNavInstrUpdatedPassed call back not called" }
        assert(onWaypointReachedPassed) { "OnWaypointReachedPassed  call back not called" }
        assert(onNavSoundPassed) { "OnNavSoundPassed call back not called" }
        assert(onNavStatusChangedPassed) { "OnNavStatusChangedPassed call back not called" }
        assert(destinationReachedPassed) { "Destination reached callback not called" }
    }

    /**NOT TEST*/
    private fun notify(lock: Object) = synchronized(lock) { lock.notify() }
    private fun wait(lock: Object, timeout: Long) = synchronized(lock) { lock.wait(timeout) }
}
