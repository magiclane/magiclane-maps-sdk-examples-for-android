// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.rangefinder

// -------------------------------------------------------------------------------------------------------------------------------

import androidx.lifecycle.ViewModel
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.routesandnavigation.EBikeProfile
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.ERouteType
import com.magiclane.sdk.routesandnavigation.RouteList
import com.magiclane.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------------------------------------

internal const val MAX_ITEMS = 10

class MainActivityViewModel : ViewModel()
{
    data class ColorInfo(
        var rgba: Rgba = Rgba(),
        var isInUse: Boolean = false
    )
    {
        override fun equals(other: Any?): Boolean
        {
            if (other is ColorInfo)
            {
                other.let { color ->
                    return SdkCall.execute {
                        color.rgba.red == this.rgba.red &&
                                color.rgba.blue == this.rgba.blue &&
                                color.rgba.green == this.rgba.green
                    } ?: false
                }
            }
            else throw IllegalArgumentException()

        }

        override fun hashCode(): Int
        {
            var result = rgba.hashCode()
            result = 31 * result + isInUse.hashCode()
            return result
        }
    }

    private lateinit var colors : MutableList<ColorInfo>

    
    fun load(){
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
                ColorInfo(Rgba(153, 89, 67, 63), false)
            )
        } ?: MutableList(MAX_ITEMS) { ColorInfo() }
        cashList = MutableList(listOfTransportTypes.size) { RangeSettingsProfile() }
        currentRangeSettingsProfile = cashList[0]
    }
    
    // -------------------------------------------------------------------------------------------------------------------------------

    val listOfRangeProfiles = mutableListOf<RangeSettingsProfile>()
    val listOfRoutes = RouteList()

    val listOfTransportTypes = ArrayList(
        mutableListOf(
            ERouteTransportMode.Car,
            ERouteTransportMode.Lorry,
            ERouteTransportMode.Pedestrian,
            ERouteTransportMode.Bicycle
        )
    )

    //cashes the choices made for each transport mode
    lateinit var cashList : MutableList<RangeSettingsProfile>

    lateinit var currentRangeSettingsProfile :RangeSettingsProfile 

    // -------------------------------------------------------------------------------------------------------------------------------

    val listOfBikeTypes = ArrayList(EBikeProfile.values().toList())

    val listOfBicycleRangeTypes = ArrayList(
        mutableListOf(
            ERouteType.Fastest,
            ERouteType.Economic,
        )
    )

    val listOfRangeTypes = ArrayList(
        mutableListOf(
            ERouteType.Fastest,
            ERouteType.Shortest,
        )
    )
    // -------------------------------------------------------------------------------------------------------------------------------

    //needs sdk call
    fun getNewColor() = colors.find { !it.isInUse }?.apply {
        cashList.forEach { it.color = rgba }
        isInUse = true }?.rgba ?: Rgba.noColor()

    // -------------------------------------------------------------------------------------------------------------------------------

    //needs sdk call
    fun resetColor(index: Int) {
        colors[index].isInUse = false
    }
    // -------------------------------------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------------------------------------
