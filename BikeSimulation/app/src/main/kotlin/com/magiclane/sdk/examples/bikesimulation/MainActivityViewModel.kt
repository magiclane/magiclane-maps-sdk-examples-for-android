// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bikesimulation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.magiclane.sdk.routesandnavigation.EBikeProfile
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.ElectricBikeProfile
import com.magiclane.sdk.routesandnavigation.RoutePreferences
import com.magiclane.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------

class MainActivityViewModel : ViewModel() {
    
    // -------------------------------------------------------------------------------------------------
    
    val searchResultListLivedata = MutableLiveData<MutableList<SearchResultItem>>()
    val isElectricBikeProfile = MutableLiveData(false)
    var destination: SearchResultItem? = null
    var isElectric = false
    var bikeProfile = EBikeProfile.City
    lateinit var routePreferences: RoutePreferences
    private var electricBikeProfile: ElectricBikeProfile? = null
    private val settingsList = mutableListOf<SettingsItem>()
    private var hillsFactor = 5f
    private var bikeWeight= 12f
    private var bikerWeight = 70f
    private var auxConsumptionDay = 20f
    private var auxConsumptionNight = 20f
    private var avoidFerries = false
    private var avoidUnpavedRoads = false

    // -------------------------------------------------------------------------------------------------
    
    fun initPreferences() = SdkCall.execute {
        electricBikeProfile = ElectricBikeProfile()
        routePreferences = RoutePreferences().apply {
            transportMode = ERouteTransportMode.Bicycle
            setBikeProfile(EBikeProfile.City, if (isElectric) electricBikeProfile else null)
            hillsFactor
        }
    }
    
    // -------------------------------------------------------------------------------------------------
    
    fun setBikeProfile(type: EBikeProfile) = SdkCall.execute {
        bikeProfile = type
        routePreferences.setBikeProfile(type, if (isElectric) electricBikeProfile else null)
    }

    // -------------------------------------------------------------------------------------------------
    
    private fun setIsElectric(isElectric: Boolean) = SdkCall.execute {
        this.isElectric = isElectric
        isElectricBikeProfile.postValue(isElectric)
        setBikeProfile(bikeProfile)
    }

    // -------------------------------------------------------------------------------------------------

    fun getSettingsList(): MutableList<SettingsItem> {
        settingsList.clear()
        settingsList.apply {
            add(SettingsSwitchItem("E-Bike", isElectric) {
                setIsElectric(it)
            })
            add(SettingsSliderItem("Hills", 0f , hillsFactor,10f, "" ){
                hillsFactor = it
                SdkCall.execute{ routePreferences.avoidBikingHillFactor = it }
            })
            add(SettingsSwitchItem("Avoid Ferries", avoidFerries) {
               avoidFerries = it
                SdkCall.execute{ routePreferences.avoidFerries = it }
            })
            add(SettingsSwitchItem("Avoid Unpaved Roads", avoidUnpavedRoads) {
                avoidUnpavedRoads = it
                SdkCall.execute{ routePreferences.avoidUnpavedRoads = it }
            })
            add(SettingsSliderItem("Bike Weight", 9f , bikeWeight,50f, "kg" ){
                bikerWeight = it
                SdkCall.execute{ electricBikeProfile?.bikeMass = it }
            })
            add(SettingsSliderItem("Biker Weight", 10f , bikerWeight,150f, "kg" ){
                bikerWeight = it
                SdkCall.execute{ electricBikeProfile?.bikerMass = it }
            })
            add(SettingsSliderItem("Aux Consumption Day", 0f , auxConsumptionDay,100f, "Wh/h" ){
                auxConsumptionDay = it
                SdkCall.execute{ electricBikeProfile?.auxConsumptionDay = it }
            })
            add(SettingsSliderItem("Aux Consumption Night", 0f , auxConsumptionNight,100f, "Wh/h" ){
                auxConsumptionNight = it
                SdkCall.execute{ electricBikeProfile?.auxConsumptionNight = it }
            })
        }
        return settingsList
    }
    // -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
