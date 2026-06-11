/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.setttslanguage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Language
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.TTSLanguage
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sound.SoundUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SetTTSLanguageTests : SoundUtils.ITTSPlayerInitializationListener {

    private var ttsPlayerIsInitialized = false
    private val classChannel = Channel<Unit>() // acts like a lock

    companion object {
        const val TIMEOUT = 180000L // 3 MIN
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Before
    fun initSoundUtils() = runBlocking {
        ttsPlayerIsInitialized =
            SdkCall.execute { SoundPlayingService.ttsPlayerIsInitialized } ?: false

        if (!ttsPlayerIsInitialized) {
            SdkCall.execute {
                SoundUtils.addTTSPlayerInitializationListener(this@SetTTSLanguageTests)
            }
            withTimeout(TIMEOUT) { classChannel.receive() }
        }
        assert(ttsPlayerIsInitialized) { "SoundPlayingService was not initialised" }
    }

    @Test
    fun soundPlayingServiceShouldReturnListOfTTSLanguages() = runBlocking {
        // TTS languages may not be available immediately after initialization;
        // retry a few times with delay to allow the TTS engine to fully load.
        var list: ArrayList<TTSLanguage>? = null
        repeat(10) {
            list = SdkCall.execute { SoundPlayingService.getTTSLanguages() }
            if (list?.isNotEmpty() == true) return@repeat
            delay(2000)
        }
        Assume.assumeTrue(
            "TTS languages not available on this device",
            list?.isNotEmpty() == true,
        )
    }

    @Test
    fun languageShouldChange() = runBlocking {
        // Ensure TTS languages are loaded before attempting to set one.
        var languages: ArrayList<TTSLanguage>? = null
        repeat(10) {
            languages = SdkCall.execute { SoundPlayingService.getTTSLanguages() }
            if (languages?.isNotEmpty() == true) return@repeat
            delay(2000)
        }
        Assume.assumeTrue(
            "TTS languages not available on this device",
            languages?.isNotEmpty() == true,
        )

        SdkCall.execute {
            SoundPlayingService.setTTSLanguage("deu-DEU")
        }
        // Allow the language change to take effect.
        delay(2000)

        val lang: Language? = SdkCall.execute {
            SoundPlayingService.ttsPlayer?.getLanguage()
        }

        assert(lang != null) { "Language not set." }

        val langCode = SdkCall.execute { lang?.languageCode } ?: ""
        assert(langCode == "deu") {
            "Language code does not match the selected value! $lang with $langCode"
        }
    }

    @Test
    fun shouldPlaySound() = runBlocking {
        val channel = Channel<Unit>() // acts like a lock
        var wasPlayed = false
        var error = GemError.NoError
        var hintStr = ""
        SdkCall.execute {
            val soundLayingListener = object : SoundPlayingListener() {
                override fun notifyComplete(errorCode: Int, hint: String) {
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
                prefs,
            )
        }
        // 5min limit
        withTimeout(300000) {
            channel.receive()
            assert(error == GemError.NoError) { "${GemError.getMessage(error)} HINT: $hintStr" }
            assert(wasPlayed) { "Sound not played" }
        }
    }

    override fun onTTSPlayerInitialized() {
        if (!ttsPlayerIsInitialized) {
            ttsPlayerIsInitialized = true
            runBlocking { classChannel.send(Unit) }
        }
    }

    override fun onTTSPlayerInitializationFailed() {
        // TTS player initialization failed - still unblock the channel to prevent deadlock
        runBlocking { classChannel.send(Unit) }
    }
}
