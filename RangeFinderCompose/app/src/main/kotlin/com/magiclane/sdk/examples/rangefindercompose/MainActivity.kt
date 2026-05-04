/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.rangefindercompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.rangefindercompose.ui.theme.RangeFinderTheme
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: RangeFinderModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RangeFinderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    viewModel = viewModel()
                    RangeFinderScreen(viewModel = viewModel)
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            viewModel.errorMessage = getString(R.string.token_rejected_message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        viewModel.surfaceView = null
        viewModel.context = null

        if (isFinishing) {
            GemSdk.release()
        }
        exitProcess(0)
    }
}

private val RangePanelHeight = 300.dp
private val RangePanelTopPadding = 5.dp
private val RangePanelBottomContentPadding = 16.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RangeFinderScreen(modifier: Modifier = Modifier, viewModel: RangeFinderModel = viewModel()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    viewModel.initializeStrings(context)

    if (viewModel.surfaceView == null) {
        val surfaceView = GemSurfaceView(context)
        viewModel.surfaceView = surfaceView

        surfaceView.onSdkInitSucceeded = {
            viewModel.onSdkInitSucceeded(context)
        }

        surfaceView.onSdkInitFailed = { error ->
            viewModel.errorMessage = context.getString(
                R.string.sdk_initialization_failed,
                GemError.getMessage(error, context),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewModel.surfaceView!! },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                Modifier
                    .padding(top = RangePanelTopPadding)
                    .clip(shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                    .background(Color.White)
                    .height(RangePanelHeight)
                    .fillMaxWidth(1f),
            ) {
                val alpha = if (viewModel.displayProgress) 1f else 0f
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth(1f)
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp)
                        .alpha(alpha),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(1f)
                        .padding(bottom = RangePanelBottomContentPadding)
                        .verticalScroll(scrollState),
                ) {
                    LazyRow {
                        itemsIndexed(viewModel.ranges) { index, range ->
                            RangeItem(
                                range.imageResourceId,
                                range.text,
                                borderColor = range.borderColor,
                                enabled = range.enabled,
                                onItemClick = {
                                    range.enabled.value = !range.enabled.value
                                    viewModel.didTapRange(index, range.enabled.value)
                                },
                                onItemLongClick = {
                                    viewModel.zoomToRange(index)
                                },
                                onDeleteItemClick = {
                                    viewModel.didTapRemoveRangeButton(index)
                                },
                            )
                        }
                    }

                    Row(
                        modifier
                            .padding(all = 10.dp)
                            .fillMaxWidth(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.padding(start = 10.dp),
                            text = stringResource(R.string.range_value),
                            color = Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = { viewModel.didTapAddRangeButton() },
                            enabled = viewModel.addRangeButtonIsEnabled,
                        ) {
                            Text(
                                text = stringResource(R.string.add_range),
                                fontSize = 20.sp,
                            )
                        }
                    }

                    Row(
                        modifier.padding(start = 10.dp, end = 10.dp)
                            .fillMaxWidth(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.padding(start = 5.dp),
                            text = viewModel.rangeSlider.leftSideText.value,
                            color = Color.Black,
                            fontSize = 15.sp,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = viewModel.rangeSlider.valueText.value,
                            color = Color.Black,
                            fontSize = 15.sp,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            modifier = Modifier.padding(end = 5.dp),
                            text = viewModel.rangeSlider.rightSideText.value,
                            color = Color.Black,
                            fontSize = 15.sp,
                        )
                    }

                    Slider(
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                        value = viewModel.rangeSlider.value.value,
                        onValueChange = { viewModel.didChangeRangeSliderPosition(it) },
                        steps = viewModel.rangeSlider.steps.value,
                        valueRange = viewModel.rangeSlider.leftSide.value..viewModel.rangeSlider.rightSide.value,
                    )

                    Row(
                        modifier.padding(start = 10.dp, end = 10.dp).fillMaxWidth(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.transport_mode),
                            color = Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        // Spacer(modifier = Modifier.weight(1f))

                        DropdownMenuBox(
                            viewModel.transportModes,
                            viewModel.selectedTransportModeText,
                        ) { transportMode: Int ->
                            viewModel.didSelectNewTransportMode(transportMode)
                        }
                    }

                    if (viewModel.selectedTransportMode.value != ERouteTransportMode.Pedestrian.value) {
                        SelectionRow(
                            titleResId = R.string.range_type,
                            options = viewModel.rangeTypes,
                            selectedText = viewModel.selectedRangeTypeText,
                            onSelectionChanged = viewModel::didSelectNewRangeType,
                        )
                    }

                    if (viewModel.selectedTransportMode.value == ERouteTransportMode.Bicycle.value) {
                        SelectionRow(
                            titleResId = R.string.bike_type,
                            options = viewModel.bikeTypes,
                            selectedText = viewModel.selectedBikeTypeText,
                            onSelectionChanged = viewModel::didSelectNewBikeType,
                        )

                        Row(
                            modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp)
                                .fillMaxWidth(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                modifier = Modifier.padding(start = 5.dp),
                                text = viewModel.hillsFactorSlider.leftSideText.value,
                                color = Color.Black,
                                fontSize = 15.sp,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = viewModel.hillsFactorSlider.valueText.value,
                                color = Color.Black,
                                fontSize = 15.sp,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                modifier = Modifier.padding(end = 5.dp),
                                text = viewModel.hillsFactorSlider.rightSideText.value,
                                color = Color.Black,
                                fontSize = 15.sp,
                            )
                        }

                        Slider(
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                            value = viewModel.hillsFactorSlider.value.value,
                            onValueChange = { viewModel.didChangeHillsFactorSliderPosition(it) },
                            steps = viewModel.hillsFactorSlider.steps.value,
                            valueRange = viewModel.hillsFactorSlider.leftSide.value..viewModel.hillsFactorSlider.rightSide.value,
                        )
                    }

                    Text(
                        modifier = Modifier.padding(start = 10.dp, top = 10.dp),
                        text = stringResource(R.string.avoid),
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    AvoidOptions(viewModel = viewModel)
                }
            }
        }
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(viewModel)
    }
}

@Composable
private fun SelectionRow(
    @StringRes titleResId: Int,
    options: List<String>,
    selectedText: MutableState<String>,
    onSelectionChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 10.dp, end = 10.dp, top = 10.dp)
            .fillMaxWidth(1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleResId),
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.weight(1f))

        DropdownMenuBox(
            options = options,
            selectedText = selectedText,
            onSelectionChanged = onSelectionChanged,
        )
    }
}

@Composable
private fun AvoidOptions(viewModel: RangeFinderModel) {
    when (viewModel.selectedTransportMode.value) {
        ERouteTransportMode.Car.value -> {
            FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                AvoidItem(R.string.ferries, viewModel.carSettings.avoidFerries)
                AvoidItem(R.string.motorways, viewModel.carSettings.avoidMotorways)
                AvoidItem(R.string.unpaved_roads, viewModel.carSettings.avoidUnpavedRoads)
                AvoidItem(R.string.toll_roads, viewModel.carSettings.avoidTollRoads)
                AvoidItem(R.string.traffic, viewModel.carSettings.avoidTraffic)
            }
        }

        ERouteTransportMode.Lorry.value -> {
            FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                AvoidItem(R.string.ferries, viewModel.truckSettings.avoidFerries)
                AvoidItem(R.string.motorways, viewModel.truckSettings.avoidMotorways)
                AvoidItem(R.string.unpaved_roads, viewModel.truckSettings.avoidUnpavedRoads)
                AvoidItem(R.string.toll_roads, viewModel.truckSettings.avoidTollRoads)
                AvoidItem(R.string.traffic, viewModel.truckSettings.avoidTraffic)
            }
        }

        ERouteTransportMode.Pedestrian.value -> {
            FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                AvoidItem(R.string.ferries, viewModel.pedestrianSettings.avoidFerries)
                AvoidItem(R.string.unpaved_roads, viewModel.pedestrianSettings.avoidUnpavedRoads)
            }
        }

        ERouteTransportMode.Bicycle.value -> {
            FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                AvoidItem(R.string.ferries, viewModel.bicycleSettings.avoidFerries)
                AvoidItem(R.string.unpaved_roads, viewModel.bicycleSettings.avoidUnpavedRoads)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvoidItem(@StringRes textResId: Int, selected: MutableState<Boolean>) {
    FilterChip(
        modifier = Modifier.padding(end = 10.dp),
        onClick = { selected.value = !selected.value },
        label = {
            Text(
                text = stringResource(textResId),
                fontSize = 16.sp,
            )
        },
        selected = selected.value,
        leadingIcon = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuBox(options: List<String>, selectedText: MutableState<String>, onSelectionChanged: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp)),
    ) {
        Box(
            modifier = Modifier.padding(start = 10.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    expanded = !expanded
                    if (expanded) {
                        keyboardController?.hide()
                    }
                },
            ) {
                TextField(
                    value = selectedText.value,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    maxLines = 1,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = expanded,
                        )
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .border(2.dp, SolidColor(Color.Blue), shape = RoundedCornerShape(15.dp)),
                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    ),
                )

                ExposedDropdownMenu(
                    modifier = Modifier.background(Color.White),
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    for (i in options.indices) {
                        val isSelected = selectedText.value == options[i]

                        DropdownMenuItem(
                            text = { Text(text = options[i]) },
                            leadingIcon = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                )
                            },
                            onClick = {
                                selectedText.value = options[i]
                                expanded = false
                                onSelectionChanged(i)
                            },
                        )

                        if (i != options.lastIndex) {
                            HorizontalDivider(
                                color = Color.LightGray,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDialog(viewModel: RangeFinderModel) {
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

@Preview(showBackground = true)
@Composable
fun RangeFinderPreview() {
    RangeFinderTheme {
        RangeFinderScreen()
    }
}
