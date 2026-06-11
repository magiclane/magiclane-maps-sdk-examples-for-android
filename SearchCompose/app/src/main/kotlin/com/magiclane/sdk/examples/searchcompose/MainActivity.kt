/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchcompose

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.searchcompose.ui.SearchScreen
import com.magiclane.sdk.examples.searchcompose.ui.theme.SearchComposeTheme
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Thin UI layer: sets up SDK lifecycle, delegates all UI to SearchScreen composable.
class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.filter { it.value }.keys.toList()
        val rejected = permissions.filter { !it.value }.keys.toList()
        PermissionsHelper.onRequestPermissionsResult(granted, rejected)
    }

    // Handles the async result of SDK token verification.
    private val checkAuthorizationListener = ProgressListener.create(
        onCompleted = { errorCode, _ ->
            if (errorCode != GemError.NoError) showInvalidTokenDialog()
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status-bar icons on the purple toolbar background.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        val imageSize = resources.getDimensionPixelSize(R.dimen.list_image_size)
        val iconSize = resources.getDimensionPixelSize(R.dimen.category_icon_size)
        viewModel.initialize(imageSize, iconSize)

        // Keep the idling resource busy until the SDK map data is ready.
        EspressoIdlingResource.increment()

        val initResult = GemSdk.initSdkWithDefaults(this)
        if (initResult != GemError.NoError) {
            viewModel.showFatalError(
                getString(
                    R.string.sdk_initialization_failed,
                    SdkCall.runSynced {
                        GemError.getMessage(initResult, this)
                    },
                ),
            )
        } else {
            requestPermissions()

            if (!Util.isInternetConnected(this)) {
                viewModel.showInfoError(getString(R.string.internet_required))
            }

            registerSdkListeners()
        }

        setContent {
            SearchComposeTheme {
                SearchScreen(viewModel = viewModel, onFatalDismiss = ::finish)
            }
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        GemSdk.release()
        super.onDestroy()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        // Self-clearing listener: fires once when the SDK map data is ready, then removes itself.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                runOnAliveUi {
                    viewModel.onSdkReady()
                    EspressoIdlingResource.decrement()
                }
            }
        }

        SdkSettings.onApiTokenRejected = { showInvalidTokenDialog() }

        // Verify the app token on the first successful internet connection.
        // Self-clearing so it fires only once per session.
        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute { SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener) }
                } ?: showInvalidTokenDialog()
                SdkSettings.onConnectionStatusUpdated = {}
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onConnectionStatusUpdated = {}
    }

    private fun requestPermissions() {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun showInvalidTokenDialog() {
        runOnAliveUi { viewModel.showFatalError(getString(R.string.invalid_token)) }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
