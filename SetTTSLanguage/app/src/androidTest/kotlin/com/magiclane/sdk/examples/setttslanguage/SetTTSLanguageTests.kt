// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.setttslanguage
// -------------------------------------------------------------------------------------------------------------------------------

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Language
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.TTSLanguage
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sound.SoundUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

// -------------------------------------------------------------------------------------------------------------------------------

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SetTTSLanguageTests : SoundUtils.ITTSPlayerInitializationListener
{
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    private var ttsPlayerIsInitialized = false
    private val classChannel = Channel<Unit>() //acts like a lock

    companion object
    {
        const val TIMEOUT = 180000L // 3 MIN
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private var initResult = false

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
    fun checkTokenAndNetwork(){
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

    @Before
    fun initSoundUtils() = runBlocking {
        SdkCall.execute {
            ttsPlayerIsInitialized =
                SdkCall.execute { SoundPlayingService.ttsPlayerIsInitialized } ?: false

            if (!ttsPlayerIsInitialized)
                SoundUtils.addTTSPlayerInitializationListener(this@SetTTSLanguageTests)
        }
        if (!ttsPlayerIsInitialized)
            runBlocking {
                withTimeout(TIMEOUT) { classChannel.receive() }
            }
        assert(ttsPlayerIsInitialized) { "SoundPlayingService was not initialised" }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    @Test
    fun soundPlayingServiceShouldReturnListOfTTSLanguages() = runBlocking {
        val list: ArrayList<TTSLanguage>? = async {
            SdkCall.execute {
                SoundPlayingService.getTTSLanguages()
            }
        }.await()
        assert(list?.isNotEmpty() == true)
    }

    @Test
    fun languageShouldChange() = runBlocking {
        SdkCall.execute {
            SoundPlayingService.setTTSLanguage("deu-DEU")
        }

        val lang: Language? = async {
            SdkCall.execute {
                SoundPlayingService.ttsPlayer?.getLanguage()
            }
        }.await()

        val result = async {
            SdkCall.execute { lang?.languageCode == "deu" } ?: false
        }.await()

        assert(lang != null) { "Language not set." }

        val str = async {
            SdkCall.execute {
                "Language code does not match the selected value! $lang with ${lang?.languageCode}"
            }
        }.await()

        assert(result) { str!! }
    }

    @Test
    fun shouldPlaySound() = runBlocking {
        val channel = Channel<Unit>() //acts like a lock
        var wasPlayed = false
        var error = GemError.NoError
        var hintStr = ""
        SdkCall.execute {
            val soundLayingListener = object : SoundPlayingListener()
            {
                override fun notifyComplete(errorCode: Int, hint: String)
                {
                    wasPlayed = true
                    error = errorCode
                    hintStr = hint
                    launch {
                        channel.send(Unit)
                    }
                }
            }
            val prefs = SoundPlayingPreferences()
            prefs.delay = 1000
            SoundPlayingService.playText(
                "Mind your speed!",
                soundLayingListener,
                prefs
            )
        }
        //5min limit
        withTimeout(300000) {
            channel.receive()
            assert(error == GemError.NoError) { "${GemError.getMessage(error)} HINT: $hintStr" }
            assert(wasPlayed) { "Sound not played" }
        }
    }

    // -------------------------------------------------------------------------------------------------
    override fun onTTSPlayerInitialized()
    {
        if (!ttsPlayerIsInitialized)
        {
            ttsPlayerIsInitialized = true
            runBlocking { classChannel.send(Unit) }
        }
    }
}
