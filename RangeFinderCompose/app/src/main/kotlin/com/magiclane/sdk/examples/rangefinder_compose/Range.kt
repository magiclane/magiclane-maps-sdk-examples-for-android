// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.rangefinder_compose

// --------------------------------------------------------------------------------------------------------------------------------------------------

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RouteRenderSettings

// --------------------------------------------------------------------------------------------------------------------------------------------------

data class Range(
    val imageResourceId: Int,
    val text: String,
    val borderColor: Color,
    val enabled: MutableState<Boolean>,
    val rangeValue: Int,
    val renderingSettings: RouteRenderSettings
)
{
    fun transportMode(): ERouteTransportMode
    {
        return when (imageResourceId)
        {
            R.drawable.car -> ERouteTransportMode.Car
            R.drawable.bike -> ERouteTransportMode.Bicycle
            R.drawable.truck -> ERouteTransportMode.Lorry
            R.drawable.pedestrian -> ERouteTransportMode.Pedestrian
            else -> ERouteTransportMode.Car
        }
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
