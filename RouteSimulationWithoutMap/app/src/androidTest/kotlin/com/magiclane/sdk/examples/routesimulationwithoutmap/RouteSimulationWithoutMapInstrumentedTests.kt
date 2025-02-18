// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.routesimulationwithoutmap
// -------------------------------------------------------------------------------------------------------------------------------

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

// -------------------------------------------------------------------------------------------------------------------------------

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RouteSimulationWithoutMapInstrumentedTests
{
    // -------------------------------------------------------------------------------------------------
    companion object
    {
        // -------------------------------------------------------------------------------------------------
        const val TIMEOUT = 600000L
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private var initResult = false

        @get:ClassRule
        @JvmStatic
        val sdkInitRule = SDKInitRule()

        @BeforeClass
        @JvmStatic
        fun checkSdkInitStartActivity()
        {
            assert(initResult) {"GEM SDK not initialized" }
        }
        // -------------------------------------------------------------------------------------------------
    }


    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    class SDKInitRule : TestRule
    {
        override fun apply(base: Statement, description: Description) = SDKStatement(base)

        inner class SDKStatement(private val base: Statement) : Statement()
        {
            private val lock = Object()

            init
            {
                SdkSettings.onMapDataReady = { isReady ->
                    if (isReady)
                        synchronized(lock) { lock.notify() }
                }
            }

            @Throws(Throwable::class)
            override fun evaluate()
            {
                //before tests are executed
                if (!GemSdk.isInitialized())
                {
                    runBlocking {
                        initResult = GemSdk.initSdkWithDefaults(appContext)

                        // must wait for map data ready
                        synchronized(lock) { lock.wait(TIMEOUT) }
                    }
                }
                else return

                if (!SdkSettings.isMapDataReady)
                    throw Error(GemError.getMessage(GemError.OperationTimeout))

                try
                {
                    base.evaluate() // This executes tests
                }
                finally
                {
                    GemSdk.release()
                }
            }
        }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    @Test
    fun checkRoutingProgressListenerCallbacks()
    {
        var destinationReachedPassed = false
        val objSync = Object()
        var onProgressCompletedPassed = false
        var onProgressStartedPassed = false
        var onProgressStatusChangedPassed = false

        SdkCall.execute {
            val navigationService = NavigationService()

            val waypoints = arrayListOf(
                Landmark("StartPoint", 45.654789, 25.612160),
                Landmark("EndPoint", 45.650643, 25.606352)
            )

            val routingProgressListener = ProgressListener.create(
                onStarted = {
                    onProgressStartedPassed = true
                },
                onCompleted = { errorCode, _ ->
                    assert(errorCode == GemError.NoError) {
                        "Progress Completed with error: ${
                            GemError.getMessage(
                                errorCode
                            )
                        }"
                    }
                    onProgressCompletedPassed = true
                },
                onStatusChanged = { status ->
                    onProgressStatusChangedPassed = true
                }
            )
            val navigationListener = NavigationListener.create(
                onDestinationReached = { _: Landmark ->
                    destinationReachedPassed = true
                    notify(objSync)
                }
            )

            navigationService.startSimulation(
                waypoints,
                navigationListener,
                routingProgressListener
            )
        }
        wait(objSync, 300000)

        assert(destinationReachedPassed) { "Destination reached callback not called" }
        assert(onProgressStartedPassed) { "Progress onCStarted callback not called" }
        assert(onProgressCompletedPassed) { "Progress onCompleted callback not called" }
        assert(onProgressStatusChangedPassed) { "Progress onStatusChanged callback not called" }
    }

    @Test
    fun checkNavigationListenerCallbacks()
    {
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
                Landmark("EndPoint", 45.650643, 25.606352)
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
                }
            )

            navigationService.startSimulation(
                waypoints,
                navigationListener,
                routingProgressListener
            )
        }
        wait(objSync, 300000)

        assert(onNavStartedPassed){"OnNavigationStarted call back not called"}
        assert(onNavInstrUpdatedPassed){"OnNavInstrUpdatedPassed call back not called"}
        assert(onWaypointReachedPassed){"OnWaypointReachedPassed  call back not called"}
        assert(onNavSoundPassed){"OnNavSoundPassed call back not called"}
        assert(onNavStatusChangedPassed){"OnNavStatusChangedPassed call back not called"}
        assert(destinationReachedPassed) { "Destination reached callback not called" }
    }

    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    /**NOT TEST*/
    private fun notify(lock: Object) = synchronized(lock) { lock.notify() }
    private fun wait(lock: Object, timeout: Long) = synchronized(lock) { lock.wait(timeout) }
    // -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------------------------------------
