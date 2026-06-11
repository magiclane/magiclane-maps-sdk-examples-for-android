/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimulationcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.examples.routesimulationcompose.ui.components.MapSurface
import com.magiclane.sdk.examples.routesimulationcompose.ui.components.RouteSimulationScreen
import com.magiclane.sdk.examples.routesimulationcompose.ui.theme.RouteSimulationComposeTheme
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

class MainActivity : ComponentActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private lateinit var gemSurfaceView: GemSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SoundUtils.addTTSPlayerInitializationListener(this)

        setupContent()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )
    }

    private fun setupContent() {
        setContent {
            RouteSimulationComposeTheme {
                val viewModel = viewModel<RouteSimulationModel>()

                viewModel.turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
                viewModel.navigationImageSize = resources.getDimension(R.dimen.navigation_image_size).toInt()
                viewModel.signPostImageSize = resources.getDimension(R.dimen.sign_post_image_size).toInt()
                viewModel.turnPaddingPx = resources.getDimension(R.dimen.nav_top_panel_turn_margin).toInt()

                SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
                    if (status == EOffboardListenerStatus.UpToDate) {
                        SdkSettings.onWorldwideRoadMapSupportStatus = {}
                        viewModel.startSimulation()
                    }
                }

                SdkSettings.onApiTokenRejected = {
                    runOnUiThread {
                        viewModel.errorMessage = getString(R.string.token_rejected_message)
                    }
                }

                if (!Util.isInternetConnected(this@MainActivity)) {
                    viewModel.errorMessage = getString(R.string.internet_required)
                }

                RouteSimulationApp(Modifier.fillMaxSize(), viewModel)
            }
        }
    }

    fun setGemSurfaceView(view: GemSurfaceView) {
        gemSurfaceView = view
    }

    fun getGemSurfaceView() = if (::gemSurfaceView.isInitialized) gemSurfaceView else null

    override fun onDestroy() {
        super.onDestroy()

        if (::gemSurfaceView.isInitialized) {
            gemSurfaceView.release()
        }

        if (isFinishing) {
            GemSdk.release()
        }

        exitProcess(0)
    }

    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}

@Composable
fun RouteSimulationApp(modifier: Modifier, viewModel: RouteSimulationModel = viewModel()) {
    val mainActivity = LocalActivity.current as MainActivity
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        MapSurface(modifier, viewModel) { mainActivity.setGemSurfaceView(it) }
        RouteSimulationScreen(
            modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            viewModel = viewModel,
            onFollowPositionButtonClick = {
                viewModel.startFollowingPosition(mainActivity.getGemSurfaceView())
            },
            onErrorDismiss = { viewModel.errorMessage = "" },
        )
    }
}
