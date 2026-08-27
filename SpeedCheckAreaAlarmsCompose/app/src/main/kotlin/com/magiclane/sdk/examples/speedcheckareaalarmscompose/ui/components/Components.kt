/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.speedcheckareaalarmscompose.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.alarm.AlarmPanel
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.compose.map.ObstructionEdge
import com.magiclane.sdk.compose.map.mapObstruction
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.compose.ui.FollowGpsButton
import com.magiclane.sdk.compose.ui.LoadingOverlay
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.speedcheckareaalarmscompose.R
import com.magiclane.sdk.examples.speedcheckareaalarmscompose.SpeedCheckAreaAlarmsModel
import com.magiclane.sdk.util.Util
import kotlin.math.roundToInt

// Fraction of the screen width the alarm panel occupies in landscape, leaving the
// right half of the screen free for the GPS arrow.
private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.5f

// Box the section entry gantry's icon is rendered into (the panel's icon height).
private val AlarmIconSize = 64.dp

@Composable
fun SpeedCheckAreaAlarmsApp(modifier: Modifier = Modifier, viewModel: SpeedCheckAreaAlarmsModel = viewModel()) {
    val context = LocalContext.current

    // Map hosting, SDK listeners and the alarm panel come from the maps-compose
    // library; this app owns the navigation simulation and the alarm service.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()

    val alarmIconSizePx = with(LocalDensity.current) { AlarmIconSize.roundToPx() }

    // Start the simulation once the SDK map data is available.
    LaunchedEffect(sdkState.isMapDataReady) {
        if (sdkState.isMapDataReady) {
            viewModel.startSimulation(mapState, alarmIconSizePx)
        }
    }

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    // The example needs the network for the route and the safety cameras overlay;
    // warn once if there is none.
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

            SpeedCheckAreaAlarmsScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                mapState = mapState,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
fun SpeedCheckAreaAlarmsScreen(
    modifier: Modifier = Modifier,
    mapState: GemMapState,
    viewModel: SpeedCheckAreaAlarmsModel,
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // The alarm panel shows only while the map follows the driven position -
    // browsing the map hides it, exactly like the View-based SpeedCheckAreaAlarms
    // example. The panel data survives browsing, so re-entering follow position
    // mode restores the panel.
    val hasAlarm = viewModel.alarmPanelData.title.isNotEmpty() ||
        viewModel.alarmPanelData.text.isNotEmpty()
    val panelVisible = hasAlarm && mapState.isFollowingPosition

    // The GPS arrow stays horizontally centered in the map area not covered by the
    // panel: while the panel covers the left half of a landscape screen, the center
    // of the free right half; 0.5 otherwise. isMapReady is a key because postToMap
    // drops the call while the map view is not attached yet.
    val landscapeFocusX = landscapeCameraFocusX()
    LaunchedEffect(mapState.isMapReady, isLandscape, panelVisible, landscapeFocusX) {
        viewModel.applyCameraFocus(
            mapState,
            x = if (isLandscape && panelVisible) landscapeFocusX else 0.5f,
        )
    }

    Box(modifier) {
        if (panelVisible) {
            // The library alarm panel spans the whole screen width in portrait and
            // only the left half in landscape; the map obstruction lifts the Magic
            // Lane logo above it.
            AlarmPanel(
                data = viewModel.alarmPanelData,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(if (isLandscape) LANDSCAPE_PANEL_WIDTH_FRACTION else 1f)
                    .padding(10.dp)
                    .mapObstruction(mapState, ObstructionEdge.Bottom),
                iconSize = AlarmIconSize,
            )
        }

        FollowGpsButton(
            mapState = mapState,
            // GPS positions exist only while the simulation runs; before it starts
            // the map is not following anything, so the button would show up right away.
            visible = viewModel.isNavigating,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            // The screen already applies the safe-drawing insets.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        LoadingOverlay(visible = viewModel.progressBarIsVisible)
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

/**
 * Horizontal camera focus in landscape: the middle of the free map area right of the
 * panel, as a fraction of the full window width. Only the left inset matters: a left
 * system bar / cutout offsets the inset-padded panel, while on the right the map
 * keeps drawing edge-to-edge under the bar, so the free area ends at the window edge.
 */
private fun landscapeCameraFocusXFraction(leftInsetPx: Int, contentWidthPx: Int, windowWidthPx: Int): Float {
    val panelRight = leftInsetPx + (contentWidthPx * LANDSCAPE_PANEL_WIDTH_FRACTION).roundToInt()
    return (panelRight + windowWidthPx) / 2f / windowWidthPx
}

/** The landscape camera focus X for the current window size and insets. */
@Composable
private fun landscapeCameraFocusX(): Float {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val leftInsetPx = insets.getLeft(density, layoutDirection)
    val rightInsetPx = insets.getRight(density, layoutDirection)
    val windowWidthPx = LocalResources.current.displayMetrics.widthPixels
    return landscapeCameraFocusXFraction(
        leftInsetPx = leftInsetPx,
        contentWidthPx = windowWidthPx - leftInsetPx - rightInsetPx,
        windowWidthPx = windowWidthPx,
    )
}
