/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
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
                MapSelectionApp { setMapSurfaceView(it) }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                viewModel.detailsPanelImageSize = resources.getDimension(R.dimen.image_size).toInt()
                viewModel.overlayImageSize = resources.getDimension(R.dimen.overlay_image_size).toInt()
                viewModel.padding = resources.getDimension(R.dimen.big_padding).toInt()

                // Set GPS button if location permission is granted, otherwise request permission
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

        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.isBottomViewVisible()) {
                if (::mapSurfaceView.isInitialized) {
                    mapSurfaceView.mapView?.let { viewModel.hideBottomView(it) }
                } else {
                    finish()
                }
            } else {
                finish()
            }
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
                // Notify permission status had changed
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

    override fun onDestroy() {
        super.onDestroy()

        GemSdk.release() // Release the SDK.
        exitProcess(0)
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

    Box(Modifier.fillMaxSize().background(color = Color.Black)) {
        Column {
            MapSurface(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                mapSurfaceViewSetter,
                viewModel,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (viewModel.followGpsButtonIsVisible) {
                FloatingActionButton(
                    modifier = Modifier.padding(
                        start = 8.dp,
                        bottom = 8.dp,
                    ),
                    onClick = {
                        activity?.let {
                            viewModel.startFollowingPosition(it.getGemSurfaceView())
                        }
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.baseline_my_location_24),
                        contentDescription = "Follow GPS position",
                    )
                }
            }

            BottomContent(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned {
                        if (viewModel.bottomPartHeight != it.size.height /*+ navBarHeight*/) {
                            viewModel.bottomPartHeight = it.size.height /*+ navBarHeight*/
                            activity?.getGemSurfaceView()?.let {
                                viewModel.setVisibleArea(it)
                            }
                        }
                    },
                iconOnClick = {
                    viewModel.hideBottomView(activity?.getGemSurfaceView()?.mapView)
                },
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (viewModel.progressBarIsVisible) {
                CircularProgressIndicator(
                    modifier = Modifier.wrapContentSize().defaultMinSize(
                        minWidth = 50.dp,
                        minHeight = 50.dp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
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
            }

            surfaceView.onSdkInitFailed = { error ->
                viewModel.errorMessage = context.getString(
                    R.string.sdk_initialization_failed,
                    GemError.getMessage(error, context),
                )
            }

            mapSurfaceViewSetter(surfaceView)
        }
    })
}

@Composable
fun BottomContent(
    modifier: Modifier = Modifier,
    viewModel: MapSelectionModel = viewModel(),
    iconOnClick: (() -> Unit)? = null,
) {
    // Fire highlight after layout (and map visible area update)
    LaunchedEffect(viewModel.invokeHighlight) {
        if (viewModel.invokeHighlight) {
            // wait one frame to ensure map consumed visibleArea
            withFrameNanos { }
            viewModel.invokeHighlightEffect()
        }
    }

    if ((viewModel.locationDetailsInfo != null) ||
        (viewModel.safetyCameraInfo != null) ||
        (viewModel.trafficEventInfo != null) ||
        (viewModel.socialReportInfo != null) ||
        (viewModel.routeInfo != null)
    ) {
        val title = when {
            viewModel.routeInfo != null -> R.string.route_info
            viewModel.trafficEventInfo != null -> R.string.traffic_event
            viewModel.locationDetailsInfo != null -> R.string.location_details
            viewModel.safetyCameraInfo != null -> R.string.safety_camera
            viewModel.socialReportInfo != null -> R.string.social_report
            else -> R.string.app_name
        }

        Box(modifier) {
            Surface(
                modifier = modifier.align(
                    Alignment.BottomCenter,
                ).requiredHeightIn(80.dp, 350.dp).verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                Column {
                    TopAppBar(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(title), // set title based on what is clicked
                        toolbarColor = Color.Transparent,
                        iconOnClick = iconOnClick,
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
