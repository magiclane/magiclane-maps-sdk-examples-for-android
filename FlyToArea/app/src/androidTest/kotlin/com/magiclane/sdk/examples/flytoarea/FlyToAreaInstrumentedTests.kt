/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.flytoarea

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class FlyToAreaInstrumentedTests {
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

        val channel = Channel<Unit>()
        val searchService = SearchService(
            onCompleted = { results, errorCode, _ ->
                assert(!GemError.isError(errorCode)) { GemError.getMessage(errorCode) }
                assert(results.isNotEmpty()) { "Search completed successfully but with no results" }
                val landmark = results[0]
                SdkCall.execute {
                    landmark.geographicArea?.let { area ->
                        gemOffscreenSurfaceView.mapView?.apply {
                            // Define highlight settings for displaying the area contour on map.
                            val settings = HighlightRenderSettings(EHighlightOptions.ShowContour)
                            // Center the map on a specific area using the provided animation.
                            centerOnArea(area)
                            // Highlights a specific area on the map using the provided settings.
                            activateHighlightLandmarks(landmark, settings)
                            runBlocking {
                                channel.send(Unit)
                            }
                        }
                    }
                }
            },
        )

        SdkCall.execute {
            val text = "Statue of Liberty New York"
            val coordinates = Coordinates(40.68925476, -74.04456329)
            searchService.searchByFilter(text, coordinates)
        }

        withTimeout(120000) {
            channel.receive()
        }
    }
}
