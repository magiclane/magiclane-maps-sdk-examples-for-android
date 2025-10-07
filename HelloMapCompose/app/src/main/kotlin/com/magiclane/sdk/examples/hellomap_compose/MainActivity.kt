// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.hellomap_compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.hellomap_compose.ui.theme.HelloMapComposeTheme
import com.magiclane.sdk.util.Util

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        configureWindow()

        if (!GemSdk.initSdkWithDefaults(this)) {
            // The SDK initialization was not completed.
            finish()
            return
        }
        
        setContent {
            HelloMapComposeTheme {
                MainApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GemSdk.release() // Release the SDK.
    }

    private fun configureWindow() {
        val window = this.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }
}

@Composable
fun MainApp() {
    val view = LocalView.current
    
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(Util.isInternetConnected(view.context)) }

    LaunchedEffect(Unit) {
        SdkSettings.onMapDataReady = { isLoading = false }
        if (SdkSettings.isMapDataReady) {
            isLoading = false
        }
        SdkSettings.onConnectionStatusUpdated = { connected ->
            isConnected = connected
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar() },
        contentWindowInsets = WindowInsets.systemBars,
        containerColor = Color.Black
    ) { innerPadding ->
        MainContent(
            innerPadding = innerPadding,
            isLoading = isLoading,
            isConnected = isConnected
        )
    }
}

@Composable
fun MainContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    isLoading: Boolean,
    isConnected: Boolean
) {
    val contentModifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
    
    GEMMap(contentModifier)
    
    when {
        !isConnected -> NoInternetScreen(contentModifier)
        isLoading -> LoadingScreen(contentModifier)
    }
}

@Composable
fun GEMMap(modifier: Modifier = Modifier) {
    AndroidView(modifier = modifier, factory = { context ->
        GemSurfaceView(context)
    })
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
fun TopAppBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(60.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) { Text("Hello Map", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
fun NoInternetScreen(modifier: Modifier = Modifier) {
    Surface(modifier) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_internet_connection),
                textAlign = TextAlign.Center,
                color = Color.Red
            )
        }
    }
}

@Preview
@Composable
private fun TopAppBarPreview() {
    HelloMapComposeTheme {
        TopAppBar()
    }
}

@Preview()
@Composable
private fun NoInternetScreenPreview() {
    HelloMapComposeTheme {
        NoInternetScreen(Modifier.fillMaxSize())
    }
}

@Preview
@Composable
private fun LoadingScreenPreview() {
    HelloMapComposeTheme {
        LoadingScreen(modifier = Modifier.fillMaxSize())
    }
}
