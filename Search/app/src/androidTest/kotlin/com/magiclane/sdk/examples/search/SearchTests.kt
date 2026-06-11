/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.search

import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchTests {
    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

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
            },
        )

        val code = async {
            SdkCall.execute {
                // London coordinates
                val centerLondon = Coordinates(51.5072, 0.1276)
                searchService.searchByFilter(
                    "a",
                    centerLondon,
                ) // result of method one is separate test, see searchByFilterShouldReturnSuccess()
            }
        }.await()

        withTimeout(12000) {
            channel.receive()
            assert(code == GemError.NoError) { GemError.getMessage(error) }
            assert(onCompletedPassed)
            assert(error == GemError.NoError) {
                "An error occurred on GEM SDK thread: ${GemError.getMessage(error)}"
            }
            assert(res.isNotEmpty()) { "Result list is empty" }
        }
    }

    @Test
    fun searchByFilterShouldReturnSuccess(): Unit = runBlocking {
        val channel = Channel<Unit>(Channel.RENDEZVOUS)
        val searchService = SearchService(
            onCompleted = { _, _, _ ->
                launch { channel.send(Unit) }
            },
        )

        val code = async {
            SdkCall.execute {
                // London coordinates
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

    @Test
    fun searchByFilterShouldNotSearchEmptyString(): Unit = runBlocking {
        val searchService = SearchService()
        val code = async {
            SdkCall.execute {
                // London coordinates
                val centerLondon = Coordinates(51.5072, 0.1276)
                searchService.searchByFilter(
                    "",
                    centerLondon,
                )
            } ?: GemError.General
        }.await()
        assert(code == GemError.InvalidInput)
    }
}
