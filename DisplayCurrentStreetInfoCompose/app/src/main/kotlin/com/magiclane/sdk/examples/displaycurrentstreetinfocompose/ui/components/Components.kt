/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.displaycurrentstreetinfocompose.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.navigation.CurrentStreetPanel
import com.magiclane.sdk.compose.components.navigation.SpeedLimitSign
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.permission.LocationPermissionState
import com.magiclane.sdk.compose.permission.rememberLocationPermissionState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.compose.ui.FollowGpsButton
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.d3scene.EWatermarkPosition
import com.magiclane.sdk.examples.displaycurrentstreetinfocompose.DisplayCurrentStreetInfoModel
import com.magiclane.sdk.examples.displaycurrentstreetinfocompose.R
import com.magiclane.sdk.util.Util

private const val WATERMARK_LOGO_SIZE_MM = 20f

@Composable
fun DisplayCurrentStreetInfoApp(modifier: Modifier = Modifier, viewModel: DisplayCurrentStreetInfoModel = viewModel()) {
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

    // Place the followed-position arrow per orientation: in landscape it moves up so it
    // does not hide behind the bottom-centered street panel.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(mapState.isMapReady, isLandscape) {
        if (mapState.isMapReady) {
            viewModel.applyCameraFocus(mapState, isLandscape)
        }
    }

    // Show the Magic Lane logo in the top-left corner of the map (the SDK default is
    // bottom-left); 20 mm is the SDK default size, only the corner changes.
    LaunchedEffect(mapState.isMapReady) {
        if (mapState.isMapReady) {
            mapState.postToMap { map ->
                map.setWatermarkLogoProperties(EWatermarkPosition.EWPTopLeft, WATERMARK_LOGO_SIZE_MM)
            }
        }
    }

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    // The example needs the network for the map and the road address data; warn once
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

            // Only the vertical insets pad the whole screen: the horizontal ones are
            // asymmetric in landscape (display cutout on one side), and padding them
            // here would shift the content box so the bottom-centered street panel
            // would no longer sit at half of the screen width. The corner-anchored
            // controls apply the horizontal insets themselves instead.
            DisplayCurrentStreetInfoScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
                mapState = mapState,
                permissionState = permissionState,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
fun DisplayCurrentStreetInfoScreen(
    modifier: Modifier = Modifier,
    mapState: GemMapState,
    permissionState: LocationPermissionState,
    viewModel: DisplayCurrentStreetInfoModel,
) {
    // The screen itself is only padded vertically, so the edge-anchored controls stay
    // clear of the cutout/navigation bar on their own; the bottom-centered street
    // panel skips them on purpose to stay at half of the screen width.
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

    Box(modifier) {
        // The street info describes the followed position, so it hides as soon as the
        // user pans away from it.
        if (mapState.isFollowingPosition) {
            if (viewModel.speedLimitText.isNotEmpty()) {
                SpeedLimitSign(
                    text = viewModel.speedLimitText,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(horizontalInsets)
                        .padding(8.dp),
                )
            }

            // The library current-street panel sits at the bottom center and stays
            // hidden until a position update carries a street name.
            CurrentStreetPanel(
                data = viewModel.currentStreetData,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            )
        }

        FollowGpsButton(
            mapState = mapState,
            visible = permissionState.hasLocationPermission,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            // The screen already applies the vertical safe-drawing insets.
            windowInsets = horizontalInsets,
        )
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            onDismiss = { viewModel.errorMessage = "" },
            title = null,
            confirmText = stringResource(R.string.ok),
        )
    } else if (viewModel.infoMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.infoMessage,
            onDismiss = { viewModel.infoMessage = "" },
            title = stringResource(R.string.info),
            confirmText = stringResource(R.string.ok),
        )
    }
}
