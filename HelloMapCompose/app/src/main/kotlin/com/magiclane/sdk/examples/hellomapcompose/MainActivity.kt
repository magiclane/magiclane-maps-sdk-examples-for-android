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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.hellomapcompose.ui.theme.HelloMapComposeTheme
import com.magiclane.sdk.util.SdkCall
import kotlin.system.exitProcess

// System window insets the Magic Lane logo should stay clear of.
private val SYSTEM_INSET_TYPES =
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the app draw edge-to-edge with light (white) status-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            HelloMapComposeTheme {
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        MainContent(uiState = uiState, viewModel = viewModel)
        MapTopAppBar()
    }

    if (uiState.errorMessage.isNotEmpty()) {
        ErrorDialog(uiState.errorMessage) { viewModel.dismissError() }
    }
}

@Composable
private fun MainContent(uiState: UiState, viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        GEMMap(modifier = Modifier.fillMaxSize(), viewModel = viewModel)
        if (uiState.isLoading) {
            LoadingScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GEMMap(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    AndroidView(modifier = modifier, factory = { context ->
        SdkSettings.onApiTokenRejected = {
            viewModel.onSdkError(context.getString(R.string.token_rejected_message))
        }

        GemSurfaceView(context).apply {
            onSdkInitFailed = { error ->
                viewModel.onSdkError(
                    context.getString(
                        R.string.sdk_initialization_failed,
                        GemError.getMessage(error, context),
                    ),
                )
            }
            onDefaultMapViewCreated = {
                // Align the Magic Lane logo with system window insets on first map creation.
                updateFocusViewport()
                viewModel.onMapReady()
            }
            // Re-align the logo whenever the surface is resized (e.g. rotation).
            onSurfaceChanged = { _, _ ->
                updateFocusViewport()
            }
        }
    })
}

// Adjusts the Magic Lane logo position to respect system window insets.
private fun GemSurfaceView.updateFocusViewport() {
    SdkCall.runSynced {
        val mapView = mapView ?: return@runSynced
        val viewport = mapView.viewport ?: return@runSynced
        val insets = ViewCompat.getRootWindowInsets(this)?.getInsets(SYSTEM_INSET_TYPES)

        val w = viewport.width
        val h = viewport.height
        val left = insets?.left ?: 0
        val top = insets?.top ?: 0
        val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
        mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

@Composable
fun ErrorDialog(errorMessage: String, onDismiss: () -> Unit) {
    AlertDialog(
        text = { Text(text = errorMessage) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_button))
            }
        },
    )
}

@Preview(name = "TopAppBar – Light", showBackground = true)
@Composable
private fun MapTopAppBarPreview() {
    HelloMapComposeTheme {
        MapTopAppBar()
    }
}

@Preview(name = "TopAppBar – Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapTopAppBarDarkPreview() {
    HelloMapComposeTheme(darkTheme = true) {
        MapTopAppBar()
    }
}

@Preview(name = "Loading Screen", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LoadingScreenPreview() {
    HelloMapComposeTheme {
        LoadingScreen(modifier = Modifier.fillMaxSize())
    }
}
