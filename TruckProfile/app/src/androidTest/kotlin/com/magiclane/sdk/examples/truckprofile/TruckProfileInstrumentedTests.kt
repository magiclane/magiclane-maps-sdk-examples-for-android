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

package com.magiclane.sdk.examples.truckprofile

// -------------------------------------------------------------------------------------------------
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.routesandnavigation.TruckProfile
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

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class TruckProfileInstrumentedTests
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
    fun routeProfileShouldBeTheSameAfterCalculatingRoute() = runBlocking {
        var onCompletedPassed = false
        var error : ErrorCode
        val channel = Channel<Unit>()

        lateinit var preferencesTruckProfile: TruckProfile

        val routingService = RoutingService(
            onCompleted = { _, errorCode, _ ->
                error = errorCode
                onCompletedPassed = true
                launch {
                    channel.send(Unit)
                }
            }
        )

        error = async {
            SdkCall.execute {
                preferencesTruckProfile = TruckProfile(
                    (3 * MainActivity.ETruckProfileUnitConverters.Weight.unit).toInt(),
                    (1.8 * MainActivity.ETruckProfileUnitConverters.Height.unit).toInt(),
                    (5 * MainActivity.ETruckProfileUnitConverters.Length.unit).toInt(),
                    (2 * MainActivity.ETruckProfileUnitConverters.Width.unit).toInt(),
                    (1.5 * MainActivity.ETruckProfileUnitConverters.AxleWeight.unit).toInt(),
                    (60 * MainActivity.ETruckProfileUnitConverters.MaxSpeed.unit).toDouble()
                )
                routingService.preferences.truckProfile = preferencesTruckProfile
                val waypoints = arrayListOf(
                    Landmark("London", 51.5073204, -0.1276475),
                    Landmark("Paris", 48.8566932, 2.3514616)
                )
                routingService.calculateRoute(waypoints)
            }
        }.await() as ErrorCode

        val res = async {
            SdkCall.execute {
                routingService.preferences.truckProfile?.equalsContent(preferencesTruckProfile)
            } ?: false
        }.await()

        //5min limit
        withTimeout(300000) {
            channel.receive()
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(res) { "Truck profile changed after calculate route" }
        }
    }

    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
}

internal fun TruckProfile.equalsContent(o: TruckProfile?): Boolean
{
    return o?.let {
        this.mass == it.mass && this.height == it.height && this.length == it.length &&
                this.width == it.width && this.axleLoad == it.axleLoad && this.maxSpeed == it.maxSpeed

    } ?: false
}