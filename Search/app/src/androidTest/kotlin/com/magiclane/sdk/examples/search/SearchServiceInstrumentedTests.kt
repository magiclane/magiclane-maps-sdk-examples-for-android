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


package com.magiclane.sdk.examples.search

// -------------------------------------------------------------------------------------------------

import android.content.Context
import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

// -------------------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchServiceInstrumentedTests
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
            assert(initResult) { "GEM SDK not initialized" }
        }
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