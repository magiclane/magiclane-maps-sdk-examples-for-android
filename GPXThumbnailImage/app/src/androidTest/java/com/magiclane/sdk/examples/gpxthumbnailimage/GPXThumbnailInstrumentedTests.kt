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


package com.magiclane.sdk.examples.gpxthumbnailimage

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EViewCameraTransitionStatus
import com.magiclane.sdk.d3scene.EViewDataTransitionStatus
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
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

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class GPXThumbnailInstrumentedTests
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
    fun createMapBitmap() = runBlocking {
        val channel = Channel<Bitmap?>()
        val channel2 = Channel<MapView>()
        val padding = appContext.resources.getDimensionPixelSize(R.dimen.padding)
        val thumbnailWidth = appContext.resources.getDimensionPixelSize(R.dimen.thumbnail_width)
        val thumbnailHeight = appContext.resources.getDimensionPixelSize(R.dimen.thumbnail_height)
        val gemOffscreenSurfaceView = GemOffscreenSurfaceView(
            thumbnailWidth,
            thumbnailHeight,
            appContext.resources.displayMetrics.densityDpi,
            onDefaultMapViewCreated = {
                launch { channel2.send(it) }
            }
        )
        var screenShotReceived = false


        async {
            channel2.receive()
        }.await().let { mapView ->
            SdkCall.execute {
                val gpxAssetsFileName = "gpx/test_route.gpx"

                // Opens GPX input stream.
                val input = appContext.resources.assets.open(gpxAssetsFileName)

                // Produce a Path based on the data in the buffer.
                val path = Path.produceWithGpx(input) ?: return@execute

                val coordinatesList = path.coordinates
                if (!coordinatesList.isNullOrEmpty())
                {
                    val departureLmk = Landmark("", coordinatesList.first()).also {
                        it.image =
                            ImageDatabase().getImageById(SdkImages.Core.Waypoint_Start.value)
                    }
                    val destinationLmk = Landmark("", coordinatesList.last()).also {
                        it.image =
                            ImageDatabase().getImageById(SdkImages.Core.Waypoint_Finish.value)
                    }

                    val highlightSettings = HighlightRenderSettings().also {
                        it.options = EHighlightOptions.ShowLandmark
                        it.imageSize = 4.0
                    }

                    mapView.activateHighlightLandmarks(
                        arrayListOf(
                            departureLmk,
                            destinationLmk
                        ), highlightSettings
                    )
                }

                val pathCollection = mapView.preferences?.paths
                pathCollection?.add(
                    path,
                    colorBorder = Rgba.black(),
                    colorInner = Rgba.orange(),
                    szBorder = 0.5,
                    szInner = 1.0
                )

                path.area?.let { area ->
                    val margin = 2 * padding
                    mapView.centerOnRectArea(
                        area = area,
                        viewRc = Rect(
                            margin,
                            margin,
                            thumbnailWidth - margin,
                            thumbnailHeight - margin
                        ),
                        animation = Animation(EAnimation.Linear, 10)
                    )
                }
                mapView.preferences?.mapLabelsFading = false
                mapView.onViewRendered = onViewRendered@{ tivStatus, camStatus ->
                    if (screenShotReceived) return@onViewRendered
                    if (tivStatus == EViewDataTransitionStatus.Complete && camStatus == EViewCameraTransitionStatus.Stationary)
                    {
                        gemOffscreenSurfaceView.takeScreenshot { bitmap ->
                            screenShotReceived = true
                            runBlocking {
                                channel.send(bitmap)
                            }
                        }
                        gemOffscreenSurfaceView.mapView?.onViewRendered = null
                    }
                }
            }
        }

        withTimeout(60000)
        {
            val bmp = channel.receive()
            assert(bmp != null)
        }
    }
// -------------------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------
}