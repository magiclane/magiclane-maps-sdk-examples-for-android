/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.truckprofile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.routesandnavigation.TruckProfile
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
class TruckProfileInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun routeProfileShouldBeTheSameAfterCalculatingRoute() = runBlocking {
        var onCompletedPassed = false
        var error: ErrorCode
        val channel = Channel<Unit>()

        lateinit var preferencesTruckProfile: TruckProfile

        val routingService = RoutingService(
            onCompleted = { _, errorCode, _ ->
                error = errorCode
                onCompletedPassed = true
                launch {
                    channel.send(Unit)
                }
            },
        )

        error = async {
            SdkCall.execute {
                preferencesTruckProfile = TruckProfile(
                    (3 * MainActivity.ETruckProfileUnitConverters.Weight.unit).toInt(),
                    (1.8 * MainActivity.ETruckProfileUnitConverters.Height.unit).toInt(),
                    (5 * MainActivity.ETruckProfileUnitConverters.Length.unit).toInt(),
                    (2 * MainActivity.ETruckProfileUnitConverters.Width.unit).toInt(),
                    (1.5 * MainActivity.ETruckProfileUnitConverters.AxleWeight.unit).toInt(),
                    (60 * MainActivity.ETruckProfileUnitConverters.MaxSpeed.unit).toDouble(),
                )
                routingService.preferences.truckProfile = preferencesTruckProfile
                val waypoints = arrayListOf(
                    Landmark("London", 51.5073204, -0.1276475),
                    Landmark("Paris", 48.8566932, 2.3514616),
                )
                routingService.calculateRoute(waypoints)
            }
        }.await() as ErrorCode

        val res = async {
            SdkCall.execute {
                routingService.preferences.truckProfile?.equalsContent(preferencesTruckProfile)
            } ?: false
        }.await()

        // 5min limit
        withTimeout(300000) {
            channel.receive()
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(res) { "Truck profile changed after calculate route" }
        }
    }
}

internal fun TruckProfile.equalsContent(o: TruckProfile?): Boolean {
    return o?.let {
        this.mass == it.mass && this.height == it.height && this.length == it.length &&
            this.width == it.width && this.axleLoad == it.axleLoad && this.maxSpeed == it.maxSpeed
    } ?: false
}
