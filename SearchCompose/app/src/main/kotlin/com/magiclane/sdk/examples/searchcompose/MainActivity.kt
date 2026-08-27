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
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.examples.searchcompose.ui.SearchApp
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Thin UI layer: initializes the SDK and delegates everything else to the SearchApp
// composable (the SDK listeners and the search pipeline come from maps-compose).
class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.filter { it.value }.keys.toList()
        val rejected = permissions.filter { !it.value }.keys.toList()
        PermissionsHelper.onRequestPermissionsResult(granted, rejected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status-bar icons on the purple toolbar background.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        // Released once the SDK reports its map data ready (see SearchApp).
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
        }

        setContent {
            MagicLaneTheme {
                SearchApp(viewModel = viewModel, onFatalDismiss = ::finish)
            }
        }
    }

    override fun onDestroy() {
        GemSdk.release()
        super.onDestroy()
        exitProcess(0)
    }

    private fun requestPermissions() {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
}
