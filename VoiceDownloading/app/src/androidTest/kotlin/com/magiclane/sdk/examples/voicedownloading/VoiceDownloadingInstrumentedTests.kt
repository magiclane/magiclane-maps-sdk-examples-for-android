// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.voicedownloading

// -------------------------------------------------------------------------------------------------
import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import kotlin.math.log

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class VoiceDownloadingInstrumentedTests
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
    fun checkTokenAndNetwork(){
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(isInternetOn()) { " No internet connection." }
    }

    // -------------------------------------------------------------------------------------------------
    @Rule(order = 0)
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

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
    fun requestVoiceAndDownload() = runBlocking {

        val channel = Channel<Unit>()
        var count = 0
        val contentStore = ContentStore()
        var errorAsyncDownload: ErrorCode = GemError.General
        var progressErrorCode: ErrorCode = GemError.General
        val progressListener = ProgressListener.create(
            onCompleted = onCompletedFun@{ errorCode, _ ->
                progressErrorCode = errorCode
                if (errorCode != GemError.NoError)
                {
                    runBlocking { channel.send(Unit) }
                    return@onCompletedFun
                }
                SdkCall.execute {
                    // No error encountered, we can handle the results.
                    // Get the list of voices that was retrieved in the content store.
                    val models =
                        contentStore.getStoreContentList(EContentType.HumanVoice)?.first
                    if (!models.isNullOrEmpty())
                    {
                        // The voice items list is not empty or null.
                        val voiceItem = models[0]

                        // Start downloading the first voice item.
                        runBlocking {
                            errorAsyncDownload = async {
                                SdkCall.execute {
                                    voiceItem.asyncDownload(GemSdk.EDataSavePolicy.UseDefault,
                                        true,
                                        onStarted = {
                                        },
                                        onCompleted = { _, _ ->
                                            count++
                                            runBlocking {
                                                channel.send(Unit)
                                            }
                                        },
                                        onProgress = {
                                        })
                                } ?: GemError.General
                            }.await()
                        }
                    }
                }

            },
            postOnMain = true
        )

        val error = async {
            SdkCall.execute {
                // Defines an action that should be done after the network is connected.
                // Call to the content store to asynchronously retrieve the list of voices.
                contentStore.asyncGetStoreContentList(
                    EContentType.HumanVoice,
                    progressListener
                )
            } ?: GemError.General
        }.await()


        withTimeout(300000) {
            channel.receive()
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(errorAsyncDownload == GemError.NoError) { GemError.getMessage(errorAsyncDownload) }
            assert(progressErrorCode == GemError.NoError) { GemError.getMessage(progressErrorCode) }
        }
    }
    // -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
