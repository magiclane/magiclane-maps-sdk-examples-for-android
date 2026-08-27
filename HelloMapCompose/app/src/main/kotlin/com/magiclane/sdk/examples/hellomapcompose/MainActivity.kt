/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellomapcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.compose.ui.LoadingOverlay
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the app draw edge-to-edge with light (white) status-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            MagicLaneTheme {
                MainApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GemSdk.release()

        exitProcess(0)
    }
}

@Composable
fun MainApp() {
    val viewModel: MainViewModel = viewModel()
    val uiState = viewModel.uiState
    val context = LocalContext.current

    // The map surface lifecycle, SDK init callbacks and the Magic Lane logo inset
    // alignment are all handled by the maps-compose library.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.onSdkError(context.getString(R.string.token_rejected_message))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        GemMap(
            modifier = Modifier.fillMaxSize(),
            mapState = mapState,
            sdkState = sdkState,
            onMapReady = { viewModel.onMapReady() },
            onSdkInitFailed = { errorCode ->
                viewModel.onSdkError(
                    context.getString(
                        R.string.sdk_initialization_failed,
                        GemError.getMessage(errorCode, context),
                    ),
                )
            },
        )
        LoadingOverlay(visible = uiState.isLoading)
        MapTopAppBar()
    }

    if (uiState.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = uiState.errorMessage,
            onDismiss = { viewModel.dismissError() },
            title = null,
            confirmText = stringResource(R.string.close_button),
        )
    }
}

@Composable
fun MapTopAppBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Preview(name = "TopAppBar – Light", showBackground = true)
@Composable
private fun MapTopAppBarPreview() {
    MagicLaneTheme {
        MapTopAppBar()
    }
}

@Preview(name = "TopAppBar – Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapTopAppBarDarkPreview() {
    MagicLaneTheme(darkTheme = true) {
        MapTopAppBar()
    }
}

@Preview(name = "Loading Screen", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LoadingScreenPreview() {
    MagicLaneTheme {
        LoadingOverlay(visible = true)
    }
}
