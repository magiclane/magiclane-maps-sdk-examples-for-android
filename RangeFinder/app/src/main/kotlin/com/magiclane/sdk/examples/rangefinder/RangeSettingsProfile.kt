// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
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