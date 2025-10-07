// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapselection_compose

import android.Manifest
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.mapselection_compose.ui.theme.MapSelectionTheme
import com.magiclane.sdk.util.PermissionsHelper

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    private val viewModel: MapSelectionModel by viewModels()
    private lateinit var gemSurfaceView: GemSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureWindow()
        setContent {
            MapSelectionTheme {
                MapSelectionApp { setGemSurfaceView(it) }
            }
        }

        SdkSettings.onMapDataReady = { isReady ->
            if (isReady) {
                viewModel.detailsPanelImageSize = resources.getDimension(R.dimen.image_size).toInt()
                viewModel.overlayImageSize = resources.getDimension(R.dimen.overlay_image_size).toInt()

                // Set GPS button if location permission is granted, otherwise request permission
                if (checkPermissions()) {
                    viewModel.followGpsButtonIsVisible = true
                } else {
                    requestPermissions(this)
                }
                viewModel.calculateRoutes()
                //viewModel.initialize(this, gemSurfaceView)
            }
        }

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one.
             */
            viewModel.errorMessage = "Token rejected!"
        }

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.isBottomViewVisible()) {
                    if (::gemSurfaceView.isInitialized)
                        gemSurfaceView.mapView?.let { viewModel.deactivateHighlights(it) }
                    else
                        finish()
                } else
                    finish()
            }
        })
    }

    private fun checkPermissions() = PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        return PermissionsHelper.requestPermissions(REQUEST_PERMISSIONS, activity, permissions.toTypedArray())
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            GemSdk.release() // Release the SDK.
        }
    }

    private fun configureWindow() {
        val window = this.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    fun setGemSurfaceView(view: GemSurfaceView) {
        gemSurfaceView = view
    }

    fun getGemSurfaceView() = if (::gemSurfaceView.isInitialized) gemSurfaceView else null
}

@Composable
fun MapSelectionApp(viewModel: MapSelectionModel = viewModel(), mapSetter: (GemSurfaceView) -> Unit) {
    val activity = LocalActivity.current as? MainActivity
    viewModel.detailsPanelImageSize = with(LocalDensity.current) { 60.dp.toPx().toInt() }
    viewModel.overlayImageSize = with(LocalDensity.current) { 60.dp.toPx().toInt() }
    val navBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(color = Color.Black)
    ) {
        Column {
            TopAppBar(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars),
                title = stringResource(R.string.app_name), toolbarColor = MaterialTheme.colorScheme.primary
            )
            MapSurface(Modifier.windowInsetsPadding(WindowInsets.navigationBars), mapSetter, viewModel)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (viewModel.flyToRoutesButtonIsVisible)
                FloatingActionButton(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), onClick = {
                    activity?.getGemSurfaceView()?.mapView?.let { mapview ->
                        viewModel.showRoutes(mapview)
                        //mapview.preferences?.routes?.mainRoute?.let { viewModel.selectRoute(it, mapview) }
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_baseline_route_24), contentDescription = "Add")
                }
            if (viewModel.followGpsButtonIsVisible)
                FloatingActionButton(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), onClick = {
                    activity?.let {
                        viewModel.startFollowingPosition(it.getGemSurfaceView())
                    }
                }) {
                    Icon(painterResource(R.drawable.baseline_my_location_24), contentDescription = "Edit")
                }
            BottomContent(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        viewModel.bottomPartHeight = it.size.height + navBarHeight
                        activity?.getGemSurfaceView()?.let {
                            viewModel.setVisibleArea(it)
                        }
                    },
                iconOnClick = {
                    viewModel.hideBottomView()
                }
            )
        }
    }
}

@Composable
fun MapSurface(modifier: Modifier = Modifier, mapSetter: (GemSurfaceView) -> Unit, viewModel: MapSelectionModel) {
    AndroidView(modifier = modifier, factory = { context ->
        GemSurfaceView(context).also {
            it.onDefaultMapViewCreated = { _ ->
                viewModel.initialize(context, it)
            }
            mapSetter(it)
        }
    })
}

@Composable
fun BottomContent(modifier: Modifier = Modifier, viewModel: MapSelectionModel = viewModel(), iconOnClick: (() -> Unit)? = null) {
    var laidOut = viewModel.invokeHighlight
    // Fire highlight after layout (and map visible area update)
    LaunchedEffect(laidOut) {
        if (laidOut) {
            // wait one frame to ensure map consumed visibleArea
            withFrameNanos { }
            viewModel.invokeHighlightEffect()
        }
    }
    
    val activity = LocalActivity.current as? MainActivity
    val titleID = when {
        viewModel.trafficEventInfo != null -> R.string.traffic_event
        viewModel.locationDetailsInfo != null -> R.string.location_details
        viewModel.safetyCameraInfo != null -> R.string.safety_camera
        viewModel.socialReportInfo != null -> R.string.social_report
        else -> R.string.app_name
    }
    if (viewModel.locationDetailsInfo != null
        || viewModel.safetyCameraInfo != null
        || viewModel.trafficEventInfo != null
        || viewModel.socialReportInfo != null
    )
        Box(modifier) {
            Surface(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .requiredHeightIn(80.dp, 350.dp)
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column {
                    TopAppBar(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(titleID), //set title based on what is clicked
                        toolbarColor = Color.Transparent,
                        iconOnClick = iconOnClick
                    )
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
