// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.search

// -------------------------------------------------------------------------------------------------

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
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
// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchTests
{
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    companion object
    {
        const val TIMEOUT = 180000L // 3 MIN
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private var initResult = false

        @get:ClassRule
        @JvmStatic
        val sdkInitRule = SDKInitRule()

        @BeforeClass
        @JvmStatic
        fun checkSdkInit()
        {
            assert(isInternetOn()) { "No internet connection." }
            assert(initResult) { "GEM SDK not initialized" }
        }

        /** not a test*/
        private fun isInternetOn(): Boolean = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null
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
                        } ?: run {
                            if (isInternetOn())
                                assert(false) { "On map data ready callback was not called!" }
                            else
                                assert(false) { "On map data ready callback was not called because of no network connection! Check your network connection and retry. " }
                        }
                    }
                } else return

                if (!SdkSettings.isMapDataReady)
                    throw Error(GemError.getMessage(GemError.OperationTimeout))

                try
                {
                    base.evaluate() // This executes tests
                } finally
                {
                    GemSdk.release()
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    @Before
    fun checkTokenAndNetwork(){
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(isInternetOn()) { " No internet connection." }
    }
    
    // -------------------------------------------------------------------------------------------------

    @Test
    fun searchByFilterShouldReturnListOfResults(): Unit = runBlocking {
        var onCompletedPassed = false
        var res = LandmarkList()
        var error = GemError.NoError
        val channel = Channel<Unit>(Channel.RENDEZVOUS)
        val searchService = SearchService(
            onCompleted = { results, errorCode, _ ->
                onCompletedPassed = true
                res = results
                error = errorCode
                launch { channel.send(Unit) }
            }
        )

        val code = async {
            SdkCall.execute {
                //London coordinates
                val centerLondon = Coordinates(51.5072, 0.1276)
                searchService.searchByFilter(
                    "a",
                    centerLondon
                ) //result of method one is separate test, see searchByFilterShouldReturnSuccess()
            }
        }.await()

        withTimeout(12000) {
            channel.receive()
            assert(code == GemError.NoError) { GemError.getMessage(error) }
            assert(onCompletedPassed)
            assert(error == GemError.NoError)
            { "An error occurred on GEM SDK thread: ${GemError.getMessage(error)}" }
            assert(res.isNotEmpty())
            { "Result list is empty" }
        }
    }

    // -------------------------------------------------------------------------------------------------
    @Test
    fun searchByFilterShouldReturnSuccess(): Unit = runBlocking {
        val channel = Channel<Unit>(Channel.RENDEZVOUS)
        val searchService = SearchService(
            onCompleted = { _, _, _ ->
                launch { channel.send(Unit) }
            }
        )

        val code = async {
            SdkCall.execute {
                //London coordinates
                val centerLondon = Coordinates(51.5072, 0.1276)
                searchService.searchByFilter("London", centerLondon)
            } ?: GemError.General
        }.await()

        withTimeout(12000) {
            channel.receive()
            assert(code == GemError.NoError) {
                " response to searchByFilter was ${GemError.getMessage(code)}"
            }
        }
    }

    // -------------------------------------------------------------------------------------------------
    @Test
    fun searchByFilterShouldNotSearchEmptyString(): Unit = runBlocking {
        val searchService = SearchService()
        val code = async {
            SdkCall.execute {
                //London coordinates
                val centerLondon = Coordinates(51.5072, 0.1276)
                searchService.searchByFilter(
                    "",
                    centerLondon
                )
            } ?: GemError.General
        }.await()
        assert(code == GemError.InvalidInput)
    }
// -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------
