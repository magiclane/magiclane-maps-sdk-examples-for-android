// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.gpxroutesimulation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class GPXRouteSimulationInstrumentedTests
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
            assert(initResult) { "GEM SDK not initialized" }
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
            private val channel = Channel<Boolean>()

            init
            {
                SdkSettings.onMapDataReady = { isReady ->
                    if (isReady)
                        runBlocking {
                            channel.send(true)
                        }
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
                        val sdkChannelJob = launch { channel.receive() }
                        withTimeout(TIMEOUT) {
                            while (sdkChannelJob.isActive) delay(500)
                        }
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
    fun simulateRoute(): Unit = runBlocking {

        val channel = Channel<Unit>()

        val navigationService = NavigationService()
        val navigationListener = SdkCall.execute { NavigationListener.create() }!!
        var error: ErrorCode = GemError.General

        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                error = errorCode
                when (errorCode)
                {
                    GemError.NoError ->
                    {
                        val route = routes[0]
                        SdkCall.execute {
                            navigationService.startSimulationWithRoute(
                                route,
                                navigationListener,
                                ProgressListener.create(
                                    onCompleted = { code, _ ->
                                        error = code
                                        runBlocking {
                                            channel.send(Unit)
                                            SdkCall.execute {
                                                navigationService.cancelNavigation(
                                                    navigationListener
                                                )
                                            }
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
            }
        )

        arrayListOf(
            "test_route.gpx",
            "1.gpx",
            "2.gpx",
            "3.gpx",
            "4.gpx",
            "5.gpx",
            "test.gpx",
            "test_route_old.gpx"
        ).forEach { gpxAssetPath ->
            calculateRouteFromGPX(routingService, "gpx/$gpxAssetPath")
            withTimeout(120000) {
                channel.receive()
                assert(!GemError.isError(error)) {
                    "For $gpxAssetPath: " + GemError.getMessage(
                        error
                    )
                }
            }
        }
    }

    private fun calculateRouteFromGPX(routingService: RoutingService, gpxAssetPath: String) =
        SdkCall.execute {
            // Opens GPX input stream.
            val input = appContext.resources.assets.open(gpxAssetPath)

            // Produce a Path based on the data in the buffer.
            val track = Path.produceWithGpx(input/*.readBytes()*/) ?: return@execute

            // Set the transport mode to bike and calculate the route.
            routingService.calculateRoute(track, ERouteTransportMode.Bicycle)
        }
}