/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weathercompose

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.common.PanelTopBar
import com.magiclane.sdk.compose.components.details.LocationDetailsPanel
import com.magiclane.sdk.compose.components.weather.WeatherWarningRow
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.compose.map.ObstructionEdge
import com.magiclane.sdk.compose.map.mapObstruction
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.permission.rememberLocationPermissionState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.compose.ui.FollowGpsButton
import com.magiclane.sdk.compose.ui.LoadingOverlay
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.examples.weathercompose.ui.theme.WeatherTheme
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Fraction of the screen width the bottom panels occupy when shown as a bottom-left card in
// landscape.
private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.45f

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        setContent {
            WeatherTheme {
                WeatherApp(viewModel)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            // Back press first closes the full screen forecast view, then the warnings panel,
            // then dismisses the details panel (if shown), otherwise closes the app.
            when {
                viewModel.forecastType != null -> viewModel.closeForecast()
                viewModel.isWarningsPanelVisible() -> viewModel.closeWarnings()
                viewModel.isLocationDetailsVisible() -> viewModel.hideLocationDetails()
                else -> finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        GemSdk.release() // Release the SDK.
        exitProcess(0)
    }
}

@Composable
fun WeatherApp(viewModel: WeatherViewModel = viewModel()) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Map hosting, SDK listeners and the location permission flow come from the
    // maps-compose library.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()
    val permissionState = rememberLocationPermissionState(
        onGranted = { viewModel.showFollowGpsOnFirstValidPosition() },
    )

    // Once the SDK map data is available, enable the follow-GPS button — asking for the
    // location permission first if it is not granted yet.
    LaunchedEffect(sdkState.isMapDataReady) {
        if (sdkState.isMapDataReady) {
            if (permissionState.hasLocationPermission) {
                viewModel.followGpsButtonIsVisible = true
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

    // The example needs the network for the map and the weather services; warn once if there
    // is none.
    LaunchedEffect(Unit) {
        if (!Util.isInternetConnected(context)) {
            viewModel.errorMessage = context.getString(R.string.internet_required)
        }
    }

    // Entering GPS-following mode dismisses the selection panels (the follow-GPS button itself
    // hides through mapState.isFollowingPosition).
    LaunchedEffect(mapState.isFollowingPosition) {
        if (mapState.isFollowingPosition) {
            viewModel.hideLocationDetails()
        }
    }

    // Fire the pending highlight after layout (one frame after the panel has been laid out and
    // the map's free area updated for it), so the highlighted element is centered in what
    // remains visible of the map next to the panel.
    LaunchedEffect(viewModel.invokeHighlight) {
        if (viewModel.invokeHighlight) {
            withFrameNanos { }
            viewModel.invokeHighlightEffect()
        }
    }

    Box(Modifier.fillMaxSize().background(color = Color.Black)) {
        // Full-screen map. The Magic Lane logo is kept inside the visible area via the focus
        // viewport GemMap maintains from the registered map obstructions, so it never hides
        // behind the panel.
        GemMap(
            modifier = Modifier.fillMaxSize(),
            mapState = mapState,
            sdkState = sdkState,
            onMapReady = { viewModel.initialize(mapState) },
            onSdkInitFailed = { errorCode ->
                viewModel.errorMessage = context.getString(
                    R.string.sdk_initialization_failed,
                    GemError.getMessage(errorCode, context),
                )
            },
        )

        if (isLandscape) {
            // Landscape: the bottom panel is a card pinned to the bottom-left corner and the
            // follow-GPS button sits in the opposite (bottom-right) corner.
            BottomPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(LANDSCAPE_PANEL_WIDTH_FRACTION),
                viewModel = viewModel,
                mapState = mapState,
                isLandscape = true,
            )
            FollowGpsButton(
                mapState = mapState,
                visible = viewModel.followGpsButtonIsVisible,
                modifier = Modifier.align(Alignment.BottomEnd),
                windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.End + WindowInsetsSides.Bottom),
            )
        } else {
            // Portrait: the follow-GPS button is stacked directly above the full-width bottom
            // panel.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                // When a panel is visible it carries the bottom inset; the button only needs
                // it when it sits alone at the bottom of the screen.
                val buttonInsetSides = if (viewModel.isLocationDetailsVisible()) {
                    WindowInsetsSides.End
                } else {
                    WindowInsetsSides.End + WindowInsetsSides.Bottom
                }
                FollowGpsButton(
                    mapState = mapState,
                    visible = viewModel.followGpsButtonIsVisible,
                    windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(buttonInsetSides),
                )
                BottomPanel(
                    modifier = Modifier.fillMaxWidth(),
                    viewModel = viewModel,
                    mapState = mapState,
                    isLandscape = false,
                )
            }
        }

        // Centered loading indicator shown while the weather warnings are being requested.
        LoadingOverlay(visible = viewModel.progressBarIsVisible)

        // Full screen view of the requested forecast (current, daily or hourly), stacked on top
        // of everything while it is inspected.
        viewModel.forecastType?.let { type ->
            ForecastScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel,
                type = type,
            )
        }
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            onDismiss = { viewModel.errorMessage = "" },
            title = stringResource(R.string.error),
            confirmText = stringResource(R.string.ok),
        )
    }
    if (viewModel.infoMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.infoMessage,
            onDismiss = { viewModel.infoMessage = "" },
            title = stringResource(R.string.info),
            confirmText = stringResource(R.string.ok),
        )
    }
}

// Bottom panel slot: the weather warnings panel when warnings are listed, the location details
// panel otherwise. The two panels occupy the same screen area, so at most one of them is
// composed at a time (the warnings panel "stacks" over the details panel, which is revealed
// again when the warnings are closed).
@Composable
fun BottomPanel(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel,
    mapState: GemMapState,
    isLandscape: Boolean,
) {
    val warnings = viewModel.warningItems
    if (warnings != null) {
        WarningsPanel(modifier, viewModel, mapState, warnings, isLandscape)
    } else if (viewModel.locationDetailsInfo != null) {
        InfoPanel(modifier, viewModel, mapState, isLandscape)
    }
}

// The bottom panels obstruct the map along the bottom edge in portrait and, as a bottom-left
// card, along the start edge in landscape — GemMap keeps the Magic Lane logo and the camera
// centering clear of them.
private fun panelObstructionEdge(isLandscape: Boolean) = if (isLandscape) {
    ObstructionEdge.Start
} else {
    ObstructionEdge.Bottom
}

// Window inset sides the bottom panels' content must clear: the bottom always, both sides in
// portrait, but only the left in landscape (the panel hugs the left edge, so the right inset
// does not apply).
private fun panelInsetSides(isLandscape: Boolean) = if (isLandscape) {
    WindowInsetsSides.Start + WindowInsetsSides.Bottom
} else {
    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
}

// Info panel describing the tapped place, with one button per weather request that can be made
// for it. Spans the bottom edge in portrait and becomes a bottom-left card in landscape.
@Composable
fun InfoPanel(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel,
    mapState: GemMapState,
    isLandscape: Boolean,
) {
    val details = viewModel.locationDetailsInfo ?: return

    Surface(
        modifier = modifier
            .requiredHeightIn(80.dp, 350.dp)
            .mapObstruction(mapState, panelObstructionEdge(isLandscape)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(panelInsetSides(isLandscape)),
                ),
        ) {
            PanelTopBar(
                title = stringResource(R.string.location_details),
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClose = { viewModel.hideLocationDetails() },
            )

            LocationDetailsPanel(
                data = details,
                modifier = Modifier.fillMaxWidth(),
                fallbackIcon = if (details.title == stringResource(R.string.my_position)) {
                    painterResource(R.drawable.ic_current_location_arrow)
                } else {
                    null
                },
            )

            ForecastButtons(viewModel = viewModel)
        }
    }
}

// One button per weather request offered for the selected place; horizontally scrollable so all
// of them stay reachable on narrow screens.
@Composable
fun ForecastButtons(modifier: Modifier = Modifier, viewModel: WeatherViewModel) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(onClick = { viewModel.openForecast(EForecastType.CURRENT) }) {
            Text(stringResource(R.string.current_forecast))
        }
        FilledTonalButton(onClick = { viewModel.openForecast(EForecastType.DAILY) }) {
            Text(stringResource(R.string.daily_forecast))
        }
        FilledTonalButton(onClick = { viewModel.openForecast(EForecastType.HOURLY) }) {
            Text(stringResource(R.string.hourly_forecast))
        }
        FilledTonalButton(onClick = { viewModel.requestWarnings() }) {
            Text(stringResource(R.string.warnings))
        }
    }
}

// Weather warnings panel, stacked over the info panel with the same placement. Tapping a warning
// re-centers the map on its coverage polygons (all of them are already drawn).
@Composable
fun WarningsPanel(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel,
    mapState: GemMapState,
    warnings: List<WarningUiItem>,
    isLandscape: Boolean,
) {
    Surface(
        modifier = modifier
            .requiredHeightIn(80.dp, 350.dp)
            .mapObstruction(mapState, panelObstructionEdge(isLandscape)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout).only(panelInsetSides(isLandscape)),
            ),
        ) {
            PanelTopBar(
                title = stringResource(R.string.weather_warnings),
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClose = { viewModel.closeWarnings() },
            )

            LazyColumn(Modifier.fillMaxWidth()) {
                items(warnings.size) { index ->
                    val item = warnings[index]
                    WeatherWarningRow(
                        data = item.data,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectWarning(item) },
                    )
                    if (index < warnings.size - 1) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
