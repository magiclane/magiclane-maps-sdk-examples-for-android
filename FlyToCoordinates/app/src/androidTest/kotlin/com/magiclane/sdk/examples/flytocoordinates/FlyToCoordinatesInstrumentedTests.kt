/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.flytocoordinates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class FlyToCoordinatesInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun getTrafficEvent(): Unit = runBlocking {
        val mapWidth = 200 * appContext.resources.displayMetrics.density
        val mapHeight = 200 * appContext.resources.displayMetrics.density
        val gemOffscreenSurfaceView =
            GemOffscreenSurfaceView(
                mapWidth.toInt(),
                mapHeight.toInt(),
                appContext.resources.displayMetrics.densityDpi,
            )

        delay(2000)
        assert(gemOffscreenSurfaceView.mapView != null)
        SdkCall.execute {
            gemOffscreenSurfaceView.mapView?.centerOnCoordinates(
                Coordinates(45.65112176095828, 25.60473923113322),
            )
        }
    }
}
