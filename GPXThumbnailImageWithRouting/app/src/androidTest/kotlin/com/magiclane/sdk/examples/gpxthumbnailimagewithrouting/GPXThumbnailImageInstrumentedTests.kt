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


package com.magiclane.sdk.examples.gpxthumbnailimage

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.examples.gpxthumbnailimagewithrouting.R
import com.magiclane.sdk.routesandnavigation.ELineType
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RouteRenderSettings
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
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
class GPXThumbnailImageInstrumentedTests
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

    private fun calculateRouteFromGPX(routingService: RoutingService) = SdkCall.execute {
        val gpxAssetsFilename = "gpx/test_route.gpx"

        // Opens GPX input stream.
        val input = appContext.resources.assets.open(gpxAssetsFilename)

        // Produce a Path based on the data in the buffer.
        val track = Path.produceWithGpx(input/*.readBytes()*/) ?: return@execute

        // Set the transport mode to bike and calculate the route.
        routingService.calculateRoute(track, ERouteTransportMode.Car)
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
    fun createMapBitmap() = runBlocking{
        val padding = appContext.resources.getDimensionPixelSize(R.dimen.padding)
        val mapWidth = appContext.resources.getDimensionPixelSize(R.dimen.thumbnail_width)
        val mapHeight = appContext.resources.getDimensionPixelSize(R.dimen.thumbnail_height)
        var mapBitmap: Bitmap? = null

        val channel = Channel<Unit>()
        var error = GemError.General

        val gemOffscreenSurfaceView =
            GemOffscreenSurfaceView(mapWidth,
                mapHeight,
                appContext.resources.displayMetrics.densityDpi,
                onMapRendered = { bitmap ->
                    mapBitmap = bitmap
                })

        val routingService = RoutingService(
            onCompleted = { routes, errorCode, _ ->
                error = errorCode
                when (errorCode)
                {
                    GemError.NoError ->
                    {
                        if (routes.isNotEmpty())
                        {
                            SdkCall.execute {
                                val routeRenderSettings = RouteRenderSettings()
                                routeRenderSettings.innerColor = Rgba.blue()
                                routeRenderSettings.outerColor = Rgba.blue()
                                routeRenderSettings.innerSize = 1.0
                                routeRenderSettings.outerSize = 0.0
                                routeRenderSettings.lineType = ELineType.LT_Solid

                                gemOffscreenSurfaceView.mapView?.presentRoute(
                                    routes[0],
                                    animation = Animation(
                                        listener = ProgressListener.create(onCompleted = { _, _ ->
                                            /*Util.postOnMainDelayed({
                                                //set bitmap to image view
                                            }, 3000)*/
                                            runBlocking { delay(3000)
                                                channel.send(Unit)
                                            }
                                        }),
                                        animation = EAnimation.Linear,
                                        duration = 100
                                    ),
                                    edgeAreaInsets = Rect(padding, padding, padding, padding),
                                    routeRenderSettings = routeRenderSettings
                                )
                            }
                        }
                    }

                    else ->
                    {
                        runBlocking { channel.send(Unit) }
                    }
                }
            }
        )

        calculateRouteFromGPX(routingService)

        withTimeout(12000){
            channel.receive()
            assert(error == GemError.NoError){GemError.getMessage(error)}
            assert( mapBitmap != null) {"Map did not pass on render callback" }
        }

    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
}
