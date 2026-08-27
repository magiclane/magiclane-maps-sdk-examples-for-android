/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.overspeedttswarningcompose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.navigation.SpeedPanel
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.permission.LocationPermissionState
import com.magiclane.sdk.compose.permission.rememberLocationPermissionState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.compose.ui.FollowGpsButton
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.overspeedttswarningcompose.OverspeedTTSWarningModel
import com.magiclane.sdk.examples.overspeedttswarningcompose.R
import com.magiclane.sdk.util.Util

@Composable
fun OverspeedTTSWarningApp(modifier: Modifier = Modifier, viewModel: OverspeedTTSWarningModel = viewModel()) {
    val context = LocalContext.current

    // Map hosting, SDK listeners and the location permission flow come from the
    // maps-compose library.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()
    val permissionState = rememberLocationPermissionState(
        onGranted = { viewModel.startPositionTracking(mapState) },
    )

    // Once the SDK map data is available, start tracking the position — or ask for the
    // location permission first.
    LaunchedEffect(sdkState.isMapDataReady) {
        if (sdkState.isMapDataReady) {
            if (permissionState.hasLocationPermission) {
                viewModel.startPositionTracking(mapState)
            } else {
                permissionState.launchRequest()
            }
        }
    }

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    // The example needs the network for the map and the road speed limits; warn once
    // if there is none.
    LaunchedEffect(Unit) {
        if (!Util.isInternetConnected(context)) {
            viewModel.errorMessage = context.getString(R.string.internet_required)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GemMap(
                modifier = Modifier.fillMaxSize(),
                mapState = mapState,
                sdkState = sdkState,
                onSdkInitFailed = { errorCode ->
                    viewModel.errorMessage = context.getString(
                        R.string.sdk_initialization_failed,
                        GemError.getMessage(errorCode, context),
                    )
                },
            )

            OverspeedTTSWarningScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                mapState = mapState,
                permissionState = permissionState,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
fun OverspeedTTSWarningScreen(
    modifier: Modifier = Modifier,
    mapState: GemMapState,
    permissionState: LocationPermissionState,
    viewModel: OverspeedTTSWarningModel,
) {
    Box(modifier) {
        // The library speed panel hugs the end edge of the screen and stays empty
        // until the first position update.
        SpeedPanel(
            data = viewModel.speedPanelData,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 5.dp),
        )

        FollowGpsButton(
            mapState = mapState,
            visible = permissionState.hasLocationPermission,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            // The screen already applies the safe-drawing insets.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            onDismiss = { viewModel.errorMessage = "" },
            title = null,
            confirmText = stringResource(R.string.ok),
        )
    }
}
