/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// ActivationModesCompose
// ----------------------
// Shows every way an SDK with auto-activation can become (and stop being) activated, and how an application can
// reflect the SDK's activation state to its user.
//
// SDKs downloaded from the Magic Lane website have auto-activation enabled: given a project API token, the SDK
// activates itself the first time it can reach Magic Lane Services. That leaves two situations an application must
// handle on its own:
//
//   1. The device has no internet at all (or the application must not go online). The SDK cannot auto-activate, so it
//      reports itself as NOT ACTIVATED through GemSdk.onSdkNotActivated, and offline functionality is disabled until it
//      is activated. The application decides how to show this - here we draw a watermark text on the map, and clear it
//      again when GemSdk.onSdkActivated arrives.
//
//   2. Activating (or releasing) such a device MANUALLY, without the device ever going online - the offline ceremony:
//        a. ActivationService.getOfflineActivationRequestBlob()  -> a request blob, produced entirely on-device
//        b. take the blob to Magic Lane Services from a machine that IS online. Either scan the QR code shown here with
//           the Magic Lane companion app, or send the REST request shown here yourself - both return an
//           offline_activation_key.
//        c. ActivationService.completeOfflineActivation(key)      -> the SDK is activated, offline stays usable
//      Deactivation mirrors it with getOfflineDeactivationRequestBlob() / completeOfflineDeactivation().
//
// The example starts with the internet connection DISALLOWED so the not-activated state is visible immediately. Use
// the panel to allow the connection (auto-activation), or to walk through the manual offline ceremony. A "Reset to
// first run" button returns the device to the not-activated offline state so every scenario can be replayed.

package com.magiclane.sdk.examples.activationmodescompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.compose.ui.AdaptivePanelScaffold
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    private val viewModel: ActivationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Register for the activation-state notifications BEFORE initializing the SDK: with a token but no connection
        // the SDK reports "not activated" already during initialization. The callbacks are delivered on the main thread.
        GemSdk.onSdkNotActivated = { reason -> viewModel.onSdkNotActivated(reason) }
        GemSdk.onSdkActivated = { viewModel.onSdkActivated() }

        // Initialize the SDK here rather than letting GemMap do it, so the internet connection starts DISALLOWED - like a
        // device without connectivity - and the not-activated state (and the manual offline ceremony) can be exercised.
        // The token comes from the manifest (com.magiclane.sdk.token). GemMap picks up the already initialized SDK.
        val initCode = GemSdk.initSdkWithDefaults(this, allowOnline = false)
        if (initCode != GemError.NoError) {
            viewModel.errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(initCode, this))
        }

        // A device already activated from a previous run gets no notification at start; read the initial state.
        viewModel.refreshStatus()

        setContent {
            MagicLaneTheme {
                ActivationModesApp(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GemSdk.onSdkNotActivated = {}
        GemSdk.onSdkActivated = {}
        GemSdk.release()

        exitProcess(0)
    }
}

@Composable
fun ActivationModesApp(viewModel: ActivationViewModel) {
    val context = LocalContext.current
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    AdaptivePanelScaffold(
        mapState = mapState,
        modifier = Modifier.fillMaxSize(),
        portraitPanelMaxHeight = 480.dp,
        panel = {
            ActivationPanel(viewModel = viewModel, isOnline = sdkState.isOnline)
        },
        map = {
            GemMap(
                modifier = Modifier.fillMaxSize(),
                mapState = mapState,
                sdkState = sdkState,
                onMapReady = { viewModel.onMapReady(mapState) },
                onSdkInitFailed = { errorCode ->
                    viewModel.errorMessage = context.getString(
                        R.string.sdk_initialization_failed,
                        GemError.getMessage(errorCode, context),
                    )
                },
            )
        },
    )

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            onDismiss = { viewModel.errorMessage = "" },
            title = null,
            confirmText = stringResource(R.string.close_button),
        )
    }
}
