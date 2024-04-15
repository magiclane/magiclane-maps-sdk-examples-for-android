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


package com.magiclane.sdk.examples.externalpositionsourcenavigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.sensordatasource.ExternalDataSource
import com.magiclane.sdk.sensordatasource.ExternalPosition
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
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
import org.junit.runners.model.Statement
import kotlin.concurrent.fixedRateTimer

class ExternalPositionSourceNavigationInstrumentedTests
{
    companion object
    {
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
    fun testExternalDataSource(): Unit = runBlocking {
        val navigationService = NavigationService()
        lateinit var positionListener: PositionListener
        var externalDataSource: ExternalDataSource?
        val channel = Channel<Unit>()

        SdkCall.execute {
            externalDataSource =
                DataSourceFactory.produceExternal(arrayListOf(EDataType.Position))
            externalDataSource?.start()

            positionListener = PositionListener { position: PositionData ->
                if (position.isValid())
                {
                    navigationService.startNavigation(
                        Landmark(
                            "Poing",
                            MainActivity.destination.first,
                            MainActivity.destination.second
                        ),
                        NavigationListener(),
                        ProgressListener()
                    )
                    PositionService.removeListener(positionListener)
                }
            }

            PositionService.dataSource = externalDataSource
            PositionService.addListener(positionListener)
            var index = 0
            externalDataSource?.let { dataSource ->
                fixedRateTimer("timer", false, 0L, 1000) {
                    SdkCall.execute {
                        val externalPosition = ExternalPosition.produceExternalPosition(
                            System.currentTimeMillis(),
                            MainActivity.positions[index].first,
                            MainActivity.positions[index].second,
                            -1.0,
                            MainActivity.positions.getBearing(index).also {
                                if (index > 0) assert(it < 180 && it > -180)
                            },
                            MainActivity.positions.getSpeed(index).also {
                                if (index > 0) assert(it > 0)
                            }
                        )
                        externalPosition?.let { pos ->
                            dataSource.pushData(pos)
                        }

                    }
                    index++
                    if (index == MainActivity.positions.size)
                    {
                        launch { channel.send(Unit) }
                    }
                }
            }
        }
        withTimeout(180000) {
            channel.receive()
        }
    }

}