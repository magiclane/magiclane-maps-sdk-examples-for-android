/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.externalpositionsourcenavigation

import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
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
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test

class ExternalPositionTests {
    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

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
                if (position.isValid()) {
                    navigationService.startNavigation(
                        Landmark(
                            "Poing",
                            MainActivity.destination.first,
                            MainActivity.destination.second,
                        ),
                        NavigationListener(),
                        ProgressListener(),
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
                            },
                        )
                        externalPosition?.let { pos ->
                            dataSource.pushData(pos)
                        }
                    }
                    index++
                    if (index == MainActivity.positions.size) {
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
