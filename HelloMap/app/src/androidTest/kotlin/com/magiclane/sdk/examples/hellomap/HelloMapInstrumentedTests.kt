// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.hellomap
// -------------------------------------------------------------------------------------------------

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
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

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class HelloMapInstrumentedTests()
{
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    companion object
    {
        // -------------------------------------------------------------------------------------------------
        const val TIMEOUT = 600000L
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private val mainIntent = Intent(appContext, MainActivity::class.java)
        private var initResult = false

        @get:ClassRule
        @JvmStatic
        val sdkInitRule = SDKInitRule()

        fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null

        // -------------------------------------------------------------------------------------------------
    }

    @Before
    fun checkTokenAndNetwork()
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
            private val channel = Channel<Unit>()

            init
            {
                SdkSettings.onMapDataReady = { isReady ->
                    if (isReady)
                        runBlocking { channel.send(Unit) }
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
    fun checkSDKInit()
    {
        assert(initResult)
    }

    // -------------------------------------------------------------------------------------------------
}
