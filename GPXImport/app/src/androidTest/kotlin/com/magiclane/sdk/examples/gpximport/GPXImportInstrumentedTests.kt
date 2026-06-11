/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpximport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class GPXImportInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Test
    fun importGPX() {
        SdkCall.execute {
            val gpxAssetsFilename = "gpx/test_route.gpx"
            // Opens GPX input stream.
            val input = appContext.resources.assets.open(gpxAssetsFilename)
            // Produce a Path based on the data in the buffer.
            val track = Path.produceWithGpx(input/*.readBytes()*/)
            assert(track != null)
            val routingService = RoutingService()
            val error = routingService.calculateRoute(track!!, ERouteTransportMode.Bicycle)
            assert(!GemError.isError(error)) { GemError.getMessage(error) }
        }
    }
}
