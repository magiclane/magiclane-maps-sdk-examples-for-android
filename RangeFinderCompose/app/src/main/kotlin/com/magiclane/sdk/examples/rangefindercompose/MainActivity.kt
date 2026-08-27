/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.rangefindercompose

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.common.FilterChipRow
import com.magiclane.sdk.compose.components.settings.DropdownSettingRow
import com.magiclane.sdk.compose.components.settings.SliderSettingRow
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.sdk.SdkInitState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.compose.ui.AdaptivePanelScaffold
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
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
            MagicLaneTheme {
                RangeFinderApp(viewModel)
            }
        }

        // The example needs the network to compute routes; warn the user if there is none.
        if (!Util.isInternetConnected(this)) {
            viewModel.errorMessage = getString(R.string.internet_required)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        GemSdk.release() // Release the SDK.
        exitProcess(0)
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
}

@Composable
fun RangeFinderApp(viewModel: RangeFinderModel = viewModel()) {
    val context = LocalContext.current

    // The map surface lifecycle, the SDK listeners and the free-map-area / logo placement
    // are all handled by the maps-compose library.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()

    LaunchedEffect(mapState) {
        viewModel.initialize(mapState)
    }

    LaunchedEffect(sdkState.initState) {
        if (sdkState.initState == SdkInitState.Ready) {
            viewModel.onSdkInitSucceeded()
        }
    }

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    AdaptivePanelScaffold(
        mapState = mapState,
        modifier = Modifier.fillMaxSize().background(color = Color.Black),
        panel = { RangePanel(viewModel = viewModel) },
        portraitPanelMaxHeight = PORTRAIT_PANEL_HEIGHT,
        landscapePanelWidthFraction = LANDSCAPE_PANEL_WIDTH_FRACTION,
        // The panel deliberately ignores the bottom inset so it draws edge-to-edge under the
        // transparent navigation bar.
        portraitPanelInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        landscapePanelInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Start + WindowInsetsSides.Top,
        ),
        map = {
            GemMap(
                modifier = Modifier.fillMaxSize(),
                mapState = mapState,
                sdkState = sdkState,
                onSdkInitFailed = { error ->
                    // The SDK is not initialized here, so resolve the message directly (no SdkCall).
                    viewModel.errorMessage = context.getString(
                        R.string.sdk_initialization_failed,
                        GemError.getMessage(error, context),
                    )
                },
            )
        },
    )

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            onDismiss = { viewModel.errorMessage = "" },
            title = null,
            confirmText = stringResource(R.string.ok),
        )
    }
}

// Options panel hosting the range list and the routing controls. Its placement (bottom edge in
// portrait, full-height start card in landscape), corner rounding, insets and the map free-area
// bookkeeping are provided by AdaptivePanelScaffold.
@Composable
fun RangePanel(viewModel: RangeFinderModel) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize(),
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

            // Slider, dropdown and filter-chip rows come from the maps-compose-components
            // settings/common catalogs; only the range list and its add/remove chrome stay local.
            SliderSettingRow(
                label = "",
                value = viewModel.rangeSlider.value.value,
                onValueChange = { viewModel.didChangeRangeSliderPosition(it) },
                valueText = viewModel.rangeSlider.valueText.value,
                valueRange = viewModel.rangeSlider.leftSide.value..viewModel.rangeSlider.rightSide.value,
                steps = viewModel.rangeSlider.steps.value,
                minText = viewModel.rangeSlider.leftSideText.value,
                maxText = viewModel.rangeSlider.rightSideText.value,
            )

            DropdownSettingRow(
                label = stringResource(R.string.transport_mode),
                options = viewModel.transportModes,
                selectedOption = viewModel.selectedTransportModeText.value,
                onSelectionChanged = viewModel::didSelectNewTransportMode,
            )

            if (viewModel.selectedTransportMode.value != ERouteTransportMode.Pedestrian.value) {
                DropdownSettingRow(
                    label = stringResource(R.string.range_type),
                    options = viewModel.rangeTypes,
                    selectedOption = viewModel.selectedRangeTypeText.value,
                    onSelectionChanged = viewModel::didSelectNewRangeType,
                )
            }

            if (viewModel.selectedTransportMode.value == ERouteTransportMode.Bicycle.value) {
                DropdownSettingRow(
                    label = stringResource(R.string.bike_type),
                    options = viewModel.bikeTypes,
                    selectedOption = viewModel.selectedBikeTypeText.value,
                    onSelectionChanged = viewModel::didSelectNewBikeType,
                )

                SliderSettingRow(
                    label = stringResource(R.string.hills_factor),
                    value = viewModel.hillsFactorSlider.value.value,
                    onValueChange = { viewModel.didChangeHillsFactorSliderPosition(it) },
                    valueText = viewModel.hillsFactorSlider.valueText.value,
                    valueRange = viewModel.hillsFactorSlider.leftSide.value..viewModel.hillsFactorSlider.rightSide.value,
                    steps = viewModel.hillsFactorSlider.steps.value,
                    minText = viewModel.hillsFactorSlider.leftSideText.value,
                    maxText = viewModel.hillsFactorSlider.rightSideText.value,
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

// Maps the selected transport mode to its avoidance flags and shows them as library filter chips.
@Composable
private fun AvoidOptions(viewModel: RangeFinderModel) {
    val avoidOptions = when (viewModel.selectedTransportMode.value) {
        ERouteTransportMode.Car.value -> listOf(
            R.string.ferries to viewModel.carSettings.avoidFerries,
            R.string.motorways to viewModel.carSettings.avoidMotorways,
            R.string.unpaved_roads to viewModel.carSettings.avoidUnpavedRoads,
            R.string.toll_roads to viewModel.carSettings.avoidTollRoads,
            R.string.traffic to viewModel.carSettings.avoidTraffic,
        )

        ERouteTransportMode.Lorry.value -> listOf(
            R.string.ferries to viewModel.truckSettings.avoidFerries,
            R.string.motorways to viewModel.truckSettings.avoidMotorways,
            R.string.unpaved_roads to viewModel.truckSettings.avoidUnpavedRoads,
            R.string.toll_roads to viewModel.truckSettings.avoidTollRoads,
            R.string.traffic to viewModel.truckSettings.avoidTraffic,
        )

        ERouteTransportMode.Pedestrian.value -> listOf(
            R.string.ferries to viewModel.pedestrianSettings.avoidFerries,
            R.string.unpaved_roads to viewModel.pedestrianSettings.avoidUnpavedRoads,
        )

        ERouteTransportMode.Bicycle.value -> listOf(
            R.string.ferries to viewModel.bicycleSettings.avoidFerries,
            R.string.unpaved_roads to viewModel.bicycleSettings.avoidUnpavedRoads,
        )

        else -> emptyList()
    }

    FilterChipRow(
        labels = avoidOptions.map { stringResource(it.first) },
        selected = avoidOptions.map { it.second.value },
        onToggle = { index ->
            val flag = avoidOptions[index].second
            flag.value = !flag.value
        },
        modifier = Modifier.padding(horizontal = 10.dp),
    )
}
