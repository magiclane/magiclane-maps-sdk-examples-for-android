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

package com.magiclane.sdk.examples.voiceinstrroutesimulation

// -------------------------------------------------------------------------------------------------
import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.examples.voiceinstrroutesimulation.VoiceInstrInstrumentedTests.Companion.voiceHasBeenDownloaded
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class VoiceInstrInstrumentedTests
{
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    companion object
    {
        const val TIMEOUT = 180000L // 3 MIN
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private var initResult = false
        private var contentStore: ContentStore? = null
        val type = EContentType.HumanVoice
        const val COUNTRY_CODE = "DEU"
        var voiceHasBeenDownloaded = false

        @get:ClassRule
        @JvmStatic
        val sdkInitRule = SDKInitRule()

        @BeforeClass
        @JvmStatic
        fun checkSdkInit()
        {
            assert(initResult) { "GEM SDK not initialized" }
        }

        fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null
    }

    @Before
    fun checkTokenAndNetwork() {
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(isInternetOn()) { " No internet connection." }
    }
    
    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
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

    @Before
    fun beforeTests() = runBlocking {
        val channel = Channel<Unit>()
        SdkCall.execute {
            contentStore = ContentStore()
            // check if already exists locally
            contentStore?.getLocalContentList(type)?.let { localList ->
                for (item in localList)
                {
                    if (item.countryCodes?.contains(COUNTRY_CODE) == true)
                    {
                        voiceHasBeenDownloaded = true
                        onVoiceReady(item.filename!!)
                        launch { channel.send(Unit) }
                        return@execute // already exists
                    }
                }
                launch { channel.send(Unit) }
            }
        }
        withTimeoutOrNull(30000) { channel.receive() } ?: Assert.fail("Failed to get local content list")

        if (!voiceHasBeenDownloaded)
            SdkCall.execute {
                contentStore?.asyncGetStoreContentList(
                    type,
                    onCompleted = { result, _, _ ->
                        SdkCall.execute {
                            for (item in result)
                            {
                                if (item.countryCodes?.contains(COUNTRY_CODE) == true)
                                {
                                    item.asyncDownload(onCompleted = { _, _ ->
                                        SdkCall.execute {
                                            onVoiceReady(item.filename!!)
                                            voiceHasBeenDownloaded = true
                                            launch { channel.send(Unit) }
                                        }
                                    })
                                    break
                                }
                            }
                        }
                    })
            }
        withTimeoutOrNull(30000) { channel.receive() } ?: Assert.fail("Voice failed to download")
    }

    // -------------------------------------------------------------------------------------------------

    @Test
    fun simulationShouldEndSuccessfullyAndPlaySound() = runBlocking {

        val channel = Channel<Unit>() //acts like a lock}
        var completed = false
        var soundReceived = false
        var error = GemError.NoError
        val navigationService = NavigationService()

        assert(voiceHasBeenDownloaded) { "Voice has not been downloaded" }

        SdkCall.execute {
            val waypoints = arrayListOf(
                /*                    Landmark("Start", 45.655136, 25.612328),
                                    Landmark("Destination", 45.648965, 25.612081)*/
                Landmark("Start", 45.657188, 25.612740),
                Landmark("Destination", 45.652875, 25.605771)
            )

            error = navigationService.startSimulation(
                waypoints,
                NavigationListener.create(
                    onNavigationSound = { sound ->
                        SdkCall.execute {
                            soundReceived = sound.isValid()
                            SoundPlayingService.play(
                                sound,
                                SoundPlayingListener(),
                                SoundPlayingPreferences()
                            )
                        }
                    },
                    onDestinationReached = {
                        completed = true
                        launch {
                            channel.send(Unit)
                        }
                    },
                    onNavigationError = { error = it },
                    canPlayNavigationSound = true,
                    postOnMain = true
                ),
                ProgressListener.create(
                    onCompleted = { _, _ ->
                    },
                    postOnMain = true
                ),
                speedMultiplier = 3f
            )
        }

        withTimeout(300000) {

            channel.receive()
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(completed) { "Destination not reached or failed callback invoke" }
            assert(soundReceived) { "Sound is invalid" }
        }
    }

    // -------------------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------
    private fun onVoiceReady(voiceFilePath: String) = SdkSettings.setVoiceByPath(voiceFilePath)
}
