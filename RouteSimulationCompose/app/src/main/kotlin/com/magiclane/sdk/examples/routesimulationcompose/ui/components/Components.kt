/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimulationcompose.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.navigation.EtaPanel
import com.magiclane.sdk.compose.components.navigation.LanePanel
import com.magiclane.sdk.compose.components.navigation.NavigationInstructionPanel
import com.magiclane.sdk.compose.components.navigation.NavigationUiState
import com.magiclane.sdk.compose.components.navigation.TrafficBanner
import com.magiclane.sdk.compose.components.navigation.rememberNavigationUiState
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
import com.magiclane.sdk.examples.routesimulationcompose.LaneInfoPlacement
import com.magiclane.sdk.examples.routesimulationcompose.R
import com.magiclane.sdk.examples.routesimulationcompose.RouteSimulationModel
import com.magiclane.sdk.util.Util
import kotlin.math.roundToInt

// Fraction of the screen width the navigation panels occupy in landscape.
private const val LANDSCAPE_PANELS_WIDTH_FRACTION = 0.45f

@Composable
fun RouteSimulationApp(modifier: Modifier = Modifier, viewModel: RouteSimulationModel = viewModel()) {
    val context = LocalContext.current

    // Map hosting, SDK listeners and the whole navigation UI pipeline come from the
    // maps-compose library.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()
    val navState = rememberNavigationUiState(
        navigationService = viewModel.navigationService,
        mapState = mapState,
        onNavigationEnded = { errorCode -> viewModel.onNavigationEnded(errorCode) },
    )

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Start the simulation once the SDK map data is available.
    LaunchedEffect(sdkState.isMapDataReady) {
        if (sdkState.isMapDataReady) {
            viewModel.startSimulation(navState.listener)
        }
    }

    // Only the standalone bottom lane panel can cover the followed position; the
    // top-panel placement never needs the elevated camera focus.
    val hasLanePanel = navState.laneImage != null &&
        viewModel.laneInfoPlacement == LaneInfoPlacement.NearEtaPanel
    // isMapReady is a key because postToMap drops the call while the map view is not
    // attached yet: the focus must be re-applied on attach so it is already correct
    // when navigation starts following the position.
    val landscapeFocusX = landscapeCameraFocusX()
    LaunchedEffect(mapState.isMapReady, isLandscape, navState.isNavigating, hasLanePanel, landscapeFocusX) {
        viewModel.applyCameraFocus(mapState, isLandscape, hasLanePanel, landscapeFocusX)
    }

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    // The example needs the network to calculate the route; warn once if there is none.
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
                        R.string.route_simulation_error,
                        GemError.getMessage(errorCode, context),
                    )
                },
            )

            RouteSimulationScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                mapState = mapState,
                navState = navState,
                viewModel = viewModel,
                isLandscape = isLandscape,
            )
        }
    }
}

@Composable
fun RouteSimulationScreen(
    modifier: Modifier = Modifier,
    mapState: GemMapState,
    navState: NavigationUiState,
    viewModel: RouteSimulationModel,
    isLandscape: Boolean,
) {
    // Panels are shown while navigating and following the position (browsing the map
    // hides them, exactly like the View-based RouteNavigation example).
    val panelsVisible = navState.isNavigating && mapState.isFollowingPosition

    val endOfSectionIcon = rememberEndOfSectionIcon()

    Box(modifier) {
        if (panelsVisible) {
            Column(
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(LANDSCAPE_PANELS_WIDTH_FRACTION)
                        // The panel column blocks the whole start side in landscape.
                        .mapObstruction(mapState, ObstructionEdge.Start)
                } else {
                    Modifier.fillMaxSize()
                },
            ) {
                Column(
                    modifier = if (isLandscape) {
                        Modifier
                    } else {
                        Modifier.mapObstruction(mapState, ObstructionEdge.Top)
                    },
                ) {
                    NavigationInstructionPanel(
                        data = navState.panel,
                        laneImage = navState.laneImage
                            .takeIf { viewModel.laneInfoPlacement == LaneInfoPlacement.TopPanel },
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    )

                    navState.trafficBanner?.let { banner ->
                        TrafficBanner(
                            data = banner,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            endOfSectionIcon = endOfSectionIcon,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!isLandscape && viewModel.laneInfoPlacement == LaneInfoPlacement.NearEtaPanel) {
                    // Lane guidance as a standalone content-sized panel above the ETA
                    // panel (the ETA panel's 10.dp padding provides the gap between them).
                    LanePanel(
                        laneImage = navState.laneImage,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                EtaPanel(
                    data = navState.eta,
                    modifier = Modifier
                        .padding(10.dp)
                        .let { if (isLandscape) it else it.mapObstruction(mapState, ObstructionEdge.Bottom) },
                )
            }

            if (isLandscape && viewModel.laneInfoPlacement == LaneInfoPlacement.NearEtaPanel) {
                // Lane guidance over the map, centered on the followed position and
                // bottom-aligned with the ETA panel.
                LanePanel(
                    laneImage = navState.laneImage,
                    modifier = Modifier.landscapeLanePanelPosition(),
                )
            }
        }

        FollowGpsButton(
            mapState = mapState,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 10.dp, vertical = 5.dp),
            // GPS positions exist only while the simulation runs; before it starts the
            // map is not following anything, so the button would show up right away.
            visible = navState.isNavigating,
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
 * panel column, as a fraction of the full window width. Only the left inset matters: a
 * left system bar / cutout offsets the inset-padded panel column, while on the right the
 * map keeps drawing edge-to-edge under the bar, so nothing is offset there and the free
 * area ends at the window edge.
 */
private fun landscapeCameraFocusXFraction(leftInsetPx: Int, contentWidthPx: Int, windowWidthPx: Int): Float {
    val panelRight = leftInsetPx + (contentWidthPx * LANDSCAPE_PANELS_WIDTH_FRACTION).roundToInt()
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

/**
 * Positions the landscape lane panel in the free space between the panel column and
 * the right screen edge (already inset from the system bar / cutout), minus a small
 * margin on each side, which also caps the panel's width. Bottom-aligned with the
 * ETA panel. Horizontally the panel centers on the followed position (the camera
 * focus is a fraction of the full window width, so the screen's safe-drawing insets
 * are added back before converting it to local coordinates); when it doesn't fit
 * there — it would cover the ETA panel or the right margin — it centers in the free
 * space instead.
 */
@Composable
private fun Modifier.landscapeLanePanelPosition(): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val leftInsetPx = insets.getLeft(density, layoutDirection)
    val rightInsetPx = insets.getRight(density, layoutDirection)
    val edgeGapPx = with(density) { 10.dp.roundToPx() }

    return layout { measurable, constraints ->
        val minX = (constraints.maxWidth * LANDSCAPE_PANELS_WIDTH_FRACTION).roundToInt() + edgeGapPx
        val maxRight = constraints.maxWidth - edgeGapPx
        val availableWidth = (maxRight - minX).coerceAtLeast(0)

        val placeable = measurable.measure(
            Constraints(maxWidth = availableWidth, maxHeight = constraints.maxHeight),
        )

        val windowWidth = leftInsetPx + constraints.maxWidth + rightInsetPx
        val focusX = (
            windowWidth * landscapeCameraFocusXFraction(leftInsetPx, constraints.maxWidth, windowWidth)
            ).roundToInt() - leftInsetPx
        val centeredOnGps = focusX - placeable.width / 2
        val x = if (centeredOnGps >= minX && centeredOnGps + placeable.width <= maxRight) {
            centeredOnGps
        } else {
            minX + (availableWidth - placeable.width) / 2
        }
        // The ETA panel sits 10.dp (its own padding) above the screen bottom.
        val y = constraints.maxHeight - edgeGapPx - placeable.height

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(x, y)
        }
    }
}

@Composable
private fun rememberEndOfSectionIcon(): ImageBitmap? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 44.dp.roundToPx() }
    return remember(sizePx) {
        ContextCompat.getDrawable(context, R.drawable.end_of_traffic_section)
            ?.toBitmap(sizePx, sizePx)
            ?.asImageBitmap()
    }
}
