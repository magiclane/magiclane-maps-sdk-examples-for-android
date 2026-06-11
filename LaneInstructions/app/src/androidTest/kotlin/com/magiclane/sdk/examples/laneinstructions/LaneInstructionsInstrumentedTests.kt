/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.laneinstructions

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
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
class LaneInstructionsInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    /***
     * Lasts about 1 min 15s
     */
    @Test
    fun getLaneInstructionsImages(): Unit = runBlocking {
        val channel = Channel<Unit>() // acts like a lock
        val list = arrayListOf<Bitmap?>()
        val navigationService = NavigationService()
        val navigationListener: NavigationListener = NavigationListener.create(
            onNavigationStarted = {
            },
            onDestinationReached = {
                launch { channel.send(Unit) }
            },
            onNavigationInstructionUpdated = { instr ->
                // Fetch the bitmap for recommended lanes.
                val lanes = SdkCall.execute {
                    instr.laneImage?.asBitmap(150, 30, activeColor = Rgba.white())
                }
                list.add(lanes)
            },
        )

        val routingProgressListener = ProgressListener.create(
            onStarted = {
            },

            onCompleted = { _, _ ->
            },

            postOnMain = false,
        )

        val deferredWaypoints = async {
            SdkCall.execute {
                arrayListOf(
                    Landmark("Toamnei", 45.65060409523955, 25.616351544839894),
                    Landmark("Harmanului", 45.657543255739384, 25.620411332785498),
                )
            } ?: arrayListOf()
        }
        val waypoints = deferredWaypoints.await()
        assert(waypoints.isNotEmpty())
        val deferredNavResult = async {
            SdkCall.execute {
                navigationService.startSimulation(
                    waypoints,
                    navigationListener,
                    routingProgressListener,
                )
            }
        }
        val startNavigationResult = deferredNavResult.await()

        // 5min limit
        withTimeout(300000) {
            launch {
                // waits till a matching channel.send() is invoked
                channel.receive()
                assert(list.isNotEmpty()) {
                    "List is empty, no lane instruction received." +
                        "This may be false positive if your route does not have lane updates"
                }
                assert(list.filterNotNull().isNotEmpty()) { "No Bitmaps received" }
                assert(startNavigationResult == GemError.NoError) { "Could not start navigation" }
            }
        }
    }
}
