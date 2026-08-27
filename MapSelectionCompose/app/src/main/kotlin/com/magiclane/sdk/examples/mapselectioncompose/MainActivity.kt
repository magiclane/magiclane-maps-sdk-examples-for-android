/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.magiclane.sdk.compose.components.details.LocationDetailsData as LocationDetailsInfo
import com.magiclane.sdk.compose.components.details.LocationDetailsPanel
import com.magiclane.sdk.compose.components.details.SafetyCameraPanel
import com.magiclane.sdk.compose.components.details.SocialReportPanel
import com.magiclane.sdk.compose.components.details.TrafficEventPanel
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
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.MapSelectionTheme
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Fraction of the screen width the info panel occupies when shown as a bottom-left card in landscape.
private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.45f

// Fraction of the screen the public transport station panel occupies: its height in portrait
// (full width bottom panel) and its width in landscape (left-side card spanning the full height).
private const val PT_STATION_PANEL_FRACTION = 0.5f

class MainActivity : ComponentActivity() {

    private val viewModel: MapSelectionModel by viewModels()

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
            MapSelectionTheme {
                MapSelectionApp(viewModel)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            // Back press first closes the public transport trip view, then the station view,
            // then dismisses the details panel (if shown), otherwise closes the app.
            when {
                viewModel.ptTripViewTrips != null -> viewModel.closePublicTransportTripView()
                viewModel.ptStationInfo != null -> viewModel.closePublicTransportStationView()
                viewModel.isBottomViewVisible() -> viewModel.hideBottomView()
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
fun MapSelectionApp(viewModel: MapSelectionModel = viewModel()) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Map hosting, SDK listeners and the location permission flow come from the
    // maps-compose library.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()
    val permissionState = rememberLocationPermissionState(
        onGranted = { viewModel.showFollowGpsOnFirstValidPosition() },
    )

    // Once the SDK map data is available, calculate the example routes and enable the follow-GPS
    // button — asking for the location permission first if it is not granted yet.
    LaunchedEffect(sdkState.isMapDataReady) {
        if (sdkState.isMapDataReady) {
            if (permissionState.hasLocationPermission) {
                viewModel.followGpsButtonIsVisible = true
            } else {
                permissionState.launchRequest()
            }
            viewModel.calculateRoutes()
        }
    }

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    // The example needs the network to calculate the London–Paris routes; warn once if there
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
            viewModel.onEnterFollowingPosition()
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
        // behind the panels.
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
            // Landscape: the info panel is a card pinned to the bottom-left corner, the station
            // panel a half-width card spanning the full screen height on the left, and the
            // follow-GPS button sits in the opposite (bottom-right) corner.
            InfoPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(LANDSCAPE_PANEL_WIDTH_FRACTION),
                viewModel = viewModel,
                mapState = mapState,
                isLandscape = true,
            )
            viewModel.ptStationInfo?.let { station ->
                PublicTransportStationScreen(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(PT_STATION_PANEL_FRACTION),
                    viewModel = viewModel,
                    mapState = mapState,
                    station = station,
                    isLandscape = true,
                )
            }
            FollowGpsButton(
                mapState = mapState,
                visible = viewModel.followGpsButtonIsVisible,
                modifier = Modifier.align(Alignment.BottomEnd),
                windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.End + WindowInsetsSides.Bottom),
            )
        } else {
            // Portrait: the follow-GPS button is stacked directly above the full-width bottom
            // panel (the info panel, or the half screen station panel — never both).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                // When a panel is visible it carries the bottom inset; the button only needs
                // it when it sits alone at the bottom of the screen.
                val buttonInsetSides =
                    if (!viewModel.isBottomViewVisible() && viewModel.ptStationInfo == null) {
                        WindowInsetsSides.End + WindowInsetsSides.Bottom
                    } else {
                        WindowInsetsSides.End
                    }
                FollowGpsButton(
                    mapState = mapState,
                    visible = viewModel.followGpsButtonIsVisible,
                    windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(buttonInsetSides),
                )
                InfoPanel(
                    modifier = Modifier.fillMaxWidth(),
                    viewModel = viewModel,
                    mapState = mapState,
                    isLandscape = false,
                )
                viewModel.ptStationInfo?.let { station ->
                    PublicTransportStationScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(PT_STATION_PANEL_FRACTION),
                        viewModel = viewModel,
                        mapState = mapState,
                        station = station,
                        isLandscape = false,
                    )
                }
            }
        }

        // Centered loading indicator shown while a route is being computed.
        LoadingOverlay(visible = viewModel.progressBarIsVisible)

        // Full screen view of the trip (stations list) of a departure tapped in the station
        // panel, stacked on top of everything while it is inspected.
        viewModel.ptStationInfo?.let { station ->
            viewModel.ptTripViewTrips?.let { trips ->
                PublicTransportTripScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    trips = trips,
                    tappedIndex = viewModel.ptTripViewIndex,
                    stationUtcOffsetMs = station.utcOffsetMs,
                )
            }
        }
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

// Info panel describing the tapped map element (route, landmark, traffic event, safety camera or
// social report). Spans the bottom edge in portrait and becomes a bottom-left card in landscape.
@Composable
fun InfoPanel(
    modifier: Modifier = Modifier,
    viewModel: MapSelectionModel,
    mapState: GemMapState,
    isLandscape: Boolean,
) {
    // Title reflects the kind of element currently selected; bail out when nothing is selected.
    val title = when {
        viewModel.routeInfo != null -> R.string.route_info
        viewModel.trafficEventInfo != null -> R.string.traffic_event
        viewModel.locationDetailsInfo != null -> R.string.location_details
        viewModel.safetyCameraInfo != null -> R.string.safety_camera
        viewModel.socialReportInfo != null -> R.string.social_report
        else -> return
    }

    // Content must clear the system bars: the bottom always, both sides in portrait, but only the
    // left in landscape (the panel hugs the left edge, so the right inset does not apply).
    val insetSides = if (isLandscape) {
        WindowInsetsSides.Start + WindowInsetsSides.Bottom
    } else {
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
    }

    Surface(
        modifier = modifier
            .requiredHeightIn(80.dp, 350.dp)
            // The panel obstructs the map along its bottom edge in portrait and, as a bottom-left
            // card, along the start edge in landscape — GemMap keeps the Magic Lane logo and the
            // camera centering clear of it.
            .mapObstruction(mapState, if (isLandscape) ObstructionEdge.Start else ObstructionEdge.Bottom),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(insetSides),
                ),
        ) {
            PanelTopBar(
                title = stringResource(title),
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClose = {
                    viewModel.hideBottomView()
                },
            )

            viewModel.routeInfo?.let {
                LocationDetailsPanel(
                    data = LocationDetailsInfo(null, it.routeType, it.routeDescription),
                    modifier = Modifier.fillMaxWidth(),
                    fallbackIcon = painterResource(R.drawable.ic_baseline_route_24),
                )
            }
            viewModel.locationDetailsInfo?.let {
                LocationDetailsPanel(
                    data = it,
                    modifier = Modifier.fillMaxWidth(),
                    fallbackIcon = painterResource(
                        if (it.title == stringResource(R.string.my_position)) {
                            R.drawable.ic_current_location_arrow
                        } else {
                            R.drawable.ic_baseline_route_24
                        },
                    ),
                )
            }
            viewModel.socialReportInfo?.let {
                SocialReportPanel(data = it, modifier = Modifier.fillMaxWidth())
            }
            viewModel.trafficEventInfo?.let {
                TrafficEventPanel(data = it, modifier = Modifier.fillMaxWidth())
            }
            viewModel.safetyCameraInfo?.let {
                SafetyCameraPanel(data = it, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
