// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.ptroutingonmap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RouteList
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
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
class PublicTransitInstrumentationTests
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
            try
            {
                assert(initResult)
            }
            catch (e: AssertionError)
            {
                throw AssertionError("GEM SDK not initialized", e)
            }
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
    fun routingServiceShouldReturnRoutesWithInstructions() = runBlocking {
        var onCompletedPassed = false
        var error : ErrorCode
        val channel = Channel<Unit>()
        var routeList: RouteList? = null

        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                error = errorCode
                onCompletedPassed = true
                SdkCall.execute {
                    routeList = routes
                    launch { channel.send(Unit) }
                }
            }
        )

        error = async {
            SdkCall.execute {
                val waypoints = ArrayList<Landmark>()
                waypoints.add(Landmark("San Francisco", 37.77903, -122.41991))
                waypoints.add(Landmark("San Jose", 37.33619, -121.89058))
                routingService.preferences.transportMode = ERouteTransportMode.Public

                routingService.calculateRoute(waypoints = waypoints)
            }
        }.await() as ErrorCode

        withTimeout(12000) {
            channel.receive()
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(routeList?.isNotEmpty() == true) { "Routing service returned no results." }
        }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
}