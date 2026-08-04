/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.rangefindercompose

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.rangefindercompose.ui.theme.RangeFinderTheme
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Fraction of the screen width the options panel occupies when shown as a full-height left card in
// landscape.
private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.5f

// Height of the options panel when it spans the bottom edge in portrait.
private val PORTRAIT_PANEL_HEIGHT = 410.dp

class MainActivity : ComponentActivity() {

    private val viewModel: RangeFinderModel by viewModels()

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
            RangeFinderTheme {
                RangeFinderApp(viewModel) { setMapSurfaceView(it) }
            }
        }

        registerSdkListeners()

        // The example needs the network to compute routes; warn the user if there is none.
        if (!Util.isInternetConnected(this)) {
            viewModel.errorMessage = getString(R.string.internet_required)
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
        SdkSettings.onApiTokenRejected = {
            viewModel.errorMessage = getString(R.string.token_rejected_message)
        }
    }

    // Clears SDK-level and map surface listeners so callbacks never reach a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        if (::mapSurfaceView.isInitialized) {
            mapSurfaceView.onSdkInitSucceeded = {}
            mapSurfaceView.onSdkInitFailed = {}
            mapSurfaceView.onSurfaceChanged = null
        }
    }

    private fun configureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // enableEdgeToEdge() sets a transparent navigation bar, but SystemBarStyle.auto also turns on
        // contrast enforcement, which paints a translucent scrim that looks white over light content.
        // Disable it so the navigation bar is truly transparent (API 29+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun setMapSurfaceView(view: GemSurfaceView) {
        mapSurfaceView = view
    }
}

@Composable
fun RangeFinderApp(viewModel: RangeFinderModel = viewModel(), mapSurfaceViewSetter: (GemSurfaceView) -> Unit) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Keep the model's orientation in sync and recompute the free map area on rotation so the
    // Magic Lane logo and the route centering stay clear of the options panel.
    LaunchedEffect(isLandscape) {
        viewModel.isLandscape = isLandscape
        viewModel.updateMapAreas()
    }

    Box(Modifier.fillMaxSize().background(color = Color.Black)) {
        // Full-screen map. The Magic Lane logo is kept inside the visible area via the focus
        // viewport (see RangeFinderModel.updateMapAreas), so it never hides behind the panel.
        MapSurface(Modifier.fillMaxSize(), mapSurfaceViewSetter, viewModel)

        // Portrait: the panel spans the bottom edge. Landscape: it becomes a full-height card
        // pinned to the left edge, leaving the rest of the map visible.
        RangePanel(
            modifier = if (isLandscape) {
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(LANDSCAPE_PANEL_WIDTH_FRACTION)
                    .fillMaxHeight()
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(PORTRAIT_PANEL_HEIGHT)
            },
            viewModel = viewModel,
            isLandscape = isLandscape,
        )
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(viewModel)
    }
}

@Composable
fun MapSurface(
    modifier: Modifier = Modifier,
    mapSurfaceViewSetter: (GemSurfaceView) -> Unit,
    viewModel: RangeFinderModel,
) {
    AndroidView(modifier = modifier, factory = { context ->
        GemSurfaceView(context).also { surfaceView ->
            surfaceView.onSdkInitSucceeded = {
                viewModel.onSdkInitSucceeded()
                // Store the surface and position the Magic Lane logo as soon as the map is ready.
                viewModel.initialize(surfaceView)
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

// Options panel hosting the range list and the routing controls. Spans the bottom edge in portrait
// and becomes a full-height card on the left in landscape.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RangePanel(modifier: Modifier = Modifier, viewModel: RangeFinderModel, isLandscape: Boolean) {
    val scrollState = rememberScrollState()

    // Round only the corners that face the map interior.
    val shape = if (isLandscape) {
        RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp)
    } else {
        RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    }

    // Content must clear the side/top system bars, but in portrait it deliberately ignores the
    // bottom inset so the panel draws under the transparent navigation bar (edge-to-edge). In
    // landscape the panel hugs the left edge, so only the start and top insets apply.
    val insetSides = if (isLandscape) {
        WindowInsetsSides.Start + WindowInsetsSides.Top
    } else {
        WindowInsetsSides.Horizontal
    }

    Surface(
        modifier = modifier.onGloballyPositioned { coordinates ->
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
                viewModel.updateMapAreas()
            }
        },
        shape = shape,
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(insetSides),
                ),
        ) {
            // Indeterminate progress bar shown while a range route is being computed (kept in the
            // layout via alpha so the content below does not jump).
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .alpha(if (viewModel.displayProgress) 1f else 0f),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .verticalScroll(scrollState),
            ) {
                RangeList(viewModel)

                RangeValueRow(viewModel)

                SliderRow(
                    leftSideText = viewModel.rangeSlider.leftSideText.value,
                    valueText = viewModel.rangeSlider.valueText.value,
                    rightSideText = viewModel.rangeSlider.rightSideText.value,
                )

                Slider(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    value = viewModel.rangeSlider.value.value,
                    onValueChange = { viewModel.didChangeRangeSliderPosition(it) },
                    steps = viewModel.rangeSlider.steps.value,
                    valueRange = viewModel.rangeSlider.leftSide.value..viewModel.rangeSlider.rightSide.value,
                )

                Row(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(stringResource(R.string.transport_mode))

                    Spacer(modifier = Modifier.weight(1f))

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

                    SliderRow(
                        modifier = Modifier.padding(top = 10.dp),
                        leftSideText = viewModel.hillsFactorSlider.leftSideText.value,
                        valueText = viewModel.hillsFactorSlider.valueText.value,
                        rightSideText = viewModel.hillsFactorSlider.rightSideText.value,
                    )

                    Slider(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        value = viewModel.hillsFactorSlider.value.value,
                        onValueChange = { viewModel.didChangeHillsFactorSliderPosition(it) },
                        steps = viewModel.hillsFactorSlider.steps.value,
                        valueRange = viewModel.hillsFactorSlider.leftSide.value..viewModel.hillsFactorSlider.rightSide.value,
                    )
                }

                SectionTitle(
                    text = stringResource(R.string.avoid),
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp),
                )

                AvoidOptions(viewModel = viewModel)
            }
        }
    }
}

// Horizontally scrollable list of the ranges currently shown on the map.
@Composable
private fun RangeList(viewModel: RangeFinderModel) {
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
}

// "Range Value" title together with the button that adds a new range.
@Composable
private fun RangeValueRow(viewModel: RangeFinderModel) {
    Row(
        Modifier
            .padding(all = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(
            text = stringResource(R.string.range_value),
            modifier = Modifier.padding(start = 10.dp),
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
}

// Row showing a slider's left bound, current value and right bound.
@Composable
private fun SliderRow(modifier: Modifier = Modifier, leftSideText: String, valueText: String, rightSideText: String) {
    Row(
        modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.padding(start = 5.dp),
            text = leftSideText,
            color = Color.Black,
            fontSize = 15.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = valueText,
            color = Color.Black,
            fontSize = 15.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            modifier = Modifier.padding(end = 5.dp),
            text = rightSideText,
            color = Color.Black,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        color = Color.Black,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
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
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(stringResource(titleResId))

        Spacer(modifier = Modifier.weight(1f))

        DropdownMenuBox(
            options = options,
            selectedText = selectedText,
            onSelectionChanged = onSelectionChanged,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
