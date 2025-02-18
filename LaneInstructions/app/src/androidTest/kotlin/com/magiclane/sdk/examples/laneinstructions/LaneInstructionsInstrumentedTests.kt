// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.laneinstructions

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
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

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class LaneInstructionsInstrumentedTests
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

        fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null
    }

    @Before
    fun checkTokenAndNetwork()
    {
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

    /***
     * Lasts about 1 min 15s
     */
    @Test
    fun getLaneInstructionsImages(): Unit = runBlocking() {

        val channel = Channel<Unit>() //acts like a lock
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
                }?.second
                list.add(lanes)
            })

        val routingProgressListener = ProgressListener.create(
            onStarted = {
            },

            onCompleted = { _, _ ->
            },

            postOnMain = false
        )

        val deferredWaypoints = async {
            SdkCall.execute {
                arrayListOf(
                    Landmark("Toamnei", 45.65060409523955, 25.616351544839894),
                    Landmark("Harmanului", 45.657543255739384, 25.620411332785498)
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
                    routingProgressListener
                )
            }
        }
        val startNavigationResult = deferredNavResult.await()

        //5min limit
        withTimeout(300000) {
            launch {
                //waits till a matching channel.send() is invoked
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
