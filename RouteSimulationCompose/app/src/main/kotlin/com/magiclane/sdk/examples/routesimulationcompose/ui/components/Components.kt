/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimulationcompose.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.examples.routesimulationcompose.R
import com.magiclane.sdk.examples.routesimulationcompose.RouteSimulationModel
import com.magiclane.sdk.examples.routesimulationcompose.ui.theme.RouteSimulationComposeTheme

private val trafficPanelColor = Color(0xFFFFAF3F)

@Composable
fun MapSurface(modifier: Modifier = Modifier, viewModel: RouteSimulationModel, mapSetter: (GemSurfaceView) -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val endOfSectionBmp = ContextCompat.getDrawable(context, R.drawable.end_of_traffic_section)
                ?.toBitmap(viewModel.navigationImageSize, viewModel.navigationImageSize)
            GemSurfaceView(ctx).also { view ->
                viewModel.initialize(view, endOfSectionBmp)
                view.onSdkInitFailed = { error ->
                    viewModel.errorMessage = context.getString(
                        R.string.sdk_init_failed,
                        GemError.getMessage(error, context),
                    )
                }
                view.onDefaultMapViewCreated = { viewModel.updateFocusViewport() }
                view.onSurfaceChanged = { _, _ ->
                    viewModel.applyCameraFocus()
                    viewModel.updateFocusViewport()
                }
                mapSetter(view)
            }
        },
    )
}

@Composable
fun ErrorDialog(errorMessage: String, onDismiss: () -> Unit) {
    AlertDialog(
        text = { Text(text = errorMessage) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Ok") }
        },
    )
}

@Composable
fun RouteSimulationScreen(
    modifier: Modifier = Modifier,
    viewModel: RouteSimulationModel,
    onFollowPositionButtonClick: () -> Unit = {},
    onErrorDismiss: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // When panels are hidden, update the viewport immediately (no layout needed).
    // When panels become visible, BottomPanel/TrafficPanel's onGloballyPositioned
    // fires after rendering with correct dimensions — equivalent to View.post { }.
    LaunchedEffect(viewModel.navigationPanelsAreVisible) {
        if (!viewModel.navigationPanelsAreVisible) {
            viewModel.updateFocusViewport()
        }
    }

    Box(modifier) {
        if (viewModel.navigationPanelsAreVisible) {
            Column(
                modifier = if (isLandscape) {
                    Modifier.fillMaxHeight().fillMaxWidth(0.5f)
                } else {
                    Modifier.fillMaxHeight().fillMaxWidth()
                },
            ) {
                TopPanel(viewModel)

                if (viewModel.trafficPanelVisible) {
                    TrafficPanel(viewModel)
                }

                Spacer(modifier = Modifier.weight(1f))

                BottomPanel(viewModel)
            }
        }

        if (viewModel.followGpsButtonIsVisible) {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                onClick = onFollowPositionButtonClick,
            ) {
                Icon(
                    painterResource(id = R.drawable.baseline_my_location_24),
                    contentDescription = "Follow GPS button",
                    tint = Color.White,
                )
            }
        }

        if (viewModel.progressBarIsVisible) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .defaultMinSize(minWidth = 50.dp, minHeight = 50.dp),
                color = Color.Cyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(viewModel.errorMessage, onErrorDismiss)
    }
}

@Composable
private fun TopPanel(viewModel: RouteSimulationModel) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    viewModel.distanceTextWidthPx = textMeasurer.measure(
        text = "9000 km",
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    ).size.width

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 10.dp)
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                viewModel.topPanelBottomPx = bounds.bottom.toInt()
                viewModel.topPanelRightPx = bounds.right.toInt()
                viewModel.topPanelWidthPx = coords.size.width
                val marginPx = with(density) { 20.dp.roundToPx() }
                viewModel.signPostHeightPx = (coords.size.height - marginPx).coerceAtLeast(0)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.padding(all = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            viewModel.turnImage?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = "Turn image",
                    modifier = Modifier.size(50.dp),
                )
            }
            Text(
                text = viewModel.instrDistance,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val signPost = viewModel.signPostImage
            val roadCode = viewModel.roadCodeImage

            if (signPost != null) {
                Image(
                    bitmap = signPost,
                    contentDescription = "Sign post",
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (roadCode != null) {
                        Image(
                            bitmap = roadCode,
                            contentDescription = "Road code",
                        )
                    }
                    if (viewModel.statusMessage.isNotEmpty()) {
                        Text(
                            text = viewModel.statusMessage,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = viewModel.instrText,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (roadCode != null) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficPanel(viewModel: RouteSimulationModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(trafficPanelColor)
            .onGloballyPositioned { coords ->
                viewModel.trafficPanelBottomPx = coords.boundsInRoot().bottom.toInt()
                viewModel.updateFocusViewport()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val trafficImg = viewModel.trafficImage
        val endOfSectionImg = viewModel.endOfSectionImage

        Box(modifier = Modifier.padding(10.dp)) {
            if (trafficImg != null) {
                Image(
                    bitmap = trafficImg,
                    contentDescription = "Traffic event",
                    modifier = Modifier.size(44.dp),
                )
            }
            if (viewModel.endOfSectionVisible && endOfSectionImg != null) {
                Image(
                    bitmap = endOfSectionImg,
                    contentDescription = "End of traffic section",
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp, end = 10.dp),
        ) {
            Text(
                text = viewModel.trafficEventDescription,
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Row {
                Text(
                    text = viewModel.distanceToTrafficPrefix,
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = viewModel.distanceToTrafficText,
                    color = Color.Black,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = viewModel.distanceToTrafficUnitText.trimStart(),
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )

                Spacer(modifier = Modifier.weight(1f))

                if (viewModel.trafficDelayTimeText.isNotEmpty()) {
                    Text(
                        text = viewModel.trafficDelayTimeText,
                        color = Color.Black,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = viewModel.trafficDelayTimeUnitText.trimStart(),
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline(),
                    )
                }

                if (viewModel.trafficDelayDistanceText.isNotEmpty()) {
                    Text(
                        text = " ${viewModel.trafficDelayDistanceText}",
                        color = Color.Black,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = viewModel.trafficDelayDistanceUnitText.trimStart(),
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPanel(viewModel: RouteSimulationModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(Color.White)
            .onGloballyPositioned { coords ->
                viewModel.bottomPanelTopPx = coords.boundsInRoot().top.toInt()
                viewModel.updateFocusViewport()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomPanelText(Modifier.weight(1f), viewModel.etaText, TextAlign.Left)
        BottomPanelText(Modifier.weight(1f), viewModel.rttText, TextAlign.Center)
        BottomPanelText(Modifier.weight(1f), viewModel.rtdText, TextAlign.Right)
    }
}

@Composable
private fun BottomPanelText(modifier: Modifier = Modifier, text: String, align: TextAlign) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        modifier = modifier.padding(all = 10.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun RouteSimulationScreenPreview() {
    RouteSimulationComposeTheme {
        val context = LocalContext.current
        val viewModel = remember {
            RouteSimulationModel(context.applicationContext as android.app.Application).apply {
                navigationPanelsAreVisible = true
                instrText = "Rue de Rivoli"
                instrDistance = "500 m"
                etaText = "15:30"
                rttText = "25 min"
                rtdText = "18.2 km"
                followGpsButtonIsVisible = true
            }
        }
        RouteSimulationScreen(
            viewModel = viewModel,
        )
    }
}
