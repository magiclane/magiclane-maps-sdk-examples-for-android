// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routesimulationwithinstructions_compose


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.routesimulationwithinstructions_compose.ui.components.MapSurface
import com.magiclane.sdk.examples.routesimulationwithinstructions_compose.ui.components.RouteSimulationScreen
import com.magiclane.sdk.examples.routesimulationwithinstructions_compose.ui.theme.RouteSimulationWithInstructionsTheme
import com.magiclane.sdk.util.Util


class MainActivity : ComponentActivity() {

    private lateinit var gemSurfaceView: GemSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeSdk()
        setupContent()
        setupBackPressHandler()

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun initializeSdk() {
        if (!GemSdk.initSdkWithDefaults(this)) {
            finish()
        }
    }

    private fun setupContent() {
        setContent {
            RouteSimulationWithInstructionsTheme {
                val viewModel = viewModel<RouteSimulationModel>()

                // Check internet connection and set status message
                if (!Util.isInternetConnected(this@MainActivity)) {
                    viewModel.errorMessage = "Please connect to the internet!"
                }

                viewModel.turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
                startSimulation(viewModel)
                RouteSimulationApp(Modifier.fillMaxSize(), viewModel)
            }
        }
    }

    private fun startSimulation(viewModel: RouteSimulationModel) {
        SdkSettings.onMapDataReady = { isReady ->
            if (isReady) {
                viewModel.startSimulation()
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    fun setGemSurfaceView(view: GemSurfaceView) {
        gemSurfaceView = view
    }

    fun getGemSurfaceView() = if (::gemSurfaceView.isInitialized) gemSurfaceView else null

    override fun onDestroy() {
        super.onDestroy()

        gemSurfaceView.release()

        if (isFinishing) {
            GemSdk.release() // Release the SDK.
        }
    }

}

@Composable
fun RouteSimulationApp(modifier: Modifier, viewModel: RouteSimulationModel = viewModel()) {
    val mainActivity = LocalActivity.current as MainActivity
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        MapSurface(modifier, viewModel) { mainActivity.setGemSurfaceView(it) }
        RouteSimulationScreen(
            modifier = modifier.windowInsetsPadding(WindowInsets.systemBars),
            instrText = viewModel.instrText,
            turnImage = viewModel.turnImage,
            instrDistance = viewModel.instrDistance,
            followGpsButtonIsVisible = viewModel.followGpsButtonIsVisible,
            etaText = viewModel.etaText,
            rttText = viewModel.rttText,
            rtdText = viewModel.rtdText,
            progressBarIsVisible = viewModel.progressBarIsVisible,
            errorMessage = viewModel.errorMessage,
            onFollowPositionButtonClick = { viewModel.startFollowingPosition(mainActivity.getGemSurfaceView()) },
            onErrorDismiss = { viewModel.errorMessage = "" }
        )
    }
}
