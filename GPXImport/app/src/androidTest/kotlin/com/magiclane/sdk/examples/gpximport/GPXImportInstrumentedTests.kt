// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.gpximport

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class GPXImportInstrumentedTests
{
    // -------------------------------------------------------------------------------------------------

    companion object
    {
        // -------------------------------------------------------------------------------------------------
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

        fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null
        // -------------------------------------------------------------------------------------------------
    }
    
    @Before
    fun checkTokenAndInternet(){
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
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
    fun importGPX(){
        SdkCall.execute{
            val gpxAssetsFilename = "gpx/test_route.gpx"
            // Opens GPX input stream.
            val input = appContext.resources.assets.open(gpxAssetsFilename)
            // Produce a Path based on the data in the buffer.
            val track = Path.produceWithGpx(input/*.readBytes()*/)
            assert(track != null)
            val routingService = RoutingService()
            val error = routingService.calculateRoute(track!!, ERouteTransportMode.Bicycle)
            assert(!GemError.isError(error)){GemError.getMessage(error)}
        }
    }
}
