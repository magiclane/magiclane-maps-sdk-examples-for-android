// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routesimulationwithinstructions_compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.examples.routesimulationwithinstructions_compose.RouteSimulationModel
import com.magiclane.sdk.examples.routesimulationwithinstructions_compose.R
import com.magiclane.sdk.examples.routesimulationwithinstructions_compose.ui.theme.RouteSimulationWithInstructionsTheme

@Composable
fun MapSurface(modifier: Modifier = Modifier, viewModel: RouteSimulationModel, mapSetter: (GemSurfaceView) -> Unit) {
    AndroidView(modifier = modifier, factory = { context ->
        GemSurfaceView(context).also {
            viewModel.initialize(it)
            mapSetter(it)
        }
    })
}

@Composable
fun ErrorDialog(errorMessage: String, onDismiss: () -> Unit) {
    AlertDialog(
        text = { Text(text = errorMessage) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Ok") }
        }
    )
}

@Composable
fun RouteSimulationScreen(
    modifier: Modifier = Modifier,
    instrText: String,
    turnImage: androidx.compose.ui.graphics.ImageBitmap?,
    instrDistance: String,
    followGpsButtonIsVisible: Boolean,
    etaText: String,
    rttText: String,
    rtdText: String,
    progressBarIsVisible: Boolean,
    errorMessage: String,
    onFollowPositionButtonClick: () -> Unit = {},
    onErrorDismiss: () -> Unit = {},
) {
    if (instrText.isNotBlank()) {
        Column(modifier) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .background(Color.Black),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.padding(all = 10.dp)) {
                    if (turnImage != null) {
                        Image(
                            bitmap = turnImage,
                            contentDescription = "turn image",
                        )
                    }
                    Text(
                        text = instrDistance,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = instrText,
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
            if (followGpsButtonIsVisible) {
                FloatingActionButton(
                    containerColor = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    onClick = onFollowPositionButtonClick,
                ) {
                    Icon(painterResource(id = R.drawable.baseline_my_location_24), "Follow GPS button.", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                Modifier
                    .padding(10.dp)
                    .background(Color.White)
                    .fillMaxWidth(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextUnitCell(Modifier.weight(1f), etaText, TextAlign.Left)
                TextUnitCell(Modifier.weight(1f), rttText, TextAlign.Center)
                TextUnitCell(Modifier.weight(1f), rtdText, TextAlign.Right)
            }
        }
    }
    if (progressBarIsVisible) {
        CircularProgressIndicator(
            modifier = Modifier
                .wrapContentSize()
                .defaultMinSize(minWidth = 50.dp, minHeight = 50.dp),
            color = Color.Cyan,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    if (errorMessage.isNotEmpty()) {
        ErrorDialog(errorMessage, onErrorDismiss)
    }
}

@Composable
fun TextUnitCell(modifier: Modifier = Modifier, text: String, align: TextAlign) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        modifier = modifier
            .padding(all = 10.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun RouteSimulationScreenPreview() {
    RouteSimulationWithInstructionsTheme {
        RouteSimulationScreen(
            instrText = "Turn right",
            turnImage = null,
            instrDistance = "500m",
            followGpsButtonIsVisible = true,
            etaText = "10:15",
            rttText = "5 mins",
            rtdText = "2.5 km",
            progressBarIsVisible = false,
            errorMessage = ""
        )
    }
}
