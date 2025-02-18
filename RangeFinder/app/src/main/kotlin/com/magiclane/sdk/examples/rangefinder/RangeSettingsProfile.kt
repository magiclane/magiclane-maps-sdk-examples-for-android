// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.rangefinder

// -------------------------------------------------------------------------------------------------------------------------------
import androidx.databinding.BaseObservable
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.routesandnavigation.EBikeProfile
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.ERouteType
import com.magiclane.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------------------------------------

/***
 * A [BaseObservable] that notifies listeners on the following member changes: [transportMode],
 * [rangeType], [bikeType]
 */
class RangeSettingsProfile : BaseObservable()
{
    // -------------------------------------------------------------------------------------------------------------------------------

    var transportMode: ERouteTransportMode = ERouteTransportMode.Car
        set(value)
        {
            field = value
            notifyChange()
        }
    var rangeType: ERouteType = ERouteType.Fastest
        set(value)
        {
            field = value
            notifyChange()
        }
    var bikeType: EBikeProfile = EBikeProfile.Road
        set(value)
        {
            field = value
            notifyChange()
        }
    var bikeWeight: Int = 0
    var bikerWeight: Int = 0
    var rangeValue: Int = 0
    var color : Rgba = SdkCall.execute { Rgba.noColor()} !!
    var isDisplayed = true

    // -------------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------

/**
 * Extension function of [RangeSettingsProfile] that returns a copy of the object
 */
fun RangeSettingsProfile.copy() = RangeSettingsProfile().also {
    it.transportMode = this.transportMode
    it.rangeType = this.rangeType
    it.bikeType = this.bikeType
    it.bikeWeight = this.bikeWeight
    it.bikerWeight = this.bikerWeight
    it.rangeValue = this.rangeValue
}
// -------------------------------------------------------------------------------------------------------------------------------