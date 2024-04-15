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

package com.magiclane.sdk.examples.speedwatcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemMap
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SpeedWatcherNavServiceInstrumentedTests
{
    // -------------------------------------------------------------------------------------------------
    companion object
    {
        // -------------------------------------------------------------------------------------------------
        const val TIMEOUT = 600000L
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private val mainIntent = Intent(appContext, MainActivity::class.java)
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
    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )


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
    fun navigationServiceCallbacksAreCalled() = runBlocking {

        val channel = Channel<Unit>() //acts like a lock
        var navStarted = false
        var navEnded = false
        var hasHadError = false
        var error = GemError.NoError
        var updateCount = 0
        val navigationListener: NavigationListener = NavigationListener.create(
            onNavigationStarted = {
                navStarted = true
            },
            onNavigationInstructionUpdated = { instr ->
                updateCount++
                runBlocking {
                    val positionIsValid = async {
                        SdkCall.execute {
                            instr.currentPosition?.isValid() == true
                        } ?: false
                    }.await()
                    val speed = async {
                        SdkCall.execute { instr.currentPosition?.speed } ?: 0.0
                    }.await()
                    assert(positionIsValid)
                    assert(speed > 0)
                }
            },
            onDestinationReached = {
                // DON'T FORGET to remove the position listener after the navigation is done.
                navEnded = true
                launch { channel.send(Unit) }
            },
            onNavigationError = {
                error = it
                hasHadError = true
                launch { channel.send(Unit) }
            }
        )

        val navigationService = NavigationService()

        SdkCall.execute {
            val waypoints = arrayListOf(
                Landmark("START", 45.654200, 25.605294),
                Landmark("FINISH", 45.648774, 25.619747)
            )
            navigationService.startSimulation(
                waypoints,
                navigationListener,
                ProgressListener.create(),
                speedMultiplier = 5f
            )
        }

        withTimeout(300000) {
            channel.receive()
            assert(navStarted)
            assert(!hasHadError) {
                "Passed through onNavigationError callBack: $hasHadError ${
                    GemError.getMessage(
                        error
                    )
                }"
            }
            assert(updateCount > 0)
            assert(navEnded)
        }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
}