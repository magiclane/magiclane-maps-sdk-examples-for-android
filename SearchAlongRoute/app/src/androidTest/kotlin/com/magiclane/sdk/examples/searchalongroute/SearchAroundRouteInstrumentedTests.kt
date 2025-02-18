// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.searchalongroute

// -------------------------------------------------------------------------------------------------

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

// -------------------------------------------------------------------------------------------------

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchAroundRouteInstrumentedTests
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
        fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null
        // -------------------------------------------------------------------------------------------------
    }

    @Before
    fun checkTokenAndNetwork(){
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(isInternetOn()) { " No internet connection." }
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
                        withTimeoutOrNull(TIMEOUT) {
                            channel.receive()
                        } ?: if (isInternetOn()) assert(false) { "No internet." }
                        else assert(false) { "Unexpected error. SDK not initialised." }
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
    fun routingServiceShouldReturnRoutesList(): Unit = runBlocking {
        var onCompletedPassed = false
        val channel = Channel<Unit>(Channel.RENDEZVOUS)
        var error: ErrorCode
        var routesList: ArrayList<Route>? = null
        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                onCompletedPassed = true
                error = errorCode
                routesList = routes
                launch { channel.send(Unit) }
            }
        )

        error = async {
            SdkCall.execute {
                val waypoints = arrayListOf(
                    Landmark("Folkestone", 51.0814, 1.1695),
                    Landmark("Paris", 48.8566932, 2.3514616)
                )
                routingService.calculateRoute(waypoints)
            }
        }.await() as ErrorCode

        withTimeout(TIMEOUT) {
            channel.receive()
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(routesList != null)
        }
    }

    @Test
    fun searchServiceSearchAlongRouteShouldReturnListOfNearbyGasStationsOnFirstFoundRoute(): Unit =
        runBlocking {
            var onCompletedPassed = false
            var error = GemError.NoError
            var routesList: ArrayList<Route>? = null
            var landmarkList: LandmarkList? = null
            val channel = Channel<Unit>()
            val routingService = RoutingService(
                onCompleted = { routes, errorCode, _ ->
                    routesList = routes
                    error = errorCode
                    launch { channel.send(Unit) }
                }
            )
            val searchService = SearchService(
                onCompleted = { results, errorCode, _ ->
                    onCompletedPassed = true
                    error = errorCode
                    landmarkList = results
                    launch { channel.send(Unit) }
                }
            )

            val job1 = launch {
                SdkCall.execute {
                    val waypoints = arrayListOf(
                        Landmark("Folkestone", 51.0814, 1.1695),
                        Landmark("Paris", 48.8566932, 2.3514616)
                    )
                    error = routingService.calculateRoute(waypoints)
                }
                channel.receive()
            }


            withTimeout(12000) {

                withTimeout(12000) {
                    while (job1.isActive) delay(500)
                }
                SdkCall.execute {
                    // Set the maximum number of results to 25.
                    searchService.preferences.maxMatches = 25

                    // Search Gas Stations along the route.
                    routesList?.let {
                        searchService.searchAlongRoute(it[0], EGenericCategoriesIDs.GasStation)
                    }
                }
                channel.receive()
                assert(onCompletedPassed) {
                    "OnCompleted not passed : ${
                        GemError.getMessage(
                            error
                        )
                    }"
                }
                assert(error == GemError.NoError) { GemError.getMessage(error) }
                assert(!routesList.isNullOrEmpty()) { "Route lists were empty" }
                assert(!landmarkList.isNullOrEmpty()) { "Search around route " }
            }
        }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
}
