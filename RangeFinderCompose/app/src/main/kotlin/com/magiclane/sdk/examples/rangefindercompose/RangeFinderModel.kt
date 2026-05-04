/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.rangefindercompose

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ERouteDisplayMode
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.EEBikeType
import com.magiclane.sdk.routesandnavigation.ERouteRenderOptions
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.ERouteType
import com.magiclane.sdk.routesandnavigation.ETrafficAvoidance
import com.magiclane.sdk.routesandnavigation.ElectricBikeProfile
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RouteList
import com.magiclane.sdk.routesandnavigation.RouteRenderSettings
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.EnumHelp
import com.magiclane.sdk.util.SdkCall

class RangeFinderModel : ViewModel() {

    data class ColorInfo(var rgba: Rgba = Rgba(), var isInUse: Boolean = false)

    data class VehicleSettings(
        var rangeType: MutableState<Int> = mutableIntStateOf(0),
        var avoidFerries: MutableState<Boolean> = mutableStateOf(false),
        var avoidMotorways: MutableState<Boolean> = mutableStateOf(false),
        var avoidUnpavedRoads: MutableState<Boolean> = mutableStateOf(false),
        var avoidTollRoads: MutableState<Boolean> = mutableStateOf(false),
        var avoidTraffic: MutableState<Boolean> = mutableStateOf(false),
    )

    data class SliderInfo(
        var value: MutableState<Float>,
        var valueText: MutableState<String>,
        var leftSide: MutableState<Float>,
        var leftSideText: MutableState<String>,
        var rightSide: MutableState<Float>,
        var rightSideText: MutableState<String>,
        var steps: MutableState<Int>,
    )

    private lateinit var colors: MutableList<ColorInfo>

    private val routes = RouteList()

    private val routingService = RoutingService(
        onCompleted = { routes, errorCode, _ ->

            displayProgress = false
            addRangeButtonIsEnabled = true

            when (errorCode) {
                GemError.NoError ->
                    {
                        SdkCall.execute {
                            // if the process ended with no error add the new route to the route list
                            this@RangeFinderModel.routes.add(routes[0])

                            // then display
                            addRouteToMap(routes[0], newRange.renderingSettings)

                            centerRoutes()

                            ranges.add(newRange)
                        }
                    }
                else ->
                    {
                        // There was a problem at computing the routing operation.
                        if (errorCode != GemError.Cancel) {
                            errorMessage = GemError.getMessage(errorCode)
                        }

                        SdkCall.execute {
                            markColorUnused(newRange.borderColor)
                        }
                    }
            }
        },
    )

    var errorMessage by mutableStateOf("")

    @SuppressLint("StaticFieldLeak")
    var surfaceView: GemSurfaceView? = null

    @SuppressLint("StaticFieldLeak")
    var context: Context? = null

    private lateinit var animation: Animation

    private lateinit var newRange: Range

    private val maxItems = 10

    var displayProgress by mutableStateOf(false)

    var addRangeButtonIsEnabled by mutableStateOf(true)

    val rangeSlider = SliderInfo(
        value = mutableFloatStateOf(10f),
        valueText = mutableStateOf(""),
        leftSide = mutableFloatStateOf(1f),
        leftSideText = mutableStateOf(""),
        rightSide = mutableFloatStateOf(180f),
        rightSideText = mutableStateOf(""),
        steps = mutableIntStateOf(178),
    )

    val hillsFactorSlider = SliderInfo(
        value = mutableFloatStateOf(5f),
        valueText = mutableStateOf(""),
        leftSide = mutableFloatStateOf(0f),
        leftSideText = mutableStateOf(""),
        rightSide = mutableFloatStateOf(10f),
        rightSideText = mutableStateOf(""),
        steps = mutableIntStateOf(9),
    )

    var transportModes: List<String> by mutableStateOf(emptyList())

    var selectedTransportMode: MutableState<Int> = mutableIntStateOf(0)

    var selectedTransportModeText: MutableState<String> = mutableStateOf("")

    var rangeTypes: List<String> by mutableStateOf(emptyList())

    var selectedRangeTypeText: MutableState<String> = mutableStateOf("")

    var bikeTypes: List<String> by mutableStateOf(emptyList())

    private var selectedBikeType: MutableState<Int> = mutableIntStateOf(0)

    var selectedBikeTypeText: MutableState<String> = mutableStateOf("")

    val carSettings =
        VehicleSettings(
            avoidTraffic = mutableStateOf(true),
            avoidUnpavedRoads = mutableStateOf(true),
        )

    val truckSettings =
        VehicleSettings(
            avoidTraffic = mutableStateOf(true),
            avoidUnpavedRoads = mutableStateOf(true),
        )

    val pedestrianSettings = VehicleSettings()

    val bicycleSettings = VehicleSettings()

    private var selectedVehicleSettings = carSettings

    val ranges = mutableStateListOf<Range>()

    fun initializeStrings(context: Context) {
        val appContext = context.applicationContext
        val needsInitialization = this.context !== appContext ||
            transportModes.isEmpty() ||
            rangeTypes.isEmpty() ||
            bikeTypes.isEmpty() ||
            selectedTransportModeText.value.isEmpty() ||
            selectedRangeTypeText.value.isEmpty() ||
            selectedBikeTypeText.value.isEmpty()

        this.context = appContext

        if (!needsInitialization) {
            return
        }

        transportModes = transportModeLabels()
        bikeTypes = bikeTypeLabels()
        rangeTypes = rangeTypeLabels(selectedTransportMode.value)
        selectedTransportModeText.value = transportModeLabel(selectedTransportMode.value)
        selectedRangeTypeText.value = routeTypeLabel(selectedVehicleSettings.rangeType.value)
        selectedBikeTypeText.value = bikeTypeLabel(selectedBikeType.value)
        updateRangeSliderForSelectedRouteType()
        updateHillsFactorSliderTexts()
    }

    fun onSdkInitSucceeded(context: Context) {
        initializeStrings(context)

        animation = Animation(EAnimation.Linear)
        animation.duration = 900

        colors = SdkCall.execute {
            mutableListOf(
                ColorInfo(Rgba(52, 119, 235, 63), false),
                ColorInfo(Rgba(159, 122, 255, 63), false),
                ColorInfo(Rgba(195, 98, 217, 63), false),
                ColorInfo(Rgba(84, 73, 179, 63), false),
                ColorInfo(Rgba(212, 59, 156, 63), false),
                ColorInfo(Rgba(72, 153, 70, 63), false),
                ColorInfo(Rgba(237, 45, 45, 63), false),
                ColorInfo(Rgba(240, 160, 41, 63), false),
                ColorInfo(Rgba(245, 106, 47, 63), false),
                ColorInfo(Rgba(153, 89, 67, 63), false),
            )
        } ?: MutableList(maxItems) { ColorInfo() }
    }

    private fun addRouteToMap(route: Route, routeRenderSettings: RouteRenderSettings) {
        surfaceView?.mapView?.preferences?.routes?.addWithRenderSettings(route, routeRenderSettings)
    }

    private fun removeRouteFromMap(route: Route) {
        surfaceView?.mapView?.preferences?.routes?.remove(route)
    }

    /**
     * Utility function that centers the route on map in a predefined rectangle.
     * If no route is provided all routes will be centered.
     * Needs [SdkCall]
     */
    private fun centerRoutes(route: Route? = null) {
        val centeringPadding = context?.resources?.getDimensionPixelSize(R.dimen.big_padding) ?: 0
        val centeringRectangle = Rect(
            left = 0,
            top = 0,
            right = surfaceView?.measuredWidth ?: 0,
            bottom = surfaceView?.measuredHeight ?: 0,
        )

        if (!centeringRectangle.isEmpty() && (centeringPadding > 0)) {
            centeringRectangle.inflate(-centeringPadding, -centeringPadding)

            val rangePanelHeight = context?.resources?.getDimensionPixelSize(
                R.dimen.range_panel_height,
            ) ?: 0
            centeringRectangle.height -= rangePanelHeight
        }

        route?.let {
            surfaceView?.mapView?.centerOnRoute(route, centeringRectangle, animation)
        } ?: surfaceView?.mapView?.centerOnRoutes(
            routes,
            ERouteDisplayMode.Full,
            centeringRectangle,
            animation,
        )
    }

    fun didTapAddRangeButton() {
        // check to see if more ranges can be generated on map
        if (ranges.size >= maxItems) {
            errorMessage = getString(R.string.max_ranges_generated, maxItems)
        } else {
            val transportMode: ERouteTransportMode = EnumHelp.fromInt(selectedTransportMode.value)
            val routeType: ERouteType = EnumHelp.fromInt(selectedVehicleSettings.rangeType.value)

            val rangeValue = if (routeType == ERouteType.Fastest) {
                (rangeSlider.value.value * 60).toInt()
            } else {
                rangeSlider.value.value.toInt()
            }

            for (range in ranges) {
                if ((transportMode == range.transportMode()) &&
                    (rangeValue == range.rangeValue)
                ) {
                    errorMessage = GemError.getMessage(GemError.Exist)
                    return
                }
            }

            calculateRoute()
        }
    }

    // needs sdk call
    private fun getNewColor() = colors.find {
        !it.isInUse
    }?.apply { isInUse = true }?.rgba ?: Rgba.noColor()

    // needs sdk call
    private fun markColorUnused(rangeColor: Color) {
        for (color in colors) {
            if (color.rgba.red.toFloat() / 255f == rangeColor.red) {
                color.isInUse = false
                break
            }
        }
    }

    fun didSelectNewTransportMode(transportMode: Int) {
        selectedTransportMode.value = transportMode
        selectedTransportModeText.value = transportModeLabel(transportMode)

        rangeTypes = rangeTypeLabels(selectedTransportMode.value)

        val oldRangeTypeValue = selectedVehicleSettings.rangeType.value

        selectedVehicleSettings = when (selectedTransportMode.value) {
            ERouteTransportMode.Lorry.value ->
                {
                    truckSettings
                }
            ERouteTransportMode.Bicycle.value ->
                {
                    bicycleSettings
                }
            ERouteTransportMode.Pedestrian.value ->
                {
                    pedestrianSettings
                }
            else ->
                {
                    carSettings
                }
        }

        selectedRangeTypeText.value = routeTypeLabel(selectedVehicleSettings.rangeType.value)

        if (oldRangeTypeValue != selectedVehicleSettings.rangeType.value) {
            didSelectNewRangeType(selectedVehicleSettings.rangeType.value)
        }
    }

    fun didSelectNewRangeType(rangeType: Int) {
        selectedVehicleSettings.rangeType.value = if (selectedTransportMode.value == ERouteTransportMode.Bicycle.value) {
            if (rangeType == ERouteType.Fastest.value) {
                ERouteType.Fastest.value
            } else {
                ERouteType.Economic.value
            }
        } else {
            rangeType
        }

        selectedRangeTypeText.value = routeTypeLabel(selectedVehicleSettings.rangeType.value)
        updateRangeSliderForSelectedRouteType()
    }

    fun didSelectNewBikeType(bikeType: Int) {
        selectedBikeType.value = bikeType
        selectedBikeTypeText.value = bikeTypeLabel(bikeType)
    }

    fun didChangeRangeSliderPosition(position: Float) {
        rangeSlider.value.value = position
        when (selectedVehicleSettings.rangeType.value) {
            ERouteType.Fastest.value ->
                {
                    val minutes = (position + 0.5f).toInt()
                    rangeSlider.valueText.value = if (minutes < 60) {
                        getString(R.string.duration_minutes, minutes)
                    } else {
                        val hours = minutes / 60
                        val min = minutes % 60

                        if (min > 0) {
                            getString(R.string.duration_hours_minutes, hours, min)
                        } else {
                            getString(R.string.duration_hours, hours)
                        }
                    }
                }
            ERouteType.Shortest.value ->
                {
                    rangeSlider.valueText.value = getDistanceText((position + 0.5f).toInt())
                }
            ERouteType.Economic.value ->
                {
                    rangeSlider.valueText.value = getString(
                        R.string.energy_watt_hours,
                        (position + 0.5f).toInt(),
                    )
                }
        }
    }

    fun didChangeHillsFactorSliderPosition(position: Float) {
        hillsFactorSlider.value.value = position
        hillsFactorSlider.valueText.value = position.toInt().toString()
    }

    private fun getDistanceText(meters: Int): String {
        return if (meters < 1000) {
            getString(R.string.distance_meters, meters)
        } else {
            val kilometers = meters.toDouble() / 1000
            getString(R.string.distance_kilometers, kilometers)
        }
    }

    fun didTapRange(index: Int, enabled: Boolean) {
        if ((index >= 0) && (index < ranges.size)) {
            SdkCall.execute {
                if (enabled) {
                    addRouteToMap(routes[index], ranges[index].renderingSettings)
                } else {
                    removeRouteFromMap(routes[index])
                }
            }
        }
    }

    fun didTapRemoveRangeButton(index: Int) {
        if ((index >= 0) && (index < ranges.size)) {
            SdkCall.execute {
                markColorUnused(ranges.removeAt(index).borderColor)

                removeRouteFromMap(routes[index])
                routes.removeAt(index)

                centerRoutes()
            }
        }
    }

    fun zoomToRange(index: Int) {
        if ((index >= 0) && (index < ranges.size)) {
            SdkCall.execute {
                centerRoutes(routes[index])
            }
        }
    }

    private fun calculateRoute() = SdkCall.execute {
        val rangeType: ERouteType = EnumHelp.fromInt(selectedVehicleSettings.rangeType.value)

        val rangeValue = if (rangeType == ERouteType.Fastest) {
            (rangeSlider.value.value * 60).toInt()
        } else {
            rangeSlider.value.value.toInt()
        }

        val rangesList: ArrayList<Int> = arrayListOf(rangeValue)

        with(routingService.preferences) {
            // get an electric bike profile in case the Economic range type option is picked
            val electricBikeProfile = if ((selectedTransportMode.value == ERouteTransportMode.Bicycle.value) &&
                (selectedVehicleSettings.rangeType.value == ERouteType.Economic.value)
            ) {
                ElectricBikeProfile(EEBikeType.Pedelec, 15f, 75f, 2f, 4f)
            } else {
                null
            }

            // set your routing preferences according to your selected options
            transportMode = EnumHelp.fromInt(selectedTransportMode.value)
            routeType = EnumHelp.fromInt(selectedVehicleSettings.rangeType.value)

            setRouteRanges(rangesList, 100)

            if (selectedTransportMode.value == ERouteTransportMode.Bicycle.value) {
                avoidBikingHillFactor = (10f - hillsFactorSlider.value.value) / 10f
                setBikeProfile(EnumHelp.fromInt(selectedBikeType.value), electricBikeProfile)
            }

            avoidFerries = selectedVehicleSettings.avoidFerries.value
            avoidMotorways = selectedVehicleSettings.avoidMotorways.value
            avoidUnpavedRoads = selectedVehicleSettings.avoidUnpavedRoads.value
            avoidTollRoads = selectedVehicleSettings.avoidTollRoads.value
            avoidTraffic = if (selectedVehicleSettings.avoidTraffic.value) ETrafficAvoidance.All else ETrafficAvoidance.None
        }

        val errorCode = routingService.calculateRoute(
            arrayListOf(Landmark("London", 51.5073204, -0.1276475)),
        )
        if (errorCode == GemError.NoError) {
            displayProgress = true
            addRangeButtonIsEnabled = false

            val color = getNewColor()

            val renderingSettings = RouteRenderSettings().also {
                it.innerSize = 0.3
                it.outerSize = 0.3

                it.outerColor = color
                it.innerColor = color

                it.options = ERouteRenderOptions.Main.value
            }

            val drawableResource = when (routingService.preferences.transportMode) {
                ERouteTransportMode.Car -> R.drawable.car
                ERouteTransportMode.Lorry -> R.drawable.truck
                ERouteTransportMode.Pedestrian -> R.drawable.pedestrian
                ERouteTransportMode.Bicycle -> R.drawable.bike
                else -> R.drawable.car
            }

            newRange = Range(
                drawableResource,
                rangeSlider.valueText.value,
                Color(color.red, color.green, color.blue, 255),
                mutableStateOf(true),
                rangeValue,
                renderingSettings,
            )
        } else {
            errorMessage = GemError.getMessage(errorCode)
        }
    }

    private fun updateRangeSliderForSelectedRouteType() {
        when (selectedVehicleSettings.rangeType.value) {
            ERouteType.Fastest.value -> {
                rangeSlider.value.value = 10f
                rangeSlider.valueText.value = getString(R.string.duration_minutes, 10)
                rangeSlider.leftSide.value = 1f
                rangeSlider.leftSideText.value = getString(R.string.duration_minutes, 1)
                rangeSlider.rightSide.value = 180f
                rangeSlider.rightSideText.value = getString(R.string.duration_hours, 3)
                rangeSlider.steps.value = 178
            }

            ERouteType.Shortest.value -> {
                rangeSlider.value.value = 1000f
                rangeSlider.valueText.value = getString(R.string.distance_kilometers, 1.0)
                rangeSlider.leftSide.value = 100f
                rangeSlider.leftSideText.value = getString(R.string.distance_meters, 100)
                rangeSlider.rightSide.value = 200000f
                rangeSlider.rightSideText.value = getString(R.string.distance_kilometers, 200.0)
                rangeSlider.steps.value = 1998
            }

            ERouteType.Economic.value -> {
                rangeSlider.value.value = 100f
                rangeSlider.valueText.value = getString(R.string.energy_watt_hours, 100)
                rangeSlider.leftSide.value = 10f
                rangeSlider.leftSideText.value = getString(R.string.energy_watt_hours, 10)
                rangeSlider.rightSide.value = 2000f
                rangeSlider.rightSideText.value = getString(R.string.energy_watt_hours, 2000)
                rangeSlider.steps.value = 198
            }
        }
    }

    private fun updateHillsFactorSliderTexts() {
        hillsFactorSlider.valueText.value = hillsFactorSlider.value.value.toInt().toString()
        hillsFactorSlider.leftSideText.value = getString(R.string.hills_factor_avoid)
        hillsFactorSlider.rightSideText.value = getString(R.string.hills_factor_allow)
    }

    private fun transportModeLabels() = listOf(
        getString(R.string.transport_mode_car),
        getString(R.string.transport_mode_truck),
        getString(R.string.transport_mode_pedestrian),
        getString(R.string.transport_mode_bicycle),
    )

    private fun rangeTypeLabels(transportMode: Int) = if (transportMode == ERouteTransportMode.Bicycle.value) {
        listOf(
            getString(R.string.route_type_fastest),
            getString(R.string.route_type_economic),
        )
    } else {
        listOf(
            getString(R.string.route_type_fastest),
            getString(R.string.route_type_shortest),
        )
    }

    private fun bikeTypeLabels() = listOf(
        getString(R.string.bike_type_road),
        getString(R.string.bike_type_cross),
        getString(R.string.bike_type_city),
        getString(R.string.bike_type_mountain),
    )

    private fun transportModeLabel(transportMode: Int) = when (transportMode) {
        ERouteTransportMode.Lorry.value -> getString(R.string.transport_mode_truck)
        ERouteTransportMode.Pedestrian.value -> getString(R.string.transport_mode_pedestrian)
        ERouteTransportMode.Bicycle.value -> getString(R.string.transport_mode_bicycle)
        else -> getString(R.string.transport_mode_car)
    }

    private fun routeTypeLabel(rangeType: Int) = when (rangeType) {
        ERouteType.Fastest.value -> getString(R.string.route_type_fastest)
        ERouteType.Shortest.value -> getString(R.string.route_type_shortest)
        ERouteType.Economic.value -> getString(R.string.route_type_economic)
        else -> ""
    }

    private fun bikeTypeLabel(bikeType: Int) = bikeTypes.getOrElse(bikeType) { "" }

    private fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        val appContext = context ?: return ""
        return if (formatArgs.isEmpty()) {
            appContext.getString(resId)
        } else {
            appContext.getString(resId, *formatArgs)
        }
    }
}
