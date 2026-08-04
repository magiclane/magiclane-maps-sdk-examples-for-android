/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.mapselectioncompose.data.LocationDetailsInfo
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.MapSelectionTheme
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Fraction of the screen width the info panel occupies when shown as a bottom-left card in landscape.
private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.45f

// Fraction of the screen the public transport station panel occupies: its height in portrait
// (full width bottom panel) and its width in landscape (left-side card spanning the full height).
private const val PT_STATION_PANEL_FRACTION = 0.5f

// Predefined margin kept between the follow-GPS button and the screen edges (added on top of the
// system bar / display cutout insets).
private val GPS_BUTTON_MARGIN = 8.dp

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    private val viewModel: MapSelectionModel by viewModels()

    private lateinit var mapSurfaceView: GemSurfaceView

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
        configureWindow()
        setContent {
            MapSelectionTheme {
                MapSelectionApp(viewModel) { setMapSurfaceView(it) }
            }
        }

        registerSdkListeners()

        onBackPressedDispatcher.addCallback(this) {
            // Back press first closes the public transport trip view, then the station view,
            // then dismisses the details panel (if shown), otherwise closes the app.
            when {
                viewModel.ptTripViewTrips != null -> viewModel.closePublicTransportTripView()
                viewModel.ptStationInfo != null -> viewModel.closePublicTransportStationView()
                viewModel.isBottomViewVisible() -> viewModel.hideBottomView(getGemSurfaceView()?.mapView)
                else -> finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()
        GemSdk.release() // Release the SDK.
        exitProcess(0)
    }

    // Registers all SDK-level listeners. The map surface listeners are wired in the MapSurface
    // composable factory and reset in clearSdkListeners().
    private fun registerSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                viewModel.detailsPanelImageSize = resources.getDimension(R.dimen.image_size).toInt()
                viewModel.overlayImageSize = resources.getDimension(R.dimen.overlay_image_size).toInt()
                viewModel.padding = resources.getDimension(R.dimen.big_padding).toInt()

                // Enable the follow-GPS button if the location permission is already granted,
                // otherwise request it.
                if (checkPermissions()) {
                    viewModel.followGpsButtonIsVisible = true
                } else {
                    requestPermissions(this)
                }
                viewModel.calculateRoutes()
            }
        }

        SdkSettings.onApiTokenRejected = {
            viewModel.errorMessage = getString(R.string.token_rejected_message)
        }
    }

    // Clears SDK-level and map surface listeners so callbacks never reach a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        if (::mapSurfaceView.isInitialized) {
            mapSurfaceView.onSdkInitFailed = {}
            mapSurfaceView.onDefaultMapViewCreated = {}
            mapSurfaceView.onSurfaceChanged = null
        }
    }

    private fun checkPermissions() = PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions =
            arrayListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions.toTypedArray(),
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)

        if (requestCode == REQUEST_PERMISSIONS) {
            for (item in grantResults) {
                if (item != PackageManager.PERMISSION_GRANTED) {
                    viewModel.errorMessage = getString(R.string.location_permission_required)
                    return
                }
            }

            SdkCall.execute {
                // Notify the SDK that the permission status has changed.
                PermissionsHelper.instance?.notifyOnPermissionsStatusChanged()

                lateinit var positionListener: PositionListener
                if (PositionService.position?.isValid() == true) {
                    Util.postOnMain { viewModel.followGpsButtonIsVisible = true }
                } else {
                    positionListener = PositionListener {
                        if (it.isValid()) {
                            PositionService.removeListener(positionListener)
                            Util.postOnMain { viewModel.followGpsButtonIsVisible = true }
                        }
                    }

                    PositionService.addListener(positionListener, EDataType.Position)
                }
            }
        }
    }

    private fun configureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    fun setMapSurfaceView(view: GemSurfaceView) {
        mapSurfaceView = view
    }

    fun getGemSurfaceView() = if (::mapSurfaceView.isInitialized) mapSurfaceView else null
}

@Composable
fun MapSelectionApp(viewModel: MapSelectionModel = viewModel(), mapSurfaceViewSetter: (GemSurfaceView) -> Unit) {
    val activity = LocalActivity.current as? MainActivity
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Keep the model's orientation in sync and recompute the free map area on rotation so the
    // Magic Lane logo and the camera centering stay clear of the info panel.
    LaunchedEffect(isLandscape) {
        viewModel.isLandscape = isLandscape
        viewModel.updateMapAreas()
    }

    Box(Modifier.fillMaxSize().background(color = Color.Black)) {
        // Full-screen map. The Magic Lane logo is kept inside the visible area via the focus
        // viewport (see MapSelectionModel.updateMapAreas), so it never hides behind the panel.
        MapSurface(Modifier.fillMaxSize(), mapSurfaceViewSetter, viewModel)

        if (isLandscape) {
            // Landscape: the info panel is a card pinned to the bottom-left corner, the station
            // panel a half-width card spanning the full screen height on the left, and the
            // follow-GPS button sits in the opposite (bottom-right) corner.
            InfoPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(LANDSCAPE_PANEL_WIDTH_FRACTION),
                viewModel = viewModel,
                activity = activity,
                isLandscape = true,
            )
            viewModel.ptStationInfo?.let { station ->
                PublicTransportStationScreen(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(PT_STATION_PANEL_FRACTION),
                    viewModel = viewModel,
                    station = station,
                    isLandscape = true,
                )
            }
            FollowGpsButton(
                modifier = Modifier.align(Alignment.BottomEnd),
                viewModel = viewModel,
                activity = activity,
                applyBottomInset = true,
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
                FollowGpsButton(
                    viewModel = viewModel,
                    activity = activity,
                    // When a panel is visible it carries the bottom inset; the button only needs
                    // it when it sits alone at the bottom of the screen.
                    applyBottomInset = !viewModel.isBottomViewVisible() && viewModel.ptStationInfo == null,
                )
                InfoPanel(
                    modifier = Modifier.fillMaxWidth(),
                    viewModel = viewModel,
                    activity = activity,
                    isLandscape = false,
                )
                viewModel.ptStationInfo?.let { station ->
                    PublicTransportStationScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(PT_STATION_PANEL_FRACTION),
                        viewModel = viewModel,
                        station = station,
                        isLandscape = false,
                    )
                }
            }
        }

        // Centered loading indicator shown while a route is being computed.
        if (viewModel.progressBarIsVisible) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredHeightIn(min = 50.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

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
        ErrorDialog(viewModel)
    }
}

@Composable
fun MapSurface(
    modifier: Modifier = Modifier,
    mapSurfaceViewSetter: (GemSurfaceView) -> Unit,
    viewModel: MapSelectionModel,
) {
    AndroidView(modifier = modifier, factory = { context ->
        GemSurfaceView(context).also { surfaceView ->
            surfaceView.onDefaultMapViewCreated = {
                viewModel.initialize(surfaceView)
                // Position the Magic Lane logo as soon as the map view exists.
                viewModel.updateMapAreas(surfaceView)
            }

            // Re-align the Magic Lane logo whenever the surface is resized (e.g. on rotation).
            surfaceView.onSurfaceChanged = { _, _ ->
                viewModel.updateMapAreas(surfaceView)
            }

            surfaceView.onSdkInitFailed = { error ->
                // The SDK is not initialized here, so resolve the message directly (no SdkCall).
                viewModel.errorMessage = context.getString(
                    R.string.sdk_initialization_failed,
                    GemError.getMessage(error, context),
                )
            }

            mapSurfaceViewSetter(surfaceView)
        }
    })
}

// Follow-GPS floating button. Styled with the primary color and always kept clear of the right-side
// system bar / display cutout; it additionally clears the bottom inset when anchored to the edge.
@Composable
fun FollowGpsButton(
    modifier: Modifier = Modifier,
    viewModel: MapSelectionModel,
    activity: MainActivity?,
    applyBottomInset: Boolean,
) {
    if (!viewModel.followGpsButtonIsVisible) return

    val insetSides = if (applyBottomInset) {
        WindowInsetsSides.End + WindowInsetsSides.Bottom
    } else {
        WindowInsetsSides.End
    }

    FloatingActionButton(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout).only(insetSides),
            )
            .padding(GPS_BUTTON_MARGIN),
        onClick = {
            activity?.let { viewModel.startFollowingPosition(it.getGemSurfaceView()) }
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_gps_fixed_24),
            contentDescription = stringResource(R.string.follow_gps),
        )
    }
}

// Info panel describing the tapped map element (route, landmark, traffic event, safety camera or
// social report). Spans the bottom edge in portrait and becomes a bottom-left card in landscape.
@Composable
fun InfoPanel(
    modifier: Modifier = Modifier,
    viewModel: MapSelectionModel,
    activity: MainActivity?,
    isLandscape: Boolean,
) {
    // Fire the pending highlight after layout (one frame after the map's visible area is updated).
    LaunchedEffect(viewModel.invokeHighlight) {
        if (viewModel.invokeHighlight) {
            withFrameNanos { }
            viewModel.invokeHighlightEffect()
        }
    }

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
            .onGloballyPositioned { coordinates ->
                // Report the panel extent so the model keeps the logo / centering clear of it.
                val width = coordinates.size.width
                val height = coordinates.size.height
                val changed = if (isLandscape) {
                    viewModel.panelWidthPx != width
                } else {
                    viewModel.panelHeightPx != height
                }
                if (changed) {
                    viewModel.panelWidthPx = width
                    viewModel.panelHeightPx = height
                    viewModel.updateMapAreas(activity?.getGemSurfaceView())
                }
            },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(insetSides),
                ),
        ) {
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(title),
                toolbarColor = Color.Transparent,
                iconOnClick = {
                    viewModel.hideBottomView(activity?.getGemSurfaceView()?.mapView)
                },
            )

            viewModel.routeInfo?.let {
                LocationDetailsScreen(
                    Modifier.fillMaxWidth(),
                    LocationDetailsInfo(null, it.routeType, it.routeDescription),
                )
            }
            viewModel.locationDetailsInfo?.let {
                LocationDetailsScreen(Modifier.fillMaxWidth(), it)
            }
            viewModel.socialReportInfo?.let {
                SocialReportScreen(Modifier.fillMaxWidth(), it)
            }
            viewModel.trafficEventInfo?.let {
                TrafficEventScreen(Modifier.fillMaxWidth(), it)
            }
            viewModel.safetyCameraInfo?.let {
                SafetyCameraScreen(Modifier.fillMaxWidth(), it)
            }
        }
    }
}

@Composable
fun ErrorDialog(viewModel: MapSelectionModel) {
    AlertDialog(
        text = {
            Text(text = viewModel.errorMessage)
        },
        onDismissRequest = {
            viewModel.errorMessage = ""
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.errorMessage = ""
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
