// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.externalpositionsourcenavigation

import android.content.Context
import android.net.ConnectivityManager
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
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
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
import org.junit.runners.model.Statement
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class ExternalPositionTests
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

        private fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null

    }

    @Before
    fun checkTokenAndInternetConnection()
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
    // -------------------------------------------------------------------------------------------------

    @Test
    fun testExternalDataSource(): Unit = runBlocking {
        val navigationService = NavigationService()
        lateinit var positionListener: PositionListener
        var externalDataSource: ExternalDataSource?
        val channel = Channel<Unit>()
        var timer: Timer? = null

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
                timer = fixedRateTimer("timer", false, 0L, 1000) {
                    SdkCall.execute {
                        val externalPosition = PositionData.produce(
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
                        index = 0
                        launch { channel.send(Unit) }
                    }
                }
            }
        }
        withTimeout(180000) {
            channel.receive()
            timer?.cancel()
        }
    }
}
