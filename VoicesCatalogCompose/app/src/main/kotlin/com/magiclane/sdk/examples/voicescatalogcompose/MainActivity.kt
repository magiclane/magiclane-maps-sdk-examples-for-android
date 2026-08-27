/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalogcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.core.EServiceGroupType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

class MainActivity : ComponentActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private val viewModel: VoicesCatalogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MagicLaneTheme {
                VoicesCatalogApp(modifier = Modifier.fillMaxSize())
            }
        }

        registerSdkListeners()

        // This example has no map view, so the SDK must be initialized explicitly.
        val errorCode = GemSdk.initSdkWithDefaults(this)
        if (errorCode != GemError.NoError) {
            viewModel.errorMessage = getString(
                R.string.sdk_initialization_failed,
                SdkCall.runSynced { GemError.getMessage(errorCode, this) },
            )
        } else {
            // Voices downloaded in previous sessions (and the applied guidance voice)
            // are available right away; only the online catalog needs to wait for the
            // content service availability.
            viewModel.refreshLocalContent()

            // The device engine may have finished initializing before the listener
            // registration; the languages are loaded now, or from the listener.
            if (SdkCall.runSynced { SoundPlayingService.ttsPlayerIsInitialized } == true) {
                viewModel.onTtsPlayerReady()
            }
        }

        if (!Util.isInternetConnected(this)) {
            viewModel.errorMessage = getString(R.string.internet_required)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // The device Text-to-Speech engine initializes asynchronously; its languages can
    // only be listed (and the default TTS selection applied) once it reports ready.
    override fun onTTSPlayerInitialized() = viewModel.onTtsPlayerReady()

    override fun onTTSPlayerInitializationFailed() = viewModel.onTtsPlayerUnavailable()

    // Registers all SDK-level callbacks.
    private fun registerSdkListeners() {
        // Must be registered before initSdkWithDefaults so an engine that initializes
        // during SDK init is not missed.
        SoundUtils.addTTSPlayerInitializationListener(this)
        // The content-download service connection drives the online state: gained, it
        // serves the catalog request the online tab may be waiting for; lost, it parks
        // the online tab on its "no internet" report.
        SdkSettings.onServiceStatusUpdated = { service, connected ->
            if (service == EServiceGroupType.ContentService) {
                Util.postOnMain { viewModel.onContentServiceStatus(connected) }
            }
        }

        SdkSettings.onApiTokenRejected = {
            Util.postOnMain { viewModel.errorMessage = getString(R.string.token_rejected_message) }
        }
    }

    // Clears SDK-level callbacks to prevent them reaching a destroyed activity.
    private fun clearSdkListeners() {
        SoundUtils.removeTTSPlayerInitializationListener(this)
        SdkSettings.onServiceStatusUpdated = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
    }
}
