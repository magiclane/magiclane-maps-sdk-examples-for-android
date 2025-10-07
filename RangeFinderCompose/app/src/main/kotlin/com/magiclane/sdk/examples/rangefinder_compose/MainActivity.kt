// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.rangefinder_compose

// ---------------------------------------------------------------------------------------------------------------------------------------------------

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.rangefinder_compose.ui.theme.RangeFinderTheme
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode

// ---------------------------------------------------------------------------------------------------------------------------------------------------

class MainActivity : ComponentActivity()
{
    // -----------------------------------------------------------------------------------------------------------------------------------------------

    private lateinit var viewModel: RangeFinderModel

    // -----------------------------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContent {
            RangeFinderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    viewModel = viewModel()
                    RangeFinderScreen(viewModel = viewModel)
                }
            }
        }

        SdkSettings.onMapDataReady = { isReady ->
            if (isReady)
            {
                viewModel.onMapDataReady(this)
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
        viewModel.context = null

        if (isFinishing)
        {
            GemSdk.release() // Release the SDK.
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------------------------
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RangeFinderScreen(modifier: Modifier = Modifier, viewModel: RangeFinderModel = viewModel())
{
    if (viewModel.surfaceView == null)
    {
        viewModel.surfaceView = GemSurfaceView(LocalContext.current)
    }

    AndroidView(factory = { viewModel.surfaceView!! })

    Column {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            Modifier
                .padding(top = 5.dp)
                .clip(shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                .background(Color.White)
                .heightIn(0.dp, 300.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(1f)
        )
        {
            val alpha = if (viewModel.displayProgress) 1f else 0f
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth(1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .alpha(alpha))

            LazyRow() {
                itemsIndexed(viewModel.ranges) { index, range ->
                    RangeItem(range.imageResourceId, range.text, borderColor = range.borderColor, enabled = range.enabled, onItemClick = {
                        range.enabled.value = !range.enabled.value
                        viewModel.didTapRange(index, range.enabled.value)
                    },
                    onItemLongClick = {
                        viewModel.zoomToRange(index)
                    },
                    onDeleteItemClick = {
                        viewModel.didTapRemoveRangeButton(index)
                    })
                }
            }

            Row (
                modifier
                    .padding(all = 10.dp)
                    .fillMaxWidth(1f),
                 verticalAlignment = Alignment.CenterVertically)
            {
                Text(modifier = Modifier.padding(start = 10.dp),
                    text = "Range Value",
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = { viewModel.didTapAddRangeButton() },
                       enabled = viewModel.addRangeButtonIsEnabled)
                {
                    Text(text = "+",
                         fontSize = 20.sp)
                }
            }

            Row (modifier.padding(start = 10.dp, end = 10.dp)
                         .fillMaxWidth(1f),
                verticalAlignment = Alignment.CenterVertically)
            {
                Text(modifier = Modifier.padding(start = 5.dp),
                     text = viewModel.rangeSlider.leftSideText.value,
                     color = Color.Black,
                     fontSize = 15.sp)

                Spacer(modifier = Modifier.weight(1f))

                Text(text = viewModel.rangeSlider.valueText.value,
                     color = Color.Black,
                     fontSize = 15.sp)

                Spacer(modifier = Modifier.weight(1f))

                Text(modifier = Modifier.padding(end = 5.dp),
                     text = viewModel.rangeSlider.rightSideText.value,
                     color = Color.Black,
                     fontSize = 15.sp)
            }

            Slider(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                value = viewModel.rangeSlider.value.value,
                onValueChange = { viewModel.didChangeRangeSliderPosition(it) },
                steps = viewModel.rangeSlider.steps.value,
                valueRange = viewModel.rangeSlider.leftSide.value..viewModel.rangeSlider.rightSide.value
            )

            Row (modifier.padding(start = 10.dp, end = 10.dp).fillMaxWidth(1f),
                 verticalAlignment = Alignment.CenterVertically)
            {
                Text(text = "Transport Mode",
                     color = Color.Black,
                     fontSize = 20.sp,
                     fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.weight(1f))

                DropdownMenuBox(viewModel.transportModes, viewModel.selectedTransportModeText) { transportMode: Int ->
                    viewModel.didSelectNewTransportMode(transportMode)
                }
            }

            if (viewModel.selectedTransportMode.value != ERouteTransportMode.Pedestrian.value)
            {
                Row (modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp).fillMaxWidth(1f),
                    verticalAlignment = Alignment.CenterVertically)
                {
                    Text(text = "Range Type",
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.weight(1f))

                    DropdownMenuBox(viewModel.rangeTypes, viewModel.selectedRangeTypeText) { rangeType ->
                        viewModel.didSelectNewRangeType(rangeType)
                    }
                }
            }

            if (viewModel.selectedTransportMode.value == ERouteTransportMode.Bicycle.value)
            {
                Row (modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp).fillMaxWidth(1f),
                    verticalAlignment = Alignment.CenterVertically)
                {
                    Text(text = "Bike Type",
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.weight(1f))

                    DropdownMenuBox(viewModel.bikeTypes, viewModel.selectedBikeTypeText) { bikeType ->
                        viewModel.didSelectNewBikeType(bikeType)
                    }
                }

                Row (modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp)
                    .fillMaxWidth(1f),
                    verticalAlignment = Alignment.CenterVertically)
                {
                    Text(modifier = Modifier.padding(start = 5.dp),
                        text = viewModel.hillsFactorSlider.leftSideText.value,
                        color = Color.Black,
                        fontSize = 15.sp)

                    Spacer(modifier = Modifier.weight(1f))

                    Text(text = viewModel.hillsFactorSlider.valueText.value,
                        color = Color.Black,
                        fontSize = 15.sp)

                    Spacer(modifier = Modifier.weight(1f))

                    Text(modifier = Modifier.padding(end = 5.dp),
                        text = viewModel.hillsFactorSlider.rightSideText.value,
                        color = Color.Black,
                        fontSize = 15.sp)
                }

                Slider(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                    value = viewModel.hillsFactorSlider.value.value,
                    onValueChange = { viewModel.didChangeHillsFactorSliderPosition(it) },
                    steps = viewModel.hillsFactorSlider.steps.value,
                    valueRange = viewModel.hillsFactorSlider.leftSide.value..viewModel.hillsFactorSlider.rightSide.value
                )
            }

            Text(modifier = Modifier.padding(start = 10.dp, top = 10.dp),
                 text = "Avoid",
                 color = Color.Black,
                 fontSize = 20.sp,
                 fontWeight = FontWeight.Bold)

            when (viewModel.selectedTransportMode.value)
            {
                ERouteTransportMode.Car.value ->
                {
                    FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                        AvoidItem("Ferries", viewModel.carSettings.avoidFerries)
                        AvoidItem("Motorways", viewModel.carSettings.avoidMotorways)
                        AvoidItem("Unpaved Roads", viewModel.carSettings.avoidUnpavedRoads)
                        AvoidItem("Toll Roads", viewModel.carSettings.avoidTollRoads)
                        AvoidItem("Traffic", viewModel.carSettings.avoidTraffic)
                    }
                }
                ERouteTransportMode.Lorry.value ->
                {
                    FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                        AvoidItem("Ferries", viewModel.truckSettings.avoidFerries)
                        AvoidItem("Motorways", viewModel.truckSettings.avoidMotorways)
                        AvoidItem("Unpaved Roads", viewModel.truckSettings.avoidUnpavedRoads)
                        AvoidItem("Toll Roads", viewModel.truckSettings.avoidTollRoads)
                        AvoidItem("Traffic", viewModel.truckSettings.avoidTraffic)
                    }
                }
                ERouteTransportMode.Pedestrian.value ->
                {
                    FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                        AvoidItem("Ferries", viewModel.pedestrianSettings.avoidFerries)
                        AvoidItem("Unpaved Roads", viewModel.pedestrianSettings.avoidUnpavedRoads)
                    }
                }
                ERouteTransportMode.Bicycle.value ->
                {
                    FlowRow(modifier = Modifier.padding(horizontal = 10.dp)) {
                        AvoidItem("Ferries", viewModel.bicycleSettings.avoidFerries)
                        AvoidItem("Unpaved Roads", viewModel.bicycleSettings.avoidUnpavedRoads)
                    }
                }
            }
        }
    }

    if (viewModel.errorMessage.isNotEmpty())
    {
        ErrorDialog(viewModel)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvoidItem(text: String, selected: MutableState<Boolean>)
{
    FilterChip(
        modifier = Modifier.padding(end = 10.dp),
        onClick = { selected.value = !selected.value },
        label = {
            Text(text = text,
                fontSize = 16.sp)
        },
        selected = selected.value,
        leadingIcon = null
    )
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuBox(options: List<String>, selectedText: MutableState<String>, onSelectionChanged: (Int)->Unit)
{
    var expanded by remember { mutableStateOf(false) }

    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier.padding(start = 30.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    expanded = !expanded
                }
            ) {

                CompositionLocalProvider(
                    LocalTextInputService provides null
                ) {
                    TextField(
                        value = selectedText.value,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .border(2.dp, SolidColor(Color.Blue), shape = RoundedCornerShape(15.dp)),
                        colors = ExposedDropdownMenuDefaults.textFieldColors(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )
                }

                ExposedDropdownMenu(
                    modifier = Modifier.background(Color.White),
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    for (i in options.indices)
                    {
                        DropdownMenuItem(
                            text = { Text(text = options[i]) },
                            onClick = {
                                selectedText.value = options[i]
                                expanded = false
                                onSelectionChanged(i)
                            }
                        )

                        if (i != options.lastIndex)
                        {
                            Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(horizontal = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun ErrorDialog(viewModel: RangeFinderModel)
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

@Preview(showBackground = true)
@Composable
fun RangeFinderPreview() {
    RangeFinderTheme {
        RangeFinderScreen()
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------
