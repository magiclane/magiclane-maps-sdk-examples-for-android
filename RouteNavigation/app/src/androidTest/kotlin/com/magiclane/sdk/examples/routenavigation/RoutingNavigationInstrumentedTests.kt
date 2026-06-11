/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routenavigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RoutingNavigationInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun checkRoutingProgressListenerCallbacks(): Unit = runBlocking {
        var navigationStarted = false
        var onProgressCompletedPassed = false
        var onProgressStartedPassed = false
        var onProgressStatusChangedPassed = false
        var navigationLaunched = false
        val channel = Channel<Unit>()

        SdkCall.execute {
            val navigationService = NavigationService()

            val waypoints = arrayListOf(
                Landmark("StartPoint", 45.654749, 25.612273),
                Landmark("EndPoint", 45.650643, 25.606352),
            )
            val externalDataSource = DataSourceFactory.produceExternal(
                arrayListOf(EDataType.Position),
            )
            externalDataSource?.start()
            PositionService.dataSource = externalDataSource

            val routingProgressListener = ProgressListener.create(
                onStarted = {
                    onProgressStartedPassed = true
                },
                onCompleted = { errorCode, _ ->
                    assert(errorCode == GemError.NoError) {
                        "Progress Completed with error: ${
                            GemError.getMessage(
                                errorCode,
                            )
                        }"
                    }
                    onProgressCompletedPassed = true
                },
                onStatusChanged = { _ ->
                    onProgressStatusChangedPassed = true
                },
            )
            val navigationListener = NavigationListener.create(
                onNavigationStarted = {
                    navigationStarted = true
                    launch { channel.send(Unit) }
                },
            )
            val positionListenerChannel = Channel<Unit>()
            val positionListener = PositionListener { positionData ->
                assert(positionData.isValid()) { "InvalidDataPosition" }
                if (navigationLaunched) return@PositionListener
                val error = navigationService.startNavigation(
                    destination = waypoints[1],
                    navigationListener = navigationListener,
                    progressListener = routingProgressListener,
                )
                navigationLaunched = true
                assert(error == 0) { GemError.getMessage(error) }
                launch { positionListenerChannel.send(Unit) }
            }
            PositionService.dataSource = externalDataSource
            PositionService.addListener(positionListener)
            val externalPosition = PositionData.produce(
                System.currentTimeMillis(),
                waypoints[0].coordinates!!.latitude,
                waypoints[0].coordinates!!.longitude,
            )
            assert(externalPosition != null) { "External position returned null" }
            externalDataSource?.pushData(externalPosition!!)
            launch {
                positionListenerChannel.receive()
                SdkCall.execute {
                    PositionService.removeListener(positionListener)
                }
            }
        }

        withTimeout(300000) {
            channel.receive()
            delay(2000)
            assert(navigationStarted) { "Destination reached callback not called" }
            assert(onProgressStartedPassed) { "Progress onCStarted callback not called" }
            assert(onProgressCompletedPassed) { "Progress onCompleted callback not called" }
            assert(onProgressStatusChangedPassed) { "Progress onStatusChanged callback not called" }
        }
    }
}
