// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routesimulationwithinstructions_compose

// ---------------------------------------------------------------------------------------------------------------------------------------------------

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.routesimulationwithinstructions_compose.ui.theme.RouteSimulationWithInstructionsTheme
import com.magiclane.sdk.util.Util

// ---------------------------------------------------------------------------------------------------------------------------------------------------

class MainActivity : ComponentActivity()
{
    private lateinit var viewModel: RouteSimulationModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RouteSimulationWithInstructionsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                )
                {
                    viewModel = viewModel()
                    viewModel.turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

                    if (!Util.isInternetConnected(this))
                    {
                        viewModel.errorMessage = "Please connect to the internet!"
                    }

                    RouteSimulationScreen(viewModel = viewModel, onFollowPositionButtonClick = { viewModel.startFollowingPosition() })
                }
            }
        }

        SdkSettings.onMapDataReady = { isReady ->
            if (isReady)
            {
                viewModel.startSimulation()
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
                finish()
            }
        })
    }

    // -----------------------------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        viewModel.surfaceView = null

        if (isFinishing)
        {
            GemSdk.release() // Release the SDK.
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------------------------
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun ErrorDialog(viewModel: RouteSimulationModel)
{
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
                }
            ) {
                Text("Ok")
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun RouteSimulationScreen(modifier: Modifier = Modifier,
                          viewModel: RouteSimulationModel = viewModel(),
                          onFollowPositionButtonClick: () -> Unit = {})
{
    if (viewModel.surfaceView == null)
    {
        viewModel.surfaceView = GemSurfaceView(LocalContext.current)
    }

    AndroidView(factory = { viewModel.surfaceView!! })

    if (viewModel.instrText.isNotBlank())
    {
        Column {
            Row (
                modifier
                    .padding(10.dp)
                    .background(Color.Black),
                verticalAlignment = Alignment.CenterVertically) {
                Column (Modifier.padding(all = 10.dp)) {
                    Image(
                        bitmap = viewModel.turnImage,
                        contentDescription = "turn image",
                    )

                    Text(
                        text = viewModel.instrDistance,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = viewModel.instrText,
                    fontSize = 26.sp,
                    color = Color.White,
                    maxLines = 3,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .weight(1f),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (viewModel.followGpsButtonIsVisible)
            {
                FloatingActionButton(
                    containerColor = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    onClick = { onFollowPositionButtonClick() },
                ) {
                    Icon(painterResource(id = R.drawable.baseline_my_location_24), "Follow GPS button.")
                }
            }

            Row (
                modifier
                    .padding(10.dp)
                    .background(Color.White)
                    .fillMaxWidth(1f),
                verticalAlignment = Alignment.CenterVertically) {

                Text(
                    text = viewModel.etaText,
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Left,
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .weight(1f)
                )

                Text(
                    text = viewModel.rttText,
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .weight(1f)
                )

                Text(
                    text = viewModel.rtdText,
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .weight(1f)
                )
            }
        }
    }

    if (viewModel.progressBarIsVisible)
    {
        CircularProgressIndicator(
            modifier = Modifier
                .wrapContentSize()
                .defaultMinSize(minWidth = 50.dp, minHeight = 50.dp),
            color = Color.Cyan,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (viewModel.errorMessage.isNotEmpty())
    {
        ErrorDialog(viewModel)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
fun RouteSimulationScreenPreview() {
    RouteSimulationWithInstructionsTheme {
        RouteSimulationScreen()
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------
