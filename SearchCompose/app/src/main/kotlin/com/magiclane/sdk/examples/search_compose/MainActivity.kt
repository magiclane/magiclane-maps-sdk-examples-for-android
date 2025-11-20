/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.search_compose

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.search_compose.ui.components.SearchScreen
import com.magiclane.sdk.examples.search_compose.ui.theme.SearchTheme
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.Util

/**
 * Clean MainActivity following MVVM architecture principles.
 * Responsibilities:
 * - SDK initialization and lifecycle management
 * - Permission handling
 * - UI composition
 * - Delegating business logic to ViewModel
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    private lateinit var viewModel: SearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeSdk()
        setupContent()
        setupSdkCallbacks()
        requestPermissions()
        setupBackPressHandler()
    }

    private fun initializeSdk() {
        if (!GemSdk.initSdkWithDefaults(this)) {
            finish()
        }
    }

    private fun setupContent() {
        setContent {
            SearchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isSystemInDarkTheme())  MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                ) {
                    viewModel = viewModel()
                    initializeViewModel()

                    SearchScreen(
                        viewModel = viewModel,
                        onTextChanged = { text: String ->
                            // Delegate to ViewModel's business logic
                            viewModel.applyFilter(text, this@MainActivity)
                        }
                    )
                }
            }
        }
    }

    private fun initializeViewModel() {
        viewModel.size = resources.getDimension(R.dimen.image_size).toInt()
        
        if (!Util.isInternetConnected(this@MainActivity)) {
            viewModel.statusMessage = "Please connect to the internet!"
        }
    }

    private fun setupSdkCallbacks() {
        SdkSettings.onApiTokenRejected = {
            viewModel.errorMessage = "Token rejected!"
        }

        SdkSettings.onConnectionStatusUpdated = { connected ->
            viewModel.connected = connected

            if (viewModel.filter.isBlank()) {
                viewModel.statusMessage = if (connected) {
                    ""
                } else {
                    "Please connect to the internet!"
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            this,
            permissions
        )
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            GemSdk.release()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SearchTheme {
        Greeting("Android")
    }
}
